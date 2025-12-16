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

import com.xemantic.kotlin.test.sameAs
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.js.appendSemanticEvents
import kotlinx.browser.document
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AppendSemanticEventsTest {

    @Test
    fun `should append simple text to element`() = runTest {
        // given
        val div = document.createElement("div")
        val events = semanticEvents {
            +"Hello, World!"
        }

        // when
        div.appendSemanticEvents(events)

        // then
        div.innerHTML sameAs "Hello, World!"
    }

    @Test
    fun `should append paragraph with text to element`() = runTest {
        // given
        val div = document.createElement("div")
        val events = semanticEvents {
            "p" {
                +"Lorem ipsum"
            }
        }

        // when
        div.appendSemanticEvents(events)

        // then
        div.innerHTML sameAs "<p>Lorem ipsum</p>"
    }

    @Test
    fun `should append nested elements to element`() = runTest {
        // given
        val div = document.createElement("div")
        val events = semanticEvents {
            "h1" {
                +"Title"
            }
            "p" {
                +"Some "
                "strong" {
                    +"bold"
                }
                +" text"
            }
        }

        // when
        div.appendSemanticEvents(events)

        // then
        div.innerHTML sameAs "<h1>Title</h1><p>Some <strong>bold</strong> text</p>"
    }

    @Test
    fun `should append deeply nested structure`() = runTest {
        // given
        val div = document.createElement("div")
        val events = semanticEvents {
            "article" {
                "header" {
                    "h1" {
                        +"Main Title"
                    }
                }
                "section" {
                    "p" {
                        +"First paragraph"
                    }
                }
            }
        }

        // when
        div.appendSemanticEvents(events)

        // then
        div.innerHTML sameAs "<article><header><h1>Main Title</h1></header><section><p>First paragraph</p></section></article>"
    }

    @Test
    fun `should append multiple sibling elements`() = runTest {
        // given
        val div = document.createElement("div")
        val events = semanticEvents {
            "p" {
                +"First"
            }
            "p" {
                +"Second"
            }
            "p" {
                +"Third"
            }
        }

        // when
        div.appendSemanticEvents(events)

        // then
        div.innerHTML sameAs "<p>First</p><p>Second</p><p>Third</p>"
    }

    @Test
    fun `should append inline elements within text`() = runTest {
        // given
        val div = document.createElement("div")
        val events = semanticEvents {
            "p" {
                +"This is "
                "em" {
                    +"emphasized"
                }
                +" and "
                "strong" {
                    +"strong"
                }
                +" text."
            }
        }

        // when
        div.appendSemanticEvents(events)

        // then
        div.innerHTML sameAs "<p>This is <em>emphasized</em> and <strong>strong</strong> text.</p>"
    }

    @Test
    fun `should append link element`() = runTest {
        // given
        val div = document.createElement("div")
        val events = semanticEvents {
            "a" {
                +"Click here"
            }
        }

        // when
        div.appendSemanticEvents(events)

        // then
        div.innerHTML sameAs "<a>Click here</a>"
    }

    @Test
    fun `should append list structure`() = runTest {
        // given
        val div = document.createElement("div")
        val events = semanticEvents {
            "ul" {
                "li" {
                    +"Item 1"
                }
                "li" {
                    +"Item 2"
                }
                "li" {
                    +"Item 3"
                }
            }
        }

        // when
        div.appendSemanticEvents(events)

        // then
        div.innerHTML sameAs "<ul><li>Item 1</li><li>Item 2</li><li>Item 3</li></ul>"
    }

    @Test
    fun `should handle empty element`() = runTest {
        // given
        val div = document.createElement("div")
        val events = semanticEvents {
            "span" {}
        }

        // when
        div.appendSemanticEvents(events)

        // then
        div.innerHTML sameAs "<span></span>"
    }

    @Test
    fun `should handle mixed content with text and elements`() = runTest {
        // given
        val div = document.createElement("div")
        val events = semanticEvents {
            +"Start "
            "b" {
                +"middle"
            }
            +" end"
        }

        // when
        div.appendSemanticEvents(events)

        // then
        div.innerHTML sameAs "Start <b>middle</b> end"
    }

    @Test
    fun `should append element with single attribute`() = runTest {
        // given
        val div = document.createElement("div")
        val events = semanticEvents {
            "a"("href" to "https://example.com") {
                +"Link"
            }
        }

        // when
        div.appendSemanticEvents(events)

        // then
        div.innerHTML sameAs """<a href="https://example.com">Link</a>"""
    }

    @Test
    fun `should append element with multiple attributes`() = runTest {
        // given
        val div = document.createElement("div")
        val events = semanticEvents {
            "a"("href" to "https://example.com", "target" to "_blank") {
                +"External Link"
            }
        }

        // when
        div.appendSemanticEvents(events)

        // then
        div.innerHTML sameAs """<a href="https://example.com" target="_blank">External Link</a>"""
    }

    @Test
    fun `should append element with id and class attributes`() = runTest {
        // given
        val div = document.createElement("div")
        val events = semanticEvents {
            "h1"("id" to "main-title", "class" to "header") {
                +"Title"
            }
        }

        // when
        div.appendSemanticEvents(events)

        // then
        div.innerHTML sameAs """<h1 id="main-title" class="header">Title</h1>"""
    }

    @Test
    fun `should append nested elements with attributes`() = runTest {
        // given
        val div = document.createElement("div")
        val events = semanticEvents {
            "div"("class" to "container") {
                "p"("class" to "content") {
                    +"Check out "
                    "a"("href" to "https://example.com") {
                        +"this link"
                    }
                }
            }
        }

        // when
        div.appendSemanticEvents(events)

        // then
        div.innerHTML sameAs """<div class="container"><p class="content">Check out <a href="https://example.com">this link</a></p></div>"""
    }

    @Test
    fun `should escape HTML special characters in text`() = runTest {
        // given
        val div = document.createElement("div")
        val events = semanticEvents {
            "p" {
                +"Use <div> and <span> tags & escape \"quotes\""
            }
        }

        // when
        div.appendSemanticEvents(events)

        // then
        div.innerHTML sameAs """<p>Use &lt;div&gt; and &lt;span&gt; tags &amp; escape "quotes"</p>"""
    }

    @Test
    fun `should handle special characters in attribute values`() = runTest {
        // given
        val div = document.createElement("div")
        val events = semanticEvents {
            "a"("href" to "https://example.com?foo=1&bar=2") {
                +"Link with query params"
            }
        }

        // when
        div.appendSemanticEvents(events)

        // then
        div.innerHTML sameAs """<a href="https://example.com?foo=1&amp;bar=2">Link with query params</a>"""
    }

    @Test
    fun `should handle quotes in attribute values`() = runTest {
        // given
        val div = document.createElement("div")
        val events = semanticEvents {
            "div"("data-json" to """{"key": "value"}""") {
                +"Content"
            }
        }

        // when
        div.appendSemanticEvents(events)

        // then
        div.innerHTML sameAs """<div data-json="{&quot;key&quot;: &quot;value&quot;}">Content</div>"""
    }

}