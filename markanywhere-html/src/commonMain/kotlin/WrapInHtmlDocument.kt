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
import kotlinx.coroutines.flow.Flow

/**
 * Wraps a semantic event stream (typically parsed Markdown) in an
 * `html`/`head`/`body` document structure.
 *
 * A **leading** `frontmatter` block (the untagged mark the parser emits for
 * YAML `---` / TOML `+++` front matter) feeds the `head` — the inverse of
 * [simplifyHtml]'s head-to-frontmatter extraction, so the two round-trip:
 *
 * - `title` becomes `<title>`
 * - `lang` becomes the `lang` attribute on `<html>`
 * - every other flat key becomes a void `<meta name content>`
 *
 * Only the flat `key: value` (YAML) / `key = "value"` (TOML) subset is
 * interpreted — exactly the shape [simplifyHtml] produces. Nested structures,
 * comments, and malformed lines are silently skipped; an unknown format or
 * a wholly unparseable body yields an empty `head` (never an error). A
 * `frontmatter` mark appearing anywhere past the first event is ordinary
 * content and flows into `body` verbatim.
 *
 * Only the frontmatter body is buffered (bounded); without one the document
 * opening is emitted on the first event and body content streams through
 * untouched. All synthetic marks are untagged, consistent with the parser's
 * `frontmatter` mark and [simplifyHtml] output. An empty input stream still
 * yields the full document skeleton.
 */
public fun Flow<SemanticEvent>.wrapInHtmlDocument(): Flow<SemanticEvent> = semanticEvents {

    var opened = false
    var collectingFrontmatter = false
    // Balances defensively against nested marks inside the frontmatter block
    // (the parser emits only text there, but a hand-built flow might not).
    var frontmatterDepth = 0
    var frontmatterFormat = ""
    val frontmatterBody = StringBuilder()

    // `head` and its subtree are lexically scoped, so the paired `"name" { }`
    // builder fits; `html` and `body` close only at end-of-stream, so their
    // unmark cannot come from a builder block — explicit mark/unmark instead.
    suspend fun openDocument(metadata: Map<String, String>) {
        opened = true
        mark(
            "html",
            attributes = metadata["lang"]
                ?.let { mapOf("lang" to it) }
                ?: emptyMap()
        )
        "head" {
            metadata["title"]?.let { title ->
                "title" {
                    +title
                }
            }
            for ((key, value) in metadata) {
                if (key == "title" || key == "lang") continue
                "meta"("name" to key, "content" to value) {}
            }
        }
        mark("body")
    }

    suspend fun openDocumentFromFrontmatter() {
        collectingFrontmatter = false
        openDocument(
            parseFlatFrontmatter(frontmatterBody.toString(), frontmatterFormat)
        )
    }

    collect { event ->
        when {
            collectingFrontmatter -> when (event) {
                is Text -> frontmatterBody.append(event.text)
                is Mark -> frontmatterDepth++
                is Unmark -> if (frontmatterDepth == 0) {
                    openDocumentFromFrontmatter()
                } else {
                    frontmatterDepth--
                }
            }
            !opened -> if (
                event is Mark && !event.isTagged && event.name == "frontmatter"
            ) {
                collectingFrontmatter = true
                frontmatterFormat = event["format"] ?: ""
            } else {
                openDocument(emptyMap())
                emit(event)
            }
            else -> emit(event)
        }
    }

    // An unclosed frontmatter at end of stream (broken upstream contract) is
    // still used; an empty stream yields the bare skeleton.
    if (collectingFrontmatter) openDocumentFromFrontmatter()
    if (!opened) openDocument(emptyMap())
    unmark("body")
    unmark("html")
}
