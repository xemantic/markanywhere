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

package com.xemantic.markanywhere.html

import com.xemantic.kotlin.test.assert
import com.xemantic.kotlin.test.sameAsMarkdown
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.dump.AccessibilityAnnotations.CAPTURE
import com.xemantic.markanywhere.dump.AccessibilityAnnotations.DISPLAY
import com.xemantic.markanywhere.dump.AccessibilityAnnotations.REF
import com.xemantic.markanywhere.dump.AccessibilityAnnotations.VISIBILITY
import com.xemantic.markanywhere.flow.SemanticEventScope
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.render.renderMarkdown
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * How the operators read a `layout-3` capture (see
 * [com.xemantic.markanywhere.dump.CaptureFormat]), whose displays are left out
 * below a `none` root and for the laid-out parts of an svg — mostly as
 * equivalence with the same stream recording every display. The unmarked
 * (legacy) readings are pinned by each operator's own tests.
 */
class CaptureFormatReadingTest {

    /**
     * The same stream as a `layout-3` capture records it, given one with every
     * display recorded: none below a `none` root, and inside an `<svg>` (up
     * to a `<foreignObject>`) only a `none`. Written out here rather than
     * through the production rule, so the equivalence tests check the rule.
     */
    private fun Flow<SemanticEvent>.leaveUnrecordedDisplaysOut(): Flow<SemanticEvent> = flow {
        val regions = ArrayDeque<String?>()
        collect { event ->
            when (event) {
                is Mark -> {
                    val region = regions.lastOrNull()
                    val display = event[DISPLAY]
                    val recorded = when (region) {
                        null -> true
                        "svg" -> display == "none"
                        else -> false
                    }
                    regions.addLast(
                        when (region) {
                            "none" -> "none"
                            "svg" -> if (display == "none") "none" else if (event.name == "foreignObject") null else "svg"
                            else -> if (display == "none") "none" else if (event.name == "svg") "svg" else null
                        }
                    )
                    emit(if (recorded) event else event.copy(attributes = event.attributes - DISPLAY))
                }
                is Unmark -> {
                    regions.removeLastOrNull()
                    emit(event)
                }
                is Text -> emit(event)
            }
        }
    }

    /**
     * A page modelled on the probe a live Chrome capture was checked against,
     * every display recorded: hidden svg parts (a `display:none` group, a
     * `<title>`, a `<desc>`, a `<symbol>`'s title, an HTML child the svg never
     * renders), icon links whose only graphic hides its name, a
     * `foreignObject` holding HTML and a hidden subtree holding an svg.
     */
    private val probePage = layoutCapture {
        "p"(DISPLAY to "block") { +"C1:" }
        "svg"(DISPLAY to "inline") {
            "g"(DISPLAY to "none") { "text"(DISPLAY to "none") { +"HIDDEN-SVG-G" } }
            "text"(DISPLAY to "inline") { +"VISIBLE-SVG" }
        }
        "p"(DISPLAY to "block") {
            +"C2: "
            "a"("href" to "/x", REF to "1", DISPLAY to "inline") {
                "svg"(DISPLAY to "inline") {
                    "g"(DISPLAY to "none") { "title"(DISPLAY to "none") { +"HIDDEN-TITLE" } }
                    "path"(DISPLAY to "inline") { }
                }
            }
        }
        "p"(DISPLAY to "block") {
            +"C3: "
            "a"("href" to "/y", REF to "2", DISPLAY to "inline") {
                "svg"(DISPLAY to "inline") {
                    "title"(DISPLAY to "none") { +"OWN-TITLE" }
                    "path"(DISPLAY to "inline") { }
                }
            }
        }
        "p"(DISPLAY to "block") { +"C7:" }
        "svg"(DISPLAY to "inline") {
            "foreignObject"(DISPLAY to "inline") {
                "div"(DISPLAY to "block") {
                    +"FO-DIV "
                    "span"(DISPLAY to "none") { +"FO-HIDDEN" }
                }
            }
        }
        "p"(DISPLAY to "block") { +"C9:" }
        "svg"(DISPLAY to "inline") {
            "symbol"(DISPLAY to "inline") {
                "title"(DISPLAY to "none") { +"SYMBOL-TITLE" }
                "text"(DISPLAY to "inline") { +"SYMBOL-TEXT" }
            }
            "use"(DISPLAY to "inline") { }
        }
        "div"(DISPLAY to "none") {
            "svg"(DISPLAY to "none") { "text"(DISPLAY to "none") { +"NONE-SVG" } }
        }
        "p"(DISPLAY to "block") { +"C13:" }
        "svg"("id" to "svgx", DISPLAY to "inline") {
            "div"(DISPLAY to "none") { +"SVG-HTML-CHILD" }
        }
        "p"(DISPLAY to "block") {
            +"C15: "
            "a"("href" to "/z", REF to "7", DISPLAY to "inline") {
                "svg"(DISPLAY to "inline") {
                    "desc"(DISPLAY to "none") { +"DESC" }
                    "g"(DISPLAY to "none") { "path"(DISPLAY to "none") { } }
                }
            }
        }
    }

