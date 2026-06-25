package farm.query.vgi.hl7;

import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.protocol.FunctionExample;
import farm.query.vgi.scalar.ScalarFn;
import farm.query.vgi.scalar.Vector;
import farm.query.vgi.types.Schemas;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.arrow.vector.util.Text;

/**
 * Base for the single-field MSH accessor scalars
 * ({@code hl7_message_type} = MSH-9, {@code hl7_version} = MSH-12,
 * {@code hl7_message_control_id} = MSH-10). Each returns the raw field text, or
 * NULL when the message is malformed or the field is absent. NULL input → NULL.
 */
abstract class MshScalar extends ScalarFn {

    private final String sqlName;
    private final int mshField;
    private final String description;
    private final FunctionExample example;
    private final String title;
    private final String docLlm;
    private final String docMd;
    private final String keywords;

    MshScalar(String sqlName, int mshField, String description, FunctionExample example,
              String title, String docLlm, String docMd, String keywords) {
        this.sqlName = sqlName;
        this.mshField = mshField;
        this.description = description;
        this.example = example;
        this.title = title;
        this.docLlm = docLlm;
        this.docMd = docMd;
        this.keywords = keywords;
    }

    @Override public final String name() { return sqlName; }

    @Override public final String description() { return description; }

    @Override public final FunctionMetadata metadata() {
        return FunctionMetadata.describe(description)
                .withCategories("hl7", "healthcare")
                .withTags(Meta.objectTags(title, docLlm, docMd, keywords))
                .withExamples(java.util.List.of(example))
                .withTag("vgi.example_queries",
                        Examples.exampleQueriesTag(example.sql(), example.description()));
    }

    @Override protected final ArrowType outputType(Schema inputSchema, Arguments args) {
        return Schemas.UTF8;
    }

