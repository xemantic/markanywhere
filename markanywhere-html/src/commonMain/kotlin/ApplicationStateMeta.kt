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
// so this judges the *value*, which is what tells metadata apart from state.
// A percent-encoded value is judged by what it decodes to, so the verdict
// never depends on the encoding:
// - a value that parses as a JSON object. Parsing, not a look at the first
//   and last char, is what keeps human text that merely starts with a
//   bracket (`[Solved] …`, `{Draft} …`, `[2024] Annual report [PDF]`);
// - a JSON array holding anything but strings and numbers (an object, a
//   nested array, a flag, a `null`). A flat list of words or numbers —
//   an empty one included — is metadata a person writes (`keywords`,
//   `article:tag`, `citation_volume`);
// - a value longer than [MAX_META_VALUE_LENGTH] that does not read as text —
//   the backstop for opaque blobs of any other shape (base64, hash lists,
//   truncated JSON), which a long abstract in prose is not.
internal fun isApplicationStateMeta(content: String): Boolean {
    val value = content.trim { it.isHtmlWhitespace() }
    if (value.length > MAX_META_VALUE_LENGTH && !value.readsAsText()) return true
    return value.isJsonState() || value.firstOrNull() == '%' &&
            value.percentDecodedOrNull()?.trim { it.isHtmlWhitespace() }?.isJsonState() == true
}

// Real metadata is short: `description` / `og:description` rarely exceed 300
// characters. Past this, a value must read as text to be kept.
private const val MAX_META_VALUE_LENGTH = 4096

private fun String.isJsonState(): Boolean {
    val first = firstOrNull()
    if (first != '{' && first != '[') return false
    return when (val json = parseJsonOrNull()) {
        is JsonObject -> true
        is JsonArray -> json.any { !it.isWordOrNumber() }
        else -> false
    }
}

// Prose breaks into words: at least one char in ten is whitespace, or a
// letter of a script written without spaces (anything past ASCII — base64
// and hex never contain one), while the punctuation of serialised data
// (quotes, brackets, `=`, `;`, `|`, `\`) stays rare.
private fun String.readsAsText(): Boolean {
    var wordBreaks = 0
    var dataPunctuation = 0
    for (c in this) {
        if (c.isWhitespace() || c.code > 0x7F && c.isLetter()) wordBreaks++
        else if (c in DATA_PUNCTUATION) dataPunctuation++
    }
    return wordBreaks * 10 >= length && dataPunctuation * 20 < length
}

private const val DATA_PUNCTUATION = "{}[]\"<>=;|\\"

// Never throws: the value is page-controlled, and a malformed one is simply
// not JSON, whatever the parser reports it with.
private fun String.parseJsonOrNull(): JsonElement? = try {
    Json.parseToJsonElement(this)
} catch (_: Exception) {
    null
}

// kotlinx's tree reader is no strict validator: it takes any unquoted token
// as a literal (`[PDF]` parses as an array, `{"a":abc}` as an object) and
// never checks a number, so the verdict rests on the element's shape, never
// on "it parsed". Everything that is not a string, a flag or `null` counts —
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
