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

package com.xemantic.markanywhere.parse.gfm

import com.xemantic.kotlin.core.text.buildText
import com.xemantic.kotlin.test.text.chunkedRandomly
import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.parse.parse
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Tests for GFM Section 04.05 — Fenced code blocks.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#fenced-code-blocks
 */
@Suppress("ClassName")
class Gfm_04_05_Test {

    // TODO review
    @Test
    fun `example 89 - indented code block`() = runTest {
        // given
        val textFlow = buildText {
            +"```\n"
            +"<\n"
            +" >\n"
            +"```\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre"("class" to "code") {
                +"<\n >"
            }
        }
        // GFM expected:
        /*
            <pre><code>&lt;
             &gt;
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 90 - indented code block`() = runTest {
        // given
        val textFlow = buildText {
            +"~~~\n"
            +"<\n"
            +" >\n"
            +"~~~\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre"("class" to "code") {
                +"<\n >"
            }
        }
        // GFM expected:
        /*
            <pre><code>&lt;
             &gt;
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 91 - paragraph foo`() = runTest {
        // given
        val textFlow = """
            ``
            foo
            ``
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><code>foo</code></p>
         */
    }

    // TODO review
    @Test
    fun `example 92 - indented code block`() = runTest {
        // given
        val textFlow = """
            ```
            aaa
            ~~~
            ```
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre"("class" to "code") {
                +"aaa\n~~~"
            }
        }
        // GFM expected:
        /*
            <pre><code>aaa
            ~~~
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 93 - indented code block`() = runTest {
        // given
        val textFlow = """
            ~~~
            aaa
            ```
            ~~~
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre"("class" to "code") {
                +"aaa\n```"
            }
        }
        // GFM expected:
        /*
            <pre><code>aaa
            ```
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 94 - indented code block`() = runTest {
        // given
        val textFlow = """
            ````
            aaa
            ```
            ``````
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre"("class" to "code") {
                +"aaa\n```"
            }
        }
        // GFM expected:
        /*
            <pre><code>aaa
            ```
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 95 - indented code block`() = runTest {
        // given
        val textFlow = """
            ~~~~
            aaa
            ~~~
            ~~~~
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre"("class" to "code") {
                +"aaa\n~~~"
            }
        }
        // GFM expected:
        /*
            <pre><code>aaa
            ~~~
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 96 - indented code block`() = runTest {
        // given
        val textFlow = "```".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre"("class" to "code") {
                +""
            }
        }
        // GFM expected:
        /*
            <pre><code></code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 97 - indented code block`() = runTest {
        // given
        val textFlow = """
            `````

            ```
            aaa
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre"("class" to "code") {
                +"\n```\naaa"
            }
        }
        // GFM expected:
        /*
            <pre><code>
            ```
            aaa
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 98 - blockquote (text , indented code block, text ), paragraph bbb`() = runTest {
        // given
        val textFlow = """
            > ```
            > aaa

            bbb
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "pre" {
                    "code" {
                        +"aaa\n"
                    }
                }
            }
            "p" {
                +"bbb"
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <pre><code>aaa
            </code></pre>
            </blockquote>
            <p>bbb</p>
         */
    }

    // TODO review
    @Test
    fun `example 99 - indented code block`() = runTest {
        // given
        val textFlow = buildText {
            +"```\n"
            +"\n"
            +"  \n"
            +"```\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre"("class" to "code") {
                +"\n  "
            }
        }
        // GFM expected:
        /*
            <pre><code>
              
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 100 - indented code block`() = runTest {
        // given
        val textFlow = """
            ```
            ```
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre"("class" to "code") {
                +""
            }
        }
        // GFM expected:
        /*
            <pre><code></code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 101 - indented code block`() = runTest {
        // given
        val textFlow = buildText {
            +" ```\n"
            +" aaa\n"
            +"aaa\n"
            +"```\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre"("class" to "code") {
                +"aaa\naaa"
            }
        }
        // GFM expected:
        /*
            <pre><code>aaa
            aaa
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 102 - indented code block`() = runTest {
        // given
        val textFlow = buildText {
            +"  ```\n"
            +"aaa\n"
            +"  aaa\n"
            +"aaa\n"
            +"  ```\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre"("class" to "code") {
                +"aaa\naaa\naaa"
            }
        }
        // GFM expected:
        /*
            <pre><code>aaa
            aaa
            aaa
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 103 - indented code block`() = runTest {
        // given
        val textFlow = buildText {
            +"   ```\n"
            +"   aaa\n"
            +"    aaa\n"
            +"  aaa\n"
            +"   ```\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre"("class" to "code") {
                +"aaa\n aaa\naaa"
            }
        }
        // GFM expected:
        /*
            <pre><code>aaa
             aaa
            aaa
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 104 - indented code block`() = runTest {
        // given
        val textFlow = buildText {
            +"    ```\n"
            +"    aaa\n"
            +"    ```\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre"("class" to "code") {
                +"```\naaa\n```"
            }
        }
        // GFM expected:
        /*
            <pre><code>```
            aaa
            ```
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 105 - indented code block`() = runTest {
        // given
        val textFlow = buildText {
            +"```\n"
            +"aaa\n"
            +"  ```\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre"("class" to "code") {
                +"aaa"
            }
        }
        // GFM expected:
        /*
            <pre><code>aaa
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 106 - indented code block`() = runTest {
        // given
        val textFlow = buildText {
            +"   ```\n"
            +"aaa\n"
            +"  ```\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre"("class" to "code") {
                +"aaa"
            }
        }
        // GFM expected:
        /*
            <pre><code>aaa
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 107 - indented code block`() = runTest {
        // given
        val textFlow = buildText {
            +"```\n"
            +"aaa\n"
            +"    ```\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre"("class" to "code") {
                +"aaa\n    ```"
            }
        }
        // GFM expected:
        /*
            <pre><code>aaa
                ```
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 108 - paragraph aaa`() = runTest {
        // given
        val textFlow = """
            ``` ```
            aaa
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +" "
                }
                +"\naaa"
            }
        }
        // GFM expected:
        /*
            <p><code> </code>
            aaa</p>
         */
    }

    // TODO review
    @Test
    fun `example 109 - indented code block`() = runTest {
        // given
        val textFlow = """
            ~~~~~~
            aaa
            ~~~ ~~
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre"("class" to "code") {
                +"aaa\n~~~ ~~"
            }
        }
        // GFM expected:
        /*
            <pre><code>aaa
            ~~~ ~~
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 110 - paragraph foo, indented code block, paragraph baz`() = runTest {
        // given
        val textFlow = """
            foo
            ```
            bar
            ```
            baz
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo"
            }
            "pre" {
                "code" {
                    +"bar\n"
                }
            }
            "p" {
                +"baz"
            }
        }
        // GFM expected:
        /*
            <p>foo</p>
            <pre><code>bar
            </code></pre>
            <p>baz</p>
         */
    }

    // TODO review
    @Test
    fun `example 111 - h2 foo, indented code block, h1 baz`() = runTest {
        // given
        val textFlow = """
            foo
            ---
            ~~~
            bar
            ~~~
            # baz
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h2" {
                +"foo"
            }
            "pre" {
                "code" {
                    +"bar\n"
                }
            }
            "h1" {
                +"baz"
            }
        }
        // GFM expected:
        /*
            <h2>foo</h2>
            <pre><code>bar
            </code></pre>
            <h1>baz</h1>
         */
    }

    // TODO review
    @Test
    fun `example 112 - fenced code`() = runTest {
        // given
        val textFlow = buildText {
            +"```ruby\n"
            +"def foo(x)\n"
            +"  return 3\n"
            +"end\n"
            +"```\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre"("class" to "code lang-ruby") {
                +"def foo(x)\n  return 3\nend"
            }
        }
        // GFM expected:
        /*
            <pre><code class="language-ruby">def foo(x)
              return 3
            end
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 113 - fenced code`() = runTest {
        // given
        val textFlow = buildText {
            +"~~~~    ruby startline=3 \$%@#\$\n"
            +"def foo(x)\n"
            +"  return 3\n"
            +"end\n"
            +"~~~~~~~\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre"("class" to "code lang-ruby") {
                +"def foo(x)\n  return 3\nend"
            }
        }
        // GFM expected:
        /*
            <pre><code class="language-ruby">def foo(x)
              return 3
            end
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 114 - fenced code`() = runTest {
        // given
        val textFlow = """
            ````;
            ````
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre"("class" to "code lang-;") {
                +""
            }
        }
        // GFM expected:
        /*
            <pre><code class="language-;"></code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 115 - paragraph aa foo`() = runTest {
        // given
        val textFlow = """
            ``` aa ```
            foo
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"aa"
                }
                +"\nfoo"
            }
        }
        // GFM expected:
        /*
            <p><code>aa</code>
            foo</p>
         */
    }

    // TODO review
    @Test
    fun `example 116 - fenced code`() = runTest {
        // given
        val textFlow = """
            ~~~ aa ``` ~~~
            foo
            ~~~
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre"("class" to "code lang-aa") {
                +"foo"
            }
        }
        // GFM expected:
        /*
            <pre><code class="language-aa">foo
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 117 - indented code block`() = runTest {
        // given
        val textFlow = """
            ```
            ``` aaa
            ```
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre"("class" to "code") {
                +"``` aaa"
            }
        }
        // GFM expected:
        /*
            <pre><code>``` aaa
            </code></pre>
         */
    }

}
