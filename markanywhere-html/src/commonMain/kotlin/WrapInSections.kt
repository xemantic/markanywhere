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
import com.xemantic.markanywhere.flow.semanticEvents
import kotlinx.coroutines.flow.Flow

/**
 * Groups every mark ranked by [sectionRank] and its following siblings into
 * a synthetic `section` mark carrying an `id` derived from the heading text,
 * nesting lower-rank sections inside higher-rank ones.
 *
 * By default the HTML headings `h2`..`h6` rank sections `1`..`5` — `h1` is
 * conventionally the unique document title, not a section start, so it flows
 * through unmatched (pass a custom [sectionRank] for a stream sectioned by
 * `h1`). `wrapInSections()` turns a flat heading-delimited stream
 * into an explicitly sectioned one: `<h2>A</h2><p>a</p><h2>B</h2><p>b</p>`
 * becomes
 * `<section id="a"><h2>A</h2><p>a</p></section><section id="b"><h2>B</h2><p>b</p></section>`,
 * and an `h3` section nests *inside* the open `h2` section, mirroring the
 * subordination the heading ranks express.
 *
 * When a [Mark][SemanticEvent.Mark] ranked `r` arrives, every open section at
 * the same sibling level with rank ≥ `r` closes first (a sibling of the same
 * rank, or the parent of a *higher* rank number — note lower number = higher
 * rank), then a `section` opens right before the matched mark and stays open
 * across it and its following siblings. So a rank-2 match after a rank-1 match
 * nests its section inside the still-open rank-1 section, while the next
 * rank-1 match closes both. A section also closes at the closing unmark of the
 * enclosing container and at the end of the stream.
 *
 * The section's `id` is the anchor form of the matched subtree's flattened
 * text (nested marks stripped away): trimmed, lowercased, letters/digits/`-`/`_`
 * kept, whitespace replaced with `-`, everything else dropped — the GitHub
 * heading-anchor convention. A repeated id gains a `-1`/`-2`/… suffix so ids
 * stay unique within the stream; a heading yielding no anchor characters
 * falls back to the synthetic `section` slug (deduplicated the same way), so
 * every section carries an `id` and stays linkable.
 *
 * With a positive [tocDepth], a table of contents is appended after the last
 * section closes, at the very end of the stream: a `nav` with `id="toc"`
 * holding a `ul` of `<li><a href="#section-id">heading text</a></li>` entries,
 * one per section whose nesting level is at most [tocDepth], with nested `ul`
 * lists mirroring the section nesting. When no section was created, no `nav`
 * is emitted at all. Because the `nav` lands at the absolute end of the stream,
 * apply this operator *before* any enclosing wrapper (e.g.
 * [wrapInHtmlDocument]) so the `nav` ends up inside the document. The TOC
 * entries are the one piece of state held until the end of the stream — one
 * short `(id, label, level)` tuple per section, metadata rather than content,
 * so buffering stays negligible.
 *
 * Content preceding the first match flows through untouched. Sectioning is
 * per-sibling-level: a match nested structurally deeper inside an open section
 * starts its own nested `section` there, closed by the same rules. The
 * synthetic marks mirror the matched mark's [isTagged][SemanticEvent.Mark.isTagged],
 * and every emitted mark is guaranteed a matching unmark, so a balanced input
 * stays balanced.
 *
 * Buffering is bounded to the matched mark's own subtree — its events are held
 * between the mark and its balanced unmark so the `id` can be derived before
 * the `section` opens, then replayed verbatim. Everything else is forwarded as
 * it arrives.
 *
 * @param tocDepth the deepest section nesting level linked from the appended
 *   table of contents; `0` (the default) disables the TOC.
 * @param sectionRank ranks an opening mark: `1` for a top-level section start,
 *   `2` for a subsection nesting inside an open rank-1 section, and so on;
 *   `null` for a mark that starts no section. Defaults to the HTML heading
 *   ranks `h2`..`h6`.
 */
