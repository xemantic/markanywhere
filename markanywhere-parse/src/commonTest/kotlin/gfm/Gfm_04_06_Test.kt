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
 * Tests for GFM Section 04.06 — HTML blocks.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#html-blocks
 */
@Suppress("ClassName")
class Gfm_04_06_Test {

    // TODO review
    @Test
    fun `example 118 - table`() = runTest {
        // given
        val textFlow = """
            <table><tr><td>
            <pre>
            **Hello**,

            _world_.
            </pre>
            </td></tr></table>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "table" {
                "tr" {
                    "td" {
                        "pre" {
                            +"\n**Hello**,\n"
                            "p" {
                                "em" {
                                    +"world"
                                }
                                +".\n"
                            }
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <table><tr><td>
            <pre>
            **Hello**,
            <p><em>world</em>.
            </pre></p>
            </td></tr></table>
         */
    }

    // TODO review
    @Test
    fun `example 119 - table, paragraph okay`() = runTest {
        // given
        val textFlow = buildText {
            +"<table>\n"
            +"  <tr>\n"
            +"    <td>\n"
            +"           hi\n"
            +"    </td>\n"
            +"  </tr>\n"
            +"</table>\n"
            +"\n"
            +"okay.\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "table" {
                "tr" {
                    "td" {
                        +"\n           hi\n    "
                    }
                }
            }
            "p" {
                +"okay."
            }
        }
        // GFM expected:
        /*
            <table>
              <tr>
                <td>
                       hi
                </td>
              </tr>
            </table>
            <p>okay.</p>
         */
    }

    // TODO review
    @Test
    fun `example 120 - div hello`() = runTest {
        // given
        val textFlow = buildText {
            +" <div>\n"
            +"  *hello*\n"
            +"         <foo><a>\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "div" {
                +"\n  *hello*\n         "
                "foo" {
                    "a" {
                        +"\n"
                    }
                }
            }
        }
        // GFM expected:
        /*
             <div>
              *hello*
                     <foo><a>
         */
    }

    // TODO review
    @Test
    fun `example 121 - text foo`() = runTest {
        // given
        val textFlow = """
            </div>
            *foo*
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            +"\n*foo*\n"
        }
        // GFM expected:
        /*
            </div>
            *foo*
         */
    }

    // TODO review
    @Test
    fun `example 122 - div Markdown`() = runTest {
        // given
        val textFlow = """
            <DIV CLASS="foo">

            *Markdown*

            </DIV>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "div"("class" to "foo") {
                "p" {
                    "em" {
                        +"Markdown"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <DIV CLASS="foo">
            <p><em>Markdown</em></p>
            </DIV>
         */
    }

    // TODO review
    @Test
    fun `example 123 - div`() = runTest {
        // given
        val textFlow = buildText {
            +"<div id=\"foo\"\n"
            +"  class=\"bar\">\n"
            +"</div>\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "div"("id" to "foo", "class" to "bar") {
            }
        }
        // GFM expected:
        /*
            <div id="foo"
              class="bar">
            </div>
         */
    }

    // TODO review
    @Test
    fun `example 124 - div`() = runTest {
        // given
        val textFlow = buildText {
            +"<div id=\"foo\" class=\"bar\n"
            +"  baz\">\n"
            +"</div>\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "div"("id" to "foo", "class" to "bar\n  baz") {
            }
        }
        // GFM expected:
        /*
            <div id="foo" class="bar
              baz">
            </div>
         */
    }

    // TODO review
    @Test
    fun `example 125 - div foo bar`() = runTest {
        // given
        val textFlow = """
            <div>
            *foo*

            *bar*
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "div" {
                +"\n*foo*\n"
                "p" {
                    "em" {
                        +"bar"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <div>
            *foo*
            <p><em>bar</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 126`() = runTest {
        // given
        val textFlow = """
            <div id="foo"
            *hi*
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            // TODO assertion
        }
        // GFM expected:
        /*
            <div id="foo"
            *hi*
         */
    }

    // TODO review
    @Test
    fun `example 127`() = runTest {
        // given
        val textFlow = """
            <div class
            foo
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            // TODO assertion
        }
        // GFM expected:
        /*
            <div class
            foo
         */
    }

    // TODO review
    @Test
    fun `example 128`() = runTest {
        // given
        val textFlow = """
            <div *???-&&&-<---
            *foo*
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            // TODO assertion
        }
        // GFM expected:
        /*
            <div *???-&&&-<---
            *foo*
         */
    }

    // TODO review
    @Test
    fun `example 129 - div foo`() = runTest {
        // given
        val textFlow = """<div><a href="bar">*foo*</a></div>""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "div" {
                "a"("href" to "bar") {
                    +"*foo*"
                }
            }
        }
        // GFM expected:
        /*
            <div><a href="bar">*foo*</a></div>
         */
    }

    // TODO review
    @Test
    fun `example 130 - table`() = runTest {
        // given
        val textFlow = """
            <table><tr><td>
            foo
            </td></tr></table>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "table" {
                "tr" {
                    "td" {
                        +"\nfoo\n"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <table><tr><td>
            foo
            </td></tr></table>
         */
    }

    // TODO review
    @Test
    fun `example 131 - div, text c int x = 33`() = runTest {
        // given
        val textFlow = """
            <div></div>
            ``` c
            int x = 33;
            ```
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "div" {
            }
            +"\n``` c\nint x = 33;\n```\n"
        }
        // GFM expected:
        /*
            <div></div>
            ``` c
            int x = 33;
            ```
         */
    }

    // TODO review
    @Test
    fun `example 132 - link bar - foo`() = runTest {
        // given
        val textFlow = """
            <a href="foo">
            *bar*
            </a>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "a"("href" to "foo") {
                +"\n*bar*\n"
            }
        }
        // GFM expected:
        /*
            <a href="foo">
            *bar*
            </a>
         */
    }

    // TODO review
    @Test
    fun `example 133 - warning bar`() = runTest {
        // given
        val textFlow = """
            <Warning>
            *bar*
            </Warning>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "warning" {
                +"\n*bar*\n"
            }
        }
        // GFM expected:
        /*
            <Warning>
            *bar*
            </Warning>
         */
    }

    // TODO review
    @Test
    fun `example 134 - i bar`() = runTest {
        // given
        val textFlow = """
            <i class="foo">
            *bar*
            </i>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "i"("class" to "foo") {
                +"\n*bar*\n"
            }
        }
        // GFM expected:
        /*
            <i class="foo">
            *bar*
            </i>
         */
    }

    // TODO review
    @Test
    fun `example 135 - text bar`() = runTest {
        // given
        val textFlow = """
            </ins>
            *bar*
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            +"\n*bar*\n"
        }
        // GFM expected:
        /*
            </ins>
            *bar*
         */
    }

    // TODO review
    @Test
    fun `example 136 - strikethrough foo`() = runTest {
        // given
        val textFlow = """
            <del>
            *foo*
            </del>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "del" {
                +"\n*foo*\n"
            }
        }
        // GFM expected:
        /*
            <del>
            *foo*
            </del>
         */
    }

    // TODO review
    @Test
    fun `example 137 - strikethrough foo`() = runTest {
        // given
        val textFlow = """
            <del>

            *foo*

            </del>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "del" {
                +"\n"
                "p" {
                    "em" {
                        +"foo"
                    }
                }
                +"\n"
            }
        }
        // GFM expected:
        /*
            <del>
            <p><em>foo</em></p>
            </del>
         */
    }

    // TODO review
    @Test
    fun `example 138 - paragraph foo`() = runTest {
        // given
        val textFlow = "<del>*foo*</del>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "del" {
                    "em" {
                        +"foo"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p><del><em>foo</em></del></p>
         */
    }

    // TODO review
    @Test
    fun `example 139 - indented code block, paragraph okay`() = runTest {
        // given
        val textFlow = buildText {
            +"<pre language=\"haskell\"><code>\n"
            +"import Text.HTML.TagSoup\n"
            +"\n"
            +"main :: IO ()\n"
            +"main = print \$ parseTags tags\n"
            +"</code></pre>\n"
            +"okay\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"\nimport Text.HTML.TagSoup\n\nmain :: IO ()\nmain = print \$ parseTags tags\n"
                }
            }
            "p" {
                +"okay"
            }
        }
        // GFM expected:
        /*
            <pre language="haskell"><code>
            import Text.HTML.TagSoup
            
            main :: IO ()
            main = print $ parseTags tags
            </code></pre>
            <p>okay</p>
         */
    }

    // TODO review
    @Test
    fun `example 140 - script JavaScript example d, paragraph okay`() = runTest {
        // given
        val textFlow = """
            <script type="text/javascript">
            // JavaScript example

            document.getElementById("demo").innerHTML = "Hello JavaScript!";
            </script>
            okay
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "script"("type" to "text/javascript") {
                +"\n// JavaScript example\n\ndocument.getElementById(\"demo\").innerHTML = \"Hello JavaScript!\";\n"
            }
            "p" {
                +"okay"
            }
        }
        // GFM expected:
        /*
            <script type="text/javascript">
            // JavaScript example
            
            document.getElementById("demo").innerHTML = "Hello JavaScript!";
            </script>
            <p>okay</p>
         */
    }

    // TODO review
    @Test
    fun `example 141 - style h1 {colorred} p {colo, paragraph okay`() = runTest {
        // given
        val textFlow = buildText {
            +"<style\n"
            +"  type=\"text/css\">\n"
            +"h1 {color:red;}\n"
            +"\n"
            +"p {color:blue;}\n"
            +"</style>\n"
            +"okay\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "style"("type" to "text/css") {
                +"\nh1 {color:red;}\n\np {color:blue;}\n"
            }
            "p" {
                +"okay"
            }
        }
        // GFM expected:
        /*
            <style
              type="text/css">
            h1 {color:red;}
            
            p {color:blue;}
            </style>
            <p>okay</p>
         */
    }

    // TODO review
    @Test
    fun `example 142 - style foo`() = runTest {
        // given
        val textFlow = buildText {
            +"<style\n"
            +"  type=\"text/css\">\n"
            +"\n"
            +"foo\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "style"("type" to "text/css") {
                +"\n\nfoo\n"
            }
        }
        // GFM expected:
        /*
            <style
              type="text/css">
            
            foo
         */
    }

    // TODO review
    @Test
    fun `example 143 - blockquote (text , div foo), paragraph bar`() = runTest {
        // given
        val textFlow = """
            > <div>
            > foo

            bar
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "div" {
                    +"\nfoo\n"
                }
            }
            "p" {
                +"bar"
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <div>
            foo
            </blockquote>
            <p>bar</p>
         */
    }

    // TODO review
    @Test
    fun `example 144 - ul with 2 items`() = runTest {
        // given
        val textFlow = """
            - <div>
            - foo
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "div" {
                    }
                }
                "li" {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>
            <div>
            </li>
            <li>foo</li>
            </ul>
         */
    }

    // TODO review
    @Test
    fun `example 145 - style p{colorred}, paragraph foo`() = runTest {
        // given
        val textFlow = """
            <style>p{color:red;}</style>
            *foo*
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "style" {
                +"p{color:red;}"
            }
            "p" {
                "em" {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <style>p{color:red;}</style>
            <p><em>foo</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 146 - text bar, paragraph baz`() = runTest {
        // given
        val textFlow = """
            <!-- foo -->*bar*
            *baz*
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            +"*bar*\n"
            "p" {
                "em" {
                    +"baz"
                }
            }
        }
        // GFM expected:
        /*
            <!-- foo -->*bar*
            <p><em>baz</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 147 - script foo, text 1 bar`() = runTest {
        // given
        val textFlow = """
            <script>
            foo
            </script>1. *bar*
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "script" {
                +"\nfoo\n"
            }
            +"1. *bar*\n"
        }
        // GFM expected:
        /*
            <script>
            foo
            </script>1. *bar*
         */
    }

    // TODO review
    @Test
    fun `example 148 - paragraph okay`() = runTest {
        // given
        val textFlow = buildText {
            +"<!-- Foo\n"
            +"\n"
            +"bar\n"
            +"   baz -->\n"
            +"okay\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"okay"
            }
        }
        // GFM expected:
        /*
            <!-- Foo
            
            bar
               baz -->
            <p>okay</p>
         */
    }

    // TODO review
    @Test
    fun `example 149 - text ' , paragraph okay`() = runTest {
        // given
        val textFlow = buildText {
            +"<?php\n"
            +"\n"
            +"  echo '>';\n"
            +"\n"
            +"?>\n"
            +"okay\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            +"';\n\n?>\n"
            "p" {
                +"okay"
            }
        }
        // GFM expected:
        /*
            <?php
            
              echo '>';
            
            ?>
            <p>okay</p>
         */
    }

    // TODO review
    @Test
    fun `example 150`() = runTest {
        // given
        val textFlow = "<!DOCTYPE html>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            // TODO assertion
        }
        // GFM expected:
        /*
            <!DOCTYPE html>
         */
    }

    // TODO review
    @Test
    fun `example 151 - paragraph okay`() = runTest {
        // given
        val textFlow = buildText {
            +"<![CDATA[\n"
            +"function matchwo(a,b)\n"
            +"{\n"
            +"  if (a < b && a < 0) then {\n"
            +"    return 1;\n"
            +"\n"
            +"  } else {\n"
            +"\n"
            +"    return 0;\n"
            +"  }\n"
            +"}\n"
            +"]]>\n"
            +"okay\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"okay"
            }
        }
        // GFM expected:
        /*
            <![CDATA[
            function matchwo(a,b)
            {
              if (a < b && a < 0) then {
                return 1;
            
              } else {
            
                return 0;
              }
            }
            ]]>
            <p>okay</p>
         */
    }

    // TODO review
    @Test
    fun `example 152 - indented code block`() = runTest {
        // given
        val textFlow = buildText {
            +"  <!-- foo -->\n"
            +"\n"
            +"    <!-- foo -->\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"<!-- foo -->\n"
                }
            }
        }
        // GFM expected:
        /*
              <!-- foo -->
            <pre><code>&lt;!-- foo --&gt;
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 153 - div div`() = runTest {
        // given
        val textFlow = buildText {
            +"  <div>\n"
            +"\n"
            +"    <div>\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "div" {
                "pre" {
                    "code" {
                        +"<div>\n"
                    }
                }
            }
        }
        // GFM expected:
        /*
              <div>
            <pre><code>&lt;div&gt;
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 154 - paragraph Foo, div bar`() = runTest {
        // given
        val textFlow = """
            Foo
            <div>
            bar
            </div>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo"
            }
            "div" {
                +"\nbar\n"
            }
        }
        // GFM expected:
        /*
            <p>Foo</p>
            <div>
            bar
            </div>
         */
    }

    // TODO review
    @Test
    fun `example 155 - div bar, text foo`() = runTest {
        // given
        val textFlow = """
            <div>
            bar
            </div>
            *foo*
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "div" {
                +"\nbar\n"
            }
            +"\n*foo*\n"
        }
        // GFM expected:
        /*
            <div>
            bar
            </div>
            *foo*
         */
    }

    // TODO review
    @Test
    fun `example 156 - paragraph Foo baz`() = runTest {
        // given
        val textFlow = """
            Foo
            <a href="bar">
            baz
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo\n"
                "a"("href" to "bar") {
                    +"\nbaz"
                }
            }
        }
        // GFM expected:
        /*
            <p>Foo
            <a href="bar">
            baz</p>
         */
    }

    // TODO review
    @Test
    fun `example 157 - div Emphasized text`() = runTest {
        // given
        val textFlow = """
            <div>

            *Emphasized* text.

            </div>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "div" {
                "p" {
                    "em" {
                        +"Emphasized"
                    }
                    +" text."
                }
            }
        }
        // GFM expected:
        /*
            <div>
            <p><em>Emphasized</em> text.</p>
            </div>
         */
    }

    // TODO review
    @Test
    fun `example 158 - div Emphasized text`() = runTest {
        // given
        val textFlow = """
            <div>
            *Emphasized* text.
            </div>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "div" {
                +"\n*Emphasized* text.\n"
            }
        }
        // GFM expected:
        /*
            <div>
            *Emphasized* text.
            </div>
         */
    }

    // TODO review
    @Test
    fun `example 159 - table`() = runTest {
        // given
        val textFlow = """
            <table>

            <tr>

            <td>
            Hi
            </td>

            </tr>

            </table>
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "table" {
                "tr" {
                    "td" {
                        +"\nHi\n"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <table>
            <tr>
            <td>
            Hi
            </td>
            </tr>
            </table>
         */
    }

    // TODO review
    @Test
    fun `example 160 - table`() = runTest {
        // given
        val textFlow = buildText {
            +"<table>\n"
            +"\n"
            +"  <tr>\n"
            +"\n"
            +"    <td>\n"
            +"      Hi\n"
            +"    </td>\n"
            +"\n"
            +"  </tr>\n"
            +"\n"
            +"</table>\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "table" {
                "tr" {
                    "pre" {
                        "code" {
                            +"<td>\n  Hi\n</td>\n"
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <table>
              <tr>
            <pre><code>&lt;td&gt;
              Hi
            &lt;/td&gt;
            </code></pre>
              </tr>
            </table>
         */
    }

}
