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

package com.xemantic.markanywhere.render

import com.xemantic.kotlin.test.sameAs
import com.xemantic.markanywhere.flow.semanticEvents
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MarkdownRenderingTest {

    // Basics

    @Test
    fun `should render empty flow as empty string`() = runTest {
        // given
        val flow = semanticEvents { }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs ""
    }

    @Test
    fun `should render bare text without surrounding block`() = runTest {
        // given
        val flow = semanticEvents { +"Hello World" }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "Hello World"
    }

    @Test
    fun `should render a single paragraph`() = runTest {
        // given
        val flow = semanticEvents {
            "p" { +"Hello World" }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "Hello World"
    }

    @Test
    fun `should separate sibling paragraphs with a blank line`() = runTest {
        // given
        val flow = semanticEvents {
            "p" { +"First paragraph." }
            "p" { +"Second paragraph." }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            First paragraph.

            Second paragraph.
        """.trimIndent()
    }

    // Headings

    @Test
    fun `should render all heading levels`() = runTest {
        // given
        val flow = semanticEvents {
            "h1" { +"H1" }
            "h2" { +"H2" }
            "h3" { +"H3" }
            "h4" { +"H4" }
            "h5" { +"H5" }
            "h6" { +"H6" }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            # H1

            ## H2

            ### H3

            #### H4

            ##### H5

            ###### H6
        """.trimIndent()
    }

    @Test
    fun `should render heading followed by paragraph`() = runTest {
        // given
        val flow = semanticEvents {
            "h1" { +"Title" }
            "p" { +"Body." }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            # Title

            Body.
        """.trimIndent()
    }

    // Inline formatting

    @Test
    fun `should render strong emphasis`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"This is "
                "strong" { +"bold" }
                +" text."
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "This is **bold** text."
    }

    @Test
    fun `should render emphasis`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"This is "
                "em" { +"italic" }
                +" text."
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "This is *italic* text."
    }

    @Test
    fun `should render nested strong and em`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                "strong" {
                    "em" { +"bold italic" }
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "***bold italic***"
    }

    @Test
    fun `should render strikethrough`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"Cross "
                "del" { +"this" }
                +" out."
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "Cross ~~this~~ out."
    }

    @Test
    fun `should render inline code`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"Run "
                "code" { +"git status" }
                +" first."
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "Run `git status` first."
    }

    @Test
    fun `should render highlight mark`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                "mark" { +"highlighted" }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "==highlighted=="
    }

    @Test
    fun `should render superscript`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"E = mc"
                "sup" { +"2" }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "E = mc^2^"
    }

    // Links and images

    @Test
    fun `should render inline link`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"See "
                "a"("href" to "https://example.com") { +"Example" }
                +"."
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "See [Example](https://example.com)."
    }

    @Test
    fun `should render link with formatted label`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                "a"("href" to "https://example.com") {
                    +"Click "
                    "strong" { +"here" }
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "[Click **here**](https://example.com)"
    }

    @Test
    fun `should render inline image`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                "img"(
                    "src" to "https://example.com/cat.png",
                    "alt" to "A cat"
                ) {}
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "![A cat](https://example.com/cat.png)"
    }

    @Test
    fun `should render link with block paragraph content as raw HTML anchor`() = runTest {
        // given
        // When an `<a>` wraps block-level content (`<p>`, headings, etc.) the
        // Markdown `[label](url)` shape can't represent it — the label would
        // have to span paragraphs. The renderer spills the anchor as a raw
        // HTML `<a href="…">…</a>` pair instead, so the inner block content
        // streams through unchanged.
        val flow = semanticEvents {
            "a"("href" to "/page") {
                "p" { +"Body." }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            <a href="/page">
            Body.
            </a>
        """.trimIndent()
    }

    @Test
    fun `should render link with heading and paragraph content as raw HTML anchor`() = runTest {
        // given
        val flow = semanticEvents {
            "a"("href" to "/berlin-2026") {
                "h3" { +"BERLIN '26" }
                "p" { +"May 18-22 · 2026" }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        // The first block sits directly under the open `<a>` tag (one
        // newline); sibling blocks inside get the usual blank-line
        // separator from `endBlock` / `startBlock`.
        markdown sameAs """
            <a href="/berlin-2026">
            ### BERLIN '26

            May 18-22 · 2026
            </a>
        """.trimIndent()
    }

    @Test
    fun `should preserve inline label before spilling raw HTML anchor on block content`() = runTest {
        // given
        // Any inline content collected into the link label before the block
        // mark arrives is flushed inline right after the opening `<a …>` tag.
        val flow = semanticEvents {
            "a"("href" to "/page") {
                +"Click "
                "strong" { +"here" }
                "p" { +"Then read this." }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            <a href="/page">Click **here**
            Then read this.
            </a>
        """.trimIndent()
    }

    // Block-level elements

    @Test
    fun `should render horizontal rule`() = runTest {
        // given
        val flow = semanticEvents {
            "p" { +"Above." }
            "hr" {}
            "p" { +"Below." }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            Above.

            ---

            Below.
        """.trimIndent()
    }

    @Test
    fun `should render hard line break inside paragraph`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"Line one"
                "br" {}
                +"Line two"
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "Line one  \nLine two"
    }

    // Blockquote

    @Test
    fun `should render blockquote with single paragraph`() = runTest {
        // given
        val flow = semanticEvents {
            "blockquote" {
                "p" { +"A wise quote." }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "> A wise quote."
    }

    @Test
    fun `should render multi-line blockquote paragraph`() = runTest {
        // given
        val flow = semanticEvents {
            "blockquote" {
                "p" { +"Line one.\nLine two." }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            > Line one.
            > Line two.
        """.trimIndent()
    }

    @Test
    fun `should render nested blockquote`() = runTest {
        // given
        val flow = semanticEvents {
            "blockquote" {
                "p" { +"Outer." }
                "blockquote" {
                    "p" { +"Inner." }
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            > Outer.
            >
            > > Inner.
        """.trimIndent()
    }

    // Lists

    @Test
    fun `should render unordered list`() = runTest {
        // given
        val flow = semanticEvents {
            "ul" {
                "li" { "p" { +"Apples" } }
                "li" { "p" { +"Bananas" } }
                "li" { "p" { +"Oranges" } }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            - Apples
            - Bananas
            - Oranges
        """.trimIndent()
    }

    @Test
    fun `should render ordered list`() = runTest {
        // given
        val flow = semanticEvents {
            "ol" {
                "li" { "p" { +"First" } }
                "li" { "p" { +"Second" } }
                "li" { "p" { +"Third" } }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            1. First
            2. Second
            3. Third
        """.trimIndent()
    }

    @Test
    fun `should render nested unordered list`() = runTest {
        // given
        val flow = semanticEvents {
            "ul" {
                "li" {
                    "p" { +"Item 1" }
                    "ul" {
                        "li" { "p" { +"Nested 1.1" } }
                        "li" { "p" { +"Nested 1.2" } }
                    }
                }
                "li" { "p" { +"Item 2" } }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            - Item 1
              - Nested 1.1
              - Nested 1.2
            - Item 2
        """.trimIndent()
    }

    @Test
    fun `should render list item with inline formatting`() = runTest {
        // given
        val flow = semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"Item with "
                        "strong" { +"bold" }
                        +" and "
                        "a"("href" to "https://x.test") { +"a link" }
                    }
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "- Item with **bold** and [a link](https://x.test)"
    }

    @Test
    fun `should render list item whose only content is a link`() = runTest {
        // A list item with no leading text and no <p> wrapper — the item's
        // first (and only) content is a link. Reproduces the BBC footer nav
        // (`<ul><li><div><a>Home</a></div></li>…`, the `div` unwrapped by
        // simplifyHtml), which previously dropped the `- ` markers and rendered
        // a flat run of links instead of a list.
        // given
        val flow = semanticEvents {
            "ul" {
                "li" { "a"("href" to "https://www.bbc.com/") { +"Home" } }
                "li" { "a"("href" to "/news") { +"News" } }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            - [Home](https://www.bbc.com/)
            - [News](/news)
        """.trimIndent()
    }

    @Test
    fun `should render ordered list item whose first content is emphasis`() = runTest {
        // Same class of bug for inline emphasis as the first content of an
        // ordered-list item — the marker must still appear.
        // given
        val flow = semanticEvents {
            "ol" {
                "li" { "strong" { +"First" } }
                "li" { "em" { +"Second" } }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            1. **First**
            2. *Second*
        """.trimIndent()
    }

    // Fenced code blocks

    @Test
    fun `should render fenced code block with language`() = runTest {
        // given
        val flow = semanticEvents {
            "pre" {
                "code"("class" to "language-kotlin") {
                    +"fun main() = println(\"Hello\")\n"
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            ```kotlin
            fun main() = println("Hello")
            ```
        """.trimIndent()
    }

    @Test
    fun `should render fenced code block without language`() = runTest {
        // given
        val flow = semanticEvents {
            "pre" {
                "code" {
                    +"plain text code block\n"
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            ```
            plain text code block
            ```
        """.trimIndent()
    }

    // Tables

    @Test
    fun `should render simple table`() = runTest {
        // given
        val flow = semanticEvents {
            "table" {
                "thead" {
                    "tr" {
                        "th" { +"Col A" }
                        "th" { +"Col B" }
                    }
                }
                "tbody" {
                    "tr" {
                        "td" { +"a1" }
                        "td" { +"b1" }
                    }
                    "tr" {
                        "td" { +"a2" }
                        "td" { +"b2" }
                    }
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            | Col A | Col B |
            | --- | --- |
            | a1 | b1 |
            | a2 | b2 |
        """.trimIndent()
    }

    @Test
    fun `should synthesize empty header for headerless table`() = runTest {
        // given — a table with no `th` header row (only `tbody`/`td`). GFM has
        // no headerless table; using the first row as the header would
        // misrepresent data as a header, so an empty header row is synthesized
        // and every row stays data.
        val flow = semanticEvents {
            "table" {
                "tbody" {
                    "tr" {
                        "td" { +"a" }
                        "td" { +"b" }
                    }
                    "tr" {
                        "td" { +"c" }
                        "td" { +"d" }
                    }
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            |  |  |
            | --- | --- |
            | a | b |
            | c | d |
        """.trimIndent()
    }

    @Test
    fun `should drop empty spacer rows in table`() = runTest {
        // given — empty `tr` rows (HN-style layout spacers) would otherwise
        // emit a lone `|`, breaking the table; they are dropped.
        val flow = semanticEvents {
            "table" {
                "thead" {
                    "tr" {
                        "th" { +"A" }
                        "th" { +"B" }
                    }
                }
                "tbody" {
                    "tr" {
                        "td" { +"a1" }
                        "td" { +"b1" }
                    }
                    "tr" {}
                    "tr" {
                        "td" { +"a2" }
                        "td" { +"b2" }
                    }
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            | A | B |
            | --- | --- |
            | a1 | b1 |
            | a2 | b2 |
        """.trimIndent()
    }

    @Test
    fun `should render ragged row with fewer cells than header`() = runTest {
        // given — a body row with fewer cells than the header. GFM tolerates
        // this (the missing trailing cells render empty); the table stays valid.
        val flow = semanticEvents {
            "table" {
                "thead" {
                    "tr" {
                        "th" { +"A" }
                        "th" { +"B" }
                        "th" { +"C" }
                    }
                }
                "tbody" {
                    "tr" {
                        "td" { +"1" }
                    }
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            | A | B | C |
            | --- | --- | --- |
            | 1 |
        """.trimIndent()
    }

    @Test
    fun `should expand colspan into padding cells`() = runTest {
        // given — GFM has no colspan; a cell with colspan=N must occupy N
        // columns (content + N-1 empty padding cells) so later cells stay
        // aligned under the correct header column.
        val flow = semanticEvents {
            "table" {
                "thead" {
                    "tr" {
                        "th" { +"A" }
                        "th" { +"B" }
                        "th" { +"C" }
                    }
                }
                "tbody" {
                    "tr" {
                        "td"("colspan" to "2") { +"x" }
                        "td" { +"y" }
                    }
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            | A | B | C |
            | --- | --- | --- |
            | x |  | y |
        """.trimIndent()
    }

    @Test
    fun `should escape literal pipe in table cell text`() = runTest {
        // given
        val flow = semanticEvents {
            "table" {
                "thead" {
                    "tr" {
                        "th" { +"f|oo" }
                    }
                }
                "tbody" {
                    "tr" {
                        "td" { +"a|b" }
                    }
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then a literal `|` in cell content must be backslash-escaped so a
        // GFM parser reads it as cell text rather than a column delimiter.
        markdown sameAs """
            | f\|oo |
            | --- |
            | a\|b |
        """.trimIndent()
    }

    @Test
    fun `should escape pipe inside inline spans in table cell`() = runTest {
        // given — render-direction of GFM table example 200: a `|` inside a
        // code span or emphasis must STILL be escaped, because GFM processes
        // cell-level `\|` before inline parsing ("including inside other
        // inline spans"). See Gfm_04_10_Test `example 200`.
        val flow = semanticEvents {
            "table" {
                "thead" {
                    "tr" {
                        "th" { +"f|oo" }
                    }
                }
                "tbody" {
                    "tr" {
                        "td" {
                            +"b "
                            "code" { +"|" }
                            +" az"
                        }
                    }
                    "tr" {
                        "td" {
                            +"b "
                            "strong" { +"|" }
                            +" im"
                        }
                    }
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            | f\|oo |
            | --- |
            | b `\|` az |
            | b **\|** im |
        """.trimIndent()
    }

    @Test
    fun `should render nested table as inline single-line HTML`() = runTest {
        // given — Markdown has no nested-table syntax, so a table inside a
        // table cell must be emitted as raw inline HTML on the cell's single
        // line (all structural whitespace stripped).
        val flow = semanticEvents {
            "table" {
                "thead" {
                    "tr" {
                        "th" { +"Outer" }
                    }
                }
                "tbody" {
                    "tr" {
                        "td" {
                            "table" {
                                "thead" {
                                    "tr" {
                                        "th" { +"A" }
                                        "th" { +"B" }
                                    }
                                }
                                "tbody" {
                                    "tr" {
                                        "td" { +"1" }
                                        "td" { +"2" }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            | Outer |
            | --- |
            | <table><thead><tr><th>A</th><th>B</th></tr></thead><tbody><tr><td>1</td><td>2</td></tr></tbody></table> |
        """.trimIndent()
    }

    @Test
    fun `should render inline formatting inside nested table as HTML`() = runTest {
        // given — inside the embedded HTML table, inner inline content is in
        // an HTML context, so emphasis / links serialize as HTML tags rather
        // than Markdown delimiters.
        val flow = semanticEvents {
            "table" {
                "thead" {
                    "tr" {
                        "th" { +"Outer" }
                    }
                }
                "tbody" {
                    "tr" {
                        "td" {
                            "table" {
                                "tbody" {
                                    "tr" {
                                        "td" { "strong" { +"bold" } }
                                        "td" {
                                            "a"("href" to "https://example.com") {
                                                +"link"
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

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            | Outer |
            | --- |
            | <table><tbody><tr><td><strong>bold</strong></td><td><a href="https://example.com">link</a></td></tr></tbody></table> |
        """.trimIndent()
    }

    @Test
    fun `should convert content line break inside nested table cell to br`() = runTest {
        // given — a hard line break inside a nested-table cell cannot use the
        // Markdown `  \n` form (the outer row is a single line); it must be an
        // HTML `<br>`.
        val flow = semanticEvents {
            "table" {
                "thead" {
                    "tr" {
                        "th" { +"Outer" }
                    }
                }
                "tbody" {
                    "tr" {
                        "td" {
                            "table" {
                                "tbody" {
                                    "tr" {
                                        "td" {
                                            +"line 1"
                                            "br" {}
                                            +"line 2"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            | Outer |
            | --- |
            | <table><tbody><tr><td>line 1<br>line 2</td></tr></tbody></table> |
        """.trimIndent()
    }

    @Test
    fun `should escape literal pipe inside nested table cell text`() = runTest {
        // given — a literal `|` in a nested-table cell still has to be escaped:
        // the OUTER GFM cell-splitter runs before the inline HTML is handed to
        // a browser, so the whole-cell `\|` escaping must cover the embedded
        // HTML too.
        val flow = semanticEvents {
            "table" {
                "thead" {
                    "tr" {
                        "th" { +"Outer" }
                    }
                }
                "tbody" {
                    "tr" {
                        "td" {
                            "table" {
                                "tbody" {
                                    "tr" {
                                        "td" { +"a|b" }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            | Outer |
            | --- |
            | <table><tbody><tr><td>a\|b</td></tr></tbody></table> |
        """.trimIndent()
    }

    @Test
    fun `should render doubly nested table as inline HTML`() = runTest {
        // given — nesting recurses: a table inside a cell inside a nested
        // table is still serialized as inline HTML.
        val flow = semanticEvents {
            "table" {
                "thead" {
                    "tr" {
                        "th" { +"Outer" }
                    }
                }
                "tbody" {
                    "tr" {
                        "td" {
                            "table" {
                                "tbody" {
                                    "tr" {
                                        "td" {
                                            "table" {
                                                "tbody" {
                                                    "tr" {
                                                        "td" { +"deep" }
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
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            | Outer |
            | --- |
            | <table><tbody><tr><td><table><tbody><tr><td>deep</td></tr></tbody></table></td></tr></tbody></table> |
        """.trimIndent()
    }

    @Test
    fun `should escape HTML special characters in nested table cell text`() = runTest {
        // given — inside the embedded HTML, text nodes must be HTML-escaped so
        // a stray `<`, `>` or `&` doesn't break the markup.
        val flow = semanticEvents {
            "table" {
                "thead" {
                    "tr" {
                        "th" { +"Outer" }
                    }
                }
                "tbody" {
                    "tr" {
                        "td" {
                            "table" {
                                "tbody" {
                                    "tr" {
                                        "td" { +"a < b & c > d" }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            | Outer |
            | --- |
            | <table><tbody><tr><td>a &lt; b &amp; c &gt; d</td></tr></tbody></table> |
        """.trimIndent()
    }

    @Test
    fun `should render void element inside nested table cell without closing tag`() = runTest {
        // given — a void element (here `img`) inside the embedded HTML is
        // emitted as a single open tag, no closing tag.
        val flow = semanticEvents {
            "table" {
                "thead" {
                    "tr" {
                        "th" { +"Outer" }
                    }
                }
                "tbody" {
                    "tr" {
                        "td" {
                            "table" {
                                "tbody" {
                                    "tr" {
                                        "td" {
                                            "img"(
                                                "src" to "x.png",
                                                "alt" to "Cat"
                                            ) {}
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            | Outer |
            | --- |
            | <table><tbody><tr><td><img src="x.png" alt="Cat"></td></tr></tbody></table> |
        """.trimIndent()
    }

    // Front matter

    @Test
    fun `should render YAML front matter`() = runTest {
        // given
        val flow = semanticEvents {
            "frontmatter"("format" to "yaml") {
                +"title: Hello\nauthor: Alice\n"
            }
            "h1" { +"Heading" }
            "p" { +"Body." }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            ---
            title: Hello
            author: Alice
            ---

            # Heading

            Body.
        """.trimIndent()
    }

    @Test
    fun `should render TOML front matter`() = runTest {
        // given
        val flow = semanticEvents {
            "frontmatter"("format" to "toml") {
                +"title = \"Hello\"\nauthor = \"Alice\"\n"
            }
            "p" { +"Body." }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            +++
            title = "Hello"
            author = "Alice"
            +++

            Body.
        """.trimIndent()
    }

    @Test
    fun `should render front matter as the entire document`() = runTest {
        // given
        val flow = semanticEvents {
            "frontmatter"("format" to "yaml") {
                +"title: Standalone\n"
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            ---
            title: Standalone
            ---
        """.trimIndent()
    }

    // Tagged HTML pass-through

    @Test
    fun `should pass through tagged inline HTML`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"Inline "
                tag("span", "class" to "warn") { +"warning" }
                +" tag."
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """Inline <span class="warn">warning</span> tag."""
    }

    @Test
    fun `should render details semantic element with newlines around content`() = runTest {
        // given
        val flow = semanticEvents {
            "p" { +"Before." }
            tag("details") {
                tag("summary") { +"Click" }
                +"Hidden content."
            }
            "p" { +"After." }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        // Block-level semantic elements split open/close tags onto their own
        // lines (no indentation), and isolate their Markdown content with blank
        // lines so it stays parseable; adjacent tags (<details>/<summary>) stay
        // tight.
        markdown sameAs """
            Before.

            <details>
            <summary>

            Click

            </summary>

            Hidden content.

            </details>

            After.
        """.trimIndent()
    }

    @Test
    fun `should render button form element with newlines around content`() = runTest {
        // given
        val flow = semanticEvents {
            "p" { +"Before." }
            tag("button", "type" to "submit") { +"Submit" }
            "p" { +"After." }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            Before.

            <button type="submit">

            Submit

            </button>

            After.
        """.trimIndent()
    }

    @Test
    fun `should render section semantic element with newlines around content`() = runTest {
        // given
        val flow = semanticEvents {
            "p" { +"Before." }
            tag("section", "class" to "intro") {
                "p" { +"Section body." }
            }
            "p" { +"After." }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            Before.

            <section class="intro">

            Section body.

            </section>

            After.
        """.trimIndent()
    }

    @Test
    fun `should render untagged button element with newlines around content`() = runTest {
        // given
        // Simulates the simplifying HTML transformer, which emits everything
        // as `isTagged = false` even for tags without a Markdown equivalent.
        val flow = semanticEvents {
            "p" { +"Before." }
            "button"("type" to "submit") { +"Submit" }
            "p" { +"After." }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            Before.

            <button type="submit">

            Submit

            </button>

            After.
        """.trimIndent()
    }

    @Test
    fun `should render untagged section element with newlines around content`() = runTest {
        // given
        val flow = semanticEvents {
            "p" { +"Before." }
            "section"("id" to "intro") {
                "p" { +"Body." }
            }
            "p" { +"After." }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            Before.

            <section id="intro">

            Body.

            </section>

            After.
        """.trimIndent()
    }

    @Test
    fun `should render void input element without closing tag`() = runTest {
        // given
        val flow = semanticEvents {
            "form"("action" to "/search", "method" to "GET") {
                "input"("type" to "search", "name" to "q") { }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        // Void HTML elements (`input`, `link`, `meta`, etc.) must not emit a
        // closing tag — `</input>` is invalid HTML.
        markdown sameAs """
            <form action="/search" method="GET">
            <input type="search" name="q">
            </form>
        """.trimIndent()
    }

    @Test
    fun `should render nav semantic element with newlines around content`() = runTest {
        // given
        val flow = semanticEvents {
            tag("nav") {
                "ul" {
                    "li" { "p" { +"Home" } }
                    "li" { "p" { +"About" } }
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            <nav>

            - Home
            - About

            </nav>
        """.trimIndent()
    }

    @Test
    fun `should keep adjacent block tags tight with no blank line between them`() = runTest {
        // given — a block tag immediately followed/preceded by another block
        // tag (no Markdown content between) stays tight: newline only, no blank.
        val flow = semanticEvents {
            tag("details") {
                tag("summary") { +"Click" }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            <details>
            <summary>

            Click

            </summary>
            </details>
        """.trimIndent()
    }

    // Loose inline content at block level — the shapes real HTML produces once
    // containers are unwrapped (children not wrapped in a paragraph). These
    // guard the block-separation gaps that previously only the end-to-end dump
    // goldens caught.

    @Test
    fun `should isolate inline content that is a block tag's only child`() = runTest {
        // given — a link directly inside a block tag (no wrapping paragraph),
        // as in an unwrapped <nav>.
        val flow = semanticEvents {
            tag("nav") {
                "a"("href" to "/home") { +"Home" }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then — the inline content is blank-isolated so it parses as Markdown
        markdown sameAs """
            <nav>

            [Home](/home)

            </nav>
        """.trimIndent()
    }

    @Test
    fun `should separate a heading from following loose text`() = runTest {
        // given — bare text directly after a heading (not wrapped in a <p>)
        val flow = semanticEvents {
            "h6" { +"Title" }
            +"Body text"
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            ###### Title

            Body text
        """.trimIndent()
    }

    @Test
    fun `should separate loose inline content from a following list`() = runTest {
        // given — the unwrapped-<nav> shape: a loose link followed by a list,
        // neither wrapped in a paragraph.
        val flow = semanticEvents {
            tag("nav") {
                "a"("href" to "/logo") { +"Logo" }
                "ul" {
                    "li" { "p" { +"One" } }
                    "li" { "p" { +"Two" } }
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then — a blank line separates the loose link from the list
        markdown sameAs """
            <nav>

            [Logo](/logo)

            - One
            - Two

            </nav>
        """.trimIndent()
    }

    @Test
    fun `should separate loose inline content from a following block tag`() = runTest {
        // given — a loose link followed by a nested block tag inside a block tag
        val flow = semanticEvents {
            tag("nav") {
                "a"("href" to "/a") { +"A" }
                tag("form") {
                    "input"("type" to "text") { }
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then — blank before the <form>; the void <input> stays tight
        markdown sameAs """
            <nav>

            [A](/a)

            <form>
            <input type="text">
            </form>
            </nav>
        """.trimIndent()
    }

    // Combined document

    @Test
    fun `should render a complete small document`() = runTest {
        // given
        val flow = semanticEvents {
            "frontmatter"("format" to "yaml") {
                +"title: Demo\n"
            }
            "h1" { +"Demo" }
            "p" {
                +"A short paragraph with "
                "strong" { +"bold" }
                +", "
                "em" { +"italic" }
                +", and a "
                "a"("href" to "https://example.com") { +"link" }
                +"."
            }
            "ul" {
                "li" { "p" { +"one" } }
                "li" { "p" { +"two" } }
            }
            "pre" {
                "code"("class" to "language-kotlin") {
                    +"val x = 1\n"
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            ---
            title: Demo
            ---

            # Demo

            A short paragraph with **bold**, *italic*, and a [link](https://example.com).

            - one
            - two

            ```kotlin
            val x = 1
            ```
        """.trimIndent()
    }
}
