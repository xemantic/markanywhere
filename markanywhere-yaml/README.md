# markanywhere-yaml

Streaming YAML codec for semantic event streams:
`Flow<String>.parseYaml()` turns a YAML document into a `Flow<SemanticEvent>`,
and `Flow<SemanticEvent>.asYaml()` / `renderYaml()` write such a stream back as YAML.

It is the YAML half of Markdown front matter —
`markanywhere-parse` hands it the body of a `---` block and `markanywhere-render` writes the block back with it —
but it has no dependency on either and parses any YAML document within the subset below.

## Event vocabulary

```yaml
title: My Post
date: 2026-05-10
draft: true
tags: [kotlin, markdown]
author:
  name: Alice
```

→

```text
Mark("entry", key="title")                    Text("My Post")    Unmark("entry")
Mark("entry", key="date", type="timestamp")   Text("2026-05-10") Unmark("entry")
Mark("entry", key="draft", type="bool")       Text("true")       Unmark("entry")
Mark("entry", key="tags")
  Mark("item") Text("kotlin") Unmark("item")
  Mark("item") Text("markdown") Unmark("item")
Unmark("entry")
Mark("entry", key="author")
  Mark("entry", key="name") Text("Alice") Unmark("entry")
Unmark("entry")
```

The vocabulary is deliberately small and the key lives in an attribute, so a key never collides with a tag name (`title`, `p`, `og:title`):

- `entry` (attribute `key`) is a mapping entry; `item` is a sequence element.
  A root mapping is a run of top-level `entry` marks, a root sequence a run of top-level `item` marks — there is no wrapper mark.
- The kind of value follows from the children: text only is a scalar, nested `entry` marks are a mapping, nested `item` marks are a sequence.
- `type` is present only on a non-string scalar: `bool`, `int`, `float`, `null`, `timestamp`.
  A plain scalar is typed exactly when some reader front matter is written for reads it as something other than a string — the YAML 1.2 core schema, plus the YAML 1.1 shapes Psych (Jekyll), PyYAML and go-yaml v2 (Hugo) still type — so a Jekyll / Hugo document round-trips as written.
  Beyond the core schema that means booleans and nulls in any letter case and `y` / `n`, numbers with `_` / `,` separators or a binary / octal / upper-case base prefix (`1_000`, `1,000`, `0b101`, `0X1F`), base-60 numbers (`12:30` is an `int`), and timestamps with a one-digit month or day or a colonless offset (`2016-01-01 12:00:00 -0500`); a date only when it is in the calendar, and a number only go-yaml v2 reads only within its 64-bit range (`1e999` stays a string).
  So `type="int"` / `type="float"` says a reader reads a number, not that the text is a Kotlin literal: reading the value yourself takes that reader's rules (drop the separators, resolve the prefix or the base 60).
  No text and no type is an empty string; a bare `key:` is `type="null"`; an empty `[]` / `{}` is `type="seq"` / `type="map"` with no children.
- `type="verbatim"` marks an `entry` / `item` whose value is outside the supported subset: its value is unknown (only a YAML reader knows it), and its text is the value's YAML source — the rest of the key or dash line, then every further line of the value, each ending in `\n`, with its indentation up to the key's or dash's column removed, so it re-indents along with its key.
  While a quoted scalar or flow collection the value opens is left open, every line belongs to it whatever its indentation, as the readers read it (to the end of the document, if it never closes).
  A consumer can tell a known value from an unknown one by this attribute alone, never by guessing from the text around an entry.
- All marks are untagged.

## Supported YAML

Block mappings and sequences (a sequence may sit at its key's own indentation), nesting, plain / double-quoted / single-quoted scalars, block scalars (`|`, `>`, chomping `-`/`+`, indentation indicator), single-line flow sequences and mappings, comments and blank lines (dropped), duplicate keys (all kept, in order), any line ending.

Streaming: a `key: value` line is held until the next line that is not blank or a comment, which decides whether the value continues on it.
A bare `key:` (or `-`) is held for one line to decide between a nested block, a verbatim value and a null value; a block scalar is buffered until it closes — never past the enclosing construct.

DIVERGENCE (never throws, never loses content): a value outside that subset — a multi-line flow collection or quoted scalar, a plain scalar continued on further lines, plain lines under a bare `key:`, a line the readers refuse after a value, a shape the front matter readers read differently from one another (`[draft:, x]`, `{a:[1]}`, `|-#note`) — is kept as source in a `type="verbatim"` entry or item (see above).
Any other line outside the subset — a complex `? key`, a directive, a document marker, a `- item` inside a mapping — is emitted **verbatim** (with its `\n`) as a text child of the container it sits in, its indentation up to the container's entries removed, so the writer re-indents it with them.
At the root, a line ahead of the first entry or item keeps its own indentation, which is what keeps an indented root indented once written back.
DIVERGENCE: a line less indented than an indented root (PyYAML refuses it, Psych and go-yaml v2 read the document up to it) and every line after it is text at the root with its own indentation — no entry is read that the readers do not see, but as the writer writes the root at column 0, such a document loses the fixpoint.
The writer writes both back as-is.
Anchors, aliases and tags are not resolved: a plain scalar starting with `&`, `*` or `!` is just a string.

## Writing

`renderYaml()` emits block style, one line per scalar, two spaces per nesting level.
A string that would re-parse as another type or shape (`"true"`, `"y"`, `"42"`, `"12:30"`, `"- dash"`, `"a: b"`, surrounding whitespace) is double-quoted, with every character YAML does not allow in a document `\u`-escaped; a multi-line string becomes a literal block scalar with the chomping indicator its trailing newlines call for; a key is written plain only when identifier-shaped and not a typed word (`"yes"`, `"y"`, `"null"`).
DIVERGENCE: a string some reader refuses to load when plain — `=`, `<<`, a base prefix with no digit (`0x_`), a date not in the calendar (`2024-13-45`) — is quoted too, although the parser reads it back as a plain string, so the first render quotes such a value in the source.
Any mark other than `entry` / `item` is transparent, so a `frontmatter` wrapper renders the same with or without it.
Every line is terminated — a non-empty document ends with `\n`, which a `|+` block scalar at the end needs.

Comments and the original quoting style are not preserved, so parse → render → parse is a fixpoint after the first render; `YamlRoundTripTest` pins the writer's quoting rules against the parser's typing rules.
