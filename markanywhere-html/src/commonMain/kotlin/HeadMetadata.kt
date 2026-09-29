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

// Whether a value carries anything for a reader — a blank one does not.
internal fun isMetadataValue(value: String): Boolean = value.isNotBlank()

// A title as `document.title` reads a `<title>` — HTML whitespace stripped
// and collapsed — with any other whitespace at its edges (the NBSP padding an
// icon often leaves) trimmed too: inside, NBSP is content and stays, at an
// edge it only forces the title into quotes.
internal fun String.normalizeTitle(): String = stripAndCollapseHtmlWhitespace().trim()

// Whether a front matter entry spelled `candidate` supersedes an earlier one
// of the same name spelled `existing`, as front matter readers resolve a
// duplicate key: the later one wins (Psych — Jekyll's — and PyYAML), except
// that the lowercase spelling, the one a case-sensitive reader such as Jekyll
// looks up for `title`, beats a variant wherever it occurs.
internal fun frontMatterKeySupersedes(existing: String, candidate: String): Boolean {
    val name = candidate.asciiLowercase()
    return candidate == name || existing != name
}

// A front matter entry: the name as first spelled, and its value.
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

    // Adds a `<meta>` read from HTML, where the first of duplicate elements
    // is the one a query finds: the first occurrence of a name, in any letter
    // case, wins — spelling and value.
    fun addFromHtml(key: String, value: String) {
        if (!isMetadataValue(value)) return
        @OptIn(ExperimentalStdlibApi::class)
        entries.getOrPutIfMissing(key.asciiLowercase()) { MetadataEntry(key, value) }
    }

    // Adds a front matter entry the way its readers resolve a duplicate key
    // ([frontMatterKeySupersedes]). The name keeps the position of its first
    // occurrence.
    fun addFromFrontMatter(key: String, value: String) {
        if (!isMetadataValue(value)) return
        val name = key.asciiLowercase()
        val existing = entries[name]
        if (existing == null || frontMatterKeySupersedes(existing.key, key)) {
            entries[name] = MetadataEntry(key, value)
        }
    }

    // Sets the entry whether or not its name is present, keeping the position
    // of an existing one.
    operator fun set(key: String, value: String) {
        entries[key.asciiLowercase()] = MetadataEntry(key, value)
    }

}
