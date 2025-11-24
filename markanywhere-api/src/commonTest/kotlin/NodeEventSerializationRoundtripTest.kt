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

class NodeEventSerializationRoundtripTest {

    @Test
    fun `should round-trip serialize Start NodeEvent without attributes`() {
        // given
        val original = NodeEvent.Start(mark = "paragraph")

        // when
        val json = original.toJson()
        val deserialized = NodeEvent.fromJson(json)

        // then
        assert(original == deserialized)
    }

    @Test
    fun `should round-trip serialize Start NodeEvent with null attributes`() {
        // given
        val original = NodeEvent.Start(mark = "paragraph", attributes = null)

        // when
        val json = original.toJson()
        val deserialized = NodeEvent.fromJson(json)

        // then
        assert(original == deserialized)
    }

    @Test
    fun `should round-trip serialize Start NodeEvent with empty attributes`() {
        // given
        val original = NodeEvent.Start(mark = "paragraph", attributes = emptyMap())

        // when
        val json = original.toJson()
        val deserialized = NodeEvent.fromJson(json)

        // then
        assert(original == deserialized)
    }

    @Test
    fun `should round-trip serialize Start NodeEvent with single attribute`() {
        // given
        val original = NodeEvent.Start(
            mark = "link",
            attributes = mapOf("href" to "https://example.com")
        )

        // when
        val json = original.toJson()
        val deserialized = NodeEvent.fromJson(json)

        // then
        assert(original == deserialized)
    }

    @Test
    fun `should round-trip serialize Start NodeEvent with multiple attributes`() {
        // given
        val original = NodeEvent.Start(
            mark = "link",
            attributes = mapOf(
                "href" to "https://example.com",
                "title" to "Example Site",
                "target" to "_blank"
            )
        )

        // when
        val json = original.toJson()
        val deserialized = NodeEvent.fromJson(json)

        // then
        assert(original == deserialized)
    }

    @Test
    fun `should round-trip serialize Text NodeEvent`() {
        // given
        val original = NodeEvent.Text(text = "Hello World")

        // when
        val json = original.toJson()
        val deserialized = NodeEvent.fromJson(json)

        // then
        assert(original == deserialized)
    }

    @Test
    fun `should round-trip serialize Text NodeEvent with empty string`() {
        // given
        val original = NodeEvent.Text(text = "")

        // when
        val json = original.toJson()
        val deserialized = NodeEvent.fromJson(json)

        // then
        assert(original == deserialized)
    }

    @Test
    fun `should round-trip serialize Text NodeEvent with special characters`() {
        // given
        val original = NodeEvent.Text(text = "Hello \"World\"\nNew line\tTab")

        // when
        val json = original.toJson()
        val deserialized = NodeEvent.fromJson(json)

        // then
        assert(original == deserialized)
    }

    @Test
    fun `should round-trip serialize Text NodeEvent with unicode characters`() {
        // given
        val original = NodeEvent.Text(text = "Hello \"World\" & <foo> 👋")

        // when
        val json = original.toJson()
        val deserialized = NodeEvent.fromJson(json)

        // then
        assert(original == deserialized)
    }

    @Test
    fun `should round-trip serialize End NodeEvent`() {
        // given
        val original = NodeEvent.End(mark = "paragraph")

        // when
        val json = original.toJson()
        val deserialized = NodeEvent.fromJson(json)

        // then
        assert(original == deserialized)
    }

}
