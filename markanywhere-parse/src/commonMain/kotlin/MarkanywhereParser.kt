/*
 * Copyright 2025-2026 Kazimierz Pogoda / Xemantic
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

package com.xemantic.markanywhere.parse

import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.flow.SemanticEventScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

public fun Flow<String>.parse(): Flow<SemanticEvent> = flow {
    val outer: FlowCollector<SemanticEvent> = this
    val autolinker = AutolinkCollector(outer)
    val redirector = RedirectingCollector(autolinker)
    val state = ParserState(
        scope = SemanticEventScope(collector = redirector),
        redirector = redirector,
        autolinker = autolinker
    )
    val frontMatter = FrontMatterFilter(
        directDownstream = outer,
        processInner = { chunk -> state.processChunk(chunk) }
    )
    collect { chunk -> frontMatter.feed(chunk) }
    frontMatter.finalize()
    state.finalize()
    autolinker.finalize()
}

/**
 * A [FlowCollector] that the parser can use to **temporarily divert** emitted
 * [SemanticEvent]s into an in-memory buffer instead of forwarding them
 * downstream. Used to implement bounded-label buffering for inline link/image
 * label content (CommonMark §6.4 / §6.5) — events emitted while parsing
 * `[…label…]` are captured, then either replayed inside an `<a>` mark on
 * successful link resolution, or replayed as bare events surrounded by `[` and
 * `]` text on abort.
 *
 * The capture stack supports nesting, although the parser currently uses only
 * a single level (no nested label rendering: spec §6.4 forbids `[a [b](u)](u)`,
 * the inner link aborts back to text).
 */
internal class RedirectingCollector(
    private val downstream: FlowCollector<SemanticEvent>
) : FlowCollector<SemanticEvent> {
    private val captureStack = ArrayDeque<MutableList<SemanticEvent>>()

    fun startCapture() {
        captureStack.addLast(mutableListOf())
    }

    /** Pop and return the events captured since the matching [startCapture]. */
    fun stopCapture(): List<SemanticEvent> = captureStack.removeLast()

    val isCapturing: Boolean get() = captureStack.isNotEmpty()

    override suspend fun emit(value: SemanticEvent) {
        if (captureStack.isNotEmpty()) {
            val buf = captureStack.last()
            // Merge adjacent text events while capturing — the standard inline
            // parser emits one Text event per char when chars flow through
            // char-by-char, and the existing tests expect a single merged Text
            // event for label content. Doing the merge here keeps replay
            // identical to the pre-Phase-3a single-text emission shape.
            val last = buf.lastOrNull()
            if (last is Text && value is Text) {
                buf[buf.size - 1] = SemanticEvent.Text(last.text + value.text)
                return
            }
            buf += value
        } else {
            downstream.emit(value)
        }
    }

    /**
     * Replay [events] through [emit]. If a capture is active, events are
     * appended to it; otherwise they go downstream. Used both on commit
     * (replay inside `<a>` mark) and on abort (replay as bare events).
     */
    suspend fun replay(events: List<SemanticEvent>) {
        for (e in events) emit(e)
    }
}

public class DefaultMarkanywhereParser {
    public fun parse(chunks: Flow<String>): Flow<SemanticEvent> = chunks.parse()
}

@Suppress("RegExpRedundantEscape") // it is required for JS
private object Patterns {
    val HORIZONTAL_RULE = Regex("^-{3,}$")
    /** GFM thematic break: 3+ matching `-`, `*`, or `_`, separated by spaces/tabs. */
    val THEMATIC_BREAK = Regex("^[ \t]{0,3}(?:(?:\\*[ \t]*){3,}|(?:-[ \t]*){3,}|(?:_[ \t]*){3,})$")
    /** Up to 3 spaces of indentation are permitted before the `#` run (GFM). */
    val HEADING_WITH_SPACE = Regex("^ {0,3}#{1,6} $")
    val HEADING_NO_SPACE = Regex("^ {0,3}#{1,6}$")
    /** GFM ATX heading on a complete line: optional 0-3 space indent, `#{1,6}`, then EOL or whitespace + content. */
    val ATX_HEADING_LINE = Regex("^ {0,3}(#{1,6})(?:[ \t]+(.*?))?[ \t]*$")
    val TOO_MANY_HASHES = Regex("^#{7,}.*")
    val DASHES = Regex("^-+$")
    val TASK_UNCHECKED = Regex("^- \\[ \\] $")
    val TASK_CHECKED = Regex("^- \\[x\\] $")
    val TASK_PARTIAL = Regex("^- \\[[ x]?\\]?$")
    val ORDERED_LIST_ITEM = Regex("^\\d+\\. $")
    val ORDERED_LIST_PARTIAL = Regex("^\\d+\\.?$")
    val BLOCKQUOTE_EMPTY = Regex("^> ?$")
    val BLOCKQUOTE_DASH_PARTIAL = Regex("^> -?$")
    val WHITESPACE = Regex("\\s+")
    val ATTRIBUTE = Regex("""(\w+)=["']([^"']*)["']""")
    val HTML_OPEN_TAG = Regex("^<([a-zA-Z][a-zA-Z0-9-]*)(\\s|/?>|$)")
    val HTML_CLOSE_TAG = Regex("^</([a-zA-Z][a-zA-Z0-9-]*)\\s*>$")
}

/**
 * Parsed bullet-list marker (`-`, `+`, or `*`) or ordered-list marker (e.g. `1.`)
 * at the start of a line. [contentCol] is the column where item content begins.
 * [digits] holds the raw digit run for ordered markers (null for bullet markers),
 * used to emit the `<ol start="N">` attribute when the parsed value is not 1.
 * [markerChar] is the bullet character (`-`, `+`, `*`) for unordered, or the
 * delimiter (`.` or `)`) for ordered. Per GFM §5.4, a list ends when the next
 * marker uses a different bullet char (or different ordered delimiter), even
 * at the same indent level.
 */
private data class ListMarker(
    val ordered: Boolean,
    val markerStartCol: Int,
    val contentCol: Int,
    /** Index in the source line where the marker (and its trailing whitespace) ends. */
    val markerEndIndex: Int,
    val digits: String? = null,
    val markerChar: Char
)

/**
 * One level in the streaming list stack. [markerStartCol] and [contentCol] are
 * absolute columns (after expanding outer container offsets). The boolean flags
 * are mutated by the streaming state machine as lines flow in.
 */
private class ListContext(
    val ordered: Boolean,
    /** Bullet char (`-`, `+`, `*`) for unordered, or delimiter (`.`, `)`) for ordered. */
    var markerChar: Char,
    var markerStartCol: Int,
    var contentCol: Int,
    /** True while a `<p>` is currently open in the active item. */
    var paragraphOpen: Boolean = false,
    /** True while a `<pre><code>` block is currently open in the active item. */
    var codeBlockOpen: Boolean = false,
    /** Pending blank lines in an open code block (emitted lazily on next code line). */
    var codeBlankLines: Int = 0,
    /** True while a fenced `<pre><code>` block is currently open in the active item. */
    var fencedCodeOpen: Boolean = false,
    /** Marker char (`` ` `` or `~`) and length of the open fenced block, if any. */
    var fencedMarker: Char = ' ',
    var fencedLength: Int = 0,
    /** True while a display-math `<math display="block">` block is currently open in the active item. */
    var mathBlockOpen: Boolean = false,
    /** True while a `<blockquote>` is currently open in the active item. */
    var blockquoteOpen: Boolean = false,
    /** True while the inner `<p>` of an open blockquote is currently open. */
    var blockquoteParagraphOpen: Boolean = false,
    /**
     * True once any content (paragraph, code, heading, blockquote, etc.) has
     * been emitted for the active item. Used to decide whether a blank line
     * followed by a continuation line ends the list (an empty item followed
     * by a blank line ends the list — GFM §5.2 example 258).
     */
    var hasContent: Boolean = false,
    /**
     * Buffered (indent-stripped) table header line awaiting separator
     * confirmation on the next line (GFM §4.10 nested inside §5.2). Null
     * when no header is pending. On separator mismatch the header is drained
     * inline as paragraph text by `drainListPendingTableHeader`; only the
     * current (rejected) line is replayed through `processListBlock`.
     */
    var tableHeaderPending: String? = null,
    /** True while a `<table>` is currently open in the active item. */
    var tableOpen: Boolean = false,
    /** True once `<tbody>` has been emitted for the open table. */
    var tableBodyOpened: Boolean = false,
    var tableColumnCount: Int = 0,
    var tableAlignments: List<String?> = emptyList(),
    /**
     * In-progress HTML block (CommonMark §4.6) opened inside the active list
     * item. Null when no HTML block is open. Mirrors the top-level
     * `BlockMode.HtmlBlock1` / `BlockMode.HtmlBlock2to5` / `BlockMode.HtmlBlock6or7`
     * frames, but lives on the list context (instead of pushing onto
     * `blockModeStack`) so the list dispatcher remains the top-of-stack
     * handler — same trade-off as `tableOpen`/`tableHeaderPending`.
     * DIVERGENCE: list-internal HTML 6/7 blocks do NOT enter sub-parse mode
     * on a blank line — they stay in raw-text streaming mode so the list
     * dispatcher keeps its container-indent-stripping role.
     */
    var htmlBlock: ListHtmlBlockState? = null,
    /**
     * Tag name of an open custom markup block (`<ns:name …>`) inside the
     * active list item. Null when none is open. Mirrors the top-level
     * `BlockMode.CustomMarkup(tagName)` frame but lives on the list context
     * (same architectural pattern as `tableHeaderPending` / `htmlBlock`):
     * pushing a `CustomMarkup` frame above `ListBlock` would knock the list
     * dispatcher off the top-of-stack slot and break per-line container-
     * indent stripping.
     *
     * The detection is line-based: the opener is a whole line, content
     * lines are emitted as text (joined by `\n`), and the closer is a line
     * exactly equal to `</tagName>` (after `trimEnd`). [customMarkupHasContent]
     * tracks whether any content line has been emitted yet, so the leading
     * `\n` join is skipped before the first content (matches the top-level
     * `customMarkupSkipFirstNewline` behaviour).
     */
    var customMarkupTagName: String? = null,
    var customMarkupHasContent: Boolean = false
)

/**
 * State for an HTML block (CommonMark §4.6) currently open inside a list item.
 * [type] is the detected block type (1, 2, 3, 4, 5, 6 or 7). For type 1, 6, 7
 * [rootTagName] is the lowercased root tag, [openTags] tracks nested still-open
 * tags (root first), and [firstLineBuffer] holds the opening-tag chars until
 * the first `>` is seen (null once parsed). For type 2-5 [closingSeq] is the
 * sequence that ends the block (`-->`, `?>`, `>`, `]]>`). [isDoctype] flags
 * the structural DOCTYPE subset of type 4 — those emit `mark("doctype")` +
 * content + `unmark` rather than raw text.
 */
private class ListHtmlBlockState(
    val type: Int,
    val rootTagName: String = "",
    val rootIsClosingTag: Boolean = false,
    val closingSeq: String = "",
    val isDoctype: Boolean = false
) {
    val openTags: MutableList<String> = mutableListOf()
    var firstLineBuffer: StringBuilder? = StringBuilder()
}

/**
 * Parse a GFM list marker at the start of [line]. Allows up to 3 leading spaces
 * before the marker; rejects tabs in leading whitespace (those are handled by an
 * outer container's strip step). Returns null if [line] does not start a list item.
 */
private fun parseListMarker(line: String): ListMarker? {
    var col = 0
    var i = 0
    while (i < line.length && col < 4 && line[i] == ' ') { col++; i++ }
    if (col >= 4 || i >= line.length) return null
    val markerStartCol = col
    val ch = line[i]
    val ordered: Boolean
    val markerWidth: Int
    var digits: String? = null
    val markerChar: Char
    when {
        ch in "-+*" -> { ordered = false; markerWidth = 1; markerChar = ch; i++ }
        ch in '0'..'9' -> {
            val digitStart = i
            while (i < line.length && line[i].isDigit()) i++
            if (i - digitStart > 9 || i >= line.length) return null
            if (line[i] != '.' && line[i] != ')') return null
            digits = line.substring(digitStart, i)
            markerChar = line[i]
            i++
            ordered = true; markerWidth = i - digitStart
        }
        else -> return null
    }
    val markerEndCol = markerStartCol + markerWidth
    if (i >= line.length) {
        return ListMarker(ordered, markerStartCol, markerEndCol + 1, i, digits, markerChar)
    }
    when (line[i]) {
        ' ' -> {
            var j = i
            var cc = markerEndCol
            while (j < line.length && line[j] == ' ' && cc - markerEndCol < 5) {
                cc++; j++
            }
            // Marker followed only by whitespace (empty item on this line): per
            // CommonMark, content (if any continuation arrives) begins at
            // markerEndCol + 1, regardless of how many trailing spaces follow.
            if (j >= line.length) {
                return ListMarker(ordered, markerStartCol, markerEndCol + 1, line.length, digits, markerChar)
            }
            val spacesAfter = cc - markerEndCol
            val contentCol = if (spacesAfter >= 5) markerEndCol + 1 else cc
            val end = if (spacesAfter >= 5) i + 1 else j
            return ListMarker(ordered, markerStartCol, contentCol, end, digits, markerChar)
        }
        '\t' -> {
            // Tab after marker: per GFM, marker + 1-space-equivalent consumes 1 col.
            // The unconsumed remainder of the tab stays as content indent.
            return ListMarker(ordered, markerStartCol, markerEndCol + 1, i + 1, digits, markerChar)
        }
        else -> return null
    }
}

/**
 * Parsed opening fence of a GFM fenced code block (§4.5).
 * [language] is the first whitespace-delimited token of the info string (or null
 * if the info string is empty/whitespace-only).
 */
private data class FenceOpen(
    val marker: Char,        // '`' or '~'
    val length: Int,         // count of marker chars (≥3)
    val indent: Int,         // 0..3 leading spaces of the opening fence
    val language: String?
)

/**
 * Try to parse [line] as a fenced code-block opening fence. Returns null if [line]
 * is not a valid open: more than 3 leading spaces, fewer than 3 marker chars, or
 * (for `` ` `` fences) backticks anywhere in the info string.
 */
private fun parseFenceOpen(line: String): FenceOpen? {
    var i = 0
    while (i < line.length && i < 4 && line[i] == ' ') i++
    if (i >= 4) return null
    val indent = i
    if (i >= line.length) return null
    val marker = line[i]
    if (marker != '`' && marker != '~') return null
    val markerStart = i
    while (i < line.length && line[i] == marker) i++
    val length = i - markerStart
    if (length < 3) return null
    val info = line.substring(i)
    if (marker == '`' && '`' in info) return null
    val language = info.trim().takeIf { it.isNotEmpty() }
        ?.substringBefore(' ')
        ?.substringBefore('\t')
        ?.takeIf { it.isNotEmpty() }
        ?.let(::decodeEntities)
        ?.let(::applyBackslashEscapes)
    return FenceOpen(marker, length, indent, language)
}

/**
 * True if [line] is a valid closing fence for an open code block of [marker]
 * char and at least [openLength] marker chars. Allows 0–3 spaces of leading
 * indent and only spaces after the marker run (GFM §4.5).
 */
private fun isFenceClose(line: String, marker: Char, openLength: Int): Boolean {
    var i = 0
    while (i < line.length && i < 4 && line[i] == ' ') i++
    if (i >= 4) return false
    val markerStart = i
    while (i < line.length && line[i] == marker) i++
    val length = i - markerStart
    if (length < openLength) return false
    while (i < line.length) {
        if (line[i] != ' ') return false
        i++
    }
    return true
}

/**
 * Number of indentation columns at the start of [line], counting tabs as advancing
 * to the next tab stop with width 4 (per GFM section 2.2 — Tabs). [startCol] is the
 * absolute column at which [line] begins (non-zero when [line] is the content view
 * of a container like a blockquote or list item) so tab stops stay aligned.
 */
private fun leadingIndentCols(line: String, startCol: Int = 0): Int {
    var col = startCol
    for (c in line) {
        when (c) {
            ' ' -> col++
            '\t' -> col += 4 - (col % 4)
            else -> return col - startCol
        }
    }
    return col - startCol
}

/**
 * Return [line] with [cols] columns of leading indentation removed. If a tab
 * straddles the boundary, the unconsumed portion of the tab is replaced with
 * spaces so the remaining content keeps its original column position. Tabs
 * inside the content (after the consumed prefix) are preserved literally.
 * [startCol] is the absolute column at which [line] begins (see
 * [leadingIndentCols]).
 */
private fun stripIndentCols(line: String, cols: Int, startCol: Int = 0): String {
    var col = startCol
    var i = 0
    val target = startCol + cols
    while (i < line.length && col < target) {
        when (line[i]) {
            ' ' -> { col++; i++ }
            '\t' -> {
                val advance = 4 - (col % 4)
                if (col + advance <= target) {
                    col += advance
                    i++
                } else {
                    val spacesLeft = (col + advance) - target
                    return " ".repeat(spacesLeft) + line.substring(i + 1)
                }
            }
            else -> break
        }
    }
    return line.substring(i)
}

// CommonMark HTML block type 1 start tags
private val HTML_BLOCK_TYPE1_TAGS = setOf(
    "pre", "script", "style", "textarea"
)

/**
 * GFM §6.11 Disallowed Raw HTML extension: tag names that must be rendered
 * as literal text rather than as `mark`/`unmark` events. The check is
 * case-insensitive (`<XMP>` is equivalent to `<xmp>`). Several names are
 * already filtered upstream because they're in [HTML_BLOCK_TYPE1_TAGS] or
 * [HTML_BLOCK_TYPE6_TAGS] — listing them here is redundant but harmless;
 * `xmp`, `noembed`, `plaintext` are the names not covered elsewhere.
 */
private val GFM_DISALLOWED_TAGS = setOf(
    "title", "textarea", "style", "xmp", "iframe",
    "noembed", "noframes", "script", "plaintext"
)

// CommonMark HTML block type 6 block-level tag names
private val HTML_BLOCK_TYPE6_TAGS = setOf(
    "address", "article", "aside", "base", "basefont", "blockquote", "body",
    "caption", "center", "col", "colgroup", "dd", "details", "dialog",
    "dir", "div", "dl", "dt", "fieldset", "figcaption", "figure", "footer",
    "form", "frame", "frameset", "h1", "h2", "h3", "h4", "h5", "h6",
    "head", "header", "hr", "html", "iframe", "legend", "li", "link",
    "main", "menu", "menuitem", "nav", "noframes", "ol", "optgroup",
    "option", "p", "param", "search", "section", "summary", "table",
    "tbody", "td", "tfoot", "th", "thead", "title", "tr", "track", "ul"
)

private const val ASCII_PUNCTUATION = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

/** Applies CommonMark backslash escapes to a string: `\X` → `X` for any ASCII punctuation X. */
private fun applyBackslashEscapes(s: String): String {
    if ('\\' !in s) return s
    val out = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length && s[i + 1] in ASCII_PUNCTUATION) {
            out.append(s[i + 1])
            i += 2
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString()
}

/**
 * Percent-encodes URL-unsafe ASCII characters in a link href the same way
 * GFM/CommonMark normalizes URLs. Encodes `` ` `` → `%60`, `\` → `%5C`, space →
 * `%20`, `<` → `%3C`, `>` → `%3E`, `"` → `%22`, `{` → `%7B`, `}` → `%7D`,
 * `|` → `%7C`, `^` → `%5E`. Other characters (including already-percent-encoded
 * sequences) pass through; non-ASCII handling is done by [percentEncodeNonAscii].
 */
private fun normalizeUrlEscapes(s: String): String {
    if (s.none { it.code <= 0x7F && it in URL_UNSAFE_ASCII }) return s
    val out = StringBuilder(s.length)
    for (c in s) {
        if (c.code <= 0x7F && c in URL_UNSAFE_ASCII) {
            out.append('%')
            out.append(HEX_DIGITS[(c.code ushr 4) and 0xF])
            out.append(HEX_DIGITS[c.code and 0xF])
        } else {
            out.append(c)
        }
    }
    return out.toString()
}

private val URL_UNSAFE_ASCII = setOf(' ', '"', '<', '>', '[', '\\', ']', '^', '`', '{', '|', '}')
private val HEX_DIGITS = "0123456789ABCDEF".toCharArray()

/**
 * GFM §6.8 URI-autolink validation: scheme `[A-Za-z][A-Za-z0-9+.-]{1,31}`,
 * a `:`, then any chars except space, ASCII control, `<`, `>`. Backslash
 * escapes do not apply inside autolinks, so [content] is the raw buffer.
 */
private fun isValidUriAutolink(content: String): Boolean {
    val colonIdx = content.indexOf(':')
    if (colonIdx !in 2..32) return false
    val first = content[0]
    if (!(first in 'A'..'Z' || first in 'a'..'z')) return false
    for (i in 1 until colonIdx) {
        val c = content[i]
        if (!(c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' ||
              c == '+' || c == '-' || c == '.')) return false
    }
    for (i in colonIdx + 1 until content.length) {
        val c = content[i]
        if (c == ' ' || c == '<' || c == '>' || c.code < 0x20 || c.code == 0x7F) return false
    }
    return true
}

/**
 * GFM §6.8 email-autolink regex:
 *   `[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*`
 */
private fun isValidEmailAutolink(content: String): Boolean {
    val atIdx = content.indexOf('@')
    if (atIdx <= 0 || atIdx == content.length - 1) return false
    for (i in 0 until atIdx) {
        val c = content[i]
        if (!(c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' ||
              c in EMAIL_LOCAL_PART_PUNCT)) return false
    }
    var i = atIdx + 1
    while (i < content.length) {
        val labelStart = i
        while (i < content.length && content[i] != '.') i++
        val labelEnd = i
        val labelLen = labelEnd - labelStart
        if (labelLen == 0 || labelLen > 63) return false
        val firstC = content[labelStart]
        val lastC = content[labelEnd - 1]
        if (!(firstC in 'A'..'Z' || firstC in 'a'..'z' || firstC in '0'..'9')) return false
        if (!(lastC in 'A'..'Z' || lastC in 'a'..'z' || lastC in '0'..'9')) return false
        for (j in labelStart until labelEnd) {
            val c = content[j]
            if (!(c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-')) return false
        }
        if (i < content.length) i++ // consume `.`
    }
    return true
}

private val EMAIL_LOCAL_PART_PUNCT = setOf(
    '.', '!', '#', '$', '%', '&', '\'', '*', '+', '/', '=', '?',
    '^', '_', '`', '{', '|', '}', '~', '-'
)

/**
 * UTF-8 percent-encode every non-ASCII char (code > 0x7F) in [s], leaving ASCII
 * bytes untouched. Matches CommonMark URL normalization for link destinations
 * after entity refs have been decoded (e.g. `&ouml;` → `ö` → `%C3%B6`).
 * Surrogate pairs decode to a single supplementary codepoint and produce a
 * 4-byte UTF-8 sequence.
 */
private fun percentEncodeNonAscii(s: String): String {
    if (s.all { it.code <= 0x7F }) return s
    val out = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c.code <= 0x7F) {
            out.append(c)
            i++
            continue
        }
        val cp: Int
        if (c.code in 0xD800..0xDBFF && i + 1 < s.length) {
            val low = s[i + 1].code
            if (low in 0xDC00..0xDFFF) {
                cp = 0x10000 + ((c.code - 0xD800) shl 10) + (low - 0xDC00)
                i += 2
            } else {
                cp = 0xFFFD; i++
            }
        } else {
            cp = c.code; i++
        }
        when {
            cp < 0x80 -> appendPercentByte(out, cp)
            cp < 0x800 -> {
                appendPercentByte(out, 0xC0 or (cp ushr 6))
                appendPercentByte(out, 0x80 or (cp and 0x3F))
            }
            cp < 0x10000 -> {
                appendPercentByte(out, 0xE0 or (cp ushr 12))
                appendPercentByte(out, 0x80 or ((cp ushr 6) and 0x3F))
                appendPercentByte(out, 0x80 or (cp and 0x3F))
            }
            else -> {
                appendPercentByte(out, 0xF0 or (cp ushr 18))
                appendPercentByte(out, 0x80 or ((cp ushr 12) and 0x3F))
                appendPercentByte(out, 0x80 or ((cp ushr 6) and 0x3F))
                appendPercentByte(out, 0x80 or (cp and 0x3F))
            }
        }
    }
    return out.toString()
}

private fun appendPercentByte(out: StringBuilder, b: Int) {
    out.append('%')
    val hi = (b ushr 4) and 0xF
    val lo = b and 0xF
    out.append(if (hi < 10) ('0' + hi) else ('A' + (hi - 10)))
    out.append(if (lo < 10) ('0' + lo) else ('A' + (lo - 10)))
}

/**
 * Convert a Unicode codepoint to its String form, handling the supplementary
 * range (> 0xFFFF) by emitting a UTF-16 surrogate pair manually. Avoids
 * `Character.toChars` which is JVM-only.
 */
private fun codepointToString(cp: Int): String {
    if (cp <= 0xFFFF) return cp.toChar().toString()
    val adjusted = cp - 0x10000
    val high = (0xD800 or (adjusted ushr 10)).toChar()
    val low = (0xDC00 or (adjusted and 0x3FF)).toChar()
    return "$high$low"
}

/**
 * Try to decode an HTML entity / numeric character reference *body* — i.e. the
 * chars between `&` and `;`, exclusive of both. Returns the decoded String or
 * null if [body] does not name a valid reference (unknown entity name, missing
 * digits, too many digits, etc.). The caller is responsible for stripping any
 * `&` prefix and verifying the trailing `;` was seen.
 *
 * GFM rules:
 *  - Decimal numeric: `#` + 1..7 digits. Codepoint 0, surrogate range, or
 *    > 0x10FFFF resolves to U+FFFD.
 *  - Hex numeric: `#x` or `#X` + 1..6 hex digits. Same out-of-range rule.
 *  - Named: must be in [NAMED_ENTITIES] (the full HTML5 named-character-reference
 *    list, codegen'd into `NamedEntities.kt` from `entities.json`; filtered to
 *    the canonical trailing-`;` form).
 */
private fun tryDecodeEntityBody(body: String): String? {
    if (body.isEmpty()) return null
    if (body[0] == '#') {
        if (body.length < 2) return null
        val hex = body[1] == 'x' || body[1] == 'X'
        val digits = if (hex) body.substring(2) else body.substring(1)
        if (digits.isEmpty()) return null
        val maxLen = if (hex) 6 else 7
        if (digits.length > maxLen) return null
        for (c in digits) {
            val ok = if (hex) {
                c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
            } else {
                c in '0'..'9'
            }
            if (!ok) return null
        }
        val cp = digits.toInt(if (hex) 16 else 10)
        if (cp == 0 || cp > 0x10FFFF || cp in 0xD800..0xDFFF) return "�"
        return codepointToString(cp)
    }
    return NAMED_ENTITIES[body]
}

/**
 * Decode every valid HTML entity / numeric character reference in [s] in a
 * single pass, leaving invalid `&...;` runs literal. Used for batch decoding of
 * link URLs, link titles, fenced code info strings, and HTML attribute values.
 * Inline paragraph text uses the streaming entity-buffer state in the parser
 * instead, so this helper isn't on the per-char hot path.
 */
private fun decodeEntities(s: String): String {
    if ('&' !in s) return s
    val out = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '&') {
            val semi = s.indexOf(';', i + 1)
            if (semi > i + 1) {
                val body = s.substring(i + 1, semi)
                val decoded = tryDecodeEntityBody(body)
                if (decoded != null) {
                    out.append(decoded)
                    i = semi + 1
                    continue
                }
            }
        }
        out.append(c)
        i++
    }
    return out.toString()
}

// Strict custom markup tagname: namespace:name where each segment is letter then letters/digits/dashes.
private val CUSTOM_MARKUP_TAGNAME = Regex("^[a-zA-Z][a-zA-Z0-9-]*:[a-zA-Z][a-zA-Z0-9-]*$")

// Block-level elements that should NOT be converted to inline Mark/Unmark events
// when they appear mid-paragraph (CommonMark allows them inline, but we conservatively
// emit literal text so renderers escape `<div>` rather than treat it as actual markup).
private val INLINE_HTML_BLOCK_ELEMENTS = HTML_BLOCK_TYPE6_TAGS + HTML_BLOCK_TYPE1_TAGS

// Known HTML5 element names. Tag and attribute names of these elements are
// emitted lowercased (so `<DIV CLASS="foo">` becomes `mark("div", {"class":
// "foo"})`); names outside this set are emitted with their source casing
// preserved (so `<Warning>` stays `Warning`, treating non-HTML5 tags as
// XML-ish where case is significant). Combines TYPE1, TYPE6, and the
// inline-only HTML5 elements not covered by either of those sets.
private val HTML5_ELEMENTS: Set<String> = HTML_BLOCK_TYPE1_TAGS + HTML_BLOCK_TYPE6_TAGS + setOf(
    "a", "abbr", "b", "bdi", "bdo", "br", "button", "canvas", "cite", "code",
    "data", "datalist", "del", "dfn", "em", "embed", "hgroup", "i", "img",
    "input", "ins", "kbd", "label", "map", "mark", "math", "meta", "meter",
    "noscript", "object", "output", "picture", "progress", "q", "rp", "rt",
    "ruby", "s", "samp", "select", "slot", "small", "source", "span",
    "strong", "sub", "sup", "svg", "time", "u", "var", "video", "wbr"
)

/**
 * Returns [name] lowercased if its lowercased form names a known HTML5
 * element, otherwise returns [name] verbatim. Used to normalize tag names
 * for `mark`/`unmark` emission so HTML5 tags are case-insensitive while
 * custom (non-HTML5) tags keep their source casing.
 */
private fun normalizeHtmlName(name: String): String {
    val lower = name.lowercase()
    return if (lower in HTML5_ELEMENTS) lower else name
}

/**
 * HTML5 void elements (no content, no closing tag — always treated as
 * self-closing). Real-world HTML omits `/>` on these (`<meta charset="…">`
 * not `<meta charset="…"/>`), so the parser must auto-close them on `mark`
 * to keep the event stream balanced. Names compared case-insensitively
 * after lowering.
 */
private val HTML5_VOID_ELEMENTS: Set<String> = setOf(
    "area", "base", "br", "col", "embed", "hr", "img", "input",
    "keygen", "link", "meta", "param", "source", "track", "wbr"
)

/** True when this open tag must auto-emit `unmark` immediately. */
private fun HtmlToken.OpenTag.isSelfClosingOrVoid(): Boolean =
    selfClosing || name.lowercase() in HTML5_VOID_ELEMENTS

private sealed interface HtmlToken {
    data class OpenTag(
        val name: String,
        val attributes: Map<String, String>?,
        val selfClosing: Boolean
    ) : HtmlToken
    data class CloseTag(val name: String) : HtmlToken
    data class Text(val content: String) : HtmlToken
}

/**
 * Try to parse an HTML open tag starting at [start] in [s].
 * Returns the index just past `>` and the parsed token, or null if not a valid open tag.
 */
private fun tryParseOpenTag(s: String, start: Int): Pair<Int, HtmlToken.OpenTag>? {
    if (start >= s.length || s[start] != '<') return null
    if (start + 1 >= s.length || !s[start + 1].isLetter()) return null
    var i = start + 1
    while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '-')) i++
    val name = s.substring(start + 1, i)
    // CommonMark: each attribute requires preceding whitespace, so the char
    // immediately after the tag name must be whitespace, `>`, or `/` (self-close).
    // Otherwise constructs like `<m:abc>` would parse as tag `m` with attribute
    // `:abc`, which is invalid HTML.
    if (i < s.length && s[i] != ' ' && s[i] != '\t' && s[i] != '\n' &&
        s[i] != '>' && s[i] != '/'
    ) return null
    val attrs = mutableMapOf<String, String>()
    while (i < s.length) {
        // skip whitespace
        while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n')) i++
        if (i >= s.length) return null
        when (s[i]) {
            '>' -> return (i + 1) to HtmlToken.OpenTag(name, attrs.takeIf { it.isNotEmpty() }, false)
            '/' -> {
                if (i + 1 < s.length && s[i + 1] == '>') {
                    return (i + 2) to HtmlToken.OpenTag(name, attrs.takeIf { it.isNotEmpty() }, true)
                }
                return null
            }
            else -> {
                // attribute name: letter/_/: then [a-zA-Z0-9_.:-]*
                val attrStart = i
                if (!(s[i].isLetter() || s[i] == '_' || s[i] == ':')) return null
                i++
                while (i < s.length &&
                    (s[i].isLetterOrDigit() || s[i] == '_' || s[i] == '.' || s[i] == ':' || s[i] == '-')
                ) i++
                val attrName = s.substring(attrStart, i)
                // optional value
                val savedI = i
                while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n')) i++
                if (i < s.length && s[i] == '=') {
                    i++
                    while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n')) i++
                    if (i >= s.length) return null
                    val q = s[i]
                    when (q) {
                        '"', '\'' -> {
                            i++
                            val valStart = i
                            while (i < s.length && s[i] != q) i++
                            if (i >= s.length) return null
                            val v = s.substring(valStart, i)
                            i++
                            attrs[attrName] = decodeEntities(v)
                        }
                        else -> {
                            // unquoted attribute value: cannot contain whitespace, <, >, =, `, ', ", or be empty
                            val valStart = i
                            while (i < s.length && s[i] !in " \t\n<>=`\"'") i++
                            if (i == valStart) return null
                            attrs[attrName] = decodeEntities(s.substring(valStart, i))
                        }
                    }
                } else {
                    // boolean / valueless attribute
                    i = savedI
                    attrs[attrName] = ""
                }
                // require whitespace before next attribute
                if (i < s.length && s[i] != '>' && s[i] != '/' &&
                    s[i] != ' ' && s[i] != '\t' && s[i] != '\n'
                ) return null
            }
        }
    }
    return null
}

/**
 * Try to parse an HTML close tag `</name>` starting at [start] in [s].
 * Returns the index just past `>` and the parsed token, or null if not a valid close tag.
 * A close tag may not have attributes.
 */
private fun tryParseCloseTag(s: String, start: Int): Pair<Int, HtmlToken.CloseTag>? {
    if (start + 1 >= s.length || s[start] != '<' || s[start + 1] != '/') return null
    if (start + 2 >= s.length || !s[start + 2].isLetter()) return null
    var i = start + 2
    while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '-')) i++
    val name = s.substring(start + 2, i)
    while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n')) i++
    if (i >= s.length || s[i] != '>') return null
    return (i + 1) to HtmlToken.CloseTag(name)
}

/**
 * Tokenize a line of HTML source into Open/Close tag tokens and Text runs.
 * Unparseable `<...>` sequences become text. Used for HTML block content.
 */
private fun tokenizeHtmlLine(line: String): List<HtmlToken> {
    val tokens = mutableListOf<HtmlToken>()
    val text = StringBuilder()
    fun flushText() {
        if (text.isNotEmpty()) {
            tokens += HtmlToken.Text(text.toString())
            text.clear()
        }
    }
    var i = 0
    while (i < line.length) {
        if (line[i] == '<') {
            val open = tryParseOpenTag(line, i)
            if (open != null) {
                val openLower = open.second.name.lowercase()
                if (openLower in GFM_DISALLOWED_TAGS) {
                    // GFM §6.11: the disallowed tag itself becomes literal text.
                    // Additionally — to keep the body opaque to HTML detection
                    // (a `<script>var s = "<a>"` shape must not open a nested
                    // `<a>` mark) — scan ahead on this line for the matching
                    // `</name>`. If found, the whole span (open + body + close)
                    // emits as one text run. If not (multi-line shape), only the
                    // opener is text — subsequent lines flow back through normal
                    // tokenization. The multi-line case is left to a follow-up.
                    val matchEnd = findDisallowedCloseOnLine(line, open.first, openLower)
                    if (matchEnd >= 0) {
                        text.append(line, i, matchEnd)
                        i = matchEnd
                    } else {
                        text.append(line, i, open.first)
                        i = open.first
                    }
                } else {
                    flushText()
                    tokens += open.second
                    i = open.first
                }
                continue
            }
            val close = tryParseCloseTag(line, i)
            if (close != null) {
                if (close.second.name.lowercase() in GFM_DISALLOWED_TAGS) {
                    text.append(line, i, close.first)
                } else {
                    flushText()
                    tokens += close.second
                }
                i = close.first
                continue
            }
        }
        text.append(line[i])
        i++
    }
    flushText()
    return tokens
}

