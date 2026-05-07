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
 * Tests for GFM Section 06.03 — Code spans.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#code-spans
 */
@Suppress("ClassName")
class Gfm_06_03_Test {

    @Test
    fun `example 338 - paragraph foo`() = runTest {
        // given
        val textFlow = "`foo`".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><code>foo</code></p>
         */
    }

    @Test
    fun `example 339 - paragraph foo bar`() = runTest {
        // given
        val textFlow = "`` foo ` bar ``".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"foo ` bar"
                }
            }
        }
        // GFM expected:
        /*
            <p><code>foo ` bar</code></p>
         */
    }

    @Test
    fun `example 340 - DIVERGENCE - N=1 streaming skips strip rule`() = runTest {
        // given
        val textFlow = "` `` `".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Streaming divergence: N=1 code spans emit content as text events as
        // chars arrive (typewriter UX), so the GFM §6.3 strip rule (which would
        // remove a leading and trailing space when content begins+ends with
        // space and has any non-space) cannot be applied — by close-time the
        // edge spaces have already been flushed downstream. Run-length matching
        // *is* applied, so the inner `` `` `` remains content rather than closing.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +" `` "
                }
            }
        }
        // GFM expected:
        /*
            <p><code>``</code></p>
         */
    }

    @Test
    fun `example 341 - DIVERGENCE - N=1 streaming skips strip rule`() = runTest {
        // given
        val textFlow = "`  ``  `".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Same divergence as 340 — N=1 streams content, so the strip rule does
        // not run.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"  ``  "
                }
            }
        }
        // GFM expected:
        /*
            <p><code> `` </code></p>
         */
    }

    @Test
    fun `example 342 - paragraph a`() = runTest {
        // given
        val textFlow = "` a`".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +" a"
                }
            }
        }
        // GFM expected:
        /*
            <p><code> a</code></p>
         */
    }

    @Test
    fun `example 343 - paragraph b`() = runTest {
        // given
        val textFlow = "` b `".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +" b "
                }
            }
        }
        // GFM expected:
        /*
            <p><code> b </code></p>
         */
    }

    @Test
    fun `example 344 - empty paragraph`() = runTest {
        // given
        val textFlow = """
            ` `
            `  `
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +" "
                }
                +"\n"
                "code" {
                    +"  "
                }
            }
        }
        // GFM expected:
        /*
            <p><code> </code>
            <code>  </code></p>
         */
    }

    @Test
    fun `example 345 - DIVERGENCE - multi-line code span does not pair across lines`() = runTest {
        // given
        val textFlow = buildText {
            +"``\n"
            +"foo\n"
            +"bar  \n"
            +"baz\n"
            +"``\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Streaming divergence: `flushInline` force-closes inline state at every
        // line boundary, so an unclosed backtick run cannot span a soft break.
        // Both `` `` `` openers replay as literal text. Trailing two spaces on
        // `bar  ` still produce a hard line break per GFM §6.7.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"``\nfoo\nbar"
                "br" {}
                +"\nbaz\n``"
            }
        }
        // GFM expected:
        /*
            <p><code>foo bar   baz</code></p>
         */
    }

    @Test
    fun `example 346 - DIVERGENCE - multi-line code span does not pair across lines`() = runTest {
        // given
        val textFlow = buildText {
            +"``\n"
            +"foo \n"
            +"``\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Same divergence as 345: backtick runs cannot span a soft break, so
        // both `` `` `` markers are literal source text.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"``\nfoo\n``"
            }
        }
        // GFM expected:
        /*
            <p><code>foo </code></p>
         */
    }

    @Test
    fun `example 347 - DIVERGENCE - multi-line code span force-closes at line boundary`() = runTest {
        // given
        val textFlow = buildText {
            +"`foo   bar \n"
            +"baz`\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Streaming divergence: `flushInline` force-closes the open code span
        // at every line/block boundary, so the first line's `` ` `` opens a
        // span that closes at end-of-line (with whatever streamed before — the
        // trailing space is consumed by `paragraphTrailingSpaces` and dropped
        // for the soft break). The second line's `` ` `` accumulates as a
        // pending opener and emits as literal text at paragraph close.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"foo   bar"
                }
                +"\nbaz`"
            }
        }
        // GFM expected:
        /*
            <p><code>foo   bar  baz</code></p>
         */
    }

    @Test
    fun `example 348 - paragraph foobar`() = runTest {
        // given
        val textFlow = buildText {
            +"`foo\\`bar`\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"foo\\"
                }
                +"bar`"
            }
        }
        // GFM expected:
        /*
            <p><code>foo\</code>bar`</p>
         */
    }

    @Test
    fun `example 349 - paragraph foobar`() = runTest {
        // given
        val textFlow = "``foo`bar``".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"foo`bar"
                }
            }
        }
        // GFM expected:
        /*
            <p><code>foo`bar</code></p>
         */
    }

    @Test
    fun `example 350 - DIVERGENCE - N=1 streaming skips strip rule`() = runTest {
        // given
        val textFlow = "` foo `` bar `".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Same divergence as 340/341 — N=1 streams content, so the strip rule
        // does not run. Run-length matching is applied, so the inner `` `` ``
        // stays as content rather than closing the span.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +" foo `` bar "
                }
            }
        }
        // GFM expected:
        /*
            <p><code>foo `` bar</code></p>
         */
    }

    @Test
    fun `example 351 - DIVERGENCE - emphasis opens before code-span priority is known`() = runTest {
        // given
        val textFlow = "*foo`*`".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // GFM gives code spans precedence over emphasis: the second `*` lives
        // inside `<code>…</code>` and so cannot close the opening `*`. Our
        // streaming emphasis resolver commits the open `*` before the code-span
        // delimiter is seen, producing `<em>foo<code>*</code></em>` instead.
        // Spec-correct precedence requires CommonMark's process_emphasis pass,
        // which can only run after the paragraph closes — incompatible with
        // append-only event emission.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo"
                    "code" {
                        +"*"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p>*foo<code>*</code></p>
         */
    }

    @Test
    fun `example 352 - DIVERGENCE - inline link commits before code-span priority is known`() = runTest {
        // given
        val textFlow = "[not a `link](/foo`)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // After Phase 3a label-content rendering, label content flows through
        // normal inline parsing — so the backtick run between `` `link `` and
        // `` `) `` opens a code span before the `]` triggers tentative close.
        // The code span captures `]` and `(` as content, the label never
        // completes, and the bracket aborts to literal text. This is closer
        // to GFM's expected output (code span wins over link priority).
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"[not a "
                "code" {
                    +"link](/foo"
                }
                +")"
            }
        }
        // GFM expected:
        /*
            <p>[not a <code>link](/foo</code>)</p>
         */
    }

    @Test
    fun `example 353 - paragraph a href=`() = runTest {
        // given
        val textFlow = """`<a href="`">`""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"<a href=\""
                }
                +"\">`"
            }
        }
        // GFM expected:
        /*
            <p><code>&lt;a href=&quot;</code>&quot;&gt;`</p>
         */
    }

    @Test
    fun `example 354 - paragraph`() = runTest {
        // given
        val textFlow = """<a href="`">`""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                tagged {
                    "a"("href" to "`") {
                        +"`"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p><a href="`">`</p>
         */
    }

    @Test
    fun `example 355 - paragraph httpfoobarbaz`() = runTest {
        // given
        val textFlow = "`<http://foo.bar.`baz>`".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"<http://foo.bar."
                }
                +"baz>`"
            }
        }
        // GFM expected:
        /*
            <p><code>&lt;http://foo.bar.</code>baz&gt;`</p>
         */
    }

    @Test
    fun `example 356 - paragraph httpfoobarbaz`() = runTest {
        // given
        val textFlow = "<http://foo.bar.`baz>`".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "a"("href" to "http://foo.bar.%60baz") {
                    +"http://foo.bar.`baz"
                }
                +"`"
            }
        }
        // GFM expected:
        /*
            <p><a href="http://foo.bar.%60baz">http://foo.bar.`baz</a>`</p>
         */
    }

    @Test
    fun `example 357 - DIVERGENCE - unmatched opener force-closes as code span`() = runTest {
        // given
        val textFlow = "```foo``".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Streaming divergence: `` ``` `` opens a span at the first non-backtick
        // (mark fires eagerly so chars can stream into `<code>` for typewriter
        // UX). With no run of three backticks later, the span force-closes at
        // paragraph end and trailing buffered backticks emit as content.
        // GFM-correct behavior would replay the opening run as literal source,
        // but that requires deferring the open mark until the closer is seen
        // — incompatible with mid-span streaming.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"foo``"
                }
            }
        }
        // GFM expected:
        /*
            <p>```foo``</p>
         */
    }

    @Test
    fun `example 358 - DIVERGENCE - unmatched opener force-closes as code span`() = runTest {
        // given
        val textFlow = "`foo".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Same divergence as 357 — eager `mark("code")` at open commits to a
        // code span; an unmatched closer force-closes at paragraph end.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p>`foo</p>
         */
    }

    @Test
    fun `example 359 - DIVERGENCE - unmatched opener consumes following backticks as content`() = runTest {
        // given
        val textFlow = "`foo``bar``".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        // Streaming divergence: the opening single `` ` `` commits to a code
        // span at `f`; run-length matching keeps the inner `` `` `` as content
        // (since 2 ≠ 1), and with no isolated `` ` `` later, the span
        // force-closes at paragraph end with all remaining backticks as content.
        // GFM-correct output (`` `foo<code>bar</code> ``) requires deferring
        // open + abort-with-rescan, which loses mid-span streaming.
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "code" {
                    +"foo``bar``"
                }
            }
        }
        // GFM expected:
        /*
            <p>`foo<code>bar</code></p>
         */
    }

}
