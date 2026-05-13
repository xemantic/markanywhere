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
import kotlinx.browser.document
import kotlinx.coroutines.flow.Flow
import org.w3c.dom.Element

private const val HTML_NS = "http://www.w3.org/1999/xhtml"
private const val SVG_NS = "http://www.w3.org/2000/svg"
private const val MATHML_NS = "http://www.w3.org/1998/Math/MathML"
private const val XLINK_NS = "http://www.w3.org/1999/xlink"
private const val XML_NS = "http://www.w3.org/XML/1998/namespace"
private const val XMLNS_NS = "http://www.w3.org/2000/xmlns/"

private val HTML_ENCODINGS = setOf("text/html", "application/xhtml+xml")

public suspend fun Element.appendSemanticEvents(
    events: Flow<SemanticEvent>
) {

    val path = mutableListOf(this)
    val childNamespaceStack = mutableListOf(namespaceURI ?: HTML_NS)

    events.collect { event ->
        when (event) {

            is SemanticEvent.Mark -> {
                val parentChildNs = childNamespaceStack.last()
                val elementNs = resolveElementNamespace(event.name, parentChildNs)
                val element = event.toElement(elementNs)
                path.last().appendChild(element)
                path += element
                childNamespaceStack += resolveChildrenNamespace(event, elementNs)
            }

            is SemanticEvent.Text -> {
                path.last().appendChild(
                    document.createTextNode(event.text)
                )
            }

            is SemanticEvent.Unmark -> {
                path.last().normalize()
                path.removeLast()
                childNamespaceStack.removeLast()
            }

        }
    }
}

private fun resolveElementNamespace(
    name: String,
    parentChildNs: String
): String = when (name) {
    "svg" -> SVG_NS
    "math" -> MATHML_NS
    else -> parentChildNs
}

private fun resolveChildrenNamespace(
    mark: SemanticEvent.Mark,
    elementNs: String
): String = when {
    elementNs == SVG_NS && mark.name == "foreignObject" -> HTML_NS
    elementNs == MATHML_NS
        && mark.name == "annotation-xml"
        && mark.attributes?.get("encoding") in HTML_ENCODINGS -> HTML_NS
    else -> elementNs
}

private fun SemanticEvent.Mark.toElement(
    elementNs: String
): Element {
    val element = document.createElementNS(elementNs, name)
    attributes?.forEach { (key, value) ->
        val attrNs = attributeNamespace(key)
        if (attrNs == null) element.setAttribute(key, value)
        else element.setAttributeNS(attrNs, key, value)
    }
    return element
}

private fun attributeNamespace(
    name: String
): String? = when {
    name == "xmlns" || name.startsWith("xmlns:") -> XMLNS_NS
    name.startsWith("xlink:") -> XLINK_NS
    name.startsWith("xml:") -> XML_NS
    else -> null
}