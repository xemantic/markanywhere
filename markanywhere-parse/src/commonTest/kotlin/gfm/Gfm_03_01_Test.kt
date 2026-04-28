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
 * Tests for GFM Section 3.1 — Precedence.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#precedence
 *
 * Indicators of block structure always take precedence over
 * indicators of inline structure.
 *
 * See [markanywhere-parse/README.md](../../../README.md) — "Divergences from
 * GFM" — for why this rule is intentionally not honored.
 */
@Suppress("ClassName")
class Gfm_03_01_Test {

    @Test
    fun `example 12 - DIVERGENCE - inline backticks match across block boundaries`() = runTest {
        // given
        val textFlow = buildText {
            +"- `one\n"
            +"- two`\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Streaming-driven divergence: the parser does not buffer inline state
        // across block transitions, so the two backticks pair into a code span
        // that visually spans two list items. Always-loose policy still applies,
        // so item content is wrapped in `<p>`.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        "code" { +"one" }
                    }
                }
                "li" {
                    "p" { +"two`" }
                }
            }
        }
        // CommonMark expected:
        /*
            <ul>
            <li>`one</li>
            <li>two`</li>
            </ul>
         */
    }

}
