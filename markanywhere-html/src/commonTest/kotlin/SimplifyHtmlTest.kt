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

import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SimplifyHtmlTest {

    // --- drop ---------------------------------------------------------------

    @Test
    fun `should drop script tag and its text content entirely`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "body" {
                "script" { +"console.log('x')" }
                "p" { +"after" }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then
        output sameAs semanticEvents {
            "p" { +"after" }
        }
    }

    @Test
    fun `should drop style tag and any nested marks inside it`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "body" {
                "style" {
                    "media" { +"screen" }
                    +"body { color: red; }"
                }
                "p" { +"content" }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then
        output sameAs semanticEvents {
            "p" { +"content" }
        }
    }

    @Test
    fun `should drop link and noscript tags`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "body" {
                "link"("rel" to "stylesheet") { }
                "noscript" { +"no js" }
                "p" { +"content" }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then
        output sameAs semanticEvents {
            "p" { +"content" }
        }
    }

    // --- unwrap -------------------------------------------------------------

    @Test
    fun `should unwrap div span and other presentational wrappers`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "body" {
                "div"("class" to "container") {
                    "span"("class" to "highlight") { +"text " }
                    "font"("color" to "red") { +"more" }
                }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then — div, span, font and body all unwrapped
        output sameAs semanticEvents {
            +"text "
            +"more"
        }
    }

    // --- preserve with id only ---------------------------------------------

    @Test
    fun `should preserve nav and section with id and strip other attributes`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "body" {
                "nav"("id" to "main-nav", "class" to "menu", "role" to "navigation") {
                    +"navigation"
                }
                "section"("id" to "intro", "data-section" to "x") {
                    +"intro body"
                }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then
        output sameAs semanticEvents {
            "nav"("id" to "main-nav") { +"navigation" }
            "section"("id" to "intro") { +"intro body" }
        }
    }

    @Test
    fun `should preserve nav and section without id when none is set`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "body" {
                "nav"("class" to "menu") { +"navigation" }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then
        output sameAs semanticEvents {
            "nav" { +"navigation" }
        }
    }

    @Test
    fun `should preserve paragraph and heading tags stripping presentational attrs`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "body" {
                "h1"("id" to "title", "class" to "huge") { +"Title" }
                "p"("class" to "lead", "style" to "margin:0") { +"Lead text" }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then
        output sameAs semanticEvents {
            "h1"("id" to "title") { +"Title" }
            "p" { +"Lead text" }
        }
    }

    @Test
    fun `should preserve list structure stripping class on ul and li`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "body" {
                "ul"("class" to "items") {
                    "li"("class" to "item") { +"one" }
                    "li" { +"two" }
                }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then
        output sameAs semanticEvents {
            "ul" {
                "li" { +"one" }
                "li" { +"two" }
            }
        }
    }

    // --- inline emphasis (no attrs) -----------------------------------------

    @Test
    fun `should preserve inline emphasis tags discarding all attributes`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "p" {
                +"hello "
                "em"("class" to "emph") { +"world" }
                +" and "
                "strong"("id" to "x") { +"bold" }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then
        output sameAs semanticEvents {
            "p" {
                +"hello "
                "em" { +"world" }
                +" and "
                "strong" { +"bold" }
            }
        }
    }

    @Test
    fun `should preserve b i and s and strike tags as they are`() = runTest {
        // given
        // <b>, <i>, <s> elements were rescued from being "italic/bold" buttons
        // into semantic roles, <strike> is depreciated, but we might still see it in the content
        val input = semanticEvents(tagged = true) {
            "p" {
                "b"("class" to "noisy") { +"bring attention to element" }
                +" "
                "i" { +"idiomatic text element" }
                +" "
                "s" { +"strikethrough element" }
                +" "
                "strike" { +"depreciated strike" }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then
        output sameAs semanticEvents {
            "p" {
                "b" { +"bring attention to element" }
                +" "
                "i" { +"idiomatic text element" }
                +" "
                "s" { +"strikethrough element" }
                +" "
                "strike" { +"depreciated strike" }
            }
        }
    }

    // --- custom attribute whitelists ---------------------------------------

    @Test
    fun `should preserve a with href and title only`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "p" {
                "a"(
                    "href" to "https://example.com",
                    "title" to "Example",
                    "class" to "external",
                    "target" to "_blank",
                ) { +"link" }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then
        output sameAs semanticEvents {
            "p" {
                "a"("href" to "https://example.com", "title" to "Example") { +"link" }
            }
        }
    }

    @Test
    fun `should preserve img with src and alt and title`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "p" {
                "img"(
                    "src" to "pic.png",
                    "alt" to "a picture",
                    "width" to "200",
                    "loading" to "lazy",
                ) { }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then
        output sameAs semanticEvents {
            "p" {
                "img"("src" to "pic.png", "alt" to "a picture") { }
            }
        }
    }

    @Test
    fun `should preserve code class for language hint`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "pre" {
                "code"("class" to "language-kotlin", "data-line" to "1") { +"val x = 1" }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then
        output sameAs semanticEvents {
            "pre" {
                "code"("class" to "language-kotlin") { +"val x = 1" }
            }
        }
    }

    @Test
    fun `should preserve ol start attribute`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "ol"("start" to "5", "type" to "1") {
                "li" { +"five" }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then
        output sameAs semanticEvents {
            "ol"("start" to "5") {
                "li" { +"five" }
            }
        }
    }

    @Test
    fun `should preserve th and td alignment and span attrs`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "table" {
                "thead" {
                    "tr" {
                        "th"("align" to "left", "colspan" to "2", "class" to "head") {
                            +"H"
                        }
                    }
                }
                "tbody" {
                    "tr" {
                        "td"("align" to "right", "rowspan" to "2", "style" to "x") {
                            +"v"
                        }
                    }
                }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then
        output sameAs semanticEvents {
            "table" {
                "thead" {
                    "tr" {
                        "th"("align" to "left", "colspan" to "2") { +"H" }
                    }
                }
                "tbody" {
                    "tr" {
                        "td"("align" to "right", "rowspan" to "2") { +"v" }
                    }
                }
            }
        }
    }

    // --- form elements -----------------------------------------------------

    @Test
    fun `should preserve form input and button with semantic attributes`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "form"("action" to "/submit", "method" to "post", "class" to "f") {
                "label"("for" to "n", "class" to "lbl") { +"Name" }
                "input"(
                    "id" to "n",
                    "type" to "text",
                    "name" to "name",
                    "required" to "",
                    "class" to "input",
                ) { }
                "button"("type" to "submit", "class" to "btn") { +"Go" }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then
        output sameAs semanticEvents {
            "form"("action" to "/submit", "method" to "post") {
                "label"("for" to "n") { +"Name" }
                "input"(
                    "id" to "n",
                    "type" to "text",
                    "name" to "name",
                    "required" to "",
                ) { }
                "button"("type" to "submit") { +"Go" }
            }
        }
    }

    // --- aria ---------------------------------------------------------------

    @Test
    fun `should keep whitelisted aria attributes and drop the rest`() = runTest {
        // given — a typical icon button: the accessible name and interaction
        // state are the only thing telling an agent what it does.
        val input = semanticEvents(tagged = true) {
            "button"(
                "id" to "menu",
                "aria-label" to "Open menu",
                "aria-expanded" to "false",
                "aria-haspopup" to "true",
                "aria-controls" to "menu-list",
                "aria-labelledby" to "x",
                "aria-live" to "polite",
                "role" to "button",
                "class" to "sc-643c51a4-3 dcFqSm",
            ) { +"Menu" }
        }

        // when
        val output = input.simplifyHtml()

        // then — id + accessible-name + actionable state survive; id-reference
        // aria (controls/labelledby), live regions, role, and class are dropped.
        output sameAs semanticEvents {
            "button"(
                "id" to "menu",
                "aria-label" to "Open menu",
                "aria-expanded" to "false",
                "aria-haspopup" to "true",
            ) { +"Menu" }
        }
    }

    @Test
    fun `should keep aria-label and state on structural and link elements`() = runTest {
        // when
        val input = semanticEvents(tagged = true) {
            "nav"("id" to "n", "aria-label" to "Primary", "class" to "c") {
                "a"(
                    "href" to "/",
                    "aria-label" to "Home",
                    "aria-current" to "page",
                    "class" to "k",
                ) { +"Home" }
            }
        }

        // then — aria keep-set applies to every preserved element, not just forms.
        input.simplifyHtml() sameAs semanticEvents {
            "nav"("id" to "n", "aria-label" to "Primary") {
                "a"("href" to "/", "aria-label" to "Home", "aria-current" to "page") { +"Home" }
            }
        }
    }

    @Test
    fun `should keep the remaining aria state attributes`() = runTest {
        // when — the rest of the keep-set, on a single element.
        val input = semanticEvents(tagged = true) {
            "li"(
                "aria-checked" to "true",
                "aria-selected" to "false",
                "aria-pressed" to "mixed",
                "aria-disabled" to "true",
            ) { +"x" }
        }

        // then
        input.simplifyHtml() sameAs semanticEvents {
            "li"(
                "aria-checked" to "true",
                "aria-selected" to "false",
                "aria-pressed" to "mixed",
                "aria-disabled" to "true",
            ) { +"x" }
        }
    }

    @Test
    fun `should drop an aria-hidden element and its entire subtree`() = runTest {
        // given — aria-hidden="true" is hidden from the accessibility tree;
        // drop it like display:none, even though `span` would normally unwrap.
        val input = semanticEvents(tagged = true) {
            "section"("id" to "keep") {
                "p" { +"visible" }
                "span"("aria-hidden" to "true") {
                    "a"("href" to "/x") { +"decorative" }
                    +"icon glyph"
                }
                "p" { +"also visible" }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then — the hidden subtree (and its link/text) is gone entirely.
        output sameAs semanticEvents {
            "section"("id" to "keep") {
                "p" { +"visible" }
                "p" { +"also visible" }
            }
        }
    }

    @Test
    fun `should keep an aria-hidden false element dropping only the attribute`() = runTest {
        // when — only "true" hides; "false" leaves the element, but the
        // attribute itself is not in the keep-set, so it is stripped.
        val input = semanticEvents(tagged = true) {
            "p"("aria-hidden" to "false") { +"shown" }
        }

        // then
        input.simplifyHtml() sameAs semanticEvents {
            "p" { +"shown" }
        }
    }

    // --- role promotion ----------------------------------------------------

    @Test
    fun `should promote a div with role heading and aria-level to a heading`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "div"("role" to "heading", "aria-level" to "3", "class" to "title") {
                +"Section title"
            }
        }

        // when
        val output = input.simplifyHtml()

        // then — promoted to h3, role/level/class dropped.
        output sameAs semanticEvents {
            "h3" { +"Section title" }
        }
    }

    @Test
    fun `should default role heading without level to h2`() = runTest {
        // when
        val input = semanticEvents(tagged = true) {
            "div"("role" to "heading") { +"Untitled level" }
        }

        // then — ARIA's default heading level is 2.
        input.simplifyHtml() sameAs semanticEvents {
            "h2" { +"Untitled level" }
        }
    }

    @Test
    fun `should promote landmark and list roles on generic elements`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "div"("role" to "navigation", "id" to "n") {
                "div"("role" to "list") {
                    "div"("role" to "listitem") { +"Home" }
                    "div"("role" to "listitem") { +"About" }
                }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then
        output sameAs semanticEvents {
            "nav"("id" to "n") {
                "ul" {
                    "li" { +"Home" }
                    "li" { +"About" }
                }
            }
        }
    }

    @Test
    fun `should promote role img to an image using the accessible name as alt`() = runTest {
        // given — an icon-only graphic whose only description is its aria-label.
        val input = semanticEvents(tagged = true) {
            "span"("role" to "img", "aria-label" to "Five star rating") {
                +"★★★★★"
            }
        }

        // when
        val output = input.simplifyHtml()

        // then — the descriptive children collapse to the accessible name.
        output sameAs semanticEvents {
            "img"("aria-label" to "Five star rating", "alt" to "Five star rating") { }
        }
    }

    @Test
    fun `should promote role separator to a thematic break`() = runTest {
        // when
        val input = semanticEvents(tagged = true) {
            "div"("role" to "separator") { }
        }

        // then
        input.simplifyHtml() sameAs semanticEvents {
            "hr" { }
        }
    }

    @Test
    fun `should unwrap a role presentation element keeping its children`() = runTest {
        // given — role="presentation" strips this element's own semantics (a
        // normally-preserved nav) while its content still flows through. Note we
        // do not propagate presentation to required-owned descendants, so this
        // demonstrates the single-element unwrap.
        val input = semanticEvents(tagged = true) {
            "nav"("role" to "presentation") {
                "p" { +"laid out" }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then — the nav semantics are gone, the paragraph survives.
        output sameAs semanticEvents {
            "p" { +"laid out" }
        }
    }

    @Test
    fun `should promote the ARIA grid family to a table`() = runTest {
        // given — a data grid built on divs.
        val input = semanticEvents(tagged = true) {
            "div"("role" to "table") {
                "div"("role" to "row") {
                    "div"("role" to "columnheader") { +"Name" }
                    "div"("role" to "columnheader") { +"Role" }
                }
                "div"("role" to "row") {
                    "div"("role" to "cell") { +"Ada" }
                    "div"("role" to "cell") { +"Pioneer" }
                }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then
        output sameAs semanticEvents {
            "table" {
                "tr" {
                    "th" { +"Name" }
                    "th" { +"Role" }
                }
                "tr" {
                    "td" { +"Ada" }
                    "td" { +"Pioneer" }
                }
            }
        }
    }

    @Test
    fun `should let a native tag win over a redundant role`() = runTest {
        // given — role on an element that already has the semantics natively.
        val input = semanticEvents(tagged = true) {
            "nav"("id" to "main-nav", "role" to "navigation", "class" to "menu") {
                +"links"
            }
        }

        // when
        val output = input.simplifyHtml()

        // then — stays nav with id; role and class dropped.
        output sameAs semanticEvents {
            "nav"("id" to "main-nav") { +"links" }
        }
    }

    // --- metadata extraction (frontmatter) ---------------------------------

    @Test
    fun `should emit frontmatter from html lang and head metadata`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "html"("lang" to "en") {
                "head" {
                    "title" { +"Hello" }
                    "meta"("name" to "author", "content" to "Alice") { }
                    "meta"("name" to "description", "content" to "A doc") { }
                }
                "body" {
                    "p" { +"body text" }
                }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then
        // The renderer reads YAML text from inside the frontmatter mark;
        // metadata is emitted there, not as attributes, so the final
        // Markdown shows actual key/value lines between the `---` fences.
        output sameAs semanticEvents {
            "frontmatter"("format" to "yaml") {
                +"lang: en\ntitle: Hello\nauthor: Alice\ndescription: A doc\n"
            }
            "p" { +"body text" }
        }
    }

    @Test
    fun `should skip meta without name or content attribute`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "html" {
                "head" {
                    "meta"("charset" to "utf-8") { }
                    "meta"("http-equiv" to "X-UA-Compatible", "content" to "IE=edge") { }
                    "meta"("name" to "description", "content" to "A doc") { }
                }
                "body" { "p" { +"text" } }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then — only the name+content meta survives
        output sameAs semanticEvents {
            "frontmatter"("format" to "yaml") {
                +"description: A doc\n"
            }
            "p" { +"text" }
        }
    }

    @Test
    fun `should drop technical noise meta names from frontmatter`() = runTest {
        // given — a mix of content-signal and technical / verification meta
        val input = semanticEvents(tagged = true) {
            "html" {
                "head" {
                    "meta"("name" to "description", "content" to "A doc") { }
                    "meta"("name" to "viewport", "content" to "width=device-width") { }
                    "meta"("name" to "generator", "content" to "SomeCMS 1.0") { }
                    "meta"("name" to "theme-color", "content" to "#fff") { }
                    "meta"("name" to "msapplication-TileColor", "content" to "#fff") { }
                    "meta"("name" to "robots", "content" to "index,follow") { }
                    "meta"("name" to "google-site-verification", "content" to "abc") { }
                    "meta"("name" to "author", "content" to "Alice") { }
                }
                "body" { "p" { +"text" } }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then — only the content-bearing names survive
        output sameAs semanticEvents {
            "frontmatter"("format" to "yaml") {
                +"description: A doc\nauthor: Alice\n"
            }
            "p" { +"text" }
        }
    }

    @Test
    fun `should not emit frontmatter when head yields no metadata`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "html" {
                "head" {
                    "meta"("charset" to "utf-8") { }
                }
                "body" { "p" { +"text" } }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then
        output sameAs semanticEvents {
            "p" { +"text" }
        }
    }

    @Test
    fun `should swallow loose text inside head`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "html"("lang" to "en") {
                "head" {
                    +"  whitespace and noise  "
                    "title" { +"T" }
                    +"more noise"
                }
                "body" { "p" { +"body" } }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then — no stray text in output
        output sameAs semanticEvents {
            "frontmatter"("format" to "yaml") {
                +"lang: en\ntitle: T\n"
            }
            "p" { +"body" }
        }
    }

    @Test
    fun `should trim title text whitespace`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "html" {
                "head" {
                    "title" { +"  Spaced Title  " }
                }
                "body" { "p" { +"x" } }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then
        output sameAs semanticEvents {
            "frontmatter"("format" to "yaml") {
                +"title: Spaced Title\n"
            }
            "p" { +"x" }
        }
    }

    @Test
    fun `should drop marks nested inside title and keep only their text`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "html" {
                "head" {
                    "title" {
                        +"Hello "
                        "em" { +"World" }
                    }
                }
                "body" { "p" { +"x" } }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then — the nested <em> is dropped, its text is captured into title
        output sameAs semanticEvents {
            "frontmatter"("format" to "yaml") {
                +"title: Hello World\n"
            }
            "p" { +"x" }
        }
    }

    @Test
    fun `should quote YAML-unsafe keys and values in frontmatter`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "html" {
                "head" {
                    // Key contains `:` — must be quoted in YAML output.
                    "meta"("name" to "og:title", "content" to "Hello") { }
                    // Value contains `:` and `"` — must be quoted/escaped.
                    "meta"("name" to "summary", "content" to "Title: \"Special\"") { }
                    // Value is a YAML reserved literal — must be quoted to
                    // stay a string instead of decoding to boolean `true`.
                    "meta"("name" to "live", "content" to "true") { }
                }
                "body" { "p" { +"x" } }
            }
        }

        // when
        val output = input.simplifyHtml()

        // then
        output sameAs semanticEvents {
            "frontmatter"("format" to "yaml") {
                +"\"og:title\": Hello\nsummary: \"Title: \\\"Special\\\"\"\nlive: \"true\"\n"
            }
            "p" { +"x" }
        }
    }

    // --- caller keep-attributes --------------------------------------------

    @Test
    fun `should keep a caller-requested attribute on otherwise-kept elements`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "body" {
                "a"("href" to "/x", "golemId" to "1", "class" to "c") { +"link" }
                "img"("src" to "/i.png", "alt" to "pic", "golemId" to "2") { }
                "em"("golemId" to "3") { +"italic" }
            }
        }

        // when
        val output = input.simplifyHtml(
            keepAttributes = setOf("golemId")
        )

        // then — golemId survives alongside each element's own whitelist
        output sameAs semanticEvents {
            "a"("href" to "/x", "golemId" to "1") { +"link" }
            "img"("src" to "/i.png", "alt" to "pic", "golemId" to "2") { }
            "em"("golemId" to "3") { +"italic" }
        }
    }

    @Test
    fun `should promote an otherwise-unwrapped element that carries a keep-attribute`() = runTest {
        // given — a span normally unwraps; one carrying the keep-attribute is
        // promoted to a preserved wrapper so the correlation id is not lost.
        val input = semanticEvents(tagged = true) {
            "body" {
                "span"("golemId" to "1", "class" to "c") { +"tracked" }
                "span"("class" to "c") { +"plain" }
            }
        }

        // when
        val output = input.simplifyHtml(
            keepAttributes = setOf("golemId")
        )

        // then — tracked span preserved (golemId only), plain span still unwrapped
        output sameAs semanticEvents {
            "span"("golemId" to "1") { +"tracked" }
            +"plain"
        }
    }

    @Test
    fun `should not resurrect a dropped or aria-hidden subtree via a keep-attribute`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "body" {
                "script"("golemId" to "1") { +"code" }
                "span"("aria-hidden" to "true", "golemId" to "2") { +"hidden" }
                "p" { +"visible" }
            }
        }

        // when
        val output = input.simplifyHtml(
            keepAttributes = setOf("golemId")
        )

        // then — the keep-attribute does not override drop / aria-hidden
        output sameAs semanticEvents {
            "p" { +"visible" }
        }
    }

}
