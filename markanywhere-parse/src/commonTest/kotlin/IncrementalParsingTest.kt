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
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test

class IncrementalParsingTest {

    @Test
    fun `should emit SemanticEvents incrementally without buffering`() = runTest {
        // given
        val input = MutableSharedFlow<String>()
        val eventBuffer = mutableListOf<SemanticEvent>()

        suspend fun assertEmissions(vararg events: SemanticEvent) {
            yield()
            assert(eventBuffer == events.toList())
            eventBuffer.clear()
        }

        // Start collecting events
        val collectJob = launch {
            input.parse().collect { event ->
                eventBuffer += event
            }
        }
        yield() // let the collector start

        // when: send '#' - parser cannot yet determine heading level
        input.emit("#")
        // then: no event emitted yet
        assertEmissions() // empty

        // when: send ' ' - now parser knows it's h1
        input.emit(" ")
        // then: Mark(h1) should be emitted
        assertEmissions(SemanticEvent.Mark("h1"))

        // when: send header text character by character
        input.emit("H")
        // then
        assertEmissions(SemanticEvent.Text("H"))
        // when
        input.emit("i")
        // then
        assertEmissions(SemanticEvent.Text("i"))

        // when: send newline - header ends
        input.emit("\n")
        // then: h1 should be closed
        assertEmissions(SemanticEvent.Unmark("h1"))

        // when: send paragraph text
        input.emit("T")
        // then: paragraph should open first
        assertEmissions(
            SemanticEvent.Mark("p"),
            SemanticEvent.Text("T")
        )

        // when: complete input
        collectJob.cancel()
    }

    @Test
    fun `should parse incrementally`() = runTest {
        // given
        val textFlow = flowOf(
            "# Hello ",
            "World\n",
            "\n",
            "<foo:bar buzz=\"42\">",
            "println(\"Hello ",
            "World\")",
            "</foo:bar>\n",
            "\n",
            "Another paragraph."
        )

        // when
        val parsed = textFlow.parse()

        // then
        parsed sameAs semanticEvents {
            "h1" {
                +"Hello "
                +"World"
            }
            tag("foo:bar", "buzz" to "42") {
                +"println(\"Hello "
                +"World\")"
            }
            "p" {
                +"A"
                +"nother paragraph."
            }
        }
    }

