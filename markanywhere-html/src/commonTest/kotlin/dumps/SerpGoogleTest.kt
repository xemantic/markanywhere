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

package com.xemantic.markanywhere.html.dumps

import com.xemantic.kotlin.test.sameAs
import com.xemantic.markanywhere.html.DumpFixtures
import com.xemantic.markanywhere.html.dumpFlow
import com.xemantic.markanywhere.html.transformHtmlToMarkdown
import com.xemantic.markanywhere.parse.parse
import com.xemantic.markanywhere.render.renderMarkdown
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SerpGoogleTest {

    @Test
    fun `should convert captured serp-google DOM dump to Markdown`() = runTest {
        // given
        val events = dumpFlow(DumpFixtures.serpGoogle)

        // when
        val markdown = events.transformHtmlToMarkdown().renderMarkdown()

        // then
        markdown sameAs /* language=markdown */ """
            ---
            lang: en-DE
            title: markdown parser - Google Search
            ---
            
            <div ref="1">![Google](https://fonts.gstatic.com/s/i/productlogos/googleg/v6/24px.svg)
            
            <button id="vc3jof" aria-label="Language: ‪English‬" aria-expanded="false" aria-haspopup="true" aria-disabled="false" ref="2">
            
            <div>en</div>
            
            </button>
            <button id="gksS1d" ref="3">
            
            <div>Sign in</div>
            
            </button>
            
            # Before you continue to Google
            
            We use [cookies](ref:4:https://policies.google.com/technologies/cookies?utm_source=ucbs&hl=en-DE) and data to
            
            - Deliver and maintain Google services
            - Track outages and protect against spam, fraud and abuse
            - Measure audience engagement and site statistics to understand how our services are used and enhance the quality of those services
            
            If you choose to 'Accept all', we will also use cookies and data to
            
            - Develop and improve new services
            - Deliver and measure the effectiveness of ads
            - Show personalised content, depending on your settings
            - Show personalised ads, depending on your settings
            
            If you choose to 'Reject all', we will not use cookies for these additional purposes.Non-personalised content is influenced by things like the content that you’re currently viewing, activity in your active Search session, and your location. Non-personalised ads are influenced by the content that you’re currently viewing and your general location. Personalised content and ads can also include more relevant results, recommendations and tailored ads based on past activity from this browser, like previous Google searches. We also use cookies and data to tailor the experience to be age-appropriate, if relevant.Select 'More options' to see additional information, including details about managing your privacy settings. You can also visit g.co/privacytools at any time.
            
            <button id="W0wltc" ref="5">
            
            <div>Reject all</div>
            
            </button>
            <button id="L2AGLb" ref="6">
            
            <div>Accept all</div>
            
            </button>
            <button id="VnjCcb" ref="7">
            
            [More options](ref:8:)
            
            </button>
            
            [Privacy](ref:9:https://policies.google.com/privacy?hl=en-DE&fg=1&utm_source=ucbs)[Terms](ref:10:https://policies.google.com/terms?hl=en-DE&fg=1&utm_source=ucbs)</div>
        """.trimIndent()
    }

    @Test
    fun `should round-trip the rendered Markdown to a stable fixpoint`() = runTest {
        // given — the Markdown the pipeline produces for the Google SERP dump
        val markdown = dumpFlow(DumpFixtures.serpGoogle).transformHtmlToMarkdown().renderMarkdown()

        // when
        val roundtripped = flowOf(markdown).parse().renderMarkdown()

        // then — a clean fixpoint: parsing and re-rendering reproduces the
        // pipeline Markdown exactly. The Google capture is a cookie-consent wall
        // with no bare URLs to re-autolink and no headings nested inside list
        // items, so it round-trips without any divergence.
        roundtripped sameAs markdown
    }

}
