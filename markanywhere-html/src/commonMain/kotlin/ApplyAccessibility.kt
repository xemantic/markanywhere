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

package com.xemantic.markanywhere.html

import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.dump.AccessibilityAnnotations
import com.xemantic.markanywhere.html.spec.isHtmlBlank
import com.xemantic.markanywhere.transform.MatcherScope
import com.xemantic.markanywhere.transform.TransformerBuilder
import com.xemantic.markanywhere.transform.transform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * Applies the browser's accessibility verdicts that a page dump recorded as
 * reserved annotations (see [AccessibilityAnnotations]) onto an otherwise
 * untouched, full-DOM event stream:
 *
 * - **Drop hidden subtrees** — an element with `aria-hidden="true"` or
 *   [AccessibilityAnnotations.DISPLAY] `= none` (`display:none`) is dropped
 *   together with its whole subtree. Inside a `<select>` the `display:none`
 *   half of that rule is suspended — the popup lays out no box for any of its
 *   content, which the capture cannot tell apart from real hiding (see
 *   [SELECT_CONTENT_MODE]).
 * - **Drop invisible content, not its visible descendants** — the text of an
 *   element with [AccessibilityAnnotations.VISIBILITY] `= hidden`
 *   (`visibility:hidden`) is dropped, and the element itself only when nothing
 *   below it is visible: unlike `display:none`, `visibility` is inherited and a
 *   descendant may set it back to `visible` (see [resolveVisibility]).
 * - **Unwrap layout tables** — a `<table>` whose computed role
 *   ([AccessibilityAnnotations.ROLE]) is not a data-table role (Blink's
 *   `LayoutTable`, `generic`, `none`, …) emits no mark; its structural
 *   `thead`/`tbody`/`tr`/`td`/… descendants are likewise unwrapped and the real
 *   cell content is promoted into the surrounding flow. A nested `<table>` is
 *   re-evaluated independently, so a data table inside a layout table survives.
 *   A `<table>` carrying a data-table role (or no role annotation at all) is
 *   left intact.
 *
 * Everything else passes through unchanged, and the reserved annotation
 * attributes are stripped from every surviving element so they do not leak
 * downstream. Keeping this policy here — rather than in the capture — lets one
 * lossless dump be replayed against different filtering rules.
 *
 * In the HTML→Markdown pipeline this runs *before* `simplifyHtml` (which then
 * sees a tree already cleaned of hidden/layout noise).
 */
public fun Flow<SemanticEvent>.applyAccessibility(): Flow<SemanticEvent> =
    resolveVisibility().transform { accessibilityRuleSet() }

/**
 * Formerly registered the accessibility rules on a [TransformerBuilder]. The
 * `visibility:hidden` verdicts now need a streaming pre-pass no rule can
 * express (a mode cannot return to its caller's), so the policy is only
 * available as the [Flow] operator: apply it before your own `transform`.
 */
@Deprecated(
    message = "visibility now needs a pre-pass rules cannot express; " +
            "apply the Flow operator before your own transform instead",
    level = DeprecationLevel.ERROR,
)
public fun TransformerBuilder.applyAccessibility() {
    throw UnsupportedOperationException(
        "use Flow<SemanticEvent>.applyAccessibility() before the transform"
    )
}

