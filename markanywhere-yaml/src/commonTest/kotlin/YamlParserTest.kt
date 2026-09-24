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
import com.xemantic.kotlin.test.assert
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.measureTime

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
    fun `should type the plain scalars a YAML 1_1 reader types`() = runTest {
        // given — YAML 1.2 reads these as strings, but Psych (Jekyll), PyYAML
        // or go-yaml v2 type them, and front matter is written for those
        // readers: Jekyll's documented date format carries a colonless offset
        val textFlow = """
            date: 2016-01-01 12:00:00 -0500
            short: 2024-5-1
            answer: y
            shout: N
            mixed: yEs
            nil: nULL
            big: 1_000
            grouped: 1,000
            bin: 0b101
            signed: +0x1F
            money: 1_000.5
            nan: .NaN
            time: 12:30
            clock: 12:30:45:10
            angle: 190:20:30.15
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "date", "type" to "timestamp") { +"2016-01-01 12:00:00 -0500" }
            "entry"("key" to "short", "type" to "timestamp") { +"2024-5-1" }
            "entry"("key" to "answer", "type" to "bool") { +"y" }
            "entry"("key" to "shout", "type" to "bool") { +"N" }
            "entry"("key" to "mixed", "type" to "bool") { +"yEs" }
            "entry"("key" to "nil", "type" to "null") { +"nULL" }
            "entry"("key" to "big", "type" to "int") { +"1_000" }
            "entry"("key" to "grouped", "type" to "int") { +"1,000" }
            "entry"("key" to "bin", "type" to "int") { +"0b101" }
            "entry"("key" to "signed", "type" to "int") { +"+0x1F" }
            "entry"("key" to "money", "type" to "float") { +"1_000.5" }
            "entry"("key" to "nan", "type" to "float") { +".NaN" }
            "entry"("key" to "time", "type" to "int") { +"12:30" }
            "entry"("key" to "clock", "type" to "int") { +"12:30:45:10" }
            "entry"("key" to "angle", "type" to "float") { +"190:20:30.15" }
        }
    }

    @Test
    fun `should type a timestamp offset the way Psych splits and bounds it`() = runTest {
        // given — Psych reads up to two digits as the offset hours and the
        // rest as its minutes, and only bounds the whole offset under a day:
        // minutes past 59 carry into the hours, and a three-digit offset is
        // two hour digits and one minute digit
        val textFlow = """
            a: 2024-01-01 10:00:00 +05:99
            b: 2024-01-01 10:00:00 +0599
            c: 2024-01-01 10:00:00 +19:70
            d: 2024-01-01 10:00:00 +070
            e: 2024-01-01 10:00:00 -2359
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "a", "type" to "timestamp") { +"2024-01-01 10:00:00 +05:99" }
            "entry"("key" to "b", "type" to "timestamp") { +"2024-01-01 10:00:00 +0599" }
            "entry"("key" to "c", "type" to "timestamp") { +"2024-01-01 10:00:00 +19:70" }
            "entry"("key" to "d", "type" to "timestamp") { +"2024-01-01 10:00:00 +070" }
            "entry"("key" to "e", "type" to "timestamp") { +"2024-01-01 10:00:00 -2359" }
        }
    }

    @Test
    fun `should not type a timestamp whose offset ends in a colon`() {
        // given — a `:` ending a plain scalar is a mapping indicator, so
        // Psych refuses the line: the shape can never be a timestamp
        val values = listOf("2024-01-01 12:00:00 +5:", "2024-01-01 12:00:00 -05:")

        // when
        val types = values.map { yamlScalarType(it) }

        // then
        assert(types == listOf(null, null))
    }

    @Test
    fun `should type the number shapes go-yaml v2 reads once underscores are removed`() = runTest {
        // given — go-yaml v2 (Hugo) deletes every `_` before it parses a
        // number, so an underscore next to a sign, an exponent or a base
        // prefix still makes one; it also takes an upper-case base prefix
        val textFlow = """
            a: 1_e5
            b: 1e5_
            c: 1_E5
            d: +_1
            e: +_1.
            f: 0_x1
            g: 0X1F
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "a", "type" to "float") { +"1_e5" }
            "entry"("key" to "b", "type" to "float") { +"1e5_" }
            "entry"("key" to "c", "type" to "float") { +"1_E5" }
            "entry"("key" to "d", "type" to "int") { +"+_1" }
            "entry"("key" to "e", "type" to "float") { +"+_1." }
            "entry"("key" to "f", "type" to "int") { +"0_x1" }
            "entry"("key" to "g", "type" to "int") { +"0X1F" }
        }
    }

    @Test
    fun `should not type a shape no reader types`() = runTest {
        // given — a trailing or doubled separator, a dot before an
        // underscore, an exponent with no mantissa digit, a date that is not
        // in the calendar, a date-only shape with a sign, a minute Psych's
        // Time refuses, a sign after a signed binary prefix, an offset
        // Psych splits into 53 hours or bounds at a day and a date-only
        // shape with a signed zero year: PyYAML, Psych and go-yaml v2 all
        // read strings
        val textFlow = """
            a: 1,
            b: 1,,2
            c: ._0
            d: .e5
            e: 2024-2-30
            f: -2024-01-01
            g: 2024-01-01T12:60:00
            h: -0b-1
            i: -0b+1
            j: 2024-01-01 10:00:00 +530
            k: 2024-01-01 10:00:00 +2400
            l: -0000-01-01
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "a") { +"1," }
            "entry"("key" to "b") { +"1,,2" }
            "entry"("key" to "c") { +"._0" }
            "entry"("key" to "d") { +".e5" }
            "entry"("key" to "e") { +"2024-2-30" }
            "entry"("key" to "f") { +"-2024-01-01" }
            "entry"("key" to "g") { +"2024-01-01T12:60:00" }
            "entry"("key" to "h") { +"-0b-1" }
            "entry"("key" to "i") { +"-0b+1" }
            "entry"("key" to "j") { +"2024-01-01 10:00:00 +530" }
            "entry"("key" to "k") { +"2024-01-01 10:00:00 +2400" }
            "entry"("key" to "l") { +"-0000-01-01" }
        }
    }

    @Test
    fun `should DIVERGENCE not type a plain scalar a reader refuses to load`() = runTest {
        // given — PyYAML resolves `=` and `<<` to its value / merge tags and
        // then refuses to construct them; it refuses a date not in the
        // calendar or a time / offset out of range (which Psych reads as a
        // string), and PyYAML / Psych refuse a base prefix with no digit or
        // an exponent with no mantissa: none of them has a type to report
        val textFlow = """
            a: =
            b: <<
            c: 2024-13-45
            d: 0x_
            e: .e+4
            f: 2024-01-01 24:30:00
            g: 2024-01-01 10:00:00 +23:99
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "a") { +"=" }
            "entry"("key" to "b") { +"<<" }
            "entry"("key" to "c") { +"2024-13-45" }
            "entry"("key" to "d") { +"0x_" }
            "entry"("key" to "e") { +".e+4" }
            "entry"("key" to "f") { +"2024-01-01 24:30:00" }
            "entry"("key" to "g") { +"2024-01-01 10:00:00 +23:99" }
        }
    }

    @Test
    fun `should resolve a long near-miss of a base prefixed number in linear time`() {
        // given — a base prefix, digits and one character outside the base:
        // a pattern that lets the digits match two ways backtracks
        // quadratically over them, and every plain scalar is typed
        val values = listOf("0x", "0b", "+0x", "-0b").map { prefix ->
            prefix + "1".repeat(50_000) + "g"
        }

        // when
        val elapsed = TimeSource.Monotonic.measureTime {
            for (value in values) {
                assert(yamlScalarType(value) == null)
                assert(!isUnsafePlainScalar(value))
            }
        }

        // then — linear takes milliseconds, quadratic minutes; the bound
        // is far from both so a slow runner cannot trip it
        assert(elapsed < 20.seconds)
    }

    @Test
    fun `should type a long run of digits without exhausting the stack`() {
        // given — a repeated group is matched recursively by the JVM regex
        // engine, one frame per repetition, so a pattern that repeats one
        // over a digit run overflows the stack on a long enough value
        val digits = "1".repeat(100_000)
        val values = mapOf(
            digits to "int",
            "1,".repeat(50_000) + "1" to "int",
            ".$digits" to "float",
            ".1" + "_1".repeat(50_000) to "float",
            "1" + ":1".repeat(50_000) to "int",
            "1" + ":1".repeat(50_000) + ".5" to "float",
            "${digits}g" to null,
            "1,".repeat(50_000) + "g" to null,
            ".${digits}g" to null,
            ".1" + "_1".repeat(50_000) + "g" to null,
            "1" + ":1".repeat(50_000) + "g" to null,
        )

        // when
        val types = values.keys.map { yamlScalarType(it) }

        // then
        assert(types == values.values.toList())
    }

    @Test
    fun `should type a leading-zero decimal go-yaml v2 reads as a float`() = runTest {
        // given — PyYAML and Psych read these as strings; go-yaml v2 takes
        // the leading zero as an octal prefix, fails on the 8 or 9 and falls
        // back to a float
        val textFlow = """
            a: 08
            b: 019
            c: 0_9
            d: -08
            e: 07
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "a", "type" to "float") { +"08" }
            "entry"("key" to "b", "type" to "float") { +"019" }
            "entry"("key" to "c", "type" to "float") { +"0_9" }
            "entry"("key" to "d", "type" to "float") { +"-08" }
            "entry"("key" to "e", "type" to "int") { +"07" }
        }
    }

    @Test
    fun `should not type a float shape without a digit`() = runTest {
        // given — no reader types a lone dot or a sign and a dot
        val textFlow = """
            dot: .
            signed: +.
            under: ._
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "dot") { +"." }
            "entry"("key" to "signed") { +"+." }
            "entry"("key" to "under") { +"._" }
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

    @Test
    fun `should read a colon before a tab as a mapping indicator in a flow mapping`() = runTest {
        // given
        val textFlow = "geo: {lat:\t1.5}\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "geo") {
                "entry"("key" to "lat", "type" to "float") { +"1.5" }
            }
        }
    }

    // --- verbatim fallback (DIVERGENCE) -----------------------------------

    @Test
    fun `should read a hash after a tab as a comment in a flow sequence`() = runTest {
        // given — the comment leaves the sequence unterminated, which every
        // reader refuses, so the line is outside the subset
        val textFlow = "title: x\ntags: [a\t#b]\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "title") { +"x" }
            +"tags: [a\t#b]\n"
        }
    }

    @Test
    fun `should read a hash after a tab as a comment in a key line`() = runTest {
        // given — `#` after any whitespace starts a comment, so the colon
        // behind it is not a mapping indicator and the line is not an entry
        val textFlow = "title: x\na\t#b: c\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "title") { +"x" }
            +"a\t#b: c\n"
        }
    }

    @Test
    fun `should read a hash after a space as a comment in a flow mapping key`() = runTest {
        // given — the comment leaves the mapping unterminated, which every
        // reader refuses, so the line is outside the subset
        val textFlow = "title: x\ngeo: {a #b: c}\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "title") { +"x" }
            +"geo: {a #b: c}\n"
        }
    }

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
    fun `should DIVERGENCE accept a mapping indicator inside a plain value`() = runTest {
        // given — YAML readers (Psych, PyYAML) reject both lines
        val textFlow = """
            note: Note: see
            end: ends:
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "note") { +"Note: see" }
            "entry"("key" to "end") { +"ends:" }
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
