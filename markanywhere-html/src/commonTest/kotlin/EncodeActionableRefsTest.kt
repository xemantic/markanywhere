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
import com.xemantic.kotlin.test.sameAs
import com.xemantic.markanywhere.dump.AccessibilityAnnotations
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class EncodeActionableRefsTest {

    // --- ActionableRef.encode / decode --------------------------------------

    @Test
    fun `should encode ref and href into the ref scheme destination`() {
        // when
        val encoded = ActionableRef.encode("42", "/menu")

        // then
        encoded sameAs "ref:42:/menu"
    }

    @Test
    fun `should round-trip a relative href`() {
        // when
        val decoded = ActionableRef.decode(ActionableRef.encode("42", "/menu"))

        // then
        assert(decoded == ("42" to "/menu"))
    }

    @Test
    fun `should round-trip an absolute href keeping its own colons intact`() {
        // given — the href itself carries colons, so decode must split on the
        // first two ':' only
        val href = "https://example.com:8080/path?q=1"

        // when
        val decoded = ActionableRef.decode(ActionableRef.encode("7", href))

        // then
        assert(decoded == ("7" to href))
    }

    @Test
    fun `should round-trip a fragment-only href`() {
        // when
        val decoded = ActionableRef.decode(ActionableRef.encode("3", "#"))

        // then
        assert(decoded == ("3" to "#"))
    }

    @Test
    fun `should round-trip an empty href`() {
        // when
        val decoded = ActionableRef.decode(ActionableRef.encode("9", ""))

        // then
        assert(decoded == ("9" to ""))
    }

    @Test
    fun `should decode to null when the value is not ref-encoded`() {
        // when
        val decoded = ActionableRef.decode("https://example.com")

        // then
        assert(decoded == null)
    }

    @Test
    fun `should decode to null when the second separator is missing`() {
        // when — has the scheme but no ':' after the id
        val decoded = ActionableRef.decode("ref:42")

        // then
        assert(decoded == null)
    }

    @Test
    fun `should expose the expected scheme and attribute names`() {
        // then
        ActionableRef.SCHEME sameAs "ref"
        ActionableRef.ATTRIBUTE sameAs "ref"
    }

    // --- encodeActionableRefs transformer -----------------------------------

    @Test
    fun `should fold a link's ref into the destination and drop the dom ref`() = runTest {
        // given
        val input = semanticEvents {
            "a"(AccessibilityAnnotations.REF to "42", "href" to "/menu") { +"Menu" }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "a"("href" to "ref:42:/menu") { +"Menu" }
        }
    }

    @Test
    fun `should encode a hrefless link with an empty original href`() = runTest {
        // given — a JS control with no href, only a ref
        val input = semanticEvents {
            "a"(AccessibilityAnnotations.REF to "5") { +"Toggle" }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "a"("href" to "ref:5:") { +"Toggle" }
        }
    }

    @Test
    fun `should keep a block-wrapping link's ref as an attribute with the real href`() = runTest {
        // given — a link wrapping a heading renders as a raw <a> tag, which can
        // carry attributes, so the ref stays an attribute and the href is real
        val input = semanticEvents {
            "a"(AccessibilityAnnotations.REF to "9", "href" to "/live") {
                +"LIVE"
                "h2" { +"Headline" }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "a"("href" to "/live", "ref" to "9") {
                +"LIVE"
                "h2" { +"Headline" }
            }
        }
    }

    @Test
    fun `should keep an image-only link inline as the scheme destination`() = runTest {
        // given — `<img>` is NOT block content, so a link wrapping only an image
        // stays an inline Markdown link (`[![alt](src)](url)`); its ref folds
        // into the destination, it does not become a raw tag. Guards the
        // deliberate exclusion of `img` from LINK_BLOCK_CONTENT_TAGS.
        val input = semanticEvents {
            "a"(AccessibilityAnnotations.REF to "8", "href" to "/live") {
                "img"("src" to "/p.jpg", "alt" to "Photo") {}
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "a"("href" to "ref:8:/live") {
                "img"("src" to "/p.jpg", "alt" to "Photo") {}
            }
        }
    }

    @Test
    fun `should detect block content nested deeper inside the link subtree`() = runTest {
        // given — the block-vs-inline decision scans the whole `<a>` subtree, not
        // just its direct children, so a heading wrapped in an inline `span`
        // still forces the raw-tag (attribute) form.
        val input = semanticEvents {
            "a"(AccessibilityAnnotations.REF to "9", "href" to "/x") {
                "span" {
                    "h2" { +"Deep" }
                }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "a"("href" to "/x", "ref" to "9") {
                "span" {
                    "h2" { +"Deep" }
                }
            }
        }
    }

    @Test
    fun `should encode consecutive ref links independently`() = runTest {
        // given — the link buffer must reset between links: an inline one and a
        // block-wrapping one in sequence each get their own encoding.
        val input = semanticEvents {
            "a"(AccessibilityAnnotations.REF to "1", "href" to "/a") { +"Inline" }
            "a"(AccessibilityAnnotations.REF to "2", "href" to "/b") {
                "h2" { +"Block" }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "a"("href" to "ref:1:/a") { +"Inline" }
            "a"("href" to "/b", "ref" to "2") {
                "h2" { +"Block" }
            }
        }
    }

    @Test
    fun `should encode a ref-bearing control nested inside a block-wrapping link`() = runTest {
        // given — the link pre-pass re-emits a block link's subtree unchanged;
        // a ref-bearing control inside it is still encoded (to the short
        // attribute) by the downstream non-link pass.
        val input = semanticEvents {
            "a"(AccessibilityAnnotations.REF to "9", "href" to "/x") {
                "h2" { +"Title" }
                "button"(AccessibilityAnnotations.REF to "10") { +"Act" }
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "a"("href" to "/x", "ref" to "9") {
                "h2" { +"Title" }
                "button"("ref" to "10") { +"Act" }
            }
        }
    }

    @Test
    fun `should leave a link without a ref untouched`() = runTest {
        // given
        val input = semanticEvents {
            "a"("href" to "/plain") { +"Plain" }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "a"("href" to "/plain") { +"Plain" }
        }
    }

    @Test
    fun `should rename a hrefless control's ref to the short attribute`() = runTest {
        // given
        val input = semanticEvents {
            "button"(AccessibilityAnnotations.REF to "13", "type" to "submit") { +"Send" }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "button"("type" to "submit", "ref" to "13") { +"Send" }
        }
    }

    @Test
    fun `should pass through elements and text that carry no ref`() = runTest {
        // given
        val input = semanticEvents {
            "p" {
                +"before "
                "span"("class" to "x") { +"plain" }
                +" after"
            }
        }

        // when
        val output = input.encodeActionableRefs()

        // then
        output sameAs semanticEvents {
            "p" {
                +"before "
                "span"("class" to "x") { +"plain" }
                +" after"
            }
        }
    }

}