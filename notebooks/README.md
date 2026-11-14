# HL7 v2 Parser notebooks

[01 · Keep repeated identifiers and their namespaces](01_preserve_identifier_repetitions.ipynb)
asks what disappears when a repeating field is reduced to its first value. The
worker's shipped sample registry establishes the declared header context; seven
separate invented payloads then exercise repeated PID identifiers, custom separators,
empty/absent fields, malformed text and SQL NULL. Visible SQL preserves row identity
and namespace information; two SVG charts compare the selected view with all
identifier occurrences. No real patient records or external service are involved.

## Run locally or in Colab

Install Git and [uv](https://docs.astral.sh/uv/), then run from this directory:

```sh
uv sync --locked
uv run --locked vgi-notebooks 01_preserve_identifier_repetitions.ipynb --timeout 600
```

Use this directory's `.venv/bin/python` in a notebook editor, or open the notebook's
**Open in Colab** badge and run all cells. The bootstrap installs only base
[shared tooling v0.3.15](https://github.com/Query-farm/vgi-example-notebooks/tree/v0.3.15),
preserving compatible preloaded pandas/Matplotlib/IPython versions. Local publication
adds `[runner]`; Colab does not. A fresh preloaded Python 3.12 kernel is tested with
the complete notebook; hosted Colab is not manually exercised.

The notebook runs without the worker checkout. Shared registry `hl7` retrieves the
immutable launcher and script lock when they are absent. The proper catalog title
links to the worker repository; unchanged official branding appears at both ends.
Python setup/plotting code is folded, with parameters, connection SQL, analytical
SQL and useful results visible. GitHub displays linked SVG assets; interactive
notebook reruns display native SVG.

## Released JAR and managed Java

```sql
INSTALL vgi FROM community;
LOAD vgi;
ATTACH 'hl7' (TYPE vgi,
 LOCATION 'uv run --locked --no-project --python 3.13 hl7_release.py v0.4.0');
```

The reviewed [launcher](hl7_release.py) and [script lock](hl7_release.py.lock) are
registered at immutable public commit `53265ad261b889899bb978eb383f68ebac146cb7`.
The wrapper accepts only its reviewed **v0.4.0** release, corresponding to worker
source `5f5eccef160080fc6be65a2f9111157219c769e9`. It downloads the approximately
34 MB `vgi-hl7-v0.4.0.jar` and requires SHA-256
`ec9a95f3f5f81a905402151d0eba493012e00ea4d7f54e3f03b0969c97c45633`.
Cached bytes are verified every launch; a bounded POSIX lock and atomic publication
protect `${XDG_CACHE_HOME:-~/.cache}/vgi-notebook-jars/` from partial downloads.

A separate PEP 723 environment pins `jdk4py==21.0.8.2`, supplying **Java 21.0.8**.
The launcher uses that package's absolute Java path and checks the binary's actual
version before starting the worker. It ignores system Java and does not add Java
to the notebook kernel. The script lock records managed-runtime wheel hashes.
Supported platforms are macOS and glibc Linux on x86-64/arm64, including Colab's
Linux platform; this POSIX launcher does not support Windows.

No Gradle, engine build or local worker build is needed. First use downloads Python
packages, the runtime, JAR and VGI extension; parsing then runs locally. Haybarn
**2.0.0rc11** is locked separately. SQL starts with an empty temporary secret directory
and persistent secrets disabled. There are no credentials or account operations.

Optional runtime/artifact preflight:

```sh
uv run --locked --no-project --python 3.13 hl7_release.py --check-runtime v0.4.0
uv run --locked --no-project --python 3.13 hl7_release.py --verify-only v0.4.0
```

Changing the release requires reviewing the artifact, digest and runtime compatibility,
then rerunning the default and experiment. Real SQL startup was tested with system
Java absent and with a fake Java 11 on PATH; neither was used.

## One-parameter experiment

```sh
uv run --locked vgi-notebooks 01_preserve_identifier_repetitions.ipynb \
  --parameters '{"INCLUDE_REPEATS":false}' --output-dir validation/first-only --timeout 600
```

The default keeps six nonempty identifier occurrences across three messages. The
experiment keeps three occurrences and removes the LAB and RESEARCH namespaces
from the selected view. Distinct bare strings change from four to two; sample keys
including authority and type change from six to three. Neither measure counts people.
The original and expanded rows remain available. Restore `True` for the default.

The runner starts fresh kernels, rejects unknown parameters and requires a separate
destination for overrides. A failed execution preserves the last successful default.
Detached copies include SVG assets and a README. Logs, review PNGs and experiments
are ignored; successful notebook outputs, coverage evidence and `charts/` SVGs are
published.

## Interpretation and limits

The shipped `example_messages` view contains synthetic ADT and ORU examples. Their
headers declare version 2.5. This walkthrough borrows the ADT shape and leaves
observation-result interpretation out of scope. Header accessors read declared
text; they do not validate a complete profile. Message control IDs and repeated
PID identifiers have different roles. The authored `message_key` preserves every
input row even when the sender's control ID is missing.

`is_valid_hl7` reports parser acceptance, not full conformance. The short-header
sample is accepted but has no control ID or PID-3. Missing PID-3, present empty
PID-3, rejection and SQL NULL remain distinct. Missing counts are not changed to
zero; the present empty field has a known zero nonempty identifier count.

The scalar `hl7_get` returns the first field repetition. In this release that rule
also truncates MSH-2 to its prefix. The notebook shows this limitation and reads the
complete encoding characters from `hl7_fields`. Each scalar location is materialized
in a separate SQL statement to avoid the published engine's documented
constant-argument expression-merging issue. The segment table retains source order;
the field table retains zero-based segment/field repetition indices and verbatim
MSH-1/MSH-2 values. Field numbers are one-based.

The sample's components are deliberately simple. SQL uses the declared component
separator, including C's custom `#`/`*` message, rather than assuming `^`. There are
no escaped values or nested authority subcomponents. This is not general CX
validation, escape decoding, namespace governance or entity resolution. The sample
key `(authority, identifier_type, identifier_text)` illustrates namespace separation
without asserting universal identity, uniqueness or clinical validity.

Twelve SQL checks cover row identity, invalid/null behavior, empty/absent fields,
verbatim encoding fields, first-repetition consistency, custom separators, expanded
row grain and selection/count reconciliation. Their success describes the sample's
parsing and accounting, not an identity match or clinical assessment.

## Coverage and validation

[coverage.json](coverage.json) maps all **8/8** logical public surfaces to successful
visible SQL and reviewed interpretations. Full native function/table/view exports
include the `example_messages` view; overloads and same-named aliases count once.
No capability exclusions are applied.

Release v0.4.0 and current public main `e7b1d954bb2b49d1626f8d25cff17a4f4082c179`
have the same eight-name interface. Current source adds composed metadata examples;
the parser and selected computations are unchanged. The current catalog was collected
from its existing built JAR without rebuilding. Notebook execution uses the released
JAR: exact-runtime coverage is **8 released / 0 current**; editorial compatibility
review supports **8/8 current-interface** coverage without claiming a current-source
notebook run.

This notebook-only change reuses successful source CI
[43571384303](https://github.com/Query-farm/vgi-hl7/actions/runs/43571384303).
Worker sources, generated fixtures and root build dependencies remain untouched.
Notebook default/experiment, portable preloaded setup, chart review, runner guards,
Ruff/format and hash-bound coverage receipts are checked separately.
