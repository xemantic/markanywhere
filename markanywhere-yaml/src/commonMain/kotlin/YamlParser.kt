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

package com.xemantic.markanywhere.yaml

import com.xemantic.markanywhere.SemanticEvent
import kotlinx.coroutines.flow.FlowCollector

/**
 * Streaming, line-driven parser of a YAML document into semantic events.
 *
 * Fed one line at a time via [line] (the [parseYaml] flow operator splits
 * chunks into lines for you; `FrontMatterFilter` in `markanywhere-parse`
 * feeds the body of a `---` front matter block, owning the fences and the
 * enclosing `frontmatter` mark itself), then [finish] closes whatever is
 * still open.
 *
 * Vocabulary:
 * - `entry` (attribute `key`) — a mapping entry; its value is the content.
 * - `item` — a sequence element.
 * - A scalar is the text inside; a mapping is nested `entry` marks; a
 *   sequence is nested `item` marks. The root is a mapping (top-level
 *   `entry` marks) or a sequence (top-level `item` marks) — no wrapper mark.
 * - A non-string scalar carries `type` = `bool` / `int` / `float` / `null` /
 *   `timestamp`: typed exactly when a front matter reader — the YAML 1.2
 *   core schema, or the YAML 1.1 shapes of Psych (Jekyll), PyYAML and
 *   go-yaml v2 (Hugo) — reads it as other than a string (`y`, `yEs`,
 *   `1_000`, `0X1F`, `12:30`, `2024-5-1`), so a Jekyll / Hugo document
 *   round-trips as written. A number keeps that reader's syntax (separators,
 *   base prefixes, base 60) in its text. An empty
 *   flow collection carries `type` = `seq` / `map`. An empty string is an
 *   entry with no text and no type; a missing value (`key:`) is `type=null`.
 *
 * Supported: block mappings and sequences (a sequence may sit at its key's
 * own indentation), nesting, plain / double-quoted / single-quoted scalars,
 * block scalars (`|`, `>`, chomping `-`/`+`, indentation indicator),
 * single-line flow sequences and mappings, comments (dropped), blank lines,
 * duplicate keys (all kept, in order).
 *
 * DIVERGENCE (verbatim fallback): a line the subset does not understand —
 * complex keys, directives, multi-line flow collections or quoted scalars,
 * a plain scalar's continuation line, a `- item` inside a mapping, or a
 * shape the front matter readers read differently from one another (a
 * flow scalar's `:` before `,` / `[` / `]` / `{` / `}`, a block scalar
 * header followed by `#` with no space) — is
 * emitted **verbatim** (with its `\n`) as a text child of the container it
 * sits in, so nothing is lost and [YamlWriter] can write it back as-is.
 * Anchors, aliases and tags are not resolved: a plain scalar starting with
 * `&` / `*` / `!` is just a string. Document markers (`---`, `...`) and
 * directives are not recognised — a multi-document stream is outside the
 * subset.
 *
 * DIVERGENCE (lenient plain scalars): a mapping entry's plain value
 * containing a mapping indicator — `k: Note: see`, `k: ends:` — is read as
 * the string after the first `: `, where YAML (Psych, PyYAML) rejects the
 * line. A sequence item is not lenient: `- Note: see` is a compact mapping
 * (an item holding the entry `Note`), as in YAML. This does not make such a
 * value safe to write plain: [YamlWriter] still quotes it.
 *
 * Streaming: a `key: value` line commits on its newline. A bare `key:` (or
 * `-`) is held for one line to decide between a nested block and a null
 * value. A block scalar is buffered until it closes — never past the
 * enclosing construct. Never throws.
 */
