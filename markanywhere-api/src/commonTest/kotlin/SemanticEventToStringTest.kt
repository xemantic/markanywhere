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

class SemanticEventToStringTest {

    @Test
    fun `should omit null attributes in JSON output`() {
        // given
        val event = SemanticEvent.Mark("div")

        // when
        val json = event.toString()

        // then
        json sameAsJson """
            {
              "type": "mark",
              "name": "div"
            }
        """.trimIndent()
    }

    @Test
    fun `should omit empty attributes in JSON output`() {
        // given
        val event = SemanticEvent.Mark(name = "div", attributes = emptyMap())

        // when
        val json = event.toString()

        // then
        json sameAsJson """
            {
              "type": "mark",
              "name": "div",
              "attributes": {}
            }
        """.trimIndent()
    }

    @Test
    fun `should include single attribute in JSON output`() {
        // given
        val event = SemanticEvent.Mark(
            name = "link",
            attributes = mapOf("href" to "https://example.com")
        )

        // when
        val json = event.toString()

        // then
        json sameAsJson """
            {
              "type": "mark",
              "name": "link",
              "attributes": {
                "href": "https://example.com"
              }
            }
        """.trimIndent()
    }

    @Test
    fun `should include multiple attributes in JSON output`() {
        // given
        val event = SemanticEvent.Mark(
            name = "link",
            attributes = mapOf(
                "href" to "https://example.com",
                "title" to "Example Site",
                "target" to "_blank"
            )
        )

        // when
        val json = event.toString()

        // then
        json sameAsJson """
            {
              "type": "mark",
              "name": "link",
              "attributes": {
                "href": "https://example.com",
                "title": "Example Site",
                "target": "_blank"
              }
            }
        """.trimIndent()
    }

    @Test
    fun `should serialize Text SemanticEvent to JSON`() {
        // given
        val event = SemanticEvent.Text("Hello World")

        // when
        val json = event.toString()

        // then
        json sameAsJson """
            {
              "type": "text",
              "text": "Hello World"
            }
        """.trimIndent()
    }

    @Test
    fun `should serialize Text SemanticEvent with empty string to JSON`() {
        // given
        val event = SemanticEvent.Text(text = "")

        // when
        val json = event.toString()

        // then
        json sameAsJson """
            {
              "type": "text",
              "text": ""
            }
        """.trimIndent()
    }

    @Test
    fun `should serialize Text SemanticEvent with special characters to JSON`() {
        // given
        val event = SemanticEvent.Text("Hello \"World\"\nNew line\tTab")

        // when
        val json = event.toString()

        // then
        json sameAsJson """
            {
              "type": "text",
              "text": "Hello \"World\"\nNew line\tTab"
            }
        """.trimIndent()
    }

    @Test
    fun `should serialize Unmark SemanticEvent to JSON`() {
        // given
        val event = SemanticEvent.Unmark("p")

        // when
        val json = event.toString()

        // then
        json sameAsJson """
            {
              "type": "unmark",
              "name": "p"
            }
        """.trimIndent()
    }

    @Test
    fun `should omit isTag when false in Mark JSON output`() {
        // given
        val event = SemanticEvent.Mark(name = "div", isTag = false)

        // when
        val json = event.toString()

        // then
        json sameAsJson """
            {
              "type": "mark",
              "name": "div"
            }
        """.trimIndent()
    }

    @Test
    fun `should include isTag when true in Mark JSON output`() {
        // given
        val event = SemanticEvent.Mark(name = "br", isTag = true)

        // when
        val json = event.toString()

        // then
        json sameAsJson """
            {
              "type": "mark",
              "name": "br",
              "isTag": true
            }
        """.trimIndent()
    }

    @Test
    fun `should include isTag and attributes in Mark JSON output`() {
        // given
        val event = SemanticEvent.Mark(
            name = "img",
            isTag = true,
            attributes = mapOf("src" to "image.png", "alt" to "An image")
        )

        // when
        val json = event.toString()

        // then
        json sameAsJson """
            {
              "type": "mark",
              "name": "img",
              "isTag": true,
              "attributes": {
                "src": "image.png",
                "alt": "An image"
              }
            }
        """.trimIndent()
    }

    @Test
    fun `should omit isTag when false in Unmark JSON output`() {
        // given
        val event = SemanticEvent.Unmark(name = "p", isTag = false)

        // when
        val json = event.toString()

        // then
        json sameAsJson """
            {
              "type": "unmark",
              "name": "p"
            }
        """.trimIndent()
    }

    @Test
    fun `should include isTag when true in Unmark JSON output`() {
        // given
        val event = SemanticEvent.Unmark(name = "br", isTag = true)

        // when
        val json = event.toString()

        // then
        json sameAsJson """
            {
              "type": "unmark",
              "name": "br",
              "isTag": true
            }
        """.trimIndent()
    }

}