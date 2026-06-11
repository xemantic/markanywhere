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

package com.xemantic.markanywhere

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A captured stream of [SemanticEvent]s representing the
 * rendered DOM tree of an HTML page, together with the provenance metadata
 * needed to reproduce and reason about the capture.
 *
 * Note: A real page is walked in the browser,
 * see `markanywhere-js` and `markanywhere-dump`.
 *
 * @param url the URL of the page the DOM was captured from.
 * @param dumpedAt the instant at which the DOM was captured.
 * @param events the semantic event stream of the captured, rendered DOM tree.
 */
@Serializable
public data class SemanticEventDump(
    val url: String,
    val dumpedAt: Instant,
    val events: List<SemanticEvent>
)