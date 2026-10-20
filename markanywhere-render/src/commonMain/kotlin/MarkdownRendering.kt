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

package com.xemantic.markanywhere.render

import com.xemantic.kotlin.core.text.joinToString
import com.xemantic.kotlin.core.text.unaryPlus
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.html.spec.HTML_RAW_TEXT_ELEMENTS
import com.xemantic.markanywhere.html.spec.HTML_VOID_ELEMENTS
import com.xemantic.markanywhere.yaml.YamlWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Streams the flow of [SemanticEvent]s back as Markdown source chunks.
 *
 * Output is incremental at the granularity of upstream events: each Mark /
 * Text / Unmark produces at most one downstream [Flow] emission. Within an
 * event, all writes accumulate into a reusable per-event buffer which flushes
 * once at the end of the event. Apply downstream batching if the consumer
 * needs coarser units.
 *
 * Bounded buffering still occurs around link/image labels (`[label](url)`
 * can't emit `[` until the closing bracket / URL resolves), table cells
 * (cell content is held until the cell closes so the trailing ` |` lands
 * after it), and inline code spans (content is held until the close so the
 * backtick fence can be made longer than any internal backtick run). These
 * match the parser's own buffering constraints. A text event ending in a char
 * whose escaping depends on the char after it (`$b` is math, `$5` is not) is
 * held for one event.
 *
 * Untagged events (Markdown-derived) are emitted using Markdown syntax —
 * headings as `#`-prefixed lines, emphasis as `*…*` / `**…**`, links as
 * `[label](url)`, fenced code as ` ``` `, etc.
 *
 * Tagged events (HTML-originated, [SemanticEvent.Marked.isTagged] = `true`)
 * are passed through as raw HTML tags, since Markdown permits embedded HTML.
 *
 * The synthetic `frontmatter` block is written as YAML between `---`
 * fences from its structured `entry` / `item` events (see [YamlWriter] in
 * `markanywhere-yaml`); `entry` and `item` are Markdown-native only inside
 * it and render as raw tags anywhere else.
 *
 * A single trailing `\n` (if present at end of stream) is suppressed —
 * the renderer holds at most one pending newline and drops it on completion.
 */
public fun Flow<SemanticEvent>.asMarkdown(): Flow<String> = flow {

    val blockStack = ArrayDeque<BlockFrame>()
    val labelBuffers = ArrayDeque<StringBuilder>()
    // Non-null while inside a `frontmatter` block: every event until its
    // matching unmark is YAML metadata, written by the dedicated writer.
    var frontmatter: YamlWriter? = null
    var pendingBlockSeparator = false
    var atLineStart = true
    var pendingHardBreak = false
    // Trailing ASCII spaces emitted from text content are deferred here rather
    // than written immediately: they are flushed only when real content follows
    // on the same line, and dropped at a line/block boundary. A single trailing
    // space is insignificant in Markdown (the parser strips it), and `<br>` hard
    // breaks arrive as structural events — not literal double-spaces — so
    // dropping deferred spaces never loses a break. Without this the renderer
    // leaks trailing whitespace that re-parsing strips, so a round-trip diverges.
    var pendingSpaces = 0
    // Trailing whitespace other than ASCII spaces (NBSP, tab, any `Zs`) at the
    // end of an emphasis span's text, held so a closer can be written before
    // it: a closer after whitespace is not right-flanking (`**Price:\u00A0**5`).
    // Unlike deferred spaces it is content, so a line end writes it, never
    // drops it. Written before any spaces deferred after it.
    var pendingWhitespace = ""
    // True when the next content begins a fresh Markdown line in a block context
    // (right after a newline or a block prefix, before any content is written).
    // At that position a leading block marker in paragraph text (`1.`, `- `,
    // `# `, `>`, `---`) is backslash-escaped so it re-parses as text, not as a
    // different block — without it, e.g., a `<p>1.</p>` margin number renders as
    // bare `1.`, re-parses as an empty list item, and is dropped.
    var freshBlockLine = false
    // True when the last content written was a bare, unescaped digit run that
    // began a Markdown line. The parser splits an escaped ordered-list marker
    // (`1\.`) into separate "1" and "." text events, so a following "."/")"
    // fragment must be escaped to keep the round-trip from re-forming a list.
    var lineStartDigits = false
    var inPreCode = false
    // After a heading's `#`s are emitted, the single space before its content
    // is deferred until the first content actually arrives — so an empty
    // heading (`# ` with no content) renders as bare `#`, not a dangling `# `.
    var headingSpacePending = false
    // A non-list paragraph defers its block-start (the blank-line separation +
    // prefix) until its first real content arrives — so a content-less
    // paragraph (`<p> </p>`, common in captured DOMs) emits nothing instead of
    // injecting a stray blank line that a round-trip would then collapse.
    var pendingParagraph = false
    // Tracks the kind of the last block-level item emitted, to decide block
    // separation: a blank line is inserted at every Markdown↔raw-tag boundary
    // (so a block-level HTML element's Markdown content stays parseable), while
    // two adjacent block-level tags stay tight (newline only).
    var lastBlock = BLOCK_NONE
    // Nesting depth of `<table>`s being serialized as inline HTML inside a
    // table cell (Markdown has no nested-table syntax). >0 means every event
    // is serialized as raw single-line HTML into the enclosing cell buffer.
    var htmlTableDepth = 0
    // Bitmask of the open inline-emphasis delimiter base chars (`*`/`~`/`=`/`^` —
    // see delimiterBit). Maintained on Inline mark/unmark so the per-text escaping
    // pass (escapeInlineText) needs neither a blockStack rescan nor a
    // set lookup per char — a plain `and` keeps the hot path allocation-free.
    var activeDelimiterMask = 0
    // An emphasis span whose closing delimiter is held for one event: if the
    // very next event reopens the same span, the two merge instead of abutting
    // (`**a****b**` re-parses as neither two spans nor one).
    var pendingInlineClose: BlockFrame.Inline? = null
    // The last char written to any sink, and the delimiter char of a closer
    // that is the very last thing written (null once anything follows it) —
    // what decides whether the next opener or text would make the closer stop
    // re-parsing as one (see closePendingInline and openInline).
    var lastChar = '\n'
    var closerChar: Char? = null

    val eventBuffer = StringBuilder()
    var hasPendingNewline = false
    // While a table row is being assembled its output is captured here so the
    // row can be dropped if empty, and so a headerless table can synthesize an
    // empty header (column count is known only once the first row closes).
    var rowCapture: StringBuilder? = null
    // Whether the output ends with a blank line (or nothing was written yet):
    // a raw tag written here starts a block of its own.
    var afterBlankLine = true
    // Counts the writes that broke a line, to tell whether an HTML block's
    // content stayed on its opening line (see closesAsBlock).
    var lineBreaks = 0
    // The line (as a lineBreaks count) that opens a type-6 HTML block at a
    // block boundary: whatever follows the tag on it is the block's raw text
    // for certain (see textContext), -1 when there is none.
    var rawLine = -1

    // Whether this raw tag's close goes on a line of its own: a block-level
    // tag's always; a block tag name's (an HTML block start, `blockName`) once
    // its content left the opening line — a closing tag at the end of a content
    // line makes the parser read that line as the block's raw text, or as
    // Markdown with a literal `</div>`, depending on how the line starts.
    fun BlockFrame.TaggedInline.closesAsBlock(): Boolean =
        block || (blockName && lineBreaks != openedAtLineBreak)

    fun out(s: String) {
        if (s.isEmpty()) return
        (rowCapture ?: eventBuffer).append(s)
        afterBlankLine = if (s == "\n") lastChar == '\n' else s.endsWith("\n\n")
        if ('\n' in s) lineBreaks++
        lastChar = s.last()
        closerChar = null
    }

    // Emit any deferred trailing spaces — called right before real content is
    // written on the current line, so a space between inline tokens survives
    // while a space at a line end is dropped (it is never flushed there).
    fun flushPendingSpaces() {
        if (pendingWhitespace.isNotEmpty()) {
            out(pendingWhitespace)
            pendingWhitespace = ""
        }
        if (pendingSpaces > 0) {
            out(" ".repeat(pendingSpaces))
            pendingSpaces = 0
        }
    }

    // At a line end deferred spaces are trailing and dropped, but held
    // whitespace is content and written — without its own trailing ASCII
    // spaces, which before the newline would re-parse as a hard break.
    fun flushHeldWhitespaceAtLineEnd() {
        pendingSpaces = 0
        out(pendingWhitespace.trimEnd(' '))
        pendingWhitespace = ""
    }

    // Emits the deferred space between a heading's `#`s and its first content
    // (no-op outside a just-opened heading). Called at every content-entry
    // point so the space lands only when content actually follows.
    fun consumeHeadingSpace() {
        if (headingSpacePending) {
            out(" ")
            headingSpacePending = false
        }
    }

    suspend fun flush() {
        hasPendingNewline = flushDeferringTrailingNewline(eventBuffer, hasPendingNewline)
    }

    // Build a line prefix from the current block context. If `consumeMarker`
    // is true, an active list-item's pending marker is consumed (used at the
    // very first content position of the item — replaces that list's indent
    // contribution). Subsequent calls use the indent.
    fun buildPrefix(consumeMarker: Boolean): String {
        val sb = StringBuilder()
        var i = 0
        while (i < blockStack.size) {
            when (val frame = blockStack[i]) {
                is Blockquote -> sb.append("> ")
                // `BlockFrame.` prefix kept here only: bare `is List` resolves to
                // `kotlin.collections.List` (imported by default), which CSR does not
                // override — unlike `Blockquote`/`Inline`/`Code`, which have no
                // colliding top-level name.
                is BlockFrame.List -> {
                    val next = blockStack.getOrNull(i + 1)
                    val pending = (next as? ListItem)?.pendingMarker
                    if (consumeMarker && pending != null) {
                        sb.append(pending)
                        next.pendingMarker = null
                    } else {
                        sb.append(frame.indent)
                    }
                }
                else -> { }
            }
            i++
        }
        return sb.toString()
    }

    fun appendLabel(text: String) {
        if (text.isEmpty()) return
        labelBuffers.last().append(text)
        lastChar = text.last()
        closerChar = null
    }
    fun inLabel(): Boolean = labelBuffers.isNotEmpty()

    // Whether the current content lands on a heading's line: the nearest
    // enclosing block frame is a `Heading`. Inline frames — emphasis, links and
    // raw tags rendered inline — are transparent; a raw tag rendered as a block
    // is not, so a block nested in a heading behaves as it would anywhere else.
    fun onHeadingLine(): Boolean {
        for (i in blockStack.indices.reversed()) {
            when (val frame = blockStack[i]) {
                is Inline, is Link -> continue
                is TaggedInline -> if (frame.block) return false
                else -> return frame == Heading
            }
        }
        return false
    }

    // The one decision every raw-tag site shares: does a raw tag named [name],
    // opened here, render as a block-level tag on lines of its own? A sectioning
    // or form tag does, and so does a void element or an `<a>` landing at a line
    // start. A Markdown heading is a single line, though, so on a heading's line
    // only a tag the parser cannot keep inline stays a block — phrasing content
    // ([HEADING_INLINE_TAGS], the disclosure `<button>` of an accordion heading)
    // joins the line instead of leaving the heading empty above it.
    fun rendersAsBlockTag(name: String): Boolean {
        if (inLabel()) return false
        val block = name in BLOCK_TAGGED_ELEMENTS ||
            ((name in HTML_VOID_ELEMENTS || name == "a") && atLineStart)
        return block && !(name in HEADING_INLINE_TAGS && onHeadingLine())
    }

    // Starts capturing a label. The capture is written after something that
    // separates it from the last closer (`[`, a backtick, a table cell's
    // leading space, or a spilled link's raw `<a …>` tag) — [before], which is
    // what the label's first char follows on re-parse.
    fun openLabelBuffer(before: Char) {
        labelBuffers.addLast(StringBuilder())
        lastChar = before
        closerChar = null
    }

    // Serialize one event of a nested table as raw HTML into the enclosing
    // cell buffer — no newlines (the outer row is a single line), text nodes
    // HTML-escaped, void elements emitted as a bare open tag. The `<table>`
    // depth is tracked so the matching root close exits this mode.
    fun serializeHtmlTableEvent(event: SemanticEvent) {
        when (event) {
            is Mark -> {
                appendLabel(event.renderOpenTag())
                if (event.name == "table") htmlTableDepth++
            }
            is Unmark -> when (event.name) {
                "table" -> {
                    appendLabel("</table>")
                    htmlTableDepth--
                }
                in HTML_VOID_ELEMENTS -> { /* no closing tag */ }
                else -> appendLabel("</${event.name}>")
            }
            is Text -> appendLabel(event.text.escapeHtmlText())
        }
    }

    // Emit a raw string with no block-separation logic — used for structural
    // tags, whose separation is handled by their callers (separateBeforeBlockTag).
    fun writeTag(s: String) {
        if (inLabel()) { appendLabel(s); return }
        consumeHeadingSpace()
        flushPendingSpaces()
        // At line start, emit the block prefix (consuming a pending list-item
        // marker) before the content — mirrors `emitText` and the `img` branch.
        // Without this, a list item whose first content is a link / emphasis /
        // raw tag (rather than plain text) loses its `- ` / `1. ` marker.
        if (atLineStart) out(buildPrefix(consumeMarker = true))
        out(s)
        atLineStart = false
        freshBlockLine = false
        lineStartDigits = false
    }

    fun ensureLineStart() {
        if (!atLineStart) {
            // Deferred spaces at a line end are trailing — drop them.
            flushHeldWhitespaceAtLineEnd()
            out("\n")
            atLineStart = true
            freshBlockLine = true
            lineStartDigits = false
        }
    }

    // Called before emitting Markdown content / a Markdown block. Emits a
    // blank separator line when one is pending (sibling blocks) or when the
    // previous block-level item was a raw HTML tag (tag→Markdown boundary).
    fun ensureBlankLine() {
        // A pending hard break (`<br>`) is inline-only; reaching a block
        // boundary makes it moot, so drop it rather than letting it inject
        // `  \n` into the next block (e.g. between a heading marker and its
        // text when a `<br>` precedes the heading).
        pendingHardBreak = false
        ensureLineStart()
        if (pendingBlockSeparator || lastBlock == BLOCK_TAG) {
            out(buildPrefix(consumeMarker = false).trimEnd())
            out("\n")
        }
        pendingBlockSeparator = false
        lastBlock = BLOCK_MARKDOWN
        freshBlockLine = true
    }

    // Begin a block element on its own line, with a blank line separating it
    // from any prior sibling block.
    fun startBlock() {
        ensureBlankLine()
        out(buildPrefix(consumeMarker = true))
        atLineStart = false
        // The block prefix (if any) is emitted but no content yet — the next
        // text still begins the Markdown line, so escaping must still apply.
        freshBlockLine = true
    }

    // Resolves any pending line/block break right before real Markdown content
    // is emitted. Three outcomes:
    //  1. Block boundary (a closed Markdown block, a just-opened block tag, or a
    //     loose-content run) → blank-line separation. This also applies when a
    //     `<br>` precedes the boundary: the hard break is moot, so it (and the
    //     deferred whitespace around it) is dropped by the separation.
    //  2. A `<br>` with no block boundary → a hard line break within the block.
    //  3. Otherwise nothing — inline flow continues on the current line (deferred
    //     spaces, if any, are flushed by the caller).
    fun beforeContent() {
        // A deferred non-list paragraph starts its block now that real content
        // has arrived. startBlock() emits the separation/prefix and clears the
        // pending separator, so the boundary check below becomes a no-op.
        if (pendingParagraph) {
            pendingParagraph = false
            startBlock()
        }
        val boundary = pendingBlockSeparator || lastBlock == BLOCK_TAG
        // A clean line start (no deferred spaces) keeps the original behavior;
        // a pending `<br>` lets the boundary win even mid-line (the moot-break
        // case). Without a `<br>`, mid-line content stays on the line so a run
        // of loose inline siblings (e.g. `[a] [b]`) is not split block-by-block.
        if (boundary && (pendingHardBreak || (atLineStart && pendingSpaces == 0 && pendingWhitespace.isEmpty()))) {
            ensureBlankLine() // drops a moot pendingHardBreak + deferred spaces
            // Loose inline content at a block boundary (e.g. a link directly
            // inside an unwrapped <nav>, not wrapped in a paragraph) behaves as
            // its own block — the next block-level construct separates from it.
            pendingBlockSeparator = true
        } else if (pendingHardBreak) {
            // Hard line break inside the same block. Deferred trailing spaces
            // before the break are insignificant and dropped.
            flushHeldWhitespaceAtLineEnd()
            out("  ")
            ensureLineStart()
            pendingHardBreak = false
        }
    }

    // Emit inline Markdown content (links, emphasis delimiters, inline raw
    // tags). Separates from a preceding block / block tag when at a boundary.
    fun writeRaw(s: String) {
        if (!inLabel()) beforeContent()
        writeTag(s)
    }

    // Called before emitting a block-level raw HTML tag (open or close).
    // Inserts a blank line at a Markdown→tag boundary, but keeps two adjacent
    // tags tight (newline only).
    fun separateBeforeBlockTag() {
        pendingHardBreak = false
        ensureLineStart()
        if (lastBlock == BLOCK_MARKDOWN || pendingBlockSeparator) {
            out(buildPrefix(consumeMarker = false).trimEnd())
            out("\n")
        }
        pendingBlockSeparator = false
    }

    // The first frame from the top of blockStack that is not a transparent inline
    // raw tag (`TaggedInline`, e.g. a `<b>`) — the real frame directly enclosing the
    // current text. Both the inline-code check and the label-emphasis check key off
    // this single frame, so the transparency rule lives in one place: a raw `<b>`
    // inside the construct must not hide the real enclosing frame. A `Code` frame
    // means inline-code content (kept verbatim); an `Inline` frame means an emphasis
    // span opened *inside* the current label directly encloses the text (a literal
    // `*` inside `<b>` inside `**…**` still re-pairs with the strong on re-parse, so
    // scanning past the `<b>` to the `Inline` below is required).
    fun firstOpaqueFrame(): BlockFrame? {
        for (i in blockStack.indices.reversed()) {
            val frame = blockStack[i]
            if (frame !is TaggedInline) return frame
        }
        return null
    }

    // The char the parser sees right before the next text on re-parse: a line
    // start, a hard break or a deferred space reads as whitespace.
    fun prevWrittenChar(): Char = when {
        inLabel() -> lastChar
        pendingHardBreak || pendingParagraph || atLineStart -> '\n'
        pendingSpaces > 0 || headingSpacePending -> ' '
        pendingWhitespace.isNotEmpty() -> pendingWhitespace.last()
        else -> lastChar
    }

    // How text here is read back on re-parse — the one place this is decided;
    // escaping, block-marker escaping and the fresh-line trim all ask it:
    //  - TEXT_VERBATIM: certainly as raw text, where a backslash is literal —
    //    code, preformatted text, front matter, a raw verbatim tag (`<pre>`,
    //    `<textarea>`, …), custom markup (`<ns:name>`) opening a top-level
    //    block (TaggedInline.rawCertain), and the rest of the line a type-6
    //    HTML block opens at a block boundary (rawLine). Written as is.
    //  - TEXT_RAW_OR_MARKDOWN: inside an HTML block candidate written inline
    //    (a raw tag starting a line with its content after it on the same line
    //    — TaggedInline.htmlBlock, not a block-level tag). Whether the parser
    //    reads it raw or as Markdown depends on blank lines, the line the
    //    closing tag lands on, the container and more, so the renderer does
    //    not predict it: syntax is written as numeric
    //    character references (`&#42;`), which Markdown decodes to plain text
    //    and raw text keeps — either way the output is a fixpoint.
    //  - TEXT_MARKDOWN: certainly Markdown, escaped with backslashes.
    fun textContext(): Int {
        if (inPreCode || lineBreaks == rawLine) return TEXT_VERBATIM
        var context = TEXT_MARKDOWN
        for (frame in blockStack) {
            when (frame) {
                is Code, Pre, Frontmatter -> return TEXT_VERBATIM
                is TaggedInline -> when {
                    frame.verbatim || frame.rawCertain -> return TEXT_VERBATIM
                    // A block-level tag is written alone on its line, its content
                    // and its closer after blank lines: Markdown in between.
                    frame.htmlBlock && !frame.block -> context = TEXT_RAW_OR_MARKDOWN
                }
                // A spilled `<a>` is written like a block-level tag.
                is Link -> if (frame.rawMode && !frame.rawInline && frame.rawInItem) return TEXT_VERBATIM
                else -> {}
            }
        }
        return context
    }

    fun inVerbatimText(): Boolean = textContext() == TEXT_VERBATIM

    // Whether a raw tag written here, at a line start, opens an HTML block on
    // re-parse: a type-6 tag ([type6]) anywhere, as it interrupts a paragraph;
    // any other after a blank line or as a list item's first content. Never
    // in a blockquote, where the parser detects no HTML block.
    fun opensBlock(type6: Boolean): Boolean =
        (type6 || afterBlankLine || (blockStack.lastOrNull { it is ListItem } as? ListItem)?.pendingMarker != null) &&
            blockStack.none { it == Blockquote }

    fun opensHtmlBlockInItem(type6: Boolean): Boolean = blockStack.any { it is ListItem } && opensBlock(type6)

    // The emphasis delimiter chars (`*`/`~`/`=`/`^`, as delimiterBit bits) to
    // escape wherever they stand in the current text: every one of them when
    // any emphasis span is open — not only the open span's own delimiter. A
    // literal delimiter inside the content would otherwise re-pair against the
    // span's own closing run (`…)*` → `…)**` → `…)***`, the unbounded Brave SERP
    // instability), OR — cross-type — open a brand-new span on re-parse that
    // restructures the content (`==a~b==`: the lone `~` opens a del inside the
    // mark, which the `==` closer then force-closes, giving
    // `<mark>a<del>b</del></mark>`).
    fun spanEscapeMask(): Int =
        // Outside a label, all open emphasis is contiguous at the top of the
        // stack, so any open span (mask non-zero) means escape *all four*
        // delimiter types — cross-type, see allInlineDelimiters. Inside a label
        // the mask is two parts, because label emphasis is scoped:
        //  (a) label-internal span: if an emphasis span opened *inside* this label
        //      directly encloses the text (the first opaque frame is `Inline`; it
        //      scans past a transparent `TaggedInline` like `<b>` to the real
        //      frame), escape all four — a literal delimiter that is content of a
        //      label-local span (`[**a~b**]`) re-pairs/crosses on re-parse, and the
        //      parser's label-scoped close only resolves a delimiter at the very
        //      label end; plus
        //  (b) OUTER `del`/`mark`/`sup` (the `~`/`=`/`^` bits of the full mask).
        //      The parser scopes every span by `linkLabelOuterStackDepth`, so a
        //      matching delimiter in label text can no longer close the OUTER span
        //      (issue #58) — this escape is defense in depth that keeps the output
        //      unambiguous (`~~[foo\~bar](u)~~`). The `*` bit is left out, so an
        //      inner `*` stays unescaped against an outer em — see "should not
        //      escape label content against emphasis opened outside the label".
        if (inLabel()) {
            (if (firstOpaqueFrame() is Inline) allInlineDelimiters else 0) or
                (activeDelimiterMask and delMarkSupDelimiters)
        } else if (activeDelimiterMask != 0) allInlineDelimiters
        else 0

    // Escapes [text] written here, given [next], the char written right after
    // it (see escapeInlineSyntax). Suppressed in verbatim text, where a
    // backslash is literal: code, preformatted text, front matter, a raw
    // verbatim tag, and an HTML block in a list item.
    fun escapeInlineText(text: String, next: Char?): String {
        // Pre-scan so plain words return without the stack scans below.
        if (text.none { it in INLINE_SYNTAX_CHARS || it == '[' || it == ']' }) return text
        val context = textContext()
        if (context == TEXT_VERBATIM) return text
        // The parser never opens an em (strong) while one is open, even outside
        // the label the text is in, so `*x [a*b](u) y*` keeps its `*` as is.
        var emOpen = false
        var strongOpen = false
        for (frame in blockStack) {
            if (frame is Inline) when (frame.delimiter) {
                "*" -> emOpen = true
                "**" -> strongOpen = true
            }
        }
        return escapeInlineSyntax(
            text = text,
            before = prevWrittenChar(),
            next = next,
            mask = spanEscapeMask(),
            emOpen = emOpen,
            strongOpen = strongOpen,
            // A raw `[x](u)` read back as Markdown would become a link.
            brackets = context == TEXT_RAW_OR_MARKDOWN,
            entities = context == TEXT_RAW_OR_MARKDOWN
        )
    }

    // Recompute the open inline emphasis delimiter mask from blockStack — called
    // only on Inline *close* (rare), so even the recompute is allocation-free. The
    // open path ORs in the new bit incrementally instead (see the mark dispatch):
    // a close may need to clear the `*` bit shared by em/strong, but an open can
    // only add bits, never remove them.
    fun refreshActiveDelimiters() {
        var mask = 0
        for (frame in blockStack) {
            // `delimiter[0]` (not `firstOrNull()`) avoids boxing the Char on JVM.
            if (frame is Inline && frame.delimiter.isNotEmpty()) mask = mask or delimiterBit(frame.delimiter[0])
        }
        activeDelimiterMask = mask
    }

    // Writes an emphasis closing delimiter. Whitespace deferred inside the span
    // stays deferred past it: a closer after whitespace is not right-flanking
    // (`**a **c`), while one before it is (`**a** c`).
    fun writeCloser(delimiter: String) {
        val spaces = pendingSpaces
        val whitespace = pendingWhitespace
        pendingSpaces = 0
        pendingWhitespace = ""
        writeRaw(delimiter)
        pendingSpaces = spaces
        pendingWhitespace = whitespace
        refreshActiveDelimiters()
    }

    // Writes the closing delimiter held back by an emphasis unmark, given the
    // event that follows it. A closer after punctuation is right-flanking only
    // when whitespace or punctuation follows (CommonMark §6.2), so any other
    // char right after it (`**Price:**5`, `**Price:**€5`) is written as a
    // character reference: the closer is then followed by `&`, and the text
    // reads the same. Both are judged by the parser's flanking classes.
    // Returns the text still to be written for [next], or null when none of
    // it was.
    fun closePendingInline(next: SemanticEvent?): String? {
        val frame = pendingInlineClose ?: return null
        pendingInlineClose = null
        val contentEnd = lastChar
        writeCloser(frame.delimiter)
        closerChar = frame.delimiter[0]
        val text = (next as? Text)?.text ?: return null
        if (pendingSpaces > 0 || pendingWhitespace.isNotEmpty() || !contentEnd.isFlankPunctuation()) return null
        val first = text.firstOrNull() ?: return null
        if (first.isFlankWhitespace() || first.isFlankPunctuation()) return null
        val pair = first.isHighSurrogate() && text.length > 1 && text[1].isLowSurrogate()
        val codePoint = if (pair) {
            0x10000 + ((first.code - 0xD800) shl 10) + (text[1].code - 0xDC00)
        } else first.code
        writeRaw("&#$codePoint;")
        return text.substring(if (pair) 2 else 1)
    }

    // Opens an emphasis span — or, when a span with the same delimiter is
    // already open, a transparent one: the inner span adds nothing visible, and
    // its delimiters would abut the outer ones (`****x****`) in a run that does
    // not re-parse as nesting. The search covers only the spans enclosing the
    // current text — it stops at the first block or link frame, beyond which a
    // span's delimiters are in another block or are scoped to a link label on
    // re-parse. An opener that would directly follow a closer of the same
    // delimiter char (`**a**` + `*` + `**b***`) joins it into one run that no
    // longer re-parses as a closer and openers, so it is written as a tag.
    fun openInline(name: String, delimiter: String) {
        var nested = false
        for (i in blockStack.indices.reversed()) {
            val frame = blockStack[i]
            if (frame is Inline) {
                if (frame.delimiter == delimiter) { nested = true; break }
            } else if (frame !is TaggedInline) break
        }
        when {
            nested -> blockStack.addLast(BlockFrame.Inline(""))
            // A deferred space or hard break will be written between the two.
            closerChar == delimiter[0] && pendingSpaces == 0 && pendingWhitespace.isEmpty() && !pendingHardBreak -> {
                writeRaw("<$name>")
                blockStack.addLast(BlockFrame.TaggedInline())
            }
            else -> {
                writeRaw(delimiter)
                blockStack.addLast(BlockFrame.Inline(delimiter))
                activeDelimiterMask = activeDelimiterMask or delimiterBit(delimiter[0])
            }
        }
    }

    // Escaping a leading block marker is only safe in a Markdown-block context —
    // never inside verbatim `pre`/`code` or a raw frontmatter block.
    fun blockMarkerEscapingAllowed(): Boolean = !inVerbatimText()

    // A soft line break. Spaces deferred from the previous line are trailing
    // and dropped (insignificant around a soft break); the line resets to a
    // fresh Markdown line so a continuation line that begins with a block
    // marker (`# x`, `- x`, …) is still escaped on re-emission.
    fun emitSoftBreak() {
        // A heading is a single line: a soft break inside it is a space.
        if (onHeadingLine()) {
            pendingSpaces = 1
            return
        }
        flushHeldWhitespaceAtLineEnd()
        out("\n")
        atLineStart = true
        freshBlockLine = true
        lineStartDigits = false
    }

    // The delimiter of the emphasis span enclosing the current text, looking
    // past raw inline tags and the transparent frame of a span nested in a span
    // of the same kind (`Inline("")`); null when no span encloses it.
    fun enclosingSpanDelimiter(): String? {
        for (i in blockStack.indices.reversed()) {
            when (val frame = blockStack[i]) {
                is TaggedInline -> continue
                is Inline -> if (frame.delimiter.isNotEmpty()) return frame.delimiter
                else -> return null
            }
        }
        return null
    }

    // Holds the whitespace of a whitespace-only text event: ASCII spaces are
    // deferred, other space separators held as content (see
    // pendingWhitespace) — dropping a no-break space between two spans would
    // make a closer and an opener abut. Tabs are structural and dropped.
    fun holdBlankText(text: String) {
        for (c in text) when {
            c == ' ' -> pendingSpaces++
            c.category == SPACE_SEPARATOR && !inLabel() -> {
                pendingWhitespace += " ".repeat(pendingSpaces) + c
                pendingSpaces = 0
            }
        }
    }

    // [next]: the char written right after this text, null when unknown (see
    // escapeInlineText).
    fun emitText(rawText: String, next: Char?) {
        if (rawText.isEmpty()) return
        // CONTRACT: escapeInlineText runs for ALL text, including label and
        // inline-code content (it's invoked *before* the inLabel() guard below —
        // inline code is captured into a label buffer). It must therefore
        // self-guard every context where escaping is wrong (verbatim text), and
        // handle `inLabel()` scoping itself. Any new label-style capture must
        // keep that guarantee or move this call after the appropriate guard.
        // Outside a label the block separation comes first, so the escaping
        // sees the line start it writes (see prevWrittenChar).
        if (!inLabel()) {
            consumeHeadingSpace()
            beforeContent()
        }
        val text = escapeInlineText(rawText, next)
        if (inLabel()) { appendLabel(text); return }
        val parts = text.split('\n')
        parts.forEachIndexed { i, part ->
            if (i > 0) emitSoftBreak()
            if (part.isNotEmpty()) {
                // Split off the line's trailing ASCII spaces and defer them
                // (see flushPendingSpaces / pendingSpaces): they are emitted
                // only if real content follows on this line, dropped otherwise.
                // Inside an emphasis span any trailing whitespace is held, so
                // the span's closer can precede it (see pendingWhitespace) —
                // but not inside a raw verbatim tag, whose text is kept as
                // written (as is its indentation, see below).
                val verbatim = inVerbatimText()
                val inSpan = !verbatim && enclosingSpanDelimiter() != null
                val contentEnd = if (inSpan) {
                    part.indexOfLast { !it.isFlankWhitespace() } + 1
                } else part.indexOfLast { it != ' ' } + 1
                // Leading spaces on a fresh Markdown line are stripped by the
                // parser (and four would open an indented code block), e.g. the
                // ` tail` after a block-level raw tag closes mid-paragraph. Not
                // inside a raw verbatim tag (`<pre>`, `<textarea>`, …), whose
                // indentation is content.
                val original = part.substring(0, contentEnd).let {
                    // Only ASCII spaces: whitespace held as content (NBSP and
                    // other `Zs`, see pendingWhitespace) is never trimmed.
                    if (atLineStart && freshBlockLine && blockMarkerEscapingAllowed() && !verbatim) it.trimStart(' ')
                    else it
                }
                if (original.isNotEmpty()) {
                    flushPendingSpaces()
                    if (atLineStart) out(buildPrefix(consumeMarker = true))
                    var content = original
                    if (blockMarkerEscapingAllowed()) {
                        if (freshBlockLine) {
                            // At a fresh Markdown line, escape a leading block
                            // marker so paragraph text like "1." / "- x" / "# x"
                            // / "---" / ">" does not re-parse as a different block.
                            content = escapeLeadingBlockMarker(original, entities = textContext() == TEXT_RAW_OR_MARKDOWN)
                        } else if (lineStartDigits) {
                            // Complete a fragmented ordered marker: a "."/")"
                            // right after a bare line-starting digit run.
                            content = escapeLeadingOrderedDelimiter(original, entities = textContext() == TEXT_RAW_OR_MARKDOWN)
                        }
                    }
                    out(content)
                    atLineStart = false
                    // A bare, unescaped digit run starting the line may be the
                    // first half of a fragmented ordered marker (`1\.` parses to
                    // "1" then "."); remember it so the delimiter is escaped next.
                    lineStartDigits = freshBlockLine && blockMarkerEscapingAllowed() &&
                        content == original && original.all { it in '0'..'9' }
                    freshBlockLine = false
                }
                val trailing = part.substring(contentEnd)
                if (trailing.all { it == ' ' }) {
                    pendingSpaces += trailing.length
                } else {
                    pendingWhitespace += " ".repeat(pendingSpaces) + trailing
                    pendingSpaces = 0
                }
            }
        }
    }

    fun endBlock() {
        ensureLineStart()
        pendingBlockSeparator = true
        lastBlock = BLOCK_MARKDOWN
    }

    // Ends a list whose frame has just been popped: nested in an item it only
    // ends the line (the item's own close separates), at top level it is a
    // block like any other.
    fun closeList() {
        val nestedInItem = blockStack.lastOrNull() is ListItem
        if (nestedInItem) {
            ensureLineStart()
            pendingBlockSeparator = false
        } else endBlock()
    }

    // When a block-level mark arrives while one or more `<a>` frames are
    // still collecting Markdown into label buffers, the Markdown `[label](url)`
    // form can't represent the block content. Spill every open link as a raw
    // `<a …>` tag (innermost first); their matching `</a>` will then emit
    // `</a>` on Unmark. The raw tag preserves the link's full attributes (it,
    // unlike Markdown link syntax, can carry them), so any actionable ref or
    // other attribute survives. Anything already buffered into the label
    // streams out inline right after the opening tag.
    fun spillActiveLinks() {
        for (i in blockStack.indices.reversed()) {
            val frame = blockStack[i]
            if (frame !is Link || frame.rawMode) continue
            val labelText = labelBuffers.removeLast().toString()
            val openTag = renderOpenTag("a", frame.attributes)
            // Once this link's label buffer is popped, the *next* spill writes
            // into either an outer label buffer (nested link) or the actual
            // output (the outermost link).
            if (labelBuffers.isEmpty() && onHeadingLine()) {
                // Outermost spilled link on a heading's line: the heading is a
                // single line, so the raw tag stays on it (see rendersAsBlockTag).
                writeRaw("$openTag$labelText")
                frame.rawInline = true
            } else if (labelBuffers.isEmpty()) {
                // Outermost spilled link: a block-level raw tag. Separate it
                // from the preceding block (blank line at a Markdown→tag
                // boundary, newline-tight after another tag) and put the open
                // tag *alone* on its line — only then is it a valid CommonMark
                // HTML-block start, so the heading/paragraph that follows parses
                // as Markdown rather than raw HTML. Any inline label collected
                // before the spill (e.g. a "LIVE" badge) becomes its own block.
                separateBeforeBlockTag()
                frame.rawInItem = opensHtmlBlockInItem(type6 = false)
                writeTag(openTag)
                ensureLineStart()
                lastBlock = BLOCK_TAG
                if (labelText.isNotEmpty()) writeRaw(labelText)
            } else {
                writeTag("$openTag$labelText")
            }
            frame.rawMode = true
        }
    }

    // Raw-HTML open emission shared by tagged events and the untagged
    // catch-all. Splits block-level semantic / form tags onto their own
    // line, and treats HTML void elements as self-closing (no children,
    // no closing tag emitted on the matching Unmark).
    fun handleRawOpen(event: SemanticEvent.Mark, wrapsBlock: Boolean = false) {
        // An inline tag found to wrap a block (see heldRawInline) opens like a
        // block-level tag: alone on its line, at a block boundary. Only then is
        // it a CommonMark HTML-block start — `<b>Lead:` or `Intro <b>` is not,
        // so on re-parse the `<b>` would close at the line end and leave its
        // `</b>` orphaned. Its content and a deferred paragraph follow it.
        if (wrapsBlock) {
            separateBeforeBlockTag()
            writeTag(event.renderOpenTag())
            ensureLineStart()
            lastBlock = BLOCK_TAG
            blockStack.addLast(BlockFrame.TaggedInline(block = true, htmlBlock = true))
            return
        }
        // A deferred non-list paragraph whose first content is a raw tag starts
        // now (as beforeContent does for text): the paragraph's separation and
        // prefix are emitted and atLineStart clears. This also keeps an inline
        // `<a>` at a paragraph start inline — without it, atLineStart would
        // still be true and trip the line-start block-tag rule below, wrongly
        // spilling `<a href>text</a> rest` to a block-level raw tag.
        if (!inLabel() && pendingParagraph) {
            pendingParagraph = false
            startBlock()
        }
        val isVoid = event.name in HTML_VOID_ELEMENTS
        // A void element — and an `<a>` (a block-wrapping link the forward
        // pipeline spills to raw HTML) — only counts as a block-level tag when
        // it lands at a line start; mid-line (e.g. `<input>` after text, or an
        // inline link/autolink) it is inline content.
        val headingLine = onHeadingLine()
        val blockLevelTag = rendersAsBlockTag(event.name)
        if (!inLabel()) {
            // A block-level tag always starts its own line (breaking the
            // current line if needed); an inline tag only separates when it
            // already begins a block.
            if (blockLevelTag) separateBeforeBlockTag()
            else if (atLineStart && pendingSpaces == 0) ensureBlankLine()
        }
        val startsLine = atLineStart && !inLabel()
        // Custom markup opening a top-level block is read back raw for certain:
        // in a list item, a blockquote or after a soft break it is not.
        val custom = ':' in event.name
        val blockName = custom || event.name.lowercase() in HTML_BLOCK_START_TAGS
        val rawCertain = custom && startsLine && afterBlankLine &&
            blockStack.none { it is ListItem || it == Blockquote }
        // The first line of an HTML block is raw text, wherever the block may
        // go on: at a blank line, or as a list item's first line — never in a
        // blockquote, where the parser detects no HTML block.
        val type6 = event.name.lowercase() in HTML_BLOCK_START_TAGS
        if (startsLine && type6 && opensBlock(type6 = true)) rawLine = lineBreaks
        // In a list item the parser never sub-parses an HTML block: all of it
        // is raw text for certain.
        val rawInItem = startsLine && (blockLevelTag || type6) && opensHtmlBlockInItem(type6)
        writeTag(event.renderOpenTag())
        // Block decision captured before writeTag cleared atLineStart. A void
        // element also ends its line outside a heading (a soft break there);
        // on a heading's line only a block-level tag may end it.
        if (blockLevelTag || (isVoid && !inLabel() && !headingLine)) {
            ensureLineStart()
        }
        if (!inLabel()) lastBlock = if (blockLevelTag) BLOCK_TAG else BLOCK_MARKDOWN
        blockStack.addLast(
            if (isVoid) BlockFrame.SelfClosed
            else BlockFrame.TaggedInline(
                block = blockLevelTag,
                verbatim = event.name in VERBATIM_RAW_TAGS,
                htmlBlock = startsLine && (blockLevelTag || blockName),
                rawCertain = rawCertain || rawInItem,
                blockName = blockName,
                openedAtLineBreak = lineBreaks
            )
        )
    }

    // Emit a captured table row. An empty row (a layout spacer) is dropped.
    // The first emitted row resolves the table's column count and the header:
    // a row with `th` cells is the header itself; a headerless row gets an
    // empty header synthesized above it so the output is valid GFM.
    fun emitTableRow(row: BlockFrame.TableRow, table: BlockFrame.Table?) {
        if (row.buffer.isEmpty()) return
        val rowText = row.buffer.toString()
        // The first row must resolve a block separator left by a preceding
        // block inside the table (a `<caption>` renders as one): glued to the
        // caption line, the whole table becomes paragraph continuation text on
        // re-parse, since a table never interrupts a paragraph.
        if (table != null && !table.headerEmitted) {
            ensureBlankLine()
            table.headerEmitted = true
            if (row.hadHeaderCell) {
                out(buildPrefix(consumeMarker = true)); out(rowText); out("\n")
            } else {
                out(buildPrefix(consumeMarker = true))
                out("|"); repeat(row.cellCount) { out("  |") }; out("\n")
            }
            out(buildPrefix(consumeMarker = false))
            out("|"); repeat(row.cellCount) { out(" --- |") }; out("\n")
            if (!row.hadHeaderCell) {
                out(buildPrefix(consumeMarker = false)); out(rowText); out("\n")
            }
        } else {
            ensureLineStart()
            out(buildPrefix(consumeMarker = true)); out(rowText); out("\n")
        }
        atLineStart = true
    }

    // [next]: for a Text event, the char written right after it (see emitText).
    suspend fun processEvent(incoming: SemanticEvent, wrapsBlock: Boolean, next: Char?) {
        var event = incoming

        // Inside a front matter block the events are YAML metadata, not
        // Markdown content: the writer consumes them, and only the closing
        // `frontmatter` unmark (an unmark with nothing open in the writer)
        // falls through.
        val yaml = frontmatter
        if (yaml != null) {
            if (event is Unmark && yaml.depth == 0) {
                frontmatter = null
            } else {
                yaml.collect(event)
                return
            }
        }

        // An emphasis span reopened right where the same span closed continues
        // it: drop the held closer and put the frame back, writing nothing.
        val pendingClose = pendingInlineClose
        if (pendingClose != null) {
            if (event is Mark && !event.isTagged && emphasisDelimiter(event.name) == pendingClose.delimiter) {
                pendingInlineClose = null
                blockStack.addLast(pendingClose)
                return
            }
            val remainder = closePendingInline(event)
            if (remainder != null) event = SemanticEvent.Text(remainder)
        }

        // A list synthesized for an orphan `li` stays open across its
        // siblings — the next orphan joins it instead of opening a list of
        // its own — and closes on the first event that is not another item:
        // left on the stack its indent would apply to everything that follows.
        // One event of lookahead over the stack top, nothing buffered.
        if ((blockStack.lastOrNull() as? BlockFrame.List)?.synthetic == true &&
            !(event is Mark && event.name == "li")
        ) {
            blockStack.removeLast()
            closeList()
        }

        // Content written straight into a list item, with no `p` around it,
        // is the item's first child: a `p` after it is a block of its own, not
        // the text that opens the item's line (else `- LabelDescription`).
        val openItem = blockStack.lastOrNull() as? ListItem
        if (openItem != null && openItem.firstChild &&
            ((event is Text && event.text.isNotBlank()) ||
                (event is Mark && event.name != "p" && event.name != "caption"))
        ) {
            openItem.firstChild = false
        }

        // A `<table>` nested inside a table cell can't be expressed in
        // Markdown — emit it as raw single-line HTML into the cell buffer.
        // The mode is entered when a `table` mark arrives while a cell (or
        // link label) buffer is open, and persists until the matching root
        // close.
        if (htmlTableDepth > 0 ||
            (event is Mark && event.name == "table" && inLabel())
        ) {
            serializeHtmlTableEvent(event)
            return
        }

        // If a block-level mark arrives while any `<a>` is still buffering
        // a label, spill the affected links to raw HTML before dispatching.
        if (event is Mark
            && (event.name in BLOCK_LEVEL_MARK_NAMES || event.name in BLOCK_TAGGED_ELEMENTS)
            && blockStack.any { it is Link && !it.rawMode }
        ) {
            spillActiveLinks()
        }

        // A Markdown block directly inside an inline raw tag (a `<b>` wrapping
        // a list, kept a tag because emphasis cannot span blocks) turns that
        // tag into a block-level one: it must end its line and be followed by
        // a blank line, or the block re-parses as the HTML block's raw text. (A
        // block-level raw tag separates itself — see separateBeforeBlockTag.)
        if (event is Mark && !event.isTagged && !inLabel()
            && event.name in BLOCK_LEVEL_MARK_NAMES
            && blockStack.lastOrNull() is TaggedInline
        ) {
            ensureLineStart()
            lastBlock = BLOCK_TAG
        }

        // Tagged events (HTML-derived) pass through as raw HTML. At a block
        // boundary (atLineStart with a pending separator) emit the blank line
        // so the next Markdown block parses correctly after the raw HTML.
        if (event is Mark && event.isTagged) {
            handleRawOpen(event, wrapsBlock)
            flush()
            return
        }
        if (event is Unmark && event.isTagged) {
            val frame = blockStack.lastOrNull()
            if (frame is SelfClosed) {
                blockStack.removeLast()
                flush()
                return
            }
            // The close mirrors its own open (see rendersAsBlockTag, and
            // handleRawOpen's wrapsBlock, which forces block).
            val blockTagged = if (frame is TaggedInline) frame.closesAsBlock()
                else rendersAsBlockTag(event.name)
            if (blockTagged) {
                // A raw tag that is not a block-level one only needs a line of
                // its own (its close tag interrupts a paragraph); a blank line
                // before it would be content in raw text.
                if (frame is TaggedInline && !frame.block) {
                    pendingHardBreak = false
                    ensureLineStart()
                } else separateBeforeBlockTag()
                writeTag("</${event.name}>")
                ensureLineStart()
                lastBlock = BLOCK_TAG
            } else {
                writeRaw("</${event.name}>")
            }
            if (frame is TaggedInline) {
                blockStack.removeLast()
            }
            // If the closing tag returned us to a block-level context
            // (no inline frame open above), treat it as a block boundary
            // so the next Markdown block parses with a blank line before it.
            // A list item's own text is the exception: it is inline flow with
            // no `p` frame around it, so the item's paragraph goes on (text
            // after the tag stays on its line, a `<br>` stays a hard break).
            if (!blockTagged && !inLabel() && blockStack.none { it.isInline() } &&
                blockStack.lastOrNull() !is ListItem
            ) {
                endBlock()
            }
            flush()
            return
        }

        when (event) {

            is Text -> {
                val text = event.text
                // Pure whitespace text outside verbatim pre/code carries no
                // content; it must never resolve a pending `<br>` (a moot hard
                // break followed only by whitespace before a block boundary has
                // to vanish — that resolution happens in `beforeContent`).
                val blank = !inLabel() && !inPreCode
                    && text.isNotEmpty() && text.isBlank()
                    && blockStack.none { it is Pre || it is Code }
                when {
                    // Structural inter-block whitespace at a line start — e.g. the
                    // literal `\n` the parser emits between two block-level HTML
                    // tags (`<header>\n<nav>`). Drop it entirely (leaving block
                    // bookkeeping untouched so adjacent tags stay tight); emitting
                    // it would amplify one newline into a blank line and grow on
                    // every render→parse round-trip.
                    blank && atLineStart -> { }
                    // A soft line break mid-line: the parser re-emits a Markdown
                    // soft break (`a\nb`) as a standalone whitespace `\n` text
                    // event once the line's real content has streamed (so we are
                    // not at line start). Preserve it — dropping it merges the
                    // two lines (`ab`) and breaks the render→parse→render
                    // fixpoint. Excluded when a hard break is pending: there the
                    // `\n` is the source newline that follows the `<br>` and is
                    // absorbed by it (the hard break renders when the next real
                    // content arrives, via beforeContent).
                    blank && '\n' in text && !pendingHardBreak -> {
                        holdBlankText(text.substringBefore('\n'))
                        emitSoftBreak()
                    }
                    // Mid-line whitespace with no newline: defer ASCII spaces
                    // (re-emitted before real same-line content, dropped at a
                    // line/block boundary), hold other space separators.
                    blank -> holdBlankText(text)
                    else -> {
                        emitText(text, next)
                        if (text.isNotEmpty() && !inLabel()) lastBlock = BLOCK_MARKDOWN
                    }
                }
            }

            is Mark -> when (event.name) {

                "frontmatter" -> {
                    ensureBlankLine()
                    out("---\n")
                    atLineStart = true
                    pendingBlockSeparator = false
                    blockStack.addLast(Frontmatter)
                    frontmatter = YamlWriter { out(it) }
                }

                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    val level = event.name.substring(1).toInt()
                    startBlock()
                    out("#".repeat(level))
                    // The `#`s are this line's start content — heading text that
                    // follows is mid-line and must not be marker-escaped.
                    freshBlockLine = false
                    lineStartDigits = false
                    // The separating space is deferred so an empty heading
                    // renders as bare `#` (see consumeHeadingSpace).
                    headingSpacePending = true
                    blockStack.addLast(Heading)
                }

                // A `<caption>` has no GFM equivalent, so it degrades to the
                // block preceding its table — which is what a paragraph does.
                "p", "caption" -> {
                    // Inside a list item awaiting its first content, the `p`
                    // becomes inline — the item marker already opens the line.
                    val parent = blockStack.lastOrNull()
                    if (parent is ListItem && parent.firstChild) {
                        parent.firstChild = false
                        blockStack.addLast(BlockFrame.Paragraph(suppressClose = true))
                    } else {
                        // A paragraph after the item's own text on the
                        // marker line starts on a line of its own — as an
                        // indented continuation, the same shape a second `p`
                        // in an item takes (see the two-paragraph item test).
                        if (parent is ListItem && !atLineStart && !inLabel()) ensureLineStart()
                        // Loose inline text still open on the current line is a
                        // block of its own (`A<p>B</p>` is two blocks in HTML), so
                        // the paragraph needs a blank line, not the bare newline
                        // that re-parses as a soft break inside one paragraph.
                        else if (!atLineStart && !inLabel()) pendingBlockSeparator = true
                        // Defer startBlock() until the first real content (see
                        // pendingParagraph): an empty paragraph then emits nothing.
                        pendingParagraph = true
                        blockStack.addLast(BlockFrame.Paragraph())
                    }
                }

                "hr" -> {
                    startBlock()
                    out("---")
                    atLineStart = false
                    freshBlockLine = false
                    lineStartDigits = false
                    endBlock()
                    blockStack.addLast(SelfClosed)
                }

                "blockquote" -> {
                    ensureBlankLine()
                    blockStack.addLast(Blockquote)
                }

                "ul" -> {
                    val nested = blockStack.lastOrNull() is ListItem
                    if (nested) {
                        (blockStack.last() as ListItem).firstChild = false
                        ensureLineStart()
                    } else ensureBlankLine()
                    blockStack.addLast(BlockFrame.List(ordered = false))
                }

                "ol" -> {
                    val nested = blockStack.lastOrNull() is ListItem
                    if (nested) {
                        (blockStack.last() as ListItem).firstChild = false
                        ensureLineStart()
                    } else ensureBlankLine()
                    val start = event.attributes["start"]?.toIntOrNull() ?: 1
                    blockStack.addLast(BlockFrame.List(ordered = true, counter = start))
                }

                "li" -> {
                    // An item whose list wrapper was unwrapped upstream (a
                    // container no rule preserved) has no list to take its
                    // marker from. Degrade to a synthesized list rather than
                    // failing the whole render — a missing wrapper must never
                    // cost the document. Inside an open item the orphans are
                    // that item's sub-list (an unwrapped nested container),
                    // so the synthesized list nests exactly as a `ul` would;
                    // consecutive orphans share it (see the lazy pop above).
                    val list = blockStack.lastOrNull() as? BlockFrame.List
                        ?: BlockFrame.List(ordered = false, synthetic = true).also {
                            val parent = blockStack.lastOrNull()
                            if (parent is ListItem) {
                                parent.firstChild = false
                                ensureLineStart()
                            } else ensureBlankLine()
                            blockStack.addLast(it)
                        }
                    ensureLineStart()
                    pendingBlockSeparator = false
                    val marker = if (list.ordered) "${list.counter}. " else "- "
                    list.counter++
                    list.indent = " ".repeat(marker.length)
                    blockStack.addLast(BlockFrame.ListItem(pendingMarker = marker))
                }

                "pre" -> {
                    startBlock()
                    blockStack.addLast(Pre)
                }

                "code" -> {
                    if (blockStack.lastOrNull() is Pre) {
                        val lang = event.attributes["class"]
                            ?.removePrefix("language-")
                            ?: ""
                        // Rebuild the container prefix when the fence opens a fresh
                        // line (e.g. the `<pre>` wrapped a heading before the
                        // `<code>`, so the heading's line-end left us at line start).
                        // Without this the opening fence drops to column 0 and, on
                        // re-parse, escapes its list item / blockquote.
                        if (atLineStart) out(buildPrefix(consumeMarker = false))
                        out("```")
                        out(lang)
                        ensureLineStart()
                        inPreCode = true
                    } else {
                        // Inline code content is captured so the backtick fence
                        // length (and any space padding) can be chosen once the
                        // full content is known — see renderInlineCode.
                        openLabelBuffer(before = '`')
                    }
                    blockStack.addLast(Code)
                }

                // Opening an emphasis frame can only *set* a bit in the mask, never
                // clear one, so the open path is O(1): OR in the new frame's bit. The
                // full refreshActiveDelimiters() recompute is needed only on *close*,
                // where em and strong share the `*` bit (clearing it on strong-close
                // would wrongly drop emphasis while an em is still open).
                "strong", "em", "del", "mark", "sup" ->
                    openInline(event.name, emphasisDelimiter(event.name)!!)

                "a" -> {
                    openLabelBuffer(before = '[')
                    blockStack.addLast(
                        BlockFrame.Link(
                            href = event.attributes["href"] ?: "",
                            attributes = event.attributes
                        )
                    )
                }

                "img" -> {
                    val src = event.attributes["src"] ?: ""
                    val context = textContext()
                    val alt = (event.attributes["alt"] ?: "").let {
                        if (context == TEXT_VERBATIM) it
                        else escapeAltText(it, entities = context == TEXT_RAW_OR_MARKDOWN)
                    }
                    if (inLabel()) appendLabel("![$alt]($src)")
                    else {
                        consumeHeadingSpace()
                        beforeContent()
                        flushPendingSpaces()
                        if (atLineStart) out(buildPrefix(consumeMarker = true))
                        out("![")
                        out(alt)
                        out("](")
                        out(src)
                        out(")")
                        atLineStart = false
                        freshBlockLine = false
                        lineStartDigits = false
                    }
                    blockStack.addLast(SelfClosed)
                }

                "br" -> {
                    pendingHardBreak = true
                    blockStack.addLast(SelfClosed)
                }

                "table" -> {
                    // Don't startBlock — the first `tr` will emit its own
                    // line prefix. We just need the blank separator from any
                    // prior block.
                    ensureBlankLine()
                    blockStack.addLast(BlockFrame.Table())
                }

                "thead", "tbody" -> {
                    blockStack.addLast(BlockFrame.TableSection(event.name))
                }

                "tr" -> {
                    // Capture the row's output so an empty row can be dropped
                    // and the column count is known before the header line is
                    // emitted (at `tr` close).
                    val row = BlockFrame.TableRow()
                    rowCapture = row.buffer
                    blockStack.addLast(row)
                }

                "th", "td" -> {
                    // GFM has no colspan — a `colspan=N` cell occupies N
                    // columns (rendered as the content plus N-1 empty cells).
                    val colspan = event.attributes["colspan"]
                        ?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val row = blockStack.lastOrNull { it is TableRow }
                        as? TableRow
                    if (row != null) {
                        if (row.buffer.isEmpty()) out("|")
                        row.cellCount += colspan
                        if (event.name == "th") row.hadHeaderCell = true
                    }
                    out(" ")
                    openLabelBuffer(before = ' ')
                    blockStack.addLast(
                        BlockFrame.TableCell(isHeader = event.name == "th", colspan = colspan)
                    )
                }

                else -> handleRawOpen(event, wrapsBlock)
            }

            is Unmark -> {
                val frame = blockStack.removeLast()
                when (event.name) {

                    "frontmatter" -> {
                        ensureLineStart()
                        writeRaw("---")
                        endBlock()
                    }

                    "h1", "h2", "h3", "h4", "h5", "h6" -> {
                        // An empty heading never consumed its deferred space.
                        headingSpacePending = false
                        endBlock()
                    }

                    "p", "caption" -> {
                        when {
                            // Deferred paragraph that never received content —
                            // a no-op (it emitted nothing, so nothing to close).
                            pendingParagraph -> pendingParagraph = false
                            (frame as Paragraph).suppressClose -> {
                                // first-content paragraph in a list item — leave
                                // pendingBlockSeparator alone so the list-item
                                // line just ends.
                                ensureLineStart()
                            }
                            else -> endBlock()
                        }
                    }

                    "blockquote" -> endBlock()

                    "ul", "ol" -> closeList()

                    "li" -> ensureLineStart()

                    "pre" -> endBlock()

                    "code" -> {
                        if (inPreCode) {
                            ensureLineStart()
                            out(buildPrefix(consumeMarker = false))
                            out("```")
                            inPreCode = false
                        } else {
                            val content = labelBuffers.removeLast().toString()
                            writeRaw(renderInlineCode(content))
                        }
                    }

                    "strong", "em", "del", "mark", "sup" -> when {
                        // Opened as a tag, where a delimiter would have joined
                        // the closer before it (see openInline).
                        frame is TaggedInline -> writeRaw("</${event.name}>")
                        frame !is Inline -> error("<${event.name}> closed over $frame")
                        // A transparent (nested) span wrote no delimiter.
                        frame.delimiter.isEmpty() -> {}
                        // `^a^^b^` re-parses as the two spans it is (a `^`
                        // toggles), so adjacent superscripts — a run of
                        // citations — stay separate.
                        frame.delimiter == "^" -> writeCloser("^")
                        else -> pendingInlineClose = frame
                    }

                    "a" -> {
                        val link = frame as Link
                        if (link.rawInline) {
                            // Spilled inline on a heading's line — close it there.
                            writeRaw("</a>")
                        } else if (link.rawMode) {
                            // Already spilled to a raw `<a …>` tag — close it as a
                            // block-level raw tag (blank line at the Markdown→tag
                            // boundary), mirroring its block-level open.
                            separateBeforeBlockTag()
                            writeTag("</a>")
                            ensureLineStart()
                            lastBlock = BLOCK_TAG
                        } else {
                            val label = labelBuffers.removeLast().toString()
                            writeRaw("[$label](${link.href})")
                        }
                    }

                    "img", "br", "hr" -> { /* emitted on Mark */ }

                    "table" -> endBlock()

                    // The header separator is emitted at the first row's close
                    // (see emitTableRow), so thead/tbody just pop their frame.
                    "thead", "tbody" -> { /* no-op */ }

                    "tr" -> {
                        rowCapture = null
                        val table = blockStack.lastOrNull { it is Table }
                            as? Table
                        emitTableRow(frame as TableRow, table)
                    }

                    "th", "td" -> {
                        // GFM tables process cell-level `\|` escapes before
                        // inline parsing, so every literal `|` in the cell
                        // content must be escaped — even inside code spans or
                        // emphasis ("including inside other inline spans", GFM
                        // table example 200). Structural pipes are emitted
                        // outside this buffer, so escaping it wholesale is safe.
                        val content = labelBuffers.removeLast().toString()
                        out(content.replace("|", "\\|"))
                        out(" |")
                        // Empty padding cells for a colspan > 1.
                        repeat((frame as TableCell).colspan - 1) { out("  |") }
                    }

                    else -> {
                        if (frame is SelfClosed) {
                            // void element — no closing tag
                        } else {
                            // The close mirrors its own open (see rendersAsBlockTag,
                            // and handleRawOpen's wrapsBlock, which forces block).
                            val blockTagged = if (frame is TaggedInline) frame.closesAsBlock()
                                else rendersAsBlockTag(event.name)
                            if (blockTagged) {
                                // See the tagged close above.
                                if (frame is TaggedInline && !frame.block) {
                                    pendingHardBreak = false
                                    ensureLineStart()
                                } else separateBeforeBlockTag()
                                writeTag("</${event.name}>")
                                ensureLineStart()
                                lastBlock = BLOCK_TAG
                            } else {
                                writeRaw("</${event.name}>")
                                if (!inLabel() && blockStack.none { it.isInline() }) {
                                    endBlock()
                                }
                            }
                        }
                    }
                }
            }
        }

        flush()
    }

    // One event of lookahead, so that what follows an event can decide how it
    // is written. Held, and released by the next event (or the stream's end):
    //  - heldText: a text whose last char is inline syntax or not depending on
    //    the char written after it (`$b` is math, `$5` is not) — the parser
    //    splits text at every such char (`\$` + `b`), so the next char is
    //    usually in the next event; or a text in an emphasis span ending in
    //    whitespace, which moves after the span's closer if that comes next;
    //  - heldOpeners: emphasis openers, which move after whitespace starting
    //    the text that comes next (issue #167).
    // The reordering keeps an opener left-flanking and a closer right-flanking
    // wherever the text is written — also inside a link label or a table cell,
    // where the whitespace the main output defers (pendingWhitespace) is not.
    var heldText: SemanticEvent.Text? = null
    // The held text is whitespace moved past a closer: content, not the
    // structural whitespace a blank text event usually is (see emitMovedWhitespace).
    var heldTextMoved = false
    val heldOpeners = mutableListOf<SemanticEvent.Mark>()

    // Writes whitespace moved out of an emphasis span, before its opener or
    // after its closer. It is content: a tab or a no-break space is held like
    // the whitespace ending a span's text (pendingWhitespace), never dropped
    // as a blank text event's is. In a label, where nothing is deferred, it is
    // ordinary label text.
    suspend fun emitMovedWhitespace(ws: String) {
        if (inLabel()) {
            processEvent(SemanticEvent.Text(ws), wrapsBlock = false, next = null)
            return
        }
        // A line break must not land before a closer still held.
        if ('\n' in ws) closePendingInline(next = null)
        for (c in ws) when {
            // At a line start, spaces and tabs would indent the line — four
            // open an indented code block — and the parser strips them anyway;
            // right after a heading's `#`s the separating space is written.
            (c == ' ' || c == '\t') && (atLineStart || pendingParagraph || headingSpacePending) -> {}
            c == ' ' -> pendingSpaces++
            c == '\n' -> if (!atLineStart && !pendingHardBreak) emitSoftBreak()
            else -> {
                pendingWhitespace += " ".repeat(pendingSpaces) + c
                pendingSpaces = 0
            }
        }
        flush()
    }

    fun SemanticEvent.isEmphasisMark(): Boolean =
        this is Mark && !isTagged && emphasisDelimiter(name) != null

    fun SemanticEvent.isEmphasisUnmark(): Boolean =
        this is Unmark && !isTagged && emphasisDelimiter(name) != null

    fun holdsText(event: SemanticEvent): Boolean {
        if (event !is Text) return false
        val last = event.text.lastOrNull() ?: return false
        // A `<` is judged by the chars after it, up to past the text's end.
        return last in LOOKAHEAD_CHARS || '<' in event.text ||
            (last.isFlankWhitespace() && enclosingSpanDelimiter() != null)
    }

    suspend fun process(event: SemanticEvent, wrapsBlock: Boolean = false) {
        if (frontmatter != null) {
            processEvent(event, wrapsBlock, next = null)
            return
        }
        var current = event
        if (heldOpeners.isNotEmpty()) {
            if (current.isEmphasisMark()) {
                heldOpeners += current as Mark
                return
            }
            // Whitespace with nothing yet to open the span for moves out in
            // front of it, and the openers stay held; a span closing with only
            // whitespace in it is dropped — its delimiters around nothing would
            // re-parse as literal text, or as a span around what follows.
            if (current is Text && current.text.all { it.isFlankWhitespace() }) {
                emitMovedWhitespace(current.text)
                return
            }
            if (current is Unmark && current.isEmphasisUnmark() && current.name == heldOpeners.last().name) {
                heldOpeners.removeLast()
                return
            }
            val leading = (current as? Text)?.text?.takeWhile { it.isFlankWhitespace() } ?: ""
            if (leading.isNotEmpty()) emitMovedWhitespace(leading)
            for (opener in heldOpeners) processEvent(opener, wrapsBlock = false, next = null)
            heldOpeners.clear()
            if (leading.isNotEmpty()) {
                val rest = (current as Text).text.substring(leading.length)
                if (rest.isEmpty()) return
                current = SemanticEvent.Text(rest)
            }
        }
        val held = heldText
        if (held != null) {
            val moved = heldTextMoved
            heldText = null
            heldTextMoved = false
            val content = held.text.trimEnd { it.isFlankWhitespace() }
            if (current.isEmphasisUnmark() && content.length < held.text.length) {
                if (content.isNotEmpty()) {
                    processEvent(SemanticEvent.Text(content), wrapsBlock = false, next = current.leadingChar())
                }
                processEvent(current, wrapsBlock, next = null)
                // The whitespace may move past an enclosing span's closer too.
                heldText = SemanticEvent.Text(held.text.substring(content.length))
                heldTextMoved = true
                return
            }
            if (moved) emitMovedWhitespace(held.text)
            else processEvent(held, wrapsBlock = false, next = current.leadingChar())
        }
        when {
            holdsText(current) -> heldText = current as Text
            current.isEmphasisMark() -> heldOpeners += current as Mark
            else -> processEvent(current, wrapsBlock, next = null)
        }
    }

    // An inline raw tag (a `<b>` kept a tag because it wraps a list) cannot know
    // at its open whether a block follows inside it, and once `<b>Lead:` is
    // written the tag can no longer be moved onto a line of its own. So its
    // events are held and replayed: as they came when the tag closes first, or
    // with the tag opened alone on its line (handleRawOpen's `wrapsBlock`) when
    // a block arrives inside it. The held content is re-rendered in its new
    // position, so it is escaped for where it lands. The hold ends at the tag's
    // close or at the first block inside it, whichever comes first, and covers
    // only the phrasing tags in HELD_RAW_INLINE_TAGS — never a `div`, a table or
    // custom markup (`<x:think>`), which stream as they arrive. A tag inside a
    // heading is not held: a heading is one line, which a block tag would break
    // either way.
    var heldRawInline: MutableList<SemanticEvent>? = null
    var heldRawInlineDepth = 0

    fun holdsRawInline(event: SemanticEvent): Boolean =
        event is Mark && frontmatter == null && htmlTableDepth == 0
            && !inLabel() && !inPreCode
            && (event.isTagged || event.name !in MARKDOWN_NATIVE_MARK_NAMES)
            && event.name in HELD_RAW_INLINE_TAGS
            && blockStack.none { it == Heading }

    suspend fun route(event: SemanticEvent) {
        val held = heldRawInline
        if (held == null) {
            if (holdsRawInline(event)) {
                heldRawInline = mutableListOf(event)
                heldRawInlineDepth = 1
            } else {
                process(event)
            }
            return
        }
        if (event is Mark && event.name in MARKDOWN_BLOCK_CONTENT_TAGS) {
            heldRawInline = null
            process(held.first(), wrapsBlock = true)
            for (i in 1 until held.size) route(held[i])
            route(event)
            return
        }
        held += event
        when (event) {
            is Mark -> heldRawInlineDepth++
            is Unmark -> heldRawInlineDepth--
            is Text -> {}
        }
        if (heldRawInlineDepth == 0) {
            heldRawInline = null
            process(held.first())
            for (i in 1 until held.size) route(held[i])
        }
    }

    collect { route(it) }

    // An inline raw tag left open by an unbalanced stream.
    while (true) {
        val held = heldRawInline ?: break
        heldRawInline = null
        process(held.first())
        for (i in 1 until held.size) route(held[i])
    }

    // An event held until the next one is the last thing in the stream.
    for (opener in heldOpeners) processEvent(opener, wrapsBlock = false, next = null)
    heldOpeners.clear()
    heldText?.let {
        heldText = null
        if (heldTextMoved) emitMovedWhitespace(it.text)
        else processEvent(it, wrapsBlock = false, next = '\n')
    }

    // A stream ending on an emphasis close (no enclosing block) still owes it,
    // and the whitespace held inside it.
    closePendingInline(next = null)
    flushHeldWhitespaceAtLineEnd()
    flush()
}

