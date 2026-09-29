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
 * The frontmatter judged is the one [wrapInHtmlDocument] reads: an untagged
 * `frontmatter` mark opening the stream — blank text before it is
 * insignificant, and the frontmatter is emitted ahead of it, so that
 * [wrapInHtmlDocument] reads it too; one anywhere else, or a tagged one, is
 * ordinary content. Its title entries are its top-level `entry` marks with
 * `key="title"` in any ASCII letter case, and one holding non-blank scalar
 * text is usable — entries holding a nested structure, an empty collection,
 * `null` or blank text are not, as there.
 *
 * With a usable title entry the frontmatter passes through, edited only so
 * that a front matter reader — matching keys case-sensitively and keeping
 * the later of duplicate keys — reads the title [wrapInHtmlDocument] reads:
 * every blank or `null` entry spelled `title` is dropped, and when the
 * usable entry [wrapInHtmlDocument] picks is spelled in another letter case
 * (`Title`) its key is respelled `title` — unless an entry spelled `title`
 * holding a nested structure or an empty collection is present, which the
 * respelling would duplicate.
 *
 * Otherwise the frontmatter is held back and the title is derived from the
 * very first `h1` following it (only blank text may intervene): the `h1`
 * subtree's flattened text — its text events plus the `alt` of every `img`
 * mark, in document order, the way an accessible name is computed from
 * content — with its HTML whitespace stripped and collapsed, as
 * `document.title` reads a `<title>`, becomes the `title`, and only then the
 * frontmatter and the buffered `h1` are emitted, in source order. The
 * derived title replaces, in place, the first blank or `null` (a bare
 * `title:` line) entry spelled `title` — dropping every other one, as above
 * — else the first blank scalar title entry in another spelling, else the
 * first `null` one, spelling its key `title` on the same terms as above;
 * without any it is injected as the first `entry` of the frontmatter —
 * unless an entry spelled `title` holding a nested structure or an empty
 * collection is present, which is then left to stand alone: replacing it
 * would lose content, and a second entry would duplicate its key. When no
 * frontmatter exists at all, one carrying just the derived `title` is
 * synthesized as the **first** event — ahead of any blank text that
 * preceded the `h1`.
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

    // the top-level title entries (in any letter case) within
    // `frontmatterEvents`, in source order
    val titleSlots = mutableListOf<TitleSlot>()
    // the top-level title entry currently open: its mark and start, its
    // text, and whether it holds nested marks
    var openTitle: SemanticEvent.Mark? = null
    var openTitleStart = -1
    var openTitleHasChildren = false
    val openTitleText = StringBuilder()

    // the blank or `null` title entries spelled `title`, the slot of them (or
    // of a variant) the derived title replaces, and whether an unreadable
    // entry spelled `title` stands, which a respelled key would duplicate
    var emptyTitles: List<TitleSlot> = emptyList()
    var replacedTitle: TitleSlot? = null
    var titleKeyTaken = false

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

    // emits the held frontmatter, the span of each slot in `edits` replaced
    // by what its edit emits, then the blanks held with it
    suspend fun flushHeld(edits: Map<TitleSlot, suspend () -> Unit> = emptyMap()) {
        frontmatterEvents?.let { held ->
            var next = 0
            for ((slot, edit) in edits.entries.sortedBy { it.key.start }) {
                emit(held.subList(next, slot.start))
                edit()
                next = slot.end + 1
            }
            emit(held.subList(next, held.size))
        }
        frontmatterEvents = null
        flushBlanks()
    }

    suspend fun emitTitleEntry(title: String, key: String = "title") {
        "entry"("key" to key) { +title }
    }

    // the edits dropping every empty title entry spelled `title` but `kept`
    fun dropEmptyTitles(kept: TitleSlot? = null): Map<TitleSlot, suspend () -> Unit> =
        emptyTitles.filter { it != kept }.associateWith { {} }

    suspend fun commitHeading() {
        val title = headingText.toString().stripAndCollapseHtmlWhitespace()
        val replaced = replacedTitle
        when {
            !isMetadataValue(title) -> flushHeld()
            frontmatterEvents == null -> {
                "frontmatter" {
                    emitTitleEntry(title)
                }
                flushBlanks()
            }
            // the derived entry in place of the unusable title entry
            replaced != null -> {
                val key = if (titleKeyTaken) replaced.key else "title"
                flushHeld(dropEmptyTitles(kept = replaced) + (replaced to { emitTitleEntry(title, key) }))
            }
            // a single title entry prepended to the frontmatter's content
            else -> {
                val held = frontmatterEvents!!
                frontmatterEvents = null
                emit(held.first())
                emitTitleEntry(title)
                emit(held.subList(1, held.size))
                flushBlanks()
            }
        }
        emit(headingEvents)
        headingEvents.clear()
        state = PassThrough
    }

    // decides, once the frontmatter closes, whether it holds a usable title
    suspend fun judgeFrontmatter() {
        titleKeyTaken = titleSlots.any { it.kind == UNREADABLE && it.key == "title" }
        emptyTitles = titleSlots.filter { it.key == "title" && (it.kind == BLANK || it.kind == NULL) }
        // the entry wrapInHtmlDocument reads (HeadMetadata.addFromFrontMatter)
        val usable = titleSlots.lastOrNull { it.kind == USABLE && it.key == "title" }
            ?: titleSlots.lastOrNull { it.kind == USABLE }
        if (usable != null) {
            val edits = dropEmptyTitles()
            val held = frontmatterEvents!!
            flushHeld(
                if (usable.key == "title" || titleKeyTaken) edits
                else edits + (usable to {
                    val mark = held[usable.start] as Mark
                    emit(mark.copy(attributes = mark.attributes + ("key" to "title")))
                    emit(held.subList(usable.start + 1, usable.end + 1))
                })
            )
            state = PassThrough
            return
        }
        replacedTitle = emptyTitles.firstOrNull()
            ?: titleSlots.firstOrNull { it.kind == BLANK }
            ?: titleSlots.firstOrNull { it.kind == NULL }
        // with no slot to replace, an entry spelled `title` left is
        // unreadable: injecting would duplicate its key
        if (replacedTitle == null && titleKeyTaken) {
            flushHeld()
            state = PassThrough
        } else {
            state = AwaitingHeading
        }
    }

    fun startHeading(event: SemanticEvent) {
        headingEvents += event
        headingDepth = 1
        state = InHeading
    }

    collect { event ->
        when (state) {
            AtStart -> when {
                // only a frontmatter opening the stream (but for blank text)
                // is the page's metadata; anywhere else it is content
                event is Mark && !event.isTagged && event.name == "frontmatter" -> {
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
                            if (event.name == "entry" && event["key"]?.asciiLowercase() == "title") {
                                openTitle = event
                                openTitleStart = events.lastIndex
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
                            val type = open["type"]
                            val kind: TitleKind = when {
                                openTitleHasChildren || type != "null" && !isScalarEntryType(type) -> UNREADABLE
                                type == "null" -> NULL
                                isMetadataValue(openTitleText.toString()) -> USABLE
                                else -> BLANK
                            }
                            titleSlots += TitleSlot(openTitleStart, events.lastIndex, open["key"]!!, kind)
                        }
                        0 -> judgeFrontmatter()
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

// How a title entry reads: a non-blank scalar, blank text, `null`, or a
// nested structure or an empty collection.
private enum class TitleKind {
    USABLE, BLANK, NULL, UNREADABLE
}

// A top-level title entry: the span of its events within the held
// frontmatter, its key as spelled, and how it reads.
private data class TitleSlot(val start: Int, val end: Int, val key: String, val kind: TitleKind)
