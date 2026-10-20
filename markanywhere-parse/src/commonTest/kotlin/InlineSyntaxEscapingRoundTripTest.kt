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

package com.xemantic.markanywhere.parse

import com.xemantic.kotlin.test.sameAs
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.render.renderMarkdown
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * A literal char of inline syntax in text — a tilde, an underscore, a dollar
 * sign, a backtick… — must be rendered so that re-parsing the Markdown reads
 * it as the same literal char, not as the start of a span (issues #133, #135,
 * #162, #167). Each test checks the rendered shape and that it is a fixpoint
 * of render → parse → render.
 */
class InlineSyntaxEscapingRoundTripTest {

    // A Markdown string that is already canonical must be a stable fixpoint: a
    // parse + re-render reproduces it, and a second pass changes nothing.
    private suspend fun assertMarkdownFixpoint(
        markdown: String
    ) {
        // when
        val once = flowOf(markdown).parse().renderMarkdown()
        val twice = flowOf(once).parse().renderMarkdown()

        // then
        once sameAs markdown
        twice sameAs once
    }

    // --- #133: del / mark content and a bare tilde pair ---------------------

    @Test
    fun `should escape a literal tilde pair outside any span`() = runTest {
        // given
        val events = semanticEvents { "p" { +"a ~~b~~ c" } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs """a \~\~b\~\~ c"""
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should escape the literal closer of a del force-closed by an em`() = runTest {
        // given — the parser reads the `~~` after `c` as the orphaned closer
        // of the del the em force-closed: literal text
        val markdown = "*a ~~b* c~~ d"
        // when
        val once = flowOf(markdown).parse().renderMarkdown()
        val twice = flowOf(once).parse().renderMarkdown()
        // then
        once sameAs """*a ~~b~~* c\~\~ d"""
        twice sameAs once
    }

    @Test
    fun `should escape a dollar before a letter inside a del span`() = runTest {
        // given
        val events = semanticEvents { "p" { "del" { +$$"a$b" } } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs $$"""~~a\$b~~"""
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should escape a dollar before a letter inside a mark span`() = runTest {
        // given
        val events = semanticEvents { "p" { "mark" { +$$"a$b" } } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs $$"""==a\$b=="""
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should escape an underscore that would open emphasis inside a del span`() = runTest {
        // given
        val events = semanticEvents { "p" { "del" { +"_a_" } } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs """~~\_a_~~"""
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should escape an underscore that would open emphasis inside a mark span`() = runTest {
        // given
        val events = semanticEvents { "p" { "mark" { +"_a_" } } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs """==\_a_=="""
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should escape a backtick inside a mark span`() = runTest {
        // given
        val events = semanticEvents { "p" { "mark" { +"a`b" } } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs """==a\`b=="""
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should round-trip an escaped dollar before a letter`() = runTest {
        // The parser emits the escaped `$` and the `b` as two text events, so
        // the dollar is judged by the first char of the next event.
        assertMarkdownFixpoint($$"""a \$b""")
    }

    @Test
    fun `should keep a dollar before a digit unescaped`() = runTest {
        assertMarkdownFixpoint($$"costs $5")
    }

    @Test
    fun `should keep an intraword underscore unescaped`() = runTest {
        // The parser emits every `_` as a text event of its own.
        assertMarkdownFixpoint("snake_case_name")
    }

    @Test
    fun `should round-trip an escaped underscore opening a word`() = runTest {
        assertMarkdownFixpoint("""a \_b_ c""")
    }

    @Test
    fun `should round-trip an escaped single tilde`() = runTest {
        assertMarkdownFixpoint("""a \~b c""")
    }

    @Test
    fun `should keep a tilde pair ending a line unescaped`() = runTest {
        // At a line end the run has nothing to open: the parser keeps it.
        assertMarkdownFixpoint("foo~~\nbar")
    }

}
