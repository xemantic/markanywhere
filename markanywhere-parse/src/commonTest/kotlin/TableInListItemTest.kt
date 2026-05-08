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
    fun `back-to-back pipe lines without separator merge into one paragraph`() = runTest {
        // given: the second `|`-line is not a valid separator. Top-level
        // `processStart` flushes both lines as a single `<p>` (suppressed
        // table re-detection); the list path achieves the same result via
        // `lineInterruptsParagraph` returning false for `|`-lines, so the
        // replayed second line falls through to lazy-paragraph continuation
        // rather than re-entering table detection.
        val textFlow = /* language=markdown */ """
            - before

              | a | b |
              | x | y |
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
                        +"| a | b |\n| x | y |"
                    }
                }
            }
        }
    }

    @Test
    fun `pending table header drains as paragraph at end of list block`() = runTest {
        // given: header line followed by EOF — no second line to confirm the
        // separator. Exercises the `popListContexts` drain path (vs. the
        // pending-resolution drain path covered by the abort test above).
        val textFlow = /* language=markdown */ """
            - before

              | a | b |
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
                        +"| a | b |"
                    }
                }
            }
        }
    }

    @Test
    fun `table inside nested list item`() = runTest {
        // given: outer item holds a single nested list whose item contains a
        // table. Verifies that table state on `ListContext` is per-instance —
        // the inner context's `tableHeaderPending`/`tableOpen` must not leak
        // to the outer context.
        val textFlow = /* language=markdown */ """
            - outer
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
                    "p" { +"outer" }
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
        }
    }

    @Test
    fun `ATX heading inside list item closes an open table`() = runTest {
        // given: a paragraph-interrupter (`## …`) arriving while a table is
        // open closes the table and emits the heading inside the same `<li>`.
        // Exercises the `endsTable` fall-through in `processListBlock`'s
        // `tableOpen` branch.
        val textFlow = /* language=markdown */ """
            - intro

              | a | b |
              | - | - |
              | 1 | 2 |
              ## After table
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
                    "h2" { +"After table" }
                }
            }
        }
    }

    @Test
    fun `ordered list item contains a table`() = runTest {
        // given: same shape as the unordered case but with an ordered list —
        // verifies the table state on `ListContext` is independent of the
        // marker style.
        val textFlow = /* language=markdown */ """
            1. before

               | a | b |
               | - | - |
               | 1 | 2 |
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol" {
                "li" {
                    "p" { +"before" }
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
