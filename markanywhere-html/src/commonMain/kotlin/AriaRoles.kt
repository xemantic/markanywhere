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
import com.xemantic.markanywhere.dump.AccessibilityAnnotations
import com.xemantic.markanywhere.html.spec.asciiLowercase
import com.xemantic.markanywhere.html.spec.isHtmlWhitespace

/**
 * The role an author gave this element with the `role` attribute, ASCII
 * lowercased, or `null` when it has none a browser recognises.
 *
 * `role` is a token list separated by HTML whitespace, whose first token a
 * browser recognises wins (WAI-ARIA 1.2 §7.1) — the later ones are fallbacks
 * for older readers. A token naming no concrete role ([ARIA_ROLES]) is
 * skipped, so `role="foo heading"` is a heading, and a list of nothing but
 * such tokens is no role at all: an item whose `role="bogus"` is dropped
 * inherits its list's presentational role like an item with none.
 *
 * Every operator deciding by role reads it here, so `simplifyHtml` promoting a
 * `<div>` and `labelActionableElements` judging it a control cannot read one
 * attribute two ways.
 */
internal fun Mark.explicitRole(): String? {
    val role = this["role"] ?: return null
    var index = 0
    while (index < role.length) {
        while (index < role.length && role[index].isHtmlWhitespace()) index++
        val start = index
        while (index < role.length && !role[index].isHtmlWhitespace()) index++
        if (index > start) {
            val token = role.substring(start, index).asciiLowercase()
            if (token in ARIA_ROLES) return token
        }
    }
    return null
}

/**
 * The concrete roles Chrome recognises in a `role` token list: WAI-ARIA 1.2
 * with the ARIA 1.3 additions, the Digital Publishing (`doc-*`) and Graphics
 * (`graphics-*`) modules. The abstract roles (`landmark`, `section`, `widget`,
 * …) are deliberately absent — an author must not use them, and a browser
 * skips them like an unknown token. Verified live against Chrome's computed
 * role of `<table role="…">`.
 */
private val ARIA_ROLES = setOf(
    // WAI-ARIA 1.2
    "alert", "alertdialog", "application", "article", "banner", "blockquote",
    "button", "caption", "cell", "checkbox", "code", "columnheader", "combobox",
    "complementary", "contentinfo", "definition", "deletion", "dialog",
    "directory", "document", "emphasis", "feed", "figure", "form", "generic",
    "grid", "gridcell", "group", "heading", "img", "insertion", "link", "list",
    "listbox", "listitem", "log", "main", "marquee", "math", "menu", "menubar",
    "menuitem", "menuitemcheckbox", "menuitemradio", "meter", "navigation",
    "none", "note", "option", "paragraph", "presentation", "progressbar",
    "radio", "radiogroup", "region", "row", "rowgroup", "rowheader",
    "scrollbar", "search", "searchbox", "separator", "slider", "spinbutton",
    "status", "strong", "subscript", "superscript", "switch", "tab", "table",
    "tablist", "tabpanel", "term", "textbox", "time", "timer", "toolbar",
    "tooltip", "tree", "treegrid", "treeitem",
    // WAI-ARIA 1.3
    "comment", "image", "mark", "sectionfooter", "sectionheader", "suggestion",
    // Graphics ARIA
    "graphics-document", "graphics-object", "graphics-symbol",
    // DPUB-ARIA 1.1
    "doc-abstract", "doc-acknowledgments", "doc-afterword", "doc-appendix",
    "doc-backlink", "doc-biblioentry", "doc-bibliography", "doc-biblioref",
    "doc-chapter", "doc-colophon", "doc-conclusion", "doc-cover", "doc-credit",
    "doc-credits", "doc-dedication", "doc-endnote", "doc-endnotes",
    "doc-epigraph", "doc-epilogue", "doc-errata", "doc-example", "doc-footnote",
    "doc-foreword", "doc-glossary", "doc-glossref", "doc-index",
    "doc-introduction", "doc-noteref", "doc-notice", "doc-pagebreak",
    "doc-pagefooter", "doc-pageheader", "doc-pagelist", "doc-part",
    "doc-preface", "doc-prologue", "doc-pullquote", "doc-qna", "doc-subtitle",
    "doc-tip", "doc-toc",
)

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
 * `tabindex` (`-1` included), or an editing host — and from the capture's
 * [AccessibilityAnnotations.SCROLLABLE], since a browser makes a scroll
 * container focusable too (Chrome keeps a scrolling `<ul role="presentation">`
 * a list), which no markup and no ref says. The capture's actionable verdict
 * (`AccessibilityAnnotations.REF`) is not read here: `simplifyHtml` keeps a
 * ref-bearing element through rules of its own.
 */
internal fun Mark.undoesPresentationalRole(): Boolean =
    isNativelyFocusable()
        || this["tabindex"]?.isHtmlInteger() == true
        || isEditingHost()
        || this[AccessibilityAnnotations.SCROLLABLE] == "true"
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
