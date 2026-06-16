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

package com.xemantic.markanywhere.html.icons

import com.xemantic.kotlin.test.assert
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import com.xemantic.markanywhere.transform.transformMatchingMarks
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MaterialDesignTest {

    @Test
    fun `should resolve a Material ligature icon from its text content`() = runTest {
        // given — Material Icons encode the glyph as text, with `_` separators
        val input = semanticEvents(tagged = true) {
            "span"("class" to "material-icons") { +"open_in_new" }
        }

        // when
        val output = input.transformMatchingMarks(MaterialIconResolver)

        // then
        output sameAs semanticEvents {
            "icon" { +"↗️" }
        }
    }

    @Test
    fun `should match a Material Symbols class variant`() = runTest {
        // when
        val output = semanticEvents(tagged = true) {
            "span"("class" to "material-symbols-outlined") { +"info" }
        }.transformMatchingMarks(MaterialIconResolver)

        // then
        output sameAs semanticEvents {
            "icon" { +"ℹ️" }
        }
    }

    @Test
    fun `should resolve a ligature variant through suffix canonicalization`() = runTest {
        // when — star_border canonicalizes to "star"
        val output = semanticEvents(tagged = true) {
            "span"("class" to "material-icons") { +"star_border" }
        }.transformMatchingMarks(MaterialIconResolver)

        // then
        output sameAs semanticEvents {
            "icon" { +"⭐" }
        }
    }

    @Test
    fun `should resolve an unmapped ligature to a name hint`() = runTest {
        // when — manage_accounts is Material but has no emoji mapping
        val output = semanticEvents(tagged = true) {
            "span"("class" to "material-icons") { +"manage_accounts" }
        }.transformMatchingMarks(MaterialIconResolver)

        // then — the derived label keeps the glyph's identity, `-`-normalized
        output sameAs semanticEvents {
            "icon" { +":manage-accounts:" }
        }
    }

    @Test
    fun `should replay a matched element with no ligature text verbatim`() = runTest {
        // given — a material class on an element carrying no glyph text
        val input = semanticEvents(tagged = true) {
            "span"("class" to "material-icons") { }
        }

        // when
        val output = input.transformMatchingMarks(MaterialIconResolver)

        // then — not an icon carrier; the subtree survives untouched
        output sameAs semanticEvents(tagged = true) {
            "span"("class" to "material-icons") { }
        }
    }

    @Test
    fun `should decline an element without Material classes`() {
        // when / then — the select never matches, regardless of text content
        assert(MaterialIconResolver(mark("span", "badge badge-oj")) == null)
        assert(MaterialIconResolver(mark("i", "fa-solid fa-star")) == null)
    }

}
