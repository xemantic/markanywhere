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
import kotlinx.coroutines.flow.FlowCollector

/**
 * Streaming auto-detection of YAML / TOML front matter at the start of the
 * document.
 *
 * Sits between the input chunk flow and the regular Markdown parser:
 *
 * - Buffers chars until enough is seen to decide (line 1 + line 2, or EOF).
 * - **Trigger**: line 1 is exactly `---` (YAML) or `+++` (TOML), and line 2
 *   matches a strict discriminator that almost no natural-language paragraph
 *   does (an identifier-then-colon for YAML, an identifier-then-equals or
 *   `[section]` header for TOML). The discriminator is what disambiguates a
 *   real front matter from a `---` thematic break followed by prose.
 * - **On hit**: emits an untagged `mark("frontmatter", format=...)` directly
 *   to the downstream collector (bypassing the parser's autolink stage,
 *   since body content is opaque), streams body lines as `text` events
 *   preserving `\n`, drops the closer line, and emits `unmark`. Anything
 *   after the closer is forwarded into the regular parser as a fresh stream.
 * - **On miss**: replays the full buffered prelude into the regular parser
 *   and switches to pass-through.
 * - **On EOF without a closer** (after a hit): force-closes the open
 *   `frontmatter` mark. This mirrors how unmatched code-span openers behave
 *   — the streaming invariant forbids retracting an emitted `mark`.
 */
internal class FrontMatterFilter(
    private val directDownstream: FlowCollector<SemanticEvent>,
    private val processInner: suspend (String) -> Unit
) {

    private enum class Mode { Detecting, InBody, AfterClose }

    private var mode: Mode = Mode.Detecting
    private val prelude = StringBuilder()
    private val body = StringBuilder()
    private var format: String = ""
    private var closerLine: String = ""

    suspend fun feed(chunk: String) {
        when (mode) {
            Detecting -> {
                prelude.append(chunk)
                tryDecide(eof = false)
            }
            InBody -> {
                body.append(chunk)
                drainBodyToCloser()
            }
            AfterClose -> processInner(chunk)
        }
    }

    suspend fun finalize() {
        if (mode == Mode.Detecting) {
            tryDecide(eof = true)
            if (mode == Mode.Detecting && prelude.isNotEmpty()) {
                processInner(prelude.toString())
                prelude.clear()
                mode = Mode.AfterClose
            }
        }
        if (mode == Mode.InBody) {
            finalizeInBody()
        }
    }

    private suspend fun tryDecide(eof: Boolean) {
        if (prelude.isEmpty()) return
        val first = prelude[0]
        if (first != '-' && first != '+') {
            replayPreludeAsContent()
            return
        }
        // Verify chars seen so far match "---\n" or "+++\n" prefix.
        val expected = if (first == '-') "---\n" else "+++\n"
        val seen = minOf(prelude.length, 4)
        for (i in 0 until seen) {
            if (prelude[i] != expected[i]) {
                replayPreludeAsContent()
                return
            }
        }
        // Not enough chars yet to confirm the opener line itself.
        if (prelude.length < 4) {
            if (eof) replayPreludeAsContent()
            return
        }
        val opener = if (first == '-') "---" else "+++"
        val fmt = if (first == '-') "yaml" else "toml"

        // Examine line 2 (everything after the opener `\n` until next `\n`/EOF).
        val line2Start = 4
        val nl2 = prelude.indexOf('\n', line2Start)
        val line2: String = when {
            nl2 >= 0 -> prelude.substring(line2Start, nl2)
            eof -> prelude.substring(line2Start)
            else -> return  // wait for more chars
        }
        if (!discriminatorMatches(line2, fmt)) {
            replayPreludeAsContent()
            return
        }

        // Open front matter.
        format = fmt
        closerLine = opener
        directDownstream.emit(
            SemanticEvent.Mark(
                name = FRONTMATTER,
                isTagged = false,
                attributes = mapOf("format" to fmt)
            )
        )
        body.append(prelude, line2Start, prelude.length)
        prelude.clear()
        mode = Mode.InBody
        drainBodyToCloser()
    }

    private suspend fun replayPreludeAsContent() {
        if (prelude.isNotEmpty()) {
            processInner(prelude.toString())
            prelude.clear()
        }
        mode = Mode.AfterClose
    }

    private suspend fun drainBodyToCloser() {
        var start = 0
        while (true) {
            val nl = body.indexOf('\n', start)
            if (nl < 0) break
            val line = body.substring(start, nl)
            if (line == closerLine) {
                if (start > 0) {
                    directDownstream.emit(SemanticEvent.Text(body.substring(0, start)))
                }
                directDownstream.emit(
                    SemanticEvent.Unmark(name = FRONTMATTER, isTagged = false)
                )
                val rest = body.substring(nl + 1)
                body.clear()
                mode = Mode.AfterClose
                if (rest.isNotEmpty()) processInner(rest)
                return
            }
            start = nl + 1
        }
        if (start > 0) {
            directDownstream.emit(SemanticEvent.Text(body.substring(0, start)))
            body.deleteRange(0, start)
        }
    }

    private suspend fun finalizeInBody() {
        val residual = body.toString()
        if (residual != closerLine && residual.isNotEmpty()) {
            directDownstream.emit(SemanticEvent.Text(residual))
        }
        body.clear()
        directDownstream.emit(
            SemanticEvent.Unmark(name = FRONTMATTER, isTagged = false)
        )
        mode = Mode.AfterClose
    }

    private companion object {
        const val FRONTMATTER: String = "frontmatter"

        // Strict YAML discriminator: top-level identifier followed by `:`.
        // Catches `title:`, `_key:`, `date-published:` — the overwhelming
        // shape of real-world YAML front-matter first keys.
        val YAML_KEY = Regex("""^[A-Za-z_][A-Za-z0-9_-]*\s*:""")

        // Strict TOML discriminator: a `[section]` / `[[array]]` header,
        // or an identifier followed by `=` (`key = value`).
        val TOML_KEY_OR_SECTION =
            Regex("""^(\[.+\]|[A-Za-z_][A-Za-z0-9_-]*\s*=)""")

        fun discriminatorMatches(line: String, format: String): Boolean =
            when (format) {
                "yaml" -> YAML_KEY.containsMatchIn(line)
                "toml" -> TOML_KEY_OR_SECTION.containsMatchIn(line)
                else -> false
            }
    }
}
