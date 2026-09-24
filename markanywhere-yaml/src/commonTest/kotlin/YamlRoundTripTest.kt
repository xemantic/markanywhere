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
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * The structured events must survive `renderYaml()` → `parseYaml()`
 * unchanged: the writer's quoting rules are pinned against the parser's
 * typing rules here — a plain scalar the parser types must be quoted by the
 * writer when it is a string, and every value shape must come back as the
 * same events.
 */
class YamlRoundTripTest {

    @Test
    fun `should round-trip string scalars that look like other types or need quoting`() = runTest {
        // given — every value is a string; several would be typed if left plain
        val values = listOf(
            "true", "False", "yes", "NO", "on", "Off", "null", "Null", "~",
            "42", "-3", "+7", "0x1F", "0o17", "0.5", "1e3", ".inf", "-.Inf", ".nan", "1.",
            "2026-05-10", "2026-05-10T10:00:00+02:00", "2026-5-1 10:00:00Z",
            "Title: \"Special\"", "value # not a comment", "- not an item", "? not a key",
            ": colon", "[not, a, list]", "{not: a map}", "&anchor", "*alias", "!tag",
            "|pipe", ">gt", "'single'", "\"double\"", "%percent", "@at", "`tick",
            " padded ", "a\tb", "a\\b", "http://x#y", "Ready to check  - Nu Html Checker",
            "café ☕", "first\n\nsecond\n", "x\ny", "z\n\n", "  indented\nline", "\nleading",
            "trailing space \nline", "", "only\n\n\n",
        )
        for (value in values) {
            val events = semanticEvents {
                "entry"("key" to "k") { if (value.isNotEmpty()) +value }
            }

            // when
            val reparsed = flowOf(events.renderYaml()).parseYaml()

            // then
            reparsed.mergeAdjacentText() sameAs events
        }
    }

    @Test
    fun `should write a colon or hash that is not an indicator plain`() = runTest {
        // given — `:` not followed by a space and `#` not after a space are
        // plain-scalar content for every YAML reader (issue #80)
        val values = listOf(
            "https://xemantic.com/contact", "a:b", "C#", "a#b", "say \"hi\"", "C:\\path",
        )
        for (value in values) {
            val source = "k: $value\n"

            // when
            val rendered = flowOf(source).parseYaml().renderYaml()
            val reparsed = flowOf(rendered).parseYaml()

            // then
            rendered sameAs source
            reparsed.mergeAdjacentText() sameAs semanticEvents {
                "entry"("key" to "k") { +value }
            }
        }
    }

    @Test
    fun `should write a colon or hash that is not an indicator plain in a sequence item`() = runTest {
        // given — an item's content is first checked for a compact mapping
        // (`- key: value`), so a `:` or `#` there takes a different path
        // through the parser than an entry value
        val source = "k:\n  - https://xemantic.com/contact\n  - a:b\n  - C#\n  - a#b\n"

        // when
        val rendered = flowOf(source).parseYaml().renderYaml()
        val reparsed = flowOf(rendered).parseYaml()

        // then
        rendered sameAs source
        reparsed.mergeAdjacentText() sameAs semanticEvents {
            "entry"("key" to "k") {
                "item" { +"https://xemantic.com/contact" }
                "item" { +"a:b" }
                "item" { +"C#" }
                "item" { +"a#b" }
            }
        }
    }

    @Test
    fun `should keep quoting a colon or hash that is an indicator and YAML 1_1 sexagesimals`() = runTest {
        // given — a mapping colon, a comment, a leading indicator, and base-60
        // numbers a YAML 1.1 reader (Psych, PyYAML) would type as int / float
        val values = listOf(
            "Note: see", "ends:", "a #b", "12:30", "+1:30", "190:20:30.15", "#tag", ": x",
        )
        for (value in values) {
            val events = semanticEvents {
                "entry"("key" to "k") { +value }
            }

            // when
            val rendered = events.renderYaml()
            val reparsed = flowOf(rendered).parseYaml()

            // then
            rendered sameAs "k: \"$value\"\n"
            reparsed.mergeAdjacentText() sameAs events
        }
    }

