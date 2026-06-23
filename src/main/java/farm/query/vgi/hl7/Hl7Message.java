package farm.query.vgi.hl7;

import java.util.ArrayList;
import java.util.List;

/**
 * A pure-JDK parser for HL7 v2.x pipe-delimited clinical messages.
 *
 * <p><b>Why a pure parser (no HAPI):</b> HL7 v2 is a delimited text format, not a
 * binary one, so it can be parsed with nothing but the JDK. We deliberately avoid
 * the HAPI HL7v2 library because its licensing is murky for commercial use; a
 * hand-rolled parser keeps this worker MIT-licensed and dependency-light (the VGI
 * SDK is the only runtime dependency).
 *
 * <p><b>Structure of an HL7 v2 message:</b>
 * <ul>
 *   <li>A message is a list of <em>segments</em> separated by carriage returns
 *       ({@code \r}). We tolerate {@code \n} and {@code \r\n} as well, since real
 *       feeds are sloppy about line endings.</li>
 *   <li>The first segment is always {@code MSH} (Message Header). It is special:
 *       <ul>
 *         <li><b>MSH-1</b> is the <em>field separator</em> itself — the very first
 *             character after {@code MSH} (canonically {@code |}). We read it from
 *             the message rather than hardcoding.</li>
 *         <li><b>MSH-2</b> is the <em>encoding characters</em>: component separator,
 *             repetition separator, escape character, subcomponent separator —
 *             canonically {@code ^~\&}. We read these too.</li>
 *       </ul></li>
 *   <li>Within a segment, fields are split by the field separator; a field may
 *       carry repetitions split by the repetition separator; each repetition is
 *       split into components by the component separator; each component into
 *       subcomponents by the subcomponent separator.</li>
 * </ul>
 *
 * <p><b>The MSH numbering quirk:</b> because MSH-1 <em>is</em> the field separator
 * character, the fields of the MSH segment are numbered with a one-position
 * offset relative to every other segment. When you tokenize {@code "MSH" + sep +
 * rest} by the separator, the {@code "MSH"} token would naturally be field 0 and
 * the first real field ({@code ^~\&}) would be index 1 — but HL7 calls the
 * separator MSH-1 and the encoding chars MSH-2. {@link #field(String, int, int)}
 * encapsulates that offset so callers can address {@code MSH-9}, {@code MSH-12},
 * etc. by their canonical HL7 numbers.
 */
public final class Hl7Message {

    /** Default HL7 v2 encoding when a message omits or under-specifies MSH-2. */
    static final char DEFAULT_FIELD_SEP = '|';
    static final char DEFAULT_COMPONENT_SEP = '^';
    static final char DEFAULT_REPETITION_SEP = '~';
    static final char DEFAULT_ESCAPE = '\\';
    static final char DEFAULT_SUBCOMPONENT_SEP = '&';

    private final List<Segment> segments;
    private final char fieldSep;
    private final char componentSep;
    private final char repetitionSep;
    private final char escape;
    private final char subcomponentSep;
    private final boolean valid;

    private Hl7Message(List<Segment> segments, char fieldSep, char componentSep,
                       char repetitionSep, char escape, char subcomponentSep, boolean valid) {
        this.segments = segments;
        this.fieldSep = fieldSep;
        this.componentSep = componentSep;
        this.repetitionSep = repetitionSep;
        this.escape = escape;
        this.subcomponentSep = subcomponentSep;
        this.valid = valid;
    }

    /** One parsed segment: its 3-letter name and the raw text of each of its fields. */
    public record Segment(String name, List<String> rawFields, String raw) {}

    /**
     * Parse a message. Never throws and never returns null: malformed input
     * (null/empty/no MSH/too short to carry separators) yields an
     * {@link #isValid() invalid} message with no segments. NULL bytes/strings are
     * the caller's responsibility (they should not call this at all).
     */
    public static Hl7Message parse(String text) {
        if (text == null) {
            return invalid();
        }
        // An MSH must carry at least "MSH" + field-sep + encoding chars: "MSH|^~\&".
        // Anything shorter cannot define the separators, so it is not valid HL7.
        if (text.length() < 4 || !text.startsWith("MSH")) {
            return invalid();
        }

        char fieldSep = text.charAt(3);

        // MSH-2 is the run of encoding characters immediately after MSH-1. It ends
        // at the next field separator. Read whatever is present and fall back to
        // canonical defaults for any position the message does not supply.
        int encStart = 4;
        int encEnd = text.indexOf(fieldSep, encStart);
        if (encEnd < 0) {
            encEnd = text.length();
        }
        String enc = text.substring(encStart, encEnd);

        char componentSep = enc.length() > 0 ? enc.charAt(0) : DEFAULT_COMPONENT_SEP;
        char repetitionSep = enc.length() > 1 ? enc.charAt(1) : DEFAULT_REPETITION_SEP;
        char escape = enc.length() > 2 ? enc.charAt(2) : DEFAULT_ESCAPE;
        char subcomponentSep = enc.length() > 3 ? enc.charAt(3) : DEFAULT_SUBCOMPONENT_SEP;

        List<Segment> segments = splitSegments(text, fieldSep);
        boolean valid = !segments.isEmpty() && segments.get(0).name().equals("MSH");
        return new Hl7Message(segments, fieldSep, componentSep, repetitionSep,
                escape, subcomponentSep, valid);
    }

