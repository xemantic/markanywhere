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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Runs the whole [XmlParserTest] conformance suite against the multiplatform
 * parser — on every target the module is built for — and adds what the contract
 * leaves to the implementation: how much of a document violating a constraint
 * about its structure, rather than about its markup, is still read.
 */
class MarkanywhereXmlParserTest : XmlParserTest() {

    override val parser: XmlParser = MarkanywhereXmlParser()

    @Test
    fun `should stop reading XML parsed out of another parse without disturbing it`() = runTest {
        // given — the inner document escapes a malformed one, as an RSS
        // description escapes its HTML, and every inner event reaches the outer
        // parser as a chunk
        val inner = MarkanywhereXmlParser().parse(flowOf("<a>&lt;b&gt;&lt;/c&gt;</a>"))
            .map { event -> (event as? SemanticEvent.Text)?.text ?: "" }

        // when — the outer read stops at `</c>`, while the inner one is still
        // emitting: its abort must end the inner parse too, not be caught there
        val events = parser.parse(inner).toList()

        // then
        events should {
            have(markNames() == listOf("b"))
            have(isBalanced())
        }
    }

    @Test
    fun `should read a fragment holding more than one root element`() = runTest {
        // when — a fragment is what markanywhere is usually handed, so the
        // one-root rule is not enforced
        val events = parse("<a/><b>text</b>")

        // then
        events sameAs semanticEvents(tagged = true) {
            "a" { }
            "b" { +"text" }
        }
    }

    @Test
    fun `should read text standing outside any element`() = runTest {
        // when
        val events = parse("before<a/>after")

        // then
        events sameAs semanticEvents(tagged = true) {
            +"before"
            "a" { }
            +"after"
        }
    }

    @Test
    fun `should keep whitespace continuing text outside any element across chunks`() = runTest {
        // when — split right before the space, so that the chunk ending in
        // `foo` has already flushed it when the space arrives
        val beforeElement = parser.parse(flowOf("foo", " <a/>")).mergeAdjacentText()
        val atEnd = parser.parse(flowOf("foo", " ")).mergeAdjacentText()

        // then — the same text the document read as one chunk yields
        beforeElement sameAs semanticEvents(tagged = true) {
            +"foo "
            "a" { }
        }
        atEnd sameAs semanticEvents(tagged = true) {
            +"foo "
        }
    }

    @Test
    fun `should drop whitespace between markup outside any element after text`() = runTest {
        // when — the line break after the comment stands between markup, like
        // the prolog's, although text was read before it
        val events = parse("x\n<!-- c -->\n<a/>\n<!-- d -->\n")

        // then
        events sameAs semanticEvents(tagged = true) {
            +"x\n"
            "a" { }
        }
    }

    @Test
    fun `should keep whitespace written as character data outside any element`() = runTest {
        // when — a reference or a CDATA section is character data, not layout
        val events = parse("&#32;<a/><![CDATA[ ]]>")

        // then
        events sameAs semanticEvents(tagged = true) {
            +" "
            "a" { }
            +" "
        }
    }

    @Test
    fun `should drop whitespace after a CDATA section outside any element`() = runTest {
        // when — the line break after the section stands between markup, as
        // it would after a comment
        val betweenElements = parse("<a/><![CDATA[]]>\n<b/>")
        val atEnd = parse("<a/><![CDATA[x]]>\n")

        // then
        betweenElements sameAs semanticEvents(tagged = true) {
            "a" { }
            "b" { }
        }
        atEnd sameAs semanticEvents(tagged = true) {
            "a" { }
            +"x"
        }
    }

    @Test
    fun `should stop reading a reference longer than any which resolves`() = runTest {
        // given — a source which never ends the reference it started
        val endless = flow {
            emit("<a>x&")
            repeat(1_000) { emit("b") }
            error("the reference was read to the end")
        }

        // when
        val events = parser.parse(endless).mergeAdjacentText()

        // then
        events sameAs semanticEvents(tagged = true) {
            "a" { +"x" }
        }
    }

    @Test
    fun `should bound the whitespace held outside any element`() = runTest {
        // when — only what follows the run tells whether it is layout
        val events = parse("<a/>" + " ".repeat(100_000) + "y").toList()

        // then — truncated, yet still separating the text from the element
        val text = (events.last() as SemanticEvent.Text).text
        assert(text.length < 100_000)
        assert(text.startsWith(" ") && text.endsWith(" y"))
    }

    @Test
    fun `should keep the text read before a literal character XML does not allow`() = runTest {
        // when
        val events = parse("<a>x\u0000y</a>")

        // then
        events sameAs semanticEvents(tagged = true) {
            "a" { +"x" }
        }
    }

    @Test
    fun `should keep the right brackets read of a CDATA section cut short`() = runTest {
        // when — the stream ends where the brackets may yet have ended the section
        val events = parse("<a><![CDATA[x]]")

        // then
        events sameAs semanticEvents(tagged = true) {
            "a" { +"x]]" }
        }
    }

    @Test
    fun `should keep the last value of a repeated attribute`() = runTest {
        // when — a repeated attribute makes a document invalid, but leaves no
        // doubt about what was written
        val events = parse("""<a href="/first" href="/second"/>""")

        // then
        events sameAs semanticEvents(tagged = true) {
            "a"("href" to "/second") { }
        }
    }

    @Test
    fun `should read a comment holding markup-like content`() = runTest {
        // when — everything up to the first `-->` is comment, dashes included
        val events = parse("<a><!-- <b/> & -- text --></a>")

        // then
        events sameAs semanticEvents(tagged = true) {
            "a" { }
        }
    }

    @Test
    fun `should read a right bracket ending a CDATA section as its content`() = runTest {
        // when — the `]]>` is the last three characters, the first `]` content
        val events = parse("<a><![CDATA[x]]]></a>")

        // then
        events sameAs semanticEvents(tagged = true) {
            "a" { +"x]" }
        }
    }

    @Test
    fun `should skip a DOCTYPE whose internal subset holds brackets in a comment and an instruction`() = runTest {
        // when — a `]` followed by `>` inside a comment or an instruction ends
        // nothing. The JDK's StAX, with DTD support off, ends the DOCTYPE at the
        // first such pair and rejects the rest as content in the prolog, which
        // is why this is asserted here rather than in the shared suite.
        val events = parse(
            """<!DOCTYPE a [<!-- ] > --><?pi ] >?>]><a>text</a>"""
        )

        // then
        events sameAs semanticEvents(tagged = true) {
            "a" { +"text" }
        }
    }

    @Test
    fun `should skip a DOCTYPE holding an internal subset`() = runTest {
        // when — neither the `>` of the element declaration nor the one in the
        // system literal ends the DOCTYPE
        val events = parse(
            """<!DOCTYPE a SYSTEM "a>b.dtd" [<!ELEMENT a (#PCDATA)>]><a>text</a>"""
        )

        // then
        events sameAs semanticEvents(tagged = true) {
            "a" { +"text" }
        }
    }

}
