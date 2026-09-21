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

    private fun document(
        nodeType: List<Int>,
        nodeName: List<Int>,
        parentIndex: List<Int>,
        backendNodeId: List<Int>,
        attributes: List<List<Int>>,
        contentDocumentIndex: DOMSnapshot.RareIntegerData? = null,
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
            nodeValue = nodeType.map { -1 },
            backendNodeId = backendNodeId,
            attributes = attributes,
            contentDocumentIndex = contentDocumentIndex,
        ),
        layout = DOMSnapshot.LayoutTreeSnapshot(
            nodeIndex = emptyList(),
            styles = emptyList(),
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
