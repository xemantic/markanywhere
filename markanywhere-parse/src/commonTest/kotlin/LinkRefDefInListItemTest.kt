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
 * Link reference definitions (CommonMark §4.7) nested inside list items
 * (§5.2). The two specs compose naturally — a list item is a block container,
 * a link reference definition is a block-level construct — but no numbered
 * GFM example covers the combination directly.
 *
 * Detection mirrors the top-level path in `processStart`: at an item block
 * boundary, a single-line `[label]: dest "title"` shape is registered in
 * `linkDefinitions` and consumed silently (no events). Same streaming
 * divergences apply — multi-line shapes and forward-reference resolution
 * are not supported (see `Gfm_04_07_Test` for the top-level versions).
 */
class LinkRefDefInListItemTest {

    @Test
    fun `definition between paragraphs in list item resolves a later use`() = runTest {
        // given: canonical repro from issue #34.
        val textFlow = /* language=markdown */ """
            - intro

              [foo]: /url "title"

              See [foo].
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"intro" }
                    "p" {
                        +"See "
                        "a"("href" to "/url", "title" to "title") {
                            +"foo"
                        }
                        +"."
                    }
                }
            }
        }
    }

    @Test
    fun `definition between paragraphs in list item without title`() = runTest {
        // given
        val textFlow = /* language=markdown */ """
            - intro

              [foo]: /url

              See [foo].
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"intro" }
                    "p" {
                        +"See "
                        "a"("href" to "/url") {
                            +"foo"
                        }
                        +"."
                    }
                }
            }
        }
    }

    @Test
    fun `multiple definitions and references inside a list item`() = runTest {
        // given
        val textFlow = /* language=markdown */ """
            - heading

              [foo]: /foo-url "foo"
              [bar]: /bar-url "bar"

              [foo] and [bar].
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"heading" }
                    "p" {
                        "a"("href" to "/foo-url", "title" to "foo") { +"foo" }
                        +" and "
                        "a"("href" to "/bar-url", "title" to "bar") { +"bar" }
                        +"."
                    }
                }
            }
        }
    }

    @Test
    fun `DIVERGENCE - forward reference inside list item does not resolve`() = runTest {
        // given: usage precedes the definition — same append-only constraint as
        // the top-level forward-reference DIVERGENCE (Gfm_04_07_Test example 172).
        val textFlow = /* language=markdown */ """
            - See [foo].

              [foo]: /url
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"See [foo]."
                    }
                }
            }
        }
    }

    @Test
    fun `definition on the line directly following intro joins paragraph`() = runTest {
        // given: no blank line between intro and the definition-shaped line —
        // the line is a paragraph continuation (soft break), not a definition,
        // matching the top-level GFM rule that definitions only apply at a
        // fresh block boundary.
        val textFlow = /* language=markdown */ """
            - intro
              [foo]: /url

              See [foo].
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"intro\n[foo]: /url"
                    }
                    "p" {
                        +"See [foo]."
                    }
                }
            }
        }
    }

    @Test
    fun `definition as first content of list item resolves a later use`() = runTest {
        // given: the definition is the marker line itself — no intro paragraph
        // before it. Routed via `emitItemFirstLine` rather than the in-item
        // `atBlockBoundary` branch.
        val textFlow = /* language=markdown */ """
            - [foo]: /url "title"

              See [foo].
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"See "
                        "a"("href" to "/url", "title" to "title") {
                            +"foo"
                        }
                        +"."
                    }
                }
            }
        }
    }

    @Test
    fun `definition-only list item produces an empty li`() = runTest {
        // given: a list item whose only content is a link reference definition.
        // GFM consumes the def silently — the `<li>` exists but has no visible
        // content.
        val textFlow = /* language=markdown */ """
            - [foo]: /url
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {}
            }
        }
    }

    @Test
    fun `definition in nested list item resolves use within same item`() = runTest {
        // given
        val textFlow = /* language=markdown */ """
            - outer
              - inner

                [foo]: /url "title"

                See [foo].
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
                            "p" { +"inner" }
                            "p" {
                                +"See "
                                "a"("href" to "/url", "title" to "title") {
                                    +"foo"
                                }
                                +"."
                            }
                        }
                    }
                }
            }
        }
    }

}
