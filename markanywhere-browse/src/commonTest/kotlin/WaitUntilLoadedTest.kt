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
import dev.kdriver.cdp.domain.Fetch
import dev.kdriver.cdp.domain.fetch
import dev.kdriver.core.tab.ReadyState
import dev.kdriver.core.tab.Tab
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.measureTime

/**
 * Integration coverage for the page-settle waiters in `WaitUntilLoaded.kt`.
 *
 * There is no seam to unit-test these against — each is a thin orchestration
 * over live CDP signals (`network.*` events, an in-page `MutationObserver`),
 * so the only honest test drives a real headless browser over a `file://`
 * fixture engineered to either settle or stay busy. Assertions are on the
 * reported outcome (settled vs. hit-the-cap), not on elapsed time, to keep them
 * off the wall clock and non-flaky — the one exception is the shared-budget
 * test, whose contract *is* the time spent, asserted with a generous margin.
 *
 * Each test runs in its own browser via [runInBrowser] (the module's existing
 * pattern) and on a real dispatcher — see [CreateBrowserTest] for why
 * `runTest`'s virtual time cannot be used for browser control.
 */
class WaitUntilLoadedTest {

    @Test
    fun `waitForDomIdle should report idle once the DOM stops mutating`() = runTest {
        val idle = runInBrowser { browser ->
            // given — a page that mutates a few times then goes permanently quiet
            val tab = browser.get(testPageUrl("dom-idle.html"))

            // when
            tab.waitForDomIdle(quietTime = 300.milliseconds, timeout = 10.seconds)
        }

        // then
        assert(idle)
    }

    @Test
    fun `waitForDomIdle should hit its cap on a page that mutates forever`() = runTest {
        val idle = runInBrowser { browser ->
            // given — a page mutating the DOM faster than quietTime, indefinitely
            val tab = browser.get(testPageUrl("dom-busy.html"))

            // when
            tab.waitForDomIdle(quietTime = 300.milliseconds, timeout = 1500.milliseconds)
        }

        // then — the debounce never fires, so the timeout cap resolves false
        assert(!idle)
    }

    @Test
    fun `waitForDomIdle should report idle on a document with no document element`() = runTest {
        val idle = runInBrowser { browser ->
            // given — a document whose documentElement is gone, standing in for the
            // window right after a navigation commits, before the new document has
            // one; observing it directly would throw "parameter 1 is not of type
            // 'Node'" and fail every click that navigates
            val tab = browser.get(testPageUrl("simple.html"))
            tab.rawEvaluate("document.removeChild(document.documentElement)")

            // when
            tab.waitForDomIdle(quietTime = 300.milliseconds, timeout = 10.seconds)
        }

        // then — the wait completes normally, and the empty document is quiet
        assert(idle)
    }

    @Test
    fun `waitForNetworkIdle should report idle on a page issuing no requests`() = runTest {
        val idle = runInBrowser { browser ->
            // given — a fully static page whose load requests have settled
            val tab = browser.get(testPageUrl("simple.html"))

            // when
            tab.waitForNetworkIdle(idleTime = 300.milliseconds, timeout = 10.seconds)
        }

        // then
        assert(idle)
    }

    @Test
    fun `waitForNetworkIdle should hit its cap on a page that keeps requesting`() = runTest {
        val idle = runInBrowser { browser ->
            // given — a page firing a fresh request every 100ms (< idleTime), forever
            val tab = browser.get(testPageUrl("network-busy.html"))

            // when
            tab.waitForNetworkIdle(idleTime = 500.milliseconds, timeout = 1500.milliseconds)
        }

        // then — the in-flight set never stays quiet for a full idleTime window
        assert(!idle)
    }

    @Test
    fun `waitUntilLoaded should report settled on a quiescent page`() = runTest {
        val load = runInBrowser { browser ->
            // given
            val tab = browser.get(testPageUrl("simple.html"))

            // when
            tab.waitUntilLoaded(
                networkIdleTime = 300.milliseconds,
                domQuietTime = 300.milliseconds,
                timeout = 10.seconds,
            )
        }

        // then — the document completed, and both network and DOM went quiet
        assert(load == PageLoad(ReadyState.COMPLETE, networkIdle = true, domIdle = true))
        assert(load.settled)
    }

    @Test
    fun `waitUntilLoaded should report best-effort false when the DOM never settles`() = runTest {
        val load = runInBrowser { browser ->
            // given — DOM mutates forever, so the dom-idle leg can never confirm
            val tab = browser.get(testPageUrl("dom-busy.html"))

            // when
            tab.waitUntilLoaded(
                networkIdleTime = 300.milliseconds,
                domQuietTime = 300.milliseconds,
                timeout = 1500.milliseconds,
            )
        }

        // then — not settled (capture should still proceed), and only the DOM is to blame
        assert(load == PageLoad(ReadyState.COMPLETE, networkIdle = true, domIdle = false))
        assert(!load.settled)
    }

