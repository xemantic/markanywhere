# Markanywhere Parser — Open Work

Snapshot of remaining test gaps after the link/label work, organized by user
impact for the parser's core use case (real-time rendering of streamed LLM
markdown). Numbers are spec-example test counts in the suite; "Impact"
estimates how often the construct shows up in typical LLM output.

---

## Next-batch candidates (recommended order)

### 1. GFM §6.7 — Images (15 tests, **HIGH impact**)

Image edge cases beyond what Phase 1–3 covered. Many likely benefit from the
same label-content + URL-state-machine work links got.

**Sample failing tests**: 582–598 (multi-line image refs and labels), 601/602
(`!foo` non-image cases). Similar label-buffering and URL-parsing infrastructure
applies — though the alt-text rendering rule (plain text only) keeps it
simpler than full link content.

**Where**: `Gfm_06_07_Test`. Re-run after auditing — some may already pass after
Phase 3a/3b that I didn't re-verify.

### 2. GFM §6.10 — Disallowed Raw HTML extension (12 tests, **MEDIUM impact**)

GFM filters specific HTML tags (e.g. `<title>`, `<textarea>`, `<style>`,
`<xmp>`) by emitting them as `&lt;...&gt;` text instead of as inline-HTML
marks. Defensive sanitisation of LLM-produced HTML — useful but not critical.

**Scope**: a hard-coded tag name set, applied in `tryParseOpenTag` /
`tryParseCloseTag` to redirect those tags to literal-text emission.

### 3. Full HTML5 named entity table (GFM §6.2, **MEDIUM impact**)

`NAMED_ENTITIES` in `MarkanywhereParser.kt` (around line 470) is a hand-picked
16-entry subset covering only the names exercised by the §6.2 test suite. The
spec defers to the WHATWG HTML5 named character references list — ~2,231
entries — and CommonMark §6.2 explicitly requires that any name in that list
decode, while names outside it must be left literal.

LLM output regularly emits names not in our subset: `mdash`, `ndash`,
`hellip`, `ldquo`/`rdquo`, `lsquo`/`rsquo`, `trade`, `reg`, `deg`, `times`,
`divide`, `pound`, `euro`, `larr`/`rarr`/`uarr`/`darr`, `harr`, `infin`,
`plusmn`, `sup2`/`sup3`, `frac12`/`frac14`, Greek letters, etc. Each of these
currently flows through as literal source.

**Authoritative source**: `https://html.spec.whatwg.org/entities.json`
(name → one or two codepoints; case-sensitive — `Amp` ≠ `amp`).

**Recommended approach**: Gradle codegen task that fetches/parses
`entities.json` at build time and writes a `NamedEntities.kt` into
`build/generated/...`. Keeps the source small and the data authoritative.
Resource loading is not uniform across JS/Wasm/Native (KMP-purity rule), so
codegen beats runtime resource lookup here.

**Memory cost**: ~60 KB of static strings — negligible on JVM/Native, slightly
larger Wasm/JS bundle. If bundle size matters, pack as sorted name array +
codepoint array with binary-search lookup.

**Interim option**: extend the static map to the ~50–100 entities LLMs
actually emit, before committing to full codegen.

**Why not delegate to JS DOM on browser targets?** The browser ships a full
HTML5 entity decoder reachable via `textarea.innerHTML` / `DOMParser` /
`Document.parseHTMLUnsafe`, but it's a poor fit:

- **Browser-only**: not in Node, Deno, Bun (without `jsdom`/`linkedom`), Wasm,
  or Native. KMP `commonMain` would need `expect`/`actual` and every non-
  browser target still needs the codegen table — so the JS-browser branch is
  carrying-cost only.
- **Performance**: per-entity DOM allocation in a streaming hot path means an
  `innerHTML` setter + DOM tree mutation per `&name;`. A static map lookup is
  orders of magnitude cheaper with zero GC pressure.
- **Security**: `innerHTML` executes side effects on certain inputs
  (`<img onerror=…>`); a pure lookup table has no such surface.
- **Spec drift**: browser entity tables track living-standard HTML and differ
  across vendors/versions; CommonMark pins to a snapshot. Routing through the
  DOM would make decoding non-deterministic across JS runtimes.

Conclusion: codegen the table once, ship it to every KMP target uniformly.

---

## Remaining link/label cases (LOW priority, mostly DIVERGENCE)

These are tracked in `Gfm_06_06_Test` and represent fundamental architectural
limits of the streaming model. Each requires a substantial change for marginal
real-world value. Recommended: leave as DIVERGENCE.

### Multi-line links (3 tests: 501, 504, 519)

`[link](url\n  "title")` — title on next line, label spanning newline, etc.
Requires `flushInline` to NOT abort `inLink/inLinkUrl` at line breaks AND a
re-feed-through-inline mechanism on abort so HTML tags inside the aborted
source can still be detected.

LLMs essentially never split a `[…](…)` link across newlines.

### Image-inside-link / nested image (2 tests: 526, 529)

`[![alt](src)](url)` — clickable image. Needs recursive label parsing — opening
a nested capture for the inner `![…](…)` while still tracking the outer label.

LLMs occasionally emit clickable images in README-style output. Low frequency.

### Em ↔ link delimiter scoping (1 test: 530)

`*[foo*](/uri)` — leading `*` should pair only with `*`s outside the label.
Currently the in-label `*` resolver can reach back into the outer
`inlineOpenStack` and close an outer em, producing unbalanced events.

Fix: scope the resolver to frames above `linkLabelOuterStackDepth`. Possible,
but invasive change to `resolveEmphasisRun`.

LLMs essentially never produce this pattern.

---

## Notes for whoever picks this up

- Run `./gradlew :markanywhere-parse:jvmTest 2>&1 | grep "tests completed"` to
  see current count. Baseline at the start of link work was 205 failures; the
  link work shipped at 63. §6.8 standard autolinks then closed (5 spec
  compliance fixes + 1 marked DIVERGENCE for the custom-markup `<ns:name>`
  extension), bringing the count to 57. §6.9 extended autolinks closed all
  14 of its tests (and updated 3 §6.8 tests whose expectations became
  obsolete once bare URLs/emails autolink), bringing the count to 43. §6.12
  hard-line-break work then closed 2 isolated fixes (669, 671) and converted
  6 multi-line tests to DIVERGENCE markers (the underlying constraint —
  inline state can't span `\n` — is documented in CLAUDE.md and shared
  with §6.1/§6.3), bringing the count to 36. §6.11 disallowed-raw-HTML
  closed (added `GFM_DISALLOWED_TAGS` set applied in `tokenizeHtmlLine` and
  inline `<…>` parsing) and §6.14 example 675 closed (tightened math-open
  rule: `$` now opens math only when followed by a letter, `\`, or `{`,
  preserving `hello $.;'there` as plain text); current count is 33 failures.
- The systematic divergences are documented in `CLAUDE.md` under "Streaming
  divergences" — read that before starting any of the link cases above.
- Most failures map cleanly to a single GFM section, so each work stream is
  fairly independent.
