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
 * Tests for GFM Section 05.04 — Lists.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#lists
 */
@Suppress("ClassName")
class Gfm_05_04_Test {

    @Test
    fun `example 281 - DIVERGENCE - ul with 2 items ul with 1 item`() = runTest {
        // given
        val textFlow = """
            - foo
            - bar
            + baz
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"foo" }
                }
                "li" {
                    "p" { +"bar" }
                }
            }
            "ul" {
                "li" {
                    "p" { +"baz" }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>foo</li>
            <li>bar</li>
            </ul>
            <ul>
            <li>baz</li>
            </ul>
         */
    }

    @Test
    fun `example 282 - DIVERGENCE - ol with 2 items ol with 1 item start 3`() = runTest {
        // given
        val textFlow = """
            1. foo
            2. bar
            3) baz
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol" {
                "li" {
                    "p" { +"foo" }
                }
                "li" {
                    "p" { +"bar" }
                }
            }
            "ol"("start" to "3") {
                "li" {
                    "p" { +"baz" }
                }
            }
        }
        // GFM expected:
        /*
            <ol>
            <li>foo</li>
            <li>bar</li>
            </ol>
            <ol start="3">
            <li>baz</li>
            </ol>
         */
    }

    @Test
    fun `example 283 - DIVERGENCE - paragraph Foo ul with 2 items`() = runTest {
        // given
        val textFlow = """
            Foo
            - bar
            - baz
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo"
            }
            "ul" {
                "li" {
                    "p" { +"bar" }
                }
                "li" {
                    "p" { +"baz" }
                }
            }
        }
        // GFM expected:
        /*
            <p>Foo</p>
            <ul>
            <li>bar</li>
            <li>baz</li>
            </ul>
         */
    }

    @Test
    fun `example 284 - paragraph The number of windows i`() = runTest {
        // given
        val textFlow = """
            The number of windows in my house is
            14.  The number of doors is 6.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"The number of windows in my house is\n14.  The number of doors is 6."
            }
        }
        // GFM expected:
        /*
            <p>The number of windows in my house is
            14.  The number of doors is 6.</p>
         */
    }

    @Test
    fun `example 285 - DIVERGENCE - paragraph The number of windows i ol with 1 item`() = runTest {
        // given
        val textFlow = """
            The number of windows in my house is
            1.  The number of doors is 6.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"The number of windows in my house is"
            }
            "ol" {
                "li" {
                    "p" { +"The number of doors is 6." }
                }
            }
        }
        // GFM expected:
        /*
            <p>The number of windows in my house is</p>
            <ol>
            <li>The number of doors is 6.</li>
            </ol>
         */
    }

    @Test
    fun `example 286 - ul with 3 items`() = runTest {
        // given
        val textFlow = """
            - foo

            - bar


            - baz
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
                    "p" {
                        +"bar"
                    }
                }
                "li" {
                    "p" {
                        +"baz"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>
            <p>foo</p>
            </li>
            <li>
            <p>bar</p>
            </li>
            <li>
            <p>baz</p>
            </li>
            </ul>
         */
    }

    @Test
    fun `example 287 - DIVERGENCE - ul with 1 item`() = runTest {
        // given
        val textFlow = buildText {
            +"- foo\n"
            +"  - bar\n"
            +"    - baz\n"
            +"\n"
            +"\n"
            +"      bim\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"foo" }
                    "ul" {
                        "li" {
                            "p" { +"bar" }
                            "ul" {
                                "li" {
                                    "p" {
                                        +"baz"
                                    }
                                    "p" {
                                        +"bim"
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
            <li>
            <p>baz</p>
            <p>bim</p>
            </li>
            </ul>
            </li>
            </ul>
            </li>
            </ul>
         */
    }

    @Test
    fun `example 288 - DIVERGENCE - two ul with 2 items`() = runTest {
        // given
        val textFlow = """
            - foo
            - bar

            <!-- -->

            - baz
            - bim
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"foo" }
                }
                "li" {
                    "p" { +"bar" }
                }
            }
            +"<!-- -->\n"
            "ul" {
                "li" {
                    "p" { +"baz" }
                }
                "li" {
                    "p" { +"bim" }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>foo</li>
            <li>bar</li>
            </ul>
            <!-- -->
            <ul>
            <li>baz</li>
            <li>bim</li>
            </ul>
         */
    }

    @Test
    fun `example 289 - DIVERGENCE - ul with 2 items indented code block`() = runTest {
        // given
        val textFlow = buildText {
            +"-   foo\n"
            +"\n"
            +"    notcode\n"
            +"\n"
            +"-   foo\n"
            +"\n"
            +"<!-- -->\n"
            +"\n"
            +"    code\n"
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
                        +"notcode"
                    }
                }
                "li" {
                    "p" {
                        +"foo"
                    }
                }
            }
            +"<!-- -->\n"
            "pre" {
                "code" {
                    +"code\n"
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>
            <p>foo</p>
            <p>notcode</p>
            </li>
            <li>
            <p>foo</p>
            </li>
            </ul>
            <!-- -->
            <pre><code>code
            </code></pre>
         */
    }

    @Test
    fun `example 290 - DIVERGENCE - ul with 7 items`() = runTest {
        // given
        val textFlow = buildText {
            +"- a\n"
            +" - b\n"
            +"  - c\n"
            +"   - d\n"
            +"  - e\n"
            +" - f\n"
            +"- g\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" { "p" { +"a" } }
                "li" { "p" { +"b" } }
                "li" { "p" { +"c" } }
                "li" { "p" { +"d" } }
                "li" { "p" { +"e" } }
                "li" { "p" { +"f" } }
                "li" { "p" { +"g" } }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>a</li>
            <li>b</li>
            <li>c</li>
            <li>d</li>
            <li>e</li>
            <li>f</li>
            <li>g</li>
            </ul>
         */
    }

    @Test
    fun `example 291 - ol with 3 items`() = runTest {
        // given
        val textFlow = buildText {
            +"1. a\n"
            +"\n"
            +"  2. b\n"
            +"\n"
            +"   3. c\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol" {
                "li" {
                    "p" {
                        +"a"
                    }
                }
                "li" {
                    "p" {
                        +"b"
                    }
                }
                "li" {
                    "p" {
                        +"c"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ol>
            <li>
            <p>a</p>
            </li>
            <li>
            <p>b</p>
            </li>
            <li>
            <p>c</p>
            </li>
            </ol>
         */
    }

    @Test
    fun `example 292 - DIVERGENCE - ul with 4 items`() = runTest {
        // given
        val textFlow = buildText {
            +"- a\n"
            +" - b\n"
            +"  - c\n"
            +"   - d\n"
            +"    - e\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" { "p" { +"a" } }
                "li" { "p" { +"b" } }
                "li" { "p" { +"c" } }
                "li" {
                    "p" { +"d\n- e" }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>a</li>
            <li>b</li>
            <li>c</li>
            <li>d
            - e</li>
            </ul>
         */
    }

    @Test
    fun `example 293 - ol with 2 items indented code block`() = runTest {
        // given
        val textFlow = buildText {
            +"1. a\n"
            +"\n"
            +"  2. b\n"
            +"\n"
            +"    3. c\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol" {
                "li" {
                    "p" {
                        +"a"
                    }
                }
                "li" {
                    "p" {
                        +"b"
                    }
                }
            }
            "pre" {
                "code" {
                    +"3. c\n"
                }
            }
        }
        // GFM expected:
        /*
            <ol>
            <li>
            <p>a</p>
            </li>
            <li>
            <p>b</p>
            </li>
            </ol>
            <pre><code>3. c
            </code></pre>
         */
    }

    @Test
    fun `example 294 - ul with 3 items`() = runTest {
        // given
        val textFlow = """
            - a
            - b

            - c
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"a"
                    }
                }
                "li" {
                    "p" {
                        +"b"
                    }
                }
                "li" {
                    "p" {
                        +"c"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>
            <p>a</p>
            </li>
            <li>
            <p>b</p>
            </li>
            <li>
            <p>c</p>
            </li>
            </ul>
         */
    }

    @Test
    fun `example 295 - ul with 3 items`() = runTest {
        // given
        val textFlow = """
            * a
            *

            * c
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"a"
                    }
                }
                "li" {
                }
                "li" {
                    "p" {
                        +"c"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>
            <p>a</p>
            </li>
            <li></li>
            <li>
            <p>c</p>
            </li>
            </ul>
         */
    }

    @Test
    fun `example 296 - ul with 3 items`() = runTest {
        // given
        val textFlow = buildText {
            +"- a\n"
            +"- b\n"
            +"\n"
            +"  c\n"
            +"- d\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"a"
                    }
                }
                "li" {
                    "p" {
                        +"b"
                    }
                    "p" {
                        +"c"
                    }
                }
                "li" {
                    "p" {
                        +"d"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>
            <p>a</p>
            </li>
            <li>
            <p>b</p>
            <p>c</p>
            </li>
            <li>
            <p>d</p>
            </li>
            </ul>
         */
    }

    @Test
    fun `example 297 - DIVERGENCE - ul with 3 items`() = runTest {
        // given
        val textFlow = buildText {
            +"- a\n"
            +"- b\n"
            +"\n"
            +"  [ref]: /url\n"
            +"- d\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // DIVERGENCE: GFM treats `[ref]: /url` as a link reference definition
        // and emits no visible output. We don't track link reference definitions,
        // so the line is rendered as a second paragraph inside the second item.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"a"
                    }
                }
                "li" {
                    "p" {
                        +"b"
                    }
                    "p" {
                        +"[ref]: /url"
                    }
                }
                "li" {
                    "p" {
                        +"d"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>
            <p>a</p>
            </li>
            <li>
            <p>b</p>
            </li>
            <li>
            <p>d</p>
            </li>
            </ul>
         */
    }

    @Test
    fun `example 298 - DIVERGENCE - ul with 3 items`() = runTest {
        // given
        val textFlow = buildText {
            +"- a\n"
            +"- ```\n"
            +"  b\n"
            +"\n"
            +"\n"
            +"  ```\n"
            +"- c\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"a" }
                }
                "li" {
                    "pre" {
                        "code" {
                            +"b\n\n\n"
                        }
                    }
                }
                "li" {
                    "p" { +"c" }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>a</li>
            <li>
            <pre><code>b
            
            
            </code></pre>
            </li>
            <li>c</li>
            </ul>
         */
    }

    @Test
    fun `example 299 - DIVERGENCE - ul with 2 items`() = runTest {
        // given
        val textFlow = buildText {
            +"- a\n"
            +"  - b\n"
            +"\n"
            +"    c\n"
            +"- d\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"a" }
                    "ul" {
                        "li" {
                            "p" {
                                +"b"
                            }
                            "p" {
                                +"c"
                            }
                        }
                    }
                }
                "li" {
                    "p" { +"d" }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>a
            <ul>
            <li>
            <p>b</p>
            <p>c</p>
            </li>
            </ul>
            </li>
            <li>d</li>
            </ul>
         */
    }

    @Test
    fun `example 300 - DIVERGENCE - ul with 2 items`() = runTest {
        // given
        val textFlow = buildText {
            +"* a\n"
            +"  > b\n"
            +"  >\n"
            +"* c\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"a" }
                    "blockquote" {
                        "p" {
                            +"b"
                        }
                    }
                }
                "li" {
                    "p" { +"c" }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>a
            <blockquote>
            <p>b</p>
            </blockquote>
            </li>
            <li>c</li>
            </ul>
         */
    }

    @Test
    fun `example 301 - DIVERGENCE - ul with 2 items`() = runTest {
        // given
        val textFlow = buildText {
            +"- a\n"
            +"  > b\n"
            +"  ```\n"
            +"  c\n"
            +"  ```\n"
            +"- d\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"a" }
                    "blockquote" {
                        "p" {
                            +"b"
                        }
                    }
                    "pre" {
                        "code" {
                            +"c\n"
                        }
                    }
                }
                "li" {
                    "p" { +"d" }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>a
            <blockquote>
            <p>b</p>
            </blockquote>
            <pre><code>c
            </code></pre>
            </li>
            <li>d</li>
            </ul>
         */
    }

    @Test
    fun `example 302 - DIVERGENCE - ul with 1 item`() = runTest {
        // given
        val textFlow = "- a".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"a" }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>a</li>
            </ul>
         */
    }

    @Test
    fun `example 303 - DIVERGENCE - ul with 1 item`() = runTest {
        // given
        val textFlow = buildText {
            +"- a\n"
            +"  - b\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"a" }
                    "ul" {
                        "li" {
                            "p" { +"b" }
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>a
            <ul>
            <li>b</li>
            </ul>
            </li>
            </ul>
         */
    }

    @Test
    fun `example 304 - ol with 1 item`() = runTest {
        // given
        val textFlow = buildText {
            +"1. ```\n"
            +"   foo\n"
            +"   ```\n"
            +"\n"
            +"   bar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol" {
                "li" {
                    "pre" {
                        "code" {
                            +"foo\n"
                        }
                    }
                    "p" {
                        +"bar"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ol>
            <li>
            <pre><code>foo
            </code></pre>
            <p>bar</p>
            </li>
            </ol>
         */
    }

    @Test
    fun `example 305 - DIVERGENCE - ul with 1 item`() = runTest {
        // given
        val textFlow = buildText {
            +"* foo\n"
            +"  * bar\n"
            +"\n"
            +"  baz\n"
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
                            "p" { +"bar" }
                        }
                    }
                    "p" {
                        +"baz"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>
            <p>foo</p>
            <ul>
            <li>bar</li>
            </ul>
            <p>baz</p>
            </li>
            </ul>
         */
    }

    @Test
    fun `example 306 - DIVERGENCE - ul with 2 items`() = runTest {
        // given
        val textFlow = buildText {
            +"- a\n"
            +"  - b\n"
            +"  - c\n"
            +"\n"
            +"- d\n"
            +"  - e\n"
            +"  - f\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"a"
                    }
                    "ul" {
                        "li" { "p" { +"b" } }
                        "li" { "p" { +"c" } }
                    }
                }
                "li" {
                    "p" {
                        +"d"
                    }
                    "ul" {
                        "li" { "p" { +"e" } }
                        "li" { "p" { +"f" } }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>
            <p>a</p>
            <ul>
            <li>b</li>
            <li>c</li>
            </ul>
            </li>
            <li>
            <p>d</p>
            <ul>
            <li>e</li>
            <li>f</li>
            </ul>
            </li>
            </ul>
         */
    }

}
