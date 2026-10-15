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
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test

class CapturePageTest {

    @Test
    fun `should skip a frame whose document never committed`() {
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
     * whether the secret is a target itself or sits inside one. The name is
     * then composed from the references that hold no secret. Issue #101.
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
     * `getElementById` returns the first match in tree order, but the snapshot
     * also holds `<template>` content (and flattened shadow trees), so the first
     * element carrying an id in the snapshot is not necessarily the one Blink
     * resolved. Every element carrying a referenced id is checked.
     */
    @Test
    fun `should check every element carrying a referenced id for a secret`() = runTest {
        // given
        val page = NamedNodes(
            Node("SPAN", parent = 1, attributes = mapOf("id" to "lbl")),
            Node("#text", parent = 2, text = "Card number"),
            Node("TEMPLATE", parent = 1),
            Node("INPUT", parent = 4, attributes = mapOf("id" to "cc")),
            Node("INPUT", parent = 1, value = "4111111111111111", attributes = mapOf(
                "id" to "cc", "autocomplete" to "cc-number"
            )),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "lbl cc")),
        )
        val axNames = mapOf(7 to "Card number 4111111111111111")

        // when
        val events = page.capture(axNames)

        // then
        events sameAs page.expected {
            "span"("id" to "lbl", DISPLAY to "block") { +"Card number" }
            "template"(DISPLAY to "block") { "input"("id" to "cc", DISPLAY to "block") { } }
            "input"("id" to "cc", "autocomplete" to "cc-number", REDACTED to "filled", DISPLAY to "block") { }
            "button"("aria-labelledby" to "lbl cc", DISPLAY to "block", "aria-label" to "Card number") { }
        }
    }

    /**
     * Blink names an element from its references whenever they yield any
     * text, so a secret elsewhere in the element — a sign-in form's password
     * field, a hidden honeypot — never reaches the name. Whether Blink's
     * references did yield text is not known for sure, so the name is
     * composed from them, which gives the same name.
     */
    @Test
    fun `should name an element from references that reach no secret`() = runTest {
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
     * When no id reference resolves, Blink falls back to the other sources of
     * a name, the element's own content among them — which takes the value of
     * a text box inside it. Which source it took is not known here, so the name
     * is withheld, unless it is the element's `title`, which holds no value.
     */
    @Test
    fun `should withhold a name that may come from content holding a secret`() = runTest {
        // given
        val page = NamedNodes(
            Node("LABEL", parent = 1, attributes = mapOf("aria-labelledby" to "missing")),
            Node("#text", parent = 2, text = "Card "),
            Node("INPUT", parent = 2, value = "4111111111111111", attributes = mapOf(
                "autocomplete" to "cc-number"
            )),
            Node("DIV", parent = 1, attributes = mapOf(
                "role" to "dialog", "aria-labelledby" to "gone", "title" to "Sign in"
            )),
            Node("INPUT", parent = 5, value = "hunter2", attributes = mapOf("type" to "password")),
        )
        val axNames = mapOf(2 to "Card 4111111111111111", 5 to "Sign in")

        // when
        val events = page.capture(axNames)

        // then
        events sameAs page.expected {
            "label"("aria-labelledby" to "missing", DISPLAY to "block") {
                +"Card "
                "input"("autocomplete" to "cc-number", REDACTED to "filled", DISPLAY to "block") { }
            }
            "div"(
                "role" to "dialog",
                "aria-labelledby" to "gone",
                "title" to "Sign in",
                DISPLAY to "block",
                "aria-label" to "Sign in"
            ) {
                "input"("type" to "password", REDACTED to "filled", DISPLAY to "block") { }
            }
        }
    }

    /**
     * A name composed without a secret follows the text alternative Blink
     * computes for each reference: a select names its chosen option, an image
     * its `alt`, an element its `aria-label`, hidden content is skipped and
     * blocks are kept apart.
     */
    @Test
    fun `should compose a name without a secret as the browser computes it`() = runTest {
        // given
        val page = NamedNodes(
            Node("DIV", parent = 1, attributes = mapOf("id" to "lbl")),
            Node("P", parent = 2),
            Node("#text", parent = 3, text = "Card"),
            Node("P", parent = 2),
            Node("#text", parent = 5, text = "expiry"),
            Node("SPAN", parent = 2, display = "inline", attributes = mapOf("aria-hidden" to "true")),
            Node("#text", parent = 7, text = "*"),
            Node("SPAN", parent = 2, laidOut = false),
            Node("#text", parent = 9, text = "(required)"),
            Node("SELECT", parent = 1, attributes = mapOf("id" to "month")),
            Node("OPTION", parent = 11, attributes = mapOf("value" to "02")),
            Node("#text", parent = 12, text = "February"),
            Node("OPTION", parent = 11, selected = true, attributes = mapOf("value" to "03")),
            Node("#text", parent = 14, text = "March"),
            Node("SPAN", parent = 1, display = "inline", attributes = mapOf("id" to "icon")),
            Node("IMG", parent = 16, display = "inline", attributes = mapOf("alt" to "Visa")),
            Node("SPAN", parent = 1, display = "inline", attributes = mapOf("id" to "hint", "aria-label" to "or later")),
            Node("#text", parent = 18, text = "≥"),
            Node("INPUT", parent = 1, value = "2031", attributes = mapOf(
                "id" to "year", "autocomplete" to "cc-exp-year"
            )),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "lbl month icon hint year")),
        )
        val axNames = mapOf(21 to "Card expiry March Visa or later 2031")

        // when
        val events = page.capture(axNames)

        // then
        events sameAs page.expected {
            "div"("id" to "lbl", DISPLAY to "block") {
                "p"(DISPLAY to "block") { +"Card" }
                "p"(DISPLAY to "block") { +"expiry" }
                "span"("aria-hidden" to "true", DISPLAY to "inline") { +"*" }
                "span"(DISPLAY to "none") { +"(required)" }
            }
            "select"("id" to "month", DISPLAY to "block") {
                "option"("value" to "02", DISPLAY to "block") { +"February" }
                "option"("value" to "03", "selected" to "", DISPLAY to "block") { +"March" }
            }
            "span"("id" to "icon", DISPLAY to "inline") {
                "img"("alt" to "Visa", DISPLAY to "inline") { }
            }
            "span"("id" to "hint", "aria-label" to "or later", DISPLAY to "inline") { +"≥" }
            "input"("id" to "year", "autocomplete" to "cc-exp-year", REDACTED to "filled", DISPLAY to "block") { }
            "button"(
                "aria-labelledby" to "lbl month icon hint year",
                DISPLAY to "block",
                "aria-label" to "Card expiry March Visa or later"
            ) { }
        }
    }

    /**
     * A name is judged by the same rule as the control itself
     * ([isRedactedControl]): a field that is not laid out has no computed
     * `-webkit-text-security`, so it is not known to be masked and keeps its
     * value — in the dump and in a name alike. Issue #100 covers reading the
     * style of such a field, which will hide both at once.
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
     * The computed name and a composed one are normalised alike: HTML
     * whitespace is stripped and collapsed, a non-breaking space is content.
     */
    @Test
    fun `should normalise a computed name as a composed one`() = runTest {
        // given
        val page = NamedNodes(
            Node("SPAN", parent = 1, attributes = mapOf("id" to "lbl")),
            Node("#text", parent = 2, text = "\u00A0Card  number "),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "lbl")),
        )
        val axNames = mapOf(4 to " \u00A0Card  number ")

        // when
        val events = page.capture(axNames)

        // then
        events sameAs page.expected {
            "span"("id" to "lbl", DISPLAY to "block") { +"\u00A0Card  number " }
            "button"("aria-labelledby" to "lbl", DISPLAY to "block", "aria-label" to "\u00A0Card number") { }
        }
    }

    /**
     * An id carried only by `<template>` content or by an element of a shadow
     * tree is not found by `getElementById`, so Blink falls back on the
     * element's content, which holds the secret. The reference counts as
     * unresolved and the name is withheld.
     */
    @Test
    fun `should withhold a name whose only reference lies outside the tree scope`() = runTest {
        // given
        val page = NamedNodes(
            Node("LABEL", parent = 1, attributes = mapOf("aria-labelledby" to "t")),
            Node("#text", parent = 2, text = "Card "),
            Node("INPUT", parent = 2, value = "4111111111111111", attributes = mapOf(
                "autocomplete" to "cc-number"
            )),
            Node("TEMPLATE", parent = 1),
            Node("SPAN", parent = 5, attributes = mapOf("id" to "t")),
            Node("#text", parent = 6, text = "Pay"),
            Node("DIV", parent = 1),
            Node("#document-fragment", parent = 8),
            Node("SPAN", parent = 9, attributes = mapOf("id" to "s")),
            Node("#text", parent = 10, text = "Pay"),
            Node("LABEL", parent = 1, attributes = mapOf("aria-labelledby" to "s")),
            Node("#text", parent = 12, text = "CVC "),
            Node("INPUT", parent = 12, value = "123", attributes = mapOf("autocomplete" to "cc-csc")),
        )
        val axNames = mapOf(2 to "Card 4111111111111111", 12 to "CVC 123")

        // when
        val events = page.capture(axNames)

        // then
        events sameAs page.expected {
            "label"("aria-labelledby" to "t", DISPLAY to "block") {
                +"Card "
                "input"("autocomplete" to "cc-number", REDACTED to "filled", DISPLAY to "block") { }
            }
            "template"(DISPLAY to "block") { "span"("id" to "t", DISPLAY to "block") { +"Pay" } }
            "div"(DISPLAY to "block") { }
            "label"("aria-labelledby" to "s", DISPLAY to "block") {
                +"CVC "
                "input"("autocomplete" to "cc-csc", REDACTED to "filled", DISPLAY to "block") { }
            }
        }
    }

    /**
     * When a referenced element is hidden its hidden content counts, but never
     * what is not rendered at all — a `type=hidden` input's value, a script's
     * source — which Blink does not take into a name either. Counting it would
     * make references that yield nothing look as if they named the element.
     */
    @Test
    fun `should leave unrendered content of a hidden reference out of a name`() = runTest {
        // given
        val page = NamedNodes(
            Node("LABEL", parent = 1, attributes = mapOf("aria-labelledby" to "h")),
            Node("#text", parent = 2, text = "Card "),
            Node("INPUT", parent = 2, value = "4111111111111111", attributes = mapOf(
                "id" to "cc", "autocomplete" to "cc-number"
            )),
            Node("DIV", parent = 1, laidOut = false, attributes = mapOf("id" to "h")),
            Node("INPUT", parent = 5, laidOut = false, attributes = mapOf("type" to "hidden", "value" to "csrf123")),
            Node("SCRIPT", parent = 5, laidOut = false),
            Node("#text", parent = 7, text = "track()"),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "h cc")),
        )
        val axNames = mapOf(2 to "Card 4111111111111111", 9 to "4111111111111111")

        // when
        val events = page.capture(axNames)

        // then
        events sameAs page.expected {
            "label"("aria-labelledby" to "h", DISPLAY to "block") {
                +"Card "
                "input"("id" to "cc", "autocomplete" to "cc-number", REDACTED to "filled", DISPLAY to "block") { }
            }
            "div"("id" to "h", DISPLAY to "none") {
                "input"("type" to "hidden", "value" to "csrf123", DISPLAY to "none") { }
                "script"(DISPLAY to "none") { +"track()" }
            }
            "button"("aria-labelledby" to "h cc", DISPLAY to "block") { }
        }
    }

    /**
     * Blink's fallback sources of a name include a labelable element's
     * `<label>`s — one pointing at it with `for`, or one wrapping it — whose
     * text takes the value of a control inside them.
     */
    @Test
    fun `should withhold a name that may come from a label holding a secret`() = runTest {
        // given
        val page = NamedNodes(
            Node("INPUT", parent = 1, value = "50", attributes = mapOf("id" to "amt", "aria-labelledby" to "gone")),
            Node("LABEL", parent = 1, attributes = mapOf("for" to "amt")),
            Node("#text", parent = 3, text = "Card "),
            Node("INPUT", parent = 3, value = "4111111111111111", attributes = mapOf("autocomplete" to "cc-number")),
            Node("LABEL", parent = 1),
            Node("#text", parent = 6, text = "CVC "),
            Node("INPUT", parent = 6, value = "123", attributes = mapOf("autocomplete" to "cc-csc")),
            Node("SELECT", parent = 6, attributes = mapOf("aria-labelledby" to "missing")),
        )
        val axNames = mapOf(2 to "Card 4111111111111111", 9 to "CVC 123")

        // when
        val events = page.capture(axNames)

        // then
        events sameAs page.expected {
            "input"("id" to "amt", "aria-labelledby" to "gone", "value" to "50", DISPLAY to "block") { }
            "label"("for" to "amt", DISPLAY to "block") {
                +"Card "
                "input"("autocomplete" to "cc-number", REDACTED to "filled", DISPLAY to "block") { }
            }
            "label"(DISPLAY to "block") {
                +"CVC "
                "input"("autocomplete" to "cc-csc", REDACTED to "filled", DISPLAY to "block") { }
                "select"("aria-labelledby" to "missing", DISPLAY to "block") { }
            }
        }
    }

    /**
     * A secret control naming itself through a reference that does not resolve
     * may get its name from its own value — a select its chosen option.
     */
    @Test
    fun `should withhold a name a secret control may take from itself`() = runTest {
        // given
        val page = NamedNodes(
            Node("SELECT", parent = 1, attributes = mapOf(
                "autocomplete" to "cc-exp-month", "aria-labelledby" to "missing"
            )),
            Node("OPTION", parent = 2, selected = true, attributes = mapOf("value" to "03")),
            Node("#text", parent = 3, text = "March"),
        )
        val axNames = mapOf(2 to "March")

        // when
        val events = page.capture(axNames)

        // then
        events sameAs page.expected {
            "select"(
                "autocomplete" to "cc-exp-month",
                "aria-labelledby" to "missing",
                REDACTED to "filled",
                DISPLAY to "block"
            ) {
                "option"("value" to "03", DISPLAY to "block") { +"March" }
            }
        }
    }

    /**
     * Everything below a `<select>` is annotated `display:none` (its popup has
     * no layout box), so a chosen option's text is read from its text nodes,
     * markup inside the option included.
     */
    @Test
    fun `should name a referenced select by its chosen option's marked-up text`() = runTest {
        // given
        val page = NamedNodes(
            Node("SELECT", parent = 1, attributes = mapOf("id" to "month")),
            Node("OPTION", parent = 2, selected = true, laidOut = false),
            Node("SPAN", parent = 3, laidOut = false),
            Node("#text", parent = 4, text = "March"),
            Node("INPUT", parent = 1, value = "2031", attributes = mapOf(
                "id" to "year", "autocomplete" to "cc-exp-year"
            )),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "month year")),
        )
        val axNames = mapOf(7 to "March 2031")

        // when
        val events = page.capture(axNames)

        // then
        events sameAs page.expected {
            "select"("id" to "month", DISPLAY to "block") {
                "option"("selected" to "", DISPLAY to "none") { "span"(DISPLAY to "none") { +"March" } }
            }
            "input"("id" to "year", "autocomplete" to "cc-exp-year", REDACTED to "filled", DISPLAY to "block") { }
            "button"("aria-labelledby" to "month year", DISPLAY to "block", "aria-label" to "March") { }
        }
    }

    /**
     * A referenced input contributes what Blink takes from it: a submit or
     * reset button its value or default label, an image button its `alt`.
     */
    @Test
    fun `should name referenced input buttons as the browser does`() = runTest {
        // given
        val page = NamedNodes(
            Node("INPUT", parent = 1, attributes = mapOf("id" to "s", "type" to "submit")),
            Node("INPUT", parent = 1, attributes = mapOf("id" to "r", "type" to "reset", "value" to "Clear")),
            Node("INPUT", parent = 1, attributes = mapOf("id" to "i", "type" to "image", "alt" to "Go")),
            Node("INPUT", parent = 1, value = "hunter2", attributes = mapOf("id" to "p", "type" to "password")),
            Node("DIV", parent = 1, attributes = mapOf("aria-labelledby" to "s r i p")),
        )
        val axNames = mapOf(6 to "Submit Clear Go hunter2")

        // when
        val events = page.capture(axNames)

        // then
        events sameAs page.expected {
            "input"("id" to "s", "type" to "submit", DISPLAY to "block") { }
            "input"("id" to "r", "type" to "reset", "value" to "Clear", DISPLAY to "block") { }
            "input"("id" to "i", "type" to "image", "alt" to "Go", DISPLAY to "block") { }
            "input"("id" to "p", "type" to "password", REDACTED to "filled", DISPLAY to "block") { }
            "div"("aria-labelledby" to "s r i p", DISPLAY to "block", "aria-label" to "Submit Clear Go") { }
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
    )

    /** A one-document snapshot of [nodes], every element laid out as a block unless told otherwise. */
    private inner class NamedNodes(vararg nodes: Node) {

        private val nodes = listOf(Node("HTML", parent = -1), Node("BODY", parent = 0)) + nodes

        /** Captures the page, Blink's computed name of each node given by node index in [axNames]. */
        fun capture(axNames: Map<Int, String>): Flow<SemanticEvent> {
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
                            listOf(nodes[it].display, "visible", if (nodes[it].masked) "disc" else "none").map(::string)
                        },
                    ),
                ),
                strings = strings,
            )
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
                snapshot = snapshot,
                axNodes = axNodes,
                refAttribute = null,
                isActionable = { false },
            ).events.asFlow()
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
