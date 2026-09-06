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

import com.xemantic.kotlin.test.have
import com.xemantic.kotlin.test.should
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
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
    fun `should stop reading a source which never ends once the document is malformed`() = runTest {
        // given — a malformed document followed by a source which never ends
        val chunks = flow {
            emit("<a></b>")
            awaitCancellation()
        }

        // when — the mismatched end tag ends the read, so this completes only
        // because the parser stops asking for what it will no longer read.
        // `StaxXmlParser` cannot: its reader blocks for characters it needs
        // before it can even see the error, which is why this is asserted here
        // rather than in the shared suite.
        val events = parser.parse(chunks).toList()

        // then
        events should {
            have(markNames() == listOf("a"))
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
