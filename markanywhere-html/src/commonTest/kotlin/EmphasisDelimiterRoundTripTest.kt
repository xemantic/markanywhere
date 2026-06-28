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
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.parse.parse
import com.xemantic.markanywhere.render.renderMarkdown
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Round-trip fixpoint guards for emphasis whose closing delimiter sits at the
 * very end of a link label, and for emphasis whose content carries a literal
 * delimiter. Both used to make the delimiter run *grow by one every round-trip*
 * — the unbounded `…)*` → `…)**` → `…)***` instability surfaced by the Brave
 * SERP dump, whose unsupported image-in-link puts a `*` from the image URL next
 * to a trailing `*` inside the link label.
 *
 * Two independent fixes are exercised:
 *  - parser: [com.xemantic.markanywhere.parse] closes the matching label-local
 *    emphasis frame with a closing delimiter run at the label's end instead of
 *    flushing it as literal content (see `closeLabelLocalEmphasisRun`);
 *  - renderer: [com.xemantic.markanywhere.render] backslash-escapes a literal
 *    delimiter inside an emphasis span so it does not re-pair with the span's
 *    own closing run.
 */
class EmphasisDelimiterRoundTripTest {

    // A Markdown string that is already canonical must be a stable fixpoint: a
    // parse + re-render reproduces it, and a second pass changes nothing.
    private suspend fun assertMarkdownFixpoint(markdown: String) {
        // when
        val once = flowOf(markdown).parse().renderMarkdown()
        val twice = flowOf(once).parse().renderMarkdown()

        // then
        once sameAs markdown
        twice sameAs once
    }

    @Test
    fun `should round-trip strong spanning a whole link label`() = runTest {
        assertMarkdownFixpoint("[**bold**](u)")
    }

    @Test
    fun `should round-trip emphasis spanning a whole link label`() = runTest {
        assertMarkdownFixpoint("[*em*](u)")
    }

    @Test
    fun `should round-trip an image-in-link label with a trailing asterisk`() = runTest {
        // The minimised Brave shape: a link label holding image-as-text whose URL
        // carries a `*`, plus a trailing `*`. The opening `*` (in the URL) and the
        // trailing `*` form a label-local emphasis that must close on the trailing
        // delimiter rather than swallow it as content (which grew the run forever).
        assertMarkdownFixpoint("[![alt](http://x/v3/*app/a.svg)*](http://r/)")
    }

    @Test
    fun `should round-trip emphasis whose content contains a literal asterisk`() = runTest {
        // An HTML-sourced `<em>a*b</em>` — the parser never produces this shape on
        // its own, but the DOM pipeline does. The renderer must escape the inner
        // `*` so the re-emitted `*a*b*` does not re-pair into `<em>a</em>b*`.
        val markdown = semanticEvents { "p" { "em" { +"a*b" } } }.renderMarkdown()
        markdown sameAs "*a\\*b*"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should round-trip strong whose content contains a literal asterisk`() = runTest {
        val markdown = semanticEvents { "p" { "strong" { +"a*b" } } }.renderMarkdown()
        markdown sameAs "**a\\*b**"
        assertMarkdownFixpoint(markdown)
    }
}
