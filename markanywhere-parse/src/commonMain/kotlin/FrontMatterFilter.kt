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
import com.xemantic.markanywhere.yaml.YamlParser
import com.xemantic.markanywhere.yaml.isYamlKeyLine
import kotlinx.coroutines.flow.FlowCollector

/**
 * Streaming auto-detection of YAML front matter at the start of the
 * document.
 *
 * Sits between the input chunk flow and the regular Markdown parser:
 *
 * - Buffers chars until enough is seen to decide (line 1 + line 2, or EOF).
 * - **Trigger**: line 1 is exactly `---` and line 2 matches a strict
 *   discriminator that almost no natural-language paragraph does: a mapping
 *   key — an identifier (`title`, `date-published`, `page.section`, any
 *   letters) or a quoted key (`"og:title"`) — followed by `:` and whitespace
 *   or end of line (so `http://…` does not qualify). A comment line does not
 *   qualify either: `---` followed by `# Heading` is a thematic break plus
 *   a heading. The discriminator is what disambiguates a real front matter
 *   from a `---` thematic break followed by prose. Only YAML is recognised;
 *   a `+++` (TOML) fence is ordinary Markdown.
 * - **On hit**: emits an untagged `mark("frontmatter")` directly to the
 *   downstream collector (bypassing the parser's autolink stage, since the
 *   body is not Markdown), feeds each body line to a [YamlParser] (from
 *   `markanywhere-yaml`) that emits the structured `entry` / `item` events,
 *   drops the closer line,
 *   and emits `unmark`. Anything after the closer is forwarded into the
 *   regular parser as a fresh stream.
 * - **On miss**: replays the full buffered prelude into the regular parser
 *   and switches to pass-through.
 * - **On EOF without a closer** (after a hit): force-closes the open
 *   `frontmatter` mark. This mirrors how unmatched code-span openers behave
 *   — the streaming invariant forbids retracting an emitted `mark`.
 *
 * Line endings (`\r\n`, `\r`, `\n`) are normalized to `\n` at the entry of
 * [feed], so opener / closer detection works regardless of source platform.
 *
 * **Known limitation** (matching Jekyll/Hugo behaviour): an unindented
 * `---` line anywhere in the body is treated as the closer — even inside a
 * block scalar; content that needs to contain such a line at the left
 * margin must indent it by at least one space.
 */
