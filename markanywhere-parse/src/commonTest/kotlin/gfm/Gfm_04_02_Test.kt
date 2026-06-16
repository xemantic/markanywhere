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
 * Tests for GFM Section 04.02 — ATX headings.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#atx-headings
 */
@Suppress("ClassName")
class Gfm_04_02_Test {

    @Test
    fun `example 32 - h1 foo h2 foo h3 foo h4 foo h5 foo h6 foo`() = runTest {
        // given
        val textFlow = """
            # foo
            ## foo
            ### foo
            #### foo
            ##### foo
            ###### foo
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h1" { +"foo" }
            "h2" { +"foo" }
            "h3" { +"foo" }
            "h4" { +"foo" }
            "h5" { +"foo" }
            "h6" { +"foo" }
        }
        // GFM expected:
        /*
            <h1>foo</h1>
            <h2>foo</h2>
            <h3>foo</h3>
            <h4>foo</h4>
            <h5>foo</h5>
            <h6>foo</h6>
         */
    }

    @Test
    fun `example 33 - paragraph foo`() = runTest {
        // given
        val textFlow = "####### foo".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"####### foo"
            }
        }
        // GFM expected:
        /*
            <p>####### foo</p>
         */
    }

    @Test
    fun `example 34 - paragraph 5 bolt paragraph hashtag`() = runTest {
        // given
        val textFlow = """
            #5 bolt

            #hashtag
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"#5 bolt"
            }
            "p" {
                +"#hashtag"
            }
        }
        // GFM expected:
        /*
            <p>#5 bolt</p>
            <p>#hashtag</p>
         */
    }

    @Test
    fun `example 35 - paragraph foo`() = runTest {
        // given
        val textFlow = "\\## foo\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"## foo"
            }
        }
        // GFM expected:
        /*
            <p>## foo</p>
         */
    }

    @Test
    fun `example 36 - h1 foo bar baz`() = runTest {
        // given
        val textFlow = buildString {
            +"# foo *bar* \\*baz\\*\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h1" {
                +"foo "
                "em" {
                    +"bar"
                }
                +" *baz*"
            }
        }
        // GFM expected:
        /*
            <h1>foo <em>bar</em> *baz*</h1>
         */
    }

    @Test
    fun `example 37 - h1 foo`() = runTest {
        // given
        val textFlow = buildString {
            +"#                  foo                     \n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h1" {
                +"foo"
            }
        }
        // GFM expected:
        /*
            <h1>foo</h1>
         */
    }

    @Test
    fun `example 38 - h3 foo h2 foo h1 foo`() = runTest {
        // given
        val textFlow = buildString {
            +" ### foo\n"
            +"  ## foo\n"
            +"   # foo\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h3" {
                +"foo"
            }
            "h2" {
                +"foo"
            }
            "h1" {
                +"foo"
            }
        }
        // GFM expected:
        /*
            <h3>foo</h3>
            <h2>foo</h2>
            <h1>foo</h1>
         */
    }

    @Test
    fun `example 39 - indented code block`() = runTest {
        // given
        val textFlow = buildString {
            +"    # foo\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"# foo\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code># foo
            </code></pre>
         */
    }

    @Test
    fun `example 40 - paragraph foo bar`() = runTest {
        // given
        val textFlow = buildString {
            +"foo\n"
            +"    # bar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo\n# bar"
            }
        }
        // GFM expected:
        /*
            <p>foo
            # bar</p>
         */
    }

    @Test
    fun `example 41 - h2 foo h3 bar`() = runTest {
        // given
        val textFlow = buildString {
            +"## foo ##\n"
            +"  ###   bar    ###\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h2" {
                +"foo"
            }
            "h3" {
                +"bar"
            }
        }
        // GFM expected:
        /*
            <h2>foo</h2>
            <h3>bar</h3>
         */
    }

    @Test
    fun `example 42 - h1 foo h5 foo`() = runTest {
        // given
        val textFlow = """
            # foo ##################################
            ##### foo ##
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h1" {
                +"foo"
            }
            "h5" {
                +"foo"
            }
        }
        // GFM expected:
        /*
            <h1>foo</h1>
            <h5>foo</h5>
         */
    }

    @Test
    fun `example 43 - h3 foo`() = runTest {
        // given
        val textFlow = buildString {
            +"### foo ###     \n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h3" {
                +"foo"
            }
        }
        // GFM expected:
        /*
            <h3>foo</h3>
         */
    }

    @Test
    fun `example 44 - h3 foo b`() = runTest {
        // given
        val textFlow = "### foo ### b".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h3" {
                +"foo ### b"
            }
        }
        // GFM expected:
        /*
            <h3>foo ### b</h3>
         */
    }

    @Test
    fun `example 45 - h1 foo`() = runTest {
        // given
        val textFlow = "# foo#".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h1" {
                +"foo#"
            }
        }
        // GFM expected:
        /*
            <h1>foo#</h1>
         */
    }

    @Test
    fun `example 46 - h3 foo h2 foo h1 foo`() = runTest {
        // given
        val textFlow = """
            ### foo \###
            ## foo #\##
            # foo \#
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h3" {
                +"foo ###"
            }
            "h2" {
                +"foo ###"
            }
            "h1" {
                +"foo #"
            }
        }
        // GFM expected:
        /*
            <h3>foo ###</h3>
            <h2>foo ###</h2>
            <h1>foo #</h1>
         */
    }

    @Test
    fun `example 47 - thematic break h2 foo thematic break`() = runTest {
        // given
        val textFlow = """
            ****
            ## foo
            ****
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "hr" {}
            "h2" {
                +"foo"
            }
            "hr" {}
        }
        // GFM expected:
        /*
            <hr />
            <h2>foo</h2>
            <hr />
         */
    }

    @Test
    fun `example 48 - paragraph Foo bar h1 baz paragraph Bar foo`() = runTest {
        // given
        val textFlow = """
            Foo bar
            # baz
            Bar foo
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo bar"
            }
            "h1" {
                +"baz"
            }
            "p" {
                +"Bar foo"
            }
        }
        // GFM expected:
        /*
            <p>Foo bar</p>
            <h1>baz</h1>
            <p>Bar foo</p>
         */
    }

    @Test
    fun `example 49 - h2 empty h1 empty h3 empty`() = runTest {
        // given
        val textFlow = buildString {
            +"## \n"
            +"#\n"
            +"### ###\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h2" {
            }
            "h1" {
            }
            "h3" {
            }
        }
        // GFM expected:
        /*
            <h2></h2>
            <h1></h1>
            <h3></h3>
         */
    }

}
