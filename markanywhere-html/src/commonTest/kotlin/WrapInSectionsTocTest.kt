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
 * [wrapInSections] with a positive `tocDepth` appends a `nav` with
 * `id="toc"` after the last section closes, linking every section whose
 * nesting level does not exceed the requested depth.
 */
class WrapInSectionsTocTest {

    @Test
    fun `should append a table of contents nav after the last section`() = runTest {
        // given
        val input = semanticEvents {
            "h2" { +"First" }
            "p" { +"First body." }
            "h2" { +"Second" }
            "p" { +"Second body." }
        }

        // when
        val output = input.wrapInSections(tocDepth = 1)

        // then
        output sameAs semanticEvents {
            "section"("id" to "first") {
                "h2" { +"First" }
                "p" { +"First body." }
            }
            "section"("id" to "second") {
                "h2" { +"Second" }
                "p" { +"Second body." }
            }
            "nav"("id" to "toc") {
                "ul" {
                    "li" {
                        "a"("href" to "#first") { +"First" }
                    }
                    "li" {
                        "a"("href" to "#second") { +"Second" }
                    }
                }
            }
        }
    }

    @Test
    fun `should nest toc list entries mirroring the section nesting`() = runTest {
        // given
        val input = semanticEvents {
            "h2" { +"A" }
            "h3" { +"A sub" }
            "p" { +"A sub body." }
            "h2" { +"B" }
        }

        // when
        val output = input.wrapInSections(tocDepth = 2)

        // then
        output sameAs semanticEvents {
            "section"("id" to "a") {
                "h2" { +"A" }
                "section"("id" to "a-sub") {
                    "h3" { +"A sub" }
                    "p" { +"A sub body." }
                }
            }
            "section"("id" to "b") {
                "h2" { +"B" }
            }
            "nav"("id" to "toc") {
                "ul" {
                    "li" {
                        "a"("href" to "#a") { +"A" }
                        "ul" {
                            "li" {
                                "a"("href" to "#a-sub") { +"A sub" }
                            }
                        }
                    }
                    "li" {
                        "a"("href" to "#b") { +"B" }
                    }
                }
            }
        }
    }

    @Test
    fun `should omit sections nested deeper than the toc depth`() = runTest {
        // given
        val input = semanticEvents {
            "h2" { +"A" }
            "h3" { +"A sub" }
            "h2" { +"B" }
        }

        // when
        val output = input.wrapInSections(tocDepth = 1)

        // then
        output sameAs semanticEvents {
            "section"("id" to "a") {
                "h2" { +"A" }
                "section"("id" to "a-sub") {
                    "h3" { +"A sub" }
                }
            }
            "section"("id" to "b") {
                "h2" { +"B" }
            }
            "nav"("id" to "toc") {
                "ul" {
                    "li" {
                        "a"("href" to "#a") { +"A" }
                    }
                    "li" {
                        "a"("href" to "#b") { +"B" }
                    }
                }
            }
        }
    }

    @Test
    fun `should link a section with a synthetic id from the toc`() = runTest {
        // given
        val input = semanticEvents {
            "h2" { +"First" }
            "h2" { +"!!!" }
            "h2" { +"Last" }
        }

        // when
        val output = input.wrapInSections(tocDepth = 1)

        // then
        output sameAs semanticEvents {
            "section"("id" to "first") {
                "h2" { +"First" }
            }
            "section"("id" to "section") {
                "h2" { +"!!!" }
            }
            "section"("id" to "last") {
                "h2" { +"Last" }
            }
            "nav"("id" to "toc") {
                "ul" {
                    "li" {
                        "a"("href" to "#first") { +"First" }
                    }
                    "li" {
                        "a"("href" to "#section") { +"!!!" }
                    }
                    "li" {
                        "a"("href" to "#last") { +"Last" }
                    }
                }
            }
        }
    }

