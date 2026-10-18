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

import com.xemantic.kotlin.test.sameAs
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.render.renderMarkdown
import com.xemantic.markanywhere.test.sameAs
import com.xemantic.markanywhere.SemanticEvent.Mark
import com.xemantic.markanywhere.SemanticEvent.Unmark
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ResolveLazyImagesTest {

    @Test
    fun `should replace a data placeholder with data-src`() = runTest {
        // given — the 1x1 transparent GIF a lazy-loading library keeps in
        // `src` until the image scrolls into view
        val input = semanticEvents(tagged = true) {
            "img"(
                "src" to TRANSPARENT_GIF,
                "data-src" to "/flavors/dastardly-mash.png",
                "alt" to "Dastardly Mash",
            ) { }
        }

        // when
        val output = input.resolveLazyImages()

        // then
        output sameAs semanticEvents(tagged = true) {
            "img"(
                "src" to "/flavors/dastardly-mash.png",
                "data-src" to "/flavors/dastardly-mash.png",
                "alt" to "Dastardly Mash",
            ) { }
        }
    }

    @Test
    fun `should fall back to data-lazy-src and data-original`() = runTest {
        // when
        val output = semanticEvents(tagged = true) {
            "img"("src" to TRANSPARENT_GIF, "data-lazy-src" to "a.jpg") { }
            "img"("src" to TRANSPARENT_GIF, "data-original" to "b.jpg") { }
        }.resolveLazyImages()

        // then
        output sameAs semanticEvents(tagged = true) {
            "img"("src" to "a.jpg", "data-lazy-src" to "a.jpg") { }
            "img"("src" to "b.jpg", "data-original" to "b.jpg") { }
        }
    }

    @Test
    fun `should resolve an image with no src at all`() = runTest {
        // when — some libraries leave `src` out entirely until load
        val output = semanticEvents(tagged = true) {
            "img"("data-src" to "a.jpg", "alt" to "A") { }
        }.resolveLazyImages()

        // then
        output sameAs semanticEvents(tagged = true) {
            "img"("data-src" to "a.jpg", "alt" to "A", "src" to "a.jpg") { }
        }
    }

    @Test
    fun `should pick the widest srcset candidate`() = runTest {
        // when — lazysizes after the swap: `srcset` holds the real candidates,
        // `src` still the placeholder
        val output = semanticEvents(tagged = true) {
            "img"(
                "src" to TRANSPARENT_GIF,
                "srcset" to "small.jpg 240w, large.jpg 960w,medium.jpg 480w",
            ) { }
        }.resolveLazyImages()

        // then
        output sameAs semanticEvents(tagged = true) {
            "img"(
                "src" to "large.jpg",
                "srcset" to "small.jpg 240w, large.jpg 960w,medium.jpg 480w",
            ) { }
        }
    }

    @Test
    fun `should pick the highest density srcset candidate`() = runTest {
        // when — a bare URL is an implicit 1x candidate
        val output = semanticEvents(tagged = true) {
            "img"("data-srcset" to "a.jpg, b.jpg 3x, c.jpg 2x") { }
        }.resolveLazyImages()

        // then
        output sameAs semanticEvents(tagged = true) {
            "img"("data-srcset" to "a.jpg, b.jpg 3x, c.jpg 2x", "src" to "b.jpg") { }
        }
    }

    @Test
    fun `should keep a comma inside a srcset candidate url`() = runTest {
        // when — commas are legal inside a URL; only a comma after whitespace
        // (or trailing the URL) separates candidates
        val output = semanticEvents(tagged = true) {
            "img"("srcset" to "img.php?size=1,2 100w, img.php?size=3,4 200w") { }
        }.resolveLazyImages()

        // then
        output sameAs semanticEvents(tagged = true) {
            "img"(
                "srcset" to "img.php?size=1,2 100w, img.php?size=3,4 200w",
                "src" to "img.php?size=3,4",
            ) { }
        }
    }

    @Test
    fun `should prefer data-src over srcset`() = runTest {
        // when
        val output = semanticEvents(tagged = true) {
            "img"("src" to TRANSPARENT_GIF, "srcset" to TRANSPARENT_GIF, "data-src" to "real.jpg") { }
        }.resolveLazyImages()

        // then
        output sameAs semanticEvents(tagged = true) {
            "img"("src" to "real.jpg", "srcset" to TRANSPARENT_GIF, "data-src" to "real.jpg") { }
        }
    }

    @Test
    fun `should skip data placeholders among the candidates`() = runTest {
        // when — a placeholder can sit in the lazy attributes too
        val output = semanticEvents(tagged = true) {
            "img"(
                "src" to TRANSPARENT_GIF,
                "data-src" to TRANSPARENT_GIF,
                "srcset" to "$TRANSPARENT_GIF 1x, real.jpg 2x",
            ) { }
        }.resolveLazyImages()

        // then
        output sameAs semanticEvents(tagged = true) {
            "img"(
                "src" to "real.jpg",
                "data-src" to TRANSPARENT_GIF,
                "srcset" to "$TRANSPARENT_GIF 1x, real.jpg 2x",
            ) { }
        }
    }

    @Test
    fun `should resolve from the source of an enclosing picture`() = runTest {
        // when — the real candidates sit on the <source>, the <img> only
        // carries the placeholder
        val output = semanticEvents(tagged = true) {
            "picture" {
                "source"("data-srcset" to "cat.webp 1x, cat@2x.webp 2x", "type" to "image/webp") { }
                "img"("src" to TRANSPARENT_GIF, "alt" to "A cat") { }
            }
            "img"("src" to TRANSPARENT_GIF, "alt" to "outside") { }
        }.resolveLazyImages()

        // then — the picture's source does not leak to a later image
        output sameAs semanticEvents(tagged = true) {
            "picture" {
                "source"("data-srcset" to "cat.webp 1x, cat@2x.webp 2x", "type" to "image/webp") { }
                "img"("src" to "cat@2x.webp", "alt" to "A cat") { }
            }
            "img"("alt" to "outside") { }
        }
    }

    @Test
    fun `should drop a data src that has nothing to replace it`() = runTest {
        // when — an inline data: image carries ~kilobytes of base64 and no
        // information a reader can use; the alt text is what remains
        val output = semanticEvents(tagged = true) {
            "img"("src" to "data:image/png;base64,iVBORw0KGgo=", "alt" to "Video source site") { }
        }.resolveLazyImages()

        // then
        output sameAs semanticEvents(tagged = true) {
            "img"("alt" to "Video source site") { }
        }
    }

    @Test
    fun `should drop an image left with neither src nor alt`() = runTest {
        // when — `![]()` tells a reader nothing; inside a control,
        // labelActionableElements has already labelled it from this image
        val output = semanticEvents(tagged = true) {
            "p" {
                +"a"
                "img"("src" to "data:image/svg+xml;base64,PHN2Zz4=", "alt" to " ") { }
                "img" { }
                "img"("src" to "") { }
                +"b"
            }
        }.resolveLazyImages()

        // then
        output sameAs semanticEvents(tagged = true) {
            "p" {
                +"a"
                +"b"
            }
        }
    }

    @Test
    fun `should keep data uri images when preserving graphics`() = runTest {
        // when — SvgMode.PRESERVE renders for a reader, who sees an inline
        // image whatever its format; a placeholder with a real candidate is
        // still resolved
        val output = semanticEvents(tagged = true) {
            "img"("src" to "data:image/svg+xml,%3Csvg%3E", "alt" to "Copy") { }
            "img"("src" to "data:image/png;base64,iVBORw0KGgo=") { }
            "img"("src" to TRANSPARENT_GIF, "data-src" to "real.jpg") { }
        }.resolveLazyImages(SvgMode.PRESERVE)

        // then
        output sameAs semanticEvents(tagged = true) {
            "img"("src" to "data:image/svg+xml,%3Csvg%3E", "alt" to "Copy") { }
            "img"("src" to "data:image/png;base64,iVBORw0KGgo=") { }
            "img"("src" to "real.jpg", "data-src" to "real.jpg") { }
        }
    }

    @Test
    fun `should prefer data-srcset over srcset for a placeholder`() = runTest {
        // when — the placeholder can be a real-URL blank image in `srcset`,
        // while `data-srcset` holds what the library will swap in
        val output = semanticEvents(tagged = true) {
            "img"(
                "src" to TRANSPARENT_GIF,
                "srcset" to "blank.gif",
                "data-srcset" to "a-480.jpg 480w, a-960.jpg 960w",
            ) { }
            "picture" {
                "source"("srcset" to "blank.gif", "data-srcset" to "b.webp 2x") { }
                "img"("src" to TRANSPARENT_GIF) { }
            }
        }.resolveLazyImages()

        // then
        output sameAs semanticEvents(tagged = true) {
            "img"(
                "src" to "a-960.jpg",
                "srcset" to "blank.gif",
                "data-srcset" to "a-480.jpg 480w, a-960.jpg 960w",
            ) { }
            "picture" {
                "source"("srcset" to "blank.gif", "data-srcset" to "b.webp 2x") { }
                "img"("src" to "b.webp") { }
            }
        }
    }

    @Test
    fun `should not leak the source of an unclosed picture`() = runTest {
        // given — a truncated capture: the <picture> never closes, its parent does
        val input = flowOf(
            Mark("div", isTagged = true),
            Mark("picture", isTagged = true),
            Mark("source", isTagged = true, attributes = mapOf("srcset" to "cat.webp")),
            Unmark("source", isTagged = true),
            Unmark("div", isTagged = true),
            Mark("img", isTagged = true, attributes = mapOf("src" to TRANSPARENT_GIF, "alt" to "later")),
            Unmark("img", isTagged = true),
        )

        // when
        val output = input.resolveLazyImages()

        // then
        output sameAs flowOf(
            Mark("div", isTagged = true),
            Mark("picture", isTagged = true),
            Mark("source", isTagged = true, attributes = mapOf("srcset" to "cat.webp")),
            Unmark("source", isTagged = true),
            Unmark("div", isTagged = true),
            Mark("img", isTagged = true, attributes = mapOf("alt" to "later")),
            Unmark("img", isTagged = true),
        )
    }

    @Test
    fun `should skip srcset candidates with invalid descriptors`() = runTest {
        // when — the HTML standard drops a candidate whose width is not a
        // positive integer or whose density is not a positive finite number
        val output = semanticEvents(tagged = true) {
            "img"("srcset" to "neg.jpg -500w, zero.jpg 0w, huge.jpg 99999999999w, plus.jpg +900w, ok.jpg 100w") { }
            "img"("srcset" to "inf.jpg Infinityx, nan.jpg NaNx, neg.jpg -3x, zero.jpg 0x, ok.jpg 2x") { }
            "img"("srcset" to "both.jpg 100w 2x, ok.jpg") { }
        }.resolveLazyImages()

        // then
        output sameAs semanticEvents(tagged = true) {
            "img"("srcset" to "neg.jpg -500w, zero.jpg 0w, huge.jpg 99999999999w, plus.jpg +900w, ok.jpg 100w", "src" to "ok.jpg") { }
            "img"("srcset" to "inf.jpg Infinityx, nan.jpg NaNx, neg.jpg -3x, zero.jpg 0x, ok.jpg 2x", "src" to "ok.jpg") { }
            "img"("srcset" to "both.jpg 100w 2x, ok.jpg", "src" to "ok.jpg") { }
        }
    }

    @Test
    fun `should keep a real src untouched`() = runTest {
        // given — native lazy loading and responsive images already carry the
        // real URL in `src`
        val input = semanticEvents(tagged = true) {
            "img"(
                "src" to "https://ichef.bbci.co.uk/news/480/a.jpg",
                "srcset" to "https://ichef.bbci.co.uk/news/240/a.jpg 240w, https://ichef.bbci.co.uk/news/960/a.jpg 960w",
                "loading" to "lazy",
                "alt" to "A",
            ) { }
        }

        // when
        val output = input.resolveLazyImages()

        // then
        output sameAs input
    }

    @Test
    fun `should render the real image of a lazy-loaded link card`() = runTest {
        // given — the shape reported in issue #88 (Ben & Jerry's flavor graveyard)
        val page = semanticEvents(tagged = true) {
            "body" {
                "a"("href" to "/flavors/dastardly-mash") {
                    "img"(
                        "src" to TRANSPARENT_GIF,
                        "data-src" to "/files/dastardly-mash.png",
                        "alt" to "Dastardly Mash",
                    ) { }
                    +" Dastardly Mash"
                }
            }
        }

        // when
        val markdown = page.transformHtmlToMarkdown().renderMarkdown()

        // then
        markdown sameAs "[![Dastardly Mash](/files/dastardly-mash.png) Dastardly Mash](/flavors/dastardly-mash)"
    }

}

private const val TRANSPARENT_GIF =
    "data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw=="
