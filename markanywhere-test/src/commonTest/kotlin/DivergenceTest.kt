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

package com.xemantic.markanywhere.test

import com.xemantic.kotlin.test.assert
import com.xemantic.kotlin.test.sameAs
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DivergenceTest {

    @Test
    fun `should report the divergence without hunk line numbers`() {
        // given
        val expected = "a\nb\nc\nd\ne\nf\ng\nh\n"
        val actual = "a\nb\nc\nd\ne\nF\ng\nh\n"

        // when
        val divergence = actual.divergenceFrom(expected)

        // then
        divergence sameAs """
            --- expected
            +++ actual
            @@
             c
             d
             e
            -f
            +F
             g
             h
        """.trimIndent()
    }

    @Test
    fun `should report the same divergence when the surrounding output shifts`() {
        // given — the same change, two lines further down
        val shift = "x\ny\n"
        val expected = "a\nb\nc\nd\ne\nf\ng\nh\n"
        val actual = "a\nb\nc\nd\ne\nF\ng\nh\n"

        // when
        val divergence = actual.divergenceFrom(expected)
        val shiftedDivergence = (shift + actual).divergenceFrom(shift + expected)

        // then
        assert(shiftedDivergence == divergence)
    }

    @Test
    fun `should report a blank context line as an empty line`() {
        // given
        val expected = "a\n\nb\n"
        val actual = "a\n\nB\n"

        // when
        val divergence = actual.divergenceFrom(expected)

        // then — the diff writes the blank context line as a lone space; the
        // pinned string must not depend on keeping that trailing whitespace
        assert(" " !in divergence.lines())
        divergence sameAs "--- expected\n+++ actual\n@@\n a\n\n-b\n+B"
    }

    @Test
    fun `should report the same truncated divergence when the surrounding output shifts`() {
        // given — more than 100 changed lines, so sameAs truncates the diff and
        // appends the total line counts of both strings; the shift lands outside
        // the context of the first hunk
        val shift = "x\ny\n"
        val unchanged = (1..5).joinToString("\n", postfix = "\n") { "same $it" }
        val expected = unchanged + (1..120).joinToString("\n", postfix = "\n") { "line $it" }
        val actual = unchanged + (1..120).joinToString("\n", postfix = "\n") { "LINE $it" }

        // when
        val divergence = actual.divergenceFrom(expected)
        val shiftedDivergence = (shift + actual).divergenceFrom(shift + expected)

        // then
        assert("Diff truncated: more than 100 lines changed" in divergence)
        assert(shiftedDivergence == divergence)
    }

    @Test
    fun `should keep the trailing whitespace of a whitespace-only changed line`() {
        // given
        val expected = "a\n  \nb\n"
        val actual = "a\n \nb\n"

        // when
        val divergence = actual.divergenceFrom(expected)

        // then — only blank *context* lines are normalized; a whitespace-only
        // `-`/`+` line is the divergence itself, so the pinned string must keep
        // its trailing whitespace
        divergence sameAs "--- expected\n+++ actual\n@@\n a\n-  \n+ \n b"
    }

    @Test
    fun `should fail when there is no divergence`() {
        // given
        val text = "a\nb\n"

        // when
        val failure = assertFailsWith<AssertionError> {
            text.divergenceFrom(text)
        }

        // then
        assert(failure.message!!.startsWith("expected a divergence, but the strings are identical"))
    }

}
