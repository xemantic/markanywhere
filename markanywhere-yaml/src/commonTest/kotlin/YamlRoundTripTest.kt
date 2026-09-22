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
