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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Gives an `<img>` whose `src` is a lazy-loading placeholder the real image
 * URL its page declares elsewhere, **before** [simplifyHtml] keeps only `src`
 * and discards every attribute that could have told them apart.
 *
 * Lazy-loading libraries keep a `data:` URI — typically the 1×1 transparent
 * GIF — in `src` (or leave `src` out) until the image scrolls into view, and a
 * dump is taken without scrolling, so nearly every image below the fold would
 * otherwise render as `![alt](data:image/gif;base64,…)`: ~80 bytes of noise
 * and no image. Only an `http:` / `https:` or a relative URL counts as an
 * image URL: a `src` that is missing, blank, a `data:` or `blob:` URI (the
 * latter meaningless outside the captured page), `about:blank`, an empty host
 * (`//:0`) or in any other scheme is a placeholder, replaced by the first of:
 *
 * 1. `data-src`, `data-lazy-src`, `data-original` — the attributes the common
 *    libraries swap into `src` on load;
 * 2. the best candidate of the image's `data-srcset`, then its `srcset` — the
 *    lazy attribute first, since a placeholder image's `srcset` may itself hold
 *    the placeholder;
 * 3. the best candidate of a `<source>` (`data-srcset`, then `srcset`) of an
 *    enclosing `<picture>`: the first one without a `media` condition — the
 *    picture's default, where a `media` source is a variant (an animation for
 *    users who allow motion, a dark-mode or mobile crop) — or else the first
 *    conditional one. A `<source>` whose `type` the capturing browser (Chrome)
 *    cannot decode is skipped, as the browser skips it.
 *
 * A lazy attribute of step 1 wins over a `src` that **is** an image URL too:
 * some libraries (WordPress a3-lazy-load, jQuery lazyload) keep a real
 * placeholder image in `src` (`lazy_placeholder.gif`, `grey.gif`), which no
 * rule could tell from the image by its URL, and the attribute names what the
 * library swaps in. A `srcset` never replaces such a `src`: beside a real
 * `src` it is a responsive image, not a lazy one.
 *
 * The "best" `srcset` candidate is the widest (`w` descriptor), or, without
 * width descriptors, the densest (`x`, a bare URL counting as `1x`) — the one
 * that carries the most of the image, rather than the one a particular viewport
 * would pick. A candidate that is no image URL (above) is never chosen, nor
 * one whose descriptors the HTML standard rejects.
 *
 * Under [SvgMode.RESOLVE] (the LLM-facing default), a placeholder `src`
 * nothing replaces is **dropped**: an inline image's base64 payload is noise to a
 * model, real icon or placeholder alike, and the `alt` text is what remains
 * (`![alt]()`). An image left with neither a usable `src` nor an `alt` is
 * dropped whole — `![]()` says nothing. Inside a link or button that would
 * leave the control empty, so [labelActionableElements], which runs first,
 * counts such an image as a vanishing graphic and labels the control for it.
 * Under [SvgMode.PRESERVE] the output is for a reader, who sees an inline image
 * whatever its format, so every `data:` image is kept — `image/png` as well as
 * `image/svg+xml`, a distinction no reader would draw — while a placeholder
 * with a real candidate is still resolved (and a `src` nothing replaces kept
 * as it is).
 *
 * An image whose `src` is an image URL is otherwise left untouched — native
 * `loading="lazy"` keeps the real URL in `src`.
 *
 * The capture deliberately does not resolve this from the live `currentSrc`:
 * for a placeholder the library has not swapped yet, Blink's `currentSrc` *is*
 * the `data:` placeholder (it loaded), so it carries nothing the attributes
 * above do not.
 *
 * Streaming, with no buffering: only the `<source>` candidates of each open
 * `<picture>` are remembered, which precede its `<img>` in the content model.
 * Elements are matched by name regardless of [SemanticEvent.Mark.isTagged].
 * Run it in the HTML→Markdown pipeline after [labelActionableElements] and
 * before [simplifyHtml].
 */
public fun Flow<SemanticEvent>.resolveLazyImages(
    svgMode: SvgMode = SvgMode.RESOLVE,
): Flow<SemanticEvent> = flow {
    val resolver = LazyImageResolver(dropPlaceholders = svgMode == SvgMode.RESOLVE)
    var droppedImage = false
    collect { event ->
        val resolved = resolver.resolve(event)
        // a dropped image is void: its unmark follows its mark directly
        val skip = droppedImage && event is Unmark && event.name == "img"
        droppedImage = resolved == null
        if (resolved != null && !skip) emit(resolved)
    }
}

