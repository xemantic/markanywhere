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

package com.xemantic.markanywhere.transform

import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.flow.SemanticEventScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A transform applied to a complete balanced subtree of [SemanticEvent]s.
 *
 * Receives the opening mark, all of its descendants, and its closing unmark, and
 * emits the replacement events into the downstream flow via the
 * [SemanticEventScope] receiver. Emitting nothing drops the subtree entirely.
 */
public typealias MarkTransform =
    suspend SemanticEventScope.(List<SemanticEvent>) -> Unit

/**
 * Decides, from an opening [Mark][SemanticEvent.Mark], the [MarkTransform] to apply
 * to its balanced subtree, or `null` to forward the subtree verbatim.
 */
public typealias MarkSelect = (SemanticEvent.Mark) -> MarkTransform?

/**
 * Intercepts every balanced subtree whose opening [Mark][SemanticEvent.Mark] is
 * selected by [select] and rewrites it through the returned [MarkTransform].
 *
 * The flow is forwarded verbatim until a `Mark` for which [select] returns a
 * non-null [MarkTransform]. From that point the matching mark, all of its
 * descendants, and its closing [Unmark][SemanticEvent.Unmark] are buffered (never
 * emitted directly) until the subtree is balanced — i.e. the suppression depth
 * returns to zero. The complete, balanced list of buffered events is then handed
 * to the selected transform, whose emissions replace the original subtree in the
 * output. Nested matches are absorbed into the outermost buffered subtree, so the
 * transform is invoked once per top-level match, never re-entrantly.
 *
 * To drop a matched subtree entirely, have the transform emit nothing.
 *
 * @param select decides, from an opening mark, the [MarkTransform] to apply to its
 *   subtree, or `null` to forward the subtree verbatim.
 */
public fun Flow<SemanticEvent>.transformMatchingMarks(
    select: MarkSelect,
): Flow<SemanticEvent> = flow {

    var suppressionDepth = 0
    var suppressedEvents = mutableListOf<SemanticEvent>()
    val semanticEventScope = SemanticEventScope(collector = this)
    var transform: MarkTransform? = null

    collect { event ->

        when (event) {

            is Mark -> if (suppressionDepth == 0) {
                val matched = select(event)
                if (matched != null) {
                    transform = matched
                    suppressionDepth = 1
                    suppressedEvents += event
                } else {
                    emit(event)
                }
            } else {
                // already buffering a subtree — absorb the nested mark and
                // count it toward balance; select is consulted only at depth 0
                suppressionDepth++
                suppressedEvents += event
            }

            is Text -> if (suppressionDepth == 0) {
                emit(event)
            } else {
                suppressedEvents += event
            }

            is Unmark -> if (suppressionDepth == 0) {
                emit(event)
            } else {
                suppressionDepth--
                suppressedEvents += event
                if (suppressionDepth == 0) {
                    val events = suppressedEvents
                    suppressedEvents = mutableListOf() // defensive new list
                    transform!!.invoke(semanticEventScope, events)
                    transform = null
                }
            }

        }

    }

}

/**
 * Combines several [MarkSelect]s into one by consulting them in iteration order and
 * returning the first non-null [MarkTransform].
 *
 * Use it to feed multiple independent selectors to [transformMatchingMarks] while
 * keeping its single-[select][MarkSelect] contract: the earliest selector that
 * claims a mark wins, so order encodes priority. If no selector matches, the
 * combined function returns `null` and the subtree is forwarded verbatim.
 *
 * The receiver is evaluated lazily on every call, so it must be re-iterable
 * (any static list is fine).
 */
public fun Iterable<MarkSelect>.toFirstMatchSelect(): MarkSelect = { mark ->
    firstNotNullOfOrNull { it(mark) }
}