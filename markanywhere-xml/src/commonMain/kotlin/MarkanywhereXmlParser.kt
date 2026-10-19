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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile

/**
 * The hand-written multiplatform [XmlParser] — the engine which needs no
 * platform XML implementation underneath, and therefore runs everywhere
 * markanywhere does.
 *
 * It is a character-driven state machine: every character of every chunk is
 * fed to it as it arrives, and an event is emitted the moment the construct
 * carrying it has been read. Nothing beyond the construct currently being read
 * is held — a tag name, an attribute value, the run of text since the last
 * event — so memory stays bounded by the largest single construct rather than
 * by the document, and no chunk boundary can fall anywhere the machine cannot
 * resume from. This is what a pull parser such as `StaxXmlParser` can only
 * achieve by parking a thread on a blocking reader.
 *
 * Namespaces need no processing here: the contract keeps qualified names as
 * written and treats `xmlns` declarations as ordinary attributes, which is
 * precisely what a machine reading tags verbatim produces.
 *
 * ## Reading beyond what XML allows
 *
 * The [XmlParser] contract replaces draconian error handling with a balanced
 * stream, so this parser reports what it managed to read and stops at the first
 * thing it cannot: an unresolvable reference, a malformed tag, or a `</x>`
 * which does not close the innermost open element. Whatever was read before
 * that point stands, and the marks left open are closed afterwards.
 *
 * Rather than fail on constraints which say nothing about the shape of the
 * markup, it goes on reading: a fragment holding several root elements, or text
 * outside any element, parses as written, and a repeated attribute keeps the
 * value read last. Documents markanywhere is handed are often fragments, and
 * rejecting them would report less than was actually understood.
 *
 * ## Deliberate simplifications
 *
 * - An **undeclared entity** stops the read. DTDs are not processed — neither
 *   here nor, deliberately, in `StaxXmlParser` — so nothing beyond the five
 *   predefined entities can be resolved, and guessing at HTML's names would
 *   invent content the document does not declare.
 * - **Names** follow the XML 1.0 fifth-edition productions, whose ranges are
 *   broader than the older tables some platform parsers still apply — a name
 *   one of those rejects is an error the contract lets us read past.
 *   Surrogate halves are judged one at a time, so an unpaired one is accepted.
 */
public class MarkanywhereXmlParser : XmlParser {

    override fun parse(
        chunks: Flow<String>
    ): Flow<SemanticEvent> = flow {
        val document = XmlDocumentReader(collector = this)
        // Once malformed input has ended the read, draining the rest of the
        // source would be pointless, and a source which never ends would never
        // let the marks left open be closed. `takeWhile` ends the collection
        // with an exception owned by this very collection, so a parse whose
        // source is another parse never mistakes the other's abort for its own.
        chunks
            .takeWhile { chunk ->
                document.read(chunk)
                !document.isStopped
            }
            .collect()
        document.closeOpenElements()
    }

}

/**
 * The state machine reading one document into [collector], one character at a
 * time.
 *
 * A separate object rather than a fold over the chunks, because a chunk
 * boundary may fall anywhere — mid-tag, mid-attribute value, mid-reference —
 * and everything needed to resume is exactly the state kept here.
 */
