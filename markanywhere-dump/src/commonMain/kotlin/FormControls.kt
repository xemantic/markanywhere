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
import com.xemantic.markanywhere.html.spec.stripAndCollapseHtmlWhitespace

/**
 * The live state of one form control, as a capture read it from the page.
 *
 * Typing, ticking and choosing change DOM *properties*, never the *content
 * attributes* the markup declared, so a capture built on attributes alone
 * shows what the server sent rather than what the control holds. Each capture
 * reads these properties its own way — the CDP snapshot reports them as rare
 * data, the in-page walker reads them off the element — and hands them to
 * [captureFormControl], so that both apply the same rules, the secret
 * redaction above all.
 *
 * Only the fields relevant to the element are consulted (see
 * [hasLiveFormState]): [value], [text] and [masked] for an `<input>` /
 * `<textarea>` ([masked] only where [readsMaskedState] says so), [value] for a
 * `<select>`, [checked] for a checkbox or radio, [selected], [select] and
 * [text] for an `<option>`. A field the capture did not read is `null` (or
 * `false` for [masked]), and the markup then speaks for it.
 *
 * @property value the control's current `value`, or `null` when the capture
 *   could not read it (the markup is then kept as it is). For a `<select>`,
 *   the value of the option it has selected.
 * @property checked whether a checkbox or radio is ticked now, or `null` when
 *   the capture could not read it (the markup's `checked` is then kept).
 * @property selected whether an `<option>` is selected now, including the
 *   first option a single `<select>` selects implicitly — that is the one the
 *   form would submit — or `null` when the capture could not read it (the
 *   markup's `selected` is then kept).
 * @property masked whether the control renders its text masked, i.e. its
 *   computed `-webkit-text-security` masks it (see [isMaskingTextSecurity]) —
 *   how some pages hide a secret typed into a plain text input.
 * @property select for an `<option>`, the attributes of the `<select>` it
 *   belongs to, or `null` when it belongs to none (a `<datalist>` option): which
 *   option is chosen is as secret as the select it is chosen in.
 * @property text the text of the element's children, which its attributes do
 *   not carry: a `<textarea>`'s default value, an `<option>`'s label — or
 *   `null` when the capture did not read it.
 */
public class FormControlState(
    public val value: String? = null,
    public val checked: Boolean? = null,
    public val selected: Boolean? = null,
    public val masked: Boolean = false,
    public val select: Map<String, String>? = null,
    public val text: String? = null,
)

/**
 * A form control as a capture records it (see [captureFormControl]).
 *
 * @property attributes the attributes to emit on the element's `mark`.
 * @property text the text to emit as the element's content in place of its
 *   children, or `null` to walk the children as usual.
 */
public class CapturedFormControl(
    public val attributes: Map<String, String>,
    public val text: String?,
)

/**
 * Whether an element named [name] (a lowercase HTML local name) has live state
 * a capture should read into a [FormControlState].
 */
public fun hasLiveFormState(name: String): Boolean = name in LIVE_STATE_ELEMENTS

/**
 * Whether [captureFormControl] reads [FormControlState.masked] of the element
 * named [name] with these markup [attributes]: only a control rendering typed
 * text can show it masked — a text-entry `<input>` or a `<textarea>`, not a
 * checkbox, a button or a `type=hidden` input. A capture computes the masked
 * state, a computed-style lookup, only where this holds; elsewhere it makes
 * no difference to the capture.
 */
public fun readsMaskedState(
    name: String,
    attributes: Map<String, String>
): Boolean = when (name) {
    "input" -> inputType(attributes) !in UNREDACTED_INPUT_TYPES
    "textarea" -> true
    else -> false
}

/**
 * Whether a computed `-webkit-text-security` [value] renders text masked:
 * anything but `none`. An absent or empty value — what a browser reports for
 * a property it does not support — masks nothing. Both captures judge the
 * style they read by this, so they cannot disagree on a field.
 */
public fun isMaskingTextSecurity(value: String?): Boolean = !value.isNullOrEmpty() && value != "none"

/**
 * The element named [name] as a capture records it: its markup [attributes]
 * with its live [state] written over them, and the text standing in for its
 * children. Any element without live state (see [hasLiveFormState]) keeps its
 * attributes and children.
 *
 * The attributes carry an `<input>`'s current `value`, a checkbox's or radio's
 * `checked`, an `<option>`'s `selected`. The value recorded is the one the form
 * would submit, which is what an agent checking a form needs: a `type=number`
 * holding text that does not parse records no value (the browser submits
 * none), and a `color` / `range` input records the value it holds even when the
 * markup declared none. Types whose `value` is not user input keep their
 * markup: the buttons, `file` (which reports a fake path) and `hidden`.
 *
 * Only a `<textarea>` has text in place of its children: its child text is
 * merely the default value from the markup, so its current
 * [FormControlState.value] stands in for it.
 *
 * A **secret** control — see [isRedactedControl] — has its value dropped, markup
 * and live alike (a secret `<textarea>` gets empty text, its default text being
 * no less a secret than what was typed over it), and carries
 * [AccessibilityAnnotations.REDACTED] instead, saying only whether it is
 * filled, so a capture shows that the typing landed without showing what was
 * typed. For a secret `<select>` (a card expiry month, say) the secret is
 * *which* option is chosen: every one of its options loses `selected`, and the
 * select itself carries the marker — `filled` once the chosen option has a
 * non-empty value, so a `Month` placeholder option with `value=""` still reads
 * as `empty`.
 */
