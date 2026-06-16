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

import com.xemantic.kotlin.test.assert
import kotlin.test.Test

class SemanticEventTextContentTest {

    @Test
    fun `should return empty string for empty events`() {
        // when
        val content = emptyList<SemanticEvent>().textContent()

        // then
        assert(content == "")
    }

    @Test
    fun `should concatenate text of consecutive Text events`() {
        // given
        val events = listOf(
            SemanticEvent.Text("in"),
            SemanticEvent.Text("fo"),
        )

        // when
        val content = events.textContent()

        // then
        assert(content == "info")
    }

    @Test
    fun `should ignore Mark and Unmark events while preserving text order`() {
        // given
        val events = listOf(
            SemanticEvent.Mark("p"),
            SemanticEvent.Text("keep "),
            SemanticEvent.Mark("code"),
            SemanticEvent.Text("this"),
            SemanticEvent.Unmark("code"),
            SemanticEvent.Unmark("p"),
        )

        // when
        val content = events.textContent()

        // then
        assert(content == "keep this")
    }

    @Test
    fun `should return empty string when there are no Text events`() {
        // given
        val events = listOf(
            SemanticEvent.Mark("i", attributes = mapOf("class" to "icon")),
            SemanticEvent.Unmark("i"),
        )

        // when
        val content = events.textContent()

        // then
        assert(content == "")
    }

}