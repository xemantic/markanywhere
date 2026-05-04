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
 * Supplementary tests for GFM Section 04.06 — HTML blocks.
 *
 * These tests are **not** part of the numbered GFM examples; they cover
 * sub-parse transitions inside type-6/7 HTML blocks (a parser-specific
 * behavior described in `Gfm_04_06_Test`'s divergence notes) that the spec
 * does not exercise directly. Kept in a sibling file so `Gfm_04_06_Test`
 * remains a 1:1 mirror of the GFM example list.
 */
@Suppress("ClassName")
class Gfm_04_06_SupplementalTest {

    // Sub-parsed list inside an HTML block: blank line transitions the `<div>`
    // to sub-parse, the `- one` / `- two` lines open a `<ul><li>...</li></ul>`,
    // and the matching `</div>` close-tag check pops the frame.
    @Test
    fun `sub-parsed list inside div`() = runTest {
        // given
        val textFlow = """
            <div>

            - one
            - two

            </div>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("div") {
                +"\n\n"
                "ul" {
                    "li" {
                        "p" {
                            +"one"
                        }
                    }
                    "li" {
                        "p" {
                            +"two"
                        }
                    }
                }
            }
        }
    }

    // Sub-parsed fenced code block inside `<section>`: the blank line transitions
    // the section frame to sub-parse, the triple-backtick fence opens a
    // `<pre><code>` block whose closing fence emits unmark, and the matching
    // `</section>` close-tag check pops the frame.
    @Test
    fun `sub-parsed fenced code inside section`() = runTest {
        // given
        val textFlow = """
            <section>

            ```
            int x = 1;
            ```

            </section>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("section") {
                +"\n\n"
                "pre" {
                    "code" {
                        +"int x = 1;\n"
                    }
                }
            }
        }
    }

}
