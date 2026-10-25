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
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.flow.mergeAdjacentText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    fun `should report a tag as soon as it is read once the document has started`() = runTest(timeout = 30.seconds) {
        // given — the opening chunk is long enough for the reader's look-ahead
        // at the document start, after which each tag arrives on its own
        val chunks = flow {
            emit("<root>")
            emit("<a>")
            awaitCancellation()
        }

        // when — this can only complete if `a` was reported without more input
        val events = withContext(Dispatchers.Default) {
            parser.parse(chunks).take(2).toList()
        }

        // then
        assert(events.markNames() == listOf("root", "a"))
    }

    @Test
    fun `DIVERGENCE - should report nothing until the first five characters of the document arrive`() = runTest(timeout = 30.seconds) {
        // given — a document whose opening tag is shorter than the `<?xml` the
        // JDK reader looks ahead for at the document start (MarkanywhereXmlParser
        // reports `a` at once), followed by more input only once released
        val more = CompletableDeferred<Unit>()
        val chunks = flow {
            emit("<a>")
            more.await()
            emit("<b/>")
            awaitCancellation()
        }

        // when
        val (early, late) = parseLive(chunks) { events ->
            val early = withTimeoutOrNull(1.seconds) { events.receive() }
            more.complete(Unit)
            early to withTimeoutOrNull(10.seconds) { events.receive() }
        }

        // then — `a` was held by the look-ahead, not lost by a stuck parse
        assert(early == null)
        assert(late == SemanticEvent.Mark("a", isTagged = true))
    }

    @Test
    fun `should report text only once the markup after it begins`() = runTest(timeout = 30.seconds) {
        // given — text which may yet continue, followed by markup only once
        // released (MarkanywhereXmlParser reports it at the chunk end)
        val more = CompletableDeferred<Unit>()
        val chunks = flow {
            emit("<a>hello")
            more.await()
            emit("<b/>")
            awaitCancellation()
        }

        // when
        val (opening, early, late) = parseLive(chunks) { events ->
            val opening = withTimeoutOrNull(10.seconds) { events.receive() }
            val early = withTimeoutOrNull(1.seconds) { events.receive() }
            more.complete(Unit)
            Triple(opening, early, withTimeoutOrNull(10.seconds) { events.receive() })
        }

        // then
        assert(opening == SemanticEvent.Mark("a", isTagged = true))
        assert(early == null)
        assert(late == SemanticEvent.Text("hello"))
    }

    /**
     * Runs [block] against the events of parsing [chunks] as they arrive, on
     * real time rather than the test's virtual clock, the parse cancelled once
     * [block] returns.
     */
    private suspend fun <T> parseLive(
        chunks: Flow<String>,
        block: suspend (ReceiveChannel<SemanticEvent>) -> T
    ): T = withContext(Dispatchers.Default) {
        val events = Channel<SemanticEvent>(Channel.UNLIMITED)
        val parse = launch { parser.parse(chunks).collect { events.send(it) } }
        try {
            block(events)
        } finally {
            parse.cancel()
        }
    }

    @Test
    fun `should not hang when cancelled before its source is read`() = runTest(timeout = 30.seconds) {
        // given — a parse merged with a flow which emits first, so that the
        // parse is cancelled before the coroutine feeding its source ever runs
        val other = flowOf("other")

        // when
        val first = merge(parser.parse("<a>x</a>").map { "event" }, other).first()

        // then
        assert(first == "other")
    }

    @Test
    fun `should end the pull loop on cancellation while the source is suspended`() = runTest(timeout = 30.seconds) {
        // given — a source which does not respond to cancellation, and a
        // provider reporting when the pull loop closes its reader
        val started = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val reporting = StaxXmlParser(
            factory = {
                object : DelegatingXmlInputFactory() {
                    override fun createXMLStreamReader(reader: Reader): XMLStreamReader =
                        object : StreamReaderDelegate(delegate.createXMLStreamReader(reader)) {
                            override fun close() {
                                super.close()
                                closed.complete(Unit)
                            }
                        }
                }
            }
        )
        // long enough for the reader's look-ahead past `<?xml` to report `a`
        val source = flow {
            emit("<a><b>")
            withContext(NonCancellable) { gate.await() }
        }
        val parse = launch(Dispatchers.Default) {
            reporting.parse(source).collect { started.complete(Unit) }
        }
        val isClosed = try {
            started.await()

            // when
            parse.cancel()

            // then — the loop ends without waiting for the source to return
            withContext(Dispatchers.Default) {
                withTimeoutOrNull(5.seconds) { closed.await() } != null
            }
        } finally {
            // Released either way, or the test could not even time out.
            gate.complete(Unit)
        }
        parse.join()
        assert(isClosed)
    }

    @Test
    fun `should create a factory for every parse`() = runTest {
        // given — XMLInputFactory promises no thread safety
        var created = 0
        val counting = StaxXmlParser(
            factory = {
                created++
                defaultXmlInputFactory()
            }
        )

        // when
        counting.parse("<a/>").toList()
        counting.parse("<b/>").toList()

        // then
        assert(created == 2)
    }

    @Test
    fun `should not throw when the provider fails with an unchecked exception`() = runTest {
        // given — a provider failing with an exception which is no
        // XMLStreamException, nor caused by one, once the first element is read
        val failing = StaxXmlParser(
            factory = {
                object : DelegatingXmlInputFactory() {
                    override fun createXMLStreamReader(reader: Reader): XMLStreamReader =
                        object : StreamReaderDelegate(delegate.createXMLStreamReader(reader)) {
                            override fun next(): Int =
                                if (isStartElement) throw IllegalStateException("provider bug") else super.next()
                        }
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
    fun `should not unmark an element whose attributes the provider failed to report`() = runTest {
        // given — a provider reporting an error lazily, from an accessor
        val failing = StaxXmlParser(
            factory = {
                object : DelegatingXmlInputFactory() {
                    override fun createXMLStreamReader(reader: Reader): XMLStreamReader =
                        object : StreamReaderDelegate(delegate.createXMLStreamReader(reader)) {
                            override fun getAttributeValue(index: Int): String =
                                throw IllegalStateException("lazy error")
                        }
                }
            }
        )

        // when
        val events = failing.parse("""<a><b x="1"/></a>""").toList()

        // then — `b` was never marked, so it must not be unmarked either
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
