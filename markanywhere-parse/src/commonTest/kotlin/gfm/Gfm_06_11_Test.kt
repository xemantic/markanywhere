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
 * Tests for GFM Section 06.11 — Disallowed Raw HTML (extension).
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#disallowed-raw-html-extension-
 */
@Suppress("ClassName")
class Gfm_06_11_Test {

    @Test
    fun `example 657 - paragraph title style blockquote text xmp is disallowed X`() = runTest {
        // given
        val textFlow = buildText {
            +"<strong> <title> <style> <em>\n"
            +"\n"
            +"<blockquote>\n"
            +"  <xmp> is disallowed.  <XMP> is also disallowed.\n"
            +"</blockquote>\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                tag("strong") {
                    +" <title> <style> "
                    tag("em") {
                    }
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
