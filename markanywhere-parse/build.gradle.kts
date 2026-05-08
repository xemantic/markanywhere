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

import groovy.json.JsonSlurper
import java.net.URI

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("markanywhere.convention")
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

val generateNamedEntities by tasks.registering {
    description = "Rewrites src/commonMain/kotlin/NamedEntities.kt from $namedEntitiesUrl"
    group = "build"

    val url = namedEntitiesUrl
    val pkg = namedEntitiesPackage
    val cacheFile = namedEntitiesCacheFile
    val outFile = namedEntitiesSourceFile

    inputs.property("url", url)
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

kotlin {

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
