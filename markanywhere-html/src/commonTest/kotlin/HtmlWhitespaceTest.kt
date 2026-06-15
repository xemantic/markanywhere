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

import com.xemantic.kotlin.test.assert
import kotlin.test.Test

class HtmlWhitespaceTest {

    @Test
    fun `should recognize ASCII whitespace as HTML whitespace`() {
        // the WHATWG "ASCII whitespace" set: SPACE, TAB, LF, CR, FF
        assert(' '.isHtmlWhitespace())
        assert('\t'.isHtmlWhitespace())
        assert('\n'.isHtmlWhitespace())
        assert('\r'.isHtmlWhitespace())
        assert('\u000C'.isHtmlWhitespace())
    }

    @Test
    fun `should not treat NBSP or other Unicode spaces as HTML whitespace`() {
        // these are printable content in HTML, never structural whitespace
        assert(!' '.isHtmlWhitespace()) // NBSP
        assert(!' '.isHtmlWhitespace()) // narrow NBSP
        assert(!' '.isHtmlWhitespace()) // em space
        assert(!' '.isHtmlWhitespace()) // thin space
    }

    @Test
    fun `should not treat ordinary content as HTML whitespace`() {
        assert(!'a'.isHtmlWhitespace())
        assert(!'§'.isHtmlWhitespace())
    }

    @Test
    fun `an empty string should be HTML blank`() {
        assert("".isHtmlBlank())
    }

    @Test
    fun `a string of only ASCII whitespace should be HTML blank`() {
        assert(" \t\n\r\u000C".isHtmlBlank())
    }

    @Test
    fun `an NBSP-only string should not be HTML blank`() {
        // NBSP is content (e.g. the spaces in legal citations like `§ 823`),
        // so unlike kotlin's isBlank() it does not qualify as blank
        assert(!" ".isHtmlBlank())
        assert(" ".isBlank()) // contrast: kotlin's stdlib treats it as blank
    }

    @Test
    fun `a string with any content should not be HTML blank`() {
        assert(!"  a  ".isHtmlBlank())
        assert(!"text".isHtmlBlank())
    }

}