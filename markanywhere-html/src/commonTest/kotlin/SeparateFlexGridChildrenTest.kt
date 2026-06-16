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

class SeparateFlexGridChildrenTest {

    @Test
    fun `should separate flex children that share no source whitespace`() = runTest {
        // given — the BBC card-metadata shape: two spans in a flex container with
        // an empty CSS-drawn separator div between them and no whitespace text.
        val input = semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "flex") {
                "span" { +"3 hrs ago" }
                "div"("data-testid" to "card-metadata-separator") { }
                "span" { +"Europe" }
            }
        }

        // when
        val output = input.separateFlexGridChildren()

        // then — one space separates the two inline spans; the empty divider
        // div is transparent (neither separated around nor breaking the run)
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
    fun `should not separate flex children that flatten to block elements`() = runTest {
        // given — the BBC header shape: a flex container whose children each wrap
        // a block-level <button>; they get block separation already, so injecting
        // a space would leave a stray whitespace line between them.
        val input = semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "flex") {
                "a"("href" to "/register") { "button" { +"Register" } }
                "a"("href" to "/signin") { "button" { +"Sign In" } }
            }
        }

        // when
        val output = input.separateFlexGridChildren()

        // then — nothing injected: a <button> child renders as a block
        output sameAs semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "flex") {
                "a"("href" to "/register") { "button" { +"Register" } }
                "a"("href" to "/signin") { "button" { +"Sign In" } }
            }
        }
    }

    @Test
    fun `should separate grid children`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "grid") {
                "span" { +"A" }
                "span" { +"B" }
            }
        }

        // when
        val output = input.separateFlexGridChildren()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "grid") {
                "span" { +"A" }
                +" "
                "span" { +"B" }
            }
        }
    }

    @Test
    fun `should separate flex children nested inside a grid container independently`() = runTest {
        // given — a flex metadata strip inside a grid card: only the grid is the
        // outermost flex/grid container, yet the inner flex children must also be
        // separated.
        val input = semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "grid") {
                "div"(AccessibilityAnnotations.DISPLAY to "flex") {
                    "span" { +"3 hrs ago" }
                    "span" { +"Europe" }
                }
            }
        }

        // when
        val output = input.separateFlexGridChildren()

        // then — the grid has a single child (no separator), the inner flex has two
        output sameAs semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "grid") {
                "div"(AccessibilityAnnotations.DISPLAY to "flex") {
                    "span" { +"3 hrs ago" }
                    +" "
                    "span" { +"Europe" }
                }
            }
        }
    }

    @Test
    fun `should not touch children of a non-flex container`() = runTest {
        // given — an ordinary block container: existing whitespace rules apply,
        // nothing is injected.
        val input = semanticEvents(tagged = true) {
            "div" {
                "span" { +"A" }
                "span" { +"B" }
            }
        }

        // when
        val output = input.separateFlexGridChildren()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div" {
                "span" { +"A" }
                "span" { +"B" }
            }
        }
    }
}
