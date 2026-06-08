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

import com.xemantic.kotlin.core.text.unaryPlus
import com.xemantic.kotlin.test.text.chunkedRandomly
import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.parse.parse
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Tests for GFM Section 04.08 — Paragraphs.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#paragraphs
 */
@Suppress("ClassName")
class Gfm_04_08_Test {

    @Test
    fun `example 189 - paragraph aaa paragraph bbb`() = runTest {
        // given
        val textFlow = """
            aaa

            bbb
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"aaa"
            }
            "p" {
                +"bbb"
            }
        }
        // GFM expected:
        /*
            <p>aaa</p>
            <p>bbb</p>
         */
    }

    @Test
    fun `example 190 - paragraph aaa bbb paragraph ccc ddd`() = runTest {
        // given
        val textFlow = """
            aaa
            bbb

            ccc
            ddd
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"aaa\nbbb"
            }
            "p" {
                +"ccc\nddd"
            }
        }
        // GFM expected:
        /*
            <p>aaa
            bbb</p>
            <p>ccc
            ddd</p>
         */
    }

    @Test
    fun `example 191 - paragraph aaa paragraph bbb`() = runTest {
        // given
        val textFlow = """
            aaa


            bbb
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"aaa"
            }
            "p" {
                +"bbb"
            }
        }
        // GFM expected:
        /*
            <p>aaa</p>
            <p>bbb</p>
         */
    }

    @Test
    fun `example 192 - paragraph aaa bbb`() = runTest {
        // given
        val textFlow = buildString {
            +"  aaa\n"
            +" bbb\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"aaa\nbbb"
            }
        }
        // GFM expected:
        /*
            <p>aaa
            bbb</p>
         */
    }

    @Test
    fun `example 193 - paragraph aaa bbb ccc`() = runTest {
        // given
        val textFlow = buildString {
            +"aaa\n"
            +"             bbb\n"
            +"                                       ccc\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"aaa\nbbb\nccc"
            }
        }
        // GFM expected:
        /*
            <p>aaa
            bbb
            ccc</p>
         */
    }

    @Test
    fun `example 194 - paragraph aaa bbb`() = runTest {
        // given
        val textFlow = buildString {
            +"   aaa\n"
            +"bbb\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"aaa\nbbb"
            }
        }
        // GFM expected:
        /*
            <p>aaa
            bbb</p>
         */
    }

    @Test
    fun `example 195 - indented code block paragraph bbb`() = runTest {
        // given
        val textFlow = buildString {
            +"    aaa\n"
            +"bbb\n"
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
            "p" {
                +"bbb"
            }
        }
        // GFM expected:
        /*
            <pre><code>aaa
            </code></pre>
            <p>bbb</p>
         */
    }

    @Test
    fun `example 196 - paragraph aaa bbb`() = runTest {
        // given
        val textFlow = buildString {
            +"aaa     \n"
            +"bbb     \n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"aaa"
                "br" {}
                +"\nbbb"
            }
        }
        // GFM expected:
        /*
            <p>aaa<br />
            bbb</p>
         */
    }

}
