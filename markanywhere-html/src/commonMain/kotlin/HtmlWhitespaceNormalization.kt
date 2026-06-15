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
import com.xemantic.markanywhere.flow.mergeAdjacentText
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
 *   and the interior runs of `A\n\t\tB` collapse to `A B`.
 *
 * The asymmetry mirrors HTML: a closing inline tag or non-whitespace text can
 * be the *left* side of a kept space (content just ended), and an opening inline
 * tag or non-whitespace text can be its *right* side (content is about to
 * start). Whitespace that is merely the interior of an empty inline element
 * (`<code> </code>`) therefore drops, because its left neighbour is the *open*
 * tag, not content.
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

    // Resolve a buffered whitespace run now that its right neighbour is known:
    // keep a single collapsed space if inline content flanks both sides, else
    // drop it.
    suspend fun resolvePending(rightQualifies: Boolean) {
        if (pending) {
            if (leftQualifies && rightQualifies) emit(SemanticEvent.Text(" "))
            pending = false
        }
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
                    resolvePending(rightQualifies = true)
                    emit(SemanticEvent.Text(event.text.collapseWhitespace()))
                    leftQualifies = true
                    pending = event.text.last().isHtmlWhitespace()
                }
            }

            is Mark -> {
                resolvePending(rightQualifies = event.name in INLINE_HTML_ELEMENTS)
                emit(event)
                depth++
                if (preserveFrom < 0 && event.isPreserveRegion()) {
                    preserveFrom = depth
                }
                leftQualifies = false // an opening tag is not the end of content
            }

            is Unmark -> {
                resolvePending(rightQualifies = false) // a closing tag is not the start of content
                if (depth == preserveFrom) preserveFrom = -1
                depth--
                emit(event)
                leftQualifies = event.name in INLINE_HTML_ELEMENTS
            }
        }
    }
    // End-of-stream (a block boundary): any trailing whitespace run is dropped.
}.mergeAdjacentText()

// Collapses every run of ASCII whitespace to a single space and trims the ends —
// the caller re-attaches a separating space via the block/inline gate when one
// is warranted. Non-ASCII spaces (NBSP ` `, narrow NBSP, en/em space, …)
// are content, not structural whitespace: HTML never collapses them, so they
// pass through verbatim (e.g. legal citations like `§ 823`).
private fun String.collapseWhitespace(): String = buildString {
    var inWhitespace = false
    for (c in this@collapseWhitespace) {
        if (c.isHtmlWhitespace()) {
            inWhitespace = true
        } else {
            if (isNotEmpty() && inWhitespace) append(' ')
            inWhitespace = false
            append(c)
        }
    }
}

// Opens a whitespace-preserving region: tagged HTML `<pre>`/`<code>`/`<textarea>`
// (their content whitespace is significant), plus the synthetic `frontmatter`
// block, whose newline-separated YAML must survive verbatim regardless of
// `isTagged` (`simplifyHtml` emits it untagged).
private fun SemanticEvent.Mark.isPreserveRegion(): Boolean =
    name == "frontmatter" || (isTagged && name in WHITESPACE_PRESERVE_TAGS)

private val WHITESPACE_PRESERVE_TAGS = setOf("pre", "code", "textarea")

// HTML phrasing / inline-level elements: whitespace touching their boundaries
// is part of inline flow and collapses to a single space rather than being
// dropped. Everything not listed here (div, p, section, li, table, …, and any
// unknown / custom tag) is treated as a block boundary.
private val INLINE_HTML_ELEMENTS = setOf(
    "a", "abbr", "b", "bdi", "bdo", "big", "cite", "code", "data", "dfn",
    "em", "font", "i", "img", "ins", "del", "kbd", "label", "mark", "q",
    "rp", "rt", "ruby", "s", "samp", "small", "span", "strike", "strong",
    "sub", "sup", "time", "tt", "u", "var", "wbr",
)