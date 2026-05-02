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
 * Tests for GFM Section 06.04 — Emphasis and strong emphasis.
 *
 * Each test corresponds to a numbered example from:
 * https://github.github.com/gfm/#emphasis-and-strong-emphasis
 */
@Suppress("ClassName")
class Gfm_06_04_Test {

    // TODO review
    @Test
    fun `example 360 - paragraph foo bar`() = runTest {
        // given
        val textFlow = "*foo bar*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo bar"
                }
            }
        }
        // GFM expected:
        /*
            <p><em>foo bar</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 361 - paragraph a foo bar`() = runTest {
        // given
        val textFlow = "a * foo bar*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"a * foo bar*"
            }
        }
        // GFM expected:
        /*
            <p>a * foo bar*</p>
         */
    }

    // TODO review
    @Test
    fun `example 362 - paragraph afoo`() = runTest {
        // given
        val textFlow = """a*"foo"*""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"a*\"foo\"*"
            }
        }
        // GFM expected:
        /*
            <p>a*&quot;foo&quot;*</p>
         */
    }

    // TODO review
    @Test
    fun `example 363 - paragraph a`() = runTest {
        // given
        val textFlow = "* a *".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"* a *"
            }
        }
        // GFM expected:
        /*
            <p>* a *</p>
         */
    }

    // TODO review
    @Test
    fun `example 364 - paragraph foobar`() = runTest {
        // given
        val textFlow = "foo*bar*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo"
                "em" {
                    +"bar"
                }
            }
        }
        // GFM expected:
        /*
            <p>foo<em>bar</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 365 - paragraph 5678`() = runTest {
        // given
        val textFlow = "5*6*78".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"5"
                "em" {
                    +"6"
                }
                +"78"
            }
        }
        // GFM expected:
        /*
            <p>5<em>6</em>78</p>
         */
    }

    // TODO review
    @Test
    fun `example 366 - paragraph foo bar`() = runTest {
        // given
        val textFlow = "_foo bar_".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo bar"
                }
            }
        }
        // GFM expected:
        /*
            <p><em>foo bar</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 367 - paragraph _ foo bar_`() = runTest {
        // given
        val textFlow = "_ foo bar_".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"_ foo bar_"
            }
        }
        // GFM expected:
        /*
            <p>_ foo bar_</p>
         */
    }

    // TODO review
    @Test
    fun `example 368 - paragraph a_foo_`() = runTest {
        // given
        val textFlow = """a_"foo"_""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"a_\"foo\"_"
            }
        }
        // GFM expected:
        /*
            <p>a_&quot;foo&quot;_</p>
         */
    }

    // TODO review
    @Test
    fun `example 369 - paragraph foo_bar_`() = runTest {
        // given
        val textFlow = "foo_bar_".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo_bar_"
            }
        }
        // GFM expected:
        /*
            <p>foo_bar_</p>
         */
    }

    // TODO review
    @Test
    fun `example 370 - paragraph 5_6_78`() = runTest {
        // given
        val textFlow = "5_6_78".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"5_6_78"
            }
        }
        // GFM expected:
        /*
            <p>5_6_78</p>
         */
    }

    // TODO review
    @Test
    fun `example 371 - paragraph пристаням_стремятся_`() = runTest {
        // given
        val textFlow = "пристаням_стремятся_".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"пристаням_стремятся_"
            }
        }
        // GFM expected:
        /*
            <p>пристаням_стремятся_</p>
         */
    }

    // TODO review
    @Test
    fun `example 372 - paragraph aa_bb_cc`() = runTest {
        // given
        val textFlow = """aa_"bb"_cc""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"aa_\"bb\"_cc"
            }
        }
        // GFM expected:
        /*
            <p>aa_&quot;bb&quot;_cc</p>
         */
    }

    // TODO review
    @Test
    fun `example 373 - paragraph foo-(bar)`() = runTest {
        // given
        val textFlow = "foo-_(bar)_".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo-"
                "em" {
                    +"(bar)"
                }
            }
        }
        // GFM expected:
        /*
            <p>foo-<em>(bar)</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 374 - paragraph _foo`() = runTest {
        // given
        val textFlow = "_foo*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"_foo*"
            }
        }
        // GFM expected:
        /*
            <p>_foo*</p>
         */
    }

    // TODO review
    @Test
    fun `example 375 - paragraph foo bar`() = runTest {
        // given
        val textFlow = "*foo bar *".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"*foo bar *"
            }
        }
        // GFM expected:
        /*
            <p>*foo bar *</p>
         */
    }

    // TODO review
    @Test
    fun `example 376 - paragraph foo bar`() = runTest {
        // given
        val textFlow = """
            *foo bar
            *
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"*foo bar\n*"
            }
        }
        // GFM expected:
        /*
            <p>*foo bar
            *</p>
         */
    }

    // TODO review
    @Test
    fun `example 377 - paragraph (foo)`() = runTest {
        // given
        val textFlow = "*(*foo)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"*(*foo)"
            }
        }
        // GFM expected:
        /*
            <p>*(*foo)</p>
         */
    }

    // TODO review
    @Test
    fun `example 378 - paragraph (foo)`() = runTest {
        // given
        val textFlow = "*(*foo*)*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"("
                    "em" {
                        +"foo"
                    }
                    +")"
                }
            }
        }
        // GFM expected:
        /*
            <p><em>(<em>foo</em>)</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 379 - paragraph foobar`() = runTest {
        // given
        val textFlow = "*foo*bar".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo"
                }
                +"bar"
            }
        }
        // GFM expected:
        /*
            <p><em>foo</em>bar</p>
         */
    }

    // TODO review
    @Test
    fun `example 380 - paragraph _foo bar _`() = runTest {
        // given
        val textFlow = "_foo bar _".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"_foo bar _"
            }
        }
        // GFM expected:
        /*
            <p>_foo bar _</p>
         */
    }

    // TODO review
    @Test
    fun `example 381 - paragraph _(_foo)`() = runTest {
        // given
        val textFlow = "_(_foo)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"_(_foo)"
            }
        }
        // GFM expected:
        /*
            <p>_(_foo)</p>
         */
    }

    // TODO review
    @Test
    fun `example 382 - paragraph (foo)`() = runTest {
        // given
        val textFlow = "_(_foo_)_".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"("
                    "em" {
                        +"foo"
                    }
                    +")"
                }
            }
        }
        // GFM expected:
        /*
            <p><em>(<em>foo</em>)</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 383 - paragraph _foo_bar`() = runTest {
        // given
        val textFlow = "_foo_bar".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"_foo_bar"
            }
        }
        // GFM expected:
        /*
            <p>_foo_bar</p>
         */
    }

    // TODO review
    @Test
    fun `example 384 - paragraph _пристаням_стремятся`() = runTest {
        // given
        val textFlow = "_пристаням_стремятся".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"_пристаням_стремятся"
            }
        }
        // GFM expected:
        /*
            <p>_пристаням_стремятся</p>
         */
    }

    // TODO review
    @Test
    fun `example 385 - paragraph foo_bar_baz`() = runTest {
        // given
        val textFlow = "_foo_bar_baz_".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo_bar_baz"
                }
            }
        }
        // GFM expected:
        /*
            <p><em>foo_bar_baz</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 386 - paragraph (bar)`() = runTest {
        // given
        val textFlow = "_(bar)_.".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"(bar)"
                }
                +"."
            }
        }
        // GFM expected:
        /*
            <p><em>(bar)</em>.</p>
         */
    }

    // TODO review
    @Test
    fun `example 387 - paragraph foo bar`() = runTest {
        // given
        val textFlow = "**foo bar**".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    +"foo bar"
                }
            }
        }
        // GFM expected:
        /*
            <p><strong>foo bar</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 388 - paragraph foo bar`() = runTest {
        // given
        val textFlow = "** foo bar**".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"** foo bar**"
            }
        }
        // GFM expected:
        /*
            <p>** foo bar**</p>
         */
    }

    // TODO review
    @Test
    fun `example 389 - paragraph afoo`() = runTest {
        // given
        val textFlow = """a**"foo"**""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"a**\"foo\"**"
            }
        }
        // GFM expected:
        /*
            <p>a**&quot;foo&quot;**</p>
         */
    }

    // TODO review
    @Test
    fun `example 390 - paragraph foobar`() = runTest {
        // given
        val textFlow = "foo**bar**".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo"
                "strong" {
                    +"bar"
                }
            }
        }
        // GFM expected:
        /*
            <p>foo<strong>bar</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 391 - paragraph foo bar`() = runTest {
        // given
        val textFlow = "__foo bar__".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    +"foo bar"
                }
            }
        }
        // GFM expected:
        /*
            <p><strong>foo bar</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 392 - paragraph __ foo bar__`() = runTest {
        // given
        val textFlow = "__ foo bar__".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"__ foo bar__"
            }
        }
        // GFM expected:
        /*
            <p>__ foo bar__</p>
         */
    }

    // TODO review
    @Test
    fun `example 393 - paragraph __ foo bar__`() = runTest {
        // given
        val textFlow = """
            __
            foo bar__
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"__\nfoo bar__"
            }
        }
        // GFM expected:
        /*
            <p>__
            foo bar__</p>
         */
    }

    // TODO review
    @Test
    fun `example 394 - paragraph a__foo__`() = runTest {
        // given
        val textFlow = """a__"foo"__""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"a__\"foo\"__"
            }
        }
        // GFM expected:
        /*
            <p>a__&quot;foo&quot;__</p>
         */
    }

    // TODO review
    @Test
    fun `example 395 - paragraph foo__bar__`() = runTest {
        // given
        val textFlow = "foo__bar__".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo__bar__"
            }
        }
        // GFM expected:
        /*
            <p>foo__bar__</p>
         */
    }

    // TODO review
    @Test
    fun `example 396 - paragraph 5__6__78`() = runTest {
        // given
        val textFlow = "5__6__78".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"5__6__78"
            }
        }
        // GFM expected:
        /*
            <p>5__6__78</p>
         */
    }

    // TODO review
    @Test
    fun `example 397 - paragraph пристаням__стремятся__`() = runTest {
        // given
        val textFlow = "пристаням__стремятся__".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"пристаням__стремятся__"
            }
        }
        // GFM expected:
        /*
            <p>пристаням__стремятся__</p>
         */
    }

    // TODO review
    @Test
    fun `example 398 - paragraph foo, bar, baz`() = runTest {
        // given
        val textFlow = "__foo, __bar__, baz__".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    +"foo, "
                    "strong" {
                        +"bar"
                    }
                    +", baz"
                }
            }
        }
        // GFM expected:
        /*
            <p><strong>foo, <strong>bar</strong>, baz</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 399 - paragraph foo-(bar)`() = runTest {
        // given
        val textFlow = "foo-__(bar)__".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo-"
                "strong" {
                    +"(bar)"
                }
            }
        }
        // GFM expected:
        /*
            <p>foo-<strong>(bar)</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 400 - paragraph foo bar`() = runTest {
        // given
        val textFlow = "**foo bar **".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"**foo bar **"
            }
        }
        // GFM expected:
        /*
            <p>**foo bar **</p>
         */
    }

    // TODO review
    @Test
    fun `example 401 - paragraph (foo)`() = runTest {
        // given
        val textFlow = "**(**foo)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"**(**foo)"
            }
        }
        // GFM expected:
        /*
            <p>**(**foo)</p>
         */
    }

    // TODO review
    @Test
    fun `example 402 - paragraph (foo)`() = runTest {
        // given
        val textFlow = "*(**foo**)*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"("
                    "strong" {
                        +"foo"
                    }
                    +")"
                }
            }
        }
        // GFM expected:
        /*
            <p><em>(<strong>foo</strong>)</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 403 - paragraph Gomphocarpus (Gomphocar`() = runTest {
        // given
        val textFlow = """
            **Gomphocarpus (*Gomphocarpus physocarpus*, syn.
            *Asclepias physocarpa*)**
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    +"Gomphocarpus ("
                    "em" {
                        +"Gomphocarpus physocarpus"
                    }
                    +", syn.\n"
                    "em" {
                        +"Asclepias physocarpa"
                    }
                    +")"
                }
            }
        }
        // GFM expected:
        /*
            <p><strong>Gomphocarpus (<em>Gomphocarpus physocarpus</em>, syn.
            <em>Asclepias physocarpa</em>)</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 404 - paragraph foo bar foo`() = runTest {
        // given
        val textFlow = """**foo "*bar*" foo**""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    +"foo \""
                    "em" {
                        +"bar"
                    }
                    +"\" foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><strong>foo &quot;<em>bar</em>&quot; foo</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 405 - paragraph foobar`() = runTest {
        // given
        val textFlow = "**foo**bar".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    +"foo"
                }
                +"bar"
            }
        }
        // GFM expected:
        /*
            <p><strong>foo</strong>bar</p>
         */
    }

    // TODO review
    @Test
    fun `example 406 - paragraph __foo bar __`() = runTest {
        // given
        val textFlow = "__foo bar __".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"__foo bar __"
            }
        }
        // GFM expected:
        /*
            <p>__foo bar __</p>
         */
    }

    // TODO review
    @Test
    fun `example 407 - paragraph __(__foo)`() = runTest {
        // given
        val textFlow = "__(__foo)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"__(__foo)"
            }
        }
        // GFM expected:
        /*
            <p>__(__foo)</p>
         */
    }

    // TODO review
    @Test
    fun `example 408 - paragraph (foo)`() = runTest {
        // given
        val textFlow = "_(__foo__)_".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"("
                    "strong" {
                        +"foo"
                    }
                    +")"
                }
            }
        }
        // GFM expected:
        /*
            <p><em>(<strong>foo</strong>)</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 409 - paragraph __foo__bar`() = runTest {
        // given
        val textFlow = "__foo__bar".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"__foo__bar"
            }
        }
        // GFM expected:
        /*
            <p>__foo__bar</p>
         */
    }

    // TODO review
    @Test
    fun `example 410 - paragraph __пристаням__стремятся`() = runTest {
        // given
        val textFlow = "__пристаням__стремятся".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"__пристаням__стремятся"
            }
        }
        // GFM expected:
        /*
            <p>__пристаням__стремятся</p>
         */
    }

    // TODO review
    @Test
    fun `example 411 - paragraph foo__bar__baz`() = runTest {
        // given
        val textFlow = "__foo__bar__baz__".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    +"foo__bar__baz"
                }
            }
        }
        // GFM expected:
        /*
            <p><strong>foo__bar__baz</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 412 - paragraph (bar)`() = runTest {
        // given
        val textFlow = "__(bar)__.".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    +"(bar)"
                }
                +"."
            }
        }
        // GFM expected:
        /*
            <p><strong>(bar)</strong>.</p>
         */
    }

    // TODO review
    @Test
    fun `example 413 - paragraph foo bar`() = runTest {
        // given
        val textFlow = "*foo [bar](/url)*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo "
                    "a"("href" to "/url") {
                        +"bar"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p><em>foo <a href="/url">bar</a></em></p>
         */
    }

    // TODO review
    @Test
    fun `example 414 - paragraph foo bar`() = runTest {
        // given
        val textFlow = """
            *foo
            bar*
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo\nbar"
                }
            }
        }
        // GFM expected:
        /*
            <p><em>foo
            bar</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 415 - paragraph foo bar baz`() = runTest {
        // given
        val textFlow = "_foo __bar__ baz_".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo "
                    "strong" {
                        +"bar"
                    }
                    +" baz"
                }
            }
        }
        // GFM expected:
        /*
            <p><em>foo <strong>bar</strong> baz</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 416 - paragraph foo bar baz`() = runTest {
        // given
        val textFlow = "_foo _bar_ baz_".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo "
                    "em" {
                        +"bar"
                    }
                    +" baz"
                }
            }
        }
        // GFM expected:
        /*
            <p><em>foo <em>bar</em> baz</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 417 - paragraph foo bar`() = runTest {
        // given
        val textFlow = "__foo_ bar_".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    "em" {
                        +"foo"
                    }
                    +" bar"
                }
            }
        }
        // GFM expected:
        /*
            <p><em><em>foo</em> bar</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 418 - paragraph foo bar`() = runTest {
        // given
        val textFlow = "*foo *bar**".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo "
                    "em" {
                        +"bar"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p><em>foo <em>bar</em></em></p>
         */
    }

    // TODO review
    @Test
    fun `example 419 - paragraph foo bar baz`() = runTest {
        // given
        val textFlow = "*foo **bar** baz*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo "
                    "strong" {
                        +"bar"
                    }
                    +" baz"
                }
            }
        }
        // GFM expected:
        /*
            <p><em>foo <strong>bar</strong> baz</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 420 - paragraph foobarbaz`() = runTest {
        // given
        val textFlow = "*foo**bar**baz*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo"
                    "strong" {
                        +"bar"
                    }
                    +"baz"
                }
            }
        }
        // GFM expected:
        /*
            <p><em>foo<strong>bar</strong>baz</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 421 - paragraph foobar`() = runTest {
        // given
        val textFlow = "*foo**bar*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo**bar"
                }
            }
        }
        // GFM expected:
        /*
            <p><em>foo**bar</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 422 - paragraph foo bar`() = runTest {
        // given
        val textFlow = "***foo** bar*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    "strong" {
                        +"foo"
                    }
                    +" bar"
                }
            }
        }
        // GFM expected:
        /*
            <p><em><strong>foo</strong> bar</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 423 - paragraph foo bar`() = runTest {
        // given
        val textFlow = "*foo **bar***".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo "
                    "strong" {
                        +"bar"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p><em>foo <strong>bar</strong></em></p>
         */
    }

    // TODO review
    @Test
    fun `example 424 - paragraph foobar`() = runTest {
        // given
        val textFlow = "*foo**bar***".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo"
                    "strong" {
                        +"bar"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p><em>foo<strong>bar</strong></em></p>
         */
    }

    // TODO review
    @Test
    fun `example 425 - paragraph foobarbaz`() = runTest {
        // given
        val textFlow = "foo***bar***baz".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo"
                "em" {
                    "strong" {
                        +"bar"
                    }
                }
                +"baz"
            }
        }
        // GFM expected:
        /*
            <p>foo<em><strong>bar</strong></em>baz</p>
         */
    }

    // TODO review
    @Test
    fun `example 426 - paragraph foobarbaz`() = runTest {
        // given
        val textFlow = "foo******bar*********baz".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo"
                "strong" {
                    "strong" {
                        "strong" {
                            +"bar"
                        }
                    }
                }
                +"***baz"
            }
        }
        // GFM expected:
        /*
            <p>foo<strong><strong><strong>bar</strong></strong></strong>***baz</p>
         */
    }

    // TODO review
    @Test
    fun `example 427 - paragraph foo bar baz bim bop`() = runTest {
        // given
        val textFlow = "*foo **bar *baz* bim** bop*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo "
                    "strong" {
                        +"bar "
                        "em" {
                            +"baz"
                        }
                        +" bim"
                    }
                    +" bop"
                }
            }
        }
        // GFM expected:
        /*
            <p><em>foo <strong>bar <em>baz</em> bim</strong> bop</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 428 - paragraph foo bar`() = runTest {
        // given
        val textFlow = "*foo [*bar*](/url)*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo "
                    "a"("href" to "/url") {
                        "em" {
                            +"bar"
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p><em>foo <a href="/url"><em>bar</em></a></em></p>
         */
    }

    // TODO review
    @Test
    fun `example 429 - paragraph is not an empty emph`() = runTest {
        // given
        val textFlow = "** is not an empty emphasis".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"** is not an empty emphasis"
            }
        }
        // GFM expected:
        /*
            <p>** is not an empty emphasis</p>
         */
    }

    // TODO review
    @Test
    fun `example 430 - paragraph is not an empty st`() = runTest {
        // given
        val textFlow = "**** is not an empty strong emphasis".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"**** is not an empty strong emphasis"
            }
        }
        // GFM expected:
        /*
            <p>**** is not an empty strong emphasis</p>
         */
    }

    // TODO review
    @Test
    fun `example 431 - paragraph foo bar`() = runTest {
        // given
        val textFlow = "**foo [bar](/url)**".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    +"foo "
                    "a"("href" to "/url") {
                        +"bar"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p><strong>foo <a href="/url">bar</a></strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 432 - paragraph foo bar`() = runTest {
        // given
        val textFlow = """
            **foo
            bar**
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    +"foo\nbar"
                }
            }
        }
        // GFM expected:
        /*
            <p><strong>foo
            bar</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 433 - paragraph foo bar baz`() = runTest {
        // given
        val textFlow = "__foo _bar_ baz__".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    +"foo "
                    "em" {
                        +"bar"
                    }
                    +" baz"
                }
            }
        }
        // GFM expected:
        /*
            <p><strong>foo <em>bar</em> baz</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 434 - paragraph foo bar baz`() = runTest {
        // given
        val textFlow = "__foo __bar__ baz__".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    +"foo "
                    "strong" {
                        +"bar"
                    }
                    +" baz"
                }
            }
        }
        // GFM expected:
        /*
            <p><strong>foo <strong>bar</strong> baz</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 435 - paragraph foo bar`() = runTest {
        // given
        val textFlow = "____foo__ bar__".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    "strong" {
                        +"foo"
                    }
                    +" bar"
                }
            }
        }
        // GFM expected:
        /*
            <p><strong><strong>foo</strong> bar</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 436 - paragraph foo bar`() = runTest {
        // given
        val textFlow = "**foo **bar****".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    +"foo "
                    "strong" {
                        +"bar"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p><strong>foo <strong>bar</strong></strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 437 - paragraph foo bar baz`() = runTest {
        // given
        val textFlow = "**foo *bar* baz**".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    +"foo "
                    "em" {
                        +"bar"
                    }
                    +" baz"
                }
            }
        }
        // GFM expected:
        /*
            <p><strong>foo <em>bar</em> baz</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 438 - paragraph foobarbaz`() = runTest {
        // given
        val textFlow = "**foo*bar*baz**".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    +"foo"
                    "em" {
                        +"bar"
                    }
                    +"baz"
                }
            }
        }
        // GFM expected:
        /*
            <p><strong>foo<em>bar</em>baz</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 439 - paragraph foo bar`() = runTest {
        // given
        val textFlow = "***foo* bar**".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    "em" {
                        +"foo"
                    }
                    +" bar"
                }
            }
        }
        // GFM expected:
        /*
            <p><strong><em>foo</em> bar</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 440 - paragraph foo bar`() = runTest {
        // given
        val textFlow = "**foo *bar***".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    +"foo "
                    "em" {
                        +"bar"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p><strong>foo <em>bar</em></strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 441 - paragraph foo bar baz bim bop`() = runTest {
        // given
        val textFlow = """
            **foo *bar **baz**
            bim* bop**
        """.trimIndent().chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    +"foo "
                    "em" {
                        +"bar "
                        "strong" {
                            +"baz"
                        }
                        +"\nbim"
                    }
                    +" bop"
                }
            }
        }
        // GFM expected:
        /*
            <p><strong>foo <em>bar <strong>baz</strong>
            bim</em> bop</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 442 - paragraph foo bar`() = runTest {
        // given
        val textFlow = "**foo [*bar*](/url)**".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    +"foo "
                    "a"("href" to "/url") {
                        "em" {
                            +"bar"
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p><strong>foo <a href="/url"><em>bar</em></a></strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 443 - paragraph __ is not an empty emph`() = runTest {
        // given
        val textFlow = "__ is not an empty emphasis".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"__ is not an empty emphasis"
            }
        }
        // GFM expected:
        /*
            <p>__ is not an empty emphasis</p>
         */
    }

    // TODO review
    @Test
    fun `example 444 - paragraph ____ is not an empty st`() = runTest {
        // given
        val textFlow = "____ is not an empty strong emphasis".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"____ is not an empty strong emphasis"
            }
        }
        // GFM expected:
        /*
            <p>____ is not an empty strong emphasis</p>
         */
    }

    // TODO review
    @Test
    fun `example 445 - paragraph foo`() = runTest {
        // given
        val textFlow = "foo ***".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo ***"
            }
        }
        // GFM expected:
        /*
            <p>foo ***</p>
         */
    }

    // TODO review
    @Test
    fun `example 446 - paragraph foo`() = runTest {
        // given
        val textFlow = buildText {
            +"foo *\\**\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo "
                "em" {
                    +"*"
                }
            }
        }
        // GFM expected:
        /*
            <p>foo <em>*</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 447 - paragraph foo _`() = runTest {
        // given
        val textFlow = "foo *_*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo "
                "em" {
                    +"_"
                }
            }
        }
        // GFM expected:
        /*
            <p>foo <em>_</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 448 - paragraph foo`() = runTest {
        // given
        val textFlow = "foo *****".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo *****"
            }
        }
        // GFM expected:
        /*
            <p>foo *****</p>
         */
    }

    // TODO review
    @Test
    fun `example 449 - paragraph foo`() = runTest {
        // given
        val textFlow = buildText {
            +"foo **\\***\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo "
                "strong" {
                    +"*"
                }
            }
        }
        // GFM expected:
        /*
            <p>foo <strong>*</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 450 - paragraph foo _`() = runTest {
        // given
        val textFlow = "foo **_**".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo "
                "strong" {
                    +"_"
                }
            }
        }
        // GFM expected:
        /*
            <p>foo <strong>_</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 451 - paragraph foo`() = runTest {
        // given
        val textFlow = "**foo*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"*"
                "em" {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p>*<em>foo</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 452 - paragraph foo`() = runTest {
        // given
        val textFlow = "*foo**".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo"
                }
                +"*"
            }
        }
        // GFM expected:
        /*
            <p><em>foo</em>*</p>
         */
    }

    // TODO review
    @Test
    fun `example 453 - paragraph foo`() = runTest {
        // given
        val textFlow = "***foo**".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"*"
                "strong" {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p>*<strong>foo</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 454 - paragraph foo`() = runTest {
        // given
        val textFlow = "****foo*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"***"
                "em" {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p>***<em>foo</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 455 - paragraph foo`() = runTest {
        // given
        val textFlow = "**foo***".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    +"foo"
                }
                +"*"
            }
        }
        // GFM expected:
        /*
            <p><strong>foo</strong>*</p>
         */
    }

    // TODO review
    @Test
    fun `example 456 - paragraph foo`() = runTest {
        // given
        val textFlow = "*foo****".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo"
                }
                +"***"
            }
        }
        // GFM expected:
        /*
            <p><em>foo</em>***</p>
         */
    }

    // TODO review
    @Test
    fun `example 457 - paragraph foo ___`() = runTest {
        // given
        val textFlow = "foo ___".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo ___"
            }
        }
        // GFM expected:
        /*
            <p>foo ___</p>
         */
    }

    // TODO review
    @Test
    fun `example 458 - paragraph foo _`() = runTest {
        // given
        val textFlow = buildText {
            +"foo _\\__\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo "
                "em" {
                    +"_"
                }
            }
        }
        // GFM expected:
        /*
            <p>foo <em>_</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 459 - paragraph foo`() = runTest {
        // given
        val textFlow = "foo _*_".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo "
                "em" {
                    +"*"
                }
            }
        }
        // GFM expected:
        /*
            <p>foo <em>*</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 460 - paragraph foo _____`() = runTest {
        // given
        val textFlow = "foo _____".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo _____"
            }
        }
        // GFM expected:
        /*
            <p>foo _____</p>
         */
    }

    // TODO review
    @Test
    fun `example 461 - paragraph foo _`() = runTest {
        // given
        val textFlow = buildText {
            +"foo __\\___\n"
        }.chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo "
                "strong" {
                    +"_"
                }
            }
        }
        // GFM expected:
        /*
            <p>foo <strong>_</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 462 - paragraph foo`() = runTest {
        // given
        val textFlow = "foo __*__".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"foo "
                "strong" {
                    +"*"
                }
            }
        }
        // GFM expected:
        /*
            <p>foo <strong>*</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 463 - paragraph _foo`() = runTest {
        // given
        val textFlow = "__foo_".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"_"
                "em" {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p>_<em>foo</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 464 - paragraph foo_`() = runTest {
        // given
        val textFlow = "_foo__".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo"
                }
                +"_"
            }
        }
        // GFM expected:
        /*
            <p><em>foo</em>_</p>
         */
    }

    // TODO review
    @Test
    fun `example 465 - paragraph _foo`() = runTest {
        // given
        val textFlow = "___foo__".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"_"
                "strong" {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p>_<strong>foo</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 466 - paragraph ___foo`() = runTest {
        // given
        val textFlow = "____foo_".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"___"
                "em" {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p>___<em>foo</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 467 - paragraph foo_`() = runTest {
        // given
        val textFlow = "__foo___".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    +"foo"
                }
                +"_"
            }
        }
        // GFM expected:
        /*
            <p><strong>foo</strong>_</p>
         */
    }

    // TODO review
    @Test
    fun `example 468 - paragraph foo___`() = runTest {
        // given
        val textFlow = "_foo____".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo"
                }
                +"___"
            }
        }
        // GFM expected:
        /*
            <p><em>foo</em>___</p>
         */
    }

    // TODO review
    @Test
    fun `example 469 - paragraph foo`() = runTest {
        // given
        val textFlow = "**foo**".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><strong>foo</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 470 - paragraph foo`() = runTest {
        // given
        val textFlow = "*_foo_*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    "em" {
                        +"foo"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p><em><em>foo</em></em></p>
         */
    }

    // TODO review
    @Test
    fun `example 471 - paragraph foo`() = runTest {
        // given
        val textFlow = "__foo__".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    +"foo"
                }
            }
        }
        // GFM expected:
        /*
            <p><strong>foo</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 472 - paragraph foo`() = runTest {
        // given
        val textFlow = "_*foo*_".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    "em" {
                        +"foo"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p><em><em>foo</em></em></p>
         */
    }

    // TODO review
    @Test
    fun `example 473 - paragraph foo`() = runTest {
        // given
        val textFlow = "****foo****".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    "strong" {
                        +"foo"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p><strong><strong>foo</strong></strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 474 - paragraph foo`() = runTest {
        // given
        val textFlow = "____foo____".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    "strong" {
                        +"foo"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p><strong><strong>foo</strong></strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 475 - paragraph foo`() = runTest {
        // given
        val textFlow = "******foo******".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "strong" {
                    "strong" {
                        "strong" {
                            +"foo"
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p><strong><strong><strong>foo</strong></strong></strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 476 - paragraph foo`() = runTest {
        // given
        val textFlow = "***foo***".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    "strong" {
                        +"foo"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p><em><strong>foo</strong></em></p>
         */
    }

    // TODO review
    @Test
    fun `example 477 - paragraph foo`() = runTest {
        // given
        val textFlow = "_____foo_____".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    "strong" {
                        "strong" {
                            +"foo"
                        }
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p><em><strong><strong>foo</strong></strong></em></p>
         */
    }

    // TODO review
    @Test
    fun `example 478 - paragraph foo _bar baz_`() = runTest {
        // given
        val textFlow = "*foo _bar* baz_".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo _bar"
                }
                +" baz_"
            }
        }
        // GFM expected:
        /*
            <p><em>foo _bar</em> baz_</p>
         */
    }

    // TODO review
    @Test
    fun `example 479 - paragraph foo bar baz bim bam`() = runTest {
        // given
        val textFlow = "*foo __bar *baz bim__ bam*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"foo "
                    "strong" {
                        +"bar *baz bim"
                    }
                    +" bam"
                }
            }
        }
        // GFM expected:
        /*
            <p><em>foo <strong>bar *baz bim</strong> bam</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 480 - paragraph foo bar baz`() = runTest {
        // given
        val textFlow = "**foo **bar baz**".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"**foo "
                "strong" {
                    +"bar baz"
                }
            }
        }
        // GFM expected:
        /*
            <p>**foo <strong>bar baz</strong></p>
         */
    }

    // TODO review
    @Test
    fun `example 481 - paragraph foo bar baz`() = runTest {
        // given
        val textFlow = "*foo *bar baz*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"*foo "
                "em" {
                    +"bar baz"
                }
            }
        }
        // GFM expected:
        /*
            <p>*foo <em>bar baz</em></p>
         */
    }

    // TODO review
    @Test
    fun `example 482 - paragraph bar`() = runTest {
        // given
        val textFlow = "*[bar*](/url)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"*"
                "a"("href" to "/url") {
                    +"bar*"
                }
            }
        }
        // GFM expected:
        /*
            <p>*<a href="/url">bar*</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 483 - paragraph _foo bar_`() = runTest {
        // given
        val textFlow = "_foo [bar_](/url)".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"_foo "
                "a"("href" to "/url") {
                    +"bar_"
                }
            }
        }
        // GFM expected:
        /*
            <p>_foo <a href="/url">bar_</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 484 - paragraph`() = runTest {
        // given
        val textFlow = """*<img src="foo" title="*"/>""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"*"
                "img"("src" to "foo", "title" to "*") {}
            }
        }
        // GFM expected:
        /*
            <p>*<img src="foo" title="*"/></p>
         */
    }

    // TODO review
    @Test
    fun `example 485 - paragraph`() = runTest {
        // given
        val textFlow = """**<a href="**">""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"**"
                "a"("href" to "**") {
                }
            }
        }
        // GFM expected:
        /*
            <p>**<a href="**"></p>
         */
    }

    // TODO review
    @Test
    fun `example 486 - paragraph __`() = runTest {
        // given
        val textFlow = """__<a href="__">""".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"__"
                "a"("href" to "__") {
                }
            }
        }
        // GFM expected:
        /*
            <p>__<a href="__"></p>
         */
    }

    // TODO review
    @Test
    fun `example 487 - paragraph a`() = runTest {
        // given
        val textFlow = "*a `*`*".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"a "
                    "code" {
                        +"*"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p><em>a <code>*</code></em></p>
         */
    }

    // TODO review
    @Test
    fun `example 488 - paragraph a _`() = runTest {
        // given
        val textFlow = "_a `_`_".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                "em" {
                    +"a "
                    "code" {
                        +"_"
                    }
                }
            }
        }
        // GFM expected:
        /*
            <p><em>a <code>_</code></em></p>
         */
    }

    // TODO review
    @Test
    fun `example 489 - paragraph ahttpfoobarq=`() = runTest {
        // given
        val textFlow = "**a<http://foo.bar/?q=**>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"**a"
                "a"("href" to "http://foo.bar/?q=**") {
                    +"http://foo.bar/?q=**"
                }
            }
        }
        // GFM expected:
        /*
            <p>**a<a href="http://foo.bar/?q=**">http://foo.bar/?q=**</a></p>
         */
    }

    // TODO review
    @Test
    fun `example 490 - paragraph __ahttpfoobarq=__`() = runTest {
        // given
        val textFlow = "__a<http://foo.bar/?q=__>".chunkedRandomly().asFlow()

        // when
        val parsed = textFlow.parse()

        // then
        parsed.mergeAdjacentText() sameAs semanticEvents {
            "p" {
                +"__a"
                "a"("href" to "http://foo.bar/?q=__") {
                    +"http://foo.bar/?q=__"
                }
            }
        }
        // GFM expected:
        /*
            <p>__a<a href="http://foo.bar/?q=__">http://foo.bar/?q=__</a></p>
         */
    }

}
