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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.channelFlow
import java.io.Reader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/**
 * An [XmlParser] backed by the JDK's StAX implementation
 * (`javax.xml.stream`), serving as the reference against which a hand-written
 * multiplatform parser can be measured — both for conformance (it runs the
 * same `XmlParserTest` conformance suite) and for throughput.
 *
 * StAX is a *pull* parser reading from a blocking [Reader], which inverts the
 * control flow of a [Flow]-driven pipeline: it wants to ask for the next
 * characters, while a flow wants to hand them over. The two are bridged by
 * running the pull loop on a dedicated view of [Dispatchers.IO] against a
 * [Reader] blocking on a
 * [Channel] which a coroutine fills from [parse]'s chunks. Parsing is
 * therefore genuinely incremental — an element is reported as soon as its tag
 * has been read, however much of the document is still to come — and memory
 * stays bounded by the channel rather than by the document.
 *
 * What that costs, and what a native implementation would save: a thread
 * held by the pull loop for the whole parse — blocked in [runBlocking]
 * whenever the parser has read every chunk buffered so far — a queue hop per
 * chunk, and a second coroutine to feed it. The JDK ships no non-blocking XML parser; a
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
        val feeder = launch {
            try {
                chunks.collect { chunk -> source.send(chunk) }
            } finally {
                source.close()
            }
        }
        val collector = FlowCollector<SemanticEvent> { event -> send(event) }
        try {
            withContext(PULL_DISPATCHER) {
                collector.readDocument(ChannelReader(source))
            }
        } finally {
            // Releases the feeding coroutine when the reader stopped early —
            // whether it is suspended sending a chunk or waiting for the next
            // one from its source — since this channelFlow awaits its children.
            feeder.cancel()
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
                        val mark = SemanticEvent.Mark(
                            name = reader.elementName(),
                            isTagged = true,
                            attributes = reader.readAttributes()
                        )
                        emit(mark)
                        // Only now, as reading the attributes may still have
                        // failed: an element never marked must not be unmarked.
                        open.addLast(mark.name)
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
                        // An empty CDATA section is reported as empty text,
                        // which carries nothing.
                        val text = reader.text
                        if (text.isNotEmpty()) emit(SemanticEvent.Text(text))
                    }
                    // COMMENT, PROCESSING_INSTRUCTION, DTD, START_DOCUMENT and
                    // END_DOCUMENT carry nothing the event model can express.
                    else -> {}
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Not well-formed — reported as an XMLStreamException, lazily
            // wrapped in an unchecked one (Woodstox's `WstxLazyException`), or
            // as whatever a provider throws on input it chokes on: stop reading
            // here. The contract forbids throwing, and the balancing below
            // keeps the truncated stream renderable.
        } finally {
            try {
                reader?.close()
            } catch (_: Exception) {
                // Closing a reader which already failed is of no interest.
            }
        }
        unmarkAll(open)
    }

}

/**
 * A [Reader] draining [chunks], blocking the calling thread until the next one
 * arrives — the adapter which lets a pull parser read from a [Flow].
 *
 * It may only ever be read on a thread allowed to block, hence the
 * [PULL_DISPATCHER] confinement at its single call site. A closed or cancelled
 * channel reads as end of input, so a parse ends when its source does.
 */
private class ChannelReader(
    private val chunks: ReceiveChannel<String>
) : Reader() {

    private var chunk: String? = null
    private var position = 0

    /** Whether no character has been read yet, so a byte order mark is due. */
    private var atStart = true

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
            // Blocking only when no chunk is buffered yet, since every
            // `runBlocking` sets up an event loop of its own.
            val buffered = chunks.tryReceive()
            val received = if (buffered.isSuccess || buffered.isClosed) {
                buffered
            } else {
                runBlocking { chunks.receiveCatching() }
            }
            val next = received.getOrNull() ?: return null
            if (next.isEmpty()) continue
            // A leading byte order mark is skipped, per the XmlParser
            // contract — the JDK's reader would take it for content in the prolog.
            val start = if (atStart && next[0] == BYTE_ORDER_MARK) 1 else 0
            atStart = false
            if (start < next.length) {
                chunk = next
                position = start
            }
        }
        return chunk
    }

    override fun close() {
        // The channel belongs to the parser, which cancels it when the parse ends.
    }

}

private const val BYTE_ORDER_MARK = '\uFEFF'

/**
 * Where the pull loops run, each parking its thread for the whole parse.
 *
 * A view of [Dispatchers.IO] rather than the dispatcher itself: the parked
 * threads must not count against the shared pool's limit, or as many
 * concurrent parses as it has threads — 64 by default — would leave none for
 * the sources they wait on, whenever those read on [Dispatchers.IO] too. The
 * view is unbounded for the same reason: a parse whose source is another parse
 * needs a second thread while holding the first.
 */
private val PULL_DISPATCHER = Dispatchers.IO.limitedParallelism(Int.MAX_VALUE)

/**
 * The qualified name of the current element as written. A reader that is not
 * namespace-aware keeps the prefix in the local name of an element, so the
 * prefix is joined back only for a provider which reports it apart.
 */
private fun XMLStreamReader.elementName(): String =
    qualifiedName(prefix, localName)

/**
 * The attributes of the current element in document order, `xmlns` / `xmlns:*`
 * declarations among them — a reader that is not namespace-aware reports them
 * as ordinary attributes. The JDK's still splits the prefix off an attribute
 * name, which is why it is joined back.
 */
private fun XMLStreamReader.readAttributes(): Map<String, String> {
    if (attributeCount == 0) return emptyMap()
    val attributes = LinkedHashMap<String, String>(attributeCount)
    for (i in 0 until attributeCount) {
        attributes[qualifiedName(getAttributePrefix(i), getAttributeLocalName(i))] =
            getAttributeValue(i)
    }
    return attributes
}

private fun qualifiedName(prefix: String?, localName: String): String =
    if (prefix.isNullOrEmpty()) localName else "$prefix:$localName"

/**
 * The JDK's own [XMLInputFactory], configured for the [XmlParser] contract,
 * with every door to an external resource shut.
 *
 * Namespace processing is off: the contract keeps qualified names as written
 * and declarations as attributes, and a namespace-aware reader would reject a
 * fragment using a prefix it does not declare — an RSS item without its feed's
 * `xmlns:dc`, say.
 *
 * Text is not coalesced — the contract leaves its granularity open — because a
 * coalescing reader discards the character data it has read when an error
 * follows in the same text run, `text ` of `<a>text &nbsp;</a>` included.
 *
 * `newDefaultFactory`, not `newInstance`: the latter picks whichever StAX
 * provider is on the classpath (Woodstox, say, which Jackson XML brings in), and
 * would silently swap the reference engine for another.
 */
public fun defaultXmlInputFactory(): XMLInputFactory =
    XMLInputFactory.newDefaultFactory().apply {
        setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false)
        setProperty(XMLInputFactory.IS_COALESCING, false)
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    }