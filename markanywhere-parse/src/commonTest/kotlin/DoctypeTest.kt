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
import com.xemantic.kotlin.test.text.chunkedRandomly
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * DOCTYPE declarations (a structural subset of CommonMark §4.6 type-4 HTML
 * blocks) are parsed as `mark("doctype", isTagged = true)` + content text +
 * `unmark`, rather than the GFM raw-HTML pass-through.
 *
 * Detection:
 *  - Up to 3 leading spaces, then `<!DOCTYPE` matched case-insensitively.
 *  - Next char must be space, tab, `>`, or end-of-line (so `<!DOCTYPER>` is
 *    a generic type-4 declaration, not a DOCTYPE).
 *
 * Content:
 *  - Everything between `<!DOCTYPE` and the first `>` is the content text.
 *  - The whitespace run immediately after `<!DOCTYPE` on the opener line is
 *    stripped (it's the keyword/value separator).
 *  - Multi-line content preserves leading whitespace on continuation lines
 *    verbatim, joined by `\n`.
 *  - Anything after the closing `>` flows as a top-level text event with
 *    a trailing `\n`.
 */
class DoctypeTest {

    @Test
    fun `should emit mark unmark for HTML5 doctype`() = runTest {
        // given
        val textFlow = "<!DOCTYPE html>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("doctype") {
                +"html"
            }
        }
    }

    @Test
    fun `should match DOCTYPE keyword in title case`() = runTest {
        // given
        val textFlow = "<!Doctype html>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("doctype") {
                +"html"
            }
        }
    }

    @Test
    fun `should match DOCTYPE keyword in all lowercase`() = runTest {
        // given: HTML5 declares DOCTYPE case-insensitive. CommonMark restricts
        // type-4 to uppercase declarations; markanywhere extends just this
        // name so the all-lowercase form routes through the structural path.
        val textFlow = "<!doctype html>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("doctype") {
                +"html"
            }
        }
    }

    @Test
    fun `should match DOCTYPE keyword with arbitrary mixed casing`() = runTest {
        // given
        val textFlow = "<!dOcTyPe html>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("doctype") {
                +"html"
            }
        }
    }

    @Test
    fun `should always emit lowercase doctype mark name`() = runTest {
        // given: regardless of source casing, the mark name is normalised to
        // `doctype` (lowercase) — downstream renderers/transformers can match
        // by a single canonical name.
        val textFlow = "<!DOCTYPE html>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then: explicit, no DSL — verify the mark name is exactly "doctype"
        val events = parsed.mergeAdjacentText().toList()
        val mark = events.first() as SemanticEvent.Mark
        assertEquals("doctype", mark.name)
        assertEquals(true, mark.isTagged)
        val unmark = events.last() as SemanticEvent.Unmark
        assertEquals("doctype", unmark.name)
        assertEquals(true, unmark.isTagged)
    }

    @Test
    fun `should preserve content casing`() = runTest {
        // given
        val textFlow = "<!DOCTYPE HTML>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("doctype") {
                +"HTML"
            }
        }
    }

    @Test
    fun `should emit empty content for bare DOCTYPE`() = runTest {
        // given
        val textFlow = "<!DOCTYPE>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("doctype") {}
        }
    }

    @Test
    fun `should collapse leading whitespace run after keyword`() = runTest {
        // given: tab + multiple spaces between `<!DOCTYPE` and `html`
        val textFlow = "<!DOCTYPE\t   html>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("doctype") {
                +"html"
            }
        }
    }

    @Test
    fun `should preserve HTML 4_01 strict PUBLIC declaration as a single text run`() = runTest {
        // given: HTML 4.01 strict DOCTYPE with FPI (Formal Public Identifier)
        // and DTD URL. Internal whitespace, double-quoted strings, and the
        // `//` slashes inside the FPI are all preserved verbatim — no nested
        // structure is exposed, the whole declaration body lands as one text
        // event between `Mark("doctype")` and `Unmark("doctype")`.
        val source = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">"
        val textFlow = source.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("doctype") {
                +"HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\""
            }
        }
    }

    @Test
    fun `should preserve XHTML 1_0 strict PUBLIC declaration`() = runTest {
        // given
        val source = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">"
        val textFlow = source.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("doctype") {
                +"html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\""
            }
        }
    }

    @Test
    fun `should preserve SYSTEM identifier`() = runTest {
        // given: HTML5 about:legacy-compat SYSTEM identifier
        val source = "<!DOCTYPE html SYSTEM \"about:legacy-compat\">"
        val textFlow = source.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("doctype") {
                +"html SYSTEM \"about:legacy-compat\""
            }
        }
    }

    @Test
    fun `should preserve internal subset square brackets`() = runTest {
        // given: an XML-style internal DTD subset. Square brackets, semicolons,
        // and percent-references are content — the parser emits the whole
        // declaration body as text without trying to interpret any of it.
        val source = "<!DOCTYPE root [<!ELEMENT root EMPTY>]>"
        val textFlow = source.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then: closing `>` is the FIRST `>` — the inner `<!ELEMENT root EMPTY>`
        // contains a `>` so the DOCTYPE closes there, and the residue `]>`
        // emits as a trailing top-level text. This is a known limitation: the
        // declaration grammar is not parsed, only the outermost `<!DOCTYPE`/`>`
        // pair is recognised.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("doctype") {
                +"root [<!ELEMENT root EMPTY"
            }
            +"]>\n"
        }
    }

    @Test
    fun `should stream multi-line DOCTYPE`() = runTest {
        // given
        val textFlow = buildString {
            +"<!DOCTYPE html PUBLIC\n"
            +"  \"-//W3C//DTD HTML 4.01//EN\"\n"
            +"  \"http://www.w3.org/TR/html4/strict.dtd\">\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("doctype") {
                +"html PUBLIC\n  \"-//W3C//DTD HTML 4.01//EN\"\n  \"http://www.w3.org/TR/html4/strict.dtd\""
            }
        }
    }

    @Test
    fun `should allow up to 3 leading spaces`() = runTest {
        // given
        val textFlow = "   <!DOCTYPE html>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("doctype") {
                +"html"
            }
        }
    }

    @Test
    fun `should not parse DOCTYPE with 4 leading spaces as declaration`() = runTest {
        // given: 4+ leading spaces forces an indented code block
        val textFlow = "    <!DOCTYPE html>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code" {
                    +"<!DOCTYPE html>\n"
                }
            }
        }
    }

    @Test
    fun `should keep generic declaration as raw text`() = runTest {
        // given: type-4 detection still fires for `<!ENTITY …>` etc., but the
        // DOCTYPE narrowing rejects it — falls back to the existing raw-text path
        val textFlow = "<!ENTITY foo>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            +"<!ENTITY foo>\n"
        }
    }

    @Test
    fun `should not match DOCTYPER as DOCTYPE`() = runTest {
        // given: name continues past `DOCTYPE` — falls back to raw text
        val textFlow = "<!DOCTYPER html>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            +"<!DOCTYPER html>\n"
        }
    }

    @Test
    fun `should emit trailing text after closing gt as paragraph`() = runTest {
        // given: GFM treats this as a single type-4 block — anything after `>`
        // on the same line is part of the raw-HTML content. We split: DOCTYPE
        // closes at `>` and the trailing text is emitted as a standalone text
        // event with `\n` (mirrors the type-1 trailing-after-close behaviour).
        val textFlow = "<!DOCTYPE html>trailing".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("doctype") {
                +"html"
            }
            +"trailing\n"
        }
    }

    @Test
    fun `should close DOCTYPE before following paragraph`() = runTest {
        // given
        val textFlow = buildString {
            +"<!DOCTYPE html>\n"
            +"\n"
            +"hello\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("doctype") {
                +"html"
            }
            "p" { +"hello" }
        }
    }

    @Test
    fun `should interrupt paragraph with DOCTYPE`() = runTest {
        // given: CommonMark HTML block types 1-6 (DOCTYPE is type 4) can
        // interrupt an open paragraph without a preceding blank line.
        val textFlow = buildString {
            +"some text\n"
            +"<!DOCTYPE html>\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"some text" }
            tag("doctype") {
                +"html"
            }
        }
    }

    @Test
    fun `should pass DOCTYPE through fenced code block as raw text`() = runTest {
        // given: inside a fenced code block, no constructs are parsed — content
        // streams verbatim. The DOCTYPE recognition is suppressed because the
        // dispatcher is in CodeBlock mode, not Start mode, when the line arrives.
        val textFlow = buildString {
            +"```html\n"
            +"<!DOCTYPE html>\n"
            +"```\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "pre" {
                "code"("class" to "language-html") {
                    +"<!DOCTYPE html>\n"
                }
            }
        }
    }

    @Test
    fun `should pass DOCTYPE through indented code block as raw text`() = runTest {
        // given: a 4-space-indented line is an indented code block — the line's
        // 4 leading spaces never reach the HTML-block dispatcher, so the DOCTYPE
        // detector is never asked about this line.
        val textFlow = buildString {
            +"some paragraph\n"
            +"\n"
            +"    <!DOCTYPE html>\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" { +"some paragraph" }
            "pre" {
                "code" {
                    +"<!DOCTYPE html>\n"
                }
            }
        }
    }

    @Test
    fun `should pass DOCTYPE through inline code span as literal`() = runTest {
        // given: between backticks the DOCTYPE characters are inline code
        // content — no block-level dispatch can fire mid-paragraph.
        val textFlow = "Use `<!DOCTYPE html>` to opt into HTML5.".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"Use "
                "code" {
                    +"<!DOCTYPE html>"
                }
                +" to opt into HTML5."
            }
        }
    }

    @Test
    fun `should force-close unterminated DOCTYPE at EOF`() = runTest {
        // given: no closing `>` ever arrives — finalize() must still emit
        // unmark to keep the event stream balanced
        val textFlow = flowOf("<!DOCTYPE html PUBLIC")

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            tag("doctype") {
                +"html PUBLIC"
            }
        }
    }
}
