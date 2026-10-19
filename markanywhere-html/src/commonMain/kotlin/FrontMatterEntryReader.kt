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
import com.xemantic.markanywhere.html.spec.isHtmlBlank
import com.xemantic.markanywhere.yaml.YAML_VERBATIM_TYPE

// Whether this event opens the frontmatter that is the page's metadata, the
// `preceding` events being all the stream held before it: an untagged
// `frontmatter` mark, nothing but [insignificant][mayPrecedeFrontmatter]
// text ahead of it. The one rule wrapInHtmlDocument reads the head by and
// ensureFrontmatterTitle judges the title by — anywhere else a frontmatter
// is content.
internal fun SemanticEvent.opensFrontmatter(preceding: List<SemanticEvent>): Boolean =
    isFrontmatterMark() && preceding.all { it.mayPrecedeFrontmatter() }

// Whether this is the mark of a frontmatter that is metadata wherever it
// stands first: an untagged `frontmatter` mark (a tagged one is content).
internal fun SemanticEvent.isFrontmatterMark(): Boolean =
    this is Mark && !isTagged && name == "frontmatter"

// Whether this event may come ahead of the frontmatter without keeping it
// from opening the stream: text of HTML whitespace (an NBSP is content).
internal fun SemanticEvent.mayPrecedeFrontmatter(): Boolean = this is Text && text.isHtmlBlank()

// A top-level front matter `entry`: its key as spelled, `type`, text,
// whether it holds nested marks, and the span of its events among those
// read. An entry the YAML parser kept as source (`type=verbatim`, a value
// outside its subset) [isVerbatim]: its value is unknown, so it is never
// head metadata, yet it holds the key for readers.
internal class FrontMatterEntry(
    val key: String,
    val type: String?,
    val text: String,
    val hasChildren: Boolean,
    val start: Int,
    val end: Int
) {

    // Whether its value is unknown: kept as YAML source by the parser.
    val isVerbatim: Boolean get() = type == YAML_VERBATIM_TYPE

    // Whether it holds head metadata: scalar text with visible content —
    // not a value kept as source, a nested structure, an empty collection,
    // `null` or blank text.
    val isHeadMetadata: Boolean =
        !isVerbatim && !hasChildren && isScalarEntryType(type) && isMetadataValue(text)

}

// Reads the top-level entries of a `frontmatter` subtree, reporting each to
// `onEntry` once its unmark is read. The one reader of front matter as head
// metadata — wrapInHtmlDocument turns the entries into `<head>`,
// ensureFrontmatterTitle judges the title entries among them, and the two
// must agree on both. Text outside any entry (a line outside the YAML subset
// that is no entry) is no part of any entry: the YAML parser puts every
// line of an entry's value inside its mark.
internal class FrontMatterEntryReader(
    private val onEntry: (FrontMatterEntry) -> Unit
) {

    // 1 inside the frontmatter, 2 inside a top-level entry
    private var depth = 0
    private var index = -1
    private var open: SemanticEvent.Mark? = null
    private var openStart = 0
    private var openHasChildren = false
    private val openText = StringBuilder()

    // Whether the root is a sequence: a top-level `item` was read.
    var isSequence: Boolean = false
        private set

    // Reads the next event of the subtree, the frontmatter mark first; true
    // once the frontmatter's own unmark is read.
    fun read(event: SemanticEvent): Boolean {
        index++
        when (event) {
            is Mark -> {
                depth++
                if (depth == 2) {
                    if (event.name == "item") isSequence = true
                    open = if (event.name == "entry" && event["key"] != null) event else null
                    openStart = index
                    openHasChildren = false
                    openText.clear()
                } else if (depth > 2) {
                    openHasChildren = true
                }
            }
            is Text -> if (depth == 2) openText.append(event.text)
            is Unmark -> {
                if (--depth == 1) closeEntry()
                if (depth == 0) return true
            }
        }
        return false
    }

    // Reports an entry left open by a frontmatter that never closed (a broken
    // upstream contract) — its text is complete by then.
    fun finish() {
        closeEntry()
    }

    private fun closeEntry() {
        open?.let {
            open = null
            onEntry(
                FrontMatterEntry(
                    key = it["key"]!!,
                    type = it["type"],
                    text = openText.toString(),
                    hasChildren = openHasChildren,
                    start = openStart,
                    end = index
                )
            )
        }
    }

}
