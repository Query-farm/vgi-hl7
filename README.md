<p align="center">
  <img src="docs/vgi-logo.png" alt="Vector Gateway Interface (VGI)" width="320">
</p>

<p align="center"><em>A <a href="https://query.farm">Query.Farm</a> VGI worker for DuckDB.</em></p>

# vgi-hl7

A [VGI](https://query.farm) worker that parses **HL7 v2.x** clinical messages —
the pipe-delimited format used across healthcare for ADT, ORU, ORM, and other
clinical events — into DuckDB rows you can query with SQL.

The parser is **pure Java** (JDK only). HL7 v2 is a delimited text format, so
there is no third-party HL7 library: in particular this worker does **not** use
HAPI, keeping it MIT-licensed and dependency-light (the VGI SDK is the only
runtime dependency).

```sql
ATTACH 'hl7' (TYPE vgi, LOCATION 'java -jar vgi-hl7-all.jar');

-- One row per segment
SELECT * FROM hl7.hl7_segments(msg);

-- Extract a value by HL7 location
SELECT hl7.hl7_get(msg, 'PID-5.1') AS family_name,
       hl7.hl7_message_type(msg)   AS message_type,
       hl7.hl7_version(msg)        AS version
FROM messages;
```

## Input

Every function accepts the HL7 message as either:

- a **VARCHAR** carrying the message text directly (HL7 messages are short, so
  they travel inline — this is *not* a file path), or
- a **BLOB** carrying the message bytes (decoded as UTF-8).

`NULL` input yields `NULL` (scalars) or no rows (table functions). Malformed input
(no `MSH` header) yields `is_valid_hl7 = false`, `NULL` from the accessors, and no
rows from the table functions — never a crash.

## Functions

### Table functions

| Function | Output columns | Description |
|---|---|---|
| `hl7_segments(message)` | `seq INT, segment VARCHAR, field_count INT, raw VARCHAR` | One row per segment, in message order. |
| `hl7_fields(message)` | `segment VARCHAR, segment_rep INT, field INT, repetition INT, value VARCHAR` | Long format: one row per field value, with field repetitions expanded. Component structure is preserved as the raw field text. |

`segment_rep` (0-based) disambiguates repeated segments (e.g. multiple `OBX` or
`DG1`). `field` is the 1-based HL7 field number; `repetition` is the 0-based field
repetition index.

### Scalar functions

| Function | Returns | Description |
|---|---|---|
| `hl7_get(message, location)` | `VARCHAR` | Extract a value by HL7 location: `'PID-5'` (field), `'PID-5.1'` (component), `'MSH-9.1'`, or with a segment repetition `'DG1[2]-3'`. Returns the first repetition of a repeating field. `NULL` if absent. |
| `hl7_message_type(message)` | `VARCHAR` | `MSH-9` (e.g. `'ADT^A01'`). |
| `hl7_version(message)` | `VARCHAR` | `MSH-12` (e.g. `'2.5'`). |
| `hl7_message_control_id(message)` | `VARCHAR` | `MSH-10`. |
| `is_valid_hl7(message)` | `BOOLEAN` | True when the input parses as a well-formed HL7 v2 message (begins with an `MSH` defining the separators). |

## How the parser works

HL7 v2 is **MSH-driven**: the separators are declared in the message itself, not
fixed by the standard. The parser reads them rather than hardcoding:

- A message is **segments separated by `\r`** (CR; `\r\n` and `\n` are tolerated).
- The first segment is `MSH`. **MSH-1 is the field separator** (the first
  character after `MSH`, canonically `|`), and **MSH-2 is the encoding
  characters** (component / repetition / escape / subcomponent, canonically
  `^~\&`).
- Fields split on the field separator; repetitions on `~`; components on `^`;
  subcomponents on `&` — all as read from MSH-1/MSH-2.

Because MSH-1 *is* the separator character, MSH's fields carry a one-position
numbering offset that the worker handles transparently: you address `MSH-9`,
`MSH-12`, etc. by their canonical HL7 numbers, and `hl7_get('MSH-2')` returns the
encoding characters verbatim.

A message with non-default encoding characters (a custom MSH-2) parses correctly,
because the separators come from the message:

```sql
-- field sep '#', component '*', repetition '!', escape '/', subcomponent '@'
SELECT hl7.hl7_get(
  'MSH#*!/@#APP#FAC#...#ADT*A01#MSG2#P#2.5' || chr(13) || 'PID#1##...##SMITH*JANE',
  'PID-5.1');   -- -> SMITH
```

## Build & test

```sh
./gradlew test     # JUnit: parser + table functions + scalars
make test-sql      # fat JAR + fixtures + haybarn-unittest E2E over test/sql/*
make test          # both
make build         # fat JAR -> build/libs/vgi-hl7-<ver>-all.jar
```

The SQL E2E runner is `haybarn-unittest` (`uv tool install haybarn-unittest`, then
`export PATH="$HOME/.local/bin:$PATH"`). The committed `test/sql/data/*.hl7`
fixtures are reproducible from source via `make fixtures`.

The VGI Java SDK (`farm.query:vgi`, `farm.query:vgirpc`) resolves from **Maven
Central**, so the build is fully self-contained — no sibling checkout, no
`mavenLocal`.

## License

MIT. See `LICENSE`. No HAPI / GPL/LGPL HL7 dependency.

---

## Authorship & License

Written by [Query.Farm](https://query.farm) — every VGI worker is designed and built by Query.Farm.

Copyright 2026 Query Farm LLC - https://query.farm

