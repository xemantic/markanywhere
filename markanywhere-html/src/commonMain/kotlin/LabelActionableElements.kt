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
import com.xemantic.markanywhere.SemanticEvent.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * Gives an actionable element that would otherwise render with **no label** one
 * taken from its accessible name, so a link or button never reaches the output
 * as a bare `[](…)`.
 *
 * A control whose only content is an icon is everywhere on the modern web, and
 * the pieces that carry its name — a `title` on an inner `<div>`, an
 * `aria-label`, an icon font's class — are exactly the ones [simplifyHtml]
 * strips. Hacker News renders thirty upvote arrows as
 * `<a href="vote?…"><div class="votearrow" title="upvote"></div></a>`: without
 * this operator every one of them becomes an actionable ref with nothing saying
 * what it does.
 *
 * For each [ACTIONABLE_TAGS] element the operator holds the subtree only until
 * the question is settled: the first child that would render as a label releases
 * everything held so far and the rest of the control streams through untouched
 * (for the common text-labelled link that is the first text node). Only a
 * control that shows no label all the way to its close is held whole — bounded:
 * one control at a time, the same pre-pass shape [encodeActionableRefs] uses for
 * links. For that control the operator inserts a label as the element's first
 * child, in this order:
 *
 * 1. the element's own `aria-label`, then its own `title`;
 * 2. the first `aria-label` / `title` on any descendant (the Hacker News shape);
 * 3. failing both, [GRAPHIC_PLACEHOLDER_ALT] as an `img` — but only when the
 *    subtree actually holds a graphic *that is about to vanish*, i.e. under
 *    [SvgMode.RESOLVE]; under [SvgMode.PRESERVE] the graphic survives and
 *    speaks for itself. Clicking a picture means something even
 *    when nothing names it, while a control holding no graphic and no name has
 *    nothing to report, and inventing a label would be worse than leaving the
 *    empty link visible for what it is.
 *
 * "Would render as a label" means non-blank text, an `img` with a non-blank
 * `alt`, or an `<svg>` carrying an accessible name — the last because
 * [resolveInlineGraphics] turns exactly those into a labelled `img` downstream.
 * A control that already has one is emitted verbatim: the BBC follow button
 * (`<button aria-label="Follow BBC on x"><svg/></button>`) keeps its own label
 * and its decorative icon still vanishes, and the many links that pair a text
 * label with a decorative chevron never sprout a placeholder.
 *
 * Runs **after** [resolveIcons] and [applyAccessibility] (so an icon font has
 * already become emoji text and hidden subtrees are gone — neither should be
 * read as a label) and **before** [resolveInlineGraphics] (which drops a
 * nameless `<svg>`, the very evidence rule 3 needs) and before [simplifyHtml]
 * (which strips the `title` rule 2 reads).
 */
public fun Flow<SemanticEvent>.labelActionableElements(
    svgMode: SvgMode = SvgMode.RESOLVE,
): Flow<SemanticEvent> = flow {
    val buffer = mutableListOf<SemanticEvent>()
    var depth = 0 // nesting depth inside the current control; 0 = outside any
    var labelled = false // the control has shown a label: nothing left to hold
    collect { event ->
        if (depth == 0) {
            if (event is Mark && event.name in ACTIONABLE_TAGS) {
                buffer.add(event)
                depth = 1
                labelled = false
            } else {
                emit(event)
            }
            return@collect
        }
        when (event) {
            is Mark -> depth++
            is Unmark -> depth--
            else -> { /* text does not nest */ }
        }
        when {
            labelled -> emit(event)
            event.rendersALabel() -> {
                labelled = true
                buffer.forEach { emit(it) }
                buffer.clear()
                emit(event)
            }
            else -> {
                buffer.add(event)
                if (depth == 0) {
                    emitUnlabelled(buffer, svgMode)
                    buffer.clear()
                }
            }
        }
    }
    // An unclosed control at end-of-stream (synthetic edge): flush what we have.
    if (buffer.isNotEmpty()) emitUnlabelled(buffer, svgMode)
}

/** Elements whose whole point is to be activated by the reader. */
private val ACTIONABLE_TAGS = setOf("a", "button", "summary")

/**
 * The `alt` given to a nameless graphic inside an unlabelled control, following
 * the `:name:` convention [resolveIcons] uses for a recognised icon with no
 * emoji mapping — a label that reads as "a graphic was here" rather than
 * pretending to name it.
 */
public const val GRAPHIC_PLACEHOLDER_ALT: String = ":svg:"

// Attributes holding an accessible name, in the order they are consulted.
private val NAME_ATTRIBUTES = listOf("aria-label", "title")

/**
 * Emits a control none of whose held events [rendersALabel] — the buffer is
 * only ever handed over here on that condition — inserting one where a name or
 * a vanishing graphic gives us something to say.
 */
private suspend fun FlowCollector<SemanticEvent>.emitUnlabelled(
    buffer: List<SemanticEvent>,
    svgMode: SvgMode,
) {
    val open = buffer.first() as Mark
    val name = open.accessibleName() ?: buffer.descendantName()
    when {
        name != null -> {
            emit(open)
            emit(Text(name))
            buffer.drop(1).forEach { emit(it) }
            return
        }
        svgMode == SvgMode.RESOLVE && buffer.holdsAVanishingGraphic() -> {
            emit(open)
            emit(Mark("img", isTagged = open.isTagged,
                attributes = mapOf("alt" to GRAPHIC_PLACEHOLDER_ALT)))
            emit(Unmark("img", isTagged = open.isTagged))
            buffer.drop(1).forEach { emit(it) }
            return
        }
    }
    buffer.forEach { emit(it) }
}

/** The first non-blank [NAME_ATTRIBUTES] value on this mark, if any. */
private fun Mark.accessibleName(): String? = NAME_ATTRIBUTES
    .firstNotNullOfOrNull { this[it]?.trim()?.takeIf { name -> name.isNotEmpty() } }

/** The first non-blank accessible name on any descendant of the control. */
private fun List<SemanticEvent>.descendantName(): String? = asSequence()
    .drop(1)
    .filterIsInstance<Mark>()
    .firstNotNullOfOrNull { it.accessibleName() }

/**
 * Whether this event, inside a control, reaches the output as the control's
 * label: non-blank text, an `img` with a non-blank `alt`, or an `<svg>` whose
 * own accessible name [resolveInlineGraphics] will turn into one downstream.
 */
private fun SemanticEvent.rendersALabel(): Boolean = when (this) {
    // Ordinary content, or the text of an `<svg><title>` — that one names its
    // graphic, which resolveInlineGraphics turns into a labelled `img`
    // downstream, so either way a label reaches the output and the control
    // needs nothing from us.
    is Text -> !text.isHtmlBlank()
    is Mark -> when (name) {
        "img" -> !(this["alt"] ?: "").isHtmlBlank()
        "svg" -> accessibleName() != null
        else -> false
    }
    else -> false
}

/**
 * Whether the control holds a graphic that will **vanish** from the output — a
 * nameless `<svg>`, which [resolveInlineGraphics] drops as decorative.
 *
 * Deliberately not `img`: an unnamed `<img>` survives as `![](src)`, which
 * already tells the reader a picture is there, so standing a placeholder beside
 * it (`![:svg:]()![](y18.svg)`, the Hacker News masthead) would say it twice.
 */
private fun List<SemanticEvent>.holdsAVanishingGraphic(): Boolean = asSequence()
    .drop(1)
    .any { it is Mark && it.name == "svg" }
