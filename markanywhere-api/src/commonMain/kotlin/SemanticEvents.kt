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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.intellij.lang.annotations.Language

/**
 * A semantic event which occurs in the streams of meanings.
 *
 * It is based on assumption, that hierarchical documents can be
 * serialized as a stream of semantic events indicating:
 *
 * - the textual content
 * - the mark
 * - the unmark
 *
 * of nested structures. HTML or XML is a good example of a hierarchical
 * document fitting such a criteria.
 *
 * Note: documents like Markdown can be represented as a stream of text events
 * while still featuring embedded markup.
 */
@Serializable
public sealed interface SemanticEvent {

    @Serializable
    @SerialName("text")
    public data class Text(
        public val text: String
    ) : SemanticEvent {
        override fun toString(): String = toJson()
    }

    @Serializable
    @SerialName("mark")
    public data class Mark(
        public override val name: String,
        public override val isTag: Boolean = false,
        public val attributes: Map<String, String>? = null
    ) : SemanticEvent, Marked {

        override fun toString(): String = toJson()

    }

    @Serializable
    @SerialName("unmark")
    public data class Unmark(
        public override val name: String,
        public override val isTag: Boolean = false
    ) : SemanticEvent, Marked {

        override fun toString(): String = toJson()

    }

    /**
     * Common interface for [Mark] and [Unmark] events.
     */
    public interface Marked {

        /**
         * The name of the mark, typically corresponding to an HTML5 tag name.
         */
        public val name: String

        /**
         * Indicates whether this mark originates from an HTML tag embedded in the source,
         * rather than from Markdown syntax.
         *
         * When `false` (default), the mark was produced from Markdown syntax
         * (e.g., `*text*` produces an `em` mark with `isTag = false`).
         *
         * When `true`, the mark was produced from an actual HTML tag in the source
         * (e.g., `<em>text</em>` in Markdown produces an `em` mark with `isTag = true`).
         *
         * This distinction allows downstream processors to differentiate between
         * semantic marks derived from Markdown formatting and explicit HTML markup.
         */
        public val isTag: Boolean

    }

    public companion object {

        /**
         * Parses JSON representing a [SemanticEvent]
         *
         * @param json the JSON to parse.
         * @throws kotlinx.serialization.SerializationException on malformed or unexpected JSON.
         */
        public fun fromJson(
            @Language("json") json: String
        ): SemanticEvent = markanywhereJson.decodeFromString<SemanticEvent>(
            string = json
        )

    }

}

private fun SemanticEvent.toJson() = markanywhereJson.encodeToString<SemanticEvent>(
    value = this
)
