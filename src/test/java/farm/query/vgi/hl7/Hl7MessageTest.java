package farm.query.vgi.hl7;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-parser unit tests: MSH-driven separators, numbering quirk, location parsing. */
class Hl7MessageTest {

    @Test void readsSeparatorsFromMsh() {
        Hl7Message m = Hl7Message.parse(Fixtures.adtA01());
        assertTrue(m.isValid());
        assertEquals('|', m.fieldSep());
        assertEquals('^', m.componentSep());
        assertEquals('~', m.repetitionSep());
        assertEquals('\\', m.escape());
        assertEquals('&', m.subcomponentSep());
    }

    @Test void mshOneIsSeparatorTwoIsEncoding() {
        Hl7Message m = Hl7Message.parse(Fixtures.adtA01());
        assertEquals("|", m.field("MSH", 0, 1), "MSH-1 is the field separator char");
        assertEquals("^~\\&", m.field("MSH", 0, 2), "MSH-2 is the encoding chars");
        assertEquals("ADT^A01", m.field("MSH", 0, 9));
        assertEquals("MSG00001", m.field("MSH", 0, 10));
        assertEquals("2.5", m.field("MSH", 0, 12));
    }

    @Test void customEncodingDrivesParsing() {
        Hl7Message m = Hl7Message.parse(Fixtures.customEncoding());
        assertTrue(m.isValid());
        assertEquals('#', m.fieldSep());
        assertEquals('*', m.componentSep());
        assertEquals('!', m.repetitionSep());
        assertEquals('/', m.escape());
        assertEquals('@', m.subcomponentSep());
        assertEquals("ADT*A01", m.field("MSH", 0, 9));
        assertEquals("SMITH", m.component(m.field("PID", 0, 5), 1));
    }

    @Test void malformedAndNullAreInvalid() {
        assertFalse(Hl7Message.parse(null).isValid());
        assertFalse(Hl7Message.parse("").isValid());
        assertFalse(Hl7Message.parse(Fixtures.malformed()).isValid());
        assertTrue(Hl7Message.parse(Fixtures.malformed()).segments().isEmpty());
    }

    @Test void locationParsing() {
        Hl7Location loc = Hl7Location.parse("PID-5.1");
        assertEquals("PID", loc.segment());
        assertEquals(0, loc.segmentRep());
        assertEquals(5, loc.field());
        assertEquals(1, loc.component());
        assertEquals(-1, loc.subcomponent());

        Hl7Location rep = Hl7Location.parse("DG1[2]-3");
        assertEquals("DG1", rep.segment());
        assertEquals(1, rep.segmentRep(), "1-based in text, 0-based stored");

        assertNull(Hl7Location.parse("garbage"));
        assertNull(Hl7Location.parse(null));
        assertNull(Hl7Location.parse("PID-0"));
    }

    @Test void resolveDrillsComponents() {
        Hl7Message m = Hl7Message.parse(Fixtures.adtA01());
        assertEquals("DOE", Hl7Location.parse("PID-5.1").resolve(m));
        assertEquals("JR", Hl7Location.parse("PID-5.4").resolve(m));
        assertEquals("123456^^^MRN", Hl7Location.parse("PID-3").resolve(m));
        assertNull(Hl7Location.parse("PID-3.9").resolve(m));
    }
}
