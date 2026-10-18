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

package com.xemantic.markanywhere.html

import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.SemanticEvent.Mark
import com.xemantic.markanywhere.SemanticEvent.Unmark
import com.xemantic.markanywhere.dump.AccessibilityAnnotations
import com.xemantic.markanywhere.html.spec.HTML_VOID_ELEMENTS
import com.xemantic.markanywhere.html.ActionableRef.ATTRIBUTE
import com.xemantic.markanywhere.html.ActionableRef.SCHEME
import com.xemantic.markanywhere.html.ActionableRef.decode
import com.xemantic.markanywhere.html.ActionableRef.encode
import com.xemantic.markanywhere.transform.MatcherScope
import com.xemantic.markanywhere.transform.TransformerBuilder
import com.xemantic.markanywhere.transform.transform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * The Markdown-output encoding of an actionable element's ref — the bridge
 * between the dump's namespace-safe DOM ref ([AccessibilityAnnotations.REF],
 * `data-markanywhere-ref`) and a short, token-cheap, LLM-facing handle an agent
 * can name to act on the element.
 *
 * Two surfaces, because Markdown links carry no attributes:
 *
 * - **Inline links** (`<a>` wrapping only inline content) embed the ref *in the
 *   destination* via the [SCHEME] pseudo-scheme — `[label](ref:42:/menu)`,
 *   `[ext](ref:52:https://example.com)`. The original href rides along after the
 *   id, so the agent sees both where the link goes *and* a handle to click it
 *   (faithful for `href="#"` / JS controls that a hard navigation can't
 *   trigger). [decode] splits it back on the first two `:` (the id is numeric;
 *   the original href keeps its own colons).
 * - **Everything else** carries it as the short [ATTRIBUTE] (`ref="42"`): hrefless
 *   controls (`<button>`, `<input>`, a promoted `role=button` container, …) *and*
 *   a block-wrapping `<a>` that the Markdown renderer can only emit as a raw
 *   `<a …>` tag (a raw tag *can* carry attributes, so the ref need not ride the
 *   href there — see [encodeActionableRefs]).
 *
 * Both share one id namespace, so an agent's click tool always takes a number,
 * regardless of which form surfaced it. This is a deliberately non-standard URL
 * scheme: the output is LLM-facing, so a dead href in a browser is a non-issue.
 */
public object ActionableRef {

    /** Pseudo-scheme prefixing a ref-bearing link's encoded destination. */
    public const val SCHEME: String = "ref"

    /** Short attribute name carrying a hrefless control's ref in the output. */
    public const val ATTRIBUTE: String = "ref"

    /** Encodes [ref] + the original [href] into a `ref:<id>:<href>` destination. */
    public fun encode(ref: String, href: String): String = "$SCHEME:$ref:$href"

    /**
     * Reverses [encode]: `ref:<id>:<href>` → `(id, href)`, or `null` when [value]
     * is not a ref-encoded destination. Splits on the first two `:` only, so an
     * `https://` original href round-trips intact.
     */
    public fun decode(value: String): Pair<String, String>? {
        val prefix = "$SCHEME:"
        if (!value.startsWith(prefix)) return null
        val rest = value.substring(prefix.length)
        val separator = rest.indexOf(':')
        if (separator < 0) return null
        return rest.substring(0, separator) to rest.substring(separator + 1)
    }

}

/**
 * Block-level mark names whose presence anywhere inside an `<a>` forces the
 * Markdown renderer to spill the link as a **raw `<a …>` tag** — the
 * `[label](url)` form can't wrap block content. Such a link therefore carries
 * its ref as the short `ref="<id>"` attribute (the raw tag can hold attributes),
 * not the [ActionableRef.SCHEME] destination.
 *
 * This MUST mirror the union of `BLOCK_LEVEL_MARK_NAMES` and
 * `BLOCK_TAGGED_ELEMENTS` in markanywhere-render's `MarkdownRendering.kt`: the
 * spill decision lives there, but the renderer is deliberately ref-agnostic, so
 * the encoder replicates the predicate rather than the renderer learning the
 * ref scheme. The two modules are siblings with no shared home for the set —
 * keep the two copies in sync when either changes. `EncodeActionableRefsTest`
 * asserts this set equals the renderer's public `MARKDOWN_BLOCK_CONTENT_TAGS`,
 * so a drift fails CI instead of silently misencoding a block-wrapping `<a>`.
 */
