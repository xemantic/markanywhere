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

package com.xemantic.markanywhere.parse

import com.xemantic.kotlin.test.sameAs
import com.xemantic.markanywhere.flow.flowRandomLengthChunks
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MarkanywhereParserTest {

    @Test
    fun `should parse simple Hello World markdown`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            # Hello World

            This is a simple paragraph.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <h1>Hello World</h1>
            <p>
              This is a simple paragraph.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse paragraph immediately after header without blank line`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            # Hello World
            This paragraph follows immediately.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <h1>Hello World</h1>
            <p>
              This paragraph follows immediately.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse list immediately after header without blank line`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            # Shopping List
            - Apples
            - Bananas
            - Oranges
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <h1>Shopping List</h1>
            <ul>
              <li>Apples</li>
              <li>Bananas</li>
              <li>Oranges</li>
            </ul>
        """.trimIndent()
    }

    @Test
    fun `should parse list immediately after descriptive paragraph without blank line`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            Here are the items:
            - First item
            - Second item
            - Third item
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              Here are the items:
            </p>
            <ul>
              <li>First item</li>
              <li>Second item</li>
              <li>Third item</li>
            </ul>
        """.trimIndent()
    }

    @Test
    fun `should parse ordered list immediately after descriptive paragraph without blank line`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            Follow these steps:
            1. First step
            2. Second step
            3. Third step
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              Follow these steps:
            </p>
            <ol>
              <li>First step</li>
              <li>Second step</li>
              <li>Third step</li>
            </ol>
        """.trimIndent()
    }

    @Test
    fun `should parse code block immediately after paragraph without blank line`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            Here is the code:
            ```kotlin
            fun hello() = println("Hello")
            ```
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              Here is the code:
            </p>
            <pre class="code lang-kotlin">
              fun hello() = println("Hello")
            </pre>
        """.trimIndent()
    }

    @Test
    fun `should parse multiple headers without blank lines between them`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            # Main Title
            ## Subtitle
            ### Section
            Content here.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <h1>Main Title</h1>
            <h2>Subtitle</h2>
            <h3>Section</h3>
            <p>
              Content here.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse blockquote immediately after paragraph without blank line`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            As someone once said:
            > This is a famous quote.
            > It spans multiple lines.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              As someone once said:
            </p>
            <blockquote>
              <p>
                This is a famous quote.
                It spans multiple lines.
              </p>
            </blockquote>
        """.trimIndent()
    }

    @Test
    fun `should parse compact LLM-style output with no blank lines`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            # Summary
            Here is what I found:
            - Point one with **emphasis**
            - Point two with `code`
            - Point three
            ## Details
            More information follows:
            1. First detail
            2. Second detail
            Here is an example:
            ```python
            print("hello")
            ```
            That concludes the response.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <h1>Summary</h1>
            <p>
              Here is what I found:
            </p>
            <ul>
              <li>Point one with <strong>emphasis</strong></li>
              <li>Point two with <code>code</code></li>
              <li>Point three</li>
            </ul>
            <h2>Details</h2>
            <p>
              More information follows:
            </p>
            <ol>
              <li>First detail</li>
              <li>Second detail</li>
            </ol>
            <p>
              Here is an example:
            </p>
            <pre class="code lang-python">
              print("hello")
            </pre>
            <p>
              That concludes the response.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse comprehensive markdown with all elements`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            # Heading 1

            ## Heading 2

            ### Heading 3

            #### Heading 4

            ##### Heading 5

            ###### Heading 6

            This is a paragraph with **bold text**, *italic text*, and ***bold italic text***.

            Here is some `inline code` within a paragraph.

            > This is a blockquote.
            > It can span multiple lines.

            ---

            Here is an unordered list:

            - First item
            - Second item
            - Third item with **bold**

            And an ordered list:

            1. First numbered item
            2. Second numbered item
            3. Third numbered item

            Here is a link: [Example](https://example.com)

            And an image: ![Alt text](https://example.com/image.png)

            A code block with language specification:

            ```kotlin
            fun main() {
                println("Hello, World!")
            }
            ```

            A code block without language:

            ```
            plain text code block
            ```

            | Column 1 | Column 2 | Column 3 |
            |----------|----------|----------|
            | Cell 1   | Cell 2   | Cell 3   |
            | Cell 4   | Cell 5   | Cell 6   |

            - [ ] Unchecked task
            - [x] Checked task

            Here is a footnote reference[^1].

            [^1]: This is the footnote content.

            <custom:element attr1="value1" attr2="value2">
            Custom element content with **markdown** inside.
            </custom:element>

            <another:tag purpose="testing">
            More custom content.
            </another:tag>
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <h1>Heading 1</h1>
            <h2>Heading 2</h2>
            <h3>Heading 3</h3>
            <h4>Heading 4</h4>
            <h5>Heading 5</h5>
            <h6>Heading 6</h6>
            <p>
              This is a paragraph with <strong>bold text</strong>, <em>italic text</em>, and <strong><em>bold italic text</em></strong>.
            </p>
            <p>
              Here is some <code>inline code</code> within a paragraph.
            </p>
            <blockquote>
              <p>
                This is a blockquote.
                It can span multiple lines.
              </p>
            </blockquote>
            <hr><hr/>
            <p>
              Here is an unordered list:
            </p>
            <ul>
              <li>First item</li>
              <li>Second item</li>
              <li>Third item with <strong>bold</strong></li>
            </ul>
            <p>
              And an ordered list:
            </p>
            <ol>
              <li>First numbered item</li>
              <li>Second numbered item</li>
              <li>Third numbered item</li>
            </ol>
            <p>
              Here is a link: <a href="https://example.com">Example</a>
            </p>
            <p>
              And an image: <img src="https://example.com/image.png" alt="Alt text" />
            </p>
            <p>
              A code block with language specification:
            </p>
            <pre class="code lang-kotlin">
            fun main() {
                println("Hello, World!")
            }
            </pre>
            <p>
              A code block without language:
            </p>
            <pre class="code">
              plain text code block
            </pre>
            <table>
              <thead>
                <tr>
                  <th>Column 1</th>
                  <th>Column 2</th>
                  <th>Column 3</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>Cell 1</td>
                  <td>Cell 2</td>
                  <td>Cell 3</td>
                </tr>
                <tr>
                  <td>Cell 4</td>
                  <td>Cell 5</td>
                  <td>Cell 6</td>
                </tr>
              </tbody>
            </table>
            <ul>
              <li><input type="checkbox" />Unchecked task</li>
              <li><input type="checkbox" checked />Checked task</li>
            </ul>
            <p>
              Here is a footnote reference<sup><a href="#fn1">1</a></sup>.
            </p>
            <footnote id="fn1">
              This is the footnote content.
            </footnote>
            <custom:element attr1="value1" attr2="value2">
            Custom element content with <strong>markdown</strong> inside.
            </custom:element>
            <another:tag purpose="testing">
            More custom content.
            </another:tag>
        """.trimIndent()
    }

    // Nested structures

    @Test
    fun `should parse nested unordered list`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            - Item 1
              - Nested item 1.1
              - Nested item 1.2
            - Item 2
              - Nested item 2.1
                - Deeply nested item
            - Item 3
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <ul>
              <li>Item 1
                <ul>
                  <li>Nested item 1.1</li>
                  <li>Nested item 1.2</li>
                </ul>
              </li>
              <li>Item 2
                <ul>
                  <li>Nested item 2.1
                    <ul>
                      <li>Deeply nested item</li>
                    </ul>
                  </li>
                </ul>
              </li>
              <li>Item 3</li>
            </ul>
        """.trimIndent()
    }

    @Test
    fun `should parse nested ordered list`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            1. First item
               1. Sub-item 1.1
               2. Sub-item 1.2
            2. Second item
               1. Sub-item 2.1
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <ol>
              <li>First item
                <ol>
                  <li>Sub-item 1.1</li>
                  <li>Sub-item 1.2</li>
                </ol>
              </li>
              <li>Second item
                <ol>
                  <li>Sub-item 2.1</li>
                </ol>
              </li>
            </ol>
        """.trimIndent()
    }

    @Test
    fun `should parse mixed nested lists`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            1. Ordered item
               - Unordered nested
               - Another unordered
            2. Another ordered
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <ol>
              <li>Ordered item
                <ul>
                  <li>Unordered nested</li>
                  <li>Another unordered</li>
                </ul>
              </li>
              <li>Another ordered</li>
            </ol>
        """.trimIndent()
    }

    @Test
    fun `should parse nested blockquotes`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            > Outer quote
            > > Inner quote
            > > More inner content
            > Back to outer
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <blockquote>
              <p>
                Outer quote
              </p>
              <blockquote>
                <p>
                  Inner quote
                  More inner content
                </p>
              </blockquote>
              <p>
                Back to outer
              </p>
            </blockquote>
        """.trimIndent()
    }

    @Test
    fun `should parse blockquote with list inside`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            > Here are the points:
            > - First point
            > - Second point
            > - Third point
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <blockquote>
              <p>
                Here are the points:
              </p>
              <ul>
                <li>First point</li>
                <li>Second point</li>
                <li>Third point</li>
              </ul>
            </blockquote>
        """.trimIndent()
    }

    // Inline formatting edge cases

    @Test
    fun `should parse mixed bold and italic in same text`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            This has **bold then *italic inside* bold** text.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              This has <strong>bold then <em>italic inside</em> bold</strong> text.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse adjacent inline elements without space`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            **bold***italic*`code`
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              <strong>bold</strong><em>italic</em><code>code</code>
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse underscore-style emphasis`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            This has _italic_ and __bold__ and ___bold italic___ text.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              This has <em>italic</em> and <strong>bold</strong> and <strong><em>bold italic</em></strong> text.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse strikethrough text`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            This has ~~strikethrough~~ text.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              This has <del>strikethrough</del> text.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse strikethrough with other formatting`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            This is ~~deleted **bold** text~~ here.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              This is <del>deleted <strong>bold</strong> text</del> here.
            </p>
        """.trimIndent()
    }

    // HTML escaping

    @Test
    fun `should escape HTML special characters in text`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            Use <div> elements and & ampersands and "quotes" carefully.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              Use &lt;div&gt; elements and &amp; ampersands and "quotes" carefully.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should escape HTML in code blocks`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            ```html
            <div class="test">
              <p>Hello & goodbye</p>
            </div>
            ```
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <pre class="code lang-html">
            &lt;div class="test"&gt;
              &lt;p&gt;Hello &amp; goodbye&lt;/p&gt;
            &lt;/div&gt;
            </pre>
        """.trimIndent()
    }

    @Test
    fun `should escape HTML in inline code`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            Use `<script>alert("XSS")</script>` carefully.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              Use <code>&lt;script&gt;alert("XSS")&lt;/script&gt;</code> carefully.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle less than and greater than comparisons`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            Check if a < b and c > d or x <= y and z >= w.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              Check if a &lt; b and c &gt; d or x &lt;= y and z &gt;= w.
            </p>
        """.trimIndent()
    }

    // Edge cases

    @Test
    fun `should parse empty paragraph`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            # Header



            Content after empty line.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <h1>Header</h1>
            <p>
              Content after empty line.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse unicode content`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            # 你好世界

            This has émojis 🎉 and Ümlauts and 日本語 text.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <h1>你好世界</h1>
            <p>
              This has émojis 🎉 and Ümlauts and 日本語 text.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse link with title`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            Check out [Example](https://example.com "Example Site") for more.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              Check out <a href="https://example.com" title="Example Site">Example</a> for more.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse autolinks`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            Visit <https://example.com> or email <user@example.com>.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              Visit <a href="https://example.com">https://example.com</a> or email <a href="mailto:user@example.com">user@example.com</a>.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse line breaks with two trailing spaces`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = "Line one  \nLine two  \nLine three".flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              Line one<br />
              Line two<br />
              Line three
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse line breaks with backslash`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            Line one\
            Line two\
            Line three
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              Line one<br />
              Line two<br />
              Line three
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse escaped special characters`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            Use \*asterisks\* and \`backticks\` and \[brackets\] literally.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              Use *asterisks* and `backticks` and [brackets] literally.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse inline code with backticks inside`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            Use `` `backticks` `` inside code.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              Use <code>`backticks`</code> inside code.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse multiple paragraphs with blank lines`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            First paragraph with some content.

            Second paragraph with more content.

            Third paragraph to finish.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              First paragraph with some content.
            </p>
            <p>
              Second paragraph with more content.
            </p>
            <p>
              Third paragraph to finish.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse setext-style headers`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            Header 1
            ========

            Header 2
            --------

            Some content.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <h1>Header 1</h1>
            <h2>Header 2</h2>
            <p>
              Some content.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse reference-style links`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            Check out [Example][ex] and [Another][another] links.

            [ex]: https://example.com
            [another]: https://another.com "Another Site"
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              Check out <a href="https://example.com">Example</a> and <a href="https://another.com" title="Another Site">Another</a> links.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse reference-style images`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            Here is an image: ![Alt text][img]

            [img]: https://example.com/image.png "Image Title"
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              Here is an image: <img src="https://example.com/image.png" alt="Alt text" title="Image Title" />
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle indented code block`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            Some text:

                function hello() {
                    console.log("Hello");
                }

            More text.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              Some text:
            </p>
            <pre class="code">
            function hello() {
                console.log("Hello");
            }
            </pre>
            <p>
              More text.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse definition list`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            Term 1
            : Definition for term 1

            Term 2
            : Definition for term 2
            : Another definition for term 2
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <dl>
              <dt>Term 1</dt>
              <dd>Definition for term 1</dd>
              <dt>Term 2</dt>
              <dd>Definition for term 2</dd>
              <dd>Another definition for term 2</dd>
            </dl>
        """.trimIndent()
    }

    @Test
    fun `should parse abbreviation definitions`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            The HTML specification is maintained by the W3C.

            *[HTML]: Hyper Text Markup Language
            *[W3C]: World Wide Web Consortium
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              The <abbr title="Hyper Text Markup Language">HTML</abbr> specification is maintained by the <abbr title="World Wide Web Consortium">W3C</abbr>.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse superscript and subscript`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            Water is H~2~O and E=mc^2^ is famous.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              Water is H<sub>2</sub>O and E=mc<sup>2</sup> is famous.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse highlight or mark text`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            This is ==highlighted== text.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              This is <mark>highlighted</mark> text.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse inline math`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            The equation ${'$'}E = mc^2${'$'} is famous.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              The equation <math>E = mc^2</math> is famous.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse display math block`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = $$"""
            Here is an equation:
            
            $$
            \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}
            $$
            
            This is the quadratic formula.
        """.trimIndent().flowRandomLengthChunks()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.toHtml() sameAs """
            <p>
              Here is an equation:
            </p>
            <math display="block">
            \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}
            </math>
            <p>
              This is the quadratic formula.
            </p>
        """.trimIndent()
    }

}
