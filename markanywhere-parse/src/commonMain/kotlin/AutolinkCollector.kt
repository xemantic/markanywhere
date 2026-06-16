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

package com.xemantic.markanywhere.parse

import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.parse.AutolinkCollector.Companion.SUPPRESS_NAMES
import kotlinx.coroutines.flow.FlowCollector

/**
 * GFM §6.9 extended autolink detection. Wraps a downstream
 * [FlowCollector] and re-tokenises the [SemanticEvent.Text] events flowing
 * through "regular inline content" — replacing word-shaped runs that match
 * an extended autolink (www-prefix, scheme `http(s)?:` / `ftp:` /
 * `mailto:` / `xmpp:`, or a bare email address) with `<a href="…">…</a>`
 * around the matched text.
 *
 * **Buffering model**: text events are held until the next non-text
 * event (a `mark`/`unmark` or end-of-flow). At that point the held text is
 * scanned for autolinks and either replayed unchanged (no match) or with
 * `<a>` marks woven in (one or more matches). This is a conscious
 * streaming trade-off: the entire run of text inside e.g. a `<p>` is
 * delayed until the paragraph closes. The alternative — splitting the
 * stream at every whitespace boundary — would preserve incremental
 * emission but reshape every text event in the parser, which the existing
 * incremental tests already verify against the unbuffered shape.
 *
 * **Suppression**: detection is disabled inside marks where Markdown
 * processing is inactive or where an explicit link already encloses the
 * text — `<code>`, `<pre>`, `<a>`, `<img>`, `<math>`, `<script>`, `<style>`.
 * While `suppressDepth > 0`, text events pass through immediately.
 */
internal class AutolinkCollector(
    private val downstream: FlowCollector<SemanticEvent>
) : FlowCollector<SemanticEvent> {

    /**
     * Text events buffered since the last non-text event (or since
     * suppression was last cleared). Concatenated at drain time for
     * autolink detection; replayed in original shape if no match found.
     */
    private val pendingEvents = mutableListOf<SemanticEvent.Text>()

    /**
     * Char immediately before [pendingEvents]'s first char (in seen-event
     * order). null = start-of-flow or just after a mark/unmark event,
     * which both count as a valid autolink pre-boundary.
     */
    private var charBeforeWord: Char? = null

    /**
     * Suppression depth: incremented on `mark` of a name in
     * [SUPPRESS_NAMES] and decremented on the matching `unmark`. While
     * > 0, text events pass through unchanged with no detection.
     */
    private var suppressDepth = 0

    /**
     * Suppression depth for HTML block 6/7 raw-text streaming (CommonMark
     * §4.6). Disallowed tags (§6.11) like `<style>` and `<script>` inside
     * such blocks are filtered by `tokenizeHtmlLine` and reach this collector
     * as plain text — no enclosing `mark`/`unmark` is emitted, so the
     * standard [suppressDepth] mechanism never activates. The parser
     * increments [htmlRawTextDepth] on entry to an opening-tag-rooted
     * HTML 6/7 frame's raw-text phase, and decrements on exit (root close,
     * transition to sub-parse on blank line, or finalize cleanup).
     */
    var htmlRawTextDepth: Int = 0

    override suspend fun emit(value: SemanticEvent) {
        when (value) {
            is Text -> {
                if (suppressDepth > 0 || htmlRawTextDepth > 0) {
                    drain()
                    charBeforeWord = null
                    downstream.emit(value)
                    return
                }
                pendingEvents += value
            }
            is Mark -> {
                drain()
                charBeforeWord = null
                if (value.name.lowercase() in SUPPRESS_NAMES) suppressDepth++
                downstream.emit(value)
            }
            is Unmark -> {
                drain()
                charBeforeWord = null
                if (value.name.lowercase() in SUPPRESS_NAMES && suppressDepth > 0) suppressDepth--
                downstream.emit(value)
            }
        }
    }

    /**
     * Drain any pending text. Must be called at end-of-flow (after the
     * parser finalizes) so the last buffered run is emitted.
     */
    suspend fun finalize() {
        drain()
    }

    private suspend fun drain() {
        if (pendingEvents.isEmpty()) return
        val events = pendingEvents.toList()
        pendingEvents.clear()

        // Build concatenated string and per-event offset table.
        val fullSb = StringBuilder()
        val starts = IntArray(events.size + 1)
        for ((idx, e) in events.withIndex()) {
            starts[idx] = fullSb.length
            fullSb.append(e.text)
        }
        starts[events.size] = fullSb.length
        val full = fullSb.toString()

        val spans = findAutolinkSpans(full, charBeforeWord)
        if (spans.isEmpty()) {
            for (e in events) downstream.emit(e)
            if (full.isNotEmpty()) charBeforeWord = full.last()
            return
        }

        var pos = 0
        for (span in spans) {
            emitRange(events, starts, pos, span.urlStart)
            downstream.emit(
                SemanticEvent.Mark(
                    name = "a",
                    attributes = mapOf("href" to span.href)
                )
            )
            downstream.emit(SemanticEvent.Text(span.linkText))
            downstream.emit(SemanticEvent.Unmark(name = "a"))
            pos = span.urlEnd
        }
        emitRange(events, starts, pos, full.length)
        if (full.isNotEmpty()) charBeforeWord = full.last()
    }

    /**
     * Emit the concatenated-buffer range `[from, to)` downstream, preserving
     * the original event shapes from [events] (located via [starts]): a fully
     * covered event is emitted as-is (re-using the event instance); a partial
     * cover emits a fresh `Text` with the substring.
     */
    private suspend fun emitRange(
        events: List<SemanticEvent.Text>,
        starts: IntArray,
        from: Int,
        to: Int
    ) {
        if (from >= to) return
        var idx = 0
        while (idx < events.size && starts[idx + 1] <= from) idx++
        var pos = from
        while (pos < to && idx < events.size) {
            val eventStart = starts[idx]
            val eventEnd = starts[idx + 1]
            val localFrom = pos - eventStart
            val localTo = minOf(to, eventEnd) - eventStart
            if (localFrom == 0 && localTo == events[idx].text.length) {
                downstream.emit(events[idx])
            } else {
                downstream.emit(
                    SemanticEvent.Text(
                        events[idx].text.substring(localFrom, localTo)
                    )
                )
            }
            pos = eventStart + localTo
            if (pos >= eventEnd) idx++
        }
    }

    companion object {
        /**
         * Marks whose open scope disables autolink detection on enclosed
         * text. `code` and `pre` cover code spans / code blocks; `a` and
         * `img` avoid double-linking; `math`, `script`, `style` are raw
         * content where autolinks aren't applicable.
         */
        private val SUPPRESS_NAMES = setOf(
            "code", "pre", "a", "img", "math", "script", "style"
        )
    }
}

