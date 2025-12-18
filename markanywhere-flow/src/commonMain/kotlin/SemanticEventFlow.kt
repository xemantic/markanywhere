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

package com.xemantic.markanywhere.flow

import com.xemantic.markanywhere.SemanticEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * Creates [Flow] of [SemanticEvent]s.
 *
 * Convenient for testing.
 */
public fun semanticEvents(
    produceTags: Boolean = false,
    block: suspend SemanticEventScope.() -> Unit
): Flow<SemanticEvent> = flow {
    SemanticEventScope(produceTags, this).block()
}

public class SemanticEventScope(
    public val produceTags: Boolean = false,
    @PublishedApi
    internal val collector: FlowCollector<SemanticEvent>,
) {

    public suspend inline operator fun String.unaryPlus() {
        text(this)
    }

    public suspend inline operator fun Char.unaryPlus() {
        text(this.toString())
    }

    public suspend inline operator fun String.invoke(
        vararg attributes: Pair<String, String>,
        crossinline block: suspend SemanticEventScope.() -> Unit
    ) {
        invoke(attributes.toMap(), block)
    }

    public suspend fun text(
        text: String,
    ) {
        collector.emit(
            SemanticEvent.Text(text)
        )
    }

    public suspend inline fun mark(
        name: String,
        isTag: Boolean = produceTags,
        vararg attributes: Pair<String, String>,
    ) {
        mark(name, isTag, attributes.toMap())
    }

    public suspend fun mark(
        name: String,
        isTag: Boolean = produceTags,
        attributes: Map<String, String>? = null
    ) {
        collector.emit(
            SemanticEvent.Mark(
                name = name,
                attributes = attributes,
                isTag = isTag
            )
        )
    }

    public suspend fun unmark(
        name: String,
        isTag: Boolean = produceTags
    ) {
        collector.emit(
            SemanticEvent.Unmark(name, isTag)
        )
    }

    public suspend inline operator fun String.invoke(
        attributes: Map<String, String>? = null,
        crossinline block: suspend SemanticEventScope.() -> Unit
    ) {
        mark(this, attributes = attributes)
        block()
        unmark(this)
    }

    public suspend inline fun tag(
        name: String,
        attributes: Map<String, String>? = null,
        crossinline block: suspend SemanticEventScope.() -> Unit
    ) {
        mark(name, isTag = true, attributes)
        block()
        unmark(name, isTag = true)
    }

    public suspend inline fun tag(
        name: String,
        vararg attributes: Pair<String, String>,
        crossinline block: suspend SemanticEventScope.() -> Unit
    ) {
        tag(name, attributes.toMap(), block)
    }

}
