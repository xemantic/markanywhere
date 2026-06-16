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
 * Tests for GFM Section 04.01 — Thematic breaks.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#thematic-breaks
 */
@Suppress("ClassName")
class Gfm_04_01_Test {

    @Test
    fun `example 13 - three thematic breaks`() = runTest {
        // given
        val textFlow = """
            ***
            ---
            ___
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "hr" {}
            "hr" {}
            "hr" {}
        }
        // GFM expected:
        /*
            <hr />
            <hr />
            <hr />
         */
    }

    @Test
    fun `example 14 - paragraph +++`() = runTest {
        // given
        val textFlow = "+++".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"+++"
            }
        }
        // GFM expected:
        /*
             <p>+++</p>
         */
    }

    @Test
    fun `example 15 - paragraph ===`() = runTest {
        // given
        val textFlow = "===".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"==="
            }
        }
        // GFM expected:
        /*
            <p>===</p>
         */
    }

    @Test
    fun `example 16 - paragraph -- __`() = runTest {
        // given
        val textFlow = """
            --
            **
            __
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"--\n**\n__"
            }
        }
        // GFM expected:
        /*
            <p>--
            **
            __</p>
         */
    }

    @Test
    fun `example 17 - three thematic breaks`() = runTest {
        // given
        val textFlow = """
            | ***
            |  ***
            |   ***
            |
        """.trimMargin().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "hr" {}
            "hr" {}
            "hr" {}
        }
        // GFM expected:
        /*
             <hr />
             <hr />
             <hr />
         */
    }

    @Test
    fun `example 18 - indented code block`() = runTest {
        // given
        val textFlow = "    ***\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"***\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>***
            </code></pre>
         */
    }

    @Test
    fun `example 19 - paragraph Foo`() = runTest {
        // given
        val textFlow = buildString {
            +"Foo\n"
            +"    ***\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo\n***"
            }
        }
        // GFM expected:
        /*
            <p>Foo
            ***</p>
         */
    }

    @Test
    fun `example 20 - thematic break`() = runTest {
        // given
        val textFlow = "_____________________________________".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "hr" {}
        }
        // GFM expected:
        /*
            <hr />
         */
    }

    @Test
    fun `example 21 - thematic break`() = runTest {
        // given
        val textFlow = buildString {
            +" - - -\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "hr" {}
        }
        // GFM expected:
        /*
            <hr />
         */
    }

    @Test
    fun `example 22 - thematic break`() = runTest {
        // given
        val textFlow = buildString {
            +" **  * ** * ** * **\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "hr" {}
        }
        // GFM expected:
        /*
            <hr />
         */
    }

    @Test
    fun `example 23 - thematic break`() = runTest {
        // given
        val textFlow = "-     -      -      -".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "hr" {}
        }
        // GFM expected:
        /*
            <hr />
         */
    }

    @Test
    fun `example 24 - thematic break`() = runTest {
        // given
        val textFlow = buildString {
            +"- - - -    \n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "hr" {}
        }
        // GFM expected:
        /*
            <hr />
         */
    }

    @Test
    fun `example 25 - paragraph _ _ _ _ a paragraph a------ paragraph ---a---`() = runTest {
        // given
        val textFlow = """
            _ _ _ _ a

            a------

            ---a---
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"_ _ _ _ a"
            }
            "p" {
                +"a------"
            }
            "p" {
                +"---a---"
            }
        }
        // GFM expected:
        /*
            <p>_ _ _ _ a</p>
            <p>a------</p>
            <p>---a---</p>
         */
    }

    @Test
    fun `example 26 - paragraph -`() = runTest {
        // given
        val textFlow = buildString {
            +" *-*\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"-"
                }
            }
        }
        // GFM expected:
        /*
            <p><em>-</em></p>
         */
    }

    // DIVERGENCE: incremental list-item parsing always wraps item content in `<p>`
    // even for tight (single-line) items. CommonMark distinguishes tight vs. loose
    // lists by looking ahead across the whole list to detect blank lines between
    // items; the streaming parser cannot defer that decision without buffering the
    // full list, so it conservatively emits `<p>` always.
    @Test
    fun `example 27 - DIVERGENCE - ul with 1 item thematic break ul with 1 item`() = runTest {
        // given
        val textFlow = """
            - foo
            ***
            - bar
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
            "ul" {
                "li" {
                    "p" {
                        +"bar"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>foo</li>
            </ul>
            <hr />
            <ul>
            <li>bar</li>
            </ul>
         */
    }

    @Test
    fun `example 28 - paragraph Foo thematic break paragraph bar`() = runTest {
        // given
        val textFlow = """
            Foo
            ***
            bar
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
                +"bar"
            }
        }
        // GFM expected:
        /*
            <p>Foo</p>
            <hr />
            <p>bar</p>
         */
    }

    // DIVERGENCE: Setext headings are not supported (see Gfm_04_03_Test for the
    // section devoted to them). `Foo\n---` would be an h2 in GFM; the parser
    // treats `---` as a thematic break that interrupts the paragraph.
    @Test
    fun `example 29 - DIVERGENCE - paragraph Foo thematic break paragraph bar`() = runTest {
        // given
        val textFlow = """
            Foo
            ---
            bar
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
                +"bar"
            }
        }
        // GFM expected:
        /*
            <h2>Foo</h2>
            <p>bar</p>
         */
    }

    // DIVERGENCE: incremental list-item parsing wraps text content in `<p>` even
    // for tight (single-line) items — see ex 27 for the rationale.
    @Test
    fun `example 30 - DIVERGENCE - ul with 1 item thematic break ul with 1 item`() = runTest {
        // given
        val textFlow = """
            * Foo
            * * *
            * Bar
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
            "ul" {
                "li" {
                    "p" {
                        +"Bar"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>Foo</li>
            </ul>
            <hr />
            <ul>
            <li>Bar</li>
            </ul>
         */
    }

    // DIVERGENCE: incremental list-item parsing wraps text content in `<p>` even
    // for tight items (see ex 27). Block-level constructs at item start (here a
    // thematic break) are still emitted directly without `<p>`, matching GFM.
    @Test
    fun `example 31 - DIVERGENCE - ul with 2 items`() = runTest {
        // given
        val textFlow = """
            - Foo
            - * * *
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
                "li" {
                    "hr" {}
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>Foo</li>
            <li>
            <hr />
            </li>
            </ul>
         */
    }

}
