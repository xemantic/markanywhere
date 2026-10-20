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

package com.xemantic.markanywhere.html.spec

/**
 * Whether a `hidden` attribute with this [value] puts its element in the
 * *hidden until found* state (WHATWG HTML §6.1, the `until-found` keyword,
 * matched ASCII case-insensitively) rather than the *hidden* state every other
 * value maps to.
 *
 * Content hidden until found is still page content: the browser skips
 * rendering it (`content-visibility: hidden`) but find-in-page and fragment
 * navigation reveal it.
 */
public fun isHiddenUntilFound(value: String): Boolean =
    value.asciiLowercase() == "until-found"
