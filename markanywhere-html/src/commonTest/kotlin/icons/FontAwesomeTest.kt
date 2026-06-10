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

class FontAwesomeTest {

    @Test
    fun `should resolve a known FontAwesome glyph to an emoji icon`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "a"("href" to "/x") {
                "i"("class" to "fa-solid fa-square-info") { }
                +" Versionen"
            }
        }

        // when
        val output = input.transformMatchingMarks(FontAwesomeIconResolver)

        // then — the <i> subtree is replaced by an untagged icon mark
        output sameAs semanticEvents {
            tag("a", "href" to "/x") {
                "icon" { +"ℹ️" }
                +" Versionen"
            }
        }
    }

    @Test
    fun `should resolve a glyph through decorative-affix canonicalization`() = runTest {
        // when — fa-exclamation-circle canonicalizes to "exclamation"
        val output = semanticEvents(tagged = true) {
            "i"("class" to "fas fa-exclamation-circle") { }
        }.transformMatchingMarks(FontAwesomeIconResolver)

        // then
        output sameAs semanticEvents {
            "icon" { +"⚠️" }
        }
    }

    @Test
    fun `should skip style and modifier tokens when picking the glyph`() = runTest {
        // when — sharp/solid/fw/2x/spin are modifiers, the glyph is fa-star
        val output = semanticEvents(tagged = true) {
            "i"("class" to "fa-sharp fa-solid fa-fw fa-2x fa-spin fa-star") { }
        }.transformMatchingMarks(FontAwesomeIconResolver)

        // then
        output sameAs semanticEvents {
            "icon" { +"⭐" }
        }
    }

    @Test
    fun `should resolve an unmapped recognized glyph to a name hint`() = runTest {
        // when — fa-square-quote is FontAwesome but has no emoji mapping
        val output = semanticEvents(tagged = true) {
            "i"("class" to "fa-solid fa-square-quote") { }
        }.transformMatchingMarks(FontAwesomeIconResolver)

        // then — the derived label keeps the glyph's identity
        output sameAs semanticEvents {
            "icon" { +":square-quote:" }
        }
    }

    @Test
    fun `should resolve a nested icon while preserving the enclosing span`() = runTest {
        // given — an icon nested inside a non-icon wrapper span
        val input = semanticEvents(tagged = true) {
            "span"("class" to "wrap") {
                "i"("class" to "fas fa-star") { }
                +" Fav"
            }
        }

        // when
        val output = input.transformMatchingMarks(FontAwesomeIconResolver)

        // then — the inner icon resolves, the span survives untouched
        output sameAs semanticEvents {
            tag("span", "class" to "wrap") {
                "icon" { +"⭐" }
                +" Fav"
            }
        }
    }

    @Test
    fun `should leave a content-bearing non-icon span untouched`() = runTest {
        // given — a badge span carrying text, not an icon
        val input = semanticEvents(tagged = true) {
            "span"("class" to "badge badge-oj") { +"Datenschutzrecht" }
        }

        // when
        val output = input.transformMatchingMarks(FontAwesomeIconResolver)

        // then
        output sameAs semanticEvents(tagged = true) {
            "span"("class" to "badge badge-oj") { +"Datenschutzrecht" }
        }
    }

    @Test
    fun `should decline a FontAwesome element carrying no usable glyph name`() {
        // when / then — family or modifier classes only, or a stray fa- prefix
        assert(FontAwesomeIconResolver(mark("i", "fa fa-fw")) == null)
        assert(FontAwesomeIconResolver(mark("i", "fa-solid fa-")) == null)
        assert(FontAwesomeIconResolver(mark("span", "badge badge-oj")) == null)
    }

}