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

import com.xemantic.kotlin.core.text.unaryPlus
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.dump.AccessibilityAnnotations
import com.xemantic.markanywhere.transform.TransformerBuilder
import com.xemantic.markanywhere.transform.transform
import kotlinx.coroutines.flow.Flow

public fun Flow<SemanticEvent>.simplifyHtml(
    keepAttributes: Set<String> = emptySet(),
): Flow<SemanticEvent> = transform {
    simplifyHtml(keepAttributes)
}

/**
 * Simplifies an HTML-derived semantic event stream by keeping only the
 * tags that carry semantic meaning and discarding presentational noise.
 *
 * The resulting stream emits **untagged** events (`isTagged = false`).
 * Downstream consumers — notably `renderMarkdown()` — treat the
 * Markdown-equivalent tags (`p`, `h1`–`h6`, `ul`, `ol`, `li`, `em`,
 * `strong`, `a`, `img`, tables, etc.) as native Markdown and pass the
 * remaining structural / form tags through as their HTML form.
 *
 * Behaviour by tag class:
 *
 * - **Drop (any element)**: `aria-hidden="true"` — the element is hidden from
 *   the accessibility tree (decorative / duplicate / off-screen), so it and its
 *   whole subtree are discarded, like `display:none`. Checked first, before the
 *   per-tag rules below.
 * - **Drop**: `script`, `style`, `link`, `noscript` — the wrapper and all
 *   of its content are discarded.
 * - **Unwrap**: `body`, `div`, `span`, `font`, `center`, `tt` — the
 *   wrapper is removed, children flow through unchanged.
 * - **Preserve with `id` only**:
 *   - structural: `article`, `aside`, `footer`, `header`, `main`, `nav`,
 *     `section`, `figure`, `figcaption`, `details`, `summary`, `address`,
 *     `hgroup`
 *   - block content: `p`, `blockquote`, `pre`, `hr`, `br`, `h1`–`h6`,
 *     `ul`, `li`, `dl`, `dt`, `dd`, table family (`table`, `thead`,
 *     `tbody`, `tfoot`, `tr`)
 * - **Preserve, drop all attributes**: inline emphasis — `em`, `strong`,
 *   `del`, `mark`, `sub`, `sup`, `u`, `s`, `i`, `b`, `small`, `cite`,
 *   `abbr`, `kbd`, `samp`, `var`, `time`, `q`, `dfn`, `ins`
 * - **Preserve with attribute whitelist**:
 *   - `a` — `href`, `title`, `id`
 *   - `img` — `src`, `alt`, `title`, `id`
 *   - `code` — `class` (language hint), `id`
 *   - `ol` — `start`, `id`
 *   - `th`, `td` — `align`, `colspan`, `rowspan`, `scope`, `headers`, `id`
 *   - form elements — their semantically meaningful attributes
 * - **ARIA role promotion**: a `role` attribute on an otherwise generic element
 *   recovers the semantics the author expressed through it — `role="heading"`
 *   (+ `aria-level`) → `h1`–`h6`, `role="img"` → `img` (accessible name as
 *   `alt`), `role="separator"` → `hr`, `role="presentation"`/`"none"` → unwrap,
 *   and the landmark / list / table-grid roles (see [ROLE_TO_TAG]) → their
 *   semantic tag. Promotion is gated to generic containers ([UNWRAPPED_TAGS]) so
 *   a `role` only upgrades an element that would otherwise be unwrapped and never
 *   overrides a native semantic tag (a `<form role="search">` stays a form). The
 *   `presentation`/`none` unwrap is the exception — it deliberately strips an
 *   element's own semantics, so it applies to any element. The now-redundant
 *   `role` is dropped.
 * - **ARIA keep-set (every preserved element)**: on top of each element's own
 *   whitelist, the accessible name and actionable state survive — `aria-label`,
 *   `aria-expanded`, `aria-haspopup`, `aria-current`, `aria-checked`,
 *   `aria-selected`, `aria-pressed`, `aria-disabled` (see [ARIA_KEEP]). These
 *   are often the only label on an icon control and the state an LLM-driven
 *   agent needs to decide how to interact. Inline emphasis carries no
 *   attributes, so it does not surface these. Other `aria-*` (id-reference,
 *   live-region, positional) and `role` are dropped.
 * - **Caller keep-set ([keepAttributes])**: a generic escape hatch for
 *   application-specific attributes the caller wants to survive simplification
 *   — e.g. a `golemId` correlation id mapping events back to their source DOM
 *   nodes. These are kept on every preserved element (on top of its own
 *   whitelist and the ARIA keep-set), are carried through inline emphasis
 *   (which otherwise drops all attributes), and **promote an otherwise-unwrapped
 *   element to a preserved wrapper** when it actually carries one (so a
 *   `<span golemId="…">` survives instead of being unwrapped; a `<span>`
 *   without survives as an unwrap). They never resurrect a dropped subtree
 *   (`script` / `style` / `aria-hidden`). Empty by default, so the default
 *   behaviour is unchanged. Note: at Markdown render time, attributes on
 *   emphasis / `img` / void elements are dropped (Markdown syntax can't carry
 *   them) — the keep-attribute still survives in the event stream and in
 *   `asHtml()` output.
 *
 * Metadata extraction: `<html lang>` and any `<meta name="…"
 * content="…">` inside `<head>`, along with `<title>` text, are collected
 * and emitted as a single synthetic `frontmatter` mark with those values
 * as attributes just before `<body>` content streams through. Technical meta
 * names that carry no content signal (rendering hints, crawler / verification
 * directives, platform tile metadata — see [isNoiseMetaName]) are dropped so
 * they don't inflate the frontmatter. If `<head>` is absent or yields no
 * metadata, no frontmatter mark is emitted.
 *
 * Matcher registration is grouped: per-tag explicit matchers come first
 * (so they win the `firstOrNull` race), then a small number of
 * expression-based matchers handle whole tag families via set / map
 * lookup — one matcher per family, not one per tag.
 *
 * Registers its rules on the receiving [TransformerBuilder]; use it inside a
 * [com.xemantic.markanywhere.transform.transform] block — e.g.
 * `flow.simplifyHtml()`. The per-collection rebuild of the
 * transform pipeline reinitialises the `metadata` / `titleText` accumulators
 * captured below on every run, so the same flow can be collected repeatedly
 * without state leaking between collections.
 *
 * @param keepAttributes application-specific attribute names to preserve on
 *   every element that survives simplification (see the "Caller keep-set"
 *   behaviour above). Empty by default.
 */