/**
 * The stateful core of [resolveLazyImages], shared with [labelActionableElements]
 * so that the images one drops are exactly the ones the other labels a control
 * for. Feed it every event of a stream (or of a buffered subtree) in order.
 */
internal class LazyImageResolver(private val dropPlaceholders: Boolean = true) {

    // One frame per open <picture>: the mark depth it sits at, and the image
    // URLs its first usable unconditional and conditional <source> offer.
    // Scoped by depth, not by name, so an unclosed <picture> ends with its
    // parent instead of leaking its source into every later image.
    private class Picture(val depth: Int) {
        var defaultSource: String? = null
        var conditionalSource: String? = null
        val source: String? get() = defaultSource ?: conditionalSource
    }

    private val pictures = ArrayDeque<Picture>()

    private var depth = 0

    /**
     * Returns [event] with its image source resolved, or `null` for an `img`
     * mark that is to be dropped whole (its unmark is the caller's to skip).
     */
    fun resolve(event: SemanticEvent): SemanticEvent? = when (event) {
        is Mark -> {
            depth++
            when (event.name) {
                "picture" -> {
                    pictures.addLast(Picture(depth))
                    event
                }
                "source" -> {
                    val picture = pictures.lastOrNull()
                    if (picture != null && picture.defaultSource == null && event.hasDecodableType()) {
                        val candidate = event.bestSrcsetCandidate()
                        if (event.isUnconditional()) {
                            picture.defaultSource = candidate
                        } else if (picture.conditionalSource == null) {
                            picture.conditionalSource = candidate
                        }
                    }
                    event
                }
                "img" -> event.withResolvedSrc(pictures.lastOrNull()?.source)
                else -> event
            }
        }
        is Unmark -> {
            while (pictures.isNotEmpty() && pictures.last().depth >= depth) pictures.removeLast()
            depth--
            event
        }
        is Text -> event
    }

    private fun Mark.withResolvedSrc(pictureSource: String?): Mark? {
        val src = this["src"]
        val lazySrc = LAZY_SRC_ATTRIBUTES.firstNotNullOfOrNull { name ->
            this[name]?.trim()?.takeIf { it.isImageUrl() }
        }
        if (src != null && src.isImageUrl()) {
            return if (lazySrc == null || lazySrc == src) this
            else copy(attributes = attributes + ("src" to lazySrc))
        }
        val resolved = lazySrc ?: bestSrcsetCandidate() ?: pictureSource
        if (resolved != null) return copy(attributes = attributes + ("src" to resolved))
        if (!dropPlaceholders) return this
        val alt = this["alt"]
        return when {
            alt == null || alt.isBlank() -> null
            src != null -> copy(attributes = attributes - "src")
            else -> this
        }
    }

}

private val LAZY_SRC_ATTRIBUTES = listOf("data-src", "data-lazy-src", "data-original")

private val SRCSET_ATTRIBUTES = listOf("data-srcset", "srcset")

private fun Mark.bestSrcsetCandidate(): String? =
    SRCSET_ATTRIBUTES.firstNotNullOfOrNull { name -> this[name]?.bestSrcsetCandidate() }

/**
 * Whether this `<source>` names no `media` condition — or one that always
 * holds (`all`, `screen`) — and so is its picture's default.
 */
private fun Mark.isUnconditional(): Boolean =
    this["media"]?.trim()?.lowercase().let { it == null || it in UNCONDITIONAL_MEDIA }

private val UNCONDITIONAL_MEDIA = setOf("", "all", "screen")

/**
 * Whether the capturing browser can decode the image type this `<source>`
 * declares — Chrome passes over one it cannot, so its URL is not the picture's
 * image. No `type` declares nothing and is decodable.
 */
private fun Mark.hasDecodableType(): Boolean {
    val type = this["type"] ?: return true
    val essence = type.substringBefore(';').trim().lowercase()
    return essence.isEmpty() || essence in DECODABLE_IMAGE_TYPES
}

