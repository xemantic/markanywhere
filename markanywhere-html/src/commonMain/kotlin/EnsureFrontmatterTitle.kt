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
 * A verbatim line defining a title key (a YAML shape outside the parsed
 * subset, such as a multi-line quoted scalar) is a title entry too, one whose
 * value only the front matter readers know. Each title entry spans the
 * indented verbatim lines continuing it, so dropping one drops them too,
 * rather than leaving them to continue the entry before it.
 *
 * Whenever a title comes out, the frontmatter holds exactly one title entry,
 * spelled `title`: every front matter reader then reads the same title —
 * whether it matches keys case-sensitively (Jekyll) or not, keeps the later
 * of duplicate keys (Psych, PyYAML) or rejects them (js-yaml) — and it is the
 * one [wrapInHtmlDocument] reads. It comes out in one of two ways:
 *
 * - **A usable title entry exists**: the one [wrapInHtmlDocument] picks is
 *   respelled `title` in place, and every other title entry is dropped.
 * - **None exists**: the frontmatter is held back and the title is derived
 *   from the very first `h1` following it, only text showing nothing
 *   (whitespace, an NBSP, a zero-width char) intervening. The `h1` subtree's
 *   flattened text — its text events plus the `alt` of every `img` mark, in
 *   document order, the way an accessible name is computed from content —
 *   normalized as [normalizeTitle] reads a `<title>` becomes the `title`, and
 *   only then are the frontmatter and the buffered `h1` emitted, in source
 *   order. The derived entry takes the place of the first title entry, every
 *   other one dropped, or is injected as the first `entry` of a frontmatter
 *   without any. When no frontmatter exists at all, one carrying just the
 *   derived `title` is synthesized as the **first** event, ahead of any text
 *   that preceded the `h1`.
 *
 * Two shapes are left without a title:
 *
 * - A title entry holding a nested structure (localized titles, say) or kept
 *   as a verbatim line is no title for [wrapInHtmlDocument], but it is
 *   content: no title is derived and no title entry respelled. Of the other title entries only the usable
 *   one [wrapInHtmlDocument] picks is kept, as spelled, so readers may
 *   disagree on the title as they did on the input; the empty ones (`null`,
 *   blank, an empty collection) carry nothing, and another usable one would
 *   be a duplicate key, which makes js-yaml reject the whole front matter.
 *   A frontmatter whose root is a sequence (top-level `item` marks) has no
 *   place for a title entry and passes through unchanged.
 * - When no title can be derived — the first event after the frontmatter
 *   that shows something is not an `h1`, the `h1` yields no text, or the
 *   stream ends — everything held is flushed unchanged but for the title
 *   entries dropped as above.
 *
 * A frontmatter left empty, by dropping its title entries or as it came, is
 * dropped too (it would render as two thematic breaks), so no empty
 * frontmatter is ever emitted. Nor is a title entry dropped from the front
 * of a frontmatter where that would leave a verbatim line (one outside the
 * YAML subset) first — front matter detection needs a key there, so the
 * block would parse back as a thematic break and a paragraph: the title
 * entry kept moves into the first title entry's place instead, or, none
 * being kept, the first one stays.
 *
 * A frontmatter or `h1` the stream ends inside (a broken upstream contract)
 * is judged all the same, an entry left open included, and emitted closed,
 * so the output stays balanced.
 *
 * Buffering is bounded to the frontmatter subtree plus one `h1` subtree —
 * everything after the decision point is forwarded as it arrives.
 */