public fun TransformerBuilder.simplifyHtml(
    keepAttributes: Set<String> = emptySet()
) {

    val metadata = mutableMapOf<String, String>()
    val titleText = StringBuilder()

    // Attribute map kept on a preserved element: its own [names] whitelist, the
    // ARIA name/state keep-set, and any caller-requested [keepAttributes]. An
    // explicit `class` in [names] (e.g. `code`'s language hint) keeps the full
    // attribute value.
    fun preserveAttrs(
        event: SemanticEvent.Mark,
        vararg names: String,
    ): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (name in names) event[name]?.let { result[name] = it }
        for (name in ARIA_KEEP) event[name]?.let { result[name] = it }
        for (name in keepAttributes) event[name]?.let { result[name] = it }
        return result
    }

    // Just the caller-requested keep-attributes present on this event (no ARIA,
    // no per-tag whitelist) — used to carry the correlation attribute through
    // elements that otherwise drop all attributes (inline emphasis) or are
    // unwrapped. Empty when the caller requested none, so the emitted mark is
    // identical to the no-attributes form and default behaviour is unchanged.
    fun extraKept(event: SemanticEvent.Mark): Map<String, String> {
        if (keepAttributes.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (name in keepAttributes) event[name]?.let { result[name] = it }
        return result
    }

    // --- aria-hidden subtree drop (must win over every other matcher) ---

    // `aria-hidden="true"` removes the element from the accessibility tree
    // (decorative, duplicated, or off-screen content) — drop it and its whole
    // subtree, like `display:none`. Registered first so it beats the per-tag
    // and group matchers below in the `firstOrNull` race; the empty body skips
    // the subtree (no `children()` call).
    match({ this["aria-hidden"]?.equals("true", ignoreCase = true) == true }) { /* drop */ }

    // --- metadata extraction (explicit per-tag) -------------------------

    match("html") { event ->
        event["lang"]?.let { metadata["lang"] = it }
        children()
    }

    match("head") {
        // Descend in "head" mode so title/meta resolve and loose text is
        // swallowed (see the mode-scoped matchText below); emit no mark.
        children(mode = "head")
        afterClose {
            if (metadata.isNotEmpty()) {
                val yaml = renderYamlFrontmatter(metadata)
                "frontmatter"(mapOf("format" to "yaml")) {
                    +yaml
                }
            }
        }
    }

    match("title") {
        // Capture the title's text into metadata; the mark itself is dropped.
        children(mode = "titleText")
        afterClose {
            val trimmed = titleText.toString().trim()
            if (trimmed.isNotEmpty()) {
                metadata["title"] = trimmed
            }
        }
    }

    match("meta") { event ->
        val name = event["name"]
        val content = event["content"]
        if (name != null && content != null && !isNoiseMetaName(name)) {
            metadata[name] = content
        }
    }

    // Loose text directly inside <head> is structural noise — swallow it.
    matchText(mode = "head") { /* discard */ }

    // <title> text (including text inside nested inline marks) is captured
    // into metadata rather than emitted; the wildcard unwraps any nested
    // mark while keeping its text flowing to the capture above.
    matchText(mode = "titleText") { titleText.append(it) }
    match("*", mode = "titleText") { children(mode = "titleText") }

    // --- tags with custom attribute whitelists (explicit per-tag) -------

    match("a") { event ->
        "a"(preserveAttrs(event, "href", "title", "id")) { children() }
    }

    match("img") { event ->
        "img"(preserveAttrs(event, "src", "alt", "title", "id")) { /* void */ }
    }

    match("code") { event ->
        "code"(preserveAttrs(event, "class", "id")) { children() }
    }

    match("ol") { event ->
        "ol"(preserveAttrs(event, "start", "id")) { children() }
    }

    match("th") { event ->
        "th"(preserveAttrs(event, "align", "colspan", "rowspan", "scope", "headers", "id")) {
            children()
        }
    }

    match("td") { event ->
        "td"(preserveAttrs(event, "align", "colspan", "rowspan", "scope", "headers", "id")) {
            children()
        }
    }

    // --- ARIA role promotion (explicit, before the tag-family groups) ---

    // Recover the document semantics an author expressed via `role` on otherwise
    // generic containers (the div/span soup of SPAs and design systems). The
    // promotion matchers are gated to UNWRAPPED_TAGS so a `role` never overrides
    // a native semantic tag — a `<form role="search">` stays a form, a
    // `<button role="button">` stays a button — it only upgrades an element that
    // would otherwise be unwrapped. The now-redundant `role` is dropped (it is
    // not in ARIA_KEEP). `presentation`/`none` is the exception: it strips an
    // element's *own* semantics on purpose, so it applies to any element (e.g. a
    // layout `<table role="presentation">`), carrying any caller keep-attribute
    // like the UNWRAPPED_TAGS rule does.
    match({ this["role"]?.lowercase() in PRESENTATION_ROLES }) { event ->
        val kept = extraKept(event)
        if (kept.isEmpty()) children()
        else event.name(kept) { children() }
    }

    // `role="heading"` + `aria-level` → `h1`–`h6` (ARIA's default level is 2).
    match({ name in UNWRAPPED_TAGS && this["role"]?.lowercase() == "heading" }) { event ->
        val level = event["aria-level"]?.toIntOrNull()?.coerceIn(1, 6) ?: 2
        "h$level"(preserveAttrs(event, "id")) { children() }
    }

    // `role="img"` → image; its accessible name becomes the alt text.
    match({ name in UNWRAPPED_TAGS && this["role"]?.lowercase() == "img" }) { event ->
        val attrs = preserveAttrs(event, "id").toMutableMap()
        event["aria-label"]?.let { attrs["alt"] = it }
        "img"(attrs) { /* void — descriptive children collapse to the name */ }
    }

    // `role="separator"` → thematic break (void).
    match({ name in UNWRAPPED_TAGS && this["role"]?.lowercase() == "separator" }) { event ->
        "hr"(preserveAttrs(event, "id")) { /* void */ }
    }

    // Remaining promotable roles are straight renames to the semantic tag,
    // keeping `id` (+ ARIA name/state via preserveAttrs); children flow through.
    match({ name in UNWRAPPED_TAGS && this["role"]?.lowercase() in ROLE_TO_TAG }) { event ->
        val target = ROLE_TO_TAG.getValue(event["role"]!!.lowercase())
        target(preserveAttrs(event, "id")) { children() }
    }

    // --- group rules (one matcher per family, name lookup) --------------

    // Not descending (no children()) skips the wrapper and its whole subtree.
    match({ name in DROPPED_TAGS }) { }

    // Unwrap presentational wrappers — but if one carries a caller-requested
    // keep-attribute, promote it to a preserved wrapper so the attribute (e.g.
    // a `golemId` correlation id) is not lost with the unwrapped tag.
    match({ name in UNWRAPPED_TAGS }) { event ->
        val kept = extraKept(event)
        // The display annotation is downstream whitespace metadata, not
        // meaningful content — it alone must not promote an otherwise-unwrapped
        // element (and is dropped with the unwrapped tag, as it's no longer
        // needed once the element is gone).
        if (kept.keys.all { it == AccessibilityAnnotations.DISPLAY }) children()
        else event.name(kept) { children() }
    }

    match({ name in PRESERVE_WITH_ID_TAGS }) { event ->
        event.name(preserveAttrs(event, "id")) { children() }
    }

    match({ name in INLINE_FORMATTING_TAGS }) { event ->
        event.name(extraKept(event)) { children() }
    }

    match({ name in FORM_ELEMENT_ATTRS }) { event ->
        val allowed = FORM_ELEMENT_ATTRS.getValue(event.name)
        event.name(preserveAttrs(event, *allowed)) { children() }
    }

    match("icon") {
        children()
    }

    // Body content text passes through unchanged.
    matchText { +it }
}