private const val DOCTYPE_PREFIX_LENGTH = "<!DOCTYPE".length

/**
 * True if [line] is the opener of a DOCTYPE declaration (a structurally
 * distinguished subset of HTML block type 4). Matches `<!DOCTYPE`
 * case-insensitively, and requires the next char (if any) to be a space,
 * tab, or `>`.
 *
 * The 3-leading-spaces limit for HTML blocks is enforced by callers — see
 * [detectHtmlBlockType], which rejects `leadingSpaces > 3` before calling
 * this predicate with a pre-trimmed line. Inside [detectHtmlBlockType] this
 * function plays two roles: it acts as the type-4 detector for the
 * lowercase `<!doctype …>` form (CommonMark restricts type 4 to uppercase),
 * and it gates the structural DOCTYPE path for the uppercase form already
 * recognised as type 4.
 */
private fun isDoctypeLine(line: String): Boolean {
    val trimmed = line.trimStart(' ')
    if (trimmed.length < DOCTYPE_PREFIX_LENGTH) return false
    if (!trimmed.regionMatches(0, "<!DOCTYPE", 0, DOCTYPE_PREFIX_LENGTH, ignoreCase = true)) {
        return false
    }
    if (trimmed.length == DOCTYPE_PREFIX_LENGTH) return true
    val next = trimmed[DOCTYPE_PREFIX_LENGTH]
    return next == ' ' || next == '\t' || next == '>'
}

/**
 * Find the end index (past `>`) of `</[lowerName]>` in [line] starting at
 * [startIdx], with optional whitespace before the `>`. Returns -1 if no
 * such close tag appears on the line. Used by [tokenizeHtmlLine] to make
 * the body of a GFM §6.11 disallowed tag opaque on a single line.
 */
private fun findDisallowedCloseOnLine(
    line: String,
    startIdx: Int,
    lowerName: String
): Int {
    val pattern = "</$lowerName"
    val lower = line.lowercase()
    var idx = lower.indexOf(pattern, startIdx)
    while (idx >= 0) {
        val afterName = idx + pattern.length
        // The char after `</name` must be whitespace or `>` so we don't match
        // a prefix like `</scripty>` against `script`.
        if (afterName < line.length) {
            val c = line[afterName]
            if (c == '>' || c == ' ' || c == '\t') {
                var j = afterName
                while (j < line.length && (line[j] == ' ' || line[j] == '\t')) j++
                if (j < line.length && line[j] == '>') return j + 1
            }
        }
        idx = lower.indexOf(pattern, idx + 1)
    }
    return -1
}

/**
 * Detects which CommonMark HTML block type (1-7) a line starts, or 0 if none.
 * The line should not include the trailing newline.
 */
private fun detectHtmlBlockType(line: String): Int {
    // Allow up to 3 leading spaces
    val trimmed = line.trimStart(' ')
    val leadingSpaces = line.length - trimmed.length
    if (leadingSpaces > 3) return 0
    if (!trimmed.startsWith("<") && !trimmed.startsWith("<!") && !trimmed.startsWith("<?")) return 0

    val lower = trimmed.lowercase()

    // Type 1: <pre, <script, <style, <textarea (case-insensitive)
    for (tag in HTML_BLOCK_TYPE1_TAGS) {
        val prefix = "<$tag"
        if (lower.startsWith(prefix)) {
            if (lower.length == prefix.length) return 1
            val next = lower[prefix.length]
            if (next == ' ' || next == '\t' || next == '>') return 1
        }
    }

    // Type 2: <!--
    if (trimmed.startsWith("<!--")) return 2

    // Type 3: <?
    if (trimmed.startsWith("<?")) return 3

    // Type 4: <! followed by uppercase ASCII letter
    if (trimmed.length >= 3 && trimmed[0] == '<' && trimmed[1] == '!' && trimmed[2] in 'A'..'Z') return 4

    // markanywhere extension: lowercase `<!doctype …>` is also recognised as
    // type 4 so it routes through the DOCTYPE structural path. CommonMark
    // restricts type 4 to uppercase declarations, but DOCTYPE in HTML5 is
    // explicitly case-insensitive (HTML Living Standard §13.1.3) and the
    // semantic value is identical regardless of case.
    if (isDoctypeLine(trimmed)) return 4

    // Type 5: <![CDATA[
    if (trimmed.startsWith("<![CDATA[")) return 5

    // Type 6: opening or closing block-level tag
    val type6Match = Patterns.HTML_OPEN_TAG.find(lower) ?: Regex("^</([a-zA-Z][a-zA-Z0-9-]*)(\\s|>|$)").find(lower)
    if (type6Match != null) {
        val tagName = type6Match.groupValues[1]
        if (tagName in HTML_BLOCK_TYPE6_TAGS) return 6
    }

    // Type 7: complete open tag or closing tag, alone on line (rest is whitespace)
    if (isCompleteHtmlTagLine(trimmed)) return 7

    return 0
}

/**
 * Checks if the trimmed line is a complete HTML tag (open or close) followed by optional whitespace.
 * Used for type 7 detection. Uses strict CommonMark validation (rejects invalid attribute
 * names, unquoted values containing forbidden characters, missing whitespace between attrs).
 */
private fun isCompleteHtmlTagLine(trimmed: String): Boolean {
    if (trimmed.isEmpty() || trimmed[0] != '<') return false
    val open = tryParseOpenTag(trimmed, 0)
    if (open != null) {
        val name = open.second.name.lowercase()
        if (name in HTML_BLOCK_TYPE1_TAGS) return false
        if (name in HTML_BLOCK_TYPE6_TAGS) return false
        return trimmed.substring(open.first).trimEnd().isEmpty()
    }
    val close = tryParseCloseTag(trimmed, 0)
    if (close != null) {
        val name = close.second.name.lowercase()
        if (name in HTML_BLOCK_TYPE1_TAGS) return false
        if (name in HTML_BLOCK_TYPE6_TAGS) return false
        return trimmed.substring(close.first).trimEnd().isEmpty()
    }
    return false
}

