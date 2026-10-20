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
 * - An entry / item whose value is outside the subset carries `type` =
 *   `verbatim`: its value is unknown, only the front matter readers know it,
 *   and its text is the value's YAML source — the rest of the key or dash
 *   line, then every further line of the value, each ending in `\n`, with
 *   its indentation up to the key's or dash's column removed (so it re-indents
 *   along with its key). Further lines are the ones more indented than that
 *   column, and the blank and comment lines among them — or, while a quoted
 *   scalar or flow collection the value opens is left open, any line, as
 *   the readers continue one whatever its indentation (to the end of the
 *   document, if it never closes).
 *
 * Supported: block mappings and sequences (a sequence may sit at its key's
 * own indentation), nesting, plain / double-quoted / single-quoted scalars,
 * block scalars (`|`, `>`, chomping `-`/`+`, indentation indicator),
 * single-line flow sequences and mappings, comments (dropped), blank lines,
 * duplicate keys (all kept, in order).
 *
 * DIVERGENCE (verbatim fallback): a value the subset does not understand —
 * multi-line flow collections or quoted scalars, a plain scalar continued on
 * further lines, a line under a bare `key:` that is neither an entry nor an
 * item, a line the readers refuse after a single-line value or a block
 * scalar, or a shape the front matter readers read differently from one
 * another (a flow scalar's `:` before `,` / `[` / `]` / `{` / `}`, a block
 * scalar header followed by `#` with no space) — is kept as source in a
 * `type=verbatim` entry / item, as above: whether a value is known is a fact
 * of this parse, so a consumer never has to guess it from the events around
 * an entry. Any other line the subset does not understand — complex keys,
 * directives, a `- item` inside a mapping, a line with no key — is emitted
 * **verbatim** (with its `\n`) as a text child of the container it sits in,
 * its indentation up to the column of the container's entries removed, so
 * the writer re-indents it along with them.
 * Either way nothing is lost and [YamlWriter] writes it back as-is.
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
 * Streaming: a `key: value` line is held until the next line that is not
 * blank or a comment, which decides whether the value continues (and is
 * verbatim). A bare `key:` (or `-`) is held for one line to decide between a
 * nested block, a verbatim value and a null value. A block scalar is
 * buffered until it closes — never past the enclosing construct; one a
 * comment ends is held like a `key: value` line. The lines outside the
 * subset ahead of the first entry or item of an indented root are held until
 * it arrives (they are then written relative to the root) or cannot arrive
 * any more (they keep their own indentation). A verbatim
 * value streams line by line. Never throws.
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
        val key: String?,
        // the source after the `:` / `-`, kept should the value be verbatim
        val source: String
    )

    // An entry / item whose single-line value is complete, held for one line:
    // a more indented line continues it (a plain scalar's continuation, or a
    // line the readers refuse), which makes its value verbatim. The blank and
    // comment lines in between are kept, as they are part of such a value.
    private class Held(
        val owner: String,
        val key: String?,
        val indent: Int,
        val value: Value,
        val source: String
    ) {
        val skipped = mutableListOf<String>()
    }

    private class BlockScalar(
        val owner: String,
        val key: String?,
        // the source after the `:` / `-`, kept should the value be verbatim
        val source: String,
        val folded: Boolean,
        val chomp: Char,
        // absolute content indentation from an explicit indicator, else -1
        val explicitIndent: Int,
        val parentIndent: Int
    ) {
        var contentIndent = -1
        val lines = mutableListOf<String>()
        // the same lines as source, should the value turn out verbatim
        val sourceLines = mutableListOf<String>()
        // whether a comment less indented than the content ended it: it is
        // then held, like a single-line value, until the next line that is
        // not blank or a comment decides whether it is continued
        var ended = false
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
    private var held: Held? = null
    private var block: BlockScalar? = null
    // the column of the open verbatim entry / item, -1 when none is open
    private var verbatimIndent = -1
    private var verbatimOwner = ENTRY
    // the quoted scalar or flow collection the open verbatim value leaves
    // open, which continues on any line, whatever its indentation, until it
    // closes; null when none is open
    private var openValue: OpenValueScan? = null
    // whether an entry / item was emitted at the root
    private var rootHasNode = false
    // the lines outside the subset ahead of the first root entry / item of
    // an indented root, held until it is known how they are written back:
    // relative to the root once an entry or item follows (the writer writes
    // it at column 0, so must they be), with their own indentation if none
    // does (which is then what keeps the root indented)
    private val leadingLines = mutableListOf<String>()
    // whether a line less indented than the root was read
    private var rootBroken = false

    /** Feeds one source line, without its line terminator. */
    public suspend fun line(line: String) {
        block?.let { if (feedBlock(it, line)) return }
        val indent = leadingSpaces(line)
        val skipped = indent == line.length || line[indent] == '#' // blank / comment
        if (verbatimIndent >= 0) {
            openValue?.let {
                emitText(sourceLine(line, verbatimIndent))
                if (!it.feed(line)) openValue = null
                return
            }
            if (skipped || indent > verbatimIndent) {
                emitText(sourceLine(line, verbatimIndent))
                return
            }
            closeVerbatim()
        }
        held?.let { h ->
            if (skipped) {
                h.skipped += line
                return
            }
            held = null
            if (indent > h.indent) {
                openVerbatim(h.owner, h.key, h.indent, h.source)
                for (s in h.skipped) emitText(sourceLine(s, h.indent))
                emitText(sourceLine(line, h.indent))
                return
            }
            emitValue(h.owner, h.key, h.indent, h.value)
        }
        if (skipped) return
        processContent(line, indent, line.substring(indent))
    }

    /** Closes every open construct; the parser is then ready for a new document. */
    public suspend fun finish() {
        block?.let { closeBlock(it) }
        if (verbatimIndent >= 0) closeVerbatim()
        held?.let { emitValue(it.owner, it.key, it.indent, it.value) }
        held = null
        pending?.let { resolvePendingAsNull(it) }
        pending = null
        flushLeadingLines(indent = 0)
        while (frames.size > 1) closeTopFrame()
        frames.clear()
        rootHasNode = false
        rootBroken = false
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
                // a deeper line that is neither an entry nor an item is the
                // value itself, in a shape the subset does not parse (plain
                // lines the readers join, a flow collection they disagree on)
                if (indent > p.indent && !seqLine && parseKey(content) == null) {
                    openVerbatim(owner, p.key, p.indent, p.source, value = null)
                    emitText(sourceLine(line, p.indent))
                    scanOpenValue(content)
                    return
                }
                emitMark(owner, p.key, type = null)
                frames.addLast(Frame(indent, seqLine, owner))
            } else {
                resolvePendingAsNull(p)
            }
        }
        while (frames.size > 1 && frames.last().indent > indent) closeTopFrame()
        // DIVERGENCE: a line less indented than the root — PyYAML refuses
        // it, Psych and go-yaml v2 read the document up to it — and every
        // line after it is text at the root, with its own indentation. The
        // writer writes the root at column 0, so such a document loses the
        // fixpoint, but no entry is read that the readers do not see.
        if (frames.last().indent > indent && !rootBroken) {
            rootBroken = true
            flushLeadingLines(indent = 0)
        }
        if (rootBroken) return verbatim(line)
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
        val value = parseValue(rest) ?: return openVerbatim(ENTRY, key, indent, rest)
        lineValue(ENTRY, key, indent, value, rest)
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
                pending = Pending(indent, isItem = true, key = null, source = after)
            isSequenceEntry(inner) -> {
                emitMark(ITEM, null, null)
                frames.addLast(Frame(col, isSequence = true, owner = ITEM))
                item(line, col, inner)
            }
            else -> {
                val keyed = parseKey(inner)
                if (keyed != null) {
                    // the item holds a mapping either way: a compact entry
                    // outside the subset is an entry of it, as one on the
                    // next line would be
                    val value = parseValue(keyed.second)
                    emitMark(ITEM, null, null)
                    frames.addLast(Frame(col, isSequence = false, owner = ITEM))
                    if (value == null) return openVerbatim(ENTRY, keyed.first, col, keyed.second)
                    lineValue(ENTRY, keyed.first, col, value, keyed.second)
                } else {
                    val value = parseValue(inner) ?: return openVerbatim(ITEM, null, indent, after)
                    lineValue(ITEM, null, indent, value, after)
                }
            }
        }
    }

    // The value of an entry / item line, [source] being the line after its
    // `:` / `-`: a bare key decides on the next line, a block scalar is
    // buffered until it closes, and a complete value is held for one line.
    private fun lineValue(owner: String, key: String?, indent: Int, value: Value, source: String) {
        when (value) {
            Value.Pending -> pending = Pending(indent, owner == ITEM, key, source)
            is Block -> block = BlockScalar(
                owner = owner,
                key = key,
                source = source,
                folded = value.folded,
                chomp = value.chomp,
                explicitIndent = if (value.indentDigit > 0) indent + value.indentDigit else -1,
                parentIndent = indent
            )
            else -> held = Held(owner, key, indent, value, source)
        }
    }

    // Emits a complete value: a scalar or a single-line flow collection.
    private suspend fun emitValue(owner: String, key: String?, indent: Int, value: Value) {
        when (value) {
            // never a complete value, see lineValue
            Value.Pending, is Block -> {}
            is Scalar -> {
                emitMark(owner, key, value.type)
                if (value.text.isNotEmpty()) emitText(value.text)
                emitUnmark(owner)
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

    // A line outside the subset that is no entry or item: a text child of the
    // top container, its indentation up to the container's entries removed
    // (a line indented less keeps its own), so the writer can re-indent it
    // along with the container. At the root, a line after the root was
    // broken keeps its own indentation, and one ahead of the first entry or
    // item of an indented root is held until that entry or item, or its
    // absence, decides ([leadingLines]).
    private suspend fun verbatim(line: String) {
        val top = frames.last()
        when {
            frames.size > 1 -> emitText(sourceLine(line, top.indent))
            rootBroken -> emitText(sourceLine(line, 0))
            !rootHasNode && top.indent > 0 -> leadingLines += line
            else -> emitText(sourceLine(line, top.indent))
        }
    }

    // Emits the held [leadingLines], their indentation up to [indent] removed.
    private suspend fun flushLeadingLines(indent: Int) {
        for (line in leadingLines) emitText(sourceLine(line, indent))
        leadingLines.clear()
    }

    // Opens an entry / item at column [indent] whose value is outside the
    // subset: `type=verbatim`, its text the value's source — [source], the
    // rest of its own line, then every line the value spans, each with its
    // `\n` and its indentation up to [indent] removed. Each line more indented
    // than the key or dash, blank or a comment continues it — and, while a
    // quoted scalar or flow collection opening [value] is left open, any line.
    private suspend fun openVerbatim(
        owner: String,
        key: String?,
        indent: Int,
        source: String,
        value: String? = source
    ) {
        emitMark(owner, key, VERBATIM)
        emitText(source + "\n")
        verbatimIndent = indent
        verbatimOwner = owner
        openValue = null
        if (value != null) scanOpenValue(value)
    }

    // Starts tracking the quoted scalar or flow collection [value] opens, if
    // it opens one and leaves it open.
    private fun scanOpenValue(value: String) {
        val start = skipSpaces(value, 0)
        if (start == value.length || value[start] !in OPEN_VALUE_STARTS) return
        val scan = OpenValueScan()
        if (scan.feed(value.substring(start))) openValue = scan
    }

    private suspend fun closeVerbatim() {
        verbatimIndent = -1
        openValue = null
        emitUnmark(verbatimOwner)
    }

    // --- block scalars ----------------------------------------------------

    // Returns true when the line was consumed as block scalar content; false
    // when it ends the scalar (and must be processed as a regular line).
    private suspend fun feedBlock(b: BlockScalar, line: String): Boolean {
        val indent = leadingSpaces(line)
        val blank = indent == line.length
        if (b.ended) {
            if (blank || line[indent] == '#') {
                b.sourceLines += sourceLine(line, b.parentIndent)
                return true
            }
            return endBlock(b, line, indent)
        }
        if (b.contentIndent < 0) {
            if (blank) {
                b.lines += ""
                b.sourceLines += sourceLine(line, b.parentIndent)
                return true
            }
            val contentIndent = if (b.explicitIndent >= 0) b.explicitIndent else indent
            if (indent <= b.parentIndent || indent < contentIndent) {
                return endBlock(b, line, indent)
            }
            b.contentIndent = contentIndent
            b.lines += line.substring(contentIndent)
            b.sourceLines += sourceLine(line, b.parentIndent)
            return true
        }
        if (!blank && indent < b.contentIndent) return endBlock(b, line, indent)
        b.sourceLines += sourceLine(line, b.parentIndent)
        b.lines += when {
            !blank -> line.substring(b.contentIndent)
            line.length > b.contentIndent -> line.substring(b.contentIndent)
            else -> ""
        }
        return true
    }

    // Ends the block scalar at a line indented less than its content; true
    // when that line is consumed. A line still more indented than the key or
    // dash is one the readers refuse, so the value becomes verbatim, that
    // line included. A comment ends the scalar for the readers, yet a line
    // after it more indented than the key or dash is refused all the same:
    // the scalar is held until the next line that is not blank or a comment
    // (its lines kept as source meanwhile) — read as ended, it would leave
    // that line to the root, which renders as a continuation of the scalar
    // the readers accept.
    private suspend fun endBlock(b: BlockScalar, line: String, indent: Int): Boolean {
        if (line[indent] == '#') {
            b.ended = true
            b.sourceLines += sourceLine(line, b.parentIndent)
            return true
        }
        if (indent > b.parentIndent) {
            block = null
            openVerbatim(b.owner, b.key, b.parentIndent, b.source)
            for (s in b.sourceLines) emitText(s)
            emitText(sourceLine(line, b.parentIndent))
            return true
        }
        closeBlock(b)
        return false
    }

    private suspend fun closeBlock(b: BlockScalar) {
        block = null
        emitMark(b.owner, b.key, null)
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
        if (frames.size == 1 && !rootHasNode) {
            rootHasNode = true
            flushLeadingLines(indent = frames.last().indent)
        }
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

// The `type` of an entry / item whose text is its value's YAML source.
private const val VERBATIM = YAML_VERBATIM_TYPE

// What opens a quoted scalar or a flow collection.
private const val OPEN_VALUE_STARTS = "\"'[{"

// Tracks, line by line, a quoted scalar or flow collection (and the quoted
// scalars nested in it) until it closes, the way the front matter readers
// read one that spans lines: whatever the indentation of its lines. Starts
// at the opening quote or bracket.
private class OpenValueScan {

    private var quote = NO_QUOTE
    private var depth = 0
    // whether a quote here would open a quoted scalar: at the start of a
    // node, after `[` / `{` / `,` / `:`, not inside a plain scalar
    private var atNodeStart = true

    // Feeds the next stretch of source; true while the value is still open.
    fun feed(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quote != NO_QUOTE) {
                when {
                    quote == '"' && c == '\\' -> i++
                    c == '\'' && quote == '\'' && i + 1 < text.length && text[i + 1] == '\'' -> i++
                    c == quote -> {
                        quote = NO_QUOTE
                        if (depth == 0) return false
                        atNodeStart = false
                    }
                }
                i++
                continue
            }
            when {
                c == '"' || c == '\'' -> if (atNodeStart) quote = c
                c == '[' || c == '{' -> if (atNodeStart) depth++
                c == ']' || c == '}' -> {
                    if (depth > 0) depth--
                    if (depth == 0) return false
                    atNodeStart = false
                }
                c == ',' -> atNodeStart = true
                // a colon starts a node only as a mapping indicator: before
                // whitespace, the end of the line or a flow indicator
                // (`x:"y` is one plain scalar)
                c == ':' -> atNodeStart =
                    i + 1 == text.length || text[i + 1] == ' ' || text[i + 1] == '\t' ||
                        text[i + 1] in FLOW_INDICATORS
                c == '#' && (i == 0 || text[i - 1] == ' ' || text[i - 1] == '\t') -> break
                c == ' ' || c == '\t' -> {}
                else -> atNodeStart = false
            }
            i++
        }
        // a line break ends a plain scalar inside a flow collection
        if (depth > 0) atNodeStart = true
        return quote != NO_QUOTE || depth > 0
    }

}

private const val NO_QUOTE = '\u0000'

// A line of a verbatim value: its indentation up to the column of the key
// or dash removed (a blank or comment line may have less), then `\n` — so
// the writer can re-indent the value along with its key.
private fun sourceLine(line: String, indent: Int): String =
    line.substring(minOf(indent, leadingSpaces(line))) + "\n"

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
