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

package com.xemantic.markanywhere.transform

import com.xemantic.kotlin.test.assert
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import com.xemantic.markanywhere.textContent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class TransformMatchingMarksTest {

    @Test
    fun `should leave a flow without any matching mark untouched`() = runTest {
        // given - nothing in this flow is named "i"
        fun input() = semanticEvents {
            "p" {
                +"hello "
                "em" { +"world" }
            }
        }

        // when - the matcher never fires, so the transformer is never invoked
        val transformed = input().transformMatchingMarks { mark ->
            if (mark.name == "i") {
                { error("transformer must not be called") }
            } else null
        }

        // then - the whole stream, including the nested em, flows through verbatim
        transformed sameAs input()
    }

    @Test
    fun `should collapse a matched leaf mark into replacement text`() = runTest {
        // given - an empty icon element sitting between paragraph text
        val events = semanticEvents {
            "p" {
                +"Versionen "
                "i"("class" to "icon") {}
            }
        }

        // when - the icon collapses into a single token, surrounding text is untouched
        val transformed = events.transformMatchingMarks { mark ->
            if (mark.name == "i") {
                { +"🔵" }
            } else null
        }

        // then
        transformed sameAs semanticEvents {
            "p" {
                +"Versionen "
                +"🔵"
            }
        }
    }

    @Test
    fun `should choose the replacement from the matched mark's attribute`() = runTest {
        // given - two icons distinguished only by their class
        val events = semanticEvents {
            "p" {
                "i"("class" to "fa-info") {}
                +" / "
                "i"("class" to "fa-warning") {}
            }
        }

        // when - the transformer reads the class off the opening mark in the buffer
        val transformed = events.transformMatchingMarks { openingMark ->
            if (openingMark.name == "i") {
                { buffered ->
                    val mark = buffered.first() as SemanticEvent.Mark
                    +when (mark["class"]) {
                        "fa-info" -> "ℹ️"
                        "fa-warning" -> "⚠️"
                        else -> "?"
                    }
                }
            } else null
        }

        // then
        transformed sameAs semanticEvents {
            "p" {
                +"ℹ️"
                +" / "
                +"⚠️"
            }
        }
    }

    @Test
    fun `should choose the replacement from the matched element's inner text`() = runTest {
        // given - a span icon whose name is carried as inner text, split across two events
        val events = semanticEvents {
            "span"("class" to "icon") {
                +"in"
                +"fo"
            }
        }

        // when - the transformer reads the flattened content of the buffered subtree
        val transformed = events.transformMatchingMarks { mark ->
            if (mark["class"] == "icon") {
                { buffered ->
                    +if (buffered.textContent() == "info") "ℹ️" else "?"
                }
            } else null
        }

        // then
        transformed sameAs semanticEvents {
            +"ℹ️"
        }
    }

    @Test
    fun `should drop the matched element entirely when the transformer emits nothing`() = runTest {
        // given
        val events = semanticEvents {
            "p" {
                +"a"
                "i"("class" to "x") { +"ignored" }
                +"b"
            }
        }

        // when - the transformer body is empty, so the matched element vanishes
        val transformed = events.transformMatchingMarks { mark ->
            if (mark.name == "i") {
                { /* emit nothing */ }
            } else null
        }

        // then
        transformed sameAs semanticEvents {
            "p" {
                +"a"
                +"b"
            }
        }
    }

    @Test
    fun `should re-wrap the matched element preserving its content`() = runTest {
        // given - an HTML-ish bold element to be rewritten as emphasis
        val events = semanticEvents {
            "b" {
                +"keep "
                "code" { +"this" }
            }
        }

        // when - the transformer replays the buffered content (minus the outer
        //   mark/unmark) inside a fresh em
        val transformed = events.transformMatchingMarks { mark ->
            if (mark.name == "b") {
                { buffered ->
                    "em" {
                        emit(buffered.subList(1, buffered.size - 1))
                    }
                }
            } else null
        }

        // then - the inner code subtree survives, only the wrapper changed
        transformed sameAs semanticEvents {
            "em" {
                +"keep "
                "code" { +"this" }
            }
        }
    }

    @Test
    fun `should keep the stream balanced when a matched element wraps a non-matched child`() = runTest {
        // given - the regression case: a matched span wrapping a non-matched anchor.
        //   A naive per-unmark pop would mis-close on the inner </a>; the depth
        //   counter must hold suppression until the outer </span>.
        val events = semanticEvents {
            "span"("class" to "icon") {
                "a"("href" to "/x") { +"y" }
            }
        }

        // when
        val transformed = events.transformMatchingMarks { mark ->
            if (mark["class"] == "icon") {
                { +"⭐" }
            } else null
        }

        // then - the entire subtree collapses to one token, with nothing leaked
        //   and no orphaned unmark trailing behind
        assert(transformed.toList() == listOf(SemanticEvent.Text("⭐")))
    }

    @Test
    fun `should pass the complete balanced subtree to the transformer`() = runTest {
        // given
        val events = semanticEvents {
            "span"("class" to "icon") {
                "a"("href" to "/x") { +"y" }
            }
        }

        // when - capture exactly what the transformer receives
        var captured: List<SemanticEvent>? = null
        events.transformMatchingMarks { mark ->
            if (mark.name == "span") {
                { buffered ->
                    captured = buffered
                }
            } else null
        }.toList() // cold flow - must collect to drive the transformer

        // then - the buffer is the whole subtree in document order, mark/unmark balanced
        assert(captured == listOf(
            SemanticEvent.Mark("span", attributes = mapOf("class" to "icon")),
            SemanticEvent.Mark("a", attributes = mapOf("href" to "/x")),
            SemanticEvent.Text("y"),
            SemanticEvent.Unmark("a"),
            SemanticEvent.Unmark("span"),
        ))
    }

    @Test
    fun `should fold a nested matching child into the outer match and transform once`() = runTest {
        // given - two nested elements that BOTH satisfy the matcher
        val events = semanticEvents {
            "div" {
                "div" { +"x" }
            }
        }

        // when - only the outermost match opens suppression; the inner div is folded
        //   into the same buffer rather than triggering a second transform
        var invocations = 0
        val transformed = events.transformMatchingMarks { mark ->
            if (mark.name == "div") {
                { buffered ->
                    invocations++
                    // the buffer holds both div marks and both unmarks
                    assert(buffered.count { it is Mark } == 2)
                    +"flat"
                }
            } else null
        }.toList()

        // then - the transformer ran exactly once over the merged subtree
        assert(invocations == 1)
        assert(transformed == listOf(SemanticEvent.Text("flat")))
    }

    @Test
    fun `should reset its buffer between sibling matched elements`() = runTest {
        // given - two sibling icons; if the buffer were not reset, the second
        //   transform would see the first element's events too
        val events = semanticEvents {
            "i" { +"a" }
            "i" { +"b" }
        }

        // when - each transform echoes only its own content, bracketed
        val transformed = events.transformMatchingMarks { mark ->
            if (mark.name == "i") {
                { buffered ->
                    +"[${buffered.textContent()}]"
                }
            } else null
        }

        // then - "[a]" then "[b]", never "[a]" then "[ab]"
        transformed sameAs semanticEvents {
            +"[a]"
            +"[b]"
        }
    }

    @Test
    fun `should transform matched marks while leaving surrounding marks and text intact`() = runTest {
        // given - an icon nested inside a paragraph, plus an untouched sibling block
        val events = semanticEvents {
            "p" {
                +"See "
                "i"("class" to "icon") {}
                +" the docs"
            }
            "div" {
                +"unrelated"
            }
        }

        // when
        val transformed = events.transformMatchingMarks { mark ->
            if (mark.name == "i") {
                { +"📄" }
            } else null
        }

        // then - only the icon changed; p, its text, and the sibling div are verbatim
        transformed sameAs semanticEvents {
            "p" {
                +"See "
                +"📄"
                +" the docs"
            }
            "div" {
                +"unrelated"
            }
        }
    }

    @Test
    fun `should let the transformer control the tagging of emitted marks`() = runTest {
        // given - a tagged (HTML-derived) icon element
        val events = semanticEvents(tagged = true) {
            "i"("class" to "icon") {}
        }

        // when - the transformer emits a tagged replacement element of its own choosing
        val transformed = events.transformMatchingMarks { mark ->
            if (mark.name == "i") {
                {
                    tag("abbr", "title" to "info") { +"ℹ️" }
                }
            } else null
        }

        // then
        transformed sameAs semanticEvents {
            tag("abbr", "title" to "info") { +"ℹ️" }
        }
    }

}