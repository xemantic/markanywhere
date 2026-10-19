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
import com.xemantic.kotlin.test.have
import com.xemantic.kotlin.test.should
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
import java.io.InputStream
import java.io.Reader
import javax.xml.stream.EventFilter
import javax.xml.stream.StreamFilter
import javax.xml.stream.XMLEventReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLReporter
import javax.xml.stream.XMLResolver
import javax.xml.stream.XMLStreamReader
import javax.xml.stream.util.StreamReaderDelegate
import javax.xml.stream.util.XMLEventAllocator
import javax.xml.transform.Source
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
    fun `should not throw when the provider fails with an unchecked exception`() = runTest {
        // given — a provider failing with an exception which is no
        // XMLStreamException, nor caused by one, once the first element is read
        val failing = StaxXmlParser(
            factory = object : DelegatingXmlInputFactory() {
                override fun createXMLStreamReader(reader: Reader): XMLStreamReader =
                    object : StreamReaderDelegate(delegate.createXMLStreamReader(reader)) {
                        override fun next(): Int =
                            if (isStartElement) throw IllegalStateException("provider bug") else super.next()
                    }
            }
        )

        // when
        val events = failing.parse("<a><b/></a>").toList()

        // then
        events should {
            have(markNames() == listOf("a"))
            have(isBalanced())
        }
    }

    @Test
    fun `should default to the JDK StAX implementation`() {
        // when
        val factory = defaultXmlInputFactory()

        // then — not whichever provider happens to be on the classpath
        assert(factory.javaClass == XMLInputFactory.newDefaultFactory().javaClass)
    }

}

/**
 * An [XMLInputFactory] handing everything to the JDK's own, for a test to
 * override the one method it needs.
 */
private abstract class DelegatingXmlInputFactory : XMLInputFactory() {
    val delegate: XMLInputFactory = defaultXmlInputFactory()
    override fun createXMLStreamReader(reader: Reader): XMLStreamReader = delegate.createXMLStreamReader(reader)
    override fun createXMLStreamReader(source: Source): XMLStreamReader = delegate.createXMLStreamReader(source)
    override fun createXMLStreamReader(stream: InputStream): XMLStreamReader = delegate.createXMLStreamReader(stream)
    override fun createXMLStreamReader(stream: InputStream, encoding: String?): XMLStreamReader = delegate.createXMLStreamReader(stream, encoding)
    override fun createXMLStreamReader(systemId: String?, stream: InputStream): XMLStreamReader = delegate.createXMLStreamReader(systemId, stream)
    override fun createXMLStreamReader(systemId: String?, reader: Reader): XMLStreamReader = delegate.createXMLStreamReader(systemId, reader)
    override fun createXMLEventReader(reader: Reader): XMLEventReader = delegate.createXMLEventReader(reader)
    override fun createXMLEventReader(systemId: String?, reader: Reader): XMLEventReader = delegate.createXMLEventReader(systemId, reader)
    override fun createXMLEventReader(reader: XMLStreamReader): XMLEventReader = delegate.createXMLEventReader(reader)
    override fun createXMLEventReader(source: Source): XMLEventReader = delegate.createXMLEventReader(source)
    override fun createXMLEventReader(stream: InputStream): XMLEventReader = delegate.createXMLEventReader(stream)
    override fun createXMLEventReader(stream: InputStream, encoding: String?): XMLEventReader = delegate.createXMLEventReader(stream, encoding)
    override fun createXMLEventReader(systemId: String?, stream: InputStream): XMLEventReader = delegate.createXMLEventReader(systemId, stream)
    override fun createFilteredReader(reader: XMLStreamReader, filter: StreamFilter): XMLStreamReader = delegate.createFilteredReader(reader, filter)
    override fun createFilteredReader(reader: XMLEventReader, filter: EventFilter): XMLEventReader = delegate.createFilteredReader(reader, filter)
    override fun getXMLResolver(): XMLResolver? = delegate.xmlResolver
    override fun setXMLResolver(resolver: XMLResolver?) { delegate.xmlResolver = resolver }
    override fun getXMLReporter(): XMLReporter? = delegate.xmlReporter
    override fun setXMLReporter(reporter: XMLReporter?) { delegate.xmlReporter = reporter }
    override fun setProperty(name: String, value: Any?) = delegate.setProperty(name, value)
    override fun getProperty(name: String): Any? = delegate.getProperty(name)
    override fun isPropertySupported(name: String): Boolean = delegate.isPropertySupported(name)
    override fun setEventAllocator(allocator: XMLEventAllocator?) { delegate.eventAllocator = allocator }
    override fun getEventAllocator(): XMLEventAllocator? = delegate.eventAllocator
}
