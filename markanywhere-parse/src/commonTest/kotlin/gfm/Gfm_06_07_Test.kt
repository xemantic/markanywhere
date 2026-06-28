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

import com.xemantic.kotlin.core.text.unaryPlus
import com.xemantic.kotlin.test.text.chunkedRandomly
import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.parse.parse
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Tests for GFM Section 06.07 — Images.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#images
 */
@Suppress("ClassName")
class Gfm_06_07_Test {

    @Test
    fun `example 581 - empty paragraph`() = runTest {
        // given
        val textFlow = """![foo](/url "title")""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "img"("src" to "/url", "alt" to "foo", "title" to "title") {}
            }
        }
        // GFM expected:
        /*
            <p><img src="/url" alt="foo" title="title" /></p>
         */
    }

    @Test
    fun `example 582 - DIVERGENCE - forward ref shortcut image with em label`() = runTest {
        // given
        val textFlow = """
            ![foo *bar*]

            [foo *bar*]: train.jpg "train & tracks"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Forward reference DIVERGENCE: definition appears after usage; the
        // append-only stream emits the usage as literal text before the def
        // is registered. The trailing `*` in closing position closes the
        // label-local em (via `closeLabelLocalEmphasisRun`).
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"![foo "
                "em" {
                    +"bar"
                }
                +"]"
            }
        }
        // GFM expected:
        /*
            <p><img src="train.jpg" alt="foo bar" title="train &amp; tracks" /></p>
         */
    }

    @Test
    fun `example 583 - DIVERGENCE - nested image inside image`() = runTest {
        // given
        val textFlow = "![foo ![bar](/url)](/url2)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Nested-image DIVERGENCE: the inner `![bar](/url)` is treated as
        // content of the outer label by the depth-counter (CLAUDE.md:
        // "Image-inside-link not supported. Same fix as nested link parsing
        // — speculative recursion."). The outer image commits with the raw
        // inner source as alt text.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "img"("src" to "/url2", "alt" to "foo ![bar](/url)") {}
            }
        }
        // GFM expected:
        /*
            <p><img src="/url2" alt="foo bar" /></p>
         */
    }

    @Test
    fun `example 584 - DIVERGENCE - nested link inside image`() = runTest {
        // given
        val textFlow = "![foo [bar](/url)](/url2)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Nested-link-in-image DIVERGENCE: the inner `[bar](/url)` is treated
        // as content of the outer label by the depth-counter (same constraint
        // as ex 583). The outer image commits with the raw inner source as
        // alt text.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "img"("src" to "/url2", "alt" to "foo [bar](/url)") {}
            }
        }
        // GFM expected:
        /*
            <p><img src="/url2" alt="foo bar" /></p>
         */
    }

    @Test
    fun `example 585 - DIVERGENCE - forward ref collapsed image with em label`() = runTest {
        // given
        val textFlow = """
            ![foo *bar*][]

            [foo *bar*]: train.jpg "train & tracks"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Forward reference DIVERGENCE: see ex 582. Trailing `[]` after the
        // label is then literal too since the speculative collapsed-ref form
        // also has no def to resolve against.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"![foo "
                "em" {
                    +"bar"
                }
                +"][]"
            }
        }
        // GFM expected:
        /*
            <p><img src="train.jpg" alt="foo bar" title="train &amp; tracks" /></p>
         */
    }

    @Test
    fun `example 586 - DIVERGENCE - forward ref full image case-insensitive label`() = runTest {
        // given
        val textFlow = """
            ![foo *bar*][foobar]

            [FOOBAR]: train.jpg "train & tracks"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Forward reference DIVERGENCE: see ex 582.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"![foo "
                "em" {
                    +"bar"
                }
                +"][foobar]"
            }
        }
        // GFM expected:
        /*
            <p><img src="train.jpg" alt="foo bar" title="train &amp; tracks" /></p>
         */
    }

    @Test
    fun `example 587 - empty paragraph`() = runTest {
        // given
        val textFlow = "![foo](train.jpg)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "img"("src" to "train.jpg", "alt" to "foo") {}
            }
        }
        // GFM expected:
        /*
            <p><img src="train.jpg" alt="foo" /></p>
         */
    }

    @Test
    fun `example 588 - paragraph My`() = runTest {
        // given
        val textFlow = """My ![foo bar](/path/to/train.jpg  "title"   )""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"My "
                "img"("src" to "/path/to/train.jpg", "alt" to "foo bar", "title" to "title") {}
            }
        }
        // GFM expected:
        /*
            <p>My <img src="/path/to/train.jpg" alt="foo bar" title="title" /></p>
         */
    }

    @Test
    fun `example 589 - empty paragraph`() = runTest {
        // given
        val textFlow = "![foo](<url>)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "img"("src" to "url", "alt" to "foo") {}
            }
        }
        // GFM expected:
        /*
            <p><img src="url" alt="foo" /></p>
         */
    }

    @Test
    fun `example 590 - empty paragraph`() = runTest {
        // given
        val textFlow = "![](/url)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "img"("src" to "/url", "alt" to "") {}
            }
        }
        // GFM expected:
        /*
            <p><img src="/url" alt="" /></p>
         */
    }

    @Test
    fun `example 591 - DIVERGENCE - forward ref full image`() = runTest {
        // given
        val textFlow = """
            ![foo][bar]

            [bar]: /url
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Forward reference DIVERGENCE: see ex 582.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"![foo][bar]"
            }
        }
        // GFM expected:
        /*
            <p><img src="/url" alt="foo" /></p>
         */
    }

    @Test
    fun `example 592 - DIVERGENCE - forward ref full image case-insensitive`() = runTest {
        // given
        val textFlow = """
            ![foo][bar]

            [BAR]: /url
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Forward reference DIVERGENCE: see ex 582.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"![foo][bar]"
            }
        }
        // GFM expected:
        /*
            <p><img src="/url" alt="foo" /></p>
         */
    }

    @Test
    fun `example 593 - DIVERGENCE - forward ref collapsed image`() = runTest {
        // given
        val textFlow = """
            ![foo][]

            [foo]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Forward reference DIVERGENCE: see ex 582.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"![foo][]"
            }
        }
        // GFM expected:
        /*
            <p><img src="/url" alt="foo" title="title" /></p>
         */
    }

    @Test
    fun `example 594 - DIVERGENCE - forward ref collapsed image em label`() = runTest {
        // given
        val textFlow = """
            ![*foo* bar][]

            [*foo* bar]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Forward reference DIVERGENCE: see ex 582.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"!["
                "em" {
                    +"foo"
                }
                +" bar][]"
            }
        }
        // GFM expected:
        /*
            <p><img src="/url" alt="foo bar" title="title" /></p>
         */
    }

    @Test
    fun `example 595 - DIVERGENCE - forward ref collapsed image case-insensitive`() = runTest {
        // given
        val textFlow = """
            ![Foo][]

            [foo]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Forward reference DIVERGENCE: see ex 582.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"![Foo][]"
            }
        }
        // GFM expected:
        /*
            <p><img src="/url" alt="Foo" title="title" /></p>
         */
    }

    @Test
    fun `example 596 - DIVERGENCE - forward ref multi-line collapsed image`() = runTest {
        // given
        val textFlow = buildString {
            +"![foo] \n"
            +"[]\n"
            +"\n"
            +"[foo]: /url \"title\"\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Forward reference DIVERGENCE (see ex 582) compounded by multi-line
        // label DIVERGENCE: `flushInline` closes inline state at every line
        // break, so the speculative `![foo] ` aborts before the next-line
        // `[]` can collapse-combine with it.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"![foo]\n[]"
            }
        }
        // GFM expected:
        /*
            <p><img src="/url" alt="foo" title="title" />
            []</p>
         */
    }

    @Test
    fun `example 597 - DIVERGENCE - forward ref shortcut image`() = runTest {
        // given
        val textFlow = """
            ![foo]

            [foo]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Forward reference DIVERGENCE: see ex 582.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"![foo]"
            }
        }
        // GFM expected:
        /*
            <p><img src="/url" alt="foo" title="title" /></p>
         */
    }

    @Test
    fun `example 598 - DIVERGENCE - forward ref shortcut image em label`() = runTest {
        // given
        val textFlow = """
            ![*foo* bar]

            [*foo* bar]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Forward reference DIVERGENCE: see ex 582.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"!["
                "em" {
                    +"foo"
                }
                +" bar]"
            }
        }
        // GFM expected:
        /*
            <p><img src="/url" alt="foo bar" title="title" /></p>
         */
    }

    @Test
    fun `example 599 - paragraph !foo paragraph foo url title`() = runTest {
        // given
        val textFlow = """
            ![[foo]]

            [[foo]]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"![[foo]]"
            }
            "p" {
                +"[[foo]]: /url \"title\""
            }
        }
        // GFM expected:
        /*
            <p>![[foo]]</p>
            <p>[[foo]]: /url &quot;title&quot;</p>
         */
    }

    @Test
    fun `example 600 - DIVERGENCE - forward ref shortcut image case-insensitive`() = runTest {
        // given
        val textFlow = """
            ![Foo]

            [foo]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Forward reference DIVERGENCE: see ex 582.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"![Foo]"
            }
        }
        // GFM expected:
        /*
            <p><img src="/url" alt="Foo" title="title" /></p>
         */
    }

    @Test
    fun `example 601 - paragraph !foo`() = runTest {
        // given
        val textFlow = """
            !\[foo]

            [foo]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"![foo]"
            }
        }
        // GFM expected:
        /*
            <p>![foo]</p>
         */
    }

    @Test
    fun `example 602 - DIVERGENCE - forward ref escape-disabled image becomes link`() = runTest {
        // given
        val textFlow = """
            \![foo]

            [foo]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Forward reference DIVERGENCE: the `\!` correctly escapes the image
        // marker, but the residual `[foo]` shortcut link reference still
        // can't resolve a forward def (see ex 582). Escape itself works —
        // the `\` is consumed, leaving `![foo]` as the captured source.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"![foo]"
            }
        }
        // GFM expected:
        /*
            <p>!<a href="/url" title="title">foo</a></p>
         */
    }

}
