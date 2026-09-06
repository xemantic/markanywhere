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

package com.xemantic.markanywhere.xml

import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * The engine-independent half of the module: the two conveniences which route
 * source into an [XmlParser]. Asserted against a parser which merely echoes the
 * chunks it was handed, so that what reaches the engine is visible in the
 * resulting events.
 */
class XmlParserExtensionsTest {

    private val echo = object : XmlParser {
        override fun parse(
            chunks: Flow<String>
        ): Flow<SemanticEvent> = chunks.map {
            chunk -> SemanticEvent.Text(chunk)
        }
    }

    @Test
    fun `should parse a document held in memory as a single chunk`() = runTest {
        // when
        val events = echo.parse("<a>text</a>")

        // then
        events sameAs semanticEvents {
            +"<a>text</a>"
        }
    }

    @Test
    fun `should pass a flow of source chunks through to the parser`() = runTest {
        // when
        val events = flowOf("<a>", "text", "</a>").parseXml(echo)

        // then — chunk boundaries reach the engine untouched
        events sameAs semanticEvents {
            +"<a>"
            +"text"
            +"</a>"
        }
    }

}
