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
 * Tests for GFM Section 05.01 — Block quotes.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#block-quotes
 */
@Suppress("ClassName")
class Gfm_05_01_Test {

    @Test
    fun `example 206 - blockquote text h1 Foo text paragraph bar baz text`() = runTest {
        // given
        val textFlow = """
            > # Foo
            > bar
            > baz
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "h1" {
                    +"Foo"
                }
                "p" {
                    +"bar\nbaz"
                }
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <h1>Foo</h1>
            <p>bar
            baz</p>
            </blockquote>
         */
    }

    @Test
    fun `example 207 - blockquote text h1 Foo text paragraph bar baz text`() = runTest {
        // given
        val textFlow = """
            ># Foo
            >bar
            > baz
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "h1" {
                    +"Foo"
                }
                "p" {
                    +"bar\nbaz"
                }
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <h1>Foo</h1>
            <p>bar
            baz</p>
            </blockquote>
         */
    }

    @Test
    fun `example 208 - blockquote text h1 Foo text paragraph bar baz text`() = runTest {
        // given
        val textFlow = buildString {
            +"   > # Foo\n"
            +"   > bar\n"
            +" > baz\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "h1" {
                    +"Foo"
                }
                "p" {
                    +"bar\nbaz"
                }
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <h1>Foo</h1>
            <p>bar
            baz</p>
            </blockquote>
         */
    }

    @Test
    fun `example 209 - indented code block`() = runTest {
        // given
        val textFlow = buildString {
            +"    > # Foo\n"
            +"    > bar\n"
            +"    > baz\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"> # Foo\n> bar\n> baz\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>&gt; # Foo
            &gt; bar
            &gt; baz
            </code></pre>
         */
    }

    @Test
    fun `example 210 - blockquote text h1 Foo text paragraph bar baz text`() = runTest {
        // given
        val textFlow = """
            > # Foo
            > bar
            baz
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "h1" {
                    +"Foo"
                }
                "p" {
                    +"bar\nbaz"
                }
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <h1>Foo</h1>
            <p>bar
            baz</p>
            </blockquote>
         */
    }

    @Test
    fun `example 211 - blockquote text paragraph bar baz foo text`() = runTest {
        // given
        val textFlow = """
            > bar
            baz
            > foo
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "p" {
                    +"bar\nbaz\nfoo"
                }
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <p>bar
            baz
            foo</p>
            </blockquote>
         */
    }

    @Test
    fun `example 212 - blockquote text paragraph foo text thematic break`() = runTest {
        // given
        val textFlow = """
            > foo
            ---
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

    // DIVERGENCE: list items are always loose (`<p>` wrapper) per the project-wide
    // always-loose list policy. GFM would emit tight `<li>foo</li>`.
    @Test
    fun `example 213 - DIVERGENCE - blockquote ul with 1 item ul with 1 item`() = runTest {
        // given
        val textFlow = """
            > - foo
            - bar
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "ul" {
                    "li" {
                        "p" {
                            +"foo"
                        }
                    }
                }
            }
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
            <blockquote>
            <ul>
            <li>foo</li>
            </ul>
            </blockquote>
            <ul>
            <li>bar</li>
            </ul>
         */
    }

    @Test
    fun `example 214 - blockquote text indented code block text indented code block`() = runTest {
        // given
        val textFlow = buildString {
            +">     foo\n"
            +"    bar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "pre" {
                    "code" {
                        +"foo\n"
                    }
                }
            }
            "pre" {
                "code" {
                    +"bar\n"
                }
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <pre><code>foo
            </code></pre>
            </blockquote>
            <pre><code>bar
            </code></pre>
         */
    }

