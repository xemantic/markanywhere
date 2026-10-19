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

package com.xemantic.markanywhere.browse

import com.xemantic.kotlin.test.assert
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.dump.AccessibilityAnnotations.DISPLAY
import com.xemantic.markanywhere.dump.AccessibilityAnnotations.REDACTED
import com.xemantic.markanywhere.dump.AccessibilityAnnotations.VISIBILITY
import com.xemantic.markanywhere.flow.SemanticEventScope
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import dev.kdriver.cdp.domain.Accessibility
import dev.kdriver.cdp.domain.DOMSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test

class CapturePageTest {

    @Test
    fun `should skip a frame whose document never committed`() = runTest {
        // given - a page embedding an iframe whose snapshot document holds no
        // <html> element (a frame that never committed a document), which CDP
        // still reports as a document of its own
        val strings = listOf("HTML", "BODY", "IFRAME", "#document", "src", "slow.html")
        val snapshot = DOMSnapshot.CaptureSnapshotReturn(
            documents = listOf(
                document(
                    nodeType = listOf(1, 1, 1),
                    nodeName = listOf(0, 1, 2),
                    parentIndex = listOf(-1, 0, 1),
                    backendNodeId = listOf(1, 2, 3),
                    attributes = listOf(emptyList(), emptyList(), listOf(4, 5)),
                    contentDocumentIndex = DOMSnapshot.RareIntegerData(
                        index = listOf(2),
                        value = listOf(1)
                    ),
                ),
                document(
                    nodeType = listOf(9),
                    nodeName = listOf(3),
                    parentIndex = listOf(-1),
                    backendNodeId = listOf(4),
                    attributes = listOf(emptyList()),
                ),
            ),
            strings = strings,
        )

        // when
        val events = captureEvents(
            snapshot = snapshot,
            axNodes = emptyList(),
            refAttribute = null,
            isActionable = { false },
        ).events

        // then - the empty frame is skipped, not fatal to the capture
        val outline = events.map {
            when (it) {
                is Mark -> "<${it.name}>"
                is Unmark -> "</${it.name}>"
                is Text -> it.text
            }
        }
        assert(outline == listOf("<html>", "<body>", "<iframe>", "</iframe>", "</body>", "</html>"))
        assert((events[2] as SemanticEvent.Mark)["src"] == "slow.html")
    }

    /**
     * Every element carries its computed `display`, the plain `block` default
     * included, so a stream without the annotation can only mean one that was
     * never captured — and an element that lays out no box of its own while
     * its descendants do (`display:contents`) is recorded as such, rather than
     * being indistinguishable from a `block` by an absent annotation.
     */
    @Test
    fun `should record every computed display including block and contents`() = runTest {
        // given
        val strings = mutableListOf<String>()
        fun string(value: String): Int =
            strings.indexOf(value).takeIf { it >= 0 } ?: strings.size.also { strings += value }

        val names = listOf("HTML", "BODY", "DIV", "SPAN", "#text", "B", "DIV")
        val snapshot = DOMSnapshot.CaptureSnapshotReturn(
            documents = listOf(
                document(
                    nodeType = names.map { if (it == "#text") 3 else 1 },
                    nodeName = names.map(::string),
                    nodeValue = names.map { if (it == "#text") string("US") else -1 },
                    parentIndex = listOf(-1, 0, 1, 2, 3, 2, 1),
                    backendNodeId = names.indices.map { it + 1 },
                    attributes = names.map { emptyList() },
                    // the span is `display:contents` — no box of its own, but
                    // its text is laid out — and the last div is `display:none`
                    layoutNodeIndex = listOf(0, 1, 2, 4, 5),
                    layoutStyles = listOf("block", "block", "block", "inline", "inline").map {
                        listOf(it, "visible", "none").map(::string)
                    },
                ),
            ),
            strings = strings,
        )

        // when
        val events = captureEvents(
            snapshot = snapshot,
            axNodes = emptyList(),
            refAttribute = null,
            isActionable = { false },
        ).events

        // then
        events.asFlow() sameAs semanticEvents(tagged = true) {
            "html"(DISPLAY to "block") {
                "body"(DISPLAY to "block") {
                    "div"(DISPLAY to "block") {
                        "span"(DISPLAY to "contents") { +"US" }
                        "b"(DISPLAY to "inline") { }
                    }
                    "div"(DISPLAY to "none") { }
                }
            }
        }
    }

