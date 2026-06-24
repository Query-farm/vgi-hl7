package farm.query.vgi.hl7;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared helpers for the per-object discovery/description metadata that the
 * {@code vgi-lint} strict profile (0.26.0) expects on <em>every</em> function and
 * table.
 *
 * <p>Each function/table surfaces these in its {@code FunctionMetadata.tags}:
 * <ul>
 *   <li>{@code vgi.title} (VGI124) — human-friendly display name (must not
 *       normalize-equal the machine name).</li>
 *   <li>{@code vgi.doc_llm} (VGI112) — a Markdown narrative aimed at an
 *       LLM/agent audience.</li>
 *   <li>{@code vgi.doc_md} (VGI113) — a Markdown narrative aimed at human docs
 *       (distinct content from {@code vgi.doc_llm}).</li>
 *   <li>{@code vgi.keywords} (VGI126) — comma-separated search terms/synonyms.</li>
 *   <li>{@code vgi.source_url} (VGI128) — link to the implementing source file.</li>
 * </ul>
 */
final class Meta {

    private Meta() {}

    /** Base GitHub blob URL for source files in this repo (pinned to {@code main}). */
    private static final String SOURCE_BASE =
            "https://github.com/Query-farm/vgi-hl7/blob/main/src/main/java/farm/query/vgi/hl7";

    /** Build the {@code vgi.source_url} for a source file, e.g. {@code "GetFunction.java"}. */
    static String sourceUrl(String relativePath) {
        return SOURCE_BASE + "/" + relativePath;
    }

    /**
     * Build the five standard per-object discovery/description tags into a mutable
     * map (so callers can add {@code vgi.result_columns_md} / executable examples).
     *
     * @param title       human display name (VGI124)
     * @param docLlm      Markdown narrative for LLMs (VGI112)
     * @param docMd       Markdown narrative for human docs (VGI113), distinct from docLlm
     * @param keywords    comma-separated search terms (VGI126)
     * @param sourceFile  implementing file relative to the package dir (VGI128)
     */
    static Map<String, String> objectTags(
            String title, String docLlm, String docMd, String keywords, String sourceFile) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("vgi.title", title);
        tags.put("vgi.doc_llm", docLlm);
        tags.put("vgi.doc_md", docMd);
        tags.put("vgi.keywords", keywords);
        tags.put("vgi.source_url", sourceUrl(sourceFile));
        return tags;
    }
}
