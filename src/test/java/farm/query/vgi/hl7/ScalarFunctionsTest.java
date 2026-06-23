package farm.query.vgi.hl7;

import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.util.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Coverage for hl7_get, hl7_message_type, hl7_version, hl7_message_control_id, is_valid_hl7. */
class ScalarFunctionsTest {

    private final RootAllocator alloc = new RootAllocator();

    @AfterEach void tearDown() { alloc.close(); }

    private VarCharVector msgVec(String... values) {
        VarCharVector v = new VarCharVector("message", alloc);
        v.allocateNew();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) v.setNull(i);
            else v.setSafe(i, new Text(values[i]));
        }
        v.setValueCount(values.length);
        return v;
    }

    private VarCharVector out() {
        VarCharVector v = new VarCharVector("out", alloc);
        v.allocateNew();
        return v;
    }

    private String getAt(VarCharVector v, int i) {
        return v.isNull(i) ? null : v.getObject(i).toString();
    }

    // ---- hl7_get -----------------------------------------------------------

    @Test void getFieldComponentAndSubcomponent() {
        try (VarCharVector in = msgVec(Fixtures.adtA01(), null, Fixtures.malformed());
             VarCharVector out = out()) {
            // PID-5.1 = family name.
            new GetFunction().compute(in, "PID-5.1", out);
            assertEquals("DOE", getAt(out, 0));
            assertNull(getAt(out, 1), "null message -> null");
            assertNull(getAt(out, 2), "malformed -> null");
        }
        try (VarCharVector in = msgVec(Fixtures.adtA01()); VarCharVector out = out()) {
            new GetFunction().compute(in, "PID-5.2", out);
            assertEquals("JOHN", getAt(out, 0), "PID-5.2 = given name");
        }
        try (VarCharVector in = msgVec(Fixtures.adtA01()); VarCharVector out = out()) {
            // PID-3 whole field -> first repetition raw value.
            new GetFunction().compute(in, "PID-3", out);
            assertEquals("123456^^^MRN", getAt(out, 0));
        }
        try (VarCharVector in = msgVec(Fixtures.adtA01()); VarCharVector out = out()) {
            // MSH-9.1 = message code (ADT).
            new GetFunction().compute(in, "MSH-9.1", out);
            assertEquals("ADT", getAt(out, 0));
        }
        try (VarCharVector in = msgVec(Fixtures.adtA01()); VarCharVector out = out()) {
            // Absent element -> NULL (no crash).
            new GetFunction().compute(in, "PID-99", out);
            assertNull(getAt(out, 0));
        }
    }

    @Test void getWithCustomEncodingChars() {
        try (VarCharVector in = msgVec(Fixtures.customEncoding()); VarCharVector out = out()) {
            // Under field sep '#', component '*': PID-5.1 family name = SMITH.
            new GetFunction().compute(in, "PID-5.1", out);
            assertEquals("SMITH", getAt(out, 0));
        }
    }

    // ---- MSH accessors -----------------------------------------------------

    @Test void messageTypeVersionControlId() {
        try (VarCharVector in = msgVec(Fixtures.adtA01(), null); VarCharVector out = out()) {
            new MshScalar.MessageType().compute(in, out);
            assertEquals("ADT^A01", getAt(out, 0));
            assertNull(getAt(out, 1));
        }
        try (VarCharVector in = msgVec(Fixtures.adtA01()); VarCharVector out = out()) {
            new MshScalar.Version().compute(in, out);
            assertEquals("2.5", getAt(out, 0));
        }
        try (VarCharVector in = msgVec(Fixtures.adtA01()); VarCharVector out = out()) {
            new MshScalar.ControlId().compute(in, out);
            assertEquals("MSG00001", getAt(out, 0));
        }
    }

    @Test void messageTypeUnderCustomEncoding() {
        try (VarCharVector in = msgVec(Fixtures.customEncoding()); VarCharVector out = out()) {
            new MshScalar.MessageType().compute(in, out);
            // MSH-9 under component sep '*' is 'ADT*A01' (raw field).
            assertEquals("ADT*A01", getAt(out, 0));
        }
    }

    // ---- is_valid_hl7 ------------------------------------------------------

    @Test void isValidTrueForGoodFalseForMalformed() {
        try (VarCharVector in = msgVec(Fixtures.adtA01(), null, Fixtures.malformed(), "");
             BitVector out = new BitVector("out", alloc)) {
            out.allocateNew();
            new IsValidFunction().compute(in, out);
            assertTrue(out.get(0) != 0, "ADT^A01 is valid");
            assertTrue(out.isNull(1), "null -> null");
            assertFalse(out.get(2) != 0, "malformed -> false");
            assertFalse(out.get(3) != 0, "empty -> false");
        }
    }

    @Test void isValidTrueForCustomEncodingAndCrlf() {
        try (VarCharVector in = msgVec(Fixtures.customEncoding(), Fixtures.crlfMessage());
             BitVector out = new BitVector("out", alloc)) {
            out.allocateNew();
            new IsValidFunction().compute(in, out);
            assertTrue(out.get(0) != 0, "custom encoding is valid");
            assertTrue(out.get(1) != 0, "CRLF endings tolerated");
        }
    }
}
