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

package com.xemantic.markanywhere.buildlogic

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.io.File
import java.time.Year

/**
 * Renders the license header of a generated source file from the IDE's own
 * copyright profile in [copyrightDir] (`.idea/copyright`, committed) rather than
 * from a hardcoded copy, so the two can never drift: the generated file gets
 * exactly the notice the IDE's copyright autosave would stamp on it, and a
 * regeneration shows up as a pure data diff instead of a header churn.
 *
 * The current [year] is a parameter rather than read from the clock here, so
 * that a task can declare it as an input (see [currentYear]) — otherwise the
 * task stays up to date across a new year and keeps the old header.
 *
 * Lives here, and not in the build script which uses it, for the same
 * configuration cache reason as [fixturePropertyName].
 */
fun licenseHeader(copyrightDir: File, existing: File, year: Int): String {
    val notice = defaultCopyrightNotice(copyrightDir)
    val body = notice
        // The profile's `$originalComment.match("Copyright (\d+)", 1, "-")$today.year`
        // year expression, evaluated the way the IDE evaluates it: keep the year
        // already present in the file being rewritten, extending it to a range
        // once the current year moves on.
        .replace(YEAR_EXPRESSION_REGEX) { copyrightYears(existing, year) }
    return buildString {
        append("/*\n")
        body.lineSequence().forEach { line ->
            append(if (line.isEmpty()) " *\n" else " * $line\n")
        }
        append(" */\n\n")
    }
}

private fun defaultCopyrightNotice(copyrightDir: File): String {
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

/**
 * The current year, as a task input [licenseHeader] needs — a [ValueSource], so
 * that the configuration cache reads the clock again on reuse instead of
 * replaying the year it was stored in.
 */
fun ProviderFactory.currentYear(): Provider<Int> = of(CurrentYear::class.java) {}

abstract class CurrentYear : ValueSource<Int, ValueSourceParameters.None> {
    override fun obtain(): Int = Year.now().value
}

private fun copyrightYears(existing: File, year: Int): String {
    val thisYear = year.toString()
    val firstYear = existing
        .takeIf { it.exists() }
        ?.let { Regex("""Copyright (\d+)""").find(it.readText())?.groupValues?.get(1) }
        ?: thisYear
    return if (firstYear == thisYear) thisYear else "$firstYear-$thisYear"
}

// IDEA escapes `$` in the notice as `&#36;`, so the year expression survives one
// XML-decoding pass in either form.
private val YEAR_EXPRESSION_REGEX =
    Regex("""(?:\$|&#36;)originalComment\.match\(.*?\)(?:\$|&#36;)today\.year""")

private val XML_OPTION_REGEX = Regex("""<option\s+name="([^"]*)"\s+value="([^"]*)"\s*/>""")

// Single left-to-right pass, so a doubly-escaped `&amp;#36;` decodes to the
// literal `&#36;` the profile actually stores, not all the way through to `$`.
private fun decodeXmlEntities(s: String): String =
    Regex("""&(?:#(\d+)|#x([0-9a-fA-F]+)|(amp|lt|gt|quot|apos));""").replace(s) { match ->
        val (decimal, hex, named) = match.destructured
        when {
            decimal.isNotEmpty() -> codePointText(decimal, radix = 10) ?: match.value
            hex.isNotEmpty() -> codePointText(hex, radix = 16) ?: match.value
            else -> when (named) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                else -> "'"
            }
        }
    }

// A code point outside the basic plane takes a surrogate pair, which `toChar()`
// would truncate; a reference to no code point at all is left as written.
private fun codePointText(digits: String, radix: Int): String? =
    digits.toIntOrNull(radix)
        ?.takeIf { Character.isValidCodePoint(it) }
        ?.let { Character.toString(it) }
