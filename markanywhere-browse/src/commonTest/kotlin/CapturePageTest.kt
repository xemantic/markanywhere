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

package com.xemantic.markanywhere.browse

import com.xemantic.kotlin.test.assert
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.dump.AccessibilityAnnotations.DISPLAY
import com.xemantic.markanywhere.dump.AccessibilityAnnotations.REDACTED
import com.xemantic.markanywhere.dump.AccessibilityAnnotations.VISIBILITY
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import dev.kdriver.cdp.domain.DOMSnapshot
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class CapturePageTest {

    @Test
    fun `should skip a frame whose document never committed`() {
        // given - a page embedding an iframe whose snapshot document holds no
        // <html> element (a frame that never committed a document), which CDP
        // still reports as a document of its own
        val strings = listOf("HTML", "BODY", "IFRAME", "#document", "src", "slow.html")
        val snapshot = DOMSnapshot.CaptureSnapshotReturn(
            documents = listOf(
                document(
                    nodeType = listOf(1, 1, 1),
                    nodeName = listOf(0, 1, 2),
                    parentIndex = listOf(-1, 0, 1),
                    backendNodeId = listOf(1, 2, 3),
                    attributes = listOf(emptyList(), emptyList(), listOf(4, 5)),
                    contentDocumentIndex = DOMSnapshot.RareIntegerData(
                        index = listOf(2),
                        value = listOf(1)
                    ),
                ),
                document(
                    nodeType = listOf(9),
                    nodeName = listOf(3),
                    parentIndex = listOf(-1),
                    backendNodeId = listOf(4),
                    attributes = listOf(emptyList()),
                ),
            ),
            strings = strings,
        )

        // when
        val events = captureEvents(
            snapshot = snapshot,
            axNodes = emptyList(),
            refAttribute = null,
            isActionable = { false },
        ).events

        // then - the empty frame is skipped, not fatal to the capture
        val outline = events.map {
            when (it) {
                is Mark -> "<${it.name}>"
                is Unmark -> "</${it.name}>"
                is Text -> it.text
            }
        }
        assert(outline == listOf("<html>", "<body>", "<iframe>", "</iframe>", "</body>", "</html>"))
        assert((events[2] as SemanticEvent.Mark)["src"] == "slow.html")
    }

    /**
     * Every element carries its computed `display`, the plain `block` default
     * included, so a stream without the annotation can only mean one that was
     * never captured — and an element that lays out no box of its own while
     * its descendants do (`display:contents`) is recorded as such, rather than
     * being indistinguishable from a `block` by an absent annotation.
     */
    @Test
    fun `should record every computed display including block and contents`() = runTest {
        // given
        val strings = mutableListOf<String>()
        fun string(value: String): Int =
            strings.indexOf(value).takeIf { it >= 0 } ?: strings.size.also { strings += value }

        val names = listOf("HTML", "BODY", "DIV", "SPAN", "#text", "B", "DIV")
        val snapshot = DOMSnapshot.CaptureSnapshotReturn(
            documents = listOf(
                document(
                    nodeType = names.map { if (it == "#text") 3 else 1 },
                    nodeName = names.map(::string),
                    nodeValue = names.map { if (it == "#text") string("US") else -1 },
                    parentIndex = listOf(-1, 0, 1, 2, 3, 2, 1),
                    backendNodeId = names.indices.map { it + 1 },
                    attributes = names.map { emptyList() },
                    // the span is `display:contents` — no box of its own, but
                    // its text is laid out — and the last div is `display:none`
                    layoutNodeIndex = listOf(0, 1, 2, 4, 5),
                    layoutStyles = listOf("block", "block", "block", "inline", "inline").map {
                        listOf(it, "visible", "none").map(::string)
                    },
                ),
            ),
            strings = strings,
        )

        // when
        val events = captureEvents(
            snapshot = snapshot,
            axNodes = emptyList(),
            refAttribute = null,
            isActionable = { false },
        ).events

        // then
        events.asFlow() sameAs semanticEvents(tagged = true) {
            "html"(DISPLAY to "block") {
                "body"(DISPLAY to "block") {
                    "div"(DISPLAY to "block") {
                        "span"(DISPLAY to "contents") { +"US" }
                        "b"(DISPLAY to "inline") { }
                    }
                    "div"(DISPLAY to "none") { }
                }
            }
        }
    }

    /**
     * An element laying out no box of its own (`display:contents`) has no
     * computed style in the layout tree, so its `visibility` is read from the
     * text it lays out — which inherits the element's own value. It is recorded
     * only where it differs from what the element would inherit: `hidden` set
     * on the element itself, or `visible` set back under a hidden parent.
     */
    @Test
    fun `should record the visibility of a display contents element from its text`() = runTest {
        // given
        val strings = mutableListOf<String>()
        fun string(value: String): Int =
            strings.indexOf(value).takeIf { it >= 0 } ?: strings.size.also { strings += value }

        val names = listOf("HTML", "BODY", "SPAN", "#text", "DIV", "SPAN", "#text", "SPAN", "#text")
        val texts = listOf("secret", "shown", "inherited")
        var textIndex = 0
        val snapshot = DOMSnapshot.CaptureSnapshotReturn(
            documents = listOf(
                document(
                    nodeType = names.map { if (it == "#text") 3 else 1 },
                    nodeName = names.map(::string),
                    nodeValue = names.map { if (it == "#text") string(texts[textIndex++]) else -1 },
                    parentIndex = listOf(-1, 0, 1, 2, 1, 4, 5, 4, 7),
                    backendNodeId = names.indices.map { it + 1 },
                    attributes = names.map { emptyList() },
                    // every span is `display:contents`: the first sets
                    // `visibility:hidden` itself, the second sets `visible`
                    // back inside a hidden div, the third inherits its
                    layoutNodeIndex = listOf(0, 1, 3, 4, 6, 8),
                    layoutStyles = listOf(
                        "block" to "visible",
                        "block" to "visible",
                        "inline" to "hidden",
                        "block" to "hidden",
                        "inline" to "visible",
                        "inline" to "hidden",
                    ).map { (display, visibility) ->
                        listOf(display, visibility, "none").map(::string)
                    },
                ),
            ),
            strings = strings,
        )

        // when
        val events = captureEvents(
            snapshot = snapshot,
            axNodes = emptyList(),
            refAttribute = null,
            isActionable = { false },
        ).events

        // then
        events.asFlow() sameAs semanticEvents(tagged = true) {
            "html"(DISPLAY to "block") {
                "body"(DISPLAY to "block") {
                    "span"(DISPLAY to "contents", VISIBILITY to "hidden") { +"secret" }
                    "div"(DISPLAY to "block", VISIBILITY to "hidden") {
                        "span"(DISPLAY to "contents", VISIBILITY to "visible") { +"shown" }
                        "span"(DISPLAY to "contents") { +"inherited" }
                    }
                }
            }
        }
    }

    /**
     * `visibility:collapse` hides an element like `hidden` (a collapsed table
     * row is not painted), so it is recorded as `hidden`, on a laid-out
     * element and through the text of a `display:contents` one alike.
     */
    @Test
    fun `should record visibility collapse as hidden`() = runTest {
        // given
        val strings = mutableListOf<String>()
        fun string(value: String): Int =
            strings.indexOf(value).takeIf { it >= 0 } ?: strings.size.also { strings += value }

        val names = listOf("HTML", "BODY", "DIV", "#text", "SPAN", "#text")
        val texts = listOf("collapsed", "contents")
        var textIndex = 0
        val snapshot = DOMSnapshot.CaptureSnapshotReturn(
            documents = listOf(
                document(
                    nodeType = names.map { if (it == "#text") 3 else 1 },
                    nodeName = names.map(::string),
                    nodeValue = names.map { if (it == "#text") string(texts[textIndex++]) else -1 },
                    parentIndex = listOf(-1, 0, 1, 2, 1, 4),
                    backendNodeId = names.indices.map { it + 1 },
                    attributes = names.map { emptyList() },
                    // the div is laid out collapsed, the span is
                    // `display:contents`, its text collapsed
                    layoutNodeIndex = listOf(0, 1, 2, 3, 5),
                    layoutStyles = listOf(
                        "block" to "visible",
                        "block" to "visible",
                        "block" to "collapse",
                        "inline" to "collapse",
                        "inline" to "collapse",
                    ).map { (display, visibility) ->
                        listOf(display, visibility, "none").map(::string)
                    },
                ),
            ),
            strings = strings,
        )

        // when
        val events = captureEvents(
            snapshot = snapshot,
            axNodes = emptyList(),
            refAttribute = null,
            isActionable = { false },
        ).events

        // then
        events.asFlow() sameAs semanticEvents(tagged = true) {
            "html"(DISPLAY to "block") {
                "body"(DISPLAY to "block") {
                    "div"(DISPLAY to "block", VISIBILITY to "hidden") { +"collapsed" }
                    "span"(DISPLAY to "contents", VISIBILITY to "hidden") { +"contents" }
                }
            }
        }
    }

    /**
     * Form controls are captured with the live state the snapshot reports
     * beside the markup's attributes, through the rules shared with the
     * in-page walker — the redaction of a password and of a control the page
     * renders masked (its computed `-webkit-text-security`) among them.
     */
    @Test
    fun `should capture form controls with their live state`() = runTest {
        // given
        val strings = mutableListOf<String>()
        fun string(value: String): Int =
            strings.indexOf(value).takeIf { it >= 0 } ?: strings.size.also { strings += value }
        fun attributes(vararg pairs: Pair<String, String>): List<Int> =
            pairs.flatMap { (name, value) -> listOf(string(name), string(value)) }

        val names = listOf(
            "HTML", "BODY", "INPUT", "INPUT", "INPUT", "TEXTAREA", "#text",
            "SELECT", "OPTION", "OPTION", "INPUT", "INPUT"
        )
        val snapshot = DOMSnapshot.CaptureSnapshotReturn(
            documents = listOf(
                document(
                    nodeType = names.map { if (it == "#text") 3 else 1 },
                    nodeName = names.map(::string),
                    nodeValue = names.map { if (it == "#text") string("old bio") else -1 },
                    parentIndex = listOf(-1, 0, 1, 1, 1, 1, 5, 1, 7, 7, 1, 1),
                    backendNodeId = names.indices.map { it + 1 },
                    attributes = listOf(
                        emptyList(),
                        emptyList(),
                        attributes("type" to "text", "value" to "preset"),
                        attributes("type" to "password", "value" to "markup-secret"),
                        attributes("type" to "text"),
                        emptyList(),
                        emptyList(),
                        emptyList(),
                        attributes("value" to "r", "selected" to ""),
                        attributes("value" to "g"),
                        attributes("type" to "checkbox"),
                        attributes("type" to "submit", "value" to "Send"),
                    ),
                    inputValue = DOMSnapshot.RareStringData(
                        index = listOf(2, 3, 4, 10, 11),
                        value = listOf("Alice", "typed", "1234", "on", "Send").map(::string)
                    ),
                    textValue = DOMSnapshot.RareStringData(
                        index = listOf(5),
                        value = listOf(string("new bio"))
                    ),
                    inputChecked = DOMSnapshot.RareBooleanData(index = listOf(10)),
                    optionSelected = DOMSnapshot.RareBooleanData(index = listOf(9)),
                    // every element laid out, only the third input masked
                    layoutNodeIndex = names.indices.filter { names[it] != "#text" },
                    layoutStyles = names.indices.filter { names[it] != "#text" }.map {
                        listOf("block", "visible", if (it == 4) "disc" else "none").map(::string)
                    },
                ),
            ),
            strings = strings,
        )

        // when
        val events = captureEvents(
            snapshot = snapshot,
            axNodes = emptyList(),
            refAttribute = null,
            isActionable = { false },
        ).events

        // then
        events.asFlow() sameAs semanticEvents(tagged = true) {
            "html"(DISPLAY to "block") {
                "body"(DISPLAY to "block") {
                    "input"("type" to "text", "value" to "Alice", DISPLAY to "block") { }
                    "input"("type" to "password", REDACTED to "filled", DISPLAY to "block") { }
                    "input"("type" to "text", REDACTED to "filled", DISPLAY to "block") { }
                    "textarea"(DISPLAY to "block") { +"new bio" }
                    "select"(DISPLAY to "block") {
                        "option"("value" to "r", DISPLAY to "block") { }
                        "option"("value" to "g", "selected" to "", DISPLAY to "block") { }
                    }
                    "input"("type" to "checkbox", "checked" to "", DISPLAY to "block") { }
                    "input"("type" to "submit", "value" to "Send", DISPLAY to "block") { }
                }
            }
        }
    }

    /**
     * Which option a secret select (a card expiry) has chosen is the secret:
     * no option keeps `selected`, markup or live, and the select says only
     * whether a real value is chosen — through an `<optgroup>` too.
     */
    @Test
    fun `should redact which option a secret select has chosen`() = runTest {
        // given
        val strings = mutableListOf<String>()
        fun string(value: String): Int =
            strings.indexOf(value).takeIf { it >= 0 } ?: strings.size.also { strings += value }
        fun attributes(vararg pairs: Pair<String, String>): List<Int> =
            pairs.flatMap { (name, value) -> listOf(string(name), string(value)) }

        val names = listOf("HTML", "BODY", "SELECT", "OPTION", "OPTGROUP", "OPTION")
        val snapshot = DOMSnapshot.CaptureSnapshotReturn(
            documents = listOf(
                document(
                    nodeType = names.map { 1 },
                    nodeName = names.map(::string),
                    parentIndex = listOf(-1, 0, 1, 2, 2, 4),
                    backendNodeId = names.indices.map { it + 1 },
                    attributes = listOf(
                        emptyList(),
                        emptyList(),
                        attributes("autocomplete" to "cc-exp-month"),
                        attributes("value" to "", "selected" to ""),
                        attributes("label" to "Summer"),
                        attributes("value" to "08"),
                    ),
                    optionSelected = DOMSnapshot.RareBooleanData(index = listOf(5)),
                    // every element laid out as a block
                    layoutNodeIndex = names.indices.toList(),
                    layoutStyles = names.map {
                        listOf("block", "visible", "none").map(::string)
                    },
                ),
            ),
            strings = strings,
        )

        // when
        val events = captureEvents(
            snapshot = snapshot,
            axNodes = emptyList(),
            refAttribute = null,
            isActionable = { false },
        ).events

        // then
        events.asFlow() sameAs semanticEvents(tagged = true) {
            "html"(DISPLAY to "block") {
                "body"(DISPLAY to "block") {
                    "select"("autocomplete" to "cc-exp-month", REDACTED to "filled", DISPLAY to "block") {
                        "option"("value" to "", DISPLAY to "block") { }
                        "optgroup"("label" to "Summer", DISPLAY to "block") {
                            "option"("value" to "08", DISPLAY to "block") { }
                        }
                    }
                }
            }
        }
    }

    private fun document(
        nodeType: List<Int>,
        nodeName: List<Int>,
        parentIndex: List<Int>,
        backendNodeId: List<Int>,
        attributes: List<List<Int>>,
        contentDocumentIndex: DOMSnapshot.RareIntegerData? = null,
        nodeValue: List<Int> = nodeType.map { -1 },
        inputValue: DOMSnapshot.RareStringData? = null,
        textValue: DOMSnapshot.RareStringData? = null,
        inputChecked: DOMSnapshot.RareBooleanData? = null,
        optionSelected: DOMSnapshot.RareBooleanData? = null,
        layoutNodeIndex: List<Int> = emptyList(),
        layoutStyles: List<List<Int>> = emptyList(),
    ) = DOMSnapshot.DocumentSnapshot(
        documentURL = -1,
        title = -1,
        baseURL = -1,
        contentLanguage = -1,
        encodingName = -1,
        publicId = -1,
        systemId = -1,
        frameId = -1,
        nodes = DOMSnapshot.NodeTreeSnapshot(
            parentIndex = parentIndex,
            nodeType = nodeType,
            nodeName = nodeName,
            nodeValue = nodeValue,
            backendNodeId = backendNodeId,
            attributes = attributes,
            contentDocumentIndex = contentDocumentIndex,
            inputValue = inputValue,
            textValue = textValue,
            inputChecked = inputChecked,
            optionSelected = optionSelected,
        ),
        layout = DOMSnapshot.LayoutTreeSnapshot(
            nodeIndex = layoutNodeIndex,
            styles = layoutStyles,
            bounds = emptyList(),
            text = emptyList(),
            stackingContexts = DOMSnapshot.RareBooleanData(index = emptyList()),
        ),
        textBoxes = DOMSnapshot.TextBoxSnapshot(
            layoutIndex = emptyList(),
            bounds = emptyList(),
            start = emptyList(),
            length = emptyList(),
        ),
    )

}