    /**
     * An element laying out no box of its own (`display:contents`) has no
     * computed style in the layout tree, so its `visibility` is read from the
     * text it lays out — which inherits the element's own value. It is recorded
     * only where it differs from what the element would inherit: `hidden` set
     * on the element itself, or `visible` set back under a hidden parent.
     */
    @Test
    fun `should record the visibility of a display contents element from its text`() = runTest {
        // given
        val strings = mutableListOf<String>()
        fun string(value: String): Int =
            strings.indexOf(value).takeIf { it >= 0 } ?: strings.size.also { strings += value }

        val names = listOf("HTML", "BODY", "SPAN", "#text", "DIV", "SPAN", "#text", "SPAN", "#text")
        val texts = listOf("secret", "shown", "inherited")
        var textIndex = 0
        val snapshot = DOMSnapshot.CaptureSnapshotReturn(
            documents = listOf(
                document(
                    nodeType = names.map { if (it == "#text") 3 else 1 },
                    nodeName = names.map(::string),
                    nodeValue = names.map { if (it == "#text") string(texts[textIndex++]) else -1 },
                    parentIndex = listOf(-1, 0, 1, 2, 1, 4, 5, 4, 7),
                    backendNodeId = names.indices.map { it + 1 },
                    attributes = names.map { emptyList() },
                    // every span is `display:contents`: the first sets
                    // `visibility:hidden` itself, the second sets `visible`
                    // back inside a hidden div, the third inherits its
                    layoutNodeIndex = listOf(0, 1, 3, 4, 6, 8),
                    layoutStyles = listOf(
                        "block" to "visible",
                        "block" to "visible",
                        "inline" to "hidden",
                        "block" to "hidden",
                        "inline" to "visible",
                        "inline" to "hidden",
                    ).map { (display, visibility) ->
                        listOf(display, visibility, "none").map(::string)
                    },
                ),
            ),
            strings = strings,
        )

        // when
        val events = captureEvents(
            snapshot = snapshot,
            axNodes = emptyList(),
            refAttribute = null,
            isActionable = { false },
        ).events

        // then
        events.asFlow() sameAs semanticEvents(tagged = true) {
            "html"(DISPLAY to "block") {
                "body"(DISPLAY to "block") {
                    "span"(DISPLAY to "contents", VISIBILITY to "hidden") { +"secret" }
                    "div"(DISPLAY to "block", VISIBILITY to "hidden") {
                        "span"(DISPLAY to "contents", VISIBILITY to "visible") { +"shown" }
                        "span"(DISPLAY to "contents") { +"inherited" }
                    }
                }
            }
        }
    }

    /**
     * `visibility:collapse` hides an element like `hidden` (a collapsed table
     * row is not painted), so it is recorded as `hidden`, on a laid-out
     * element and through the text of a `display:contents` one alike.
     */
    @Test
    fun `should record visibility collapse as hidden`() = runTest {
        // given
        val strings = mutableListOf<String>()
        fun string(value: String): Int =
            strings.indexOf(value).takeIf { it >= 0 } ?: strings.size.also { strings += value }

        val names = listOf("HTML", "BODY", "DIV", "#text", "SPAN", "#text")
        val texts = listOf("collapsed", "contents")
        var textIndex = 0
        val snapshot = DOMSnapshot.CaptureSnapshotReturn(
            documents = listOf(
                document(
                    nodeType = names.map { if (it == "#text") 3 else 1 },
                    nodeName = names.map(::string),
                    nodeValue = names.map { if (it == "#text") string(texts[textIndex++]) else -1 },
                    parentIndex = listOf(-1, 0, 1, 2, 1, 4),
                    backendNodeId = names.indices.map { it + 1 },
                    attributes = names.map { emptyList() },
                    // the div is laid out collapsed, the span is
                    // `display:contents`, its text collapsed
                    layoutNodeIndex = listOf(0, 1, 2, 3, 5),
                    layoutStyles = listOf(
                        "block" to "visible",
                        "block" to "visible",
                        "block" to "collapse",
                        "inline" to "collapse",
                        "inline" to "collapse",
                    ).map { (display, visibility) ->
                        listOf(display, visibility, "none").map(::string)
                    },
                ),
            ),
            strings = strings,
        )

        // when
        val events = captureEvents(
            snapshot = snapshot,
            axNodes = emptyList(),
            refAttribute = null,
            isActionable = { false },
        ).events

        // then
        events.asFlow() sameAs semanticEvents(tagged = true) {
            "html"(DISPLAY to "block") {
                "body"(DISPLAY to "block") {
                    "div"(DISPLAY to "block", VISIBILITY to "hidden") { +"collapsed" }
                    "span"(DISPLAY to "contents", VISIBILITY to "hidden") { +"contents" }
                }
            }
        }
    }

