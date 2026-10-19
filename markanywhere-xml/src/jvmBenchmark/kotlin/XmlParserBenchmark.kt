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

import com.xemantic.kotlin.core.text.unaryPlus
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Fork

/**
 * Compares [MarkanywhereXmlParser] with [StaxXmlParser] on the same document,
 * fed whole, in large chunks, and in token-sized chunks as an LLM would stream
 * it.
 *
 * The parsers split text into different numbers of events (StAX does not
 * coalesce, the streaming parser emits a text event per chunk), so the score is
 * the time to parse the document, not the time per event.
 */
@State(Scope.Benchmark)
// A fixed heap, so that every fork collects garbage on the same schedule; the
// number of forks comes from the benchmark configuration.
@Fork(jvmArgsAppend = ["-Xms2g", "-Xmx2g"])
class XmlParserBenchmark {

    @Param("markanywhere", "stax")
    var parser: String = ""

    /** Number of `<book>` records — 100 is ~44 KB, 5000 is ~2.2 MB. */
    @Param("100", "5000")
    var records: Int = 0

    /** Chunk length in characters, 0 meaning the whole document at once. */
    @Param("0", "4096", "16")
    var chunkSize: Int = 0

    private lateinit var xmlParser: XmlParser

    private lateinit var chunks: List<String>

    @Setup
    fun setUp() {
        xmlParser = when (parser) {
            "markanywhere" -> MarkanywhereXmlParser()
            "stax" -> StaxXmlParser()
            else -> error("Unknown parser: $parser")
        }
        val xml = document(records)
        chunks = if (chunkSize == 0) listOf(xml) else xml.chunked(chunkSize)
    }

    @Benchmark
    fun parse(): Int = runBlocking {
        xmlParser.parse(chunks.asFlow()).count()
    }

}

private fun document(records: Int): String = buildString {
    +"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
    +"<catalog xmlns=\"urn:x\" xmlns:m=\"urn:meta\">\n"
    repeat(records) { i ->
        +"  <book id=\"b$i\" m:lang=\"en\" available=\"${i % 2 == 0}\">\n"
        +"    <title>Title number $i &amp; friends</title>\n"
        +"    <author first=\"Ada\" last=\"Lovelace\"/>\n"
        +"    <!-- a comment $i -->\n"
        +"    <description>Some <em>emphasised</em> text with &lt;escapes&gt; and &#169; refs, "
        +"plus a longer run of plain prose to make text nodes realistic in size. Lorem ipsum dolor sit amet.</description>\n"
        +"    <code><![CDATA[if (a < b && c > d) { return; }]]></code>\n"
        +"  </book>\n"
    }
    +"</catalog>\n"
}
