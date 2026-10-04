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
import com.xemantic.markanywhere.dump.AccessibilityAnnotations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Decides, for every emphasis element [simplifyHtml] preserves, whether it
 * renders as Markdown emphasis or as a raw tag — the last step of
 * [simplifyHtml].
 *
 * Presentational `b` / `i` / `s` / `strike` are renamed to the Markdown
 * emphasis they look like ([MARKDOWN_EMPHASIS_EQUIVALENTS]), and the native
 * `strong` / `em` / `del` / `mark` / `sup` stay untagged — except where
 * Markdown cannot express the element, in which case it is (or becomes) a
 * tagged raw tag:
 * - its subtree holds **block content** ([LINK_BLOCK_CONTENT_TAGS]) — Markdown
 *   emphasis cannot span blocks, and its delimiters would be left on lines of
 *   their own, while a raw tag can wrap them;
 * - it carries a **kept attribute** other than the transient
 *   [AccessibilityAnnotations.DISPLAY] — Markdown emphasis has no syntax for
 *   attributes, so the actionable ref of a clickable `<b>` would be lost;
 * - it sits **inside code** (`pre` / `code`), where delimiters would read as
 *   part of the code, so a `b` stays the tag it is.
 *
 * Only the block test needs to see past the mark: an emphasis element is held
 * until its close, so buffering is bounded to one emphasis subtree at a time —
 * the same bound `encodeActionableRefs` pays for a link.
 */
internal fun Flow<SemanticEvent>.expressEmphasisInMarkdown(): Flow<SemanticEvent> = flow {

    // Every open mark as emitted, so its unmark is rewritten to match.
    val open = ArrayDeque<SemanticEvent.Mark>()
    var codeDepth = 0
    val buffer = mutableListOf<SemanticEvent>()
    var bufferDepth = 0

    suspend fun rewrite(event: SemanticEvent, containsBlock: Boolean) {
        when (event) {
            is Mark -> {
                val mark = if (codeDepth > 0) event else event.expressed(containsBlock)
                if (event.name in CODE_TAGS) codeDepth++
                open.addLast(mark)
                emit(mark)
            }
            is Unmark -> {
                val mark = open.removeLastOrNull()
                if (mark == null) emit(event)
                else {
                    if (mark.name in CODE_TAGS) codeDepth--
                    emit(SemanticEvent.Unmark(mark.name, mark.isTagged))
                }
            }
            is Text -> emit(event)
        }
    }

    suspend fun drainBuffer() {
        // Whether each buffered mark's subtree holds block content, propagated
        // to the enclosing mark when its own subtree closes.
        val containsBlock = BooleanArray(buffer.size)
        val stack = ArrayDeque<Int>()
        buffer.forEachIndexed { index, event ->
            when (event) {
                is Mark -> {
                    if (event.name in LINK_BLOCK_CONTENT_TAGS) {
                        stack.lastOrNull()?.let { containsBlock[it] = true }
                    }
                    stack.addLast(index)
                }
                is Unmark -> {
                    val closed = stack.removeLastOrNull()
                    if (closed != null && containsBlock[closed]) {
                        stack.lastOrNull()?.let { containsBlock[it] = true }
                    }
                }
                is Text -> {}
            }
        }
        buffer.forEachIndexed { index, event -> rewrite(event, containsBlock[index]) }
        buffer.clear()
    }

    collect { event ->
        if (bufferDepth > 0) {
            buffer += event
            when (event) {
                is Mark -> bufferDepth++
                is Unmark -> bufferDepth--
                is Text -> {}
            }
            if (bufferDepth == 0) drainBuffer()
        } else if (event is SemanticEvent.Mark && codeDepth == 0 && event.awaitsBlockTest()) {
            buffer += event
            bufferDepth = 1
        } else {
            rewrite(event, containsBlock = false)
        }
    }
    // An unclosed emphasis element at the end of an unbalanced stream.
    drainBuffer()
}

private fun SemanticEvent.Mark.isEmphasis(): Boolean =
    if (isTagged) name in MARKDOWN_EMPHASIS_EQUIVALENTS else name in MARKDOWN_EMPHASIS

private fun SemanticEvent.Mark.carriesInexpressibleAttribute(): Boolean =
    attributes.keys.any { it != AccessibilityAnnotations.DISPLAY }

// Only an emphasis element Markdown could still express needs its subtree
// checked for block content.
private fun SemanticEvent.Mark.awaitsBlockTest(): Boolean =
    isEmphasis() && !carriesInexpressibleAttribute()

private fun SemanticEvent.Mark.expressed(containsBlock: Boolean): SemanticEvent.Mark {
    if (!isEmphasis()) return this
    val expressible = !containsBlock && !carriesInexpressibleAttribute()
    return when {
        isTagged && expressible -> copy(name = MARKDOWN_EMPHASIS_EQUIVALENTS.getValue(name), isTagged = false)
        !isTagged && !expressible -> copy(isTagged = true)
        else -> this
    }
}

// Presentational formatting renamed to the Markdown-native emphasis it looks
// like. HTML5 gives `b` / `i` / `s` meanings of their own (attention, another
// voice, no longer accurate), but the web keeps using them as plain bold /
// italic / strikethrough, and a reader of the Markdown — model or human — can
// neither see nor act on the distinction, while a raw tag costs tokens and
// leaves stray HTML in ordinary text. `u` has no Markdown equivalent and stays
// a raw tag.
private val MARKDOWN_EMPHASIS_EQUIVALENTS = mapOf(
    "b" to "strong",
    "i" to "em",
    "s" to "del",
    "strike" to "del",
)

private val MARKDOWN_EMPHASIS = setOf("strong", "em", "del", "mark", "sup")

private val CODE_TAGS = setOf("pre", "code")