public fun Flow<SemanticEvent>.ensureFrontmatterTitle(): Flow<SemanticEvent> = semanticEvents {

    var state: State = AtStart

    // the held frontmatter subtree (mark + body events + unmark); null when
    // no frontmatter was present and a default one is to be synthesized
    var frontmatterEvents: MutableList<SemanticEvent>? = null

    // the top-level title entries (in any letter case) within
    // `frontmatterEvents`, in source order, their spans indexing it
    val titleSlots = mutableListOf<FrontMatterEntry>()
    // the one of them wrapInHtmlDocument reads the title from, if any
    var read: FrontMatterEntry? = null
    val reader = FrontMatterEntryReader { entry ->
        if (entry.key.asciiLowercase() == "title") {
            titleSlots += entry
            if (entry.readOver(read?.key)) read = entry
        }
    }

    // text showing nothing held before the first h1 (ahead of the
    // frontmatter, or between it and the h1), replayed in source order on
    // commit
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

    // the held events of this slot, as they came
    fun FrontMatterEntry.events(): List<SemanticEvent> = frontmatterEvents!!.subList(start, end + 1)

    // the held frontmatter's body — `prepended` first, the span of each slot
    // in `rewrite` (in source order, as `titleSlots` is) replaced by its
    // events (none drops the entry)
    fun heldBody(
        rewrite: Map<FrontMatterEntry, List<SemanticEvent>>,
        prepended: List<SemanticEvent>
    ): List<SemanticEvent> = buildList {
        val held = frontmatterEvents!!
        addAll(prepended)
        var next = 1
        for ((slot, events) in rewrite) {
            addAll(held.subList(next, slot.start))
            addAll(events)
            next = slot.end + 1
        }
        addAll(held.subList(next, held.size))
    }

    // Whether the held frontmatter's body, rewritten as [heldBody] would,
    // opens with a verbatim line: text, past any of HTML whitespace, ahead of
    // the first entry — found without building the body.
    fun opensWithVerbatimLine(
        rewrite: Map<FrontMatterEntry, List<SemanticEvent>>,
        prepended: List<SemanticEvent>
    ): Boolean {
        if (prepended.isNotEmpty()) return false
        val held = frontmatterEvents!!
        val slots = rewrite.keys.associateBy { it.start }
        var i = 1
        while (i < held.size) {
            val slot = slots[i]
            if (slot != null) {
                val events = rewrite.getValue(slot)
                if (events.isNotEmpty()) return events.first() is Text
                i = slot.end + 1
                continue
            }
            val event = held[i]
            if (!(event is Text && event.text.isHtmlBlank())) return event is Text
            i++
        }
        return false
    }

    // `rewrite` keeping the first title slot: the one scalar title entry it
    // keeps elsewhere moves there, or, keeping none, the slot stays as it
    // came
    fun keepingFirstSlot(
        rewrite: Map<FrontMatterEntry, List<SemanticEvent>>
    ): Map<FrontMatterEntry, List<SemanticEvent>> {
        val first = titleSlots.first()
        val kept = rewrite.entries.firstOrNull { (slot, events) ->
            slot != first && !slot.isUnreadable && events.isNotEmpty()
        }
        return rewrite.mapValues { (slot, events) ->
            when (slot) {
                first -> kept?.value ?: first.events()
                kept?.key -> emptyList()
                else -> events
            }
        }
    }

    // emits the held frontmatter rewritten as [heldBody] does, then the
    // blanks held with it. Dropping leading title entries must not leave a
    // verbatim line (one outside the YAML subset) first: front matter
    // detection needs a key on the line after the `---`, so the block would
    // parse back as a thematic break and a paragraph — the first title slot
    // is kept then ([keepingFirstSlot]). A frontmatter left empty, by the
    // rewrite or as it came, is dropped: it would render as a `---` pair,
    // which parses back as two thematic breaks.
    suspend fun flushHeld(
        rewrite: Map<FrontMatterEntry, List<SemanticEvent>> = emptyMap(),
        prepended: List<SemanticEvent> = emptyList()
    ) {
        frontmatterEvents?.let { held ->
            val leavesVerbatimLineFirst = opensWithVerbatimLine(rewrite, prepended)
                && !opensWithVerbatimLine(emptyMap(), emptyList())
            val body = heldBody(if (leavesVerbatimLineFirst) keepingFirstSlot(rewrite) else rewrite, prepended)
            val empty = body.none {
                it is Mark || (it is Text && !it.text.isHtmlBlank())
            }
            if (!empty) {
                emit((listOf(held.first()) + body).closed())
            }
        }
        frontmatterEvents = null
        flushBlanks()
    }

    // emits the held frontmatter with `entry` in place of `slot`, every
    // other title entry dropped
    suspend fun flushWithSingleTitle(slot: FrontMatterEntry, entry: List<SemanticEvent>) {
        flushHeld(titleSlots.associateWith { if (it == slot) entry else emptyList() })
    }

    // emits the held frontmatter keeping, of its title entries, only the
    // unreadable ones and the one wrapInHtmlDocument reads: the empty ones
    // carry nothing, and another usable one would be a duplicate key
    suspend fun flushWithReadTitleOnly() {
        flushHeld(
            titleSlots.associateWith {
                if (it.isUnreadable || it == read) it.events() else emptyList()
            }
        )
    }

    suspend fun commitHeading() {
        val title = headingText.toString().normalizeTitle()
        val slot = titleSlots.firstOrNull()
        when {
            !isMetadataValue(title) -> flushWithReadTitleOnly()
            frontmatterEvents == null -> {
                "frontmatter" {
                    emit(titleEntry(title))
                }
                flushBlanks()
            }
            slot == null -> flushHeld(prepended = titleEntry(title))
            else -> flushWithSingleTitle(slot, titleEntry(title))
        }
        emit(headingEvents.closed())
        headingEvents.clear()
        state = PassThrough
    }

    // decides, once the frontmatter closes, whether it holds a usable title
    suspend fun judgeFrontmatter() {
        val usable = read
        state = when {
            // a nested or verbatim title is content, not ours to replace or
            // drop; a sequence has no place for a title entry
            titleSlots.any { it.isUnreadable } || reader.isSequence -> {
                flushWithReadTitleOnly()
                PassThrough
            }
            usable != null -> {
                flushWithSingleTitle(usable, respelled(usable.events()))
                PassThrough
            }
            else -> AwaitingHeading
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
                // only a frontmatter opening the stream (but for HTML
                // whitespace text) is the page's metadata; anywhere else it
                // is content
                event.opensFrontmatter(blanks) -> {
                    frontmatterEvents = mutableListOf(event)
                    reader.read(event)
                    state = InFrontmatter
                }
                event is Mark && event.name == "h1" -> startHeading(event)
                event.showsNothing() -> blanks += event
                else -> {
                    flushBlanks()
                    emit(event)
                    state = PassThrough
                }
            }
            InFrontmatter -> {
                frontmatterEvents!! += event
                if (reader.read(event)) judgeFrontmatter()
            }
            AwaitingHeading -> when (event) {
                is Text if event.showsNothing() -> blanks += event
                is Mark if event.name == "h1" -> startHeading(event)
                else -> {
                    flushWithReadTitleOnly()
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

    // an unclosed frontmatter or h1 (broken upstream contract) is still
    // committed so no buffered events are lost; otherwise flush whatever is
    // still held
    when (state) {
        // judged like a closed one, an entry left open included, as
        // wrapInHtmlDocument reads it
        InFrontmatter -> {
            reader.finish()
            judgeFrontmatter()
            if (state == AwaitingHeading) flushWithReadTitleOnly()
        }
        InHeading -> commitHeading()
        AwaitingHeading -> flushWithReadTitleOnly()
        else -> flushHeld()
    }
}

// Whether this event is text a reader sees nothing of — the one test of
// blankness around the h1, the one a title is judged by ([isMetadataValue]).
// Which text may precede a frontmatter is a different, HTML question
// ([mayPrecedeFrontmatter]).
private fun SemanticEvent.showsNothing(): Boolean = this is Text && !isMetadataValue(text)

// Whether this title entry holds a title [wrapInHtmlDocument] cannot read,
// though front matter readers do: a nested structure or a verbatim line.
private val FrontMatterEntry.isUnreadable: Boolean get() = hasChildren || isVerbatim

// These events with an unmark appended for every mark left open, innermost
// first.
private fun List<SemanticEvent>.closed(): List<SemanticEvent> {
    val open = ArrayDeque<SemanticEvent.Mark>()
    for (event in this) when (event) {
        is Mark -> open.addLast(event)
        is Unmark -> open.removeLastOrNull()
        else -> {}
    }
    return if (open.isEmpty()) this
    else this + open.reversed().map { SemanticEvent.Unmark(it.name, it.isTagged) }
}

private enum class State {
    AtStart, InFrontmatter, AwaitingHeading, InHeading, PassThrough
}

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
