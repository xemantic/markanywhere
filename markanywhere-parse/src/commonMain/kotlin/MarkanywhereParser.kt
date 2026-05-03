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
import kotlinx.coroutines.flow.flow

public fun Flow<String>.parse(): Flow<SemanticEvent> = flow {
    val state = ParserState(scope = SemanticEventScope(collector = this))
    collect { chunk -> state.processChunk(chunk) }
    state.finalize()
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
    val TABLE_SEPARATOR = Regex("^\\|[-:|\\s]+\\|$")
    val WHITESPACE = Regex("\\s+")
    val ATTRIBUTE = Regex("""(\w+)=["']([^"']*)["']""")
    val HTML_OPEN_TAG = Regex("^<([a-zA-Z][a-zA-Z0-9-]*)(\\s|/?>|$)")
    val HTML_CLOSE_TAG = Regex("^</([a-zA-Z][a-zA-Z0-9-]*)\\s*>$")
}

/**
 * Parsed bullet-list marker (`-`, `+`, or `*`) or ordered-list marker (e.g. `1.`)
 * at the start of a line. [contentCol] is the column where item content begins.
 */
private data class ListMarker(
    val ordered: Boolean,
    val markerStartCol: Int,
    val contentCol: Int,
    /** Index in the source line where the marker (and its trailing whitespace) ends. */
    val markerEndIndex: Int
)

/**
 * One level in the streaming list stack. [markerStartCol] and [contentCol] are
 * absolute columns (after expanding outer container offsets). The boolean flags
 * are mutated by the streaming state machine as lines flow in.
 */
