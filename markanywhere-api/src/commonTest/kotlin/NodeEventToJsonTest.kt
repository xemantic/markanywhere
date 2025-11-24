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

import com.xemantic.kotlin.test.sameAsJson
import kotlin.test.Test

class NodeEventToJsonTest {

    @Test
    fun `should omit null attributes in JSON output`() {
        // given
        val event = NodeEvent.Start(mark = "<div>", attributes = null)

        // when
        val json = event.toJson()

        // then
        json sameAsJson """
            {
              "type": "start",
              "mark": "<div>"
            }
        """.trimIndent()
    }

    @Test
    fun `should include empty attributes in JSON output`() {
        // given
        val event = NodeEvent.Start(mark = "<div>", attributes = emptyMap())

        // when
        val json = event.toJson()

        // then
        json sameAsJson """
            {
              "type": "start",
              "mark": "<div>",
              "attributes": {}
            }
        """.trimIndent()
    }

    @Test
    fun `should include single attribute in JSON output`() {
        // given
        val event = NodeEvent.Start(
            mark = "<link>",
            attributes = mapOf("href" to "https://example.com")
        )

        // when
        val json = event.toJson()

        // then
        json sameAsJson """
            {
              "type": "start",
              "mark": "<link>",
              "attributes": {
                "href": "https://example.com"
              }
            }
        """.trimIndent()
    }

    @Test
    fun `should include multiple attributes in JSON output`() {
        // given
        val event = NodeEvent.Start(
            mark = "<link>",
            attributes = mapOf(
                "href" to "https://example.com",
                "title" to "Example Site",
                "target" to "_blank"
            )
        )

        // when
        val json = event.toJson()

        // then
        json sameAsJson """
            {
              "type": "start",
              "mark": "<link>",
              "attributes": {
                "href": "https://example.com",
                "title": "Example Site",
                "target": "_blank"
              }
            }
        """.trimIndent()
    }

    @Test
    fun `should serialize Text NodeEvent to JSON`() {
        // given
        val event = NodeEvent.Text("Hello World")

        // when
        val json = event.toJson()

        // then
        json sameAsJson """
            {
              "type": "text",
              "text": "Hello World"
            }
        """.trimIndent()
    }

    @Test
    fun `should serialize Text NodeEvent with empty string to JSON`() {
        // given
        val event = NodeEvent.Text(text = "")

        // when
        val json = event.toJson()

        // then
        json sameAsJson """
            {
              "type": "text",
              "text": ""
            }
        """.trimIndent()
    }

    @Test
    fun `should serialize Text NodeEvent with special characters to JSON`() {
        // given
        val event = NodeEvent.Text("Hello \"World\"\nNew line\tTab")

        // when
        val json = event.toJson()

        // then
        json sameAsJson """
            {
              "type": "text",
              "text": "Hello \"World\"\nNew line\tTab"
            }
        """.trimIndent()
    }

    @Test
    fun `should serialize End NodeEvent to JSON`() {
        // given
        val event = NodeEvent.End(mark = "</p>")

        // when
        val json = event.toJson()

        // then
        json sameAsJson """
            {
              "type": "end",
              "mark": "</p>"
            }
        """.trimIndent()
    }

}
