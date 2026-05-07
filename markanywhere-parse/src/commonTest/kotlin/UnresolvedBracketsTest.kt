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
 * Regression tests for unresolved-bracket content preservation.
 *
 * The parser opens link/image state on `[` / `![` and buffers the label until
 * a closing `](url)` resolves the construct. If the construct never resolves
 * — because the next char after `]` is not `(`, or because a block boundary
 * (newline-flush, paragraph close, EOF) interrupts the parse — the buffered
 * label must be **replayed as literal text**. A previous version of the
 * parser silently dropped the label, so `[foo]: /url` emitted only `]` and
 * `[unmatched` emitted nothing.
 *
 * These tests pin down the abort behavior across link-adjacent contexts so
 * future changes to inline parsing (delimiter-stack rework, real reference
 * link support, etc.) don't reintroduce the content-loss regression.
 *
 * Companion to:
 * - [com.xemantic.markanywhere.parse.gfm.Gfm_04_07_Test] — link reference
 *   definitions (DIVERGENCE: not supported, all flow as literal text).
 * - [com.xemantic.markanywhere.parse.gfm.Gfm_06_06_Test] — inline links
 *   (happy path: `[text](url)` resolves to `<a>`).
 * - [com.xemantic.markanywhere.parse.gfm.Gfm_06_07_Test] — images
 *   (happy path: `![alt](src)` resolves to `<img>`).
 */
class UnresolvedBracketsTest {

    @Test
    fun `bare opening bracket with no close`() = runTest {
        // given
        val textFlow = "before [foo and after".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"before [foo and after"
            }
        }
    }

    @Test
    fun `bracket label without follow-up`() = runTest {
        // given
        val textFlow = "see [section] for details".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"see [section] for details"
            }
        }
    }

    @Test
    fun `bracket label followed by colon - link reference shape`() = runTest {
        // given
        val textFlow = "[foo]: /url".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Link reference definitions (CommonMark §4.7) are recognized at block
        // boundary and consumed silently — the definition produces no events
        // because it has no visible content; subsequent `[foo]` usages would
        // resolve against the registered href.
        parsed.mergeAdjacentText() sameAs semanticEvents {}
    }

    @Test
    fun `bracket label followed by another bracket - reference link shape`() = runTest {
        // given
        val textFlow = "[text][ref]".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Reference-style links are not supported (DIVERGENCE — see README).
        // Both labels flow as literal text rather than resolving via a
        // (non-existent) reference table.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[text][ref]"
            }
        }
    }

    @Test
    fun `unmatched bracket interrupted by newline`() = runTest {
        // given
        val textFlow = """
            [foo
            bar
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo\nbar"
            }
        }
    }

    @Test
    fun `bracket label closed at end of paragraph`() = runTest {
        // given
        val textFlow = """
            [unresolved]

            next paragraph
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[unresolved]"
            }
            "p" {
                +"next paragraph"
            }
        }
    }

    @Test
    fun `unresolved bracket inside ATX heading`() = runTest {
        // given
        val textFlow = "# Heading with [unmatched".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h1" {
                +"Heading with [unmatched"
            }
        }
    }

    @Test
    fun `unresolved bracket label inside ATX heading`() = runTest {
        // given
        val textFlow = "## See [section]".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h2" {
                +"See [section]"
            }
        }
    }

    @Test
    fun `unresolved bracket inside blockquote`() = runTest {
        // given
        val textFlow = "> quote with [unmatched bracket".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "p" {
                    +"quote with [unmatched bracket"
                }
            }
        }
    }

    @Test
    fun `unresolved image label without follow-up`() = runTest {
        // given
        val textFlow = "see ![alt text] for details".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"see ![alt text] for details"
            }
        }
    }

    @Test
    fun `unmatched image opening with no close`() = runTest {
        // given
        val textFlow = "before ![alt and after".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"before ![alt and after"
            }
        }
    }

    @Test
    fun `unresolved image at end of paragraph`() = runTest {
        // given
        val textFlow = """
            ![unresolved]

            next paragraph
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"![unresolved]"
            }
            "p" {
                +"next paragraph"
            }
        }
    }

    @Test
    fun `mixed resolved and unresolved brackets in same paragraph`() = runTest {
        // given
        val textFlow = "see [docs](/docs) and [other] references".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"see "
                "a"("href" to "/docs") {
                    +"docs"
                }
                +" and [other] references"
            }
        }
    }

    @Test
    fun `inline link preceded by unresolved bracket`() = runTest {
        // given
        val textFlow = "[unresolved] then [link](/url)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[unresolved] then "
                "a"("href" to "/url") {
                    +"link"
                }
            }
        }
    }

    @Test
    fun `unresolved bracket interrupted by paragraph end then link in next paragraph`() = runTest {
        // given
        val textFlow = """
            opening [foo

            [resolved](/url) here
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // The `[foo` from the first paragraph must not bleed into the second
        // paragraph's link parse: flushInline at the blank line aborts the
        // open link state and replays `[foo` as text.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"opening [foo"
            }
            "p" {
                "a"("href" to "/url") {
                    +"resolved"
                }
                +" here"
            }
        }
    }

    @Test
    fun `escaped opening bracket does not start link`() = runTest {
        // given
        val textFlow = """\[not a link]""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[not a link]"
            }
        }
    }

    @Test
    fun `inline link with empty url still works after fix`() = runTest {
        // given
        val textFlow = "[label]()".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "") {
                    +"label"
                }
            }
        }
    }

    @Test
    fun `inline link happy path still works after fix`() = runTest {
        // given
        val textFlow = "[click here](https://example.com)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "https://example.com") {
                    +"click here"
                }
            }
        }
    }

}
