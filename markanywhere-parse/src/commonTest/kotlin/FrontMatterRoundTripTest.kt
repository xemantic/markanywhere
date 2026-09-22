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

import com.xemantic.kotlin.test.assert
import com.xemantic.kotlin.test.sameAs
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.render.renderMarkdown
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * The structured front matter must survive `renderMarkdown()` → `parse()`
 * unchanged. The YAML codec itself is pinned by `YamlRoundTripTest` in
 * `markanywhere-yaml`; this suite covers the fences around it — the
 * `frontmatter` wrapper the filter emits and the renderer writes, and the
 * hand-off to the Markdown body.
 */
class FrontMatterRoundTripTest {

    @Test
    fun `should round-trip every value shape`() = runTest {
        // given — typed scalars, empty string, null, empty collections,
        // nested mappings and sequences, a verbatim line
        val events = semanticEvents {
            "frontmatter" {
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
            "h1" { +"Body" }
        }

        // when
        val reparsed = flowOf(events.renderMarkdown()).parse()

        // then
        reparsed.mergeAdjacentText() sameAs events
    }

    @Test
    fun `should reach a fixpoint after the first render of a source document`() = runTest {
        // given — comments and the original quoting are canonicalized once
        val source = """
            ---
            title: 'Hello'   # trailing
            # a comment
            "og:title": Hello
            draft: yes
            tags: [a, b]
            desc: >
              folded
              text
            ---
            # Hello
        """.trimIndent()

        // when
        val first = flowOf(source).parse().events()
        val rendered = flowOf(source).parse().renderMarkdown()
        val second = flowOf(rendered).parse().events()
        val renderedAgain = flowOf(rendered).parse().renderMarkdown()

        // then
        rendered sameAs """
            ---
            title: Hello
            "og:title": Hello
            draft: yes
            tags:
              - a
              - b
            desc: |
              folded text
            ---

            # Hello
        """.trimIndent()
        renderedAgain sameAs rendered
        assert(first == second)
    }

    private suspend fun Flow<SemanticEvent>.events(): List<SemanticEvent> =
        mergeAdjacentText().toList()
}
