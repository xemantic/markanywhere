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
 * Tests for GFM Section 06.10 — Raw HTML.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#raw-html
 */
@Suppress("ClassName")
class Gfm_06_10_Test {

    @Test
    fun `example 636 - empty paragraph`() = runTest {
        // given
        val textFlow = "<a><bab><c2c>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                tagged {
                    "a" {
                        "bab" {
                            "c2c" {
                            }
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p><a><bab><c2c></p>
         */
    }

    @Test
    fun `example 637 - empty paragraph`() = runTest {
        // given
        val textFlow = "<a/><b2/>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                tagged {
                    "a" {}
                    "b2" {}
                }
            }
        }
        // GFM expected:
        /*
            <p><a/><b2/></p>
         */
    }

    @Test
    fun `example 638 - DIVERGENCE - multi-line opening tag`() = runTest {
        // given
        val textFlow = """
            <a  /><b2
            data="foo" >
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // DIVERGENCE: inline HTML opening tags cannot span newlines — `flushInline`
        // force-closes inline state at every line break, so the second tag whose
        // opener and attributes straddle `\n` falls through as literal text.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                tag("a") {}
                +"<b2\ndata=\"foo\" >"
            }
        }
        // GFM expected:
        /*
            <p><a  /><b2
            data="foo" ></p>
         */
    }

    @Test
    fun `example 639 - DIVERGENCE - multi-line attributes`() = runTest {
        // given
        val textFlow = """
            <a foo="bar" bam = 'baz <em>"</em>'
            _boolean zoop:33=zoop:33 />
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // DIVERGENCE: opening tag with attributes spanning a newline cannot be
        // recognised — inline HTML buffering aborts at `\n`. The first line drops
        // out as literal text (with the inner `<em>` / `</em>` also failing to
        // tag-resolve), and the second line's leading `_boolean` opens a real em
        // span that flushInline force-closes at paragraph end.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"<a foo=\"bar\" bam = 'baz <em>\"</em>'\n"
                "em" {
                    +"boolean zoop:33=zoop:33 />"
                }
            }
        }
        // GFM expected:
        /*
            <p><a foo="bar" bam = 'baz <em>"</em>'
            _boolean zoop:33=zoop:33 /></p>
         */
    }

    @Test
    fun `example 640 - paragraph Foo`() = runTest {
        // given
        val textFlow = """Foo <responsive-image src="foo.jpg" />""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo "
                tag("responsive-image", "src" to "foo.jpg") {}
            }
        }
        // GFM expected:
        /*
            <p>Foo <responsive-image src="foo.jpg" /></p>
         */
    }

    @Test
    fun `example 641 - paragraph 33 __`() = runTest {
        // given
        val textFlow = "<33> <__>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"<33> <__>"
            }
        }
        // GFM expected:
        /*
            <p>&lt;33&gt; &lt;__&gt;</p>
         */
    }

    @Test
    fun `example 642 - paragraph a h#ref=hi`() = runTest {
        // given
        val textFlow = """<a h*#ref="hi">""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"<a h*#ref=\"hi\">"
            }
        }
        // GFM expected:
        /*
            <p>&lt;a h*#ref=&quot;hi&quot;&gt;</p>
         */
    }

    @Test
    fun `example 643 - paragraph a href=hi' a href=h`() = runTest {
        // given
        val textFlow = """<a href="hi'> <a href=hi'>""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"<a href=\"hi'> <a href=hi'>"
            }
        }
        // GFM expected:
        /*
            <p>&lt;a href=&quot;hi'&gt; &lt;a href=hi'&gt;</p>
         */
    }

    @Test
    fun `example 644 - paragraph a foobar foo`() = runTest {
        // given
        val textFlow = """
            < a><
            foo><bar/ >
            <foo bar=baz
            bim!bop />
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"< a><\nfoo><bar/ >\n<foo bar=baz\nbim!bop />"
            }
        }
        // GFM expected:
        /*
            <p>&lt; a&gt;&lt;
            foo&gt;&lt;bar/ &gt;
            &lt;foo bar=baz
            bim!bop /&gt;</p>
         */
    }

    @Test
    fun `example 645 - paragraph a href='bar'title=titl`() = runTest {
        // given
        val textFlow = "<a href='bar'title=title>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"<a href='bar'title=title>"
            }
        }
        // GFM expected:
        /*
            <p>&lt;a href='bar'title=title&gt;</p>
         */
    }

    @Test
    fun `example 646 - DIVERGENCE - orphan close tags`() = runTest {
        // given
        val textFlow = "</a></foo >".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // DIVERGENCE: GFM passes through valid orphan close tags as raw HTML.
        // Our parser falls back to literal text when an inline `</tag>` has no
        // matching open in `inlineOpenStack` — emitting an unmark would break
        // the LIFO mark/unmark invariant.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"</a></foo >"
            }
        }
        // GFM expected:
        /*
            <p></a></foo ></p>
         */
    }

    @Test
    fun `example 647 - paragraph a href=foo`() = runTest {
        // given
        val textFlow = """</a href="foo">""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"</a href=\"foo\">"
            }
        }
        // GFM expected:
        /*
            <p>&lt;/a href=&quot;foo&quot;&gt;</p>
         */
    }

    @Test
    fun `example 648 - DIVERGENCE - multi-line comment`() = runTest {
        // given
        val textFlow = """
            foo <!-- this is a
            comment - with hyphen -->
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // DIVERGENCE: the parser does not recognise inline HTML comments at all,
        // and even if it did, a comment whose body spans `\n` would still be
        // unrecognisable in an append-only stream that closes inline state at
        // line breaks. Source survives as literal text.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo <!-- this is a\ncomment - with hyphen -->"
            }
        }
        // GFM expected:
        /*
            <p>foo <!-- this is a
            comment - with hyphen --></p>
         */
    }

    @Test
    fun `example 649 - paragraph foo !-- not a comment`() = runTest {
        // given
        val textFlow = "foo <!-- not a comment -- two hyphens -->".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo <!-- not a comment -- two hyphens -->"
            }
        }
        // GFM expected:
        /*
            <p>foo &lt;!-- not a comment -- two hyphens --&gt;</p>
         */
    }

    @Test
    fun `example 650 - paragraph foo !-- foo --, paragraph foo !-- foo---`() = runTest {
        // given
        val textFlow = """
            foo <!--> foo -->

            foo <!-- foo--->
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo <!--> foo -->"
            }
            "p" {
                +"foo <!-- foo--->"
            }
        }
        // GFM expected:
        /*
            <p>foo &lt;!--&gt; foo --&gt;</p>
            <p>foo &lt;!-- foo---&gt;</p>
         */
    }

    @Test
    fun `example 651 - DIVERGENCE - processing instruction`() = runTest {
        // given
        val textFlow = buildText {
            +$$"foo <?php echo $a; ?>\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // DIVERGENCE: inline HTML processing instructions (`<?…?>`) are not
        // recognised — the inline `<` accumulator terminates on the first `>`,
        // which for a PI is the `?>` closer, so the construct cannot be matched
        // generically. Source survives as literal text.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +$$"foo <?php echo $a; ?>"
            }
        }
        // GFM expected:
        /*
            <p>foo <?php echo $a; ?></p>
         */
    }

    @Test
    fun `example 652 - DIVERGENCE - declaration`() = runTest {
        // given
        val textFlow = "foo <!ELEMENT br EMPTY>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // DIVERGENCE: inline HTML declarations (`<!NAME …>`) are not recognised —
        // the inline raw-HTML dispatch only handles `<tag …>` / `</tag>` forms.
        // Source survives as literal text.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo <!ELEMENT br EMPTY>"
            }
        }
        // GFM expected:
        /*
            <p>foo <!ELEMENT br EMPTY></p>
         */
    }

    @Test
    fun `example 653 - DIVERGENCE - CDATA`() = runTest {
        // given
        val textFlow = "foo <![CDATA[>&<]]>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // DIVERGENCE: inline CDATA sections are not recognised. The body would
        // need a multi-char terminator scan (`]]>`) inside the inline buffer, and
        // CDATA content can contain `>` and `<` literally — incompatible with the
        // current `>`-terminated buffer. Source survives as literal text.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo <![CDATA[>&<]]>"
            }
        }
        // GFM expected:
        /*
            <p>foo <![CDATA[>&<]]></p>
         */
    }

    @Test
    fun `example 654 - paragraph foo`() = runTest {
        // given
        val textFlow = """foo <a href="&ouml;">""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo "
                tag("a", "href" to "ö") {
                }
            }
        }
        // GFM expected:
        /*
            <p>foo <a href="&ouml;"></p>
         */
    }

    @Test
    fun `example 655 - paragraph foo`() = runTest {
        // given
        val textFlow = buildText {
            +"foo <a href=\"\\*\">\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo "
                tag("a", "href" to "\\*") {
                }
            }
        }
        // GFM expected:
        /*
            <p>foo <a href="\*"></p>
         */
    }

    @Test
    fun `example 656 - paragraph a href=`() = runTest {
        // given
        val textFlow = buildText {
            +"<a href=\"\\\"\">\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"<a href=\"\"\">"
            }
        }
        // GFM expected:
        /*
            <p>&lt;a href=&quot;&quot;&quot;&gt;</p>
         */
    }

}
