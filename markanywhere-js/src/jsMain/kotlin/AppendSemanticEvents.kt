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

package com.xemantic.markanywhere.js

import com.xemantic.markanywhere.SemanticEvent
import kotlinx.browser.document
import kotlinx.coroutines.flow.Flow
import org.w3c.dom.Element

public suspend fun Element.appendSemanticEvents(
    events: Flow<SemanticEvent>
) {

    val path = mutableListOf(this)

    events.collect { event ->
        when (event) {

            is SemanticEvent.Mark -> {
                val element = event.toElement()
                path.last().appendChild(element)
                path += element
            }

            is SemanticEvent.Text -> {
                path.last().appendChild(
                    document.createTextNode(event.text)
                )
            }

            is SemanticEvent.Unmark -> {
                path.last().normalize()
                path.removeLast()
            }

        }
    }
}

private fun SemanticEvent.Mark.toElement(): Element {
    val element = document.createElement(name)
    attributes?.forEach { (key, value) ->
        element.setAttribute(key, value)
    }
    return element
}