private class XmlDocumentReader(
    private val collector: FlowCollector<SemanticEvent>
) {

    /** Whether malformed input has ended the read. */
    var isStopped: Boolean = false
        private set

    private var state = ReaderState.CONTENT

    /**
     * The names of the elements whose mark was emitted but not yet unmarked,
     * innermost last — both the stack matching end tags against, and what
     * [closeOpenElements] needs to balance a document cut short.
     */
    private val open = ArrayDeque<String>()

    private val text = StringBuilder()

    /**
     * Whether the text read since the last mark or unmark holds anything but
     * whitespace — kept apart from [text], which is flushed at every chunk end,
     * so that whitespace continuing such text is never taken for the prolog's.
     */
    private var textHasContent = false
    private val elementName = StringBuilder()
    private val attributes = LinkedHashMap<String, String>()
    private val attributeName = StringBuilder()
    private val attributeValue = StringBuilder()
    private val reference = StringBuilder()

    /** What has been read of a `<!` construct, until it identifies itself. */
    private val declaration = StringBuilder()

    /** The quote which opened the attribute value being read. */
    private var valueQuote = ' '

    /** Whether the reference being read stands in an attribute value. */
    private var referenceInAttribute = false

    /** Consecutive `-` of a comment, or `]` of a CDATA section. */
    private var repeats = 0

    /** Whether the previous character of a processing instruction was `?`. */
    private var questionMark = false

    /** The quote which opened a literal of the DOCTYPE being skipped, if any. */
    private var doctypeQuote: Char? = null

    /** Whether the DOCTYPE being skipped stands in its internal subset. */
    private var inInternalSubset = false

    /** The comment or processing instruction of the internal subset being skipped. */
    private var subsetMarkup: SubsetMarkup = NONE

    /** What has been read of a `<!--` or `<?` opener in the internal subset. */
    private val subsetOpening = StringBuilder()

    /** Set by a carriage return, so that the line feed of a CRLF is not read twice. */
    private var skipLineFeed = false

    /**
     * Reads [chunk], emitting every event it completes.
     *
     * The text read since the last event is flushed when the chunk ends, so
     * that text arriving over many chunks does not accumulate — the split
     * points are of no consequence, the contract leaving text granularity to
     * the implementation.
     */
    suspend fun read(chunk: String) {
        for (char in chunk) {
            if (isStopped) return
            if (skipLineFeed) {
                skipLineFeed = false
                if (char == '\n') continue
            }
            if (!char.isXmlCharacter()) {
                stop()
                return
            }
            process(char)
        }
        // Whitespace outside every element is held rather than flushed: only
        // what follows it tells whether it is the prolog or epilog whitespace
        // dropped below, or the start of text a fragment carries.
        if (!isOutsideElementsWhitespace()) flushText()
    }

    /**
     * Closes the elements left open — by a document which ended early, or by
     * one whose read was stopped — so that the stream stays balanced whatever
     * the input was.
     */
    suspend fun closeOpenElements() {
        // The `]` held back in a CDATA section cut short were read as content
        // as much as anything before them.
        if (state == CDATA) repeat(repeats) { appendText(']') }
        flushText()
        while (open.isNotEmpty()) {
            collector.emit(
                SemanticEvent.Unmark(
                    name = open.removeLast(),
                    isTagged = true
                )
            )
        }
    }

    private suspend fun process(char: Char) {
        when (state) {
            CONTENT -> when (char) {
                '<' -> state = MARKUP
                '&' -> startReference(inAttribute = false)
                '\r' -> appendLineFeed()
                else -> appendText(char)
            }
            MARKUP -> when {
                char == '/' -> {
                    elementName.clear()
                    state = END_TAG
                }
                char == '!' -> {
                    declaration.clear()
                    state = DECLARATION
                }
                char == '?' -> {
                    questionMark = false
                    state = PROCESSING_INSTRUCTION
                }
                char.isXmlNameStart() -> {
                    elementName.clear()
                    elementName.append(char)
                    attributes.clear()
                    state = START_TAG
                }
                else -> stop()
            }
            START_TAG -> when {
                char.isXmlNameChar() -> elementName.append(char)
                char.isXmlWhitespace() -> state = ATTRIBUTE_LIST
                char == '/' -> state = EMPTY_ELEMENT
                char == '>' -> startElement(isEmpty = false)
                else -> stop()
            }
            ATTRIBUTE_LIST -> when {
                char.isXmlWhitespace() -> {}
                char == '/' -> state = EMPTY_ELEMENT
                char == '>' -> startElement(isEmpty = false)
                char.isXmlNameStart() -> {
                    attributeName.clear()
                    attributeName.append(char)
                    state = ATTRIBUTE_NAME
                }
                else -> stop()
            }
            ATTRIBUTE_NAME -> when {
                char.isXmlNameChar() -> attributeName.append(char)
                char == '=' -> state = ATTRIBUTE_QUOTE
                char.isXmlWhitespace() -> state = ATTRIBUTE_EQUALS
                else -> stop()
            }
            ATTRIBUTE_EQUALS -> when {
                char.isXmlWhitespace() -> {}
                char == '=' -> state = ATTRIBUTE_QUOTE
                else -> stop()
            }
            ATTRIBUTE_QUOTE -> when {
                char.isXmlWhitespace() -> {}
                char == '"' || char == '\'' -> {
                    valueQuote = char
                    attributeValue.clear()
                    state = ATTRIBUTE_VALUE
                }
                else -> stop()
            }
            // A value is normalized as the XML specification prescribes: every
            // whitespace character becomes a space, while a character reference
            // to one stays what it refers to — hence the two separate paths.
            ATTRIBUTE_VALUE -> when (char) {
                valueQuote -> endAttribute()
                '&' -> startReference(inAttribute = true)
                '<' -> stop()
                '\r' -> {
                    attributeValue.append(' ')
                    skipLineFeed = true
                }
                '\n', '\t' -> attributeValue.append(' ')
                else -> attributeValue.append(char)
            }
            ATTRIBUTE_END -> when {
                char.isXmlWhitespace() -> state = ATTRIBUTE_LIST
                char == '/' -> state = EMPTY_ELEMENT
                char == '>' -> startElement(isEmpty = false)
                else -> stop()
            }
            EMPTY_ELEMENT -> if (char == '>') startElement(isEmpty = true) else stop()
            END_TAG -> when {
                elementName.isEmpty() -> if (char.isXmlNameStart()) {
                    elementName.append(char)
                } else {
                    stop()
                }
                char.isXmlNameChar() -> elementName.append(char)
                char.isXmlWhitespace() -> state = END_TAG_END
                char == '>' -> endElement()
                else -> stop()
            }
            END_TAG_END -> when {
                char.isXmlWhitespace() -> {}
                char == '>' -> endElement()
                else -> stop()
            }
            REFERENCE -> when {
                char == ';' -> resolveReference()
                char == '#' && reference.isEmpty() -> reference.append(char)
                char.isXmlNameChar() -> reference.append(char)
                else -> stop()
            }
            DECLARATION -> readDeclaration(char)
            COMMENT -> if (endsComment(char)) state = CONTENT
            CDATA -> readCdata(char)
            PROCESSING_INSTRUCTION -> if (endsInstruction(char)) state = CONTENT
            DOCTYPE -> readDoctype(char)
        }
    }

    /**
     * Identifies the `<!` construct being read from the characters seen so far
     * — `--` a comment, `[CDATA[` a section of character data, and a name the
     * DOCTYPE, the only one of the three which is neither content nor dropped
     * wholesale by a single terminator.
     */
    private suspend fun readDeclaration(char: Char) {
        declaration.append(char)
        val read = declaration.toString()
        when {
            read == "-" -> {}
            read == "--" -> {
                repeats = 0
                state = COMMENT
            }
            CDATA_OPENING.startsWith(read) -> if (read == CDATA_OPENING) {
                repeats = 0
                state = CDATA
            }
            read.length == 1 && char.isXmlNameStart() -> {
                doctypeQuote = null
                inInternalSubset = false
                subsetMarkup = NONE
                subsetOpening.clear()
                state = DOCTYPE
            }
            else -> stop()
        }
    }

    /**
     * Reads a CDATA section as the character data it is — the boundaries are
     * not preserved, per the [XmlParser] contract.
     *
     * A `]` is held back until the following character tells whether it ends
     * the section or is content, which is what makes `]]]>` a `]` followed by
     * the end of the section.
     */
    private fun readCdata(char: Char) {
        when {
            char == ']' -> repeats++
            char == '>' && repeats >= 2 -> {
                repeat(repeats - 2) { appendText(']') }
                repeats = 0
                state = CONTENT
            }
            else -> {
                repeat(repeats) { appendText(']') }
                repeats = 0
                if (char == '\r') appendLineFeed() else appendText(char)
            }
        }
    }

    /**
     * Skips a DOCTYPE declaration, which the event model cannot carry.
     *
     * Its terminating `>` is found rather than assumed: a system literal may
     * hold one, and so may the internal subset — whose comments and processing
     * instructions are skipped whole, since a quote, `]` or `>` inside one is
     * neither a literal nor the end of anything.
     */
    private fun readDoctype(char: Char) {
        when (subsetMarkup) {
            COMMENT -> if (endsComment(char)) subsetMarkup = NONE
            PROCESSING_INSTRUCTION -> if (endsInstruction(char)) subsetMarkup = NONE
            NONE -> readDoctypeDeclaration(char)
        }
    }

    /**
     * Follows a comment, counting its consecutive `-` in [repeats], up to the
     * `-->` which ends it — wherever the comment stands.
     */
    private fun endsComment(char: Char): Boolean {
        when {
            char == '-' -> repeats++
            char == '>' && repeats >= 2 -> {
                repeats = 0
                return true
            }
            else -> repeats = 0
        }
        return false
    }

    /**
     * Follows a processing instruction up to the `?>` which ends it — wherever
     * the instruction stands.
     */
    private fun endsInstruction(char: Char): Boolean {
        val ends = char == '>' && questionMark
        questionMark = char == '?'
        return ends
    }

    private fun readDoctypeDeclaration(char: Char) {
        val quote = doctypeQuote
        if (quote != null) {
            if (char == quote) doctypeQuote = null
            return
        }
        if (inInternalSubset && startsSubsetMarkup(char)) return
        when (char) {
            '"', '\'' -> doctypeQuote = char
            '[' -> inInternalSubset = true
            ']' -> inInternalSubset = false
            '>' -> if (!inInternalSubset) state = CONTENT
        }
    }

    /**
     * Follows a `<!--` or `<?` opener in the internal subset, entering the
     * comment or processing instruction it starts once complete.
     */
    private fun startsSubsetMarkup(char: Char): Boolean {
        if (char == '<') {
            subsetOpening.clear()
            subsetOpening.append(char)
            return false
        }
        if (subsetOpening.isEmpty()) return false
        subsetOpening.append(char)
        val read = subsetOpening.toString()
        val markup: SubsetMarkup = when (read) {
            "<?" -> PROCESSING_INSTRUCTION
            COMMENT_OPENING -> COMMENT
            else -> {
                if (!COMMENT_OPENING.startsWith(read)) subsetOpening.clear()
                return false
            }
        }
        subsetOpening.clear()
        repeats = 0
        questionMark = false
        subsetMarkup = markup
        return true
    }

    private fun startReference(inAttribute: Boolean) {
        reference.clear()
        referenceInAttribute = inAttribute
        state = REFERENCE
    }

    /**
     * Resolves the reference just read into the text or attribute value it
     * stands in, stopping the read when it resolves to nothing.
     */
    private suspend fun resolveReference() {
        val body = reference.toString()
        val resolved = if (body.startsWith("#")) {
            decodeCharacterReference(body)
        } else {
            PREDEFINED_ENTITIES[body]
        }
        if (resolved == null) {
            stop()
            return
        }
        if (referenceInAttribute) {
            attributeValue.append(resolved)
            state = ATTRIBUTE_VALUE
        } else {
            resolved.forEach { appendText(it) }
            state = CONTENT
        }
    }

    private fun endAttribute() {
        attributes[attributeName.toString()] = attributeValue.toString()
        state = ATTRIBUTE_END
    }

    /**
     * The attributes read, namespace declarations first — the order the
     * [XmlParser] contract prescribes, each group otherwise in document order.
     */
    private fun orderedAttributes(): Map<String, String> {
        if (attributes.keys.none { it.isNamespaceDeclaration() }) return attributes.toMap()
        val (declarations, others) = attributes.entries.partition {
            it.key.isNamespaceDeclaration()
        }
        return (declarations + others).associate { it.key to it.value }
    }

    private suspend fun startElement(isEmpty: Boolean) {
        val name = elementName.toString()
        flushText()
        collector.emit(
            SemanticEvent.Mark(
                name = name,
                isTagged = true,
                attributes = orderedAttributes()
            )
        )
        if (isEmpty) {
            collector.emit(SemanticEvent.Unmark(name = name, isTagged = true))
        } else {
            open.addLast(name)
        }
        textHasContent = false
        attributes.clear()
        state = CONTENT
    }

    private suspend fun endElement() {
        flushText()
        val name = elementName.toString()
        if (open.lastOrNull() != name) {
            // An end tag which does not close the innermost open element cannot
            // be reported without emitting an unmark crossing a mark, so the
            // read gives up here and lets the open elements be closed in order.
            stop()
            return
        }
        open.removeLast()
        collector.emit(SemanticEvent.Unmark(name = name, isTagged = true))
        textHasContent = false
        state = CONTENT
    }

    private fun appendText(char: Char) {
        text.append(char)
        if (!char.isXmlWhitespace()) textHasContent = true
    }

    /** Appends the line feed a carriage return is read as, CRLF being one. */
    private fun appendLineFeed() {
        text.append('\n')
        skipLineFeed = true
    }

    /**
     * Emits the text read since the last event — except whitespace standing
     * outside every element, which is dropped: the line breaks around the root
     * element of a file are not character data, and StAX does not report them
     * either. Text outside any element which is not all whitespace is kept,
     * since a fragment may carry it.
     */
    private suspend fun flushText() {
        if (text.isEmpty()) return
        if (!isOutsideElementsWhitespace()) {
            collector.emit(SemanticEvent.Text(text.toString()))
        }
        text.clear()
    }

    private fun isOutsideElementsWhitespace(): Boolean =
        open.isEmpty() && !textHasContent

    /** Ends the read, keeping everything understood up to this point. */
    private suspend fun stop() {
        flushText()
        isStopped = true
    }

}

