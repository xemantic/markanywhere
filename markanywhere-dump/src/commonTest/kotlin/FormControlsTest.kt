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

package com.xemantic.markanywhere.dump

import com.xemantic.kotlin.test.assert
import kotlin.test.Test

class FormControlsTest {

    private val redacted = AccessibilityAnnotations.REDACTED

    @Test
    fun `should write the live value over the markup value`() {
        // given
        val attributes = mapOf("type" to "text", "value" to "preset")

        // when
        val live = formControlAttributes("input", attributes, FormControlState(value = "Alice"))

        // then
        assert(live == mapOf("type" to "text", "value" to "Alice"))
    }

    @Test
    fun `should treat an input without a type as text`() {
        // when
        val live = formControlAttributes("input", emptyMap(), FormControlState(value = "x"))

        // then
        assert(live == mapOf("value" to "x"))
    }

    @Test
    fun `should drop the markup value of a field cleared by the user`() {
        // given
        val attributes = mapOf("type" to "text", "value" to "preset")

        // when
        val live = formControlAttributes("input", attributes, FormControlState(value = ""))

        // then
        assert(live == mapOf("type" to "text"))
    }

    @Test
    fun `should keep the markup when the live value was not read`() {
        // given
        val attributes = mapOf("type" to "text", "value" to "preset")

        // when
        val live = formControlAttributes("input", attributes, FormControlState(value = null))

        // then
        assert(live == attributes)
    }

    @Test
    fun `should keep the markup value of inputs whose value is not user input`() {
        listOf("file", "submit", "reset", "button", "image", "hidden").forEach { type ->
            // given
            val attributes = mapOf("type" to type, "value" to "markup")

            // when
            val live = formControlAttributes("input", attributes, FormControlState(value = "live"))

            // then
            assert(live == attributes)
        }
    }

    @Test
    fun `should tick and untick checkboxes and radios by their live state`() {
        listOf("checkbox", "radio", "CheckBox").forEach { type ->
            // when
            val ticked = formControlAttributes(
                "input", mapOf("type" to type), FormControlState(checked = true)
            )
            val unticked = formControlAttributes(
                "input", mapOf("type" to type, "checked" to ""), FormControlState(checked = false)
            )

            // then
            assert(ticked == mapOf("type" to type, "checked" to ""))
            assert(unticked == mapOf("type" to type))
        }
    }

    @Test
    fun `should select and unselect options by their live state`() {
        // when
        val selected = formControlAttributes(
            "option", mapOf("value" to "g"), FormControlState(selected = true)
        )
        val unselected = formControlAttributes(
            "option", mapOf("value" to "r", "selected" to ""), FormControlState(selected = false)
        )

        // then
        assert(selected == mapOf("value" to "g", "selected" to ""))
        assert(unselected == mapOf("value" to "r"))
    }

    @Test
    fun `should leave other elements untouched`() {
        // given
        val attributes = mapOf("value" to "3", "max" to "10")

        // when
        val live = formControlAttributes("progress", attributes, FormControlState(value = "9"))

        // then
        assert(live == attributes)
        assert(!hasLiveFormState("progress"))
        assert(hasLiveFormState("input") && hasLiveFormState("textarea") && hasLiveFormState("option"))
    }

    @Test
    fun `should redact a password and mark it filled`() {
        // given
        val attributes = mapOf("type" to "password", "value" to "markup-secret")

        // when
        val live = formControlAttributes("input", attributes, FormControlState(value = "typed"))

        // then
        assert(live == mapOf("type" to "password", redacted to "filled"))
    }

    @Test
    fun `should mark an empty password as empty`() {
        // when
        val live = formControlAttributes(
            "input", mapOf("type" to "password"), FormControlState(value = "")
        )

        // then
        assert(live == mapOf("type" to "password", redacted to "empty"))
    }

    @Test
    fun `should judge a password filled by its markup value when the live value was not read`() {
        // when
        val live = formControlAttributes(
            "input", mapOf("type" to "password", "value" to "s"), FormControlState(value = null)
        )

        // then
        assert(live == mapOf("type" to "password", redacted to "filled"))
    }

    @Test
    fun `should redact a text input whose autocomplete names a secret`() {
        SECRET_AUTOCOMPLETE_TOKENS.forEach { token ->
            // given
            val attributes = mapOf("type" to "text", "autocomplete" to token)

            // when
            val live = formControlAttributes("input", attributes, FormControlState(value = "4111"))

            // then
            assert(live == attributes + (redacted to "filled"))
        }
    }

    @Test
    fun `should find a secret token among the other autocomplete tokens`() {
        // given - a password revealed by a "show password" toggle
        val attributes = mapOf("type" to "text", "autocomplete" to "section-login  Current-Password")

        // when
        val live = formControlAttributes("input", attributes, FormControlState(value = "hunter2"))

        // then
        assert(live == attributes + (redacted to "filled"))
    }

    @Test
    fun `should not redact a field whose autocomplete names no secret`() {
        // given
        val attributes = mapOf("type" to "text", "autocomplete" to "cc-name username")

        // when
        val live = formControlAttributes("input", attributes, FormControlState(value = "Alice"))

        // then
        assert(live == attributes + ("value" to "Alice"))
    }

    @Test
    fun `should redact a text input the page renders masked`() {
        // when
        val live = formControlAttributes(
            "input", mapOf("type" to "text"), FormControlState(value = "1234", masked = true)
        )

        // then
        assert(live == mapOf("type" to "text", redacted to "filled"))
    }

    @Test
    fun `should emit the live value of a textarea in place of its children`() {
        // when
        val text = formControlText("textarea", emptyMap(), FormControlState(value = "new\nbio"))
        val empty = formControlText("textarea", emptyMap(), FormControlState(value = ""))
        val unread = formControlText("textarea", emptyMap(), FormControlState(value = null))
        val other = formControlText("input", emptyMap(), FormControlState(value = "x"))

        // then
        assert(text == "new\nbio")
        assert(empty == "")
        assert(unread == null)
        assert(other == null)
    }

    @Test
    fun `should redact a secret textarea`() {
        // given
        val attributes = mapOf("autocomplete" to "one-time-code")
        val state = FormControlState(value = "123456")

        // when
        val live = formControlAttributes("textarea", attributes, state)
        val text = formControlText("textarea", attributes, state)

        // then
        assert(live == attributes + (redacted to "filled"))
        assert(text == "")
    }

    @Test
    fun `should redact a masked textarea`() {
        // given
        val state = FormControlState(value = "", masked = true)

        // when
        val live = formControlAttributes("textarea", emptyMap(), state)
        val text = formControlText("textarea", emptyMap(), state)

        // then
        assert(live == mapOf(redacted to "empty"))
        assert(text == "")
    }

}
