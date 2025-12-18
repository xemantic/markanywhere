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

import com.xemantic.kotlin.core.text.lineFlow
import com.xemantic.kotlin.test.assert
import com.xemantic.kotlin.test.sameAs
import com.xemantic.kotlin.test.text.chunkedRandomly
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.render.render
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test

class MarkanywhereParserTest {

    @Test
    fun `should emit SemanticEvents incrementally without buffering`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val input = MutableSharedFlow<String>()
        val eventBuffer = mutableListOf<SemanticEvent>()

        suspend fun assertEmissions(vararg events: SemanticEvent) {
            yield()
            assert(eventBuffer == events.toList())
            eventBuffer.clear()
        }

        // Start collecting events
        val collectJob = launch {
            input.parse(parser).collect { event ->
                eventBuffer += event
            }
        }
        yield() // let the collector start

        // when: send '#' - parser cannot yet determine heading level
        input.emit("#")
        // then: no event emitted yet
        assertEmissions() // empty

        // when: send ' ' - now parser knows it's h1
        input.emit(" ")
        // then: Mark(h1) should be emitted
        assertEmissions(SemanticEvent.Mark("h1"))

        // when: send header text character by character
        input.emit("H")
        // then
        assertEmissions(SemanticEvent.Text("H"))
        // when
        input.emit("i")
        // then
        assertEmissions(SemanticEvent.Text("i"))

        // when: send newline - header ends
        input.emit("\n")
        // then: h1 should be closed
        assertEmissions(SemanticEvent.Unmark("h1"))

        // when: send paragraph text
        input.emit("T")
        // then: paragraph should open first
        assertEmissions(
            SemanticEvent.Mark("p"),
            SemanticEvent.Text("T")
        )