private val DROPPED_TAGS = setOf(
    "script", "style", "link", "noscript",
)

// `role` values that strip an element's semantics while keeping its children.
private val PRESENTATION_ROLES = setOf("presentation", "none")

// ARIA roles promoted to the equivalent semantic tag (straight rename, children
// preserved). `heading`, `img`, `separator`, and `presentation`/`none` are
// handled separately because they need a level, alt text, void output, or an
// unwrap respectively. Landmarks, list/grouping, and the ARIA table/grid family
// are covered here so authored-on-`<div>` structure survives into Markdown.
private val ROLE_TO_TAG = mapOf(
    // landmarks
    "article" to "article",
    "banner" to "header",
    "complementary" to "aside",
    "contentinfo" to "footer",
    "main" to "main",
    "navigation" to "nav",
    "region" to "section",
    "search" to "section",
    // grouping / block content
    "list" to "ul",
    "listitem" to "li",
    "paragraph" to "p",
    "blockquote" to "blockquote",
    "code" to "code",
    "figure" to "figure",
    // table / grid family
    "table" to "table",
    "grid" to "table",
    "rowgroup" to "tbody",
    "row" to "tr",
    "columnheader" to "th",
    "rowheader" to "th",
    "cell" to "td",
    "gridcell" to "td",
)

