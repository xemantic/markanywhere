# CLAUDE.md

This file captures only what cannot be inferred from the codebase itself.

## Rules for editing this file

Both developers and AI agents are expected to add entries as they encounter surprises.

- **Add an entry** when you encounter something unexpected: a build quirk, a non-obvious constraint, a dependency gotcha, or any behavior that would surprise the next agent or developer.
- **Add an entry** when a developer flags an anti-pattern produced by AI — describe the anti-pattern and the preferred alternative.
- **Do not** add codebase overviews, directory listings, or anything discoverable by reading the source.
- Keep entries concise: one line per lesson, grouped under a heading if a theme emerges.

## Streaming divergences

The parser intentionally diverges from GFM in well-defined places where
spec-correct behavior would force buffering past the next emitted event,
breaking the typewriter-style streaming UX. **These are not bugs — read this
section before "fixing" a DIVERGENCE-marked test.** Tests covering these
cases are explicitly named with `DIVERGENCE` in their function name.

### Core invariant

The semantic event stream is **append-only**. Once a `text` / `mark` / `unmark`
event is emitted to the downstream collector, it cannot be retracted or
modified. All divergences below trace back to this constraint.

### What we DO buffer (bounded, well-defined cases)

The parser does buffer in a few specific places — never past a single inline
construct or two-line lookahead, never past the next emitted event:

- **N≥2 inline code spans** (`` `` …content… `` ``): full content held until
  the close-run resolves the GFM strip rule.
- **GFM tables** (`BlockMode.TableHeaderPending`): a `|`-line is held for one
  more line to see if a separator confirms the table.
- **List blocks**: the entire list block is buffered before structural emit.
- **Inline link/image labels** (`[label]` / `![alt]`): a `RedirectingCollector`
  capture holds events between `[` and `](url)` (or abort) so `<a>`/`<img>`
  can wrap the rendered content (Phase 3a).
- **HTML 6/7 blocks**: rich state machine with sub-parse mode for nested
  Markdown — but no event retraction.
- **Extended autolinks (GFM §6.9)**: the `AutolinkCollector` wrapper holds
  every `text` event arriving inside an inline context until the next
  `mark`/`unmark` (or end-of-flow). At drain time the buffered text is scanned
  for `www.` / `http(s)://` / `ftp://` / `mailto:` / `xmpp:` / bare email
  patterns; non-matches replay the original events untouched, matches replay
  with `<a>` woven in. This delays paragraph text emission until the next
  inline mark/unmark — a deliberate trade-off vs. correctness for the
  email-autolink case (we can't recognise `local-part@host` until `@` is seen,
  by which point the local part has already been emitted otherwise).

### What we DO NOT do (the divergences)

#### Inline state cannot span line breaks

`flushInline` force-closes every inline state (`code`, `math`, `strong`, `em`,
`del`, `mark`, `sup`) at every line/block boundary. Consequence: multi-line
inline code spans, multi-line emphasis runs, etc. close at the line break.
Lifting this would require inline state to persist past `\n` and the
paragraph/continuation handlers to suppress structural `\n` events while inside
an open inline span.

**Affected**: GFM ex 357/358/359 (unmatched code-span openers), 340/341/350
(N=1 strip rule), some §6.13 soft-break edge cases.

#### Eager open commits for code, math, em (no retroactive close)

Inline code, math, and emphasis emit their `mark` event on open detection —
content streams immediately. If the close never arrives (or the source's
structure wants the open delimiters as literal), we cannot retract. `<code>` at
end of a code-spanless paragraph closes via `flushInline` rather than replaying
the opener as text.

Trade-off was made in favor of typewriter UX — code/em should appear as you
type, not pop in only after the closer.

#### Lists are always rendered loose

`<p>` wraps every list-item's first content. Tight/loose can only be decided
after the entire list closes (a blank line in the very last item retroactively
makes prior items loose). Once `<li>foo` text is emitted, it cannot be wrapped
in `<p>` after-the-fact. Buffering the whole list breaks streaming.

CSS picks up the slack: `li > p:only-child { margin: 0 }` collapses the visual
gap so loose-rendered tight lists look right.

#### Emphasis resolution is flanking-aware but not delimiter-stack-aware

`resolveEmphasisRun` consults `inlineOpenStack` for em/strong frames already
open and uses CommonMark flanking (rules 1–8). When the run can legitimately
close, `closeInlineDownTo` walks the stack LIFO closing inner frames first.

But: the spec's full process_emphasis algorithm needs a paragraph-wide delimiter
stack with bidirectional pairing — incompatible with our streaming, where
events are emitted as runs resolve. So `*foo**bar*` produces
`<em>foo<strong>bar</strong></em>` (we close strong inside em on the closer);
spec emits `<em>foo**bar</em>` because no `**` closer follows, but that
requires whole-paragraph buffering.

