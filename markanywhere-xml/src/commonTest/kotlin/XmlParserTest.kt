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
    fun `should keep namespace declarations among the attributes in document order`() = runTest {
        // when — a declaration is an ordinary attribute here, so it keeps its place
        val events = parse("""<a b="1" xmlns="u" c="2" xmlns:p="v"/>""").toList()

        // then
        val attributes = events.filterIsInstance<SemanticEvent.Mark>().single().attributes
        assert(attributes.keys.toList() == listOf("b", "xmlns", "c", "xmlns:p"))
    }

    @Test
    fun `should read names written in a script with combining marks`() = runTest {
        // when — U+093E, the second character, is a spacing combining mark
        val events = parse("<नाम लेख=\"x\">y</नाम>")

        // then
        events sameAs semanticEvents(tagged = true) {
            "नाम"("लेख" to "x") { +"y" }
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
    fun `should stop at a character reference XML does not allow`() = runTest {
        // given — an uppercase X, and digits which are digits only to Unicode
        val references = listOf("&#X41;", "&#\u0661\u0662\u0663;", "&#x\uFF21;")

        references.forEach { reference ->
            // when
            val events = parse("<a>$reference</a>").toList()

            // then
            events should {
                have(markNames() == listOf("a"))
                have(textContent() == "")
                have(isBalanced())
            }
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
    fun `should read prefixes a fragment does not declare`() = runTest {
        // when — an RSS item cut out of its feed, without the feed's xmlns:dc
        val events = parse("""<item q:id="1"><dc:creator>x</dc:creator></item>""")

        // then
        events sameAs semanticEvents(tagged = true) {
            "item"("q:id" to "1") {
                "dc:creator" { +"x" }
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
    fun `should skip a DOCTYPE whose internal subset holds quotes in a comment and an instruction`() = runTest {
        // when — the apostrophes open no literal, so the DOCTYPE still ends
        val events = parse(
            """<!DOCTYPE a [<!-- don't --><?pi it's ?>]><a>text</a>"""
        )

        // then
        events sameAs semanticEvents(tagged = true) {
            "a" { +"text" }
        }
    }

    @Test
    fun `should drop whitespace outside the root element`() = runTest {
        // when — the line breaks after the declaration and at the end of the
        // file are not character data of any element
        val events = parse("<?xml version=\"1.0\"?>\n<!-- c -->\n<a> </a>\n")

        // then
        events sameAs semanticEvents(tagged = true) {
            "a" { +" " }
        }
    }

    @Test
    fun `should drop whitespace outside the root element arriving one character at a time`() = runTest {
        // given
        val xml = "\n \n<a>x</a>\n\n"

        // when
        val events = parser.parse(xml.map { char -> char.toString() }.asFlow())
            .mergeAdjacentText()

        // then
        events sameAs semanticEvents(tagged = true) {
            "a" { +"x" }
        }
    }

    @Test
    fun `should skip a leading byte order mark`() = runTest {
        // when — a UTF-8 file decoded the usual way keeps its BOM as U+FEFF
        val whole = parse("\uFEFF<?xml version=\"1.0\"?>\n<a/>")
        val chunked = parser.parse(flowOf("\uFEFF", "<a>\uFEFF</a>")).mergeAdjacentText()

        // then — only the one at the very start is skipped
        whole sameAs semanticEvents(tagged = true) {
            "a" { }
        }
        chunked sameAs semanticEvents(tagged = true) {
            "a" { +"\uFEFF" }
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
    fun `should parse a document split in two at any position`() = runTest {
        // given — CDATA sections and attribute values holding every character
        // their runs stop at
        val xml = "<a b=\"x &amp; y\tz\r\n'\" c='\"q\"'><![CDATA[1 ]] ]x]>]<&\r\n2]]]>tail\r\n</a>"
        val whole = parse(xml).toList()

        (1 until xml.length).forEach { index ->
            // when
            val events = parser.parse(flowOf(xml.take(index), xml.drop(index)))
                .mergeAdjacentText()
                .toList()

            // then
            assert(events == whole)
        }
        whole.asFlow() sameAs semanticEvents(tagged = true) {
            "a"("b" to "x & y z '", "c" to "\"q\"") { +"1 ]] ]x]>]<&\n2]tail\n" }
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
    fun `should stop reading a source which never ends once the document is malformed`() = runTest {
        // given — a malformed document followed by a source which never ends
        val chunks = flow {
            emit("<a></b>")
            awaitCancellation()
        }

        // when — the mismatched end tag ends the read, so this completes only
        // because the parser stops waiting for what it will no longer read
        val events = parser.parse(chunks).toList()

        // then
        events should {
            have(markNames() == listOf("a"))
            have(isBalanced())
        }
    }

    @Test
    fun `should close open marks instead of throwing on a truncated document`() = runTest {
        // when — the stream ends mid-document
        val events = parse("<a><b>text").toList()

        // then — the elements read before the input ran out are reported, and
        // the mark of every element left open is closed, LIFO. How much of the
        // trailing character data survives is left to the implementation:
        // StAX needs the end of a text run before it can report it.
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

        // then — the text read before the reference stands
        events should {
            have(markNames() == listOf("a"))
            have(textContent() == "text ")
            have(isBalanced())
        }
    }

    @Test
    fun `should stop at a literal character XML does not allow`() = runTest {
        // given — control characters, the two noncharacters of the basic
        // plane, and a surrogate half standing without its pair
        val characters = listOf(
            '\u0000', '\u0001', '\u000B', '\u001F', '\uFFFE', '\uFFFF',
            '\uD800', '\uDBFF', '\uDC00', '\uDFFF'
        )

        characters.forEach { character ->
            // when
            val inText = parse("<a>x${character}y</a>").toList()
            val inAttribute = parse("<a b=\"x${character}y\"/>").toList()

            // then — the character is as malformed as a reference to it. How
            // much of the text run it interrupts survives is left to the
            // implementation: StAX fails the whole run it was scanning.
            inText should {
                have(markNames() == listOf("a"))
                have(character !in textContent())
                have(isBalanced())
            }
            assert(inAttribute.isEmpty())
        }
    }

    @Test
    fun `should read a literal character outside the basic plane`() = runTest {
        // when — written as a surrogate pair, each half of which is no
        // character on its own
        val events = parse("<a b=\"😀\">😀</a>")

        // then
        events sameAs semanticEvents(tagged = true) {
            "a"("b" to "😀") { +"😀" }
        }
    }

    @Test
    fun `should read a surrogate pair split across chunks`() = runTest {
        // when — the chunk boundary falls between the two halves
        val events = parser.parse(flowOf("<a b=\"\uD83D", "\uDE00\">\uD83D", "\uDE00</a>"))
            .mergeAdjacentText()

        // then
        events sameAs semanticEvents(tagged = true) {
            "a"("b" to "😀") { +"😀" }
        }
    }

    @Test
    fun `should read a character reference written with leading zeros`() = runTest {
        // when
        val events = parse("<a>&#x0000000041;&#0000000066;</a>")

        // then
        events sameAs semanticEvents(tagged = true) {
            "a" { +"AB" }
        }
    }

    @Test
    fun `should stop at a decimal character reference continuing as a hexadecimal one`() = runTest {
        // given — the `x` comes after a leading zero, so neither reads as hex
        val references = listOf("&#0x41;", "&#000x0041;")

        references.forEach { reference ->
            // when
            val events = parse("<a>x${reference}y</a>").toList()

            // then
            events should {
                have(markNames() == listOf("a"))
                have('A' !in textContent() && 'y' !in textContent())
                have(isBalanced())
            }
        }
    }

    @Test
    fun `should stop at malformed markup`() = runTest {
        // given — a declaration no keyword opens, a DOCTYPE inside an element,
        // an instruction without a target or with no whitespace after it, an
        // XML declaration not opening the document, and the end of a CDATA
        // section outside one
        val markup = listOf(
            "<!foo>",
            "<!x'>",
            "<!DOCTYPE a>",
            "<??>",
            "<? pi?>",
            "<?pi?data?>",
            "<?a??>",
            "<?xml version=\"1.0\"?>",
            "<?XML version=\"1.0\"?>",
            "]]>",
            "<!-- x -- y -->",
            "<!-- x --->"
        )

        markup.forEach { construct ->
            // when
            val events = parse("<a>x${construct}<b/>y</a>").toList()

            // then
            events should {
                have(markNames() == listOf("a"))
                have('y' !in textContent())
                have(isBalanced())
            }
        }
    }

    @Test
    fun `should stop at an XML declaration preceded by whitespace`() = runTest {
        // when — the declaration must be the very first thing in the document
        val events = parse("\n<?xml version=\"1.0\"?><a/>").toList()

        // then
        assert(events.isEmpty())
    }

    @Test
    fun `should read an instruction holding no data`() = runTest {
        // when — the `?>` may follow the target directly
        val events = parse("<a>x<?pi?>y</a>")

        // then
        events sameAs semanticEvents(tagged = true) {
            "a" { +"xy" }
        }
    }

    @Test
    fun `should read an XML declaration holding every pseudo-attribute`() = runTest {
        // given
        val declarations = listOf(
            "<?xml version = \"1.0\" encoding='UTF-8' standalone=\"no\" ?>\n",
            "<?xml\tversion='1.0'\r\n?>"
        )

        declarations.forEach { declaration ->
            // when
            val events = parse("$declaration<a/>")

            // then
            events sameAs semanticEvents(tagged = true) {
                "a" { }
            }
        }
    }

    @Test
    fun `should stop at a malformed XML declaration`() = runTest {
        // given — no version, an unknown pseudo-attribute, pseudo-attributes
        // out of order or not separated, and values outside their productions
        val declarations = listOf(
            "<?xml?>",
            "<?xml foo?>",
            "<?xml encoding=\"UTF-8\"?>",
            "<?xml version=\"1.0\" foo=\"x\"?>",
            "<?xml version=\"1.0\"encoding=\"UTF-8\"?>",
            "<?xml version=\"1.0\" standalone=\"yes\" encoding=\"UTF-8\"?>",
            "<?xml version=\"1.0\" standalone=\"maybe\"?>",
            "<?xml version=\"1.\"?>",
            "<?xml version=1.0?>"
        )

        declarations.forEach { declaration ->
            // when
            val events = parse("$declaration<a/>").toList()

            // then
            assert(events.isEmpty())
        }
    }

    @Test
    fun `should read a DOCTYPE in each of its forms`() = runTest {
        // given
        val doctypes = listOf(
            "<!DOCTYPE a>",
            "<!DOCTYPE a[]>",
            "<!DOCTYPE  a  SYSTEM  \"s\"  >",
            "<!DOCTYPE a PUBLIC \"-//p\" 's'>",
            "<!DOCTYPE a SYSTEM \"s\"[<!ENTITY e 'v'>] >"
        )

        doctypes.forEach { doctype ->
            // when
            val events = parse("$doctype<a>text</a>")

            // then
            events sameAs semanticEvents(tagged = true) {
                "a" { +"text" }
            }
        }
    }

    @Test
    fun `should stop at a malformed DOCTYPE`() = runTest {
        // given — a literal where none may stand, an unknown or lowercase
        // keyword, literals missing, extra or not separated, a public
        // identifier holding a character it may not, and anything after the
        // internal subset
        val doctypes = listOf(
            "<!DOCTYPE a'b>",
            "<!DOCTYPE 'a'>",
            "<!DOCTYPE a FOO \"s\">",
            "<!DOCTYPE a system \"s\">",
            "<!DOCTYPE a SYSTEM\"s\">",
            "<!DOCTYPE a SYSTEM \"s\" 'x'>",
            "<!DOCTYPE a SYSTEM \"s\"x>",
            "<!DOCTYPE a PUBLIC \"p\">",
            "<!DOCTYPE a PUBLIC \"p\"\"s\">",
            "<!DOCTYPE a PUBLIC \"<\" \"s\">",
            "<!DOCTYPE a [] x>",
            "<!DOCTYPE a><!DOCTYPE a>"
        )

        doctypes.forEach { doctype ->
            // when
            val events = parse("$doctype<a>text</a>").toList()

            // then
            assert(events.isEmpty())
        }
    }

    @Test
    fun `should stop reading a source which never ends at a quote opening no literal in a DOCTYPE`() = runTest {
        // given — the apostrophe must not be taken for a literal swallowing
        // everything after it
        val chunks = flow {
            emit("<!DOCTYPE a'b><a>text</a>")
            awaitCancellation()
        }

        // when
        val events = parser.parse(chunks).toList()

        // then
        assert(events.isEmpty())
    }

    @Test
    fun `should skip an internal subset without checking what stands between its declarations`() = runTest {
        // given — DTDs are not processed, so the subset is not checked: a quote
        // outside a declaration opens no literal, and neither a target-less
        // instruction nor a comment holding `--` stops the read there
        val doctypes = listOf(
            "<!DOCTYPE a [ \" ]>",
            "<!DOCTYPE a [<!ENTITY e 'v'> ' ]>",
            "<!DOCTYPE a [ x ]>",
            "<!DOCTYPE a [% e;]>",
            "<!DOCTYPE a [<??>]>",
            "<!DOCTYPE a [<?xml version=\"1.0\"?>]>",
            "<!DOCTYPE a [<!-- x -- y -->]>"
        )

        doctypes.forEach { doctype ->
            // when
            val events = parse("$doctype<a>text</a>")

            // then
            events sameAs semanticEvents(tagged = true) {
                "a" { +"text" }
            }
        }
    }

    @Test
    fun `should emit no empty text for an empty CDATA section`() = runTest {
        // when — not merged, so that an empty event would show
        val events = parser.parse("<a>x<![CDATA[]]>y</a>").toList()

        // then
        assert(events.none { it is SemanticEvent.Text && it.text.isEmpty() })
    }

    @Test
    fun `should read an instruction whose target only starts with xml`() = runTest {
        // when — only the target `xml` itself is reserved for the declaration
        val events = parse("<a>x<?xml-stylesheet href=\"a.css\"?>y</a>")

        // then
        events sameAs semanticEvents(tagged = true) {
            "a" { +"xy" }
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