/**
 * Collects [asMarkdown] into a single Markdown string.
 */
public suspend fun Flow<SemanticEvent>.renderMarkdown(): String =
    asMarkdown().joinToString()

// Kinds of the most recently emitted block-level item, used to decide block
// separation in [asMarkdown] (blank line at Markdown↔tag boundaries, tight
// between two adjacent tags).
private const val BLOCK_NONE = 0
private const val BLOCK_TAG = 1
private const val BLOCK_MARKDOWN = 2

private sealed interface BlockFrame {
    fun isInline(): Boolean = when (this) {
        is Paragraph, Heading, is Inline, is Link, is TableCell, is TaggedInline -> true
        else -> false
    }
    data object Heading : BlockFrame
    class Paragraph(val suppressClose: Boolean = false) : BlockFrame
    data object Blockquote : BlockFrame
    class List(
        val ordered: Boolean,
        var counter: Int = 1,
        var indent: String = "",
        // Synthesized for an orphan `li` (no `ul`/`ol` mark), so the `li` close
        // knows to pop it again — see the `li` branches.
        val synthetic: Boolean = false
    ) : BlockFrame
    class ListItem(
        var pendingMarker: String?,
        var firstChild: Boolean = true
    ) : BlockFrame
    data object Pre : BlockFrame
    data object Code : BlockFrame
    class Inline(val delimiter: String) : BlockFrame
    class Link(
        val href: String,
        val attributes: Map<String, String>,
        var rawMode: Boolean = false,
        // Spilled inline on a heading's line (see spillActiveLinks), so its
        // `</a>` closes there too instead of as a block-level tag.
        var rawInline: Boolean = false,
        // Spilled at a block boundary in a list item: raw text for certain.
        var rawInItem: Boolean = false
    ) : BlockFrame
    data object SelfClosed : BlockFrame
    // A raw HTML tag, transparent to the inline context around it.
    // `block`: its tags render as a block-level raw tag on lines of their own —
    // decided once at the open (rendersAsBlockTag, or forced by handleRawOpen's
    // `wrapsBlock` when it wraps a block); every close mirrors it.
    // `verbatim`: a raw tag whose text is kept as written (see VERBATIM_RAW_TAGS).
    // `htmlBlock`: its open tag may start an HTML block on re-parse — written at
    // a line start, and a type-6 block tag, a block-level raw tag alone on its
    // line (type 7) or custom markup — so its text may be read raw.
    // `rawCertain`: custom markup opening a top-level block, whose content the
    // parser never reads as Markdown (see textContext).
    class TaggedInline(
        val block: Boolean = false,
        val verbatim: Boolean = false,
        val htmlBlock: Boolean = false,
        val rawCertain: Boolean = false,
        val blockName: Boolean = false,
        val openedAtLineBreak: Int = 0
    ) : BlockFrame
    data object Frontmatter : BlockFrame
    class Table(var columnCount: Int = 0, var headerEmitted: Boolean = false) : BlockFrame
    class TableSection(val name: String) : BlockFrame
    class TableRow(
        val buffer: StringBuilder = StringBuilder(),
        var cellCount: Int = 0,
        var hadHeaderCell: Boolean = false
    ) : BlockFrame
    class TableCell(val isHeader: Boolean, val colspan: Int = 1) : BlockFrame
}