    /**
     * Form controls are captured with the live state the snapshot reports
     * beside the markup's attributes, through the rules shared with the
     * in-page walker — the redaction of a password and of a control the page
     * renders masked (its computed `-webkit-text-security`) among them.
     */
    @Test
    fun `should capture form controls with their live state`() = runTest {
        // given
        val strings = mutableListOf<String>()
        fun string(value: String): Int =
            strings.indexOf(value).takeIf { it >= 0 } ?: strings.size.also { strings += value }
        fun attributes(vararg pairs: Pair<String, String>): List<Int> =
            pairs.flatMap { (name, value) -> listOf(string(name), string(value)) }

        val names = listOf(
            "HTML", "BODY", "INPUT", "INPUT", "INPUT", "TEXTAREA", "#text",
            "SELECT", "OPTION", "OPTION", "INPUT", "INPUT"
        )
        val snapshot = DOMSnapshot.CaptureSnapshotReturn(
            documents = listOf(
                document(
                    nodeType = names.map { if (it == "#text") 3 else 1 },
                    nodeName = names.map(::string),
                    nodeValue = names.map { if (it == "#text") string("old bio") else -1 },
                    parentIndex = listOf(-1, 0, 1, 1, 1, 1, 5, 1, 7, 7, 1, 1),
                    backendNodeId = names.indices.map { it + 1 },
                    attributes = listOf(
                        emptyList(),
                        emptyList(),
                        attributes("type" to "text", "value" to "preset"),
                        attributes("type" to "password", "value" to "markup-secret"),
                        attributes("type" to "text"),
                        emptyList(),
                        emptyList(),
                        emptyList(),
                        attributes("value" to "r", "selected" to ""),
                        attributes("value" to "g"),
                        attributes("type" to "checkbox"),
                        attributes("type" to "submit", "value" to "Send"),
                    ),
                    inputValue = DOMSnapshot.RareStringData(
                        index = listOf(2, 3, 4, 10, 11),
                        value = listOf("Alice", "typed", "1234", "on", "Send").map(::string)
                    ),
                    textValue = DOMSnapshot.RareStringData(
                        index = listOf(5),
                        value = listOf(string("new bio"))
                    ),
                    inputChecked = DOMSnapshot.RareBooleanData(index = listOf(10)),
                    optionSelected = DOMSnapshot.RareBooleanData(index = listOf(9)),
                    // every element laid out, only the third input masked
                    layoutNodeIndex = names.indices.filter { names[it] != "#text" },
                    layoutStyles = names.indices.filter { names[it] != "#text" }.map {
                        listOf("block", "visible", if (it == 4) "disc" else "none").map(::string)
                    },
                ),
            ),
            strings = strings,
        )

        // when
        val events = captureEvents(
            snapshot = snapshot,
            axNodes = emptyList(),
            refAttribute = null,
            isActionable = { false },
        ).events

        // then
        events.asFlow() sameAs semanticEvents(tagged = true) {
            "html"(DISPLAY to "block") {
                "body"(DISPLAY to "block") {
                    "input"("type" to "text", "value" to "Alice", DISPLAY to "block") { }
                    "input"("type" to "password", REDACTED to "filled", DISPLAY to "block") { }
                    "input"("type" to "text", REDACTED to "filled", DISPLAY to "block") { }
                    "textarea"(DISPLAY to "block") { +"new bio" }
                    "select"(DISPLAY to "block") {
                        "option"("value" to "r", DISPLAY to "block") { }
                        "option"("value" to "g", "selected" to "", DISPLAY to "block") { }
                    }
                    "input"("type" to "checkbox", "checked" to "", DISPLAY to "block") { }
                    "input"("type" to "submit", "value" to "Send", DISPLAY to "block") { }
                }
            }
        }
    }

    /**
     * Which option a secret select (a card expiry) has chosen is the secret:
     * no option keeps `selected`, markup or live, and the select says only
     * whether a real value is chosen — through an `<optgroup>` too.
     */
    @Test
    fun `should redact which option a secret select has chosen`() = runTest {
        // given
        val strings = mutableListOf<String>()
        fun string(value: String): Int =
            strings.indexOf(value).takeIf { it >= 0 } ?: strings.size.also { strings += value }
        fun attributes(vararg pairs: Pair<String, String>): List<Int> =
            pairs.flatMap { (name, value) -> listOf(string(name), string(value)) }

        val names = listOf("HTML", "BODY", "SELECT", "OPTION", "OPTGROUP", "OPTION")
        val snapshot = DOMSnapshot.CaptureSnapshotReturn(
            documents = listOf(
                document(
                    nodeType = names.map { 1 },
                    nodeName = names.map(::string),
                    parentIndex = listOf(-1, 0, 1, 2, 2, 4),
                    backendNodeId = names.indices.map { it + 1 },
                    attributes = listOf(
                        emptyList(),
                        emptyList(),
                        attributes("autocomplete" to "cc-exp-month"),
                        attributes("value" to "", "selected" to ""),
                        attributes("label" to "Summer"),
                        attributes("value" to "08"),
                    ),
                    optionSelected = DOMSnapshot.RareBooleanData(index = listOf(5)),
                    // every element laid out as a block
                    layoutNodeIndex = names.indices.toList(),
                    layoutStyles = names.map {
                        listOf("block", "visible", "none").map(::string)
                    },
                ),
            ),
            strings = strings,
        )

        // when
        val events = captureEvents(
            snapshot = snapshot,
            axNodes = emptyList(),
            refAttribute = null,
            isActionable = { false },
        ).events

        // then
        events.asFlow() sameAs semanticEvents(tagged = true) {
            "html"(DISPLAY to "block") {
                "body"(DISPLAY to "block") {
                    "select"("autocomplete" to "cc-exp-month", REDACTED to "filled", DISPLAY to "block") {
                        "option"("value" to "", DISPLAY to "block") { }
                        "optgroup"("label" to "Summer", DISPLAY to "block") {
                            "option"("value" to "08", DISPLAY to "block") { }
                        }
                    }
                }
            }
        }
    }

