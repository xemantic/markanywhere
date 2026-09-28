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

// Single-page apps use `<meta>` as a transport for application state
// (LinkedIn: `__init`, `spark/hash-includes`, Ember's percent-encoded
// `<app>/config/environment`, … — 95% of a page's Markdown, issue #82). A
// name denylist cannot keep up with names private to each site's framework,
// so this judges the *value*, which is what tells metadata apart from state:
// - a value that parses as a JSON object or array, raw or percent-encoded.
//   Parsing, not a look at the first and last char, is what keeps human text
//   that merely starts with a bracket (`[Solved] …`, `{Draft} …`,
//   `[2024] Annual report [PDF]`);
// - a value longer than [MAX_META_VALUE_LENGTH] — the backstop for opaque
//   blobs of any other shape (base64, hash lists, truncated JSON).
internal fun isApplicationStateMeta(content: String): Boolean {
    val value = content.trim { it.isHtmlWhitespace() }
    return value.length > MAX_META_VALUE_LENGTH
            || value.isJsonContainer()
            || value.startsWith('%') && value.percentDecodedOrNull()?.isJsonContainer() == true
}

// Real metadata is short: `description` / `og:description` rarely exceed 300
// characters, and the cap still fits a full academic abstract
// (`citation_abstract`, `dc.description`).
private const val MAX_META_VALUE_LENGTH = 4096

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

private enum class JsonExpect { VALUE, VALUE_OR_END, KEY, KEY_OR_END, COLON, SEPARATOR_OR_END }

// Whether the whole string (JSON whitespace around it allowed) is one JSON
// object or array (RFC 8259). Iterative with an explicit stack, so nesting
// depth in untrusted input cannot overflow the call stack.
private fun String.isJsonContainer(): Boolean {
    var i = skipJsonWhitespace(0)
    if (getOrNull(i) != '{' && getOrNull(i) != '[') return false
    val open = StringBuilder()
    var expect: JsonExpect = VALUE
    while (true) {
        i = skipJsonWhitespace(i)
        val c = getOrNull(i) ?: return false
        when (expect) {
            VALUE, VALUE_OR_END -> when {
                c == ']' && expect == VALUE_OR_END -> {
                    open.setLength(open.length - 1)
                    i++
                    if (open.isEmpty()) return skipJsonWhitespace(i) == length
                    expect = SEPARATOR_OR_END
                }
                c == '{' -> {
                    open.append(c)
                    i++
                    expect = KEY_OR_END
                }
                c == '[' -> {
                    open.append(c)
                    i++
                    expect = VALUE_OR_END
                }
                else -> {
                    i = skipJsonScalar(i) ?: return false
                    expect = SEPARATOR_OR_END
                }
            }
            KEY, KEY_OR_END -> when {
                c == '}' && expect == KEY_OR_END -> {
                    open.setLength(open.length - 1)
                    i++
                    if (open.isEmpty()) return skipJsonWhitespace(i) == length
                    expect = SEPARATOR_OR_END
                }
                c == '"' -> {
                    i = skipJsonString(i) ?: return false
                    expect = COLON
                }
                else -> return false
            }
            COLON -> {
                if (c != ':') return false
                i++
                expect = VALUE
            }
            SEPARATOR_OR_END -> {
                val container = open.last()
                when (c) {
                    ',' -> expect = if (container == '{') KEY else VALUE
                    '}', ']' -> {
                        if (c != if (container == '{') '}' else ']') return false
                        open.setLength(open.length - 1)
                        if (open.isEmpty()) return skipJsonWhitespace(i + 1) == length
                    }
                    else -> return false
                }
                i++
            }
        }
    }
}

private fun String.skipJsonWhitespace(from: Int): Int {
    var i = from
    while (i < length && this[i] in " \t\n\r") i++
    return i
}

// The index past a string, number or literal starting at [from], or null.
private fun String.skipJsonScalar(from: Int): Int? = when (this[from]) {
    '"' -> skipJsonString(from)
    't' -> skipJsonLiteral(from, "true")
    'f' -> skipJsonLiteral(from, "false")
    'n' -> skipJsonLiteral(from, "null")
    else -> skipJsonNumber(from)
}

private fun String.skipJsonLiteral(from: Int, literal: String): Int? =
    if (startsWith(literal, from)) from + literal.length else null

private fun String.skipJsonString(from: Int): Int? {
    var i = from + 1
    while (i < length) {
        val c = this[i]
        when {
            c == '"' -> return i + 1
            c < ' ' -> return null
            c == '\\' -> {
                val escaped = getOrNull(i + 1) ?: return null
                i += when (escaped) {
                    in "\"\\/bfnrt" -> 2
                    'u' -> if (
                        (i + 2..i + 5).all { getOrNull(it)?.digitToIntOrNull(16) != null }
                    ) 6 else return null
                    else -> return null
                }
            }
            else -> i++
        }
    }
    return null
}

// `-? (0 | [1-9][0-9]*) (. [0-9]+)? ([eE] [+-]? [0-9]+)?`
private fun String.skipJsonNumber(from: Int): Int? {
    var i = from
    if (getOrNull(i) == '-') i++
    when (getOrNull(i)) {
        '0' -> i++
        in '1'..'9' -> i = skipDigits(i)
        else -> return null
    }
    if (getOrNull(i) == '.') {
        if (getOrNull(i + 1) !in '0'..'9') return null
        i = skipDigits(i + 1)
    }
    if (getOrNull(i) == 'e' || getOrNull(i) == 'E') {
        i++
        if (getOrNull(i) == '+' || getOrNull(i) == '-') i++
        if (getOrNull(i) !in '0'..'9') return null
        i = skipDigits(i)
    }
    return i
}

private fun String.skipDigits(from: Int): Int {
    var i = from
    while (getOrNull(i) in '0'..'9') i++
    return i
}
