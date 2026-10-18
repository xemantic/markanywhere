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
 * Injects a single separator space where flattening an **unwrapped block box**
 * (a `<div>`/`<span>`/… that is a separate visual box — CSS `block`/`flex`/
 * `grid`/`table`/… — but which [simplifyHtml] unwraps) would let inline content
 * from two different boxes word-merge.
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
 * The rule is a streaming, near-zero-buffer test (one small enum per open mark
 * on the depth stack — never a subtree). Between two inline content tokens
 * (text / `<img>`) a separator is injected **iff**, since the previous token, we
 * crossed:
 * - a **separated box** — a block-level box ([AccessibilityAnnotations.DISPLAY]
 *   neither `inline*` nor `contents`; when absent, either the stream was
 *   never captured and the UA default of the tag is assumed, or it is a dump
 *   captured before every display was recorded, where none is) that is *not* a
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
 * each other on purpose — they would read as one.
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
    // The open controls, innermost last, each by a serial number, and the
    // control (0 = none) holding the last inline content token.
    val openControls = ArrayDeque<Int>()
    var controlSerial = 0
    var previousTokenControl = 0
    // Whether the stream was captured, as far as it has been read (see
    // [showsCapture]). An old dump left the `block` of its `<html>`, `<body>`
    // and leading wrappers out, so those still read as their tag's UA default
    // — a `<div>`'s `block`, wrong only for a leading `display: contents` one.
    var captured = false
    // The opening marks held since the last token, so a separator can still
    // be placed before them.
    val heldMarks = mutableListOf<SemanticEvent>()

    suspend fun releaseHeldMarks() {
        heldMarks.forEach { emit(it) }
        heldMarks.clear()
    }

    fun cross(boundary: BlockBoundary) {
        when (boundary) {
            SURVIVING_BLOCK -> crossedSurvivingBlock = true
            SEPARATED_BOX -> crossedSeparatedBox = true
            INLINE -> { /* do nothing */ }
        }
    }

    suspend fun emitInlineToken(token: SemanticEvent) {
        // An <img> token carries no leading/trailing text to separate around.
        val text = (token as? SemanticEvent.Text)?.text
        // `isWhitespace()` (unlike `isHtmlBlank`) also covers NBSP / Unicode
        // spaces, so existing non-breaking separation isn't doubled.
        val startsWithSpace = text?.firstOrNull()?.isWhitespace() == true
        if (seenInlineToken && crossedSeparatedBox && !crossedSurvivingBlock &&
            !previousEndedWithSpace && !startsWithSpace
        ) {
            emit(SemanticEvent.Text(" "))
        }
        releaseHeldMarks()
        emit(token)
        crossedSeparatedBox = false
        crossedSurvivingBlock = false
        seenInlineToken = true
        previousEndedWithSpace = text?.lastOrNull()?.isWhitespace() == true
        previousTokenControl = openControls.lastOrNull() ?: 0
    }

    // Separates a control opening right after the content of another, closed one.
    suspend fun separateAdjacentControl() {
        if (previousTokenControl != 0 && previousTokenControl !in openControls &&
            !crossedSurvivingBlock && !previousEndedWithSpace
        ) {
            emit(SemanticEvent.Text(" "))
            previousEndedWithSpace = true
            crossedSeparatedBox = false
        }
    }

    collect { event ->
        when (event) {
            is Mark -> {
                val parentIsFlexGrid = stack.lastOrNull()?.isFlexGridContainer == true
                if (!captured && event.showsCapture()) captured = true
                val boundary = event.blockBoundary(parentIsFlexGrid, captured)
                val control = event.isDeclaredControl()
                if (preserveDepth == 0) {
                    cross(boundary)
                    if (control) separateAdjacentControl()
                }
                if (preserveDepth == 0 && event.name == "img") emitInlineToken(event)
                else heldMarks += event
                if (control) openControls.addLast(++controlSerial)
                val opensPreserve = event.isPreserveRegion()
                stack.addLast(
                    BlockBoundaryFrame(boundary, opensPreserve, event.isFlexGridContainer(), control)
                )
                if (opensPreserve) preserveDepth++
            }
            is Unmark -> {
                val frame = stack.removeLastOrNull()
                if (frame?.opensPreserve == true) preserveDepth--
                if (frame?.control == true) openControls.removeLastOrNull()
                if (preserveDepth == 0) cross(frame?.boundary ?: BlockBoundary.INLINE)
                releaseHeldMarks()
                emit(event)
            }
            is Text -> when {
                // Verbatim text: no separator is injected inside the region,
                // as nothing is crossed there once its first token is seen.
                preserveDepth > 0 && event.text.isNotEmpty() -> emitInlineToken(event)
                preserveDepth > 0 || event.text.isHtmlBlank() -> {
                    releaseHeldMarks()
                    emit(event)
                }
                else -> emitInlineToken(event)
            }
        }
    }
    releaseHeldMarks()
}

