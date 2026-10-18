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
import kotlin.test.assertFailsWith

class FormControlsTest {

    private val redacted = AccessibilityAnnotations.REDACTED

    private fun capturedAttributes(
        name: String,
        attributes: Map<String, String>,
        state: FormControlState
    ): Map<String, String> = captureFormControl(name, attributes, state).attributes

    private fun capturedText(
        name: String,
        attributes: Map<String, String>,
        state: FormControlState
    ): String? = captureFormControl(name, attributes, state).text

    @Test
    fun `should write the live value over the markup value`() {
        // given
        val attributes = mapOf("type" to "text", "value" to "preset")

        // when
        val live = capturedAttributes("input", attributes, FormControlState(value = "Alice"))

        // then
        assert(live == mapOf("type" to "text", "value" to "Alice"))
    }

    @Test
    fun `should treat an input without a type as text`() {
        // when
        val live = capturedAttributes("input", emptyMap(), FormControlState(value = "x"))

        // then
        assert(live == mapOf("value" to "x"))
    }

    @Test
    fun `should drop the markup value of a field cleared by the user`() {
        // given
        val attributes = mapOf("type" to "text", "value" to "preset")

        // when
        val live = capturedAttributes("input", attributes, FormControlState(value = ""))

        // then
        assert(live == mapOf("type" to "text"))
    }

    @Test
    fun `should keep the markup when the live value was not read`() {
        // given
        val attributes = mapOf("type" to "text", "value" to "preset")

        // when
        val live = capturedAttributes("input", attributes, FormControlState(value = null))

        // then
        assert(live == attributes)
    }

    @Test
    fun `should keep the markup value of inputs whose value is not user input`() {
        listOf("file", "submit", "reset", "button", "image", "hidden").forEach { type ->
            // given
            val attributes = mapOf("type" to type, "value" to "markup")

            // when
            val live = capturedAttributes("input", attributes, FormControlState(value = "live"))

            // then
            assert(live == attributes)
        }
    }

