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
 * head-to-frontmatter extraction, minus the values it discards.
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
    fun `should trim HTML whitespace around the lang`() = runTest {
        // given — as simplifyHtml trims it on the way in (text that opens
        // with indentation and ends with a newline would read as a verbatim
        // line of unknown value)
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "lang") { +"\n en " }
            }
            "p" { +"Body." }
        }

        // when
        val output = input.wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html"("lang" to "en") {
                "head" { }
                "body" {
                    "p" { +"Body." }
                }
            }
        }
    }

    @Test
    fun `should trim invisible chars around the lang`() = runTest {
        // given — a byte order mark or a zero-width space makes no valid
        // language tag, as they are trimmed from a title
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "lang") { +"\uFEFFen\u200B" }
            }
            "p" { +"Body." }
        }

        // when
        val output = input.wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html"("lang" to "en") {
                "head" { }
                "body" {
                    "p" { +"Body." }
                }
            }
        }
    }

    @Test
    fun `should normalize the title as simplifyHtml reads it`() = runTest {
        // given — invisible chars at the edges, a line separator inside
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"\uFEFF Foo\u2028Bar  baz " }
            }
            "p" { +"Body." }
        }

        // when
        val output = input.wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
                "head" {
                    "title" { +"Foo Bar baz" }
                }
                "body" {
                    "p" { +"Body." }
                }
            }
        }
    }

    @Test
    fun `should use scalar text verbatim including decoded quoting`() = runTest {
        // given — the parser has already decoded the YAML; the values carry a
        // colon, quotes and a newline as plain text (a title is one line)
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"He said: \"hi\"" }
                "entry"("key" to "description") { +"Hello\nBye" }
                "entry"("key" to "og:image") { +"https://example.com/img.png" }
            }
        }

        // when
        val output = input.wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
                "head" {
                    "title" { +"He said: \"hi\"" }
                    "meta"("name" to "description", "content" to "Hello\nBye") { }
                    "meta"("name" to "og:image", "content" to "https://example.com/img.png") { }
                }
                "body" { }
            }
        }
    }

    @Test
    fun `should include typed scalars and skip nested null blank and verbatim content`() = runTest {
        // given — only a top-level non-blank scalar is head metadata
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
                }
                "body" { }
            }
        }
    }

    @Test
    fun `should let the last of duplicate keys win as front matter readers do`() = runTest {
        // given — Psych (Jekyll) and PyYAML keep the later duplicate
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
    fun `should read the title front matter readers read of duplicate title keys`() = runTest {
        // given
        val document = flowOf(
            """
            ---
            title: Draft
            author: Alice
            title: Final
            ---
            """.trimIndent()
        )

        // when
        val output = document.parse().wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
                "head" {
                    "title" { +"Final" }
                    "meta"("name" to "author", "content" to "Alice") { }
                }
                "body" { }
            }
        }
    }

    @Test
    fun `should prefer the lowercase spelling of a key over a later variant`() = runTest {
        // given — a case-sensitive reader (Jekyll) looks up `title`, whatever
        // follows it in another letter case
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Real" }
                "entry"("key" to "Title") { +"Variant" }
            }
        }

        // when
        val output = input.wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
                "head" {
                    "title" { +"Real" }
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
    fun `should read a frontmatter preceded by blank text into the head`() = runTest {
        // given — blank text is insignificant, as for ensureFrontmatterTitle
        val input = semanticEvents {
            +"\n"
            "frontmatter" {
                "entry"("key" to "title") { +"Page" }
            }
            "p" { +"Body." }
        }

        // when
        val output = input.wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
                "head" {
                    "title" { +"Page" }
                }
                "body" {
                    +"\n"
                    "p" { +"Body." }
                }
            }
        }
    }

    @Test
    fun `should treat a frontmatter preceded by a non-breaking space as content`() = runTest {
        // given — NBSP is content in HTML, so the frontmatter does not open
        // the stream
        val input = semanticEvents {
            +"\u00A0"
            "frontmatter" {
                "entry"("key" to "title") { +"Page" }
            }
        }

        // when
        val output = input.wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
                "head" { }
                "body" {
                    +"\u00A0"
                    "frontmatter" {
                        "entry"("key" to "title") { +"Page" }
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
    fun `should read no value from an entry continued on indented lines`() = runTest {
        // given — readers join the continuation lines into the value, which
        // the YAML subset keeps verbatim, so the first line alone is not it
        val markdown = """
            ---
            title: A long
              title
            description: first
              second
            author: Alice
            ---
            Body.
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
                <meta name="author" content="Alice"/>
              </head>
              <body>
                <p>
                  Body.
                </p>
              </body>
            </html>
        """.trimIndent()
    }

    @Test
    fun `should read no value from indented verbatim lines under a bare key`() = runTest {
        // given — the lines are outside the YAML subset, kept verbatim with
        // their indentation, and readers disagree on what they hold
        val markdown = """
            ---
            title:
              [a:, b]
            description:
              first line
              second
            author: Alice
            ---
            Body.
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
                <meta name="author" content="Alice"/>
              </head>
              <body>
                <p>
                  Body.
                </p>
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
    fun `should round-trip through simplifyHtml except the values it discards`() = runTest {
        // given — simplifyHtml keeps no blank value and no application state
        // (a JSON object or array, or an over-long blob), so those entries do
        // not come back; text that merely looks bracketed does
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Page" }
                "entry"("key" to "summary") { +"{draft} notes" }
                "entry"("key" to "keywords") { }
                "entry"("key" to "state") { +"{\"a\":1}" }
                "entry"("key" to "blob") { +"x".repeat(4097) }
            }
            "p" { +"Hi" }
        }

        // when
        val output = input.wrapInHtmlDocument().simplifyHtml()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Page" }
                "entry"("key" to "summary") { +"{draft} notes" }
            }
            "p" { +"Hi" }
        }
    }

    @Test
    fun `should read keys ASCII case-insensitively like meta names`() = runTest {
        // given — a key differing only in letter case is a duplicate, and
        // its lowercase spelling wins, at the position of the first one
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "Title") { +"My Page" }
                "entry"("key" to "LANG") { +"de" }
                "entry"("key" to "Author") { +"Alice" }
                "entry"("key" to "description") { +"A doc" }
                "entry"("key" to "author") { +"Bob" }
            }
        }

        // when
        val output = input.wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html"("lang" to "de") {
                "head" {
                    "title" { +"My Page" }
                    "meta"("name" to "author", "content" to "Bob") { }
                    "meta"("name" to "description", "content" to "A doc") { }
                }
                "body" { }
            }
        }
    }

    @Test
    fun `should not let a blank key hide a later variant differing in letter case`() = runTest {
        // given — simplifyHtml never extracts a blank value, so a blank key
        // must not win the duplicate policy either, or the round-trip loses
        // the real value
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "description") { +"" }
                "entry"("key" to "Description") { +"A real summary" }
                "entry"("key" to "lang") { +" " }
                "entry"("key" to "Lang") { +"de" }
                "entry"("key" to "title") { }
                "entry"("key" to "Title") { +"Page" }
            }
        }

        // when
        val output = input.wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html"("lang" to "de") {
                "head" {
                    "title" { +"Page" }
                    "meta"("name" to "Description", "content" to "A real summary") { }
                }
                "body" { }
            }
        }
    }

    @Test
    fun `should keep a later non-blank variant of a blank key on a round-trip through simplifyHtml`() = runTest {
        // given
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "description") { +"" }
                "entry"("key" to "Description") { +"A real summary" }
            }
        }

        // when
        val output = input.wrapInHtmlDocument().simplifyHtml()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "Description") { +"A real summary" }
            }
        }
    }

    @Test
    fun `should keep a title key in any letter case on a round-trip through ensureFrontmatterTitle and simplifyHtml`() = runTest {
        // given — the H1 must not displace the `Title` entry
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "Title") { +"My Page" }
            }
            "h1" { +"Heading" }
        }

        // when
        val output = input.ensureFrontmatterTitle().wrapInHtmlDocument().simplifyHtml()

        // then
        output sameAs semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"My Page" }
            }
            "h1" { +"Heading" }
        }
    }

    @Test
    fun `should agree with ensureFrontmatterTitle when a later title variant is blank`() = runTest {
        // given — the usable first title is kept by ensureFrontmatterTitle,
        // so the blank variant after it must not blank the <title>
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { +"Foo" }
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
                    "title" { +"Foo" }
                }
                "body" {
                    "h1" { +"Heading" }
                }
            }
        }
    }

    @Test
    fun `should agree with ensureFrontmatterTitle when the first title variant is blank`() = runTest {
        // given — both skip the blank first title, so the later usable
        // variant is the title and the heading does not displace it
        val input = semanticEvents {
            "frontmatter" {
                "entry"("key" to "title") { }
                "entry"("key" to "Title") { +"Foo" }
            }
            "h1" { +"Heading" }
        }

        // when
        val output = input.ensureFrontmatterTitle().wrapInHtmlDocument()

        // then
        output sameAs semanticEvents {
            "html" {
                "head" {
                    "title" { +"Foo" }
                }
                "body" {
                    "h1" { +"Heading" }
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