#### Tables only at block boundary, never interrupting paragraphs

GFM allows a `|`-line in mid-paragraph followed by a separator to start a
table. We don't — tables only start at fresh block boundaries. Implementing
this needs the same 2-line lookahead in `ParagraphContinuation` that
`processStart` already does, plus a way to retract the in-flight paragraph.

#### Link reference definitions: forward references unresolved

Single-line `[label]: url "title"` definitions are recognised at block
boundary and registered for use later. **But** GFM/CommonMark allow a usage
to reference a definition that appears *later* in the document — that requires
buffering the entire document or revisiting emitted events. Append-only mode
forbids both.

Result: only **back-references** (def-then-use) resolve. Forward-reference
tests are marked `DIVERGENCE` and emit the usage as literal text. Multi-line
ref-def shapes (label split across lines, dest on next line, multi-line title)
are also unrecognised — same buffering issue, plus the recovery path on
malformed shapes would need to re-feed the buffered chars through inline
parsing.

#### Link/image label content rendering (bounded buffering)

Inline events emitted while parsing a label are captured by `RedirectingCollector`
and replayed inside `<a>` on commit / outside the brackets on abort. This is
bounded buffering (one label at a time, ≤ ~one paragraph in extreme cases).

Residual divergences within label content:
- **Delimiter scoping**: `*` inside a label can't currently see the
  `linkLabelOuterStackDepth` watermark, so an inner `*` could close an outer
  em opened *before* the label, producing unbalanced events. We work around
  this by flushing pending non-backtick delimiter runs in `inlineBuffer` as
  literal text at `]` instead of routing through the resolver. Cost: trailing
  delimiter chars in unmatched-closer position become literal text inside the
  em (see `Gfm_06_06_Test` ex 525, 539; `Gfm_06_04_Test` ex 428, 442, 482).
  Real fix: scoped resolver above the watermark — invasive change to
  `resolveEmphasisRun`.
- **Backtick exception**: an unresolved backtick run in `inlineBuffer` falls
  through to the standard dispatcher so the code span opens with `]` as
  content (`[foo``]``](/uri)` etc. work per spec).

#### Nested label brackets — depth-counted, not recursive

Phase 3b adds `linkLabelBracketDepth`: balanced `[…]` inside a label is
content (`[link [foo [bar]]](/uri)` works). But spec wants `[a [b](u)](u)` to
**resolve the inner inline link and abort the outer** — that requires
speculative recursive parsing with unwinding. Our depth-counter treats the
inner `[b](u)` as content of the outer label, so the outer link forms with
those raw brackets+URL inside. See `Gfm_06_06_Test` ex 523, 527, 528.

#### Image-inside-link not supported

Inline images `![alt](src)` inside link labels (`[![alt](src)](url)`) are not
parsed as nested constructs. The depth-counter treats the inner `![…](…)` as
content. Same fix as nested link parsing — speculative recursion.

#### Multi-line link parsing not supported

`[link](\n  /uri\n  "title")` shapes — destination, title, or label spanning
newlines. Requires the URL state machine + label capture to persist past `\n`,
plus a re-feed-through-inline mechanism on abort so HTML tags inside the
aborted source still emit (see ex 501).

#### Inline HTML re-detection on abort

When a label aborts, the captured events are replayed and the source after the
label flows as paragraph content. If the source between `[` and the abort
contained an HTML opener that was *not yet committed* (still in inlineBuffer),
re-feeding through the inline char processor would re-trigger detection. We
don't do this — the replay emits as text. Cost: ex 501, 504 produce literal
text where GFM expects `<br>` etc. inline HTML.

### When weighing a divergence vs. a fix

Factor in whether the fix forces buffering past the next emitted event. If
yes, default to DIVERGENCE — preserving streaming UX is the parser's reason
to exist. If the fix is bounded (one inline construct, one or two lines of
lookahead), it's likely worth doing.

---

## Known gotchas