    @Test
    fun `example 215 - blockquote empty fenced code paragraph foo empty fenced code`() = runTest {
        // given
        val textFlow = """
            > ```
            foo
            ```
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "pre" {
                    "code" {}
                }
            }
            "p" {
                +"foo"
            }
            "pre" {
                "code" {}
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <pre><code></code></pre>
            </blockquote>
            <p>foo</p>
            <pre><code></code></pre>
         */
    }

    @Test
    fun `example 216 - blockquote text paragraph foo - bar text`() = runTest {
        // given
        val textFlow = buildString {
            +"> foo\n"
            +"    - bar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "p" {
                    +"foo\n- bar"
                }
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <p>foo
            - bar</p>
            </blockquote>
         */
    }

    @Test
    fun `example 217 - blockquote text`() = runTest {
        // given
        val textFlow = ">".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
            }
        }
        // GFM expected:
        /*
            <blockquote>
            </blockquote>
         */
    }

    @Test
    fun `example 218 - blockquote text`() = runTest {
        // given
        val textFlow = buildString {
            +">\n"
            +">  \n"
            +"> \n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
            }
        }
        // GFM expected:
        /*
            <blockquote>
            </blockquote>
         */
    }

    @Test
    fun `example 219 - blockquote text paragraph foo text`() = runTest {
        // given
        val textFlow = buildString {
            +">\n"
            +"> foo\n"
            +">  \n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "p" {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <p>foo</p>
            </blockquote>
         */
    }

    @Test
    fun `example 220 - blockquote text paragraph foo text blockquote text paragraph truncated`() = runTest {
        // given
        val textFlow = """
            > foo

            > bar
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
            "blockquote" {
                "p" {
                    +"bar"
                }
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <p>foo</p>
            </blockquote>
            <blockquote>
            <p>bar</p>
            </blockquote>
         */
    }

    @Test
    fun `example 221 - blockquote text paragraph foo bar text`() = runTest {
        // given
        val textFlow = """
            > foo
            > bar
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "p" {
                    +"foo\nbar"
                }
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <p>foo
            bar</p>
            </blockquote>
         */
    }

    @Test
    fun `example 222 - blockquote text paragraph foo text paragraph bar text`() = runTest {
        // given
        val textFlow = """
            > foo
            >
            > bar
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "p" {
                    +"foo"
                }
                "p" {
                    +"bar"
                }
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <p>foo</p>
            <p>bar</p>
            </blockquote>
         */
    }

    @Test
    fun `example 223 - paragraph foo blockquote text paragraph bar text`() = runTest {
        // given
        val textFlow = """
            foo
            > bar
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo"
            }
            "blockquote" {
                "p" {
                    +"bar"
                }
            }
        }
        // GFM expected:
        /*
            <p>foo</p>
            <blockquote>
            <p>bar</p>
            </blockquote>
         */
    }

    @Test
    fun `example 224 - blockquote text paragraph aaa text thematic break blockquote truncated`() = runTest {
        // given
        val textFlow = """
            > aaa
            ***
            > bbb
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "p" {
                    +"aaa"
                }
            }
            "hr" {}
            "blockquote" {
                "p" {
                    +"bbb"
                }
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <p>aaa</p>
            </blockquote>
            <hr />
            <blockquote>
            <p>bbb</p>
            </blockquote>
         */
    }

    @Test
    fun `example 225 - blockquote text paragraph bar baz text`() = runTest {
        // given
        val textFlow = """
            > bar
            baz
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "p" {
                    +"bar\nbaz"
                }
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <p>bar
            baz</p>
            </blockquote>
         */
    }

    @Test
    fun `example 226 - blockquote text paragraph bar text paragraph baz`() = runTest {
        // given
        val textFlow = """
            > bar

            baz
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "p" {
                    +"bar"
                }
            }
            "p" {
                +"baz"
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <p>bar</p>
            </blockquote>
            <p>baz</p>
         */
    }

    @Test
    fun `example 227 - blockquote text paragraph bar text paragraph baz`() = runTest {
        // given
        val textFlow = """
            > bar
            >
            baz
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "p" {
                    +"bar"
                }
            }
            "p" {
                +"baz"
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <p>bar</p>
            </blockquote>
            <p>baz</p>
         */
    }

    @Test
    fun `example 228 - blockquote text blockquote text blockquote text paragraph foo truncated`() = runTest {
        // given
        val textFlow = """
            > > > foo
            bar
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "blockquote" {
                    "blockquote" {
                        "p" {
                            +"foo\nbar"
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <blockquote>
            <blockquote>
            <p>foo
            bar</p>
            </blockquote>
            </blockquote>
            </blockquote>
         */
    }

    @Test
    fun `example 229 - blockquote text blockquote text blockquote text paragraph foo truncated`() = runTest {
        // given
        val textFlow = """
            >>> foo
            > bar
            >>baz
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "blockquote" {
                    "blockquote" {
                        "p" {
                            +"foo\nbar\nbaz"
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <blockquote>
            <blockquote>
            <p>foo
            bar
            baz</p>
            </blockquote>
            </blockquote>
            </blockquote>
         */
    }

    @Test
    fun `example 230 - blockquote text indented code block text blockquote text par truncated`() = runTest {
        // given
        val textFlow = """
            >     code

            >    not code
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "pre" {
                    "code" {
                        +"code\n"
                    }
                }
            }
            "blockquote" {
                "p" {
                    +"not code"
                }
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <pre><code>code
            </code></pre>
            </blockquote>
            <blockquote>
            <p>not code</p>
            </blockquote>
         */
    }

}
