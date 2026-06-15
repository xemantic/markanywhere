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

class ApplyAccessibilityTest {

    @Test
    fun `should drop a subtree hidden via display none`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "div" {
                "span"(AccessibilityAnnotations.DISPLAY to "none") { +"hidden" }
                "p" { +"visible" }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div" {
                "p" { +"visible" }
            }
        }
    }

    @Test
    fun `should drop a subtree hidden via visibility hidden`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "div" {
                "p"(AccessibilityAnnotations.VISIBILITY to "hidden") { +"gone" }
                "p" { +"kept" }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div" {
                "p" { +"kept" }
            }
        }
    }

    @Test
    fun `should drop a subtree hidden via aria-hidden`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "div" {
                "i"("aria-hidden" to "true", "class" to "icon") { }
                +"text"
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div" {
                +"text"
            }
        }
    }

    @Test
    fun `should unwrap a layout table promoting its content`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "div" {
                "table"(AccessibilityAnnotations.ROLE to "LayoutTable") {
                    "tbody" {
                        "tr" {
                            "td" { "p" { +"Hello" } }
                            "td" {
                                "span"(AccessibilityAnnotations.DISPLAY to "none") { +"secret" }
                                +"World"
                            }
                        }
                    }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then the table / tbody / tr / td skeleton is gone, the hidden span is
        // dropped, and the real content is promoted into the surrounding div
        output sameAs semanticEvents(tagged = true) {
            "div" {
                "p" { +"Hello" }
                +"World"
            }
        }
    }

    @Test
    fun `should keep a data table intact and strip its role annotation`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "table"(AccessibilityAnnotations.ROLE to "table") {
                "tbody" {
                    "tr" {
                        "td" { +"A" }
                        "td" { +"B" }
                    }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "table" {
                "tbody" {
                    "tr" {
                        "td" { +"A" }
                        "td" { +"B" }
                    }
                }
            }
        }
    }

    @Test
    fun `should re-evaluate a data table nested inside a layout table`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "table"(AccessibilityAnnotations.ROLE to "LayoutTable") {
                "tr" {
                    "td" {
                        "table"(AccessibilityAnnotations.ROLE to "table") {
                            "tr" { "td" { +"data" } }
                        }
                    }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then the outer layout table is unwrapped, but the inner data table
        // survives — re-evaluated on its own role
        output sameAs semanticEvents(tagged = true) {
            "table" {
                "tr" { "td" { +"data" } }
            }
        }
    }

    @Test
    fun `should leave a table without a role annotation intact`() = runTest {
        // given a table with no role annotation (e.g. captured with annotate=false)
        val input = semanticEvents(tagged = true) {
            "table" {
                "tr" { "td" { +"cell" } }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "table" {
                "tr" { "td" { +"cell" } }
            }
        }
    }

}