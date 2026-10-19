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
 * resume from. Where nothing bounds a construct but what follows it — the
 * whitespace outside every element, which is layout only if markup comes next —
 * the hold is capped. This is what a pull parser such as `StaxXmlParser` can only
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
 * - **Whitespace outside every element** is held only up to
 *   1024 characters; the rest of a longer run is dropped,
 *   whether it turns out to be layout or to precede a fragment's text.
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

    private var state: ReaderState = CONTENT

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

    /** Handed over to the mark once the tag is read, rather than copied. */
    private var attributes = LinkedHashMap<String, String>()
    private val attributeName = StringBuilder()
    private val attributeValue = StringBuilder()
    private val reference = StringBuilder()

    /** The opening a `<!` construct is being matched against, `--` or `[CDATA[`. */
    private var declarationOpening = ""

    /** How many characters of a `<!` construct have been read. */
    private var declarationLength = 0

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

    /**
     * The [COMMENT] or [PROCESSING_INSTRUCTION] of the internal subset being
     * skipped, if any.
     */
    private var subsetMarkup: ReaderState? = null

    /** How many characters of a `<!--` or `<?` opener in the internal subset have been read. */
    private var subsetOpeningLength = 0

    /** Set by a carriage return, so that the line feed of a CRLF is not read twice. */
    private var skipLineFeed = false

    /** Whether no character has been read yet, so a byte order mark is due. */
    private var atStart = true

    /**
     * A high surrogate waiting for the low one which makes it a character —
     * held, since a chunk boundary may fall between the two halves.
     */
    private var highSurrogate: Char? = null

    /**
     * Reads [chunk], emitting every event it completes.
     *
     * The text read since the last event is flushed when the chunk ends, so
     * that text arriving over many chunks does not accumulate — the split
     * points are of no consequence, the contract leaving text granularity to
     * the implementation.
     */
    suspend fun read(chunk: String) {
        var index = 0
        while (index < chunk.length) {
            if (isStopped) return
            val textEnd = ordinaryTextEnd(chunk, index)
            if (textEnd > index) {
                text.appendRange(chunk, index, textEnd)
                index = textEnd
                continue
            }
            val char = chunk[index++]
            if (atStart) {
                atStart = false
                // Skipped per the XmlParser contract: a UTF-8 file decoded the
                // usual way keeps its BOM, which is no character data.
                if (char == '\uFEFF') continue
            }
            if (skipLineFeed) {
                skipLineFeed = false
                if (char == '\n') continue
            }
            val high = highSurrogate
            if (high != null) {
                highSurrogate = null
                if (!char.isLowSurrogate()) {
                    stop()
                    return
                }
                process(high)
                process(char)
                continue
            }
            if (char.isHighSurrogate()) {
                highSurrogate = char
                continue
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
     * Where the run of ordinary character data starting at [start] in [chunk]
     * ends — [start] itself unless the reader stands in an element's content,
     * where such a run can be appended whole rather than character by
     * character. Ordinary is anything [process] would only append: no markup,
     * reference or line end, and nothing [read] must check first.
     */
    private fun ordinaryTextEnd(chunk: String, start: Int): Int {
        // Inside an element, whitespace needs no judging, so the run is plain.
        if (state != CONTENT || open.isEmpty() || atStart || skipLineFeed || highSurrogate != null) {
            return start
        }
        var end = start
        while (end < chunk.length && chunk[end].isOrdinaryText()) end++
        return end
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
                '<' -> startMarkup()
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
                    declarationLength = 0
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
            // Leading zeros of a character reference are skipped, so that a
            // body longer than any which resolves can stop the read at once.
            REFERENCE -> when {
                char == ';' -> resolveReference()
                char == '0' && isAtCharacterReferenceDigits() -> {}
                reference.length == MAX_REFERENCE_LENGTH -> stop()
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
        if (declarationLength == 0) {
            declarationOpening = when {
                char == '-' -> COMMENT_DASHES
                char == '[' -> CDATA_OPENING
                char.isXmlNameStart() -> {
                    doctypeQuote = null
                    inInternalSubset = false
                    subsetMarkup = null
                    subsetOpeningLength = 0
                    state = DOCTYPE
                    return
                }
                else -> {
                    stop()
                    return
                }
            }
        }
        if (char != declarationOpening[declarationLength]) {
            stop()
            return
        }
        declarationLength++
        if (declarationLength < declarationOpening.length) return
        repeats = 0
        if (declarationOpening == COMMENT_DASHES) {
            state = COMMENT
        } else {
            // Character data even where it is all whitespace, so never
            // dropped as the layout outside every element.
            textHasContent = true
            state = CDATA
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
    private suspend fun readCdata(char: Char) {
        when {
            char == ']' -> repeats++
            char == '>' && repeats >= 2 -> {
                repeat(repeats - 2) { appendText(']') }
                repeats = 0
                state = CONTENT
                // Outside every element the section ends a piece of markup,
                // so the whitespace after it is judged on its own, as it is
                // after a comment.
                if (open.isEmpty()) {
                    flushText()
                    textHasContent = false
                }
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
            COMMENT -> if (endsComment(char)) subsetMarkup = null
            PROCESSING_INSTRUCTION -> if (endsInstruction(char)) subsetMarkup = null
            else -> readDoctypeDeclaration(char)
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
            subsetOpeningLength = 1
            return false
        }
        if (subsetOpeningLength == 0) return false
        val markup: ReaderState = when {
            subsetOpeningLength == 1 && char == '?' -> PROCESSING_INSTRUCTION
            char == COMMENT_OPENING[subsetOpeningLength] -> {
                subsetOpeningLength++
                if (subsetOpeningLength < COMMENT_OPENING.length) return false
                COMMENT
            }
            else -> {
                subsetOpeningLength = 0
                return false
            }
        }
        subsetOpeningLength = 0
        repeats = 0
        questionMark = false
        subsetMarkup = markup
        return true
    }

    /** Whether only the `#` or `#x` of a character reference has been read. */
    private fun isAtCharacterReferenceDigits(): Boolean =
        reference.isNotEmpty() && reference[0] == '#' &&
            reference.length == (if (reference.length == 2 && reference[1] == 'x') 2 else 1)

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
            // A reference to whitespace is character data all the same.
            textHasContent = true
            resolved.forEach { appendText(it) }
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
                attributes = if (attributes.isEmpty()) emptyMap() else attributes
            )
        )
        if (attributes.isNotEmpty()) attributes = LinkedHashMap()
        if (isEmpty) {
            collector.emit(SemanticEvent.Unmark(name = name, isTagged = true))
        } else {
            open.addLast(name)
        }
        textHasContent = false
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

    /**
     * Enters markup. Outside every element the text read so far ends here, so
     * that the whitespace between this markup and the next is judged on its
     * own — dropped as layout even after text a fragment carries.
     */
    private suspend fun startMarkup() {
        if (open.isEmpty()) {
            flushText()
            textHasContent = false
        }
        state = MARKUP
    }

    private fun appendText(char: Char) {
        if (char.isXmlWhitespace()) {
            if (isOutsideElementsWhitespace() && text.length >= MAX_LAYOUT_WHITESPACE) return
        } else {
            textHasContent = true
        }
        text.append(char)
    }

    /** Appends the line feed a carriage return is read as, CRLF being one. */
    private fun appendLineFeed() {
        appendText('\n')
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

private const val CDATA_OPENING = "[CDATA["

private const val COMMENT_DASHES = "--"

private const val COMMENT_OPENING = "<!--"

/**
 * The longest reference body which can resolve, leading zeros aside —
 * `#x10FFFF` or `#1114111`; the predefined entity names are shorter.
 */
private const val MAX_REFERENCE_LENGTH = 8

/**
 * How much of a run of whitespace outside every element is held while it is
 * not yet known whether it is layout — far more than any document's layout,
 * so that only a pathological run is cut.
 */
private const val MAX_LAYOUT_WHITESPACE = 1024

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
 * Whether this UTF-16 unit is a character a document may hold — a surrogate
 * half is not, the pair it belongs to being judged by [XmlDocumentReader.read].
 */
private fun Char.isXmlCharacter(): Boolean =
    (this == '\t') || (this == '\n') || (this == '\r') ||
        (this in '\u0020'..'\uD7FF') ||
        (this in '\uE000'..'\uFFFD')

/**
 * Whether this UTF-16 unit is character data [XmlDocumentReader.process] would
 * only append in an element's content — [isXmlCharacter], except for the
 * characters it reads as markup, a reference or a line end.
 */
private fun Char.isOrdinaryText(): Boolean =
    (this != '<') && (this != '&') && (this != '\r') && isXmlCharacter()

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
