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
import com.xemantic.markanywhere.dump.AccessibilityAnnotations
import com.xemantic.markanywhere.html.spec.HTML_VOID_ELEMENTS
import com.xemantic.markanywhere.html.spec.isHtmlBlank
import kotlinx.coroutines.flow.Flow
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
 * A *control* is decided by what the element is, not by its tag name (see
 * [isControl]): a link, button or `<summary>`; anything with an interactive
 * ARIA role that takes its name from its content (`option`, `menuitem`, `tab`,
 * `switch`, …); and anything the capture found actionable — it carries an
 * [AccessibilityAnnotations.REF] — unless its content is not where its name
 * goes (a void element, a field whose content is its value, a container of
 * options or rows). A combobox suggestion `<li role="option">` whose visible
 * text is `aria-hidden` and whose only name is `aria-label` would otherwise
 * reach the output empty, and an empty list item vanishes with its ref. An
 * `<a>` without `href` is a placeholder, not a link, and counts only when it
 * carries a ref kept under [refMode].
 *
 * For each control the operator holds the subtree only until
 * the question is settled: the first child that would render as a label releases
 * everything held so far and the rest of the control streams through untouched
 * (for the common text-labelled link that is the first text node). Only a
 * control that shows no label all the way to its close is held whole — bounded:
 * one control's subtree at a time, the same pre-pass shape [encodeActionableRefs]
 * uses for links. For that control the operator inserts a label as the element's
 * first child, in this order:
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
 * `alt`, or an `<svg>` carrying an `aria-label` or a `<title>` child — exactly
 * the names [resolveInlineGraphics] turns into a labelled `img` downstream. An
 * svg named only by a `title` *attribute* does not count: that name is read by
 * rule 2 and inserted here, since downstream it would vanish with the graphic.
 * A control that already has one is emitted verbatim: the BBC follow button
 * (`<button aria-label="Follow BBC on x"><svg/></button>`) keeps its own label
 * and its decorative icon still vanishes, and the many links that pair a text
 * label with a decorative chevron never sprout a placeholder.
 *
 * Controls nest (a card link holding an icon-only button, a `<summary>` with a
 * button in it), and each is judged on its own: an inner control is held and
 * labelled by the same rules, and the label inserted into it is content of the
 * enclosing control too, which therefore needs none of its own.
 *
 * Runs **after** [resolveIcons] and [applyAccessibility] (so an icon font has
 * already become emoji text and hidden subtrees are gone — neither should be
 * read as a label) and **before** [resolveInlineGraphics] (which drops a
 * nameless `<svg>`, the very evidence rule 3 needs) and before [simplifyHtml]
 * (which strips the `title` rule 2 reads).
 */
public fun Flow<SemanticEvent>.labelActionableElements(
    svgMode: SvgMode = SvgMode.RESOLVE,
    refMode: RefMode = RefMode.ENCODE,
): Flow<SemanticEvent> = flow {
    // One frame per open control, innermost last. An event arriving inside a
    // control is offered to the innermost frame; a frame that has shown its
    // label passes events on to the frame below (or downstream when it is the
    // outermost), one that has not holds them until it is settled.
    val frames = ArrayDeque<Control>()

    suspend fun pass(event: SemanticEvent, level: Int) {
        if (level < 0) {
            emit(event)
            return
        }
        val frame = frames[level]
        when {
            frame.labelled -> pass(event, level - 1)
            event.rendersALabel() -> {
                frame.labelled = true
                val held = frame.buffer.toList()
                frame.buffer.clear()
                held.forEach { pass(it, level - 1) }
                pass(event, level - 1)
            }
            else -> frame.buffer.add(event)
        }
    }

    collect { event ->
        if (event is Mark && event.isControl(refMode)) {
            frames.addLast(Control().also { it.buffer.add(event) })
            return@collect
        }
        val top = frames.lastOrNull()
        if (top == null) {
            emit(event)
            return@collect
        }
        when (event) {
            is Mark -> top.depth++
            is Unmark -> top.depth--
            else -> { /* text does not nest */ }
        }
        if (top.depth > 0) {
            pass(event, frames.lastIndex)
            return@collect
        }
        // The control's own close settles it.
        frames.removeLast()
        if (top.labelled) {
            pass(event, frames.lastIndex)
        } else {
            top.buffer.add(event)
            labelled(top.buffer, svgMode).forEach { pass(it, frames.lastIndex) }
        }
    }
    // Controls unclosed at end-of-stream (synthetic edge): flush what we have,
    // innermost first so its events reach the enclosing frame's buffer.
    while (frames.isNotEmpty()) {
        val top = frames.removeLast()
        val held = if (top.labelled) top.buffer else labelled(top.buffer, svgMode)
        held.forEach { pass(it, frames.lastIndex) }
    }
}