public fun Flow<SemanticEvent>.wrapInSections(
    tocDepth: Int = 0,
    sectionRank: (SemanticEvent.Mark) -> Int? = HTML_HEADING_RANK
): Flow<SemanticEvent> = semanticEvents {

    // count of currently open source marks; a section opened by a match at
    // depth d wraps siblings living exactly at depth d
    var depth = 0
    // open synthetic sections, innermost last
    val openSections = ArrayDeque<OpenSection>()
    val usedIds = mutableSetOf<String>()
    val tocEntries = mutableListOf<TocEntry>()

    // the matched mark's subtree, buffered between its mark and balanced
    // unmark so the section id can be derived from the flattened text
    var headingEvents: MutableList<SemanticEvent>? = null
    var headingDepth = 0
    var headingIsTagged = false
    var headingRank = 0
    val headingText = StringBuilder()

    suspend fun closeSection() {
        val section = openSections.removeLast()
        unmark("section", isTagged = section.isTagged)
    }

    suspend fun openSectionFromHeading() {
        val events = headingEvents!!
        headingEvents = null
        val id = anchorId(headingText.toString(), usedIds)
        val label = headingText.toString().trim()
        headingText.clear()
        val level = openSections.size + 1
        if (level <= tocDepth) {
            tocEntries += TocEntry(id, label, level, headingIsTagged)
        }
        mark(
            "section",
            isTagged = headingIsTagged,
            attributes = mapOf("id" to id)
        )
        openSections += OpenSection(depth, headingRank, headingIsTagged)
        emit(events)
    }

    collect { event ->
        val heading = headingEvents
        if (heading != null) {
            heading += event
            when (event) {
                is Mark -> headingDepth++
                is Text -> headingText.append(event.text)
                is Unmark -> if (--headingDepth == 0) {
                    openSectionFromHeading()
                }
            }
        } else when (event) {
            is Mark -> {
                val rank = sectionRank(event)
                if (rank != null) {
                    while (
                        openSections.lastOrNull()
                            ?.let { it.depth == depth && it.rank >= rank } == true
                    ) {
                        closeSection()
                    }
                    headingEvents = mutableListOf(event)
                    headingDepth = 1
                    headingIsTagged = event.isTagged
                    headingRank = rank
                } else {
                    depth++
                    emit(event)
                }
            }
            is Text -> emit(event)
            is Unmark -> {
                // an unmark arriving at the section's own sibling level closes
                // the enclosing container, so the section must close first
                while (openSections.lastOrNull()?.let { it.depth >= depth } == true) {
                    closeSection()
                }
                depth--
                emit(event)
            }
        }
    }

    // an unclosed matched mark (broken upstream contract) still opens its
    // section so no buffered events are lost
    if (headingEvents != null) {
        openSectionFromHeading()
    }
    while (openSections.isNotEmpty()) {
        closeSection()
    }

    if (tocEntries.isNotEmpty()) {
        val isTagged = tocEntries.first().isTagged
        mark("nav", isTagged = isTagged, attributes = mapOf("id" to "toc"))
        var level = 0
        for (entry in tocEntries) {
            // every ancestor section is recorded before its descendants, so an
            // entry only ever steps one level deeper than the previous one
            if (entry.level > level) {
                mark("ul", isTagged = isTagged)
                level = entry.level
            } else {
                while (level > entry.level) {
                    unmark("li", isTagged = isTagged)
                    unmark("ul", isTagged = isTagged)
                    level--
                }
                unmark("li", isTagged = isTagged)
            }
            mark("li", isTagged = isTagged)
            mark("a", isTagged = isTagged, attributes = mapOf("href" to "#${entry.id}"))
            text(entry.label)
            unmark("a", isTagged = isTagged)
        }
        while (level > 0) {
            unmark("li", isTagged = isTagged)
            unmark("ul", isTagged = isTagged)
            level--
        }
        unmark("nav", isTagged = isTagged)
    }
}

// The default section ranking: HTML headings h2..h6 rank 1..5. h1 is
// conventionally the unique document title rather than a section start,
// so it is deliberately left unranked.
private val HTML_HEADING_RANK: (SemanticEvent.Mark) -> Int? = { mark ->
    when (mark.name) {
        "h2" -> 1
        "h3" -> 2
        "h4" -> 3
        "h5" -> 4
        "h6" -> 5
        else -> null
    }
}

private class OpenSection(
    val depth: Int,
    val rank: Int,
    val isTagged: Boolean
)

private class TocEntry(
    val id: String,
    val label: String,
    val level: Int,
    val isTagged: Boolean
)

// The GitHub heading-anchor convention: trim, lowercase, keep letters,
// digits, `-`, `_`; whitespace becomes `-`; everything else is dropped.
// Duplicates gain a `-1`/`-2`/… suffix; an all-dropped title falls back to
// the synthetic `section` slug so every section stays linkable.
private fun anchorId(
    title: String,
    usedIds: MutableSet<String>
): String {
    val slug = buildString {
        for (c in title.trim().lowercase()) {
            when {
                c.isLetterOrDigit() || c == '-' || c == '_' -> append(c)
                c.isWhitespace() -> append('-')
            }
        }
    }.ifEmpty { "section" }
    if (usedIds.add(slug)) return slug
    var counter = 1
    while (true) {
        val candidate = "$slug-$counter"
        if (usedIds.add(candidate)) return candidate
        counter++
    }
}
