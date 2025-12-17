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

import com.xemantic.kotlin.test.assert
import kotlin.test.Test

class SemanticEventSerializationRoundtripTest {

    @Test
    fun `should round-trip serialize Mark SemanticEvent without attributes`() {
        // given
        val original = SemanticEvent.Mark("p")

        // when
        val json = original.toString()
        val deserialized = SemanticEvent.fromJson(json)

        // then
        assert(original == deserialized)
    }

    @Test
    fun `should round-trip serialize Mark SemanticEvent with null attributes`() {
        // given
        val original = SemanticEvent.Mark(name = "p", attributes = null)

        // when
        val json = original.toString()
        val deserialized = SemanticEvent.fromJson(json)

        // then
        assert(original == deserialized)
    }

    @Test
    fun `should round-trip serialize Mark SemanticEvent with empty attributes`() {
        // given
        val original = SemanticEvent.Mark(name = "p", attributes = emptyMap())

        // when
        val json = original.toString()
        val deserialized = SemanticEvent.fromJson(json)

        // then
        assert(original == deserialized)
    }

    @Test
    fun `should round-trip serialize Mark SemanticEvent with single attribute`() {
        // given
        val original = SemanticEvent.Mark(
            name = "link",
            attributes = mapOf("href" to "https://example.com")
        )

        // when
        val json = original.toString()
        val deserialized = SemanticEvent.fromJson(json)

        // then
        assert(original == deserialized)
    }

    @Test
    fun `should round-trip serialize Mark SemanticEvent with multiple attributes`() {
        // given
        val original = SemanticEvent.Mark(
            name = "link",
            attributes = mapOf(
                "href" to "https://example.com",
                "title" to "Example Site",
                "target" to "_blank"
            )
        )

        // when
        val json = original.toString()
        val deserialized = SemanticEvent.fromJson(json)

        // then
        assert(original == deserialized)
    }

    @Test
    fun `should round-trip serialize Text SemanticEvent`() {
        // given
        val original = SemanticEvent.Text("Hello World")

        // when
        val json = original.toString()
        val deserialized = SemanticEvent.fromJson(json)

        // then
        assert(original == deserialized)
    }

    @Test
    fun `should round-trip serialize Text SemanticEvent with empty string`() {
        // given
        val original = SemanticEvent.Text(text = "")

        // when
        val json = original.toString()
        val deserialized = SemanticEvent.fromJson(json)

        // then
        assert(original == deserialized)
    }

    @Test
    fun `should round-trip serialize Text SemanticEvent with special characters`() {
        // given
        val original = SemanticEvent.Text("Hello \"World\"\nNew line\tTab")

        // when
        val json = original.toString()
        val deserialized = SemanticEvent.fromJson(json)

        // then
        assert(original == deserialized)
    }

    @Test
    fun `should round-trip serialize Text SemanticEvent with unicode characters`() {
        // given
        val original = SemanticEvent.Text("Hello \"World\" & <foo> 👋")

        // when
        val json = original.toString()
        val deserialized = SemanticEvent.fromJson(json)

        // then
        assert(original == deserialized)
    }

    @Test
    fun `should round-trip serialize Unmark SemanticEvent`() {
        // given
        val original = SemanticEvent.Unmark("p")

        // when
        val json = original.toString()
        val deserialized = SemanticEvent.fromJson(json)

        // then
        assert(original == deserialized)
    }

    @Test
    fun `should round-trip serialize Mark SemanticEvent with isTag true`() {
        // given
        val original = SemanticEvent.Mark(name = "br", isTag = true)

        // when
        val json = original.toString()
        val deserialized = SemanticEvent.fromJson(json)

        // then
        assert(original == deserialized)
    }

    @Test
    fun `should round-trip serialize Mark SemanticEvent with isTag and attributes`() {
        // given
        val original = SemanticEvent.Mark(
            name = "img",
            isTag = true,
            attributes = mapOf("src" to "image.png", "alt" to "An image")
        )

        // when
        val json = original.toString()
        val deserialized = SemanticEvent.fromJson(json)

        // then
        assert(original == deserialized)
    }

    @Test
    fun `should round-trip serialize Unmark SemanticEvent with isTag true`() {
        // given
        val original = SemanticEvent.Unmark(name = "br", isTag = true)

        // when
        val json = original.toString()
        val deserialized = SemanticEvent.fromJson(json)

        // then
        assert(original == deserialized)
    }

    @Test
    fun `should round-trip serialize Unmark SemanticEvent with isTag false`() {
        // given
        val original = SemanticEvent.Unmark(name = "p", isTag = false)

        // when
        val json = original.toString()
        val deserialized = SemanticEvent.fromJson(json)

        // then
        assert(original == deserialized)
    }

}
