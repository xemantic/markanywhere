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

import com.xemantic.kotlin.test.be
import com.xemantic.kotlin.test.have
import com.xemantic.kotlin.test.should
import kotlin.test.Test

class NodeEventDeserializationTest {

    @Test
    fun `should deserialize Start NodeEvent from JSON`() {
        // given
        val json = """
            {
              "type": "start",
              "mark": "<html>"
            }            
        """.trimIndent()

        // when
        val event = NodeEvent.fromJson(json)

        // then
        event should {
            be<NodeEvent.Start>()
            have(mark == "<html>")
        }
    }

    @Test
    fun `should deserialize Start NodeEvent with attributes from JSON`() {
        // given
        val json = """
            {
              "type": "start",
              "mark": "<div>",
              "attributes": {
                "id": "foo",
                "class": "bar"
              }
            }            
        """.trimIndent()

        // when
        val event = NodeEvent.fromJson(json)

        // then
        event should {
            be<NodeEvent.Start>()
            have(mark == "<div>")
            have(attributes == mapOf(
                "id" to "foo",
                "class" to "bar"
            ))
        }
    }

    @Test
    fun `should deserialize Text NodeEvent from JSON`() {
        // given
        val json = """
            {
              "type": "text",
              "text": "Foo"
            }            
        """.trimIndent()

        // when
        val event = NodeEvent.fromJson(json)

        // then
        event should {
            be<NodeEvent.Text>()
            have(text == "Foo")
        }
    }

    @Test
    fun `should parse End NodeEvent from JSON`() {
        // given
        val json = """
            {
              "type": "end",
              "mark": "</div>"
            }
        """.trimIndent()

        // when
        val event = NodeEvent.fromJson(json)

        // then
        event should {
            be<NodeEvent.End>()
            have(mark == "</div>")
        }
    }

    @Test
    fun `should deserialize Text NodeEvent with empty text from JSON`() {
        // given
        val json = """
            {
              "type": "text",
              "text": ""
            }
        """.trimIndent()

        // when
        val event = NodeEvent.fromJson(json)

        // then
        event should {
            be<NodeEvent.Text>()
            have(text == "")
        }
    }

    @Test
    fun `should deserialize Text NodeEvent with special characters from JSON`() {
        // given
        val json = """
            {
              "type": "text",
              "text": "Hello \"World\" & <foo>"
            }
        """.trimIndent()

        // when
        val event = NodeEvent.fromJson(json)

        // then
        event should {
            be<NodeEvent.Text>()
            have(text == "Hello \"World\" & <foo>")
        }
    }

    @Test
    fun `should deserialize Text NodeEvent with unicode and emojis from JSON`() {
        // given
        val json = """
            {
              "type": "text",
              "text": "Hello 👋 World 🌍"
            }
        """.trimIndent()

        // when
        val event = NodeEvent.fromJson(json)

        // then
        event should {
            be<NodeEvent.Text>()
            have(text == "Hello 👋 World 🌍")
        }
    }

    @Test
    fun `should deserialize Text NodeEvent with whitespace variations from JSON`() {
        // given
        val json = """
            {
              "type": "text",
              "text": "  \n\t  "
            }
        """.trimIndent()

        // when
        val event = NodeEvent.fromJson(json)

        // then
        event should {
            be<NodeEvent.Text>()
            have(text == "  \n\t  ")
        }
    }

    @Test
    fun `should deserialize Start NodeEvent with empty attributes map from JSON`() {
        // given
        val json = """
            {
              "type": "start",
              "mark": "<div>",
              "attributes": {}
            }
        """.trimIndent()

        // when
        val event = NodeEvent.fromJson(json)

        // then
        event should {
            be<NodeEvent.Start>()
            have(mark == "<div>")
            have(attributes == emptyMap<String, String>())
        }
    }

    @Test
    fun `should deserialize Start NodeEvent with empty string attribute values from JSON`() {
        // given
        val json = """
            {
              "type": "start",
              "mark": "<input>",
              "attributes": {
                "id": "",
                "value": ""
              }
            }
        """.trimIndent()

        // when
        val event = NodeEvent.fromJson(json)

        // then
        event should {
            be<NodeEvent.Start>()
            have(mark == "<input>")
            have(attributes == mapOf(
                "id" to "",
                "value" to ""
            ))
        }
    }

    @Test
    fun `should deserialize Start NodeEvent with special characters in attribute values from JSON`() {
        // given
        val json = """
            {
              "type": "start",
              "mark": "<div>",
              "attributes": {
                "data-test": "Hello \"World\" & <foo>",
                "title": "Line 1\nLine 2"
              }
            }
        """.trimIndent()

        // when
        val event = NodeEvent.fromJson(json)

        // then
        event should {
            be<NodeEvent.Start>()
            have(mark == "<div>")
            have(attributes == mapOf(
                "data-test" to "Hello \"World\" & <foo>",
                "title" to "Line 1\nLine 2"
            ))
        }
    }

}
