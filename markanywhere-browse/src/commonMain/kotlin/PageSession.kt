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

import com.xemantic.markanywhere.browse.PageSession.Companion.REF_ATTRIBUTE
import com.xemantic.markanywhere.dump.SemanticEventDump
import dev.kdriver.cdp.domain.Accessibility
import dev.kdriver.cdp.domain.dom
import dev.kdriver.core.dom.DefaultElement
import dev.kdriver.core.dom.Element
import dev.kdriver.core.tab.Tab
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * A stateful handle over a live [Tab] that bridges the serialized, LLM-facing
 * [dump] and the page it describes: every [dump] stamps a short, dense ref
 * ([REF_ATTRIBUTE]) on each *actionable* element (a focusable control / link,
 * or one whose accessibility role is interactive), and remembers `ref →
 * backendNodeId` so an LLM that read the dump can name an element by its ref and
 * have the session resolve it back to a live [Element] to act on — `click`,
 * `sendKeys`, etc.
 *
 * The refs are **regenerated on every [dump]** (the DOM changes as you interact,
 * which invalidates the underlying node identities), so a ref is only valid
 * until the next capture — [element] throws on a ref it does not currently know.
 *
 * It keeps state (the ref registry), so it is a class rather than a stateless
 * function; it does not own the [Tab] (the caller manages the browser /
 * navigation lifecycle).
 */
public class PageSession(
    private val tab: Tab
) {

    private var refs: Map<String, Int> = emptyMap()

    /**
     * Captures the page as a [SemanticEventDump] (full DOM, accessibility
     * verdicts annotated — see [capturePage]) and, additionally, a
     * [REF_ATTRIBUTE] on every actionable element. Refreshes this session's
     * ref registry, so it must be called before [element].
     */
    public suspend fun dump(): SemanticEventDump {
        val capture = tab.capturePage(
            refAttribute = REF_ATTRIBUTE,
            isActionable = ::isActionable
        )
        refs = capture.refs
        return capture.dump
    }

    /**
     * Resolves the [REF_ATTRIBUTE] value [ref] from the most recent [dump] back
     * to a live, actionable [Element]. Rehydration goes through the captured
     * `backendNodeId` (stable for the node's lifetime) via CDP `DOM.describeNode`
     * — no DOM mutation, the same path kdriver's own selectors take.
     *
     * @throws NoSuchElementException if [ref] is not in the current capture
     *   (e.g. the page changed and [dump] has not been re-run).
     */
    public suspend fun element(ref: String): Element {
        val backendNodeId = refs[ref] ?: throw NoSuchElementException(
            "no actionable element with ref '$ref' in the current capture — re-run dump()"
        )
        val node = tab.dom.describeNode(backendNodeId = backendNodeId).node
        return DefaultElement(tab, node, node)
    }

    public companion object {
        /** Attribute carrying an actionable element's short ref in the dump. */
        public const val REF_ATTRIBUTE: String = "data-markanywhere-ref"
    }

}

/**
 * Whether the browser's accessibility tree considers this node something a user
 * (or an agent) can act on: a present, non-ignored node that is focusable or
 * carries an interactive role. Reading Blink's verdict — rather than matching a
 * tag allowlist — also catches scripted controls like `<div role="button"
 * tabindex="0">` that a tag list would miss.
 */
private fun isActionable(ax: Accessibility.AXNode?): Boolean =
    ax != null && !ax.ignored &&
        (ax.hasTrueProperty(Accessibility.AXPropertyName.FOCUSABLE) ||
            ax.roleName in ACTIONABLE_ROLES)

private val ACTIONABLE_ROLES = setOf(
    "link", "button", "textbox", "searchbox", "checkbox", "radio", "switch",
    "combobox", "listbox", "option", "menuitem", "menuitemcheckbox",
    "menuitemradio", "tab", "slider", "spinbutton",
)

private fun Accessibility.AXNode.hasTrueProperty(
    name: Accessibility.AXPropertyName
): Boolean = properties?.any {
    it.name == name && (it.value.value as? JsonPrimitive)?.booleanOrNull == true
} == true
