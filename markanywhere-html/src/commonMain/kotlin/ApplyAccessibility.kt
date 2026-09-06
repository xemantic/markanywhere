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
import com.xemantic.markanywhere.transform.MatcherScope
import com.xemantic.markanywhere.transform.TransformerBuilder
import com.xemantic.markanywhere.transform.transform
import kotlinx.coroutines.flow.Flow

public fun Flow<SemanticEvent>.applyAccessibility(): Flow<SemanticEvent> = transform {
    applyAccessibility()
}

/**
 * Applies the browser's accessibility verdicts that a page dump recorded as
 * reserved annotations (see [AccessibilityAnnotations]) onto an otherwise
 * untouched, full-DOM event stream:
 *
 * - **Drop hidden subtrees** — an element with `aria-hidden="true"`,
 *   [AccessibilityAnnotations.DISPLAY] `= none` (`display:none`), or
 *   [AccessibilityAnnotations.VISIBILITY] `= hidden` (`visibility:hidden`) is
 *   dropped together with its whole subtree. Inside a `<select>` the
 *   `display:none` half of that rule is suspended — the popup lays out no box
 *   for any of its content, which the capture cannot tell apart from real
 *   hiding (see [SELECT_CONTENT_MODE]).
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
 * sees a tree already cleaned of hidden/layout noise). Registers its rules on
 * the receiving [TransformerBuilder]; use it inside a
 * [com.xemantic.markanywhere.transform.transform] block.
 */
public fun TransformerBuilder.applyAccessibility() {

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
    // is stripped.
    match("*", mode = SELECT_CONTENT_MODE) { event -> passThroughSelectContent(event) }
    matchText(mode = SELECT_CONTENT_MODE) { +it }
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
            // the select's own verdict is genuine unless it sits in a popup
            // itself (a <select> nested in a JS-built select subtree)
            insidePopup = insideSelect
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
 * annotation is stripped (see [passThrough]), while everything else still
 * applies: `aria-hidden` / `visibility:hidden` are author signals and still
 * hide, [NEVER_RENDERED_TAGS] are still dropped, and a hidden `<select>` never
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
        || this[AccessibilityAnnotations.VISIBILITY] == "hidden"

private fun SemanticEvent.Mark.isAxIgnoredImage(): Boolean =
    this[AccessibilityAnnotations.IGNORED] == "true"

/**
 * Whether the browser laid no box out for this element *and* none for any of
 * its descendants — the single verdict a `<select>`'s popup content produces,
 * and the only state in which the missing box is uninterpretable.
 *
 * An **absent** annotation is emphatically not this state: the capture omits
 * the annotation for a laid-out element whose computed display is the plain
 * `block` default (and for a not-laid-out `display:contents` element that does
 * have laid-out descendants) — see `CapturePage.displayAnnotation()`. Reading
 * "no annotation" as "no layout box" would keep the popup exemption alive
 * across a laid-out listbox `<option>` or a plain-`block` `<button>` face, and
 * a genuine `display:none` below it would then leak into the output.
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
 * alive: the annotation is meaningless there, so it is stripped and the subtree
 * descends in [SELECT_CONTENT_MODE]. Anything else means the browser did lay a
 * box out, so the artifact is over and the subtree is judged in the default
 * mode again, where `display:none` hides as usual — the rendered `<button>`
 * face of a customizable select, most of all, and equally a laid-out listbox
 * `<option>`.
 */
private suspend fun MatcherScope.passThroughSelectContent(
    event: SemanticEvent.Mark
) {
    val insidePopup = event.hasNoLayoutBox()
    passThrough(
        event,
        childMode = if (insidePopup) SELECT_CONTENT_MODE else null,
        insidePopup = insidePopup
    )
}

/**
 * Re-emits [event] verbatim, descending in [childMode]. The role / visibility
 * annotations this operator consumed are stripped, but [AccessibilityAnnotations.DISPLAY]
 * is deliberately preserved so the downstream whitespace normalizer can gate on
 * the browser's computed block/inline verdict; it strips the annotation itself.
 * The exception is a `none` on an element sitting in a `<select>`'s popup
 * ([insidePopup]), an artifact of the popup living outside the layout tree —
 * dropped here so the gating falls back to the tag-name heuristic instead of
 * reading it. Any other value is genuine (the rendered `<button>` face of a
 * customizable select) and survives.
 */
private suspend fun MatcherScope.passThrough(
    event: SemanticEvent.Mark,
    childMode: String? = null,
    insidePopup: Boolean = false
) {
    val attributes = event.attributes.filterKeys {
        it != AccessibilityAnnotations.ROLE &&
            it != AccessibilityAnnotations.VISIBILITY &&
            it != AccessibilityAnnotations.IGNORED
    }.filterNot {
        insidePopup &&
            it.key == AccessibilityAnnotations.DISPLAY && it.value == "none"
    }
    if (event.isTagged) {
        tag(event.name, attributes) { children(childMode) }
    } else {
        event.name(attributes) { children(childMode) }
    }
}
