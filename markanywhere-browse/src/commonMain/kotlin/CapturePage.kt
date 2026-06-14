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

import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.dump.AccessibilityAnnotations
import com.xemantic.markanywhere.dump.SemanticEventDump
import dev.kdriver.cdp.domain.Accessibility
import dev.kdriver.cdp.domain.DOMSnapshot
import dev.kdriver.cdp.domain.accessibility
import dev.kdriver.cdp.domain.dOMSnapshot
import dev.kdriver.core.tab.Tab
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Clock

private const val ELEMENT_NODE = 1
private const val TEXT_NODE = 3

/**
 * The result of a [capturePage]: the [SemanticEventDump] plus the `ref →
 * backendNodeId` registry that lets [PageSession] resolve a short element ref
 * back to a live, actionable DOM node.
 */
internal class PageCapture(
    val dump: SemanticEventDump,
    val refs: Map<String, Int>
)

/**
 * Captures the page open in this [Tab] as a [PageCapture]: the semantic event
 * stream of the rendered DOM tree (a [SemanticEventDump]) plus the `ref →
 * backendNodeId` registry [PageSession] uses to resolve element refs back to
 * live nodes. Both the DOM and the browser's internal (Blink) accessibility
 * tree are fetched over the Chrome DevTools Protocol and joined per element.
 *
 * The DOM side comes from `DOMSnapshot.captureSnapshot` rather than
 * `DOM.getDocument` — the latter silently omits whitespace-only text nodes
 * (DevTools-style filtering), and the downstream pipeline needs inter-tag
 * whitespace as a word separator. The snapshot also carries the computed
 * `display`/`visibility` per laid-out node, driving the display annotations.
 *
 * The walk is **lossless**: every element is captured, including the ones a
 * rendering- or accessibility-aware consumer will later drop or unwrap. The
 * verdicts that drive that downstream filtering are recorded *as data* on the
 * `mark` event's attributes (see [AccessibilityAnnotations]) rather than acted
 * on here — so a single dump can be replayed against different filtering
 * policies without re-capturing.
 *
 * @param refAttribute when non-null, a dense document-order ref is stamped on
 *   every element [isActionable] accepts (given its accessibility node), and the
 *   `ref → backendNodeId` map is collected for later element retrieval.
 * @param isActionable decides which elements receive a [refAttribute] ref.
 */
internal suspend fun Tab.capturePage(
    refAttribute: String?,
    isActionable: (Accessibility.AXNode?) -> Boolean
): PageCapture {
    val axNodes = accessibility.getAxTree()
    val snapshot = dOMSnapshot.captureSnapshot(
        computedStyles = listOf("display", "visibility")
    )
    val dom = SnapshotDom(snapshot)
    val builder = DomEventBuilder(
        dom = dom,
        axIndex = buildMap {
            axNodes.forEach { node ->
                node.backendDOMNodeId?.let { putIfAbsent(it, node) }
            }
        },
        refAttribute = refAttribute,
        isActionable = isActionable
    )
    builder.walkElement(dom.htmlIndex, annotate = true)
    return PageCapture(
        dump = SemanticEventDump(
            url = url ?: "",
            dumpedAt = Clock.System.now(),
            events = builder.events
        ),
        refs = builder.refs
    )
}

private suspend fun Accessibility.getAxTree(): List<Accessibility.AXNode> {
    enable()
    return try {
        getFullAXTree().nodes
    } finally {
        disable()
    }
}

/**
 * Random-access view over the flattened parallel arrays of a
 * `DOMSnapshot.captureSnapshot` main-frame document.
 */
private class SnapshotDom(snapshot: DOMSnapshot.CaptureSnapshotReturn) {

    private val strings = snapshot.strings
    private val nodes = snapshot.documents.first().nodes
    private val layout = snapshot.documents.first().layout

    private val nodeType = requireNotNull(nodes.nodeType) { "no nodeType in snapshot" }
    private val nodeName = requireNotNull(nodes.nodeName) { "no nodeName in snapshot" }
    private val nodeValue = requireNotNull(nodes.nodeValue) { "no nodeValue in snapshot" }
    private val backendIds = requireNotNull(nodes.backendNodeId) { "no backendNodeId in snapshot" }
    private val attributes = requireNotNull(nodes.attributes) { "no attributes in snapshot" }

