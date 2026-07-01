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
import kotlin.test.assertFailsWith

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
    fun `should round-trip the rendered Markdown with the captured divergence`() = runTest {
        // given — the Markdown the pipeline produces for the W3C validator dump
        val markdown = dumpFlow(DumpFixtures.w3cValidator).transformHtmlToMarkdown().renderMarkdown()

        // when
        val reparsed = flowOf(markdown).parse()

        // then — re-parsing does NOT reproduce the pipeline Markdown verbatim. The
        // form's interleaved `<label>`/`<input>` re-nests on re-parse (the parser
        // auto-closes the labels and strips the leading spaces it added) — the same
        // class of inline-HTML divergence the SERP dumps carry. Asserting the exact
        // diff pins it down: any new round-trip difference (a regression) changes
        // this message and fails the test.
        val divergence = assertFailsWith<AssertionError> {
            reparsed.renderMarkdown() sameAs markdown
        }
        divergence.message sameAs """
            --- expected
            +++ actual
            @@ -18,12 +18,16 @@
             </legend>
             
             Show <label for="showsource"><input id="showsource" type="checkbox" name="showsource" value="yes" ref="2">
            +</label>
             source</label><label for="showoutline"><input id="showoutline" type="checkbox" name="showoutline" value="yes" ref="3">
            - outline</label><label for="showimagereport"><input id="showimagereport" type="checkbox" name="showimagereport" value="yes" ref="4">
            - image report</label><label for="level"><input id="level" type="checkbox" name="level" value="warning" ref="5">
            - errors & warnings only</label><input id="show_options" type="button" value="Options…" ref="6">
            +</label>
            +outline</label><label for="showimagereport"><input id="showimagereport" type="checkbox" name="showimagereport" value="yes" ref="4">
            +</label>
            +image report</label><label for="level"><input id="level" type="checkbox" name="level" value="warning" ref="5">
            +</label>
            +errors & warnings only</label><input id="show_options" type="button" value="Options…" ref="6">
             
            -<label id="inputlabel">Check by
            +<label id="inputlabel">Check by</label>
             
             <select id="docselect" ref="7">
             </select>
            @@ -31,7 +35,6 @@
             </label><input id="doc" type="url" name="doc" placeholder="Enter the URL for an HTML, CSS, or SVG document" required="" pattern="(?:(?:https?://.+)|(?:data:.+))?" aria-label="address" ref="11">
             
             <input id="submit" type="submit" value="Check" ref="12">
            -
             </fieldset>
             </form>
             
            
        """.trimIndent()
    }

}
