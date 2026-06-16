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

/**
 * Named sets of HTML tag names shared by more than one operator in this module.
 *
 * Admission rule: a tag-name set belongs here only once at least two operators
 * consume it. Single-use sets stay private to their operator (e.g.
 * `SimplifyHtml`'s `PRESERVE_WITH_ID_TAGS`, the whitespace transformer's
 * broader `INLINE_HTML_ELEMENTS`).
 */

/**
 * Pure inline *formatting* / emphasis elements — phrasing tags that carry no
 * meaning beyond their styling, so they are safe to drop when they wrap no
 * content.
 *
 * Shared by [simplifyHtml] (which preserves these tags while dropping their
 * attributes) and [dropBlankInlineFormatting] (which drops them when blank).
 * Because the latter only ever runs *after* the former, the inline formatting
 * tags it can encounter are exactly the ones simplify kept — so the two stay in
 * lock-step by construction rather than by two hand-maintained copies.
 *
 * Deliberately excludes `a`, `img`, `code`, and `span`: these can be meaningful
 * even when textless, and simplify handles them via attribute whitelists /
 * unwrapping rather than as bare emphasis.
 */
internal val INLINE_FORMATTING_TAGS: Set<String> = setOf(
    "em", "strong", "del", "mark", "sub", "sup", "u", "small",
    "cite", "abbr", "kbd", "samp", "var", "time", "q", "dfn", "ins", "i", "b", "s", "strike"
)