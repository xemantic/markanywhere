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

import com.xemantic.kotlin.core.text.unaryPlus
import com.xemantic.markanywhere.SemanticEvent

/**
 * Writes the structured YAML events (`entry` / `item` marks — see
 * [YamlParser] for the vocabulary) back as block-style YAML, one line per
 * scalar, two spaces per nesting level.
 *
 * Events are pushed one at a time via [collect] (the [asYaml] flow operator
 * does this for you; the Markdown renderer in `markanywhere-render` feeds
 * the children of a `frontmatter` mark and reads [depth] to see its close
 * coming). Any mark other than `entry` / `item` is transparent: its own text
 * is written verbatim and its children lay out as if it were not there.
 * An `unmark` with nothing open is ignored.
 *
 * The output is what [YamlParser] reads back to the same events (a fixpoint):
 * - a scalar with a `type` (`bool`, `int`, `float`, `null`, `timestamp`) is
 *   written bare; a string is written plain unless a plain scalar would
 *   re-parse as something else — reserved literals, numbers, timestamps,
 *   YAML 1.1 sexagesimals (`12:30`), a leading indicator, `: ` or a
 *   trailing `:`, ` #`, surrounding whitespace, a control character — in
 *   which case it is double-quoted with the YAML escapes;
 * - a multi-line string becomes a literal block scalar (`|`, `|-`, `|+`
 *   according to its trailing newlines) unless its first line starts with
 *   whitespace or it has no content, which fall back to a quoted scalar;
 * - no text and no type is an empty string (`""`); `type=null` with no
 *   text is a bare `key:`; `type=seq` / `type=map` with no children are
 *   `[]` / `{}`;
 * - a key is written plain only when identifier-shaped (letters, digits,
 *   `_`, `-`, `.`, starting with a letter or `_`) and not a reserved
 *   literal — a YAML 1.1 reader takes a bare `yes:` as a boolean key — and
 *   double-quoted otherwise, even where YAML would not require it;
 * - a `text` child of a container (the parser's verbatim fallback for a
 *   line outside its YAML subset) is written as-is, on its own line(s).
 *
 * Event-incremental with one bounded buffer: a scalar's text is held until
 * its `unmark`, since the quoting decision needs the whole value.
 */
public class YamlWriter(
    private val out: (String) -> Unit
) {

    // ENTRY and ITEM are the YAML nodes; OTHER is the implicit root and any
    // foreign mark — transparent, its text written verbatim.
    private enum class Kind { ENTRY, ITEM, OTHER }

    private enum class State { UNDECIDED, SCALAR, CONTAINER }

    private class Node(
        val kind: Kind,
        val key: String?,
        val type: String?,
        // the column this node's own line starts at
        val indent: Int,
        // the column this node's children start at
        val childIndent: Int
    ) {
        var state: State = UNDECIDED
        val scalar = StringBuilder()
        val isValue: Boolean get() = kind != OTHER
    }

    private val stack = ArrayDeque<Node>().apply {
        addLast(Node(OTHER, key = null, type = null, indent = 0, childIndent = 0))
    }

    // True right after `-` was written for an item whose first child
    // continues on the same line (`- key: value`, `- - nested`); the child's
    // line prefix is then the single space after the dash.
    private var afterDash = false

    private var atLineStart = true

    /** The number of marks currently open. */
    public val depth: Int get() = stack.size - 1

    public fun collect(event: SemanticEvent) {
        when (event) {
            is Mark -> mark(event)
            is Text -> text(event.text)
            is Unmark -> unmark()
        }
    }

    private fun mark(event: SemanticEvent.Mark) {
        val parent = stack.last()
        openContainer(parent)
        val kind: Kind = when (event.name) {
            "entry" -> ENTRY
            "item" -> ITEM
            else -> OTHER
        }
        val indent = parent.childIndent
        val childIndent = if (kind == OTHER) indent else indent + 2
        stack.addLast(Node(kind, event.attributes["key"], event.attributes["type"], indent, childIndent))
    }

    private fun text(text: String) {
        val node = stack.last()
        if (node.isValue && node.state != CONTAINER) {
            node.state = SCALAR
            node.scalar.append(text)
        } else {
            writeVerbatim(text)
        }
    }

    private fun unmark() {
        if (depth == 0) return
        val node = stack.removeLast()
        if (!node.isValue) return
        when (node.state) {
            // an item whose children wrote nothing (only foreign marks) still
            // has its dash on an open line — end it, or whatever the caller
            // writes next (the Markdown renderer's closing fence) joins it
            CONTAINER -> if (!atLineStart) {
                out("\n")
                afterDash = false
                atLineStart = true
            }
            UNDECIDED -> writeLine(node, emptyValue(node.type))
            SCALAR -> writeLine(node, formatScalar(node.scalar.toString(), node.type, node.childIndent))
        }
    }

    // Ensures the node has emitted its own line prefix before its first child.
    private fun openContainer(node: Node) {
        if (!node.isValue || node.state == CONTAINER) return
        // text seen before a nested mark can only be verbatim lines
        val pendingVerbatim = if (node.state == SCALAR) node.scalar.toString() else null
        node.state = CONTAINER
        if (node.kind == ITEM) {
            out(linePrefix(node) + "-")
            atLineStart = false
            afterDash = true
        } else {
            out(linePrefix(node) + renderKey(node.key) + ":\n")
            atLineStart = true
        }
        if (pendingVerbatim != null) writeVerbatim(pendingVerbatim)
    }

    private fun writeLine(node: Node, value: String) {
        val head = if (node.kind == ITEM) "-" else renderKey(node.key) + ":"
        out(linePrefix(node) + head + (if (value.isEmpty()) "" else " $value") + "\n")
        atLineStart = true
    }

    private fun linePrefix(node: Node): String =
        if (afterDash) {
            afterDash = false
            " "
        } else {
            " ".repeat(node.indent)
        }

    private fun writeVerbatim(text: String) {
        if (text.isEmpty()) return
        if (!atLineStart) out("\n")
        afterDash = false
        out(text)
        if (!text.endsWith("\n")) out("\n")
        atLineStart = true
    }

    private fun emptyValue(type: String?): String = when (type) {
        null -> "\"\""
        "seq" -> "[]"
        "map" -> "{}"
        else -> ""
    }

    // [contentIndent] is the column block scalar lines are written at.
    private fun formatScalar(text: String, type: String?, contentIndent: Int): String {
        if (type != null && '\n' !in text && !text.startsWith(' ') && !text.endsWith(' ')) {
            return text
        }
        if (text.isEmpty()) return "\"\""
        if ('\n' in text && canBlock(text)) return blockScalar(text, contentIndent)
        return if (needsQuoting(text)) quoted(text) else text
    }

    private fun canBlock(text: String): Boolean {
        val first = text[0]
        if (first == ' ' || first == '\t' || first == '\n') return false
        for (c in text) if (c != '\n' && c != '\t' && c < ' ') return false
        return text.trimEnd('\n').isNotEmpty()
    }

    private fun blockScalar(text: String, contentIndent: Int): String {
        val content = text.trimEnd('\n')
        val trailingNewlines = text.length - content.length
        val header = when (trailingNewlines) {
            0 -> "|-"
            1 -> "|"
            else -> "|+"
        }
        val prefix = " ".repeat(contentIndent)
        val sb = StringBuilder(header)
        for (line in content.split('\n')) {
            sb.append('\n')
            if (line.isNotEmpty()) sb.append(prefix).append(line)
        }
        // `|+` keeps every trailing line break: one ends the last content
        // line, the rest are empty lines
        repeat(trailingNewlines - 1) { sb.append('\n') }
        return sb.toString()
    }

    // A key is written plain only when it is identifier-shaped and not a
    // reserved literal: the Markdown parser's front matter detection requires
    // the first line to pass `isYamlKeyLine` (such a key, or a quoted one),
    // so quoting everything else keeps whatever entry comes first
    // re-detectable. The character rules are shared with that check.
    private fun renderKey(key: String?): String {
        val k = key ?: ""
        return if (isIdentifierKey(k) && yamlScalarType(k) == null) k else quoted(k)
    }
}

