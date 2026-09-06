/*
 * Copyright 2026 Kazimierz Pogoda / Xemantic
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import con.xemantic.markanywhere.buildlogic.allTargets
import groovy.json.JsonSlurper
import java.net.URI
import java.time.Year

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("markanywhere.convention")
}


val devBuild = extra["devBuild"] as Boolean

kotlin {

    explicitApi()

    // jvm + browser-js in dev: markanywhere-js (and its js test chain) needs a
    // js variant of this module, so dev builds expose one. The full set in CI.
    if (devBuild) { jvm(); js { browser() } } else allTargets()

    sourceSets {

        commonMain {
            dependencies {
                api(project(":markanywhere-api"))
                api(project(":markanywhere-flow"))
            }
        }

        commonTest {
            dependencies {
                implementation(project(":markanywhere-test"))
                implementation(project(":markanywhere-render"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlin.test)
                implementation(libs.xemantic.kotlin.test)
                implementation(libs.xemantic.kotlin.core)
            }
        }

    }

}

// ---------------------------------------------------------------------------
// HTML5 named character references (CommonMark/GFM §6.2)
//
// The spec's reference set is the WHATWG HTML5 list at
// https://html.spec.whatwg.org/entities.json — ~2125 names with the canonical
// trailing `;`. Hand-maintaining a subset diverges from the spec; instead the
// `generateNamedEntities` task fetches the authoritative table on demand and
// rewrites `src/commonMain/kotlin/NamedEntities.kt` in place. The file is
// checked into the repo so every KMP target ships the same complete data with
// no runtime resource lookup (which is not uniformly available across
// JVM/JS/Wasm/Native), and so normal builds don't depend on network access.
// The WHATWG list changes very rarely; refresh by running
// `./gradlew :markanywhere-parse:generateNamedEntities` and committing the
// resulting diff.
// ---------------------------------------------------------------------------

val namedEntitiesUrl = "https://html.spec.whatwg.org/entities.json"
val namedEntitiesPackage = "com.xemantic.markanywhere.parse"
val namedEntitiesSourceFile =
    layout.projectDirectory.file("src/commonMain/kotlin/NamedEntities.kt")
val namedEntitiesCacheFile =
    layout.buildDirectory.file("named-entities/entities.json")
val copyrightProfileDir = rootProject.layout.projectDirectory.dir(".idea/copyright")

val generateNamedEntities = tasks.register("generateNamedEntities") {
    description = "Rewrites src/commonMain/kotlin/NamedEntities.kt from $namedEntitiesUrl"
    group = "build"

    val url = namedEntitiesUrl
    val pkg = namedEntitiesPackage
    val cacheFile = namedEntitiesCacheFile
    val outFile = namedEntitiesSourceFile
    val copyrightDir = copyrightProfileDir

    inputs.property("url", url)
    inputs.files(fileTree(copyrightDir) { include("*.xml") })
        .withPropertyName("copyrightProfiles")
    outputs.file(outFile)

    doLast {
        val cache = cacheFile.get().asFile
        if (!cache.exists()) {
            cache.parentFile.mkdirs()
            URI(url).toURL().openStream().use { input ->
                cache.outputStream().use { input.copyTo(it) }
            }
        }

        @Suppress("UNCHECKED_CAST")
        val parsed = JsonSlurper().parse(cache) as Map<String, Any>
        val entries = parsed.entries.asSequence()
            .filter { it.key.endsWith(";") }
            .map { (k, v) ->
                val name = k.removePrefix("&").removeSuffix(";")
                @Suppress("UNCHECKED_CAST")
                val chars = (v as Map<String, Any>)["characters"] as String
                name to chars
            }
            .sortedBy { it.first }
            .toList()

        val kotlin = StringBuilder().apply {
            append(licenseHeader(copyrightDir.asFile, outFile.asFile))
            append(
                """
                |// Auto-generated from $url. Do not edit by hand.
                |// Regenerate with: ./gradlew :markanywhere-parse:generateNamedEntities
                |
                |package $pkg
                |
                |internal val NAMED_ENTITIES: Map<String, String> = mapOf(
                |""".trimMargin()
            )
            for ((name, chars) in entries) {
                append("    \"")
                appendEscaped(name)
                append("\" to \"")
                appendEscaped(chars)
                append("\",\n")
            }
            append(")\n")
        }

        outFile.asFile.writeText(kotlin.toString())
    }
}

// The license header is rendered from the IDE's own copyright profile in
// `.idea/copyright` (both files are committed) rather than hardcoded here, so
// the two can never drift: the generated file gets exactly the notice the IDE's
// copyright autosave would stamp on it, and a genuine entity-table refresh
// shows up as a pure data diff instead of a header churn.
fun licenseHeader(copyrightDir: File, existing: File): String {
    val notice = defaultCopyrightNotice(copyrightDir)
    val body = notice
        // The profile's `$originalComment.match("Copyright (\d+)", 1, "-")$today.year`
        // year expression, evaluated the way the IDE evaluates it: keep the year
        // already present in the file being rewritten, extending it to a range
        // once the current year moves on.
        .replace(YEAR_EXPRESSION_REGEX) { copyrightYears(existing) }
    return buildString {
        append("/*\n")
        body.lineSequence().forEach { line ->
            append(if (line.isEmpty()) " *\n" else " * $line\n")
        }
        append(" */\n\n")
    }
}

