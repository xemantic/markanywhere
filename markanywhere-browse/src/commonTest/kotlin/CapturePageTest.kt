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
import com.xemantic.markanywhere.dump.AccessibilityAnnotations
import dev.kdriver.cdp.domain.DOMSnapshot
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
     * Form controls are captured with the live state the snapshot reports
     * beside the markup's attributes, through the rules shared with the
     * in-page walker — the redaction of a password and of a control the page
     * renders masked (its computed `-webkit-text-security`) among them.
     */
    @Test
    fun `should capture form controls with their live state`() {
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
        val redacted = AccessibilityAnnotations.REDACTED
        assert(
            events.outline() == listOf(
                "<html>", "<body>",
                "<input type=text value=Alice>", "</input>",
                "<input type=password $redacted=filled>", "</input>",
                "<input type=text $redacted=filled>", "</input>",
                "<textarea>", "new bio", "</textarea>",
                "<select>",
                "<option value=r>", "</option>",
                "<option value=g selected=>", "</option>",
                "</select>",
                "<input type=checkbox checked=>", "</input>",
                "<input type=submit value=Send>", "</input>",
                "</body>", "</html>"
            )
        )
    }

    /** Each event as one line, attributes in order, without display annotations. */
    private fun List<SemanticEvent>.outline(): List<String> = map { event ->
        when (event) {
            is Mark -> "<${event.name}" + event.attributes
                .filterKeys { it != AccessibilityAnnotations.DISPLAY }
                .entries.joinToString("") { " ${it.key}=${it.value}" } + ">"

            is Unmark -> "</${event.name}>"
            is Text -> event.text
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