// Mark names that introduce a block-level structure in Markdown output.
// When one arrives while an `<a>` is still buffering a label, the link
// can no longer be represented as `[label](url)` and must spill to raw
// HTML. (`li`, `tr`, `thead`, `tbody`, `th`, `td` only appear inside a
// block parent that already triggered the spill — listing them here is
// belt-and-braces.)
// Phrasing tags held by the renderer until their close or the first block
// inside them (see heldRawInline): the emphasis `expressEmphasisInMarkdown`
// keeps as a raw tag when it wraps a block, and its inline siblings. Only tags
// a reader expects to be short — never a block-capable wrapper or custom
// markup, whose content must stream.
private val HELD_RAW_INLINE_TAGS = setOf(
    "b", "i", "em", "strong", "s", "strike", "del", "u", "mark",
    "small", "sub", "sup", "ins", "q", "cite",
)

// Raw tags whose text content, indentation included, is kept as written.
private val VERBATIM_RAW_TAGS = setOf("pre", "textarea", "code", "listing", "xmp", "plaintext") +
    HTML_RAW_TEXT_ELEMENTS

private val BLOCK_LEVEL_MARK_NAMES = setOf(
    "h1", "h2", "h3", "h4", "h5", "h6",
    "p", "hr", "blockquote",
    "ul", "ol", "li",
    "pre",
    "table", "thead", "tbody", "tr", "caption"
)

