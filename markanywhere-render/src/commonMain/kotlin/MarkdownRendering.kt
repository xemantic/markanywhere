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
 * match the parser's own buffering constraints.
 *
 * Untagged events (Markdown-derived) are emitted using Markdown syntax —
 * headings as `#`-prefixed lines, emphasis as `*…*` / `**…**`, links as
 * `[label](url)`, fenced code as ` ``` `, etc.
 *
 * Tagged events (HTML-originated, [SemanticEvent.Marked.isTagged] = `true`)
 * are passed through as raw HTML tags, since Markdown permits embedded HTML.
 *
 * The synthetic `frontmatter` block (with `format` attribute) is rendered
 * with the matching delimiter line (`---` for YAML, `+++` for TOML).
 *
 * A single trailing `\n` (if present at end of stream) is suppressed —
 * the renderer holds at most one pending newline and drops it on completion.
 */
public fun Flow<SemanticEvent>.asMarkdown(): Flow<String> = flow {

    val blockStack = ArrayDeque<BlockFrame>()
    val labelBuffers = ArrayDeque<StringBuilder>()
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
    // Base chars of the currently-open inline emphasis delimiters (e.g. `*` for
    // em/strong, `~` for del). Maintained on Inline mark/unmark so the per-text
    // escaping pass (escapeActiveInlineDelimiters) doesn't rescan blockStack on
    // every text token of a streamed span.
    var activeDelimiters: Set<Char> = emptySet()

    val eventBuffer = StringBuilder()
    var hasPendingNewline = false
    // While a table row is being assembled its output is captured here so the
    // row can be dropped if empty, and so a headerless table can synthesize an
    // empty header (column count is known only once the first row closes).
    var rowCapture: StringBuilder? = null

    fun out(s: String) { (rowCapture ?: eventBuffer).append(s) }

    // Emit any deferred trailing spaces — called right before real content is
    // written on the current line, so a space between inline tokens survives
    // while a space at a line end is dropped (it is never flushed there).
    fun flushPendingSpaces() {
        if (pendingSpaces > 0) {
            out(" ".repeat(pendingSpaces))
            pendingSpaces = 0
        }
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
                is BlockFrame.Blockquote -> sb.append("> ")
                is BlockFrame.List -> {
                    val next = blockStack.getOrNull(i + 1)
                    val pending = (next as? BlockFrame.ListItem)?.pendingMarker
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

    fun appendLabel(text: String) { labelBuffers.last().append(text) }
    fun inLabel(): Boolean = labelBuffers.isNotEmpty()

    // Serialize one event of a nested table as raw HTML into the enclosing
    // cell buffer — no newlines (the outer row is a single line), text nodes
    // HTML-escaped, void elements emitted as a bare open tag. The `<table>`
    // depth is tracked so the matching root close exits this mode.
    fun serializeHtmlTableEvent(event: SemanticEvent) {
        when (event) {
            is SemanticEvent.Mark -> {
                appendLabel(event.renderOpenTag())
                if (event.name == "table") htmlTableDepth++
            }
            is SemanticEvent.Unmark -> when {
                event.name == "table" -> {
                    appendLabel("</table>")
                    htmlTableDepth--
                }
                event.name in VOID_HTML_ELEMENTS -> { /* no closing tag */ }
                else -> appendLabel("</${event.name}>")
            }
            is SemanticEvent.Text -> appendLabel(event.text.escapeHtmlText())
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
            pendingSpaces = 0
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
        if (boundary && (pendingHardBreak || (atLineStart && pendingSpaces == 0))) {
            ensureBlankLine() // drops a moot pendingHardBreak + deferred spaces
            // Loose inline content at a block boundary (e.g. a link directly
            // inside an unwrapped <nav>, not wrapped in a paragraph) behaves as
            // its own block — the next block-level construct separates from it.
            pendingBlockSeparator = true
        } else if (pendingHardBreak) {
            // Hard line break inside the same block. Deferred trailing spaces
            // before the break are insignificant and dropped.
            pendingSpaces = 0
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

    // Backslash-escape every character that is the run delimiter of an enclosing
    // inline emphasis span (`*` for em/strong, `~` for del, `=` for mark, `^` for
    // sup). A literal delimiter inside the span's content would otherwise re-pair
    // against the span's own closing run on the next parse — making the run grow
    // by one every round-trip (the unbounded `…)*` → `…)**` → `…)***` instability
    // seen in the Brave SERP dump). Suppressed inside verbatim `pre`/`code`, where
    // a backslash is literal text, not an escape.
    fun escapeActiveInlineDelimiters(text: String): String {
        // Link/image labels are buffered and re-emitted verbatim between `[` … `]`;
        // their emphasis round-trips through the parser's label-scoped close, so
        // escaping here would only add noise. Restrict to direct block content.
        // Every literal occurrence of the base char is escaped (not just doubled
        // runs). A single `~` is itself a valid GFM strikethrough delimiter (`~a~`
        // → `<del>a</del>`), so it must always be escaped. A lone `=` is NOT a
        // delimiter (only `==` toggles mark), so escaping it is *conservative* —
        // but pair-only escaping is unsafe here: re-parsing an escaped span emits
        // its `\=\=` as two separate single-`=` text events, so this per-event pass
        // could no longer see the pair and the run would re-form. Over-escaping a
        // lone `=` (a harmless `\=` in `<mark>x=5</mark>`) keeps the round-trip
        // stable, which the append-only stream cannot trade away.
        if (inPreCode || inLabel() || text.isEmpty() || activeDelimiters.isEmpty()) return text
        return buildString(text.length) {
            for (c in text) {
                if (c in activeDelimiters) +'\\'
                +c
            }
        }
    }

    // Recompute the open inline emphasis delimiters from blockStack — called only
    // on Inline mark/unmark (rare), keeping the per-text escaping pass allocation-free.
    fun refreshActiveDelimiters() {
        activeDelimiters = blockStack.mapNotNullTo(mutableSetOf()) {
            (it as? Inline)?.delimiter?.firstOrNull()
        }
    }

    // Escaping a leading block marker is only safe in a Markdown-block context —
    // never inside verbatim `pre`/`code` or a raw frontmatter block.
    fun blockMarkerEscapingAllowed(): Boolean =
        !inPreCode && blockStack.none {
            it is Pre || it is Code || it is Frontmatter
        }

    // A soft line break. Spaces deferred from the previous line are trailing
    // and dropped (insignificant around a soft break); the line resets to a
    // fresh Markdown line so a continuation line that begins with a block
    // marker (`# x`, `- x`, …) is still escaped on re-emission.
    fun emitSoftBreak() {
        pendingSpaces = 0
        out("\n")
        atLineStart = true
        freshBlockLine = true
        lineStartDigits = false
    }

    fun emitText(rawText: String) {
        if (rawText.isEmpty()) return
        val text = escapeActiveInlineDelimiters(rawText)
        if (inLabel()) { appendLabel(text); return }
        consumeHeadingSpace()
        beforeContent()
        val parts = text.split('\n')
        parts.forEachIndexed { i, part ->
            if (i > 0) emitSoftBreak()
            if (part.isNotEmpty()) {
                // Split off the line's trailing ASCII spaces and defer them
                // (see flushPendingSpaces / pendingSpaces): they are emitted
                // only if real content follows on this line, dropped otherwise.
                val contentEnd = part.indexOfLast { it != ' ' } + 1
                val original = part.substring(0, contentEnd)
                if (original.isNotEmpty()) {
                    flushPendingSpaces()
                    if (atLineStart) out(buildPrefix(consumeMarker = true))
                    var content = original
                    if (blockMarkerEscapingAllowed()) {
                        if (freshBlockLine) {
                            // At a fresh Markdown line, escape a leading block
                            // marker so paragraph text like "1." / "- x" / "# x"
                            // / "---" / ">" does not re-parse as a different block.
                            content = escapeLeadingBlockMarker(original)
                        } else if (lineStartDigits) {
                            // Complete a fragmented ordered marker: a "."/")"
                            // right after a bare line-starting digit run.
                            content = escapeLeadingOrderedDelimiter(original)
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
                pendingSpaces += part.length - contentEnd
            }
        }
    }

    fun endBlock() {
        ensureLineStart()
        pendingBlockSeparator = true
        lastBlock = BLOCK_MARKDOWN
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
            if (frame !is BlockFrame.Link || frame.rawMode) continue
            val labelText = labelBuffers.removeLast().toString()
            val openTag = renderOpenTag("a", frame.attributes)
            // Once this link's label buffer is popped, the *next* spill writes
            // into either an outer label buffer (nested link) or the actual
            // output (the outermost link).
            if (labelBuffers.isEmpty()) {
                // Outermost spilled link: a block-level raw tag. Separate it
                // from the preceding block (blank line at a Markdown→tag
                // boundary, newline-tight after another tag) and put the open
                // tag *alone* on its line — only then is it a valid CommonMark
                // HTML-block start, so the heading/paragraph that follows parses
                // as Markdown rather than raw HTML. Any inline label collected
                // before the spill (e.g. a "LIVE" badge) becomes its own block.
                separateBeforeBlockTag()
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
    fun handleRawOpen(event: SemanticEvent.Mark) {
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
        val isBlockTagged = !inLabel() && event.name in BLOCK_TAGGED_ELEMENTS
        val isVoid = event.name in VOID_HTML_ELEMENTS
        // A void element — and an `<a>` (a block-wrapping link the forward
        // pipeline spills to raw HTML) — only counts as a block-level tag when
        // it lands at a line start; mid-line (e.g. `<input>` after text, or an
        // inline link/autolink) it is inline content.
        val blockLevelTag = !inLabel() &&
            (isBlockTagged || ((isVoid || event.name == "a") && atLineStart))
        if (!inLabel()) {
            // A block-level tag always starts its own line (breaking the
            // current line if needed); an inline tag only separates when it
            // already begins a block.
            if (blockLevelTag) separateBeforeBlockTag()
            else if (atLineStart && pendingSpaces == 0) ensureBlankLine()
        }
        writeTag(event.renderOpenTag())
        // Block decision captured before writeTag cleared atLineStart.
        if ((isBlockTagged || isVoid || blockLevelTag) && !inLabel()) {
            ensureLineStart()
        }
        if (!inLabel()) lastBlock = if (blockLevelTag) BLOCK_TAG else BLOCK_MARKDOWN
        blockStack.addLast(
            if (isVoid) BlockFrame.SelfClosed else BlockFrame.TaggedInline
        )
    }

    // Emit a captured table row. An empty row (a layout spacer) is dropped.
    // The first emitted row resolves the table's column count and the header:
    // a row with `th` cells is the header itself; a headerless row gets an
    // empty header synthesized above it so the output is valid GFM.
    fun emitTableRow(row: BlockFrame.TableRow, table: BlockFrame.Table?) {
        if (row.buffer.isEmpty()) return
        val rowText = row.buffer.toString()
        ensureLineStart()
        if (table != null && !table.headerEmitted) {
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
            out(buildPrefix(consumeMarker = true)); out(rowText); out("\n")
        }
        atLineStart = true
    }

    collect { event ->

        // A `<table>` nested inside a table cell can't be expressed in
        // Markdown — emit it as raw single-line HTML into the cell buffer.
        // The mode is entered when a `table` mark arrives while a cell (or
        // link label) buffer is open, and persists until the matching root
        // close.
        if (htmlTableDepth > 0 ||
            (event is Mark && event.name == "table" && inLabel())
        ) {
            serializeHtmlTableEvent(event)
            return@collect
        }

        // If a block-level mark arrives while any `<a>` is still buffering
        // a label, spill the affected links to raw HTML before dispatching.
        if (event is Mark
            && (event.name in BLOCK_LEVEL_MARK_NAMES || event.name in BLOCK_TAGGED_ELEMENTS)
            && blockStack.any { it is BlockFrame.Link && !it.rawMode }
        ) {
            spillActiveLinks()
        }

        // Tagged events (HTML-derived) pass through as raw HTML. At a block
        // boundary (atLineStart with a pending separator) emit the blank line
        // so the next Markdown block parses correctly after the raw HTML.
        if (event is Mark && event.isTagged) {
            handleRawOpen(event)
            flush()
            return@collect
        }
        if (event is Unmark && event.isTagged) {
            val frame = blockStack.lastOrNull()
            if (frame is BlockFrame.SelfClosed) {
                blockStack.removeLast()
                flush()
                return@collect
            }
            val blockTagged = !inLabel() &&
                (event.name in BLOCK_TAGGED_ELEMENTS || (event.name == "a" && atLineStart))
            if (blockTagged) {
                separateBeforeBlockTag()
                writeTag("</${event.name}>")
                ensureLineStart()
                lastBlock = BLOCK_TAG
            } else {
                writeRaw("</${event.name}>")
            }
            if (frame is BlockFrame.TaggedInline) {
                blockStack.removeLast()
            }
            // If the closing tag returned us to a block-level context
            // (no inline frame open above), treat it as a block boundary
            // so the next Markdown block parses with a blank line before it.
            if (!blockTagged && !inLabel() && blockStack.none { it.isInline() }) {
                endBlock()
            }
            flush()
            return@collect
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
                    blank && '\n' in text && !pendingHardBreak -> emitSoftBreak()
                    // Mid-line whitespace with no newline: defer ASCII spaces
                    // (re-emitted before real same-line content, dropped at a
                    // line/block boundary); tabs are structural and dropped.
                    blank -> { for (c in text) if (c == ' ') pendingSpaces++ }
                    else -> {
                        emitText(text)
                        if (text.isNotEmpty() && !inLabel()) lastBlock = BLOCK_MARKDOWN
                    }
                }
            }

            is Mark -> when (event.name) {

                "frontmatter" -> {
                    val format = event.attributes["format"] ?: "yaml"
                    val delim = if (format == "toml") "+++" else "---"
                    ensureBlankLine()
                    out(delim)
                    out("\n")
                    atLineStart = true
                    pendingBlockSeparator = false
                    blockStack.addLast(BlockFrame.Frontmatter(delim))
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
                    blockStack.addLast(BlockFrame.Heading)
                }

                "p" -> {
                    // Inside a list item awaiting its first content, the `p`
                    // becomes inline — the item marker already opens the line.
                    val parent = blockStack.lastOrNull()
                    if (parent is BlockFrame.ListItem && parent.firstChild) {
                        parent.firstChild = false
                        blockStack.addLast(BlockFrame.Paragraph(suppressClose = true))
                    } else {
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
                    blockStack.addLast(BlockFrame.SelfClosed)
                }

                "blockquote" -> {
                    ensureBlankLine()
                    blockStack.addLast(BlockFrame.Blockquote)
                }

                "ul" -> {
                    val nested = blockStack.lastOrNull() is BlockFrame.ListItem
                    if (nested) {
                        (blockStack.last() as BlockFrame.ListItem).firstChild = false
                        ensureLineStart()
                    } else ensureBlankLine()
                    blockStack.addLast(BlockFrame.List(ordered = false))
                }

                "ol" -> {
                    val nested = blockStack.lastOrNull() is BlockFrame.ListItem
                    if (nested) {
                        (blockStack.last() as BlockFrame.ListItem).firstChild = false
                        ensureLineStart()
                    } else ensureBlankLine()
                    val start = event.attributes["start"]?.toIntOrNull() ?: 1
                    blockStack.addLast(BlockFrame.List(ordered = true, counter = start))
                }

                "li" -> {
                    val list = blockStack.lastOrNull() as? BlockFrame.List
                        ?: error("<li> outside of a list")
                    ensureLineStart()
                    pendingBlockSeparator = false
                    val marker = if (list.ordered) "${list.counter}. " else "- "
                    list.counter++
                    list.indent = " ".repeat(marker.length)
                    blockStack.addLast(BlockFrame.ListItem(pendingMarker = marker))
                }

                "pre" -> {
                    startBlock()
                    blockStack.addLast(BlockFrame.Pre)
                }

                "code" -> {
                    if (blockStack.lastOrNull() is BlockFrame.Pre) {
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
                        labelBuffers.addLast(StringBuilder())
                    }
                    blockStack.addLast(BlockFrame.Code)
                }

                "strong" -> { writeRaw("**"); blockStack.addLast(BlockFrame.Inline("**")); refreshActiveDelimiters() }
                "em" -> { writeRaw("*"); blockStack.addLast(BlockFrame.Inline("*")); refreshActiveDelimiters() }
                "del" -> { writeRaw("~~"); blockStack.addLast(BlockFrame.Inline("~~")); refreshActiveDelimiters() }
                "mark" -> { writeRaw("=="); blockStack.addLast(BlockFrame.Inline("==")); refreshActiveDelimiters() }
                "sup" -> { writeRaw("^"); blockStack.addLast(BlockFrame.Inline("^")); refreshActiveDelimiters() }

                "a" -> {
                    labelBuffers.addLast(StringBuilder())
                    blockStack.addLast(
                        BlockFrame.Link(
                            href = event.attributes["href"] ?: "",
                            attributes = event.attributes
                        )
                    )
                }

                "img" -> {
                    val src = event.attributes["src"] ?: ""
                    val alt = event.attributes["alt"] ?: ""
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
                    blockStack.addLast(BlockFrame.SelfClosed)
                }

                "br" -> {
                    pendingHardBreak = true
                    blockStack.addLast(BlockFrame.SelfClosed)
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
                    val row = blockStack.lastOrNull { it is BlockFrame.TableRow }
                        as? BlockFrame.TableRow
                    if (row != null) {
                        if (row.buffer.isEmpty()) out("|")
                        row.cellCount += colspan
                        if (event.name == "th") row.hadHeaderCell = true
                    }
                    out(" ")
                    labelBuffers.addLast(StringBuilder())
                    blockStack.addLast(
                        BlockFrame.TableCell(isHeader = event.name == "th", colspan = colspan)
                    )
                }

                else -> handleRawOpen(event)
            }

            is Unmark -> {
                val frame = blockStack.removeLast()
                when (event.name) {

                    "frontmatter" -> {
                        ensureLineStart()
                        writeRaw((frame as BlockFrame.Frontmatter).delimiter)
                        endBlock()
                    }

                    "h1", "h2", "h3", "h4", "h5", "h6" -> {
                        // An empty heading never consumed its deferred space.
                        headingSpacePending = false
                        endBlock()
                    }

                    "p" -> {
                        when {
                            // Deferred paragraph that never received content —
                            // a no-op (it emitted nothing, so nothing to close).
                            pendingParagraph -> pendingParagraph = false
                            (frame as BlockFrame.Paragraph).suppressClose -> {
                                // first-content paragraph in a list item — leave
                                // pendingBlockSeparator alone so the list-item
                                // line just ends.
                                ensureLineStart()
                            }
                            else -> endBlock()
                        }
                    }

                    "blockquote" -> endBlock()

                    "ul", "ol" -> {
                        val nestedInItem = blockStack.lastOrNull() is BlockFrame.ListItem
                        if (nestedInItem) {
                            ensureLineStart()
                            pendingBlockSeparator = false
                        } else endBlock()
                    }

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

                    "strong", "em", "del", "mark", "sup" -> {
                        writeRaw((frame as BlockFrame.Inline).delimiter)
                        refreshActiveDelimiters()
                    }

                    "a" -> {
                        val link = frame as BlockFrame.Link
                        if (link.rawMode) {
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
                        val table = blockStack.lastOrNull { it is BlockFrame.Table }
                            as? BlockFrame.Table
                        emitTableRow(frame as BlockFrame.TableRow, table)
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
                        repeat((frame as BlockFrame.TableCell).colspan - 1) { out("  |") }
                    }

                    else -> {
                        if (frame is BlockFrame.SelfClosed) {
                            // void element — no closing tag
                        } else {
                            val blockTagged = !inLabel()
                                && event.name in BLOCK_TAGGED_ELEMENTS
                            if (blockTagged) {
                                separateBeforeBlockTag()
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
        is Paragraph, Heading, is Inline, is Link, is TableCell, TaggedInline -> true
        else -> false
    }
    data object Heading : BlockFrame
    class Paragraph(val suppressClose: Boolean = false) : BlockFrame
    data object Blockquote : BlockFrame
    class List(
        val ordered: Boolean,
        var counter: Int = 1,
        var indent: String = ""
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
        var rawMode: Boolean = false
    ) : BlockFrame
    data object SelfClosed : BlockFrame
    data object TaggedInline : BlockFrame
    class Frontmatter(val delimiter: String) : BlockFrame
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
private val BLOCK_LEVEL_MARK_NAMES = setOf(
    "h1", "h2", "h3", "h4", "h5", "h6",
    "p", "hr", "blockquote",
    "ul", "ol", "li",
    "pre",
    "table", "thead", "tbody", "tr"
)

// HTML void elements — content model is empty, no closing tag is emitted
// in HTML5 (see WHATWG HTML §13.1.2). `br`, `hr`, `img` are handled in
// dedicated `Markdown` branches; the rest fall through to the raw-HTML
// path which uses this set to suppress the matching closing tag.
private val VOID_HTML_ELEMENTS = setOf(
    "area", "base", "br", "col", "embed", "hr", "img",
    "input", "link", "meta", "param", "source", "track", "wbr"
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

// Backslash-escapes a leading block marker in paragraph text so it round-trips
// as text instead of re-parsing as a different block construct. Applied only at
// a fresh Markdown line in a block context (see emitText). Covers: ATX heading
// (`#`{1,6} + space/EOL), blockquote (`>`), thematic break (≥3 of `-`/`*`/`_`),
// bullet list (`-`/`+`/`*` + space/EOL), ordered list (digits + `.`/`)` +
// space/EOL). Anything else is returned unchanged.
private fun escapeLeadingBlockMarker(line: String): String {
    val c = line[0]
    when (c) {
        '>' -> return "\\" + line
        '#' -> {
            var n = 0
            while (n < line.length && line[n] == '#') n++
            if (n in 1..6 && (n == line.length || line[n] == ' ' || line[n] == '\t')) {
                return "\\" + line
            }
            return line
        }
        '-', '*', '_' -> {
            if (isThematicBreak(line)) return "\\" + line
            // `_` is never a bullet; `-`/`*` are when followed by a space/EOL.
            if (c != '_' && (line.length == 1 || line[1] == ' ' || line[1] == '\t')) {
                return "\\" + line
            }
            return line
        }
        '+' -> {
            if (line.length == 1 || line[1] == ' ' || line[1] == '\t') return "\\" + line
            return line
        }
        in '0'..'9' -> {
            var n = 0
            while (n < line.length && line[n] in '0'..'9') n++
            // CommonMark caps an ordered-list start marker at 9 digits.
            if (n in 1..9 && n < line.length && (line[n] == '.' || line[n] == ')')) {
                val after = n + 1
                if (after == line.length || line[after] == ' ' || line[after] == '\t') {
                    return line.substring(0, n) + "\\" + line.substring(n)
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
private fun escapeLeadingOrderedDelimiter(s: String): String {
    val c = s[0]
    if ((c == '.' || c == ')') && (s.length == 1 || s[1] == ' ' || s[1] == '\t')) {
        return "\\" + s
    }
    return s
}

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