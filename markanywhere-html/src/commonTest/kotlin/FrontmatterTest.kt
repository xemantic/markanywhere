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
import kotlin.test.Test

/**
 * The flat YAML / TOML front-matter codec shared by [simplifyHtml],
 * [wrapInHtmlDocument] and [ensureFrontmatterTitle]. Its writing and reading
 * halves must stay exact inverses, so the tests below assert the round-trip
 * rather than either side's literal output.
 */
class FrontmatterTest {

    @Test
    fun `should round-trip a plain title through YAML and TOML`() {
        // when
        val yaml = parseFlatFrontmatter(titleLine("Hello", "yaml"), "yaml")
        val toml = parseFlatFrontmatter(titleLine("Hello", "toml"), "toml")

        // then
        assert(yaml == mapOf("title" to "Hello"))
        assert(toml == mapOf("title" to "Hello"))
    }

    @Test
    fun `should round-trip a title containing quotes and a backslash`() {
        // given
        val title = """a "b" c\d"""

        // when
        val yaml = parseFlatFrontmatter(titleLine(title, "yaml"), "yaml")
        val toml = parseFlatFrontmatter(titleLine(title, "toml"), "toml")

        // then
        assert(yaml == mapOf("title" to title))
        assert(toml == mapOf("title" to title))
    }

    @Test
    fun `should escape control characters so a title stays on one line`() {
        // given — a raw newline would terminate the entry mid-value and a raw
        // tab / carriage return is invalid inside a YAML double-quoted scalar
        // and a TOML basic string alike
        val title = "a\nb\tc\rd"

        // when
        val yamlLine = titleLine(title, "yaml")
        val tomlLine = titleLine(title, "toml")

        // then — one line each, and both decode back to the original
        assert(yamlLine.trimEnd('\n').lineSequence().count() == 1)
        assert(tomlLine.trimEnd('\n').lineSequence().count() == 1)
        assert(parseFlatFrontmatter(yamlLine, "yaml") == mapOf("title" to title))
        assert(parseFlatFrontmatter(tomlLine, "toml") == mapOf("title" to title))
    }

    @Test
    fun `should round-trip a rendered metadata map through YAML`() {
        // given — an `og:` key needs quoting (colon), a version-like value
        // needs quoting (YAML reserved literal shape is checked too)
        val metadata = mapOf(
            "title" to "Hello: world",
            "og:image" to "https://example.com/a.png",
            "draft" to "true",
        )

        // when
        val parsed = parseFlatFrontmatter(renderYamlFrontmatter(metadata), "yaml")

        // then
        assert(parsed == metadata)
    }

    @Test
    fun `should detect a top-level title key only`() {
        // when, then
        assert(hasTitle("title: Hello\n", "yaml"))
        assert(!hasTitle("  title: Hello\n", "yaml"))
        assert(hasTitle("title = \"Hello\"\n", "toml"))
        assert(!hasTitle("author = \"Alice\"\n", "toml"))
    }
}
