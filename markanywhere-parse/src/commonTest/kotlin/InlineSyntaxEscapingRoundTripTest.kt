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
    fun `should round-trip a del paired after the closer of a del force-closed by an em`() = runTest {
        // given
        val markdown = "*a ~~b* c~~d~~ e"
        // when
        val once = flowOf(markdown).parse().renderMarkdown()
        val twice = flowOf(once).parse().renderMarkdown()
        // then
        once sameAs "*a ~~b~~* c~~d~~ e"
        twice sameAs once
    }

    @Test
    fun `should round-trip an escaped tilde right after a del`() = runTest {
        assertMarkdownFixpoint("""a ~~b~~\~ c""")
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
        assertMarkdownFixpoint("foo==\nbar")
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
        assertMarkdownFixpoint("a < b")
    }

    @Test
    fun `should keep a backslash before a letter unescaped`() = runTest {
        assertMarkdownFixpoint("""C:\Users""")
    }

    @Test
    fun `should round-trip an escaped backslash ending a paragraph`() = runTest {
        assertMarkdownFixpoint("""a\\""")
    }

    @Test
    fun `should round-trip an escaped backslash before an asterisk`() = runTest {
        assertMarkdownFixpoint("""a \\* b""")
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

}
