/*
 * Copyright 2025 Kazimierz Pogoda / Xemantic
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

package com.xemantic.markanywhere.transform

import com.xemantic.kotlin.test.sameAs
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.render.render
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.text.digitToInt
import kotlin.text.matches
import kotlin.text.repeat
import kotlin.text.replace
import kotlin.text.trim

class TransformerTest {

    @Test
    fun `should transform simple HTML to Markdown`() = runTest {
        // given
        val transformer = Transformer {

            val whitespaceRegex = Regex("\\s+")
            val htmlHeadingRegex = Regex("h[1-6]")

            // Headings - use proper markers and discard breaks
            match({ name matches htmlHeadingRegex }) {
                val level = it.name[1].digitToInt()
                +"#".repeat(level)
                +" "
                children(mode = "span")
                +"\n\n"
            }

            match("p") {
                children()
                +"\n\n"
            }

            match("ul") {
                children()
                +"\n"
            }

            match("li") {
                +"* "
                children(mode = "span")
                +"\n"
            }

            match("br") {
                +"\n"
            }

            match("br", mode = "span") {
                +" "
            }

            matchText {
                +it.trim().replace(whitespaceRegex, " ")
            }

            match("em", mode = "span") {
                +"*"
                children(mode = "span")
                +"*"
            }

        }

        val htmlEvents = semanticEvents {
            "html" {
                "body" {
                    "h1" {
                        +"Title"
                    }
                    "p" {
                        +"Paragraph text\nanother line"
                    }
                }
            }
        }

        // when
        val markdownEvents = htmlEvents.transform(
            transformer
        )

        // then
        markdownEvents.render() sameAs """
            # Title

            Paragraph text another line

        """.trimIndent()
    }

    @Test
    fun `should transform different heading levels`() = runTest {
        // given
        val transformer = createHtmlToMarkdownTransformer()

        val htmlEvents = semanticEvents {
            "html" {
                "body" {
                    "h1" { +"Main Title" }
                    "h2" { +"Section" }
                    "h3" { +"Subsection" }
                    "h4" { +"Sub-subsection" }
                    "h5" { +"Minor Heading" }
                    "h6" { +"Smallest Heading" }
                }
            }
        }

        // when
        val markdownEvents = htmlEvents.transform(transformer)

        // then
        markdownEvents.render() sameAs """
            # Main Title

            ## Section

            ### Subsection

            #### Sub-subsection

            ##### Minor Heading

            ###### Smallest Heading

        """.trimIndent()
    }

    @Test
    fun `should transform unordered lists`() = runTest {
        // given
        val transformer = createHtmlToMarkdownTransformer()

        val htmlEvents = semanticEvents {
            "html" {
                "body" {
                    "p" { +"Shopping list:" }
                    "ul" {
                        "li" { +"Apples" }
                        "li" { +"Bananas" }
                        "li" { +"Cherries" }
                    }
                }
            }
        }

        // when
        val markdownEvents = htmlEvents.transform(transformer)

        // then
        markdownEvents.render() sameAs """
            Shopping list:

            * Apples
            * Bananas
            * Cherries

        """.trimIndent()
    }

    @Test
    fun `should transform emphasis text`() = runTest {
        // given
        val transformer = createHtmlToMarkdownTransformer()

        val htmlEvents = semanticEvents {
            "html" {
                "body" {
                    "p" {
                        +"This is "
                        "em" { +"important" }
                        +" text."
                    }
                }
            }
        }

        // when
        val markdownEvents = htmlEvents.transform(transformer)

        // then
        markdownEvents.render() sameAs """
            This is *important* text.

        """.trimIndent()
    }

    @Test
    fun `should handle line breaks in span mode as spaces`() = runTest {
        // given
        val transformer = createHtmlToMarkdownTransformer()

        // Line breaks in span mode (paragraph children) become spaces
        val htmlEvents = semanticEvents {
            "html" {
                "body" {
                    "p" {
                        +"First line"
                        "br" {}
                        +"Second line"
                    }
                }
            }
        }

        // when
        val markdownEvents = htmlEvents.transform(transformer)

        // then - br in span mode outputs space to maintain inline flow
        markdownEvents.render() sameAs """
            First line Second line

        """.trimIndent()
    }

    @Test
    fun `should handle line breaks in headings as spaces`() = runTest {
        // given
        val transformer = createHtmlToMarkdownTransformer()

        // Line breaks in span context (heading) become spaces
        val htmlEvents = semanticEvents {
            "html" {
                "body" {
                    "h1" {
                        +"Title"
                        "br" {}
                        +"Subtitle"
                    }
                }
            }
        }

        // when
        val markdownEvents = htmlEvents.transform(transformer)

        // then - br in span mode becomes space
        markdownEvents.render() sameAs """
            # Title Subtitle

        """.trimIndent()
    }

    @Test
    fun `should transform complex nested structure`() = runTest {
        // given
        val transformer = createHtmlToMarkdownTransformer()

        val htmlEvents = semanticEvents {
            "html" {
                "body" {
                    "h1" { +"Document Title" }
                    "p" {
                        +"This document has "
                        "em" { +"emphasized" }
                        +" content."
                    }
                    "h2" { +"Features" }
                    "ul" {
                        "li" { +"Easy to use" }
                        "li" { +"Powerful transformations" }
                        "li" { +"Multiplatform support" }
                    }
                    "h2" { +"Conclusion" }
                    "p" {
                        +"Thank you for reading."
                    }
                }
            }
        }

        // when
        val markdownEvents = htmlEvents.transform(transformer)

        // then
        markdownEvents.render() sameAs """
            # Document Title

            This document has *emphasized* content.

            ## Features

            * Easy to use
            * Powerful transformations
            * Multiplatform support

            ## Conclusion

            Thank you for reading.

        """.trimIndent()
    }

    @Test
    fun `should handle list items with emphasis`() = runTest {
        // given
        val transformer = createHtmlToMarkdownTransformer()

        val htmlEvents = semanticEvents {
            "html" {
                "body" {
                    "ul" {
                        "li" {
                            +"This is "
                            "em" { +"critical" }
                        }
                        "li" {
                            "em" { +"All" }
                            +" emphasized"
                        }
                    }
                }
            }
        }

        // when
        val markdownEvents = htmlEvents.transform(transformer)

        // then
        markdownEvents.render() sameAs """
            * This is *critical*
            * *All* emphasized

        """.trimIndent()
    }

    @Test
    fun `should collapse internal whitespace in text`() = runTest {
        // given
        val transformer = createHtmlToMarkdownTransformer()

        // Text with multiple spaces, tabs, and newlines gets normalized
        val htmlEvents = semanticEvents {
            "html" {
                "body" {
                    "p" {
                        +"Multiple   spaces   and\n\ttabs\t\tare   collapsed"
                    }
                }
            }
        }

        // when
        val markdownEvents = htmlEvents.transform(transformer)

        // then - all whitespace runs become single spaces
        markdownEvents.render() sameAs """
            Multiple spaces and tabs are collapsed

        """.trimIndent()
    }

    private fun createHtmlToMarkdownTransformer(): Transformer = Transformer {
        val whitespaceRegex = Regex("\\s+")
        val htmlHeadingRegex = Regex("h[1-6]")

        // Headings - use proper markers and process children in span mode
        match({ name matches htmlHeadingRegex }) {
            val level = it.name[1].digitToInt()
            +"#".repeat(level)
            +" "
            children(mode = "span")
            +"\n\n"
        }

        // Paragraphs process children in span mode for inline formatting
        match("p") {
            children(mode = "span")
            +"\n\n"
        }

        match("ul") {
            children()
            +"\n"
        }

        match("li") {
            +"* "
            children(mode = "span")
            +"\n"
        }

        // Mode-specific rules must come BEFORE general rules
        // because the matcher uses firstOrNull
        match("br", mode = "span") {
            +" "
        }

        match("br") {
            +"\n"
        }

        // Emphasis wraps content in asterisks (span mode for inline formatting)
        match("em", mode = "span") {
            +"*"
            children(mode = "span")
            +"*"
        }

        // Text normalization: collapse whitespace, preserve word boundaries
        matchText {
            val normalized = it.replace(whitespaceRegex, " ")
            // Only output non-whitespace or preserve single space for word boundaries
            if (normalized.isNotBlank()) {
                +normalized
            } else if (normalized == " ") {
                +" "
            }
        }
    }

}