private class ParserState(
    private val scope: SemanticEventScope,
    private val redirector: RedirectingCollector,
    private val autolinker: AutolinkCollector
) {

    // Block modes
    private sealed interface BlockMode {
        data object Start : BlockMode
        class Heading(val level: Int) : BlockMode {
            // Tracks whether any non-whitespace content has been seen in this
            // heading. Used to discard leading spaces/tabs after the opening
            // `#` run (CommonMark: an arbitrary run of spaces or tabs separates
            // the `#` characters from the heading content).
            var contentStarted: Boolean = false
            // Whitespace (space/tab) buffered while we wait to see whether more
            // content follows or the line ends. Flushed before emitting the
            // next non-whitespace char; discarded on `\n` (CommonMark: trailing
            // spaces/tabs are not part of heading content).
            val pendingSpaces: StringBuilder = StringBuilder()
        }
        data object Paragraph : BlockMode
        /** Paragraph remains open across newlines until a block-start or blank line ends it. */
        data object ParagraphContinuation : BlockMode
        data class CodeBlock(
            val marker: Char,
            val length: Int,
            val indent: Int
        ) : BlockMode
        /** Indented code block: started by ≥4 cols of leading whitespace at top level. */
        data object IndentedCodeBlock : BlockMode
        /**
         * Streaming list container. Holds a stack of open list contexts (one per
         * nesting level) so `<ul>`/`<ol>`/`<li>` events can be emitted as soon as
         * each marker line is parsed, with no whole-list buffering. List items are
         * always rendered loose (inline content wrapped in `<p>`); see the
         * "Lists are always rendered as loose" entry in `markanywhere-parse/README.md`.
         */
        class ListBlock(
            val stack: MutableList<ListContext> = mutableListOf(),
            /** True iff a blank line has been observed since the last non-blank list line. */
            var blankSeen: Boolean = false
        ) : BlockMode
        data object UnorderedList : BlockMode
        data object OrderedList : BlockMode
        /**
         * Blockquote sub-parse frame. The frame is "passive" — content is sub-parsed
         * by the regular Markdown dispatcher in a `Start` frame pushed on top. Each
         * subsequent line's `>` prefix is consumed by a parser-level interceptor
         * before chars reach the inner sub-parser; non-`>` lines are either lazy-
         * continuation (when the inner top is `ParagraphContinuation`) or close the
         * blockquote and replay through the outer `Start`.
         */
        data object Blockquote : BlockMode
        data object MathBlock : BlockMode
        /**
         * Holds the prospective table header line until the next line is seen.
         * If that next line is a valid separator with matching column count we
         * commit (emit `<table><thead>…</thead><tbody>` and switch to
         * [TableBody]); otherwise we replay [headerLine] back through `Start`
         * so it dispatches as a paragraph (or whatever it actually is).
         *
         * This one-line lookahead is the minimum needed to satisfy GFM's
         * spec — a `|`-prefixed line is only a header when followed by a
         * matching separator. Emitting `<table>` eagerly would lock in a
         * decision the stream can't retract.
         */
        data class TableHeaderPending(val headerLine: String) : BlockMode
        /**
         * In-progress table body. [columnCount] and [alignments] come from the
         * separator row and govern cell padding/truncation and per-cell
         * `align=` attributes for every body row. [bodyOpened] is flipped from
         * false to true when the first body row arrives, deferring the
         * `<tbody>` mark so a header-only table emits no empty `<tbody>`.
         */
        class TableBody(
            val columnCount: Int,
            val alignments: List<String?>
        ) : BlockMode {
            var bodyOpened: Boolean = false
        }
        data class CustomMarkup(val tagName: String) : BlockMode

        /** Type 1 block (pre/script/style/textarea). Always emitted structurally. */
        class HtmlBlock1(val rootTag: String) : BlockMode {
            // Stack of all open tag names (root first), still open inside this block.
            val openTags: MutableList<String> = mutableListOf()
            // Buffer for opening line(s) until the opening tag's `>` is seen. Null once parsed.
            var firstLineBuffer: StringBuilder? = StringBuilder()
        }

        /** Types 2–5 (comment / PI / declaration / CDATA): emit each line as raw text. */
        data class HtmlBlock2to5(val closingSequence: String) : BlockMode

        /**
         * DOCTYPE declaration (subset of type-4): emitted structurally as
         * `mark("doctype", isTagged = true)` + content text + `unmark`. The
         * content is everything between `<!DOCTYPE` (case-insensitive) and the
         * closing `>`, with the whitespace run immediately after `<!DOCTYPE`
         * stripped on the opener line; subsequent lines (multi-line DOCTYPE)
         * preserve their leading whitespace verbatim, joined by `\n`. The
         * close arrives on the first `>` — anything after `>` on that line
         * flows as a top-level text event with a trailing `\n`.
         */
        data object Doctype : BlockMode

        /**
         * Types 6/7 (block-level / type-7 tag). Streamed incrementally — the opening
         * tag emits `mark` immediately (after `>` is parsed); content lines emit text
         * events as they arrive; the matching root close tag emits `unmark` and exits.
         *
         * Two child-content modes: `RawText` is the default (each line streams as
         * text events, nested HTML tokens recognized). On the first blank line the
         * frame transitions to `SubParse`, and subsequent lines route through the
         * regular Markdown line dispatcher (paragraph/list/heading/code/etc.).
         * The matching root close tag still pops the frame in either mode.
         */
        class HtmlBlock6or7(
            /** Lowercased root tag name, as detected from the opener line. */
            val rootTagName: String,
            /** True when the opener was a closing tag (e.g. `</div>`); no mark is emitted. */
            val rootIsClosingTag: Boolean
        ) : BlockMode {
            // Phase 1: buffer the opening tag chars until '>' is parsed. Null once consumed.
            var firstLineBuffer: StringBuilder? = StringBuilder()
            // Phase 2: stack of all open tag names (root first, lowercased) still inside.
            // Empty when rootIsClosingTag (raw-text mode).
            val openTags: MutableList<String> = mutableListOf()
            // Child-content mode. Starts as `RawText`; flips to `SubParse` on the
            // first blank line in the block, after which inner content is parsed as
            // Markdown until the matching root close tag (or EOF).
            var childMode: ChildMode = ChildMode.RawText
        }

        enum class ChildMode { RawText, SubParse }
    }

    // Stack of block-mode frames. Bottom is always the outermost context (initially
    // `Start`); top drives dispatch. Pushing on top represents nesting (currently
    // only when entering an HTML block 6/7 in sub-parse mode); replacing the top
    // is the common sibling-transition case (e.g. Start -> Paragraph). Popping
    // returns to the enclosing frame (e.g. closing the matching root HTML tag).
    private val blockModeStack: ArrayDeque<BlockMode> =
        ArrayDeque<BlockMode>().apply { addLast(BlockMode.Start) }
    private val blockMode: BlockMode get() = blockModeStack.last()
    private fun replaceMode(m: BlockMode) {
        blockModeStack[blockModeStack.lastIndex] = m
    }
    private fun pushMode(m: BlockMode) {
        blockModeStack.addLast(m)
    }
    private fun popMode(): BlockMode = blockModeStack.removeLast()
    /**
     * Index (from the bottom of the stack) of the nearest enclosing
     * `HtmlBlock6or7` frame whose root close tag appears in [line], or -1.
     * Scans from the top down so the most recently opened HTML frame is
     * matched first (e.g. `<section>...<div>...</section>` resolves
     * `</section>` against the section frame).
     */
    private fun findEnclosingHtmlFrameIndex(line: String): Int {
        for (i in blockModeStack.lastIndex downTo 0) {
            val frame = blockModeStack[i]
            if (frame is BlockMode.HtmlBlock6or7 && !frame.rootIsClosingTag &&
                frame.firstLineBuffer == null &&
                findRootCloseTagIndex(line, frame.rootTagName) >= 0
            ) {
                return i
            }
        }
        return -1
    }

    /**
     * If [line] is solely a close tag (with at most leading/trailing whitespace)
     * whose lowercased name matches an `openTag` tracked by an enclosing
     * `HtmlBlock6or7` frame *other than* that frame's root, returns
     * `(frame index, close tag name)`. The match scans top-down so the
     * deepest still-open inner tag wins.
     *
     * Used to (a) interrupt a sub-parsed paragraph when an inner tag closes
     * and (b) trigger the partial close in `tryCloseEnclosingHtmlBlock` —
     * which drains the frame's `openTags` down to and including the matched
     * name, then resumes sub-parse with a fresh `Start` frame on top.
     *
     * A match against an inner `openTag` is preferred over a root match even
     * when the two share a name (e.g. `<nav><nav>…</nav></nav>`): the inner
     * tag was opened later, so a stand-alone close resolves to it LIFO. The
     * caller ([tryCloseEnclosingHtmlBlock]) checks this before the root-close
     * path so a same-named nesting closes one level at a time rather than
     * draining the whole frame.
     */
    private fun findEnclosingHtmlOpenTagClose(line: String): Pair<Int, String>? {
        val trimmed = line.trimStart()
        if (!trimmed.startsWith("</")) return null
        val close = tryParseCloseTag(trimmed, 0) ?: return null
        if (trimmed.substring(close.first).trimEnd().isNotEmpty()) return null
        val name = close.second.name.lowercase()
        for (i in blockModeStack.lastIndex downTo 0) {
            val frame = blockModeStack[i] as? BlockMode.HtmlBlock6or7 ?: continue
            if (frame.rootIsClosingTag || frame.firstLineBuffer != null) continue
            // openTags entries may keep source casing for non-HTML5 tags, so
            // compare case-insensitively against the (already-lowercased) name.
            if (frame.openTags.any { it.lowercase() == name }) return i to name
        }
        return null
    }

    private var lineBuffer = StringBuilder()
    private var atLineStart = true
    // Count of trailing ASCII spaces on the most recently processed paragraph line.
    // Read at the next-line boundary in `processParagraphContinuation` to decide
    // whether to emit a `<br/>` (GFM §6.7 hard line break: ≥2 trailing spaces).
    // In fast-path `processParagraph`, doubles as the in-flight trailing-run
    // counter — space chars increment it and are deferred (not emitted) until a
    // non-space char flushes them as text or `\n` finalizes the count.
    private var paragraphTrailingSpaces: Int = 0
    // True when the most recently processed paragraph line ended with `\<newline>`
    // (GFM §6.13 hard line break via backslash). On continuation, behaves like
    // ≥2 trailing spaces and produces a `<br/>` (alongside the existing
    // [paragraphTrailingSpaces] = 2 setting). On paragraph close (no
    // continuation), emits the `\` as literal text — GFM example 669 expects
    // `<p>foo\</p>`, never a dangling `<br/>` followed by nothing.
    private var paragraphTrailingBackslash: Boolean = false
    private val indentedCodeDeferredBlanks = mutableListOf<String>()
    // True while replaying lines that we already proved cannot start a table
    // (TableHeaderPending rejection branch). Suppresses the `|`-line table
    // entry in `processStart` and the `|`-line interrupt in
    // `lineInterruptsParagraph` so the replayed lines flow as paragraph
    // content instead of immediately re-entering table detection.
    private var suppressTableDetection = false
    private var inListItem = false

    // Blockquote prefix-consumption state. At the start of every line where
    // `blockquoteFrameCount() > 0`, the interceptor consumes `>` markers (and
    // optional spaces) before chars reach the inner sub-parser. `prefixDone`
    // flips to true once all enclosing Blockquote frames' prefixes are consumed
    // (or when the line is recognized as content already past the prefix).
    private var blockquotePrefixDone: Boolean = true
    private var blockquotePrefixCount: Int = 0
    private var blockquotePrefixIndent: Int = 0
    private var blockquoteJustAfterGt: Boolean = false
    // When prefix consumption fails before all levels are matched, the rest of
    // the line is buffered here so its content can be classified at `\n`:
    // blank → close blockquote; paragraph-eligible + inner is paragraph → lazy
    // continue; otherwise close blockquote and replay through outer Start.
    private var blockquoteInFailedLine: Boolean = false
    private val blockquoteFailedLineBuffer = StringBuilder()

    // Inline state
    private var code = false
    private var strikethrough = false
    private var superscript = false
    private var highlight = false
    private var math = false
    private var inLink = false
    private var inLinkUrl = false
    private var linkText = StringBuilder()
    private var linkUrl = StringBuilder()
    private var linkTitle = StringBuilder()
    private var inImage = false
    private var imageAlt = StringBuilder()
    private var imageUrl = StringBuilder()

    /**
     * Inline link/image URL parsing phase. Drives [handleLinkUrlChar] when
     * `inLinkUrl == true`. Reset to [LinkUrlPhase.PreDest] each time a `](`
     * or `]( ` boundary fires.
     */
    private enum class LinkUrlPhase {
        /** After `(`, skipping leading ASCII whitespace before the destination. */
        PreDest,
        /** Inside `<...>` destination — allows space, rejects `<` / `\n`, accepts backslash escapes. */
        DestAngle,
        /** Unbracketed destination — counts balanced parens, rejects raw space/`\n`, accepts backslash escapes. */
        DestPlain,
        /** Whitespace between destination and optional title; a closing `)` here finalizes the link. */
        BetweenDestTitle,
        /** Inside `"..."` title. */
        TitleDouble,
        /** Inside `'...'` title. */
        TitleSingle,
        /** Inside `(...)` title — nested unescaped `(` aborts the link (GFM constraint). */
        TitleParen,
        /** After title close — only whitespace then the closing `)` is permitted. */
        AfterTitle
    }
    private var linkUrlPhase = LinkUrlPhase.PreDest
    /** Open-paren depth inside [LinkUrlPhase.DestPlain] for balanced-parens rule. */
    private var linkParenDepth = 0
    /** True iff the previous URL/title char was an unescaped `\` — next char is taken literally. */
    private var linkEscape = false
    /**
     * Raw source chars consumed since `(` while in URL-parsing mode — used by
     * [abortInlineLink] / [abortInlineImage] to replay literal text on a parse
     * failure (e.g. unmatched destination, malformed title).
     */
    private var linkUrlSource = StringBuilder()

    /**
     * True when the parser is reading the *second* `[…]` of a full or collapsed
     * reference link/image — `[label][ref]` or `[label][]` — accumulating the
     * reference label in [linkRefText]. Resolved to a `<a>` / `<img>` on the
     * closing `]` if the (case-folded, whitespace-collapsed) key is in
     * [linkDefinitions]; otherwise the entire bracket run replays as text.
     */
    private var inLinkRef = false
    private var linkRefText = StringBuilder()

    /**
     * Set when an unescaped `]` is seen while parsing a link/image label and
     * no inline state (code span, math, HTML attr accumulation) is mid-resolution.
     * The `]` itself is *not* emitted; the next char decides:
     *   - `(` → inline link (begin URL parsing)
     *   - `[` → full or collapsed reference (begin ref label)
     *   - anything else → shortcut reference lookup, or abort to literal text
     * Replaces the prior `inlineBuffer.endsWith("]")` check, which conflicted
     * with using `inlineBuffer` to accumulate label-internal delimiter runs.
     */
    private var linkLabelTentativeClose = false

    /**
     * Size of [inlineOpenStack] at the moment `[` (or `![`) opened the current
     * label. On commit/abort, [flushInlineLabelClose] drains down to this
     * watermark — closing only inline state (em, strong, code, inline HTML)
     * that was opened *inside* the label, while leaving any outer state alone.
     */
    private var linkLabelOuterStackDepth = 0

    /**
     * Snapshot of the boolean-tracked inline flags (`del`/`mark`/`sup`) at the
     * moment `[` (or `![`) opened the current label. Unlike em/strong/HTML — which
     * live in [inlineOpenStack] and are watermark-scoped by [linkLabelOuterStackDepth]
     * — these flags have no stack position, so [flushInlineLabelClose] consults this
     * snapshot to close only the ones opened *inside* the label, leaving a `del`/
     * `mark`/`sup` opened *before* the `[` alone (otherwise its `unmark` would leak
     * into the captured label buffer and produce a crossed, unrenderable stream —
     * e.g. the citation superscript `^[1](url)^`).
     */
    private var linkLabelOuterStrikethrough = false
    private var linkLabelOuterHighlight = false
    private var linkLabelOuterSuperscript = false

    /**
     * True when the next [processInlineCharImpl] call is the *re-process* leg
     * of the [pendingDeferredChar] protocol — i.e. the same source char is
     * being delivered for a second time so that buffered delimiter resolution
     * can finalize before it consumes the char. Used by the label-mode
     * dispatcher to skip appending the char to [linkText] / [imageAlt] twice.
     * Set in [processInlineChar]'s finally block when [pendingDeferredChar]
     * was set during this call; cleared at the top of the next call.
     */
    private var inlineCharIsReprocess = false

    /**
     * Bracket nesting depth inside the current link/image label. Outer `[`
     * (or `![`) opens at depth 0; an unescaped `[` *inside* the label
     * increments, an unescaped `]` decrements. Only a `]` at depth 0 closes
     * the label (sets [linkLabelTentativeClose]); deeper `]` is content.
     * Permits CommonMark-correct labels like `[link [foo [bar]]](/uri)`.
     * Code-span / math / HTML-attr accumulation suppress the depth update —
     * brackets inside those constructs are content of the inner construct.
     */
    private var linkLabelBracketDepth = 0

    /**
     * Resolved link reference definitions discovered so far. CommonMark §4.7:
     * `[label]: destination "title"` lines are consumed silently at block boundary
     * and recorded here, then resolved when `[label]`, `[label][]`, or
     * `[foo][label]` appears inline. Keys are normalized: trimmed, internal
     * whitespace collapsed to single space, lowercased (Unicode case-fold).
     *
     * STREAMING DIVERGENCE: spec resolves references against ALL definitions in
     * the document — including those appearing *after* the usage. Our parser is
     * append-only and cannot retroactively rewrite emitted events, so reference
     * lookups only see definitions that arrived *before* the usage line. Tests
     * exercising forward references are marked DIVERGENCE.
     */
    private val linkDefinitions = mutableMapOf<String, LinkDefinition>()
    private data class LinkDefinition(val href: String, val title: String?)
    private var escaped = false
    /** Length of the opening backtick run for the currently-open inline code span (0 if not in code). */
    private var codeRunLength = 0
    private var inlineBuffer = StringBuilder()

    // GFM §6.11 disallowed raw HTML (`script`, `style`, `textarea`, `iframe`,
    // …) encountered *inline* (mid-line, not at a block boundary). At a block
    // boundary these open a type-1 HTML block that is emitted structurally (and
    // dropped downstream); mid-line they reach the inline `<…>` dispatch, where
    // we suppress the element and its raw-text body entirely rather than leaking
    // it as literal text — a minified `<script>{json}</script>` would otherwise
    // dump its whole body into the output. While [inlineRawSkipTag] is set, every
    // char is dropped until the matching close tag completes. [inlineRawSkipMatch]
    // is the incremental match position against [inlineRawSkipClose] (`</name`),
    // and reaching its length means the name matched and we are consuming optional
    // whitespace before the final `>`. STREAMING DIVERGENCE: the skip is bounded to
    // a single line — a `\n` falls through to the block machine and [flushInline]
    // clears the state, so a disallowed tag whose body spans soft breaks only drops
    // its first line (same constraint as every other inline construct).
    private var inlineRawSkipTag: String? = null
    private var inlineRawSkipClose: String = ""
    private var inlineRawSkipMatch = 0

    // Unified stack of in-flight inline opens — emphasis (`em`/`strong`) and
    // inline HTML tags — recorded in the order they were emitted. A single
    // stack is required to preserve LIFO close order across the two: a
    // `<strong>` opened *inside* an outer `<em>` must close *before* the em,
    // even when the matching `*` arrives first. Drained top-to-bottom in
    // `flushInline` so the event stream is *always* balanced — every `mark`
    // pairs with an `unmark`, including for sources that omit the closer
    // (e.g. `*foo` with no closing `*` in the same paragraph) or close tags
    // out of order.
    // `delimChar` is the run char that opened an em/strong frame (`*` or `_`),
    // used by closeLabelLocalEmphasisRun to refuse closing a `*`-opened span with
    // a `_` run (or vice versa) — `*` and `_` are distinct delimiter types that
    // never pair (CommonMark §6.2). null for tagged HTML frames.
    private data class InlineOpenFrame(
        val name: String,
        val isTagged: Boolean,
        val delimChar: Char? = null
    )
    private val inlineOpenStack = ArrayDeque<InlineOpenFrame>()

    private fun isInlineOpen(name: String): Boolean =
        inlineOpenStack.any { !it.isTagged && it.name == name }

    // After resolving a buffered marker (e.g. "*"), the trailing char is left
    // unconsumed so the outer loop's fast-path can coalesce it with subsequent
    // non-control characters. Replaces the previous recursive `processInlineChar`.
    private var pendingDeferredChar: Char? = null

    // HTML entity / numeric character reference accumulation (GFM §6.2). Set
    // when we see `&` in regular inline text; chars after the `&` (entity body)
    // accumulate in [entityBuffer] until either `;` arrives (try to decode) or
    // an invalid char arrives (abort, emit `&body` literally, reprocess char).
    // Decoded chars are emitted as plain text — they do NOT participate in
    // inline parsing (so `&#42;foo&#42;` produces literal `*foo*`, not `<em>`).
    private var inEntityRef = false
    private val entityBuffer = StringBuilder()

    // Flanking state for CommonMark emphasis rules.
    // `prevInlineChar` is the most recently processed source char (null = block
    // boundary, treated as Unicode whitespace). `runPrevChar` is the char that
    // immediately preceded the currently-buffered delimiter run (captured when
    // the run started). Used to compute left/right-flanking at run resolution.
    private var prevInlineChar: Char? = null
    private var runPrevChar: Char? = null

    // Custom markup state
    private var customMarkupClosingBuffer = StringBuilder()
    private var customMarkupInClosingTag = false
    private var customMarkupSkipFirstNewline = false
    private var customMarkupPendingNewline = false

    // True when the previous chunk ended with `\r` and we already emitted `\n`
    // for it. If the next chunk begins with `\n`, we swallow it so a CRLF pair
    // straddling a chunk boundary still counts as a single line ending.
    private var pendingCarriageReturn: Boolean = false

    suspend fun processChunk(chunk: String) {
        val normalized = preprocessChunk(chunk)
        if (normalized.isNotEmpty()) scope.processChunk(normalized)
    }

    // GFM input preprocessing: normalize line endings to `\n` (§2.1) and replace
    // the insecure U+0000 NUL with U+FFFD REPLACEMENT CHARACTER (§2.3) so the rest
    // of the parser only ever sees a single line-ending form and no NULs.
    private fun preprocessChunk(chunk: String): String {
        if (chunk.isEmpty()) return chunk
        if (!pendingCarriageReturn
            && chunk.indexOf('\r') < 0
            && chunk.indexOf('\u0000') < 0
        ) return chunk

        val sb = StringBuilder(chunk.length)
        var i = 0
        if (pendingCarriageReturn) {
            pendingCarriageReturn = false
            if (chunk[i] == '\n') i++
        }
        while (i < chunk.length) {
            when (val c = chunk[i]) {
                '\r' -> {
                    sb.append('\n')
                    i++
                    if (i < chunk.length) {
                        if (chunk[i] == '\n') i++
                    } else {
                        pendingCarriageReturn = true
                    }
                }
                '\u0000' -> {
                    sb.append('\uFFFD')
                    i++
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString()
    }

    // Control characters that require special handling in inline text
    private fun Char.isInlineControl(): Boolean = when (this) {
        '*', '_', '`', '~', '^', '=', '$', '[', '!', '<', '\\', '\n', '&' -> true
        else -> false
    }

    // Characters that may start (or be ambiguous with) a block-level element.
    // Used to gate eager paragraph opening in Start mode: a char NOT in this set
    // is unambiguously paragraph content, so we can mark <p> immediately and
    // route subsequent chars through Paragraph mode for incremental emission.
    // Includes `*`, `_` (thematic break vs. emphasis), `\t` (indented code), and
    // ` ` (leading whitespace before any block).
    private fun Char.isBlockStart(): Boolean = when (this) {
        // `[` joins the block-start set so `[label]: dest` link reference definitions
        // (CommonMark §4.7) can be recognized in `processStart`'s `\n` dispatch.
        // Cost: paragraphs starting with `[` no longer eagerly emit `<p>` on the
        // first char — they buffer the line until `\n`. Same trade-off as `>`,
        // `-`, `*`, etc. which all reserve the line opener for block detection.
        '#', '`', '~', '-', '>', '|', '$', '<', ' ', '\t', '*', '_', '[' -> true
        else -> isDigit()
    }

    // CommonMark emphasis flanking helpers. Block boundaries (null) count as
    // Unicode whitespace per spec. Per CommonMark, "Unicode whitespace" is any
    // char in Unicode general category `Zs` (which already includes ASCII space
    // and NBSP) plus tab, line feed, form feed, and carriage return — so e.g.
    // `* a *` with NBSPs around `a` is non-flanking on both sides.
    private fun Char?.isFlankWhitespace(): Boolean {
        val c = this ?: return true
        return c == '\t' || c == '\n' || c == '\u000C' || c == '\r' ||
            c.category == CharCategory.SPACE_SEPARATOR
    }

    private fun Char?.isFlankPunct(): Boolean {
        val c = this ?: return false
        return c in '!'..'/' || c in ':'..'@' || c in '['..'`' || c in '{'..'~'
    }

    /**
     * Returns (canOpen, canClose) for a delimiter run of [runChar] (`*`, `_`,
     * or `~`) given the chars immediately before and after the run.
     * Implements CommonMark §6.2 rules 1–8 (`_` has stricter rules to suppress
     * intraword emphasis). For `~` we apply the `*` rules — GFM strikethrough
     * doesn't formally use flanking, but the `*` rules are a reasonable proxy.
     */
    private fun emphasisFlanking(
        runChar: Char,
        prev: Char?,
        next: Char?
    ): Pair<Boolean, Boolean> {
        val nextIsWs = next.isFlankWhitespace()
        val nextIsPunct = next.isFlankPunct()
        val prevIsWs = prev.isFlankWhitespace()
        val prevIsPunct = prev.isFlankPunct()
        val leftFlank = !nextIsWs && (!nextIsPunct || prevIsWs || prevIsPunct)
        val rightFlank = !prevIsWs && (!prevIsPunct || nextIsWs || nextIsPunct)
        return if (runChar == '_') {
            val canOpen = leftFlank && (!rightFlank || prevIsPunct)
            val canClose = rightFlank && (!leftFlank || nextIsPunct)
            canOpen to canClose
        } else {
            leftFlank to rightFlank
        }
    }

    /**
     * Resolves a buffered emphasis delimiter run of length [runLen] (1, 2, or 3)
     * for [runChar] (`*` or `_`). Applies CommonMark flanking to decide whether
     * the run can open and/or close; if it can do neither (e.g. `_` between
     * whitespace), emits the run as literal text.
     *
     * Closes via [closeInlineDownTo] so any inner inline frames (other emphasis
     * or HTML tags opened *inside* the one being closed) are force-closed first
     * — the LIFO drain keeps the event stream balanced even when the source
     * delimiter ordering would cross. When the run can't legitimately close
     * (no matching opener on the stack) and can't open (already open with
     * same name), the delimiters are emitted as literal text rather than
     * force-closing the wrong opener — closer to spec than the previous
     * fallback-close behavior.
     *
     * [next] is the char immediately after the run; null = block boundary
     * (Unicode whitespace per spec).
     */
    private suspend fun SemanticEventScope.resolveEmphasisRun(
        runChar: Char,
        runLen: Int,
        next: Char?
    ) {
        val (canOpen, canClose) = emphasisFlanking(runChar, runPrevChar, next)
        if (!canOpen && !canClose) {
            +runChar.toString().repeat(runLen)
            return
        }
        val emOpen = isInlineOpen("em")
        val strongOpen = isInlineOpen("strong")
        when (runLen) {
            1 -> when {
                canClose && emOpen -> closeInlineDownTo("em", isTagged = false)
                canOpen && !emOpen -> openInlineEmphasis("em", runChar)
                else -> +runChar.toString()
            }
            2 -> when {
                canClose && strongOpen -> closeInlineDownTo("strong", isTagged = false)
                canOpen && !strongOpen -> openInlineEmphasis("strong", runChar)
                else -> +runChar.toString().repeat(2)
            }
            else -> when {
                canClose && strongOpen && emOpen -> {
                    // Close the inner first, then the outer — opening order on
                    // the stack tells us which is which.
                    closeInlineDownTo("em", isTagged = false)
                    closeInlineDownTo("strong", isTagged = false)
                }
                canClose && strongOpen -> {
                    // 2 of the 3 delimiters close strong; the remaining 1 opens
                    // em if it can, else falls through as literal.
                    closeInlineDownTo("strong", isTagged = false)
                    if (canOpen) openInlineEmphasis("em", runChar) else +runChar.toString()
                }
                canClose && emOpen -> {
                    // 1 of the 3 delimiters closes em; the remaining 2 open
                    // strong if they can, else fall through as literal.
                    closeInlineDownTo("em", isTagged = false)
                    if (canOpen) openInlineEmphasis("strong", runChar) else +runChar.toString().repeat(2)
                }
                canOpen && !strongOpen && !emOpen -> {
                    // ***foo***: outer strong, inner em — closes em first via LIFO.
                    openInlineEmphasis("strong", runChar)
                    openInlineEmphasis("em", runChar)
                }
                else -> +runChar.toString().repeat(3)
            }
        }
    }

    /**
     * Open an emphasis tag and push it onto [inlineOpenStack].
     */
    private suspend fun SemanticEventScope.openInlineEmphasis(name: String, delimChar: Char) {
        mark(name)
        inlineOpenStack.addLast(InlineOpenFrame(name, isTagged = false, delimChar = delimChar))
    }

    /**
     * Close down to the topmost frame matching [name] + [isTagged], emitting
     * `unmark` for every frame along the way (LIFO). Returns false if no
     * matching frame is found; the caller is responsible for the literal
     * fallback in that case.
     */
    private suspend fun SemanticEventScope.closeInlineDownTo(
        name: String,
        isTagged: Boolean,
        nameIgnoreCase: Boolean = false
    ): Boolean {
        val idx = inlineOpenStack.indexOfLast {
            it.isTagged == isTagged && (
                if (nameIgnoreCase) it.name.equals(name, ignoreCase = true) else it.name == name
                )
        }
        if (idx < 0) return false
        while (inlineOpenStack.size > idx) {
            val frame = inlineOpenStack.removeLast()
            unmark(frame.name, isTagged = frame.isTagged)
        }
        return true
    }

    /** Enter inline raw-text skip mode for a disallowed [tagName] (GFM §6.11). */
    private fun beginInlineRawSkip(tagName: String) {
        inlineRawSkipTag = tagName
        inlineRawSkipClose = "</" + tagName.lowercase()
        inlineRawSkipMatch = 0
    }

    private fun endInlineRawSkip() {
        inlineRawSkipTag = null
        inlineRawSkipClose = ""
        inlineRawSkipMatch = 0
    }

    /**
     * Consume one dropped char while in inline raw-text skip mode, advancing the
     * incremental match against the close tag `</name …>`. Returns `true` when
     * the close completes (skip mode ends). The restart-on-`<` rule is sufficient
     * because `</name` has no internal repeat of its `<` prefix.
     */
    private fun consumeInlineRawSkipChar(char: Char): Boolean {
        val close = inlineRawSkipClose
        if (inlineRawSkipMatch < close.length) {
            inlineRawSkipMatch = when {
                char.lowercaseChar() == close[inlineRawSkipMatch] -> inlineRawSkipMatch + 1
                char == '<' -> 1
                else -> 0
            }
            return false
        }
        // Name matched: skip optional whitespace, then require the closing `>`.
        return when {
            char == '>' -> { endInlineRawSkip(); true }
            char == ' ' || char == '\t' -> false
            char == '<' -> { inlineRawSkipMatch = 1; false }
            else -> { inlineRawSkipMatch = 0; false }
        }
    }

    /**
     * Process inline content in bulk, emitting maximal text runs.
     * This is used when replaying buffered content (e.g., from lineBuffer)
     * after block-level ambiguity has been resolved.
     */
    private suspend fun SemanticEventScope.processInlineContent(content: String) {
        if (content.isEmpty()) return

        var index = 0
        while (index < content.length) {
            val fastPathEnd = getInlineFastPathEnd(content, index)

            if (fastPathEnd > index) {
                +content.substring(index, fastPathEnd)
                // Keep flanking state in sync — the fast-path bypasses processInlineChar
                // but emphasis decisions on the next delimiter need to know the char
                // that just landed.
                prevInlineChar = content[fastPathEnd - 1]
                index = fastPathEnd
                continue
            }

            processInlineChar(content[index])
            // If a buffer-resolution branch deferred the char, reprocess it without
            // advancing — fast-path will pick it up alongside subsequent non-control text.
            if (pendingDeferredChar != null) {
                pendingDeferredChar = null
            } else {
                index++
            }
        }
    }

    /**
     * Returns the end index for fast-path text emission within inline content.
     * Uses the common inline state check, then falls back to control char scanning.
     */
    private fun getInlineFastPathEnd(
        content: String,
        startIndex: Int
    ): Int {
        val inlineEnd = getInlineStateFastPathEnd(content, startIndex)
        if (inlineEnd >= 0) return inlineEnd
        // If there's pending inline buffer content, can't fast-path
        if (inlineBuffer.isNotEmpty()) return startIndex
        // Scan for next control character
        return findNextControlChar(content, startIndex)
    }

    /**
     * Checks inline formatting state for fast-path eligibility.
     * Returns the fast-path end index if an inline state applies, or -1 if no inline state is active.
     */
    private fun getInlineStateFastPathEnd(content: String, startIndex: Int): Int = when {
        // Inline raw-text skip drops every char one-by-one through the close-tag
        // matcher — no fast-path substring emit may bypass it.
        inlineRawSkipTag != null -> startIndex
        escaped -> startIndex
        // N=1 code spans stream content as text events for typewriter UX — the
        // fast-path emits a maximal run of non-backtick chars at once, stopping
        // at the next `` ` `` so the run-length-matching tentative-close logic
        // in `processInlineCharImpl` can take over. The `inlineBuffer.isEmpty()`
        // guard is critical: once a tentative close `` ` `` has been buffered,
        // the next non-` char must flow through `processInlineCharImpl` to
        // resolve the close — fast-path would otherwise emit content past the
        // unresolved buffer (turning `` `foo`bar` `` into `` `<code>foo`bar`</code> ``).
        // N≥2 buffers everything and therefore must not fast-path.
        code && codeRunLength == 1 && inlineBuffer.isEmpty() ->
            findNextChar(content, startIndex, '`')
        code -> startIndex
        math -> findNextChar(content, startIndex, '$')
        inLinkUrl -> startIndex
        inLink -> startIndex
        inImage -> startIndex
        inEntityRef -> startIndex
        else -> -1
    }

    private suspend fun SemanticEventScope.processChunk(chunk: String) {
        if (chunk.isEmpty()) return

        var index = 0
        while (index < chunk.length) {
            // Special handling for CustomMarkup skip-first-newline at chunk level
            // This must be handled before fast-path to avoid splitting chunks
            if (blockMode is BlockMode.CustomMarkup && customMarkupSkipFirstNewline) {
                customMarkupSkipFirstNewline = false
                if (chunk[index] == '\n') {
                    index++
                    continue  // Skip this newline and continue
                }
                // Not a newline - continue with normal processing
            }

            // Heading-specific fast-path: emits maximal runs of plain content
            // chars. Whitespace, `#`, and inline-control chars stop the scan
            // and fall through to processHeading, where pendingSpaces handles
            // trailing-ws + closing-# stripping (CommonMark heading content
            // strip). Only fires when inline state is clean.
            val heading = blockMode
            if (heading is BlockMode.Heading &&
                inlineBuffer.isEmpty() && !escaped && !code && !math &&
                !inLink && !inImage && inlineRawSkipTag == null
            ) {
                if (!heading.contentStarted && heading.pendingSpaces.isEmpty()) {
                    var skip = index
                    while (skip < chunk.length &&
                        (chunk[skip] == ' ' || chunk[skip] == '\t')
                    ) {
                        skip++
                    }
                    if (skip > index) {
                        index = skip
                        if (index >= chunk.length) break
                    }
                }
                var end = index
                while (end < chunk.length &&
                    !chunk[end].isInlineControl() &&
                    chunk[end] != ' ' && chunk[end] != '\t' &&
                    chunk[end] != '#'
                ) {
                    end++
                }
                if (end > index) {
                    if (heading.pendingSpaces.isNotEmpty()) {
                        +(heading.pendingSpaces.toString() +
                            chunk.substring(index, end))
                        heading.pendingSpaces.clear()
                    } else {
                        +chunk.substring(index, end)
                    }
                    prevInlineChar = chunk[end - 1]
                    heading.contentStarted = true
                    index = end
                    continue
                }
                // end == index: ws, `#`, or control char at index — fall
                // through to char-by-char processing.
            }

            // Determine if we can fast-path based on current state
            val fastPathResult = getFastPathEnd(chunk, index)

            if (fastPathResult > index) {
                // For custom markup, emit pending newline before content
                if (blockMode is BlockMode.CustomMarkup && customMarkupPendingNewline) {
                    customMarkupPendingNewline = false
                    +'\n'
                }
                // Combine any deferred paragraph trailing-space run (from prior
                // char-by-char iterations) with this fast-path substring into a
                // single text emit. The substring here starts with a non-space
                // char, so those spaces were mid-line content rather than line-
                // trailing whitespace.
                if (paragraphTrailingSpaces > 0 && blockMode == Paragraph) {
                    +(" ".repeat(paragraphTrailingSpaces) +
                        chunk.substring(index, fastPathResult))
                    paragraphTrailingSpaces = 0
                } else {
                    +chunk.substring(index, fastPathResult)
                }
                // Keep flanking state in sync — see processInlineContent for rationale.
                prevInlineChar = chunk[fastPathResult - 1]
                index = fastPathResult
                // If a deferred char was waiting for re-processing, the substring
                // we just emitted covered its position; clear the flag so the
                // next char-by-char iteration doesn't reprocess the char after
                // [index] twice.
                pendingDeferredChar = null
                continue
            }

            // Fall back to character-by-character processing
            process(chunk[index])
            if (pendingDeferredChar != null) {
                pendingDeferredChar = null
            } else {
                index++
            }
        }
    }

    /**
     * Returns the end index for fast-path text emission, or [startIndex] if no fast-path is possible.
     *
     * Fast-path is possible when we can emit multiple characters without state changes.
     * This depends on the current block mode and inline formatting state.
     */
    private fun getFastPathEnd(chunk: String, startIndex: Int): Int {
        // Check for fast-path based on current inline state (takes priority)
        val inlineEnd = getInlineStateFastPathEnd(chunk, startIndex)
        if (inlineEnd >= 0) return inlineEnd

        // Custom markup mode - < and \n are control (< starts potential closing tag, \n needs buffering)
        // Also disable fast-path when we need to skip the first newline or have pending newline
        if (blockMode is BlockMode.CustomMarkup && !customMarkupInClosingTag && !customMarkupSkipFirstNewline && !customMarkupPendingNewline) {
            return findNextCustomMarkupControl(chunk, startIndex)
        }

        // If there's pending inline buffer content, can't fast-path (ambiguity)
        // This includes autolinks (<...>) which need character-by-character buffering
        if (inlineBuffer.isNotEmpty()) return startIndex

        // Check block mode for fast-path eligibility
        val canFastPath = when (blockMode) {
            Paragraph -> true
            // Heading mode handles its own fast-path inline in processChunk
            // (see the Heading-specific branch). The generic fast-path here
            // is bypassed because heading content needs leading/trailing
            // whitespace stripping that a plain substring emit can't do.
            UnorderedList -> inListItem
            OrderedList -> inListItem
            else -> false
        }

        if (!canFastPath) return startIndex

        // In Paragraph mode, also stop at ASCII space so the per-char dispatcher
        // (processParagraph) can defer trailing whitespace runs and promote ≥2
        // trailing spaces before `\n` into a `<br/>` (GFM §6.7).
        if (blockMode == Paragraph) {
            return findNextParagraphFastPathEnd(chunk, startIndex)
        }
        // In inline text (possibly with active formatting like bold/italic) - scan for any control character
        return findNextControlChar(chunk, startIndex)
    }

    private fun findNextParagraphFastPathEnd(chunk: String, startIndex: Int): Int {
        for (i in startIndex until chunk.length) {
            val c = chunk[i]
            if (c == ' ' || c.isInlineControl()) return i
        }
        return chunk.length
    }

    private fun findNextChar(chunk: String, startIndex: Int, target: Char): Int {
        for (i in startIndex until chunk.length) {
            if (chunk[i] == target) {
                return i
            }
        }
        return chunk.length
    }

    private fun findNextControlChar(chunk: String, startIndex: Int): Int {
        for (i in startIndex until chunk.length) {
            if (chunk[i].isInlineControl()) {
                return i
            }
        }
        return chunk.length
    }

    private fun findNextCustomMarkupControl(chunk: String, startIndex: Int): Int {
        for (i in startIndex until chunk.length) {
            val c = chunk[i]
            if (c == '<' || c == '\n') {
                return i
            }
        }
        return chunk.length
    }

    private suspend fun SemanticEventScope.process(char: Char) {
        // Inline raw-text skip (GFM §6.11): drop the body of a mid-line disallowed
        // tag until its close completes. `\n` falls through so the block machine
        // and `flushInline` bound the skip to a single line.
        if (inlineRawSkipTag != null && char != '\n') {
            consumeInlineRawSkipChar(char)
            return
        }
        // Blockquote prefix interceptor: consumes `>` (and optional space) at the
        // start of each line for every enclosing Blockquote frame, before chars
        // reach the inner sub-parser. Failed prefix consumption either closes
        // the blockquote(s) or routes the line as lazy continuation.
        if (interceptBlockquoteLine(char)) return

        when (val mode = blockMode) {
            Start -> processStart(char)
            is Heading -> processHeading(char, mode)
            Paragraph -> processParagraph(char)
            ParagraphContinuation -> processParagraphContinuation(char)
            is CodeBlock -> processCodeBlock(char, mode)
            IndentedCodeBlock -> processIndentedCodeBlock(char)
            is ListBlock -> processListBlock(char, mode)
            UnorderedList -> processUnorderedList(char)
            OrderedList -> processOrderedList(char)
            Blockquote -> { /* passive frame; chars dispatch to the Start above it */ }
            MathBlock -> processMathBlock(char)
            is TableHeaderPending -> processTableHeaderPending(char, mode)
            is TableBody -> processTableBody(char, mode)
            is CustomMarkup -> processCustomMarkup(char, mode.tagName)
            is HtmlBlock1 -> processHtmlBlock1(char, mode)
            is HtmlBlock2to5 -> processHtmlBlock2to5(char, mode)
            Doctype -> processDoctypeBlock(char)
            is HtmlBlock6or7 -> processHtmlBlock6or7(char, mode)
        }

        // Reset prefix-consume state at line boundaries so the next line starts
        // a fresh prefix detection cycle (only meaningful when a Blockquote frame
        // is still on the stack).
        if (char == '\n' && blockquoteFrameCount() > 0) {
            blockquotePrefixDone = false
            blockquotePrefixCount = 0
            blockquotePrefixIndent = 0
            blockquoteJustAfterGt = false
        }
    }

    /**
     * Replay [line] through the same dispatch loop as [processChunk]: honors the
     * [pendingDeferredChar] protocol (so eager-paragraph and other buffer-resolution
     * branches can defer a char back to the outer loop) and uses fast-path emission
     * once the new block mode supports it. Used when a line buffered by one block
     * mode (e.g. a list-end line) needs to be re-fed through Start mode.
     */
    private suspend fun SemanticEventScope.replay(line: String) {
        if (line.isEmpty()) return
        var index = 0
        while (index < line.length) {
            val fastPathResult = getFastPathEnd(line, index)
            if (fastPathResult > index) {
                flushDeferredParagraphSpaces()
                +line.substring(index, fastPathResult)
                prevInlineChar = line[fastPathResult - 1]
                index = fastPathResult
                continue
            }
            process(line[index])
            if (pendingDeferredChar != null) {
                pendingDeferredChar = null
            } else {
                index++
            }
        }
    }

    /**
     * Emit any deferred paragraph mid-line space run as text. Called before a
     * fast-path substring emit so that mid-line spaces accumulated by the
     * char-by-char dispatcher (where each `' '` increments the counter without
     * emitting) appear in the output before the next non-space content.
     */
    private suspend fun SemanticEventScope.flushDeferredParagraphSpaces() {
        if (paragraphTrailingSpaces > 0 && blockMode == BlockMode.Paragraph) {
            val n = paragraphTrailingSpaces
            paragraphTrailingSpaces = 0
            +" ".repeat(n)
            prevInlineChar = ' '
        }
    }

    private suspend fun SemanticEventScope.processStart(
        char: Char
    ) {
        // Eager paragraph opening: if this is the first char of a fresh line and the
        // char cannot start any other block, open the paragraph now and reprocess the
        // char through Paragraph mode (which supports fast-path for incremental emission).
        if (lineBuffer.isEmpty() && char != '\n' && !char.isBlockStart()) {
            mark("p")
            replaceMode(BlockMode.Paragraph)
            pendingDeferredChar = char
            return
        }

        // Handle newline - process buffered line
        if (char == '\n') {
            val line = lineBuffer.toString()
            lineBuffer.clear()

            if (line.isBlank()) {
                // Blank line (including whitespace-only): stay in start.
                // A whitespace-only line is not enough to open an indented code
                // block — GFM strips leading blanks even if they contain ≥4 spaces.
                return
            }

            // Close-tag detection for sub-parsed HTML 6/7 blocks. If any enclosing
            // frame's matching root close tag is on this line, pop everything above
            // it (including the pushed sub-parse `Start` frame) and pop the HTML
            // frame, emitting unmarks for its open tags.
            if (tryCloseEnclosingHtmlBlock(line)) return

            when {
                // Indented code block: ≥4 cols of leading whitespace (tabs count to tab stop 4).
                leadingIndentCols(line) >= 4 -> {
                    mark("pre")
                    mark("code")
                    +"${stripIndentCols(line, 4)}\n"
                    indentedCodeDeferredBlanks.clear()
                    replaceMode(BlockMode.IndentedCodeBlock)
                }
                // ATX heading allowing tab/space after #s
                line matches Patterns.ATX_HEADING_LINE -> {
                    val match = Patterns.ATX_HEADING_LINE.matchEntire(line)!!
                    val level = match.groupValues[1].length
                    val content = match.groupValues[2].trimEnd().removeSuffix("#").trimEnd()
                    "h$level" {
                        if (content.isNotEmpty()) processInlineContent(content)
                        flushInline()
                    }
                }
                // GFM thematic break: 3+ matching `-`/`*`/`_` separated by spaces/tabs
                line matches Patterns.THEMATIC_BREAK -> {
                    "hr" {}
                }
                // Single-line blockquote/list whose content is an indented code block —
                // catches GFM examples like `>\t\tfoo` and `-\t\tfoo` that the incremental
                // detector below misses because the marker is followed by a tab, not a space.
                tryEmitSingleLineContainerWithCode(line) -> {
                    // Already emitted; nothing more to do.
                }
                // Multi-line list (with leading-space indentation, blank lines, indented
                // continuations, or nested items). The incremental detector handles only
                // the simplest `- foo\n- bar\n` case; this branch buffers the full list
                // block and emits it structurally on close.
                shouldStartListBlock(line) -> {
                    openListBlockWithMarker(line)
                }
                // Blockquote opener: 0..3 spaces + `>` then optional content.
                // Sub-parses inner content via a Start frame pushed atop a
                // Blockquote frame; subsequent lines have their `>` prefix
                // consumed by `interceptBlockquoteLine` before reaching the
                // inner sub-parser.
                isBlockquoteOpener(line) -> {
                    openBlockquote(line)
                }
                // Fenced code block opening: ``` or ~~~ (GFM §4.5)
                parseFenceOpen(line) != null -> {
                    val fence = parseFenceOpen(line)!!
                    mark("pre")
                    val codeAttrs = fence.language?.let { mapOf("class" to "language-$it") } ?: emptyMap()
                    mark("code", attributes = codeAttrs)
                    replaceMode(BlockMode.CodeBlock(fence.marker, fence.length, fence.indent))
                }
                // Horizontal rule: ---
                line matches Patterns.HORIZONTAL_RULE -> {
                    "hr" {}
                }
                // Math block: $$
                line == "$$" -> {
                    mark("math", attributes = mapOf("display" to "block"))
                    replaceMode(BlockMode.MathBlock)
                }
                // Table header (provisional). Don't emit yet — buffer the line
                // and decide on the next line whether this is actually a table.
                // See TableHeaderPending for the rationale.
                line.startsWith("|") && !suppressTableDetection -> {
                    replaceMode(BlockMode.TableHeaderPending(line))
                }
                // HTML block detection (CommonMark 4.6) - check before custom markup.
                // DIVERGENCE: when sub-parsing inside a blockquote, suppress HTML block
                // detection so `> <div>` etc. flow as paragraph content (matches the
                // existing 04_06 behaviour for blockquote-prefixed HTML).
                detectHtmlBlockType(line) > 0 && !isInsideBlockquote() -> {
                    enterHtmlBlock(line)
                }
                // Custom markup opening tag: <namespace:name ...>
                line.startsWith("<") && line.endsWith(">") && !line.startsWith("</") -> {
                    val parsed = parseCustomMarkupOpeningTag(line)
                    if (parsed != null) {
                        val (tagName, attributes) = parsed
                        mark(tagName, isTagged = true, attributes = attributes ?: emptyMap())
                        customMarkupSkipFirstNewline = false  // Already consumed by line-based detection
                        customMarkupPendingNewline = false  // Reset any stale state from previous custom markup
                        replaceMode(BlockMode.CustomMarkup(tagName))
                    } else {
                        // Not a valid custom markup tag, treat as paragraph
                        beginParagraph(line)
                    }
                }
                // Link reference definition (CommonMark §4.7). Recognized as a
                // single-line shape `[label]: dest` (with optional title) and
                // consumed silently — produces no events. Multi-line shapes
                // fall through to paragraph processing (DIVERGENCE).
                line.trimStart(' ').startsWith("[") && tryParseLinkDefinition(line) -> {
                    // No-op: definition recorded in linkDefinitions.
                }
                // Single line paragraph (kept open for multi-line continuation)
                else -> beginParagraph(line)
            }
            return
        }

        lineBuffer.append(char)
        // Check for block-level patterns
        val line = lineBuffer.toString()
        when {
            // Headings: # ## ### etc.
            line matches Patterns.HEADING_WITH_SPACE -> {
                val level = line.count { it == '#' }
                mark("h$level")
                lineBuffer.clear()
                replaceMode(BlockMode.Heading(level))
            }
            line matches Patterns.HEADING_NO_SPACE -> {
                // Keep buffering to see if space follows
            }
            line matches Patterns.TOO_MANY_HASHES -> {
                // Too many #, treat as paragraph
                mark("p")
                processInlineContent(line)
                lineBuffer.clear()
                replaceMode(BlockMode.Paragraph)
            }
            // Fenced code block opener (`` ``` `` or `~~~`) — keep buffering until newline
            // so the full info string can be parsed by parseFenceOpen.
            parseFenceOpen(line) != null -> {
                // Keep buffering for newline
            }
            // Horizontal rule: --- keep buffering
            line matches Patterns.DASHES -> {
                // Keep buffering (could be HR or list start)
            }
            // List items are now handled at line dispatch (`\n` handler) via ListBlock mode,
            // which buffers the full list block. We still need to keep the buffer intact
            // during character-by-character buffering so the line is delivered to the
            // dispatcher whole.
            line matches Patterns.TASK_PARTIAL || line == "- [" || line == "- " -> {
                // Keep buffering — defer all list decisions to the `\n` handler.
            }
            line.startsWith("- ") -> {
                // Keep buffering — defer all list decisions to the `\n` handler.
            }
            line matches Patterns.ORDERED_LIST_PARTIAL -> {
                // Keep buffering for ordered list dispatch at `\n`.
            }
            line matches Patterns.ORDERED_LIST_ITEM -> {
                // Defer to `\n` handler; ListBlock handles ordered + nested ordered.
            }
            // Blockquote opener: any line starting with `>` is dispatched at `\n`
            // by `openBlockquote(line)`. Buffer until newline so we have the full
            // line for prefix stripping and inner-content replay.
            line.startsWith(">") -> {
                // Keep buffering until `\n`.
            }
            // Table: | header |
            line.startsWith("|") -> {
                // Keep buffering until newline
            }
            // Math block: $$
            line == "$$" || line == "$" -> {
                // Keep buffering
            }
            // Custom markup: detect opening tag incrementally when > is seen
            line.startsWith("<") && line.endsWith(">") && !line.startsWith("</") -> {
                // Try to parse as custom markup opening tag immediately
                val parsed = parseCustomMarkupOpeningTag(line)
                if (parsed != null) {
                    val (tagName, attributes) = parsed
                    mark(tagName, isTagged = true, attributes = attributes ?: emptyMap())
                    lineBuffer.clear()
                    customMarkupSkipFirstNewline = true  // Skip newline that may follow immediately
                    customMarkupPendingNewline = false  // Reset any stale state from previous custom markup
                    replaceMode(BlockMode.CustomMarkup(tagName))
                }
                // If not valid custom markup, keep buffering until newline
            }
            line.startsWith("<") -> {
                // Keep buffering - could become custom markup tag or HTML block
            }
            // Leading spaces (up to 3) - keep buffering, could precede a block element
            line.all { it == ' ' } && line.length <= 3 -> {
                // Keep buffering
            }
            // Lines with leading spaces followed by < - keep buffering for potential HTML block
            line.trimStart(' ').startsWith("<") && line.length - line.trimStart(' ').length <= 3 -> {
                // Keep buffering - could be HTML block with leading spaces
            }
            !line.first().isBlockStart() -> {
                // Not a special line start — keep buffering until newline so the
                // entire line is processed as one paragraph in the \n handler.
            }
        }
    }

    private suspend fun SemanticEventScope.processHeading(
        char: Char,
        mode: BlockMode.Heading
    ) {
        when (char) {
            '\n' -> {
                // pendingSpaces holds trailing whitespace and any closing-#
                // candidate; both are discarded at line end.
                mode.pendingSpaces.clear()
                // GFM example 671: a `\<newline>` at the end of a heading
                // closes the heading with the `\` rendered as literal text
                // (no `<br/>` — headings are single-line and never continue).
                if (escaped) {
                    +"\\"
                    escaped = false
                }
                flushInline()
                unmark("h${mode.level}")
                replaceMode(BlockMode.Start)
            }
            ' ', '\t' -> when {
                // Leading whitespace before any content or closing-# candidate.
                !mode.contentStarted && mode.pendingSpaces.isEmpty() -> {}
                // Pending delimiter or escape needs this char now to resolve
                // flanking. Route directly — pendingSpaces is reserved for
                // trailing-or-closing material observed with a clean inline
                // state.
                inlineBuffer.isNotEmpty() || escaped -> processInlineChar(char)
                else -> mode.pendingSpaces.append(char)
            }
            '#' -> when {
                inlineBuffer.isNotEmpty() || escaped -> processInlineChar(char)
                // Potential closing-#: a `#` that follows the opening's
                // whitespace (no content yet) or that follows already-buffered
                // trailing material. Defer; flushed as content if non-trailing
                // chars follow, dropped on `\n`.
                !mode.contentStarted || mode.pendingSpaces.isNotEmpty() ->
                    mode.pendingSpaces.append(char)
                else -> processInlineChar(char)
            }
            else -> {
                if (mode.pendingSpaces.isNotEmpty()) {
                    // pendingSpaces is only populated when inlineBuffer was
                    // empty, so it's still empty here — emit as plain text and
                    // sync flanking state.
                    +mode.pendingSpaces.toString()
                    prevInlineChar = mode.pendingSpaces[mode.pendingSpaces.length - 1]
                    mode.pendingSpaces.clear()
                }
                mode.contentStarted = true
                processInlineChar(char)
            }
        }
    }

    private suspend fun SemanticEventScope.processParagraph(
        char: Char
    ) {
        when (char) {
            '\n' -> {
                // Trailing ASCII spaces accumulated in `paragraphTrailingSpaces` are
                // dropped from the visible stream; the count is preserved across the
                // line boundary so `processParagraphContinuation` can promote it to
                // a `<br/>` (GFM §6.7) once continuation is confirmed, or discard it
                // if the paragraph ends here.
                if (escaped) {
                    // Backslash immediately before `\n` is a hard line break (GFM §6.7).
                    // Reuse the trailing-space tally so `emitParagraphLineBreak` produces
                    // `<br/>` once the next line confirms continuation. Also flag the
                    // backslash so paragraph-close paths can emit it as literal text
                    // (`<p>foo\</p>`, GFM example 669).
                    escaped = false
                    paragraphTrailingSpaces = 2
                    paragraphTrailingBackslash = true
                }
                flushInline(softBreak = true)
                lineBuffer.clear()
                atLineStart = true
                replaceMode(BlockMode.ParagraphContinuation)
            }
            ' ' -> {
                if (inlineBuffer.isNotEmpty() || escaped || inEntityRef ||
                    linkLabelTentativeClose
                ) {
                    // Inline state is non-neutral (e.g. an unresolved `***`
                    // delimiter run, a pending backslash escape, an in-flight
                    // entity ref, or a tentative `]` waiting for the next char
                    // to resolve a link/ref-shortcut). The space must flow
                    // through inline processing to drive resolution;
                    // re-processing via `pendingDeferredChar` will then re-enter
                    // this `' '` branch with a clean buffer and increment the
                    // counter normally.
                    if (paragraphTrailingSpaces > 0) {
                        val n = paragraphTrailingSpaces
                        paragraphTrailingSpaces = 0
                        repeat(n) { processInlineChar(' ') }
                    }
                    atLineStart = false
                    processInlineChar(' ')
                } else {
                    // Defer: this run might be trailing whitespace before `\n`.
                    // If a non-space char follows on the same line, the spaces
                    // flush as text (mid-line whitespace); if `\n` follows, the
                    // count survives as the trailing-space tally for the
                    // hard-break decision.
                    paragraphTrailingSpaces++
                }
            }
            else -> {
                if (paragraphTrailingSpaces > 0) {
                    repeat(paragraphTrailingSpaces) { processInlineChar(' ') }
                    paragraphTrailingSpaces = 0
                }
                atLineStart = false
                processInlineChar(char)
            }
        }
    }

    /** Open a paragraph for the given first line and leave it open for continuation lines. */
    private suspend fun SemanticEventScope.beginParagraph(line: String) {
        mark("p")
        // Strip leading spaces/tabs (CommonMark: leading whitespace on a paragraph's
        // first line is not part of inline content; up to 3 spaces of indentation is
        // permitted, and 4+ spaces would have been claimed by the indented-code-block
        // branch upstream of here).
        val leftTrimmed = line.trimStart(' ', '\t')
        val content = leftTrimmed.trimEnd(' ')
        paragraphTrailingSpaces = leftTrimmed.length - content.length
        processInlineContent(content)
        captureLineEndBackslash()
        flushInline(softBreak = true)
        replaceMode(BlockMode.ParagraphContinuation)
    }

    /**
     * Returns true when [char] is a valid first char inside an inline math
     * span (`$char…$`). The conservative set is letters, `\` (LaTeX command),
     * and `{` (group opener) — exclusions like digits ("$5", currency),
     * whitespace, and punctuation ($... GFM §6.14 ex 675) keep `$` as
     * literal text in those contexts.
     */
    private fun isMathOpenChar(char: Char): Boolean =
        char.isLetter() || char == '\\' || char == '{'

    /**
     * GFM §6.7 hard line break via `\<newline>`: when [processInlineContent]
     * finishes with [escaped] set, the line ended with a literal `\`. Promote
     * it to the same hard-break tally as `≥2 trailing spaces` so the next
     * line's [emitParagraphLineBreak] produces `<br/>`, and flag the
     * backslash so a paragraph-close path emits it as literal text instead
     * (GFM example 669 `<p>foo\</p>`).
     */
    private fun captureLineEndBackslash() {
        if (escaped) {
            escaped = false
            paragraphTrailingSpaces = 2
            paragraphTrailingBackslash = true
        }
    }

    /**
     * Continue or end a paragraph at line boundaries. The previous line's content is
     * already emitted; we now buffer the next line and decide on `\n` whether to:
     *   - end the paragraph (blank line or block-start line), or
     *   - continue it (emit a soft break and the line's inline content).
     */
    private suspend fun SemanticEventScope.processParagraphContinuation(
        char: Char
    ) {
        // Eager continuation: if this is the first char of the next line and it cannot
        // start a block that interrupts the paragraph, emit the soft-break newline and
        // switch to Paragraph mode so subsequent chars stream incrementally via fast-path.
        if (lineBuffer.isEmpty() && char != '\n' && !char.isBlockStart()) {
            emitParagraphLineBreak()
            replaceMode(BlockMode.Paragraph)
            pendingDeferredChar = char
            return
        }

        if (char != '\n') {
            lineBuffer.append(char)
            return
        }
        val line = lineBuffer.toString()
        lineBuffer.clear()
        if (line.isBlank()) {
            // GFM treats a whitespace-only line as a blank line (§4.9). At top level
            // this closes the paragraph; inside a blockquote the same close happens
            // here and the outer blockquote machinery preserves the blockquote frame.
            flushPendingTrailingBackslash()
            endParagraph()
            replaceMode(BlockMode.Start)
            paragraphTrailingSpaces = 0
            return
        }
        if (lineInterruptsParagraph(line)) {
            flushPendingTrailingBackslash()
            endParagraph()
            replaceMode(BlockMode.Start)
            paragraphTrailingSpaces = 0
            // Replay the line through Start so it can be parsed as its own block.
            replay(line)
            process('\n')
            return
        }
        // Continuation line: hard or soft break then inline content.
        // Leading spaces/tabs are normally stripped (CommonMark: indented code cannot
        // interrupt a paragraph, so leading indentation on continuation lines is
        // paragraph content with the indentation removed). Trailing spaces are stripped
        // here and their count carried forward for the *next* line's hard-break decision.
        // EXCEPTION: when a tagged inline HTML frame is still open across this soft break
        // (kept by `flushInline(softBreak = true)`), the leading whitespace is *content*
        // inside that tag, not paragraph indentation — preserve it so the tag round-trips
        // (e.g. `<label>…<input>\n outline</label>`).
        val leftTrimmed = if (inlineOpenStack.any { it.isTagged }) line else line.trimStart(' ', '\t')
        val stripped = leftTrimmed.trimEnd(' ')
        val newTrailing = leftTrimmed.length - stripped.length
        emitParagraphLineBreak()
        // Special case: a line that is exactly one open HTML tag is rendered as a
        // self-closing-equivalent (mark + unmark) so the event tree stays balanced.
        val singleTag = tryParseOpenTag(stripped, 0)
        if (singleTag != null && singleTag.first == stripped.length &&
            singleTag.second.name.lowercase() !in INLINE_HTML_BLOCK_ELEMENTS
        ) {
            val tag = singleTag.second
            mark(tag.name, isTagged = true, attributes = tag.attributes ?: emptyMap())
            unmark(tag.name, isTagged = true)
        } else {
            processInlineContent(stripped)
            captureLineEndBackslash()
            flushInline(softBreak = true)
        }
        if (paragraphTrailingBackslash) {
            // captureLineEndBackslash overrode newTrailing — keep the hard-break
            // tally it set instead of clobbering with the trimEnd count.
        } else {
            paragraphTrailingSpaces = newTrailing
        }
        replaceMode(BlockMode.ParagraphContinuation)
    }

    /**
     * Emit the line break separating the previous paragraph line from the next.
     * If the previous line ended with ≥2 trailing ASCII spaces (GFM §6.7), the
     * break is a hard `<br/>` followed by `\n`; otherwise just `\n`. Resets
     * `paragraphTrailingSpaces` to 0.
     */
    private suspend fun SemanticEventScope.emitParagraphLineBreak() {
        if (paragraphTrailingSpaces >= 2) {
            mark("br")
            unmark("br")
        }
        paragraphTrailingSpaces = 0
        // Continuation absorbed the `\<newline>` as a hard break — clear the
        // pending-backslash flag without emitting the literal `\` (the `<br/>`
        // already replaces it).
        paragraphTrailingBackslash = false
        +"\n"
    }

    /**
     * Emit a trailing `\` as literal text on a paragraph-close path that ends
     * with `\<newline>` and no continuation (GFM example 669: `<p>foo\</p>`).
     * Caller must invoke this *before* `unmark("p")` so the text lives inside
     * the paragraph.
     */
    private suspend fun SemanticEventScope.flushPendingTrailingBackslash() {
        if (paragraphTrailingBackslash) {
            +"\\"
            paragraphTrailingBackslash = false
        }
    }

    /**
     * Close the current paragraph's `</p>`, first force-closing any inline opens
     * still on the stack. `flushInline(softBreak = true)` lets a tagged inline
     * HTML frame (e.g. an unclosed `<label>`) survive soft breaks, so it can
     * still be open at this hard paragraph boundary and must close here to keep
     * the event stream balanced. For an ordinary paragraph the stack is already
     * empty (the soft flush drained every non-tagged frame, and a hard flush ran
     * on most close paths), so the drain is a no-op and behaviour is unchanged.
     */
    private suspend fun SemanticEventScope.endParagraph() {
        while (inlineOpenStack.isNotEmpty()) {
            val frame = inlineOpenStack.removeLast()
            unmark(frame.name, isTagged = frame.isTagged)
        }
        unmark("p")
    }

    /**
     * Returns true if [line] would start a block construct that interrupts an open
     * paragraph. Conservative: any line whose first non-leading-space token looks
     * like a markanywhere block opener (heading, list, blockquote, fenced code,
     * HTML block, table, math) ends the current paragraph.
     */
    private fun lineInterruptsParagraph(line: String): Boolean {
        if (line.isEmpty()) return true
        // Heading-like: any line starting with `#` (markanywhere treats invalid headings as their own paragraph).
        if (line.startsWith("#")) return true
        // Fenced code or thematic break (GFM §4.1: a thematic break can interrupt
        // a paragraph). HORIZONTAL_RULE is a subset of THEMATIC_BREAK kept for
        // belt-and-suspenders; THEMATIC_BREAK matches `***`, `___`, `* * *`, etc.
        if (parseFenceOpen(line) != null) return true
        if (line matches Patterns.HORIZONTAL_RULE) return true
        if (line matches Patterns.THEMATIC_BREAK) return true
        // Math block
        if (line == "$$") return true
        // DIVERGENCE: GFM allows a `|`-line to interrupt a paragraph and start
        // a table (when followed by a valid separator). That requires the same
        // 2-line lookahead from ParagraphContinuation that processStart uses,
        // which we don't implement. As a result a `|`-line in mid-paragraph
        // stays paragraph content; tables only start at a block boundary
        // (after a blank line or another block close).
        // Blockquote
        if (line == ">" || line.startsWith("> ")) return true
        // Lists
        if (line matches Patterns.TASK_UNCHECKED) return true
        if (line matches Patterns.TASK_CHECKED) return true
        if (line.startsWith("- ") && line.length > 2 && line[2] != '[') return true
        // Ordered list: digit(s) then '.' then ' '. An empty marker (no space
        // after `.`) cannot interrupt a paragraph — GFM §5.2 example 263.
        // Only a `1.` marker can interrupt a paragraph (GFM §5.4 example 284):
        // otherwise `windows is\n14. ...` would split into a paragraph +
        // ordered list with start=14.
        var i = 0
        while (i < line.length && line[i].isDigit()) i++
        if (i > 0 && i + 1 < line.length && line[i] == '.' && line[i + 1] == ' '
            && line.substring(0, i).trimStart('0') == "1"
        ) return true
        // HTML block start: types 1-6 interrupt paragraphs (type 7 does not by spec).
        val type = detectHtmlBlockType(line)
        if (type in 1..6) return true
        // Inside a sub-parsed HTML 6/7 frame, a stand-alone close tag for any
        // tag tracked by an enclosing frame's `openTags` (e.g. `</pre>` inside
        // a sub-parsed `<table>...<pre>...`) closes that inner element — let
        // the paragraph drop out so the close-tag check can fire in `processStart`.
        if (findEnclosingHtmlOpenTagClose(line) != null) return true
        return false
    }

    private suspend fun SemanticEventScope.processCodeBlock(
        char: Char,
        mode: BlockMode.CodeBlock
    ) {
        if (char == '\n') {
            val line = lineBuffer.toString()
            lineBuffer.clear()
            if (isFenceClose(line, mode.marker, mode.length)) {
                unmark("code")
                unmark("pre")
                replaceMode(BlockMode.Start)
            } else {
                +"${stripIndentCols(line, mode.indent)}\n"
            }
        } else {
            lineBuffer.append(char)
        }
    }

    /**
     * True if [line] should open a multi-line list block. Any line whose first
     * non-leading-space character begins a list marker is routed through the
     * streaming `ListBlock` state machine.
     */
    private fun shouldStartListBlock(line: String): Boolean =
        parseListMarker(line) != null

    /**
     * Open a fresh `BlockMode.ListBlock` for [markerLine] (which already starts a
     * list marker), emit `<ul>`/`<ol>` + `<li>` immediately, and emit the marker
     * line's inline content (always wrapped in `<p>` per the always-loose policy).
     */
    private suspend fun SemanticEventScope.openListBlockWithMarker(markerLine: String) {
        val mode = BlockMode.ListBlock()
        replaceMode(mode)
        startListItemFromMarker(mode, markerLine, parentContentCol = 0)
    }

    /**
     * Emit the opening tags for a new list level whose marker line is [markerLine],
     * relative to [parentContentCol] (the absolute column at which [markerLine]
     * begins — non-zero when the line was already stripped of an outer container's
     * indent). Pushes a new context onto [mode]'s stack and opens its first `<li>`
     * plus a `<p>` if the marker has inline content.
     */
    private suspend fun SemanticEventScope.startListItemFromMarker(
        mode: BlockMode.ListBlock,
        markerLine: String,
        parentContentCol: Int
    ) {
        val marker = parseListMarker(markerLine) ?: return
        val ctx = ListContext(
            ordered = marker.ordered,
            markerChar = marker.markerChar,
            markerStartCol = parentContentCol + marker.markerStartCol,
            contentCol = parentContentCol + marker.contentCol
        )
        mode.stack += ctx
        val listAttrs = if (marker.ordered && marker.digits != null) {
            val start = marker.digits.trimStart('0').ifEmpty { "0" }
            if (start != "1") mapOf("start" to start) else emptyMap()
        } else emptyMap()
        mark(if (ctx.ordered) "ol" else "ul", attributes = listAttrs)
        mark("li")
        val firstContent = markerLine.substring(marker.markerEndIndex)
        if (firstContent.isNotBlank()) {
            // Same-line nested list (GFM §5.2 examples 276, 277): if the content
            // immediately following the marker is itself a list marker, recurse
            // to open the nested level rather than emit it as paragraph text.
            if (parseListMarker(firstContent) != null) {
                ctx.hasContent = true
                startListItemFromMarker(mode, firstContent, parentContentCol = ctx.contentCol)
            } else {
                emitItemFirstLine(firstContent, ctx)
            }
        }
        mode.blankSeen = false
    }

    /**
     * Emit the first content line of a freshly opened `<li>`. If the content is a
     * stand-alone block-level construct (currently only thematic break), emit it
     * directly with no surrounding `<p>`. Otherwise open `<p>` and process the
     * content as inline. Updates [ctx].paragraphOpen accordingly.
     */
    private suspend fun SemanticEventScope.emitItemFirstLine(
        firstContent: String,
        ctx: ListContext
    ) {
        val trimmed = firstContent.trimEnd()
        ctx.hasContent = true
        if (trimmed matches Patterns.THEMATIC_BREAK) {
            "hr" {}
            return
        }
        // GFM §5.2: when ≥5 spaces follow the list marker, content begins at
        // marker+1 and the remaining whitespace is part of the content. ≥4 leading
        // spaces in that content open an indented code block as the item's first
        // child rather than a paragraph (examples 235, 251, 252).
        if (leadingIndentCols(firstContent) >= 4) {
            mark("pre")
            mark("code")
            ctx.codeBlockOpen = true
            +"${stripIndentCols(trimmed, 4)}\n"
            return
        }
        // ATX heading as first content of a list item (example 278).
        if (trimmed matches Patterns.ATX_HEADING_LINE) {
            val match = Patterns.ATX_HEADING_LINE.matchEntire(trimmed)!!
            val level = match.groupValues[1].length
            val content = match.groupValues[2].trimEnd().removeSuffix("#").trimEnd()
            "h$level" {
                if (content.isNotEmpty()) processInlineContent(content)
                flushInline()
            }
            return
        }
        // Fenced code as first content of a list item (GFM §5.4 example 304).
        // Subsequent indented lines stream through `processListBlock`'s
        // `ctx.fencedCodeOpen` branch until the matching close fence.
        val fence = parseFenceOpen(trimmed)
        if (fence != null) {
            mark("pre")
            val codeAttrs = fence.language?.let { mapOf("class" to "language-$it") } ?: emptyMap()
            mark("code", attributes = codeAttrs)
            ctx.fencedCodeOpen = true
            ctx.fencedMarker = fence.marker
            ctx.fencedLength = fence.length
            return
        }
        // Display math as first content of a list item (e.g. `- $$`). Subsequent
        // lines stream through `processListBlock`'s `ctx.mathBlockOpen` branch.
        if (trimmed == "$$") {
            mark("math", attributes = mapOf("display" to "block"))
            ctx.mathBlockOpen = true
            return
        }
        // Blockquote as first content of a list item (e.g. `1. > X`). Open the
        // inner `<blockquote>` and its `<p>`; subsequent `>`-prefixed lines (or
        // lazy-continuation lines) extend the same paragraph via the
        // blockquote-handling branches in `processListBlock`.
        if (isBlockquoteOpener(trimmed)) {
            mark("blockquote")
            ctx.blockquoteOpen = true
            mark("p")
            ctx.blockquoteParagraphOpen = true
            val bqContent = stripBlockquotePrefix(trimmed)
            if (bqContent.isNotBlank()) {
                processInlineContent(bqContent.trimEnd())
            }
            flushInline()
            return
        }
        // Table header as the first content of a list item (`- | a | b |`).
        // Defer paragraph-opening until the next line either confirms a
        // separator (committing the table) or aborts (drained as paragraph
        // by `drainListPendingTableHeader`). `suppressTableDetection` matters
        // when top-level `TableHeaderPending` rejects a header and replays
        // the lines (e.g. `- | a | b |` happens to be both a list marker and
        // a `|`-line) — without the flag we'd buffer again here and loop.
        // The list-internal abort-replay path (within `processListBlock`)
        // does not set this flag because the replayed line lazy-continues
        // the just-drained paragraph instead of re-entering detection — see
        // `back-to-back pipe lines without separator merge into one paragraph`.
        if (trimmed.startsWith("|") && !suppressTableDetection) {
            ctx.tableHeaderPending = trimmed
            return
        }
        // HTML block (CommonMark §4.6) as the first content of a list item.
        // Subsequent lines stream through `processListBlock`'s `ctx.htmlBlock`
        // branch until the matching root close tag (or closing sequence).
        if (tryEnterListHtmlBlock(ctx, trimmed)) {
            return
        }
        // Custom markup (`<ns:name …>`) as the first content of a list item.
        // Subsequent lines stream through `processListBlock`'s
        // `ctx.customMarkupTagName != null` branch until the matching
        // `</tagName>` closing line.
        if (tryEnterListCustomMarkup(ctx, trimmed)) {
            return
        }
        // Link reference definition (CommonMark §4.7) as the first content of
        // a list item (`- [foo]: /url`). Same contract as the top-level path:
        // single-line shape only, registered in `linkDefinitions`, no events.
        if (tryParseLinkDefinition(trimmed)) {
            return
        }
        mark("p")
        ctx.paragraphOpen = true
        emitItemFirstContent(trimmed)
        flushInline()
    }

    /**
     * Emit the first paragraph content of a list item. If [content] begins with a
     * GFM task-list marker (`\[ \]`, `\[x\]`, or `\[X\]` followed by a space), emit
     * a disabled checkbox `<input>` and pass the remainder (including the space
     * after `\]`) through inline processing. Otherwise process [content] as inline.
     */
    private suspend fun SemanticEventScope.emitItemFirstContent(content: String) {
        if (content.length >= 4
            && content[0] == '['
            && content[2] == ']'
            && content[3] == ' '
            && (content[1] == ' ' || content[1] == 'x' || content[1] == 'X')
        ) {
            val checked = content[1] != ' '
            if (checked) {
                "input"("checked" to "", "disabled" to "", "type" to "checkbox") {}
            } else {
                "input"("disabled" to "", "type" to "checkbox") {}
            }
            processInlineContent(content.substring(3))
        } else {
            processInlineContent(content)
        }
    }

    /** Close any open `<p>` in the top context. */
    private suspend fun SemanticEventScope.closeListParagraphIfOpen(mode: BlockMode.ListBlock) {
        val top = mode.stack.lastOrNull() ?: return
        if (top.paragraphOpen) {
            flushInline()
            endParagraph()
            top.paragraphOpen = false
        }
    }

    /** Close any open code block in the top context. */
    private suspend fun SemanticEventScope.closeListCodeIfOpen(mode: BlockMode.ListBlock) {
        val top = mode.stack.lastOrNull() ?: return
        if (top.codeBlockOpen) {
            unmark("code")
            unmark("pre")
            top.codeBlockOpen = false
            top.codeBlankLines = 0
        }
    }

    /** Close any open fenced code block in the top context. */
    private suspend fun SemanticEventScope.closeListFencedCodeIfOpen(mode: BlockMode.ListBlock) {
        val top = mode.stack.lastOrNull() ?: return
        if (top.fencedCodeOpen) {
            unmark("code")
            unmark("pre")
            top.fencedCodeOpen = false
        }
    }

    /** Close any open display-math block in the top context. */
    private suspend fun SemanticEventScope.closeListMathBlockIfOpen(mode: BlockMode.ListBlock) {
        val top = mode.stack.lastOrNull() ?: return
        if (top.mathBlockOpen) {
            unmark("math")
            top.mathBlockOpen = false
        }
    }

    /** Close any open blockquote (and its inner paragraph) in the top context. */
    private suspend fun SemanticEventScope.closeListBlockquoteIfOpen(mode: BlockMode.ListBlock) {
        val top = mode.stack.lastOrNull() ?: return
        if (top.blockquoteOpen) {
            if (top.blockquoteParagraphOpen) {
                flushInline()
                endParagraph()
                top.blockquoteParagraphOpen = false
            }
            unmark("blockquote")
            top.blockquoteOpen = false
        }
    }

    /**
     * Close any open `<table>` in the top context (header-only tables emit no
     * `<tbody>`, mirroring the top-level `closeTableBody`). Pending unresolved
     * table header lines are drained separately by [drainListPendingTableHeader].
     */
    private suspend fun SemanticEventScope.closeListTableIfOpen(mode: BlockMode.ListBlock) {
        val top = mode.stack.lastOrNull() ?: return
        if (top.tableOpen) {
            if (top.tableBodyOpened) unmark("tbody")
            unmark("table")
            top.tableOpen = false
            top.tableBodyOpened = false
            top.tableAlignments = emptyList()
            top.tableColumnCount = 0
        }
    }

    /**
     * Render an unresolved [ListContext.tableHeaderPending] as paragraph text.
     * Used when the header line never saw a confirming separator (next line
     * was something else, or the item closed before a second line arrived).
     * The pending field is always cleared, even when the stored content was
     * empty, so the abort-replay caller cannot loop on a stuck pending state.
     */
    private suspend fun SemanticEventScope.drainListPendingTableHeader(mode: BlockMode.ListBlock) {
        val top = mode.stack.lastOrNull() ?: return
        val pending = top.tableHeaderPending ?: return
        top.tableHeaderPending = null
        // Call sites store the indent-stripped header (which begins with `|`),
        // so leading whitespace is impossible — only trailing whitespace from
        // the source line might still be present.
        val text = pending.trimEnd()
        if (text.isEmpty()) return
        check(text.first() == '|') {
            "ListContext.tableHeaderPending must be indent-stripped " +
                "(begins with `|`); got: '${text.take(20)}'"
        }
        if (!top.paragraphOpen) {
            mark("p")
            top.paragraphOpen = true
        } else {
            +"\n"
        }
        processInlineContent(text)
        flushInline()
        top.hasContent = true
    }

    /**
     * Try to commit a `<table>` inside [ctx] using the buffered header line
     * and [nextLine] as the candidate separator. Returns true on commit
     * (pending cleared, `<table>`/`<thead>` emitted, `tableOpen` set) or
     * false on abort (pending left intact for the caller to drain).
     */
    private suspend fun SemanticEventScope.tryCommitListTable(
        mode: BlockMode.ListBlock,
        ctx: ListContext,
        nextLine: String
    ): Boolean {
        val headerStripped = ctx.tableHeaderPending ?: return false
        val containerContentCol = ctx.contentCol
        val nextIndent = leadingIndentCols(nextLine)
        if (nextIndent < containerContentCol) return false
        val nextStripped = if (containerContentCol > 0)
            stripIndentCols(nextLine, containerContentCol, startCol = 0)
        else nextLine
        if (nextStripped.isBlank()) return false
        val headerCells = splitTableCells(headerStripped.trimEnd())
        val alignments = parseSeparatorAlignments(nextStripped.trimEnd(), headerCells.size)
            ?: return false
        ctx.tableHeaderPending = null
        closeListParagraphIfOpen(mode)
        closeListBlockquoteIfOpen(mode)
        mark("table")
        mark("thead")
        "tr" {
            emitTableCells(headerCells, isHeader = true, alignments = alignments)
        }
        unmark("thead")
        ctx.tableOpen = true
        ctx.tableColumnCount = headerCells.size
        ctx.tableAlignments = alignments
        ctx.tableBodyOpened = false
        ctx.hasContent = true
        mode.blankSeen = false
        return true
    }

    /**
     * Emit a single body row for [ctx]'s open table, opening `<tbody>` lazily
     * on the first row (so a header-only table closes without an empty
     * `<tbody>`, mirroring the top-level `closeTableBody` invariant).
     */
    private suspend fun SemanticEventScope.emitListTableBodyRow(
        ctx: ListContext,
        line: String
    ) {
        if (!ctx.tableBodyOpened) {
            mark("tbody")
            ctx.tableBodyOpened = true
        }
        val cells = splitTableCells(line)
        "tr" {
            emitTableCells(
                cells,
                isHeader = false,
                alignments = ctx.tableAlignments,
                columnCount = ctx.tableColumnCount
            )
        }
    }

    /**
     * Try to enter a CommonMark §4.6 HTML block as the next sub-block of [ctx].
     * Returns true on success ([ctx.htmlBlock] set when the block stays open
     * across more than one source line, or null when the block closed on the
     * same line). Returns false when [line] is not an HTML-block opener.
     *
     * Routes by detected type (1, 2-5, 6/7) and reuses the same line-emission
     * shape as the top-level helpers — without `replaceMode` / `pushMode`,
     * since list-internal HTML state lives on [ctx] (see [ListHtmlBlockState]).
     */
    private suspend fun SemanticEventScope.tryEnterListHtmlBlock(
        ctx: ListContext,
        line: String
    ): Boolean {
        val type = detectHtmlBlockType(line)
        if (type == 0) return false
        // Set early so all early-out paths (unparseable root tag for type 1/6/7)
        // still mark the item as having content — the source line is emitted
        // either way.
        ctx.hasContent = true
        when (type) {
            1 -> {
                val rootTag = type1RootTagOf(line) ?: run {
                    +"$line\n"
                    return true
                }
                val state = ListHtmlBlockState(type = 1, rootTagName = rootTag)
                ctx.htmlBlock = state
                state.firstLineBuffer!!.append(line)
                if (!tryFinishListHtmlBlock1FirstLine(ctx, state)) {
                    // No `>` on the first line: append `\n` and keep buffering.
                    state.firstLineBuffer!!.append('\n')
                }
            }
            2, 3, 4, 5 -> {
                if (type == 4 && isDoctypeLine(line)) {
                    enterListDoctypeBlock(ctx, line)
                    return true
                }
                val seq = when (type) {
                    2 -> "-->"
                    3 -> "?>"
                    4 -> ">"
                    5 -> "]]>"
                    else -> error("unreachable")
                }
                +"$line\n"
                if (!lineContainsClosingSeq(line, seq)) {
                    ctx.htmlBlock = ListHtmlBlockState(
                        type = type,
                        closingSeq = seq
                    )
                }
            }
            6, 7 -> {
                val (rootName, isClose) = type6or7RootTagOf(line) ?: run {
                    +"$line\n"
                    return true
                }
                val state = ListHtmlBlockState(
                    type = type,
                    rootTagName = rootName,
                    rootIsClosingTag = isClose
                )
                ctx.htmlBlock = state
                state.firstLineBuffer!!.append(line)
                if (!tryFinishListHtmlBlock6or7FirstLine(ctx, state)) {
                    state.firstLineBuffer!!.append('\n')
                }
            }
        }
        return true
    }

    /**
     * Stream a single content line of a list-internal HTML block. The block
     * may close on this line; callers should re-check [ctx.htmlBlock] after.
     * DIVERGENCE from the top-level type 6/7 path: blank lines emit `\n` and
     * stay in raw-text mode (no sub-parse) because pushing a fresh `Start`
     * frame above `ListBlock` would knock the list dispatcher out of the
     * top-of-stack slot, losing per-line container-indent stripping.
     */
    private suspend fun SemanticEventScope.streamListHtmlBlockLine(
        ctx: ListContext,
        line: String
    ) {
        val state = ctx.htmlBlock ?: return
        when (state.type) {
            1 -> {
                val firstLineBuffer = state.firstLineBuffer
                if (firstLineBuffer != null) {
                    firstLineBuffer.append(line)
                    if (!tryFinishListHtmlBlock1FirstLine(ctx, state)) {
                        firstLineBuffer.append('\n')
                    }
                    return
                }
                emitListHtmlBlock1ContentLine(ctx, state, line)
            }
            2, 3, 4, 5 -> {
                if (state.isDoctype) {
                    streamListDoctypeLine(ctx, line)
                    return
                }
                +"$line\n"
                if (lineContainsClosingSeq(line, state.closingSeq)) {
                    ctx.htmlBlock = null
                }
            }
            6, 7 -> {
                val firstLineBuffer = state.firstLineBuffer
                if (firstLineBuffer != null) {
                    firstLineBuffer.append(line)
                    if (!tryFinishListHtmlBlock6or7FirstLine(ctx, state)) {
                        firstLineBuffer.append('\n')
                    }
                    return
                }
                streamListHtmlBlock6or7ContentLine(ctx, state, line)
            }
        }
    }

    /**
     * Emit a single content line for a list-internal type-1 HTML block,
     * mirroring [emitHtmlBlock1ContentLine] but writing close-state to
     * [ctx.htmlBlock] instead of `replaceMode(Start)`.
     */
    private suspend fun SemanticEventScope.emitListHtmlBlock1ContentLine(
        ctx: ListContext,
        state: ListHtmlBlockState,
        line: String
    ) {
        val closingPattern = "</${state.rootTagName}"
        val lowerLine = line.lowercase()
        val closeIndex = lowerLine.indexOf(closingPattern)
        if (closeIndex < 0) {
            +"$line\n"
            return
        }
        val afterName = closeIndex + closingPattern.length
        var i = afterName
        while (i < line.length && (line[i] == ' ' || line[i] == '\t')) i++
        if (i >= line.length || line[i] != '>') {
            +"$line\n"
            return
        }
        val closeEnd = i + 1
        val before = line.substring(0, closeIndex)
        if (before.isNotEmpty()) {
            val beforeTokens = tokenizeHtmlLine(before)
            val text = StringBuilder()
            for (tok in beforeTokens) {
                when (tok) {
                    is HtmlToken.Text -> text.append(tok.content)
                    is HtmlToken.CloseTag -> {
                        if (text.isNotEmpty()) { +text.toString(); text.clear() }
                        emitMatchingClose(tok.name, state.openTags)
                    }
                    is HtmlToken.OpenTag -> {
                        if (text.isNotEmpty()) { +text.toString(); text.clear() }
                        markHtml(tok.name, tok.attributes)
                        if (tok.isSelfClosingOrVoid()) unmarkHtml(tok.name)
                        else state.openTags += normalizeHtmlName(tok.name)
                    }
                }
            }
            if (text.isNotEmpty()) +text.toString()
        }
        while (state.openTags.size > 1) unmarkHtml(state.openTags.removeLast())
        if (state.openTags.isNotEmpty()) unmarkHtml(state.openTags.removeLast())
        val trailing = line.substring(closeEnd)
        if (trailing.isNotEmpty()) +"$trailing\n"
        ctx.htmlBlock = null
    }

    /**
     * Once the type-1 first-line buffer holds a line with `>`, parse the
     * opening tag(s), emit marks, and stream any remainder. Mirrors
     * [tryFinishHtmlBlock1FirstLine] but updates [ctx.htmlBlock] state.
     */
    private suspend fun SemanticEventScope.tryFinishListHtmlBlock1FirstLine(
        ctx: ListContext,
        state: ListHtmlBlockState
    ): Boolean {
        val buf = state.firstLineBuffer ?: return true
        val source = buf.toString()
        val ltIndex = source.indexOf('<')
        if (ltIndex < 0) return false
        val leading = source.substring(0, ltIndex)
        var idx = ltIndex
        val opens = mutableListOf<HtmlToken.OpenTag>()
        var foundRoot = false
        while (idx < source.length && source[idx] == '<') {
            val open = tryParseOpenTag(source, idx) ?: break
            opens += open.second
            if (open.second.name.lowercase() == state.rootTagName) foundRoot = true
            idx = open.first
        }
        if (!foundRoot) return false
        if (leading.isNotEmpty()) +leading
        for (tag in opens) {
            markHtml(tag.name, tag.attributes)
            state.openTags += normalizeHtmlName(tag.name)
        }
        state.firstLineBuffer = null
        val remainder = source.substring(idx)
        if (remainder.isNotEmpty()) {
            emitListHtmlBlock1ContentLine(ctx, state, remainder)
        }
        return true
    }

    /**
     * Stream a single content line of a list-internal type-6/7 HTML block,
     * mirroring [streamHtmlBlock6or7ContentLine] but with no sub-parse
     * transition (see DIVERGENCE on [ListContext.htmlBlock]).
     */
    private suspend fun SemanticEventScope.streamListHtmlBlock6or7ContentLine(
        ctx: ListContext,
        state: ListHtmlBlockState,
        line: String
    ) {
        if (state.rootIsClosingTag) {
            +"$line\n"
            return
        }
        if (line.isEmpty()) {
            +"\n"
            return
        }
        val rootCloseIdx = findRootCloseTagIndex(line, state.rootTagName)
        if (rootCloseIdx < 0) {
            emitListHtmlBlock6or7ContentTokens(line, state, trailingNewline = true)
            return
        }
        val before = line.substring(0, rootCloseIdx)
        if (before.isNotEmpty()) {
            emitListHtmlBlock6or7ContentTokens(before, state, trailingNewline = false)
        }
        var p = rootCloseIdx + 2 + state.rootTagName.length
        while (p < line.length && (line[p] == ' ' || line[p] == '\t')) p++
        val closeEnd = p + 1
        while (state.openTags.size > 1) unmarkHtml(state.openTags.removeLast())
        if (state.openTags.isNotEmpty()) unmarkHtml(state.openTags.removeLast())
        val trailing = line.substring(closeEnd)
        if (trailing.isNotEmpty()) +"$trailing\n"
        ctx.htmlBlock = null
    }

    /**
     * Once the type-6/7 first-line buffer holds a complete opening (or
     * closing) tag, emit marks for any open tag(s), then stream any remainder
     * as content. Mirrors [tryFinishHtmlBlock6or7FirstLine].
     */
    private suspend fun SemanticEventScope.tryFinishListHtmlBlock6or7FirstLine(
        ctx: ListContext,
        state: ListHtmlBlockState
    ): Boolean {
        val buf = state.firstLineBuffer ?: return true
        val source = buf.toString()
        var i = 0
        while (i < source.length && source[i] == ' ') i++
        if (i >= source.length || source[i] != '<') return false
        val open = tryParseOpenTag(source, i)
        val close = if (open == null) tryParseCloseTag(source, i) else null
        if (open == null && close == null) return false
        state.firstLineBuffer = null
        if (state.rootIsClosingTag) {
            +"$source\n"
            return true
        }
        val leading = source.substring(0, i)
        if (leading.isNotEmpty()) +leading
        var idx = i
        while (idx < source.length && source[idx] == '<') {
            val nextOpen = tryParseOpenTag(source, idx) ?: break
            markHtml(nextOpen.second.name, nextOpen.second.attributes)
            if (nextOpen.second.isSelfClosingOrVoid()) {
                unmarkHtml(nextOpen.second.name)
            } else {
                state.openTags += normalizeHtmlName(nextOpen.second.name)
            }
            idx = nextOpen.first
        }
        val remainder = source.substring(idx)
        if (remainder.isEmpty()) {
            +"\n"
        } else {
            streamListHtmlBlock6or7ContentLine(ctx, state, remainder)
        }
        return true
    }

    /**
     * Tokenize [line] for a list-internal type-6/7 block, emitting nested
     * open/close marks plus text. Adds a trailing `\n` when [trailingNewline].
     */
    private suspend fun SemanticEventScope.emitListHtmlBlock6or7ContentTokens(
        line: String,
        state: ListHtmlBlockState,
        trailingNewline: Boolean
    ) {
        val pending = StringBuilder()
        suspend fun flushPending() {
            if (pending.isNotEmpty()) { +pending.toString(); pending.clear() }
        }
        val tokens = tokenizeHtmlLine(line)
        for (tok in tokens) {
            when (tok) {
                is HtmlToken.Text -> pending.append(tok.content)
                is HtmlToken.OpenTag -> {
                    flushPending()
                    markHtml(tok.name, tok.attributes)
                    if (tok.isSelfClosingOrVoid()) unmarkHtml(tok.name)
                    else state.openTags += normalizeHtmlName(tok.name)
                }
                is HtmlToken.CloseTag -> {
                    flushPending()
                    emitMatchingClose(tok.name, state.openTags)
                }
            }
        }
        if (trailingNewline) pending.append('\n')
        if (pending.isNotEmpty()) +pending.toString()
    }

    /**
     * Force-close any open list-internal HTML block. Drains [openTags] (in
     * LIFO) and the still-buffered first-line opener (emitted as raw text so
     * an unclosed `<pre` doesn't disappear). Used when the active item closes
     * before the block's natural close tag / closing sequence arrives.
     */
    private suspend fun SemanticEventScope.closeListHtmlBlockIfOpen(
        mode: BlockMode.ListBlock
    ) {
        val top = mode.stack.lastOrNull() ?: return
        val state = top.htmlBlock ?: return
        if (state.isDoctype) {
            unmark("doctype", isTagged = true)
            top.htmlBlock = null
            return
        }
        val buf = state.firstLineBuffer
        if (buf != null && buf.isNotEmpty()) +"$buf\n"
        while (state.openTags.isNotEmpty()) unmarkHtml(state.openTags.removeLast())
        top.htmlBlock = null
    }

    /**
     * Open a DOCTYPE block inside the active list item. Mirrors the top-level
     * [enterDoctypeBlock] but stores in-flight state on [ctx.htmlBlock] (as a
     * [ListHtmlBlockState] with [ListHtmlBlockState.isDoctype] = true) instead
     * of replacing the block mode.
     */
    private suspend fun SemanticEventScope.enterListDoctypeBlock(
        ctx: ListContext,
        line: String
    ) {
        val trimmed = line.trimStart(' ')
        val afterPrefix = trimmed.substring(DOCTYPE_PREFIX_LENGTH)
        mark("doctype", isTagged = true)
        val gtIndex = afterPrefix.indexOf('>')
        if (gtIndex >= 0) {
            val content = afterPrefix.substring(0, gtIndex).trimStart(' ', '\t')
            if (content.isNotEmpty()) +content
            unmark("doctype", isTagged = true)
            val trailing = afterPrefix.substring(gtIndex + 1)
            if (trailing.isNotEmpty()) +"$trailing\n"
        } else {
            val content = afterPrefix.trimStart(' ', '\t')
            if (content.isNotEmpty()) +content
            ctx.htmlBlock = ListHtmlBlockState(type = 4, isDoctype = true)
        }
    }

    /**
     * Stream a single content line of an open list-internal DOCTYPE block.
     * Mirrors [processDoctypeBlock]'s `\n` branch but clears [ctx.htmlBlock]
     * (rather than replacing the block mode) when the closing `>` arrives.
     * Continuation lines are prefixed with `\n` so consecutive lines join
     * verbatim — same rationale as [processDoctypeBlock].
     */
    private suspend fun SemanticEventScope.streamListDoctypeLine(
        ctx: ListContext,
        line: String
    ) {
        val gtIndex = line.indexOf('>')
        if (gtIndex < 0) {
            +"\n$line"
            return
        }
        val before = line.substring(0, gtIndex)
        if (before.isNotEmpty()) +"\n$before"
        unmark("doctype", isTagged = true)
        val trailing = line.substring(gtIndex + 1)
        if (trailing.isNotEmpty()) +"$trailing\n"
        ctx.htmlBlock = null
    }

    /**
     * Try to open a custom markup block (`<ns:name …>`) inside the active
     * list item using [line] (already indent-stripped). Returns true and emits
     * `mark(tagName, isTagged = true)` on success. Mirrors the top-level
     * detection in `processStart` (line-shape `<…>` not starting with `</`),
     * but stores the open-tag name on [ctx] instead of pushing a frame.
     */
    private suspend fun SemanticEventScope.tryEnterListCustomMarkup(
        ctx: ListContext,
        line: String
    ): Boolean {
        if (!line.startsWith("<") || line.startsWith("</") || !line.endsWith(">")) {
            return false
        }
        val parsed = parseCustomMarkupOpeningTag(line) ?: return false
        val (tagName, attributes) = parsed
        mark(tagName, isTagged = true, attributes = attributes ?: emptyMap())
        ctx.customMarkupTagName = tagName
        ctx.customMarkupHasContent = false
        ctx.hasContent = true
        return true
    }

    /**
     * Stream a single content line of an open list-internal custom markup
     * block. A line equal to `</tagName>` (after `trimEnd`) closes the block
     * and emits `unmark`. Otherwise the line is emitted as text content,
     * joined to prior content lines by `\n` (no leading `\n` before the very
     * first content line — matches the top-level `customMarkupSkipFirstNewline`
     * behaviour).
     *
     * A blank line (`line == ""`) flips [ctx.customMarkupHasContent] to true
     * so the next content line gets a leading `\n`, and the block stays open
     * — same DIVERGENCE rationale as `streamListHtmlBlockLine`.
     */
    private suspend fun SemanticEventScope.streamListCustomMarkupLine(
        ctx: ListContext,
        line: String
    ) {
        val tagName = ctx.customMarkupTagName ?: return
        if (line.trimEnd() == "</$tagName>") {
            unmark(tagName, isTagged = true)
            ctx.customMarkupTagName = null
            ctx.customMarkupHasContent = false
            return
        }
        if (ctx.customMarkupHasContent) {
            if (line.isEmpty()) +"\n"
            else +"\n$line"
        } else {
            if (line.isNotEmpty()) +line
            ctx.customMarkupHasContent = true
        }
    }

    /**
     * Force-close any open list-internal custom markup block. Used when the
     * active item closes before the matching `</tagName>` line arrives.
     */
    private suspend fun SemanticEventScope.closeListCustomMarkupIfOpen(
        mode: BlockMode.ListBlock
    ) {
        val top = mode.stack.lastOrNull() ?: return
        val tagName = top.customMarkupTagName ?: return
        unmark(tagName, isTagged = true)
        top.customMarkupTagName = null
        top.customMarkupHasContent = false
    }

    /**
     * Pop list contexts of [mode] until the stack size equals [downTo], emitting
     * `</li>` and `</ul>`/`</ol>` for each popped level. Closes any open `<p>` or
     * code block on the topmost context as part of closing the active item.
     */
    private suspend fun SemanticEventScope.popListContexts(
        mode: BlockMode.ListBlock,
        downTo: Int
    ) {
        while (mode.stack.size > downTo) {
            // Drain any unresolved table header before closing — it must surface
            // as paragraph text rather than disappear with the popped context.
            drainListPendingTableHeader(mode)
            closeListTableIfOpen(mode)
            closeListHtmlBlockIfOpen(mode)
            closeListCustomMarkupIfOpen(mode)
            closeListParagraphIfOpen(mode)
            closeListCodeIfOpen(mode)
            closeListFencedCodeIfOpen(mode)
            closeListMathBlockIfOpen(mode)
            closeListBlockquoteIfOpen(mode)
            unmark("li")
            val ctx = mode.stack.removeLast()
            unmark(if (ctx.ordered) "ol" else "ul")
        }
    }

    private suspend fun SemanticEventScope.processListBlock(
        char: Char,
        mode: BlockMode.ListBlock
    ) {
        if (char != '\n') {
            lineBuffer.append(char)
            return
        }
        val line = lineBuffer.toString()
        lineBuffer.clear()

        // Resolve any pending table header from the previous line first. On
        // commit, the table is emitted in-place. On abort, the header drains
        // as paragraph text and the current line replays through this same
        // mode — `tableHeaderPending` is null at that point so no recursion.
        val pendingCtx = mode.stack.lastOrNull()
        if (pendingCtx?.tableHeaderPending != null) {
            if (tryCommitListTable(mode, pendingCtx, line)) return
            drainListPendingTableHeader(mode)
            replay(line)
            process('\n')
            return
        }

        // Blank line: close any open paragraph (lazy code-block lines only emit on
        // resumption). Mark the list as having seen a blank — used to decide whether
        // a subsequent indented continuation opens a new paragraph.
        if (line.isBlank()) {
            // A blank line inside an open HTML block streams as content (the block
            // stays open) and does not flip `blankSeen`, so the following non-blank
            // line still routes into the HTML content path. Mirrors the top-level
            // type-6/7 raw-text behaviour minus the sub-parse transition.
            if (pendingCtx?.htmlBlock != null) {
                streamListHtmlBlockLine(pendingCtx, "")
                return
            }
            // Same rationale as the htmlBlock branch above: blank lines inside
            // an open custom markup block are content (a `\n` separator between
            // content lines), not block boundaries — the block stays open until
            // the matching `</tagName>` line.
            if (pendingCtx?.customMarkupTagName != null) {
                streamListCustomMarkupLine(pendingCtx, "")
                return
            }
            // A blank line inside an open table closes it (mirrors the top-level
            // TableBody behaviour where `line.isEmpty()` ends the table).
            if (pendingCtx?.tableOpen == true) {
                closeListTableIfOpen(mode)
            }
            closeListParagraphIfOpen(mode)
            val top = mode.stack.lastOrNull()
            // Defer blank-line emission for an open indented code block until more
            // code arrives (it may turn out to be the trailing blanks before the
            // block closes, which are dropped). Fenced code preserves every blank
            // line literally — emit straight away (GFM §5.4 example 298).
            if (top != null && top.codeBlockOpen) top.codeBlankLines++
            else if (top != null && top.fencedCodeOpen) +"\n"
            else if (top != null && top.mathBlockOpen) +"\n"
            mode.blankSeen = true
            return
        }

        val indent = leadingIndentCols(line)

        // HTML block continuation takes precedence over marker / thematic-break
        // detection on lines that satisfy the active item's indent: inside an
        // HTML block, content like `- foo` or `---` must stream as raw text
        // rather than open a sibling list / close the list. An under-indented
        // line force-closes the HTML block and falls through to the normal
        // handling (mirrors how an under-indented line ends a fenced block).
        val activeCtx = mode.stack.lastOrNull()
        if (activeCtx?.htmlBlock != null) {
            if (indent >= activeCtx.contentCol) {
                val htmlStripped = stripIndentCols(line, activeCtx.contentCol, startCol = 0)
                streamListHtmlBlockLine(activeCtx, htmlStripped)
                mode.blankSeen = false
                return
            }
            closeListHtmlBlockIfOpen(mode)
        }

        // Custom markup continuation: same precedence rationale as the htmlBlock
        // branch above. A line satisfying the item's indent streams as content
        // (or closes the block when it equals `</tagName>`); an under-indented
        // line force-closes the block and falls through to normal handling.
        if (activeCtx?.customMarkupTagName != null) {
            if (indent >= activeCtx.contentCol) {
                val cmStripped = stripIndentCols(line, activeCtx.contentCol, startCol = 0)
                streamListCustomMarkupLine(activeCtx, cmStripped)
                mode.blankSeen = false
                return
            }
            closeListCustomMarkupIfOpen(mode)
        }

        // Find the deepest context whose contentCol is satisfied by this line's indent.
        // If `indent < stack[0].contentCol` AND the line is not itself a marker at a
        // sibling/outer position, the list ends.
        val topMostMatching = (mode.stack.size - 1 downTo 0).firstOrNull { i ->
            indent >= mode.stack[i].contentCol
        }

        // Try the line (after stripping the deepest container's contentCol) as a marker.
        val markerCtxIndex = topMostMatching ?: -1
        val containerContentCol = if (markerCtxIndex >= 0) mode.stack[markerCtxIndex].contentCol else 0
        val stripped = if (markerCtxIndex >= 0)
            stripIndentCols(line, containerContentCol, startCol = 0)
        else line
        // Thematic break takes precedence over list-item markers (CommonMark §4.1):
        // a line like `* * *` is a thematic break, not a list with `* *` content.
        // Inside a list block this ends the current list — pop all contexts and
        // replay the line through Start so the `<hr />` is emitted at top level.
        if (stripped matches Patterns.THEMATIC_BREAK) {
            popListContexts(mode, downTo = 0)
            replaceMode(BlockMode.Start)
            replay(line)
            process('\n')
            return
        }

        val strippedMarker = parseListMarker(stripped)

        if (strippedMarker != null) {
            // Absolute marker column inside the source line.
            val absMarkerStart = containerContentCol + strippedMarker.markerStartCol

            // Decide which existing context (if any) this marker is a sibling of.
            // A marker is a sibling of context i when its absMarkerStart sits in
            // [stack[i].markerStartCol, stack[i].contentCol). Markers at or beyond the
            // top context's contentCol open a new nested list.
            val siblingIndex = mode.stack.indexOfLast { ctx ->
                absMarkerStart >= ctx.markerStartCol && absMarkerStart < ctx.contentCol
            }

            if (siblingIndex >= 0) {
                // Close everything below the sibling, including its current item.
                popListContexts(mode, downTo = siblingIndex + 1)
                val ctx = mode.stack[siblingIndex]
                // GFM §5.4: a list ends when the next marker uses a different
                // marker style. For unordered, that means a different bullet char
                // (`-` vs `+` vs `*`); for ordered, a different delimiter
                // (`.` vs `)`). Either change closes this list and opens a new
                // sibling list at the same indent.
                if (ctx.ordered != strippedMarker.ordered
                    || ctx.markerChar != strippedMarker.markerChar) {
                    popListContexts(mode, downTo = siblingIndex)
                    startListItemFromMarker(mode, stripped, parentContentCol = containerContentCol)
                } else {
                    closeListParagraphIfOpen(mode)
                    closeListCodeIfOpen(mode)
                    closeListFencedCodeIfOpen(mode)
                    closeListMathBlockIfOpen(mode)
                    closeListBlockquoteIfOpen(mode)
                    closeListHtmlBlockIfOpen(mode)
                    closeListCustomMarkupIfOpen(mode)
                    unmark("li")
                    mark("li")
                    ctx.hasContent = false
                    // Track the leftmost marker col seen so far. The 0..3-leading-
                    // space progression case (GFM §5.2 example 273) requires the
                    // sibling range to expand outward only — never narrow — or
                    // example 290 (`- a\n - b\n  - c\n   - d\n  - e\n - f\n- g`)
                    // would see the back-and-forth indents create spurious
                    // nested lists once the marker column dropped again.
                    if (absMarkerStart < ctx.markerStartCol) {
                        ctx.markerStartCol = absMarkerStart
                    }
                    ctx.contentCol = containerContentCol + strippedMarker.contentCol
                    val firstContent = stripped.substring(strippedMarker.markerEndIndex)
                    if (firstContent.isNotBlank()) {
                        emitItemFirstLine(firstContent, ctx)
                    }
                }
                mode.blankSeen = false
                return
            }

            // Otherwise: this is a deeper marker — push a new nested list level.
            // First close any open paragraph/code in the parent (we're entering block
            // content inside the parent item).
            closeListParagraphIfOpen(mode)
            closeListCodeIfOpen(mode)
            closeListFencedCodeIfOpen(mode)
            closeListMathBlockIfOpen(mode)
            closeListBlockquoteIfOpen(mode)
            closeListHtmlBlockIfOpen(mode)
            closeListCustomMarkupIfOpen(mode)
            startListItemFromMarker(mode, stripped, parentContentCol = containerContentCol)
            return
        }

        // Not a marker. If no context contains this line at all, the list ends —
        // unless this line is lazy-continuation content for an open paragraph in
        // the deepest item (GFM §5.2 examples 268, 269: an under-indented,
        // non-blank, non-interrupting line joins the open paragraph via soft-break).
        if (markerCtxIndex < 0) {
            val top = mode.stack.lastOrNull()
            // Use the un-trimmed line for the interrupter check: a `- ` at col 4+
            // is NOT a valid list marker (markers allow 0..3 leading spaces) and
            // therefore does not interrupt the paragraph (GFM §5.4 example 292
            // — `    - e` lazy-continues the open paragraph instead of opening
            // a new sibling item).
            if (top != null && top.paragraphOpen && !lineInterruptsParagraph(line)) {
                +"\n"
                processInlineContent(line.trimStart().trimEnd())
                flushInline()
                mode.blankSeen = false
                return
            }
            // Lazy continuation of an in-item blockquote's `<p>`: a non-indented
            // non-interrupting line joins the open inner-blockquote paragraph
            // via soft-break (mirrors the paragraphOpen branch above for the
            // inner-blockquote case used by GFM §5.2 examples 270, 271).
            if (top != null && top.blockquoteOpen && top.blockquoteParagraphOpen
                && !lineInterruptsParagraph(line)) {
                +"\n"
                processInlineContent(line.trimStart().trimEnd())
                flushInline()
                mode.blankSeen = false
                return
            }
            popListContexts(mode, downTo = 0)
            replaceMode(BlockMode.Start)
            replay(line)
            process('\n')
            return
        }

        // The line is a continuation inside `mode.stack[markerCtxIndex]`. Pop deeper
        // contexts (their items end where the indent dropped below their contentCol).
        popListContexts(mode, downTo = markerCtxIndex + 1)
        val ctx = mode.stack[markerCtxIndex]

        // Empty item followed by a blank line ends the list (GFM §5.2 example 258).
        // The current line is replayed through Start so its block type is decided
        // afresh at top level.
        if (mode.blankSeen && !ctx.hasContent) {
            popListContexts(mode, downTo = 0)
            replaceMode(BlockMode.Start)
            replay(line)
            process('\n')
            return
        }

        // `stripped` is the line with the container's contentCol of leading indent
        // removed; semantically it begins at absolute column [containerContentCol].
        val innerIndent = leadingIndentCols(stripped, startCol = containerContentCol)

        // Fenced code continuation: route every line through the fenced block
        // until we see a matching close fence.
        if (ctx.fencedCodeOpen) {
            if (isFenceClose(stripped, ctx.fencedMarker, ctx.fencedLength)) {
                closeListFencedCodeIfOpen(mode)
                mode.blankSeen = false
                return
            }
            +"$stripped\n"
            mode.blankSeen = false
            return
        }

        // Math block continuation: route every line through until the closing `$$`.
        if (ctx.mathBlockOpen) {
            if (stripped.trim() == "$$") {
                closeListMathBlockIfOpen(mode)
                mode.blankSeen = false
                return
            }
            +"$stripped\n"
            mode.blankSeen = false
            return
        }

        // Indented code block start/continuation: only if a blank line preceded (item
        // is between blocks) or a code block is already open for this context.
        if (ctx.codeBlockOpen) {
            if (innerIndent >= 4) {
                // Continuation: flush deferred blank lines, then the stripped content.
                repeat(ctx.codeBlankLines) { +"\n" }
                ctx.codeBlankLines = 0
                +"${stripIndentCols(stripped, 4, startCol = containerContentCol)}\n"
                return
            }
            // Code block ends — close it (deferred blanks are dropped, mirroring
            // top-level processIndentedCodeBlock) and fall through.
            closeListCodeIfOpen(mode)
        }

        // Table body continuation. Mirrors `processTableBody`: a `|`-prefixed
        // line is always a row, a non-`|` line is a row only when it doesn't
        // interrupt a paragraph (heading, blockquote, fence, hr, html block —
        // those break the table and fall through).
        if (ctx.tableOpen) {
            val rowStripped = stripped.trimEnd()
            val endsTable = !rowStripped.startsWith("|") && lineInterruptsParagraph(rowStripped)
            if (!endsTable) {
                emitListTableBodyRow(ctx, rowStripped)
                ctx.hasContent = true
                mode.blankSeen = false
                return
            }
            closeListTableIfOpen(mode)
        }

        if (innerIndent >= 4 && (mode.blankSeen || !ctx.paragraphOpen)) {
            // Open a new indented code block within the current item.
            closeListParagraphIfOpen(mode)
            closeListBlockquoteIfOpen(mode)
            mark("pre")
            mark("code")
            ctx.codeBlockOpen = true
            ctx.hasContent = true
            +"${stripIndentCols(stripped, 4, startCol = containerContentCol)}\n"
            mode.blankSeen = false
            return
        }

        // At a block boundary inside the active item, detect block-level constructs
        // (fenced code, ATX heading, blockquote) before falling through to paragraph.
        // Applies when `mode.blankSeen` (between sub-blocks), no paragraph is
        // currently open, OR the line is a paragraph-interrupter (`>`, fence,
        // ATX, etc. — GFM §5.4 examples 300, 301): such lines must close any
        // open paragraph or inner blockquote and let the new block take over.
        val atBlockBoundary = mode.blankSeen
            || (!ctx.paragraphOpen && !ctx.blockquoteOpen)
            || ((ctx.paragraphOpen || ctx.blockquoteParagraphOpen)
                && lineInterruptsParagraph(stripped.trimStart()))
        if (atBlockBoundary) {
            val fence = parseFenceOpen(stripped)
            if (fence != null) {
                closeListParagraphIfOpen(mode)
                closeListBlockquoteIfOpen(mode)
                mark("pre")
                val codeAttrs = fence.language?.let { mapOf("class" to "language-$it") } ?: emptyMap()
                mark("code", attributes = codeAttrs)
                ctx.fencedCodeOpen = true
                ctx.fencedMarker = fence.marker
                ctx.fencedLength = fence.length
                ctx.hasContent = true
                mode.blankSeen = false
                return
            }
            if (stripped matches Patterns.ATX_HEADING_LINE) {
                closeListParagraphIfOpen(mode)
                closeListBlockquoteIfOpen(mode)
                val match = Patterns.ATX_HEADING_LINE.matchEntire(stripped)!!
                val level = match.groupValues[1].length
                val content = match.groupValues[2].trimEnd().removeSuffix("#").trimEnd()
                "h$level" {
                    if (content.isNotEmpty()) processInlineContent(content)
                    flushInline()
                }
                ctx.hasContent = true
                mode.blankSeen = false
                return
            }
            if (stripped.trim() == "$$") {
                closeListParagraphIfOpen(mode)
                closeListBlockquoteIfOpen(mode)
                mark("math", attributes = mapOf("display" to "block"))
                ctx.mathBlockOpen = true
                ctx.hasContent = true
                mode.blankSeen = false
                return
            }
            // Table header (provisional, GFM §4.10 nested inside §5.2). Don't
            // emit yet — buffer the stripped line and let the next call resolve
            // it via `tryCommitListTable`. Identical contract to `processStart`'s
            // top-level `BlockMode.TableHeaderPending`, except the buffered
            // header lives on `ListContext` so the existing list dispatch loop
            // remains the top-of-stack handler.
            if (stripped.startsWith("|") && !suppressTableDetection) {
                closeListParagraphIfOpen(mode)
                closeListBlockquoteIfOpen(mode)
                ctx.tableHeaderPending = stripped
                mode.blankSeen = false
                return
            }
            // HTML block (CommonMark §4.6) at an item block boundary. Same
            // architectural pattern as the table-header branch above: state
            // lives on `ListContext` rather than pushing a frame onto
            // `blockModeStack` (see `ListContext.htmlBlock`). Closes mirror
            // the table-detection preamble for defensive consistency, even
            // though fenced code / math should already be closed before
            // reaching `atBlockBoundary` via the blank-line handler.
            if (detectHtmlBlockType(stripped) > 0) {
                closeListParagraphIfOpen(mode)
                closeListFencedCodeIfOpen(mode)
                closeListMathBlockIfOpen(mode)
                closeListBlockquoteIfOpen(mode)
                tryEnterListHtmlBlock(ctx, stripped)
                mode.blankSeen = false
                return
            }
            // Custom markup opening tag (`<ns:name …>`) at an item block
            // boundary. State lives on `ListContext.customMarkupTagName`,
            // following the same pattern as table-header / htmlBlock above —
            // pushing a `BlockMode.CustomMarkup` frame would knock `ListBlock`
            // off the top of the dispatch stack and break per-line
            // container-indent stripping. Subsequent indented lines stream
            // through the `customMarkupTagName != null` branch above.
            if (stripped.startsWith("<") && !stripped.startsWith("</")
                && stripped.endsWith(">")) {
                val parsed = parseCustomMarkupOpeningTag(stripped)
                if (parsed != null) {
                    closeListParagraphIfOpen(mode)
                    closeListFencedCodeIfOpen(mode)
                    closeListMathBlockIfOpen(mode)
                    closeListBlockquoteIfOpen(mode)
                    val (tagName, attributes) = parsed
                    mark(tagName, isTagged = true, attributes = attributes ?: emptyMap())
                    ctx.customMarkupTagName = tagName
                    ctx.customMarkupHasContent = false
                    ctx.hasContent = true
                    mode.blankSeen = false
                    return
                }
            }
            // Link reference definition (CommonMark §4.7) at an item block
            // boundary. Mirrors the top-level detection in `processStart`:
            // single-line shape `[label]: dest "title"` is consumed silently
            // (registered in `linkDefinitions`, no events emitted). Same
            // streaming divergences as the top-level path — multi-line shapes
            // and forward references are not supported.
            if (tryParseLinkDefinition(stripped)) {
                mode.blankSeen = false
                return
            }
        }

        // Blockquote inside list item. A `>`-prefixed line at a block boundary opens
        // the blockquote (if not already open) and emits its content as a paragraph;
        // consecutive `>`-prefixed lines join the same paragraph via soft-break.
        // Only single-line lazy continuation is supported.
        if (atBlockBoundary || ctx.blockquoteOpen) {
            if (isBlockquoteOpener(stripped)) {
                closeListParagraphIfOpen(mode)
                if (!ctx.blockquoteOpen) {
                    mark("blockquote")
                    ctx.blockquoteOpen = true
                }
                val bqContent = stripBlockquotePrefix(stripped)
                // Empty `>` line: end the blockquote's open paragraph (GFM §5.4
                // example 300) — the next blockquote line starts a fresh `<p>`.
                if (bqContent.isBlank() && ctx.blockquoteParagraphOpen) {
                    flushInline()
                    endParagraph()
                    ctx.blockquoteParagraphOpen = false
                    ctx.hasContent = true
                    mode.blankSeen = false
                    return
                }
                if (!ctx.blockquoteParagraphOpen) {
                    mark("p")
                    ctx.blockquoteParagraphOpen = true
                } else {
                    +"\n"
                }
                if (bqContent.isNotBlank()) {
                    processInlineContent(bqContent.trimEnd())
                }
                flushInline()
                ctx.hasContent = true
                mode.blankSeen = false
                return
            }
            // Non-`>` line at a block boundary closes the blockquote.
            if (ctx.blockquoteOpen && atBlockBoundary) {
                closeListBlockquoteIfOpen(mode)
            }
        }

        // Plain continuation paragraph content.
        if (mode.blankSeen) {
            // Blank line before — start a new paragraph.
            closeListParagraphIfOpen(mode)
        }
        if (!ctx.paragraphOpen) {
            mark("p")
            ctx.paragraphOpen = true
        } else {
            // Soft-break between joined lines of the same paragraph.
            +"\n"
        }
        processInlineContent(stripped.trimStart().trimEnd())
        flushInline()
        ctx.hasContent = true
        mode.blankSeen = false
    }

    /**
     * Emit a single-line `<blockquote>` or `<ul><li>` whose only content is an
     * indented code block, when [line] uses tabs in places where the incremental
     * detector expects spaces. Returns true when something was emitted.
     */
    private suspend fun SemanticEventScope.tryEmitSingleLineContainerWithCode(
        line: String
    ): Boolean {
        // Blockquote: `>` followed by a single space or tab.
        if (line.startsWith(">") && line.length > 1 && (line[1] == ' ' || line[1] == '\t')) {
            val content = stripIndentCols(line.substring(1), 1, startCol = 1)
            if (leadingIndentCols(content, startCol = 2) >= 4) {
                val codeContent = stripIndentCols(content, 4, startCol = 2)
                "blockquote" {
                    "pre" {
                        "code" {
                            +"$codeContent\n"
                        }
                    }
                }
                return true
            }
        }
        // Bullet list: `-`, `+`, or `*` followed by a single space or tab.
        if (line.length > 1 && line[0] in "-+*" && (line[1] == ' ' || line[1] == '\t')) {
            val content = stripIndentCols(line.substring(1), 1, startCol = 1)
            if (leadingIndentCols(content, startCol = 2) >= 4) {
                val codeContent = stripIndentCols(content, 4, startCol = 2)
                "ul" {
                    "li" {
                        "pre" {
                            "code" {
                                +"$codeContent\n"
                            }
                        }
                    }
                }
                return true
            }
        }
        return false
    }

    private suspend fun SemanticEventScope.processIndentedCodeBlock(
        char: Char
    ) {
        if (char != '\n') {
            lineBuffer.append(char)
            return
        }
        val line = lineBuffer.toString()
        lineBuffer.clear()
        if (line.isBlank()) {
            // Defer blank lines: only emit them if more code-block content follows.
            // Capture the stripped content so trailing whitespace past the 4-space
            // prefix (e.g. `      \n` → `  \n`) is preserved on flush.
            indentedCodeDeferredBlanks += stripIndentCols(line, 4)
            return
        }
        if (leadingIndentCols(line) >= 4) {
            // Flush any pending blank lines, then emit this content line.
            indentedCodeDeferredBlanks.forEach { +"$it\n" }
            indentedCodeDeferredBlanks.clear()
            +"${stripIndentCols(line, 4)}\n"
            return
        }
        // End of code block: trailing blanks are dropped.
        indentedCodeDeferredBlanks.clear()
        unmark("code")
        unmark("pre")
        replaceMode(BlockMode.Start)
        // Replay this line through Start so it can be parsed as its own block.
        replay(line)
        process('\n')
    }

    private suspend fun SemanticEventScope.processUnorderedList(
        char: Char
    ) {

        if (char == '\n') {
            if (inListItem) {
                flushInline()
                unmark("li")
                inListItem = false
            }
            lineBuffer.clear()
            return
        }

        if (!inListItem) {
            lineBuffer.append(char)
            val line = lineBuffer.toString()
            when {
                // Task list: - [ ] or - [x]  (check BEFORE simple list item)
                line matches Patterns.TASK_UNCHECKED -> {
                    mark("li")
                    "input"("type" to "checkbox") {}
                    unmark("input")
                    lineBuffer.clear()
                    inListItem = true
                }
                line matches Patterns.TASK_CHECKED -> {
                    mark("li")
                    "input"("type" to "checkbox", "checked" to "true") {}
                    lineBuffer.clear()
                    inListItem = true
                }
                // Keep buffering for potential task list
                line matches Patterns.TASK_PARTIAL || line == "- [" || line == "- " || line == "-" -> {
                    // Keep buffering
                }
                // Regular list item: "- X" where X is not [
                line.startsWith("- ") && line.length > 2 && line[2] != '[' -> {
                    mark("li")
                    // Emit the content after "- "
                    processInlineContent(line.substring(2))
                    lineBuffer.clear()
                    inListItem = true
                }
                line.isEmpty() -> {
                    // End of list
                    unmark("ul")
                    replaceMode(BlockMode.Start)
                }
                line.startsWith("#") -> {
                    // Header after list
                    unmark("ul")
                    lineBuffer.clear()
                    replaceMode(BlockMode.Start)
                    replay(line)
                }
                else -> {
                    // End of list, start new block
                    unmark("ul")
                    lineBuffer.clear()
                    replaceMode(BlockMode.Start)
                    replay(line)
                }
            }
        } else {
            processInlineChar(char)
        }
    }

    private suspend fun SemanticEventScope.processOrderedList(
        char: Char
    ) {
        if (char == '\n') {
            if (inListItem) {
                flushInline()
                unmark("li")
                inListItem = false
            }
            lineBuffer.clear()
            return
        }

        if (!inListItem) {
            lineBuffer.append(char)
            val line = lineBuffer.toString()
            when {
                line matches Patterns.ORDERED_LIST_ITEM -> {
                    mark("li")
                    lineBuffer.clear()
                    inListItem = true
                }
                line matches Patterns.ORDERED_LIST_PARTIAL -> {
                    // Keep buffering
                }
                line.isEmpty() -> {
                    unmark("ol")
                    replaceMode(BlockMode.Start)
                }
                line.startsWith("#") -> {
                    unmark("ol")
                    lineBuffer.clear()
                    replaceMode(BlockMode.Start)
                    replay(line)
                }
                else -> {
                    unmark("ol")
                    lineBuffer.clear()
                    replaceMode(BlockMode.Start)
                    replay(line)
                }
            }
        } else {
            processInlineChar(char)
        }
    }

    /** Number of `Blockquote` frames currently on `blockModeStack`. */
    private fun blockquoteFrameCount(): Int =
        blockModeStack.count { it is BlockMode.Blockquote }

    /** True if any enclosing frame is a `Blockquote`. */
    private fun isInsideBlockquote(): Boolean =
        blockModeStack.any { it is BlockMode.Blockquote }

    /**
     * True iff [line] (already stripped of any outer container prefix) matches
     * a blockquote opener: 0..3 leading spaces, then `>`. The `>` may be the
     * last char or be followed by anything (CommonMark allows the optional
     * single space after `>`, but also accepts `>X` where X is content).
     */
    private fun isBlockquoteOpener(line: String): Boolean {
        var i = 0
        while (i < line.length && i < 3 && line[i] == ' ') i++
        return i < line.length && line[i] == '>'
    }

    /**
     * Strip a single blockquote prefix (`0..3 spaces` + `>` + optional one space)
     * from [line]. Used by the list-item dispatcher's blockquote handler to
     * recover the content portion of a `>`-prefixed line.
     */
    private fun stripBlockquotePrefix(line: String): String {
        var i = 0
        while (i < line.length && i < 3 && line[i] == ' ') i++
        if (i >= line.length || line[i] != '>') return line
        i++
        if (i < line.length && line[i] == ' ') i++
        return line.substring(i)
    }

    /**
     * Blockquote prefix interceptor. Called from `process()` before the normal
     * dispatch. Returns true when the char was handled here (consumed as part
     * of a `>` prefix or buffered as the start of a failed-prefix line) and
     * the caller should skip normal dispatch.
     *
     * State machine — for each enclosing `Blockquote` frame the line must
     * supply (in order):
     *   - 0..3 leading spaces
     *   - one `>`
     *   - optionally one space (or tab acting as a single space)
     *
     * Once all levels are matched, [blockquotePrefixDone] flips to true and
     * subsequent chars on this line dispatch normally to the inner sub-parser.
     */
    private suspend fun SemanticEventScope.interceptBlockquoteLine(char: Char): Boolean {
        // Already buffering a failed-prefix line: keep buffering until `\n`,
        // then classify (blank / lazy continuation / close-and-replay).
        if (blockquoteInFailedLine) {
            if (char != '\n') {
                blockquoteFailedLineBuffer.append(char)
                return true
            }
            val line = blockquoteFailedLineBuffer.toString()
            blockquoteFailedLineBuffer.clear()
            blockquoteInFailedLine = false
            dispatchFailedBlockquoteLine(line)
            return true
        }

        val depth = blockquoteFrameCount()
        if (depth == 0 || blockquotePrefixDone) return false

        if (char == '\n') {
            // `\n` while still expecting prefix:
            //   - prefixCount == 0 and not just-after-`>`: a blank line at the
            //     outer level closes the blockquote.
            //   - otherwise the line carried at least one `>` (possibly empty
            //     content) — dispatch the `\n` through the inner sub-parser.
            if (blockquotePrefixCount == 0 && !blockquoteJustAfterGt) {
                closeAllBlockquotes()
            }
            blockquotePrefixDone = true
            return false
        }

        if (tryConsumeBlockquotePrefixChar(char, depth)) {
            if (blockquotePrefixCount >= depth) blockquotePrefixDone = true
            return true
        }

        // `tryConsume` may have closed the last open level as a side effect
        // (e.g. `>X` where X is the first content char with no optional space).
        // If all levels are now matched, treat the char as content and dispatch
        // normally instead of buffering it as a failed-prefix line.
        if (blockquotePrefixCount >= depth) {
            blockquotePrefixDone = true
            return false
        }

        // Prefix consumption failed: buffer the rest of the line for `\n`-time
        // classification.
        blockquoteInFailedLine = true
        blockquoteFailedLineBuffer.append(char)
        return true
    }

    /**
     * Try to consume one prefix char. Returns true if [char] advanced the
     * prefix state machine; false signals "no fit, the line lacks the
     * remaining `>` prefix(es)".
     */
    private fun tryConsumeBlockquotePrefixChar(char: Char, depth: Int): Boolean {
        when {
            char == '>' -> {
                if (blockquoteJustAfterGt) {
                    // Previous level had no following space (e.g. `>>`); count it now.
                    blockquotePrefixCount++
                    if (blockquotePrefixCount >= depth) {
                        // Prefix already complete — this `>` is content (a nested
                        // blockquote opener). Don't consume.
                        return false
                    }
                }
                blockquotePrefixIndent = 0
                blockquoteJustAfterGt = true
                return true
            }
            (char == ' ' || char == '\t') && blockquoteJustAfterGt -> {
                // Optional single space (or tab) after `>`. Closes this level.
                blockquoteJustAfterGt = false
                blockquotePrefixCount++
                return true
            }
            char == ' ' && blockquotePrefixIndent < 3 -> {
                blockquotePrefixIndent++
                return true
            }
            blockquoteJustAfterGt -> {
                // Non-space, non-`>` after `>` (no optional space). Close this
                // level; if more levels are still expected, the caller will
                // see prefixCount < depth and fail.
                blockquoteJustAfterGt = false
                blockquotePrefixCount++
                if (blockquotePrefixCount >= depth) {
                    // All levels matched — caller should treat as "consumed"
                    // and dispatch this char normally on next call. We signal
                    // success by returning true *without* advancing state, but
                    // the prefixDone check in the caller will fire because
                    // prefixCount is now >= depth.
                    // Re-classify the char: it's content. Don't consume here.
                    return false
                }
                return false
            }
            else -> return false
        }
    }

    /**
     * Classify a buffered failed-prefix line and act:
     * - Empty line → close all blockquote frames.
     * - Inner top is `ParagraphContinuation` and the line wouldn't interrupt
     *   a paragraph → lazy continuation: replay through the inner mode.
     * - Otherwise → close all blockquotes and replay through the outer Start.
     */
    private suspend fun SemanticEventScope.dispatchFailedBlockquoteLine(line: String) {
        if (line.isEmpty()) {
            closeAllBlockquotes()
            return
        }
        val innerIsParagraphContinuation = blockMode is BlockMode.ParagraphContinuation
        if (innerIsParagraphContinuation && !lineInterruptsParagraph(line)) {
            // Lazy continuation. Skip prefix detection during the replay.
            blockquotePrefixDone = true
            replay(line)
            process('\n')
            return
        }
        // Lazy continuation through a list-item's inner blockquote: when the
        // deepest list context has an open `<blockquote><p>`, replay the line
        // through the list-block dispatcher so it joins that paragraph via
        // soft-break instead of closing the outer blockquote (GFM §5.2 ex. 270).
        val listMode = blockMode as? BlockMode.ListBlock
        val listTop = listMode?.stack?.lastOrNull()
        if (listTop != null && listTop.blockquoteOpen && listTop.blockquoteParagraphOpen
            && !lineInterruptsParagraph(line)) {
            blockquotePrefixDone = true
            replay(line)
            process('\n')
            return
        }
        closeAllBlockquotes()
        replay(line)
        process('\n')
    }

    /**
     * Drain frames above the bottommost `Blockquote`, then pop the blockquote
     * frame(s) themselves, leaving the outer `Start` on top. Resets prefix
     * state.
     */
    private suspend fun SemanticEventScope.closeAllBlockquotes() {
        while (blockModeStack.any { it is BlockMode.Blockquote }) {
            when (blockMode) {
                BlockMode.Blockquote -> {
                    unmark("blockquote")
                    popMode()
                }
                BlockMode.Start -> {
                    if (blockModeStack.size == 1) break
                    popMode()
                }
                else -> drainTopFrameToStart()
            }
        }
        blockquotePrefixDone = true
        blockquotePrefixCount = 0
        blockquotePrefixIndent = 0
        blockquoteJustAfterGt = false
        blockquoteInFailedLine = false
        blockquoteFailedLineBuffer.clear()
    }

    /**
     * Close the in-flight block of the current top frame, replacing it with
     * `Start`. Used by [closeAllBlockquotes] to drain inner sub-parse state
     * before unmarking the enclosing `<blockquote>`.
     */
    private suspend fun SemanticEventScope.drainTopFrameToStart() {
        when (val mode = blockMode) {
            is BlockMode.Heading -> {
                flushInline()
                unmark("h${mode.level}")
                replaceMode(BlockMode.Start)
            }
            BlockMode.Paragraph -> {
                flushInline()
                endParagraph()
                replaceMode(BlockMode.Start)
                paragraphTrailingSpaces = 0
            }
            BlockMode.ParagraphContinuation -> {
                endParagraph()
                replaceMode(BlockMode.Start)
                paragraphTrailingSpaces = 0
            }
            is BlockMode.CodeBlock -> {
                if (lineBuffer.isNotEmpty()) {
                    val line = lineBuffer.toString()
                    lineBuffer.clear()
                    if (!isFenceClose(line, mode.marker, mode.length)) {
                        +"${stripIndentCols(line, mode.indent)}\n"
                    }
                }
                unmark("code")
                unmark("pre")
                replaceMode(BlockMode.Start)
            }
            BlockMode.IndentedCodeBlock -> {
                indentedCodeDeferredBlanks.clear()
                if (lineBuffer.isNotEmpty()) {
                    val line = lineBuffer.toString()
                    lineBuffer.clear()
                    if (leadingIndentCols(line) >= 4) {
                        +"${stripIndentCols(line, 4)}\n"
                    }
                }
                unmark("code")
                unmark("pre")
                replaceMode(BlockMode.Start)
            }
            is BlockMode.ListBlock -> {
                popListContexts(mode, downTo = 0)
                replaceMode(BlockMode.Start)
            }
            else -> {
                // Best-effort fallback for any other in-flight frame.
                replaceMode(BlockMode.Start)
            }
        }
    }

    /**
     * Open a blockquote frame and route inner content through a fresh `Start`
     * sub-parse frame. Called from `processStart` once a blockquote opener
     * line (`>`, `> X`, or `>X`) has been recognized.
     */
    private suspend fun SemanticEventScope.openBlockquote(line: String) {
        mark("blockquote")
        pushMode(BlockMode.Blockquote)
        pushMode(BlockMode.Start)
        // We just consumed this line's `>` prefix at the new level; mark prefix done
        // so the rest of the line (replayed below) flows through inner Start as content.
        blockquotePrefixDone = true
        blockquotePrefixCount = blockquoteFrameCount()
        // Strip the leading 0..3 spaces, the `>`, and the optional single space.
        var i = 0
        while (i < line.length && i < 3 && line[i] == ' ') i++
        if (i >= line.length || line[i] != '>') return
        i++
        if (i < line.length && (line[i] == ' ' || line[i] == '\t')) i++
        val rest = if (i < line.length) line.substring(i) else ""
        if (rest.isNotEmpty()) replay(rest)
        process('\n')
    }

    private suspend fun SemanticEventScope.processMathBlock(
        char: Char
    ) {
        if (char == '\n') {
            val line = lineBuffer.toString()
            if (line.trim() == "$$") {
                unmark("math")
                lineBuffer.clear()
                replaceMode(BlockMode.Start)
            } else {
                if (line.isNotEmpty()) {
                    +line
                }
                lineBuffer.clear()
            }
        } else {
            lineBuffer.append(char)
        }
    }

    /**
     * Validate the buffered header line against [separatorLine]: parse the
     * separator into per-column alignments (each entry: "left", "center",
     * "right", or null) iff every cell matches `:?-+:?`. Returns null when
     * [separatorLine] is not a valid separator or its column count differs
     * from [expectedCols].
     */
    private fun parseSeparatorAlignments(
        separatorLine: String,
        expectedCols: Int
    ): List<String?>? {
        val trimmed = separatorLine.trim()
        if (trimmed.isEmpty()) return null
        // Quick reject: separator chars are only `:`, `-`, `|`, space, tab.
        if (!trimmed.all { it == ':' || it == '-' || it == '|' || it == ' ' || it == '\t' }) return null
        val cells = splitTableCells(trimmed)
        if (cells.size != expectedCols) return null
        val out = ArrayList<String?>(cells.size)
        for (cell in cells) {
            val c = cell.trim()
            if (c.isEmpty()) return null
            val left = c.startsWith(":")
            val right = c.endsWith(":")
            val mid = c.removePrefix(":").removeSuffix(":")
            if (mid.isEmpty() || !mid.all { it == '-' }) return null
            out += when {
                left && right -> "center"
                right -> "right"
                left -> "left"
                else -> null
            }
        }
        return out
    }

    /**
     * Split a GFM table row into cells. Strips one optional leading and one
     * optional trailing pipe, treats `\|` as a literal `|` in cell content
     * (not a separator), and treats `|` inside a backtick code span as cell
     * content (not a separator). Whitespace around cells is preserved here —
     * callers trim individual cells.
     */
    private fun splitTableCells(line: String): List<String> {
        var s = line
        if (s.startsWith("|")) s = s.substring(1)
        // Drop a single trailing unescaped `|`.
        if (s.endsWith("|") && !s.endsWith("\\|")) s = s.substring(0, s.length - 1)
        val cells = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        var inCode = false
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < s.length && s[i + 1] == '|' -> {
                    // GFM table extension: `\|` is a literal pipe in cell text.
                    sb.append('|')
                    i += 2
                }
                c == '`' -> {
                    var j = i
                    while (j < s.length && s[j] == '`') {
                        sb.append('`')
                        j++
                    }
                    inCode = !inCode
                    i = j
                }
                c == '|' && !inCode -> {
                    cells += sb.toString()
                    sb.clear()
                    i++
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        cells += sb.toString()
        return cells
    }

    /**
     * Process the line that follows a buffered prospective table header.
     * If it parses as a valid separator with matching column count, commit
     * the table (emit thead with the cached header cells, then defer the
     * tbody mark until the first body row); otherwise replay the header
     * line and the current line back through `Start` (with table detection
     * suppressed) so they flow as a paragraph instead of re-entering
     * TableHeaderPending in an infinite loop.
     */
    private suspend fun SemanticEventScope.processTableHeaderPending(
        char: Char,
        mode: BlockMode.TableHeaderPending
    ) {
        lineBuffer.append(char)
        if (char != '\n') return
        val secondLine = lineBuffer.toString().trimEnd()
        lineBuffer.clear()
        val headerCells = splitTableCells(mode.headerLine.trimEnd())
        val alignments = parseSeparatorAlignments(secondLine, headerCells.size)
        if (alignments != null) {
            mark("table")
            mark("thead")
            "tr" {
                emitTableCells(headerCells, isHeader = true, alignments = alignments)
            }
            unmark("thead")
            replaceMode(BlockMode.TableBody(headerCells.size, alignments))
        } else {
            replaceMode(BlockMode.Start)
            suppressTableDetection = true
            try {
                replay(mode.headerLine)
                process('\n')
                if (secondLine.isNotEmpty()) {
                    replay(secondLine)
                    process('\n')
                }
            } finally {
                suppressTableDetection = false
            }
        }
    }

    private suspend fun SemanticEventScope.processTableBody(
        char: Char,
        mode: BlockMode.TableBody
    ) {
        lineBuffer.append(char)
        if (char != '\n') return
        val line = lineBuffer.toString().trimEnd()
        lineBuffer.clear()
        if (line.isEmpty()) {
            closeTableBody(mode)
            replaceMode(BlockMode.Start)
            return
        }
        // A `|`-prefixed line is always a row. A non-`|` line is a continuation
        // row unless it would interrupt a paragraph (heading, blockquote, list,
        // hr, fenced code, html block, …) — those break the table and replay.
        val endsTable = !line.startsWith("|") && lineInterruptsParagraph(line)
        if (endsTable) {
            closeTableBody(mode)
            replaceMode(BlockMode.Start)
            replay(line)
            process('\n')
            return
        }
        emitBodyRow(mode, line)
    }

    private suspend fun SemanticEventScope.emitBodyRow(
        mode: BlockMode.TableBody,
        line: String
    ) {
        if (!mode.bodyOpened) {
            mark("tbody")
            mode.bodyOpened = true
        }
        val cells = splitTableCells(line)
        "tr" {
            emitTableCells(
                cells,
                isHeader = false,
                alignments = mode.alignments,
                columnCount = mode.columnCount
            )
        }
    }

    private suspend fun SemanticEventScope.closeTableBody(mode: BlockMode.TableBody) {
        if (mode.bodyOpened) unmark("tbody")
        unmark("table")
    }

    private suspend fun SemanticEventScope.emitTableCells(
        cells: List<String>,
        isHeader: Boolean,
        alignments: List<String?>,
        columnCount: Int = cells.size
    ) {
        val tag = if (isHeader) "th" else "td"
        for (i in 0 until columnCount) {
            val align = alignments.getOrNull(i)
            val attrs = align?.let { mapOf("align" to it) } ?: emptyMap()
            mark(tag, attributes = attrs)
            val cell = cells.getOrNull(i)?.trim().orEmpty()
            if (cell.isNotEmpty()) {
                processInlineContent(cell)
                flushInline()
            }
            unmark(tag)
        }
    }

    /**
     * Parses a custom markup opening tag like `<foo:bar attr="value">`.
     * Returns the tag name and attributes map, or null if not a valid custom markup tag.
     * Custom markup tags must have a namespace (contain a colon in the tag name).
     */
    private fun parseCustomMarkupOpeningTag(line: String): Pair<String, Map<String, String>?>? {
        // Remove < and >
        val content = line.removePrefix("<").removeSuffix(">").trim()
        if (content.isEmpty()) return null

        // Split into tag name and attributes
        val parts = content.split(Patterns.WHITESPACE, limit = 2)
        val tagName = parts[0]

        // Strict tag name validation: namespace:name where each segment is [letter][letter|digit|-]*
        if (!CUSTOM_MARKUP_TAGNAME.matches(tagName)) return null

        // Parse attributes if present
        val attributes = if (parts.size > 1) {
            parseAttributes(parts[1])
        } else {
            null
        }

        return tagName to attributes
    }

    /**
     * Parses attribute string like `attr="value" other="val2"` into a map.
     */
    private fun parseAttributes(attrString: String): Map<String, String>? {
        if (attrString.isBlank()) return null

        val attributes = mutableMapOf<String, String>()
        // Simple regex-based attribute parsing: name="value" or name='value'
        Patterns.ATTRIBUTE.findAll(attrString).forEach { match ->
            val (name, value) = match.destructured
            attributes[name] = value
        }

        return if (attributes.isNotEmpty()) attributes else null
    }

    /**
     * Process content inside a custom markup block.
     * Content is emitted immediately as it arrives.
     * Closing tag is detected incrementally when </tagname> pattern is seen.
     */
    private suspend fun SemanticEventScope.processCustomMarkup(
        char: Char,
        tagName: String
    ) {
        // Skip the first newline after opening tag (when detected incrementally)
        if (customMarkupSkipFirstNewline) {
            customMarkupSkipFirstNewline = false
            if (char == '\n') {
                return
            }
        }

        val closingTag = "</$tagName>"

        // If we're tracking a potential closing tag
        if (customMarkupInClosingTag) {
            customMarkupClosingBuffer.append(char)
            val bufferStr = customMarkupClosingBuffer.toString()

            when {
                // Complete closing tag found
                bufferStr == closingTag -> {
                    customMarkupClosingBuffer.clear()
                    customMarkupInClosingTag = false
                    unmark(tagName, isTagged = true)
                    replaceMode(BlockMode.Start)
                }
                // Still a valid prefix of closing tag
                closingTag.startsWith(bufferStr) -> {
                    // Keep buffering
                }
                // Not a closing tag - emit pending newline and buffered content
                else -> {
                    if (customMarkupPendingNewline) {
                        customMarkupPendingNewline = false
                        +'\n'
                    }
                    +customMarkupClosingBuffer.toString()
                    customMarkupClosingBuffer.clear()
                    customMarkupInClosingTag = false
                }
            }
            return
        }

        // Check if this character starts a potential closing tag
        // Note: Don't emit pending newline yet - it may be part of whitespace before closing tag
        if (char == '<') {
            customMarkupClosingBuffer.append(char)
            customMarkupInClosingTag = true
            return
        }

        // Handle pending newline - emit it now before new content
        if (customMarkupPendingNewline) {
            customMarkupPendingNewline = false
            +'\n'
        }

        // Handle newlines - buffer them to avoid trailing newlines
        if (char == '\n') {
            customMarkupPendingNewline = true
            return
        }

        // Regular content - emit immediately
        +char
    }

    /**
     * Emit `mark` for an HTML-derived tag with `isTagged = true`. Known HTML5
     * tag and attribute names are lowercased (so `<DIV CLASS="foo">` and
     * `<div class="foo">` produce identical events); names outside the HTML5
     * set keep their source casing — `<Warning>` stays `Warning`, treating
     * non-HTML5 tags as XML-ish where case is significant.
     */
    private suspend fun SemanticEventScope.markHtml(
        name: String,
        attributes: Map<String, String>? = null
    ) {
        val isHtml5 = name.lowercase() in HTML5_ELEMENTS
        mark(
            name = if (isHtml5) name.lowercase() else name,
            isTagged = true,
            attributes = attributes?.takeIf { it.isNotEmpty() }
                ?.let { attrs -> if (isHtml5) attrs.mapKeys { it.key.lowercase() } else attrs }
                ?: emptyMap()
        )
    }

    /**
     * Emit `unmark` for an HTML-derived tag with `isTagged = true`. Known
     * HTML5 names are lowercased; non-HTML5 names keep their source casing.
     * See [markHtml] for the rationale.
     */
    private suspend fun SemanticEventScope.unmarkHtml(name: String) {
        unmark(normalizeHtmlName(name), isTagged = true)
    }

    /**
     * Emit balanced close events for the HTML close tag named [closeName],
     * draining [openTags] LIFO down to (and including) the deepest matching
     * open. If no match is found, the close is **dropped silently** — per
     * the HTML5 parser spec ("an end tag whose tag name is not in the stack
     * of open elements is a parse error; ignore the token"), this keeps the
     * downstream event stream balanced for the common real-world case where
     * source HTML has stray closes that don't pair with anything on the
     * current frame's open stack.
     */
    private suspend fun SemanticEventScope.emitMatchingClose(
        closeName: String,
        openTags: MutableList<String>
    ) {
        val lower = closeName.lowercase()
        val matchIdx = openTags.indexOfLast { it.lowercase() == lower }
        if (matchIdx < 0) return
        while (openTags.size > matchIdx + 1) {
            unmarkHtml(openTags.removeLast())
        }
        unmarkHtml(openTags.removeLast())
    }

    /**
     * Enter an HTML block (CommonMark 4.6) given the first source line (already buffered).
     * Dispatches by detected block type and seeds the per-type state.
     */
    private suspend fun SemanticEventScope.enterHtmlBlock(line: String) {
        val type = detectHtmlBlockType(line)
        when (type) {
            1 -> {
                val rootTag = type1RootTagOf(line) ?: run {
                    +"$line\n"
                    return
                }
                val mode = BlockMode.HtmlBlock1(rootTag)
                replaceMode(mode)
                // Feed every character of the first line into the type-1 processor.
                for (c in line) processHtmlBlock1(c, mode)
                processHtmlBlock1('\n', mode)
            }
            2, 3, 4, 5 -> {
                if (type == 4 && isDoctypeLine(line)) {
                    enterDoctypeBlock(line)
                    return
                }
                val closingSeq = when (type) {
                    2 -> "-->"
                    3 -> "?>"
                    4 -> ">"
                    5 -> "]]>"
                    else -> error("unreachable")
                }
                +"$line\n"
                if (lineContainsClosingSeq(line, closingSeq)) {
                    replaceMode(BlockMode.Start)
                } else {
                    replaceMode(BlockMode.HtmlBlock2to5(closingSeq))
                }
            }
            6, 7 -> {
                val (rootName, isClose) = type6or7RootTagOf(line) ?: run {
                    +"$line\n"
                    return
                }
                val mode = BlockMode.HtmlBlock6or7(rootName, isClose)
                // Push (don't replace) so the enclosing context survives — sub-parse
                // may push a fresh `Start` frame on top, and the matching root close
                // tag pops back to whatever was underneath.
                pushMode(mode)
                // Opening-tag root enters raw-text streaming — suppress extended
                // autolinks until raw-text exits (close / sub-parse transition).
                // See [leaveRawHtmlBlock] for the matching decrement.
                if (!isClose) autolinker.htmlRawTextDepth++
                // Buffer the first line into firstLineBuffer for tag-completion checks.
                mode.firstLineBuffer!!.append(line)
                tryFinishHtmlBlock6or7FirstLine(mode)
            }
            else -> +"$line\n"
        }
    }

    /**
     * Decrement [autolinker]'s HTML raw-text suppression counter when an
     * opening-tag-rooted [BlockMode.HtmlBlock6or7] frame leaves its raw-text
     * phase — by closing, by transitioning to sub-parse on a blank line, or
     * by finalize/EOF cleanup. Idempotent: uses [BlockMode.ChildMode.SubParse]
     * as a sentinel so callers may invoke at any exit point without tracking
     * which transition already fired.
     */
    private fun leaveRawHtmlBlock(mode: BlockMode.HtmlBlock6or7) {
        if (mode.rootIsClosingTag) return
        if (mode.childMode == BlockMode.ChildMode.RawText) {
            autolinker.htmlRawTextDepth--
            mode.childMode = BlockMode.ChildMode.SubParse
        }
    }

    /**
     * Process a character inside an HTML block of type 1 (pre/script/style/textarea).
     * Opening tag(s) on the first line(s) are parsed structurally; subsequent lines emit
     * content as raw text events; closing tag emits unmark for the root and any inner tags.
     */
    private suspend fun SemanticEventScope.processHtmlBlock1(
        char: Char,
        mode: BlockMode.HtmlBlock1
    ) {
        // Phase 1: still buffering the opening tag line(s) until '>' is found.
        val firstLineBuffer = mode.firstLineBuffer
        if (firstLineBuffer != null) {
            if (char == '\n') {
                // Try to finalize the opening tag(s).
                if (tryFinishHtmlBlock1FirstLine(mode)) {
                    // Opening parsed; remainder (if any) was handled. Stay in mode unless block ended.
                    return
                }
                // Not yet complete; append the newline and keep buffering.
                firstLineBuffer.append('\n')
                return
            }
            firstLineBuffer.append(char)
            return
        }

        // Phase 2: buffer content lines, on \n decide.
        if (char != '\n') {
            lineBuffer.append(char)
            return
        }
        val line = lineBuffer.toString()
        lineBuffer.clear()
        emitHtmlBlock1ContentLine(line, mode)
    }

    /** Emit a single content line for a type-1 block, handling closing tag detection. */
    private suspend fun SemanticEventScope.emitHtmlBlock1ContentLine(
        line: String,
        mode: BlockMode.HtmlBlock1
    ) {
        // Look for the root closing tag, case-insensitive: "</rootTag>"
        val closingPattern = "</${mode.rootTag}"
        val lowerLine = line.lowercase()
        val closeIndex = lowerLine.indexOf(closingPattern)
        if (closeIndex < 0) {
            +"$line\n"
            return
        }
        val afterName = closeIndex + closingPattern.length
        var i = afterName
        while (i < line.length && (line[i] == ' ' || line[i] == '\t')) i++
        if (i >= line.length || line[i] != '>') {
            +"$line\n"
            return
        }
        val closeEnd = i + 1
        // GFM §4.6: type-1 block bodies are raw text — open tags inside the body
        // (`var s = "<div>foo</div>"` in `<script>`, `a::before{content:"<x>"}`
        // in `<style>`) must NOT open nested marks. But the opener-chain
        // mechanism in [tryFinishHtmlBlock1FirstLine] does open contiguous tags
        // (e.g. `<pre><code>`), so we still recognise close tags inside the body
        // that match the current top of `mode.openTags` and pop them at the
        // right position. Non-matching closes and any open tags inside the body
        // pass through as literal text.
        val before = line.substring(0, closeIndex)
        if (before.isNotEmpty()) {
            val pendingText = StringBuilder()
            var j = 0
            while (j < before.length) {
                if (before[j] == '<') {
                    val tokClose = tryParseCloseTag(before, j)
                    if (tokClose != null && mode.openTags.isNotEmpty() &&
                        mode.openTags.last().lowercase() == tokClose.second.name.lowercase()
                    ) {
                        if (pendingText.isNotEmpty()) {
                            +pendingText.toString(); pendingText.clear()
                        }
                        mode.openTags.removeLast()
                        unmarkHtml(tokClose.second.name)
                        j = tokClose.first
                        continue
                    }
                }
                pendingText.append(before[j])
                j++
            }
            if (pendingText.isNotEmpty()) +pendingText.toString()
        }
        // Close any tags still open then the root.
        while (mode.openTags.size > 1) {
            unmarkHtml(mode.openTags.removeLast())
        }
        if (mode.openTags.isNotEmpty()) {
            unmarkHtml(mode.openTags.removeLast())
        }
        // Trailing content after the close: re-run HTML block detection so
        // adjacent type-1 blocks on the same line (e.g. `</script><style>...`)
        // open a fresh block instead of flowing as paragraph text. Non-HTML
        // trailing content keeps the historical behaviour (emitted as a raw
        // text event with newline — no paragraph wrapping).
        val trailing = line.substring(closeEnd)
        replaceMode(BlockMode.Start)
        if (trailing.isNotEmpty()) {
            if (detectHtmlBlockType(trailing) > 0) {
                enterHtmlBlock(trailing)
            } else {
                +"$trailing\n"
            }
        }
    }

    /**
     * Once `mode.firstLineBuffer` contains the entire opening tag (with `>` found),
     * tokenize, emit marks for opening tag(s), then handle any remainder of the same line.
     * Returns true if the opening tag was successfully parsed.
     */
    private suspend fun SemanticEventScope.tryFinishHtmlBlock1FirstLine(
        mode: BlockMode.HtmlBlock1
    ): Boolean {
        val buf = mode.firstLineBuffer ?: return true
        val source = buf.toString()
        // Try to parse from the first '<' onwards.
        val ltIndex = source.indexOf('<')
        if (ltIndex < 0) return false
        // Emit any text before '<' as raw text.
        val leading = source.substring(0, ltIndex)
        // Try to parse one or more open tags starting at ltIndex.
        var idx = ltIndex
        val opens = mutableListOf<HtmlToken.OpenTag>()
        var foundRoot = false
        while (idx < source.length && source[idx] == '<') {
            val open = tryParseOpenTag(source, idx) ?: break
            opens += open.second
            if (open.second.name.lowercase() == mode.rootTag) foundRoot = true
            idx = open.first
        }
        if (!foundRoot) return false
        // Successfully parsed up to and including the root tag.
        if (leading.isNotEmpty()) +leading
        for (tag in opens) {
            markHtml(tag.name, tag.attributes)
            mode.openTags += normalizeHtmlName(tag.name)
        }
        mode.firstLineBuffer = null
        // Handle any remaining content on the same first line.
        val remainder = source.substring(idx)
        if (remainder.isNotEmpty()) {
            // Treat the rest of the line as content; emit as text up to closing tag if present.
            emitHtmlBlock1ContentLine(remainder, mode)
        }
        return true
    }

    /** Process a character inside HTML block of type 2–5 (comment / PI / decl / CDATA). */
    private suspend fun SemanticEventScope.processHtmlBlock2to5(
        char: Char,
        mode: BlockMode.HtmlBlock2to5
    ) {
        if (char != '\n') {
            lineBuffer.append(char)
            return
        }
        val line = lineBuffer.toString()
        lineBuffer.clear()
        +"$line\n"
        if (lineContainsClosingSeq(line, mode.closingSequence)) {
            replaceMode(BlockMode.Start)
        }
    }

    /**
     * Open a DOCTYPE block from its first source [line]. Emits
     * `mark("doctype", isTagged = true)`, then the content between `<!DOCTYPE`
     * and `>` (or end-of-line) as text, stripping the whitespace run
     * immediately after `<!DOCTYPE`. On same-line close, also emits the
     * matching `unmark` and any trailing text after `>` (with a `\n`).
     * Otherwise enters [BlockMode.Doctype] so subsequent lines stream as
     * text content until the closing `>`.
     */
    private suspend fun SemanticEventScope.enterDoctypeBlock(line: String) {
        val trimmed = line.trimStart(' ')
        val afterPrefix = trimmed.substring(DOCTYPE_PREFIX_LENGTH)
        mark("doctype", isTagged = true)
        val gtIndex = afterPrefix.indexOf('>')
        if (gtIndex >= 0) {
            val content = afterPrefix.substring(0, gtIndex).trimStart(' ', '\t')
            if (content.isNotEmpty()) +content
            unmark("doctype", isTagged = true)
            val trailing = afterPrefix.substring(gtIndex + 1)
            if (trailing.isNotEmpty()) +"$trailing\n"
            replaceMode(BlockMode.Start)
        } else {
            val content = afterPrefix.trimStart(' ', '\t')
            if (content.isNotEmpty()) +content
            replaceMode(BlockMode.Doctype)
        }
    }

    /**
     * Process a character inside a multi-line DOCTYPE block. Each continuation
     * line's text is prefixed with `\n` so consecutive content lines are
     * joined verbatim (the opener line's text has no trailing `\n` to avoid
     * a spurious newline if the input ends with no further content). When `>`
     * is seen the block closes (emits `unmark`); any trailing chars after
     * `>` are emitted as a top-level text event with `\n`. Unclosed DOCTYPE
     * at EOF is force-closed by the [finalize] drain.
     *
     * DIVERGENCE from GFM type-4: a blank line inside the declaration does
     * not close the block — it streams as `"\n"` content and the block stays
     * open until `>` or EOF. Same rationale as the HTML 6/7 blank-line
     * divergence: append-only emission cannot retract the already-emitted
     * `mark("doctype")`. Multi-line DOCTYPE with an embedded blank line is
     * malformed in practice, so the cost is theoretical.
     */
    private suspend fun SemanticEventScope.processDoctypeBlock(char: Char) {
        if (char != '\n') {
            lineBuffer.append(char)
            return
        }
        val line = lineBuffer.toString()
        lineBuffer.clear()
        val gtIndex = line.indexOf('>')
        if (gtIndex < 0) {
            +"\n$line"
            return
        }
        val before = line.substring(0, gtIndex)
        if (before.isNotEmpty()) +"\n$before"
        unmark("doctype", isTagged = true)
        val trailing = line.substring(gtIndex + 1)
        if (trailing.isNotEmpty()) +"$trailing\n"
        replaceMode(BlockMode.Start)
    }

    /**
     * Process a character inside an HTML block of type 6 or 7. Streams incrementally:
     *
     *   Phase 1 — opening tag(s): buffer until the first tag's `>` is parsed, then
     *     emit `mark` for each open tag (and any inline content before the matching
     *     root close, if it lands on the same line). For a closing-tag opener
     *     (e.g. `</div>` on its own line), no mark is emitted; the block transitions
     *     to raw-text mode.
     *
     *   Phase 2 — content streaming: each subsequent line is tokenized; nested
     *     open/close tags emit marks; the matching root close emits the final
     *     `unmark` and exits. Blank lines emit `\n` as text and the block stays
     *     open (DIVERGENCE from GFM, which closes on blank lines — required to
     *     keep emission append-only).
     *
     *   EOF — `flush()` emits any still-open `unmark` for tags on the openTags stack.
     */
    private suspend fun SemanticEventScope.processHtmlBlock6or7(
        char: Char,
        mode: BlockMode.HtmlBlock6or7
    ) {
        val firstLineBuffer = mode.firstLineBuffer
        if (firstLineBuffer != null) {
            if (char == '\n') {
                // tryFinish appends '\n' to the buffer on failure so that
                // multi-line attribute values keep their line breaks.
                tryFinishHtmlBlock6or7FirstLine(mode)
                return
            }
            firstLineBuffer.append(char)
            return
        }
        if (char != '\n') {
            lineBuffer.append(char)
            return
        }
        val line = lineBuffer.toString()
        lineBuffer.clear()
        streamHtmlBlock6or7ContentLine(line, mode)
    }

    /**
     * Once `mode.firstLineBuffer` contains a complete opening (or closing) tag,
     * emit marks for any open tag(s), then process any remainder of the same line
     * as content. Returns true if the tag was parsed (Phase 1 complete).
     */
    private suspend fun SemanticEventScope.tryFinishHtmlBlock6or7FirstLine(
        mode: BlockMode.HtmlBlock6or7
    ): Boolean {
        val buf = mode.firstLineBuffer ?: return true
        val source = buf.toString()
        var i = 0
        while (i < source.length && source[i] == ' ') i++
        if (i >= source.length || source[i] != '<') {
            buf.append('\n')
            return false
        }
        val open = tryParseOpenTag(source, i)
        val close = if (open == null) tryParseCloseTag(source, i) else null
        if (open == null && close == null) {
            buf.append('\n')
            return false
        }
        mode.firstLineBuffer = null
        if (mode.rootIsClosingTag) {
            // Closing-tag root: no mark. Pass opener line through as raw text and
            // continue in raw-text streaming mode (no nested-tag recognition).
            +"$source\n"
            return true
        }
        // Opening-tag root: emit mark for the first open and any contiguous opens
        // that follow on the same line, then stream the remainder as content.
        val leading = source.substring(0, i)
        if (leading.isNotEmpty()) +leading
        var idx = i
        while (idx < source.length && source[idx] == '<') {
            val nextOpen = tryParseOpenTag(source, idx) ?: break
            if (nextOpen.second.name.lowercase() in GFM_DISALLOWED_TAGS) {
                // GFM §6.11: stop the opener chain here. The disallowed tag and
                // everything after it route through `streamHtmlBlock6or7ContentLine`,
                // where `tokenizeHtmlLine` returns the tag source as literal text.
                break
            }
            markHtml(nextOpen.second.name, nextOpen.second.attributes)
            if (nextOpen.second.isSelfClosingOrVoid()) {
                unmarkHtml(nextOpen.second.name)
            } else {
                mode.openTags += normalizeHtmlName(nextOpen.second.name)
            }
            idx = nextOpen.first
        }
        val remainder = source.substring(idx)
        if (remainder.isEmpty()) {
            +"\n"
        } else {
            streamHtmlBlock6or7ContentLine(remainder, mode)
        }
        return true
    }

    /**
     * Stream a single content line of a type-6/7 block: tokenize, emit nested
     * open/close marks, and exit on the matching root close. Blank lines emit
     * a single `\n` text event and keep the block open.
     */
    private suspend fun SemanticEventScope.streamHtmlBlock6or7ContentLine(
        line: String,
        mode: BlockMode.HtmlBlock6or7
    ) {
        if (mode.rootIsClosingTag) {
            // Raw-text passthrough mode — no nested tag recognition.
            +"$line\n"
            return
        }
        if (line.isEmpty()) {
            // Blank line in raw-text mode: emit `\n` and transition the frame to
            // sub-parse mode. From here on, subsequent lines route through the
            // regular Markdown dispatcher (we push a fresh `Start` frame on top).
            // The matching root close tag is then handled by the close-tag check
            // at the top of `processStart`'s `\n` handler.
            +"\n"
            leaveRawHtmlBlock(mode)
            pushMode(BlockMode.Start)
            return
        }
        val rootCloseIdx = findRootCloseTagIndex(line, mode.rootTagName)
        if (rootCloseIdx < 0) {
            emitContentTokensWithTrailingNewline(line, mode)
            return
        }
        // Root close present on this line. Emit content before it (with nested-tag
        // handling), then unmark all open tags, then trailing as top-level text.
        val before = line.substring(0, rootCloseIdx)
        if (before.isNotEmpty()) emitContentTokensNoTrailing(before, mode)
        // Advance past `</rootTag` then optional whitespace + `>`.
        var p = rootCloseIdx + 2 + mode.rootTagName.length
        while (p < line.length && (line[p] == ' ' || line[p] == '\t')) p++
        val closeEnd = p + 1  // past '>'
        while (mode.openTags.size > 1) unmarkHtml(mode.openTags.removeLast())
        if (mode.openTags.isNotEmpty()) unmarkHtml(mode.openTags.removeLast())
        val trailing = line.substring(closeEnd)
        if (trailing.isNotEmpty()) +"$trailing\n"
        leaveRawHtmlBlock(mode)
        // Pop the HTML frame; the enclosing context resumes (typically `Start`).
        popMode()
    }

    /**
     * If [line] contains the matching root close tag of an enclosing
     * `HtmlBlock6or7` sub-parse frame, close everything above it (the pushed
     * sub-parse `Start` frame), then close the HTML frame itself: emit unmarks
     * for nested still-open tags plus the root, and emit any trailing text
     * after the close tag. Returns true when the close fired.
     *
     * Called from `processStart`'s `\n` handler after the line has been formed.
     * Top of stack is `Start`; below it is the HTML frame whose root close was
     * matched. Frames between (defensive — should not normally exist here)
     * are popped without ceremony.
     */
    private suspend fun SemanticEventScope.tryCloseEnclosingHtmlBlock(
        line: String
    ): Boolean {
        // Non-root close: a stand-alone close tag matching an inner `openTag`
        // tracked by an enclosing frame (e.g. `</pre>` while a `<table>` frame
        // has `pre` in its openTags). Drain that frame's openTags down to and
        // including the matched name, then resume sub-parse with a fresh
        // `Start` on top so subsequent lines route through the dispatcher.
        //
        // Checked BEFORE the root close: an inner `openTag` is deeper than the
        // frame root, so when the close name matches both (same-named nesting
        // like `<nav><nav>…</nav></nav>`) the inner one wins LIFO — closing a
        // single level instead of draining the whole frame.
        val nonRoot = findEnclosingHtmlOpenTagClose(line)
        if (nonRoot != null) {
            val (frameIdx, closeName) = nonRoot
            while (blockModeStack.lastIndex > frameIdx) popMode()
            val mode = blockModeStack[frameIdx] as BlockMode.HtmlBlock6or7
            // Compare case-insensitively against `closeName` (always lowercased)
            // so non-HTML5 openTags (which preserve source casing) still match.
            while (mode.openTags.isNotEmpty() &&
                mode.openTags.last().lowercase() != closeName
            ) {
                unmarkHtml(mode.openTags.removeLast())
            }
            if (mode.openTags.isNotEmpty()) {
                unmarkHtml(mode.openTags.removeLast())
            }
            leaveRawHtmlBlock(mode)
            pushMode(BlockMode.Start)
            return true
        }
        // Root close: the line carries the matching root close tag of an
        // enclosing frame (and the name did not match any deeper inner
        // `openTag`). Drain all of the frame's open tags and pop it.
        val idx = findEnclosingHtmlFrameIndex(line)
        if (idx >= 0) {
            val mode = blockModeStack[idx] as BlockMode.HtmlBlock6or7
            // Drop frames above the HTML frame (the pushed sub-parse `Start`,
            // and any defensive remnants).
            while (blockModeStack.lastIndex > idx) popMode()
            val rootCloseIdx = findRootCloseTagIndex(line, mode.rootTagName)
            val before = line.substring(0, rootCloseIdx)
            if (before.isNotEmpty()) emitContentTokensNoTrailing(before, mode)
            var p = rootCloseIdx + 2 + mode.rootTagName.length
            while (p < line.length && (line[p] == ' ' || line[p] == '\t')) p++
            val closeEnd = p + 1
            while (mode.openTags.size > 1) unmarkHtml(mode.openTags.removeLast())
            if (mode.openTags.isNotEmpty()) unmarkHtml(mode.openTags.removeLast())
            val trailing = line.substring(closeEnd)
            if (trailing.isNotEmpty()) +"$trailing\n"
            leaveRawHtmlBlock(mode)
            popMode()
            return true
        }
        return false
    }

    /**
     * Tokenize [line] and emit nested open/close marks plus text. Adds a trailing
     * `\n` as the line's terminator (used when the root close did NOT appear).
     */
    private suspend fun SemanticEventScope.emitContentTokensWithTrailingNewline(
        line: String,
        mode: BlockMode.HtmlBlock6or7
    ) {
        val pending = StringBuilder()
        suspend fun flushPending() {
            if (pending.isNotEmpty()) { +pending.toString(); pending.clear() }
        }
        // [tokenizeHtmlLine] already returns disallowed tags (GFM §6.11) as
        // `HtmlToken.Text` so they pass through as plain text without ever
        // becoming a `mark`/`unmark` pair here.
        val tokens = tokenizeHtmlLine(line)
        for (tok in tokens) {
            when (tok) {
                is HtmlToken.Text -> pending.append(tok.content)
                is HtmlToken.OpenTag -> {
                    flushPending()
                    markHtml(tok.name, tok.attributes)
                    if (tok.isSelfClosingOrVoid()) unmarkHtml(tok.name)
                    else mode.openTags += normalizeHtmlName(tok.name)
                }
                is HtmlToken.CloseTag -> {
                    flushPending()
                    emitMatchingClose(tok.name, mode.openTags)
                }
            }
        }
        pending.append('\n')
        +pending.toString()
    }

    /**
     * Tokenize [line] and emit nested open/close marks plus text. Does NOT add a
     * trailing `\n` (used for the segment before a root close on the same line).
     */
    private suspend fun SemanticEventScope.emitContentTokensNoTrailing(
        line: String,
        mode: BlockMode.HtmlBlock6or7
    ) {
        val pending = StringBuilder()
        suspend fun flushPending() {
            if (pending.isNotEmpty()) { +pending.toString(); pending.clear() }
        }
        val tokens = tokenizeHtmlLine(line)
        for (tok in tokens) {
            when (tok) {
                is HtmlToken.Text -> pending.append(tok.content)
                is HtmlToken.OpenTag -> {
                    flushPending()
                    markHtml(tok.name, tok.attributes)
                    if (tok.isSelfClosingOrVoid()) unmarkHtml(tok.name)
                    else mode.openTags += normalizeHtmlName(tok.name)
                }
                is HtmlToken.CloseTag -> {
                    flushPending()
                    emitMatchingClose(tok.name, mode.openTags)
                }
            }
        }
        if (pending.isNotEmpty()) +pending.toString()
    }

    /**
     * Find the index of the matching root close tag `</rootName>`
     * (case-insensitive) in [line], or -1.
     *
     * Tracks same-line nesting of `<rootName …>` openers vs `</rootName>`
     * closers and returns only the index of the first **excess** close — one
     * not balanced by a line-local opener of the same name. A self-contained
     * `<div …></div>` on a content line therefore does NOT count as the
     * enclosing `div` frame's root close (its `</div>` balances its own
     * opener); only a genuinely unmatched `</div>` closes the frame. Without
     * this, a one-line `<div></div>` inside a `div`-rooted block would be
     * mistaken for the block's close, prematurely popping it and leaking the
     * rest of the document as raw text.
     */
    private fun findRootCloseTagIndex(line: String, rootTagName: String): Int {
        val lower = line.lowercase()
        val openPattern = "<$rootTagName"
        val closePattern = "</$rootTagName"
        var depth = 0
        var i = 0
        while (i < line.length) {
            if (line[i] == '<') {
                if (lower.startsWith(closePattern, i)) {
                    var j = i + closePattern.length
                    while (j < line.length && (line[j] == ' ' || line[j] == '\t')) j++
                    if (j < line.length && line[j] == '>') {
                        if (depth == 0) return i
                        depth--
                        i = j + 1
                        continue
                    }
                } else if (lower.startsWith(openPattern, i)) {
                    val afterName = i + openPattern.length
                    val next = if (afterName < line.length) line[afterName] else '\u0000'
                    // Real tag boundary, not a longer name like `<divider`.
                    if (next == ' ' || next == '\t' || next == '>' || next == '/' || next == '\u0000') {
                        val gt = line.indexOf('>', i)
                        if (gt >= 0) {
                            // `<rootName … />` opens and closes — no net depth change.
                            if (line[gt - 1] != '/') depth++
                            i = gt + 1
                            continue
                        }
                    }
                }
            }
            i++
        }
        return -1
    }

    /** True if [line] contains the [closingSeq] (case-insensitive). */
    private fun lineContainsClosingSeq(line: String, closingSeq: String): Boolean =
        line.contains(closingSeq, ignoreCase = false)

    /** Returns the lowercase root tag name for a type-1 block opener line, or null. */
    private fun type1RootTagOf(line: String): String? {
        val trimmed = line.trimStart(' ')
        for (tag in HTML_BLOCK_TYPE1_TAGS) {
            val prefix = "<$tag"
            if (trimmed.length >= prefix.length &&
                trimmed.substring(0, prefix.length).equals(prefix, ignoreCase = true)
            ) {
                if (trimmed.length == prefix.length) return tag
                val next = trimmed[prefix.length]
                if (next == ' ' || next == '\t' || next == '>' || next == '/' || next == '\n') return tag
            }
        }
        return null
    }

    /**
     * Returns (rootTagName, isClosingTag) for the type-6/7 block opener line, or null if neither.
     * For type 6, the tag must be in HTML_BLOCK_TYPE6_TAGS. For type 7, any valid tag works.
     */
    private fun type6or7RootTagOf(line: String): Pair<String, Boolean>? {
        val trimmed = line.trimStart(' ')
        if (!trimmed.startsWith("<")) return null
        if (trimmed.startsWith("</")) {
            // Closing tag root: extract name
            var i = 2
            if (i >= trimmed.length || !trimmed[i].isLetter()) return null
            val nameStart = i
            while (i < trimmed.length && (trimmed[i].isLetterOrDigit() || trimmed[i] == '-')) i++
            return trimmed.substring(nameStart, i).lowercase() to true
        }
        // Opening tag root
        var i = 1
        if (i >= trimmed.length || !trimmed[i].isLetter()) return null
        val nameStart = i
        while (i < trimmed.length && (trimmed[i].isLetterOrDigit() || trimmed[i] == '-')) i++
        return trimmed.substring(nameStart, i).lowercase() to false
    }

    private suspend fun SemanticEventScope.processInlineChar(char: Char) {
        // Inline raw-text skip (GFM §6.11): drop disallowed-tag body chars; `\n`
        // falls through so the block boundary clears the skip in `flushInline`.
        if (inlineRawSkipTag != null && char != '\n') {
            consumeInlineRawSkipChar(char)
            return
        }
        // Capture the char that immediately preceded any delimiter run that
        // might start (or has started) in this call. We do this opportunistically
        // whenever the inline buffer is empty at the start of the call: if a run
        // begins on this char, runPrevChar is correct; if no run starts, the
        // captured value is harmless because it won't be read until a run resolves.
        if (inlineBuffer.isEmpty()) {
            runPrevChar = prevInlineChar
        }
        try {
            processInlineCharImpl(char)
        } finally {
            // Track the most recent source char for flanking decisions on future
            // delimiter runs. Approximate: when a delimiter run resolves, the next
            // run's true prev would be the resolved run's last delimiter char, but
            // tracking that adds complexity for marginal correctness gain on
            // adjacent-delimiter cases. Most cases are well-served by char.
            prevInlineChar = char
            // If the implementation set `pendingDeferredChar`, the same char will
            // be delivered again by the chunk-loop's re-process protocol. Mark
            // that so the next call can skip duplicate label-source accumulation.
            inlineCharIsReprocess = pendingDeferredChar != null
        }
    }

    private suspend fun SemanticEventScope.processInlineCharImpl(char: Char) {
        // Handle escaping (GFM §6.1: only ASCII punctuation is escapable; before any
        // other char the backslash stays literal).
        if (escaped) {
            escaped = false
            // If we are inside an inline HTML tag accumulation, backslashes are literal.
            if (inlineBuffer.startsWith("<")) {
                inlineBuffer.append(char)
                return
            }
            // Inside a link/image label (`[label]` or `![alt]` before `]`), an
            // escaped char must be added to the source accumulator (used as the
            // ref-lookup key) AND emitted as a text event so it's captured into
            // the label-event buffer for replay on link commit/abort.
            if (inLink && !inLinkUrl && !inLinkRef) {
                if (char in ASCII_PUNCTUATION) {
                    linkText.append(char)
                    +char
                } else {
                    linkText.append('\\')
                    linkText.append(char)
                    +"\\"
                    +char
                }
                return
            }
            if (inImage && !inLinkUrl && !inLinkRef) {
                if (char in ASCII_PUNCTUATION) {
                    imageAlt.append(char)
                    +char
                } else {
                    imageAlt.append('\\')
                    imageAlt.append(char)
                    +"\\"
                    +char
                }
                return
            }
            // Inside a reference label (the second `[…]`), an escaped char
            // belongs to the ref-text accumulator.
            if (inLinkRef) {
                if (char in ASCII_PUNCTUATION) {
                    linkRefText.append(char)
                } else {
                    linkRefText.append('\\')
                    linkRefText.append(char)
                }
                return
            }
            if (char in ASCII_PUNCTUATION) {
                +char
            } else {
                +"\\"
                +char
            }
            return
        }
        // Inside a code span backslashes are literal (GFM §6.3) — skip the escape
        // pre-processing so the backslash flows into the code-span buffer below.
        // Inside a link URL / image URL the backslash is buffered raw and resolved
        // by `applyBackslashEscapes` when the URL/title is finalized at `)`.
        if (char == '\\' && !code && !(inLink && inLinkUrl) && !(inImage && inLinkUrl)) {
            // Inside an inline HTML tag accumulation, the backslash is literal too —
            // preserve it in the buffer so attribute values keep `\` characters.
            if (inlineBuffer.startsWith("<")) {
                inlineBuffer.append('\\')
                return
            }
            // A pending `$` opener whose next char is `\` is a math opener
            // (LaTeX command, e.g. `$\int x$`). `isMathOpenChar('\\')` is true,
            // so defer to the math-open path rather than flushing `$` as literal
            // and consuming `\` as a backslash escape.
            if (inlineBuffer.toString() == "$") {
                inlineBuffer.clear()
                mark("math")
                math = true
                pendingDeferredChar = char
                return
            }
            if (inlineBuffer.isNotEmpty()) {
                +inlineBuffer.toString()
                inlineBuffer.clear()
            }
            escaped = true
            return
        }

        // Inside code — close on a backtick run of *exactly* the opening run length
        // (GFM §6.3). N=1 streams non-backtick content as text events for typewriter UX;
        // backticks are tentatively buffered so a longer run can be recognized as
        // content (e.g. ` `` ` keeps the inner `` `` `` as content of a 1-tick span)
        // rather than closing. N≥2 buffers everything since the strip rule needs full
        // content visibility at close. An opener with no matching closer force-closes
        // at `flushInline` (deliberate streaming divergence — see CLAUDE.md, examples
        // 357/358/359).
        if (code) {
            if (codeRunLength == 1) {
                if (char == '`') {
                    inlineBuffer.append('`')
                    return
                }
                if (inlineBuffer.isNotEmpty()) {
                    if (inlineBuffer.length == 1) {
                        inlineBuffer.clear()
                        unmark("code")
                        code = false
                        codeRunLength = 0
                        pendingDeferredChar = char
                        return
                    }
                    +inlineBuffer.toString()
                    inlineBuffer.clear()
                }
                +char
                return
            }
            // N>=2: buffer all chars; close on a non-backtick when the trailing
            // backtick run length equals the opening run length.
            if (char == '`') {
                inlineBuffer.append('`')
                return
            }
            var trail = 0
            while (trail < inlineBuffer.length &&
                inlineBuffer[inlineBuffer.length - 1 - trail] == '`'
            ) trail++
            if (trail == codeRunLength) {
                var content = inlineBuffer.substring(0, inlineBuffer.length - trail)
                if (content.startsWith(" ") && content.endsWith(" ") && content.length > 1
                    && content.any { it != ' ' }) {
                    content = content.substring(1, content.length - 1)
                }
                +content
                inlineBuffer.clear()
                unmark("code")
                code = false
                codeRunLength = 0
                pendingDeferredChar = char
                return
            }
            inlineBuffer.append(char)
            return
        }

        // Inside math - only look for closing $
        if (math) {
            if (char == '$') {
                if (inlineBuffer.isNotEmpty()) {
                    +inlineBuffer.toString()
                    inlineBuffer.clear()
                }
                unmark("math")
                math = false
            } else {
                +char
            }
            return
        }

        // Reading the second `[…]` of `[label][ref]` / `![alt][ref]`
        // (or the `[]` of `[label][]` / `![alt][]`). Resolves to a
        // `<a>` / `<img>` on the closing `]` if the key matches a
        // registered ref definition; otherwise replays as literal text.
        if (inLinkRef) {
            handleLinkRefChar(char)
            return
        }

        // URL-mode dispatch — image and link share the URL state machine.
        if (inImage && inLinkUrl) {
            handleLinkUrlChar(char, isImage = true)
            return
        }
        if (inLink && inLinkUrl) {
            handleLinkUrlChar(char, isImage = false)
            return
        }

        // Label-mode dispatch (link or image, before the closing `]`/`](url)`).
        // Tentative-close resolution: a previously-seen `]` is now resolved by
        // the current char. The `linkLabelTentativeClose` flag stays set during
        // the shortcut lookup so that an abort can replay the `]` literal; it
        // is cleared on a successful resolution.
        if ((inLink || inImage) && linkLabelTentativeClose) {
            when (char) {
                '(' -> {
                    linkLabelTentativeClose = false
                    enterLinkUrlPhase()
                }
                '[' -> {
                    linkLabelTentativeClose = false
                    inLinkRef = true
                    linkRefText.clear()
                }
                else -> {
                    // Shortcut reference: look up the label as key.
                    val isImage = inImage
                    val labelText =
                        if (isImage) imageAlt.toString() else linkText.toString()
                    val key = normalizeLinkLabel(applyBackslashEscapes(labelText))
                    val def = if (key.isNotEmpty()) linkDefinitions[key] else null
                    if (def != null) {
                        linkLabelTentativeClose = false
                        val labelEvents = redirector.stopCapture()
                        if (isImage) {
                            val alt = labelEvents.toLabelText().ifEmpty { labelText }
                            val attrs = if (def.title != null) {
                                mapOf("src" to def.href, "alt" to alt, "title" to def.title)
                            } else {
                                mapOf("src" to def.href, "alt" to alt)
                            }
                            "img"(attributes = attrs) {}
                        } else {
                            val attrs = if (def.title != null) {
                                mapOf("href" to def.href, "title" to def.title)
                            } else {
                                mapOf("href" to def.href)
                            }
                            mark("a", attributes = attrs)
                            redirector.replay(labelEvents)
                            unmark("a")
                        }
                        resetInlineLinkState()
                    } else {
                        // Abort with `]` still in the flag so the replay
                        // includes the tentative-close bracket.
                        abortInlineLinkOrImage(isImage)
                    }
                    pendingDeferredChar = char
                }
            }
            return
        }

        // `]` in label mode (link or image). When inline state is mid-resolution
        // (inside a code span, math span, or HTML attribute accumulation), the
        // `]` is content; otherwise it either decrements label bracket depth
        // (a balanced `]` inside nested brackets) or, at depth 0, becomes the
        // tentative close marker.
        //
        // EXCEPTION: a pending backtick run in `inlineBuffer` is *about* to
        // open a code span — letting `]` fall through to the standard inline
        // dispatcher commits the code span open and re-delivers `]` as
        // content (so `[foo``]``](/uri)` etc. behave per spec).
        //
        // KNOWN DIVERGENCE: a pending non-backtick delimiter run (e.g. `*`)
        // is flushed as literal text by `flushInlineLabelClose`, NOT routed
        // through the standard delimiter resolver. Routing it through would
        // let the inner `*` close an *outer* em that was open before the
        // label, producing unbalanced events. Spec-correct resolution
        // requires delimiter scoping (label-internal `*` should only see
        // label-local frames in `inlineOpenStack`) — out of scope here.
        val backticksPending =
            inlineBuffer.isNotEmpty() && inlineBuffer.all { it == '`' }
        if ((inLink || inImage) && !inLinkRef &&
            char == ']' && !code && !math && !inlineBuffer.startsWith("<") &&
            !backticksPending
        ) {
            if (linkLabelBracketDepth > 0) {
                // Balanced `]` inside nested brackets — content.
                linkLabelBracketDepth--
                if (inImage) imageAlt.append(']') else linkText.append(']')
                if (inlineBuffer.isNotEmpty()) {
                    +inlineBuffer.toString()
                    inlineBuffer.clear()
                }
                +"]"
                return
            }
            // Outer label close.
            flushInlineLabelClose()
            linkLabelTentativeClose = true
            return
        }

        // `[` in label mode increments bracket depth — the inner `[` is content
        // of the outer label, not a nested link parse. Spec wants `[a [b](u)](u)`
        // to resolve the inner inline link (and abort the outer); tracking that
        // requires recursive label parsing, which is out of scope. Treating
        // `[` as content + balanced-brackets is a contained CommonMark subset
        // covering `[link [foo [bar]]](/uri)` style labels.
        if ((inLink || inImage) && !inLinkRef && !inLinkUrl &&
            char == '[' && !code && !math && !inlineBuffer.startsWith("<")
        ) {
            linkLabelBracketDepth++
            if (inImage) imageAlt.append('[') else linkText.append('[')
            if (inlineBuffer.isNotEmpty()) {
                +inlineBuffer.toString()
                inlineBuffer.clear()
            }
            +"["
            return
        }

        // Track source chars in linkText/imageAlt for ref lookup. Image alt is
        // ALSO derived from captured Text events on commit (for the alt attr),
        // but linkText is needed as the lookup key for shortcut/collapsed refs
        // because event flattening drops literal `*` etc. that source labels
        // keep (CommonMark normalizes the *source* label, not rendered text).
        // The `!inlineCharIsReprocess` guard skips the second delivery of a
        // char going through the pendingDeferredChar re-process protocol —
        // otherwise delimiter resolution (e.g. `*` open on `b`) appends `b`
        // twice (once on first attempt, once on re-process).
        if (!inlineCharIsReprocess) {
            if (inLink && !inLinkUrl && !inLinkRef) {
                linkText.append(char)
                // Fall through to normal inline parsing below — events captured.
            } else if (inImage && !inLinkUrl && !inLinkRef) {
                imageAlt.append(char)
                // Fall through to normal inline parsing below.
            }
        }
        // The reprocess flag is consumed; clear so the next call starts fresh.
        inlineCharIsReprocess = false

        // Autolinks and inline HTML tags
        if (inlineBuffer.startsWith("<")) {
            if (char == '>') {
                val content = inlineBuffer.substring(1)
                inlineBuffer.clear()
                when {
                    // GFM §6.8: URI autolink takes precedence — `<MAILTO:FOO@BAR.BAZ>`
                    // is a URI autolink with scheme `MAILTO`, not a mailto-prefixed email.
                    isValidUriAutolink(content) -> {
                        "a"("href" to normalizeUrlEscapes(content)) {
                            +content
                        }
                    }
                    isValidEmailAutolink(content) -> {
                        "a"("href" to "mailto:$content") {
                            +content
                        }
                    }
                    else -> {
                        val full = "<$content>"
                        val open = tryParseOpenTag(full, 0)
                        val close = if (open == null) tryParseCloseTag(full, 0) else null
                        if (open != null && open.first == full.length &&
                            open.second.name.lowercase() in GFM_DISALLOWED_TAGS
                        ) {
                            // GFM §6.11 disallowed raw HTML reached mid-line. Drop the
                            // element and its raw-text body (emit nothing) rather than
                            // leaking source as literal text — see [inlineRawSkipTag].
                            // A self-closing/void shape has no body to skip.
                            val tag = open.second
                            if (!tag.isSelfClosingOrVoid()) {
                                beginInlineRawSkip(tag.name)
                            }
                        } else if (open != null && open.first == full.length &&
                            open.second.name.lowercase() !in INLINE_HTML_BLOCK_ELEMENTS
                        ) {
                            val tag = open.second
                            mark(tag.name, isTagged = true, attributes = tag.attributes ?: emptyMap())
                            if (tag.isSelfClosingOrVoid()) {
                                unmark(tag.name, isTagged = true)
                            } else {
                                inlineOpenStack.addLast(InlineOpenFrame(tag.name, isTagged = true))
                            }
                        } else if (close != null && close.first == full.length &&
                            close.second.name.lowercase() !in INLINE_HTML_BLOCK_ELEMENTS &&
                            close.second.name.lowercase() !in GFM_DISALLOWED_TAGS
                        ) {
                            // Match against the most recent open whose name equals (case-insensitive).
                            // `closeInlineDownTo` pops every inline frame above the match —
                            // including non-HTML emphasis frames opened *inside* the HTML element
                            // — emitting their balancing `unmark`s LIFO. Orphan close (no matching
                            // open): fall back to literal text rather than emit an unbalanced
                            // `unmark`.
                            val name = close.second.name
                            if (!closeInlineDownTo(name, isTagged = true, nameIgnoreCase = true)) {
                                +"<${applyBackslashEscapes(content)}>"
                            }
                        } else {
                            // Fallback: not a valid tag — apply backslash-escape pass to the
                            // raw content and emit as text. (Matches CommonMark: failed HTML
                            // candidates undergo normal inline backslash-escape processing.)
                            +"<${applyBackslashEscapes(content)}>"
                        }
                    }
                }
                return
            } else {
                inlineBuffer.append(char)
                return
            }
        }

        // Entity / numeric character reference accumulation. Once an `&` has
        // started an entity ref, every subsequent char either extends the body,
        // commits via `;`, or aborts (replay raw + reprocess current char).
        if (inEntityRef) {
            handleEntityRefChar(char)
            return
        }

        // Handle special characters
        // IMPORTANT: Buffer-based checks must come BEFORE new character checks
        // so that pending formatting markers are processed before the new char
        when {
            // First, check if buffer contains formatting markers that should be resolved.
            // Backtick run resolution (GFM §6.1): a run of N backticks followed by a
            // non-backtick opens an inline code span with run length N.
            inlineBuffer.isNotEmpty() && inlineBuffer.all { it == '`' } && char != '`' -> {
                val n = inlineBuffer.length
                inlineBuffer.clear()
                code = true
                codeRunLength = n
                mark("code")
                if (n == 1) {
                    +char
                } else {
                    pendingDeferredChar = char
                }
            }
            inlineBuffer.toString() == "***" && char != '*' -> {
                inlineBuffer.clear()
                resolveEmphasisRun('*', 3, char)
                pendingDeferredChar = char
            }
            inlineBuffer.toString() == "**" && char != '*' -> {
                inlineBuffer.clear()
                resolveEmphasisRun('*', 2, char)
                pendingDeferredChar = char
            }
            inlineBuffer.toString() == "*" && char != '*' -> {
                inlineBuffer.clear()
                resolveEmphasisRun('*', 1, char)
                pendingDeferredChar = char
            }
            // Backtick run accumulation: keep appending backticks until a non-backtick
            // arrives, at which point the buffer-resolution branch above opens code.
            char == '`' -> {
                if (inlineBuffer.isNotEmpty() && !inlineBuffer.all { it == '`' }) {
                    +inlineBuffer.toString()
                    inlineBuffer.clear()
                }
                inlineBuffer.append('`')
            }
            char == '*' -> {
                if (inlineBuffer.endsWith("*")) {
                    inlineBuffer.append('*')
                } else {
                    if (inlineBuffer.isNotEmpty()) {
                        +inlineBuffer.toString()
                        inlineBuffer.clear()
                    }
                    inlineBuffer.append('*')
                }
            }
            // Underscore buffer checks (before char == '_')
            inlineBuffer.toString() == "___" && char != '_' -> {
                inlineBuffer.clear()
                resolveEmphasisRun('_', 3, char)
                pendingDeferredChar = char
            }
            inlineBuffer.toString() == "__" && char != '_' -> {
                inlineBuffer.clear()
                resolveEmphasisRun('_', 2, char)
                pendingDeferredChar = char
            }
            inlineBuffer.toString() == "_" && char != '_' -> {
                inlineBuffer.clear()
                resolveEmphasisRun('_', 1, char)
                pendingDeferredChar = char
            }
            // Tilde buffer checks (before char == '~')
            inlineBuffer.toString() == "~~" && char != '~' -> {
                inlineBuffer.clear()
                if (strikethrough) {
                    unmark("del")
                    strikethrough = false
                } else {
                    mark("del")
                    strikethrough = true
                }
                pendingDeferredChar = char
            }
            inlineBuffer.toString() == "~" && char != '~' -> {
                inlineBuffer.clear()
                if (strikethrough) {
                    unmark("del")
                    strikethrough = false
                } else {
                    mark("del")
                    strikethrough = true
                }
                pendingDeferredChar = char
            }
            // Now handle new character cases
            char == '_' -> {
                if (inlineBuffer.endsWith("__")) {
                    inlineBuffer.append('_')
                } else if (inlineBuffer.endsWith("_")) {
                    inlineBuffer.append('_')
                } else {
                    if (inlineBuffer.isNotEmpty()) {
                        +inlineBuffer.toString()
                        inlineBuffer.clear()
                    }
                    inlineBuffer.append('_')
                }
            }
            char == '~' -> {
                if (inlineBuffer.endsWith("~")) {
                    inlineBuffer.append('~')
                } else {
                    if (inlineBuffer.isNotEmpty()) {
                        +inlineBuffer.toString()
                        inlineBuffer.clear()
                    }
                    inlineBuffer.append('~')
                }
            }
            char == '^' -> {
                if (inlineBuffer.isNotEmpty()) {
                    +inlineBuffer.toString()
                    inlineBuffer.clear()
                }
                if (superscript) {
                    unmark("sup")
                    superscript = false
                } else {
                    mark("sup")
                    superscript = true
                }
            }
            // Equals buffer checks (before char == '=')
            inlineBuffer.toString() == "==" && char != '=' -> {
                inlineBuffer.clear()
                if (highlight) {
                    unmark("mark")
                    highlight = false
                } else {
                    mark("mark")
                    highlight = true
                }
                pendingDeferredChar = char
            }
            inlineBuffer.toString() == "=" && char != '=' -> {
                inlineBuffer.clear()
                +"="
                pendingDeferredChar = char
            }
            // Now handle new equals
            char == '=' -> {
                if (inlineBuffer.endsWith("=")) {
                    inlineBuffer.append('=')
                } else {
                    if (inlineBuffer.isNotEmpty()) {
                        +inlineBuffer.toString()
                        inlineBuffer.clear()
                    }
                    inlineBuffer.append('=')
                }
            }
            // Inline math (`$…$`): defer the open decision until we see the
            // next char. A bare `$` followed by punctuation, whitespace, or
            // a digit is *not* a math opener (GFM §6.14 example 675 keeps
            // `hello $.;'there` as plain text; GitHub-style math also treats
            // `$5` as currency, never math). Open only when followed by a
            // letter, `\` (LaTeX command), or `{` (group). Buffer-resolution
            // branches below handle the dispatch on the next char.
            inlineBuffer.toString() == "$" && isMathOpenChar(char) -> {
                inlineBuffer.clear()
                mark("math")
                math = true
                pendingDeferredChar = char
            }
            inlineBuffer.toString() == "$" && !isMathOpenChar(char) -> {
                +"$"
                inlineBuffer.clear()
                pendingDeferredChar = char
            }
            char == '$' -> {
                if (inlineBuffer.isNotEmpty()) {
                    +inlineBuffer.toString()
                    inlineBuffer.clear()
                }
                inlineBuffer.append('$')
            }
            // Exclamation buffer checks (before char == '[' for image syntax)
            inlineBuffer.toString() == "!" && char == '[' -> {
                inlineBuffer.clear()
                inImage = true
                openLinkLabelCapture()
            }
            inlineBuffer.toString() == "!" && char != '[' -> {
                inlineBuffer.clear()
                +"!"
                pendingDeferredChar = char
            }
            // Now handle bracket and exclamation
            char == '[' -> {
                if (inlineBuffer.isNotEmpty()) {
                    +inlineBuffer.toString()
                    inlineBuffer.clear()
                }
                inLink = true
                openLinkLabelCapture()
            }
            char == '!' -> {
                if (inlineBuffer.isNotEmpty()) {
                    +inlineBuffer.toString()
                    inlineBuffer.clear()
                }
                inlineBuffer.append('!')
            }
            char == '<' -> {
                if (inlineBuffer.isNotEmpty()) {
                    +inlineBuffer.toString()
                    inlineBuffer.clear()
                }
                inlineBuffer.append('<')
            }
            char == '&' -> {
                if (inlineBuffer.isNotEmpty()) {
                    +inlineBuffer.toString()
                    inlineBuffer.clear()
                }
                inEntityRef = true
                entityBuffer.clear()
            }
            else -> {
                if (inlineBuffer.isNotEmpty()) {
                    +inlineBuffer.toString()
                    inlineBuffer.clear()
                }
                +char
            }
        }
    }

    /**
     * Step the entity-ref accumulator on [char] while [inEntityRef] is true.
     * On a valid commit (`;` after a recognized body), emits the decoded chars
     * as plain text — they do NOT re-enter inline parsing, so e.g. `&#42;`
     * becomes literal `*` rather than an emphasis delimiter (GFM example 333).
     * On any other terminator (invalid char, no decode), replays `&body` as
     * literal text and reprocesses the current char via [pendingDeferredChar].
     */
    private suspend fun SemanticEventScope.handleEntityRefChar(char: Char) {
        if (char == ';') {
            val decoded = tryDecodeEntityBody(entityBuffer.toString())
            if (decoded != null) {
                +decoded
            } else {
                +"&$entityBuffer;"
            }
            entityBuffer.clear()
            inEntityRef = false
            return
        }
        val accept = char.isLetterOrDigit() ||
            (char == '#' && entityBuffer.isEmpty()) ||
            ((char == 'x' || char == 'X') &&
                entityBuffer.length == 1 && entityBuffer[0] == '#')
        if (accept) {
            entityBuffer.append(char)
            // Cap body length defensively: longest valid named entity is short
            // and numeric refs cap at 7 digits + `#x`. Anything past 32 chars
            // is definitely not a valid ref — abort to avoid unbounded buffering.
            if (entityBuffer.length > 32) {
                +"&$entityBuffer"
                entityBuffer.clear()
                inEntityRef = false
            }
            return
        }
        +"&$entityBuffer"
        entityBuffer.clear()
        inEntityRef = false
        pendingDeferredChar = char
    }

    /**
     * Force-close an in-flight entity ref at a block/line boundary by replaying
     * `&body` as literal text. Called from [flushInline] so an unterminated
     * `&copy` (no `;`) survives as source rather than vanishing.
     */
    private suspend fun SemanticEventScope.abortEntityRef() {
        if (!inEntityRef) return
        +"&$entityBuffer"
        entityBuffer.clear()
        inEntityRef = false
    }

    /**
     * Transition from `[label](` (or `![alt](`) into URL parsing — sets
     * `inLinkUrl = true` and resets the URL-phase state machine.
     */
    private fun enterLinkUrlPhase() {
        inLinkUrl = true
        linkUrlPhase = LinkUrlPhase.PreDest
        linkParenDepth = 0
        linkEscape = false
        linkUrlSource.clear()
    }

    /**
     * Begin capturing inline events for the current link/image label. Records
     * the [inlineOpenStack] watermark so [flushInlineLabelClose] can later
     * close only inline state opened *during* the label, leaving outer state
     * intact.
     */
    private fun openLinkLabelCapture() {
        linkLabelOuterStackDepth = inlineOpenStack.size
        linkLabelOuterStrikethrough = strikethrough
        linkLabelOuterHighlight = highlight
        linkLabelOuterSuperscript = superscript
        linkLabelBracketDepth = 0
        redirector.startCapture()
    }

    /**
     * Flatten captured label events into a plain-text string. Used both for
     * (a) ref-resolution lookup keys (with [normalizeLinkLabel] applied
     * downstream) and (b) the `alt` attribute of `<img>` (CommonMark §6.5
     * recommends rendering as plain text only). Marks/unmarks contribute
     * nothing — only [SemanticEvent.Text] payloads are concatenated.
     */
    private fun List<SemanticEvent>.toLabelText(): String {
        val sb = StringBuilder()
        for (e in this) {
            if (e is Text) sb.append(e.text)
        }
        return sb.toString()
    }

    /**
     * Drain inline state opened *inside* the current link/image label down to
     * [linkLabelOuterStackDepth]. Closes em/strong/inline-HTML/code/strike/
     * mark/sup that were opened during label parsing, leaving any outer state
     * (i.e. an em already open *before* the `[`) untouched. Called both on
     * commit (so the captured event buffer is balanced before replay inside
     * `<a>`) and on abort (same buffer is replayed as bare events).
     */
    private suspend fun SemanticEventScope.flushInlineLabelClose() {
        if (inEntityRef) abortEntityRef()
        if (code) {
            // N=1 close: a buffered single backtick counts as the close marker;
            // anything longer is content. Otherwise force-close (DIVERGENCE
            // matching flushInline behavior — see CLAUDE.md).
            if (codeRunLength == 1) {
                if (inlineBuffer.length == 1) {
                    inlineBuffer.clear()
                } else if (inlineBuffer.isNotEmpty()) {
                    +inlineBuffer.toString()
                    inlineBuffer.clear()
                }
            } else {
                var trail = 0
                while (trail < inlineBuffer.length &&
                    inlineBuffer[inlineBuffer.length - 1 - trail] == '`'
                ) trail++
                if (trail == codeRunLength) {
                    var content = inlineBuffer.substring(0, inlineBuffer.length - trail)
                    if (content.startsWith(" ") && content.endsWith(" ") &&
                        content.length > 1 && content.any { it != ' ' }
                    ) {
                        content = content.substring(1, content.length - 1)
                    }
                    +content
                } else if (inlineBuffer.isNotEmpty()) {
                    +inlineBuffer.toString()
                }
                inlineBuffer.clear()
            }
            unmark("code")
            code = false
            codeRunLength = 0
        }
        // Close only the boolean-tracked inline state opened *inside* this label
        // (see [linkLabelOuterSuperscript] et al.). A `del`/`mark`/`sup` opened
        // before the `[` stays open — its closer arrives after the label — so its
        // `unmark` is not captured into the label buffer (which would cross the
        // link nesting and break the stream).
        if (strikethrough && !linkLabelOuterStrikethrough) {
            unmark("del"); strikethrough = false
            // `del`/`mark` resolve their closing run on the *next* char (to size
            // the run), but at a label's end the `]` is not that char — so the
            // trailing `~`/`~~`/`=`/`==` (or a longer homogeneous run) sits
            // unresolved in inlineBuffer. Absorb the *whole* run here, else it
            // flushes as literal text inside the committed link and grows
            // *unboundedly* on every round-trip (`[==foo=]` → `[==foo===]` → …;
            // `[~~del~~~]` → `[~~del~~~~~]` → …). Any trailing dangling delimiter
            // run here is malformed noise, so dropping it is the round-trip-stable
            // choice. `sup` resolves eagerly (never buffered), so it needs no guard.
            val run = inlineBuffer.toString()
            if (run.isNotEmpty() && run.all { it == '~' }) inlineBuffer.clear()
        }
        if (highlight && !linkLabelOuterHighlight) {
            unmark("mark"); highlight = false
            val run = inlineBuffer.toString()
            if (run.isNotEmpty() && run.all { it == '=' }) inlineBuffer.clear()
        }
        if (superscript && !linkLabelOuterSuperscript) { unmark("sup"); superscript = false }
        // A pending delimiter run in inlineBuffer that sits in closing position at
        // the label's end (e.g. the `**` of `[**bold**]`, or the trailing `*` of
        // an image-in-link label like `[![a](u*)*]`) closes the matching emphasis
        // frame *opened inside this label* (above the watermark), LIFO and
        // innermost-first. This is the label-scoped counterpart of the normal
        // delimiter resolver: it only consults label-local frames, so an inner
        // closer can never pair with an em/strong opened *before* the `[`. Without
        // it the closer was emitted as literal text inside the still-open span, so
        // the span's delimiter run grew by one on every render→parse round-trip
        // (the unbounded `…)*` → `…)**` instability of the Brave SERP dump).
        closeLabelLocalEmphasisRun()
        // Any leftover run (no matching label-local frame) flushes as literal text
        // — same fallback the rest of the parser uses when a run can't resolve.
        if (inlineBuffer.isNotEmpty()) {
            +inlineBuffer.toString()
            inlineBuffer.clear()
        }
        // Drain inlineOpenStack down to the watermark (LIFO close).
        while (inlineOpenStack.size > linkLabelOuterStackDepth) {
            val frame = inlineOpenStack.removeLast()
            unmark(frame.name, isTagged = frame.isTagged)
        }
    }

    /**
     * Consume a trailing emphasis-delimiter run in [inlineBuffer] by closing the
     * matching label-local frames at the top of [inlineOpenStack] (those above
     * [linkLabelOuterStackDepth], i.e. opened inside the current label). Each
     * close pops one frame and removes the delimiter characters it consumed from
     * the run; matching stops at the first frame the run can't close, leaving the
     * remainder for the literal-flush fallback. Only `em`/`strong` live on
     * [inlineOpenStack], so only pure `*`/`_` runs can match — `~`/`=`/`^`
     * (`del`/`mark`/`sup`) are boolean-flag state closed by the caller before this
     * runs, so a run of those is left for the literal-flush / buffer-clear path.
     */
    private suspend fun SemanticEventScope.closeLabelLocalEmphasisRun() {
        if (inlineBuffer.isEmpty()) return
        val delim = inlineBuffer[0]
        if (delim !in "*_" || !inlineBuffer.all { it == delim }) return
        while (inlineBuffer.isNotEmpty() &&
            inlineOpenStack.size > linkLabelOuterStackDepth
        ) {
            val frame = inlineOpenStack.last()
            if (frame.isTagged) break
            // `*` and `_` are distinct delimiter types and never pair (CommonMark
            // §6.2): a `_` run must not close a `*`-opened em (or vice versa). Leave
            // the mismatched run for the literal-flush fallback (`[*foo_](u)` keeps
            // the `_` as content → `<em>foo_</em>`, not a silently-dropped closer).
            // `delimChar` is non-null here: tagged frames (delimChar == null) already
            // broke above, and every non-tagged frame is pushed via openInlineEmphasis
            // with a non-null delimChar.
            if (frame.delimChar != delim) break
            // Only `em`/`strong` live on inlineOpenStack — `del`/`mark`/`sup` are
            // tracked as boolean flags and never pushed here, so they cannot match.
            // `delim` is already constrained to `*`/`_` by the guard above.
            val need = when (frame.name) {
                "em" -> 1
                "strong" -> 2
                else -> break
            }
            if (inlineBuffer.length < need) break
            inlineBuffer.deleteRange(inlineBuffer.length - need, inlineBuffer.length)
            inlineOpenStack.removeLast()
            unmark(frame.name)
        }
    }

    /**
     * Normalize a link reference label per CommonMark §4.7: trim, collapse
     * internal whitespace runs to a single space, and Unicode-fold case so
     * that `[Foo bar]` matches `[foo  BAR]`.
     */
    private fun normalizeLinkLabel(label: String): String {
        val sb = StringBuilder(label.length)
        var sawSpace = false
        var sawNonSpace = false
        for (c in label) {
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                sawSpace = sawNonSpace
            } else {
                if (sawSpace) {
                    sb.append(' ')
                    sawSpace = false
                }
                sb.append(c)
                sawNonSpace = true
            }
        }
        return sb.toString().lowercase()
    }

    /**
     * Try to recognize [line] as a single-line link reference definition
     * (CommonMark §4.7). On success, store a normalized [LinkDefinition] in
     * [linkDefinitions] (first-wins on duplicate labels) and return `true`;
     * the caller skips the `beginParagraph` fallback.
     *
     * STREAMING DIVERGENCE: only single-line definitions are recognized.
     * Multi-line shapes — `[label]:\n  /url`, `[label]: /url\n  "title"`,
     * label running across lines — fall through to paragraph processing.
     */
    private fun tryParseLinkDefinition(line: String): Boolean {
        // Up to 3 leading spaces of indent; 4+ would be indented code block.
        val stripped = line.trimStart(' ')
        val indent = line.length - stripped.length
        if (indent > 3) return false
        if (!stripped.startsWith("[")) return false

        // Scan to matching unescaped `]`. Unescaped `[` inside the label is
        // not allowed by spec; bail out if we see one.
        var i = 1
        val labelEnd: Int
        while (true) {
            if (i >= stripped.length) return false
            val c = stripped[i]
            if (c == '\\' && i + 1 < stripped.length) { i += 2; continue }
            if (c == ']') { labelEnd = i; break }
            if (c == '[') return false
            i++
        }

        val labelRaw = stripped.substring(1, labelEnd)
        // Per spec, the label must have at least one non-whitespace char.
        if (labelRaw.isBlank()) return false

        if (labelEnd + 1 >= stripped.length || stripped[labelEnd + 1] != ':') return false

        var j = labelEnd + 2
        while (j < stripped.length && (stripped[j] == ' ' || stripped[j] == '\t')) j++
        if (j >= stripped.length) return false

        val destResult = parseLinkDefDestination(stripped, j) ?: return false
        val (rawDest, destEnd) = destResult

        var k = destEnd
        while (k < stripped.length && (stripped[k] == ' ' || stripped[k] == '\t')) k++

        val title: String?
        if (k < stripped.length) {
            // CommonMark §4.7: title must be separated from destination by at
            // least one whitespace char. Without separation `(baz)` immediately
            // after `<bar>` is trailing content, not a title — invalidates the
            // definition.
            if (k == destEnd) return false
            val titleResult = parseLinkDefTitle(stripped, k) ?: return false
            val (rawTitle, titleEnd) = titleResult
            // Whatever follows the title must be whitespace only.
            for (t in titleEnd until stripped.length) {
                if (stripped[t] != ' ' && stripped[t] != '\t') return false
            }
            title = applyBackslashEscapes(decodeEntities(rawTitle))
        } else {
            title = null
        }

        // Backslash escapes inside the label decode before normalization so a
        // definition `[bar\\]` matches a usage `[bar\\]` (both reduce to key
        // `bar\` after escape + case-fold).
        val key = normalizeLinkLabel(applyBackslashEscapes(labelRaw))
        if (key.isEmpty()) return false
        if (key !in linkDefinitions) {
            val href = percentEncodeNonAscii(
                normalizeUrlEscapes(applyBackslashEscapes(decodeEntities(rawDest)))
            )
            linkDefinitions[key] = LinkDefinition(href, title)
        }
        return true
    }

    /** Parse a link destination at `s[start..]`. Returns (raw, indexAfter). */
    private fun parseLinkDefDestination(s: String, start: Int): Pair<String, Int>? {
        if (start >= s.length) return null
        if (s[start] == '<') {
            // Angle-bracketed: closes at first unescaped `>`. Newlines and `<`
            // are forbidden inside.
            var i = start + 1
            val sb = StringBuilder()
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    sb.append(c); sb.append(s[i + 1]); i += 2; continue
                }
                if (c == '>') return sb.toString() to (i + 1)
                if (c == '<' || c == '\n') return null
                sb.append(c); i++
            }
            return null
        }
        // Plain destination: ends at unescaped whitespace or unbalanced `)`.
        var i = start
        val sb = StringBuilder()
        var depth = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                sb.append(c); sb.append(s[i + 1]); i += 2; continue
            }
            if (c == ' ' || c == '\t') break
            if (c == '(') { depth++; sb.append(c); i++; continue }
            if (c == ')') {
                if (depth == 0) break
                depth--; sb.append(c); i++; continue
            }
            sb.append(c); i++
        }
        if (depth != 0 || sb.isEmpty()) return null
        return sb.toString() to i
    }

    /** Parse a link title at `s[start]..`. Returns (raw, indexAfter). */
    private fun parseLinkDefTitle(s: String, start: Int): Pair<String, Int>? {
        if (start >= s.length) return null
        val open = s[start]
        val close = when (open) { '"' -> '"'; '\'' -> '\''; '(' -> ')'; else -> return null }
        var i = start + 1
        val sb = StringBuilder()
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                sb.append(c); sb.append(s[i + 1]); i += 2; continue
            }
            if (c == close) return sb.toString() to (i + 1)
            if (open == '(' && c == '(') return null
            sb.append(c); i++
        }
        return null
    }

    /**
     * Drive the URL/title state machine for one char. Shared by inline links
     * and inline images: [isImage] selects between `linkUrl`/`linkTitle` (link)
     * and `imageUrl`/`linkTitle` (image — image alt is already in `imageAlt`).
     *
     * The machine runs through phases [LinkUrlPhase.PreDest] → (DestAngle | DestPlain)
     * → BetweenDestTitle → (TitleDouble | TitleSingle | TitleParen) → AfterTitle.
     * Phase transitions are documented inline; on any malformed transition the
     * link/image aborts and the consumed source is replayed as literal text.
     *
     * Backslash-before-punctuation is captured at accumulation time but the
     * `\X` pair is preserved literally in the destination/title buffers and
     * decoded by [applyBackslashEscapes] at finalize time. The [linkEscape]
     * flag suppresses termination logic for the char immediately after `\`.
     */
    private suspend fun SemanticEventScope.handleLinkUrlChar(
        char: Char,
        isImage: Boolean
    ) {
        linkUrlSource.append(char)
        val dest = if (isImage) imageUrl else linkUrl

        // Char immediately after an unescaped `\` is taken literally — append to
        // the active accumulator and clear the flag without consulting termination.
        if (linkEscape) {
            linkEscape = false
            when (linkUrlPhase) {
                LinkUrlPhase.DestAngle, LinkUrlPhase.DestPlain -> dest.append(char)
                LinkUrlPhase.TitleDouble, LinkUrlPhase.TitleSingle, LinkUrlPhase.TitleParen ->
                    linkTitle.append(char)
                else -> {} // unreachable: \ only sets escape inside dest/title phases
            }
            return
        }

        when (linkUrlPhase) {
            PreDest -> when (char) {
                ' ', '\t' -> {} // skip leading whitespace
                ')' -> finalizeInlineLink(isImage) // empty destination — `[foo]()`
                '<' -> linkUrlPhase = LinkUrlPhase.DestAngle
                '\\' -> {
                    linkEscape = true
                    linkUrlPhase = LinkUrlPhase.DestPlain
                    dest.append('\\')
                }
                '(' -> {
                    linkUrlPhase = LinkUrlPhase.DestPlain
                    linkParenDepth = 1
                    dest.append('(')
                }
                else -> {
                    linkUrlPhase = LinkUrlPhase.DestPlain
                    dest.append(char)
                }
            }
            DestAngle -> when (char) {
                '\\' -> {
                    linkEscape = true
                    dest.append('\\')
                }
                '>' -> linkUrlPhase = LinkUrlPhase.BetweenDestTitle
                '<' -> abortInlineLinkOrImage(isImage)
                else -> dest.append(char)
            }
            DestPlain -> when (char) {
                '\\' -> {
                    linkEscape = true
                    dest.append('\\')
                }
                '(' -> {
                    linkParenDepth++
                    dest.append('(')
                }
                ')' -> {
                    if (linkParenDepth == 0) finalizeInlineLink(isImage)
                    else {
                        linkParenDepth--
                        dest.append(')')
                    }
                }
                ' ', '\t' -> {
                    // Plain destination ends at first unescaped whitespace; what
                    // follows must be a title (or the closing `)`).
                    if (linkParenDepth != 0) abortInlineLinkOrImage(isImage)
                    else linkUrlPhase = LinkUrlPhase.BetweenDestTitle
                }
                else -> dest.append(char)
            }
            BetweenDestTitle -> when (char) {
                ' ', '\t' -> {}
                ')' -> finalizeInlineLink(isImage)
                '"' -> linkUrlPhase = LinkUrlPhase.TitleDouble
                '\'' -> linkUrlPhase = LinkUrlPhase.TitleSingle
                '(' -> linkUrlPhase = LinkUrlPhase.TitleParen
                else -> abortInlineLinkOrImage(isImage)
            }
            TitleDouble -> when (char) {
                '\\' -> { linkEscape = true; linkTitle.append('\\') }
                '"' -> linkUrlPhase = LinkUrlPhase.AfterTitle
                else -> linkTitle.append(char)
            }
            TitleSingle -> when (char) {
                '\\' -> { linkEscape = true; linkTitle.append('\\') }
                '\'' -> linkUrlPhase = LinkUrlPhase.AfterTitle
                else -> linkTitle.append(char)
            }
            TitleParen -> when (char) {
                '\\' -> { linkEscape = true; linkTitle.append('\\') }
                ')' -> linkUrlPhase = LinkUrlPhase.AfterTitle
                '(' -> abortInlineLinkOrImage(isImage)
                else -> linkTitle.append(char)
            }
            AfterTitle -> when (char) {
                ' ', '\t' -> {}
                ')' -> finalizeInlineLink(isImage)
                else -> abortInlineLinkOrImage(isImage)
            }
        }
    }

    private suspend fun SemanticEventScope.abortInlineLinkOrImage(isImage: Boolean) {
        if (isImage) abortInlineImage() else abortInlineLink()
    }

    /**
     * Read the second `[…]` of a full or collapsed reference link/image
     * (`[label][ref]` / `[label][]` / `![alt][ref]` / `![alt][]`). The
     * caller has already consumed `[label][` (or `![alt][`) and set
     * [inLinkRef] = true. Each char extends [linkRefText] until `]` closes
     * the reference. On close: a non-empty ref text is the lookup key (full
     * reference); an empty ref text falls back to the original label/alt
     * (collapsed reference). On match, emit `<a>` / `<img>`. On miss, replay
     * the entire source as literal text.
     *
     * Bail-out: an inner `[` invalidates the reference (per spec, ref labels
     * forbid unescaped `[`); replay as literal source.
     */
    private suspend fun SemanticEventScope.handleLinkRefChar(char: Char) {
        val isImage = inImage
        when (char) {
            ']' -> {
                val refRaw = linkRefText.toString()
                val labelText = if (isImage) imageAlt.toString() else linkText.toString()
                val key = if (refRaw.isBlank()) {
                    normalizeLinkLabel(applyBackslashEscapes(labelText))
                } else {
                    normalizeLinkLabel(applyBackslashEscapes(refRaw))
                }
                val def = if (key.isNotEmpty()) linkDefinitions[key] else null
                if (def != null) {
                    // Resolved — replay captured label events inside <a>/<img>.
                    val labelEvents = redirector.stopCapture()
                    if (isImage) {
                        val alt = labelEvents.toLabelText().ifEmpty { labelText }
                        val attrs = if (def.title != null) {
                            mapOf("src" to def.href, "alt" to alt, "title" to def.title)
                        } else {
                            mapOf("src" to def.href, "alt" to alt)
                        }
                        "img"(attributes = attrs) {}
                    } else {
                        val attrs = if (def.title != null) {
                            mapOf("href" to def.href, "title" to def.title)
                        } else {
                            mapOf("href" to def.href)
                        }
                        mark("a", attributes = attrs)
                        redirector.replay(labelEvents)
                        unmark("a")
                    }
                    resetInlineLinkState()
                } else {
                    // Not resolved — replay label events surrounded by literal
                    // brackets and the reference label as text.
                    val labelEvents = redirector.stopCapture()
                    val prefix = if (isImage) "!" else ""
                    +"$prefix["
                    redirector.replay(labelEvents)
                    +applyBackslashEscapes("][$refRaw]")
                    resetInlineLinkState()
                }
            }
            '[' -> {
                // Unescaped `[` invalidates the reference label. Replay source.
                val labelEvents = redirector.stopCapture()
                val prefix = if (inImage) "!" else ""
                +"$prefix["
                redirector.replay(labelEvents)
                +applyBackslashEscapes("][${linkRefText}")
                resetInlineLinkState()
                pendingDeferredChar = char
            }
            else -> linkRefText.append(char)
        }
    }

    /**
     * Commit the in-flight inline link `[label](url "title")` or image
     * `![alt](src "title")`. Decodes entity refs first then applies backslash
     * escapes (matches the order in the rest of the parser); URLs additionally
     * percent-encode URL-unsafe ASCII (e.g. `\` → `%5C`, `"` → `%22`) and any
     * non-ASCII bytes (UTF-8).
     *
     * The label content events captured between `[` and `]` are stopped from
     * the redirector and replayed *inside* the `<a>` mark for links. For
     * images, GFM/CommonMark renders `<img alt="…">` with plain-text alt, so
     * the events are flattened to text via [toLabelText] (markup discarded).
     * Resets all link state.
     */
    private suspend fun SemanticEventScope.finalizeInlineLink(isImage: Boolean) {
        val rawDest = if (isImage) imageUrl.toString() else linkUrl.toString()
        val decodedDest = applyBackslashEscapes(decodeEntities(rawDest))
        val href = percentEncodeNonAscii(normalizeUrlEscapes(decodedDest))
        val title = applyBackslashEscapes(decodeEntities(linkTitle.toString()))
        val labelEvents = redirector.stopCapture()
        if (isImage) {
            val alt = labelEvents.toLabelText().ifEmpty { imageAlt.toString() }
            val attrs = if (title.isNotEmpty()) {
                mapOf("src" to href, "alt" to alt, "title" to title)
            } else {
                mapOf("src" to href, "alt" to alt)
            }
            "img"(attributes = attrs) {}
        } else {
            val attrs = if (title.isNotEmpty()) {
                mapOf("href" to href, "title" to title)
            } else {
                mapOf("href" to href)
            }
            mark("a", attributes = attrs)
            // Replay captured label events *inside* the <a> mark. Capture is
            // no longer active, so they go straight to the downstream collector.
            redirector.replay(labelEvents)
            unmark("a")
        }
        resetInlineLinkState()
    }

    private fun resetInlineLinkState() {
        inLink = false
        inLinkUrl = false
        inImage = false
        inLinkRef = false
        linkText.clear()
        linkUrl.clear()
        linkTitle.clear()
        imageAlt.clear()
        imageUrl.clear()
        linkUrlSource.clear()
        linkRefText.clear()
        linkUrlPhase = LinkUrlPhase.PreDest
        linkParenDepth = 0
        linkEscape = false
        linkLabelTentativeClose = false
        linkLabelOuterStackDepth = 0
        linkLabelBracketDepth = 0
        // Outer del/mark/sup snapshots taken in openLinkLabelCapture. Cleared here
        // for an explicit invariant: today openLinkLabelCapture always overwrites
        // them before flushInlineLabelClose reads them, but resetting keeps a future
        // abort path that calls flushInlineLabelClose directly from reading a stale
        // snapshot left by a prior committed link.
        linkLabelOuterStrikethrough = false
        linkLabelOuterHighlight = false
        linkLabelOuterSuperscript = false
    }

    /**
     * Replay an unresolved inline link `[label]` (or `[label](partial_url…`) as
     * literal text and reset link state. Called when the parser determines that
     * a `[` did not start a real link — either because `[label]` was not
     * followed by `(`, or because URL/title parsing hit a malformed transition,
     * or because a block boundary (`flushInline`) closed the paragraph mid-link.
     *
     * Phase 3a behavior: the captured label events are replayed *as-is* (so
     * inline markup like `[*foo*]` renders the `<em>foo</em>` even when the
     * brackets stay literal), surrounded by `[` and `]` text events. URL
     * source after `](` is replayed with backslash escapes applied. HTML tag
     * detection inside the replay is *not* re-run — that would require
     * re-feeding through the inline char processor and is currently a
     * streaming divergence (see Gfm_06_06_Test ex. 501, 504).
     */
    private suspend fun SemanticEventScope.abortInlineLink() {
        // Order matters: flushInlineLabelClose may emit closing marks for
        // inline state still open inside the label (e.g. an unmatched `<bar>`
        // HTML opener). Those emissions must enter the capture buffer so the
        // replay is balanced; doing them after `stopCapture` would leak the
        // unmark to the downstream collector before the `[` literal.
        flushInlineLabelClose()
        val labelEvents = if (redirector.isCapturing) redirector.stopCapture() else emptyList()
        +"["
        redirector.replay(labelEvents)
        if (linkLabelTentativeClose || inLinkUrl) {
            +"]"
            linkLabelTentativeClose = false
        }
        if (inLinkUrl) {
            +applyBackslashEscapes("($linkUrlSource")
        }
        resetInlineLinkState()
    }

    /**
     * Image counterpart of [abortInlineLink]. Replays `![alt]` (or
     * `![alt](partial_url…`) as literal text. The label events are replayed
     * the same way as for links (so inline markup inside the alt label
     * renders correctly when the image fails to resolve).
     */
    private suspend fun SemanticEventScope.abortInlineImage() {
        flushInlineLabelClose()
        val labelEvents = if (redirector.isCapturing) redirector.stopCapture() else emptyList()
        +"!["
        redirector.replay(labelEvents)
        if (linkLabelTentativeClose || inLinkUrl) {
            +"]"
            linkLabelTentativeClose = false
        }
        if (inLinkUrl) {
            +applyBackslashEscapes("($linkUrlSource")
        }
        resetInlineLinkState()
    }

    private suspend fun SemanticEventScope.flushInline(softBreak: Boolean = false) {
        // Tagged inline HTML (e.g. `<label>`) is an *unambiguous* open, unlike a
        // speculative `*`, so at a SOFT line break we may keep its mark open and
        // let the `\n` flow as content — closing it only at the matching `</tag>`
        // or a hard boundary. We do this (`preserveTags`) only when no *off-stack*
        // speculative span is open: the del/mark/sup/code/math booleans and an
        // in-flight link/image/entity have no stack position, so closing one here
        // while keeping a tagged frame open would cross the stream. In any other
        // state we fall through to the full drain below, identical to a hard flush.
        val preserveTags = softBreak && !code && !math && !strikethrough &&
            !highlight && !superscript && !inLink && !inImage && !inEntityRef
        // Try shortcut reference resolution for an unresolved `[label]` /
        // `![alt]` at the block boundary before aborting. The block close means
        // there's no further char that could turn it into an inline link or
        // full/collapsed reference, so a registered ref-def match is the last
        // chance to commit it as a link.
        if ((inLink || inImage) && !inLinkUrl && !inLinkRef && linkLabelTentativeClose) {
            val isImage = inImage
            val labelText = if (isImage) imageAlt.toString() else linkText.toString()
            val key = normalizeLinkLabel(applyBackslashEscapes(labelText))
            val def = if (key.isNotEmpty()) linkDefinitions[key] else null
            if (def != null) {
                val labelEvents = redirector.stopCapture()
                if (isImage) {
                    val alt = labelEvents.toLabelText().ifEmpty { labelText }
                    val attrs = if (def.title != null) {
                        mapOf("src" to def.href, "alt" to alt, "title" to def.title)
                    } else {
                        mapOf("src" to def.href, "alt" to alt)
                    }
                    "img"(attributes = attrs) {}
                } else {
                    val attrs = if (def.title != null) {
                        mapOf("href" to def.href, "title" to def.title)
                    } else {
                        mapOf("href" to def.href)
                    }
                    mark("a", attributes = attrs)
                    redirector.replay(labelEvents)
                    unmark("a")
                }
                linkLabelTentativeClose = false
                resetInlineLinkState()
            }
        }
        // Abort any still-in-flight link/image parse: a block boundary means the
        // bracket run never resolved, so replay it as literal text instead of
        // silently dropping the buffered label.
        if (inLink) abortInlineLink()
        if (inImage) abortInlineImage()
        // Same idea for entity refs: an unterminated `&copy` at block end must
        // survive as literal source, not silently disappear.
        if (inEntityRef) abortEntityRef()
        // Close inline code: an N≥2 buffered close-run at the boundary is recognized
        // as a valid close (with strip rule). For N=1 a tentative single-` in the
        // buffer counts as the close; a longer run is content. Otherwise, force-close
        // — the `<code>` mark was committed at open and cannot be retracted in this
        // append-only stream, so unmatched openers visibly close as `<code>…</code>`
        // (deliberate streaming divergence from GFM, see CLAUDE.md).
        if (code) {
            if (codeRunLength == 1) {
                if (inlineBuffer.length == 1) {
                    inlineBuffer.clear()
                } else if (inlineBuffer.isNotEmpty()) {
                    +inlineBuffer.toString()
                    inlineBuffer.clear()
                }
            } else {
                var trail = 0
                while (trail < inlineBuffer.length &&
                    inlineBuffer[inlineBuffer.length - 1 - trail] == '`'
                ) trail++
                if (trail == codeRunLength) {
                    var content = inlineBuffer.substring(0, inlineBuffer.length - trail)
                    if (content.startsWith(" ") && content.endsWith(" ") && content.length > 1
                        && content.any { it != ' ' }) {
                        content = content.substring(1, content.length - 1)
                    }
                    +content
                } else if (inlineBuffer.isNotEmpty()) {
                    +inlineBuffer.toString()
                }
                inlineBuffer.clear()
            }
            unmark("code")
            code = false
            codeRunLength = 0
        }
        if (inlineBuffer.isNotEmpty()) {
            val buf = inlineBuffer.toString()
            inlineBuffer.clear()
            // Resolve pending formatting markers using flanking-aware logic.
            // `next = null` because flush happens at a block/line boundary, which
            // counts as Unicode whitespace per CommonMark.
            when (buf) {
                "*" -> resolveEmphasisRun('*', 1, null)
                "**" -> resolveEmphasisRun('*', 2, null)
                "***" -> resolveEmphasisRun('*', 3, null)
                "_" -> resolveEmphasisRun('_', 1, null)
                "__" -> resolveEmphasisRun('_', 2, null)
                "___" -> resolveEmphasisRun('_', 3, null)
                // `del`/`mark` resolve their run on the *next* char during inline
                // parsing (to size the run), so a run at the very end of a line
                // never sees that char and lands here. A run that *closes* an open
                // span (`==a==`, `~~a~~`) must emit the `unmark` — without it the
                // closer leaked into the span as literal content (`==a==` →
                // `<mark>a==</mark>`) and grew on every round-trip. But an *unmatched*
                // run (no span open — `foo==`, `foo~~`) is literal text: opening an
                // empty span here would rewrite `foo==` to `foo====` on the first
                // render. So close-if-open, else emit the buffer verbatim.
                "~", "~~" -> when {
                    strikethrough -> { unmark("del"); strikethrough = false }
                    else -> +buf
                }
                "==" -> when {
                    highlight -> { unmark("mark"); highlight = false }
                    else -> +buf
                }
                else -> +buf
            }
        }
        if (math) {
            unmark("math")
            math = false
        }
        if (highlight) {
            unmark("mark")
            highlight = false
        }
        if (superscript) {
            unmark("sup")
            superscript = false
        }
        if (strikethrough) {
            unmark("del")
            strikethrough = false
        }
        // Drain unclosed inline opens — emphasis (`em`/`strong`) and HTML tags
        // share a single stack so they pop in *opening order*. A `<strong>`
        // opened inside an outer `<em>` closes *before* the em here, never after
        // — required to keep the event stream balanced. With [preserveTags] (a
        // soft break in a clean state) keep the topmost tagged frame and
        // everything below it open, closing only the Markdown frames stacked
        // above it; `keepThrough == -1` (no tagged frame, or a hard flush) drains
        // the whole stack as before.
        val keepThrough = if (preserveTags) inlineOpenStack.indexOfLast { it.isTagged } else -1
        while (inlineOpenStack.size > keepThrough + 1) {
            val frame = inlineOpenStack.removeLast()
            unmark(frame.name, isTagged = frame.isTagged)
        }
        escaped = false
        // Clear any in-flight inline raw-text skip — a disallowed tag's body does
        // not span a block boundary (STREAMING DIVERGENCE, see [inlineRawSkipTag]).
        endInlineRawSkip()
        // Reset flanking state so the next inline run starts at a block boundary
        // (treated as Unicode whitespace) — matches CommonMark expectation that
        // delimiters at the start of a paragraph are preceded by whitespace.
        prevInlineChar = null
        runPrevChar = null
    }

    suspend fun finalize() {
        scope.finalize()
    }

    private suspend fun SemanticEventScope.finalize() {
        // If a failed-prefix blockquote line was being buffered when the input
        // ended (no trailing `\n`), dispatch it now as if a `\n` arrived.
        if (blockquoteInFailedLine) {
            val line = blockquoteFailedLineBuffer.toString()
            blockquoteFailedLineBuffer.clear()
            blockquoteInFailedLine = false
            dispatchFailedBlockquoteLine(line)
        }

        // Drain the block-mode stack from top to bottom. Each iteration closes
        // the current top frame's in-flight block (emitting balancing unmarks
        // and any buffered trailing content), then transitions or pops so the
        // loop converges on the bottom `Start` frame with empty `lineBuffer`.
        while (true) {
            when (val mode = blockMode) {
                is Heading -> {
                    flushInline()
                    unmark("h${mode.level}")
                    replaceMode(Start)
                }
                Paragraph -> {
                    flushInline()
                    flushPendingTrailingBackslash()
                    endParagraph()
                    replaceMode(Start)
                    paragraphTrailingSpaces = 0
                }
                ParagraphContinuation -> {
                    if (lineBuffer.isNotEmpty()) {
                        val line = lineBuffer.toString()
                        lineBuffer.clear()
                        if (lineInterruptsParagraph(line)) {
                            flushPendingTrailingBackslash()
                            endParagraph()
                            replaceMode(Start)
                            paragraphTrailingSpaces = 0
                            replay(line)
                            process('\n')
                            continue
                        }
                        emitParagraphLineBreak()
                        processInlineContent(line.trimEnd(' '))
                        flushInline()
                    }
                    flushPendingTrailingBackslash()
                    endParagraph()
                    replaceMode(Start)
                    paragraphTrailingSpaces = 0
                }
                is CodeBlock -> {
                    if (lineBuffer.isNotEmpty()) {
                        val line = lineBuffer.toString()
                        lineBuffer.clear()
                        if (!isFenceClose(line, mode.marker, mode.length)) {
                            +"${stripIndentCols(line, mode.indent)}\n"
                        }
                    }
                    unmark("code")
                    unmark("pre")
                    replaceMode(Start)
                }
                UnorderedList -> {
                    if (inListItem) {
                        flushInline()
                        unmark("li")
                        inListItem = false
                    }
                    unmark("ul")
                    replaceMode(Start)
                }
                OrderedList -> {
                    if (inListItem) {
                        flushInline()
                        unmark("li")
                        inListItem = false
                    }
                    unmark("ol")
                    replaceMode(Start)
                }
                Blockquote -> {
                    // Sub-parse blockquote frame: any in-flight inner content was
                    // already drained by the time we reach this frame (inner
                    // frames sit above and are popped first). Emit the closing
                    // `</blockquote>` and pop.
                    unmark("blockquote")
                    popMode()
                }
                MathBlock -> {
                    if (lineBuffer.isNotEmpty()) {
                        +lineBuffer.toString()
                        lineBuffer.clear()
                    }
                    unmark("math")
                    replaceMode(Start)
                }
                is TableHeaderPending -> {
                    // EOF before the second line was newline-terminated. If the
                    // residual line in `lineBuffer` parses as a valid separator,
                    // commit a header-only table; otherwise replay both as
                    // ordinary content with table detection suppressed.
                    val pendingTail = lineBuffer.toString().trimEnd()
                    lineBuffer.clear()
                    val headerCells = splitTableCells(mode.headerLine.trimEnd())
                    val alignments = if (pendingTail.isNotEmpty())
                        parseSeparatorAlignments(pendingTail, headerCells.size)
                    else null
                    if (alignments != null) {
                        mark("table")
                        mark("thead")
                        "tr" {
                            emitTableCells(headerCells, isHeader = true, alignments = alignments)
                        }
                        unmark("thead")
                        unmark("table")
                        replaceMode(Start)
                    } else {
                        replaceMode(Start)
                        suppressTableDetection = true
                        try {
                            replay(mode.headerLine)
                            process('\n')
                            if (pendingTail.isNotEmpty()) {
                                replay(pendingTail)
                                process('\n')
                            }
                        } finally {
                            suppressTableDetection = false
                        }
                    }
                }
                is TableBody -> {
                    if (lineBuffer.isNotEmpty()) {
                        val line = lineBuffer.toString().trimEnd()
                        lineBuffer.clear()
                        if (line.isNotEmpty()) {
                            val endsTable = !line.startsWith("|") && lineInterruptsParagraph(line)
                            if (endsTable) {
                                closeTableBody(mode)
                                replaceMode(Start)
                                replay(line)
                                process('\n')
                                continue
                            }
                            emitBodyRow(mode, line)
                        }
                    }
                    closeTableBody(mode)
                    replaceMode(Start)
                }
                Start -> {
                    if (lineBuffer.isNotEmpty()) {
                        process('\n')
                        continue
                    }
                    if (blockModeStack.size == 1) return
                    // Pushed sub-parse `Start` frame above an HTML block — pop and
                    // continue draining the enclosing frame.
                    popMode()
                }
                IndentedCodeBlock -> {
                    indentedCodeDeferredBlanks.clear()
                    if (lineBuffer.isNotEmpty()) {
                        val line = lineBuffer.toString()
                        lineBuffer.clear()
                        if (leadingIndentCols(line) >= 4) {
                            +"${stripIndentCols(line, 4)}\n"
                        }
                    }
                    unmark("code")
                    unmark("pre")
                    replaceMode(Start)
                }
                is ListBlock -> {
                    if (lineBuffer.isNotEmpty()) {
                        process('\n')
                        if (blockMode !== mode) continue
                    }
                    popListContexts(mode, downTo = 0)
                    replaceMode(Start)
                }
                is CustomMarkup -> {
                    if (customMarkupClosingBuffer.isNotEmpty()) {
                        +customMarkupClosingBuffer.toString()
                        customMarkupClosingBuffer.clear()
                        customMarkupInClosingTag = false
                    }
                    unmark(mode.tagName, isTagged = true)
                    replaceMode(Start)
                }
                is HtmlBlock1 -> {
                    if (mode.firstLineBuffer != null) {
                        val raw = mode.firstLineBuffer!!.toString()
                        if (raw.isNotEmpty()) +"$raw\n"
                        mode.firstLineBuffer = null
                    } else {
                        if (lineBuffer.isNotEmpty()) {
                            +"${lineBuffer.toString()}\n"
                            lineBuffer.clear()
                        }
                        while (mode.openTags.isNotEmpty()) {
                            unmarkHtml(mode.openTags.removeLast())
                        }
                    }
                    replaceMode(Start)
                }
                is HtmlBlock2to5 -> {
                    if (lineBuffer.isNotEmpty()) {
                        +"${lineBuffer.toString()}\n"
                        lineBuffer.clear()
                    }
                    replaceMode(Start)
                }
                Doctype -> {
                    if (lineBuffer.isNotEmpty()) {
                        +"\n${lineBuffer.toString()}"
                        lineBuffer.clear()
                    }
                    unmark("doctype", isTagged = true)
                    replaceMode(Start)
                }
                is HtmlBlock6or7 -> {
                    val fb = mode.firstLineBuffer
                    if (fb != null) {
                        val raw = fb.toString()
                        if (raw.isNotEmpty()) +"$raw\n"
                        mode.firstLineBuffer = null
                        if (lineBuffer.isNotEmpty()) {
                            +"${lineBuffer.toString()}\n"
                            lineBuffer.clear()
                        }
                        // Phase-1 fallback emitted the buffered opener as text; pop
                        // back to the enclosing frame.
                        leaveRawHtmlBlock(mode)
                        popMode()
                        continue
                    }
                    if (lineBuffer.isNotEmpty()) {
                        val line = lineBuffer.toString()
                        lineBuffer.clear()
                        streamHtmlBlock6or7ContentLine(line, mode)
                        // Streaming may have popped (root close) or pushed (blank-
                        // line transition to sub-parse). Re-enter the loop.
                        if (blockMode !== mode) continue
                    }
                    while (mode.openTags.isNotEmpty()) {
                        unmarkHtml(mode.openTags.removeLast())
                    }
                    leaveRawHtmlBlock(mode)
                    popMode()
                }
            }
        }
    }
}
