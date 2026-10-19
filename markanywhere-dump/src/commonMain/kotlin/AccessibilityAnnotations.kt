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
 * Reserved [com.xemantic.markanywhere.SemanticEvent.Mark] attribute names
 * carrying the browser's accessibility verdicts captured during a DOM dump.
 *
 * The capture (`PageSession` in `markanywhere-browse`) is deliberately
 * **lossless**: it walks the full DOM and records these verdicts *as data*
 * rather than acting on them, so the
 * same dump can be replayed against different filtering policies without
 * re-capturing. The accessibility-driven filtering itself — dropping hidden
 * subtrees, unwrapping layout tables — lives downstream (see
 * `Flow<SemanticEvent>.applyAccessibility()` in `markanywhere-html`), which
 * reads these annotations and then strips them from the surviving stream.
 *
 * Raw signals (the computed role string, the hiding property) are recorded
 * rather than cooked booleans so policy can evolve — e.g. later treating
 * `visibility:hidden` differently from `display:none` — without a re-capture.
 */
public object AccessibilityAnnotations {

    /**
     * The browser's computed accessibility role, recorded on `<table>` so a
     * consumer can tell a *data* table (role `table` / `grid` / `treegrid`)
     * from a *layout* table (any other role, e.g. `LayoutTable`) — Blink's own
     * verdict, rather than a reimplementation of its `IsDataTable()` heuristic.
     */
    public const val ROLE: String = "data-markanywhere-role"

    /**
     * The format of the capture, stamped on the root mark of the stream alone
     * (the `<html>` of a page) — what an *absent* [DISPLAY] means anywhere in
     * it depends on it. See [CaptureFormat] for the values, and an unmarked
     * stream.
     */
    public const val CAPTURE: String = "data-markanywhere-capture"

    /**
     * The element's computed `display`, so a consumer can tell inline-level
     * content (`inline`, `inline-block`, …), whose flanking whitespace is a
     * rendered separator, from block-level content, whose flanking whitespace
     * is structural. Two values describe an element laying out no box:
     * `contents` (`display:contents` — the box is transparent and its
     * children's display is what counts) and `none` (`display:none`, nothing
     * in the subtree rendered) — hidden, drop the subtree.
     *
     * Which elements carry it, and so what its absence means, is set by the
     * stream's [CAPTURE] format — in a [CaptureFormat.LAYOUT] capture every
     * HTML element outside the two regions [unrecordedDisplayOfChildren]
     * names, the plain `block` default included.
     */
    public const val DISPLAY: String = "data-markanywhere-display"

    /**
     * Present with value `hidden` when the element computes to
     * `visibility:hidden` — its own content is invisible, but unlike
     * [DISPLAY] `none` not necessarily its subtree: `visibility` is inherited
     * and a descendant may set it back to `visible`, so each descendant laying
     * out a box carries its own verdict. One laying out none (`contents`)
     * carries one only where its own value differs from the one it inherits —
     * `hidden`, or `visible` set back under a hidden ancestor — and otherwise
     * inherits its parent's.
     */
    public const val VISIBILITY: String = "data-markanywhere-visibility"

    /**
     * Present with value `true` on an `<img>` whose accessibility node Blink
     * marks **ignored** — i.e. the browser keeps the image out of the
     * accessibility tree, its own verdict that the image is decorative /
     * redundant (empty `alt`, `role="presentation"`, a lazy-load placeholder
     * superseded by a sibling, …). A downstream consumer can drop such an image
     * without re-deriving "is this decorative?" from `alt`/`role` heuristics.
     * Recorded only for images — most other ignored nodes (generic `<div>`s, …)
     * are structurally meaningful and must not be dropped.
     */
    public const val IGNORED: String = "data-markanywhere-ignored"

    /**
     * A short, dense ref stamped on every *actionable* element (a focusable
     * control / link, or one whose accessibility role is interactive) so a
     * downstream LLM that read the dump can name an element by its ref and have
     * the session resolve it back to a live element to act on. Unlike the
     * verdict annotations above this one is **not** consumed-and-stripped — it
     * survives to the LLM-facing output (renamed to a shorter token by
     * `simplifyHtml`'s `renameAttributes`), which is why it is deliberately
     * absent from [ALL]. See `PageSession` in `markanywhere-browse`.
     */
    public const val REF: String = "data-markanywhere-ref"

    /**
     * Present on a form control holding a secret (a password, a card number, a
     * one-time code — see [captureFormControl]) whose value the capture
     * left out, with value `filled` or `empty`: an agent that typed into the
     * field can tell its typing landed without the dump carrying what was
     * typed. Like [REF] it is meant for the LLM-facing output, so it is not in
     * [ALL].
     */
    public const val REDACTED: String = "data-markanywhere-redacted"

    /**
     * All reserved verdict annotation names — strip these before output.
     * Deliberately excludes [REF], which is renamed and kept (see its doc).
     */
    public val ALL: Set<String> = setOf(ROLE, DISPLAY, VISIBILITY, IGNORED)

}