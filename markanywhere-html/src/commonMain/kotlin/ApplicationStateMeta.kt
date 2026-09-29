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

import com.xemantic.markanywhere.html.spec.stripHtmlWhitespace
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

// Single-page apps use `<meta>` as a transport for application state
// (LinkedIn: `__init`, `spark/hash-includes`, Ember's percent-encoded
// `<app>/config/environment`, … — 95% of a page's Markdown, issue #82). A
// name denylist cannot keep up with names private to each site's framework,
// so this judges the *value*, which is what tells metadata apart from state.
// A percent-encoded value's structure is judged by what it decodes to — undoing
// the encoding as many times over as it was applied, and inside a JSON string
// too — so encoding state does not hide it:
// - a value that parses as a JSON object. Parsing, not a look at the first
//   and last char, is what keeps human text that merely starts with a
//   bracket (`[Solved] …`, `{Draft} …`, `[2024] Annual report [PDF]`);
// - a JSON array holding an object or a nested array. A flat list of
//   scalars — an empty one included — is metadata a person writes
//   (`keywords`, `article:tag`, `citation_volume`), whether a bare word in it
//   reads as a string, a number, a flag or `null`;
// - a JSON string whose content is itself state by these rules — state
//   serialised twice, a common single-page-app double encoding — whether
//   it stands alone or as an element of an array;
// - a value longer than [MAX_META_VALUE_LENGTH] that does not read as text —
//   the backstop for opaque blobs of any other shape (base64, hash lists,
//   truncated JSON), which a long abstract in prose is not. It judges the
//   value as it will be written, so a percent-encoded one by its escapes,
//   never by what they decode to. A flat JSON array is measured by its
//   elements joined, not the quotes and commas serialising them — its length
//   as well as whether it reads as text — so it is judged as the same list
//   written plainly would be: a long list of words is kept, a long list of
//   hashes is not, and one whose elements fit under the cap is short.
internal fun isApplicationStateMeta(content: String): Boolean {
    val written = content.stripHtmlWhitespace()
    val decoded = written.percentDecodedOrNull()
    val value = decoded ?: written
    val long = written.length > MAX_META_VALUE_LENGTH
    // An over-long value is kept only if it reads as text, whatever it
    // parses as, so a blob — megabytes of JSON, say — is dropped unparsed.
    // Only a raw array must be parsed first, to be read by its words — unless
    // an element of it is an object or an array: then it is state if it
    // parses and a blob if it does not, dropped either way.
    val readByWords = decoded == null && value.firstOrNull() == '['
    if (long && (!readByWords || value.hasNestedElement()) && !written.readsAsText()) return true
    val json = value.parseJsonCandidateOrNull()
    if (json?.isState(MAX_DECODING_DEPTH) == true) return true
    if (!long || !readByWords) return false
    // a flat array is measured by its elements joined, in length as in text,
    // as the same elements written as a plain list would be
    val text = if (json is JsonArray) json.joinToString(" ") { it.jsonPrimitive.content } else written
    return text.length > MAX_META_VALUE_LENGTH && !text.readsAsText()
}

// Real metadata is short: `description` / `og:description` rarely exceed 300
// characters. Past this, a value must read as text to be kept.
private const val MAX_META_VALUE_LENGTH = 4096

// Whether this (raw, possibly malformed) array holds an object or an array
// — a bracket past the opening one outside a JSON string — found by a scan,
// without parsing.
private fun String.hasNestedElement(): Boolean {
    var inString = false
    var escaped = false
    for (i in 1 until length) {
        val c = this[i]
        when {
            escaped -> escaped = false
            inString -> when (c) {
                '\\' -> escaped = true
                '"' -> inString = false
            }
            c == '"' -> inString = true
            c == '{' || c == '[' -> return true
        }
    }
    return false
}

// How many layers of encoding — percent-encoding, a JSON string — are undone
// in all to find state. Real double encodings take two or three; the bound
// keeps a crafted value from costing a pass over itself per layer.
private const val MAX_DECODING_DEPTH = 8

// Only a value opening like a JSON object, array or string is parsed.
private fun String.parseJsonCandidateOrNull(): JsonElement? {
    val first = firstOrNull()
    return if (first == '{' || first == '[' || first == '"') parseJsonOrNull() else null
}