/**
 * Where in the markup the reader stands — the states a chunk boundary may fall
 * between.
 */
private enum class ReaderState {
    CONTENT,
    MARKUP,
    START_TAG,
    ATTRIBUTE_LIST,
    ATTRIBUTE_NAME,
    ATTRIBUTE_EQUALS,
    ATTRIBUTE_QUOTE,
    ATTRIBUTE_VALUE,
    ATTRIBUTE_END,
    EMPTY_ELEMENT,
    END_TAG,
    END_TAG_END,
    REFERENCE,
    DECLARATION,
    COMMENT,
    CDATA,
    PROCESSING_INSTRUCTION,
    DOCTYPE
}

/** A comment or processing instruction inside a DOCTYPE's internal subset. */
private enum class SubsetMarkup {
    NONE,
    COMMENT,
    PROCESSING_INSTRUCTION
}

private const val CDATA_OPENING = "[CDATA["

private const val COMMENT_OPENING = "<!--"

/** The only entities a document needs no DTD to use. */
private val PREDEFINED_ENTITIES = mapOf(
    "lt" to "<",
    "gt" to ">",
    "amp" to "&",
    "apos" to "'",
    "quot" to "\""
)

/**
 * The character [body] — a reference body starting with `#` — refers to, or
 * `null` when it refers to no character XML allows.
 */
