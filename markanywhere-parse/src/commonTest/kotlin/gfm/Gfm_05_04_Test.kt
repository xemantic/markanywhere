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

    // TODO review
    @Test
    fun `example 281 - ul with 2 items, ul with 1 item`() = runTest {
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
                    +"foo"
                }
                "li" {
                    +"bar"
                }
            }
            "ul" {
                "li" {
                    +"baz"
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

    // TODO review
    @Test
    fun `example 282 - ol with 2 items, ol with 1 item (start 3)`() = runTest {
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
                    +"foo"
                }
                "li" {
                    +"bar"
                }
            }
            "ol"("start" to "3") {
                "li" {
                    +"baz"
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

    // TODO review
    @Test
    fun `example 283 - paragraph Foo, ul with 2 items`() = runTest {
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
                    +"bar"
                }
                "li" {
                    +"baz"
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

    // TODO review
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

    // TODO review
    @Test
    fun `example 285 - paragraph The number of windows i, ol with 1 item`() = runTest {
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
                    +"The number of doors is 6."
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

    // TODO review
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

    // TODO review
    @Test
    fun `example 287 - ul with 1 item`() = runTest {
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
                    +"foo\n"
                    "ul" {
                        "li" {
                            +"bar\n"
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

    // TODO review
    @Test
    fun `example 288 - two ul with 2 items`() = runTest {
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
                    +"foo"
                }
                "li" {
                    +"bar"
                }
            }
            "ul" {
                "li" {
                    +"baz"
                }
                "li" {
                    +"bim"
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

    // TODO review
    @Test
    fun `example 289 - ul with 2 items, indented code block`() = runTest {
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

    // TODO review
    @Test
    fun `example 290 - ul with 7 items`() = runTest {
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
                "li" {
                    +"a"
                }
                "li" {
                    +"b"
                }
                "li" {
                    +"c"
                }
                "li" {
                    +"d"
                }
                "li" {
                    +"e"
                }
                "li" {
                    +"f"
                }
                "li" {
                    +"g"
                }
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

    // TODO review
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

    // TODO review
    @Test
    fun `example 292 - ul with 4 items`() = runTest {
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
                "li" {
                    +"a"
                }
                "li" {
                    +"b"
                }
                "li" {
                    +"c"
                }
                "li" {
                    +"d\n- e"
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

    // TODO review
    @Test
    fun `example 293 - ol with 2 items, indented code block`() = runTest {
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

    // TODO review
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

    // TODO review
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

    // TODO review
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

    // TODO review
    @Test
    fun `example 297 - ul with 3 items`() = runTest {
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

    // TODO review
    @Test
    fun `example 298 - ul with 3 items`() = runTest {
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
                    +"a"
                }
                "li" {
                    "pre" {
                        "code" {
                            +"b\n\n\n"
                        }
                    }
                }
                "li" {
                    +"c"
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

    // TODO review
    @Test
    fun `example 299 - ul with 2 items`() = runTest {
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
                    +"a\n"
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
                    +"d"
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

    // TODO review
    @Test
    fun `example 300 - ul with 2 items`() = runTest {
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
                    +"a\n"
                    "blockquote" {
                        "p" {
                            +"b"
                        }
                    }
                }
                "li" {
                    +"c"
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

    // TODO review
    @Test
    fun `example 301 - ul with 2 items`() = runTest {
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
                    +"a\n"
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
                    +"d"
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

    // TODO review
    @Test
    fun `example 302 - ul with 1 item`() = runTest {
        // given
        val textFlow = "- a".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    +"a"
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

    // TODO review
    @Test
    fun `example 303 - ul with 1 item`() = runTest {
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
                    +"a\n"
                    "ul" {
                        "li" {
                            +"b"
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

    // TODO review
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

    // TODO review
    @Test
    fun `example 305 - ul with 1 item`() = runTest {
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
                            +"bar"
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

    // TODO review
    @Test
    fun `example 306 - ul with 2 items`() = runTest {
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
                        "li" {
                            +"b"
                        }
                        "li" {
                            +"c"
                        }
                    }
                }
                "li" {
                    "p" {
                        +"d"
                    }
                    "ul" {
                        "li" {
                            +"e"
                        }
                        "li" {
                            +"f"
                        }
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
