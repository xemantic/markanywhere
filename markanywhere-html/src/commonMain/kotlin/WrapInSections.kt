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
 * Groups every mark named [tagName] and its following siblings into a
 * synthetic `section` mark carrying an `id` derived from the heading text.
 *
 * `wrapInSections("h2")` turns a flat heading-delimited stream into an
 * explicitly sectioned one: `<h2>A</h2><p>a</p><h2>B</h2><p>b</p>` becomes
 * `<section id="a"><h2>A</h2><p>a</p></section><section id="b"><h2>B</h2><p>b</p></section>`.
 * See the matcher overload for the full semantics.
 */
public fun Flow<SemanticEvent>.wrapInSections(
    tagName: String
): Flow<SemanticEvent> = wrapInSections { mark -> mark.name == tagName }

/**
 * Groups every mark matched by [sectionStart] and its following siblings into
 * a synthetic `section` mark carrying an `id` derived from the heading text.
 *
 * When a matching [Mark][SemanticEvent.Mark] arrives, a `section` opens right
 * before it and stays open across the matched mark and all of its following
 * siblings. The section closes at whichever comes first:
 *
 * - the next matching sibling mark — which immediately opens the next section,
 * - the closing unmark of the enclosing container,
 * - the end of the stream.
 *
 * The section's `id` is the anchor form of the matched subtree's flattened
 * text (nested marks stripped away): trimmed, lowercased, letters/digits/`-`/`_`
 * kept, whitespace replaced with `-`, everything else dropped — the GitHub
 * heading-anchor convention. A repeated id gains a `-1`/`-2`/… suffix so ids
 * stay unique within the stream; a heading yielding no anchor characters
 * produces a section without an `id`.
 *
 * Content preceding the first match flows through untouched. Sectioning is
 * per-sibling-level: a match nested deeper inside an open section starts its
 * own nested `section` there, closed by the same rules. The synthetic mark
 * mirrors the matched mark's [isTagged][SemanticEvent.Mark.isTagged], and
 * every `section` mark is guaranteed a matching unmark, so a balanced input
 * stays balanced.
 *
 * Buffering is bounded to the matched mark's own subtree — its events are held
 * between the mark and its balanced unmark so the `id` can be derived before
 * the `section` opens, then replayed verbatim. Everything else is forwarded as
 * it arrives.
 *
 * @param sectionStart decides, from an opening mark, whether it starts a section.
 */
public fun Flow<SemanticEvent>.wrapInSections(
    sectionStart: (SemanticEvent.Mark) -> Boolean
): Flow<SemanticEvent> = semanticEvents {

    // count of currently open source marks; a section opened by a match at
    // depth d wraps siblings living exactly at depth d
    var depth = 0
    // open synthetic sections, innermost last
    val openSections = ArrayDeque<OpenSection>()
    val usedIds = mutableSetOf<String>()

    // the matched mark's subtree, buffered between its mark and balanced
    // unmark so the section id can be derived from the flattened text
    var headingEvents: MutableList<SemanticEvent>? = null
    var headingDepth = 0
    var headingIsTagged = false
    val headingText = StringBuilder()

    suspend fun closeSection() {
        val section = openSections.removeLast()
        unmark("section", isTagged = section.isTagged)
    }

    suspend fun openSectionFromHeading() {
        val events = headingEvents!!
        headingEvents = null
        val id = anchorId(headingText.toString(), usedIds)
        headingText.clear()
        mark(
            "section",
            isTagged = headingIsTagged,
            attributes = id?.let { mapOf("id" to it) } ?: emptyMap()
        )
        openSections += OpenSection(depth, headingIsTagged)
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
            is Mark -> if (sectionStart(event)) {
                if (openSections.lastOrNull()?.depth == depth) {
                    closeSection()
                }
                headingEvents = mutableListOf(event)
                headingDepth = 1
                headingIsTagged = event.isTagged
            } else {
                depth++
                emit(event)
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
}

private class OpenSection(
    val depth: Int,
    val isTagged: Boolean
)

// The GitHub heading-anchor convention: trim, lowercase, keep letters,
// digits, `-`, `_`; whitespace becomes `-`; everything else is dropped.
// Duplicates gain a `-1`/`-2`/… suffix; an all-dropped title yields null.
private fun anchorId(
    title: String,
    usedIds: MutableSet<String>
): String? {
    val slug = buildString {
        for (c in title.trim().lowercase()) {
            when {
                c.isLetterOrDigit() || c == '-' || c == '_' -> append(c)
                c.isWhitespace() -> append('-')
            }
        }
    }
    if (slug.isEmpty()) return null
    if (usedIds.add(slug)) return slug
    var counter = 1
    while (true) {
        val candidate = "$slug-$counter"
        if (usedIds.add(candidate)) return candidate
        counter++
    }
}
