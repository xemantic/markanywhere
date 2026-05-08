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

import com.xemantic.kotlin.core.text.buildText
import com.xemantic.kotlin.test.text.chunkedRandomly
import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.parse.parse
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Tests for GFM Section 04.09 — Blank lines.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#blank-lines
 */
@Suppress("ClassName")
class Gfm_04_09_Test {

    @Test
    fun `example 197 - paragraph aaa h1 aaa`() = runTest {
        // given
        val textFlow = buildText {
            +"  \n"
            +"\n"
            +"aaa\n"
            +"  \n"
            +"\n"
            +"# aaa\n"
            +"\n"
            +"  \n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"aaa"
            }
            "h1" {
                +"aaa"
            }
        }
        // GFM expected:
        /*
            <p>aaa</p>
            <h1>aaa</h1>
         */
    }

}