// Blink's supported image MIME types (`mime_util`), plus SVG.
private val DECODABLE_IMAGE_TYPES = setOf(
    "image/apng", "image/avif", "image/bmp", "image/gif", "image/jpeg",
    "image/jpg", "image/pjpeg", "image/png", "image/svg+xml",
    "image/vnd.microsoft.icon", "image/webp", "image/x-icon", "image/x-png",
    "image/x-xbitmap",
)

/** Whether this attribute value is a `data:` URI. */
internal fun String.isDataUrl(): Boolean = trimStart().startsWith("data:", ignoreCase = true)

/**
 * Whether this is a URL a reader can fetch an image from: a relative URL, or
 * an absolute `http:` / `https:` one — not blank, not `data:`, `blob:`,
 * `about:`, `javascript:` or any other scheme, and not a protocol-relative URL
 * with an empty host (`//:0`, a placeholder that loads nothing).
 */
private fun String.isImageUrl(): Boolean {
    val url = trim()
    if (url.isEmpty()) return false
    val scheme = url.urlScheme()
    if (scheme != null) return scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)
    if (url.startsWith("//")) {
        val authority = url.substring(2).takeWhile { it != '/' && it != '?' && it != '#' }
        val host = authority.substringAfterLast('@').substringBefore(':')
        return host.isNotEmpty()
    }
    return true
}

/**
 * The scheme of this URL, or `null` for a relative one: a letter followed by
 * letters, digits, `+`, `-` or `.`, ending at a `:` that comes before any `/`,
 * `?` or `#`.
 */
private fun String.urlScheme(): String? {
    val colon = indexOf(':')
    if (colon <= 0) return null
    val scheme = substring(0, colon)
    if (!scheme[0].isAsciiLetter()) return null
    if (!scheme.all { it.isAsciiLetter() || it in '0'..'9' || it == '+' || it == '-' || it == '.' }) return null
    return scheme
}

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

private class SrcsetCandidate(val url: String, val width: Int?, val density: Double)

/**
 * The best candidate of a `srcset` value (see [resolveLazyImages]), or `null`
 * when it holds no usable one. Parses candidates as the HTML standard does: a
 * URL is a run of non-whitespace (so it may contain commas), a trailing comma
 * ends it, and its descriptors run to the next comma outside parentheses.
 */
private fun String.bestSrcsetCandidate(): String? {
    val candidates = mutableListOf<SrcsetCandidate>()
    var position = 0
    while (true) {
        while (position < length && (this[position].isWhitespace() || this[position] == ',')) position++
        if (position >= length) break
        val urlStart = position
        while (position < length && !this[position].isWhitespace()) position++
        var url = substring(urlStart, position)
        var descriptors = ""
        if (url.endsWith(',')) {
            url = url.trimEnd(',')
        } else {
            val descriptorStart = position
            var inParentheses = false
            while (position < length) {
                val char = this[position]
                if (char == '(') inParentheses = true
                else if (char == ')') inParentheses = false
                else if (char == ',' && !inParentheses) break
                position++
            }
            descriptors = substring(descriptorStart, position)
        }
        if (!url.isImageUrl()) continue
        candidates += parseCandidate(url, descriptors) ?: continue
    }
    val widest = candidates.filter { it.width != null }.maxByOrNull { it.width!! }
    return (widest ?: candidates.maxByOrNull { it.density })?.url
}

/**
 * A candidate from its URL and descriptors, or `null` when the HTML standard
 * rejects them: a width that is not a positive integer of ASCII digits, a
 * density that is not a positive finite number, or more than one of either.
 * An unknown descriptor (`h`, future ones) is ignored rather than rejected.
 */
private fun parseCandidate(url: String, descriptors: String): SrcsetCandidate? {
    var width: Int? = null
    var density: Double? = null
    for (token in descriptors.split(' ', '\t', '\n', '\r', '\u000C')) {
        if (token.isEmpty()) continue
        val value = token.dropLast(1)
        when (token.last()) {
            'w' -> {
                if (width != null || density != null) return null
                if (value.isEmpty() || !value.all { it in '0'..'9' }) return null
                width = value.toIntOrNull()?.takeIf { it > 0 } ?: return null
            }
            'x' -> {
                if (width != null || density != null) return null
                density = value.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
            }
        }
    }
    return SrcsetCandidate(url, width, density ?: 1.0)
}
