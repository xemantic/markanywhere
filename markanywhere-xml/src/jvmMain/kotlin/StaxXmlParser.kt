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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.Reader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader

/**
 * An [XmlParser] backed by the JDK's StAX implementation
 * (`javax.xml.stream`), serving as the reference against which a hand-written
 * multiplatform parser can be measured — both for conformance (it runs the
 * same [XmlParserTest] suite) and for throughput.
 *
 * StAX is a *pull* parser reading from a blocking [Reader], which inverts the
 * control flow of a [Flow]-driven pipeline: it wants to ask for the next
 * characters, while a flow wants to hand them over. The two are bridged by
 * running the pull loop on [Dispatchers.IO] against a [Reader] blocking on a
 * [Channel] which a coroutine fills from [parse]'s chunks. Parsing is
 * therefore genuinely incremental — an element is reported as soon as its tag
 * has been read, however much of the document is still to come — and memory
 * stays bounded by the channel rather than by the document.
 *
 * What that costs, and what a native implementation would save: a thread
 * parked in [runBlocking] for the whole parse, a queue hop per read, and a
 * second coroutine to feed it. The JDK ships no non-blocking XML parser; a
 * push-mode one (Aalto's `AsyncXMLStreamReader`, say) would free the parked
 * thread at the price of a dependency.
 *
 * Both DTD processing and external entities are disabled, so no external
 * resource is ever resolved (XXE) and no entity expansion can be nested
 * (billion laughs). Per the [XmlParser] contract a DOCTYPE is dropped rather
 * than rejected.
 */
public class StaxXmlParser(
    private val factory: XMLInputFactory = defaultXmlInputFactory()
) : XmlParser {

    override fun parse(
        chunks: Flow<String>
    ): Flow<SemanticEvent> = channelFlow {
        val source = Channel<String>(Channel.BUFFERED)
        launch {
            try {
                chunks.collect { chunk -> source.send(chunk) }
            } finally {
                source.close()
            }
        }
        val collector = FlowCollector<SemanticEvent> { event -> send(event) }
        try {
            withContext(Dispatchers.IO) {
                collector.readDocument(ChannelReader(source))
            }
        } finally {
            // Releases the feeding coroutine when the reader stopped early: its
            // pending send fails, the collection unwinds, and only then can this
            // channelFlow — which awaits its children — complete.
            source.cancel()
        }
    }

    /**
     * Pulls the document [source] through an [XMLStreamReader], emitting
     * semantic events as it goes. A malformed document stops the read where
     * the error occurred instead of propagating — the marks left open are
     * closed afterwards.
     */
    private suspend fun FlowCollector<SemanticEvent>.readDocument(source: Reader) {
        // Names of the elements whose mark has been emitted but not yet
        // unmarked, so that a document cut short still yields a balanced stream.
        val open = ArrayDeque<String>()
        var reader: XMLStreamReader? = null
        try {
            reader = factory.createXMLStreamReader(source)
            while (reader.hasNext()) {
                when (reader.next()) {
                    XMLStreamConstants.START_ELEMENT -> {
                        val name = reader.elementName()
                        open.addLast(name)
                        emit(
                            SemanticEvent.Mark(
                                name = name,
                                isTagged = true,
                                attributes = reader.readAttributes()
                            )
                        )
                    }
                    XMLStreamConstants.END_ELEMENT -> {
                        emit(
                            SemanticEvent.Unmark(
                                name = open.removeLast(),
                                isTagged = true
                            )
                        )
                    }
                    XMLStreamConstants.CHARACTERS,
                    XMLStreamConstants.CDATA,
                    XMLStreamConstants.SPACE,
                    XMLStreamConstants.ENTITY_REFERENCE -> {
                        emit(SemanticEvent.Text(reader.text))
                    }
                    // COMMENT, PROCESSING_INSTRUCTION, DTD, START_DOCUMENT and
                    // END_DOCUMENT carry nothing the event model can express.
                    else -> {}
                }
            }
        } catch (_: XMLStreamException) {
            // Not well-formed: stop reading here. The contract forbids throwing,
            // and the balancing below keeps the truncated stream renderable.
        } finally {
            try {
                reader?.close()
            } catch (_: XMLStreamException) {
                // Closing a reader which already failed is of no interest.
            }
        }
        while (open.isNotEmpty()) {
            emit(
                SemanticEvent.Unmark(
                    name = open.removeLast(),
                    isTagged = true
                )
            )
        }
    }

}

/**
 * A [Reader] draining [chunks], blocking the calling thread until the next one
 * arrives — the adapter which lets a pull parser read from a [Flow].
 *
 * It may only ever be read on a thread allowed to block, hence the
 * [Dispatchers.IO] confinement at its single call site. A closed or cancelled
 * channel reads as end of input, so a parse ends when its source does.
 */
private class ChannelReader(
    private val chunks: ReceiveChannel<String>
) : Reader() {

    private var chunk: String? = null
    private var position = 0

    override fun read(buffer: CharArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val current = awaitChunk() ?: return -1
        val count = minOf(length, current.length - position)
        current.toCharArray(buffer, offset, position, position + count)
        position += count
        if (position == current.length) chunk = null
        return count
    }

    /**
     * The chunk being read, blocking for the next non-empty one once the
     * previous is exhausted. Null when the source is done.
     */
    private fun awaitChunk(): String? {
        while (chunk == null) {
            val next = runBlocking { chunks.receiveCatching() }.getOrNull() ?: return null
            if (next.isNotEmpty()) {
                chunk = next
                position = 0
            }
        }
        return chunk
    }

    override fun close() {
        // The channel belongs to the parser, which cancels it when the parse ends.
    }

}

/**
 * The qualified name of the current element, prefix included — StAX reports
 * the prefix and the local name separately, while the event model keeps the
 * name as written.
 */
private fun XMLStreamReader.elementName(): String =
    if (prefix.isNullOrEmpty()) localName else "$prefix:$localName"

/**
 * The attributes of the current element, namespace declarations first.
 *
 * A namespace-aware StAX reader consumes `xmlns` / `xmlns:*` declarations and
 * reports them apart from the attributes, so they have to be put back to keep
 * the event stream a faithful record of the source.
 */
private fun XMLStreamReader.readAttributes(): Map<String, String> {
    if (namespaceCount == 0 && attributeCount == 0) return emptyMap()
    val attributes = LinkedHashMap<String, String>(namespaceCount + attributeCount)
    for (i in 0 until namespaceCount) {
        val prefix = getNamespacePrefix(i)
        val name = if (prefix.isNullOrEmpty()) "xmlns" else "xmlns:$prefix"
        attributes[name] = getNamespaceURI(i) ?: ""
    }
    for (i in 0 until attributeCount) {
        val prefix = getAttributePrefix(i)
        val name = if (prefix.isNullOrEmpty()) {
            getAttributeLocalName(i)
        } else {
            "$prefix:${getAttributeLocalName(i)}"
        }
        attributes[name] = getAttributeValue(i)
    }
    return attributes
}

/**
 * An [XMLInputFactory] configured for the [XmlParser] contract: adjacent
 * character data and CDATA sections coalesced into a single text event, and
 * every door to an external resource shut.
 */
public fun defaultXmlInputFactory(): XMLInputFactory =
    XMLInputFactory.newInstance().apply {
        setProperty(XMLInputFactory.IS_COALESCING, true)
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    }