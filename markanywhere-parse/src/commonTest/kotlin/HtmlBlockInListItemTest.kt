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
 * HTML blocks (CommonMark §4.6, types 1–7) nested inside list items (§5.2).
 * The two specs compose naturally — a list item is a block container, an HTML
 * block is a block-level construct — but no numbered example covers the
 * combination directly.
 *
 * Implementation lives on `ListContext.htmlBlock` instead of pushing an
 * HtmlBlock frame onto `blockModeStack`, so the list dispatcher remains the
 * top-of-stack handler for per-line container-indent stripping (same
 * architectural pattern as `tableHeaderPending`/`tableOpen`).
 *
 * DIVERGENCE: a blank line inside a list-internal type-6/7 HTML block emits
 * `\n` and stays in raw-text streaming mode (no sub-parse). At top level the
 * same blank line would push a fresh `Start` frame above the HTML frame so
 * inner content parses as Markdown — but inside a list that would knock
 * `ListBlock` off the top of the dispatch stack, breaking indent stripping.
 */
class HtmlBlockInListItemTest {

    @Test
    fun `type 6 div block between paragraphs in list item`() = runTest {
        // given: the canonical repro from issue #31.
        val textFlow = /* language=markdown */ """
            - before

              <div class="info">
              inside the div
              </div>

              after
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"before" }
                    tag("div", "class" to "info") {
                        +"\ninside the div\n"
                    }
                    "p" { +"after" }
                }
            }
        }
    }

    @Test
    fun `type 6 div block as first content of list item`() = runTest {
        // given
        val textFlow = /* language=markdown */ """
            - <div>
              inside
              </div>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    tag("div") {
                        +"\ninside\n"
                    }
                }
            }
        }
    }

    @Test
    fun `type 1 pre block inside list item DIVERGENCE`() = runTest {
        // given
        val textFlow = /* language=markdown */ """
            - intro

              <pre>
              raw code
              </pre>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then: DIVERGENCE — same as top-level type 1: no leading `\n` after the
        // opener (the `\n` after `<pre>` is consumed by the first-line parser
        // without emitting it as content).
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"intro" }
                    tag("pre") {
                        +"raw code\n"
                    }
                }
            }
        }
    }

    @Test
    fun `type 2 comment inside list item`() = runTest {
        // given
        val textFlow = /* language=markdown */ """
            - intro

              <!-- a comment -->

              after
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then: type 2-5 emits content as raw text (no mark/unmark pair).
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"intro" }
                    +"<!-- a comment -->\n"
                    "p" { +"after" }
                }
            }
        }
    }

    @Test
    fun `type 7 custom tag inside list item`() = runTest {
        // given: a custom (non-block-level) tag alone on a line opens a type-7
        // block — interrupts paragraphs only when at a block boundary.
        val textFlow = /* language=markdown */ """
            - intro

              <warning>
              danger
              </warning>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then: non-HTML5 tag preserves source casing.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"intro" }
                    tag("warning") {
                        +"\ndanger\n"
                    }
                }
            }
        }
    }

    @Test
    fun `nested tags inside type 6 block in list item`() = runTest {
        // given: nested `<p>` inside a `<div>` — both produce HTML-tagged
        // marks (no Markdown reinterpretation in raw-text mode).
        val textFlow = /* language=markdown */ """
            - intro

              <div>
              <p>nested</p>
              </div>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"intro" }
                    tag("div") {
                        +"\n"
                        tag("p") { +"nested" }
                        +"\n"
                    }
                }
            }
        }
    }

    @Test
    fun `html block inside nested list item`() = runTest {
        // given: outer item holds a single nested list whose item contains an
        // HTML block. Verifies htmlBlock state on `ListContext` is per-instance.
        val textFlow = /* language=markdown */ """
            - outer
              - <div>
                inner
                </div>
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
                            tag("div") {
                                +"\ninner\n"
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `html block in ordered list item`() = runTest {
        // given
        val textFlow = /* language=markdown */ """
            1. before

               <div>
               inside
               </div>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol" {
                "li" {
                    "p" { +"before" }
                    tag("div") {
                        +"\ninside\n"
                    }
                }
            }
        }
    }

    @Test
    fun `unclosed html block force-closes when list item closes`() = runTest {
        // given: `<div>` opens but never closes. The list-block close
        // (popListContexts) must drain the openTags so the event stream stays
        // balanced. The opener line was already emitted; the `unmark("div")`
        // is added at item close.
        val textFlow = /* language=markdown */ """
            - intro

              <div>
              inside
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"intro" }
                    tag("div") {
                        +"\ninside\n"
                    }
                }
            }
        }
    }

    @Test
    fun `blank line inside list-internal html block stays in raw mode DIVERGENCE`() = runTest {
        // given: a blank line inside a `<div>` block. At top level this would
        // transition the block to sub-parse mode and the `- nested` line would
        // open a Markdown list. Inside a list item we stay in raw-text mode
        // and emit the `- nested` text literally.
        val textFlow = /* language=markdown */ """
            - intro

              <div>

              - nested

              </div>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"intro" }
                    tag("div") {
                        +"\n\n- nested\n\n"
                    }
                }
            }
        }
    }

    @Test
    fun `marker-shaped and thematic-break content inside html block streams as raw text`() = runTest {
        // given: a `- foo` line and a `---` line inside an open `<div>` —
        // both shapes that would otherwise open a sibling list / end the
        // current list. Exercises the early-out in `processListBlock`.
        val textFlow = /* language=markdown */ """
            - intro

              <div>
              - not a new list item
              ---
              </div>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"intro" }
                    tag("div") {
                        +"\n- not a new list item\n---\n"
                    }
                }
            }
        }
    }

    @Test
    fun `under-indented line force-closes html block and ends list`() = runTest {
        // given: the `tail` line sits at column 0, below the item's content
        // column. The HTML block force-closes (events stay balanced) and the
        // line replays at top level as its own paragraph.
        val textFlow = /* language=markdown */ """
            - intro

              <div>
              inside
            tail
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"intro" }
                    tag("div") {
                        +"\ninside\n"
                    }
                }
            }
            "p" { +"tail" }
        }
    }

    @Test
    fun `type 3 processing instruction inside list item`() = runTest {
        // given
        val textFlow = /* language=markdown */ """
            - intro

              <?php echo 1; ?>

              after
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then: type 2-5 emits raw text, no mark/unmark pair.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"intro" }
                    +"<?php echo 1; ?>\n"
                    "p" { +"after" }
                }
            }
        }
    }

    @Test
    fun `type 4 declaration inside list item`() = runTest {
        // given
        val textFlow = /* language=markdown */ """
            - intro

              <!DOCTYPE html>

              after
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"intro" }
                    tag("doctype") {
                        +"html"
                    }
                    "p" { +"after" }
                }
            }
        }
    }

    @Test
    fun `type 1 pre block with multi-line opener inside list item DIVERGENCE`() = runTest {
        // given: `<pre` on the marker line, attribute on the next line, `>`
        // closes the opener. Exercises the buffered-opener path in the type-1
        // branch of `streamListHtmlBlockLine` — `firstLineBuffer` accumulates
        // across calls until `tryFinishListHtmlBlock1FirstLine` finds the `>`.
        val textFlow = /* language=markdown */ """
            - <pre
              class="x">
              body
              </pre>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then: DIVERGENCE — same as top-level type 1: no leading `\n` after
        // the opener (the `\n` between `<pre` and `class="x">` is consumed by
        // the first-line parser and not emitted as content).
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    tag("pre", "class" to "x") {
                        +"body\n"
                    }
                }
            }
        }
    }

    @Test
    fun `multiple consecutive html blocks in same list item`() = runTest {
        // given: two type-6 blocks separated by a blank line. After the first
        // `</div>` closes (`ctx.htmlBlock = null`), the second `<div>` opener
        // at the next block boundary must enter a fresh `ListHtmlBlockState`.
        val textFlow = /* language=markdown */ """
            - <div>
              first
              </div>

              <div>
              second
              </div>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    tag("div") {
                        +"\nfirst\n"
                    }
                    tag("div") {
                        +"\nsecond\n"
                    }
                }
            }
        }
    }

    @Test
    fun `type 5 CDATA inside list item`() = runTest {
        // given: closing `]]>` on the second content line — exercises the
        // multi-line streaming path through `streamListHtmlBlockLine` for
        // type 2-5 blocks.
        val textFlow = /* language=markdown */ """
            - intro

              <![CDATA[
              raw bytes
              ]]>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"intro" }
                    +"<![CDATA[\nraw bytes\n]]>\n"
                }
            }
        }
    }

}
