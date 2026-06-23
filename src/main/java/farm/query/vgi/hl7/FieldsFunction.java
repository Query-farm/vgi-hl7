package farm.query.vgi.hl7;

import farm.query.vgi.function.ArgSpec;
import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
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
        return FunctionMetadata.describe(
                        "Explode an HL7 v2.x message into long format: one row per field value "
                                + "(repetitions expanded), keyed by segment, segment occurrence, field, and "
                                + "repetition. Component structure is preserved in the raw value text.")
                .withCategories("hl7", "healthcare", "parsing");
    }

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
