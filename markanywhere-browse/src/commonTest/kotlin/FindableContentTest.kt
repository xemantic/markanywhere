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
import com.xemantic.markanywhere.dump.AccessibilityAnnotations.DISPLAY
import com.xemantic.markanywhere.dump.AccessibilityAnnotations.VISIBILITY
import com.xemantic.markanywhere.dump.SemanticEventDump
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Content the browser skips rendering but find-in-page and fragment
 * navigation reveal — `hidden="until-found"` and the content of a closed
 * `<details>` — against real Chrome, which renders it with
 * `content-visibility: hidden` and so skips its layout. Issue #179.
 *
 * Chrome lays such content out on its own some time after load, so the page
 * inserts it right before the capture: otherwise whether the capture meets
 * the skipped state would depend on timing.
 */
class FindableContentTest {

    @Test
    fun `should record the display of content hidden until found`() = runTest {
        // when
        val dump = capture()

        // then
        val displays = listOf("faq", "answer", "answer-strong", "answer-img", "answer-section", "answer-item", "answer-gone", "answer-invisible")
            .map { dump.element(it)[DISPLAY] }
        assert(displays == listOf("block", "block", "inline", "inline", "flex", "block", "none", "inline"))
        // below a display:none root nothing is recorded, a reader reads it `none`
        assert(dump.element("answer-gone-p")[DISPLAY] == null)
        assert(dump.element("answer-invisible")[VISIBILITY] == "hidden")
    }

    @Test
    fun `should record the display of the content of a closed details`() = runTest {
        // when
        val dump = capture()

        // then
        val displays = listOf("closed-details", "closed-summary", "details-answer", "tip-link", "nested-details", "nested-summary", "nested-answer")
            .map { dump.element(it)[DISPLAY] }
        assert(displays == listOf("block", "list-item", "block", "inline", "block", "list-item", "block"))
    }

    @Test
    fun `should keep the title of a graphic in a closed details laying out nothing`() = runTest {
        // when
        val dump = capture()

        // then - a laid-out part of the svg is left out, its title is `none`
        assert(dump.element("tip-svg")[DISPLAY] == "inline")
        assert(dump.element("tip-title")[DISPLAY] == "none")
        assert(dump.element("tip-path")[DISPLAY] == null)
    }

    @Test
    fun `should keep recording content find-in-page skips as none`() = runTest {
        // when
        val dump = capture()

        // then - the hidden state, an author content-visibility: hidden around
        // or inside findable content, and findable content below display:none
        assert(dump.element("plain-hidden")[DISPLAY] == "none")
        assert(dump.element("answer-skipped")[DISPLAY] == "block")
        assert(dump.element("answer-skipped-p")[DISPLAY] == "none")
        assert(dump.element("skipped")[DISPLAY] == "block")
        assert(dump.element("skipped-p")[DISPLAY] == "none")
        assert(dump.element("skipped-details")[DISPLAY] == "none")
        assert(dump.element("skipped-details-p")[DISPLAY] == null)
        assert(dump.element("gone")[DISPLAY] == "none")
        assert(dump.element("gone-faq-p")[DISPLAY] == null)
    }

    private suspend fun capture(): SemanticEventDump = runInBrowser { browser ->
        // given - the findable content inserted right before the capture
        val tab = browser.get(testPageUrl("findable-content.html"))
        tab.waitUntilLoaded()
        tab.rawEvaluate(
            "document.body.append(document.getElementById('content').content.cloneNode(true))"
        )
        PageSession(tab).dump()
    }

    private fun SemanticEventDump.element(id: String): SemanticEvent.Mark = events
        .filterIsInstance<SemanticEvent.Mark>()
        .single { it["id"] == id }

}
