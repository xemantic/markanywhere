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
import com.xemantic.markanywhere.dump.AccessibilityAnnotations.DISPLAY
import com.xemantic.markanywhere.dump.AccessibilityAnnotations.REF
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.render.renderMarkdown
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Regression suite for the content `simplifyHtml` used to swallow together with
 * every element it did not recognise — design-system web components, and the
 * `display: contents` wrappers frameworks emit. Each test names an actionable
 * control that a captured page really has and that the pipeline dropped, ref
 * and all, before the catch-all unwrap rule was added.
 *
 * Each such control is rebuilt here as a synthetic stream mirroring the dump's
 * exact shape, so the whole output can be read at a glance; the last test is a
 * sweep over the real dumps, whose value is breadth.
 */
class UnrecognisedElementsTest {

    @Test
    fun `should keep a button wrapped in svelte-css-wrapper`() = runTest {
        // given — one "People also ask" FAQ item as the Brave SERP dump
        // captures it: a <details> whose <summary> holds the question and, in a
        // `display: contents` web component Svelte emits, the icon-only
        // disclosure button
        val faqItem = semanticEvents(tagged = true) {
            "body" {
                "div"("class" to "faq-item") {
                    "details" {
                        "summary"(
                            "title" to "Can I export markdown to PDF or HTML?",
                            REF to "17",
                            DISPLAY to "flex"
                        ) {
                            "div"("class" to "title", DISPLAY to "flow-root") {
                                +"Can I export markdown to PDF or HTML?"
                            }
                            +" "
                            "svelte-css-wrapper"("style" to "display: contents; --height: 32px;") {
                                "button"("class" to "button icon-only", REF to "18", DISPLAY to "flex") {
                                    +" "
                                    "svg"("viewBox" to "0 0 24 24") {
                                        "path"("d" to "M12.566 15.316a.8.8 0 0 1-1.132 0z", DISPLAY to "inline") {}
                                    }
                                }
                            }
                        }
                        +" "
                        "div"("class" to "content", DISPLAY to "none") {
                            +"Yes. Dillinger exports the current document as raw Markdown."
                        }
                    }
                }
            }
        }

        // when
        val markdown = faqItem.transformHtmlToMarkdown().renderMarkdown()

        // then — the wrapper is unwrapped and the button survives, ref and all
        // (before the catch-all unwrap rule the whole wrapper subtree vanished);
        // the collapsed answer is display:none and goes, and the nameless icon
        // is decorative and goes with it — so the button, a control of its own
        // even inside the labelled summary, gets the graphic placeholder
        markdown sameAsMarkdown """
            <details>
            <summary ref="17">
            
            Can I export markdown to PDF or HTML?
            
            <button ref="18">
            
            ![:svg:]()
            
            </button>
            </summary>
            </details>
        """.trimIndent()
    }

    @Test
    fun `should keep the buttons wrapped in acf-button-standard`() = runTest {
        // given — Copilot's answer toolbar as the Bing SERP dump captures it:
        // each button lives in an `acf-button-standard` design-system component,
        // its icon in an `acf-icon` one, and its label in a visually-hidden div
        val toolbar = semanticEvents(tagged = true) {
            "body" {
                "div"("class" to "b_crtrm_actions", DISPLAY to "flex") {
                    for ((label, ref) in listOf("Undo" to "26", "Redo" to "27")) {
                        "acf-button-standard"("data-icon-only" to "", "data-is-ready" to "") {
                            "button"(
                                "type" to "button",
                                "aria-disabled" to "true",
                                "title" to label,
                                REF to ref,
                                DISPLAY to "grid"
                            ) {
                                "acf-icon"("data-size" to "M") {
                                    "svg"("viewBox" to "0 0 20 20", "aria-hidden" to "true") {
                                        "use"("href" to "#acf-icon__ArrowHookUpLeft", DISPLAY to "inline") {}
                                    }
                                }
                                "div"("class" to "acf-button-standard__label", "data-visually-hidden" to "") {
                                    +label
                                }
                            }
                        }
                    }
                }
            }
        }

        // when
        val markdown = toolbar.transformHtmlToMarkdown().renderMarkdown()

        // then — both components are unwrapped and the buttons come through
        // labelled and actionable (before the catch-all unwrap rule the
        // toolbar was gone entirely)
        markdown sameAsMarkdown """
            <button type="button" aria-disabled="true" ref="26">
            
            Undo
            
            </button>
            <button type="button" aria-disabled="true" ref="27">
            
            Redo
            
            </button>
        """.trimIndent()
    }

    @Test
    fun `should keep the text of a mistyped tag`() = runTest {
        // given — a DuckDuckGo nav menu whose source reads
        // `<ahref="/duckduckgo-help-pages/desktop/adding-duckduckgo-to-your-browser/">`
        // (no space after the `a`), which Chrome parses as an element named
        // `ahref="` with the path segments as attributes
        val menu = semanticEvents(tagged = true) {
            "body" {
                "ul" {
                    "li"("class" to "nav-menu__item", DISPLAY to "list-item") {
                        "a"("href" to "/windows?origin=funnel_browser_searchresults", REF to "123") {
                            +"Windows Browser"
                        }
                    }
                    "li"("class" to "nav-menu__item", DISPLAY to "list-item") {
                        "ahref=\""(
                            "duckduckgo-help-pages" to "",
                            "desktop" to "",
                            "adding-duckduckgo-to-your-browser" to "",
                            "\"" to "",
                            DISPLAY to "inline"
                        ) {
                            +"Browser Extensions"
                        }
                    }
                }
            }
        }

        // when
        val markdown = menu.transformHtmlToMarkdown().renderMarkdown()

        // then — the mistyped element is unwrapped: its link is lost with the
        // typo, but its text is not (before the catch-all unwrap rule the
        // whole item vanished)
        markdown sameAsMarkdown """
            - [Windows Browser](ref:123:/windows?origin=funnel_browser_searchresults)
            - Browser Extensions
        """.trimIndent()
    }

    @Test
    fun `should not spill vector graphics noise into any dump`() = runTest {
        // given — every captured page carries inline SVG icons
        val dumps = listOf(
            DumpFixtures.bbcNews, DumpFixtures.serpBing, DumpFixtures.serpBrave,
            DumpFixtures.serpDuckduckgo, DumpFixtures.serpGoogle,
        )

        for (dump in dumps) {
            // when
            val markdown = dumpFlow(dump).transformHtmlToMarkdown().renderMarkdown()

            // then — the catch-all unwrap must not reach inside a dropped <svg>
            assert("<path" !in markdown)
            assert("<defs" !in markdown)
            assert("<symbol" !in markdown)
            assert("<linearGradient" !in markdown)
        }
    }

}
