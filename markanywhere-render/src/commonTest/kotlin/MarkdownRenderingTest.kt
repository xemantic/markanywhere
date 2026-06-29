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

import com.xemantic.kotlin.test.assert
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

    @Test
    fun `should suppress a single trailing newline at end of stream`() = runTest {
        // given — text ending in a newline. The renderer holds at most one
        // pending `\n` and drops it on completion, so the stream never ends
        // with a trailing newline (guards flushDeferringTrailingNewline).
        val flow = semanticEvents { +"Hello\n" }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "Hello"
    }

    @Test
    fun `should preserve interior newlines and suppress only the trailing one`() = runTest {
        // given — only the final newline is suppressed; newlines between
        // content are emitted untouched.
        val flow = semanticEvents { +"a\nb\n" }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "a\nb"
    }

    @Test
    fun `should suppress only one of several trailing newlines`() = runTest {
        // given — the renderer defers a single newline, so trailing blank lines
        // collapse by exactly one: `a\n\n` becomes `a\n`, not `a`.
        val flow = semanticEvents { +"a\n\n" }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "a\n"
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

    @Test
    fun `should render empty heading without a trailing space`() = runTest {
        // given — the parser emits an empty ATX heading (`#`) as `h1 {}` with
        // no content (see MarkanywhereParserTest
        // `should treat bare hash lines as empty ATX headings`). The renderer
        // must emit a bare `#`, not a dangling `# ` with trailing whitespace.
        val flow = semanticEvents {
            "h1" {}
            "p" { +"after" }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            #

            after
        """.trimIndent()
    }

    @Test
    fun `should render heading with inline formatting`() = runTest {
        // given — headings carry inline content too (code, links, emphasis),
        // not just plain text; the `#` prefix precedes the rendered inline run.
        val flow = semanticEvents {
            "h2" {
                +"Use "
                "code" { +"git" }
                +" — see "
                "a"("href" to "/docs") { +"docs" }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "## Use `git` — see [docs](/docs)"
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
    fun `should widen backtick fence for inline code containing a backtick`() = runTest {
        // given — content with a literal backtick can't be wrapped in a single
        // backtick (`` `a`b` `` would close early); the fence must be one
        // backtick longer than the longest internal run.
        val flow = semanticEvents {
            "p" {
                "code" { +"a`b" }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "``a`b``"
    }

    @Test
    fun `should pad inline code that begins or ends with a backtick`() = runTest {
        // given — when the content starts (or ends) with a backtick, a single
        // space is padded inside the fence so the delimiters stay distinct; a
        // GFM parser strips that leading/trailing space back out on read.
        val flow = semanticEvents {
            "p" {
                "code" { +"`x`" }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "`` `x` ``"
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
    fun `should render image with missing alt as empty alt`() = runTest {
        // given — an `img` with no `alt` attribute (common in real HTML) still
        // produces a valid `![](src)` shape with an empty description.
        val flow = semanticEvents {
            "p" {
                "img"("src" to "https://example.com/x.png") {}
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "![](https://example.com/x.png)"
    }

    @Test
    fun `should render link with block paragraph content as raw HTML anchor`() = runTest {
        // given
        // When an `<a>` wraps block-level content (`<p>`, headings, etc.) the
        // Markdown `[label](url)` shape can't represent it — the label would
        // have to span paragraphs. The renderer spills the anchor as a raw
        // HTML `<a href="…">…</a>` pair instead, separated like any block-level
        // tag (blank lines at the tag↔Markdown boundaries) so the inner block
        // content parses as Markdown rather than raw HTML.
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
        // The spilled `<a>` is a block-level raw tag: its content is isolated
        // by a blank line on each side, and sibling blocks inside get the
        // usual blank-line separator from `endBlock` / `startBlock`.
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
        // mark arrives becomes its own block under the open tag — the tag stays
        // alone on its line (a valid HTML-block start) and the label, like the
        // block that forced the spill, is isolated by blank lines.
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
            <a href="/page">

            Click **here**

            Then read this.

            </a>
        """.trimIndent()
    }

    @Test
    fun `should preserve all attributes when spilling a link as a raw HTML anchor`() = runTest {
        // given
        // A spilled anchor is a raw HTML tag, which (unlike Markdown link
        // syntax) can carry attributes — so every attribute survives, not just
        // href. This is what lets an actionable `ref` ride a block-wrapping link.
        val flow = semanticEvents {
            "a"("href" to "/live", "ref" to "9") {
                +"LIVE"
                "h2" { +"Headline" }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            <a href="/live" ref="9">

            LIVE

            ## Headline

            </a>
        """.trimIndent()
    }

    @Test
    fun `should separate consecutive sibling links spilled as raw HTML anchors`() = runTest {
        // given
        // Two block-wrapping links in a row (e.g. a "Most watched" list). Each
        // spills to a raw anchor; the separation must be consistent regardless
        // of position — no `</a><a>` jammed onto one line, every open tag alone
        // on its line, every inner heading isolated by blank lines.
        val flow = semanticEvents {
            "h2" { +"Most watched" }
            "a"("href" to "/v1", "ref" to "1") { +"1"; "h2" { +"First" } }
            "a"("href" to "/v2", "ref" to "2") { +"2"; "h2" { +"Second" } }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        // Sibling raw tags stay newline-tight (`</a>` then `<a>`), like
        // `</section>` / `<section>`; everything inside is blank-line separated.
        markdown sameAs """
            ## Most watched

            <a href="/v1" ref="1">

            1

            ## First

            </a>
            <a href="/v2" ref="2">

            2

            ## Second

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
    fun `should render a tagged line-start anchor wrapping block content as a block-level raw tag`() = runTest {
        // given
        // A block-wrapping `<a>` arrives as a *tagged* mark (what `parse()`
        // emits for a raw `<a …>` HTML block — distinct from the untagged-link
        // spill path above). At a line start it must render as a block-level
        // raw tag: a blank line at the tag↔Markdown boundary so the inner
        // `## heading` survives and re-parses as a heading, not raw text. This
        // is what keeps the HTML→Markdown→parse round-trip a fixpoint.
        val flow = semanticEvents {
            tag("a", "href" to "/article", "ref" to "34") {
                "h2" { +"Headline" }
                "p" { +"Body." }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            <a href="/article" ref="34">
            
            ## Headline
            
            Body.
            
            </a>
        """.trimIndent()
    }

    @Test
    fun `should keep adjacent tagged anchors newline-tight`() = runTest {
        // given
        // Two block-wrapping links in a row, each a tagged `<a>` mark. Like
        // sibling `</section>` / `<section>`, the close and the next open stay
        // newline-tight (no blank line between `</a>` and `<a>`), while inner
        // content is blank-line separated.
        val flow = semanticEvents {
            tag("a", "href" to "/a", "ref" to "4") { "h2" { +"First" } }
            tag("a", "href" to "/b", "ref" to "6") { "h2" { +"Second" } }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            <a href="/a" ref="4">
            
            ## First
            
            </a>
            <a href="/b" ref="6">
            
            ## Second
            
            </a>
        """.trimIndent()
    }

    @Test
    fun `should render a whitespace-only paragraph as nothing`() = runTest {
        // given
        // A content-less `<p> </p>` (common in captured DOMs) between two real
        // paragraphs. Its block-start is deferred until real content arrives,
        // so it emits nothing — no stray blank line that a render→parse→render
        // round-trip would then collapse, breaking the fixpoint.
        val flow = semanticEvents {
            "p" { +"First." }
            "p" { +" " }
            "p" { +"Second." }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            First.
            
            Second.
        """.trimIndent()
    }

    @Test
    fun `should keep a tagged anchor that starts a paragraph inline`() = runTest {
        // given
        // A tagged inline `<a>` that is the first content of a paragraph sits
        // at a line start, but it is NOT a block-wrapping link — it must stay
        // inline. Regression: the line-start block-tag rule (combined with the
        // deferred paragraph start) once spilled it to a block-level raw tag,
        // wrongly producing `<a href>\n\nhi</a> rest`. The preceding heading
        // also checks the paragraph still gets its blank-line separation.
        val flow = semanticEvents {
            "h1" { +"Title" }
            "p" {
                tag("a", "href" to "/x") { +"hi" }
                +" rest of line"
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            # Title
            
            <a href="/x">hi</a> rest of line
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

    @Test
    fun `should drop a trailing hard break at a paragraph boundary`() = runTest {
        // given — a `<br>` as the last child of a paragraph. The hard break is
        // inline-only; reaching the block boundary makes it moot, so it is
        // dropped rather than injecting a dangling `  \n` before the next block.
        val flow = semanticEvents {
            "p" {
                +"text"
                "br" {}
            }
            "p" { +"next" }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            text

            next
        """.trimIndent()
    }

    @Test
    fun `should drop a moot hard break and stray whitespace between loose blocks`() = runTest {
        // given — the Hacker News footer shape: after a block, loose inline
        // content of an image, a `<br>`, then a link, with a space on each side
        // of the `<br>`. The image is its own block (loose content), so the
        // `<br>` sits at a block boundary — it is moot, and the surrounding
        // spaces are insignificant. The round-trip would otherwise leave a moot
        // trailing hard break on the image line and a leading space on the link.
        val flow = semanticEvents {
            "p" { +"Above" }
            "img"("src" to "s.gif") {}
            +" "
            "br" {}
            +" "
            "a"("href" to "/g") { +"Guidelines" }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then — the `<br>` and both spaces are dropped; the blocks separate
        // with a blank line and neither carries stray whitespace
        markdown sameAs """
            Above
            
            ![](s.gif)
            
            [Guidelines](/g)
        """.trimIndent()
    }

    // Trailing whitespace at line ends — a single trailing space on a line is
    // insignificant in Markdown (GFM strips it on parse), and `<br>` hard breaks
    // arrive as structural events, not literal double-spaces, so the renderer can
    // safely drop text trailing spaces at a line/block boundary. Without this the
    // renderer leaks insignificant trailing whitespace that the parser then strips
    // — making a render→parse round-trip diverge on whitespace alone.

    @Test
    fun `should drop an insignificant trailing space at a paragraph end`() = runTest {
        // given — a paragraph whose text ends with a space (as a captured DOM
        // text node often does, e.g. openJur's "Rechtskraft: ❓ ").
        val flow = semanticEvents {
            "p" { +"Done " }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then — the trailing space is dropped
        markdown sameAs "Done"
    }

    @Test
    fun `should drop trailing spaces before a following block`() = runTest {
        // given — loose text ending in a space, immediately followed by a
        // block-level tag (the openJur "❓ <button>" shape).
        val flow = semanticEvents {
            "h6" { +"Heading" }
            +"Label "
            tag("button") { +"Optionen" }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then — "Label" has no trailing space before the blank-line boundary
        markdown sameAs """
            ###### Heading
            
            Label
            
            <button>
            
            Optionen
            
            </button>
        """.trimIndent()
    }

    @Test
    fun `should keep interior spaces between inline content`() = runTest {
        // given — spaces that are *not* trailing must survive: deferral only
        // drops a space when the line actually ends, not when content follows.
        val flow = semanticEvents {
            "p" {
                +"see the "
                "a"("href" to "/x") { +"link" }
                +" now"
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "see the [link](/x) now"
    }

    @Test
    fun `should drop a trailing space before a soft line break`() = runTest {
        // given — a soft break (`\n` inside a paragraph's text) preceded by a
        // space. The space is trailing on its line and is dropped.
        val flow = semanticEvents {
            "p" { +"Line one \nLine two" }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "Line one\nLine two"
    }

    // Leading block-marker escaping — paragraph text that, written verbatim,
    // would re-parse as a *different* block construct (an ordered/bullet list,
    // ATX heading, blockquote, thematic break) is backslash-escaped at the line
    // start so it round-trips as the paragraph text it is. The openJur case:
    // separate `<p>1.</p>` margin numbers (Randnummern) that bare `1.` would
    // turn into an empty list item and drop.

    @Test
    fun `should escape an ordered-list marker that is paragraph text`() = runTest {
        // given — a paragraph whose entire text is "1." (an openJur margin number)
        val flow = semanticEvents { "p" { +"1." } }

        // when
        val markdown = flow.renderMarkdown()

        // then — escaped so it does not re-parse as an ordered list
        markdown sameAs "1\\."
    }

    @Test
    fun `should escape an ordered-list marker that has following content`() = runTest {
        // given
        val flow = semanticEvents { "p" { +"1. Stop here" } }

        // when
        val markdown = flow.renderMarkdown()

        // then — only the delimiter is escaped
        markdown sameAs "1\\. Stop here"
    }

    @Test
    fun `should escape a bullet-list marker that is paragraph text`() = runTest {
        // given
        val flow = semanticEvents { "p" { +"- item" } }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "\\- item"
    }

    @Test
    fun `should escape an ATX heading marker that is paragraph text`() = runTest {
        // given
        val flow = semanticEvents { "p" { +"# not a heading" } }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "\\# not a heading"
    }

    @Test
    fun `should escape a blockquote marker that is paragraph text`() = runTest {
        // given
        val flow = semanticEvents { "p" { +"> not a quote" } }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "\\> not a quote"
    }

    @Test
    fun `should escape a thematic break that is paragraph text`() = runTest {
        // given
        val flow = semanticEvents { "p" { +"---" } }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "\\---"
    }

    @Test
    fun `should not escape a marker that is not at the line start`() = runTest {
        // given — the marker characters appear mid-line, where they are plain
        // text and must not be escaped.
        val flow = semanticEvents { "p" { +"see point 1. below - now" } }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs "see point 1. below - now"
    }

    @Test
    fun `should not escape markers inside a fenced code block`() = runTest {
        // given — code content is verbatim; a leading "1." or "- " is real code.
        val flow = semanticEvents {
            "pre" {
                "code" {
                    +"1. not a list\n"
                    +"- not a bullet\n"
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            ```
            1. not a list
            - not a bullet
            ```
        """.trimIndent()
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
    fun `should honor ordered list start attribute`() = runTest {
        // given — an `ol` with `start=5` numbers from 5, incrementing per item.
        val flow = semanticEvents {
            "ol"("start" to "5") {
                "li" { "p" { +"five" } }
                "li" { "p" { +"six" } }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            5. five
            6. six
        """.trimIndent()
    }

    @Test
    fun `should render ordered list nested in unordered list`() = runTest {
        // given — mixed nesting: an `ol` inside a `ul` item. The nested ordered
        // markers are indented under the unordered item's content.
        val flow = semanticEvents {
            "ul" {
                "li" {
                    "p" { +"Item" }
                    "ol" {
                        "li" { "p" { +"one" } }
                        "li" { "p" { +"two" } }
                    }
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            - Item
              1. one
              2. two
        """.trimIndent()
    }

    @Test
    fun `should render list item with two paragraphs as indented continuation`() = runTest {
        // given — a list item with two `p` children. Lists are always rendered
        // loose (see CLAUDE.md), but the second paragraph is emitted as an
        // indented continuation line under the marker rather than a separate
        // blank-line-separated block — locking the current streaming behavior.
        val flow = semanticEvents {
            "ul" {
                "li" {
                    "p" { +"First para" }
                    "p" { +"Second para" }
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            - First para
              Second para
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

    @Test
    fun `should indent a fenced code opening fence to its list item even after an intervening block`() = runTest {
        // given — the Bing Copilot shape: a single <pre> wraps both a heading
        // (the language label) and the <code>, inside a list item. The heading
        // ends the line, so the fence open must rebuild the item's indent prefix
        // — otherwise the opening fence dropped to column 0 and, on re-parse,
        // escaped its list item (the closing fence and body stayed indented).
        val flow = semanticEvents {
            "ul" {
                "li" {
                    "pre" {
                        "h5" { +"Javascript" }
                        "code"("class" to "language-javascript") {
                            +"x = 1\n"
                        }
                    }
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then — every line of the code block sits at the item's 2-space content
        // indent. (The bare `- ` first line is a separate artifact of a <pre>
        // wrapping a block-level heading; the fix here is purely the fence indent.)
        markdown sameAs /* language=markdown */ """
            - 
              ##### Javascript
              ```javascript
              x = 1
              ```
        """.trimIndent()
        // The `- ` marker's trailing space is significant but lives at a line end
        // in the golden above, where "strip trailing whitespace on save" can erase
        // it — silently weakening the assertion to accept a renderer that drops the
        // space. Pin it explicitly; the space here sits inside the literal, safe.
        assert(markdown.lineSequence().first() == "- ")
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
    fun `should default front matter format to YAML when format attribute is absent`() = runTest {
        // given — a `frontmatter` mark with no `format` attribute defaults to
        // YAML, so the `---` delimiter is used.
        val flow = semanticEvents {
            "frontmatter" { +"title: Hello\n" }
            "p" { +"Body." }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            ---
            title: Hello
            ---

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

    // Structural inter-block whitespace — the shape a *re-parsed* HTML block
    // produces: the parser emits the source line breaks between block-level
    // tags (`<header>\n<nav>`) as whitespace-only `text` events. The reverse
    // renderer previously amplified each into a blank line, and since every
    // render left those text events behind, the blank lines grew on every
    // render→parse round-trip (never converging). These lock the fix in at the
    // unit level — the end-to-end dump round-trip was the only thing catching it.

    @Test
    fun `should keep adjacent block tags tight across a structural newline text event`() = runTest {
        // given — `<header>` then a whitespace-only `\n` text event then
        // `<nav>`, exactly as the parser re-emits an HTML block's line breaks.
        val flow = semanticEvents {
            tag("header") {
                +"\n"
                tag("nav") {
                    +"\n"
                    "p" { +"Home" }
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then — the structural newlines are dropped: the tags stay tight and
        // the content gets a single blank-line separation (not amplified).
        markdown sameAs """
            <header>
            <nav>
            
            Home
            
            </nav>
            </header>
        """.trimIndent()
    }

    @Test
    fun `should not let structural whitespace text events accumulate blank lines`() = runTest {
        // when — two streams differing only in how many whitespace-only text
        // events sit between the block tag and its content (one vs. five): the
        // root cause of the round-trip accumulation was that more such events
        // produced more blank lines.
        suspend fun render(blankRuns: Int) = semanticEvents {
            tag("section") {
                repeat(blankRuns) { +"\n" }
                "p" { +"Body" }
            }
        }.renderMarkdown()

        // then — both render identically, to a single blank separation
        val expected = """
            <section>

            Body

            </section>
        """.trimIndent()
        render(1) sameAs expected
        render(5) sameAs expected
    }

    @Test
    fun `should preserve blank lines inside fenced code despite the structural whitespace rule`() = runTest {
        // given — a blank line *inside* a code block is significant content and
        // must survive (the structural-whitespace drop is gated off in pre/code).
        val flow = semanticEvents {
            "pre" {
                "code" {
                    +"line 1\n"
                    +"\n"
                    +"line 3\n"
                }
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then
        markdown sameAs """
            ```
            line 1

            line 3
            ```
        """.trimIndent()
    }

    // Soft line breaks emitted as standalone `\n` text events — the shape the
    // parser re-emits a Markdown soft break in (`a\nb` parses to text "a",
    // text "\n", text "b"). Unlike a structural inter-block `\n` (which arrives
    // at line start), a soft break sits mid-line after real content, so it is
    // meaningful and must be preserved. Dropping it merged the lines (`ab`) and
    // broke the render→parse→render fixpoint for every paragraph carrying one.

    @Test
    fun `should preserve a soft break emitted as a standalone newline text event`() = runTest {
        // given — a paragraph whose soft line break arrives as its own
        // whitespace-only `\n` text event between two content events
        val flow = semanticEvents {
            "p" {
                +"a"
                +"\n"
                +"b"
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then — the soft break survives; dropping it would render `ab`
        markdown sameAs "a\nb"
    }

    @Test
    fun `should drop the source newline that follows a hard break`() = runTest {
        // given — a hard break (`<br>`) followed by the source line break the
        // parser emits after it (`foo  \nbar` parses to text "foo", br, text
        // "\n", text "bar"). The `\n` is absorbed by the hard break and must
        // not also render as a separate soft break.
        val flow = semanticEvents {
            "p" {
                +"foo"
                "br" {}
                +"\n"
                +"bar"
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then — exactly one hard break, no extra soft-break newline
        markdown sameAs "foo  \nbar"
    }

    @Test
    fun `should escape a leading block marker after a soft break`() = runTest {
        // given — a soft break followed by content that begins with a block
        // marker (`a\n# b`). On the continuation line `# b` is paragraph text,
        // not a heading, so it must be escaped to survive re-parsing.
        val flow = semanticEvents {
            "p" {
                +"a"
                +"\n"
                +"# b"
            }
        }

        // when
        val markdown = flow.renderMarkdown()

        // then — the marker is escaped so the line does not re-parse as a heading
        markdown sameAs "a\n\\# b"
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

    @Test
    fun `should not escape link label content against emphasis opened outside the label`() = runTest {
        // An `<em>` wrapping a link: a literal `*` in the *link label* must NOT be
        // escaped against the outer em — only against emphasis opened *inside* the
        // label (here there is none). escapeActiveInlineDelimiters scopes label
        // escaping to the enclosing Inline run up to the Link frame, so the label
        // `*` stays unescaped. Render-only (no parse/fixpoint): re-parsing `[a*b]`
        // inside `*…*` trips a SEPARATE pre-existing parser bug (the lone label `*`
        // crosses the outer em → unbalanced stream), unrelated to this renderer-side
        // escaping scope. See issue #58.
        // when
        val markdown = semanticEvents {
            "p" { "em" { +"x "; "a"("href" to "u") { +"a*b" }; +" y" } }
        }.renderMarkdown()
        // then
        markdown sameAs "*x [a*b](u) y*"
    }
}
