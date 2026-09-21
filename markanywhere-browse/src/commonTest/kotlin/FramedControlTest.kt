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

class FramedControlTest {

    @Test
    fun `should stamp a ref on a control inside a same-origin frame`() = runTest {
        val dump = runInBrowser { browser ->
            // given - a control in the page and one in a same-origin frame
            val tab = browser.get(testPageUrl("framed-control.html"))
            tab.waitUntilLoaded()
            val session = PageSession(tab)

            // when
            session.dump()
        }

        // then - both controls carry a ref
        val buttons = dump.events
            .filterIsInstance<SemanticEvent.Mark>()
            .filter { it.name == "button" }
        assert(buttons.map { it["id"] } == listOf("outer", "inner"))
        assert(buttons.all { it[AccessibilityAnnotations.REF] != null })
    }

}
