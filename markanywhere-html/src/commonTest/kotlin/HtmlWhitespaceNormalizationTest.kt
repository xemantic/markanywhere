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

package com.xemantic.markanywhere.html

import com.xemantic.kotlin.test.assert
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.dump.AccessibilityAnnotations
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

// Generic boundary/preservation behaviour is covered by
// DropStructuralWhitespaceTest in markanywhere-flow. These tests cover only
// the HTML-specific policy: delegation plus the `isTagged` preserve precision.
class HtmlWhitespaceNormalizationTest {

    @Test
    fun `should drop whitespace-only text events between sibling marks`() = runTest {
        // given
        // The HTML source `<p>x</p>\n  <p>y</p>` produces a whitespace-only
        // text event between the two paragraphs.
        val input = semanticEvents(tagged = true) {
            "p" { +"x" }
            +"\n  "
            "p" { +"y" }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p" { +"x" }
            "p" { +"y" }
        }
    }

    @Test
    fun `should carry a trailing space past an inline close`() = runTest {
        // given — `<b>Note: </b>text` reads "Note: text" in a browser
        val input = semanticEvents(tagged = true) {
            "p" { "b" { +"Note: " }; +"text" }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then — the space lands after the close
        output sameAs semanticEvents(tagged = true) {
            "p" { "b" { +"Note:" }; +" text" }
        }
    }

    @Test
    fun `should move a leading space out of an inline open`() = runTest {
        // given — issue #144: `Menu<a> Home</a>` reads "Menu Home" in a browser
        val input = semanticEvents(tagged = true) {
            "p" { +"Menu"; "a"("href" to "/x") { +" Home" } }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then — the space lands before the open, where a Markdown link label
        // and an opening emphasis delimiter need it
        output sameAs semanticEvents(tagged = true) {
            "p" { +"Menu "; "a"("href" to "/x") { +"Home" } }
        }
    }

    @Test
    fun `should move a leading space out of nested inline opens`() = runTest {
        // given — the space after two opening tags, as its own text event
        val input = semanticEvents(tagged = true) {
            "p" { +"Menu"; "b" { "a"("href" to "/x") { +" "; +"Home" } } }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p" { +"Menu "; "b" { "a"("href" to "/x") { +"Home" } } }
        }
    }

    @Test
    fun `should keep the space after an image at the leading edge of an inline element`() = runTest {
        // given — an image is content, not an opening tag to hold: the space
        // after it separates it from the text that follows
        val input = semanticEvents(tagged = true) {
            "p" { +"Logo"; "b" { +" "; "img"("alt" to "x") { }; +" text" } }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p" { +"Logo "; "b" { "img"("alt" to "x") { }; +" text" } }
        }
    }

    @Test
    fun `should drop a leading space inside an inline open at a block boundary`() = runTest {
        // given — nothing before the link to separate it from
        val input = semanticEvents(tagged = true) {
            "p" { "a"("href" to "/x") { +" Home" } }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p" { "a"("href" to "/x") { +"Home" } }
        }
    }

    @Test
    fun `should keep a leading space inside an inline code region`() = runTest {
        // given — whitespace in a tagged <code> is significant
        val input = semanticEvents(tagged = true) {
            "p" { +"Run"; "code" { +" x" } }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then
        output sameAs input
    }

    @Test
    fun `should drop a space carried past an inline close at a block boundary`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "p" { "b" { +" " }; +"text" }
            "p" { +"a"; "b" { +"b " } }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p" { "b" { }; +"text" }
            "p" { +"a"; "b" { +"b" } }
        }
    }

    @Test
    fun `should preserve whitespace inside tagged pre`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "pre" {
                +"\n    indented code\n"
            }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then
        output sameAs semanticEvents(tagged = true) {
            "pre" {
                +"\n    indented code\n"
            }
        }
    }

