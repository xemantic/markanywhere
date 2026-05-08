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

A *streaming* parser.
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
{"type":"text","text":" "}
{"type":"text","text":"parser."}
{"type":"unmark","name":"p"}
```

The stream is append-only: each event is emitted as soon as the parser commits to it, so `<h1>` opens before `Hello` arrives and `<em>` opens the moment the `*` resolves.

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
  A <em>streaming</em> parser.
</p>
```

### Rendering Markdown as DOM (Kotlin JS)

```kotlin
val markdownFlow = flowOf(markdown)
document.body!!.appendSemanticEvents(
    markdownFlow.parse()
)
```

Renders equivalent HTML into the browser's DOM tree.

Note: Typically `markdownFlow: Flow<String>` represents a Markdown text stream, for example from LLM inference.

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
