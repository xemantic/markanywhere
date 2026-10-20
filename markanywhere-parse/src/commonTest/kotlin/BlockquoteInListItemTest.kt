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
 * Blockquotes (GFM §5.1) inside list items (§5.2). A blockquote opened in a
 * list item lives on `ListContext` rather than as a sub-parse frame, so the
 * block constructs it may hold are recognised by the list dispatcher itself
 * (issue #149: an ATX heading in it was read as paragraph text).
 */
class BlockquoteInListItemTest {

    @Test
    fun `should parse an ATX heading in a blockquote opening a list item`() = runTest {
        // given
        val textFlow = "- > ### B\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "blockquote" {
                        "h3" { +"B" }
                    }
                }
            }
        }
    }

    @Test
    fun `should parse an ATX heading in a blockquote later in a list item`() = runTest {
        // given
        val textFlow = buildString {
            +"- x\n"
            +"\n"
            +"  > ### B\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"x" }
                    "blockquote" {
                        "h3" { +"B" }
                    }
                }
            }
        }
    }

    @Test
    fun `should let an ATX heading interrupt a blockquote paragraph in a list item`() = runTest {
        // given
        val textFlow = buildString {
            +"- > a\n"
            +"  > ### B\n"
            +"  > c\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "blockquote" {
                        "p" { +"a" }
                        "h3" { +"B" }
                        "p" { +"c" }
                    }
                }
            }
        }
    }

    @Test
    fun `should end a blockquote after a heading at a line without a prefix`() = runTest {
        // given — a heading is no paragraph, so `foo` cannot lazily continue it
        val textFlow = buildString {
            +"- > ### B\n"
            +"  foo\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "blockquote" {
                        "h3" { +"B" }
                    }
                    "p" { +"foo" }
                }
            }
        }
    }

    @Test
    fun `should parse a heading of the list item after a blockquote heading`() = runTest {
        // given
        val textFlow = buildString {
            +"- > ### B\n"
            +"  ## C\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "blockquote" {
                        "h3" { +"B" }
                    }
                    "h2" { +"C" }
                }
            }
        }
    }

    @Test
    fun `should end a blockquote after an empty prefix line at a line without a prefix`() = runTest {
        // given
        val textFlow = buildString {
            +"- > a\n"
            +"  >\n"
            +"  foo\n"
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
                    "p" { +"foo" }
                }
            }
        }
    }

    @Test
    fun `should lazily continue a blockquote paragraph in a list item`() = runTest {
        // given
        val textFlow = buildString {
            +"- > a\n"
            +"  b\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "blockquote" {
                        "p" { +"a\nb" }
                    }
                }
            }
        }
    }

    @Test
    fun `should lazily continue a blockquote paragraph in a list item with an indented line`() = runTest {
        // given — an indented code block cannot interrupt a paragraph
        val textFlow = buildString {
            +"- > a\n"
            +"      b\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "blockquote" {
                        "p" { +"a\nb" }
                    }
                }
            }
        }
    }

    @Test
    fun `should end a blockquote at a link reference definition`() = runTest {
        // given
        val textFlow = buildString {
            +"- > a\n"
            +"  >\n"
            +"  [x]: /u\n"
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
    fun `should strip a closing sequence from a heading in a blockquote in a list item`() = runTest {
        // given
        val textFlow = "- > ## foo ##\n".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "blockquote" {
                        "h2" { +"foo" }
                    }
                }
            }
        }
    }

    @Test
    fun `should strip a closing sequence from a heading in a list item`() = runTest {
        // given
        val textFlow = buildString {
            +"- ## foo ##  \n"
            +"- x\n"
            +"\n"
            +"  ### bar ###\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "h2" { +"foo" }
                }
                "li" {
                    "p" { +"x" }
                    "h3" { +"bar" }
                }
            }
        }
    }

    @Test
    fun `should keep a hash run not preceded by whitespace in a heading in a list item`() = runTest {
        // given — GFM example 81 inside a list item
        val textFlow = buildString {
            +"- # foo#\n"
            +"- # foo \\#\n"
            +"- ### ###\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "h1" { +"foo#" }
                }
                "li" {
                    "h1" { +"foo #" }
                }
                "li" {
                    "h3" {}
                }
            }
        }
    }

    @Test
    fun `should round-trip a heading in a blockquote in a list item`() = runTest {
        // given
        val events = semanticEvents {
            "ul" {
                "li" {
                    "blockquote" {
                        "h3" { +"B" }
                        "p" { +"c" }
                    }
                }
            }
        }

        // when
        val markdown = events.renderMarkdown()
        val parsed = flowOf(markdown).parse()

        // then
        markdown sameAs "- > ### B\n  >\n  > c"
        parsed.mergeAdjacentText() sameAs events
        flowOf(markdown).parse().renderMarkdown() sameAs markdown
    }

}