/**
 * Resolves the `visibility:hidden` verdicts ahead of the rule set, the same way
 * wherever the element sits — a layout table's skeleton and a `<select>`'s
 * popup included, which the rule set handles in modes of their own.
 *
 * `visibility` is inherited and a descendant can set it back to `visible`
 * (Gmail's compose dialog sits in a `visibility:hidden` host), so an invisible
 * element is not a hidden *subtree*: its own text is dropped, and its mark is
 * held back until a visible descendant shows that its box still matters —
 * the `<a>` a visible label clicks through, the `<table>`/`<tr>` a visible cell
 * sits in, the `<form>` a visible input submits. An invisible element with
 * nothing visible below it is dropped whole. Only marks are held, never
 * content: nothing is emitted while the verdict is pending, as everything
 * below an invisible element is either dropped text or another pending mark.
 *
 * The capture records `visibility` for every element that lays out a box, so
 * an inherited `hidden` is on each such descendant. One that lays out none
 * (`display: contents`, `display: none`) carries a verdict only where its own
 * value differs from the one it inherits (`hidden`, or `visible` again), and
 * otherwise inherits its parent's — as does a descendant with no recorded
 * display at all (a hand-built stream, or a dump captured before every display
 * was recorded, where it may well be `display: contents`). An invisible frame
 * (`<iframe>`, …) hides the document nested in it whole: `visibility` does not
 * inherit across the document boundary, so the frame's elements carry no
 * verdict, yet the browser paints none of them.
 *
 * The whitespace-only text of an invisible element is kept, held back like
 * the marks: should the element be kept for a visible descendant, it is what
 * separates the visible content on either side (`Inbox Sent`, not
 * `InboxSent`); dropped with the element otherwise.
 *
 * Unlike `display:none`, an invisible element still takes its space: the
 * browser paints a blank gap where its text (or its image, or its block) is,
 * so the content on either side never reads as one word. Its other text
 * therefore becomes a single space, and an element dropped whole leaves one
 * in its place (`Price 5`, not `Price5`) — when it lays out any width at all:
 * text, a box that is not plain `inline`, or a replaced element
 * ([SPACE_TAKING_ELEMENTS]). An empty inline element takes none, so
 * `a<span></span>b` stays one word, as in the browser. The space is only
 * written after inline content not already ending in whitespace — at the
 * edge of a line (a block, a `<br>`) there is nothing for it to separate — and
 * never into verbatim text (`pre`, `code`, `textarea`). Content laying out no
 * box (`display:none`, the H5BP `.hidden { display: none; visibility: hidden }`
 * included) or dropped by the rule set anyway takes no space and is no
 * content to separate either.
 *
 * The `<head>` never inherits invisibility: its metadata is not painted at
 * all, so `visibility` does not apply to it, and the capture leaves it
 * unannotated — inheriting an anti-flicker `html { visibility: hidden }` lost
 * the page's title and front matter.
 *
 * An invisible `<td>`/`<th>` in a row that is emitted is kept as an (empty)
 * cell, so the columns after it stay aligned, and likewise an invisible
 * `<thead>` and its rows in a table that is emitted, so the first body row is
 * not promoted to the table's header — structure closing while the verdict of
 * its parent is still pending is held (as marks) until that verdict is known.
 */
