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
import com.xemantic.kotlin.test.sameAsMarkdown
import com.xemantic.markanywhere.html.DumpFixtures
import com.xemantic.markanywhere.html.dumpFlow
import com.xemantic.markanywhere.html.transformHtmlToMarkdown
import com.xemantic.markanywhere.parse.parse
import com.xemantic.markanywhere.render.renderMarkdown
import kotlinx.coroutines.flow.flowOf
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
        markdown sameAsMarkdown """
            ---
            lang: en-GB
            title: BBC News - Breaking news, video and the latest top stories from the U.S. and around the world
            description: Visit BBC News for the latest news, breaking news, video, audio and analysis. BBC News provides trusted World, U.S. and U.K. news as well as local and regional perspectives. Also entertainment, climate, business, science, technology and health news.
            pagetype: Section
            page.section: News
            "twitter:title": BBC News - Breaking news, video and the latest top stories from the U.S. and around the world
            "twitter:description": Visit BBC News for the latest news, breaking news, video, audio and analysis. BBC News provides trusted World, U.S. and U.K. news as well as local and regional perspectives. Also entertainment, climate, business, science, technology and health news.
            version: web-3.21.0
            "cXenseParse:pageclass": frontpage
            ---
            
            [Skip to content](ref:1:#bbc-main)
            
            <header id="bbc-header">
            <button aria-label="Open menu" aria-expanded="false" ref="2">
            
            Open menu
            
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
            - [Sport](ref:10:/sport)
            - [Business](ref:11:/business)
            - [Technology](ref:12:/technology)
            - [Health](ref:13:/health)
            - [Culture](ref:14:/culture)
            - [Arts](ref:15:/arts)
            - [Travel](ref:16:/travel)
            - [Earth](ref:17:/future-planet)
            - [Audio](ref:18:/audio)
            - [Video](ref:19:/video)
            - [Live](ref:20:/live)
            
            </nav>
            </section>
            <nav>
            <nav>
            
            - [US & Canada](ref:21:/news/us-canada)
            - [UK](ref:22:/news/uk)
            - [Africa](ref:23:/news/world/africa)
            - [Asia](ref:24:/news/world/asia)
            - [Australia](ref:25:/news/world/australia)
            - [Europe](ref:26:/news/world/europe)
            - [Latin America](ref:27:/news/world/latin_america)
            - [Middle East](ref:28:/news/world/middle_east)
            - [In Pictures](ref:29:/news/in_pictures)
            - [BBC InDepth](ref:30:/news/bbcindepth)
            - [BBC Verify](ref:31:/news/bbcverify)
            
            </nav>
            </nav>
            </nav>
            <main id="bbc-main">
            <article>
            
            # ![News]()
            
            <section>
            <section>
            
            [![German Chancellor Friedrich Merz gives a statement after first exit polls of the Berlin and Mecklenburg Western Pomerania state elections, at the CDU headquarters in Berlin](https://ichef.bbci.co.uk/news/480/cpsprodpb/78b8/live/cb4d6df0-b526-11f1-b1d1-571ed4d7ff2c.jpg.webp)](ref:32:/news/articles/cvwyz29n0nn2o)
            
            <a href="/news/articles/cvwyz29n0nn2o" ref="33">
            
            ## German Chancellor Merz calls state election a 'disaster' for his party but vows to stay on
            
            Exit polls show damaging losses for Merz's centre-right CDU party in the states of Berlin and Mecklenburg-Vorpommern.
            
            1 hr ago Europe
            
            </a>
            <a href="/news/articles/c34gdjk1ne8yo" ref="34">
            
            ![Plumes of smoke rise from fires at an oil refinery in Moscow after an overnight Ukrainian drone attack. Photo: 20 September 2026](https://ichef.bbci.co.uk/news/480/cpsprodpb/15c3/live/41153930-b4bb-11f1-b1d1-571ed4d7ff2c.jpg.webp)
            
            ## Largest attack on Moscow sees Ukraine fire hundreds of drones, mayor says
            
            Moscow's mayor says 450 drones were downed during the overnight barrage, in which two people died.
            
            10 hrs ago Europe
            
            </a>
            <a href="/news/articles/cm36l2pye2kwo" ref="35">
            
            ## Sister of Pakistan's ex-PM Imran Khan arrested
            
            Aleema Khanum is accused of mobilising her brother’s supporters ahead of country-wide protests.
            
            2 hrs ago Asia
            
            </a>
            <a href="/news/articles/cw62m6z7m7zjo" ref="36">
            
            ## Mum's viral barefoot race sparks debate over India's education crisis
            
            The 47-year-old ran - and won - a 3km race where she hoped to win enough prize money to buy study books for her daughter.
            
            21 hrs ago Asia
            
            </a>
            <a href="/news/articles/cwj3d7zrgvk1o" ref="37">
            
            ![Britain's Prince Harry and Meghan, the Duke and Duchess of Sussex, disembark after sailing on Sydney Harbour with veterans from the Invictus Australia community, in Sydney, Australia, April 17, 2026.](https://ichef.bbci.co.uk/news/480/cpsprodpb/6eff/live/165a3eb0-b520-11f1-b1e2-034207760e32.jpg.webp)
            
            ## Harry and Meghan's media treatment echoes what happened to Diana, Earl Spencer tells BBC
            
            Princess Diana's brother describes press coverage of Prince Harry and Meghan as a "cancerous influence in their life" and says he saw his sister cry "tears of despair" over interest in her.
            
            8 hrs ago UK
            
            </a>
            <a href="/news/videos/cw62mdrjll18o" ref="38">
            
            ![Man with white hair wearing blue shirt and navy cardigan](https://ichef.bbci.co.uk/news/480/cpsprodpb/04b4/live/dbe02390-b4f6-11f1-947c-4906c0564be0.jpg.webp)
            
            ## Watch: Emotional Earl Spencer says he misses sister Diana every day
            
            "This is my swan song too. I'm not here to bang on about Diana, this is my final word, celebration of her", Earl Spencer told presenter Laura Kuenssberg.
            
            6 hrs ago UK
            
            </a>
            <a href="/news/articles/cqdj4pez00dzo" ref="39">
            
            ![Trump holding up a tiny model of a triumphal arch in the White House](https://ichef.bbci.co.uk/news/480/cpsprodpb/2e12/live/3a464990-b4f8-11f1-947c-4906c0564be0.jpg.webp)
            
            ## Trump says triumphal arch will be military complex with drones and snipers
            
            Trump said the updated plans were at the request of the US military and for national security purposes.
            
            4 hrs ago US & Canada
            
            </a>
            <a href="/sport/swimming/articles/cqx2z1ky4d6lo" ref="40">
            
            ![Yu Zidi of China wins gold medal after competing in the Women's 200m butterfly final on day one of the 20th Asian Games ](https://ichef.bbci.co.uk/news/480/cpsprodpb/9b63/live/c69b10a0-b4fe-11f1-afad-1d9cb3111733.jpg.webp)
            
            ## Yu, 13, wins record-breaking first major swimming gold medal
            
            China’s Yu Zidi, 13, wins a record‑breaking 200m women’s butterfly gold at the Asian Games in Tokyo.
            
            6 hrs ago Swimming
            
            </a>
            </section>
            </section>
            <section>
            <a href="/news/articles/c8207255rq5lo" ref="41">
            
            ## UK PM hails Greenland deal ahead of expected first Trump meeting
            
            </a>
            <a href="/news/articles/cg4d9vly636o" ref="42">
            
            ## Our head teacher was an abuser. We joined forces to get justice - now we're married
            
            </a>
            <a href="/news/articles/c65y5084x8e0o" ref="43">
            
            ## Top Gear star Richard Hammond thanks emergency crews 20 years on from crash
            
            </a>
            <a href="/news/articles/cm5y7qj54klpo" ref="44">
            
            ## Not all AI workers think the tech could kill everyone
            
            </a>
            <a href="/news/articles/cvgy1q2k2z0o" ref="45">
            
            ## Cultural treasures are being destroyed worldwide - so why is no-one being punished?
            
            </a>
            </section>
            <section>
            
            ---
            
            ## More to explore
            
            <section>
            
            [![The BBC's Laura Kuenssberg sits opposite Earl Spencer in a room at the Althorp estate. ](https://ichef.bbci.co.uk/news/480/cpsprodpb/f04c/live/5fddaaf0-b506-11f1-91cc-c5691e33b858.png.webp)](ref:46:/news/articles/cry4z1xykeleo)
            
            <a href="/news/articles/cry4z1xykeleo" ref="47">
            
            ## Key takeaways from BBC interview as Earl Spencer defends claims about King
            
            Earl Spencer speaks to the BBC's Laura Kuenssberg ahead of the publication of his book about his sister Diana, Princess of Wales.
            
            5 hrs ago UK
            
            </a>
            <a href="/news/articles/czxz0zq9y6do" ref="48">
            
            ![Sharon Horgan in Youth, she is wearing a black top and has her brown hair down](https://ichef.bbci.co.uk/news/480/cpsprodpb/8a8a/live/b0fcfca0-b0f2-11f1-9707-2187f52634c5.jpg.webp)
            
            ## Sex and dating for the sandwich generation - Sharon Horgan on her latest comedy Youth
            
            "It's really weird watching yourself age on screen because that's not fun," Horgan reveals.
            
            21 hrs ago Culture
            
            </a>
            <a href="/news/articles/cmn8ed1xpxnyo" ref="49">
            
            ![Image shows President Donald Trump](https://ichef.bbci.co.uk/news/480/cpsprodpb/3c72/live/446b36e0-b44b-11f1-a430-4d16ee157c41.jpg.webp)
            
            ## Trump escalates media fight after week of setbacks
            
            An embattled Trump has injected his fight with the news media with new acrimony, writes the BBC's Anthony Zurcher.
            
            1 day ago US & Canada
            
            </a>
            <a href="/news/articles/cx1l6762jeq4o" ref="50">
            
            ![Aster Yohannes and Petros Solomon smiling and dressed in white](https://ichef.bbci.co.uk/news/480/cpsprodpb/6132/live/ba962470-b355-11f1-bc1f-3f186ca4140c.png.webp)
            
            ## 'I don't even know if my parents are alive' - the war heroes jailed 25 years ago
            
            The children of key politicians jailed 25 years ago without a trace in Eritrea want answers.
            
            22 hrs ago Africa
            
            </a>
            <a href="/news/articles/c62e04wkj803o" ref="51">
            
            ![A tightly cropped photo of a woman in her 70s with short grey hair looking up at the sky, smiling. She has sunglasses on, and bright red lipstick that matches her leather jacket. Graffiti is in the background, slightly out of focus.](https://ichef.bbci.co.uk/news/480/cpsprodpb/55eb/live/fb40eb10-b28b-11f1-8096-b33a1297275a.jpg.webp)
            
            ## 'People forget we exist': Why older LGBTQ+ people fear losing identity
            
            A report suggests older LGBTQ+ people experience more social isolation than their heterosexual peers.
            
            21 hrs ago Health
            
            </a>
            </section>
            </section>
            <section>
            
            ---
            
            ## Also in news
            
            <section>
            
            [![](https://static.files.bbci.co.uk/bbcdotcom/web/20260907-085256-bea8b058c0-web-3.21.0/grey-placeholder.png)![Ed Sheeran, in a long sleeved white T-shirt, holds a guitar and sings into a microphone. He is looking slightly off to one side. ](https://ichef.bbci.co.uk/news/480/cpsprodpb/846c/live/65039890-b494-11f1-8153-85d44903e598.jpg.webp)](ref:52:/news/articles/cm780ll1de18o)
            
            <a href="/news/articles/cm780ll1de18o" ref="53">
            
            ## Ed Sheeran admits he made 'mistakes' as he addresses Macklemore row at Philadelphia show
            
            The singer spoke about Israel and Gaza after Macklemore was dropped from his tour for making pro-Palestinian remarks on stage.
            
            16 hrs ago US & Canada
            
            </a>
            <a href="/sport/football/articles/cr93e70xv21ko" ref="54">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260907-085256-bea8b058c0-web-3.21.0/grey-placeholder.png)![Andros Townsend playing for Kanchanaburi Power](https://ichef.bbci.co.uk/news/480/cpsprodpb/dffc/live/8ec1f110-b4d3-11f1-b55d-81b847f392df.jpg.webp)
            
            ## Ex-England player Townsend unhurt after being run over by pitch roller
            
            Former England winger Andros Townsend jokes that playing Stoke away "wasn't so bad after all" following a bizarre incident before a game in Thailand.
            
            11 hrs ago Football
            
            </a>
            <a href="/news/articles/crwyzj53pl1lo" ref="55">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260907-085256-bea8b058c0-web-3.21.0/grey-placeholder.png)![The Converse name is seen on a storefront at an airport in Singapore earlier this month](https://ichef.bbci.co.uk/news/480/cpsprodpb/3993/live/0f1fa560-b433-11f1-9fa0-111a12dcb7b0.jpg.webp)
            
            ## Converse pulls 'deeply upsetting' advert after backlash
            
            Social media users say an image from the brand's Instagram account resembled a KKK hood and hanging.
            
            1 day ago US & Canada
            
            </a>
            <a href="/news/articles/cj4gklz9dxplo" ref="56">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260907-085256-bea8b058c0-web-3.21.0/grey-placeholder.png)![The White House press room full of reporters with their hands raised](https://ichef.bbci.co.uk/news/480/cpsprodpb/1b27/live/d8b31f40-b435-11f1-9fa0-111a12dcb7b0.jpg.webp)
            
            ## Journalists denied White House access after Trump banned some media outlets
            
            CNN, MS NOW and Politico reporters' White House press badges were confiscated, the outlets reported.
            
            1 day ago US & Canada
            
            </a>
            <a href="/news/articles/cwly5d9v7r43o" ref="57">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260907-085256-bea8b058c0-web-3.21.0/grey-placeholder.png)![arge plume of black smoke rises from the fuel storage area at King Khalid International Airport](https://ichef.bbci.co.uk/news/480/cpsprodpb/39c5/live/a0844fe0-b45d-11f1-b213-1db858c3e27c.jpg.webp)
            
            ## Houthis say they targeted Saudi capital with ballistic missiles
            
            A reported strike on a fuel depot at Riyadh airport caused delays on Saturday, as Saudi Arabia says it shot down a ballistic missile aimed at the capital.
            
            21 hrs ago Middle East
            
            </a>
            </section>
            </section>
            <section>
            
            ---
            
            ## Most watched
            
            <a href="/news/videos/cw62mdrjll18o" ref="58">
            
            1
            
            ## Watch: Emotional Earl Spencer says he misses sister Diana every day
            
            </a>
            <a href="/news/videos/cmy0z2ljelgko" ref="59">
            
            2
            
            ## Earl Spencer's claims about King's reaction to Diana's death opens old wounds
            
            </a>
            <a href="/news/videos/c6q8jpxxv9k2o" ref="60">
            
            3
            
            ## Watch: Earl Spencer says he felt Charles had 'giddy tone' during call after Diana's death
            
            </a>
            <a href="/news/videos/cv1j4794gn6po" ref="61">
            
            4
            
            ## Watch: Diana's brother says press has 'cancerous influence' on Harry and Meghan's lives
            
            </a>
            <a href="/news/videos/ckd68l9g0gy5o" ref="62">
            
            5
            
            ## Watch: Concertgoers and pro-Palestinian activists arrive at Ed Sheeran concert
            
            </a>
            </section>
            <section>
            
            ---
            
            ## Most read
            
            <a href="/news/articles/cvwyz29n0nn2o" ref="63">
            
            1
            
            ## German Chancellor Merz calls state election a 'disaster' for his party but vows to stay on
            
            </a>
            <a href="/news/articles/c34gdjk1ne8yo" ref="64">
            
            2
            
            ## Largest attack on Moscow sees Ukraine fire hundreds of drones, mayor says
            
            </a>
            <a href="/news/articles/cqdj4pez00dzo" ref="65">
            
            3
            
            ## Trump says triumphal arch will be military complex with drones and snipers
            
            </a>
            <a href="/news/articles/cm780ll1de18o" ref="66">
            
            4
            
            ## Ed Sheeran admits 'mistakes' at first show since Macklemore controversy
            
            </a>
            <a href="/news/articles/cry4z1xykeleo" ref="67">
            
            5
            
            ## Key takeaways from BBC interview as Earl Spencer defends claims about King
            
            </a>
            <a href="/news/articles/cwj3d7zrgvk1o" ref="68">
            
            6
            
            ## Harry and Meghan's media treatment echoes what happened to Diana, Earl Spencer tells BBC
            
            </a>
            <a href="/news/articles/cm0463619r1no" ref="69">
            
            7
            
            ## Billionaire Man United owner says he has lost confidence in the UK
            
            </a>
            <a href="/news/articles/c39w4n07ekeno" ref="70">
            
            8
            
            ## The deadly new drugs making Scotland's fight against addiction even harder
            
            </a>
            <a href="/news/articles/crwyzj53pl1lo" ref="71">
            
            9
            
            ## Converse pulls 'deeply upsetting' advert after backlash
            
            </a>
            <a href="/news/articles/ckvgyr6m9jmeo" ref="72">
            
            10
            
            ## I loved capybaras before they were viral - I'm so glad they're having a moment
            
            </a>
            </section>
            <section>
            
            ---
            
            ## Sport
            
            <section>
            
            [![](https://static.files.bbci.co.uk/bbcdotcom/web/20260907-085256-bea8b058c0-web-3.21.0/grey-placeholder.png)![Matheus Cunha](https://ichef.bbci.co.uk/ace/standard/480/cpsprodpb/765e/live/020d3aa0-b518-11f1-aae2-bf700d72ca2a.jpg.webp)](ref:73:https://www.bbc.com/sport/football/live/cqvgy1vzey9rt)
            
            <a href="https://www.bbc.com/sport/football/live/cqvgy1vzey9rt" ref="74">
            
            ## Cunha rescues point for Man Utd at Fulham
            
            Matheus Cunha's deflected strike rescues a point for Manchester United late on to deny Fulham a first Premier League win of the season.
            
            </a>
            <a href="/sport/football/articles/cvp8d4116zglo" ref="75">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260907-085256-bea8b058c0-web-3.21.0/grey-placeholder.png)![Brighton celebrate goal against Arsenal](https://ichef.bbci.co.uk/news/480/cpsprodpb/9b6b/live/4871c290-b51d-11f1-b01b-6bc45d07eb85.jpg.webp)
            
            ## What have we learned from the Premier League so far?
            
            BBC Sport takes a look at the opening weeks of the Premier League season as a long international break arrives.
            
            1 hr ago Premier League
            
            </a>
            <a href="/sport/football/articles/cxddvp49gg1ro" ref="76">
            
            ## Last-minute Cunha goal saves Man Utd from another defeat - who rated highest at Fulham?
            
            Last minute Cunha goal saves Man Utd from another defeat - who rated highest at Fulham?
            
            3 hrs ago Premier League
            
            </a>
            <a href="/sport/darts/articles/cr5ye91l200go" ref="77">
            
            ## Littler could boycott Dutch events over booing
            
            Luke Littler threatens a boycott of playing in the Netherlands after being booed and whistled at the World Series of Darts Finals in Amsterdam.
            
            12 hrs ago Darts
            
            </a>
            <a href="https://www.bbc.com/sport/football/live/c61mv7p2k4dlt" ref="78">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260907-085256-bea8b058c0-web-3.21.0/grey-placeholder.png)![Atletico Madrid players celebrate](https://ichef.bbci.co.uk/ace/standard/480/cpsprodpb/d8e1/live/0949cd70-b50d-11f1-91cc-c5691e33b858.jpg.webp)
            
            ## Atletico beat 10-man Real in feisty Madrid derby
            
            Atletico Madrid move above Real in La Liga with 2-1 derby win over their 10-man neighbours
            
            </a>
            <a href="/sport/football/articles/c6z7z2we5lgwo" ref="79">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260907-085256-bea8b058c0-web-3.21.0/grey-placeholder.png)![Rayan Cherki and Enzo Fernandez](https://ichef.bbci.co.uk/news/480/cpsprodpb/1732/live/e8376180-b50a-11f1-91cc-c5691e33b858.jpg.webp)
            
            ## Perfect 24 hours for Maresca as Man City put pressure on Arsenal
            
            Manchester City head into the international break in a jubilant mood after enjoying the perfect 24 hours this weekend.
            
            4 hrs ago Man City
            
            </a>
            <a href="/sport/football/articles/c317j2lzg8rdo" ref="80">
            
            ![](https://static.files.bbci.co.uk/bbcdotcom/web/20260907-085256-bea8b058c0-web-3.21.0/grey-placeholder.png)![Ryan Naderi scores for Rangers](https://ichef.bbci.co.uk/news/480/cpsprodpb/7d6a/live/78a0d860-b50f-11f1-b1d1-571ed4d7ff2c.jpg.webp)
            
            ## Rangers look like they know where they want to go - Celtic just look lost
            
            Tom English reflects on the first Old Firm league game of the season, in which Rangers repeated their League Cup win over Celtic.
            
            4 hrs ago Football
            
            </a>
            </section>
            </section>
            <section>
            <section>
            
            [![](https://static.files.bbci.co.uk/bbcdotcom/web/20260907-085256-bea8b058c0-web-3.21.0/grey-placeholder.png)![A collage of images and illustrations](https://ichef.bbci.co.uk/ace/standard/480/cpsprodpb/4234/live/ccb11760-566c-11f0-9074-8989d8c97d87.png.webp)](ref:81:https://cloud.email.bbc.com/bbcnewsignup2?&at_bbc_team=studios&at_medium=display&at_objective=acquisition&at_ptr_type=&at_ptr_name=bbc.comhp&at_format=Module&at_link_origin=intlfront&at_campaign=newsbriefing&at_campaign_type=owned)
            
            <a href="https://cloud.email.bbc.com/bbcnewsignup2?&amp;at_bbc_team=studios&amp;at_medium=display&amp;at_objective=acquisition&amp;at_ptr_type=&amp;at_ptr_name=bbc.comhp&amp;at_format=Module&amp;at_link_origin=intlfront&amp;at_campaign=newsbriefing&amp;at_campaign_type=owned" ref="82">
            
            ## Sign up to News Briefing
            
            News and expert analysis for every schedule. Get morning and evening editions of our flagship newsletter in your inbox. See more
            
            </a>
            </section>
            </section>
            </article>
            </main>
            
            ---
            
            <footer id="bbc-footer">
            
            [![British Broadcasting Corporation]()](ref:83:/)
            
            <section>
            <nav aria-label="Footer navigation">
            
            - [Home](ref:84:https://www.bbc.com/)
            - [News](ref:85:/news)
            - [Sport](ref:86:/sport)
            - [Business](ref:87:/business)
            - [Technology](ref:88:/technology)
            - [Health](ref:89:/health)
            - [Culture](ref:90:/culture)
            - [Arts](ref:91:/arts)
            - [Travel](ref:92:/travel)
            - [Earth](ref:93:/future-planet)
            - [Audio](ref:94:/audio)
            - [Video](ref:95:/video)
            - [Live](ref:96:/live)
            - [Weather](ref:97:https://www.bbc.com/weather)
            - [BBC Shop](ref:98:https://shop.bbc.com/)
            - [BritBox](ref:99:https://www.britbox.com/?utm_source=bbc.com&utm_medium=referral&utm_campaign=footer)
            
            </nav>
            </section>
            <section>
            <button type="button" ref="100">
            
            BBC in other languages
            
            </button>
            </section>
            
            Follow BBC on:
            
            <button aria-label="Follow BBC on x" ref="101">
            
            Follow BBC on x
            
            </button>
            <button aria-label="Follow BBC on facebook" ref="102">
            
            Follow BBC on facebook
            
            </button>
            <button aria-label="Follow BBC on instagram" ref="103">
            
            Follow BBC on instagram
            
            </button>
            <button aria-label="Follow BBC on tiktok" ref="104">
            
            Follow BBC on tiktok
            
            </button>
            <button aria-label="Follow BBC on linkedin" ref="105">
            
            Follow BBC on linkedin
            
            </button>
            <button aria-label="Follow BBC on youtube" ref="106">
            
            Follow BBC on youtube
            
            </button>
            <section>
            <nav>
            
            - [Terms of Use](ref:107:https://www.bbc.com/pages/terms-of-use)
            - [About the BBC](ref:108:https://www.bbc.co.uk/aboutthebbc)
            - [Privacy Policy](ref:109:https://www.bbc.com/pages/privacy-policy)
            - [Cookies](ref:110:https://www.bbc.com/usingthebbc/cookies/)
            - [Accessibility Help](ref:111:https://www.bbc.co.uk/accessibility/)
            - [Contact the BBC](ref:112:https://www.bbc.co.uk/contact)
            - [Advertise with us](ref:113:https://advertising.bbcstudios.com/)
            - [Do not share or sell my info](ref:114:https://www.bbc.com/usingthebbc/cookies/how-can-i-change-my-bbc-cookie-settings/)
            - [BBC.com Help & FAQs](ref:115:https://help.bbc.com/hc/)
            - [Content Index](ref:116:https://www.bbc.com/pages/content-index)
            - [Set Preferred Source](ref:117:https://www.bbc.com/future/article/20260128-how-to-make-google-put-trusted-sources-up-top-when-you-search)
            
            </nav>
            </section>
            
            Copyright 2026 BBC. All rights reserved. The BBC is not responsible for the content of external sites. [:DACi:](ref:118:https://www.bbc.com/editorialguidelines/guidance/links-and-feeds)
            
            </footer>
            <section>
            
            ## Let us know you agree to cookies
            
            We use [cookies](ref:119:https://www.bbc.com/usingthebbc/cookies/what-do-i-need-to-know-about-cookies/) to give you the best online experience.
            
            Please let us know if you agree to all of these cookies.
            
            
            - <button id="bbccookies-continue-button" type="button" ref="120">
            
              Yes, I agree
            
              </button>
            
            - [No, take me to settings](ref:121:https://www.bbc.com/usingthebbc/cookies/how-can-i-change-my-bbc-cookie-settings/)
            
            </section>
            <iframe id="sp_message_iframe_1489022" src="https://cdn.privacy-mgmt.com/index.html?hasCsp=true&amp;message_id=1489022&amp;consentUUID=null&amp;consent_origin=https%3A%2F%2Fcdn.privacy-mgmt.com%2Fconsent%2Ftcfv2&amp;preload_message=true&amp;version=v1" title="SP Consent Message">
            </iframe>
        """.trimIndent()
    }

    @Test
    fun `should round-trip the rendered Markdown to a stable fixpoint`() = runTest {
        // given — the Markdown the pipeline produces for the BBC News dump
        val markdown = dumpFlow(DumpFixtures.bbcNews).transformHtmlToMarkdown().renderMarkdown()

        // when
        val roundtripped = flowOf(markdown).parse().renderMarkdown()

        // then — a clean fixpoint: parsing and re-rendering reproduces the
        // pipeline Markdown exactly. This dump exercises the heavy raw-HTML
        // shape (block-wrapping `<a>` links and nested `<header>`/`<nav>`/
        // `<section>` wrappers around Markdown headings, lists and images); the
        // round-trip is stable because the renderer treats a line-start `<a>`
        // as a block tag, the parser closes same-named HTML nesting one level
        // at a time, and an empty `<p>` renders to nothing.
        roundtripped sameAs markdown
    }

}
