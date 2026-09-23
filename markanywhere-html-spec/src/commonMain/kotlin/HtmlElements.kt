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
 * The HTML void elements: their content model is empty, so they have no
 * closing tag (WHATWG HTML §13.1.2).
 *
 * Includes the obsolete `keygen` and `param`, which the HTML parser still
 * treats as void, so real-world markup using them stays balanced.
 */
public val HTML_VOID_ELEMENTS: Set<String> = setOf(
    "area", "base", "br", "col", "embed", "hr", "img", "input",
    "keygen", "link", "meta", "param", "source", "track", "wbr"
)

/**
 * The HTML raw text elements, whose content is not parsed as markup, so it is
 * neither escaped nor entity-decoded (WHATWG HTML §13.1.2).
 */
public val HTML_RAW_TEXT_ELEMENTS: Set<String> = setOf("script", "style")