internal class FrontMatterFilter(
    private val directDownstream: FlowCollector<SemanticEvent>,
    private val processInner: suspend (String) -> Unit
) {

    private enum class Mode { Detecting, InBody, AfterClose }

    private var mode: Mode = Detecting
    private val prelude = StringBuilder()

    // The body text not yet consumed: at most one incomplete line plus the
    // chunk just appended — complete lines are handed to [yaml] on arrival.
    private val body = StringBuilder()
    private val yaml = YamlParser(directDownstream)

    // Tracks how far into `body` we already scanned for `\n` without finding
    // one — the resume index for the next call to `drainBodyToCloser`. Without
    // this, a long front-matter line arriving in tiny chunks would be O(n²)
    // (each call re-scans from index 0 looking for the same missing `\n`).
    private var bodyScanOffset: Int = 0

    // Cross-chunk `\r\n` handling: when a chunk ends with `\r`, we already
    // emit `\n` in its place and set this flag so the next chunk's leading
    // `\n` (the second half of the CRLF pair) is swallowed.
    private var pendingCr: Boolean = false

    suspend fun feed(chunk: String) {
        val normalized = normalizeLineEndings(chunk)
        when (mode) {
            Detecting -> {
                prelude.append(normalized)
                tryDecide(eof = false)
            }
            InBody -> {
                body.append(normalized)
                drainBodyToCloser()
            }
            AfterClose -> processInner(normalized)
        }
    }

    // Normalize `\r\n` / `\r` to `\n`. Mirrors `ParserState.preprocessChunk`
    // (which is private and runs on the AfterClose pass-through path), but
    // we need it here for opener/closer detection too — the `Detecting` and
    // `InBody` paths bypass `processInner` entirely.
    private fun normalizeLineEndings(chunk: String): String {
        if (!pendingCr && chunk.indexOf('\r') < 0) return chunk
        val sb = StringBuilder(chunk.length)
        var i = 0
        if (pendingCr) {
            pendingCr = false
            if (chunk[0] == '\n') i++
        }
        while (i < chunk.length) {
            val c = chunk[i]
            if (c == '\r') {
                sb.append('\n')
                i++
                if (i < chunk.length) {
                    if (chunk[i] == '\n') i++
                } else {
                    pendingCr = true
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    suspend fun finalize() {
        if (mode == Detecting) {
            tryDecide(eof = true)
            if (mode == Detecting && prelude.isNotEmpty()) {
                processInner(prelude.toString())
                prelude.clear()
                mode = AfterClose
            }
        }
        if (mode == InBody) {
            finalizeInBody()
        }
    }

    private suspend fun tryDecide(eof: Boolean) {
        if (prelude.isEmpty()) return
        // Verify chars seen so far match the "---\n" prefix.
        val seen = minOf(prelude.length, OPENER_LINE.length)
        for (i in 0 until seen) {
            if (prelude[i] != OPENER_LINE[i]) {
                replayPreludeAsContent()
                return
            }
        }
        // Not enough chars yet to confirm the opener line itself.
        if (prelude.length < OPENER_LINE.length) {
            if (eof) replayPreludeAsContent()
            return
        }

        // Examine line 2 (everything after the opener `\n` until next `\n`/EOF).
        val line2Start = OPENER_LINE.length
        val nl2 = prelude.indexOf('\n', line2Start)
        val line2: String = when {
            nl2 >= 0 -> prelude.substring(line2Start, nl2)
            eof -> prelude.substring(line2Start)
            else -> return  // wait for more chars
        }
        if (!isYamlKeyLine(line2)) {
            replayPreludeAsContent()
            return
        }

        // Open front matter.
        directDownstream.emit(
            SemanticEvent.Mark(name = FRONTMATTER, isTagged = false)
        )
        body.append(prelude, line2Start, prelude.length)
        prelude.clear()
        mode = InBody
        drainBodyToCloser()
    }

    private suspend fun replayPreludeAsContent() {
        if (prelude.isNotEmpty()) {
            processInner(prelude.toString())
            prelude.clear()
        }
        mode = AfterClose
    }

    // Hands every complete line to the YAML parser. The consumed prefix is
    // deleted from `body` once, after the loop — deleting per line would
    // shift the whole remaining buffer for each of the N lines of a chunk.
    private suspend fun drainBodyToCloser() {
        var consumed = 0
        while (true) {
            val nl = body.indexOf('\n', maxOf(consumed, bodyScanOffset))
            if (nl < 0) {
                body.deleteRange(0, consumed)
                // Everything currently in `body` has been scanned for `\n`
                // with none found; resume future scans from the tail.
                bodyScanOffset = body.length
                return
            }
            val line = body.substring(consumed, nl)
            consumed = nl + 1
            if (line == CLOSER) {
                closeFrontMatter()
                val rest = body.substring(consumed)
                body.clear()
                bodyScanOffset = 0
                if (rest.isNotEmpty()) processInner(rest)
                return
            }
            yaml.line(line)
        }
    }

    private suspend fun finalizeInBody() {
        val residual = body.toString()
        // A bare `---` at EOF without a trailing `\n` is treated as the
        // structural closer arriving without its terminator — drop it
        // rather than feed it as a body line.
        if (residual != CLOSER && residual.isNotEmpty()) {
            yaml.line(residual)
        }
        body.clear()
        closeFrontMatter()
    }

    private suspend fun closeFrontMatter() {
        yaml.finish()
        directDownstream.emit(
            SemanticEvent.Unmark(name = FRONTMATTER, isTagged = false)
        )
        mode = AfterClose
    }

    private companion object {
        const val FRONTMATTER: String = "frontmatter"
        const val CLOSER: String = "---"
        const val OPENER_LINE: String = "---\n"
    }
}
