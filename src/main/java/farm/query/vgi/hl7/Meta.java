package farm.query.vgi.hl7;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared helpers for the per-object discovery/description metadata that the
 * {@code vgi-lint} strict profile expects on <em>every</em> function and table.
 *
 * <p>Each function/table surfaces these in its {@code FunctionMetadata.tags}:
 * <ul>
 *   <li>{@code vgi.title} (VGI124) — human-friendly display name (must not
 *       normalize-equal the machine name).</li>
 *   <li>{@code vgi.doc_llm} (VGI112) — a Markdown narrative aimed at an
 *       LLM/agent audience.</li>
 *   <li>{@code vgi.doc_md} (VGI113) — a Markdown narrative aimed at human docs
 *       (distinct content from {@code vgi.doc_llm}).</li>
 *   <li>{@code vgi.keywords} (VGI126/VGI138) — a JSON array of search terms.</li>
 * </ul>
 *
 * <p>Per-object {@code vgi.source_url} is intentionally NOT emitted here: VGI139
 * wants {@code source_url} only on the catalog object (set via
 * {@code Worker.builder().sourceUrl(...)}).
 */
final class Meta {

    private Meta() {}

    /**
     * Render a comma-or-space-separated keyword list as a JSON array string,
     * e.g. {@code "a, b"} -> {@code ["a","b"]}, so {@code vgi.keywords} satisfies
     * VGI138 (must be a JSON array of strings, not a delimited string).
     */
    static String keywordsJson(String keywords) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String raw : keywords.split(",")) {
            String kw = raw.trim();
            if (kw.isEmpty()) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(kw.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
    }

    /**
     * Build the four standard per-object discovery/description tags into a mutable
     * map (so callers can add {@code vgi.result_columns_md} / executable examples).
     *
     * @param title    human display name (VGI124)
     * @param docLlm   Markdown narrative for LLMs (VGI112)
     * @param docMd    Markdown narrative for human docs (VGI113), distinct from docLlm
     * @param keywords comma-separated search terms, serialized as a JSON array (VGI138)
     */
    static Map<String, String> objectTags(
            String title, String docLlm, String docMd, String keywords) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("vgi.title", title);
        tags.put("vgi.doc_llm", docLlm);
        tags.put("vgi.doc_md", docMd);
        tags.put("vgi.keywords", keywordsJson(keywords));
        return tags;
    }
}