/** An open actionable element and what it has held back so far. */
private class Control {
    val buffer = mutableListOf<SemanticEvent>()
    var depth = 1 // nesting depth inside the control, 1 = its own open mark
    var labelled = false // the control has shown a label: nothing left to hold
}

/** Elements whose whole point is to be activated by the reader. */
private val ACTIONABLE_TAGS = setOf("a", "button", "summary")

/**
 * The interactive ARIA roles whose accessible name is computed from their
 * content (WAI-ARIA 1.2 "name from: contents"): for these, writing the name as
 * content is what a screen reader would read anyway. An element carrying one
 * is a control whatever its tag.
 */
private val INTERACTIVE_NAME_FROM_CONTENT_ROLES = setOf(
    "button", "link", "option", "menuitem", "menuitemcheckbox", "menuitemradio",
    "tab", "checkbox", "radio", "switch", "treeitem", "gridcell",
)

/**
 * The roles a ref-bearing element may carry and still be named from its
 * content: the interactive ones, the non-interactive "name from: contents"
 * roles, and the roles that say nothing about the element at all.
 */
private val CONTENT_NAMED_ROLES = INTERACTIVE_NAME_FROM_CONTENT_ROLES + setOf(
    "cell", "columnheader", "rowheader", "row", "heading", "tooltip",
    "generic", "presentation", "none",
)

/**
 * Elements whose content is not where their name goes, even when the capture
 * found them actionable — mostly because their implicit role is not named from
 * content: a field's content is its value; a container's content is its
 * options, items or rows; a landmark, section, form or dialog *holds* the
 * page's controls (a focusable `<main tabindex="-1">` carries a ref, and the
 * name of the search box inside it must not be written into it); an embedded
 * document or graphic is its own thing. A name written there would read as a
 * value or an item.
 */
private val NOT_CONTENT_NAMED_TAGS = setOf(
    "select", "textarea", "optgroup", "datalist",
    "ul", "ol", "menu", "dl",
    "table", "thead", "tbody", "tfoot", "tr", "colgroup",
    "html", "body", "main", "nav", "section", "article", "aside",
    "header", "footer", "search", "address", "hgroup", "blockquote",
    "form", "fieldset", "dialog", "details", "figure",
    "iframe", "frame", "object", "video", "audio", "canvas", "picture",
    "svg", "math", "template",
)

/**
 * Whether this element is a control this operator labels — see the operator's
 * KDoc. An explicit `role` is read here; an element without one is judged by
 * its tag, whose implicit role [NOT_CONTENT_NAMED_TAGS] and
 * [HTML_VOID_ELEMENTS] exclude where it is not named from content. The tag
 * wins over an explicit role too: a name written into a `<select>` or a
 * `<main>` is wrong whatever role the author gave it.
 *
 * An `<a>` without `href` is a placeholder, not a link, and [simplifyHtml]
 * unwraps it whatever its role (or keeps a named-anchor target as a plain tag) — a label inserted into it would be left behind
 * as stray text, or as a `![:svg:]()` pointing at nothing — unless the capture
 * found it actionable anyway (a scripted control carrying a ref), which keeps
 * it a link — under [RefMode.ENCODE] only: [RefMode.STRIP] drops the ref, and
 * simplify then unwraps the placeholder like any other.
 */