    @Test
    fun `should preserve whitespace inside tagged code`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "p" {
                +"Run "
                "code" { +"  spaced  " }
                +" first"
            }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p" {
                +"Run "
                "code" { +"  spaced  " }
                +" first"
            }
        }
    }

    @Test
    fun `should keep a single space between adjacent inline elements`() = runTest {
        // given — the source `<a>…</a> <a>…</a>` produces a whitespace-only
        // text event between two links; the separating space is significant.
        val input = semanticEvents(tagged = true) {
            "a"("href" to "/a") { +"A" }
            +" "
            "a"("href" to "/b") { +"B" }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then — the space survives, so the links don't run together
        output sameAs semanticEvents(tagged = true) {
            "a"("href" to "/a") { +"A" }
            +" "
            "a"("href" to "/b") { +"B" }
        }
    }

    @Test
    fun `should keep a space before Markdown emphasis displayed as a block`() = runTest {
        // given — a blockified <strong> that simplifyHtml kept as Markdown
        // emphasis: it renders inline whatever its CSS display, so the space
        // separating it from the text before is significant
        val input = semanticEvents {
            "p" {
                +"Price"
                +" "
                "strong"(AccessibilityAnnotations.DISPLAY to "block") { +"5" }
            }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then
        output sameAs semanticEvents {
            "p" {
                +"Price "
                "strong" { +"5" }
            }
        }
    }

    @Test
    fun `should collapse interior whitespace and trim block edges of text`() = runTest {
        // given — a content text node with a leading newline+indent, a tab run
        // inside, and a trailing space, sitting directly inside a block.
        val input = semanticEvents(tagged = true) {
            "p" { +"\n    Fundstelle\t\topenJur " }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then — interior run collapses to one space, block edges are trimmed
        output sameAs semanticEvents(tagged = true) {
            "p" { +"Fundstelle openJur" }
        }
    }

    @Test
    fun `should drop whitespace flanking a block element`() = runTest {
        // given — indentation around a heading between two unwrapped contexts
        val input = semanticEvents(tagged = true) {
            +"x\n\t\t\t"
            "h6" { +"Tenor" }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then — the whitespace before the block heading is dropped
        output sameAs semanticEvents(tagged = true) {
            +"x"
            "h6" { +"Tenor" }
        }
    }

    @Test
    fun `should preserve whitespace inside the synthetic frontmatter block`() = runTest {
        // given — the untagged `frontmatter` block holds scalar values whose
        // whitespace is content (a multi-line block scalar, padding, a
        // double space); collapsing it would change the metadata
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "lang") { +"de" }
                "entry"("key" to "title") { +"Ready to check  - Nu" }
                "entry"("key" to "description") { +"first\n\nsecond\n" }
                "entry"("key" to "padded") { +"  x  " }
            }
            "p" { +"x" }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then — every value survives verbatim
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "lang") { +"de" }
                "entry"("key" to "title") { +"Ready to check  - Nu" }
                "entry"("key" to "description") { +"first\n\nsecond\n" }
                "entry"("key" to "padded") { +"  x  " }
            }
            "p" { +"x" }
        }
    }

    @Test
    fun `should drop whitespace between the frontmatter block and the body text`() = runTest {
        // given — `simplifyHtml` unwraps the `<body>`, so its leading
        // whitespace and first text follow the front matter directly; the
        // synthetic block is no inline element, whatever a browser would make
        // of an unknown tag
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Shop" }
            }
            +"\n  "
            +"Hello"
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Shop" }
            }
            +"Hello"
        }
    }

    @Test
    fun `should drop whitespace before an unmatched frontmatter close like before a matched one`() = runTest {
        // given — a truncated stream whose `frontmatter` open was cut off: the
        // close has no open-tag verdict to read back, so it falls back to the
        // element's name, which must still classify the synthetic block as a
        // block, as its open would
        val input = flowOf(
            SemanticEvent.Text("Shop "),
            SemanticEvent.Unmark("frontmatter"),
            SemanticEvent.Text("Hello"),
        )

        // when
        val output = input.dropHtmlStructuralWhitespace().toList()

        // then — no stray space lands between the front matter and the body
        assert(
            output == listOf(
                SemanticEvent.Text("Shop"),
                SemanticEvent.Unmark("frontmatter"),
                SemanticEvent.Text("Hello"),
            )
        )
    }

    @Test
    fun `should preserve non-breaking spaces as content`() = runTest {
        // given — NBSP (U+00A0) is content, not structural whitespace: HTML
        // never collapses it (e.g. legal citations keep "\u00A0823" together).
        val input = semanticEvents(tagged = true) {
            "p" { +"\n  Gem\u00E4\u00DF \u00A7\u00A0823 Abs.\u00A01 ZPO " }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then — ASCII indentation collapses/trims, NBSPs survive verbatim
        output sameAs semanticEvents(tagged = true) {
            "p" { +"Gem\u00E4\u00DF \u00A7\u00A0823 Abs.\u00A01 ZPO" }
        }
    }

    @Test
    fun `should preserve whitespace inside an untagged pre`() = runTest {
        // given - `simplifyHtml` untags everything it keeps, so by the time this
        // operator runs in the HTML pipeline a real HTML <pre> is untagged; an
        // untagged `pre` from the parser is a fenced code block. Both are
        // verbatim, so `pre` cannot be gated on isTagged.
        val input = semanticEvents {
            "pre" {
                "code" { +"fun main() {\n    println()\n}" }
            }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then
        output sameAs semanticEvents {
            "pre" {
                "code" { +"fun main() {\n    println()\n}" }
            }
        }
    }

    @Test
    fun `should not preserve whitespace inside a non-tagged code mark`() = runTest {
        // given
        // A Markdown-native inline `code` mark (isTagged = false) is not an
        // HTML <code> element, so its boundary whitespace is structural noise.
        val input = semanticEvents {
            "p" {
                "code" { +"\n  " }
            }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then
        output sameAs semanticEvents {
            "p" {
                "code" { }
            }
        }
    }

    @Test
    fun `should keep space between block-named elements that compute to inline`() = runTest {
        // given — `<div>` is block by tag name, but its computed display is
        // inline here; the separating space must be kept (and the annotation
        // stripped from the output).
        val input = semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "inline") { +"A" }
            +" "
            "div"(AccessibilityAnnotations.DISPLAY to "inline") { +"B" }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then — display overrides the tag-name heuristic, annotation dropped
        output sameAs semanticEvents(tagged = true) {
            "div" { +"A" }
            +" "
            "div" { +"B" }
        }
    }

    @Test
    fun `should drop space around an inline-named element that computes to block`() = runTest {
        // given — `<span>` is inline by tag name, but its computed display is
        // block here, so the whitespace touching it is structural and dropped.
        val input = semanticEvents(tagged = true) {
            "a"("href" to "/x") { +"A" }
            +" "
            "span"(AccessibilityAnnotations.DISPLAY to "block") { +"B" }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then
        output sameAs semanticEvents(tagged = true) {
            "a"("href" to "/x") { +"A" }
            "span" { +"B" }
        }
    }

    @Test
    fun `should keep space around Markdown links and images displayed as blocks`() = runTest {
        // given — issue #169: a Markdown link or image renders inline whatever
        // CSS displayed it as, so the separator next to it is significant
        val input = semanticEvents {
            "p" {
                "a"("href" to "u", AccessibilityAnnotations.DISPLAY to "block") { +"Home" }
                +" "
                "a"("href" to "v", AccessibilityAnnotations.DISPLAY to "block") { +"About" }
                +" "
                "img"("alt" to "logo", AccessibilityAnnotations.DISPLAY to "block") { }
                +" x"
            }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then
        output sameAs semanticEvents {
            "p" {
                "a"("href" to "u") { +"Home" }
                +" "
                "a"("href" to "v") { +"About" }
                +" "
                "img"("alt" to "logo") { }
                +" x"
            }
        }
    }

    @Test
    fun `should keep space around a kept ref-bearing element displayed as a block`() = runTest {
        // given — issue #169: a generic element simplifyHtml keeps for its ref
        // is written as a raw inline tag, so the separator before it shows
        val input = semanticEvents {
            "p" {
                +"Pro "
                tag("div", AccessibilityAnnotations.REF to "5", AccessibilityAnnotations.DISPLAY to "block") { +"teams" }
            }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then
        output sameAs semanticEvents {
            "p" {
                +"Pro "
                tag("div", AccessibilityAnnotations.REF to "5") { +"teams" }
            }
        }
    }

    @Test
    fun `should keep space around a display contents wrapper`() = runTest {
        // given — a `display: contents` `<div>` lays out no box of its own, so
        // the whitespace touching it separates the inline content on either
        // side, whatever its tag name says.
        val input = semanticEvents(tagged = true) {
            "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"A" }
            +" "
            "div"(AccessibilityAnnotations.DISPLAY to "contents") {
                "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"B" }
            }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then
        output sameAs semanticEvents(tagged = true) {
            "span" { +"A" }
            +" "
            "div" {
                "span" { +"B" }
            }
        }
    }

    @Test
    fun `should move the space out of an inline element holding only a space`() = runTest {
        // given — review of #144: `Hi<b> </b>there` reads "Hi there"
        val input = semanticEvents(tagged = true) {
            "p" { +"Hi"; "b" { +" " }; +"there" }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p" { +"Hi "; "b" { }; +"there" }
        }
    }

    @Test
    fun `should move a leading space out of inline opens before an empty element`() = runTest {
        // given — review of #144: `x<b> <i></i>y</b>` reads "x y"
        val input = semanticEvents(tagged = true) {
            "p" { +"x"; "b" { +" "; "i" { }; +"y" } }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p" { +"x "; "b" { "i" { }; +"y" } }
        }
    }
}
