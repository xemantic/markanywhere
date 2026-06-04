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
import com.xemantic.markanywhere.flow.SemanticEventScope
import com.xemantic.markanywhere.flow.semanticEvents
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.flow.Flow
import org.w3c.dom.Element
import org.w3c.dom.Text
import org.w3c.dom.asList

/**
 * Captures the DOM subtree rooted at this [Element] as a [Flow] of [SemanticEvent]s.
 *
 * @param respectAccessibility when `true`, the capture follows the accessibility
 *   tree rather than the raw DOM:
 *   - child subtrees the accessibility tree excludes — `aria-hidden="true"`,
 *     `display:none`, `visibility:hidden` — are skipped (the element and all its
 *     descendants emit no events; the root element itself is always emitted);
 *   - an element that names itself only via `aria-labelledby` (no own
 *     `aria-label`) gets the referenced elements' text resolved into a synthetic
 *     `aria-label`, since the id references cannot be dereferenced once the
 *     stream is serialized and other nodes are dropped or reordered downstream;
 *   - a `<table>` the browser classifies as a *layout table* (rather than a data
 *     table) is **unwrapped**: the `table`/`thead`/`tbody`/`tfoot`/`tr`/`td`/`th`/
 *     `colgroup`/`col`/`caption` structural tags emit no events and their content
 *     is promoted into the surrounding flow — mirroring the accessibility tree,
 *     where Chrome collapses layout tables to generic containers. The verdict is
 *     read from [Element.computedRole]: a data table reports `"table"` (kept
 *     intact), a layout table reports a non-table role (unwrapped). `computedRole`
 *     requires Chrome's `--enable-experimental-web-platform-features` launch flag;
 *     without it the property is `undefined` and tables are left intact (see
 *     [isLayoutTable]).
 *
 *   Defaults to `false` (raw 1:1 capture of the DOM attributes).
 */
public fun Element.toSemanticEvents(
    respectAccessibility: Boolean = false
): Flow<SemanticEvent> = semanticEvents(
    tagged = true
) {
    flowElement(
        element = this@toSemanticEvents,
        respectAccessibility = respectAccessibility,
        insideLayoutTable = false
    )
}

/**
 * Structural `<table>` descendant tags that carry no accessible semantics once
 * the enclosing table is classified as a layout table — they are unwrapped (their
 * content is promoted), while a nested `<table>` is intentionally absent so it is
 * re-evaluated independently by [flowElement].
 */
private val TABLE_STRUCTURAL_TAGS = setOf(
    "thead", "tbody", "tfoot", "tr", "td", "th", "colgroup", "col", "caption"
)

private suspend fun SemanticEventScope.flowElement(
    element: Element,
    respectAccessibility: Boolean,
    insideLayoutTable: Boolean
) {

    val tagName = element.localName

    if (respectAccessibility) {
        // A layout table (or a structural tag inside one) carries no accessible
        // semantics: drop the tag and promote its content into the surrounding flow.
        if (tagName == "table" && element.isLayoutTable()) {
            flowChildren(element, respectAccessibility, insideLayoutTable = true)
            return
        }
        if (insideLayoutTable && tagName in TABLE_STRUCTURAL_TAGS) {
            flowChildren(element, respectAccessibility, insideLayoutTable = true)
            return
        }
    }

    val attributes = element.attributes.asList().associate {
        it.name to it.value
    }.let { if (respectAccessibility) it.withResolvedAccessibleName() else it }

    tag(name = tagName, attributes) {
        flowChildren(element, respectAccessibility, insideLayoutTable = false)
    }

}

private suspend fun SemanticEventScope.flowChildren(
    element: Element,
    respectAccessibility: Boolean,
    insideLayoutTable: Boolean
) {
    element.childNodes.asList().forEach {
        when (it) {
            is Text -> +it.wholeText // unescaped, escaping done on render
            is Element -> if (!(respectAccessibility && it.isAccessibilityHidden())) {
                flowElement(it, respectAccessibility, insideLayoutTable)
            }
        }
    }
}

/**
 * Whether this [Element] (and therefore its subtree) is excluded from the
 * accessibility tree by an explicit `aria-hidden` or by computed style.
 *
 * Note: `getComputedStyle` only reflects layout for elements connected to a
 * rendered document, so this is meaningful for live-DOM capture, not detached
 * nodes.
 */
private fun Element.isAccessibilityHidden(): Boolean {
    if (getAttribute("aria-hidden") == "true") return true
    val style = window.getComputedStyle(this)
    return style.display == "none" || style.visibility == "hidden"
}

/**
 * ARIA roles a `<table>` reports through [Element.computedRole] when the browser
 * keeps it a *data* table; any other (or empty) role means it was collapsed to a
 * layout table.
 */
private val DATA_TABLE_ROLES = setOf("table", "grid", "treegrid")

/**
 * Whether the browser's accessibility engine classifies this `<table>` as a
 * *layout* table (to be unwrapped) rather than a data table.
 *
 * The verdict is Chrome's own — read from [Element.computedRole] — so we do not
 * reimplement the brittle, font/viewport-sensitive `IsDataTable()` heuristic. A
 * data table reports a role in [DATA_TABLE_ROLES]; a layout table reports a
 * non-table role (typically `""`/`"none"`/`"generic"`).
 *
 * `computedRole` is gated behind Chrome's `--enable-experimental-web-platform-features`
 * launch flag. When the flag is absent the property is `undefined` ([computedRole]
 * returns `null`) and this returns `false` — i.e. we fall back to leaving the
 * table intact rather than guessing.
 */
private fun Element.isLayoutTable(): Boolean {
    val role = computedRole ?: return false
    return role !in DATA_TABLE_ROLES
}

/**
 * Chrome's computed accessibility role for this element, or `null` when the
 * `Element.computedRole` API is unavailable (the property is `undefined` unless
 * the browser runs with `--enable-experimental-web-platform-features`). An empty
 * string — a present-but-roleless node — is preserved as `""`, distinct from the
 * unavailable case.
 */
private val Element.computedRole: String?
    get() {
        val role = asDynamic().computedRole
        return if (jsTypeOf(role) == "string") role.unsafeCast<String>() else null
    }

/**
 * If this attribute map names its element only via `aria-labelledby`, resolves
 * the referenced elements' text into a synthetic `aria-label`. A map that
 * already carries an explicit `aria-label`, or no `aria-labelledby`, or whose
 * references resolve to nothing, is returned unchanged.
 */
private fun Map<String, String>.withResolvedAccessibleName(): Map<String, String> {
    if ("aria-label" in this) return this
    val ids = this["aria-labelledby"] ?: return this
    val name = ids.trim()
        .split(Regex("\\s+"))
        .mapNotNull { id -> document.getElementById(id)?.textContent?.trim()?.ifEmpty { null } }
        .joinToString(" ")
    return if (name.isEmpty()) this else this + ("aria-label" to name)
}
