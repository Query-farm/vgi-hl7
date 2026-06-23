package farm.query.vgi.hl7;

import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
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

    MshScalar(String sqlName, int mshField, String description) {
        this.sqlName = sqlName;
        this.mshField = mshField;
        this.description = description;
    }

    @Override public final String name() { return sqlName; }

    @Override public final String description() { return description; }

    @Override public final FunctionMetadata metadata() {
        return FunctionMetadata.describe(description).withCategories("hl7", "healthcare");
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
                    "The HL7 message type from MSH-9 (e.g. 'ADT^A01' or 'ADT^A01^ADT_A01').");
        }
        public void compute(@Vector(value = "message", any = true) FieldVector in, VarCharVector out) {
            run(in, out);
        }
    }

    /** {@code hl7_version(message)} — MSH-12 (e.g. '2.5'). */
    public static final class Version extends MshScalar {
        public Version() {
            super("hl7_version", 12, "The HL7 version ID from MSH-12 (e.g. '2.5').");
        }
        public void compute(@Vector(value = "message", any = true) FieldVector in, VarCharVector out) {
            run(in, out);
        }
    }

    /** {@code hl7_message_control_id(message)} — MSH-10. */
    public static final class ControlId extends MshScalar {
        public ControlId() {
            super("hl7_message_control_id", 10, "The message control ID from MSH-10.");
        }
        public void compute(@Vector(value = "message", any = true) FieldVector in, VarCharVector out) {
            run(in, out);
        }
    }
}
