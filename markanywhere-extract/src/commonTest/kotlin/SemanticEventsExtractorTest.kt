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

package com.xemantic.markanywhere.extract

import com.xemantic.kotlin.core.text.lineFlow
import com.xemantic.kotlin.test.coroutines.should
import com.xemantic.kotlin.test.have
import com.xemantic.kotlin.test.sameAs
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.parse.DefaultMarkanywhereParser
import com.xemantic.markanywhere.parse.parse
import com.xemantic.markanywhere.render.render
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SemanticEventsExtractorTest {

    @Test
    fun `should extract custom tag`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val events = """
            Some text
            
            <foo:bar buzz="42">
            println("Hello World")
            </foo:bar>
            
            Some other text
        """.trimIndent().lineFlow().parse(parser)
        val extractor = MarkupContentExtractor(
            tag = "foo:bar"
        )

        // when
        val rendered = events.extract(extractor).render()

        // then
        extractor should {
            have(succeeded)
            have(!isExtracting)
            extractedEvents.asFlow() sameAs semanticEvents {
                tag("foo:bar", "buzz" to "42") {
                    +"""println("Hello World")"""
                }
            }
            have(attributes == mapOf("buzz" to "42"))
            content sameAs """
                println("Hello World")
            """.trimIndent()
        }

        rendered sameAs """
            <p>
              Some text
            </p>
            <foo:bar buzz="42">
            println("Hello World")
            </foo:bar>
            <p>
              Some other text
            </p>
        """.trimIndent()
    }

    @Test
    fun `should not extract when tag is not found`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val events = """
            Some text without the target tag
        """.trimIndent().lineFlow().parse(parser)
        val extractor = MarkupContentExtractor(
            tag = "foo:bar"
        )

        // when
        val rendered = events.extract(extractor).render()

        // then
        extractor should {
            have(!succeeded)
            have(!isExtracting)
            have(extractedEvents.isEmpty())
            have(attributes == null)
            have(content == null)
        }

        rendered sameAs """
            <p>
              Some text without the target tag
            </p>
        """.trimIndent()
    }

    @Test
    fun `should extract tag without attributes`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val events = """
            <foo:bar>
            content
            </foo:bar>
        """.trimIndent().lineFlow().parse(parser)
        val extractor = MarkupContentExtractor(
            tag = "foo:bar"
        )

        // when
        val rendered = events.extract(extractor).render()

        // then
        extractor should {
            have(succeeded)
            have(!isExtracting)
            have(attributes == null)
            extractedEvents.asFlow() sameAs semanticEvents {
                tag("foo:bar") {
                    +"content"
                }
            }
            content sameAs "content"
        }

        rendered sameAs """
            <foo:bar>
            content
            </foo:bar>
        """.trimIndent()
    }

    @Test
    fun `should extract empty tag`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val events = """
            <foo:bar>
            </foo:bar>
        """.trimIndent().lineFlow().parse(parser)
        val extractor = MarkupContentExtractor(
            tag = "foo:bar"
        )

        // when
        val rendered = events.extract(extractor).render()

        // then
        extractor should {
            have(succeeded)
            have(!isExtracting)
            have(attributes == null)
            extractedEvents.asFlow() sameAs semanticEvents {
                tag("foo:bar") {}
            }
            have(content == null)
        }

        rendered sameAs """
            <foo:bar>
            </foo:bar>
        """.trimIndent()
    }

    @Test
    fun `should extract multiple lines of content`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val events = """
            <foo:bar>
            line1
            line2
            line3
            </foo:bar>
        """.trimIndent().lineFlow().parse(parser)
        val extractor = MarkupContentExtractor(tag = "foo:bar")

        // when
        val rendered = events.extract(extractor).render()

        // then
        extractor should {
            have(succeeded)
            have(!isExtracting)
            have(attributes == null)
            extractedEvents.asFlow() sameAs semanticEvents {
                tag("foo:bar") {
                    +"line1"
                    +"\n"
                    +"l"
                    +"ine2"
                    +"\n"
                    +"l"
                    +"ine3"
                }
            }
            content sameAs """
                line1
                line2
                line3
            """.trimIndent()
        }

        rendered sameAs """
            <foo:bar>
            line1
            line2
            line3
            </foo:bar>
        """.trimIndent()
    }

    @Test
    fun `should extract tag with nested tags inside`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        // NOTE: Parser treats <nested:tag> as plain text, not as Mark events
        val events = """
            <foo:bar>
            before
            <nested:tag>
            inside nested
            </nested:tag>
            after
            </foo:bar>
        """.trimIndent().lineFlow().parse(parser)
        val extractor = MarkupContentExtractor(
            tag = "foo:bar"
        )

        // when
        val rendered = events.extract(extractor).render()

        // then
        extractor should {
            have(succeeded)
            have(!isExtracting)
            have(attributes == null)
            // NOTE: The text chunking reflects how the parser incrementally detects
            // potential closing tags - when '<' is seen, it buffers until it can
            // determine if it's the closing tag or not.
            extractedEvents.asFlow() sameAs semanticEvents {
                tag("foo:bar") {
                    +"before"
                    +"\n"
                    +"<n"
                    +"ested:tag>"
                    +"\n"
                    +"i"
                    +"nside nested"
                    +"\n"
                    +"</n"
                    +"ested:tag>"
                    +"\n"
                    +"a"
                    +"fter"
                }
            }
            content sameAs """
                before
                <nested:tag>
                inside nested
                </nested:tag>
                after
            """.trimIndent()
        }

        rendered sameAs """
            <foo:bar>
            before
            <nested:tag>
            inside nested
            </nested:tag>
            after
            </foo:bar>
        """.trimIndent()
    }

    @Test
    fun `should extract only first occurrence of tag`() = runTest {
        // given
        val parser = DefaultMarkanywhereParser()
        val events = """
            <foo:bar>
            first content
            </foo:bar>

            <foo:bar>
            second content
            </foo:bar>
        """.trimIndent().lineFlow().parse(parser)
        val extractor = MarkupContentExtractor(
            tag = "foo:bar"
        )

        // when
        val rendered = events.extract(extractor).render()

        // then
        extractor should {
            have(succeeded)
            have(!isExtracting)
            have(attributes == null)
            extractedEvents.asFlow() sameAs semanticEvents {
                tag("foo:bar") {
                    +"first content"
                }
            }
            content sameAs "first content"
        }

        rendered sameAs """
            <foo:bar>
            first content
            </foo:bar>
            <foo:bar>
            second content
            </foo:bar>
        """.trimIndent()
    }

}