// Raw tags that stay on a heading's line even where they would otherwise render
// as a block (see `rendersAsBlockTag`): phrasing content that the parser reads
// back as an inline tag mid-line. Deliberately absent: `iframe` and `textarea`
// (dropped mid-line, see the parser's `INLINE_DISALLOWED_TAGS`), `select` and
// `datalist` (their `option`s re-parse as literal text), and every element that
// can hold Markdown blocks (`object`, `menu`, `details`, `section`, `dialog`…),
// whose tags re-parse as literal text inside a heading.
private val HEADING_INLINE_TAGS = setOf(
    "a", "button", "label",
    "input", "img", "br", "wbr", "embed", "area"
)

// HTML5 block-level semantic and form-associated elements. Each is rendered
// with newlines around its content (no indentation), so the raw HTML reads
// cleanly inside Markdown source.
private val BLOCK_TAGGED_ELEMENTS = setOf(
    // sectioning content
    "section", "nav", "article", "aside",
    "header", "footer", "main", "hgroup",
    // figure
    "figure", "figcaption",
    // interactive / disclosure
    "details", "summary", "dialog",
    // form-associated
    "form", "fieldset", "legend",
    "button", "select", "textarea",
    "optgroup", "option", "datalist",
    // list container with no Markdown equivalent — `simplifyHtml` keeps the
    // `<menu>` wrapper and nests a `ul` inside it, so the tag must be
    // block-level for the blank line that lets that list parse on re-read
    "menu",
    // embedded documents — `simplifyHtml` renders the document a same-origin
    // frame embeds inside its tag, so the tag needs the blank line that lets
    // that content parse as Markdown on re-read
    "iframe", "frame", "object",
    // misc block-level semantics
    "address", "search"
)

