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
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import com.xemantic.kotlin.test.assert
import kotlin.test.Test

/**
 * Specifies the streaming YAML parser and its event representation.
 *
 * A YAML document is parsed into **structured events**: `entry` marks (a
 * mapping entry, `key` attribute) and `item` marks (a sequence element); a
 * scalar value is the text inside, a mapping is nested `entry` marks, a
 * sequence is nested `item` marks. A non-string scalar carries a `type`
 * attribute (`bool`, `int`, `float`, `null`, `timestamp`); an empty flow
 * collection carries `type=seq` / `type=map`. An entry or item whose value
 * the YAML subset does not understand carries `type=verbatim`, its text the
 * value's source; any other line the subset does not understand survives
 * verbatim as a text child of the container it sits in (with its `\n`), so
 * nothing is ever lost and the parser never throws.
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

        // then — the deeper line is no nested value of `title` (a scalar)
        // but a continuation the readers refuse, so the value is verbatim
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "title", "type" to "verbatim") { +" x\n  author: y\n" }
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
    fun `should not type a number go-yaml v2 alone reads and then overflows`() = runTest {
        // given — PyYAML and Psych need a `.` and a signed exponent for a
        // float and take only lower-case `0x` / `0b` prefixes, so go-yaml v2
        // alone reads these shapes as numbers, and it falls back to a string
        // when the value overflows its int64 / uint64 / float64
        val textFlow = """
            a: 1e999
            b: .5e999
            c: 0XFFFFFFFFFFFFFFFFF
            d: 0o7777777777777777777777777
            e: +0XFFFFFFFFFFFFFFFF
            f: -0X8000000000000001
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "a") { +"1e999" }
            "entry"("key" to "b") { +".5e999" }
            "entry"("key" to "c") { +"0XFFFFFFFFFFFFFFFFF" }
            "entry"("key" to "d") { +"0o7777777777777777777777777" }
            "entry"("key" to "e") { +"+0XFFFFFFFFFFFFFFFF" }
            "entry"("key" to "f") { +"-0X8000000000000001" }
        }
    }

    @Test
    fun `should type a number go-yaml v2 alone reads within its range`() = runTest {
        // given — the largest uint64 and the smallest int64 still fit, a
        // decimal too large for an integer is read as a float, and an
        // underflowing float is zero rather than an error
        val textFlow = """
            a: 0XFFFFFFFFFFFFFFFF
            b: -0X8000000000000000
            c: 0b-1000000000000000000000000000000000000000000000000000000000000000
            d: -_99999999999999999999
            e: 1e-999
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "a", "type" to "int") { +"0XFFFFFFFFFFFFFFFF" }
            "entry"("key" to "b", "type" to "int") { +"-0X8000000000000000" }
            "entry"("key" to "c", "type" to "int") {
                +"0b-1000000000000000000000000000000000000000000000000000000000000000"
            }
            "entry"("key" to "d", "type" to "float") { +"-_99999999999999999999" }
            "entry"("key" to "e", "type" to "float") { +"1e-999" }
        }
    }

    @Test
    fun `DIVERGENCE - should not type a plain scalar a reader refuses to load`() = runTest {
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
    fun `should resolve a long near-miss of a base prefixed number`() {
        // given — a base prefix, a long digit run and one character outside
        // the base but still numeric-shaped, so the number patterns do run:
        // a pattern that lets the digits match two ways backtracks
        // quadratically, which at this length is hours instead of
        // milliseconds — a regression hangs the test rather than failing an
        // assertion, since a wall-clock bound would be flaky across targets
        val values = listOf("0x", "+0x").map { it + "1".repeat(500_000) + "." } +
            listOf("0b", "-0b").map { it + "1".repeat(500_000) + "2" }

        for (value in values) {
            // when
            val type = yamlScalarType(value)
            val unsafe = isUnsafePlainScalar(value)

            // then
            assert(type == null)
            assert(!unsafe)
        }
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

    @Test
    fun `DIVERGENCE - should read a hash right after a closed token as a comment`() = runTest {
        // given — YAML 1.2 §6.6 wants whitespace before a comment, but the
        // front matter readers (PyYAML, Psych, go-yaml v2 — all libyaml's
        // scanner) start one at any `#` after a quoted scalar or a flow
        // collection, and read these lines as the values below; a strict
        // reader (npm `yaml`, snakeyaml-engine) refuses them instead
        val textFlow = "a: \"x\"#b\nc: 'y'#d\ntags: [e]#f\ngeo: {g: 1}#h\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "a") { +"x" }
            "entry"("key" to "c") { +"y" }
            "entry"("key" to "tags") {
                "item" { +"e" }
            }
            "entry"("key" to "geo") {
                "entry"("key" to "g", "type" to "int") { +"1" }
            }
        }
    }

    // --- verbatim fallback (DIVERGENCE) -----------------------------------

    @Test
    fun `should read a hash after a tab as a comment in a flow sequence`() = runTest {
        // given — the comment leaves the sequence unterminated, which every
        // reader refuses, so the value is outside the subset
        val textFlow = "title: x\ntags: [a\t#b]\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "title") { +"x" }
            "entry"("key" to "tags", "type" to "verbatim") { +" [a\t#b]\n" }
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
        // reader refuses, so the value is outside the subset
        val textFlow = "title: x\ngeo: {a #b: c}\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "title") { +"x" }
            "entry"("key" to "geo", "type" to "verbatim") { +" {a #b: c}\n" }
        }
    }

    @Test
    fun `DIVERGENCE - should keep a flow scalar with a colon before a flow indicator verbatim`() = runTest {
        // given — the readers disagree on a plain flow scalar whose `:` is
        // followed by `,` / `[` / `]` / `{` / `}`: PyYAML reads a mapping
        // (`[{draft: null}, x]`), Psych refuses the line, go-yaml v2 reads
        // the colon as content (`"draft:"`), so no single reading is right and
        // each value is kept as written; a colon followed by anything else is
        // content for all three
        val textFlow = """
            title: x
            tags: [draft:, x]
            one: [a:]
            geo: {a:[1, 2]}
            nested: {a:{b: 1}}
            empty: {a:, b: 1}
            last: {a:}
            time: [a:b]
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "title") { +"x" }
            "entry"("key" to "tags", "type" to "verbatim") { +" [draft:, x]\n" }
            "entry"("key" to "one", "type" to "verbatim") { +" [a:]\n" }
            "entry"("key" to "geo", "type" to "verbatim") { +" {a:[1, 2]}\n" }
            "entry"("key" to "nested", "type" to "verbatim") { +" {a:{b: 1}}\n" }
            "entry"("key" to "empty", "type" to "verbatim") { +" {a:, b: 1}\n" }
            "entry"("key" to "last", "type" to "verbatim") { +" {a:}\n" }
            "entry"("key" to "time") {
                "item" { +"a:b" }
            }
        }
    }

    @Test
    fun `should keep a block scalar header with a hash right after it verbatim`() = runTest {
        // given — Psych and go-yaml v2 read `|-#x` as a header and a comment,
        // but PyYAML refuses it, so the value is outside the subset; with
        // whitespace before the `#` every reader takes the comment
        val textFlow = "a: |-#x\nb: | #y\n  z\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "a", "type" to "verbatim") { +" |-#x\n" }
            "entry"("key" to "b") { +"z\n" }
        }
    }

    @Test
    fun `DIVERGENCE - should keep an unrecognised line verbatim`() = runTest {
        // given — a complex key, a multi-line flow sequence, an unterminated
        // quote, a `key:value` without a space and a tab-indented line are
        // outside the subset; a line that is no entry survives verbatim (with
        // its newline) in the container it sits in, an entry whose value is
        // outside the subset keeps that value's source, every line of it
        val textFlow = (
            """
            title: x
            ? complex
            : value
            tags: [a,
              b]
            key:value
            """.trimIndent() + "\n\ttabbed: x\nquoted: \"unterminated\nnext: x\n"
        ).chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "title") { +"x" }
            +"? complex\n: value\n"
            "entry"("key" to "tags", "type" to "verbatim") { +" [a,\n  b]\n" }
            +"key:value\n\ttabbed: x\n"
            // a quote left open runs to the end of the document
            "entry"("key" to "quoted", "type" to "verbatim") { +" \"unterminated\nnext: x\n" }
        }
    }

    @Test
    fun `DIVERGENCE - should keep a sequence line inside a mapping verbatim`() = runTest {
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
    fun `DIVERGENCE - should parse anchors aliases and tags as plain strings`() = runTest {
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
    fun `DIVERGENCE - should accept a mapping indicator inside a plain value`() = runTest {
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

    // --- values of unknown shape (type=verbatim) ---------------------------

    @Test
    fun `DIVERGENCE - should mark a plain scalar continued on indented lines as verbatim`() = runTest {
        // given — the readers join the lines into one value (`A long title`),
        // which the subset does not parse, so the entry's value is unknown:
        // it keeps the source after `key:`, the continuation line included
        val textFlow = """
            title: A long
              title
            b: 1
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "title", "type" to "verbatim") { +" A long\n  title\n" }
            "entry"("key" to "b", "type" to "int") { +"1" }
        }
    }

    @Test
    fun `DIVERGENCE - should mark a bare key holding a line outside the subset as verbatim`() = runTest {
        // given — PyYAML reads a mapping in the flow sequence, Psych refuses
        // it, go-yaml v2 reads `a:` as a string
        val textFlow = """
            title:
              [a:, b]
            b: 1
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "title", "type" to "verbatim") { +"\n  [a:, b]\n" }
            "entry"("key" to "b", "type" to "int") { +"1" }
        }
    }

    @Test
    fun `DIVERGENCE - should mark a bare key holding indented plain lines as verbatim`() = runTest {
        // given — the readers read `first line second`
        val textFlow = """
            description:
              first line
              second
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "description", "type" to "verbatim") { +"\n  first line\n  second\n" }
        }
    }

    @Test
    fun `DIVERGENCE - should mark a key whose value is outside the subset as verbatim`() = runTest {
        // given — a multi-line quoted scalar; its empty line and the line
        // shaped like a comment are content for the readers (`a\n# c b`)
        val textFlow = "t: \"a\n\n# c\n  b\"\nz: 1\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "t", "type" to "verbatim") { +" \"a\n\n# c\n  b\"\n" }
            "entry"("key" to "z", "type" to "int") { +"1" }
        }
    }

    @Test
    fun `DIVERGENCE - should mark a scalar continued past an empty line as verbatim`() = runTest {
        // given — the readers read `a\nb`: the empty line is part of the value
        val textFlow = "t: a\n\n  b\nz: 1\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "t", "type" to "verbatim") { +" a\n\n  b\n" }
            "entry"("key" to "z", "type" to "int") { +"1" }
        }
    }

    @Test
    fun `should mark a block scalar followed by a more indented line as verbatim`() = runTest {
        // given — a line between the key's and the content's indentation
        // ends the block scalar, and the readers refuse it
        val textFlow = "t: |\n  x\n c\nz: 1\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "t", "type" to "verbatim") { +" |\n  x\n c\n" }
            "entry"("key" to "z", "type" to "int") { +"1" }
        }
    }

    @Test
    fun `should mark a block scalar continued past a less indented comment as verbatim`() = runTest {
        // given — a comment less indented than the content ends the block
        // scalar, and a line after it more indented than the key is one the
        // readers refuse: PyYAML, Psych and go-yaml v2 refuse each document,
        // so the value stays source — read as the block scalar `x\n` with
        // `y` at the root, it would render as `a: |\n  x\n    y\n`, which
        // the readers accept as `x\n  y\n`
        val sources = listOf(
            "a: |\n    x\n  # c\n    y\nz: 1\n",
            "a: |\n    x\n# c\n\n    y\nz: 1\n",
            "a: |\n# c\n  x\nz: 1\n",
        )

        for (source in sources) {
            // when
            val parsed = source.chunkedRandomly().asFlow().parseYaml()

            // then
            parsed.mergeAdjacentText() sameAs semanticEvents {
                "entry"("key" to "a", "type" to "verbatim") {
                    +(" |\n" + source.removePrefix("a: |\n").removeSuffix("z: 1\n"))
                }
                "entry"("key" to "z", "type" to "int") { +"1" }
            }
        }
    }

    @Test
    fun `should end a block scalar at a less indented comment`() = runTest {
        // given — the readers read `{a: "x\n", b: 1}` and, keeping the empty
        // line ahead of the comment, `{k: "x\n\n", b: 1}`
        val textFlow = "a: |\n    x\n  # c\n\nb: 1\nk: |+\n  x\n\n# c\n\nb: 1\n"
            .chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "a") { +"x\n" }
            "entry"("key" to "b", "type" to "int") { +"1" }
            "entry"("key" to "k") { +"x\n\n" }
            "entry"("key" to "b", "type" to "int") { +"1" }
        }
    }

    @Test
    fun `DIVERGENCE - should continue an open quote on a tab-led line`() = runTest {
        // given — a tab is no indentation, yet an open quoted scalar
        // continues on any line: the readers read `{p: {a: "x y", b: 1}}`
        val textFlow = "p:\n  a: \"x\n\ty\"\n  b: 1\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "p") {
                "entry"("key" to "a", "type" to "verbatim") { +" \"x\n\ty\"\n" }
                "entry"("key" to "b", "type" to "int") { +"1" }
            }
        }
    }

    @Test
    fun `DIVERGENCE - should mark a sequence item continued on indented lines as verbatim`() = runTest {
        // given — the readers read `a b`, and `v x` for the compact entry
        val textFlow = """
            - a
              b
            - k: v
               x
            - c
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "item"("type" to "verbatim") { +" a\n  b\n" }
            "item" {
                "entry"("key" to "k", "type" to "verbatim") { +" v\n x\n" }
            }
            "item" { +"c" }
        }
    }

    @Test
    fun `DIVERGENCE - should keep the lines of a verbatim value indented relative to its key`() = runTest {
        // given — a nested entry continued one column deeper than its key:
        // the source's own indentation is removed up to the key's column, so
        // the writer can re-indent the entry without losing the continuation
        val textFlow = "a:\n b: A\n  x\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "a") {
                "entry"("key" to "b", "type" to "verbatim") { +" A\n x\n" }
            }
        }
    }

    @Test
    fun `should not hold a scalar entry past the end of the document`() = runTest {
        // given — the last entry is held for one line to see whether it is
        // continued; finish commits it
        val parsed = flow {
            val parser = YamlParser(this)
            parser.line("a: 1")
            parser.line("")
            parser.finish()
        }

        // when
        val events = parsed.mergeAdjacentText()

        // then
        events sameAs semanticEvents {
            "entry"("key" to "a", "type" to "int") { +"1" }
        }
    }

    @Test
    fun `should keep a line outside the subset indented relative to its container`() = runTest {
        // given — a line that is no entry, at the indentation of the entries
        // around it, which need not be the writer's two spaces; the parser
        // removes that indentation so the writer can re-indent the line
        // along with its container
        val nested = "p:\n    a: 1\n    ? x\n".chunkedRandomly().asFlow()
        val indentedRoot = "  a: 1\n  ? x\n".chunkedRandomly().asFlow()

        // when
        val parsedNested = nested.parseYaml()
        val parsedIndentedRoot = indentedRoot.parseYaml()

        // then
        parsedNested.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "p") {
                "entry"("key" to "a", "type" to "int") { +"1" }
                +"? x\n"
            }
        }
        parsedIndentedRoot.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "a", "type" to "int") { +"1" }
            +"? x\n"
        }
    }

    @Test
    fun `should keep a line ahead of the first entry of an indented root relative to the root`() = runTest {
        // given — the writer writes the root at column 0, so a line outside
        // the subset ahead of the first root entry must lose the root's
        // indentation as well: kept as is, `  ? x` would sit above `a: 1`
        // and turn it into a line less indented than the root. The readers
        // read `{x y: null, a: 1}` either way
        val textFlow = "  ? x\n    y\n  a: 1\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            +"? x\n  y\n"
            "entry"("key" to "a", "type" to "int") { +"1" }
        }
    }

    @Test
    fun `should hold only a few lines ahead of the first entry of an indented root`() = runTest {
        // given — `  ? a\n  : 1` repeated, which the readers accept as
        // `{a: 1, …}`: none of its lines is an entry the subset parses, so
        // holding them all would buffer the whole document
        val events = mutableListOf<SemanticEvent>()
        val parser = YamlParser { events += it }
        val lines = (1..5).flatMap { listOf("  ? k$it", "  : $it") }

        // when
        lines.take(8).forEach { parser.line(it) }
        val heldEvents = events.toList()
        lines.drop(8).forEach { parser.line(it) }

        // then — the ninth line releases them with their own indentation,
        // and every line after it streams
        assert(heldEvents.isEmpty())
        assert(events.joinToString("") { (it as SemanticEvent.Text).text } == lines.joinToString("") { "$it\n" })
    }

    @Test
    fun `should keep the lines of an indented root holding no entry as written`() = runTest {
        // given — no entry follows the lines outside the subset, so nothing
        // is written at column 0 and their own indentation is what keeps the
        // root indented
        val textFlow = "  ? x\n    y\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            +"  ? x\n    y\n"
        }
    }

    @Test
    fun `DIVERGENCE - should keep a quoted or flow value open below its key as source`() = runTest {
        // given — an open quote or flow collection continues on any line
        // until it closes, whatever its indentation: the readers read
        // `x author: y`, `[a, b]`, `[a, "b, ]"]` and `x y`
        val textFlow = """
            title: "x
            author: y"
            tags: [a,
            b]
            more: [a, "b,
            ]"]
            nested:
              t: 'x
            y'
              m: 1
            z: 1
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "title", "type" to "verbatim") { +" \"x\nauthor: y\"\n" }
            "entry"("key" to "tags", "type" to "verbatim") { +" [a,\nb]\n" }
            "entry"("key" to "more", "type" to "verbatim") { +" [a, \"b,\n]\"]\n" }
            "entry"("key" to "nested") {
                "entry"("key" to "t", "type" to "verbatim") { +" 'x\ny'\n" }
                "entry"("key" to "m", "type" to "int") { +"1" }
            }
            "entry"("key" to "z", "type" to "int") { +"1" }
        }
    }

    @Test
    fun `DIVERGENCE - should keep an unclosed quote as source up to the end of the document`() = runTest {
        // given
        val textFlow = "title: \"x\nauthor: y\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "title", "type" to "verbatim") { +" \"x\nauthor: y\n" }
        }
    }

    @Test
    fun `DIVERGENCE - should keep a compact entry outside the subset as an entry of its item`() = runTest {
        // given — the compact form the writer writes for an item holding a
        // mapping; the readers read `[{k: "x y", m: 1}]`
        val textFlow = "- k: \"x\n    y\"\n  m: 1\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "item" {
                "entry"("key" to "k", "type" to "verbatim") { +" \"x\n  y\"\n" }
                "entry"("key" to "m", "type" to "int") { +"1" }
            }
        }
    }

    /**
     * PyYAML refuses a line less indented than an indented root, Psych and
     * go-yaml v2 read the document up to it (`{a: 1}`): the parser keeps
     * that line and everything after it as text at the root, with its own
     * indentation, and reads no entry the readers do not see. The cost: the
     * writer writes the root at column 0, so the rendered text no longer
     * sits below the root and re-parses as entries — the fixpoint is lost,
     * but only for a document the readers refuse or cut short (never a
     * front matter, whose root is at column 0).
     */
    @Test
    fun `DIVERGENCE - should keep the rest of the document as text after a line less indented than the root`() = runTest {
        // given
        val textFlow = "  a: 1\n  ? x\nb: 2\n  c: 3\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "a", "type" to "int") { +"1" }
            +"? x\nb: 2\n  c: 3\n"
        }
    }

    @Test
    fun `should end a flow collection whose colon starts no node`() = runTest {
        // given — a colon not followed by whitespace, the end of the line or
        // a flow indicator is content of a plain scalar (`x:"y`), so the
        // quote after it opens nothing; the readers read
        // `{a: ['x:"y', b], c: 2, d: 3}`
        val textFlow = "a: [x:\"y,\nb]\nc: 2\nd: 3\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parseYaml()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "a", "type" to "verbatim") { +" [x:\"y,\nb]\n" }
            "entry"("key" to "c", "type" to "int") { +"2" }
            "entry"("key" to "d", "type" to "int") { +"3" }
        }
    }

}
