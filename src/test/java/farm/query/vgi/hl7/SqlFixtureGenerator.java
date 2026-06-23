package farm.query.vgi.hl7;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the committed SQL E2E fixtures under {@code test/sql/data/} from the same
 * canonical message builders the JUnit tests use, so the {@code .hl7} files are
 * reproducible from source rather than opaque committed text. The Makefile
 * {@code test-sql} target runs this (via the Gradle {@code generateSqlFixtures}
 * task) before haybarn-unittest.
 *
 * <p>Files are written with their literal {@code \r} (CR) segment separators
 * intact — that is the HL7 wire form, and DuckDB reads them back via the worker's
 * {@code read_text}-driven inputs in the .test files.
 *
 * <p>Usage: {@code SqlFixtureGenerator <output-dir>} (defaults to test/sql/data).
 */
public final class SqlFixtureGenerator {

    private SqlFixtureGenerator() {}

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : "test/sql/data");
        Files.createDirectories(dir);

        write(dir.resolve("adt_a01.hl7"), Fixtures.adtA01());
        write(dir.resolve("custom_encoding.hl7"), Fixtures.customEncoding());
        write(dir.resolve("crlf.hl7"), Fixtures.crlfMessage());
        write(dir.resolve("malformed.hl7"), Fixtures.malformed());

        System.out.println("Wrote HL7 SQL fixtures to " + dir.toAbsolutePath());
    }

    private static void write(Path p, String content) throws Exception {
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
    }
}
