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

package com.xemantic.markanywhere.flow

import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class DropStructuralWhitespaceTest {

    @Test
    fun `should drop whitespace-only text events between sibling marks`() = runTest {
        // given
        val input = semanticEvents {
            "p" { +"x" }
            +"\n  "
            "p" { +"y" }
        }

        // when
        val output = input.dropStructuralWhitespace()

        // then
        output sameAs semanticEvents {
            "p" { +"x" }
            "p" { +"y" }
        }
    }

    @Test
    fun `should drop whitespace-only text events at start and end of stream`() = runTest {
        // given
        val input = semanticEvents {
            +"\n  \t"
            "p" { +"Hello" }
            +"\n"
        }

        // when
        val output = input.dropStructuralWhitespace()

        // then
        output sameAs semanticEvents {
            "p" { +"Hello" }
        }
    }

    @Test
    fun `should preserve whitespace between two non-whitespace text events`() = runTest {
        // given
        // Some parsers emit "Hello", " ", "World" as three separate events.
        // The interior space is significant — keep it.
        val input = semanticEvents {
            "p" {
                +"Hello"
                +" "
                +"World"
            }
        }

        // when
        val output = input.dropStructuralWhitespace()

        // then
        output sameAs semanticEvents {
            "p" {
                +"Hello"
                +" "
                +"World"
            }
        }
    }

    @Test
    fun `should preserve text whose content is not pure whitespace`() = runTest {
        // given
        // A text event with leading whitespace but non-whitespace content is
        // preserved verbatim — we don't trim interior whitespace.
        val input = semanticEvents {
            "p" { +"  Hello\n  " }
        }

        // when
        val output = input.dropStructuralWhitespace()

        // then
        output sameAs semanticEvents {
            "p" { +"  Hello\n  " }
        }
    }

    @Test
    fun `should drop whitespace-only text inside a mark that has no other content`() = runTest {
        // given
        val input = semanticEvents {
            "a"("href" to "/x") { +"\n  " }
        }

        // when
        val output = input.dropStructuralWhitespace()

        // then
        output sameAs semanticEvents {
            "a"("href" to "/x") { }
        }
    }

    @Test
    fun `should preserve whitespace inside a mark named by the set overload`() = runTest {
        // given
        val input = semanticEvents {
            "pre" { +"\n    indented code\n" }
        }

        // when
        val output = input.dropStructuralWhitespace(preserveWithin = setOf("pre"))

        // then
        output sameAs semanticEvents {
            "pre" { +"\n    indented code\n" }
        }
    }

    @Test
    fun `should preserve whitespace nested inside a preserved subtree`() = runTest {
        // given
        // Whitespace inside a nested child of <pre> is still significant.
        val input = semanticEvents {
            "pre" {
                "code" { +"\n    line\n" }
            }
        }

        // when
        val output = input.dropStructuralWhitespace(preserveWithin = setOf("pre"))

        // then
        output sameAs semanticEvents {
            "pre" {
                "code" { +"\n    line\n" }
            }
        }
    }

    @Test
    fun `should resume dropping after the preserved subtree closes`() = runTest {
        // given
        val input = semanticEvents {
            "pre" { +"\n  kept\n" }
            +"\n  "
            "p" { +"y" }
        }

        // when
        val output = input.dropStructuralWhitespace(preserveWithin = setOf("pre"))

        // then
        output sameAs semanticEvents {
            "pre" { +"\n  kept\n" }
            "p" { +"y" }
        }
    }

    @Test
    fun `should select the preserved subtree via a predicate over the mark`() = runTest {
        // given
        // The predicate can inspect anything on the mark — here, an attribute.
        val input = semanticEvents {
            "span"("class" to "ws") { +"\n  kept\n" }
            "span" { +"\n  " }
        }

        // when
        val output = input.dropStructuralWhitespace { it["class"] == "ws" }

        // then
        output sameAs semanticEvents {
            "span"("class" to "ws") { +"\n  kept\n" }
            "span" { }
        }
    }

}
