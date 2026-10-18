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

package com.xemantic.markanywhere.test

import com.xemantic.kotlin.test.sameAs
import kotlin.test.assertFailsWith

/**
 * Returns the known divergence of this string from [expected] as a
 * position-independent unified diff, so a test can pin it with `sameAs`:
 *
 * ```
 * // when
 * val divergence = reparsed.renderMarkdown().divergenceFrom(markdown)
 *
 * // then
 * divergence sameAs """
 *     --- expected
 *     +++ actual
 *     @@
 *     -foo www.example.com
 *     +foo [www.example.com](http://www.example.com)
 * """.trimIndent()
 * ```
 *
 * The diff is the one `sameAs` reports, normalized so that the pin fails only
 * when the *divergence* changes, not when the surrounding output moves:
 * - every hunk header (`@@ -18,12 +18,16 @@`) becomes a bare `@@` — its line
 *   numbers shift with any unrelated change above the hunk;
 * - every whitespace-only line becomes empty — a blank *context* line is a
 *   lone space in the diff, which an editor stripping trailing whitespace would
 *   otherwise silently break in the pinned string; write it as an empty line.
 *   This protects blank context lines only: a whitespace-only changed line
 *   (`-  ` / `+ `) still carries its whitespace after the prefix, and the
 *   pinned string must keep that trailing whitespace;
 * - when `sameAs` truncates a large diff (more than 100 changed lines), the
 *   `Expected: N lines` / `Actual: M lines` totals of its trailer are dropped —
 *   they count the whole strings and change with any unrelated line shift;
 * - trailing blank lines are dropped.
 *
 * Fails with an [AssertionError] when this string equals [expected], i.e. the
 * pinned divergence has been fixed — the test should then become a plain
 * `sameAs` fixpoint assertion.
 *
 * @param expected the string this one is known to diverge from.
 * @return the normalized unified diff from [expected] to this string.
 */
public fun String.divergenceFrom(expected: String): String {
    val failure = assertFailsWith<AssertionError>(
        "expected a divergence, but the strings are identical — " +
            "the divergence is fixed, replace this assertion with a plain sameAs"
    ) {
        this sameAs expected
    }
    return failure.message.orEmpty().normalizeDivergence()
}

private fun String.normalizeDivergence(): String {
    var truncated = false
    return lines()
        .mapNotNull { line ->
            when {
                line.startsWith("@@ ") -> "@@"
                line.isBlank() -> ""
                line.startsWith("Diff truncated: ") -> line.also { truncated = true }
                // the trailer's totals, never a diff line (those carry a prefix)
                truncated && (line.startsWith("Expected: ") || line.startsWith("Actual: ")) -> null
                else -> line
            }
        }
        .dropLastWhile { it.isEmpty() }
        .joinToString("\n")
}
