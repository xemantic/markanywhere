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

package com.xemantic.markanywhere.flow

import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MergeAdjacentTextTest {

    @Test
    fun `should do nothing on empty flow`() = runTest {
        // given
        val flow = emptyFlow<SemanticEvent>()

        // when
        val transformed = flow.mergeAdjacentText()

        // then
        transformed sameAs emptyFlow()
    }

    @Test
    fun `should do nothing on non-fragmented text`() = runTest {
        // given
        val flow = semanticEvents {
            +"foo"
        }

        // when
        val transformed = flow.mergeAdjacentText()

        // then
        transformed sameAs semanticEvents {
            +"foo"
        }
    }

    @Test
    fun `should merge adjacent text fragments`() = runTest {
        // given
        val flow = semanticEvents {
            +"foo"
            +" "
            +"bar"
        }

        // when
        val transformed = flow.mergeAdjacentText()

        // then
        transformed sameAs semanticEvents {
            +"foo bar"
        }
    }

    @Test
    fun `should not merge text fragments separated by a mark`() = runTest {
        // given
        val flow = semanticEvents {
            +"foo"
            "em" {
                +"bar"
            }
            +"baz"
        }

        // when
        val transformed = flow.mergeAdjacentText()

        // then
        transformed sameAs semanticEvents {
            +"foo"
            "em" {
                +"bar"
            }
            +"baz"
        }
    }

    @Test
    fun `should merge fragments within a marked block`() = runTest {
        // given
        val flow = semanticEvents {
            "em" {
                +"foo"
                +" "
                +"bar"
            }
        }

        // when
        val transformed = flow.mergeAdjacentText()

        // then
        transformed sameAs semanticEvents {
            "em" {
                +"foo bar"
            }
        }
    }

    @Test
    fun `should merge fragments independently in two adjacent marked blocks`() = runTest {
        // given
        val flow = semanticEvents {
            "em" {
                +"foo"
                +" "
                +"bar"
            }
            "strong" {
                +"baz"
                +" "
                +"qux"
            }
        }

        // when
        val transformed = flow.mergeAdjacentText()

        // then
        transformed sameAs semanticEvents {
            "em" {
                +"foo bar"
            }
            "strong" {
                +"baz qux"
            }
        }
    }

    @Test
    fun `should merge fragments at every level of nested marks`() = runTest {
        // given
        val flow = semanticEvents {
            +"a"
            +"b"
            "em" {
                +"c"
                +"d"
                "strong" {
                    +"e"
                    +"f"
                }
                +"g"
                +"h"
            }
            +"i"
            +"j"
        }

        // when
        val transformed = flow.mergeAdjacentText()

        // then
        transformed sameAs semanticEvents {
            +"ab"
            "em" {
                +"cd"
                "strong" {
                    +"ef"
                }
                +"gh"
            }
            +"ij"
        }
    }

    @Test
    fun `should preserve marks with no surrounding text`() = runTest {
        // given
        val flow = semanticEvents {
            "em" {
                "strong" {
                    +"foo"
                }
            }
        }

        // when
        val transformed = flow.mergeAdjacentText()

        // then
        transformed sameAs semanticEvents {
            "em" {
                "strong" {
                    +"foo"
                }
            }
        }
    }

    @Test
    fun `should drop empty text fragments`() = runTest {
        // given
        val flow = semanticEvents {
            +""
            +"foo"
            +""
            +"bar"
            +""
        }

        // when
        val transformed = flow.mergeAdjacentText()

        // then
        transformed sameAs semanticEvents {
            +"foobar"
        }
    }

    @Test
    fun `should emit buffered text before a trailing mark`() = runTest {
        // given
        val flow = semanticEvents {
            +"foo"
            +"bar"
            "em" {
                +"baz"
            }
        }

        // when
        val transformed = flow.mergeAdjacentText()

        // then
        transformed sameAs semanticEvents {
            +"foobar"
            "em" {
                +"baz"
            }
        }
    }

    @Test
    fun `should emit buffered text at end of flow after a mark`() = runTest {
        // given
        val flow = semanticEvents {
            "em" {
                +"foo"
            }
            +"bar"
            +"baz"
        }

        // when
        val transformed = flow.mergeAdjacentText()

        // then
        transformed sameAs semanticEvents {
            "em" {
                +"foo"
            }
            +"barbaz"
        }
    }

}
