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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
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
// too — so encoding state does not hide it, nor do invisible chars (a byte
// order mark, NBSP) at its edges or those of a JSON string within:
// - a value that parses as a JSON object. Parsing, not a look at the first
//   and last char, is what keeps human text that merely starts with a
//   bracket (`[Solved] …`, `{Draft} …`, `[2024] Annual report [PDF]`) —
//   parsing as strict JSON, since the parser takes a bare word for a value
//   and would read `[[Wiki]]` as a nested array;
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
// Past [MAX_PARSED_LENGTH] nothing is decoded or parsed: a value is state
// unless it reads as text, as written, and does not open like encoded state
// (a JSON object, array or string, a percent escape) — no page writes
// metadata that long, and a page-controlled value must not cost a tree many
// times its size.
internal fun isApplicationStateMeta(content: String): Boolean {
    val written = content.trimInvisible()
    return when {
        written.length <= MAX_META_VALUE_LENGTH -> written.isStructuralState()
        written.length <= MAX_PARSED_LENGTH -> when {
            written.isFlatArray() -> written.isLongFlatArrayState()
            // decoded and parsed only once it reads as text, so a blob is
            // dropped unparsed
            else -> !written.readsAsText() || written.isStructuralState()
        }
        // never parsed: opening like encoded state is state enough
        else -> !written.readsAsText() || written.opensLikeEncodedState()
    }
}

// Whether this value opens the way a value [isStructuralState] parses does:
// like a JSON object, array or string, or with a percent escape.
private fun String.opensLikeEncodedState(): Boolean = firstOrNull()?.let { it in "{[\"%" } == true

// Real metadata is short: `description` / `og:description` rarely exceed 300
// characters. Past this, a value must read as text to be kept.
private const val MAX_META_VALUE_LENGTH = 4096

// Past this, even a flat array is not parsed to be read by its words: its
// tree would cost many times the value's size, for a list no page writes.
private const val MAX_PARSED_LENGTH = 16 * MAX_META_VALUE_LENGTH

// Whether this value, percent-decoded when it is encoded, parses as state.
private fun String.isStructuralState(): Boolean {
    val decoded = percentDecodedOrNull(MAX_DECODING_DEPTH)
    val json = (decoded?.value ?: this).parseJsonCandidateOrNull() ?: return false
    return json.isState(decoded?.layersLeft ?: MAX_DECODING_DEPTH)
}

// A raw array none of whose elements is an object or an array.
private fun String.isFlatArray(): Boolean = firstOrNull() == '[' && !hasNestedElement()

// An over-long flat array: state if it parses as state, a blob if it does not
// parse and does not read as text, and otherwise judged by its elements
// joined, as the same list written plainly would be.
private fun String.isLongFlatArrayState(): Boolean {
    val json = parseJsonOrNull() as? JsonArray ?: return !readsAsText()
    if (json.isState(MAX_DECODING_DEPTH)) return true
    val text = json.joinToString(" ") { it.jsonPrimitive.content }
    return text.length > MAX_META_VALUE_LENGTH && !text.readsAsText()
}

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

// How many layers of encoding — percent-encoding and JSON strings, one budget
// for both — are undone, one within another, to find state. Real double
// encodings take two or three; the bound keeps a crafted value from costing
// a pass over itself per layer.
private const val MAX_DECODING_DEPTH = 8

// Only a value opening like a JSON object, array or string is parsed, and
// only strict JSON counts ([isStrictJson]).
private fun String.parseJsonCandidateOrNull(): JsonElement? {
    val first = firstOrNull()
    return if (first == '{' || first == '[' || first == '"') {
        parseJsonOrNull()?.takeIf { it.isStrictJson() }
    } else null
}

// Whether every scalar in this tree is a JSON value — a string, a number,
// `true`, `false` or `null` — and not a bare word, which the parser accepts
// even when not lenient (keys it does require quoted). Walked with a stack
// of its own, as a crafted value nests deeper than the call stack reaches.
private fun JsonElement.isStrictJson(): Boolean {
    val pending = ArrayDeque<JsonElement>()
    pending.addLast(this)
    while (pending.isNotEmpty()) {
        when (val element = pending.removeLast()) {
            is JsonObject -> pending.addAll(element.values)
            is JsonArray -> pending.addAll(element)
            is JsonPrimitive -> if (!element.isString
                && element.content != "true"
                && element.content != "false"
                && element !is JsonNull
                && !JSON_NUMBER.matches(element.content)
            ) return false
        }
    }
    return true
}

private val JSON_NUMBER = Regex("^-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][-+]?[0-9]+)?$")