fun defaultCopyrightNotice(copyrightDir: File): String {
    val profiles = copyrightDir.listFiles { file: File ->
        file.extension == "xml" && file.name != "profiles_settings.xml"
    }?.sorted() ?: emptyList()
    check(profiles.isNotEmpty()) {
        "No copyright profile found in $copyrightDir"
    }
    val default = copyrightDir.resolve("profiles_settings.xml")
        .takeIf { it.exists() }
        ?.let { Regex("""<settings\s+default="([^"]+)"""").find(it.readText())?.groupValues?.get(1) }
    val notices = profiles.associate { profile ->
        val options = XML_OPTION_REGEX.findAll(profile.readText()).associate { match ->
            match.groupValues[1] to decodeXmlEntities(match.groupValues[2])
        }
        options["myName"] to options["notice"]
    }
    val notice = notices[default] ?: notices.values.firstOrNull()
    return checkNotNull(notice) {
        "Copyright profile '$default' in $copyrightDir declares no notice"
    }
}

fun copyrightYears(existing: File): String {
    val thisYear = Year.now().value.toString()
    val firstYear = existing
        .takeIf { it.exists() }
        ?.let { Regex("""Copyright (\d+)""").find(it.readText())?.groupValues?.get(1) }
        ?: thisYear
    return if (firstYear == thisYear) thisYear else "$firstYear-$thisYear"
}

// IDEA escapes `$` in the notice as `&#36;`, so the year expression survives one
// XML-decoding pass in either form.
val YEAR_EXPRESSION_REGEX =
    Regex("""(?:\$|&#36;)originalComment\.match\(.*?\)(?:\$|&#36;)today\.year""")

val XML_OPTION_REGEX = Regex("""<option\s+name="([^"]*)"\s+value="([^"]*)"\s*/>""")

// Single left-to-right pass, so a doubly-escaped `&amp;#36;` decodes to the
// literal `&#36;` the profile actually stores, not all the way through to `$`.
fun decodeXmlEntities(s: String): String =
    Regex("""&(?:#(\d+)|#x([0-9a-fA-F]+)|(amp|lt|gt|quot|apos));""").replace(s) { match ->
        val (decimal, hex, named) = match.destructured
        when {
            decimal.isNotEmpty() -> decimal.toInt().toChar().toString()
            hex.isNotEmpty() -> hex.toInt(16).toChar().toString()
            else -> when (named) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                else -> "'"
            }
        }
    }

fun StringBuilder.appendEscaped(s: String) {
    for (c in s) {
        when {
            c == '\\' -> append("\\\\")
            c == '"' -> append("\\\"")
            c == '$' -> append("\\\$")
            c.code in 0x20..0x7E -> append(c)
            else -> {
                append("\\u")
                append(c.code.toString(16).padStart(4, '0').uppercase())
            }
        }
    }
}