internal val LINK_BLOCK_CONTENT_TAGS: Set<String> = setOf(
    // BLOCK_LEVEL_MARK_NAMES (Markdown block marks)
    "h1", "h2", "h3", "h4", "h5", "h6",
    "p", "hr", "blockquote",
    "ul", "ol", "li",
    "pre",
    "table", "thead", "tbody", "tr", "caption",
    // BLOCK_TAGGED_ELEMENTS (block-level raw HTML tags)
    "section", "nav", "article", "aside",
    "header", "footer", "main", "hgroup",
    "figure", "figcaption",
    "details", "summary", "dialog",
    "form", "fieldset", "legend",
    "button", "select", "textarea",
    "optgroup", "option", "datalist",
    "menu",
    "iframe", "frame", "object",
    "address", "search"
)

/**
 * Rewrites the dump's [AccessibilityAnnotations.REF] into its LLM-facing
 * [ActionableRef] form. An inline `<a>` folds its ref into the
 * `ref:<id>:<href>` destination; a block-wrapping `<a>` (rendered as a raw tag)
 * and every other ref-bearing element take the short `ref="<id>"` attribute —
 * on a tagged `span` around the content of an untagged list item, heading or
 * cell, whose Markdown syntax carries no attributes (see
 * [moveRefsOffAttributelessBlocks]). Elements without a ref pass through
 * untouched.
 *
 * This is a **separate, optional step** on purpose: the rest of the pipeline
 * ([simplifyHtml] etc.) is ref-agnostic, so a human-readable rendering can reuse
 * the same machinery and simply omit this transformer (and not ask `simplifyHtml`
 * to keep the ref). Apply it after `simplifyHtml` has preserved the ref — see
 * [transformHtmlToMarkdown].
 *
 * Links run through a buffering pre-pass ([encodeActionableLinkRefs]) because
 * the inline-vs-block choice needs the whole `<a>` subtree, which the streaming
 * matcher framework can't see before emitting the mark; every other element is
 * handled by the matcher pass that follows.
 */
public fun Flow<SemanticEvent>.encodeActionableRefs(): Flow<SemanticEvent> =
    encodeActionableLinkRefs().moveRefsOffAttributelessBlocks().transform {
        encodeActionableNonLinkRefs()
    }

/**
 * Untagged Markdown blocks that hold inline content but whose Markdown syntax
 * carries no attributes — `- `, `## `, a `| cell |` — so a ref on one of them
 * would vanish when the renderer writes it. A list item is the common case: a
 * combobox suggestion, `<li role="option">`.
 */
private val ATTRIBUTELESS_BLOCKS = setOf(
    "li", "p", "h1", "h2", "h3", "h4", "h5", "h6", "th", "td", "caption",
)

/**
 * Moves the ref of each ref-bearing untagged [ATTRIBUTELESS_BLOCKS] mark, with
 * the ARIA state that goes with it ([isMovedToRefSpan]: `role`, `aria-*`),
 * onto a tagged `span` around the block's leading inline content — the item's
 * own label — so it reaches the output as `- <span ref="23">Berlin Hbf</span>`.
 * Structural attributes the renderer reads from the block itself (`colspan`,
 * `rowspan`, `align`, `id`, …) stay on the block. A moved `aria-label` the span
 * already shows as its text (the name [labelActionableElements] wrote there)
 * is dropped rather than said twice.
 *
 * The label ends at the first [breaksRefSpan] mark — a nested block (a raw
 * inline tag cannot wrap a Markdown block) or a `<br>` (the parser closes
 * inline HTML at a line end, so a `</span>` after the break would come back as
 * literal text) — and so does the span, even when that mark sits deep inside
 * an inline wrapper (`<a><h3>…</h3></a>`): each top-level inline child is held
 * until it closes, and one that holds such a mark goes after the span instead
 * of into it. Three shapes of the item's *first* child need more than that:
 *
 * - a `p` (a loose item): the span moves into it, around its leading content;
 * - a child that carries a ref of its own (a `<button>`, a card `<a>`): it is
 *   what an agent acts on, so no empty span is put beside it;
 * - a leading raw void tag (a task-list checkbox `<input>`), which the
 *   renderer writes as a line-start block tag: it goes before the span.
 *
 * Any other item with no leading inline content gets an empty span, since its
 * ref is still something to act on. Bounded by one top-level inline child of
 * the innermost ref-bearing block at a time — which can be a whole card `<a>`
 * subtree, the same bound [encodeActionableLinkRefs] pays per link.
 *
 * A tagged mark is a literal tag that keeps its attributes, so it is left to
 * the matcher pass like any other element.
 */
