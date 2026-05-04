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
 * Tests for GFM Section 04.03 — Setext headings.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#setext-headings
 *
 * DIVERGENCE: Setext headings are not supported (see README — Divergences from GFM).
 * Most examples in this section exercise setext heading syntax. Tests marked
 * `DIVERGENCE` capture the spec input and the GFM expected output for reference,
 * but their assertions reflect what markanywhere-parse actually emits — usually
 * paragraphs and thematic breaks instead of h1/h2 headings. The remaining
 * examples (e.g. lines that are unambiguously thematic breaks or indented code)
 * happen to coincide with GFM output and are therefore spec-conformant.
 */
@Suppress("ClassName")
class Gfm_04_03_Test {

    @Test
    fun `example 50 - DIVERGENCE - 2 paragraphs with em, thematic break`() = runTest {
        // given
        val textFlow = """
            Foo *bar*
            =========

            Foo *bar*
            ---------
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo "
                "em" {
                    +"bar"
                }
                +"\n========="
            }
            "p" {
                +"Foo "
                "em" {
                    +"bar"
                }
            }
            "hr" {}
        }
        // GFM expected:
        /*
            <h1>Foo <em>bar</em></h1>
            <h2>Foo <em>bar</em></h2>
         */
    }

    @Test
    fun `example 51 - DIVERGENCE - paragraph with broken em across newline`() = runTest {
        // given
        val textFlow = """
            Foo *bar
            baz*
            ====
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo "
                "em" {
                    +"bar"
                }
                +"\nbaz*\n===="
            }
        }
        // GFM expected:
        /*
            <h1>Foo <em>bar
            baz</em></h1>
         */
    }

    @Test
    fun `example 52 - DIVERGENCE - paragraph with leading whitespace, broken em, tab em`() = runTest {
        // given
        val textFlow = buildText {
            +"  Foo *bar\n"
            +"baz*\t\n"
            +"====\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo "
                "em" {
                    +"bar"
                }
                +"\nbaz*\t\n===="
            }
        }
        // GFM expected:
        /*
            <h1>Foo <em>bar
            baz</em></h1>
         */
    }

    @Test
    fun `example 53 - DIVERGENCE - paragraph, thematic break, paragraph`() = runTest {
        // given
        val textFlow = """
            Foo
            -------------------------

            Foo
            =
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo"
            }
            "hr" {}
            "p" {
                +"Foo\n="
            }
        }
        // GFM expected:
        /*
            <h2>Foo</h2>
            <h1>Foo</h1>
         */
    }

    @Test
    fun `example 54 - DIVERGENCE - 3 paragraphs and 2 thematic breaks`() = runTest {
        // given
        val textFlow = buildText {
            +"   Foo\n"
            +"---\n"
            +"\n"
            +"  Foo\n"
            +"-----\n"
            +"\n"
            +"  Foo\n"
            +"  ===\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo"
            }
            "hr" {}
            "p" {
                +"Foo"
            }
            "hr" {}
            "p" {
                +"Foo\n==="
            }
        }
        // GFM expected:
        /*
            <h2>Foo</h2>
            <h2>Foo</h2>
            <h1>Foo</h1>
         */
    }

    @Test
    fun `example 55 - indented code block, thematic break`() = runTest {
        // given
        val textFlow = buildText {
            +"    Foo\n"
            +"    ---\n"
            +"\n"
            +"    Foo\n"
            +"---\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"Foo\n---\n\nFoo\n"
                }
            }
            "hr" {}
        }
        // GFM expected:
        /*
            <pre><code>Foo
            ---

            Foo
            </code></pre>
            <hr />
         */
    }

    @Test
    fun `example 56 - DIVERGENCE - paragraph Foo, thematic break`() = runTest {
        // given
        val textFlow = buildText {
            +"Foo\n"
            +"   ----      \n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo"
            }
            "hr" {}
        }
        // GFM expected:
        /*
            <h2>Foo</h2>
         */
    }

    @Test
    fun `example 57 - paragraph with 4-space indented dashes`() = runTest {
        // given
        val textFlow = buildText {
            +"Foo\n"
            +"    ---\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo\n---"
            }
        }
        // GFM expected:
        /*
            <p>Foo
            ---</p>
         */
    }

    @Test
    fun `example 58 - paragraph Foo equal equal, paragraph Foo, thematic break`() = runTest {
        // given
        val textFlow = """
            Foo
            = =

            Foo
            --- -
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo\n= ="
            }
            "p" {
                +"Foo"
            }
            "hr" {}
        }
        // GFM expected:
        /*
            <p>Foo
            = =</p>
            <p>Foo</p>
            <hr />
         */
    }

    @Test
    fun `example 59 - DIVERGENCE - paragraph Foo, thematic break`() = runTest {
        // given
        val textFlow = buildText {
            +"Foo  \n"
            +"-----\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // DIVERGENCE: GFM treats `-----` after `Foo  ` as a setext H2 marker
        // (`<h2>Foo</h2>`); markanywhere parses it as paragraph + thematic
        // break. Trailing spaces on the paragraph's last line are stripped
        // when the paragraph closes (matches GFM §6.7: hard breaks fire only
        // when the paragraph continues, not at end-of-block).
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo"
            }
            "hr" {}
        }
        // GFM expected:
        /*
            <h2>Foo</h2>
         */
    }

    @Test
    fun `example 60 - DIVERGENCE - paragraph Foo, thematic break`() = runTest {
        // given
        val textFlow = """
            Foo\
            ----
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo"
            }
            "hr" {}
        }
        // GFM expected:
        /*
            <h2>Foo\</h2>
         */
    }

    @Test
    fun `example 61 - DIVERGENCE - paragraph with code, thematic break, paragraph, paragraph, thematic break, paragraph`() = runTest {
        // given
        val textFlow = """
            `Foo
            ----
            `

            <a title="a lot
            ---
            of dashes"/>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"Foo"
                }
            }
            "hr" {}
            "p" {
                +"`"
            }
            "p" {
                +"<a title=\"a lot"
            }
            "hr" {}
            "p" {
                +"of dashes\"/>"
            }
        }
        // GFM expected:
        /*
            <h2>`Foo</h2>
            <p>`</p>
            <h2>&lt;a title=&quot;a lot</h2>
            <p>of dashes&quot;/&gt;</p>
         */
    }

    @Test
    fun `example 62 - blockquote with paragraph Foo, thematic break`() = runTest {
        // given
        val textFlow = """
            > Foo
            ---
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "p" {
                    +"Foo"
                }
            }
            "hr" {}
        }
        // GFM expected:
        /*
            <blockquote>
            <p>Foo</p>
            </blockquote>
            <hr />
         */
    }

    @Test
    fun `example 63 - blockquote with paragraph foo bar equals`() = runTest {
        // given
        val textFlow = """
            > foo
            bar
            ===
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "p" {
                    +"foo\nbar\n==="
                }
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <p>foo
            bar
            ===</p>
            </blockquote>
         */
    }

    @Test
    fun `example 64 - DIVERGENCE - ul with li with paragraph Foo, thematic break`() = runTest {
        // given
        val textFlow = """
            - Foo
            ---
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"Foo"
                    }
                }
            }
            "hr" {}
        }
        // GFM expected:
        /*
            <ul>
            <li>Foo</li>
            </ul>
            <hr />
         */
    }

    @Test
    fun `example 65 - DIVERGENCE - paragraph Foo Bar, thematic break`() = runTest {
        // given
        val textFlow = """
            Foo
            Bar
            ---
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo\nBar"
            }
            "hr" {}
        }
        // GFM expected:
        /*
            <h2>Foo
            Bar</h2>
         */
    }

    @Test
    fun `example 66 - DIVERGENCE - thematic break, paragraph, thematic break, paragraph, thematic break, paragraph`() = runTest {
        // given
        val textFlow = """
            ---
            Foo
            ---
            Bar
            ---
            Baz
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "hr" {}
            "p" {
                +"Foo"
            }
            "hr" {}
            "p" {
                +"Bar"
            }
            "hr" {}
            "p" {
                +"Baz"
            }
        }
        // GFM expected:
        /*
            <hr />
            <h2>Foo</h2>
            <h2>Bar</h2>
            <p>Baz</p>
         */
    }

    @Test
    fun `example 67 - paragraph ====`() = runTest {
        // given
        val textFlow = """

            ====
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"===="
            }
        }
        // GFM expected:
        /*
            <p>====</p>
         */
    }

    @Test
    fun `example 68 - 2 thematic breaks`() = runTest {
        // given
        val textFlow = """
            ---
            ---
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "hr" {}
            "hr" {}
        }
        // GFM expected:
        /*
            <hr />
            <hr />
         */
    }

    @Test
    fun `example 69 - DIVERGENCE - ul with li with paragraph foo, thematic break`() = runTest {
        // given
        val textFlow = """
            - foo
            -----
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"foo"
                    }
                }
            }
            "hr" {}
        }
        // GFM expected:
        /*
            <ul>
            <li>foo</li>
            </ul>
            <hr />
         */
    }

    @Test
    fun `example 70 - indented code block foo, thematic break`() = runTest {
        // given
        val textFlow = buildText {
            +"    foo\n"
            +"---\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"foo\n"
                }
            }
            "hr" {}
        }
        // GFM expected:
        /*
            <pre><code>foo
            </code></pre>
            <hr />
         */
    }

    @Test
    fun `example 71 - blockquote with paragraph foo, thematic break`() = runTest {
        // given
        val textFlow = """
            > foo
            -----
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "p" {
                    +"foo"
                }
            }
            "hr" {}
        }
        // GFM expected:
        /*
            <blockquote>
            <p>foo</p>
            </blockquote>
            <hr />
         */
    }

    @Test
    fun `example 72 - DIVERGENCE - paragraph with escaped greater-than, thematic break`() = runTest {
        // given
        val textFlow = """
            \> foo
            ------
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"> foo"
            }
            "hr" {}
        }
        // GFM expected:
        /*
            <h2>&gt; foo</h2>
         */
    }

    @Test
    fun `example 73 - DIVERGENCE - paragraph Foo, paragraph bar, thematic break, paragraph baz`() = runTest {
        // given
        val textFlow = """
            Foo

            bar
            ---
            baz
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo"
            }
            "p" {
                +"bar"
            }
            "hr" {}
            "p" {
                +"baz"
            }
        }
        // GFM expected:
        /*
            <p>Foo</p>
            <h2>bar</h2>
            <p>baz</p>
         */
    }

    @Test
    fun `example 74 - paragraph Foo bar, thematic break, paragraph baz`() = runTest {
        // given
        val textFlow = """
            Foo
            bar

            ---

            baz
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo\nbar"
            }
            "hr" {}
            "p" {
                +"baz"
            }
        }
        // GFM expected:
        /*
            <p>Foo
            bar</p>
            <hr />
            <p>baz</p>
         */
    }

    @Test
    fun `example 75 - paragraph Foo bar, thematic break, paragraph baz`() = runTest {
        // given
        val textFlow = """
            Foo
            bar
            * * *
            baz
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo\nbar"
            }
            "hr" {}
            "p" {
                +"baz"
            }
        }
        // GFM expected:
        /*
            <p>Foo
            bar</p>
            <hr />
            <p>baz</p>
         */
    }

    @Test
    fun `example 76 - paragraph Foo bar dashes baz`() = runTest {
        // given
        val textFlow = """
            Foo
            bar
            \---
            baz
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo\nbar\n---\nbaz"
            }
        }
        // GFM expected:
        /*
            <p>Foo
            bar
            ---
            baz</p>
         */
    }

}
