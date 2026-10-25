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

import dev.kdriver.cdp.domain.DOMSnapshot
import dev.kdriver.cdp.domain.css
import dev.kdriver.cdp.domain.dom
import dev.kdriver.core.tab.Tab
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * The snapshot [captureSnapshot] takes once Blink has laid out the content it
 * skips rendering though find-in-page and fragment navigation reveal it — the
 * content of an element hidden until found and of a closed `<details>` (see
 * [SnapshotDom.findableContentOutsideLayout]) — so the capture records it as
 * the page content it is (issue #179).
 *
 * Blink renders both with `content-visibility: hidden`, which skips the
 * content's style and layout, so the snapshot reports no layout for any of it
 * and the capture would record it `display:none` — downstream filters then
 * drop collapsed FAQ answers and accordion panels. Blink lays such content out
 * on its own some time after load (verified live, ~0.6–1.5 s), so whether a
 * capture met the skipped state depended on its timing.
 *
 * Asking Blink for the computed style of one element of the content
 * (`CSS.getComputedStyleForNode`) has it lay out that whole skipped subtree
 * for the next snapshot (verified live), which then carries Blink's own
 * layout verdict: a `display:none` part, an `<svg>`'s `<title>`, or a nested
 * author `content-visibility: hidden` inside still lays out nothing. Forcing a
 * node lays out the skipped ancestors too, so only content whose root the
 * snapshot shows laid out is forced, never what an author
 * `content-visibility: hidden` around it skips; a closed `<details>` nested in
 * another one is laid out only then, so the snapshot is retaken in rounds
 * until nothing more is forced — only on a page holding such content, the
 * common one costs the single snapshot it always took.
 *
 * Side effect: the forced layout stays on the page, as Blink would have laid
 * the content out itself shortly; nothing is painted or revealed by it.
 * Blink keeps the content out of the accessibility tree, so controls inside
 * it get no ref. A failure leaves the content captured as the snapshot finds
 * it.
 */
internal suspend fun Tab.layOutFindableContent(
    captureSnapshot: suspend () -> DOMSnapshot.CaptureSnapshotReturn
): DOMSnapshot.CaptureSnapshotReturn {
    var snapshot = captureSnapshot()
    val forced = mutableSetOf<Int>()
    repeat(MAX_LAYOUT_ROUNDS) {
        val content = snapshot.documents.indices.flatMap {
            SnapshotDom(snapshot, it).findableContentOutsideLayout(forced)
        }
        if (content.isEmpty() || !forceStyles(content)) return snapshot
        forced += content
        snapshot = captureSnapshot()
    }
    return snapshot
}

// a closed <details> nested this deep in others is captured as Blink found it
private const val MAX_LAYOUT_ROUNDS = 4

/**
 * Asks Blink for the computed style of each of the elements with these
 * [backendNodeIds], `true` when it could ask at all.
 */
private suspend fun Tab.forceStyles(backendNodeIds: List<Int>): Boolean = try {
    // a node id exists only below a document the DOM agent handed out
    dom.getDocument(depth = 0)
    val nodeIds = dom.pushNodesByBackendIdsToFrontend(backendNodeIds).nodeIds
    css.enable()
    try {
        coroutineScope {
            nodeIds
                .filter { it != 0 } // 0: no such node any more
                .map { nodeId -> async { forceStyle(nodeId) } }
                .awaitAll()
        }
    } finally {
        withContext(NonCancellable) {
            try {
                css.disable()
            } catch (_: Exception) {
                // an agent left enabled costs only its events; the capture stands
            }
        }
    }
    true
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    false
}

private suspend fun Tab.forceStyle(nodeId: Int) {
    try {
        css.getComputedStyleForNode(nodeId)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // gone since the snapshot: captured as the snapshot finds it
    }
}