public class YamlParser(
    private val downstream: FlowCollector<SemanticEvent>
) {

    // An open container: the mapping / sequence whose entries sit at [indent].
    // [owner] is the name of the entry / item mark that owns it (null for
    // the root mapping).
    private class Frame(
        val indent: Int,
        val isSequence: Boolean,
        val owner: String?
    )

    // An entry / item whose value has not started on its own line (`key:` or
    // a bare `-`): the next content line decides nested block vs. null, so
    // the mark is not emitted yet (its `type` depends on the decision).
    private class Pending(
        val indent: Int,
        val isItem: Boolean,
        val key: String?
    )

    private class BlockScalar(
        val owner: String,
        val folded: Boolean,
        val chomp: Char,
        // absolute content indentation from an explicit indicator, else -1
        val explicitIndent: Int,
        val parentIndent: Int
    ) {
        var contentIndent = -1
        val lines = mutableListOf<String>()
    }

    private sealed interface Value {
        data object Pending : Value
        class Scalar(val text: String, val type: String?) : Value
        class Block(val folded: Boolean, val chomp: Char, val indentDigit: Int) : Value
        class Seq(val items: List<Value>) : Value
        class Map(val entries: List<Pair<String, Value>>) : Value
    }

    private val frames = ArrayDeque<Frame>()
    private var pending: Pending? = null
    private var block: BlockScalar? = null

    /** Feeds one source line, without its line terminator. */
    public suspend fun line(line: String) {
        block?.let { if (feedBlock(it, line)) return }
        val indent = leadingSpaces(line)
        if (indent == line.length || line[indent] == '#') return // blank / comment
        processContent(line, indent, line.substring(indent))
    }

    /** Closes every open construct; the parser is then ready for a new document. */
    public suspend fun finish() {
        block?.let { closeBlock(it) }
        pending?.let { resolvePendingAsNull(it) }
        pending = null
        while (frames.size > 1) closeTopFrame()
        frames.clear()
    }

    private suspend fun processContent(line: String, indent: Int, content: String) {
        if (frames.isEmpty()) {
            // the root's kind and indentation are whatever the first line uses
            frames.addLast(Frame(indent, isSequence = isSequenceEntry(content), owner = null))
        }
        val seqLine = isSequenceEntry(content)
        pending?.let { p ->
            pending = null
            if (indent > p.indent || (indent == p.indent && seqLine && !p.isItem)) {
                val owner = if (p.isItem) ITEM else ENTRY
                emitMark(owner, p.key, type = null)
                frames.addLast(Frame(indent, seqLine, owner))
            } else {
                resolvePendingAsNull(p)
            }
        }
        while (frames.size > 1 && frames.last().indent > indent) closeTopFrame()
        // a sequence at its key's own indentation ends at the first non-`-` line
        while (frames.size > 1 && frames.last().isSequence && !seqLine
            && frames.last().indent == indent
        ) closeTopFrame()
        val top = frames.last()
        when {
            top.indent != indent -> verbatim(line)
            top.isSequence -> if (seqLine) item(line, indent, content) else verbatim(line)
            seqLine -> verbatim(line)
            else -> entry(line, indent, content)
        }
    }

    private suspend fun entry(line: String, indent: Int, content: String) {
        val (key, rest) = parseKey(content) ?: return verbatim(line)
        val value = parseValue(rest) ?: return verbatim(line)
        emitValue(ENTRY, key, indent, value)
    }

    // [content] is `-` or starts with `- `; the value after the dash is parsed
    // as if it were a line at the column where it starts.
    private suspend fun item(line: String, indent: Int, content: String) {
        val after = content.substring(1)
        val spaces = leadingSpaces(after)
        val inner = after.substring(spaces)
        val col = indent + 1 + spaces
        when {
            inner.isEmpty() || inner[0] == '#' ->
                pending = Pending(indent, isItem = true, key = null)
            isSequenceEntry(inner) -> {
                emitMark(ITEM, null, null)
                frames.addLast(Frame(col, isSequence = true, owner = ITEM))
                item(line, col, inner)
            }
            else -> {
                val keyed = parseKey(inner)
                if (keyed != null) {
                    val value = parseValue(keyed.second) ?: return verbatim(line)
                    emitMark(ITEM, null, null)
                    frames.addLast(Frame(col, isSequence = false, owner = ITEM))
                    emitValue(ENTRY, keyed.first, col, value)
                } else {
                    val value = parseValue(inner) ?: return verbatim(line)
                    emitValue(ITEM, null, indent, value)
                }
            }
        }
    }

    private suspend fun emitValue(owner: String, key: String?, indent: Int, value: Value) {
        when (value) {
            Value.Pending -> pending = Pending(indent, owner == ITEM, key)
            is Scalar -> {
                emitMark(owner, key, value.type)
                if (value.text.isNotEmpty()) emitText(value.text)
                emitUnmark(owner)
            }
            is Block -> {
                emitMark(owner, key, null)
                block = BlockScalar(
                    owner = owner,
                    folded = value.folded,
                    chomp = value.chomp,
                    explicitIndent = if (value.indentDigit > 0) indent + value.indentDigit else -1,
                    parentIndent = indent
                )
            }
            is Seq -> {
                emitMark(owner, key, if (value.items.isEmpty()) "seq" else null)
                for (item in value.items) emitValue(ITEM, null, indent, item)
                emitUnmark(owner)
            }
            is Value.Map -> {
                emitMark(owner, key, if (value.entries.isEmpty()) "map" else null)
                for ((k, v) in value.entries) emitValue(ENTRY, k, indent, v)
                emitUnmark(owner)
            }
        }
    }

    private suspend fun resolvePendingAsNull(p: Pending) {
        val owner = if (p.isItem) ITEM else ENTRY
        emitMark(owner, p.key, "null")
        emitUnmark(owner)
    }

    private suspend fun closeTopFrame() {
        val frame = frames.removeLast()
        frame.owner?.let { emitUnmark(it) }
    }

    private suspend fun verbatim(line: String) {
        emitText(line + "\n")
    }

    // --- block scalars ----------------------------------------------------

    // Returns true when the line was consumed as block scalar content; false
    // when it ends the scalar (and must be processed as a regular line).
    private suspend fun feedBlock(b: BlockScalar, line: String): Boolean {
        val indent = leadingSpaces(line)
        val blank = indent == line.length
        if (b.contentIndent < 0) {
            if (blank) {
                b.lines += ""
                return true
            }
            val contentIndent = if (b.explicitIndent >= 0) b.explicitIndent else indent
            if (indent <= b.parentIndent || indent < contentIndent) {
                closeBlock(b)
                return false
            }
            b.contentIndent = contentIndent
            b.lines += line.substring(contentIndent)
            return true
        }
        if (blank) {
            b.lines += if (line.length > b.contentIndent) line.substring(b.contentIndent) else ""
            return true
        }
        if (indent < b.contentIndent) {
            closeBlock(b)
            return false
        }
        b.lines += line.substring(b.contentIndent)
        return true
    }

    private suspend fun closeBlock(b: BlockScalar) {
        block = null
        val lines = b.lines
        var end = lines.size
        while (end > 0 && lines[end - 1].isEmpty()) end--
        val trailing = lines.size - end
        val body = lines.subList(0, end)
        val content = if (b.folded) fold(body) else body.joinToString("\n")
        val text = when {
            content.isEmpty() -> if (b.chomp == '+') "\n".repeat(lines.size) else ""
            b.chomp == '-' -> content
            b.chomp == '+' -> content + "\n" + "\n".repeat(trailing)
            else -> content + "\n"
        }
        if (text.isNotEmpty()) emitText(text)
        emitUnmark(b.owner)
    }

    // YAML 1.2 §8.1.3 line folding: every empty line is a line break (also
    // before the first content line); the break between two normal lines is
    // a space, or nothing when empty lines separate them (the break is
    // "trimmed", the empty lines already broke the line); a break next to a
    // more-indented line is never folded.
    private fun fold(lines: List<String>): String {
        val sb = StringBuilder()
        // the kind of the last content line, and whether an empty line was
        // seen since it
        var last: LineKind = NONE
        var emptySince = false
        for (line in lines) {
            val kind: LineKind = when {
                line.isEmpty() -> EMPTY
                line[0] == ' ' || line[0] == '\t' -> MORE_INDENTED
                else -> NORMAL
            }
            if (kind == EMPTY) {
                sb.append('\n')
                emptySince = true
                continue
            }
            when {
                last == NONE -> {}
                last == NORMAL && kind == NORMAL -> if (!emptySince) sb.append(' ')
                else -> sb.append('\n')
            }
            sb.append(line)
            last = kind
            emptySince = false
        }
        return sb.toString()
    }

    private enum class LineKind { NONE, EMPTY, NORMAL, MORE_INDENTED }

    // --- line-level parsing -----------------------------------------------

    // Splits `key: rest` — returns the decoded key and everything after the
    // colon, or null when the content is not a mapping entry.
    private fun parseKey(content: String): Pair<String, String>? {
        val first = content[0]
        // a tab is not YAML indentation, so a tab-led line is not an entry
        if (first == '\t') return null
        if (first == '"' || first == '\'') {
            val (key, end) = scanQuoted(content) ?: return null
            var i = end
            while (i < content.length && content[i] == ' ') i++
            if (i >= content.length || !content.isMappingColonAt(i)) return null
            return key to content.substring(i + 1)
        }
        if (first in KEY_FORBIDDEN_START) return null
        if (content == "?" || content.startsWith("? ")) return null
        var i = 0
        while (i < content.length) {
            if (content.isMappingColonAt(i)) break
            if (content.isCommentStartAt(i)) return null
            i++
        }
        if (i == content.length) return null
        val key = content.substring(0, i).trimEnd()
        if (key.isEmpty()) return null
        return key to content.substring(i + 1)
    }

    // Parses the value part of an entry / item line; null when the shape is
    // outside the subset (the caller then keeps the whole line verbatim).
    private fun parseValue(rest: String): Value? {
        val s = rest.trimStart(' ', '\t')
        if (s.isEmpty() || s[0] == '#') return Value.Pending
        return when (s[0]) {
            '|', '>' -> parseBlockHeader(s)
            '[', '{' -> parseFlow(s)
            '"', '\'' -> {
                val (text, end) = scanQuoted(s) ?: return null
                if (!isBlankOrComment(s, end)) return null
                Value.Scalar(text, null)
            }
            else -> {
                val plain = stripTrailingComment(s).trimEnd(' ', '\t')
                Value.Scalar(plain, yamlScalarType(plain))
            }
        }
    }

    private fun parseBlockHeader(s: String): Value? {
        val folded = s[0] == '>'
        var chomp = ' '
        var digit = 0
        var i = 1
        while (i < s.length) {
            val c = s[i]
            when {
                (c == '-' || c == '+') && chomp == ' ' -> chomp = c
                c in '1'..'9' && digit == 0 -> digit = c - '0'
                else -> break
            }
            i++
        }
        // PyYAML wants whitespace before a comment here, unlike after a
        // quoted scalar or a flow collection (`|-#x` is refused)
        val rest = skipSpaces(s, i)
        if (rest < s.length && !s.isCommentStartAt(rest)) return null
        return Value.Block(folded, chomp, digit)
    }

    // --- flow collections (single line) -----------------------------------

    private fun parseFlow(s: String): Value? {
        val (node, end) = parseFlowNode(s, 0) ?: return null
        if (!isBlankOrComment(s, end)) return null
        return node
    }

    private fun parseFlowNode(s: String, start: Int): Pair<Value, Int>? {
        val i = skipSpaces(s, start)
        if (i >= s.length) return null
        return when (s[i]) {
            '[' -> parseFlowSeq(s, i + 1)
            '{' -> parseFlowMap(s, i + 1)
            '"', '\'' -> {
                val (text, end) = scanQuoted(s, i) ?: return null
                Value.Scalar(text, null) to end
            }
            else -> {
                val (text, end) = scanFlowPlain(s, i) ?: return null
                Value.Scalar(text, yamlScalarType(text)) to end
            }
        }
    }

    private fun parseFlowSeq(s: String, start: Int): Pair<Value, Int>? {
        val items = mutableListOf<Value>()
        var i = start
        while (true) {
            i = skipSpaces(s, i)
            if (i >= s.length) return null
            if (s[i] == ']') return Value.Seq(items) to i + 1
            val (node, end) = parseFlowNode(s, i) ?: return null
            items += node
            i = skipSpaces(s, end)
            if (i >= s.length) return null
            when (s[i]) {
                ',' -> i++
                ']' -> return Value.Seq(items) to i + 1
                else -> return null
            }
        }
    }

    private fun parseFlowMap(s: String, start: Int): Pair<Value, Int>? {
        val entries = mutableListOf<Pair<String, Value>>()
        var i = start
        while (true) {
            i = skipSpaces(s, i)
            if (i >= s.length) return null
            if (s[i] == '}') return Value.Map(entries) to i + 1
            val (key, afterKey) = parseFlowKey(s, i) ?: return null
            i = skipSpaces(s, afterKey)
            if (i >= s.length || s[i] != ':') return null
            i = skipSpaces(s, i + 1)
            if (i >= s.length) return null
            val value: Value
            if (s[i] == ',' || s[i] == '}') {
                value = Value.Scalar("", "null")
            } else {
                val (node, end) = parseFlowNode(s, i) ?: return null
                value = node
                i = skipSpaces(s, end)
                if (i >= s.length) return null
            }
            entries += key to value
            when (s[i]) {
                ',' -> i++
                '}' -> return Value.Map(entries) to i + 1
                else -> return null
            }
        }
    }

    private fun parseFlowKey(s: String, start: Int): Pair<String, Int>? {
        if (s[start] == '"' || s[start] == '\'') return scanQuoted(s, start)
        var i = start
        while (i < s.length) {
            val c = s[i]
            if (s.isMappingColonAt(i)) break
            if (c in FLOW_INDICATORS || s.isFlowColonAt(i) || s.isCommentStartAt(i)) return null
            i++
        }
        val key = s.substring(start, i).trim()
        if (key.isEmpty()) return null
        return key to i
    }

    // A plain scalar inside a flow collection ends at `,` / `]` / `}`; an
    // implicit `key: value` pair inside a flow sequence is outside the subset.
    private fun scanFlowPlain(s: String, start: Int): Pair<String, Int>? {
        var i = start
        while (i < s.length) {
            val c = s[i]
            if (c == ',' || c == ']' || c == '}') break
            if (s.isMappingColonAt(i) || s.isFlowColonAt(i) || s.isCommentStartAt(i)) return null
            i++
        }
        val text = s.substring(start, i).trim()
        if (text.isEmpty()) return null
        return text to i
    }

    // --- emission ---------------------------------------------------------

    private suspend fun emitMark(name: String, key: String?, type: String?) {
        val attributes = LinkedHashMap<String, String>(2)
        if (key != null) attributes["key"] = key
        if (type != null) attributes["type"] = type
        downstream.emit(SemanticEvent.Mark(name = name, isTagged = false, attributes = attributes))
    }

    private suspend fun emitUnmark(name: String) {
        downstream.emit(SemanticEvent.Unmark(name = name, isTagged = false))
    }

    private suspend fun emitText(text: String) {
        downstream.emit(SemanticEvent.Text(text))
    }

}

