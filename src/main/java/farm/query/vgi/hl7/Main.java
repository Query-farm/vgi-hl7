package farm.query.vgi.hl7;

import farm.query.vgi.Worker;

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

    public static Worker buildWorker() {
        return Worker.builder()
                .catalogName("hl7")
                .implementationVersion(GIT_COMMIT)
                .catalogComment("HL7 v2.x clinical message parsing (pure-Java, no HAPI)")
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
