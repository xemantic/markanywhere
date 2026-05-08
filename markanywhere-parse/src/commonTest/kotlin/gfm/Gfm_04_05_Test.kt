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

    @Test
    fun `example 89 - fenced code block`() = runTest {
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
            "pre" {
                "code" {
                    +"<\n >\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>&lt;
             &gt;
            </code></pre>
         */
    }

    @Test
    fun `example 90 - fenced code block with tildes`() = runTest {
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
            "pre" {
                "code" {
                    +"<\n >\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>&lt;
             &gt;
            </code></pre>
         */
    }

    // GFM expects a multi-line inline code span (`` ` `` / `` `` `` runs span line
    // breaks). The parser intentionally closes inline state at line boundaries to keep
    // streaming append-only, so a `` `` ``-led line followed by content on the next line
    // emits the backticks as literal text rather than opening a span that closes later.
    @Test
    fun `example 91 - DIVERGENCE - paragraph with literal backticks`() = runTest {
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
                +"``\nfoo\n``"
            }
        }
        // GFM expected:
        /*
            <p><code>foo</code></p>
         */
    }

    @Test
    fun `example 92 - fenced code block tilde-only inside backtick fence`() = runTest {
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
            "pre" {
                "code" {
                    +"aaa\n~~~\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>aaa
            ~~~
            </code></pre>
         */
    }

    @Test
    fun `example 93 - fenced code block backticks-only inside tilde fence`() = runTest {
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
            "pre" {
                "code" {
                    +"aaa\n```\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>aaa
            ```
            </code></pre>
         */
    }

    @Test
    fun `example 94 - fenced code block longer fence allows shorter inside`() = runTest {
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
            "pre" {
                "code" {
                    +"aaa\n```\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>aaa
            ```
            </code></pre>
         */
    }

    @Test
    fun `example 95 - fenced code block longer tilde fence allows shorter inside`() = runTest {
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
            "pre" {
                "code" {
                    +"aaa\n~~~\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>aaa
            ~~~
            </code></pre>
         */
    }

    @Test
    fun `example 96 - unclosed fenced code block empty`() = runTest {
        // given
        val textFlow = "```".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {}
            }
        }
        // GFM expected:
        /*
            <pre><code></code></pre>
         */
    }

    @Test
    fun `example 97 - unclosed fenced code block with content`() = runTest {
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
            "pre" {
                "code" {
                    +"\n```\naaa\n"
                }
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

    @Test
    fun `example 98 - blockquote with fenced code then paragraph bbb`() = runTest {
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

    @Test
    fun `example 99 - fenced code block with blank lines preserved`() = runTest {
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
            "pre" {
                "code" {
                    +"\n  \n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>
              
            </code></pre>
         */
    }

    @Test
    fun `example 100 - empty fenced code block`() = runTest {
        // given
        val textFlow = """
            ```
            ```
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {}
            }
        }
        // GFM expected:
        /*
            <pre><code></code></pre>
         */
    }

    @Test
    fun `example 101 - fenced code block 1-space indented opening fence`() = runTest {
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
            "pre" {
                "code" {
                    +"aaa\naaa\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>aaa
            aaa
            </code></pre>
         */
    }

    @Test
    fun `example 102 - fenced code block 2-space indented opening fence`() = runTest {
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
            "pre" {
                "code" {
                    +"aaa\naaa\naaa\n"
                }
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

    @Test
    fun `example 103 - fenced code block 3-space indented opening fence`() = runTest {
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
            "pre" {
                "code" {
                    +"aaa\n aaa\naaa\n"
                }
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

    @Test
    fun `example 104 - 4-space indent makes indented code block not fence`() = runTest {
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
            "pre" {
                "code" {
                    +"```\naaa\n```\n"
                }
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

    @Test
    fun `example 105 - fenced code block 2-space indented closing fence`() = runTest {
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
            "pre" {
                "code" {
                    +"aaa\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>aaa
            </code></pre>
         */
    }

    @Test
    fun `example 106 - fenced code block mixed-indent open and close`() = runTest {
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
            "pre" {
                "code" {
                    +"aaa\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>aaa
            </code></pre>
         */
    }

    @Test
    fun `example 107 - fenced code block 4-space indented closing fence is content`() = runTest {
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
            "pre" {
                "code" {
                    +"aaa\n    ```\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>aaa
                ```
            </code></pre>
         */
    }

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

    @Test
    fun `example 109 - unclosed tilde fence internal tildes do not close`() = runTest {
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
            "pre" {
                "code" {
                    +"aaa\n~~~ ~~\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>aaa
            ~~~ ~~
            </code></pre>
         */
    }

    @Test
    fun `example 110 - paragraph foo fenced code block paragraph baz`() = runTest {
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

    // GFM expects the `foo\n---` pair to form a setext heading (`<h2>foo</h2>`).
    // The parser does not implement setext headings, so it emits `foo` as a paragraph
    // and `---` as a thematic break before reaching the fenced code block and the
    // ATX `# baz` heading.
    @Test
    fun `example 111 - DIVERGENCE - paragraph foo hr fenced code h1 baz`() = runTest {
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
            "p" {
                +"foo"
            }
            "hr" {}
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

    @Test
    fun `example 112 - fenced code block with language ruby`() = runTest {
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
            "pre" {
                "code"("class" to "language-ruby") {
                    +"def foo(x)\n  return 3\nend\n"
                }
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

    @Test
    fun `example 113 - tilde fence with language and extra info string`() = runTest {
        // given
        val textFlow = buildText {
            +"~~~~    ruby startline=3 $%@#$\n"
            +"def foo(x)\n"
            +"  return 3\n"
            +"end\n"
            +"~~~~~~~\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code"("class" to "language-ruby") {
                    +"def foo(x)\n  return 3\nend\n"
                }
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

    @Test
    fun `example 114 - fenced code block info string can be punctuation`() = runTest {
        // given
        val textFlow = """
            ````;
            ````
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code"("class" to "language-;") {}
            }
        }
        // GFM expected:
        /*
            <pre><code class="language-;"></code></pre>
         */
    }

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

    @Test
    fun `example 116 - tilde fence info string may contain backticks`() = runTest {
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
            "pre" {
                "code"("class" to "language-aa") {
                    +"foo\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code class="language-aa">foo
            </code></pre>
         */
    }

    @Test
    fun `example 117 - backtick info string forbids backticks - opens new fence`() = runTest {
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
            "pre" {
                "code" {
                    +"``` aaa\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>``` aaa
            </code></pre>
         */
    }

}
