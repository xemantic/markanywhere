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

package com.xemantic.markanywhere.parse

import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Minimal repros for problematic places found while running the parser
 * against a real-world HTML document (BBC News front page). Each test
 * isolates one pattern where the contents of `<script>` or `<style>` (or
 * their surrounding HTML 6/7 structure) were previously misinterpreted.
 */
class HtmlParsingTest {

    /**
     * **Place 1** — adjacent type-1 blocks on the same line.
     *
     * When two `<script>` / `<style>` blocks sit on the same source line
     * (`</script><style>…</style>`), the second opener is missed entirely:
     * the trailing portion after the first `</script>` is emitted as plain
     * paragraph text, and [detectHtmlBlockType] is not re-run on it.
     * Consequence: the CSS inside the dropped `<style>` flows as paragraph
     * content and §6.9 extended autolinks fire on any `url(https://…)`.
     */
    @Test
    fun `adjacent type-1 blocks on the same line - second opener detected`() = runTest {
        // given
        val src = "<script>a()</script><style>p{color:red}</style>\n"

        // when
        val parsed = flowOf(src).parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            "script" { +"a()" }
            "style" { +"p{color:red}" }
        }
    }

    /**
     * **Place 2** — CSS `url(https://…)` inside `<style>` nested in an outer
     * HTML 6/7 block gets autolinked.
     *
     * Outer `<div>` opens HTML block 6. Inside, `<style>` is a GFM §6.11
     * disallowed tag, so `tokenizeHtmlLine` returns the `<style>` /
     * `</style>` source as `HtmlToken.Text`. The CSS body in between is
     * also tokenized as text and then emitted through the autolink
     * collector — which knows nothing about the surrounding `<style>`
     * scope (no mark was emitted) and happily wraps the URL in `<a>`.
     */
    @Test
    fun `CSS url inside style nested in outer HTML 6 block is not autolinked`() = runTest {
        // given
        val src = "<div>\n<style>@font-face{src:url(https://example.com/x.woff2)}</style>\n</div>\n"

        // when
        val parsed = flowOf(src).parse()

        // then — the URL inside the CSS must NOT generate an `<a>` mark.
        // The `<style>` literal arrives as text (GFM §6.11), so the whole
        // CSS line should reach the collector as a single text run.
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            tag("div") {
                +"\n<style>@font-face{src:url(https://example.com/x.woff2)}</style>\n"
            }
        }
    }

    /**
     * **Place 3** — HTML-like substrings inside a `<script>` body nested
     * in an outer HTML 6/7 block become nested marks.
     *
     * Same root cause as Place 2: the `<script>` source is filtered to
     * literal text (§6.11), but the JS body in between is tokenized line
     * by line — so a `<a>` / `<div>` in a string literal opens a mark.
     */
    @Test
    fun `tags inside script body nested in outer HTML 6 block stay literal`() = runTest {
        // given
        val src = "<div>\n<script>var s = \"<a href='x'>hi</a>\";</script>\n</div>\n"

        // when
        val parsed = flowOf(src).parse()

        // then — no `<a>` mark should appear: the script body is text.
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            tag("div") {
                +"\n<script>var s = \"<a href='x'>hi</a>\";</script>\n"
            }
        }
    }

    /**
     * **Place 4** — `<title>` / `<style>` / `<script>` in the opener chain
     * of an outer HTML 6/7 block leak through as `tagged` marks.
     *
     * [tryFinishHtmlBlock6or7FirstLine] walks contiguous `<…>` opens on
     * the first line and calls `markHtml(name, attrs)` for each one
     * without consulting [GFM_DISALLOWED_TAGS]. So `<title>` opens as a
     * tagged mark; the following content streams through
     * [streamHtmlBlock6or7ContentLine], which *does* filter, so the
     * matching `</title>` arrives as literal text. The block is then
     * unbalanced until end-of-document finalize closes it.
     *
     * Same lookup gap affects `<style>` and `<script>` appearing in the
     * opener chain — i.e. exactly the BBC-style head:
     * `<html><head><meta …><meta …><title …>Page title</title>…`.
     */
    @Test
    fun `disallowed tags in HTML 6 opener chain are filtered to literal text`() = runTest {
        // given
        val src = "<html><head><title>Hello</title></head></html>\n"

        // when
        val parsed = flowOf(src).parse()

        // then — `<title>` is a §6.11 disallowed tag: the opener should
        // arrive as text, the content as text, and the closer as text.
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            tag("html") {
                tag("head") {
                    +"<title>Hello</title>"
                }
            }
        }
    }

    /**
     * **Place 5** — `<` inside a top-level `<script>` body confuses the
     * close-detection helper.
     *
     * [emitHtmlBlock1ContentLine] tokenizes the substring *before* the
     * matched `</script>` with [tokenizeHtmlLine] in order to recognise
     * nested inner close tags. That tokenizer treats any `<name …>` as a
     * potential open tag — including occurrences inside JS string
     * literals (`"<div>foo</div>"`) or JSX-y comparisons (`a < b`). Per
     * GFM §4.6 the body of a type-1 block is **raw text** and must NOT
     * be tokenized: the script body should reach the collector verbatim.
     */
    @Test
    fun `tags inside top-level script body remain literal text`() = runTest {
        // given
        val src = "<script>var s = \"<div>hi</div>\";</script>\n"

        // when
        val parsed = flowOf(src).parse()

        // then — no nested `<div>` mark/unmark should appear in the stream.
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            "script" {
                +"var s = \"<div>hi</div>\";"
            }
        }
    }

    /**
     * **Place 6** — same as Place 5, but for `<style>`. CSS content like
     * `a[href^="<"]` or `selector::before { content: "<x>" }` should pass
     * through verbatim, never tokenized as HTML.
     */
    @Test
    fun `tags inside top-level style body remain literal text`() = runTest {
        // given
        val src = "<style>a::before{content:\"<x>\"}</style>\n"

        // when
        val parsed = flowOf(src).parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            "style" {
                +"a::before{content:\"<x>\"}"
            }
        }
    }

    /**
     * **Place 7** — HTML5 void elements (`<meta>`, `<link>`, `<img>`, `<br>`,
     * `<hr>`, `<input>`, etc.) lack a closing tag in HTML5 source. Without
     * auto-closing them on `mark`, the parser would leave their entries on
     * the open-tag stack until end-of-document — producing a stream that's
     * superficially balanced (drain in `finalize()`) but with all the void
     * `unmark`s piled up at the very end of the output rather than at the
     * source position.
     */
    @Test
    fun `void element inside HTML 6 content auto-closes inline`() = runTest {
        // given
        val src = "<div>\n<meta charset=\"utf-8\">\n</div>\n"

        // when
        val parsed = flowOf(src).parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            tag("div") {
                +"\n"
                tag("meta", "charset" to "utf-8") {}
                +"\n"
            }
        }
    }

    /**
     * **Place 8** — Same auto-close requirement applies to void elements
     * appearing in the contiguous opener chain of an HTML 6/7 block
     * (`<html><head><meta …><meta …>…`). The opener-chain walker must not
     * push them onto `openTags` since no matching close tag will ever
     * arrive.
     */
    @Test
    fun `void element in HTML 6 opener chain auto-closes`() = runTest {
        // given
        val src = "<html><meta charset=\"utf-8\"><body>x</body></html>\n"

        // when
        val parsed = flowOf(src).parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            tag("html") {
                tag("meta", "charset" to "utf-8") {}
                tag("body") {
                    +"x"
                }
            }
        }
    }

    /**
     * **Place 9** — Inline raw HTML (`<…>` inside a paragraph, dispatched
     * via `processInlineCharImpl`) must also auto-close void elements so
     * the inline open stack stays balanced. Without it, every inline `<br>`
     * leaves an unmatched frame until block close drains it.
     */
    @Test
    fun `inline void element in paragraph auto-closes`() = runTest {
        // given
        val src = "foo<br>bar\n"

        // when
        val parsed = flowOf(src).parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo"
                tag("br") {}
                +"bar"
            }
        }
    }

    /**
     * **Place 10** — A close tag whose name doesn't match the top of the
     * current frame's `openTags` (but does match a deeper open) must
     * **drain** the intermediate frames LIFO and close the match. Before
     * the fix, the close fired its `unmark` unconditionally, leaving inner
     * opens dangling and producing an unbalanced stream.
     */
    @Test
    fun `mismatched close drains inner tags LIFO`() = runTest {
        // given
        val src = "<div>\n<span><b>text</span>\n</div>\n"

        // when
        val parsed = flowOf(src).parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            tag("div") {
                +"\n"
                tag("span") {
                    tag("b") {
                        +"text"
                    }
                }
                +"\n"
            }
        }
    }

    /**
     * **Place 11** — A close tag with no matching open in the current
     * frame's `openTags` must be dropped silently (HTML5 parser rule:
     * "an end tag whose tag name is not in the stack of open elements is
     * a parse error; ignore the token"). Before the fix, the close fired
     * an orphan `unmark` event with no matching `mark`, breaking the
     * downstream stack-based renderer.
     */
    @Test
    fun `orphan close tag with no matching open is dropped`() = runTest {
        // given
        val src = "<div>text</span></div>\n"

        // when
        val parsed = flowOf(src).parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            tag("div") {
                +"text"
            }
        }
    }

    /**
     * **Place 12** — A self-contained `<div>…</div>` on a single content line
     * inside a sub-parsed `div`-rooted HTML block must NOT be mistaken for the
     * enclosing frame's root close.
     *
     * The blank line transitions the outer `<div class="ad">` frame to
     * sub-parse mode. The line `<div id="slot"></div>` both opens and closes a
     * nested `div`. Before the fix, [findRootCloseTagIndex] returned the first
     * `</div>` regardless of same-line balance, so `tryCloseEnclosingHtmlBlock`
     * popped the enclosing `ad` frame prematurely (an extra `unmark`), and the
     * real `</div>` then leaked as literal text — desyncing the rest of the
     * document into raw passthrough. The index now tracks same-line nesting and
     * only matches an *excess* (unbalanced) close.
     */
    @Test
    fun `one-line nested div in sub-parsed div block does not close the frame early`() = runTest {
        // given
        val src = "<div class=\"ad\">\n\n<div id=\"slot\"></div>\n<section>after</section>\n</div>\n"

        // when
        val parsed = flowOf(src).parse()

        // then — the empty inner div is balanced; the outer `ad` div closes
        // only on the final `</div>`, with `<section>` content in between.
        parsed.mergeAdjacentText() sameAs semanticEvents(tagged = true) {
            "div"("class" to "ad") {
                +"\n\n"
                "div"("id" to "slot") {}
                "section" { +"after" }
            }
        }
    }

    /**
     * **Place 13** — A GFM §6.11 disallowed tag (`<script>`) encountered
     * *inline* (mid-line, not at a block boundary) drops the element **and
     * its raw-text body** rather than leaking the source as literal text.
     *
     * At a block boundary `<script>` opens a type-1 HTML block (Place 1/5);
     * minified real-world HTML, however, packs the whole document onto one
     * line (`…</footer><script id="__NEXT_DATA__">{…}</script>…`), so the
     * `<script>` arrives mid-line and reaches the inline `<…>` dispatch in
     * `processInlineCharImpl`. The body — a serialized state blob in the BBC
     * page — would otherwise dump verbatim into the output. The surrounding
     * paragraph text is preserved.
     *
     * DIVERGENCE from GFM §6.11, which escapes disallowed tags to visible
     * text (`&lt;script>`); we drop them, prioritising clean LLM-facing output
     * over spec-faithful escaping.
     */
    @Test
    fun `DIVERGENCE inline disallowed script tag and its body are dropped`() = runTest {
        // given
        val src = "before<script>alert(1)</script>after\n"

        // when
        val parsed = flowOf(src).parse()

        // then — only the paragraph text on either side survives.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"beforeafter" }
        }
    }

    /**
     * **Place 14** — same as Place 13 for inline `<style>`; the CSS body,
     * including HTML-like substrings, is dropped.
     */
    @Test
    fun `DIVERGENCE inline disallowed style tag and its body are dropped`() = runTest {
        // given
        val src = "x<style>a::before{content:\"<x>\"}</style>y\n"

        // when
        val parsed = flowOf(src).parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"xy" }
        }
    }

    /**
     * **Place 15** — the inline close-tag matcher tolerates whitespace before
     * the final `>` (`</script   >`), mirroring the block-1 close rule.
     */
    @Test
    fun `DIVERGENCE inline disallowed close tag tolerates trailing whitespace`() = runTest {
        // given
        val src = "a<script>js</script\t >b\n"

        // when
        val parsed = flowOf(src).parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"ab" }
        }
    }

    /**
     * **Place 16** — once the disallowed element closes, normal inline parsing
     * resumes: a following link / emphasis is recognised, and a JSON body
     * containing `<x>` does not derail the close detection. Models the BBC
     * `[link]<script>{json}</script>[link]` shape.
     */
    @Test
    fun `inline parsing resumes after a dropped disallowed element`() = runTest {
        // given
        val src = "[BBC](/news)<script type=\"application/json\">{\"a\":1,\"b\":\"<x>\"}</script>[More](/more)\n"

        // when
        val parsed = flowOf(src).parse()

        // then — both links parse; the script body (with its `<x>`) is gone.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "/news") { +"BBC" }
                "a"("href" to "/more") { +"More" }
            }
        }
    }

    /**
     * **Place 17** — a self-closing / void-shaped disallowed tag has no body to
     * skip; it is simply dropped and the surrounding text flows through.
     */
    @Test
    fun `inline self-closing disallowed tag is dropped without skipping a body`() = runTest {
        // given
        val src = "p<iframe src=\"x\"/>q\n"

        // when
        val parsed = flowOf(src).parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"pq" }
        }
    }

    /**
     * **Place 18** — DIVERGENCE / bound: the skip cannot span a soft break (same
     * constraint as every other inline construct — `flushInline` clears it at the
     * block boundary). An *unclosed* inline disallowed opener therefore consumes
     * the rest of its line only; the next line resumes normally. Real captures
     * always close their `<script>`/`<style>`, so this edge is benign in practice.
     */
    @Test
    fun `DIVERGENCE unclosed inline disallowed opener drops only the rest of its line`() = runTest {
        // given
        val src = "keep<script>dropped to end of line\nnext line\n"

        // when
        val parsed = flowOf(src).parse()

        // then — line 1 keeps only `keep`; line 2 is an ordinary soft-break
        // continuation of the same paragraph.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"keep\nnext line" }
        }
    }
}
