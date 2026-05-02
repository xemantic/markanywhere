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

import com.xemantic.kotlin.test.text.chunkedRandomly
import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.parse.parse
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Tests for GFM Section 04.10 — Tables (extension).
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#tables-extension-
 */
@Suppress("ClassName")
class Gfm_04_10_Test {

    @Test
    fun `example 198 - table`() = runTest {
        // given
        val textFlow = """
            | foo | bar |
            | --- | --- |
            | baz | bim |
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "table" {
                "thead" {
                    "tr" {
                        "th" {
                            +"foo"
                        }
                        "th" {
                            +"bar"
                        }
                    }
                }
                "tbody" {
                    "tr" {
                        "td" {
                            +"baz"
                        }
                        "td" {
                            +"bim"
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <table>
            <thead>
            <tr>
            <th>foo</th>
            <th>bar</th>
            </tr>
            </thead>
            <tbody>
            <tr>
            <td>baz</td>
            <td>bim</td>
            </tr>
            </tbody>
            </table>
         */
    }

    // TODO review
    @Test
    fun `example 199 - table`() = runTest {
        // given
        val textFlow = """
            | abc | defghi |
            :-: | -----------:
            bar | baz
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "table" {
                "thead" {
                    "tr" {
                        "th"("align" to "center") {
                            +"abc"
                        }
                        "th"("align" to "right") {
                            +"defghi"
                        }
                    }
                }
                "tbody" {
                    "tr" {
                        "td"("align" to "center") {
                            +"bar"
                        }
                        "td"("align" to "right") {
                            +"baz"
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <table>
            <thead>
            <tr>
            <th align="center">abc</th>
            <th align="right">defghi</th>
            </tr>
            </thead>
            <tbody>
            <tr>
            <td align="center">bar</td>
            <td align="right">baz</td>
            </tr>
            </tbody>
            </table>
         */
    }

    // TODO review
    @Test
    fun `example 200 - table`() = runTest {
        // given
        val textFlow = """
            | f\|oo  |
            | ------ |
            | b `\|` az |
            | b **\|** im |
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "table" {
                "thead" {
                    "tr" {
                        "th" {
                            +"f|oo"
                        }
                    }
                }
                "tbody" {
                    "tr" {
                        "td" {
                            +"b "
                            "code" {
                                +"|"
                            }
                            +" az"
                        }
                    }
                    "tr" {
                        "td" {
                            +"b "
                            "strong" {
                                +"|"
                            }
                            +" im"
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <table>
            <thead>
            <tr>
            <th>f|oo</th>
            </tr>
            </thead>
            <tbody>
            <tr>
            <td>b <code>|</code> az</td>
            </tr>
            <tr>
            <td>b <strong>|</strong> im</td>
            </tr>
            </tbody>
            </table>
         */
    }

    // TODO review
    @Test
    fun `example 201 - table, blockquote (text , paragraph bar, text )`() = runTest {
        // given
        val textFlow = """
            | abc | def |
            | --- | --- |
            | bar | baz |
            > bar
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "table" {
                "thead" {
                    "tr" {
                        "th" {
                            +"abc"
                        }
                        "th" {
                            +"def"
                        }
                    }
                }
                "tbody" {
                    "tr" {
                        "td" {
                            +"bar"
                        }
                        "td" {
                            +"baz"
                        }
                    }
                }
            }
            "blockquote" {
                "p" {
                    +"bar"
                }
            }
        }
        // GFM expected:
        /*
            <table>
            <thead>
            <tr>
            <th>abc</th>
            <th>def</th>
            </tr>
            </thead>
            <tbody>
            <tr>
            <td>bar</td>
            <td>baz</td>
            </tr>
            </tbody>
            </table>
            <blockquote>
            <p>bar</p>
            </blockquote>
         */
    }

    // TODO review
    @Test
    fun `example 202 - table, paragraph bar`() = runTest {
        // given
        val textFlow = """
            | abc | def |
            | --- | --- |
            | bar | baz |
            bar

            bar
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "table" {
                "thead" {
                    "tr" {
                        "th" {
                            +"abc"
                        }
                        "th" {
                            +"def"
                        }
                    }
                }
                "tbody" {
                    "tr" {
                        "td" {
                            +"bar"
                        }
                        "td" {
                            +"baz"
                        }
                    }
                    "tr" {
                        "td" {
                            +"bar"
                        }
                        "td" {
                        }
                    }
                }
            }
            "p" {
                +"bar"
            }
        }
        // GFM expected:
        /*
            <table>
            <thead>
            <tr>
            <th>abc</th>
            <th>def</th>
            </tr>
            </thead>
            <tbody>
            <tr>
            <td>bar</td>
            <td>baz</td>
            </tr>
            <tr>
            <td>bar</td>
            <td></td>
            </tr>
            </tbody>
            </table>
            <p>bar</p>
         */
    }

    // TODO review
    @Test
    fun `example 203 - paragraph abc def ---`() = runTest {
        // given
        val textFlow = """
            | abc | def |
            | --- |
            | bar |
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"| abc | def |\n| --- |\n| bar |"
            }
        }
        // GFM expected:
        /*
            <p>| abc | def |
            | --- |
            | bar |</p>
         */
    }

    // TODO review
    @Test
    fun `example 204 - table`() = runTest {
        // given
        val textFlow = """
            | abc | def |
            | --- | --- |
            | bar |
            | bar | baz | boo |
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "table" {
                "thead" {
                    "tr" {
                        "th" {
                            +"abc"
                        }
                        "th" {
                            +"def"
                        }
                    }
                }
                "tbody" {
                    "tr" {
                        "td" {
                            +"bar"
                        }
                        "td" {
                        }
                    }
                    "tr" {
                        "td" {
                            +"bar"
                        }
                        "td" {
                            +"baz"
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <table>
            <thead>
            <tr>
            <th>abc</th>
            <th>def</th>
            </tr>
            </thead>
            <tbody>
            <tr>
            <td>bar</td>
            <td></td>
            </tr>
            <tr>
            <td>bar</td>
            <td>baz</td>
            </tr>
            </tbody>
            </table>
         */
    }

    // TODO review
    @Test
    fun `example 205 - table`() = runTest {
        // given
        val textFlow = """
            | abc | def |
            | --- | --- |
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "table" {
                "thead" {
                    "tr" {
                        "th" {
                            +"abc"
                        }
                        "th" {
                            +"def"
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <table>
            <thead>
            <tr>
            <th>abc</th>
            <th>def</th>
            </tr>
            </thead>
            </table>
         */
    }

}
