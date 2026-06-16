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

package com.xemantic.markanywhere.html

import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Tests the [resolveIcons] chain — icons from different libraries resolving
 * together in one stream. Per-library resolution details are covered by the
 * dedicated tests in the `icons` package.
 */
class IconResolutionTest {

    @Test
    fun `should resolve icons from all supported libraries in one stream`() = runTest {
        // given — four icon libraries mixed in a single document
        val input = semanticEvents(tagged = true) {
            "i"("class" to "fa-solid fa-square-info") { }
            +" Info "
            "i"("class" to "bi bi-exclamation-triangle-fill") { }
            +" Achtung "
            "span"("class" to "material-icons") { +"open_in_new" }
            +" Link "
            "span"("class" to "glyphicon glyphicon-ok-sign") { }
            +" OK"
        }

        // when
        val output = input.resolveIcons()

        // then — each library's resolver claims its own elements
        output sameAs semanticEvents {
            "icon" { +"ℹ️" }
            +" Info "
            "icon" { +"⚠️" }
            +" Achtung "
            "icon" { +"↗️" }
            +" Link "
            "icon" { +"✅" }
            +" OK"
        }
    }

    @Test
    fun `should resolve mixed icons nested in a non-icon wrapper`() = runTest {
        // given — icons from two libraries inside a content wrapper
        val input = semanticEvents(tagged = true) {
            "span"("class" to "wrap") {
                "i"("class" to "fas fa-star") { }
                +" Favorit "
                "i"("class" to "bi bi-heart") { }
            }
        }

        // when
        val output = input.resolveIcons()

        // then — both inner icons resolve, the wrapper span survives
        output sameAs semanticEvents {
            tag("span", "class" to "wrap") {
                "icon" { +"⭐" }
                +" Favorit "
                "icon" { +"❤️" }
            }
        }
    }

    @Test
    fun `should let the earliest matching resolver claim an ambiguous element`() = runTest {
        // when — an element carrying both FontAwesome and Bootstrap classes
        val output = semanticEvents(tagged = true) {
            "i"("class" to "fa-solid fa-star bi bi-heart") { }
        }.resolveIcons()

        // then — FontAwesome precedes Bootstrap in the default chain
        output sameAs semanticEvents {
            "icon" { +"⭐" }
        }
    }

    @Test
    fun `should leave a content-bearing span untouched`() = runTest {
        // given — a badge span carrying text, not an icon
        val input = semanticEvents(tagged = true) {
            "span"("class" to "badge badge-oj") { +"Datenschutzrecht" }
        }

        // when
        val output = input.resolveIcons()

        // then — no resolver in the chain claims it
        output sameAs semanticEvents(tagged = true) {
            "span"("class" to "badge badge-oj") { +"Datenschutzrecht" }
        }
    }

}