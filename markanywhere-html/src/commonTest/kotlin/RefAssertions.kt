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

/**
 * Asserts that [markdown] carries no actionable-ref residue on any of its three
 * surfaces: the [ActionableRef.SCHEME] pseudo-scheme (in a link destination or
 * as bare text), the short [ActionableRef.ATTRIBUTE] on a raw tag, and the raw
 * dump attribute ([AccessibilityAnnotations.REF]) itself.
 *
 * The scheme guard is deliberately broad — it would also fire on literal `ref:`
 * body text. A loud false failure on a future fixture beats a narrower guard
 * silently missing a leaked scheme.
 */
internal fun assertNoActionableRefs(markdown: String) {
    assert("${ActionableRef.SCHEME}:" !in markdown)
    // The leading space matches a ref= attribute but not href=.
    assert(" ${ActionableRef.ATTRIBUTE}=" !in markdown)
    assert(AccessibilityAnnotations.REF !in markdown)
}
