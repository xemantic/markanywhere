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
 * Tests for GFM Section 04.06 — HTML blocks.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#html-blocks
 *
 * Implementation notes (see CLAUDE.md and `MarkanywhereParser.kt` for details):
 *
 * - HTML tag and attribute names are emitted **lowercased** so that downstream
 *   transformers can match `<DIV>` and `<div>` semantically. Source casing is
 *   not preserved — that's a deliberate semantic normalization, not a bug.
 *
 * - HTML-derived `Mark`/`Unmark` events carry `isTagged = true` (expressed via
 *   the `tagged { … }` / `tag(name) { … }` builders below). Markdown-derived
 *   events stay `isTagged = false`. Use `tagged` for HTML blocks where the
 *   entire subtree is HTML; use `tag("name")` for inline-mixed contexts where
 *   only the named element is HTML and its children are Markdown-derived.
 *
 * - HTML block types 6 and 7 stream incrementally. The opening tag's `mark`
 *   fires as soon as `>` is parsed; content streams as text events while the
 *   frame is in `RawText` child-mode. The first blank line transitions the
 *   frame to `SubParse` and pushes a fresh `Start` frame on top, so subsequent
 *   lines route through the regular Markdown dispatcher (paragraphs, lists,
 *   fenced code, etc.). The matching root close tag pops the frame; close
 *   tags for inner tracked `openTags` (e.g. `</pre>` while a `<table>` frame
 *   has `pre` in its `openTags`) drain that frame's `openTags` down to and
 *   including the matched name. There is no look-ahead past the next
 *   emitted event.
 *
 * Divergences flagged with `DIVERGENCE` in the test name:
 *
 * 1. **Blank lines transition to sub-parse instead of closing the block.**
 *    GFM closes the type-6/7 frame on a blank line and emits subsequent
 *    Markdown at top level. We keep the frame open and sub-parse Markdown
 *    *inside* it — so any sub-parsed paragraphs/lists/code blocks land
 *    nested under the still-open HTML element.
 *
 * 2. **Unclosed HTML blocks auto-close at EOF.** GFM leaves them dangling
 *    (renderer outputs the open tag literally). We must emit a balancing
 *    `unmark` so the event stream stays well-formed.
 *
 * 3. **Whitespace between sibling tags surfaces as text events.** When the
 *    source has `<table>\n  <tr>` etc., the indent and newline between sibling
 *    HTML tags are emitted as `text("\n  ")`. GFM would absorb them.
 *
 * 4. **Indented content after a blank line opens an indented code block
 *    inside the still-open HTML frame** (a consequence of #1: sub-parse keeps
 *    the frame open). GFM would have closed the HTML at the blank line and
 *    emitted the indented code block at top level instead.
 *
 * Closing-tag block openers (e.g. `</div>` on its own line) emit their content
 * as raw text with no `mark`/`unmark` pair. This is *not* a divergence — GFM
 * also treats type-6/7 HTML block content (with closing-tag root) as literal
 * raw markup, which renders the same as our text-only event stream.
 */
@Suppress("ClassName")
class Gfm_04_06_Test {

