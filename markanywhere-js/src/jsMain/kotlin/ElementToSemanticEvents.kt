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
import org.w3c.dom.Node
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
    element.childNodes.asList().forEach { node ->
        node.asTextOrNull?.let { +it.data } // escaping done on render
        node.asElementOrNull?.let { flowElement(it) }
    }
    // A frame's document belongs inside the element that embeds it, the way a
    // screen reader reads an iframe — its content is part of the same
    // accessibility tree, not a separate page. It is not reachable through
    // `childNodes` (the frame holds a whole separate document), so it is
    // descended explicitly, and only when same-origin: `contentDocument` is
    // `null` for a cross-origin frame by the same-origin policy, which page
    // script cannot lift. A capture that must reach cross-origin frames has to
    // come from the CDP side (`CapturePage`), which is not bound by it.
    element.frameDocumentElement()?.let { flowElement(it) }
}

/**
 * The root element of the document this element embeds, or `null` when it
 * embeds none, when the frame is cross-origin, or when it has not yet
 * committed a document.
 *
 * Reading `contentDocument` on a cross-origin frame is not an error — the
 * browser simply hands back `null` (unlike `contentWindow.document`, which
 * throws a `SecurityError`) — so no origin comparison or guard is needed.
 */
private fun Element.frameDocumentElement(): Element? =
    // The typed DOM API, not `asDynamic()`: a `dynamic as? Element` cast is
    // always `null` in Kotlin/JS, because an external interface carries no
    // runtime type information for the check to succeed.
    // Same reason as the walk above: `is HTMLIFrameElement` would be false for a
    // frame *nested inside another frame*, so the element is recognised by name
    // and its `contentDocument` read dynamically.
    when (localName) {
        "iframe", "frame", "object" ->
            asDynamic().contentDocument?.documentElement.unsafeCast<Element?>()
        else -> null
    }

/**
 * This node as a [Text], or `null` when it is not one.
 *
 * Dispatches on `nodeType`, never on `is Text`: an iframe's document is a
 * separate JS realm with its own DOM constructors, so `instanceof Text` —
 * which is what a Kotlin/JS `is` compiles to — is **false** for every node
 * inside a frame. Keying the walk off the type check silently emitted each
 * frame as an empty `<html></html>`. The `unsafeCast` is a compile-time
 * no-op, so this costs nothing at runtime.
 *
 * Read its [Text.data], not `wholeText`: the latter concatenates every
 * logically adjacent text node, so walking two adjacent siblings (the shape
 * a runtime renderer leaves behind) would emit their combined text twice.
 */
private val Node.asTextOrNull: Text?
    get() = if (nodeType == Node.TEXT_NODE) unsafeCast<Text>() else null

/**
 * This node as an [Element], or `null` when it is not one.
 *
 * Same realm-agnostic `nodeType` dispatch as [asTextOrNull].
 */
private val Node.asElementOrNull: Element?
    get() = if (nodeType == Node.ELEMENT_NODE) unsafeCast<Element>() else null
