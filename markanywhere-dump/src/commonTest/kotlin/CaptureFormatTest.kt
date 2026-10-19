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

package com.xemantic.markanywhere.dump

import com.xemantic.kotlin.test.assert
import kotlin.test.Test

class CaptureFormatTest {

    @Test
    fun `should read the format a marker names`() {
        // when
        val layout = CaptureFormat.of("layout-3")

        // then
        assert(layout == LAYOUT)
    }

    @Test
    fun `should read no format from an absent or unknown marker`() {
        // when
        val absent = CaptureFormat.of(null)
        val unknown = CaptureFormat.of("layout-4")
        val walker = CaptureFormat.of("dom-1")

        // then - an unmarked stream (or a format this reader predates) is left
        // to the reader's legacy inference
        assert(absent == null)
        assert(unknown == null)
        assert(walker == null)
    }

    @Test
    fun `should stamp the marker on the root attributes`() {
        // when
        val attributes = mapOf("lang" to "en").withCaptureFormat(LAYOUT)

        // then
        assert(attributes == mapOf("lang" to "en", AccessibilityAnnotations.CAPTURE to "layout-3"))
    }

    @Test
    fun `should leave the display of every descendant of a display none root unrecorded`() {
        // when
        val belowRoot = unrecordedDisplayOfChildren(name = "div", display = "none", inherited = null)
        val belowDescendant = unrecordedDisplayOfChildren(name = "svg", display = null, inherited = NO_BOX)
        val belowForeignObject = unrecordedDisplayOfChildren(
            name = "foreignObject", display = null, inherited = NO_BOX
        )

        // then - nothing below lays out a box, the graphic and its HTML included
        assert(belowRoot == NO_BOX)
        assert(belowDescendant == NO_BOX)
        assert(belowForeignObject == NO_BOX)
    }

    @Test
    fun `should leave the display inside an svg unrecorded up to a foreignObject`() {
        // when
        val belowSvg = unrecordedDisplayOfChildren(name = "svg", display = "inline", inherited = null)
        val belowGroup = unrecordedDisplayOfChildren(name = "g", display = null, inherited = GRAPHIC)
        val belowNestedSvg = unrecordedDisplayOfChildren(name = "svg", display = null, inherited = GRAPHIC)
        val belowForeignObject = unrecordedDisplayOfChildren(
            name = "foreignObject", display = null, inherited = GRAPHIC
        )

        // then - a foreignObject's children are HTML laid out by CSS again
        assert(belowSvg == GRAPHIC)
        assert(
            unrecordedDisplayOfChildren(name = "g", display = "none", inherited = GRAPHIC) == NO_BOX
        )
        assert(belowGroup == GRAPHIC)
        assert(belowNestedSvg == GRAPHIC)
        assert(belowForeignObject == null)
    }

    @Test
    fun `should record the display of the children of a laid out html element`() {
        // when
        val belowBlock = unrecordedDisplayOfChildren(name = "div", display = "block", inherited = null)
        val belowContents = unrecordedDisplayOfChildren(name = "div", display = "contents", inherited = null)
        val belowHiddenSvg = unrecordedDisplayOfChildren(name = "svg", display = "none", inherited = null)

        // then - a display:none svg is a display:none root first
        assert(belowBlock == null)
        assert(belowContents == null)
        assert(belowHiddenSvg == NO_BOX)
    }

    @Test
    fun `should record the display of a laid out html element`() {
        // when
        val recorded = displayToRecord(display = "block", inherited = null)

        // then
        assert(recorded == "block")
    }

    @Test
    fun `should record only a none inside an svg`() {
        // when
        val laidOut = displayToRecord(display = "inline", inherited = GRAPHIC)
        val contents = displayToRecord(display = "contents", inherited = GRAPHIC)
        val hidden = displayToRecord(display = "none", inherited = GRAPHIC)

        // then - a part laying out nothing (a display:none group, a title, a
        // symbol's content, an HTML child the svg never renders) is still
        // dropped downstream with its subtree
        assert(laidOut == null)
        assert(contents == null)
        assert(hidden == "none")
    }

    @Test
    fun `should record no display below a display none root`() {
        // when
        val recorded = displayToRecord(display = "none", inherited = NO_BOX)

        // then
        assert(recorded == null)
    }

    @Test
    fun `should strip annotations a page authored itself`() {
        // given - only a producer may write them: a page faking a layout box,
        // a ref or a capture marker would be believed downstream
        val attributes = mapOf(
            "class" to "x",
            AccessibilityAnnotations.DISPLAY to "inline",
            AccessibilityAnnotations.REF to "1",
            AccessibilityAnnotations.CAPTURE to "layout-3",
            "data-markanywhere-anything" to "y",
            "data-markanywhere" to "kept",
            "data-other" to "kept",
        )

        // when
        val stripped = attributes.withoutCaptureAnnotations()

        // then
        assert(stripped == mapOf("class" to "x", "data-markanywhere" to "kept", "data-other" to "kept"))
    }

}
