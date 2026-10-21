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

import com.xemantic.kotlin.test.assert
import com.xemantic.kotlin.test.sameAs
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.render.MARKDOWN_BLOCK_CONTENT_TAGS
import com.xemantic.markanywhere.render.renderMarkdown
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
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
        markdown sameAs """~~\_a\_~~"""
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should escape an underscore that would open emphasis inside a mark span`() = runTest {
        // given
        val events = semanticEvents { "p" { "mark" { +"_a_" } } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs """==\_a\_=="""
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
        // given
        val markdown = $$"""a \$b"""
        // when
        val once = flowOf(markdown).parse().renderMarkdown()
        val twice = flowOf(once).parse().renderMarkdown()
        // then
        once sameAs markdown
        twice sameAs once
    }

    @Test
    fun `should keep a dollar before a digit unescaped`() = runTest {
        // given
        val markdown = $$"costs $5"
        // when
        val once = flowOf(markdown).parse().renderMarkdown()
        val twice = flowOf(once).parse().renderMarkdown()
        // then
        once sameAs markdown
        twice sameAs once
    }

    @Test
    fun `should keep an intraword underscore unescaped`() = runTest {
        // The parser emits every `_` as a text event of its own.
        // given
        val markdown = "snake_case_name"
        // when
        val once = flowOf(markdown).parse().renderMarkdown()
        val twice = flowOf(once).parse().renderMarkdown()
        // then
        once sameAs markdown
        twice sameAs once
    }

    @Test
    fun `should round-trip an escaped underscore opening a word`() = runTest {
        // given
        val markdown = """a \_b_ c"""
        // when
        val once = flowOf(markdown).parse().renderMarkdown()
        val twice = flowOf(once).parse().renderMarkdown()
        // then
        once sameAs markdown
        twice sameAs once
    }

    @Test
    fun `should round-trip an escaped single tilde`() = runTest {
        // given
        val markdown = """a \~b c"""
        // when
        val once = flowOf(markdown).parse().renderMarkdown()
        val twice = flowOf(once).parse().renderMarkdown()
        // then
        once sameAs markdown
        twice sameAs once
    }

    @Test
    fun `should keep a tilde pair ending a line unescaped`() = runTest {
        // At a line end the run has nothing to open: the parser keeps it.
        // given
        val markdown = "foo~~\nbar"
        // when
        val once = flowOf(markdown).parse().renderMarkdown()
        val twice = flowOf(once).parse().renderMarkdown()
        // then
        once sameAs markdown
        twice sameAs once
    }

    // --- #135: force-closed span closers and escaped delimiters ------------

    @Test
    fun `should escape the literal closer of a mark force-closed by an em`() = runTest {
        // given
        val markdown = "*a ==b* c== d ==e=="
        // when
        val once = flowOf(markdown).parse().renderMarkdown()
        val twice = flowOf(once).parse().renderMarkdown()
        // then
        once sameAs """*a ==b==* c\=\= d ==e=="""
        twice sameAs once
    }

    @Test
    fun `should escape the literal closer of a sup force-closed by an em`() = runTest {
        // given
        val markdown = "*a ^b* c^ d ^e^"
        // when
        val once = flowOf(markdown).parse().renderMarkdown()
        val twice = flowOf(once).parse().renderMarkdown()
        // then
        once sameAs """*a ^b^* c\^ d ^e^"""
        twice sameAs once
    }

    @Test
    fun `DIVERGENCE - should escape the closer of a del force-closed by an em before a letter`() = runTest {
        // given — the parser keeps the `~~` after `c` literal (a DIVERGENCE)
        val markdown = "*a ~~b* c~~d~~ e"
        // when
        val once = flowOf(markdown).parse().renderMarkdown()
        val twice = flowOf(once).parse().renderMarkdown()
        // then
        once sameAs """*a ~~b~~* c\~\~d ~~e~~"""
        twice sameAs once
    }

    @Test
    fun `should round-trip an escaped tilde right after a del`() = runTest {
        // given
        val markdown = """a ~~b~~\~ c"""
        // when
        val once = flowOf(markdown).parse().renderMarkdown()
        val twice = flowOf(once).parse().renderMarkdown()
        // then
        once sameAs markdown
        twice sameAs once
    }

    @Test
    fun `should escape a caret in plain text`() = runTest {
        // given
        val events = semanticEvents { "p" { +"x^2" } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs """x\^2"""
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should escape an equals pair in plain text`() = runTest {
        // given
        val events = semanticEvents { "p" { +"a == b = c" } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs """a \=\= b = c"""
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should keep an equals pair ending a line unescaped`() = runTest {
        // given
        val markdown = "foo==\nbar"
        // when
        val once = flowOf(markdown).parse().renderMarkdown()
        val twice = flowOf(once).parse().renderMarkdown()
        // then
        once sameAs markdown
        twice sameAs once
    }

    @Test
    fun `should escape a tilde in image alt text`() = runTest {
        // given — the alt is the plain text of the description: `a ~b`
        val markdown = """![a ~~\~b~~](i.png)"""
        // when
        val once = flowOf(markdown).parse().renderMarkdown()
        val twice = flowOf(once).parse().renderMarkdown()
        // then
        once sameAs """![a \~b](i.png)"""
        twice sameAs once
    }

    @Test
    fun `should escape inline syntax in image alt text`() = runTest {
        // given
        val events = semanticEvents {
            "p" { "img"("src" to "i.png", "alt" to $$"_b_ `c` ^d^ $e [f]") {} }
        }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs $$"""![\_b_ \`c\` \^d\^ \$e \[f\]](i.png)"""
        assertMarkdownFixpoint(markdown)
    }

    // --- #162: an inline-syntax char right after a closer -------------------

    private suspend fun renderAfterCloser(text: String): String =
        semanticEvents { "p" { "strong" { +"a:" }; +text } }.renderMarkdown()

    @Test
    fun `should escape a dollar after a closer`() = runTest {
        // when
        val markdown = renderAfterCloser($$"$b")
        // then
        markdown sameAs $$"""**a:**\$b"""
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should escape a tilde after a closer`() = runTest {
        // when
        val markdown = renderAfterCloser("~b")
        // then
        markdown sameAs """**a:**\~b"""
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should escape an underscore after a closer`() = runTest {
        // when
        val markdown = renderAfterCloser("_b")
        // then
        markdown sameAs """**a:**\_b"""
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should escape a caret after a closer`() = runTest {
        // when
        val markdown = renderAfterCloser("^b")
        // then
        markdown sameAs """**a:**\^b"""
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should escape a backtick after a closer`() = runTest {
        // when
        val markdown = renderAfterCloser("`b")
        // then
        markdown sameAs """**a:**\`b"""
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should escape an asterisk after a closer`() = runTest {
        // when
        val markdown = renderAfterCloser("*b")
        // then
        markdown sameAs """**a:**\*b"""
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should escape a backslash before punctuation after a closer`() = runTest {
        // when
        val markdown = renderAfterCloser("""\*b""")
        // then
        markdown sameAs """**a:**\\\*b"""
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should escape a less-than sign that would start a tag after a closer`() = runTest {
        // when
        val markdown = renderAfterCloser("<b>")
        // then
        markdown sameAs """**a:**\<b>"""
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should escape a less-than sign ending the content of a span`() = runTest {
        // given — unescaped, the `<` starts inline-HTML speculation on
        // re-parse that swallows the closer
        val events = semanticEvents { "p" { "strong" { +"a<" }; +"b" } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs """**a\<**&#98;"""
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should keep a less-than sign before a space unescaped`() = runTest {
        // given
        val markdown = "a < b"
        // when
        val once = flowOf(markdown).parse().renderMarkdown()
        val twice = flowOf(once).parse().renderMarkdown()
        // then
        once sameAs markdown
        twice sameAs once
    }

    @Test
    fun `should keep a backslash before a letter unescaped`() = runTest {
        // given
        val markdown = """C:\Users"""
        // when
        val once = flowOf(markdown).parse().renderMarkdown()
        val twice = flowOf(once).parse().renderMarkdown()
        // then
        once sameAs markdown
        twice sameAs once
    }

    @Test
    fun `should round-trip an escaped backslash ending a paragraph`() = runTest {
        // given
        val markdown = """a\\"""
        // when
        val once = flowOf(markdown).parse().renderMarkdown()
        val twice = flowOf(once).parse().renderMarkdown()
        // then
        once sameAs markdown
        twice sameAs once
    }

    @Test
    fun `should round-trip an escaped backslash before an asterisk`() = runTest {
        // given
        val markdown = """a \\* b"""
        // when
        val once = flowOf(markdown).parse().renderMarkdown()
        val twice = flowOf(once).parse().renderMarkdown()
        // then
        once sameAs markdown
        twice sameAs once
    }

    @Test
    fun `should escape an asterisk that would open emphasis in plain text`() = runTest {
        // given
        val events = semanticEvents { "p" { +"*a* 2 * 3" } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs """\*a* 2 * 3"""
        assertMarkdownFixpoint(markdown)
    }

    // --- #167: whitespace at a span's edge, in labels and table cells -------

    @Test
    fun `should write a no-break space starting a span before its opener`() = runTest {
        // given — after the opener, the no-break space would make it not
        // left-flanking
        val events = semanticEvents { "p" { +"a"; "strong" { +" x" } } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs "a **x**"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should write a space starting a span before its opener`() = runTest {
        // given
        val events = semanticEvents { "p" { +"a"; "em" { +" x" }; +" b" } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs "a *x* b"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should write a no-break space starting a nested span before both openers`() = runTest {
        // given
        val events = semanticEvents { "p" { +"a"; "strong" { "em" { +" x" } } } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs "a ***x***"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should write a no-break space ending a span in a link label after its closer`() = runTest {
        // given
        val events = semanticEvents {
            "p" { "a"("href" to "/u") { "strong" { +"a " }; +"b" } }
        }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs "[**a** b](/u)"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should write a no-break space starting a span in a link label before its opener`() = runTest {
        // given
        val events = semanticEvents {
            "p" { "a"("href" to "/u") { +"a"; "strong" { +" b" } } }
        }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs "[a **b**](/u)"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should write a no-break space ending a span in a table cell after its closer`() = runTest {
        // given
        val events = semanticEvents {
            "table" {
                "tr" { "th" { +"h" } }
                "tr" { "td" { "strong" { +"a " }; +"5" } }
            }
        }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs "| h |\n| --- |\n| **a** 5 |"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should write a line break ending a span after its closer`() = runTest {
        // given
        val events = semanticEvents { "p" { "em" { +"a\n" }; +"b" } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs "*a*\nb"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should escape an asterisk in image alt text`() = runTest {
        // given
        val events = semanticEvents { "p" { "img"("src" to "i.png", "alt" to "*a*") {} } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs """![\*a*](i.png)"""
        assertMarkdownFixpoint(markdown)
    }

    // --- review findings: escapes that must not grow or invent syntax ------

    // Every input below must be a fixpoint of parse → render already.
    private suspend fun roundTrip(markdown: String): Pair<String, String> {
        val once = flowOf(markdown).parse().renderMarkdown()
        return once to flowOf(once).parse().renderMarkdown()
    }

    @Test
    fun `should keep code after a less-than sign before a space`() = runTest {
        // given
        val markdown = "when n < 10, use `x` here"
        // when
        val (once, twice) = roundTrip(markdown)
        // then
        once sameAs markdown
        twice sameAs once
    }

    @Test
    fun `should keep syntax after a less-than sign before a space escaped once`() = runTest {
        // given
        val markdown = $$"""if a < b and \$HOME or x_1 or 2\^3"""
        // when
        val (once, twice) = roundTrip(markdown)
        // then
        once sameAs markdown
        twice sameAs once
    }

    @Test
    fun `should keep emphasis after a less-than sign before a space`() = runTest {
        // given
        val markdown = "a < b *c*"
        // when
        val (once, twice) = roundTrip(markdown)
        // then
        once sameAs markdown
        twice sameAs once
    }

    @Test
    fun `should keep a less-than sign that starts no tag unescaped`() = runTest {
        // given
        val markdown = "<3 and a <= b and a <- b"
        // when
        val (once, twice) = roundTrip(markdown)
        // then
        once sameAs markdown
        twice sameAs once
    }

    @Test
    fun `should keep text in an html block in a list item unescaped`() = runTest {
        // An HTML block in a list item is never sub-parsed: a backslash in it
        // is literal and would grow on every pass.
        // given
        val markdown = "- a\n  <div>\n  x^2 `c` a<b\n  </div>"
        // when
        val (once, twice) = roundTrip(markdown)
        // then
        once sameAs markdown
        twice sameAs once
    }

    @Test
    fun `should drop an emphasis span holding only whitespace`() = runTest {
        // given
        val events = semanticEvents { "p" { +"a"; "em" { +" " }; +"b" } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs "a b"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should escape a dollar before a char written escaped`() = runTest {
        // given — `$\<` would open math
        val events = semanticEvents { "p" { +$$"price $<10 or $^x" } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs $$"""price \$<10 or \$\^x"""
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should round-trip an underscore run split between two merged emphasis spans`() = runTest {
        // given — the second em reopens the first, so its closer is never written
        val events = semanticEvents { "p" { "em" { +"a_" }; "em" { +"_b" } } }
        // when
        val markdown = events.renderMarkdown()
        val reparsed = flowOf(markdown).parse().renderMarkdown()
        // then
        reparsed sameAs markdown
        assert(flowOf(markdown).parse().toList().count { it is SemanticEvent.Mark && it.name == "em" } == 1)
    }

    @Test
    fun `should escape a tilde pair before a hard break`() = runTest {
        // given — a hard break is written as two spaces before the newline
        val events = semanticEvents { "p" { +"a ~~"; "br" { }; +"b ~~" } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs "a \\~\\~  \nb ~~"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should escape a tilde fence starting a deferred paragraph`() = runTest {
        // given — loose text, then a paragraph whose text would open a fence
        val events = semanticEvents { +"A"; "p" { +"~~~" } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs "A\n\n\\~\\~\\~"
        assertMarkdownFixpoint(markdown)
    }

    // --- round 3: less-than signs, HTML blocks in list items, line starts --

    // The first render need not be canonical; every later one must be.
    private suspend fun renderThreePasses(events: Flow<SemanticEvent>): List<String> {
        val once = events.renderMarkdown()
        val twice = flowOf(once).parse().renderMarkdown()
        return listOf(once, twice, flowOf(twice).parse().renderMarkdown())
    }

    @Test
    fun `should escape a less-than sign before an equals pair`() = runTest {
        // given
        val events = semanticEvents { "p" { +"x <== y" } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs """x \<\=\= y"""
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should keep a mark closing after a less-than sign before an equals sign`() = runTest {
        // given
        val events = semanticEvents { "p" { "mark" { +"a <= b" } } }
        // when
        val markdown = events.renderMarkdown()
        val reparsed = flowOf(markdown).parse().toList()
        // then
        assert(reparsed.count { it is SemanticEvent.Mark && it.name == "mark" } == 1)
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should stay stable with less-than signs before chars written escaped`() = runTest {
        for (text in listOf("1 <<n", """a <\* b""", """a <\""", "a <` b", "a <^ b")) {
            // given
            val events = semanticEvents { "p" { +text } }
            // when
            val (_, twice, thrice) = renderThreePasses(events)
            // then
            thrice sameAs twice
        }
    }

    @Test
    fun `should stay stable with a less-than sign before a bracket in image alt text`() = runTest {
        // given
        val events = semanticEvents { "p" { "img"("src" to "i.png", "alt" to "a <[b]") {} } }
        // when
        val (once, twice, thrice) = renderThreePasses(events)
        // then
        twice sameAs once
        thrice sameAs twice
    }

    @Test
    fun `should escape a less-than sign that would start an email autolink`() = runTest {
        // given
        val events = semanticEvents { "p" { +"mail <1u@x.com> or I <3 you" } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs """mail \<1u@x.com> or I <3 you"""
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should escape text in an inline raw tag starting a list item`() = runTest {
        // given — a `<span>` at the line start is no HTML block: Markdown inside
        val events = semanticEvents {
            "ul" { "li" { "p" { tag("span") { +"a *b* c x^2" }; +" d" } } }
        }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs """- <span>a \*b* c x\^2</span> d"""
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should not indent a span starting a paragraph with spaces`() = runTest {
        // given
        val events = semanticEvents { "p" { "em" { +"    a" } } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs "*a*"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should escape a backslash before a space ending a line`() = runTest {
        // given — the space is dropped at the line end, and a backslash right
        // before a line break is a hard break
        val events = semanticEvents { "p" { +"a\\ "; +"\n"; +"b" } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs "a\\\\\nb"
        assertMarkdownFixpoint(markdown)
    }

    // Pins the renderer's `<` escaping (`lessThanStartsInlineHtml`) to the
    // parser's inline-HTML speculation (`canFollowInlineHtmlOpen`,
    // `isEmailAutolinkCandidate`), which live in sibling modules: for every
    // printable ASCII char after a `<` in plain text, the rendering must keep
    // the emphasis after it and be a fixpoint, and must escape the `<` exactly
    // where the parser would speculate on it.
    @Test
    fun `should agree with the parser on every char after a less-than sign`() = runTest {
        // given — chars written escaped themselves get an escaped `<` anyway
        val escapedAnyway = """\<=[]`^~*_$"""
        val failures = mutableListOf<String>()

        // when
        for (c in '!'..'~') {
            if (c == '>') continue
            val markdown = semanticEvents { "p" { +"a <${c}q "; "em" { +"x" } } }.renderMarkdown()
            val reparsed = flowOf(markdown).parse().toList()
            if (reparsed.none { it is SemanticEvent.Mark && it.name == "em" }) failures += "$c: em lost in $markdown"
            if (flowOf(markdown).parse().renderMarkdown() != markdown) failures += "$c: no fixpoint: $markdown"
            if (c !in escapedAnyway) {
                val speculates = flowOf("a <${c}q *x*").parse().toList()
                    .none { it is SemanticEvent.Mark && it.name == "em" }
                val escaped = """\<""" in markdown
                if (speculates != escaped) failures += "$c: speculates=$speculates escaped=$escaped"
            }
        }

        // then
        assert(failures.isEmpty())
    }

    // --- round 4: raw text the parser reads back without escapes ------------

    @Test
    fun `should keep custom markup text verbatim`() = runTest {
        // given — `<ns:name>` content is never parsed as Markdown
        val events = semanticEvents { tag("x:think") { +"use `foo` *a* ^x \$z" } }
        // when
        val (once, twice, thrice) = renderThreePasses(events)
        // then
        once sameAs "<x:think>use `foo` *a* ^x \$z</x:think>"
        twice sameAs once
        thrice sameAs twice
    }

    @Test
    fun `should keep a parsed custom markup block verbatim`() = runTest {
        // given
        val markdown = "<x:think>\nuse `foo` ^x \$z\n</x:think>"
        // when
        val once = flowOf(markdown).parse().renderMarkdown()
        val twice = flowOf(once).parse().renderMarkdown()
        val thrice = flowOf(twice).parse().renderMarkdown()
        // then
        assert("\\" !in once)
        twice sameAs once
        thrice sameAs twice
    }

    @Test
    fun `should keep text on the line of a block html tag verbatim`() = runTest {
        // given — `<div>` starts an HTML block whose first line is raw text
        val events = semanticEvents { tag("div") { +"a `b` c" } }
        // when
        val (once, twice, thrice) = renderThreePasses(events)
        // then
        once sameAs "<div>a `b` c</div>"
        twice sameAs once
        thrice sameAs twice
    }

    @Test
    fun `should escape Markdown after a blank line in a block html tag`() = runTest {
        // given — after a blank line the parser sub-parses the block as Markdown
        val events = semanticEvents { tag("section") { "p" { +"a `b` c" } } }
        // when
        val (once, twice, thrice) = renderThreePasses(events)
        // then
        once sameAs "<section>\n\na \\`b\\` c\n\n</section>"
        twice sameAs once
        thrice sameAs twice
    }

    @Test
    fun `should keep the text of a link spilled to a raw tag in a list item verbatim`() = runTest {
        // given
        val events = semanticEvents {
            "ul" { "li" { "a"("href" to "u") { "p" { +"a*b \$c `d`" } } } }
        }
        // when
        val (once, twice, thrice) = renderThreePasses(events)
        // then
        assert("\\" !in once)
        twice sameAs once
        thrice sameAs twice
    }

    @Test
    fun `should escape a less-than sign before an equals sign starting an email address`() = runTest {
        // given
        val events = semanticEvents { "p" { +"a <=x@y.com> b" } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs """a \<=x@y.com> b"""
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should write one space after the marker of a heading starting with a span`() = runTest {
        // given
        val events = semanticEvents { "h2" { "em" { +" a" } } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs "## *a*"
        assertMarkdownFixpoint(markdown)
    }

    // --- round 5: text that may be read raw or as Markdown ------------------

    private val spanNames = setOf("em", "strong", "code", "sup", "del", "mark", "math", "a")

    private suspend fun Flow<SemanticEvent>.spanCount(): Int =
        toList().count { it is SemanticEvent.Mark && it.name in spanNames }

    // The first render is a fixpoint — or, where the parser reads a raw tag
    // back as literal text ([exact] false), the second one is — and
    // re-parsing it invents no span the events did not have.
    private suspend fun assertStableWithoutInventedSpans(events: Flow<SemanticEvent>, exact: Boolean = true) {
        // when
        val (once, twice, thrice) = renderThreePasses(events)
        // then
        if (exact) twice sameAs once
        thrice sameAs twice
        assert(flowOf(once).parse().spanCount() <= events.spanCount())
        assert(flowOf(twice).parse().spanCount() <= events.spanCount())
    }

    @Test
    fun `should stay stable with Markdown and inline tags in a div closed on a content line`() = runTest {
        // given — an inline-written HTML block: raw text or Markdown, depending
        // on the line its closer lands on
        val cases = listOf(
            semanticEvents { tag("div") { "p" { +"c" }; tag("span") { +"e *f*" } } },
            semanticEvents { tag("div") { "p" { +"c" }; tag("span") { +"e *f*" }; +" g *h*" } },
            semanticEvents { tag("div") { "p" { +"c" }; "em" { +"x" }; +" e *f*" } },
            semanticEvents { tag("div") { "p" { +"c" }; "a"("href" to "u") { +"e *f*" } } },
            semanticEvents { tag("div") { tag("div") { "p" { +"c" }; tag("span") { +"e *f*" } } } },
            semanticEvents { tag("div") { "p" { +"c" }; "img"("src" to "i") {}; +" e *f*" } },
        )
        for (events in cases) {
            // when / then
            assertStableWithoutInventedSpans(events)
        }
    }

    @Test
    fun `should stay stable with a div whose last line ends with its closer`() = runTest {
        for (markdown in listOf("<div>\n\nc\n\n<span>e *f*</span></div>", "<div>\n\nc\n\n[e *f*](u)</div>")) {
            // given
            val events = flowOf(markdown).parse()
            // when / then
            assertStableWithoutInventedSpans(events)
        }
    }

    @Test
    fun `should invent no emphasis in custom markup the parser reads as Markdown`() = runTest {
        // given — single-line custom markup in a list item, after a soft
        // break, and a div in a blockquote, which detects no HTML block: the
        // parser reads the tags back as literal text
        val cases = listOf(
            semanticEvents { "ul" { "li" { tag("x:think") { +"a *b* c" } } } },
            semanticEvents { "p" { +"a\n"; tag("x:ref") { +"b *c*" } } },
            semanticEvents { "blockquote" { tag("div") { +"a *b* `c`" } } },
        )
        for (events in cases) {
            // when / then
            assertStableWithoutInventedSpans(events, exact = false)
        }
    }

    @Test
    fun `should keep image alt text in an html block in a list item verbatim`() = runTest {
        // given
        val events = semanticEvents {
            "ul" { "li" { "p" { +"x" }; tag("div") { "img"("src" to "i", "alt" to "a_b *c*") {} } } }
        }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs "- x\n  <div>![a_b *c*](i)</div>"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should decide a less-than sign before a digit the same however the text is split`() = runTest {
        // given
        val split = semanticEvents { "p" { +"a <1"; +"*b* c" } }
        val whole = semanticEvents { "p" { +"a <1*b* c" } }
        // when
        val splitMarkdown = split.renderMarkdown()
        val wholeMarkdown = whole.renderMarkdown()
        // then
        splitMarkdown sameAs wholeMarkdown
        assertMarkdownFixpoint(wholeMarkdown)
    }

    // Pins the renderer's type-6 tag set (`HTML_BLOCK_START_TAGS`) to the
    // parser's (`HTML_BLOCK_TYPE6_TAGS`), which live in sibling modules: a
    // type-6 tag interrupts a paragraph on re-parse, so the renderer writes the
    // rest of its line verbatim; any other tag after a soft break is inline
    // Markdown, so the renderer escapes it.
    @Test
    fun `should agree with the parser on every html block start tag`() = runTest {
        // given
        val failures = mutableListOf<String>()

        // when
        for (name in ELEMENT_NAMES) {
            if (name in MARKDOWN_BLOCK_CONTENT_TAGS || name in VOID_NAMES || name in OTHER_RAW_NAMES) continue
            val parsed = flowOf("x\n<$name>\ny").parse().toList()
            val paragraphEnd = parsed.indexOfFirst { it is SemanticEvent.Unmark && it.name == "p" }
            val tagStart = parsed.indexOfFirst { it is SemanticEvent.Mark && it.name == name }
            val parserType6 = tagStart >= 0 && paragraphEnd in 0 until tagStart
            val markdown = semanticEvents { "p" { +"x\n"; tag(name) { +"a *b*" } } }.renderMarkdown()
            val rendererType6 = "a *b*" in markdown
            if (parserType6 != rendererType6) failures += "$name: parser=$parserType6 renderer=$rendererType6"
        }

        // then
        assert(failures.isEmpty())
    }

    // --- round 6: raw text for certain stays exact --------------------------

    @Test
    fun `should keep the lines of an html block before its first blank line exact`() = runTest {
        for (markdown in listOf(
            "<div align=\"center\">\n**Bold** and *em* text\n</div>",
            "<div>\nSome *text* with a [link](u) here\n</div>",
            "<center>\n*x* & y\n</center>",
            "<div>\n- a\n- c\n</div>",
            "<div>\n1. a\n2. b\n</div>",
        )) {
            // given — the parser reads them as the block's raw text
            // when
            val once = flowOf(markdown).parse().renderMarkdown()
            // then
            once sameAs markdown
        }
    }

    @Test
    fun `should keep raw text with block markers in an html block exact`() = runTest {
        // given
        val events = semanticEvents { tag("div") { +"a\n# b *c*\n- d" } }
        // when
        val (once, twice, thrice) = renderThreePasses(events)
        // then
        assert("\\" !in once && "&#" !in once)
        twice sameAs once
        thrice sameAs twice
    }

    @Test
    fun `should keep custom markup opening a blockquote verbatim`() = runTest {
        // given
        val markdown = "> <x:think>a *b* `c`</x:think>"
        // when
        val once = flowOf(markdown).parse().renderMarkdown()
        // then
        once sameAs markdown
    }

    @Test
    fun `should keep a raw tag that is the first content of a paragraph verbatim`() = runTest {
        for (name in listOf("x:note", "h3", "div")) {
            // given — the tag opens the paragraph's line, so it starts an HTML block
            val events = semanticEvents { "p" { tag(name) { +"x*y* `c`" } } }
            // when
            val markdown = events.renderMarkdown()
            // then
            markdown sameAs "<$name>x*y* `c`</$name>"
            assertMarkdownFixpoint(markdown)
        }
    }

    @Test
    fun `should keep a block marker in raw text after a block starts verbatim`() = runTest {
        // given — the marker opens a paragraph's first line inside an HTML
        // block; in a list item the block stays raw, so the blank line before
        // the paragraph is dropped once
        val cases = listOf(
            semanticEvents { "ul" { "li" { tag("div") { "p" { +"# x" } } } } } to false,
            semanticEvents { tag("div") { "p" { +"1. x" } } } to true,
        )
        for ((events, exact) in cases) {
            // when
            val (once, twice, thrice) = renderThreePasses(events)
            // then
            assert("\\" !in once && "\\" !in thrice)
            if (exact) twice sameAs once
            thrice sameAs twice
        }
    }

    @Test
    fun `should not escape a block marker in custom markup on any pass`() = runTest {
        // given — custom markup content is raw: its layout settles over passes
        // (as on main), but a backslash written into it would stay and grow
        val events = semanticEvents { tag("x:think") { "p" { +"- a" } } }
        // when
        val passes = renderThreePasses(events)
        // then
        assert(passes.none { "\\" in it })
    }

    @Test
    fun `should round-trip a del or mark around emphasis starting with code or punctuation`() = runTest {
        val cases = listOf(
            semanticEvents { "p" { "del" { "em" { "code" { +"x" } } } } },
            semanticEvents { "p" { "del" { "strong" { "code" { +"x" } } } } },
            semanticEvents { "p" { "mark" { "em" { "code" { +"x" } } } } },
            semanticEvents { "p" { "mark" { "strong" { "sup" { +"a" } } } } },
            semanticEvents { "p" { "del" { "em" { +"_a" } } } },
            semanticEvents { "p" { "del" { "em" { +"*a" } } } },
            semanticEvents { "p" { "del" { "em" { +"\$x\$" } } } },
        )
        for (events in cases) {
            // when
            val markdown = events.renderMarkdown()
            // then
            flowOf(markdown).parse().mergeAdjacentText() sameAs events
            assertMarkdownFixpoint(markdown)
        }
    }

    @Test
    fun `should end the raw text of an html block at a blank line spanning two text events`() = runTest {
        // given — the first text ends its line, the second opens with a line
        // break: a blank line, after which the parser reads Markdown
        val events = semanticEvents { tag("div") { +"a\n"; +"\n*b*" } }
        // when
        val (once, twice, thrice) = renderThreePasses(events)
        // then
        assert(flowOf(once).parse().spanCount() == 0)
        twice sameAs once
        thrice sameAs twice
    }

    @Test
    fun `should render a tilde pair before an asterisk opening nothing as text`() = runTest {
        for (markdown in listOf("approx ~~*~~ y", "a ~~*~~ b *c*", "~~*~~")) {
            // given
            val events = flowOf(markdown).parse()
            // when
            val (once, twice, thrice) = renderThreePasses(events)
            // then — no source char is lost
            assert(once.count { it == '~' } >= markdown.count { it == '~' })
            assert(once.count { it == '*' } >= markdown.count { it == '*' })
            twice sameAs once
            thrice sameAs twice
        }
    }

    @Test
    fun `should keep custom markup that is the first content of a paragraph in a blockquote verbatim`() = runTest {
        // given
        val events = semanticEvents { "blockquote" { "p" { tag("x:note") { +"x*y* `c`" } } } }
        // when
        val markdown = events.renderMarkdown()
        // then
        markdown sameAs "> <x:note>x*y* `c`</x:note>"
        assertMarkdownFixpoint(markdown)
    }

    // --- #130: an outer span closed inside brackets that are not a link ---

    @Test
    fun `should reach a fixpoint for an outer span closed inside brackets that are not a link`() = runTest {
        for (markdown in listOf(
            "**foo [bar*** baz]", "*foo [bar*** baz]", "*a [b***]", "**a [b***]",
            "*foo [bar*]", "*foo [bar*\\* baz]", "*a ~~[b*]~~", "*a ==[b*]==", "*a ^[b*]^",
            "*a <b>[b*]</b>", "**a ~~[b*** c]~~", "*a ~~[b* c]~~"
        )) {
            // when
            val (once, twice) = roundTrip(markdown)

            // then
            twice sameAs once
        }
    }

}

// Written verbatim by the renderer, or dropped / kept literal by the parser
// (GFM §6.11 disallowed raw HTML): no HTML block start question.
private val OTHER_RAW_NAMES = setOf(
    "code", "pre", "textarea", "script", "style", "title", "xmp", "iframe", "noembed", "noframes", "plaintext"
)

private val VOID_NAMES = setOf(
    "area", "base", "br", "col", "embed", "hr", "img", "input", "keygen", "link", "meta", "param", "source", "track", "wbr"
)

// Every HTML element name, current and obsolete, that a block tag set may hold.
private val ELEMENT_NAMES = listOf(
    "a", "abbr", "address", "area", "article", "aside", "audio", "b", "base", "basefont", "bdi", "bdo",
    "blockquote", "body", "br", "button", "canvas", "caption", "center", "cite", "code", "col", "colgroup",
    "data", "datalist", "dd", "del", "details", "dfn", "dialog", "dir", "div", "dl", "dt", "em", "embed",
    "fieldset", "figcaption", "figure", "font", "footer", "form", "frame", "frameset", "h1", "h2", "h3", "h4",
    "h5", "h6", "head", "header", "hgroup", "hr", "html", "i", "iframe", "img", "input", "ins", "kbd", "label",
    "legend", "li", "link", "main", "map", "mark", "menu", "menuitem", "meta", "meter", "nav", "noframes",
    "noscript", "object", "ol", "optgroup", "option", "output", "p", "param", "picture", "pre", "progress", "q",
    "rp", "rt", "ruby", "s", "samp", "script", "search", "section", "select", "slot", "small", "source", "span",
    "strong", "style", "sub", "summary", "sup", "table", "tbody", "td", "template", "textarea", "tfoot", "th",
    "thead", "time", "title", "tr", "track", "u", "ul", "var", "video", "wbr"
)
