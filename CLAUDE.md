# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Kotlin Multiplatform library template project using Gradle and the Xemantic conventions plugin. The project is designed to be published to Maven Central and supports a comprehensive set of platforms including JVM, JS, WebAssembly (WASM), and native targets (macOS, iOS, Linux, Windows, Android Native, watchOS, tvOS).

## Build Commands

### Basic Build and Test
```shell
./gradlew build
```

### Run Tests
```shell
./gradlew test
```

### Run Tests for Specific Platform
```shell
./gradlew jvmTest           # JVM only
./gradlew jsTest            # JavaScript only
./gradlew wasmJsTest        # WebAssembly JS only
./gradlew macosX64Test      # macOS x64 only
```

### Check for Dependency Updates
```shell
./gradlew dependencyUpdates
```

### Generate Documentation
```shell
./gradlew dokkaGeneratePublicationHtml
```

### Update Gradle Wrapper
```shell
./gradlew wrapper --gradle-version <version> --distribution-type bin
```

### Publishing
```shell
./gradlew publishAllPublicationsToMavenLocalRepository  # Local Maven repo
./gradlew jreleaserDeploy                               # Deploy to Maven Central
```

## Architecture

### Multiplatform Configuration

The project uses Kotlin Multiplatform with explicit API mode enabled, targeting:
- **JVM** (Java 17 target)
- **JavaScript** (browser + Node.js)
- **WebAssembly** (wasmJs for browser/Node.js/d8, wasmWasi for Node.js)
- **Native targets**: Comprehensive support across Tier 1-3 platforms including macOS, iOS, Linux, Windows (mingw), watchOS, tvOS, and Android Native

### Compiler Settings

- Kotlin API/Language version: 2.2
- JVM target: 17
- Progressive mode enabled
- Experimental features: Context parameters and context-sensitive resolution
- Extra warnings enabled
- Power Assert plugin configured for enhanced test assertions

### Source Layout

- `src/commonMain/kotlin/` - Shared Kotlin code across all platforms
- `src/commonTest/kotlin/` - Shared test code using kotlin-test and xemantic-kotlin-test
- Platform-specific sources can be added in `src/<platform>Main/kotlin/` and `src/<platform>Test/kotlin/`

### Testing Framework

Tests use `xemantic-kotlin-test` which provides an expressive DSL with Power Assert integration:
```kotlin
Foo should {
    have(BAR == "buzz")
}
```

The Power Assert plugin is configured to work with `com.xemantic.kotlin.test.assert` and `com.xemantic.kotlin.test.have` functions.

When testing for exceptions, always assert the full error message text:
- For single-line messages: `assert(error.message == "expected message")`
- For multi-line messages: use `error.message sameAs """...""".trimIndent()`

### Dependency Management

All dependencies are managed in `gradle/libs.versions.toml` using the version catalog feature. When adding new dependencies, update this file rather than inline declarations in build scripts.

### Build Configuration

- `build.gradle.kts` - Main build configuration with multiplatform setup, publishing, and JReleaser configuration
- `settings.gradle.kts` - Project name configuration
- `gradle.properties` - Kotlin compiler flags and project metadata

Some tests are disabled by default because they require XCode components:
- `tvosSimulatorArm64Test`
- `watchosSimulatorArm64Test`

### Publishing and Release

The project uses:
- Maven Central for artifact distribution via JReleaser
- GitHub for releases (manual through UI)
- Automated announcements to Discord, LinkedIn, and Bluesky
- Dokka for API documentation generation
- Binary compatibility validator for tracking API changes

### Xemantic Conventions

The project uses the `xemantic-conventions` Gradle plugin which provides:
- Standardized POM configuration
- License management (Apache 2.0)
- Developer information
- Publishing conventions
- Release announcement templates

### Library Configuration

This is set up as a library project with:
- Explicit API mode (`kotlin.explicitApi()`)
- Binary outputs for JS and WASM targets
- Source and javadoc JARs for Maven Central
- Binary compatibility validation