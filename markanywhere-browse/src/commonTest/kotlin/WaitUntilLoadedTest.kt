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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Integration coverage for the page-settle waiters in `WaitUntilLoaded.kt`.
 *
 * There is no seam to unit-test these against — each is a thin orchestration
 * over live CDP signals (`network.*` events, an in-page `MutationObserver`),
 * so the only honest test drives a real headless browser over a `file://`
 * fixture engineered to either settle or stay busy. Assertions are on the
 * boolean contract only (settled vs. hit-the-cap), never on elapsed time, to
 * keep them off the wall clock and non-flaky.
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
        val settled = runInBrowser { browser ->
            // given
            val tab = browser.get(testPageUrl("simple.html"))

            // when
            tab.waitUntilLoaded(
                networkIdleTime = 300.milliseconds,
                domQuietTime = 300.milliseconds,
                timeout = 10.seconds,
            )
        }

        // then — both network and DOM went quiet within the timeout
        assert(settled)
    }

    @Test
    fun `waitUntilLoaded should report best-effort false when the DOM never settles`() = runTest {
        val settled = runInBrowser { browser ->
            // given — DOM mutates forever, so the dom-idle leg can never confirm
            val tab = browser.get(testPageUrl("dom-busy.html"))

            // when
            tab.waitUntilLoaded(
                networkIdleTime = 300.milliseconds,
                domQuietTime = 300.milliseconds,
                timeout = 1500.milliseconds,
            )
        }

        // then — AND of the two legs is false (capture should still proceed)
        assert(!settled)
    }

}
