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

package com.xemantic.markanywhere.test

import com.xemantic.kotlin.test.assert
import com.xemantic.kotlin.test.sameAs
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.flow.semanticEvents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class MarkanywhereTestsTest {

    @Test
    fun `should pass when comparing identical empty flows`() = runTest {
        // given
        val actual: Flow<SemanticEvent> = emptyFlow()
        val expected: Flow<SemanticEvent> = emptyFlow()

        // then (no exception)
        actual sameAs expected
    }

    @Test
    fun `should pass when comparing identical single text event flows`() = runTest {
        // given
        val actual = semanticEvents { +"Hello World" }
        val expected = semanticEvents { +"Hello World" }

        // then (no exception)
        actual sameAs expected
    }

    @Test
    fun `should pass when comparing identical mark-text-unmark flows`() = runTest {
        // given
        val actual = semanticEvents {
            "p" {
                +"Hello World"
            }
        }
        val expected = semanticEvents {
            "p" {
                +"Hello World"
            }
        }

        // then (no exception)
        actual sameAs expected
    }

    @Test
    fun `should pass when comparing identical nested structure flows`() = runTest {
        // given
        val actual = semanticEvents {
            "div" {
                "p" {
                    +"First paragraph"
                }
                "p" {
                    +"Second paragraph"
                }
            }
        }
        val expected = semanticEvents {
            "div" {
                "p" {
                    +"First paragraph"
                }
                "p" {
                    +"Second paragraph"
                }
            }
        }

        // then (no exception)
        actual sameAs expected
    }

    @Test
    fun `should pass when comparing flows with attributes`() = runTest {
        // given
        val actual = semanticEvents {
            "a"("href" to "https://example.com", "target" to "_blank") {
                +"Click here"
            }
        }
        val expected = semanticEvents {
            "a"("href" to "https://example.com", "target" to "_blank") {
                +"Click here"
            }
        }

        // then (no exception)
        actual sameAs expected
    }

    @Test
    fun `should fail when null flow is compared with non-empty expected`() = runTest {
        // given
        val actual: Flow<SemanticEvent>? = null
        val expected = semanticEvents { +"Expected text" }

        // when
        val error = assertFailsWith<AssertionError> {
            actual sameAs expected
        }

        // then
        assert(error.message == """The Flow<SemanticEvent> is null, but expected to be: {"type":"text","text":"Expected text"}""")
    }

    @Test
    fun `should fail when text content differs`() = runTest {
        // given
        val actual = semanticEvents { +"Hello" }
        val expected = semanticEvents { +"World" }

        // when
        val error = assertFailsWith<AssertionError> {
            actual sameAs expected
        }

        // then
        error.message sameAs """
            --- expected
            +++ actual
            @@ -1 +1 @@
            -{"type":"text","text":"World"}
            \ No newline at end of file
            +{"type":"text","text":"Hello"}
            \ No newline at end of file
            
        """.trimIndent()
    }

    @Test
    fun `should fail when mark names differ`() = runTest {
        // given
        val actual = semanticEvents {
            "p" {
                +"Text"
            }
        }
        val expected = semanticEvents {
            "div" {
                +"Text"
            }
        }

        // when
        val error = assertFailsWith<AssertionError> {
            actual sameAs expected
        }

        // then
        error.message sameAs """
            --- expected
            +++ actual
            @@ -1,3 +1,3 @@
            -{"type":"mark","name":"div"}
            +{"type":"mark","name":"p"}
             {"type":"text","text":"Text"}
            -{"type":"unmark","name":"div"}
            \ No newline at end of file
            +{"type":"unmark","name":"p"}
            \ No newline at end of file

        """.trimIndent()
    }

    @Test
    fun `should fail when attributes differ`() = runTest {
        // given
        val actual = semanticEvents {
            "a"("href" to "https://foo.com") {
                +"Link"
            }
        }
        val expected = semanticEvents {
            "a"("href" to "https://bar.com") {
                +"Link"
            }
        }

        // when
        val error = assertFailsWith<AssertionError> {
            actual sameAs expected
        }

        // then
        error.message sameAs """
            --- expected
            +++ actual
            @@ -1,3 +1,3 @@
            -{"type":"mark","name":"a","attributes":{"href":"https://bar.com"}}
            +{"type":"mark","name":"a","attributes":{"href":"https://foo.com"}}
             {"type":"text","text":"Link"}
             {"type":"unmark","name":"a"}

        """.trimIndent()
    }

    @Test
    fun `should fail when actual has more events than expected`() = runTest {
        // given
        val actual = semanticEvents {
            +"First"
            +"Second"
        }
        val expected = semanticEvents {
            +"First"
        }

        // when
        val error = assertFailsWith<AssertionError> {
            actual sameAs expected
        }

        // then
        error.message sameAs """
            --- expected
            +++ actual
            @@ -1 +1,2 @@
            -{"type":"text","text":"First"}
            \ No newline at end of file
            +{"type":"text","text":"First"}
            +{"type":"text","text":"Second"}
            \ No newline at end of file

        """.trimIndent()
    }

    @Test
    fun `should fail when actual has fewer events than expected`() = runTest {
        // given
        val actual = semanticEvents {
            +"First"
        }
        val expected = semanticEvents {
            +"First"
            +"Second"
        }

        // when
        val error = assertFailsWith<AssertionError> {
            actual sameAs expected
        }

        // then
        error.message sameAs """
            --- expected
            +++ actual
            @@ -1,2 +1 @@
            -{"type":"text","text":"First"}
            -{"type":"text","text":"Second"}
            \ No newline at end of file
            +{"type":"text","text":"First"}
            \ No newline at end of file

        """.trimIndent()
    }

    @Test
    fun `should fail when structure differs`() = runTest {
        // given
        val actual = semanticEvents {
            "p" {
                "strong" {
                    +"Bold text"
                }
            }
        }
        val expected = semanticEvents {
            "p" {
                "em" {
                    +"Bold text"
                }
            }
        }

        // when
        val error = assertFailsWith<AssertionError> {
            actual sameAs expected
        }

        // then
        error.message sameAs """
            --- expected
            +++ actual
            @@ -1,5 +1,5 @@
             {"type":"mark","name":"p"}
            -{"type":"mark","name":"em"}
            +{"type":"mark","name":"strong"}
             {"type":"text","text":"Bold text"}
            -{"type":"unmark","name":"em"}
            +{"type":"unmark","name":"strong"}
             {"type":"unmark","name":"p"}

        """.trimIndent()
    }

    @Test
    fun `should pass when comparing flows with isTag flag`() = runTest {
        // given
        val actual = semanticEvents(produceTags = true) {
            "div" {
                +"Content"
            }
        }
        val expected = semanticEvents(produceTags = true) {
            "div" {
                +"Content"
            }
        }

        // then (no exception)
        actual sameAs expected
    }

    @Test
    fun `should fail when isTag flag differs`() = runTest {
        // given
        val actual = semanticEvents(produceTags = true) {
            "div" {
                +"Content"
            }
        }
        val expected = semanticEvents(produceTags = false) {
            "div" {
                +"Content"
            }
        }

        // when
        val error = assertFailsWith<AssertionError> {
            actual sameAs expected
        }

        // then
        error.message sameAs """
            --- expected
            +++ actual
            @@ -1,3 +1,3 @@
            -{"type":"mark","name":"div"}
            +{"type":"mark","name":"div","isTag":true}
             {"type":"text","text":"Content"}
            -{"type":"unmark","name":"div"}
            \ No newline at end of file
            +{"type":"unmark","name":"div","isTag":true}
            \ No newline at end of file

        """.trimIndent()
    }

    @Test
    fun `should pass when comparing complex nested flows with multiple elements`() = runTest {
        // given
        val actual = semanticEvents {
            "html" {
                "head" {
                    "title" {
                        +"Page Title"
                    }
                }
                "body" {
                    "h1" {
                        +"Main Heading"
                    }
                    "p" {
                        +"First paragraph with "
                        "strong" {
                            +"bold"
                        }
                        +" text."
                    }
                    "ul" {
                        "li" {
                            +"Item 1"
                        }
                        "li" {
                            +"Item 2"
                        }
                    }
                }
            }
        }
        val expected = semanticEvents {
            "html" {
                "head" {
                    "title" {
                        +"Page Title"
                    }
                }
                "body" {
                    "h1" {
                        +"Main Heading"
                    }
                    "p" {
                        +"First paragraph with "
                        "strong" {
                            +"bold"
                        }
                        +" text."
                    }
                    "ul" {
                        "li" {
                            +"Item 1"
                        }
                        "li" {
                            +"Item 2"
                        }
                    }
                }
            }
        }

        // then (no exception)
        actual sameAs expected
    }

}