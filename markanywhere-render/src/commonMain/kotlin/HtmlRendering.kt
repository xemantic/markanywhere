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

package com.xemantic.markanywhere.render

import com.xemantic.kotlin.core.text.joinToString
import com.xemantic.kotlin.core.text.unaryPlus
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.html.spec.HTML_RAW_TEXT_ELEMENTS
import com.xemantic.markanywhere.html.spec.HTML_VOID_ELEMENTS
import com.xemantic.markanywhere.html.spec.isHtmlWhitespace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * Streams the flow of [SemanticEvent]s as HTML source chunks.
 *
 * Output is pretty-printed with 2-space indentation for block elements.
 * Output flows incrementally at the granularity of upstream events: each
 * Mark / Text / Unmark produces at most one downstream [Flow] emission.
 * Within an event, all writes accumulate into a reusable per-event buffer
 * which flushes once at the end of the event.
 *
 * Inline elements are rendered on the same line as their surrounding content.
 * Content inside `<pre>` elements is not indented to preserve whitespace.
 * Content inside `<style>` and `<script>` elements is not indented or
 * HTML-escaped (HTML raw text elements). Custom namespaced elements
 * (containing `:`) are treated as block elements. All elements inside
 * `<svg>` are treated as block elements for Chrome DevTools-like
 * indentation. Text content elements (HTML `<title>`, SVG `<title>`,
 * `<desc>` and `<text>`) sit on their own line, but their content is kept
 * inline, since added whitespace would become part of the text: the content
 * of SVG `<text>` (including its `tspan`, `textPath` and `a` children) is
 * emitted verbatim, while the whitespace of a title or description,
 * insignificant to its readers, is collapsed to single spaces and trimmed.
 * Whitespace is only ever preserved deeper down: a `<pre>` nested in a
 * title keeps its content verbatim, and custom markup nested anywhere keeps
 * it raw. HTML void elements and empty SVG elements are rendered with XHTML
 * self-closing syntax (e.g. `<br/>`, `<img src="..."/>`).
 *
 * The opening tag's closing `>` is deferred by one event so an immediately
 * following matching unmark can render the element as self-closing. A
 * dangling pending mark at end of stream is resolved before completion.
 * An unbalanced stream degrades instead of failing: an unmark closes the
 * innermost open element, and one with no element open is rendered as a
 * bare closing tag.
 *
 * A single trailing `\n` (if present at end of stream) is suppressed —
 * the renderer holds at most one pending newline and drops it on completion.
 */
