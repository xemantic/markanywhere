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

package com.xemantic.markanywhere.js

import com.xemantic.kotlin.test.assert
import com.xemantic.kotlin.test.coroutines.should
import com.xemantic.kotlin.test.have
import com.xemantic.markanywhere.parse.parse
import kotlinx.browser.document
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.w3c.dom.Element
import kotlin.test.Test

class AppendSemanticEventsNamespaceTest {

    @Test
    fun `should create non-xmlns svg root element in SVG namespace`() = runTest {
        // given
        val div = document.createElement("div")
        val events = flowOf("""
            <svg/>
        """.trimIndent()).parse()

        // when
        div.appendSemanticEvents(events)

        // then
        div.child("svg") should {
            have(namespaceURI == SVG_NS)
        }
    }

    @Test
    fun `should create svg descendants in SVG namespace when root has no xmlns`() = runTest {
        // given
        val div = document.createElement("div")
        val events = flowOf("""
            <svg viewBox="0 0 100 100">
            <g>
            <rect x="10" y="10" width="80" height="80"/>
            <circle cx="50" cy="50" r="20"/>
            </g>
            </svg>
        """.trimIndent()).parse()

        // when
        div.appendSemanticEvents(events)

        // then
        div.child("svg") should {
            have(namespaceURI == SVG_NS)
            child("g") should {
                have(namespaceURI == SVG_NS)
                child("rect") should {
                    have(namespaceURI == SVG_NS)
                }
                child("circle") should {
                    have(namespaceURI == SVG_NS)
                }
            }
        }
    }

    @Test
    fun `should create svg root element in SVG namespace`() = runTest {
        // given
        val div = document.createElement("div")
        val events = flowOf("""
            <svg xmlns="$SVG_NS"/>
        """.trimIndent()).parse()

        // when
        div.appendSemanticEvents(events)

        // then
        div.child("svg") should {
            have(namespaceURI == SVG_NS)
        }
    }

    @Test
    fun `should create svg descendants in SVG namespace`() = runTest {
        // given
        val div = document.createElement("div")
        val events = flowOf("""
            <svg xmlns="$SVG_NS" viewBox="0 0 100 100">
            <g>
            <rect x="10" y="10" width="80" height="80"/>
            <circle cx="50" cy="50" r="20"/>
            </g>
            </svg>
        """.trimIndent()).parse()

        // when
        div.appendSemanticEvents(events)

        // then
        div.child("svg") should {
            have(namespaceURI == SVG_NS)
            child("g") should {
                have(namespaceURI == SVG_NS)
                child("rect") should {
                    have(namespaceURI == SVG_NS)
                }
                child("circle") should {
                    have(namespaceURI == SVG_NS)
                }
            }
        }
    }

    @Test
    fun `should preserve SVG namespace for deeply nested camelCase elements`() = runTest {
        // given
        val div = document.createElement("div")
        val events = flowOf("""
            <svg xmlns="$SVG_NS">
            <defs>
            <linearGradient id="g1">
            <stop offset="0%" stop-color="red"/>
            </linearGradient>
            <clipPath id="c1">
            <rect width="10" height="10"/>
            </clipPath>
            </defs>
            </svg>
        """.trimIndent()).parse()

        // when
        div.appendSemanticEvents(events)

        // then
        div.child("svg") should {
            child("defs") should {
                have(namespaceURI == SVG_NS)
                child("linearGradient") should {
                    have(namespaceURI == SVG_NS)
                    have(localName == "linearGradient")
                    child("stop") should {
                        have(namespaceURI == SVG_NS)
                    }
                }
                child("clipPath") should {
                    have(namespaceURI == SVG_NS)
                    have(localName == "clipPath")
                    child("rect") should {
                        have(namespaceURI == SVG_NS)
                    }
                }
            }
        }
    }

    @Test
    fun `should create non-xmlns math root element in MathML namespace`() = runTest {
        // given
        val div = document.createElement("div")
        val events = flowOf("""
            <math/>
        """.trimIndent()).parse()

        // when
        div.appendSemanticEvents(events)

        // then
        div.child("math") should {
            have(namespaceURI == MATHML_NS)
        }
    }

    @Test
    fun `should create MathML descendants in MathML namespace when root has no xmlns`() = runTest {
        // given
        val div = document.createElement("div")
        val events = flowOf("""
            <math>
            <mrow>
            <mi>x</mi>
            </mrow>
            </math>
        """.trimIndent()).parse()

        // when
        div.appendSemanticEvents(events)

        // then
        div.child("math") should {
            have(namespaceURI == MATHML_NS)
            child("mrow") should {
                have(namespaceURI == MATHML_NS)
                child("mi") should {
                    have(namespaceURI == MATHML_NS)
                }
            }
        }
    }

    @Test
    fun `should create math root element in MathML namespace`() = runTest {
        // given
        val div = document.createElement("div")
        val events = flowOf("""
            <math xmlns="$MATHML_NS"/>
        """.trimIndent()).parse()

        // when
        div.appendSemanticEvents(events)

        // then
        div.child("math") should {
            have(namespaceURI == MATHML_NS)
        }
    }