// Recursion unwraps one JSON string per level, `depth` bounding the levels
// ([MAX_DECODING_DEPTH]).
private fun JsonElement.isState(depth: Int): Boolean = when (this) {
    is JsonObject -> true
    is JsonArray -> any { it !is JsonPrimitive || it.isEncodedState(depth) }
    is JsonPrimitive -> isEncodedState(depth)
}

// A JSON string whose content — percent-decoded, when it is encoded — is
// state.
private fun JsonElement.isEncodedState(depth: Int): Boolean {
    if (this !is JsonPrimitive || !isString || depth == 0) return false
    val content = content.stripHtmlWhitespace()
    return (content.percentDecodedOrNull() ?: content).parseJsonCandidateOrNull()?.isState(depth - 1) == true
}

// Text is made of words: at least half the chars are letters (hex and
// number lists are mostly digits) — a combining mark counting as one, since
// scripts like Devanagari and vowel-marked Arabic write vowels as marks —, and at least one char in sixteen breaks a
// word — whitespace, a list separator (`,` `;`, so `a,b,c` keywords count),
// or a letter of a script written without spaces (anything past ASCII —
// base64 and hex never contain one) — sparse enough for a list of long
// compound words, while the punctuation of serialised data (quotes,
// brackets, `=`, `|`, `\`) stays rare.
// Chars are counted as code points: a surrogate pair — a letter of a
// supplementary-plane script (CJK Extension B, historic scripts) or an emoji,
// which the common stdlib cannot classify — counts once, as a letter of a
// script without spaces; serialised data never holds one.
private fun String.readsAsText(): Boolean {
    var chars = 0
    var letters = 0
    var wordBreaks = 0
    var dataPunctuation = 0
    var i = 0
    while (i < length) {
        val c = this[i]
        chars++
        if (c.isHighSurrogate() && getOrNull(i + 1)?.isLowSurrogate() == true) {
            letters++
            wordBreaks++
            i += 2
            continue
        }
        if (c.isLetter() || c.category in COMBINING_MARKS) letters++
        if (c.isWhitespace() || c == ',' || c == ';' || c.code > 0x7F && c.isLetter()) wordBreaks++
        else if (c in DATA_PUNCTUATION) dataPunctuation++
        i++
    }
    return letters * 2 >= chars && wordBreaks * 16 >= chars && dataPunctuation * 20 < chars
}

private val COMBINING_MARKS = setOf(
    CharCategory.NON_SPACING_MARK,
    CharCategory.COMBINING_SPACING_MARK,
)

private const val DATA_PUNCTUATION = "{}[]\"<>=|\\"

// Never throws: the value is page-controlled, and a malformed one is simply
// not JSON, whatever the parser reports it with.
private fun String.parseJsonOrNull(): JsonElement? = try {
    Json.parseToJsonElement(this)
} catch (_: Exception) {
    null
}

// This value with its percent-encoding undone — as many times over as it was
// applied, up to [MAX_DECODING_DEPTH] — and HTML whitespace stripped, or null
// when it is not percent-encoded: it does not open with an escape, or its
// first decoding fails.
private fun String.percentDecodedOrNull(): String? {
    var value = this
    var depth = 0
    while (depth < MAX_DECODING_DEPTH && value.firstOrNull() == '%') {
        value = value.percentDecodedOnceOrNull()?.stripHtmlWhitespace() ?: break
        depth++
    }
    return if (depth == 0) null else value
}

// Decodes `%XX` escapes as UTF-8, or null when the value is not
// percent-encoded text (a malformed escape, a raw non-ASCII char).
private fun String.percentDecodedOnceOrNull(): String? {
    val bytes = ByteArray(length)
    var size = 0
    var i = 0
    while (i < length) {
        val c = this[i]
        if (c == '%') {
            val high = getOrNull(i + 1)?.asciiHexDigitOrNull() ?: return null
            val low = getOrNull(i + 2)?.asciiHexDigitOrNull() ?: return null
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

// Unlike [Char.digitToIntOrNull], which also takes non-ASCII Unicode digits.
private fun Char.asciiHexDigitOrNull(): Int? = when (this) {
    in '0'..'9' -> this - '0'
    in 'a'..'f' -> this - 'a' + 10
    in 'A'..'F' -> this - 'A' + 10
    else -> null
}
