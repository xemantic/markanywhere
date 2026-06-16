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
 *   absent or non-`inline`) that is *not* a surviving block, i.e. one whose
 *   boundary vanishes when [simplifyHtml] unwraps it (`<div>`, blockified
 *   `<span>`, the empty divider `<div>`); **and**
 * - **no surviving block** — nothing in [LINK_BLOCK_CONTENT_TAGS] (`<p>`,
 *   `<section>`, `<h2>`, `<button>`, …) which already renders with its own block
 *   separation, so two tokens split by one need no help (this is what stops a
 *   stray space between the header's `<a><button>Register</button></a>` items).
 *
 * Adjacent **inline** elements (`<b>bold</b><i>italic</i>`) cross no separated
 * box, so they are never split. Whitespace-only text passes through untouched —
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
        emit(token)
        crossedSeparatedBox = false
        crossedSurvivingBlock = false
        seenInlineToken = true
        previousEndedWithSpace = text?.lastOrNull()?.isWhitespace() == true
    }

    collect { event ->
        when (event) {
            is Mark -> {
                val parentIsFlexGrid = stack.lastOrNull()?.isFlexGridContainer == true
                val boundary = event.blockBoundary(parentIsFlexGrid)
                if (preserveDepth == 0) {
                    cross(boundary)
                    if (event.name == "img") emitInlineToken(event) else emit(event)
                } else emit(event)
                val opensPreserve = event.isPreserveRegion()
                stack.addLast(
                    BlockBoundaryFrame(boundary, opensPreserve, event.isFlexGridContainer())
                )
                if (opensPreserve) preserveDepth++
            }
            is Unmark -> {
                val frame = stack.removeLastOrNull()
                if (frame?.opensPreserve == true) preserveDepth--
                if (preserveDepth == 0) cross(frame?.boundary ?: BlockBoundary.INLINE)
                emit(event)
            }
            is Text -> when {
                preserveDepth > 0 -> emit(event)
                event.text.isHtmlBlank() -> emit(event)
                else -> emitInlineToken(event)
            }
        }
    }
}

/** How crossing an element's boundary bears on separating adjacent inline content. */
private enum class BlockBoundary { SURVIVING_BLOCK, SEPARATED_BOX, INLINE }

private class BlockBoundaryFrame(
    val boundary: BlockBoundary,
    val opensPreserve: Boolean,
    val isFlexGridContainer: Boolean,
)

private fun SemanticEvent.Mark.blockBoundary(parentIsFlexGrid: Boolean): BlockBoundary = when {
    // A surviving block ([simplifyHtml] keeps it) already gives block separation.
    name in LINK_BLOCK_CONTENT_TAGS -> BlockBoundary.SURVIVING_BLOCK
    // An <img> is replaced *content*, not a wrapper: it is an inline token and
    // separation comes from the boxes around it, never from the image itself —
    // so a lazy-load placeholder <img> next to its real sibling <img> (two views
    // of one image) is not split, even though the placeholder has no display
    // annotation and would otherwise read as a block box.
    name == "img" -> BlockBoundary.INLINE
    else -> when (val display = this[AccessibilityAnnotations.DISPLAY]) {
        // No annotation = computed `block` *only when reliably captured*. The
        // browser blockifies the direct children of a flex/grid container, so an
        // unannotated child there is a separated box (the BBC metadata spans);
        // anywhere else an unannotated element is normal inline flow — a hand-
        // built / un-annotated stream's `<a>`/`<strong>`/`<i>` must not over-split.
        null -> if (parentIsFlexGrid) BlockBoundary.SEPARATED_BOX else BlockBoundary.INLINE
        // An explicit block-level box ([simplifyHtml] unwraps `<div>`/`<span>`):
        // its boundary vanishes on flatten. `inline*` stays inline flow.
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