- GFM §6.9 extended autolinks (`www.foo.com`, `http(s)://…`, `ftp://…`, `mailto:`, `xmpp:`, bare `local@host`) live in `AutolinkCollector.kt` (a wrapper between `RedirectingCollector` and the downstream collector inside `parse()`), not in `MarkanywhereParser.kt`. The wrapper holds every paragraph/heading/etc. text event between consecutive non-text events, then re-tokenises the buffered run word-by-word with the GFM pre-boundary check (start-of-text or whitespace/`*`/`_`/`~`/`(`), trailing-strip rules (entity-ref, punctuation, unbalanced `)`), and email last-char rule (no `-`/`_`). Suppressed inside `code`/`pre`/`a`/`img`/`math`/`script`/`style` so existing `<…>` autolinks (§6.8) and inline links don't double-wrap. Consequence (deliberate): `should emit SemanticEvents incrementally without buffering` had to relax from per-char emission to "drain at next mark/unmark"; per-paragraph buffer is bounded but NOT zero.
- GFM §6.11 disallowed raw HTML — `title`, `textarea`, `style`, `xmp`, `iframe`, `noembed`, `noframes`, `script`, `plaintext` — are listed in `GFM_DISALLOWED_TAGS` and filtered in two places: `tokenizeHtmlLine` returns disallowed open/close tags as `HtmlToken.Text` so HTML 6/7 sub-parse content emits them as literal text instead of nested marks; the inline `<…>` dispatch in `processInlineCharImpl` adds the same set as a second guard alongside `INLINE_HTML_BLOCK_ELEMENTS`. The two filters overlap (most disallowed names are also block-only) but listing them explicitly keeps the GFM extension definition co-located.
- Inline raw HTML dispatch (`<…>` inside paragraph text in `processInlineCharImpl`) only recognises **`<tag …>` / `</tag>`** shapes (and the `<…>` autolink forms from §6.8). Comments (`<!--…-->`), processing instructions (`<?…?>`), declarations (`<!NAME …>`), and CDATA (`<![CDATA[…]]>`) are NOT recognised inline — the buffer terminates at the first `>`, so PI/CDATA whose content can contain `>` cannot be matched generically. All four constructs survive as **literal text** (DIVERGENCE: GFM passes them through as raw HTML; rendered escaped, they appear as visible source). Multi-line shapes (opener spanning `\n`, attribute value spanning `\n`, comment body spanning `\n`) cannot work either — `flushInline` aborts inline state at every line break. Tests under `Gfm_06_10_Test` are marked `DIVERGENCE` for these cases. LLM impact is low: comments are the most common (single-line ones already render as escaped, which is usually acceptable), the rest are essentially never emitted by chat models.
- Inline math `$…$` opens *speculatively*: `$` buffers in `inlineBuffer`, then the buffer-resolution branches commit on the next char — open `<math>` only if the char passes `isMathOpenChar` (letter, `\`, or `{`), otherwise emit the `$` as literal text and re-process the char. This keeps `hello $.;'there` (GFM §6.14 ex 675) as plain text and `got $5` from accidentally opening math, while still recognising `$E=mc^2$` and `$\frac{a}{b}$`. Block math `$$…$$` and the math-close path are unaffected — the rule only narrows the open decision.
- The parser is designed for **incremental, real-time rendering of streamed LLM Markdown output** — it emits semantic events append-only as characters arrive, with no retraction. This is why some intentional divergences from GFM exist: spec-correct algorithms that require buffering a whole paragraph (e.g. CommonMark's `process_emphasis` delimiter-stack pairing) would delay visible output until the block closes, breaking the typewriter-style UX. When weighing a divergence vs. a fix, factor in whether the fix forces buffering past the next emitted event.
- `flushInline` in `MarkanywhereParser.kt` force-closes every inline state (`code`, `math`, `strong`, `em`, `del`, `mark`, `sup`) at every line/block boundary. Consequence: inline constructs cannot span a soft-break — multi-line inline code spans (CommonMark §6.1) and other multi-line inline runs are intentionally divergent. Lifting this would require the inline state to persist past `\n` and the paragraph/continuation handlers to know not to emit a structural `\n` while inside an open inline span. Mark such tests `DIVERGENCE`.
- Blockquote (GFM §5.1) uses the same **sub-parse** model as HTML 6/7 blocks: a `Blockquote` frame is pushed onto `blockModeStack` and an inner `Start` frame is pushed atop it; the inner frame handles content via the regular Markdown dispatcher (so headings, fenced code, lists, indented code, nested blockquotes all "just work" recursively). Each subsequent line's `>` prefix (with optional 0..3 leading spaces and optional 1 trailing space) is consumed by `interceptBlockquoteLine` in `process()` *before* chars reach the inner sub-parser, which is why a nested `>` on the inner content is correctly interpreted as a *new* nested Blockquote opener instead of being re-eaten as outer prefix. Lazy continuation: when the inner top is `ParagraphContinuation` and a non-`>` line is not a paragraph-interrupter, the line is replayed through the inner mode (so it joins the open paragraph). Otherwise the blockquote drains and closes (`closeAllBlockquotes` walks the stack popping non-blockquote frames via `drainTopFrameToStart`, then unmark+pop each `Blockquote`). HTML block detection (`detectHtmlBlockType`) is suppressed inside blockquote (DIVERGENCE: `> <div>` etc. flow as paragraph content).
- HTML-derived `mark`/`unmark` events are emitted with `isTagged = true`. Tag and attribute names are lowercased **only when the lowercased name is a known HTML5 element** (the `HTML5_ELEMENTS` set in `MarkanywhereParser.kt`) — so `<DIV CLASS="foo">` and `<div class="foo">` produce identical events. Tag names outside that set keep their source casing (`<Warning>` stays `Warning`), treating non-HTML5 tags as XML-ish where case is significant. `openTags` storage uses the same normalized name; close-tag matching against `openTags` always compares case-insensitively so a closer with different casing still pops correctly. HTML block types 6 and 7 stream incrementally — the opening tag emits `mark` as soon as `>` is parsed, content lines stream as text events, and the matching root close emits `unmark`. The frame starts in `RawText` child-mode; the first blank line transitions it to `SubParse` and pushes a fresh `Start` frame on top of `blockModeStack` so subsequent lines route through the regular Markdown dispatcher (paragraphs, lists, fenced code, etc., are all sub-parsed). The matching root close tag is detected at the top of `processStart`'s `\n` handler via `findEnclosingHtmlFrameIndex` + `tryCloseEnclosingHtmlBlock`; close tags for inner tracked `openTags` (e.g. `</pre>` inside a `<table>...<pre>...` frame) drain that frame's `openTags` down to and including the matched name. Residual divergences from GFM: blank lines transition to sub-parse rather than closing the block, and unclosed blocks auto-close at EOF (the close-tag detection scans the whole stack so nested HTML works). See `processHtmlBlock6or7`, `streamHtmlBlock6or7ContentLine`, `BlockMode.ChildMode`, `normalizeHtmlName`, and `tryCloseEnclosingHtmlBlock`.
- `blockMode` is a stack (`ArrayDeque<BlockMode>`) — the bottom is always `Start`, the top drives dispatch. Most transitions use `replaceMode(...)` (sibling Start ↔ Paragraph etc.); only entering an HTML 6/7 block uses `pushMode(...)`, and only the matching root close (in `streamHtmlBlock6or7ContentLine` or `tryCloseEnclosingHtmlBlock`) uses `popMode()`. The blank-line sub-parse transition pushes a `Start` frame above the HTML frame; the HTML close pops both. `finalize()` drains the stack from top to bottom — each non-`Start` frame closes its in-flight block and `replaceMode(Start)`, then the loop pops pushed sub-parse Starts until only the bottom Start remains.
- When asserting an event flow whose **entire** top level is HTML-tagged, prefer `semanticEvents(tagged = true) { … }` over wrapping the body in `tagged { … }` — same result, less indentation. Reserve the `tagged { … }` / `tag(name) { … }` builders for *mixed* expectations where only part of the tree is tagged (e.g. an HTML block followed by a Markdown `"p"` paragraph, or a `<del>` inside a paragraph). Switching the outer scope's default would silently flip every nested `"p"`/`"em"`/etc. to `isTagged = true` and cause confusing test failures.
- Copyright year range (e.g. 2025-2026) is applied on autosave — new files should use only the current year (e.g. 2026).
- Kotlin context-sensitive resolution (`-Xcontext-sensitive-resolution`, preview in 2.2 / refined in 2.3) is enabled in the convention plugin. Inside a `when` whose subject has a known sealed type (or for `is`/`as` against that type), drop the type prefix on subclass references — write `is Heading` / `Paragraph`, not `is BlockMode.Heading` / `BlockMode.Paragraph`. CSR also applies to explicit return types, declared variable types, and parameter types when an outer expected type drives resolution. It does NOT apply to functions, properties with parameters, extension properties with receivers, type-annotation positions for variables, supertype lists, or generic constraints — keep the prefix in those positions.
- In Claude Code "auto mode", never commit on your own — leave changes in the working tree so the user can review the diff first. Only commit when the user explicitly asks for it.
- When generating backtick-quoted Kotlin identifiers (e.g. test names) from arbitrary input, strip CR, LF, and ``` ` \ < > [ ] / . : ; * ? " | , # ( ) & ~ @ $ { } ``` before wrapping in backticks. The JVM backend accepts most of these in backtick identifiers, but Native (Android Native, Linux/macOS/iOS, etc.) and JS reject them with `Name contains illegal characters: …` — the project is Kotlin Multiplatform, so the strict Native/JS rules govern. Keep alphanumerics, `_`, `-`, ASCII space, and `!` (which all targets accept).
- Paragraph trailing-space handling (GFM §6.7 hard line break / §6.13 soft break whitespace stripping) uses a single `paragraphTrailingSpaces` counter. ASCII spaces in `processParagraph`'s fast-path are *deferred* (counted, not emitted) until either a non-space char flushes them as text or `\n` finalizes the count for the line. The next-line decision in `processParagraphContinuation` (and the fall-through helper `emitParagraphLineBreak`) promotes ≥2 trailing spaces to `<br/>` if the paragraph continues, or drops them silently on close/interrupt. Two corollaries: (1) `findNextParagraphFastPathEnd` stops the chunk fast-path at every space (not just inline controls) — this fragments paragraph text across word boundaries into multiple `text` events, which `mergeAdjacentText` reunifies but raw event streams will see; (2) `' '` is *not* deferred when `inlineBuffer.isNotEmpty() || escaped || inEntityRef`, because an unresolved delimiter run (e.g. `***`), a pending backslash escape, or an in-flight entity ref (`&copy<space>`) all need the space to drive resolution — let it flow through `processInlineChar` and re-enter via `pendingDeferredChar`. Also: the `processChunk` fast-path now clears `pendingDeferredChar` on substring emit (the substring covers the deferred char's position), fixing a latent double-process that the space-stop scan would otherwise expose.
- Entity / numeric character reference decoding (GFM §6.2) uses a separate `inEntityRef`+`entityBuffer` state — *not* the shared `inlineBuffer`, because that buffer is already overloaded for `*`/`**`/`***`/`_`/`~`/`==`/`!`/`<`/backtick-run accumulation, and a `&` body would collide with several of those buffer-resolution branches. The state machine: a regular-text `&` enters `inEntityRef`, subsequent letters/digits (plus a leading `#` then optional `x`/`X`) accumulate in `entityBuffer`; a `;` commits via `tryDecodeEntityBody` (decoded chars are emitted as **plain text** so `&#42;` is literal `*`, not an emphasis delimiter — GFM example 333), and any other char aborts: replay `&body` literal + reprocess current char via `pendingDeferredChar`. `flushInline` calls `abortEntityRef` so an unterminated `&copy` at block end survives as source. Outside the inline char path, `decodeEntities(s)` does a one-shot pass for batch contexts where chars don't stream through `processInlineChar`: link href/title (`[x](url)`), HTML attribute values (in `tryParseOpenTag`), and the fenced code info string (in `parseFenceOpen`). `&` is in `Char.isInlineControl()` so the paragraph and inline fast-paths halt at it. Code spans, indented code blocks, and fenced code block *content* must NOT decode — those branches sit before the entity-state check or use raw buffering, so entities pass through literal (GFM examples 331, 332). The `NAMED_ENTITIES` map is intentionally a small subset of HTML5 — extend as new examples need names; an unknown name leaves the source `&name;` literal (GFM example 326).
- Inline link/image parsing in `MarkanywhereParser.kt` is speculative: a `[` (or `![`) flips `inLink` (or `inImage`) to true and `openLinkLabelCapture()` starts a `RedirectingCollector` capture so all events emitted while parsing the label content are buffered (Phase 3a). Label content flows through normal inline parsing — em/strong/code/inline-HTML inside `[label]` renders into the captured event buffer. A `]` is held via the `linkLabelTentativeClose` flag (not via `inlineBuffer` — the buffer is also used for label-internal delimiter accumulation, so the two are now decoupled). The construct only resolves on `](url)` (commit URL phase) or `]<next char>` (commit ref / shortcut, abort otherwise) — until then the buffered events stay captured. After `](`, URL/title parsing runs through a phase machine in `handleLinkUrlChar` (`LinkUrlPhase` enum: `PreDest` → (`DestAngle` | `DestPlain`) → `BetweenDestTitle` → (`TitleDouble` | `TitleSingle` | `TitleParen`) → `AfterTitle`), with `linkUrlSource` accumulating raw chars for replay on abort. On commit, `<a>`/`<img>` is emitted and the captured events are replayed inside the mark — except for images, whose `alt` is reduced to plain text via `toLabelText()` (CommonMark §6.5: image description renders as plain text). On abort, `flushInlineLabelClose()` drains inline state down to the `linkLabelOuterStackDepth` watermark (closing only state opened *inside* the label, leaving any outer em/strong alone) BEFORE `stopCapture` so closing emissions stay in the buffer; then `[`+events+`]` replays as bare events. The captured events are merged adjacent-text on add (in `RedirectingCollector.emit`) so replay matches the pre-Phase-3a single-text-event shape. Abort triggers: (1) `]` followed by neither `(` nor `[` nor a registered shortcut-ref — replay + `pendingDeferredChar`; (2) URL/title state machine hits a malformed transition (e.g. nested unescaped `(` in a `(...)` title); (3) `flushInline()` at any block boundary; (4) ref-resolution helper `handleLinkRefChar` if the second `[…]` resolves to no definition or contains an unescaped `[`. The lookup key for shortcut/collapsed refs uses `applyBackslashEscapes(linkText)` then `normalizeLinkLabel`, matching the def-side normalization in `tryParseLinkDefinition`. Skipping the replay silently drops content; see `UnresolvedBracketsTest` for the regression suite.
- The `pendingDeferredChar` re-process protocol delivers the same source char twice through `processInlineCharImpl` (once before delimiter resolution, once after). The label-source accumulator (`linkText` / `imageAlt`) must avoid double-appending on the second delivery. `inlineCharIsReprocess` is set in `processInlineChar`'s finally block when `pendingDeferredChar` is non-null after the call returns, then read at the top of the next call to skip the label-source append. Without this guard, a label like `[Foo*bar]` accumulates `Foo*bbar]` (the `b` after `*` is appended twice — once on first attempt, once on `*`-resolved re-process) and the shortcut lookup fails to match a registered definition.
- Link/image labels permit balanced `[…]` inside via the `linkLabelBracketDepth` counter (Phase 3b). An unescaped `[` at depth 0 inside a label increments depth and emits as content; a `]` at depth ≥1 decrements and is content; only a `]` at depth 0 triggers `linkLabelTentativeClose`. This makes labels like `[link [foo [bar]]](/uri)` parse per spec. **Residual divergence**: spec-correct nested handling would speculate and **abort the outer link** when an inner `[…](…)` *would* form a valid inline link (so `[foo [bar](/uri)](/uri)` becomes `<p>[foo <a href="/uri">bar</a>](/uri)</p>`). Our depth counter treats the inner `[bar](/uri)` as plain content, then recognises the outer `](/uri)` as the URL phase — so the outer link forms with the inner brackets+URL inside its label. Real recursion would require speculative parser unwinding. The `]` handler also has a **backtick-pending exception**: when `inlineBuffer` holds an unresolved backtick run, `]` falls through to standard inline parsing so the code span opens (with `]` as content). All other pending delimiter runs (`*`, `_`, `~`, etc.) are flushed as literal text by `flushInlineLabelClose` — routing them through standard resolution would let an inner closer pair with an *outer* em/strong frame, producing unbalanced events. Cost: trailing `*]` or `_]` patterns leave the delimiter as content of an unmatched em (see `Gfm_06_06_Test` ex 525, 539; `Gfm_06_04_Test` ex 428, 442, 482).
- Link reference definitions (CommonMark §4.7) are recognized at block boundary by `tryParseLinkDefinition` and consumed silently — they emit no events. The parser keeps a `linkDefinitions: MutableMap<String, LinkDefinition>` keyed on the case-folded, whitespace-collapsed, backslash-decoded label; first-wins on duplicates. Inline `[label]`, `[label][]`, and `[label][ref]` resolve against this map at the speculation-resolution point (`inlineBuffer.endsWith("]")` followed by `[`, then second-`]` close, or a non-`(`/`[` char that triggers shortcut lookup). `flushInline` runs a final shortcut lookup before aborting so an unresolved `[label]` at block close still has a chance to commit. **STREAMING DIVERGENCE**: only single-line ref-def shapes are recognized, and only definitions arriving *before* their use can be resolved (an append-only stream cannot retroactively rewrite a usage emitted before its definition). Forward-reference and multi-line-def tests are marked `DIVERGENCE`. Adding `[` to `Char.isBlockStart()` is what enables the line-buffered detection — at the cost of paragraphs starting with `[` no longer eagerly emitting `<p>` on the first char (same trade-off as `>`, `-`, `*`, etc.). Backslash escapes inside link/image labels (`[foo\]bar]`, `![alt\]more]`) and inside reference labels are routed to the label/alt/ref accumulators in `processInlineCharImpl`'s `escaped` block — without that routing the escaped char would be emitted as paragraph text, splitting the label.
- The semantic event stream is a **strict invariant**: every `mark` must be paired with a matching `unmark` in LIFO order. Unbalanced streams cannot be rendered (the renderer's open-element stack will never close, leaving the output in a broken state). This applies in *both* directions: (a) every parser code path that emits a `mark` must guarantee an `unmark` before the stream ends — emphasis (`em`/`strong`) and inline HTML opens share a single `inlineOpenStack` so they pop in *opening order* (a `<strong>` opened inside an outer `<em>` is force-closed *before* the em when the matching `*` closer arrives, never after); `flushInline` drains the stack top-to-bottom at every block boundary, and an orphan HTML close (no matching open in the stack) falls back to literal `<…>` text rather than emit an unbalanced `unmark`; (b) test expectations built with `semanticEvents { … }` must also balance. Prefer the matched-pair builders — `"name" { … }`, `tag(name) { … }`, `tagged { … }` — over raw `mark(…)` / `unmark(…)`. Raw `mark`/`unmark` calls in tests are reserved for unit-testing the builder primitives themselves (see `SemanticEventFlowTest`); never use them in event-flow expectations against parser output, since an orphan `mark` in a test silently encodes a parser bug as the "expected" behavior.
- Emphasis resolution in `resolveEmphasisRun` (`MarkanywhereParser.kt`) is **flanking-aware but not delimiter-stack-aware**: at each `*`/`**`/`***`/`_`/`__`/`___` run we consult `inlineOpenStack` for em/strong frames already open, and use CommonMark flanking (rules 1–8, with stricter `_` rules to suppress intraword emphasis) to decide can-open / can-close. When the run can legitimately close, `closeInlineDownTo` walks the stack LIFO, force-closing any inner frames first — so `*foo**bar*` produces `<em>foo<strong>bar</strong></em>` (DIVERGENCE: spec emits `<em>foo**bar</em>` because no `**` closer follows, but that requires whole-paragraph buffering). When neither legitimate-open nor legitimate-close applies, the delimiters are emitted as **literal text** rather than force-closing the wrong frame — closer to spec than the historical fallback-close behavior. Run-length-3 splits 1+2 between em/strong: if one is already open and matches, the matching tag closes and the other half opens (or stays literal if it can't open). `isFlankWhitespace` recognizes Unicode `Zs` (incl. NBSP) plus `\t`/`\n`/`\f`/`\r` per the spec, not just ASCII space.
- Custom markup blocks (`<ns:name …>` / `</ns:name>`, namespace-qualified per `CUSTOM_MARKUP_TAGNAME`) **inside list items** (§5.2) follow the same architectural pattern as the table / HTML-block fixes: state lives on `ListContext.customMarkupTagName` (with `customMarkupHasContent` tracking whether any content line has been emitted yet) instead of pushing a `BlockMode.CustomMarkup` frame onto `blockModeStack` — pushing would knock `ListBlock` off the dispatcher's top-of-stack slot and break per-line container-indent stripping. Detection is line-based at the item's block boundary in `processListBlock` and at first-line in `emitItemFirstLine` (via `tryEnterListCustomMarkup`); content lines stream through `streamListCustomMarkupLine`, joined by `\n` (no leading `\n` before the first content line — mirrors the top-level `customMarkupSkipFirstNewline` behaviour). The closer is a line exactly equal to `</tagName>` after `trimEnd`. `closeListCustomMarkupIfOpen` is invoked from `popListContexts` and the sibling-marker close paths so unclosed blocks force-close at list end / item boundary. Same DIVERGENCE surface as the top-level `BlockMode.CustomMarkup`: single-line shapes like `<foo:bar>x</foo:bar>` are not recognised (the opener requires the line to end at `>` and the closer to be its own line). See `CustomMarkupInListItemTest`.
- HTML blocks (CommonMark §4.6) **inside list items** (§5.2) live on `ListContext.htmlBlock` (a `ListHtmlBlockState`) instead of pushing an HtmlBlock frame onto `blockModeStack` — same architectural reason as tables-in-list: pushing would knock `ListBlock` off the dispatcher's top-of-stack slot and break per-line container-indent stripping. Detection fires at `emitItemFirstLine` and at `processListBlock`'s `atBlockBoundary` branch (after table-header speculation, before the paragraph fallback). An early-out near the top of `processListBlock` (right after blank-line handling, before thematic-break / marker resolution) routes any non-blank line satisfying the active item's indent into `streamListHtmlBlockLine` — so list-marker-shaped content (`- foo`) and thematic-break-shaped content (`---`) inside an open HTML block stream as raw text rather than open a sibling list / end the list. Under-indented lines force-close the HTML block and fall through. **DIVERGENCE from top-level type 6/7**: a blank line emits `\n` and stays in raw-text mode (no sub-parse transition) — sub-parsing would push a fresh `Start` frame above `ListBlock`, again breaking indent stripping. **DIVERGENCE from top-level type 1**: same as top level (no leading `\n` after the opener — example 139 et al.). On item close (`popListContexts`), `closeListHtmlBlockIfOpen` drains `openTags` LIFO and, if the opener's `firstLineBuffer` is still non-null, emits it as raw text so an unclosed `<pre` doesn't disappear; this also keeps the event stream balanced (so GFM example 144 emits a force-closed `</div>` where GFM leaves it unclosed).
- GFM table detection (§4.10) is the one place the parser uses **2-line lookahead** without buffering past a visible event. A `|`-prefixed line at block boundary parks in `BlockMode.TableHeaderPending(headerLine)` — *no events emitted yet*. The next line decides: a valid `:?-+:?` separator with matching column count commits the table (emit `<table><thead>` + cached header cells with `align=` attributes from the separator, then defer `<tbody>` until the first body row); anything else replays both lines through `Start` with `suppressTableDetection = true` so they flow as a paragraph instead of looping back into `TableHeaderPending`. The same suppression flag also blocks `lineInterruptsParagraph` from breaking the rejected-table paragraph on subsequent `|`-lines. `TableBody(columnCount, alignments)` carries the separator-derived metadata so every body row pads/truncates to `columnCount` and copies per-cell `align=`. Body row splitter `splitTableCells` honors `\|` (literal pipe) and pipes inside backtick code spans, and accepts rows without leading/trailing pipes. Lazy continuation: a non-blank, non-block-interrupter line without a leading `|` is still a row (single cell, padded). `closeTableBody` skips the `<tbody>` unmark when no body row was ever emitted (header-only tables produce no `<tbody>` at all). Tables nested inside list items (§5.2) reuse the same 2-line lookahead but store the buffered header on `ListContext.tableHeaderPending` (alongside `tableOpen`, `tableBodyOpened`, `tableColumnCount`, `tableAlignments`) instead of pushing a `TableHeaderPending` frame onto `blockModeStack` — pushing would knock `ListBlock` out of the dispatcher's top-of-stack slot and break per-line container-indent stripping. The list-item path also defers in `emitItemFirstLine` so a marker-line shape like `- | a | b |` followed by `| - | - |` resolves into a `<table>` rather than a `<p>`. Residual divergence: GFM allows tables to interrupt paragraphs (a `|`-line in `ParagraphContinuation` followed by a separator); we do not — tables only start at a fresh block boundary.
- Lists are always rendered **loose** — every list item's first content is wrapped in `<p>` (`emitItemFirstContent` in `MarkanywhereParser.kt`), even when GFM's tight/loose rule would call the list tight. This is a deliberate streaming divergence: tight/loose can only be decided after the entire list closes (a blank line in the very last item retroactively makes every prior item loose), but the parser is append-only — once `<li>foo` text has been emitted, it cannot retroactively be wrapped in `<p>`. Buffering the whole list is incompatible with the typewriter UX. Rendering picks up the slack: a stylesheet rule like `li > p:only-child { margin: 0 }` collapses the visual gap so loose-rendered tight lists look correct. Mark such tests `DIVERGENCE`. Task-list `<input>` attributes are emitted in **alphabetical order** (`checked`, `disabled`, `type`) to match GFM canonical output regardless of the tight/loose question.
- Inline code spans (GFM §6.3) commit `mark("code")` *eagerly* at the opening run + first non-backtick (`processInlineCharImpl`'s open branch), so content can stream into `<code>` for typewriter UX. **N=1** streams non-backtick chars as text events (fast-path `findNextChar(content, startIndex, '`')`, gated on `inlineBuffer.isEmpty()` — once a tentative-close `` ` `` is buffered, the fast-path turns off so the next non-` reaches `processInlineCharImpl` to resolve the close). Backticks inside an N=1 span are *tentatively buffered*: a single buffered `` ` `` followed by a non-` confirms close; a longer run flushes as content text. **N≥2** buffers everything (the GFM strip rule needs full content visibility on close); the close uses trailing-run-length matching and applies the strip when content begins+ends with space and has any non-space char. **Unmatched openers force-close at `flushInline`** — emitting `<code>…</code>` rather than replaying the opener as literal source. This is a deliberate streaming divergence: deferring the open mark until close-confirmation would buffer all content past the next emitted event, killing typewriter UX inside long code spans. Tests covering unmatched openers (`example 357/358/359`) and the N=1 strip-rule edge cases (`example 340/341/350`) are marked `DIVERGENCE`; spec example 333 (`` ` b ` ``) intentionally uses NBSP, not ASCII space, to dodge the strip-rule question for N=1.
- The parser (and every module published as `commonMain`) must stay **Kotlin Multiplatform** — it ships to JVM, JS, Wasm, and Native. Even though dev runs default to `:jvmTest`, never reach for `java.*`, `javax.*`, or any JVM-only type (`java.util.ArrayDeque`, `java.util.LinkedList`, `Thread`, `File`, `ConcurrentHashMap`, `System.currentTimeMillis`, etc.) in `commonMain`. Use the Kotlin stdlib equivalents: `kotlin.collections.ArrayDeque` (multiplatform since 1.4), `MutableList` / `mutableListOf`, `kotlinx.coroutines` primitives, `kotlin.time.TimeSource`. If a JVM-only API is genuinely needed, isolate it behind an `expect`/`actual` declaration.
- **Whenever a new parser feature is added**, update the supported Markdown feature tables in both `README.md` (root, "Supported Markdown features" section) and `markanywhere-parse/README.md` ("Extensions beyond GFM" section if it's a parser extension). Omitting this is the main way the docs drift from reality.

## Test conventions

- Tests must retain `// given`, `// when`, `// then` comment structure — AI agents tend to omit these.
- For semantic event flow testing, use the overloaded `sameAs` infix on `Flow<SemanticEvent>` (defined in `markanywhere-test`) against a `semanticEvents { ... }` builder — this is event-stream comparison, unrelated to HTML.
- For asserting rendered HTML output (e.g. in `markanywhere-render` tests), use `sameAsHtml` (not the generic string `sameAs`) — provides syntax highlighting in the IDE.
- GFM example test naming convention: `example N - <description>` for spec-conformant tests, `example N - DIVERGENCE - <description>` when the parser intentionally diverges from GFM. Keep `DIVERGENCE` in the name even when updating expectations to match the divergent behavior.

## Anti-patterns to avoid

- Do not add content to this file that is already discoverable by reading the source or build scripts — that inflates context without adding signal, reducing AI agent task success rates (see [arxiv 2602.11988](https://arxiv.org/abs/2602.11988)).