// Plain-scalar indicator characters: starting with any of these forces
// double-quoted output (see YAML 1.2 §6.4 / §6.6).
private const val YAML_INDICATORS = "-?:,[]{}#&*!|>'\"%@`"

// A character a plain scalar cannot carry: C0 / DEL / NEL and the Unicode
// line and paragraph separators (YAML 1.2 §5.1 printable characters).
private val Char.isYamlControl: Boolean
    get() = this < ' ' || this == '\u007f' || this == '\u0085' || this == '\u2028' || this == '\u2029'

// A YAML 1.1 base-60 number (`12:30`, `-1:30`, `190:20:30.15`): YAML 1.2 —
// and so `yamlScalarType` — reads it as a string, but Psych (Jekyll) and
// PyYAML type it as an int / float, so the writer keeps it quoted, in the
// same spirit as the YAML 1.1 booleans (`yes`, `on`). Anchored, see the
// note on the patterns in `YamlParser.kt`.
private val YAML_1_1_SEXAGESIMAL = Regex("""^[-+]?[0-9][0-9_]*(:[0-5]?[0-9])+(\.[0-9_]*)?$""")

// A plain scalar the parser would not read back as the same string: one it
// would type (`yamlScalarType`), or whose shape is an indicator, a comment
// (`#` after a space), a mapping colon (`:` before a space or at the end),
// surrounding whitespace or a control character. A `:` or `#` anywhere else
// is plain content (`https://x/y`, `C#`), as is an inner `"` or `\`.
private fun needsQuoting(s: String): Boolean {
    if (s.isEmpty()) return true
    if (s.first().isWhitespace() || s.last().isWhitespace()) return true
    if (s[0] in YAML_INDICATORS) return true
    for (i in s.indices) {
        val c = s[i]
        if (c.isYamlControl) return true
        if (c == ':' && (i + 1 == s.length || s[i + 1] == ' ')) return true
        if (c == '#' && s[i - 1] == ' ') return true
    }
    if (YAML_1_1_SEXAGESIMAL.matches(s)) return true
    return yamlScalarType(s) != null
}

private fun quoted(s: String): String = buildString {
    +'"'
    for (c in s) when {
        c == '\\' -> +"\\\\"
        c == '"' -> +"\\\""
        c == '\n' -> +"\\n"
        c == '\r' -> +"\\r"
        c == '\t' -> +"\\t"
        c.isYamlControl -> +("\\u" + c.code.toString(16).padStart(4, '0'))
        else -> +c
    }
    +'"'
}
