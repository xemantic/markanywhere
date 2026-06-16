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

import com.xemantic.markanywhere.dump.AccessibilityAnnotations
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

// Generic boundary/preservation behaviour is covered by
// DropStructuralWhitespaceTest in markanywhere-flow. These tests cover only
// the HTML-specific policy: delegation plus the `isTagged` preserve precision.
class HtmlWhitespaceNormalizationTest {

    @Test
    fun `should drop whitespace-only text events between sibling marks`() = runTest {
        // given
        // The HTML source `<p>x</p>\n  <p>y</p>` produces a whitespace-only
        // text event between the two paragraphs.
        val input = semanticEvents(tagged = true) {
            "p" { +"x" }
            +"\n  "
            "p" { +"y" }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p" { +"x" }
            "p" { +"y" }
        }
    }

    @Test
    fun `should preserve whitespace inside tagged pre`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "pre" {
                +"\n    indented code\n"
            }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then
        output sameAs semanticEvents(tagged = true) {
            "pre" {
                +"\n    indented code\n"
            }
        }
    }

    @Test
    fun `should preserve whitespace inside tagged code`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "p" {
                +"Run "
                "code" { +"  spaced  " }
                +" first"
            }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p" {
                +"Run "
                "code" { +"  spaced  " }
                +" first"
            }
        }
    }

    @Test
    fun `should keep a single space between adjacent inline elements`() = runTest {
        // given — the source `<a>…</a> <a>…</a>` produces a whitespace-only
        // text event between two links; the separating space is significant.
        val input = semanticEvents(tagged = true) {
            "a"("href" to "/a") { +"A" }
            +" "
            "a"("href" to "/b") { +"B" }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then — the space survives, so the links don't run together
        output sameAs semanticEvents(tagged = true) {
            "a"("href" to "/a") { +"A" }
            +" "
            "a"("href" to "/b") { +"B" }
        }
    }

    @Test
    fun `should collapse interior whitespace and trim block edges of text`() = runTest {
        // given — a content text node with a leading newline+indent, a tab run
        // inside, and a trailing space, sitting directly inside a block.
        val input = semanticEvents(tagged = true) {
            "p" { +"\n    Fundstelle\t\topenJur " }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then — interior run collapses to one space, block edges are trimmed
        output sameAs semanticEvents(tagged = true) {
            "p" { +"Fundstelle openJur" }
        }
    }

    @Test
    fun `should drop whitespace flanking a block element`() = runTest {
        // given — indentation around a heading between two unwrapped contexts
        val input = semanticEvents(tagged = true) {
            +"x"
            +"\n\t\t\t"
            "h6" { +"Tenor" }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then — the whitespace before the block heading is dropped
        output sameAs semanticEvents(tagged = true) {
            +"x"
            "h6" { +"Tenor" }
        }
    }

    @Test
    fun `should preserve newlines inside the synthetic frontmatter block`() = runTest {
        // given — simplifyHtml emits the untagged `frontmatter` block as one
        // text node of newline-separated YAML; collapsing it would break it.
        val input = semanticEvents {
            "frontmatter"("format" to "yaml") {
                +"lang: de\ntitle: Doc\n"
            }
            "p" { +"x" }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then — the YAML newlines survive verbatim
        output sameAs semanticEvents {
            "frontmatter"("format" to "yaml") {
                +"lang: de\ntitle: Doc\n"
            }
            "p" { +"x" }
        }
    }

    @Test
    fun `should preserve non-breaking spaces as content`() = runTest {
        // given — NBSP (U+00A0) is content, not structural whitespace: HTML
        // never collapses it (e.g. legal citations keep "\u00A0823" together).
        val input = semanticEvents(tagged = true) {
            "p" { +"\n  Gem\u00E4\u00DF \u00A7\u00A0823 Abs.\u00A01 ZPO " }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then — ASCII indentation collapses/trims, NBSPs survive verbatim
        output sameAs semanticEvents(tagged = true) {
            "p" { +"Gem\u00E4\u00DF \u00A7\u00A0823 Abs.\u00A01 ZPO" }
        }
    }

    @Test
    fun `should not preserve whitespace inside a non-tagged code mark`() = runTest {
        // given
        // A Markdown-native inline `code` mark (isTagged = false) is not an
        // HTML <code> element, so its boundary whitespace is structural noise.
        val input = semanticEvents {
            "p" {
                "code" { +"\n  " }
            }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then
        output sameAs semanticEvents {
            "p" {
                "code" { }
            }
        }
    }

    @Test
    fun `should keep space between block-named elements that compute to inline`() = runTest {
        // given — `<div>` is block by tag name, but its computed display is
        // inline here; the separating space must be kept (and the annotation
        // stripped from the output).
        val input = semanticEvents(tagged = true) {
            "div"(AccessibilityAnnotations.DISPLAY to "inline") { +"A" }
            +" "
            "div"(AccessibilityAnnotations.DISPLAY to "inline") { +"B" }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then — display overrides the tag-name heuristic, annotation dropped
        output sameAs semanticEvents(tagged = true) {
            "div" { +"A" }
            +" "
            "div" { +"B" }
        }
    }

    @Test
    fun `should drop space around an inline-named element that computes to block`() = runTest {
        // given — `<span>` is inline by tag name, but its computed display is
        // block here, so the whitespace touching it is structural and dropped.
        val input = semanticEvents(tagged = true) {
            "a"("href" to "/x") { +"A" }
            +" "
            "span"(AccessibilityAnnotations.DISPLAY to "block") { +"B" }
        }

        // when
        val output = input.dropHtmlStructuralWhitespace()

        // then
        output sameAs semanticEvents(tagged = true) {
            "a"("href" to "/x") { +"A" }
            "span" { +"B" }
        }
    }
}