    @Test
    fun `should dedup a section id colliding with the toc nav id`() = runTest {
        // given
        val input = semanticEvents {
            "h2" { +"TOC" }
            "h2" { +"Body" }
        }

        // when
        val output = input.wrapInSections(tocDepth = 1)

        // then
        output sameAs semanticEvents {
            "section"("id" to "toc-1") {
                "h2" { +"TOC" }
            }
            "section"("id" to "body") {
                "h2" { +"Body" }
            }
            "nav"("id" to "toc") {
                "ul" {
                    "li" {
                        "a"("href" to "#toc-1") { +"TOC" }
                    }
                    "li" {
                        "a"("href" to "#body") { +"Body" }
                    }
                }
            }
        }
    }

    @Test
    fun `should keep the toc id for a section when the toc nav is disabled`() = runTest {
        // given
        val input = semanticEvents {
            "h2" { +"TOC" }
        }

        // when
        val output = input.wrapInSections()

        // then
        output sameAs semanticEvents {
            "section"("id" to "toc") {
                "h2" { +"TOC" }
            }
        }
    }

    @Test
    fun `should step the toc list back multiple levels at once`() = runTest {
        // given
        val input = semanticEvents {
            "h2" { +"A" }
            "h3" { +"A sub" }
            "h4" { +"A sub sub" }
            "h2" { +"B" }
        }

        // when
        val output = input.wrapInSections(tocDepth = 3)

        // then
        output sameAs semanticEvents {
            "section"("id" to "a") {
                "h2" { +"A" }
                "section"("id" to "a-sub") {
                    "h3" { +"A sub" }
                    "section"("id" to "a-sub-sub") {
                        "h4" { +"A sub sub" }
                    }
                }
            }
            "section"("id" to "b") {
                "h2" { +"B" }
            }
            "nav"("id" to "toc") {
                "ul" {
                    "li" {
                        "a"("href" to "#a") { +"A" }
                        "ul" {
                            "li" {
                                "a"("href" to "#a-sub") { +"A sub" }
                                "ul" {
                                    "li" {
                                        "a"("href" to "#a-sub-sub") { +"A sub sub" }
                                    }
                                }
                            }
                        }
                    }
                    "li" {
                        "a"("href" to "#b") { +"B" }
                    }
                }
            }
        }
    }

    @Test
    fun `should not emit a toc nav when no section was created`() = runTest {
        // given
        val input = semanticEvents {
            "h1" { +"Title" }
            "p" { +"Body." }
        }

        // when
        val output = input.wrapInSections(tocDepth = 1)

        // then
        output sameAs semanticEvents {
            "h1" { +"Title" }
            "p" { +"Body." }
        }
    }

    @Test
    fun `should flatten nested heading marks into the toc entry label`() = runTest {
        // given
        val input = semanticEvents {
            "h2" {
                "em" { +"Fancy" }
                +" Title 2.0!"
            }
            "p" { +"Body." }
        }

        // when
        val output = input.wrapInSections(tocDepth = 1)

        // then
        output sameAs semanticEvents {
            "section"("id" to "fancy-title-20") {
                "h2" {
                    "em" { +"Fancy" }
                    +" Title 2.0!"
                }
                "p" { +"Body." }
            }
            "nav"("id" to "toc") {
                "ul" {
                    "li" {
                        "a"("href" to "#fancy-title-20") { +"Fancy Title 2.0!" }
                    }
                }
            }
        }
    }

    @Test
    fun `should mirror the tagged flag of the sections on the toc nav`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "h2" { +"Heading" }
            "p" { +"Body." }
        }

        // when
        val output = input.wrapInSections(tocDepth = 1)

        // then
        output sameAs semanticEvents(tagged = true) {
            "section"("id" to "heading") {
                "h2" { +"Heading" }
                "p" { +"Body." }
            }
            "nav"("id" to "toc") {
                "ul" {
                    "li" {
                        "a"("href" to "#heading") { +"Heading" }
                    }
                }
            }
        }
    }

}
