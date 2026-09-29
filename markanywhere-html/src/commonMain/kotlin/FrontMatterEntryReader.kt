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

// A top-level front matter `entry`: its key as spelled, `type`, text, whether
// it holds nested marks, and the span of its events among those read.
internal class FrontMatterEntry(
    val key: String,
    val type: String?,
    val text: String,
    val hasChildren: Boolean,
    val start: Int,
    val end: Int
) {

    // Whether it holds head metadata: scalar text with visible content —
    // not a nested structure, an empty collection, `null` or blank text.
    val isHeadMetadata: Boolean
        get() = !hasChildren && isScalarEntryType(type) && isMetadataValue(text)

}

// Reads the top-level entries of a `frontmatter` subtree, reporting each to
// `onEntry` as it closes. The one reader of front matter as head metadata —
// wrapInHtmlDocument turns the entries into `<head>`, ensureFrontmatterTitle
// judges the title entries among them, and the two must agree on both.
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

    // Reads the next event of the subtree, the frontmatter mark first; true
    // once the frontmatter's own unmark is read.
    fun read(event: SemanticEvent): Boolean {
        index++
        when (event) {
            is Mark -> {
                depth++
                if (depth == 2) {
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
                return depth == 0
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
