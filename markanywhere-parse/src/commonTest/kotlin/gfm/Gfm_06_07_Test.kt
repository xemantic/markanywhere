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
 * Tests for GFM Section 06.07 — Images.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#images
 */
@Suppress("ClassName")
class Gfm_06_07_Test {

    // TODO review
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

    // TODO review
    @Test
    fun `example 582 - empty paragraph`() = runTest {
        // given
        val textFlow = """
            ![foo *bar*]

            [foo *bar*]: train.jpg "train & tracks"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "img"("src" to "train.jpg", "alt" to "foo bar", "title" to "train & tracks") {}
            }
        }
        // GFM expected:
        /*
            <p><img src="train.jpg" alt="foo bar" title="train &amp; tracks" /></p>
         */
    }

    // TODO review
    @Test
    fun `example 583 - empty paragraph`() = runTest {
        // given
        val textFlow = "![foo ![bar](/url)](/url2)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "img"("src" to "/url2", "alt" to "foo bar") {}
            }
        }
        // GFM expected:
        /*
            <p><img src="/url2" alt="foo bar" /></p>
         */
    }

    // TODO review
    @Test
    fun `example 584 - empty paragraph`() = runTest {
        // given
        val textFlow = "![foo [bar](/url)](/url2)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "img"("src" to "/url2", "alt" to "foo bar") {}
            }
        }
        // GFM expected:
        /*
            <p><img src="/url2" alt="foo bar" /></p>
         */
    }

    // TODO review
    @Test
    fun `example 585 - empty paragraph`() = runTest {
        // given
        val textFlow = """
            ![foo *bar*][]

            [foo *bar*]: train.jpg "train & tracks"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "img"("src" to "train.jpg", "alt" to "foo bar", "title" to "train & tracks") {}
            }
        }
        // GFM expected:
        /*
            <p><img src="train.jpg" alt="foo bar" title="train &amp; tracks" /></p>
         */
    }

    // TODO review
    @Test
    fun `example 586 - empty paragraph`() = runTest {
        // given
        val textFlow = """
            ![foo *bar*][foobar]

            [FOOBAR]: train.jpg "train & tracks"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "img"("src" to "train.jpg", "alt" to "foo bar", "title" to "train & tracks") {}
            }
        }
        // GFM expected:
        /*
            <p><img src="train.jpg" alt="foo bar" title="train &amp; tracks" /></p>
         */
    }

    // TODO review
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

    // TODO review
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

    // TODO review
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

    // TODO review
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

    // TODO review
    @Test
    fun `example 591 - empty paragraph`() = runTest {
        // given
        val textFlow = """
            ![foo][bar]

            [bar]: /url
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "img"("src" to "/url", "alt" to "foo") {}
            }
        }
        // GFM expected:
        /*
            <p><img src="/url" alt="foo" /></p>
         */
    }

    // TODO review
    @Test
    fun `example 592 - empty paragraph`() = runTest {
        // given
        val textFlow = """
            ![foo][bar]

            [BAR]: /url
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "img"("src" to "/url", "alt" to "foo") {}
            }
        }
        // GFM expected:
        /*
            <p><img src="/url" alt="foo" /></p>
         */
    }

    // TODO review
    @Test
    fun `example 593 - empty paragraph`() = runTest {
        // given
        val textFlow = """
            ![foo][]

            [foo]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

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

    // TODO review
    @Test
    fun `example 594 - empty paragraph`() = runTest {
        // given
        val textFlow = """
            ![*foo* bar][]

            [*foo* bar]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "img"("src" to "/url", "alt" to "foo bar", "title" to "title") {}
            }
        }
        // GFM expected:
        /*
            <p><img src="/url" alt="foo bar" title="title" /></p>
         */
    }

    // TODO review
    @Test
    fun `example 595 - empty paragraph`() = runTest {
        // given
        val textFlow = """
            ![Foo][]

            [foo]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "img"("src" to "/url", "alt" to "Foo", "title" to "title") {}
            }
        }
        // GFM expected:
        /*
            <p><img src="/url" alt="Foo" title="title" /></p>
         */
    }

    // TODO review
    @Test
    fun `example 596 - paragraph`() = runTest {
        // given
        val textFlow = buildText {
            +"![foo] \n"
            +"[]\n"
            +"\n"
            +"[foo]: /url \"title\"\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "img"("src" to "/url", "alt" to "foo", "title" to "title") {}
                +"\n[]"
            }
        }
        // GFM expected:
        /*
            <p><img src="/url" alt="foo" title="title" />
            []</p>
         */
    }

    // TODO review
    @Test
    fun `example 597 - empty paragraph`() = runTest {
        // given
        val textFlow = """
            ![foo]

            [foo]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

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

    // TODO review
    @Test
    fun `example 598 - empty paragraph`() = runTest {
        // given
        val textFlow = """
            ![*foo* bar]

            [*foo* bar]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "img"("src" to "/url", "alt" to "foo bar", "title" to "title") {}
            }
        }
        // GFM expected:
        /*
            <p><img src="/url" alt="foo bar" title="title" /></p>
         */
    }

    // TODO review
    @Test
    fun `example 599 - paragraph !foo, paragraph foo url title`() = runTest {
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

    // TODO review
    @Test
    fun `example 600 - empty paragraph`() = runTest {
        // given
        val textFlow = """
            ![Foo]

            [foo]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "img"("src" to "/url", "alt" to "Foo", "title" to "title") {}
            }
        }
        // GFM expected:
        /*
            <p><img src="/url" alt="Foo" title="title" /></p>
         */
    }

    // TODO review
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

    // TODO review
    @Test
    fun `example 602 - paragraph !foo`() = runTest {
        // given
        val textFlow = """
            \![foo]

            [foo]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"!"
                "a"("href" to "/url", "title" to "title") {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p>!<a href="/url" title="title">foo</a></p>
         */
    }

}