private fun Flow<SemanticEvent>.resolveVisibility(): Flow<SemanticEvent> = flow {

    class Frame(
        val mark: SemanticEvent.Mark,
        val invisible: Boolean,
        var emitted: Boolean,
        // this element or an ancestor lays out no box (`display:none`, …) or
        // is dropped by the rule set anyway: it takes no space and is no
        // content a gap could separate
        val silent: Boolean,
        // inside a `<select>`'s popup, where `display:none` is an artifact
        // (see [SELECT_CONTENT_MODE])
        val inPopup: Boolean,
        // inside verbatim text, where no gap is written
        val verbatim: Boolean,
    ) {
        // whether this invisible element lays out any width — its own box, or
        // text / a space-taking descendant below it
        var takesSpace = invisible && !silent && mark.takesSpaceWhenEmpty()

        // the invisible table structure this held-back element closed,
        // re-emitted empty if the element turns out to be shown — allocated
        // only by the few elements holding any, not by every mark
        var heldStructure: MutableList<SemanticEvent>? = null

        fun hold(events: List<SemanticEvent>) {
            (heldStructure ?: mutableListOf<SemanticEvent>().also { heldStructure = it }) += events
        }
    }

    val stack = ArrayDeque<Frame>()
    var skipDepth = 0 // inside an invisible frame's document

    // whether the last content emitted that survives the rule set is inline
    // content an invisible gap after it would separate from what follows: not
    // the edge of a line, and not already whitespace
    var separable = false

    suspend fun FlowCollector<SemanticEvent>.out(
        event: SemanticEvent,
        silent: Boolean,
        opening: SemanticEvent.Mark? = null
    ) {
        emit(event)
        separable = separable.after(event, silent, opening)
    }

    // [separable] as it will be once the held-back content of the pending
    // frames is emitted — in stack order, as [emitHeldBack] emits it — so a
    // second gap written behind a held one sees the first
    fun pendingSeparable(): Boolean {
        var result = separable
        for (frame in stack) {
            if (frame.emitted) continue
            result = result.after(frame.mark, frame.silent)
            frame.heldStructure?.forEach { result = result.after(it, frame.silent) }
        }
        return result
    }

    // the gap an invisible element (or its text) paints, written into
    // [target] — the element it sits in
    suspend fun FlowCollector<SemanticEvent>.separate(target: Frame?) {
        if (target?.silent == true) return
        target?.takesSpace = true
        if (target?.verbatim == true || !pendingSeparable()) return
        val space = SemanticEvent.Text(" ")
        if (target == null || target.emitted) out(space, silent = false) else target.hold(listOf(space))
    }

    // Emitted frames are always a prefix of the stack: a frame is emitted only
    // after every ancestor was.
    suspend fun FlowCollector<SemanticEvent>.emitHeldBack() {
        val first = stack.indexOfFirst { !it.emitted }
        if (first < 0) return
        for (index in first until stack.size) {
            val frame = stack[index]
            out(frame.mark, frame.silent)
            frame.emitted = true
            frame.heldStructure?.forEach { out(it, frame.silent) }
        }
    }

    collect { event ->
        if (skipDepth > 0) {
            when (event) {
                is SemanticEvent.Mark -> skipDepth++
                is SemanticEvent.Unmark -> skipDepth--
                is SemanticEvent.Text -> { /* drop */ }
            }
            return@collect
        }
        when (event) {
            is Mark -> {
                val parent = stack.lastOrNull()
                val invisible = event.isInvisible(parentInvisible = parent?.invisible == true)
                val inPopup = (parent?.mark?.name == "select" || parent?.inPopup == true)
                        && event.hasNoLayoutBox()
                val silent = parent?.silent == true || event.isDroppedContent(honourDisplayNone = !inPopup)
                if (invisible && event.name in FRAME_TAGS) {
                    // a frame keeps its box like any replaced element
                    if (!silent && event.takesSpaceWhenEmpty()) separate(parent)
                    skipDepth = 1
                    return@collect
                }
                val mark = if (AccessibilityAnnotations.VISIBILITY in event.attributes) {
                    event.copy(attributes = event.attributes - AccessibilityAnnotations.VISIBILITY)
                } else event
                val frame = Frame(
                    mark, invisible, emitted = false,
                    silent = silent,
                    inPopup = inPopup,
                    verbatim = parent?.verbatim == true || event.name in VERBATIM_TAGS,
                )
                if (!invisible || (parent?.emitted == true && event.isTableStructure(parent.mark.name))) {
                    if (parent != null && !parent.emitted) emitHeldBack()
                    out(mark, silent)
                    frame.emitted = true
                }
                stack.addLast(frame)
            }
            is Text -> {
                val parent = stack.lastOrNull()
                if (parent?.invisible != true) out(event, silent = parent?.silent == true)
                // the whitespace of an element kept for a visible descendant
                // still separates the visible content on either side of it
                else if (event.text.isHtmlBlank()) {
                    if (!parent.silent) parent.takesSpace = true
                    if (parent.emitted) out(event, parent.silent) else parent.hold(listOf(event))
                }
                // and its other text is painted as a blank gap of its width
                else separate(parent)
            }
            is Unmark -> {
                val frame = stack.removeLastOrNull()
                val parent = stack.lastOrNull()
                if (frame == null || frame.emitted) {
                    out(event, silent = frame?.silent == true, opening = frame?.mark)
                } else if (parent != null && frame.mark.isTableStructure(parent.mark.name)) {
                    parent.hold(listOf(frame.mark))
                    frame.heldStructure?.let { parent.hold(it) }
                    parent.hold(listOf(event))
                }
                // dropped whole, yet its box still takes its space
                else if (frame.takesSpace) separate(parent)
            }
        }
    }
}

/**
 * Whether content after this event can be separated from the content before
 * it by an invisible gap, given whether it [this] could before: inline
 * content not ending in whitespace can, the edge of a line cannot. A [silent]
 * event — content the rule set drops, laying out no box — changes nothing.
 * [opening] is the mark an unmark closes, when known (an unmark carries no
 * display of its own); otherwise its tag's UA default decides.
 */
