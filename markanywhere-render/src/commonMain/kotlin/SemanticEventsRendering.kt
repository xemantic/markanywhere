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

import com.xemantic.kotlin.core.text.buildText
import com.xemantic.markanywhere.SemanticEvent
import kotlinx.coroutines.flow.Flow

/**
 * Converts the flow of [SemanticEvent]s into a string.
 *
 * The output is pretty-printed with 2-space indentation for block elements.
 * Inline elements are rendered on the same line as their surrounding content.
 * Content inside `<pre>` elements is not indented to preserve whitespace.
 * Content inside `<style>` and `<script>` elements is not indented or HTML-escaped
 * (HTML raw text elements).
 * Custom namespaced elements (containing `:`) are treated as block elements.
 * All elements inside `<svg>` are treated as block elements for
 * Chrome DevTools-like indentation, except for inline text elements
 * (`tspan`, `textPath`, `a`) which remain inline to preserve text content.
 * HTML void elements and empty SVG elements are rendered with XHTML
 * self-closing syntax (e.g. `<br/>`, `<img src="..."/>`).
 */
public suspend fun Flow<SemanticEvent>.render(): String = buildText {

    var level = 0
    val indentAtom = "  "
    var indentation = ""
    var atLineStart = true
    var preCount = 0
    var svgCount = 0
    var customMarkupCount = 0
    var rawTextCount = 0

    // Pending mark state for self-closing detection.
    // When a Mark event is processed, the closing ">" is deferred until the next
    // event arrives, so we can detect empty elements and render them as self-closing.
    var pendingMarkName: String? = null
    var pendingMarkIsBlock = false
    var pendingMarkInsideSvg = false

    fun SemanticEvent.Mark.flowAttributes() {
        attributes?.forEach { (name, value) ->
            +" "; +name; +"=\""; +value.escapeAttributeValue(); +"\""
        }
    }

    fun confirmPendingMark() {
        if (pendingMarkName == null) return
        pendingMarkName = null
        +">"
        level++
        indentation = indentAtom.repeat(level)
        if (pendingMarkIsBlock) {
            +"\n"
            atLineStart = true
        }
    }

    fun SemanticEvent.Marked.isBlockMark(insideSvg: Boolean, insidePre: Boolean): Boolean =
        (isBlock || (insideSvg && name !in SVG_INLINE_ELEMENTS)) && !insidePre

    try {
        collect { event ->

            // Check for self-closing opportunity before processing the next event
            val pmName = pendingMarkName
            if (pmName != null) {
                if (
                    event is SemanticEvent.Unmark
                    && event.name == pmName
                    && (pmName in VOID_ELEMENTS || pendingMarkInsideSvg)
                ) {
                    pendingMarkName = null
                    // Undo counter increments from Mark processing
                    if (pmName == "pre") preCount--
                    if (pmName == "svg") svgCount--
                    if (':' in pmName) customMarkupCount--
                    if (pmName in RAW_TEXT_ELEMENTS) rawTextCount--
                    +"/>"
                    if (pendingMarkIsBlock) {
                        +"\n"
                        atLineStart = true
                    }
                    return@collect
                } else {
                    confirmPendingMark()
                }
            }

            when (event) {

                is SemanticEvent.Text -> {
                    if (event.text != "") {
                        val text = if (customMarkupCount > 0 || rawTextCount > 0) {
                            event.text  // Don't escape inside custom markup or raw text elements
                        } else {
                            event.text.escapeHtml()
                        }
                        if (preCount == 0 && customMarkupCount == 0 && rawTextCount == 0) {
                            // Handle newlines in text by re-indenting after each newline
                            val lines = text.split('\n')
                            lines.forEachIndexed { index, line ->
                                if (index > 0) {
                                    +"\n"
                                    atLineStart = true
                                }
                                if (line.isNotEmpty()) {
                                    if (atLineStart) {
                                        +indentation
                                    }
                                    +line
                                    atLineStart = false
                                }
                            }
                        } else {
                            // Inside pre or custom markup - output text as-is without indentation
                            +text
                            atLineStart = false
                        }
                    }
                }

                is SemanticEvent.Mark -> {
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
                            +indentation
                        } else {
                            +"\n"
                            +indentation
                        }
                    } else {
                        if (atLineStart && !insidePre) {
                            +indentation
                        }
                        atLineStart = false
                    }
                    +"<"; +event.name; event.flowAttributes()
                    // Defer closing ">" for potential self-close detection
                    pendingMarkName = event.name
                    pendingMarkIsBlock = isBlock
                    pendingMarkInsideSvg = insideSvg
                }

                is SemanticEvent.Unmark -> {
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
                            +indentation
                        } else {
                            +"\n"
                            +indentation
                        }
                    } else if (atLineStart && !insidePre) {
                        +indentation
                    }
                    +"</"; +event.name; +">"
                    if (isBlock) {
                        +"\n"
                        atLineStart = true
                    } else {
                        atLineStart = false
                    }
                }

            }
        }
    } finally {
        // Resolve any remaining pending mark (last childless element or malformed unclosed element)
        confirmPendingMark()
    }

    trimLastNewLine()

}

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

private fun String.escapeHtml(): String = buildText {
    for (char in this@escapeHtml) {
        when (char) {
            '<' -> +"&lt;"
            '>' -> +"&gt;"
            '&' -> +"&amp;"
            else -> +char
        }
    }
}

private fun String.escapeAttributeValue(): String = buildText {
    for (char in this@escapeAttributeValue) {
        when (char) {
            '<' -> +"&lt;"
            '>' -> +"&gt;"
            '&' -> +"&amp;"
            '"' -> +"&quot;"
            else -> +char
        }
    }
}

private val SemanticEvent.Marked.isBlock get() = name in BLOCK_ELEMENTS || name.contains(":")