    @Test
    fun `should tick and untick checkboxes and radios by their live state`() {
        listOf("checkbox", "radio", "CheckBox").forEach { type ->
            // when
            val ticked = capturedAttributes(
                "input", mapOf("type" to type), FormControlState(checked = true)
            )
            val unticked = capturedAttributes(
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
        val selected = capturedAttributes(
            "option", mapOf("value" to "g"), FormControlState(selected = true)
        )
        val unselected = capturedAttributes(
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
        val live = capturedAttributes("progress", attributes, FormControlState(value = "9"))

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
        val live = capturedAttributes("input", attributes, FormControlState(value = "typed"))

        // then
        assert(live == mapOf("type" to "password", redacted to "filled"))
    }

    @Test
    fun `should mark an empty password as empty`() {
        // when
        val live = capturedAttributes(
            "input", mapOf("type" to "password"), FormControlState(value = "")
        )

        // then
        assert(live == mapOf("type" to "password", redacted to "empty"))
    }

    @Test
    fun `should judge a password filled by its markup value when the live value was not read`() {
        // when
        val live = capturedAttributes(
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
            val live = capturedAttributes("input", attributes, FormControlState(value = "4111"))

            // then
            assert(live == attributes + (redacted to "filled"))
        }
    }

    @Test
    fun `should find a secret token among the other autocomplete tokens`() {
        // given - a password revealed by a "show password" toggle
        val attributes = mapOf("type" to "text", "autocomplete" to "section-login  Current-Password")

        // when
        val live = capturedAttributes("input", attributes, FormControlState(value = "hunter2"))

        // then
        assert(live == attributes + (redacted to "filled"))
    }

    @Test
    fun `should not redact a field whose autocomplete names no secret`() {
        // given
        val attributes = mapOf("type" to "text", "autocomplete" to "cc-name username")

        // when
        val live = capturedAttributes("input", attributes, FormControlState(value = "Alice"))

        // then
        assert(live == attributes + ("value" to "Alice"))
    }

    @Test
    fun `should redact a text input the page renders masked`() {
        // when
        val live = capturedAttributes(
            "input", mapOf("type" to "text"), FormControlState(value = "1234", masked = true)
        )

        // then
        assert(live == mapOf("type" to "text", redacted to "filled"))
    }

    /**
     * Both captures judge a computed `-webkit-text-security` by this one
     * check: an empty value — what a browser reports for a property it does
     * not support — masks nothing. Issue #104.
     */
    @Test
    fun `should judge a text security value masking unless it is none or absent`() {
        // when
        val verdicts = listOf("disc", "circle", "square", "none", "", null).map(::isMaskingTextSecurity)

        // then
        assert(verdicts == listOf(true, true, true, false, false, false))
    }

    /**
     * Computing the masked state costs a computed-style lookup, so a capture
     * reads it only for the controls whose capture depends on it. Issue #106.
     */
    @Test
    fun `should read the masked state only of a control that renders typed text`() {
        // given
        val typed = listOf("text", "email", "search", "tel", "url", "number", "password", "")
        val untyped = listOf("checkbox", "radio", "submit", "reset", "button", "image", "hidden", "file")

        // when
        val reads = { name: String, attributes: Map<String, String> -> readsMaskedState(name, attributes) }

        // then
        typed.forEach { assert(reads("input", mapOf("type" to it))) }
        untyped.forEach { assert(!reads("input", mapOf("type" to it))) }
        assert(reads("input", emptyMap()))
        assert(reads("input", mapOf("type" to " Text ")))
        assert(!reads("input", mapOf("type" to "CHECKBOX")))
        assert(reads("textarea", emptyMap()))
        assert(!reads("select", emptyMap()))
        assert(!reads("option", emptyMap()))
        assert(!reads("div", emptyMap()))
    }

    @Test
    fun `should capture a control that does not read the masked state the same either way`() {
        // given
        val controls = listOf(
            "input" to mapOf("type" to "checkbox", "value" to "on"),
            "input" to mapOf("type" to "radio", "value" to "a"),
            "input" to mapOf("type" to "submit", "value" to "Go"),
            "input" to mapOf("type" to "hidden", "value" to "token"),
            "input" to mapOf("type" to "file"),
            "select" to mapOf("name" to "country"),
            "option" to mapOf("value" to "PL"),
        )

        controls.forEach { (name, attributes) ->
            // when
            val unmasked = FormControlState(value = "x", checked = true, selected = true)
            val masked = FormControlState(value = "x", checked = true, selected = true, masked = true)

            // then
            assert(!readsMaskedState(name, attributes))
            assert(
                captureFormControl(name, attributes, masked).attributes ==
                        captureFormControl(name, attributes, unmasked).attributes
            )
            assert(
                captureFormControl(name, attributes, masked).text ==
                        captureFormControl(name, attributes, unmasked).text
            )
            assert(
                secretValueTexts(name, attributes, masked) ==
                        secretValueTexts(name, attributes, unmasked)
            )
        }
    }

    /**
     * A capture that could not read whether a box is ticked or an option
     * chosen keeps what the markup says, as it does for an unread value —
     * rather than unticking every box. Issue #103.
     */
    @Test
    fun `should keep the markup checked and selected when the live state was not read`() {
        // given
        val unread = FormControlState()

        // when
        val ticked = capturedAttributes("input", mapOf("type" to "checkbox", "checked" to ""), unread)
        val unticked = capturedAttributes("input", mapOf("type" to "radio"), unread)
        val chosen = capturedAttributes("option", mapOf("value" to "g", "selected" to ""), unread)
        val unchosen = capturedAttributes("option", mapOf("value" to "r"), unread)

        // then
        assert(ticked == mapOf("type" to "checkbox", "checked" to ""))
        assert(unticked == mapOf("type" to "radio"))
        assert(chosen == mapOf("value" to "g", "selected" to ""))
        assert(unchosen == mapOf("value" to "r"))
    }

    @Test
    fun `should emit the live value of a textarea in place of its children`() {
        // when
        val text = capturedText("textarea", emptyMap(), FormControlState(value = "new\nbio"))
        val empty = capturedText("textarea", emptyMap(), FormControlState(value = ""))
        val unread = capturedText("textarea", emptyMap(), FormControlState(value = null))
        val other = capturedText("input", emptyMap(), FormControlState(value = "x"))

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
        val live = capturedAttributes("textarea", attributes, state)
        val text = capturedText("textarea", attributes, state)

        // then
        assert(live == attributes + (redacted to "filled"))
        assert(text == "")
    }

    /**
     * A secret text area whose live value was not read is judged by its
     * default text, as an input is by its markup `value`, and that text is
     * hidden as well. Issue #102.
     */
    @Test
    fun `should judge a secret textarea filled by its default text when the live value was not read`() {
        // given
        val attributes = mapOf("autocomplete" to "one-time-code")

        // when
        val prefilled = captureFormControl("textarea", attributes, FormControlState(text = "123456"))
        val blank = captureFormControl("textarea", attributes, FormControlState(text = ""))

        // then
        assert(prefilled.attributes == attributes + (redacted to "filled"))
        assert(prefilled.text == "")
        assert(blank.attributes == attributes + (redacted to "empty"))
        assert(blank.text == "")
    }

    @Test
    fun `should redact a masked textarea`() {
        // given
        val state = FormControlState(value = "", masked = true)

        // when
        val live = capturedAttributes("textarea", emptyMap(), state)
        val text = capturedText("textarea", emptyMap(), state)

        // then
        assert(live == mapOf(redacted to "empty"))
        assert(text == "")
    }

    @Test
    fun `should redact which option a secret select has chosen`() {
        listOf("cc-exp-month", "cc-exp-year", "cc-exp").forEach { token ->
            // given - a card expiry picked from a select
            val select = mapOf("autocomplete" to token)

            // when
            val selectAttributes = capturedAttributes("select", select, FormControlState(value = "08"))
            val chosen = capturedAttributes(
                "option", mapOf("value" to "08"), FormControlState(selected = true, select = select)
            )
            val markupChosen = capturedAttributes(
                "option",
                mapOf("value" to "01", "selected" to ""),
                FormControlState(selected = false, select = select)
            )

            // then
            assert(selectAttributes == select + (redacted to "filled"))
            assert(chosen == mapOf("value" to "08"))
            assert(markupChosen == mapOf("value" to "01"))
        }
    }

    @Test
    fun `should mark a secret select left on its placeholder option as empty`() {
        // when
        val live = capturedAttributes(
            "select", mapOf("autocomplete" to "cc-exp-month"), FormControlState(value = "")
        )

        // then
        assert(live == mapOf("autocomplete" to "cc-exp-month", redacted to "empty"))
    }

    @Test
    fun `should keep the chosen option of a select that holds no secret`() {
        // given
        val select = mapOf("autocomplete" to "country")

        // when
        val selectAttributes = capturedAttributes("select", select, FormControlState(value = "PL"))
        val chosen = capturedAttributes(
            "option", mapOf("value" to "PL"), FormControlState(selected = true, select = select)
        )

        // then
        assert(selectAttributes == select)
        assert(chosen == mapOf("value" to "PL", "selected" to ""))
        assert(hasLiveFormState("select"))
    }

    /**
     * Anything else a capture derives from a control's value — an accessible
     * name resolved from it, say — has to know which controls hide theirs, and
     * must agree with [captureFormControl] on every one of them.
     */
    @Test
    fun `should report a control as redacted exactly when its attributes are`() {
        // given
        val controls = listOf(
            Triple("input", mapOf("type" to "password"), FormControlState(value = "hunter2")),
            Triple("input", mapOf("autocomplete" to "cc-number"), FormControlState(value = "")),
            Triple("input", mapOf("type" to "text"), FormControlState(value = "1234", masked = true)),
            Triple("input", mapOf("type" to "hidden", "autocomplete" to "one-time-code"), FormControlState()),
            Triple("input", mapOf("type" to "checkbox", "autocomplete" to "cc-csc"), FormControlState()),
            Triple("input", mapOf("type" to "text"), FormControlState(value = "Alice")),
            Triple("textarea", mapOf("autocomplete" to "one-time-code"), FormControlState(value = "x")),
            Triple("textarea", emptyMap(), FormControlState(value = "bio")),
            Triple("select", mapOf("autocomplete" to "cc-exp-month"), FormControlState(value = "08")),
            Triple("select", mapOf("autocomplete" to "country"), FormControlState(value = "PL")),
            Triple("option", emptyMap(), FormControlState(select = mapOf("autocomplete" to "cc-exp"))),
            Triple("div", mapOf("autocomplete" to "cc-number"), FormControlState()),
        )

        // when
        val verdicts = controls.map { (name, attributes, state) ->
            isRedactedControl(name, attributes, state) to
                    (redacted in capturedAttributes(name, attributes, state))
        }

        // then
        assert(verdicts.all { (redacts, marked) -> redacts == marked })
        assert(controls.all { (name, attributes, state) ->
            !isRedactedControl(name, attributes, state) || isRedactableElement(name)
        })
        assert(listOf("input", "textarea", "select").all(::isRedactableElement))
        assert(listOf("option", "div", "button").none(::isRedactableElement))
        assert(verdicts.map { it.first } == listOf(
            true, true, true, false, false, false, true, false, true, false, false, false
        ))
    }

    /**
     * Whatever else a capture derives from a control — an accessible name the
     * browser composed from its value — takes the secret texts from here, so it
     * hides exactly the values the control hides. Issue #124.
     */
    @Test
    fun `should lend the value of a secret text control as its secret`() {
        // when
        val typed = secretValueTexts(
            "input", mapOf("autocomplete" to "cc-number", "value" to "0000"), FormControlState(value = "4111")
        )
        val unread = secretValueTexts(
            "input", mapOf("autocomplete" to "cc-number", "value" to "0000"), FormControlState()
        )
        val password = secretValueTexts("input", mapOf("type" to "password"), FormControlState(value = "hunter2"))
        val textarea = secretValueTexts("textarea", emptyMap(), FormControlState(value = "a b", masked = true))
        val defaultText = secretValueTexts(
            "textarea", mapOf("autocomplete" to "one-time-code"), FormControlState(text = "123456")
        )
        val empty = secretValueTexts("input", mapOf("autocomplete" to "cc-csc"), FormControlState(value = ""))

        // then
        assert(typed == listOf("4111"))
        assert(unread == listOf("0000"))
        assert(password == listOf("hunter2"))
        assert(textarea == listOf("a b"))
        assert(defaultText == listOf("123456"))
        assert(empty.isEmpty())
    }

    @Test
    fun `should lend no secret from a control that holds none`() {
        // when
        val texts = listOf(
            secretValueTexts("input", mapOf("type" to "text"), FormControlState(value = "Alice")),
            secretValueTexts("input", mapOf("type" to "hidden", "autocomplete" to "one-time-code", "value" to "x"), FormControlState()),
            secretValueTexts("textarea", emptyMap(), FormControlState(value = "bio")),
            secretValueTexts("div", mapOf("autocomplete" to "cc-number"), FormControlState(value = "4111")),
        )

        // then
        assert(texts.all { it.isEmpty() })
    }

    /**
     * Which option a secret select has chosen is the secret, lent by the
     * option's text and `label` — the select itself lends none.
     */
    @Test
    fun `should lend the chosen option of a secret select as its secret`() {
        // given
        val select = mapOf("autocomplete" to "cc-exp-month")

        // when
        val chosen = secretValueTexts(
            "option", mapOf("value" to "03", "label" to "Mar"),
            FormControlState(selected = true, select = select, text = "March")
        )
        val unchosen = secretValueTexts(
            "option", mapOf("value" to "04"), FormControlState(selected = false, select = select, text = "April")
        )
        val markupChosen = secretValueTexts(
            "option", mapOf("value" to "05", "selected" to ""), FormControlState(select = select, text = "May")
        )
        val ofPlainSelect = secretValueTexts(
            "option", mapOf("value" to "PL"),
            FormControlState(selected = true, select = mapOf("autocomplete" to "country"), text = "Poland")
        )
        val ofDatalist = secretValueTexts("option", mapOf("value" to "x"), FormControlState(selected = true, text = "x"))
        val ofSelectItself = secretValueTexts("select", select, FormControlState(value = "03"))

        // then
        assert(chosen == listOf("March", "Mar"))
        assert(unchosen.isEmpty())
        assert(markupChosen == listOf("May"))
        assert(ofPlainSelect.isEmpty())
        assert(ofDatalist.isEmpty())
        assert(ofSelectItself.isEmpty())
    }

    /**
     * An option without a `value` attribute submits its text, so that text is
     * the secret — and a caller that did not read it cannot be answered with
     * fewer secrets than there are: it is told so instead.
     */
    @Test
    fun `should not lend fewer secrets for a chosen option whose text was not read`() {
        // given
        val select = mapOf("autocomplete" to "cc-exp-month")

        // when
        val read = secretValueTexts("option", emptyMap(), FormControlState(selected = true, select = select, text = "05"))

        // then
        assert(read == listOf("05"))
        assertFailsWith<IllegalArgumentException> {
            secretValueTexts("option", emptyMap(), FormControlState(selected = true, select = select))
        }
        assertFailsWith<IllegalArgumentException> {
            secretValueTexts("option", mapOf("value" to "05"), FormControlState(selected = true, select = select))
        }
    }

    /**
     * A secret made of whitespace alone — an NBSP option, a field holding
     * spaces — is no text to look for: taking it out of a name would blank
     * every name with a space of that kind in it.
     */
    @Test
    fun `should lend no whitespace-only secret`() {
        // given
        val select = mapOf("autocomplete" to "cc-exp-month")

        // when
        val option = secretValueTexts("option", emptyMap(), FormControlState(selected = true, select = select, text = "\u00A0"))
        val labelled = secretValueTexts(
            "option", mapOf("label" to " "), FormControlState(selected = true, select = select, text = "\u00A0May")
        )
        val input = secretValueTexts("input", mapOf("autocomplete" to "cc-csc"), FormControlState(value = " \u00A0 "))
        val textarea = secretValueTexts("textarea", mapOf("autocomplete" to "one-time-code"), FormControlState(value = "\n\t"))

        // then
        assert(option.isEmpty())
        assert(labelled == listOf("\u00A0May"))
        assert(input.isEmpty())
        assert(textarea.isEmpty())
    }

    /**
     * A placeholder option submitting an empty value (`Month`) means no secret
     * was chosen — the select is marked `empty` — so its text is no secret
     * either. Issue #121.
     */
    @Test
    fun `should lend no secret from the placeholder option of a secret select`() {
        // given
        val select = mapOf("autocomplete" to "cc-exp-month")

        // when
        val placeholder = secretValueTexts(
            "option", mapOf("value" to ""), FormControlState(selected = true, select = select, text = "Month")
        )
        val blankText = secretValueTexts(
            "option", emptyMap(), FormControlState(selected = true, select = select, text = " \n ")
        )

        // then
        assert(placeholder.isEmpty())
        assert(blankText.isEmpty())
    }

    /**
     * Every rule reads an `<input>`'s type the same way — trimmed and
     * lowercased, an absent one the empty string — and so does any capture
     * code that needs it.
     */
    @Test
    fun `should read an input type trimmed and lowercased`() {
        // when
        val types = listOf(mapOf("type" to " Month "), mapOf("type" to "DATE"), emptyMap()).map(::inputType)

        // then
        assert(types == listOf("month", "date", ""))
    }

}
