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

    // TODO review
    @Test
    fun `example 308 - paragraph !#$%&'()+,-=@`() = runTest {
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

    // TODO review
    @Test
    fun `example 309 - paragraph Aa 3φ«`() = runTest {
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

    // TODO review
    @Test
    fun `example 310 - paragraph not emphasized br`() = runTest {
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

    // TODO review
    @Test
    fun `example 311 - paragraph emphasis`() = runTest {
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

    // TODO review
    @Test
    fun `example 312 - paragraph foo bar`() = runTest {
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

    // TODO review
    @Test
    fun `example 313 - paragraph`() = runTest {
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

    // TODO review
    @Test
    fun `example 314 - indented code block`() = runTest {
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

    // TODO review
    @Test
    fun `example 315 - indented code block`() = runTest {
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
            "pre"("class" to "code") {
                +"\\[\\]"
            }
        }
        // GFM expected:
        /*
            <pre><code>\[\]
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 316 - paragraph httpexamplecomfind`() = runTest {
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

    // TODO review
    @Test
    fun `example 317 - link to bar)`() = runTest {
        // given
        val textFlow = buildText {
            +"<a href=\"/bar\\/)\">\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "a"("href" to "/bar\\/)") {
                +"\n"
            }
        }
        // GFM expected:
        /*
            <a href="/bar\/)">
         */
    }

    // TODO review
    @Test
    fun `example 318 - paragraph foo`() = runTest {
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

    // TODO review
    @Test
    fun `example 319 - paragraph foo`() = runTest {
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

    // TODO review
    @Test
    fun `example 320 - fenced code`() = runTest {
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
            "pre"("class" to "code lang-foo+bar") {
                +"foo"
            }
        }
        // GFM expected:
        /*
            <pre><code class="language-foo+bar">foo
            </code></pre>
         */
    }

}
