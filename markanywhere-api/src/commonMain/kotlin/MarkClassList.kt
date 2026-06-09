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

package com.xemantic.markanywhere

public val SemanticEvent.Mark.classList: List<String> get() =
    this["class"]
        ?.split(whitespaceRegex)
        ?.filter { it.isNotEmpty() }
        ?.distinct()
        ?: emptyList()

// ASCII whitespace per the HTML spec (space, tab, LF, FF, CR), deliberately
// not \s which matches different character sets on JVM and JS
private val whitespaceRegex = Regex("[ \t\n\\f\r]+")
