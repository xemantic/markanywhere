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
 * Tests for GFM Section 06.05 — Strikethrough (extension).
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#strikethrough-extension-
 */
@Suppress("ClassName")
class Gfm_06_05_Test {

    @Test
    fun `example 491 - paragraph Hi Hello, there world!`() = runTest {
        // given
        val textFlow = "~~Hi~~ Hello, ~there~ world!".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "del" {
                    +"Hi"
                }
                +" Hello, "
                "del" {
                    +"there"
                }
                +" world!"
            }
        }
        // GFM expected:
        /*
            <p><del>Hi</del> Hello, <del>there</del> world!</p>
         */
    }

    @Test
    fun `example 492 - DIVERGENCE - unclosed strikethrough force-closes at paragraph end`() = runTest {
        // given
        val textFlow = """
            This ~~has a

            new paragraph~~.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"This "
                "del" {
                    +"has a"
                }
            }
            "p" {
                +"new paragraph"
                "del" {
                    +"."
                }
            }
        }
        // GFM expected:
        /*
            <p>This ~~has a</p>
            <p>new paragraph~~.</p>
         */
        // DIVERGENCE: strikethrough is not flanking-aware and force-closes at
        // every block/line boundary in `flushInline` (same streaming-friendly
        // policy as inline code spans). Replaying the opener as literal would
        // require buffering the whole content past the next emitted event,
        // breaking the typewriter UX.
    }

    @Test
    fun `example 493 - paragraph This will ~~~not~~~ str`() = runTest {
        // given
        val textFlow = "This will ~~~not~~~ strike.".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"This will ~~~not~~~ strike."
            }
        }
        // GFM expected:
        /*
            <p>This will ~~~not~~~ strike.</p>
         */
    }

}
