package farm.query.vgi.hl7;

/**
 * Shared example-query fragments for function {@code metadata().examples}.
 *
 * <p>HL7 v2.x messages are CR-delimited, so the SQL literals use DuckDB's
 * {@code E'...'} escape syntax with {@code \r} between segments. The sample is a
 * minimal but realistic {@code ADT^A01} (patient admit) message.
 */
final class Examples {

    private Examples() {}

    /**
     * A minimal ADT^A01 admit message as a DuckDB {@code E'...'} string literal,
     * CR-delimited. PID-5 (patient name) is {@code Doe^John}.
     */
    static final String SAMPLE_MSG_SQL =
            "E'MSH|^~\\\\&|SENDING_APP|SENDING_FAC|RECV_APP|RECV_FAC|"
                    + "20240101120000||ADT^A01|MSG00001|P|2.5\\rEVN|A01|20240101120000\\r"
                    + "PID|1||10001^^^HOSP^MR||Doe^John||19800101|M\\rPV1|1|I'";

    /**
     * Build the value for a {@code vgi.example_queries} tag: a JSON array of
     * {@code {"sql", "description"}} objects. The {@code pairs} are flat
     * {@code sql, description, sql, description, …}. The linter (vgi-lint)
     * decodes this tag to count and surface a function's example queries.
     */
    static String exampleQueriesTag(String... pairs) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"sql\":").append(jsonString(pairs[i]))
                    .append(",\"description\":").append(jsonString(pairs[i + 1]))
                    .append('}');
        }
        return sb.append(']').toString();
    }

    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