public fun Flow<SemanticEvent>.asHtml(): Flow<String> = flow {

    val indentAtom = "  "
    var indentation = ""
    var atLineStart = true
    val openElements = ArrayDeque<OpenElement>()

    val eventBuffer = StringBuilder()
    var hasPendingNewline = false

    // Pending mark for self-closing detection.
    // When a Mark event is processed, the closing ">" is deferred until the next
    // event arrives, so we can detect empty elements and render them as self-closing.
    var pendingMark: OpenElement? = null

    // Markup opened after a pending collapsed space is held back, since only the
    // content that follows decides where the space goes: before the held markup
    // when visible text follows, nowhere when the collapsing element closes first.
    // Only markup is ever held, never text.
    var heldMarkup: StringBuilder? = null
    // the collapsing element whose pending space the held markup follows
    var heldAfterSpaceOf: Collapsed? = null

    // where output goes: the held markup, if any, otherwise the event buffer
    var sink = eventBuffer

    fun out(s: String) { sink.append(s) }

    fun releaseHeldMarkup(withSpace: Boolean) {
        val held = heldMarkup ?: return
        if (withSpace) eventBuffer.append(' ')
        eventBuffer.append(held)
        heldAfterSpaceOf?.spacePending = false
        heldMarkup = null
        heldAfterSpaceOf = null
        sink = eventBuffer
    }

    // Writes title / description text: whitespace runs collapse to single
    // spaces, dropping leading and trailing ones
    fun writeCollapsed(text: String, collapsed: Collapsed) {
        for (c in text) {
            if (c.isHtmlWhitespace()) {
                if (collapsed.started) collapsed.spacePending = true
            } else {
                releaseHeldMarkup(withSpace = true)
                if (collapsed.spacePending) sink.append(' ')
                collapsed.spacePending = false
                collapsed.started = true
                sink.escapeHtml(c)
            }
        }
    }

    suspend fun flush() {
        hasPendingNewline = flushDeferringTrailingNewline(eventBuffer, hasPendingNewline)
    }

    fun SemanticEvent.Mark.flowAttributes() {
        attributes.forEach { (name, value) ->
            out(" "); out(name); out("=\"")
            sink.escapeAttributeValue(value)
            out("\"")
        }
    }

    fun confirmPendingMark() {
        val element = pendingMark ?: return
        pendingMark = null
        out(">")
        indentation = indentAtom.repeat(openElements.size)
        if (element.isBlock && !element.isTextContent) {
            out("\n")
            atLineStart = true
        } else {
            atLineStart = false
        }
    }

    collect { event ->

        // Check for self-closing opportunity before processing the next event
        val pending = pendingMark
        if (pending != null) {
            if (
                event is Unmark
                && event.name == pending.name
                && (pending.name in HTML_VOID_ELEMENTS || pending.insideSvg)
            ) {
                pendingMark = null
                openElements.removeLast()
                out("/>")
                if (pending.isBlock) {
                    out("\n")
                    atLineStart = true
                }
                flush()
                return@collect
            } else {
                confirmPendingMark()
            }
        }

        when (event) {

            is Text -> if (event.text != "") {
                when (val content = openElements.lastOrNull()?.content) {
                    null -> {
                        // Raw HTML at level 0 - output as-is without escaping
                        out(event.text)
                        atLineStart = event.text.endsWith('\n')
                    }
                    Raw -> {
                        // custom markup / raw text - neither escaped nor indented
                        releaseHeldMarkup(withSpace = true)
                        out(event.text)
                        atLineStart = false
                    }
                    Verbatim -> {
                        // pre / SVG text - escaped, but not indented
                        releaseHeldMarkup(withSpace = true)
                        sink.escapeHtml(event.text)
                        atLineStart = false
                    }
                    is Collapsed -> {
                        writeCollapsed(event.text, content)
                        atLineStart = false
                    }
                    Indented -> {
                        // Re-indent after each newline. Escape line-by-line
                        // straight into the sink — escaping doesn't insert
                        // or remove `\n`, so split-then-escape is equivalent
                        // to escape-then-split.
                        val lines = event.text.split('\n')
                        lines.forEachIndexed { index, line ->
                            if (index > 0) {
                                out("\n")
                                atLineStart = true
                            }
                            if (line.isNotEmpty()) {
                                if (atLineStart) {
                                    out(indentation)
                                }
                                sink.escapeHtml(line)
                                atLineStart = false
                            }
                        }
                    }
                }
            }

            is Mark -> {
                val parent = openElements.lastOrNull() ?: DOCUMENT
                val parentContent = parent.content
                if (heldMarkup == null && parentContent is Collapsed && parentContent.spacePending) {
                    val held = StringBuilder()
                    heldMarkup = held
                    heldAfterSpaceOf = parentContent
                    sink = held
                }
                val element = event.openIn(parent)
                openElements.addLast(element)
                if (element.isBlock) {
                    if (atLineStart) {
                        out(indentation)
                    } else {
                        out("\n")
                        out(indentation)
                    }
                } else {
                    if (atLineStart && !parent.inlineLayout) {
                        out(indentation)
                    }
                    atLineStart = false
                }
                out("<"); out(event.name); event.flowAttributes()
                // Defer closing ">" for potential self-close detection
                pendingMark = element
            }

            is Unmark -> {
                // a stray unmark, with nothing open, closes as if at top level
                val element = openElements.removeLastOrNull() ?: event.openIn(DOCUMENT)
                val parent = openElements.lastOrNull() ?: DOCUMENT
                if (element.content === heldAfterSpaceOf && parent.content !== element.content) {
                    // the collapsing element itself (not one nested in it, sharing
                    // its state) closes, so its trailing space is dropped
                    releaseHeldMarkup(withSpace = false)
                }
                indentation = indentAtom.repeat(openElements.size)
                // a text content element closes right after its content
                if (element.isBlock && !element.isTextContent) {
                    if (atLineStart) {
                        out(indentation)
                    } else {
                        out("\n")
                        out(indentation)
                    }
                } else if (!element.isBlock && atLineStart && !parent.inlineLayout) {
                    out(indentation)
                }
                out("</"); out(event.name); out(">")
                if (element.isBlock) {
                    out("\n")
                    atLineStart = true
                } else {
                    atLineStart = false
                }
            }

        }

        flush()
    }

    // Resolve any remaining pending mark (last childless element or malformed
    // unclosed element). Runs only on normal completion — on cancellation
    // the collect lambda throws and we skip this; the consumer is gone anyway.
    confirmPendingMark()
    releaseHeldMarkup(withSpace = false)
    flush()
}

// An element open in the rendered output, with everything its descendants
// need to know about how to lay out and write their content.
private class OpenElement(
    val name: String,
    // whether the element itself sits inside an <svg>
    val insideSvg: Boolean,
    // whether the element is laid out as a block: its tags on their own lines
    val isBlock: Boolean,
    // whether the element is a title / description / SVG text: its content
    // sits between its tags with nothing added
    val isTextContent: Boolean,
    // how text inside the element is written
    val content: Content,
    // whether elements inside are laid out inline, never adding whitespace
    val inlineLayout: Boolean,
    // whether elements inside sit inside an <svg>
    val svg: Boolean,
)

// The implicit parent of top-level elements
private val DOCUMENT = OpenElement(
    name = "",
    insideSvg = false,
    isBlock = false,
    isTextContent = false,
    content = Indented,
    inlineLayout = false,
    svg = false,
)

