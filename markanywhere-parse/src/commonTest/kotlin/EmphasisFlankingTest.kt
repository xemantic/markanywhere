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

package com.xemantic.markanywhere.parse

import com.xemantic.kotlin.test.text.chunkedRandomly
import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Emphasis flanking counts only ASCII punctuation — a deliberate divergence
 * from GFM 0.29, which also counts the Unicode categories `Pc`, `Pd`, `Ps`,
 * `Pe`, `Pi`, `Pf` and `Po` (issue #164). Under GFM's class the closers below
 * are not right-flanking, and since the parser opens a span eagerly, the span
 * would run to the end of the line instead of staying literal; the ASCII class
 * keeps the bold the author (or the LLM) meant.
 */
class EmphasisFlankingTest {

    @Test
    fun `DIVERGENCE - should close strong around CJK brackets between CJK text`() = runTest {
        // given
        val textFlow = "日本**「重要」**です".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"日本"
                "strong" { +"「重要」" }
                +"です"
            }
        }
        // GFM expected:
        /*
            <p>日本**「重要」**です</p>
         */
    }

    @Test
    fun `DIVERGENCE - should close strong ending in a CJK colon before CJK text`() = runTest {
        // given
        val textFlow = "**注意：**内容".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" { +"注意：" }
                +"内容"
            }
        }
        // GFM expected:
        /*
            <p>**注意：**内容</p>
         */
    }

    @Test
    fun `DIVERGENCE - should close strong around curly quotes before a word char`() = runTest {
        // given
        val textFlow = "He said **“Hi”**to me".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"He said "
                "strong" { +"“Hi”" }
                +"to me"
            }
        }
        // GFM expected:
        /*
            <p>He said **“Hi”**to me</p>
         */
    }

    @Test
    fun `DIVERGENCE - should open strong on a curly quote after a word char`() = runTest {
        // given
        val textFlow = "foo**“bar”**baz".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo"
                "strong" { +"“bar”" }
                +"baz"
            }
        }
        // GFM expected:
        /*
            <p>foo**“bar”**baz</p>
         */
    }

    @Test
    fun `DIVERGENCE - should open em on a guillemet after a word char`() = runTest {
        // given
        val textFlow = "a*«b»*c".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"a"
                "em" { +"«b»" }
                +"c"
            }
        }
        // GFM expected:
        /*
            <p>a*«b»*c</p>
         */
    }

    @Test
    fun `should not open strong on ASCII punctuation after a word char`() = runTest {
        // given
        val textFlow = "foo**:bar:**".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"foo**:bar:**" }
        }
    }

    @Test
    fun `should open strong on a currency symbol after a word char`() = runTest {
        // given
        val textFlow = "foo**€5**bar".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo"
                "strong" { +"€5" }
                +"bar"
            }
        }
    }

}