/**
 * The mark names this renderer treats as block-level content — the union of the
 * block-level Markdown marks and the block-level raw HTML tags. A buffering
 * `<a>` label that contains any of these can no longer render as `[label](url)`
 * and must spill to a raw `<a …>` tag.
 *
 * Exposed publicly so a sibling module that must replicate this predicate
 * (markanywhere-html's `LINK_BLOCK_CONTENT_TAGS`, used by the ref encoder to
 * decide inline-vs-block ref encoding) can verify its mirror against this set in
 * a CI test, instead of the two drifting silently. The renderer itself stays
 * ref-agnostic; this is only a name set.
 */
public val MARKDOWN_BLOCK_CONTENT_TAGS: Set<String> =
    BLOCK_LEVEL_MARK_NAMES + BLOCK_TAGGED_ELEMENTS

/**
 * The mark names this renderer has native Markdown syntax for — the exact set
 * of names dispatched by the untagged `Mark` branch, rather than falling
 * through to the raw-tag path.
 *
 * This is what [SemanticEvent.Marked.isTagged] means at render time: an
 * **untagged** mark named here renders as Markdown (`em` → `*…*`, `ul` → `- `,
 * `h2` → `## `), while everything else — tagged, or untagged with a name
 * outside this set — renders as a literal tag.
 *
 * Exposed publicly so a producer can decide, at the point where the knowledge
 * lives, whether a mark it emits should be tagged: markanywhere-html's
 * `simplifyHtml` tags exactly the elements *not* in this set, so its output
 * stream says how it must render instead of leaving the decision to a name
 * lookup here. `SimplifyHtmlTest` verifies its mirror against this set, and
 * `MarkdownRenderingTest` pins the set itself to the renderer's actual
 * behaviour, so neither can drift silently.
 */
