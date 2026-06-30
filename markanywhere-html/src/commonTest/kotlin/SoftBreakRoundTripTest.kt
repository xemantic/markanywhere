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

import com.xemantic.kotlin.test.sameAs
import com.xemantic.markanywhere.flow.SemanticEventScope
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.parse.parse
import com.xemantic.markanywhere.render.renderMarkdown
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Round-trip fixpoint guards for soft line breaks. A single `\n` (soft break)
 * within a paragraph parses to a standalone whitespace `text` event, which the
 * reverse renderer previously dropped — so `render → parse → render` collapsed
 * `a\nb` to `ab` and was not a fixpoint. These assert the rendered Markdown is
 * stable under a re-parse + re-render for every shape that carries a soft break.
 */
class SoftBreakRoundTripTest {

    private suspend fun assertFixpoint(
        expected: String,
        block: suspend SemanticEventScope.() -> Unit
    ) {
        // when — render the events, then re-parse and re-render the output
        val markdown = semanticEvents(block = block).renderMarkdown()
        val roundtripped = flowOf(markdown).parse().renderMarkdown()

        // then — the first render matches the expected Markdown, and re-parsing
        // and re-rendering reproduces it exactly (a stable fixpoint)
        markdown sameAs expected
        roundtripped sameAs markdown
    }

    @Test
    fun `should round-trip a paragraph with a soft break`() = runTest {
        assertFixpoint("a\nb") {
            "p" { +"a\nb" }
        }
    }

    @Test
    fun `should round-trip an image followed by a paragraph`() = runTest {
        assertFixpoint("![](x.gif)\nAfter") {
            "img"("src" to "x.gif") {}
            "p" { +"After" }
        }
    }

    @Test
    fun `should round-trip loose text followed by a paragraph`() = runTest {
        assertFixpoint("Loose\nAfter") {
            +"Loose"
            "p" { +"After" }
        }
    }

    @Test
    fun `should round-trip an image hard break then a paragraph`() = runTest {
        // The reviewer's original repro shape: a moot `<br>` before a following
        // block. The break is dropped (block boundary), and the soft break the
        // re-parse introduces between the image and the text is preserved.
        assertFixpoint("![](x.gif)\nAfter") {
            "img"("src" to "x.gif") {}
            "br" {}
            "p" { +"After" }
        }
    }

    @Test
    fun `should round-trip inline HTML spanning a soft break`() = runTest {
        // A raw inline tag is an *unambiguous* open (unlike a speculative `*`), so
        // its mark can stay open across a soft break and close at `</label>` — a
        // true fixpoint while staying append-only. (Markdown emphasis still
        // force-closes at the break; see the parser divergence docs.)
        assertFixpoint("<label>a\nb</label>") {
            "p" { tag("label") { +"a\nb" } }
        }
    }

    @Test
    fun `should round-trip inline HTML with a leading space after a soft break`() = runTest {
        // The continuation line's content begins with a space that lives *inside*
        // the open tag — it must survive (not be stripped as paragraph indent).
        assertFixpoint("<label>a\n b</label>") {
            "p" { tag("label") { +"a\n b" } }
        }
    }

    @Test
    fun `should round-trip a chain of inline tags each spanning a soft break`() = runTest {
        // The W3C-form shape: `</label>` closes one tag and `<label>` opens the
        // next on the same line, each spanning to the next line.
        assertFixpoint("<label>a\nb</label><label>c\nd</label>") {
            "p" {
                tag("label") { +"a\nb" }
                tag("label") { +"c\nd" }
            }
        }
    }
}
