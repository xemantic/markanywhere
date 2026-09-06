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
        // dropped, and the real content is promoted into the surrounding div —
        // each cell followed by a separating space so adjacent cells don't merge
        output sameAs semanticEvents(tagged = true) {
            "div" {
                "p" { +"Hello" }
                +" "
                +"World"
                +" "
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
        // survives — re-evaluated on its own role; the unwrapped outer cell adds
        // its trailing separator space
        output sameAs semanticEvents(tagged = true) {
            "table" {
                "tr" { "td" { +"data" } }
            }
            +" "
        }
    }

    @Test
    fun `should separate adjacent layout table cells with a space`() = runTest {
        // given two cells with inline content and no inter-cell whitespace
        // (as in real minified markup like Hacker News' nav)
        val input = semanticEvents(tagged = true) {
            "table"(AccessibilityAnnotations.ROLE to "LayoutTable") {
                "tr" {
                    "td" { "a"("href" to "/1") { +"A" } }
                    "td" { "a"("href" to "/2") { +"B" } }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then the two links are separated by a space instead of merging
        output sameAs semanticEvents(tagged = true) {
            "a"("href" to "/1") { +"A" }
            +" "
            "a"("href" to "/2") { +"B" }
            +" "
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

    @Test
    fun `should drop an image Blink marks accessibility-ignored`() = runTest {
        // given — a decorative placeholder image flagged ignored by the browser,
        // next to a real image that is kept.
        val input = semanticEvents(tagged = true) {
            "div" {
                "img"("src" to "/placeholder.png", AccessibilityAnnotations.IGNORED to "true") {}
                "img"("src" to "/real.jpg", "alt" to "Photo") {}
            }
        }

        // when
        val output = input.applyAccessibility()

        // then — the ignored image is dropped, the real one passes unchanged
        output sameAs semanticEvents(tagged = true) {
            "div" {
                "img"("src" to "/real.jpg", "alt" to "Photo") {}
            }
        }
    }

    @Test
    fun `should strip the ignored annotation from a surviving image`() = runTest {
        // given — defensive: a non-decorative image must not leak the annotation.
        val input = semanticEvents(tagged = true) {
            "img"("src" to "/real.jpg", "alt" to "Photo", AccessibilityAnnotations.IGNORED to "false") {}
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "img"("src" to "/real.jpg", "alt" to "Photo") {}
        }
    }

    @Test
    fun `should keep select options the browser gives no layout box`() = runTest {
        // given — a closed <select>: Blink lays out no box for the popup options,
        // which the capture cannot tell apart from `display:none`.
        val input = semanticEvents(tagged = true) {
            "select"("id" to "docselect", AccessibilityAnnotations.DISPLAY to "inline-block") {
                "option"("value" to "", AccessibilityAnnotations.DISPLAY to "none") { +"address" }
                "option"("value" to "file", AccessibilityAnnotations.DISPLAY to "none") { +"file upload" }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then — the options survive, and the meaningless display verdict is
        // dropped rather than passed on to the whitespace normalizer.
        output sameAs semanticEvents(tagged = true) {
            "select"("id" to "docselect", AccessibilityAnnotations.DISPLAY to "inline-block") {
                "option"("value" to "") { +"address" }
                "option"("value" to "file") { +"file upload" }
            }
        }
    }

    @Test
    fun `should keep optgroups the browser gives no layout box`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "select" {
                "optgroup"("label" to "Fruit", AccessibilityAnnotations.DISPLAY to "none") {
                    "option"("value" to "1", AccessibilityAnnotations.DISPLAY to "none") { +"apple" }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "select" {
                "optgroup"("label" to "Fruit") {
                    "option"("value" to "1") { +"apple" }
                }
            }
        }
    }

    @Test
    fun `should drop an option hidden by an explicit accessibility verdict`() = runTest {
        // given — `aria-hidden` / `visibility:hidden` are author signals, not the
        // missing-layout-box artifact, so they still hide the option.
        val input = semanticEvents(tagged = true) {
            "select" {
                "option"("value" to "1", "aria-hidden" to "true") { +"aria hidden" }
                "option"("value" to "2", AccessibilityAnnotations.VISIBILITY to "hidden") { +"invisible" }
                "option"("value" to "3", AccessibilityAnnotations.DISPLAY to "none") { +"kept" }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "select" {
                "option"("value" to "3") { +"kept" }
            }
        }
    }

    @Test
    fun `should drop the options of a hidden select`() = runTest {
        // given — the exemption is per element: a hidden ancestor still takes the
        // whole subtree with it.
        val input = semanticEvents(tagged = true) {
            "div" {
                "select"("id" to "gone", AccessibilityAnnotations.DISPLAY to "none") {
                    "option"("value" to "1", AccessibilityAnnotations.DISPLAY to "none") { +"nope" }
                }
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
    fun `should keep options wrapped in a formatting div inside a select`() = runTest {
        // given — modern Chrome keeps a <div> between <select> and <option> (the
        // customizable-select content model allows it, and a JS-built DOM can
        // nest anything): the wrapper has no layout box either, so it carries the
        // same meaningless `none`.
        val input = semanticEvents(tagged = true) {
            "select"("id" to "country", AccessibilityAnnotations.DISPLAY to "inline-block") {
                "div"("class" to "popup", AccessibilityAnnotations.DISPLAY to "none") {
                    "option"("value" to "pl", AccessibilityAnnotations.DISPLAY to "none") { +"Poland" }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then — the wrapper survives (simplifyHtml unwraps it later) and takes
        // the options with it
        output sameAs semanticEvents(tagged = true) {
            "select"("id" to "country", AccessibilityAnnotations.DISPLAY to "inline-block") {
                "div"("class" to "popup") {
                    "option"("value" to "pl") { +"Poland" }
                }
            }
        }
    }

    @Test
    fun `should keep inline markup inside an option`() = runTest {
        // given — an option's own descendants have no layout box either, so
        // exempting only the <option> would still strip its markup
        val input = semanticEvents(tagged = true) {
            "select" {
                "option"("value" to "e", AccessibilityAnnotations.DISPLAY to "none") {
                    "span"("class" to "flag", AccessibilityAnnotations.DISPLAY to "none") { +"E" }
                    +" Epsilon"
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "select" {
                "option"("value" to "e") {
                    "span"("class" to "flag") { +"E" }
                    +" Epsilon"
                }
            }
        }
    }

    @Test
    fun `should keep a genuine display verdict inside a select`() = runTest {
        // given — a customizable select renders its <button> face, so that
        // subtree's display verdicts are real and must survive untouched.
        val input = semanticEvents(tagged = true) {
            "select"(AccessibilityAnnotations.DISPLAY to "inline-flex") {
                "button" {
                    "span"("class" to "flag", AccessibilityAnnotations.DISPLAY to "inline") { +"E" }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "select"(AccessibilityAnnotations.DISPLAY to "inline-flex") {
                "button" {
                    "span"("class" to "flag", AccessibilityAnnotations.DISPLAY to "inline") { +"E" }
                }
            }
        }
    }

    @Test
    fun `should still drop a script inside a select`() = runTest {
        // given — a script is a legal child of <select>, and its `display:none`
        // is real: the exemption must not leak source code into the output.
        val input = semanticEvents(tagged = true) {
            "select" {
                "script"(AccessibilityAnnotations.DISPLAY to "none") { +"alert('x')" }
                "option"("value" to "a", AccessibilityAnnotations.DISPLAY to "none") { +"Alpha" }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "select" {
                "option"("value" to "a") { +"Alpha" }
            }
        }
    }

    @Test
    fun `should keep select options inside an unwrapped layout table`() = runTest {
        // given — a form laid out in a layout table: the select is reached in
        // layout-table mode, which must not bypass the select exemption.
        val input = semanticEvents(tagged = true) {
            "table"(AccessibilityAnnotations.ROLE to "none") {
                "tr" {
                    "td" {
                        "select"("id" to "country", AccessibilityAnnotations.DISPLAY to "inline-block") {
                            "option"("value" to "pl", AccessibilityAnnotations.DISPLAY to "none") { +"Poland" }
                        }
                    }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "select"("id" to "country", AccessibilityAnnotations.DISPLAY to "inline-block") {
                "option"("value" to "pl") { +"Poland" }
            }
            +" "
        }
    }

    @Test
    fun `should drop a decorative image inside a select`() = runTest {
        // given — a flag icon Blink kept out of the accessibility tree, sitting
        // in the popup half of the select where the display verdict is unusable.
        val input = semanticEvents(tagged = true) {
            "select" {
                "option"("value" to "pl", AccessibilityAnnotations.DISPLAY to "none") {
                    "img"(
                        "src" to "/flag.png",
                        "alt" to "",
                        AccessibilityAnnotations.IGNORED to "true",
                        AccessibilityAnnotations.DISPLAY to "none"
                    ) {}
                    +"Poland"
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "select" {
                "option"("value" to "pl") { +"Poland" }
            }
        }
    }

    @Test
    fun `should drop hidden content in the rendered face of a select`() = runTest {
        // given — a customizable select's <button> face is laid out, so a
        // `display:none` below it is a real author verdict again, not the
        // missing-layout-box artifact.
        val input = semanticEvents(tagged = true) {
            "select"(AccessibilityAnnotations.DISPLAY to "inline-flex") {
                "button"(AccessibilityAnnotations.DISPLAY to "inline-flex") {
                    "span"(AccessibilityAnnotations.DISPLAY to "none") { +"secret" }
                    "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"visible" }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "select"(AccessibilityAnnotations.DISPLAY to "inline-flex") {
                "button"(AccessibilityAnnotations.DISPLAY to "inline-flex") {
                    "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"visible" }
                }
            }
        }
    }

    @Test
    fun `should drop hidden content inside a laid-out listbox option`() = runTest {
        // given — a listbox (<select size>) lays its options out, so each option
        // computes a plain `display:block` for which the capture records no
        // annotation at all; a `display:none` below it is a real author verdict,
        // not the popup's missing-layout-box artifact.
        val input = semanticEvents(tagged = true) {
            "select"("size" to "4", AccessibilityAnnotations.DISPLAY to "inline-block") {
                "option"("value" to "pl") {
                    "span"(AccessibilityAnnotations.DISPLAY to "none") { +"internal note" }
                    +"Poland"
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "select"("size" to "4", AccessibilityAnnotations.DISPLAY to "inline-block") {
                "option"("value" to "pl") { +"Poland" }
            }
        }
    }

    @Test
    fun `should drop hidden content in a select face carrying no display annotation`() = runTest {
        // given — the same, one level up: a customizable select's <button> face
        // styled plain `display:block` carries no annotation either, and must
        // still end the artifact.
        val input = semanticEvents(tagged = true) {
            "select"(AccessibilityAnnotations.DISPLAY to "inline-flex") {
                "button" {
                    "span"(AccessibilityAnnotations.DISPLAY to "none") { +"secret" }
                    "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"visible" }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "select"(AccessibilityAnnotations.DISPLAY to "inline-flex") {
                "button" {
                    "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"visible" }
                }
            }
        }
    }

    @Test
    fun `should keep select options laid out in a layout table in the popup`() = runTest {
        // given — a JS-built select whose popup arranges its options in a layout
        // table: everything below the <select> carries the artifact `none`, so
        // unwrapping the table must not start honouring it again.
        val input = semanticEvents(tagged = true) {
            "select"("id" to "country", AccessibilityAnnotations.DISPLAY to "inline-block") {
                "table"(
                    AccessibilityAnnotations.ROLE to "none",
                    AccessibilityAnnotations.DISPLAY to "none"
                ) {
                    "tr"(AccessibilityAnnotations.DISPLAY to "none") {
                        "td"(AccessibilityAnnotations.DISPLAY to "none") {
                            "option"("value" to "pl", AccessibilityAnnotations.DISPLAY to "none") {
                                +"Poland"
                            }
                        }
                    }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "select"("id" to "country", AccessibilityAnnotations.DISPLAY to "inline-block") {
                "option"("value" to "pl") { +"Poland" }
                +" "
            }
        }
    }

    @Test
    fun `should drop a script inside a select carrying no display annotation`() = runTest {
        // given — a hand-built or non-Chrome-sourced stream carries no display
        // annotation, but a script is never rendered content in any case.
        val input = semanticEvents(tagged = true) {
            "select" {
                "script" { +"alert('x')" }
                "option"("value" to "a", AccessibilityAnnotations.DISPLAY to "none") { +"Alpha" }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "select" {
                "option"("value" to "a") { +"Alpha" }
            }
        }
    }

    @Test
    fun `should drop a never rendered element carrying no display annotation`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "div" {
                "style" { +"body { color: red }" }
                "noscript" { +"enable JavaScript" }
                "template" { +"boilerplate" }
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

}
