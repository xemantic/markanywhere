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

        // then — a space is injected before the second box's content
        output sameAs semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "flex") {
                "span" { +"3 hrs ago" }
                "div"("data-testid" to "card-metadata-separator") { }
                "span" {
                    +" "
                    +"Europe"
                }
            }
        }
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
                "div"(AccessibilityAnnotations.DISPLAY to "grid") {
                    "span" {
                        +" "
                        +"LIVE"
                    }
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
}
