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

import com.xemantic.kotlin.test.text.chunkedRandomly
import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Specifies the streaming YAML parser and its event representation.
 *
 * A YAML document is parsed into **structured events**: `entry` marks (a
 * mapping entry, `key` attribute) and `item` marks (a sequence element); a
 * scalar value is the text inside, a mapping is nested `entry` marks, a
 * sequence is nested `item` marks. A non-string scalar carries a `type`
 * attribute (`bool`, `int`, `float`, `null`, `timestamp`); an empty flow
 * collection carries `type=seq` / `type=map`. A line the YAML subset does
 * not understand survives verbatim as a text child of the container it
 * sits in (with its `\n`), so nothing is ever lost and the parser never
 * throws.
 */
class YamlParserTest {

    @Test
    fun `should parse a flat mapping`() = runTest {
        // given
        val textFlow = """
            title: Hello
            author: Alice
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "title") { +"Hello" }
            "entry"("key" to "author") { +"Alice" }
        }
    }

    @Test
    fun `should parse a root sequence`() = runTest {
        // given
        val textFlow = """
            - a
            - name: Alice
              role: dev
            -
              - nested
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "item" { +"a" }
            "item" {
                "entry"("key" to "name") { +"Alice" }
                "entry"("key" to "role") { +"dev" }
            }
            "item" {
                "item" { +"nested" }
            }
        }
    }

    @Test
    fun `should parse a document with any line ending`() = runTest {
        // given
        val textFlow = "title: Hello\r\nauthor: Alice\rdraft: true\n"
            .chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "title") { +"Hello" }
            "entry"("key" to "author") { +"Alice" }
            "entry"("key" to "draft", "type" to "bool") { +"true" }
        }
    }

    @Test
    fun `should parse an empty document`() = runTest {
        // given
        val textFlow = "# only a comment\n\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed sameAs semanticEvents { }
    }

    // --- mappings and sequences -------------------------------------------

    @Test
    fun `should parse a nested mapping`() = runTest {
        // given
        val textFlow = """
            author:
              name: Alice
              email: alice@example.com
            title: Doc
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "author") {
                "entry"("key" to "name") { +"Alice" }
                "entry"("key" to "email") { +"alice@example.com" }
            }
            "entry"("key" to "title") { +"Doc" }
        }
    }

    @Test
    fun `should parse a sequence at the same indent as its key`() = runTest {
        // given — YAML permits a block sequence to sit at its parent key's
        // indentation
        val textFlow = """
            tags:
            - a
            - b
            next: x
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "tags") {
                "item" { +"a" }
                "item" { +"b" }
            }
            "entry"("key" to "next") { +"x" }
        }
    }

    @Test
    fun `should parse a sequence of mappings`() = runTest {
        // given
        val textFlow = """
            authors:
              - name: Alice
                role: dev
              - name: Bob
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "authors") {
                "item" {
                    "entry"("key" to "name") { +"Alice" }
                    "entry"("key" to "role") { +"dev" }
                }
                "item" {
                    "entry"("key" to "name") { +"Bob" }
                }
            }
        }
    }

    @Test
    fun `should parse nested sequences`() = runTest {
        // given
        val textFlow = """
            matrix:
              - - 1
                - 2
              - - 3
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "matrix") {
                "item" {
                    "item"("type" to "int") { +"1" }
                    "item"("type" to "int") { +"2" }
                }
                "item" {
                    "item"("type" to "int") { +"3" }
                }
            }
        }
    }

    @Test
    fun `should parse an item whose value starts on the next line`() = runTest {
        // given
        val textFlow = """
            list:
              -
                name: x
              -
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "list") {
                "item" {
                    "entry"("key" to "name") { +"x" }
                }
                "item"("type" to "null") { }
            }
        }
    }

    @Test
    fun `should parse an indented top-level mapping`() = runTest {
        // given — the root mapping's indentation is whatever the first line uses
        val textFlow = "title: x\n  author: y\n"
            .chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then — the deeper line is not a nested value of `title` (a scalar),
        // so it survives verbatim
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "title") { +"x" }
            +"  author: y\n"
        }
    }

    // --- scalars ----------------------------------------------------------

    @Test
    fun `should type non-string plain scalars`() = runTest {
        // given — YAML 1.2 core schema, plus the 1.1 booleans Jekyll / Hugo
        // documents still use
        val textFlow = """
            draft: true
            published: yes
            count: 42
            neg: -3
            hex: 0x1F
            ratio: 0.5
            exp: 1e3
            inf: .inf
            nothing: null
            tilde: ~
            when: 2026-05-10
            stamp: 2026-05-10T10:00:00+02:00
            word: hello
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "draft", "type" to "bool") { +"true" }
            "entry"("key" to "published", "type" to "bool") { +"yes" }
            "entry"("key" to "count", "type" to "int") { +"42" }
            "entry"("key" to "neg", "type" to "int") { +"-3" }
            "entry"("key" to "hex", "type" to "int") { +"0x1F" }
            "entry"("key" to "ratio", "type" to "float") { +"0.5" }
            "entry"("key" to "exp", "type" to "float") { +"1e3" }
            "entry"("key" to "inf", "type" to "float") { +".inf" }
            "entry"("key" to "nothing", "type" to "null") { +"null" }
            "entry"("key" to "tilde", "type" to "null") { +"~" }
            "entry"("key" to "when", "type" to "timestamp") { +"2026-05-10" }
            "entry"("key" to "stamp", "type" to "timestamp") { +"2026-05-10T10:00:00+02:00" }
            "entry"("key" to "word") { +"hello" }
        }
    }

    @Test
    fun `should keep a quoted scalar a string`() = runTest {
        // given — quoting suppresses the type resolution
        val textFlow = """
            live: "true"
            id: '42'
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "live") { +"true" }
            "entry"("key" to "id") { +"42" }
        }
    }

    @Test
    fun `should decode double-quoted and single-quoted scalars`() = runTest {
        // given
        val textFlow = """
            title: "He said: \"hi\"\nBye\té"
            author: 'O''Brien'
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "title") { +"He said: \"hi\"\nBye\té" }
            "entry"("key" to "author") { +"O'Brien" }
        }
    }

    @Test
    fun `should decode quoted keys`() = runTest {
        // given
        val textFlow = """
            "og:title": Hello
            'a b': c
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "og:title") { +"Hello" }
            "entry"("key" to "a b") { +"c" }
        }
    }

    @Test
    fun `should represent an empty string and a missing value differently`() = runTest {
        // given — `""` is an empty string (no text, no type), a bare `key:`
        // is null
        val textFlow = """
            sub: ""
            draft:
            next: x
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "sub") { }
            "entry"("key" to "draft", "type" to "null") { }
            "entry"("key" to "next") { +"x" }
        }
    }

    @Test
    fun `should resolve a missing value at the end of the document as null`() = runTest {
        // given
        val textFlow = """
            title: x
            draft:
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "title") { +"x" }
            "entry"("key" to "draft", "type" to "null") { }
        }
    }

    @Test
    fun `should drop comment lines and trailing comments`() = runTest {
        // given
        val textFlow = """
            title: Hello # trailing
            # full-line comment
              # indented comment
            url: http://x#y
            author: Alice
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then — a `#` without a preceding space is content
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "title") { +"Hello" }
            "entry"("key" to "url") { +"http://x#y" }
            "entry"("key" to "author") { +"Alice" }
        }
    }

    @Test
    fun `should keep duplicate keys in source order`() = runTest {
        // given
        val textFlow = """
            tag: a
            tag: b
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "tag") { +"a" }
            "entry"("key" to "tag") { +"b" }
        }
    }

    @Test
    fun `should keep unicode keys and values`() = runTest {
        // given
        val textFlow = """
            título: café ☕
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "título") { +"café ☕" }
        }
    }

    @Test
    fun `should preserve interior whitespace of a plain scalar`() = runTest {
        // given
        val textFlow = "title:   Ready to check  - Nu Html Checker   \n"
            .chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "title") { +"Ready to check  - Nu Html Checker" }
        }
    }

    // --- block scalars ----------------------------------------------------

    @Test
    fun `should parse a literal block scalar preserving blank lines`() = runTest {
        // given
        val textFlow = """
            title: With blanks

            description: |
              first

              second
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then — clip chomping: a single trailing newline
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "title") { +"With blanks" }
            "entry"("key" to "description") { +"first\n\nsecond\n" }
        }
    }

    @Test
    fun `should honour block scalar chomping indicators`() = runTest {
        // given
        val textFlow = """
            a: |-
              x
              y

            b: |+
              z

            c: d
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "a") { +"x\ny" }
            "entry"("key" to "b") { +"z\n\n" }
            "entry"("key" to "c") { +"d" }
        }
    }

    @Test
    fun `should fold a folded block scalar`() = runTest {
        // given
        val textFlow = """
            desc: >
              one
              two

              three
                more indented
              four
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "desc") { +"one two\nthree\n  more indented\nfour\n" }
        }
    }

    @Test
    fun `should honour an explicit block scalar indentation indicator`() = runTest {
        // given
        val textFlow = """
            code: |2
                indented
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then — content indent is 2, so the extra 2 spaces are content
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "code") { +"  indented\n" }
        }
    }

    @Test
    fun `should parse a block scalar as a sequence item`() = runTest {
        // given
        val textFlow = """
            notes:
              - |
                text
              - plain
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "notes") {
                "item" { +"text\n" }
                "item" { +"plain" }
            }
        }
    }

    // --- flow collections -------------------------------------------------

    @Test
    fun `should parse a single-line flow sequence`() = runTest {
        // given
        val textFlow = """
            tags: [a, "b c", 3, [x]]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "tags") {
                "item" { +"a" }
                "item" { +"b c" }
                "item"("type" to "int") { +"3" }
                "item" { "item" { +"x" } }
            }
        }
    }

    @Test
    fun `should parse a single-line flow mapping`() = runTest {
        // given
        val textFlow = """
            geo: {lat: 1.5, lon: -2, name: "Berlin"}
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "geo") {
                "entry"("key" to "lat", "type" to "float") { +"1.5" }
                "entry"("key" to "lon", "type" to "int") { +"-2" }
                "entry"("key" to "name") { +"Berlin" }
            }
        }
    }

    @Test
    fun `should type empty flow collections`() = runTest {
        // given
        val textFlow = """
            tags: []
            meta: {}
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "tags", "type" to "seq") { }
            "entry"("key" to "meta", "type" to "map") { }
        }
    }

    // --- verbatim fallback (DIVERGENCE) -----------------------------------

    @Test
    fun `should DIVERGENCE keep an unrecognised line verbatim`() = runTest {
        // given — a complex key, a multi-line flow sequence, an unterminated
        // quote, a `key:value` without a space and a tab-indented line are
        // outside the subset; each line survives verbatim (with its newline)
        // in the container it sits in
        val textFlow = (
            """
            title: x
            ? complex
            : value
            tags: [a,
              b]
            quoted: "unterminated
            key:value
            """.trimIndent() + "\n\ttabbed: x\n"
        ).chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "title") { +"x" }
            +"? complex\n: value\ntags: [a,\n  b]\nquoted: \"unterminated\nkey:value\n\ttabbed: x\n"
        }
    }

    @Test
    fun `should DIVERGENCE keep a sequence line inside a mapping verbatim`() = runTest {
        // given — a `- item` at the mapping's own indentation is a YAML error
        val textFlow = """
            title: x
            - item
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "title") { +"x" }
            +"- item\n"
        }
    }

    @Test
    fun `should DIVERGENCE parse anchors aliases and tags as plain strings`() = runTest {
        // given
        val textFlow = """
            base: &b value
            other: *b
            typed: !!str 123
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "base") { +"&b value" }
            "entry"("key" to "other") { +"*b" }
            "entry"("key" to "typed") { +"!!str 123" }
        }
    }

    @Test
    fun `should be ready for a new document after finish`() = runTest {
        // given — the first document ends with a pending `key:` that finish
        // resolves as null; the second must not see it
        val parsed = flow {
            val parser = YamlParser(this)
            parser.line("key:")
            parser.finish()
            parser.line("a: 1")
            parser.finish()
            parser.line("- x")
            parser.finish()
        }

        // when
        val events = parsed.mergeAdjacentText()

        // then
        events sameAs semanticEvents {
            "entry"("key" to "key", "type" to "null") { }
            "entry"("key" to "a", "type" to "int") { +"1" }
            "item" { +"x" }
        }
    }

    @Test
    fun `should keep a leading empty line of a folded block scalar`() = runTest {
        // given — an empty line before the first content line is content
        // (YAML 1.2 §8.1.3: leading empty lines each become a line break)
        val textFlow = """
            desc: >

              foo
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "desc") { +"\nfoo\n" }
        }
    }

    @Test
    fun `should keep an empty line after a more-indented folded line`() = runTest {
        // given — the break after a more-indented line is never folded, so
        // the empty line that follows it stays a separate line break
        val textFlow = """
            desc: >
              a
                b

              c
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "desc") { +"a\n  b\n\nc\n" }
        }
    }

}
