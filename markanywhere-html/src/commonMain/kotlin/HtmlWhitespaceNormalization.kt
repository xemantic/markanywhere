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
import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.html.spec.HTML_VOID_ELEMENTS
import com.xemantic.markanywhere.html.spec.isHtmlBlank
import com.xemantic.markanywhere.html.spec.isHtmlWhitespace
import com.xemantic.markanywhere.html.spec.stripAndCollapseHtmlWhitespace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Normalizes the insignificant whitespace HTML uses for source legibility —
 * the `\n` and indentation between tags — applying the HTML block/inline
 * collapsing rules to a semantic event stream.
 *
 * Whitespace runs are collapsed the way a browser would: every run (whether a
 * standalone whitespace-only text event, or the leading / interior / trailing
 * whitespace of a content-bearing text event) collapses to at most a single
 * space, which is then **kept** only when inline content flanks both sides and
 * **dropped** when it touches a block boundary:
 *
 * - Touching a **block** boundary (a block element's mark/unmark, or the start
 *   / end of the stream) the space is **dropped** — this strips indentation
 *   after an open tag, blank lines before the first content, the leading /
 *   trailing whitespace inside a block (`\n   Rechtskraft:` → `Rechtskraft:`),
 *   and the `\n` between sibling blocks that would otherwise become stray
 *   spaces on heading lines (`## \nTitle`).
 * - Sitting between two pieces of **inline** content — text, or an inline
 *   element's boundary — it is **collapsed to a single space** and kept, so a
 *   space that separates adjacent inline elements survives:
 *   `</a> <a>` → `</a>` `" "` `<a>` rather than the two links running together,
 *   and the interior runs of `A\n\t\tB` collapse to `A B`. A run right before
 *   an inline *closing* tag is carried past it (`<b>Note: </b>text` →
 *   `<b>Note:</b>` `" text"`), where a Markdown closing delimiter needs it;
 *   symmetrically, a run right after inline *opening* tags that follow
 *   content is moved before them (`Menu<a> Home</a>` → `"Menu "` `<a>`
 *   `"Home"`), where a link label or an opening delimiter needs it.
 *
 * The asymmetry mirrors HTML: a closing inline tag or non-whitespace text can
 * be the *left* side of a kept space (content just ended), and an opening inline
 * tag or non-whitespace text can be its *right* side (content is about to
 * start). Whitespace inside an empty inline element (`Hi<b> </b>there`) is a
 * run between the content around the element, so it is written before the
 * element when content follows and dropped at a block boundary.
 *
 * Only **ASCII whitespace** (space, tab, LF, CR, FF) is collapsed. Non-ASCII
 * spaces — NBSP (` `), narrow NBSP, en/em space, … — are content, never
 * structural, exactly as in HTML, so they survive verbatim (e.g. the
 * non-breaking spaces in legal citations like `§ 823` / `Abs. 1`).
 *
 * Whitespace inside HTML `<pre>`, `<code>`, or `<textarea>` is significant and
 * passes through untouched. Preservation is keyed on *tagged* (HTML-derived)
 * marks, so a Markdown-native inline `code` mark (`isTagged = false`) is treated
 * as a normal inline element, not a preserve region.
 *
 * Run this **after** [simplifyHtml]. When `simplifyHtml` unwraps a block wrapper
 * (`div`), the indentation whitespace that sat between its children survives as
 * plain text; the block/inline classification then collapses that whitespace to
 * a single space between the two text runs (so adjacent grid cells read
 * `Fundstelle openJur` rather than merging into `FundstelleopenJur`), while the
 * whitespace flanking a real block element (`p`, `h6`, `li`, …) is dropped.
 */
