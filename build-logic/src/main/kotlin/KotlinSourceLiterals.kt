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

package con.xemantic.markanywhere.buildlogic

/**
 * Derives a camelCase Kotlin property name from a fixture file name,
 * e.g. `bbc-news.json` becomes `bbcNews`.
 *
 * Lives here, and not in the build script which uses it, because a task action
 * referencing a build script function captures the script object, which the
 * Gradle configuration cache cannot serialize.
 */
fun fixturePropertyName(
    fileName: String
): String = fileName
    .substringBeforeLast('.')
    .split('-', '_', '.', ' ')
    .filter { it.isNotEmpty() }
    .mapIndexed { i, p ->
        if (i == 0) p.replaceFirstChar { it.lowercase() }
        else p.replaceFirstChar { it.uppercase() }
    }.joinToString("")

/**
 * Renders this string as Kotlin source for a `String` constant.
 *
 * A single string literal is capped at 65535 UTF-8 bytes in a JVM class file
 * (`CONSTANT_Utf8`), so the content is chunked and concatenated. 12000 chars
 * stays well under the limit even at 4 bytes/char worst case.
 *
 * Lives here for the same configuration cache reason as [fixturePropertyName].
 */
fun String.toKotlinStringLiteral(): String =
    if (isEmpty()) "\"\""
    else chunked(12000)
        .joinToString(" +\n        ") { chunk ->
            // Quotes and backslashes pass through a triple-quoted string
            // literally; only `$` can start interpolation. A multi-dollar string
            // with N leading `$` requires N consecutive `$` to interpolate, so
            // picking N one greater than the longest `$` run in the chunk makes
            // every `$` literal.
            val longestDollarRun =
                Regex("\\$+").findAll(chunk).maxOfOrNull { it.value.length } ?: 0
            val prefix = "$".repeat(longestDollarRun + 1)
            "$prefix\"\"\"$chunk\"\"\""
        }
