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

class MarkClassOperatorTest {

    @Test
    fun `plus should create attributes map with class when attributes are null`() {
        // given
        val mark = SemanticEvent.Mark("div")

        // when
        val result = mark + "foo"

        // then
        assert(result == SemanticEvent.Mark("div", attributes = mapOf("class" to "foo")))
    }

    @Test
    fun `plus should create class entry when attributes map is empty`() {
        // given
        val mark = SemanticEvent.Mark("div", attributes = emptyMap())

        // when
        val result = mark + "foo"

        // then
        assert(result == SemanticEvent.Mark("div", attributes = mapOf("class" to "foo")))
    }

    @Test
    fun `plus should append class to existing class attribute`() {
        // given
        val mark = SemanticEvent.Mark("div", attributes = mapOf("class" to "bar"))

        // when
        val result = mark + "foo"

        // then
        assert(result == SemanticEvent.Mark("div", attributes = mapOf("class" to "bar foo")))
    }

    @Test
    fun `plus should leave class untouched when value already present`() {
        // given
        val mark = SemanticEvent.Mark("div", attributes = mapOf("class" to "foo"))

        // when
        val result = mark + "foo"

        // then
        assert(result == mark)
    }

    @Test
    fun `plus should leave class untouched when value already present among multiple classes`() {
        // given
        val mark = SemanticEvent.Mark("div", attributes = mapOf("class" to "foo bar baz"))

        // when
        val result = mark + "bar"

        // then
        assert(result == mark)
    }

    @Test
    fun `plus should preserve other attributes when adding class`() {
        // given
        val mark = SemanticEvent.Mark(
            name = "div",
            attributes = mapOf("id" to "x")
        )

        // when
        val result = mark + "foo"

        // then
        assert(result == SemanticEvent.Mark(
            name = "div",
            attributes = mapOf("id" to "x", "class" to "foo")
        ))
    }

    @Test
    fun `plus should preserve isTagged when adding class`() {
        // given
        val mark = SemanticEvent.Mark(name = "div", isTagged = true)

        // when
        val result = mark + "foo"

        // then
        assert(result == SemanticEvent.Mark(
            name = "div",
            isTagged = true,
            attributes = mapOf("class" to "foo")
        ))
    }

    @Test
    fun `plus should normalize existing class whitespace when appending`() {
        // given
        val mark = SemanticEvent.Mark("div", attributes = mapOf("class" to "  bar   baz  "))

        // when
        val result = mark + "foo"

        // then
        assert(result == SemanticEvent.Mark(
            name = "div",
            attributes = mapOf("class" to "bar baz foo")
        ))
    }

    @Test
    fun `plus should split operand on whitespace adding each token`() {
        // given
        val mark = SemanticEvent.Mark("div")

        // when
        val result = mark + "foo bar"

        // then
        assert(result == SemanticEvent.Mark(
            name = "div",
            attributes = mapOf("class" to "foo bar")
        ))
    }

    @Test
    fun `plus should dedupe tokens from operand against existing class`() {
        // given
        val mark = SemanticEvent.Mark("div", attributes = mapOf("class" to "bar"))

        // when
        val result = mark + "foo bar baz"

        // then
        assert(result == SemanticEvent.Mark(
            name = "div",
            attributes = mapOf("class" to "bar foo baz")
        ))
    }

    @Test
    fun `plus should be no-op when operand is blank`() {
        // given
        val mark = SemanticEvent.Mark("div", attributes = mapOf("class" to "bar"))

        // when
        val result = mark + "   "

        // then
        assert(result == mark)
    }

    @Test
    fun `plus should be no-op when operand is empty`() {
        // given
        val mark = SemanticEvent.Mark("div")

        // when
        val result = mark + ""

        // then
        assert(result == mark)
    }

    @Test
    fun `minus should be no-op when attributes are null`() {
        // given
        val mark = SemanticEvent.Mark("div")

        // when
        val result = mark - "foo"

        // then
        assert(result == mark)
    }