/** Whitespace chars that delimit autolink-candidate words. */
private fun Char.isAutolinkBoundary(): Boolean =
    this == ' ' || this == '\t' || this == '\n' ||
        this == '\r' || this == '\u000C'

/** Chars that are valid pre-boundary for an extended autolink (GFM §6.9). */
private fun Char?.isAutolinkPreBoundary(): Boolean {
    if (this == null) return true
    if (isAutolinkBoundary()) return true
    return this == '*' || this == '_' || this == '~' || this == '('
}

/**
 * Position-tagged autolink found in a concatenated paragraph buffer.
 * The visible link text occupies `[urlStart, urlEnd)` in the original
 * full string; chars outside that range belong to the surrounding
 * (possibly split) plain-text events.
 */
internal data class AutolinkSpan(
    val urlStart: Int,
    val urlEnd: Int,
    val href: String,
    val linkText: String
)

/**
 * Scan [full] for autolinks, walking word-by-word (whitespace-delimited)
 * and applying the pre-boundary rule to each word's start char. Returns
 * the matches in order.
 */
internal fun findAutolinkSpans(
    full: String,
    charBeforeFirst: Char?
): List<AutolinkSpan> {
    if (full.isEmpty()) return emptyList()
    val out = mutableListOf<AutolinkSpan>()
    var i = 0
    while (i < full.length) {
        while (i < full.length && full[i].isAutolinkBoundary()) i++
        if (i >= full.length) break
        val wordStart = i
        while (i < full.length && !full[i].isAutolinkBoundary()) i++
        val wordEnd = i
        val word = full.substring(wordStart, wordEnd)
        val pre = if (wordStart == 0) charBeforeFirst else full[wordStart - 1]
        val match = detectAutolink(word, pre) ?: continue
        out += AutolinkSpan(
            urlStart = wordStart + match.prefix.length,
            urlEnd = wordStart + match.prefix.length + match.linkText.length,
            href = match.href,
            linkText = match.linkText
        )
    }
    return out
}

