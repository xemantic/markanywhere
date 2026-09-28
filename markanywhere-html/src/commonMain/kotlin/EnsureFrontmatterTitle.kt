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
import com.xemantic.markanywhere.html.spec.asciiLowercase
import com.xemantic.markanywhere.html.spec.stripAndCollapseHtmlWhitespace
import kotlinx.coroutines.flow.Flow

/**
 * Ensures the stream starts with a `frontmatter` mark defining a `title`,
 * deriving a missing title from the first `h1`.
 *
 * The frontmatter and title entry judged are the ones [wrapInHtmlDocument]
 * reads: an untagged `frontmatter` mark that is the very first event — one
 * anywhere else, or a tagged one, is ordinary content — and in it the first
 * top-level `entry` with `key="title"` (in any ASCII letter case) holding
 * non-blank scalar text — entries holding a nested structure, an empty
 * collection, `null` or blank text are skipped, as there. A stream whose
 * leading `frontmatter` holds such an entry passes through untouched, except
 * that a key spelled in another letter case (`Title`) is respelled `title`,
 * the one spelling every front matter reader recognises — unless an entry
 * spelled `title` is also present, which the respelling would duplicate.
 * Otherwise the frontmatter is held back and the title is derived from the
 * very first `h1` following it (only blank text may intervene): the `h1`
 * subtree's flattened text — its text events plus the `alt` of every `img`
 * mark, in document order, the way an accessible name is computed from
 * content — with its HTML whitespace stripped and collapsed, as
 * `document.title` reads a `<title>`, becomes the `title`, and only then the
 * frontmatter and the buffered `h1` are emitted, in source order. The
 * derived title replaces, in place, the first blank or `null` (a bare
 * `title:` line) entry spelled `title`, else the first blank scalar title
 * entry in another spelling, else the first `null` one — spelling its key
 * `title` on the same terms as above; without any it is injected as the
 * first `entry` of the frontmatter — unless an entry spelled `title` holding
 * a nested structure or an empty collection is present, which is then left
 * to stand alone: replacing it would lose content, and a second entry would
 * duplicate its key. When no frontmatter exists at all, one carrying just
 * the derived `title` is synthesized as the **first** event — ahead of any
 * blank text that preceded the `h1`, since [wrapInHtmlDocument] reads only
 * a frontmatter that opens the stream.
 *
 * When no title can be derived — the first non-blank event after the
 * frontmatter is not an `h1`, the `h1` yields no text, or the stream ends —
 * everything held is flushed unchanged: a stream without a leading `h1`
 * passes through untouched and no empty frontmatter is fabricated.
 *
 * Buffering is bounded to the frontmatter subtree plus one `h1` subtree —
 * everything after the decision point is forwarded as it arrives.
 */
