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

package com.xemantic.markanywhere.xml

import com.xemantic.kotlin.test.assert
import com.xemantic.kotlin.test.have
import com.xemantic.kotlin.test.should
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import com.xemantic.markanywhere.textContent
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * The conformance suite every [XmlParser] implementation must satisfy — the
 * executable form of the mapping contract documented on [XmlParser].
 *
 * It is an abstract class rather than a set of free functions so that each
 * implementation gets its own reported test case: a concrete subclass supplies
 * the [parser] and inherits the whole suite. Two implementations passing this
 * suite produce indistinguishable event streams, which is what makes a
 * performance comparison between them meaningful.
 */
abstract class XmlParserTest {

    abstract val parser: XmlParser

    /**
     * Parses [xml] with maximal text runs, so that an implementation's
     * arbitrary text splitting (chunk boundaries, expanded entities, CDATA
     * section ends) does not show up in the expectations — see the *Text
     * granularity* section of the [XmlParser] contract.
     */
    protected fun parse(
        xml: String
    ): Flow<SemanticEvent> = parser.parse(xml).mergeAdjacentText()

    @Test
    fun `should parse an empty element`() = runTest {
        // when
        val events = parse("<foo/>")

        // then
        events sameAs semanticEvents(tagged = true) {
            "foo" { }
        }
    }

    @Test
    fun `should parse an empty element written as a tag pair`() = runTest {
        // when — indistinguishable from the self-closed form
        val events = parse("<foo></foo>")

        // then
        events sameAs semanticEvents(tagged = true) {
            "foo" { }
        }
    }

    @Test
    fun `should parse nested elements with text`() = runTest {
        // when
        val events = parse("<a>one<b>two</b>three</a>")

        // then
        events sameAs semanticEvents(tagged = true) {
            "a" {
                +"one"
                "b" { +"two" }
                +"three"
            }
        }
    }

    @Test
    fun `should preserve element name case`() = runTest {
        // when — XML is case-sensitive, unlike the HTML5 names the Markdown
        // parser lowercases
        val events = parse("<Warning><Detail/></Warning>")

        // then
        events sameAs semanticEvents(tagged = true) {
            "Warning" {
                "Detail" { }
            }
        }
    }

    @Test
    fun `should parse attributes in document order`() = runTest {
        // when
        val events = parse("""<a href="/x" id="y" title='z'/>""")

        // then
        events sameAs semanticEvents(tagged = true) {
            "a"("href" to "/x", "id" to "y", "title" to "z") { }
        }
    }

    @Test
    fun `should decode predefined entities and character references`() = runTest {
        // when
        val events = parse(
            "<p title=\"&lt;&amp;&gt;\">&lt;a&gt; &amp; &apos;b&apos; &quot;c&quot; &#65;&#x42;</p>"
        )

        // then
        events sameAs semanticEvents(tagged = true) {
            "p"("title" to "<&>") {
                +"""<a> & 'b' "c" AB"""
            }
        }
    }

    @Test
    fun `should decode a character reference outside the basic plane`() = runTest {
        // when — a code point above U+FFFF is a surrogate pair in the text
        val events = parse("<a>&#128512;&#x1F600;</a>")

        // then
        events sameAs semanticEvents(tagged = true) {
            "a" { +"😀😀" }
        }
    }

    @Test
    fun `should read a CDATA section as plain text`() = runTest {
        // when — the CDATA boundaries are not preserved, so this is
        // indistinguishable from the escaped form asserted below
        val events = parse("<code><![CDATA[if (a < b && c) { }]]></code>")

        // then
        events sameAs semanticEvents(tagged = true) {
            "code" { +"if (a < b && c) { }" }
        }
    }

    @Test
    fun `should read a CDATA section and its escaped form identically`() = runTest {
        // when
        val cdata = parse("<code><![CDATA[a < b]]></code>").toList()
        val escaped = parse("<code>a &lt; b</code>").toList()

        // then
        assert(cdata == escaped)
    }

    @Test
    fun `should keep namespace prefixes and declarations`() = runTest {
        // when — names stay qualified as written, and the xmlns declarations
        // survive as ordinary attributes
        val events = parse(
            """<svg:svg xmlns:svg="http://www.w3.org/2000/svg"><svg:rect x="1"/></svg:svg>"""
        )

        // then
        events sameAs semanticEvents(tagged = true) {
            "svg:svg"("xmlns:svg" to "http://www.w3.org/2000/svg") {
                "svg:rect"("x" to "1") { }
            }
        }
    }

    @Test
    fun `should keep a default namespace declaration`() = runTest {
        // when
        val events = parse("""<html xmlns="http://www.w3.org/1999/xhtml"><br/></html>""")

        // then
        events sameAs semanticEvents(tagged = true) {
            "html"("xmlns" to "http://www.w3.org/1999/xhtml") {
                "br" { }
            }
        }
    }

    @Test
    fun `should drop comments and processing instructions`() = runTest {
        // when
        val events = parse(
            """<?xml version="1.0"?><a><!-- note --><?target data?>text</a>"""
        )

        // then
        events sameAs semanticEvents(tagged = true) {
            "a" { +"text" }
        }
    }