    /**
     * Pseudo elements (`::before`, `::marker`, …) appear in the snapshot as
     * children of their originating element — they are not DOM elements and
     * are excluded from the walk and from capture-identity assignment.
     */
    private val pseudoNodes: Set<Int> =
        nodes.pseudoType?.index?.toSet() ?: emptySet()

    val children: List<List<Int>> = buildList<MutableList<Int>> {
        repeat(nodeType.size) { add(mutableListOf()) }
        nodes.parentIndex?.forEachIndexed { index, parent ->
            if (parent >= 0) this[parent] += index
        }
    }

    // styles are aligned with the computedStyles request: [0] = display,
    // [1] = visibility. Both are absent for a node that is not laid out.

    /** Computed `display` per laid-out node index, absent = not laid out. */
    private val display: Map<Int, String> = buildMap {
        layout.nodeIndex.forEachIndexed { i, node ->
            val styles = layout.styles.getOrNull(i) ?: return@forEachIndexed
            styles.getOrNull(0)?.let {
                put(node, string(it.toInt()) ?: return@forEachIndexed)
            }
        }
    }

    /** Computed `visibility` per laid-out node index, absent = not laid out. */
    private val visibility: Map<Int, String> = buildMap {
        layout.nodeIndex.forEachIndexed { i, node ->
            val styles = layout.styles.getOrNull(i) ?: return@forEachIndexed
            styles.getOrNull(1)?.let {
                put(node, string(it.toInt()) ?: return@forEachIndexed)
            }
        }
    }

    /**
     * Whether the node or any of its descendants has a layout object — the
     * discriminator between `display:none` (nothing in the subtree is
     * rendered) and `display:contents` (the element has no box but its
     * children render).
     */
    private val subtreeHasLayout: BooleanArray = BooleanArray(
        size = nodeType.size
    ).also { has ->
        layout.nodeIndex.forEach { has[it] = true }
        val parents = nodes.parentIndex ?: return@also
        // parents precede children in the flattened pre-order array,
        // so one reverse pass propagates the bit bottom-up
        for (index in nodeType.size - 1 downTo 0) {
            val parent = parents.getOrNull(index) ?: continue
            if (parent >= 0 && has[index]) has[parent] = true
        }
    }

    val htmlIndex: Int = requireNotNull(
        nodeType.indices.firstOrNull {
            isElement(it) && name(it) == "html"
        }
    ) { "no <html> element in the captured DOM" }

    private fun string(index: Int): String? = strings.getOrNull(index)

    fun isElement(index: Int): Boolean =
        nodeType[index] == ELEMENT_NODE && index !in pseudoNodes

    fun isText(index: Int): Boolean = nodeType[index] == TEXT_NODE

    /**
     * The element's local name: the snapshot only carries `nodeName`, which is
     * uppercased for HTML-namespace elements while foreign content (SVG,
     * MathML) keeps its case-significant name — so an all-uppercase name is
     * lowercased and any name already containing lowercase is preserved.
     */
    fun name(index: Int): String = string(nodeName[index]).orEmpty().let { name ->
        if (name.any { it.isLowerCase() }) name else name.lowercase()
    }

    fun text(index: Int): String = string(nodeValue[index]).orEmpty()

    fun backendNodeId(index: Int): Int = backendIds[index]

    fun attributeMap(index: Int): Map<String, String> {
        val flat = attributes[index]
        return buildMap {
            for (i in 0 until flat.size - 1 step 2) {
                put(
                    string(flat[i].toInt()).orEmpty(),
                    string(flat[i + 1].toInt()).orEmpty()
                )
            }
        }
    }

    /**
     * The value to record for [AccessibilityAnnotations.DISPLAY], or `null` when
     * nothing should be recorded: `none` when the element is not laid out and has
     * no laid-out descendant (`display:none`), the computed `display` when it is
     * laid out and that display is not the ubiquitous `block` default (so the
     * annotation flags only the inline / table / flex / … cases that change how a
     * downstream whitespace-collapser treats the element's boundaries), and
     * `null` for a plain `block` element or a not-laid-out `display:contents`
     * element (whose own box is transparent — its children's display is what
     * matters).
     */
    fun displayAnnotation(
        index: Int
    ): String? = when (val display = display[index]) {
        null -> if (subtreeHasLayout[index]) null else "none"
        "block" -> null
        else -> display
    }

    /** Whether the element is laid out with computed `visibility:hidden`. */
    fun isVisibilityHidden(index: Int): Boolean = visibility[index] == "hidden"

}

