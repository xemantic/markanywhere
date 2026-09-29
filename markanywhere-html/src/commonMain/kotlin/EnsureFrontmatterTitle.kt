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
import kotlinx.coroutines.flow.Flow

/**
 * Ensures the stream starts with a `frontmatter` mark defining a `title`,
 * deriving a missing title from the first `h1`.
 *
 * The frontmatter judged is the one [wrapInHtmlDocument] reads: an untagged
 * `frontmatter` mark opening the stream, blank text before it being
 * insignificant — the frontmatter is emitted ahead of that text, so it opens
 * the stream as Markdown front matter must; one anywhere else, or a tagged
 * one, is ordinary content. Its title entries are its top-level `entry` marks with
 * `key="title"` in any ASCII letter case, and one holding non-blank scalar
 * text is usable — entries holding a nested structure, an empty collection,
 * `null` or blank text are not, as there.
 *
 * With a usable title entry the frontmatter passes through, edited only so
 * that a front matter reader — matching keys case-sensitively and keeping
 * the later of duplicate keys — reads the title [wrapInHtmlDocument] reads:
 * every blank or `null` entry spelled `title` is dropped; when the usable
 * entry [wrapInHtmlDocument] picks is spelled `title` and followed by one
 * holding a nested structure or an empty collection, it is moved right after
 * that one; and when it is spelled in another letter case (`Title`) its key
 * is respelled `title` — unless an entry spelled `title` holding a nested
 * structure or an empty collection is present, which the respelling would
 * duplicate.
 *
 * Otherwise the frontmatter is held back and the title is derived from the
 * very first `h1` following it (only blank text may intervene): the `h1`
 * subtree's flattened text — its text events plus the `alt` of every `img`
 * mark, in document order, the way an accessible name is computed from
 * content — with its HTML whitespace stripped and collapsed, as
 * `document.title` reads a `<title>`, and any non-breaking space at its edges
 * trimmed, becomes the `title`, and only then the
 * frontmatter and the buffered `h1` are emitted, in source order. The
 * derived title replaces, in place, the first blank or `null` (a bare
 * `title:` line) entry spelled `title` — dropping every other one, as above
 * — else the first blank scalar title entry in another spelling, else the
 * first `null` one, spelling its key `title` on the same terms as above —
 * or, spelled `title`, it goes right after a later entry spelled `title`
 * holding a nested structure or an empty collection, as a usable one is
 * moved; without any it is injected as the first `entry` of the frontmatter —
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

    // how a derived title goes into the held frontmatter, decided once it
    // closes; null while none is held
    var derivation: Derivation? = null

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

    // the events of a slot's entry within the held frontmatter
    fun span(slot: TitleSlot): List<SemanticEvent> = frontmatterEvents!!.subList(slot.start, slot.end + 1)

    // the rewrite putting `entry`, keyed `key`, in place of `slot` — or,
    // spelled `title` and followed by an unreadable entry spelled `title`
    // which a later-wins reader would read instead, right after that one
    fun placed(slot: TitleSlot, entry: List<SemanticEvent>, key: String): Map<TitleSlot, List<SemanticEvent>> {
        val shadowing = titleSlots
            .lastOrNull { it.kind == UNREADABLE && it.key == "title" }
            ?.takeIf { key == "title" && it.start > slot.start }
        return if (shadowing == null) {
            mapOf(slot to entry)
        } else {
            mapOf(slot to emptyList(), shadowing to span(shadowing) + entry)
        }
    }

    fun dropping(slots: List<TitleSlot>): Map<TitleSlot, List<SemanticEvent>> =
        slots.associateWith { emptyList() }

    suspend fun commitHeading() {
        val title = headingText.toString().normalizeTitle()
        val plan = derivation
        when {
            !isMetadataValue(title) -> flushHeld()
            frontmatterEvents == null -> {
                "frontmatter" {
                    emit(titleEntry(title, "title"))
                }
                flushBlanks()
            }
            // a single title entry prepended to the frontmatter's content
            plan!!.slot == null -> flushHeld(prepended = titleEntry(title, plan.key))
            // the derived entry in place of the unusable title entry
            else -> flushHeld(dropping(plan.dropped) + placed(plan.slot, titleEntry(title, plan.key), plan.key))
        }
        emit(headingEvents)
        headingEvents.clear()
        state = PassThrough
    }

    // decides, once the frontmatter closes, whether it holds a usable title
    suspend fun judgeFrontmatter() {
        // an unreadable entry spelled `title`, which a respelled key would duplicate
        val titleKeyTaken = titleSlots.any { it.kind == UNREADABLE && it.key == "title" }
        val emptyTitles = titleSlots.filter { it.key == "title" && (it.kind == BLANK || it.kind == NULL) }
        // the entry wrapInHtmlDocument reads
        val usable = titleSlots
            .filter { it.kind == USABLE }
            .reduceOrNull { read, next -> if (frontMatterKeySupersedes(read.key, next.key)) next else read }
        if (usable != null) {
            val key = if (titleKeyTaken) usable.key else "title"
            val entry = if (key == usable.key) span(usable) else respelled(span(usable), key)
            flushHeld(dropping(emptyTitles) + placed(usable, entry, key))
            state = PassThrough
            return
        }
        val slot = emptyTitles.firstOrNull()
            ?: titleSlots.firstOrNull { it.kind == BLANK }
            ?: titleSlots.firstOrNull { it.kind == NULL }
        // with no slot to replace, an entry spelled `title` left is
        // unreadable: injecting would duplicate its key
        if (slot == null && titleKeyTaken) {
            flushHeld()
            state = PassThrough
        } else {
            derivation = Derivation(
                slot = slot,
                key = if (slot != null && titleKeyTaken) slot.key else "title",
                dropped = emptyTitles.filter { it != slot }
            )
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

// How a derived title goes into the held frontmatter: in place of `slot`
// (null: prepended to the frontmatter's entries), keyed `key`, the `dropped`
// empty title entries removed.
private class Derivation(val slot: TitleSlot?, val key: String, val dropped: List<TitleSlot>)

// A top-level front matter entry keyed `key` holding the scalar `value`.
private fun titleEntry(value: String, key: String): List<SemanticEvent> = listOf(
    SemanticEvent.Mark("entry", attributes = mapOf("key" to key)),
    SemanticEvent.Text(value),
    SemanticEvent.Unmark("entry")
)

// An entry's events with its key spelled `key`.
private fun respelled(entry: List<SemanticEvent>, key: String): List<SemanticEvent> {
    val mark = entry.first() as Mark
    return listOf(mark.copy(attributes = mark.attributes + ("key" to key))) + entry.drop(1)
}
