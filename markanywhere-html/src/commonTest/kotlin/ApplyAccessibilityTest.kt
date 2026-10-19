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
    fun `should keep a visible descendant of a visibility hidden element`() = runTest {
        // given — Gmail's compose dialog: the host is `visibility:hidden`, the
        // dialog sets `visibility:visible` again, so the capture annotates the
        // host and its inheriting wrapper but not the dialog.
        val input = semanticEvents(tagged = true) {
            "div"("class" to "host", AccessibilityAnnotations.VISIBILITY to "hidden") {
                +"host text"
                "div"("class" to "wrapper", AccessibilityAnnotations.VISIBILITY to "hidden") {
                    "div"(
                        "role" to "dialog",
                        "aria-label" to "New Message",
                        AccessibilityAnnotations.DISPLAY to "block"
                    ) {
                        "input"("aria-label" to "Subject", AccessibilityAnnotations.DISPLAY to "inline-block") { }
                        +"shown"
                    }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then — the invisible boxes still lay out around what is shown, so
        // they stay; only their own text goes.
        output sameAs semanticEvents(tagged = true) {
            "div"("class" to "host") {
                "div"("class" to "wrapper") {
                    "div"(
                        "role" to "dialog",
                        "aria-label" to "New Message",
                        AccessibilityAnnotations.DISPLAY to "block"
                    ) {
                        "input"("aria-label" to "Subject", AccessibilityAnnotations.DISPLAY to "inline-block") { }
                        +"shown"
                    }
                }
            }
        }
    }

    @Test
    fun `should drop descendants inheriting visibility hidden`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "div" {
                "div"(AccessibilityAnnotations.VISIBILITY to "hidden") {
                    "p"(AccessibilityAnnotations.VISIBILITY to "hidden") { +"gone" }
                    "button"(AccessibilityAnnotations.VISIBILITY to "hidden") { +"gone too" }
                }
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
    fun `should drop a hidden subtree below a visible descendant of an invisible element`() = runTest {
        // given — leaving the invisible ancestry restores every other verdict.
        val input = semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.VISIBILITY to "hidden") {
                "section"(AccessibilityAnnotations.DISPLAY to "block") {
                    "p"(AccessibilityAnnotations.DISPLAY to "none") { +"gone" }
                    "p"(AccessibilityAnnotations.DISPLAY to "block") { +"kept" }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div" {
                "section"(AccessibilityAnnotations.DISPLAY to "block") {
                    "p"(AccessibilityAnnotations.DISPLAY to "block") { +"kept" }
                }
            }
        }
    }

    @Test
    fun `should keep an invisible link whose label is visible`() = runTest {
        // given — clicking the visible label follows the invisible link, so
        // the href and the ref must survive with it.
        val input = semanticEvents(tagged = true) {
            "a"(
                "href" to "/compose",
                AccessibilityAnnotations.REF to "42",
                AccessibilityAnnotations.VISIBILITY to "hidden"
            ) {
                "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"Compose" }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "a"("href" to "/compose", AccessibilityAnnotations.REF to "42") {
                "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"Compose" }
            }
        }
    }

    @Test
    fun `should keep the structure of an invisible data table around a visible cell`() = runTest {
        // given — an invisible cell beside the visible one keeps its column.
        val input = semanticEvents(tagged = true) {
            "table"(
                AccessibilityAnnotations.ROLE to "table",
                AccessibilityAnnotations.VISIBILITY to "hidden"
            ) {
                "tr"(AccessibilityAnnotations.VISIBILITY to "hidden") {
                    "td"(AccessibilityAnnotations.VISIBILITY to "hidden") { +"gone" }
                    "td"(AccessibilityAnnotations.DISPLAY to "table-cell") { +"Shown" }
                }
                "tr"(AccessibilityAnnotations.VISIBILITY to "hidden") {
                    "td"(AccessibilityAnnotations.VISIBILITY to "hidden") { +"gone" }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "table" {
                "tr" {
                    "td" { }
                    "td"(AccessibilityAnnotations.DISPLAY to "table-cell") { +"Shown" }
                }
            }
        }
    }

    @Test
    fun `should keep a visible descendant of an invisible layout table cell`() = runTest {
        // given — e-mail HTML lays its body out in a borderless table
        val input = semanticEvents(tagged = true) {
            "table"(AccessibilityAnnotations.ROLE to "LayoutTable") {
                "tr" {
                    "td"(AccessibilityAnnotations.VISIBILITY to "hidden") {
                        +"gone"
                        "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"Shown" }
                    }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then — the cell is unwrapped like any other layout table cell
        output sameAs semanticEvents(tagged = true) {
            "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"Shown" }
            +" "
        }
    }

    @Test
    fun `should drop the text of a display contents element inheriting visibility hidden`() = runTest {
        // given — a `display: contents` element lays out no box, so the capture
        // records no visibility of its own: it inherits its parent's.
        val input = semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.VISIBILITY to "hidden") {
                "x-host"(AccessibilityAnnotations.DISPLAY to "contents") {
                    +"Secret label"
                    "span"(
                        AccessibilityAnnotations.DISPLAY to "inline",
                        AccessibilityAnnotations.VISIBILITY to "hidden"
                    ) { +"gone" }
                }
            }
            "x-host"(AccessibilityAnnotations.DISPLAY to "contents") { +"kept" }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "x-host"(AccessibilityAnnotations.DISPLAY to "contents") { +"kept" }
        }
    }

    @Test
    fun `should keep the text of a display contents element set visible again`() = runTest {
        // given — the capture records `visible` on a box-less element only
        // where it differs from what the element would inherit.
        val input = semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.VISIBILITY to "hidden") {
                +"gone"
                "x-host"(
                    AccessibilityAnnotations.DISPLAY to "contents",
                    AccessibilityAnnotations.VISIBILITY to "visible"
                ) { +"shown" }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div" {
                "x-host"(AccessibilityAnnotations.DISPLAY to "contents") { +"shown" }
            }
        }
    }

    @Test
    fun `should drop an unannotated descendant of an invisible element`() = runTest {
        // given — a hand-built stream, or a dump captured before every display
        // was recorded (where an absent annotation may be `display:contents`,
        // which carries no visibility of its own): only a recorded box shows
        // the element has a verdict of its own, so anything else inherits.
        val input = semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.VISIBILITY to "hidden") {
                "p" { +"secret" }
            }
            "p" { +"kept" }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p" { +"kept" }
        }
    }

    @Test
    fun `should drop the document of an invisible frame`() = runTest {
        // given — the capture nests a same-origin frame's document in its
        // <iframe>. `visibility` does not inherit across the document boundary,
        // so the frame's elements carry no verdict, yet the browser paints
        // nothing of an invisible frame.
        val input = semanticEvents(tagged = true) {
            "iframe"(
                "src" to "frame.html",
                AccessibilityAnnotations.DISPLAY to "inline",
                AccessibilityAnnotations.VISIBILITY to "hidden"
            ) {
                "html"(AccessibilityAnnotations.DISPLAY to "block") {
                    "body"(AccessibilityAnnotations.DISPLAY to "block") { +"framed secret" }
                }
            }
            "p" { +"kept" }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p" { +"kept" }
        }
    }

    @Test
    fun `should keep the invisible header row of a data table with a visible body`() = runTest {
        // given — the browser keeps the (blank) header row; dropping it would
        // promote the first body row to the table's header.
        val input = semanticEvents(tagged = true) {
            "table"(AccessibilityAnnotations.ROLE to "table") {
                "thead"(AccessibilityAnnotations.VISIBILITY to "hidden") {
                    "tr"(AccessibilityAnnotations.VISIBILITY to "hidden") {
                        "th"(AccessibilityAnnotations.VISIBILITY to "hidden") { +"A" }
                    }
                }
                "tbody" {
                    "tr" {
                        "td" { +"x" }
                    }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "table" {
                "thead" {
                    "tr" {
                        "th" { }
                    }
                }
                "tbody" {
                    "tr" {
                        "td" { +"x" }
                    }
                }
            }
        }
    }

    @Test
    fun `should keep the invisible header row of an invisible table around a visible cell`() = runTest {
        // given — the header row closes while the table's own verdict is still
        // pending, so it is held with the table and released with it.
        val input = semanticEvents(tagged = true) {
            "table"(
                AccessibilityAnnotations.ROLE to "table",
                AccessibilityAnnotations.VISIBILITY to "hidden"
            ) {
                "thead"(AccessibilityAnnotations.VISIBILITY to "hidden") {
                    "tr"(AccessibilityAnnotations.VISIBILITY to "hidden") {
                        "th"(AccessibilityAnnotations.VISIBILITY to "hidden") { +"A" }
                    }
                }
                "tbody"(AccessibilityAnnotations.VISIBILITY to "hidden") {
                    "tr"(AccessibilityAnnotations.VISIBILITY to "hidden") {
                        "td"(AccessibilityAnnotations.DISPLAY to "table-cell") { +"x" }
                    }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "table" {
                "thead" {
                    "tr" {
                        "th" { }
                    }
                }
                "tbody" {
                    "tr" {
                        "td"(AccessibilityAnnotations.DISPLAY to "table-cell") { +"x" }
                    }
                }
            }
        }
    }

    @Test
    fun `should drop an invisible select with its options`() = runTest {
        // given — the options lay out no box, so they inherit the select's
        // verdict.
        val input = semanticEvents(tagged = true) {
            "select"(AccessibilityAnnotations.VISIBILITY to "hidden") {
                "option"(AccessibilityAnnotations.DISPLAY to "none") { +"apple" }
            }
            "p" { +"kept" }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p" { +"kept" }
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
        // replaced by the UA default of the tag before it reaches the operators
        // downstream.
        output sameAs semanticEvents(tagged = true) {
            "select"("id" to "docselect", AccessibilityAnnotations.DISPLAY to "inline-block") {
                "option"("value" to "", AccessibilityAnnotations.DISPLAY to "flex") { +"address" }
                "option"("value" to "file", AccessibilityAnnotations.DISPLAY to "flex") { +"file upload" }
            }
        }
    }

    @Test
    fun `should read select popup content as the UA default display of its tag`() = runTest {
        // given — a customizable select's popup: every node below the <select>
        // reports the no-layout-box `none`, whatever the stylesheet would lay
        // it out as once the popup opens.
        val input = semanticEvents(tagged = true) {
            "select"("id" to "plan", AccessibilityAnnotations.DISPLAY to "inline-block") {
                "option"("value" to "pro", AccessibilityAnnotations.DISPLAY to "none") {
                    "div"(AccessibilityAnnotations.DISPLAY to "none") { +"Pro" }
                    "div"(AccessibilityAnnotations.DISPLAY to "none") {
                        "span"(AccessibilityAnnotations.DISPLAY to "none") { +"For teams" }
                        "x-badge"(AccessibilityAnnotations.DISPLAY to "none") { +"new" }
                        "source"("srcset" to "/pro.webp", AccessibilityAnnotations.DISPLAY to "none") {}
                    }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then — the artifact is replaced by the display the UA stylesheet gives
        // the tag (an option is a flex container, whose children are left to
        // be blockified downstream), so the operators downstream separate a
        // block `<div>` from its sibling instead of reading it as an inline
        // wrapper; an unknown
        // element is inline, as in a browser, and one the UA stylesheet never
        // renders lays out no box, so it reads as `contents` — no boundary to
        // cross for any operator.
        output sameAs semanticEvents(tagged = true) {
            "select"("id" to "plan", AccessibilityAnnotations.DISPLAY to "inline-block") {
                "option"("value" to "pro", AccessibilityAnnotations.DISPLAY to "flex") {
                    "div" { +"Pro" }
                    "div" {
                        "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"For teams" }
                        "x-badge"(AccessibilityAnnotations.DISPLAY to "inline") { +"new" }
                        "source"("srcset" to "/pro.webp", AccessibilityAnnotations.DISPLAY to "contents") {}
                    }
                }
            }
        }
    }

    @Test
    fun `should blockify the direct children of a select popup option`() = runTest {
        // given — a customizable select lays each <option> out as a flex
        // container, which blockifies its direct children whatever their tag:
        // the flag and the label, the star and the plan name are separate boxes.
        val input = semanticEvents(tagged = true) {
            "select"("id" to "country", AccessibilityAnnotations.DISPLAY to "inline-block") {
                "option"("value" to "pl", AccessibilityAnnotations.DISPLAY to "none") {
                    "img"("src" to "/pl.svg", "alt" to "flag", AccessibilityAnnotations.DISPLAY to "none") {}
                    "span"(AccessibilityAnnotations.DISPLAY to "none") {
                        "b"(AccessibilityAnnotations.DISPLAY to "none") { +"Pol" }
                        +"and"
                    }
                    "source"("srcset" to "/pl.webp", AccessibilityAnnotations.DISPLAY to "none") {}
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then — the option reads as the flex container it is, so its own
        // children carry no display and are blockified downstream; their
        // descendants keep the UA default of their tag, and a never-rendered
        // child still lays out no box.
        output sameAs semanticEvents(tagged = true) {
            "select"("id" to "country", AccessibilityAnnotations.DISPLAY to "inline-block") {
                "option"("value" to "pl", AccessibilityAnnotations.DISPLAY to "flex") {
                    "img"("src" to "/pl.svg", "alt" to "flag") {}
                    "span" {
                        "b"(AccessibilityAnnotations.DISPLAY to "inline") { +"Pol" }
                        +"and"
                    }
                    "source"("srcset" to "/pl.webp", AccessibilityAnnotations.DISPLAY to "contents") {}
                }
            }
        }
    }

    @Test
    fun `should keep the genuine display of a select laid out inside a select popup`() = runTest {
        // given — a JS-built popup holding a select that does lay out a box:
        // its verdict is genuine, so the popup rewrite must not replace it.
        val input = semanticEvents(tagged = true) {
            "select"("id" to "outer", AccessibilityAnnotations.DISPLAY to "inline-block") {
                "div"(AccessibilityAnnotations.DISPLAY to "none") {
                    "select"("id" to "inner", AccessibilityAnnotations.DISPLAY to "inline-flex") {
                        "option"("value" to "a", AccessibilityAnnotations.DISPLAY to "none") { +"Alpha" }
                    }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "select"("id" to "outer", AccessibilityAnnotations.DISPLAY to "inline-block") {
                "div"(AccessibilityAnnotations.DISPLAY to "block") {
                    "select"("id" to "inner", AccessibilityAnnotations.DISPLAY to "inline-flex") {
                        "option"("value" to "a", AccessibilityAnnotations.DISPLAY to "flex") { +"Alpha" }
                    }
                }
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
                "optgroup"("label" to "Fruit", AccessibilityAnnotations.DISPLAY to "block") {
                    "option"("value" to "1", AccessibilityAnnotations.DISPLAY to "flex") { +"apple" }
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
                "option"("value" to "3", AccessibilityAnnotations.DISPLAY to "flex") { +"kept" }
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
                "div"("class" to "popup", AccessibilityAnnotations.DISPLAY to "block") {
                    "option"("value" to "pl", AccessibilityAnnotations.DISPLAY to "flex") { +"Poland" }
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
                "option"("value" to "e", AccessibilityAnnotations.DISPLAY to "flex") {
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
                "option"("value" to "a", AccessibilityAnnotations.DISPLAY to "flex") { +"Alpha" }
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
                "option"("value" to "pl", AccessibilityAnnotations.DISPLAY to "flex") { +"Poland" }
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
                "option"("value" to "pl", AccessibilityAnnotations.DISPLAY to "flex") { +"Poland" }
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
                "option"("value" to "pl", AccessibilityAnnotations.DISPLAY to "flex") { +"Poland" }
                +" "
            }
        }
    }

    @Test
    fun `should keep a select nested in another select's popup`() = runTest {
        // given — a JS-built select whose popup contains a select of its own:
        // the nested <select> carries the popup's artifact `none` on its *own*
        // mark, which the select rule must recognise as the artifact it is
        // (it is the one place the "inside a select" verdict is threaded onto
        // a mark itself rather than onto its children).
        val input = semanticEvents(tagged = true) {
            "select"("id" to "outer", AccessibilityAnnotations.DISPLAY to "inline-block") {
                "div"(AccessibilityAnnotations.DISPLAY to "none") {
                    "select"("id" to "inner", AccessibilityAnnotations.DISPLAY to "none") {
                        "option"("value" to "a", AccessibilityAnnotations.DISPLAY to "none") {
                            +"Alpha"
                        }
                    }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then — the nested select survives, and the meaningless `none` is
        // replaced by its UA default just as it is on every other node in the
        // popup.
        output sameAs semanticEvents(tagged = true) {
            "select"("id" to "outer", AccessibilityAnnotations.DISPLAY to "inline-block") {
                "div"(AccessibilityAnnotations.DISPLAY to "block") {
                    "select"("id" to "inner", AccessibilityAnnotations.DISPLAY to "inline") {
                        "option"("value" to "a", AccessibilityAnnotations.DISPLAY to "flex") { +"Alpha" }
                    }
                }
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
                "option"("value" to "a", AccessibilityAnnotations.DISPLAY to "flex") { +"Alpha" }
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

    @Test
    fun `should keep the whitespace between visible children of an invisible element`() = runTest {
        // given — the space separating the two labels belongs to the hidden
        // host, but the browser still lays it out between them
        val input = semanticEvents(tagged = true) {
            "div"(
                AccessibilityAnnotations.DISPLAY to "block",
                AccessibilityAnnotations.VISIBILITY to "hidden"
            ) {
                "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"Inbox" }
                +" "
                "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"Sent" }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "block") {
                "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"Inbox" }
                +" "
                "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"Sent" }
            }
        }
    }

    @Test
    fun `should keep the head of a document hidden with visibility`() = runTest {
        // given — an anti-flicker `html { visibility: hidden }` with a loading
        // overlay set back to visible; the `<head>` is captured unannotated
        // and its metadata is never painted, so visibility does not apply
        val input = semanticEvents(tagged = true) {
            "html"(
                AccessibilityAnnotations.DISPLAY to "block",
                AccessibilityAnnotations.VISIBILITY to "hidden"
            ) {
                "head" {
                    "title" { +"Shop" }
                }
                "body"(
                    AccessibilityAnnotations.DISPLAY to "block",
                    AccessibilityAnnotations.VISIBILITY to "hidden"
                ) {
                    "div"(AccessibilityAnnotations.DISPLAY to "block") { +"Loading" }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "html"(AccessibilityAnnotations.DISPLAY to "block") {
                "head" {
                    "title" { +"Shop" }
                }
                "body"(AccessibilityAnnotations.DISPLAY to "block") {
                    "div"(AccessibilityAnnotations.DISPLAY to "block") { +"Loading" }
                }
            }
        }
    }

    @Test
    fun `should separate the content on either side of a dropped invisible element`() = runTest {
        // given — `visibility:hidden` still lays the element out, so the
        // browser paints a gap of its width between the two words
        val input = semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"Price"
                "span"(
                    AccessibilityAnnotations.DISPLAY to "inline",
                    AccessibilityAnnotations.VISIBILITY to "hidden"
                ) { +" hidden " }
                +"5"
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"Price"
                +" "
                +"5"
            }
        }
    }

    @Test
    fun `should separate the content on either side of invisible whitespace`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"A"
                "span"(
                    AccessibilityAnnotations.DISPLAY to "inline",
                    AccessibilityAnnotations.VISIBILITY to "hidden"
                ) { +" " }
                +"B"
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"A"
                +" "
                +"B"
            }
        }
    }

    @Test
    fun `should separate the content on either side of a dropped invisible image`() = runTest {
        // given — an image keeps its box when invisible, text or not
        val input = semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"Price"
                "img"(
                    "src" to "spinner.gif",
                    AccessibilityAnnotations.DISPLAY to "inline",
                    AccessibilityAnnotations.VISIBILITY to "hidden"
                ) { }
                +"5"
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"Price"
                +" "
                +"5"
            }
        }
    }

    @Test
    fun `should separate the inline content around an empty invisible block`() = runTest {
        // given — a block box breaks the line even when it is empty and
        // invisible, and no surviving box marks that boundary once it is gone
        val input = semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "block") {
                "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"Price" }
                "div"(
                    AccessibilityAnnotations.DISPLAY to "block",
                    AccessibilityAnnotations.VISIBILITY to "hidden"
                ) { }
                "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"5" }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "block") {
                "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"Price" }
                +" "
                "span"(AccessibilityAnnotations.DISPLAY to "inline") { +"5" }
            }
        }
    }

    @Test
    fun `should not separate the content around an empty invisible inline element`() = runTest {
        // given — an empty inline box has no width, so the browser paints the
        // two letters as one word
        val input = semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"a"
                "span"(
                    AccessibilityAnnotations.DISPLAY to "inline",
                    AccessibilityAnnotations.VISIBILITY to "hidden"
                ) { }
                +"b"
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"a"
                +"b"
            }
        }
    }

    @Test
    fun `should not separate a dropped invisible element from the edge of a block`() = runTest {
        // given — nothing inline precedes the invisible element, so there is
        // nothing for a gap to separate
        val input = semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                "span"(
                    AccessibilityAnnotations.DISPLAY to "inline",
                    AccessibilityAnnotations.VISIBILITY to "hidden"
                ) { +"Sale" }
                +"Price "
                "span"(
                    AccessibilityAnnotations.DISPLAY to "inline",
                    AccessibilityAnnotations.VISIBILITY to "hidden"
                ) { +"old" }
                +"5"
            }
        }

        // when
        val output = input.applyAccessibility()

        // then — and a word already followed by whitespace needs no more
        output sameAs semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"Price "
                +"5"
            }
        }
    }

    @Test
    fun `should separate the invisible text of an element kept for a visible descendant`() = runTest {
        // given — the invisible text around the visible label still takes its
        // width on either side of it
        val input = semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"Price"
                "span"(
                    AccessibilityAnnotations.DISPLAY to "inline",
                    AccessibilityAnnotations.VISIBILITY to "hidden"
                ) {
                    +"was"
                    "b"(
                        AccessibilityAnnotations.DISPLAY to "inline",
                        AccessibilityAnnotations.VISIBILITY to "visible"
                    ) { +"now" }
                    +"only"
                }
                +"5"
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"Price"
                "span"(AccessibilityAnnotations.DISPLAY to "inline") {
                    +" "
                    "b"(AccessibilityAnnotations.DISPLAY to "inline") { +"now" }
                    +" "
                }
                +"5"
            }
        }
    }

    @Test
    fun `should not separate around an element hidden by both display none and visibility hidden`() = runTest {
        // given — the H5BP / Bootstrap `.hidden { display: none; visibility: hidden }`
        // lays out no box at all, so the browser shows `Price5`
        val input = semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"Price"
                "span"(AccessibilityAnnotations.DISPLAY to "none", AccessibilityAnnotations.VISIBILITY to "hidden") { +"old" }
                +"5"
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"Price"
                +"5"
            }
        }
    }

    @Test
    fun `should not separate around display none content inside an invisible element`() = runTest {
        // given — the invisible span holds nothing but content laying out no box
        val input = semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"Price"
                "span"(AccessibilityAnnotations.DISPLAY to "inline", AccessibilityAnnotations.VISIBILITY to "hidden") {
                    "b"(AccessibilityAnnotations.DISPLAY to "none") { +"x" }
                    "img"("src" to "a.png", AccessibilityAnnotations.DISPLAY to "none") { }
                    "script" { +"track()" }
                }
                +"5"
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"Price"
                +"5"
            }
        }
    }

    @Test
    fun `should not separate around an invisible frame laying out no box`() = runTest {
        // given — a `display:none` frame takes no space, invisible or not
        val input = semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"Price"
                "iframe"("src" to "ad.html", AccessibilityAnnotations.DISPLAY to "none", AccessibilityAnnotations.VISIBILITY to "hidden") { }
                +"5"
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"Price"
                +"5"
            }
        }
    }

    @Test
    fun `should not separate around an invisible image laying out no box`() = runTest {
        // given — the display of an image decides before its tag does
        val input = semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"foo"
                "span"(AccessibilityAnnotations.DISPLAY to "inline", AccessibilityAnnotations.VISIBILITY to "hidden") {
                    "img"("src" to "a.png", AccessibilityAnnotations.DISPLAY to "none", AccessibilityAnnotations.VISIBILITY to "hidden") { }
                }
                +"bar"
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"foo"
                +"bar"
            }
        }
    }

    @Test
    fun `should judge the separation by content laying out a box`() = runTest {
        // given — the whitespace ending the `display:none` span is dropped downstream,
        // so it does not separate `Price` from the gap of the invisible span
        val input = semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"Price"
                "span"(AccessibilityAnnotations.DISPLAY to "none") { +"USD " }
                "span"(AccessibilityAnnotations.DISPLAY to "inline", AccessibilityAnnotations.VISIBILITY to "hidden") { +"old" }
                +"5"
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"Price"
                +" "
                +"5"
            }
        }
    }

    @Test
    fun `should not separate inside inline code`() = runTest {
        // given — code is verbatim, so no gap is written into it
        val input = semanticEvents(tagged = true) {
            "code"(AccessibilityAnnotations.DISPLAY to "inline") {
                +"foo"
                "span"(AccessibilityAnnotations.DISPLAY to "inline", AccessibilityAnnotations.VISIBILITY to "hidden") { +"X" }
                +"bar"
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "code"(AccessibilityAnnotations.DISPLAY to "inline") {
                +"foo"
                +"bar"
            }
        }
    }

    @Test
    fun `should not separate after a line break`() = runTest {
        // given — a `<br>` ends the line, whatever its captured display
        val input = semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"Line one"
                "br"(AccessibilityAnnotations.DISPLAY to "inline") { }
                "span"(AccessibilityAnnotations.DISPLAY to "inline", AccessibilityAnnotations.VISIBILITY to "hidden") { +"x" }
                +"two"
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"Line one"
                "br"(AccessibilityAnnotations.DISPLAY to "inline") { }
                +"two"
            }
        }
    }

    @Test
    fun `should write a single gap for nested invisible content held back together`() = runTest {
        // given — the outer text and the dropped inner element are one gap in a row
        val input = semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"Price"
                "span"(AccessibilityAnnotations.DISPLAY to "inline", AccessibilityAnnotations.VISIBILITY to "hidden") {
                    +"was"
                    "span"(AccessibilityAnnotations.DISPLAY to "inline", AccessibilityAnnotations.VISIBILITY to "hidden") { +"old" }
                    "b"(AccessibilityAnnotations.DISPLAY to "inline", AccessibilityAnnotations.VISIBILITY to "visible") { +"now" }
                }
                +"5"
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"Price"
                "span"(AccessibilityAnnotations.DISPLAY to "inline") {
                    +" "
                    "b"(AccessibilityAnnotations.DISPLAY to "inline") { +"now" }
                }
                +"5"
            }
        }
    }


    // --- presentational roles (issue #50) --------------------------------

    @Test
    fun `should demote a heading with a presentation role to a generic container`() = runTest {
        // given — a heading used only for its styling
        val input = semanticEvents(tagged = true) {
            "h2"("role" to "presentation", "id" to "promo", AccessibilityAnnotations.DISPLAY to "block") {
                +"Free shipping"
            }
        }

        // when
        val output = input.applyAccessibility()

        // then the semantics are gone, the box (and its display) stays, and the
        // consumed role does not leak downstream
        output sameAs semanticEvents(tagged = true) {
            "div"("id" to "promo", AccessibilityAnnotations.DISPLAY to "block") {
                +"Free shipping"
            }
        }
    }

    @Test
    fun `should demote a landmark with the none role`() = runTest {
        // given — `none` is a synonym of `presentation`, read case-insensitively
        // from the first token of the role list
        val input = semanticEvents(tagged = true) {
            "nav"("role" to " NONE navigation") {
                "a"("href" to "/home") { +"Home" }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div" {
                "a"("href" to "/home") { +"Home" }
            }
        }
    }

    @Test
    fun `should demote the items of a presentational list too`() = runTest {
        // given — a list's items are its required owned elements, so they
        // inherit its presentational role
        val input = semanticEvents(tagged = true) {
            "ul"("role" to "presentation", AccessibilityAnnotations.DISPLAY to "flex") {
                "li"(AccessibilityAnnotations.DISPLAY to "block") {
                    "a"("href" to "/a") { +"A" }
                }
                "li"(AccessibilityAnnotations.DISPLAY to "block") {
                    "a"("href" to "/b") { +"B" }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "flex") {
                "div"(AccessibilityAnnotations.DISPLAY to "block") {
                    "a"("href" to "/a") { +"A" }
                }
                "div"(AccessibilityAnnotations.DISPLAY to "block") {
                    "a"("href" to "/b") { +"B" }
                }
            }
        }
    }

    @Test
    fun `should keep a list nested in an item of a presentational list`() = runTest {
        // given — the inheritance stops at the nearest list: a nested list owns
        // its own items, and an item with an explicit role does not inherit
        val input = semanticEvents(tagged = true) {
            "ol"("role" to "none") {
                "li" {
                    +"Menu"
                    "ul" {
                        "li" { +"Sub" }
                    }
                }
                "li"("role" to "separator") { }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div" {
                "div" {
                    +"Menu"
                    "ul" {
                        "li" { +"Sub" }
                    }
                }
                "li"("role" to "separator") { }
            }
        }
    }

    @Test
    fun `should demote the terms and definitions of a presentational description list`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "dl"("role" to "presentation") {
                "div" {
                    "dt" { +"Price" }
                    "dd" { +"5" }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then the wrapper between the list and its items does not stop the
        // inheritance — both deliberate choices, not WAI-ARIA: Blink inherits
        // a presentational role from the direct parent only, and `dt`/`dd`
        // are not required owned elements; demoting them keeps a demoted
        // list from leaving items Markdown cannot render outside it
        output sameAs semanticEvents(tagged = true) {
            "div" {
                "div" {
                    "div" { +"Price" }
                    "div" { +"5" }
                }
            }
        }
    }

    @Test
    fun `should ignore a presentation role on an item of a list keeping its role`() = runTest {
        // given — demoting the item alone would leave list content outside any
        // item, which Markdown cannot express
        val input = semanticEvents(tagged = true) {
            "ul" {
                "li"("role" to "presentation") { +"A" }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then the ignored role is gone, so nothing downstream honours it
        output sameAs semanticEvents(tagged = true) {
            "ul" {
                "li" { +"A" }
            }
        }
    }

    @Test
    fun `should ignore a presentation role on a focusable element`() = runTest {
        // given — ARIA presentational role conflict resolution: a focusable
        // element keeps its implicit role
        val input = semanticEvents(tagged = true) {
            "h2"("role" to "presentation", "tabindex" to "-1") { +"Title" }
            "button"("role" to "presentation") { +"Buy" }
            "section"("role" to "none", AccessibilityAnnotations.REF to "7") { +"Card" }
            "p"("role" to "none", "contenteditable" to "") { +"Edit me" }
        }

        // when
        val output = input.applyAccessibility()

        // then each keeps its implicit role: the ignored role is gone, so
        // nothing downstream honours it
        output sameAs semanticEvents(tagged = true) {
            "h2"("tabindex" to "-1") { +"Title" }
            "button" { +"Buy" }
            "section"(AccessibilityAnnotations.REF to "7") { +"Card" }
            "p"("contenteditable" to "") { +"Edit me" }
        }
    }

    @Test
    fun `should ignore a presentation role on an element with a global ARIA attribute`() = runTest {
        // given — a global ARIA attribute also undoes the presentational role,
        // and a list that keeps its role keeps its items as well
        val input = semanticEvents(tagged = true) {
            "ul"("role" to "presentation", "aria-label" to "Sections") {
                "li" { +"A" }
            }
            "h3"("role" to "none", "aria-describedby" to "hint") { +"Title" }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "ul"("aria-label" to "Sections") {
                "li" { +"A" }
            }
            "h3"("aria-describedby" to "hint") { +"Title" }
        }
    }

    @Test
    fun `should keep an inherited item focusable within a presentational list`() = runTest {
        // given — conflict resolution applies to an inherited role too
        val input = semanticEvents(tagged = true) {
            "ul"("role" to "presentation") {
                "li"("tabindex" to "0") { +"A" }
                "li" { +"B" }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div" {
                "li"("tabindex" to "0") { +"A" }
                "div" { +"B" }
            }
        }
    }

    @Test
    fun `should not demote an element for a generic role`() = runTest {
        // given — only an explicit presentation or none removes semantics;
        // generic is every div and span, and a stray one is no such signal
        val input = semanticEvents(tagged = true) {
            "h2"("role" to "generic") { +"Title" }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "h2"("role" to "generic") { +"Title" }
        }
    }

    @Test
    fun `should unwrap a presentational table carrying no role annotation`() = runTest {
        // given — a stream never captured (or by a producer recording no
        // computed role) still says what the author meant
        val input = semanticEvents(tagged = true) {
            "table"("role" to "presentation") {
                "tr" {
                    "td" { +"A" }
                    "td" { +"B" }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            +"A"
            +" "
            +"B"
            +" "
        }
    }

    @Test
    fun `should prefer the computed role of a table over its presentation role`() = runTest {
        // given — Blink resolved the conflict (here a focusable table) itself
        val input = semanticEvents(tagged = true) {
            "table"("role" to "presentation", AccessibilityAnnotations.ROLE to "table") {
                "tr" { "td" { +"A" } }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "table" {
                "tr" { "td" { +"A" } }
            }
        }
    }

    @Test
    fun `should demote a presentational list inside an unwrapped layout table`() = runTest {
        // given — the verdict belongs to the element, wherever it sits
        val input = semanticEvents(tagged = true) {
            "table"(AccessibilityAnnotations.ROLE to "LayoutTable") {
                "tr" {
                    "td" {
                        "ul"("role" to "none") { "li" { +"A" } }
                    }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "div" { "div" { +"A" } }
            +" "
        }
    }


    @Test
    fun `should parse tabindex and contenteditable as HTML does`() = runTest {
        // given — a tabindex is read by its leading integer, and an invalid
        // contenteditable value is the inherit state, not an editing host
        val input = semanticEvents(tagged = true) {
            "h2"("role" to "none", "tabindex" to " 1.5") { +"Focusable" }
            "h2"("role" to "none", "tabindex" to "x1") { +"Not focusable" }
            "p"("role" to "none", "contenteditable" to "PLAINTEXT-ONLY") { +"Editable" }
            "p"("role" to "none", "contenteditable" to "foo") { +"Inherits" }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "h2"("tabindex" to " 1.5") { +"Focusable" }
            "div"("tabindex" to "x1") { +"Not focusable" }
            "p"("contenteditable" to "PLAINTEXT-ONLY") { +"Editable" }
            "div"("contenteditable" to "foo") { +"Inherits" }
        }
    }

    @Test
    fun `should ignore a presentation role on an element with a braille ARIA attribute`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "h2"("role" to "none", "aria-braillelabel" to "T") { +"Title" }
            "nav"("role" to "none", "aria-brailleroledescription" to "menu") { +"Menu" }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs semanticEvents(tagged = true) {
            "h2"("aria-braillelabel" to "T") { +"Title" }
            "nav"("aria-brailleroledescription" to "menu") { +"Menu" }
        }
    }

}
