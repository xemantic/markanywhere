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
 * `frontmatter` block (the untagged mark emitted by the parser) feeds the
 * `head`: `title` becomes `<title>`, `lang` becomes the `<html lang>`
 * attribute, every other flat key becomes a `<meta name content>` — the
 * inverse of `simplifyHtml`'s head-to-frontmatter extraction.
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
    fun `should populate head from YAML frontmatter`() = runTest {
        // given
        val input = semanticEvents {
            "frontmatter"("format" to "yaml") {
                +"title: Hello\nauthor: Alice\nlang: en\n"
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
    fun `should populate head from TOML frontmatter`() = runTest {
        // given
        val input = semanticEvents {
            "frontmatter"("format" to "toml") {
                +"title = \"Hello\"\nauthor = \"Alice\"\n"
            }
            "p" { +"Body." }
        }

        // when
        val output = input.wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
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
    fun `should unescape a double-quoted YAML scalar`() = runTest {
        // given — the value contains a colon, quotes, and an escaped newline,
        // so the YAML side must have quoted it
        val input = semanticEvents {
            "frontmatter"("format" to "yaml") {
                +"title: \"He said: \\\"hi\\\"\\nBye\"\n"
            }
        }

        // when
        val output = input.wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
                "head" {
                    "title" { +"He said: \"hi\"\nBye" }
                }
                "body" { }
            }
        }
    }

    @Test
    fun `should unescape a single-quoted YAML scalar`() = runTest {
        // given
        val input = semanticEvents {
            "frontmatter"("format" to "yaml") {
                +"author: 'O''Brien'\n"
            }
        }

        // when
        val output = input.wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
                "head" {
                    "meta"("name" to "author", "content" to "O'Brien") { }
                }
                "body" { }
            }
        }
    }

    @Test
    fun `should parse a double-quoted YAML key`() = runTest {
        // given — meta names like `og:image` contain a colon, so the YAML side
        // must have quoted the key (see renderYamlFrontmatter in SimplifyHtml)
        val input = semanticEvents {
            "frontmatter"("format" to "yaml") {
                +"\"og:image\": \"https://example.com/img.png\"\n"
            }
        }

        // when
        val output = input.wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
                "head" {
                    "meta"("name" to "og:image", "content" to "https://example.com/img.png") { }
                }
                "body" { }
            }
        }
    }

    @Test
    fun `should skip YAML comments blank lines and nested structures`() = runTest {
        // given — only the flat `title` key is representable in head metadata
        val input = semanticEvents {
            "frontmatter"("format" to "yaml") {
                +"# a comment\ntitle: Hello\ntags:\n  - a\n  - b\n\nempty:\n"
            }
        }

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

    @Test
    fun `should strip a trailing comment from a plain YAML scalar`() = runTest {
        // given
        val input = semanticEvents {
            "frontmatter"("format" to "yaml") {
                +"author: Alice # the reviewer\n"
            }
        }

        // when
        val output = input.wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
                "head" {
                    "meta"("name" to "author", "content" to "Alice") { }
                }
                "body" { }
            }
        }
    }

    @Test
    fun `should stop TOML parsing at the first section header`() = runTest {
        // given — keys after `[params]` are section-scoped, not top-level
        val input = semanticEvents {
            "frontmatter"("format" to "toml") {
                +"title = \"Hello\"\n[params]\nauthor = \"Alice\"\n"
            }
        }

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

    @Test
    fun `should parse TOML literal strings and bare values`() = runTest {
        // given — a literal string is verbatim (no escapes), a bare value is
        // kept as its source string with any trailing comment stripped
        val input = semanticEvents {
            "frontmatter"("format" to "toml") {
                +"path = 'C:\\Users\\alice'\nyear = 2026 # release\n"
            }
        }

        // when
        val output = input.wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
                "head" {
                    "meta"("name" to "path", "content" to "C:\\Users\\alice") { }
                    "meta"("name" to "year", "content" to "2026") { }
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
            "frontmatter"("format" to "yaml") {
                +"title: Not a head\n"
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
                    "frontmatter"("format" to "yaml") {
                        +"title: Not a head\n"
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
                <title>
                  Hello
                </title>
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

}