    @Test
    fun `waitUntilLoaded should report a parsed document whose load never fires`() = runTest {
        val (paused, load) = runInBrowser { browser ->
            // given — a fully arrived document whose one image is never answered,
            // so the load event never fires and readyState stays "interactive"
            val tab = browser.get()
            tab.withStalledRequests("*stalled.png") { paused ->
                tab.get(testPageUrl("stalled.html"))

                // when
                paused.isCompleted to tab.waitUntilLoaded(
                    networkIdleTime = 300.milliseconds,
                    domQuietTime = 300.milliseconds,
                    timeout = 1500.milliseconds,
                )
            }
        }

        // then — the readyState cap folds into the result instead of throwing,
        // and the document is reported as parsed, hence safe to capture
        assert(paused)
        assert(load.readyState == ReadyState.INTERACTIVE)
        assert(load.parsed)
        assert(!load.settled)
    }

    @Test
    fun `waitUntilLoaded should report a document the parser never finished`() = runTest {
        val (paused, load) = runInBrowser { browser ->
            // given — a parser-blocking script the server never delivers, so the
            // document is cut off before the rest of its body
            val tab = browser.get()
            tab.withStalledRequests("*blocked.js") { paused ->
                tab.get(testPageUrl("blocked.html"))

                // when
                paused.isCompleted to tab.waitUntilLoaded(
                    networkIdleTime = 300.milliseconds,
                    domQuietTime = 300.milliseconds,
                    timeout = 1500.milliseconds,
                )
            }
        }

        // then — distinguishable from a harmless "still busy": the capture would be truncated
        assert(paused)
        assert(load.readyState == ReadyState.LOADING)
        assert(!load.parsed)
        assert(!load.settled)
    }

    @Test
    fun `waitUntilLoaded should not throw when the page navigates during the wait`() = runTest {
        val load = runInBrowser { browser ->
            // given — a page reloading itself every 50ms, so evaluations keep
            // losing their JavaScript context mid-flight, as after a click that navigates
            val tab = browser.get(testPageUrl("navigating.html"))

            // when
            tab.waitUntilLoaded(
                networkIdleTime = 300.milliseconds,
                domQuietTime = 300.milliseconds,
                timeout = 1500.milliseconds,
            )
        }

        // then — best effort: the page never settled, but the wait still returned
        assert(!load.settled)
    }

    @Test
    fun `waitUntilLoaded should spend a single timeout across all its steps`() = runTest {
        val elapsed = runInBrowser { browser ->
            // given — the load event never fires (stalled image) AND the DOM never
            // goes quiet, so every step runs to its cap
            val tab = browser.get()
            tab.withStalledRequests("*stalled.png") {
                tab.get(testPageUrl("stalled.html"))
                tab.rawEvaluate("setInterval(() => document.body.dataset.tick = Date.now(), 50)")

                // when
                TimeSource.Monotonic.measureTime {
                    tab.waitUntilLoaded(
                        networkIdleTime = 300.milliseconds,
                        domQuietTime = 300.milliseconds,
                        timeout = 3.seconds,
                    )
                }
            }
        }

        // then — one budget, not one per step (which would take 6s or more);
        // the margin absorbs CDP round-trips on a slow machine
        assert(elapsed < 4500.milliseconds)
    }

}

/**
 * Pauses every request matching [urlPattern] via the CDP Fetch domain and never
 * answers it — a server that never responds — for the duration of [block].
 *
 * [block] receives a deferred completed once a request was actually paused, so a
 * test can assert the interception happened rather than silently exercising a
 * request that failed fast (e.g. a browser not intercepting `file://` URLs).
 * Interception is disabled afterwards, which releases the paused request.
 */
private suspend fun <T> Tab.withStalledRequests(
    urlPattern: String,
    block: suspend (paused: Deferred<Unit>) -> T,
): T = coroutineScope {
    val paused = CompletableDeferred<Unit>()
    // UNDISPATCHED so the collector is subscribed before interception is enabled
    val collector = launch(start = CoroutineStart.UNDISPATCHED) {
        fetch.requestPaused.first()
        paused.complete(Unit)
    }
    fetch.enable(patterns = listOf(Fetch.RequestPattern(urlPattern = urlPattern)))
    try {
        block(paused)
    } finally {
        collector.cancel()
        fetch.disable()
    }
}