private val UNWRAPPED_TAGS = setOf(
    "body", "div", "span", "font", "center", "tt",
)

// Structural HTML5 elements + Markdown-equivalent block content.
// All get the same treatment: preserve the tag, retain `id` only.
private val PRESERVE_WITH_ID_TAGS = setOf(
    // structural
    "article", "aside", "footer", "header", "main", "nav", "section",
    "figure", "figcaption", "details", "summary", "address", "hgroup",
    // Markdown-equivalent blocks
    "p", "blockquote", "pre", "hr", "br",
    "h1", "h2", "h3", "h4", "h5", "h6",
    "ul", "li",
    "table", "thead", "tbody", "tfoot", "tr",
    "dl", "dt", "dd",
)

private val FORM_ELEMENT_ATTRS: Map<String, Array<String>> = mapOf(
    "form" to arrayOf("id", "action", "method", "enctype", "name", "target"),
    "input" to arrayOf("id", "type", "name", "value", "placeholder", "required",
        "checked", "disabled", "readonly", "min", "max", "step", "pattern", "list"),
    "textarea" to arrayOf("id", "name", "placeholder", "required", "disabled",
        "readonly", "rows", "cols"),
    "select" to arrayOf("id", "name", "required", "multiple", "disabled"),
    "option" to arrayOf("value", "selected", "disabled", "label"),
    "optgroup" to arrayOf("label", "disabled"),
    "button" to arrayOf("id", "type", "name", "value", "disabled"),
    "label" to arrayOf("id", "for"),
    "fieldset" to arrayOf("id", "name", "disabled"),
    "legend" to arrayOf("id"),
    "datalist" to arrayOf("id"),
    "output" to arrayOf("id", "name", "for"),
    "progress" to arrayOf("value", "max"),
    "meter" to arrayOf("value", "min", "max", "low", "high", "optimum"),
)