public val MARKDOWN_NATIVE_MARK_NAMES: Set<String> = setOf(
    "frontmatter",
    "h1", "h2", "h3", "h4", "h5", "h6",
    "p", "blockquote", "hr", "br",
    "ul", "ol", "li",
    "pre", "code",
    "strong", "em", "del", "mark", "sup",
    "a", "img",
    "table", "thead", "tbody", "tr", "th", "td", "caption",
)

// Renders an inline code span (CommonMark §6.3). The backtick fence is one
// longer than the longest backtick run in the content, so a literal backtick
// in the content can never close the span. A single space is padded on each
// side when the content would otherwise be misread on re-parse — it begins or
// ends with a backtick, or it is space-padded around non-space content (the
// parser strips that one leading/trailing space back out). Content that is
// empty or entirely spaces needs no padding.
private fun renderInlineCode(content: String): String {
    var maxRun = 0
    var run = 0
    for (c in content) {
        if (c == '`') {
            run++
            if (run > maxRun) maxRun = run
        } else run = 0
    }
    val fence = "`".repeat(maxRun + 1)
    val hasNonSpace = content.any { it != ' ' }
    val needsPad = hasNonSpace && (
        content.first() == '`' ||
            content.last() == '`' ||
            (content.first() == ' ' && content.last() == ' ')
    )
    val pad = if (needsPad) " " else ""
    return "$fence$pad$content$pad$fence"
}

