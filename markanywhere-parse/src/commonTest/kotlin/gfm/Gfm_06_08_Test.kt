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
 * Tests for GFM Section 06.08 — Autolinks.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#autolinks
 */
@Suppress("ClassName")
class Gfm_06_08_Test {

    // TODO review
    @Test
    fun `example 603 - paragraph httpfoobarbaz`() = runTest {
        // given
        val textFlow = "<http://foo.bar.baz>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "http://foo.bar.baz") {
                    +"http://foo.bar.baz"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="http://foo.bar.baz">http://foo.bar.baz</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 604 - paragraph httpfoobarbaztest`() = runTest {
        // given
        val textFlow = "<http://foo.bar.baz/test?q=hello&id=22&boolean>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "http://foo.bar.baz/test?q=hello&id=22&boolean") {
                    +"http://foo.bar.baz/test?q=hello&id=22&boolean"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="http://foo.bar.baz/test?q=hello&amp;id=22&amp;boolean">http://foo.bar.baz/test?q=hello&amp;id=22&amp;boolean</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 605 - paragraph ircfoobar2233baz`() = runTest {
        // given
        val textFlow = "<irc://foo.bar:2233/baz>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "irc://foo.bar:2233/baz") {
                    +"irc://foo.bar:2233/baz"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="irc://foo.bar:2233/baz">irc://foo.bar:2233/baz</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 606 - paragraph MAILTOFOO@BARBAZ`() = runTest {
        // given
        val textFlow = "<MAILTO:FOO@BAR.BAZ>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "MAILTO:FOO@BAR.BAZ") {
                    +"MAILTO:FOO@BAR.BAZ"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="MAILTO:FOO@BAR.BAZ">MAILTO:FOO@BAR.BAZ</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 607 - paragraph a+b+cd`() = runTest {
        // given
        val textFlow = "<a+b+c:d>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "a+b+c:d") {
                    +"a+b+c:d"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="a+b+c:d">a+b+c:d</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 608 - paragraph made-up-schemefoo,bar`() = runTest {
        // given
        val textFlow = "<made-up-scheme://foo,bar>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "made-up-scheme://foo,bar") {
                    +"made-up-scheme://foo,bar"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="made-up-scheme://foo,bar">made-up-scheme://foo,bar</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 609 - paragraph http`() = runTest {
        // given
        val textFlow = "<http://../>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "http://../") {
                    +"http://../"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="http://../">http://../</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 610 - paragraph localhost5001foo`() = runTest {
        // given
        val textFlow = "<localhost:5001/foo>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "localhost:5001/foo") {
                    +"localhost:5001/foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="localhost:5001/foo">localhost:5001/foo</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 611 - paragraph httpfoobarbaz bim`() = runTest {
        // given
        val textFlow = "<http://foo.bar/baz bim>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"<http://foo.bar/baz bim>"
            }
        }
        // GFM expected:
        /*
            <p>&lt;http://foo.bar/baz bim&gt;</p>
         */
    }

    // TODO review
    @Test
    fun `example 612 - paragraph httpexamplecom`() = runTest {
        // given
        val textFlow = buildText {
            +"<http://example.com/\\[\\>\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "http://example.com/%5C%5B%5C") {
                    +"http://example.com/\\[\\"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="http://example.com/%5C%5B%5C">http://example.com/\[\</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 613 - paragraph foo@barexamplecom`() = runTest {
        // given
        val textFlow = "<foo@bar.example.com>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "mailto:foo@bar.example.com") {
                    +"foo@bar.example.com"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="mailto:foo@bar.example.com">foo@bar.example.com</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 614 - paragraph foo+special@Barbaz-bar`() = runTest {
        // given
        val textFlow = "<foo+special@Bar.baz-bar0.com>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "mailto:foo+special@Bar.baz-bar0.com") {
                    +"foo+special@Bar.baz-bar0.com"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="mailto:foo+special@Bar.baz-bar0.com">foo+special@Bar.baz-bar0.com</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 615 - paragraph foo+@barexamplecom`() = runTest {
        // given
        val textFlow = buildText {
            +"<foo\\+@bar.example.com>\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"<foo+@bar.example.com>"
            }
        }
        // GFM expected:
        /*
            <p>&lt;foo+@bar.example.com&gt;</p>
         */
    }

    // TODO review
    @Test
    fun `example 616 - paragraph`() = runTest {
        // given
        val textFlow = "<>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"<>"
            }
        }
        // GFM expected:
        /*
            <p>&lt;&gt;</p>
         */
    }

    // CommonMark §6.8 expects no link (the `< … >` form fails the autolink
    // shape because of the surrounding spaces). With GFM §6.9 extended
    // autolinks active, the bare `http://foo.bar` is recognised because it
    // sits between whitespace boundaries.
    @Test
    fun `example 617 - paragraph httpfoobar`() = runTest {
        // given
        val textFlow = "< http://foo.bar >".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"< "
                "a"("href" to "http://foo.bar") {
                    +"http://foo.bar"
                }
                +" >"
            }
        }
        // GFM expected (CommonMark §6.8, no extended autolinks):
        /*
            <p>&lt; http://foo.bar &gt;</p>
         */
    }

    // DIVERGENCE: GFM expects `<m:abc>` to render as literal text because a
    // 1-letter scheme is too short for a URI autolink and `:` is not allowed in
    // a standard HTML tag name. This parser's custom-markup extension (see
    // `parseCustomMarkupOpeningTag` and the `<foo:bar>...</foo:bar>` block test)
    // intentionally treats a `<namespace:name>` line at block boundary as a
    // custom-markup opener.
    @Test
    fun `example 618 - DIVERGENCE - paragraph mabc as custom markup opener`() = runTest {
        // given
        val textFlow = "<m:abc>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            "m:abc" {}
        }
        // GFM expected:
        /*
            <p>&lt;m:abc&gt;</p>
         */
    }

    // TODO review
    @Test
    fun `example 619 - paragraph foobarbaz`() = runTest {
        // given
        val textFlow = "<foo.bar.baz>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"<foo.bar.baz>"
            }
        }
        // GFM expected:
        /*
            <p>&lt;foo.bar.baz&gt;</p>
         */
    }

    // CommonMark §6.8 expects no autolink (only the `<URL>` form is
    // recognised). With GFM §6.9 extended autolinks active, a bare URL
    // beginning with `http://` is wrapped in `<a>`.
    @Test
    fun `example 620 - paragraph httpexamplecom`() = runTest {
        // given
        val textFlow = "http://example.com".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "http://example.com") {
                    +"http://example.com"
                }
            }
        }
        // GFM expected (CommonMark §6.8, no extended autolinks):
        /*
            <p>http://example.com</p>
         */
    }

    // CommonMark §6.8 expects no autolink for a bare email. With GFM §6.9
    // extended autolinks active, the address is wrapped in `<a href="mailto:…">`.
    @Test
    fun `example 621 - paragraph foo@barexamplecom`() = runTest {
        // given
        val textFlow = "foo@bar.example.com".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "mailto:foo@bar.example.com") {
                    +"foo@bar.example.com"
                }
            }
        }
        // GFM expected (CommonMark §6.8, no extended autolinks):
        /*
            <p>foo@bar.example.com</p>
         */
    }

}