private fun Boolean.after(
    event: SemanticEvent,
    silent: Boolean,
    opening: SemanticEvent.Mark? = null
): Boolean = if (silent) this else when (event) {
    is Text -> if (event.text.isEmpty()) this else !event.text.last().isWhitespace()
    // a replaced element's content (a <select>'s options, a <button>'s
    // label) lays out in a box of its own, starting at its edge
    is Mark -> if (event.name in SPACE_TAKING_ELEMENTS || event.isLineEdge()) false else this
    is Unmark -> when {
        opening?.isLineEdge() ?: (event.name == "br" || !isInlineByUaDefault(event.name)) -> false
        // and the closed box is inline content of the flow around it
        event.name in SPACE_TAKING_ELEMENTS -> true
        else -> this
    }
}

/**
 * Whether this element starts or ends a line: a block-level box, or a `<br>`
 * whatever display the capture recorded for it.
 */
private fun SemanticEvent.Mark.isLineEdge(): Boolean = name == "br" || !isInlineLevel()

/**
 * Whether the rule set drops this element with its subtree, or the browser
 * lays no box out for it: either way it takes no space and is no content.
 */
private fun SemanticEvent.Mark.isDroppedContent(honourDisplayNone: Boolean): Boolean =
    name in NEVER_RENDERED_TAGS
        || isHidden(honourDisplayNone)
        || (name == "img" && isAxIgnoredImage())

/** Elements whose text is verbatim, so no gap is written into it. */
private val VERBATIM_TAGS = setOf("pre", "code", "textarea")

/**
 * Whether this element lays out width of its own even with no text in it.
 * The display decides first: `none` and `contents` lay out no box, a plain
 * `inline` box only a replaced element's ([SPACE_TAKING_ELEMENTS]) has width,
 * any other box (a block, an `inline-block`, …) has. An element with no
 * recorded display falls back to the UA default of its tag, like every
 * operator.
 */
private fun SemanticEvent.Mark.takesSpaceWhenEmpty(): Boolean =
    when (this[AccessibilityAnnotations.DISPLAY]) {
        null -> name in SPACE_TAKING_ELEMENTS
            || (!isInlineByUaDefault() && !isNeverRenderedByUaDefault())
        "none", "contents" -> false
        "inline" -> name in SPACE_TAKING_ELEMENTS
        else -> true
    }

/**
 * Replaced elements and frames: each lays out a box of its own size, so an
 * invisible one still takes its space between the content around it, with no
 * text in it — and a visible one is inline content of the flow around it,
 * while its own content starts at the edge of that box.
 */
private val SPACE_TAKING_ELEMENTS = setOf(
    "img", "input", "textarea", "select", "button", "svg", "canvas", "video",
    "audio", "iframe", "frame", "embed", "object",
)

/**
 * Whether this element is table structure an emitted parent keeps even when
 * invisible: a cell holds its column, and a header row keeps the first body
 * row from becoming the table's header.
 */
private fun SemanticEvent.Mark.isTableStructure(parentName: String): Boolean = when (name) {
    "td", "th", "thead" -> true
    "tr" -> parentName == "thead"
    else -> false
}

/**
 * The rules applying every verdict but `visibility`, which [resolveVisibility]
 * has already resolved.
 */
private fun TransformerBuilder.accessibilityRuleSet() {

    // --- default mode ---------------------------------------------------

    accessibilityRules()

    // everything else flows through, annotations stripped.
    match("*") { event -> passThrough(event) }
    matchText { +it }

    // --- inside an unwrapped layout table -------------------------------

    // once outside a <select>, once inside one: the popup artifact is
    // orthogonal to the table skeleton, and a layout table can perfectly well
    // sit in a JS-built select's popup, so both combinations need a mode.
    layoutTableRules(LAYOUT_TABLE_MODE, insideSelect = false)
    layoutTableRules(LAYOUT_TABLE_IN_SELECT_MODE, insideSelect = true)

    // --- inside a <select> ----------------------------------------------

    accessibilityRules(mode = SELECT_CONTENT_MODE, insideSelect = true)

    // everything else — options, optgroups, the wrappers between them, an
    // option's own inline markup — flows through, and the meaningless `none`
    // is replaced by the UA default display of the tag.
    match("*", mode = SELECT_CONTENT_MODE) { event -> passThroughSelectContent(event) }
    matchText(mode = SELECT_CONTENT_MODE) { +it }

    // --- the direct children of an <option> in a <select>'s popup --------

    accessibilityRules(mode = OPTION_CONTENT_MODE, insideSelect = true)

    // the same, but a customizable select lays the option out as a flex
    // container, which blockifies these children (see [OPTION_CONTENT_MODE]).
    match("*", mode = OPTION_CONTENT_MODE) { event ->
        passThroughSelectContent(event, blockified = true)
    }
    matchText(mode = OPTION_CONTENT_MODE) { +it }
}

