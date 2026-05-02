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

package com.xemantic.markanywhere.parse.gfm

import com.xemantic.kotlin.test.text.chunkedRandomly
import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.parse.parse
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Tests for GFM Section 06.14 — Textual content.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#textual-content
 */
@Suppress("ClassName")
class Gfm_06_14_Test {

    // TODO review
    @Test
    fun `example 675 - paragraph hello $'there`() = runTest {
        // given
        val textFlow = "hello $.;'there\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"hello $.;'there"
            }
        }
        // GFM expected:
        /*
            <p>hello $.;'there</p>
         */
    }

    @Test
    fun `example 676 - paragraph Foo χρῆν`() = runTest {
        // given
        val textFlow = "Foo χρῆν".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo χρῆν"
            }
        }
        // GFM expected:
        /*
            <p>Foo χρῆν</p>
         */
    }

    @Test
    fun `example 677 - paragraph Multiple spaces`() = runTest {
        // given
        val textFlow = "Multiple     spaces".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Multiple     spaces"
            }
        }
        // GFM expected:
        /*
            <p>Multiple     spaces</p>
         */
    }

}