// Escapes (see escapeCharAt) a leading block marker in paragraph text so it round-trips
// as text instead of re-parsing as a different block construct. Applied only at
// a fresh Markdown line in a block context (see emitText). Covers: ATX heading
// (`#`{1,6} + space/EOL), blockquote (`>`), thematic break (≥3 of `-`/`*`/`_`),
// bullet list (`-`/`+`/`*` + space/EOL), ordered list (digits + `.`/`)` +
// space/EOL). Anything else is returned unchanged.
private fun escapeLeadingBlockMarker(line: String, entities: Boolean): String {
    val c = line[0]
    when (c) {
        '>' -> return line.escapeCharAt(0, entities)
        '#' -> {
            var n = 0
            while (n < line.length && line[n] == '#') n++
            if (n in 1..6 && (n == line.length || line[n] == ' ' || line[n] == '\t')) {
                return line.escapeCharAt(0, entities)
            }
            return line
        }
        '-', '*', '_' -> {
            if (isThematicBreak(line)) return line.escapeCharAt(0, entities)
            // `_` is never a bullet; `-`/`*` are when followed by a space/EOL.
            if (c != '_' && (line.length == 1 || line[1] == ' ' || line[1] == '\t')) {
                return line.escapeCharAt(0, entities)
            }
            return line
        }
        '+' -> {
            if (line.length == 1 || line[1] == ' ' || line[1] == '\t') return line.escapeCharAt(0, entities)
            return line
        }
        in '0'..'9' -> {
            var n = 0
            while (n < line.length && line[n] in '0'..'9') n++
            // CommonMark caps an ordered-list start marker at 9 digits.
            if (n in 1..9 && n < line.length && (line[n] == '.' || line[n] == ')')) {
                val after = n + 1
                if (after == line.length || line[after] == ' ' || line[after] == '\t') {
                    return line.escapeCharAt(n, entities)
                }
            }
            return line
        }
        else -> return line
    }
}

// Escapes a leading ordered-list delimiter (`.` or `)` followed by a space/tab
// or end-of-line) — used to complete the escape of an ordered marker whose
// digits were emitted in a preceding text fragment (`1\.` → "1" then ".").
private fun escapeLeadingOrderedDelimiter(s: String, entities: Boolean): String {
    val c = s[0]
    if ((c == '.' || c == ')') && (s.length == 1 || s[1] == ' ' || s[1] == '\t')) {
        return s.escapeCharAt(0, entities)
    }
    return s
}

// This string with the char at [index] escaped: as a numeric character
// reference when [entities] (see textContext), else with a backslash.
private fun String.escapeCharAt(index: Int, entities: Boolean): String =
    substring(0, index) + (if (entities) "&#${this[index].code};" else "\\${this[index]}") + substring(index + 1)

// A thematic break (CommonMark §4.1): a line of ≥3 of the same marker char
// (`-`, `*`, or `_`), with only spaces/tabs allowed between them.
private fun isThematicBreak(line: String): Boolean {
    val marker = line[0]
    if (marker != '-' && marker != '*' && marker != '_') return false
    var count = 0
    for (ch in line) when (ch) {
        marker -> count++
        ' ', '\t' -> { }
        else -> return false
    }
    return count >= 3
}

private fun SemanticEvent.Mark.renderOpenTag(): String = renderOpenTag(name, attributes)

private fun renderOpenTag(name: String, attributes: Map<String, String>): String = buildString {
    +'<'
    append(name)
    for ((k, v) in attributes) {
        +' '; +k; +"=\""
        v.escapeAttrTo(this)
        +'"'
    }
    +'>'
}

private fun String.escapeAttrTo(out: Appendable) {
    with(out) {
        for (c in this@escapeAttrTo) when (c) {
            '<' -> +"&lt;"
            '>' -> +"&gt;"
            '&' -> +"&amp;"
            '"' -> +"&quot;"
            else -> +c
        }
    }
}

// HTML-escapes a text node for raw inline HTML serialization (nested tables).
private fun String.escapeHtmlText(): String = buildString {
    for (c in this@escapeHtmlText) when (c) {
        '&' -> +"&amp;"
        '<' -> +"&lt;"
        '>' -> +"&gt;"
        else -> +c
    }
}

// The Markdown delimiter of an inline emphasis mark, null for any other mark.
private fun emphasisDelimiter(name: String): String? = when (name) {
    "strong" -> "**"
    "em" -> "*"
    "del" -> "~~"
    "mark" -> "=="
    "sup" -> "^"
    else -> null
}

// The parser's emphasis flanking classes (`isFlankWhitespace` / `isFlankPunct`
// in `MarkanywhereParser.kt`), so a closer is kept only where it re-parses as
// one (issue #93). Whitespace is CommonMark's Unicode whitespace (`Zs` plus tab,
// line feed, form feed, carriage return) — narrower than Kotlin's
// `isWhitespace()`, which also counts VT, U+001C–U+001F and U+2028/U+2029.
// Punctuation is ASCII only, a GFM divergence of the parser (CommonMark counts
// Unicode categories P and S). The two modules share no home for them, so they
// are pinned to each other by `EmphasisDelimiterRoundTripTest` in
// `markanywhere-html`.
private fun Char.isFlankWhitespace(): Boolean =
    this == '\t' || this == '\n' || this == '\u000C' || this == '\r' ||
        category == SPACE_SEPARATOR

private fun Char.isFlankPunctuation(): Boolean =
    this in '!'..'/' || this in ':'..'@' || this in '['..'`' || this in '{'..'~'

