# markanywhere-browse

Drives a real Chrome over the Chrome DevTools Protocol — via
[kdriver](https://github.com/kdriver/kdriver) — to capture a live page as a
[`SemanticEventDump`](../markanywhere-dump/src/commonMain/kotlin/SemanticEventDump.kt)
and to act on that page by element reference. It is the producer of the dump
fixtures consumed by [`markanywhere-html`](../markanywhere-html)'s end-to-end
tests, and the basis for letting an LLM read a page and then click, type, and
navigate it — as consumed by [Golem XIV](https://github.com/xemantic/golem-xiv).

The module is two cooperating pieces:
[`PageSession`](src/commonMain/kotlin/PageSession.kt) (capture + act) and the
[`Tab.waitUntilLoaded()`](src/commonMain/kotlin/WaitUntilLoaded.kt) family
(best-effort "the page has settled" waiters you run before capturing). It does
not own the `Tab` — the caller manages the browser and navigation lifecycle.

## Capturing a page

`PageSession(tab).dump()` walks the **full** DOM into a
[`SemanticEventDump`](../markanywhere-dump/src/commonMain/kotlin/SemanticEventDump.kt)
(the page `url`, the `dumpedAt` capture instant, and the semantic event stream of
the rendered tree). The walk is **lossless** — every element, every inter-tag
whitespace text node, and every `aria-hidden` / hidden subtree is kept — and the
browser's accessibility verdicts are recorded *as data* on each `mark` (reserved
attributes, see
[`AccessibilityAnnotations`](../markanywhere-dump/src/commonMain/kotlin/AccessibilityAnnotations.kt)
in [`markanywhere-dump`](../markanywhere-dump)) rather than acted on:

- a `<table>`'s data-vs-layout verdict → `data-markanywhere-role` (Blink's own
  role, e.g. `LayoutTable`, read from the accessibility tree — not a
  reimplementation of its `IsDataTable()` heuristic);
- a not-rendered or non-`block` element's computed display → `data-markanywhere-display`
  (with `none` standing for `display:none`), plus `data-markanywhere-visibility`
  for `visibility:hidden`;
- an `aria-labelledby`-only element's computed accessible name → a synthetic
  `aria-label`;
- every **actionable** element (focusable, or carrying an interactive
  accessibility role) → a dense, document-order `data-markanywhere-ref`.

The `<head>` is exempt from annotation: it computes to `display:none` wholesale,
so annotating it would let a downstream filter drop the `<title>` / `<meta>`
provenance the HTML→Markdown pipeline turns into frontmatter.

The DOM side comes from `DOMSnapshot.captureSnapshot` rather than
`DOM.getDocument` — the latter silently omits whitespace-only text nodes, which
the downstream pipeline needs as a word separator — joined per element with the
Blink accessibility tree. The capture itself lives in
[`CapturePage.kt`](src/commonMain/kotlin/CapturePage.kt).

Acting on these verdicts (dropping hidden subtrees, unwrapping layout tables,
collapsing structural whitespace) is a **downstream** concern, living in
[`markanywhere-html`](../markanywhere-html)
([`applyAccessibility`](../markanywhere-html/src/commonMain/kotlin/ApplyAccessibility.kt),
[`simplifyHtml`](../markanywhere-html/src/commonMain/kotlin/SimplifyHtml.kt),
[`dropHtmlStructuralWhitespace`](../markanywhere-html/src/commonMain/kotlin/HtmlWhitespaceNormalizingTransformer.kt)).
Keeping the capture lossless means one dump can be replayed against different
filtering policies without re-capturing.

## Acting on the page by reference

[`PageSession`](src/commonMain/kotlin/PageSession.kt) is stateful: every
`dump()` refreshes a `ref → backendNodeId`
registry, so a consumer that read the dump can name an element by its
`data-markanywhere-ref` and get a live kdriver `Element` back:

```kotlin
val session = PageSession(tab)
val dump = session.dump()        // SemanticEventDump; actionable elements carry data-markanywhere-ref
val el = session.element("3")    // resolve ref -> live Element (via CDP DOM.describeNode, no DOM mutation)
el.click()                       // or focus(), sendKeys("…"), getInputValue(), clearInput(), …
```

`element(ref)` rehydrates through the captured `backendNodeId` (the same path
kdriver's own selectors take). Refs are **regenerated on every `dump()`** —
interaction changes the DOM and invalidates node identities — so `element(ref)`
throws `NoSuchElementException` on a ref not in the *current* capture;
re-`dump()` after the page changes.

## Waiting for the page to settle

There is no reliable oracle for "an arbitrary page is fully loaded" (a clock,
carousel, analytics poll, or websocket never goes quiet), so `Tab.waitUntilLoaded()`
layers three generic signals before you `dump()`, with a hard `timeout` backstop:

```kotlin
val tab = browser.get(url)
tab.waitUntilLoaded()            // ReadyState.COMPLETE, then network-idle AND dom-idle
val dump = PageSession(tab).dump()
```

1. `ReadyState.COMPLETE` — document parsed + synchronous sub-resources;
2. `waitForNetworkIdle` — no in-flight requests for `networkIdleTime` (tracks raw
   CDP `Network` events, since kdriver's own readiness check ignores XHR/fetch);
3. `waitForDomIdle` — an in-page `MutationObserver` reports the DOM stopped
   mutating for `domQuietTime` (catches pure client-side renders the network
   never sees).

Steps 2 and 3 run concurrently and **both** must report quiet — each covers the
other's blind spot. All three return a boolean: `true` if it genuinely settled,
`false` if a `timeout` cap tripped first. A `false` means "best effort, proceed
anyway", **not** "failed" — on a never-quiet page you still capture. The waiters
are also usable standalone (`tab.waitForNetworkIdle()`, `tab.waitForDomIdle()`).

## Module notes

- Only the **jvm** target is enabled in the default dev build — kdriver's
  browser-process control is most mature there. The full build additionally
  declares exactly the intersection kdriver (`dev.kdriver:core`) publishes (js
  and the desktop-native triples: macosArm64, linuxX64, linuxArm64, mingwX64),
  not `allTargets()`, because kdriver ships no wasm / apple-mobile / android-native
  artifacts.
- Depends on [`markanywhere-api`](../markanywhere-api) and
  [`markanywhere-dump`](../markanywhere-dump) (the
  [`SemanticEventDump`](../markanywhere-dump/src/commonMain/kotlin/SemanticEventDump.kt)
  / [`AccessibilityAnnotations`](../markanywhere-dump/src/commonMain/kotlin/AccessibilityAnnotations.kt)
  types) plus `kdriver-core`.
- Published to Maven Central via the `markanywhere.convention` plugin, with
  `explicitApi()` enforced.
- Tests are integration tests that drive a real headless Chrome over `file://`
  fixtures under `src/commonTest/html` — there is no seam to unit-test the CDP
  orchestration against, so the waiter tests assert only the boolean contract
  (settled vs. hit-the-cap), never elapsed time, to stay off the wall clock.