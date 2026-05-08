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

import com.xemantic.kotlin.test.text.chunkedRandomly
import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.parse.parse
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Tests for GFM Section 06.09 — Autolinks (extension).
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#autolinks-extension-
 */
@Suppress("ClassName")
class Gfm_06_09_Test {

    @Test
    fun `example 622 - paragraph wwwcommonmarkorg`() = runTest {
        // given
        val textFlow = "www.commonmark.org".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "http://www.commonmark.org") {
                    +"www.commonmark.org"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="http://www.commonmark.org">www.commonmark.org</a></p>
         */
    }

    @Test
    fun `example 623 - paragraph Visit wwwcommonmarkor`() = runTest {
        // given
        val textFlow = "Visit www.commonmark.org/help for more information.".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Visit "
                "a"("href" to "http://www.commonmark.org/help") {
                    +"www.commonmark.org/help"
                }
                +" for more information."
            }
        }
        // GFM expected:
        /*
            <p>Visit <a href="http://www.commonmark.org/help">www.commonmark.org/help</a> for more information.</p>
         */
    }

    @Test
    fun `example 624 - two paragraph Visit wwwcommonmarkors`() = runTest {
        // given
        val textFlow = """
            Visit www.commonmark.org.

            Visit www.commonmark.org/a.b.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Visit "
                "a"("href" to "http://www.commonmark.org") {
                    +"www.commonmark.org"
                }
                +"."
            }
            "p" {
                +"Visit "
                "a"("href" to "http://www.commonmark.org/a.b") {
                    +"www.commonmark.org/a.b"
                }
                +"."
            }
        }
        // GFM expected:
        /*
            <p>Visit <a href="http://www.commonmark.org">www.commonmark.org</a>.</p>
            <p>Visit <a href="http://www.commonmark.org/a.b">www.commonmark.org/a.b</a>.</p>
         */
    }

    @Test
    fun `example 625 - paragraph wwwgooglecomsearchq paragraph wwwgooglecomsearchq paragrap truncated`() = runTest {
        // given
        val textFlow = """
            www.google.com/search?q=Markup+(business)

            www.google.com/search?q=Markup+(business)))

            (www.google.com/search?q=Markup+(business))

            (www.google.com/search?q=Markup+(business)
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "http://www.google.com/search?q=Markup+(business)") {
                    +"www.google.com/search?q=Markup+(business)"
                }
            }
            "p" {
                "a"("href" to "http://www.google.com/search?q=Markup+(business)") {
                    +"www.google.com/search?q=Markup+(business)"
                }
                +"))"
            }
            "p" {
                +"("
                "a"("href" to "http://www.google.com/search?q=Markup+(business)") {
                    +"www.google.com/search?q=Markup+(business)"
                }
                +")"
            }
            "p" {
                +"("
                "a"("href" to "http://www.google.com/search?q=Markup+(business)") {
                    +"www.google.com/search?q=Markup+(business)"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="http://www.google.com/search?q=Markup+(business)">www.google.com/search?q=Markup+(business)</a></p>
            <p><a href="http://www.google.com/search?q=Markup+(business)">www.google.com/search?q=Markup+(business)</a>))</p>
            <p>(<a href="http://www.google.com/search?q=Markup+(business)">www.google.com/search?q=Markup+(business)</a>)</p>
            <p>(<a href="http://www.google.com/search?q=Markup+(business)">www.google.com/search?q=Markup+(business)</a></p>
         */
    }

    @Test
    fun `example 626 - paragraph wwwgooglecomsearchq`() = runTest {
        // given
        val textFlow = "www.google.com/search?q=(business))+ok".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "http://www.google.com/search?q=(business))+ok") {
                    +"www.google.com/search?q=(business))+ok"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="http://www.google.com/search?q=(business))+ok">www.google.com/search?q=(business))+ok</a></p>
         */
    }

    @Test
    fun `example 627 - two paragraph wwwgooglecomsearchqs`() = runTest {
        // given
        val textFlow = """
            www.google.com/search?q=commonmark&hl=en

            www.google.com/search?q=commonmark&hl;
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "http://www.google.com/search?q=commonmark&hl=en") {
                    +"www.google.com/search?q=commonmark&hl=en"
                }
            }
            "p" {
                "a"("href" to "http://www.google.com/search?q=commonmark") {
                    +"www.google.com/search?q=commonmark"
                }
                +"&hl;"
            }
        }
        // GFM expected:
        /*
            <p><a href="http://www.google.com/search?q=commonmark&amp;hl=en">www.google.com/search?q=commonmark&amp;hl=en</a></p>
            <p><a href="http://www.google.com/search?q=commonmark">www.google.com/search?q=commonmark</a>&amp;hl;</p>
         */
    }

    @Test
    fun `example 628 - paragraph wwwcommonmarkorghelp`() = runTest {
        // given
        val textFlow = "www.commonmark.org/he<lp".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "http://www.commonmark.org/he") {
                    +"www.commonmark.org/he"
                }
                +"<lp"
            }
        }
        // GFM expected:
        /*
            <p><a href="http://www.commonmark.org/he">www.commonmark.org/he</a>&lt;lp</p>
         */
    }

    @Test
    fun `example 629 - paragraph httpcommonmarkorg paragraph Visit httpsencrypte`() = runTest {
        // given
        val textFlow = """
            http://commonmark.org

            (Visit https://encrypted.google.com/search?q=Markup+(business))
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "http://commonmark.org") {
                    +"http://commonmark.org"
                }
            }
            "p" {
                +"(Visit "
                "a"("href" to "https://encrypted.google.com/search?q=Markup+(business)") {
                    +"https://encrypted.google.com/search?q=Markup+(business)"
                }
                +")"
            }
        }
        // GFM expected:
        /*
            <p><a href="http://commonmark.org">http://commonmark.org</a></p>
            <p>(Visit <a href="https://encrypted.google.com/search?q=Markup+(business)">https://encrypted.google.com/search?q=Markup+(business)</a>)</p>
         */
    }

    @Test
    fun `example 630 - paragraph foobarbaz`() = runTest {
        // given
        val textFlow = "foo@bar.baz".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "mailto:foo@bar.baz") {
                    +"foo@bar.baz"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="mailto:foo@bar.baz">foo@bar.baz</a></p>
         */
    }

    @Test
    fun `example 631 - paragraph hellomail+xyzexample`() = runTest {
        // given
        val textFlow = "hello@mail+xyz.example isn't valid, but hello+xyz@mail.example is.".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"hello@mail+xyz.example isn't valid, but "
                "a"("href" to "mailto:hello+xyz@mail.example") {
                    +"hello+xyz@mail.example"
                }
                +" is."
            }
        }
        // GFM expected:
        /*
            <p>hello@mail+xyz.example isn't valid, but <a href="mailto:hello+xyz@mail.example">hello+xyz@mail.example</a> is.</p>
         */
    }

    @Test
    fun `example 632 - paragraph ab-c_dab paragraph ab-c_dab paragraph ab-c_dab- paragr truncated`() = runTest {
        // given
        val textFlow = """
            a.b-c_d@a.b

            a.b-c_d@a.b.

            a.b-c_d@a.b-

            a.b-c_d@a.b_
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "mailto:a.b-c_d@a.b") {
                    +"a.b-c_d@a.b"
                }
            }
            "p" {
                "a"("href" to "mailto:a.b-c_d@a.b") {
                    +"a.b-c_d@a.b"
                }
                +"."
            }
            "p" {
                +"a.b-c_d@a.b-"
            }
            "p" {
                +"a.b-c_d@a.b_"
            }
        }
        // GFM expected:
        /*
            <p><a href="mailto:a.b-c_d@a.b">a.b-c_d@a.b</a></p>
            <p><a href="mailto:a.b-c_d@a.b">a.b-c_d@a.b</a>.</p>
            <p>a.b-c_d@a.b-</p>
            <p>a.b-c_d@a.b_</p>
         */
    }

    @Test
    fun `example 633 - paragraph mailtofoobarbaz paragraph mailtoab-c_dab paragraph mailt truncated`() = runTest {
        // given
        val textFlow = """
            mailto:foo@bar.baz

            mailto:a.b-c_d@a.b

            mailto:a.b-c_d@a.b.

            mailto:a.b-c_d@a.b/

            mailto:a.b-c_d@a.b-

            mailto:a.b-c_d@a.b_

            xmpp:foo@bar.baz

            xmpp:foo@bar.baz.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "mailto:foo@bar.baz") {
                    +"mailto:foo@bar.baz"
                }
            }
            "p" {
                "a"("href" to "mailto:a.b-c_d@a.b") {
                    +"mailto:a.b-c_d@a.b"
                }
            }
            "p" {
                "a"("href" to "mailto:a.b-c_d@a.b") {
                    +"mailto:a.b-c_d@a.b"
                }
                +"."
            }
            "p" {
                "a"("href" to "mailto:a.b-c_d@a.b") {
                    +"mailto:a.b-c_d@a.b"
                }
                +"/"
            }
            "p" {
                +"mailto:a.b-c_d@a.b-"
            }
            "p" {
                +"mailto:a.b-c_d@a.b_"
            }
            "p" {
                "a"("href" to "xmpp:foo@bar.baz") {
                    +"xmpp:foo@bar.baz"
                }
            }
            "p" {
                "a"("href" to "xmpp:foo@bar.baz") {
                    +"xmpp:foo@bar.baz"
                }
                +"."
            }
        }
        // GFM expected:
        /*
            <p><a href="mailto:foo@bar.baz">mailto:foo@bar.baz</a></p>
            <p><a href="mailto:a.b-c_d@a.b">mailto:a.b-c_d@a.b</a></p>
            <p><a href="mailto:a.b-c_d@a.b">mailto:a.b-c_d@a.b</a>.</p>
            <p><a href="mailto:a.b-c_d@a.b">mailto:a.b-c_d@a.b</a>/</p>
            <p>mailto:a.b-c_d@a.b-</p>
            <p>mailto:a.b-c_d@a.b_</p>
            <p><a href="xmpp:foo@bar.baz">xmpp:foo@bar.baz</a></p>
            <p><a href="xmpp:foo@bar.baz">xmpp:foo@bar.baz</a>.</p>
         */
    }

    @Test
    fun `example 634 - paragraph xmppfoobarbaztxt paragraph xmppfoobarbaztxtbin paragrap truncated`() = runTest {
        // given
        val textFlow = """
            xmpp:foo@bar.baz/txt

            xmpp:foo@bar.baz/txt@bin

            xmpp:foo@bar.baz/txt@bin.com
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "xmpp:foo@bar.baz/txt") {
                    +"xmpp:foo@bar.baz/txt"
                }
            }
            "p" {
                "a"("href" to "xmpp:foo@bar.baz/txt@bin") {
                    +"xmpp:foo@bar.baz/txt@bin"
                }
            }
            "p" {
                "a"("href" to "xmpp:foo@bar.baz/txt@bin.com") {
                    +"xmpp:foo@bar.baz/txt@bin.com"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="xmpp:foo@bar.baz/txt">xmpp:foo@bar.baz/txt</a></p>
            <p><a href="xmpp:foo@bar.baz/txt@bin">xmpp:foo@bar.baz/txt@bin</a></p>
            <p><a href="xmpp:foo@bar.baz/txt@bin.com">xmpp:foo@bar.baz/txt@bin.com</a></p>
         */
    }

    @Test
    fun `example 635 - paragraph xmppfoobarbaztxtbin`() = runTest {
        // given
        val textFlow = "xmpp:foo@bar.baz/txt/bin".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "xmpp:foo@bar.baz/txt") {
                    +"xmpp:foo@bar.baz/txt"
                }
                +"/bin"
            }
        }
        // GFM expected:
        /*
            <p><a href="xmpp:foo@bar.baz/txt">xmpp:foo@bar.baz/txt</a>/bin</p>
         */
    }

}
