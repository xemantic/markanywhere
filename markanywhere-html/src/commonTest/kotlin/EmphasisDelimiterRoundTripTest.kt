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
import com.xemantic.markanywhere.parse.parse
import com.xemantic.markanywhere.render.renderMarkdown
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.intellij.lang.annotations.Language
import kotlin.test.Test

/**
 * Round-trip fixpoint guards for emphasis whose closing delimiter sits at the
 * very end of a link label, and for emphasis whose content carries a literal
 * delimiter. Both used to make the delimiter run *grow by one every round-trip*
 * — the unbounded `…)*` → `…)**` → `…)***` instability surfaced by the Brave
 * SERP dump, whose unsupported image-in-link puts a `*` from the image URL next
 * to a trailing `*` inside the link label.
 *
 * Two independent fixes are exercised:
 *  - parser: [com.xemantic.markanywhere.parse] closes the matching label-local
 *    emphasis frame with a closing delimiter run at the label's end instead of
 *    flushing it as literal content (see `closeLabelLocalEmphasisRun`);
 *  - renderer: [com.xemantic.markanywhere.render] backslash-escapes a literal
 *    delimiter inside an emphasis span so it does not re-pair with the span's
 *    own closing run.
 */
class EmphasisDelimiterRoundTripTest {

    // A Markdown string that is already canonical must be a stable fixpoint: a
    // parse + re-render reproduces it, and a second pass changes nothing.
    private suspend fun assertMarkdownFixpoint(
        @Language("markdown") markdown: String
    ) {
        // when
        val once = flowOf(markdown).parse().renderMarkdown()
        val twice = flowOf(once).parse().renderMarkdown()

        // then
        once sameAs markdown
        twice sameAs once
    }

    @Test
    fun `should round-trip strong spanning a whole link label`() = runTest {
        // given
        val markdown = "[**bold**](u)"
        // when
        val rendered = flowOf(markdown).parse().renderMarkdown()
        // then
        rendered sameAs markdown
    }

    @Test
    fun `should round-trip emphasis spanning a whole link label`() = runTest {
        // given
        val markdown = "[*em*](u)"
        // when
        val rendered = flowOf(markdown).parse().renderMarkdown()
        // then
        rendered sameAs markdown
    }

    @Test
    fun `should round-trip mark spanning a whole link label`() = runTest {
        // `mark` (`==`) is a boolean-flagged span whose closer resolves on the next
        // char — but at a label's end the `]` is not that char, so the closing `==`
        // leaked into the committed link as literal content (`<mark>mark</mark>==`)
        // and grew on every round-trip. flushInlineLabelClose must consume it.
        // given
        val markdown = "[==mark==](u)"
        // when
        val rendered = flowOf(markdown).parse().renderMarkdown()
        // then
        rendered sameAs markdown
    }

    @Test
    fun `should round-trip del spanning a whole link label`() = runTest {
        // given
        val markdown = "[~~del~~](u)"
        // when
        val rendered = flowOf(markdown).parse().renderMarkdown()
        // then
        rendered sameAs markdown
    }

    @Test
    fun `should round-trip superscript spanning a whole link label`() = runTest {
        // `sup` (`^`) resolves eagerly (no buffering), so its closer is consumed
        // mid-label and never leaks — locking that this stays a fixpoint.
        // given
        val markdown = "[^sup^](u)"
        // when
        val rendered = flowOf(markdown).parse().renderMarkdown()
        // then
        rendered sameAs markdown
    }