internal fun Mark.isControl(refMode: RefMode): Boolean {
    if (name == "a" && !isHyperlink()) {
        return refMode == RefMode.ENCODE && AccessibilityAnnotations.REF in attributes
    }
    if (name in ACTIONABLE_TAGS) return true
    if (!mayHoldItsName()) return false
    val role = explicitRole()
    return role in INTERACTIVE_NAME_FROM_CONTENT_ROLES ||
        (this[AccessibilityAnnotations.REF] != null && (role == null || role in CONTENT_NAMED_ROLES))
}

/**
 * Whether this element *says* it is a control — a link, button or `<summary>`,
 * or an interactive [INTERACTIVE_NAME_FROM_CONTENT_ROLES] role — as opposed to
 * one the capture merely found actionable ([isControl] also counts a bare
 * ref-bearing generic, which may be a fragment of a word: `US` + `$5`).
 */
internal fun Mark.isDeclaredControl(): Boolean =
    name in ACTIONABLE_TAGS || (mayHoldItsName() && explicitRole() in INTERACTIVE_NAME_FROM_CONTENT_ROLES)

private fun Mark.mayHoldItsName(): Boolean =
    name !in HTML_VOID_ELEMENTS && name !in NOT_CONTENT_NAMED_TAGS && !isEditable()

/** Whether this element is a rich-text editor, whose content is its value. */
private fun Mark.isEditable(): Boolean =
    this["contenteditable"]?.trim()?.lowercase()?.let { it != "false" } == true

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
 * A control none of whose held events [rendersALabel] — the buffer is only ever
 * handed over here on that condition — with a label inserted where a name or a
 * vanishing graphic gives us something to say.
 */
private fun labelled(
    buffer: List<SemanticEvent>,
    svgMode: SvgMode,
): List<SemanticEvent> {
    val open = buffer.first() as Mark
    val name = open.accessibleName() ?: buffer.descendantName()
    val label = when {
        name != null -> listOf(Text(name))
        svgMode == SvgMode.RESOLVE && buffer.holdsAVanishingGraphic() -> listOf(
            Mark("img", isTagged = open.isTagged,
                attributes = mapOf("alt" to GRAPHIC_PLACEHOLDER_ALT)),
            Unmark("img", isTagged = open.isTagged),
        )
        else -> return buffer
    }
    return listOf(open) + label + buffer.drop(1)
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
 * `aria-label` [resolveInlineGraphics] will turn into one downstream.
 */
private fun SemanticEvent.rendersALabel(): Boolean = when (this) {
    // Ordinary content, or the text of an `<svg><title>` — that one names its
    // graphic, which resolveInlineGraphics turns into a labelled `img`
    // downstream, so either way a label reaches the output and the control
    // needs nothing from us.
    is Text -> !text.isHtmlBlank()
    is Mark -> when (name) {
        "img" -> !(this["alt"] ?: "").isHtmlBlank()
        // Only what resolveInlineGraphics honours: a `title` attribute names
        // the graphic to nobody once it is dropped, so it is left for rule 2.
        "svg" -> this["aria-label"]?.trim()?.isNotEmpty() == true
        else -> false
    }
    else -> false
}

/**
 * Whether the control holds a graphic that will **vanish** from the output — a
 * nameless `<svg>`, which [resolveInlineGraphics] drops as decorative, or an
 * unnamed `<img>` left without a usable `src` (an inline `data:` icon), which
 * [resolveLazyImages] drops — asked of the same [LazyImageResolver].
 *
 * Not an unnamed `<img>` with a real `src`: it survives as `![](src)`, which
 * already tells the reader a picture is there, so standing a placeholder beside
 * it (`![:svg:]()![](y18.svg)`, the Hacker News masthead) would say it twice.
 */
private fun List<SemanticEvent>.holdsAVanishingGraphic(): Boolean {
    val images = LazyImageResolver()
    return asSequence()
        .drop(1)
        .any { images.resolve(it) == null || (it is Mark && it.name == "svg") }
}
