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
import com.xemantic.markanywhere.transform.MatcherScope
import com.xemantic.markanywhere.transform.TransformerBuilder
import com.xemantic.markanywhere.transform.transform
import kotlinx.coroutines.flow.Flow

/**
 * The Markdown-output encoding of an actionable element's ref — the bridge
 * between the dump's namespace-safe DOM ref ([AccessibilityAnnotations.REF],
 * `data-markanywhere-ref`) and a short, token-cheap, LLM-facing handle an agent
 * can name to act on the element.
 *
 * Two surfaces, because Markdown links carry no attributes:
 *
 * - **Links** (`<a>`) embed the ref *in the destination* via the [SCHEME]
 *   pseudo-scheme — `[label](ref:42:/menu)`, `[ext](ref:52:https://example.com)`.
 *   The original href rides along after the id, so the agent sees both where the
 *   link goes *and* a handle to click it (faithful for `href="#"` / JS controls
 *   that a hard navigation can't trigger). [decode] splits it back on the first
 *   two `:` (the id is numeric; the original href keeps its own colons).
 * - **Hrefless controls** (`<button>`, `<input>`, a promoted `role=button`
 *   container, …) carry it as the short [ATTRIBUTE] (`ref="42"`).
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
 * Rewrites the dump's [AccessibilityAnnotations.REF] into its LLM-facing
 * [ActionableRef] form — link refs into the `ref:<id>:<href>` destination,
 * every other ref-bearing element into the short `ref="<id>"` attribute.
 * Elements without a ref pass through untouched.
 *
 * This is a **separate, optional step** on purpose: the rest of the pipeline
 * ([simplifyHtml] etc.) is ref-agnostic, so a human-readable rendering can reuse
 * the same machinery and simply omit this transformer (and not ask `simplifyHtml`
 * to keep the ref). Apply it after `simplifyHtml` has preserved the ref — see
 * [transformHtmlToMarkdown].
 */
public fun Flow<SemanticEvent>.encodeActionableRefs(): Flow<SemanticEvent> = transform {
    encodeActionableRefs()
}

public fun TransformerBuilder.encodeActionableRefs() {

    // Links: fold the ref into the destination, drop the DOM attribute.
    match("a") { event ->
        val ref = event[AccessibilityAnnotations.REF]
        val attributes = if (ref == null) event.attributes else
            (event.attributes - AccessibilityAnnotations.REF) +
                ("href" to ActionableRef.encode(ref, event["href"] ?: ""))
        reemit(event, attributes)
    }

    // Every other ref-bearing element: rename the DOM attribute to the short one.
    match({ this[AccessibilityAnnotations.REF] != null }) { event ->
        val ref = event[AccessibilityAnnotations.REF]!!
        reemit(
            event,
            (event.attributes - AccessibilityAnnotations.REF) + (ActionableRef.ATTRIBUTE to ref)
        )
    }

    // Everything else (and all text) flows through verbatim.
    passthrough()
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