/**
 * Result of autolink detection on a single word. The four fields
 * concatenate back to the original word: `prefix + linkText + suffix`
 * (the `<a>` mark surrounds [linkText]).
 */
internal data class AutolinkMatch(
    val prefix: String,
    val href: String,
    val linkText: String,
    val suffix: String
)

/**
 * Try to match an extended autolink in [word], honouring [charBeforeWord]
 * for the pre-boundary check on URL/email starts at position 0.
 *
 * Returns null when no autolink is found (caller emits the word as
 * plain text).
 */
internal fun detectAutolink(word: String, charBeforeWord: Char?): AutolinkMatch? {
    var i = 0
    while (i < word.length) {
        val prev: Char? = if (i == 0) charBeforeWord else word[i - 1]
        if (prev.isAutolinkPreBoundary()) {
            val match = tryAutolinkAt(word, i)
            if (match != null) return match
        }
        i++
    }
    return null
}

/**
 * Try every autolink kind starting at [word]`[start]`. The candidate kinds
 * (in priority order) are: scheme `http(s)?://` or `ftp://` URI, `www.`
 * URL, `mailto:` / `xmpp:` scheme, plain email.
 */
private fun tryAutolinkAt(word: String, start: Int): AutolinkMatch? {
    return tryUrlSchemeAutolink(word, start)
        ?: tryWwwAutolink(word, start)
        ?: tryMailtoOrXmppAutolink(word, start, "mailto:", isXmpp = false)
        ?: tryMailtoOrXmppAutolink(word, start, "xmpp:", isXmpp = true)
        ?: tryEmailAutolink(word, start)
}

private val URL_SCHEMES = listOf("http://", "https://", "ftp://")

private fun tryUrlSchemeAutolink(word: String, start: Int): AutolinkMatch? {
    val scheme = URL_SCHEMES.firstOrNull {
        word.regionMatchesAsciiCi(start, it, 0, it.length)
    } ?: return null
    val urlEnd = findUrlEnd(word, start + scheme.length)
    val rawUrl = word.substring(start, urlEnd)
    val (urlBody, stripped) = stripTrailingUrl(rawUrl)
    if (urlBody.length <= scheme.length) return null
    if (!isValidExtendedDomain(urlBody.substring(scheme.length))) return null
    return AutolinkMatch(
        prefix = word.substring(0, start),
        href = urlBody,
        linkText = urlBody,
        suffix = stripped + word.substring(urlEnd)
    )
}

private fun tryWwwAutolink(word: String, start: Int): AutolinkMatch? {
    if (!word.regionMatchesAsciiCi(start, "www.", 0, 4)) return null
    val urlEnd = findUrlEnd(word, start + 4)
    val rawUrl = word.substring(start, urlEnd)
    val (urlBody, stripped) = stripTrailingUrl(rawUrl)
    if (urlBody.length <= 4) return null
    if (!isValidExtendedDomain(urlBody)) return null
    return AutolinkMatch(
        prefix = word.substring(0, start),
        href = "http://$urlBody",
        linkText = urlBody,
        suffix = stripped + word.substring(urlEnd)
    )
}

private fun tryMailtoOrXmppAutolink(
    word: String,
    start: Int,
    scheme: String,
    isXmpp: Boolean
): AutolinkMatch? {
    if (!word.regionMatchesAsciiCi(start, scheme, 0, scheme.length)) return null
    val addrStart = start + scheme.length
    val email = scanEmail(word, addrStart) ?: return null
    var addrEnd = addrStart + email.length
    // xmpp: optional `/resource` (one segment, no further `/`).
    if (isXmpp && addrEnd < word.length && word[addrEnd] == '/') {
        var j = addrEnd + 1
        while (j < word.length && word[j] != '/' && word[j] != '<' &&
            !word[j].isAutolinkBoundary()
        ) j++
        if (j > addrEnd + 1) addrEnd = j
    }
    val rawUrl = word.substring(start, addrEnd)
    val (urlBody, stripped) = stripTrailingMailtoOrXmpp(rawUrl)
    val emailPart = urlBody.substring(scheme.length)
    if (!isValidEmailLastChar(emailPart)) return null
    return AutolinkMatch(
        prefix = word.substring(0, start),
        href = urlBody,
        linkText = urlBody,
        suffix = stripped + word.substring(addrEnd)
    )
}

