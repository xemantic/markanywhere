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

class SemanticEventToStringTest {

    @Test
    fun `should render Text event as data class string`() {
        // given
        val event = SemanticEvent.Text("Hello World")

        // when
        val string = event.toString()

        // then
        assert(string == "Text(text=Hello World)")
    }

    @Test
    fun `should render Text event with empty string as data class string`() {
        // given
        val event = SemanticEvent.Text(text = "")

        // when
        val string = event.toString()

        // then
        assert(string == "Text(text=)")
    }

    @Test
    fun `should render Mark event with defaults as data class string`() {
        // given
        val event = SemanticEvent.Mark("div")

        // when
        val string = event.toString()

        // then
        assert(string == "Mark(name=div, isTagged=false, attributes={})")
    }

    @Test
    fun `should render tagged Mark event as data class string`() {
        // given
        val event = SemanticEvent.Mark(name = "br", isTagged = true)

        // when
        val string = event.toString()

        // then
        assert(string == "Mark(name=br, isTagged=true, attributes={})")
    }

    @Test
    fun `should render Mark event with attributes as data class string`() {
        // given
        val event = SemanticEvent.Mark(
            name = "a",
            isTagged = true,
            attributes = mapOf(
                "href" to "https://example.com",
                "title" to "Example Site"
            )
        )

        // when
        val string = event.toString()

        // then
        assert(
            string == "Mark(name=a, isTagged=true, " +
                "attributes={href=https://example.com, title=Example Site})"
        )
    }

    @Test
    fun `should render Unmark event with defaults as data class string`() {
        // given
        val event = SemanticEvent.Unmark("p")

        // when
        val string = event.toString()

        // then
        assert(string == "Unmark(name=p, isTagged=false)")
    }

    @Test
    fun `should render tagged Unmark event as data class string`() {
        // given
        val event = SemanticEvent.Unmark(name = "br", isTagged = true)

        // when
        val string = event.toString()

        // then
        assert(string == "Unmark(name=br, isTagged=true)")
    }

}