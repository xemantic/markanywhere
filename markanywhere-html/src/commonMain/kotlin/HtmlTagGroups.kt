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

/**
 * Named sets of HTML tag names shared by more than one operator in this module.
 *
 * Admission rule: a tag-name set belongs here only once at least two operators
 * consume it. Single-use sets stay private to their operator (e.g.
 * `SimplifyHtml`'s `PRESERVE_WITH_ID_TAGS`, `ApplyAccessibility`'s
 * `SPACE_TAKING_ELEMENTS`).
 *
 * A set that is a plain fact of the HTML standard needed by more than one of
 * `parse` / `render` / `html` belongs in `markanywhere-html-spec` instead. The
 * UA display defaults below stay here: only this module consumes them, and they
 * are curated for whitespace and box classification rather than a transcript
 * of the UA stylesheet (`br` counts as non-inline, the `display: none` set is
 * folded into the non-inline one).
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

/**
 * Whether the UA stylesheet lays this element out inline — the default every
 * operator starts from for an element with no captured display (a hand-built
 * or Markdown-native stream).
 *
 * Operators classifying an element as inline content or a block boundary read
 * it through [isInlineLevel], which adds one exception: the synthetic
 * `frontmatter` block is a block, not the unknown (so inline) tag a browser
 * would see. `ApplyAccessibility`'s `takesSpaceWhenEmpty` alone reads the raw
 * UA default, asking a different question — whether an invisible element lays
 * out a box of its own width — and metadata lays out none.
 */
internal fun SemanticEvent.Mark.isInlineByUaDefault(): Boolean = isInlineByUaDefault(name)

internal fun isInlineByUaDefault(name: String): Boolean = name !in UA_NON_INLINE_ELEMENTS

/**
 * Whether an element of this [name] is inline-level when nothing but its name
 * is known — a mark with no captured display, or a close whose open never
 * arrived (a truncated stream), which must reach the same verdict as its open.
 *
 * The UA default of the tag ([isInlineByUaDefault]) decides, except for the
 * synthetic `frontmatter` block (`simplifyHtml`'s, or the parser's): it is no
 * HTML element at all — a browser would lay an unknown tag out inline — but
 * document metadata standing apart from the body, so it is a block.
 */
internal fun isInlineLevel(name: String): Boolean =
    name != "frontmatter" && isInlineByUaDefault(name)

/**
 * Whether the UA stylesheet never renders this element (`display: none`) —
 * the subset of the non-inline elements laying out no box at all.
 *
 * Shared by [separateUnwrappedBlocks] and [applyAccessibility].
 */
internal fun SemanticEvent.Mark.isNeverRenderedByUaDefault(): Boolean = name in UA_NEVER_RENDERED_ELEMENTS

internal val UA_NEVER_RENDERED_ELEMENTS: Set<String> = setOf(
    "head", "title", "meta", "link", "base", "style", "script", "noscript",
    "template", "area", "datalist", "param", "source", "track", "noembed",
    "noframes",
)

/**
 * The elements the HTML rendering section (WHATWG HTML §15.3) lays out as
 * anything but inline — `block`, `list-item`, the `table-*` family — plus the
 * ones it never renders ([UA_NEVER_RENDERED_ELEMENTS], `display: none`), which
 * are no inline content either, and `br`, a line break rather than a space.
 *
 * The polarity matters: every other element, an unknown or custom one
 * (`x-currency`, `my-b`) included, is inline by default, as in a browser, so a
 * web component inside a word does not split it
 * (`Price: <x-currency>€</x-currency>5`).
 */
internal val UA_NON_INLINE_ELEMENTS: Set<String> = setOf(
    // block
    "html", "body", "address", "article", "aside", "blockquote", "center",
    "dialog", "dir", "div", "dd", "details", "dl", "dt", "fieldset",
    "figcaption", "figure", "footer", "form", "frame", "frameset",
    "h1", "h2", "h3", "h4", "h5", "h6", "header", "hgroup", "hr", "legend",
    "listing", "main", "menu", "nav", "ol", "optgroup", "option", "p",
    "plaintext", "pre", "search", "section", "summary", "ul", "xmp",
    // list-item
    "li",
    // table
    "table", "caption", "colgroup", "col", "thead", "tbody", "tfoot", "tr",
    "td", "th",
    // line break
    "br",
) + UA_NEVER_RENDERED_ELEMENTS