private fun decodeCharacterReference(body: String): String? {
    // Only a lowercase `x`, and only ASCII digits: `toIntOrNull` would also
    // take any Unicode digit, Arabic-Indic or fullwidth, which XML does not.
    val isHex = body.startsWith("#x")
    val digits = body.substring(if (isHex) 2 else 1)
    if (digits.isEmpty()) return null
    if (!digits.all { if (isHex) it.isAsciiHexDigit() else it.isAsciiDigit() }) return null
    val code = digits.toIntOrNull(radix = if (isHex) 16 else 10) ?: return null
    if (!isXmlCharacter(code)) return null
    if (code <= 0xFFFF) return code.toChar().toString()
    val supplementary = code - 0x10000
    return charArrayOf(
        (0xD800 + (supplementary shr 10)).toChar(),
        (0xDC00 + (supplementary and 0x3FF)).toChar()
    ).concatToString()
}

/** Whether [code] is a code point XML permits in a document. */
private fun isXmlCharacter(code: Int): Boolean =
    (code == 0x9) || (code == 0xA) || (code == 0xD) ||
        (code in 0x20..0xD7FF) ||
        (code in 0xE000..0xFFFD) ||
        (code in 0x10000..0x10FFFF)

/**
 * Whether this UTF-16 unit may stand in a document — [isXmlCharacter] judged
 * one unit at a time, so either half of a surrogate pair is accepted, the pair
 * encoding a character above the basic plane.
 */
