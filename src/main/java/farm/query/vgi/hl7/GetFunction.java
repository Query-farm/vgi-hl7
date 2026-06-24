package farm.query.vgi.hl7;

import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.protocol.FunctionExample;
import farm.query.vgi.scalar.Const;
import farm.query.vgi.scalar.ScalarFn;
import farm.query.vgi.scalar.Vector;
import farm.query.vgi.types.Schemas;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.arrow.vector.util.Text;

/**
 * {@code hl7.hl7_get(message, location) -> VARCHAR} — extract a single value from
 * an HL7 v2 message by location, e.g. {@code 'PID-5'} (field), {@code 'PID-5.1'}
 * (component 1 of field 5), {@code 'MSH-9.1'}, or with a segment repetition
 * {@code 'DG1[2]-3'}. Field, component, and subcomponent indices are 1-based per
 * HL7 convention; the first repetition of a repeating field is addressed.
 *
 * <p>Returns NULL when the message is malformed, the location syntax is invalid,
 * or the addressed element is absent. NULL message → NULL.
 */
public final class GetFunction extends ScalarFn {

    @Override public String name() { return "hl7_get"; }

    @Override public String description() {
        return "Extract a value from an HL7 v2.x message by location (e.g. 'PID-5', 'PID-5.1', "
                + "'MSH-9.1', 'DG1[2]-3'); 1-based field/component/subcomponent indices.";
    }

    @Override public FunctionMetadata metadata() {
        String getPid = "SELECT hl7.main.hl7_get(" + Examples.SAMPLE_MSG_SQL + ", 'PID-5.1');";
        String getMsh = "SELECT hl7.main.hl7_get(" + Examples.SAMPLE_MSG_SQL + ", 'MSH-9');";
        return FunctionMetadata.describe(description())
                .withCategories("hl7", "healthcare", "extraction")
                .withTags(Meta.objectTags(
                        "Extract HL7 Value By Location",
                        "Extracts a single value from an HL7 v2.x message addressed by an HL7 "
                                + "location string. Supports `'PID-5'` (a whole field), `'PID-5.1'` "
                                + "(component 1 of field 5), `'MSH-9.1.2'` (component.subcomponent), "
                                + "and a segment repetition such as `'DG1[2]-3'` or `'DG1(2)-3'`. "
                                + "Field, component, and subcomponent indices are 1-based per HL7 "
                                + "convention; the first repetition of a repeating field is "
                                + "addressed.\n\n"
                                + "Use it to pull individual data points (patient name, message "
                                + "type, ordering provider, etc.) out of a feed without exploding "
                                + "the whole message. Returns NULL when the message is malformed, "
                                + "the location syntax is invalid, or the addressed element is "
                                + "absent; a NULL message yields NULL. The `message` argument is a "
                                + "VARCHAR text or a BLOB of bytes.",
                        "## hl7_get\n\n"
                                + "Reads one value from an HL7 v2.x message by location — e.g. "
                                + "`hl7_get(msg, 'PID-5.1')` for a patient's family name.\n\n"
                                + "Location grammar: `SEG-field[.component[.subcomponent]]` with an "
                                + "optional segment repetition `SEG[n]-field`. Indices are 1-based. "
                                + "Missing/invalid locations return NULL rather than erroring, so "
                                + "it is safe to run across dirty data.",
                        "hl7 get, extract, location, PID-5, MSH-9, component, subcomponent, "
                                + "accessor, field path, pluck value, parse hl7, hl7 v2",
                        "GetFunction.java"))
                .withExamples(java.util.List.of(
                        new FunctionExample(getPid,
                                "Extract the patient's family name (component 1 of PID-5).", "Doe"),
                        new FunctionExample(getMsh,
                                "Extract the message type field (MSH-9) by location.", "ADT^A01")))
                .withTag("vgi.example_queries", Examples.exampleQueriesTag(
                        getPid, "Extract the patient's family name (component 1 of PID-5).",
                        getMsh, "Extract the message type field (MSH-9) by location."));
    }

    @Override protected ArrowType outputType(Schema inputSchema, Arguments args) {
        return Schemas.UTF8;
    }

    public void compute(@Vector(value = "message", any = true) FieldVector in,
                        @Const("location") String location,
                        VarCharVector out) {
        Hl7Location loc = Hl7Location.parse(location);
        int n = in.getValueCount();
        for (int i = 0; i < n; i++) {
            String message = MessageInput.at(in, i);
            if (message == null || loc == null) { out.setNull(i); continue; }
            String value;
            try {
                value = loc.resolve(Hl7Message.parse(message));
            } catch (Throwable t) {
                value = null;
            }
            if (value == null) {
                out.setNull(i);
            } else {
                out.setSafe(i, new Text(value));
            }
        }
    }
}