private const val ENTRY = "entry"
private const val ITEM = "item"

// Indicator characters that cannot start a plain mapping key in the
// supported subset (`-` is handled by the sequence check first).
private const val KEY_FORBIDDEN_START = "[]{}&*!|>%@`,"

private const val FLOW_INDICATORS = ",[]{}"

// A `:` followed by a flow indicator inside a flow collection, where the
// readers disagree: PyYAML ends the plain scalar there (`[a:, b]` holds the
// mapping `{a: null}`), Psych refuses the line and go-yaml v2 reads the
// colon as content (`"a:"`). No reading is right for all three, so the
// line is left outside the subset and kept as written.
private fun String.isFlowColonAt(i: Int): Boolean =
    this[i] == ':' && i + 1 < length && this[i + 1] in FLOW_INDICATORS

private fun leadingSpaces(s: String): Int {
    var i = 0
    while (i < s.length && s[i] == ' ') i++
    return i
}

private fun skipSpaces(s: String, from: Int): Int {
    var i = from
    while (i < s.length && (s[i] == ' ' || s[i] == '\t')) i++
    return i
}

private fun isSequenceEntry(content: String): Boolean =
    content == "-" || content.startsWith("- ") || content.startsWith("-\t")

// True when only whitespace or a `#` comment follows index [from], the end
// of a quoted scalar or a flow collection. Not [isCommentStartAt]: that is
// the rule inside a plain scalar, while after a closed token the front
// matter readers (PyYAML, Psych, go-yaml v2 — all libyaml's scanner) start a
// comment at any `#`, even with no space (`"x"#b`). DIVERGENCE: YAML 1.2
// §6.6 wants whitespace first, and a strict reader (npm `yaml`,
// snakeyaml-engine) refuses such a line; the writer never produces one.
private fun isBlankOrComment(s: String, from: Int): Boolean {
    val i = skipSpaces(s, from)
    return i >= s.length || s[i] == '#'
}