// Recursion unwraps one JSON string per level, `layers` bounding the
// layers of encoding still to be undone ([MAX_DECODING_DEPTH]).
private fun JsonElement.isState(layers: Int): Boolean = when (this) {
    is JsonObject -> true
    is JsonArray -> any { it !is JsonPrimitive || it.isEncodedState(layers) }
    is JsonPrimitive -> isEncodedState(layers)
}

// A JSON string whose content — percent-decoded, when it is encoded — is
// state.
private fun JsonElement.isEncodedState(layers: Int): Boolean {
    if (this !is JsonPrimitive || !isString || layers == 0) return false
    val content = content.trimInvisible()
    val decoded = content.percentDecodedOrNull(layers - 1)
    return (decoded?.value ?: content).parseJsonCandidateOrNull()
        ?.isState(decoded?.layersLeft ?: (layers - 1)) == true
}

// Text is made of words: at least half the chars are letters (hex and
// number lists are mostly digits), a combining mark counting as one, since
// scripts like Devanagari and vowel-marked Arabic write vowels as marks; and
// at least one char in sixteen breaks a word — whitespace, a list separator
// (`,` `;`, so `a,b,c` keywords count), a path separator (`/`, so a list of
// URLs counts — base64 holds one in 64 chars, too few), or a letter of a
// script written without spaces ([isUnspacedScriptLetter] — base64 and hex
// never contain one; a Latin, Greek or Cyrillic letter, accented or not,
// breaks no word) — sparse enough for a list of long compound words, while the
// punctuation of serialised data (quotes, brackets, `=`, `|`, `\`) stays
// rare.
// Chars are counted as code points: a surrogate pair, which the common stdlib
// cannot classify, counts once, judged by its plane.
private fun String.readsAsText(): Boolean {
    var chars = 0
    var letters = 0
    var wordBreaks = 0
    var dataPunctuation = 0
    var i = 0
    while (i < length) {
        val c = this[i]
        chars++
        val low = getOrNull(i + 1)
        if (c.isHighSurrogate() && low != null && low.isLowSurrogate()) {
            when (supplementaryCodePoint(c, low)) {
                // the CJK ideograph extensions: letters of a script written
                // without spaces
                in 0x20000..0x3FFFF -> { letters++; wordBreaks++ }
                // historic scripts and styled mathematical letters, written
                // with spaces
                in 0x10000..0x1DFFF -> letters++
                // emoji, other symbols, private use: no letter, so a blob
                // encoded as emoji does not read as text
                else -> {}
            }
            i += 2
            continue
        }
        if (c.isLetter() || c.category in COMBINING_MARKS) letters++
        if (c.isWhitespace() || c == ',' || c == ';' || c == '/' || c.isUnspacedScriptLetter()) wordBreaks++
        else if (c in DATA_PUNCTUATION) dataPunctuation++
        i++
    }
    return letters * 2 >= chars && wordBreaks * 16 >= chars && dataPunctuation * 20 < chars
}

// The code point a surrogate pair encodes.
private fun supplementaryCodePoint(high: Char, low: Char): Int =
    0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)

// Whether this is a letter of a script written without spaces between words:
// Thai, Lao, Tibetan, Myanmar, Khmer, Japanese kana, Bopomofo, the CJK
// ideographs and Yi. (A supplementary-plane char, which the common stdlib
// cannot classify, is judged by its plane in [readsAsText] directly.)
private fun Char.isUnspacedScriptLetter(): Boolean =
    isLetter() && UNSPACED_SCRIPT_RANGES.any { code in it }

private val UNSPACED_SCRIPT_RANGES = listOf(
    0x0E00..0x0FFF, // Thai, Lao, Tibetan
    0x1000..0x109F, // Myanmar
    0x1780..0x17FF, // Khmer
    0x3040..0x31FF, // Hiragana, Katakana, Bopomofo, Katakana extensions
    0x3400..0x4DBF, // CJK Extension A
    0x4E00..0xA4CF, // CJK Unified Ideographs, Yi
    0xF900..0xFAFF, // CJK Compatibility Ideographs
    0xFF66..0xFF9F, // halfwidth Katakana
)

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

// A value with some layers of its encoding undone, and how many more may be.
private class Decoded(val value: String, val layersLeft: Int)

// This value with its percent-encoding undone — as many times over as it was
// applied, up to `layers` times — and invisible chars trimmed from its
// edges, or null when it is not percent-encoded: it does not open with an
// escape, its first decoding fails, or no layer may be undone.
private fun String.percentDecodedOrNull(layers: Int): Decoded? {
    var value = this
    var left = layers
    while (left > 0 && value.firstOrNull() == '%') {
        value = value.percentDecodedOnceOrNull()?.trimInvisible() ?: break
        left--
    }
    return if (left == layers) null else Decoded(value, left)
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
