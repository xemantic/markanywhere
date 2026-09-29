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
 * This character with an ASCII upper alpha (`A`–`Z`) replaced by its lowercase
 * counterpart — the WHATWG Infra "ASCII lowercase", the folding HTML applies
 * wherever it matches names "ASCII case-insensitively" (element names,
 * `<meta name>`, URL schemes).
 *
 * Deliberately narrower than [Char.lowercaseChar]: every other character,
 * including non-ASCII letters, is returned unchanged.
 */
public fun Char.asciiLowercase(): Char =
    if (this in 'A'..'Z') this + ('a' - 'A') else this

/**
 * This string with every ASCII upper alpha replaced by its lowercase
 * counterpart (see [Char.asciiLowercase]) — unlike [String.lowercase], which
 * folds some non-ASCII letters into ASCII ones: `"\u212Aey".lowercase()`
 * (a Kelvin sign `K`, U+212A) is `"key"`, while this leaves it unchanged, so
 * it never matches a `key` name.
 */
public fun String.asciiLowercase(): String =
    if (none { it in 'A'..'Z' }) this else CharArray(length) { this[it].asciiLowercase() }.concatToString()
