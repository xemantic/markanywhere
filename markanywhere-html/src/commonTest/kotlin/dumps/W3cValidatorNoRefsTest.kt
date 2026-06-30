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

import com.xemantic.kotlin.test.assert
import com.xemantic.kotlin.test.sameAs
import com.xemantic.markanywhere.html.DumpFixtures
import com.xemantic.markanywhere.html.RefMode
import com.xemantic.markanywhere.html.dumpFlow
import com.xemantic.markanywhere.html.transformHtmlToMarkdown
import com.xemantic.markanywhere.render.renderMarkdown
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * The clean ([RefMode.STRIP]) rendering of the W3C Nu HTML Checker: every
 * actionable ref is dropped across BOTH surfaces — links keep only their real
 * href and form controls carry no `ref` — so nothing references back.
 *
 * Byte-for-byte counterpart of [W3cValidatorRefsTest] (the same dump, default
 * `RefMode.ENCODE`); the only differences are the `ref:N:` link prefixes and
 * the `ref="N"` attributes, all absent here.
 */
class W3cValidatorNoRefsTest {

    @Test
    fun `should render the w3c validator with refs stripped in STRIP mode`() = runTest {
        // given
        val events = dumpFlow(DumpFixtures.w3cValidator)

        // when — RefMode.STRIP drops every actionable ref
        val markdown = events.transformHtmlToMarkdown(refMode = RefMode.STRIP).renderMarkdown()

        // then — no `ref:` link destinations and no `ref="N"` attributes survive
        assert("(ref:" !in markdown)
        assert(" ref=" !in markdown)
        markdown sameAs /* language=markdown */ """
            ---
            lang: en
            title: Ready to check  - Nu Html Checker
            ---

            # [Nu Html Checker](.)

            This tool is an ongoing experiment in better HTML checking, and its behavior remains subject to change

            ## Ready to check

            <form method="get" enctype="">
            <fieldset>
            <legend>

            Checker Input

            </legend>

            Show <label for="showsource"><input id="showsource" type="checkbox" name="showsource" value="yes">
            source</label><label for="showoutline"><input id="showoutline" type="checkbox" name="showoutline" value="yes">
             outline</label><label for="showimagereport"><input id="showimagereport" type="checkbox" name="showimagereport" value="yes">
             image report</label><label for="level"><input id="level" type="checkbox" name="level" value="warning">
             errors & warnings only</label><input id="show_options" type="button" value="Options…">

            <label id="inputlabel">Check by

            <select id="docselect">
            </select>

            </label><input id="doc" type="url" name="doc" placeholder="Enter the URL for an HTML, CSS, or SVG document" required="" pattern="(?:(?:https?://.+)|(?:data:.+))?" aria-label="address">

            <input id="submit" type="submit" value="Check">

            </fieldset>
            </form>

            ---

            [About this checker](about.html) • [Report an issue](about.html#issues)
        """.trimIndent()
    }

}
