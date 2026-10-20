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
import com.xemantic.markanywhere.render.renderMarkdown
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * A sweep over the real dumps for issues #88 and #142: no `data:` payload may
 * reach the Markdown — not as an image destination, not on a raw `<img>`, not
 * in a `poster` or an `<object data>` — the inline base64 icons of Bing and
 * DuckDuckGo (kilobytes each) render as their alt text only.
 */
class DataUriImagesTest {

    @Test
    fun `should not inline a data uri payload into any dump`() = runTest {
        // given — every dump; Bing, DuckDuckGo and Google carry `data:` images
        val dumps = DumpFixtures.byFileName

        // when
        val leaks = dumps.mapValues { (_, dump) ->
            val markdown = dumpFlow(dump).transformHtmlToMarkdown().renderMarkdown()
            DATA_URI_PAYLOAD.find(markdown)?.value
        }.filterValues { it != null }

        // then — a media type (or its empty form, `data:,` / `data:;base64,`)
        // marks a payload, not prose or a form pattern mentioning the scheme
        // (`(?:data:.+)` on the W3C validator)
        assert(dumps.size >= 8)
        assert(leaks.isEmpty())
    }

}

private val DATA_URI_PAYLOAD = Regex("data:(?:[a-z]+/[a-z0-9.+-]+)?[;,]", RegexOption.IGNORE_CASE)
