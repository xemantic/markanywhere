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
import com.xemantic.markanywhere.dump.CapturedFormControl
import com.xemantic.markanywhere.dump.FormControlState
import com.xemantic.markanywhere.dump.SemanticEventDump
import com.xemantic.markanywhere.dump.captureFormControl
import com.xemantic.markanywhere.dump.hasLiveFormState
import com.xemantic.markanywhere.dump.inputType
import com.xemantic.markanywhere.dump.isMaskingTextSecurity
import com.xemantic.markanywhere.dump.isRedactableElement
import com.xemantic.markanywhere.dump.isRedactedControl
import com.xemantic.markanywhere.dump.readsMaskedState
import com.xemantic.markanywhere.dump.secretValueTexts
import com.xemantic.markanywhere.html.spec.stripAndCollapseHtmlWhitespace
import dev.kdriver.cdp.domain.Accessibility
import dev.kdriver.cdp.domain.DOMSnapshot
import dev.kdriver.cdp.domain.accessibility
import dev.kdriver.cdp.domain.dom
import dev.kdriver.cdp.domain.dOMSnapshot
import dev.kdriver.cdp.domain.runtime
import dev.kdriver.core.tab.Tab
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

private const val ELEMENT_NODE = 1
private const val TEXT_NODE = 3

// The computed styles requested from the snapshot, in the order its per-node
// style lists report them.
private val COMPUTED_STYLES = listOf("display", "visibility", "-webkit-text-security")
private const val DISPLAY_STYLE = 0
private const val VISIBILITY_STYLE = 1
private const val TEXT_SECURITY_STYLE = 2

