/*
 * Copyright 2025-2026 Kazimierz Pogoda / Xemantic
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

import com.xemantic.kotlin.core.text.trimLastNewLine
import com.xemantic.kotlin.core.text.unaryPlus
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.toJson
import kotlinx.coroutines.flow.Flow

/**
 * Transforms the [Flow] of [SemanticEvent]s to a multiline-string.
 *
 * Note: Each line represents a single event.
 *
 * This function is useful for testing, where produced string can be
 * asserted against expectation string.
 */
public suspend fun Flow<SemanticEvent>.toJsonLines(): String = buildString {
    collect { event ->
        +event.toJson()
        +"\n"
    }
    trimLastNewLine()
}
