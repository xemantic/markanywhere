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

class GlyphiconsTest {

    @Test
    fun `should resolve a known Glyphicon to an emoji icon`() = runTest {
        // given
        val input = semanticEvents(tagged = true) {
            "span"("class" to "glyphicon glyphicon-info-sign") { }
            +" Hinweis"
        }

        // when
        val output = input.transformMatchingMarks(GlyphiconResolver)

        // then — the <span> subtree is replaced by an untagged icon mark
        output sameAs semanticEvents {
            "icon" { +"ℹ️" }
            +" Hinweis"
        }
    }

    @Test
    fun `should resolve a glyph class without the bare family token`() = runTest {
        // when — the glyphicon-<name> token alone proves the family
        val output = semanticEvents(tagged = true) {
            "span"("class" to "glyphicon-info-sign") { }
        }.transformMatchingMarks(GlyphiconResolver)

        // then
        output sameAs semanticEvents {
            "icon" { +"ℹ️" }
        }
    }

    @Test
    fun `should resolve a glyph through decorative-suffix canonicalization`() = runTest {
        // when — ok-circle canonicalizes to "ok"
        val output = semanticEvents(tagged = true) {
            "span"("class" to "glyphicon glyphicon-ok-circle") { }
        }.transformMatchingMarks(GlyphiconResolver)

        // then
        output sameAs semanticEvents {
            "icon" { +"✅" }
        }
    }

    @Test
    fun `should resolve an empty-variant glyph`() = runTest {
        // when — star-empty canonicalizes to "star"
        val output = semanticEvents(tagged = true) {
            "span"("class" to "glyphicon glyphicon-star-empty") { }
        }.transformMatchingMarks(GlyphiconResolver)

        // then
        output sameAs semanticEvents {
            "icon" { +"⭐" }
        }
    }

    @Test
    fun `should resolve an unmapped recognized glyph to a name hint`() = runTest {
        // when — glyphicon-screenshot is Glyphicons but has no emoji mapping
        val output = semanticEvents(tagged = true) {
            "span"("class" to "glyphicon glyphicon-screenshot") { }
        }.transformMatchingMarks(GlyphiconResolver)

        // then — the derived label keeps the glyph's identity
        output sameAs semanticEvents {
            "icon" { +":screenshot:" }
        }
    }

    @Test
    fun `should decline a Glyphicons element carrying no glyph name`() {
        // when / then — bare family token, stray glyphicon- prefix, or non-Glyphicons classes
        assert(GlyphiconResolver(mark("span", "glyphicon")) == null)
        assert(GlyphiconResolver(mark("span", "glyphicon glyphicon-")) == null)
        assert(GlyphiconResolver(mark("span", "badge badge-oj")) == null)
    }

}