    @Test
    fun `should quote a string a YAML 1_1 reader would type`() = runTest {
        // given — plain scalars YAML 1.2 reads as strings, but Psych (Jekyll)
        // or PyYAML type as a number, time, boolean or null, or refuse
        val values = listOf(
            "2024-05-01T10:00:00+0100", "2024-05-01 10:00:00 +0100", "2024-5-1",
            "1_000", "1,000", "0b101", "+0x1F", "1_000.5", "1,000.5", "1.5_0", ".e+4",
            "yEs", "oFF", "nULL", "y", "Y", "n", "N", ".Nan", ".iNf", "+.InF", "-.INf", "=", "<<",
        )
        for (value in values) {
            val events = semanticEvents {
                "entry"("key" to "k") { +value }
            }

            // when
            val rendered = events.renderYaml()
            val reparsed = flowOf(rendered).parseYaml()

            // then
            rendered sameAs "k: \"$value\"\n"
            reparsed.mergeAdjacentText() sameAs events
        }
    }

    @Test
    fun `should quote a key a YAML 1_1 reader would type`() = runTest {
        // given — Psych reads booleans and nulls in any letter case
        val events = semanticEvents {
            "entry"("key" to "yEs") { +"v" }
            "entry"("key" to "nULL") { +"v" }
        }

        // when
        val rendered = events.renderYaml()
        val reparsed = flowOf(rendered).parseYaml()

        // then
        rendered sameAs "\"yEs\": v\n\"nULL\": v\n"
        reparsed.mergeAdjacentText() sameAs events
    }

    @Test
    fun `should not quote a one-letter boolean key`() = runTest {
        // given — go-yaml v2 reads a plain `y` / `n` value as a boolean, but
        // decodes a front matter key into a string, and neither Psych nor
        // PyYAML types them at all
        val source = "x: 1\ny: 2\nn: 3\nY: 4\nN: 5\n"

        // when
        val rendered = flowOf(source).parseYaml().renderYaml()

        // then
        rendered sameAs source
    }

    @Test
    fun `should keep YAML 1_1 typed front matter values as written`() = runTest {
        // given — Jekyll's documented date format, a short date, go-yaml v2
        // booleans and Psych numbers: the parser types them, so the writer
        // writes them back bare instead of quoting them into strings
        val source = """
            date: 2016-01-01 12:00:00 -0500
            short: 2024-5-1
            answer: y
            mixed: yEs
            big: 1_000
            time: 12:30
            offset: 2024-01-01 10:00:00 +05:99
        """.trimIndent() + "\n"

        // when
        val rendered = flowOf(source).parseYaml().renderYaml()

        // then
        rendered sameAs source
    }

    @Test
    fun `should quote a string go-yaml v2 reads as a number once underscores are removed`() = runTest {
        // given
        val values = listOf("1_e5", "1e5_", "1_E5", "+_1", "+_1.", "0_x1", "0X1F")
        for (value in values) {
            val events = semanticEvents {
                "entry"("key" to "k") { +value }
            }

            // when
            val rendered = events.renderYaml()
            val reparsed = flowOf(rendered).parseYaml()

            // then
            rendered sameAs "k: \"$value\"\n"
            reparsed.mergeAdjacentText() sameAs events
        }
    }

    @Test
    fun `should not quote a shape no reader types`() = runTest {
        // given
        val values = listOf(
            "1,", "1,,2", "._0", ".e5", "2024-2-30", "2024-5-32", "2024-01-01 10:00:00 +530",
        )
        for (value in values) {
            val events = semanticEvents {
                "entry"("key" to "k") { +value }
            }

            // when
            val rendered = events.renderYaml()
            val reparsed = flowOf(rendered).parseYaml()

            // then
            rendered sameAs "k: $value\n"
            reparsed.mergeAdjacentText() sameAs events
        }
    }

