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

package com.xemantic.markanywhere.parse

import com.xemantic.kotlin.test.text.chunkedRandomly
import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Specifies streaming front matter recognition and its structured event
 * representation.
 *
 * Front matter is **auto-detected**: a document that begins with `---` at
 * byte 0, followed by a line-2 line that is a mapping key line (an
 * identifier-shaped or quoted key, then `:` and whitespace or the end of
 * the line — `isYamlKeyLine` in `markanywhere-yaml`, e.g. `title:`), opens
 * a `frontmatter` block. The discriminator is what disqualifies a legitimate
 * `---` thematic break followed by a paragraph or any other Markdown
 * structure. Only YAML is recognised — a `+++` (TOML) fence is ordinary
 * Markdown.
 *
 * The body is parsed as YAML into **structured events** — the untagged
 * `frontmatter` mark holds the `entry` / `item` marks emitted by
 * `markanywhere-yaml`'s `YamlParser` (whose own suite, `YamlParserTest`,
 * specifies the YAML subset and the event vocabulary). The tests here cover
 * the fences: detection, the closer, line endings and the hand-off between
 * the front matter and the Markdown body.
 */
class FrontMatterTest {

    @Test
    fun `should parse YAML front matter at document start`() = runTest {
        // given
        val textFlow = """
            ---
            title: Hello
            author: Alice
            ---
            # Heading

            Body paragraph.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Hello" }
                "entry"("key" to "author") { +"Alice" }
            }
            "h1" { +"Heading" }
            "p" { +"Body paragraph." }
        }
    }

    @Test
    fun `should not recognise a TOML fence as front matter`() = runTest {
        // given — only YAML is supported; `+++` is ordinary paragraph text
        val textFlow = """
            +++
            title = "Hello"
            +++
            Body paragraph.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"+++\ntitle = \"Hello\"\n+++\nBody paragraph." }
        }
    }

    @Test
    fun `should parse front matter as the entire document`() = runTest {
        // given
        val textFlow = """
            ---
            title: Standalone
            ---
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Standalone" }
            }
        }
    }

    @Test
    fun `should not interpret Markdown syntax inside front matter values`() = runTest {
        // given
        val textFlow = """
            ---
            title: "**Bold** _and_ # Hash"
            list:
              - one
              - two
            ---
            # Real Heading
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"**Bold** _and_ # Hash" }
                "entry"("key" to "list") {
                    "item" { +"one" }
                    "item" { +"two" }
                }
            }
            "h1" { +"Real Heading" }
        }
    }

    @Test
    fun `should not treat fenced-code-like block scalar content as a code block`() = runTest {
        // given — triple backticks inside front matter must NOT open a fence
        val textFlow = """
            ---
            content: |
              ```
              not-a-fence
              ```
            ---
            Body.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "content") { +"```\nnot-a-fence\n```\n" }
            }
            "p" { +"Body." }
        }
    }

    @Test
    fun `should DIVERGENCE auto-close unterminated front matter at EOF`() = runTest {
        // given — opener but no closer. DIVERGENCE: spec-correct behavior would
        // require buffering the whole document to detect the missing closer
        // and fall back to a thematic break + paragraphs. The streaming parser
        // commits the `frontmatter` open mark eagerly and force-closes at
        // finalize, mirroring how unmatched code-span openers behave.
        val textFlow = """
            ---
            title: Forgotten
            This still flows into front matter because no closer arrived.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Forgotten" }
                +"This still flows into front matter because no closer arrived.\n"
            }
        }
    }

    // --- detection --------------------------------------------------------

    @Test
    fun `should not auto-detect front matter when line 2 is not key-like`() = runTest {
        // given — `---` followed by a natural-language paragraph fails the
        // YAML discriminator, so the opener is treated as a thematic break.
        val textFlow = """
            ---
            This is just a paragraph.
            ---
            Body.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then — both `---` lines are thematic breaks (the parser does not
        // implement setext headings — DIVERGENCE), with paragraphs around them.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "hr" {}
            "p" { +"This is just a paragraph." }
            "hr" {}
            "p" { +"Body." }
        }
    }

    @Test
    fun `should not auto-detect front matter when line 2 is another dashes line`() = runTest {
        // given — `---` followed by another `---` is two thematic breaks,
        // not empty front matter. Empty front matter has no real-world use
        // and conflicts with the more common double-thematic-break case.
        val textFlow = """
            ---
            ---
            Body.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "hr" {}
            "hr" {}
            "p" { +"Body." }
        }
    }

    @Test
    fun `should not recognise front matter when opener is preceded by content`() = runTest {
        // given — a leading paragraph means line 1 is not the opener; the
        // `---` on line 2 is a thematic break (the parser does not implement
        // setext headings — DIVERGENCE).
        val textFlow = """
            title
            ---
            Body.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"title" }
            "hr" {}
            "p" { +"Body." }
        }
    }

    @Test
    fun `should not recognise front matter mid-document after a heading`() = runTest {
        // given — front matter only triggers at byte 0; a `---` after a heading
        // is a thematic break under default GFM rules.
        val textFlow = """
            # Heading

            ---

            Body.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h1" { +"Heading" }
            "hr" {}
            "p" { +"Body." }
        }
    }

    @Test
    fun `should parse YAML front matter with CRLF line endings`() = runTest {
        // given — a Windows-formatted document. `\r\n` must normalize to `\n`
        // so opener / closer line-equality comparisons still work.
        val textFlow =
            "---\r\ntitle: Hello\r\nauthor: Alice\r\n---\r\nBody paragraph."
                .chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Hello" }
                "entry"("key" to "author") { +"Alice" }
            }
            "p" { +"Body paragraph." }
        }
    }

    @Test
    fun `should treat bare opener without trailing newline as thematic break`() = runTest {
        // given — a document consisting solely of `---` (no `\n`). The opener
        // never completes, so at EOF the prelude replays as content and the
        // regular parser emits a thematic break.
        val textFlow = flowOf("---")

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "hr" {}
        }
    }

    @Test
    fun `should close front matter when closer at EOF has no trailing newline`() = runTest {
        // given — closer arrives without a terminating `\n`; a bare `---` at
        // EOF is the structural close, not body content.
        val textFlow =
            "---\ntitle: Hello\n---".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Hello" }
            }
        }
    }

    @Test
    fun `should not treat four-dash line in body as closer`() = runTest {
        // given — closer comparison is exact line equality, so `----` (or any
        // longer dash run) inside the body is content, not the closer — and
        // not a YAML entry either, so it survives verbatim.
        val textFlow = """
            ---
            title: test
            ----
            still: body
            ---
            After.
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"test" }
                +"----\n"
                "entry"("key" to "still") { +"body" }
            }
            "p" { +"After." }
        }
    }

    @Test
    fun `should not treat dashes with trailing space as closer`() = runTest {
        // given — closer comparison is byte-exact (matches Jekyll/Hugo). A
        // `--- ` line with a trailing space is body content, not the closer.
        // Explicit concatenation guards against editor auto-strip of the
        // trailing space in the source.
        val source = "---\ntitle: test\n--- \nstill: body\n---\nAfter."
        val textFlow = source.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"test" }
                +"--- \n"
                "entry"("key" to "still") { +"body" }
            }
            "p" { +"After." }
        }
    }
}
