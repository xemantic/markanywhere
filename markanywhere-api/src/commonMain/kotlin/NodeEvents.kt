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

package com.xemantic.markanywhere

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.intellij.lang.annotations.Language

/**
 * The document node event.
 *
 * It is based on assumption, that hierarchical documents can be
 * serialized as a stream of node events indicating:
 *
 * - the start
 * - the textual content
 * - the end
 *
 * of nested structures. HTML or XML is a good example of a hierarchical
 * document fitting such a criteria.
 *
 * Note: documents like markdown can be represented as a stream of text events.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
public sealed class NodeEvent {

    public fun toJson(): String = markanywhereJson.encodeToString<NodeEvent>(
        this
    )

    @Serializable
    @SerialName("start")
    public data class Start(
        public val mark: String,
        public val attributes: Map<String, String>? = null
    ) : NodeEvent() {
        override fun toString(): String = toJson()
    }

    @Serializable
    @SerialName("text")
    public data class Text(
        public val text: String
    ) : NodeEvent() {
        override fun toString(): String = toJson()
    }

    @Serializable
    @SerialName("end")
    public data class End(
        public val mark: String
    ) : NodeEvent() {
        override fun toString(): String = toJson()
    }

    public companion object {

        public fun fromJson(
            @Language("json")
            json: String
        ): NodeEvent = markanywhereJson.decodeFromString<NodeEvent>(json)

    }

}
