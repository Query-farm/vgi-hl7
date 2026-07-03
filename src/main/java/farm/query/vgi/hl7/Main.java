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
        tags.put("vgi.title", "HL7 v2.x Clinical Message Parser");
        tags.put(
                "vgi.keywords",
                Meta.keywordsJson(
                        "hl7, hl7 v2, hl7v2, healthcare, clinical, interoperability, ADT, ORU, "
                                + "ORM, MSH, PID, segment, field, pipe-delimited, parse, message, "
                                + "EHR, EMR, HL7 location"));
        tags.put(
                "vgi.doc_llm",
                "Parse and query HL7 v2.x pipe-delimited clinical messages (ADT, ORU, ORM, "
                        + "etc.) in SQL. Split a message into its segments, explode it into long "
                        + "format (one row per field value with repetitions expanded), extract a "
                        + "single value by HL7 location like 'PID-5.1' or 'MSH-9', read the message "
                        + "type/version/control-id from MSH, and test whether text is a well-formed "
                        + "HL7 v2 message. Use to ingest, validate, and pull fields out of HL7 v2 "
                        + "feeds without a heavyweight HL7 library.");
        tags.put(
                "vgi.doc_md",
                "# HL7 v2.x Clinical Message Parsing in SQL\n\n"
                        + "![HL7 International logo](https://brandguide.hl7.org/wp-content/uploads/sites/5/2025/07/HL7-V2Primary-Logo600px.png)\n\n"
                        + "Parse, validate, and query **HL7 v2.x** pipe-delimited clinical messages "
                        + "directly in DuckDB SQL — turn raw ADT, ORU, and ORM feeds into queryable "
                        + "rows over Apache Arrow, with no ETL pipeline and no heavyweight HL7 "
                        + "library to install.\n\n"
                        + "This extension is built for healthcare data engineers, interoperability "
                        + "and integration teams, and anyone who needs to ingest HL7 v2 messages "
                        + "from EHR/EMR systems, interface engines, or message archives and analyze "
                        + "them with plain SQL. HL7 v2 is the most widely deployed healthcare "
                        + "messaging standard in the world, and its segment/field/component/"
                        + "subcomponent layout is notoriously fiddly to parse by hand. Reach for it "
                        + "whenever a clinical feed arrives as raw pipe-delimited text and you want "
                        + "to JOIN, filter, and aggregate its fields the same way you would any "
                        + "other table — rather than pre-processing the messages in an external "
                        + "language first.\n\n"
                        + "The core concepts mirror the HL7 v2 grammar. A **message** is a sequence "
                        + "of carriage-return–separated **segments** (MSH, EVN, PID, PV1, OBX, ...), "
                        + "each of which is a list of **fields**; a field can carry repetitions and "
                        + "decomposes further into **components** and **subcomponents**. Individual "
                        + "elements are addressed by an **HL7 location** path such as `PID-5.1` "
                        + "(component 1 of field 5 of the PID segment) or `MSH-9`, optionally with a "
                        + "segment repetition like `DG1[2]-3`. Indices are 1-based per HL7 "
                        + "convention.\n\n"
                        + "Under the hood the parser is a compact, pure-Java (pure-JDK) "
                        + "implementation — there is **no HAPI dependency**, keeping the worker "
                        + "MIT-licensed and dependency-light. It is fully **MSH-driven**: the field "
                        + "separator (MSH-1) and encoding characters (MSH-2: component, repetition, "
                        + "escape, and subcomponent delimiters) are read from each message rather "
                        + "than assumed, so non-standard delimiters are handled correctly. Malformed "
                        + "input never crashes the worker — it simply parses as invalid and yields "
                        + "no rows, so the functions are safe to run across dirty, mixed-quality "
                        + "feeds.\n\n"
                        + "The format and message grammar follow the official **HL7 v2** standard "
                        + "published by [Health Level Seven International](https://www.hl7.org); see "
                        + "the [HL7 v2 product brief and documentation]("
                        + "https://www.hl7.org/implement/standards/product_brief.cfm?product_id=185) "
                        + "for the canonical segment, field, and message-type definitions. Source "
                        + "code, examples, and issues live in the "
                        + "[Query-farm/vgi-hl7 repository](https://github.com/Query-farm/vgi-hl7).");
        tags.put("vgi.author", "Query.Farm");
        tags.put("vgi.copyright", "Copyright 2026 Query Farm LLC - https://query.farm");
        tags.put("vgi.license", "MIT");
        tags.put("vgi.support_contact", "https://github.com/Query-farm/vgi-hl7/issues");
        tags.put(
                "vgi.support_policy_url",
                "https://github.com/Query-farm/vgi-hl7/blob/main/README.md");
        // VGI152/VGI407/VGI920: agent-suitability suite for `vgi-lint simulate`.
        tags.put("vgi.agent_test_tasks", Examples.AGENT_TEST_TASKS);
        return tags;
    }

    private static Map<String, String> schemaTags() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("vgi.title", "HL7 v2.x Parsing — main");
        tags.put(
                "vgi.keywords",
                Meta.keywordsJson(
                        "hl7, hl7 v2, healthcare, clinical, segments, fields, hl7_get, "
                                + "hl7_segments, hl7_fields, hl7_message_type, hl7_version, "
                                + "is_valid_hl7, MSH, PID, location"));
        // VGI123 classifying tags — BARE keys (not vgi.-namespaced).
        tags.put("domain", "healthcare");
        tags.put("category", "parsing");
        tags.put("topic", "hl7-v2-clinical-messaging");
        // VGI139: source_url lives only on the catalog object (Worker.sourceUrl);
        // not repeated per-schema/per-object.
        tags.put(
                "vgi.doc_llm",
                "HL7 v2.x message-parsing functions: split a message into segments, explode it "
                        + "into long-format field rows, extract a value by location (e.g. 'PID-5.1'), "
                        + "read MSH message type/version/control-id, and validate HL7 v2 text. Use "
                        + "these to ingest and query pipe-delimited clinical feeds directly in SQL.");
        tags.put(
                "vgi.doc_md",
                "## HL7 v2.x parsing functions\n\n"
                        + "Clinical-message parsing over Apache Arrow. The functions in this schema "
                        + "fall into three capability areas that mirror how you work with an HL7 v2 "
                        + "feed.\n\n"
                        + "**Parsing** turns a raw message into rows: one row per segment, or a "
                        + "long-format explosion with one row per field value (repetitions "
                        + "expanded), so you can query a message set-wise instead of by hand.\n\n"
                        + "**Extraction** pulls out individual values addressed by an HL7 location "
                        + "path (such as `PID-5.1` or `MSH-9`), including the common MSH header "
                        + "accessors for message type, version, and control id.\n\n"
                        + "**Validation** tests whether text is a well-formed HL7 v2 message, which "
                        + "is handy for filtering dirty feeds before parsing. The parser is pure-JDK "
                        + "and MSH-driven — the field separator and encoding characters are read "
                        + "from each message rather than assumed.");
        // VGI413/VGI408/VGI409: ordered category registry for schema navigation/SEO.
        // Each object joins by its `vgi.category` name.
        tags.put(
                "vgi.categories",
                "[{\"name\":\"parsing\",\"title\":\"Parsing\","
                        + "\"description\":\"Explode a message into segment or long-format field rows.\"},"
                        + "{\"name\":\"extraction\",\"title\":\"Extraction\","
                        + "\"description\":\"Pull individual values out by HL7 location or MSH header accessor.\"},"
                        + "{\"name\":\"validation\",\"title\":\"Validation\","
                        + "\"description\":\"Test whether text is a well-formed HL7 v2 message.\"}]");
        tags.put("vgi.example_queries", Examples.SCHEMA_EXAMPLE_QUERIES);
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
