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
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.render.renderMarkdown
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Plain text outside any span that contains inline Markdown syntax must be
 * rendered so that it re-parses to the same text (issue #211), escaping only
 * the chars that could open a construct at their position.
 */
class PlainTextEscapingRoundTripTest {

    // Renders [events], checks the Markdown and that parsing it gives the
    // same events back.
    private suspend fun assertRoundTrip(
        events: Flow<SemanticEvent>,
        expected: String
    ) {
        // when
        val markdown = events.renderMarkdown()

        // then
        markdown sameAs expected
        flowOf(markdown).parse().mergeAdjacentText() sameAs events.mergeAdjacentText()
        flowOf(markdown).parse().renderMarkdown() sameAs markdown
    }

    private suspend fun assertParagraphRoundTrip(text: String, expected: String) {
        assertRoundTrip(semanticEvents { "p" { +text } }, expected)
    }

    @Test
    fun `should escape a literal em run`() = runTest {
        assertParagraphRoundTrip("a *b* c", """a \*b* c""")
    }

    @Test
    fun `should escape a literal strong run`() = runTest {
        assertParagraphRoundTrip("a **b** c", """a \*\*b** c""")
    }

    @Test
    fun `should escape a literal underscore em run`() = runTest {
        assertParagraphRoundTrip("a _b_ c", """a \_b_ c""")
    }

    @Test
    fun `should escape a literal code span`() = runTest {
        assertParagraphRoundTrip("a `b` c", """a \`b\` c""")
    }

    @Test
    fun `should escape a literal inline link`() = runTest {
        assertParagraphRoundTrip("a [b](c) d", """a \[b](c) d""")
    }

    @Test
    fun `should escape a literal single tilde del`() = runTest {
        assertParagraphRoundTrip("a ~b~ c", """a \~b\~ c""")
    }

    @Test
    fun `should escape a literal mark`() = runTest {
        assertParagraphRoundTrip("a ==b== c", """a \=\=b\=\= c""")
    }

    @Test
    fun `should escape literal raw html`() = runTest {
        assertParagraphRoundTrip("a <b>x</b> c", """a \<b>x\</b> c""")
    }

    @Test
    fun `should escape a literal backslash escape`() = runTest {
        assertParagraphRoundTrip("""a \*b c""", """a \\\*b c""")
    }

    @Test
    fun `should escape a literal character reference`() = runTest {
        assertParagraphRoundTrip("a &amp; c", """a \&amp; c""")
    }

    @Test
    fun `should keep intraword underscores unescaped`() = runTest {
        assertParagraphRoundTrip("ao1977_180abs2v", "ao1977_180abs2v")
        assertParagraphRoundTrip("snake_case", "snake_case")
    }

    @Test
    fun `should round-trip law titles with inline syntax in a list`() = runTest {
        // given
        val titles = listOf(
            "Verordnung über *Sterne*",
            "Gesetz zu [Artikel 1](Satz 2)",
            "Abkommen ao1977_180abs2v",
            "Gesetz über `Code` und ~Tilden~"
        )
        val events = semanticEvents {
            "ul" { for (title in titles) "li" { "p" { +title } } }
        }

        // then
        assertRoundTrip(
            events,
            """
            - Verordnung über \*Sterne*
            - Gesetz zu \[Artikel 1](Satz 2)
            - Abkommen ao1977_180abs2v
            - Gesetz über \`Code\` und \~Tilden~
            """.trimIndent()
        )
    }

    // --- literal brackets (#212 item 3) --------------------------------------

    @Test
    fun `should escape every opening bracket but no closing one`() = runTest {
        assertParagraphRoundTrip("see [1] and [a [b] c] here", """see \[1] and \[a \[b] c] here""")
    }

    @Test
    fun `should escape an unmatched opening bracket`() = runTest {
        assertParagraphRoundTrip("a [b c", """a \[b c""")
    }

    @Test
    fun `should escape the opening brackets of a literal reference link`() = runTest {
        assertParagraphRoundTrip("a [b][c] d", """a \[b]\[c] d""")
    }

    @Test
    fun `should escape the opening bracket of a literal link reference definition`() = runTest {
        assertParagraphRoundTrip("[a]: /u", """\[a]: /u""")
    }

    @Test
    fun `should escape the opening bracket of a literal image`() = runTest {
        assertParagraphRoundTrip("New![docs](u)", """New!\[docs](u)""")
    }

    @Test
    fun `should escape an exclamation mark before a link`() = runTest {
        assertRoundTrip(
            semanticEvents { "p" { +"New!"; "a"("href" to "u") { +"docs" } } },
            """New\![docs](u)"""
        )
    }

    @Test
    fun `should escape a bracket closed by a later text event`() = runTest {
        assertRoundTrip(
            semanticEvents { "p" { +"a [b"; "em" { +"c" }; +"] d" } },
            """a \[b*c*] d"""
        )
    }

    @Test
    fun `should escape a bracket before a link on the same line`() = runTest {
        assertRoundTrip(
            semanticEvents { "p" { +"a [b "; "a"("href" to "u") { +"x" }; +" c" } },
            """a \[b [x](u) c"""
        )
    }

    @Test
    fun `should escape a bracket before a span closer`() = runTest {
        assertRoundTrip(
            semanticEvents { "p" { "strong" { +"a [b" }; +" c" } },
            """**a \[b** c"""
        )
    }

    @Test
    fun `should escape a bracket pair followed by a link`() = runTest {
        assertRoundTrip(
            semanticEvents { "p" { +"[a]"; "a"("href" to "u") { +"x" } } },
            """\[a][x](u)"""
        )
    }

    @Test
    fun `should escape brackets in a link label`() = runTest {
        assertRoundTrip(
            semanticEvents { "p" { "a"("href" to "u") { +"x] [y" } } },
            """[x\] \[y](u)"""
        )
    }

    @Test
    fun `should keep balanced brackets in a link label unescaped`() = runTest {
        // The parser counts bracket depth in a label, and reads an image in it
        // as label text — the shape a captured image link re-parses to.
        assertRoundTrip(
            semanticEvents { "p" { "a"("href" to "u") { +"![alt](s)" } } },
            """[![alt](s)](u)"""
        )
    }

    @Test
    fun `should escape label brackets around a span`() = runTest {
        assertRoundTrip(
            semanticEvents { "p" { "a"("href" to "u") { +"a [b"; "em" { +"c" }; +"] d" } } },
            """[a \[b*c*\] d](u)"""
        )
    }

    @Test
    fun `should escape an unbalanced bracket split across a link label`() = runTest {
        assertRoundTrip(
            semanticEvents { "p" { "a"("href" to "u") { +"a ["; "em" { +"b" }; +" c" } } },
            """[a \[*b* c](u)"""
        )
    }

    // --- a tag alone on its line opens a type-7 HTML block --------------------

    @Test
    fun `should keep the text of a tag alone on its line at a block start as is`() = runTest {
        // The parser reads it raw up to a blank line — escaping it grew a
        // backslash on every pass.
        for (markdown in listOf("<b>\na*b \\- `c`\n</b>", "- <b>\n  a*b \\- `c`\n  </b>")) {
            // when
            val once = flowOf(markdown).parse().renderMarkdown()

            // then
            once sameAs markdown
            flowOf(once).parse().renderMarkdown() sameAs once
        }
    }

    @Test
    fun `should escape the text after a tag with content on its line`() = runTest {
        assertRoundTrip(
            semanticEvents { "p" { tag("b") { +"a*b" }; +" c" } },
            """<b>a\*b</b> c"""
        )
    }

    // --- an underscore closing an open span (#212 item 5) ---------------------

    @Test
    fun `should escape an underscore that would close an open em`() = runTest {
        assertRoundTrip(
            semanticEvents { "p" { "em" { +"a_ b" } }; "p" { "em" { +"file_.txt" } }; "p" { "strong" { +"a_" } } },
            """
            *a\_ b*

            *file\_.txt*

            **a\_**
            """.trimIndent()
        )
    }

    @Test
    fun `should keep an intraword underscore in an open em unescaped`() = runTest {
        assertRoundTrip(
            semanticEvents { "p" { "em" { +"snake_case" } } },
            """*snake_case*"""
        )
    }

    // --- a backslash before punctuation (#212 item 1) -------------------------

    @Test
    fun `should keep a backslash before any ASCII punctuation`() = runTest {
        assertParagraphRoundTrip("""regex \d+\.\(x\)""", """regex \d+\\.\\(x\\)""")
        assertParagraphRoundTrip("""C:\-tmp""", """C:\\-tmp""")
    }

    @Test
    fun `should escape a backslash starting an emphasis span`() = runTest {
        // A `*` before a `\` opens only before a real escape.
        assertRoundTrip(
            semanticEvents { "p" { +"a "; "em" { +"\\y" }; +" b" } },
            """a *\\y* b"""
        )
    }

    @Test
    fun `should escape a dollar before an escaped bracket in a link label`() = runTest {
        // `$\` opens math.
        assertRoundTrip(
            semanticEvents { "p" { "a"("href" to "u") { +$$"a $] b" } } },
            $$"""[a \$\] b](u)"""
        )
    }

    // --- a leading block marker split off by an escape ------------------------

    @Test
    fun `should not escape a leading marker char the parser splits off its line`() = runTest {
        // The parser emits `-` and `#!` as text events of their own, ended by
        // the escape after them: the marker is judged with the char after it.
        for (markdown in listOf($$"""-\$a""", """#!\[x""", """+\`a\`""")) {
            // when
            val once = flowOf(markdown).parse().renderMarkdown()

            // then
            once sameAs markdown
        }
    }

    @Test
    fun `should escape a hash starting a paragraph continuation line`() = runTest {
        // The parser lets any `#` line interrupt a paragraph.
        assertRoundTrip(
            semanticEvents { "p" { +"y\n#"; "a"("href" to "u") { +"l" } } },
            "y\n\\#[l](u)"
        )
    }

    @Test
    fun `should keep a less-than before a digit the next event ends as is`() = runTest {
        // The parser emits `<=a` as a text of its own, ended by the `;`.
        for (markdown in listOf("x<=a;\\[y", "<-(]")) {
            // when
            val once = flowOf(markdown).parse().renderMarkdown()

            // then
            once sameAs markdown
        }
    }

    @Test
    fun `should escape list item text forming a thematic break with its marker`() = runTest {
        assertRoundTrip(
            semanticEvents { "ul" { "li" { "p" { +"--" } } } },
            """- \--"""
        )
    }

    @Test
    fun `should not escape list item text a char in the next event keeps from a thematic break`() = runTest {
        // The parser splits `--` off at the escape after it.
        // given
        val markdown = """- --\^y\^"""

        // when
        val once = flowOf(markdown).parse().renderMarkdown()

        // then
        once sameAs markdown
    }

    @Test
    fun `should still escape a leading marker followed by a space in the next event`() = runTest {
        assertRoundTrip(
            semanticEvents { "p" { +"-"; +" a" } },
            """\- a"""
        )
    }

    @Test
    fun `should keep a backslash before a letter unescaped`() = runTest {
        assertParagraphRoundTrip("""C:\Users\x""", """C:\Users\x""")
    }

}
