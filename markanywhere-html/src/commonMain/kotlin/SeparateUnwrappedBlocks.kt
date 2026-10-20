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
import com.xemantic.markanywhere.html.spec.isHtmlBlank
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Keeps the boundaries of the block boxes [simplifyHtml] unwraps (a
 * `<div>`/`<span>`/… that is a separate visual box — CSS `block`/`flex`/
 * `grid`/`table`/… — but which carries no Markdown meaning of its own):
 * content stacked one box **below** another is broken into paragraphs, and
 * content of boxes laid out **beside** each other is separated by a space.
 *
 * ## Paragraph breaks
 *
 * `<div>A</div><div>B</div>` is two lines on screen, but once [simplifyHtml]
 * unwraps the divs nothing tells `A` from `B` (issue #109). Between two inline
 * content tokens (text / `<img>`) the line breaks when the **shallowest** box
 * boundary crossed on the way from one to the other is a *stacked* box — a
 * block-level box in block flow (its parent is not a flex/grid container) —
 * and not a *side-by-side* one: a flex/grid item or an `inline-block`-like
 * atomic inline box. Only the shallowest boundary decides, the one at the
 * level where the two contents part: the first item of a flex row is below
 * the headline above the row, while two items of the row are beside each
 * other even when each holds stacked blocks of its own (the BBC card
 * metadata `3 hrs ago` / `Europe` stays on one line). A flex row is assumed
 * to be a row — `flex-direction` is not captured.
 *
 * At a break the content after it is wrapped in a `p` — anchored at the
 * outermost stacked box entered on the way to it (or the common parent, for
 * loose content after a box), so a whole flex row below a headline becomes
 * one paragraph — which ends where that anchor closes or the next stacked box
 * or surviving block starts in it (directly, or behind `display: contents` or
 * inline wrappers opened since the last content), and a paragraph opened
 * around a stacked box ends where that box closes. The content *before* the first break of a
 * container stays loose: the renderer separates loose text from a following
 * paragraph by a blank line. Wrapping is the only way to say "paragraph" in
 * the event model: it needs no renderer convention, and an HTML rendering of
 * the stream shows the same structure.
 *
 * A paragraph opens only where Markdown can hold one: never inside a
 * heading, paragraph, list container, table, link, control (anything that
 * renders as a label), a ref-bearing element, an inline element
 * [simplifyHtml] keeps (`strong`, `abbr`, … — even one displayed as a
 * block), inline-level content or a preserve region (`<pre>`/`<code>`/
 * `<textarea>`). When the stacked box itself is such an element (a nav or
 * card link displayed as a block, a control), the paragraph opens *around*
 * it, in the nearest ancestor that can hold one; below that, inside it, the
 * separator space applies, as before. A document root (`html`) holds
 * paragraphs again, so a same-origin frame's document nested in its inline
 * frame element breaks like the page's own. The cells of a CSS table
 * (`table-cell`) count as side by side, its rows as stacked.
 *
 * The run after a break is held until the paragraph ends — where its anchor
 * closes or the next stacked box or block starts directly in the anchor —
 * because only then is it known that no block (a block-wrapping `<a>` card
 * with a heading inside) sits within it: a paragraph around a block is not
 * expressible, so such a run is released unwrapped as soon as a block
 * appears. The bound is therefore the anchor's content up to the next box or
 * block in it, not one paragraph of text: an anchor holding only inline
 * content and side-by-side boxes is held whole — in a dump captured before
 * `block` was recorded, where the plain block boxes are not annotated, that
 * can be a large part of a container (up to `body`). The events between a
 * stacked box boundary and the next content token (markup only) are held
 * too, since the paragraph opens before them.
 *
 * ## Separator spaces
 *
 * Runtime-rendered DOMs (React, …) lay rows of items out as flex/grid/block
 * boxes with **no whitespace text** between them — the visual gap is `gap:`, an
 * empty CSS-drawn divider, or just separate boxes. Once [simplifyHtml] unwraps
 * those `<div>`/`<span>` wrappers their content abuts directly, so
 * `<span>3 hrs ago</span><div class=sep></div><span>Europe</span>` becomes
 * `3 hrs agoEurope`, and an image box next to a badge box (`![img](…)LIVE`)
 * merges too. [dropHtmlStructuralWhitespace] only *collapses existing*
 * whitespace into a separator — it never *inserts* one — and by the time it runs
 * the boxes (and their `data-markanywhere-display` annotations) are gone, so the
 * `" "` has to be injected here while the boxes are still intact (the same
 * single-space convention `applyAccessibility` uses between unwrapped
 * layout-table cells).
 *
 * Between two inline content tokens not separated by a paragraph a separator
 * is injected **iff**, since the previous token, we crossed:
 * - a **separated box** — a block-level box ([AccessibilityAnnotations.DISPLAY]
 *   neither `inline*` nor `contents`, read through [CapturedDisplayReader]:
 *   an absent one is the UA default of the tag, or — in an unmarked dump
 *   captured before every display was recorded — none) that is *not* a
 *   surviving block, i.e. one whose
 *   boundary vanishes when [simplifyHtml] unwraps it (`<div>`, blockified
 *   `<span>`, the empty divider `<div>`); **and**
 * - **no surviving block** — nothing in [LINK_BLOCK_CONTENT_TAGS] (`<p>`,
 *   `<section>`, `<h2>`, `<button>`, …) which already renders with its own block
 *   separation, so two tokens split by one need no help (this is what stops a
 *   stray space between the header's `<a><button>Register</button></a>` items).
 *
 * Adjacent **inline** elements (`<b>bold</b><i>italic</i>`) cross no separated
 * box, so they are never split — with one exception: two **adjacent controls**
 * (`<b><a>Hacker News</a></b><a>new</a>`, the gap a CSS margin; two icon
 * buttons built out of `<div role="button">`s, named by
 * [labelActionableElements]) get a separator *before* the second control,
 * outside its label. Controls are distinct things to act on (see
 * [isDeclaredControl] — only a declared one: a bare ref-bearing `<span>` may
 * be half a word, `US` + `$5`), and nothing renders two of them flush against
 * each other on purpose — they would read as one. Unless the capture shows
 * both in the inline text flow (a recorded `inline`/`contents` display) and
 * neither is a link (`<a>` or `role=link`), though: such controls may be one
 * word a page made a tap target per syllable
 * (`<span role=button>Hel</span><span role=button>lo</span>`, #141). Links
 * are always separated — a per-syllable `<a>` is split, the price of the
 * masthead links whose gap is a CSS margin.
 *
 * A separator goes *before* the marks opened since the previous token (`<b>`,
 * `<em>`, an inline `<a>`, a blockified `<strong>`, the box itself …), never
 * inside them: the whitespace normalizer drops a space that directly follows
 * an opening tag, so `[x](u)<b><a>y</a></b>` would otherwise still render
 * `[x](u)**[y](v)**` (issues #96, #148). Those marks are held only until the
 * next event decides — a run of opening tags, never content. The first text
 * of a preserve region (`<code>`, …) is a token like any other for this
 * decision, its own text left verbatim. Whitespace-only text passes through untouched —
 * the downstream collapser already turns it into the separator, and any doubling
 * with an injected space collapses there.
 *
 * Run this *after* [applyAccessibility] (which preserves the DISPLAY annotation
 * through its pass-through) and *before* [simplifyHtml] (which unwraps the boxes,
 * discarding the annotation and the structure this relies on).
 */
public fun Flow<SemanticEvent>.separateUnwrappedBlocks(): Flow<SemanticEvent> = flow {

    val stack = ArrayDeque<BlockBoundaryFrame>()
    var preserveDepth = 0 // inside <pre>/<code>/<textarea>, where whitespace is significant

    // Boundaries crossed since the last inline content token:
    var crossedSeparatedBox = false
    var crossedSurvivingBlock = false
    var seenInlineToken = false // suppresses a leading separator at stream start
    var previousEndedWithSpace = false // last token already ended in whitespace
    // The open controls, innermost last, and the control (null = none)
    // holding the last inline content token.
    val openControls = ArrayDeque<OpenControl>()
    var previousTokenControl: OpenControl? = null
    // What each element's display is, the same reading every operator makes.
    // An old unmarked dump left the `block` of its `<html>`, `<body>` and
    // leading wrappers out, so those still read as their tag's UA default —
    // a `<div>`'s `block`, wrong only for a leading `display: contents` one.
    val displays = CapturedDisplayReader()

    // The path since the last inline content token, by depth (a frame's
    // level is its depth in the stack, 1-based): the shallowest depth it
    // reached, and the shallowest stacked / side-by-side box boundary crossed.
    var turnDepth = 0
    var stackedLevel = Int.MAX_VALUE
    var sideBySideLevel = Int.MAX_VALUE

    // The held events (null while streaming), each with the depth after it.
    var held: MutableList<HeldEvent>? = null
    var heldFromDepth = 0 // the depth before the first held event
    var paragraphDepth = -1 // the anchor depth of the held run, -1 before it is decided
    var paragraphStart = 0 // the index of the first held event inside the paragraph
    var omittedSeparator = -1 // where a separator space was left out for the paragraph
    // The opening marks since the last token, each with the depth after it:
    // always the tail of the stream, behind the held run, so a separator (and
    // a paragraph) can still be placed before them.
    val heldMarks = mutableListOf<HeldEvent>()
    // The open marks of an element with the `img` role, itself included.
    var imageRoleDepth = 0

    // A tie goes to the stacked box: a block after an inline-block at the same
    // level still starts a line of its own.
    fun breaksLine() = stackedLevel != Int.MAX_VALUE && stackedLevel <= sideBySideLevel

    suspend fun out(event: SemanticEvent, depthAfter: Int = stack.size) {
        val buffer = held
        if (buffer != null) buffer += HeldEvent(event, depthAfter) else emit(event)
    }

    // Writes the opening marks held since the last token behind the held run.
    suspend fun releaseHeldMarks() {
        heldMarks.forEach { out(it.event, it.depthAfter) }
        heldMarks.clear()
    }

    suspend fun release(wrap: Boolean) {
        val buffer = held ?: return
        held = null
        for ((index, heldEvent) in buffer.withIndex()) {
            if (wrap && index == paragraphStart) emit(PARAGRAPH_MARK)
            if (!wrap && index == omittedSeparator) emit(SemanticEvent.Text(" "))
            emit(heldEvent.event)
        }
        // Left out right before the token, which is not held yet.
        if (!wrap && omittedSeparator == buffer.size) emit(SemanticEvent.Text(" "))
        if (wrap) emit(PARAGRAPH_UNMARK)
        paragraphDepth = -1
        omittedSeparator = -1
    }

    // Starts holding once the path from the last token breaks the line, as
    // the paragraph may have to open before the markup that follows.
    fun holdIfLineBreaks() {
        if (held == null && seenInlineToken && preserveDepth == 0 &&
            !crossedSurvivingBlock && breaksLine()
        ) {
            held = mutableListOf()
            // The marks held since the last token precede the run.
            heldFromDepth = stack.size - heldMarks.size
        }
    }

    fun crossBox(boundary: BlockBoundary, level: Int) {
        when (boundary) {
            STACKED_BOX -> stackedLevel = minOf(stackedLevel, level)
            SIDE_BY_SIDE_BOX, INLINE_BOX -> sideBySideLevel = minOf(sideBySideLevel, level)
            SURVIVING_BLOCK, INLINE -> { /* no box edge for the line */ }
        }
    }

    // The depth the paragraph is anchored at: the outermost stacked box
    // entered on the way down to the token, before any side-by-side box, or
    // the depth where the path turned.
    fun anchorDepth(): Int {
        var depth = turnDepth
        for (level in turnDepth + 1..stack.size) {
            when (stack[level - 1].boundary) {
                SIDE_BY_SIDE_BOX, INLINE_BOX -> break
                STACKED_BOX -> { depth = level; break }
                SURVIVING_BLOCK, INLINE -> { /* keep descending */ }
            }
        }
        return depth
    }

    fun allowsParagraph(depth: Int) = depth == 0 || stack[depth - 1].allowsParagraph

    // Whether a box opening now starts directly in the held paragraph's
    // anchor: right inside it, or behind wrappers laying out no box of their
    // own (`display: contents`, inline) opened since the last token, before
    // which the paragraph can still end (issue #154). A wrapper already
    // holding part of the paragraph cannot be crossed by its end.
    fun opensDirectlyInParagraph(): Boolean =
        stack.size == paragraphDepth || (
            stack.size - heldMarks.size <= paragraphDepth &&
                (paragraphDepth + 1..stack.size).all { stack[it - 1].boundary == INLINE }
            )

    fun cross(boundary: BlockBoundary) {
        when (boundary) {
            SURVIVING_BLOCK -> crossedSurvivingBlock = true
            STACKED_BOX, SIDE_BY_SIDE_BOX -> crossedSeparatedBox = true
            INLINE_BOX, INLINE -> { /* do nothing */ }
        }
    }

    suspend fun emitInlineToken(token: SemanticEvent, depthAfter: Int) {
        // An <img> token carries no leading/trailing text to separate around.
        val text = (token as? SemanticEvent.Text)?.text
        // `isWhitespace()` (unlike `isHtmlBlank`) also covers NBSP / Unicode
        // spaces, so existing non-breaking separation isn't doubled.
        val startsWithSpace = text?.firstOrNull()?.isWhitespace() == true
        val separate = seenInlineToken && crossedSeparatedBox && !crossedSurvivingBlock &&
            !previousEndedWithSpace && !startsWithSpace
        val buffer = held
        if (buffer != null && paragraphDepth < 0) {
            // The marks held since the last token join the run, so the
            // paragraph opens before them when they are inside its anchor.
            val marksStart = buffer.size
            for (mark in heldMarks) buffer += mark
            heldMarks.clear()
            // A box that cannot hold a paragraph (a link, a control, a kept
            // formatting element displayed as a block) gets one around it.
            var depth = anchorDepth()
            while (depth > turnDepth && !allowsParagraph(depth)) depth--
            val start = buffer.indexOfLast { it.depthAfter <= depth } + 1
            val startDepth = if (start == 0) heldFromDepth else buffer[start - 1].depthAfter
            val holdsBlock = (depth + 1..stack.size).any { stack[it - 1].rendersAsBlock }
            // A paragraph is the separation; the space, before the held
            // marks, is restored if the run turns out to be one a paragraph
            // cannot wrap.
            if (separate) omittedSeparator = marksStart
            if (breaksLine() && !crossedSurvivingBlock && !holdsBlock &&
                startDepth == depth && allowsParagraph(depth)
            ) {
                paragraphDepth = depth
                paragraphStart = start
            } else {
                release(wrap = false)
            }
        } else {
            if (separate) out(SemanticEvent.Text(" "), stack.size - heldMarks.size)
            releaseHeldMarks()
        }
        out(token, depthAfter)
        crossedSeparatedBox = false
        crossedSurvivingBlock = false
        seenInlineToken = true
        previousEndedWithSpace = text?.lastOrNull()?.isWhitespace() == true
        previousTokenControl = openControls.lastOrNull()
        turnDepth = stack.size
        stackedLevel = Int.MAX_VALUE
        sideBySideLevel = Int.MAX_VALUE
    }

    // Separates a control opening right after the content of another, closed
    // one, unless both are inline text flow (per-token tap targets, #141).
    suspend fun separateAdjacentControl(control: OpenControl) {
        val previous = previousTokenControl ?: return
        if (previous !in openControls && (previous.distinct || control.distinct) &&
            !crossedSurvivingBlock && !previousEndedWithSpace
        ) {
            out(SemanticEvent.Text(" "), stack.size - heldMarks.size)
            previousEndedWithSpace = true
            crossedSeparatedBox = false
        }
    }

    collect { event ->
        // The children of an element with the `img` role describe the image
        // simplifyHtml makes of it and are dropped there for its name, so
        // they pass through as no content of their own (#169).
        if (imageRoleDepth > 0 && !(event is Unmark && imageRoleDepth == 1)) {
            when (event) {
                is Mark -> {
                    displays.open(event)
                    stack.addLast(IMAGE_DESCRIPTION_FRAME)
                    imageRoleDepth++
                }
                is Unmark -> {
                    displays.close()
                    stack.removeLastOrNull()
                    imageRoleDepth--
                }
                else -> {}
            }
            out(event)
            return@collect
        }
        when (event) {
            is Mark -> {
                val parent = stack.lastOrNull()
                val parentIsFlexGrid = parent?.isFlexGridContainer == true
                val display = displays.open(event)
                val boundary = event.blockBoundary(parentIsFlexGrid, display)
                // The held paragraph ends at a block starting directly in its
                // anchor, and cannot hold one starting deeper inside its run.
                if (paragraphDepth >= 0) {
                    val block = boundary == STACKED_BOX || event.rendersAsBlock()
                    if (block && opensDirectlyInParagraph()) release(wrap = true)
                    else if (event.rendersAsBlock()) release(wrap = false)
                }
                val control = if (event.isDeclaredControl()) OpenControl(
                    // A link is a distinct thing to follow wherever it sits; any
                    // other control unless the capture shows it in the inline
                    // text flow — an unrecorded or UA-default display says
                    // nothing about that, so it keeps the separator.
                    distinct = event.name == "a" || event.explicitRole() == "link" ||
                        !display.isCapturedInlineFlow()
                ) else null
                if (preserveDepth == 0) {
                    cross(boundary)
                    crossBox(boundary, stack.size + 1)
                    // Held from before this mark, so a paragraph can open
                    // around the box when the box itself cannot hold one.
                    holdIfLineBreaks()
                    if (control != null) separateAdjacentControl(control)
                }
                val opensPreserve = event.isPreserveRegion()
                stack.addLast(
                    BlockBoundaryFrame(
                        boundary = boundary,
                        opensPreserve = opensPreserve,
                        // `display: contents` lays out no box, so its children
                        // are laid out by its parent's display.
                        isFlexGridContainer = if (display == CapturedDisplay.Known("contents")) parentIsFlexGrid
                        else display.isFlexGridContainer(),
                        control = control != null,
                        rendersAsBlock = event.rendersAsBlock(),
                        // A document root (a same-origin frame's, nested in
                        // its inline frame element) lays its content out on
                        // its own, like the page's (issue #154) — unless the
                        // frame sits in an element holding only phrasing.
                        allowsParagraph = (
                            if (event.name == "html") parent?.insidePhrasingHost != true
                            else parent?.allowsParagraph ?: true
                            ) && event.mayHoldParagraph(display),
                        // The frame element itself is no such element: it
                        // renders as a block-level tag around its document.
                        insidePhrasingHost = parent?.insidePhrasingHost == true ||
                            (event.name !in FRAME_TAGS && event.holdsOnlyPhrasing())
                    )
                )
                // An image is a content token; so is a generic element with the
                // `img` role, which simplifyHtml turns into one (#169).
                if (preserveDepth == 0 && (event.name == "img" || event.isImageRole())) {
                    emitInlineToken(event, stack.size)
                    if (event.isImageRole()) imageRoleDepth = 1
                } else {
                    heldMarks += HeldEvent(event, stack.size)
                }
                if (control != null) openControls.addLast(control)
                if (opensPreserve) preserveDepth++
            }
            is Unmark -> {
                imageRoleDepth = 0
                displays.close()
                releaseHeldMarks()
                if (paragraphDepth >= 0 && stack.size == paragraphDepth) release(wrap = true)
                val level = stack.size
                val frame = stack.removeLastOrNull()
                if (frame?.opensPreserve == true) preserveDepth--
                if (frame?.control == true) openControls.removeLastOrNull()
                if (preserveDepth == 0) {
                    cross(frame?.boundary ?: BlockBoundary.INLINE)
                    crossBox(frame?.boundary ?: BlockBoundary.INLINE, level)
                    turnDepth = minOf(turnDepth, stack.size)
                }
                out(event)
                // A stacked box directly in the anchor is one the paragraph
                // opened around (a box starting there inside the paragraph
                // ends it), and what follows it is below it (issue #154).
                if (paragraphDepth >= 0 && frame?.boundary == STACKED_BOX && stack.size == paragraphDepth) {
                    release(wrap = true)
                }
                if (preserveDepth == 0) holdIfLineBreaks()
            }
            is Text -> when {
                // Verbatim text: no separator is injected inside the region,
                // as nothing is crossed there once its first token is seen.
                preserveDepth > 0 && event.text.isNotEmpty() -> emitInlineToken(event, stack.size)
                preserveDepth > 0 || event.text.isHtmlBlank() -> {
                    releaseHeldMarks()
                    out(event)
                }
                else -> emitInlineToken(event, stack.size)
            }
        }
        // Holding before the token stops as soon as no paragraph can follow,
        // so a surviving block (a whole `<pre>`) is never held.
        if (held != null && paragraphDepth < 0 && (crossedSurvivingBlock || !breaksLine())) {
            release(wrap = false)
        }
    }
    releaseHeldMarks()
    release(wrap = paragraphDepth >= 0 && stack.size <= paragraphDepth)
}

private val PARAGRAPH_MARK = SemanticEvent.Mark("p", isTagged = true)
private val PARAGRAPH_UNMARK = SemanticEvent.Unmark("p", isTagged = true)

// The frame of an element inside an `img` role's description: no box edge,
// no paragraph.
private val IMAGE_DESCRIPTION_FRAME = BlockBoundaryFrame(
    boundary = BlockBoundary.INLINE,
    opensPreserve = false,
    isFlexGridContainer = false,
    control = false,
    rendersAsBlock = false,
    allowsParagraph = false,
    insidePhrasingHost = true,
)

private class HeldEvent(val event: SemanticEvent, val depthAfter: Int)

/**
 * An open declared control, compared by identity. A [distinct] one is
 * separated from an adjacent control: a link, or a control that is a box of
 * its own — two controls in the inline text flow may be the halves of one
 * word (`<span role=button>Hel</span><span role=button>lo</span>`, #141).
 */
private class OpenControl(val distinct: Boolean)

/** How crossing an element's boundary bears on separating adjacent inline content. */
private enum class BlockBoundary {
    /** Kept by [simplifyHtml] as a block, so it separates on its own. */
    SURVIVING_BLOCK,
    /** A block-level box in block flow: what follows it is below it. */
    STACKED_BOX,
    /** A flex / grid item: laid out beside its siblings. */
    SIDE_BY_SIDE_BOX,
    /** An atomic inline box (`inline-block`, …): beside its siblings, flush. */
    INLINE_BOX,
    /** No box edge at all (inline, `contents`, never rendered). */
    INLINE,
}

private class BlockBoundaryFrame(
    val boundary: BlockBoundary,
    val opensPreserve: Boolean,
    val isFlexGridContainer: Boolean,
    val control: Boolean,
    val rendersAsBlock: Boolean,
    val allowsParagraph: Boolean,
    val insidePhrasingHost: Boolean,
)

private fun SemanticEvent.Mark.blockBoundary(
    parentIsFlexGrid: Boolean,
    display: CapturedDisplay
): BlockBoundary = when {
    // A surviving block ([simplifyHtml] keeps it) already gives block separation.
    name in LINK_BLOCK_CONTENT_TAGS -> BlockBoundary.SURVIVING_BLOCK
    // An <img> is replaced *content*, not a wrapper: it is an inline token and
    // separation comes from the boxes around it, never from the image itself —
    // so a lazy-load placeholder <img> next to its real sibling <img> (two views
    // of one image) is not split, even though the placeholder has no display
    // annotation and would otherwise read as a block box.
    name == "img" -> BlockBoundary.INLINE
    else -> when (display) {
        // The direct children of a flex/grid container are blockified whatever
        // their tag.
        !is Known if parentIsFlexGrid -> BlockBoundary.SIDE_BY_SIDE_BOX
        // An absent annotation in an unmarked dump captured before every
        // display was recorded, when both a plain `block` and a
        // `display: contents` were left out — no boundary can be told apart,
        // so none is crossed (a `contents` wrapper split `US$5`).
        Unrecorded -> BlockBoundary.INLINE
        // Not recorded: the UA stylesheet's default is assumed, read the same
        // way the whitespace normalizer reads it — a `<div>` is a block (the
        // `<div>` per line a rich-text editor writes, `Hi,<div>there</div>`),
        // while a hand-built stream's `<a>`/`<strong>`/`<i>` must not over-split.
        // An element the UA stylesheet never renders (`<script>`, `<style>`, …)
        // lays out no box, so like `contents` it has no boundary to cross.
        UaDefault -> when {
            isInlineLevel(name) || isNeverRenderedByUaDefault() -> BlockBoundary.INLINE
            else -> BlockBoundary.STACKED_BOX
        }
        is Known -> when (val value = display.value) {
            // `display: contents` lays out no box of its own, so it has no
            // boundary to cross — its children's display is what separates.
            "contents" -> BlockBoundary.INLINE
            "inline" -> BlockBoundary.INLINE
            // The cells of a CSS table sit beside each other; its rows stack.
            "table-cell" -> BlockBoundary.SIDE_BY_SIDE_BOX
            // A block-level box ([simplifyHtml] unwraps `<div>`/`<span>`): its
            // boundary vanishes on flatten. An atomic inline box (`inline-block`,
            // `inline-flex`, …) stays in the inline flow, beside its siblings.
            else -> when {
                value.startsWith("inline") -> BlockBoundary.INLINE_BOX
                parentIsFlexGrid -> BlockBoundary.SIDE_BY_SIDE_BOX
                else -> BlockBoundary.STACKED_BOX
            }
        }
    }
}

/**
 * Whether a paragraph may open directly inside this element, as far as the
 * element itself is concerned (its ancestors must allow one too).
 */
private fun SemanticEvent.Mark.mayHoldParagraph(display: CapturedDisplay): Boolean = when {
    holdsOnlyPhrasing() -> false
    else -> when (display) {
        // A block or a `contents` wrapper, which defers to its parent.
        Unrecorded -> true
        UaDefault -> !isInlineLevel(name) && !isNeverRenderedByUaDefault()
        is Known -> when (display.value) {
            "contents" -> true
            "none" -> false
            else -> !display.value.startsWith("inline")
        }
    }
}

/**
 * Whether this element's content is phrasing once rendered, whatever its
 * display — a heading, a table cell, a control's label, … — so no paragraph
 * is expressible anywhere inside it.
 */
private fun SemanticEvent.Mark.holdsOnlyPhrasing(): Boolean = when {
    name in PHRASING_HOSTS -> true
    // A control's content is its label, and a ref-bearing element is kept
    // as a tag whose content the agent reads as one thing.
    this[AccessibilityAnnotations.REF] != null || isDeclaredControl() -> true
    explicitRole() in PHRASING_ROLES -> true
    // An inline element [simplifyHtml] keeps (`strong`, `abbr`, `time`, …)
    // stays a raw tag around a block, which does not re-parse, whatever
    // display the page gave it (Bootstrap's `.d-block`). Only the generic
    // wrappers and custom elements are unwrapped.
    isInlineByUaDefault() && name !in UNWRAPPED_INLINE_WRAPPERS && '-' !in name -> true
    else -> false
}

/**
 * Whether this element comes out of [simplifyHtml] as a Markdown block (or a
 * block-level tag), which a paragraph cannot hold.
 */
private fun SemanticEvent.Mark.rendersAsBlock(): Boolean =
    name in LINK_BLOCK_CONTENT_TAGS || name in DESCRIPTION_LIST_TAGS || explicitRole() in BLOCK_ROLES

// Kept by [simplifyHtml] as block-level tags, though not in
// [LINK_BLOCK_CONTENT_TAGS], which mirrors the Markdown renderer's block sets.
private val DESCRIPTION_LIST_TAGS = setOf("dl", "dt", "dd")

// The inline-by-UA elements [simplifyHtml] unwraps.
private val UNWRAPPED_INLINE_WRAPPERS = setOf("span", "font", "tt")

// Elements whose content is phrasing in Markdown (or in HTML): a heading, a
// table cell, a label, … — a paragraph inside one is not expressible.
private val PHRASING_HOSTS = setOf(
    "h1", "h2", "h3", "h4", "h5", "h6", "p", "pre", "code", "textarea",
    "table", "caption", "thead", "tbody", "tfoot", "tr", "td", "th", "colgroup", "col",
    "ul", "ol", "dl", "dt", "dd", "menu",
    "a", "button", "summary", "label", "legend", "select", "optgroup", "option", "datalist",
    "head", "title", "hr", "br", "img", "ruby", "rt", "rp", "svg",
)

// The `role`s [simplifyHtml] turns a generic element into phrasing content
// for, or the interactive ones whose content is their name.
private val PHRASING_ROLES = setOf(
    "heading", "paragraph", "code", "img", "separator", "math", "tooltip", "term",
    "table", "grid", "treegrid", "rowgroup", "row", "cell", "gridcell", "columnheader", "rowheader",
    "list", "directory", "listbox", "menu", "menubar", "tablist", "tree", "radiogroup",
    "button", "link", "option", "menuitem", "menuitemcheckbox", "menuitemradio",
    "tab", "checkbox", "radio", "switch", "treeitem", "combobox", "searchbox", "slider", "spinbutton",
)

// The `role`s [simplifyHtml] promotes a generic element to a block for.
private val BLOCK_ROLES = setOf(
    "article", "banner", "complementary", "contentinfo", "main", "navigation", "region", "search",
    "list", "directory", "listitem", "paragraph", "blockquote", "figure",
    "table", "grid", "rowgroup", "row", "columnheader", "rowheader", "cell", "gridcell",
    "heading", "separator", "dialog", "alertdialog",
)

// A flex / grid container blockifies its direct children, so an unannotated
// child of one is a separated box. An absent display is never a flex container.
private fun CapturedDisplay.isFlexGridContainer(): Boolean =
    this is Known && ("flex" in value || "grid" in value)

// A captured `inline` (or box-less `contents`) display: the element sits in
// the text flow of its parent, flush against its neighbours.
private fun CapturedDisplay.isCapturedInlineFlow(): Boolean =
    this is Known && (value == "inline" || value == "contents")

private fun SemanticEvent.Mark.isPreserveRegion(): Boolean =
    isTagged && name in WHITESPACE_PRESERVE_TAGS

private val WHITESPACE_PRESERVE_TAGS = setOf("pre", "code", "textarea")
