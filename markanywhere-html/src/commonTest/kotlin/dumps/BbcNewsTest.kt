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

class BbcNewsTest {

    @Test
    fun `should convert captured bbc-news DOM dump to Markdown`() = runTest {
        // given
        val events = dumpFlow(DumpFixtures.bbcNews)

        // when
        val markdown = events.transformHtmlToMarkdown().renderMarkdown()

        // then
        markdown sameAs /* language=markdown */ """
            ---
            lang: en-GB
            title: BBC News - Breaking news, video and the latest top stories from the U.S. and around the world
            page.section: News
            "twitter:title": BBC News - Breaking news, video and the latest top stories from the U.S. and around the world
            description: Visit BBC News for the latest news, breaking news, video, audio and analysis. BBC News provides trusted World, U.S. and U.K. news as well as local and regional perspectives. Also entertainment, climate, business, science, technology and health news.
            "twitter:description": Visit BBC News for the latest news, breaking news, video, audio and analysis. BBC News provides trusted World, U.S. and U.K. news as well as local and regional perspectives. Also entertainment, climate, business, science, technology and health news.
            version: web-3.9.0-5
            "cXenseParse:pageclass": frontpage
            ---
            
            [Skip to content](ref:1:#bbc-main)
            
            <header id="bbc-header">
            <button aria-label="Open menu" aria-expanded="false" ref="2">
            </button>
            
            [![British Broadcasting Corporation]()](ref:3:/)
            
            <a href="https://session.bbc.com/session?action=register&amp;userOrigin=BBCS_BBC&amp;ptrt=https%3A%2F%2Fwww.bbc.com%2Fnews" ref="4">
            <button type="button" aria-label="Register" ref="5">
            
            Register
            
            </button>
            </a>
            <a href="https://session.bbc.com/session?userOrigin=BBCS_BBC&amp;ptrt=https%3A%2F%2Fwww.bbc.com%2Fnews" ref="6">
            <button type="button" aria-label="Sign In" ref="7">
            
            Sign In
            
            </button>
            </a>
            </header>
            <nav id="main-navigation-container">
            <section>
            <nav>
            
            - [Home](ref:8:/)
            - [News](ref:9:/news)
            - [Football 2026](ref:10:/sport/football/world-cup)
            - [Sport](ref:11:/sport)
            - [Business](ref:12:/business)
            - [Technology](ref:13:/technology)
            - [Health](ref:14:/health)
            - [Culture](ref:15:/culture)
            - [Arts](ref:16:/arts)
            - [Travel](ref:17:/travel)
            - [Earth](ref:18:/future-planet)
            - [Audio](ref:19:/audio)
            - [Video](ref:20:/video)
            - [Live](ref:21:/live)
            
            </nav>
            </section>
            <nav>
            <nav>
            
            - [US & Canada](ref:22:/news/us-canada)
            - [UK](ref:23:/news/uk)
            - [Africa](ref:24:/news/world/africa)
            - [Asia](ref:25:/news/world/asia)
            - [Australia](ref:26:/news/world/australia)
            - [Europe](ref:27:/news/world/europe)
            - [Latin America](ref:28:/news/world/latin_america)
            - [Middle East](ref:29:/news/world/middle_east)
            - [In Pictures](ref:30:/news/in_pictures)
            - [BBC InDepth](ref:31:/news/bbcindepth)
            - [BBC Verify](ref:32:/news/bbcverify)
            
            </nav>
            </nav>
            </nav>
            <main id="bbc-main">
            <article>
            
            # ![News]()
            
            <section>
            <section>
            
            [![Russian frigate Admiral Grigorovich](https://ichef.bbci.co.uk/news/480/cpsprodpb/9b77/live/208679c0-699a-11f1-9685-4f5a66750059.jpg.webp)](ref:33:/news/articles/c20yzm84r7lo)
            
            <a href="/news/articles/c20yzm84r7lo" ref="34">
            
            ## UK investigating reports Russian warship fired warning shots near yacht in English Channel
            
            BBC News understands the yacht had drifted towards the Admiral Grigorovich, a Russian frigate which has been operating in the Channel.
            
            Just now Europe
            
            </a>
            <a href="/news/articles/c20ydx06ym2o" ref="35">
            
            ![Sir Richard Knighton, in RAF uniform, addresses a defence conference](https://ichef.bbci.co.uk/news/480/cpsprodpb/fa3e/live/1b787b00-698a-11f1-8e1d-bbbb1017d210.jpg.webp)
            
            ## UK forces face operational cuts without more cash, defence chief warns
            
            It comes as ex-defence secretary John Healey says the UK's enemies "do not follow timetables set by the Treasury".
            
            3 hrs ago UK
            
            </a>
            <a href="/news/articles/c20yzm58vk3o" ref="36">
            
            ## Hundreds of cats stolen for food in Vietnam rescued by police, welfare group says
            
            Police have arrested nine people and rescued more than 400 cats destined for slaughter, with more than 40 reunited with their owners.
            
            3 hrs ago Asia
            
            </a>
            <a href="/news/articles/c621zplqrrno" ref="37">
            
            ## Hungary's MPs block return of Orbán, limiting rule of PM to eight years
            
            The vote fulfils a promise by new PM Péter Magyar to stop his predecessor from becoming prime minister again.
            
            8 hrs ago Europe
            
            </a>
            <a href="/news/articles/cn4rwzxvl8ko" ref="38">
            
            ![A woman holds an Iranian flag on a street in Tehran, Iran.](https://ichef.bbci.co.uk/news/480/cpsprodpb/bd69/live/fa1ab490-698f-11f1-bf78-b9ee73ed2967.jpg.webp)
            
            ## Tehran selling deal with US as victory – but for Iranians it was necessity
            
            For many Iranians, the question is not whether the deal means victory, but whether it lowers prices and reduces fear of another war.
            
            5 hrs ago World
            
            </a>
            <a href="/news/articles/cn4rw784nj2o" ref="39">
            
            ![Ships on the Strait of Hormuz at sunset](https://ichef.bbci.co.uk/news/480/cpsprodpb/939d/live/e5acdee0-6983-11f1-b1db-af71d47507d6.png.webp)
            
            ## Three reasons ships are not going through the Strait of Hormuz yet
            
            Experts say that there are significant obstacles preventing traffic from returning to the levels seen before the conflict began – security, mines and tolls.
            
            6 hrs ago BBC Verify
            
            </a>
            <a href="/news/articles/c0jyzp9z9deo" ref="40">
            
            ![A man in a suit and tie stares at his phone while sitting down opposite a man in a blue jacket in Beijing](https://ichef.bbci.co.uk/news/480/cpsprodpb/c8f4/live/3b494590-6994-11f1-a716-b5396926119a.jpg.webp)
            
            ## German broadcaster removes TV intro after Elon Musk takes legal action
            
            ZDF TV responded to a "cease and desist" letter after the tech trillionaire condemned the broadcaster's "outrageous lies".
            
            2 hrs ago Europe
            
            </a>
            <a href="https://www.bbc.com/sport/football/live/cze9nr5eg2xt" ref="41">
            
            ![Nicolas Jackson hits the post](https://ichef.bbci.co.uk/ace/standard/480/cpsprodpb/8381/live/59947b60-69bb-11f1-8e1d-bbbb1017d210.jpg.webp) LIVE
            
            ## Watch as Senegal miss chances to take lead against France
            
            Watch France versus Senegal in World Cup Group I live on BBC One, listen to BBC Radio 5 Live commentary and follow live text coverage.
            
            </a>
            </section>
            </section>
            <section>
            <a href="/news/articles/cvgd5g7d7gyo" ref="42">
            
            ## Musk's SpaceX overtakes Amazon to become world's fifth most valuable firm
            
            </a>
            <a href="/news/articles/clyrzd5g6k2o" ref="43">
            
            ## Russian artist and Putin critic shot dead in Poland
            
            </a>
            <a href="/news/articles/cqx10xlje1lo" ref="44">
            
            ## Group planned to attack White House UFC event using snipers and drones, FBI says
            
            </a>
            <a href="/news/articles/crlwxg721eeo" ref="45">
            
            ## Swedish man jailed for four years for coercing wife into sex with 120 men
            
            </a>
            <a href="/news/articles/cn074j04l3eo" ref="46">
            
            ## India temporarily bans Telegram over exam paper leak concerns
            
            </a>
            </section>
            <section>
            
            ---
            
            ## World Cup 2026
            
            <a href="/news/articles/cy73xe2006po" ref="47">
            
            ![Iain Bagwell, right, is a man in his 50s. He takes a selfie with his teenage son, pictured left. Both smile for the camera.](https://ichef.bbci.co.uk/news/480/cpsprodpb/e3a9/live/d284ded0-68f7-11f1-b777-eb5f33120f12.jpg.webp)
            
            ## 'Daylight robbery but worth it' - what fans are spending on World Cup
            
            Fans in the US, Canada and Mexico are spending big on tickets, transport and accommodation - is it worth it?
            
            </a>
            <a href="/sport/football/articles/c24y18g9v03o" ref="48">
            
            ![Scoreboard of Belgium and Egypt's result](https://ichef.bbci.co.uk/news/480/cpsprodpb/4f91/live/61e48460-698c-11f1-bf78-b9ee73ed2967.jpg.webp)
            
            ## Record draws and Europe's slow start - is the World Cup lacking jeopardy?
            
            Seven of the 10 European teams to have played at the World Cup so far have failed to win - is heat the issue?
            
            </a>
            <a href="/sport/football/articles/ckg42nvgx77o" ref="49">
            
            ![Argentina's Lionel Messi](https://ichef.bbci.co.uk/news/480/cpsprodpb/46f9/live/b1f7db30-6281-11f1-86be-0da436fa9788.jpg.webp)
            
            ## From dazzling winger to veteran who barely runs - the evolution of Messi
            
            Lionel Messi is set for his sixth World Cup with Argentina. Guillem Balague looks at how the Argentine has evolved.
            
            </a>
            <a href="/sport/football/articles/ckg48wvv0v2o" ref="50">
            
            ![Iran head coach Amir Ghalenoei, wearing a black jumper, frowns after his side's World Cup draw with New Zealand](https://ichef.bbci.co.uk/news/480/cpsprodpb/2255/live/1b608800-694f-11f1-82ab-bf70ea527fb6.jpg.webp)
            
            ## Iran 'most oppressed' team at World Cup, head coach says
            
            Iran head coach Amir Ghalenoei says his team were told to leave LA "immediately" after their match against New Zealand.
            
            </a>
            </section>
            <section>
            
            ---
            
            ## More to explore
            
            <section>
            
            [![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![A man looks at the remains of a building destroyed by an Israeli strike in Kfar Sir, in Nabatieh district, southern Lebanon (16 June 2026)](https://ichef.bbci.co.uk/news/480/cpsprodpb/16f0/live/d36dc9e0-6994-11f1-b1db-af71d47507d6.jpg.webp)](ref:51:/news/articles/cvgepl5gdp1o)
            
            <a href="/news/articles/cvgepl5gdp1o" ref="52">
            
            ## Fragile quiet in Lebanon as US-Iran truce leaves unanswered questions
            
            Many Lebanese remain doubtful that the agreement could finally mean the end of the fighting between Israel and Hezbollah.
            
            4 hrs ago Middle East
            
            </a>
            <a href="/news/articles/cp3xyvww1lqo" ref="53">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![A health workers wearing green scrubs smiles and dances as she waves hr left hand in the air.](https://ichef.bbci.co.uk/news/480/cpsprodpb/23f6/live/0200a2a0-68c8-11f1-8e1d-bbbb1017d210.jpg.webp)
            
            ## Recovery of Ebola patients offers rare moments of joy at epicentre of outbreak
            
            There are glimpses of happiness in the Democratic Republic of Congo's fight against the virus that has killed more than 170.
            
            21 hrs ago Africa
            
            </a>
            <a href="/news/videos/clyr5v7k00zo" ref="54">
            
            ## How Prince George will follow in his father's footsteps at Eton College
            
            The prince is set to attend the elite boarding school from September, Kensington Palace has announced.
            
            3 hrs ago Berkshire
            
            </a>
            <a href="/news/articles/c9824zvpz9po" ref="55">
            
            ## Five big questions about the UK's under-16s social media ban
            
            A ban is coming - but it's still not clear what it will mean for sites including Roblox, YouTube and WhatsApp.
            
            5 hrs ago Technology
            
            </a>
            <a href="/news/articles/c6214prydklo" ref="56">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![FILE PHOTO: The Air India Boeing 787 Dreamliner plane that crashed in Ahmedabad on June 12, 2025, flies over Melbourne, Australia, on December 29, 2024, in this handout picture. RYAN ZHANG/via REUTERS/File Photo](https://ichef.bbci.co.uk/news/480/cpsprodpb/3a0a/live/fbecc040-4b4c-11f0-8bdb-73c0815c1d31.jpg.webp)
            
            ## A year on, six questions still haunt the Air India crash investigation
            
            From fuel switches to engine failures, here are the biggest mysteries around the Air India crash inquiry.
            
            21 hrs ago Asia
            
            </a>
            <a href="/news/articles/c5yzdr4ygdno" ref="57">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![On the right is a treated image of some swings which are empty on a red background and on the left is a black and white image of a toddler](https://ichef.bbci.co.uk/news/480/cpsprodpb/d66b/live/93a2db20-65ac-11f1-8546-8f19e4fe30f4.jpg.webp)
            
            ## What one country's experiment says about attempts to boost birth rates
            
            Why did Hungary’s pronatalist approach deliver an early rise in births only then to fall back? And what lessons does it offer to other countries desperate to lift fertility?
            
            20 hrs ago BBC InDepth
            
            </a>
            <a href="/news/articles/c87q7g48y4po" ref="58">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![Ruth has dark hair and is looking directly at the camera.](https://ichef.bbci.co.uk/news/480/cpsprodpb/91c8/live/6e77b680-6977-11f1-8e1d-bbbb1017d210.jpg.webp)
            
            ## Woman left traumatised by swinging says website 'facilitated abuse'
            
            She wants to warn others after her husband pressured her into sex she did not want, she says.
            
            15 hrs ago Wales
            
            </a>
            </section>
            </section>
            <section>
            
            ---
            
            ## Most watched
            
            <a href="/news/videos/clyr5v7k00zo" ref="59">
            
            1
            
            ## How Prince George will follow in his father's footsteps at Eton College
            
            </a>
            <a href="/news/videos/ced4y9ljye9o" ref="60">
            
            2
            
            ## Drones create the first-ever Fifa scoreboard in Seattle sky
            
            </a>
            <a href="/news/videos/c1dygrpd19go" ref="61">
            
            3
            
            ## Royal Family joined by thousands at Trooping the Colour
            
            </a>
            <a href="/news/videos/cn94vqzev5lo" ref="62">
            
            4
            
            ## What did Trump do differently to Obama on Iran?
            
            </a>
            <a href="/news/videos/cn8q909jld2o" ref="63">
            
            5
            
            ## Watch: California wildfires rage near passing vehicles
            
            </a>
            </section>
            <section>
            
            ---
            
            ## Also in news
            
            <section>
            
            [![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![Abdirahman Mohamed Abdullahi and Benjamin Netanyahu shake hands. In the backdrop are the flags of Somaliland and Israel.](https://ichef.bbci.co.uk/news/480/cpsprodpb/1f4f/live/5af064b0-6975-11f1-a610-610c6ec6d3a5.jpg.webp)](ref:64:/news/articles/cj4gn4d54y0o)
            
            <a href="/news/articles/cj4gn4d54y0o" ref="65">
            
            ## Somaliland opens Jerusalem embassy after Israel's recognition of its independence
            
            The move comes six months after Israel became the first country to recognise the breakaway East African state.
            
            6 hrs ago World
            
            </a>
            <a href="/news/articles/c0jyzpv52yyo" ref="66">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![A spider web of power lines can be seen in front of a billboard calling for the releas of ousted Venezuelan President Nicolas Maduro and his wife, Cilia Flores, inthe Petare neighborhood, Caracas. ](https://ichef.bbci.co.uk/news/480/cpsprodpb/206d/live/4711bd90-6993-11f1-bf78-b9ee73ed2967.jpg.webp)
            
            ## Venezuela signs deal with US energy giant to rebuild power grid
            
            The deal with General Electric is the latest sign of co-operation between US firms and Venezuela's interim government.
            
            5 hrs ago World
            
            </a>
            <a href="/sport/tennis/articles/clyerm8lym9o" ref="67">
            
            ## Williams sisters receive Wimbledon doubles wildcard
            
            Serena Williams is given a wildcard to play alongside older sister Venus in the Wimbledon women's doubles.
            
            10 hrs ago Tennis
            
            </a>
            <a href="/news/articles/c7vyzgl2142o" ref="68">
            
            ## 'Dancing girl's' bare torso restored in Indian textbook after backlash
            
            A picture in a new school textbook had covered up the naked torso of the famous figurine with dark shading.
            
            13 hrs ago Asia
            
            </a>
            <a href="/news/articles/ckg8zrm20jjo" ref="69">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![An old car drives past debris from a demolished house occupying part of the seaside promenade in the Centro Habana neighbourhood in Havana on 9 June , 2026](https://ichef.bbci.co.uk/news/480/cpsprodpb/5724/live/1d7d1180-6972-11f1-8cc2-edef34f05c3f.jpg.webp)
            
            ## Cuba tourism collapses as US pressure campaign bites
            
            The number of foreign visitors is down by 58% compared to last year, Cuban officials say, amid sanctions and an effective oil blockade.
            
            9 hrs ago Latin America
            
            </a>
            <a href="/news/videos/cn8q909jld2o" ref="70">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![Aerial night time image of vehicles driving on a road close to orange wildfire flames.](https://ichef.bbci.co.uk/news/480/cpsprodpb/07d9/live/e691eab0-6962-11f1-9f4a-21531e88e991.jpg.webp)
            
            ## Watch: California wildfires rage near passing vehicles
            
            The fires in Riverside County cover over 2,000 acres, say authorities.
            
            11 hrs ago US & Canada
            
            </a>
            <a href="/news/articles/clyx4jd9kkdo" ref="71">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![Prince George of Wales during Trooping The Colour on June 13, 2026 in London, England.](https://ichef.bbci.co.uk/news/480/cpsprodpb/f648/live/78800c50-6994-11f1-a716-b5396926119a.jpg.webp)
            
            ## Prince George to attend Eton College from September
            
            He will follow in the footsteps of his father, the Prince of Wales, who also attended the private school.
            
            3 hrs ago UK
            
            </a>
            </section>
            </section>
            <section>
            
            ---
            
            ## Most read
            
            <a href="/news/articles/c20yzm84r7lo" ref="72">
            
            1
            
            ## Russian warship fires warning shots near UK-registered yacht in Channel
            
            </a>
            <a href="/news/articles/cqx10xlje1lo" ref="73">
            
            2
            
            ## Group planned to attack White House UFC event using snipers and drones, FBI says
            
            </a>
            <a href="/news/articles/clyrzd5g6k2o" ref="74">
            
            3
            
            ## Russian artist and Putin critic shot dead in Poland
            
            </a>
            <a href="/news/articles/c0jyzp9z9deo" ref="75">
            
            4
            
            ## German broadcaster removes TV intro after Elon Musk takes legal action
            
            </a>
            <a href="/news/articles/cy73xe2006po" ref="76">
            
            5
            
            ## 'Daylight robbery but worth it' - what fans are spending on World Cup
            
            </a>
            <a href="/news/articles/c87q7g48y4po" ref="77">
            
            6
            
            ## Woman left traumatised by swinging says website 'facilitated abuse'
            
            </a>
            <a href="/news/articles/cqx10gg2r2vo" ref="78">
            
            7
            
            ## Dozens walk out as Google boss Pichai addresses Stanford graduates
            
            </a>
            <a href="/news/articles/cn4rw784nj2o" ref="79">
            
            8
            
            ## Three reasons ships are not going through the Strait of Hormuz yet
            
            </a>
            <a href="/news/articles/cvgd5g7d7gyo" ref="80">
            
            9
            
            ## Musk's SpaceX overtakes Amazon to become world's fifth most valuable firm
            
            </a>
            <a href="/news/articles/cn4rwzxvl8ko" ref="81">
            
            10
            
            ## Tehran selling deal with US as victory – but for Iranians it was necessity
            
            </a>
            </section>
            <section>
            
            ---
            
            ## Sport
            
            <section>
            
            [![Nicolas Jackson hits the post](https://ichef.bbci.co.uk/ace/standard/480/cpsprodpb/8381/live/59947b60-69bb-11f1-8e1d-bbbb1017d210.jpg.webp)](ref:82:https://www.bbc.com/sport/football/live/cze9nr5eg2xt)
            
            <a href="https://www.bbc.com/sport/football/live/cze9nr5eg2xt" ref="83">
            
            LIVE
            
            ## Watch as Senegal miss chances to take lead against France
            
            Watch France versus Senegal in World Cup Group I live on BBC One, listen to BBC Radio 5 Live commentary and follow live text coverage.
            
            </a>
            <a href="/sport/football/articles/cy4ev318qd2o" ref="84">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![England head coach Thomas Tuchel speaks to Trent Alexander-Arnold on the touchline](https://ichef.bbci.co.uk/news/480/cpsprodpb/5835/live/c227dec0-69a1-11f1-938e-83ab58a2163c.jpg.webp)
            
            ## Tuchel's defensive gambles and what do they say about Alexander-Arnold?
            
            England head coach Thomas Tuchel's high-risk defensive selection for World Cup exposed by Tino Livramento's injury as Trent Alexander-Arnold is snubbed again, says Phil McNulty.
            
            2 hrs ago England Men
            
            </a>
            <a href="/sport/tennis/articles/clyerm8lym9o" ref="85">
            
            ## Williams sisters to play together at Wimbledon
            
            Serena Williams is given a wildcard to play alongside older sister Venus in the Wimbledon women's doubles.
            
            10 hrs ago Tennis
            
            </a>
            <a href="/sport/football/articles/c99lzern58eo" ref="86">
            
            ## Meet the Iraq player set to make history for Pakistan
            
            Zidane Iqbal, a former Manchester United player, will make history when he plays for Iraq this summer, becoming the first player of Pakistani heritage to feature at a men's World Cup.
            
            13 hrs ago World Cup
            
            </a>
            <a href="https://www.bbc.com/sport/cricket/live/cy510923w0pt" ref="87">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![England's Amy Jones and Danni Wyatt-Hodge run between the wickets](https://ichef.bbci.co.uk/ace/standard/480/cpsprodpb/5b8f/live/9d5ca300-69bf-11f1-bd62-216646a5e7ca.jpg.webp) LIVE
            
            ## Women's T20 World Cup: England chasing 119 to beat Ireland
            
            Follow live text, BBC Radio 5 Live Sport commentary and in-play video highlights as England face Ireland in the Women's T20 World Cup in Southampton.
            
            </a>
            <a href="/sport/cricket/articles/c3ry8lyd7j0o" ref="88">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![England captain Ben Stokes looks on at Lord's during the first Test against New Zealand](https://ichef.bbci.co.uk/news/480/cpsprodpb/9368/live/c9996ed0-6997-11f1-9f1e-3f3de09371ef.jpg.webp)
            
            ## Spectre of Stokes hangs over England at The Oval
            
            England have handed out four debuts in two Tests this summer but Ben Stokes' role in the latest revolution remains a mystery and will hang over England at The Oval.
            
            3 hrs ago England
            
            </a>
            <a href="/sport/articles/cpwez2pg8x1o" ref="89">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![Jan Paul van Hecke](https://ichef.bbci.co.uk/news/480/cpsprodpb/7a4d/live/882c1cc0-69b7-11f1-9a2b-c1fb07dd1b47.jpg.webp)
            
            ## Spurs agree £52m Van Hecke deal with Brighton
            
            Netherlands World Cup defender Jan Paul van Hecke is set to leave Brighton for Tottenham Hotspur after the two clubs agree a £52m deal.
            
            45 mins ago Premier League
            
            </a>
            </section>
            </section>
            <section>
            <section>
            
            [![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![A collage of images and illustrations](https://ichef.bbci.co.uk/ace/standard/480/cpsprodpb/4234/live/ccb11760-566c-11f0-9074-8989d8c97d87.png.webp)](ref:90:https://cloud.email.bbc.com/bbcnewsignup2?&at_bbc_team=studios&at_medium=display&at_objective=acquisition&at_ptr_type=&at_ptr_name=bbc.comhp&at_format=Module&at_link_origin=intlfront&at_campaign=newsbriefing&at_campaign_type=owned)
            
            <a href="https://cloud.email.bbc.com/bbcnewsignup2?&amp;at_bbc_team=studios&amp;at_medium=display&amp;at_objective=acquisition&amp;at_ptr_type=&amp;at_ptr_name=bbc.comhp&amp;at_format=Module&amp;at_link_origin=intlfront&amp;at_campaign=newsbriefing&amp;at_campaign_type=owned" ref="91">
            
            ## Sign up to News Briefing
            
            News and expert analysis for every schedule. Get morning and evening editions of our flagship newsletter in your inbox. See more
            
            </a>
            </section>
            </section>
            </article>
            </main>
            
            ---
            
            <footer id="bbc-footer">
            
            [![British Broadcasting Corporation]()](ref:92:/)
            
            <section>
            <nav aria-label="Footer navigation">
            
            - [Home](ref:93:https://www.bbc.com/)
            - [News](ref:94:/news)
            - [Football 2026](ref:95:/sport/football/world-cup)
            - [Business](ref:96:/business)
            - [Technology](ref:97:/technology)
            - [Health](ref:98:/health)
            - [Culture](ref:99:/culture)
            - [Arts](ref:100:/arts)
            - [Travel](ref:101:/travel)
            - [Earth](ref:102:/future-planet)
            - [Sport](ref:103:/sport)
            - [Audio](ref:104:/audio)
            - [Video](ref:105:/video)
            - [Live](ref:106:/live)
            - [Weather](ref:107:https://www.bbc.com/weather)
            - [BBC Shop](ref:108:https://shop.bbc.com/)
            - [BritBox](ref:109:https://www.britbox.com/?utm_source=bbc.com&utm_medium=referral&utm_campaign=footer)
            
            </nav>
            </section>
            <section>
            <button type="button" ref="110">
            
            BBC in other languages
            
            </button>
            </section>
            
            Follow BBC on:
            
            <button aria-label="Follow BBC on x" ref="111">
            </button>
            <button aria-label="Follow BBC on facebook" ref="112">
            </button>
            <button aria-label="Follow BBC on instagram" ref="113">
            </button>
            <button aria-label="Follow BBC on tiktok" ref="114">
            </button>
            <button aria-label="Follow BBC on linkedin" ref="115">
            </button>
            <button aria-label="Follow BBC on youtube" ref="116">
            </button>
            <section>
            <nav>
            
            - [Terms of Use](ref:117:https://www.bbc.com/pages/terms-of-use)
            - [Subscription Terms](ref:118:https://www.bbc.com/pages/subscription-terms)
            - [About the BBC](ref:119:https://www.bbc.co.uk/aboutthebbc)
            - [Privacy Policy](ref:120:https://www.bbc.com/pages/privacy-policy)
            - [Cookies](ref:121:https://www.bbc.com/usingthebbc/cookies/)
            - [Accessibility Help](ref:122:https://www.bbc.co.uk/accessibility/)
            - [Contact the BBC](ref:123:https://www.bbc.co.uk/contact)
            - [Advertise with us](ref:124:https://advertising.bbcstudios.com/)
            - [Do not share or sell my info](ref:125:https://www.bbc.com/usingthebbc/cookies/how-can-i-change-my-bbc-cookie-settings/)
            - [BBC.com Help & FAQs](ref:126:https://help.bbc.com/hc/)
            - [Content Index](ref:127:https://www.bbc.com/pages/content-index)
            - [Set Preferred Source](ref:128:https://www.bbc.com/future/article/20260128-how-to-make-google-put-trusted-sources-up-top-when-you-search)
            
            </nav>
            </section>
            
            Copyright 2026 BBC. All rights reserved. The BBC is not responsible for the content of external sites. [:DACi:](ref:129:https://www.bbc.com/editorialguidelines/guidance/links-and-feeds)
            
             
            
            </footer>
            <section>
            
            ## Let us know you agree to cookies
            
            We use [cookies](ref:130:https://www.bbc.com/usingthebbc/cookies/what-do-i-need-to-know-about-cookies/) to give you the best online experience.
            
            Please let us know if you agree to all of these cookies.
            
            
            - <button id="bbccookies-continue-button" type="button" ref="131">
            
              Yes, I agree
            
              </button>
            
            - [No, take me to settings](ref:132:https://www.bbc.com/usingthebbc/cookies/how-can-i-change-my-bbc-cookie-settings/)
            
            </section>
        """.trimIndent()
    }

}
