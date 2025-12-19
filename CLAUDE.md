# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

markanywhere is a Kotlin Multiplatform library for streaming Markdown and Markup document formats as interchangeable hierarchical streams of semantic events. It inverts the traditional document processing flow: rather than consuming complete documents and producing structure, it consumes streaming tokens and emits semantic events in real-time.

## Build Commands

```shell
./gradlew build                                          # Build and test all modules
./gradlew test                                           # Run tests for all platforms
./gradlew jvmTest                                        # JVM tests only
./gradlew jsTest                                         # JavaScript tests only
./gradlew :markanywhere-parse:jvmTest                    # Single module, single platform
./gradlew dependencyUpdates                              # Check for dependency updates
./gradlew dokkaGeneratePublicationHtml                   # Generate API documentation
./gradlew publishAllPublicationsToMavenLocalRepository   # Publish to local Maven
```

## Architecture

### Module Structure

```
markanywhere-api        # Core SemanticEvent types (Text, Mark, Unmark) with JSON serialization
markanywhere-flow       # Kotlin Flow-based DSL for building SemanticEvent streams
markanywhere-parse      # Streaming Markdown parser (DefaultMarkanywhereParser)
markanywhere-render     # Renders SemanticEvent flows to HTML strings
markanywhere-transform  # Pattern-matching transformer for SemanticEvent flows
markanywhere-extract    # Extracts content from specific markup tags during streaming
markanywhere-js         # JavaScript DOM integration (appending events, reading elements)
markanywhere-test       # Test utilities
```

### Core Abstraction

The `SemanticEvent` sealed interface represents three event types:
- `Text(text: String)` - textual content
- `Mark(name: String, isTag: Boolean, attributes: Map?)` - opening tag/formatting
- `Unmark(name: String, isTag: Boolean)` - closing tag/formatting

The `isTag` flag distinguishes between events from Markdown syntax (`*text*` -> `em` with `isTag=false`) and embedded HTML (`<em>` -> `em` with `isTag=true`).

### Key Patterns

**Building event flows** (markanywhere-flow):
```kotlin
semanticEvents {
    "p" {                    // Opens <p>, closes after block
        +"Hello "            // Text event
        "strong" { +"world" } // Nested formatting
    }
}
```

**Parsing streaming Markdown** (markanywhere-parse):
```kotlin
flow { emit("# Hello\n**world**") }
    .parse(DefaultMarkanywhereParser())
    .render()  // Returns HTML string
```

**Transforming events** (markanywhere-transform):
```kotlin
val transformer = Transformer {
    match("thinking") { event ->
        "div"(mapOf("class" to "thinking")) { children() }
    }
}
flow.transform(transformer)
```

### Build Logic

The `build-logic` module contains `MarkanywhereConventionPlugin` which configures:
- Kotlin 2.3 with context-sensitive resolution
- JVM 17 target
- Power Assert for test assertions
- Explicit API mode
- JavaScript ES modules

### Testing Framework

Tests use `xemantic-kotlin-test` with Power Assert:
```kotlin
result should {
    have(text == "expected")
}
```

Always assert full error message text when testing exceptions.

### Multiplatform Targets

All modules target JVM and JS. The `markanywhere-js` module is JS-only for DOM integration.