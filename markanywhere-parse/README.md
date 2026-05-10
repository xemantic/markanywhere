# markanywhere-parse

Streaming parser that turns a `Flow<String>` of source chunks into a `Flow<SemanticEvent>`.

The parser targets [GitHub Flavored Markdown](https://github.github.com/gfm/) (GFM)
as its compatibility baseline, with extensions for embedded HTML/XML and custom
namespaced markup tags.

## `isTagged` semantics

Every `Mark` and `Unmark` event carries an `isTagged` boolean. The parser sets it
according to the **origin** of the event in the source:

- `isTagged = false` — the event was derived from Markdown syntax. Examples:
  `*text*` → `<em>`, `# Heading` → `<h1>`, `    code` → `<pre><code>`,
  `- item` → `<ul><li>`.
- `isTagged = true` — the event was produced from an actual HTML/XML tag literally
  present in the source. Examples: `<em>text</em>`, `<div>...</div>`,
  `<my:tag attr="x">...</my:tag>`.

Tests therefore use the plain `semanticEvents { ... }` builder (which defaults to
`tagged = false`) for any expectation derived from Markdown syntax, and
`semanticEvents(tagged = true) { ... }` (or per-call `mark(name, isTagged = true)`)
only when the expectation models an embedded HTML/XML construct.

See [the project root README](../README.md) for the broader rationale behind the
`SemanticEvent` model.

## Extensions beyond GFM

The parser supports several features beyond the GFM spec:

### Highlight (`==text==`)

Double-equals delimiters emit `<mark>`:

```markdown
This is ==highlighted== text.
```

→ `<p>This is <mark>highlighted</mark> text.</p>`

### Superscript (`^text^`)

Caret delimiters emit `<sup>`:

```markdown
E=mc^2^
```

→ `<p>E=mc<sup>2</sup></p>`

### Inline math (`$…$`)

Single-dollar delimiters emit `<math>`. Opening only triggers when the character after `$` is a letter, `\`, or `{` — so bare currency (`$5`) and punctuation (`$.;'`) stay as plain text:

```markdown
Einstein's $E=mc^2$ equation.
```

→ `<p>Einstein's <math>E=mc^2</math> equation.</p>`

### Display math (`$$…$$`)

A `$$` fence on its own line opens a `<math display="block">` block:

````markdown
$$
\frac{a}{b} = c
$$
````

→ `<math display="block">\frac{a}{b} = c</math>`

### Front matter (YAML / TOML)

A `---` (YAML) or `+++` (TOML) fence at the very start of the document is captured as a `frontmatter` block when the second line looks like a valid key — `word:` (or `word : value`) for YAML, or `word =` / `[section]` for TOML. If the second line does not match, the fence falls through to a thematic break or paragraph as normal Markdown:

```markdown
---
title: My Post
date: 2026-05-10
---

Normal Markdown content here.
```

→ `Mark("frontmatter", attributes={"format": "yaml"})` + text content + `Unmark("frontmatter")`, followed by the regular document events.

### Namespaced custom tags (`<ns:tag …>`)

Any `<namespace:tagname attr="value">` / `</namespace:tagname>` pair in the source passes through as `Mark`/`Unmark` events with `isTagged = true` and fully parsed attributes. This lets you embed custom structured markup in a Markdown document without any parser changes — your downstream renderer handles the semantics:

```html
<myapp:card title="Getting started">
Normal **Markdown** content inside the card.
</myapp:card>
```

→ `Mark("myapp:card", isTagged=true, attributes={"title": "Getting started"})` … content events … `Unmark("myapp:card", isTagged=true)`

## Divergences from GFM

`markanywhere-parse` is a streaming parser whose primary use case is rendering
LLM output token-by-token. Strict CommonMark/GFM conformance in some places
requires unbounded look-ahead, which translates into user-visible latency in an
interactive session. Where the spec and the streaming model conflict, the parser
prefers low latency and emits eagerly. Each divergence is exercised by a test
named `DIVERGENCE - …` so the deviation is explicit and reviewable.

### Block structure does not reset inline state (GFM §3.1, example 12)

GFM specifies that block-structure indicators take precedence over inline
indicators, e.g. `` `one `` on one list item and `` two` `` on the next must
remain literal backticks rather than forming a code span. Honoring this rule
requires buffering every potential inline opener until the enclosing block ends.

We instead emit inline events as soon as a matching pair is seen, even when the
pair straddles a block boundary. In the pathological case the visual nesting may
disagree with GFM, but in practice LLM output never produces unmatched inline
markers across block boundaries.

See `Gfm_03_01_Test.kt`.

### Setext headings are not supported (GFM §4.3)

GFM allows headings written as a content line followed by an underline of `=`
(level 1) or `-` (level 2). The first line is indistinguishable from a paragraph
until the next line arrives, so honoring setext requires holding the entire
first line before emitting anything.

`markanywhere-parse` only recognizes ATX headings (`# Foo`, `## Bar`, …). A line
of `=` or `-` characters following text is parsed as a paragraph followed by a
thematic break (for `-`) or as ordinary paragraph content (for `=`).

A side benefit: the `---` ambiguity between a setext underline and a thematic
break disappears — `---` always means thematic break.

### Forward reference links not supported (GFM §4.7)

GFM allows `[label]: url "title"` definitions to appear *anywhere* in the
document, including after the references that use them. Resolving forward
references requires buffering the entire document so that a usage emitted
before its definition can be retroactively rewritten — incompatible with the
append-only stream.

Back-references **do** work: a `[text][label]` / `[label][]` / `[label]`
shortcut that appears *after* its definition resolves correctly. Only
single-line definition shapes are recognized (multi-line label, dest on next
line, or multi-line title are not). A usage that appears before its definition,
or a multi-line definition shape, emits its source text as literal characters.

### Lists are always rendered as loose (GFM §5.3)

GFM distinguishes "tight" lists (item content emitted directly) from "loose"
lists (item content wrapped in `<p>`). A list is loose iff any item is separated
from another by a blank line, which can only be decided after the entire list
has been seen.

`markanywhere-parse` always emits list items as if the list were loose, wrapping
their content in `<p>` regardless of blank-line layout in the source. This keeps
each item's events emittable as soon as the item itself is parsed.