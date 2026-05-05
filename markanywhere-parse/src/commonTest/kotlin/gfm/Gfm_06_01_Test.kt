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
 * Tests for GFM Section 06.01 — Backslash escapes.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#backslash-escapes
 */
@Suppress("ClassName")
class Gfm_06_01_Test {

    @Test
    fun `example 308 - escapes for all ASCII punctuation`() = runTest {
        // given
        val textFlow = buildText {
            +"\\!\\\"\\#\\\$\\%\\&\\'\\(\\)\\*\\+\\,\\-\\.\\/\\:\\;\\<\\=\\>\\?\\@\\[\\\\\\]\\^\\_\\`\\{\\|\\}\\~\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"!\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
            }
        }
        // GFM expected:
        /*
            <p>!&quot;#$%&amp;'()*+,-./:;&lt;=&gt;?@[\]^_`{|}~</p>
         */
    }

    @Test
    fun `example 309 - backslash before non-punctuation stays literal`() = runTest {
        // given
        val textFlow = buildText {
            +"\\\t\\A\\a\\ \\3\\φ\\«\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"\\\t\\A\\a\\ \\3\\φ\\«"
            }
        }
        // GFM expected:
        /*
            <p>\	\A\a\ \3\φ\«</p>
         */
    }

    @Test
    fun `example 310 - backslash prevents Markdown construct`() = runTest {
        // given
        val textFlow = """
            \*not emphasized*
            \<br/> not a tag
            \[not a link](/foo)
            \`not code`
            1\. not a list
            \* not a list
            \# not a heading
            \[foo]: /url "not a reference"
            \&ouml; not a character entity
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"*not emphasized*\n<br/> not a tag\n[not a link](/foo)\n`not code`\n1. not a list\n* not a list\n# not a heading\n[foo]: /url \"not a reference\"\n&ouml; not a character entity"
            }
        }
        // GFM expected:
        /*
            <p>*not emphasized*
            &lt;br/&gt; not a tag
            [not a link](/foo)
            `not code`
            1. not a list
            * not a list
            # not a heading
            [foo]: /url &quot;not a reference&quot;
            &amp;ouml; not a character entity</p>
         */
    }

    @Test
    fun `example 311 - escaped backslash before emphasis`() = runTest {
        // given
        val textFlow = buildText {
            +"\\\\*emphasis*\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"\\"
                "em" {
                    +"emphasis"
                }
            }
        }
        // GFM expected:
        /*
            <p>\<em>emphasis</em></p>
         */
    }

    @Test
    fun `example 312 - backslash before newline is hard line break`() = runTest {
        // given
        val textFlow = """
            foo\
            bar
        """.trimIndent().chunkedRandomly().asFlow()

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
    fun `example 313 - backslashes in code span are literal`() = runTest {
        // given
        val textFlow = buildText {
            +"`` \\[\\` ``\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"\\[\\`"
                }
            }
        }
        // GFM expected:
        /*
            <p><code>\[\`</code></p>
         */
    }

    @Test
    fun `example 314 - backslashes in indented code block are literal`() = runTest {
        // given
        val textFlow = buildText {
            +"    \\[\\]\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"\\[\\]\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>\[\]
            </code></pre>
         */
    }

    @Test
    fun `example 315 - backslashes in fenced code block are literal`() = runTest {
        // given
        val textFlow = """
            ~~~
            \[\]
            ~~~
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"\\[\\]\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>\[\]
            </code></pre>
         */
    }

    @Test
    fun `example 316 - autolink URL escape is percent-encoded`() = runTest {
        // given
        val textFlow = buildText {
            +"<http://example.com?find=\\*>\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "http://example.com?find=%5C*") {
                    +"http://example.com?find=\\*"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="http://example.com?find=%5C*">http://example.com?find=\*</a></p>
         */
    }

    @Test
    fun `example 317 - backslashes in raw HTML are literal`() = runTest {
        // given
        val textFlow = buildText {
            +"<a href=\"/bar\\/)\">\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("a", "href" to "/bar\\/)") {
                +"\n"
            }
        }
        // GFM expected:
        /*
            <a href="/bar\/)">
         */
    }

    @Test
    fun `example 318 - backslash escapes in link URL and title`() = runTest {
        // given
        val textFlow = buildText {
            +"[foo](/bar\\* \"ti\\*tle\")\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/bar*", "title" to "ti*tle") {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/bar*" title="ti*tle">foo</a></p>
         */
    }

    // DIVERGENCE: link reference definitions require buffering the whole paragraph
    // before deciding whether `[foo]` resolves to a link, which is incompatible with
    // append-only streaming. The reference is treated as a literal paragraph instead.
    @Test
    fun `example 319 - DIVERGENCE - reference link definition with escapes`() = runTest {
        // given
        val textFlow = """
            [foo]

            [foo]: /bar\* "ti\*tle"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo]"
            }
            "p" {
                +"[foo]: /bar* \"ti*tle\""
            }
        }
        // GFM expected:
        /*
            <p><a href="/bar*" title="ti*tle">foo</a></p>
         */
    }

    @Test
    fun `example 320 - backslash escapes in fenced code info string`() = runTest {
        // given
        val textFlow = """
            ``` foo\+bar
            foo
            ```
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code"("class" to "language-foo+bar") {
                    +"foo\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code class="language-foo+bar">foo
            </code></pre>
         */
    }

}
