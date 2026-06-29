/*
 * Copyright 2025-2026 Kazimierz Pogoda / Xemantic
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

package com.xemantic.markanywhere.parse

import com.xemantic.kotlin.test.text.chunkedRandomly
import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MarkanywhereParserTest {

    @Test
    fun `should parse the README parsing example`() = runTest {
        // given - the running example from the project README "Markdown Parsing" section
        val markdown = """
            # Hello

            A *streaming* parser, <b>live</b>.
        """.trimIndent()

        // when
        val parsed = flowOf(markdown).parse()

        // then - the Markdown `*streaming*` yields an untagged `em`, the literal
        //   `<b>` yields a tagged `b` - same event shape, different origin
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h1" { +"Hello" }
            "p" {
                +"A "
                "em" { +"streaming" }
                +" parser, "
                tag("b") { +"live" }
                +"."
            }
        }
    }

    @Test
    fun `should parse simple Hello World markdown`() = runTest {
        // given
        val textFlow = """
            # Hello World

            This is a simple paragraph.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h1" { +"Hello World" }
            "p" {
                +"This is a simple paragraph."
            }
        }
    }

    @Test
    fun `should parse paragraph immediately after header without blank line`() = runTest {
        // given
        val textFlow = """
            # Hello World
            This paragraph follows immediately.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h1" { +"Hello World" }
            "p" { +"This paragraph follows immediately." }
        }
    }

    @Test
    fun `should parse list immediately after header without blank line`() = runTest {
        // given
        val textFlow = """
            # Shopping List
            - Apples
            - Bananas
            - Oranges
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h1" { +"Shopping List" }
            "ul" {
                "li" { "p" { +"Apples" } }
                "li" { "p" { +"Bananas" } }
                "li" { "p" { +"Oranges" } }
            }
        }
    }

    @Test
    fun `should parse list immediately after descriptive paragraph without blank line`() = runTest {
        // given
        val textFlow = """
            Here are the items:
            - First item
            - Second item
            - Third item
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"Here are the items:" }
            "ul" {
                "li" { "p" { +"First item" } }
                "li" { "p" { +"Second item" } }
                "li" { "p" { +"Third item" } }
            }
        }
    }

    @Test
    fun `should parse ordered list immediately after descriptive paragraph without blank line`() = runTest {
        // given
        val textFlow = """
            Follow these steps:
            1. First step
            2. Second step
            3. Third step
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"Follow these steps:" }
            "ol" {
                "li" { "p" { +"First step" } }
                "li" { "p" { +"Second step" } }
                "li" { "p" { +"Third step" } }
            }
        }
    }

    @Test
    fun `should parse code block immediately after paragraph without blank line`() = runTest {
        // given
        
        val textFlow = """
            Here is the code:
            ```kotlin
            fun hello() = println("Hello")
            ```
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"Here is the code:" }
            "pre" {
                "code"("class" to "language-kotlin") {
                    +"""fun hello() = println("Hello")
"""
                }
            }
        }
    }

    @Test
    fun `should parse multiple headers without blank lines between them`() = runTest {
        // given
        
        val textFlow = """
            # Main Title
            ## Subtitle
            ### Section
            Content here.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h1" { +"Main Title" }
            "h2" { +"Subtitle" }
            "h3" { +"Section" }
            "p" { +"Content here." }
        }
    }

    @Test
    fun `should parse blockquote after paragraph`() = runTest {
        // given
        
        val textFlow = """
            As someone once said:
            > This is a famous quote.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"As someone once said:" }
            "blockquote" {
                "p" { +"This is a famous quote." }
            }
        }
    }

    @Test
    fun `should parse multi-line blockquote`() = runTest {
        // given
        
        val textFlow = """
            > This is a famous quote.
            > It spans multiple lines.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "p" { +"This is a famous quote.\nIt spans multiple lines." }
            }
        }
    }

    // Inline formatting edge cases

    @Test
    fun `should parse mixed bold and italic in same text`() = runTest {
        // given
        
        val textFlow = """
            This has **bold then *italic inside* bold** text.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"This has "
                "strong" {
                    +"bold then "
                    "em" { +"italic inside" }
                    +" bold"
                }
                +" text."
            }
        }
    }

    @Test
    fun `should parse adjacent inline elements without space`() = runTest {
        // given
        
        val textFlow = """
            **bold***italic*`code`
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" { +"bold" }
                "em" { +"italic" }
                "code" { +"code" }
            }
        }
    }

    @Test
    fun `should parse underscore-style emphasis`() = runTest {
        // given
        
        val textFlow = """
            This has _italic_ and __bold__ and ___bold italic___ text.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"This has "
                "em" { +"italic" }
                +" and "
                "strong" { +"bold" }
                +" and "
                "strong" {
                    "em" { +"bold italic" }
                }
                +" text."
            }
        }
    }

    @Test
    fun `should parse asterisk-style bold italic`() = runTest {
        // given
        
        val textFlow = """
            This has ***bold italic*** text.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"This has "
                "strong" {
                    "em" { +"bold italic" }
                }
                +" text."
            }
        }
    }

    @Test
    fun `should parse strikethrough text`() = runTest {
        // given
        
        val textFlow = """
            This has ~~strikethrough~~ text.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"This has "
                "del" { +"strikethrough" }
                +" text."
            }
        }
    }

    @Test
    fun `should parse strikethrough with other formatting`() = runTest {
        // given
        
        val textFlow = """
            This is ~~deleted **bold** text~~ here.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"This is "
                "del" {
                    +"deleted "
                    "strong" { +"bold" }
                    +" text"
                }
                +" here."
            }
        }
    }

    // HTML escaping

    @Test
    fun `should escape HTML special characters in text`() = runTest {
        // given
        
        val textFlow = """
            Use <div> elements and & ampersands and "quotes" carefully.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"""Use <div> elements and & ampersands and "quotes" carefully.""" }
        }
    }

    @Test
    fun `should escape HTML in code blocks`() = runTest {
        // given
        
        val textFlow = """
            ```html
            <div class="test">
              <p>Hello & goodbye</p>
            </div>
            ```
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code"("class" to "language-html") {
                    +"<div class=\"test\">\n  <p>Hello & goodbye</p>\n</div>\n"
                }
            }
        }
    }

    @Test
    fun `should escape HTML in inline code`() = runTest {
        // given
        
        val textFlow = """
            Use `<script>alert("XSS")</script>` carefully.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Use "
                "code" { +"""<script>alert("XSS")</script>""" }
                +" carefully."
            }
        }
    }

    @Test
    fun `should handle less than and greater than comparisons`() = runTest {
        // given
        
        val textFlow = """
            Check if a < b and c > d or x <= y and z >= w.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"Check if a < b and c > d or x <= y and z >= w." }
        }
    }

    // Edge cases

    @Test
    fun `should parse empty paragraph`() = runTest {
        // given
        
        val textFlow = """
            # Header



            Content after empty line.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h1" { +"Header" }
            "p" { +"Content after empty line." }
        }
    }

    @Test
    fun `should parse unicode content`() = runTest {
        // given
        
        val textFlow = """
            # 你好世界

            This has émojis 🎉 and Ümlauts and 日本語 text.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h1" { +"你好世界" }
            "p" { +"This has émojis 🎉 and Ümlauts and 日本語 text." }
        }
    }

    @Test
    fun `should parse link with title`() = runTest {
        // given
        
        val textFlow = """
            Check out [Example](https://example.com "Example Site") for more.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Check out "
                "a"("href" to "https://example.com", "title" to "Example Site") { +"Example" }
                +" for more."
            }
        }
    }

    @Test
    fun `should parse autolinks`() = runTest {
        // given
        
        val textFlow = """
            Visit <https://example.com> or email <user@example.com>.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Visit "
                "a"("href" to "https://example.com") { +"https://example.com" }
                +" or email "
                "a"("href" to "mailto:user@example.com") { +"user@example.com" }
                +"."
            }
        }
    }

    @Test
    fun `should parse escaped special characters`() = runTest {
        // given
        
        val textFlow = """
            Use \*asterisks\* and \`backticks\` and \[brackets\] literally.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"Use *asterisks* and `backticks` and [brackets] literally." }
        }
    }

    @Test
    fun `should parse inline code with backticks inside`() = runTest {
        // given
        
        val textFlow = """
            Use `` `backticks` `` inside code.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Use "
                "code" { +"`backticks`" }
                +" inside code."
            }
        }
    }

    @Test
    fun `should parse multiple paragraphs with blank lines`() = runTest {
        // given
        
        val textFlow = """
            First paragraph with some content.

            Second paragraph with more content.

            Third paragraph to finish.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"First paragraph with some content." }
            "p" { +"Second paragraph with more content." }
            "p" { +"Third paragraph to finish." }
        }
    }

    @Test
    fun `should parse superscript`() = runTest {
        // given

        val textFlow = """
            E=mc^2^ is famous.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"E=mc"
                "sup" { +"2" }
                +" is famous."
            }
        }
    }

    @Test
    fun `should parse a superscript wrapping an inline link`() = runTest {
        // given — citation superscripts (`^[1](url)^`) appear in real content
        // (e.g. the Bing SERP answer). The closing `^` must close the sup that
        // wraps the link, producing a balanced, properly-nested stream — not an
        // unmark leaking into the link label (which previously produced a crossed,
        // unrenderable stream).
        val textFlow = "^[1](u)^".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "sup" {
                    "a"("href" to "u") { +"1" }
                }
            }
        }
    }

    @Test
    fun `should parse strikethrough wrapping an inline link`() = runTest {
        // given
        val textFlow = "~~[1](u)~~".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "del" {
                    "a"("href" to "u") { +"1" }
                }
            }
        }
    }

    @Test
    fun `should parse highlight wrapping an inline link`() = runTest {
        // given — `mark` wrapping a whole inline link. (The block-end closer case,
        // `==…==` with nothing after, is covered separately by "should close a mark
        // span whose closing run ends the block".)
        val textFlow = "==[1](u)== ok".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "mark" {
                    "a"("href" to "u") { +"1" }
                }
                +" ok"
            }
        }
    }

    @Test
    fun `should close a label-local mark whose closing run ends the label`() = runTest {
        // given — `mark` opened *inside* a link label, closer right before `]`.
        // The `==` resolves on the next char, which `]` is not, so the closing run
        // sits in inlineBuffer; flushInlineLabelClose must consume it instead of
        // leaking it as literal content after the span (`<mark>mark</mark>==`),
        // which grew the run on every round-trip.
        val textFlow = "[==mark==](u)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "u") {
                    "mark" { +"mark" }
                }
            }
        }
    }

    @Test
    fun `should absorb a dangling single equals at a mark label close`() = runTest {
        // given — `[==foo=]`: inside the label `==` opens mark, `foo` streams, and a
        // single trailing `=` lands in inlineBuffer (it resolves on the next char,
        // which is `]`). A lone `=` is not a mark delimiter, so left in the buffer it
        // leaks out as literal text *after* the unmark and grows the run unboundedly
        // every round-trip (`[==foo=]` → `[==foo===]` → `[==foo=====]` → …).
        // flushInlineLabelClose must drop the dangling delimiter.
        val textFlow = "[==foo=](u)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "u") {
                    "mark" { +"foo" }
                }
            }
        }
    }

    @Test
    fun `should not close a star-opened em with an underscore at a label close`() = runTest {
        // given — `[*foo_]`: `*` opens em inside the label, `_` lands in inlineBuffer
        // before `]`. `*` and `_` are distinct delimiter types and never pair
        // (CommonMark §6.2), so the `_` must NOT close the em (which would also
        // silently drop it) — it stays literal content of the force-closed em.
        val textFlow = "[*foo_](u)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "u") {
                    "em" { +"foo_" }
                }
            }
        }
    }

    @Test
    fun `should not close an underscore-opened em with an asterisk at a label close`() = runTest {
        // given — the mirror of the star/underscore case: `_` opens the em, a
        // trailing `*` must NOT close it (delimiter-type mismatch), staying literal.
        val textFlow = "[_foo*](u)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "u") {
                    "em" { +"foo*" }
                }
            }
        }
    }

    @Test
    fun `should close an underscore-opened em on its own trailing underscore at a label close`() = runTest {
        // given — matching `_` run closes the label-local `_`-em (`[_em_]`), the
        // underscore counterpart of the `[*em*]` / `[**bold**]` cases.
        val textFlow = "[_em_](u)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "u") {
                    "em" { +"em" }
                }
            }
        }
    }

    @Test
    fun `should absorb a dangling single tilde at a del label close`() = runTest {
        // given — the del counterpart of `[==foo=]`: a single trailing `~` after a
        // label-local del must be absorbed (not leaked as literal text after the
        // unmark), so `[~~del~]` does not grow on every round-trip.
        val textFlow = "[~~del~](u)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "u") {
                    "del" { +"del" }
                }
            }
        }
    }

    @Test
    fun `should absorb a longer dangling tilde run at a del label close`() = runTest {
        // given — a 3+ char homogeneous run (`~~~`) at the label close must be
        // absorbed *whole*, not just its first 1-2 chars: a leftover `~` flushes as
        // literal after the unmark and grows the run by 2 every round-trip
        // (`[~~del~~~]` → `[~~del~~~~~]` → …).
        val textFlow = "[~~del~~~](u)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "u") {
                    "del" { +"del" }
                }
            }
        }
    }

    @Test
    fun `should parse highlight or mark text`() = runTest {
        // given
        
        val textFlow = """
            This is ==highlighted== text.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"This is "
                "mark" { +"highlighted" }
                +" text."
            }
        }
    }

    @Test
    fun `should close a mark span whose closing run ends the block`() = runTest {
        // given — a `==` run is resolved on the *next* char during inline parsing,
        // but a closing `==` at the very end of a line never sees that char. It must
        // still close the span at the block boundary (flushInline), like a trailing
        // `~~` closes del — otherwise the closer leaked in as literal content
        // (`==a==` → `<mark>a==</mark>`) and grew on every round-trip.
        val textFlow = "==highlighted==".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "mark" { +"highlighted" }
            }
        }
    }

    @Test
    fun `should treat an unmatched trailing mark run as literal text`() = runTest {
        // given — `foo==` has no open `mark` to close: the trailing `==` is literal
        // (Python equality, math). flushInline must emit it verbatim, NOT open an
        // empty `<mark></mark>` (which would rewrite `foo==` to `foo====`).
        val textFlow = "foo==".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"foo==" }
        }
    }

    @Test
    fun `should treat an unmatched trailing del run as literal text`() = runTest {
        // given — same as the mark case, for `~~` (a trailing `~~` with no open del).
        val textFlow = "foo~~".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"foo~~" }
        }
    }

    @Test
    fun `should treat an unmatched trailing single tilde as literal text`() = runTest {
        // given — a lone trailing `~` with no open del (the single-`~` sub-case of
        // the flushInline fix); it must stay literal, not open an empty `<del></del>`.
        val textFlow = "foo~".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"foo~" }
        }
    }

    @Test
    fun `should parse inline math`() = runTest {
        // given

        val textFlow = $$"""
            The equation $E = mc^2$ is famous.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"The equation "
                "math" { +"E = mc^2" }
                +" is famous."
            }
        }
    }

    @Test
    fun `should parse two inline math expressions in same paragraph`() = runTest {
        // given
        val textFlow = $$"""
            Inline: $E = mc^2$ and $\int_0^\infty e^{-x^2}\,dx = \tfrac{\sqrt{\pi}}{2}$
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Inline: "
                "math" { +"E = mc^2" }
                +" and "
                "math" { +"""\int_0^\infty e^{-x^2}\,dx = \tfrac{\sqrt{\pi}}{2}""" }
            }
        }
    }

    @Test
    fun `should open inline math when content starts with a LaTeX command`() = runTest {
        // given
        val textFlow = $$"""
            See $\int x\,dx$ here.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"See "
                "math" { +"""\int x\,dx""" }
                +" here."
            }
        }
    }

    @Test
    fun `should not confuse caret-runs inside inline math with sup`() = runTest {
        // given
        val textFlow = $$"""
            **Third item** — with math: $\sum_{i=1}^{n} i = \frac{n(n+1)}{2}$
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" { +"Third item" }
                +" — with math: "
                "math" { +"""\sum_{i=1}^{n} i = \frac{n(n+1)}{2}""" }
            }
        }
    }

    @Test
    fun `should close inline math before trailing sentence punctuation`() = runTest {
        // given
        val textFlow = $$"""
            **Third item** — with math: $\sum_{i=1}^{n} i = \frac{n(n+1)}{2}$.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" { +"Third item" }
                +" — with math: "
                "math" { +"""\sum_{i=1}^{n} i = \frac{n(n+1)}{2}""" }
                +"."
            }
        }
    }

    @Test
    fun `should parse two inline math expressions both starting with LaTeX commands`() = runTest {
        // given
        val textFlow = $$"""
            $\frac{a}{b}$ and $\sqrt{c}$
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "math" { +"""\frac{a}{b}""" }
                +" and "
                "math" { +"""\sqrt{c}""" }
            }
        }
    }

    @Test
    fun `should parse display math block`() = runTest {
        // given
        
        val textFlow = """
            Here is an equation:

            $$
            \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}
            $$

            This is the quadratic formula.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"Here is an equation:" }
            "math"("display" to "block") {
                +"\\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}"
            }
            "p" { +"This is the quadratic formula." }
        }
    }

    @Test
    fun `should parse display math block inside list item`() = runTest {
        // given
        val textFlow = /* language=markdown */"""
            - intro

              $$
              E = mc^2
              $$

              outro
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"intro" }
                    "math"("display" to "block") {
                        +"E = mc^2\n"
                    }
                    "p" { +"outro" }
                }
            }
        }
    }

    @Test
    fun `should parse display math block as only content of list item`() = runTest {
        // given
        val textFlow = /* language=markdown */"""
            - $$
              x^2
              $$
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "math"("display" to "block") {
                        +"x^2\n"
                    }
                }
            }
        }
    }

    @Test
    fun `should parse display math blocks in multiple list items`() = runTest {
        // given
        val textFlow = /* language=markdown */"""
            - item 1

              $$
              a + b
              $$

            - item 2

              $$
              c + d
              $$
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"item 1" }
                    "math"("display" to "block") {
                        +"a + b\n"
                    }
                }
                "li" {
                    "p" { +"item 2" }
                    "math"("display" to "block") {
                        +"c + d\n"
                    }
                }
            }
        }
    }

    @Test
    fun `should preserve newlines in multi-line display math inside list item`() = runTest {
        // given
        val textFlow = /* language=markdown */"""
            - $$
              a + b
              c + d
              $$
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "math"("display" to "block") {
                        +"a + b\nc + d\n"
                    }
                }
            }
        }
    }

    @Test
    fun `should preserve blank lines in display math inside list item`() = runTest {
        // given
        val textFlow = /* language=markdown */"""
            - $$
              a + b

              c + d
              $$
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "math"("display" to "block") {
                        +"a + b\n\nc + d\n"
                    }
                }
            }
        }
    }

    @Test
    fun `should parse empty display math block inside list item`() = runTest {
        // given
        val textFlow = /* language=markdown */"""
            - $$
              $$
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "math"("display" to "block") {}
                }
            }
        }
    }

    @Test
    fun `should parse display math block inside ordered list item`() = runTest {
        // given
        val textFlow = /* language=markdown */"""
            1. $$
               x^2 + y^2
               $$
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol" {
                "li" {
                    "math"("display" to "block") {
                        +"x^2 + y^2\n"
                    }
                }
            }
        }
    }

    @Test
    fun `should auto-close unclosed display math block at list end`() = runTest {
        // given
        val textFlow = /* language=markdown */"""
            - $$
              x^2
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "math"("display" to "block") {
                        +"x^2\n"
                    }
                }
            }
        }
    }

    @Test
    fun `should parse display math block inside nested list item`() = runTest {
        // given
        val textFlow = /* language=markdown */"""
            - outer
              - $$
                x^2
                $$
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"outer" }
                    "ul" {
                        "li" {
                            "math"("display" to "block") {
                                +"x^2\n"
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `should parse horizontal rule`() = runTest {
        // given
        
        val textFlow = """
            Some content.

            ---

            More content.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"Some content." }
            "hr" {}
            "p" { +"More content." }
        }
    }

    @Test
    fun `should parse table`() = runTest {
        // given
        
        val textFlow = """
            | Column 1 | Column 2 |
            |----------|----------|
            | Cell 1   | Cell 2   |
            | Cell 3   | Cell 4   |
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "table" {
                "thead" {
                    "tr" {
                        "th" { +"Column 1" }
                        "th" { +"Column 2" }
                    }
                }
                "tbody" {
                    "tr" {
                        "td" { +"Cell 1" }
                        "td" { +"Cell 2" }
                    }
                    "tr" {
                        "td" { +"Cell 3" }
                        "td" { +"Cell 4" }
                    }
                }
            }
        }
    }

    @Test
    fun `should parse task list`() = runTest {
        // given
        
        val textFlow = """
            - [ ] Unchecked task
            - [x] Checked task
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        "input"("disabled" to "", "type" to "checkbox") {}
                        +" Unchecked task"
                    }
                }
                "li" {
                    "p" {
                        "input"("checked" to "", "disabled" to "", "type" to "checkbox") {}
                        +" Checked task"
                    }
                }
            }
        }
    }

    @Test
    fun `should parse inline link`() = runTest {
        // given
        
        val textFlow = """
            Here is a link: [Example](https://example.com)
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Here is a link: "
                "a"("href" to "https://example.com") { +"Example" }
            }
        }
    }

    @Test
    fun `should parse inline image`() = runTest {
        // given
        
        val textFlow = """
            And an image: ![Alt text](https://example.com/image.png)
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"And an image: "
                "img"("src" to "https://example.com/image.png", "alt" to "Alt text") {}
            }
        }
    }

    @Test
    fun `should parse code block without language`() = runTest {
        // given
        
        val textFlow = """
            ```
            plain text code block
            ```
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"plain text code block\n"
                }
            }
        }
    }

    @Test
    fun `should parse all heading levels`() = runTest {
        // given
        
        val textFlow = """
            # Heading 1
            ## Heading 2
            ### Heading 3
            #### Heading 4
            ##### Heading 5
            ###### Heading 6
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h1" { +"Heading 1" }
            "h2" { +"Heading 2" }
            "h3" { +"Heading 3" }
            "h4" { +"Heading 4" }
            "h5" { +"Heading 5" }
            "h6" { +"Heading 6" }
        }
    }

    @Test
    fun `should parse blockquote with list inside`() = runTest {
        // given
        
        val textFlow = """
            > Here are the points:
            > - First point
            > - Second point
            > - Third point
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "p" { +"Here are the points:" }
                "ul" {
                    "li" { "p" { +"First point" } }
                    "li" { "p" { +"Second point" } }
                    "li" { "p" { +"Third point" } }
                }
            }
        }
    }

    @Test
    fun `should parse custom markup in markdown`() = runTest {
        // given
        val textFlow = """
            # Hello World
            
            <foo:bar buzz="42">
            println("Hello World")
            </foo:bar>
            
            Another paragraph.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h1" {
                +"Hello World"
            }
            tagged {
                "foo:bar"("buzz" to "42") {
                    +"""println("Hello World")"""
                }
            }
            "p" {
                +"Another paragraph."
            }
        }
    }

    // Edge case tests

    @Test
    fun `should parse indented bullet items as nested unordered list`() = runTest {
        // given
        val textFlow = """
            - Item 1
              - Nested item 1.1
              - Nested item 1.2
            - Item 2
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"Item 1" }
                    "ul" {
                        "li" { "p" { +"Nested item 1.1" } }
                        "li" { "p" { +"Nested item 1.2" } }
                    }
                }
                "li" { "p" { +"Item 2" } }
            }
        }
    }

    @Test
    fun `should parse indented ordered items as nested ordered list`() = runTest {
        // given
        val textFlow = """
            1. First item
               1. Nested first
               2. Nested second
            2. Second item
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol" {
                "li" {
                    "p" { +"First item" }
                    "ol" {
                        "li" { "p" { +"Nested first" } }
                        "li" { "p" { +"Nested second" } }
                    }
                }
                "li" { "p" { +"Second item" } }
            }
        }
    }

    @Test
    fun `should parse ordered items nested inside unordered list`() = runTest {
        // given
        val textFlow = """
            - Unordered item
              1. Ordered nested
              2. Another ordered
            - Another unordered
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"Unordered item" }
                    "ol" {
                        "li" { "p" { +"Ordered nested" } }
                        "li" { "p" { +"Another ordered" } }
                    }
                }
                "li" { "p" { +"Another unordered" } }
            }
        }
    }

    @Test
    fun `should handle empty bold markers as literal text`() = runTest {
        // given
        
        val textFlow = """
            Text with **** empty bold markers.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"Text with **** empty bold markers." }
        }
    }

    @Test
    fun `should handle empty underscore markers as literal text`() = runTest {
        // given
        
        val textFlow = """
            Text with ____ empty underscore markers.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"Text with ____ empty underscore markers." }
        }
    }

    // Note: The parser auto-closes unclosed inline formatting markers at the end of the paragraph.

    @Test
    fun `should auto-close unclosed bold marker at paragraph end`() = runTest {
        // given
        
        val textFlow = """
            This has **unclosed bold text.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Note: parser auto-closes unclosed bold at paragraph end
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"This has "
                "strong" { +"unclosed bold text." }
            }
        }
    }

    @Test
    fun `should auto-close unclosed italic marker at paragraph end`() = runTest {
        // given
        
        val textFlow = """
            This has *unclosed italic text.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Note: parser auto-closes unclosed italic at paragraph end
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"This has "
                "em" { +"unclosed italic text." }
            }
        }
    }

    @Test
    fun `should auto-close unclosed inline code marker at paragraph end`() = runTest {
        // given

        val textFlow = """
            This has `unclosed code text.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Note: parser auto-closes unclosed inline code at paragraph end
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"This has "
                "code" { +"unclosed code text." }
            }
        }
    }

    @Test
    fun `should handle hash without space as regular text`() = runTest {
        // given
        
        val textFlow = """
            #hashtag is not a header
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"#hashtag is not a header" }
        }
    }

    @Test
    fun `should handle multiple hashes without space as separate paragraphs`() = runTest {
        // given
        
        val textFlow = """
            ##not a header
            ###also not a header
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Note: each line starting with hashes (no space after) becomes a separate paragraph
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"##not a header" }
            "p" { +"###also not a header" }
        }
    }

    @Test
    fun `should treat bare hash lines as empty ATX headings`() = runTest {
        // given

        val textFlow = """
            #
            ##
            Content after hash lines.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h1" {}
            "h2" {}
            "p" { +"Content after hash lines." }
        }
    }

    @Test
    fun `should handle very long single line content`() = runTest {
        // given
        
        val longText = "A".repeat(10000)
        val textFlow = """
            # Header

            $longText
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h1" { +"Header" }
            "p" { +longText }
        }
    }

    @Test
    fun `should parse deeply indented bullet items as four-level nested list`() = runTest {
        // given
        val textFlow = """
            - Level 1
              - Level 2
                - Level 3
                  - Level 4
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"Level 1" }
                    "ul" {
                        "li" {
                            "p" { +"Level 2" }
                            "ul" {
                                "li" {
                                    "p" { +"Level 3" }
                                    "ul" {
                                        "li" { "p" { +"Level 4" } }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
