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
import kotlinx.coroutines.flow.Flow

/**
 * Selects how [transformHtmlToMarkdown] treats a dump's actionable refs
 * ([AccessibilityAnnotations.REF], stamped on links/controls during capture) —
 * the two conceptual output contracts of the pipeline.
 */
public enum class RefMode {

    /**
     * Preserve and encode the refs into their compact [ActionableRef] form
     * (`[label](ref:42:/href)` for inline links, `ref="42"` for everything
     * else), so an agent that read the Markdown can name an element back and
     * act on it on the live page. This is the default — a *stateful* proxy of
     * the DOM tree.
     */
    ENCODE,

    /**
     * Drop the refs entirely for a clean, standard Markdown dump: no `ref:`
     * destinations and no `ref="…"` attributes anywhere — links carry only
     * their real href. The ref is **not preserved in the first place** (dropped
     * from `simplifyHtml`'s keep-set), so there is nothing to encode later.
     */
    STRIP,

}

/**
 * Converts an HTML-derived semantic event stream into a Markdown-equivalent one
 * for LLM/agent consumption: icon glyphs resolved, presentational noise
 * simplified away, blank formatting and structural whitespace dropped.
 *
 * [refMode] picks one of two output contracts for the dump's actionable refs
 * ([AccessibilityAnnotations.REF]):
 *
 * - [RefMode.ENCODE] (default) — a *stateful* proxy of the DOM tree: the ref is
 *   preserved through [simplifyHtml] and rewritten last by
 *   [encodeActionableRefs] into its compact [ActionableRef] form, so an agent
 *   can reference DOM nodes back.
 * - [RefMode.STRIP] — a *clean* Markdown dump: the ref is dropped from
 *   `simplifyHtml`'s keep-set (so it is never carried into the output) and the
 *   [encodeActionableRefs] step is skipped entirely — no `ref:`/`ref="…"`
 *   appears anywhere, links keep only their real href. [AccessibilityAnnotations.REF]
 *   is removed from [keepAttributes] too, so an explicit `keepAttributes =
 *   setOf(REF)` cannot smuggle the ref past STRIP.
 *
 * The actionable-ref handling is the only agent-specific step; every other
 * operator is ref-agnostic, so a human-readable rendering can reuse the same
 * machinery and call them directly (the [RefMode.STRIP] pipeline, minus the
 * keep-set decision).
 */
public fun Flow<SemanticEvent>.transformHtmlToMarkdown(
    keepAttributes: Set<String> = emptySet(),
    refMode: RefMode = RefMode.ENCODE,
): Flow<SemanticEvent> {
    // The shared, ref-agnostic pipeline up to (but excluding) the ref-encoding
    // step. [refKeep] is the ref's contribution to `simplifyHtml`'s keep-set;
    // REF is first removed from the caller's [keepAttributes] so its presence is
    // governed solely by [refMode] (an explicit `keepAttributes = setOf(REF)`
    // cannot bypass STRIP).
    fun base(refKeep: Set<String>) = resolveIcons()
        .applyAccessibility()
        // After applyAccessibility (aria-hidden SVGs already dropped), before
        // simplifyHtml (which would otherwise discard the whole svg subtree): turn
        // an accessible-name-bearing inline <svg> logo/wordmark into an ![name]().
        .resolveInlineGraphics()
        // Still before simplifyHtml (which unwraps block boxes and discards their
        // DISPLAY annotation): inject a separator where flattening an unwrapped
        // block box would merge inline content from two boxes that share no source
        // whitespace (e.g. the BBC card metadata `3 hrs ago` + `Europe`, an image
        // box next to a `LIVE` badge box).
        .separateUnwrappedBlocks()
        // DISPLAY is preserved through simplify so dropHtmlStructuralWhitespace can
        // gate whitespace on the browser's computed block/inline verdict; it strips
        // the annotation itself.
        .simplifyHtml((keepAttributes - AccessibilityAnnotations.REF) + refKeep + AccessibilityAnnotations.DISPLAY)
        .dropBlankInlineFormatting()
        .dropHtmlStructuralWhitespace()
    // One `when` couples both ref decisions (keep-set + encode) so they cannot
    // drift, and stays exhaustive if a third RefMode is ever added.
    return when (refMode) {
        RefMode.ENCODE -> base(setOf(AccessibilityAnnotations.REF)).encodeActionableRefs()
        RefMode.STRIP -> base(emptySet())
    }
}