/**
 * Registers the rules unwrapping a layout table's skeleton, in [mode].
 *
 * [insideSelect] says whether this table was reached inside a `<select>`, and
 * is threaded through unchanged: the popup artifact covers a table's content
 * exactly as it covers everything else below the `<select>`
 * (see [SELECT_CONTENT_MODE]), so unwrapping the skeleton must not start
 * honouring `display:none` again halfway down.
 */
private fun TransformerBuilder.layoutTableRules(
    mode: String,
    insideSelect: Boolean
) {

    // the shared rules apply here as well — registered per mode, because a
    // mode-specific matcher (the wildcard below) is preferred over any
    // default-mode one, so an unrepeated rule would be shadowed.
    accessibilityRules(mode = mode, insideSelect = insideSelect)

    // a cell's content is promoted, then a single space separates it from the
    // next cell so adjacent cells don't merge into one word once the table
    // skeleton is gone (real captures often carry no inter-cell whitespace).
    // Downstream `dropHtmlStructuralWhitespace` collapses/trims the extra space.
    match({ name == "td" || name == "th" }, mode = mode) {
        children(mode = mode)
        +" "
    }

    // the remaining structural descendants are unwrapped, staying in layout-table
    // mode so the skeleton keeps promoting; `<table>` is deliberately not in
    // this set.
    match({ name in TABLE_STRUCTURAL_TAGS }, mode = mode) {
        children(mode = mode)
    }

    // real cell content is re-emitted and its subtree leaves the skeleton: for
    // the default mode (so nested tables / hidden subtrees are handled
    // normally), or back into the select's popup when that is where the table
    // sits.
    match("*", mode = mode) { event ->
        if (insideSelect) passThroughSelectContent(event) else passThrough(event)
    }
    // inter-cell whitespace text is promoted into the surrounding flow.
    matchText(mode = mode) { +it }
}

/**
 * Registers the rules that apply in every [mode], on the [mode] itself.
 *
 * They cannot be registered once in the default mode and inherited: the
 * transformer prefers a matcher registered on the current mode over a
 * default-mode one, and every mode ends with a `match("*")` wildcard — which
 * would match first and shadow them all.
 *
 * [insideSelect] says whether [mode] describes a position inside a `<select>`,
 * where the `display:none` half of the hidden verdict is an artifact rather
 * than an author signal (see [SELECT_CONTENT_MODE]). It is a property of the
 * *position*, not of the mode's own name — [LAYOUT_TABLE_IN_SELECT_MODE] is
 * the layout-table skeleton reached inside a popup — so it is passed in rather
 * than derived from [mode].
 *
 * Order matters within a mode: the first matching rule wins.
 */
