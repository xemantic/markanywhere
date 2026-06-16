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
 * indentation, except for inline text elements (`tspan`, `textPath`, `a`)
 * which remain inline to preserve text content. HTML void elements and
 * empty SVG elements are rendered with XHTML self-closing syntax
 * (e.g. `<br/>`, `<img src="..."/>`).
 *
 * The opening tag's closing `>` is deferred by one event so an immediately
 * following matching unmark can render the element as self-closing. A
 * dangling pending mark at end of stream is resolved before completion.
 *
 * A single trailing `\n` (if present at end of stream) is suppressed —
 * the renderer holds at most one pending newline and drops it on completion.
 */
public fun Flow<SemanticEvent>.asHtml(): Flow<String> = flow {

    var level = 0
    val indentAtom = "  "
    var indentation = ""
    var atLineStart = true
    var preCount = 0
    var svgCount = 0
    var customMarkupCount = 0
    var rawTextCount = 0

    val eventBuffer = StringBuilder()
    var hasPendingNewline = false

    // Pending mark state for self-closing detection.
    // When a Mark event is processed, the closing ">" is deferred until the next
    // event arrives, so we can detect empty elements and render them as self-closing.
    var pendingMarkName: String? = null
    var pendingMarkIsBlock = false
    var pendingMarkInsideSvg = false

    fun out(s: String) { eventBuffer.append(s) }

    suspend fun flush() {
        hasPendingNewline = flushDeferringTrailingNewline(eventBuffer, hasPendingNewline)
    }

    fun SemanticEvent.Mark.flowAttributes() {
        attributes.forEach { (name, value) ->
            out(" "); out(name); out("=\"")
            eventBuffer.escapeAttributeValue(value)
            out("\"")
        }
    }

    fun confirmPendingMark() {
        if (pendingMarkName == null) return
        pendingMarkName = null
        out(">")
        level++
        indentation = indentAtom.repeat(level)
        if (pendingMarkIsBlock) {
            out("\n")
            atLineStart = true
        }
    }

    fun SemanticEvent.Marked.isBlockMark(insideSvg: Boolean, insidePre: Boolean): Boolean =
        (isBlock || (insideSvg && name !in SVG_INLINE_ELEMENTS)) && !insidePre

    collect { event ->

        // Check for self-closing opportunity before processing the next event
        val pmName = pendingMarkName
        if (pmName != null) {
            if (
                event is Unmark
                && event.name == pmName
                && (pmName in VOID_ELEMENTS || pendingMarkInsideSvg)
            ) {
                pendingMarkName = null
                // Undo counter increments from Mark processing
                if (pmName == "pre") preCount--
                if (pmName == "svg") svgCount--
                if (':' in pmName) customMarkupCount--
                if (pmName in RAW_TEXT_ELEMENTS) rawTextCount--
                out("/>")
                if (pendingMarkIsBlock) {
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

            is Text -> {
                if (event.text != "") {
                    if (level == 0) {
                        // Raw HTML at level 0 - output as-is without escaping
                        out(event.text)
                        atLineStart = event.text.endsWith('\n')
                    } else if (preCount > 0 || customMarkupCount > 0 || rawTextCount > 0) {
                        // Inside pre / custom markup / raw text - emit as-is
                        // without indentation. Custom markup and raw text also
                        // bypass HTML escaping.
                        if (customMarkupCount > 0 || rawTextCount > 0) {
                            out(event.text)
                        } else {
                            eventBuffer.escapeHtml(event.text)
                        }
                        atLineStart = false
                    } else {
                        // Re-indent after each newline. Escape line-by-line
                        // straight into eventBuffer — escaping doesn't insert
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
                                eventBuffer.escapeHtml(line)
                                atLineStart = false
                            }
                        }
                    }
                }
            }

            is Mark -> {
                val insidePre = preCount > 0
                val insideSvg = svgCount > 0
                if (event.name == "pre") {
                    preCount++
                }
                if (event.name == "svg") {
                    svgCount++
                }
                if (':' in event.name) {
                    customMarkupCount++
                }
                if (event.name in RAW_TEXT_ELEMENTS) {
                    rawTextCount++
                }
                val isBlock = event.isBlockMark(insideSvg, insidePre)
                if (isBlock) {
                    if (atLineStart) {
                        out(indentation)
                    } else {
                        out("\n")
                        out(indentation)
                    }
                } else {
                    if (atLineStart && !insidePre) {
                        out(indentation)
                    }
                    atLineStart = false
                }
                out("<"); out(event.name); event.flowAttributes()
                // Defer closing ">" for potential self-close detection
                pendingMarkName = event.name
                pendingMarkIsBlock = isBlock
                pendingMarkInsideSvg = insideSvg
            }

            is Unmark -> {
                if (event.name == "pre") {
                    preCount--
                }
                if (event.name == "svg") {
                    svgCount--
                }
                if (':' in event.name) {
                    customMarkupCount--
                }
                if (event.name in RAW_TEXT_ELEMENTS) {
                    rawTextCount--
                }
                val insidePre = preCount > 0
                val insideSvg = svgCount > 0
                level--
                indentation = indentAtom.repeat(level)
                val isBlock = event.isBlockMark(insideSvg, insidePre)
                if (isBlock) {
                    if (atLineStart) {
                        out(indentation)
                    } else {
                        out("\n")
                        out(indentation)
                    }
                } else if (atLineStart && !insidePre) {
                    out(indentation)
                }
                out("</"); out(event.name); out(">")
                if (isBlock) {
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
    flush()
}

/**
 * Collects [asHtml] into a single HTML string.
 */
// TODO later on we have to rename it to renderHtml
public suspend fun Flow<SemanticEvent>.render(): String = asHtml().joinToString()

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
    // SVG root element (all children are treated as block via svg context tracking)
    "svg",
)

// SVG inline elements that remain inline even inside SVG context.
// These elements contain text content where added whitespace would affect rendering.
private val SVG_INLINE_ELEMENTS = setOf(
    "tspan", "textPath", "a"
)

// HTML raw text elements whose content is not HTML-parsed (no escaping, no indentation)
private val RAW_TEXT_ELEMENTS = setOf(
    "style", "script"
)

// HTML void elements that cannot have children and are rendered as self-closing (e.g. <br/>)
private val VOID_ELEMENTS = setOf(
    "area", "base", "br", "col", "embed", "hr", "img",
    "input", "link", "meta", "param", "source", "track", "wbr"
)

private fun Appendable.escapeHtml(value: String) {
    for (c in value) when (c) {
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