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
        String q = "SELECT seq, segment, field_count FROM hl7.main.hl7_segments("
                + Examples.SAMPLE_MSG_SQL + ");";
        String desc = "List every segment of an admit message in order (MSH, EVN, PID, PV1) "
                + "with its field count.";
        return FunctionMetadata.describe(
                        "Split an HL7 v2.x message into its segments: one row per segment with its "
                                + "3-letter id, field count, and raw text.")
                .withCategories("hl7", "healthcare", "parsing")
                .withTags(Meta.objectTags(
                        "Split HL7 Message Into Segments",
                        "Splits an HL7 v2.x message into its constituent segments, returning one "
                                + "row per segment in message order. Each row carries the 1-based "
                                + "ordinal (`seq`), the 3-letter segment id (`segment`, e.g. `MSH`, "
                                + "`PID`, `PV1`), the number of fields in the segment (`field_count`, "
                                + "using HL7 numbering so `MSH-1` counts), and the raw segment text "
                                + "(`raw`).\n\n"
                                + "Use it as the first step when ingesting a feed: enumerate what "
                                + "segments a message contains before drilling into fields with "
                                + "`hl7_fields` or `hl7_get`. The `message` argument is polymorphic — "
                                + "a VARCHAR carrying the message text or a BLOB carrying its bytes. "
                                + "NULL or malformed input (no leading MSH) yields no rows; the "
                                + "worker never throws.",
                        "## hl7_segments\n\n"
                                + "Returns one row per segment of an HL7 v2.x message, in order. "
                                + "Handy for discovering a message's structure before extracting "
                                + "fields.\n\n"
                                + "Pass the message inline as a VARCHAR/BLOB. Malformed or NULL "
                                + "input simply produces no rows. See `vgi.result_columns_md` for "
                                + "the returned columns.",
                        "hl7 segments, split message, segment list, MSH, PID, PV1, parse hl7, "
                                + "segment id, message structure, hl7 v2"))
                .withTag("vgi.result_columns_md", COLUMNS_MD)
                .withTag("vgi.executable_examples", Examples.EXECUTABLE_EXAMPLES)
                .withExamples(java.util.List.of(new FunctionExample(q, desc, null)))
                .withTag("vgi.example_queries", Examples.exampleQueriesTag(q, desc));
    }

    /** Markdown table of the returned columns (static schema, same for every call). */
    private static final String COLUMNS_MD =
            "| column | type | description |\n"
                    + "| --- | --- | --- |\n"
                    + "| `seq` | INTEGER | 1-based ordinal of the segment within the message. |\n"
                    + "| `segment` | VARCHAR | 3-letter segment identifier (e.g. MSH, PID, PV1). |\n"
                    + "| `field_count` | INTEGER | Number of fields in the segment (HL7 numbering; "
                    + "MSH-1 counts). |\n"
                    + "| `raw` | VARCHAR | Raw text of the entire segment. |";

    @Override public List<ArgSpec> argumentSpecs() {
        // Polymorphic message arg: an any-typed positional so DuckDB binds both a
        // VARCHAR message and a BLOB/BINARY of bytes; resolved via MessageInput.
        // Built like ArgSpec.any(...) but with a per-argument doc (VGI312).
        return List.of(new ArgSpec(
                "message", 0, new org.apache.arrow.vector.types.pojo.ArrowType.Null(),
                "The HL7 v2.x message to split, supplied inline as the message itself (either "
                        + "its text or its raw bytes), never a file path. Segments are separated "
                        + "by carriage returns and the first must be MSH. NULL or malformed input "
                        + "(no leading MSH) yields no rows.",
                false, false, "", List.of(), false, true, false));
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
