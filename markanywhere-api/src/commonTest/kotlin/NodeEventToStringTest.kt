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

class NodeEventToStringTest {

    @Test
    fun `should return JSON String for Start event`() {
        // given
        val event = NodeEvent.Start(
            mark = "<div>",
            attributes = mapOf(
                "id" to "foo",
                "class" to "bar"
            )
        )

        // when
        val json = event.toString()

        // then
        json sameAsJson """
            {
              "type": "start",
              "mark": "<div>",
              "attributes": {
                "id": "foo",
                "class": "bar"
              }
            }
        """.trimIndent()
    }

    @Test
    fun `should return JSON String for Text event`() {
        // given
        val event = NodeEvent.Text("foo")

        // when
        val json = event.toString()

        // then
        json sameAsJson """
            {
              "type": "text",
              "text": "foo"
            }
        """.trimIndent()
    }

    @Test
    fun `should return JSON String for End event`() {
        // given
        val event = NodeEvent.End("</div>")

        // when
        val json = event.toString()

        // then
        json sameAsJson """
            {
              "type": "end",
              "mark": "</div>"
            }
        """.trimIndent()
    }

}
