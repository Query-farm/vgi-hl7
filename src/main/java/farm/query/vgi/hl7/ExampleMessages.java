package farm.query.vgi.hl7;

import farm.query.vgi.catalog.View;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The browsable {@code hl7.main.example_messages} registry view (VGI146).
 *
 * <p>The two table functions ({@code hl7_segments}, {@code hl7_fields}) both
 * require a message argument, so an agent has nothing to scan before it knows how
 * to call them. This view is a small, curated, credential-free catalog of
 * ready-to-use sample HL7 v2 messages an agent (or human) can {@code SELECT}
 * straight away to see what a well-formed message looks like and to learn the
 * available message types. It is defined purely over an inline {@code VALUES}
 * list, so it scans with no arguments, no network, and no secrets.
 *
 * <p>Precomputed columns ({@code message_type}, {@code trigger_event},
 * {@code segment_count}) let an agent answer questions about the samples without
 * having to paste a full message into a table function — VGI table functions bind
 * only literal arguments, so a column reference cannot be fed into
 * {@code hl7_segments(...)} directly.
 */
final class ExampleMessages {

    private ExampleMessages() {}

    /**
     * Build the {@code example_messages} view: a SQL view over an inline
     * {@code VALUES} list of curated sample messages. The {@code message} column
     * carries the raw, CR-delimited HL7 text (the same literals the function
     * examples use); the other columns are precomputed facts about each sample.
     */
    static View view() {
        String definition =
                "SELECT * FROM (VALUES\n"
                        + "  ('adt_a01', 'ADT^A01', 'A01', 4, "
                        + "'Patient admission: a new inpatient admit carrying EVN, PID, and PV1 "
                        + "segments. The classic ADT (Admit/Discharge/Transfer) event.', "
                        + Examples.SAMPLE_MSG_SQL + "),\n"
                        + "  ('oru_r01', 'ORU^R01', 'R01', 5, "
                        + "'Unsolicited observation result: a laboratory report with an OBR order "
                        + "and two OBX result rows (a complete blood count).', "
                        + Examples.ORU_MSG_SQL + ")\n"
                        + ") AS t(name, message_type, trigger_event, segment_count, description, "
                        + "message)";

        String docLlm =
                "A curated, browsable registry of ready-to-use example HL7 v2 messages, one per "
                        + "row. Query it first to discover what message types this worker handles "
                        + "and to obtain a well-formed sample you can study or copy. Columns: "
                        + "`name` (a short slug id), `message_type` (the HL7 message type such as "
                        + "`ADT^A01` or `ORU^R01`), `trigger_event` (the HL7 trigger-event code "
                        + "such as `A01`), `segment_count` (how many segments the sample has), "
                        + "`description` (a one-line summary), and `message` (the raw, "
                        + "carriage-return-delimited HL7 text). The view takes no arguments and "
                        + "needs no credentials. Note: VGI table functions accept only literal "
                        + "arguments, so paste the `message` text inline into `hl7_segments`, "
                        + "`hl7_fields`, or `hl7_get` rather than passing this column as a "
                        + "subquery.";

        String docMd =
                "## example_messages\n\n"
                        + "A small, built-in catalog of sample **HL7 v2** messages you can query "
                        + "without supplying any input. It exists so you can see a well-formed "
                        + "message and the message types this worker understands before calling "
                        + "the parsing functions, which all require a message argument.\n\n"
                        + "Each row describes one sample: a slug `name`, the HL7 `message_type` "
                        + "(e.g. `ADT^A01`, `ORU^R01`), the `trigger_event` code, a precomputed "
                        + "`segment_count`, a short `description`, and the raw CR-delimited "
                        + "`message` text itself. Browse it, sort by `segment_count`, or copy a "
                        + "`message` literal into `hl7_segments`, `hl7_fields`, or `hl7_get` to "
                        + "experiment.\n\n"
                        + "The view is defined over an inline `VALUES` list, so it scans instantly "
                        + "with no network access and no credentials.";

        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("vgi.title", "Example HL7 v2 Messages");
        tags.put("vgi.doc_llm", docLlm);
        tags.put("vgi.doc_md", docMd);
        tags.put(
                "vgi.keywords",
                Meta.keywordsJson(
                        "example, examples, sample, samples, registry, catalog, hl7, hl7 v2, "
                                + "ADT, ORU, message type, trigger event, browse, demo"));
        tags.put("vgi.category", "reference");
        // VGI123 classifying tags — BARE keys (not vgi.-namespaced), a small reused
        // vocabulary matching the schema's own domain/topic facets.
        tags.put("domain", "healthcare");
        tags.put("topic", "hl7-v2-clinical-messaging");
        tags.put(
                "vgi.example_queries",
                Examples.exampleQueriesTag(
                        "SELECT name, message_type, trigger_event, segment_count "
                                + "FROM hl7.main.example_messages ORDER BY segment_count DESC, name;",
                        "List the built-in sample messages, richest first, showing their type, "
                                + "trigger event, and segment count."));

        Map<String, String> columnComments = new LinkedHashMap<>();
        columnComments.put("name", "Short slug identifier for the sample (e.g. `adt_a01`).");
        columnComments.put(
                "message_type",
                "HL7 message type as `type^event` (e.g. `ADT^A01`, `ORU^R01`).");
        columnComments.put(
                "trigger_event", "HL7 trigger-event code for the sample (e.g. `A01`, `R01`).");
        columnComments.put(
                "segment_count", "Number of segments in the sample message (a small integer count).");
        columnComments.put("description", "One-line human summary of what the sample represents.");
        columnComments.put(
                "message", "Raw, carriage-return-delimited HL7 v2 message text for the sample.");

        return new View(
                        "main",
                        "example_messages",
                        definition,
                        "Curated, browsable registry of ready-to-use sample HL7 v2 messages.",
                        tags)
                .withColumnComments(columnComments);
    }
}
