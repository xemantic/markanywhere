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
import com.xemantic.markanywhere.dump.AccessibilityAnnotations
import com.xemantic.markanywhere.dump.CaptureFormat
import com.xemantic.markanywhere.dump.UnrecordedDisplay
import com.xemantic.markanywhere.dump.unrecordedDisplayOfChildren

/**
 * What an element's display is, as far as the stream tells — the reading of an
 * *absent* [AccessibilityAnnotations.DISPLAY] that `separateUnwrappedBlocks`,
 * `simplifyHtml` and `dropHtmlStructuralWhitespace` share.
 *
 * One read stays on the raw annotation, and is correct only because it
 * reads a *present* value: `applyAccessibility` (a recorded `none` drops
 * the subtree, which a layout capture always records on the root of a hidden
 * region — inside an svg too; a missing box is inherited through
 * `hasOwnLayoutBox`, matching the box-less `visibility` rule the capture
 * applies to svg parts; its visibility gap reads line edges through the
 * raw `isInlineLevel()`). And one reading still differs
 * between the operators, for unmarked dumps only:
 * [CapturedDisplay.Unrecorded] crosses no boundary in
 * `separateUnwrappedBlocks` but is read by the UA default in the whitespace
 * normalizer, which runs after `simplifyHtml` has added displayless elements
 * of its own — kept as it was, so the committed dumps render unchanged.
 */
internal sealed interface CapturedDisplay {

    /**
     * The display is known: recorded on the element, or implied by the region
     * of a [CaptureFormat.LAYOUT] capture it sits in — `none` below a `none`
     * root, `inline` inside an `<svg>` (see [UnrecordedDisplay]).
     */
    data class Known(val value: String) : CapturedDisplay

    /**
     * Nothing was recorded and nothing is implied — a stream never captured,
     * the in-page DOM walk, or a position a layout capture leaves out
     * (the `<head>`) or a filter consumed the verdict of: the UA stylesheet's
     * default for the tag is the best reading.
     */
    data object UaDefault : CapturedDisplay

    /**
     * Left out by an unmarked dump captured before every display was
     * recorded, once an annotation has shown the stream is one: a plain
     * `block`, or a `display: contents` the dump could not tell from it.
     */
    data object Unrecorded : CapturedDisplay

}

/**
 * Reads the display of each element of a stream, opened and closed in stream
 * order ([open] / [close]): from the [AccessibilityAnnotations.CAPTURE] marker
 * on the stream's first mark, it knows what an absent annotation means (see
 * [CaptureFormat]).
 *
 * An unmarked stream keeps the inference every dump committed before the
 * marker is rendered by: it counts as a capture from the first element
 * carrying an annotation only the CDP capture writes ([showsCapture]), an
 * absent display then being [CapturedDisplay.Unrecorded], and before that
 * [CapturedDisplay.UaDefault].
 */
internal class CapturedDisplayReader {

    private var started = false
    private var format: CaptureFormat? = null
    private var latched = false

    // per open element, the unrecorded region its children sit in
    private val regions = ArrayDeque<UnrecordedDisplay?>()

    /**
     * Whether the stream, as far as it has been read, is a layout capture —
     * one recording which elements the browser laid out, so that, once
     * `applyAccessibility` dropped the hidden ones, a surviving element was
     * shown.
     */
    val isLayoutCapture: Boolean
        get() = format == LAYOUT || (format == null && latched)

    /** The display of the element most recently opened. */
    var current: CapturedDisplay = UaDefault
        private set

    /** Opens [mark], returning its display. */
    fun open(mark: SemanticEvent.Mark): CapturedDisplay {
        if (!started) {
            started = true
            format = CaptureFormat.of(mark[AccessibilityAnnotations.CAPTURE])
        }
        if (format == null && !latched && mark.showsCapture()) latched = true
        val recorded = mark[AccessibilityAnnotations.DISPLAY]
        val region = regions.lastOrNull()
        current = when {
            recorded != null -> CapturedDisplay.Known(recorded)
            region == NO_BOX -> CapturedDisplay.Known("none")
            region == GRAPHIC -> CapturedDisplay.Known("inline")
            format == null && latched -> Unrecorded
            else -> UaDefault
        }
        regions.addLast(
            if (format == LAYOUT) unrecordedDisplayOfChildren(mark.name, recorded, region)
            else null
        )
        return current
    }

    /** Closes the element most recently opened. */
    fun close() {
        regions.removeLastOrNull()
    }

}

// The annotations only the CDP capture writes. [AccessibilityAnnotations.REDACTED]
// and [AccessibilityAnnotations.SCROLLABLE] are not among them: the in-page JS
// walker writes them too (the two producers share `captureFormControl` and
// `isUserScrollable`), while recording no display at all — taking one for a
// capture switched a JS dump's display fallback halfway down the page.
private val CDP_CAPTURE_ANNOTATIONS =
    AccessibilityAnnotations.ALL + AccessibilityAnnotations.REF - AccessibilityAnnotations.SCROLLABLE

/**
 * Whether this element shows that an *unmarked* stream is a CDP capture (see
 * [CapturedDisplayReader]): a dump captured before every display was recorded
 * left a plain `block` out, so its first annotated element is what shows it.
 */
internal fun SemanticEvent.Mark.showsCapture(): Boolean =
    attributes.keys.any { it in CDP_CAPTURE_ANNOTATIONS }
