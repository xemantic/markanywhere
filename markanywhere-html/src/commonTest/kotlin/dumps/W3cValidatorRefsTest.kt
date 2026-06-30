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

package com.xemantic.markanywhere.html.dumps

import com.xemantic.kotlin.test.sameAs
import com.xemantic.markanywhere.html.DumpFixtures
import com.xemantic.markanywhere.html.dumpFlow
import com.xemantic.markanywhere.html.transformHtmlToMarkdown
import com.xemantic.markanywhere.parse.parse
import com.xemantic.markanywhere.render.renderMarkdown
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * The default (ref-encoding) rendering of the W3C Nu HTML Checker — a
 * canonical page with a MIX of both ref surfaces: inline links fold their ref
 * into the destination (`[Nu Html Checker](ref:1:.)`), while form controls
 * carry it as a `ref="N"` attribute.
 *
 * Diff this golden against the sibling [W3cValidatorNoRefsTest] (the same dump
 * rendered with `RefMode.STRIP`): every `ref:N:` link prefix AND every
 * `ref="N"` attribute is exactly what disappears.
 */
class W3cValidatorRefsTest {

    @Test
    fun `should render the w3c validator with actionable refs encoded by default`() = runTest {
        // given
        val events = dumpFlow(DumpFixtures.w3cValidator)

        // when — RefMode defaults to ENCODE
        val markdown = events.transformHtmlToMarkdown().renderMarkdown()

        // then — inline links fold the ref into the destination (`ref:N:href`)
        // and form controls carry it as a `ref="N"` attribute
        markdown sameAs /* language=markdown */ """
            ---
            lang: en
            title: Ready to check  - Nu Html Checker
            ---

            # [Nu Html Checker](ref:1:.)

            This tool is an ongoing experiment in better HTML checking, and its behavior remains subject to change

            ## Ready to check

            <form method="get" enctype="">
            <fieldset>
            <legend>

            Checker Input

            </legend>

            Show <label for="showsource"><input id="showsource" type="checkbox" name="showsource" value="yes" ref="2">
            source</label><label for="showoutline"><input id="showoutline" type="checkbox" name="showoutline" value="yes" ref="3">
             outline</label><label for="showimagereport"><input id="showimagereport" type="checkbox" name="showimagereport" value="yes" ref="4">
             image report</label><label for="level"><input id="level" type="checkbox" name="level" value="warning" ref="5">
             errors & warnings only</label><input id="show_options" type="button" value="Options…" ref="6">

            <label id="inputlabel">Check by

            <select id="docselect" ref="7">
            </select>

            </label><input id="doc" type="url" name="doc" placeholder="Enter the URL for an HTML, CSS, or SVG document" required="" pattern="(?:(?:https?://.+)|(?:data:.+))?" aria-label="address" ref="11">

            <input id="submit" type="submit" value="Check" ref="12">

            </fieldset>
            </form>

            ---

            [About this checker](ref:13:about.html) • [Report an issue](ref:14:about.html#issues)
        """.trimIndent()
    }

    @Test
    fun `should round-trip the rendered Markdown with the residual block-tag divergence`() = runTest {
        // given — the Markdown the pipeline produces for the W3C validator dump
        val markdown = dumpFlow(DumpFixtures.w3cValidator).transformHtmlToMarkdown().renderMarkdown()

        // when
        val reparsed = flowOf(markdown).parse()

        // then — every inline `<label>` that spans a SOFT break now round-trips
        // verbatim: the parser keeps a tagged frame open across the break (see
        // SoftBreakRoundTripTest). The RESIDUAL divergence is the harder class the
        // soft-break fix deliberately does NOT cover — a `<label>` that wraps a
        // *block* `<select>` across BLANK lines force-closes at the hard boundary
        // (an inline tag cannot span a paragraph break), with a neighbouring blank
        // line collapsing. Asserting the exact diff pins it down: any new round-trip
        // difference (a regression) changes this message and fails the test.
        try {
            reparsed.renderMarkdown() sameAs markdown
            error("expected the round-trip to diverge")
        } catch (e: AssertionError) {
            e.message sameAs """
                --- expected
                +++ actual
                @@ -23,7 +23,7 @@
                  image report</label><label for="level"><input id="level" type="checkbox" name="level" value="warning" ref="5">
                  errors & warnings only</label><input id="show_options" type="button" value="Options…" ref="6">
                 
                -<label id="inputlabel">Check by
                +<label id="inputlabel">Check by</label>
                 
                 <select id="docselect" ref="7">
                 </select>
                @@ -31,7 +31,6 @@
                 </label><input id="doc" type="url" name="doc" placeholder="Enter the URL for an HTML, CSS, or SVG document" required="" pattern="(?:(?:https?://.+)|(?:data:.+))?" aria-label="address" ref="11">
                 
                 <input id="submit" type="submit" value="Check" ref="12">
                -
                 </fieldset>
                 </form>
                 
                
            """.trimIndent()
        }
    }

}