public fun captureFormControl(
    name: String,
    attributes: Map<String, String>,
    state: FormControlState
): CapturedFormControl {
    val redacted = isRedactedControl(name, attributes, state)
    return CapturedFormControl(
        attributes = when {
            redacted -> attributes.redacted(filled = !submittedValue(name, attributes, state).isNullOrEmpty())

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
                isInSecretSelect(state) -> attributes - "selected"
                else -> attributes.withFlag("selected", state.selected)
            }

            else -> attributes
        },
        text = when {
            name != "textarea" -> null
            redacted -> ""
            else -> state.value
        }
    )
}

/**
 * Whether [isRedactedControl] can hold for an element named [name] at all,
 * judged by the name alone: a capture looking for secret controls skips
 * everything else — the options of a long `<select>` above all — before
 * building its attributes or reading its state.
 */
public fun isRedactableElement(name: String): Boolean = name in REDACTABLE_ELEMENTS

/**
 * Whether the capture hides the value of the element named [name] as a
 * secret (see [isSecretControl]): [captureFormControl] then drops the value
 * and marks the control with [AccessibilityAnnotations.REDACTED] instead.
 *
 * Only a text-entry `<input>`, a `<textarea>` and a `<select>` hold such a
 * value: a checkbox, a button or a `type=hidden` input keeps its markup. Of
 * the [state] only [FormControlState.masked] is consulted, and only where
 * [readsMaskedState] holds — so a capture can judge a control before reading
 * the rest of its state.
 */
public fun isRedactedControl(
    name: String,
    attributes: Map<String, String>,
    state: FormControlState
): Boolean = when (name) {
    "input", "textarea" -> readsMaskedState(name, attributes) && isSecretControl(name, attributes, state.masked)
    "select" -> isSecretControl(name, attributes, masked = false)
    else -> false
}

/**
 * The texts of the element named [name] that are secret: what the control
 * [captureFormControl] redacts would lend to anything else a capture derives
 * from it — an accessible name the browser composed from its value, for
 * instance — so that a value hidden on the control never surfaces elsewhere.
 * Empty for a control that holds no secret, or holds an empty one; a text of
 * whitespace alone (an NBSP option) is never lent, being nothing a name could
 * be told apart by.
 *
 * A text-entry `<input>` lends its value (the markup's when the live one was
 * not read), a `<textarea>` its value (else its default text). A `<select>`
 * lends nothing itself: its secret is which option is chosen, so each of its
 * `<option>`s lends its own text and `label` when it is chosen with a
 * non-empty value — a placeholder submitting `""` is no secret, the select
 * being marked `empty` then.
 *
 * For an `<option>` the caller must have read its [FormControlState.text]
 * whenever it may be a secret — chosen in a secret select with a `value` other
 * than `""`: its text is both the secret and, without a `value` attribute, the
 * value that decides whether it is one, so without it the answer could only be
 * fewer secrets than there are. Such a call fails with an
 * [IllegalArgumentException] rather than fail open.
 */
public fun secretValueTexts(
    name: String,
    attributes: Map<String, String>,
    state: FormControlState
): List<String> = when {
    name == "option" -> secretOptionTexts(attributes, state)
    name == "select" -> emptyList()
    isRedactedControl(name, attributes, state) -> listOfNotNull(submittedValue(name, attributes, state))
    else -> emptyList()
}.filter { it.isNotBlank() }

/** The [secretValueTexts] of an `<option>`. */
private fun secretOptionTexts(
    attributes: Map<String, String>,
    state: FormControlState
): List<String> {
    if (!isInSecretSelect(state) || !(state.selected ?: ("selected" in attributes))) return emptyList()
    // a placeholder submitting "" holds no secret, whatever its text
    if (attributes["value"] == "") return emptyList()
    requireNotNull(state.text) {
        "the text of an <option> chosen in a secret <select> must be read to tell its secret texts"
    }
    if (submittedValue("option", attributes, state).isNullOrEmpty()) return emptyList()
    return listOfNotNull(state.text, attributes["label"])
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

// the only elements isRedactedControl can hold for
private val REDACTABLE_ELEMENTS = setOf("input", "textarea", "select")

private val MARKUP_VALUE_TYPES = setOf(
    "file", "submit", "reset", "button", "image", "hidden"
)

// the input types whose value is never redacted: either it is not user input
// or the control's state is a tick, not a value
private val UNREDACTED_INPUT_TYPES = MARKUP_VALUE_TYPES + setOf("checkbox", "radio")

/**
 * The type of an `<input>` with these [attributes] as every rule here reads
 * it: trimmed and lowercased, the empty string when the markup declares none
 * (a text field). A capture deciding anything else by the type reads it
 * through this too, so it cannot disagree with the rules on a control.
 */
public fun inputType(attributes: Map<String, String>): String =
    attributes["type"]?.trim()?.lowercase().orEmpty()

/**
 * The value the control would submit, as far as the capture knows it — the
 * markup's standing in for a live one it did not read — or `null` when
 * neither is known: whether it is empty is what makes a secret control
 * `filled`.
 */
private fun submittedValue(
    name: String,
    attributes: Map<String, String>,
    state: FormControlState
): String? = when (name) {
    "input" -> state.value ?: attributes["value"]
    "textarea" -> state.value ?: state.text
    // an option without a value attribute submits its text, whitespace collapsed
    "option" -> attributes["value"] ?: state.text?.stripAndCollapseHtmlWhitespace()
    else -> state.value
}

/** Whether this option's state places it in a [secret][isRedactedControl] `<select>`. */
private fun isInSecretSelect(state: FormControlState): Boolean =
    state.select != null && isRedactedControl("select", state.select, FormControlState())

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

/** Sets or clears the boolean attribute [name] by [set], or keeps the markup's when it is `null`. */
private fun Map<String, String>.withFlag(
    name: String,
    set: Boolean?
): Map<String, String> = when {
    set == true && name !in this -> this + (name to "")
    set == false && name in this -> this - name
    else -> this
}
