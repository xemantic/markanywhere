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
import com.xemantic.markanywhere.dump.AccessibilityAnnotations.CAPTURE
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
import kotlinx.coroutines.flow.toList
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
            "html"(DISPLAY to "block", CAPTURE to "layout-3") {
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
            "html"(DISPLAY to "block", CAPTURE to "layout-3") {
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
            "html"(DISPLAY to "block", CAPTURE to "layout-3") {
                "body"(DISPLAY to "block") {
                    "div"(DISPLAY to "block", VISIBILITY to "hidden") { +"collapsed" }
                    "span"(DISPLAY to "contents", VISIBILITY to "hidden") { +"contents" }
                }
            }
        }
    }

    /**
     * Below an element recorded `none` nothing lays out a box, so the capture
     * leaves every descendant unannotated — a reader of the `layout-3` format
     * reads each of them as `none` too, without the stream repeating it.
     */
    @Test
    fun `should not record a display below a display none root`() = runTest {
        // given
        val page = NamedNodes(
            Node("DIV", parent = 1, laidOut = false),
            Node("SPAN", parent = 2, laidOut = false),
            Node("#text", parent = 3, text = "hidden"),
            Node("P", parent = 1),
        )

        // when
        val events = page.capture(axNames = emptyMap())

        // then
        events sameAs page.expected {
            "div"(DISPLAY to "none") {
                "span" { +"hidden" }
            }
            "p"(DISPLAY to "block") { }
        }
    }

    /**
     * The parts of an `<svg>` are not CSS boxes, so the capture leaves the
     * display of a laid-out part out — until a `<foreignObject>`, whose
     * children are HTML laid out by CSS again. A part laying out nothing is
     * still recorded `none`: a `display:none` group, a `<title>`, an HTML
     * child the svg never renders — each hidden, and dropped downstream with
     * its subtree, whose own displays are then left out.
     */
    @Test
    fun `should record only the parts laying out nothing inside an svg up to its foreignObject`() = runTest {
        // given
        val page = NamedNodes(
            Node("svg", parent = 1, display = "inline"),
            Node("g", parent = 2, display = "inline"),
            Node("path", parent = 3, display = "inline"),
            Node("foreignObject", parent = 2, display = "inline"),
            Node("DIV", parent = 5),
            Node("#text", parent = 6, text = "label"),
            Node("g", parent = 2, laidOut = false),
            Node("title", parent = 8, laidOut = false),
            Node("#text", parent = 9, text = "hidden"),
            Node("DIV", parent = 2, laidOut = false),
            Node("#text", parent = 11, text = "unrendered"),
        )

        // when
        val events = page.capture(axNames = emptyMap())

        // then
        events sameAs page.expected {
            "svg"(DISPLAY to "inline") {
                "g" { "path" { } }
                "foreignObject" {
                    "div"(DISPLAY to "block") { +"label" }
                }
                "g"(DISPLAY to "none") {
                    "title" { +"hidden" }
                }
                "div"(DISPLAY to "none") { +"unrendered" }
            }
        }
    }

    /**
     * A page writing the capture's own annotations into its markup would be
     * believed downstream — a faked display inside an svg would read as a
     * box of its own and escape its hidden parent's verdict, a faked ref
     * would name another element — so the capture drops every page-authored
     * `data-markanywhere-*` attribute before recording its own.
     */
    @Test
    fun `should drop capture annotations the page authored`() = runTest {
        // given
        val page = NamedNodes(
            Node("svg", parent = 1, display = "inline", visibility = "hidden"),
            Node("text", parent = 2, display = "inline", visibility = "hidden", attributes = mapOf(
                DISPLAY to "inline",
                "data-markanywhere-ref" to "9",
                "data-markanywhere-capture" to "layout-3",
                "class" to "label",
            )),
            Node("#text", parent = 3, text = "spoof"),
        )

        // when
        val events = page.capture(axNames = emptyMap())

        // then
        events sameAs page.expected {
            "svg"(DISPLAY to "inline", VISIBILITY to "hidden") {
                "text"("class" to "label") { +"spoof" }
            }
        }
    }

    /**
     * Inside an `<svg>`, where no display is recorded, a part's `visibility`
     * is recorded only where it differs from the one it inherits — the rule a
     * box-less element follows — so a reader inheriting the verdict of a part
     * without a recorded box still sees one set back to `visible`.
     */
    @Test
    fun `should record the visibility inside an svg only where it changes`() = runTest {
        // given
        val page = NamedNodes(
            Node("svg", parent = 1, display = "inline", visibility = "hidden"),
            Node("g", parent = 2, display = "inline", visibility = "hidden"),
            Node("path", parent = 3, display = "inline", visibility = "hidden"),
            Node("g", parent = 2, display = "inline", visibility = "visible"),
        )

        // when
        val events = page.capture(axNames = emptyMap())

        // then
        events sameAs page.expected {
            "svg"(DISPLAY to "inline", VISIBILITY to "hidden") {
                "g" { "path" { } }
                "g"(VISIBILITY to "visible") { }
            }
        }
    }

    /**
     * The format marker is stamped on the root of the stream alone, the main
     * document's `<html>` — a frame's document nested in it belongs to the
     * same capture — and a frame below a `display:none` root is part of the
     * subtree laying out no box, so its document is left unannotated too.
     */
    @Test
    fun `should stamp the capture format on the main document root only`() = runTest {
        // given
        val strings = listOf("HTML", "BODY", "IFRAME", "DIV", "block", "visible", "none")
        val snapshot = DOMSnapshot.CaptureSnapshotReturn(
            documents = listOf(
                document(
                    nodeType = listOf(1, 1, 1, 1, 1),
                    nodeName = listOf(0, 1, 2, 3, 2),
                    parentIndex = listOf(-1, 0, 1, 1, 3),
                    backendNodeId = listOf(1, 2, 3, 4, 5),
                    attributes = List(5) { emptyList() },
                    contentDocumentIndex = DOMSnapshot.RareIntegerData(
                        index = listOf(2, 4),
                        value = listOf(1, 2)
                    ),
                    layoutNodeIndex = listOf(0, 1, 2),
                    layoutStyles = List(3) { listOf(4, 5, 6) },
                ),
                document(
                    nodeType = listOf(1, 1),
                    nodeName = listOf(0, 1),
                    parentIndex = listOf(-1, 0),
                    backendNodeId = listOf(6, 7),
                    attributes = List(2) { emptyList() },
                    layoutNodeIndex = listOf(0, 1),
                    layoutStyles = List(2) { listOf(4, 5, 6) },
                ),
                document(
                    nodeType = listOf(1, 1),
                    nodeName = listOf(0, 1),
                    parentIndex = listOf(-1, 0),
                    backendNodeId = listOf(8, 9),
                    attributes = List(2) { emptyList() },
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
            "html"(DISPLAY to "block", CAPTURE to "layout-3") {
                "body"(DISPLAY to "block") {
                    "iframe"(DISPLAY to "block") {
                        "html"(DISPLAY to "block") { "body"(DISPLAY to "block") { } }
                    }
                    "div"(DISPLAY to "none") {
                        "iframe" {
                            "html" { "body" { } }
                        }
                    }
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
            "html"(DISPLAY to "block", CAPTURE to "layout-3") {
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
            "html"(DISPLAY to "block", CAPTURE to "layout-3") {
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
            "html"(DISPLAY to "block", CAPTURE to "layout-3") {
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
     * A target Blink reports having spelled from its own text — the text the
     * dump carries anyway — lends the name no secret, so a short secret
     * elsewhere on the page (a chosen expiry month `1`, a CVC `123`, the
     * numbers of a filled expiry date) neither cuts nor withholds it. Issue #120.
     */
    @Test
    fun `should keep a name spelled from the text of its targets whatever secrets the page holds`() = runTest {
        // given
        val page = NamedNodes(
            Node("SELECT", parent = 1, attributes = mapOf("autocomplete" to "cc-exp-month")),
            Node("OPTION", parent = 2, selected = true, laidOut = false),
            Node("#text", parent = 3, text = "1"),
            Node("INPUT", parent = 1, value = "123", attributes = mapOf("autocomplete" to "cc-csc")),
            Node("INPUT", parent = 1, value = "2027-01-01", attributes = mapOf("type" to "date", "autocomplete" to "cc-exp")),
            Node("SPAN", parent = 1, attributes = mapOf("id" to "step")),
            Node("#text", parent = 7, text = "Step "),
            Node("B", parent = 7),
            Node("#text", parent = 9, text = "1"),
            Node("#text", parent = 7, text = " of 3"),
            Node("SPAN", parent = 1, attributes = mapOf("id" to "copy")),
            Node("#text", parent = 12, text = "© 2027 Acme, 123\n  Main St"),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "step")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "step copy")),
        )
        val axNames = mapOf(
            14 to "Step 1 of 3",
            15 to "Step 1 of 3 © 2027 Acme, 123 Main St",
        )
        val labelledBy = mapOf(
            14 to listOf(7 to "Step 1 of 3"),
            15 to listOf(7 to "Step 1 of 3", 12 to "© 2027 Acme, 123 Main St"),
        )

        // when
        val events = page.capture(axNames, labelledBy)

        // then
        events sameAs page.expected {
            "select"("autocomplete" to "cc-exp-month", REDACTED to "filled", DISPLAY to "block") {
                "option"(DISPLAY to "none") { +"1" }
            }
            "input"("autocomplete" to "cc-csc", REDACTED to "filled", DISPLAY to "block") { }
            "input"("type" to "date", "autocomplete" to "cc-exp", REDACTED to "filled", DISPLAY to "block") { }
            "span"("id" to "step", DISPLAY to "block") {
                +"Step "
                "b"(DISPLAY to "block") { +"1" }
                +" of 3"
            }
            "span"("id" to "copy", DISPLAY to "block") { +"© 2027 Acme, 123\n  Main St" }
            "button"("aria-labelledby" to "step", DISPLAY to "block", "aria-label" to "Step 1 of 3") { }
            "button"(
                "aria-labelledby" to "step copy",
                DISPLAY to "block",
                "aria-label" to "Step 1 of 3 © 2027 Acme, 123 Main St"
            ) { }
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
     * A target whose text Blink built from more than its own text nodes — a
     * secret field among the targets, one it owns (`aria-owns`), a secret
     * select inside it, or an option of a secret select — is checked for every
     * secret on the page as before, while the targets that spell their own
     * text keep theirs. Issue #120.
     */
    @Test
    fun `should check a target for secrets when Blink did not spell it from its own text`() = runTest {
        // given
        val page = NamedNodes(
            Node("SPAN", parent = 1, attributes = mapOf("id" to "lbl")),
            Node("#text", parent = 2, text = "Code 123"),
            Node("INPUT", parent = 1, value = "123", attributes = mapOf("id" to "cvc", "autocomplete" to "cc-csc")),
            Node("SPAN", parent = 1, attributes = mapOf("id" to "own", "aria-owns" to "cvc")),
            Node("#text", parent = 5, text = "Own"),
            Node("SPAN", parent = 1, attributes = mapOf("id" to "pick")),
            Node("#text", parent = 7, text = "Pick "),
            Node("SELECT", parent = 7, attributes = mapOf("autocomplete" to "cc-exp-month")),
            Node("OPTION", parent = 9, laidOut = false, attributes = mapOf("id" to "o1")),
            Node("#text", parent = 10, text = "01"),
            Node("OPTION", parent = 9, selected = true, laidOut = false),
            Node("#text", parent = 12, text = "02"),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "lbl cvc")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "own")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "pick")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "o1")),
        )
        val axNames = mapOf(14 to "Code 123 123", 15 to "Own 123", 16 to "Pick 02", 17 to "01")
        val labelledBy = mapOf(
            14 to listOf(2 to "Code 123", 4 to "123"),
            15 to listOf(5 to "Own 123"),
            16 to listOf(7 to "Pick 02"),
            17 to listOf(10 to "01"),
        )

        // when
        val events = page.capture(axNames, labelledBy)

        // then - the first button keeps its label's own "123", the owned and
        // chosen secrets are cut, and an option of a secret select is judged
        // as its select (its "01" is no secret: "02" is chosen)
        events sameAs page.expected {
            "span"("id" to "lbl", DISPLAY to "block") { +"Code 123" }
            "input"("id" to "cvc", "autocomplete" to "cc-csc", REDACTED to "filled", DISPLAY to "block") { }
            "span"("id" to "own", "aria-owns" to "cvc", DISPLAY to "block") { +"Own" }
            "span"("id" to "pick", DISPLAY to "block") {
                +"Pick "
                "select"("autocomplete" to "cc-exp-month", REDACTED to "filled", DISPLAY to "block") {
                    "option"("id" to "o1", DISPLAY to "none") { +"01" }
                    "option"(DISPLAY to "none") { +"02" }
                }
            }
            "button"("aria-labelledby" to "lbl cvc", DISPLAY to "block", "aria-label" to "Code 123") { }
            "button"("aria-labelledby" to "own", DISPLAY to "block", "aria-label" to "Own") { }
            "button"("aria-labelledby" to "pick", DISPLAY to "block", "aria-label" to "Pick") { }
            "button"("aria-labelledby" to "o1", DISPLAY to "block", "aria-label" to "01") { }
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
     * Text a target holds but the dump does not show as visible text — inside
     * an `aria-hidden` subtree, not laid out, in a `<script>`, among the
     * options of a `<select>` — can equal the secret Blink lent from a field,
     * so it is not the target's own: the target is checked for every secret.
     * Issue #120.
     */
    @Test
    fun `should not count text the dump does not show among a target's own`() = runTest {
        // given
        val page = NamedNodes(
            Node("DIV", parent = 1, attributes = mapOf("id" to "a")),
            Node("#text", parent = 2, text = "CVC "),
            Node("INPUT", parent = 2, value = "917", attributes = mapOf("autocomplete" to "cc-csc")),
            Node("SPAN", parent = 2, attributes = mapOf("aria-hidden" to "true")),
            Node("#text", parent = 5, text = "917"),
            Node("DIV", parent = 1, attributes = mapOf("id" to "b")),
            Node("#text", parent = 7, text = "Code "),
            Node("INPUT", parent = 7, value = "638", attributes = mapOf("autocomplete" to "cc-csc")),
            Node("SCRIPT", parent = 7),
            Node("#text", parent = 10, text = "638"),
            Node("DIV", parent = 1, attributes = mapOf("id" to "c")),
            Node("#text", parent = 12, text = "PIN "),
            Node("INPUT", parent = 12, value = "13", attributes = mapOf("autocomplete" to "one-time-code")),
            Node("SPAN", parent = 12, laidOut = false),
            Node("#text", parent = 15, text = "13"),
            Node("DIV", parent = 1, attributes = mapOf("id" to "d")),
            Node("#text", parent = 17, text = "Card "),
            Node("SELECT", parent = 17),
            Node("OPTION", parent = 19, selected = true),
            Node("#text", parent = 20, text = "A"),
            Node("OPTION", parent = 19),
            Node("#text", parent = 22, text = "5556"),
            Node("INPUT", parent = 17, value = "5556", attributes = mapOf("autocomplete" to "cc-number")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "a")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "b")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "c")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "d")),
        )
        val axNames = mapOf(25 to "CVC 917", 26 to "Code 638", 27 to "PIN 13", 28 to "Card A 5556")
        val labelledBy = mapOf(
            25 to listOf(2 to "CVC 917"),
            26 to listOf(7 to "Code 638"),
            27 to listOf(12 to "PIN 13"),
            28 to listOf(17 to "Card A 5556"),
        )

        // when
        val labels = page.capture(axNames, labelledBy).toList()
            .filterIsInstance<SemanticEvent.Mark>()
            .filter { it.name == "button" }
            .map { it["aria-label"] }

        // then
        assert(labels == listOf("CVC", "Code", "PIN", "Card A"))
    }

    /**
     * Shown text Blink replaces rather than lends — inside an `inert` subtree,
     * or of an element named from elsewhere (its own `aria-labelledby` or
     * `aria-label`) — is not a target's own either, so a copy of a secret there
     * does not keep the name. Issue #120.
     */
    @Test
    fun `should not count text Blink replaces among a target's own`() = runTest {
        // given
        val page = NamedNodes(
            Node("DIV", parent = 1, attributes = mapOf("id" to "a")),
            Node("#text", parent = 2, text = "Code "),
            Node("INPUT", parent = 2, value = "919", attributes = mapOf("id" to "f", "autocomplete" to "cc-csc")),
            Node("SPAN", parent = 2, attributes = mapOf("inert" to "")),
            Node("#text", parent = 5, text = "919"),
            Node("DIV", parent = 1, attributes = mapOf("id" to "b")),
            Node("#text", parent = 7, text = "Code "),
            Node("SPAN", parent = 7, attributes = mapOf("aria-labelledby" to "f")),
            Node("#text", parent = 9, text = "919"),
            Node("DIV", parent = 1, attributes = mapOf("id" to "c")),
            Node("#text", parent = 11, text = "Code "),
            Node("SPAN", parent = 11, attributes = mapOf("aria-label" to "919")),
            Node("#text", parent = 13, text = "919"),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "a")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "b")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "c")),
        )
        val axNames = mapOf(15 to "Code 919", 16 to "Code 919", 17 to "Code 919")
        val labelledBy = mapOf(
            15 to listOf(2 to "Code 919"),
            16 to listOf(7 to "Code 919"),
            17 to listOf(11 to "Code 919"),
        )

        // when
        val labels = page.capture(axNames, labelledBy).toList()
            .filterIsInstance<SemanticEvent.Mark>()
            .filter { it.name == "button" }
            .map { it["aria-label"] }

        // then
        assert(labels == listOf("Code", "Code", "Code"))
    }

    /**
     * A name left with nothing but non-breaking spaces once a secret is taken
     * out shows nothing, so it is not resolved into an `aria-label`.
     */
    @Test
    fun `should not resolve a name left with non-breaking spaces alone`() = runTest {
        // given
        val page = NamedNodes(
            Node("SPAN", parent = 1, attributes = mapOf("id" to "a")),
            Node("#text", parent = 2, text = "\u00A0"),
            Node("INPUT", parent = 1, value = "123", attributes = mapOf("id" to "cvc", "autocomplete" to "cc-csc")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "a cvc")),
        )
        val axNames = mapOf(5 to "\u00A0 123")
        val labelledBy = mapOf(5 to listOf(2 to "\u00A0", 4 to "123"))

        // when
        val events = page.capture(axNames, labelledBy)

        // then
        events sameAs page.expected {
            "span"("id" to "a", DISPLAY to "block") { +"\u00A0" }
            "input"("id" to "cvc", "autocomplete" to "cc-csc", REDACTED to "filled", DISPLAY to "block") { }
            "button"("aria-labelledby" to "a cvc", DISPLAY to "block") { }
        }
    }

    /**
     * When the targets Blink reports do not add up to the name it computed,
     * nothing tells which part of the name came from where, so the whole name
     * is checked for every secret on the page. Issue #120.
     */
    @Test
    fun `should check the whole name when its targets do not add up to it`() = runTest {
        // given
        val page = NamedNodes(
            Node("INPUT", parent = 1, value = "123", attributes = mapOf("autocomplete" to "cc-csc")),
            Node("SPAN", parent = 1, attributes = mapOf("id" to "lbl")),
            Node("#text", parent = 3, text = "Code"),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "lbl")),
        )
        val axNames = mapOf(5 to "Code 123")
        val labelledBy = mapOf(5 to listOf(3 to "Code"))

        // when
        val events = page.capture(axNames, labelledBy)

        // then
        events sameAs page.expected {
            "input"("autocomplete" to "cc-csc", REDACTED to "filled", DISPLAY to "block") { }
            "span"("id" to "lbl", DISPLAY to "block") { +"Code" }
            "button"("aria-labelledby" to "lbl", DISPLAY to "block", "aria-label" to "Code") { }
        }
    }

    /**
     * A resolved name is always whitespace-collapsed — whether or not a secret
     * was taken out of it — so the same label renders the same whatever the
     * form state elsewhere on the page. Issue #125.
     */
    @Test
    fun `should collapse the whitespace of a resolved name whether or not a secret was taken out`() = runTest {
        // given
        val page = NamedNodes(
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "a")),
        )
        val withSecret = NamedNodes(
            Node("INPUT", parent = 1, value = "123", attributes = mapOf("autocomplete" to "cc-csc")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "a")),
            Node("BUTTON", parent = 1, attributes = mapOf("aria-labelledby" to "b")),
        )

        // when
        val events = page.capture(mapOf(2 to "Pay\t\n now"))
        val secretEvents = withSecret.capture(mapOf(3 to "Pay\t\n now", 4 to "Pay\t\n now 123"))

        // then
        events sameAs page.expected {
            "button"("aria-labelledby" to "a", DISPLAY to "block", "aria-label" to "Pay now") { }
        }
        secretEvents sameAs withSecret.expected {
            "input"("autocomplete" to "cc-csc", REDACTED to "filled", DISPLAY to "block") { }
            "button"("aria-labelledby" to "a", DISPLAY to "block", "aria-label" to "Pay now") { }
            "button"("aria-labelledby" to "b", DISPLAY to "block", "aria-label" to "Pay now") { }
        }
    }

    /**
     * A computed name is trimmed as the browser reported it — non-breaking
     * spaces at its edges included — and its whitespace collapsed (issue #125).
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
        val visibility: String = "visible",
        val masked: Boolean = false,
        val selected: Boolean = false,
        val textSecurityReported: Boolean = true,
    )

    /** A one-document snapshot of [nodes], every element laid out as a block unless told otherwise. */
    private inner class NamedNodes(vararg nodes: Node) {

        private val nodes = listOf(Node("HTML", parent = -1), Node("BODY", parent = 0)) + nodes

        /**
         * Captures the page, Blink's computed name of each node given by node
         * index in [axNames] and, in [labelledBy], the `aria-labelledby`
         * targets Blink reports the name was built from — each a target node
         * index and the text it lent — the controls the page reported masked
         * though not laid out answered by [maskedControls].
         */
        suspend fun capture(
            axNames: Map<Int, String>,
            labelledBy: Map<Int, List<Pair<Int, String>>> = emptyMap(),
            maskedControls: suspend (List<Int>) -> Set<Int> = { emptySet() },
        ): Flow<SemanticEvent> {
            val axNodes = axNames.map { (index, name) ->
                val backendNodeId = index + 1
                Accessibility.AXNode(
                    nodeId = backendNodeId.toString(),
                    ignored = false,
                    name = Accessibility.AXValue(
                        type = Accessibility.AXValueType.COMPUTEDSTRING,
                        value = JsonPrimitive(name),
                        sources = labelledBy[index]?.let { targets ->
                            listOf(
                                Accessibility.AXValueSource(
                                    type = Accessibility.AXValueSourceType.RELATEDELEMENT,
                                    attribute = "aria-labelledby",
                                    attributeValue = Accessibility.AXValue(
                                        type = Accessibility.AXValueType.IDREFLIST,
                                        relatedNodes = targets.map { (target, text) ->
                                            Accessibility.AXRelatedNode(backendDOMNodeId = target + 1, text = text)
                                        }
                                    )
                                )
                            )
                        }
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
            // a text node is laid out with its parent element, as Blink lays it out
            val laidOut = nodes.indices.filter { index ->
                val node = nodes[index]
                if (node.name == "#text") nodes.getOrNull(node.parent)?.let { it.laidOut && !it.name.startsWith("#") } == true
                else !node.name.startsWith("#") && node.laidOut
            }
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
                                node.visibility,
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
            "html"(DISPLAY to "block", CAPTURE to "layout-3") { "body"(DISPLAY to "block") { body() } }
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