    @Test
    fun `should DIVERGENCE quote a plain scalar a reader refuses to load`() = runTest {
        // given — the parser leaves these strings (no reader has a type for
        // them), but PyYAML or Psych refuse the whole document when they are
        // plain, so the writer quotes them and the first render rewrites the
        // source; PyYAML refuses a timestamp shape out of range
        val source = """
            a: =
            b: <<
            c: 2024-13-45
            d: 2024-01-01 24:30:00
            e: 0x_
            f: .e+4
            g: 2024-01-01 10:00:00 +23:99
        """.trimIndent() + "\n"

        // when
        val rendered = flowOf(source).parseYaml().renderYaml()

        // then
        rendered sameAs """
            a: "="
            b: "<<"
            c: "2024-13-45"
            d: "2024-01-01 24:30:00"
            e: "0x_"
            f: ".e+4"
            g: "2024-01-01 10:00:00 +23:99"
        """.trimIndent() + "\n"
    }

    @Test
    fun `should not quote a float shape without a digit`() = runTest {
        // given
        val values = listOf(".", "+.", "._")
        for (value in values) {
            val events = semanticEvents {
                "entry"("key" to "k") { +value }
            }

            // when
            val rendered = events.renderYaml()
            val reparsed = flowOf(rendered).parseYaml()

            // then
            rendered sameAs "k: $value\n"
            reparsed.mergeAdjacentText() sameAs events
        }
    }

    @Test
    fun `should escape every character YAML does not allow in a document`() = runTest {
        // given — C1 controls (mojibake like a Windows-1252 apostrophe read
        // as Latin-1) and the U+FFFE / U+FFFF non-characters are outside
        // YAML's printable set (YAML 1.2 §5.1): Psych and PyYAML refuse
        // the whole document when they appear raw, even in a block scalar;
        // a byte order mark must not appear inside a document (§5.2)
        val values = listOf(
            "It\u0092s" to "\"It\\u0092s\"",
            "a\u0080b" to "\"a\\u0080b\"",
            "\u009f" to "\"\\u009f\"",
            "x\uFFFEy" to "\"x\\ufffey\"",
            "x\uFFFF" to "\"x\\uffff\"",
            "first\u0092\nsecond" to "\"first\\u0092\\nsecond\"",
            "\uFEFFTitle" to "\"\\ufeffTitle\"",
            "first\n\uFEFFsecond" to "\"first\\n\\ufeffsecond\"",
            "smile 😀" to "smile 😀",
        )
        for ((value, expected) in values) {
            val events = semanticEvents {
                "entry"("key" to "k") { +value }
            }

            // when
            val rendered = events.renderYaml()
            val reparsed = flowOf(rendered).parseYaml()

            // then
            rendered sameAs "k: $expected\n"
            reparsed.mergeAdjacentText() sameAs events
        }
    }

    @Test
    fun `should DIVERGENCE write a lone surrogate as the replacement character`() = runTest {
        // given — a lone surrogate (a JS string cut mid-pair) has no YAML
        // representation: raw it is outside the printable set, and Psych and
        // go-yaml refuse its `\u` escape as an invalid code point, so either
        // loses the whole document; U+FFFD keeps it loadable, but the
        // surrogate does not survive the round-trip
        val values = listOf(
            "lone \uD800 high" to "lone \uFFFD high",
            "lone \uDC00 low" to "lone \uFFFD low",
            "\uDC00\uD800" to "\uFFFD\uFFFD",
        )
        for ((value, replaced) in values) {
            val events = semanticEvents {
                "entry"("key" to "k") { +value }
            }

            // when
            val rendered = events.renderYaml()
            val reparsed = flowOf(rendered).parseYaml()

            // then
            rendered sameAs "k: \"$replaced\"\n"
            reparsed.mergeAdjacentText() sameAs semanticEvents {
                "entry"("key" to "k") { +replaced }
            }
        }
    }