    private static Hl7Message invalid() {
        return new Hl7Message(List.of(), DEFAULT_FIELD_SEP, DEFAULT_COMPONENT_SEP,
                DEFAULT_REPETITION_SEP, DEFAULT_ESCAPE, DEFAULT_SUBCOMPONENT_SEP, false);
    }

    /** Split a message body into segments on CR / LF / CRLF, dropping empty lines. */
    private static List<Segment> splitSegments(String text, char fieldSep) {
        List<Segment> out = new ArrayList<>();
        // Normalize all line endings to \n then split; tolerant of \r, \r\n, \n.
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        for (String line : normalized.split("\n", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            out.add(parseSegment(line, fieldSep));
        }
        return out;
    }

    private static Segment parseSegment(String line, char fieldSep) {
        // Split on the field separator literally (it may be a regex metachar).
        List<String> tokens = splitLiteral(line, fieldSep);
        String name = tokens.isEmpty() ? "" : tokens.get(0);

        List<String> rawFields;
        if (name.equals("MSH")) {
            // Reconstruct MSH so that MSH-1 is the field separator char and MSH-2
            // is the encoding characters, matching canonical HL7 field numbering.
            // tokens = ["MSH", "<enc>", f3, f4, ...]; we want
            // rawFields(MSH) = [ "MSH"(seg id), "<fieldSep>"(MSH-1), "<enc>"(MSH-2), f3, ... ].
            rawFields = new ArrayList<>();
            rawFields.add(name);                      // segment id, addressed as field 0
            rawFields.add(String.valueOf(fieldSep));  // MSH-1
            for (int i = 1; i < tokens.size(); i++) { // MSH-2 onward
                rawFields.add(tokens.get(i));
            }
        } else {
            rawFields = tokens;
        }
        return new Segment(name, rawFields, line);
    }

    /** Literal (non-regex) split that keeps trailing empties, like split(re, -1). */
    static List<String> splitLiteral(String s, char sep) {
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == sep) {
                out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        out.add(s.substring(start));
        return out;
    }

    // ---- accessors ---------------------------------------------------------

    public boolean isValid() { return valid; }

    public List<Segment> segments() { return segments; }

    public char fieldSep() { return fieldSep; }
    public char componentSep() { return componentSep; }
    public char repetitionSep() { return repetitionSep; }
    public char subcomponentSep() { return subcomponentSep; }
    public char escape() { return escape; }

    /** Count of "user" fields for a segment, mirroring how {@code field()} indexes. */
    public int fieldCount(Segment seg) {
        // rawFields[0] is the segment id; fields are numbered from 1.
        return Math.max(0, seg.rawFields().size() - 1);
    }

    /**
     * The first segment with the given 3-letter name, or null. {@code rep} is
     * 0-based (so the second PID would be rep 1).
     */
    public Segment segment(String name, int rep) {
        int seen = 0;
        for (Segment s : segments) {
            if (s.name().equals(name)) {
                if (seen == rep) {
                    return s;
                }
                seen++;
            }
        }
        return null;
    }

    /**
     * Raw text of field {@code fieldNum} (1-based) of the {@code segRep}-th
     * (0-based) occurrence of segment {@code segName}, or null if absent. For an
     * MSH segment, {@code field("MSH", *, 1)} returns the field separator and
     * {@code field("MSH", *, 2)} the encoding characters — canonical HL7 numbering.
     */
    public String field(String segName, int segRep, int fieldNum) {
        Segment seg = segment(segName, segRep);
        if (seg == null || fieldNum < 1) {
            return null;
        }
        List<String> raw = seg.rawFields();
        // rawFields[0] is the segment id; field N lives at index N.
        if (fieldNum >= raw.size()) {
            return null;
        }
        return raw.get(fieldNum);
    }

    /** Split a field's raw text into its repetitions. */
    public List<String> repetitions(String fieldText) {
        if (fieldText == null) {
            return List.of();
        }
        return splitLiteral(fieldText, repetitionSep);
    }

    /** The 1-based component {@code comp} of a single (already de-repeated) field value. */
    public String component(String value, int comp) {
        if (value == null || comp < 1) {
            return null;
        }
        List<String> comps = splitLiteral(value, componentSep);
        if (comp > comps.size()) {
            return null;
        }
        return comps.get(comp - 1);
    }

    /** The 1-based subcomponent {@code sub} of a single component value. */
    public String subcomponent(String component, int sub) {
        if (component == null || sub < 1) {
            return null;
        }
        List<String> subs = splitLiteral(component, subcomponentSep);
        if (sub > subs.size()) {
            return null;
        }
        return subs.get(sub - 1);
    }
}
