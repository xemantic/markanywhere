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

// The keys wrapInHtmlDocument turns into `<title>` and `<html lang>` rather
// than a `<meta>`, spelled as it reads them.
internal val HEAD_KEYS = setOf("title", "lang")

// The front matter `entry` types whose text is meaningful as head metadata
// (`null` and the empty collections are not); an entry without a `type` is a
// string.
internal val SCALAR_ENTRY_TYPES = setOf("bool", "int", "float", "timestamp")

// A front matter entry: the name as first spelled, and its value.
internal data class MetadataEntry(val key: String, val value: String)

// The `<head>` metadata a front matter holds, in insertion order and keyed
// the way HTML reads `<meta>` names: ASCII case-insensitively (HTML §4.2.5).
// The one duplicate policy [simplifyHtml] and [wrapInHtmlDocument] share, so
// the two stay inverses whichever way a document travels: the first
// occurrence of a name, in any letter case, wins — spelling and value.
internal class HeadMetadata {

    private val entries = LinkedHashMap<String, MetadataEntry>()

    val values: Collection<MetadataEntry> get() = entries.values

    fun isNotEmpty(): Boolean = entries.isNotEmpty()

    operator fun contains(name: String): Boolean = name.asciiLowercase() in entries

    operator fun get(name: String): MetadataEntry? = entries[name.asciiLowercase()]

    // Adds the entry unless its name is already present.
    fun add(key: String, value: String) {
        val name = key.asciiLowercase()
        if (name !in entries) entries[name] = MetadataEntry(key, value)
    }

    // Sets the entry whether or not its name is present, keeping the position
    // of an existing one.
    operator fun set(key: String, value: String) {
        entries[key.asciiLowercase()] = MetadataEntry(key, value)
    }

}
