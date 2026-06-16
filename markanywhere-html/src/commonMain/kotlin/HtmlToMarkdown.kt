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
 * Converts an HTML-derived semantic event stream into a Markdown-equivalent one
 * for LLM/agent consumption: icon glyphs resolved, presentational noise
 * simplified away, blank formatting and structural whitespace dropped, and the
 * dump's actionable refs encoded into their compact [ActionableRef] form.
 *
 * The actionable-ref handling is the only agent-specific step: [simplifyHtml] is
 * asked to preserve [AccessibilityAnnotations.REF], and [encodeActionableRefs]
 * rewrites it last. A human-readable rendering can reuse every other operator
 * and simply drop both (call them directly instead of this helper).
 */
public fun Flow<SemanticEvent>.transformHtmlToMarkdown(
    keepAttributes: Set<String> = emptySet(),
): Flow<SemanticEvent> = resolveIcons()
    .applyAccessibility()
    // DISPLAY is preserved through simplify so dropHtmlStructuralWhitespace can
    // gate whitespace on the browser's computed block/inline verdict; it strips
    // the annotation itself.
    .simplifyHtml(keepAttributes + AccessibilityAnnotations.REF + AccessibilityAnnotations.DISPLAY)
    .dropBlankInlineFormatting()
    .dropHtmlStructuralWhitespace()
    .encodeActionableRefs()
