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
import com.xemantic.markanywhere.html.spec.isHtmlBlank
import kotlinx.coroutines.flow.Flow

/**
 * Ensures the stream starts with a `frontmatter` mark defining a `title`,
 * deriving a missing title from the first `h1`.
 *
 * The frontmatter judged is the one [wrapInHtmlDocument] reads: an untagged
 * `frontmatter` mark opening the stream, text of HTML whitespace before it
 * being insignificant — the frontmatter is emitted ahead of that text, so it
 * opens the stream as Markdown front matter must; one anywhere else, or a
 * tagged one, is ordinary content. Its title entries are its top-level
 * `entry` marks with `key="title"` in any ASCII letter case, and one holding
 * scalar text with visible content is usable — entries holding a nested
 * structure, an empty collection, `null` or blank text are not, as there.
 *
 * Whenever a title comes out, the frontmatter holds exactly one title entry,
 * spelled `title`: every front matter reader then reads the same title —
 * whether it matches keys case-sensitively (Jekyll) or not, keeps the later
 * of duplicate keys (Psych, PyYAML) or rejects them (js-yaml) — and it is the
 * one [wrapInHtmlDocument] reads. With a usable title entry that is the one
 * [wrapInHtmlDocument] picks, respelled `title` in place; every other title
 * entry is dropped.
 *
 * Otherwise the frontmatter is held back and the title is derived from the
 * very first `h1` following it (only HTML whitespace text may intervene): the
 * `h1` subtree's flattened text — its text events plus the `alt` of every
 * `img` mark, in document order, the way an accessible name is computed from
 * content — with its HTML whitespace stripped and collapsed, as
 * `document.title` reads a `<title>`, and any other whitespace at its edges
 * trimmed, becomes the `title`, and only then the frontmatter and the
 * buffered `h1` are emitted, in source order. The derived entry takes the
 * place of the first title entry, every other one dropped, or is injected as
 * the first `entry` of a frontmatter without any. When no frontmatter exists
 * at all, one carrying just the derived `title` is synthesized as the
 * **first** event — ahead of any whitespace text that preceded the `h1`.
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

    // emits the held frontmatter — `prepended` right after its mark, the span
    // of each slot in `rewrite` replaced by its events (none drops the entry)
    // — then the blanks held with it
    suspend fun flushHeld(
        rewrite: Map<TitleSlot, List<SemanticEvent>> = emptyMap(),
        prepended: List<SemanticEvent> = emptyList()
    ) {
        frontmatterEvents?.let { held ->
            emit(held.first())
            emit(prepended)
            var next = 1
            for ((slot, events) in rewrite.entries.sortedBy { it.key.start }) {
                emit(held.subList(next, slot.start))
                emit(events)
                next = slot.end + 1
            }
            emit(held.subList(next, held.size))
        }
        frontmatterEvents = null
        flushBlanks()
    }

    // emits the held frontmatter with `entry` in place of `slot`, every
    // other title entry dropped
    suspend fun flushWithSingleTitle(slot: TitleSlot, entry: List<SemanticEvent>) {
        flushHeld(titleSlots.associateWith { if (it == slot) entry else emptyList() })
    }

    suspend fun commitHeading() {
        val title = headingText.toString().normalizeTitle()
        val slot = titleSlots.firstOrNull()
        when {
            !isMetadataValue(title) -> flushHeld()
            frontmatterEvents == null -> {
                "frontmatter" {
                    emit(titleEntry(title))
                }
                flushBlanks()
            }
            slot == null -> flushHeld(prepended = titleEntry(title))
            else -> flushWithSingleTitle(slot, titleEntry(title))
        }
        emit(headingEvents)
        headingEvents.clear()
        state = PassThrough
    }

    // decides, once the frontmatter closes, whether it holds a usable title
    suspend fun judgeFrontmatter() {
        // the entry wrapInHtmlDocument reads
        val usable = titleSlots
            .filter { it.usable }
            .reduceOrNull { read, next -> if (frontMatterKeySupersedes(read.key, next.key)) next else read }
        if (usable != null) {
            val events = frontmatterEvents!!.subList(usable.start, usable.end + 1)
            flushWithSingleTitle(usable, respelled(events))
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
                event is Text && event.text.isHtmlBlank() -> blanks += event
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
                            val usable = !openTitleHasChildren && isScalarEntryType(type) &&
                                    isMetadataValue(openTitleText.toString())
                            titleSlots += TitleSlot(openTitleStart, events.lastIndex, open["key"]!!, usable)
                        }
                        0 -> judgeFrontmatter()
                    }
                }
            }
            AwaitingHeading -> when (event) {
                is Text if event.text.isHtmlBlank() -> blanks += event
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
// frontmatter, its key as spelled, and whether it holds a usable title.
private data class TitleSlot(val start: Int, val end: Int, val key: String, val usable: Boolean)

// A top-level front matter `title` entry holding the scalar `value`.
private fun titleEntry(value: String): List<SemanticEvent> = listOf(
    SemanticEvent.Mark("entry", attributes = mapOf("key" to "title")),
    SemanticEvent.Text(value),
    SemanticEvent.Unmark("entry")
)

// An entry's events with its key spelled `title`.
private fun respelled(entry: List<SemanticEvent>): List<SemanticEvent> {
    val mark = entry.first() as Mark
    return if (mark["key"] == "title") entry
    else listOf(mark.copy(attributes = mark.attributes + ("key" to "title"))) + entry.drop(1)
}