private fun Flow<SemanticEvent>.moveRefsOffAttributelessBlocks(): Flow<SemanticEvent> = flow {
    // One frame per open ref-bearing block, innermost last. Only the innermost
    // can still be deciding its span: an inner block opens only after a
    // [breaksRefSpan] mark has settled every enclosing one.
    class Holder(val moved: Map<String, String>) {
        var depth = 0 // nesting depth below the block's own mark
        var state: SpanState = PENDING
        val child = mutableListOf<SemanticEvent>() // the held top-level inline child
        var childDepth = 0
    }
    val holders = ArrayDeque<Holder>()
    // The span attributes a ref-bearing item hands to its first `p`.
    var handedToParagraph: Map<String, String>? = null

    suspend fun openSpan(holder: Holder, firstText: String?) {
        val label = holder.moved["aria-label"]
        val attributes = if (label != null && firstText != null &&
            label.collapseWhitespace() == firstText.collapseWhitespace()
        ) holder.moved - "aria-label" else holder.moved
        emit(Mark("span", isTagged = true, attributes = attributes))
        holder.state = OPEN
    }

    // Ends the label: closes the span, or, when there was no label, emits an
    // empty one unless [besideOwnControl] says the next child carries a ref.
    suspend fun settle(holder: Holder, besideOwnControl: Boolean = false) {
        when (holder.state) {
            PENDING -> if (!besideOwnControl) {
                openSpan(holder, firstText = null)
                emit(Unmark("span", isTagged = true))
            }
            OPEN -> emit(Unmark("span", isTagged = true))
            CLOSED -> { /* already settled */ }
        }
        holder.state = CLOSED
    }

    suspend fun process(event: SemanticEvent) {
        val holder = holders.lastOrNull()
        if (holder != null && holder.state != CLOSED) {
            if (holder.childDepth > 0) {
                if (event is Mark && event.breaksRefSpan()) {
                    // The held child is no label: it goes after the span.
                    val held = holder.child.toList()
                    settle(holder, besideOwnControl = held.first().carriesRef())
                    holder.child.clear()
                    holder.childDepth = 0
                    held.forEach { process(it) }
                    process(event)
                    return
                }
                holder.child.add(event)
                when (event) {
                    is Mark -> holder.childDepth++
                    is Unmark -> holder.childDepth--
                    else -> { /* text does not nest */ }
                }
                if (holder.childDepth == 0) {
                    // A whole inline child, nothing in it breaking the label.
                    // A leading raw void tag stays outside: the renderer
                    // writes it as a block tag at the start of the line.
                    val first = holder.child.first() as Mark
                    val leadingVoid = holder.state == PENDING && first.isTagged &&
                        first.name in HTML_VOID_ELEMENTS
                    if (holder.state == PENDING && !leadingVoid) openSpan(holder, firstText = null)
                    holder.child.forEach { emit(it) }
                    holder.child.clear()
                }
                return
            }
            when {
                event is Mark && holder.state == PENDING && event.name == "p" &&
                    !event.isTagged && !event.carriesRef() -> {
                    // A loose item: its label is the paragraph's lead.
                    holder.state = CLOSED
                    handedToParagraph = holder.moved
                }
                event is Mark && event.breaksRefSpan() ->
                    settle(holder, besideOwnControl = event.carriesRef())
                event is Unmark -> settle(holder) // the block's own close
                event is Text -> {
                    if (holder.state == PENDING) openSpan(holder, event.text)
                    emit(event)
                    return
                }
                event is Mark -> {
                    holder.child.add(event)
                    holder.childDepth = 1
                    return
                }
                else -> { /* nothing else reaches here */ }
            }
        }
        when (event) {
            is Mark -> holders.forEach { it.depth++ }
            is Unmark -> holders.forEach { it.depth-- }
            else -> { /* text does not nest */ }
        }
        val handed = handedToParagraph
        when {
            handed != null && event is Mark -> {
                handedToParagraph = null
                emit(event)
                holders.addLast(Holder(handed))
            }
            event is Mark && !event.isTagged && event.name in ATTRIBUTELESS_BLOCKS &&
                event.carriesRef() -> {
                val (moved, kept) = event.attributes.entries.partition { it.key.isMovedToRefSpan() }
                emit(event.copy(attributes = kept.associate { it.toPair() }))
                holders.addLast(Holder(moved.associate { it.toPair() }))
            }
            event is Unmark && holder != null && holder.depth < 0 -> {
                holders.removeLast()
                emit(event)
            }
            else -> emit(event)
        }
    }

    collect { process(it) }
    // An unclosed block at end-of-stream (synthetic edge): flush what is held.
    holders.lastOrNull()?.let { holder ->
        if (holder.child.isNotEmpty()) {
            if (holder.state == PENDING) openSpan(holder, firstText = null)
            holder.child.forEach { emit(it) }
        }
        settle(holder)
    }
}

