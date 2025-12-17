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

class SemanticEventDeserializationTest {

    @Test
    fun `should deserialize Mark SemanticEvent from JSON`() {
        // given
        val json = """
            {
              "type": "mark",
              "name": "html"
            }
        """.trimIndent()

        // when
        val event = SemanticEvent.fromJson(json)

        // then
        event should {
            be<SemanticEvent.Mark>()
            have(name == "html")
            have(isTag == false)
        }
    }

    @Test
    fun `should deserialize Mark SemanticEvent with attributes from JSON`() {
        // given
        val json = """
            {
              "type": "mark",
              "name": "div",
              "attributes": {
                "id": "foo",
                "class": "bar"
              }
            }
        """.trimIndent()

        // when
        val event = SemanticEvent.fromJson(json)

        // then
        event should {
            be<SemanticEvent.Mark>()
            have(name == "div")
            have(isTag == false)
            have(attributes == mapOf(
                "id" to "foo",
                "class" to "bar"
            ))
        }
    }

    @Test
    fun `should deserialize Mark SemanticEvent with isTag true from JSON`() {
        // given
        val json = """
            {
              "type": "mark",
              "name": "br",
              "isTag": true
            }
        """.trimIndent()

        // when
        val event = SemanticEvent.fromJson(json)

        // then
        event should {
            be<SemanticEvent.Mark>()
            have(name == "br")
            have(isTag == true)
        }
    }

    @Test
    fun `should deserialize Mark SemanticEvent with isTag and attributes from JSON`() {
        // given
        val json = """
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

        // when
        val event = SemanticEvent.fromJson(json)

        // then
        event should {
            be<SemanticEvent.Mark>()
            have(name == "img")
            have(isTag == true)
            have(attributes == mapOf(
                "src" to "image.png",
                "alt" to "An image"
            ))
        }
    }

    @Test
    fun `should deserialize Text SemanticEvent from JSON`() {
        // given
        val json = """
            {
              "type": "text",
              "text": "Foo"
            }
        """.trimIndent()

        // when
        val event = SemanticEvent.fromJson(json)

        // then
        event should {
            be<SemanticEvent.Text>()
            have(text == "Foo")
        }
    }

    @Test
    fun `should deserialize Unmark SemanticEvent from JSON`() {
        // given
        val json = """
            {
              "type": "unmark",
              "name": "div"
            }
        """.trimIndent()

        // when
        val event = SemanticEvent.fromJson(json)

        // then
        event should {
            be<SemanticEvent.Unmark>()
            have(name == "div")
            have(isTag == false)
        }
    }

    @Test
    fun `should deserialize Unmark SemanticEvent with isTag true from JSON`() {
        // given
        val json = """
            {
              "type": "unmark",
              "name": "br",
              "isTag": true
            }
        """.trimIndent()

        // when
        val event = SemanticEvent.fromJson(json)

        // then
        event should {
            be<SemanticEvent.Unmark>()
            have(name == "br")
            have(isTag == true)
        }
    }

    @Test
    fun `should deserialize Text SemanticEvent with empty text from JSON`() {
        // given
        val json = """
            {
              "type": "text",
              "text": ""
            }
        """.trimIndent()

        // when
        val event = SemanticEvent.fromJson(json)

        // then
        event should {
            be<SemanticEvent.Text>()
            have(text == "")
        }
    }

    @Test
    fun `should deserialize Text SemanticEvent with special characters from JSON`() {
        // given
        val json = """
            {
              "type": "text",
              "text": "Hello \"World\" & <foo>"
            }
        """.trimIndent()

        // when
        val event = SemanticEvent.fromJson(json)

        // then
        event should {
            be<SemanticEvent.Text>()
            have(text == "Hello \"World\" & <foo>")
        }
    }

    @Test
    fun `should deserialize Text SemanticEvent with unicode and emojis from JSON`() {
        // given
        val json = """
            {
              "type": "text",
              "text": "Hello 👋 World 🌍"
            }
        """.trimIndent()

        // when
        val event = SemanticEvent.fromJson(json)

        // then
        event should {
            be<SemanticEvent.Text>()
            have(text == "Hello 👋 World 🌍")
        }
    }

    @Test
    fun `should deserialize Text SemanticEvent with whitespace variations from JSON`() {
        // given
        val json = """
            {
              "type": "text",
              "text": "  \n\t  "
            }
        """.trimIndent()

        // when
        val event = SemanticEvent.fromJson(json)

        // then
        event should {
            be<SemanticEvent.Text>()
            have(text == "  \n\t  ")
        }
    }

    @Test
    fun `should deserialize Mark SemanticEvent with empty attributes map from JSON`() {
        // given
        val json = """
            {
              "type": "mark",
              "name": "div",
              "attributes": {}
            }
        """.trimIndent()

        // when
        val event = SemanticEvent.fromJson(json)

        // then
        event should {
            be<SemanticEvent.Mark>()
            have(name == "div")
            have(attributes == emptyMap<String, String>())
        }
    }

    @Test
    fun `should deserialize Mark SemanticEvent with empty string attribute values from JSON`() {
        // given
        val json = """
            {
              "type": "mark",
              "name": "input",
              "attributes": {
                "id": "",
                "value": ""
              }
            }
        """.trimIndent()

        // when
        val event = SemanticEvent.fromJson(json)

        // then
        event should {
            be<SemanticEvent.Mark>()
            have(name == "input")
            have(attributes == mapOf(
                "id" to "",
                "value" to ""
            ))
        }
    }

    @Test
    fun `should deserialize Mark SemanticEvent with special characters in attribute values from JSON`() {
        // given
        val json = """
            {
              "type": "mark",
              "name": "div",
              "attributes": {
                "data-test": "Hello \"World\" & <foo>",
                "title": "Line 1\nLine 2"
              }
            }
        """.trimIndent()

        // when
        val event = SemanticEvent.fromJson(json)

        // then
        event should {
            be<SemanticEvent.Mark>()
            have(name == "div")
            have(attributes == mapOf(
                "data-test" to "Hello \"World\" & <foo>",
                "title" to "Line 1\nLine 2"
            ))
        }
    }

}