private fun tryEmailAutolink(word: String, start: Int): AutolinkMatch? {
    val email = scanEmail(word, start) ?: return null
    val rawEnd = start + email.length
    val (body, stripped) = stripTrailingEmail(email)
    if (body.isEmpty()) return null
    if (!isValidEmailLastChar(body)) return null
    return AutolinkMatch(
        prefix = word.substring(0, start),
        href = "mailto:$body",
        linkText = body,
        suffix = stripped + word.substring(rawEnd)
    )
}

/**
 * Scan an email address starting at [start]. Returns the matched local-part
 * + `@` + domain string, or null if the position doesn't begin a valid
 * email shape.
 *
 * Local part: `[A-Za-z0-9._\-+]+` (GFM §6.9 character set).
 *
 * Domain: alphanumeric/`-`/`_` segments separated by `.`, at least one `.`,
 * each segment 1..63 chars, segments cannot begin or end with `-`, and the
 * last two segments must not contain `_` (GFM §6.9 rule).
 */
private fun scanEmail(s: String, start: Int): String? {
    var i = start
    while (i < s.length && s[i].isEmailLocalChar()) i++
    if (i == start) return null
    if (i >= s.length || s[i] != '@') return null
    val atIdx = i
    if (s[atIdx - 1] == '.') return null  // local-part can't end with `.`
    i++
    val segments = mutableListOf<IntRange>()
    var segStart = i
    while (i < s.length) {
        val c = s[i]
        if (c == '.') {
            if (i == segStart) return null
            segments += (segStart until i)
            segStart = i + 1
            i++
        } else if (c.isDomainSegmentChar()) {
            i++
        } else {
            break
        }
    }
    if (segStart < i) {
        segments += (segStart until i)
    }
    if (segments.size < 2) return null
    for ((idx, range) in segments.withIndex()) {
        val len = range.last - range.first + 1
        if (len !in 1..63) return null
        val first = s[range.first]
        val last = s[range.last]
        if (first == '-' || last == '-') return null
        val isLastTwo = idx >= segments.size - 2
        if (isLastTwo) {
            for (k in range) if (s[k] == '_') return null
        }
    }
    val emailEnd = segments.last().last + 1
    return s.substring(start, emailEnd)
}

/**
 * Validate a "valid extended domain" string (the part after `www.` or
 * after the URI scheme — i.e., the host plus any path/query/fragment).
 * The host portion (chars up to first `/`, `?`, `#`) is checked: at least
 * one `.` separating segments of `[A-Za-z0-9_-]`, last two segments forbid
 * `_`.
 */
private fun isValidExtendedDomain(hostAndPath: String): Boolean {
    val hostEnd = hostAndPath.indexOfAny(charArrayOf('/', '?', '#'))
        .let { if (it == -1) hostAndPath.length else it }
    val host = hostAndPath.substring(0, hostEnd)
    if (host.isEmpty()) return false
    val segments = host.split('.')
    if (segments.size < 2) return false
    for ((idx, seg) in segments.withIndex()) {
        if (seg.isEmpty() || seg.length > 63) return false
        if (seg.first() == '-' || seg.last() == '-') return false
        for (c in seg) {
            if (!c.isDomainSegmentChar()) return false
        }
        val isLastTwo = idx >= segments.size - 2
        if (isLastTwo && '_' in seg) return false
    }
    return true
}

/**
 * Find the URL-body end, scanning from [start] in [word]. Stops at the
 * first `<` (GFM forbids `<` inside extended URL bodies) or end-of-word.
 */
private fun findUrlEnd(word: String, start: Int): Int {
    var i = start
    while (i < word.length && word[i] != '<') i++
    return i
}

/**
 * Apply the GFM trailing-strip rules for `http(s)?:` / `ftp:` / `www.`
 * extended URL bodies. Returns the kept body and the stripped suffix
 * (which the caller emits as plain text after the `<a>`).
 *
 * Rules applied in order:
 * 1. Trailing `&xxx;` HTML entity is stripped (`xxx` is `[A-Za-z0-9]+`).
 * 2. Trailing punctuation `?!.,:*_~` is stripped repeatedly.
 * 3. Trailing unbalanced `)` is stripped while the body has more `)`
 *    than `(`.
 * Steps 2 and 3 alternate: stripping a `)` may expose another stripable
 * punct, etc.
 */
