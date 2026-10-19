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

package com.xemantic.markanywhere.dump

/**
 * The format of a captured event stream, stamped by its producer as
 * [AccessibilityAnnotations.CAPTURE] on the stream's root mark, so a reader
 * knows from the first event what an *absent* [AccessibilityAnnotations.DISPLAY]
 * means instead of inferring it from the annotations it happens to meet.
 *
 * An **unmarked** stream is either never captured (parsed, hand-built) or a
 * dump captured before the marker existed, which comes in two layout formats
 * the stream cannot tell apart: the first left a plain `block` out (and so
 * could not tell `display:contents` from it either), the second recorded a
 * display on every element. A reader of an unmarked stream keeps inferring
 * whether it is a capture from the first capture-only annotation it meets —
 * which is why the committed dumps, all unmarked, render as they always did.
 * A marker this reader does not know reads as unmarked too ([of]).
 *
 * Only the CDP capture stamps a marker. The in-page DOM walk
 * (`Element.toSemanticEvents()` in `markanywhere-js`) records no layout, which
 * is exactly how an unmarked stream without any CDP-only annotation reads —
 * and it can carry none, since both producers drop the ones a page authored
 * ([withoutCaptureAnnotations]). Stamping it would add nothing a reader uses,
 * while leaking into the DOM or HTML its events are rendered back into.
 *
 * @property marker the value stamped as [AccessibilityAnnotations.CAPTURE].
 */
public enum class CaptureFormat(public val marker: String) {

    /**
     * The CDP capture (`CapturePage` in `markanywhere-browse`), the third
     * layout format after the two unmarked ones: it records a display on every
     * element except in two regions where none can tell a reader anything, an
     * absent one there meaning (see [unrecordedDisplayOfChildren] and
     * [displayToRecord]):
     * - **below a `none` root** — `none` again: no descendant of an element
     *   laying out nothing lays out a box;
     * - **inside an `<svg>`**, up to a `<foreignObject>` — inline: the parts
     *   of a graphic are no CSS boxes and separate no text, whatever HTML
     *   element their name collides with (`title`, `a`). Only a laid-out part
     *   is left out: one laying out nothing (a `display:none` group, a
     *   `<title>`, a `<symbol>`'s content, an HTML child the svg never renders)
     *   is still recorded `none`, a root of the region above, so a reader of
     *   the raw annotation drops it as it always did.
     *
     * The `<head>` subtree is left unannotated as well (it is `display:none`
     * wholesale, and a hidden-subtree filter would drop the front matter). An
     * absent display anywhere else — the `<head>`, or a verdict a filter
     * consumed — is not known, and reads as the UA default of the tag.
     */
    LAYOUT("layout-3");

    public companion object {

        /**
         * The format a [marker] names, or `null` for an unmarked stream or a
         * marker this reader predates — both read as unmarked.
         */
        public fun of(marker: String?): CaptureFormat? = entries.firstOrNull { it.marker == marker }

    }

}

/** These root attributes, stamped with the [format] of the capture they open. */
public fun Map<String, String>.withCaptureFormat(
    format: CaptureFormat
): Map<String, String> = this + (AccessibilityAnnotations.CAPTURE to format.marker)

/**
 * A region of a [CaptureFormat.LAYOUT] capture where no display is recorded,
 * and what an absent one means there.
 */
public enum class UnrecordedDisplay {

    /** Below a `none` root: nothing lays out a box — read as `none`. */
    NO_BOX,

    /**
     * Inside an `<svg>`: the parts of a graphic are no CSS boxes — an absent
     * display reads as `inline`; a part laying out nothing is recorded `none`.
     */
    GRAPHIC,

}

/**
 * Where the children of an element sit in a [CaptureFormat.LAYOUT] capture:
 * the [UnrecordedDisplay] region whose display the capture leaves out, or
 * `null` where it records one. Shared by the capture deciding what to record
 * and every reader deciding what an absent display means, so the two cannot
 * drift apart.
 *
 * @param name the element's local name.
 * @param display the element's recorded display, `null` when none is.
 * @param inherited the region the element itself sits in.
 */
public fun unrecordedDisplayOfChildren(
    name: String,
    display: String?,
    inherited: UnrecordedDisplay?,
): UnrecordedDisplay? = when (inherited) {
    NO_BOX -> NO_BOX
    // an HTML integration point: its children are laid out by CSS again
    GRAPHIC -> when {
        display == "none" -> NO_BOX
        name == "foreignObject" -> null
        else -> GRAPHIC
    }
    null -> when {
        display == "none" -> NO_BOX
        name == "svg" -> GRAPHIC
        else -> null
    }
}

/**
 * The display a [CaptureFormat.LAYOUT] capture records for an element whose
 * computed one is [display] and which sits in the [inherited] region, or
 * `null` when it leaves it out: everything outside a region, nothing below a
 * `none` root, and inside an `<svg>` only a `none` — the verdict a reader of
 * the raw annotation must still see, to drop the hidden part.
 */
public fun displayToRecord(
    display: String,
    inherited: UnrecordedDisplay?,
): String? = when (inherited) {
    null -> display
    GRAPHIC -> display.takeIf { it == "none" }
    NO_BOX -> null
}

/**
 * These markup attributes without the capture's own reserved
 * `data-markanywhere-*` names: only a producer may write those — a page
 * authoring one could fake a layout box, a ref or a format marker that every
 * reader would believe. Both producers apply it to the markup they read.
 */
public fun Map<String, String>.withoutCaptureAnnotations(): Map<String, String> =
    if (keys.none { it.startsWith(CAPTURE_ANNOTATION_PREFIX) }) this
    else filterKeys { !it.startsWith(CAPTURE_ANNOTATION_PREFIX) }

private const val CAPTURE_ANNOTATION_PREFIX = "data-markanywhere-"
