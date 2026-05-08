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
 * Tests for GFM Section 06.13 — Soft line breaks.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#soft-line-breaks
 */
@Suppress("ClassName")
class Gfm_06_13_Test {

    @Test
    fun `example 673 - paragraph foo baz`() = runTest {
        // given
        val textFlow = """
            foo
            baz
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo\nbaz"
            }
        }
        // GFM expected:
        /*
            <p>foo
            baz</p>
         */
    }

    @Test
    fun `example 674 - paragraph foo baz`() = runTest {
        // given
        val textFlow = buildText {
            +"foo \n"
            +" baz\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo\nbaz"
            }
        }
        // GFM expected:
        /*
            <p>foo
            baz</p>
         */
    }

}
