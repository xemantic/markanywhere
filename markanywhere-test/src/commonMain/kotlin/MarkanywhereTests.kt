/*
 * Copyright 2025 Kazimierz Pogoda / Xemantic
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

package com.xemantic.markanywhere.test

import com.xemantic.kotlin.test.sameAs
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.flow.toJsonLines
import kotlinx.coroutines.flow.Flow
import kotlin.test.fail

public suspend infix fun Flow<SemanticEvent>?.sameAs(
    expected: Flow<SemanticEvent>
) {

    if (this == null) {
        fail(
            "The Flow<SemanticEvent> is null, " +
                    "but expected to be: ${expected.toJsonLines()}"
        )
    }

    toJsonLines() sameAs expected.toJsonLines()
}
