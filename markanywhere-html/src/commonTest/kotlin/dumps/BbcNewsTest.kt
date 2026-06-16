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
        markdown sameAs /* language=markdown */ $$"""
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
            </nav>
            <main id="bbc-main">
            <article>
            
            # ![News]()
            
            <section>
            <section>
            
            [![US president Donald Trump at the g7 summit](https://ichef.bbci.co.uk/ace/standard/480/cpsprodpb/d50d/live/07d4db80-6970-11f1-8546-8f19e4fe30f4.jpg.webp)](ref:8:https://www.bbc.com/news/live/c98247ml02qt)
            
            <a href="https://www.bbc.com/news/live/c98247ml02qt" ref="9">
            
            LIVE
            
            ## Trump says Iran will 'never have nuclear weapon' under deal and criticises Israel over Lebanon
            
            Full details of the agreement are still to be released, as Keir Starmer says the UK would play its "full part" in getting the Strait of Hormuz open.
            
            </a>
            <a href="/news/articles/clyrzd5g6k2o" ref="10">
            
            ![A man wearing an old-style Russian hat with a Soviet badge holds the top of a framed picture and wears a jacket crammed with medals](https://ichef.bbci.co.uk/news/480/cpsprodpb/802d/live/a26f4a30-6962-11f1-9f4a-21531e88e991.jpg.webp)
            
            ## Russian artist and Putin critic shot dead in Poland
            
            Robert Kuzovkov, who used the pseudonym Semyon Skrepetsky, has been known for his caricatures of politicians including Vladimir Putin.
            
            3 hrs ago Europe
            
            </a>
            <a href="https://www.bbc.com/sport/football/live/c3wy8j9j21yt" ref="11">
            
            LIVE
            
            ## World Cup: Chalobah replaces Livramento in England squad
            
            All the latest news and updates as Scotland hold news conference before Tuesday's matches, including France, Norway and Argentina.
            
            </a>
            <a href="/news/articles/cy73xe2006po" ref="12">
            
            ## 'Daylight robbery but worth it' - what fans are spending on World Cup
            
            Fans in the US, Canada and Mexico are spending big on tickets, transport and accommodation - is it worth it?
            
            4 hrs ago World
            
            </a>
            <a href="/news/articles/cqx10yql319o" ref="13">
            
            ![File photo of a US B-52 Stratofortress bomber seen in the skies above the UK in March](https://ichef.bbci.co.uk/news/480/cpsprodpb/3e43/live/732b1820-6945-11f1-8e1d-bbbb1017d210.jpg.webp)
            
            ## Eight dead after US Air Force B-52 bomber crashes in California
            
            The incident occurred on Monday morning while the aircraft had been on a routine test mission.
            
            12 hrs ago US & Canada
            
            </a>
            <a href="/news/articles/cp3xyvww1lqo" ref="14">
            
            ![A health workers wearing green scrubs smiles and dances as she waves hr left hand in the air.](https://ichef.bbci.co.uk/news/480/cpsprodpb/23f6/live/0200a2a0-68c8-11f1-8e1d-bbbb1017d210.jpg.webp)
            
            ## Recovery of Ebola patients offers rare moments of joy at epicentre of outbreak
            
            There are glimpses of happiness in the Democratic Republic of Congo's fight against the virus that has killed more than 170.
            
            15 hrs ago Africa
            
            </a>
            <a href="/news/articles/cvgd5g7d7gyo" ref="15">
            
            ![Elon Musk wearing a black T-shirt and holding his phone above his head.](https://ichef.bbci.co.uk/news/480/cpsprodpb/4d41/live/929b64a0-697a-11f1-8546-8f19e4fe30f4.jpg.webp)
            
            ## Musk's SpaceX buys AI coding start-up for $60bn days after IPO
            
            Elon Musk's rocket company has agreed to buy Cursor, which uses AI to automate the process of writing code.
            
            2 hrs ago
            
            </a>
            <a href="/news/articles/c621zplqrrno" ref="16">
            
            ![Hungary's prime minister Péter Magyar speaking in parliament in Budapest](https://ichef.bbci.co.uk/news/480/cpsprodpb/b138/live/13ff5dc0-697c-11f1-a63b-a7cb529e1ca8.jpg.webp)
            
            ## Hungary's MPs block return of Orbán, limiting rule of PM to eight years
            
            The vote fulfils a promise by new PM Péter Magyar to stop his predecessor from becoming prime minister again.
            
            1 hr ago Europe
            
            </a>
            </section>
            </section>
            <section>
            <a href="/news/articles/cn074j04l3eo" ref="17">
            
            ## India temporarily bans Telegram over exam paper leak concerns
            
            </a>
            <a href="/news/articles/crlwxg721eeo" ref="18">
            
            ## Swedish man jailed for four years for coercing wife into sex with 120 men
            
            </a>
            <a href="/news/articles/cqx10xlje1lo" ref="19">
            
            ## FBI thwarted plot targeting White House UFC event, Patel says
            
            </a>
            <a href="/news/articles/ckg8zrm20jjo" ref="20">
            
            ## Cuba tourism collapses as US pressure campaign bites
            
            </a>
            <a href="/sport/tennis/articles/clyerm8lym9o" ref="21">
            
            ## Williams sisters receive Wimbledon doubles wildcard
            
            </a>
            </section>
            <section>
            
            ---
            
            ## World Cup 2026
            
            <a href="/sport/football/articles/ckg42nvgx77o" ref="22">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![Argentina's Lionel Messi](https://ichef.bbci.co.uk/news/480/cpsprodpb/46f9/live/b1f7db30-6281-11f1-86be-0da436fa9788.jpg.webp)
            
            ## From dazzling winger to veteran who barely runs - the evolution of Messi
            
            Lionel Messi is set for his sixth World Cup with Argentina. Guillem Balague looks at how the Argentine has evolved.
            
            </a>
            <a href="/sport/football/articles/ckg48wvv0v2o" ref="23">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![Amir Ghalenoei, wearing a black jumper, frowns ](https://ichef.bbci.co.uk/news/480/cpsprodpb/2255/live/1b608800-694f-11f1-82ab-bf70ea527fb6.jpg.webp)
            
            ## Iran 'most oppressed' team at World Cup, head coach says
            
            Iran head coach Amir Ghalenoei says his team were told to leave LA "immediately" after their match against New Zealand.
            
            </a>
            <a href="/sport/football/articles/cp8rldpky7wo" ref="24">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![Close up of Sabri Lamouchi standing in the dugout looking focused](https://ichef.bbci.co.uk/news/480/cpsprodpb/1e22/live/ff8e8a90-695e-11f1-8546-8f19e4fe30f4.jpg.webp)
            
            ## Tunisia sack head coach just one game into tournament
            
            Tunisia sack Sabri Lamouchi one game into their World Cup, with former Morocco and Saudi Arabia manager Herve Renard replacing him.
            
            </a>
            <a href="/news/articles/clyer09rd1ro" ref="25">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![Cape Verdean fans. ](https://ichef.bbci.co.uk/news/480/cpsprodpb/830b/live/fa8d8a20-690c-11f1-9db7-8df355e9939d.jpg.webp)
            
            ## 'Greatest feeling ever': Cape Verdeans tell BBC of joy at holding Spain to draw
            
            The streets of the capital, Praia, shook with wild celebrations as the small island nation held Spain to a shock 0-0 draw.
            
            </a>
            </section>
            <section>
            
            ---
            
            ## More to explore
            
            <section>
            
            [![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![Netanyahu speaking in front of a a blue background and Israeli flag wearing a black suit and red tie](https://ichef.bbci.co.uk/news/480/cpsprodpb/ee5d/live/2f08dc10-6961-11f1-8e1d-bbbb1017d210.jpg.webp)](ref:26:/news/articles/cj4gnqw8j52o)
            
            <a href="/news/articles/cj4gnqw8j52o" ref="27">
            
            ## Iran deal presents political nightmare for Netanyahu
            
            Donald Trump's ceasefire agreement with Iran leaves the Israeli PM trapped in a new political and security dilemma.
            
            10 hrs ago World
            
            </a>
            <a href="/news/videos/c5yzdejw2eko" ref="28">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![A close-up photo of a large cutlet of raw pork. Underneath are various other cuts of red meat. The bright blue and green BBC Verify logo is overlaid in the top left-hand corner. ](https://ichef.bbci.co.uk/news/480/cpsprodpb/9581/live/80046f20-65bb-11f1-8e1d-bbbb1017d210.jpg.webp)
            
            ## No, there’s no plot to make you allergic to meat
            
            Viral conspiracy posts claim there is a secret effort to spread ticks on US farms to trigger an allergy to red meat.
            
            14 hrs ago US & Canada
            
            </a>
            <a href="/news/articles/c87q7g48y4po" ref="29">
            
            ## Woman left traumatised by swinging says website 'facilitated abuse'
            
            She wants to warn others after her husband pressured her into sex she did not want, she says.
            
            9 hrs ago Wales
            
            </a>
            <a href="/news/articles/c9824zvpz9po" ref="30">
            
            ## Five big questions about the UK's under-16s social media ban
            
            A ban is coming - but it's still not clear what it will mean for sites including Roblox, YouTube and WhatsApp.
            
            2 hrs ago Technology
            
            </a>
            <a href="/news/articles/c6214prydklo" ref="31">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![FILE PHOTO: The Air India Boeing 787 Dreamliner plane that crashed in Ahmedabad on June 12, 2025, flies over Melbourne, Australia, on December 29, 2024, in this handout picture. RYAN ZHANG/via REUTERS/File Photo](https://ichef.bbci.co.uk/news/480/cpsprodpb/3a0a/live/fbecc040-4b4c-11f0-8bdb-73c0815c1d31.jpg.webp)
            
            ## A year on, six questions still haunt the Air India crash investigation
            
            From fuel switches to engine failures, here are the biggest mysteries around the Air India crash inquiry.
            
            15 hrs ago Asia
            
            </a>
            <a href="/news/articles/c5yzdr4ygdno" ref="32">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![On the right is a treated image of some swings which are empty on a red background and on the left is a black and white image of a toddler](https://ichef.bbci.co.uk/news/480/cpsprodpb/d66b/live/93a2db20-65ac-11f1-8546-8f19e4fe30f4.jpg.webp)
            
            ## What one country's experiment says about attempts to boost birth rates
            
            Why did Hungary’s pronatalist approach deliver an early rise in births only then to fall back? And what lessons does it offer to other countries desperate to lift fertility?
            
            14 hrs ago BBC InDepth
            
            </a>
            <a href="https://www.bbc.com/weather/articles/c4gy3x0k32yo" ref="33">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![People in the sea, including one person on a paddle board,  silhouetted against a purple and orange sunset ](https://ichef.bbci.co.uk/ace/standard/480/cpsprodpb/eea5/live/bb7b9a20-6961-11f1-b1db-af71d47507d6.jpg.webp)
            
            ## What is the summer solstice and why is it the longest day of the year?
            
            This year the summer solstice takes place on 21 June, marking the start of the season on the astronomical calendar.
            
            7 hrs ago
            
            </a>
            </section>
            </section>
            <section>
            
            ---
            
            ## Most watched
            
            <a href="/news/videos/cn8q909jld2o" ref="34">
            
            1
            
            ## Watch: California wildfires rage near passing vehicles
            
            </a>
            <a href="/news/videos/cm20yglrgjlo" ref="35">
            
            2
            
            ## Iranian-Americans protest against Iran team at World Cup
            
            </a>
            <a href="/news/videos/cn94vqzev5lo" ref="36">
            
            3
            
            ## What did Trump do differently to Obama on Iran?
            
            </a>
            <a href="/news/videos/c5yzdejw2eko" ref="37">
            
            4
            
            ## No, there’s no plot to make you allergic to meat
            
            </a>
            <a href="/news/videos/cp9ly81797ko" ref="38">
            
            5
            
            ## Dozens of Stanford grads walk out on Google CEO's speech
            
            </a>
            </section>
            <section>
            
            ---
            
            ## Also in news
            
            <section>
            
            [![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![Aerial night time image of vehicles driving on a road close to orange wildfire flames.](https://ichef.bbci.co.uk/news/480/cpsprodpb/07d9/live/e691eab0-6962-11f1-9f4a-21531e88e991.jpg.webp)](ref:39:/news/videos/cn8q909jld2o)
            
            <a href="/news/videos/cn8q909jld2o" ref="40">
            
            ## Watch: California wildfires rage near passing vehicles
            
            The fires in Riverside County cover over 2,000 acres, say authorities.
            
            5 hrs ago US & Canada
            
            </a>
            <a href="/news/articles/cjdgl213dpzo" ref="41">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![A customer walking past a row of shelves filled to the brim with instant noodles at a supermarket in Japan.](https://ichef.bbci.co.uk/news/480/cpsprodpb/3a76/live/d7e33c30-6872-11f1-8d7f-231b457c3604.jpg.webp)
            
            ## Japan raises interest rate to highest for 31 years
            
            The Bank of Japan has been raising rates from near-zero since 2024.
            
            6 hrs ago Business
            
            </a>
            <a href="/news/articles/clyx4eny41zo" ref="42">
            
            ## Australia to probe assault claims by Gaza flotilla activists against Israeli forces
            
            Australian activists claim they were kidnapped, raped and tortured after being detained in May.
            
            14 hrs ago World
            
            </a>
            <a href="/news/articles/c4gyp099vl7o" ref="43">
            
            ## Married at First Sight Australia allegations 'disturbing', says country's watchdog
            
            The claims also prompted a response from UK media regulator Ofcom, who called them "deeply concerning".
            
            14 hrs ago Culture
            
            </a>
            <a href="/news/articles/clyx4jd9kkdo" ref="44">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![Prince George of Wales during Trooping The Colour on June 13, 2026 in London, England.](https://ichef.bbci.co.uk/news/480/cpsprodpb/fd82/live/5c7c9e70-697f-11f1-8e1d-bbbb1017d210.jpg.webp)
            
            ## Prince George to attend Eton College from September
            
            He will follow in the footsteps of his father, the Prince of Wales, who also attended the private school.
            
            2 hrs ago UK
            
            </a>
            <a href="/news/articles/c7vyzgl2142o" ref="45">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![The Dancing Girl is a bronze figurine discovered in Mohenjo-daro and dates back to 2600 BCE](https://ichef.bbci.co.uk/news/480/cpsprodpb/4be3/live/3aa49d90-6946-11f1-b8da-53bba3cad46c.jpg.webp)
            
            ## 'Dancing girl's' bare torso restored in Indian textbook after backlash
            
            A picture in a new school textbook had covered up the naked torso of the famous figurine with dark shading.
            
            7 hrs ago Asia
            
            </a>
            <a href="/sport/football/articles/cvgj1v4pnvyo" ref="46">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![Iran fans at the World Cup match against New Zealand at Los Angeles Stadium](https://ichef.bbci.co.uk/news/480/cpsprodpb/300d/live/f4f0e580-6934-11f1-9eac-df8ea7226b66.jpg.webp)
            
            ## Iran v Iran in the stands as politics and football intertwine
            
            The game with New Zealand revealed just how divided many Iranians remain, writes BBC's Shaimaa Khalil.
            
            10 hrs ago World Cup
            
            </a>
            </section>
            </section>
            <section>
            
            ---
            
            ## Most read
            
            <a href="/news/articles/clyrzd5g6k2o" ref="47">
            
            1
            
            ## Russian artist and Putin critic shot dead in Poland
            
            </a>
            <a href="/news/articles/cj4gnqw8j52o" ref="48">
            
            2
            
            ## Iran deal presents political nightmare for Netanyahu
            
            </a>
            <a href="/news/articles/cqx10gg2r2vo" ref="49">
            
            3
            
            ## Dozens walk out as Google boss Pichai addresses Stanford graduates
            
            </a>
            <a href="/news/articles/cvgd5g7d7gyo" ref="50">
            
            4
            
            ## Musk's SpaceX buys AI coding start-up for $60bn days after IPO
            
            </a>
            <a href="/news/articles/c87q7g48y4po" ref="51">
            
            5
            
            ## Woman left traumatised by swinging says website 'facilitated abuse'
            
            </a>
            <a href="/news/articles/cqx10yql319o" ref="52">
            
            6
            
            ## Eight dead after US Air Force B-52 bomber crashes in California
            
            </a>
            <a href="/news/articles/c621zplqrrno" ref="53">
            
            7
            
            ## Hungary's MPs block return of Orbán, limiting rule of PM to eight years
            
            </a>
            <a href="/news/articles/crlwxg721eeo" ref="54">
            
            8
            
            ## Swedish man jailed for four years for coercing wife into sex with 120 men
            
            </a>
            <a href="/news/articles/clyx4jd9kkdo" ref="55">
            
            9
            
            ## Prince George to attend Eton College from September
            
            </a>
            <a href="/news/articles/c5yzdr4ygdno" ref="56">
            
            10
            
            ## What one country's experiment says about attempts to boost birth rates
            
            </a>
            </section>
            <section>
            
            ---
            
            ## Sport
            
            <section>
            
            [![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![Livramento](https://ichef.bbci.co.uk/news/480/cpsprodpb/597a/live/bbf92c90-6977-11f1-a610-610c6ec6d3a5.jpg.webp)](ref:57:/sport/football/articles/c992n95lgrmo)
            
            <a href="/sport/football/articles/c992n95lgrmo" ref="58">
            
            ## Chalobah replaces injured Livramento for World Cup
            
            Newcastle United full-back Tino Livramento is out of England's World Cup campaign with a calf injury and is replaced by Chelsea defender Trevoh Chalobah.
            
            29 mins ago England Men
            
            </a>
            <a href="/sport/cricket/articles/cm20en7rmx7o" ref="59">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![Joe Root and Brendon McCullum during an England training session](https://ichef.bbci.co.uk/news/480/cpsprodpb/e7a7/live/4e1df5c0-6981-11f1-8e1d-bbbb1017d210.jpg.webp)
            
            ## Root to captain game-by-game but is 'envious' of Stokes-McCullum dynamic
            
            Joe Root says he is taking the England captaincy on a "game-by-game basis" but admits he has been "slightly envious" of Ben Stokes for getting to work alongside Brendon McCullum.
            
            49 mins ago England
            
            </a>
            <a href="/sport/football/articles/c99lzern58eo" ref="60">
            
            ## Meet the Iraq player set to make history for Pakistan
            
            Zidane Iqbal, a former Manchester United player, will make history when he plays for Iraq this summer, becoming the first player of Pakistani heritage to feature at a men's World Cup.
            
            7 hrs ago World Cup
            
            </a>
            <a href="/sport/football/articles/cp8rldpky7wo" ref="61">
            
            ## Tunisia sack Lamouchi just one game into World Cup
            
            Tunisia sack Sabri Lamouchi one game into their World Cup, with former Morocco and Saudi Arabia manager Herve Renard replacing him.
            
            7 hrs ago World Cup
            
            </a>
            <a href="/sport/football/articles/clye3plyj7xo" ref="62">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![Thomas Tuchel during England's training session in Kansas](https://ichef.bbci.co.uk/news/480/cpsprodpb/7d71/live/7fb29e60-6918-11f1-bae7-cb8795d75d55.jpg.webp)
            
            ## Tuchel's England deal includes performance clause
            
            Thomas Tuchel's England contract extension is subject to a performance clause, says FA chief executive Mark Bullingham.
            
            3 hrs ago England Men
            
            </a>
            <a href="/sport/tennis/articles/clyerm8lym9o" ref="63">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![Serena and Venus Williams with the Wimbledon doubles trophies in 2016](https://ichef.bbci.co.uk/news/480/cpsprodpb/6b62/live/1a33e480-6973-11f1-8e1d-bbbb1017d210.jpg.webp)
            
            ## Williams sisters to play together at Wimbledon
            
            Serena Williams is given a wildcard to play alongside older sister Venus in the Wimbledon women's doubles.
            
            4 hrs ago Tennis
            
            </a>
            <a href="https://www.bbc.com/sport/football/live/c3wy8j9j21yt" ref="64">
            
            ![Chalobah](https://ichef.bbci.co.uk/ace/standard/480/cpsprodpb/4cb8/live/19c1d1b0-6987-11f1-ac49-cbb0d85207cb.jpg.webp)LIVE
            
            ## World Cup: Chalobah replaces Livramento in England squad
            
            All the latest news and updates as Scotland hold news conference before Tuesday's matches, including France, Norway and Argentina.
            
            </a>
            </section>
            </section>
            <section>
            <section>
            
            [![](https://static.files.bbci.co.uk/bbcdotcom/web/20260609-143108-fe43574f32-web-3.9.0-5/grey-placeholder.png)![A collage of images and illustrations](https://ichef.bbci.co.uk/ace/standard/480/cpsprodpb/4234/live/ccb11760-566c-11f0-9074-8989d8c97d87.png.webp)](ref:65:https://cloud.email.bbc.com/bbcnewsignup2?&at_bbc_team=studios&at_medium=display&at_objective=acquisition&at_ptr_type=&at_ptr_name=bbc.comhp&at_format=Module&at_link_origin=intlfront&at_campaign=newsbriefing&at_campaign_type=owned)
            
            <a href="https://cloud.email.bbc.com/bbcnewsignup2?&amp;at_bbc_team=studios&amp;at_medium=display&amp;at_objective=acquisition&amp;at_ptr_type=&amp;at_ptr_name=bbc.comhp&amp;at_format=Module&amp;at_link_origin=intlfront&amp;at_campaign=newsbriefing&amp;at_campaign_type=owned" ref="66">
            
            ## Sign up to News Briefing
            
            News and expert analysis for every schedule. Get morning and evening editions of our flagship newsletter in your inbox. See more
            
            </a>
            </section>
            </section>
            </article>
            </main>
            
            ---
            
            <footer id="bbc-footer">
            
            [![British Broadcasting Corporation]()](ref:67:/)
            
            <section>
            <nav aria-label="Footer navigation">
            
            - [Home](ref:68:https://www.bbc.com/)
            - [News](ref:69:/news)
            - [Football 2026](ref:70:/sport/football/world-cup)
            - [Business](ref:71:/business)
            - [Technology](ref:72:/technology)
            - [Health](ref:73:/health)
            - [Culture](ref:74:/culture)
            - [Arts](ref:75:/arts)
            - [Travel](ref:76:/travel)
            - [Earth](ref:77:/future-planet)
            - [Sport](ref:78:/sport)
            - [Audio](ref:79:/audio)
            - [Video](ref:80:/video)
            - [Live](ref:81:/live)
            - [Weather](ref:82:https://www.bbc.com/weather)
            - [BBC Shop](ref:83:https://shop.bbc.com/)
            - [BritBox](ref:84:https://www.britbox.com/?utm_source=bbc.com&utm_medium=referral&utm_campaign=footer)
            
            </nav>
            </section>
            <section>
            <button type="button" ref="85">
            
            BBC in other languages
            
            </button>
            </section>
            
            Follow BBC on:
            
            <button aria-label="Follow BBC on x" ref="86">
            </button>
            <button aria-label="Follow BBC on facebook" ref="87">
            </button>
            <button aria-label="Follow BBC on instagram" ref="88">
            </button>
            <button aria-label="Follow BBC on tiktok" ref="89">
            </button>
            <button aria-label="Follow BBC on linkedin" ref="90">
            </button>
            <button aria-label="Follow BBC on youtube" ref="91">
            </button>
            <section>
            <nav>
            
            - [Terms of Use](ref:92:https://www.bbc.com/pages/terms-of-use)
            - [Subscription Terms](ref:93:https://www.bbc.com/pages/subscription-terms)
            - [About the BBC](ref:94:https://www.bbc.co.uk/aboutthebbc)
            - [Privacy Policy](ref:95:https://www.bbc.com/pages/privacy-policy)
            - [Cookies](ref:96:https://www.bbc.com/usingthebbc/cookies/)
            - [Accessibility Help](ref:97:https://www.bbc.co.uk/accessibility/)
            - [Contact the BBC](ref:98:https://www.bbc.co.uk/contact)
            - [Advertise with us](ref:99:https://advertising.bbcstudios.com/)
            - [Do not share or sell my info](ref:100:https://www.bbc.com/usingthebbc/cookies/how-can-i-change-my-bbc-cookie-settings/)
            - [BBC.com Help & FAQs](ref:101:https://help.bbc.com/hc/)
            - [Content Index](ref:102:https://www.bbc.com/pages/content-index)
            - [Set Preferred Source](ref:103:https://www.bbc.com/future/article/20260128-how-to-make-google-put-trusted-sources-up-top-when-you-search)
            
            </nav>
            </section>
            
            Copyright 2026 BBC. All rights reserved. The BBC is not responsible for the content of external sites. [:DACi:](ref:104:https://www.bbc.com/editorialguidelines/guidance/links-and-feeds)
            
             
            
            </footer>
            <section>
            
            ## Let us know you agree to cookies
            
            We use [cookies](ref:105:https://www.bbc.com/usingthebbc/cookies/what-do-i-need-to-know-about-cookies/) to give you the best online experience.
            
            Please let us know if you agree to all of these cookies.
            
            
            - <button id="bbccookies-continue-button" type="button" ref="106">
            
              Yes, I agree
            
              </button>
            
            - [No, take me to settings](ref:107:https://www.bbc.com/usingthebbc/cookies/how-can-i-change-my-bbc-cookie-settings/)
            
            </section>
        """.trimIndent()
    }

}
