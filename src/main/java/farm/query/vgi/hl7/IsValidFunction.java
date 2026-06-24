package farm.query.vgi.hl7;

import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.protocol.FunctionExample;
import farm.query.vgi.scalar.ScalarFn;
import farm.query.vgi.scalar.Vector;
import farm.query.vgi.types.Schemas;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Schema;

/**
 * {@code hl7.is_valid_hl7(message) -> BOOLEAN} — true when the input parses as a
 * well-formed HL7 v2 message: it begins with an {@code MSH} segment that defines
 * the field separator and encoding characters. NULL input yields NULL; malformed
 * input yields false (never an error).
 */
public final class IsValidFunction extends ScalarFn {

    @Override public String name() { return "is_valid_hl7"; }

    @Override public String description() {
        return "True when the input parses as a well-formed HL7 v2.x message (begins with an MSH "
                + "segment defining the field separator and encoding characters).";
    }

    @Override public FunctionMetadata metadata() {
        String okQuery = "SELECT hl7.main.is_valid_hl7(" + Examples.SAMPLE_MSG_SQL + ");";
        String badQuery = "SELECT hl7.main.is_valid_hl7('not an hl7 message');";
        return FunctionMetadata.describe(description())
                .withCategories("hl7", "healthcare", "validation")
                .withTags(Meta.objectTags(
                        "Validate HL7 v2 Message",
                        "Tests whether text parses as a well-formed HL7 v2.x message: it must begin "
                                + "with an `MSH` segment that defines the field separator (MSH-1) and "
                                + "the encoding characters (MSH-2). Returns true for a valid message, "
                                + "false for malformed text (never an error), and NULL for a NULL "
                                + "input.\n\n"
                                + "Use it as a cheap gate before parsing — e.g. filter a staging "
                                + "table to rows that are actually HL7 v2 before calling "
                                + "`hl7_segments`, `hl7_fields`, or `hl7_get`. The `message` "
                                + "argument is a VARCHAR text or a BLOB of bytes.",
                        "## is_valid_hl7\n\n"
                                + "Boolean predicate: does this text look like an HL7 v2.x message "
                                + "(leading MSH with separators and encoding chars)?\n\n"
                                + "Designed to be safe on dirty input — malformed text returns "
                                + "`false` rather than raising, and NULL maps to NULL. Pair it with "
                                + "a `WHERE` clause to skip non-HL7 rows before parsing.",
                        "is valid hl7, validate, well-formed, MSH, sniff, gate, predicate, "
                                + "verify message, parse hl7, hl7 v2",
                        "IsValidFunction.java"))
                .withExamples(java.util.List.of(
                        new FunctionExample(okQuery,
                                "Check that a well-formed ADT^A01 message validates.", "true"),
                        new FunctionExample(badQuery,
                                "Malformed text validates as false (never errors).", "false")))
                .withTag("vgi.example_queries", Examples.exampleQueriesTag(
                        okQuery, "Check that a well-formed ADT^A01 message validates.",
                        badQuery, "Malformed text validates as false (never errors)."));
    }

    @Override protected ArrowType outputType(Schema inputSchema, Arguments args) {
        return Schemas.BOOL;
    }

    public void compute(@Vector(value = "message", any = true) FieldVector in, BitVector out) {
        int n = in.getValueCount();
        for (int i = 0; i < n; i++) {
            String message = MessageInput.at(in, i);
            if (message == null) { out.setNull(i); continue; }
            boolean valid;
            try {
                valid = Hl7Message.parse(message).isValid();
            } catch (Throwable t) {
                valid = false;
            }
            out.setSafe(i, valid ? 1 : 0);
        }
    }
}
