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

package com.xemantic.markanywhere.yaml

import com.xemantic.kotlin.test.assert
import kotlin.test.Test

/**
 * [isYamlKeyLine] is the discriminator the Markdown parser's front matter
 * detection applies to the line after the opening `---`: a mapping key —
 * identifier-shaped or quoted — followed by `:` and whitespace or the end
 * of the line. It is deliberately strict, so a `---` thematic break followed
 * by prose, a heading, a comment or a list is never taken for front matter.
 */
class YamlKeyLineTest {

    @Test
    fun `should accept a mapping key line`() {
        // given
        val lines = listOf(
            "title: Hello", "title:", "_key: v", "date-published: 2026", "page.section: x",
            "título: café", "\"og:title\": x", "'it''s': x", "\"a \\\" b\": x", "key : v", "key:\tv",
        )

        // then
        for (line in lines) assert(isYamlKeyLine(line))
    }

    @Test
    fun `should reject a line that is not a mapping key`() {
        // given
        val lines = listOf(
            "", "# Heading", "http://example.com", "- item", "plain prose", "42: x", "a b: c",
            "title:x", "\"unterminated: x", "key", ": v",
        )

        // then
        for (line in lines) assert(!isYamlKeyLine(line))
    }
}
