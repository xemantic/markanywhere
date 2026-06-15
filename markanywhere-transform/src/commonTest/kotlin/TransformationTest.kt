/*
 * Copyright 2025-2026 Kazimierz Pogoda / Xemantic
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

package com.xemantic.markanywhere.transform

import com.xemantic.kotlin.test.sameAsHtml
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.parse.parse
import com.xemantic.markanywhere.render.render
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class TransformationTest {

    @Test
    fun `should transform the README example`() = runTest {
        // given - the running example from the project README
        //   "Transforming the event stream" section
        val markdown = """
            # Hello

            A *streaming* parser, <b>live</b>.
        """.trimIndent()

        // each reusable rule set is an extension on TransformerBuilder
        fun TransformerBuilder.demoteHeadings() {
            match("h1") { "h2" { children() } } // re-emit <h1> as <h2>, keeping its content
        }
        fun TransformerBuilder.emphasizeToStrong() {
            match("em") { "strong" { children() } } // re-emit <em> as <strong>
        }

        // when
        val html = flowOf(markdown)
            .parse()
            .transform {
                demoteHeadings()
                emphasizeToStrong()
                passthrough() // copy every other mark and its text verbatim
            }
            .render()

        // then - the Markdown `*streaming*` is rewritten to <strong>, while the
        //   literal <b> flows through untouched via passthrough
        html sameAsHtml """
            <h2>
              Hello
            </h2>
            <p>
              A <strong>streaming</strong> parser, <b>live</b>.
            </p>
        """.trimIndent()
    }

    // fixture
    fun testHtmlEvents() = semanticEvents(tagged = true) { // tagged because this is what HTML parsing would return
        "html"("lang" to "en") {
            "head" {
                "title" { +"Document title" }
            }
            "body" {
                "nav" {
                    "ul" {
                        "li" { "a"("href" to "/") { +"Home" } }
                    }
                    "ul" {
                        "li" { "a"("href" to "/about") { +"About" } }
                    }
                }
                "h1" { +"Title" }
                "p" {
                    +"Paragraph text\nanother line "; "em" { +"emphasis" }
                }
            }
        }
    }

    @Test
    fun `should drop all events when no rules are registered`() = runTest {
        // given
        val events = semanticEvents {
            "p" {
                +"hello "
                "em" { +"world" }
            }
        }

        // when
        val transformed = events.transform {}

        // then
        transformed sameAs semanticEvents {}
    }

    @Test
    fun `should copy all events on passthrough`() = runTest {
        // given
        val events = testHtmlEvents()

        // when
        val transformed = events.transform {
            passthrough()
        }

        // then
        transformed sameAs testHtmlEvents()
    }

    @Test
    fun `should skip an unmatched mark and its entire subtree`() = runTest {
        // given
        val events = semanticEvents {
            "p" { +"hello" }
            "div" { +"world" }
        }

        // when
        val transformed = events.transform {
            match("p") {
                "p" { children() }
            }
            matchText { +it }
        }

        // then - p has a rule and survives; div has none, so div and its whole
        //   subtree are skipped - "world" is never reached, even though a global
        //   matchText is registered, because descent happens only via children()
        transformed sameAs semanticEvents {
            "p" { +"hello" }
        }
    }

    @Test
    fun `should descend through unmatched marks only via an explicit wildcard rule`() = runTest {
        // given - a 'div' wrapping a 'p'; the div itself has no dedicated rule
        fun input() = semanticEvents {
            "div" {
                "p" { +"hello" }
            }
        }

        // when - without a wildcard, the unmatched div stops traversal
        val withoutWildcard = input().transform {
            match("p") { "p" { children() } }
            matchText { +it }
        }

        // when - an explicit wildcard descends through div without emitting it
        val withWildcard = input().transform {
            match("p") { "p" { children() } }
            match("*") { children() }
            matchText { +it }
        }

        // then - no wildcard: div and its entire subtree are skipped
        withoutWildcard sameAs semanticEvents {}
        // then - wildcard: traversal passes through div (which emits nothing)
        //   and reaches the inner p
        withWildcard sameAs semanticEvents {
            "p" { +"hello" }
        }
    }

    @Test
    fun `should emit text only via a matchText rule and not via children alone`() = runTest {
        // given - a paragraph whose mark is matched and re-emitted with children()
        fun input() = semanticEvents {
            "p" { +"hello" }
        }

        // when - no matchText rule is registered
        val withoutMatchText = input().transform {
            match("p") {
                "p" { children() }
            }
        }

        // when - a matchText rule is registered
        val withMatchText = input().transform {
            match("p") {
                "p" { children() }
            }
            matchText { +it }
        }

        // then - children() recurses into the subtree but never emits text on its
        //   own; a text event survives only when a matchText rule handles it
        withoutMatchText sameAs semanticEvents {
            "p" {}
        }
        withMatchText sameAs semanticEvents {
            "p" { +"hello" }
        }
    }

    @Test
    fun `should copy all events while adding sequence number`() = runTest {
        // given
        val events = semanticEvents {
            "section" {
                "h2" { +"Title" }
                "p"("class" to "lead") {
                    +"text "
                    "em" { +"word" }
                }
            }
        }

        // when - data-seq is appended in document (pre)order, preserving any
        //   existing attribute on the element
        val transformed = events.transform {
            var seq = 0
            match("*") {
                it.name(it.attributes + ("data-seq" to (seq++).toString())) {
                    children()
                }
            }
            matchText { +it }
        }

        // then
        transformed sameAs semanticEvents {
            "section"("data-seq" to "0") {
                "h2"("data-seq" to "1") { +"Title" }
                "p"("class" to "lead", "data-seq" to "2") {
                    +"text "
                    "em"("data-seq" to "3") { +"word" }
                }
            }
        }
    }

    @Test
    fun `should let the first matching mark matcher win`() = runTest {
        // given
        val events = semanticEvents {
            "p" { +"hello" }
        }

        // when - two rules match the same mark; only the first is applied
        val transformed = events.transform {
            match("*") {
                it.name(it.attributes) {
                    children()
                }
            }
            match("*") { +"Should be swallowed" }
            matchText { +it }
        }

        // then
        transformed sameAs semanticEvents {
            "p" { +"hello" }
        }
    }

    @Test
    fun `should emit only the top-level name when children is not called`() = runTest {
        // given
        val events = semanticEvents {
            "section" {
                "h2" { +"Title" }
            }
        }

        // when - the matched rule never calls children(), so descent stops at the root
        val transformed = events.transform {
            match("*") {
                +it.name
            }
        }

        // then - only the root is visited; the nested h2 is never reached
        transformed sameAs semanticEvents {
            +"section"
        }
    }

    @Test
    fun `should recursively emit all names when children is called`() = runTest {
        // given
        val events = semanticEvents {
            "section" {
                "h2" { +"Title" }
                "p" {
                    "em" { +"word" }
                }
            }
        }

        // when - children() drives descent into the subtree
        val transformed = events.transform {
            match("*") {
                +it.name
                children()
            }
        }

        // then - every element name in document (pre)order
        transformed sameAs semanticEvents {
            +"section"
            +"h2"
            +"p"
            +"em"
        }
    }

    @Test
    fun `should extract title from HTML`() = runTest {
        // given
        val events = semanticEvents {
            "html" {
                "head" {
                    "title" { +"Document title" }
                }
                "body" {
                    "p" { +"ignored body text" }
                }
            }
        }

        // when - descent is explicit: the wildcard rule opts into transparent
        //   traversal so the deeply nested title is reached, while the specific
        //   title rule (registered first, so it wins) switches into "titleText"
        //   mode for its content
        val transformed = events.transform {
            match("title") { children(mode = "titleText") }
            match("*") { children() }
            matchText(mode = "titleText") { +it }
        }

        // then - only the title's text is emitted: default-mode text has no
        //   matchText rule and is dropped; "titleText"-mode text survives
        transformed sameAs semanticEvents {
            +"Document title"
        }
    }

    @Test
    fun `should add ARIA label to navigation`() = runTest {
        // given
        val events = semanticEvents {
            "nav" {
                "a"("href" to "/") { +"Home" }
            }
            "footer" { +"Made with love" }
        }

        // when - nav gets a rewritten attribute set; everything else is copied
        //   verbatim by passthrough (and the explicit nav rule, registered first,
        //   wins over the passthrough wildcard)
        val transformed = events.transform {
            match("nav") {
                "nav"("aria-label" to "Main") {
                    children()
                }
            }
            passthrough()
        }

        // then
        transformed sameAs semanticEvents {
            "nav"("aria-label" to "Main") {
                "a"("href" to "/") { +"Home" }
            }
            "footer" { +"Made with love" }
        }
    }

    @Test
    fun `should preserve each mark's tagging status on passthrough`() = runTest {
        // given - one untagged (semantic) mark and one tagged (HTML-derived) mark
        //   in the same flow
        val events = semanticEvents {
            "p" { +"semantic" }
            tag("div") { +"html" }
        }

        // when - passthrough copies each mark verbatim, deriving isTagged from the
        //   input mark itself; there is no transform-level flag to override it
        val transformed = events.transform {
            passthrough()
        }

        // then - the untagged mark stays untagged, the tagged mark stays tagged
        transformed sameAs semanticEvents {
            "p" { +"semantic" }
            tag("div") { +"html" }
        }
    }

    @Test
    fun `should emit untagged marks by default`() = runTest {
        // given
        val events = semanticEvents {
            "section" {
                "h2" { +"Title" }
                "p" { +"text" }
            }
        }

        // when - marks re-emitted via the "name" { } builder are untagged; there
        //   is no transform-level flag to flip them
        val transformed = events.transform {
            match("*") {
                it.name(it.attributes) {
                    children()
                }
            }
            matchText { +it }
        }

        // then
        transformed sameAs semanticEvents {
            "section" {
                "h2" { +"Title" }
                "p" { +"text" }
            }
        }
    }

    @Test
    fun `should tag a single mark despite the untagged default`() = runTest {
        // given
        val events = semanticEvents {
            "p" { +"hello" }
        }

        // when - marks are untagged by default, but tag() forces this one tagged
        val transformed = events.transform {
            match("p") {
                tag("p") { children() }
            }
            matchText { +it }
        }

        // then
        transformed sameAs semanticEvents {
            tag("p") { +"hello" }
        }
    }

    @Test
    fun `should untag a single mark inside a tagged block`() = runTest {
        // given
        val events = semanticEvents {
            "p" { +"hello" }
        }

        // when - the enclosing tagged { } turns tagging on; untagged { } forces
        //   this single mark back to untagged
        val transformed = events.transform {
            match("p") {
                tagged {
                    untagged {
                        "p" { children() }
                    }
                }
            }
            matchText { +it }
        }

        // then
        transformed sameAs semanticEvents {
            "p" { +"hello" }
        }
    }

    @Test
    fun `should emit a tagged mark with attributes via tag`() = runTest {
        // given
        val events = semanticEvents {
            "link" {}
        }

        // when - tag() always emits a tagged mark and preserves its attributes,
        //   even though the transform default is untagged
        val transformed = events.transform {
            match("link") {
                tag("a", "href" to "/home") {}
            }
        }

        // then
        transformed sameAs semanticEvents {
            tag("a", "href" to "/home") {}
        }
    }

    @Test
    fun `should tag marks inside a tagged block and restore the default after`() = runTest {
        // given
        val events = semanticEvents {
            "root" {}
        }

        // when - marks are untagged by default; only the mark inside tagged { } is
        //   tagged, marks before and after keep the untagged default
        val transformed = events.transform {
            match("root") {
                "before" {}
                tagged {
                    "inside" {}
                }
                "after" {}
            }
        }

        // then
        transformed sameAs semanticEvents {
            "before" {}
            tag("inside") {}
            "after" {}
        }
    }

    @Test
    fun `should restore the enclosing default when tagged and untagged blocks are nested`() = runTest {
        // given
        val events = semanticEvents {
            "root" {}
        }

        // when - default untagged; tagged { } turns tagging on, a nested untagged { }
        //   turns it off again, and exiting each block restores the enclosing default
        val transformed = events.transform {
            match("root") {
                "a" {}
                tagged {
                    "b" {}
                    untagged {
                        "c" {}
                    }
                    "d" {}
                }
                "e" {}
            }
        }

        // then
        transformed sameAs semanticEvents {
            "a" {}
            tag("b") {}
            "c" {}
            tag("d") {}
            "e" {}
        }
    }

    @Test
    fun `should emit a synthetic mark after the matched subtree via afterClose`() = runTest {
        // given
        val events = semanticEvents {
            "section" { "p" { +"body" } }
        }

        // when - afterClose runs once the matched subtree has fully closed,
        //   so the synthetic hr is emitted as a sibling after section
        val transformed = events.transform {
            match("section") {
                "section" { children() }
                afterClose { "hr" {} }
            }
            match("p") { "p" { children() } }
            matchText { +it }
        }

        // then
        transformed sameAs semanticEvents {
            "section" { "p" { +"body" } }
            "hr" {}
        }
    }

    @Test
    fun `should expose subtree text collected before afterClose runs`() = runTest {
        // given
        val events = semanticEvents {
            "head" {
                "title" {
                    +"Hello "
                    "em" { +"World" }
                }
            }
        }

        // when - title text (including text nested in marks) is captured into a
        //   builder via a mode-scoped matchText; afterClose reads the complete
        //   text once head closes and emits a synthetic summary
        val transformed = events.transform {
            val collected = StringBuilder()
            match("head") {
                children(mode = "capture")
                afterClose {
                    "summary" { +collected.toString() }
                }
            }
            match("*", mode = "capture") { children(mode = "capture") }
            matchText(mode = "capture") { collected.append(it) }
        }

        // then - head, title and em marks are all dropped; only the summary
        //   built from the captured text survives
        transformed sameAs semanticEvents {
            "summary" { +"Hello World" }
        }
    }

    @Test
    fun `should run nested afterClose hooks innermost-first`() = runTest {
        // given
        val events = semanticEvents {
            "outer" { "inner" { +"x" } }
        }

        // when - each level re-emits itself and registers an afterClose; the
        //   inner hook fires at the inner unmark, the outer at the outer unmark
        val transformed = events.transform {
            match("outer") {
                "outer" { children() }
                afterClose { "outer-done" {} }
            }
            match("inner") {
                "inner" { children() }
                afterClose { "inner-done" {} }
            }
            matchText { +it }
        }

        // then - inner-done is emitted inside outer (after inner closes);
        //   outer-done follows after outer closes
        transformed sameAs semanticEvents {
            "outer" {
                "inner" { +"x" }
                "inner-done" {}
            }
            "outer-done" {}
        }
    }

    @Test
    fun `should expose attribute access via subscript and attributes map`() = runTest {
        // given
        val events = semanticEvents {
            "a"("href" to "/foo") { +"link" }
        }

        // when
        val viaSubscript = events.transform {
            match("a") {
                "a"("href" to (it["href"] ?: "")) {
                    children()
                }
            }
            matchText { +it }
        }
        val viaAttributesMap = events.transform {
            match("a") {
                "a"("href" to (it.attributes["href"] ?: "")) {
                    children()
                }
            }
            matchText { +it }
        }

        // then - both accessors yield the correct attribute value (so they also agree)
        val expected = semanticEvents {
            "a"("href" to "/foo") { +"link" }
        }
        viaSubscript sameAs expected
        viaAttributesMap sameAs expected
    }

    @Test
    fun `should transform XML to another XML`() = runTest {
        // given
        val events = semanticEvents {
            "document" {
                "list" {
                    "item" { +"Foo" }
                    "item" { +"Bar" }
                }
            }
        }

        // when
        val transformed = events.transform {

            match("list") {
                "section" {
                    if (it["type"] == "ordered") {
                        "ol" {
                            children()
                        }
                    } else {
                        "ul" {
                            children()
                        }
                    }
                }
            }

            match("item") {
                "li" {
                    children()
                }
            }

            // document has no dedicated rule — the wildcard (registered last so
            // the specific list/item rules win) descends through it transparently
            // so its children are reached without re-emitting document itself
            match("*") { children() }

            matchText { +it }

        }

        // then
        transformed sameAs semanticEvents {
            "section" {
                "ul" {
                    "li" { +"Foo" }
                    "li" { +"Bar" }
                }
            }
        }
    }

    @Test
    fun `should match a mark via a predicate expression`() = runTest {
        // given - two paragraphs, only one carrying role=note
        val events = semanticEvents {
            "p"("role" to "note") { +"kept" }
            "p" { +"dropped" }
        }

        // when - the predicate matches only the p whose role attribute is "note";
        //   the other p has no matching rule and is skipped with its subtree
        val transformed = events.transform {
            match({ name == "p" && this["role"] == "note" }) {
                "aside" { children() }
            }
            matchText { +it }
        }

        // then
        transformed sameAs semanticEvents {
            "aside" { +"kept" }
        }
    }

    @Test
    fun `should prefer a mode-specific matcher over an earlier default-mode matcher`() = runTest {
        // given
        fun input() = semanticEvents {
            "root" { "p" { +"text" } }
        }

        // when - only a default-mode wildcard exists for the inner p, so selection
        //   falls back to it (the second matcher pass matches on mode == null)
        val fallback = input().transform {
            match("root") { children(mode = "special") }
            match("*") { "default" { children() } }
            matchText { +it }
        }

        // when - a "special"-mode rule for p is added AFTER the wildcard; despite the
        //   later registration the mode-specific matcher wins over the default-mode one,
        //   because the matcher search checks the current-mode tier before falling back
        //   to the mode == null tier (so mode beats registration order)
        val modeSpecific = input().transform {
            match("root") { children(mode = "special") }
            match("*") { "default" { children() } }
            match("p", mode = "special") { "special" { children() } }
            matchText { +it }
        }

        // then - fallback: the default-mode wildcard handles the inner p
        fallback sameAs semanticEvents {
            "default" { +"text" }
        }
        // then - mode-specific rule takes precedence over the earlier wildcard
        modeSpecific sameAs semanticEvents {
            "special" { +"text" }
        }
    }

    @Test
    fun `should pass a subtree through verbatim in a dedicated mode`() = runTest {
        // given
        val events = semanticEvents {
            "nav" {
                "a"("href" to "/") { +"Home" }
            }
            "p" { +"dropped" }
        }

        // when - nav re-emits itself and hands its subtree to "navigation" mode, where a
        //   mode-scoped passthrough copies everything verbatim; content outside nav (in
        //   the default mode) has no rule and is skipped
        val transformed = events.transform {
            match("nav") {
                "nav"("aria-label" to "Main") {
                    children(mode = "navigation")
                }
            }
            passthrough(mode = "navigation")
        }

        // then - the nav subtree survives verbatim; the default-mode p is dropped
        transformed sameAs semanticEvents {
            "nav"("aria-label" to "Main") {
                "a"("href" to "/") { +"Home" }
            }
        }
    }

    @Test
    fun `should defer sibling output emitted after children within the same matcher block`() = runTest {
        // given
        val events = semanticEvents {
            "section" { "p" { +"body" } }
        }

        // when - the matcher re-emits section, descends via children(), then emits more
        //   output after the children() call; that trailing output is deferred until the
        //   subtree has streamed but - unlike afterClose - still lands inside section,
        //   before its unmark
        val transformed = events.transform {
            match("section") {
                "section" {
                    children()
                    "footer" { +"end" }
                }
            }
            match("p") { "p" { children() } }
            matchText { +it }
        }

        // then - footer follows the streamed p subtree yet remains inside the open section
        transformed sameAs semanticEvents {
            "section" {
                "p" { +"body" }
                "footer" { +"end" }
            }
        }
    }

    @Test
    fun `should treat children and afterClose as no-ops inside an afterClose block`() = runTest {
        // given
        val events = semanticEvents {
            "section" { "p" { +"body" } }
        }

        // when - afterClose runs on a sink scope, where children() and a nested
        //   afterClose() are silently ignored, while plain emission still reaches output
        val transformed = events.transform {
            match("section") {
                "section" { children() }
                afterClose {
                    children()                // no-op: sink scope has no subtree to descend
                    afterClose { "never" {} } // no-op: not re-registered
                    "hr" {}
                }
            }
            match("p") { "p" { children() } }
            matchText { +it }
        }

        // then - only the hr from afterClose is emitted; the no-op calls add nothing
        transformed sameAs semanticEvents {
            "section" { "p" { +"body" } }
            "hr" {}
        }
    }

    @Test
    fun `should treat children as a no-op inside a matchText block`() = runTest {
        // given
        val events = semanticEvents {
            "p" { +"hello" }
        }

        // when - matchText also runs on a sink scope; children() inside it is ignored
        //   and the text is still emitted
        val transformed = events.transform {
            match("p") { "p" { children() } }
            matchText {
                children() // no-op
                +it
            }
        }

        // then
        transformed sameAs semanticEvents {
            "p" { +"hello" }
        }
    }

}