private fun TransformerBuilder.accessibilityRules(
    mode: String? = null,
    insideSelect: Boolean = false
) {

    // never rendered content: <script>, <style>, … carry no display verdict of
    // their own in a hand-built (or non-Chrome-sourced) stream, and leaking
    // their source as text would be worse than losing them.
    match({ name in NEVER_RENDERED_TAGS }, mode = mode) { /* drop */ }

    // hidden subtree: no children() → the whole subtree is skipped. Inside a
    // <select> the `display:none` half of the verdict is suspended — the popup
    // lays out no box for any of its content (see [SELECT_CONTENT_MODE]).
    match(
        { isHidden(honourDisplayNone = !insideSelect) },
        mode = mode
    ) { /* drop */ }

    // decorative image: Blink kept it out of the accessibility tree (empty alt,
    // role=presentation, a superseded lazy-load placeholder, …). An <img> is a
    // void element, so there is no subtree to descend — just drop it.
    match({ name == "img" && isAxIgnoredImage() }, mode = mode) { /* drop */ }

    // layout table: drop the mark, descend unwrapping the structural skeleton.
    // A nested layout table keeps unwrapping; a nested data table falls through
    // to the wildcard and is re-emitted (then re-evaluated in default mode).
    match({ name == "table" && isLayoutTable() }, mode = mode) {
        children(
            mode = if (insideSelect) LAYOUT_TABLE_IN_SELECT_MODE else LAYOUT_TABLE_MODE
        )
    }

    // a (visible — the rule above already dropped a hidden one) <select>: its
    // popup lives outside the layout tree, so the display verdict is unreliable
    // for the whole subtree. Descend with the `display:none` drop suspended.
    match({ name == "select" }, mode = mode) { event ->
        passThrough(
            event,
            childMode = SELECT_CONTENT_MODE,
            // the select's own verdict is genuine unless it is the popup's
            // artifact itself (a <select> nested in a JS-built select subtree)
            insidePopup = insideSelect && event.hasNoLayoutBox()
        )
    }
}

private const val LAYOUT_TABLE_MODE = "layoutTable"

/**
 * [LAYOUT_TABLE_MODE] for a layout table reached inside a `<select>`: the
 * skeleton is unwrapped exactly the same way, but the popup's `display:none`
 * artifact still covers everything below (see [SELECT_CONTENT_MODE]), so the
 * hidden verdict stays suspended and promoted cell content returns to
 * [SELECT_CONTENT_MODE] rather than to the default mode.
 */
private const val LAYOUT_TABLE_IN_SELECT_MODE = "layoutTableInSelect"

/**
 * The mode every descendant of a `<select>` is transformed in.
 *
 * A `<select>` renders its option list in a popup that Blink keeps out of the
 * layout tree, so **nothing** below the `<select>` has a layout box — and the
 * capture, which can only report "no box, and no laid-out descendant", cannot
 * tell that apart from a real `display:none`
 * (`CapturePage.displayAnnotation()`). The artifact therefore belongs to the
 * *position* in the tree, not to a set of tag names: it hits the `<option>`s,
 * an `<optgroup>`, any wrapper between them (Chrome's parser keeps a `<div>`
 * inside a `<select>`, the customizable-select content model allows one, and a
 * JS-built DOM can nest anything), and an option's own inline markup alike.
 *
 * So in this mode the `display:none` drop is suspended and the meaningless
 * annotation is replaced by the UA default display of the tag (see
 * [passThrough], [uaDefaultDisplay]), while everything else still
 * applies: `aria-hidden` is an author signal and still hides (as does
 * `visibility:hidden`, resolved before any mode — see [resolveVisibility]),
 * [NEVER_RENDERED_TAGS] are still dropped, and a hidden `<select>` never
 * enters this mode at all — the hidden-subtree rule matches first and takes its
 * whole subtree with it.
 *
 * The mode also *ends* where the artifact does: an element the browser did lay
 * a box out for — anything but the popup's own verdict, an absent annotation
 * included — is judged in the default mode again, so a `display:none` inside
 * the rendered `<button>` face of a customizable select, or inside a laid-out
 * listbox `<option>`, hides as usual (see [hasNoLayoutBox]).
 *
 * The cost is that an option (or a wrapper) the author *deliberately* hid with
 * `display:none` now survives. That is unavoidable — the two are the same
 * annotation — and showing a stale option is the far cheaper error: honouring
 * the verdict dropped every option of every real page's `<select>`.
 */
private const val SELECT_CONTENT_MODE = "selectContent"

/**
 * [SELECT_CONTENT_MODE] for the direct children of an `<option>` in the
 * popup. A customizable select lays each option out as a flex container, and
 * a flex container blockifies its children: the flag `<img>` and the label
 * `<span>`, or the `★` and the plan name, are separate boxes, not one word.
 *
 * The option therefore reads as `flex` and these children carry **no**
 * display (see [uaDefaultDisplay]): `separateUnwrappedBlocks` blockifies an
 * unannotated child of a flex container already, while the whitespace
 * normalizer reads the child by its tag. Writing `block` on the child instead
 * would make the normalizer drop the whitespace beside an element that still
 * renders inline in Markdown (`<strong>PL</strong> Poland` → `**PL**Poland`,
 * and likewise next to an `<img>`). Their own descendants are back in
 * [SELECT_CONTENT_MODE].
 */
