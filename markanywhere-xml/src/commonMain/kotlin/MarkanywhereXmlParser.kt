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
import kotlinx.coroutines.flow.flow

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
 * - A **name character** is approximated as a letter, a digit, `_`, `:`, `-`,
 *   `.` or `·`, instead of enumerating the Unicode ranges of the XML
 *   production. This accepts every name in practical use, and additionally
 *   accepts a few exotic ones XML would reject — an error the contract lets us
 *   read past rather than a construct we misread.
 */
public class MarkanywhereXmlParser : XmlParser {

    override fun parse(
        chunks: Flow<String>
    ): Flow<SemanticEvent> = flow {
        val document = XmlDocumentReader(collector = this)
        try {
            chunks.collect { chunk ->
                document.read(chunk)
                if (document.isStopped) throw EndOfRead
            }
        } catch (_: EndOfRead) {
            // Malformed input ended the read before the source ended. Draining
            // the rest would be pointless, and a source which never ends would
            // never let the marks left open be closed.
        }
        document.closeOpenElements()
    }

}

/**
 * Thrown to leave [Flow.collect] once the read is over, the way
 * `kotlinx.coroutines` itself ends a collection early. Caught immediately at
 * its only call site, so it never escapes the parser.
 */
private object EndOfRead : Throwable(null, null)

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
            process(char)
        }
        flushText()
    }

    /**
     * Closes the elements left open — by a document which ended early, or by
     * one whose read was stopped — so that the stream stays balanced whatever
     * the input was.
     */
    suspend fun closeOpenElements() {
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
                '\r' -> appendLineFeed(text)
                else -> text.append(char)
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
            COMMENT -> when {
                char == '-' -> repeats++
                char == '>' && repeats >= 2 -> {
                    repeats = 0
                    state = CONTENT
                }
                else -> repeats = 0
            }
            CDATA -> readCdata(char)
            PROCESSING_INSTRUCTION -> when {
                char == '?' -> questionMark = true
                char == '>' && questionMark -> {
                    questionMark = false
                    state = CONTENT
                }
                else -> questionMark = false
            }
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
                repeat(repeats - 2) { text.append(']') }
                repeats = 0
                state = CONTENT
            }
            else -> {
                repeat(repeats) { text.append(']') }
                repeats = 0
                if (char == '\r') appendLineFeed(text) else text.append(char)
            }
        }
    }

    /**
     * Skips a DOCTYPE declaration, which the event model cannot carry.
     *
     * Its terminating `>` is found rather than assumed: a system literal may
     * hold one, and so may the internal subset.
     */
    private fun readDoctype(char: Char) {
        val quote = doctypeQuote
        when {
            quote != null -> if (char == quote) doctypeQuote = null
            char == '"' || char == '\'' -> doctypeQuote = char
            char == '[' -> inInternalSubset = true
            char == ']' -> inInternalSubset = false
            char == '>' && !inInternalSubset -> state = CONTENT
        }
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
            text.append(resolved)
            state = CONTENT
        }
    }

    private fun endAttribute() {
        attributes[attributeName.toString()] = attributeValue.toString()
        state = ATTRIBUTE_END
    }

    private suspend fun startElement(isEmpty: Boolean) {
        val name = elementName.toString()
        flushText()
        collector.emit(
            SemanticEvent.Mark(
                name = name,
                isTagged = true,
                attributes = attributes.toMap()
            )
        )
        if (isEmpty) {
            collector.emit(SemanticEvent.Unmark(name = name, isTagged = true))
        } else {
            open.addLast(name)
        }
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
        state = CONTENT
    }

    /** Appends the line feed a carriage return is read as, CRLF being one. */
    private fun appendLineFeed(target: StringBuilder) {
        target.append('\n')
        skipLineFeed = true
    }

    private suspend fun flushText() {
        if (text.isEmpty()) return
        collector.emit(SemanticEvent.Text(text.toString()))
        text.clear()
    }

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

private const val CDATA_OPENING = "[CDATA["

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
    val isHex = (body.length > 1) && ((body[1] == 'x') || (body[1] == 'X'))
    val digits = body.substring(if (isHex) 2 else 1)
    if (digits.isEmpty()) return null
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

private fun Char.isXmlWhitespace(): Boolean =
    (this == ' ') || (this == '\t') || (this == '\n') || (this == '\r')

private fun Char.isXmlNameStart(): Boolean =
    isLetter() || (this == '_') || (this == ':')

private fun Char.isXmlNameChar(): Boolean =
    isXmlNameStart() || isDigit() || (this == '-') || (this == '.') || (this == '·')
