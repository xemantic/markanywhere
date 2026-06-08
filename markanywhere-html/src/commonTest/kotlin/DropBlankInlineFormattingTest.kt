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

import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class DropBlankInlineFormattingTest {

    @Test
    fun `should drop an empty emphasis element`() = runTest {
        // given — a stray empty <em> (e.g. an icon-font <i> renamed by simplify)
        val input = semanticEvents {
            "p" {
                "em" { }
                +"Versionen"
            }
        }

        // when
        val output = input.dropBlankInlineFormatting()

        // then
        output sameAs semanticEvents {
            "p" { +"Versionen" }
        }
    }

    @Test
    fun `should drop an empty abbr element`() = runTest {
        // when
        val output = semanticEvents {
            "p" {
                +"a"
                "abbr" { }
                +"b"
            }
        }.dropBlankInlineFormatting()

        // then
        output sameAs semanticEvents {
            "p" { +"a"; +"b" }
        }
    }

    @Test
    fun `should drop an element whose only content is whitespace`() = runTest {
        // when
        val output = semanticEvents {
            "strong" { +"  \n " }
        }.dropBlankInlineFormatting()

        // then
        output sameAs semanticEvents { }
    }

    @Test
    fun `should keep a formatting element with text content`() = runTest {
        // given
        val input = semanticEvents {
            "p" {
                "strong" { +"bold" }
            }
        }

        // when
        val output = input.dropBlankInlineFormatting()

        // then
        output sameAs semanticEvents {
            "p" {
                "strong" { +"bold" }
            }
        }
    }

    @Test
    fun `should collapse a wrapper whose only child is an empty formatting element`() = runTest {
        // given — <sup><em></em></sup> is empty bottom-up
        val input = semanticEvents {
            "sup" {
                "em" { }
            }
        }

        // when
        val output = input.dropBlankInlineFormatting()

        // then
        output sameAs semanticEvents { }
    }

    @Test
    fun `should keep a formatting element that wraps a non-formatting child`() = runTest {
        // given — an empty <em> would drop, but here it wraps a link
        val input = semanticEvents {
            "em" {
                "a"("href" to "/x") { +"link" }
            }
        }

        // when
        val output = input.dropBlankInlineFormatting()

        // then
        output sameAs semanticEvents {
            "em" {
                "a"("href" to "/x") { +"link" }
            }
        }
    }

    @Test
    fun `should keep an outer wrapper with content while dropping a nested empty element`() = runTest {
        // given — <sup><em></em>1</sup>: the em is empty (drop), the sup has
        // text content (keep). The nested unmark must not disturb the still-
        // suppressed outer mark.
        val input = semanticEvents {
            "sup" {
                "em" { }
                +"1"
            }
        }

        // when
        val output = input.dropBlankInlineFormatting()

        // then
        output sameAs semanticEvents {
            "sup" { +"1" }
        }
    }

    @Test
    fun `should keep a formatting element whose only content is a non-breaking space`() = runTest {
        // given — NBSP is printable content in HTML, not blank whitespace
        val input = semanticEvents {
            "p" {
                "em" { +" " }
            }
        }

        // when
        val output = input.dropBlankInlineFormatting()

        // then
        output sameAs semanticEvents {
            "p" {
                "em" { +" " }
            }
        }
    }

    @Test
    fun `should drop an empty formatting element even when it carries attributes`() = runTest {
        // given — attributes on inline emphasis are dropped at render time anyway
        val input = semanticEvents {
            "p" {
                "time"("datetime" to "2026-06-08") { }
                +"x"
            }
        }

        // when
        val output = input.dropBlankInlineFormatting()

        // then
        output sameAs semanticEvents {
            "p" { +"x" }
        }
    }

    @Test
    fun `should leave an empty span untouched`() = runTest {
        // given — span is not a formatting tag; a caller may keep it for its id
        val input = semanticEvents {
            "span"("golemId" to "2") { }
        }

        // when
        val output = input.dropBlankInlineFormatting()

        // then
        output sameAs semanticEvents {
            "span"("golemId" to "2") { }
        }
    }
}