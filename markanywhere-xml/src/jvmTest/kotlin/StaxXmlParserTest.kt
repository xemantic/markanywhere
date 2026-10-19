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

import com.xemantic.kotlin.test.assert
import com.xemantic.markanywhere.flow.mergeAdjacentText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import javax.xml.stream.XMLInputFactory
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the whole [XmlParserTest] conformance suite against the JDK's StAX
 * implementation.
 */
class StaxXmlParserTest : XmlParserTest() {

    override val parser: XmlParser = StaxXmlParser()

    @Test
    fun `should not starve sources read on the IO dispatcher`() = runTest(timeout = 30.seconds) {
        // given — more concurrent parses than Dispatchers.IO has threads, each
        // reading a source which itself needs an IO thread to go on
        val parses = 200
        val source = flow {
            emit("<a>")
            delay(200)
            emit("x</a>")
        }.flowOn(Dispatchers.IO)

        // when — a parked pull loop occupying an IO thread would leave no
        // thread for any source to resume on
        val results = withContext(Dispatchers.Default) {
            List(parses) {
                async { parser.parse(source).mergeAdjacentText().toList() }
            }.awaitAll()
        }

        // then
        assert(results.all { it.markNames() == listOf("a") && it.isBalanced() })
    }

    @Test
    fun `should default to the JDK StAX implementation`() {
        // when
        val factory = defaultXmlInputFactory()

        // then — not whichever provider happens to be on the classpath
        assert(factory.javaClass == XMLInputFactory.newDefaultFactory().javaClass)
    }

}
