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
 * Replaces an inline `<svg>` graphic that carries an accessible name with a
 * synthetic `img` mark (`alt` = the accessible name, no `src`) **before**
 * [simplifyHtml] discards the `svg` subtree wholesale.
 *
 * Pages often render logos, masthead wordmarks, and meaningful glyphs as inline
 * `<svg role="img" aria-label="…">` (or an `<svg>` with a `<title>` child)
 * rather than an `<img>` — accessibility-wise these are images, announced by a
 * screen reader as "*name*, image". [simplifyHtml] keeps none of the SVG tags
 * (`svg`/`title`/`path`/…), so without this step the graphic vanishes and an
 * enclosing `<h1>`/`<a>` collapses to an empty `#` / `[]( )`. This operator
 * turns it into the `![name]()` an LLM reads directly, carrying the only signal
 * it can use — the accessible name as `alt`.
 *
 * The destination is left **empty**: an inline SVG has no fetchable URL, and
 * serialising the vector path into a `data:` URI would re-inject exactly the
 * noise the pipeline exists to strip. The accessible name is resolved like the
 * a11y accessible-name algorithm, limited to what a single stream can see:
 * `aria-label`, else the text of the SVG's `<title>` child. `aria-labelledby`
 * is an id reference into the rest of the document, so it is not resolvable here
 * and is ignored.
 *
 * An `<svg>` with **no** accessible name is purely decorative; it emits nothing
 * (the same outcome [simplifyHtml] would produce by dropping it), so icon-only
 * SVGs and the inner glyphs of `aria-label`-bearing controls don't turn into
 * empty `![]()`.
 *
 * Run this in the HTML→Markdown pipeline *after* [applyAccessibility] (so an
 * `aria-hidden` SVG is already gone) and *before* [simplifyHtml] (so the
 * synthetic `img` survives simplification while the raw `svg` subtree is still
 * dropped). The emitted `img` mirrors the source's [SemanticEvent.Mark.isTagged]
 * and carries over the SVG's `id` and [AccessibilityAnnotations.DISPLAY]
 * annotation when present, so downstream attribute and whitespace handling stays
 * correct.
 */
public fun Flow<SemanticEvent>.resolveInlineGraphics(): Flow<SemanticEvent> =
    transformMatchingMarks(INLINE_GRAPHIC_RESOLVER)

/**
 * Selects an inline `<svg>` subtree and, when it carries an accessible name,
 * replaces it with a void `img` mark. See [resolveInlineGraphics].
 */
public val INLINE_GRAPHIC_RESOLVER: MarkSelect = select@{ svg ->
    if (svg.name != "svg") return@select null
    transform@{ events ->
        val name = events.accessibleName() ?: return@transform // decorative — drop
        val attributes = buildMap {
            put("alt", name)
            svg["id"]?.let { put("id", it) }
            svg[AccessibilityAnnotations.DISPLAY]?.let {
                put(AccessibilityAnnotations.DISPLAY, it)
            }
        }
        mark("img", isTagged = svg.isTagged, attributes = attributes)
        unmark("img", isTagged = svg.isTagged)
    }
}

/**
 * Resolves the accessible name of an `<svg>` subtree from its buffered events:
 * the opening mark's `aria-label`, else the text of its first `<title>` child.
 * Returns `null` (decorative graphic) when neither yields a non-blank name.
 */
private fun List<SemanticEvent>.accessibleName(): String? {
    (first() as SemanticEvent.Mark)["aria-label"]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }
    return titleText()?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * Concatenates the text of the first `<title>` element in this subtree, or
 * `null` when there is none / it is empty. Nested marks inside the title are
 * unwrapped (their text still counts); depth bookkeeping stops at the title's
 * own closing unmark.
 */
private fun List<SemanticEvent>.titleText(): String? {
    var capturing = false
    var depth = 0
    val text = StringBuilder()
    for (event in this) {
        when (event) {
            is Mark ->
                if (!capturing) { if (event.name == "title") capturing = true }
                else depth++
            is Text -> if (capturing) text.append(event.text)
            is Unmark -> if (capturing) {
                if (depth == 0) break // the title's own closer
                else depth--
            }
        }
    }
    return text.toString().ifEmpty { null }
}