// A ` #` (hash preceded by whitespace) starts a trailing comment.
private fun stripTrailingComment(value: String): String {
    for (i in 1 until value.length) {
        if (value.isCommentStartAt(i)) return value.substring(0, i)
    }
    return value
}

// Scans a quoted scalar starting at [start]; returns the decoded content and
// the index right after the closing quote, or null when unterminated on this
// line. Double quotes decode the YAML escapes, single quotes only `''`.
internal fun scanQuoted(source: String, start: Int = 0): Pair<String, Int>? {
    val quote = source[start]
    val content = StringBuilder()
    var i = start + 1
    while (i < source.length) {
        val c = source[i]
        when {
            c == quote -> {
                if (quote == '\'' && i + 1 < source.length && source[i + 1] == '\'') {
                    content.append('\'')
                    i += 2
                } else {
                    return content.toString() to i + 1
                }
            }
            c == '\\' && quote == '"' && i + 1 < source.length -> {
                i = decodeEscape(source, i + 1, content)
            }
            else -> {
                content.append(c)
                i++
            }
        }
    }
    return null
}

// Decodes the escape whose letter sits at [i] into [out]; returns the index
// after the escape sequence. An unknown escape keeps the escaped character.
private fun decodeEscape(s: String, i: Int, out: StringBuilder): Int {
    val hexLength = when (s[i]) {
        'x' -> 2
        'u' -> 4
        'U' -> 8
        else -> 0
    }
    if (hexLength > 0) {
        val end = i + 1 + hexLength
        if (end <= s.length) {
            val code = s.substring(i + 1, end).toIntOrNull(16)
            if (code != null) {
                if (code <= 0xFFFF) out.append(code.toChar())
                else out.appendCodePointCompat(code)
                return end
            }
        }
        out.append(s[i])
        return i + 1
    }
    out.append(
        when (s[i]) {
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            '0' -> '\u0000'
            'a' -> '\u0007'
            'b' -> '\b'
            'e' -> '\u001b'
            'f' -> '\u000c'
            'v' -> '\u000b'
            'N' -> '\u0085'
            '_' -> ' '
            'L' -> ' '
            'P' -> ' '
            else -> s[i]
        }
    )
    return i + 1
}

private fun StringBuilder.appendCodePointCompat(code: Int) {
    val v = code - 0x10000
    append(((v shr 10) + 0xD800).toChar())
    append(((v and 0x3FF) + 0xDC00).toChar())
}
