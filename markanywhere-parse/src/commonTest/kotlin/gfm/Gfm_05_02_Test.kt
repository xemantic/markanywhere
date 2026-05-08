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
 * Tests for GFM Section 05.02 — List items.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#list-items
 */
@Suppress("ClassName")
class Gfm_05_02_Test {

    @Test
    fun `example 231 - paragraph A paragraph with two li indented code block blockquote te truncated`() = runTest {
        // given
        val textFlow = buildText {
            +"A paragraph\n"
            +"with two lines.\n"
            +"\n"
            +"    indented code\n"
            +"\n"
            +"> A block quote.\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"A paragraph\nwith two lines."
            }
            "pre" {
                "code" {
                    +"indented code\n"
                }
            }
            "blockquote" {
                "p" {
                    +"A block quote."
                }
            }
        }
        // GFM expected:
        /*
            <p>A paragraph
            with two lines.</p>
            <pre><code>indented code
            </code></pre>
            <blockquote>
            <p>A block quote.</p>
            </blockquote>
         */
    }

    @Test
    fun `example 232 - ol with 1 item`() = runTest {
        // given
        val textFlow = buildText {
            +"1.  A paragraph\n"
            +"    with two lines.\n"
            +"\n"
            +"        indented code\n"
            +"\n"
            +"    > A block quote.\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol" {
                "li" {
                    "p" {
                        +"A paragraph\nwith two lines."
                    }
                    "pre" {
                        "code" {
                            +"indented code\n"
                        }
                    }
                    "blockquote" {
                        "p" {
                            +"A block quote."
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ol>
            <li>
            <p>A paragraph
            with two lines.</p>
            <pre><code>indented code
            </code></pre>
            <blockquote>
            <p>A block quote.</p>
            </blockquote>
            </li>
            </ol>
         */
    }

    @Test
    fun `example 233 - DIVERGENCE - ul with 1 item paragraph two`() = runTest {
        // given
        val textFlow = buildText {
            +"- one\n"
            +"\n"
            +" two\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"one"
                    }
                }
            }
            "p" {
                +"two"
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>one</li>
            </ul>
            <p>two</p>
         */
    }

    @Test
    fun `example 234 - ul with 1 item`() = runTest {
        // given
        val textFlow = buildText {
            +"- one\n"
            +"\n"
            +"  two\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"one"
                    }
                    "p" {
                        +"two"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>
            <p>one</p>
            <p>two</p>
            </li>
            </ul>
         */
    }

    @Test
    fun `example 235 - DIVERGENCE - ul with 1 item indented code block`() = runTest {
        // given
        val textFlow = buildText {
            +" -    one\n"
            +"\n"
            +"     two\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"one"
                    }
                }
            }
            "pre" {
                "code" {
                    +" two\n"
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>one</li>
            </ul>
            <pre><code> two
            </code></pre>
         */
    }

    @Test
    fun `example 236 - ul with 1 item`() = runTest {
        // given
        val textFlow = buildText {
            +" -    one\n"
            +"\n"
            +"      two\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"one"
                    }
                    "p" {
                        +"two"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>
            <p>one</p>
            <p>two</p>
            </li>
            </ul>
         */
    }

    @Test
    fun `example 237 - blockquote text blockquote text ol with 1 item text text`() = runTest {
        // given
        val textFlow = buildText {
            +"   > > 1.  one\n"
            +">>\n"
            +">>     two\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "blockquote" {
                    "ol" {
                        "li" {
                            "p" {
                                +"one"
                            }
                            "p" {
                                +"two"
                            }
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <blockquote>
            <ol>
            <li>
            <p>one</p>
            <p>two</p>
            </li>
            </ol>
            </blockquote>
            </blockquote>
         */
    }

    @Test
    fun `example 238 - DIVERGENCE - blockquote text blockquote text ul with 1 item text paragraph truncated`() = runTest {
        // given
        val textFlow = buildText {
            +">>- one\n"
            +">>\n"
            +"  >  > two\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "blockquote" {
                    "ul" {
                        "li" {
                            "p" {
                                +"one"
                            }
                        }
                    }
                    "p" {
                        +"two"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <blockquote>
            <ul>
            <li>one</li>
            </ul>
            <p>two</p>
            </blockquote>
            </blockquote>
         */
    }

    @Test
    fun `example 239 - paragraph -one paragraph 2two`() = runTest {
        // given
        val textFlow = """
            -one

            2.two
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"-one"
            }
            "p" {
                +"2.two"
            }
        }
        // GFM expected:
        /*
            <p>-one</p>
            <p>2.two</p>
         */
    }

    @Test
    fun `example 240 - ul with 1 item`() = runTest {
        // given
        val textFlow = buildText {
            +"- foo\n"
            +"\n"
            +"\n"
            +"  bar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"foo"
                    }
                    "p" {
                        +"bar"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>
            <p>foo</p>
            <p>bar</p>
            </li>
            </ul>
         */
    }

    @Test
    fun `example 241 - ol with 1 item`() = runTest {
        // given
        val textFlow = buildText {
            +"1.  foo\n"
            +"\n"
            +"    ```\n"
            +"    bar\n"
            +"    ```\n"
            +"\n"
            +"    baz\n"
            +"\n"
            +"    > bam\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol" {
                "li" {
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
                    "blockquote" {
                        "p" {
                            +"bam"
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ol>
            <li>
            <p>foo</p>
            <pre><code>bar
            </code></pre>
            <p>baz</p>
            <blockquote>
            <p>bam</p>
            </blockquote>
            </li>
            </ol>
         */
    }

    @Test
    fun `example 242 - ul with 1 item`() = runTest {
        // given
        val textFlow = buildText {
            +"- Foo\n"
            +"\n"
            +"      bar\n"
            +"\n"
            +"\n"
            +"      baz\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"Foo"
                    }
                    "pre" {
                        "code" {
                            +"bar\n\n\nbaz\n"
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>
            <p>Foo</p>
            <pre><code>bar
            
            
            baz
            </code></pre>
            </li>
            </ul>
         */
    }

    @Test
    fun `example 243 - DIVERGENCE - ol with 1 item start 123456789`() = runTest {
        // given
        val textFlow = "123456789. ok".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol"("start" to "123456789") {
                "li" {
                    "p" {
                        +"ok"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ol start="123456789">
            <li>ok</li>
            </ol>
         */
    }

    @Test
    fun `example 244 - paragraph 1234567890 not ok`() = runTest {
        // given
        val textFlow = "1234567890. not ok".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"1234567890. not ok"
            }
        }
        // GFM expected:
        /*
            <p>1234567890. not ok</p>
         */
    }

    @Test
    fun `example 245 - DIVERGENCE - ol with 1 item start 0`() = runTest {
        // given
        val textFlow = "0. ok".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol"("start" to "0") {
                "li" {
                    "p" {
                        +"ok"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ol start="0">
            <li>ok</li>
            </ol>
         */
    }

    @Test
    fun `example 246 - DIVERGENCE - ol with 1 item start 3`() = runTest {
        // given
        val textFlow = "003. ok".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol"("start" to "3") {
                "li" {
                    "p" {
                        +"ok"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ol start="3">
            <li>ok</li>
            </ol>
         */
    }

    @Test
    fun `example 247 - paragraph -1 not ok`() = runTest {
        // given
        val textFlow = "-1. not ok".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"-1. not ok"
            }
        }
        // GFM expected:
        /*
            <p>-1. not ok</p>
         */
    }

    @Test
    fun `example 248 - ul with 1 item`() = runTest {
        // given
        val textFlow = buildText {
            +"- foo\n"
            +"\n"
            +"      bar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"foo"
                    }
                    "pre" {
                        "code" {
                            +"bar\n"
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>
            <p>foo</p>
            <pre><code>bar
            </code></pre>
            </li>
            </ul>
         */
    }

    @Test
    fun `example 249 - ol with 1 item start 10`() = runTest {
        // given
        val textFlow = buildText {
            +"  10.  foo\n"
            +"\n"
            +"           bar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol"("start" to "10") {
                "li" {
                    "p" {
                        +"foo"
                    }
                    "pre" {
                        "code" {
                            +"bar\n"
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ol start="10">
            <li>
            <p>foo</p>
            <pre><code>bar
            </code></pre>
            </li>
            </ol>
         */
    }

    @Test
    fun `example 250 - indented code block paragraph paragraph indented code block`() = runTest {
        // given
        val textFlow = buildText {
            +"    indented code\n"
            +"\n"
            +"paragraph\n"
            +"\n"
            +"    more code\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"indented code\n"
                }
            }
            "p" {
                +"paragraph"
            }
            "pre" {
                "code" {
                    +"more code\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>indented code
            </code></pre>
            <p>paragraph</p>
            <pre><code>more code
            </code></pre>
         */
    }

    @Test
    fun `example 251 - ol with 1 item`() = runTest {
        // given
        val textFlow = buildText {
            +"1.     indented code\n"
            +"\n"
            +"   paragraph\n"
            +"\n"
            +"       more code\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol" {
                "li" {
                    "pre" {
                        "code" {
                            +"indented code\n"
                        }
                    }
                    "p" {
                        +"paragraph"
                    }
                    "pre" {
                        "code" {
                            +"more code\n"
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ol>
            <li>
            <pre><code>indented code
            </code></pre>
            <p>paragraph</p>
            <pre><code>more code
            </code></pre>
            </li>
            </ol>
         */
    }

    @Test
    fun `example 252 - ol with 1 item`() = runTest {
        // given
        val textFlow = buildText {
            +"1.      indented code\n"
            +"\n"
            +"   paragraph\n"
            +"\n"
            +"       more code\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol" {
                "li" {
                    "pre" {
                        "code" {
                            +" indented code\n"
                        }
                    }
                    "p" {
                        +"paragraph"
                    }
                    "pre" {
                        "code" {
                            +"more code\n"
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ol>
            <li>
            <pre><code> indented code
            </code></pre>
            <p>paragraph</p>
            <pre><code>more code
            </code></pre>
            </li>
            </ol>
         */
    }

    @Test
    fun `example 253 - paragraph foo paragraph bar`() = runTest {
        // given
        val textFlow = buildText {
            +"   foo\n"
            +"\n"
            +"bar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo"
            }
            "p" {
                +"bar"
            }
        }
        // GFM expected:
        /*
            <p>foo</p>
            <p>bar</p>
         */
    }

    fun `example 254 - DIVERGENCE - ul with 1 item paragraph bar`() = runTest {
        // given
        val textFlow = buildText {
            +"-    foo\n"
            +"\n"
            +"  bar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"foo"
                    }
                }
            }
            "p" {
                +"bar"
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>foo</li>
            </ul>
            <p>bar</p>
         */
    }

    @Test
    fun `example 255 - ul with 1 item`() = runTest {
        // given
        val textFlow = buildText {
            +"-  foo\n"
            +"\n"
            +"   bar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"foo"
                    }
                    "p" {
                        +"bar"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>
            <p>foo</p>
            <p>bar</p>
            </li>
            </ul>
         */
    }

    @Test
    fun `example 256 - DIVERGENCE - ul with 3 items`() = runTest {
        // given
        val textFlow = buildText {
            +"-\n"
            +"  foo\n"
            +"-\n"
            +"  ```\n"
            +"  bar\n"
            +"  ```\n"
            +"-\n"
            +"      baz\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"foo"
                    }
                }
                "li" {
                    "pre" {
                        "code" {
                            +"bar\n"
                        }
                    }
                }
                "li" {
                    "pre" {
                        "code" {
                            +"baz\n"
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>foo</li>
            <li>
            <pre><code>bar
            </code></pre>
            </li>
            <li>
            <pre><code>baz
            </code></pre>
            </li>
            </ul>
         */
    }

    @Test
    fun `example 257 - DIVERGENCE - ul with 1 item`() = runTest {
        // given
        val textFlow = buildText {
            +"-   \n"
            +"  foo\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"foo"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>foo</li>
            </ul>
         */
    }

    @Test
    fun `example 258 - ul with 1 item paragraph foo`() = runTest {
        // given
        val textFlow = buildText {
            +"-\n"
            +"\n"
            +"  foo\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                }
            }
            "p" {
                +"foo"
            }
        }
        // GFM expected:
        /*
            <ul>
            <li></li>
            </ul>
            <p>foo</p>
         */
    }

    @Test
    fun `example 259 - DIVERGENCE - ul with 3 items`() = runTest {
        // given
        val textFlow = """
            - foo
            -
            - bar
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"foo"
                    }
                }
                "li" {
                }
                "li" {
                    "p" {
                        +"bar"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>foo</li>
            <li></li>
            <li>bar</li>
            </ul>
         */
    }

    @Test
    fun `example 260 - DIVERGENCE - ul with 3 items`() = runTest {
        // given
        val textFlow = buildText {
            +"- foo\n"
            +"-   \n"
            +"- bar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"foo"
                    }
                }
                "li" {
                }
                "li" {
                    "p" {
                        +"bar"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>foo</li>
            <li></li>
            <li>bar</li>
            </ul>
         */
    }

    @Test
    fun `example 261 - DIVERGENCE - ol with 3 items`() = runTest {
        // given
        val textFlow = """
            1. foo
            2.
            3. bar
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol" {
                "li" {
                    "p" {
                        +"foo"
                    }
                }
                "li" {
                }
                "li" {
                    "p" {
                        +"bar"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ol>
            <li>foo</li>
            <li></li>
            <li>bar</li>
            </ol>
         */
    }

    @Test
    fun `example 262 - ul with 1 item`() = runTest {
        // given
        val textFlow = "*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li></li>
            </ul>
         */
    }

    @Test
    fun `example 263 - paragraph foo paragraph foo 1`() = runTest {
        // given
        val textFlow = """
            foo
            *

            foo
            1.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo\n*"
            }
            "p" {
                +"foo\n1."
            }
        }
        // GFM expected:
        /*
            <p>foo
            *</p>
            <p>foo
            1.</p>
         */
    }

    @Test
    fun `example 264 - ol with 1 item`() = runTest {
        // given
        val textFlow = buildText {
            +" 1.  A paragraph\n"
            +"     with two lines.\n"
            +"\n"
            +"         indented code\n"
            +"\n"
            +"     > A block quote.\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol" {
                "li" {
                    "p" {
                        +"A paragraph\nwith two lines."
                    }
                    "pre" {
                        "code" {
                            +"indented code\n"
                        }
                    }
                    "blockquote" {
                        "p" {
                            +"A block quote."
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ol>
            <li>
            <p>A paragraph
            with two lines.</p>
            <pre><code>indented code
            </code></pre>
            <blockquote>
            <p>A block quote.</p>
            </blockquote>
            </li>
            </ol>
         */
    }

    @Test
    fun `example 265 - ol with 1 item`() = runTest {
        // given
        val textFlow = buildText {
            +"  1.  A paragraph\n"
            +"      with two lines.\n"
            +"\n"
            +"          indented code\n"
            +"\n"
            +"      > A block quote.\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol" {
                "li" {
                    "p" {
                        +"A paragraph\nwith two lines."
                    }
                    "pre" {
                        "code" {
                            +"indented code\n"
                        }
                    }
                    "blockquote" {
                        "p" {
                            +"A block quote."
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ol>
            <li>
            <p>A paragraph
            with two lines.</p>
            <pre><code>indented code
            </code></pre>
            <blockquote>
            <p>A block quote.</p>
            </blockquote>
            </li>
            </ol>
         */
    }

    @Test
    fun `example 266 - ol with 1 item`() = runTest {
        // given
        val textFlow = buildText {
            +"   1.  A paragraph\n"
            +"       with two lines.\n"
            +"\n"
            +"           indented code\n"
            +"\n"
            +"       > A block quote.\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol" {
                "li" {
                    "p" {
                        +"A paragraph\nwith two lines."
                    }
                    "pre" {
                        "code" {
                            +"indented code\n"
                        }
                    }
                    "blockquote" {
                        "p" {
                            +"A block quote."
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ol>
            <li>
            <p>A paragraph
            with two lines.</p>
            <pre><code>indented code
            </code></pre>
            <blockquote>
            <p>A block quote.</p>
            </blockquote>
            </li>
            </ol>
         */
    }

    @Test
    fun `example 267 - indented code block`() = runTest {
        // given
        val textFlow = buildText {
            +"    1.  A paragraph\n"
            +"        with two lines.\n"
            +"\n"
            +"            indented code\n"
            +"\n"
            +"        > A block quote.\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"1.  A paragraph\n    with two lines.\n\n        indented code\n\n    > A block quote.\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>1.  A paragraph
                with two lines.
            
                    indented code
            
                &gt; A block quote.
            </code></pre>
         */
    }

    @Test
    fun `example 268 - ol with 1 item`() = runTest {
        // given
        val textFlow = buildText {
            +"  1.  A paragraph\n"
            +"with two lines.\n"
            +"\n"
            +"          indented code\n"
            +"\n"
            +"      > A block quote.\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol" {
                "li" {
                    "p" {
                        +"A paragraph\nwith two lines."
                    }
                    "pre" {
                        "code" {
                            +"indented code\n"
                        }
                    }
                    "blockquote" {
                        "p" {
                            +"A block quote."
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ol>
            <li>
            <p>A paragraph
            with two lines.</p>
            <pre><code>indented code
            </code></pre>
            <blockquote>
            <p>A block quote.</p>
            </blockquote>
            </li>
            </ol>
         */
    }

    @Test
    fun `example 269 - DIVERGENCE - ol with 1 item`() = runTest {
        // given
        val textFlow = buildText {
            +"  1.  A paragraph\n"
            +"    with two lines.\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol" {
                "li" {
                    "p" {
                        +"A paragraph\nwith two lines."
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ol>
            <li>A paragraph
            with two lines.</li>
            </ol>
         */
    }

    @Test
    fun `example 270 - blockquote text ol with 1 item text`() = runTest {
        // given
        val textFlow = """
            > 1. > Blockquote
            continued here.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "ol" {
                    "li" {
                        "blockquote" {
                            "p" {
                                +"Blockquote\ncontinued here."
                            }
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <ol>
            <li>
            <blockquote>
            <p>Blockquote
            continued here.</p>
            </blockquote>
            </li>
            </ol>
            </blockquote>
         */
    }

    @Test
    fun `example 271 - blockquote text ol with 1 item text`() = runTest {
        // given
        val textFlow = """
            > 1. > Blockquote
            > continued here.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "ol" {
                    "li" {
                        "blockquote" {
                            "p" {
                                +"Blockquote\ncontinued here."
                            }
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <blockquote>
            <ol>
            <li>
            <blockquote>
            <p>Blockquote
            continued here.</p>
            </blockquote>
            </li>
            </ol>
            </blockquote>
         */
    }

    @Test
    fun `example 272 - DIVERGENCE - ul with 1 item`() = runTest {
        // given
        val textFlow = buildText {
            +"- foo\n"
            +"  - bar\n"
            +"    - baz\n"
            +"      - boo\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"foo"
                    }
                    "ul" {
                        "li" {
                            "p" {
                                +"bar"
                            }
                            "ul" {
                                "li" {
                                    "p" {
                                        +"baz"
                                    }
                                    "ul" {
                                        "li" {
                                            "p" {
                                                +"boo"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>foo
            <ul>
            <li>bar
            <ul>
            <li>baz
            <ul>
            <li>boo</li>
            </ul>
            </li>
            </ul>
            </li>
            </ul>
            </li>
            </ul>
         */
    }

    @Test
    fun `example 273 - DIVERGENCE - ul with 4 items`() = runTest {
        // given
        val textFlow = buildText {
            +"- foo\n"
            +" - bar\n"
            +"  - baz\n"
            +"   - boo\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"foo"
                    }
                }
                "li" {
                    "p" {
                        +"bar"
                    }
                }
                "li" {
                    "p" {
                        +"baz"
                    }
                }
                "li" {
                    "p" {
                        +"boo"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>foo</li>
            <li>bar</li>
            <li>baz</li>
            <li>boo</li>
            </ul>
         */
    }

    @Test
    fun `example 274 - DIVERGENCE - ol with 1 item start 10`() = runTest {
        // given
        val textFlow = buildText {
            +"10) foo\n"
            +"    - bar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol"("start" to "10") {
                "li" {
                    "p" {
                        +"foo"
                    }
                    "ul" {
                        "li" {
                            "p" {
                                +"bar"
                            }
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ol start="10">
            <li>foo
            <ul>
            <li>bar</li>
            </ul>
            </li>
            </ol>
         */
    }

    @Test
    fun `example 275 - DIVERGENCE - ol with 1 item start 10 ul with 1 item`() = runTest {
        // given
        val textFlow = buildText {
            +"10) foo\n"
            +"   - bar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol"("start" to "10") {
                "li" {
                    "p" {
                        +"foo"
                    }
                }
            }
            "ul" {
                "li" {
                    "p" {
                        +"bar"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ol start="10">
            <li>foo</li>
            </ol>
            <ul>
            <li>bar</li>
            </ul>
         */
    }

    @Test
    fun `example 276 - DIVERGENCE - ul with 1 item`() = runTest {
        // given
        val textFlow = "- - foo".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "ul" {
                        "li" {
                            "p" {
                                +"foo"
                            }
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>
            <ul>
            <li>foo</li>
            </ul>
            </li>
            </ul>
         */
    }

    @Test
    fun `example 277 - DIVERGENCE - ol with 1 item`() = runTest {
        // given
        val textFlow = "1. - 2. foo".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol" {
                "li" {
                    "ul" {
                        "li" {
                            "ol"("start" to "2") {
                                "li" {
                                    "p" {
                                        +"foo"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ol>
            <li>
            <ul>
            <li>
            <ol start="2">
            <li>foo</li>
            </ol>
            </li>
            </ul>
            </li>
            </ol>
         */
    }

    @Test
    fun `example 278 - DIVERGENCE - ul with 2 items`() = runTest {
        // given
        val textFlow = buildText {
            +"- # Foo\n"
            +"- Bar\n"
            +"  ---\n"
            +"  baz\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Setext headings are not supported (see DIVERGENCE in markanywhere-parse
        // README); `---` becomes a thematic break that ends the list, and the
        // following `baz` flows as a top-level paragraph.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "h1" {
                        +"Foo"
                    }
                }
                "li" {
                    "p" {
                        +"Bar"
                    }
                }
            }
            "hr" {}
            "p" {
                +"baz"
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>
            <h1>Foo</h1>
            </li>
            <li>
            <h2>Bar</h2>
            baz</li>
            </ul>
         */
    }

}
