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
 * Tests for GFM Section 04.07 — Link reference definitions.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#link-reference-definitions
 *
 * DIVERGENCE: Link reference definitions are not supported (see README — Divergences from GFM)
 * Tests in this file document the divergence — they capture the spec input
 * and the GFM expected output for reference, but assertions reflect what
 * markanywhere-parse actually emits, not GFM.
 */
@Suppress("ClassName")
class Gfm_04_07_Test {

    // TODO review
    @Test
    fun `example 161 - DIVERGENCE`() = runTest {
        // given
        val textFlow = """
            [foo]: /url "title"

            [foo]
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
    fun `example 162 - DIVERGENCE`() = runTest {
        // given
        val textFlow = buildText {
            +"   [foo]: \n"
            +"      /url  \n"
            +"           'the title'  \n"
            +"\n"
            +"[foo]\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url", "title" to "the title") {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/url" title="the title">foo</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 163 - DIVERGENCE`() = runTest {
        // given
        val textFlow = """
            [Foo*bar\]]:my_(url) 'title (with parens)'

            [Foo*bar\]]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "my_(url)", "title" to "title (with parens)") {
                    +"Foo*bar]"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="my_(url)" title="title (with parens)">Foo*bar]</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 164 - DIVERGENCE`() = runTest {
        // given
        val textFlow = """
            [Foo bar]:
            <my url>
            'title'

            [Foo bar]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "my%20url", "title" to "title") {
                    +"Foo bar"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="my%20url" title="title">Foo bar</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 165 - DIVERGENCE`() = runTest {
        // given
        val textFlow = """
            [foo]: /url '
            title
            line1
            line2
            '

            [foo]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url", "title" to "\ntitle\nline1\nline2\n") {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/url" title="
            title
            line1
            line2
            ">foo</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 166 - DIVERGENCE`() = runTest {
        // given
        val textFlow = """
            [foo]: /url 'title

            with blank line'

            [foo]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo]: /url 'title"
            }
            "p" {
                +"with blank line'"
            }
            "p" {
                +"[foo]"
            }
        }
        // GFM expected:
        /*
            <p>[foo]: /url 'title</p>
            <p>with blank line'</p>
            <p>[foo]</p>
         */
    }

    // TODO review
    @Test
    fun `example 167 - DIVERGENCE`() = runTest {
        // given
        val textFlow = """
            [foo]:
            /url

            [foo]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url") {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/url">foo</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 168 - DIVERGENCE`() = runTest {
        // given
        val textFlow = """
            [foo]:

            [foo]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo]:"
            }
            "p" {
                +"[foo]"
            }
        }
        // GFM expected:
        /*
            <p>[foo]:</p>
            <p>[foo]</p>
         */
    }

    // TODO review
    @Test
    fun `example 169 - DIVERGENCE`() = runTest {
        // given
        val textFlow = """
            [foo]: <>

            [foo]
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
    fun `example 170 - DIVERGENCE`() = runTest {
        // given
        val textFlow = """
            [foo]: <bar>(baz)

            [foo]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo]: "
                "bar" {
                    +"(baz)"
                }
            }
            "p" {
                +"[foo]"
            }
        }
        // GFM expected:
        /*
            <p>[foo]: <bar>(baz)</p>
            <p>[foo]</p>
         */
    }

    // TODO review
    @Test
    fun `example 171 - DIVERGENCE`() = runTest {
        // given
        val textFlow = """
            [foo]: /url\bar\*baz "foo\"bar\baz"

            [foo]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url%5Cbar*baz", "title" to "foo\"bar\\baz") {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/url%5Cbar*baz" title="foo&quot;bar\baz">foo</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 172 - DIVERGENCE`() = runTest {
        // given
        val textFlow = """
            [foo]

            [foo]: url
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "url") {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="url">foo</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 173 - DIVERGENCE`() = runTest {
        // given
        val textFlow = """
            [foo]

            [foo]: first
            [foo]: second
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "first") {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="first">foo</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 174 - DIVERGENCE`() = runTest {
        // given
        val textFlow = """
            [FOO]: /url

            [Foo]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url") {
                    +"Foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/url">Foo</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 175 - DIVERGENCE`() = runTest {
        // given
        val textFlow = """
            [ΑΓΩ]: /φου

            [αγω]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/%CF%86%CE%BF%CF%85") {
                    +"αγω"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/%CF%86%CE%BF%CF%85">αγω</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 176 - DIVERGENCE`() = runTest {
        // given
        val textFlow = "[foo]: /url".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            // TODO assertion
        }
        // GFM expected:
        /*
            
         */
    }

    // TODO review
    @Test
    fun `example 177 - DIVERGENCE`() = runTest {
        // given
        val textFlow = """
            [
            foo
            ]: /url
            bar
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"bar"
            }
        }
        // GFM expected:
        /*
            <p>bar</p>
         */
    }

    // TODO review
    @Test
    fun `example 178 - DIVERGENCE`() = runTest {
        // given
        val textFlow = """[foo]: /url "title" ok""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo]: /url \"title\" ok"
            }
        }
        // GFM expected:
        /*
            <p>[foo]: /url &quot;title&quot; ok</p>
         */
    }

    // TODO review
    @Test
    fun `example 179 - DIVERGENCE`() = runTest {
        // given
        val textFlow = """
            [foo]: /url
            "title" ok
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"\"title\" ok"
            }
        }
        // GFM expected:
        /*
            <p>&quot;title&quot; ok</p>
         */
    }

    // TODO review
    @Test
    fun `example 180 - DIVERGENCE`() = runTest {
        // given
        val textFlow = buildText {
            +"    [foo]: /url \"title\"\n"
            +"\n"
            +"[foo]\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"[foo]: /url \"title\"\n"
                }
            }
            "p" {
                +"[foo]"
            }
        }
        // GFM expected:
        /*
            <pre><code>[foo]: /url &quot;title&quot;
            </code></pre>
            <p>[foo]</p>
         */
    }

    // TODO review
    @Test
    fun `example 181 - DIVERGENCE`() = runTest {
        // given
        val textFlow = """
            ```
            [foo]: /url
            ```

            [foo]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre"("class" to "code") {
                +"[foo]: /url"
            }
            "p" {
                +"[foo]"
            }
        }
        // GFM expected:
        /*
            <pre><code>[foo]: /url
            </code></pre>
            <p>[foo]</p>
         */
    }

    // TODO review
    @Test
    fun `example 182 - DIVERGENCE`() = runTest {
        // given
        val textFlow = """
            Foo
            [bar]: /baz

            [bar]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo\n[bar]: /baz"
            }
            "p" {
                +"[bar]"
            }
        }
        // GFM expected:
        /*
            <p>Foo
            [bar]: /baz</p>
            <p>[bar]</p>
         */
    }

    // TODO review
    @Test
    fun `example 183 - DIVERGENCE`() = runTest {
        // given
        val textFlow = """
            # [Foo]
            [foo]: /url
            > bar
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h1" {
                "a"("href" to "/url") {
                    +"Foo"
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
            <h1><a href="/url">Foo</a></h1>
            <blockquote>
            <p>bar</p>
            </blockquote>
         */
    }

    // TODO review
    @Test
    fun `example 184 - DIVERGENCE`() = runTest {
        // given
        val textFlow = """
            [foo]: /url
            bar
            ===
            [foo]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h1" {
                +"bar"
            }
            "p" {
                "a"("href" to "/url") {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <h1>bar</h1>
            <p><a href="/url">foo</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 185 - DIVERGENCE`() = runTest {
        // given
        val textFlow = """
            [foo]: /url
            ===
            [foo]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"===\n"
                "a"("href" to "/url") {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p>===
            <a href="/url">foo</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 186 - DIVERGENCE`() = runTest {
        // given
        val textFlow = buildText {
            +"[foo]: /foo-url \"foo\"\n"
            +"[bar]: /bar-url\n"
            +"  \"bar\"\n"
            +"[baz]: /baz-url\n"
            +"\n"
            +"[foo],\n"
            +"[bar],\n"
            +"[baz]\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/foo-url", "title" to "foo") {
                    +"foo"
                }
                +",\n"
                "a"("href" to "/bar-url", "title" to "bar") {
                    +"bar"
                }
                +",\n"
                "a"("href" to "/baz-url") {
                    +"baz"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/foo-url" title="foo">foo</a>,
            <a href="/bar-url" title="bar">bar</a>,
            <a href="/baz-url">baz</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 187 - DIVERGENCE`() = runTest {
        // given
        val textFlow = """
            [foo]

            > [foo]: /url
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/url") {
                    +"foo"
                }
            }
            "blockquote" {
            }
        }
        // GFM expected:
        /*
            <p><a href="/url">foo</a></p>
            <blockquote>
            </blockquote>
         */
    }

    // TODO review
    @Test
    fun `example 188 - DIVERGENCE`() = runTest {
        // given
        val textFlow = "[foo]: /url".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            // TODO assertion
        }
        // GFM expected:
        /*
            
         */
    }

}
