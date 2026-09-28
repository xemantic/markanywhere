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

import com.xemantic.markanywhere.html.spec.isHtmlWhitespace
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Single-page apps use `<meta>` as a transport for application state
// (LinkedIn: `__init`, `spark/hash-includes`, Ember's percent-encoded
// `<app>/config/environment`, … — 95% of a page's Markdown, issue #82). A
// name denylist cannot keep up with names private to each site's framework,
// so this judges the *value*, which is what tells metadata apart from state:
// - a value that parses as a JSON object, raw or percent-encoded. Parsing,
//   not a look at the first and last char, is what keeps human text that
//   merely starts with a bracket (`[Solved] …`, `{Draft} …`,
//   `[2024] Annual report [PDF]`);
// - a JSON array that is percent-encoded, or holds anything but strings and
//   numbers (an object, a nested array, a flag, a `null`), or nothing at all.
//   A flat list of words or numbers is metadata a person writes
//   (`keywords`, `article:tag`, `citation_volume`);
// - a value longer than [MAX_META_VALUE_LENGTH] — the backstop for opaque
//   blobs of any other shape (base64, hash lists, truncated JSON).
internal fun isApplicationStateMeta(content: String): Boolean {
    val value = content.trim { it.isHtmlWhitespace() }
    if (value.length > MAX_META_VALUE_LENGTH) return true
    val first = value.firstOrNull()
    return when {
        first == '{' || first == '[' -> when (val json = value.parseJsonOrNull()) {
            is JsonObject -> true
            is JsonArray -> json.isEmpty() || json.any { !it.isWordOrNumber() }
            else -> false
        }
        first == '%' -> value.percentDecodedOrNull()?.parseJsonOrNull()
            .let { it is JsonObject || it is JsonArray }
        else -> false
    }
}

// Real metadata is short: `description` / `og:description` rarely exceed 300
// characters, and the cap still fits a full academic abstract
// (`citation_abstract`, `dc.description`).
private const val MAX_META_VALUE_LENGTH = 4096

private fun String.parseJsonOrNull(): JsonElement? = try {
    Json.parseToJsonElement(this)
} catch (_: SerializationException) {
    null
}

// kotlinx's tree reader takes any unquoted token as a literal (`[PDF]` parses
// as an array), so everything that is not a string, a flag or `null` counts —
// a number, or a word that no JSON writer would have produced.
private fun JsonElement.isWordOrNumber(): Boolean =
    this is JsonPrimitive && this !is JsonNull && (isString || content != "true" && content != "false")

// Decodes `%XX` escapes as UTF-8, or null when the value is not
// percent-encoded text (a malformed escape, a raw non-ASCII char).
private fun String.percentDecodedOrNull(): String? {
    val bytes = ByteArray(length)
    var size = 0
    var i = 0
    while (i < length) {
        val c = this[i]
        if (c == '%') {
            val high = getOrNull(i + 1)?.digitToIntOrNull(16) ?: return null
            val low = getOrNull(i + 2)?.digitToIntOrNull(16) ?: return null
            bytes[size++] = (high * 16 + low).toByte()
            i += 3
        } else {
            if (c.code > 0x7F) return null
            bytes[size++] = c.code.toByte()
            i++
        }
    }
    return bytes.decodeToString(0, size)
}
