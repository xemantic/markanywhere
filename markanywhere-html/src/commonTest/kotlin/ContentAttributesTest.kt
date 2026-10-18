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
import com.xemantic.markanywhere.dump.AccessibilityAnnotations
import kotlin.test.Test

class ContentAttributesTest {

    @Test
    fun `should not count an empty attribute map as content`() {
        // given
        val attributes = emptyMap<String, String>()

        // when
        val carriesContent = attributes.carriesContentAttribute()

        // then
        assert(!carriesContent)
    }

    @Test
    fun `should not count the display annotation alone as content`() {
        // given
        val attributes = mapOf(AccessibilityAnnotations.DISPLAY to "block")

        // when
        val carriesContent = attributes.carriesContentAttribute()

        // then
        assert(!carriesContent)
    }

    @Test
    fun `should not count any contentless annotation alone as content`() {
        // given — every annotation the rule ignores, together
        val attributes = CONTENTLESS_ANNOTATIONS.associateWith { "x" }
        assert(attributes.isNotEmpty())

        // when
        val carriesContent = attributes.carriesContentAttribute()

        // then
        assert(!carriesContent)
    }

    @Test
    fun `should count an actionable ref as content`() {
        // given
        val attributes = mapOf(AccessibilityAnnotations.REF to "7")

        // when
        val carriesContent = attributes.carriesContentAttribute()

        // then
        assert(carriesContent)
    }

    @Test
    fun `should count a caller keep-attribute next to the display annotation as content`() {
        // given
        val attributes = mapOf(
            AccessibilityAnnotations.DISPLAY to "flex",
            "golemId" to "42"
        )

        // when
        val carriesContent = attributes.carriesContentAttribute()

        // then
        assert(carriesContent)
    }

}