private const val OPTION_CONTENT_MODE = "optionContent"

/**
 * Tags that are never rendered content, so they are dropped regardless of the
 * display verdict — which they may well not carry at all in a hand-built or
 * non-Chrome-sourced stream. A `<script>` is a legal child of a `<select>`, and
 * leaking its source as text would be worse than losing it. (In the
 * HTML→Markdown pipeline `simplifyHtml` drops these anyway; this keeps the
 * operator correct when it is used on its own.)
 */
private val NEVER_RENDERED_TAGS = setOf("script", "style", "noscript", "template")

/**
 * Structural `<table>` descendant tags unwrapped along with their enclosing
 * layout table; a nested `<table>` is intentionally absent so it is
 * re-evaluated independently.
 */
private val TABLE_STRUCTURAL_TAGS = setOf(
    "thead", "tbody", "tfoot", "tr", "td", "th", "colgroup", "col", "caption"
)

/**
 * Computed accessibility roles a `<table>` reports when the browser keeps it a
 * *data* table; any other role means it was demoted to a layout table.
 */
private val DATA_TABLE_ROLES = setOf("table", "grid", "treegrid")

private fun SemanticEvent.Mark.isHidden(honourDisplayNone: Boolean = true): Boolean =
    this["aria-hidden"]?.equals("true", ignoreCase = true) == true
        || (honourDisplayNone && this[AccessibilityAnnotations.DISPLAY] == "none")

/**
 * Whether this element renders invisible: its own recorded `visibility`
 * verdict, or — for an element with no recorded box, which carries a verdict
 * only where it differs from its parent's — the one it inherits.
 */
private fun SemanticEvent.Mark.isInvisible(parentInvisible: Boolean): Boolean =
    if (name == "head") false
    else when (this[AccessibilityAnnotations.VISIBILITY]) {
        "hidden" -> true
        "visible" -> false
        // a laid-out box carries its own verdict; anything else inherits
        else -> parentInvisible && !hasOwnLayoutBox()
    }

/**
 * Whether the capture recorded a box laid out for this element, which then
 * carries a `visibility` verdict of its own — neither `contents` nor `none`,
 * and not absent either (never captured, or captured before every display
 * was recorded, when a `display: contents` was left out too).
 */
private fun SemanticEvent.Mark.hasOwnLayoutBox(): Boolean =
    when (this[AccessibilityAnnotations.DISPLAY]) {
        null, "contents", "none" -> false
        else -> true
    }

private fun SemanticEvent.Mark.isAxIgnoredImage(): Boolean =
    this[AccessibilityAnnotations.IGNORED] == "true"

/**
 * Whether the browser laid no box out for this element *and* none for any of
 * its descendants — the single verdict a `<select>`'s popup content produces,
 * and the only state in which the missing box is uninterpretable.
 *
 * Any other value is not this state — a laid-out `block` or `inline-block`,
 * and equally `contents` (no box of its own, but laid-out descendants) — and
 * neither is an **absent** annotation: the stream was never captured, or was
 * captured before the capture recorded every element's display (see
 * `CapturePage.displayAnnotation()`), when a plain `block` was left out.
 * Reading "no annotation" as "no layout box" would keep the popup exemption
 * alive across a laid-out listbox `<option>` or a plain-`block` `<button>`
 * face, and a genuine `display:none` below it would then leak into the
 * output.
 */
private fun SemanticEvent.Mark.hasNoLayoutBox(): Boolean =
    this[AccessibilityAnnotations.DISPLAY] == "none"

private fun SemanticEvent.Mark.isLayoutTable(): Boolean {
    val role = this[AccessibilityAnnotations.ROLE] ?: return false
    return role !in DATA_TABLE_ROLES
}

/**
 * Re-emits an element reached inside a `<select>`, deciding whether the popup
 * artifact still covers it.
 *
 * Only [hasNoLayoutBox] — the verdict the popup produces — keeps the exemption
 * alive: the annotation is meaningless there, so it is replaced by the tag's
 * UA default display ([uaDefaultDisplay]) and the subtree descends in
 * [SELECT_CONTENT_MODE]. Anything else means the browser did lay a
 * box out, so the artifact is over and the subtree is judged in the default
 * mode again, where `display:none` hides as usual — the rendered `<button>`
 * face of a customizable select, most of all, and equally a laid-out listbox
 * `<option>`.
 */