private fun stripTrailingUrl(raw: String): Pair<String, String> {
    var end = raw.length
    var changed = true
    while (changed) {
        changed = false
        val ent = trailingEntityLength(raw, end)
        if (ent > 0) {
            end -= ent
            changed = true
            continue
        }
        if (end > 0 && raw[end - 1] in TRAILING_URL_PUNCT) {
            end--
            changed = true
            continue
        }
        if (end > 0 && raw[end - 1] == ')') {
            var opens = 0
            var closes = 0
            for (i in 0 until end) {
                val c = raw[i]
                if (c == '(') opens++ else if (c == ')') closes++
            }
            if (closes > opens) {
                end--
                changed = true
                continue
            }
        }
    }
    return raw.substring(0, end) to raw.substring(end)
}

/**
 * Trailing-strip rules for `mailto:` / `xmpp:` URIs: punctuation `?!.,:*_~`
 * plus `/` (`mailto:foo@bar.baz/` → URL is `mailto:foo@bar.baz`, `/` is
 * trailing text per GFM example 633). Entity-ref / paren-balance rules
 * do not apply (the address shape forbids `&xxx;` / `(`/`)` anyway).
 */
private fun stripTrailingMailtoOrXmpp(raw: String): Pair<String, String> {
    var end = raw.length
    while (end > 0 &&
        (raw[end - 1] in TRAILING_URL_PUNCT || raw[end - 1] == '/')
    ) {
        end--
    }
    return raw.substring(0, end) to raw.substring(end)
}

/**
 * Trailing-strip rules for plain email autolinks: only punctuation
 * `?!.,:*_~` (the spec calls out trailing `.`).
 */
private fun stripTrailingEmail(raw: String): Pair<String, String> {
    var end = raw.length
    while (end > 0 && raw[end - 1] in TRAILING_URL_PUNCT) {
        end--
    }
    return raw.substring(0, end) to raw.substring(end)
}

/**
 * Returns the length of a trailing `&[A-Za-z0-9]+;` HTML entity reference
 * in `s[0, end)`, or 0 if none. Used by [stripTrailingUrl].
 */
private fun trailingEntityLength(s: String, end: Int): Int {
    if (end <= 0 || s[end - 1] != ';') return 0
    var i = end - 2
    while (i >= 0 && s[i].isAsciiAlphanumeric()) i--
    if (i < 0 || s[i] != '&') return 0
    if (i == end - 2) return 0  // `&;` with no body
    return end - i
}

/**
 * GFM email rule: after stripping trailing punctuation, the last char of
 * the autolink must not be `-` or `_`. If it is, the entire autolink is
 * invalid (caller emits the source as plain text).
 */
private fun isValidEmailLastChar(body: String): Boolean {
    if (body.isEmpty()) return false
    val last = body.last()
    return last != '-' && last != '_'
}

private val TRAILING_URL_PUNCT = setOf('?', '!', '.', ',', ':', '*', '_', '~')

private fun Char.isEmailLocalChar(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' ||
        this == '.' || this == '-' || this == '_' || this == '+'

private fun Char.isDomainSegmentChar(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' ||
        this == '-' || this == '_'

private fun Char.isAsciiAlphanumeric(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9'

/**
 * ASCII-case-insensitive [String.regionMatches]. Only ASCII letters fold;
 * other chars must match exactly. Faster than full-locale folding and
 * matches the GFM autolink scheme rule (case-insensitive on ASCII).
 */
private fun String.regionMatchesAsciiCi(
    thisOffset: Int,
    other: String,
    otherOffset: Int,
    length: Int
): Boolean {
    if (thisOffset < 0 || otherOffset < 0) return false
    if (thisOffset + length > this.length || otherOffset + length > other.length) return false
    for (i in 0 until length) {
        val a = this[thisOffset + i]
        val b = other[otherOffset + i]
        if (a == b) continue
        if (a.foldAsciiLower() == b.foldAsciiLower()) continue
        return false
    }
    return true
}

private fun Char.foldAsciiLower(): Char =
    if (this in 'A'..'Z') (this.code or 0x20).toChar() else this
