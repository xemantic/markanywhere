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
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.html.spec.asciiLowercase
import kotlinx.coroutines.flow.Flow

/**
 * Wraps a semantic event stream (typically parsed Markdown) in an
 * `html`/`head`/`body` document structure.
 *
 * A **leading** `frontmatter` block (the untagged mark the parser emits for
 * YAML `---` front matter, holding `entry` marks) feeds the `head` — the
 * inverse of [simplifyHtml]'s head-to-frontmatter extraction:
 *
 * - the `title` entry becomes `<title>`
 * - the `lang` entry, its HTML whitespace and any other invisible char at
 *   its edges stripped, becomes the `lang` attribute on `<html>`
 * - every other top-level scalar entry becomes a void `<meta name content>`
 *
 * Only top-level scalar entries are interpreted — exactly the shape
 * [simplifyHtml] produces. A nested mapping or sequence, a null value, a
 * blank value and verbatim text are skipped (never an error). Keys are read
 * the way HTML reads `<meta>` names, ASCII case-insensitively — a `Title`
 * entry is the title. Of duplicate keys the later one wins, as front matter
 * readers (Jekyll, PyYAML) resolve them, except that a key spelled in
 * lowercase — the spelling a case-sensitive reader looks up — beats a
 * variant in another letter case wherever it occurs; the winner keeps the
 * position of the first duplicate.
 *
 * Passing the result back through [simplifyHtml] restores the front matter
 * except for what either side normalises or discards: skipped entries
 * (above) and values [simplifyHtml] drops (noise names such as `viewport`,
 * application state such as a JSON object or an opaque over-long blob) are
 * gone, case-variant duplicates are merged, the `title` and `lang` keys come
 * back spelled in lowercase, the title with its whitespace stripped and
 * collapsed (as `document.title` reads it) and `lang` trimmed, and a typed
 * scalar comes back as a string.
 * Text of HTML whitespace ahead of the frontmatter is insignificant — it is
 * moved to the start of `body` (a non-breaking space is content, as
 * everywhere in HTML, and opens the body instead), as [ensureFrontmatterTitle] moves it after the
 * frontmatter. A `frontmatter` mark appearing past any other event is
 * ordinary content and flows into `body` verbatim.
 *
 * Only leading whitespace text and the frontmatter subtree are read ahead
 * (bounded); without a frontmatter the document opening is emitted on the
 * first other event and body content streams through untouched. All
 * synthetic marks are untagged, consistent with the parser's `frontmatter` mark and [simplifyHtml] output. An empty input
 * stream still yields the full document skeleton.
 */
public fun Flow<SemanticEvent>.wrapInHtmlDocument(): Flow<SemanticEvent> = semanticEvents {

    var opened = false
    val metadata = HeadMetadata()
    // reads the frontmatter while it is being collected
    var frontmatter: FrontMatterEntryReader? = null
    // blank text ahead of the frontmatter, replayed at the start of `body`
    val blanks = mutableListOf<SemanticEvent>()

    // `head` and its subtree are lexically scoped, so the paired `"name" { }`
    // builder fits; `html` and `body` close only at end-of-stream, so their
    // unmark cannot come from a builder block — explicit mark/unmark instead.
    suspend fun openDocument() {
        opened = true
        frontmatter = null
        mark(
            "html",
            attributes = metadata["lang"]
                // trimmed, as simplifyHtml reads it
                ?.let { mapOf("lang" to it.value.normalizeLang()) }
                ?: emptyMap()
        )
        "head" {
            metadata["title"]?.let {
                "title" {
                    +it.value
                }
            }
            for ((key, value) in metadata.values) {
                if (key.asciiLowercase() in HEAD_KEYS) continue
                "meta"("name" to key, "content" to value) {}
            }
        }
        mark("body")
        emit(blanks)
        blanks.clear()
    }

    collect { event ->
        val reader = frontmatter
        when {
            reader != null -> if (reader.read(event)) openDocument()
            !opened -> when {
                event.opensFrontmatter(blanks) -> {
                    frontmatter = FrontMatterEntryReader(metadata::addFromFrontMatter).also { it.read(event) }
                }
                event.mayPrecedeFrontmatter() -> blanks += event
                else -> {
                    openDocument()
                    emit(event)
                }
            }
            else -> emit(event)
        }
    }

    // An unclosed frontmatter at end of stream (broken upstream contract) is
    // still used, including an entry left open; an empty stream yields the
    // bare skeleton.
    frontmatter?.finish()
    if (!opened) openDocument()
    unmark("body")
    unmark("html")
}
