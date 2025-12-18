# markanywhere-kotlin

Stream Markdown or Markup document formats as interchangeable hierarchical streams of events

[<img alt="Maven Central Version" src="https://img.shields.io/maven-central/v/com.xemantic.markanywhere/markanywhere-kotlin">](https://central.sonatype.com/artifact/com.xemantic.markanywhere/markanywhere-kotlin)
[<img alt="GitHub Release Date" src="https://img.shields.io/github/release-date/xemantic/markanywhere-kotlin">](https://github.com/xemantic/markanywhere-kotlin/releases)
[<img alt="license" src="https://img.shields.io/github/license/xemantic/markanywhere-kotlin?color=blue">](https://github.com/xemantic/markanywhere-kotlin/blob/main/LICENSE)

[<img alt="GitHub Actions Workflow Status" src="https://img.shields.io/github/actions/workflow/status/xemantic/markanywhere-kotlin/build-main.yml">](https://github.com/xemantic/markanywhere-kotlin/actions/workflows/build-main.yml)
[<img alt="GitHub branch check runs" src="https://img.shields.io/github/check-runs/xemantic/markanywhere-kotlin/main">](https://github.com/xemantic/markanywhere-kotlin/actions/workflows/build-main.yml)
[<img alt="GitHub commits since latest release" src="https://img.shields.io/github/commits-since/xemantic/markanywhere-kotlin/latest">](https://github.com/xemantic/markanywhere-kotlin/commits/main/)
[<img alt="GitHub last commit" src="https://img.shields.io/github/last-commit/xemantic/markanywhere-kotlin">](https://github.com/xemantic/markanywhere-kotlin/commits/main/)

[<img alt="GitHub contributors" src="https://img.shields.io/github/contributors/xemantic/markanywhere-kotlin">](https://github.com/xemantic/markanywhere-kotlin/graphs/contributors)
[<img alt="GitHub commit activity" src="https://img.shields.io/github/commit-activity/t/xemantic/markanywhere-kotlin">](https://github.com/xemantic/markanywhere-kotlin/commits/main/)
[<img alt="GitHub code size in bytes" src="https://img.shields.io/github/languages/code-size/xemantic/markanywhere-kotlin">]()
[<img alt="GitHub Created At" src="https://img.shields.io/github/created-at/xemantic/markanywhere-kotlin">](https://github.com/xemantic/markanywhere-kotlin/commits)
[<img alt="kotlin version" src="https://img.shields.io/badge/dynamic/toml?url=https%3A%2F%2Fraw.githubusercontent.com%2Fxemantic%2Fmarkanywhere-kotlin%2Fmain%2Fgradle%2Flibs.versions.toml&query=versions.kotlin&label=kotlin">](https://kotlinlang.org/docs/releases.html)
[<img alt="discord users online" src="https://img.shields.io/discord/811561179280965673">](https://discord.gg/vQktqqN2Vn)
[![Bluesky](https://img.shields.io/badge/Bluesky-0285FF?logo=bluesky&logoColor=fff)](https://bsky.app/profile/xemantic.com)

## TL;DR

```markdown
# Very important expression of machine cognition

Hi, this is your LLM speaking.

<thinking>
OK, maybe I am too informal. **I will change the tone**.
</thinking>

Dear user of this system ...
```

So Markdown, but sometimes there is Markup inside, and it is streaming. How to tackle this.

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

See the [SemanticEvent](markanywhere-api/src/commonMain/kotlin/SemanticEvents.kt) definition.

## Usage

In `build.gradle.kts` add:

```kotlin
dependencies {
    implementation("com.xemantic.markanywhere:markanywhere:0.1-SNAPSHOT")
}
```
