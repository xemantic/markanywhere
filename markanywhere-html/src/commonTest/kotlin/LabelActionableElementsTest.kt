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
}