// ARIA attributes worth keeping for an LLM-driven agent: the accessible name
// (`aria-label`) plus actionable state / affordances. Merged into every
// preserved element's attribute set by [preserveAttrs]. Deliberately excluded:
// id-reference attributes (`aria-labelledby`/`describedby`/`controls` — opaque
// without resolving the referenced element, a DOM-walk concern), live-region
// plumbing, and positional/structural aria (redundant with the rendered
// structure). `aria-hidden` is handled separately as a subtree drop.
private val ARIA_KEEP = arrayOf(
    "aria-label", "aria-expanded", "aria-haspopup", "aria-current",
    "aria-checked", "aria-selected", "aria-pressed", "aria-disabled",
)

// Technical `<meta name>` values that carry no content signal for an LLM and
// only inflate the frontmatter: rendering hints, crawler / verification
// directives, and platform tile metadata. Dropped from the extracted metadata.
// A denylist (rather than an allowlist) keeps unknown-but-possibly-useful names
// — `description`, `keywords`, `author`, `og:*`, `article:*`, … — by default.
private fun isNoiseMetaName(name: String): Boolean {
    val n = name.lowercase()
    return n in NOISE_META_NAMES
            || NOISE_META_PREFIXES.any { n.startsWith(it) }
            || n.endsWith("-verification")
            || n.endsWith("-verify")
            || n.startsWith("verify-")
}

private val NOISE_META_NAMES = setOf(
    "viewport", "referrer", "generator", "theme-color", "color-scheme",
    "format-detection", "tdm-reservation", "robots", "googlebot", "bingbot",
    "rating", "google", "csrf-token", "csrf-param", "build", "revision",
    "mobile-web-app-capable",
)

private val NOISE_META_PREFIXES = setOf(
    "msapplication-", "apple-", "mobile-web-app-",
)

private fun renderYamlFrontmatter(
    metadata: Map<String, String>
): String = buildString {
    for ((key, value) in metadata) {
        yamlScalar(key)
        +": "
        yamlScalar(value)
        +'\n'
    }
}

// YAML 1.2 reserved boolean / null literals. Must be quoted to keep them as
// strings instead of decoding to `true`/`false`/`null`.
private val YAML_RESERVED_LITERALS = setOf(
    "true", "True", "TRUE", "false", "False", "FALSE",
    "yes", "Yes", "YES", "no", "No", "NO",
    "on", "On", "ON", "off", "Off", "OFF",
    "null", "Null", "NULL", "~"
)

// Plain-scalar indicator characters: starting with any of these forces
// double-quoted output (see YAML 1.2 §6.4 / §6.6).
private const val YAML_INDICATORS = "-?:,[]{}#&*!|>'\"%@`"

private fun Appendable.yamlScalar(s: String) {
    if (s.isEmpty()) +"\"\""
    else if (s in YAML_RESERVED_LITERALS) yamlQuoted(s)
    else {
        val first = s.first()
        val last = s.last()
        val needsQuoting = first.isWhitespace()
                || last.isWhitespace()
                || first in YAML_INDICATORS
                || s.any { c ->
            c == ':' || c == '#' || c == '"' || c == '\\'
                    || c == '\n' || c == '\r' || c == '\t'
        }
        if (needsQuoting) yamlQuoted(s) else +s
    }
}

private fun Appendable.yamlQuoted(s: String) {
    +'"'
    for (c in s) when (c) {
        '\\' -> +"\\\\"
        '"' -> +"\\\""
        '\n' -> +"\\n"
        '\r' -> +"\\r"
        '\t' -> +"\\t"
        else -> +c
    }
    +'"'
}