    /**
     * A text box referenced by `aria-labelledby` contributes its *value* to the
     * computed accessible name, so resolving that name into a synthetic
     * `aria-label` would carry a secret field's value past its redaction —
     * whether the secret is a target itself or sits inside one. The value is
     * taken out of the name, the label text kept. Issue #101.
     */
    @Test
    fun `should leave a secret control out of a resolved accessible name`() = runTest {
        // given
        val page = NamedNodes(
            Node("SPAN", parent = 1, attributes = mapOf("id" to "lbl")),
            Node("#text", parent = 2, text = "Card number"),
            Node(
                "INPUT", parent = 1, value = "4111111111111111", attributes = mapOf(
                    "id" to "cc", "autocomplete" to "cc-number", "aria-labelledby" to "lbl cc"
                )
            ),
            Node("DIV", parent = 1, attributes = mapOf("id" to "wrap")),
            Node("INPUT", parent = 5, value = "1234", masked = true, attributes = mapOf("type" to "text")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "wrap")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to " lbl ")),
        )
        // Blink's computed names by node index, the referenced field's value included
        val axNames = mapOf(
            4 to "Card number 4111111111111111",
            7 to "1234",
            8 to "Card number",
        )

        // when
        val events = page.capture(axNames)

        // then - the label text survives, the value never does
        events sameAs page.expected {
            "span"("id" to "lbl", DISPLAY to "block") { +"Card number" }
            "input"(
                "id" to "cc",
                "autocomplete" to "cc-number",
                "aria-labelledby" to "lbl cc",
                REDACTED to "filled",
                DISPLAY to "block",
                "aria-label" to "Card number"
            ) { }
            "div"("id" to "wrap", DISPLAY to "block") {
                "input"("type" to "text", REDACTED to "filled", DISPLAY to "block") { }
            }
            "button"("aria-labelledby" to "wrap", DISPLAY to "block") { }
            "button"("aria-labelledby" to " lbl ", DISPLAY to "block", "aria-label" to "Card number") { }
        }
    }

    /**
     * A name that holds no secret is resolved as Blink computed it, even when
     * a secret control sits inside the named element — a sign-in form's
     * password field.
     */
    @Test
    fun `should keep a computed name that holds no secret`() = runTest {
        // given
        val page = NamedNodes(
            Node("FORM", parent = 1, attributes = mapOf("aria-labelledby" to "h")),
            Node("H2", parent = 2, attributes = mapOf("id" to "h")),
            Node("IMG", parent = 3, attributes = mapOf("alt" to "Acme")),
            Node("#text", parent = 3, text = " Sign in"),
            Node("INPUT", parent = 2, value = "hunter2", attributes = mapOf("type" to "password")),
        )
        val axNames = mapOf(2 to "Acme Sign in")

        // when
        val events = page.capture(axNames)

        // then
        events sameAs page.expected {
            "form"("aria-labelledby" to "h", DISPLAY to "block", "aria-label" to "Acme Sign in") {
                "h2"("id" to "h", DISPLAY to "block") {
                    "img"("alt" to "Acme", DISPLAY to "block") { }
                    +" Sign in"
                }
                "input"("type" to "password", REDACTED to "filled", DISPLAY to "block") { }
            }
        }
    }

    /**
     * The accessible-name computation reaches a field's value along paths no
     * DOM walk follows — `aria-owns`, a descendant's own `aria-labelledby`,
     * the `<label>` of a form-associated custom element, the element's content
     * when its references resolve to nothing — so the name Blink computed is
     * checked for the secret itself, whichever path brought it.
     */
    @Test
    fun `should take a secret out of a name whatever path brought it`() = runTest {
        // given
        val page = NamedNodes(
            Node("INPUT", parent = 1, value = "123", attributes = mapOf("id" to "cvc", "autocomplete" to "cc-csc")),
            Node("SPAN", parent = 1, attributes = mapOf("id" to "lbl", "aria-owns" to "cvc")),
            Node("#text", parent = 3, text = "Code"),
            Node("DIV", parent = 1, attributes = mapOf("aria-labelledby" to "lbl")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "missing")),
            Node("SPAN", parent = 6, attributes = mapOf("aria-labelledby" to "cvc")),
            Node("LABEL", parent = 1),
            Node("#text", parent = 8, text = "Security code "),
            Node("X-PAY-FIELD", parent = 8, attributes = mapOf("aria-labelledby" to "none")),
        )
        val axNames = mapOf(5 to "Code 123", 6 to "123", 10 to "Security code 123")

        // when
        val events = page.capture(axNames)

        // then
        events sameAs page.expected {
            "input"("id" to "cvc", "autocomplete" to "cc-csc", REDACTED to "filled", DISPLAY to "block") { }
            "span"("id" to "lbl", "aria-owns" to "cvc", DISPLAY to "block") { +"Code" }
            "div"("aria-labelledby" to "lbl", DISPLAY to "block", "aria-label" to "Code") { }
            "button"("aria-labelledby" to "missing", DISPLAY to "block") {
                "span"("aria-labelledby" to "cvc", DISPLAY to "block") { }
            }
            "label"(DISPLAY to "block") {
                +"Security code "
                "x-pay-field"("aria-labelledby" to "none", DISPLAY to "block", "aria-label" to "Security code") { }
            }
        }
    }

    /**
     * A secret glued to other text — no space between the label and the value,
     * or a short value that is only part of a longer number — cannot be told
     * apart from the text around it, so the name is withheld whole rather
     * than cut up or leaked.
     */
    @Test
    fun `should withhold a name a secret is glued into`() = runTest {
        // given
        val page = NamedNodes(
            Node("INPUT", parent = 1, value = "123", attributes = mapOf("autocomplete" to "cc-csc")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "a")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "b")),
        )
        val axNames = mapOf(3 to "CVC:123", 4 to "Order 1234")

        // when
        val events = page.capture(axNames)

        // then
        events sameAs page.expected {
            "input"("autocomplete" to "cc-csc", REDACTED to "filled", DISPLAY to "block") { }
            "button"("aria-labelledby" to "a", DISPLAY to "block") { }
            "button"("aria-labelledby" to "b", DISPLAY to "block") { }
        }
    }

    /**
     * A secret select lends a name its chosen option — by text, or by the
     * option's `label` where it has one — and a secret text area its value,
     * whitespace collapsed as in the computed name; a secret's markup value
     * counts when the live one was not read.
     */
    @Test
    fun `should take secret select options and text area values out of a name`() = runTest {
        // given
        val page = NamedNodes(
            Node("SELECT", parent = 1, attributes = mapOf("autocomplete" to "cc-exp-month")),
            Node("OPTION", parent = 2, selected = true, laidOut = false, attributes = mapOf("label" to "Mar")),
            Node("#text", parent = 3, text = "March"),
            Node("TEXTAREA", parent = 1, value = "line one\nline  two", masked = true),
            Node("INPUT", parent = 1, attributes = mapOf("autocomplete" to "cc-number", "value" to "4111")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "x")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "y")),
        )
        val axNames = mapOf(7 to "Expires Mar or March", 8 to "Memo line one line two 4111")

        // when
        val events = page.capture(axNames)

        // then
        events sameAs page.expected {
            "select"("autocomplete" to "cc-exp-month", REDACTED to "filled", DISPLAY to "block") {
                "option"("label" to "Mar", DISPLAY to "none") { +"March" }
            }
            "textarea"(REDACTED to "filled", DISPLAY to "block") { }
            "input"("autocomplete" to "cc-number", REDACTED to "filled", DISPLAY to "block") { }
            "button"("aria-labelledby" to "x", DISPLAY to "block", "aria-label" to "Expires or") { }
            "button"("aria-labelledby" to "y", DISPLAY to "block", "aria-label" to "Memo") { }
        }
    }

    /**
     * A secret select left on its placeholder option (`value=""`) holds no
     * secret — the capture marks it `empty` — so the placeholder's text is not
     * taken out of a name. Issue #121.
     */
    @Test
    fun `should keep the placeholder option of a secret select in a name`() = runTest {
        // given
        val page = NamedNodes(
            Node("SELECT", parent = 1, attributes = mapOf("autocomplete" to "cc-exp-month")),
            Node("OPTION", parent = 2, selected = true, laidOut = false, attributes = mapOf("value" to "")),
            Node("#text", parent = 3, text = "Month"),
            Node("OPTION", parent = 2, laidOut = false, attributes = mapOf("value" to "01")),
            Node("#text", parent = 5, text = "01"),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "x")),
        )
        val axNames = mapOf(7 to "Expiry Month")

        // when
        val events = page.capture(axNames)

        // then
        events sameAs page.expected {
            "select"("autocomplete" to "cc-exp-month", REDACTED to "empty", DISPLAY to "block") {
                "option"("value" to "", DISPLAY to "none") { +"Month" }
                "option"("value" to "01", DISPLAY to "none") { +"01" }
            }
            "button"("aria-labelledby" to "x", DISPLAY to "block", "aria-label" to "Expiry Month") { }
        }
    }

    /**
     * An option without a `value` submits its text with only HTML (ASCII)
     * whitespace stripped, so an NBSP option makes the select `filled`, as in
     * the in-page walker — but an NBSP is no secret to take out of a name,
     * which would otherwise blank every name holding one.
     */
    @Test
    fun `should judge an NBSP option filled without lending it to a name`() = runTest {
        // given
        val page = NamedNodes(
            Node("SELECT", parent = 1, attributes = mapOf("autocomplete" to "cc-exp-month")),
            Node("OPTION", parent = 2, selected = true, laidOut = false),
            Node("#text", parent = 3, text = " "),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "x")),
        )
        val axNames = mapOf(5 to "Expiry Month")

        // when
        val events = page.capture(axNames)

        // then
        events sameAs page.expected {
            "select"("autocomplete" to "cc-exp-month", REDACTED to "filled", DISPLAY to "block") {
                "option"(DISPLAY to "none") { +" " }
            }
            "button"("aria-labelledby" to "x", DISPLAY to "block", "aria-label" to "Expiry Month") { }
        }
    }

    /**
     * A snapshot without the `inputChecked` / `optionSelected` rare data has
     * not said which boxes are ticked or options chosen, so the markup's
     * `checked` / `selected` is kept rather than cleared. Issue #103.
     */
    @Test
    fun `should keep the markup checked and selected when the snapshot omits them`() = runTest {
        // given
        val strings = mutableListOf<String>()
        fun string(value: String): Int =
            strings.indexOf(value).takeIf { it >= 0 } ?: strings.size.also { strings += value }
        fun attributes(vararg pairs: Pair<String, String>): List<Int> =
            pairs.flatMap { (name, value) -> listOf(string(name), string(value)) }

        val names = listOf("HTML", "BODY", "INPUT", "INPUT", "SELECT", "OPTION", "OPTION")
        val snapshot = DOMSnapshot.CaptureSnapshotReturn(
            documents = listOf(
                document(
                    nodeType = names.map { 1 },
                    nodeName = names.map(::string),
                    parentIndex = listOf(-1, 0, 1, 1, 1, 4, 4),
                    backendNodeId = names.indices.map { it + 1 },
                    attributes = listOf(
                        emptyList(),
                        emptyList(),
                        attributes("type" to "checkbox", "checked" to ""),
                        attributes("type" to "radio"),
                        emptyList(),
                        attributes("value" to "r"),
                        attributes("value" to "g", "selected" to ""),
                    ),
                    inputChecked = null,
                    optionSelected = null,
                    layoutNodeIndex = names.indices.toList(),
                    layoutStyles = names.map {
                        listOf("block", "visible", "none").map(::string)
                    },
                ),
            ),
            strings = strings,
        )

        // when
        val events = captureEvents(
            snapshot = snapshot,
            axNodes = emptyList(),
            refAttribute = null,
            isActionable = { false },
        ).events

        // then
        events.asFlow() sameAs semanticEvents(tagged = true) {
            "html"(DISPLAY to "block") {
                "body"(DISPLAY to "block") {
                    "input"("type" to "checkbox", "checked" to "", DISPLAY to "block") { }
                    "input"("type" to "radio", DISPLAY to "block") { }
                    "select"(DISPLAY to "block") {
                        "option"("value" to "r", DISPLAY to "block") { }
                        "option"("value" to "g", "selected" to "", DISPLAY to "block") { }
                    }
                }
            }
        }
    }

    /**
     * Blink names a date, time or number control from the value it displays,
     * formatted in the browser's locale — `May 2027` for `2027-05`, `987,50`
     * for `987.50`, in whatever digits the locale writes — so the value string
     * is not in the name to be cut out. A name holding any of the value's
     * numbers is withheld instead; one holding none of them is kept, and one
     * holding the value as written still has it cut out. Issue #123.
     */
    @Test
    fun `should withhold a name holding a secret value Blink formatted for display`() = runTest {
        // given
        val page = NamedNodes(
            Node("INPUT", parent = 1, value = "2027-05", attributes = mapOf("type" to "month", "autocomplete" to "cc-exp")),
            Node("INPUT", parent = 1, value = "987.50", attributes = mapOf("type" to "number", "autocomplete" to "cc-csc")),
            Node("INPUT", parent = 1, value = "2027-05-31", attributes = mapOf("type" to "DATE", "autocomplete" to "cc-exp")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "a")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "b")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "c")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "d")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "e")),
        )
        val axNames = mapOf(
            5 to "Expires May 2027 Show month picker",
            6 to "CVC 987,50",
            7 to "Expires ٣١/٠٥/٢٠٢٧",
            8 to "Step 3 of 4",
            9 to "CVC 987.50",
        )

        // when
        val events = page.capture(axNames)

        // then
        events sameAs page.expected {
            "input"("type" to "month", "autocomplete" to "cc-exp", REDACTED to "filled", DISPLAY to "block") { }
            "input"("type" to "number", "autocomplete" to "cc-csc", REDACTED to "filled", DISPLAY to "block") { }
            "input"("type" to "DATE", "autocomplete" to "cc-exp", REDACTED to "filled", DISPLAY to "block") { }
            "button"("aria-labelledby" to "a", DISPLAY to "block") { }
            "button"("aria-labelledby" to "b", DISPLAY to "block") { }
            "button"("aria-labelledby" to "c", DISPLAY to "block") { }
            "button"("aria-labelledby" to "d", DISPLAY to "block", "aria-label" to "Step 3 of 4") { }
            "button"("aria-labelledby" to "e", DISPLAY to "block", "aria-label" to "CVC") { }
        }
    }

    /**
     * Blink names a password field by its mask, a bullet per character, never
     * by its value (verified in `SecretNameTest`), so a password is no secret
     * a name can carry and an unrelated name holding the same text is kept.
     * Blink reads the `type` ASCII case-insensitively but untrimmed: a
     * `type=" password"` is a text field naming its value, which still counts.
     * Issue #122.
     */
    @Test
    fun `should not count a password value among the secrets a name can carry`() = runTest {
        // given
        val page = NamedNodes(
            Node("INPUT", parent = 1, value = "1234", attributes = mapOf("type" to "PassWord")),
            Node("INPUT", parent = 1, value = "5678", attributes = mapOf("type" to " password")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "a")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "b")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "c")),
        )
        val axNames = mapOf(
            4 to "Order 1234",
            5 to "Password ••••",
            6 to "Code 5678",
        )

        // when
        val events = page.capture(axNames)

        // then
        events sameAs page.expected {
            "input"("type" to "PassWord", REDACTED to "filled", DISPLAY to "block") { }
            "input"("type" to " password", REDACTED to "filled", DISPLAY to "block") { }
            "button"("aria-labelledby" to "a", DISPLAY to "block", "aria-label" to "Order 1234") { }
            "button"("aria-labelledby" to "b", DISPLAY to "block", "aria-label" to "Password ••••") { }
            "button"("aria-labelledby" to "c", DISPLAY to "block", "aria-label" to "Code") { }
        }
    }

    /**
     * A name is judged by the same rule as the control itself
     * ([isRedactedControl]): a field that is not laid out has no computed
     * `-webkit-text-security` in the snapshot, so unless the page reported it
     * masked (see the next test) it keeps its value — in the dump and in a
     * name alike.
     */
    @Test
    fun `should judge a field in a name as the field itself is judged`() = runTest {
        // given
        val page = NamedNodes(
            Node("INPUT", parent = 1, value = "1234", laidOut = false, attributes = mapOf(
                "id" to "pin", "type" to "text"
            )),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "pin")),
        )
        val axNames = mapOf(3 to "1234")

        // when
        val events = page.capture(axNames)

        // then
        events sameAs page.expected {
            "input"("id" to "pin", "type" to "text", "value" to "1234", DISPLAY to "none") { }
            "button"("aria-labelledby" to "pin", DISPLAY to "block", "aria-label" to "1234") { }
        }
    }

    /**
     * A field that is not laid out, which the page reported masked when asked
     * for its style (issue #100), is redacted like a laid-out masked one — its
     * value hidden in the dump and in a name alike.
     */
    @Test
    fun `should redact a field the page reports masked though it is not laid out`() = runTest {
        // given
        val page = NamedNodes(
            Node("INPUT", parent = 1, value = "1234", laidOut = false, attributes = mapOf(
                "id" to "pin", "type" to "text"
            )),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "pin")),
        )
        val axNames = mapOf(3 to "PIN 1234")

        // when - the input's backend node id is its index + 1
        val events = page.capture(axNames) { asked -> asked.filterTo(mutableSetOf()) { it == 3 } }

        // then
        events sameAs page.expected {
            "input"("id" to "pin", "type" to "text", REDACTED to "filled", DISPLAY to "none") { }
            "button"("aria-labelledby" to "pin", DISPLAY to "block", "aria-label" to "PIN") { }
        }
    }

    /**
     * The page answers the text-security lookup with a string; anything else —
     * `undefined` from a page that overrides `getComputedStyle` or
     * `getPropertyValue`, a number, an object — leaves the masking unknown,
     * and the capture fails closed.
     */
    @Test
    fun `should judge any answer but a string to the masking lookup as masked`() = runTest {
        // given
        val answers = listOf(
            JsonPrimitive("disc"),
            JsonPrimitive("none"),
            JsonPrimitive(""),
            JsonNull,
            null,
            JsonPrimitive(42),
            JsonPrimitive(false),
            JsonObject(emptyMap()),
        )

        // when
        val verdicts = answers.map(::isMaskingAnswer)

        // then
        assert(verdicts == listOf(true, false, false, true, true, true, true, true))
    }

    /**
     * A laid-out control whose style list lacks the `-webkit-text-security`
     * slot (a malformed snapshot) was never asked about — the page is queried
     * only for controls that are not laid out — so its masking is unknown and
     * the capture fails closed, redacting it.
     */
    @Test
    fun `should redact a laid-out field whose masking the snapshot does not report`() = runTest {
        // given
        val page = NamedNodes(
            Node("INPUT", parent = 1, value = "1234", textSecurityReported = false, attributes = mapOf(
                "id" to "pin", "type" to "text"
            )),
        )

        // when
        val events = page.capture(axNames = emptyMap())

        // then
        events sameAs page.expected {
            "input"("id" to "pin", "type" to "text", REDACTED to "filled", DISPLAY to "block") { }
        }
    }

    /**
     * Only a control whose masking the capture reads ([readsMaskedState]), the
     * snapshot carries no style for — one not laid out — and whose capture
     * the answer can change is asked about: a password or a field with a
     * secret `autocomplete` is redacted whatever its masking.
     */
    @Test
    fun `should ask only about the controls whose capture their masking can change`() = runTest {
        // given
        val page = NamedNodes(
            Node("INPUT", parent = 1, attributes = mapOf("type" to "text")),
            Node("INPUT", parent = 1, laidOut = false, attributes = mapOf("type" to "text")),
            Node("INPUT", parent = 1, laidOut = false, attributes = mapOf("type" to "checkbox")),
            Node("TEXTAREA", parent = 1, laidOut = false),
            Node("SELECT", parent = 1, laidOut = false),
            Node("OPTION", parent = 6, laidOut = false),
            Node("DIV", parent = 1, laidOut = false),
            Node("INPUT", parent = 1, laidOut = false, attributes = mapOf("type" to "password")),
            Node("INPUT", parent = 1, laidOut = false, attributes = mapOf("autocomplete" to "one-time-code")),
        )
        val asked = mutableListOf<Int>()

        // when
        page.capture(axNames = emptyMap()) { asked += it; emptySet() }

        // then - by backend node id, the node index + 1
        assert(asked == listOf(4, 6))
    }

    /**
     * A computed name is trimmed as the browser reported it — non-breaking
     * spaces at its edges included — and otherwise kept as it is.
     */
    @Test
    fun `should trim a computed name`() = runTest {
        // given
        val page = NamedNodes(
            Node("SPAN", parent = 1, attributes = mapOf("id" to "lbl")),
            Node("#text", parent = 2, text = "Search\u00A0"),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "lbl")),
        )
        val axNames = mapOf(4 to " Search\u00A0")

        // when
        val events = page.capture(axNames)

        // then
        events sameAs page.expected {
            "span"("id" to "lbl", DISPLAY to "block") { +"Search\u00A0" }
            "button"("aria-labelledby" to "lbl", DISPLAY to "block", "aria-label" to "Search") { }
        }
    }

    /**
     * A computed name of non-breaking spaces alone shows nothing, yet would
     * count as a label downstream and keep an icon-only control from being
     * given one, so it is not resolved into an `aria-label`.
     */
    @Test
    fun `should not resolve a name of non-breaking spaces alone`() = runTest {
        // given
        val page = NamedNodes(
            Node("SPAN", parent = 1, attributes = mapOf("id" to "lbl")),
            Node("#text", parent = 2, text = "\u00A0"),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "lbl")),
        )
        val axNames = mapOf(4 to "\u00A0")

        // when
        val events = page.capture(axNames)

        // then
        events sameAs page.expected {
            "span"("id" to "lbl", DISPLAY to "block") { +"\u00A0" }
            "button"("aria-labelledby" to "lbl", DISPLAY to "block") { }
        }
    }

    /**
     * One node of a [NamedNodes] page, below its implicit `<html><body>`
     * (indexes 0 and 1): [parent] is a node index.
     */
    private class Node(
        val name: String,
        val parent: Int,
        val attributes: Map<String, String> = emptyMap(),
        val text: String? = null,
        val value: String? = null,
        val laidOut: Boolean = true,
        val display: String = "block",
        val masked: Boolean = false,
        val selected: Boolean = false,
        val textSecurityReported: Boolean = true,
    )

    /** A one-document snapshot of [nodes], every element laid out as a block unless told otherwise. */
    private inner class NamedNodes(vararg nodes: Node) {

        private val nodes = listOf(Node("HTML", parent = -1), Node("BODY", parent = 0)) + nodes

        /**
         * Captures the page, Blink's computed name of each node given by node
         * index in [axNames], the controls the page reported masked though not
         * laid out answered by [maskedControls].
         */
        suspend fun capture(
            axNames: Map<Int, String>,
            maskedControls: suspend (List<Int>) -> Set<Int> = { emptySet() },
        ): Flow<SemanticEvent> {
            val axNodes = axNames.map { (index, name) ->
                val backendNodeId = index + 1
                Accessibility.AXNode(
                    nodeId = backendNodeId.toString(),
                    ignored = false,
                    name = Accessibility.AXValue(
                        type = Accessibility.AXValueType.COMPUTEDSTRING,
                        value = JsonPrimitive(name)
                    ),
                    backendDOMNodeId = backendNodeId,
                )
            }
            return captureEvents(
                snapshot = snapshot(),
                axNodes = axNodes,
                refAttribute = null,
                isActionable = { false },
                maskedControls = maskedControls,
            ).events.asFlow()
        }

        /** The page as `DOMSnapshot.captureSnapshot` reports it, each node's backend id its index + 1. */
        fun snapshot(): DOMSnapshot.CaptureSnapshotReturn {
            val strings = mutableListOf<String>()
            fun string(value: String): Int =
                strings.indexOf(value).takeIf { it >= 0 } ?: strings.size.also { strings += value }
            val laidOut = nodes.indices.filter { !nodes[it].name.startsWith("#") && nodes[it].laidOut }
            val values = nodes.indices.filter { nodes[it].value != null }
            val selected = nodes.indices.filter { nodes[it].selected }
            val snapshot = DOMSnapshot.CaptureSnapshotReturn(
                documents = listOf(
                    document(
                        nodeType = nodes.map {
                            when (it.name) {
                                "#text" -> 3
                                "#document-fragment" -> 11
                                else -> 1
                            }
                        },
                        nodeName = nodes.map { string(it.name) },
                        nodeValue = nodes.map { node -> node.text?.let { string(it) } ?: -1 },
                        parentIndex = nodes.map { it.parent },
                        backendNodeId = nodes.indices.map { it + 1 },
                        attributes = nodes.map { node ->
                            node.attributes.flatMap { (name, value) -> listOf(string(name), string(value)) }
                        },
                        inputValue = DOMSnapshot.RareStringData(
                            index = values,
                            value = values.map { string(nodes[it].value!!) }
                        ),
                        optionSelected = DOMSnapshot.RareBooleanData(index = selected),
                        layoutNodeIndex = laidOut,
                        layoutStyles = laidOut.map {
                            val node = nodes[it]
                            listOfNotNull(
                                node.display,
                                "visible",
                                if (!node.textSecurityReported) null else if (node.masked) "disc" else "none"
                            ).map(::string)
                        },
                    ),
                ),
                strings = strings,
            )
            return snapshot
        }

        fun expected(
            body: suspend SemanticEventScope.() -> Unit
        ): Flow<SemanticEvent> = semanticEvents(tagged = true) {
            "html"(DISPLAY to "block") { "body"(DISPLAY to "block") { body() } }
        }

    }

    private fun document(
        nodeType: List<Int>,
        nodeName: List<Int>,
        parentIndex: List<Int>,
        backendNodeId: List<Int>,
        attributes: List<List<Int>>,
        contentDocumentIndex: DOMSnapshot.RareIntegerData? = null,
        nodeValue: List<Int> = nodeType.map { -1 },
        inputValue: DOMSnapshot.RareStringData? = null,
        textValue: DOMSnapshot.RareStringData? = null,
        inputChecked: DOMSnapshot.RareBooleanData? = null,
        optionSelected: DOMSnapshot.RareBooleanData? = null,
        layoutNodeIndex: List<Int> = emptyList(),
        layoutStyles: List<List<Int>> = emptyList(),
    ) = DOMSnapshot.DocumentSnapshot(
        documentURL = -1,
        title = -1,
        baseURL = -1,
        contentLanguage = -1,
        encodingName = -1,
        publicId = -1,
        systemId = -1,
        frameId = -1,
        nodes = DOMSnapshot.NodeTreeSnapshot(
            parentIndex = parentIndex,
            nodeType = nodeType,
            nodeName = nodeName,
            nodeValue = nodeValue,
            backendNodeId = backendNodeId,
            attributes = attributes,
            contentDocumentIndex = contentDocumentIndex,
            inputValue = inputValue,
            textValue = textValue,
            inputChecked = inputChecked,
            optionSelected = optionSelected,
        ),
        layout = DOMSnapshot.LayoutTreeSnapshot(
            nodeIndex = layoutNodeIndex,
            styles = layoutStyles,
            bounds = emptyList(),
            text = emptyList(),
            stackingContexts = DOMSnapshot.RareBooleanData(index = emptyList()),
        ),
        textBoxes = DOMSnapshot.TextBoxSnapshot(
            layoutIndex = emptyList(),
            bounds = emptyList(),
            start = emptyList(),
            length = emptyList(),
        ),
    )

}
