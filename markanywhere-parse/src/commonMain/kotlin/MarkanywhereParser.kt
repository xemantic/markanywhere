/*
 * Copyright 2025 Kazimierz Pogoda / Xemantic
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

package com.xemantic.markanywhere.parse

import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.flow.SemanticEventScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

public interface MarkanywhereParser {

    public fun parse(
        chunks: Flow<String>
    ): Flow<SemanticEvent>

}

public fun Flow<String>.parse(
    parser: MarkanywhereParser
): Flow<SemanticEvent> = parser.parse(this)

public class DefaultMarkanywhereParser : MarkanywhereParser {

    override fun parse(
        chunks: Flow<String>
    ): Flow<SemanticEvent> = flow {
        val state = ParserState(
            scope = SemanticEventScope(
                collector = this
            )
        )
        chunks.collect { chunk ->
            state.processChunk(chunk)
        }
        state.finalize()
    }

}

private class ParserState(
    private val scope: SemanticEventScope
) {

    // Block modes
    private sealed interface BlockMode {
        data object Start : BlockMode
        data class Heading(val level: Int) : BlockMode
        data object Paragraph : BlockMode
        data class CodeBlock(val backticks: Int) : BlockMode
        data object UnorderedList : BlockMode
        data object OrderedList : BlockMode
        data object Blockquote : BlockMode
        data object BlockquoteList : BlockMode
        data object MathBlock : BlockMode
        data object Table : BlockMode
        data object TableBody : BlockMode
        data class CustomMarkup(val tagName: String) : BlockMode
    }

    private var blockMode: BlockMode = BlockMode.Start
    private var lineBuffer = StringBuilder()
    private var atLineStart = true
    private var codeBlockBackticks = 0
    private var codeBlockPendingLine: String? = null
    private var tableHasBody = false
    private var inListItem = false
    private var inBlockquoteParagraph = false

    // Inline state
    private var bold = false
    private var italic = false
    private var code = false
    private var strikethrough = false
    private var subscript = false
    private var superscript = false
    private var highlight = false
    private var math = false
    private var inLink = false
    private var inLinkUrl = false
    private var linkText = StringBuilder()
    private var linkUrl = StringBuilder()
    private var linkTitle = StringBuilder()
    private var inImage = false
    private var imageAlt = StringBuilder()
    private var imageUrl = StringBuilder()
    private var escaped = false
    private var doubleBacktickCode = false
    private var inlineBuffer = StringBuilder()

    // Custom markup state
    private var customMarkupClosingBuffer = StringBuilder()
    private var customMarkupInClosingTag = false
    private var customMarkupSkipFirstNewline = false
    private var customMarkupPendingNewline = false

    suspend fun process(char: Char) {
        scope.process(char)
    }

    suspend fun processChunk(chunk: String) {
        scope.processChunk(chunk)
    }

    // Control characters that require special handling in inline text
    private fun isInlineControlChar(char: Char): Boolean = when (char) {
        '*', '_', '`', '~', '^', '=', '$', '[', '!', '<', '\\', '\n' -> true
        else -> false
    }

    /**
     * Process inline content in bulk, emitting maximal text runs.
     * This is used when replaying buffered content (e.g., from lineBuffer)
     * after block-level ambiguity has been resolved.
     */
    private suspend fun SemanticEventScope.processInlineContent(content: String) {
        if (content.isEmpty()) return

        var index = 0
        while (index < content.length) {
            // Check if we can fast-path based on current inline state
            val fastPathEnd = getInlineFastPathEnd(content, index)

            if (fastPathEnd > index) {
                // Emit the safe substring in one go
                +content.substring(index, fastPathEnd)
                index = fastPathEnd
                continue
            }

            // Fall back to character-by-character for control characters
            processInlineChar(content[index])
            index++
        }
    }

    /**
     * Returns the end index for fast-path text emission within inline content.
     * Similar to getFastPathEnd but specifically for inline processing context.
     */
    private fun getInlineFastPathEnd(content: String, startIndex: Int): Int {
        if (escaped) return startIndex

        // Check for states that require character-by-character processing
        when {
            code && !doubleBacktickCode -> return findNextChar(content, startIndex, '`')
            code && doubleBacktickCode -> return startIndex
            math -> return findNextChar(content, startIndex, '$')
            inLinkUrl -> return startIndex
            inLink -> return startIndex
            inImage -> return startIndex
        }

        // If there's pending inline buffer content, can't fast-path
        if (inlineBuffer.isNotEmpty()) return startIndex

        // Scan for next control character
        return findNextControlChar(content, startIndex)
    }

    private suspend fun SemanticEventScope.processChunk(chunk: String) {
        if (chunk.isEmpty()) return

        var index = 0
        while (index < chunk.length) {
            // Special handling for CustomMarkup skip-first-newline at chunk level
            // This must be handled before fast-path to avoid splitting chunks
            if (blockMode is BlockMode.CustomMarkup && customMarkupSkipFirstNewline) {
                customMarkupSkipFirstNewline = false
                if (chunk[index] == '\n') {
                    index++
                    continue  // Skip this newline and continue
                }
                // Not a newline - continue with normal processing
            }

            // Determine if we can fast-path based on current state
            val fastPathResult = getFastPathEnd(chunk, index)

            if (fastPathResult > index) {
                // For custom markup, emit pending newline before content
                if (blockMode is BlockMode.CustomMarkup && customMarkupPendingNewline) {
                    customMarkupPendingNewline = false
                    +'\n'
                }
                // Emit the safe substring in one go
                +chunk.substring(index, fastPathResult)
                index = fastPathResult
                continue
            }

            // Fall back to character-by-character processing
            process(chunk[index])
            index++
        }
    }

    /**
     * Returns the end index for fast-path text emission, or [startIndex] if no fast-path is possible.
     *
     * Fast-path is possible when we can emit multiple characters without state changes.
     * This depends on the current block mode and inline formatting state.
     */
    private fun getFastPathEnd(chunk: String, startIndex: Int): Int {
        if (escaped) return startIndex

        // Check for fast-path based on current inline state (takes priority)
        // These are "isolated" contexts where only specific chars are control
        when {
            // Inside code (single backtick) - only ` is control, chars are emitted directly
            code && !doubleBacktickCode -> return findNextChar(chunk, startIndex, '`')
            // Inside code (double backtick) - chars go to inlineBuffer, no fast-path
            code && doubleBacktickCode -> return startIndex
            // Inside math - only $ is control, chars are emitted directly
            math -> return findNextChar(chunk, startIndex, '$')
            // Inside link/image URL - chars go to linkUrl/imageUrl buffer, no fast-path
            inLinkUrl -> return startIndex
            // Inside link text - chars go to linkText buffer, no fast-path
            inLink -> return startIndex
            // Inside image alt - chars go to imageAlt buffer, no fast-path
            inImage -> return startIndex
        }

        // Custom markup mode - < and \n are control (< starts potential closing tag, \n needs buffering)
        // Also disable fast-path when we need to skip the first newline or have pending newline
        if (blockMode is BlockMode.CustomMarkup && !customMarkupInClosingTag && !customMarkupSkipFirstNewline && !customMarkupPendingNewline) {
            return findNextCustomMarkupControl(chunk, startIndex)
        }

        // If there's pending inline buffer content, can't fast-path (ambiguity)
        // This includes autolinks (<...>) which need character-by-character buffering
        if (inlineBuffer.isNotEmpty()) return startIndex

        // Check block mode for fast-path eligibility
        val canFastPath = when (blockMode) {
            BlockMode.Paragraph -> true
            is BlockMode.Heading -> true
            BlockMode.UnorderedList -> inListItem
            BlockMode.OrderedList -> inListItem
            BlockMode.Blockquote -> inBlockquoteParagraph && !atLineStart
            BlockMode.BlockquoteList -> inListItem
            else -> false
        }

        if (!canFastPath) return startIndex

        // In inline text (possibly with active formatting like bold/italic) - scan for any control character
        return findNextControlChar(chunk, startIndex)
    }

    private fun findNextChar(chunk: String, startIndex: Int, target: Char): Int {
        for (i in startIndex until chunk.length) {
            if (chunk[i] == target) {
                return i
            }
        }
        return chunk.length
    }

    private fun findNextControlChar(chunk: String, startIndex: Int): Int {
        for (i in startIndex until chunk.length) {
            if (isInlineControlChar(chunk[i])) {
                return i
            }
        }
        return chunk.length
    }

    private fun findNextCustomMarkupControl(chunk: String, startIndex: Int): Int {
        for (i in startIndex until chunk.length) {
            val c = chunk[i]
            if (c == '<' || c == '\n') {
                return i
            }
        }
        return chunk.length
    }

    private suspend fun SemanticEventScope.process(char: Char) {
        when (val mode = blockMode) {
            BlockMode.Start -> processStart(char)
            is BlockMode.Heading -> processHeading(char, mode.level)
            BlockMode.Paragraph -> processParagraph(char)
            is BlockMode.CodeBlock -> processCodeBlock(char, mode.backticks)
            BlockMode.UnorderedList -> processUnorderedList(char)
            BlockMode.OrderedList -> processOrderedList(char)
            BlockMode.Blockquote -> processBlockquote(char)
            BlockMode.BlockquoteList -> processBlockquoteList(char)
            BlockMode.MathBlock -> processMathBlock(char)
            BlockMode.Table -> processTable(char)
            BlockMode.TableBody -> processTableBody(char)
            is BlockMode.CustomMarkup -> processCustomMarkup(char, mode.tagName)
        }
    }

    private suspend fun SemanticEventScope.processStart(
        char: Char
    ) {
        // Handle newline - process buffered line
        if (char == '\n') {
            val line = lineBuffer.toString()
            lineBuffer.clear()

            if (line.isEmpty()) {
                // Empty line, stay in start
                return
            }

            when {
                // Code block opening: ```lang
                line.matches(Regex("^```[a-zA-Z0-9]*$")) -> {
                    val lang = line.removePrefix("```").trim()
                    val attrs = if (lang.isNotEmpty()) {
                        mapOf("class" to "code lang-$lang")
                    } else {
                        mapOf("class" to "code")
                    }
                    mark("pre", attributes = attrs)
                    codeBlockBackticks = 3
                    blockMode = BlockMode.CodeBlock(3)
                }
                // Horizontal rule: ---
                line.matches(Regex("^-{3,}$")) -> {
                    "hr" {}
                }
                // Math block: $$
                line == "$$" -> {
                    mark("math", attributes = mapOf("display" to "block"))
                    blockMode = BlockMode.MathBlock
                }
                // Table row
                line.startsWith("|") -> {
                    mark("table")
                    mark("thead")
                    "tr" {
                        emitTableRow(line, isHeader = true)
                    }
                    tableHasBody = false
                    blockMode = BlockMode.Table
                }
                // Custom markup opening tag: <namespace:name ...>
                line.startsWith("<") && line.endsWith(">") && !line.startsWith("</") -> {
                    val parsed = parseCustomMarkupOpeningTag(line)
                    if (parsed != null) {
                        val (tagName, attributes) = parsed
                        mark(tagName, isTag = true, attributes = attributes)
                        customMarkupSkipFirstNewline = false  // Already consumed by line-based detection
                        customMarkupPendingNewline = false  // Reset any stale state from previous custom markup
                        blockMode = BlockMode.CustomMarkup(tagName)
                    } else {
                        // Not a valid custom markup tag, treat as paragraph
                        "p" {
                            processInlineContent(line)
                            flushInline()
                        }
                    }
                }
                // Single line paragraph
                else -> {
                    "p" {
                        processInlineContent(line)
                        flushInline()
                    }
                }
            }
            return
        }

        lineBuffer.append(char)
        // Check for block-level patterns
        val line = lineBuffer.toString()
        when {
            // Headings: # ## ### etc.
            line.matches(Regex("^#{1,6} $")) -> {
                val level = line.count { it == '#' }
                mark("h$level")
                lineBuffer.clear()
                blockMode = BlockMode.Heading(level)
            }
            line.matches(Regex("^#{1,6}$")) -> {
                // Keep buffering to see if space follows
            }
            line.matches(Regex("^#{7,}.*")) -> {
                // Too many #, treat as paragraph
                mark("p")
                processInlineContent(line)
                lineBuffer.clear()
                blockMode = BlockMode.Paragraph
            }
            // Code block: ```
            line == "```" || line.matches(Regex("^```[a-zA-Z0-9]*$")) -> {
                // Keep buffering for newline
            }
            // Horizontal rule: --- keep buffering
            line.matches(Regex("^-{1,}$")) -> {
                // Keep buffering (could be HR or list start)
            }
            // Task list: - [ ] or - [x]  (check BEFORE simple list item)
            line.matches(Regex("^- \\[ \\] $")) -> {
                mark("ul")
                mark("li")
                "input"("type" to "checkbox") {}
                lineBuffer.clear()
                inListItem = true
                blockMode = BlockMode.UnorderedList
            }
            line.matches(Regex("^- \\[x\\] $")) -> {
                mark("ul")
                mark("li")
                "input"("type" to "checkbox", "checked" to "true") {}
                unmark("input")
                lineBuffer.clear()
                inListItem = true
                blockMode = BlockMode.UnorderedList
            }
            // Keep buffering for potential task list
            line.matches(Regex("^- \\[[ x]?\\]?$")) || line == "- [" || line == "- " -> {
                // Keep buffering - could be task list
            }
            // Regular unordered list: "- X" where X is not [
            line.startsWith("- ") && line.length > 2 && line[2] != '[' -> {
                mark("ul")
                mark("li")
                // Emit the content after "- "
                processInlineContent(line.substring(2))
                lineBuffer.clear()
                inListItem = true
                blockMode = BlockMode.UnorderedList
            }
            // Ordered list: 1. item
            line.matches(Regex("^\\d+\\. $")) -> {
                mark("ol")
                mark("li")
                lineBuffer.clear()
                inListItem = true
                blockMode = BlockMode.OrderedList
            }
            line.matches(Regex("^\\d+\\.?$")) -> {
                // Keep buffering
            }
            // Blockquote: > text
            line == "> " -> {
                mark("blockquote")
                mark("p")
                inBlockquoteParagraph = true
                lineBuffer.clear()
                atLineStart = false
                blockMode = BlockMode.Blockquote
            }
            line == ">" -> {
                // Keep buffering - might be "> " or just ">"
            }
            // Table: | header |
            line.startsWith("|") -> {
                // Keep buffering until newline
            }
            // Math block: $$
            line == "$$" || line == "$" -> {
                // Keep buffering
            }
            // Custom markup: detect opening tag incrementally when > is seen
            line.startsWith("<") && line.endsWith(">") && !line.startsWith("</") -> {
                // Try to parse as custom markup opening tag immediately
                val parsed = parseCustomMarkupOpeningTag(line)
                if (parsed != null) {
                    val (tagName, attributes) = parsed
                    mark(tagName, isTag = true, attributes = attributes)
                    lineBuffer.clear()
                    customMarkupSkipFirstNewline = true  // Skip newline that may follow immediately
                    customMarkupPendingNewline = false  // Reset any stale state from previous custom markup
                    blockMode = BlockMode.CustomMarkup(tagName)
                }
                // If not valid custom markup, keep buffering until newline
            }
            line.startsWith("<") -> {
                // Keep buffering - could become custom markup tag
            }
            !line.first().let { it == '#' || it == '`' || it == '-' || it == '>' || it == '|' || it == '$' || it.isDigit() } -> {
                // Not a special line start, begin paragraph
                mark("p")
                processInlineContent(line)
                lineBuffer.clear()
                blockMode = BlockMode.Paragraph
            }
        }
    }

    private suspend fun SemanticEventScope.processHeading(
        char: Char,
        level: Int
    ) {
        when (char) {
            '\n' -> {
                flushInline()
                unmark("h$level")
                blockMode = BlockMode.Start
            }
            else -> processInlineChar(char)
        }
    }

    private suspend fun SemanticEventScope.processParagraph(
        char: Char
    ) {
        when (char) {
            '\n' -> {
                flushInline()
                unmark("p")
                lineBuffer.clear()
                atLineStart = true
                blockMode = BlockMode.Start
            }
            else -> {
                atLineStart = false
                processInlineChar(char)
            }
        }
    }

    private suspend fun SemanticEventScope.processCodeBlock(
        char: Char,
        backticks: Int
    ) {
        if (char == '\n') {
            val line = lineBuffer.toString()
            lineBuffer.clear()
            if (line.trimEnd() == "`".repeat(backticks)) {
                // Closing fence - emit pending line without trailing newline
                if (codeBlockPendingLine != null) {
                    +codeBlockPendingLine!!
                    codeBlockPendingLine = null
                }
                unmark("pre")
                blockMode = BlockMode.Start
            } else {
                // Emit previous pending line with newline, then store this line
                if (codeBlockPendingLine != null) {
                    +(codeBlockPendingLine + "\n")
                }
                codeBlockPendingLine = line
            }
        } else {
            lineBuffer.append(char)
        }
    }

    private suspend fun SemanticEventScope.processUnorderedList(
        char: Char
    ) {

        if (char == '\n') {
            if (inListItem) {
                flushInline()
                unmark("li")
                inListItem = false
            }
            lineBuffer.clear()
            return
        }

        if (!inListItem) {
            lineBuffer.append(char)
            val line = lineBuffer.toString()
            when {
                // Task list: - [ ] or - [x]  (check BEFORE simple list item)
                line.matches(Regex("^- \\[ \\] $")) -> {
                    mark("li")
                    "input"("type" to "checkbox") {}
                    unmark("input")
                    lineBuffer.clear()
                    inListItem = true
                }
                line.matches(Regex("^- \\[x\\] $")) -> {
                    mark("li")
                    "input"("type" to "checkbox", "checked" to "true") {}
                    lineBuffer.clear()
                    inListItem = true
                }
                // Keep buffering for potential task list
                line.matches(Regex("^- \\[[ x]?\\]?$")) || line == "- [" || line == "- " || line == "-" -> {
                    // Keep buffering
                }
                // Regular list item: "- X" where X is not [
                line.startsWith("- ") && line.length > 2 && line[2] != '[' -> {
                    mark("li")
                    // Emit the content after "- "
                    processInlineContent(line.substring(2))
                    lineBuffer.clear()
                    inListItem = true
                }
                line.isEmpty() -> {
                    // End of list
                    unmark("ul")
                    blockMode = BlockMode.Start
                }
                line.startsWith("#") -> {
                    // Header after list
                    unmark("ul")
                    lineBuffer.clear()
                    blockMode = BlockMode.Start
                    for (c in line) {
                        process(c)
                    }
                }
                else -> {
                    // End of list, start new block
                    unmark("ul")
                    lineBuffer.clear()
                    blockMode = BlockMode.Start
                    for (c in line) {
                        process(c)
                    }
                }
            }
        } else {
            processInlineChar(char)
        }
    }

    private suspend fun SemanticEventScope.processOrderedList(
        char: Char
    ) {
        if (char == '\n') {
            if (inListItem) {
                flushInline()
                unmark("li")
                inListItem = false
            }
            lineBuffer.clear()
            return
        }

        if (!inListItem) {
            lineBuffer.append(char)
            val line = lineBuffer.toString()
            when {
                line.matches(Regex("^\\d+\\. $")) -> {
                    mark("li")
                    lineBuffer.clear()
                    inListItem = true
                }
                line.matches(Regex("^\\d+\\.?$")) -> {
                    // Keep buffering
                }
                line.isEmpty() -> {
                    unmark("ol")
                    blockMode = BlockMode.Start
                }
                line.startsWith("#") -> {
                    unmark("ol")
                    lineBuffer.clear()
                    blockMode = BlockMode.Start
                    for (c in line) {
                        process(c)
                    }
                }
                else -> {
                    unmark("ol")
                    lineBuffer.clear()
                    blockMode = BlockMode.Start
                    for (c in line) {
                        process(c)
                    }
                }
            }
        } else {
            processInlineChar(char)
        }
    }

    private var blockquotePendingNewline = false

    private suspend fun SemanticEventScope.processBlockquote(
        char: Char
    ) {
        if (char == '\n') {
            if (inBlockquoteParagraph) {
                // Don't emit newline yet - might be followed by list
                blockquotePendingNewline = true
            }
            lineBuffer.clear()
            atLineStart = true
            return
        }

        if (atLineStart) {
            lineBuffer.append(char)
            val line = lineBuffer.toString()

            when {
                line == ">" || line == "> " || line == "> -" -> {
                    // Continue blockquote marker - keep buffering (including potential list start)
                }
                line.startsWith("> - ") -> {
                    // List in blockquote - discard pending newline
                    blockquotePendingNewline = false
                    if (inBlockquoteParagraph) {
                        flushInline()
                        unmark("p")
                        inBlockquoteParagraph = false
                    }
                    mark("ul")
                    mark("li")
                    // Process content after "> - "
                    processInlineContent(line.removePrefix("> - "))
                    lineBuffer.clear()
                    inListItem = true
                    blockMode = BlockMode.BlockquoteList
                }
                line.startsWith("> ") && line.length > 2 -> {
                    // Content after "> " - emit pending newline if continuing paragraph
                    if (blockquotePendingNewline && inBlockquoteParagraph) {
                        +"\n"
                        blockquotePendingNewline = false
                    }
                    if (!inBlockquoteParagraph) {
                        mark("p")
                        inBlockquoteParagraph = true
                    }
                    // Process content after "> "
                    processInlineContent(line.removePrefix("> "))
                    lineBuffer.clear()
                    atLineStart = false
                }
                !line.startsWith(">") -> {
                    // End blockquote - line doesn't start with >
                    blockquotePendingNewline = false
                    if (inBlockquoteParagraph) {
                        flushInline()
                        unmark("p")
                        inBlockquoteParagraph = false
                    }
                    unmark("blockquote")
                    lineBuffer.clear()
                    blockMode = BlockMode.Start
                    for (c in line) {
                        process(c)
                    }
                }
            }
        } else {
            // Continue inline content within blockquote paragraph
            processInlineChar(char)
        }
    }

    private suspend fun SemanticEventScope.processBlockquoteList(
        char: Char
    ) {
        if (char == '\n') {
            if (inListItem) {
                flushInline()
                unmark("li")
                inListItem = false
            }
            lineBuffer.clear()
            return
        }

        if (!inListItem) {
            lineBuffer.append(char)
            val line = lineBuffer.toString()
            when {
                line == "> - " -> {
                    mark("li")
                    lineBuffer.clear()
                    inListItem = true
                }
                line.matches(Regex("^> ?$")) || line.matches(Regex("^> -?$")) -> {
                    // Keep buffering
                }
                line.isEmpty() -> {
                    unmark("ul")
                    unmark("blockquote")
                    blockMode = BlockMode.Start
                }
                else -> {
                    unmark("ul")
                    if (line.startsWith(">")) {
                        blockMode = BlockMode.Blockquote
                        lineBuffer.clear()
                        for (c in line) {
                            processBlockquote(c)
                        }
                    } else {
                        unmark("blockquote")
                        lineBuffer.clear()
                        blockMode = BlockMode.Start
                        for (c in line) {
                            process(c)
                        }
                    }
                }
            }
        } else {
            processInlineChar(char)
        }
    }

    private suspend fun SemanticEventScope.processMathBlock(
        char: Char
    ) {
        if (char == '\n') {
            val line = lineBuffer.toString()
            if (line.trim() == "$$") {
                unmark("math")
                lineBuffer.clear()
                blockMode = BlockMode.Start
            } else {
                if (line.isNotEmpty()) {
                    +line
                }
                lineBuffer.clear()
            }
        } else {
            lineBuffer.append(char)
        }
    }

    private suspend fun SemanticEventScope.processTable(
        char: Char
    ) {
        lineBuffer.append(char)
        if (char == '\n') {
            val line = lineBuffer.toString().trimEnd()
            if (line.matches(Regex("^\\|[-:|\\s]+\\|$"))) {
                // Separator row
                unmark("thead")
                mark("tbody")
                tableHasBody = true
                lineBuffer.clear()
                blockMode = BlockMode.TableBody
            } else if (line.startsWith("|")) {
                // Another header row
                "tr" {
                    emitTableRow(line, isHeader = true)
                }
                lineBuffer.clear()
            } else {
                // End of table
                unmark("thead")
                unmark("table")
                lineBuffer.clear()
                blockMode = BlockMode.Start
                if (line.isNotEmpty()) {
                    for (c in "$line\n") {
                        process(c)
                    }
                }
            }
        }
    }

    private suspend fun SemanticEventScope.processTableBody(
        char: Char
    ) {
        lineBuffer.append(char)
        if (char == '\n') {
            val line = lineBuffer.toString().trimEnd()
            if (line.startsWith("|")) {
                "tr" {
                    emitTableRow(line, isHeader = false)
                }
                lineBuffer.clear()
            } else {
                // End of table
                unmark("tbody")
                unmark("table")
                lineBuffer.clear()
                blockMode = BlockMode.Start
                if (line.isNotEmpty()) {
                    for (c in "$line\n") {
                        process(c)
                    }
                }
            }
        }
    }

    private suspend fun SemanticEventScope.emitTableRow(
        line: String,
        isHeader: Boolean
    ) {
        val cells = line.trim().removePrefix("|").removeSuffix("|").split("|")
        val cellTag = if (isHeader) "th" else "td"
        for (cell in cells) {
            cellTag {
                processInlineContent(cell.trim())
                flushInline()
            }
        }
    }

    /**
     * Parses a custom markup opening tag like `<foo:bar attr="value">`.
     * Returns the tag name and attributes map, or null if not a valid custom markup tag.
     * Custom markup tags must have a namespace (contain a colon in the tag name).
     */
    private fun parseCustomMarkupOpeningTag(line: String): Pair<String, Map<String, String>?>? {
        // Remove < and >
        val content = line.removePrefix("<").removeSuffix(">").trim()
        if (content.isEmpty()) return null

        // Split into tag name and attributes
        val parts = content.split(Regex("\\s+"), limit = 2)
        val tagName = parts[0]

        // Must contain a colon (namespaced tag) to be custom markup
        if (!tagName.contains(':')) return null

        // Parse attributes if present
        val attributes = if (parts.size > 1) {
            parseAttributes(parts[1])
        } else {
            null
        }

        return tagName to attributes
    }

    /**
     * Parses attribute string like `attr="value" other="val2"` into a map.
     */
    private fun parseAttributes(attrString: String): Map<String, String>? {
        if (attrString.isBlank()) return null

        val attributes = mutableMapOf<String, String>()
        // Simple regex-based attribute parsing: name="value" or name='value'
        val attrPattern = Regex("""(\w+)=["']([^"']*)["']""")
        attrPattern.findAll(attrString).forEach { match ->
            val (name, value) = match.destructured
            attributes[name] = value
        }

        return if (attributes.isNotEmpty()) attributes else null
    }

    /**
     * Process content inside a custom markup block.
     * Content is emitted immediately as it arrives.
     * Closing tag is detected incrementally when </tagname> pattern is seen.
     */
    private suspend fun SemanticEventScope.processCustomMarkup(
        char: Char,
        tagName: String
    ) {
        // Skip the first newline after opening tag (when detected incrementally)
        if (customMarkupSkipFirstNewline) {
            customMarkupSkipFirstNewline = false
            if (char == '\n') {
                return
            }
        }

        val closingTag = "</$tagName>"

        // If we're tracking a potential closing tag
        if (customMarkupInClosingTag) {
            customMarkupClosingBuffer.append(char)
            val bufferStr = customMarkupClosingBuffer.toString()

            when {
                // Complete closing tag found
                bufferStr == closingTag -> {
                    customMarkupClosingBuffer.clear()
                    customMarkupInClosingTag = false
                    unmark(tagName, isTag = true)
                    blockMode = BlockMode.Start
                }
                // Still a valid prefix of closing tag
                closingTag.startsWith(bufferStr) -> {
                    // Keep buffering
                }
                // Not a closing tag - emit pending newline and buffered content
                else -> {
                    if (customMarkupPendingNewline) {
                        customMarkupPendingNewline = false
                        +'\n'
                    }
                    +customMarkupClosingBuffer.toString()
                    customMarkupClosingBuffer.clear()
                    customMarkupInClosingTag = false
                }
            }
            return
        }

        // Check if this character starts a potential closing tag
        // Note: Don't emit pending newline yet - it may be part of whitespace before closing tag
        if (char == '<') {
            customMarkupClosingBuffer.append(char)
            customMarkupInClosingTag = true
            return
        }

        // Handle pending newline - emit it now before new content
        if (customMarkupPendingNewline) {
            customMarkupPendingNewline = false
            +'\n'
        }

        // Handle newlines - buffer them to avoid trailing newlines
        if (char == '\n') {
            customMarkupPendingNewline = true
            return
        }

        // Regular content - emit immediately
        +char
    }

    private suspend fun SemanticEventScope.processInlineChar(char: Char) {
        // Handle escaping
        if (escaped) {
            escaped = false
            +char
            return
        }
        if (char == '\\') {
            if (inlineBuffer.isNotEmpty()) {
                +inlineBuffer.toString()
                inlineBuffer.clear()
            }
            escaped = true
            return
        }

        // Inside code - only look for closing backtick
        if (code) {
            when {
                char == '`' && doubleBacktickCode && inlineBuffer.endsWith("`") -> {
                    // Closing `` found - the buffer ends with first backtick, now second arrived
                    // Content is everything before the trailing backtick
                    var content = inlineBuffer.substring(0, inlineBuffer.length - 1)
                    // Strip single leading and trailing space if both present (CommonMark rule)
                    if (content.startsWith(" ") && content.endsWith(" ") && content.length > 1) {
                        content = content.substring(1, content.length - 1)
                    }
                    +content
                    inlineBuffer.clear()
                    unmark("code")
                    code = false
                    doubleBacktickCode = false
                }
                char == '`' && !doubleBacktickCode -> {
                    if (inlineBuffer.isNotEmpty()) {
                        +inlineBuffer.toString()
                        inlineBuffer.clear()
                    }
                    unmark("code")
                    code = false
                }
                else -> {
                    if (doubleBacktickCode) {
                        inlineBuffer.append(char)
                    } else {
                        +char
                    }
                }
            }
            return
        }

        // Inside math - only look for closing $
        if (math) {
            if (char == '$') {
                if (inlineBuffer.isNotEmpty()) {
                    +inlineBuffer.toString()
                    inlineBuffer.clear()
                }
                unmark("math")
                math = false
            } else {
                +char
            }
            return
        }

        // Handle image states (check before link since both use inLinkUrl)
        if (inImage && !inLinkUrl) {
            if (char == ']') {
                inlineBuffer.append(']')
            } else if (char == '(' && inlineBuffer.endsWith("]")) {
                inlineBuffer.clear()
                inLinkUrl = true
            } else {
                imageAlt.append(char)
            }
            return
        }

        if (inImage && inLinkUrl) {
            when (char) {
                ')' -> {
                    "img"(
                        "src" to imageUrl.toString().trim(),
                        "alt" to imageAlt.toString()
                    ) {}
                    inImage = false
                    inLinkUrl = false
                    imageAlt.clear()
                    imageUrl.clear()
                }
                else -> imageUrl.append(char)
            }
            return
        }

        // Handle link states
        if (inLink && !inLinkUrl) {
            if (char == ']') {
                inlineBuffer.append(']')
            } else if (char == '(' && inlineBuffer.endsWith("]")) {
                inlineBuffer.clear()
                inLinkUrl = true
            } else {
                linkText.append(char)
            }
            return
        }

        if (inLink && inLinkUrl) {
            when (char) {
                ')' -> {
                    val urlPart = linkUrl.toString().trim()
                    val title = linkTitle.toString().trim()
                    val url = urlPart.substringBefore(" \"").trim()
                    val extractedTitle = if (urlPart.contains(" \"")) {
                        urlPart.substringAfter(" \"").removeSuffix("\"").trim()
                    } else {
                        title
                    }
                    val attrs = if (extractedTitle.isNotEmpty()) {
                        mapOf("href" to url, "title" to extractedTitle)
                    } else {
                        mapOf("href" to url)
                    }
                    "a"(attrs) {
                        +linkText.toString()
                    }
                    inLink = false
                    inLinkUrl = false
                    linkText.clear()
                    linkUrl.clear()
                    linkTitle.clear()
                }
                else -> linkUrl.append(char)
            }
            return
        }

        // Autolinks
        if (inlineBuffer.startsWith("<")) {
            if (char == '>') {
                val content = inlineBuffer.substring(1)
                inlineBuffer.clear()
                if (content.contains("@") && !content.contains("://")) {
                    "a"("href" to "mailto:$content") {
                        +content
                    }
                } else if (content.contains("://")) {
                    "a"("href" to content) {
                        +content
                    }
                } else {
                    +"<$content>"
                }
                return
            } else {
                inlineBuffer.append(char)
                return
            }
        }

        // Handle special characters
        // IMPORTANT: Buffer-based checks must come BEFORE new character checks
        // so that pending formatting markers are processed before the new char
        when {
            // First, check if buffer contains formatting markers that should be resolved
            inlineBuffer.toString() == "`" && char != '`' -> {
                inlineBuffer.clear()
                code = true
                mark("code")
                +char
            }
            inlineBuffer.toString() == "***" && char != '*' -> {
                // Three asterisks - handle bold+italic toggling
                inlineBuffer.clear()
                if (bold && italic) {
                    // Close both - em first (inner), then strong (outer)
                    unmark("em")
                    unmark("strong")
                    bold = false
                    italic = false
                } else if (bold) {
                    unmark("strong")
                    bold = false
                    // Now start italic
                    mark("em")
                    italic = true
                } else if (italic) {
                    unmark("em")
                    italic = false
                    // Now start bold
                    mark("strong")
                    bold = true
                } else {
                    // Start both - bold then italic
                    mark("strong")
                    mark("em")
                    bold = true
                    italic = true
                }
                processInlineChar(char)
            }
            inlineBuffer.toString() == "**" && char != '*' -> {
                inlineBuffer.clear()
                if (bold) {
                    unmark("strong")
                    bold = false
                } else {
                    mark("strong")
                    bold = true
                }
                processInlineChar(char)
            }
            inlineBuffer.toString() == "*" && char != '*' -> {
                inlineBuffer.clear()
                if (italic) {
                    unmark("em")
                    italic = false
                } else {
                    mark("em")
                    italic = true
                }
                processInlineChar(char)
            }
            // Now handle new characters that start or continue formatting markers
            char == '`' -> {
                if (inlineBuffer.endsWith("`")) {
                    inlineBuffer.clear()
                    code = true
                    doubleBacktickCode = true
                    mark("code")
                } else {
                    if (inlineBuffer.isNotEmpty()) {
                        +inlineBuffer.toString()
                        inlineBuffer.clear()
                    }
                    inlineBuffer.append('`')
                }
            }
            char == '*' -> {
                if (inlineBuffer.endsWith("*")) {
                    inlineBuffer.append('*')
                } else {
                    if (inlineBuffer.isNotEmpty()) {
                        +inlineBuffer.toString()
                        inlineBuffer.clear()
                    }
                    inlineBuffer.append('*')
                }
            }
            // Underscore buffer checks (before char == '_')
            inlineBuffer.toString() == "___" && char != '_' -> {
                inlineBuffer.clear()
                if (bold && italic) {
                    unmark("em")
                    unmark("strong")
                    bold = false
                    italic = false
                } else {
                    mark("strong")
                    mark("em")
                    bold = true
                    italic = true
                }
                processInlineChar(char)
            }
            inlineBuffer.toString() == "__" && char != '_' -> {
                inlineBuffer.clear()
                if (bold) {
                    unmark("strong")
                    bold = false
                } else {
                    mark("strong")
                    bold = true
                }
                processInlineChar(char)
            }
            inlineBuffer.toString() == "_" && char != '_' -> {
                inlineBuffer.clear()
                if (italic) {
                    unmark("em")
                    italic = false
                } else {
                    mark("em")
                    italic = true
                }
                processInlineChar(char)
            }
            // Tilde buffer checks (before char == '~')
            inlineBuffer.toString() == "~~" && char != '~' -> {
                inlineBuffer.clear()
                if (strikethrough) {
                    unmark("del")
                    strikethrough = false
                } else {
                    mark("del")
                    strikethrough = true
                }
                processInlineChar(char)
            }
            inlineBuffer.toString() == "~" && char != '~' -> {
                inlineBuffer.clear()
                if (subscript) {
                    unmark("sub")
                    subscript = false
                } else {
                    mark("sub")
                    subscript = true
                }
                processInlineChar(char)
            }
            // Now handle new character cases
            char == '_' -> {
                if (inlineBuffer.endsWith("__")) {
                    inlineBuffer.append('_')
                } else if (inlineBuffer.endsWith("_")) {
                    inlineBuffer.append('_')
                } else {
                    if (inlineBuffer.isNotEmpty()) {
                        +inlineBuffer.toString()
                        inlineBuffer.clear()
                    }
                    inlineBuffer.append('_')
                }
            }
            char == '~' -> {
                if (inlineBuffer.endsWith("~")) {
                    inlineBuffer.append('~')
                } else {
                    if (inlineBuffer.isNotEmpty()) {
                        +inlineBuffer.toString()
                        inlineBuffer.clear()
                    }
                    inlineBuffer.append('~')
                }
            }
            char == '^' -> {
                if (inlineBuffer.isNotEmpty()) {
                    +inlineBuffer.toString()
                    inlineBuffer.clear()
                }
                if (superscript) {
                    unmark("sup")
                    superscript = false
                } else {
                    mark("sup")
                    superscript = true
                }
            }
            // Equals buffer checks (before char == '=')
            inlineBuffer.toString() == "==" && char != '=' -> {
                inlineBuffer.clear()
                if (highlight) {
                    unmark("mark")
                    highlight = false
                } else {
                    mark("mark")
                    highlight = true
                }
                processInlineChar(char)
            }
            inlineBuffer.toString() == "=" && char != '=' -> {
                inlineBuffer.clear()
                +"="
                processInlineChar(char)
            }
            // Now handle new equals
            char == '=' -> {
                if (inlineBuffer.endsWith("=")) {
                    inlineBuffer.append('=')
                } else {
                    if (inlineBuffer.isNotEmpty()) {
                        +inlineBuffer.toString()
                        inlineBuffer.clear()
                    }
                    inlineBuffer.append('=')
                }
            }
            char == '$' -> {
                if (inlineBuffer.isNotEmpty()) {
                    +inlineBuffer.toString()
                    inlineBuffer.clear()
                }
                if (math) {
                    unmark("math")
                    math = false
                } else {
                    mark("math")
                    math = true
                }
            }
            // Exclamation buffer checks (before char == '[' for image syntax)
            inlineBuffer.toString() == "!" && char == '[' -> {
                inlineBuffer.clear()
                inImage = true
            }
            inlineBuffer.toString() == "!" && char != '[' -> {
                inlineBuffer.clear()
                +"!"
                processInlineChar(char)
            }
            // Now handle bracket and exclamation
            char == '[' -> {
                if (inlineBuffer.isNotEmpty()) {
                    +inlineBuffer.toString()
                    inlineBuffer.clear()
                }
                inLink = true
            }
            char == '!' -> {
                if (inlineBuffer.isNotEmpty()) {
                    +inlineBuffer.toString()
                    inlineBuffer.clear()
                }
                inlineBuffer.append('!')
            }
            char == '<' -> {
                if (inlineBuffer.isNotEmpty()) {
                    +inlineBuffer.toString()
                    inlineBuffer.clear()
                }
                inlineBuffer.append('<')
            }
            else -> {
                if (inlineBuffer.isNotEmpty()) {
                    +inlineBuffer.toString()
                    inlineBuffer.clear()
                }
                +char
            }
        }
    }

    private suspend fun SemanticEventScope.flushInline() {
        if (inlineBuffer.isNotEmpty()) {
            +inlineBuffer.toString()
            inlineBuffer.clear()
        }
        if (math) {
            unmark("math")
            math = false
        }
        if (code) {
            unmark("code")
            code = false
            doubleBacktickCode = false
        }
        if (highlight) {
            unmark("mark")
            highlight = false
        }
        if (superscript) {
            unmark("sup")
            superscript = false
        }
        if (subscript) {
            unmark("sub")
            subscript = false
        }
        if (strikethrough) {
            unmark("del")
            strikethrough = false
        }
        if (italic) {
            unmark("em")
            italic = false
        }
        if (bold) {
            unmark("strong")
            bold = false
        }
        escaped = false
    }

    suspend fun finalize() {
        scope.finalize()
    }

    private suspend fun SemanticEventScope.finalize() {
        // Handle any remaining content based on mode
        when (blockMode) {
            is BlockMode.Heading -> {
                flushInline()
                unmark("h${(blockMode as BlockMode.Heading).level}")
            }
            BlockMode.Paragraph -> {
                flushInline()
                unmark("p")
            }
            is BlockMode.CodeBlock -> {
                val backticks = (blockMode as BlockMode.CodeBlock).backticks
                val isClosingFence = lineBuffer.isNotEmpty() && lineBuffer.toString().trim() == "`".repeat(backticks)

                // Emit pending line if any
                if (codeBlockPendingLine != null) {
                    // If lineBuffer has content that's NOT the closing fence, add newline
                    if (lineBuffer.isNotEmpty() && !isClosingFence) {
                        +(codeBlockPendingLine + "\n")
                    } else {
                        // Last content line - no trailing newline
                        +codeBlockPendingLine!!
                    }
                    codeBlockPendingLine = null
                }
                if (lineBuffer.isNotEmpty() && !isClosingFence) {
                    +lineBuffer.toString()
                }
                unmark("pre")
            }
            BlockMode.UnorderedList -> {
                if (inListItem) {
                    flushInline()
                    unmark("li")
                }
                unmark("ul")
            }
            BlockMode.OrderedList -> {
                if (inListItem) {
                    flushInline()
                    unmark("li")
                }
                unmark("ol")
            }
            BlockMode.Blockquote -> {
                if (inBlockquoteParagraph) {
                    flushInline()
                    unmark("p")
                }
                unmark("blockquote")
            }
            BlockMode.BlockquoteList -> {
                if (inListItem) {
                    flushInline()
                    unmark("li")
                }
                unmark("ul")
                unmark("blockquote")
            }
            BlockMode.MathBlock -> {
                if (lineBuffer.isNotEmpty()) {
                    +lineBuffer.toString()
                }
                unmark("math")
            }
            BlockMode.Table -> {
                unmark("thead")
                unmark("table")
            }
            BlockMode.TableBody -> {
                // Process any pending row in lineBuffer
                if (lineBuffer.isNotEmpty()) {
                    val line = lineBuffer.toString().trimEnd()
                    if (line.startsWith("|")) {
                        "tr" {
                            emitTableRow(line, isHeader = false)
                        }
                    }
                }
                unmark("tbody")
                unmark("table")
            }
            BlockMode.Start -> {
                // Check if there's pending content in lineBuffer
                if (lineBuffer.isNotEmpty()) {
                    "p" {
                        processInlineContent(lineBuffer.toString())
                        flushInline()
                    }
                }
            }
            is BlockMode.CustomMarkup -> {
                val tagName = (blockMode as BlockMode.CustomMarkup).tagName
                // Emit any buffered content from incomplete closing tag detection
                if (customMarkupClosingBuffer.isNotEmpty()) {
                    +customMarkupClosingBuffer.toString()
                    customMarkupClosingBuffer.clear()
                    customMarkupInClosingTag = false
                }
                unmark(tagName, isTag = true)
            }
        }
    }
}
