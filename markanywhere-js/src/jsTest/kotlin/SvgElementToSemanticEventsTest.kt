/*
 * Copyright 2025 Kazimierz Pogoda / Xemantic
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

import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.js.toSemanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.browser.document
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SvgElementToSemanticEventsTest {

    @Test
    fun `should preserve camelCase SVG element names for linearGradient and clipPath`() = runTest {
        // given
        document.body!!.innerHTML = """
            <svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg"><defs><linearGradient id="grad1" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="100" y2="0"><stop offset="0%" stop-color="red"/><stop offset="100%" stop-color="blue"/></linearGradient><clipPath id="clip1"><rect x="10" y="10" width="80" height="80"/></clipPath></defs><rect width="100" height="100" fill="url(#grad1)" clip-path="url(#clip1)"/></svg>
        """.trimIndent()

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "svg"("viewBox" to "0 0 100 100", "xmlns" to "http://www.w3.org/2000/svg") {
                    "defs" {
                        "linearGradient"(
                            "id" to "grad1",
                            "gradientUnits" to "userSpaceOnUse",
                            "x1" to "0",
                            "y1" to "0",
                            "x2" to "100",
                            "y2" to "0"
                        ) {
                            "stop"("offset" to "0%", "stop-color" to "red") {}
                            "stop"("offset" to "100%", "stop-color" to "blue") {}
                        }
                        "clipPath"("id" to "clip1") {
                            "rect"("x" to "10", "y" to "10", "width" to "80", "height" to "80") {}
                        }
                    }
                    "rect"(
                        "width" to "100",
                        "height" to "100",
                        "fill" to "url(#grad1)",
                        "clip-path" to "url(#clip1)"
                    ) {}
                }
            }
        }
    }

    @Test
    fun `should preserve camelCase SVG filter element names`() = runTest {
        // given
        document.body!!.innerHTML = """
            <svg xmlns="http://www.w3.org/2000/svg"><defs><filter id="shadow"><feGaussianBlur in="SourceAlpha" stdDeviation="3"/><feOffset dx="2" dy="2"/><feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge></filter></defs><circle cx="50" cy="50" r="40" filter="url(#shadow)"/></svg>
        """.trimIndent()

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "svg"("xmlns" to "http://www.w3.org/2000/svg") {
                    "defs" {
                        "filter"("id" to "shadow") {
                            "feGaussianBlur"("in" to "SourceAlpha", "stdDeviation" to "3") {}
                            "feOffset"("dx" to "2", "dy" to "2") {}
                            "feMerge" {
                                "feMergeNode" {}
                                "feMergeNode"("in" to "SourceGraphic") {}
                            }
                        }
                    }
                    "circle"("cx" to "50", "cy" to "50", "r" to "40", "filter" to "url(#shadow)") {}
                }
            }
        }
    }

    @Test
    fun `should preserve camelCase SVG textPath element name`() = runTest {
        // given
        document.body!!.innerHTML = """
            <svg xmlns="http://www.w3.org/2000/svg"><defs><path id="curve" d="M10,80 Q95,10 180,80"/></defs><text><textPath href="#curve">Text along a path</textPath></text></svg>
        """.trimIndent()

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "svg"("xmlns" to "http://www.w3.org/2000/svg") {
                    "defs" {
                        "path"("id" to "curve", "d" to "M10,80 Q95,10 180,80") {}
                    }
                    "text" {
                        "textPath"("href" to "#curve") {
                            +"Text along a path"
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `should preserve camelCase SVG foreignObject element name`() = runTest {
        // given
        document.body!!.innerHTML = """
            <svg xmlns="http://www.w3.org/2000/svg" width="200" height="200"><foreignObject x="10" y="10" width="180" height="180"><p>HTML inside SVG</p></foreignObject></svg>
        """.trimIndent()

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "svg"("xmlns" to "http://www.w3.org/2000/svg", "width" to "200", "height" to "200") {
                    "foreignObject"("x" to "10", "y" to "10", "width" to "180", "height" to "180") {
                        "p" {
                            +"HTML inside SVG"
                        }
                    }
                }
            }
        }
    }

}