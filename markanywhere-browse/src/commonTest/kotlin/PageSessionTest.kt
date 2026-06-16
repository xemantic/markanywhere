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
import com.xemantic.kotlin.test.coroutines.should
import com.xemantic.kotlin.test.have
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class PageSessionTest {

    @Test
    fun `should dump simple local page`() = runTest {
        val dump = runInBrowser { browser ->
            // given
            val tab = browser.get(testPageUrl("simple.html"))
            tab.waitUntilLoaded()
            val session = PageSession(tab)

            // when
            session.dump()
        }

        // then
        dump.events.asFlow() sameAs semanticEvents(tagged = true) {
            "html"("lang" to "en") {
                "head" {
                    +"\n    "
                    "title" {
                        +"Simple"
                    }
                    +"\n"
                }
                +"\n"
                "body" {
                    +"\n"
                    "h1" {
                        +"Hello"
                    }
                    +"\n\n\n"
                }
            }
        }
    }

    /**
     * Hacker News lays its whole front page out with nested borderless
     * `<table>`s (no `<th>`/`<caption>`), which Chrome's accessibility engine
     * classifies as `LayoutTable`. The dump is **lossless**: it captures the full
     * DOM — every `table`/`tbody`/`tr`/`td` (note the browser-inserted `<tbody>`),
     * the decorative `aria-hidden="true"` votearrow `<div>`s, and the inter-tag
     * whitespace — and *annotates* the browser's verdicts (see
     * [com.xemantic.markanywhere.dump.AccessibilityAnnotations]): each layout `<table>`
     * carries `data-markanywhere-role="LayoutTable"`, every non-`block` element
     * its computed `data-markanywhere-display`, and — since [PageSession] is the
     * entry point — every actionable element (here the 18 `<a>` links) a dense
     * `data-markanywhere-ref`. Acting on those verdicts is a downstream concern;
     * here we assert only that the capture preserves everything a later filter or
     * an LLM-driven agent could need.
     */
    @Test
    fun `should capture full DOM and annotate layout tables of a hackernews-like page`() = runTest {
        val dump = runInBrowser { browser ->
            // given
            val tab = browser.get(testPageUrl("hackernews.html"))
            tab.waitUntilLoaded()
            val session = PageSession(tab)

            // when
            session.dump()
        }

        // then
        dump.events.asFlow() sameAs semanticEvents(tagged = true) {
            "html"("lang" to "en") {
                "head" {
                    +"\n    "
                    "meta"("charset" to "utf-8") { }
                    +"\n    "
                    "title" {
                        +"Hacker News"
                    }
                    +"\n"
                }
                +"\n"
                "body" {
                    +"\n"
                    "center" {
                        +"\n    "
                        "table"("id" to "hnmain", "border" to "0", "cellpadding" to "0", "cellspacing" to "0", "width" to "85%", "bgcolor" to "#f6f6ef", "data-markanywhere-role" to "LayoutTable", "data-markanywhere-display" to "table") {
                            +"\n        "
                            "tbody"("data-markanywhere-display" to "table-row-group") {
                                "tr"("data-markanywhere-display" to "table-row") {
                                    +"\n            "
                                    "td"("bgcolor" to "#ff6600", "data-markanywhere-display" to "table-cell") {
                                        +"\n                "
                                        "table"("border" to "0", "cellpadding" to "0", "cellspacing" to "0", "width" to "100%", "style" to "padding:2px", "data-markanywhere-role" to "LayoutTable", "data-markanywhere-display" to "table") {
                                            +"\n                    "
                                            "tbody"("data-markanywhere-display" to "table-row-group") {
                                                "tr"("data-markanywhere-display" to "table-row") {
                                                    +"\n                        "
                                                    "td"("style" to "width:18px;padding-right:4px", "data-markanywhere-display" to "table-cell") {
                                                        +"\n                            "
                                                        "a"("href" to "https://news.ycombinator.com", "data-markanywhere-ref" to "1", "data-markanywhere-display" to "inline") {
                                                            "img"("src" to "y18.svg", "width" to "18", "height" to "18", "alt" to "Hacker News", "data-markanywhere-display" to "inline") { }
                                                        }
                                                        +"\n                        "
                                                    }
                                                    +"\n                        "
                                                    "td"("style" to "line-height:12pt;height:10px;", "data-markanywhere-display" to "table-cell") {
                                                        +"\n                            "
                                                        "span"("class" to "pagetop", "data-markanywhere-display" to "inline") {
                                                            "b"("class" to "hnname", "data-markanywhere-display" to "inline") {
                                                                "a"("href" to "news", "data-markanywhere-ref" to "2", "data-markanywhere-display" to "inline") {
                                                                    +"Hacker News"
                                                                }
                                                            }
                                                            +"\n                                "
                                                            "a"("href" to "newest", "data-markanywhere-ref" to "3", "data-markanywhere-display" to "inline") {
                                                                +"new"
                                                            }
                                                            +" | "
                                                            "a"("href" to "newcomments", "data-markanywhere-ref" to "4", "data-markanywhere-display" to "inline") {
                                                                +"comments"
                                                            }
                                                            +" | "
                                                            "a"("href" to "ask", "data-markanywhere-ref" to "5", "data-markanywhere-display" to "inline") {
                                                                +"ask"
                                                            }
                                                            +" | "
                                                            "a"("href" to "show", "data-markanywhere-ref" to "6", "data-markanywhere-display" to "inline") {
                                                                +"show"
                                                            }
                                                        }
                                                        +"\n                        "
                                                    }
                                                    +"\n                    "
                                                }
                                                +"\n                "
                                            }
                                        }
                                        +"\n            "
                                    }
                                    +"\n        "
                                }
                                +"\n        "
                                "tr"("id" to "pagespace", "style" to "height:10px", "data-markanywhere-display" to "table-row") { }
                                +"\n        "
                                "tr"("data-markanywhere-display" to "table-row") {
                                    +"\n            "
                                    "td"("data-markanywhere-display" to "table-cell") {
                                        +"\n                "
                                        "table"("border" to "0", "cellpadding" to "0", "cellspacing" to "0", "class" to "itemlist", "data-markanywhere-role" to "LayoutTable", "data-markanywhere-display" to "table") {
                                            +"\n                    "
                                            "tbody"("data-markanywhere-display" to "table-row-group") {
                                                "tr"("class" to "athing", "id" to "40001", "data-markanywhere-display" to "table-row") {
                                                    +"\n                        "
                                                    "td"("align" to "right", "valign" to "top", "class" to "title", "data-markanywhere-display" to "table-cell") {
                                                        "span"("class" to "rank", "data-markanywhere-display" to "inline") {
                                                            +"1."
                                                        }
                                                    }
                                                    +"\n                        "
                                                    "td"("valign" to "top", "class" to "votelinks", "data-markanywhere-display" to "table-cell") {
                                                        +"\n                            "
                                                        "a"("href" to "vote?id=40001&how=up", "data-markanywhere-ref" to "7", "data-markanywhere-display" to "inline") {
                                                            "div"("class" to "votearrow", "title" to "upvote", "aria-hidden" to "true") { }
                                                        }
                                                        +"\n                        "
                                                    }
                                                    +"\n                        "
                                                    "td"("class" to "title", "data-markanywhere-display" to "table-cell") {
                                                        +"\n                            "
                                                        "span"("class" to "titleline", "data-markanywhere-display" to "inline") {
                                                            "a"("href" to "https://example.com/streaming-markdown", "data-markanywhere-ref" to "8", "data-markanywhere-display" to "inline") {
                                                                +"Show HN: A streaming Markdown parser"
                                                            }
                                                            "span"("class" to "sitebit comhead", "data-markanywhere-display" to "inline") {
                                                                +" ("
                                                                "a"("href" to "from?site=example.com", "data-markanywhere-ref" to "9", "data-markanywhere-display" to "inline") {
                                                                    "span"("class" to "sitestr", "data-markanywhere-display" to "inline") {
                                                                        +"example.com"
                                                                    }
                                                                }
                                                                +")"
                                                            }
                                                        }
                                                        +"\n                        "
                                                    }
                                                    +"\n                    "
                                                }
                                                +"\n                    "
                                                "tr"("data-markanywhere-display" to "table-row") {
                                                    +"\n                        "
                                                    "td"("colspan" to "2", "data-markanywhere-display" to "table-cell") { }
                                                    +"\n                        "
                                                    "td"("class" to "subtext", "data-markanywhere-display" to "table-cell") {
                                                        +"\n                            "
                                                        "span"("class" to "subline", "data-markanywhere-display" to "inline") {
                                                            "span"("class" to "score", "id" to "score_40001", "data-markanywhere-display" to "inline") {
                                                                +"128 points"
                                                            }
                                                            +" by "
                                                            "a"("href" to "user?id=alice", "class" to "hnuser", "data-markanywhere-ref" to "10", "data-markanywhere-display" to "inline") {
                                                                +"alice"
                                                            }
                                                            +" "
                                                            "span"("class" to "age", "data-markanywhere-display" to "inline") {
                                                                "a"("href" to "item?id=40001", "data-markanywhere-ref" to "11", "data-markanywhere-display" to "inline") {
                                                                    +"2 hours ago"
                                                                }
                                                            }
                                                            +" | "
                                                            "a"("href" to "item?id=40001", "data-markanywhere-ref" to "12", "data-markanywhere-display" to "inline") {
                                                                +"42\u00A0comments"
                                                            }
                                                        }
                                                        +"\n                        "
                                                    }
                                                    +"\n                    "
                                                }
                                                +"\n                    "
                                                "tr"("class" to "spacer", "style" to "height:5px", "data-markanywhere-display" to "table-row") { }
                                                +"\n                    "
                                                "tr"("class" to "athing", "id" to "40002", "data-markanywhere-display" to "table-row") {
                                                    +"\n                        "
                                                    "td"("align" to "right", "valign" to "top", "class" to "title", "data-markanywhere-display" to "table-cell") {
                                                        "span"("class" to "rank", "data-markanywhere-display" to "inline") {
                                                            +"2."
                                                        }
                                                    }
                                                    +"\n                        "
                                                    "td"("valign" to "top", "class" to "votelinks", "data-markanywhere-display" to "table-cell") {
                                                        +"\n                            "
                                                        "a"("href" to "vote?id=40002&how=up", "data-markanywhere-ref" to "13", "data-markanywhere-display" to "inline") {
                                                            "div"("class" to "votearrow", "title" to "upvote", "aria-hidden" to "true") { }
                                                        }
                                                        +"\n                        "
                                                    }
                                                    +"\n                        "
                                                    "td"("class" to "title", "data-markanywhere-display" to "table-cell") {
                                                        +"\n                            "
                                                        "span"("class" to "titleline", "data-markanywhere-display" to "inline") {
                                                            "a"("href" to "https://example.org/dom-to-markdown", "data-markanywhere-ref" to "14", "data-markanywhere-display" to "inline") {
                                                                +"Converting the DOM to Markdown via the accessibility tree"
                                                            }
                                                            "span"("class" to "sitebit comhead", "data-markanywhere-display" to "inline") {
                                                                +" ("
                                                                "a"("href" to "from?site=example.org", "data-markanywhere-ref" to "15", "data-markanywhere-display" to "inline") {
                                                                    "span"("class" to "sitestr", "data-markanywhere-display" to "inline") {
                                                                        +"example.org"
                                                                    }
                                                                }
                                                                +")"
                                                            }
                                                        }
                                                        +"\n                        "
                                                    }
                                                    +"\n                    "
                                                }
                                                +"\n                    "
                                                "tr"("data-markanywhere-display" to "table-row") {
                                                    +"\n                        "
                                                    "td"("colspan" to "2", "data-markanywhere-display" to "table-cell") { }
                                                    +"\n                        "
                                                    "td"("class" to "subtext", "data-markanywhere-display" to "table-cell") {
                                                        +"\n                            "
                                                        "span"("class" to "subline", "data-markanywhere-display" to "inline") {
                                                            "span"("class" to "score", "id" to "score_40002", "data-markanywhere-display" to "inline") {
                                                                +"73 points"
                                                            }
                                                            +" by "
                                                            "a"("href" to "user?id=bob", "class" to "hnuser", "data-markanywhere-ref" to "16", "data-markanywhere-display" to "inline") {
                                                                +"bob"
                                                            }
                                                            +" "
                                                            "span"("class" to "age", "data-markanywhere-display" to "inline") {
                                                                "a"("href" to "item?id=40002", "data-markanywhere-ref" to "17", "data-markanywhere-display" to "inline") {
                                                                    +"4 hours ago"
                                                                }
                                                            }
                                                            +" | "
                                                            "a"("href" to "item?id=40002", "data-markanywhere-ref" to "18", "data-markanywhere-display" to "inline") {
                                                                +"15\u00A0comments"
                                                            }
                                                        }
                                                        +"\n                        "
                                                    }
                                                    +"\n                    "
                                                }
                                                +"\n                "
                                            }
                                        }
                                        +"\n            "
                                    }
                                    +"\n        "
                                }
                                +"\n    "
                            }
                        }
                        +"\n"
                    }
                    +"\n\n"
                }
            }
        }
    }

    /**
     * [PageSession.dump] stamps a dense, document-order `data-markanywhere-ref`
     * on every actionable element — the link, the text input, and the button —
     * while the inert `<h1>`/`<span>`/`<p>` get none. (The same dump still carries
     * the computed `data-markanywhere-display`: `inline` on the link/span,
     * `inline-block` on the form controls.)
     */
    @Test
    fun `should dump actionable page with refs only on actionable elements`() = runTest {
        val dump = runInBrowser { browser ->
            // given
            val tab = browser.get(testPageUrl("actionable.html"))
            tab.waitUntilLoaded()
            val session = PageSession(tab)

            // when
            session.dump()
        }

        // then
        dump.events.asFlow() sameAs semanticEvents(tagged = true) {
            "html"("lang" to "en") {
                "head" {
                    +"\n    "
                    "meta"("charset" to "utf-8") { }
                    +"\n    "
                    "title" {
                        +"Actionable"
                    }
                    +"\n"
                }
                +"\n"
                "body" {
                    +"\n"
                    "h1" {
                        +"Actionable"
                    }
                    +"\n"
                    "a"("href" to "https://example.com/", "data-markanywhere-ref" to "1", "data-markanywhere-display" to "inline") {
                        +"a link"
                    }
                    +"\n"
                    "input"("type" to "text", "id" to "name", "placeholder" to "name", "data-markanywhere-ref" to "2", "data-markanywhere-display" to "inline-block") { }
                    +"\n"
                    "button"("type" to "button", "onclick" to "document.getElementById('out').textContent = 'clicked'", "data-markanywhere-ref" to "3", "data-markanywhere-display" to "inline-block") {
                        +"Go"
                    }
                    +"\n"
                    "span"("data-markanywhere-display" to "inline") {
                        +"just text, not actionable"
                    }
                    +"\n"
                    "p"("id" to "out") {
                        +"idle"
                    }
                    +"\n\n\n"
                }
            }
        }
    }

    @Test
    fun `should resolve a ref back to its live element for typing and clicking`() = runTest {
        runInBrowser { browser ->
            // given
            val tab = browser.get(testPageUrl("actionable.html"))
            tab.waitUntilLoaded()
            val session = PageSession(tab)
            session.dump()

            // when
            session.element("2").sendKeys("Alice")
            // then
            assert(session.element("2").getInputValue() == "Alice")

            // when
            session.element("3").click()
            // then
            assert(tab.select("#out").text == "clicked")
        }
    }

    @Test
    fun `should throw when resolving a ref absent from the current capture`() = runTest {
        runInBrowser { browser ->
            // given
            val tab = browser.get(testPageUrl("actionable.html"))
            tab.waitUntilLoaded()
            val session = PageSession(tab)

            // when / then — before any dump the ref registry is empty
            assertFailsWith<NoSuchElementException> {
                session.element("1")
            } should {
                have(message == "no actionable element with ref '1' in the current capture — re-run dump()")
            }

            // when
            session.dump()
            // then — a ref beyond the captured actionable set is unknown
            assertFailsWith<NoSuchElementException> {
                session.element("99")
            } should {
                have(message == "no actionable element with ref '99' in the current capture — re-run dump()")
            }
        }
    }

    @Test
    fun `should regenerate refs on re-dump invalidating refs from the prior capture`() = runTest {
        runInBrowser { browser ->
            // given — 1=link, 2=input, 3=button
            val tab = browser.get(testPageUrl("actionable.html"))
            tab.waitUntilLoaded()
            val session = PageSession(tab)
            session.dump()

            // when — the page loses its link, then we re-capture
            tab.rawEvaluate("document.querySelector('a').remove()")
            session.dump() // now 1=input, 2=button

            // then — the stale ref "3" is no longer in the fresh capture
            assertFailsWith<NoSuchElementException> {
                session.element("3")
            } should {
                have(message == "no actionable element with ref '3' in the current capture — re-run dump()")
            }
            // and the button, formerly ref "3", is renumbered to "2"
            session.element("2").click()
            assert(tab.select("#out").text == "clicked")
        }
    }

}
