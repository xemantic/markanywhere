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
import com.xemantic.markanywhere.transform.MatcherScope
import com.xemantic.markanywhere.transform.transform
import kotlinx.coroutines.flow.Flow

/**
 * Simplifies an HTML-derived semantic event stream by keeping only the
 * tags that carry semantic meaning and discarding presentational noise.
 *
 * Each surviving mark carries **how it must render** in its `isTagged` flag,
 * decided here rather than by a name lookup downstream (see [preserve]):
 *
 * - a name the Markdown renderer has syntax for ([MARKDOWN_NATIVE_TAGS]: `p`,
 *   `h1`–`h6`, `ul`, `ol`, `li`, `em`, `strong`, `a`, `img`, the table family,
 *   …) is emitted **untagged**, so it renders as Markdown — `*…*`, `- `, `## `;
 * - every other preserved element (`section`, `nav`, `dialog`, `form`,
 *   `button`, `dl`/`dt`/`dd`, `ruby`, `b`/`i`/`u`/`cite`, …) is emitted
 *   **tagged**, so it renders as the literal XML wrapper it is.
 *
 * The output is therefore self-describing: a consumer reads `isTagged` instead
 * of replicating the renderer's name map. `renderMarkdown()` keeps a fallback
 * (an untagged mark it has no syntax for still renders as a raw tag), so a
 * hand-built stream that does not set the flag behaves as before.
 *
 * Behaviour by tag class:
 *
 * - **Drop (any element)**: `aria-hidden="true"` — the element is hidden from
 *   the accessibility tree (decorative / duplicate / off-screen), so it and its
 *   whole subtree are discarded, like `display:none`. Checked first, before the
 *   per-tag rules below.
 * - **Drop**: `script`, `style`, `link`, `noscript`, `template`, `canvas` and
 *   `svg` — the wrapper and all of its content are discarded (see
 *   [DROPPED_TAGS]).
 * - **Keep the source, drop the fallbacks**: `iframe`, `video`, `audio`,
 *   `object`, `embed` survive as elements carrying what they embed (see
 *   [EMBEDDED_CONTENT_ATTRS]); their children are fallback content for a
 *   renderer we have no equivalent for and are discarded. The one exception
 *   is the **document a same-origin frame embeds**, which a capture nests
 *   inside its `iframe` / `object` as an `html` element: its body is content
 *   of the page (a consent dialog lives there) and is simplified in place,
 *   while its `head` is the frame's metadata, not the page's, and is dropped.
 * - **Unwrap**: `body`, `div`, `span`, `font`, `center`, `tt` — the
 *   wrapper is removed, children flow through unchanged.
 * - **Unwrap (catch-all)**: any element matched by none of the rules — custom
 *   elements / web components, `picture`, `slot`, and whatever HTML gains next.
 *   The wrapper is removed and its whole subtree survives; only [DROPPED_TAGS]
 *   loses its content.
 * - **Wrap**: `menu` keeps its tag (a toolbar of commands is not a bullet list,
 *   and Markdown cannot say so) with a synthetic untagged `ul` nested inside to
 *   carry the items, since an `li` needs a list to take its bullet from.
 * - **Preserve with `id` only**:
 *   - structural: `article`, `aside`, `footer`, `header`, `main`, `nav`,
 *     `section`, `search`, `figure`, `figcaption`, `details`, `summary`,
 *     `address`, `hgroup`
 *   - `dialog` — a closed one is `display:none` per the UA stylesheet and never
 *     reaches this operator, so a surviving dialog is on screen (usually a
 *     modal blocking the rest of the page), which is content-level information
 *   - block content: `p`, `blockquote`, `pre`, `hr`, `br`, `h1`–`h6`,
 *     `ul`, `li`, `dl`, `dt`, `dd`, table family (`table`, `thead`,
 *     `tbody`, `tfoot`, `tr`, `caption`)
 * - **Preserve, drop all attributes**: inline emphasis — `em`, `strong`,
 *   `del`, `mark`, `sub`, `sup`, `u`, `s`, `i`, `b`, `small`, `cite`,
 *   `abbr`, `kbd`, `samp`, `var`, `time`, `q`, `dfn`, `ins` — and the ruby
 *   annotation group ([RUBY_TAGS]), whose annotation is parallel to its base
 *   text and so cannot be flattened into it
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
 *   `aria-selected`, `aria-pressed`, `aria-disabled`, `aria-modal` (see
 *   [ARIA_KEEP]). These
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
 * lookup — one matcher per family, not one per tag. A `match("*")` unwrap
 * closes the list, so an element no earlier rule claims keeps its content
 * instead of taking it down with it.
 *
 * A plain stream operator, so it composes with the rest of the HTML pipeline
 * by chaining — e.g. `flow.applyAccessibility().simplifyHtml()`. The rule
 * block is rebuilt on **every collection**, which reinitialises the
 * `metadata` / `titleText` state captured below on each run, so the
 * same flow can be collected repeatedly without state leaking between
 * collections.
 *
 * @param keepAttributes application-specific attribute names to preserve on
 *   every element that survives simplification (see the "Caller keep-set"
 *   behaviour above). Empty by default.
 * @param svgMode what to do with an inline `<svg>`. [SvgMode.RESOLVE] (the
 *   default) drops the subtree, having let `resolveInlineGraphics` lift any
 *   accessible name out of it first; [SvgMode.PRESERVE] keeps it tagged with
 *   its own attributes intact, for a consumer that renders graphics rather
 *   than reading them — minus a `script`, an `aria-hidden` part, and the
 *   capture's annotations, which obey the same rules as everywhere else.
 */
