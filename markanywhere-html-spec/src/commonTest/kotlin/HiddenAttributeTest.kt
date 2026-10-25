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

package com.xemantic.markanywhere.html.spec

import com.xemantic.kotlin.test.assert
import kotlin.test.Test

class HiddenAttributeTest {

    @Test
    fun `should read until-found in any ASCII case as hidden until found`() {
        // when
        val states = listOf("until-found", "UNTIL-FOUND", "Until-Found").map { isHiddenUntilFound(it) }

        // then
        assert(states == listOf(true, true, true))
    }

    @Test
    fun `should read any other value as the hidden state`() {
        // when - a dotless i folds to an ASCII I under Unicode case folding
        val states = listOf("", "hidden", "until-found ", "untıl-found").map { isHiddenUntilFound(it) }

        // then
        assert(states == listOf(false, false, false, false))
    }

}
