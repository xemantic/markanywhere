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

package com.xemantic.markanywhere.parse.gfm

import com.xemantic.kotlin.core.text.buildText
import com.xemantic.kotlin.test.text.chunkedRandomly
import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.parse.parse
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Tests for GFM Section 04.04 — Indented code blocks.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#indented-code-blocks
 */
@Suppress("ClassName")
class Gfm_04_04_Test {

    // TODO review
    @Test
    fun `example 77 - indented code block`() = runTest {
        // given
        val textFlow = buildText {
            +"    a simple\n"
            +"      indented code block\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"a simple\n  indented code block\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>a simple
              indented code block
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 78 - ul with 1 item`() = runTest {
        // given
        val textFlow = buildText {
            +"  - foo\n"
            +"\n"
            +"    bar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ul" {
                "li" {
                    "p" {
                        +"foo"
                    }
                    "p" {
                        +"bar"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ul>
            <li>
            <p>foo</p>
            <p>bar</p>
            </li>
            </ul>
         */
    }

    // TODO review
    @Test
    fun `example 79 - ol with 1 item`() = runTest {
        // given
        val textFlow = buildText {
            +"1.  foo\n"
            +"\n"
            +"    - bar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "ol" {
                "li" {
                    "p" {
                        +"foo"
                    }
                    "ul" {
                        "li" {
                            +"bar"
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <ol>
            <li>
            <p>foo</p>
            <ul>
            <li>bar</li>
            </ul>
            </li>
            </ol>
         */
    }

    // TODO review
    @Test
    fun `example 80 - indented code block`() = runTest {
        // given
        val textFlow = buildText {
            +"    <a/>\n"
            +"    *hi*\n"
            +"\n"
            +"    - one\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"<a/>\n*hi*\n\n- one\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>&lt;a/&gt;
            *hi*
            
            - one
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 81 - indented code block`() = runTest {
        // given
        val textFlow = buildText {
            +"    chunk1\n"
            +"\n"
            +"    chunk2\n"
            +"  \n"
            +" \n"
            +" \n"
            +"    chunk3\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"chunk1\n\nchunk2\n\n\n\nchunk3\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>chunk1
            
            chunk2
            
            
            
            chunk3
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 82 - indented code block`() = runTest {
        // given
        val textFlow = buildText {
            +"    chunk1\n"
            +"      \n"
            +"      chunk2\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"chunk1\n  \n  chunk2\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>chunk1
              
              chunk2
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 83 - paragraph Foo bar`() = runTest {
        // given
        val textFlow = buildText {
            +"Foo\n"
            +"    bar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Foo\nbar"
            }
        }
        // GFM expected:
        /*
            <p>Foo
            bar</p>
         */
    }

    // TODO review
    @Test
    fun `example 84 - indented code block, paragraph bar`() = runTest {
        // given
        val textFlow = buildText {
            +"    foo\n"
            +"bar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"foo\n"
                }
            }
            "p" {
                +"bar"
            }
        }
        // GFM expected:
        /*
            <pre><code>foo
            </code></pre>
            <p>bar</p>
         */
    }

    // TODO review
    @Test
    fun `example 85 - h1 Heading, indented code block, h2 Heading, indented code block, them (truncated)`() = runTest {
        // given
        val textFlow = buildText {
            +"# Heading\n"
            +"    foo\n"
            +"Heading\n"
            +"------\n"
            +"    foo\n"
            +"----\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "h1" {
                +"Heading"
            }
            "pre" {
                "code" {
                    +"foo\n"
                }
            }
            "h2" {
                +"Heading"
            }
            "pre" {
                "code" {
                    +"foo\n"
                }
            }
            "hr" {}
        }
        // GFM expected:
        /*
            <h1>Heading</h1>
            <pre><code>foo
            </code></pre>
            <h2>Heading</h2>
            <pre><code>foo
            </code></pre>
            <hr />
         */
    }

    // TODO review
    @Test
    fun `example 86 - indented code block`() = runTest {
        // given
        val textFlow = buildText {
            +"        foo\n"
            +"    bar\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"    foo\nbar\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>    foo
            bar
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 87 - indented code block`() = runTest {
        // given
        val textFlow = buildText {
            +"\n"
            +"    \n"
            +"    foo\n"
            +"    \n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"foo\n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>foo
            </code></pre>
         */
    }

    // TODO review
    @Test
    fun `example 88 - indented code block`() = runTest {
        // given
        val textFlow = buildText {
            +"    foo  \n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"foo  \n"
                }
            }
        }
        // GFM expected:
        /*
            <pre><code>foo  
            </code></pre>
         */
    }

}
