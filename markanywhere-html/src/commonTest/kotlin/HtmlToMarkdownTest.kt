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

import com.xemantic.kotlin.test.sameAs
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

}