private fun Char.isXmlCharacter(): Boolean =
    (this == '\t') || (this == '\n') || (this == '\r') ||
        (this in '\u0020'..'\uFFFD')

private fun String.isNamespaceDeclaration(): Boolean =
    (this == "xmlns") || startsWith("xmlns:")

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

private fun Char.isAsciiHexDigit(): Boolean =
    isAsciiDigit() || (this in 'a'..'f') || (this in 'A'..'F')

private fun Char.isXmlWhitespace(): Boolean =
    (this == ' ') || (this == '\t') || (this == '\n') || (this == '\r')

/**
 * The `NameStartChar` production of XML 1.0 (fifth edition). A character above
 * the basic plane arrives as a surrogate pair, read one half at a time, so both
 * halves of the production's `[#x10000-#xEFFFF]` range are accepted.
 */
private fun Char.isXmlNameStart(): Boolean =
    (this in 'a'..'z') || (this in 'A'..'Z') || (this == '_') || (this == ':') ||
        (this in '\u00C0'..'\u00D6') ||
        (this in '\u00D8'..'\u00F6') ||
        (this in '\u00F8'..'\u02FF') ||
        (this in '\u0370'..'\u037D') ||
        (this in '\u037F'..'\u1FFF') ||
        (this in '\u200C'..'\u200D') ||
        (this in '\u2070'..'\u218F') ||
        (this in '\u2C00'..'\u2FEF') ||
        (this in '\u3001'..'\uD7FF') ||
        (this in '\uF900'..'\uFDCF') ||
        (this in '\uFDF0'..'\uFFFD') ||
        (this in '\uD800'..'\uDB7F') ||
        (this in '\uDC00'..'\uDFFF')

/** The `NameChar` production of XML 1.0 (fifth edition). */
private fun Char.isXmlNameChar(): Boolean =
    isXmlNameStart() || (this in '0'..'9') || (this == '-') || (this == '.') ||
        (this == '\u00B7') ||
        (this in '\u0300'..'\u036F') ||
        (this in '\u203F'..'\u2040')
