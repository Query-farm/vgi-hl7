package farm.query.vgi.hl7;

import farm.query.vgi.function.Arguments;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Coverage for hl7_segments and hl7_fields over a known ADT^A01 message. */
class TableFunctionsTest {

    /** Build single-positional Arguments carrying a VARCHAR message value. */
    private static Arguments msgArgs(String message) {
        return new Arguments(
                Arrays.asList(message == null ? null : new org.apache.arrow.vector.util.Text(message)),
                Map.of(),
                List.of(new ArrowType.Utf8()));
    }

    @Test void segmentsRowsInOrderWithFieldCounts() {
        TestSupport.Result r = TestSupport.invoke(new SegmentsFunction(), msgArgs(Fixtures.adtA01()));
        List<Map<String, Object>> rows = r.rows();
        assertEquals(4, rows.size(), "MSH, EVN, PID, PV1");

        assertEquals(1, rows.get(0).get("seq"));
        assertEquals("MSH", rows.get(0).get("segment"));
        assertEquals("EVN", rows.get(1).get("segment"));
        assertEquals("PID", rows.get(2).get("segment"));
        assertEquals("PV1", rows.get(3).get("segment"));

        // MSH-1 and MSH-2 count toward the field count (HL7 numbering): the MSH
        // line has 11 canonical fields (MSH-1..MSH-12 with MSH-11/12 = P / 2.5).
        assertEquals(12, rows.get(0).get("field_count"));
        // raw carries the whole segment text.
        assertTrue(rows.get(2).get("raw").toString().startsWith("PID|1||"));
    }

    @Test void fieldsLongFormatExpandsRepetitions() {
        TestSupport.Result r = TestSupport.invoke(new FieldsFunction(), msgArgs(Fixtures.adtA01()));
        List<Map<String, Object>> rows = r.rows();

        // PID-3 has two repetitions (123456^^^MRN and 789012^^^SSN) -> two rows.
        long pid3 = rows.stream()
                .filter(m -> "PID".equals(m.get("segment")) && Integer.valueOf(3).equals(m.get("field")))
                .count();
        assertEquals(2, pid3, "PID-3 expands into two repetition rows");

        // The second repetition of PID-3 carries the SSN identifier value.
        String pid3rep1 = rows.stream()
                .filter(m -> "PID".equals(m.get("segment"))
                        && Integer.valueOf(3).equals(m.get("field"))
                        && Integer.valueOf(1).equals(m.get("repetition")))
                .map(m -> String.valueOf(m.get("value")))
                .findFirst().orElse(null);
        assertEquals("789012^^^SSN", pid3rep1);

        // PID-5 (patient name) is a single value preserving component structure.
        String pid5 = rows.stream()
                .filter(m -> "PID".equals(m.get("segment")) && Integer.valueOf(5).equals(m.get("field")))
                .map(m -> String.valueOf(m.get("value")))
                .findFirst().orElse(null);
        assertEquals("DOE^JOHN^Q^JR^DR", pid5);

        // MSH-2 (encoding chars) must survive verbatim, NOT be split on '~'.
        String msh2 = rows.stream()
                .filter(m -> "MSH".equals(m.get("segment")) && Integer.valueOf(2).equals(m.get("field")))
                .map(m -> String.valueOf(m.get("value")))
                .findFirst().orElse(null);
        assertEquals("^~\\&", msh2);
    }

    @Test void nullMessageYieldsNoRows() {
        assertEquals(0, TestSupport.invoke(new SegmentsFunction(), msgArgs(null)).totalRows());
        assertEquals(0, TestSupport.invoke(new FieldsFunction(), msgArgs(null)).totalRows());
    }

    @Test void malformedMessageYieldsNoRows() {
        assertEquals(0, TestSupport.invoke(new SegmentsFunction(), msgArgs(Fixtures.malformed())).totalRows());
        assertEquals(0, TestSupport.invoke(new FieldsFunction(), msgArgs(Fixtures.malformed())).totalRows());
    }

    @Test void customEncodingParsesSegments() {
        TestSupport.Result r = TestSupport.invoke(new SegmentsFunction(), msgArgs(Fixtures.customEncoding()));
        List<Map<String, Object>> rows = r.rows();
        assertEquals(2, rows.size(), "MSH and PID parsed under custom '#' field sep");
        assertEquals("MSH", rows.get(0).get("segment"));
        assertEquals("PID", rows.get(1).get("segment"));
    }
}
