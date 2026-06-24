package farm.query.vgi.hl7;

import farm.query.vgi.Worker;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * VGI worker entry point for HL7 v2.x clinical message parsing.
 *
 * <p>The parser is pure JDK (see {@link Hl7Message}); there is no HAPI HL7v2
 * dependency, keeping the worker MIT-licensed and dependency-light.
 *
 * <p>Attach from DuckDB with:
 * <pre>{@code
 * ATTACH 'hl7' (TYPE vgi, LOCATION 'java -jar vgi-hl7-all.jar');
 * SELECT * FROM hl7.hl7_segments('MSH|^~\&|...\rPID|...');
 * SELECT hl7.hl7_get(msg, 'PID-5.1') FROM messages;
 * }</pre>
 */
public final class Main {

    private Main() {}

    public static final String GIT_COMMIT =
            System.getenv("VGI_HL7_GIT_COMMIT") != null
                    ? System.getenv("VGI_HL7_GIT_COMMIT") : "unknown";

    /**
     * Catalog-level provenance/description tags surfaced to DuckDB and the
     * {@code vgi-lint} metadata-quality linter.
     */
    private static Map<String, String> catalogTags() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put(
                "vgi.description_llm",
                "Parse and query HL7 v2.x pipe-delimited clinical messages (ADT, ORU, ORM, "
                        + "etc.) in SQL. Split a message into its segments, explode it into long "
                        + "format (one row per field value with repetitions expanded), extract a "
                        + "single value by HL7 location like 'PID-5.1' or 'MSH-9', read the message "
                        + "type/version/control-id from MSH, and test whether text is a well-formed "
                        + "HL7 v2 message. Use to ingest, validate, and pull fields out of HL7 v2 "
                        + "feeds without a heavyweight HL7 library.");
        tags.put(
                "vgi.description_md",
                "# hl7\n\nParse HL7 v2.x pipe-delimited clinical messages into DuckDB rows over "
                        + "Apache Arrow (pure-Java parser, no HAPI dependency).\n\n"
                        + "**Table functions:** `hl7_segments` (one row per segment), "
                        + "`hl7_fields` (long format, one row per field value).\n\n"
                        + "**Scalars:** `hl7_get` (extract by location e.g. `PID-5.1`), "
                        + "`hl7_message_type` (MSH-9), `hl7_version` (MSH-12), "
                        + "`hl7_message_control_id` (MSH-10), `is_valid_hl7` (BOOLEAN).");
        tags.put("vgi.author", "Query.Farm");
        tags.put("vgi.copyright", "Copyright 2026 Query Farm LLC - https://query.farm");
        tags.put("vgi.license", "MIT");
        tags.put("vgi.support_contact", "https://github.com/Query-farm/vgi-hl7/issues");
        tags.put(
                "vgi.support_policy_url",
                "https://github.com/Query-farm/vgi-hl7/blob/main/README.md");
        return tags;
    }

    private static Map<String, String> schemaTags() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put(
                "vgi.description_llm",
                "HL7 v2.x message-parsing functions: split a message into segments, explode it "
                        + "into long-format field rows, extract a value by location (e.g. 'PID-5.1'), "
                        + "read MSH message type/version/control-id, and validate HL7 v2 text.");
        tags.put(
                "vgi.description_md",
                "HL7 v2.x clinical-message parsing functions (segments, fields, location "
                        + "extraction, MSH accessors, validation) over Apache Arrow.");
        return tags;
    }

    public static Worker buildWorker() {
        return Worker.builder()
                .catalogName("hl7")
                .implementationVersion(GIT_COMMIT)
                .catalogComment("HL7 v2.x clinical message parsing (pure-Java, no HAPI)")
                .catalogTags(catalogTags())
                .sourceUrl("https://github.com/Query-farm/vgi-hl7")
                .schemaComment("main", "HL7 v2.x message-parsing functions.")
                .schemaTags("main", schemaTags())
                .registerTable(new SegmentsFunction())
                .registerTable(new FieldsFunction())
                .registerScalar(new GetFunction())
                .registerScalar(new MshScalar.MessageType())
                .registerScalar(new MshScalar.Version())
                .registerScalar(new MshScalar.ControlId())
                .registerScalar(new IsValidFunction());
    }

    public static void main(String[] args) {
        String stderrPath = System.getenv("VGI_WORKER_STDERR");
        if (stderrPath != null && !stderrPath.isEmpty()) {
            try {
                java.io.PrintStream ps = new java.io.PrintStream(
                        new java.io.FileOutputStream(stderrPath, true), true);
                System.setErr(ps);
            } catch (Exception ignore) {
                // best-effort stderr redirect
            }
        }
        buildWorker().runFromArgs(args);
    }
}
