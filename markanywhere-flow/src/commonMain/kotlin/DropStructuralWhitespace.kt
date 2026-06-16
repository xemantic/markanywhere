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

package com.xemantic.markanywhere.flow

import com.xemantic.markanywhere.SemanticEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Drops whitespace-only text events that appear at element boundaries —
 * i.e., between two adjacent marks/unmarks, at the very start or end of
 * the stream. Whitespace that sits between two non-whitespace text events
 * is preserved (it's a word-boundary, not structural indentation).
 *
 * This is pure semantic-event-stream logic: it applies to any tag-based
 * markup where inter-element whitespace is insignificant (HTML, XML, SVG,
 * MathML, …). The one markup-specific knob is [preserveWithin] — a predicate
 * over the opening [SemanticEvent.Mark]; while the subtree it opens is on the
 * stack (until its matching [SemanticEvent.Unmark]), whitespace passes through
 * untouched. The predicate can inspect the tag name, [SemanticEvent.Mark.isTagged],
 * attributes, or any combination, so callers express exactly the preservation
 * policy their markup needs. It defaults to "never preserve".
 *
 * @param preserveWithin opens a whitespace-preserving region when it returns
 *   `true` for a [SemanticEvent.Mark]; the region closes at the matching unmark.
 */
public fun Flow<SemanticEvent>.dropStructuralWhitespace(
    preserveWithin: (SemanticEvent.Mark) -> Boolean = { false }
): Flow<SemanticEvent> = flow {

    var pending: String? = null
    var depth = 0          // current open-mark nesting depth
    var preserveFrom = -1  // depth at which the active preserve region opened, -1 = none

    collect { event ->
        when (event) {
            is Text -> when {
                preserveFrom >= 0 -> {
                    pending?.let { emit(SemanticEvent.Text(it)) }
                    pending = null
                    emit(event)
                }
                event.text.isEmpty() -> {
                    // skip — empty text carries no information
                }
                event.text.all { it.isWhitespace() } -> {
                    pending = (pending ?: "") + event.text
                }
                else -> {
                    pending?.let { emit(SemanticEvent.Text(it)) }
                    pending = null
                    emit(event)
                }
            }
            is Mark -> {
                pending = null
                emit(event)
                depth++
                if (preserveFrom < 0 && preserveWithin(event)) preserveFrom = depth
            }
            is Unmark -> {
                pending = null
                if (depth == preserveFrom) preserveFrom = -1
                depth--
                emit(event)
            }
        }
    }
    // End-of-stream: any trailing whitespace-only text is dropped.
}

/**
 * Convenience overload of [dropStructuralWhitespace] preserving whitespace
 * inside any mark whose [SemanticEvent.Mark.name] is in [preserveWithin],
 * regardless of [SemanticEvent.Mark.isTagged]. Use the predicate overload
 * when the policy needs more than a name match.
 *
 * @param preserveWithin a set of mark names.
 */
public fun Flow<SemanticEvent>.dropStructuralWhitespace(
    preserveWithin: Set<String>
): Flow<SemanticEvent> = dropStructuralWhitespace {
    it.name in preserveWithin
}
