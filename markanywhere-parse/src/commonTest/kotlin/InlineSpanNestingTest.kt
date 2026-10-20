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

import com.xemantic.kotlin.test.text.chunkedRandomly
import com.xemantic.markanywhere.flow.mergeAdjacentText
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Every inline span — `em`/`strong`/`del`/`mark`/`sup` and inline HTML — must
 * close in strict LIFO order, and a delimiter inside a link label must never
 * close a span opened before the `[`: a crossed stream crashes or garbles every
 * open-element-stack consumer (issues #58, #60, #62).
 */
class InlineSpanNestingTest {

    // --- #58: a delimiter in a link label must not close an outer span ---

    @Test
    fun `should keep an asterisk in a link label literal when an outer em is open`() = runTest {
        // given
        val textFlow = "*x [a*b](u) y*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"x "
                    "a"("href" to "u") { +"a*b" }
                    +" y"
                }
            }
        }
    }

    @Test
    fun `should keep a double asterisk in a link label literal when an outer strong is open`() = runTest {
        // given
        val textFlow = "**x [a**b](u) y**".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    +"x "
                    "a"("href" to "u") { +"a**b" }
                    +" y"
                }
            }
        }
    }

    @Test
    fun `should keep a tilde in a link label literal when an outer del is open`() = runTest {
        // given
        val textFlow = "~~[foo~bar](u)~~".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "del" {
                    "a"("href" to "u") { +"foo~bar" }
                }
            }
        }
    }

    @Test
    fun `should keep a caret in a link label literal when an outer sup is open`() = runTest {
        // given
        val textFlow = "^[foo^bar](u)^".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "sup" {
                    "a"("href" to "u") { +"foo^bar" }
                }
            }
        }
    }

    @Test
    fun `should keep an equals pair in a link label literal when an outer mark is open`() = runTest {
        // given
        val textFlow = "==[foo==bar](u)==".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "mark" {
                    "a"("href" to "u") { +"foo==bar" }
                }
            }
        }
    }

    @Test
    fun `should keep an html close tag in a link label literal when its open tag is outside`() = runTest {
        // given
        val textFlow = "<b>x [a</b>b](u) y</b>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                tag("b") {
                    +"x "
                    "a"("href" to "u") { +"a</b>b" }
                    +" y"
                }
            }
        }
    }

    // When the brackets turn out not to be a link, they are plain text, so the
    // delimiter kept literal inside them closes the outer span after all.

    @Test
    fun `should keep an asterisk in a committed link label literal even when it could close an outer em`() = runTest {
        // given
        val textFlow = "*x [a* b](u) y*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"x "
                    "a"("href" to "u") { +"a* b" }
                    +" y"
                }
            }
        }
    }

    @Test
    fun `should close an outer em with an asterisk inside brackets that are not a link`() = runTest {
        // given
        val textFlow = "*x [a*b] y*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" { +"x [a" }
                +"b] y*"
            }
        }
    }

    @Test
    fun `should close an outer del with a tilde pair inside brackets that are not a link`() = runTest {
        // given
        val textFlow = "~~foo [bar~~ baz]".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "del" { +"foo [bar" }
                +" baz]"
            }
        }
    }

    @Test
    fun `should close an outer html element with its close tag inside brackets that are not a link`() = runTest {
        // given
        val textFlow = "<b>foo [bar</b> baz]".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                tag("b") { +"foo [bar" }
                +" baz]"
            }
        }
    }

    @Test
    fun `should close an outer strong and em with a triple asterisk inside brackets that are not a link`() = runTest {
        // given
        val textFlow = "**a *b [c*** d]".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    +"a "
                    "em" { +"b [c" }
                }
                +" d]"
            }
        }
    }

    @Test
    fun `should close two outer spans one after another inside brackets that are not a link`() = runTest {
        // given
        val textFlow = "*a **b [c** d* e]".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"a "
                    "strong" { +"b [c" }
                    +" d"
                }
                +" e]"
            }
        }
    }

    @Test
    fun `should close an outer em inside an unresolved reference link`() = runTest {
        // given
        val textFlow = "*x [a* b][nope] y".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" { +"x [a" }
                +" b][nope] y"
            }
        }
    }

    // --- #62: closing del/mark/sup closes the spans opened inside it first ---

    @Test
    fun `should close a sup inside a del before the del at the line end`() = runTest {
        // given
        val textFlow = "~~foo ^bar~~".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "del" {
                    +"foo "
                    "sup" { +"bar" }
                }
            }
        }
    }

    @Test
    fun `should close a sup inside a mark before the mark at the line end`() = runTest {
        // given
        val textFlow = "==foo ^bar==".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "mark" {
                    +"foo "
                    "sup" { +"bar" }
                }
            }
        }
    }

    @Test
    fun `should close a sup inside a del before the del mid-line`() = runTest {
        // given
        val textFlow = "~~a ^b~~c".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "del" {
                    +"a "
                    "sup" { +"b" }
                }
                +"c"
            }
        }
    }

    @Test
    fun `should close a sup inside a mark before the mark mid-line`() = runTest {
        // given
        val textFlow = "==a ^b==c".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "mark" {
                    +"a "
                    "sup" { +"b" }
                }
                +"c"
            }
        }
    }

    @Test
    fun `should close an em inside a del before the del`() = runTest {
        // given
        val textFlow = "~~a *b~~".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "del" {
                    +"a "
                    "em" { +"b" }
                }
            }
        }
    }

    @Test
    fun `should close a del inside an em before the em`() = runTest {
        // given
        val textFlow = "*a ~~b* c".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"a "
                    "del" { +"b" }
                }
                +" c"
            }
        }
    }

    @Test
    fun `should keep closing an inner del before an outer mark`() = runTest {
        // given - the shape that was already balanced: the inner span closes first
        val textFlow = "==a ~~b~~".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "mark" {
                    +"a "
                    "del" { +"b" }
                }
            }
        }
    }

    @Test
    fun `should close nested del mark and sup in reverse opening order at the line end`() = runTest {
        // given
        val textFlow = "^a ==b ~~c".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "sup" {
                    +"a "
                    "mark" {
                        +"b "
                        "del" { +"c" }
                    }
                }
            }
        }
    }

    // --- #60: the block-end drain closes in reverse opening order ---

    @Test
    fun `should close an em inside an unclosed mark before the mark at the block end`() = runTest {
        // given
        val textFlow = "==a*b".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "mark" {
                    +"a"
                    "em" { +"b" }
                }
            }
        }
    }

    @Test
    fun `should close an em inside a mark before the mark closed at the block end`() = runTest {
        // given
        val textFlow = "==a*b==".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "mark" {
                    +"a"
                    "em" { +"b" }
                }
            }
        }
    }

    @Test
    fun `should close an em inside an unclosed del before the del at the block end`() = runTest {
        // given
        val textFlow = "~~a*b".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "del" {
                    +"a"
                    "em" { +"b" }
                }
            }
        }
    }

    @Test
    fun `should close an em inside an unclosed sup before the sup at the block end`() = runTest {
        // given
        val textFlow = "^a*b".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "sup" {
                    +"a"
                    "em" { +"b" }
                }
            }
        }
    }

    @Test
    fun `should close a label-local del before the strong closed at the label end`() = runTest {
        // given
        val textFlow = "[**a ~~b**](u)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "u") {
                    "strong" {
                        +"a "
                        "del" { +"b" }
                    }
                }
            }
        }
    }

    @Test
    fun `should close a label-local sup before the em closed at the label end`() = runTest {
        // given
        val textFlow = "[*a ^b*](u)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "u") {
                    "em" {
                        +"a "
                        "sup" { +"b" }
                    }
                }
            }
        }
    }

    @Test
    fun `should close a mark whose closing run is followed by a tilde`() = runTest {
        // given
        val textFlow = "~~a ==b==~~ c".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "del" {
                    +"a "
                    "mark" { +"b" }
                }
                +" c"
            }
        }
    }

    @Test
    fun `should close a mark whose closing run is followed by a caret`() = runTest {
        // given
        val textFlow = "^a ==b==^ c".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "sup" {
                    +"a "
                    "mark" { +"b" }
                }
                +" c"
            }
        }
    }

    @Test
    fun `should close a del whose closing run is followed by an asterisk`() = runTest {
        // given
        val textFlow = "*a ~~b~~* c".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"a "
                    "del" { +"b" }
                }
                +" c"
            }
        }
    }

    @Test
    fun `should keep every caret in a committed link label literal while an outer sup is open`() = runTest {
        // given
        val textFlow = "^a [b^ c^](u)^".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "sup" {
                    +"a "
                    "a"("href" to "u") { +"b^ c^" }
                }
            }
        }
    }

    @Test
    fun `should keep every tilde pair in a committed link label literal while an outer del is open`() = runTest {
        // given
        val textFlow = "~~a [~~b~~ c](u)~~".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "del" {
                    +"a "
                    "a"("href" to "u") { +"~~b~~ c" }
                }
            }
        }
    }

    @Test
    fun `DIVERGENCE - should keep the closer of a del force-closed by an em literal`() = runTest {
        // given
        val textFlow = "*a ~~b* c~~ d".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"a "
                    "del" { +"b" }
                }
                +" c~~ d"
            }
        }
    }

    @Test
    fun `should keep the closer of a mark force-closed by an em literal`() = runTest {
        // given
        val textFlow = "*a ==b* c== d".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"a "
                    "mark" { +"b" }
                }
                +" c== d"
            }
        }
    }

    @Test
    fun `should keep the closer of a sup force-closed by an em literal`() = runTest {
        // given
        val textFlow = "*a ^b* c^ d".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"a "
                    "sup" { +"b" }
                }
                +" c^ d"
            }
        }
    }

    // The orphaned closer of a force-closed span is only a run in closing
    // position that cannot open: a run that can is a legitimate span of its
    // own, as GFM pairs it (issue #135).

    @Test
    fun `should pair a del after the closer of a del force-closed by an em`() = runTest {
        // given
        val textFlow = "*a ~~b* c~~d~~ e".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"a "
                    "del" { +"b" }
                }
                +" c"
                "del" { +"d" }
                +" e"
            }
        }
    }

    @Test
    fun `should pair a sup after the closer of a sup force-closed by an em`() = runTest {
        // given
        val textFlow = "*a ^b* c^d^".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"a "
                    "sup" { +"b" }
                }
                +" c"
                "sup" { +"d" }
            }
        }
    }

    @Test
    fun `should keep the closer of a sup force-closed by an em literal at the line end`() = runTest {
        // given
        val textFlow = "*a ^b* c^\nd".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"a "
                    "sup" { +"b" }
                }
                +" c^\nd"
            }
        }
    }

    @Test
    fun `DIVERGENCE - should open a sup at a caret with no closer after a deferred close`() = runTest {
        // GFM keeps the last `^` literal, since no closer follows it — but a `^`
        // opens eagerly, and seeing that no closer follows would mean holding
        // the rest of the line. The `^` in the aborted label closed the outer
        // sup, so nothing marks the last one as an orphaned closer: it opens a
        // span the line end closes empty.
        // given
        val textFlow = "^a [b^ c] d^".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "sup" { +"a [b" }
                +" c] d"
                "sup" { }
            }
        }
    }

    // The renderer escapes a delimiter right after an opener (`*\~*`), so a run
    // followed by a backslash must resolve like a run followed by any other char.

    @Test
    fun `should open an em whose run is followed by a backslash escape`() = runTest {
        // given
        val textFlow = "*\\~*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" { +"~" }
            }
        }
    }

    @Test
    fun `should open a del whose run is followed by a backslash escape`() = runTest {
        // given
        val textFlow = "~~\\*~~".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "del" { +"*" }
            }
        }
    }

    @Test
    fun `should close a mark whose run is followed by a backslash escape`() = runTest {
        // given
        val textFlow = "==a==\\* b".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "mark" { +"a" }
                +"* b"
            }
        }
    }

    @Test
    fun `should keep an asterisk before a hard line break backslash literal`() = runTest {
        // given
        val textFlow = "foo *\\\nbar".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo *"
                "br" {}
                +"\nbar"
            }
        }
    }

    @Test
    fun `should keep an asterisk before a backslash ending the input literal`() = runTest {
        // given
        val textFlow = "*\\".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"*\\"
            }
        }
    }

    @Test
    fun `should keep a tilde pair before a hard line break backslash literal`() = runTest {
        // given
        val textFlow = "x ~~\\\ny".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"x ~~"
                "br" {}
                +"\ny"
            }
        }
    }

    @Test
    fun `should keep a tilde before a backslash in a windows path literal`() = runTest {
        // given
        val textFlow = "cd ~\\Documents".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"cd ~\\Documents"
            }
        }
    }

    @Test
    fun `should keep a tilde before an escaped bracket in a link label literal`() = runTest {
        // given
        val textFlow = "[a~\\]b](u)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "u") { +"a~]b" }
            }
        }
    }

    // A span opening on code or emphasis — the renderer writes `~~`a`~~` for a
    // del wrapping code — must open like one opening on a letter (issue #133).

    @Test
    fun `should open a del whose run is followed by inline code`() = runTest {
        // given
        val textFlow = "~~`a~b`~~ c".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "del" { "code" { +"a~b" } }
                +" c"
            }
        }
    }

    @Test
    fun `should open a del whose run is followed by strong`() = runTest {
        // given
        val textFlow = "~~**a**~~".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "del" { "strong" { +"a" } }
            }
        }
    }

    @Test
    fun `should open a mark whose run is followed by emphasis`() = runTest {
        // given
        val textFlow = "==*a*==".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "mark" { "em" { +"a" } }
            }
        }
    }

    @Test
    fun `should open a mark whose run is followed by inline code`() = runTest {
        // given
        val textFlow = "==`a`==".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "mark" { "code" { +"a" } }
            }
        }
    }

    @Test
    fun `should keep an equals pair before an underscore literal`() = runTest {
        // given
        val textFlow = "==_".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"==_"
            }
        }
    }

    @Test
    fun `should close a label-local del inside a label-local em at the label end`() = runTest {
        // given
        val textFlow = "[*a ~~b](u)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "u") {
                    "em" {
                        +"a "
                        "del" { +"b" }
                    }
                }
            }
        }
    }

    @Test
    fun `should close a label-local em inside a label-local del at the label end`() = runTest {
        // given
        val textFlow = "[~~a *b~~](u)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "u") {
                    "del" {
                        +"a "
                        "em" { +"b" }
                    }
                }
            }
        }
    }

}
