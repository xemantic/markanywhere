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
import com.xemantic.kotlin.test.sameAs
import com.xemantic.kotlin.test.sameAsMarkdown
import com.xemantic.markanywhere.dump.AccessibilityAnnotations
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.parse.parse
import com.xemantic.markanywhere.render.renderMarkdown
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class HtmlToMarkdownTest {

    @Test
    fun `should convert the README HTML to Markdown example`() = runTest {
        // given — a captured page fragment: a presentational wrapper, an icon
        // font, a tracking script, and the real content all mixed together
        val page = semanticEvents(tagged = true) {
            "body" {
                "h1" { +"Weather" }
                "p" {
                    "i"("class" to "fa-solid fa-sun") { }
                    +" Sunny and "
                    "strong" { +"warm" }
                    +" today — see the "
                    "a"("href" to "https://example.com/forecast") { +"forecast" }
                    +"."
                }
                "script" { +"track('view')" }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown().renderMarkdown()

        // then
        markdown sameAs """
            # Weather
            
            ☀️ Sunny and **warm** today — see the [forecast](https://example.com/forecast).
        """.trimIndent()
    }

    @Test
    fun `should drop empty b i and s elements instead of rendering bare delimiters`() = runTest {
        // given — an icon element no resolver recognises, and blank formatting
        // wrappers: now simplified to strong / em / del, they must still vanish
        // rather than render as a stray `*` / `**` / `~~`
        val page = semanticEvents(tagged = true) {
            "body" {
                "p" {
                    "i"("class" to "custom-icon custom-icon-sun") { }
                    +"Sunny "
                    "b" { +" " }
                    "s" { }
                    +"today"
                }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown().renderMarkdown()

        // then
        markdown sameAs "Sunny today"
    }

    @Test
    fun `should render an a without href as plain text`() = runTest {
        // given - the issue #110 reproduction: tag "links" with no href next to
        // a real one, and an empty href, which still names the current document
        val page = semanticEvents(tagged = true) {
            "body" {
                "p" {
                    +"Tags: "
                    "a"("class" to "tag") { +"change" }
                    +" "
                    "a"("class" to "tag") { +"world" }
                    +" and a real "
                    "a"("href" to "/x") { +"link" }
                    +", even an empty "
                    "a"("href" to "") { +"one" }
                    +"."
                }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown().renderMarkdown()

        // then
        markdown sameAs "Tags: change world and a real [link](/x), even an empty [one]()."
    }

    @Test
    fun `should keep an a without href that the capture found actionable`() = runTest {
        // given - a scripted placeholder exposed as a control, an icon-only one
        // too, and a decorative one with nothing to act on
        val page = semanticEvents(tagged = true) {
            "body" {
                "p" {
                    "a"("onclick" to "more()", AccessibilityAnnotations.REF to "5") { +"more" }
                    +" "
                    "a"("title" to "Close", AccessibilityAnnotations.REF to "6") { "svg" { } }
                    +" "
                    "a"("title" to "Star") { "svg" { } }
                    +"end"
                }
            }
        }

        // when
        val encoded = page.transformHtmlToMarkdown().renderMarkdown()
        val stripped = page.transformHtmlToMarkdown(refMode = RefMode.STRIP).renderMarkdown()

        // then - the actionable ones stay links carrying their ref; without refs
        // they are placeholders like the rest, so only their text survives and
        // no label is invented for the icon-only one
        encoded sameAs "[more](ref:5:) [Close](ref:6:) end"
        stripped sameAs "more end"
    }

    @Test
    fun `should not label an a without href whose ref is stripped`() = runTest {
        // given - an icon-only scripted placeholder the capture found actionable
        val page = semanticEvents(tagged = true) {
            "body" {
                "p" {
                    +"Close "
                    "a"(AccessibilityAnnotations.REF to "6") { "svg" { } }
                    +"end"
                }
            }
        }

        // when - STRIP drops the ref, so simplifyHtml unwraps the placeholder
        val markdown = page.transformHtmlToMarkdown(refMode = RefMode.STRIP).renderMarkdown()

        // then - no placeholder label is left behind with nothing to act on
        markdown sameAs "Close end"
    }

    @Test
    fun `should strip actionable refs in STRIP mode`() = runTest {
        // given — both ref surfaces in one hand-built input: a ref-bearing
        // inline link (folds into the `ref:` destination) and a ref-bearing
        // block-wrapping link — a link around an <h2>, which renders as a raw
        // <a> tag carrying a `ref=` attribute. RefMode.STRIP must drop both.
        val page = semanticEvents(tagged = true) {
            "body" {
                "p" {
                    +"See the "
                    "a"("href" to "https://example.com", AccessibilityAnnotations.REF to "7") { +"site" }
                    +"."
                }
                "a"("href" to "/live", AccessibilityAnnotations.REF to "9") {
                    "h2" { +"Headline" }
                }
            }
        }

        // when — RefMode.STRIP drops the dump's refs entirely
        val markdown = page.transformHtmlToMarkdown(refMode = RefMode.STRIP).renderMarkdown()

        // then — no actionable-ref residue on any surface (see
        // assertNoActionableRefs); links keep their real href as standard Markdown
        assertNoActionableRefs(markdown)
        markdown sameAs """
            See the [site](https://example.com).
            
            <a href="/live">
            
            ## Headline
            
            </a>
        """.trimIndent()
    }

    @Test
    fun `should not let an explicit REF in keepAttributes bypass STRIP`() = runTest {
        // given — a ref-bearing block-wrapping link (rendered as a raw <a> tag, so
        // a surviving REF would leak as a `data-markanywhere-ref` attribute), with
        // the caller contradictorily asking to keep REF *and* selecting STRIP
        val page = semanticEvents(tagged = true) {
            "body" {
                "a"("href" to "/live", AccessibilityAnnotations.REF to "9") {
                    "h2" { +"Headline" }
                }
            }
        }

        // when — STRIP wins: REF is removed from keepAttributes too
        val markdown = page.transformHtmlToMarkdown(
            keepAttributes = setOf(AccessibilityAnnotations.REF),
            refMode = RefMode.STRIP,
        ).renderMarkdown()

        // then — the raw `data-markanywhere-ref` attribute does not leak through
        // (nor any other ref surface)
        assertNoActionableRefs(markdown)
        markdown sameAs """
            <a href="/live">
            
            ## Headline
            
            </a>
        """.trimIndent()
    }

    @Test
    fun `should thread a custom keepAttributes entry through the pipeline`() = runTest {
        // given — a section carrying a custom data attribute the caller wants kept
        val page = semanticEvents(tagged = true) {
            "section"("data-foo" to "bar") {
                "p" { +"Body" }
            }
        }

        // when — the caller keeps `data-foo`; the keep-set arithmetic
        // `(keepAttributes - REF) + refKeep + DISPLAY` must thread it through
        // unchanged in BOTH ref modes
        val encoded = page.transformHtmlToMarkdown(keepAttributes = setOf("data-foo")).renderMarkdown()
        val stripped = page.transformHtmlToMarkdown(
            keepAttributes = setOf("data-foo"),
            refMode = RefMode.STRIP,
        ).renderMarkdown()

        // then — the custom attribute survives to the raw-tag output in both modes
        assert("data-foo=\"bar\"" in encoded)
        assert("data-foo=\"bar\"" in stripped)
    }

    @Test
    fun `should render the options of a select the browser lays out no box for`() = runTest {
        // given — the shape a real capture produces: a closed <select> keeps its
        // popup out of the layout tree, so every <option> is annotated
        // `display: none` even though nothing is hidden.
        val page = semanticEvents(tagged = true) {
            "select"(
                "id" to "country",
                AccessibilityAnnotations.REF to "1",
                AccessibilityAnnotations.DISPLAY to "inline-block"
            ) {
                "option"("value" to "pl", AccessibilityAnnotations.DISPLAY to "none") { +"Poland" }
                "option"(
                    "value" to "de",
                    "selected" to "",
                    AccessibilityAnnotations.DISPLAY to "none"
                ) { +"Germany" }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown().renderMarkdown()

        // then — the options survive with their labels, so an agent can pick one
        markdown sameAsMarkdown """
            <select id="country" ref="1">
            <option value="pl">
            
            Poland
            
            </option>
            <option value="de" selected="">
            
            Germany
            
            </option>
            </select>
        """.trimIndent()
    }

    @Test
    fun `should separate the block content of a customizable select option`() = runTest {
        // given — a customizable select whose option lays its name and
        // description out as two block <div>s: the popup lives outside the
        // layout tree, so the capture annotates both `display: none` and
        // records no inter-tag whitespace between them.
        val page = semanticEvents(tagged = true) {
            "html"(AccessibilityAnnotations.DISPLAY to "block") {
                "body"(AccessibilityAnnotations.DISPLAY to "block") {
                    "select"(
                        "id" to "plan",
                        AccessibilityAnnotations.REF to "1",
                        AccessibilityAnnotations.DISPLAY to "inline-block"
                    ) {
                        "option"("value" to "pro", AccessibilityAnnotations.DISPLAY to "none") {
                            "div"(AccessibilityAnnotations.DISPLAY to "none") { +"Pro" }
                            "div"(AccessibilityAnnotations.DISPLAY to "none") { +"For teams" }
                        }
                    }
                }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown().renderMarkdown()

        // then — the name and the description stay two words, not `ProFor teams`
        markdown sameAsMarkdown """
            <select id="plan" ref="1">
            <option value="pro">
            
            Pro For teams
            
            </option>
            </select>
        """.trimIndent()
    }

    @Test
    fun `should separate the flex items of a customizable select option`() = runTest {
        // given — a customizable select lays its <option> out as a flex container, so
        // two <span>s side by side are two boxes, not one word.
        val page = semanticEvents(tagged = true) {
            "html"(AccessibilityAnnotations.DISPLAY to "block") {
                "body"(AccessibilityAnnotations.DISPLAY to "block") {
                    "select"(
                        "id" to "plan",
                        AccessibilityAnnotations.REF to "1",
                        AccessibilityAnnotations.DISPLAY to "inline-block"
                    ) {
                        "option"("value" to "pro", AccessibilityAnnotations.DISPLAY to "none") {
                            "span"(AccessibilityAnnotations.DISPLAY to "none") { +"★" }
                            "span"(AccessibilityAnnotations.DISPLAY to "none") { +"Pro" }
                        }
                    }
                }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown().renderMarkdown()

        // then
        markdown sameAsMarkdown """
            <select id="plan" ref="1">
            <option value="pro">
            
            ★ Pro
            
            </option>
            </select>
        """.trimIndent()
    }

    @Test
    fun `should separate an image from the label of a customizable select option`() = runTest {
        // given — a flag image and its label as flex items of the option.
        val page = semanticEvents(tagged = true) {
            "html"(AccessibilityAnnotations.DISPLAY to "block") {
                "body"(AccessibilityAnnotations.DISPLAY to "block") {
                    "select"(
                        "id" to "plan",
                        AccessibilityAnnotations.REF to "1",
                        AccessibilityAnnotations.DISPLAY to "inline-block"
                    ) {
                        "option"("value" to "pro", AccessibilityAnnotations.DISPLAY to "none") {
                            "img"("src" to "/pl.svg", "alt" to "flag", AccessibilityAnnotations.DISPLAY to "none") {}
                            "span"(AccessibilityAnnotations.DISPLAY to "none") { +"Poland" }
                        }
                    }
                }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown().renderMarkdown()

        // then
        markdown sameAsMarkdown """
            <select id="plan" ref="1">
            <option value="pro">
            
            ![flag](/pl.svg) Poland
            
            </option>
            </select>
        """.trimIndent()
    }

    @Test
    fun `should keep the space after emphasis in a customizable select option`() = runTest {
        // given — an emphasised flex item followed by text that carries its own space:
        // blockifying the item must not lose the separator.
        val page = semanticEvents(tagged = true) {
            "html"(AccessibilityAnnotations.DISPLAY to "block") {
                "body"(AccessibilityAnnotations.DISPLAY to "block") {
                    "select"(
                        "id" to "plan",
                        AccessibilityAnnotations.REF to "1",
                        AccessibilityAnnotations.DISPLAY to "inline-block"
                    ) {
                        "option"("value" to "pro", AccessibilityAnnotations.DISPLAY to "none") {
                            "strong"(AccessibilityAnnotations.DISPLAY to "none") { +"PL" }
                            +" Poland"
                        }
                    }
                }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown().renderMarkdown()

        // then
        markdown sameAsMarkdown """
            <select id="plan" ref="1">
            <option value="pro">
            
            **PL** Poland
            
            </option>
            </select>
        """.trimIndent()
    }

    @Test
    fun `should render the options of a select nested behind a wrapper`() = runTest {
        // given — the event shape a real Chrome capture produces when a <div>
        // sits between <select> and <option> (Chrome's parser keeps one, the
        // customizable-select content model allows one, and a JS-built DOM can
        // nest anything): the wrapper, the options and an option's own inline
        // markup all report the no-layout-box `none`.
        val page = semanticEvents(tagged = true) {
            "select"(
                "id" to "country",
                AccessibilityAnnotations.REF to "1",
                AccessibilityAnnotations.DISPLAY to "inline-flex"
            ) {
                "div"("class" to "popup", AccessibilityAnnotations.DISPLAY to "none") {
                    "option"("value" to "pl", AccessibilityAnnotations.DISPLAY to "none") {
                        "span"("class" to "flag", AccessibilityAnnotations.DISPLAY to "none") { +"PL" }
                        +" Poland"
                    }
                    "option"("value" to "de", AccessibilityAnnotations.DISPLAY to "none") { +"Germany" }
                }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown().renderMarkdown()

        // then — the wrapper is unwrapped and the options survive with the inline
        // markup of their labels intact
        markdown sameAsMarkdown """
            <select id="country" ref="1">
            <option value="pl">
            
            PL Poland
            
            </option>
            <option value="de">
            
            Germany
            
            </option>
            </select>
        """.trimIndent()
    }

    @Test
    fun `should keep the content of an unrecognised element`() = runTest {
        // given — a web component with no dedicated simplification rule
        val page = semanticEvents(tagged = true) {
            "body" {
                "my-widget" {
                    "h2" { +"Heading inside a web component" }
                    "p" { +"paragraph inside a web component" }
                }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown().renderMarkdown()

        // then
        markdown sameAsMarkdown """
            ## Heading inside a web component
            
            paragraph inside a web component
        """.trimIndent()
    }

    @Test
    fun `should render a menu as a tag around a Markdown list`() = runTest {
        // given — unwrapping `<menu>` left its items with no list, which the
        // renderer could not emit at all (it failed the whole document).
        val page = semanticEvents(tagged = true) {
            "body" {
                "nav" {
                    "menu" {
                        "li" { "a"("href" to "/") { +"Home" } }
                        "li" { "a"("href" to "/about") { +"About" } }
                    }
                }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown(refMode = RefMode.STRIP).renderMarkdown()

        // then
        markdown sameAsMarkdown """
            <nav>
            <menu>
            
            - [Home](/)
            - [About](/about)
            
            </menu>
            </nav>
        """.trimIndent()
    }

    @Test
    fun `should round-trip a menu through parse and render`() = runTest {
        // given — the wrapper only survives re-reading if it is emitted as a
        // *block* raw tag: without the blank line after the opening tag the
        // list is HTML-block raw text on re-parse, and the next render escapes
        // it to `\- Home`, degrading further every pass.
        val page = semanticEvents(tagged = true) {
            "body" {
                "menu"("id" to "toolbar") {
                    "li" { +"Home" }
                    "li" { +"About" }
                }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown(refMode = RefMode.STRIP).renderMarkdown()
        val reRendered = flowOf(markdown).parse().renderMarkdown()

        // then
        markdown sameAsMarkdown """
            <menu id="toolbar">
            
            - Home
            - About
            
            </menu>
        """.trimIndent()
        reRendered sameAs markdown
    }

    @Test
    fun `should render a table caption as the block above its table`() = runTest {
        // given
        val page = semanticEvents(tagged = true) {
            "body" {
                "table" {
                    "caption" { +"Q3 revenue" }
                    "tr" { "th" { +"Region" }; "th" { +"Total" } }
                    "tr" { "td" { +"EU" }; "td" { +"120" } }
                }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown(refMode = RefMode.STRIP).renderMarkdown()

        // then — blank-line separated, so the table still parses as a table
        markdown sameAsMarkdown """
            Q3 revenue
            
            | Region | Total |
            | --- | --- |
            | EU | 120 |
        """.trimIndent()
    }

    @Test
    fun `should keep a visible dialog as a block`() = runTest {
        // given — a closed dialog is display:none and never reaches here, so a
        // surviving one is an on-screen (usually blocking) modal.
        val page = semanticEvents(tagged = true) {
            "body" {
                "p" { +"Article text." }
                "dialog"("open" to "true", "aria-modal" to "true") {
                    "h2" { +"We value your privacy" }
                    "button" { +"Accept" }
                }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown(refMode = RefMode.STRIP).renderMarkdown()

        // then
        markdown sameAsMarkdown """
            Article text.
            
            <dialog open="" aria-modal="true">
            
            ## We value your privacy
            
            <button>
            
            Accept
            
            </button>
            </dialog>
        """.trimIndent()
    }

    @Test
    fun `should keep a ruby annotation off its base text`() = runTest {
        // given
        val page = semanticEvents(tagged = true) {
            "body" {
                "p" {
                    "ruby" { +"\u6f22\u5b57"; "rt" { +"kanji" } }
                    +" is hard"
                }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown(refMode = RefMode.STRIP).renderMarkdown()

        // then
        markdown sameAs "<ruby>\u6f22\u5b57<rt>kanji</rt></ruby> is hard"
    }

    @Test
    fun `should round-trip a ruby annotation through parse and render`() = runTest {
        // given - the Markdown the test above produces, re-read as source
        val markdown = "<ruby>\u6f22\u5b57<rt>kanji</rt></ruby> is hard"

        // when
        val reRendered = flowOf(markdown).parse().renderMarkdown()

        // then - the tagged `ruby`/`rt` the parser emits render exactly like
        //   the untagged ones simplify hands the renderer (neither name has
        //   Markdown syntax), so the inline raw HTML is a fixpoint
        reRendered sameAs markdown
    }

    @Test
    fun `should keep the line structure of a code block`() = runTest {
        // given - the whitespace normalizer's verbatim guard used to require
        // isTagged, but simplifyHtml untags `pre`/`code` upstream of it, so
        // every captured code block collapsed onto one line.
        val page = semanticEvents(tagged = true) {
            "body" {
                "pre" {
                    "code"("class" to "language-kotlin") {
                        +"fun main() {\n    println(\"hi\")\n}"
                    }
                }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown(refMode = RefMode.STRIP).renderMarkdown()

        // then
        markdown sameAsMarkdown """
            ```kotlin
            fun main() {
                println("hi")
            }
            ```
        """.trimIndent()
    }


    @Test
    fun `should keep the whole svg subtree in PRESERVE mode`() = runTest {
        // given — the catch-all unwraps anything no rule claims, so without a
        // dedicated svg mode the <path> would spill its vector data as text.
        val page = semanticEvents(tagged = true) {
            "body" {
                "p" {
                    "svg"("viewBox" to "0 0 24 24", "class" to "logo") {
                        "title" { +"Home" }
                        "path"("d" to "M0 0h24v24H0z", "fill" to "#fff") { }
                    }
                }
            }
        }

        // when
        val markdown = page
            .transformHtmlToMarkdown(refMode = RefMode.STRIP, svgMode = SvgMode.PRESERVE)
            .renderMarkdown()

        // then
        markdown sameAs "<svg viewBox=\"0 0 24 24\" class=\"logo\">" +
            "<title>Home</title><path d=\"M0 0h24v24H0z\" fill=\"#fff\"></path></svg>"
    }

    @Test
    fun `should reduce the same svg to its name in RESOLVE mode`() = runTest {
        // given — the default: the accessible name survives, the vector data
        // does not
        val page = semanticEvents(tagged = true) {
            "body" {
                "p" {
                    "svg"("viewBox" to "0 0 24 24") {
                        "title" { +"Home" }
                        "path"("d" to "M0 0h24v24H0z") { }
                    }
                }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown(refMode = RefMode.STRIP).renderMarkdown()

        // then
        markdown sameAs "![Home]()"
    }

    @Test
    fun `should not add a graphic placeholder in PRESERVE mode`() = runTest {
        // given — an icon-only link whose graphic survives speaks for itself
        val page = semanticEvents(tagged = true) {
            "body" {
                "a"("href" to "/") { "svg" { "path"("d" to "M0 0") { } } }
            }
        }

        // when
        val markdown = page
            .transformHtmlToMarkdown(refMode = RefMode.STRIP, svgMode = SvgMode.PRESERVE)
            .renderMarkdown()

        // then
        assert(GRAPHIC_PLACEHOLDER_ALT !in markdown)
        assert("<path" in markdown)
    }

    @Test
    fun `should render embedded content as tags carrying what they embed`() = runTest {
        // given - a hidden analytics frame (the overwhelming majority on real
        // pages) alongside a visible embed and a video
        val page = semanticEvents(tagged = true) {
            "body" {
                "p" { +"Article." }
                "iframe"(
                    "src" to "https://analytics.example/beacon.html",
                    AccessibilityAnnotations.DISPLAY to "none",
                ) { }
                "iframe"("src" to "https://consent.example/dialog.html", "title" to "Consent") {
                    +"fallback"
                }
                "video"("src" to "clip.mp4", "poster" to "p.jpg") { "track"("src" to "c.vtt") { } }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown(refMode = RefMode.STRIP).renderMarkdown()

        // then - the hidden frame is gone (applyAccessibility), the visible ones
        // keep what they point at, and neither keeps its fallback children
        markdown sameAsMarkdown """
            Article.
            
            <iframe src="https://consent.example/dialog.html" title="Consent">
            </iframe>
            
            <video src="clip.mp4" poster="p.jpg"></video>
        """.trimIndent()
    }

    @Test
    fun `should round-trip embedded content through parse and render`() = runTest {
        // given
        val page = semanticEvents(tagged = true) {
            "body" {
                "p" { +"before" }
                "iframe"("src" to "https://consent.example/d.html", "title" to "Consent") { }
                "p" { +"after" }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown(refMode = RefMode.STRIP).renderMarkdown()
        val reRendered = flowOf(markdown).parse().renderMarkdown()

        // then - the parser reads a block-level `<iframe>` back as the same
        // structural block (mid-line it is dropped, as GFM §6.11 wants)
        reRendered sameAs markdown
    }


    @Test
    fun `should label a link whose svg is named only by a title attribute`() = runTest {
        // given - the name is on the svg's `title` attribute, which neither
        // resolveInlineGraphics nor simplifyHtml carries into the output
        val page = semanticEvents(tagged = true) {
            "body" {
                "a"("href" to "/search") {
                    "svg"("title" to "Search") { "path"("d" to "M0 0") { } }
                }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown(refMode = RefMode.STRIP).renderMarkdown()

        // then
        markdown sameAs "[Search](/search)"
    }

    @Test
    fun `should render the document a same-origin frame embeds`() = runTest {
        // given - a capture nests a same-origin frame's document inside its
        // <iframe> (there is no fallback markup in a captured stream); the
        // frame's <head> belongs to the frame, not to the page's frontmatter
        val page = semanticEvents(tagged = true) {
            "body" {
                "p" { +"Article." }
                "iframe"("src" to "https://consent.example/dialog.html", "title" to "Consent") {
                    "html"("lang" to "de") {
                        "head" { "title" { +"Consent dialog" } }
                        "body" {
                            "p" { +"Accept cookies?" }
                            "button" { +"Accept" }
                        }
                    }
                }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown(refMode = RefMode.STRIP).renderMarkdown()

        // then - the frame's content is on the page, its metadata is not
        markdown sameAsMarkdown """
            Article.
            
            <iframe src="https://consent.example/dialog.html" title="Consent">
            
            Accept cookies?
            
            <button>
            
            Accept
            
            </button>
            </iframe>
        """.trimIndent()
    }

    @Test
    fun `should render and round-trip the document a legacy frame embeds`() = runTest {
        // given - a frameset page, captured the same way as an iframe
        val page = semanticEvents(tagged = true) {
            "frameset"("cols" to "20%,80%") {
                "frame"("src" to "nav.html", "name" to "nav") {
                    "html" {
                        "head" { "title" { +"Navigation" } }
                        "body" { "h2" { +"Sections" } }
                    }
                }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown(refMode = RefMode.STRIP).renderMarkdown()
        val reRendered = flowOf(markdown).parse().renderMarkdown()

        // then - the frame is a block-level tag whose content parses back
        markdown sameAsMarkdown """
            <frame src="nav.html" name="nav">
            
            ## Sections
            
            </frame>
        """.trimIndent()
        reRendered sameAs markdown
    }

    @Test
    fun `should strip capture annotations and refs inside a preserved svg`() = runTest {
        // given - a captured svg carries the same annotations as any other
        // element, plus a script that must never reach a reader
        val page = semanticEvents(tagged = true) {
            "body" {
                "svg"(
                    "viewBox" to "0 0 24 24",
                    AccessibilityAnnotations.DISPLAY to "inline",
                    AccessibilityAnnotations.REF to "3",
                ) {
                    "a"("href" to "/home", AccessibilityAnnotations.REF to "4") {
                        "path"("d" to "M0 0", AccessibilityAnnotations.DISPLAY to "inline") { }
                    }
                    "script" { +"alert(1)" }
                }
            }
        }

        // when
        val stripped = page
            .transformHtmlToMarkdown(refMode = RefMode.STRIP, svgMode = SvgMode.PRESERVE)
            .renderMarkdown()
        val encoded = page
            .transformHtmlToMarkdown(refMode = RefMode.ENCODE, svgMode = SvgMode.PRESERVE)
            .renderMarkdown()

        // then
        stripped sameAs "<svg viewBox=\"0 0 24 24\"><a href=\"/home\"><path d=\"M0 0\"></path></a></svg>"
        assert(AccessibilityAnnotations.REF !in encoded)
        assert(AccessibilityAnnotations.DISPLAY !in encoded)
        assert("ref=\"3\"" in encoded)
        assert("alert" !in encoded)
    }

    @Test
    fun `should keep ARIA controls named only by aria-label`() = runTest {
        // given — the bahn.de station autocomplete (issue #108): a combobox
        // whose clear button and listbox options carry their visible text only
        // in `aria-hidden` spans, so `aria-label` is all that names them
        val page = semanticEvents(tagged = true) {
            "body" {
                "input"("role" to "combobox", "aria-expanded" to "true", AccessibilityAnnotations.REF to "20") { }
                "span"("role" to "button", "aria-label" to "Delete entry", AccessibilityAnnotations.REF to "21") { }
                "ul"("role" to "listbox", AccessibilityAnnotations.REF to "22") {
                    "li"("role" to "option", "aria-label" to "Berlin Hbf. ", AccessibilityAnnotations.REF to "23") {
                        "span"("data-icon" to "station", "aria-hidden" to "true") { }
                        "span"("aria-hidden" to "true") { +"Berlin Hbf" }
                    }
                    "li"("role" to "option", "aria-label" to "Berlin Südkreuz. ", AccessibilityAnnotations.REF to "24") {
                        "span"("aria-hidden" to "true") { +"Berlin Südkreuz" }
                    }
                }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown().renderMarkdown()

        // then - every control keeps its ref and reads its name; the list
        // items' refs ride on a span, since `- ` carries no attributes
        markdown sameAs """
            <input aria-expanded="true" ref="20">
            
            <span role="button" aria-label="Delete entry" ref="21">Delete entry</span>
            
            - <span ref="23">Berlin Hbf.</span>
            - <span ref="24">Berlin Südkreuz.</span>
        """.trimIndent()
        flowOf(markdown).parse().renderMarkdown() sameAs markdown
    }

    @Test
    fun `should not glue the words around a dropped invisible element`() = runTest {
        // given — issue #111: `visibility:hidden` keeps the element's box, so
        // the browser shows a gap where the hidden text was
        val page = semanticEvents(tagged = true) {
            "p"(AccessibilityAnnotations.DISPLAY to "block") {
                +"Price"
                "span"(
                    AccessibilityAnnotations.DISPLAY to "inline",
                    AccessibilityAnnotations.VISIBILITY to "hidden"
                ) { +" hidden " }
                +"5"
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown().renderMarkdown()

        // then
        markdown sameAs "Price 5"
    }

    @Test
    fun `should label a link whose only image is an inline data uri`() = runTest {
        // given - an icon-only link whose icon is a base64 image: dropping the
        // payload must not leave an empty `[![]()](/x)` behind
        val page = semanticEvents(tagged = true) {
            "body" {
                "a"("href" to "/x") { "img"("src" to "data:image/png;base64,iVBORw0KGgo=") { } }
                +" "
                "a"("href" to "/y", "aria-label" to "Settings") {
                    "img"("src" to "data:image/png;base64,iVBORw0KGgo=") { }
                }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown(refMode = RefMode.STRIP).renderMarkdown()

        // then
        markdown sameAs "[![$GRAPHIC_PLACEHOLDER_ALT]()](/x) [Settings](/y)"
    }

}