    final void run(FieldVector in, VarCharVector out) {
        int n = in.getValueCount();
        for (int i = 0; i < n; i++) {
            String message = MessageInput.at(in, i);
            if (message == null) { out.setNull(i); continue; }
            String value;
            try {
                value = Hl7Message.parse(message).field("MSH", 0, mshField);
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

    /** {@code hl7_message_type(message)} — MSH-9 (e.g. 'ADT^A01'). */
    public static final class MessageType extends MshScalar {
        public MessageType() {
            super("hl7_message_type", 9,
                    "The HL7 message type from MSH-9 (e.g. 'ADT^A01' or 'ADT^A01^ADT_A01').",
                    new FunctionExample(
                            "SELECT hl7.main.hl7_message_type(" + Examples.SAMPLE_MSG_SQL + ");",
                            "Read the message type (MSH-9) from an admit message.",
                            "ADT^A01"),
                    "Read HL7 Message Type",
                    "Reads the HL7 message type from field MSH-9, e.g. `ADT^A01` (event) or "
                            + "`ADT^A01^ADT_A01` (with the message structure). This identifies what "
                            + "kind of message it is — an admit (ADT), observation result (ORU), "
                            + "order (ORM), etc.\n\n"
                            + "Use it to route or filter a mixed feed by message type. Returns the "
                            + "raw MSH-9 text, or NULL when the message is malformed or the field is "
                            + "absent; NULL input yields NULL. The `message` argument is a VARCHAR "
                            + "text or BLOB bytes.",
                    "## hl7_message_type\n\n"
                            + "Returns MSH-9, the HL7 message type/trigger event (e.g. `ADT^A01`).\n\n"
                            + "The most common dispatch key in an HL7 v2 feed — use it to split or "
                            + "route messages by type. Malformed/absent input maps to NULL rather "
                            + "than erroring.",
                    "hl7 message type, MSH-9, trigger event, ADT, ORU, ORM, message kind, "
                            + "routing, dispatch, parse hl7, hl7 v2");
        }
        public void compute(@Vector(value = "message", any = true,
                                    doc = "The HL7 v2.x message to read MSH-9 from, supplied "
                                            + "inline as the message itself (either its text or "
                                            + "its raw bytes), never a file path. Evaluated per "
                                            + "row; NULL or malformed input yields NULL.")
                            FieldVector in, VarCharVector out) {
            run(in, out);
        }
    }

    /** {@code hl7_version(message)} — MSH-12 (e.g. '2.5'). */
    public static final class Version extends MshScalar {
        public Version() {
            super("hl7_version", 12, "The HL7 version ID from MSH-12 (e.g. '2.5').",
                    new FunctionExample(
                            "SELECT hl7.main.hl7_version(" + Examples.SAMPLE_MSG_SQL + ");",
                            "Read the HL7 version (MSH-12) from a message.",
                            "2.5"),
                    "Read HL7 Version Id",
                    "Reads the HL7 version ID from field MSH-12, e.g. `2.3`, `2.5`, or `2.7`. This "
                            + "tells you which release of the HL7 v2.x standard the message claims "
                            + "to conform to, which affects field semantics across versions.\n\n"
                            + "Use it to branch parsing logic or to audit which versions a feed is "
                            + "sending. Returns the raw MSH-12 text, or NULL when the message is "
                            + "malformed or the field is absent; NULL input yields NULL. The "
                            + "`message` argument is a VARCHAR text or BLOB bytes.",
                    "## hl7_version\n\n"
                            + "Returns MSH-12, the HL7 v2.x version id (e.g. `2.5`).\n\n"
                            + "Useful for version-aware handling and feed audits. Malformed or "
                            + "absent input maps to NULL rather than erroring.",
                    "hl7 version, MSH-12, version id, 2.3, 2.5, 2.7, standard release, "
                            + "conformance, parse hl7, hl7 v2");
        }
        public void compute(@Vector(value = "message", any = true,
                                    doc = "The HL7 v2.x message to read MSH-12 from, supplied "
                                            + "inline as the message itself (either its text or "
                                            + "its raw bytes), never a file path. Evaluated per "
                                            + "row; NULL or malformed input yields NULL.")
                            FieldVector in, VarCharVector out) {
            run(in, out);
        }
    }

    /** {@code hl7_message_control_id(message)} — MSH-10. */
    public static final class ControlId extends MshScalar {
        public ControlId() {
            super("hl7_message_control_id", 10, "The message control ID from MSH-10.",
                    new FunctionExample(
                            "SELECT hl7.main.hl7_message_control_id(" + Examples.SAMPLE_MSG_SQL + ");",
                            "Read the message control ID (MSH-10) used for acknowledgements.",
                            "MSG00001"),
                    "Read HL7 Message Control Id",
                    "Reads the message control ID from field MSH-10, the sender-assigned unique "
                            + "identifier for this message. It is echoed back in the acknowledgement "
                            + "(ACK MSA-2) and is the key for de-duplication and message tracking.\n\n"
                            + "Use it to correlate a message with its ACK, to dedupe a feed, or to "
                            + "trace a single message end to end. Returns the raw MSH-10 text, or "
                            + "NULL when the message is malformed or the field is absent; NULL input "
                            + "yields NULL. The `message` argument is a VARCHAR text or BLOB bytes.",
                    "## hl7_message_control_id\n\n"
                            + "Returns MSH-10, the message control id — the sender's unique id for "
                            + "the message, echoed in the ACK (MSA-2).\n\n"
                            + "Use it for de-duplication, ACK correlation, and message tracing. "
                            + "Malformed or absent input maps to NULL rather than erroring.",
                    "hl7 control id, MSH-10, message control id, ACK, MSA-2, dedupe, "
                            + "deduplication, tracking, correlation, parse hl7, hl7 v2");
        }
        public void compute(@Vector(value = "message", any = true,
                                    doc = "The HL7 v2.x message to read MSH-10 from, supplied "
                                            + "inline as the message itself (either its text or "
                                            + "its raw bytes), never a file path. Evaluated per "
                                            + "row; NULL or malformed input yields NULL.")
                            FieldVector in, VarCharVector out) {
            run(in, out);
        }
    }
}
