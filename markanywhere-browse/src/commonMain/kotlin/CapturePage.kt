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
import com.xemantic.markanywhere.dump.FormControlState
import com.xemantic.markanywhere.dump.SemanticEventDump
import com.xemantic.markanywhere.dump.formControlAttributes
import com.xemantic.markanywhere.dump.formControlText
import com.xemantic.markanywhere.dump.hasLiveFormState
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

// The computed styles requested from the snapshot, in the order its per-node
// style lists report them.
private val COMPUTED_STYLES = listOf("display", "visibility", "-webkit-text-security")
private const val DISPLAY_STYLE = 0
private const val VISIBILITY_STYLE = 1
private const val TEXT_SECURITY_STYLE = 2

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
 * Form controls are the one place the walk departs from the markup: a control
 * is captured as it is *now* — the value typed into it, the box ticked, the
 * option chosen — and a control the page marks as secret has its value left
 * out (see [formControlAttributes], shared with the in-page walker of
 * `markanywhere-js`, so both captures redact alike).
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
    val snapshot = dOMSnapshot.captureSnapshot(
        computedStyles = COMPUTED_STYLES
    )
    val axNodes = accessibility.getAxTree(
        frameIds = snapshot.documents
            .map { snapshot.strings.getOrNull(it.frameId) }
            .distinct()
    )
    val builder = captureEvents(snapshot, axNodes, refAttribute, isActionable)
    return PageCapture(
        dump = SemanticEventDump(
            url = url ?: "",
            dumpedAt = Clock.System.now(),
            events = builder.events
        ),
        refs = builder.refs
    )
}

/**
 * Walks the main-frame document of [snapshot] (and, through their embedding
 * elements, every same-origin frame document it carries) into a
 * [DomEventBuilder] holding the event stream and the ref registry. Separated
 * from [capturePage] so the walk can be exercised on a hand-built snapshot.
 */
internal fun captureEvents(
    snapshot: DOMSnapshot.CaptureSnapshotReturn,
    axNodes: List<Accessibility.AXNode>,
    refAttribute: String?,
    isActionable: (Accessibility.AXNode?) -> Boolean
): DomEventBuilder {
    val dom = SnapshotDom(snapshot)
    val documents = mutableMapOf(0 to dom)
    val builder = DomEventBuilder(
        documents = { index ->
            documents.getOrPut(index) { SnapshotDom(snapshot, index) }
        },
        axIndex = buildMap {
            axNodes.forEach { node ->
                node.backendDOMNodeId?.let {
                    @OptIn(ExperimentalStdlibApi::class)
                    getOrPutIfMissing(it) { node }
                }
            }
        },
        refAttribute = refAttribute,
        isActionable = isActionable
    )
    builder.walkElement(dom, dom.htmlIndex, annotate = true)
    return builder
}

/**
 * The accessibility nodes of every document in a snapshot, one
 * `getFullAXTree` per frame in [frameIds], merged. Blink keeps an AX tree per
 * document and `getFullAXTree` walks one: the root frame's tree stops at a
 * same-origin frame's boundary (verified live — a `<button>` in a same-origin
 * `<iframe>` had no node in it, so it got no ref), so the frames the snapshot
 * carries are fetched by their own ids. The merged list is later keyed by
 * `backendDOMNodeId`, which is unique across the frames of one target. A
 * `null` id (a snapshot document without one) falls back to the root frame,
 * so the caller passes the ids deduplicated — two such documents would
 * otherwise fetch the root tree twice.
 */
private suspend fun Accessibility.getAxTree(
    frameIds: List<String?>
): List<Accessibility.AXNode> {
    enable()
    return try {
        frameIds.flatMap { getFullAXTree(frameId = it).nodes }
    } finally {
        disable()
    }
}

/**
 * Random-access view over the flattened parallel arrays of a
 * `DOMSnapshot.captureSnapshot` main-frame document.
 */
