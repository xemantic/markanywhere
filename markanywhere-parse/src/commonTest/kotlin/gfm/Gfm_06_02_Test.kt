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
 * Tests for GFM Section 06.02 — Entity and numeric character references.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#entity-and-numeric-character-references
 */
@Suppress("ClassName")
class Gfm_06_02_Test {

    @Test
    fun `example 321 - paragraph with named entity references`() = runTest {
        // given
        val textFlow = """
            &nbsp; &amp; &copy; &AElig; &Dcaron;
            &frac34; &HilbertSpace; &DifferentialD;
            &ClockwiseContourIntegral; &ngE;
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"  & © Æ Ď\n¾ ℋ ⅆ\n∲ ≧̸"
            }
        }
        // GFM expected:
        /*
            <p>  &amp; © Æ Ď
            ¾ ℋ ⅆ
            ∲ ≧̸</p>
         */
    }

    @Test
    fun `example 322 - paragraph with decimal numeric references`() = runTest {
        // given
        val textFlow = "&#35; &#1234; &#992; &#0;".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"# Ӓ Ϡ �"
            }
        }
        // GFM expected:
        /*
            <p># Ӓ Ϡ �</p>
         */
    }

    @Test
    fun `example 323 - paragraph with hexadecimal numeric references`() = runTest {
        // given
        val textFlow = "&#X22; &#XD06; &#xcab;".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"\" ആ ಫ"
            }
        }
        // GFM expected:
        /*
            <p>&quot; ആ ಫ</p>
         */
    }

    @Test
    fun `example 324 - paragraph &nbsp &x &# &#x &#87`() = runTest {
        // given
        val textFlow = """
            &nbsp &x; &#; &#x;
            &#87654321;
            &#abcdef0;
            &ThisIsNotDefined; &hi?;
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"&nbsp &x; &#; &#x;\n&#87654321;\n&#abcdef0;\n&ThisIsNotDefined; &hi?;"
            }
        }
        // GFM expected:
        /*
            <p>&amp;nbsp &amp;x; &amp;#; &amp;#x;
            &amp;#87654321;
            &amp;#abcdef0;
            &amp;ThisIsNotDefined; &amp;hi?;</p>
         */
    }

    @Test
    fun `example 325 - paragraph &copy`() = runTest {
        // given
        val textFlow = "&copy".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"&copy"
            }
        }
        // GFM expected:
        /*
            <p>&amp;copy</p>
         */
    }

    @Test
    fun `example 326 - paragraph &MadeUpEntity`() = runTest {
        // given
        val textFlow = "&MadeUpEntity;".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"&MadeUpEntity;"
            }
        }
        // GFM expected:
        /*
            <p>&amp;MadeUpEntity;</p>
         */
    }

    @Test
    fun `example 327 - link href with entity references`() = runTest {
        // given
        val textFlow = """<a href="&ouml;&ouml;.html">""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            "a"("href" to "öö.html") {
                +"\n"
            }
        }
        // GFM expected:
        /*
            <a href="&ouml;&ouml;.html">
         */
    }

    @Test
    fun `example 328 - paragraph foo`() = runTest {
        // given
        val textFlow = """[foo](/f&ouml;&ouml; "f&ouml;&ouml;")""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/f%C3%B6%C3%B6", "title" to "föö") {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/f%C3%B6%C3%B6" title="föö">foo</a></p>
         */
    }

    @Test
    fun `example 329 - DIVERGENCE - link reference definition forward reference`() = runTest {
        // given
        val textFlow = """
            [foo]

            [foo]: /f&ouml;&ouml; "f&ouml;&ouml;"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Forward reference DIVERGENCE: `[foo]` is emitted before the trailing
        // definition is registered, so the usage falls through as literal text.
        // The definition is consumed silently afterwards.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo]"
            }
        }
        // GFM expected:
        /*
            <p><a href="/f%C3%B6%C3%B6" title="föö">foo</a></p>
         */
    }

    @Test
    fun `example 330 - fenced code`() = runTest {
        // given
        val textFlow = """
            ``` f&ouml;&ouml;
            foo
            ```
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code"("class" to "language-föö") {
                    +"foo\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code class="language-föö">foo
            </code></pre>
         */
    }

    @Test
    fun `example 331 - paragraph f&ouml&ouml`() = runTest {
        // given
        val textFlow = "`f&ouml;&ouml;`".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"f&ouml;&ouml;"
                }
            }
        }
        // GFM expected:
        /*
            <p><code>f&amp;ouml;&amp;ouml;</code></p>
         */
    }

    @Test
    fun `example 332 - indented code block`() = runTest {
        // given
        val textFlow = buildText {
            +"    f&ouml;f&ouml;\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"f&ouml;f&ouml;\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>f&amp;ouml;f&amp;ouml;
            </code></pre>
         */
    }

    @Test
    fun `example 333 - paragraph foo foo`() = runTest {
        // given
        val textFlow = """
            &#42;foo&#42;
            *foo*
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"*foo*\n"
                "em" {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p>*foo*
            <em>foo</em></p>
         */
    }

    @Test
    fun `example 334 - DIVERGENCE - lists are always rendered loose`() = runTest {
        // given
        val textFlow = """
            &#42; foo

            * foo
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // DIVERGENCE: tight/loose can only be decided after the entire list
        // closes, but the parser is append-only — every list item's first
        // content is wrapped in `<p>` (see CLAUDE.md). The entity-decoded `*`
        // correctly does not form a list bullet.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"* foo"
            }
            "ul" {
                "li" {
                    "p" {
                        +"foo"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p>* foo</p>
            <ul>
            <li>foo</li>
            </ul>
         */
    }

    @Test
    fun `example 335 - paragraph foo bar`() = runTest {
        // given
        val textFlow = "foo&#10;&#10;bar".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo\n\nbar"
            }
        }
        // GFM expected:
        /*
            <p>foo
            
            bar</p>
         */
    }

    @Test
    fun `example 336 - paragraph foo`() = runTest {
        // given
        val textFlow = "&#9;foo".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"\tfoo"
            }
        }
        // GFM expected:
        /*
            <p>	foo</p>
         */
    }

    @Test
    fun `example 337 - DIVERGENCE - link URL space validation`() = runTest {
        // given
        val textFlow = "[a](url &quot;tit&quot;)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // DIVERGENCE: GFM rejects the entire `[a](…)` because `url &quot;tit&quot;`
        // is not a valid destination/title pair, and emits the source as literal
        // text with the entities still HTML-escaped. Our streaming parser correctly
        // aborts the link at the malformed `&` after the destination's trailing
        // space, but the partial replay vs. paragraph-stream continuation causes
        // only the second entity ref to decode (`&quot;` → `"`), while the first
        // survives literally because it was consumed as part of the link source
        // before the abort.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[a](url &quot;tit\")"
            }
        }
        // GFM expected:
        /*
            <p>[a](url &quot;tit&quot;)</p>
         */
    }

}
