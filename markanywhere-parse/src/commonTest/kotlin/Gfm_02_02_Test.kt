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

import com.xemantic.kotlin.core.text.buildText
import com.xemantic.kotlin.test.text.chunkedRandomly
import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Tests for GFM Section 2.2 — Tabs.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#tabs
 *
 * Tabs in lines are not expanded to spaces. However, in contexts
 * where whitespace helps to define block structure, tabs behave
 * as if they were replaced by spaces with a tab stop of 4
 * characters.
 */
@Suppress("ClassName")
class Gfm_02_02_Test {

    @Test
    fun `example 1 - tab can replace four spaces in indented code block, internal tabs preserved`() = runTest {
        // given
        val textFlow = "\tfoo\tbaz\t\tbim".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"foo\tbaz\t\tbim\n"
                }
            }
        }
        // CommonMark expected:
        /*
            <pre><code>foo→baz→→bim
            </code></pre>
         */
    }

    @Test
    fun `example 2 - two spaces plus tab still forms indented code block`() = runTest {
        // given
        val textFlow = "  \tfoo\tbaz\t\tbim".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"foo\tbaz\t\tbim\n"
                }
            }
        }
        // CommonMark expected:
        /*
            <pre><code>foo→baz→→bim
            </code></pre>
         */
    }

    @Test
    fun `example 3 - tabs preserved literally in code block content`() = runTest {
        // given
        val textFlow = buildText {
            +"    a\ta\n"
            +"    ὐ\ta\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"a\ta\nὐ\ta\n"
                }
            }
        }
        // CommonMark expected:
        /*
            <pre><code>a→a
            ὐ→a
            </code></pre>
         */
    }

    @Test
    fun `example 4 - tab as list item continuation indent`() = runTest {
        // given
        val textFlow = buildText {
            +"  - foo\n"
            +"\n"
            +"\tbar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"foo" }
                    "p" { +"bar" }
                }
            }
        }
        // CommonMark expected:
        /*
            <ul>
            <li>
            <p>foo</p>
            <p>bar</p>
            </li>
            </ul>
         */
    }

    @Test
    fun `example 5 - two tabs in list continuation produce code block with two-space content`() = runTest {
        // given
        val textFlow = buildText {
            +"- foo\n"
            +"\n"
            +"\t\tbar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"foo" }
                    "pre" {
                        "code" {
                            +"  bar\n"
                        }
                    }
                }
            }
        }
        // CommonMark expected:
        /*
            <ul>
            <li>
            <p>foo</p>
            <pre><code>  bar
            </code></pre>
            </li>
            </ul>
         */
    }

    @Test
    fun `example 6 - blockquote with two tabs yields code block with two-space content`() = runTest {
        // given
        val textFlow = ">\t\tfoo".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "blockquote" {
                "pre" {
                    "code" {
                        +"  foo\n"
                    }
                }
            }
        }
        // CommonMark expected:
        /*
            <blockquote>
            <pre><code>  foo
            </code></pre>
            </blockquote>
         */
    }

    @Test
    fun `example 7 - list item with two tabs yields code block with two-space content`() = runTest {
        // given
        val textFlow = "-\t\tfoo".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "pre" {
                        "code" {
                            +"  foo\n"
                        }
                    }
                }
            }
        }
        // CommonMark expected:
        /*
            <ul>
            <li>
            <pre><code>  foo
            </code></pre>
            </li>
            </ul>
         */
    }

    @Test
    fun `example 8 - tab line continues indented code block from four-space line`() = runTest {
        // given
        val textFlow = buildText {
            +"    foo\n"
            +"\tbar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"foo\nbar\n"
                }
            }
        }
        // CommonMark expected:
        /*
            <pre><code>foo
            bar
            </code></pre>
         */
    }

    @Test
    fun `example 9 - tab in nested list indentation`() = runTest {
        // given
        val textFlow = buildText {
            +" - foo\n"
            +"   - bar\n"
            +"\t - baz\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" { +"foo" }
                    "ul" {
                        "li" {
                            "p" { +"bar" }
                            "ul" {
                                "li" { "p" { +"baz" } }
                            }
                        }
                    }
                }
            }
        }
        // CommonMark expected:
        /*
            <ul>
            <li>foo
            <ul>
            <li>bar
            <ul>
            <li>baz</li>
            </ul>
            </li>
            </ul>
            </li>
            </ul>
         */
        // markanywhere always-loose policy: each item's inline content is wrapped in <p>.
    }

    @Test
    fun `example 10 - ATX heading with tab after hash`() = runTest {
        // given
        val textFlow = "#\tFoo".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed sameAs semanticEvents {
            "h1" { +"Foo" }
        }
        // CommonMark expected:
        /*
            <h1>Foo</h1>
         */
    }

    @Test
    fun `example 11 - thematic break with tabs between markers`() = runTest {
        // given
        val textFlow = "*\t*\t*\t".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed sameAs semanticEvents {
            "hr" {}
        }
        // CommonMark expected:
        /*
            <hr />
         */
    }

}