private class ListContext(
    val ordered: Boolean,
    val markerStartCol: Int,
    val contentCol: Int,
    /** True while a `<p>` is currently open in the active item. */
    var paragraphOpen: Boolean = false,
    /** True while a `<pre><code>` block is currently open in the active item. */
    var codeBlockOpen: Boolean = false,
    /** Pending blank lines in an open code block (emitted lazily on next code line). */
    var codeBlankLines: Int = 0
)

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
    when {
        ch in "-+*" -> { ordered = false; markerWidth = 1; i++ }
        ch in '0'..'9' -> {
            val digitStart = i
            while (i < line.length && line[i].isDigit()) i++
            if (i - digitStart > 9 || i >= line.length) return null
            if (line[i] != '.' && line[i] != ')') return null
            i++
            ordered = true; markerWidth = i - digitStart
        }
        else -> return null
    }
    val markerEndCol = markerStartCol + markerWidth
    if (i >= line.length) {
        return ListMarker(ordered, markerStartCol, markerEndCol + 1, i)
    }
    when (line[i]) {
        ' ' -> {
            var j = i
            var cc = markerEndCol
            while (j < line.length && line[j] == ' ' && cc - markerEndCol < 5) {
                cc++; j++
            }
            val spacesAfter = cc - markerEndCol
            val contentCol = if (spacesAfter >= 5) markerEndCol + 1 else cc
            val end = if (spacesAfter >= 5) i + 1 else j
            return ListMarker(ordered, markerStartCol, contentCol, end)
        }
        '\t' -> {
            // Tab after marker: per GFM, marker + 1-space-equivalent consumes 1 col.
            // The unconsumed remainder of the tab stays as content indent.
            return ListMarker(ordered, markerStartCol, markerEndCol + 1, i + 1)
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

// Strict custom markup tagname: namespace:name where each segment is letter then letters/digits/dashes.
private val CUSTOM_MARKUP_TAGNAME = Regex("^[a-zA-Z][a-zA-Z0-9-]*:[a-zA-Z][a-zA-Z0-9-]*$")

// Block-level elements that should NOT be converted to inline Mark/Unmark events
// when they appear mid-paragraph (CommonMark allows them inline, but we conservatively
// emit literal text so renderers escape `<div>` rather than treat it as actual markup).
private val INLINE_HTML_BLOCK_ELEMENTS = HTML_BLOCK_TYPE6_TAGS + HTML_BLOCK_TYPE1_TAGS

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
                            attrs[attrName] = v
                        }
                        else -> {
                            // unquoted attribute value: cannot contain whitespace, <, >, =, `, ', ", or be empty
                            val valStart = i
                            while (i < s.length && s[i] !in " \t\n<>=`\"'") i++
                            if (i == valStart) return null
                            attrs[attrName] = s.substring(valStart, i)
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
            tokens.add(HtmlToken.Text(text.toString()))
            text.clear()
        }
    }
    var i = 0
    while (i < line.length) {
        if (line[i] == '<') {
            val open = tryParseOpenTag(line, i)
            if (open != null) {
                flushText()
                tokens.add(open.second)
                i = open.first
                continue
            }
            val close = tryParseCloseTag(line, i)
            if (close != null) {
                flushText()
                tokens.add(close.second)
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
    private val scope: SemanticEventScope
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
        data object Blockquote : BlockMode
        /** Fenced code block nested inside a blockquote (GFM §5.1 + §4.5). */
        data class BlockquoteCode(
            val marker: Char,
            val length: Int,
            val indent: Int
        ) : BlockMode
        data object BlockquoteList : BlockMode
        data object MathBlock : BlockMode
        data object Table : BlockMode
        data object TableBody : BlockMode
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

        /** Types 6/7 (block-level / type-7 tag). Buffered until close decision. */
        class HtmlBlock6or7(
            val rootTagName: String,
            val rootIsClosingTag: Boolean
        ) : BlockMode {
            // Buffer for the first opening tag (may span multiple lines until '>' found).
            var firstLineBuffer: StringBuilder? = StringBuilder()
            // After firstLineBuffer is parsed: tokens of the first complete line.
            var firstLineTokens: List<HtmlToken>? = null
            // Captured raw text of the first complete line (without trailing \n).
            var firstLineRaw: String = ""
            // Whether a matching root close was found in the first line tokens.
            var rootCloseInFirstLine: Boolean = false
            // Lines after the first complete line, before any blank line.
            val preBlankLines: MutableList<String> = mutableListOf()
            // Index of the matching root close within preBlankLines, or -1.
            var rootCloseInPreBlank: Int = -1
            // True once a blank line has been observed.
            var blankLineSeen: Boolean = false
            // Lines after the first blank (markdown sub-mode); may contain blank lines too.
            val postBlankLines: MutableList<String> = mutableListOf()
            // Index of the matching root close within postBlankLines, or -1.
            var rootCloseInPostBlank: Int = -1
        }
    }

    private var blockMode: BlockMode = BlockMode.Start
    private var lineBuffer = StringBuilder()
    private var atLineStart = true
    private val indentedCodeDeferredBlanks = mutableListOf<String>()
    private var tableHasBody = false
    private var inListItem = false
    private var inBlockquoteParagraph = false

    // Inline state
    private var bold = false
    private var italic = false
    private var code = false
    private var strikethrough = false
    private var subscript = false
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
    private var escaped = false
    /** Length of the opening backtick run for the currently-open inline code span (0 if not in code). */
    private var codeRunLength = 0
    private var inlineBuffer = StringBuilder()

    // After resolving a buffered marker (e.g. "*"), the trailing char is left
    // unconsumed so the outer loop's fast-path can coalesce it with subsequent
    // non-control characters. Replaces the previous recursive `processInlineChar`.
    private var pendingDeferredChar: Char? = null

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
        '*', '_', '`', '~', '^', '=', '$', '[', '!', '<', '\\', '\n' -> true
        else -> false
    }

    // Characters that may start (or be ambiguous with) a block-level element.
    // Used to gate eager paragraph opening in Start mode: a char NOT in this set
    // is unambiguously paragraph content, so we can mark <p> immediately and
    // route subsequent chars through Paragraph mode for incremental emission.
    // Includes `*`, `_` (thematic break vs. emphasis), `\t` (indented code), and
    // ` ` (leading whitespace before any block).
    private fun Char.isBlockStart(): Boolean = when (this) {
        '#', '`', '~', '-', '>', '|', '$', '<', ' ', '\t', '*', '_' -> true
        else -> isDigit()
    }

    // CommonMark emphasis flanking helpers. Block boundaries (null) count as
    // Unicode whitespace per spec.
    private fun Char?.isFlankWhitespace(): Boolean =
        this == null || this == ' ' || this == '\t' || this == '\n' || this == '\r'

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
     * Matching of openers to closers uses the existing italic/bold toggle state
     * rather than a full CommonMark delimiter stack — sufficient for non-nested
     * cases. Orphan openers/closers can still leak empty `<em>`/`<strong>`
     * pairs at block end; those cases are tracked as DIVERGENCE in tests.
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
        when (runLen) {
            1 -> when {
                canClose && italic -> { unmark("em"); italic = false }
                canOpen && !italic -> { mark("em"); italic = true }
                italic -> { unmark("em"); italic = false }
                else -> +runChar.toString()
            }
            2 -> when {
                canClose && bold -> { unmark("strong"); bold = false }
                canOpen && !bold -> { mark("strong"); bold = true }
                bold -> { unmark("strong"); bold = false }
                else -> +runChar.toString().repeat(2)
            }
            else -> when {
                canClose && bold && italic -> {
                    unmark("em"); italic = false
                    unmark("strong"); bold = false
                }
                canOpen && !bold && !italic -> {
                    mark("strong"); bold = true
                    mark("em"); italic = true
                }
                bold && italic -> {
                    unmark("em"); italic = false
                    unmark("strong"); bold = false
                }
                bold -> { unmark("strong"); bold = false; mark("em"); italic = true }
                italic -> { unmark("em"); italic = false; mark("strong"); bold = true }
                else -> { mark("strong"); bold = true; mark("em"); italic = true }
            }
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
        escaped -> startIndex
        code && codeRunLength == 1 -> findNextChar(content, startIndex, '`')
        code -> startIndex
        math -> findNextChar(content, startIndex, '$')
        inLinkUrl -> startIndex
        inLink -> startIndex
        inImage -> startIndex
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
                !inLink && !inImage
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
                // Emit the safe substring in one go
                +chunk.substring(index, fastPathResult)
                // Keep flanking state in sync — see processInlineContent for rationale.
                prevInlineChar = chunk[fastPathResult - 1]
                index = fastPathResult
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
            Blockquote -> inBlockquoteParagraph && !atLineStart
            BlockquoteList -> inListItem
            else -> false
        }

        if (!canFastPath) return startIndex

        // In inline text (possibly with active formatting like bold/italic) - scan for any control character
        return findNextControlChar(chunk, startIndex)
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
            Blockquote -> processBlockquote(char)
            is BlockquoteCode -> processBlockquoteCode(char, mode)
            BlockquoteList -> processBlockquoteList(char)
            MathBlock -> processMathBlock(char)
            Table -> processTable(char)
            TableBody -> processTableBody(char)
            is CustomMarkup -> processCustomMarkup(char, mode.tagName)
            is HtmlBlock1 -> processHtmlBlock1(char, mode)
            is HtmlBlock2to5 -> processHtmlBlock2to5(char, mode)
            is HtmlBlock6or7 -> processHtmlBlock6or7(char, mode)
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

    private suspend fun SemanticEventScope.processStart(
        char: Char
    ) {
        // Eager paragraph opening: if this is the first char of a fresh line and the
        // char cannot start any other block, open the paragraph now and reprocess the
        // char through Paragraph mode (which supports fast-path for incremental emission).
        if (lineBuffer.isEmpty() && char != '\n' && !char.isBlockStart()) {
            mark("p")
            blockMode = BlockMode.Paragraph
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

            when {
                // Indented code block: ≥4 cols of leading whitespace (tabs count to tab stop 4).
                leadingIndentCols(line) >= 4 -> {
                    mark("pre")
                    mark("code")
                    +"${stripIndentCols(line, 4)}\n"
                    indentedCodeDeferredBlanks.clear()
                    blockMode = BlockMode.IndentedCodeBlock
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
                // Fenced code block opening: ``` or ~~~ (GFM §4.5)
                parseFenceOpen(line) != null -> {
                    val fence = parseFenceOpen(line)!!
                    mark("pre")
                    val codeAttrs = fence.language?.let { mapOf("class" to "language-$it") }
                    mark("code", attributes = codeAttrs)
                    blockMode = BlockMode.CodeBlock(fence.marker, fence.length, fence.indent)
                }
                // Horizontal rule: ---
                line matches Patterns.HORIZONTAL_RULE -> {
                    "hr" {}
                }
                // Math block: $$
                line == "$$" -> {
                    mark("math", attributes = mapOf("display" to "block"))
                    blockMode = BlockMode.MathBlock
                }
                // Table row
                line.startsWith("|") -> {
                    mark("table")
                    mark("thead")
                    "tr" {
                        emitTableRow(line, isHeader = true)
                    }
                    tableHasBody = false
                    blockMode = BlockMode.Table
                }
                // HTML block detection (CommonMark 4.6) - check before custom markup
                detectHtmlBlockType(line) > 0 -> {
                    enterHtmlBlock(line)
                }
                // Custom markup opening tag: <namespace:name ...>
                line.startsWith("<") && line.endsWith(">") && !line.startsWith("</") -> {
                    val parsed = parseCustomMarkupOpeningTag(line)
                    if (parsed != null) {
                        val (tagName, attributes) = parsed
                        mark(tagName, isTagged = true, attributes = attributes)
                        customMarkupSkipFirstNewline = false  // Already consumed by line-based detection
                        customMarkupPendingNewline = false  // Reset any stale state from previous custom markup
                        blockMode = BlockMode.CustomMarkup(tagName)
                    } else {
                        // Not a valid custom markup tag, treat as paragraph
                        beginParagraph(line)
                    }
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
                blockMode = BlockMode.Heading(level)
            }
            line matches Patterns.HEADING_NO_SPACE -> {
                // Keep buffering to see if space follows
            }
            line matches Patterns.TOO_MANY_HASHES -> {
                // Too many #, treat as paragraph
                mark("p")
                processInlineContent(line)
                lineBuffer.clear()
                blockMode = BlockMode.Paragraph
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
            // Blockquote: `> text`. Open `<blockquote>` only — defer `<p>` until first
            // content char arrives so backtick/tilde-led content can open a fenced code
            // block instead. lineBuffer keeps `> ` so processBlockquote's pattern
            // dispatcher continues from the same prefix on the next char.
            line == "> " -> {
                mark("blockquote")
                inBlockquoteParagraph = false
                blockMode = BlockMode.Blockquote
            }
            line == ">" -> {
                // Keep buffering - might be "> " or just ">"
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
                    mark(tagName, isTagged = true, attributes = attributes)
                    lineBuffer.clear()
                    customMarkupSkipFirstNewline = true  // Skip newline that may follow immediately
                    customMarkupPendingNewline = false  // Reset any stale state from previous custom markup
                    blockMode = BlockMode.CustomMarkup(tagName)
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
                flushInline()
                unmark("h${mode.level}")
                blockMode = BlockMode.Start
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
                flushInline()
                lineBuffer.clear()
                atLineStart = true
                blockMode = BlockMode.ParagraphContinuation
            }
            else -> {
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
        processInlineContent(line.trimStart(' ', '\t'))
        flushInline()
        blockMode = BlockMode.ParagraphContinuation
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
            +"\n"
            blockMode = BlockMode.Paragraph
            pendingDeferredChar = char
            return
        }

        if (char != '\n') {
            lineBuffer.append(char)
            return
        }
        val line = lineBuffer.toString()
        lineBuffer.clear()
        if (line.isEmpty()) {
            unmark("p")
            blockMode = BlockMode.Start
            return
        }
        if (lineInterruptsParagraph(line)) {
            unmark("p")
            blockMode = BlockMode.Start
            // Replay the line through Start so it can be parsed as its own block.
            replay(line)
            process('\n')
            return
        }
        // Continuation line: soft break then inline content.
        // Leading spaces/tabs are stripped (CommonMark: indented code cannot interrupt
        // a paragraph, so leading indentation on continuation lines is paragraph content
        // with the indentation removed).
        val stripped = line.trimStart(' ', '\t')
        +"\n"
        // Special case: a line that is exactly one open HTML tag is rendered as a
        // self-closing-equivalent (mark + unmark) so the event tree stays balanced.
        val singleTag = tryParseOpenTag(stripped, 0)
        if (singleTag != null && singleTag.first == stripped.length &&
            singleTag.second.name.lowercase() !in INLINE_HTML_BLOCK_ELEMENTS
        ) {
            val tag = singleTag.second
            mark(tag.name, isTagged = true, attributes = tag.attributes)
            unmark(tag.name, isTagged = true)
        } else {
            processInlineContent(stripped)
            flushInline()
        }
        blockMode = BlockMode.ParagraphContinuation
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
        // Table
        if (line.startsWith("|")) return true
        // Blockquote
        if (line == ">" || line.startsWith("> ")) return true
        // Lists
        if (line matches Patterns.TASK_UNCHECKED) return true
        if (line matches Patterns.TASK_CHECKED) return true
        if (line.startsWith("- ") && line.length > 2 && line[2] != '[') return true
        // Ordered list: digit(s) then '.' then ' ' (or end-of-line)
        var i = 0
        while (i < line.length && line[i].isDigit()) i++
        if (i > 0 && i < line.length && line[i] == '.' && (i + 1 == line.length || line[i + 1] == ' ')) return true
        // HTML block start: types 1-6 interrupt paragraphs (type 7 does not by spec).
        val type = detectHtmlBlockType(line)
        if (type in 1..6) return true
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
                blockMode = BlockMode.Start
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
        blockMode = mode
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
            markerStartCol = parentContentCol + marker.markerStartCol,
            contentCol = parentContentCol + marker.contentCol
        )
        mode.stack.add(ctx)
        mark(if (ctx.ordered) "ol" else "ul")
        mark("li")
        val firstContent = markerLine.substring(marker.markerEndIndex)
        if (firstContent.isNotBlank()) {
            emitItemFirstLine(firstContent, ctx)
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
        if (trimmed matches Patterns.THEMATIC_BREAK) {
            "hr" {}
            return
        }
        mark("p")
        ctx.paragraphOpen = true
        emitItemFirstContent(trimmed)
        flushInline()
    }

    /**
     * Emit the first paragraph content of a list item. If [content] begins with a
     * GFM task-list marker (`[ ]`, `[x]`, or `[X]` followed by a space), emit a
     * disabled checkbox `<input>` and pass the remainder (including the space
     * after `]`) through inline processing. Otherwise process [content] as inline.
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
                "input"("type" to "checkbox", "checked" to "", "disabled" to "") {}
            } else {
                "input"("type" to "checkbox", "disabled" to "") {}
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
            unmark("p")
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

    /**
     * Pop list contexts down to (and including, when [includeIndex] is true) [index],
     * emitting `</li>` and `</ul>`/`</ol>` for each. Closes any open `<p>` or code
     * block on the topmost context as part of closing the active item.
     */
    private suspend fun SemanticEventScope.popListContexts(
        mode: BlockMode.ListBlock,
        downTo: Int
    ) {
        while (mode.stack.size > downTo) {
            closeListParagraphIfOpen(mode)
            closeListCodeIfOpen(mode)
            unmark("li")
            val ctx = mode.stack.removeLast()
            unmark(if (ctx.ordered) "ol" else "ul")
        }
    }

    /** Close the current `<li>` of the top context and open a new sibling `<li>`. */
    private suspend fun SemanticEventScope.openSiblingItem(mode: BlockMode.ListBlock) {
        closeListParagraphIfOpen(mode)
        closeListCodeIfOpen(mode)
        unmark("li")
        mark("li")
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

        // Blank line: close any open paragraph (lazy code-block lines only emit on
        // resumption). Mark the list as having seen a blank — used to decide whether
        // a subsequent indented continuation opens a new paragraph.
        if (line.isBlank()) {
            closeListParagraphIfOpen(mode)
            // Defer blank-line emission for an open code block until more code arrives.
            val top = mode.stack.lastOrNull()
            if (top != null && top.codeBlockOpen) top.codeBlankLines++
            mode.blankSeen = true
            return
        }

        val indent = leadingIndentCols(line)

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
            blockMode = BlockMode.Start
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
                // If the marker style differs (ordered vs unordered), close this list
                // and start a new sibling list at the same indent level.
                if (ctx.ordered != strippedMarker.ordered) {
                    popListContexts(mode, downTo = siblingIndex)
                    startListItemFromMarker(mode, stripped, parentContentCol = containerContentCol)
                } else {
                    closeListParagraphIfOpen(mode)
                    closeListCodeIfOpen(mode)
                    unmark("li")
                    mark("li")
                    val firstContent = stripped.substring(strippedMarker.markerEndIndex)
                    if (firstContent.isNotBlank()) {
                        emitItemFirstLine(firstContent, ctx)
                    }
                    // Update content/marker cols for the new item (in case marker width differs).
                    // Keep markerStartCol stable; refresh contentCol for this item to allow
                    // varying continuation columns. (Approximation: we keep original.)
                }
                mode.blankSeen = false
                return
            }

            // Otherwise: this is a deeper marker — push a new nested list level.
            // First close any open paragraph/code in the parent (we're entering block
            // content inside the parent item).
            closeListParagraphIfOpen(mode)
            closeListCodeIfOpen(mode)
            startListItemFromMarker(mode, stripped, parentContentCol = containerContentCol)
            return
        }

        // Not a marker. If no context contains this line at all, the list ends.
        if (markerCtxIndex < 0) {
            popListContexts(mode, downTo = 0)
            blockMode = BlockMode.Start
            replay(line)
            process('\n')
            return
        }

        // The line is a continuation inside `mode.stack[markerCtxIndex]`. Pop deeper
        // contexts (their items end where the indent dropped below their contentCol).
        popListContexts(mode, downTo = markerCtxIndex + 1)
        val ctx = mode.stack[markerCtxIndex]

        // `stripped` is the line with the container's contentCol of leading indent
        // removed; semantically it begins at absolute column [containerContentCol].
        val innerIndent = leadingIndentCols(stripped, startCol = containerContentCol)

        // Indented code block start/continuation: only if a blank line preceded (item
        // is between blocks) or a code block is already open for this context.
        if (ctx.codeBlockOpen) {
            // Continuation of an open code block: emit any deferred blank lines, then
            // the stripped line content (further stripped by 4 cols past container col).
            repeat(ctx.codeBlankLines) { +"\n" }
            ctx.codeBlankLines = 0
            if (innerIndent >= 4) {
                +"${stripIndentCols(stripped, 4, startCol = containerContentCol)}\n"
                return
            }
            // Code block ends — close it and fall through to handle this line as paragraph.
            closeListCodeIfOpen(mode)
        }

        if (innerIndent >= 4 && (mode.blankSeen || !ctx.paragraphOpen)) {
            // Open a new indented code block within the current item.
            closeListParagraphIfOpen(mode)
            mark("pre")
            mark("code")
            ctx.codeBlockOpen = true
            +"${stripIndentCols(stripped, 4, startCol = containerContentCol)}\n"
            mode.blankSeen = false
            return
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
        blockMode = BlockMode.Start
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
                    blockMode = BlockMode.Start
                }
                line.startsWith("#") -> {
                    // Header after list
                    unmark("ul")
                    lineBuffer.clear()
                    blockMode = BlockMode.Start
                    replay(line)
                }
                else -> {
                    // End of list, start new block
                    unmark("ul")
                    lineBuffer.clear()
                    blockMode = BlockMode.Start
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
                    blockMode = BlockMode.Start
                }
                line.startsWith("#") -> {
                    unmark("ol")
                    lineBuffer.clear()
                    blockMode = BlockMode.Start
                    replay(line)
                }
                else -> {
                    unmark("ol")
                    lineBuffer.clear()
                    blockMode = BlockMode.Start
                    replay(line)
                }
            }
        } else {
            processInlineChar(char)
        }
    }

    private var blockquotePendingNewline = false

    private suspend fun SemanticEventScope.processBlockquote(
        char: Char
    ) {
        if (char == '\n') {
            // If we were buffering a fence-candidate line ("> ` …" or "> ~ …"),
            // dispatch it now: open a fenced code block if the stripped content
            // is a valid fence open, otherwise treat the line as paragraph content.
            if (atLineStart &&
                lineBuffer.length > 2 &&
                lineBuffer.startsWith("> ") &&
                (lineBuffer[2] == '`' || lineBuffer[2] == '~')
            ) {
                val stripped = lineBuffer.toString().removePrefix("> ")
                lineBuffer.clear()
                val fence = parseFenceOpen(stripped)
                if (fence != null) {
                    if (inBlockquoteParagraph) {
                        flushInline()
                        unmark("p")
                        inBlockquoteParagraph = false
                    }
                    blockquotePendingNewline = false
                    mark("pre")
                    val codeAttrs = fence.language?.let { mapOf("class" to "language-$it") }
                    mark("code", attributes = codeAttrs)
                    blockMode = BlockMode.BlockquoteCode(fence.marker, fence.length, fence.indent)
                    atLineStart = true
                    return
                }
                // Not a fence — treat as paragraph content.
                if (blockquotePendingNewline && inBlockquoteParagraph) {
                    +"\n"
                    blockquotePendingNewline = false
                }
                if (!inBlockquoteParagraph) {
                    mark("p")
                    inBlockquoteParagraph = true
                }
                processInlineContent(stripped)
                if (inBlockquoteParagraph) blockquotePendingNewline = true
                atLineStart = true
                return
            }
            if (inBlockquoteParagraph) {
                // Don't emit newline yet - might be followed by list
                blockquotePendingNewline = true
            }
            lineBuffer.clear()
            atLineStart = true
            return
        }

        if (atLineStart) {
            lineBuffer.append(char)
            val line = lineBuffer.toString()

            when {
                line == ">" || line == "> " || line == "> -" -> {
                    // Continue blockquote marker - keep buffering (including potential list start)
                }
                line.startsWith("> - ") -> {
                    // List in blockquote - discard pending newline
                    blockquotePendingNewline = false
                    if (inBlockquoteParagraph) {
                        flushInline()
                        unmark("p")
                        inBlockquoteParagraph = false
                    }
                    mark("ul")
                    mark("li")
                    // Process content after "> - "
                    processInlineContent(line.removePrefix("> - "))
                    lineBuffer.clear()
                    inListItem = true
                    blockMode = BlockMode.BlockquoteList
                }
                // Potential fenced-code opener inside blockquote — buffer the whole line
                // so parseFenceOpen can decide at `\n` whether this opens a fence or is
                // ordinary paragraph content (e.g. a line-leading inline code span).
                line.startsWith("> ") && line.length > 2 && (line[2] == '`' || line[2] == '~') -> {
                    // Keep buffering until newline.
                }
                line.startsWith("> ") && line.length > 2 -> {
                    // Content after "> " - emit pending newline if continuing paragraph
                    if (blockquotePendingNewline && inBlockquoteParagraph) {
                        +"\n"
                        blockquotePendingNewline = false
                    }
                    if (!inBlockquoteParagraph) {
                        mark("p")
                        inBlockquoteParagraph = true
                    }
                    // Process content after "> "
                    processInlineContent(line.removePrefix("> "))
                    lineBuffer.clear()
                    atLineStart = false
                }
                !line.startsWith(">") -> {
                    // End blockquote - line doesn't start with >
                    blockquotePendingNewline = false
                    if (inBlockquoteParagraph) {
                        flushInline()
                        unmark("p")
                        inBlockquoteParagraph = false
                    }
                    unmark("blockquote")
                    lineBuffer.clear()
                    blockMode = BlockMode.Start
                    replay(line)
                }
            }
        } else {
            // Continue inline content within blockquote paragraph
            processInlineChar(char)
        }
    }

    private suspend fun SemanticEventScope.processBlockquoteCode(
        char: Char,
        mode: BlockMode.BlockquoteCode
    ) {
        if (char != '\n') {
            lineBuffer.append(char)
            return
        }
        val line = lineBuffer.toString()
        lineBuffer.clear()
        if (line.isEmpty() || !line.startsWith(">")) {
            // Blank line or non-blockquote line: close fenced code AND blockquote.
            unmark("code")
            unmark("pre")
            unmark("blockquote")
            inBlockquoteParagraph = false
            blockquotePendingNewline = false
            blockMode = BlockMode.Start
            atLineStart = true
            if (line.isNotEmpty()) {
                replay(line)
                process('\n')
            }
            return
        }
        val stripped = if (line.startsWith("> ")) line.substring(2)
        else line.substring(1)
        if (isFenceClose(stripped, mode.marker, mode.length)) {
            unmark("code")
            unmark("pre")
            blockMode = BlockMode.Blockquote
            atLineStart = true
            return
        }
        +"${stripIndentCols(stripped, mode.indent)}\n"
        atLineStart = true
    }

    private suspend fun SemanticEventScope.processBlockquoteList(
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
                line == "> - " -> {
                    mark("li")
                    lineBuffer.clear()
                    inListItem = true
                }
                line matches Patterns.BLOCKQUOTE_EMPTY || line matches Patterns.BLOCKQUOTE_DASH_PARTIAL -> {
                    // Keep buffering
                }
                line.isEmpty() -> {
                    unmark("ul")
                    unmark("blockquote")
                    blockMode = BlockMode.Start
                }
                else -> {
                    unmark("ul")
                    if (line.startsWith(">")) {
                        blockMode = BlockMode.Blockquote
                        lineBuffer.clear()
                        for (c in line) {
                            processBlockquote(c)
                        }
                    } else {
                        unmark("blockquote")
                        lineBuffer.clear()
                        blockMode = BlockMode.Start
                        replay(line)
                    }
                }
            }
        } else {
            processInlineChar(char)
        }
    }

    private suspend fun SemanticEventScope.processMathBlock(
        char: Char
    ) {
        if (char == '\n') {
            val line = lineBuffer.toString()
            if (line.trim() == "$$") {
                unmark("math")
                lineBuffer.clear()
                blockMode = BlockMode.Start
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

    private suspend fun SemanticEventScope.processTable(
        char: Char
    ) {
        lineBuffer.append(char)
        if (char == '\n') {
            val line = lineBuffer.toString().trimEnd()
            if (line matches Patterns.TABLE_SEPARATOR) {
                // Separator row
                unmark("thead")
                mark("tbody")
                tableHasBody = true
                lineBuffer.clear()
                blockMode = BlockMode.TableBody
            } else if (line.startsWith("|")) {
                // Another header row
                "tr" {
                    emitTableRow(line, isHeader = true)
                }
                lineBuffer.clear()
            } else {
                // End of table
                unmark("thead")
                unmark("table")
                lineBuffer.clear()
                blockMode = BlockMode.Start
                if (line.isNotEmpty()) {
                    replay(line)
                    process('\n')
                }
            }
        }
    }

    private suspend fun SemanticEventScope.processTableBody(
        char: Char
    ) {
        lineBuffer.append(char)
        if (char == '\n') {
            val line = lineBuffer.toString().trimEnd()
            if (line.startsWith("|")) {
                "tr" {
                    emitTableRow(line, isHeader = false)
                }
                lineBuffer.clear()
            } else {
                // End of table
                unmark("tbody")
                unmark("table")
                lineBuffer.clear()
                blockMode = BlockMode.Start
                if (line.isNotEmpty()) {
                    replay(line)
                    process('\n')
                }
            }
        }
    }

    private suspend fun SemanticEventScope.emitTableRow(
        line: String,
        isHeader: Boolean
    ) {
        val cells = line.trim().removePrefix("|").removeSuffix("|").split("|")
        val cellTag = if (isHeader) "th" else "td"
        for (cell in cells) {
            cellTag {
                processInlineContent(cell.trim())
                flushInline()
            }
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

    private data class InlineHtmlTag(
        val name: String,
        val isClose: Boolean,
        val isSelfClosing: Boolean,
        val attributes: Map<String, String>?
    )

    /**
     * Parses the content between < and > to determine if it's a valid inline HTML tag.
     * Returns null if not a valid tag.
     */
    // TODO not used at the moment, might be useful for HTML parsing later
    private fun parseInlineHtmlTag(content: String): InlineHtmlTag? {
        if (content.isEmpty()) return null

        // Closing tag: /tagname with optional whitespace
        if (content.startsWith("/")) {
            val tagName = content.substring(1).trim()
            if (tagName.isEmpty() || !tagName[0].isLetter()) return null
            if (!tagName.all { it.isLetterOrDigit() || it == '-' }) return null
            return InlineHtmlTag(tagName, isClose = true, isSelfClosing = false, attributes = null)
        }

        // Opening tag: tagname followed by optional attributes and optional /
        if (!content[0].isLetter()) return null

        var i = 0
        while (i < content.length && (content[i].isLetterOrDigit() || content[i] == '-')) i++
        val tagName = content.substring(0, i)
        if (tagName.isEmpty()) return null

        val rest = content.substring(i).trim()
        val isSelfClosing = rest.endsWith("/")
        val attrStr = if (isSelfClosing) rest.dropLast(1).trim() else rest

        val attributes = if (attrStr.isNotEmpty()) parseAttributes(attrStr) else null

        return InlineHtmlTag(tagName, isClose = false, isSelfClosing = isSelfClosing, attributes = attributes)
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
                    blockMode = BlockMode.Start
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
                blockMode = mode
                // Feed every character of the first line into the type-1 processor.
                for (c in line) processHtmlBlock1(c, mode)
                processHtmlBlock1('\n', mode)
            }
            2, 3, 4, 5 -> {
                val closingSeq = when (type) {
                    2 -> "-->"
                    3 -> "?>"
                    4 -> ">"
                    5 -> "]]>"
                    else -> error("unreachable")
                }
                +"$line\n"
                if (lineContainsClosingSeq(line, closingSeq)) {
                    blockMode = BlockMode.Start
                } else {
                    blockMode = BlockMode.HtmlBlock2to5(closingSeq)
                }
            }
            6, 7 -> {
                val (rootName, isClose) = type6or7RootTagOf(line) ?: run {
                    +"$line\n"
                    return
                }
                val mode = BlockMode.HtmlBlock6or7(rootName, isClose)
                blockMode = mode
                // Buffer the first line into firstLineBuffer for tag-completion checks.
                mode.firstLineBuffer!!.append(line)
                tryFinishHtmlBlock6or7FirstLine(mode)
            }
            else -> +"$line\n"
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
        // Tokenize content before the root close to recognize any nested inner close tags.
        val before = line.substring(0, closeIndex)
        if (before.isNotEmpty()) {
            val beforeTokens = tokenizeHtmlLine(before)
            val text = StringBuilder()
            for (tok in beforeTokens) {
                when (tok) {
                    is HtmlToken.Text -> text.append(tok.content)
                    is HtmlToken.CloseTag -> {
                        if (text.isNotEmpty()) { +text.toString(); text.clear() }
                        if (mode.openTags.isNotEmpty() && mode.openTags.last() == tok.name) {
                            mode.openTags.removeLast()
                        }
                        unmark(tok.name, isTagged = true)
                    }
                    is HtmlToken.OpenTag -> {
                        if (text.isNotEmpty()) { +text.toString(); text.clear() }
                        mark(tok.name, isTagged = true, attributes = tok.attributes)
                        if (tok.selfClosing) unmark(tok.name, isTagged = true)
                        else mode.openTags.add(tok.name)
                    }
                }
            }
            if (text.isNotEmpty()) +text.toString()
        }
        // Close any nested still-open tags then the root.
        while (mode.openTags.size > 1) {
            unmark(mode.openTags.removeLast(), isTagged = true)
        }
        if (mode.openTags.isNotEmpty()) {
            unmark(mode.openTags.removeLast(), isTagged = true)
        }
        // Trailing content after the close becomes a top-level text event with newline.
        val trailing = line.substring(closeEnd)
        if (trailing.isNotEmpty()) {
            +"$trailing\n"
        }
        blockMode = BlockMode.Start
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
            opens.add(open.second)
            if (open.second.name.lowercase() == mode.rootTag) foundRoot = true
            idx = open.first
        }
        if (!foundRoot) return false
        // Successfully parsed up to and including the root tag.
        if (leading.isNotEmpty()) +leading
        for (tag in opens) {
            mark(tag.name, isTagged = true, attributes = tag.attributes)
            mode.openTags.add(tag.name)
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
            blockMode = BlockMode.Start
        }
    }

    /** Process a character inside an HTML block of type 6 or 7 (block-level / type-7 tag). */
    private suspend fun SemanticEventScope.processHtmlBlock6or7(
        char: Char,
        mode: BlockMode.HtmlBlock6or7
    ) {
        // Phase 1: buffer the first line until the opening tag's `>` is matched.
        val firstLineBuffer = mode.firstLineBuffer
        if (firstLineBuffer != null) {
            if (char == '\n') {
                tryFinishHtmlBlock6or7FirstLine(mode)
                return
            }
            firstLineBuffer.append(char)
            return
        }
        // Phase 2: buffer subsequent lines.
        if (char != '\n') {
            lineBuffer.append(char)
            return
        }
        val line = lineBuffer.toString()
        lineBuffer.clear()
        if (line.isEmpty()) {
            if (!mode.blankLineSeen) {
                mode.blankLineSeen = true
            } else {
                mode.postBlankLines.add("")
            }
            return
        }
        if (!mode.blankLineSeen) {
            mode.preBlankLines.add(line)
            if (mode.rootCloseInPreBlank < 0 &&
                findRootCloseTagIndex(line, mode.rootTagName) >= 0
            ) {
                mode.rootCloseInPreBlank = mode.preBlankLines.size - 1
            }
        } else {
            mode.postBlankLines.add(line)
            if (mode.rootCloseInPostBlank < 0 &&
                findRootCloseTagIndex(line, mode.rootTagName) >= 0
            ) {
                mode.rootCloseInPostBlank = mode.postBlankLines.size - 1
                emitHtmlBlock6or7AndExit(mode)
            }
        }
    }

    /**
     * Try to parse the first opening tag from `mode.firstLineBuffer`. Returns true if
     * the opening tag was completed (advances mode out of phase 1).
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
        // Tag complete.
        mode.firstLineRaw = source
        mode.firstLineTokens = tokenizeHtmlLine(source)
        mode.firstLineBuffer = null
        // Detect if the matching root close also appears on the first line.
        if (findRootCloseTagIndex(source, mode.rootTagName) >= 0 && !mode.rootIsClosingTag) {
            mode.rootCloseInFirstLine = true
        }
        return true
    }

    /**
     * Emit the buffered Type 6/7 block and exit to BlockMode.Start. If a matching root
     * closing tag was observed, emit the block structurally; otherwise emit raw text.
     */
    private suspend fun SemanticEventScope.emitHtmlBlock6or7AndExit(
        mode: BlockMode.HtmlBlock6or7
    ) {
        val structural = mode.rootCloseInFirstLine ||
            mode.rootCloseInPreBlank >= 0 ||
            mode.rootCloseInPostBlank >= 0
        if (structural) {
            emitHtmlBlock6or7Structurally(mode)
        } else {
            emitHtmlBlock6or7AsText(mode)
        }
        blockMode = BlockMode.Start
    }

    /** Structural emission for closed type-6/7 blocks. */
    private suspend fun SemanticEventScope.emitHtmlBlock6or7Structurally(
        mode: BlockMode.HtmlBlock6or7
    ) {
        val tokens = mode.firstLineTokens ?: return
        if (mode.rootCloseInFirstLine) {
            // Emit first line tokens (root open + close + any extras) and remaining lines as raw text.
            for (tok in tokens) {
                when (tok) {
                    is HtmlToken.OpenTag -> {
                        mark(tok.name, isTagged = true, attributes = tok.attributes)
                        if (tok.selfClosing) unmark(tok.name, isTagged = true)
                    }
                    is HtmlToken.CloseTag -> unmark(tok.name, isTagged = true)
                    is HtmlToken.Text -> +tok.content
                }
            }
            // Pre-blank lines (post-close): emit as raw text events.
            for (ln in mode.preBlankLines) +"$ln\n"
            // Post-blank: untagged markdown.
            if (mode.blankLineSeen && mode.postBlankLines.isNotEmpty()) {
                emitUntaggedMarkdown(mode.postBlankLines)
            }
            return
        }
        // First line opens but does not close. Emit first-line tokens (mark for opens).
        for (tok in tokens) {
            when (tok) {
                is HtmlToken.OpenTag -> {
                    mark(tok.name, isTagged = true, attributes = tok.attributes)
                    if (tok.selfClosing) unmark(tok.name, isTagged = true)
                }
                is HtmlToken.CloseTag -> unmark(tok.name, isTagged = true)
                is HtmlToken.Text -> +tok.content
            }
        }
        // Pre-blank lines: emit as inline tokens with proper newlines.
        if (mode.rootCloseInPreBlank >= 0) {
            for (i in 0 until mode.rootCloseInPreBlank) {
                emitTokensInBlock(mode.preBlankLines[i], mode.rootTagName, isLast = false)
            }
            emitTokensInBlock(
                mode.preBlankLines[mode.rootCloseInPreBlank],
                mode.rootTagName,
                isLast = true
            )
            // After close, remaining pre-blank lines emit as text.
            for (i in (mode.rootCloseInPreBlank + 1) until mode.preBlankLines.size) {
                +"${mode.preBlankLines[i]}\n"
            }
            // Post-blank as untagged markdown if present.
            if (mode.blankLineSeen && mode.postBlankLines.isNotEmpty()) {
                emitUntaggedMarkdown(mode.postBlankLines)
            }
        } else {
            // Close is in post-blank.
            for (ln in mode.preBlankLines) {
                emitTokensInBlock(ln, mode.rootTagName, isLast = false)
            }
            // Sub-parse post-blank lines before the close as untagged markdown.
            if (mode.rootCloseInPostBlank >= 0) {
                val subLines = mode.postBlankLines.subList(0, mode.rootCloseInPostBlank).toList()
                if (subLines.isNotEmpty()) emitUntaggedMarkdown(subLines)
                emitTokensInBlock(
                    mode.postBlankLines[mode.rootCloseInPostBlank],
                    mode.rootTagName,
                    isLast = true
                )
            }
        }
    }

    /** Emit tokens in a content line of a structural type-6/7 block. */
    private suspend fun SemanticEventScope.emitTokensInBlock(
        line: String,
        rootTagName: String,
        isLast: Boolean
    ) {
        val tokens = tokenizeHtmlLine(line)
        // Helper: when the trailing run of text tokens ends the line, coalesce + \n.
        val lastTextRunStart = run {
            var idx = tokens.size
            while (idx > 0 && tokens[idx - 1] is HtmlToken.Text) idx--
            idx
        }
        var done = false
        for ((i, tok) in tokens.withIndex()) {
            if (done) {
                // After root close on the last line, emit any trailing tokens as raw text events.
                when (tok) {
                    is HtmlToken.Text -> +tok.content
                    is HtmlToken.OpenTag -> {
                        mark(tok.name, isTagged = true, attributes = tok.attributes)
                        if (tok.selfClosing) unmark(tok.name, isTagged = true)
                    }
                    is HtmlToken.CloseTag -> unmark(tok.name, isTagged = true)
                }
                continue
            }
            when (tok) {
                is HtmlToken.OpenTag -> {
                    mark(tok.name, isTagged = true, attributes = tok.attributes)
                    if (tok.selfClosing) unmark(tok.name, isTagged = true)
                }
                is HtmlToken.CloseTag -> {
                    unmark(tok.name, isTagged = true)
                    if (isLast && tok.name.lowercase() == rootTagName) {
                        done = true
                    }
                }
                is HtmlToken.Text -> {
                    if (i >= lastTextRunStart) {
                        // Build the trailing text run and emit with \n at end (if not last+close-emitted).
                        val sb = StringBuilder()
                        for (j in i until tokens.size) {
                            sb.append((tokens[j] as HtmlToken.Text).content)
                        }
                        if (isLast) +sb.toString() else +"${sb}\n"
                        return
                    } else {
                        +tok.content
                    }
                }
            }
        }
        // No trailing text on this line. If not the last (close) line, emit \n.
        if (!isLast && !done) +"\n"
    }

    /** Sub-parse the given lines as fresh markdown and emit events wrapped in untagged{}. */
    private suspend fun SemanticEventScope.emitUntaggedMarkdown(lines: List<String>) {
        if (lines.isEmpty()) return
        // TODO commented out on the architecture change, some equivalent might be needed for HTML parsing
        //val sub = DefaultMarkanywhereParser()
        val content = lines.joinToString("\n") + "\n"
        val flow = kotlinx.coroutines.flow.flowOf(content)
        untagged {
//            sub.parse(flow).collect { ev ->
//                when (ev) {
//                    is com.xemantic.markanywhere.SemanticEvent.Text -> text(ev.text)
//                    is com.xemantic.markanywhere.SemanticEvent.Mark -> mark(ev.name, ev.isTagged, ev.attributes)
//                    is com.xemantic.markanywhere.SemanticEvent.Unmark -> unmark(ev.name, ev.isTagged)
//                }
//            }
        }
    }

    /** Raw-text emission for unclosed type-6/7 blocks. */
    private suspend fun SemanticEventScope.emitHtmlBlock6or7AsText(
        mode: BlockMode.HtmlBlock6or7
    ) {
        // First line may have spanned multiple source lines (incomplete opening tag).
        val rawLines = mode.firstLineRaw.split('\n')
        for ((i, ln) in rawLines.withIndex()) {
            if (i == rawLines.lastIndex && ln.isEmpty() && rawLines.size > 1) continue
            +"$ln\n"
        }
        for (ln in mode.preBlankLines) {
            +"$ln\n"
        }
        if (mode.blankLineSeen && mode.postBlankLines.isNotEmpty()) {
            emitUntaggedMarkdown(mode.postBlankLines)
        }
    }

    /** Find the index of the matching root close tag `</rootName>` (case-insensitive) in [line], or -1. */
    private fun findRootCloseTagIndex(line: String, rootTagName: String): Int {
        val pattern = "</$rootTagName"
        val lower = line.lowercase()
        var idx = lower.indexOf(pattern)
        while (idx >= 0) {
            val afterName = idx + pattern.length
            var i = afterName
            while (i < line.length && (line[i] == ' ' || line[i] == '\t')) i++
            if (i < line.length && line[i] == '>') return idx
            idx = lower.indexOf(pattern, idx + 1)
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
        }
    }

    private suspend fun SemanticEventScope.processInlineCharImpl(char: Char) {
        // Handle escaping
        if (escaped) {
            escaped = false
            // If we are inside an inline HTML tag accumulation, backslashes are literal.
            if (inlineBuffer.startsWith("<")) {
                inlineBuffer.append(char)
                return
            }
            +char
            return
        }
        if (char == '\\') {
            // Inside an inline HTML tag accumulation, the backslash is literal too —
            // preserve it in the buffer so attribute values keep `\` characters.
            if (inlineBuffer.startsWith("<")) {
                inlineBuffer.append('\\')
                return
            }
            if (inlineBuffer.isNotEmpty()) {
                +inlineBuffer.toString()
                inlineBuffer.clear()
            }
            escaped = true
            return
        }

        // Inside code — close on a backtick run that exactly matches the opening run length (GFM §6.1).
        if (code) {
            if (codeRunLength == 1) {
                // Single-tick code: stream content; any backtick closes.
                if (char == '`') {
                    unmark("code")
                    code = false
                    codeRunLength = 0
                } else {
                    +char
                }
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
                if (content.startsWith(" ") && content.endsWith(" ") && content.length > 1) {
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

        // Handle image states (check before link since both use inLinkUrl)
        if (inImage && !inLinkUrl) {
            if (char == ']') {
                inlineBuffer.append(']')
            } else if (char == '(' && inlineBuffer.endsWith("]")) {
                inlineBuffer.clear()
                inLinkUrl = true
            } else {
                imageAlt.append(char)
            }
            return
        }

        if (inImage) { // inLinkUrl is always true at this point
            when (char) {
                ')' -> {
                    "img"(
                        "src" to imageUrl.toString().trim(),
                        "alt" to imageAlt.toString()
                    ) {}
                    inImage = false
                    inLinkUrl = false
                    imageAlt.clear()
                    imageUrl.clear()
                }
                else -> imageUrl.append(char)
            }
            return
        }

        // Handle link states
        if (inLink && !inLinkUrl) {
            if (char == ']') {
                inlineBuffer.append(']')
            } else if (char == '(' && inlineBuffer.endsWith("]")) {
                inlineBuffer.clear()
                inLinkUrl = true
            } else {
                linkText.append(char)
            }
            return
        }

        if (inLink) { // inLinkUrl is always true at this point
            when (char) {
                ')' -> {
                    val urlPart = linkUrl.toString().trim()
                    val title = linkTitle.toString().trim()
                    val url = urlPart.substringBefore(" \"").trim()
                    val extractedTitle = if (urlPart.contains(" \"")) {
                        urlPart.substringAfter(" \"").removeSuffix("\"").trim()
                    } else {
                        title
                    }
                    val attrs = if (extractedTitle.isNotEmpty()) {
                        mapOf("href" to url, "title" to extractedTitle)
                    } else {
                        mapOf("href" to url)
                    }
                    "a"(attributes = attrs) {
                        +linkText.toString()
                    }
                    inLink = false
                    inLinkUrl = false
                    linkText.clear()
                    linkUrl.clear()
                    linkTitle.clear()
                }
                else -> linkUrl.append(char)
            }
            return
        }

        // Autolinks and inline HTML tags
        if (inlineBuffer.startsWith("<")) {
            if (char == '>') {
                val content = inlineBuffer.substring(1)
                inlineBuffer.clear()
                when {
                    // Check inline HTML tags BEFORE autolinks (tags can contain ://)
                    !content.contains(" ") && content.contains("@") && !content.contains("://") -> {
                        "a"("href" to "mailto:$content") {
                            +content
                        }
                    }
                    !content.contains(" ") && content.contains("://") -> {
                        "a"("href" to content) {
                            +content
                        }
                    }
                    else -> {
                        val full = "<$content>"
                        val open = tryParseOpenTag(full, 0)
                        val close = if (open == null) tryParseCloseTag(full, 0) else null
                        if (open != null && open.first == full.length &&
                            open.second.name.lowercase() !in INLINE_HTML_BLOCK_ELEMENTS
                        ) {
                            val tag = open.second
                            mark(tag.name, isTagged = true, attributes = tag.attributes)
                            if (tag.selfClosing) unmark(tag.name, isTagged = true)
                        } else if (close != null && close.first == full.length &&
                            close.second.name.lowercase() !in INLINE_HTML_BLOCK_ELEMENTS
                        ) {
                            unmark(close.second.name, isTagged = true)
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
                if (subscript) {
                    unmark("sub")
                    subscript = false
                } else {
                    mark("sub")
                    subscript = true
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
            char == '$' -> {
                if (inlineBuffer.isNotEmpty()) {
                    +inlineBuffer.toString()
                    inlineBuffer.clear()
                }
                if (math) {
                    unmark("math")
                    math = false
                } else {
                    mark("math")
                    math = true
                }
            }
            // Exclamation buffer checks (before char == '[' for image syntax)
            inlineBuffer.toString() == "!" && char == '[' -> {
                inlineBuffer.clear()
                inImage = true
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
            else -> {
                if (inlineBuffer.isNotEmpty()) {
                    +inlineBuffer.toString()
                    inlineBuffer.clear()
                }
                +char
            }
        }
    }

    private suspend fun SemanticEventScope.flushInline() {
        // Close inline code first so a buffered close-run (N≥2 backticks) at line/block
        // end is recognized as a valid close, not flushed as content + force-close.
        if (code) {
            var trail = 0
            while (trail < inlineBuffer.length &&
                inlineBuffer[inlineBuffer.length - 1 - trail] == '`'
            ) trail++
            if (trail == codeRunLength && codeRunLength >= 2) {
                var content = inlineBuffer.substring(0, inlineBuffer.length - trail)
                if (content.startsWith(" ") && content.endsWith(" ") && content.length > 1) {
                    content = content.substring(1, content.length - 1)
                }
                +content
            } else if (inlineBuffer.isNotEmpty()) {
                +inlineBuffer.toString()
            }
            inlineBuffer.clear()
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
                "~~" -> when {
                    strikethrough -> { unmark("del"); strikethrough = false }
                    else -> { mark("del"); strikethrough = true }
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
        if (subscript) {
            unmark("sub")
            subscript = false
        }
        if (strikethrough) {
            unmark("del")
            strikethrough = false
        }
        if (italic) {
            unmark("em")
            italic = false
        }
        if (bold) {
            unmark("strong")
            bold = false
        }
        escaped = false
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
        // Handle any remaining content based on mode
        when (val mode = blockMode) {
            is Heading -> {
                flushInline()
                unmark("h${mode.level}")
            }
            Paragraph -> {
                flushInline()
                unmark("p")
            }
            ParagraphContinuation -> {
                if (lineBuffer.isNotEmpty()) {
                    val line = lineBuffer.toString()
                    lineBuffer.clear()
                    if (lineInterruptsParagraph(line)) {
                        unmark("p")
                        blockMode = Start
                        replay(line)
                        process('\n')
                        finalize()  // re-finalize after replaying
                        return
                    }
                    +"\n"
                    processInlineContent(line)
                    flushInline()
                }
                unmark("p")
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
            }
            UnorderedList -> {
                if (inListItem) {
                    flushInline()
                    unmark("li")
                }
                unmark("ul")
            }
            OrderedList -> {
                if (inListItem) {
                    flushInline()
                    unmark("li")
                }
                unmark("ol")
            }
            Blockquote -> {
                if (inBlockquoteParagraph) {
                    flushInline()
                    unmark("p")
                }
                unmark("blockquote")
            }
            is BlockquoteCode -> {
                if (lineBuffer.isNotEmpty()) {
                    val line = lineBuffer.toString()
                    lineBuffer.clear()
                    if (line.startsWith(">")) {
                        val stripped = if (line.startsWith("> ")) line.substring(2)
                        else line.substring(1)
                        if (!isFenceClose(stripped, mode.marker, mode.length)) {
                            +"${stripIndentCols(stripped, mode.indent)}\n"
                        }
                    }
                }
                unmark("code")
                unmark("pre")
                unmark("blockquote")
                inBlockquoteParagraph = false
            }
            BlockquoteList -> {
                if (inListItem) {
                    flushInline()
                    unmark("li")
                }
                unmark("ul")
                unmark("blockquote")
            }
            MathBlock -> {
                if (lineBuffer.isNotEmpty()) {
                    +lineBuffer.toString()
                }
                unmark("math")
            }
            Table -> {
                unmark("thead")
                unmark("table")
            }
            TableBody -> {
                // Process any pending row in lineBuffer
                if (lineBuffer.isNotEmpty()) {
                    val line = lineBuffer.toString().trimEnd()
                    if (line.startsWith("|")) {
                        "tr" {
                            emitTableRow(line, isHeader = false)
                        }
                    }
                }
                unmark("tbody")
                unmark("table")
            }
            Start -> {
                // Trigger end-of-line processing for any buffered content; this lets
                // block-level detection (indented code, ATX heading, thematic break,
                // etc.) run, and we then recurse to close whatever mode it opened.
                if (lineBuffer.isNotEmpty()) {
                    process('\n')
                    if (blockMode != Start) finalize()
                }
            }
            IndentedCodeBlock -> {
                // Drop trailing blank lines; a final partial line, if present, is content.
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
            }
            is ListBlock -> {
                // Drain any partial trailing line through the streaming machine.
                if (lineBuffer.isNotEmpty()) {
                    process('\n')
                    if (blockMode != mode) {
                        // The trailing line ended the list and replayed as a new block.
                        if (blockMode != Start) finalize()
                        return
                    }
                }
                popListContexts(mode, downTo = 0)
                blockMode = Start
            }
            is CustomMarkup -> {
                // Emit any buffered content from incomplete closing tag detection
                if (customMarkupClosingBuffer.isNotEmpty()) {
                    +customMarkupClosingBuffer.toString()
                    customMarkupClosingBuffer.clear()
                    customMarkupInClosingTag = false
                }
                unmark(mode.tagName, isTagged = true)
            }
            is HtmlBlock1 -> {
                if (mode.firstLineBuffer != null) {
                    // Opening tag never completed: emit raw text fallback.
                    val raw = mode.firstLineBuffer!!.toString()
                    if (raw.isNotEmpty()) +"$raw\n"
                    mode.firstLineBuffer = null
                } else {
                    if (lineBuffer.isNotEmpty()) {
                        +"${lineBuffer.toString()}\n"
                        lineBuffer.clear()
                    }
                    // Auto-close any tags opened in the block.
                    while (mode.openTags.isNotEmpty()) {
                        unmark(mode.openTags.removeLast(), isTagged = true)
                    }
                }
            }
            is HtmlBlock2to5 -> {
                if (lineBuffer.isNotEmpty()) {
                    +"${lineBuffer.toString()}\n"
                    lineBuffer.clear()
                }
            }
            is HtmlBlock6or7 -> {
                if (mode.firstLineBuffer != null) {
                    val raw = mode.firstLineBuffer!!.toString()
                    mode.firstLineRaw = raw
                    mode.firstLineTokens = emptyList()
                    mode.firstLineBuffer = null
                }
                if (lineBuffer.isNotEmpty()) {
                    val pending = lineBuffer.toString()
                    if (!mode.blankLineSeen) {
                        mode.preBlankLines.add(pending)
                        if (mode.rootCloseInPreBlank < 0 &&
                            findRootCloseTagIndex(pending, mode.rootTagName) >= 0
                        ) {
                            mode.rootCloseInPreBlank = mode.preBlankLines.size - 1
                        }
                    } else {
                        mode.postBlankLines.add(pending)
                        if (mode.rootCloseInPostBlank < 0 &&
                            findRootCloseTagIndex(pending, mode.rootTagName) >= 0
                        ) {
                            mode.rootCloseInPostBlank = mode.postBlankLines.size - 1
                        }
                    }
                    lineBuffer.clear()
                }
                emitHtmlBlock6or7AndExit(mode)
            }
        }
    }
}
