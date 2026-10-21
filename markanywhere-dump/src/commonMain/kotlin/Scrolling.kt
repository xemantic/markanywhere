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
 * Whether a user can scroll this [element]'s content — the scroll container a
 * browser makes focusable, recorded as [AccessibilityAnnotations.SCROLLABLE]:
 * its content overflows it (the scroll size exceeds the client size) along an
 * axis whose computed `overflow` is `auto` or `scroll`. A scrollbar with
 * nothing to scroll, and overflow `hidden` or `clip`, make no such element;
 * neither does `<html>` or `<body>`, whose overflow scrolls the viewport.
 * Verified live: Chrome focuses exactly these elements and keeps the implicit
 * role of a `<ul role="presentation">` among them.
 *
 * Both producers decide it here, from what each reads: the CDP capture from
 * the snapshot's computed styles and DOM rects, the JS walker from
 * `getComputedStyle` and the element's own sizes.
 */
public fun isUserScrollable(
    element: String,
    overflowX: String?,
    overflowY: String?,
    scrollWidth: Int,
    scrollHeight: Int,
    clientWidth: Int,
    clientHeight: Int,
): Boolean = element !in VIEWPORT_SCROLLING_ELEMENTS && (
    (overflowX in USER_SCROLLABLE_OVERFLOW && scrollWidth > clientWidth) ||
        (overflowY in USER_SCROLLABLE_OVERFLOW && scrollHeight > clientHeight)
    )

private val USER_SCROLLABLE_OVERFLOW = setOf("auto", "scroll")

private val VIEWPORT_SCROLLING_ELEMENTS = setOf("html", "body")
