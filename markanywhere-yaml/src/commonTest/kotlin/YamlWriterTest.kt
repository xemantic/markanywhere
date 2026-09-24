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
import com.xemantic.kotlin.test.sameAs
import com.xemantic.markanywhere.flow.semanticEvents
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Specifies the YAML writer: `entry` (key attribute) / `item` marks written
 * back as block-style YAML, one line per scalar, two spaces per nesting
 * level — the inverse of [YamlParserTest].
 */
class YamlWriterTest {

    @Test
    fun `should render a flat mapping`() = runTest {
        // given
        val flow = semanticEvents {
            "entry"("key" to "title") { +"Hello" }
            "entry"("key" to "author") { +"Alice" }
        }

        // when
        val yaml = flow.renderYaml()

        // then
        yaml sameAs """
            title: Hello
            author: Alice
        """.trimIndent() + "\n"
    }

    @Test
    fun `should render a root sequence`() = runTest {
        // given
        val flow = semanticEvents {
            "item" { +"a" }
            "item" {
                "entry"("key" to "name") { +"Alice" }
            }
        }

        // when
        val yaml = flow.renderYaml()

        // then
        yaml sameAs """
            - a
            - name: Alice
        """.trimIndent() + "\n"
    }

    @Test
    fun `should treat a foreign mark as transparent`() = runTest {
        // given — a `frontmatter` wrapper (or any other mark) lays its
        // children out as if it were not there and writes its own text
        // verbatim
        val flow = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Hello" }
                "entry"("key" to "author") {
                    "entry"("key" to "name") { +"Alice" }
                }
                +"# a comment\n"
            }
        }

        // when
        val yaml = flow.renderYaml()

        // then
        yaml sameAs """
            title: Hello
            author:
              name: Alice
            # a comment
        """.trimIndent() + "\n"
    }

    @Test
    fun `should render nothing for an empty stream`() = runTest {
        // given
        val flow = semanticEvents { }

        // when
        val yaml = flow.renderYaml()

        // then
        yaml sameAs ""
    }

    @Test
    fun `should render nested mappings and sequences`() = runTest {
        // given
        val flow = semanticEvents {
            "entry"("key" to "author") {
                "entry"("key" to "name") { +"Alice" }
                "entry"("key" to "links") {
                    "item" { +"a" }
                    "item" { +"b" }
                }
            }
            "entry"("key" to "authors") {
                "item" {
                    "entry"("key" to "name") { +"Alice" }
                    "entry"("key" to "role") { +"dev" }
                }
                "item" {
                    "item"("type" to "int") { +"1" }
                    "item"("type" to "int") { +"2" }
                }
                "item" { +"plain" }
            }
        }

        // when
        val yaml = flow.renderYaml()

        // then
        yaml sameAs """
            author:
              name: Alice
              links:
                - a
                - b
            authors:
              - name: Alice
                role: dev
              - - 1
                - 2
              - plain
        """.trimIndent() + "\n"
    }

    @Test
    fun `should render typed scalars bare and typed-looking strings quoted`() = runTest {
        // given — a string that would re-parse as a bool / number / null /
        // timestamp must be quoted to stay a string (so must such a key: a
        // YAML 1.1 reader takes a bare `yes:` as a boolean key); a typed
        // scalar is written as-is
        val flow = semanticEvents {
            "entry"("key" to "draft", "type" to "bool") { +"true" }
            "entry"("key" to "count", "type" to "int") { +"42" }
            "entry"("key" to "when", "type" to "timestamp") { +"2026-05-10" }
            "entry"("key" to "nothing", "type" to "null") { +"null" }
            "entry"("key" to "live") { +"true" }
            "entry"("key" to "id") { +"42" }
            "entry"("key" to "ratio") { +"0.5" }
            "entry"("key" to "day") { +"2026-05-10" }
            "entry"("key" to "yes") { +"yes" }
            "entry"("key" to "nil") { +"~" }
        }

        // when
        val yaml = flow.renderYaml()

        // then
        yaml sameAs """
            draft: true
            count: 42
            when: 2026-05-10
            nothing: null
            live: "true"
            id: "42"
            ratio: "0.5"
            day: "2026-05-10"
            "yes": "yes"
            nil: "~"
        """.trimIndent() + "\n"
    }

    @Test
    fun `should render empty and missing values`() = runTest {
        // given — no text and no type is an empty string; `type=null` with no
        // text is a bare `key:`; empty flow collections keep their brackets
        val flow = semanticEvents {
            "entry"("key" to "sub") { }
            "entry"("key" to "draft", "type" to "null") { }
            "entry"("key" to "tags", "type" to "seq") { }
            "entry"("key" to "meta", "type" to "map") { }
            "entry"("key" to "list") {
                "item"("type" to "null") { }
                "item" { }
            }
        }

        // when
        val yaml = flow.renderYaml()

        // then
        yaml sameAs """
            sub: ""
            draft:
            tags: []
            meta: {}
            list:
              -
              - ""
        """.trimIndent() + "\n"
    }

    @Test
    fun `should quote scalars and keys that are not safe as plain`() = runTest {
        // given
        val flow = semanticEvents {
            "entry"("key" to "og:title") { +"Hello" }
            "entry"("key" to "summary") { +"Title: \"Special\"" }
            "entry"("key" to "note") { +"value # not a comment" }
            "entry"("key" to "dash") { +"- not an item" }
            "entry"("key" to "padded") { +" spaced " }
            "entry"("key" to "tabbed") { +"a\tb" }
            "entry"("key" to "url") { +"http://x#y" }
            "entry"("key" to "page.section") { +"News" }
        }

        // when
        val yaml = flow.renderYaml()

        // then
        yaml sameAs """
            "og:title": Hello
            summary: "Title: \"Special\""
            note: "value # not a comment"
            dash: "- not an item"
            padded: " spaced "
            tabbed: "a\tb"
            url: http://x#y
            page.section: News
        """.trimIndent() + "\n"
    }

    @Test
    fun `should render a multi-line string as a literal block scalar`() = runTest {
        // given — the chomping indicator follows from the trailing newlines
        val flow = semanticEvents {
            "entry"("key" to "clip") { +"first\n\nsecond\n" }
            "entry"("key" to "strip") { +"x\ny" }
            "entry"("key" to "keep") { +"z\n\n" }
            "entry"("key" to "leading") { +"  indented\nline" }
            "entry"("key" to "notes") {
                "item" { +"a\nb\n" }
            }
            "entry"("key" to "next") { +"x" }
        }

        // when
        val yaml = flow.renderYaml()

        // then — a first line starting with whitespace falls back to quoting
        yaml sameAs """
            clip: |
              first

              second
            strip: |-
              x
              y
            keep: |+
              z

            leading: "  indented\nline"
            notes:
              - |
                a
                b
            next: x
        """.trimIndent() + "\n"
    }

    @Test
    fun `should write verbatim text as-is`() = runTest {
        // given — a line the parser kept verbatim (see YamlParserTest) is a
        // text child of its container; the renderer writes it back unchanged
        val flow = semanticEvents {
            "entry"("key" to "title") { +"x" }
            +"? complex\n: value\n"
            "entry"("key" to "nested") {
                "entry"("key" to "a") { +"b" }
                +"  &anchor\n"
            }
        }

        // when
        val yaml = flow.renderYaml()

        // then
        yaml sameAs """
            title: x
            ? complex
            : value
            nested:
              a: b
              &anchor
        """.trimIndent() + "\n"
    }

    @Test
    fun `should end an item without content on its own line`() = runTest {
        // given — an item whose only child is a foreign mark writes nothing
        // after its dash; the line must still be terminated
        val flow = semanticEvents {
            "item" { "x" { } }
            "item" { +"next" }
        }

        // when
        val yaml = flow.renderYaml()

        // then
        yaml sameAs """
            -
            - next
        """.trimIndent() + "\n"
    }

    @Test
    fun `should write every key as a line the front matter detector accepts`() = runTest {
        // given — the Markdown parser opens a front matter only when its
        // first line is a mapping key line; the writer's quoting exists to
        // keep whatever entry comes first re-detectable
        val keys = listOf(
            "title", "_key", "date-published", "page.section", "título",
            "og:title", "a b", "yes", "42", "- dash", "", "with \"quote\"", "it's", "a: b",
        )
        for (key in keys) {
            val flow = semanticEvents {
                "entry"("key" to key) { +"v" }
            }

            // when
            val line = flow.renderYaml().lineSequence().first()

            // then
            assert(isYamlKeyLine(line))
        }
    }

}
