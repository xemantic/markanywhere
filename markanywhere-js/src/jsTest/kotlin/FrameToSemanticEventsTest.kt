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

package com.xemantic.markanywhere.js

import com.xemantic.kotlin.test.assert
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import org.w3c.dom.HTMLIFrameElement
import kotlin.test.Test

class FrameToSemanticEventsTest {

    @Test
    fun `should descend into a same-origin iframe`() = runTest {
        // given - a screen reader reads a same-origin frame's document as part
        // of the same accessibility tree, so the capture nests it in the
        // <iframe> rather than stopping at the element.
        val frame = loadFrame("""<iframe id="f" srcdoc="&lt;p&gt;inside&lt;/p&gt;"></iframe>""")

        // when
        val names = document.body!!.toSemanticEvents().toList().map {
            when (it) {
                is Mark -> "<${it.name}>"
                is Unmark -> "</${it.name}>"
                is Text -> it.text
            }
        }

        // then - the frame's content is nested inside the iframe mark
        assert(frame.id == "f")
        assert(names.contains("<iframe>"))
        assert(names.contains("inside"))
        assert(names.indexOf("<iframe>") < names.indexOf("inside"))
        assert(names.indexOf("inside") < names.indexOf("</iframe>"))
        // and the frame's own document root came with it
        assert(names.indexOf("<iframe>") < names.indexOf("<html>"))
    }

    @Test
    fun `should keep the event stream balanced around a frame`() = runTest {
        // given
        loadFrame("""<iframe id="g" srcdoc="&lt;p&gt;x&lt;/p&gt;"></iframe>""")

        // when
        val events = document.body!!.toSemanticEvents().toList()

        // then - every mark closes, in LIFO order
        val open = mutableListOf<String>()
        events.forEach {
            when (it) {
                is Mark -> open.add(it.name)
                is Unmark -> {
                    assert(open.isNotEmpty())
                    assert(open.removeLast() == it.name)
                }
                is Text -> { /* content */ }
            }
        }
        assert(open.isEmpty())
    }

    /** Installs [html] in the body and suspends until its frame has parsed. */
    private suspend fun loadFrame(html: String): HTMLIFrameElement {
        document.body!!.innerHTML = html
        val frame = document.body!!.firstElementChild as HTMLIFrameElement
        // Real time, not `delay`: `runTest` runs on a virtual clock that skips
        // `delay` entirely, so the frame would never get a chance to parse. The
        // load event alone is not enough either — it can fire while the frame
        // still holds its initial empty document — so poll the content.
        repeat(200) {
            val body = frame.contentDocument?.body
            if (body != null && body.innerHTML.isNotEmpty()) return frame
            realDelay(10)
        }
        error("frame did not parse its document")
    }

    private suspend fun realDelay(
        millis: Int
    ) = suspendCancellableCoroutine { continuation ->
        window.setTimeout({ continuation.resume(Unit) { _, _, _ -> } }, millis)
    }

}
