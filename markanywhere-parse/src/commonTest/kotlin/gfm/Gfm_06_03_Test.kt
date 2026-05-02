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
 * Tests for GFM Section 06.03 — Code spans.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#code-spans
 */
@Suppress("ClassName")
class Gfm_06_03_Test {

    // TODO review
    @Test
    fun `example 338 - paragraph foo`() = runTest {
        // given
        val textFlow = "`foo`".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><code>foo</code></p>
         */
    }

    // TODO review
    @Test
    fun `example 339 - paragraph foo bar`() = runTest {
        // given
        val textFlow = "`` foo ` bar ``".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"foo ` bar"
                }
            }
        }
        // GFM expected:
        /*
            <p><code>foo ` bar</code></p>
         */
    }

    // TODO review
    @Test
    fun `example 340 - paragraph`() = runTest {
        // given
        val textFlow = "` `` `".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"``"
                }
            }
        }
        // GFM expected:
        /*
            <p><code>``</code></p>
         */
    }

    // TODO review
    @Test
    fun `example 341 - paragraph`() = runTest {
        // given
        val textFlow = "`  ``  `".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +" `` "
                }
            }
        }
        // GFM expected:
        /*
            <p><code> `` </code></p>
         */
    }

    // TODO review
    @Test
    fun `example 342 - paragraph a`() = runTest {
        // given
        val textFlow = "` a`".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +" a"
                }
            }
        }
        // GFM expected:
        /*
            <p><code> a</code></p>
         */
    }

    // TODO review
    @Test
    fun `example 343 - paragraph b`() = runTest {
        // given
        val textFlow = "` b `".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +" b "
                }
            }
        }
        // GFM expected:
        /*
            <p><code> b </code></p>
         */
    }

    // TODO review
    @Test
    fun `example 344 - empty paragraph`() = runTest {
        // given
        val textFlow = """
            ` `
            `  `
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +" "
                }
                +"\n"
                "code" {
                    +"  "
                }
            }
        }
        // GFM expected:
        /*
            <p><code> </code>
            <code>  </code></p>
         */
    }

    // TODO review
    @Test
    fun `example 345 - paragraph foo bar baz`() = runTest {
        // given
        val textFlow = buildText {
            +"``\n"
            +"foo\n"
            +"bar  \n"
            +"baz\n"
            +"``\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"foo bar   baz"
                }
            }
        }
        // GFM expected:
        /*
            <p><code>foo bar   baz</code></p>
         */
    }

    // TODO review
    @Test
    fun `example 346 - paragraph foo`() = runTest {
        // given
        val textFlow = buildText {
            +"``\n"
            +"foo \n"
            +"``\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"foo "
                }
            }
        }
        // GFM expected:
        /*
            <p><code>foo </code></p>
         */
    }

    // TODO review
    @Test
    fun `example 347 - paragraph foo bar baz`() = runTest {
        // given
        val textFlow = buildText {
            +"`foo   bar \n"
            +"baz`\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"foo   bar  baz"
                }
            }
        }
        // GFM expected:
        /*
            <p><code>foo   bar  baz</code></p>
         */
    }

    // TODO review
    @Test
    fun `example 348 - paragraph foobar`() = runTest {
        // given
        val textFlow = buildText {
            +"`foo\\`bar`\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"foo\\"
                }
                +"bar`"
            }
        }
        // GFM expected:
        /*
            <p><code>foo\</code>bar`</p>
         */
    }

    // TODO review
    @Test
    fun `example 349 - paragraph foobar`() = runTest {
        // given
        val textFlow = "``foo`bar``".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"foo`bar"
                }
            }
        }
        // GFM expected:
        /*
            <p><code>foo`bar</code></p>
         */
    }

    // TODO review
    @Test
    fun `example 350 - paragraph foo bar`() = runTest {
        // given
        val textFlow = "` foo `` bar `".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"foo `` bar"
                }
            }
        }
        // GFM expected:
        /*
            <p><code>foo `` bar</code></p>
         */
    }

    // TODO review
    @Test
    fun `example 351 - paragraph foo`() = runTest {
        // given
        val textFlow = "*foo`*`".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"*foo"
                "code" {
                    +"*"
                }
            }
        }
        // GFM expected:
        /*
            <p>*foo<code>*</code></p>
         */
    }

    // TODO review
    @Test
    fun `example 352 - paragraph not a link(foo)`() = runTest {
        // given
        val textFlow = "[not a `link](/foo`)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[not a "
                "code" {
                    +"link](/foo"
                }
                +")"
            }
        }
        // GFM expected:
        /*
            <p>[not a <code>link](/foo</code>)</p>
         */
    }

    // TODO review
    @Test
    fun `example 353 - paragraph a href=`() = runTest {
        // given
        val textFlow = """`<a href="`">`""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"<a href=\""
                }
                +"\">`"
            }
        }
        // GFM expected:
        /*
            <p><code>&lt;a href=&quot;</code>&quot;&gt;`</p>
         */
    }

    // TODO review
    @Test
    fun `example 354 - paragraph`() = runTest {
        // given
        val textFlow = """<a href="`">`""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "`") {
                    +"`"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="`">`</p>
         */
    }

    // TODO review
    @Test
    fun `example 355 - paragraph httpfoobarbaz`() = runTest {
        // given
        val textFlow = "`<http://foo.bar.`baz>`".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"<http://foo.bar."
                }
                +"baz>`"
            }
        }
        // GFM expected:
        /*
            <p><code>&lt;http://foo.bar.</code>baz&gt;`</p>
         */
    }

    // TODO review
    @Test
    fun `example 356 - paragraph httpfoobarbaz`() = runTest {
        // given
        val textFlow = "<http://foo.bar.`baz>`".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "http://foo.bar.%60baz") {
                    +"http://foo.bar.`baz"
                }
                +"`"
            }
        }
        // GFM expected:
        /*
            <p><a href="http://foo.bar.%60baz">http://foo.bar.`baz</a>`</p>
         */
    }

    // TODO review
    @Test
    fun `example 357 - paragraph foo`() = runTest {
        // given
        val textFlow = "```foo``".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"```foo``"
            }
        }
        // GFM expected:
        /*
            <p>```foo``</p>
         */
    }

    // TODO review
    @Test
    fun `example 358 - paragraph foo`() = runTest {
        // given
        val textFlow = "`foo".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"`foo"
            }
        }
        // GFM expected:
        /*
            <p>`foo</p>
         */
    }

    // TODO review
    @Test
    fun `example 359 - paragraph foobar`() = runTest {
        // given
        val textFlow = "`foo``bar``".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"`foo"
                "code" {
                    +"bar"
                }
            }
        }
        // GFM expected:
        /*
            <p>`foo<code>bar</code></p>
         */
    }

}