    @Test
    fun `should create MathML descendants in MathML namespace`() = runTest {
        // given
        val div = document.createElement("div")
        val events = flowOf("""
            <math xmlns="$MATHML_NS">
            <mrow>
            <mi>x</mi>
            <mo>=</mo>
            <mfrac>
            <mn>1</mn>
            <mn>2</mn>
            </mfrac>
            </mrow>
            </math>
        """.trimIndent()).parse()

        // when
        div.appendSemanticEvents(events)

        // then
        div.child("math") should {
            have(namespaceURI == MATHML_NS)
            child("mrow") should {
                have(namespaceURI == MATHML_NS)
                child("mi") should {
                    have(namespaceURI == MATHML_NS)
                }
                child("mo") should {
                    have(namespaceURI == MATHML_NS)
                }
                child("mfrac") should {
                    have(namespaceURI == MATHML_NS)
                    for (i in 0 until children.length) {
                        children.item(i)!! should {
                            have(namespaceURI == MATHML_NS)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `should keep foreignObject in SVG namespace but switch children back to HTML`() = runTest {
        // given
        val div = document.createElement("div")
        val events = flowOf("""
            <svg xmlns="$SVG_NS">
            <foreignObject x="0" y="0" width="100" height="100">
            <p>HTML inside SVG<strong>!</strong></p>
            </foreignObject>
            </svg>
        """.trimIndent()).parse()

        // when
        div.appendSemanticEvents(events)

        // then
        div.child("svg") should {
            have(namespaceURI == SVG_NS)
            child("foreignObject") should {
                have(namespaceURI == SVG_NS)
                have(localName == "foreignObject")
                child("p") should {
                    have(namespaceURI == HTML_NS)
                    child("strong") should {
                        have(namespaceURI == HTML_NS)
                    }
                }
            }
        }
    }

    @Test
    fun `should restore SVG namespace after foreignObject closes`() = runTest {
        // given
        val div = document.createElement("div")
        val events = flowOf("""
            <svg xmlns="$SVG_NS">
            <foreignObject width="100" height="100">
            <p>HTML island</p>
            </foreignObject>
            <rect width="50" height="50"/>
            </svg>
        """.trimIndent()).parse()

        // when
        div.appendSemanticEvents(events)

        // then
        div.child("svg") should {
            child("rect") should {
                have(namespaceURI == SVG_NS)
            }
        }
    }

    @Test
    fun `should switch annotation-xml children to HTML when encoding is text-html`() = runTest {
        // given
        val div = document.createElement("div")
        val events = flowOf("""
            <math xmlns="$MATHML_NS">
            <semantics>
            <mrow><mi>x</mi></mrow>
            <annotation-xml encoding="text/html">
            <p>fallback HTML</p>
            </annotation-xml>
            </semantics>
            </math>
        """.trimIndent()).parse()

        // when
        div.appendSemanticEvents(events)

        // then
        div.child("math") should {
            child("semantics") should {
                have(namespaceURI == MATHML_NS)
                child("mrow") should {
                    have(namespaceURI == MATHML_NS)
                }
                child("annotation-xml") should {
                    have(namespaceURI == MATHML_NS)
                    child("p") should {
                        have(namespaceURI == HTML_NS)
                    }
                }
            }
        }
    }

    @Test
    fun `should keep annotation-xml children in MathML when encoding is missing`() = runTest {
        // given
        val div = document.createElement("div")
        val events = flowOf("""
            <math xmlns="$MATHML_NS">
            <semantics>
            <annotation-xml>
            <ci>x</ci>
            </annotation-xml>
            </semantics>
            </math>
        """.trimIndent()).parse()

        // when
        div.appendSemanticEvents(events)

        // then
        div.child("math") should {
            child("semantics") should {
                child("annotation-xml") should {
                    have(namespaceURI == MATHML_NS)
                    child("ci") should {
                        have(namespaceURI == MATHML_NS)
                    }
                }
            }
        }
    }

    @Test
    fun `should keep annotation-xml children in MathML when encoding is not HTML`() = runTest {
        // given
        val div = document.createElement("div")
        val events = flowOf("""
            <math xmlns="$MATHML_NS">
            <semantics>
            <annotation-xml encoding="MathML-Content">
            <ci>x</ci>
            </annotation-xml>
            </semantics>
            </math>
        """.trimIndent()).parse()

        // when
        div.appendSemanticEvents(events)

        // then
        div.child("math") should {
            child("semantics") should {
                child("annotation-xml") should {
                    have(namespaceURI == MATHML_NS)
                    child("ci") should {
                        have(namespaceURI == MATHML_NS)
                    }
                }
            }
        }
    }

    @Test
    fun `should keep HTML siblings in HTML namespace next to an SVG block`() = runTest {
        // given
        val div = document.createElement("div")
        val events = flowOf("""
            <p>before</p>

            <svg xmlns="$SVG_NS">
            <rect width="10" height="10"/>
            </svg>

            <p>after</p>
        """.trimIndent()).parse()

        // when
        div.appendSemanticEvents(events)

        // then
        div.child("p") should {
            have(namespaceURI == HTML_NS)
        }
        div.child("svg") should {
            have(namespaceURI == SVG_NS)
            child("rect") should {
                have(namespaceURI == SVG_NS)
            }
        }
        val ps = (0 until div.children.length)
            .mapNotNull { div.children.item(it) }
            .filter { it.localName == "p" }
        assert(ps.size == 2)
        for (p in ps) {
            p should {
                have(namespaceURI == HTML_NS)
            }
        }
    }

    @Test
    fun `should handle SVG nested inside HTML inside foreignObject inside SVG`() = runTest {
        // given
        val div = document.createElement("div")
        val events = flowOf("""
            <svg xmlns="$SVG_NS">
            <foreignObject width="100" height="100">
            <div>
            <svg xmlns="$SVG_NS">
            <circle r="5"/>
            </svg>
            </div>
            </foreignObject>
            </svg>
        """.trimIndent()).parse()

        // when
        div.appendSemanticEvents(events)

        // then
        div.child("svg") should {
            have(namespaceURI == SVG_NS)
            child("foreignObject") should {
                have(namespaceURI == SVG_NS)
                child("div") should {
                    have(namespaceURI == HTML_NS)
                    child("svg") should {
                        have(namespaceURI == SVG_NS)
                        child("circle") should {
                            have(namespaceURI == SVG_NS)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `should set xlink href attribute in XLink namespace`() = runTest {
        // given
        val div = document.createElement("div")
        val events = flowOf("""
            <svg xmlns="$SVG_NS" xmlns:xlink="$XLINK_NS">
            <use xlink:href="#icon"/>
            </svg>
        """.trimIndent()).parse()

        // when
        div.appendSemanticEvents(events)

        // then
        div.child("svg") should {
            have(getAttributeNS(XMLNS_NS, "xlink") == XLINK_NS)
            child("use") should {
                have(getAttributeNS(XLINK_NS, "href") == "#icon")
            }
        }
    }

    @Test
    fun `should set xml lang attribute in XML namespace`() = runTest {
        // given
        val div = document.createElement("div")
        val events = flowOf("""
            <svg xmlns="$SVG_NS">
            <text xml:lang="en" xml:space="preserve">Hello</text>
            </svg>
        """.trimIndent()).parse()

        // when
        div.appendSemanticEvents(events)

        // then
        div.child("svg") should {
            child("text") should {
                have(getAttributeNS(XML_NS, "lang") == "en")
                have(getAttributeNS(XML_NS, "space") == "preserve")
            }
        }
    }

    @Test
    fun `should set plain attributes without namespace on SVG element`() = runTest {
        // given
        val div = document.createElement("div")
        val events = flowOf("""
            <svg xmlns="$SVG_NS" viewBox="0 0 100 100">
            <rect width="10" height="10" fill="red"/>
            </svg>
        """.trimIndent()).parse()

        // when
        div.appendSemanticEvents(events)

        // then
        div.child("svg") should {
            child("rect") should {
                have(getAttribute("width") == "10")
                have(getAttribute("height") == "10")
                have(getAttribute("fill") == "red")
                have(getAttributeNS(null, "width") == "10")
            }
        }
    }

    @Test
    fun `should round-trip svg through toSemanticEvents and appendSemanticEvents preserving namespaces`() = runTest {
        // given
        val source = document.createElement("div")
        source.innerHTML = """<svg xmlns="http://www.w3.org/2000/svg"><g><rect width="10" height="10"></rect></g></svg>"""
        val sourceSvg = source.child("svg")
        sourceSvg should {
            have(namespaceURI == SVG_NS)
        }

        val target = document.createElement("div")
        val events = flowOf("""
            <svg xmlns="$SVG_NS">
            <g>
            <rect width="10" height="10"/>
            </g>
            </svg>
        """.trimIndent()).parse()

        // when
        target.appendSemanticEvents(events)

        // then
        target.child("svg") should {
            have(namespaceURI == sourceSvg.namespaceURI)
            child("g") should {
                have(namespaceURI == SVG_NS)
                child("rect") should {
                    have(namespaceURI == SVG_NS)
                }
            }
        }
    }

}

private const val HTML_NS = "http://www.w3.org/1999/xhtml"
private const val SVG_NS = "http://www.w3.org/2000/svg"
private const val MATHML_NS = "http://www.w3.org/1998/Math/MathML"
private const val XLINK_NS = "http://www.w3.org/1999/xlink"
private const val XML_NS = "http://www.w3.org/XML/1998/namespace"
private const val XMLNS_NS = "http://www.w3.org/2000/xmlns/"

private fun Element.child(
    name: String
): Element = requireNotNull(
    (0 until children.length).asSequence()
        .mapNotNull { children.item(it) }
        .firstOrNull { it.localName == name }
) { "no child element with localName='$name'" }
