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

import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SemanticEventDeserializationOfInvalidJsonTest {

    @Test
    fun `should fail to deserialize SemanticEvent with missing type field`() {
        // given
        val json = """
            {
              "mark": "div"
            }
        """.trimIndent()

        // when/then
        assertFailsWith<SerializationException> {
            SemanticEvent.fromJson(json)
        }
    }

    @Test
    fun `should fail to deserialize SemanticEvent with unknown type`() {
        // given
        val json = """
            {
              "type": "unknown",
              "mark": "div"
            }
        """.trimIndent()

        // when/then
        assertFailsWith<SerializationException> {
            SemanticEvent.fromJson(json)
        }
    }

    @Test
    fun `should fail to deserialize Mark SemanticEvent with missing name field`() {
        // given
        val json = """
            {
              "type": "mark"
            }
        """.trimIndent()

        // when/then
        assertFailsWith<SerializationException> {
            SemanticEvent.fromJson(json)
        }
    }

    @Test
    fun `should fail to deserialize Text SemanticEvent with missing text field`() {
        // given
        val json = """
            {
              "type": "text"
            }
        """.trimIndent()

        // when/then
        assertFailsWith<SerializationException> {
            SemanticEvent.fromJson(json)
        }
    }

    @Test
    fun `should fail to deserialize Unmark SemanticEvent with missing name field`() {
        // given
        val json = """
            {
              "type": "unmark"
            }
        """.trimIndent()

        // when/then
        assertFailsWith<SerializationException> {
            SemanticEvent.fromJson(json)
        }
    }

    @Test
    fun `should fail parsing malformed JSON`() {
        // given
        val json = """
            {
              "type": "start",
              "mark": "div"
        """.trimIndent()

        // when/then
        assertFailsWith<SerializationException> {
            SemanticEvent.fromJson(json)
        }
    }

    @Test
    fun `should fail parsing invalid JSON with trailing comma`() {
        // given
        val json = """
            {
              "type": "start",
              "mark": "div",
            }
        """.trimIndent()

        // when/then
        assertFailsWith<SerializationException> {
            SemanticEvent.fromJson(json)
        }
    }

}
