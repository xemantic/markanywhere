/*
 * Copyright 2025 Kazimierz Pogoda / Xemantic
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

package com.xemantic.markanywhere.flow

import com.xemantic.kotlin.test.assert
import com.xemantic.kotlin.test.sameAs
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SemanticEventFlowTest {

    @Test
    fun `should produce 0 events when empty`() = runTest {
        // when
        val flow = semanticEvents {}

        // then
        assert(flow.count() == 0)
    }

    @Test
    fun `should emit text event using text function`() = runTest {
        // when
        val events = semanticEvents {
            text("hello")
        }

        // then
        events.toJsonLines() sameAs """{"type":"text","text":"hello"}"""
    }

    @Test
    fun `should emit text event using unaryPlus String operator`() = runTest {
        // when
        val events = semanticEvents {
            +"hello world"
        }

        // then
        events.toJsonLines() sameAs """{"type":"text","text":"hello world"}"""
    }

    @Test
    fun `should emit text event using unaryPlus Char operator`() = runTest {
        // when
        val events = semanticEvents {
            +'X'
        }

        // then
        events.toJsonLines() sameAs """{"type":"text","text":"X"}"""
    }

    @Test
    fun `should emit mark event`() = runTest {
        // when
        val events = semanticEvents {
            mark("em")
        }

        // then
        events.toJsonLines() sameAs """{"type":"mark","name":"em"}"""
    }

    @Test
    fun `should emit mark event with isTag true`() = runTest {
        // when
        val events = semanticEvents {
            mark("em", isTag = true)
        }

        // then
        events.toJsonLines() sameAs """{"type":"mark","name":"em","isTag":true}"""
    }

    @Test
    fun `should emit mark event with attributes`() = runTest {
        // when
        val events = semanticEvents {
            mark("a", isTag = false, "href" to "https://example.com", "title" to "Example")
        }

        // then
        events.toJsonLines() sameAs """{"type":"mark","name":"a","attributes":{"href":"https://example.com","title":"Example"}}"""
    }

    @Test
    fun `should emit unmark event`() = runTest {
        // when
        val events = semanticEvents {
            unmark("em")
        }

        // then
        events.toJsonLines() sameAs """{"type":"unmark","name":"em"}"""
    }

    @Test
    fun `should emit unmark event with isTag true`() = runTest {
        // when
        val events = semanticEvents {
            unmark("em", isTag = true)
        }

        // then
        events.toJsonLines() sameAs """{"type":"unmark","name":"em","isTag":true}"""
    }

    @Test
    fun `should emit mark text unmark using String invoke DSL`() = runTest {
        // when
        val events = semanticEvents {
            "strong" {
                +"bold text"
            }
        }

        // then
        events.toJsonLines() sameAs """
            {"type":"mark","name":"strong"}
            {"type":"text","text":"bold text"}
            {"type":"unmark","name":"strong"}
        """.trimIndent()
    }

    @Test
    fun `should emit mark text unmark using String invoke DSL with attributes`() = runTest {
        // when
        val events = semanticEvents {
            "a"("href" to "https://example.com") {
                +"link text"
            }
        }

        // then
        events.toJsonLines() sameAs """
            {"type":"mark","name":"a","attributes":{"href":"https://example.com"}}
            {"type":"text","text":"link text"}
            {"type":"unmark","name":"a"}
        """.trimIndent()
    }

    @Test
    fun `should emit tag events with isTag true using tag function`() = runTest {
        // when
        val events = semanticEvents {
            tag("div") {
                +"content"
            }
        }

        // then
        events.toJsonLines() sameAs """
            {"type":"mark","name":"div","isTag":true}
            {"type":"text","text":"content"}
            {"type":"unmark","name":"div","isTag":true}
        """.trimIndent()
    }

    @Test
    fun `should emit tag events with attributes using tag function`() = runTest {
        // when
        val events = semanticEvents {
            tag("div", "class" to "container", "id" to "main") {
                +"content"
            }
        }

        // then
        events.toJsonLines() sameAs """
            {"type":"mark","name":"div","isTag":true,"attributes":{"class":"container","id":"main"}}
            {"type":"text","text":"content"}
            {"type":"unmark","name":"div","isTag":true}
        """.trimIndent()
    }

    @Test
    fun `should use produceTags flag for default isTag value`() = runTest {
        // when
        val events = semanticEvents(produceTags = true) {
            mark("span")
            +"text"
            unmark("span")
        }

        // then
        events.toJsonLines() sameAs """
            {"type":"mark","name":"span","isTag":true}
            {"type":"text","text":"text"}
            {"type":"unmark","name":"span","isTag":true}
        """.trimIndent()
    }

    @Test
    fun `should emit nested events`() = runTest {
        // when
        val events = semanticEvents {
            "p" {
                +"Hello "
                "strong" {
                    +"world"
                }
                +'!'
            }
        }

        // then
        events.toJsonLines() sameAs """
            {"type":"mark","name":"p"}
            {"type":"text","text":"Hello "}
            {"type":"mark","name":"strong"}
            {"type":"text","text":"world"}
            {"type":"unmark","name":"strong"}
            {"type":"text","text":"!"}
            {"type":"unmark","name":"p"}
        """.trimIndent()
    }

    @Test
    fun `should convert flow of semantic events to JSON lines`() = runTest {
        // when
        val jsonLines = semanticEvents {
            "em" {
                +"emphasized"
            }
        }.toJsonLines()

        // then
        jsonLines sameAs """
            {"type":"mark","name":"em"}
            {"type":"text","text":"emphasized"}
            {"type":"unmark","name":"em"}
        """.trimIndent()
    }

}
