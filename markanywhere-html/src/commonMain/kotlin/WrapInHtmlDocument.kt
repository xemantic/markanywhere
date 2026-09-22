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
 * YAML `---` front matter, holding `entry` marks) feeds the `head` — the
 * inverse of [simplifyHtml]'s head-to-frontmatter extraction, so the two
 * round-trip:
 *
 * - the `title` entry becomes `<title>`
 * - the `lang` entry becomes the `lang` attribute on `<html>`
 * - every other top-level scalar entry becomes a void `<meta name content>`
 *
 * Only top-level scalar entries are interpreted — exactly the shape
 * [simplifyHtml] produces. A nested mapping or sequence, a null value, and
 * verbatim text are skipped (never an error); a later duplicate key wins.
 * A `frontmatter` mark appearing anywhere past the first event is ordinary
 * content and flows into `body` verbatim.
 *
 * Only the frontmatter subtree is read ahead (bounded); without one the
 * document opening is emitted on the first event and body content streams
 * through untouched. All synthetic marks are untagged, consistent with the
 * parser's `frontmatter` mark and [simplifyHtml] output. An empty input
 * stream still yields the full document skeleton.
 */
public fun Flow<SemanticEvent>.wrapInHtmlDocument(): Flow<SemanticEvent> = semanticEvents {

    var opened = false
    var collectingFrontmatter = false
    // nesting depth inside the frontmatter: 1 while inside a top-level entry
    var depth = 0
    // the top-level entry being read, null when it is not a scalar to keep
    var entryKey: String? = null
    val entryText = StringBuilder()
    val metadata = LinkedHashMap<String, String>()

    // `head` and its subtree are lexically scoped, so the paired `"name" { }`
    // builder fits; `html` and `body` close only at end-of-stream, so their
    // unmark cannot come from a builder block — explicit mark/unmark instead.
    suspend fun openDocument() {
        opened = true
        collectingFrontmatter = false
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

    collect { event ->
        when {
            collectingFrontmatter -> when (event) {
                is Mark -> {
                    depth++
                    if (depth == 1) {
                        val type = event["type"]
                        entryKey = if (
                            event.name == "entry" && (type == null || type in SCALAR_TYPES)
                        ) event["key"] else null
                        entryText.clear()
                    } else {
                        entryKey = null // a nested mark: not a scalar
                    }
                }
                is Text -> if (depth == 1) entryText.append(event.text)
                is Unmark -> if (depth == 0) {
                    openDocument()
                } else {
                    if (depth == 1) {
                        entryKey?.let { metadata[it] = entryText.toString() }
                        entryKey = null
                    }
                    depth--
                }
            }
            !opened -> if (
                event is Mark && !event.isTagged && event.name == "frontmatter"
            ) {
                collectingFrontmatter = true
            } else {
                openDocument()
                emit(event)
            }
            else -> emit(event)
        }
    }

    // An unclosed frontmatter at end of stream (broken upstream contract) is
    // still used; an empty stream yields the bare skeleton.
    if (!opened) openDocument()
    unmark("body")
    unmark("html")
}

// Scalar `type`s whose text is meaningful as a `<meta content>` (`null` and
// the empty collections are not).
private val SCALAR_TYPES = setOf("bool", "int", "float", "timestamp")
