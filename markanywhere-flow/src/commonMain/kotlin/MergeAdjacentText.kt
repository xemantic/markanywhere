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
 * Merges runs of adjacent [SemanticEvent.Text] events into a single [SemanticEvent.Text].
 *
 * A [SemanticEvent.Mark] or [SemanticEvent.Unmark] terminates the current run, so text
 * fragments separated by marks are never merged across them.
 */
public fun Flow<SemanticEvent>.mergeAdjacentText(): Flow<SemanticEvent> = flow {
    val buffer = StringBuilder()
    collect { event ->
        if (event is Text) {
            buffer.append(event.text)
        } else {
            if (buffer.isNotEmpty()) {
                emit(SemanticEvent.Text(buffer.toString()))
                buffer.clear()
            }
            emit(event)
        }
    }
    if (buffer.isNotEmpty()) {
        emit(SemanticEvent.Text(buffer.toString()))
    }
}
