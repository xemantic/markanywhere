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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SeparateUnwrappedBlocksTest {

    @Test
    fun `should separate flex children that share no source whitespace`() = runTest {
        // given — the BBC card-metadata shape: two blockified spans in a flex
        // container with an empty CSS-drawn divider div and no whitespace text.
        val input = semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "flex") {
                "span" { +"3 hrs ago" }
                "div"("data-testid" to "card-metadata-separator") { }
                "span" { +"Europe" }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then — a space is injected before the second box
        output sameAs semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "flex") {
                "span" { +"3 hrs ago" }
                "div"("data-testid" to "card-metadata-separator") { }
                +" "
                "span" { +"Europe" }
            }
        }
    }

    @Test
    fun `should break the lines of uncaptured divs into paragraphs by their UA default display`() = runTest {
        // given — what Chrome leaves in a contenteditable after "Hi Paul,\nThree
        // hearts.": the first line as bare text, each later one in a `<div>`.
        // Without a captured display the UA stylesheet's `block` is assumed.
        val input = semanticEvents(tagged = true) {
            "div"("role" to "textbox") {
                +"Hi Paul,"
                "div" { +"Three hearts." }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div"("role" to "textbox") {
                +"Hi Paul,"
                "div" {
                    "p" { +"Three hearts." }
                }
            }
        }
    }

    @Test
    fun `should not separate content across a display contents wrapper`() = runTest {
        // given — Svelte wraps a component carrying CSS custom properties in a
        // `<div style="display: contents">`, which lays out no box of its own,
        // so the price renders flush as `US$5`.
        val input = semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"US" }
                "div"(AccessibilityAnnotations.DISPLAY to "contents") {
                    "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"$5" }
                }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then
        output sameAs input
    }

    @Test
    fun `should not separate across an unannotated div in a dump that left block out`() = runTest {
        // given — a dump captured before every display was recorded: a plain
        // `block` and a `display: contents` were both left unannotated, so once
        // an annotation shows the stream is a capture (the `<html>`, `<head>`
        // and `<body>` of such a dump carry none), an absent one cannot be read
        // as the UA default of the tag.
        val input = semanticEvents(tagged = true) {
            "html" {
                "head" { }
                "body" {
                    "p" {
                        "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"US" }
                        "div" {
                            "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"$5" }
                        }
                    }
                }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then
        output sameAs input
    }

    @Test
    fun `should recognise a capture by any of its annotations`() = runTest {
        // given — an old dump's first annotated element may be a control, its
        // ref the only capture annotation on it
        val input = semanticEvents(tagged = true) {
            "body" {
                "a"(AccessibilityAnnotations.REF to "1") { +"US" }
                "div" { +"$5" }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then
        output sameAs input
    }


    @Test
    fun `should not take a redacted control for a capture`() = runTest {
        // given — the JS walker records no display at all, but it marks a
        // password field redacted through the producers' shared form state;
        // the divs after it must still read as their UA default `block`
        val input = semanticEvents(tagged = true) {
            "body" {
                "input"("type" to "password", AccessibilityAnnotations.REDACTED to "filled") { }
                "div" { +"Forgot password?" }
                "div" { +"Sign up" }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then
        output sameAs semanticEvents(tagged = true) {
            "body" {
                "input"("type" to "password", AccessibilityAnnotations.REDACTED to "filled") { }
                "div" { +"Forgot password?" }
                "div" {
                    "p" { +"Sign up" }
                }
            }
        }
    }

    @Test
    fun `should not take a scroll container for a capture`() = runTest {
        // given — the JS walker records no display at all, but it marks a
        // scroll container scrollable by the producers' shared rule; the
        // divs after it must still read as their UA default `block`
        val input = semanticEvents(tagged = true) {
            "body" {
                "pre"(AccessibilityAnnotations.SCROLLABLE to "true") { +"code" }
                "div" { +"Forgot password?" }
                "div" { +"Sign up" }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then
        output sameAs semanticEvents(tagged = true) {
            "body" {
                "pre"(AccessibilityAnnotations.SCROLLABLE to "true") { +"code" }
                "div" { +"Forgot password?" }
                "div" {
                    "p" { +"Sign up" }
                }
            }
        }
    }

    @Test
    fun `should not separate across an uncaptured element the browser never renders`() = runTest {
        // given — the UA stylesheet gives a `<script>` `display: none`: it
        // lays out no box, so there is no boundary to cross
        val input = semanticEvents(tagged = true) {
            "p" {
                +"price"
                "script" { +"track()" }
                +"5"
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then
        output sameAs input
    }

    @Test
    fun `should read an uncaptured custom element as inline like a browser does`() = runTest {
        // given — the UA stylesheet lays an unknown or custom element out
        // inline, so a web component inside a word must not split it
        val input = semanticEvents(tagged = true) {
            "p" {
                +"Price: "
                "x-currency" { +"€" }
                +"5"
            }
            "div" {
                "x-row" { +"first" }
                "x-row" { +"second" }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then
        output sameAs input
    }

    @Test
    fun `should read an uncaptured frontmatter as a block like the whitespace normalizer does`() = runTest {
        // given — the synthetic `frontmatter` is no HTML element, so a
        // browser would lay the unknown tag out inline; it is document
        // metadata standing apart from the body, and every operator judging
        // an element by its name alone must read it as a block
        val input = semanticEvents {
            "frontmatter" { +"Shop" }
            +"Hello"
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then — the content below it opens a paragraph, as after any stacked box
        output sameAs semanticEvents {
            "frontmatter" { +"Shop" }
            tag("p") { +"Hello" }
        }
    }

    @Test
    fun `should break the lines of a span the capture recorded as a block into paragraphs`() = runTest {
        // given — a rich editor's `<span style="display: block">` per line
        val input = semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "block") {
                "span"(AccessibilityAnnotations.DISPLAY to "block") { +"Hi Paul," }
                "span"(AccessibilityAnnotations.DISPLAY to "block") { +"Three hearts." }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "block") {
                "span"(AccessibilityAnnotations.DISPLAY to "block") { +"Hi Paul," }
                "span"(AccessibilityAnnotations.DISPLAY to "block") {
                    "p" { +"Three hearts." }
                }
            }
        }
    }

    @Test
    fun `should not separate unannotated inline elements`() = runTest {
        // given — a hand-built stream with no display annotations at all
        val input = semanticEvents(tagged = true) {
            "p" {
                "strong" { +"bold" }
                "span" { +"plain" }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then — inline by their tags, so they abut as authored
        output sameAs input
    }

    @Test
    fun `should separate two block boxes under a non-flex inline parent`() = runTest {
        // given — the BBC card shape: an inline block-wrapping <a> holding an
        // image box and a badge box, each a separate grid box, no whitespace.
        val input = semanticEvents(tagged = true) {
            "a"("href" to "/card", AccessibilityAnnotations.DISPLAY to "inline") {
                "div"(AccessibilityAnnotations.DISPLAY to "grid") {
                    "img"("src" to "/photo.jpg", "alt" to "Photo") {}
                }
                "div"(AccessibilityAnnotations.DISPLAY to "grid") {
                    "span" { +"LIVE" }
                }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then — image and badge no longer merge
        output sameAs semanticEvents(tagged = true) {
            "a"("href" to "/card", AccessibilityAnnotations.DISPLAY to "inline") {
                "div"(AccessibilityAnnotations.DISPLAY to "grid") {
                    "img"("src" to "/photo.jpg", "alt" to "Photo") {}
                }
                +" "
                "div"(AccessibilityAnnotations.DISPLAY to "grid") {
                    "span" { +"LIVE" }
                }
            }
        }
    }

    @Test
    fun `should not separate adjacent inline elements`() = runTest {
        // given — `<b>bold</b><i>italic</i>` renders as "bolditalic"; no box
        // boundary is crossed, so nothing is injected.
        val input = semanticEvents(tagged = true) {
            "p" {
                "b"(AccessibilityAnnotations.DISPLAY to "inline") { +"bold" }
                "i"(AccessibilityAnnotations.DISPLAY to "inline") { +"italic" }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p" {
                "b"(AccessibilityAnnotations.DISPLAY to "inline") { +"bold" }
                "i"(AccessibilityAnnotations.DISPLAY to "inline") { +"italic" }
            }
        }
    }

    @Test
    fun `should not separate across a surviving block element`() = runTest {
        // given — the header shape: a flex container whose two children each wrap
        // a block-level <button>; the surviving <button> already separates them.
        val input = semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "flex") {
                "a"("href" to "/register", AccessibilityAnnotations.DISPLAY to "inline") {
                    "button" { "span" { +"Register" } }
                }
                "a"("href" to "/signin", AccessibilityAnnotations.DISPLAY to "inline") {
                    "button" { "span" { +"Sign In" } }
                }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then — nothing injected
        output sameAs semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "flex") {
                "a"("href" to "/register", AccessibilityAnnotations.DISPLAY to "inline") {
                    "button" { "span" { +"Register" } }
                }
                "a"("href" to "/signin", AccessibilityAnnotations.DISPLAY to "inline") {
                    "button" { "span" { +"Sign In" } }
                }
            }
        }
    }

    @Test
    fun `should not inject inside a preserve region`() = runTest {
        // given — whitespace inside <pre> is significant; no separators there.
        val input = semanticEvents(tagged = true) {
            "pre" {
                "span" { +"a" }
                "span" { +"b" }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then
        output sameAs semanticEvents(tagged = true) {
            "pre" {
                "span" { +"a" }
                "span" { +"b" }
            }
        }
    }

    @Test
    fun `should separate adjacent links that share no source whitespace`() = runTest {
        // given — the Hacker News masthead: the gap after the bold site name is
        // a CSS margin, so the next link follows with no whitespace text
        val input = semanticEvents(tagged = true) {
            "span"(AccessibilityAnnotations.DISPLAY to "inline") {
                "b"(AccessibilityAnnotations.DISPLAY to "inline") {
                    "a"("href" to "news", AccessibilityAnnotations.DISPLAY to "inline") { +"Hacker News" }
                }
                "a"("href" to "newest", AccessibilityAnnotations.DISPLAY to "inline") { +"new" }
                +" | "
                "a"("href" to "front", AccessibilityAnnotations.DISPLAY to "inline") { +"past" }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then — the space lands between the links, not inside a link label
        output sameAs semanticEvents(tagged = true) {
            "span"(AccessibilityAnnotations.DISPLAY to "inline") {
                "b"(AccessibilityAnnotations.DISPLAY to "inline") {
                    "a"("href" to "news", AccessibilityAnnotations.DISPLAY to "inline") { +"Hacker News" }
                }
                +" "
                "a"("href" to "newest", AccessibilityAnnotations.DISPLAY to "inline") { +"new" }
                +" | "
                "a"("href" to "front", AccessibilityAnnotations.DISPLAY to "inline") { +"past" }
            }
        }
    }

    @Test
    fun `should separate an adjacent link outside the formatting wrapping it`() = runTest {
        // given — the masthead shape reversed: the second link sits in a <b>,
        // so a separator right before its <a> would land inside the <b>, where
        // the whitespace normalizer drops it after the opening tag (issue #96)
        val input = semanticEvents(tagged = true) {
            "p" {
                "a"("href" to "u") { +"x" }
                "b" {
                    "a"("href" to "v") { +"y" }
                }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then — the space lands before the <b>, between the two controls
        output sameAs semanticEvents(tagged = true) {
            "p" {
                "a"("href" to "u") { +"x" }
                +" "
                "b" {
                    "a"("href" to "v") { +"y" }
                }
            }
        }
    }

    @Test
    fun `should separate an adjacent declared control outside the formatting wrapping it`() = runTest {
        // given — a link followed by a `role="button"` span inside a <b>: a
        // declared control adjacent to the link, so the separator before it
        // must not land inside the <b> either (a native <button> renders as a
        // block of its own and needs none)
        val input = semanticEvents(tagged = true) {
            "p" {
                "a"("href" to "u") { +"x" }
                "b" {
                    "span"("role" to "button", AccessibilityAnnotations.DISPLAY to "inline") { +"y" }
                }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then — the space lands before the <b>, between the two controls
        output sameAs semanticEvents(tagged = true) {
            "p" {
                "a"("href" to "u") { +"x" }
                +" "
                "b" {
                    "span"("role" to "button", AccessibilityAnnotations.DISPLAY to "inline") { +"y" }
                }
            }
        }
    }

    @Test
    fun `should separate box content outside the formatting wrapping it`() = runTest {
        // given — the second flex child's content opens in a <b>: a separator
        // right before the text would land inside the <b> and be dropped
        val input = semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "flex") {
                "span" { +"3 hrs ago" }
                "span" {
                    "b"(AccessibilityAnnotations.DISPLAY to "inline") { +"Europe" }
                }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then — the space lands before every mark opened since "3 hrs ago"
        output sameAs semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "flex") {
                "span" { +"3 hrs ago" }
                +" "
                "span" {
                    "b"(AccessibilityAnnotations.DISPLAY to "inline") { +"Europe" }
                }
            }
        }
    }

    @Test
    fun `should separate a flex item that starts with a code span before the span`() = runTest {
        // given — issue #148 with the boxes side by side: a tagged <code> is a
        // preserve region, whose verbatim text still decides the separator in
        // front of it
        val input = semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "flex") {
                "div" { +"Pro" }
                "div" {
                    "code" { +"teams" }
                }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then — the space lands before the marks opened since "Pro"
        output sameAs semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "flex") {
                "div" { +"Pro" }
                +" "
                "div" {
                    "code" { +"teams" }
                }
            }
        }
    }

    @Test
    fun `should separate box content that starts with a code span before the span`() = runTest {
        // given — issue #148: a tagged <code> is a preserve region, whose
        // verbatim text still decides the break in front of it
        val input = semanticEvents(tagged = true) {
            "section" {
                "div" { +"Pro" }
                "div" {
                    "code" { +"teams" }
                }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then — the paragraph wraps the code span, never the other way round
        output sameAs semanticEvents(tagged = true) {
            "section" {
                "div" { +"Pro" }
                "div" {
                    "p" {
                        "code" { +"teams" }
                    }
                }
            }
        }
    }

    @Test
    fun `should separate a block displayed kept element before its opening tag`() = runTest {
        // given — a blockified <strong> survives simplifyHtml, so the space
        // must not follow its opening tag
        val input = semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "flex") {
                "span"(AccessibilityAnnotations.DISPLAY to "block") { +"Price" }
                "strong"(AccessibilityAnnotations.DISPLAY to "block") { +"5" }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "flex") {
                "span"(AccessibilityAnnotations.DISPLAY to "block") { +"Price" }
                +" "
                "strong"(AccessibilityAnnotations.DISPLAY to "block") { +"5" }
            }
        }
    }

    @Test
    fun `should not separate a link from adjacent plain text`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "p" {
                "a"("href" to "u") { +"link" }
                +"s"
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then
        output sameAs input
    }

    @Test
    fun `should separate adjacent controls built out of generic elements`() = runTest {
        // given — Bing's search box: two icon buttons made of `<div>`s, each
        // given its name by labelActionableElements; simplifyHtml unwraps a
        // generic element that carries no ref, which would leave
        // `Search using voiceSearch using an image`
        val input = semanticEvents(tagged = true) {
            "div"("role" to "button", AccessibilityAnnotations.DISPLAY to "inline-block") { +"Search using voice" }
            "span"("role" to "button", AccessibilityAnnotations.DISPLAY to "inline") { +"Search using an image" }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div"("role" to "button", AccessibilityAnnotations.DISPLAY to "inline-block") { +"Search using voice" }
            +" "
            "span"("role" to "button", AccessibilityAnnotations.DISPLAY to "inline") { +"Search using an image" }
        }
    }

    @Test
    fun `should not separate adjacent ref-bearing generic elements`() = runTest {
        // given - a ref alone does not make a control a reader tells apart:
        // a price split into two clickable spans is still one word
        val input = semanticEvents(tagged = true) {
            "span"(AccessibilityAnnotations.REF to "1", AccessibilityAnnotations.DISPLAY to "inline") { +"US" }
            "span"(AccessibilityAnnotations.REF to "2", AccessibilityAnnotations.DISPLAY to "inline") { +"$5" }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then
        output sameAs input
    }

    @Test
    fun `should wrap the content after a stacked box boundary in a paragraph`() = runTest {
        // given — issue #109: content of two stacked boxes, one below the other
        val input = semanticEvents(tagged = true) {
            "body" {
                "div" {
                    +"x"
                    "div" { +"A" }
                }
                "div" { +"B" }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then — the first run stays as it is: the renderer separates loose
        // text from the paragraph after it
        output sameAs semanticEvents(tagged = true) {
            "body" {
                "div" {
                    +"x"
                    "div" { "p" { +"A" } }
                }
                "div" { "p" { +"B" } }
            }
        }
    }

    @Test
    fun `should keep a run of inline content in one paragraph`() = runTest {
        // given — inline elements and a display contents wrapper inside the
        // second box are one run of content, not boxes of their own
        val input = semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "block") {
                "div"(AccessibilityAnnotations.DISPLAY to "block") { +"A" }
                "div"(AccessibilityAnnotations.DISPLAY to "block") {
                    +"Price "
                    "strong"(AccessibilityAnnotations.DISPLAY to "inline") { +"US" }
                    "div"(AccessibilityAnnotations.DISPLAY to "contents") {
                        "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"$5" }
                    }
                    +" now"
                }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "block") {
                "div"(AccessibilityAnnotations.DISPLAY to "block") { +"A" }
                "div"(AccessibilityAnnotations.DISPLAY to "block") {
                    "p" {
                        +"Price "
                        "strong"(AccessibilityAnnotations.DISPLAY to "inline") { +"US" }
                        "div"(AccessibilityAnnotations.DISPLAY to "contents") {
                            "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"$5" }
                        }
                        +" now"
                    }
                }
            }
        }
    }

    @Test
    fun `should end a paragraph at the next stacked box`() = runTest {
        // given — loose text between stacked boxes in one container
        val input = semanticEvents(tagged = true) {
            "div" {
                "div" { +"A" }
                +"B"
                "div" { +"C" }
                +"D"
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div" {
                "div" { +"A" }
                "p" { +"B" }
                "div" { "p" { +"C" } }
                "p" { +"D" }
            }
        }
    }

    @Test
    fun `should not break between stacked boxes inside side by side flex items`() = runTest {
        // given — two flex items, each holding a block: the blocks stack inside
        // their item, but the items themselves sit beside each other
        val input = semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "flex") {
                "div"(AccessibilityAnnotations.DISPLAY to "block") {
                    "div"(AccessibilityAnnotations.DISPLAY to "block") { +"3 hrs ago" }
                }
                "div"(AccessibilityAnnotations.DISPLAY to "block") {
                    "div"(AccessibilityAnnotations.DISPLAY to "block") { +"Europe" }
                }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then — a space, as between any two flex items
        output sameAs semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "flex") {
                "div"(AccessibilityAnnotations.DISPLAY to "block") {
                    "div"(AccessibilityAnnotations.DISPLAY to "block") { +"3 hrs ago" }
                }
                +" "
                "div"(AccessibilityAnnotations.DISPLAY to "block") {
                    "div"(AccessibilityAnnotations.DISPLAY to "block") { +"Europe" }
                }
            }
        }
    }

    @Test
    fun `should break before a flex row stacked below a block`() = runTest {
        // given — a flex row under a headline: the row's first item is below
        // the headline, whatever the row does inside, so the paragraph holds
        // the whole row
        val input = semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "block") {
                "div"(AccessibilityAnnotations.DISPLAY to "block") { +"Headline" }
                "div"(AccessibilityAnnotations.DISPLAY to "flex") {
                    "span"(AccessibilityAnnotations.DISPLAY to "block") { +"3 hrs ago" }
                    "span"(AccessibilityAnnotations.DISPLAY to "block") { +"Europe" }
                }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "block") {
                "div"(AccessibilityAnnotations.DISPLAY to "block") { +"Headline" }
                "div"(AccessibilityAnnotations.DISPLAY to "flex") {
                    "p" {
                        "span"(AccessibilityAnnotations.DISPLAY to "block") { +"3 hrs ago" }
                        +" "
                        "span"(AccessibilityAnnotations.DISPLAY to "block") { +"Europe" }
                    }
                }
            }
        }
    }

    @Test
    fun `should not open a paragraph where Markdown cannot hold one`() = runTest {
        // given — stacked boxes inside a heading, a link and a control
        val input = semanticEvents(tagged = true) {
            "h2" {
                "div" { +"A" }
                "div" { +"B" }
            }
            "a"("href" to "/card") {
                "div" { +"C" }
                "div" { +"D" }
            }
            "div"("role" to "button") {
                "div" { +"E" }
                "div" { +"F" }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then — the space separator of an unwrapped box, as before (and the
        // one between two adjacent controls); the control, itself a stacked
        // box below the link, gets its paragraph around it instead
        output sameAs semanticEvents(tagged = true) {
            "h2" {
                "div" { +"A" }
                +" "
                "div" { +"B" }
            }
            "a"("href" to "/card") {
                "div" { +"C" }
                +" "
                "div" { +"D" }
            }
            +" "
            "p" {
                "div"("role" to "button") {
                    "div" { +"E" }
                    +" "
                    "div" { +"F" }
                }
            }
        }
    }

    @Test
    fun `should not wrap a run that turns out to hold a block`() = runTest {
        // given — a block-wrapping link after a stacked box: a paragraph
        // around it would put a heading inside a paragraph
        val input = semanticEvents(tagged = true) {
            "div" { +"A" }
            "div" {
                "a"("href" to "/card") {
                    +"Live"
                    "h2" { +"Headline" }
                }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then — the run is left as it was, with the separator space before
        // the marks opened since the last token
        output sameAs semanticEvents(tagged = true) {
            "div" { +"A" }
            +" "
            "div" {
                "a"("href" to "/card") {
                    +"Live"
                    "h2" { +"Headline" }
                }
            }
        }
    }

    @Test
    fun `should break before a block after an inline block at the same level`() = runTest {
        // given — an inline-block, then a block box: the block starts a line
        val input = semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "block") {
                "span"(AccessibilityAnnotations.DISPLAY to "inline-block") { +"A" }
                "div"(AccessibilityAnnotations.DISPLAY to "block") { +"B" }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "block") {
                "span"(AccessibilityAnnotations.DISPLAY to "inline-block") { +"A" }
                "div"(AccessibilityAnnotations.DISPLAY to "block") { "p" { +"B" } }
            }
        }
    }

    @Test
    fun `should not wrap a description list in a paragraph`() = runTest {
        // given — a `<dl>` reached through a `display: contents` wrapper, after
        // a block: `simplifyHtml` keeps the list, which a paragraph cannot hold
        val input = semanticEvents(tagged = true) {
            "body" {
                "div" { +"X" }
                "div"(AccessibilityAnnotations.DISPLAY to "contents") {
                    "span"(AccessibilityAnnotations.DISPLAY to "inline") {
                        "dl" {
                            "dt" { +"Term" }
                            "dd" { +"Definition" }
                        }
                    }
                }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks().toList()

        // then
        assert(output.none { it is SemanticEvent.Mark && it.name == "p" })
    }

    @Test
    fun `should open the paragraph after a stacked box before the formatting its text starts with`() = runTest {
        // given — the second box's content opens in a <b>
        val input = semanticEvents(tagged = true) {
            "body" {
                "div" { +"X" }
                "div" {
                    "b" { +"A" }
                }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then — the paragraph wraps the <b>
        output sameAs semanticEvents(tagged = true) {
            "body" {
                "div" { +"X" }
                "div" {
                    "p" {
                        "b" { +"A" }
                    }
                }
            }
        }
    }

    @Test
    fun `should open the paragraph around formatting that wraps a stacked box`() = runTest {
        // given — the <b> opens before the stacked box, so it is held with the
        // run, and a paragraph cannot open inside a kept inline element
        val input = semanticEvents(tagged = true) {
            "body" {
                "div" { +"X" }
                "b" {
                    "div" { +"A" }
                }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then — the paragraph opens before the <b>, at the nearest ancestor
        // that can hold it
        output sameAs semanticEvents(tagged = true) {
            "body" {
                "div" { +"X" }
                "p" {
                    "b" {
                        "div" { +"A" }
                    }
                }
            }
        }
    }

    @Test
    fun `should restore the separator before the formatting of a run a paragraph cannot wrap`() = runTest {
        // given — a stacked box whose content opens in a link and an <em>, the
        // link turning out to wrap a block
        val input = semanticEvents(tagged = true) {
            "body" {
                "div" { +"X" }
                "div" {
                    "a"("href" to "/card") {
                        "em" { +"A" }
                        "h2" { +"B" }
                    }
                }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then — the space is back before every mark held since "X"
        output sameAs semanticEvents(tagged = true) {
            "body" {
                "div" { +"X" }
                +" "
                "div" {
                    "a"("href" to "/card") {
                        "em" { +"A" }
                        "h2" { +"B" }
                    }
                }
            }
        }
    }

    @Test
    fun `should separate a flex item that starts with emphasis before the emphasis`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "flex") {
                "div"(AccessibilityAnnotations.DISPLAY to "block") { +"a" }
                "div"(AccessibilityAnnotations.DISPLAY to "block") {
                    "em"(AccessibilityAnnotations.DISPLAY to "inline") { +"b" }
                }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "flex") {
                "div"(AccessibilityAnnotations.DISPLAY to "block") { +"a" }
                +" "
                "div"(AccessibilityAnnotations.DISPLAY to "block") {
                    "em"(AccessibilityAnnotations.DISPLAY to "inline") { +"b" }
                }
            }
        }
    }
}