    @Test
    fun `should drop a DOCTYPE declaration without rejecting the document`() = runTest {
        // when
        val events = parse("""<!DOCTYPE a SYSTEM "a.dtd"><a>text</a>""")

        // then
        events sameAs semanticEvents(tagged = true) {
            "a" { +"text" }
        }
    }

    @Test
    fun `should preserve whitespace in element content`() = runTest {
        // when — unlike HTML, every whitespace character is character data
        val events = parse("<a>  x\n  <b/>\n</a>")

        // then
        events sameAs semanticEvents(tagged = true) {
            "a" {
                +"  x\n  "
                "b" { }
                +"\n"
            }
        }
    }

    @Test
    fun `should normalize line endings in element content`() = runTest {
        // when — XML reads a CRLF pair, and a lone CR, as a single LF
        val events = parse("<a>x\r\ny\rz</a>")

        // then
        events sameAs semanticEvents(tagged = true) {
            "a" { +"x\ny\nz" }
        }
    }

    @Test
    fun `should normalize whitespace in attribute values`() = runTest {
        // when — a tab, a line feed and a carriage return each become a space,
        // while a character reference to the very same character does not
        val events = parse("<a b=\"x\ty\r\nz\" c=\"x&#9;y\"/>")

        // then
        events sameAs semanticEvents(tagged = true) {
            "a"("b" to "x y z", "c" to "x\ty") { }
        }
    }

    @Test
    fun `should parse a document arriving in arbitrary chunks`() = runTest {
        // given — split mid-tag, mid-attribute-value and mid-entity
        val chunks = listOf("<a hr", "ef=\"/", "x\">on", "e &am", "p; two</", "a>")

        // when
        val events = parser.parse(chunks.asFlow()).mergeAdjacentText()

        // then
        events sameAs semanticEvents(tagged = true) {
            "a"("href" to "/x") { +"one & two" }
        }
    }

    @Test
    fun `should parse a document delivered one character at a time`() = runTest {
        // given — far more chunks than any internal buffer holds, so a parser
        // bridging to a blocking reader has to survive back-pressure both ways
        val items = 100
        val xml = (1..items).joinToString(
            separator = "",
            prefix = "<list>",
            postfix = "</list>"
        ) { "<item id=\"$it\">x</item>" }

        // when
        val events = parser.parse(xml.map { char -> char.toString() }.asFlow())
            .mergeAdjacentText()
            .toList()

        // then
        events should {
            have(markNames().size == items + 1)
            have(textContent() == "x".repeat(items))
            have(isBalanced())
        }
    }

    @Test
    fun `should emit events before the last chunk arrives`() = runTest {
        // given — a document which opens an element and then never ends
        val chunks = flow {
            emit("<a>x<b/>")
            awaitCancellation()
        }

        // when — taking the first event cancels the source, so this can only
        // complete if the parser reported the element as it was read
        val events = parser.parse(chunks).take(1).toList()

        // then
        assert(events == listOf(SemanticEvent.Mark("a", isTagged = true)))
    }

    @Test
    fun `should close open marks instead of throwing on a truncated document`() = runTest {
        // when — the stream ends mid-document
        val events = parse("<a><b>text").toList()

        // then — the elements read before the input ran out are reported, and
        // the mark of every element left open is closed, LIFO. How much of the
        // trailing character data survives is left to the implementation: a
        // coalescing parser needs the end of a text run before it can report it.
        events should {
            have(markNames() == listOf("a", "b"))
            have(isBalanced())
        }
    }

    @Test
    fun `should close open marks instead of throwing on mismatched tags`() = runTest {
        // when
        val events = parse("<a><b>text</a>").toList()

        // then — `</a>` cannot close `b`, so the parser gives up there rather
        // than emitting an unmark which crosses an open mark
        events should {
            have(markNames() == listOf("a", "b"))
            have(textContent() == "text")
            have(isBalanced())
        }
    }

    @Test
    fun `should close open marks instead of throwing on an undeclared entity`() = runTest {
        // when — no DTD is processed, so nothing beyond the five predefined
        // entities can be resolved
        val events = parse("<a>text &nbsp; more</a>").toList()

        // then
        events should {
            have(markNames() == listOf("a"))
            have(isBalanced())
        }
    }

    @Test
    fun `should not throw on input which is not XML at all`() = runTest {
        // when
        val events = parse("just some text < & > with no markup").toList()

        // then
        assert(events.isBalanced())
    }

}

/** The names of the elements opened in this stream, in document order. */
internal fun List<SemanticEvent>.markNames(): List<String> =
    filterIsInstance<SemanticEvent.Mark>().map { it.name }

/**
 * True when every [SemanticEvent.Mark] is paired with a matching
 * [SemanticEvent.Unmark] in LIFO order — the invariant which lets any
 * downstream renderer consume the stream, and the one a parser must uphold
 * even for input it could not fully read.
 */
internal fun List<SemanticEvent>.isBalanced(): Boolean {
    val open = ArrayDeque<String>()
    for (event in this) when (event) {
        is Mark -> open.addLast(event.name)
        is Text -> {}
        is Unmark -> if (open.removeLastOrNull() != event.name) return false
    }
    return open.isEmpty()
}