public fun Flow<SemanticEvent>.simplifyHtml(
    keepAttributes: Set<String> = emptySet(),
    svgMode: SvgMode = SvgMode.RESOLVE,
): Flow<SemanticEvent> = transform {

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

    // --- vector graphics passthrough (before the DROPPED_TAGS group) ----

    // In PRESERVE the whole `<svg>` subtree is kept tagged, for a consumer
    // rendering the graphics rather than reading them as text.
    //
    // A rule is not enough on its own: the catch-all would *unwrap* every
    // `<path>` / `<defs>` / `<linearGradient>` below it and spill the vector
    // data as text, so the subtree descends in its own mode whose wildcard
    // keeps each element instead. The element's own attributes pass through —
    // `viewBox` and `d` are the content here, not presentational noise — but
    // the capture's annotations do not, unless the caller asked for one (the
    // pipeline asks for the ref it will encode): a mode-scoped wildcard
    // shadows every default-mode rule, so the ref / annotation contract, the
    // `aria-hidden` drop and the `script` drop are repeated here.
    //
    // Not `passthrough(mode = "svg")` for two reasons: it keeps attributes
    // verbatim, and it keeps each mark's `isTagged` — while the SVG names that
    // collide with Markdown-native ones (`a`, `title`) must render as literal
    // tags, so an untagged hand-built svg is re-tagged on purpose.
    if (svgMode == SvgMode.PRESERVE) {
        fun svgAttributes(event: SemanticEvent.Mark): Map<String, String> =
            event.attributes.filterKeys { it !in CAPTURE_ANNOTATIONS || it in keepAttributes }
        suspend fun MatcherScope.keepSvgElement(event: SemanticEvent.Mark) {
            tag(event.name, svgAttributes(event)) { children(mode = "svg") }
        }
        match("svg") { event -> keepSvgElement(event) }
        match({ this["aria-hidden"]?.equals("true", ignoreCase = true) == true }, mode = "svg") { /* drop */ }
        match("script", mode = "svg") { /* drop */ }
        match("*", mode = "svg") { event -> keepSvgElement(event) }
        matchText(mode = "svg") { +it }
    }

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

    // An unrecognised head child (say an `<x-config>` payload) is still head
    // content: unwrap it *in head mode*, so a tag nested inside it — a `<p>`,
    // in a hand-built or `parse()`-sourced stream (Chrome's parser relocates
    // unknown elements out of `<head>`) — meets these head rules and not the
    // body ones, which would emit it as content. The default catch-all at the
    // bottom would reset the mode. An expression, not `match("*", mode =
    // "head")`: a mode-scoped wildcard shadows every default-mode rule, and
    // the `title` / `meta` rules above must keep resolving in here.
    match({ name !in HEAD_METADATA_TAGS }, mode = "head") { children(mode = "head") }

    // <title> text (including text inside nested inline marks) is captured
    // into metadata rather than emitted; the wildcard unwraps any nested
    // mark while keeping its text flowing to the capture above.
    matchText(mode = "titleText") { titleText.append(it) }
    match("*", mode = "titleText") { children(mode = "titleText") }

    // --- tags with custom attribute whitelists (explicit per-tag) -------

    match("a") { event ->
        preserve("a", preserveAttrs(event, "href", "title", "id")) { children() }
    }

    match("img") { event ->
        preserve("img", preserveAttrs(event, "src", "alt", "title", "id")) { /* void */ }
    }

    match("code") { event ->
        preserve("code", preserveAttrs(event, "class", "id")) { children() }
    }

    match("ol") { event ->
        preserve("ol", preserveAttrs(event, "start", "id")) { children() }
    }

    // `<menu>` is a toolbar of commands, not a bullet list — a distinction
    // Markdown cannot express, so the tag is kept (it has no Markdown syntax,
    // hence `preserve` tags it) and a `ul` is nested inside to carry the items.
    // The nested list is what makes them render as `- ` items: `li` is a
    // Markdown-native mark and the renderer takes an item's bullet from the
    // enclosing list frame, which only `ul`/`ol` opens. Renaming `menu` to `ul`
    // would render identically but throw the semantics away; emitting the bare
    // `li`s instead would fall back on the renderer's orphan-item recovery,
    // which yields a loose list. The whole child run is wrapped — a `<menu>`
    // whose content model is anything but `li` is malformed HTML anyway.
    match("menu") { event ->
        preserve("menu", preserveAttrs(event, "id")) {
            preserve("ul") { children() }
        }
    }

    match("th") { event ->
        preserve("th", preserveAttrs(event, "align", "colspan", "rowspan", "scope", "headers", "id")) {
            children()
        }
    }

    match("td") { event ->
        preserve("td", preserveAttrs(event, "align", "colspan", "rowspan", "scope", "headers", "id")) {
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
    match({ name !in DROPPED_TAGS && this["role"]?.lowercase() in PRESENTATION_ROLES }) { event ->
        val kept = extraKept(event)
        if (kept.isEmpty()) children()
        else preserve(event.name, kept) { children() }
    }

    // `role="heading"` + `aria-level` → `h1`–`h6` (ARIA's default level is 2).
    match({ name in UNWRAPPED_TAGS && this["role"]?.lowercase() == "heading" }) { event ->
        val level = event["aria-level"]?.toIntOrNull()?.coerceIn(1, 6) ?: 2
        preserve("h$level", preserveAttrs(event, "id")) { children() }
    }

    // `role="img"` → image; its accessible name becomes the alt text.
    match({ name in UNWRAPPED_TAGS && this["role"]?.lowercase() == "img" }) { event ->
        val attrs = preserveAttrs(event, "id").toMutableMap()
        event["aria-label"]?.let { attrs["alt"] = it }
        preserve("img", attrs) { /* void — descriptive children collapse to the name */ }
    }

    // `role="separator"` → thematic break (void).
    match({ name in UNWRAPPED_TAGS && this["role"]?.lowercase() == "separator" }) { event ->
        preserve("hr", preserveAttrs(event, "id")) { /* void */ }
    }

    // Remaining promotable roles are straight renames to the semantic tag,
    // keeping `id` (+ ARIA name/state via preserveAttrs); children flow through.
    match({ name in UNWRAPPED_TAGS && this["role"]?.lowercase() in ROLE_TO_TAG }) { event ->
        val target = ROLE_TO_TAG.getValue(event["role"]!!.lowercase())
        preserve(target, preserveAttrs(event, "id")) { children() }
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
        else preserve(event.name, kept) { children() }
    }

    match({ name in PRESERVE_WITH_ID_TAGS }) { event ->
        preserve(event.name, preserveAttrs(event, "id")) { children() }
    }

    match({ name in INLINE_FORMATTING_TAGS }) { event ->
        preserve(event.name, extraKept(event)) { children() }
    }

    // Ruby annotations are preserved as inline raw HTML rather than unwrapped:
    // the annotation is *parallel* to its base text, not sequential, so
    // flattening glues the two together (`漢字` + `kanji` → `漢字kanji`) and no
    // separator can repair it. Keeping the group intact is also the only option
    // that does not decide on the consumer's behalf whether pronunciation is
    // content. Markdown carries them as inline HTML and the parser recognises
    // all three as inline elements, so they round-trip.
    match({ name in RUBY_TAGS }) { event ->
        preserve(event.name, extraKept(event)) { children() }
    }

    // Embedded content: keep the element and the resource it points at, drop
    // the fallbacks. Its markup children are *fallback* content for a renderer
    // we have no equivalent for (HTML §4.8) — but the element itself names
    // something a reader may want to follow: a video to fetch, an embedded
    // PDF, or the cross-origin frame a consent dialog lives in.
    // `applyAccessibility` has already removed the hidden analytics and SSO
    // frames upstream, so what reaches here is what a person would actually
    // see on the page.
    //
    // A frame is different: a capture nests the document a *same-origin*
    // frame embeds inside its element (the way a screen reader reads it — a
    // captured stream carries no fallback markup at all), and that document's
    // body is page content. So `iframe` / `frame` / `object` descend in
    // "frame" mode,
    // where the frame's `html` unwraps, its `head` (the frame's title and
    // metadata, not the page's) is dropped, its `body` returns to the default
    // rules, and anything else — fallback markup, in a hand-built stream — is
    // discarded as before.
    match({ name in EMBEDDED_CONTENT_ATTRS }) { event ->
        val allowed = EMBEDDED_CONTENT_ATTRS.getValue(event.name)
        preserve(event.name, preserveAttrs(event, *allowed)) {
            if (event.name in FRAME_TAGS) children(mode = "frame")
        }
    }
    match("html", mode = "frame") { children(mode = "frame") }
    match("head", mode = "frame") { /* the frame's metadata, not the page's */ }
    match("body", mode = "frame") { children() }
    match("*", mode = "frame") { /* fallback markup */ }
    matchText(mode = "frame") { /* structural whitespace or fallback text */ }

    match({ name in FORM_ELEMENT_ATTRS }) { event ->
        val allowed = FORM_ELEMENT_ATTRS.getValue(event.name)
        preserve(event.name, preserveAttrs(event, *allowed)) { children() }
    }

    match("icon") {
        children()
    }

    // --- catch-all (registered last, so every rule above wins) ----------

    // An element in none of the sets above is *unwrapped*, not dropped: the
    // transform framework skips the whole subtree of a mark that has no
    // matcher, so without this rule an unrecognised element would swallow its
    // text, headings, images and controls along with itself. The open set of
    // custom elements / web components (`acf-button-standard`, `g-snackbar`,
    // `svelte-css-wrapper`, …) can't be enumerated by an allowlist, and those
    // wrappers — frequently `display: contents` — are exactly what unwrapping
    // is for. Elements whose content genuinely carries no meaning must be
    // listed in [DROPPED_TAGS] instead.
    match("*") { children() }

    // Body content text passes through unchanged.
    matchText { +it }
}

/**
 * Emits a preserved element, letting the mark itself carry how it must render.
 *
 * A name the Markdown renderer has syntax for stays **untagged**, so it renders
 * as Markdown (`em` → `*…*`, `ul` → `- `, `h2` → `## `); every other name is
 * **tagged**, so it renders as the literal XML wrapper it is. Deciding here —
 * where the HTML knowledge lives — is what makes the output stream
 * self-describing: a consumer reads `isTagged` instead of replicating the
 * renderer's name map, and the two can no longer drift apart silently.
 *
 * Every preserved element goes through this helper, so no call site can forget
 * the decision.
 */
private suspend fun MatcherScope.preserve(
    name: String,
    attributes: Map<String, String> = emptyMap(),
    block: suspend MatcherScope.() -> Unit,
) {
    if (name in MARKDOWN_NATIVE_TAGS) name(attributes, block)
    else tag(name, attributes, block)
}

/**
 * Mark names the Markdown renderer has native syntax for, so [simplifyHtml]
 * leaves them untagged.
 *
 * This MUST mirror `MARKDOWN_NATIVE_MARK_NAMES` in markanywhere-render's
 * `MarkdownRendering.kt` — the two modules are siblings with no shared home for
 * the set, the same arrangement as [LINK_BLOCK_CONTENT_TAGS]. `SimplifyHtmlTest`
 * asserts the two are equal, so a drift fails CI instead of silently emitting a
 * `<ul>` tag where a `- ` list was meant.
 */
internal val MARKDOWN_NATIVE_TAGS: Set<String> = setOf(
    "frontmatter",
    "h1", "h2", "h3", "h4", "h5", "h6",
    "p", "blockquote", "hr", "br",
    "ul", "ol", "li",
    "pre", "code",
    "strong", "em", "del", "mark", "sup",
    "a", "img",
    "table", "thead", "tbody", "tr", "th", "td", "caption",
)

// Elements dropped together with their whole subtree. Everything not listed
// here is either matched by a rule above or unwrapped by the catch-all, so
// this set is what keeps the catch-all from spilling noise into the output.
private val DROPPED_TAGS = setOf(
    // no rendered content
    "script", "style", "link", "noscript",
    // inert content, only instantiated by script
    "template",
    // Pixels drawn by script: unlike the other embedded content there is no
    // source to point a reader at, and the fallback children are boilerplate
    // far more often than the accessible representation the spec intends.
    "canvas",
    // Vector graphics. Dropping the root is enough — the whole `<path>` /
    // `<defs>` / `<symbol>` family below it is skipped with it, so the SVG
    // element names (several of which collide with HTML ones: `title`, `a`,
    // `image`) need no listing. An `<svg>` carrying an accessible name is
    // already turned into an `<img>` by `resolveInlineGraphics`, upstream of
    // this operator in `transformHtmlToMarkdown`.
    "svg",
)

// The ruby annotation group (HTML §4.5.10-12): base text plus its annotation
// (`rt`) and the parenthesis fallback (`rp`). Preserved as inline raw HTML —
// see the matcher for why flattening is not an option.
private val RUBY_TAGS = setOf("ruby", "rt", "rp")

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
    "search" to "search",
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
    "search",
    // A closed `<dialog>` is `display:none` per the UA stylesheet, so one that
    // reaches this operator is on screen — usually a modal blocking the rest of
    // the page. That is content-level information (and, for an agent, the first
    // thing to act on), so the tag is kept rather than flattened into a stray
    // paragraph.
    "dialog",
    // Markdown-equivalent blocks
    "p", "blockquote", "pre", "hr", "br",
    "h1", "h2", "h3", "h4", "h5", "h6",
    "ul", "li",
    "table", "thead", "tbody", "tfoot", "tr",
    // A data table's accessible name. The renderer has no GFM syntax for it and
    // degrades it to the block before the table, but that decision needs the
    // mark to survive this far.
    "caption",
    "dl", "dt", "dd",
)

// The head children with a rule of their own, which the head-mode unwrap of an
// unrecognised head element must not shadow.
private val HEAD_METADATA_TAGS = setOf("title", "meta")

// The embedded-content elements that can embed a whole document, which a
// capture nests inside them (see the "frame" mode) — the same three the JS
// walker (`frameDocumentElement`) descends into, the legacy frameset `<frame>`
// included.
private val FRAME_TAGS = setOf("iframe", "frame", "object")

// The capture's own bookkeeping (see [AccessibilityAnnotations]): stripped from
// a preserved svg element unless the caller asked for one through
// `keepAttributes`, the way every other preserved element only keeps them
// through `preserveAttrs`.
private val CAPTURE_ANNOTATIONS = AccessibilityAnnotations.ALL + AccessibilityAnnotations.REF

// Embedded content elements and the attributes naming what they embed. The
// dimensions (`width`/`height`) are presentational and dropped; `title` /
// `aria-label` arrive via preserveAttrs' ARIA keep-set.
private val EMBEDDED_CONTENT_ATTRS: Map<String, Array<String>> = mapOf(
    // no `srcdoc`: a srcdoc frame is same-origin by definition, so a capture
    // already nests the document it describes inside the `iframe` and it is
    // rendered in place — the attribute would repeat the whole document as an
    // escaped HTML string on the tag
    "iframe" to arrayOf("id", "src", "name", "title"),
    "frame" to arrayOf("id", "src", "name", "title"),
    "video" to arrayOf("id", "src", "poster", "controls", "title"),
    "audio" to arrayOf("id", "src", "controls", "title"),
    "object" to arrayOf("id", "data", "type", "name", "title"),
    "embed" to arrayOf("id", "src", "type", "title"),
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
    // Separates a blocking modal from an ordinary in-page dialog — the one
    // state that changes what a reader (or an agent) should do about it.
    "aria-modal",
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
