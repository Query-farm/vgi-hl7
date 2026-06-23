package farm.query.vgi.hl7;

/**
 * Canonical HL7 v2 message fixtures shared by the JUnit tests and the SQL E2E
 * fixture generator. Segments are CR-separated, as on the wire.
 */
public final class Fixtures {

    private Fixtures() {}

    private static final char CR = '\r';

    /**
     * A standard ADT^A01 (admit) message, HL7 v2.5, default encoding chars
     * {@code ^~\&}. PID-5 (patient name) carries components; PID-3 (identifier
     * list) carries two repetitions; PV1 carries components too.
     */
    public static String adtA01() {
        return String.join(String.valueOf(CR),
                "MSH|^~\\&|SENDINGAPP|SENDINGFAC|RECEIVINGAPP|RECEIVINGFAC|20240101120000||ADT^A01|MSG00001|P|2.5",
                "EVN|A01|20240101120000",
                "PID|1||123456^^^MRN~789012^^^SSN||DOE^JOHN^Q^JR^DR||19700101|M|||123 MAIN ST^^METROPOLIS^NY^10001",
                "PV1|1|I|WEST^101^1^GENHOSP||||1234^SMITH^WILLIAM^A^^DR|||SUR")
                + CR;
    }

    /**
     * The same logical message but with non-default encoding characters: field
     * separator {@code #}, component {@code *}, repetition {@code !}, escape
     * {@code /}, subcomponent {@code @}. A MSH-driven parser must read these from
     * MSH-1/MSH-2 rather than assume the canonical set.
     */
    public static String customEncoding() {
        return String.join(String.valueOf(CR),
                "MSH#*!/@#SENDINGAPP#SENDINGFAC#RECEIVINGAPP#RECEIVINGFAC#20240101120000##ADT*A01#MSG00002#P#2.5",
                "PID#1##222333*^*^*MRN##SMITH*JANE*M#19800202#F")
                + CR;
    }

    /** A message with CRLF line endings, to exercise tolerant segment splitting. */
    public static String crlfMessage() {
        return "MSH|^~\\&|APP|FAC|RAPP|RFAC|20240101120000||ORU^R01|CTRL9|P|2.4\r\n"
                + "PID|1||999^^^MRN||PATIENT^TEST\r\n";
    }

    /** Not an HL7 message — no MSH header. */
    public static String malformed() {
        return "this is not an HL7 message at all";
    }
}
