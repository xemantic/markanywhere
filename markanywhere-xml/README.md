# markanywhere-xml

XML source → `Flow<SemanticEvent>`, through an `XmlParser` contract that more than one engine implements:

- `MarkanywhereXmlParser` — the hand-written multiplatform engine.
  A character-driven state machine, so a chunk boundary may fall anywhere and memory stays bounded by the largest single construct, not by the document.
- `StaxXmlParser` (JVM only) — a binding of the JDK's StAX pull parser, the reference the multiplatform engine is measured against.

```kotlin
val events: Flow<SemanticEvent> = chunks.parseXml(MarkanywhereXmlParser())
```

Elements become tagged `mark` / `unmark` events under their qualified name, attributes stay on the mark in document order (namespace declarations among them, as ordinary attributes), and character data, CDATA sections and references become `text`.
Comments, processing instructions, the XML declaration and the DOCTYPE are dropped.
The full mapping, and what happens to malformed input, is in the KDoc of `XmlParser`.

## Conformance

The contract is pinned by one shared suite, the abstract `XmlParserTest` in `commonTest`, which every engine runs through a subclass of its own.
Results are compared after `mergeAdjacentText()`, since where a run of text is split is up to the engine.

## Benchmarks

JMH benchmarks in `src/jvmBenchmark` compare the two engines on the same document, fed whole, in 4 KiB chunks, and in 16-character chunks as an LLM streams its output.

The latest results are in the summaries of the [Benchmark workflow runs](https://github.com/xemantic/markanywhere/actions/workflows/benchmark.yml).
See [DEVELOPMENT.md](../DEVELOPMENT.md#benchmarks) for running them, locally or on GitHub Actions.
