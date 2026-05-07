# Markanywhere Parser — Open Work

Snapshot of remaining test gaps after the link/label work, organized by user
impact for the parser's core use case (real-time rendering of streamed LLM
markdown). Numbers are spec-example test counts in the suite; "Impact"
estimates how often the construct shows up in typical LLM output.

---

## Next-batch candidates (recommended order)

### 1. GFM §6.9 — Autolinks extension (14 tests, **HIGH impact**)

Bare URLs and emails: `www.example.com`, `http://example.com`, `foo@bar.com`,
`mailto:foo@bar.com` etc. recognised mid-text without `<>` brackets.

LLMs emit these constantly and users expect them clickable. Currently the
parser only recognises explicit `<http://...>` autolinks (§6.8, partially).

**Scope**: a tokeniser pass over paragraph text that detects URL / email
boundary-respecting patterns and emits `<a href="...">` mark/unmark around the
matched run. The detection heuristics are the GFM-defined "extended autolink"
rules (valid domain, trailing punctuation stripping, `_` boundary
constraints).

**Where**: probably a new pass in `processInlineCharImpl`'s text-emit fall-
through, or a post-process step that scans emitted paragraph text for URL
shapes — needs care to interact cleanly with the streaming text-event flow
and the existing `<...>` autolink handler.

**Sample failing tests**: 622 (`www.commonmark.org` → `<a>`), 623 (with
trailing punctuation), 626 (URL with query string), 630 (bare email).

### 2. GFM §6.7 — Images (15 tests, **HIGH impact**)

Image edge cases beyond what Phase 1–3 covered. Many likely benefit from the
same label-content + URL-state-machine work links got.

**Sample failing tests**: 582–598 (multi-line image refs and labels), 601/602
(`!foo` non-image cases). Similar label-buffering and URL-parsing infrastructure
applies — though the alt-text rendering rule (plain text only) keeps it
simpler than full link content.

**Where**: `Gfm_06_07_Test`. Re-run after auditing — some may already pass after
Phase 3a/3b that I didn't re-verify.

### 3. GFM §6.10 — Disallowed Raw HTML extension (12 tests, **MEDIUM impact**)

GFM filters specific HTML tags (e.g. `<title>`, `<textarea>`, `<style>`,
`<xmp>`) by emitting them as `&lt;...&gt;` text instead of as inline-HTML
marks. Defensive sanitisation of LLM-produced HTML — useful but not critical.

**Scope**: a hard-coded tag name set, applied in `tryParseOpenTag` /
`tryParseCloseTag` to redirect those tags to literal-text emission.

### 4. GFM §6.8 — Standard Autolinks (6 tests, **LOW–MEDIUM impact**)

`<http://...>` and `<email@...>` autolinks. Already partially handled by the
inline `<...>` recogniser in `processInlineCharImpl`. The 6 failing tests are
edge cases — empty schemes, invalid email shapes, escaped chars in autolink.

**Where**: the existing `inlineBuffer.startsWith("<")` resolution path
(around line 4400 in `MarkanywhereParser.kt`).

### 5. GFM §6.12 — Hard line breaks (8 tests, **MEDIUM impact**)

Trailing `  \n` (two-or-more-spaces + newline) and `\\\n` (backslash + newline)
should produce `<br/>`. Already partially supported via
`paragraphTrailingSpaces` and the `\` + `\n` path. Edge cases in interaction
with code spans, soft breaks, and inside other inline constructs.

### 6. Singletons in §6.11 (1) and §6.14 (1)

One test each. Probably small spec compliance fixes.

### 7. Full HTML5 named entity table (GFM §6.2, **MEDIUM impact**)

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
  see current count. Baseline at the start of link work was 205 failures; we
  shipped at 63 (143 fixed, 0 regressions).
- The systematic divergences are documented in `CLAUDE.md` under "Streaming
  divergences" — read that before starting any of the link cases above.
- Most failures map cleanly to a single GFM section, so each work stream is
  fairly independent.
