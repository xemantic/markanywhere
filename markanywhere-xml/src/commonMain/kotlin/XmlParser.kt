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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flowOf

/**
 * Parses XML source into a stream of [SemanticEvent]s.
 *
 * The interface exists so that the same event stream can be produced by
 * different engines — the handwritten multiplatform [MarkanywhereXmlParser], or
 * a platform XML parser such as Java's StAX (`StaxXmlParser`, JVM only) — and
 * the two compared for conformance and throughput. Every
 * implementation is expected to honour the contract below, so a document
 * parsed by one is indistinguishable from the same document parsed by another
 * (after `mergeAdjacentText()` of `markanywhere-flow`, see *Text* below).
 *
 * ## Mapping of XML constructs onto semantic events
 *
 * - **Element** → [SemanticEvent.Mark] / [SemanticEvent.Unmark] with
 *   [SemanticEvent.Marked.isTagged] `= true` (the mark originates from an
 *   actual tag, never from Markdown syntax). The name is the *qualified* name
 *   as written in the source, prefix included (`svg:rect`, not the resolved
 *   namespace URI); case is preserved, XML being case-sensitive. There is no
 *   distinction between `<a/>` and `<a></a>` — both are a mark immediately
 *   followed by its unmark, and it is up to a serializer to pick a form.
 * - **Attributes** → [SemanticEvent.Mark.attributes], with entity references
 *   expanded and values normalized per the XML specification, in document
 *   order. Namespaces are not processed: declarations (`xmlns`, `xmlns:*`)
 *   are ordinary attributes, so that no source information is lost, and a
 *   prefix nothing declares is no error — a fragment cut out of a larger
 *   document often uses one.
 * - **Character data, CDATA sections, character and entity references** →
 *   [SemanticEvent.Text]. The boundaries of a CDATA section are **not**
 *   preserved: `<![CDATA[a < b]]>` and `a &lt; b` produce the same event, so a
 *   re-serialized document is character-data-equivalent but not
 *   byte-identical. This matches the XML Infoset and the XPath data model,
 *   both of which treat a CDATA section as ordinary character data.
 * - **Comments, processing instructions, the XML declaration and the DOCTYPE**
 *   are dropped — the three-variant event model has no way to carry them.
 * - **Whitespace outside every element** — the line breaks around the root
 *   element of a file, or any other run of whitespace standing between two
 *   pieces of markup there — is dropped, as it is not character data of any
 *   element. A character reference or a CDATA section is character data
 *   even when it holds only whitespace.
 *
 * ## Text granularity
 *
 * Consecutive [SemanticEvent.Text] events may be split at arbitrary
 * boundaries — a chunk boundary, an expanded entity, the end of a CDATA
 * section — and the split points are implementation-specific. Apply
 * `mergeAdjacentText()` (`markanywhere-flow`) when maximal text runs
 * matter, in particular when comparing the output of two implementations.
 *
 * ## Well-formedness
 *
 * XML mandates draconian error handling; this interface does not. A parser
 * **must not** throw on malformed input, and **must** emit a strictly balanced
 * stream: every [SemanticEvent.Mark] paired with a matching
 * [SemanticEvent.Unmark] in LIFO order, so that any downstream renderer can
 * consume the result. On an unrecoverable error the parser stops reading and
 * closes whatever marks are still open.
 *
 * ## Encoding
 *
 * The input is already-decoded text, so the encoding declaration of an XML
 * declaration cannot influence decoding — byte-level detection (BOM sniffing,
 * `encoding="…"`) is the caller's responsibility, and out of scope here. A
 * byte order mark (U+FEFF) the decoder kept as the first character is skipped.
 */
public interface XmlParser {

    /**
     * Parses the XML arriving in [chunks] — split at arbitrary character
     * boundaries — into a flow of [SemanticEvent]s.
     */
    public fun parse(chunks: Flow<String>): Flow<SemanticEvent>

}

/**
 * Parses a complete XML document held in memory. See [XmlParser.parse].
 */
public fun XmlParser.parse(
    xml: String
): Flow<SemanticEvent> = parse(flowOf(xml))

/**
 * Parses this flow of XML source chunks with the given [parser], so that
 * parsing reads as a pipeline step: `chunks.parseXml(parser).renderMarkdown()`.
 */
public fun Flow<String>.parseXml(
    parser: XmlParser
): Flow<SemanticEvent> = parser.parse(this)

/**
 * Unmarks every element still in [open], innermost last, emptying it — how
 * every engine keeps the balanced stream the contract promises when a document
 * ends early or its read was stopped.
 */
internal suspend fun FlowCollector<SemanticEvent>.unmarkAll(open: ArrayDeque<String>) {
    while (open.isNotEmpty()) {
        emit(
            SemanticEvent.Unmark(
                name = open.removeLast(),
                isTagged = true
            )
        )
    }
}
