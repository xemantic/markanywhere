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
import com.xemantic.markanywhere.dump.AccessibilityAnnotations.REDACTED
import com.xemantic.markanywhere.dump.SemanticEventDump
import dev.kdriver.core.browser.Browser
import dev.kdriver.core.tab.Tab
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * A field the page masks with `-webkit-text-security` (a PIN, an OTP) while it
 * is not laid out — in a hidden wizard step, a collapsed panel, a frame —
 * against real Chrome. `DOMSnapshot.captureSnapshot` reports computed styles
 * for laid-out nodes only, so the capture has to ask for the style of such a
 * field on its own (issue #100).
 */
class HiddenMaskedFieldTest {

    @Test
    fun `should redact a masked field that is not laid out`() = runTest {
        val dump = runInBrowser { browser ->
            // given - one of the hidden PINs typed over its markup value
            val tab = openPage(browser)
            tab.rawEvaluate("document.getElementById('step-pin').value = '7777'")

            // when
            PageSession(tab).dump()
        }

        // then - every masked field redacted, laid out or not, in a frame too
        val ids = listOf("shown-pin", "step-pin", "inherited-pin", "own-none", "details-pin", "frame-pin")
        val leaked = ids.filter { id ->
            val input = dump.element(id)
            input[REDACTED] != "filled" || input["value"] != null
        }
        assert(leaked.isEmpty())
        assert(dump.element("step-note")[REDACTED] == "filled")
        assert(dump.textOf("step-note") == "")
        // a hidden field nothing masks keeps its value
        assert(dump.element("step-name")["value"] == "Alice" && dump.element("step-name")[REDACTED] == null)
    }

    @Test
    fun `should keep a masked field that is not laid out out of a name`() = runTest {
        val dump = runInBrowser { browser ->
            // given
            val tab = openPage(browser)

            // when
            PageSession(tab).dump()
        }

        // then
        assert(dump.element("b-step-pin")["aria-label"] == "PIN")
    }

    @Test
    fun `should redact a masked field that is not laid out on a page overriding style getters`() = runTest {
        val dump = runInBrowser { browser ->
            // given - getComputedStyle, getPropertyValue and ownerDocument all answer "unmasked"
            val tab = openPage(browser, "hidden-masked-overriding.html")

            // when
            PageSession(tab).dump()
        }

        // then - Blink's own computed style decides, not the page's script (issue #134)
        val ids = listOf("step-pin", "owner-pin", "frame-pin")
        val leaked = ids.filter { id ->
            val input = dump.element(id)
            input[REDACTED] != "filled" || input["value"] != null
        }
        assert(leaked.isEmpty())
        assert(dump.element("step-note")[REDACTED] == "filled")
        assert(dump.textOf("step-note") == "")
        // read, not failed closed: a hidden field nothing masks keeps its value, in a frame too
        assert(dump.element("step-name")["value"] == "Alice" && dump.element("step-name")[REDACTED] == null)
        assert(dump.element("frame-name")["value"] == "Bob" && dump.element("frame-name")[REDACTED] == null)
    }

    private suspend fun openPage(browser: Browser, page: String = "hidden-masked.html"): Tab =
        browser.get(testPageUrl(page)).also { it.waitUntilLoaded() }

    private fun SemanticEventDump.element(id: String): SemanticEvent.Mark = events
        .filterIsInstance<SemanticEvent.Mark>()
        .single { it["id"] == id }

    /** The text of the element with this [id], up to its close. */
    private fun SemanticEventDump.textOf(id: String): String {
        val start = events.indexOfFirst { it is SemanticEvent.Mark && it["id"] == id }
        return events.drop(start + 1)
            .takeWhile { it !is SemanticEvent.Unmark }
            .filterIsInstance<SemanticEvent.Text>()
            .joinToString("") { it.text }
    }

}