    @Test
    fun `should parse incrementally with inline formatting split at buffering boundaries`() = runTest {
        // This test verifies that inline formatting works correctly when chunks are split
        // at key points. Opening markers are combined with the first content char so that
        // marker resolution and content emission happen in the same chunk.
        val textFlow = flowOf(
            // Heading level detection: # buffered until space seen
            "## ",         // h2 with space (combined for resolution)
            "Head",
            "ing\n",

            "\n",

            // Bold with asterisks: combined opening + first char for resolution
            "**b",         // bold opens with first content char
            "old",
            "**",          // bold closes
            " text\n",

            "\n",

            // Italic with asterisk
            "*i",          // italic opens with first content char
            "talic",
            "*",           // italic closes
            " word\n",

            "\n",

            // Bold+italic: combined opening + first char
            "***b",        // bold+italic opens with first content char
            "oth",
            "***",         // both close
            ".\n",

            "\n",

            // Underscores for bold
            "__u",         // bold opens with first content char
            "nder",
            "__",          // bold closes
            " end\n",

            "\n",

            // Inline code single backtick
            "`c",          // code opens with first content char
            "ode",
            "`",           // code closes
            " more\n",

            "\n",

            // Inline code double backtick (content with embedded backtick)
            "``",          // double backtick code opens
            " code ",
            " with ",
            "``",          // double backtick code closes
            ".\n",

            "\n",

            // Strikethrough
            "~~s",         // strikethrough opens with first content char
            "trike",
            "~~",          // strikethrough closes
            ".\n",

            "\n",

            // Subscript
            "H",
            "~2",          // subscript opens with content
            "~",           // subscript closes
            "O\n",

            "\n",

            // Superscript
            "x",
            "^2",          // superscript opens with content
            "^",           // superscript closes
            " end\n",

            "\n",

            // Highlight
            "==h",         // highlight opens with first content char
            "ighlight",
            "==",          // highlight closes
            ".\n",

            "\n",

            // Inline math ($ is immediate toggle, content goes through)
            "\$E",         // math opens with first content
            "=mc^2",
            "\$",          // math closes
            " done\n",

            "\n",

            // Link: [ starts buffering text, ]( transitions to url
            "[l",          // link text starts
            "ink",
            "](",          // transition to url
            "https://",
            "ex.com",
            ")",           // link ends
            " here\n",

            "\n",

            // Image: ![ starts image (! buffered until [)
            "![a",         // combined ![ with first alt char
            "lt",
            "](",          // transition to url
            "img",
            ".png",
            ")",           // image ends
            ".\n",

            "\n",

            // Nested formatting: italic containing bold
            "*i",          // italic opens with first char
            "ta ",
            "**b",         // bold opens with first char
            "old",
            "**",          // bold closes
            " more",
            "*",           // italic closes
            ".\n",

            "\n",

            // Triple underscore bold+italic
            "___t",        // bold+italic opens with first char
            "riple",
            "___",         // both close
            ".\n"
        )

        // when
        val parsed = textFlow.parse()

        // then
        // Note: Expected chunks reflect actual parser behavior:
        // - Fast-path optimization may merge content within formatting spans
        // - Trailing content after closing markers may be split at control chars
        // - Link/image text is buffered and emitted as one chunk when link completes
        parsed sameAs semanticEvents {
            "h2" {
                +"Head"
                +"ing"
            }
            "p" {
                "strong" {
                    +"b"
                    +"old"
                }
                +" "
                +"text"
            }
            "p" {
                "em" {
                    +"i"
                    +"talic"
                }
                +" "
                +"word"
            }
            "p" {
                "strong" {
                    "em" {
                        +"b"
                        +"oth"
                    }
                }
                +"."
            }
            "p" {
                "strong" {
                    +"u"
                    +"nder"
                }
                // After __ closes (triggered by space), space emitted separately, then rest fast-pathed
                +" "
                +"end"
            }
            "p" {
                "code" {
                    +"c"
                    +"ode"
                }
                // After ` closes (immediate), next chunk starts fresh with fast-path
                +" more"
            }
            "p" {
                "code" {
                    // Double backtick code: content goes to buffer, emitted as one chunk
                    +"code  with"
                }
                +"."
            }
            "p" {
                "del" {
                    +"s"
                    +"trike"
                }
                +"."
            }
            "p" {
                +"H"
                "sub" {
                    +"2"
                }
                +"O"
            }
            "p" {
                +"x"
                "sup" {
                    +"2"
                }
                // ^ is immediate toggle, next chunk starts fresh with fast-path
                +" end"
            }
            "p" {
                "mark" {
                    +"h"
                    +"ighlight"
                }
                +"."
            }
            "p" {
                "math" {
                    // Math uses fast-path, content merged
                    +"E=mc^2"
                }
                +" done"
            }
            "p" {
                "a"("href" to "https://ex.com") {
                    // Link text is buffered, emitted as one chunk
                    +"link"
                }
                +" here"
            }
            "p" {
                "img"("src" to "img.png", "alt" to "alt") {}
                +"."
            }
            "p" {
                "em" {
                    +"i"
                    +"ta "
                    "strong" {
                        +"b"
                        +"old"
                    }
                    // After ** closes (triggered by space), space emitted separately
                    +" "
                    +"more"
                }
                +"."
            }
            "p" {
                "strong" {
                    "em" {
                        +"t"
                        +"riple"
                    }
                }
                +"."
            }
        }
    }