// Backslash-escapes every char of [text] that would read as inline syntax on
// re-parse instead of the literal char it is. [before] is the char written
// right before the text, [next] the one written right after it (null when
// unknown — then a char is escaped whenever some next char could make it
// syntax). Two layers:
//  - every emphasis delimiter in [mask] (delimiterBit bits — all of them while
//    a span is open, see spanEscapeMask);
//  - anywhere, a char that opens a construct at its position: a tilde run not
//    ending a line (#133), an `==` run not ending a line and every `^` (#135),
//    a `$` before a math opener or a char written escaped (#133), every
//    backtick, a `\` before whitespace or a char it would escape that is
//    inline syntax itself, a `<` that would start inline-HTML speculation
//    (lessThanStartsInlineHtml, #162), and a `*` / `_` run that can open
//    emphasis by the parser's flanking rules — unless the em ([emOpen]) or
//    strong ([strongOpen]) it would open is open already. A run right after
//    its own char (a closer, or that char written escaped), or followed by its
//    own char written next, would join that run, so it is escaped too.
// A `\` before other punctuation (`\-`) is left as is: re-parsed, it loses
// the backslash, while escaping it would make it grow on every pass where
// the text lands in an HTML block's raw text (an HTML block in a list item).
// [brackets] also escapes `[` / `]`, for image alt text, where an unbalanced
// one ends the description early. [entities] writes each escape as a numeric
// character reference instead of a backslash (see textContext).
//
// A single `~` is itself a valid GFM strikethrough delimiter (`~a~` →
// `<del>a</del>`), so inside a span it must always be escaped. A lone `=` is NOT
// a delimiter (only `==` toggles mark), so escaping it inside a span is
// *conservative* — but pair-only escaping is unsafe there: re-parsing an escaped
// span emits its `\=\=` as two separate single-`=` text events, so the per-event
// pass could no longer see the pair and the run would re-form. Over-escaping a
// lone `=` (a harmless `\=` in `<mark>x=5</mark>`) keeps the round-trip stable,
// which the append-only stream cannot trade away. Outside a span the pair is
// seen across events, as a text ending in `=` is judged by the next event.
//
// A literal backtick is escaped everywhere, as it opens an inline code span
// eagerly: inside `<em>`/`<strong>`/… one that `flushInline` LIFO-closes
// *before* the emphasis (`<em>a`b</em>` → `<em>a<code>b</code></em>`, growing
// every round-trip); inside a link/image label one that consumes the
// `]`/`(url)`, so the link never forms.
//
// A `<` that starts inline-HTML speculation swallows, on a line with no `>`,
// the rest of it as text — a closer included (`**a<**&#98;`).
private fun escapeInlineSyntax(
    text: String,
    before: Char,
    next: Char?,
    mask: Int,
    emOpen: Boolean = false,
    strongOpen: Boolean = false,
    brackets: Boolean = false,
    entities: Boolean = false
): String {
    // Allocated at the first escape: most text has none.
    var out: StringBuilder? = null
    var i = 0
    while (i < text.length) {
        val c = text[i]
        var end = i + 1
        if (c in RUN_SYNTAX_CHARS) while (end < text.length && text[end] == c) end++
        val prev = if (i == 0) before else text[i - 1]
        val after = if (end < text.length) text[end] else next
        // The char before is written escaped or as a closer — either way the run
        // would join it, so it is escaped like the char before.
        val joinsRun = prev == c || after == c || after == null
        val escape = (delimiterBit(c) and mask) != 0 || when (c) {
            '`', '^' -> true
            // A `~` / `~~` run toggles a del wherever it stands, except at the
            // end of a line, where it stays literal — unless it starts the
            // line, where three of them open a fence.
            '~' -> after != '\n' || prev == '\n' || joinsRun
            // Only an `==` run toggles a mark, and it too stays literal at the
            // end of a line.
            '=' -> joinsRun || (end - i == 2 && after != '\n')
            '*', '_' -> joinsRun || (canOpenEmphasis(c, prev, after) && when (end - i) {
                1 -> !emOpen
                2 -> !strongOpen
                else -> true
            })
            // `$\` opens math too, so a `$` before a char written escaped is.
            '$' -> after == null || after.isMathOpen() || after in INLINE_SYNTAX_CHARS ||
                (brackets && (after == '[' || after == ']'))
            // Before whitespace too: a space after it is dropped at a line end,
            // and `\` + line end is a hard break.
            '\\' -> after == null || after.isFlankWhitespace() || after in BACKSLASH_ESCAPED_SYNTAX
            '<' -> lessThanStartsInlineHtml(text, end, next, mask, brackets)
            '[', ']' -> brackets
            else -> false
        }
        if (escape && out == null) out = StringBuilder(text.length + 8).append(text, 0, i)
        if (out != null) {
            for (k in i until end) {
                when {
                    !escape -> out.append(text[k])
                    // Decoded to plain text as Markdown, kept as raw text.
                    entities -> out.append("&#").append(text[k].code).append(';')
                    else -> out.append('\\').append(text[k])
                }
            }
        }
        i = end
    }
    return out?.toString() ?: text
}

// Whether a `<` whose next char is at [index] of [text] (or [next] past its
// end) would start inline-HTML speculation on re-parse, which holds the rest
// of the line as raw text, emphasis and code included. A `<` before a letter,
// `/` or other email-local punctuation does (startsInlineHtml). Before a
// digit, `-` or `=` only an email autolink does (`<1u@x.com>`), so only when
// a plain address with an `@` follows, or its end is not in sight. A `<`
// before a char written escaped (`<\=\=`, `<<`, `<\[`) is escaped as well.
private fun lessThanStartsInlineHtml(text: String, index: Int, next: Char?, mask: Int, brackets: Boolean): Boolean {
    val after = if (index < text.length) text[index] else next ?: return true
    if (after == '\\' || after == '<' || (delimiterBit(after) and mask) != 0 ||
        (brackets && (after == '[' || after == ']'))
    ) return true
    if (after == '=') {
        // An `==` run is written escaped; past the text's end the run is unknown.
        val afterThat = when {
            index + 1 < text.length -> text[index + 1]
            index < text.length -> next
            else -> null
        }
        if (afterThat == null || afterThat == '=') return true
    }
    if (!after.startsInlineHtml()) return false
    if (!after.startsEmailAutolinkOnly()) return true
    // The char at [index] starts the local part; an `@` in the rest of it
    // makes the candidate an address. A syntax char in it may be written
    // escaped, which ends the parser's candidate at the `\` — whether it is
    // depends on its neighbours, possibly in another text event, so the `<`
    // is escaped then too, keeping the decision the same however the text is
    // split into events. Only whitespace or another char that ends the
    // candidate for certain leaves the `<` as is.
    var i = index + 1
    while (i < text.length) {
        val c = text[i]
        if (c == '@' || (c in INLINE_SYNTAX_CHARS && c != '_')) return true
        if (!c.continuesEmailAutolinkCandidate()) return false
        i++
    }
    // The candidate runs to the end of the text: judged by the char after it,
    // and unknown when it starts there.
    return index >= text.length || next == null || !next.isFlankWhitespace()
}

// The parser's `canFollowInlineHtmlOpen`: whether this char, right after a
// `<`, may start a tag, an autolink or an email autolink. Pinned to the
// parser by `InlineSyntaxEscapingRoundTripTest`.
private fun Char.startsInlineHtml(): Boolean =
    this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '$' ||
        this in "/.!#%&'*+=?^_`{|}~-"

// The parser's `isEmailAutolinkCandidate` and `continuesEmailAutolinkCandidate`:
// only an email autolink starts with a digit, `-` or `=`, and the parser reads
// it on only over the chars of a plain address.
private fun Char.startsEmailAutolinkOnly(): Boolean = this in '0'..'9' || this == '-' || this == '='

// A char of an email address's local part, or its `@`.
private fun Char.continuesEmailAutolinkCandidate(): Boolean =
    this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '$' ||
        this in "@.!#%&'*+/=?^_`{|}~-"

// The tags that start an HTML block of type 6 wherever they stand alone at a
// line start — the parser's `HTML_BLOCK_TYPE6_TAGS` (see TaggedInline.htmlBlock).
private val HTML_BLOCK_START_TAGS = setOf(
    "address", "article", "aside", "base", "basefont", "blockquote", "body",
    "caption", "center", "col", "colgroup", "dd", "details", "dialog",
    "dir", "div", "dl", "dt", "fieldset", "figcaption", "figure", "footer",
    "form", "frame", "frameset", "h1", "h2", "h3", "h4", "h5", "h6",
    "head", "header", "hr", "html", "iframe", "legend", "li", "link",
    "main", "menu", "menuitem", "nav", "noframes", "ol", "optgroup",
    "option", "p", "param", "search", "section", "summary", "table",
    "tbody", "td", "tfoot", "th", "thead", "title", "tr", "track", "ul"
)

// Escapes an image's alt text for its `![alt](src)` description, which the
// parser reads as inline content before reducing it to plain text.
private fun escapeAltText(alt: String, entities: Boolean): String =
    if (alt.none { it in INLINE_SYNTAX_CHARS || it == '[' || it == ']' }) alt
    else escapeInlineSyntax(text = alt, before = '[', next = ']', mask = 0, brackets = true, entities = entities)

// Whether a `*` / `_` run between [before] and [after] can open emphasis on
// re-parse — the parser's flanking rules (`emphasisFlanking` in
// `MarkanywhereParser.kt`), `_` with its stricter intraword rule.
private fun canOpenEmphasis(c: Char, before: Char, after: Char?): Boolean {
    if (after == null) return true
    val nextIsWs = after.isFlankWhitespace()
    val nextIsPunct = after.isFlankPunctuation()
    val prevIsWs = before.isFlankWhitespace()
    val prevIsPunct = before.isFlankPunctuation()
    val leftFlank = !nextIsWs && (!nextIsPunct || prevIsWs || prevIsPunct)
    val rightFlank = !prevIsWs && (!prevIsPunct || nextIsWs || nextIsPunct)
    return if (c == '_') leftFlank && (!rightFlank || prevIsPunct) else leftFlank
}

// The parser's inline math opener rule (`isMathOpenChar`): `$` followed by a
// letter, a LaTeX command or a group opens math, `$5` does not.
private fun Char.isMathOpen(): Boolean = isLetter() || this == '\\' || this == '{'

// Chars escapeInlineText may have to escape; text without any is written as is.
private const val INLINE_SYNTAX_CHARS = "*~=^`_$\\<"

// Chars whose escaping depends on the char written after them — a text event
// ending in one is held until the next event (see heldText).
private const val LOOKAHEAD_CHARS = "*~=_$\\<"

// The chars after a `\` that make it an escape the renderer must keep literal
// (see escapeInlineSyntax): inline syntax chars, brackets, `!` and `&`.
private const val BACKSLASH_ESCAPED_SYNTAX = "$INLINE_SYNTAX_CHARS[]!&"

// Chars escapeInlineSyntax judges as a run of the same char.
private const val RUN_SYNTAX_CHARS = "*~=_"

// How text is read back on re-parse (see textContext in asMarkdown).
private const val TEXT_MARKDOWN = 0
private const val TEXT_RAW_OR_MARKDOWN = 1
private const val TEXT_VERBATIM = 2

// The first char the renderer writes for this event, as far as escaping the
// text right before it is concerned (see escapeInlineText); null when unknown.
private fun SemanticEvent.leadingChar(): Char? = when (this) {
    is Text -> text.firstOrNull()
    is Mark -> if (isTagged) '<' else emphasisDelimiter(name)?.get(0) ?: when (name) {
        "a" -> '['
        "img" -> '!'
        "code" -> '`'
        // A hard break is written `  \n`.
        "br" -> ' '
        "th", "td", in BLOCK_LEVEL_MARK_NAMES -> '\n'
        in MARKDOWN_NATIVE_MARK_NAMES -> null
        else -> '<'
    }
    // An emphasis closer is held, and dropped when the same span reopens right
    // after it (pendingInlineClose), so what follows it is unknown.
    is Unmark -> if (isTagged) '<' else if (emphasisDelimiter(name) != null) null else when (name) {
        "a" -> ']'
        "code" -> '`'
        "th", "td", in BLOCK_LEVEL_MARK_NAMES -> '\n'
        in MARKDOWN_NATIVE_MARK_NAMES -> null
        else -> '<'
    }
}

// The distinct delimiter base char of every inline emphasis frame, as a bit:
// `*` (em/strong), `~` (del), `=` (mark), `^` (sup). Any other char → 0, so it
// is never escaped. (`_` is never emitted — the renderer always uses `*`.)
private fun delimiterBit(c: Char): Int = when (c) {
    '*' -> 1
    '~' -> 2
    '=' -> 4
    '^' -> 8
    else -> 0
}

// `~`/`=`/`^` bits — the del/mark/sup delimiters, escaped against an outer span
// inside a label as defense in depth (the parser no longer lets them close it).
// Excludes the `*` bit. Constant — computed once per stream, not per text event.
private val delMarkSupDelimiters = delimiterBit('~') or delimiterBit('=') or delimiterBit('^')

// All four inline-emphasis delimiter bits (`*`/`~`/`=`/`^`). When ANY emphasis
// span is open, a literal delimiter of ANY type in the content (not just the
// open span's own char) can open a *new* span on re-parse that restructures the
// content — `==a~b==` re-parses to `<mark>a<del>b</del></mark>`, `~~a*b~~` to
// `<del>a<em>b</em></del>`. So a single open span means escape all four, not
// just its own delimiter.
private val allInlineDelimiters = delimiterBit('*') or delMarkSupDelimiters
