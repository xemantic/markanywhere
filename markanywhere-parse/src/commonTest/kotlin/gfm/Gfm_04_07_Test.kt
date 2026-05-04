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
 * DIVERGENCE: Link reference definitions are not supported. Resolving a `[foo]`
 * usage to a `[foo]: /url` definition that may appear *later* in the document
 * would require retracting an already-emitted text event, violating the
 * append-only streaming contract. LLM output overwhelmingly uses inline links
 * (`[text](url)`) instead, so the cost of supporting reference definitions is
 * not justified for this parser.
 *
 * Each test below captures the spec input and the GFM expected output for
 * reference (in the trailing comment), but assertions reflect what
 * markanywhere-parse actually emits: the source flows through as plain
 * paragraph text, with inline links / inline HTML / fenced code blocks still
 * recognized where they happen to appear.
 */
@Suppress("ClassName")
class Gfm_04_07_Test {

    @Test
    fun `example 161 - DIVERGENCE - basic link reference definition`() = runTest {
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
                +"[foo]: /url \"title\""
            }
            "p" {
                +"[foo]"
            }
        }
        // GFM expected:
        /*
            <p><a href="/url" title="title">foo</a></p>
         */
    }

    @Test
    fun `example 162 - DIVERGENCE - indented definition with url and title on separate lines`() = runTest {
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
        // Trailing two spaces on lines 1 and 2 promote those soft breaks to
        // <br/> per GFM §6.7. The source flows through as paragraph text with
        // hard breaks where the original lines ended in 2+ spaces.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo]:\n/url"
                "br" {}
                +"\n'the title'"
            }
            "p" {
                +"[foo]"
            }
        }
        // GFM expected:
        /*
            <p><a href="/url" title="the title">foo</a></p>
         */
    }

    @Test
    fun `example 163 - DIVERGENCE - special characters in label, parens in url`() = runTest {
        // given
        val textFlow = """
            [Foo*bar\]]:my_(url) 'title (with parens)'

            [Foo*bar\]]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // The escaped `\]` inside the link label is emitted as a literal `]`
        // before the bracket-abort replays the buffered `[Foo*bar`, hence the
        // leading `]` in each paragraph.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"][Foo*bar]:my_(url) 'title (with parens)'"
            }
            "p" {
                +"][Foo*bar]"
            }
        }
        // GFM expected:
        /*
            <p><a href="my_(url)" title="title (with parens)">Foo*bar]</a></p>
         */
    }

    @Test
    fun `example 164 - DIVERGENCE - angle-bracketed url with spaces`() = runTest {
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
        // `<my url>` is recognized as inline HTML (a tagged element with name
        // "my" and attribute "url"=""), the rest flows as paragraph text.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[Foo bar]:\n"
                tag("my", "url" to "") {}
                +"\n'title'"
            }
            "p" {
                +"[Foo bar]"
            }
        }
        // GFM expected:
        /*
            <p><a href="my%20url" title="title">Foo bar</a></p>
         */
    }

    @Test
    fun `example 165 - DIVERGENCE - multi-line title`() = runTest {
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
                +"[foo]: /url '\ntitle\nline1\nline2\n'"
            }
            "p" {
                +"[foo]"
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

    @Test
    fun `example 166 - DIVERGENCE - blank line in title is invalid`() = runTest {
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

    @Test
    fun `example 167 - DIVERGENCE - url on next line`() = runTest {
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
                +"[foo]:\n/url"
            }
            "p" {
                +"[foo]"
            }
        }
        // GFM expected:
        /*
            <p><a href="/url">foo</a></p>
         */
    }

    @Test
    fun `example 168 - DIVERGENCE - missing url`() = runTest {
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

    @Test
    fun `example 169 - DIVERGENCE - explicitly empty url with angle brackets`() = runTest {
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
                +"[foo]: <>"
            }
            "p" {
                +"[foo]"
            }
        }
        // GFM expected:
        /*
            <p><a href="">foo</a></p>
         */
    }

    @Test
    fun `example 170 - DIVERGENCE - trailing content after url is invalid`() = runTest {
        // given
        val textFlow = """
            [foo]: <bar>(baz)

            [foo]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // `<bar>` is recognized as an inline HTML opener; absent a matching
        // closer in the same paragraph, `flushInline` drains the unclosed
        // inline HTML stack so the event stream stays balanced.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo]: "
                tag("bar") {
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

    @Test
    fun `example 171 - DIVERGENCE - backslash escapes in url and title`() = runTest {
        // given
        val textFlow = """
            [foo]: /url\bar\*baz "foo\"bar\baz"

            [foo]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Backslash escapes consume the `\` and emit the next char literally
        // (even for non-ASCII-punctuation chars, which is a separate divergence
        // from CommonMark §6.1).
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo]: /urlbar*baz \"foo\"barbaz\""
            }
            "p" {
                +"[foo]"
            }
        }
        // GFM expected:
        /*
            <p><a href="/url%5Cbar*baz" title="foo&quot;bar\baz">foo</a></p>
         */
    }

    @Test
    fun `example 172 - DIVERGENCE - forward reference, definition after use`() = runTest {
        // given
        val textFlow = """
            [foo]

            [foo]: url
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // The fundamental incompatibility with append-only streaming: by the
        // time the definition arrives, the `[foo]` use has already been emitted
        // as text and cannot be retracted to become an `<a>`.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo]"
            }
            "p" {
                +"[foo]: url"
            }
        }
        // GFM expected:
        /*
            <p><a href="url">foo</a></p>
         */
    }

    @Test
    fun `example 173 - DIVERGENCE - duplicate definitions, first wins`() = runTest {
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
                +"[foo]"
            }
            "p" {
                +"[foo]: first\n[foo]: second"
            }
        }
        // GFM expected:
        /*
            <p><a href="first">foo</a></p>
         */
    }

    @Test
    fun `example 174 - DIVERGENCE - case-insensitive label matching`() = runTest {
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
                +"[FOO]: /url"
            }
            "p" {
                +"[Foo]"
            }
        }
        // GFM expected:
        /*
            <p><a href="/url">Foo</a></p>
         */
    }

    @Test
    fun `example 175 - DIVERGENCE - unicode case folding in label`() = runTest {
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
                +"[ΑΓΩ]: /φου"
            }
            "p" {
                +"[αγω]"
            }
        }
        // GFM expected:
        /*
            <p><a href="/%CF%86%CE%BF%CF%85">αγω</a></p>
         */
    }

    @Test
    fun `example 176 - DIVERGENCE - standalone definition produces text`() = runTest {
        // given
        val textFlow = "[foo]: /url".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo]: /url"
            }
        }
        // GFM expected: (definition produces no output in GFM)
        /*

         */
    }

    @Test
    fun `example 177 - DIVERGENCE - multi-line label`() = runTest {
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
                +"[\nfoo\n]: /url\nbar"
            }
        }
        // GFM expected:
        /*
            <p>bar</p>
         */
    }

    @Test
    fun `example 178 - DIVERGENCE - trailing content on same line rejects definition`() = runTest {
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

    @Test
    fun `example 179 - DIVERGENCE - title on continuation line, trailing content rejects title`() = runTest {
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
                +"[foo]: /url\n\"title\" ok"
            }
        }
        // GFM expected:
        /*
            <p>&quot;title&quot; ok</p>
         */
    }

    @Test
    fun `example 180 - DIVERGENCE - four-space indent makes it a code block`() = runTest {
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

    @Test
    fun `example 181 - DIVERGENCE - inside fenced code block`() = runTest {
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
            "pre" {
                "code" {
                    +"[foo]: /url\n"
                }
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

    @Test
    fun `example 182 - DIVERGENCE - definition after paragraph line joins paragraph`() = runTest {
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

    @Test
    fun `example 183 - DIVERGENCE - definition between heading and blockquote`() = runTest {
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
                +"[Foo]"
            }
            "p" {
                +"[foo]: /url"
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

    @Test
    fun `example 184 - DIVERGENCE - definition before setext heading`() = runTest {
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
        // markanywhere does not implement setext headings — the `===` line
        // becomes paragraph content rather than promoting `bar` to <h1>.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo]: /url\nbar\n===\n[foo]"
            }
        }
        // GFM expected:
        /*
            <h1>bar</h1>
            <p><a href="/url">foo</a></p>
         */
    }

    @Test
    fun `example 185 - DIVERGENCE - setext underline only, no heading content`() = runTest {
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
                +"[foo]: /url\n===\n[foo]"
            }
        }
        // GFM expected:
        /*
            <p>===
            <a href="/url">foo</a></p>
         */
    }

    @Test
    fun `example 186 - DIVERGENCE - multiple definitions and references`() = runTest {
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
                +"[foo]: /foo-url \"foo\"\n[bar]: /bar-url\n\"bar\"\n[baz]: /baz-url"
            }
            "p" {
                +"[foo],\n[bar],\n[baz]"
            }
        }
        // GFM expected:
        /*
            <p><a href="/foo-url" title="foo">foo</a>,
            <a href="/bar-url" title="bar">bar</a>,
            <a href="/baz-url">baz</a></p>
         */
    }

    @Test
    fun `example 187 - DIVERGENCE - definition inside blockquote`() = runTest {
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
                +"[foo]"
            }
            "blockquote" {
                "p" {
                    +"[foo]: /url"
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="/url">foo</a></p>
            <blockquote>
            </blockquote>
         */
    }

    @Test
    fun `example 188 - DIVERGENCE - standalone definition, no trailing newline`() = runTest {
        // given
        val textFlow = "[foo]: /url".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo]: /url"
            }
        }
        // GFM expected: (definition produces no output in GFM)
        /*

         */
    }

}
