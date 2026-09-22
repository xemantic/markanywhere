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

package com.xemantic.markanywhere.html

import com.xemantic.kotlin.test.sameAs
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.parse.parse
import com.xemantic.markanywhere.render.renderMarkdown
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * [ensureFrontmatterTitle] guarantees that a stream carrying a leading `h1`
 * starts with a `frontmatter` mark holding a top-level `title` entry —
 * deriving a missing title from the `h1` text and synthesizing a frontmatter
 * when none exists at all.
 */
class EnsureFrontmatterTitleTest {

    @Test
    fun `should pass through a frontmatter that already defines a title`() = runTest {
        // given
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Existing" }
                "entry"("key" to "author") { +"Alice" }
            }
            "h1" { +"Heading" }
            "p" { +"Body." }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Existing" }
                "entry"("key" to "author") { +"Alice" }
            }
            "h1" { +"Heading" }
            "p" { +"Body." }
        }
    }

    @Test
    fun `should inject the first h1 text as title into a titleless frontmatter`() = runTest {
        // given
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "author") { +"Alice" }
            }
            "h1" { +"Hello" }
            "p" { +"Body." }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Hello" }
                "entry"("key" to "author") { +"Alice" }
            }
            "h1" { +"Hello" }
            "p" { +"Body." }
        }
    }

    @Test
    fun `should synthesize a frontmatter from the first h1 when none exists`() = runTest {
        // given
        val input = semanticEvents {
            "h1" { +"Hello" }
            "p" { +"Body." }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Hello" }
            }
            "h1" { +"Hello" }
            "p" { +"Body." }
        }
    }

    @Test
    fun `should leave a stream without frontmatter and without leading h1 untouched`() = runTest {
        // given
        val input = semanticEvents {
            "p" { +"Body." }
            "h1" { +"Late heading" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "p" { +"Body." }
            "h1" { +"Late heading" }
        }
    }

    @Test
    fun `should flush a titleless frontmatter unchanged when the next block is not an h1`() = runTest {
        // given
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "author") { +"Alice" }
            }
            "p" { +"Body." }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "author") { +"Alice" }
            }
            "p" { +"Body." }
        }
    }

    @Test
    fun `should collapse inline markup in the h1 to plain text`() = runTest {
        // given
        val input = semanticEvents {
            "h1" {
                +"Hello "
                "em" { +"semantic" }
                +" world"
            }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Hello semantic world" }
            }
            "h1" {
                +"Hello "
                "em" { +"semantic" }
                +" world"
            }
        }
    }

    @Test
    fun `should keep a derived title verbatim leaving YAML quoting to the renderer`() = runTest {
        // given
        val input = semanticEvents {
            "h1" { +"Q: What is \"markanywhere\"?" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Q: What is \"markanywhere\"?" }
            }
            "h1" { +"Q: What is \"markanywhere\"?" }
        }
    }

    @Test
    fun `should trim and collapse whitespace in the derived title`() = runTest {
        // given
        val input = semanticEvents {
            "h1" {
                +"  Hello   "
                +" world "
            }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Hello world" }
            }
            "h1" {
                +"  Hello   "
                +" world "
            }
        }
    }

    @Test
    fun `should not mistake a nested title entry for a top-level one`() = runTest {
        // given — only a direct child `entry key=title` counts
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "author") {
                    "entry"("key" to "title") { +"Dr." }
                }
            }
            "h1" { +"Hello" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Hello" }
                "entry"("key" to "author") {
                    "entry"("key" to "title") { +"Dr." }
                }
            }
            "h1" { +"Hello" }
        }
    }

    @Test
    fun `should keep blank text between the frontmatter and the h1 in source order`() = runTest {
        // given
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "author") { +"Alice" }
            }
            +"\n"
            "h1" { +"Hello" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Hello" }
                "entry"("key" to "author") { +"Alice" }
            }
            +"\n"
            "h1" { +"Hello" }
        }
    }

    @Test
    fun `should not derive a title from an h1 without text`() = runTest {
        // given
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "author") { +"Alice" }
            }
            "h1" { }
            "p" { +"Body." }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "author") { +"Alice" }
            }
            "h1" { }
            "p" { +"Body." }
        }
    }

    @Test
    fun `should flush a titleless frontmatter at the end of the stream`() = runTest {
        // given
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "author") { +"Alice" }
            }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "author") { +"Alice" }
            }
        }
    }

    @Test
    fun `should synthesize a frontmatter for parsed Markdown starting with an h1`() = runTest {
        // given
        val document = flowOf("# Hello\n\nSome text.")

        // when
        val markdown = document.parse().ensureFrontmatterTitle().renderMarkdown()

        // then
        markdown sameAs """
            ---
            title: Hello
            ---

            # Hello

            Some text.
        """.trimIndent()
    }

    @Test
    fun `should inject a title into parsed Markdown with a titleless frontmatter`() = runTest {
        // given
        val document = flowOf(
            """
            ---
            author: Alice
            ---

            # Hello

            Some text.
            """.trimIndent()
        )

        // when
        val markdown = document.parse().ensureFrontmatterTitle().renderMarkdown()

        // then
        markdown sameAs """
            ---
            title: Hello
            author: Alice
            ---

            # Hello

            Some text.
        """.trimIndent()
    }
}