public fun Flow<SemanticEvent>.ensureFrontmatterTitle(): Flow<SemanticEvent> = semanticEvents {

    var state: State = AtStart

    // the held frontmatter subtree (mark + body events + unmark); null when
    // no frontmatter was present and a default one is to be synthesized
    var frontmatterEvents: MutableList<SemanticEvent>? = null
    var frontmatterDepth = 0

    // top-level title entries (in any letter case) within `frontmatterEvents`
    // the first non-blank scalar one — the one wrapInHtmlDocument reads
    var usableTitle: TitleSlot? = null
    // the first blank or `null` one spelled exactly `title`, the first blank
    // scalar one and the first `null` one, the slots the derived title
    // replaces, in this order
    var exactTitle: TitleSlot? = null
    var blankTitle: TitleSlot? = null
    var nullTitle: TitleSlot? = null
    // an entry spelled exactly `title`, which a respelled key or an injected
    // entry would duplicate
    var titleKeyTaken = false
    // the slot replaced by the derived entry on commit
    var replacedTitle: TitleSlot? = null

    // the top-level title entry currently open (its `end` not yet known),
    // its `type` and text, and whether it holds nested marks
    var openTitle: TitleSlot? = null
    var openTitleType: String? = null
    var openTitleHasChildren = false
    val openTitleText = StringBuilder()

    // blank text held before the first h1 (ahead of the frontmatter, or
    // between it and the h1), replayed in source order on commit
    val blanks = mutableListOf<SemanticEvent>()

    // the h1 subtree, buffered between its mark and balanced unmark so the
    // title can be derived from the flattened text
    val headingEvents = mutableListOf<SemanticEvent>()
    var headingDepth = 0
    val headingText = StringBuilder()

    suspend fun flushBlanks() {
        emit(blanks)
        blanks.clear()
    }

    suspend fun flushHeld() {
        frontmatterEvents?.let { emit(it) }
        frontmatterEvents = null
        flushBlanks()
    }

    suspend fun emitTitleEntry(title: String, key: String = "title") {
        "entry"("key" to key) { +title }
    }

    // the key a title entry is written under
    fun titleKey(slot: TitleSlot?): String =
        if (slot == null || !titleKeyTaken) "title" else slot.key

    suspend fun commitHeading() {
        val title = headingText.toString().stripAndCollapseHtmlWhitespace()
        val held = frontmatterEvents
        val replaced = replacedTitle
        if (title.isBlank()) {
            flushHeld()
        } else if (held == null) {
            "frontmatter" {
                emitTitleEntry(title)
            }
            flushBlanks()
        } else if (replaced != null) {
            // replay the original frontmatter verbatim, with the derived
            // entry in place of the unusable title entry
            frontmatterEvents = null
            emit(held.subList(0, replaced.start))
            emitTitleEntry(title, titleKey(replaced))
            emit(held.subList(replaced.end + 1, held.size))
            flushBlanks()
        } else {
            // replay the original frontmatter verbatim, with a single title
            // entry prepended to its content
            frontmatterEvents = null
            emit(held.first())
            emitTitleEntry(title)
            emit(held.subList(1, held.size))
            flushBlanks()
        }
        emit(headingEvents)
        headingEvents.clear()
        state = PassThrough
    }

    fun startHeading(event: SemanticEvent) {
        headingEvents += event
        headingDepth = 1
        state = InHeading
    }

    collect { event ->
        when (state) {
            AtStart -> when {
                // only a frontmatter opening the stream is the page's
                // metadata; anywhere else it is content, as for wrapInHtmlDocument
                event is Mark && !event.isTagged && event.name == "frontmatter" && blanks.isEmpty() -> {
                    frontmatterEvents = mutableListOf(event)
                    frontmatterDepth = 1
                    state = InFrontmatter
                }
                event is Mark && event.name == "h1" -> startHeading(event)
                event is Text && event.text.isBlank() -> blanks += event
                else -> {
                    flushBlanks()
                    emit(event)
                    state = PassThrough
                }
            }
            InFrontmatter -> {
                val events = frontmatterEvents!!
                events += event
                when (event) {
                    is Mark -> {
                        frontmatterDepth++
                        if (frontmatterDepth == 2) {
                            // a top-level entry (a direct child)
                            val key = event["key"]
                            if (event.name == "entry" && key?.asciiLowercase() == "title") {
                                if (key == "title") titleKeyTaken = true
                                openTitle = TitleSlot(events.lastIndex, -1, key)
                                openTitleType = event["type"]
                                openTitleHasChildren = false
                                openTitleText.clear()
                            }
                        } else if (openTitle != null) {
                            openTitleHasChildren = true
                        }
                    }
                    is Text -> if (openTitle != null && frontmatterDepth == 2) openTitleText.append(event.text)
                    is Unmark -> when (--frontmatterDepth) {
                        1 -> openTitle?.let { open ->
                            openTitle = null
                            val slot = open.copy(end = events.lastIndex)
                            val type = openTitleType
                            when {
                                // unreadable: kept, never replaced
                                openTitleHasChildren || type != "null" && !isScalarEntryType(type) -> {}
                                type == "null" -> {
                                    if (nullTitle == null) nullTitle = slot
                                    if (exactTitle == null && slot.key == "title") exactTitle = slot
                                }
                                isMetadataValue(openTitleText.toString()) ->
                                    if (usableTitle == null) usableTitle = slot
                                else -> {
                                    if (blankTitle == null) blankTitle = slot
                                    if (exactTitle == null && slot.key == "title") exactTitle = slot
                                }
                            }
                        }
                        0 -> {
                            val usable = usableTitle
                            if (usable != null && usable.key != titleKey(usable)) {
                                val mark = events[usable.start] as Mark
                                events[usable.start] = mark.copy(
                                    attributes = mark.attributes + ("key" to "title")
                                )
                            }
                            replacedTitle = exactTitle ?: blankTitle ?: nullTitle
                            // with no slot to replace, an entry spelled
                            // `title` left is unreadable: injecting would
                            // duplicate its key
                            if (usable != null || replacedTitle == null && titleKeyTaken) {
                                flushHeld()
                                state = PassThrough
                            } else {
                                state = AwaitingHeading
                            }
                        }
                    }
                }
            }
            AwaitingHeading -> when (event) {
                is Text if event.text.isBlank() -> blanks += event
                is Mark if event.name == "h1" -> startHeading(event)
                else -> {
                    flushHeld()
                    emit(event)
                    state = PassThrough
                }
            }
            InHeading -> {
                headingEvents += event
                when (event) {
                    is Mark -> {
                        headingDepth++
                        // an image contributes its alt, as it does to the
                        // heading's accessible name; the space keeps it from
                        // merging with adjacent text (collapsed on commit)
                        if (event.name == "img") {
                            event["alt"]?.let { headingText.append(' ').append(it).append(' ') }
                        }
                    }
                    is Text -> headingText.append(event.text)
                    is Unmark -> if (--headingDepth == 0) {
                        commitHeading()
                    }
                }
            }
            PassThrough -> emit(event)
        }
    }

    // an unclosed h1 (broken upstream contract) still commits so no buffered
    // events are lost; otherwise flush whatever is still held
    if (state == InHeading) {
        commitHeading()
    } else {
        flushHeld()
    }
}

private enum class State {
    AtStart, InFrontmatter, AwaitingHeading, InHeading, PassThrough
}

// A top-level title entry: the span of its events within the held
// frontmatter, and its key as spelled.
private data class TitleSlot(val start: Int, val end: Int, val key: String)
