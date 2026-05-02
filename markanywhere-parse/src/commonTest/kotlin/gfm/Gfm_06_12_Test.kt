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
 * Tests for GFM Section 06.12 — Hard line breaks.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#hard-line-breaks
 */
@Suppress("ClassName")
class Gfm_06_12_Test {

    // TODO review
    @Test
    fun `example 658 - paragraph foo baz`() = runTest {
        // given
        val textFlow = buildText {
            +"foo  \n"
            +"baz\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo"
                "br" {}
                +"\nbaz"
            }
        }
        // GFM expected:
        /*
            <p>foo<br />
            baz</p>
         */
    }

    // TODO review
    @Test
    fun `example 659 - paragraph foo baz`() = runTest {
        // given
        val textFlow = """
            foo\
            baz
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo"
                "br" {}
                +"\nbaz"
            }
        }
        // GFM expected:
        /*
            <p>foo<br />
            baz</p>
         */
    }

    // TODO review
    @Test
    fun `example 660 - paragraph foo baz`() = runTest {
        // given
        val textFlow = buildText {
            +"foo       \n"
            +"baz\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo"
                "br" {}
                +"\nbaz"
            }
        }
        // GFM expected:
        /*
            <p>foo<br />
            baz</p>
         */
    }

    // TODO review
    @Test
    fun `example 661 - paragraph foo bar`() = runTest {
        // given
        val textFlow = buildText {
            +"foo  \n"
            +"     bar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo"
                "br" {}
                +"\nbar"
            }
        }
        // GFM expected:
        /*
            <p>foo<br />
            bar</p>
         */
    }

    // TODO review
    @Test
    fun `example 662 - paragraph foo bar`() = runTest {
        // given
        val textFlow = buildText {
            +"foo\\\n"
            +"     bar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo"
                "br" {}
                +"\nbar"
            }
        }
        // GFM expected:
        /*
            <p>foo<br />
            bar</p>
         */
    }

    // TODO review
    @Test
    fun `example 663 - paragraph foo bar`() = runTest {
        // given
        val textFlow = buildText {
            +"*foo  \n"
            +"bar*\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo"
                    "br" {}
                    +"\nbar"
                }
            }
        }
        // GFM expected:
        /*
            <p><em>foo<br />
            bar</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 664 - paragraph foo bar`() = runTest {
        // given
        val textFlow = """
            *foo\
            bar*
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo"
                    "br" {}
                    +"\nbar"
                }
            }
        }
        // GFM expected:
        /*
            <p><em>foo<br />
            bar</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 665 - paragraph code span`() = runTest {
        // given
        val textFlow = buildText {
            +"`code  \n"
            +"span`\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"code   span"
                }
            }
        }
        // GFM expected:
        /*
            <p><code>code   span</code></p>
         */
    }

    // TODO review
    @Test
    fun `example 666 - paragraph code span`() = runTest {
        // given
        val textFlow = """
            `code\
            span`
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"code\\ span"
                }
            }
        }
        // GFM expected:
        /*
            <p><code>code\ span</code></p>
         */
    }

    // TODO review
    @Test
    fun `example 667 - empty paragraph`() = runTest {
        // given
        val textFlow = buildText {
            +"<a href=\"foo  \n"
            +"bar\">\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "foo  \nbar") {
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="foo  
            bar"></p>
         */
    }

    // TODO review
    @Test
    fun `example 668 - empty paragraph`() = runTest {
        // given
        val textFlow = """
            <a href="foo\
            bar">
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "foo\\\nbar") {
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="foo\
            bar"></p>
         */
    }

    // TODO review
    @Test
    fun `example 669 - paragraph foo`() = runTest {
        // given
        val textFlow = buildText {
            +"foo\\\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo\\"
            }
        }
        // GFM expected:
        /*
            <p>foo\</p>
         */
    }

    // TODO review
    @Test
    fun `example 670 - paragraph foo`() = runTest {
        // given
        val textFlow = buildText {
            +"foo  \n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo"
            }
        }
        // GFM expected:
        /*
            <p>foo</p>
         */
    }

    // TODO review
    @Test
    fun `example 671 - h3 foo`() = runTest {
        // given
        val textFlow = buildText {
            +"### foo\\\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h3" {
                +"foo\\"
            }
        }
        // GFM expected:
        /*
            <h3>foo\</h3>
         */
    }

    // TODO review
    @Test
    fun `example 672 - h3 foo`() = runTest {
        // given
        val textFlow = buildText {
            +"### foo  \n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h3" {
                +"foo"
            }
        }
        // GFM expected:
        /*
            <h3>foo</h3>
         */
    }

}