/** How crossing an element's boundary bears on separating adjacent inline content. */
private enum class BlockBoundary { SURVIVING_BLOCK, SEPARATED_BOX, INLINE }

private class BlockBoundaryFrame(
    val boundary: BlockBoundary,
    val opensPreserve: Boolean,
    val isFlexGridContainer: Boolean,
    val control: Boolean,
)

private fun SemanticEvent.Mark.blockBoundary(
    parentIsFlexGrid: Boolean,
    captured: Boolean
): BlockBoundary = when {
    // A surviving block ([simplifyHtml] keeps it) already gives block separation.
    name in LINK_BLOCK_CONTENT_TAGS -> BlockBoundary.SURVIVING_BLOCK
    // An <img> is replaced *content*, not a wrapper: it is an inline token and
    // separation comes from the boxes around it, never from the image itself —
    // so a lazy-load placeholder <img> next to its real sibling <img> (two views
    // of one image) is not split, even though the placeholder has no display
    // annotation and would otherwise read as a block box.
    name == "img" -> BlockBoundary.INLINE
    else -> when (val display = this[AccessibilityAnnotations.DISPLAY]) {
        // The direct children of a flex/grid container are blockified whatever
        // their tag.
        null if parentIsFlexGrid -> BlockBoundary.SEPARATED_BOX
        // An absent annotation in a captured stream is a dump captured before
        // every display was recorded, when both a plain `block` and a
        // `display: contents` were left out — no boundary can be told apart,
        // so none is crossed (a `contents` wrapper split `US$5`).
        null if captured -> BlockBoundary.INLINE
        // Never captured: the UA stylesheet's default is assumed, read the same
        // way the whitespace normalizer reads it — a `<div>` is a block (the
        // `<div>` per line a rich-text editor writes, `Hi,<div>there</div>`),
        // while a hand-built stream's `<a>`/`<strong>`/`<i>` must not over-split.
        // An element the UA stylesheet never renders (`<script>`, `<style>`, …)
        // lays out no box, so like `contents` it has no boundary to cross.
        null -> when {
            isInlineByUaDefault() || isNeverRenderedByUaDefault() -> BlockBoundary.INLINE
            else -> BlockBoundary.SEPARATED_BOX
        }
        // `display: contents` lays out no box of its own, so it has no
        // boundary to cross — its children's display is what separates.
        "contents" -> BlockBoundary.INLINE
        // A block-level box ([simplifyHtml] unwraps `<div>`/`<span>`): its
        // boundary vanishes on flatten. `inline*` stays inline flow.
        else -> {
            if (display.startsWith("inline")) BlockBoundary.INLINE
            else BlockBoundary.SEPARATED_BOX
        }
    }
}

// A flex / grid container blockifies its direct children, so an unannotated
// child of one is a separated box.
private fun SemanticEvent.Mark.isFlexGridContainer(): Boolean {
    val display = this[AccessibilityAnnotations.DISPLAY] ?: return false
    return "flex" in display || "grid" in display
}

private fun SemanticEvent.Mark.isPreserveRegion(): Boolean =
    isTagged && name in WHITESPACE_PRESERVE_TAGS

private val WHITESPACE_PRESERVE_TAGS = setOf("pre", "code", "textarea")
