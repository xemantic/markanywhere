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
 *   dropped together with its whole subtree.
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

    // hidden subtree: no children() → the whole subtree is skipped.
    match({ isHidden() }) { /* drop */ }

    // layout table: drop the mark, descend unwrapping the structural skeleton.
    match({ name == "table" && isLayoutTable() }) { children(mode = LAYOUT_TABLE_MODE) }

    // everything else flows through, annotations stripped.
    match("*") { event -> passThrough(event) }
    matchText { +it }

    // --- inside an unwrapped layout table -------------------------------

    match({ isHidden() }, mode = LAYOUT_TABLE_MODE) { /* drop */ }

    // a nested layout table keeps unwrapping; a nested data table falls through
    // to the wildcard below and is re-emitted (then re-evaluated in default mode).
    match({ name == "table" && isLayoutTable() }, mode = LAYOUT_TABLE_MODE) {
        children(mode = LAYOUT_TABLE_MODE)
    }

    // a cell's content is promoted, then a single space separates it from the
    // next cell so adjacent cells don't merge into one word once the table
    // skeleton is gone (real captures often carry no inter-cell whitespace).
    // Downstream `dropHtmlStructuralWhitespace` collapses/trims the extra space.
    match({ name == "td" || name == "th" }, mode = LAYOUT_TABLE_MODE) {
        children(mode = LAYOUT_TABLE_MODE)
        +" "
    }

    // the remaining structural descendants are unwrapped, staying in layout-table
    // mode so the skeleton keeps promoting; `<table>` is deliberately not in
    // this set.
    match({ name in TABLE_STRUCTURAL_TAGS }, mode = LAYOUT_TABLE_MODE) {
        children(mode = LAYOUT_TABLE_MODE)
    }

    // real cell content is re-emitted and its subtree descends in the default
    // mode (so nested tables / hidden subtrees are handled normally).
    match("*", mode = LAYOUT_TABLE_MODE) { event -> passThrough(event) }
    // inter-cell whitespace text is promoted into the surrounding flow.
    matchText(mode = LAYOUT_TABLE_MODE) { +it }
}

private const val LAYOUT_TABLE_MODE = "layoutTable"

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

private fun SemanticEvent.Mark.isHidden(): Boolean =
    this["aria-hidden"]?.equals("true", ignoreCase = true) == true
        || this[AccessibilityAnnotations.DISPLAY] == "none"
        || this[AccessibilityAnnotations.VISIBILITY] == "hidden"

private fun SemanticEvent.Mark.isLayoutTable(): Boolean {
    val role = this[AccessibilityAnnotations.ROLE] ?: return false
    return role !in DATA_TABLE_ROLES
}

/**
 * Re-emits [event] verbatim, descending in default mode. The role / visibility
 * annotations this operator consumed are stripped, but [AccessibilityAnnotations.DISPLAY]
 * is deliberately preserved so the downstream whitespace normalizer can gate on
 * the browser's computed block/inline verdict; it strips the annotation itself.
 */
private suspend fun MatcherScope.passThrough(event: SemanticEvent.Mark) {
    val attributes = event.attributes.filterKeys {
        it != AccessibilityAnnotations.ROLE && it != AccessibilityAnnotations.VISIBILITY
    }
    if (event.isTagged) {
        tag(event.name, attributes) { children() }
    } else {
        event.name(attributes) { children() }
    }
}