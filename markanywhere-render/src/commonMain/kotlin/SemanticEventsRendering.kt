/*
 * Copyright 2025 Kazimierz Pogoda / Xemantic
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

import com.xemantic.markanywhere.MarkedSemanticEvent
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.buildString
import kotlinx.coroutines.flow.Flow
import kotlin.text.iterator

/**
 * Converts the flow of [SemanticEvent]s into a string.
 *
 * The output is pretty-printed with 2-space indentation for block elements.
 * Inline elements are rendered on the same line as their surrounding content.
 * Content inside `<pre>` elements is not indented to preserve whitespace.
 * Custom namespaced elements (containing `:`) are treated as block elements.
 */
public suspend fun Flow<SemanticEvent>.render(): String = buildString {

    var level = 0
    val indentAtom = "  "
    var indentation = ""
    var atLineStart = true
    var preCount = 0

    fun SemanticEvent.Mark.flowAttributes() {
        attributes?.forEach { (name, value) ->
            +" "; +name; +"=\""; +value.escapeAttributeValue(); +"\""
        }
    }

    collect { event ->
        when (event) {

            is SemanticEvent.Text -> {
                if (event.text != "") {
                    if (atLineStart && preCount == 0) {
                        +indentation
                    }
                    +event.text.escapeHtml()
                    atLineStart = false
                }
            }

            is SemanticEvent.Mark -> {
                val insidePre = preCount > 0
                if (event.name == "pre") {
                    preCount++
                }
                val isBlockMark = event.isBlock && !insidePre
                if (isBlockMark) {
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
                +"<"; +event.name; event.flowAttributes(); +">"
                level++
                indentation = indentAtom.repeat(level)
                if (isBlockMark) {
                    +"\n"
                    atLineStart = true
                }
            }

            is SemanticEvent.Unmark -> {
                if (event.name == "pre") {
                    preCount--
                }
                val insidePre = preCount > 0
                level--
                indentation = indentAtom.repeat(level)
                val isBlockMark = event.isBlock && !insidePre
                if (isBlockMark) {
                    if (atLineStart) {
                        +indentation
                    } else {
                        +"\n"
                        +indentation
                    }
                }
                +"</"; +event.name; +">"
                if (isBlockMark) {
                    +"\n"
                    atLineStart = true
                }
            }

        }
    }

    trimLastNewLine()

}

// Block elements that expand with newlines and indentation
private val BLOCK_ELEMENTS = setOf(
    "div", "section", "article", "header", "footer", "nav", "aside", "main",
    "p", "pre",
    "ul", "ol", "li", "dl", "dt", "dd",
    "table", "thead", "tbody", "tfoot", "tr", "th", "td",
    "blockquote", "figure", "figcaption",
    "details", "summary",
    "footnote", "h1", "h2", "h3", "h4", "h5", "h6"
)

private fun String.escapeHtml(): String = buildString {
    for (char in this@escapeHtml) {
        when (char) {
            '<' -> +"&lt;"
            '>' -> +"&gt;"
            '&' -> +"&amp;"
            else -> +char
        }
    }
}

private fun String.escapeAttributeValue(): String = buildString {
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

private val MarkedSemanticEvent.isBlock get() = name in BLOCK_ELEMENTS || name.contains(":")
