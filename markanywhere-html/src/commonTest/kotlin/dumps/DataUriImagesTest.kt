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
 * A sweep over the real dumps for issue #88: no image may reach the Markdown
 * with a `data:` URI as its destination — the inline base64 icons of Bing and
 * DuckDuckGo (kilobytes each) render as their alt text only.
 */
class DataUriImagesTest {

    @Test
    fun `should not inline a data uri image into any dump`() = runTest {
        // given — the dumps whose captured pages carry `data:` images
        val dumps = listOf(
            DumpFixtures.serpBing, DumpFixtures.serpDuckduckgo, DumpFixtures.serpGoogle,
        )

        for (dump in dumps) {
            // when
            val markdown = dumpFlow(dump).transformHtmlToMarkdown().renderMarkdown()

            // then
            assert("](data:" !in markdown)
        }
    }

}
