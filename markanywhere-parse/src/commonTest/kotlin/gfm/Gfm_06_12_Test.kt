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

    // DIVERGENCE: GFM expects emphasis to span the hard line break so the
    // closing `*` on the next line still pairs with the opener (`<em>foo<br/>
    // bar</em>`). The streaming parser force-closes every inline state at
    // each `\n` (CLAUDE.md "Inline state cannot span line breaks"), so em
    // closes at the boundary, the `<br/>` is emitted as a sibling, and the
    // unmatched `*` on the second line falls through as literal text.
    @Test
    fun `example 663 - DIVERGENCE - paragraph foo bar`() = runTest {
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
                }
                "br" {}
                +"\nbar*"
            }
        }
        // GFM expected:
        /*
            <p><em>foo<br />
            bar</em></p>
         */
    }

    // DIVERGENCE: see example 663 — `\<newline>` hard break inside `*…*`
    // produces the same shape as `  \n` because emphasis closes at the
    // line boundary in the streaming model.
    @Test
    fun `example 664 - DIVERGENCE - paragraph foo bar`() = runTest {
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
                }
                "br" {}
                +"\nbar*"
            }
        }
        // GFM expected:
        /*
            <p><em>foo<br />
            bar</em></p>
         */
    }

    // DIVERGENCE: code-span content can't span `\n` in the streaming model
    // (CLAUDE.md "Inline state cannot span line breaks"). `<code>` closes at
    // the line boundary; the `<br/>` (≥2 trailing spaces) emits as a sibling
    // and `span\`` flows as paragraph content with the unmatched backtick
    // staying literal.
    @Test
    fun `example 665 - DIVERGENCE - paragraph code span`() = runTest {
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
                    +"code"
                }
                "br" {}
                +"\nspan`"
            }
        }
        // GFM expected:
        /*
            <p><code>code   span</code></p>
         */
    }

    // DIVERGENCE: see example 665. Backslash inside a code span is *literal*
    // (GFM §6.3) and remains in the buffered content, so the close-at-`\n`
    // path emits `code\` as the inline-code body. Note: the `\<newline>`
    // hard-break promotion does NOT fire here because backslashes are not
    // tracked as escape state inside an open code span.
    @Test
    fun `example 666 - DIVERGENCE - paragraph code span`() = runTest {
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
                    +"code\\"
                }
                +"\nspan`"
            }
        }
        // GFM expected:
        /*
            <p><code>code\ span</code></p>
         */
    }

    // DIVERGENCE: an inline HTML tag with attributes that span `\n` is not
    // assembled — `<` accumulates an inline-buffer that the line-boundary
    // flushInline drains as literal text. The `≥2 trailing spaces` still
    // fires the hard-break (`<br/>` between the two halves of the source
    // tag).
    @Test
    fun `example 667 - DIVERGENCE - empty paragraph`() = runTest {
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
                +"<a href=\"foo"
                "br" {}
                +"\nbar\">"
            }
        }
        // GFM expected:
        /*
            <p><a href="foo
            bar"></p>
         */
    }

    // DIVERGENCE: see example 667 — multi-line HTML tag is not assembled.
    // Here the `\<newline>` inside the attribute value is content, not a
    // hard-break candidate, because escape tracking is per-paragraph (not
    // per-attribute), and the inline buffer keeps the `<` raw.
    @Test
    fun `example 668 - DIVERGENCE - empty paragraph`() = runTest {
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
                +"<a href=\"foo\\\nbar\">"
            }
        }
        // GFM expected:
        /*
            <p><a href="foo\
            bar"></p>
         */
    }

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
