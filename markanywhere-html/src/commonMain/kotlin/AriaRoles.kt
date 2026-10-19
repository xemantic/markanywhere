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

import com.xemantic.markanywhere.SemanticEvent.Mark
import com.xemantic.markanywhere.html.spec.asciiLowercase
import com.xemantic.markanywhere.html.spec.isHtmlWhitespace

/**
 * The role an author gave this element with the `role` attribute, ASCII
 * lowercased, or `null` when it has none.
 *
 * `role` is a token list separated by HTML whitespace, whose first token a
 * browser recognises wins — the later ones are fallbacks for older readers.
 * The first token is what every page in practice relies on, so it is read
 * without a table of the roles a browser knows.
 *
 * Every operator deciding by role reads it here, so `simplifyHtml` promoting a
 * `<div>` and `labelActionableElements` judging it a control cannot read one
 * attribute two ways.
 */
internal fun Mark.explicitRole(): String? {
    val role = this["role"] ?: return null
    val start = role.indexOfFirst { !it.isHtmlWhitespace() }
    if (start < 0) return null
    var end = start
    while (end < role.length && !role[end].isHtmlWhitespace()) end++
    return role.substring(start, end).asciiLowercase()
}

/**
 * Whether the author removed this element's semantics with an explicit
 * `presentation` / `none` role (the two are synonyms) that the browser honours.
 *
 * ARIA's presentational role conflict resolution ignores the role on an
 * element that is focusable or carries a global ARIA attribute — the element
 * keeps its implicit role then (a `<button role="presentation">` is still a
 * button), see [undoesPresentationalRole].
 *
 * Never `generic`: that is the role of every `div` and `span`, and an author
 * writing it on a semantic element says nothing a page relies on.
 */
internal fun Mark.hasPresentationalRole(): Boolean =
    explicitRole() in PRESENTATIONAL_ROLES && !undoesPresentationalRole()

/**
 * Whether ARIA's presentational role conflict resolution keeps this element's
 * implicit role despite a `presentation` / `none` one: the element is
 * focusable, or carries a global ARIA state or property.
 *
 * Focusable is read from the markup, as HTML defines it: a natively focusable
 * element (a link, a form control, `<summary>`, a frame, …), a valid
 * `tabindex` (`-1` included), or an editing host. The capture's actionable
 * verdict (`AccessibilityAnnotations.REF`) is not read here: `simplifyHtml`
 * keeps a ref-bearing element through rules of its own.
 */
internal fun Mark.undoesPresentationalRole(): Boolean =
    isNativelyFocusable()
        || this["tabindex"]?.isHtmlInteger() == true
        || isEditingHost()
        || attributes.keys.any { it in GLOBAL_ARIA_ATTRIBUTES }

internal val PRESENTATIONAL_ROLES = setOf("presentation", "none")

private fun Mark.isNativelyFocusable(): Boolean = when (name) {
    "a", "area" -> "href" in attributes
    "input" -> !this["type"].equals("hidden", ignoreCase = true)
    "audio", "video" -> "controls" in attributes
    else -> name in NATIVELY_FOCUSABLE_TAGS
}

private val NATIVELY_FOCUSABLE_TAGS = setOf(
    "button", "select", "textarea", "summary", "iframe", "frame", "embed", "object"
)

/**
 * Whether `contenteditable` makes this element an editing host: only the
 * empty string, `true` and `plaintext-only` do (ASCII case-insensitively) —
 * `false` and any invalid value are not an editing host of their own.
 */
private fun Mark.isEditingHost(): Boolean =
    this["contenteditable"]?.asciiLowercase() in EDITING_HOST_VALUES

private val EDITING_HOST_VALUES = setOf("", "true", "plaintext-only")

/**
 * Whether the HTML rules for parsing integers read a number here: leading
 * whitespace skipped, an optional sign, then at least one digit — whatever
 * follows is ignored, so `"1.5"` is `1`. A `tabindex` only matters as valid
 * or not, so the value itself is never needed.
 */
private fun String.isHtmlInteger(): Boolean {
    var index = indexOfFirst { !it.isHtmlWhitespace() }
    if (index < 0) return false
    if (this[index] == '-' || this[index] == '+') index++
    return index < length && this[index] in '0'..'9'
}

/**
 * The global ARIA states and properties (WAI-ARIA 1.2 §6.4, plus
 * `aria-description`, `aria-braillelabel` and `aria-brailleroledescription`
 * from 1.3), any of which undoes a presentational role.
 */
private val GLOBAL_ARIA_ATTRIBUTES = setOf(
    "aria-atomic", "aria-braillelabel", "aria-brailleroledescription",
    "aria-busy", "aria-controls", "aria-current",
    "aria-describedby", "aria-description", "aria-details", "aria-disabled",
    "aria-dropeffect", "aria-errormessage", "aria-flowto", "aria-grabbed",
    "aria-haspopup", "aria-hidden", "aria-invalid", "aria-keyshortcuts",
    "aria-label", "aria-labelledby", "aria-live", "aria-owns",
    "aria-relevant", "aria-roledescription",
)
