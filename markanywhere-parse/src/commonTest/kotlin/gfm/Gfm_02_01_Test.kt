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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Tests for GFM Section 2.1 - Characters and lines.
 *
 * Reference: https://github.github.com/gfm/#characters-and-lines
 *
 * The GFM spec contains no numbered examples for this section. The prose
 * defines:
 *   - what counts as a *character* (Unicode code point),
 *   - what counts as a *line ending* (LF, CRLF, or a lone CR),
 *   - the distinction between a CommonMark *whitespace character*
 *     (space, tab, newline, line tabulation, form feed, carriage return)
 *     and a *Unicode whitespace character* (any code point with the
 *     Unicode `Zs` general category, plus tab, LF, FF, CR),
 *   - the *Unicode punctuation* class used by inline flanking rules.
 *
 * The tests below smoke-cover those rules so the section has explicit
 * coverage independent of the example-driven sections that lean on it.
 */
@Suppress("ClassName")
class Gfm_02_01_Test {

    @Test
    fun `LF line endings produce expected events`() = runTest {
        // given
        val textFlow = "foo\n\nbar\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"foo" }
            "p" { +"bar" }
        }
    }

    @Test
    fun `CRLF line endings produce same events as LF`() = runTest {
        // given
        val textFlow = "foo\r\n\r\nbar\r\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"foo" }
            "p" { +"bar" }
        }
    }

    @Test
    fun `lone CR line endings produce same events as LF`() = runTest {
        // given
        val textFlow = "foo\r\rbar\r".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"foo" }
            "p" { +"bar" }
        }
    }

    @Test
    fun `CRLF split across chunk boundary is one line ending`() = runTest {
        // given
        // The chunk boundary falls between CR and LF. A streaming parser must
        // collapse the pair into a single line ending instead of treating each
        // half as its own. flowOf is used (not chunkedRandomly) so the seam
        // position is deterministic.
        val textFlow = flowOf("foo\r", "\nbar\n")

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"foo\nbar" }
        }
    }

    @Test
    fun `final line ending is optional`() = runTest {
        // given
        // CommonMark: "The last line of a document need not end in a line ending."
        val textFlow = "foo".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"foo" }
        }
    }

    @Test
    fun `non-breaking space does not count toward block indentation`() = runTest {
        // given
        // U+00A0 NBSP is *Unicode* whitespace but NOT a CommonMark whitespace
        // character. Four NBSPs at line start must therefore NOT trigger an
        // indented code block, even though four regular spaces would.
        val textFlow = "\u00A0\u00A0\u00A0\u00A0foo\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"\u00A0\u00A0\u00A0\u00A0foo" }
        }
    }

    @Test
    fun `Unicode punctuation around emphasis allows flanking`() = runTest {
        // given
        // Smoke test for the Unicode-punctuation classification that §6.4
        // emphasis flanking rules depend on. Guillemets ("«", "»")
        // are Unicode punctuation, so *foo* sandwiched between them must still
        // emphasize.
        val textFlow = "«*foo*»".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"«"
                "em" { +"foo" }
                +"»"
            }
        }
    }

}
