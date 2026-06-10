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
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import com.xemantic.markanywhere.transform.transformMatchingMarks
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class BootstrapTest {

    @Test
    fun `should resolve a known Bootstrap icon to an emoji icon`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "a"("href" to "/x") {
                "i"("class" to "bi bi-info-circle") { }
                +" Details"
            }
        }

        // when
        val output = input.transformMatchingMarks(BootstrapIconResolver)

        // then — the <i> subtree is replaced by an untagged icon mark
        output sameAs semanticEvents {
            tag("a", "href" to "/x") {
                "icon" { +"ℹ️" }
                +" Details"
            }
        }
    }

    @Test
    fun `should resolve a glyph through stacked variant suffixes`() = runTest {
        // when — exclamation-triangle-fill canonicalizes to "exclamation"
        val output = semanticEvents(tagged = true) {
            "i"("class" to "bi bi-exclamation-triangle-fill") { }
        }.transformMatchingMarks(BootstrapIconResolver)

        // then
        output sameAs semanticEvents {
            "icon" { +"⚠️" }
        }
    }

    @Test
    fun `should resolve a size variant glyph`() = runTest {
        // when — x-lg canonicalizes to "x"
        val output = semanticEvents(tagged = true) {
            "i"("class" to "bi bi-x-lg") { }
        }.transformMatchingMarks(BootstrapIconResolver)

        // then
        output sameAs semanticEvents {
            "icon" { +"❌" }
        }
    }

    @Test
    fun `should resolve an unmapped recognized glyph to a name hint`() = runTest {
        // when — bi-easel is Bootstrap but has no emoji mapping
        val output = semanticEvents(tagged = true) {
            "i"("class" to "bi bi-easel") { }
        }.transformMatchingMarks(BootstrapIconResolver)

        // then — the derived label keeps the glyph's identity
        output sameAs semanticEvents {
            "icon" { +":easel:" }
        }
    }

    @Test
    fun `should decline a Bootstrap element carrying no glyph name`() {
        // when / then — bare family token, stray bi- prefix, or non-Bootstrap classes
        assert(BootstrapIconResolver(mark("i", "bi")) == null)
        assert(BootstrapIconResolver(mark("i", "bi bi-")) == null)
        assert(BootstrapIconResolver(mark("span", "badge badge-oj")) == null)
    }

    private fun mark(
        name: String,
        classes: String
    ): SemanticEvent.Mark = SemanticEvent.Mark(
        name = name,
        attributes = mapOf("class" to classes),
        isTagged = true
    )
}