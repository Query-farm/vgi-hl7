package farm.query.vgi.hl7;

/**
 * A parsed HL7 location reference used by {@code hl7_get}, e.g. {@code "PID-5"},
 * {@code "PID-5.1"} (component), {@code "MSH-9.1.2"} (subcomponent), with an
 * optional segment repetition like {@code "DG1[1]-3"} or {@code "PID(2)-5"}.
 *
 * <p>Grammar (all parts after the segment are optional):
 * <pre>
 *   SEG [ '[' segRep ']' | '(' segRep ')' ] '-' field [ '.' component [ '.' subcomponent ] ]
 * </pre>
 * Field/component/subcomponent are 1-based (HL7 convention). The segment
 * repetition is 1-based in the text and stored 0-based here; it defaults to 0
 * (the first occurrence). Field repetition is addressed only via the long-format
 * table function; {@code hl7_get} returns the first repetition of a field.
 */
public record Hl7Location(
        String segment,
        int segmentRep,   // 0-based
        int field,        // 1-based, -1 if absent (segment-only, unsupported by get)
        int component,    // 1-based, -1 if absent
        int subcomponent  // 1-based, -1 if absent
) {

    /** Parse a location string. Returns null when the syntax is unrecognizable. */
    public static Hl7Location parse(String loc) {
        if (loc == null) {
            return null;
        }
        String s = loc.trim();
        if (s.isEmpty()) {
            return null;
        }

        int dash = s.indexOf('-');
        if (dash <= 0) {
            return null; // need a segment and a field number
        }

        String segPart = s.substring(0, dash);
        String rest = s.substring(dash + 1);

        // Optional segment repetition: SEG[1] or SEG(1).
        int segRep = 0;
        String segName = segPart;
        int open = indexOfAny(segPart, '[', '(');
        if (open >= 0) {
            char closeCh = segPart.charAt(open) == '[' ? ']' : ')';
            int close = segPart.indexOf(closeCh, open);
            if (close < 0) {
                return null;
            }
            segName = segPart.substring(0, open);
            String repStr = segPart.substring(open + 1, close).trim();
            Integer rep = parseInt(repStr);
            if (rep == null || rep < 1) {
                return null;
            }
            segRep = rep - 1; // text is 1-based; store 0-based
        }

        segName = segName.trim();
        if (segName.isEmpty()) {
            return null;
        }

        // rest = field[.component[.subcomponent]]
        String[] parts = rest.split("\\.", -1);
        Integer field = parseInt(parts[0].trim());
        if (field == null || field < 1) {
            return null;
        }
        int component = -1;
        int subcomponent = -1;
        if (parts.length > 1) {
            Integer c = parseInt(parts[1].trim());
            if (c == null || c < 1) {
                return null;
            }
            component = c;
        }
        if (parts.length > 2) {
            Integer sc = parseInt(parts[2].trim());
            if (sc == null || sc < 1) {
                return null;
            }
            subcomponent = sc;
        }
        if (parts.length > 3) {
            return null; // too many dotted parts
        }

        return new Hl7Location(segName, segRep, field, component, subcomponent);
    }

    private static int indexOfAny(String s, char a, char b) {
        int ia = s.indexOf(a);
        int ib = s.indexOf(b);
        if (ia < 0) {
            return ib;
        }
        if (ib < 0) {
            return ia;
        }
        return Math.min(ia, ib);
    }

    private static Integer parseInt(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Resolve this location against a message, returning the addressed value or
     * null if absent. Drills field -> first repetition -> component -> subcomponent.
     */
    public String resolve(Hl7Message msg) {
        String fieldText = msg.field(segment, segmentRep, field);
        if (fieldText == null) {
            return null;
        }
        // hl7_get addresses the first repetition of the field.
        var reps = msg.repetitions(fieldText);
        String value = reps.isEmpty() ? fieldText : reps.get(0);

        if (component < 0) {
            return value;
        }
        String comp = msg.component(value, component);
        if (comp == null) {
            return null;
        }
        if (subcomponent < 0) {
            return comp;
        }
        return msg.subcomponent(comp, subcomponent);
    }
}