private fun SemanticEvent.carriesRef(): Boolean =
    this is Mark && this[AccessibilityAnnotations.REF] != null

/**
 * Whether an attribute of a ref-bearing block moves onto its ref span: the ref
 * and the ARIA role and state describing the control. Everything else is the
 * block's own structure (`colspan`, `align`, `id`, …).
 */
private fun String.isMovedToRefSpan(): Boolean =
    this == AccessibilityAnnotations.REF || this == "role" || startsWith("aria-")

/**
 * Whether this mark ends the inline label a ref span may wrap: a Markdown
 * block, or a `<br>`, which the parser would read as the end of the span's
 * line.
 */
private fun Mark.breaksRefSpan(): Boolean =
    name in LINK_BLOCK_CONTENT_TAGS || name in ATTRIBUTELESS_BLOCKS || name == "br"

/**
 * Trims and collapses whitespace runs — by [Char.isWhitespace], which counts a
 * no-break space on every target, unlike a `\s` regex, which does not on the
 * JVM — so an accessible name and the text written from it compare equal.
 */
private fun String.collapseWhitespace(): String = buildString {
    var pendingSpace = false
    for (c in this@collapseWhitespace) {
        if (c.isWhitespace()) {
            pendingSpace = isNotEmpty()
        } else {
            if (pendingSpace) append(' ')
            pendingSpace = false
            append(c)
        }
    }
}

private enum class SpanState { PENDING, OPEN, CLOSED }

public fun TransformerBuilder.encodeActionableNonLinkRefs() {

    // Every ref-bearing element except <a>: rename the DOM attribute to the
    // short one. No name guard is needed — the link pre-pass consumes the ref
    // of every <a>, including one nested inside another ref-bearing <a>, so
    // no ref-bearing <a> ever reaches this matcher.
    match({ this[AccessibilityAnnotations.REF] != null }) { event ->
        val ref = event[AccessibilityAnnotations.REF]!!
        reemit(
            event,
            (event.attributes - AccessibilityAnnotations.REF) + (ATTRIBUTE to ref)
        )
    }

    // Everything else (and all text) flows through verbatim.
    passthrough()
}

