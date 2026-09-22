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
 * Ensures the stream starts with a `frontmatter` mark defining a `title`,
 * deriving a missing title from the first `h1`.
 *
 * A stream whose leading `frontmatter` already holds a usable top-level
 * `entry` with `key="title"` — one with non-blank text, or one holding a
 * nested structure — passes through untouched. Otherwise the frontmatter is
 * held back and the title is derived from the very first `h1` following it
 * (only blank text may intervene): the `h1` subtree's flattened text —
 * its text events plus the `alt` of every `img` mark, in document order,
 * the way an accessible name is computed from content — trimmed, internal
 * whitespace collapsed to single spaces — becomes the `title`, injected as
 * the first `entry` of the frontmatter (or put in
 * place of a `title` entry that is blank or `null`, such as a bare `title:`
 * line), and only then the frontmatter and the buffered `h1` are emitted,
 * in source order. When no frontmatter exists at all, one carrying just the
 * derived `title` is synthesized as the **first** event — ahead of any
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
    var hasTitle = false

    // the span of a top-level `title` entry that carries no usable title
    // (blank text, `type=null`) within `frontmatterEvents`, replaced by the
    // derived entry on commit; `titleStart` < 0 when there is none
    var titleStart = -1
    var titleEnd = -1
    // the top-level title entry currently open: its text, and whether it
    // holds nested marks (then it is kept as is, whatever its text)
    var inTitleEntry = false
    var titleHasChildren = false
    val titleText = StringBuilder()

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

    suspend fun emitTitleEntry(title: String) {
        "entry"("key" to "title") { +title }
    }

    suspend fun commitHeading() {
        val title = headingText.toString().trim().replace(WHITESPACE_RUN, " ")
        val held = frontmatterEvents
        if (title.isEmpty()) {
            flushHeld()
        } else if (held == null) {
            "frontmatter" {
                emitTitleEntry(title)
            }
            flushBlanks()
        } else if (titleStart >= 0) {
            // replay the original frontmatter verbatim, with the derived
            // entry in place of the unusable title entry
            frontmatterEvents = null
            emit(held.subList(0, titleStart))
            emitTitleEntry(title)
            emit(held.subList(titleEnd + 1, held.size))
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
                event is Mark && event.name == "frontmatter" -> {
                    // a frontmatter is only ever the first event, but keep
                    // whatever preceded it in source order
                    flushBlanks()
                    frontmatterEvents = mutableListOf(event)
                    frontmatterDepth = 1
                    hasTitle = false
                    titleStart = -1
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
                            inTitleEntry = event.name == "entry" && event["key"] == "title"
                            if (inTitleEntry) {
                                titleHasChildren = false
                                titleText.clear()
                                titleStart = events.lastIndex
                            }
                        } else if (inTitleEntry) {
                            titleHasChildren = true
                        }
                    }
                    is Text -> if (inTitleEntry && frontmatterDepth == 2) titleText.append(event.text)
                    is Unmark -> when (--frontmatterDepth) {
                        1 -> if (inTitleEntry) {
                            inTitleEntry = false
                            if (titleHasChildren || titleText.isNotBlank()) {
                                hasTitle = true
                                titleStart = -1
                            } else {
                                titleEnd = events.lastIndex
                            }
                        }
                        0 -> if (hasTitle) {
                            flushHeld()
                            state = PassThrough
                        } else {
                            state = AwaitingHeading
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

private val WHITESPACE_RUN = Regex("""\s+""")
