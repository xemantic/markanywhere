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

class ScrollingTest {

    @Test
    fun `should read an element overflowing along a scrolling axis as user scrollable`() {
        // when
        val vertical = isUserScrollable(
            element = "ul", overflowX = "auto", overflowY = "auto",
            scrollWidth = 100, scrollHeight = 90, clientWidth = 100, clientHeight = 30,
        )
        val horizontal = isUserScrollable(
            element = "div", overflowX = "scroll", overflowY = "hidden",
            scrollWidth = 200, scrollHeight = 18, clientWidth = 20, clientHeight = 18,
        )

        // then
        assert(vertical)
        assert(horizontal)
    }

    @Test
    fun `should not read an element as user scrollable without overflow to scroll`() {
        // when - a scrollbar with nothing to scroll, as Chrome leaves unfocusable
        val noOverflow = isUserScrollable(
            element = "ul", overflowX = "scroll", overflowY = "scroll",
            scrollWidth = 100, scrollHeight = 300, clientWidth = 100, clientHeight = 300,
        )
        // overflow along an axis a user cannot scroll
        val wrongAxis = isUserScrollable(
            element = "ul", overflowX = "auto", overflowY = "hidden",
            scrollWidth = 100, scrollHeight = 90, clientWidth = 100, clientHeight = 30,
        )

        // then
        assert(!noOverflow)
        assert(!wrongAxis)
    }

    @Test
    fun `should not read clipped or visible overflow as user scrollable`() {
        // when
        val verdicts = listOf("hidden", "clip", "visible", "").map { overflow ->
            isUserScrollable(
                element = "div", overflowX = overflow, overflowY = overflow,
                scrollWidth = 100, scrollHeight = 90, clientWidth = 50, clientHeight = 30,
            )
        }
        val unknown = isUserScrollable(
            element = "div", overflowX = null, overflowY = null,
            scrollWidth = 100, scrollHeight = 90, clientWidth = 50, clientHeight = 30,
        )

        // then
        assert(verdicts.none { it })
        assert(!unknown)
    }

    @Test
    fun `should not read the elements scrolling the viewport as user scrollable`() {
        // when - their overflow is the viewport's, which is no element
        val verdicts = listOf("html", "body").map { element ->
            isUserScrollable(
                element = element, overflowX = "auto", overflowY = "auto",
                scrollWidth = 100, scrollHeight = 900, clientWidth = 100, clientHeight = 300,
            )
        }

        // then
        assert(verdicts.none { it })
    }

}
