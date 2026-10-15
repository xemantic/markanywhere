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

import com.xemantic.markanywhere.html.spec.splitOnHtmlWhitespace

/**
 * The live state of one form control, as a capture read it from the page.
 *
 * Typing, ticking and choosing change DOM *properties*, never the *content
 * attributes* the markup declared, so a capture built on attributes alone
 * shows what the server sent rather than what the control holds. Each capture
 * reads these properties its own way — the CDP snapshot reports them as rare
 * data, the in-page walker reads them off the element — and hands them to
 * [formControlAttributes] / [formControlText], so that both apply the same
 * rules, the secret redaction above all.
 *
 * Only the fields relevant to the element are consulted (see
 * [hasLiveFormState]): [value] and [masked] for an `<input>` / `<textarea>`,
 * [value] for a `<select>`, [checked] for a checkbox or radio, [selected] and
 * [select] for an `<option>`.
 *
 * @property value the control's current `value`, or `null` when the capture
 *   could not read it (the markup is then kept as it is). For a `<select>`,
 *   the value of the option it has selected.
 * @property checked whether a checkbox or radio is ticked now.
 * @property selected whether an `<option>` is selected now, including the
 *   first option a single `<select>` selects implicitly — that is the one the
 *   form would submit.
 * @property masked whether the control renders its text masked, i.e. its
 *   computed `-webkit-text-security` is anything but `none` — how some pages
 *   hide a secret typed into a plain text input.
 * @property select for an `<option>`, the attributes of the `<select>` it
 *   belongs to, or `null` when it belongs to none (a `<datalist>` option): which
 *   option is chosen is as secret as the select it is chosen in.
 */
public class FormControlState(
    public val value: String? = null,
    public val checked: Boolean = false,
    public val selected: Boolean = false,
    public val masked: Boolean = false,
    public val select: Map<String, String>? = null,
)

/**
 * Whether an element named [name] (a lowercase HTML local name) has live state
 * a capture should read into a [FormControlState].
 */
public fun hasLiveFormState(name: String): Boolean = name in LIVE_STATE_ELEMENTS

/**
 * The [attributes] of the element named [name] with its live [state] written
 * over the markup's: an `<input>`'s current `value`, a checkbox's or radio's
 * `checked`, an `<option>`'s `selected`. Any other element's attributes are
 * returned unchanged.
 *
 * The value recorded is the one the form would submit, which is what an agent
 * checking a form needs: a `type=number` holding text that does not parse
 * records no value (the browser submits none), and a `color` / `range` input
 * records the value it holds even when the markup declared none. Types whose
 * `value` is not user input keep their markup: the buttons, `file` (which
 * reports a fake path) and `hidden`.
 *
 * A **secret** control — see [isRedactedControl] — has its value dropped, markup
 * and live alike, and carries [AccessibilityAnnotations.REDACTED] instead,
 * saying only whether it is filled, so a capture shows that the typing landed
 * without showing what was typed. For a secret `<select>` (a card expiry
 * month, say) the secret is *which* option is chosen: every one of its
 * options loses `selected`, and the select itself carries the marker —
 * `filled` once the chosen option has a non-empty value, so a `Month`
 * placeholder option with `value=""` still reads as `empty`.
 */
public fun formControlAttributes(
    name: String,
    attributes: Map<String, String>,
    state: FormControlState
): Map<String, String> = when {
    isRedactedControl(name, attributes, state) -> attributes.redacted(
        filled = !(if (name == "input") state.value ?: attributes["value"] else state.value).isNullOrEmpty()
    )

    name == "input" -> when (inputType(attributes)) {
        "checkbox", "radio" -> attributes.withFlag("checked", state.checked)
        in MARKUP_VALUE_TYPES -> attributes
        else -> when (val value = state.value) {
            null -> attributes
            "" -> attributes - "value"
            else -> attributes + ("value" to value)
        }
    }

    name == "option" -> when {
        state.select != null && isSecretControl("select", state.select, masked = false) ->
            attributes - "selected"

        else -> attributes.withFlag("selected", state.selected)
    }

    else -> attributes
}

