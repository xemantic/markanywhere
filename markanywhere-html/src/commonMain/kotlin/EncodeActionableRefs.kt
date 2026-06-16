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
import com.xemantic.markanywhere.dump.AccessibilityAnnotations
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
 * keep the two copies in sync when either changes.
 */
internal val LINK_BLOCK_CONTENT_TAGS: Set<String> = setOf(
    // BLOCK_LEVEL_MARK_NAMES (Markdown block marks)
    "h1", "h2", "h3", "h4", "h5", "h6",
    "p", "hr", "blockquote",
    "ul", "ol", "li",
    "pre",
    "table", "thead", "tbody", "tr",
    // BLOCK_TAGGED_ELEMENTS (block-level raw HTML tags)
    "section", "nav", "article", "aside",
    "header", "footer", "main", "hgroup",
    "figure", "figcaption",
    "details", "summary", "dialog",
    "form", "fieldset", "legend",
    "button", "select", "textarea",
    "optgroup", "option", "datalist",
    "address", "search"
)

/**
 * Rewrites the dump's [AccessibilityAnnotations.REF] into its LLM-facing
 * [ActionableRef] form. An inline `<a>` folds its ref into the
 * `ref:<id>:<href>` destination; a block-wrapping `<a>` (rendered as a raw tag)
 * and every other ref-bearing element take the short `ref="<id>"` attribute.
 * Elements without a ref pass through untouched.
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
    encodeActionableLinkRefs().transform {
        encodeActionableNonLinkRefs()
    }

public fun TransformerBuilder.encodeActionableNonLinkRefs() {

    // Every ref-bearing element except <a> (handled by the link pre-pass):
    // rename the DOM attribute to the short one.
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

private suspend fun FlowCollector<SemanticEvent>.emitEncodedLink(
    buffer: List<SemanticEvent>
) {
    val open = buffer.first() as Mark
    val ref = open[AccessibilityAnnotations.REF]!!
    val wrapsBlockContent = buffer.asSequence().drop(1).any {
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
    for (i in 1 until buffer.size) emit(buffer[i])
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