// How text inside an element is written
private sealed interface Content

// Escaped, and re-indented after each newline
private data object Indented : Content

// Escaped, but otherwise as-is
private data object Verbatim : Content

// As-is, neither escaped nor indented
private data object Raw : Content

// Escaped, with whitespace collapsed to single spaces and trimmed. One instance
// per collapsing element, shared by the elements nested in it, so that a space
// between their text is kept, while a nested collapsing element starts afresh
// and leaves the state of its parent intact.
private class Collapsed : Content {
    // whether any content was written, so that a leading space is dropped
    var started = false
    // a space is only written before the next content, so that a trailing one
    // is dropped
    var spacePending = false
}

private fun SemanticEvent.Marked.openIn(parent: OpenElement): OpenElement {
    val insideSvg = parent.svg
    val textContent = textContentOf(name, insideSvg)
    return OpenElement(
        name = name,
        insideSvg = insideSvg,
        isBlock = (isBlock || insideSvg) && !parent.inlineLayout,
        isTextContent = textContent != null,
        // whitespace preserved by an ancestor is never collapsed again
        content = when {
            parent.content == Raw || ':' in name || name in HTML_RAW_TEXT_ELEMENTS -> Raw
            parent.content == Verbatim || name == "pre" -> Verbatim
            else -> textContent ?: parent.content
        },
        inlineLayout = parent.inlineLayout || name == "pre" || textContent != null,
        svg = insideSvg || name == "svg",
    )
}

/**
 * Collects [asHtml] into a single HTML string.
 */
public suspend fun Flow<SemanticEvent>.renderHtml(): String = asHtml().joinToString()

// Block elements that expand with newlines and indentation
private val BLOCK_ELEMENTS = setOf(
    // HTML document structure
    "html", "head", "body",
    // HTML metadata
    "base", "link", "meta", "title", "style", "script", "noscript",
    // HTML sectioning
    "div", "section", "article", "header", "footer", "nav", "aside", "main",
    // HTML headings
    "h1", "h2", "h3", "h4", "h5", "h6", "hgroup",
    // HTML text blocks
    "p", "pre", "blockquote", "address",
    // HTML lists
    "ul", "ol", "li", "dl", "dt", "dd", "menu",
    // HTML tables
    "table", "caption", "colgroup", "thead", "tbody", "tfoot", "tr", "th", "td",
    // HTML forms
    "fieldset", "legend", "form",
    // HTML interactive
    "details", "summary", "dialog",
    // HTML figures
    "figure", "figcaption",
    // HTML embedded content
    "object",
    // HTML template
    "template",
    // HTML other block elements
    "hr", "search",
    // HTML custom/non-standard used in markdown
    "footnote",
    // the parser's structured front matter and its entries
    "frontmatter", "entry", "item",
    // SVG root element (all children are treated as block via svg context tracking,
    // except inside a text content element)
    "svg",
)

// Text content elements: the opening tag starts its own line and the closing
// tag ends it, with nothing added between, since whitespace there would become
// part of the text. Returns how their text is written — collapsed for a title /
// description, whose whitespace is insignificant to its readers — or null when
// the element is not a text content element at all.
private fun textContentOf(name: String, insideSvg: Boolean): Content? = when (name) {
    "title" -> Collapsed()
    "desc" -> if (insideSvg) Collapsed() else null
    "text" -> if (insideSvg) Verbatim else null
    else -> null
}

private fun Appendable.escapeHtml(value: String) {
    for (c in value) escapeHtml(c)
}

private fun Appendable.escapeHtml(c: Char) {
    when (c) {
        '<' -> +"&lt;"
        '>' -> +"&gt;"
        '&' -> +"&amp;"
        else -> +c
    }
}

private fun Appendable.escapeAttributeValue(value: String) {
    for (c in value) when (c) {
        '<' -> +"&lt;"
        '>' -> +"&gt;"
        '&' -> +"&amp;"
        '"' -> +"&quot;"
        else -> +c
    }
}

private val SemanticEvent.Marked.isBlock get() = name in BLOCK_ELEMENTS || name.contains(":")

// Flushes a per-event [buffer] downstream, deferring a single trailing `\n`
// to the next flush so the stream never emits a final newline (matches the
// original `trimLastNewLine` behaviour). Shared by the Markdown and HTML
// renderers; returns the new pending-newline state for the caller to thread
// back in on the next flush.
internal suspend fun FlowCollector<String>.flushDeferringTrailingNewline(
    buffer: StringBuilder,
    hadPendingNewline: Boolean
): Boolean {
    if (buffer.isEmpty()) return hadPendingNewline
    val s = buffer.toString()
    buffer.clear()
    val combined = if (hadPendingNewline) "\n$s" else s
    if (combined.endsWith("\n")) {
        val rest = combined.substring(0, combined.length - 1)
        if (rest.isNotEmpty()) emit(rest)
        return true
    }
    emit(combined)
    return false
}