/**
 * Whether the capture hides the value of the element named [name] as a
 * secret (see [isSecretControl]): [formControlAttributes] then drops the value
 * and marks the control with [AccessibilityAnnotations.REDACTED] instead.
 *
 * Whatever else a capture derives from a control's value — an accessible name
 * the browser composed from it, for instance — asks this rather than deciding
 * on its own, so that a value hidden on the control never surfaces elsewhere.
 * Only a text-entry `<input>`, a `<textarea>` and a `<select>` hold such a
 * value: a checkbox, a button or a `type=hidden` input keeps its markup.
 */
public fun isRedactedControl(
    name: String,
    attributes: Map<String, String>,
    state: FormControlState
): Boolean = when (name) {
    "input" -> inputType(attributes) !in UNREDACTED_INPUT_TYPES &&
            isSecretControl(name, attributes, state.masked)

    "textarea" -> isSecretControl(name, attributes, state.masked)
    "select" -> isSecretControl(name, attributes, masked = false)
    else -> false
}

/**
 * The text a capture emits as the content of the element named [name], in
 * place of its children, or `null` to walk the children as usual.
 *
 * Only a `<textarea>` has one: its child text is merely the default value from
 * the markup, so its current [FormControlState.value] stands in for it — empty
 * for a secret one (see [isSecretControl]), whose default text is no less a
 * secret than what was typed over it.
 */
public fun formControlText(
    name: String,
    attributes: Map<String, String>,
    state: FormControlState
): String? = when {
    name != "textarea" -> null
    isRedactedControl(name, attributes, state) -> ""
    else -> state.value
}

/**
 * Whether a control holds a secret: a `type=password` input, a
 * control whose `autocomplete` names a secret ([SECRET_AUTOCOMPLETE_TOKENS] —
 * the standard signal password managers and browsers read, which a page
 * usually keeps when a "show password" toggle flips `type` to `text`), or one
 * the page renders [masked].
 *
 * These are what the page *declares*; no rule can recognise a secret typed
 * into a field that declares nothing, which is why the capture promises to
 * redact the fields the page marks as secret, not every secret.
 */
internal fun isSecretControl(
    name: String,
    attributes: Map<String, String>,
    masked: Boolean
): Boolean = when (name) {
    "input" -> inputType(attributes) == "password" || masked || attributes.hasSecretAutocomplete
    "textarea" -> masked || attributes.hasSecretAutocomplete
    "select" -> attributes.hasSecretAutocomplete
    else -> false
}

/**
 * The `autocomplete` tokens that name a secret (HTML autofill field names). The
 * attribute is a space-separated list — `section-pay billing cc-number` — so
 * each token is checked on its own.
 */
internal val SECRET_AUTOCOMPLETE_TOKENS: Set<String> = setOf(
    "current-password",
    "new-password",
    "one-time-code",
    "cc-number",
    "cc-csc",
    "cc-exp",
    "cc-exp-month",
    "cc-exp-year",
)

private val LIVE_STATE_ELEMENTS = setOf("input", "textarea", "select", "option")

private val MARKUP_VALUE_TYPES = setOf(
    "file", "submit", "reset", "button", "image", "hidden"
)

// the input types whose value is never redacted: either it is not user input
// or the control's state is a tick, not a value
private val UNREDACTED_INPUT_TYPES = MARKUP_VALUE_TYPES + setOf("checkbox", "radio")

/**
 * The type of an `<input>` with these [attributes], as the redaction rules read
 * it: its `type` attribute trimmed and lowercased, empty when absent. Anything
 * else that classifies inputs — which of them contribute a value to an
 * accessible name, for instance — reads the type through this function, so it
 * never disagrees with the redaction verdict.
 */
public fun inputType(attributes: Map<String, String>): String =
    attributes["type"]?.trim()?.lowercase().orEmpty()

private val Map<String, String>.hasSecretAutocomplete: Boolean
    get() = this["autocomplete"]
        ?.lowercase()
        ?.splitOnHtmlWhitespace()
        ?.any { it in SECRET_AUTOCOMPLETE_TOKENS }
        ?: false

private fun Map<String, String>.redacted(
    filled: Boolean
): Map<String, String> = (this - "value") +
        (AccessibilityAnnotations.REDACTED to if (filled) "filled" else "empty")

private fun Map<String, String>.withFlag(
    name: String,
    set: Boolean
): Map<String, String> = when {
    set && name !in this -> this + (name to "")
    !set && name in this -> this - name
    else -> this
}
