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
 * Tests for GFM Section 06.11 — Disallowed Raw HTML (extension).
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#disallowed-raw-html-extension-
 */
@Suppress("ClassName")
class Gfm_06_11_Test {

    @Test
    fun `example 657 - DIVERGENCE disallowed inline tags are dropped not escaped`() = runTest {
        // given
        val textFlow = buildString {
            +"<strong> <title> <style> <em>\n"
            +"\n"
            +"<blockquote>\n"
            +"  <xmp> is disallowed.  <XMP> is also disallowed.\n"
            +"</blockquote>\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then — DIVERGENCE: GFM escapes disallowed tags to visible text
        // (`&lt;title>`); we *drop* them for clean LLM-facing output. An inline
        // `<title>` opener with no matching `</title>` enters raw-text skip mode
        // that consumes the rest of its line — so `<style>` and the trailing
        // `<em>` are swallowed too, leaving `<strong>` wrapping only the single
        // space that preceded `<title>`. (Real captures always close their
        // disallowed tags, so this over-consumption is a synthetic edge.) The
        // `<xmp>` inside the blockquote stays literal because HTML-block
        // detection is suppressed inside blockquotes (it never reaches the
        // inline disallowed-tag dispatch).
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                tag("strong") {
                    +" "
                }
            }
            tag("blockquote") {
                +"\n  <xmp> is disallowed.  <XMP> is also disallowed.\n"
            }
        }
        // GFM expected:
        /*
            <p><strong> &lt;title> &lt;style> <em></p>
            <blockquote>
              &lt;xmp> is disallowed.  &lt;XMP> is also disallowed.
            </blockquote>
         */
    }

}
