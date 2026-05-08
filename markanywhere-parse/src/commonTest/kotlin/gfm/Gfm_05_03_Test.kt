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
 * Tests for GFM Section 05.03 — Task list items (extension).
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#task-list-items-extension-
 */
@Suppress("ClassName")
class Gfm_05_03_Test {

    @Test
    fun `example 279 - DIVERGENCE - ul with 2 items`() = runTest {
        // given
        val textFlow = """
            - [ ] foo
            - [x] bar
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        "input"("disabled" to "", "type" to "checkbox") {}
                        +" foo"
                    }
                }
                "li" {
                    "p" {
                        "input"("checked" to "", "disabled" to "", "type" to "checkbox") {}
                        +" bar"
                    }
                }
            }
        }
        // GFM expected (tight list — no <p> wrappers):
        /*
            <ul>
            <li><input disabled="" type="checkbox"> foo</li>
            <li><input checked="" disabled="" type="checkbox"> bar</li>
            </ul>
         */
        // DIVERGENCE: the parser is append-only and cannot retroactively unwrap
        // <p> elements when a list turns out to be tight, since tight/loose is
        // decided only after the entire list is closed. We always emit <p>
        // wrappers (loose-by-default); the visual collapse to "tight" rendering
        // is delegated to the renderer's stylesheet.
    }

    @Test
    fun `example 280 - DIVERGENCE - ul with 2 items`() = runTest {
        // given
        val textFlow = buildText {
            +"- [x] foo\n"
            +"  - [ ] bar\n"
            +"  - [x] baz\n"
            +"- [ ] bim\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        "input"("checked" to "", "disabled" to "", "type" to "checkbox") {}
                        +" foo"
                    }
                    "ul" {
                        "li" {
                            "p" {
                                "input"("disabled" to "", "type" to "checkbox") {}
                                +" bar"
                            }
                        }
                        "li" {
                            "p" {
                                "input"("checked" to "", "disabled" to "", "type" to "checkbox") {}
                                +" baz"
                            }
                        }
                    }
                }
                "li" {
                    "p" {
                        "input"("disabled" to "", "type" to "checkbox") {}
                        +" bim"
                    }
                }
            }
        }
        // GFM expected (tight list — no <p> wrappers):
        /*
            <ul>
            <li><input checked="" disabled="" type="checkbox"> foo
            <ul>
            <li><input disabled="" type="checkbox"> bar</li>
            <li><input checked="" disabled="" type="checkbox"> baz</li>
            </ul>
            </li>
            <li><input disabled="" type="checkbox"> bim</li>
            </ul>
         */
        // DIVERGENCE: see example 279 — loose-by-default for streaming.
    }

}
