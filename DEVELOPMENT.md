# Development

Notes for working on this project itself:
its build layout,
the conventions its documentation follows,
and the tooling and dependency updates worth doing from time to time.

## Build target sets: `devBuild`

This is a Kotlin Multiplatform project published for JVM, JS, Wasm, and Native.
Building every target on every local run is slow, so the `markanywhere.convention` plugin exposes a **`devBuild`** Gradle property that selects a minimal, fast-to-build target set for local development:

- `devBuild` **defaults to `true`**, so a bare `./gradlew build` only touches the dev targets each module declares — JVM, plus browser-JS for the JS modules and the chain they depend on.
- CI passes **`-PdevBuild=false`** to build the full published set.

The convention plugin declares **no Kotlin target itself**.
Instead each module reads the flag via `val devBuild = extra["devBuild"] as Boolean` and branches its own `kotlin { }` target declarations on it, using two helpers the convention adds to `KotlinMultiplatformExtension` — `allTargets()` (the full published set, honoring the `targetGroup` flag) and `jsTarget()` (a configured browser+nodejs JS target):

```kotlin
import com.xemantic.markanywhere.buildlogic.allTargets

val devBuild = extra["devBuild"] as Boolean

kotlin {
    if (devBuild) jvm() else allTargets()   // most modules
    sourceSets { /* … */ }
}
```

Variations:

- **JS-only modules** (and the modules in their dependency chain, which need a JS variant available in dev builds) declare browser-JS in dev too: `if (devBuild) { jvm(); js { browser() } } else allTargets()`, or for a JS-only module `if (devBuild) js { browser() } else allTargets()`.
- **Modules whose dependencies don't cover the whole set** list their targets by hand instead of calling `allTargets()`.
  For example `markanywhere-browse` depends on `kdriver` (`dev.kdriver:core`), which publishes only JVM, JS, and the desktop-native triples — no Wasm, Apple-mobile, or android-native — so it declares exactly that intersection in its `else` branch.

To build (or just configure) the complete multiplatform set locally, run any task with `-PdevBuild=false`.

## DOM-dump fixtures and the `renderDumpFixtures` task

The end-to-end HTML→Markdown tests run against **captured DOM dumps**, not raw HTML.
Each fixture in `markanywhere-html/src/commonTest/dumps/*.json` is a [`SemanticEventDump`](markanywhere-dump/src/commonMain/kotlin/SemanticEventDump.kt): the semantic event stream of a real page's rendered DOM tree, plus the `url` it was captured from and the `dumpedAt` instant of the capture.
The events — not any original HTML — are the source of truth, so the bloated source HTML is not kept in the repository.

A raw event stream is hard to read, so to regenerate the human-readable HTML a dump represents (for example to see what input produced a given Markdown output):

```shell
./gradlew :markanywhere-html:renderDumpFixtures
```

This renders every dump back to pretty-printed HTML under `markanywhere-html/build/renderedDumps/<name>.html`.

## Update gradlew wrapper

```shell
./gradlew wrapper --gradle-version latest --distribution-type bin
```

## Update all the dependencies to the latest versions

All the gradle dependencies are managed by the
[libs.versions.toml](gradle/libs.versions.toml) file in the `gradle` dir.

To resolve the latest versions,
and apply them automatically to [libs.versions.toml](gradle/libs.versions.toml),
run the [version-catalog-update](https://github.com/littlerobots/version-catalog-update-plugin) plugin:

```shell
./gradlew versionCatalogUpdate
```

To review and pick the updates one by one instead of applying them all,
use the interactive mode:

```shell
./gradlew versionCatalogUpdate --interactive
```

then apply the staged changes with:

```shell
./gradlew versionCatalogApplyUpdates
```

> [!NOTE]
> The plugin is configured in [build.gradle.kts](build.gradle.kts)
> to preserve the manual ordering of `libs.versions.toml` (`sortByKey = false`),
> and to keep the `kotlinTarget`, `javaTarget`, and `asm` version constants,
> which have no `version.ref` and would otherwise be removed as unused.

## Public API dumps

Every module keeps a checked-in dump of its public API under `<module>/api/`.
The `build` task verifies the sources against it (`apiCheck`),
so an unintended change to the published API surface fails the build.

After an intentional API change, regenerate the dumps and commit them:

```shell
./gradlew apiDump
```

> [!NOTE]
> The dumps are produced from the JVM target,
> so `markanywhere-js` — which declares no JVM target — has none.

## Documentation conventions

All the Markdown files in this project are authored with
[semantic line breaks](https://sembr.org/).
Each sentence starts on its own line,
and long sentences may be split further at clause boundaries.
This keeps `git diff` and code review focused on the sentence that actually changed,
instead of on a whole reflowed paragraph.

There is no maximum line length,
and paragraphs are never hard-wrapped to a fixed column.
Line length is a rendering concern,
so it is left to the editor.

### Markdown soft wrapping in the IDE

**IntelliJ IDEA**:
`Settings` → `Editor` → `General` → `Soft Wraps`,
enable `Soft-wrap these files` and make sure the mask contains `*.md`
(the default mask already does).
To toggle it for the file at hand only,
use `View` → `Active Editor` → `Soft-Wrap`.

**VS Code**:
add the following to your `settings.json`:

```json
{
  "[markdown]": {
    "editor.wordWrap": "on"
  }
}
```

Alternatively toggle it for the current file with `Alt`+`Z` (`Option`+`Z` on macOS).