internal class SnapshotDom(
    snapshot: DOMSnapshot.CaptureSnapshotReturn,
    /**
     * Which of the snapshot's documents this view covers. `captureSnapshot`
     * returns index 0 for the main frame plus one document per **same-origin**
     * frame, each named by its embedding element through [contentDocumentIndex].
     *
     * **Cross-origin frames are not included** — verified against a live
     * bbc.com/news capture, where the snapshot held 3 documents (the page and
     * its two `about:blank` frames) while the Optimizely and consent frames had
     * no `contentDocumentIndex` and no document of their own. Disabling site
     * isolation does not change this: it is what `DOMSnapshot.captureSnapshot`
     * exposes for one target, not a process-boundary artifact. Reaching them
     * needs `Target.setAutoAttach(flatten = true)`, a snapshot per frame target
     * and stitching the results by frame id.
     */
    documentIndex: Int = 0,
) {

    private val strings = snapshot.strings // shared across all documents
    private val document = snapshot.documents[documentIndex]
    private val nodes = document.nodes
    private val layout = document.layout

    private val nodeType = requireNotNull(nodes.nodeType) { "no nodeType in snapshot" }
    private val nodeName = requireNotNull(nodes.nodeName) { "no nodeName in snapshot" }
    // memoised by [name]: the walk asks for an element's name several times over
    private val names = arrayOfNulls<String>(nodeName.size)
    private val nodeValue = requireNotNull(nodes.nodeValue) { "no nodeValue in snapshot" }
    private val backendIds = requireNotNull(nodes.backendNodeId) { "no backendNodeId in snapshot" }
    private val attributes = requireNotNull(nodes.attributes) { "no attributes in snapshot" }

    /**
     * Pseudo elements (`::before`, `::marker`, …) appear in the snapshot as
     * children of their originating element — they are not DOM elements and
     * are excluded from the walk and from capture-identity assignment.
     */
    private val pseudoNodes: Set<Int> = nodes.pseudoType?.index.toIndexSet()

    val children: List<List<Int>> = buildList<MutableList<Int>> {
        repeat(nodeType.size) { add(mutableListOf()) }
        nodes.parentIndex?.forEachIndexed { index, parent ->
            if (parent >= 0) this[parent] += index
        }
    }

    /** Computed `display` per laid-out node index, absent = not laid out. */
    private val display: Map<Int, String> = computedStyle(DISPLAY_STYLE)

    /** Computed `visibility` per laid-out node index, absent = not laid out. */
    private val visibility: Map<Int, String> = computedStyle(VISIBILITY_STYLE)

    /**
     * Computed `-webkit-text-security` per laid-out node index — a control
     * rendering its text masked — absent = not laid out.
     */
    private val textSecurity: Map<Int, String> = computedStyle(TEXT_SECURITY_STYLE)

    /**
     * One computed style per laid-out node index, [slot] indexing the
     * [COMPUTED_STYLES] request. Absent for a node that is not laid out.
     */
    private fun computedStyle(slot: Int): Map<Int, String> =
        rareMap(layout.nodeIndex, layout.styles) { styles ->
            styles.getOrNull(slot)?.let { string(it) }
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

    /**
     * The document's root element, or `null` when it has none — a frame that
     * never committed a document still yields an (empty) snapshot document, so
     * only the main frame is required to have one (see [htmlIndex]).
     */
    val htmlIndexOrNull: Int? = nodeType.indices.firstOrNull {
        isElement(it) && name(it) == "html"
    }

    /**
     * The main frame's root element. A getter, not an eager property: a view is
     * also constructed for a frame document, and one that never committed has
     * no root — requiring it at construction would fail the whole capture on
     * the way to the [htmlIndexOrNull] check that skips such a frame.
     */
    val htmlIndex: Int
        get() = requireNotNull(htmlIndexOrNull) {
            "no <html> element in the captured DOM"
        }

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
    fun name(index: Int): String = names[index] ?: string(nodeName[index]).orEmpty().let { name ->
        if (name.any { it.isLowerCase() }) name else name.lowercase()
    }.also { names[index] = it }

    fun text(index: Int): String = string(nodeValue[index]).orEmpty()

    fun backendNodeId(index: Int): Int = backendIds[index]

    fun attributeMap(index: Int): Map<String, String> {
        val flat = attributes[index]
        return buildMap {
            for (i in 0 until flat.size - 1 step 2) {
                put(
                    string(flat[i]).orEmpty(),
                    string(flat[i + 1]).orEmpty()
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

    /**
     * For an `<iframe>` / `<object>` / `<embed>`, the index in the snapshot's
     * `documents` of the document it embeds, or `null` when it embeds none
     * (or the frame produced no snapshot — a frame that never committed a
     * document, for instance).
     */
    fun contentDocumentIndex(index: Int): Int? = contentDocuments[index]

    private val contentDocuments: Map<Int, Int> = nodes.contentDocumentIndex.let {
        rareMap(it?.index, it?.value) { document -> document }
    }

    // The live state of form controls. The snapshot's `attributes` are the
    // *content* attributes — what the markup said — which typing, selecting or
    // ticking never changes; the state a user (or an agent driving the page)
    // produced lives in DOM properties, which the snapshot reports separately.
    private val inputValues: Map<Int, String> = nodes.inputValue.let {
        rareMap(it?.index, it?.value) { value -> string(value).orEmpty() }
    }
    private val textValues: Map<Int, String> = nodes.textValue.let {
        rareMap(it?.index, it?.value) { value -> string(value).orEmpty() }
    }
    private val checkedInputs: Set<Int> = nodes.inputChecked?.index.toIndexSet()
    private val selectedOptions: Set<Int> = nodes.optionSelected?.index.toIndexSet()

    /**
     * The live state of a form control (see [FormControlState]), or `null` for
     * an element that has none.
     */
    fun formState(index: Int): FormControlState? =
        if (hasLiveFormState(name(index))) FormControlState(
            value = inputValues[index] ?: textValues[index],
            checked = index in checkedInputs,
            selected = index in selectedOptions,
            masked = textSecurity[index].let { it != null && it != "none" },
        ) else null

    /**
     * The element's attributes as captured: the markup's, with a form
     * control's live state written over them (see [formControlAttributes]).
     */
    fun capturedAttributeMap(index: Int): Map<String, String> {
        val attributes = attributeMap(index)
        val state = formState(index) ?: return attributes
        return formControlAttributes(name(index), attributes, state)
    }

    /**
     * The text captured in place of the element's children — a `<textarea>`'s
     * current value (see [formControlText]) — or `null` to walk the children.
     */
    fun replacementText(index: Int): String? {
        val state = formState(index) ?: return null
        return formControlText(name(index), attributeMap(index), state)
    }

}

/**
 * Unpacks the snapshot's sparse "rare data" shape — parallel lists of node
 * [index]es and their [values] — into a map by node index, dropping a value
 * [transform] turns into `null`.
 */
private inline fun <T, R : Any> rareMap(
    index: List<Int>?,
    values: List<T>?,
    transform: (T) -> R?
): Map<Int, R> = buildMap {
    if (index == null || values == null) return@buildMap
    index.forEachIndexed { i, node ->
        values.getOrNull(i)?.let(transform)?.let { put(node, it) }
    }
}

/** The node indexes of a snapshot's boolean / presence rare data. */
private fun List<Int>?.toIndexSet(): Set<Int> = this?.toSet() ?: emptySet()

internal class DomEventBuilder(
    /**
     * The snapshot's documents, indexed as CDP indexes them: `[0]` is the main
     * frame and an `<iframe>`'s content document is looked up by the index its
     * element carries. Built lazily — a page with no frames pays for one view.
     */
    private val documents: (Int) -> SnapshotDom,
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
    fun walkElement(dom: SnapshotDom, element: Int, annotate: Boolean) {
        val annotated = annotate && dom.name(element) != "head"
        mark(dom, element, annotated)
        val replacementText = dom.replacementText(element)
        if (replacementText != null) {
            // what the field holds now, not the default text of its markup
            if (replacementText.isNotEmpty()) events += SemanticEvent.Text(replacementText)
        } else dom.children[element].forEach { child ->
            when {
                dom.isText(child) -> events += SemanticEvent.Text(dom.text(child))
                dom.isElement(child) -> walkElement(dom, child, annotated)
            }
        }
        // A frame's document is nested inside the element that embeds it, the
        // way a screen reader reads an iframe: its content is part of the same
        // accessibility tree, not a separate page. The embedding element has no
        // children of its own in the parent document (its markup is fallback
        // content, which the snapshot does not carry), so this always follows
        // an empty child loop. Recursion handles a frame inside a frame; a
        // cross-origin frame has no document here at all (see documentIndex).
        dom.contentDocumentIndex(element)?.let { contentIndex ->
            val content = documents(contentIndex)
            content.htmlIndexOrNull?.let { walkElement(content, it, annotated) }
        }
        unmark(dom, element)
    }

    private fun mark(dom: SnapshotDom, element: Int, annotate: Boolean) {
        val backendNodeId = dom.backendNodeId(element)
        // a dense, document-order ref on every actionable element, recorded so
        // PageSession can resolve it back to a live node for click / type
        val ref = if (refAttribute != null && isActionable(axIndex[backendNodeId])) {
            (++refCounter).toString().also { refs[it] = backendNodeId }
        } else null
        val attributes = buildMap {
            putAll(dom.capturedAttributeMap(element))
            if (ref != null) put(refAttribute!!, ref)
        }.let { if (annotate) it.withAccessibilityAnnotations(dom, element) else it }
        events += SemanticEvent.Mark(
            name = dom.name(element),
            isTagged = true,
            attributes = attributes
        )
    }

    private fun unmark(dom: SnapshotDom, element: Int) {
        events += SemanticEvent.Unmark(name = dom.name(element), isTagged = true)
    }

    private fun Int.axNode(dom: SnapshotDom): Accessibility.AXNode? =
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
        dom: SnapshotDom,
        element: Int
    ): Map<String, String> {
        val result = LinkedHashMap(this)
        if (dom.name(element) == "table") {
            element.axNode(dom)?.roleName?.let {
                result[AccessibilityAnnotations.ROLE] = it
            }
        }
        dom.displayAnnotation(element)?.let {
            result[AccessibilityAnnotations.DISPLAY] = it
        }
        if (dom.isVisibilityHidden(element)) {
            result[AccessibilityAnnotations.VISIBILITY] = "hidden"
        }
        // Blink's own decorative-image verdict: an <img> kept out of the
        // accessibility tree is decorative / redundant. Recorded for images
        // only — most other ignored nodes are structurally meaningful.
        if (dom.name(element) == "img" && element.axNode(dom)?.ignored == true) {
            result[AccessibilityAnnotations.IGNORED] = "true"
        }
        return result.withResolvedAccessibleName(dom, element)
    }

    /**
     * If this attribute map names its element only via `aria-labelledby`,
     * resolves the browser's computed accessible name into a synthetic
     * `aria-label`. A map that already carries an explicit `aria-label`, or
     * no `aria-labelledby`, or an element whose name computes to nothing, is
     * returned unchanged.
     */
    private fun Map<String, String>.withResolvedAccessibleName(
        dom: SnapshotDom,
        element: Int
    ): Map<String, String> = when {
        "aria-label" in this -> this
        "aria-labelledby" !in this -> this
        else -> {
            val name = element.axNode(dom)?.computedName?.trim()
            if (name.isNullOrEmpty()) this else this + ("aria-label" to name)
        }
    }

}

internal val Accessibility.AXNode.roleName: String?
    get() = (role?.value as? JsonPrimitive)?.contentOrNull

private val Accessibility.AXNode.computedName: String?
    get() = (name?.value as? JsonPrimitive)?.contentOrNull
