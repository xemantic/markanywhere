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

class MarkClassListTest {

    private fun markWithClass(value: String) = SemanticEvent.Mark(
        name = "div",
        attributes = mapOf("class" to value)
    )

    @Test
    fun `should return empty list when class attribute is absent`() {
        // given
        val mark = SemanticEvent.Mark("div")

        // when
        val classList = mark.classList

        // then
        assert(classList == emptyList<String>())
    }

    @Test
    fun `should return empty list for empty class attribute`() {
        // given
        val mark = markWithClass("")

        // when
        val classList = mark.classList

        // then
        assert(classList == emptyList<String>())
    }

    @Test
    fun `should return empty list for whitespace-only class attribute`() {
        // given
        val mark = markWithClass("  \t \n ")

        // when
        val classList = mark.classList

        // then
        assert(classList == emptyList<String>())
    }

    @Test
    fun `should return single class`() {
        // given
        val mark = markWithClass("icon")

        // when
        val classList = mark.classList

        // then
        assert(classList == listOf("icon"))
    }

    @Test
    fun `should split multiple classes on single spaces`() {
        // given
        val mark = markWithClass("fa-solid fa-square-info")

        // when
        val classList = mark.classList

        // then
        assert(classList == listOf("fa-solid", "fa-square-info"))
    }

    @Test
    fun `should collapse runs of multiple spaces`() {
        // given
        val mark = markWithClass("foo   bar")

        // when
        val classList = mark.classList

        // then
        assert(classList == listOf("foo", "bar"))
    }

    @Test
    fun `should ignore leading and trailing whitespace`() {
        // given
        val mark = markWithClass("  foo bar ")

        // when
        val classList = mark.classList

        // then
        assert(classList == listOf("foo", "bar"))
    }

    @Test
    fun `should split on tab, line feed, form feed and carriage return`() {
        // given
        val formFeed = Char(0x0C)
        val mark = markWithClass("a\tb\nc${formFeed}d\re")

        // when
        val classList = mark.classList

        // then
        assert(classList == listOf("a", "b", "c", "d", "e"))
    }

    @Test
    fun `should not split on non-ASCII whitespace`() {
        // given
        val nbsp = Char(0xA0)
        val mark = markWithClass("foo${nbsp}bar")

        // when
        val classList = mark.classList

        // then
        assert(classList == listOf("foo${nbsp}bar"))
    }

    @Test
    fun `should remove duplicate classes preserving first occurrence order`() {
        // given
        val mark = markWithClass("b a b c a")

        // when
        val classList = mark.classList

        // then
        assert(classList == listOf("b", "a", "c"))
    }

}