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
 * Tests for GFM Section 04.07 — Link reference definitions.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#link-reference-definitions
 *
 * Single-line link reference definitions are recognized at block boundary,
 * consumed silently, and stored for resolution by `[label]`, `[label][]`, and
 * `[label][ref]` usages later in the document.
 *
 * STREAMING DIVERGENCE: forward references — usage that appears *before* its
 * matching definition — cannot be resolved because emitted text events are
 * append-only and cannot be retracted. Multi-line definitions (label split
 * across lines, destination on the next line, multi-line title) are also not
 * recognized; the source flows through as paragraph text. Tests of those
 * shapes keep the `DIVERGENCE` marker; tests where the definition precedes
 * the usage on a single line each have been updated to assert the spec output.
 */
@Suppress("ClassName")
class Gfm_04_07_Test {

    @Test
    fun `example 161 - basic link reference definition`() = runTest {
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

    @Test
    fun `example 162 - DIVERGENCE - indented definition with url and title on separate lines`() = runTest {
        // given
        val textFlow = buildString {
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
    fun `example 163 - DIVERGENCE - special characters in label parens in url`() = runTest {
        // given
        val textFlow = """
            [Foo*bar\]]:my_(url) 'title (with parens)'

            [Foo*bar\]]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Phase 3a label-content rendering DIVERGENCE: the `*` in the label
        // opens an em delimiter that has no closer inside the label. With
        // bounded-label buffering, we force-close the em on `]` and replay
        // the captured em inside `<a>`. GFM's spec output keeps `*` literal
        // because the source label is matched by raw text (not rendered).
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "my_(url)", "title" to "title (with parens)") {
                    +"Foo"
                    "em" {
                        +"bar]"
                    }
                }
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
    fun `example 169 - explicitly empty url with angle brackets`() = runTest {
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

    @Test
    fun `example 170 - trailing content after url is invalid`() = runTest {
        // given
        val textFlow = """
            [foo]: <bar>(baz)

            [foo]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // The `(baz)` immediately following `<bar>` (no whitespace separator)
        // invalidates the would-be ref-def; the line falls through to paragraph
        // processing where `<bar>` becomes an inline-HTML mark and `(baz)` text.
        // No definition is registered, so `[foo]` resolves as literal text.
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
    fun `example 171 - backslash escapes in url and title`() = runTest {
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

    @Test
    fun `example 172 - DIVERGENCE - forward reference definition after use`() = runTest {
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
        // as text and cannot be retracted to become an `<a>`. The trailing
        // definition is recognized and consumed silently (no second paragraph).
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo]"
            }
        }
        // GFM expected:
        /*
            <p><a href="url">foo</a></p>
         */
    }

    @Test
    fun `example 173 - DIVERGENCE - duplicate definitions first wins`() = runTest {
        // given
        val textFlow = """
            [foo]

            [foo]: first
            [foo]: second
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Forward reference (DIVERGENCE) — `[foo]` is emitted before either
        // definition is registered. Both definitions are then consumed silently
        // (first-wins applies to the registered href, but no usage references it).
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo]"
            }
        }
        // GFM expected:
        /*
            <p><a href="first">foo</a></p>
         */
    }

    @Test
    fun `example 174 - case-insensitive label matching`() = runTest {
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

    @Test
    fun `example 175 - unicode case folding in label`() = runTest {
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

    @Test
    fun `example 176 - standalone definition produces no output`() = runTest {
        // given
        val textFlow = "[foo]: /url".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // The definition is consumed silently and registers no visible content;
        // there are no usages, so no events are emitted.
        parsed.mergeAdjacentText() sameAs semanticEvents {}
        // GFM expected: (definition produces no output)
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
    fun `example 179 - title on continuation line trailing content rejects title`() = runTest {
        // given
        val textFlow = """
            [foo]: /url
            "title" ok
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // First line is a valid single-line definition (no title) — consumed
        // silently. Second line is a separate paragraph; its `"title" ok` does
        // not retroactively attach to the previous definition.
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

    @Test
    fun `example 180 - DIVERGENCE - four-space indent makes it a code block`() = runTest {
        // given
        val textFlow = buildString {
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
        // Forward reference DIVERGENCE: the heading's `[Foo]` is parsed before
        // the `[foo]: /url` definition is registered, so the heading content
        // stays as literal text. The definition is consumed silently afterwards.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h1" {
                +"[Foo]"
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
        // The first line is a valid single-line ref def (consumed silently),
        // so the trailing `[foo]` shortcut resolves against the registered href.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"bar\n===\n"
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

    @Test
    fun `example 185 - DIVERGENCE - setext underline only no heading content`() = runTest {
        // given
        val textFlow = """
            [foo]: /url
            ===
            [foo]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // setext divergence as in 184; the def line is consumed silently and
        // the shortcut `[foo]` later resolves to the registered href.
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

    @Test
    fun `example 186 - DIVERGENCE - multiple definitions and references`() = runTest {
        // given
        val textFlow = buildString {
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
        // Multi-line title DIVERGENCE: the `[bar]: /bar-url` definition's title
        // (`"bar"` on the next line) is not associated with the definition. The
        // first def line registers `foo` (single-line, with title); the second
        // line registers `bar` (no title); the orphan `"bar"` line and the
        // following `[baz]: /baz-url` def-line then group into one paragraph
        // where `[baz]` is interpreted as a shortcut reference (no def for it
        // yet, so falls through). Reference paragraph after the blank line
        // resolves `[foo]` and `[bar]` against their registered hrefs.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"\"bar\"\n[baz]: /baz-url"
            }
            "p" {
                "a"("href" to "/foo-url", "title" to "foo") {
                    +"foo"
                }
                +",\n"
                "a"("href" to "/bar-url") {
                    +"bar"
                }
                +",\n[baz]"
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
        // Forward reference DIVERGENCE: `[foo]` is parsed before the inner
        // blockquote-scoped definition is registered (and the inner Start
        // sub-parser does recognize the def, so the blockquote ends up empty).
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[foo]"
            }
            "blockquote" {}
        }
        // GFM expected:
        /*
            <p><a href="/url">foo</a></p>
            <blockquote>
            </blockquote>
         */
    }

    @Test
    fun `example 188 - standalone definition no trailing newline`() = runTest {
        // given
        val textFlow = "[foo]: /url".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Same as 176 — definition consumed silently, no usages, no events.
        parsed.mergeAdjacentText() sameAs semanticEvents {}
        // GFM expected: (definition produces no output)
        /*

         */
    }

}
