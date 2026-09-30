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

package com.xemantic.markanywhere.html

import com.xemantic.markanywhere.html.spec.asciiLowercase
import com.xemantic.markanywhere.html.spec.stripAndCollapseHtmlWhitespace

// The keys wrapInHtmlDocument turns into `<title>` and `<html lang>` rather
// than a `<meta>`, spelled as it reads them.
internal val HEAD_KEYS = setOf("title", "lang")

// The front matter `entry` types whose text is meaningful as head metadata
// (`null` and the empty collections are not); an entry without a `type` is a
// string.
private val SCALAR_ENTRY_TYPES = setOf("bool", "int", "float", "timestamp")

// Whether an `entry` of this `type` holds a scalar that is head metadata —
// the rule wrapInHtmlDocument reads entries by and ensureFrontmatterTitle
// judges title entries by.
internal fun isScalarEntryType(type: String?): Boolean =
    type == null || type in SCALAR_ENTRY_TYPES

// Whether a value carries anything for a reader: a char that shows.
internal fun isMetadataValue(value: String): Boolean = value.any { !it.isInvisible() }

// Whitespace (NBSP included), a control char (a next line char, a C0
// control), an invisible format char such as a zero-width space or a byte
// order mark, or a letter or symbol that renders blank ([BLANK_GLYPHS]).
private fun Char.isInvisible(): Boolean =
    isWhitespace() || category == CONTROL || category == FORMAT || this in BLANK_GLYPHS

// Letters and symbols with no visible glyph — the Hangul fillers (choseong,
// jungseong, compatibility, halfwidth) and the blank Braille pattern: being no
// whitespace or format char, they are the common trick for a name that shows
// nothing.
private const val BLANK_GLYPHS = "\u115F\u1160\u3164\uFFA0\u2800"

// This value with the chars [isMetadataValue] finds invisible trimmed from
// its edges.
internal fun String.trimInvisible(): String = trim { it.isInvisible() }

// A title as `document.title` reads a `<title>` — HTML whitespace stripped
// and collapsed — with any other invisible char at its edges (the NBSP
// padding an icon often leaves, a byte order mark) trimmed too: inside, NBSP
// is content and stays, at an edge it only forces the title into quotes.
// A bidi control ([BIDI_CONTROLS]) is kept at an edge as well: invisible, yet
// it is what keeps the punctuation of a right-to-left title on its side.
// Whitespace that breaks a line (a vertical tab, a line or paragraph
// separator, a next line char) collapses like HTML whitespace, as a title is
// one line.
internal fun String.normalizeTitle(): String =
    CharArray(length) { if (this[it] in LINE_BREAKING_WHITESPACE) ' ' else this[it] }
        .concatToString()
        .stripAndCollapseHtmlWhitespace()
        .trim { it.isInvisible() && it !in BIDI_CONTROLS }

private const val LINE_BREAKING_WHITESPACE = "\u000B\u0085\u2028\u2029"

// The bidi marks, embeddings, overrides and isolates: the Arabic letter mark,
// the left-to-right and right-to-left marks, U+202A..U+202E and
// U+2066..U+2069.
private const val BIDI_CONTROLS =
    "\u061C\u200E\u200F\u202A\u202B\u202C\u202D\u202E\u2066\u2067\u2068\u2069"

// A language tag as `<html lang>` carries it: HTML strips its whitespace, and
// any other invisible char at its edges (a byte order mark, a zero-width
// space) is trimmed too — no valid BCP 47 tag holds one.
internal fun String.normalizeLang(): String = trimInvisible()

// Whether a front matter entry spelled `candidate` supersedes an earlier one
// of the same name spelled `existing`, as front matter readers resolve a
// duplicate key: the later one wins (Psych — Jekyll's — and PyYAML), except
// that the lowercase spelling, the one a case-sensitive reader such as Jekyll
// looks up for `title`, beats a variant wherever it occurs.
internal fun frontMatterKeySupersedes(existing: String, candidate: String): Boolean {
    val name = candidate.asciiLowercase()
    return candidate == name || existing != name
}

// Whether a front matter reader reads this entry's value over that of an
// earlier entry of the same name spelled `existingKey`, if any: it must be
// head metadata ([FrontMatterEntry.isHeadMetadata]), and must then
// [supersede][frontMatterKeySupersedes] the earlier one.
internal fun FrontMatterEntry.readOver(existingKey: String?): Boolean =
    isHeadMetadata && (existingKey == null || frontMatterKeySupersedes(existingKey, key))

// A metadata entry: its name, spelled as where its value was read, and the
// value.
internal data class MetadataEntry(val key: String, val value: String)

// The `<head>` metadata a front matter holds, in insertion order and keyed
// the way HTML reads `<meta>` names: ASCII case-insensitively (HTML §4.2.5).
// A blank value carries nothing for a reader, so it is never added, and
// cannot shadow a variant holding one. Duplicates resolve the way readers of
// the format they were read from resolve them — [simplifyHtml] reads HTML,
// [wrapInHtmlDocument] YAML. The two policies need not agree for the two to
// stay inverses: each emits a single entry per name.
internal class HeadMetadata {

    private val entries = LinkedHashMap<String, MetadataEntry>()

    val values: Collection<MetadataEntry> get() = entries.values

    fun isNotEmpty(): Boolean = entries.isNotEmpty()

    // Every lookup takes a name as spelled; folding it is this class's job.
    operator fun contains(name: String): Boolean = name.asciiLowercase() in entries

    operator fun get(name: String): MetadataEntry? = entries[name.asciiLowercase()]

    // Whether [addFromHtml] would add this `<meta>`: it carries something
    // for a reader and, the first of duplicate elements being the one a
    // query finds, no earlier one of its name, in any letter case, was added.
    // Lets a caller skip judging a value that would not be added anyway.
    fun acceptsFromHtml(key: String, value: String): Boolean =
        key !in this && isMetadataValue(value)

    // Adds a `<meta>` read from HTML when it [acceptsFromHtml] — the first
    // occurrence of a name wins, spelling and value.
    fun addFromHtml(key: String, value: String) {
        if (acceptsFromHtml(key, value)) entries[key.asciiLowercase()] = MetadataEntry(key, value)
    }

    // Adds a front matter entry the way its readers resolve a duplicate key
    // ([readOver], the rule ensureFrontmatterTitle picks the title entry by).
    // The name keeps the position of its first occurrence.
    fun addFromFrontMatter(entry: FrontMatterEntry) {
        val name = entry.key.asciiLowercase()
        if (entry.readOver(entries[name]?.key)) {
            entries[name] = MetadataEntry(entry.key, entry.text)
        }
    }

    // Sets the entry whether or not its name is present, keeping the position
    // of an existing one.
    operator fun set(key: String, value: String) {
        entries[key.asciiLowercase()] = MetadataEntry(key, value)
    }

}
