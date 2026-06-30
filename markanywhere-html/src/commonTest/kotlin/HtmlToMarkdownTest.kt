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

import com.xemantic.kotlin.test.assert
import com.xemantic.kotlin.test.sameAs
import com.xemantic.markanywhere.dump.AccessibilityAnnotations
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.render.renderMarkdown
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class HtmlToMarkdownTest {

    @Test
    fun `should convert the README HTML to Markdown example`() = runTest {
        // given — a captured page fragment: a presentational wrapper, an icon
        // font, a tracking script, and the real content all mixed together
        val page = semanticEvents(tagged = true) {
            "body" {
                "h1" { +"Weather" }
                "p" {
                    "i"("class" to "fa-solid fa-sun") { }
                    +" Sunny and "
                    "strong" { +"warm" }
                    +" today — see the "
                    "a"("href" to "https://example.com/forecast") { +"forecast" }
                    +"."
                }
                "script" { +"track('view')" }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown().renderMarkdown()

        // then
        markdown sameAs """
            # Weather
            
            ☀️ Sunny and **warm** today — see the [forecast](https://example.com/forecast).
        """.trimIndent()
    }

    @Test
    fun `should strip actionable refs in STRIP mode`() = runTest {
        // given — both ref surfaces in one hand-built input: a ref-bearing
        // inline link (folds into the `ref:` destination) and a ref-bearing
        // block-wrapping link — a link around an <h2>, which renders as a raw
        // <a> tag carrying a `ref=` attribute. RefMode.STRIP must drop both.
        val page = semanticEvents(tagged = true) {
            "body" {
                "p" {
                    +"See the "
                    "a"("href" to "https://example.com", AccessibilityAnnotations.REF to "7") { +"site" }
                    +"."
                }
                "a"("href" to "/live", AccessibilityAnnotations.REF to "9") {
                    "h2" { +"Headline" }
                }
            }
        }

        // when — RefMode.STRIP drops the dump's refs entirely
        val markdown = page.transformHtmlToMarkdown(refMode = RefMode.STRIP).renderMarkdown()

        // then — no `ref:` scheme destinations and no `ref=` attributes anywhere;
        // links keep their real href as standard Markdown
        assert("ref:" !in markdown)
        assert(" ref=" !in markdown)
        markdown sameAs """
            See the [site](https://example.com).
            
            <a href="/live">
            
            ## Headline
            
            </a>
        """.trimIndent()
    }

    @Test
    fun `should not let an explicit REF in keepAttributes bypass STRIP`() = runTest {
        // given — a ref-bearing block-wrapping link (rendered as a raw <a> tag, so
        // a surviving REF would leak as a `data-markanywhere-ref` attribute), with
        // the caller contradictorily asking to keep REF *and* selecting STRIP
        val page = semanticEvents(tagged = true) {
            "body" {
                "a"("href" to "/live", AccessibilityAnnotations.REF to "9") {
                    "h2" { +"Headline" }
                }
            }
        }

        // when — STRIP wins: REF is removed from keepAttributes too
        val markdown = page.transformHtmlToMarkdown(
            keepAttributes = setOf(AccessibilityAnnotations.REF),
            refMode = RefMode.STRIP,
        ).renderMarkdown()

        // then — the raw `data-markanywhere-ref` attribute does not leak through
        assert(AccessibilityAnnotations.REF !in markdown)
        assert(" ref=" !in markdown)
        markdown sameAs """
            <a href="/live">
            
            ## Headline
            
            </a>
        """.trimIndent()
    }

}