    @Test
    fun `should round-trip emphasis nested in a link label with a literal asterisk`() = runTest {
        // HTML-pipeline shape: an `<em>` *inside* a link label whose content holds a
        // literal `*`. The renderer must escape the inner `*` (`[*a\*b*](u)`) even
        // though it's label content — the parser's label-scoped close only resolves
        // a delimiter at the label's end, not in the middle. Unescaped, `[*a*b*](u)`
        // re-parses to the broken `<a><em>a</em>b*</a>`.
        // when
        val markdown = semanticEvents {
            "p" { "a"("href" to "u") { "em" { +"a*b" } } }
        }.renderMarkdown()
        // then
        markdown sameAs "[*a\\*b*](u)"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should round-trip strong nested in a link label with a literal asterisk`() = runTest {
        // Without escaping this grew unboundedly: `[**a*b**](u)` → `[**a*b****](u)`.
        // when
        val markdown = semanticEvents {
            "p" { "a"("href" to "u") { "strong" { +"a*b" } } }
        }.renderMarkdown()
        // then
        markdown sameAs "[**a\\*b**](u)"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should round-trip strong with a tagged child in a link label containing a literal asterisk`() = runTest {
        // A raw inline tag (`<b>`) between the `<strong>` and its text pushes a
        // TaggedInline frame; the label-scoped escaping must scan *past* it to the
        // strong below, else the `*` inside `<b>` is unescaped and re-pairs with the
        // strong on re-parse (`[**<b>a*b</b>**]` → `[**<b>a*b*</b>**]`, growing).
        // when
        val markdown = semanticEvents {
            "p" { "a"("href" to "u") { "strong" { tag("b") { +"a*b" } } } }
        }.renderMarkdown()
        // then
        markdown sameAs "[**<b>a\\*b</b>**](u)"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should round-trip del nested in a link label with a literal tilde`() = runTest {
        // when
        val markdown = semanticEvents {
            "p" { "a"("href" to "u") { "del" { +"a~b" } } }
        }.renderMarkdown()
        // then
        markdown sameAs "[~~a\\~b~~](u)"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should not escape label content against emphasis opened outside the label`() = runTest {
        // An `<em>` wrapping a link: a literal `*` in the *link label* must NOT be
        // escaped against the outer em — only against emphasis opened *inside* the
        // label (here there is none). The fix scopes escaping to the enclosing
        // Inline run up to the Link frame, so the label `*` stays unescaped.
        // when
        val markdown = semanticEvents {
            "p" { "em" { +"x "; "a"("href" to "u") { +"a*b" }; +" y" } }
        }.renderMarkdown()
        // then — render only (no fixpoint): re-parsing `[a*b]` inside `*…*` trips a
        // SEPARATE pre-existing parser bug (the lone label `*` crosses the outer em
        // → unbalanced stream), unrelated to this renderer-side escaping fix. See
        // issue #58.
        markdown sameAs "*x [a*b](u) y*"
    }

    @Test
    fun `should round-trip del wrapping a link whose label text contains a tilde`() = runTest {
        // `del`/`mark`/`sup` are parser boolean flags, NOT scoped by the label
        // watermark — so a matching delimiter in the *label text* of a wrapped link
        // closes the OUTER span on re-parse (a crossed stream that crashes the
        // renderer). The renderer must escape against the outer del/mark/sup too.
        // when
        val markdown = semanticEvents {
            "p" { "del" { "a"("href" to "u") { +"foo~bar" } } }
        }.renderMarkdown()
        // then
        markdown sameAs "~~[foo\\~bar](u)~~"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should round-trip mark wrapping a link whose label text contains an equals pair`() = runTest {
        // when
        val markdown = semanticEvents {
            "p" { "mark" { "a"("href" to "u") { +"foo==bar" } } }
        }.renderMarkdown()
        // then
        markdown sameAs "==[foo\\=\\=bar](u)=="
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should round-trip superscript wrapping a link whose label text contains a caret`() = runTest {
        // when
        val markdown = semanticEvents {
            "p" { "sup" { "a"("href" to "u") { +"foo^bar" } } }
        }.renderMarkdown()
        // then
        markdown sameAs "^[foo\\^bar](u)^"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should round-trip an image-in-link label with a trailing asterisk`() = runTest {
        // The minimised Brave shape: a link label holding image-as-text whose URL
        // carries a `*`, plus a trailing `*`. The opening `*` (in the URL) and the
        // trailing `*` form a label-local emphasis that must close on the trailing
        // delimiter rather than swallow it as content (which grew the run forever).
        // given
        val markdown = "[![alt](http://x/v3/*app/a.svg)*](http://r/)"
        // when
        val rendered = flowOf(markdown).parse().renderMarkdown()
        // then
        rendered sameAs markdown
    }

    @Test
    fun `should round-trip emphasis whose content contains a literal asterisk`() = runTest {
        // An HTML-sourced `<em>a*b</em>` — the parser never produces this shape on
        // its own, but the DOM pipeline does. The renderer must escape the inner
        // `*` so the re-emitted `*a*b*` does not re-pair into `<em>a</em>b*`.
        // when
        val markdown = semanticEvents { "p" { "em" { +"a*b" } } }.renderMarkdown()
        // then
        markdown sameAs "*a\\*b*"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should round-trip emphasis whose content contains a literal underscore`() = runTest {
        // `_` is NOT in delimiterBit (the renderer always emits `*` for em/strong),
        // and a `_` in `*…*` content can't close the em (CommonMark flanking: a
        // `_` adjacent to a letter is intraword-suppressed) — so it needs no escape
        // and stays a stable fixpoint. Confirms the "renderer always uses `*`" claim.
        // when
        val markdown = semanticEvents { "p" { "em" { +"foo_bar" } } }.renderMarkdown()
        // then
        markdown sameAs "*foo_bar*"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should round-trip emphasis whose content contains a literal backtick`() = runTest {
        // A literal backtick inside `<em>` would eagerly open an inline code span on
        // re-parse — and `flushInline` closes `code` before `em` (LIFO), turning
        // `*a`b*` into `<em>a<code>b</code></em>` and growing every round-trip. The
        // renderer must escape the backtick like an emphasis delimiter.
        // when
        val markdown = semanticEvents { "p" { "em" { +"a`b" } } }.renderMarkdown()
        // then
        markdown sameAs "*a\\`b*"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should round-trip strong whose content contains a literal asterisk`() = runTest {
        // when
        val markdown = semanticEvents { "p" { "strong" { +"a*b" } } }.renderMarkdown()
        // then
        markdown sameAs "**a\\*b**"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should round-trip superscript whose content contains a literal caret`() = runTest {
        // `^` closes a sup span, so a literal `^` inside the span must be escaped —
        // otherwise the re-emitted `^a^b^` would re-pair into `<sup>a</sup>b^`.
        // when
        val markdown = semanticEvents { "p" { "sup" { +"a^b" } } }.renderMarkdown()
        // then
        markdown sameAs "^a\\^b^"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should round-trip del whose content contains a literal tilde`() = runTest {
        // A single `~` is a valid GFM strikethrough delimiter (`~a~` is
        // `<del>a</del>`), so a lone `~` inside a `~~…~~` span must be escaped —
        // unescaped, `~~a~b~~` re-parses to the broken `<del>a</del>b<del></del>`.
        // when
        val markdown = semanticEvents { "p" { "del" { +"a~b" } } }.renderMarkdown()
        // then
        markdown sameAs "~~a\\~b~~"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should round-trip del whose content contains a literal tilde pair`() = runTest {
        // when
        val markdown = semanticEvents { "p" { "del" { +"a~~b" } } }.renderMarkdown()
        // then
        markdown sameAs "~~a\\~\\~b~~"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should round-trip a plain mark span`() = runTest {
        // A `==…==` span whose closer ends the block used to leak the closing `==`
        // into the content (`<mark>a==</mark>`), growing the run every round-trip —
        // see the parser-side flushInline fix.
        // when
        val markdown = semanticEvents { "p" { "mark" { +"highlighted" } } }.renderMarkdown()
        // then
        markdown sameAs "==highlighted=="
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should conservatively escape a lone equals inside a mark span`() = runTest {
        // A lone `=` is not itself a delimiter, but escaping it anyway is the safe
        // choice: pair-only escaping can't survive the round-trip, because
        // re-parsing an escaped span splits its `\=\=` into two single-`=` text
        // events, hiding the pair from the per-event escaping pass. Over-escaping
        // (a harmless `\=`) keeps the fixpoint stable.
        // when
        val markdown = semanticEvents { "p" { "mark" { +"a=b" } } }.renderMarkdown()
        // then
        markdown sameAs "==a\\=b=="
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should escape an equals pair inside a mark span`() = runTest {
        // A literal `==` inside the span WOULD close mark early on re-parse.
        // when
        val markdown = semanticEvents { "p" { "mark" { +"a==b" } } }.renderMarkdown()
        // then
        markdown sameAs "==a\\=\\=b=="
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should round-trip an unmatched trailing mark run as literal`() = runTest {
        // `foo==` (Python equality, math) has no span to close — it must stay
        // literal, not become an empty `<mark></mark>` that renders `foo====`.
        // given
        val markdown = "foo=="
        // when
        val rendered = flowOf(markdown).parse().renderMarkdown()
        // then
        rendered sameAs markdown
    }

    @Test
    fun `should round-trip an unmatched trailing del run as literal`() = runTest {
        // given
        val markdown = "foo~~"
        // when
        val rendered = flowOf(markdown).parse().renderMarkdown()
        // then
        rendered sameAs markdown
    }

    @Test
    fun `should round-trip strong whose content contains a literal backtick`() = runTest {
        // The backtick escape fires for ANY active emphasis (mask != 0), not just
        // `em` — locking the non-em delimiters share the same code path.
        // when
        val markdown = semanticEvents { "p" { "strong" { +"a`b" } } }.renderMarkdown()
        // then
        markdown sameAs "**a\\`b**"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should round-trip del whose content contains a literal backtick`() = runTest {
        // when
        val markdown = semanticEvents { "p" { "del" { +"a`b" } } }.renderMarkdown()
        // then
        markdown sameAs "~~a\\`b~~"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should NOT escape delimiters inside a fenced code block`() = runTest {
        // Escaping is suppressed inside verbatim `pre`/`code` (inPreCode) — a
        // backslash there is literal text, not an escape, so a `*`/`~`/backtick in
        // code content must pass through unchanged (escaping it would corrupt code).
        // when
        val markdown = semanticEvents { "pre" { "code" { +"a * b ~ c `d`" } } }.renderMarkdown()
        // then
        markdown sameAs "```\na * b ~ c `d`\n```"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should escape emphasis text but keep an inline code span verbatim`() = runTest {
        // An inline `<code>` inside `<em>` pushes a Code frame, so
        // enclosingInlineDelimiterMask stops at it (mask 0) and the code content
        // (`x*y`) is NOT escaped, while the em's own text (`a*`, `b*`) IS.
        // when
        val markdown = semanticEvents {
            "p" { "em" { +"a*"; "code" { +"x*y" }; +"b*" } }
        }.renderMarkdown()
        // then
        markdown sameAs "*a\\*`x*y`b\\**"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should keep inline code verbatim inside a del span`() = runTest {
        // Inline-code content is captured into a label buffer and reaches
        // escapeActiveInlineDelimiters *before* its inLabel() guard, so a `~` in the
        // code content must NOT be escaped against the surrounding del — code is
        // verbatim (the `is Code` early-return). The em version above passes even
        // without the fix because `*` is excluded from the outer-delimiter bits;
        // del/mark/sup are not, which is why this case needs its own guard.
        // when
        val markdown = semanticEvents { "p" { "del" { "code" { +"a~b" } } } }.renderMarkdown()
        // then
        markdown sameAs "~~`a~b`~~"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should keep inline code verbatim inside a superscript span`() = runTest {
        // The `^` counterpart — before the fix this was not even a fixpoint
        // (`^`a\^b`^` re-parsed differently each pass).
        // when
        val markdown = semanticEvents { "p" { "sup" { "code" { +"a^b" } } } }.renderMarkdown()
        // then
        markdown sameAs "^`a^b`^"
        assertMarkdownFixpoint(markdown)
    }

    @Test
    fun `should keep inline code verbatim inside a del span even with a tagged child`() = runTest {
        // A raw inline tag (`<b>`) inside the `<code>` pushes a TaggedInline frame
        // *above* the Code frame, so a top-of-stack `is Code` check would miss it
        // and the `~` would be escaped against the outer del, corrupting the code.
        // `inInlineCode()` scans past the transparent TaggedInline to the Code below.
        // when
        val markdown = semanticEvents {
            "p" { "del" { "code" { tag("b") { +"a~b" } } } }
        }.renderMarkdown()
        // then
        markdown sameAs "~~`<b>a~b</b>`~~"
        assertMarkdownFixpoint(markdown)
    }
}
