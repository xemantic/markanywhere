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
import com.xemantic.markanywhere.html.icons.BootstrapIconResolver
import com.xemantic.markanywhere.html.icons.FontAwesomeIconResolver
import com.xemantic.markanywhere.html.icons.GlyphiconResolver
import com.xemantic.markanywhere.html.icons.MaterialIconResolver
import com.xemantic.markanywhere.transform.MarkSelect
import com.xemantic.markanywhere.transform.toFirstMatchSelect
import com.xemantic.markanywhere.transform.transformMatchingMarks
import kotlinx.coroutines.flow.Flow

/**
 * Replaces icon-font elements with an `icon` mark carrying a textual token
 * (emoji) **before** [simplifyHtml] strips their `class`.
 *
 * Decorative-looking icon glyphs (`<i class="fa-solid fa-square-info"></i>`)
 * are empty elements whose only signal is the `class` — yet that signal is
 * meaningful (info / warning / external-link / …). Dropping them loses
 * information; passing the empty element through produces noise (a stray `**`
 * once `<i>` is renamed to `<em>`). This operator turns the glyph into a token
 * an LLM reads directly: `[ℹ️ Versionen](…)` instead of `[Versionen](…)`.
 *
 * Each opening [SemanticEvent.Mark] is offered to [DEFAULT_ICON_RESOLVER];
 * when it returns a transform, the element's whole subtree is replaced by the
 * transform's emissions (see [transformMatchingMarks]). Unmatched elements
 * replay unchanged, and nesting works — a genuine content span is left intact
 * while an icon inside it still resolves.
 *
 * Run this first in the pipeline, on the raw HTML-derived stream, ahead of
 * [dropHtmlStructuralWhitespace] and [simplifyHtml]. Any recognized-library
 * glyph that lacks a mapping still resolves to a derived `:name:` hint (see the
 * built-in resolvers), so unmatched leftovers reaching [dropBlankInlineFormatting]
 * are only the genuinely empty, non-icon wrappers.
 */
public fun Flow<SemanticEvent>.resolveIcons(): Flow<SemanticEvent> = transformMatchingMarks(
    DEFAULT_ICON_RESOLVER
)

/**
 * The default [resolveIcons] chain: FontAwesome, Bootstrap Icons, Material
 * Icons/Symbols, then Glyphicons — the earliest resolver claiming a mark wins.
 * To add, reorder, or replace resolvers, compose your own list, combine it
 * with [toFirstMatchSelect], and pass the result to [transformMatchingMarks]
 * in place of [resolveIcons].
 */
public val DEFAULT_ICON_RESOLVER: MarkSelect = listOf(
    FontAwesomeIconResolver,
    BootstrapIconResolver,
    MaterialIconResolver,
    GlyphiconResolver,
).toFirstMatchSelect()
