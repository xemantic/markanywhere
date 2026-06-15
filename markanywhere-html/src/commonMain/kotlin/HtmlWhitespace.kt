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

/**
 * The "ASCII whitespace" characters HTML treats as insignificant (WHATWG: TAB,
 * LF, FF, CR, SPACE).
 *
 * Exposed as a [CharArray] so it can be spread straight into the
 * [delimiters][CharSequence.split] vararg, e.g.
 * `classAttr.split(*HTML_WHITESPACE_CHARS)`, and backs [Char.isHtmlWhitespace]
 * as the single source of truth for the set.
 *
 * Deliberately narrower than [Char.isWhitespace], which also matches NBSP
 * (` `) and the other Unicode space separators that HTML renders as
 * **printable content** — they survive collapsing and never qualify a node as
 * blank (e.g. the non-breaking spaces in legal citations like `§ 823` /
 * `Abs. 1`).
 */
public val HTML_WHITESPACE_CHARS: CharArray = charArrayOf(' ', '\t', '\n', '\r', '\u000C')

/**
 * Whether this character is [HTML whitespace][HTML_WHITESPACE_CHARS].
 */
public fun Char.isHtmlWhitespace(): Boolean = this in HTML_WHITESPACE_CHARS

/**
 * Whether this string carries no renderable HTML content — it is empty or every
 * character is [HTML whitespace][isHtmlWhitespace].
 *
 * The HTML-faithful counterpart of [String.isBlank]: an NBSP-only string is
 * **not** HTML-blank, because HTML treats NBSP as content, not structural
 * whitespace.
 */
public fun String.isHtmlBlank(): Boolean = all { it.isHtmlWhitespace() }