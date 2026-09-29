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
    fun `should keep a non-breaking space inside the derived title trimming the edges`() = runTest {
        // given — NBSP inside is content, not HTML whitespace, as for a
        // <title> read by simplifyHtml; at the edges (typically after an
        // icon) it is padding, which would only force the title into quotes
        val input = semanticEvents {
            "h1" { +"\u00A0Foo\u00A0Bar\u00A0\n" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Foo\u00A0Bar" }
            }
            "h1" { +"\u00A0Foo\u00A0Bar\u00A0\n" }
        }
    }

    @Test
    fun `should not derive a title from a heading of non-breaking spaces`() = runTest {
        // given
        val input = semanticEvents {
            "h1" { +"\u00A0\u00A0" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "h1" { +"\u00A0\u00A0" }
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
    fun `should include the alt of an image in the h1 in the derived title`() = runTest {
        // given — the heading's accessible name is its text plus the image's alt
        val input = semanticEvents {
            "h1" {
                +"Logo "
                "img"("alt" to "Acme", "src" to "logo.png") { }
            }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Logo Acme" }
            }
            "h1" {
                +"Logo "
                "img"("alt" to "Acme", "src" to "logo.png") { }
            }
        }
    }

    @Test
    fun `should derive the title from an image-only h1`() = runTest {
        // given — a masthead: a link wrapping a named graphic, no text at all
        val input = semanticEvents {
            "h1" {
                "a"("href" to "/") {
                    "img"("alt" to "News") { }
                }
            }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"News" }
            }
            "h1" {
                "a"("href" to "/") {
                    "img"("alt" to "News") { }
                }
            }
        }
    }

    @Test
    fun `should derive the title from parsed Markdown whose h1 holds an image`() = runTest {
        // given
        val document = flowOf("# ![Acme](logo.png) Docs\n\nSome text.")

        // when
        val markdown = document.parse().ensureFrontmatterTitle().renderMarkdown()

        // then
        markdown sameAs """
            ---
            title: Acme Docs
            ---

            # ![Acme](logo.png) Docs

            Some text.
        """.trimIndent()
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

    @Test
    fun `should synthesize the frontmatter before leading blank text`() = runTest {
        // given — a leading structural newline before the h1
        val input = semanticEvents {
            +"\n"
            "h1" { +"Hello" }
            "p" { +"Body." }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then — the frontmatter must be the very first event for
        // wrapInHtmlDocument to read it
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Hello" }
            }
            +"\n"
            "h1" { +"Hello" }
            "p" { +"Body." }
        }
    }

    @Test
    fun `should put a derived title into the head despite leading blank text`() = runTest {
        // given
        val input = semanticEvents {
            +"\n"
            "h1" { +"Hello" }
        }

        // when
        val output = input.ensureFrontmatterTitle().wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
                "head" {
                    "title" { +"Hello" }
                }
                "body" {
                    +"\n"
                    "h1" { +"Hello" }
                }
            }
        }
    }

    @Test
    fun `should replace a null title entry with the derived title`() = runTest {
        // given — a bare `title:` line
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "author") { +"Alice" }
                "entry"("key" to "title", "type" to "null") { }
                "entry"("key" to "draft", "type" to "bool") { +"true" }
            }
            "h1" { +"Hello" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then — replaced in place, not duplicated
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "author") { +"Alice" }
                "entry"("key" to "title") { +"Hello" }
                "entry"("key" to "draft", "type" to "bool") { +"true" }
            }
            "h1" { +"Hello" }
        }
    }

    @Test
    fun `should replace a blank title entry with the derived title`() = runTest {
        // given — `title: ""` and `title: "  "` carry no title
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"  " }
            }
            "h1" { +"Hello" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Hello" }
            }
            "h1" { +"Hello" }
        }
    }

    @Test
    fun `should respell a title entry in another letter case as title`() = runTest {
        // given — wrapInHtmlDocument reads a `Title` key as the title, but
        // front matter readers matching keys case-sensitively (Jekyll) do not
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "Title") { +"My Page" }
            }
            "h1" { +"Heading" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"My Page" }
            }
            "h1" { +"Heading" }
        }
    }

    @Test
    fun `should replace a blank title entry in any letter case with the derived title`() = runTest {
        // given
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "TITLE") { +" " }
                "entry"("key" to "author") { +"Alice" }
            }
            "h1" { +"Hello" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then — replaced in place, spelled as every reader reads it
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Hello" }
                "entry"("key" to "author") { +"Alice" }
            }
            "h1" { +"Hello" }
        }
    }

    @Test
    fun `should respell a usable title variant following a blank title entry dropping the blank one`() = runTest {
        // given — wrapInHtmlDocument skips a blank entry, as simplifyHtml
        // never extracts one, and reads the later variant, while a
        // case-sensitive reader (Jekyll, Hugo) reads only the blank `title`
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +" " }
                "entry"("key" to "Title") { +"Later" }
            }
            "h1" { +"Hello" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then — the blank entry carried nothing for either reader
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Later" }
            }
            "h1" { +"Hello" }
        }
    }

    @Test
    fun `should pass through a usable first title entry followed by a blank variant`() = runTest {
        // given
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Foo" }
                "entry"("key" to "TITLE") { }
            }
            "h1" { +"Hello" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Foo" }
                "entry"("key" to "TITLE") { }
            }
            "h1" { +"Hello" }
        }
    }

    @Test
    fun `should keep a title entry holding a nested structure`() = runTest {
        // given — not a usable title, but replacing it would lose content
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") {
                    "entry"("key" to "en") { +"Hello" }
                }
            }
            "h1" { +"Hello" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") {
                    "entry"("key" to "en") { +"Hello" }
                }
            }
            "h1" { +"Hello" }
        }
    }

    @Test
    fun `should respell a title variant following a null title entry dropping the null one`() = runTest {
        // given — wrapInHtmlDocument skips the null entry and reads the
        // variant after it as the title
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title", "type" to "null") { }
                "entry"("key" to "Title") { +"Real" }
            }
            "h1" { +"Heading" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Real" }
            }
            "h1" { +"Heading" }
        }
    }

    @Test
    fun `should keep a title variant following a null title entry in the head`() = runTest {
        // given
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title", "type" to "null") { }
                "entry"("key" to "Title") { +"Real" }
            }
            "h1" { +"Heading" }
        }

        // when
        val output = input.ensureFrontmatterTitle().wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
                "head" {
                    "title" { +"Real" }
                }
                "body" {
                    "h1" { +"Heading" }
                }
            }
        }
    }

    @Test
    fun `should replace a blank title variant following a nested title entry`() = runTest {
        // given — wrapInHtmlDocument skips the nested entry, so the blank
        // variant after it is the title it reads
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") {
                    "entry"("key" to "en") { +"Hello" }
                }
                "entry"("key" to "Title") { }
            }
            "h1" { +"Heading" }
        }

        // when
        val output = input.ensureFrontmatterTitle().wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
                "head" {
                    "title" { +"Heading" }
                }
                "body" {
                    "h1" { +"Heading" }
                }
            }
        }
    }

    @Test
    fun `should replace a null title entry following a nested title entry`() = runTest {
        // given
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") {
                    "entry"("key" to "en") { +"Hello" }
                }
                "entry"("key" to "Title", "type" to "null") { }
            }
            "h1" { +"Heading" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") {
                    "entry"("key" to "en") { +"Hello" }
                }
                "entry"("key" to "Title") { +"Heading" }
            }
            "h1" { +"Heading" }
        }
    }

    @Test
    fun `should keep a title entry holding an empty collection`() = runTest {
        // given — `title: []`, unreadable as a title but not replaceable
        // without losing it
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title", "type" to "seq") { }
            }
            "h1" { +"Heading" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title", "type" to "seq") { }
            }
            "h1" { +"Heading" }
        }
    }

    @Test
    fun `should replace the slot spelled title over a blank variant preceding it`() = runTest {
        // given — `Title: ""` then `title:`; a case-sensitive reader
        // (Jekyll, Hugo) reads only the latter
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "Title") { }
                "entry"("key" to "title", "type" to "null") { }
            }
            "h1" { +"Heading" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "Title") { }
                "entry"("key" to "title") { +"Heading" }
            }
            "h1" { +"Heading" }
        }
    }

    @Test
    fun `should inject a title next to an unreadable title variant`() = runTest {
        // given — `Title: []`, which wrapInHtmlDocument skips; a `title` entry
        // collides with nothing
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "Title", "type" to "seq") { }
            }
            "h1" { +"Heading" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Heading" }
                "entry"("key" to "Title", "type" to "seq") { }
            }
            "h1" { +"Heading" }
        }
    }

    @Test
    fun `should move a frontmatter preceded by blank text ahead of it`() = runTest {
        // given — blank text before the first element is insignificant, and
        // renderMarkdown drops it, but wrapInHtmlDocument reads only a
        // frontmatter opening the stream
        val input = semanticEvents {
            +"\n"
            "frontmatter" {
                "entry"("key" to "author") { +"Alice" }
            }
            "h1" { +"Heading" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Heading" }
                "entry"("key" to "author") { +"Alice" }
            }
            +"\n"
            "h1" { +"Heading" }
        }
    }

    @Test
    fun `should put the title of a frontmatter preceded by blank text into the head`() = runTest {
        // given
        val input = semanticEvents {
            +"\n"
            "frontmatter" {
                "entry"("key" to "title") { +"Page" }
            }
            "h1" { +"Heading" }
        }

        // when
        val output = input.ensureFrontmatterTitle().wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
                "head" {
                    "title" { +"Page" }
                }
                "body" {
                    +"\n"
                    "h1" { +"Heading" }
                }
            }
        }
    }

    @Test
    fun `should drop a blank title entry following a usable one`() = runTest {
        // given — a front matter reader keeps the later duplicate, so the
        // blank entry would hide the title wrapInHtmlDocument reads
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Real" }
                "entry"("key" to "author") { +"Alice" }
                "entry"("key" to "title") { }
            }
            "h1" { +"Heading" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Real" }
                "entry"("key" to "author") { +"Alice" }
            }
            "h1" { +"Heading" }
        }
    }

    @Test
    fun `should move a usable title entry after a later unreadable one`() = runTest {
        // given — a front matter reader keeps the later duplicate, so the
        // collection would hide the title wrapInHtmlDocument reads; moving the
        // usable entry after it keeps both entries
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Real" }
                "entry"("key" to "author") { +"Alice" }
                "entry"("key" to "title", "type" to "seq") { }
                "entry"("key" to "tags") { +"x" }
            }
            "h1" { +"Heading" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "author") { +"Alice" }
                "entry"("key" to "title", "type" to "seq") { }
                "entry"("key" to "title") { +"Real" }
                "entry"("key" to "tags") { +"x" }
            }
            "h1" { +"Heading" }
        }
    }

    @Test
    fun `should respell the last usable title variant as wrapInHtmlDocument reads it`() = runTest {
        // given
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "Title") { +"First" }
                "entry"("key" to "TITLE") { +"Last" }
            }
            "h1" { +"Heading" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "Title") { +"First" }
                "entry"("key" to "title") { +"Last" }
            }
            "h1" { +"Heading" }
        }
    }

    @Test
    fun `should drop every other empty title entry when deriving the title`() = runTest {
        // given — a front matter reader keeps the later `title:`, which
        // would hide the derived title in the first slot
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { }
                "entry"("key" to "author") { +"Alice" }
                "entry"("key" to "title", "type" to "null") { }
            }
            "h1" { +"Heading" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Heading" }
                "entry"("key" to "author") { +"Alice" }
            }
            "h1" { +"Heading" }
        }
    }

    @Test
    fun `should put a derived title after a later nested title entry`() = runTest {
        // given — `title:` then `title: {en: Hello}`; a front matter reader
        // keeps the later duplicate, so the derived title in the first slot
        // would be hidden by the mapping wrapInHtmlDocument skips
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title", "type" to "null") { }
                "entry"("key" to "author") { +"Alice" }
                "entry"("key" to "title") {
                    "entry"("key" to "en") { +"Hello" }
                }
            }
            "h1" { +"Heading" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "author") { +"Alice" }
                "entry"("key" to "title") {
                    "entry"("key" to "en") { +"Hello" }
                }
                "entry"("key" to "title") { +"Heading" }
            }
            "h1" { +"Heading" }
        }
    }

    @Test
    fun `should treat a tagged frontmatter as content`() = runTest {
        // given — a literal `<frontmatter>` tag, not front matter
        val input = semanticEvents {
            tag("frontmatter") {
                "entry"("key" to "author") { +"Alice" }
            }
            "h1" { +"Heading" }
        }

        // when
        val output = input.ensureFrontmatterTitle()

        // then
        output sameAs semanticEvents {
            tag("frontmatter") {
                "entry"("key" to "author") { +"Alice" }
            }
            "h1" { +"Heading" }
        }
    }

    @Test
    fun `should derive the head title for parsed Markdown with a bare title key`() = runTest {
        // given
        val document = flowOf(
            """
            ---
            title:
            ---
            # Hello
            """.trimIndent()
        )

        // when
        val output = document.parse().ensureFrontmatterTitle().wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
                "head" {
                    "title" { +"Hello" }
                }
                "body" {
                    "h1" { +"Hello" }
                }
            }
        }
    }

}
