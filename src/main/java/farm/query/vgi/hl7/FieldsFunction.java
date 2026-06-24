package farm.query.vgi.hl7;

import farm.query.vgi.function.ArgSpec;
import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.protocol.FunctionExample;
import farm.query.vgi.internal.SchemaUtil;
import farm.query.vgi.protocol.BindResponse;
import farm.query.vgi.table.TableBindParams;
import farm.query.vgi.table.TableFunction;
import farm.query.vgi.table.TableInitParams;
import farm.query.vgi.table.TableProducerState;
import farm.query.vgirpc.CallContext;
import farm.query.vgirpc.OutputCollector;
import farm.query.vgirpc.wire.Allocators;
import org.apache.arrow.vector.types.pojo.ArrowType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code hl7.hl7_fields(message) -> (segment, segment_rep, field, repetition, value)}
 * — long format: one row per field value, with field repetitions expanded into
 * separate rows. The component/subcomponent structure of each value is preserved
 * as the raw field text (callers can decompose it with {@code hl7_get} or by
 * splitting on the encoding characters).
 *
 * <p>{@code segment_rep} disambiguates repeated segments (e.g. multiple OBX or
 * DG1). NULL or malformed input yields no rows.
 */
public final class FieldsFunction implements TableFunction {

    @Override public String name() { return "hl7_fields"; }

    @Override public FunctionMetadata metadata() {
        String q = "SELECT field, value FROM hl7.main.hl7_fields(" + Examples.SAMPLE_MSG_SQL
                + ") WHERE segment = 'PID';";
        String desc = "List every field of the PID (patient identification) segment as "
                + "long-format rows.";
        return FunctionMetadata.describe(
                        "Explode an HL7 v2.x message into long format: one row per field value "
                                + "(repetitions expanded), keyed by segment, segment occurrence, field, and "
                                + "repetition. Component structure is preserved in the raw value text.")
                .withCategories("hl7", "healthcare", "parsing")
                .withTags(Meta.objectTags(
                        "Explode HL7 Message Into Field Rows",
                        "Explodes an HL7 v2.x message into long (tidy) format: one row per field "
                                + "value, with repeating fields expanded into separate rows. Each "
                                + "row is keyed by `segment` (3-letter id), `segment_rep` (0-based "
                                + "occurrence of that segment within the message, to disambiguate "
                                + "repeated OBX/DG1/etc.), `field` (1-based HL7 field number), and "
                                + "`repetition` (0-based index of the repeated value); `value` holds "
                                + "the raw field text with its component/subcomponent structure "
                                + "preserved.\n\n"
                                + "Use it to unpivot an entire message for set-based SQL "
                                + "(filtering, grouping, joining) instead of addressing one location "
                                + "at a time with `hl7_get`. Note the MSH quirk: MSH-1 (the field "
                                + "separator) and MSH-2 (the encoding characters, which literally "
                                + "contain `~`) are emitted as single verbatim values and are not "
                                + "re-split on the repetition separator. The `message` argument is a "
                                + "VARCHAR text or BLOB bytes; NULL or malformed input yields no "
                                + "rows.",
                        "## hl7_fields\n\n"
                                + "Unpivots a whole HL7 v2.x message into long format — one row per "
                                + "field value with repetitions expanded — so you can query it with "
                                + "ordinary SQL.\n\n"
                                + "Component/subcomponent structure stays intact in `value`; "
                                + "decompose it further with `hl7_get` or by splitting on the "
                                + "encoding characters. MSH-1/MSH-2 are kept verbatim. See "
                                + "`vgi.result_columns_md` for the returned columns.",
                        "hl7 fields, long format, tidy, unpivot, explode, repetitions, field value, "
                                + "segment_rep, component, subcomponent, parse hl7, hl7 v2",
                        "FieldsFunction.java"))
                .withTag("vgi.result_columns_md", COLUMNS_MD)
                .withExamples(java.util.List.of(new FunctionExample(q, desc, null)))
                .withTag("vgi.example_queries", Examples.exampleQueriesTag(q, desc));
    }

    /** Markdown table of the returned columns (static schema, same for every call). */
    private static final String COLUMNS_MD =
            "| column | type | description |\n"
                    + "| --- | --- | --- |\n"
                    + "| `segment` | VARCHAR | 3-letter segment identifier (e.g. MSH, PID). |\n"
                    + "| `segment_rep` | INTEGER | 0-based occurrence index of this segment within "
                    + "the message. |\n"
                    + "| `field` | INTEGER | 1-based HL7 field number within the segment. |\n"
                    + "| `repetition` | INTEGER | 0-based repetition index of the field value. |\n"
                    + "| `value` | VARCHAR | Raw field-value text (component/subcomponent structure "
                    + "preserved). |";

    @Override public List<ArgSpec> argumentSpecs() {
        return List.of(ArgSpec.any("message", 0, List.of()));
    }

    @Override public BindResponse onBind(TableBindParams p) {
        return BindResponse.forSchema(SchemaUtil.serializeSchema(Hl7Schemas.FIELDS_SCHEMA));
    }

    @Override public long cardinality(TableBindParams p) { return 256L; }

    @Override public TableProducerState createProducer(TableInitParams params) {
        Arguments a = params.arguments();
        Object value = a.positionalAt(0);
        ArrowType type = a.positionalTypeAt(0);
        return new State(MessageInput.fromArgument(value, type));
    }

    public static final class State extends TableProducerState {
        public String message;
        public boolean done;

        public State() {}

        State(String message) { this.message = message; }

        @Override public void produceTick(OutputCollector out, CallContext ctx) {
            if (done) { out.finish(); return; }
            done = true;

            if (message == null) { // NULL input -> no rows.
                out.finish();
                return;
            }

            Hl7Message msg = Hl7Message.parse(message);
            List<Hl7Schemas.FieldRow> rows = new ArrayList<>();
            Map<String, Integer> segCounts = new HashMap<>();

            for (Hl7Message.Segment seg : msg.segments()) {
                int segRep = segCounts.merge(seg.name(), 0, (a, b) -> a + 1);
                int fieldCount = msg.fieldCount(seg);
                for (int fieldNum = 1; fieldNum <= fieldCount; fieldNum++) {
                    String fieldText = seg.rawFields().get(fieldNum);
                    // MSH-1 (the field separator) and MSH-2 (encoding chars) must
                    // NOT be re-split on the repetition separator: MSH-2 literally
                    // contains '~'. Emit them as single, verbatim values.
                    boolean rawField = seg.name().equals("MSH") && fieldNum <= 2;
                    List<String> reps = rawField
                            ? List.of(fieldText)
                            : msg.repetitions(fieldText);
                    for (int rep = 0; rep < reps.size(); rep++) {
                        rows.add(new Hl7Schemas.FieldRow(
                                seg.name(), segRep, fieldNum, rep, reps.get(rep)));
                    }
                }
            }

            out.emit(Hl7Schemas.fieldsBatch(Allocators.root(), rows));
            out.finish();
        }
    }
}
