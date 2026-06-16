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
import com.xemantic.markanywhere.render.renderMarkdown
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
        markdown sameAs /* language=markdown */ """
            ---
            lang: en
            title: Hacker News
            ---
            
            [![](y18.svg)](ref:1:https://news.ycombinator.com) <b>[Hacker News](ref:2:news)</b>
            
            [new](ref:3:newest) | [past](ref:4:front) | [comments](ref:5:newcomments) | [ask](ref:6:ask) | [show](ref:7:show) | [jobs](ref:8:jobs) | [submit](ref:9:submit) [login](ref:10:login?goto=news)
            
            |  |  |  |
            | --- | --- | --- |
            | 1. | [](ref:11:vote?id=48529990&how=up&goto=news) | [Show HN: Kage – Shadow any website to a single binary for offline viewing](ref:12:https://github.com/tamnd/kage) ([github.com/tamnd](ref:13:from?site=github.com/tamnd)) |
            |  |  | 164 points by [tamnd](ref:14:user?id=tamnd) [2 hours ago](ref:15:item?id=48529990) \| [hide](ref:16:hide?id=48529990&goto=news) \| [37 comments](ref:17:item?id=48529990) |
            | 2. | [](ref:18:vote?id=48531449&how=up&goto=news) | [Chaosnet (1981)](ref:19:https://tumbleweed.nu/r/lm-3/uv/amber.html) ([tumbleweed.nu](ref:20:from?site=tumbleweed.nu)) |
            |  |  | 27 points by [RGBCube](ref:21:user?id=RGBCube) [1 hour ago](ref:22:item?id=48531449) \| [hide](ref:23:hide?id=48531449&goto=news) \| [2 comments](ref:24:item?id=48531449) |
            | 3. | [](ref:25:vote?id=48528371&how=up&goto=news) | [Rio de Janeiro's "homegrown" LLM appears to be a merge of an existing model](ref:26:https://github.com/nex-agi/Nex-N2/issues/4) ([github.com/nex-agi](ref:27:from?site=github.com/nex-agi)) |
            |  |  | 196 points by [unrvl22](ref:28:user?id=unrvl22) [4 hours ago](ref:29:item?id=48528371) \| [hide](ref:30:hide?id=48528371&goto=news) \| [112 comments](ref:31:item?id=48528371) |
            | 4. | [](ref:32:vote?id=48471638&how=up&goto=news) | [Firewood Splitting Simulator](ref:33:https://screen.toys/firewood/) ([screen.toys](ref:34:from?site=screen.toys)) |
            |  |  | 459 points by [memalign](ref:35:user?id=memalign) [9 hours ago](ref:36:item?id=48471638) \| [hide](ref:37:hide?id=48471638&goto=news) \| [160 comments](ref:38:item?id=48471638) |
            | 5. | [](ref:39:vote?id=48489636&how=up&goto=news) | [Segmented type appreciation corner (2018)](ref:40:https://aresluna.org/segmented-type/) ([aresluna.org](ref:41:from?site=aresluna.org)) |
            |  |  | 37 points by [unexpectedVCR](ref:42:user?id=unexpectedVCR) [2 hours ago](ref:43:item?id=48489636) \| [hide](ref:44:hide?id=48489636&goto=news) \| [6 comments](ref:45:item?id=48489636) |
            | 6. | [](ref:46:vote?id=48531986&how=up&goto=news) | [Bring Siri AI to EU iPhone Users Safely](ref:47:https://siri4eu.com) ([siri4eu.com](ref:48:from?site=siri4eu.com)) |
            |  |  | 5 points by [peterspath](ref:49:user?id=peterspath) [19 minutes ago](ref:50:item?id=48531986) \| [hide](ref:51:hide?id=48531986&goto=news) \| [1 comment](ref:52:item?id=48531986) |
            | 7. | [](ref:53:vote?id=48528779&how=up&goto=news) | [Ask HN: What are you working on? (June 2026)](ref:54:item?id=48528779) |
            |  |  | 87 points by [david927](ref:55:user?id=david927) [4 hours ago](ref:56:item?id=48528779) \| [hide](ref:57:hide?id=48528779&goto=news) \| [336 comments](ref:58:item?id=48528779) |
            | 8. | [](ref:59:vote?id=48527145&how=up&goto=news) | [Caddy compatibility for zeroserve: 3x throughput and 70% lower latency](ref:60:https://su3.io/posts/zeroserve-caddy-compat) ([su3.io](ref:61:from?site=su3.io)) |
            |  |  | 116 points by [losfair](ref:62:user?id=losfair) [6 hours ago](ref:63:item?id=48527145) \| [hide](ref:64:hide?id=48527145&goto=news) \| [35 comments](ref:65:item?id=48527145) |
            | 9. | [](ref:66:vote?id=48527820&how=up&goto=news) | [Perlisisms](ref:67:https://www.cs.yale.edu/homes/perlis-alan/quotes.html) ([yale.edu](ref:68:from?site=yale.edu)) |
            |  |  | 67 points by [tosh](ref:69:user?id=tosh) [5 hours ago](ref:70:item?id=48527820) \| [hide](ref:71:hide?id=48527820&goto=news) \| [33 comments](ref:72:item?id=48527820) |
            | 10. | [](ref:73:vote?id=48526633&how=up&goto=news) | [Formal methods and the future of programming](ref:74:https://blog.janestreet.com/formal-methods-at-jane-street-index/?from_theconsensus=1) ([janestreet.com](ref:75:from?site=janestreet.com)) |
            |  |  | 116 points by [eatonphil](ref:76:user?id=eatonphil) [7 hours ago](ref:77:item?id=48526633) \| [hide](ref:78:hide?id=48526633&goto=news) \| [34 comments](ref:79:item?id=48526633) |
            | 11. | [](ref:80:vote?id=48485277&how=up&goto=news) | [Inverse Rubric Optimization: A testbed for agent science](ref:81:https://fulcrum.inc/2026/06/09/inverse-rubric-optimization.html) ([fulcrum.inc](ref:82:from?site=fulcrum.inc)) |
            |  |  | 6 points by [etherio](ref:83:user?id=etherio) [1 hour ago](ref:84:item?id=48485277) \| [hide](ref:85:hide?id=48485277&goto=news) \| [discuss](ref:86:item?id=48485277) |
            | 12. | [](ref:87:vote?id=48527360&how=up&goto=news) | [FarOutCompany](ref:88:https://faroutcompany.com/) ([faroutcompany.com](ref:89:from?site=faroutcompany.com)) |
            |  |  | 78 points by [bookofjoe](ref:90:user?id=bookofjoe) [6 hours ago](ref:91:item?id=48527360) \| [hide](ref:92:hide?id=48527360&goto=news) \| [12 comments](ref:93:item?id=48527360) |
            | 13. | [](ref:94:vote?id=48526661&how=up&goto=news) | [The Birth and Death of JavaScript (2014)](ref:95:https://www.destroyallsoftware.com/talks/the-birth-and-death-of-javascript) ([destroyallsoftware.com](ref:96:from?site=destroyallsoftware.com)) |
            |  |  | 182 points by [subset](ref:97:user?id=subset) [7 hours ago](ref:98:item?id=48526661) \| [hide](ref:99:hide?id=48526661&goto=news) \| [112 comments](ref:100:item?id=48526661) |
            | 14. | [](ref:101:vote?id=48491048&how=up&goto=news) | [Lisp's Influence on Ruby](ref:102:https://blog.tacoda.dev/lisps-influence-on-ruby-6a54f1a7740e) ([tacoda.dev](ref:103:from?site=tacoda.dev)) |
            |  |  | 184 points by [tacoda](ref:104:user?id=tacoda) [7 hours ago](ref:105:item?id=48491048) \| [hide](ref:106:hide?id=48491048&goto=news) \| [30 comments](ref:107:item?id=48491048) |
            | 15. | [](ref:108:vote?id=48527623&how=up&goto=news) | [Show HN: Dual YOLOv8n UAV Detection on RK3588S at 42 FPS Using NPU](ref:109:https://github.com/alebal123bal/khadas_yolov8n_multithread) ([github.com/alebal123bal](ref:110:from?site=github.com/alebal123bal)) |
            |  |  | 55 points by [alebal123bal](ref:111:user?id=alebal123bal) [5 hours ago](ref:112:item?id=48527623) \| [hide](ref:113:hide?id=48527623&goto=news) \| [9 comments](ref:114:item?id=48527623) |
            | 16. | [](ref:115:vote?id=48521236&how=up&goto=news) | [Show HN: Trace – Offline Mac meeting transcripts you can flag mid-call](ref:116:https://traceapp.info) ([traceapp.info](ref:117:from?site=traceapp.info)) |
            |  |  | 8 points by [AG342](ref:118:user?id=AG342) [1 hour ago](ref:119:item?id=48521236) \| [hide](ref:120:hide?id=48521236&goto=news) \| [2 comments](ref:121:item?id=48521236) |
            | 17. | [](ref:122:vote?id=48528029&how=up&goto=news) | [I indexed 669 GB of my GoPro videos using my M1 Max computer and local ML models](ref:123:item?id=48528029) |
            |  |  | 162 points by [iliashad](ref:124:user?id=iliashad) [5 hours ago](ref:125:item?id=48528029) \| [hide](ref:126:hide?id=48528029&goto=news) \| [34 comments](ref:127:item?id=48528029) |
            | 18. | [](ref:128:vote?id=48492822&how=up&goto=news) | [The only scalable delete in Postgres is DROP TABLE](ref:129:https://planetscale.com/blog/the-only-scalable-delete) ([planetscale.com](ref:130:from?site=planetscale.com)) |
            |  |  | 91 points by [hollylawly](ref:131:user?id=hollylawly) [7 hours ago](ref:132:item?id=48492822) \| [hide](ref:133:hide?id=48492822&goto=news) \| [39 comments](ref:134:item?id=48492822) |
            | 19. | [](ref:135:vote?id=48527700&how=up&goto=news) | [Not everyone is using AI for everything](ref:136:https://gabrielweinberg.com/p/people-are-consuming-ai-like-they) ([gabrielweinberg.com](ref:137:from?site=gabrielweinberg.com)) |
            |  |  | 337 points by [yegg](ref:138:user?id=yegg) [5 hours ago](ref:139:item?id=48527700) \| [hide](ref:140:hide?id=48527700&goto=news) \| [353 comments](ref:141:item?id=48527700) |
            | 20. | [](ref:142:vote?id=48530841&how=up&goto=news) | [Rome Fell and Nobody Noticed](ref:143:https://friedkielbasa.substack.com/p/rome-fell-and-nobody-noticed) ([friedkielbasa.substack.com](ref:144:from?site=friedkielbasa.substack.com)) |
            |  |  | 70 points by [fkozlowski](ref:145:user?id=fkozlowski) [1 hour ago](ref:146:item?id=48530841) \| [hide](ref:147:hide?id=48530841&goto=news) \| [14 comments](ref:148:item?id=48530841) |
            | 21. | [](ref:149:vote?id=48489309&how=up&goto=news) | [USB Power Delivery: Plugging into the Benefits](ref:150:https://www.aptiv.com/en/insights/article/usb-power-delivery-plugging-into-the-benefits) ([aptiv.com](ref:151:from?site=aptiv.com)) |
            |  |  | 22 points by [mooreds](ref:152:user?id=mooreds) [3 hours ago](ref:153:item?id=48489309) \| [hide](ref:154:hide?id=48489309&goto=news) \| [37 comments](ref:155:item?id=48489309) |
            | 22. | [](ref:156:vote?id=48528729&how=up&goto=news) | [Linux 7.1](ref:157:https://lore.kernel.org/lkml/CAHk-=wi4BF4bMhZNZ1tqs+FFV4OuZRe3ZqdWB+LxRLmRweUzQw@mail.gmail.com/T/#u) ([kernel.org](ref:158:from?site=kernel.org)) |
            |  |  | 146 points by [berlianta](ref:159:user?id=berlianta) [4 hours ago](ref:160:item?id=48528729) \| [hide](ref:161:hide?id=48528729&goto=news) \| [31 comments](ref:162:item?id=48528729) |
            | 23. | [](ref:163:vote?id=48519102&how=up&goto=news) | [Global density and biomass of arbuscular mycorrhizal fungal networks](ref:164:https://www.science.org/doi/10.1126/science.adu4373) ([science.org](ref:165:from?site=science.org)) |
            |  |  | 34 points by [zdw](ref:166:user?id=zdw) [5 hours ago](ref:167:item?id=48519102) \| [hide](ref:168:hide?id=48519102&goto=news) \| [3 comments](ref:169:item?id=48519102) |
            | 24. | [](ref:170:vote?id=48460762&how=up&goto=news) | [Show HN: 3D print Z reinforcement via injected loops](ref:171:https://mgunlogson.github.io/magma/) ([mgunlogson.github.io](ref:172:from?site=mgunlogson.github.io)) |
            |  |  | 35 points by [mgunlogson](ref:173:user?id=mgunlogson) [5 hours ago](ref:174:item?id=48460762) \| [hide](ref:175:hide?id=48460762&goto=news) \| [10 comments](ref:176:item?id=48460762) |
            | 25. | [](ref:177:vote?id=48474982&how=up&goto=news) | [Quivers: A year of linear algebra by drawing arrows](ref:178:https://lisyarus.github.io/blog/posts/quivers-a-year-of-linear-algebra-by-drawing-arrows.html) ([lisyarus.github.io](ref:179:from?site=lisyarus.github.io)) |
            |  |  | 26 points by [ibobev](ref:180:user?id=ibobev) [4 hours ago](ref:181:item?id=48474982) \| [hide](ref:182:hide?id=48474982&goto=news) \| [4 comments](ref:183:item?id=48474982) |
            | 26. | [](ref:184:vote?id=48526360&how=up&goto=news) | [How to earn a billion dollars](ref:185:https://paulgraham.com/earn.html) ([paulgraham.com](ref:186:from?site=paulgraham.com)) |
            |  |  | 318 points by [kingstoned](ref:187:user?id=kingstoned) [8 hours ago](ref:188:item?id=48526360) \| [hide](ref:189:hide?id=48526360&goto=news) \| [903 comments](ref:190:item?id=48526360) |
            | 27. | [](ref:191:vote?id=48527634&how=up&goto=news) | [Rio de Janeiro's city government model Rio3.5 beats Qwen3.7 in recent benchmarks](ref:192:https://twitter.com/zenmagnets/status/2065796012820848699) ([twitter.com/zenmagnets](ref:193:from?site=twitter.com/zenmagnets)) |
            |  |  | 123 points by [lucasfcosta](ref:194:user?id=lucasfcosta) [5 hours ago](ref:195:item?id=48527634) \| [hide](ref:196:hide?id=48527634&goto=news) \| [33 comments](ref:197:item?id=48527634) |
            | 28. | [](ref:198:vote?id=48523992&how=up&goto=news) | [Free SQL→ER diagram tool, runs in the browser, nothing uploaded](ref:199:https://sqltoerdiagram.com/) ([sqltoerdiagram.com](ref:200:from?site=sqltoerdiagram.com)) |
            |  |  | 322 points by [robhati](ref:201:user?id=robhati) [16 hours ago](ref:202:item?id=48523992) \| [hide](ref:203:hide?id=48523992&goto=news) \| [64 comments](ref:204:item?id=48523992) |
            | 29. | [](ref:205:vote?id=48523080&how=up&goto=news) | [Honda Civics and the Evil Valet](ref:206:https://juniperspring.org/posts/honda-evil-valet/) ([juniperspring.org](ref:207:from?site=juniperspring.org)) |
            |  |  | 372 points by [librick](ref:208:user?id=librick) [19 hours ago](ref:209:item?id=48523080) \| [hide](ref:210:hide?id=48523080&goto=news) \| [89 comments](ref:211:item?id=48523080) |
            | 30. | [](ref:212:vote?id=48526793&how=up&goto=news) | [How did Atari apply side art to Arcade Cabinets?](ref:213:https://arcadeblogger.com/2026/06/14/how-did-atari-apply-side-art-to-arcade-cabinets/) ([arcadeblogger.com](ref:214:from?site=arcadeblogger.com)) |
            |  |  | 59 points by [msephton](ref:215:user?id=msephton) [7 hours ago](ref:216:item?id=48526793) \| [hide](ref:217:hide?id=48526793&goto=news) \| [16 comments](ref:218:item?id=48526793) |
            |  |  | [More](ref:219:?p=2) |
            
            ![](s.gif)   
            
             [Guidelines](ref:220:newsguidelines.html) | [FAQ](ref:221:newsfaq.html) | [Lists](ref:222:lists) | [API](ref:223:https://github.com/HackerNews/API) | [Security](ref:224:security.html) | [Legal](ref:225:https://www.ycombinator.com/legal/) | [Apply to YC](ref:226:https://www.ycombinator.com/apply/) | [Contact](ref:227:mailto:hn@ycombinator.com)
            
            <form action="//hn.algolia.com/" method="get">
            
            Search: <input type="text" name="q" ref="228">
            
            </form>
        """.trimIndent()
    }

}
