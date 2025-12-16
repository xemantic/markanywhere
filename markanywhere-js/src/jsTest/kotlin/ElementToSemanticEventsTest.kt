/*
 * Copyright 2025 Kazimierz Pogoda / Xemantic
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

import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.js.toSemanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.browser.document
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ElementToSemanticEventsTest {

    @Test
    fun `should convert document body to Flow of SemanticEvents`() = runTest {
        // given
        val html = """
            <h1>Title</h1>
            <p>Lorem ipsum</p>
        """.trimIndent()
        document.body!!.innerHTML = html

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "h1" {
                    +"Title"
                }
                +"\n"
                "p" {
                    +"Lorem ipsum"
                }
            }
        }
    }

    @Test
    fun `should convert document body with attributes to Flow of SemanticEvents`() = runTest {
        // given
        val html = """
            <h1 id="main-title" class="header">Title</h1>
            <p class="content">Lorem ipsum</p>
            <a href="https://example.com" target="_blank">Link</a>
        """.trimIndent()
        document.body!!.innerHTML = html

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "h1"("id" to "main-title", "class" to "header") {
                    +"Title"
                }
                +"\n"
                "p"("class" to "content") {
                    +"Lorem ipsum"
                }
                +"\n"
                "a"("href" to "https://example.com", "target" to "_blank") {
                    +"Link"
                }
            }
        }
    }

    @Test
    fun `should convert simple text`() = runTest {
        // given
        document.body!!.innerHTML = "Hello, World!"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                +"Hello, World!"
            }
        }
    }

    @Test
    fun `should convert single paragraph`() = runTest {
        // given
        document.body!!.innerHTML = "<p>Lorem ipsum</p>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "p" {
                    +"Lorem ipsum"
                }
            }
        }
    }

    @Test
    fun `should convert nested inline elements`() = runTest {
        // given
        document.body!!.innerHTML = "<p>Some <strong>bold</strong> text</p>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "p" {
                    +"Some "
                    "strong" {
                        +"bold"
                    }
                    +" text"
                }
            }
        }
    }

    @Test
    fun `should convert deeply nested structure`() = runTest {
        // given
        document.body!!.innerHTML = """
            <article><header><h1>Main Title</h1></header><section><p>First paragraph</p></section></article>
        """.trimIndent()

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "article" {
                    "header" {
                        "h1" {
                            +"Main Title"
                        }
                    }
                    "section" {
                        "p" {
                            +"First paragraph"
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `should convert multiple sibling elements`() = runTest {
        // given
        document.body!!.innerHTML = "<p>First</p><p>Second</p><p>Third</p>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "p" {
                    +"First"
                }
                "p" {
                    +"Second"
                }
                "p" {
                    +"Third"
                }
            }
        }
    }

    @Test
    fun `should convert inline elements within text`() = runTest {
        // given
        document.body!!.innerHTML = "<p>This is <em>emphasized</em> and <strong>strong</strong> text.</p>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "p" {
                    +"This is "
                    "em" {
                        +"emphasized"
                    }
                    +" and "
                    "strong" {
                        +"strong"
                    }
                    +" text."
                }
            }
        }
    }

    @Test
    fun `should convert unordered list`() = runTest {
        // given
        document.body!!.innerHTML = "<ul><li>Item 1</li><li>Item 2</li><li>Item 3</li></ul>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "ul" {
                    "li" {
                        +"Item 1"
                    }
                    "li" {
                        +"Item 2"
                    }
                    "li" {
                        +"Item 3"
                    }
                }
            }
        }
    }

    @Test
    fun `should convert ordered list`() = runTest {
        // given
        document.body!!.innerHTML = "<ol><li>First</li><li>Second</li></ol>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "ol" {
                    "li" {
                        +"First"
                    }
                    "li" {
                        +"Second"
                    }
                }
            }
        }
    }

    @Test
    fun `should convert nested list`() = runTest {
        // given
        document.body!!.innerHTML = "<ul><li>Item 1<ul><li>Nested 1</li><li>Nested 2</li></ul></li><li>Item 2</li></ul>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "ul" {
                    "li" {
                        +"Item 1"
                        "ul" {
                            "li" {
                                +"Nested 1"
                            }
                            "li" {
                                +"Nested 2"
                            }
                        }
                    }
                    "li" {
                        +"Item 2"
                    }
                }
            }
        }
    }

    @Test
    fun `should convert empty element`() = runTest {
        // given
        document.body!!.innerHTML = "<span></span>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "span" {}
            }
        }
    }

    @Test
    fun `should convert mixed content with text and elements`() = runTest {
        // given
        document.body!!.innerHTML = "Start <b>middle</b> end"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                +"Start "
                "b" {
                    +"middle"
                }
                +" end"
            }
        }
    }

    @Test
    fun `should convert table structure`() = runTest {
        // given
        document.body!!.innerHTML = """
            <table><thead><tr><th>Header 1</th><th>Header 2</th></tr></thead><tbody><tr><td>Cell 1</td><td>Cell 2</td></tr></tbody></table>
        """.trimIndent()

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "table" {
                    "thead" {
                        "tr" {
                            "th" {
                                +"Header 1"
                            }
                            "th" {
                                +"Header 2"
                            }
                        }
                    }
                    "tbody" {
                        "tr" {
                            "td" {
                                +"Cell 1"
                            }
                            "td" {
                                +"Cell 2"
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `should convert code block`() = runTest {
        // given
        document.body!!.innerHTML = "<pre><code>fun main() = println(\"Hello\")</code></pre>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "pre" {
                    "code" {
                        +"fun main() = println(\"Hello\")"
                    }
                }
            }
        }
    }

    @Test
    fun `should convert inline code`() = runTest {
        // given
        document.body!!.innerHTML = "<p>Use the <code>println()</code> function</p>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "p" {
                    +"Use the "
                    "code" {
                        +"println()"
                    }
                    +" function"
                }
            }
        }
    }

    @Test
    fun `should convert blockquote`() = runTest {
        // given
        document.body!!.innerHTML = "<blockquote><p>A wise quote.</p></blockquote>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "blockquote" {
                    "p" {
                        +"A wise quote."
                    }
                }
            }
        }
    }

    @Test
    fun `should convert nested blockquote`() = runTest {
        // given
        document.body!!.innerHTML = "<blockquote><p>Outer quote</p><blockquote><p>Inner quote</p></blockquote></blockquote>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "blockquote" {
                    "p" {
                        +"Outer quote"
                    }
                    "blockquote" {
                        "p" {
                            +"Inner quote"
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `should convert definition list`() = runTest {
        // given
        document.body!!.innerHTML = "<dl><dt>Term 1</dt><dd>Definition 1</dd><dt>Term 2</dt><dd>Definition 2</dd></dl>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "dl" {
                    "dt" {
                        +"Term 1"
                    }
                    "dd" {
                        +"Definition 1"
                    }
                    "dt" {
                        +"Term 2"
                    }
                    "dd" {
                        +"Definition 2"
                    }
                }
            }
        }
    }

    @Test
    fun `should convert all heading levels`() = runTest {
        // given
        document.body!!.innerHTML = "<h1>H1</h1><h2>H2</h2><h3>H3</h3><h4>H4</h4><h5>H5</h5><h6>H6</h6>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "h1" { +"H1" }
                "h2" { +"H2" }
                "h3" { +"H3" }
                "h4" { +"H4" }
                "h5" { +"H5" }
                "h6" { +"H6" }
            }
        }
    }

    @Test
    fun `should convert deeply nested inline elements`() = runTest {
        // given
        document.body!!.innerHTML = "<p>This is <strong><em>bold italic</em></strong> text</p>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "p" {
                    +"This is "
                    "strong" {
                        "em" {
                            +"bold italic"
                        }
                    }
                    +" text"
                }
            }
        }
    }

    @Test
    fun `should convert figure with figcaption`() = runTest {
        // given
        document.body!!.innerHTML = """<figure><img src="image.png" alt="An image"><figcaption>Figure 1</figcaption></figure>"""

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "figure" {
                    "img"("src" to "image.png", "alt" to "An image") {}
                    "figcaption" {
                        +"Figure 1"
                    }
                }
            }
        }
    }

    @Test
    fun `should convert details and summary`() = runTest {
        // given
        document.body!!.innerHTML = "<details><summary>Click to expand</summary><p>Hidden content</p></details>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "details" {
                    "summary" {
                        +"Click to expand"
                    }
                    "p" {
                        +"Hidden content"
                    }
                }
            }
        }
    }

    @Test
    fun `should convert subscript and superscript`() = runTest {
        // given
        document.body!!.innerHTML = "<p>H<sub>2</sub>O and x<sup>2</sup></p>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "p" {
                    +"H"
                    "sub" { +"2" }
                    +"O and x"
                    "sup" { +"2" }
                }
            }
        }
    }

    @Test
    fun `should convert strikethrough and inserted text`() = runTest {
        // given
        document.body!!.innerHTML = "<p>This is <del>deleted</del> and <ins>inserted</ins> text</p>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "p" {
                    +"This is "
                    "del" { +"deleted" }
                    +" and "
                    "ins" { +"inserted" }
                    +" text"
                }
            }
        }
    }

    @Test
    fun `should convert mark highlight element`() = runTest {
        // given
        document.body!!.innerHTML = "<p>This is <mark>highlighted</mark> text</p>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "p" {
                    +"This is "
                    "mark" { +"highlighted" }
                    +" text"
                }
            }
        }
    }

    @Test
    fun `should convert kbd element`() = runTest {
        // given
        document.body!!.innerHTML = "<p>Press <kbd>Ctrl</kbd>+<kbd>C</kbd> to copy</p>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "p" {
                    +"Press "
                    "kbd" { +"Ctrl" }
                    +"+"
                    "kbd" { +"C" }
                    +" to copy"
                }
            }
        }
    }

    @Test
    fun `should convert abbr element with title`() = runTest {
        // given
        document.body!!.innerHTML = """<p>The <abbr title="HyperText Markup Language">HTML</abbr> spec</p>"""

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "p" {
                    +"The "
                    "abbr"("title" to "HyperText Markup Language") { +"HTML" }
                    +" spec"
                }
            }
        }
    }

    @Test
    fun `should convert time element with datetime`() = runTest {
        // given
        document.body!!.innerHTML = """<p>Published on <time datetime="2025-01-15">January 15, 2025</time></p>"""

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "p" {
                    +"Published on "
                    "time"("datetime" to "2025-01-15") { +"January 15, 2025" }
                }
            }
        }
    }

    @Test
    fun `should convert cite element`() = runTest {
        // given
        document.body!!.innerHTML = "<p>As stated in <cite>The Art of Computer Programming</cite></p>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "p" {
                    +"As stated in "
                    "cite" { +"The Art of Computer Programming" }
                }
            }
        }
    }

    @Test
    fun `should convert q inline quote element`() = runTest {
        // given
        document.body!!.innerHTML = "<p>He said <q>Hello world</q></p>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "p" {
                    +"He said "
                    "q" { +"Hello world" }
                }
            }
        }
    }

    @Test
    fun `should convert samp and var elements`() = runTest {
        // given
        document.body!!.innerHTML = "<p>The output <samp>result</samp> for variable <var>x</var></p>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "p" {
                    +"The output "
                    "samp" { +"result" }
                    +" for variable "
                    "var" { +"x" }
                }
            }
        }
    }

    @Test
    fun `should convert small element`() = runTest {
        // given
        document.body!!.innerHTML = "<p>Main text <small>(fine print)</small></p>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "p" {
                    +"Main text "
                    "small" { +"(fine print)" }
                }
            }
        }
    }

    @Test
    fun `should convert data element with value`() = runTest {
        // given
        document.body!!.innerHTML = """<p>Price: <data value="49.99">$49.99</data></p>"""

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "p" {
                    +"Price: "
                    "data"("value" to "49.99") { +"$49.99" }
                }
            }
        }
    }

    @Test
    fun `should handle unicode content`() = runTest {
        // given
        document.body!!.innerHTML = "<p>Hello 世界! 🌍 Привет мир</p>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "p" {
                    +"Hello 世界! 🌍 Привет мир"
                }
            }
        }
    }

    @Test
    fun `should extract unescaped HTML special characters from text`() = runTest {
        // given - browser receives escaped HTML entities
        document.body!!.innerHTML = """<p>Use &lt;div&gt; and &lt;span&gt; tags &amp; escape "quotes"</p>"""

        // when
        val events = document.body!!.toSemanticEvents()

        // then - semantic events contain unescaped text (escaping done on render)
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "p" {
                    +"""Use <div> and <span> tags & escape "quotes""""
                }
            }
        }
    }

    @Test
    fun `should extract unescaped special characters from attribute values`() = runTest {
        // given - ampersand in URL is escaped in HTML
        document.body!!.innerHTML = """<a href="https://example.com?foo=1&amp;bar=2">Link</a>"""

        // when
        val events = document.body!!.toSemanticEvents()

        // then - attribute value is unescaped
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "a"("href" to "https://example.com?foo=1&bar=2") {
                    +"Link"
                }
            }
        }
    }

    @Test
    fun `should extract unescaped quotes from attribute values`() = runTest {
        // given - quotes in attribute are escaped
        document.body!!.innerHTML = """<div data-json="{&quot;key&quot;: &quot;value&quot;}">Content</div>"""

        // when
        val events = document.body!!.toSemanticEvents()

        // then - attribute value contains unescaped quotes
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "div"("data-json" to """{"key": "value"}""") {
                    +"Content"
                }
            }
        }
    }

    @Test
    fun `should extract less than and greater than from text`() = runTest {
        // given
        document.body!!.innerHTML = "<p>1 &lt; 2 and 3 &gt; 2</p>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "p" {
                    +"1 < 2 and 3 > 2"
                }
            }
        }
    }

    @Test
    fun `should convert adjacent inline elements without text between them`() = runTest {
        // given
        document.body!!.innerHTML = "<p><strong>bold</strong><em>italic</em></p>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "p" {
                    "strong" { +"bold" }
                    "em" { +"italic" }
                }
            }
        }
    }

    @Test
    fun `should convert list items with inline formatting`() = runTest {
        // given
        document.body!!.innerHTML = "<ul><li>Item with <strong>bold</strong></li><li>Item with <code>code</code></li></ul>"

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "ul" {
                    "li" {
                        +"Item with "
                        "strong" { +"bold" }
                    }
                    "li" {
                        +"Item with "
                        "code" { +"code" }
                    }
                }
            }
        }
    }

    @Test
    fun `should convert complete document structure`() = runTest {
        // given
        document.body!!.innerHTML = """
            <h1>Document Title</h1><p>Introduction with <strong>important</strong> information.</p><h2>Section 1</h2><ul><li>Point A</li><li>Point B with <a href="https://example.com">link</a></li></ul><pre class="code">val x = 42</pre>
        """.trimIndent()

        // when
        val events = document.body!!.toSemanticEvents()

        // then
        events sameAs semanticEvents(produceTags = true) {
            "body" {
                "h1" { +"Document Title" }
                "p" {
                    +"Introduction with "
                    "strong" { +"important" }
                    +" information."
                }
                "h2" { +"Section 1" }
                "ul" {
                    "li" { +"Point A" }
                    "li" {
                        +"Point B with "
                        "a"("href" to "https://example.com") { +"link" }
                    }
                }
                "pre"("class" to "code") {
                    +"val x = 42"
                }
            }
        }
    }

}
