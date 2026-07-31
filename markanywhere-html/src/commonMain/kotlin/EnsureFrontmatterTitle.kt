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
 * A stream whose leading `frontmatter` already defines a top-level `title`
 * key passes through untouched. Otherwise the frontmatter is held back and
 * the title is derived from the very first `h1` following it (only blank
 * text may intervene): the `h1` subtree's flattened text — trimmed, internal
 * whitespace collapsed to single spaces — becomes the `title`, injected as
 * the first line of the frontmatter body (YAML `title: …` or TOML
 * `title = "…"`, matching the mark's `format` attribute; prepending keeps a
 * TOML title ahead of any `[section]` header), and only then the frontmatter
 * and the buffered `h1` are emitted, in source order. When no frontmatter
 * exists at all, a default YAML one carrying just the derived `title` is
 * synthesized before the `h1`.
 *
 * When no title can be derived — the first non-blank event after the
 * frontmatter is not an `h1`, the `h1` has no text, the frontmatter's
 * `format` is unrecognized, or the stream ends — everything held is flushed
 * unchanged: a stream without a leading `h1` passes through untouched and no
 * empty frontmatter is fabricated.
 *
 * Buffering is bounded to the frontmatter body plus one `h1` subtree —
 * everything after the decision point is forwarded as it arrives.
 */
public fun Flow<SemanticEvent>.ensureFrontmatterTitle(): Flow<SemanticEvent> = semanticEvents {

    var state = State.AtStart

    // the held frontmatter subtree (mark + body texts + unmark); null when
    // no frontmatter was present and a default one is to be synthesized
    var frontmatterEvents: MutableList<SemanticEvent>? = null
    var frontmatterDepth = 0
    var format = "yaml"
    val body = StringBuilder()

    // blank text between the held frontmatter and the first h1, replayed in
    // source order on commit
    val blanks = mutableListOf<SemanticEvent>()

    // the h1 subtree, buffered between its mark and balanced unmark so the
    // title can be derived from the flattened text
    val headingEvents = mutableListOf<SemanticEvent>()
    var headingDepth = 0
    val headingText = StringBuilder()

    suspend fun flushHeld() {
        frontmatterEvents?.let { emit(it) }
        frontmatterEvents = null
        emit(blanks)
        blanks.clear()
    }

    suspend fun commitHeading() {
        val title = headingText.toString().trim().replace(WHITESPACE_RUN, " ")
        val held = frontmatterEvents
        if (title.isEmpty()) {
            flushHeld()
        } else if (held == null) {
            mark(
                "frontmatter",
                isTagged = false,
                attributes = mapOf("format" to "yaml")
            )
            text(titleLine(title, "yaml"))
            unmark("frontmatter", isTagged = false)
            emit(blanks)
            blanks.clear()
        } else {
            // replay the original frontmatter verbatim, with a single title
            // line prepended to its body
            frontmatterEvents = null
            emit(held.first())
            text(titleLine(title, format))
            emit(held.subList(1, held.size))
            emit(blanks)
            blanks.clear()
        }
        emit(headingEvents)
        headingEvents.clear()
        state = State.PassThrough
    }

    collect { event ->
        when (state) {
            AtStart -> when {
                event is Mark && event.name == "frontmatter" -> {
                    frontmatterEvents = mutableListOf(event)
                    frontmatterDepth = 1
                    format = event["format"] ?: "yaml"
                    state = State.InFrontmatter
                }
                event is Mark && event.name == "h1" -> {
                    headingEvents += event
                    headingDepth = 1
                    state = State.InHeading
                }
                event is Text && event.text.isBlank() -> emit(event)
                else -> {
                    emit(event)
                    state = State.PassThrough
                }
            }
            InFrontmatter -> {
                frontmatterEvents!! += event
                when (event) {
                    is Mark -> frontmatterDepth++
                    is Text -> body.append(event.text)
                    is Unmark -> if (--frontmatterDepth == 0) {
                        val supported = format == "yaml" || format == "toml"
                        if (!supported || hasTitle(body, format)) {
                            flushHeld()
                            state = State.PassThrough
                        } else {
                            state = State.AwaitingHeading
                        }
                    }
                }
            }
            AwaitingHeading -> when {
                event is Text && event.text.isBlank() -> blanks += event
                event is Mark && event.name == "h1" -> {
                    headingEvents += event
                    headingDepth = 1
                    state = State.InHeading
                }
                else -> {
                    flushHeld()
                    emit(event)
                    state = State.PassThrough
                }
            }
            InHeading -> {
                headingEvents += event
                when (event) {
                    is Mark -> headingDepth++
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
    if (state == State.InHeading) {
        commitHeading()
    } else {
        flushHeld()
    }
}

private enum class State {
    AtStart, InFrontmatter, AwaitingHeading, InHeading, PassThrough
}

private val WHITESPACE_RUN = Regex("""\s+""")
