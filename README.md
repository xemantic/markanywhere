# markanywhere

Incremental Markdown parser that emits streams of semantic events, plus tools to manipulate them — designed for real-time rendering of streamed LLM output.

[<img alt="Maven Central Version" src="https://img.shields.io/maven-central/v/com.xemantic.markanywhere/markanywhere">](https://central.sonatype.com/artifact/com.xemantic.markanywhere/markanywhere)
[<img alt="GitHub Release Date" src="https://img.shields.io/github/release-date/xemantic/markanywhere">](https://github.com/xemantic/markanywhere/releases)
[<img alt="license" src="https://img.shields.io/github/license/xemantic/markanywhere?color=blue">](https://github.com/xemantic/markanywhere/blob/main/LICENSE)

[<img alt="GitHub Actions Workflow Status" src="https://img.shields.io/github/actions/workflow/status/xemantic/markanywhere/build-main.yml">](https://github.com/xemantic/markanywhere/actions/workflows/build-main.yml)
[<img alt="GitHub branch check runs" src="https://img.shields.io/github/check-runs/xemantic/markanywhere/main">](https://github.com/xemantic/markanywhere/actions/workflows/build-main.yml)
[<img alt="GitHub commits since latest release" src="https://img.shields.io/github/commits-since/xemantic/markanywhere/latest">](https://github.com/xemantic/markanywhere/commits/main/)
[<img alt="GitHub last commit" src="https://img.shields.io/github/last-commit/xemantic/markanywhere">](https://github.com/xemantic/markanywhere/commits/main/)

[<img alt="GitHub contributors" src="https://img.shields.io/github/contributors/xemantic/markanywhere">](https://github.com/xemantic/markanywhere/graphs/contributors)
[<img alt="GitHub commit activity" src="https://img.shields.io/github/commit-activity/t/xemantic/markanywhere">](https://github.com/xemantic/markanywhere/commits/main/)
[<img alt="GitHub code size in bytes" src="https://img.shields.io/github/languages/code-size/xemantic/markanywhere">]()
[<img alt="GitHub Created At" src="https://img.shields.io/github/created-at/xemantic/markanywhere">](https://github.com/xemantic/markanywhere/commits)
[<img alt="kotlin version" src="https://img.shields.io/badge/dynamic/toml?url=https%3A%2F%2Fraw.githubusercontent.com%2Fxemantic%2Fmarkanywhere%2Fmain%2Fgradle%2Flibs.versions.toml&query=versions.kotlin&label=kotlin">](https://kotlinlang.org/docs/releases.html)
[<img alt="discord users online" src="https://img.shields.io/discord/811561179280965673">](https://discord.gg/vQktqqN2Vn)
[![Bluesky](https://img.shields.io/badge/Bluesky-0285FF?logo=bluesky&logoColor=fff)](https://bsky.app/profile/xemantic.com)

## Use cases

### Markdown Parsing

```kotlin
val markdown = """
# Hello

A *streaming* parser, <b>live</b>.
"""

flowOf(markdown).parse().collect {
    println(it)
}
```

Will print:

```text
{"type":"mark","name":"h1"}
{"type":"text","text":"Hello"}
{"type":"unmark","name":"h1"}
{"type":"mark","name":"p"}
{"type":"text","text":"A"}
{"type":"text","text":" "}
{"type":"mark","name":"em"}
{"type":"text","text":"streaming"}
{"type":"unmark","name":"em"}
{"type":"text","text":" parser,"}
{"type":"text","text":" "}
{"type":"mark","name":"b","tagged":true}
{"type":"text","text":"live"}
{"type":"unmark","name":"b","tagged":true}
{"type":"text","text":"."}
{"type":"unmark","name":"p"}
```

The stream is append-only: each event is emitted as soon as the parser commits to it, so `<h1>` opens before `Hello` arrives and `<em>` opens the moment the `*` resolves. The Markdown `*streaming*` and the literal `<b>` tag produce the same kind of `mark`/`unmark` events — the only difference is `"tagged":true`, flagging the `b` as real HTML in the source rather than Markdown-derived (the `em`, being Markdown, omits it).

> Backed by the first test in [`MarkanywhereParserTest`](markanywhere-parse/src/commonTest/kotlin/MarkanywhereParserTest.kt) — `should parse the README parsing example`.

### Rendering Markdown as HTML

```kotlin
println(flowOf(markdown).parse().render())
```

Will print:

```text
<h1>
  Hello
</h1>
<p>
  A <em>streaming</em> parser, <b>live</b>.
</p>
```

> Backed by the first test in [`SemanticEventsRenderingTest`](markanywhere-render/src/commonTest/kotlin/SemanticEventsRenderingTest.kt) — `should render the README HTML example`.

### Converting HTML to Markdown

Because the same `SemanticEvent` stream can carry pure HTML (everything `isTagged = true`), the pipeline runs in reverse too: feed a real page's DOM — presentational wrappers, icon fonts, tracking scripts and all — and get clean Markdown back, ideal for handing a web page to an LLM.

```kotlin
val page = semanticEvents(tagged = true) {
    "body" {
        "h1" { +"Weather" }
        "p" {
            "i"("class" to "fa-solid fa-sun") { }
            +" Sunny and "
            "strong" { +"warm" }
            +" today — see the "
            "a"("href" to "https://example.com/forecast") { +"forecast" }
            +"."
        }
        "script" { +"track('view')" }
    }
}

println(page.transformHtmlToMarkdown().renderMarkdown())
```

Will print:

```text
# Weather

☀️ Sunny and **warm** today — see the [forecast](https://example.com/forecast).
```

`transformHtmlToMarkdown()` (in `markanywhere-html`) is a fixed chain of stream operators: `resolveIcons()` maps icon-font glyphs to emoji (`fa-sun` → ☀️), `simplifyHtml()` unwraps the presentational `<body>` and drops the `<script>` noise while keeping semantic tags and link `href`s, blank inline formatting and structural whitespace are dropped, and `encodeActionableRefs()` runs last; `renderMarkdown()` then serializes the result. Each operator is a plain `Flow<SemanticEvent>` extension, so you can compose your own subset — the module also ships `applyAccessibility()` (honour the browser's hidden-subtree and layout-table verdicts) for when you start from a raw capture.

The input here is built by hand, but in practice it comes from a captured page: [`markanywhere-dump`](markanywhere-dump) injects `window.markanywhere.dump()` into any browser, and [`markanywhere-browse`](markanywhere-browse) drives a real Chrome over CDP to produce a `SemanticEventDump`. That capture stamps every actionable element with a reference, and the final `encodeActionableRefs()` step turns those into `[forecast](ref:7:https://…)`-style links (a no-op here, since the hand-built input carries no refs) — so an agent can read the Markdown and then click an element back on the live page by its `ref`. See [`markanywhere-browse`](markanywhere-browse/README.md).

This actionable-ref encoding is the default (`refMode = RefMode.ENCODE`). Pass `transformHtmlToMarkdown(refMode = RefMode.STRIP)` instead for a clean, standard Markdown dump: the refs are dropped from `simplifyHtml`'s keep-set and `encodeActionableRefs()` is skipped, so no `ref:`/`ref="…"` appears anywhere and links carry only their real `href`.

> Backed by the first test in [`HtmlToMarkdownTest`](markanywhere-html/src/commonTest/kotlin/HtmlToMarkdownTest.kt) — `should convert the README HTML to Markdown example`.

### Rendering Markdown as DOM (Kotlin JS)

```kotlin
val markdownFlow = flowOf(markdown)
document.body!!.appendSemanticEvents(
    markdownFlow.parse()
)
```

Renders equivalent HTML into the browser's DOM tree.

Note: Typically `markdownFlow: Flow<String>` represents a Markdown text stream, for example from LLM inference.

> Backed by the first test in [`AppendSemanticEventsTest`](markanywhere-js/src/jsTest/kotlin/AppendSemanticEventsTest.kt) — `should append the README DOM example`.

### Transforming the event stream

Each reusable rule set is an extension on `TransformerBuilder` — think of one as an XSLT stylesheet you can compose with others:

```kotlin
fun TransformerBuilder.demoteHeadings() {
    match("h1") { "h2" { children() } } // re-emit <h1> as <h2>, keeping its content
}

fun TransformerBuilder.emphasizeToStrong() {
    match("em") { "strong" { children() } } // re-emit <em> as <strong>
}

println(
    flowOf(markdown)
        .parse()
        .transform {
            demoteHeadings()
            emphasizeToStrong()
            passthrough() // copy every other mark and its text verbatim
        }
        .render()
)
```

Will print:

```text
<h2>
  Hello
</h2>
<p>
  A <strong>streaming</strong> parser, <b>live</b>.
</p>
```

Each `match` is like an XSLT template: it selects marks by name (`"em"`), by the `"*"` wildcard, or by a predicate over the mark's attributes (`match({ name == "p" && this["role"] == "note" })`), and emits replacement marks and text in their place. `children()` mirrors `<xsl:apply-templates/>` — it descends into the matched subtree, and only there: an unmatched mark stops traversal unless a `"*"` rule opts into transparent descent. Raw text survives at exactly one place — a `matchText { +it }` rule — so `children()` alone never leaks text. Rules are tried in registration order with the first match winning, which is why the specific rules above sit ahead of `passthrough()`, the catch-all that copies every remaining mark (preserving its `isTagged` origin) and its text verbatim — here the literal `<b>` flows through untouched while the Markdown `*streaming*` is rewritten to `<strong>`.

Because the rule sets are plain extension functions, they compose — `transform { demoteHeadings(); emphasizeToStrong(); passthrough() }`. The block also runs per collection, so stateful rule sets stay correct when a flow is collected more than once: a sequence counter, text captured for an `afterClose { … }` summary emitted once the subtree closes, or a mode-scoped sub-pipeline (`children(mode = "capture")` routing a subtree to its own set of rules). The same machinery normalizes HTML down to Markdown (`simplifyHtml()` in `markanywhere-html`), redacts spans, or routes a tagged block to a separate sink.

> Backed by the first test in [`TransformerTest`](markanywhere-transform/src/commonTest/kotlin/TransformerTest.kt) — `should transform the README example`. The remaining tests there exercise each DSL feature mentioned above.

### Asserting event streams in tests

The same `semanticEvents { }` builder used to feed the renderer also describes an **expected** stream, so a parser/transformer test reads like the output it asserts:

```kotlin
@Test
fun `should parse the greeting`() = runTest {
    // given
    val markdownFlow = flowOf(markdown)
    
    // when
    val events = markdownFlow.parse()
        
    // then
    events.mergeAdjacentText() sameAs semanticEvents {
        "h1" { +"Hello" }
        "p" {
            +"A "
            "em" { +"streaming" }
            +" parser, "
            tag("b") { +"live" }
            +"."
        }
    }
}
```

- `semanticEvents { }` builds a `Flow<SemanticEvent>` from a tree-shaped DSL: `"name" { … }` opens a Markdown-derived mark, `tag("name") { … }` opens an HTML-tagged one (`isTagged = true`), and `+"…"` emits text.
- `sameAs` is a `suspend infix` that compares two event flows by serializing each to JSON lines — a mismatch prints a readable line diff.
- The parser fragments paragraph text on word boundaries (note the separate `"A"`, `" "`, `" parser,"` events in the parsing output above), so `mergeAdjacentText()` coalesces adjacent text events first, keeping the expectation concise.

`semanticEvents` and `mergeAdjacentText` ship in `markanywhere-flow`; `sameAs` ships in `markanywhere-test`:

```kotlin
testImplementation("com.xemantic.markanywhere:markanywhere-test:0.1.3")
```

> This is the exact pattern behind every "Backed by …" test linked above.

## Supported Markdown features

GFM is the dialect LLMs were trained on, which is why we take it as the baseline — not as a conformance target. The parser diverges where spec-correct behaviour would force buffering past the next emitted event (which would break streaming, the whole point of the library), and those divergent shapes are ones LLMs effectively never emit. The long-term aim is a separate spec, anchored on this parser, defined to fit the streaming model rather than the document model.

### GFM baseline

| Feature                    | Syntax                                                                                          |
|----------------------------|-------------------------------------------------------------------------------------------------|
| ATX headings               | `# H1` … `###### H6`                                                                            |
| Paragraphs                 | plain text blocks                                                                               |
| Hard line break            | two trailing spaces or `\` before newline                                                       |
| Thematic break             | `---` / `***` / `___`                                                                           |
| Fenced code block          | ` ```lang ` … ` ``` `                                                                           |
| Indented code block        | 4-space indent                                                                                  |
| Block quote                | `> text`                                                                                        |
| Unordered list             | `- item` / `* item` / `+ item`                                                                  |
| Ordered list               | `1. item`                                                                                       |
| Task list                  | `- [x] done` / `- [ ] todo`                                                                     |
| Table                      | `\| col \| col \|` with separator row                                                           |
| Inline code                | `` `code` ``                                                                                    |
| Strong                     | `**bold**` or `__bold__`                                                                        |
| Emphasis                   | `*italic*` or `_italic_`                                                                        |
| Strikethrough              | `~~text~~`                                                                                      |
| Inline link                | `[text](url)` / `[text](url "title")`                                                           |
| Inline image               | `![alt](src)`                                                                                   |
| Autolink                   | `<https://example.com>` / `<user@example.com>`                                                  |
| Extended autolink          | `www.example.com` / `https://…` (GFM §6.9)                                                      |
| Raw HTML (block)           | HTML type 1–7 blocks pass through                                                               |
| Raw HTML (inline)          | `<tag attr="val">…</tag>`                                                                       |
| Entity references          | `&amp;` / `&#42;` / `&#x2A;`                                                                    |
| Backslash escapes          | `\*` → literal `*`                                                                              |
| Link reference definitions | `[label]: url "title"` (back-references only — see [divergences](markanywhere-parse/README.md)) |

### Extensions beyond GFM

| Feature             | Syntax                               | Output element                        |
|---------------------|--------------------------------------|---------------------------------------|
| Highlight           | `==text==`                           | `<mark>`                              |
| Superscript         | `^text^`                             | `<sup>`                               |
| Inline math         | `$E=mc^2$`                           | `<math>`                              |
| Display math        | `$$`…`$$` on own lines               | `<math display="block">`              |
| Front matter        | `---`/`+++` fence at document start  | `<frontmatter format="yaml\|toml">`   |
| Namespaced tags     | `<ns:tag attr="val">`                | `Mark(name="ns:tag", isTagged=true)`  |
| DOCTYPE declaration | `<!DOCTYPE html>` (case-insensitive) | `Mark(name="doctype", isTagged=true)` |

**Namespaced tags** let you embed arbitrary custom markup in a Markdown document. Any `<namespace:tagname …>` / `</namespace:tagname>` pair passes through as `Mark`/`Unmark` events with `isTagged = true` and parsed attributes — your renderer handles them however it wants. This covers use cases like custom card components, alert boxes, or any domain-specific block type without requiring new parser syntax.

### GFM features not supported

| Feature                                                         | Reason                                                                                     |
|-----------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| Setext headings (`===` / `---` underline)                       | Requires one-line look-ahead to distinguish from paragraph + thematic break                |
| Forward reference links (`[text][label]` before `[label]: url`) | Definition must precede usage — the append-only stream cannot revisit emitted events       |
| Tight vs. loose lists                                           | Tight/loose can only be decided after the full list closes                                 |
| Mid-paragraph tables                                            | Tables only start at a fresh block boundary                                                |
| Multi-line inline constructs                                    | `flushInline` force-closes inline state (code/em/strong/etc.) at every line/block boundary |
| Image inside link (`[![alt](src)](url)`)                        | Nested inline constructs require speculative recursive parsing                             |
| Nested inline links (`[foo [bar](/u)](/u)`)                     | Inner link is treated as label content; spec requires parser unwinding                     |
| Multi-line link parsing                                         | Link destination, title, or label spanning newlines is not supported                       |

See [markanywhere-parse/README.md](markanywhere-parse/README.md) for a full list of divergences and their rationale.

## Modules

| Module                   | Purpose                                                             |
|--------------------------|---------------------------------------------------------------------|
| `markanywhere-api`       | `SemanticEvent` sealed type — the only interface between modules    |
| `markanywhere-parse`     | Streaming parser: `Flow<String>` → `Flow<SemanticEvent>`            |
| `markanywhere-render`    | HTML renderer: `Flow<SemanticEvent>` → HTML string                  |
| `markanywhere-transform` | DSL for rewriting event streams on the fly                          |
| `markanywhere-flow`      | Utilities for composing and splitting event flows                   |
| `markanywhere-extract`   | Utilities for extracting structured data from event streams         |
| `markanywhere-js`        | Kotlin/JS DOM renderer                                              |
| `markanywhere-dump`      | Injectable browser bundle exposing `window.markanywhere.dump()` — captures a live page's DOM as `SemanticEventDump` JSON |
| `markanywhere-browse`    | Drives real Chrome over CDP (via `kdriver`) to capture a live page as a `SemanticEventDump` and act on it by element reference |
| `markanywhere-html`      | HTML→Markdown pipeline `transformHtmlToMarkdown` (`resolveIcons`, `simplifyHtml`, `dropBlankInlineFormatting`, whitespace normalization, `encodeActionableRefs`), plus `applyAccessibility` |
| `markanywhere-test`      | Test helpers: `sameAs` for asserting `Flow<SemanticEvent>` equality |

You can depend only on `markanywhere-parse` and consume the `Flow<SemanticEvent>` with your own renderer — the API surface is a single three-variant sealed class. The `markanywhere-transform` module additionally lets you intercept and rewrite events before they reach any renderer.

## Elaborate rationale

We use language to convey meaning, and we use text to express language. The document-whether scroll, codex, or book-established a paradigm for how text is preserved as a packaged unit. Documents also introduced formatting: visual and structural conventions that signal the intent behind particular fragments of text within a larger context.

When we built machines to process text, we formalized this into "document formats". These formats naturally inherited the hierarchical structure of books-parts, chapters, sections, paragraphs-and the software we built assumed that documents exist as complete artifacts to be parsed, transformed, and rendered.

But something new has emerged. We started texting each other, and text became a stream of information: received, comprehended, and often discarded in the moment of reception. This is also the communication paradigm between humans and LLMs. The text is not a document to be opened and read-it is an unfolding stream, with alternating modalities, comprehended while being generated.

Structured documents are not the right abstraction here. What we need instead is an ontology of expressive meaning as a stream of events: each event signaling either an incremental fragment of text or a transition between modalities of linguistic expression (from prose to code, from paragraph to heading, from plain text to emphasis).
markanywhere inverts the traditional document processing flow. Rather than consuming complete documents and producing structure, it consumes streaming tokens and emits semantic events in real-time. These events can then be transformed-also as a stream-into various output formats: HTML, Markdown, XML, or whatever the receiving context requires.

## The ontology of a meaningful stream of text

The `SemanticEvent` can be a:

- `Text`: a chunk of characters
- `Mark` (e.g. `<em>` tag, with optional attributes)
- `Unmark` (e.g. `</div>`, indicating that previously opened mark is closed)

`Mark` and `Unmark` carry an `isTagged` flag distinguishing the origin of the event: `true` when it comes from an actual HTML/XML tag in the source, `false` when it is derived from Markdown syntax (e.g. `*text*` yields an `em` mark with `isTagged = false`, while `<em>text</em>` yields `isTagged = true`). The same `SemanticEvent` stream can therefore represent pure Markdown, pure HTML/XML (everything `isTagged = true`), or HTML embedded in Markdown — with the distinction preserved end-to-end so downstream renderers can treat each origin appropriately.

See the [SemanticEvent](markanywhere-api/src/commonMain/kotlin/SemanticEvents.kt) definition.

## Usage

In `build.gradle.kts` add:

```kotlin
dependencies {
    implementation("com.xemantic.markanywhere:markanywhere:0.1.3")
}
```

## Development

### Build target sets: `devBuild`

This is a Kotlin Multiplatform project published for JVM, JS, Wasm, and Native.
Building every target on every local run is slow, so the `markanywhere.convention`
plugin exposes a **`devBuild`** Gradle property that selects a minimal,
fast-to-build target set for local development:

- `devBuild` **defaults to `true`**, so a bare `./gradlew build` only touches the
  dev targets each module declares — JVM, plus browser-JS for the JS modules and
  the chain they depend on.
- CI passes **`-PdevBuild=false`** to build the full published set.

The convention plugin declares **no Kotlin target itself**. Instead each module
reads the flag via `val devBuild: Boolean by extra` and branches its own
`kotlin { }` target declarations on it, using two helpers the convention adds to
`KotlinMultiplatformExtension` — `allTargets()` (the full published set, honoring
the `targetGroup` flag) and `jsTarget()` (a configured browser+nodejs JS target):

```kotlin
import con.xemantic.markanywhere.buildlogic.allTargets

val devBuild: Boolean by extra

kotlin {
    if (devBuild) jvm() else allTargets()   // most modules
    sourceSets { /* … */ }
}
```

Variations:

- **JS-only modules** (and the modules in their dependency chain, which need a JS
  variant available in dev builds) declare browser-JS in dev too:
  `if (devBuild) { jvm(); js { browser() } } else allTargets()`, or for a JS-only
  module `if (devBuild) js { browser() } else allTargets()`.
- **Modules whose dependencies don't cover the whole set** list their targets by
  hand instead of calling `allTargets()`. For example `markanywhere-browse`
  depends on `kdriver` (`dev.kdriver:core`), which publishes only JVM, JS, and the
  desktop-native triples — no Wasm, Apple-mobile, or android-native — so it
  declares exactly that intersection in its `else` branch.

To build (or just configure) the complete multiplatform set locally, run any task
with `-PdevBuild=false`.

### DOM-dump fixtures and the `renderDumpFixtures` task

The end-to-end HTML→Markdown tests run against **captured DOM dumps**, not raw
HTML. Each fixture in `markanywhere-html/src/commonTest/dumps/*.json` is a
[`SemanticEventDump`](markanywhere-html/src/commonTest/kotlin/SemanticEventDump.kt):
the semantic event stream of a real page's rendered DOM tree, plus the `url` it
was captured from and the `dumpedAt` instant of the capture. The events — not any
original HTML — are the source of truth, so the bloated source HTML is not kept
in the repository.

A raw event stream is hard to read, so to regenerate the human-readable HTML a
dump represents (for example to see what input produced a given Markdown output):

```shell
./gradlew :markanywhere-html:renderDumpFixtures
```

This renders every dump back to pretty-printed HTML under
`markanywhere-html/build/renderedDumps/<name>.html`.
