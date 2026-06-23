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

class HackerNewsTest {

    @Test
    fun `should convert captured hackernews DOM dump to Markdown`() = runTest {
        // given
        val events = dumpFlow(DumpFixtures.hackernews)

        // when
        val markdown = events.transformHtmlToMarkdown().renderMarkdown()

        // then
        markdown sameAs /* language=markdown */ $$"""
            ---
            lang: en
            title: Hacker News
            ---
            
            [![](y18.svg)](ref:1:https://news.ycombinator.com) <b>[Hacker News](ref:2:news)</b>
            
            [new](ref:3:newest) | [past](ref:4:front) | [comments](ref:5:newcomments) | [ask](ref:6:ask) | [show](ref:7:show) | [jobs](ref:8:jobs) | [submit](ref:9:submit) [login](ref:10:login?goto=news)
            
            |  |  |  |
            | --- | --- | --- |
            | 1. | [](ref:11:vote?id=48560847&how=up&goto=news) | [U.S. pulling ocean sensors a 'shock' for Canadian research as El Niño nears](ref:12:https://www.timescolonist.com/local-news/us-pulling-ocean-sensors-a-shock-for-canadian-research-as-el-nino-nears-12422874) ([timescolonist.com](ref:13:from?site=timescolonist.com)) |
            |  |  | 132 points by [ResearchAtPlay](ref:14:user?id=ResearchAtPlay) [31 minutes ago](ref:15:item?id=48560847) \| [hide](ref:16:hide?id=48560847&goto=news) \| [33 comments](ref:17:item?id=48560847) |
            | 2. | [](ref:18:vote?id=48555993&how=up&goto=news) | [Running local models is good now](ref:19:https://vickiboykis.com/2026/06/15/running-local-models-is-good-now/) ([vickiboykis.com](ref:20:from?site=vickiboykis.com)) |
            |  |  | 715 points by [jfb](ref:21:user?id=jfb) [5 hours ago](ref:22:item?id=48555993) \| [hide](ref:23:hide?id=48555993&goto=news) \| [332 comments](ref:24:item?id=48555993) |
            | 3. | [](ref:25:vote?id=48559935&how=up&goto=news) | [Apple is about to make Hide My Email useless](ref:26:https://arseniyshestakov.com/2026/06/16/apple-is-about-to-make-hide-my-email-useless/) ([arseniyshestakov.com](ref:27:from?site=arseniyshestakov.com)) |
            |  |  | 81 points by [SXX](ref:28:user?id=SXX) [1 hour ago](ref:29:item?id=48559935) \| [hide](ref:30:hide?id=48559935&goto=news) \| [27 comments](ref:31:item?id=48559935) |
            | 4. | [](ref:32:vote?id=48553224&how=up&goto=news) | [SpaceX to buy Cursor for $60B](ref:33:https://www.reuters.com/legal/transactional/spacex-buy-anysphere-60-billion-2026-06-16/) ([reuters.com](ref:34:from?site=reuters.com)) |
            |  |  | 641 points by [itsmarcelg](ref:35:user?id=itsmarcelg) [9 hours ago](ref:36:item?id=48553224) \| [hide](ref:37:hide?id=48553224&goto=news) \| [1062 comments](ref:38:item?id=48553224) |
            | 5. | [](ref:39:vote?id=48558018&how=up&goto=news) | [TIL: You can make HTTP requests without curl using Bash /dev/TCP](ref:40:https://mareksuppa.com/til/bash-dev-tcp-http-without-curl/) ([mareksuppa.com](ref:41:from?site=mareksuppa.com)) |
            |  |  | 138 points by [mrshu](ref:42:user?id=mrshu) [3 hours ago](ref:43:item?id=48558018) \| [hide](ref:44:hide?id=48558018&goto=news) \| [74 comments](ref:45:item?id=48558018) |
            | 6. | [](ref:46:vote?id=48557079&how=up&goto=news) | [Calvin and Hobbes and the price of integrity](ref:47:https://therepublicofletters.substack.com/p/calvin-and-hobbes-and-the-price-of) ([therepublicofletters.substack.com](ref:48:from?site=therepublicofletters.substack.com)) |
            |  |  | 94 points by [pseudolus](ref:49:user?id=pseudolus) [3 hours ago](ref:50:item?id=48557079) \| [hide](ref:51:hide?id=48557079&goto=news) \| [31 comments](ref:52:item?id=48557079) |
            | 7. | [](ref:53:vote?id=48553550&how=up&goto=news) | [Mechanical Watch (2022)](ref:54:https://ciechanow.ski/mechanical-watch/) ([ciechanow.ski](ref:55:from?site=ciechanow.ski)) |
            |  |  | 557 points by [razin](ref:56:user?id=razin) [8 hours ago](ref:57:item?id=48553550) \| [hide](ref:58:hide?id=48553550&goto=news) \| [107 comments](ref:59:item?id=48553550) |
            | 8. | [](ref:60:vote?id=48555838&how=up&goto=news) | [But yak shaving is fun](ref:61:https://parksb.github.io/en/article/32.html) ([parksb.github.io](ref:62:from?site=parksb.github.io)) |
            |  |  | 140 points by [parksb](ref:63:user?id=parksb) [5 hours ago](ref:64:item?id=48555838) \| [hide](ref:65:hide?id=48555838&goto=news) \| [35 comments](ref:66:item?id=48555838) |
            | 9. | [](ref:67:vote?id=48559188&how=up&goto=news) | [GPT‑NL: a sovereign language model for the Netherlands](ref:68:https://www.tno.nl/en/digital/artificial-intelligence/gpt-nl/) ([tno.nl](ref:69:from?site=tno.nl)) |
            |  |  | 61 points by [root-parent](ref:70:user?id=root-parent) [2 hours ago](ref:71:item?id=48559188) \| [hide](ref:72:hide?id=48559188&goto=news) \| [46 comments](ref:73:item?id=48559188) |
            | 10. | [](ref:74:vote?id=48559083&how=up&goto=news) | [10Gb/s Ethernet: switching to a Broadcom SFP+ module](ref:75:https://www.gilesthomas.com/2026/06/10g-ethernet-switching-to-broadcom-sfp-plus) ([gilesthomas.com](ref:76:from?site=gilesthomas.com)) |
            |  |  | 42 points by [gpjt](ref:77:user?id=gpjt) [2 hours ago](ref:78:item?id=48559083) \| [hide](ref:79:hide?id=48559083&goto=news) \| [35 comments](ref:80:item?id=48559083) |
            | 11. | [](ref:81:vote?id=48558766&how=up&goto=news) | [Claude: Elevated errors across many models](ref:82:https://status.claude.com/incidents/xmhsglsz3h3w) ([claude.com](ref:83:from?site=claude.com)) |
            |  |  | 149 points by [forks](ref:84:user?id=forks) [2 hours ago](ref:85:item?id=48558766) \| [hide](ref:86:hide?id=48558766&goto=news) \| [135 comments](ref:87:item?id=48558766) |
            | 12. | [](ref:88:vote?id=48557530&how=up&goto=news) | [Apple's weird anti-nausea dots cured my car sickness](ref:89:https://www.theverge.com/tech/942854/apple-vehicle-motion-cues-review-really-work) ([theverge.com](ref:90:from?site=theverge.com)) |
            |  |  | 336 points by [neilfrndes](ref:91:user?id=neilfrndes) [4 hours ago](ref:92:item?id=48557530) \| [hide](ref:93:hide?id=48557530&goto=news) \| [108 comments](ref:94:item?id=48557530) |
            | 13. | [](ref:95:vote?id=48550779&how=up&goto=news) | [I admire Fabrice Bellard. He is almost certainly a better overall programmer](ref:96:https://twitter.com/ID_AA_Carmack/status/2064095424420487226) ([twitter.com/id_aa_carmack](ref:97:from?site=twitter.com/id_aa_carmack)) |
            |  |  | 803 points by [apitman](ref:98:user?id=apitman) [15 hours ago](ref:99:item?id=48550779) \| [hide](ref:100:hide?id=48550779&goto=news) \| [372 comments](ref:101:item?id=48550779) |
            | 14. | [](ref:102:vote?id=48552844&how=up&goto=news) | [Correlated randomness in Slay the Spire 2](ref:103:https://tck.mn/blog/correlated-randomness-sts2/) ([tck.mn](ref:104:from?site=tck.mn)) |
            |  |  | 237 points by [rdmuser](ref:105:user?id=rdmuser) [9 hours ago](ref:106:item?id=48552844) \| [hide](ref:107:hide?id=48552844&goto=news) \| [76 comments](ref:108:item?id=48552844) |
            | 15. | [](ref:109:vote?id=48557768&how=up&goto=news) | [Making ast.walk 220x Faster](ref:110:https://reflex.dev/blog/why-ast-walk-when-you-can-ast-sprint/) ([reflex.dev](ref:111:from?site=reflex.dev)) |
            |  |  | 59 points by [palashawas](ref:112:user?id=palashawas) [3 hours ago](ref:113:item?id=48557768) \| [hide](ref:114:hide?id=48557768&goto=news) \| [12 comments](ref:115:item?id=48557768) |
            | 16. | [](ref:116:vote?id=48550693&how=up&goto=news) | [The time the x86 emulator team found code so bad they fixed it during emulation](ref:117:https://devblogs.microsoft.com/oldnewthing/20260615-00/?p=112419) ([devblogs.microsoft.com/oldnewthing](ref:118:from?site=devblogs.microsoft.com/oldnewthing)) |
            |  |  | 462 points by [paulmooreparks](ref:119:user?id=paulmooreparks) [15 hours ago](ref:120:item?id=48550693) \| [hide](ref:121:hide?id=48550693&goto=news) \| [149 comments](ref:122:item?id=48550693) |
            | 17. | [](ref:123:vote?id=48556163&how=up&goto=news) | [SubQ 1.1 Small](ref:124:https://subq.ai/subq-1-1-small-technical-report) ([subq.ai](ref:125:from?site=subq.ai)) |
            |  |  | 84 points by [EDM115](ref:126:user?id=EDM115) [5 hours ago](ref:127:item?id=48556163) \| [hide](ref:128:hide?id=48556163&goto=news) \| [39 comments](ref:129:item?id=48556163) |
            | 18. | [](ref:130:vote?id=48497067&how=up&goto=news) | [Formal Methods and the Future of Programming](ref:131:https://blog.janestreet.com/formal-methods-at-jane-street-index/) ([janestreet.com](ref:132:from?site=janestreet.com)) |
            |  |  | 39 points by [nextos](ref:133:user?id=nextos) [4 hours ago](ref:134:item?id=48497067) \| [hide](ref:135:hide?id=48497067&goto=news) \| [1 comment](ref:136:item?id=48497067) |
            | 19. | [](ref:137:vote?id=48554814&how=up&goto=news) | [Qwen-Robot Suite: A Foundation Model Suite for Physical World Intelligence](ref:138:https://qwen.ai/blog?id=qwen-robotsuite) ([qwen.ai](ref:139:from?site=qwen.ai)) |
            |  |  | 73 points by [ilreb](ref:140:user?id=ilreb) [6 hours ago](ref:141:item?id=48554814) \| [hide](ref:142:hide?id=48554814&goto=news) \| [11 comments](ref:143:item?id=48554814) |
            | 20. | [](ref:144:vote?id=48558147&how=up&goto=news) | [Stop Using JWTs](ref:145:https://gist.github.com/samsch/0d1f3d3b4745d778f78b230cf6061452) ([gist.github.com](ref:146:from?site=gist.github.com)) |
            |  |  | 89 points by [dzonga](ref:147:user?id=dzonga) [3 hours ago](ref:148:item?id=48558147) \| [hide](ref:149:hide?id=48558147&goto=news) \| [57 comments](ref:150:item?id=48558147) |
            | 21. | [](ref:151:vote?id=48558337&how=up&goto=news) | [Specs Augmented Reality Glasses](ref:152:https://newsroom.snap.com/introducing-specs-augmented-reality-glasses) ([snap.com](ref:153:from?site=snap.com)) |
            |  |  | 37 points by [haberdasher](ref:154:user?id=haberdasher) [3 hours ago](ref:155:item?id=48558337) \| [hide](ref:156:hide?id=48558337&goto=news) \| [22 comments](ref:157:item?id=48558337) |
            | 22. | [](ref:158:vote?id=48519723&how=up&goto=news) | [An interview with an Apple emoji designer](ref:159:https://shadycharacters.co.uk/2026/06/ollie-wagner/) ([shadycharacters.co.uk](ref:160:from?site=shadycharacters.co.uk)) |
            |  |  | 79 points by [nate](ref:161:user?id=nate) [7 hours ago](ref:162:item?id=48519723) \| [hide](ref:163:hide?id=48519723&goto=news) \| [41 comments](ref:164:item?id=48519723) |
            | 23. | [](ref:165:vote?id=48559108&how=up&goto=news) | [Show HN: Pen and paper resource development game with an emergent world](ref:166:https://www.jameshylands.co.uk/2026/06/sortis-paper-empire-game.html) ([jameshylands.co.uk](ref:167:from?site=jameshylands.co.uk)) |
            |  |  | 10 points by [jhylands](ref:168:user?id=jhylands) [2 hours ago](ref:169:item?id=48559108) \| [hide](ref:170:hide?id=48559108&goto=news) \| [discuss](ref:171:item?id=48559108) |
            | 24. | [](ref:172:vote?id=48558045&how=up&goto=news) | [Why is Meta destroying its engineering organization?](ref:173:https://newsletter.pragmaticengineer.com/p/why-is-meta-destroying-its-engineering) ([pragmaticengineer.com](ref:174:from?site=pragmaticengineer.com)) |
            |  |  | 239 points by [throwarayes](ref:175:user?id=throwarayes) [3 hours ago](ref:176:item?id=48558045) \| [hide](ref:177:hide?id=48558045&goto=news) \| [175 comments](ref:178:item?id=48558045) |
            | 25. | [](ref:179:vote?id=48558338&how=up&goto=news) | ['Ghost jobs' could soon be illegal in New York](ref:180:https://www.fastcompany.com/91558427/ghost-jobs-could-soon-be-illegal-in-new-york) ([fastcompany.com](ref:181:from?site=fastcompany.com)) |
            |  |  | 95 points by [toomuchtodo](ref:182:user?id=toomuchtodo) [3 hours ago](ref:183:item?id=48558338) \| [hide](ref:184:hide?id=48558338&goto=news) \| [48 comments](ref:185:item?id=48558338) |
            | 26. | [](ref:186:vote?id=48522316&how=up&goto=news) | [Getting Creative with Perlin Noise Fields](ref:187:https://sighack.com/post/getting-creative-with-perlin-noise-fields) ([sighack.com](ref:188:from?site=sighack.com)) |
            |  |  | 142 points by [0x000xca0xfe](ref:189:user?id=0x000xca0xfe) [12 hours ago](ref:190:item?id=48522316) \| [hide](ref:191:hide?id=48522316&goto=news) \| [22 comments](ref:192:item?id=48522316) |
            | 27. | [](ref:193:vote?id=48525340&how=up&goto=news) | [Show HN: Sabela – A Reactive Notebook for Haskell](ref:194:https://sabela.datahaskell.com/) ([datahaskell.com](ref:195:from?site=datahaskell.com)) |
            |  |  | 11 points by [mchav](ref:196:user?id=mchav) [1 hour ago](ref:197:item?id=48525340) \| [hide](ref:198:hide?id=48525340&goto=news) \| [discuss](ref:199:item?id=48525340) |
            | 28. | [](ref:200:vote?id=48553450&how=up&goto=news) | [Unicorn – The Ultimate CPU Emulator](ref:201:https://www.unicorn-engine.org/) ([unicorn-engine.org](ref:202:from?site=unicorn-engine.org)) |
            |  |  | 77 points by [tosh](ref:203:user?id=tosh) [8 hours ago](ref:204:item?id=48553450) \| [hide](ref:205:hide?id=48553450&goto=news) \| [23 comments](ref:206:item?id=48553450) |
            | 29. | [](ref:207:vote?id=48510375&how=up&goto=news) | [Cooling at the Speed of Light](ref:208:https://cacm.acm.org/news/cooling-at-the-speed-of-light/) ([acm.org](ref:209:from?site=acm.org)) |
            |  |  | 16 points by [sohkamyung](ref:210:user?id=sohkamyung) [2 hours ago](ref:211:item?id=48510375) \| [hide](ref:212:hide?id=48510375&goto=news) \| [2 comments](ref:213:item?id=48510375) |
            | 30. | [](ref:214:vote?id=48550569&how=up&goto=news) | [Show HN: Garden of Flowers – an archive of pictorial typography before ASCII art](ref:215:https://garden-of-flowers.heikkilotvonen.com/) ([heikkilotvonen.com](ref:216:from?site=heikkilotvonen.com)) |
            |  |  | 139 points by [california-og](ref:217:user?id=california-og) [15 hours ago](ref:218:item?id=48550569) \| [hide](ref:219:hide?id=48550569&goto=news) \| [24 comments](ref:220:item?id=48550569) |
            |  |  | [More](ref:221:?p=2) |
            
            ![](s.gif)
            
            [Guidelines](ref:222:newsguidelines.html) | [FAQ](ref:223:newsfaq.html) | [Lists](ref:224:lists) | [API](ref:225:https://github.com/HackerNews/API) | [Security](ref:226:security.html) | [Legal](ref:227:https://www.ycombinator.com/legal/) | [Apply to YC](ref:228:https://www.ycombinator.com/apply/) | [Contact](ref:229:mailto:hn@ycombinator.com)
            
            <form action="//hn.algolia.com/" method="get">
            
            Search: <input type="text" name="q" ref="230">
            
            </form>
        """.trimIndent()
    }

    @Test
    fun `should round-trip the rendered Markdown to a stable fixpoint`() = runTest {
        // given — the Markdown the pipeline produces for the Hacker News dump
        val markdown = dumpFlow(DumpFixtures.hackernews).transformHtmlToMarkdown().renderMarkdown()

        // when
        val roundtripped = flowOf(markdown).parse().renderMarkdown()

        // then — a clean fixpoint: parsing and re-rendering reproduces the
        // pipeline Markdown exactly. Unlike openJur there are no bare URLs to
        // re-autolink, and the renderer now drops the moot trailing hard break
        // and leading space the forward pipeline would otherwise leave in (so
        // there is no longer any divergence to document).
        roundtripped sameAs markdown
    }

}