    @Test
    fun `should render the svg parts of a layout capture as if every display were recorded`() = runTest {
        for (svgMode in SvgMode.entries) {
            // when
            val markdown = probePage.leaveUnrecordedDisplaysOut()
                .transformHtmlToMarkdown(svgMode = svgMode).renderMarkdown()
            val expected = probePage.transformHtmlToMarkdown(svgMode = svgMode).renderMarkdown()

            // then - nothing the browser did not render reaches the output,
            // and an icon link whose graphic hides its name keeps a label
            markdown sameAsMarkdown expected
            listOf(
                "HIDDEN-SVG-G", "HIDDEN-TITLE", "SYMBOL-TITLE", "NONE-SVG",
                "SVG-HTML-CHILD", "DESC", "FO-HIDDEN", "[](ref:7:/z)"
            ).forEach { hidden -> assert(hidden !in markdown) }
            // and the rendered graphic keeps what the browser rendered
            if (svgMode == SvgMode.PRESERVE) {
                assert("VISIBLE-SVG" in markdown)
                assert("FO-DIV" in markdown)
            }
        }
    }

    /**
     * An svg's own `<title>` would name it (a screen reader reads it), but
     * Blink lays a `<title>` out as nothing, so a capture records it `none`
     * and `applyAccessibility` drops it with the other hidden parts — as it
     * did before the capture left svg displays out. Pinned as such: naming
     * an svg by its own title is a policy change of its own, not a
     * consequence of recording fewer displays.
     */
    @Test
    fun `should not name a captured svg by its own title`() = runTest {
        // when
        val resolved = probePage.leaveUnrecordedDisplaysOut()
            .transformHtmlToMarkdown(svgMode = SvgMode.RESOLVE).renderMarkdown()
        val preserved = probePage.leaveUnrecordedDisplaysOut()
            .transformHtmlToMarkdown(svgMode = SvgMode.PRESERVE).renderMarkdown()

        // then
        assert("C3: [![:svg:]()](ref:2:/y)" in resolved)
        assert("OWN-TITLE" !in preserved)
    }

