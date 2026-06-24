# CLAUDE.md — vgi-hl7

Contributor/agent notes. User-facing docs live in `README.md`; this is the
"how it's built and where the sharp edges are" companion.

## What this is

A [VGI](https://query.farm) worker (Java) that parses **HL7 v2.x** pipe-delimited
clinical messages into DuckDB rows, as SQL table and scalar functions. Modeled on
the sibling `vgi-tika` / `vgi-poi` / `vgi-ical` workers; built with Gradle (Kotlin
DSL, JDK 21) into a shaded fat JAR. Catalog name `hl7` (single `main` schema).

## Licensing — pure parser, NO HAPI

HL7 v2 is a delimited **text** format, so the parser is **pure JDK** — there is no
HL7 library dependency. We deliberately do **not** use the HAPI HL7v2 library,
whose licensing is murky for commercial use. The only runtime dependencies are the
VGI SDK (`farm.query:vgi` + `farm.query:vgirpc`) and `slf4j-simple` (stderr
logging). This keeps vgi-hl7 **MIT** and dependency-light.

## The parser (Hl7Message) — MSH-driven, not hardcoded

`Hl7Message.parse(String)` never throws and never returns null; malformed input
yields an `isValid() == false` message with no segments.

- A message is **segments separated by `\r`** (CR). We normalize `\r\n` and `\n`
  too, since real feeds are sloppy about line endings.
- The first segment is **MSH**. Its separators are read from the message, not
  assumed:
  - **MSH-1** is the **field separator** — the first char after `MSH` (e.g. `|`).
  - **MSH-2** is the **encoding characters** — component / repetition / escape /
    subcomponent (e.g. `^~\&`). We read whatever is present and default any
    missing position to the canonical set.
- Field = split by field-sep; repetition = split by MSH-2[1]; component = split by
  MSH-2[0]; subcomponent = split by MSH-2[3]. All splits are **literal** (the
  separators are regex metacharacters), via `splitLiteral`.

### The MSH numbering quirk (the one real sharp edge)

Because MSH-1 *is* the field separator character, MSH's fields are numbered with a
one-position offset versus every other segment. `parseSegment` reconstructs the
MSH segment so that `field("MSH", 0, 1)` returns the separator and
`field("MSH", 0, 2)` returns the encoding chars — i.e. callers address `MSH-9`,
`MSH-10`, `MSH-12` by their canonical HL7 numbers. `hl7_fields` likewise emits
MSH-1/MSH-2 as single verbatim values and does **not** re-split MSH-2 on `~`
(it literally contains `~`).

`Hl7Location` parses `'PID-5'`, `'PID-5.1'`, `'MSH-9.1.2'`, and segment reps
`'DG1[2]-3'` / `'DG1(2)-3'`; field/component/subcomponent are 1-based.

## Layout

```
build.gradle.kts / settings.gradle.kts / gradle.properties   Gradle, shadow plugin (com.gradleup.shadow 9.4.2)
src/main/java/farm/query/vgi/hl7/
  Main.java                  Worker.builder().catalogName("hl7")...registerTable/registerScalar
  Hl7Message.java            the pure-JDK parser (MSH-driven separators, numbering quirk)
  Hl7Location.java           parse + resolve 'PID-5.1' style locations
  MessageInput.java          VARCHAR-text-or-BLOB-bytes input dispatch (message inline, not a path)
  Hl7Schemas.java            Arrow schemas + batch builders for the two table fns
  SegmentsFunction.java      table fn: hl7_segments(message)
  FieldsFunction.java        table fn: hl7_fields(message) — long format, reps expanded
  GetFunction.java           scalar: hl7_get(message, location)
  MshScalar.java             scalars: hl7_message_type / hl7_version / hl7_message_control_id
  IsValidFunction.java       scalar: is_valid_hl7(message) -> BOOLEAN
src/test/java/...            JUnit: Hl7MessageTest, TableFunctionsTest, ScalarFunctionsTest
                             + Fixtures.java (canonical ADT^A01 etc.) + SqlFixtureGenerator + TestSupport
test/sql/*.test + data/      haybarn-unittest E2E + committed generated .hl7 fixtures
Makefile                     build / fixtures / test-unit / test-sql / test / clean
```

## Sharp edges

1. **MSH numbering offset** — see above. The most common parser bug for HL7.
2. **Don't split MSH-1/MSH-2 on the encoding separators.** MSH-2 *is* `^~\&`;
   splitting it on `~` corrupts it. `hl7_fields` special-cases `MSH` fields 1–2.
3. **VARCHAR is the message, not a path.** Unlike a document worker, a VARCHAR
   argument carries the HL7 text inline (`MessageInput`), and a BLOB carries the
   bytes (decoded UTF-8). Both are accepted on every function.
4. **VGI table functions can't take a subquery argument** (`Binder Error: Table
   function cannot contain subqueries`). So `segments.test` passes the message as
   an inline `E'...\r...'` literal; the committed `.hl7` fixtures are read via
   `read_text(...)` only in `scalars.test`, where a subquery arg is allowed.
5. **`Add-Opens: java.base/java.nio`** is baked into the fat-JAR manifest (Arrow
   needs it); the message arg is `any`-typed so a VARCHAR binds from SQL.
6. **`haybarn-unittest` skips `require vgi`** — `.test` files use explicit
   `LOAD vgi;`.
7. **NULL → NULL / no rows; malformed → false / no rows, never a crash.** Every
   function guards on `MessageInput` returning null and on parser validity.

## SDK dependency & CI (self-contained via Maven Central)

Depends on `farm.query:vgi:0.5.0` (pulls in `farm.query:vgirpc:0.10.2`
transitively; declared explicitly since the code imports `farm.query.vgirpc.*`).
**On Maven Central**, so the build is fully self-contained — no sibling checkout,
no `mavenLocal`, no composite build. `.github/workflows/test.yml` runs a
`build-and-test` job (JUnit + shadowJar + HTTP boot smoke test), a 3-transport
SQL E2E `integration` matrix, and a `metadata-quality` job that runs `vgi-lint`
(`Query-farm/vgi-lint-check@v1`, `fail-on: info`) against the built fat JAR.

- The in-process test driver `TestSupport` constructs `TableInitParams` directly,
  so an SDK bump can require appending trailing `null`s (vgi 0.4.0 added
  `atUnit` / `atValue` / `storage`). Get the exact constructor with
  `javap -cp <vgi jar> farm.query.vgi.table.TableInitParams`.

## Testing

```sh
./gradlew test                # JUnit: parser + table fns (via TestSupport) + scalars
make test-sql                 # shadowJar + fixtures + haybarn-unittest over test/sql/*
make test                     # both
```
Fixtures are reproducible via `make fixtures` (Gradle `generateSqlFixtures`,
`SqlFixtureGenerator`) — the `.hl7` files are written from the same `Fixtures`
builders the JUnit tests use.
