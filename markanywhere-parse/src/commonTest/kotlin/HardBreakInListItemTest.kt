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

import com.xemantic.kotlin.core.text.unaryPlus
import com.xemantic.kotlin.test.sameAs
import com.xemantic.kotlin.test.text.chunkedRandomly
import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.render.renderMarkdown
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Hard line breaks (GFM §6.7) inside list items (§5.2), issue #145.
 * List items are parsed line by line in `processListBlock`, so the
 * trailing-space and trailing-backslash tally that a top-level paragraph
 * carries across its line boundary has to be carried by the list item too.
 */
class HardBreakInListItemTest {

    @Test
    fun `should emit a hard break for two trailing spaces in a list item`() = runTest {
        // given
        val textFlow = buildString {
            +"- a  \n"
            +"  b\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"a"
                        "br" {}
                        +"\nb"
                    }
                }
            }
        }
    }

    @Test
    fun `should emit a hard break after emphasis in a list item`() = runTest {
        // given
        val textFlow = buildString {
            +"- *a*  \n"
            +"  b\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        "em" { +"a" }
                        "br" {}
                        +"\nb"
                    }
                }
            }
        }
    }

    @Test
    fun `should emit a hard break for a trailing backslash in a list item`() = runTest {
        // given
        val textFlow = buildString {
            +"- a\\\n"
            +"  b\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"a"
                        "br" {}
                        +"\nb"
                    }
                }
            }
        }
    }

    @Test
    fun `should keep a trailing backslash ending a list item as literal text`() = runTest {
        // given — no continuation follows, so the backslash is not a break
        // (GFM example 669 for a paragraph)
        val textFlow = buildString {
            +"- a\\\n"
            +"- b\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"a\\" }
                }
                "li" {
                    "p" { +"b" }
                }
            }
        }
    }

    @Test
    fun `should drop trailing spaces ending a list item`() = runTest {
        // given
        val textFlow = buildString {
            +"- a  \n"
            +"\n"
            +"  b\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"a" }
                    "p" { +"b" }
                }
            }
        }
    }

    @Test
    fun `should emit a hard break before every continuation line that asks for one`() = runTest {
        // given
        val textFlow = buildString {
            +"- a  \n"
            +"  b\n"
            +"  c  \n"
            +"  d\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"a"
                        "br" {}
                        +"\nb\nc"
                        "br" {}
                        +"\nd"
                    }
                }
            }
        }
    }

    @Test
    fun `should emit a hard break before a lazy continuation line`() = runTest {
        // given
        val textFlow = buildString {
            +"- a  \n"
            +"b\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"a"
                        "br" {}
                        +"\nb"
                    }
                }
            }
        }
    }

    @Test
    fun `should emit a hard break in a later paragraph of a list item`() = runTest {
        // given
        val textFlow = buildString {
            +"- x\n"
            +"\n"
            +"  a  \n"
            +"  b\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"x" }
                    "p" {
                        +"a"
                        "br" {}
                        +"\nb"
                    }
                }
            }
        }
    }

    @Test
    fun `should emit a hard break in a blockquote inside a list item`() = runTest {
        // given
        val textFlow = buildString {
            +"- > a  \n"
            +"  > b\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "blockquote" {
                        "p" {
                            +"a"
                            "br" {}
                            +"\nb"
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `should keep a backslash followed by one trailing space as literal text`() = runTest {
        // given — the backslash does not end the line, so it is no hard break
        val textFlow = buildString {
            +"- foo\\ \n"
            +"  bar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"foo\\\nbar" }
                }
            }
        }
    }

    @Test
    fun `should keep a backslash before a trailing space hard break as literal text`() = runTest {
        // given
        val textFlow = buildString {
            +"- foo\\  \n"
            +"  bar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"foo\\"
                        "br" {}
                        +"\nbar"
                    }
                }
            }
        }
    }

    @Test
    fun `should keep a backslash followed by a trailing space as literal text in a paragraph continuation`() = runTest {
        // given — the indent makes the continuation line-buffered, the path
        // that shares the rule with list items
        val textFlow = buildString {
            +"a\n"
            +" foo\\ \n"
            +"bar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"a\nfoo\\\nbar" }
        }
    }

    @Test
    fun `should not carry a hard break across a blank line into a blockquote paragraph`() = runTest {
        // given — a blank line ends the blockquote in the item
        val textFlow = buildString {
            +"- > a  \n"
            +"\n"
            +"  > b\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "blockquote" {
                        "p" { +"a" }
                    }
                    "blockquote" {
                        "p" { +"b" }
                    }
                }
            }
        }
    }

    @Test
    fun `should not carry a hard break across a blank line into a lazy line`() = runTest {
        // given
        val textFlow = buildString {
            +"- > a  \n"
            +"\n"
            +"b\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "blockquote" {
                        "p" { +"a" }
                    }
                }
            }
            "p" { +"b" }
        }
    }

    @Test
    fun `should emit a hard break after a rejected table header opening a list item`() = runTest {
        // given
        val textFlow = buildString {
            +"- | a |  \n"
            +"  b\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"| a |"
                        "br" {}
                        +"\nb"
                    }
                }
            }
        }
    }

    @Test
    fun `should round-trip a hard break inside a list item`() = runTest {
        // given — the shape the renderer writes for a `<br>` in an `<li>`
        val markdown = "- a  \n  b"

        // when
        val rendered = flowOf(markdown).parse().renderMarkdown()

        // then
        rendered sameAs markdown
    }

    @Test
    fun `should round-trip a backslash hard break inside a list item`() = runTest {
        // given
        val markdown = "- a\\\n  b"

        // when
        val rendered = flowOf(markdown).parse().renderMarkdown()

        // then — the renderer writes every hard break as two trailing spaces
        rendered sameAs "- a  \n  b"
    }

}
