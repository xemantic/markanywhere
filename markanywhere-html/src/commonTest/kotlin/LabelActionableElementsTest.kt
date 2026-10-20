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
import com.xemantic.markanywhere.dump.AccessibilityAnnotations
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class LabelActionableElementsTest {

    @Test
    fun `should leave a link that already has text alone`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "a"("href" to "/") { +"Home" }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs input
    }

    @Test
    fun `should label an empty link from a descendant title`() = runTest {
        // given - the Hacker News upvote arrow: the only name is a `title` on
        // an inner div that `simplifyHtml` unwraps, dropping the name with it.
        val input = semanticEvents(tagged = true) {
            "a"("href" to "vote?id=1&how=up") {
                "div"("class" to "votearrow", "title" to "upvote") { }
            }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs semanticEvents(tagged = true) {
            "a"("href" to "vote?id=1&how=up") {
                +"upvote"
                "div"("class" to "votearrow", "title" to "upvote") { }
            }
        }
    }

    @Test
    fun `should label an empty link from its own aria-label`() = runTest {
        // when
        val output = semanticEvents(tagged = true) {
            "a"("href" to "/", "aria-label" to "Home") { "svg" { } }
        }.labelActionableElements()

        // then
        output sameAs semanticEvents(tagged = true) {
            "a"("href" to "/", "aria-label" to "Home") {
                +"Home"
                "svg" { }
            }
        }
    }

    @Test
    fun `should mark a nameless graphic in an otherwise unlabelled link`() = runTest {
        // given - nothing names this control, but clicking a picture is still
        // information the reader can act on.
        val input = semanticEvents(tagged = true) {
            "a"("href" to "/") { "svg" { "path"("d" to "M0 0") { } } }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs semanticEvents(tagged = true) {
            "a"("href" to "/") {
                "img"("alt" to GRAPHIC_PLACEHOLDER_ALT) { }
                "svg" { "path"("d" to "M0 0") { } }
            }
        }
    }

    @Test
    fun `should not mark a graphic when the control is already labelled`() = runTest {
        // given - the BBC follow button: the label is on the control, so the
        // decorative icon inside it adds nothing.
        val input = semanticEvents(tagged = true) {
            "button"("aria-label" to "Follow BBC on x") {
                "svg" { "path"("d" to "M1 2") { } }
            }
        }

        // when
        val output = input.labelActionableElements()

        // then — labelled from aria-label, no placeholder
        output sameAs semanticEvents(tagged = true) {
            "button"("aria-label" to "Follow BBC on x") {
                +"Follow BBC on x"
                "svg" { "path"("d" to "M1 2") { } }
            }
        }
    }

    @Test
    fun `should leave a link whose graphic carries its own name alone`() = runTest {
        // given - resolveInlineGraphics turns this into an ![name]() downstream
        val input = semanticEvents(tagged = true) {
            "a"("href" to "/") { "svg"("aria-label" to "Home") { } }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs input
    }

    @Test
    fun `should leave a link containing a labelled image alone`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "a"("href" to "/") { "img"("src" to "/a.png", "alt" to "A cat") { } }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs input
    }

    @Test
    fun `should leave a link with no name and no graphic alone`() = runTest {
        // given - nothing to say about it; inventing a label would be worse
        // than leaving the empty link visible as-is.
        val input = semanticEvents(tagged = true) {
            "a"("href" to "/") { "span"("class" to "spacer") { } }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs input
    }

    @Test
    fun `should handle nested actionable elements and following content`() = runTest {
        // given - the buffer depth must come back down cleanly
        val input = semanticEvents(tagged = true) {
            "a"("href" to "/", "title" to "Home") { "svg" { } }
            "p" { +"after" }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs semanticEvents(tagged = true) {
            "a"("href" to "/", "title" to "Home") {
                +"Home"
                "svg" { }
            }
            "p" { +"after" }
        }
    }

    @Test
    fun `should ignore whitespace-only content when deciding emptiness`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "a"("href" to "/", "aria-label" to "Home") { +"  \n " }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs semanticEvents(tagged = true) {
            "a"("href" to "/", "aria-label" to "Home") {
                +"Home"
                +"  \n "
            }
        }
    }

    @Test
    fun `should leave a link whose graphic is named by a title child alone`() = runTest {
        // given - an <svg> can take its accessible name from a <title> child,
        // not only from an attribute; resolveInlineGraphics reads both, so this
        // link gets its label downstream and must not also get a placeholder.
        val input = semanticEvents(tagged = true) {
            "a"("href" to "/") {
                "svg" {
                    "title" { +"Home" }
                    "path"("d" to "M0 0") { }
                }
            }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs input
    }

    @Test
    fun `should not mark an unnamed image that survives on its own`() = runTest {
        // given - the Hacker News masthead: an <img> with no alt still renders
        // as `![](y18.svg)`, which already says a picture is there. A
        // placeholder beside it would say it twice.
        val input = semanticEvents(tagged = true) {
            "a"("href" to "https://news.ycombinator.com") {
                "img"("src" to "y18.svg") { }
            }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs input
    }

    @Test
    fun `should stream a labelled control past its first labelling child`() = runTest {
        // given - a link whose first child already settles that it needs no
        // label; every later event must flow through as it arrives, not wait
        // for the closing tag. Upstream counts what it has produced so far.
        var produced = 0
        val input = flow {
            semanticEvents(tagged = true) {
                "a"("href" to "/") {
                    +"Home"
                    "span" { +"›" }
                }
            }.collect { produced++; emit(it) }
        }

        // when - record, per arriving event, how far upstream had got
        val arrivals = mutableListOf<Int>()
        input.labelActionableElements().collect { arrivals.add(produced) }

        // then - the open tag is held only until the text decides, the rest
        // arrives one event behind upstream
        assert(arrivals == listOf(2, 2, 3, 4, 5, 6))
    }

    @Test
    fun `should hold an unlabelled control until its close`() = runTest {
        // given - nothing inside decides the question before the closing tag
        var produced = 0
        val input = flow {
            semanticEvents(tagged = true) {
                "a"("href" to "/", "aria-label" to "Home") { "svg" { } }
            }.collect { produced++; emit(it) }
        }

        // when
        val arrivals = mutableListOf<Int>()
        input.labelActionableElements().collect { arrivals.add(produced) }

        // then - open, inserted label, svg open, svg close, close: all at once
        assert(arrivals == listOf(4, 4, 4, 4, 4))
    }

    @Test
    fun `should label a link whose svg carries only a title attribute`() = runTest {
        // given - a `title` attribute names the graphic to a hover, but
        // resolveInlineGraphics reads only `aria-label` and a <title> child, so
        // downstream the svg vanishes and the link would be left empty unless
        // the name is lifted out here (rule 2 reads the same attribute).
        val input = semanticEvents(tagged = true) {
            "a"("href" to "/search") {
                "svg"("title" to "Search") { "path"("d" to "M0 0") { } }
            }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs semanticEvents(tagged = true) {
            "a"("href" to "/search") {
                +"Search"
                "svg"("title" to "Search") { "path"("d" to "M0 0") { } }
            }
        }
    }

    @Test
    fun `should label an unlabelled control nested inside a labelled one`() = runTest {
        // given - a card link whose text settles its own label, holding an
        // icon-only button whose name sits on a div simplifyHtml unwraps
        val input = semanticEvents(tagged = true) {
            "a"("href" to "/card") {
                +"Story title"
                "button" { "div"("title" to "Save") { } }
            }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs semanticEvents(tagged = true) {
            "a"("href" to "/card") {
                +"Story title"
                "button" {
                    +"Save"
                    "div"("title" to "Save") { }
                }
            }
        }
    }

    @Test
    fun `should let an inserted inner label settle the enclosing control`() = runTest {
        // given - neither control has a label of its own; the one inserted into
        // the button is content of the link too, so the link needs none
        val input = semanticEvents(tagged = true) {
            "a"("href" to "/card", "title" to "Open card") {
                "button" { "div"("title" to "Save") { } }
            }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs semanticEvents(tagged = true) {
            "a"("href" to "/card", "title" to "Open card") {
                "button" {
                    +"Save"
                    "div"("title" to "Save") { }
                }
            }
        }
    }

    @Test
    fun `should label both an unlabelled control and its unlabelled parent`() = runTest {
        // given - the inner control has nothing to say, the outer has a name
        val input = semanticEvents(tagged = true) {
            "a"("href" to "/card", "aria-label" to "Open card") {
                "button" { "span"("class" to "spacer") { } }
            }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs semanticEvents(tagged = true) {
            "a"("href" to "/card", "aria-label" to "Open card") {
                +"Open card"
                "button" { "span"("class" to "spacer") { } }
            }
        }
    }

    @Test
    fun `should name an empty heading from its own title`() = runTest {
        // given - a heading's name comes from its content, failing that from
        // its `title` (accname step 2I), which simplifyHtml strips
        val input = semanticEvents(tagged = true) {
            "h1"("title" to "Back to Bing search") { }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs semanticEvents(tagged = true) {
            "h1"("title" to "Back to Bing search") { +"Back to Bing search" }
        }
    }

    @Test
    fun `should leave a heading with content alone`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "h2"("title" to "Tooltip") { +"Visible heading" }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs input
    }

    @Test
    fun `should not mark a nameless graphic in an empty heading`() = runTest {
        // given - the placeholder says "a picture to click"; a heading is
        // not clicked, so a nameless graphic in it is left to vanish
        val input = semanticEvents(tagged = true) {
            "h1" { "svg" { "path"("d" to "M0 0") { } } }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs input
    }

    @Test
    fun `should name an element with a heading role only where it becomes a heading`() = runTest {
        // given - simplifyHtml promotes `role="heading"` only on a generic
        // container; on an `li` it stays a list item, so its tooltip is no name
        val input = semanticEvents(tagged = true) {
            "div"("role" to "heading", "title" to "Section") { }
            "ul" { "li"("role" to "heading", "title" to "Tooltip") { } }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div"("role" to "heading", "title" to "Section") { +"Section" }
            "ul" { "li"("role" to "heading", "title" to "Tooltip") { } }
        }
    }

    @Test
    fun `should label a link through the heading it wraps only once`() = runTest {
        // given - the Bing logo (issue #177): an empty link wrapping an empty
        // heading named only by its `title`, the picture a CSS background
        val input = semanticEvents(tagged = true) {
            "a"("href" to "/?FORM=Z9FD1") {
                "h1"("class" to "b_logo", "title" to "Back to Bing search") { }
            }
        }

        // when
        val output = input.labelActionableElements()

        // then - the name lands in the heading, which labels the link too
        output sameAs semanticEvents(tagged = true) {
            "a"("href" to "/?FORM=Z9FD1") {
                "h1"("class" to "b_logo", "title" to "Back to Bing search") {
                    +"Back to Bing search"
                }
            }
        }
    }

    @Test
    fun `should label an option named only by its own aria-label`() = runTest {
        // given - a combobox suggestion (issue #108) whose visible text sat in
        // an `aria-hidden` span that applyAccessibility has already dropped:
        // an `li` is no link or button, but its `role` says it is clicked
        val input = semanticEvents(tagged = true) {
            "li"("role" to "option", "aria-label" to "Berlin Hbf. ") { }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs semanticEvents(tagged = true) {
            "li"("role" to "option", "aria-label" to "Berlin Hbf. ") { +"Berlin Hbf." }
        }
    }

    @Test
    fun `should label an element with an interactive role from a descendant title`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "div"("role" to "menuitemcheckbox") {
                "span"("class" to "check", "title" to "Show hidden files") { }
            }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div"("role" to "menuitemcheckbox") {
                +"Show hidden files"
                "span"("class" to "check", "title" to "Show hidden files") { }
            }
        }
    }

    @Test
    fun `should label a ref-bearing generic element from its own aria-label`() = runTest {
        // given - the browser found it actionable (it carries a ref), though
        // nothing in the markup says what kind of control it is
        val input = semanticEvents(tagged = true) {
            "span"("aria-label" to "Delete entry", AccessibilityAnnotations.REF to "21") { }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs semanticEvents(tagged = true) {
            "span"("aria-label" to "Delete entry", AccessibilityAnnotations.REF to "21") {
                +"Delete entry"
            }
        }
    }

    @Test
    fun `should not label an element whose role does not take its name from content`() = runTest {
        // given - a listbox's name says what its options are for, it is not
        // one of them; written as content it would read as an option
        val input = semanticEvents(tagged = true) {
            "ul"("role" to "listbox", "aria-label" to "Stations", AccessibilityAnnotations.REF to "22") { }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs input
    }

    @Test
    fun `should not label a ref-bearing element whose content is its value`() = runTest {
        // given - a text field's content is what the user typed: its name
        // written there would read as a value
        val input = semanticEvents(tagged = true) {
            "textarea"("aria-label" to "Message", AccessibilityAnnotations.REF to "1") { }
            "div"("contenteditable" to "true", "aria-label" to "Body", AccessibilityAnnotations.REF to "2") { }
            "select"("aria-label" to "Country", AccessibilityAnnotations.REF to "3") { }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs input
    }

    @Test
    fun `should not label a ref-bearing void element`() = runTest {
        // given - an `input` has no content to hold a label
        val input = semanticEvents(tagged = true) {
            "input"("type" to "checkbox", "aria-label" to "Remember me", AccessibilityAnnotations.REF to "4") { }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs input
    }

    @Test
    fun `should not label an element without a ref or an interactive role`() = runTest {
        // given - a named region is not a control
        val input = semanticEvents(tagged = true) {
            "div"("aria-label" to "Sidebar") { }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs input
    }

    @Test
    fun `should not label a ref-bearing landmark or container`() = runTest {
        // given - a focusable `<main tabindex="-1">` carries a ref, but its
        // implicit role is not named from content: the input's name inside
        // must not be written into the landmark
        val input = semanticEvents(tagged = true) {
            "main"("tabindex" to "-1", AccessibilityAnnotations.REF to "1") {
                "input"("aria-label" to "Search", AccessibilityAnnotations.REF to "2") { }
            }
            "form"("title" to "Login", AccessibilityAnnotations.REF to "3") { }
            "section"("aria-label" to "News", AccessibilityAnnotations.REF to "4") { }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs input
    }

    @Test
    fun `should not label an a without href since it is no control`() = runTest {
        // given - an `<a>` with no `href` is a placeholder, not a link, and
        // simplifyHtml unwraps it: an inserted label would be left behind as
        // stray text or a `![:svg:]()` with nothing to act on.
        val input = semanticEvents(tagged = true) {
            "a"("title" to "Close") { "svg" { } }
            "a"("class" to "icon") { "svg" { } }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs input
    }

    @Test
    fun `should label an a without href that the capture found actionable`() = runTest {
        // given - a scripted placeholder the browser exposes as a control (it
        // carries a ref), which the pipeline keeps actionable
        val input = semanticEvents(tagged = true) {
            "a"("title" to "Close", AccessibilityAnnotations.REF to "4") { "svg" { } }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs semanticEvents(tagged = true) {
            "a"("title" to "Close", AccessibilityAnnotations.REF to "4") {
                +"Close"
                "svg" { }
            }
        }
    }

    @Test
    fun `should not label an a without href whose ref will be stripped`() = runTest {
        // given - under RefMode.STRIP simplifyHtml drops the ref and unwraps
        // the placeholder, which would leave an inserted label behind
        val input = semanticEvents(tagged = true) {
            "a"(AccessibilityAnnotations.REF to "6") { "svg" { } }
        }

        // when
        val output = input.labelActionableElements(refMode = RefMode.STRIP)

        // then
        output sameAs input
    }

    @Test
    fun `should not label an a without href even when its role says it is a control`() = runTest {
        // given - simplifyHtml unwraps a placeholder `<a>` whatever its role, so
        // without a kept ref it is no control and an inserted label would be
        // left behind as stray text
        val input = semanticEvents(tagged = true) {
            "a"("role" to "button", "aria-label" to "Close") { "svg" { } }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs input
    }

    @Test
    fun `should mark an unnamed image that vanishes for want of a src`() = runTest {
        // given - resolveLazyImages drops a nameless <img> whose only src is a
        // `data:` URI, so like a nameless <svg> it leaves the link empty
        val input = semanticEvents(tagged = true) {
            "a"("href" to "/x") { "img"("src" to "data:image/png;base64,iVBORw0KGgo=") { } }
            "a"("href" to "/y") { "img" { } }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs semanticEvents(tagged = true) {
            "a"("href" to "/x") {
                "img"("alt" to GRAPHIC_PLACEHOLDER_ALT) { }
                "img"("src" to "data:image/png;base64,iVBORw0KGgo=") { }
            }
            "a"("href" to "/y") {
                "img"("alt" to GRAPHIC_PLACEHOLDER_ALT) { }
                "img" { }
            }
        }
    }

    @Test
    fun `should not mark an unnamed lazy image that resolves to a real src`() = runTest {
        // given - the placeholder is replaced by the picture's source downstream
        val input = semanticEvents(tagged = true) {
            "a"("href" to "/x") {
                "picture" {
                    "source"("srcset" to "cat.webp") { }
                    "img"("src" to "data:image/gif;base64,R0lGODlhAQABAAAAACw=") { }
                }
            }
        }

        // when
        val output = input.labelActionableElements()

        // then
        output sameAs input
    }

}
