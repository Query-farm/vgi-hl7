package farm.query.vgi.hl7;

import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VarBinaryVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.util.Text;

import java.nio.charset.StandardCharsets;

/**
 * Resolves the polymorphic message argument shared by every function: a
 * {@code VARCHAR} carrying the HL7 message <em>text</em> directly, or a
 * {@code BLOB}/{@code BINARY} carrying the message <em>bytes</em>.
 *
 * <p>Unlike a document worker, a VARCHAR here is the literal message content (not
 * a file path) — HL7 messages are short text, so they travel inline. NULL input
 * yields {@code null} (the caller maps that to a NULL/no-rows result).
 */
public final class MessageInput {

    private MessageInput() {}

    /** Decode a constant scalar argument value into the message string, or null. */
    public static String fromArgument(Object value, ArrowType type) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] b) {
            return decode(b);
        }
        if (value instanceof Text t) {
            return t.toString();
        }
        if (value instanceof String s) {
            // Some transports box BLOBs as Strings — honour the declared type.
            if (type instanceof ArrowType.Binary || type instanceof ArrowType.LargeBinary) {
                return decode(s.getBytes(StandardCharsets.ISO_8859_1));
            }
            return s;
        }
        return value.toString();
    }

    /** Decode one cell of an any-typed scalar vector into a message string, or null. */
    public static String at(FieldVector in, int row) {
        if (in.isNull(row)) {
            return null;
        }
        if (in instanceof VarCharVector s) {
            Object o = s.getObject(row);
            return o == null ? null : o.toString();
        }
        if (in instanceof VarBinaryVector b) {
            return decode(b.get(row));
        }
        return fromArgument(in.getObject(row), in.getField().getType());
    }

    /**
     * Decode message bytes. HL7 v2 is typically ASCII / ISO-8859-1; we try UTF-8
     * (a superset of ASCII) which is the common modern wire encoding.
     */
    private static String decode(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
