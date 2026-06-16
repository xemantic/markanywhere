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

import com.xemantic.markanywhere.dump.AccessibilityAnnotations
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ResolveInlineGraphicsTest {

    @Test
    fun `should resolve an svg with aria-label to an image`() = runTest {
        // given — the BBC News masthead: an inline logo SVG inside an <h1>
        val input = semanticEvents(tagged = true) {
            "h1" {
                "svg"("role" to "img", "aria-label" to "News") {
                    "title" { +"News" }
                    "path"("d" to "M16 26…") { }
                }
            }
        }

        // when
        val output = input.resolveInlineGraphics()

        // then — the whole svg subtree becomes a void img carrying the name
        output sameAs semanticEvents(tagged = true) {
            "h1" {
                "img"("alt" to "News") { }
            }
        }
    }

    @Test
    fun `should fall back to the svg title when there is no aria-label`() = runTest {
        // when — accessible name carried only by the <title> child
        val output = semanticEvents(tagged = true) {
            "svg"("role" to "img") {
                "title" { +"British Broadcasting Corporation" }
                "path"("d" to "M1 2…") { }
            }
        }.resolveInlineGraphics()

        // then
        output sameAs semanticEvents(tagged = true) {
            "img"("alt" to "British Broadcasting Corporation") { }
        }
    }

    @Test
    fun `should prefer aria-label over the title child`() = runTest {
        // when — both present, aria-label wins (it is the authored accessible name)
        val output = semanticEvents(tagged = true) {
            "svg"("aria-label" to "Search") {
                "title" { +"magnifying glass" }
            }
        }.resolveInlineGraphics()

        // then
        output sameAs semanticEvents(tagged = true) {
            "img"("alt" to "Search") { }
        }
    }

    @Test
    fun `should drop a decorative svg with no accessible name`() = runTest {
        // given — an icon-only SVG inside a control whose label sits on the button
        val input = semanticEvents(tagged = true) {
            "button"("aria-label" to "Follow BBC on x") {
                "svg" {
                    "path"("d" to "M1 2…") { }
                }
            }
        }

        // when
        val output = input.resolveInlineGraphics()

        // then — the empty graphic vanishes, the labelled control survives
        output sameAs semanticEvents(tagged = true) {
            "button"("aria-label" to "Follow BBC on x") { }
        }
    }

    @Test
    fun `should treat a blank aria-label as no name and fall back to title`() = runTest {
        // when — a whitespace-only aria-label must not become the alt text
        val output = semanticEvents(tagged = true) {
            "svg"("aria-label" to "   ") {
                "title" { +"  Logo  " }
            }
        }.resolveInlineGraphics()

        // then — the trimmed title is used
        output sameAs semanticEvents(tagged = true) {
            "img"("alt" to "Logo") { }
        }
    }

    @Test
    fun `should carry over the svg id and display annotation`() = runTest {
        // when — faithfulness attributes survive onto the synthetic img
        val output = semanticEvents(tagged = true) {
            "svg"(
                "aria-label" to "News",
                "id" to "masthead",
                AccessibilityAnnotations.DISPLAY to "inline-block",
            ) { }
        }.resolveInlineGraphics()

        // then
        output sameAs semanticEvents(tagged = true) {
            "img"(
                "alt" to "News",
                "id" to "masthead",
                AccessibilityAnnotations.DISPLAY to "inline-block",
            ) { }
        }
    }

    @Test
    fun `should leave non-svg elements untouched`() = runTest {
        // given — a real <img> and surrounding content must pass through verbatim
        val input = semanticEvents(tagged = true) {
            "p" {
                +"see "
                "img"("src" to "/a.png", "alt" to "A") { }
            }
        }

        // when
        val output = input.resolveInlineGraphics()

        // then
        output sameAs input
    }
}
