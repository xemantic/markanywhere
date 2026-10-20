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
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.dump.AccessibilityAnnotations
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.parse.parse
import com.xemantic.markanywhere.render.renderMarkdown
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import com.xemantic.markanywhere.render.MARKDOWN_BLOCK_CONTENT_TAGS
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class EncodeActionableRefsTest {

    @Test
    fun `LINK_BLOCK_CONTENT_TAGS must stay in sync with the renderer block-content tags`() {
        // given the renderer's block-content predicate set (the source of truth)
        // when compared against the encoder's hand-maintained mirror
        // then they are identical — a drift would silently misencode a
        // block-wrapping <a>'s ref (inline ref: scheme instead of ref= attribute)
        assert(LINK_BLOCK_CONTENT_TAGS == MARKDOWN_BLOCK_CONTENT_TAGS)
    }

    // --- ActionableRef.encode / decode --------------------------------------

    @Test
    fun `should encode ref and href into the ref scheme destination`() {
        // when
        val encoded = ActionableRef.encode("42", "/menu")

        // then
        encoded sameAs "ref:42:/menu"
    }

    @Test
    fun `should round-trip a relative href`() {
        // when
        val decoded = ActionableRef.decode(ActionableRef.encode("42", "/menu"))

        // then
        assert(decoded == ("42" to "/menu"))
    }

    @Test
    fun `should round-trip an absolute href keeping its own colons intact`() {
        // given — the href itself carries colons, so decode must split on the
        // first two ':' only
        val href = "https://example.com:8080/path?q=1"

        // when
        val decoded = ActionableRef.decode(ActionableRef.encode("7", href))

        // then
        assert(decoded == ("7" to href))
    }

    @Test
    fun `should round-trip a fragment-only href`() {
        // when
        val decoded = ActionableRef.decode(ActionableRef.encode("3", "#"))

        // then
        assert(decoded == ("3" to "#"))
    }

    @Test
    fun `should round-trip an empty href`() {
        // when
        val decoded = ActionableRef.decode(ActionableRef.encode("9", ""))

        // then
        assert(decoded == ("9" to ""))
    }

    @Test
    fun `should decode to null when the value is not ref-encoded`() {
        // when
        val decoded = ActionableRef.decode("https://example.com")

        // then
        assert(decoded == null)
    }

    @Test
    fun `should decode to null when the second separator is missing`() {
        // when — has the scheme but no ':' after the id
        val decoded = ActionableRef.decode("ref:42")

        // then
        assert(decoded == null)
    }

    @Test
    fun `should expose the expected scheme and attribute names`() {
        // then
        ActionableRef.SCHEME sameAs "ref"
        ActionableRef.ATTRIBUTE sameAs "ref"
    }

    // --- encodeActionableRefs transformer -----------------------------------

    @Test
    fun `should fold a link's ref into the destination and drop the dom ref`() = runTest {
        // given
        val input = semanticEvents {
            "a"(AccessibilityAnnotations.REF to "42", "href" to "/menu") { +"Menu" }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "a"("href" to "ref:42:/menu") { +"Menu" }
        }
    }

    @Test
    fun `should encode a hrefless link with an empty original href`() = runTest {
        // given — a JS control with no href, only a ref
        val input = semanticEvents {
            "a"(AccessibilityAnnotations.REF to "5") { +"Toggle" }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "a"("href" to "ref:5:") { +"Toggle" }
        }
    }

    @Test
    fun `should keep a block-wrapping link's ref as an attribute with the real href`() = runTest {
        // given — a link wrapping a heading renders as a raw <a> tag, which can
        // carry attributes, so the ref stays an attribute and the href is real
        val input = semanticEvents {
            "a"(AccessibilityAnnotations.REF to "9", "href" to "/live") {
                +"LIVE"
                "h2" { +"Headline" }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "a"("href" to "/live", "ref" to "9") {
                +"LIVE"
                "h2" { +"Headline" }
            }
        }
    }

    @Test
    fun `should keep an image-only link inline as the scheme destination`() = runTest {
        // given — `<img>` is NOT block content, so a link wrapping only an image
        // stays an inline Markdown link (`[![alt](src)](url)`); its ref folds
        // into the destination, it does not become a raw tag. Guards the
        // deliberate exclusion of `img` from LINK_BLOCK_CONTENT_TAGS.
        val input = semanticEvents {
            "a"(AccessibilityAnnotations.REF to "8", "href" to "/live") {
                "img"("src" to "/p.jpg", "alt" to "Photo") {}
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "a"("href" to "ref:8:/live") {
                "img"("src" to "/p.jpg", "alt" to "Photo") {}
            }
        }
    }

    @Test
    fun `should detect block content nested deeper inside the link subtree`() = runTest {
        // given — the block-vs-inline decision scans the whole `<a>` subtree, not
        // just its direct children, so a heading wrapped in an inline `span`
        // still forces the raw-tag (attribute) form.
        val input = semanticEvents {
            "a"(AccessibilityAnnotations.REF to "9", "href" to "/x") {
                "span" {
                    "h2" { +"Deep" }
                }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "a"("href" to "/x", "ref" to "9") {
                "span" {
                    "h2" { +"Deep" }
                }
            }
        }
    }

    @Test
    fun `should encode consecutive ref links independently`() = runTest {
        // given — the link buffer must reset between links: an inline one and a
        // block-wrapping one in sequence each get their own encoding.
        val input = semanticEvents {
            "a"(AccessibilityAnnotations.REF to "1", "href" to "/a") { +"Inline" }
            "a"(AccessibilityAnnotations.REF to "2", "href" to "/b") {
                "h2" { +"Block" }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "a"("href" to "ref:1:/a") { +"Inline" }
            "a"("href" to "/b", "ref" to "2") {
                "h2" { +"Block" }
            }
        }
    }

    @Test
    fun `should encode a ref-bearing control nested inside a block-wrapping link`() = runTest {
        // given — the link pre-pass re-emits a block link's subtree unchanged;
        // a ref-bearing control inside it is still encoded (to the short
        // attribute) by the downstream non-link pass.
        val input = semanticEvents {
            "a"(AccessibilityAnnotations.REF to "9", "href" to "/x") {
                "h2" { +"Title" }
                "button"(AccessibilityAnnotations.REF to "10") { +"Act" }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "a"("href" to "/x", "ref" to "9") {
                "h2" { +"Title" }
                "button"("ref" to "10") { +"Act" }
            }
        }
    }

    @Test
    fun `should encode a ref link nested inside an inline ref link with the scheme form`() = runTest {
        // given — a nested anchor never comes from parsed HTML (the tree builder
        // auto-closes an open anchor before nesting another), but a scripted DOM
        // can carry one, and dumps capture live DOMs
        val input = semanticEvents {
            "a"(AccessibilityAnnotations.REF to "1", "href" to "/outer") {
                +"outer "
                "a"(AccessibilityAnnotations.REF to "2", "href" to "/inner") { +"inner" }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then — the inner link folds its own ref into its destination like any
        // inline link; the attribute form would lose the ref at render time,
        // since a Markdown `[label](url)` carries no attributes
        output sameAs semanticEvents {
            "a"("href" to "ref:1:/outer") {
                +"outer "
                "a"("href" to "ref:2:/inner") { +"inner" }
            }
        }
    }

    @Test
    fun `should encode an inline ref link nested inside a block-wrapping ref link`() = runTest {
        // given
        val input = semanticEvents {
            "a"(AccessibilityAnnotations.REF to "1", "href" to "/outer") {
                "h2" { +"Headline" }
                "a"(AccessibilityAnnotations.REF to "2", "href" to "/inner") { +"inner" }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then — each link picks its form by its OWN subtree: the outer wraps a
        // heading (raw tag, attribute form), the inner wraps only text (scheme)
        output sameAs semanticEvents {
            "a"("href" to "/outer", "ref" to "1") {
                "h2" { +"Headline" }
                "a"("href" to "ref:2:/inner") { +"inner" }
            }
        }
    }

    @Test
    fun `should encode a block-wrapping ref link nested inside a block-wrapping ref link`() = runTest {
        // given — the outer link's block detection sees the heading only through
        // the inner link's subtree
        val input = semanticEvents {
            "a"(AccessibilityAnnotations.REF to "1", "href" to "/outer") {
                "a"(AccessibilityAnnotations.REF to "2", "href" to "/inner") {
                    "h2" { +"Headline" }
                }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then — both spill as raw tags, so both carry the attribute form
        output sameAs semanticEvents {
            "a"("href" to "/outer", "ref" to "1") {
                "a"("href" to "/inner", "ref" to "2") {
                    "h2" { +"Headline" }
                }
            }
        }
    }

    @Test
    fun `should leave a link without a ref untouched`() = runTest {
        // given
        val input = semanticEvents {
            "a"("href" to "/plain") { +"Plain" }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "a"("href" to "/plain") { +"Plain" }
        }
    }

    @Test
    fun `should rename a hrefless control's ref to the short attribute`() = runTest {
        // given
        val input = semanticEvents {
            "button"(AccessibilityAnnotations.REF to "13", "type" to "submit") { +"Send" }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "button"("type" to "submit", "ref" to "13") { +"Send" }
        }
    }

    @Test
    fun `should pass through elements and text that carry no ref`() = runTest {
        // given
        val input = semanticEvents {
            "p" {
                +"before "
                "span"("class" to "x") { +"plain" }
                +" after"
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "p" {
                +"before "
                "span"("class" to "x") { +"plain" }
                +" after"
            }
        }
    }

    @Test
    fun `should carry a list item's ref on a span around its label`() = runTest {
        // given - a Markdown list item carries no attributes, so its ref would
        // vanish with the `li` the renderer writes as `- `
        val input = semanticEvents {
            "ul" {
                "li"(AccessibilityAnnotations.REF to "23", "aria-selected" to "false") { +"Berlin Hbf." }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "ul" {
                "li" {
                    tag("span", "aria-selected" to "false", "ref" to "23") { +"Berlin Hbf." }
                }
            }
        }
    }

    @Test
    fun `should keep a list item's nested list outside its ref span`() = runTest {
        // given - a raw inline tag cannot wrap a Markdown block
        val input = semanticEvents {
            "ul" {
                "li"(AccessibilityAnnotations.REF to "5") {
                    +"Parent "
                    "em" { +"item" }
                    "ul" { "li" { +"child" } }
                }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "ul" {
                "li" {
                    tag("span", "ref" to "5") {
                        +"Parent "
                        "em" { +"item" }
                    }
                    "ul" { "li" { +"child" } }
                }
            }
        }
    }

    @Test
    fun `should keep the ref of a list item with no label`() = runTest {
        // given - an option nothing names: the ref is still something to click
        val input = semanticEvents {
            "ul" {
                "li"(AccessibilityAnnotations.REF to "6") { }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "ul" {
                "li" {
                    tag("span", "ref" to "6") { }
                }
            }
        }
    }

    @Test
    fun `should carry a heading's ref on a span around its text`() = runTest {
        // given
        val input = semanticEvents {
            "h3"(AccessibilityAnnotations.REF to "7") { +"Settings" }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "h3" {
                tag("span", "ref" to "7") { +"Settings" }
            }
        }
    }

    @Test
    fun `should keep the ref of a tagged list item on the item itself`() = runTest {
        // given - a literal `<li>` tag holds its own attributes
        val input = semanticEvents(tagged = true) {
            "li"(AccessibilityAnnotations.REF to "8") { +"Item" }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents(tagged = true) {
            "li"("ref" to "8") { +"Item" }
        }
    }

    @Test
    fun `should end a list item's ref span at a line break`() = runTest {
        // given - a raw inline tag cannot span a hard break: the parser closes
        // inline HTML at the line end, and the `</span>` would come back as text
        val input = semanticEvents {
            "ul" {
                "li"(AccessibilityAnnotations.REF to "1") {
                    +"Berlin Hbf"
                    "br" { }
                    +"Station"
                }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "ul" {
                "li" {
                    tag("span", "ref" to "1") { +"Berlin Hbf" }
                    "br" { }
                    +"Station"
                }
            }
        }
    }

    @Test
    fun `should round-trip a ref-bearing paragraph holding a line break`() = runTest {
        // given
        val input = semanticEvents {
            "p"(AccessibilityAnnotations.REF to "2") {
                +"a"
                "br" { }
                +"b"
            }
        }

        // when
        val markdown = input.encodeActionableRefs().renderMarkdown()

        // then
        markdown sameAs "<span ref=\"2\">a</span>  \nb"
        flowOf(markdown).parse().renderMarkdown() sameAs markdown
    }

    @Test
    fun `should not leave a literal span close when re-parsing a list item holding a line break`() = runTest {
        // given
        val input = semanticEvents {
            "ul" {
                "li"(AccessibilityAnnotations.REF to "1") {
                    +"Berlin Hbf"
                    "br" { }
                    +"Station"
                }
            }
        }

        // when
        val reparsed = flowOf(input.encodeActionableRefs().renderMarkdown()).parse().toList()

        // then - the span closes on its own line; a hard break in a list item
        // does not survive the re-parse either way (a separate parser gap)
        val text = reparsed.filterIsInstance<SemanticEvent.Text>().joinToString("") { it.text }
        assert("</span>" !in text)
        assert(reparsed.count { it is SemanticEvent.Mark && it.name == "span" } == 1)
    }

    @Test
    fun `should round-trip a ref-bearing ordered list item holding a code block`() = runTest {
        // given
        val input = semanticEvents {
            "ol" {
                "li"(AccessibilityAnnotations.REF to "1") {
                    +"x"
                    "pre" { "code" { +"code" } }
                }
            }
        }

        // when
        val markdown = input.encodeActionableRefs().renderMarkdown()

        // then
        flowOf(markdown).parse().renderMarkdown() sameAs markdown
    }

    @Test
    fun `should leave a block nested in an inline wrapper outside the ref span`() = runTest {
        // given - the span opens before it can see what the wrapper holds
        val input = semanticEvents {
            "ul" {
                "li"(AccessibilityAnnotations.REF to "1") {
                    "a"("href" to "/a") { "h3" { +"Title" } }
                }
                "li"(AccessibilityAnnotations.REF to "2") {
                    +"lead "
                    "strong" {
                        +"x"
                        "p" { +"y" }
                    }
                }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then - the label is what precedes the wrapper
        output sameAs semanticEvents {
            "ul" {
                "li" {
                    tag("span", "ref" to "1") { }
                    "a"("href" to "/a") { "h3" { +"Title" } }
                }
                "li" {
                    tag("span", "ref" to "2") { +"lead " }
                    "strong" {
                        +"x"
                        "p" { +"y" }
                    }
                }
            }
        }
    }

    @Test
    fun `should carry the refs of a table cell and a caption on spans`() = runTest {
        // given
        val input = semanticEvents {
            "table" {
                "caption"(AccessibilityAnnotations.REF to "1") { +"Prices" }
                "tbody" {
                    "tr" {
                        "td"(AccessibilityAnnotations.REF to "2") { +"5" }
                    }
                }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "table" {
                "caption" { tag("span", "ref" to "1") { +"Prices" } }
                "tbody" {
                    "tr" {
                        "td" { tag("span", "ref" to "2") { +"5" } }
                    }
                }
            }
        }
    }

    @Test
    fun `should carry a table row's ref on a span in its first cell`() = runTest {
        // given - a `| … |` row carries no attributes, and no span can sit
        // between cells, so the ref goes into the row's first cell
        val input = semanticEvents {
            "table" {
                "tr"(AccessibilityAnnotations.REF to "1", "aria-selected" to "true") {
                    "td" { +"a" }
                    "td" { +"b" }
                }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "table" {
                "tr" {
                    "td" { tag("span", "aria-selected" to "true", "ref" to "1") { +"a" } }
                    "td" { +"b" }
                }
            }
        }
    }

    @Test
    fun `should keep a table row's ref beside its first cell's own ref`() = runTest {
        // given - the row and its first cell are both actionable
        val input = semanticEvents {
            "table" {
                "tr"(AccessibilityAnnotations.REF to "11") {
                    "td"(AccessibilityAnnotations.REF to "12") { +"a" }
                }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then - each keeps its own span
        output sameAs semanticEvents {
            "table" {
                "tr" {
                    "td" {
                        tag("span", "ref" to "11") { }
                        tag("span", "ref" to "12") { +"a" }
                    }
                }
            }
        }
    }

    @Test
    fun `should carry an image's ref on a span around it`() = runTest {
        // given - `![alt](src)` carries no attributes
        val input = semanticEvents {
            "p" {
                "img"(
                    "alt" to "Jane",
                    "aria-label" to "Jane",
                    "aria-pressed" to "false",
                    AccessibilityAnnotations.REF to "8",
                ) { }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then - the name the alt already says is not said twice
        output sameAs semanticEvents {
            "p" {
                tag("span", "aria-pressed" to "false", "ref" to "8") {
                    "img"("alt" to "Jane") { }
                }
            }
        }
    }

    @Test
    fun `should carry the ref of a list item nested in a ref-bearing list item`() = runTest {
        // given
        val input = semanticEvents {
            "ul" {
                "li"(AccessibilityAnnotations.REF to "1") {
                    +"Parent"
                    "ul" {
                        "li"(AccessibilityAnnotations.REF to "2") { +"child" }
                    }
                }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "ul" {
                "li" {
                    tag("span", "ref" to "1") { +"Parent" }
                    "ul" {
                        "li" { tag("span", "ref" to "2") { +"child" } }
                    }
                }
            }
        }
    }

    @Test
    fun `should drop a moved aria-label its span already shows as text`() = runTest {
        // given - labelActionableElements wrote the option's name as its text
        val input = semanticEvents {
            "ul" {
                "li"("aria-label" to "Berlin  Hbf. ", AccessibilityAnnotations.REF to "1") { +"Berlin Hbf." }
                "li"("aria-label" to "Berlin Südkreuz", AccessibilityAnnotations.REF to "2") { +"Südkreuz" }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then - a name that says more than the text stays
        output sameAs semanticEvents {
            "ul" {
                "li" { tag("span", "ref" to "1") { +"Berlin Hbf." } }
                "li" { tag("span", "aria-label" to "Berlin Südkreuz", "ref" to "2") { +"Südkreuz" } }
            }
        }
    }

    @Test
    fun `should keep a cell's structural attributes on the cell`() = runTest {
        // given - a date-picker grid: the renderer reads `colspan` from the
        // cell itself, so only the ref and the ARIA state may move
        val input = semanticEvents {
            "table" {
                "thead" { "tr" { "th" { +"Mo" }; "th" { +"Tu" }; "th" { +"We" } } }
                "tbody" {
                    "tr" {
                        "td"("colspan" to "2", "aria-selected" to "true", AccessibilityAnnotations.REF to "7") { +"1" }
                        "td"(AccessibilityAnnotations.REF to "8") { +"2" }
                    }
                }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "table" {
                "thead" { "tr" { "th" { +"Mo" }; "th" { +"Tu" }; "th" { +"We" } } }
                "tbody" {
                    "tr" {
                        "td"("colspan" to "2") { tag("span", "aria-selected" to "true", "ref" to "7") { +"1" } }
                        "td" { tag("span", "ref" to "8") { +"2" } }
                    }
                }
            }
        }
    }

    @Test
    fun `should round-trip a ref-bearing spanning cell`() = runTest {
        // given
        val input = semanticEvents {
            "table" {
                "thead" { "tr" { "th" { +"Mo" }; "th" { +"Tu" }; "th" { +"We" } } }
                "tbody" {
                    "tr" {
                        "td"("colspan" to "2", AccessibilityAnnotations.REF to "7") { +"1" }
                        "td"(AccessibilityAnnotations.REF to "8") { +"2" }
                    }
                }
            }
        }

        // when
        val markdown = input.encodeActionableRefs().renderMarkdown()

        // then - "2" stays under We
        markdown sameAs """
            | Mo | Tu | We |
            | --- | --- | --- |
            | <span ref="7">1</span> |  | <span ref="8">2</span> |
        """.trimIndent()
        flowOf(markdown).parse().renderMarkdown() sameAs markdown
    }

    @Test
    fun `should wrap the leading content of a list item's first paragraph`() = runTest {
        // given
        val input = semanticEvents {
            "ul" {
                "li"(AccessibilityAnnotations.REF to "2") { "p" { +"Only para" } }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "ul" {
                "li" { "p" { tag("span", "ref" to "2") { +"Only para" } } }
            }
        }
    }

    @Test
    fun `should leave the ref span out when the item's first child is a control of its own`() = runTest {
        // given - the button is what an agent clicks; an empty span beside it
        // would only add a second handle that says nothing
        val input = semanticEvents {
            "ul" {
                "li"(AccessibilityAnnotations.REF to "1") {
                    tag("button", AccessibilityAnnotations.REF to "2") { +"Pick" }
                }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "ul" {
                "li" { tag("button", "ref" to "2") { +"Pick" } }
            }
        }
    }

    @Test
    fun `should round-trip a ref-bearing task list item`() = runTest {
        // given - the checkbox renders as a raw tag at the start of the line
        val input = semanticEvents {
            "ul" {
                "li"(AccessibilityAnnotations.REF to "1") {
                    tag("input", "type" to "checkbox", "checked" to "", "disabled" to "") { }
                    +"done"
                }
            }
        }

        // when
        val markdown = input.encodeActionableRefs().renderMarkdown()

        // then
        assert("<span ref=\"1\">done</span>" in markdown)
        flowOf(markdown).parse().renderMarkdown() sameAs markdown
    }

    @Test
    fun `should round-trip a ref-bearing list item followed by a paragraph`() = runTest {
        // given
        val input = semanticEvents {
            "ul" {
                "li"(AccessibilityAnnotations.REF to "1") {
                    +"Label"
                    "p" { +"Description" }
                }
            }
        }

        // when
        val markdown = input.encodeActionableRefs().renderMarkdown()

        // then
        markdown sameAs "- <span ref=\"1\">Label</span>\n  Description"
        flowOf(markdown).parse().renderMarkdown() sameAs markdown
    }

    @Test
    fun `should compare a moved aria-label across a non-breaking space`() = runTest {
        // given
        val input = semanticEvents {
            "ul" {
                "li"("aria-label" to "Berlin Hbf.", AccessibilityAnnotations.REF to "1") { +"Berlin Hbf." }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "ul" {
                "li" { tag("span", "ref" to "1") { +"Berlin Hbf." } }
            }
        }
    }

}