/**
 * Buffers each ref-bearing `<a>` subtree to pick its encoding: a link wrapping
 * any [LINK_BLOCK_CONTENT_TAGS] mark becomes a raw tag carrying `ref="<id>"`
 * with its original href intact; an inline-only link folds the ref into the
 * destination via [ActionableRef.encode]. Ref-less links — and everything
 * outside an `<a>` — stream through untouched. Buffering is bounded to one link
 * subtree at a time (the same bound the renderer's label capture already pays).
 *
 * A ref-bearing `<a>` nested inside the buffered subtree (a scripted-DOM shape —
 * parsed HTML auto-closes an open anchor before nesting another) is encoded
 * recursively by [emitEncodedLink], each link picking its form by its own
 * subtree — so no ref-bearing `<a>` ever leaves this pre-pass still carrying
 * [AccessibilityAnnotations.REF].
 */
private fun Flow<SemanticEvent>.encodeActionableLinkRefs(): Flow<SemanticEvent> = flow {
    val buffer = mutableListOf<SemanticEvent>()
    var depth = 0 // nesting depth of the buffered <a>; 0 = not buffering
    collect { event ->
        if (depth == 0) {
            if (event is SemanticEvent.Mark &&
                event.name == "a" &&
                event[AccessibilityAnnotations.REF] != null
            ) {
                buffer.add(event)
                depth = 1
            } else {
                emit(event)
            }
        } else {
            when (event) {
                is SemanticEvent.Mark if event.name == "a" -> depth++
                is SemanticEvent.Unmark if event.name == "a" -> depth--
                else -> { /* do nothing */ }
            }
            buffer.add(event)
            if (depth == 0) {
                emitEncodedLink(buffer)
                buffer.clear()
            }
        }
    }
    // An unclosed <a> at end-of-stream (synthetic edge): flush what we captured.
    if (buffer.isNotEmpty()) emitEncodedLink(buffer)
}

// Encodes the ref-bearing <a> opening buffer[start] and re-emits its subtree
// [start+1, end), recursing into each nested ref-bearing <a> so every link
// picks its encoding by its own subtree — an inner ref left for the non-link
// pass would take the attribute form, which a rendered inline `[label](url)`
// then drops (Markdown links carry no attributes), losing the ref.
private suspend fun FlowCollector<SemanticEvent>.emitEncodedLink(
    buffer: List<SemanticEvent>,
    start: Int = 0,
    end: Int = buffer.size
) {
    val open = buffer[start] as Mark
    val ref = open[AccessibilityAnnotations.REF]!!
    val wrapsBlockContent = buffer.subList(start + 1, end).any {
        it is Mark && it.name in LINK_BLOCK_CONTENT_TAGS
    }
    val attributes = (open.attributes - AccessibilityAnnotations.REF) +
        if (wrapsBlockContent) {
            // Raw `<a …>` tag — carry the ref like any other element, keep the
            // real href.
            ATTRIBUTE to ref
        } else {
            // Markdown `[label](…)` — fold the ref into the destination, since a
            // Markdown link can't carry attributes.
            "href" to encode(ref, open["href"] ?: "")
        }
    emit(open.copy(attributes = attributes))
    var i = start + 1
    while (i < end) {
        val event = buffer[i]
        if (event is Mark && event.name == "a" && event[AccessibilityAnnotations.REF] != null) {
            val innerEnd = linkSubtreeEnd(buffer, i, end)
            emitEncodedLink(buffer, i, innerEnd)
            i = innerEnd
        } else {
            emit(event)
            i++
        }
    }
}

// Index just past the matching </a> of the <a> mark at [start], or [end] when
// the close never arrives (an unclosed link in the end-of-stream flush).
private fun linkSubtreeEnd(buffer: List<SemanticEvent>, start: Int, end: Int): Int {
    var depth = 0
    for (i in start until end) {
        when (val event = buffer[i]) {
            is Mark if event.name == "a" -> depth++
            is Unmark if event.name == "a" -> {
                depth--
                if (depth == 0) return i + 1
            }
            else -> { /* content */ }
        }
    }
    return end
}

// Re-emits [event] with [attributes], preserving its tagged/untagged form and
// recursing into its subtree.
private suspend fun MatcherScope.reemit(
    event: SemanticEvent.Mark,
    attributes: Map<String, String>
) {
    if (event.isTagged) tag(event.name, attributes) { children() }
    else event.name(attributes) { children() }
}