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

package com.xemantic.markanywhere.js

import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.flow.SemanticEventScope
import com.xemantic.markanywhere.flow.semanticEvents
import kotlinx.coroutines.flow.Flow
import org.w3c.dom.Element
import org.w3c.dom.Text
import org.w3c.dom.asList

/**
 * Captures the DOM subtree rooted at this [Element] as a [Flow] of [SemanticEvent]s.
 */
public fun Element.toSemanticEvents(): Flow<SemanticEvent> = semanticEvents(
    tagged = true
) {
    flowElement(
        element = this@toSemanticEvents
    )
}

private suspend fun SemanticEventScope.flowElement(
    element: Element
) {

    val tagName = element.localName

    val attributes = element.attributes.asList().associate {
        it.name to it.value
    }

    tag(name = tagName, attributes) {
        flowChildren(element)
    }

}

private suspend fun SemanticEventScope.flowChildren(
    element: Element
) {
    element.childNodes.asList().forEach {
        when (it) {
            is Text -> +it.wholeText // unescaped, escaping done on render
            is Element -> {
                flowElement(it)
            }
        }
    }
}
