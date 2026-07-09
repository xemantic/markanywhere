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

import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * [wrapInSections] groups a section-starting mark (e.g. `h2`) and all of its
 * following siblings into a synthetic `section` mark, closing the section at
 * the next section-starting sibling, at the close of the enclosing container,
 * or at the end of the stream.
 */
class WrapInSectionsTest {

    @Test
    fun `should wrap heading and following siblings in a section until the next heading`() = runTest {
        // given
        val input = semanticEvents {
            "h1" { +"Title" }
            "p" { +"Intro." }
            "h2" { +"First" }
            "p" { +"First body." }
            "h2" { +"Second" }
            "p" { +"Second body." }
        }

        // when
        val output = input.wrapInSections("h2")

        // then
        output sameAs semanticEvents {
            "h1" { +"Title" }
            "p" { +"Intro." }
            "section"("id" to "first") {
                "h2" { +"First" }
                "p" { +"First body." }
            }
            "section"("id" to "second") {
                "h2" { +"Second" }
                "p" { +"Second body." }
            }
        }
    }

    @Test
    fun `should close an open section before the enclosing container closes`() = runTest {
        // given
        val input = semanticEvents {
            "body" {
                "h2" { +"Heading" }
                "p" { +"Body." }
            }
            "footer" { +"Footer." }
        }

        // when
        val output = input.wrapInSections("h2")

        // then
        output sameAs semanticEvents {
            "body" {
                "section"("id" to "heading") {
                    "h2" { +"Heading" }
                    "p" { +"Body." }
                }
            }
            "footer" { +"Footer." }
        }
    }

    @Test
    fun `should keep nested containers inside the section`() = runTest {
        // given
        val input = semanticEvents {
            "h2" { +"Heading" }
            "div" {
                "p" { +"Nested." }
            }
            "p" { +"Sibling." }
        }

        // when
        val output = input.wrapInSections("h2")

        // then
        output sameAs semanticEvents {
            "section"("id" to "heading") {
                "h2" { +"Heading" }
                "div" {
                    "p" { +"Nested." }
                }
                "p" { +"Sibling." }
            }
        }
    }

    @Test
    fun `should start a nested section for a matching mark at a deeper level`() = runTest {
        // given
        val input = semanticEvents {
            "h2" { +"Outer" }
            "div" {
                "h2" { +"Inner" }
                "p" { +"Inner body." }
            }
            "p" { +"Outer body." }
        }

        // when
        val output = input.wrapInSections("h2")

        // then
        output sameAs semanticEvents {
            "section"("id" to "outer") {
                "h2" { +"Outer" }
                "div" {
                    "section"("id" to "inner") {
                        "h2" { +"Inner" }
                        "p" { +"Inner body." }
                    }
                }
                "p" { +"Outer body." }
            }
        }
    }

    @Test
    fun `should forward a stream without matching marks verbatim`() = runTest {
        // given
        val input = semanticEvents {
            "h1" { +"Title" }
            "p" { +"Body." }
        }

        // when
        val output = input.wrapInSections("h2")

        // then
        output sameAs semanticEvents {
            "h1" { +"Title" }
            "p" { +"Body." }
        }
    }

    @Test
    fun `should match section starts with a custom mark matcher`() = runTest {
        // given
        val input = semanticEvents {
            "h2"("class" to "chapter") { +"One" }
            "p" { +"One body." }
            "h2" { +"Plain heading" }
            "h2"("class" to "chapter") { +"Two" }
            "p" { +"Two body." }
        }

        // when
        val output = input.wrapInSections { mark ->
            mark.name == "h2" && mark["class"] == "chapter"
        }

        // then
        output sameAs semanticEvents {
            "section"("id" to "one") {
                "h2"("class" to "chapter") { +"One" }
                "p" { +"One body." }
                "h2" { +"Plain heading" }
            }
            "section"("id" to "two") {
                "h2"("class" to "chapter") { +"Two" }
                "p" { +"Two body." }
            }
        }
    }

    @Test
    fun `should strip tags and non-anchor characters when deriving the section id`() = runTest {
        // given
        val input = semanticEvents {
            "h2" {
                "em" { +"Fancy" }
                +" Title 2.0!"
            }
            "p" { +"Body." }
        }

        // when
        val output = input.wrapInSections("h2")

        // then
        output sameAs semanticEvents {
            "section"("id" to "fancy-title-20") {
                "h2" {
                    "em" { +"Fancy" }
                    +" Title 2.0!"
                }
                "p" { +"Body." }
            }
        }
    }

    @Test
    fun `should deduplicate repeated section ids`() = runTest {
        // given
        val input = semanticEvents {
            "h2" { +"Same" }
            "p" { +"One." }
            "h2" { +"Same" }
            "p" { +"Two." }
            "h2" { +"Same" }
            "p" { +"Three." }
        }

        // when
        val output = input.wrapInSections("h2")

        // then
        output sameAs semanticEvents {
            "section"("id" to "same") {
                "h2" { +"Same" }
                "p" { +"One." }
            }
            "section"("id" to "same-1") {
                "h2" { +"Same" }
                "p" { +"Two." }
            }
            "section"("id" to "same-2") {
                "h2" { +"Same" }
                "p" { +"Three." }
            }
        }
    }

    @Test
    fun `should omit the id when the heading yields no anchor characters`() = runTest {
        // given
        val input = semanticEvents {
            "h2" { +"!!!" }
            "p" { +"Body." }
        }

        // when
        val output = input.wrapInSections("h2")

        // then
        output sameAs semanticEvents {
            "section" {
                "h2" { +"!!!" }
                "p" { +"Body." }
            }
        }
    }

    @Test
    fun `should mirror the tagged flag of the matched mark on the section`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "h2" { +"Heading" }
            "p" { +"Body." }
        }

        // when
        val output = input.wrapInSections("h2")

        // then
        output sameAs semanticEvents(tagged = true) {
            "section"("id" to "heading") {
                "h2" { +"Heading" }
                "p" { +"Body." }
            }
        }
    }

}