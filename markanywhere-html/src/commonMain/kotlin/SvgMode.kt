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

/**
 * What the HTML pipeline does with an inline `<svg>`.
 *
 * The two answers serve genuinely different consumers, which is why this is a
 * choice and not a default: text for a model to read, or a document for a
 * person to look at.
 */
public enum class SvgMode {

    /**
     * Lift the accessible name out of the graphic and drop the vector data.
     *
     * A named `<svg>` becomes `![name]()` ([resolveInlineGraphics]), an
     * unlabelled control holding a nameless one gets
     * [GRAPHIC_PLACEHOLDER_ALT] ([labelActionableElements]), and the rest is
     * discarded as decorative. This is what an LLM-facing rendering wants: on
     * the pages captured in this repository the vector subtrees are 5-13% of
     * all events and up to 110 KB per page, none of it readable.
     */
    RESOLVE,

    /**
     * Keep every `<svg>` subtree tagged, its own attributes intact — minus a
     * `script`, an `aria-hidden` part and the capture's annotations, which
     * follow the same rules as everywhere else in the pipeline.
     *
     * For a simplified *reader* rather than a Markdown rendering for a model —
     * logos, diagrams and icons survive and can be displayed. [resolveIcons]
     * still maps icon fonts to emoji, and everything else in the pipeline is
     * unchanged.
     */
    PRESERVE,
}
