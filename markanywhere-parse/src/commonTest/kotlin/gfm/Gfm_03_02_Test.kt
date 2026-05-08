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

package com.xemantic.markanywhere.parse.gfm

/**
 * Tests for GFM Section 03.02 — Container blocks and leaf blocks.
 *
 * Reference: https://github.github.com/gfm/#container-blocks-and-leaf-blocks
 *
 * The section is purely taxonomy: it defines the container-vs-leaf
 * distinction and notes that containers may nest while leaves may not.
 * It contains no numbered examples, and every concrete behaviour described
 * in the prose is exercised transitively elsewhere in the suite:
 *
 *  - Leaf-block rules → Section 04.x (thematic breaks, headings, code blocks,
 *    HTML blocks, paragraphs, blank lines).
 *  - Container-block nesting → Section 05.x (block quotes, list items, lists),
 *    with further interaction coverage in Section 06.x once inlines are in play.
 *
 * This file is therefore an intentional placeholder so the section is
 * represented in the test suite and reviewers can see at a glance that it
 * was considered. Add tests here only if a prose-level behaviour turns out
 * to lack explicit coverage downstream.
 */
@Suppress("ClassName")
class Gfm_03_02_Test