    @Test
    fun `minus should be no-op when attributes are empty`() {
        // given
        val mark = SemanticEvent.Mark("div", attributes = emptyMap())

        // when
        val result = mark - "foo"

        // then
        assert(result == mark)
    }

    @Test
    fun `minus should be no-op when class attribute is absent`() {
        // given
        val mark = SemanticEvent.Mark("div", attributes = mapOf("id" to "x"))

        // when
        val result = mark - "foo"

        // then
        assert(result == mark)
    }

    @Test
    fun `minus should be no-op when value not in class`() {
        // given
        val mark = SemanticEvent.Mark("div", attributes = mapOf("class" to "bar baz"))

        // when
        val result = mark - "foo"

        // then
        assert(result == mark)
    }

    @Test
    fun `minus should remove class entry entirely when removing only class`() {
        // given
        val mark = SemanticEvent.Mark("div", attributes = mapOf("class" to "foo"))

        // when
        val result = mark - "foo"

        // then
        assert(result == SemanticEvent.Mark("div", attributes = emptyMap()))
    }

    @Test
    fun `minus should remove class entry but preserve other attributes`() {
        // given
        val mark = SemanticEvent.Mark(
            name = "div",
            attributes = mapOf("id" to "x", "class" to "foo")
        )

        // when
        val result = mark - "foo"

        // then
        assert(result == SemanticEvent.Mark(
            name = "div",
            attributes = mapOf("id" to "x")
        ))
    }

    @Test
    fun `minus should remove one class from multi-class list`() {
        // given
        val mark = SemanticEvent.Mark("div", attributes = mapOf("class" to "foo bar baz"))

        // when
        val result = mark - "bar"

        // then
        assert(result == SemanticEvent.Mark(
            name = "div",
            attributes = mapOf("class" to "foo baz")
        ))
    }

    @Test
    fun `minus should normalize existing class whitespace when removing`() {
        // given
        val mark = SemanticEvent.Mark("div", attributes = mapOf("class" to "  foo   bar  "))

        // when
        val result = mark - "foo"

        // then
        assert(result == SemanticEvent.Mark(
            name = "div",
            attributes = mapOf("class" to "bar")
        ))
    }

    @Test
    fun `minus should split operand on whitespace removing each token`() {
        // given
        val mark = SemanticEvent.Mark("div", attributes = mapOf("class" to "foo bar baz"))

        // when
        val result = mark - "foo baz"

        // then
        assert(result == SemanticEvent.Mark(
            name = "div",
            attributes = mapOf("class" to "bar")
        ))
    }

    @Test
    fun `minus should remove class entry when all tokens removed via multi-token operand`() {
        // given
        val mark = SemanticEvent.Mark("div", attributes = mapOf("class" to "foo bar"))

        // when
        val result = mark - "foo bar"

        // then
        assert(result == SemanticEvent.Mark("div", attributes = emptyMap()))
    }

    @Test
    fun `minus should ignore unknown tokens in multi-token operand`() {
        // given
        val mark = SemanticEvent.Mark("div", attributes = mapOf("class" to "foo bar"))

        // when
        val result = mark - "foo unknown"

        // then
        assert(result == SemanticEvent.Mark(
            name = "div",
            attributes = mapOf("class" to "bar")
        ))
    }

    @Test
    fun `minus should preserve isTagged`() {
        // given
        val mark = SemanticEvent.Mark(
            name = "div",
            isTagged = true,
            attributes = mapOf("class" to "foo")
        )

        // when
        val result = mark - "foo"

        // then
        assert(result == SemanticEvent.Mark(
            name = "div",
            isTagged = true,
            attributes = emptyMap()
        ))
    }

    @Test
    fun `minus should be no-op when operand is blank`() {
        // given
        val mark = SemanticEvent.Mark("div", attributes = mapOf("class" to "foo"))

        // when
        val result = mark - "   "

        // then
        assert(result == mark)
    }

}
