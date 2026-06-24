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
     * Guaranteed-runnable, catalog-qualified examples (VGI509) attached to one
     * object. Each {@code sql} is self-contained and re-runnable against an
     * attached {@code hl7} worker; {@code expected_result} is omitted on purpose
     * (the linter only needs each query to execute cleanly).
     */
    static final String EXECUTABLE_EXAMPLES =
            "[\n"
                    + "  {\n"
                    + "    \"description\": \"Split an admit message into its segments in order.\",\n"
                    + "    \"sql\": \"SELECT seq, segment, field_count FROM hl7.main.hl7_segments("
                    + SAMPLE_MSG_SQL + ") ORDER BY seq\"\n"
                    + "  },\n"
                    + "  {\n"
                    + "    \"description\": \"Explode the PID segment into long-format field rows.\",\n"
                    + "    \"sql\": \"SELECT field, repetition, value FROM hl7.main.hl7_fields("
                    + SAMPLE_MSG_SQL + ") WHERE segment = 'PID' ORDER BY field\"\n"
                    + "  },\n"
                    + "  {\n"
                    + "    \"description\": \"Extract the patient family name (component 1 of PID-5).\",\n"
                    + "    \"sql\": \"SELECT hl7.main.hl7_get(" + SAMPLE_MSG_SQL + ", 'PID-5.1') AS family_name\"\n"
                    + "  },\n"
                    + "  {\n"
                    + "    \"description\": \"Read the message type, version, and control id from MSH.\",\n"
                    + "    \"sql\": \"SELECT hl7.main.hl7_message_type(" + SAMPLE_MSG_SQL + ") AS type, "
                    + "hl7.main.hl7_version(" + SAMPLE_MSG_SQL + ") AS version, "
                    + "hl7.main.hl7_message_control_id(" + SAMPLE_MSG_SQL + ") AS control_id\"\n"
                    + "  },\n"
                    + "  {\n"
                    + "    \"description\": \"Validate that a well-formed message parses.\",\n"
                    + "    \"sql\": \"SELECT hl7.main.is_valid_hl7(" + SAMPLE_MSG_SQL + ") AS ok\"\n"
                    + "  }\n"
                    + "]";

    /** Representative catalog-qualified example queries for the schema (VGI506), a plain string. */
    static final String SCHEMA_EXAMPLE_QUERIES =
            "SELECT * FROM hl7.main.hl7_segments(" + SAMPLE_MSG_SQL + ");\n"
                    + "SELECT segment, field, repetition, value FROM hl7.main.hl7_fields("
                    + SAMPLE_MSG_SQL + ") WHERE segment = 'PID';\n"
                    + "SELECT hl7.main.hl7_get(" + SAMPLE_MSG_SQL + ", 'PID-5.1');\n"
                    + "SELECT hl7.main.hl7_message_type(" + SAMPLE_MSG_SQL + ");\n"
                    + "SELECT hl7.main.hl7_version(" + SAMPLE_MSG_SQL + ");\n"
                    + "SELECT hl7.main.hl7_message_control_id(" + SAMPLE_MSG_SQL + ");\n"
                    + "SELECT hl7.main.is_valid_hl7(" + SAMPLE_MSG_SQL + ");";

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
