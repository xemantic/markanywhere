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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * Drops inline-formatting elements that carry no renderable content.
 *
 * After [resolveIcons] has turned recognized icon glyphs into text, what's left
 * of the empty phrasing elements is genuine noise: a stray `<em></em>` (renders
 * to Markdown as a spurious `**`), an empty `<abbr></abbr>` (survives as literal
 * `<abbr></abbr>`), and similar. This operator removes such empty wrappers.
 *
 * An element is "blank" when, between its [SemanticEvent.Mark] and the matching
 * [SemanticEvent.Unmark], there is no [non-HTML-blank][isHtmlBlank] text and no
 * surviving child element. Blankness is evaluated bottom-up, so a wrapper whose
 * only children are themselves blank formatting elements (`<sup><em></em></sup>`)
 * collapses too. Only ASCII whitespace counts as blank — an NBSP-only element
 * (`<em>&nbsp;</em>`) carries content and survives, consistent with
 * [dropHtmlStructuralWhitespace].
 *
 * Only the inline *formatting* tags in [INLINE_FORMATTING_TAGS] are
 * candidates — `a`, `img`, `code`, `span`, and every block / structural element
 * are left untouched (an empty `<a href>` or a `<span golemId>` may still carry
 * meaning the caller asked to keep). Matching is by tag name regardless of
 * `isTagged`, since [simplifyHtml] emits untagged events; place this operator
 * *after* `simplifyHtml`.
 *
 * Note: attributes on inline emphasis are already dropped at Markdown render
 * time (Markdown syntax can't carry them), so removing an empty `<em golemId>`
 * loses nothing the Markdown output could have shown anyway.
 */
public fun Flow<SemanticEvent>.dropBlankInlineFormatting(): Flow<SemanticEvent> = flow {

    val suppressedMarks = ArrayDeque<SemanticEvent.Mark>()

    collect { event ->

        when (event) {

            is Mark -> if (event.name in INLINE_FORMATTING_TAGS) {
                suppressedMarks += event
            } else {
                if (suppressedMarks.isNotEmpty()) suppressedMarks.flush()
                emit(event)
            }

            is Text -> if (!event.text.isHtmlBlank()) {
                suppressedMarks.flush()
                emit(event)
            } else if (suppressedMarks.isEmpty()) {
                // A standalone blank text node is an inter-element separator,
                // not the empty content of a formatting element — keep it so
                // the downstream whitespace normalizer can gate it (drop at a
                // block boundary, keep one space between inline content). Blank
                // text *inside* a pending formatting element is still absorbed
                // (the element may turn out empty).
                emit(event)
            }

            is Unmark -> if (suppressedMarks.isNotEmpty()) {
                // LIFO: this unmark closes the most recently suppressed mark.
                // removeFirst() would desync a still-open outer mark from its
                // unmark when a nested empty element closes inside content.
                suppressedMarks.removeLast()
            } else {
                emit(event)
            }

        }

    }

}

context(collector: FlowCollector<SemanticEvent.Mark>)
private suspend fun ArrayDeque<SemanticEvent.Mark>.flush() {
    forEach { collector.emit(it) }
    clear()
}
