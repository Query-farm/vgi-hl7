# HL7 notebooks

[01 · Keep repeated identifiers and their namespaces](01_preserve_identifier_repetitions.ipynb)
uses seven invented payload rows to demonstrate the worker's message metadata,
scalar accessors, segment table and expanded field table. It keeps message and field
repetition identity, reads declared separators, and charts which identifiers survive
a first-repetition view. All identifiers are synthetic; there are no real patient
records, clinical interpretations, external services, or identity matches.

The default retains six nonempty identifier occurrences across three messages.
Changing `INCLUDE_REPEATS` to `False` keeps three occurrences and removes the LAB and
RESEARCH namespaces from the selected view. Distinct bare strings change from four
to two, while distinct sample keys including authority and type change from six to
three. Neither measure counts people. All original and expanded rows remain available.

## Run

Requirements: [uv](https://docs.astral.sh/uv/), Java 21 or newer on `PATH`, and macOS
or Linux for the companion's POSIX cache lock. On macOS with Homebrew Java, add
`/opt/homebrew/opt/openjdk@21/bin` to `PATH` if needed. The initial run needs network
access for Python dependencies, the VGI extension, and the approximately 34 MB JAR.

From the repository root:

```sh
uv sync --project notebooks --frozen
uv run --project notebooks --frozen python notebooks/execute.py
```

The complete `notebooks/` directory also runs independently of the worker checkout:

```sh
cd notebooks
uv sync --frozen
uv run --frozen python execute.py
```

In an editor, select `notebooks/.venv/bin/python`, use `notebooks/` as the kernel
working directory, and restart/run all. Connection SQL, data and the analytical
parameter remain visible. Import, plotting, assertion and cleanup code is folded;
useful outputs remain visible. Charts use Seaborn styling and Matplotlib.

Run the one-parameter experiment into a separate directory:

```sh
uv run --project notebooks --frozen python notebooks/execute.py \
  --parameters '{"INCLUDE_REPEATS":false}' \
  --output-dir notebooks/validation/first-only
```

The runner starts a fresh kernel, rejects unknown parameters, and requires a separate
output directory for overrides. It replaces a saved notebook only after successful
execution. Review PNGs are written under ignored `notebooks/validation/charts/` and
embedded in the notebook.

## Portable worker connection

The visible connection uses this literal location:

```sql
ATTACH 'hl7' (TYPE vgi,
 LOCATION 'uv run --frozen --project . python release_jar.py Query-farm/vgi-hl7 v0.4.0 vgi-hl7-v0.4.0.jar ec9a95f3f5f81a905402151d0eba493012e00ea4d7f54e3f03b0969c97c45633');
```

[`release_jar.py`](release_jar.py) downloads the public
[v0.4.0 JAR](https://github.com/Query-farm/vgi-hl7/releases/tag/v0.4.0),
checks its pinned SHA-256, and starts Java. It checks cached bytes on every launch,
uses a bounded lock to serialize downloads, and publishes files atomically. The cache
is `${XDG_CACHE_HOME:-~/.cache}/vgi-notebook-jars/`. No Gradle, worker source build,
credential, or sibling checkout is required. The separately locked notebook environment
uses Haybarn 2.0.0rc11; repository Java dependencies remain unchanged.

## Parsing and counting limits

`is_valid_hl7` is the parser's acceptance flag, not a complete conformance validator.
The header-only sample is accepted but has no control ID or PID-3. Missing PID-3,
present empty PID-3, parser rejection, and SQL NULL remain distinct. Missing comparison
counts are not replaced by zeros; the present empty field has a known zero nonempty
identifier count.

The scalar `hl7_get` returns the first field repetition. In this pinned release that
rule also truncates MSH-2 to its prefix. The notebook displays that behavior and reads
the complete encoding-character field from `hl7_fields` instead. Each scalar location
is materialized in a separate SQL statement to avoid the current engine's documented
constant-argument expression-merging issue. The field table preserves MSH-1 and MSH-2
verbatim and exposes zero-based segment and field repetition indices.

The sample's identifier components are intentionally simple. SQL splits each raw
repetition using the declared component separator, including the custom `#` / `*`
message. There are no escaped values or nested authority subcomponents. This is not
general CX validation, escape decoding, namespace governance or entity resolution.
The sample key `(authority, identifier_type, identifier_text)` illustrates namespace
separation without claiming universal identity or uniqueness.

Twelve SQL checks cover row identity, invalid/null behavior, empty/absent fields,
verbatim encoding fields, first-repetition consistency, custom separators, expanded
row grain and selection/count reconciliation. Validation includes default execution,
a detached notebooks-only first-repetition experiment, chart review, runner guards
and notebook lint. Source checks passed 17 JUnit tests, 68 stdio / 74 HTTP / 68 Unix
SQL assertions across three cases per transport, and executed metadata lint at
100/100 L2 with zero findings. Source checks build the worker for testing; notebook
execution uses the released JAR.

The worker is MIT licensed. Unmodified official assets link to
[Query.Farm](https://query.farm/) and [VGI](https://query.farm/vgi/).