    @Test
    fun `should drop a hidden svg part of a layout capture and keep the laid out ones`() = runTest {
        // given
        val input = layoutCapture {
            "svg"(DISPLAY to "inline") {
                "g" { "path" { } }
                "g"(DISPLAY to "none") {
                    "title" { +"Hidden" }
                }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs layoutCapture {
            "svg"(DISPLAY to "inline") {
                "g" { "path" { } }
            }
        }
    }

    @Test
    fun `should inherit the visibility of a hidden svg in its parts unless one is recorded`() = runTest {
        // given - inside an svg a part's visibility is recorded only where it
        // differs from what it inherits
        val input = layoutCapture {
            "svg"(DISPLAY to "inline", VISIBILITY to "hidden") {
                "text" { +"hidden" }
                "text"(VISIBILITY to "visible") { +"shown" }
            }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs layoutCapture {
            "svg"(DISPLAY to "inline") {
                "text" { +"shown" }
            }
        }
    }

    @Test
    fun `should drop the unannotated descendants of a display none root with it`() = runTest {
        // given
        val input = layoutCapture {
            "div"(DISPLAY to "none") {
                "span" { +"hidden" }
            }
            "p"(DISPLAY to "block") { +"shown" }
        }

        // when
        val output = input.applyAccessibility()

        // then
        output sameAs layoutCapture {
            "p"(DISPLAY to "block") { +"shown" }
        }
    }

    @Test
    fun `should resolve a named svg of a layout capture whatever its parts record`() = runTest {
        // given
        val input = layoutCapture {
            "svg"("aria-label" to "News", DISPLAY to "inline") {
                "g" { "path" { } }
            }
        }

        // when
        val output = input.resolveInlineGraphics()

        // then
        output sameAs layoutCapture {
            "img"("alt" to "News", DISPLAY to "inline") { }
        }
    }

    @Test
    fun `should label an icon link of a layout capture whose svg parts record nothing`() = runTest {
        // when
        val markdown = layoutCapture {
            "a"("href" to "/i", DISPLAY to "inline") {
                "svg"(DISPLAY to "inline") { "g" { "path" { } } }
            }
        }.transformHtmlToMarkdown(refMode = RefMode.STRIP).renderMarkdown()

        // then
        markdown sameAsMarkdown "[![:svg:]()](/i)"
    }

    @Test
    fun `should not separate the laid out parts of an svg of a layout capture`() = runTest {
        // given
        val input = layoutCapture {
            "p"(DISPLAY to "block") {
                "svg"(DISPLAY to "inline") {
                    "text" { +"a" }
                    "text" { +"b" }
                }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then
        output sameAs input
    }

    @Test
    fun `should read a frame below a display none root of a layout capture as laying out nothing`() = runTest {
        // given - the frame's document is part of the subtree laying out no box
        val input = layoutCapture {
            "div"(DISPLAY to "none") {
                "iframe" {
                    "html" { "body" { "span" { +"a" }; "span" { +"b" } } }
                }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then - each span reads as `none`, a box apart
        output sameAs layoutCapture {
            "div"(DISPLAY to "none") {
                "iframe" {
                    "html" {
                        "body" {
                            "span" { +"a" }
                            +" "
                            "span" { +"b" }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `should read a stream with an unknown capture marker as unmarked`() = runTest {
        // given - a format newer than this reader: the legacy inference
        // applies, as to an unmarked dump, so the absent display of the div
        // crosses no boundary
        val input = semanticEvents(tagged = true) {
            "html"(DISPLAY to "block", CAPTURE to "layout-4") {
                "body"(DISPLAY to "block") {
                    "p"(DISPLAY to "block") {
                        "span"(DISPLAY to "inline") { +"US" }
                        "div" { "span"(DISPLAY to "inline") { +"$5" } }
                    }
                }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then
        output sameAs input
    }


    /** A `layout-3` capture: the marker on the root, every recorded element a block unless told otherwise. */
    private fun layoutCapture(
        body: suspend SemanticEventScope.() -> Unit
    ) = semanticEvents(tagged = true) {
        "html"(DISPLAY to "block", CAPTURE to "layout-3") {
            "body"(DISPLAY to "block") { body() }
        }
    }

    @Test
    fun `should read an unrecorded display of a layout capture by its UA default`() = runTest {
        // given - recorded positions of a layout-3 capture all carry a display,
        // so one without (an annotation a filter consumed) is no `block` the
        // capture left out, as in an unmarked dump, but simply not known
        val input = layoutCapture {
            "p"(DISPLAY to "block") {
                "span"(DISPLAY to "inline") { +"US" }
                "div" {
                    "span"(DISPLAY to "inline") { +"$5" }
                }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then
        output sameAs layoutCapture {
            "p"(DISPLAY to "block") {
                "span"(DISPLAY to "inline") { +"US" }
                +" "
                "div" {
                    "span"(DISPLAY to "inline") { +"$5" }
                }
            }
        }
    }

    @Test
    fun `should separate below a display none root of a layout capture as if every display were recorded`() = runTest {
        // given - the capture leaves the display of every descendant of a
        // `none` root out: each reads as `none` itself, a box apart
        val input = layoutCapture {
            "div"(DISPLAY to "none") {
                "span" { +"a" }
                "span" { +"b" }
            }
        }

        // when
        val output = input.separateUnwrappedBlocks()

        // then
        output sameAs layoutCapture {
            "div"(DISPLAY to "none") {
                "span" { +"a" }
                +" "
                "span" { +"b" }
            }
        }
    }

    @Test
    fun `should not mark a dialog below a display none root of a layout capture as shown`() = runTest {
        // given - the dialog's own display is left out, the `none` of its
        // ancestor saying it lays out no box
        val input = layoutCapture {
            "div"(DISPLAY to "none") {
                "dialog" {
                    "p" { +"Closed" }
                }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then
        output sameAs semanticEvents {
            tag("dialog") {
                "p" { +"Closed" }
            }
        }
    }

    @Test
    fun `should mark a dialog of a layout capture shown from its root on`() = runTest {
        // given - the marker says the stream is a capture before any other
        // annotation does
        val input = semanticEvents(tagged = true) {
            "html"(CAPTURE to "layout-3") {
                "dialog" {
                    "p" { +"Accept cookies?" }
                }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then
        output sameAs semanticEvents {
            tag("dialog", "open" to "") {
                "p" { +"Accept cookies?" }
            }
        }
    }

    @Test
    fun `should render a select popup of a layout capture as if every display were recorded`() = runTest {
        // given - the same popup content in a layout-3 capture, which leaves
        // the display below the `none` option out, and with it recorded on each
        val unrecorded = layoutCapture {
            "select"(DISPLAY to "inline-block") {
                "option"(DISPLAY to "none") {
                    "span" { +"A" }
                    "div" { +"B" }
                }
            }
        }
        val recorded = layoutCapture {
            "select"(DISPLAY to "inline-block") {
                "option"(DISPLAY to "none") {
                    "span"(DISPLAY to "none") { +"A" }
                    "div"(DISPLAY to "none") { +"B" }
                }
            }
        }

        // when
        val markdown = unrecorded.transformHtmlToMarkdown().renderMarkdown()
        val expected = recorded.transformHtmlToMarkdown().renderMarkdown()

        // then - the popup reads as its UA default, the div a box apart
        markdown sameAsMarkdown expected
        assert("A B" in markdown)
    }

}
