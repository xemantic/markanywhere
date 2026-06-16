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
import com.xemantic.markanywhere.transform.MarkSelect
import com.xemantic.markanywhere.transform.transformMatchingMarks
import kotlinx.coroutines.flow.Flow

/**
 * Injects a single separator space between the **inline-flattening** direct
 * children of a CSS **flex** / **grid** container (read from the browser's
 * computed [AccessibilityAnnotations.DISPLAY] verdict), so adjacent items that
 * share no source whitespace don't word-merge once the container is flattened.
 *
 * Runtime-rendered DOMs (React, …) routinely lay out a row of items as flex /
 * grid boxes with **no whitespace text** between them — the visual gap is the
 * `gap` property or an empty CSS-drawn divider element, not a text node. After
 * [simplifyHtml] unwraps the container and its `<span>` / `<div>` items, those
 * items collapse to bare adjacent text runs with nothing between, so the BBC
 * card metadata `<span>3 hrs ago</span><div class=sep></div><span>Europe</span>`
 * renders as `3 hrs agoEurope`. [dropHtmlStructuralWhitespace] only ever
 * *collapses existing* whitespace into a separator — it never *inserts* one —
 * and by the time it runs the flex container (and its `display:flex` annotation)
 * is already gone, so the separator has to be injected **here**, while the
 * container is still intact.
 *
 * Only children that **flatten to inline content** are separated. Each direct
 * child is classified from its subtree:
 * - **block** — contains a mark that survives simplification as a block element
 *   ([LINK_BLOCK_CONTENT_TAGS], e.g. a `<button>` or block-wrapping link); it
 *   already gets block separation, so it is emitted untouched and *breaks* the
 *   inline run (no separator spans it). This is what keeps a flex row of cards,
 *   or the header's `<a><button>Register</button></a>` items, from sprouting
 *   stray space lines.
 * - **empty** — no text and no replaced content (`<img>`); the typical
 *   CSS-drawn divider `<div>`. It is transparent: emitted as-is, neither
 *   separated around nor breaking the run.
 * - **inline** — everything else (text, `<img>`, inline phrasing). A separator
 *   is injected before it when the previous non-empty sibling was also inline.
 *
 * The injected event is a plain `" "` text — the same single-space convention
 * [applyAccessibility] uses between unwrapped layout-table cells; it merely
 * supplies the whitespace [dropHtmlStructuralWhitespace] was missing, which then
 * collapses any doubling against pre-existing source whitespace.
 *
 * Nested flex/grid containers are handled at every level: the outermost match is
 * buffered once (via [transformMatchingMarks]) and [injectFlexGridSeparators]
 * recurses through it, so a flex metadata strip inside a grid card is separated
 * even though only the grid is the outermost match.
 *
 * Run this *after* [applyAccessibility] (which preserves the DISPLAY annotation
 * through its pass-through) and *before* [simplifyHtml] (which unwraps the
 * container, discarding the annotation and the structure this relies on).
 */
public fun Flow<SemanticEvent>.separateFlexGridChildren(): Flow<SemanticEvent> =
    transformMatchingMarks(FLEX_GRID_CHILD_SEPARATOR)

/**
 * Selects the outermost flex/grid container subtree and re-emits it with
 * separators injected between its inline-flattening children, recursively. See
 * [separateFlexGridChildren].
 */
public val FLEX_GRID_CHILD_SEPARATOR: MarkSelect = select@{ mark ->
    if (!mark.isFlexOrGridContainer()) return@select null
    transform@{ events -> emit(injectFlexGridSeparators(events)) }
}

/**
 * Re-emits a balanced subtree (`mark … unmark`) with flex/grid child separators
 * injected at every level. Recurses into every element child first (so nested
 * flex/grid containers get their own separators), then, when the subtree's own
 * mark is a flex/grid container, weaves a `" "` between consecutive
 * inline-flattening children.
 */
internal fun injectFlexGridSeparators(events: List<SemanticEvent>): List<SemanticEvent> {
    val mark = events.first()
    val children = events.subList(1, events.size - 1).topLevelChildren()
    val processed = children.map { child ->
        if (child.first() is SemanticEvent.Mark) injectFlexGridSeparators(child) else child
    }
    val out = mutableListOf(events.first())
    if (mark is SemanticEvent.Mark && mark.isFlexOrGridContainer()) {
        var previousWasInline = false
        for (child in processed) {
            when (child.flatteningKind()) {
                FlatteningKind.EMPTY -> out += child
                FlatteningKind.BLOCK -> { out += child; previousWasInline = false }
                FlatteningKind.INLINE -> {
                    if (previousWasInline) out += SemanticEvent.Text(" ")
                    out += child
                    previousWasInline = true
                }
            }
        }
    } else {
        processed.forEach { out += it }
    }
    out += events.last()
    return out
}

/** How a flex/grid child renders once the container is flattened. */
private enum class FlatteningKind { EMPTY, INLINE, BLOCK }

private fun List<SemanticEvent>.flatteningKind(): FlatteningKind {
    var hasContent = false
    for (event in this) when (event) {
        is SemanticEvent.Mark ->
            if (event.name in LINK_BLOCK_CONTENT_TAGS) return FlatteningKind.BLOCK
            else if (event.name == "img") hasContent = true
        is SemanticEvent.Text -> if (!event.text.isHtmlBlank()) hasContent = true
        is SemanticEvent.Unmark -> {}
    }
    return if (hasContent) FlatteningKind.INLINE else FlatteningKind.EMPTY
}

/**
 * Splits a sibling sequence (the interior of a balanced subtree) into its
 * top-level children: each element child is the whole `mark … unmark` slice,
 * each direct text node is a singleton.
 */
private fun List<SemanticEvent>.topLevelChildren(): List<List<SemanticEvent>> {
    val children = mutableListOf<List<SemanticEvent>>()
    var depth = 0
    var start = 0
    forEachIndexed { i, event ->
        when (event) {
            is SemanticEvent.Mark -> {
                if (depth == 0) start = i
                depth++
            }
            is SemanticEvent.Unmark -> {
                depth--
                if (depth == 0) children += subList(start, i + 1)
            }
            is SemanticEvent.Text -> if (depth == 0) children += listOf(event)
        }
    }
    return children
}

// A flex / grid container (`flex`, `inline-flex`, `grid`, `inline-grid`) lays
// its children out as separate boxes; anything else (block flow, inline flow,
// table, …) is left to the existing whitespace rules.
private fun SemanticEvent.Mark.isFlexOrGridContainer(): Boolean {
    val display = this[AccessibilityAnnotations.DISPLAY] ?: return false
    return "flex" in display || "grid" in display
}
