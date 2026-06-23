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
import java.util.List;

/**
 * {@code hl7.hl7_segments(message) -> (seq, segment, field_count, raw)} — one row
 * per segment of an HL7 v2 message, in message order.
 *
 * <p>The {@code message} argument is polymorphic: a VARCHAR carrying the message
 * text, or a BLOB carrying its bytes. NULL or malformed (no MSH) input yields no
 * rows; the worker never crashes.
 */
public final class SegmentsFunction implements TableFunction {

    @Override public String name() { return "hl7_segments"; }

    @Override public FunctionMetadata metadata() {
        return FunctionMetadata.describe(
                        "Split an HL7 v2.x message into its segments: one row per segment with its "
                                + "3-letter id, field count, and raw text.")
                .withCategories("hl7", "healthcare", "parsing");
    }

    @Override public List<ArgSpec> argumentSpecs() {
        // Polymorphic message arg: an any-typed positional so DuckDB binds both a
        // VARCHAR message and a BLOB/BINARY of bytes; resolved via MessageInput.
        return List.of(ArgSpec.any("message", 0, List.of()));
    }

    @Override public BindResponse onBind(TableBindParams p) {
        return BindResponse.forSchema(SchemaUtil.serializeSchema(Hl7Schemas.SEGMENTS_SCHEMA));
    }

    @Override public long cardinality(TableBindParams p) { return 16L; }

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
            List<Hl7Schemas.SegmentRow> rows = new ArrayList<>();
            int seq = 1;
            for (Hl7Message.Segment seg : msg.segments()) {
                rows.add(new Hl7Schemas.SegmentRow(
                        seq++, seg.name(), msg.fieldCount(seg), seg.raw()));
            }
            // Malformed (no MSH) -> no segments -> no rows.
            out.emit(Hl7Schemas.segmentsBatch(Allocators.root(), rows));
            out.finish();
        }
    }
}
