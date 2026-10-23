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
     * A minimal ORU^R01 (unsolicited observation result) message as a DuckDB
     * {@code E'...'} literal, CR-delimited: an OBR order plus two OBX result
     * segments. Used to give the browsable {@code example_messages} view a second,
     * structurally different sample beyond the ADT admit.
     */
    static final String ORU_MSG_SQL =
            "E'MSH|^~\\\\&|LAB|HOSP|EHR|HOSP|20240101123000||ORU^R01|MSG00002|P|2.5\\r"
                    + "PID|1||10001^^^HOSP^MR||Doe^John||19800101|M\\r"
                    + "OBR|1||ORD123|CBC^Complete Blood Count\\r"
                    + "OBX|1|NM|WBC^White Blood Cell Count||7.2|10*3/uL|4.0-11.0|N\\r"
                    + "OBX|2|NM|HGB^Hemoglobin||14.1|g/dL|13.5-17.5|N'";

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

    /**
     * The same canonical ADT^A01 admit message as {@link #SAMPLE_MSG_SQL}, but as
     * plain human-readable HL7 text (segments separated by newlines) for embedding
     * in {@code vgi.agent_test_tasks} prompts shown to the analyst.
     */
    static final String SAMPLE_MSG_TEXT =
            "MSH|^~\\&|SENDING_APP|SENDING_FAC|RECV_APP|RECV_FAC|20240101120000||ADT^A01|MSG00001|P|2.5\n"
                    + "EVN|A01|20240101120000\n"
                    + "PID|1||10001^^^HOSP^MR||Doe^John||19800101|M\n"
                    + "PV1|1|I";

    /**
     * The {@code vgi.agent_test_tasks} suite (VGI152/VGI407/VGI920): a JSON array
     * of {@code {name, prompt}} tasks that {@code vgi-lint simulate} runs an LLM
     * analyst through to grade how discoverable/usable the worker is. Each prompt
     * embeds a deterministic HL7 message ({@link #SAMPLE_MSG_TEXT}). The private
     * grader fields ({@code reference_sql}, {@code success_criteria},
     * {@code ignore_column_names}, {@code unordered}) live in
     * {@code vgi-agent-tests.yaml} at the repo root, keyed by task name, and are
     * never published to the analyst (VGI416).
     */
    static final String AGENT_TEST_TASKS = buildAgentTestTasks();

    private static String buildAgentTestTasks() {
        return "["
                + taskJson(
                        "list_segments",
                        "Here is an HL7 v2 message:\n" + SAMPLE_MSG_TEXT + "\n\n"
                                + "Return a single column listing each segment's 3-letter "
                                + "identifier (for example MSH, PID), one row per segment.")
                + ","
                + taskJson(
                        "pid_fields_long",
                        "Here is an HL7 v2 message:\n" + SAMPLE_MSG_TEXT + "\n\n"
                                + "Explode the PID segment into long format: return the 1-based "
                                + "field number and the field value, one row per field value.")
                + ","
                + taskJson(
                        "patient_family_name",
                        "Given this HL7 v2 message:\n" + SAMPLE_MSG_TEXT + "\n\n"
                                + "What is the patient's family (last) name? Return the single "
                                + "value.")
                + ","
                + taskJson(
                        "message_type_and_version",
                        "For this HL7 v2 message:\n" + SAMPLE_MSG_TEXT + "\n\n"
                                + "Return its message type and its HL7 version number as a single "
                                + "row of two columns.")
                + ","
                + taskJson(
                        "message_control_id",
                        "Given this HL7 v2 message:\n" + SAMPLE_MSG_TEXT + "\n\n"
                                + "Return its message control id (the value of MSH-10) as a single "
                                + "value.")
                + ","
                + taskJson(
                        "validate_message",
                        "Is the following text a well-formed HL7 v2 message?\n" + SAMPLE_MSG_TEXT
                                + "\n\nReturn the single boolean validation result.")
                + ","
                + taskJson(
                        "list_example_messages",
                        "This worker ships a built-in catalog of ready-to-use example HL7 v2 "
                                + "messages. List the message type of each built-in example, one "
                                + "per row.")
                + "]";
    }

    private static String taskJson(String name, String prompt) {
        return "{"
                + "\"name\":" + jsonString(name) + ","
                + "\"prompt\":" + jsonString(prompt)
                + "}";
    }

    /**
     * Representative catalog-qualified example queries for the schema (VGI506), as
     * a JSON array of {@code {"sql", "description"}} objects so every example
     * carries a non-empty human-readable description (VGI515).
     */
    static final String SCHEMA_EXAMPLE_QUERIES =
            exampleQueriesTag(
                    "SELECT seq, segment, field_count FROM hl7.main.hl7_segments("
                            + SAMPLE_MSG_SQL + ") ORDER BY seq",
                    "Split an admit message into its segments, in message order.",
                    "SELECT segment, field, repetition, value FROM hl7.main.hl7_fields("
                            + SAMPLE_MSG_SQL + ") WHERE segment = 'PID' ORDER BY field, repetition",
                    "Explode the PID segment into long-format field rows.",
                    "SELECT hl7.main.hl7_get(" + SAMPLE_MSG_SQL + ", 'PID-5.1') AS family_name",
                    "Extract the patient family name (component 1 of PID-5) by location.",
                    "SELECT hl7.main.hl7_message_type(" + SAMPLE_MSG_SQL + ") AS message_type",
                    "Read the message type (MSH-9) for routing a mixed feed.",
                    "SELECT hl7.main.hl7_version(" + SAMPLE_MSG_SQL + ") AS version",
                    "Read the HL7 version id (MSH-12) the message claims to conform to.",
                    "SELECT hl7.main.hl7_message_control_id(" + SAMPLE_MSG_SQL + ") AS control_id",
                    "Read the message control id (MSH-10) used for ACK correlation and dedupe.",
                    "SELECT hl7.main.is_valid_hl7(" + SAMPLE_MSG_SQL + ") AS ok",
                    "Validate that a well-formed HL7 v2 message parses.");

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