        // when: complete input
        collectJob.cancel()
    }

    @Test
    fun `should parse simple Hello World markdown`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            # Hello World

            This is a simple paragraph.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <h1>
              Hello World
            </h1>
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
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <h1>
              Hello World
            </h1>
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
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <h1>
              Shopping List
            </h1>
            <ul>
              <li>
                Apples
              </li>
              <li>
                Bananas
              </li>
              <li>
                Oranges
              </li>
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
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <p>
              Here are the items:
            </p>
            <ul>
              <li>
                First item
              </li>
              <li>
                Second item
              </li>
              <li>
                Third item
              </li>
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
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <p>
              Follow these steps:
            </p>
            <ol>
              <li>
                First step
              </li>
              <li>
                Second step
              </li>
              <li>
                Third step
              </li>
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
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
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
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <h1>
              Main Title
            </h1>
            <h2>
              Subtitle
            </h2>
            <h3>
              Section
            </h3>
            <p>
              Content here.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse blockquote after paragraph`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            As someone once said:
            > This is a famous quote.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <p>
              As someone once said:
            </p>
            <blockquote>
              <p>
                This is a famous quote.
              </p>
            </blockquote>
        """.trimIndent()
    }

    @Test
    fun `should parse multi-line blockquote`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            > This is a famous quote.
            > It spans multiple lines.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <blockquote>
              <p>
                This is a famous quote.
                It spans multiple lines.
              </p>
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
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
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
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
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
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <p>
              This has <em>italic</em> and <strong>bold</strong> and <strong><em>bold italic</em></strong> text.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse asterisk-style bold italic`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            This has ***bold italic*** text.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <p>
              This has <strong><em>bold italic</em></strong> text.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse strikethrough text`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            This has ~~strikethrough~~ text.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
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
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
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
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
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
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
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
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
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
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
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
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <h1>
              Header
            </h1>
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
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <h1>
              你好世界
            </h1>
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
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
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
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <p>
              Visit <a href="https://example.com">https://example.com</a> or email <a href="mailto:user@example.com">user@example.com</a>.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse escaped special characters`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            Use \*asterisks\* and \`backticks\` and \[brackets\] literally.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
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
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
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
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
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
    fun `should parse superscript and subscript`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            Water is H~2~O and E=mc^2^ is famous.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
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
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
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
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
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
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <p>
              Here is an equation:
            </p>
            <math display="block">\frac{-b \pm \sqrt{b^2 - 4ac}}{2a}</math>
            <p>
              This is the quadratic formula.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse horizontal rule`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            Some content.

            ---

            More content.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <p>
              Some content.
            </p>
            <hr></hr>
            <p>
              More content.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse table`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            | Column 1 | Column 2 |
            |----------|----------|
            | Cell 1   | Cell 2   |
            | Cell 3   | Cell 4   |
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <table>
              <thead>
                <tr>
                  <th>
                    Column 1
                  </th>
                  <th>
                    Column 2
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
                <tr>
                  <td>
                    Cell 3
                  </td>
                  <td>
                    Cell 4
                  </td>
                </tr>
              </tbody>
            </table>
        """.trimIndent()
    }

    @Test
    fun `should parse task list`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            - [ ] Unchecked task
            - [x] Checked task
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
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

    @Test
    fun `should parse inline link`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            Here is a link: [Example](https://example.com)
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <p>
              Here is a link: <a href="https://example.com">Example</a>
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse inline image`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            And an image: ![Alt text](https://example.com/image.png)
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <p>
              And an image: <img src="https://example.com/image.png" alt="Alt text"></img>
            </p>
        """.trimIndent()
    }

    @Test
    fun `should parse code block without language`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            ```
            plain text code block
            ```
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <pre class="code">
            plain text code block
            </pre>
        """.trimIndent()
    }

    @Test
    fun `should parse all heading levels`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            # Heading 1
            ## Heading 2
            ### Heading 3
            #### Heading 4
            ##### Heading 5
            ###### Heading 6
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <h1>
              Heading 1
            </h1>
            <h2>
              Heading 2
            </h2>
            <h3>
              Heading 3
            </h3>
            <h4>
              Heading 4
            </h4>
            <h5>
              Heading 5
            </h5>
            <h6>
              Heading 6
            </h6>
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
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <blockquote>
              <p>
                Here are the points:
              </p>
              <ul>
                <li>
                  First point
                </li>
                <li>
                  Second point
                </li>
                <li>
                  Third point
                </li>
              </ul>
            </blockquote>
        """.trimIndent()
    }

    @Test
    fun `should parse custom markup in markdown`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            # Hello World
            
            <foo:bar buzz="42">
            println("Hello World")
            </foo:bar>
            
            Another paragraph.
        """.trimIndent().lineFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed sameAs semanticEvents {
            "h1" {
                +"Hello World"
            }
            tag("foo:bar", "buzz" to "42") {
                +"""println("Hello World")"""
            }
            "p" {
                +"A"
                +"nother paragraph."
            }
        }
    }

    // Edge case tests

    // Note: The parser currently does not support nested lists via indentation.
    // Indented list items after a parent item are treated as regular paragraphs.
    // These tests document the current behavior.

    @Test
    fun `should treat indented list items as paragraphs - no nested list support`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            - Item 1
              - Nested item 1.1
              - Nested item 1.2
            - Item 2
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        // Note: indented items are treated as paragraphs, not nested lists
        parsed.render() sameAs """
            <ul>
              <li>
                Item 1
              </li>
            </ul>
            <p>
                - Nested item 1.1
            </p>
            <p>
                - Nested item 1.2
            </p>
            <ul>
              <li>
                Item 2
              </li>
            </ul>
        """.trimIndent()
    }

    @Test
    fun `should treat indented ordered list items as paragraphs - no nested list support`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            1. First item
               1. Nested first
               2. Nested second
            2. Second item
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        // Note: indented items are treated as paragraphs, not nested lists
        // The indentation is preserved from the original input
        parsed.render() sameAs """
            <ol>
              <li>
                First item
              </li>
            </ol>
            <p>
                 1. Nested first
            </p>
            <p>
                 2. Nested second
            </p>
            <ol>
              <li>
                Second item
              </li>
            </ol>
        """.trimIndent()
    }

    @Test
    fun `should treat mixed indented list items as paragraphs - no nested list support`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            - Unordered item
              1. Ordered nested
              2. Another ordered
            - Another unordered
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        // Note: indented items are treated as paragraphs, not nested lists
        // The indentation is preserved from the original input
        parsed.render() sameAs """
            <ul>
              <li>
                Unordered item
              </li>
            </ul>
            <p>
                1. Ordered nested
            </p>
            <p>
                2. Another ordered
            </p>
            <ul>
              <li>
                Another unordered
              </li>
            </ul>
        """.trimIndent()
    }

    @Test
    fun `should handle empty bold markers as literal text`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            Text with **** empty bold markers.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <p>
              Text with **** empty bold markers.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle empty underscore markers as literal text`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            Text with ____ empty underscore markers.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <p>
              Text with ____ empty underscore markers.
            </p>
        """.trimIndent()
    }

    // Note: The parser auto-closes unclosed inline formatting markers at the end of the paragraph.

    @Test
    fun `should auto-close unclosed bold marker at paragraph end`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            This has **unclosed bold text.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        // Note: parser auto-closes unclosed bold at paragraph end
        parsed.render() sameAs """
            <p>
              This has <strong>unclosed bold text.</strong>
            </p>
        """.trimIndent()
    }

    @Test
    fun `should auto-close unclosed italic marker at paragraph end`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            This has *unclosed italic text.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        // Note: parser auto-closes unclosed italic at paragraph end
        parsed.render() sameAs """
            <p>
              This has <em>unclosed italic text.</em>
            </p>
        """.trimIndent()
    }

    @Test
    fun `should auto-close unclosed inline code marker at paragraph end`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            This has `unclosed code text.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        // Note: parser auto-closes unclosed inline code at paragraph end
        parsed.render() sameAs """
            <p>
              This has <code>unclosed code text.</code>
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle hash without space as regular text`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            #hashtag is not a header
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <p>
              #hashtag is not a header
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle multiple hashes without space as separate paragraphs`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            ##not a header
            ###also not a header
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        // Note: each line starting with hashes (no space after) becomes a separate paragraph
        parsed.render() sameAs """
            <p>
              ##not a header
            </p>
            <p>
              ###also not a header
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle lines with only hashes as regular paragraphs`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            #
            ##
            Content after hash lines.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        // Note: single hash without space after is treated as a paragraph, not a header
        parsed.render() sameAs """
            <p>
              #
            </p>
            <p>
              ##
            </p>
            <p>
              Content after hash lines.
            </p>
        """.trimIndent()
    }

    @Test
    fun `should handle very long single line content`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val longText = "A".repeat(10000)
        val textFlow = """
            # Header

            $longText
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed.render() sameAs """
            <h1>
              Header
            </h1>
            <p>
              $longText
            </p>
        """.trimIndent()
    }

    @Test
    fun `should treat deeply indented list items as paragraphs - no nested list support`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = """
            - Level 1
              - Level 2
                - Level 3
                  - Level 4
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        // Note: all indented items become paragraphs, not nested lists
        parsed.render() sameAs """
            <ul>
              <li>
                Level 1
              </li>
            </ul>
            <p>
                - Level 2
            </p>
            <p>
                  - Level 3
            </p>
            <p>
                    - Level 4
            </p>
        """.trimIndent()
    }

    // incremental parsing

    @Test
    fun `should parse incrementally`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val textFlow = listOf(
            "# Hello ",
            "World\n",
            "\n",
            "<foo:bar buzz=\"42\">",
            "println(\"Hello ",
            "World\")",
            "</foo:bar>\n",
            "\n",
            "Another paragraph."
        ).asFlow()

        // when
        val parsed = textFlow.parse(parser)

        // then
        parsed sameAs semanticEvents {
            "h1" {
                +"Hello "
                +"World"
            }
            tag("foo:bar", "buzz" to "42") {
                +"println(\"Hello "
                +"World\")"
            }
            "p" {
                +"A"
                +"nother paragraph."
            }
        }
    }

}
