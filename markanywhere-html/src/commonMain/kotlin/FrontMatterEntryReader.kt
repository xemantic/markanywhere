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
import com.xemantic.markanywhere.yaml.yamlKeyLineKeyOrNull

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

// A top-level front matter `entry`: its key as spelled, `type`, text, whether
// it holds nested marks, and the span of its events among those read — the
// indented verbatim lines continuing it included, so dropping the span drops
// them too, rather than leaving them to continue the entry before it. An
// entry whose value is unknown is [isVerbatim], never head metadata, yet it
// holds the key for readers: a verbatim line defining a key (one outside the
// YAML subset, such as a multi-line quoted scalar), an entry continued by
// indented verbatim lines (readers join them into the value), and one
// holding indented verbatim lines under a bare `key:`.
internal class FrontMatterEntry(
    val key: String,
    val type: String?,
    val text: String,
    val hasChildren: Boolean,
    val isVerbatim: Boolean,
    val start: Int,
    val end: Int
) {

    // Whether it holds head metadata: scalar text with visible content —
    // not a nested structure, an empty collection, `null`, blank text or a
    // verbatim line.
    val isHeadMetadata: Boolean =
        !hasChildren && !isVerbatim && isScalarEntryType(type) && isMetadataValue(text)

}

// Reads the top-level entries of a `frontmatter` subtree, reporting each to
// `onEntry` once its span is known: when the next top-level entry or verbatim
// line opens, or the frontmatter closes. The one reader of front matter as
// head metadata — wrapInHtmlDocument turns the entries into `<head>`,
// ensureFrontmatterTitle judges the title entries among them, and the two
// must agree on both.
internal class FrontMatterEntryReader(
    private val onEntry: (FrontMatterEntry) -> Unit
) {

    // 1 inside the frontmatter, 2 inside a top-level entry
    private var depth = 0
    private var index = -1
    private var open: SemanticEvent.Mark? = null
    private var openStart = 0
    private var openHasChildren = false
    private var openIsVerbatim = false
    private val openText = StringBuilder()

    // the last top-level entry read, held until its span is known
    private var pending: FrontMatterEntry? = null

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
                    reportPending()
                    if (event.name == "item") isSequence = true
                    open = if (event.name == "entry" && event["key"] != null) event else null
                    openStart = index
                    openHasChildren = false
                    openIsVerbatim = false
                    openText.clear()
                } else if (depth > 2) {
                    openHasChildren = true
                }
            }
            is Text -> when (depth) {
                1 -> readVerbatimLine(event.text)
                2 -> {
                    // the YAML parser strips a scalar's indentation, never
                    // a verbatim line's, which also keeps its `\n`
                    if (event.text.startsWithIndentation() && event.text.endsWith('\n')) {
                        openIsVerbatim = true
                    }
                    openText.append(event.text)
                }
            }
            is Unmark -> {
                if (--depth == 1) closeEntry()
                if (depth == 0) {
                    reportPending()
                    return true
                }
            }
        }
        return false
    }

    // Reports an entry left open by a frontmatter that never closed (a broken
    // upstream contract) — its text is complete by then.
    fun finish() {
        closeEntry()
        reportPending()
    }

    // A verbatim line (text outside any entry, as the YAML parser emits a
    // line outside its subset): an indented one continues the entry before
    // it, one at the margin ends it, and defines an entry itself when it
    // opens with a key. A blank line decides nothing.
    private fun readVerbatimLine(text: String) {
        if (text.isHtmlBlank()) return
        val entry = pending
        if (text.startsWithIndentation()) {
            if (entry != null) pending = entry.continuedTo(index)
            return
        }
        reportPending()
        pending = yamlKeyLineKeyOrNull(text.substringBefore('\n'))?.let { key ->
            FrontMatterEntry(
                key = key,
                type = null,
                text = "",
                hasChildren = false,
                isVerbatim = true,
                start = index,
                end = index
            )
        }
    }

    private fun closeEntry() {
        open?.let {
            open = null
            pending = FrontMatterEntry(
                key = it["key"]!!,
                type = it["type"],
                text = openText.toString(),
                hasChildren = openHasChildren,
                isVerbatim = openIsVerbatim,
                start = openStart,
                end = index
            )
        }
    }

    private fun reportPending() {
        pending?.let {
            pending = null
            onEntry(it)
        }
    }

}

// The entry continued by the verbatim line ending at [end]: its value is
// no longer known.
private fun FrontMatterEntry.continuedTo(end: Int) =
    FrontMatterEntry(key, type, text, hasChildren, isVerbatim = true, start, end)

private fun String.startsWithIndentation() = startsWith(' ') || startsWith('\t')