    // DIVERGENCE: blank line inside the table transitions to sub-parse, so
    // `_world_` becomes `<em>` wrapped in `<p>`. The trailing `</pre>` is
    // detected as a stand-alone close tag matching the table frame's tracked
    // `openTags` — it interrupts the paragraph and emits a clean `unmark pre`.
    // The remaining `</td></tr></table>` triggers the root close, draining
    // `td`/`tr`/`table` in order. GFM further wraps the `</pre>` itself in
    // a `<p>` (giving `<p><em>world</em>.\n</pre></p>`) — we close the
    // paragraph instead, so the trailing `.` ends without re-opening.
    @Test
    fun `example 118 - DIVERGENCE - table`() = runTest {
        // given
        val textFlow = """
            <table><tr><td>
            <pre>
            **Hello**,

            _world_.
            </pre>
            </td></tr></table>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            "table" {
                "tr" {
                    "td" {
                        +"\n"
                        "pre" {
                            +"\n**Hello**,\n\n"
                            untagged {
                                "p" {
                                    "em" {
                                        +"world"
                                    }
                                    +"."
                                }
                            }
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <table><tr><td>
            <pre>
            **Hello**,
            <p><em>world</em>.
            </pre></p>
            </td></tr></table>
         */
    }

    // DIVERGENCE: incremental streaming surfaces the indentation/newlines
    // between sibling HTML tags as text events.
    @Test
    fun `example 119 - DIVERGENCE - table paragraph okay`() = runTest {
        // given
        val textFlow = buildString {
            +"<table>\n"
            +"  <tr>\n"
            +"    <td>\n"
            +"           hi\n"
            +"    </td>\n"
            +"  </tr>\n"
            +"</table>\n"
            +"\n"
            +"okay.\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            "table" {
                +"\n  "
                "tr" {
                    +"\n    "
                    "td" {
                        +"\n           hi\n    "
                    }
                    +"\n  "
                }
                +"\n"
            }
            untagged {
                "p" {
                    +"okay."
                }
            }
        }
        // GFM expected:
        /*
            <table>
              <tr>
                <td>
                       hi
                </td>
              </tr>
            </table>
            <p>okay.</p>
         */
    }

    // DIVERGENCE: unclosed `<div>`/`<foo>`/`<a>` auto-close at EOF; GFM
    // would render them literally without closing tags.
    @Test
    fun `example 120 - DIVERGENCE - div hello`() = runTest {
        // given
        val textFlow = buildString {
            +" <div>\n"
            +"  *hello*\n"
            +"         <foo><a>\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            +" "
            tagged {
                "div" {
                    +"\n  *hello*\n         "
                    "foo" {
                        "a" {
                            +"\n"
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
             <div>
              *hello*
                     <foo><a>
         */
    }

    // A closing-tag block opener (`</div>` here) starts a type-6 HTML block
    // whose content streams as raw text until EOF or a blank line. With no
    // blank line in the input, GFM keeps the block open and renders the
    // entire source literally — same as our text-event output.
    @Test
    fun `example 121 - text foo`() = runTest {
        // given
        val textFlow = """
            </div>
            *foo*
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            +"</div>\n*foo*\n"
        }
        // GFM expected:
        /*
            </div>
            *foo*
         */
    }

    // The blank line transitions the `<DIV>` to sub-parse mode, so
    // `*Markdown*` becomes a `<p><em>` paragraph and the matching `</DIV>`
    // close-tag check pops the frame. Tag and attribute names are lowercased
    // per the HTML5 normalization documented at the top of this file.
    @Test
    fun `example 122 - div Markdown`() = runTest {
        // given
        val textFlow = """
            <DIV CLASS="foo">

            *Markdown*

            </DIV>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            "div"("class" to "foo") {
                +"\n\n"
                untagged {
                    "p" {
                        "em" {
                            +"Markdown"
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <DIV CLASS="foo">
            <p><em>Markdown</em></p>
            </DIV>
         */
    }

    // DIVERGENCE: the trailing `\n` after the multi-line opening tag becomes
    // a text event inside the div.
    @Test
    fun `example 123 - DIVERGENCE - div`() = runTest {
        // given
        val textFlow = buildString {
            +"<div id=\"foo\"\n"
            +"  class=\"bar\">\n"
            +"</div>\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            "div"("id" to "foo", "class" to "bar") {
                +"\n"
            }
        }
        // GFM expected:
        /*
            <div id="foo"
              class="bar">
            </div>
         */
    }

    // DIVERGENCE: the trailing `\n` after the opening tag becomes a text event.
    @Test
    fun `example 124 - DIVERGENCE - div`() = runTest {
        // given
        val textFlow = buildString {
            +"<div id=\"foo\" class=\"bar\n"
            +"  baz\">\n"
            +"</div>\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            "div"("id" to "foo", "class" to "bar\n  baz") {
                +"\n"
            }
        }
        // GFM expected:
        /*
            <div id="foo" class="bar
              baz">
            </div>
         */
    }

    // DIVERGENCE: the blank line transitions to sub-parse, so `*bar*`
    // becomes `<p><em>bar</em></p>` nested inside the still-open div, and
    // the unclosed div auto-closes at EOF. GFM closes the type-6 block on
    // the blank line and emits the paragraph at top level with no `</div>`.
    @Test
    fun `example 125 - DIVERGENCE - div foo bar`() = runTest {
        // given
        val textFlow = """
            <div>
            *foo*

            *bar*
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("div") {
                +"\n*foo*\n\n"
                "p" {
                    "em" {
                        +"bar"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <div>
            *foo*
            <p><em>bar</em></p>
         */
    }

    // DIVERGENCE: an opening tag that never completes (no `>`) falls back to
    // raw text output rather than starting an HTML block.
    @Test
    fun `example 126 - DIVERGENCE - text div hi`() = runTest {
        // given
        val textFlow = """
            <div id="foo"
            *hi*
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            +"<div id=\"foo\"\n*hi*\n"
        }
        // GFM expected:
        /*
            <div id="foo"
            *hi*
         */
    }

    // DIVERGENCE: incomplete attribute parsing falls back to raw text.
    @Test
    fun `example 127 - DIVERGENCE - text div class foo`() = runTest {
        // given
        val textFlow = """
            <div class
            foo
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            +"<div class\nfoo\n"
        }
        // GFM expected:
        /*
            <div class
            foo
         */
    }

    // DIVERGENCE: invalid attribute syntax falls back to raw text.
    @Test
    fun `example 128 - DIVERGENCE - text div foo`() = runTest {
        // given
        val textFlow = """
            <div *???-&&&-<---
            *foo*
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            +"<div *???-&&&-<---\n*foo*\n"
        }
        // GFM expected:
        /*
            <div *???-&&&-<---
            *foo*
         */
    }

    @Test
    fun `example 129 - div foo`() = runTest {
        // given
        val textFlow = """<div><a href="bar">*foo*</a></div>""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            "div" {
                "a"("href" to "bar") {
                    +"*foo*"
                }
            }
        }
        // GFM expected:
        /*
            <div><a href="bar">*foo*</a></div>
         */
    }

    @Test
    fun `example 130 - table`() = runTest {
        // given
        val textFlow = """
            <table><tr><td>
            foo
            </td></tr></table>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            "table" {
                "tr" {
                    "td" {
                        +"\nfoo\n"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <table><tr><td>
            foo
            </td></tr></table>
         */
    }

    // DIVERGENCE: `<div></div>` followed by a fenced code block — the fence
    // is parsed structurally as `<pre><code class="language-c">`, while GFM
    // would emit the fence text literally as part of the surrounding HTML.
    @Test
    fun `example 131 - DIVERGENCE - div fenced code c`() = runTest {
        // given
        val textFlow = """
            <div></div>
            ``` c
            int x = 33;
            ```
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("div") { }
            "pre" {
                "code"("class" to "language-c") {
                    +"int x = 33;\n"
                }
            }
        }
        // GFM expected:
        /*
            <div></div>
            ``` c
            int x = 33;
            ```
         */
    }

    @Test
    fun `example 132 - link bar - foo`() = runTest {
        // given
        val textFlow = """
            <a href="foo">
            *bar*
            </a>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            "a"("href" to "foo") {
                +"\n*bar*\n"
            }
        }
        // GFM expected:
        /*
            <a href="foo">
            *bar*
            </a>
         */
    }

    // `Warning` is not a known HTML5 element, so its source casing is
    // preserved in the emitted `mark`/`unmark` (treating non-HTML5 tags
    // as XML-ish). HTML5 tags like `<DIV>` would still be lowercased.
    @Test
    fun `example 133 - Warning bar`() = runTest {
        // given
        val textFlow = """
            <Warning>
            *bar*
            </Warning>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            "Warning" {
                +"\n*bar*\n"
            }
        }
        // GFM expected:
        /*
            <Warning>
            *bar*
            </Warning>
         */
    }

    @Test
    fun `example 134 - i bar`() = runTest {
        // given
        val textFlow = """
            <i class="foo">
            *bar*
            </i>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            "i"("class" to "foo") {
                +"\n*bar*\n"
            }
        }
        // GFM expected:
        /*
            <i class="foo">
            *bar*
            </i>
         */
    }

    // A closing-tag block opener (`</ins>` here) starts a type-7 HTML block
    // (`ins` is not in the type-6 set). Like type-6 closing-tag roots, the
    // content streams as raw text until EOF/blank line — matching GFM's
    // literal-passthrough rendering for this input.
    @Test
    fun `example 135 - text bar`() = runTest {
        // given
        val textFlow = """
            </ins>
            *bar*
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            +"</ins>\n*bar*\n"
        }
        // GFM expected:
        /*
            </ins>
            *bar*
         */
    }

    @Test
    fun `example 136 - strikethrough foo`() = runTest {
        // given
        val textFlow = """
            <del>
            *foo*
            </del>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            "del" {
                +"\n*foo*\n"
            }
        }
        // GFM expected:
        /*
            <del>
            *foo*
            </del>
         */
    }

    // The blank line transitions the `<del>` to sub-parse mode, so `*foo*`
    // becomes `<p><em>foo</em></p>` and the matching `</del>` close-tag
    // check pops the frame.
    @Test
    fun `example 137 - del foo`() = runTest {
        // given
        val textFlow = """
            <del>

            *foo*

            </del>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("del") {
                +"\n\n"
                "p" {
                    "em" {
                        +"foo"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <del>
            <p><em>foo</em></p>
            </del>
         */
    }

    @Test
    fun `example 138 - paragraph foo`() = runTest {
        // given
        val textFlow = "<del>*foo*</del>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                tag("del") {
                    "em" {
                        +"foo"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p><del><em>foo</em></del></p>
         */
    }

    // DIVERGENCE: type-1 (pre/script/style/textarea) opens immediately on the
    // tag's `>`, so the trailing newline of the opener line is NOT emitted as
    // a leading `\n` text event. (GFM-style renderers often display this
    // newline; we can re-add it later if needed.)
    @Test
    fun `example 139 - DIVERGENCE - pre haskell paragraph okay`() = runTest {
        // given
        val textFlow = buildString {
            +"<pre language=\"haskell\"><code>\n"
            +"import Text.HTML.TagSoup\n"
            +"\n"
            +"main :: IO ()\n"
            +"main = print \$ parseTags tags\n"
            +"</code></pre>\n"
            +"okay\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tagged {
                "pre"("language" to "haskell") {
                    "code" {
                        +"import Text.HTML.TagSoup\n\nmain :: IO ()\nmain = print \$ parseTags tags\n"
                    }
                }
            }
            "p" {
                +"okay"
            }
        }
        // GFM expected:
        /*
            <pre language="haskell"><code>
            import Text.HTML.TagSoup

            main :: IO ()
            main = print $ parseTags tags
            </code></pre>
            <p>okay</p>
         */
    }

    // DIVERGENCE: same as #139 — no leading `\n` after the type-1 opener.
    @Test
    fun `example 140 - DIVERGENCE - script JavaScript example paragraph okay`() = runTest {
        // given
        val textFlow = """
            <script type="text/javascript">
            // JavaScript example

            document.getElementById("demo").innerHTML = "Hello JavaScript!";
            </script>
            okay
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("script", "type" to "text/javascript") {
                +"// JavaScript example\n\ndocument.getElementById(\"demo\").innerHTML = \"Hello JavaScript!\";\n"
            }
            "p" {
                +"okay"
            }
        }
        // GFM expected:
        /*
            <script type="text/javascript">
            // JavaScript example

            document.getElementById("demo").innerHTML = "Hello JavaScript!";
            </script>
            <p>okay</p>
         */
    }

    // DIVERGENCE: same as #139 — no leading `\n` after the type-1 opener.
    @Test
    fun `example 141 - DIVERGENCE - style css paragraph okay`() = runTest {
        // given
        val textFlow = buildString {
            +"<style\n"
            +"  type=\"text/css\">\n"
            +"h1 {color:red;}\n"
            +"\n"
            +"p {color:blue;}\n"
            +"</style>\n"
            +"okay\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("style", "type" to "text/css") {
                +"h1 {color:red;}\n\np {color:blue;}\n"
            }
            "p" {
                +"okay"
            }
        }
        // GFM expected:
        /*
            <style
              type="text/css">
            h1 {color:red;}

            p {color:blue;}
            </style>
            <p>okay</p>
         */
    }

    // DIVERGENCE: same as #139 — no leading `\n` after the type-1 opener;
    // unclosed `<style>` auto-closes at EOF.
    @Test
    fun `example 142 - DIVERGENCE - style foo`() = runTest {
        // given
        val textFlow = buildString {
            +"<style\n"
            +"  type=\"text/css\">\n"
            +"\n"
            +"foo\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            "style"("type" to "text/css") {
                +"\nfoo\n"
            }
        }
        // GFM expected:
        /*
            <style
              type="text/css">

            foo
         */
    }

    // DIVERGENCE: blockquote-prefixed `<div>` is not recognised as opening an
    // HTML block at top level — the blockquote treats it as paragraph content.
    @Test
    fun `example 143 - DIVERGENCE - blockquote text div foo paragraph bar`() = runTest {
        // given
        val textFlow = """
            > <div>
            > foo

            bar
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "p" {
                    +"<div>\nfoo"
                }
            }
            "p" {
                +"bar"
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <div>
            foo
            </blockquote>
            <p>bar</p>
         */
    }

    // DIVERGENCE: GFM leaves `<div>` unclosed (the HTML block ends at the
    // item boundary without a matching close tag). Our streaming model emits
    // a force-closed `</div>` so the semantic event stream stays balanced;
    // see `closeListHtmlBlockIfOpen` in the list-item HTML path.
    @Test
    fun `example 144 - DIVERGENCE - ul with 2 items`() = runTest {
        // given
        val textFlow = """
            - <div>
            - foo
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    tag("div") { +"\n" }
                }
                "li" {
                    "p" {
                        +"foo"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>
            <div>
            </li>
            <li>foo</li>
            </ul>
         */
    }

    @Test
    fun `example 145 - style pcolorred paragraph foo`() = runTest {
        // given
        val textFlow = """
            <style>p{color:red;}</style>
            *foo*
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tagged {
                "style" {
                    +"p{color:red;}"
                }
            }
            "p" {
                "em" {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <style>p{color:red;}</style>
            <p><em>foo</em></p>
         */
    }

    // Type-2 (HTML comment) block emits its line as raw text — the comment
    // and trailing `*bar*` arrive as one text run, which renders the same
    // as GFM's literal raw-HTML pass-through for that line.
    @Test
    fun `example 146 - text bar paragraph baz`() = runTest {
        // given
        val textFlow = """
            <!-- foo -->*bar*
            *baz*
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            +"<!-- foo -->*bar*\n"
            "p" {
                "em" {
                    +"baz"
                }
            }
        }
        // GFM expected:
        /*
            <!-- foo -->*bar*
            <p><em>baz</em></p>
         */
    }

    // DIVERGENCE: when a type-1 close tag arrives without a trailing `\n`
    // (here `</script>1. *bar*` at EOF), the close detection in the flush path
    // doesn't run — content + close + trailing all flatten into one text event,
    // and the `<script>` is auto-closed at EOF.
    @Test
    fun `example 147 - DIVERGENCE - script foo text 1 bar`() = runTest {
        // given
        val textFlow = """
            <script>
            foo
            </script>1. *bar*
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            "script" {
                +"foo\n</script>1. *bar*\n"
            }
        }
        // GFM expected:
        /*
            <script>
            foo
            </script>1. *bar*
         */
    }

    // Type-2 (HTML comment) block streams its lines as raw text and the
    // trailing paragraph follows. Renders the same as GFM's literal
    // pass-through of the multi-line comment.
    @Test
    fun `example 148 - comment paragraph okay`() = runTest {
        // given
        val textFlow = buildString {
            +"<!-- Foo\n"
            +"\n"
            +"bar\n"
            +"   baz -->\n"
            +"okay\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            +"<!-- Foo\n\nbar\n   baz -->\n"
            "p" {
                +"okay"
            }
        }
        // GFM expected:
        /*
            <!-- Foo

            bar
               baz -->
            <p>okay</p>
         */
    }

    // Type-3 (processing instruction) block streams as raw text, matching
    // GFM's literal pass-through.
    @Test
    fun `example 149 - php paragraph okay`() = runTest {
        // given
        val textFlow = buildString {
            +"<?php\n"
            +"\n"
            +"  echo '>';\n"
            +"\n"
            +"?>\n"
            +"okay\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            +"<?php\n\n  echo '>';\n\n?>\n"
            "p" {
                +"okay"
            }
        }
        // GFM expected:
        /*
            <?php

              echo '>';

            ?>
            <p>okay</p>
         */
    }

    // DIVERGENCE: type-4 (declaration like `<!DOCTYPE>`) is parsed as a
    // structural `mark("doctype")` + content text + `unmark`, rather than the
    // GFM raw-HTML pass-through. This is a markanywhere extension — the
    // declaration-name keyword is well-defined enough to expose semantically,
    // and downstream consumers (renderers, HTML-aware transformers) often
    // need to recognise it explicitly.
    @Test
    fun `example 150 - DIVERGENCE - doctype`() = runTest {
        // given
        val textFlow = "<!DOCTYPE html>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("doctype") {
                +"html"
            }
        }
        // GFM expected:
        /*
            <!DOCTYPE html>
         */
    }

    // Type-5 (CDATA) block streams as raw text, matching GFM's literal
    // pass-through.
    @Test
    fun `example 151 - cdata paragraph okay`() = runTest {
        // given
        val textFlow = buildString {
            +"<![CDATA[\n"
            +"function matchwo(a,b)\n"
            +"{\n"
            +"  if (a < b && a < 0) then {\n"
            +"    return 1;\n"
            +"\n"
            +"  } else {\n"
            +"\n"
            +"    return 0;\n"
            +"  }\n"
            +"}\n"
            +"]]>\n"
            +"okay\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            +"<![CDATA[\nfunction matchwo(a,b)\n{\n  if (a < b && a < 0) then {\n    return 1;\n\n  } else {\n\n    return 0;\n  }\n}\n]]>\n"
            "p" {
                +"okay"
            }
        }
        // GFM expected:
        /*
            <![CDATA[
            function matchwo(a,b)
            {
              if (a < b && a < 0) then {
                return 1;

              } else {

                return 0;
              }
            }
            ]]>
            <p>okay</p>
         */
    }

    // Indented (≤3 spaces) HTML comment is recognised as a type-2 block and
    // emitted as raw text; the 4-space indent on the next non-blank line opens
    // an indented code block — same structural output as GFM.
    @Test
    fun `example 152 - comment indented code block`() = runTest {
        // given
        val textFlow = buildString {
            +"  <!-- foo -->\n"
            +"\n"
            +"    <!-- foo -->\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            +"  <!-- foo -->\n"
            "pre" {
                "code" {
                    +"<!-- foo -->\n"
                }
            }
        }
        // GFM expected:
        /*
              <!-- foo -->
            <pre><code>&lt;!-- foo --&gt;
            </code></pre>
         */
    }

    // DIVERGENCE: blank line transitions the outer `<div>` to sub-parse,
    // and the indented `    <div>` becomes an indented code block (4+ spaces
    // of indent) inside the still-open div. GFM would emit the code block
    // at top level after closing the div on the blank line; we instead nest
    // it inside the auto-closed-at-EOF div.
    @Test
    fun `example 153 - DIVERGENCE - div div`() = runTest {
        // given
        val textFlow = buildString {
            +"  <div>\n"
            +"\n"
            +"    <div>\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            +"  "
            tag("div") {
                +"\n\n"
                "pre" {
                    "code" {
                        +"<div>\n"
                    }
                }
            }
        }
        // GFM expected:
        /*
              <div>
            <pre><code>&lt;div&gt;
            </code></pre>
         */
    }

    @Test
    fun `example 154 - paragraph Foo div bar`() = runTest {
        // given
        val textFlow = """
            Foo
            <div>
            bar
            </div>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo"
            }
            tag("div") {
                +"\nbar\n"
            }
        }
        // GFM expected:
        /*
            <p>Foo</p>
            <div>
            bar
            </div>
         */
    }

    @Test
    fun `example 155 - div bar text foo`() = runTest {
        // given
        val textFlow = """
            <div>
            bar
            </div>
            *foo*
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("div") {
                +"\nbar\n"
            }
            "p" {
                "em" {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <div>
            bar
            </div>
            *foo*
         */
    }

    // DIVERGENCE: inline `<a>` inside a paragraph self-closes immediately
    // (no nested-tag tracking inside paragraphs). The trailing `</a>` would
    // be needed to balance — instead the `<a>` mark/unmark are emitted
    // adjacent before the rest of the paragraph content.
    @Test
    fun `example 156 - DIVERGENCE - paragraph Foo baz`() = runTest {
        // given
        val textFlow = """
            Foo
            <a href="bar">
            baz
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo\n"
                tag("a", "href" to "bar") { }
                +"\nbaz"
            }
        }
        // GFM expected:
        /*
            <p>Foo
            <a href="bar">
            baz</p>
         */
    }

    // The blank line transitions the `<div>` to sub-parse mode, so the
    // emphasis line becomes `<p><em>Emphasized</em> text.</p>` and the
    // matching `</div>` close-tag check pops the frame.
    @Test
    fun `example 157 - div Emphasized text`() = runTest {
        // given
        val textFlow = """
            <div>

            *Emphasized* text.

            </div>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("div") {
                +"\n\n"
                "p" {
                    "em" {
                        +"Emphasized"
                    }
                    +" text."
                }
            }
        }
        // GFM expected:
        /*
            <div>
            <p><em>Emphasized</em> text.</p>
            </div>
         */
    }

    @Test
    fun `example 158 - div Emphasized text`() = runTest {
        // given
        val textFlow = """
            <div>
            *Emphasized* text.
            </div>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            "div" {
                +"\n*Emphasized* text.\n"
            }
        }
        // GFM expected:
        /*
            <div>
            *Emphasized* text.
            </div>
         */
    }

    // DIVERGENCE: each blank-line-then-tag transitions the surrounding
    // frame to sub-parse and the inner tags open as nested HTML 6/7 frames.
    // Blank lines in inner sub-parse mode (Start at top of stack) are
    // silent — no text events are emitted between sibling closes.
    @Test
    fun `example 159 - DIVERGENCE - table`() = runTest {
        // given
        val textFlow = """
            <table>

            <tr>

            <td>
            Hi
            </td>

            </tr>

            </table>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            "table" {
                +"\n\n"
                "tr" {
                    +"\n\n"
                    "td" {
                        +"\nHi\n"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <table>
            <tr>
            <td>
            Hi
            </td>
            </tr>
            </table>
         */
    }

    // DIVERGENCE: blank-then-indented-content transitions the surrounding
    // frame to sub-parse, where 4+ space indent now opens an indented code
    // block (capturing `<td>...Hi.../td>` as code text). The trailing
    // `  </tr>` falls below 4-space indent so the code block ends; the
    // close-tag check then pops the tr frame. GFM would emit the code at
    // top level after closing the surrounding HTML; we keep it nested.
    @Test
    fun `example 160 - DIVERGENCE - table`() = runTest {
        // given
        val textFlow = buildString {
            +"<table>\n"
            +"\n"
            +"  <tr>\n"
            +"\n"
            +"    <td>\n"
            +"      Hi\n"
            +"    </td>\n"
            +"\n"
            +"  </tr>\n"
            +"\n"
            +"</table>\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("table") {
                +"\n\n  "
                tag("tr") {
                    +"\n\n"
                    "pre" {
                        "code" {
                            +"<td>\n  Hi\n</td>\n"
                        }
                    }
                    +"  "
                }
            }
        }
        // GFM expected:
        /*
            <table>
              <tr>
            <pre><code>&lt;td&gt;
              Hi
              &lt;/td&gt;
            </code></pre>
              </tr>
            </table>
         */
    }

}