public fun Flow<SemanticEvent>.dropHtmlStructuralWhitespace(): Flow<SemanticEvent> = flow {

    var pending = false        // a collapsed whitespace run is buffered
    var depth = 0              // current open-mark nesting depth
    var preserveFrom = -1      // depth at which the active preserve region opened, -1 = none
    var leftQualifies = false  // can the previous significant token be the left side of a kept space?
    // Per open element, whether it is inline-level (so a closing tag can read
    // back its open-tag verdict — Unmark carries no attributes).
    val inlineStack = ArrayDeque<Boolean>()
    val displays = CapturedDisplayReader()
    // The inline tags since the last content, held until the next content or
    // block boundary decides whether a whitespace run after them
    // (`Menu<a> Home`, `Hi<b> </b>there`) separates the content before them
    // from what follows: such a run is written *before* the tags, where a
    // Markdown link label or an opening emphasis delimiter needs it (issue
    // #144). Only opening tags and the closes of elements emptied so far are
    // held, never content.
    val heldOpens = mutableListOf<SemanticEvent>()
    var heldOpenCount = 0 // the held opening tags not closed yet
    var leftBeforeHeld = false // could the content before the held tags be a left side?

    // Resolve a buffered whitespace run now that its right neighbour is known:
    // keep a single collapsed space if inline content flanks both sides, else
    // drop it.
    suspend fun resolvePending(rightQualifies: Boolean) {
        if (pending) {
            if (leftQualifies && rightQualifies) emit(SemanticEvent.Text(" "))
            pending = false
        }
    }

    // Writes the held opening tags, preceded by the whitespace run that
    // followed them when inline content flanks the run on both sides.
    suspend fun releaseHeldOpens(rightQualifies: Boolean) {
        if (heldOpens.isEmpty()) return
        if (pending) {
            if (leftBeforeHeld && rightQualifies) emit(SemanticEvent.Text(" "))
            pending = false
        }
        heldOpens.forEach { emit(it) }
        heldOpens.clear()
        heldOpenCount = 0
    }

    collect { event ->
        when (event) {

            is Text -> when {
                // Inside <pre>/<code>/<textarea> whitespace is significant.
                preserveFrom >= 0 -> {
                    emit(event)
                    leftQualifies = true
                }
                event.text.isEmpty() -> { /* skip — empty text carries no information */ }
                event.text.isHtmlBlank() -> { pending = true }
                else -> {
                    // Collapse interior whitespace runs and split off the
                    // leading / trailing whitespace, which is gated like any
                    // other whitespace run (kept as a space only between inline
                    // content, dropped at a block boundary).
                    if (event.text.first().isHtmlWhitespace()) pending = true
                    releaseHeldOpens(rightQualifies = true)
                    resolvePending(rightQualifies = true)
                    emit(SemanticEvent.Text(event.text.stripAndCollapseHtmlWhitespace()))
                    leftQualifies = true
                    pending = event.text.last().isHtmlWhitespace()
                }
            }

            is Mark -> {
                val display = displays.open(event)
                // A generic element [simplifyHtml] keeps only for its ref is
                // written as a raw inline tag, whatever its display (#169).
                val inline = event.isKeptForItsRef() || event.isInlineLevel(display)
                val opensPreserve = preserveFrom < 0 && event.isPreserveRegion()
                // Strip the display annotation we consumed (and the capture
                // format saying how to read it) so it can't leak into the
                // rendered output.
                val stripped = if (CONSUMED_ANNOTATIONS.any { it in event.attributes })
                    event.copy(attributes = event.attributes - CONSUMED_ANNOTATIONS)
                else event
                // A void element (`<img>`) is content, not a tag to hold.
                if (inline && !opensPreserve && preserveFrom < 0 && event.name !in HTML_VOID_ELEMENTS) {
                    if (heldOpens.isEmpty()) {
                        // A run before the first tag already separates: the
                        // content before the tags ends in a space now.
                        val separated = pending && leftQualifies
                        resolvePending(rightQualifies = true)
                        leftBeforeHeld = leftQualifies && !separated
                    }
                    heldOpens += stripped
                    heldOpenCount++
                } else {
                    // A run after held tags and before an image or a preserve
                    // region's opening tag (`Run<b> <code>`) still separates
                    // content; one before a block's touches a block boundary.
                    releaseHeldOpens(rightQualifies = inline)
                    resolvePending(rightQualifies = inline)
                    emit(stripped)
                }
                depth++
                inlineStack.addLast(inline)
                if (opensPreserve) {
                    preserveFrom = depth
                }
                leftQualifies = false // an opening tag is not the end of content
            }

            is Unmark -> {
                if (heldOpenCount > 0) {
                    // A held element closing with no content in it: what
                    // follows still decides the run after its opening tag.
                    displays.close()
                    depth--
                    inlineStack.removeLastOrNull()
                    heldOpens += event
                    heldOpenCount--
                    return@collect
                }
                if (heldOpens.isNotEmpty()) {
                    // Only emptied elements are held, and an enclosing element
                    // closes: the run after them is carried past this close
                    // (or dropped at a block one) like any run before a close.
                    heldOpens.forEach { emit(it) }
                    heldOpens.clear()
                    if (pending) leftQualifies = leftBeforeHeld
                }
                displays.close()
                if (depth == preserveFrom) preserveFrom = -1
                depth--
                // The closing tag reads back its open-tag inline verdict.
                val inline = inlineStack.removeLastOrNull()
                    ?: isInlineLevel(event.name)
                if (inline && pending) {
                    // An inline close is no boundary for whitespace: the run
                    // before it (`<b>Note: </b>text`) still separates the
                    // content on both sides, so it is carried past the close
                    // and resolved by what follows — landing outside the
                    // element, where a Markdown closing delimiter needs it.
                    emit(event)
                } else {
                    resolvePending(rightQualifies = false) // a closing tag is not the start of content
                    emit(event)
                    leftQualifies = inline
                }
            }
        }
    }
    heldOpens.forEach { emit(it) }
    // End-of-stream (a block boundary): any trailing whitespace run is dropped.
}.mergeAdjacentText()

