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

import dev.kdriver.cdp.domain.network
import dev.kdriver.core.tab.ReadyState
import dev.kdriver.core.tab.Tab
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Best-effort "the page has settled" wait for a *structure-agnostic* capture:
 * there is no reliable oracle for "an arbitrary page is fully loaded" (a clock,
 * carousel, analytics poll, or websocket never goes quiet), so this layers the
 * three generic signals and lets [timeout] be the hard backstop.
 *
 * 1. [ReadyState.COMPLETE] — document parsed + synchronous sub-resources;
 * 2. [waitForNetworkIdle] — no in-flight requests for [networkIdleTime];
 * 3. [waitForDomIdle] — DOM stops mutating for [domQuietTime].
 *
 * Steps 2 and 3 run concurrently and BOTH must report quiet — each covers the
 * other's blind spot (network-idle is momentarily true between request-sent
 * and render; DOM-idle is momentarily true while a fetch is in flight but not
 * yet rendered). On a never-quiet page the [timeout] cap inside each waiter
 * trips and capture proceeds anyway.
 *
 * @return `true` if both the network and the DOM went quiet within [timeout],
 *   `false` if either hit its cap (capture should still proceed — `false`
 *   means "best effort", not "failed").
 */
public suspend fun Tab.waitUntilLoaded(
    networkIdleTime: Duration = 500.milliseconds,
    domQuietTime: Duration = 500.milliseconds,
    timeout: Duration = 15.seconds,
): Boolean = coroutineScope {
    waitForReadyState(ReadyState.COMPLETE, timeout = timeout.inWholeMilliseconds)
    val networkIdle = async { waitForNetworkIdle(networkIdleTime, timeout) }
    val domIdle = async { waitForDomIdle(domQuietTime, timeout) }
    networkIdle.await() and domIdle.await()
}

/**
 * Suspends until the page's network has been quiet for [idleTime], mimicking
 * Playwright's `waitForLoadState("networkidle")`.
 *
 * kdriver has no built-in equivalent — [Tab.waitForReadyState] only watches
 * `document.readyState` (≈ the `"load"` state) and ignores XHR/fetch traffic,
 * so SPA hydration and lazy content finish *after* it returns. This tracks the
 * raw CDP `Network` events instead: every in-flight request id is held in a
 * set (`requestWillBeSent` adds, `loadingFinished`/`loadingFailed` remove —
 * keyed by id so redirects, which re-fire `requestWillBeSent` under the same
 * id, don't double-count), and the page is "idle" once that set stays at or
 * below [maxInflightRequests] for an uninterrupted [idleTime] window.
 *
 * All three event flows are funneled through a single [Channel] so the
 * in-flight set is only ever touched from this one coroutine — no shared-state
 * races across the [Dispatchers.Default] threads the collectors may run on.
 *
 * @return `true` if the network went idle, `false` if [timeout] elapsed first
 *   (matching [Tab.waitForReadyState]'s boolean contract rather than throwing).
 */
public suspend fun Tab.waitForNetworkIdle(
    idleTime: Duration = 500.milliseconds,
    timeout: Duration = 30.seconds,
    maxInflightRequests: Int = 0,
): Boolean = coroutineScope {
    // Network domain is tab-wide and enable() is idempotent — intentionally left
    // enabled on exit (only the collectors below are per-call and get cancelled);
    // disabling here would blind any concurrent/subsequent network consumer.
    network.enable()
    // (inflight?, requestId): true = a request started, false = it settled
    val signals = Channel<Pair<Boolean, String>>(Channel.UNLIMITED)
    // UNDISPATCHED so each collector is subscribed before this builder returns,
    // so no event fired right after enable() is missed (same guarantee kdriver's
    // own RequestExpectation relies on). This is why we don't fold the three
    // sources into a single merge(...).produceIn(this): merge subscribes its
    // children via a dispatched launch, reopening exactly that post-enable() gap.
    val collectors = listOf(
        launch(start = CoroutineStart.UNDISPATCHED) {
            network.requestWillBeSent.collect { signals.send(true to it.requestId) }
        },
        launch(start = CoroutineStart.UNDISPATCHED) {
            network.loadingFinished.collect { signals.send(false to it.requestId) }
        },
        launch(start = CoroutineStart.UNDISPATCHED) {
            network.loadingFailed.collect { signals.send(false to it.requestId) }
        },
    )
    try {
        withTimeoutOrNull(timeout) {
            val inflight = mutableSetOf<String>()
            while (true) {
                val signal = if (inflight.size <= maxInflightRequests) {
                    // already quiet — if no signal arrives within idleTime, we're done
                    withTimeoutOrNull(idleTime) { signals.receive() }
                        ?: return@withTimeoutOrNull true
                } else {
                    signals.receive()
                }
                if (signal.first) inflight += signal.second else inflight -= signal.second
            }
            @Suppress("UNREACHABLE_CODE")
            true
        } ?: false
    } finally {
        collectors.forEach { it.cancel() }
    }
}

/**
 * Suspends until the page's DOM has stopped mutating for [quietTime], or
 * [timeout] elapses — the signal most directly relevant to a DOM dump, since
 * it observes the very tree being captured regardless of whether a change
 * originated from the network (XHR/fetch render) or from pure client-side JS
 * (template rendering after hydration, which [waitForNetworkIdle] never sees).
 *
 * A `MutationObserver` is installed in the page via [Tab.rawEvaluate] and the
 * returned `Promise` resolves once no mutation has fired for [quietTime]
 * (resolving `true`) or the [timeout] cap is hit (resolving `false`). Running
 * the debounce inside the page avoids round-tripping every mutation over CDP.
 *
 * The observed target is `document`, not `document.documentElement`: right
 * after a navigation commits there is a window in which the new document has
 * no document element yet, and `observe(null)` throws `TypeError: parameter 1
 * is not of type 'Node'` — which, called on the heels of a click that
 * navigates, fails the whole wait. A `Document` is always a `Node`, and
 * `subtree: true` from it covers everything `documentElement` would have,
 * including a document element that appears (or is replaced) later.
 *
 * @return `true` if the DOM went quiet, `false` if [timeout] elapsed first.
 */
public suspend fun Tab.waitForDomIdle(
    quietTime: Duration = 500.milliseconds,
    timeout: Duration = 15.seconds,
): Boolean {
    val result = rawEvaluate(
        expression = /* language=javascript */ """
            new Promise((resolve) => {
              const quiet = ${quietTime.inWholeMilliseconds};
              const maxWait = ${timeout.inWholeMilliseconds};
              let timer = null;
              let settled = false;
              const finish = (idle) => {
                if (settled) return;
                settled = true;
                observer.disconnect();
                if (timer !== null) clearTimeout(timer);
                resolve(idle);
              };
              const bump = () => {
                if (timer !== null) clearTimeout(timer);
                timer = setTimeout(() => finish(true), quiet);
              };
              const observer = new MutationObserver(bump);
              observer.observe(document, {
                subtree: true, childList: true, attributes: true, characterData: true
              });
              bump();
              setTimeout(() => finish(false), maxWait);
            })
        """.trimIndent(),
        awaitPromise = true,
    )
    return (result as? JsonPrimitive)?.booleanOrNull ?: false
}
