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

import com.xemantic.kotlin.test.sameAsHtml
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.parse.parse
import com.xemantic.markanywhere.render.renderHtml
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * [wrapInHtmlDocument] wraps a semantic event stream (typically parsed
 * Markdown) in an `html`/`head`/`body` document structure. A leading
 * `frontmatter` block (the untagged mark emitted by the parser, holding
 * `entry` marks) feeds the `head`: the `title` entry becomes `<title>`,
 * `lang` becomes the `<html lang>` attribute, every other top-level scalar
 * entry becomes a `<meta name content>` — the inverse of `simplifyHtml`'s
 * head-to-frontmatter extraction.
 */
class WrapInHtmlDocumentTest {

    @Test
    fun `should wrap a plain event stream in an html document with an empty head`() = runTest {
        // given
        val input = semanticEvents {
            "h1" { +"Heading" }
            "p" { +"Body paragraph." }
        }

        // when
        val output = input.wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
                "head" { }
                "body" {
                    "h1" { +"Heading" }
                    "p" { +"Body paragraph." }
                }
            }
        }
    }

    @Test
    fun `should emit a document skeleton for an empty stream`() = runTest {
        // when
        val output = semanticEvents { }.wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
                "head" { }
                "body" { }
            }
        }
    }

    @Test
    fun `should populate head from frontmatter entries`() = runTest {
        // given
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Hello" }
                "entry"("key" to "author") { +"Alice" }
                "entry"("key" to "lang") { +"en" }
            }
            "p" { +"Body." }
        }

        // when
        val output = input.wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html"("lang" to "en") {
                "head" {
                    "title" { +"Hello" }
                    "meta"("name" to "author", "content" to "Alice") { }
                }
                "body" {
                    "p" { +"Body." }
                }
            }
        }
    }

    @Test
    fun `should use scalar text verbatim including decoded quoting`() = runTest {
        // given — the parser has already decoded the YAML; the value carries a
        // colon, quotes and a newline as plain text
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"He said: \"hi\"\nBye" }
                "entry"("key" to "og:image") { +"https://example.com/img.png" }
            }
        }

        // when
        val output = input.wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
                "head" {
                    "title" { +"He said: \"hi\"\nBye" }
                    "meta"("name" to "og:image", "content" to "https://example.com/img.png") { }
                }
                "body" { }
            }
        }
    }

    @Test
    fun `should include typed scalars and skip nested null and verbatim content`() = runTest {
        // given — only a top-level scalar is representable as head metadata
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Hello" }
                "entry"("key" to "year", "type" to "int") { +"2026" }
                "entry"("key" to "tags") {
                    "item" { +"a" }
                    "item" { +"b" }
                }
                "entry"("key" to "author") {
                    "entry"("key" to "name") { +"Alice" }
                }
                "entry"("key" to "empty", "type" to "null") { }
                "entry"("key" to "none", "type" to "seq") { }
                "entry"("key" to "blank") { }
                +"? complex\n"
            }
        }

        // when
        val output = input.wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
                "head" {
                    "title" { +"Hello" }
                    "meta"("name" to "year", "content" to "2026") { }
                    "meta"("name" to "blank", "content" to "") { }
                }
                "body" { }
            }
        }
    }

    @Test
    fun `should let a later duplicate key win`() = runTest {
        // given
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "author") { +"Alice" }
                "entry"("key" to "author") { +"Bob" }
            }
        }

        // when
        val output = input.wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
                "head" {
                    "meta"("name" to "author", "content" to "Bob") { }
                }
                "body" { }
            }
        }
    }

    @Test
    fun `should pass a mid-stream frontmatter block through into body`() = runTest {
        // given — only a *leading* frontmatter feeds the head; anywhere else it
        // is ordinary content (hand-built flows), forwarded verbatim
        val input = semanticEvents {
            "p" { +"Intro." }
            "frontmatter" {
                "entry"("key" to "title") { +"Not a head" }
            }
        }

        // when
        val output = input.wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
                "head" { }
                "body" {
                    "p" { +"Intro." }
                    "frontmatter" {
                        "entry"("key" to "title") { +"Not a head" }
                    }
                }
            }
        }
    }

    @Test
    fun `should wrap parsed Markdown in a complete HTML document`() = runTest {
        // given
        val markdown = """
            ---
            title: Hello
            author: Alice
            ---
            # Heading
        """.trimIndent()

        // when
        val html = flowOf(markdown)
            .parse()
            .wrapInHtmlDocument()
            .renderHtml()

        // then
        html sameAsHtml """
            <html>
              <head>
                <title>Hello</title>
                <meta name="author" content="Alice"/>
              </head>
              <body>
                <h1>
                  Heading
                </h1>
              </body>
            </html>
        """.trimIndent()
    }

    @Test
    fun `should reconstruct head metadata extracted by simplifyHtml`() = runTest {
        // given — a captured (tagged) HTML document whose head simplifyHtml
        // reduces to a frontmatter block; wrapping the result back should
        // reconstruct the equivalent document structure
        val input = semanticEvents(tagged = true) {
            "html"("lang" to "en") {
                "head" {
                    "title" { +"Page" }
                    "meta"("name" to "og:image", "content" to "https://example.com/img.png") { }
                }
                "body" {
                    "p" { +"Hi" }
                }
            }
        }

        // when
        val output = input.simplifyHtml().wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html"("lang" to "en") {
                "head" {
                    "title" { +"Page" }
                    "meta"("name" to "og:image", "content" to "https://example.com/img.png") { }
                }
                "body" {
                    "p" { +"Hi" }
                }
            }
        }
    }


    @Test
    fun `should use an entry left open when the stream ends inside the frontmatter`() = runTest {
        // given — a broken upstream contract: neither the entry nor the
        // frontmatter is closed. Built from raw events on purpose: the
        // balanced builders cannot express an unclosed mark.
        val input = flowOf(
            SemanticEvent.Mark(name = "frontmatter", isTagged = false),
            SemanticEvent.Mark(name = "entry", isTagged = false, attributes = mapOf("key" to "title")),
            SemanticEvent.Text("Hello"),
        )

        // when
        val output = input.wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
                "head" {
                    "title" { +"Hello" }
                }
                "body" { }
            }
        }
    }

}