// Opens a whitespace-preserving region, whose watermark covers the whole
// subtree — so a `code` nested in a `pre` needs no rule of its own.
//
// `pre` and `textarea` are verbatim whatever their origin, so neither may be
// gated on `isTagged`: an untagged `pre` is a Markdown fenced/indented code
// block, and in the HTML pipeline `simplifyHtml` has already untagged the HTML
// one by the time this operator runs — gating there collapsed every captured
// code block onto a single line.
//
// A *bare* `code` is the exception that still needs the flag: untagged it is a
// Markdown inline span, whose whitespace-only content is structural noise
// rather than significant text.
//
// `frontmatter` is synthetic and always untagged; the scalar text of its
// `entry` / `item` marks (block scalars, padding, double spaces) is content
// and must survive verbatim.
private fun SemanticEvent.Mark.isPreserveRegion(): Boolean =
    name == "frontmatter"
            || name == "pre"
            || name == "textarea"
            || (isTagged && name == "code")

// Decides whether an element is inline-level for whitespace gating. Prefers the
// browser's *computed* `display` captured in the dump (`data-markanywhere-display`):
// any `inline*` value (`inline`, `inline-block`, …) is inline, everything else
// (`block`, `flex`, `grid`, `table-cell`, `list-item`, …) is a block boundary —
// this honours CSS overrides a tag-name set can't see (`<span style=display:block>`).
// `contents` lays out no box of its own, so it is no boundary either: whitespace
// touching it belongs to the inline flow around it.
// The display is read through [CapturedDisplayReader], like every operator
// reads it. When none is known (synthetic / Markdown-native streams, the
// elements `simplifyHtml` synthesizes, or an unmarked dump that left a `block`
// out) the element's name decides ([isInlineLevel]).
private fun SemanticEvent.Mark.isInlineLevel(display: CapturedDisplay): Boolean =
    // Markdown emphasis / an inline code span renders inline whatever CSS
    // displayed it as (a blockified flex item `<strong>`): only its tag-ness
    // is decided upstream, by `expressEmphasisInMarkdown`. The rule makes no
    // difference to `applyAccessibility`, which reads the browser's layout: it
    // runs before `simplifyHtml`, so its marks are still the captured, tagged
    // elements, and an untagged emphasis there has an inline UA default anyway.
    if (!isTagged && name in MARKDOWN_INLINE_SPANS) true
    else when (display) {
        is Known -> display.value == "contents" || display.value.startsWith("inline")
        UaDefault, Unrecorded -> isInlineLevel(name)
    }

// The Markdown inline syntax: emphasis, a code span, a link and an image
// render inline whatever their CSS display (a link displayed as a block in a
// flex row, issue #169). A block-wrapping link, which the renderer spills to a
// raw block `<a>` tag, gets its block separation from the block inside it.
private val MARKDOWN_INLINE_SPANS = MARKDOWN_EMPHASIS + setOf("code", "a", "img")

private fun SemanticEvent.Mark.isKeptForItsRef(): Boolean =
    AccessibilityAnnotations.REF in attributes && name != "body" && isGenericContainer()

/**
 * The same verdict read from this element's own annotation alone, an absent
 * one meaning the UA default — the raw read `applyAccessibility`'s visibility
 * gap takes (see [CapturedDisplay]). A layout capture leaves a display out
 * only below a `none` root — a subtree that operator drops, or a `<select>`'s
 * popup, whose content its tags read best — and on the laid-out parts of an
 * `<svg>`, whose names are all inline by default, which the region implies.
 */
internal fun SemanticEvent.Mark.isInlineLevel(): Boolean {
    val display: CapturedDisplay = when (val recorded = this[AccessibilityAnnotations.DISPLAY]) {
        null -> UaDefault
        else -> CapturedDisplay.Known(recorded)
    }
    return isInlineLevel(display)
}

private val CONSUMED_ANNOTATIONS = setOf(AccessibilityAnnotations.DISPLAY, AccessibilityAnnotations.CAPTURE)