private suspend fun MatcherScope.passThroughSelectContent(
    event: SemanticEvent.Mark,
    blockified: Boolean = false
) {
    val insidePopup = event.hasNoLayoutBox()
    passThrough(
        event,
        childMode = when {
            !insidePopup -> null
            event.name == "option" -> OPTION_CONTENT_MODE
            else -> SELECT_CONTENT_MODE
        },
        insidePopup = insidePopup,
        blockified = blockified
    )
}

/**
 * Re-emits [event] verbatim, descending in [childMode]. The role / visibility
 * annotations this operator consumed are stripped, but [AccessibilityAnnotations.DISPLAY]
 * is deliberately preserved so the downstream operators (`separateUnwrappedBlocks`,
 * the whitespace normalizer) can gate on the browser's computed block/inline
 * verdict; the normalizer strips the annotation itself.
 *
 * The exception is a `none` on an element sitting in a `<select>`'s popup
 * ([insidePopup]), an artifact of the popup living outside the layout tree. It
 * is replaced by the display the UA stylesheet gives the tag
 * ([uaDefaultDisplay]), not merely dropped: in a captured stream an absent
 * display reads as "crosses no boundary" (a dump captured before every display
 * was recorded), so a customizable select's
 * `<option><div>Name</div><div>Desc</div></option>` merged into `NameDesc`
 * once `simplifyHtml` unwrapped the divs. [insidePopup] is set only for such
 * an element ([hasNoLayoutBox]); any other value is genuine (the rendered
 * `<button>` face of a customizable select) and survives. [blockified] says
 * the element is a flex item of a popup option (see [OPTION_CONTENT_MODE]).
 */
private suspend fun MatcherScope.passThrough(
    event: SemanticEvent.Mark,
    childMode: String? = null,
    insidePopup: Boolean = false,
    blockified: Boolean = false
) {
    val kept = event.attributes.filterKeys {
        it != AccessibilityAnnotations.ROLE &&
            it != AccessibilityAnnotations.VISIBILITY &&
            it != AccessibilityAnnotations.IGNORED
    }
    val attributes =
        if (insidePopup) {
            when (val display = event.uaDefaultDisplay(blockified)) {
                null -> kept - AccessibilityAnnotations.DISPLAY
                else -> kept + (AccessibilityAnnotations.DISPLAY to display)
            }
        } else kept
    if (event.isTagged) {
        tag(event.name, attributes) { children(childMode) }
    } else {
        event.name(attributes) { children(childMode) }
    }
}

/**
 * The display the UA stylesheet lays this element out with, read the same way
 * every downstream operator reads an element it has no captured display for
 * ([isInlineByUaDefault]) — `inline`, or `block` for any non-inline box
 * (`block`, `list-item`, the `table-*` family; the operators downstream tell
 * only inline from not). An `<option>` is `flex`, as a customizable select
 * lays it out, and a [blockified] child of one gets `null` — no annotation,
 * so its flex parent blockifies it (see [OPTION_CONTENT_MODE]). An element the
 * stylesheet never renders ([isNeverRenderedByUaDefault]) lays out no box, so
 * it reads as `contents`: no boundary to cross, for `separateUnwrappedBlocks`
 * and the whitespace normalizer alike.
 *
 * It stands in for a `<select>`'s popup content, whose captured `none` says
 * nothing about how the popup lays it out (see [SELECT_CONTENT_MODE]). The
 * real popup stylesheet is unknowable from the capture — the tag's default is
 * the one reading every operator can agree on, and it keeps a block `<div>`
 * separated from its sibling while an inline `<span>` or an unknown element
 * stays in the flow of its text.
 */
private fun SemanticEvent.Mark.uaDefaultDisplay(blockified: Boolean): String? = when {
    isNeverRenderedByUaDefault() -> "contents"
    blockified -> null
    name == "option" -> "flex"
    isInlineByUaDefault() -> "inline"
    else -> "block"
}
