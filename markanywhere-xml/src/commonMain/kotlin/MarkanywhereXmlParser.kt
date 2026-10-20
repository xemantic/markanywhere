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
import kotlinx.coroutines.flow.*

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
 * - An **XML declaration** of any `1.x` version is read as 1.0, as the fifth
 *   edition prescribes, where some platform parsers accept only `1.0`.
 * - **Whitespace outside every element** is held only up to
 *   1024 characters; the rest of a longer run is dropped,
 *   whether it turns out to be layout or to precede a fragment's text.
 *   Next to character data — however written: literally, as a reference or in
 *   a CDATA section — it is that text's; between other markup, layout.
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
     * The events completed but not yet emitted — at most the three a single
     * character can complete (a text, then the mark and unmark of an empty
     * element), emitted by [read] before it reads on.
     */
    private val completed = ArrayList<SemanticEvent>(3)

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

    /** The target of the processing instruction being read. */
    private val instructionTarget = StringBuilder()

    /** Handed over to the mark once the tag is read, rather than copied. */
    private var attributes = LinkedHashMap<String, String>()
    private val attributeName = StringBuilder()
    private val attributeValue = StringBuilder()
    private val reference = StringBuilder()

    /** The opening a `<!` construct is being matched against, `--` or `[CDATA[`. */
    private var declarationOpening = ""

    /** How many characters of a `<!` construct have been read. */
    private var declarationLength = 0

    /**
     * Consecutive `]` read in content, held back until the following character
     * tells whether they end with the `]]>` content may not hold.
     */
    private var contentBrackets = 0

    private var documentStart: DocumentStart = BYTE_ORDER_MARK_DUE

    /** Whether the markup being read opens the document, so it may be an XML declaration. */
    private var markupOpensDocument = false

    /** Whether no element and no DOCTYPE has been read, so a DOCTYPE may follow. */
    private var isDoctypeAllowed = true

    /** Whether a leading zero of the character reference being read was skipped. */
    private var referenceZeroSkipped = false

    /** The quote which opened the attribute value being read. */
    private var valueQuote = ' '

    /** Whether the reference being read stands in an attribute value. */
    private var referenceInAttribute = false

    /** Consecutive `-` of a comment, or `]` of a CDATA section. */
    private var repeats = 0

    /** Whether the previous character of a processing instruction was `?`. */
    private var questionMark = false

    /** The pseudo-attributes of the XML declaration being read, checked once it ends. */
    private val xmlDeclaration = StringBuilder()

    /** Which part of the DOCTYPE being skipped the reader stands in. */
    private var doctypePart: DoctypePart = NAME_START

    /** The quote which opened the literal of the DOCTYPE being skipped, if any. */
    private var doctypeQuote: Char? = null

    /** The `SYSTEM` or `PUBLIC` keyword the external identifier of the DOCTYPE is matched against. */
    private var doctypeKeyword = ""

    /** How many characters of [doctypeKeyword] have been read. */
    private var doctypeKeywordLength = 0

    /** How many literals the external identifier of the DOCTYPE still needs. */
    private var doctypeLiterals = 0

    /** Whether the literal being read is a public identifier, restricted to `PubidChar`. */
    private var isPublicIdLiteral = false

    /**
     * The [COMMENT] or [PROCESSING_INSTRUCTION] of the internal subset being
     * skipped, if any.
     */
    private var subsetMarkup: ReaderState? = null

    /** How many characters of a `<!--` or `<?` opener in the internal subset have been read. */
    private var subsetOpeningLength = 0

    /** Whether the reader stands in a markup declaration of the internal subset, such as `<!ENTITY`. */
    private var isInSubsetDeclaration = false

    /** Set by a carriage return, so that the line feed of a CRLF is not read twice. */
    private var skipLineFeed = false

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
        while (index < chunk.length && !isStopped) {
            index = readUntilEvent(chunk, index)
            emitCompleted()
        }
        if (isStopped) return
        // Whitespace outside every element is held rather than flushed: only
        // what follows it tells whether it is the prolog or epilog whitespace
        // dropped below, or the start of text a fragment carries.
        if (!isOutsideElementsWhitespace()) flushText()
        emitCompleted()
    }

    /**
     * Reads [chunk] from [start] until a character completes an event, the
     * read stops or the chunk ends, returning where it got to.
     *
     * Nothing here suspends — the events are only collected in [completed],
     * for [read] to emit — so reading a character allocates nothing.
     */
    private fun readUntilEvent(chunk: String, start: Int): Int {
        var index = start
        while (index < chunk.length && !isStopped && completed.isEmpty()) {
            val runEnd = readOrdinaryRun(chunk, index)
            if (runEnd > index) {
                index = runEnd
                continue
            }
            val char = chunk[index++]
            if (documentStart == BYTE_ORDER_MARK_DUE) {
                documentStart = OPENING
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
                    break
                }
                process(high)
                if (!isStopped) process(char)
                continue
            }
            if (char.isHighSurrogate()) {
                highSurrogate = char
                continue
            }
            if (!char.isXmlCharacter()) {
                stop()
                break
            }
            process(char)
        }
        return index
    }

    /** Hands the events collected in [completed] to the collector. */
    private suspend fun emitCompleted() {
        if (completed.isEmpty()) return
        completed.forEach { collector.emit(it) }
        completed.clear()
    }

    /**
     * Appends the run of ordinary characters starting at [start] in [chunk]
     * whole rather than character by character, returning where it ends —
     * [start] itself where no such run can be taken. Ordinary is anything
     * [process] would only append: in content, text which is no markup,
     * reference or line end; in a CDATA section, anything but a `]` or a line
     * end; in an attribute value, anything but its quote, a reference, a `<`
     * or the whitespace normalized away — and never anything [read] must
     * check first. The bodies of comments, processing instructions and the
     * literals of an internal subset, dropped rather than appended, are
     * skipped the same way up to the next character which may end them.
     */
    private fun readOrdinaryRun(chunk: String, start: Int): Int {
        if (skipLineFeed || highSurrogate != null) return start
        return when (state) {
            // Whitespace outside every element needs judging until text has
            // content, after which the run is plain.
            CONTENT -> if (contentBrackets != 0 || isOutsideElementsWhitespace()) {
                start
            } else {
                chunk.appendRun(start, text) { it.isOrdinaryText() }
            }
            CDATA -> if (repeats != 0) {
                start
            } else {
                chunk.appendRun(start, text) { it.isOrdinaryCharacterData() }.also { end ->
                    if (end > start) textHasContent = true
                }
            }
            ATTRIBUTE_VALUE -> chunk.appendRun(start, attributeValue) {
                it != valueQuote && it.isOrdinaryAttributeValue()
            }
            COMMENT -> commentRunEnd(chunk, start)
            PROCESSING_INSTRUCTION -> instructionRunEnd(chunk, start)
            DOCTYPE -> when {
                subsetMarkup == COMMENT -> commentRunEnd(chunk, start)
                subsetMarkup == PROCESSING_INSTRUCTION -> instructionRunEnd(chunk, start)
                doctypePart == INTERNAL_SUBSET -> {
                    val quote = doctypeQuote ?: return start
                    chunk.skipRun(start) { it != quote && it.isXmlCharacter() }
                }
                else -> start
            }
            else -> start
        }
    }

    /**
     * Where the run of a comment's characters which cannot end it stops — at
     * the next `-`, unless one was just read.
     */
    private fun commentRunEnd(chunk: String, start: Int): Int =
        if (repeats != 0) start else chunk.skipRun(start) { it != '-' && it.isXmlCharacter() }

    /**
     * Where the run of a processing instruction's characters which cannot end
     * it stops — at the next `?`, unless one was just read.
     */
    private fun instructionRunEnd(chunk: String, start: Int): Int =
        if (questionMark) start else chunk.skipRun(start) { it != '?' && it.isXmlCharacter() }

    /**
     * Closes the elements left open — by a document which ended early, or by
     * one whose read was stopped — so that the stream stays balanced whatever
     * the input was.
     */
    suspend fun closeOpenElements() {
        // A high surrogate the document ends with is as unpaired as one
        // followed by any other character.
        if (highSurrogate != null) stop()
        // The `]` held back in a CDATA section cut short were read as content
        // as much as anything before them.
        if (state == CDATA) repeat(repeats) { appendText(']') }
        releaseContentBrackets()
        flushText()
        emitCompleted()
        collector.unmarkAll(open)
    }

    private fun process(char: Char) {
        when (state) {
            CONTENT -> when (char) {
                ']' -> contentBrackets++
                '>' -> if (contentBrackets >= 2) {
                    // Only the last two brackets are the `]]>`.
                    contentBrackets -= 2
                    releaseContentBrackets()
                    stop()
                } else {
                    releaseContentBrackets()
                    appendText(char)
                }
                else -> {
                    releaseContentBrackets()
                    when (char) {
                        '<' -> startMarkup()
                        '&' -> startReference(inAttribute = false)
                        '\r' -> appendLineFeed()
                        else -> appendText(char)
                    }
                }
            }
            MARKUP -> {
                // Only a `<!` may yet turn out to be a CDATA section.
                if (char != '!') endTextAtMarkup()
                when {
                    char == '/' -> {
                        elementName.clear()
                        state = END_TAG
                    }
                    char == '!' -> {
                        declarationLength = 0
                        state = DECLARATION
                    }
                    char == '?' -> {
                        instructionTarget.clear()
                        state = INSTRUCTION_TARGET
                    }
                    char.isXmlNameStart() -> {
                        elementName.clear()
                        elementName.append(char)
                        attributes.clear()
                        state = START_TAG
                    }
                    else -> stop()
                }
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
                char == '0' && isAtCharacterReferenceDigits() -> referenceZeroSkipped = true
                // `&#0x41;` is a decimal reference gone wrong, not a hex one.
                char == 'x' && referenceZeroSkipped -> stop()
                reference.length == MAX_REFERENCE_LENGTH -> stop()
                char == '#' && reference.isEmpty() -> reference.append(char)
                char.isXmlNameChar() -> reference.append(char)
                else -> stop()
            }
            DECLARATION -> readDeclaration(char)
            COMMENT -> if (endsComment(char, isChecked = true)) state = CONTENT
            CDATA -> readCdata(char)
            INSTRUCTION_TARGET -> readInstructionTarget(char)
            INSTRUCTION_END -> if (char == '>') state = CONTENT else stop()
            PROCESSING_INSTRUCTION -> if (endsInstruction(char)) state = CONTENT
            XML_DECLARATION -> readXmlDeclaration(char)
            DOCTYPE -> readDoctype(char)
        }
        documentStart = PASSED
    }

    /**
     * Reads the target naming a processing instruction, which every one must
     * have, followed either by whitespace or by the `?>` of an instruction
     * holding no data. The target `xml` is the XML declaration's, allowed only
     * where it opens the document; any other casing of it is reserved
     * altogether.
     */
    private fun readInstructionTarget(char: Char) {
        when {
            instructionTarget.isEmpty() -> if (char.isXmlNameStart()) instructionTarget.append(char) else stop()
            char.isXmlNameChar() -> instructionTarget.append(char)
            char.isXmlWhitespace() || char == '?' -> {
                val target = instructionTarget.toString()
                when {
                    !target.equals("xml", ignoreCase = true) -> {
                        questionMark = false
                        state = if (char == '?') INSTRUCTION_END else PROCESSING_INSTRUCTION
                    }
                    // The declaration needs at least its version.
                    target == "xml" && markupOpensDocument && char != '?' -> {
                        xmlDeclaration.clear()
                        xmlDeclaration.append(char)
                        questionMark = false
                        state = XML_DECLARATION
                    }
                    else -> stop()
                }
            }
            else -> stop()
        }
    }

    /**
     * Reads the XML declaration up to its `?>`, then checks its
     * pseudo-attributes — held, since only the whole tells whether they are
     * well formed, but never beyond [MAX_XML_DECLARATION_LENGTH].
     */
    private fun readXmlDeclaration(char: Char) {
        if (char == '>' && questionMark) {
            xmlDeclaration.setLength(xmlDeclaration.length - 1)
            if (XML_DECLARATION_PSEUDO_ATTRIBUTES.matches(xmlDeclaration)) state = CONTENT else stop()
            return
        }
        if (xmlDeclaration.length == MAX_XML_DECLARATION_LENGTH) {
            stop()
            return
        }
        questionMark = char == '?'
        xmlDeclaration.append(char)
    }

    /**
     * Identifies the `<!` construct being read from the characters seen so far
     * — `--` a comment, `[CDATA[` a section of character data, and `DOCTYPE`
     * the declaration, the only one of the three which is neither content nor
     * dropped wholesale by a single terminator. A DOCTYPE is only read once,
     * before the first element: anywhere else it is as malformed as any other
     * `<!`.
     */
    private fun readDeclaration(char: Char) {
        if (declarationLength == 0) {
            if (char != '[') endTextAtMarkup()
            declarationOpening = when {
                char == '-' -> COMMENT_DASHES
                char == '[' -> CDATA_OPENING
                char == 'D' && isDoctypeAllowed -> DOCTYPE_KEYWORD
                else -> {
                    stop()
                    return
                }
            }
        }
        if (declarationLength == declarationOpening.length) {
            // Only the DOCTYPE keyword gets here, needing whitespace after it.
            if (char.isXmlWhitespace()) startDoctype() else stop()
            return
        }
        if (char != declarationOpening[declarationLength]) {
            stop()
            return
        }
        declarationLength++
        if (declarationLength < declarationOpening.length) return
        repeats = 0
        if (declarationOpening == DOCTYPE_KEYWORD) return
        state = if (declarationOpening == COMMENT_DASHES) COMMENT else CDATA
    }

    private fun startDoctype() {
        doctypePart = NAME_START
        doctypeQuote = null
        subsetMarkup = null
        subsetOpeningLength = 0
        isInSubsetDeclaration = false
        isDoctypeAllowed = false
        state = DOCTYPE
    }

    /**
     * Reads a CDATA section as the character data it is — the boundaries are
     * not preserved, per the [XmlParser] contract.
     *
     * A `]` is held back until the following character tells whether it ends
     * the section or is content, which is what makes `]]]>` a `]` followed by
     * the end of the section.
     *
     * Outside every element the section continues the text around it, as a
     * reference does: its characters are character data even where they are
     * all whitespace, and so is the whitespace next to them.
     */
    private fun readCdata(char: Char) {
        when (char) {
            ']' -> repeats++
            '>' if repeats >= 2 -> {
                repeat(repeats - 2) { appendCharacterData(']') }
                repeats = 0
                state = CONTENT
            }
            else -> {
                repeat(repeats) { appendCharacterData(']') }
                repeats = 0
                if (char == '\r') {
                    appendCharacterData('\n')
                    skipLineFeed = true
                } else {
                    appendCharacterData(char)
                }
            }
        }
    }

    /**
     * Skips a DOCTYPE declaration, which the event model cannot carry — though
     * not blindly: up to the internal subset it is read against its
     * production, so that a stray quote cannot open a literal swallowing the
     * rest of the document.
     *
     * Its terminating `>` is found rather than assumed: a system literal may
     * hold one, and so may the internal subset — whose comments and processing
     * instructions are skipped whole, since a quote, `]` or `>` inside one is
     * neither a literal nor the end of anything. Nothing else of the subset is
     * checked — neither its declarations, nor what stands between them, nor
     * the target of an instruction or the `--` of a comment in it — as
     * `StaxXmlParser`, with DTD support off, does not check them either.
     */
    private fun readDoctype(char: Char) {
        when (subsetMarkup) {
            COMMENT -> if (endsComment(char, isChecked = false)) subsetMarkup = null
            PROCESSING_INSTRUCTION -> if (endsInstruction(char)) subsetMarkup = null
            else -> readDoctypePart(char)
        }
    }

    /**
     * Follows a comment, counting its consecutive `-` in [repeats], up to the
     * `-->` which ends it — wherever the comment stands. Where [isChecked], a
     * `--` anywhere but in that `-->` stops the read, as XML requires; in the
     * internal subset, which is not checked, the first `-->` ends it.
     */
    private fun endsComment(char: Char, isChecked: Boolean): Boolean {
        when {
            char == '>' && repeats >= 2 -> {
                repeats = 0
                return true
            }
            isChecked && repeats >= 2 -> stop()
            char == '-' -> repeats++
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

    /**
     * Follows `<!DOCTYPE` S Name (S ExternalID)? S? ('[' intSubset ']' S?)? '>',
     * where ExternalID is `SYSTEM` S SystemLiteral or `PUBLIC` S PubidLiteral S
     * SystemLiteral.
     */
    private fun readDoctypePart(char: Char) {
        when (doctypePart) {
            NAME_START -> when {
                char.isXmlWhitespace() -> {}
                char.isXmlNameStart() -> doctypePart = NAME
                else -> stop()
            }
            NAME -> when {
                char.isXmlNameChar() -> {}
                char.isXmlWhitespace() -> doctypePart = AFTER_NAME
                else -> endDoctypeHeader(char)
            }
            AFTER_NAME -> when {
                char.isXmlWhitespace() -> {}
                char == 'S' || char == 'P' -> {
                    doctypeKeyword = if (char == 'S') SYSTEM_KEYWORD else PUBLIC_KEYWORD
                    doctypeKeywordLength = 1
                    doctypePart = KEYWORD
                }
                else -> endDoctypeHeader(char)
            }
            KEYWORD -> when {
                doctypeKeywordLength < doctypeKeyword.length ->
                    if (char == doctypeKeyword[doctypeKeywordLength]) doctypeKeywordLength++ else stop()
                char.isXmlWhitespace() -> {
                    isPublicIdLiteral = doctypeKeyword == PUBLIC_KEYWORD
                    doctypeLiterals = if (isPublicIdLiteral) 2 else 1
                    doctypePart = LITERAL_START
                }
                else -> stop()
            }
            LITERAL_START -> when {
                char.isXmlWhitespace() -> {}
                char == '"' || char == '\'' -> {
                    doctypeQuote = char
                    doctypePart = LITERAL
                }
                else -> stop()
            }
            LITERAL -> when {
                char == doctypeQuote -> {
                    doctypeQuote = null
                    doctypeLiterals--
                    doctypePart = if (doctypeLiterals == 0) AFTER_EXTERNAL_ID else BETWEEN_LITERALS
                }
                isPublicIdLiteral && !char.isPublicIdChar() -> stop()
                else -> {}
            }
            BETWEEN_LITERALS -> if (char.isXmlWhitespace()) {
                isPublicIdLiteral = false
                doctypePart = LITERAL_START
            } else {
                stop()
            }
            AFTER_EXTERNAL_ID -> if (!char.isXmlWhitespace()) endDoctypeHeader(char)
            INTERNAL_SUBSET -> readInternalSubset(char)
            AFTER_INTERNAL_SUBSET -> when {
                char.isXmlWhitespace() -> {}
                char == '>' -> state = CONTENT
                else -> stop()
            }
        }
    }

    /** Reads what may follow the name or external identifier: the internal subset, or the end. */
    private fun endDoctypeHeader(char: Char) {
        when (char) {
            '[' -> doctypePart = INTERNAL_SUBSET
            '>' -> state = CONTENT
            else -> stop()
        }
    }

    /**
     * Skips the internal subset up to its `]`. Only within a markup
     * declaration — a `<!` other than a comment, up to its `>` — can a quote
     * open a literal, the one place the subset's grammar has one; a quote
     * anywhere else is skipped like any other character, so that it cannot
     * swallow the rest of the document.
     */
    private fun readInternalSubset(char: Char) {
        val quote = doctypeQuote
        if (quote != null) {
            if (char == quote) doctypeQuote = null
            return
        }
        if (startsSubsetMarkup(char)) return
        when (char) {
            '"', '\'' -> if (isInSubsetDeclaration) doctypeQuote = char
            '>' -> isInSubsetDeclaration = false
            ']' -> doctypePart = AFTER_INTERNAL_SUBSET
        }
    }

    /**
     * Follows a `<!--` or `<?` opener in the internal subset, entering the
     * comment or processing instruction it starts once complete. Any other
     * `<!` opens a markup declaration.
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
                if (subsetOpeningLength >= 2) isInSubsetDeclaration = true
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
        reference.contentEquals("#") || reference.contentEquals("#x")

    private fun startReference(inAttribute: Boolean) {
        reference.clear()
        referenceZeroSkipped = false
        referenceInAttribute = inAttribute
        state = REFERENCE
    }

    /**
     * Resolves the reference just read into the text or attribute value it
     * stands in, stopping the read when it resolves to nothing.
     */
    private fun resolveReference() {
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
            resolved.forEach { appendCharacterData(it) }
            state = CONTENT
        }
    }

    private fun endAttribute() {
        val name = attributeName.toString()
        val value = attributeValue.toString()
        // A repeated attribute, rare as it is, is reported where the value kept
        // was written: re-inserted only then, so that no other attribute pays
        // for it.
        if (attributes.put(name, value) != null) {
            attributes.remove(name)
            attributes[name] = value
        }
        state = ATTRIBUTE_END
    }

    private fun startElement(isEmpty: Boolean) {
        val name = elementName.toString()
        flushText()
        completed.add(
            SemanticEvent.Mark(
                name = name,
                isTagged = true,
                attributes = if (attributes.isEmpty()) emptyMap() else attributes
            )
        )
        if (attributes.isNotEmpty()) attributes = LinkedHashMap()
        isDoctypeAllowed = false
        if (isEmpty) {
            completed.add(SemanticEvent.Unmark(name = name, isTagged = true))
        } else {
            open.addLast(name)
        }
        textHasContent = false
        state = CONTENT
    }

    private fun endElement() {
        flushText()
        if (!open.lastOrNull().contentEquals(elementName)) {
            // An end tag which does not close the innermost open element cannot
            // be reported without emitting an unmark crossing a mark, so the
            // read gives up here and lets the open elements be closed in order.
            stop()
            return
        }
        completed.add(SemanticEvent.Unmark(name = open.removeLast(), isTagged = true))
        textHasContent = false
        state = CONTENT
    }

    private fun startMarkup() {
        markupOpensDocument = documentStart == OPENING
        state = MARKUP
    }

    /**
     * Called once the markup being entered is known to be no CDATA section.
     * Outside every element the text read so far ends here, so that the
     * whitespace between this markup and the next is judged on its own —
     * dropped as layout even after text a fragment carries.
     */
    private fun endTextAtMarkup() {
        if (open.isEmpty()) {
            flushText()
            textHasContent = false
        }
    }

    /** Appends the `]` held back in [contentBrackets], once they turn out to be text. */
    private fun releaseContentBrackets() {
        repeat(contentBrackets) { appendText(']') }
        contentBrackets = 0
    }

    private fun appendText(char: Char) {
        if (char.isXmlWhitespace()) {
            if (isOutsideElementsWhitespace() && text.length >= MAX_LAYOUT_WHITESPACE) return
        } else {
            textHasContent = true
        }
        text.append(char)
    }

    /**
     * Appends a character written as character data — in a CDATA section or
     * as a reference — which is never layout, even where it is whitespace.
     */
    private fun appendCharacterData(char: Char) {
        textHasContent = true
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
    private fun flushText() {
        if (text.isEmpty()) return
        if (!isOutsideElementsWhitespace()) {
            completed.add(SemanticEvent.Text(text.toString()))
        }
        text.clear()
    }

    private fun isOutsideElementsWhitespace(): Boolean =
        open.isEmpty() && !textHasContent

    /** Ends the read, keeping everything understood up to this point. */
    private fun stop() {
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
    INSTRUCTION_TARGET,
    INSTRUCTION_END,
    COMMENT,
    CDATA,
    PROCESSING_INSTRUCTION,
    XML_DECLARATION,
    DOCTYPE
}

/** How far into the document the reader has got — what a BOM and an XML declaration depend on. */
private enum class DocumentStart {
    /** No character has been read, so a byte order mark may come. */
    BYTE_ORDER_MARK_DUE,

    /** Nothing but a byte order mark has been read, so an XML declaration may come. */
    OPENING,
    PASSED
}

/** The parts of a DOCTYPE declaration, up to the end of its internal subset. */
private enum class DoctypePart {
    NAME_START,
    NAME,
    AFTER_NAME,
    KEYWORD,
    LITERAL_START,
    LITERAL,
    BETWEEN_LITERALS,
    AFTER_EXTERNAL_ID,
    INTERNAL_SUBSET,
    AFTER_INTERNAL_SUBSET
}

private const val CDATA_OPENING = "[CDATA["

private const val COMMENT_DASHES = "--"

private const val DOCTYPE_KEYWORD = "DOCTYPE"

private const val COMMENT_OPENING = "<!--"

private const val SYSTEM_KEYWORD = "SYSTEM"

private const val PUBLIC_KEYWORD = "PUBLIC"

/**
 * The pseudo-attributes an XML declaration may hold after its target, in the
 * order they must be written: `version` (`1.x`, which XML 1.0 fifth edition
 * reads as 1.0), then optionally `encoding` and `standalone`. The encoding is
 * not checked against `EncName`: the characters are already decoded, so it
 * names nothing, and `StaxXmlParser` does not check it either.
 *
 * No group repeats, so matching cannot recurse once per repetition.
 */
private val XML_DECLARATION_PSEUDO_ATTRIBUTES = Regex(
    "^[ \\t\\r\\n]+version[ \\t\\r\\n]*=[ \\t\\r\\n]*(?:\"1\\.[0-9]+\"|'1\\.[0-9]+')" +
        "(?:[ \\t\\r\\n]+encoding[ \\t\\r\\n]*=[ \\t\\r\\n]*(?:\"[^\"]*\"|'[^']*'))?" +
        "(?:[ \\t\\r\\n]+standalone[ \\t\\r\\n]*=[ \\t\\r\\n]*(?:\"(?:yes|no)\"|'(?:yes|no)'))?" +
        "[ \\t\\r\\n]*$"
)

/** How much of an XML declaration is held — far more than any well-formed one needs. */
private const val MAX_XML_DECLARATION_LENGTH = 1024

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
 * characters it reads as markup, a reference or a line end, and those of the
 * `]]>` content may not hold.
 */
private fun Char.isOrdinaryText(): Boolean =
    (this != '<') && (this != '&') && (this != '\r') && (this != ']') && (this != '>') &&
        isXmlCharacter()

/**
 * Whether this UTF-16 unit is one [XmlDocumentReader.process] would only append
 * in a CDATA section — any [isXmlCharacter] but the `]` of a possible end and
 * the carriage return read as a line feed.
 */
private fun Char.isOrdinaryCharacterData(): Boolean =
    (this != ']') && (this != '\r') && isXmlCharacter()

/**
 * Whether this UTF-16 unit is one [XmlDocumentReader.process] would only append
 * in an attribute value — any [isXmlCharacter] but a reference, a `<` and the
 * whitespace normalized to a space; the closing quote is left to the caller.
 */
private fun Char.isOrdinaryAttributeValue(): Boolean =
    (this != '&') && (this != '<') && (this != '\r') && (this != '\n') && (this != '\t') &&
        isXmlCharacter()

/**
 * Appends to [target] the run of characters from [start] which are
 * [isOrdinary], returning the index the run ends at.
 */
private inline fun String.appendRun(
    start: Int,
    target: StringBuilder,
    isOrdinary: (Char) -> Boolean
): Int {
    val end = skipRun(start, isOrdinary)
    target.appendRange(this, start, end)
    return end
}

/** The index the run of characters from [start] which are [isSkipped] ends at. */
private inline fun String.skipRun(
    start: Int,
    isSkipped: (Char) -> Boolean
): Int {
    var end = start
    while (end < length && isSkipped(this[end])) end++
    return end
}

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

private fun Char.isAsciiHexDigit(): Boolean =
    isAsciiDigit() || (this in 'a'..'f') || (this in 'A'..'F')

/** The `PubidChar` production of XML 1.0, the characters a public identifier may hold. */
private fun Char.isPublicIdChar(): Boolean =
    (this == ' ') || (this == '\r') || (this == '\n') ||
        (this in 'a'..'z') || (this in 'A'..'Z') || (this in '0'..'9') ||
        (this in "-'()+,./:=?;!*#@\$_%")

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
