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

package com.xemantic.markanywhere.browse

import com.xemantic.kotlin.test.assert
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.dump.AccessibilityAnnotations
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ScrollContainerTest {

    @Test
    fun `should mark the scroll containers a user can scroll`() = runTest {
        val dump = runInBrowser { browser ->
            // given - lists a browser makes focusable by scrolling them, and
            // lists it does not: clipped, and with nothing to scroll
            val tab = browser.get(testPageUrl("scroll-container.html"))
            tab.waitUntilLoaded()
            val session = PageSession(tab)

            // when
            session.dump()
        }

        // then - Blink's accessibility tree reports none of them focusable,
        // so the scrollable verdict is the one telling them apart
        val lists = dump.events
            .filterIsInstance<SemanticEvent.Mark>()
            .filter { it.name == "ul" }
        val scrollable = lists.filter { it[AccessibilityAnnotations.SCROLLABLE] == "true" }
        assert(scrollable.map { it["id"] } == listOf("scrolling", "sideways"))
        assert(lists.none { it[AccessibilityAnnotations.REF] != null })
    }

}
