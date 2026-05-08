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

package com.xemantic.markanywhere.parse

import com.xemantic.kotlin.test.text.chunkedRandomly
import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * GFM tables (§4.10) nested inside list items (§5.2). The two specs compose
 * naturally — a list item is a block container, and a table is a block-level
 * construct — but no numbered example covers the combination.
 *
 * `processListBlock` now performs the same 2-line `|`-then-separator
 * lookahead that `processStart` does, with the buffered header line and the
 * `<table>`/`<tbody>` open state stored on `ListContext` rather than as a
 * dedicated `BlockMode` (so the existing list strip-and-dispatch flow stays
 * intact).
 */
class TableInListItemTest {

    @Test
    fun `table inside list item between paragraphs`() = runTest {
        // given
        val textFlow = /* language=markdown */ """
            - before
            
              | a | b |
              | - | - |
              | 1 | 2 |
            
              after
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"before"
                    }
                    "table" {
                        "thead" {
                            "tr" {
                                "th" { +"a" }
                                "th" { +"b" }
                            }
                        }
                        "tbody" {
                            "tr" {
                                "td" { +"1" }
                                "td" { +"2" }
                            }
                        }
                    }
                    "p" {
                        +"after"
                    }
                }
            }
        }
    }

    @Test
    fun `table directly after list marker`() = runTest {
        // given
        val textFlow = /* language=markdown */ """
            - | a | b |
              | - | - |
              | 1 | 2 |
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "table" {
                        "thead" {
                            "tr" {
                                "th" { +"a" }
                                "th" { +"b" }
                            }
                        }
                        "tbody" {
                            "tr" {
                                "td" { +"1" }
                                "td" { +"2" }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `header-only table inside list item closes on blank line`() = runTest {
        // given
        val textFlow = /* language=markdown */ """
            - before
            
              | a | b |
              | - | - |
            
              after
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then: header-only table — `<tbody>` is never opened.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"before"
                    }
                    "table" {
                        "thead" {
                            "tr" {
                                "th" { +"a" }
                                "th" { +"b" }
                            }
                        }
                    }
                    "p" {
                        +"after"
                    }
                }
            }
        }
    }

    @Test
    fun `pipe-line without separator inside list item is paragraph`() = runTest {
        // given: header line with no following separator — must abort to paragraph.
        val textFlow = /* language=markdown */ """
            - before
            
              | a | b |
              not a separator
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"before"
                    }
                    "p" {
                        +"| a | b |\nnot a separator"
                    }
                }
            }
        }
    }

    @Test
    fun `table with alignments inside list item`() = runTest {
        // given
        val textFlow = /* language=markdown */ """
            - intro
            
              | l | c | r |
              | :-- | :--: | --: |
              | 1 | 2 | 3 |
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"intro" }
                    "table" {
                        "thead" {
                            "tr" {
                                "th"("align" to "left") { +"l" }
                                "th"("align" to "center") { +"c" }
                                "th"("align" to "right") { +"r" }
                            }
                        }
                        "tbody" {
                            "tr" {
                                "td"("align" to "left") { +"1" }
                                "td"("align" to "center") { +"2" }
                                "td"("align" to "right") { +"3" }
                            }
                        }
                    }
                }
            }
        }
    }

}
