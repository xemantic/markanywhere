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

package com.xemantic.markanywhere.parse

import com.xemantic.kotlin.test.have
import com.xemantic.kotlin.test.should
import kotlin.test.Test

/**
 * Trailing quote stripping for GFM §6.9 extended autolinks.
 *
 * DIVERGENCE: strict GFM only excludes trailing `?!.,:*_~` punctuation, so a URL
 * wrapped in quotes (`www.x.de"`) keeps the quote in the link. We additionally
 * strip a trailing straight quote (`"` / `'`) — these never legitimately end a
 * URL in prose and keeping them produces a broken link (e.g. openJur's
 * `…datenschutz"`).
 */
class AutolinkTrailingQuoteTest {

    @Test
    fun `should strip a trailing double quote from a www autolink`() {
        // when
        val match = detectAutolink("www.example.com/path\"", null)

        // then
        match should {
            have(linkText == "www.example.com/path")
            have(href == "http://www.example.com/path")
            have(suffix == "\"")
        }
    }

    @Test
    fun `should strip a trailing single quote from a scheme autolink`() {
        // when
        val match = detectAutolink("https://example.com/a'", null)

        // then
        match should {
            have(linkText == "https://example.com/a")
            have(href == "https://example.com/a")
            have(suffix == "'")
        }
    }

    @Test
    fun `should strip a trailing quote together with paren and period`() {
        // given — the openJur shape: a URL closing a parenthetical quote
        // when
        val match = detectAutolink("www.deutschepost.de/datenschutz\").", null)

        // then — quote, unbalanced paren and period are all trailing text
        match should {
            have(linkText == "www.deutschepost.de/datenschutz")
            have(href == "http://www.deutschepost.de/datenschutz")
            have(suffix == "\").")
        }
    }

    @Test
    fun `should keep a quote in the interior of a url`() {
        // given — a quote that is not trailing must remain part of the link
        // when
        val match = detectAutolink("www.example.com/a\"b", null)

        // then
        match should {
            have(linkText == "www.example.com/a\"b")
        }
    }

}
