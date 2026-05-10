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
 * Custom markup tags (`<ns:name …>`) nested inside list items (§5.2). Mirrors
 * the architectural pattern of [TableInListItemTest] and [HtmlBlockInListItemTest]:
 * the open-tag name lives on `ListContext.customMarkupTagName` instead of pushing
 * a `BlockMode.CustomMarkup` frame onto `blockModeStack`, so the list dispatcher
 * remains the top-of-stack handler for per-line container-indent stripping.
 *
 * Detection is line-based (whole-line opener, whole-line closer); same DIVERGENCE
 * surface as the top-level `BlockMode.CustomMarkup` (single-line content like
 * `<foo:bar>x</foo:bar>` is not recognised).
 */
class CustomMarkupInListItemTest {

    @Test
    fun `custom markup block between paragraphs in list item`() = runTest {
        // given: the canonical repro from issue #33.
        val textFlow = /* language=markdown */ """
            - intro

              <warning:high level="critical">
              watch out
              </warning:high>

              outro
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"intro" }
                    tag("warning:high", "level" to "critical") {
                        +"watch out"
                    }
                    "p" { +"outro" }
                }
            }
        }
    }

    @Test
    fun `custom markup as first content of list item`() = runTest {
        // given
        val textFlow = /* language=markdown */ """
            - <foo:bar buzz="42">
              hello
              </foo:bar>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    tag("foo:bar", "buzz" to "42") {
                        +"hello"
                    }
                }
            }
        }
    }

    @Test
    fun `multi-line content inside list-item custom markup`() = runTest {
        // given
        val textFlow = /* language=markdown */ """
            - <ns:demo>
              line one
              line two
              line three
              </ns:demo>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then: subsequent content lines are joined with `\n`; no leading `\n`
        // before the first content line (mirrors top-level
        // `customMarkupSkipFirstNewline` behaviour).
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    tag("ns:demo") {
                        +"line one\nline two\nline three"
                    }
                }
            }
        }
    }

    @Test
    fun `custom markup with attributes between siblings`() = runTest {
        // given: each sibling item carries its own custom markup block —
        // verifies that opening on one item and closing on the next properly
        // closes via the sibling-marker close path in `processListBlock`.
        val textFlow = /* language=markdown */ """
            - <a:tag id="1">
              first
              </a:tag>
            - <a:tag id="2">
              second
              </a:tag>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    tag("a:tag", "id" to "1") {
                        +"first"
                    }
                }
                "li" {
                    tag("a:tag", "id" to "2") {
                        +"second"
                    }
                }
            }
        }
    }

    @Test
    fun `unclosed custom markup at end of list block force-closes`() = runTest {
        // given: no `</ns:open>` line — the list block ends and the custom
        // markup must close via `popListContexts → closeListCustomMarkupIfOpen`
        // (mirrors `closeListHtmlBlockIfOpen` on the popListContexts drain).
        val textFlow = /* language=markdown */ """
            - <ns:open>
              dangling
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    tag("ns:open") {
                        +"dangling"
                    }
                }
            }
        }
    }

    @Test
    fun `custom markup in nested list item`() = runTest {
        // given: outer item holds a nested list whose item contains a custom
        // markup block. Verifies that custom markup state on `ListContext` is
        // per-instance — the inner context's tag must not leak to the outer.
        val textFlow = /* language=markdown */ """
            - outer
              - <inner:tag>
                content
                </inner:tag>
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
                            tag("inner:tag") {
                                +"content"
                            }
                        }
                    }
                }
            }
        }
    }
}
