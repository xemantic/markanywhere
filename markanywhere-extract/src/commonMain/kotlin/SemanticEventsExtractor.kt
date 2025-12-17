/*
 * Copyright 2025 Kazimierz Pogoda / Xemantic
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

package com.xemantic.markanywhere.extract

import com.xemantic.markanywhere.SemanticEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

public interface Extractor {

    public fun process(event: SemanticEvent)

    public val isExtracting: Boolean

    public val succeeded: Boolean
        get() = !isExtracting && extractedEvents.isNotEmpty()

    public val extractedEvents: List<SemanticEvent>

    public val attributes: Map<String, String>?

    public val content: String?

}

public fun Flow<SemanticEvent>.extract(
    extractor: Extractor
): Flow<SemanticEvent> = onEach {
    extractor.process(it)
}

public class MarkupContentExtractor(
    private val tag: String
) : Extractor {

    private var _extracting = false

    private val _events = mutableListOf<SemanticEvent>()

    private var _attributes: Map<String, String>? = null

    private val contentBuilder = StringBuilder()

    override val extractedEvents: List<SemanticEvent> = _events

    override val attributes: Map<String, String>? get() = _attributes

    override val isExtracting: Boolean get() = _extracting

    override val content: String? get() = contentBuilder.takeIf {
        it.isNotEmpty()
    }?.toString()

    override fun process(event: SemanticEvent) {
        when (event) {

            is SemanticEvent.Text -> {
                if (_extracting) {
                    _events += event
                    contentBuilder.append(event.text)
                }
            }

            is SemanticEvent.Mark -> {
                if (!succeeded && event.name == tag) {
                    _events += event
                    _attributes = event.attributes
                    _extracting = true
                }
            }

            is SemanticEvent.Unmark -> {
                if (_extracting && event.name == tag) {
                    _extracting = false
                    _events += event
                }
            }

        }
    }

}