private class DomEventBuilder(
    private val dom: SnapshotDom,
    private val axIndex: Map<Int, Accessibility.AXNode>,
    private val refAttribute: String? = null,
    private val isActionable: (Accessibility.AXNode?) -> Boolean = { false },
) {

    val events = mutableListOf<SemanticEvent>()

    /** Dense, document-order ref → backendNodeId for the actionable elements. */
    val refs = LinkedHashMap<String, Int>()
    private var refCounter = 0

    /**
     * Walks [element] and its whole subtree, emitting a `mark` / children /
     * `unmark` triple per element and a `text` event per text node — nothing is
     * dropped. When [annotate] is `true` each `mark` is enriched with the
     * browser's accessibility verdicts (see [withAccessibilityAnnotations]);
     * the `<head>` subtree turns annotation off for itself and its descendants,
     * since it computes to `display:none` wholesale and annotating it would let
     * a downstream filter drop the `<title>` / `<meta>` provenance.
     */
    fun walkElement(element: Int, annotate: Boolean) {
        val annotated = annotate && dom.name(element) != "head"
        mark(element, annotated)
        dom.children[element].forEach { child ->
            when {
                dom.isText(child) -> events += SemanticEvent.Text(dom.text(child))
                dom.isElement(child) -> walkElement(child, annotated)
            }
        }
        unmark(element)
    }

    private fun mark(element: Int, annotate: Boolean) {
        val backendNodeId = dom.backendNodeId(element)
        // a dense, document-order ref on every actionable element, recorded so
        // PageSession can resolve it back to a live node for click / type
        val ref = if (refAttribute != null && isActionable(axIndex[backendNodeId])) {
            (++refCounter).toString().also { refs[it] = backendNodeId }
        } else null
        val attributes = buildMap {
            putAll(dom.attributeMap(element))
            if (ref != null) put(refAttribute!!, ref)
        }.let { if (annotate) it.withAccessibilityAnnotations(element) else it }
        events += SemanticEvent.Mark(
            name = dom.name(element),
            isTagged = true,
            attributes = attributes
        )
    }

    private fun unmark(element: Int) {
        events += SemanticEvent.Unmark(name = dom.name(element), isTagged = true)
    }

    private fun Int.axNode(): Accessibility.AXNode? =
        axIndex[dom.backendNodeId(this)]

    /**
     * Records the browser's accessibility verdicts as reserved attributes (see
     * [AccessibilityAnnotations]) so a downstream consumer can filter on them
     * without re-deriving anything: the computed accessibility role on a
     * `<table>` (Blink's own data-vs-layout verdict, read from the AX tree
     * rather than a reimplementation of its font/viewport-sensitive
     * `IsDataTable()` heuristic — absent when there is no AX node), the hiding
     * property of a rendered-hidden element, and a resolved accessible name. An
     * explicit `aria-hidden` is left untouched — it is already a real DOM
     * attribute the consumer can read directly.
     */
    private fun Map<String, String>.withAccessibilityAnnotations(
        element: Int
    ): Map<String, String> {
        val result = LinkedHashMap(this)
        if (dom.name(element) == "table") {
            element.axNode()?.roleName?.let {
                result[AccessibilityAnnotations.ROLE] = it
            }
        }
        dom.displayAnnotation(element)?.let {
            result[AccessibilityAnnotations.DISPLAY] = it
        }
        if (dom.isVisibilityHidden(element)) {
            result[AccessibilityAnnotations.VISIBILITY] = "hidden"
        }
        return result.withResolvedAccessibleName(element)
    }

    /**
     * If this attribute map names its element only via `aria-labelledby`,
     * resolves the browser's computed accessible name into a synthetic
     * `aria-label`. A map that already carries an explicit `aria-label`, or
     * no `aria-labelledby`, or an element whose name computes to nothing, is
     * returned unchanged.
     */
    private fun Map<String, String>.withResolvedAccessibleName(
        element: Int
    ): Map<String, String> = when {
        "aria-label" in this -> this
        "aria-labelledby" !in this -> this
        else -> {
            val name = element.axNode()?.computedName?.trim()
            if (name.isNullOrEmpty()) this else this + ("aria-label" to name)
        }
    }

}

internal val Accessibility.AXNode.roleName: String?
    get() = (role?.value as? JsonPrimitive)?.contentOrNull

private val Accessibility.AXNode.computedName: String?
    get() = (name?.value as? JsonPrimitive)?.contentOrNull