    @Test
    fun `should parse incrementally with block-level structures`() = runTest {
        // This test verifies block-level incremental parsing.
        // Block-level patterns require certain sequences to be seen together for
        // disambiguation. Content after the marker is processed via fast-path.
        val textFlow = flowOf(
            // Unordered list: "- " + first content char triggers list mode
            // then rest of chunk is fast-pathed
            "- i",        // list marker + first char together
            "tem",
            " one\n",
            "- i",        // next item
            "tem two\n",

            "\n",

            // Paragraph to clearly separate list sections
            "Sep\n",

            "\n",

            // Task list - marker pattern must be together
            "- [ ] u",    // unchecked task + first content char
            "ndone\n",
            "- [x] d",    // checked task + first content char
            "one\n",

            "\n",

            // Paragraph separator
            "Sep\n",

            "\n",

            // Ordered list - "1. " + first char together
            "1. f",       // ordered list marker + first char
            "irst\n",
            "2. s",       // next item marker + first char
            "econd\n",

            "\n",

            // Blockquote - "> " + first content char together
            "> q",        // blockquote marker + first char
            "uoted\n",
            "> m",        // continuation + first char
            "ore\n",

            "\n",

            // Horizontal rule: --- on single line
            "---\n",

            // Code block with fence + language
            "```kotlin\n",
            "val x = 42\n",  // code content (no inline processing, emitted as-is)
            "```\n",

            "\n",

            // Math block
            "$$\n",
            "\\sum_{i=1}^{n} i\n",  // math content merged
            "$$\n",

            "\n",

            // Table
            "| H1 | H2 |\n",
            "|---|---|\n",
            "| A | B |\n"
        )

        // when
        val parsed = textFlow.parse()

        // then
        parsed sameAs semanticEvents {
            "ul" {
                "li" {
                    +"i"
                    +"tem"
                    +" one"
                }
                "li" {
                    +"i"
                    +"tem two"
                }
            }
            "p" {
                // First char triggers paragraph open, rest is fast-pathed
                +"S"
                +"ep"
            }
            "ul" {
                "li" {
                    "input"("type" to "checkbox") {}
                    +"u"
                    +"ndone"
                }
                "li" {
                    "input"("type" to "checkbox", "checked" to "true") {}
                    +"d"
                    +"one"
                }
            }
            "p" {
                +"S"
                +"ep"
            }
            "ol" {
                "li" {
                    +"f"
                    +"irst"
                }
                "li" {
                    +"s"
                    +"econd"
                }
            }
            "blockquote" {
                "p" {
                    +"q"
                    +"uoted"
                    +"\n"
                    +"m"
                    +"ore"
                }
            }
            "hr" {}
            "pre"("class" to "code lang-kotlin") {
                +"val x = 42"
            }
            "math"("display" to "block") {
                +"\\sum_{i=1}^{n} i"
            }
            "table" {
                "thead" {
                    "tr" {
                        "th" {
                            +"H1"
                        }
                        "th" {
                            +"H2"
                        }
                    }
                }
                "tbody" {
                    "tr" {
                        "td" {
                            +"A"
                        }
                        "td" {
                            +"B"
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `should parse incrementally with custom markup tags`() = runTest {
        // given
        // Custom markup tags require special handling for opening and closing tags.
        val textFlow = flowOf(
            // Custom markup tag with attributes split character by character
            "<",          // buffered
            "ns",
            ":",
            "tag",
            " ",
            "attr",
            "=",
            "\"",
            "val",
            "\"",
            ">",          // tag opens
            "\n",
            "con",
            "tent\n",
            "<",          // buffered - potential closing tag
            "/",          // continuing
            "ns",
            ":",
            "tag",
            ">",          // tag closes
            "\n"
        )

        // when
        val parsed = textFlow.parse()

        // then
        parsed sameAs semanticEvents {
            tag("ns:tag", "attr" to "val") {
                +"con"
                +"tent"
            }
        }
    }

    @Test
    fun `should parse escape sequences incrementally`() = runTest {
        // given
        // Escape sequences with backslash buffering
        val textFlow = flowOf(
            "\\",         // buffered - escape
            "*",          // escaped asterisk (literal)
            "not italic",
            "\\",         // buffered
            "*"           // escaped
        )

        // when
        val parsed = textFlow.parse()

        // then
        parsed sameAs semanticEvents {
            "p" {
                +"*"
                +"not italic"
                +"*"
            }
        }
    }

    @Test
    fun `should parse autolinks incrementally`() = runTest {
        // given
        // Autolinks with < > buffering
        val textFlow = flowOf(
            "<",          // buffered - could be autolink
            "test",
            "@",
            "email.com",
            ">"           // autolink ends
        )

        // when
        val parsed = textFlow.parse()

        // then
        parsed sameAs semanticEvents {
            "p" {
                "a"("href" to "mailto:test@email.com") {
                    +"test@email.com"
                }
            }
        }
    }

}
