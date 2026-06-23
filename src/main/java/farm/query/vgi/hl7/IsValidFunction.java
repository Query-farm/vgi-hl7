package farm.query.vgi.hl7;

import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
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
        return FunctionMetadata.describe(description()).withCategories("hl7", "healthcare", "validation");
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
