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

package com.xemantic.markanywhere.render

import com.xemantic.kotlin.test.sameAs
import com.xemantic.markanywhere.flow.semanticEvents
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SemanticEventsRenderingTest {

    // Basic structure tests

    @Test
    fun `should convert empty flow to empty string`() = runTest {
        // given
        val flow = semanticEvents { }

        // when
        val html = flow.render()

        // then
        html sameAs ""
    }

    @Test
    fun `should convert single text event`() = runTest {
        // given
        val flow = semanticEvents {
            +"Hello World"
        }

        // when
        val html = flow.render()

        // then
        html sameAs "Hello World"
    }

    @Test
    fun `should convert multiple consecutive text events`() = runTest {
        // given
        val flow = semanticEvents {
            +"Hello "
            +"World"
            +"!"
        }

        // when
        val html = flow.render()

        // then
        html sameAs "Hello World!"
    }

    @Test
    fun `should convert simple paragraph`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"Hello World"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              Hello World
            </p>
        """.trimIndent()
    }

    @Test
    fun `should convert heading`() = runTest {
        // given
        val flow = semanticEvents {
            "h1" {
                +"Main Title"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <h1>
              Main Title
            </h1>
        """.trimIndent()
    }

    // Nested element tests

    @Test
    fun `should convert nested inline elements`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"This is "
                "strong" {
                    +"bold"
                }
                +" text"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              This is <strong>bold</strong> text
            </p>
        """.trimIndent()
    }

    @Test
    fun `should convert deeply nested elements`() = runTest {
        // given
        val flow = semanticEvents {
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

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              This is <strong><em>bold italic</em></strong> text
            </p>
        """.trimIndent()
    }

    @Test
    fun `should convert multiple sibling paragraphs`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"First paragraph"
            }
            "p" {
                +"Second paragraph"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              First paragraph
            </p>
            <p>
              Second paragraph
            </p>
        """.trimIndent()
    }

    // List structure tests

    @Test
    fun `should convert unordered list`() = runTest {
        // given
        val flow = semanticEvents {
            "ul" {
                "li" { +"Item 1" }
                "li" { +"Item 2" }
                "li" { +"Item 3" }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <ul>
              <li>
                Item 1
              </li>
              <li>
                Item 2
              </li>
              <li>
                Item 3
              </li>
            </ul>
        """.trimIndent()
    }

    @Test
    fun `should convert ordered list`() = runTest {
        // given
        val flow = semanticEvents {
            "ol" {
                "li" { +"First" }
                "li" { +"Second" }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <ol>
              <li>
                First
              </li>
              <li>
                Second
              </li>
            </ol>
        """.trimIndent()
    }

    @Test
    fun `should convert nested list`() = runTest {
        // given
        val flow = semanticEvents {
            "ul" {
                "li" {
                    +"Item 1"
                    "ul" {
                        "li" { +"Nested 1" }
                        "li" { +"Nested 2" }
                    }
                }
                "li" { +"Item 2" }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <ul>
              <li>
                Item 1
                <ul>
                  <li>
                    Nested 1
                  </li>
                  <li>
                    Nested 2
                  </li>
                </ul>
              </li>
              <li>
                Item 2
              </li>
            </ul>
        """.trimIndent()
    }

    @Test
    fun `should convert list items with inline formatting`() = runTest {
        // given
        val flow = semanticEvents {
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

        // when
        val html = flow.render()

        // then
        html sameAs """
            <ul>
              <li>
                Item with <strong>bold</strong>
              </li>
              <li>
                Item with <code>code</code>
              </li>
            </ul>
        """.trimIndent()
    }

    // Attribute tests

    @Test
    fun `should convert link with href attribute`() = runTest {
        // given
        val flow = semanticEvents {
            "a"("href" to "https://example.com") {
                +"Click here"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """<a href="https://example.com">Click here</a>"""
    }

    @Test
    fun `should convert link with multiple attributes`() = runTest {
        // given
        val flow = semanticEvents {
            "a"(
                "href" to "https://example.com",
                "target" to "_blank",
                "rel" to "noopener"
            ) {
                +"External link"
            }
        }

        // when
        val html = flow.render()

        // then
        // Note: attribute order may vary
        html sameAs """<a href="https://example.com" target="_blank" rel="noopener">External link</a>"""
    }

    @Test
    fun `should convert image with attributes`() = runTest {
        // given
        val flow = semanticEvents {
            "img"(
                "src" to "https://example.com/image.png",
                "alt" to "An example image"
            ) {}
        }

        // when
        val html = flow.render()

        // then
        html sameAs """<img src="https://example.com/image.png" alt="An example image"></img>"""
    }

    @Test
    fun `should convert code block with class attribute`() = runTest {
        // given
        val flow = semanticEvents {
            "pre"("class" to "code lang-kotlin") {
                +"fun main() = println(\"Hello\")"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <pre class="code lang-kotlin">
            fun main() = println("Hello")
            </pre>
        """.trimIndent()
    }

    @Test
    fun `should convert input checkbox with attributes`() = runTest {
        // given
        val flow = semanticEvents {
            "input"(
                "type" to "checkbox",
                "checked" to "true"
            ) {}
        }

        // when
        val html = flow.render()

        // then
        html sameAs """<input type="checkbox" checked="true"></input>"""
    }

    @Test
    fun `should handle attribute with special characters`() = runTest {
        // given
        val flow = semanticEvents {
            "a"(
                "href" to "https://example.com?foo=1&bar=2"
            ) {
                +"Link"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """<a href="https://example.com?foo=1&amp;bar=2">Link</a>"""
    }

    @Test
    fun `should handle attribute with quotes`() = runTest {
        // given
        val flow = semanticEvents {
            "div"(
                "data-value" to """Say "Hello" """
            ) {
                +"Content"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <div data-value="Say &quot;Hello&quot; ">
              Content
            </div>
        """.trimIndent()
    }

    // Table structure tests

    @Test
    fun `should convert simple table`() = runTest {
        // given
        val flow = semanticEvents {
            "table" {
                "thead" {
                    "tr" {
                        "th" { +"Header 1" }
                        "th" { +"Header 2" }
                    }
                }
                "tbody" {
                    "tr" {
                        "td" { +"Cell 1" }
                        "td" { +"Cell 2" }
                    }
                }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <table>
              <thead>
                <tr>
                  <th>
                    Header 1
                  </th>
                  <th>
                    Header 2
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>
                    Cell 1
                  </td>
                  <td>
                    Cell 2
                  </td>
                </tr>
              </tbody>
            </table>
        """.trimIndent()
    }

    // Blockquote tests

    @Test
    fun `should convert blockquote`() = runTest {
        // given
        val flow = semanticEvents {
            "blockquote" {
                "p" { +"A wise quote." }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <blockquote>
              <p>
                A wise quote.
              </p>
            </blockquote>
        """.trimIndent()
    }

    @Test
    fun `should convert nested blockquote`() = runTest {
        // given
        val flow = semanticEvents {
            "blockquote" {
                "p" { +"Outer quote" }
                "blockquote" {
                    "p" { +"Inner quote" }
                }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <blockquote>
              <p>
                Outer quote
              </p>
              <blockquote>
                <p>
                  Inner quote
                </p>
              </blockquote>
            </blockquote>
        """.trimIndent()
    }

    // Special content tests

    @Test
    fun `should handle text with HTML special characters`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"Use <div> and & for HTML"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              Use &lt;div&gt; and &amp; for HTML
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle text with newlines`() = runTest {
        // given
        val flow = semanticEvents {
            "pre" {
                +"line 1\nline 2\nline 3"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <pre>
            line 1
            line 2
            line 3
            </pre>
        """.trimIndent()
    }

    @Test
    fun `should handle empty element`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {}
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle self-closing void elements`() = runTest {
        // given
        val flow = semanticEvents {
            "hr" { }
        }

        // when
        val html = flow.render()

        // then
        // For void elements, might want to output <hr> or <hr />
        html sameAs "<hr></hr>"
    }

    @Test
    fun `should handle br element`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"Line 1"
                "br" { }
                +"Line 2"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              Line 1<br></br>Line 2
            </p>
        """.trimIndent()
    }

    // Custom element tests

    @Test
    fun `should convert custom namespaced element`() = runTest {
        // given
        val flow = semanticEvents {
            "custom:element"("attr1" to "value1") {
                +"Custom content"
            }
        }

        // when
        val html = flow.render()

        // then
        // Note: text inside custom markup is not indented to preserve raw content
        html sameAs """
            <custom:element attr1="value1">
            Custom content
            </custom:element>
        """.trimIndent()
    }

    @Test
    fun `should not escape HTML inside custom namespaced element`() = runTest {
        // given
        val flow = semanticEvents(produceTags = true) {
            "custom:html"("type" to "raw") {
                +"<div class=\"inner\">Content with <b>bold</b> & special chars</div>"
            }
        }

        // when
        val html = flow.render()

        // then
        // Note: text inside custom markup is not indented to preserve raw HTML
        html sameAs """
            <custom:html type="raw">
            <div class="inner">Content with <b>bold</b> & special chars</div>
            </custom:html>
        """.trimIndent()
    }

    @Test
    fun `should not escape HTML in nested custom namespaced elements`() = runTest {
        // given
        val flow = semanticEvents(produceTags = true) {
            "outer:wrapper" {
                +"<span>outer html</span>"
                tag("inner:content") {
                    +"<em>inner html</em>"
                }
                +"<span>more outer html</span>"
            }
        }

        // when
        val html = flow.render()

        // then
        // Note: text inside custom markup is not indented to preserve raw HTML
        html sameAs """
            <outer:wrapper>
            <span>outer html</span>
              <inner:content>
            <em>inner html</em>
              </inner:content>
            <span>more outer html</span>
            </outer:wrapper>
        """.trimIndent()
    }

    @Test
    fun `should resume escaping HTML after closing custom namespaced element`() = runTest {
        // given
        val flow = semanticEvents(produceTags = true) {
            "custom:raw" {
                +"<b>not escaped</b>"
            }
            "p" {
                +"<b>escaped</b>"
            }
        }

        // when
        val html = flow.render()

        // then
        // Note: text inside custom markup is not indented to preserve raw HTML
        html sameAs """
            <custom:raw>
            <b>not escaped</b>
            </custom:raw>
            <p>
              &lt;b&gt;escaped&lt;/b&gt;
            </p>
        """.trimIndent()
    }

    @Test
    fun `should not escape HTML in custom element with regular element sibling`() = runTest {
        // given
        val flow = semanticEvents {
            "div" {
                "p" {
                    +"Regular <b>escaped</b> content"
                }
                tag("custom:slot") {
                    +"Raw <b>not escaped</b> content"
                }
                "p" {
                    +"Back to <i>escaped</i> content"
                }
            }
        }

        // when
        val html = flow.render()

        // then
        // Note: text inside custom markup is not indented to preserve raw HTML
        html sameAs """
            <div>
              <p>
                Regular &lt;b&gt;escaped&lt;/b&gt; content
              </p>
              <custom:slot>
            Raw <b>not escaped</b> content
              </custom:slot>
              <p>
                Back to &lt;i&gt;escaped&lt;/i&gt; content
              </p>
            </div>
        """.trimIndent()
    }

    // Complex document tests

    @Test
    fun `should convert complete document structure`() = runTest {
        // given
        val flow = semanticEvents {
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
                    "a"(mapOf("href" to "https://example.com")) { +"link" }
                }
            }
            "pre"(mapOf("class" to "code lang-kotlin")) {
                +"val x = 42"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <h1>
              Document Title
            </h1>
            <p>
              Introduction with <strong>important</strong> information.
            </p>
            <h2>
              Section 1
            </h2>
            <ul>
              <li>
                Point A
              </li>
              <li>
                Point B with <a href="https://example.com">link</a>
              </li>
            </ul>
            <pre class="code lang-kotlin">
            val x = 42
            </pre>
        """.trimIndent()
    }

    @Test
    fun `should convert all heading levels`() = runTest {
        // given
        val flow = semanticEvents {
            "h1" { +"H1" }
            "h2" { +"H2" }
            "h3" { +"H3" }
            "h4" { +"H4" }
            "h5" { +"H5" }
            "h6" { +"H6" }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <h1>
              H1
            </h1>
            <h2>
              H2
            </h2>
            <h3>
              H3
            </h3>
            <h4>
              H4
            </h4>
            <h5>
              H5
            </h5>
            <h6>
              H6
            </h6>
        """.trimIndent()
    }

    @Test
    fun `should convert inline code`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"Use the "
                "code" { +"println()" }
                +" function"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              Use the <code>println()</code> function
            </p>
        """.trimIndent()
    }

    @Test
    fun `should convert emphasis and strong`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                "em" { +"italic" }
                +" and "
                "strong" { +"bold" }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              <em>italic</em> and <strong>bold</strong>
            </p>
        """.trimIndent()
    }

    // Edge case tests

    @Test
    fun `should handle unicode content`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"Hello 世界! 🌍 Привет мир"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              Hello 世界! 🌍 Привет мир
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle empty text events`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +""
                +"Hello"
                +""
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              Hello
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle whitespace-only text`() = runTest {
        // given
        val flow = semanticEvents {
            "pre" {
                +"   "
            }
        }

        // when
        val html = flow.render()

        // then - whitespace-only text inside block elements is preserved
        html sameAs """
            <pre>
               
            </pre>
        """.trimIndent()
    }

    @Test
    fun `should handle element with empty attributes map`() = runTest {
        // given
        val flow = semanticEvents {
            "div"(emptyMap()) {
                +"Content"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <div>
              Content
            </div>
        """.trimIndent()
    }

    @Test
    fun `should handle footnote element`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"See reference"
                "sup" {
                    "a"("href" to "#fn1") { +"1" }
                }
            }
            "footnote"("id" to "fn1") {
                +"The footnote content."
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              See reference<sup><a href="#fn1">1</a></sup>
            </p>
            <footnote id="fn1">
              The footnote content.
            </footnote>
        """.trimIndent()
    }

    @Test
    fun `should handle task list items`() = runTest {
        // given
        val flow = semanticEvents {
            "ul" {
                "li" {
                    "input"(mapOf("type" to "checkbox")) { }
                    +"Unchecked task"
                }
                "li" {
                    "input"(mapOf("type" to "checkbox", "checked" to "true")) { }
                    +"Checked task"
                }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <ul>
              <li>
                <input type="checkbox"></input>Unchecked task
              </li>
              <li>
                <input type="checkbox" checked="true"></input>Checked task
              </li>
            </ul>
        """.trimIndent()
    }

    // Additional edge cases

    @Test
    fun `should handle adjacent inline elements without text between them`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                "strong" { +"bold" }
                "em" { +"italic" }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              <strong>bold</strong><em>italic</em>
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle text with less than and greater than symbols`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"1 < 2 and 3 > 2"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              1 &lt; 2 and 3 &gt; 2
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle attribute with empty value`() = runTest {
        // given
        val flow = semanticEvents {
            "div"("data-empty" to "") {
                +"Content"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <div data-empty="">
              Content
            </div>
        """.trimIndent()
    }

    @Test
    fun `should handle attribute with single quotes in value`() = runTest {
        // given
        val flow = semanticEvents {
            "div"(mapOf("data-value" to "It's a test")) {
                +"Content"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <div data-value="It's a test">
              Content
            </div>
        """.trimIndent()
    }

    @Test
    fun `should handle definition list`() = runTest {
        // given
        val flow = semanticEvents {
            "dl" {
                "dt" { +"Term 1" }
                "dd" { +"Definition 1" }
                "dt" { +"Term 2" }
                "dd" { +"Definition 2" }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <dl>
              <dt>
                Term 1
              </dt>
              <dd>
                Definition 1
              </dd>
              <dt>
                Term 2
              </dt>
              <dd>
                Definition 2
              </dd>
            </dl>
        """.trimIndent()
    }

    @Test
    fun `should handle strikethrough element`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"This is "
                "del" { +"deleted" }
                +" text"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              This is <del>deleted</del> text
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle subscript and superscript`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"H"
                "sub" { +"2" }
                +"O and x"
                "sup" { +"2" }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              H<sub>2</sub>O and x<sup>2</sup>
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle table with multiple rows`() = runTest {
        // given
        val flow = semanticEvents {
            "table" {
                "tbody" {
                    "tr" {
                        "td" { +"A1" }
                        "td" { +"B1" }
                    }
                    "tr" {
                        "td" { +"A2" }
                        "td" { +"B2" }
                    }
                    "tr" {
                        "td" { +"A3" }
                        "td" { +"B3" }
                    }
                }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <table>
              <tbody>
                <tr>
                  <td>
                    A1
                  </td>
                  <td>
                    B1
                  </td>
                </tr>
                <tr>
                  <td>
                    A2
                  </td>
                  <td>
                    B2
                  </td>
                </tr>
                <tr>
                  <td>
                    A3
                  </td>
                  <td>
                    B3
                  </td>
                </tr>
              </tbody>
            </table>
        """.trimIndent()
    }

    @Test
    fun `should handle code block with multiline content`() = runTest {
        // given
        val flow = semanticEvents {
            "pre" {
                "code" {
                    +"fun main() {\n    println(\"Hello\")\n}"
                }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <pre>
            <code>fun main() {
                println("Hello")
            }</code>
            </pre>
        """.trimIndent()
    }

    @Test
    fun `should handle mark highlight element`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"This is "
                "mark" { +"highlighted" }
                +" text"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              This is <mark>highlighted</mark> text
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle kbd keyboard input element`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"Press "
                "kbd" { +"Ctrl" }
                +"+"
                "kbd" { +"C" }
                +" to copy"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              Press <kbd>Ctrl</kbd>+<kbd>C</kbd> to copy
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle abbr abbreviation element`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"The "
                "abbr"(mapOf("title" to "HyperText Markup Language")) { +"HTML" }
                +" specification"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              The <abbr title="HyperText Markup Language">HTML</abbr> specification
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle time element with datetime attribute`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"Published on "
                "time"(mapOf("datetime" to "2025-01-15")) { +"January 15, 2025" }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              Published on <time datetime="2025-01-15">January 15, 2025</time>
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle figure and figcaption`() = runTest {
        // given
        val flow = semanticEvents {
            "figure" {
                "img"(mapOf("src" to "image.png", "alt" to "A figure")) { }
                "figcaption" { +"Figure 1: An example image" }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <figure>
              <img src="image.png" alt="A figure"></img>
              <figcaption>
                Figure 1: An example image
              </figcaption>
            </figure>
        """.trimIndent()
    }

    @Test
    fun `should handle details and summary elements`() = runTest {
        // given
        val flow = semanticEvents {
            "details" {
                "summary" { +"Click to expand" }
                "p" { +"Hidden content revealed!" }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <details>
              <summary>
                Click to expand
              </summary>
              <p>
                Hidden content revealed!
              </p>
            </details>
        """.trimIndent()
    }

    @Test
    fun `should handle text containing only special characters`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"<>&\"'"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              &lt;&gt;&amp;"'
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle very long text without breaks`() = runTest {
        // given
        val longText = "a".repeat(1000)
        val flow = semanticEvents {
            "p" {
                +longText
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              $longText
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle span element`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"Some "
                "span"(mapOf("class" to "highlight")) { +"styled" }
                +" text"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              Some <span class="highlight">styled</span> text
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle ins inserted text element`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"This is "
                "ins" { +"inserted" }
                +" text"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              This is <ins>inserted</ins> text
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle cite element`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"As stated in "
                "cite" { +"The Art of Computer Programming" }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              As stated in <cite>The Art of Computer Programming</cite>
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle q inline quote element`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"He said "
                "q" { +"Hello world" }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              He said <q>Hello world</q>
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle samp sample output element`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"The output was: "
                "samp" { +"Hello, World!" }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              The output was: <samp>Hello, World!</samp>
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle var variable element`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"The variable "
                "var" { +"x" }
                +" represents the input"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              The variable <var>x</var> represents the input
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle empty inline element`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"Before"
                "span" { }
                +"After"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              Before<span></span>After
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle list item with paragraph inside`() = runTest {
        // given
        val flow = semanticEvents {
            "ul" {
                "li" {
                    "p" { +"Paragraph inside list item" }
                }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <ul>
              <li>
                <p>
                  Paragraph inside list item
                </p>
              </li>
            </ul>
        """.trimIndent()
    }

    @Test
    fun `should handle anchor without href`() = runTest {
        // given
        val flow = semanticEvents {
            "a"(mapOf("id" to "section1")) { +"Section 1" }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """<a id="section1">Section 1</a>"""
    }

    @Test
    fun `should handle data element with value`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"Price: "
                "data"(mapOf("value" to "49.99")) { +"$49.99" }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              Price: <data value="49.99">$49.99</data>
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle small element`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"Main text "
                "small" { +"(fine print)" }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              Main text <small>(fine print)</small>
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle bdi bidirectional isolation element`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"User: "
                "bdi" { +"إيان" }
                +" - 90 points"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              User: <bdi>إيان</bdi> - 90 points
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle bdo bidirectional override element`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                "bdo"("dir" to "rtl") { +"This text will be reversed" }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              <bdo dir="rtl">This text will be reversed</bdo>
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle ruby annotation`() = runTest {
        // given
        val flow = semanticEvents {
            "ruby" {
                +"漢"
                "rp" { +"(" }
                "rt" { +"かん" }
                "rp" { +")" }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs "<ruby>漢<rp>(</rp><rt>かん</rt><rp>)</rp></ruby>"
    }

    @Test
    fun `should handle wbr word break opportunity`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"super"
                "wbr" { }
                +"cali"
                "wbr" { }
                +"fragilistic"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              super<wbr></wbr>cali<wbr></wbr>fragilistic
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle deeply nested structure with mixed elements`() = runTest {
        // given
        val flow = semanticEvents {
            "div" {
                "section" {
                    "article" {
                        "header" {
                            "h1" { +"Title" }
                        }
                        "p" { +"Content" }
                        "footer" {
                            "small" { +"Footer" }
                        }
                    }
                }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <div>
              <section>
                <article>
                  <header>
                    <h1>
                      Title
                    </h1>
                  </header>
                  <p>
                    Content
                  </p>
                  <footer>
                    <small>Footer</small>
                  </footer>
                </article>
              </section>
            </div>
        """.trimIndent()
    }

    // Additional edge cases for code coverage

    @Test
    fun `should handle nested pre elements without adding whitespace`() = runTest {
        // given
        val flow = semanticEvents {
            "pre" {
                +"outer\n"
                "pre" {
                    +"inner"
                }
                +"\nouter again"
            }
        }

        // when
        val html = flow.render()

        // then
        // Nested elements inside pre should not add extra newlines or indentation
        // to preserve whitespace semantics when rendered in browser
        html sameAs """
            <pre>
            outer
            <pre>inner</pre>
            outer again
            </pre>
        """.trimIndent()
    }

    @Test
    fun `should not indent inline element at line start inside pre`() = runTest {
        // given
        val flow = semanticEvents {
            "pre" {
                "span" { +"code on first line" }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <pre>
            <span>code on first line</span>
            </pre>
        """.trimIndent()
    }

    @Test
    fun `should handle block element immediately after inline element`() = runTest {
        // given
        val flow = semanticEvents {
            "div" {
                "span" { +"inline" }
                "p" { +"block after inline" }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <div>
              <span>inline</span>
              <p>
                block after inline
              </p>
            </div>
        """.trimIndent()
    }

    @Test
    fun `should handle attribute with less than and greater than symbols`() = runTest {
        // given
        val flow = semanticEvents {
            "div"("data-expr" to "a < b > c") {
                +"Content"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <div data-expr="a &lt; b &gt; c">
              Content
            </div>
        """.trimIndent()
    }

    @Test
    fun `should handle custom namespaced element inside pre without adding whitespace`() = runTest {
        // given
        val flow = semanticEvents {
            "pre" {
                +"before "
                "my:custom"("attr" to "value") {
                    +"custom content"
                }
                +" after"
            }
        }

        // when
        val html = flow.render()

        // then
        // Custom namespaced elements (containing :) are normally block elements,
        // but inside pre they should not add extra whitespace
        html sameAs """
            <pre>
            before <my:custom attr="value">custom content</my:custom> after
            </pre>
        """.trimIndent()
    }

    @Test
    fun `should return to normal formatting after closing pre`() = runTest {
        // given
        val flow = semanticEvents {
            "pre" {
                +"code content"
            }
            "p" {
                +"Normal paragraph after pre"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <pre>
            code content
            </pre>
            <p>
              Normal paragraph after pre
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle text immediately after block element closing tag`() = runTest {
        // given
        val flow = semanticEvents {
            "div" {
                "p" {
                    +"paragraph"
                }
                +"text after block"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <div>
              <p>
                paragraph
              </p>
              text after block
            </div>
        """.trimIndent()
    }

    // Multi-line text indentation tests
    // These tests verify that text containing newlines is properly re-indented on each line

    @Test
    fun `should indent each line of multi-line text in paragraph`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"First line.\nSecond line.\nThird line."
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              First line.
              Second line.
              Third line.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should indent each line of multi-line text in blockquote paragraph`() = runTest {
        // given
        val flow = semanticEvents {
            "blockquote" {
                "p" {
                    +"This is a famous quote.\nIt spans multiple lines."
                }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <blockquote>
              <p>
                This is a famous quote.
                It spans multiple lines.
              </p>
            </blockquote>
        """.trimIndent()
    }

    @Test
    fun `should indent each line of multi-line text in nested blockquotes`() = runTest {
        // given
        val flow = semanticEvents {
            "blockquote" {
                "blockquote" {
                    "p" {
                        +"Deeply nested quote.\nWith multiple lines."
                    }
                }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <blockquote>
              <blockquote>
                <p>
                  Deeply nested quote.
                  With multiple lines.
                </p>
              </blockquote>
            </blockquote>
        """.trimIndent()
    }

    @Test
    fun `should indent each line of multi-line text in list item`() = runTest {
        // given
        val flow = semanticEvents {
            "ul" {
                "li" {
                    +"First line of item.\nSecond line of item."
                }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <ul>
              <li>
                First line of item.
                Second line of item.
              </li>
            </ul>
        """.trimIndent()
    }

    @Test
    fun `should indent multi-line text in deeply nested structure`() = runTest {
        // given
        val flow = semanticEvents {
            "div" {
                "section" {
                    "article" {
                        "p" {
                            +"Line one.\nLine two.\nLine three."
                        }
                    }
                }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <div>
              <section>
                <article>
                  <p>
                    Line one.
                    Line two.
                    Line three.
                  </p>
                </article>
              </section>
            </div>
        """.trimIndent()
    }

    @Test
    fun `should handle text with multiple consecutive newlines`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"First paragraph.\n\nSecond paragraph after blank line."
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              First paragraph.

              Second paragraph after blank line.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle text with leading newline`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"\nText after leading newline."
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>

              Text after leading newline.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle text with trailing newline`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"Text before trailing newline.\n"
            }
        }

        // when
        val html = flow.render()

        // then
        // Trailing newline sets atLineStart=true, so closing tag appears on new line without extra blank line
        html sameAs """
            <p>
              Text before trailing newline.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle multi-line text followed by inline element`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"First line.\nSecond line "
                "strong" { +"bold" }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              First line.
              Second line <strong>bold</strong>
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle inline element followed by multi-line text`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                "em" { +"emphasis" }
                +" followed by\nmulti-line text."
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              <em>emphasis</em> followed by
              multi-line text.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle multi-line text between inline elements`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                "strong" { +"start" }
                +"\nmiddle line\n"
                "em" { +"end" }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              <strong>start</strong>
              middle line
              <em>end</em>
            </p>
        """.trimIndent()
    }

    @Test
    fun `should not re-indent multi-line text inside pre element`() = runTest {
        // given
        val flow = semanticEvents {
            "pre" {
                +"line 1\n  indented line 2\n    more indented line 3"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <pre>
            line 1
              indented line 2
                more indented line 3
            </pre>
        """.trimIndent()
    }

    @Test
    fun `should not re-indent multi-line text inside custom namespaced element`() = runTest {
        // given
        val flow = semanticEvents {
            "custom:raw" {
                +"line 1\n  line 2\n    line 3"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <custom:raw>
            line 1
              line 2
                line 3
            </custom:raw>
        """.trimIndent()
    }

    @Test
    fun `should indent multi-line text in table cells`() = runTest {
        // given
        val flow = semanticEvents {
            "table" {
                "tbody" {
                    "tr" {
                        "td" {
                            +"Cell with\nmultiple lines"
                        }
                    }
                }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <table>
              <tbody>
                <tr>
                  <td>
                    Cell with
                    multiple lines
                  </td>
                </tr>
              </tbody>
            </table>
        """.trimIndent()
    }

    @Test
    fun `should indent multi-line text in definition list`() = runTest {
        // given
        val flow = semanticEvents {
            "dl" {
                "dt" { +"Term" }
                "dd" {
                    +"Definition that spans\nmultiple lines."
                }
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <dl>
              <dt>
                Term
              </dt>
              <dd>
                Definition that spans
                multiple lines.
              </dd>
            </dl>
        """.trimIndent()
    }

    @Test
    fun `should handle only newlines as text content`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"\n\n\n"
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>



            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle mixed single and multi-line text events`() = runTest {
        // given
        val flow = semanticEvents {
            "p" {
                +"Single line. "
                +"Multi-line\ntext here. "
                +"Another single line."
            }
        }

        // when
        val html = flow.render()

        // then
        html sameAs """
            <p>
              Single line. Multi-line
              text here. Another single line.
            </p>
        """.trimIndent()
    }

}