    @Test
    fun `should DIVERGENCE write a lone surrogate in a key as the replacement character`() = runTest {
        // given
        val events = semanticEvents {
            "entry"("key" to "a\uD800") { +"v" }
        }

        // when
        val rendered = events.renderYaml()

        // then
        rendered sameAs "\"a\uFFFD\": v\n"
    }

    @Test
    fun `should round-trip keys that need quoting`() = runTest {
        // given
        val keys = listOf(
            "og:title", "a b", "yes", "42", "- dash", "page.section", "título", "", "with \"quote\"",
        )
        for (key in keys) {
            val events = semanticEvents {
                "entry"("key" to key) { +"v" }
            }

            // when
            val reparsed = flowOf(events.renderYaml()).parseYaml()

            // then
            reparsed.mergeAdjacentText() sameAs events
        }
    }

    @Test
    fun `should round-trip every value shape`() = runTest {
        // given — typed scalars, empty string, null, empty collections,
        // nested mappings and sequences, a verbatim line
        val events = semanticEvents {
            "entry"("key" to "draft", "type" to "bool") { +"true" }
            "entry"("key" to "count", "type" to "int") { +"42" }
            "entry"("key" to "ratio", "type" to "float") { +"0.5" }
            "entry"("key" to "nothing", "type" to "null") { +"null" }
            "entry"("key" to "when", "type" to "timestamp") { +"2026-05-10" }
            "entry"("key" to "sub") { }
            "entry"("key" to "missing", "type" to "null") { }
            "entry"("key" to "tags", "type" to "seq") { }
            "entry"("key" to "meta", "type" to "map") { }
            "entry"("key" to "author") {
                "entry"("key" to "name") { +"Alice" }
                "entry"("key" to "links") {
                    "item" { +"a" }
                    "item"("type" to "int") { +"1" }
                    "item"("type" to "null") { }
                    "item" { }
                }
            }
            "entry"("key" to "authors") {
                "item" {
                    "entry"("key" to "name") { +"Alice" }
                    "entry"("key" to "role") { +"dev" }
                }
                "item" {
                    "item" { +"nested" }
                    "item" { +"x\ny\n" }
                }
                "item" { +"plain" }
            }
            +"? complex\n: value\n"
        }

        // when
        val reparsed = flowOf(events.renderYaml()).parseYaml()

        // then
        reparsed.mergeAdjacentText() sameAs events
    }

    @Test
    fun `should round-trip a root sequence`() = runTest {
        // given
        val events = semanticEvents {
            "item" { +"a" }
            "item"("type" to "int") { +"1" }
            "item" {
                "entry"("key" to "name") { +"Alice" }
            }
            "item" {
                "item" { +"nested" }
            }
        }

        // when
        val reparsed = flowOf(events.renderYaml()).parseYaml()

        // then
        reparsed.mergeAdjacentText() sameAs events
    }

    @Test
    fun `should reach a fixpoint after the first render of a source document`() = runTest {
        // given — comments and the original quoting are canonicalized once
        val source = """
            title: 'Hello'   # trailing
            # a comment
            "og:title": Hello
            draft: yes
            tags: [a, b]
            desc: >
              folded
              text
        """.trimIndent()

        // when
        val first = flowOf(source).parseYaml().events()
        val rendered = flowOf(source).parseYaml().renderYaml()
        val second = flowOf(rendered).parseYaml().events()
        val renderedAgain = flowOf(rendered).parseYaml().renderYaml()

        // then
        rendered sameAs """
            title: Hello
            "og:title": Hello
            draft: yes
            tags:
              - a
              - b
            desc: |
              folded text
        """.trimIndent() + "\n"
        renderedAgain sameAs rendered
        assert(first == second)
    }

    private suspend fun Flow<SemanticEvent>.events(): List<SemanticEvent> =
        mergeAdjacentText().toList()
}
