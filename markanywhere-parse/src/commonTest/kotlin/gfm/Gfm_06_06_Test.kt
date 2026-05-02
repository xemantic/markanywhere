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
 * Tests for GFM Section 06.06 — Links.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#links
 */
@Suppress("ClassName")
class Gfm_06_06_Test {

    // TODO review
    @Test
    fun `example 494 - paragraph link`() = runTest {
        // given
        val textFlow = """[link](/uri "title")""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/uri", "title" to "title") {
                    +"link"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/uri" title="title">link</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 495 - paragraph link`() = runTest {
        // given
        val textFlow = "[link](/uri)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/uri") {
                    +"link"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/uri">link</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 496 - paragraph link`() = runTest {
        // given
        val textFlow = "[link]()".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "") {
                    +"link"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="">link</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 497 - paragraph link`() = runTest {
        // given
        val textFlow = "[link](<>)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "") {
                    +"link"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="">link</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 498 - paragraph link(my uri)`() = runTest {
        // given
        val textFlow = "[link](/my uri)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[link](/my uri)"
            }
        }
        // GFM expected:
        /*
            <p>[link](/my uri)</p>
         */
    }

    // TODO review
    @Test
    fun `example 499 - paragraph link`() = runTest {
        // given
        val textFlow = "[link](</my uri>)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/my%20uri") {
                    +"link"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/my%20uri">link</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 500 - paragraph link(foo bar)`() = runTest {
        // given
        val textFlow = """
            [link](foo
            bar)
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[link](foo\nbar)"
            }
        }
        // GFM expected:
        /*
            <p>[link](foo
            bar)</p>
         */
    }

    // TODO review
    @Test
    fun `example 501 - paragraph link()`() = runTest {
        // given
        val textFlow = """
            [link](<foo
            bar>)
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[link]("
                "foo"("bar" to "") {
                    +")"
                }
            }
        }
        // GFM expected:
        /*
            <p>[link](<foo
            bar>)</p>
         */
    }

    // TODO review
    @Test
    fun `example 502 - paragraph a`() = runTest {
        // given
        val textFlow = "[a](<b)c>)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "b)c") {
                    +"a"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="b)c">a</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 503 - paragraph link(foo)`() = runTest {
        // given
        val textFlow = buildText {
            +"[link](<foo\\>)\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[link](<foo>)"
            }
        }
        // GFM expected:
        /*
            <p>[link](&lt;foo&gt;)</p>
         */
    }

    // TODO review
    @Test
    fun `example 504 - paragraph a(b)c a(b)c a(`() = runTest {
        // given
        val textFlow = """
            [a](<b)c
            [a](<b)c>
            [a](<b>c)
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[a](<b)c\n[a](<b)c>\n[a]("
                "b" {
                    +"c)"
                }
            }
        }
        // GFM expected:
        /*
            <p>[a](&lt;b)c
            [a](&lt;b)c&gt;
            [a](<b>c)</p>
         */
    }

    // TODO review
    @Test
    fun `example 505 - paragraph link`() = runTest {
        // given
        val textFlow = buildText {
            +"[link](\\(foo\\))\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "(foo)") {
                    +"link"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="(foo)">link</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 506 - paragraph link`() = runTest {
        // given
        val textFlow = "[link](foo(and(bar)))".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "foo(and(bar))") {
                    +"link"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="foo(and(bar))">link</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 507 - paragraph link`() = runTest {
        // given
        val textFlow = buildText {
            +"[link](foo\\(and\\(bar\\))\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "foo(and(bar)") {
                    +"link"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="foo(and(bar)">link</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 508 - paragraph link`() = runTest {
        // given
        val textFlow = "[link](<foo(and(bar)>)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "foo(and(bar)") {
                    +"link"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="foo(and(bar)">link</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 509 - paragraph link`() = runTest {
        // given
        val textFlow = buildText {
            +"[link](foo\\)\\:)\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "foo):") {
                    +"link"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="foo):">link</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 510 - three paragraph links`() = runTest {
        // given
        val textFlow = """
            [link](#fragment)

            [link](http://example.com#fragment)

            [link](http://example.com?foo=3#frag)
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "#fragment") {
                    +"link"
                }
            }
            "p" {
                "a"("href" to "http://example.com#fragment") {
                    +"link"
                }
            }
            "p" {
                "a"("href" to "http://example.com?foo=3#frag") {
                    +"link"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="#fragment">link</a></p>
            <p><a href="http://example.com#fragment">link</a></p>
            <p><a href="http://example.com?foo=3#frag">link</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 511 - paragraph link`() = runTest {
        // given
        val textFlow = buildText {
            +"[link](foo\\bar)\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "foo%5Cbar") {
                    +"link"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="foo%5Cbar">link</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 512 - paragraph link`() = runTest {
        // given
        val textFlow = "[link](foo%20b&auml;)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "foo%20b%C3%A4") {
                    +"link"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="foo%20b%C3%A4">link</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 513 - paragraph link`() = runTest {
        // given
        val textFlow = """[link]("title")""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "%22title%22") {
                    +"link"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="%22title%22">link</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 514 - paragraph link link link`() = runTest {
        // given
        val textFlow = """
            [link](/url "title")
            [link](/url 'title')
            [link](/url (title))
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url", "title" to "title") {
                    +"link"
                }
                +"\n"
                "a"("href" to "/url", "title" to "title") {
                    +"link"
                }
                +"\n"
                "a"("href" to "/url", "title" to "title") {
                    +"link"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/url" title="title">link</a>
            <a href="/url" title="title">link</a>
            <a href="/url" title="title">link</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 515 - paragraph link`() = runTest {
        // given
        val textFlow = buildText {
            +"[link](/url \"title \\\"&quot;\")\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url", "title" to "title \"\"") {
                    +"link"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/url" title="title &quot;&quot;">link</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 516 - paragraph link`() = runTest {
        // given
        val textFlow = """[link](/url "title")""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url%C2%A0%22title%22") {
                    +"link"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/url%C2%A0%22title%22">link</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 517 - paragraph link(url title and`() = runTest {
        // given
        val textFlow = """[link](/url "title "and" title")""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[link](/url \"title \"and\" title\")"
            }
        }
        // GFM expected:
        /*
            <p>[link](/url &quot;title &quot;and&quot; title&quot;)</p>
         */
    }

    // TODO review
    @Test
    fun `example 518 - paragraph link`() = runTest {
        // given
        val textFlow = """[link](/url 'title "and" title')""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url", "title" to "title \"and\" title") {
                    +"link"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/url" title="title &quot;and&quot; title">link</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 519 - paragraph link`() = runTest {
        // given
        val textFlow = buildText {
            +"[link](   /uri\n"
            +"  \"title\"  )\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/uri", "title" to "title") {
                    +"link"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/uri" title="title">link</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 520 - paragraph link (uri)`() = runTest {
        // given
        val textFlow = "[link] (/uri)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[link] (/uri)"
            }
        }
        // GFM expected:
        /*
            <p>[link] (/uri)</p>
         */
    }

    // TODO review
    @Test
    fun `example 521 - paragraph link foo bar`() = runTest {
        // given
        val textFlow = "[link [foo [bar]]](/uri)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/uri") {
                    +"link [foo [bar]]"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/uri">link [foo [bar]]</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 522 - paragraph link bar(uri)`() = runTest {
        // given
        val textFlow = "[link] bar](/uri)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[link] bar](/uri)"
            }
        }
        // GFM expected:
        /*
            <p>[link] bar](/uri)</p>
         */
    }

    // TODO review
    @Test
    fun `example 523 - paragraph link bar`() = runTest {
        // given
        val textFlow = "[link [bar](/uri)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[link "
                "a"("href" to "/uri") {
                    +"bar"
                }
            }
        }
        // GFM expected:
        /*
            <p>[link <a href="/uri">bar</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 524 - paragraph link bar`() = runTest {
        // given
        val textFlow = buildText {
            +"[link \\[bar](/uri)\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/uri") {
                    +"link [bar"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/uri">link [bar</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 525 - paragraph link foo bar #`() = runTest {
        // given
        val textFlow = "[link *foo **bar** `#`*](/uri)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/uri") {
                    +"link "
                    "em" {
                        +"foo "
                        "strong" {
                            +"bar"
                        }
                        +" "
                        "code" {
                            +"#"
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/uri">link <em>foo <strong>bar</strong> <code>#</code></em></a></p>
         */
    }

    // TODO review
    @Test
    fun `example 526 - empty paragraph`() = runTest {
        // given
        val textFlow = "[![moon](moon.jpg)](/uri)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/uri") {
                    "img"("src" to "moon.jpg", "alt" to "moon") {}
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/uri"><img src="moon.jpg" alt="moon" /></a></p>
         */
    }

    // TODO review
    @Test
    fun `example 527 - paragraph foo bar(uri)`() = runTest {
        // given
        val textFlow = "[foo [bar](/uri)](/uri)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo "
                "a"("href" to "/uri") {
                    +"bar"
                }
                +"](/uri)"
            }
        }
        // GFM expected:
        /*
            <p>[foo <a href="/uri">bar</a>](/uri)</p>
         */
    }

    // TODO review
    @Test
    fun `example 528 - paragraph foo bar baz(uri)(`() = runTest {
        // given
        val textFlow = "[foo *[bar [baz](/uri)](/uri)*](/uri)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo "
                "em" {
                    +"[bar "
                    "a"("href" to "/uri") {
                        +"baz"
                    }
                    +"](/uri)"
                }
                +"](/uri)"
            }
        }
        // GFM expected:
        /*
            <p>[foo <em>[bar <a href="/uri">baz</a>](/uri)</em>](/uri)</p>
         */
    }

    // TODO review
    @Test
    fun `example 529 - empty paragraph`() = runTest {
        // given
        val textFlow = "![[[foo](uri1)](uri2)](uri3)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "img"("src" to "uri3", "alt" to "[foo](uri2)") {}
            }
        }
        // GFM expected:
        /*
            <p><img src="uri3" alt="[foo](uri2)" /></p>
         */
    }

    // TODO review
    @Test
    fun `example 530 - paragraph foo`() = runTest {
        // given
        val textFlow = "*[foo*](/uri)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"*"
                "a"("href" to "/uri") {
                    +"foo*"
                }
            }
        }
        // GFM expected:
        /*
            <p>*<a href="/uri">foo*</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 531 - paragraph foo bar`() = runTest {
        // given
        val textFlow = "[foo *bar](baz*)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "baz*") {
                    +"foo *bar"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="baz*">foo *bar</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 532 - paragraph foo bar baz`() = runTest {
        // given
        val textFlow = "*foo [bar* baz]".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo [bar"
                }
                +" baz]"
            }
        }
        // GFM expected:
        /*
            <p><em>foo [bar</em> baz]</p>
         */
    }

    // TODO review
    @Test
    fun `example 533 - paragraph foo`() = runTest {
        // given
        val textFlow = """[foo <bar attr="](baz)">""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo "
                "bar"("attr" to "](baz)") {
                }
            }
        }
        // GFM expected:
        /*
            <p>[foo <bar attr="](baz)"></p>
         */
    }

    // TODO review
    @Test
    fun `example 534 - paragraph foo(uri)`() = runTest {
        // given
        val textFlow = "[foo`](/uri)`".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo"
                "code" {
                    +"](/uri)"
                }
            }
        }
        // GFM expected:
        /*
            <p>[foo<code>](/uri)</code></p>
         */
    }

    // TODO review
    @Test
    fun `example 535 - paragraph foohttpexamplecom`() = runTest {
        // given
        val textFlow = "[foo<http://example.com/?search=](uri)>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo"
                "a"("href" to "http://example.com/?search=%5D(uri)") {
                    +"http://example.com/?search=](uri)"
                }
            }
        }
        // GFM expected:
        /*
            <p>[foo<a href="http://example.com/?search=%5D(uri)">http://example.com/?search=](uri)</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 536 - paragraph foo`() = runTest {
        // given
        val textFlow = """
            [foo][bar]

            [bar]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url", "title" to "title") {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/url" title="title">foo</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 537 - paragraph link foo bar`() = runTest {
        // given
        val textFlow = """
            [link [foo [bar]]][ref]

            [ref]: /uri
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/uri") {
                    +"link [foo [bar]]"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/uri">link [foo [bar]]</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 538 - paragraph link bar`() = runTest {
        // given
        val textFlow = """
            [link \[bar][ref]

            [ref]: /uri
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/uri") {
                    +"link [bar"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/uri">link [bar</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 539 - paragraph link foo bar #`() = runTest {
        // given
        val textFlow = """
            [link *foo **bar** `#`*][ref]

            [ref]: /uri
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/uri") {
                    +"link "
                    "em" {
                        +"foo "
                        "strong" {
                            +"bar"
                        }
                        +" "
                        "code" {
                            +"#"
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/uri">link <em>foo <strong>bar</strong> <code>#</code></em></a></p>
         */
    }

    // TODO review
    @Test
    fun `example 540 - empty paragraph`() = runTest {
        // given
        val textFlow = """
            [![moon](moon.jpg)][ref]

            [ref]: /uri
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/uri") {
                    "img"("src" to "moon.jpg", "alt" to "moon") {}
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/uri"><img src="moon.jpg" alt="moon" /></a></p>
         */
    }

    // TODO review
    @Test
    fun `example 541 - paragraph foo barref`() = runTest {
        // given
        val textFlow = """
            [foo [bar](/uri)][ref]

            [ref]: /uri
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo "
                "a"("href" to "/uri") {
                    +"bar"
                }
                +"]"
                "a"("href" to "/uri") {
                    +"ref"
                }
            }
        }
        // GFM expected:
        /*
            <p>[foo <a href="/uri">bar</a>]<a href="/uri">ref</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 542 - paragraph foo bar bazref`() = runTest {
        // given
        val textFlow = """
            [foo *bar [baz][ref]*][ref]

            [ref]: /uri
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo "
                "em" {
                    +"bar "
                    "a"("href" to "/uri") {
                        +"baz"
                    }
                }
                +"]"
                "a"("href" to "/uri") {
                    +"ref"
                }
            }
        }
        // GFM expected:
        /*
            <p>[foo <em>bar <a href="/uri">baz</a></em>]<a href="/uri">ref</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 543 - paragraph foo`() = runTest {
        // given
        val textFlow = """
            *[foo*][ref]

            [ref]: /uri
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"*"
                "a"("href" to "/uri") {
                    +"foo*"
                }
            }
        }
        // GFM expected:
        /*
            <p>*<a href="/uri">foo*</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 544 - paragraph foo bar`() = runTest {
        // given
        val textFlow = """
            [foo *bar][ref]*

            [ref]: /uri
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/uri") {
                    +"foo *bar"
                }
                +"*"
            }
        }
        // GFM expected:
        /*
            <p><a href="/uri">foo *bar</a>*</p>
         */
    }

    // TODO review
    @Test
    fun `example 545 - paragraph foo`() = runTest {
        // given
        val textFlow = """
            [foo <bar attr="][ref]">

            [ref]: /uri
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo "
                "bar"("attr" to "][ref]") {
                }
            }
        }
        // GFM expected:
        /*
            <p>[foo <bar attr="][ref]"></p>
         */
    }

    // TODO review
    @Test
    fun `example 546 - paragraph fooref`() = runTest {
        // given
        val textFlow = """
            [foo`][ref]`

            [ref]: /uri
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo"
                "code" {
                    +"][ref]"
                }
            }
        }
        // GFM expected:
        /*
            <p>[foo<code>][ref]</code></p>
         */
    }

    // TODO review
    @Test
    fun `example 547 - paragraph foohttpexamplecom`() = runTest {
        // given
        val textFlow = """
            [foo<http://example.com/?search=][ref]>

            [ref]: /uri
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo"
                "a"("href" to "http://example.com/?search=%5D%5Bref%5D") {
                    +"http://example.com/?search=][ref]"
                }
            }
        }
        // GFM expected:
        /*
            <p>[foo<a href="http://example.com/?search=%5D%5Bref%5D">http://example.com/?search=][ref]</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 548 - paragraph foo`() = runTest {
        // given
        val textFlow = """
            [foo][BaR]

            [bar]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url", "title" to "title") {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/url" title="title">foo</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 549 - paragraph ẞ`() = runTest {
        // given
        val textFlow = """
            [ẞ]

            [SS]: /url
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url") {
                    +"ẞ"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/url">ẞ</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 550 - paragraph Baz`() = runTest {
        // given
        val textFlow = buildText {
            +"[Foo\n"
            +"  bar]: /url\n"
            +"\n"
            +"[Baz][Foo bar]\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url") {
                    +"Baz"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/url">Baz</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 551 - paragraph foo bar`() = runTest {
        // given
        val textFlow = """
            [foo] [bar]

            [bar]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo] "
                "a"("href" to "/url", "title" to "title") {
                    +"bar"
                }
            }
        }
        // GFM expected:
        /*
            <p>[foo] <a href="/url" title="title">bar</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 552 - paragraph foo bar`() = runTest {
        // given
        val textFlow = """
            [foo]
            [bar]

            [bar]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo]\n"
                "a"("href" to "/url", "title" to "title") {
                    +"bar"
                }
            }
        }
        // GFM expected:
        /*
            <p>[foo]
            <a href="/url" title="title">bar</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 553 - paragraph bar`() = runTest {
        // given
        val textFlow = """
            [foo]: /url1

            [foo]: /url2

            [bar][foo]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url1") {
                    +"bar"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/url1">bar</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 554 - paragraph barfoo!`() = runTest {
        // given
        val textFlow = """
            [bar][foo\!]

            [foo!]: /url
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[bar][foo!]"
            }
        }
        // GFM expected:
        /*
            <p>[bar][foo!]</p>
         */
    }

    // TODO review
    @Test
    fun `example 555 - paragraph fooref, paragraph ref uri`() = runTest {
        // given
        val textFlow = """
            [foo][ref[]

            [ref[]: /uri
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo][ref[]"
            }
            "p" {
                +"[ref[]: /uri"
            }
        }
        // GFM expected:
        /*
            <p>[foo][ref[]</p>
            <p>[ref[]: /uri</p>
         */
    }

    // TODO review
    @Test
    fun `example 556 - paragraph foorefbar, paragraph refbar uri`() = runTest {
        // given
        val textFlow = """
            [foo][ref[bar]]

            [ref[bar]]: /uri
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo][ref[bar]]"
            }
            "p" {
                +"[ref[bar]]: /uri"
            }
        }
        // GFM expected:
        /*
            <p>[foo][ref[bar]]</p>
            <p>[ref[bar]]: /uri</p>
         */
    }

    // TODO review
    @Test
    fun `example 557 - paragraph foo, paragraph foo url`() = runTest {
        // given
        val textFlow = """
            [[[foo]]]

            [[[foo]]]: /url
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[[[foo]]]"
            }
            "p" {
                +"[[[foo]]]: /url"
            }
        }
        // GFM expected:
        /*
            <p>[[[foo]]]</p>
            <p>[[[foo]]]: /url</p>
         */
    }

    // TODO review
    @Test
    fun `example 558 - paragraph foo`() = runTest {
        // given
        val textFlow = """
            [foo][ref\[]

            [ref\[]: /uri
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/uri") {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/uri">foo</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 559 - paragraph bar`() = runTest {
        // given
        val textFlow = """
            [bar\\]: /uri

            [bar\\]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/uri") {
                    +"bar\\"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/uri">bar\</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 560 - paragraph , paragraph uri`() = runTest {
        // given
        val textFlow = """
            []

            []: /uri
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[]"
            }
            "p" {
                +"[]: /uri"
            }
        }
        // GFM expected:
        /*
            <p>[]</p>
            <p>[]: /uri</p>
         */
    }

    // TODO review
    @Test
    fun `example 561 - paragraph , paragraph uri`() = runTest {
        // given
        val textFlow = buildText {
            +"[\n"
            +" ]\n"
            +"\n"
            +"[\n"
            +" ]: /uri\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[\n]"
            }
            "p" {
                +"[\n]: /uri"
            }
        }
        // GFM expected:
        /*
            <p>[
            ]</p>
            <p>[
            ]: /uri</p>
         */
    }

    // TODO review
    @Test
    fun `example 562 - paragraph foo`() = runTest {
        // given
        val textFlow = """
            [foo][]

            [foo]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url", "title" to "title") {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/url" title="title">foo</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 563 - paragraph foo bar`() = runTest {
        // given
        val textFlow = """
            [*foo* bar][]

            [*foo* bar]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url", "title" to "title") {
                    "em" {
                        +"foo"
                    }
                    +" bar"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/url" title="title"><em>foo</em> bar</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 564 - paragraph Foo`() = runTest {
        // given
        val textFlow = """
            [Foo][]

            [foo]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url", "title" to "title") {
                    +"Foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/url" title="title">Foo</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 565 - paragraph foo`() = runTest {
        // given
        val textFlow = buildText {
            +"[foo] \n"
            +"[]\n"
            +"\n"
            +"[foo]: /url \"title\"\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url", "title" to "title") {
                    +"foo"
                }
                +"\n[]"
            }
        }
        // GFM expected:
        /*
            <p><a href="/url" title="title">foo</a>
            []</p>
         */
    }

    // TODO review
    @Test
    fun `example 566 - paragraph foo`() = runTest {
        // given
        val textFlow = """
            [foo]

            [foo]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url", "title" to "title") {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/url" title="title">foo</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 567 - paragraph foo bar`() = runTest {
        // given
        val textFlow = """
            [*foo* bar]

            [*foo* bar]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url", "title" to "title") {
                    "em" {
                        +"foo"
                    }
                    +" bar"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/url" title="title"><em>foo</em> bar</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 568 - paragraph foo bar`() = runTest {
        // given
        val textFlow = """
            [[*foo* bar]]

            [*foo* bar]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"["
                "a"("href" to "/url", "title" to "title") {
                    "em" {
                        +"foo"
                    }
                    +" bar"
                }
                +"]"
            }
        }
        // GFM expected:
        /*
            <p>[<a href="/url" title="title"><em>foo</em> bar</a>]</p>
         */
    }

    // TODO review
    @Test
    fun `example 569 - paragraph bar foo`() = runTest {
        // given
        val textFlow = """
            [[bar [foo]

            [foo]: /url
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[[bar "
                "a"("href" to "/url") {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p>[[bar <a href="/url">foo</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 570 - paragraph Foo`() = runTest {
        // given
        val textFlow = """
            [Foo]

            [foo]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url", "title" to "title") {
                    +"Foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/url" title="title">Foo</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 571 - paragraph foo bar`() = runTest {
        // given
        val textFlow = """
            [foo] bar

            [foo]: /url
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url") {
                    +"foo"
                }
                +" bar"
            }
        }
        // GFM expected:
        /*
            <p><a href="/url">foo</a> bar</p>
         */
    }

    // TODO review
    @Test
    fun `example 572 - paragraph foo`() = runTest {
        // given
        val textFlow = """
            \[foo]

            [foo]: /url "title"
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo]"
            }
        }
        // GFM expected:
        /*
            <p>[foo]</p>
         */
    }

    // TODO review
    @Test
    fun `example 573 - paragraph foo`() = runTest {
        // given
        val textFlow = """
            [foo*]: /url

            *[foo*]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"*"
                "a"("href" to "/url") {
                    +"foo*"
                }
            }
        }
        // GFM expected:
        /*
            <p>*<a href="/url">foo*</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 574 - paragraph foo`() = runTest {
        // given
        val textFlow = """
            [foo][bar]

            [foo]: /url1
            [bar]: /url2
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url2") {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/url2">foo</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 575 - paragraph foo`() = runTest {
        // given
        val textFlow = """
            [foo][]

            [foo]: /url1
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url1") {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/url1">foo</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 576 - paragraph foo`() = runTest {
        // given
        val textFlow = """
            [foo]()

            [foo]: /url1
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "") {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="">foo</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 577 - paragraph foo(not a link)`() = runTest {
        // given
        val textFlow = """
            [foo](not a link)

            [foo]: /url1
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url1") {
                    +"foo"
                }
                +"(not a link)"
            }
        }
        // GFM expected:
        /*
            <p><a href="/url1">foo</a>(not a link)</p>
         */
    }

    // TODO review
    @Test
    fun `example 578 - paragraph foobar`() = runTest {
        // given
        val textFlow = """
            [foo][bar][baz]

            [baz]: /url
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo]"
                "a"("href" to "/url") {
                    +"bar"
                }
            }
        }
        // GFM expected:
        /*
            <p>[foo]<a href="/url">bar</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 579 - paragraph foobaz`() = runTest {
        // given
        val textFlow = """
            [foo][bar][baz]

            [baz]: /url1
            [bar]: /url2
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url2") {
                    +"foo"
                }
                "a"("href" to "/url1") {
                    +"baz"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/url2">foo</a><a href="/url1">baz</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 580 - paragraph foobar`() = runTest {
        // given
        val textFlow = """
            [foo][bar][baz]

            [baz]: /url1
            [foo]: /url2
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo]"
                "a"("href" to "/url1") {
                    +"bar"
                }
            }
        }
        // GFM expected:
        /*
            <p>[foo]<a href="/url1">bar</a></p>
         */
    }

}