// the input types Blink names by their displayed value, formatted in the
// browser's locale, rather than by the value string (verified live)
private val LOCALE_FORMATTED_INPUT_TYPES = setOf("date", "month", "week", "time", "datetime-local", "number")

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
 * out (see [captureFormControl], shared with the in-page walker of
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
    val builder = captureEvents(snapshot, axNodes, refAttribute, isActionable) { maskedControls(it) }
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
 * Which of the controls with these [backendNodeIds] render their text masked
 * (see [isMaskingTextSecurity]), asked of the page for every control at once —
 * through `getComputedStyle`, which answers for an element without a layout
 * box too, as in the in-page walker. A control whose style cannot be read
 * (gone from the page since the snapshot, or in a document without a window)
 * counts as masked: the capture would rather hide a plain value than show a
 * secret one. The walker, reading a windowless element as unmasked, differs
 * there on purpose — it never meets one, walking the live page from its own
 * window.
 */
private suspend fun Tab.maskedControls(backendNodeIds: List<Int>): Set<Int> {
    if (backendNodeIds.isEmpty()) return emptySet()
    return try {
        coroutineScope {
            backendNodeIds
                .map { id -> async { id.takeIf { isMaskingTextSecurityOrUnknown(it) } } }
                .awaitAll()
                .filterNotNullTo(mutableSetOf())
        }
    } finally {
        withContext(NonCancellable) {
            try {
                runtime.releaseObjectGroup(MASKING_OBJECT_GROUP)
            } catch (_: Exception) {
                // a release that fails leaves the group to the page's lifetime; the capture stands
            }
        }
    }
}

private suspend fun Tab.isMaskingTextSecurityOrUnknown(backendNodeId: Int): Boolean = try {
    val element = dom.resolveNode(backendNodeId = backendNodeId, objectGroup = MASKING_OBJECT_GROUP).`object`
    val call = runtime.callFunctionOn(
        functionDeclaration = TEXT_SECURITY_FUNCTION,
        objectId = element.objectId,
        returnByValue = true
    )
    call.exceptionDetails != null || isMaskingAnswer(call.result.value)
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    true
}

/**
 * Whether the page's answer to the text-security lookup — the value
 * `getPropertyValue('-webkit-text-security')` returned — renders the control
 * masked (see [isMaskingTextSecurity]). Anything but a string (`undefined`
 * from a page overriding `getComputedStyle`, say) leaves the masking unknown,
 * so it counts as masked.
 */
internal fun isMaskingAnswer(value: JsonElement?): Boolean =
    if (value is JsonPrimitive && value.isString) isMaskingTextSecurity(value.content) else true

private const val MASKING_OBJECT_GROUP = "markanywhere-masking"

// the element's own window: a control in a frame has the frame's styles; an
// element of a windowless (inert) document throws, which counts as masked
private const val TEXT_SECURITY_FUNCTION =
    "function() { return this.ownerDocument.defaultView.getComputedStyle(this).getPropertyValue('-webkit-text-security'); }"

/**
 * Walks the main-frame document of [snapshot] (and, through their embedding
 * elements, every same-origin frame document it carries) into a
 * [DomEventBuilder] holding the event stream and the ref registry. Separated
 * from [capturePage] so the walk can be exercised on a hand-built snapshot.
 *
 * @param maskedControls asked, before the walk, which of the controls the
 *   snapshot carries no style for render their text masked (see
 *   [SnapshotDom.maskReadersOutsideLayout]): given their backend node ids, it
 *   returns those of the masked ones.
 */
internal suspend fun captureEvents(
    snapshot: DOMSnapshot.CaptureSnapshotReturn,
    axNodes: List<Accessibility.AXNode>,
    refAttribute: String?,
    isActionable: (Accessibility.AXNode?) -> Boolean,
    maskedControls: suspend (backendNodeIds: List<Int>) -> Set<Int> = { emptySet() },
): DomEventBuilder {
    // every document up front: a control in any of them may need asking about
    val documents = snapshot.documents.indices.map { SnapshotDom(snapshot, it) }
    val masked = maskedControls(documents.flatMap { it.maskReadersOutsideLayout() })
    documents.forEach { it.maskedOutsideLayout = masked }
    val dom = documents.first()
    val builder = DomEventBuilder(
        documents = { index -> documents[index] },
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

    /**
     * The backend node ids of the controls rendering their text masked though
     * not laid out — those of [maskReadersOutsideLayout] the page reported
     * masked when asked. Set before the walk: the snapshot reports no computed
     * style for these, so they can only be judged once the page has answered.
     */
    var maskedOutsideLayout: Set<Int> = emptySet()

    private val strings = snapshot.strings // shared across all documents
    private val document = snapshot.documents[documentIndex]
    private val nodes = document.nodes
    private val layout = document.layout
    private val parentIndex = nodes.parentIndex

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
        parentIndex?.forEachIndexed { index, parent ->
            if (parent >= 0) this[parent] += index
        }
    }

    /** Computed `display` per laid-out node index, absent = not laid out. */
    private val display: Map<Int, String> = computedStyle(DISPLAY_STYLE)

    /** Computed `visibility` per laid-out node index, absent = not laid out. */
    private val visibility: Map<Int, String> = computedStyle(VISIBILITY_STYLE)

    /**
     * Computed `-webkit-text-security` per laid-out node index — a control
     * rendering its text masked — absent = not laid out (see
     * [maskedOutsideLayout]).
     */
    private val textSecurity: Map<Int, String> = computedStyle(TEXT_SECURITY_STYLE)

    /** The laid-out node indexes — those the snapshot reports computed styles for. */
    private val laidOut: Set<Int> = layout.nodeIndex.toSet()

    /**
     * The backend node ids of the controls whose masking the capture reads
     * ([readsMaskedState]) but the snapshot cannot tell: it reports computed
     * styles for laid-out nodes only, so a PIN field in a hidden wizard step or
     * a collapsed panel comes without its `-webkit-text-security`. A control
     * redacted whatever its masking (a password, a secret `autocomplete`) is
     * left out, since the answer could not change its capture.
     */
    fun maskReadersOutsideLayout(): List<Int> = nodeType.indices.filter { index ->
        // by name first: no <option>, say, has its attributes decoded
        if (!isElement(index) || index in laidOut || !isRedactableElement(name(index))) return@filter false
        val attributes = attributeMap(index)
        readsMaskedState(name(index), attributes) &&
                !isRedactedControl(name(index), attributes, FormControlState(masked = false))
    }.map { backendNodeId(it) }

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
        val parents = parentIndex ?: return@also
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
     * The value to record for [AccessibilityAnnotations.DISPLAY] — recorded on
     * every element, so that a stream without it can only mean one that was
     * never captured: the computed `display` when the element is laid out (the
     * plain `block` default included), `contents` when it lays out no box of
     * its own while a descendant does (`display:contents`, whose box is
     * transparent — its children's display is what matters), and `none` when
     * nothing in its subtree is laid out (`display:none`).
     */
    fun displayAnnotation(
        index: Int
    ): String = display[index] ?: if (subtreeHasLayout[index]) "contents" else "none"

    /**
     * The value to record for [AccessibilityAnnotations.VISIBILITY], or `null`
     * when nothing should be recorded: `hidden` for an element laid out with
     * computed `visibility:hidden` — or `collapse`, which hides it the same
     * way (a collapsed table row or column is not painted either) — each
     * laid-out descendant inheriting it being annotated on its own.
     *
     * An element laying out no box of its own (`display:contents`) has no
     * computed style in the layout tree, so its value is read from the text it
     * lays out, which inherits it, and recorded only where it differs from what
     * the element inherits: `hidden` set on the element itself, or `visible`
     * set back under a hidden ancestor. Without such text nothing of the
     * element's own is painted, so its value does not matter.
     */
    fun visibilityAnnotation(index: Int): String? {
        visibility[index]?.let { return if (it.normalizedVisibility() == "hidden") "hidden" else null }
        val own = visibilities.own[index] ?: return null
        val inherited = parentIndex?.getOrNull(index)?.let { visibilities.resolved.getOrNull(it) } ?: "visible"
        return if (own == inherited) null else own
    }

    /** The visibility a box-less element's directly laid-out text inherits from it. */
    private fun ownVisibility(index: Int): String? = children[index].firstNotNullOfOrNull {
        if (isText(it)) visibility[it] else null
    }?.normalizedVisibility()

    /**
     * Per node, its own known visibility ([Visibilities.own] — computed, or
     * read from its text for a box-less element) and the one its descendants
     * inherit ([Visibilities.resolved] — its own, else its parent's). One
     * pre-order pass (parents precede children in the flattened array), so a
     * chain of `display:contents` wrappers is not re-walked for every element
     * in it, and no element's children are scanned twice.
     */
    private val visibilities: Visibilities by lazy {
        val own = arrayOfNulls<String>(nodeType.size)
        val resolved = Array(nodeType.size) { "visible" }
        for (index in nodeType.indices) {
            val parent = parentIndex?.getOrNull(index) ?: -1
            own[index] = visibility[index]?.normalizedVisibility() ?: ownVisibility(index)
            resolved[index] = own[index] ?: if (parent >= 0) resolved[parent] else "visible"
        }
        Visibilities(own, resolved)
    }

    private class Visibilities(val own: Array<String?>, val resolved: Array<String>)

    // only `hidden` is recorded for a laid-out element, so anything else reads
    // as visible here as well; `collapse` hides like `hidden`
    private fun String.normalizedVisibility(): String =
        if (this == "hidden" || this == "collapse") "hidden" else "visible"

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
    // `null` when the snapshot did not report them: unread, not "none at all"
    private val checkedInputs: Set<Int>? = nodes.inputChecked?.index?.toSet()
    private val selectedOptions: Set<Int>? = nodes.optionSelected?.index?.toSet()

    /**
     * The live state of a form control (see [FormControlState]), or `null` for
     * an element that has none.
     */
    fun formState(index: Int): FormControlState? = when (val name = name(index)) {
        "select" -> FormControlState(value = selectedValue(index))
        "option" -> optionState(index, select = enclosingSelect(index)?.let { attributeMap(it) })
        "textarea" -> {
            val value = inputValues[index] ?: textValues[index]
            FormControlState(
                value = value,
                masked = isMasked(index, attributeMap(index)),
                // the default text stands in only for a value the snapshot did not report
                text = if (value == null) textContent(index) else null,
            )
        }

        else -> if (hasLiveFormState(name)) FormControlState(
            value = inputValues[index] ?: textValues[index],
            checked = checkedInputs?.let { index in it },
            masked = isMasked(index, attributeMap(index)),
        ) else null
    }

    /**
     * The state of an `<option>` belonging to a select with the attributes
     * [select], if any — with its [text][FormControlState.text] only when
     * [withText], since only its [secretValueTexts] read that.
     */
    private fun optionState(option: Int, select: Map<String, String>?, withText: Boolean = false) = FormControlState(
        selected = selectedOptions?.let { option in it },
        select = select,
        text = if (withText) textContent(option) else null,
    )

    /**
     * Whether the control renders its text masked (see [isMaskingTextSecurity]),
     * judged only for a control whose capture reads it ([readsMaskedState]):
     * by its computed style when it is laid out — masked when the snapshot
     * left that style out, its masking unknown — else as the page answered for
     * it ([maskedOutsideLayout]). Laid out means reported in the layout tree,
     * the criterion [maskReadersOutsideLayout] picks the controls to ask by.
     */
    private fun isMasked(index: Int, attributes: Map<String, String>): Boolean =
        readsMaskedState(name(index), attributes) && when (index) {
            in laidOut -> textSecurity[index]?.let(::isMaskingTextSecurity) ?: true
            else -> backendNodeId(index) in maskedOutsideLayout
        }

    /**
     * Whether the `<option>` is selected: as the snapshot reports it, else as
     * the markup declares it when the snapshot did not report selection.
     */
    private fun isSelected(option: Int): Boolean =
        selectedOptions?.let { option in it } ?: ("selected" in attributeMap(option))

    /**
     * The value of the option a `<select>` has selected — its `value`
     * attribute, else its text with HTML (ASCII) whitespace stripped and
     * collapsed, as the form would submit it — or `null` when none is. Not
     * `trim()`: that strips an NBSP too, which the browser submits.
     */
    private fun selectedValue(select: Int): String? =
        descendants(select).firstOrNull { name(it) == "option" && isSelected(it) }
            ?.let { option -> attributeMap(option)["value"] ?: textContent(option).stripAndCollapseHtmlWhitespace() }

    /**
     * The text of the node's descendant text nodes whatever their display — an
     * `<option>`'s label, a `<textarea>`'s markup value: everything below a
     * `<select>` is annotated `none` (see [displayAnnotation]), its popup having
     * no layout box.
     */
    private fun textContent(index: Int): String =
        descendants(index).filter { isText(it) }.joinToString("") { text(it) }

    /** The `<select>` an `<option>` belongs to, through an `<optgroup>`, if any. */
    private fun enclosingSelect(option: Int): Int? =
        ancestors(option).firstOrNull { name(it) == "select" || name(it) == "datalist" }
            ?.takeIf { name(it) == "select" }

    private fun ancestors(index: Int): Sequence<Int> =
        generateSequence(parentIndex?.getOrNull(index)?.takeIf { it >= 0 }) { node ->
            parentIndex?.getOrNull(node)?.takeIf { it >= 0 }
        }

    private fun descendants(index: Int): Sequence<Int> = children[index].asSequence().flatMap {
        sequenceOf(it) + descendants(it)
    }

    /**
     * The form control as captured (see [captureFormControl]): its attributes
     * with the live state written over the markup's and the text standing in
     * for its children — or `null` for an element that is no form control.
     */
    fun capturedFormControl(index: Int): CapturedFormControl? =
        formState(index)?.let { captureFormControl(name(index), attributeMap(index), it) }

    /**
     * [name], a name the browser computed for an element, with the value of
     * every secret control taken out of it — or an empty string when a value
     * cannot be taken out cleanly.
     *
     * The accessible-name computation takes a text box's *value* (a select's
     * chosen option) whenever it reaches one, and it reaches one along many
     * paths — `aria-labelledby`, the element's content, its `<label>`s,
     * `aria-owns`, a descendant's own `aria-labelledby`, shadow trees — so
     * whether a secret *can* reach the name is not judged here; the name is
     * checked for the secrets themselves instead, which the capture knows
     * already (see [isRedactedControl]). A secret is then a word run of its
     * own in the name ("Card number 4111…") and is dropped, label text kept;
     * one glued to other text ("Card4111…", or a CVC `123` inside an unrelated
     * `1234`) cannot be told apart from it, so the name is withheld whole.
     *
     * A control Blink names by the value it *displays* rather than the value
     * itself — a date, time or number, formatted in the browser's locale
     * (`May 2027` for `2027-05`, `987,50` for `987.50`) — cannot be found as
     * written, and predicting the locale's format would re-implement Blink. So
     * the name fails closed: it is withheld whole when it holds any number of
     * such a value ([secretNumbers]), in whatever decimal digits it is written.
     */
    fun withoutSecrets(name: String): String {
        if (secretTexts.isEmpty() && secretNumbers.isEmpty()) return name
        var result = name.stripAndCollapseHtmlWhitespace()
        if (secretTexts.none { it in result } && !result.holdsSecretNumber()) return name
        while (true) {
            val secret = secretTexts.firstOrNull { it in result }
                ?: return if (result.holdsSecretNumber()) "" else result
            val start = result.indexOf(secret)
            val end = start + secret.length
            if (start > 0 && result[start - 1] != ' ' || end < result.length && result[end] != ' ') return ""
            result = (result.take(start) + result.drop(end)).stripAndCollapseHtmlWhitespace()
        }
    }

    private fun String.holdsSecretNumber(): Boolean =
        secretNumbers.isNotEmpty() && decimalNumbers().any { it in secretNumbers }

    /**
     * Every [redacted][isRedactedControl] control in the document, with the
     * texts its value lends (see [secretValueTexts]). A control is judged by
     * its markup and masking alone before its state is read, so a control that
     * holds no secret costs no state — a long `<select>` above all, whose
     * options are walked only when it is secret, and then once.
     */
    private val secretValues: List<Pair<Int, List<String>>> by lazy {
        nodeType.indices.asSequence()
            // by name first: an <option>, say, costs nothing before it is skipped
            .filter { isElement(it) && isRedactableElement(name(it)) }
            .filter {
                val attributes = attributeMap(it)
                isRedactedControl(name(it), attributes, FormControlState(masked = isMasked(it, attributes)))
            }
            .map { index -> index to secretTextsOf(index) }
            .toList()
    }

    /**
     * The [secretValueTexts] of a redacted control — of a `<select>`, those of
     * its options, which hold its secret: which one is chosen.
     */
    private fun secretTextsOf(index: Int): List<String> = when (val name = name(index)) {
        "select" -> {
            val select = attributeMap(index)
            descendants(index)
                .filter { name(it) == "option" }
                .flatMap { option -> secretValueTexts("option", attributeMap(option), optionState(option, select, withText = true)) }
                .toList()
        }

        else -> secretValueTexts(name, attributeMap(index), formState(index) ?: FormControlState())
    }

    /**
     * The text every [redacted][isRedactedControl] control can lend to an
     * accessible name, whitespace collapsed as in a computed name, longest
     * first so a secret containing another is taken out whole: a text box's
     * value, a select's chosen options by text and `label`.
     *
     * A `type=password` field lends none: Blink names it by its mask, a bullet
     * per character, never by its value — markup or typed, along every path
     * (verified in `SecretNameTest`) — so counting it would only cut or
     * withhold unrelated names holding the same text.
     */
    private val secretTexts: List<String> by lazy {
        secretValues.asSequence()
            .filterNot { (index, _) -> isPasswordField(index) }
            .flatMap { (_, texts) -> texts }
            .map { it.stripAndCollapseHtmlWhitespace() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedByDescending { it.length }
            .toList()
    }

    /**
     * The numbers in the value of every redacted control Blink names by its
     * displayed, locale-formatted value ([LOCALE_FORMATTED_INPUT_TYPES]): the
     * value's runs of decimal digits, leading zeros dropped, since a locale
     * may write `5` for `05` (see [decimalNumbers]).
     */
    private val secretNumbers: Set<String> by lazy {
        secretValues.asSequence()
            .filter { (index, _) ->
                name(index) == "input" && inputType(attributeMap(index)) in LOCALE_FORMATTED_INPUT_TYPES
            }
            .flatMap { (_, texts) -> texts.asSequence().flatMap { it.decimalNumbers() } }
            .toSet()
    }

    /**
     * Whether the element is an `<input>` Blink treats as a password field: its
     * `type` ASCII case-insensitively `password`, untrimmed — Blink does not
     * trim it, so a `type=" password"` is a text field naming its value.
     */
    private fun isPasswordField(index: Int): Boolean =
        name(index) == "input" && attributeMap(index)["type"]?.equalsAsciiIgnoreCase("password") == true

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

/**
 * The runs of decimal digits in this text, each written in ASCII digits with
 * its leading zeros dropped (`0` for a run of zeros alone): any Unicode
 * decimal digit counts, since a locale may write a formatted date or number
 * in its own (`٢٠٢٧` for `2027`).
 */
private fun String.decimalNumbers(): Sequence<String> = sequence {
    val run = StringBuilder()
    suspend fun SequenceScope<String>.flush() {
        if (run.isNotEmpty()) yield(run.trimStart('0').ifEmpty { "0" }.toString())
        run.clear()
    }
    for (char in this@decimalNumbers) {
        val digit = char.digitToIntOrNull()
        if (digit != null) run.append(digit) else flush()
    }
    flush()
}

/**
 * Whether this text equals the lowercase [lowercase] ASCII-case-insensitively,
 * as Blink compares an enumerated attribute. Not `equals(ignoreCase = true)`:
 * that folds Unicode case too, reading `paſſword` (long s) as `password`.
 */
private fun String.equalsAsciiIgnoreCase(lowercase: String): Boolean {
    if (length != lowercase.length) return false
    for (i in indices) {
        val char = this[i]
        val folded = if (char in 'A'..'Z') char + ('a' - 'A') else char
        if (folded != lowercase[i]) return false
    }
    return true
}

/** The node indexes of a snapshot's boolean / presence rare data. */
private fun List<Int>?.toIndexSet(): Set<Int> = this?.toSet() ?: emptySet()

internal class DomEventBuilder(
    /**
     * The snapshot's documents, indexed as CDP indexes them: `[0]` is the main
     * frame and an `<iframe>`'s content document is looked up by the index its
     * element carries.
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
        // computed once: it gives both the mark's attributes and the content
        val control = dom.capturedFormControl(element)
        mark(dom, element, control?.attributes ?: dom.attributeMap(element), annotated)
        val replacementText = control?.text
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

    private fun mark(dom: SnapshotDom, element: Int, capturedAttributes: Map<String, String>, annotate: Boolean) {
        val backendNodeId = dom.backendNodeId(element)
        // a dense, document-order ref on every actionable element, recorded so
        // PageSession can resolve it back to a live node for click / type
        val ref = if (refAttribute != null && isActionable(axIndex[backendNodeId])) {
            (++refCounter).toString().also { refs[it] = backendNodeId }
        } else null
        val attributes = buildMap {
            putAll(capturedAttributes)
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
        result[AccessibilityAnnotations.DISPLAY] = dom.displayAnnotation(element)
        dom.visibilityAnnotation(element)?.let {
            result[AccessibilityAnnotations.VISIBILITY] = it
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
     * returned unchanged. The value of a secret control is taken out of the
     * name (see [SnapshotDom.withoutSecrets]), since the computed one may carry
     * what the capture redacts from the control itself.
     */
    private fun Map<String, String>.withResolvedAccessibleName(
        dom: SnapshotDom,
        element: Int
    ): Map<String, String> {
        if ("aria-label" in this || "aria-labelledby" !in this) return this
        // trim, which strips non-breaking spaces too: a name of them alone
        // would read as a label downstream while showing nothing
        val computed = element.axNode(dom)?.computedName?.trim()
        if (computed.isNullOrEmpty()) return this
        val name = dom.withoutSecrets(computed)
        return if (name.isEmpty()) this else this + ("aria-label" to name)
    }

}

internal val Accessibility.AXNode.roleName: String?
    get() = (role?.value as? JsonPrimitive)?.contentOrNull

private val Accessibility.AXNode.computedName: String?
    get() = (name?.value as? JsonPrimitive)?.contentOrNull
