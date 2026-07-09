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

class SerpBraveTest {

    @Test
    fun `should convert captured serp-brave DOM dump to Markdown`() = runTest {
        // given
        val events = dumpFlow(DumpFixtures.serpBrave)

        // when
        val markdown = events.transformHtmlToMarkdown().renderMarkdown()

        // then
        markdown sameAs /* language=markdown */ $$"""
            ---
            lang: en-gb
            description: Search the Web. Privately. Truly useful results, AI-powered answers, & more. All from an independent index. No profiling, no bias, no Big Tech.
            "twitter:card": summary_large_image
            "twitter:title": Brave Search
            "twitter:description": Search the Web. Privately. Truly useful results, AI-powered answers, & more. All from an independent index. No profiling, no bias, no Big Tech.
            "twitter:image": "https://cdn.search.brave.com/serp/v3/_app/immutable/assets/ogImg.6rMZQHXQ.png"
            keywords: brave, search
            title: markdown parser - Brave Search
            ---
            
            <header id="site-header">
            
            [![Brave logo](https://cdn.search.brave.com/serp/v3/_app/immutable/assets/brave-logo-dark.5D16vJCY.svg)](ref:1:/)
            
            <form id="searchform" action="/search" method="GET" target="_self">
            <input id="searchbox" type="text" name="q" placeholder="Ask anything, find anything…" aria-label="Search" aria-haspopup="false" ref="2">
            <button id="clear-query-button" type="reset" aria-label="Clear" ref="3">
            </button>
            <button id="submit-button" aria-label="Search" ref="4">
            </button>
            <button id="submit-llm-button" type="button" aria-label="Ask" ref="5">
            </button>
            </form>
            <button id="quick-goggles-button" type="button" aria-label="Rerank" ref="6">
            </button>
            <button id="settings-button" type="button" aria-label="Quick settings" ref="7">
            </button>
            </header>
            <nav>
            
            - [Ask](ref:8:/ask?q=markdown+parser&source=web)
            - [All](ref:9:/search?q=markdown+parser&source=web)
            - [Images](ref:10:/images?q=markdown+parser&source=web)
            - [News](ref:11:/news?q=markdown+parser&source=web)
            - [Videos](ref:12:/videos?q=markdown+parser&source=web)
            - [Goggles](ref:13:/goggles?q=markdown+parser&source=web)
            
            
            <button type="button" aria-label="Filters" ref="14">
            </button>
            
            </nav>
            <main id="search-page">
            <main>
            
            [![🌐](https://imgs.search.brave.com/DNNWccTD9Mtp2P0DINzOrm_jD-e9lJ9a0neUmmf_Hls/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvMmJiNGQxYmNh/ZGJmZjUxMzQ3ZWRi/YjRmZDg2NGJjNTRm/ODlmNWQ5Njg3NzI2/MDZiYzkwYWVlNWUz/NjgyYTAzNC9tYXJr/ZG93bmxpdmVwcmV2/aWV3LmNvbS8) Markdown Live Preview <cite>markdownlivepreview.com</cite> Markdown Live Preview](ref:15:https://markdownlivepreview.com/)You may be using [Markdown Live Preview](https://markdownlivepreview.[![🌐](https://imgs.search.brave.com/iGZu5NAmNyRvPlAPhQoHBLF3CDq5A0Ez0PrCoeRotUo/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvMmY2MTM4MTBk/NzVlNTIzMGQyYjMy/Y2M0N2M3NzAzMTgz/NmE0OGFjOTAyZjUx/M2Q2ZWFiZmY2NTRm/ODQ1Mjk2Ni9kaWxs/aW5nZXIuaW8v) Dillinger <cite>dillinger.io</cite> Markdown Editor — Online, Free, with Live Preview | Dillinger](ref:16:https://dillinger.io/)Free online Markdown editor with live preview. Write, format, and export Markdown to HTML or PDF — sync to GitHub, Dropbox & Google Drive. No signup.
            
            <header>
            
            People also ask
            
            </header>
            
            ---
            
            <details>
            <summary ref="17">
            
            Can I export markdown to PDF or HTML?
            
            </summary>
            </details>
            <details>
            <summary ref="19">
            
            What is the best free online markdown editor?
            
            </summary>
            </details>
            <details>
            <summary ref="21">
            
            How is Dillinger different from StackEdit or Typora?
            
            </summary>
            </details>
            <footer>
            <button aria-label="Feedback" ref="23">
            </button>
            </footer>
            
            ##### Videos
            
            [![](https://imgs.search.brave.com/oM5jgpUmMXnrP4sHpIySxvtQ5UVIeLNul87OkYJ5m1M/rs:fit:200:200:1:0/g:ce/aHR0cHM6Ly9pLnl0/aW1nLmNvbS92aS83/LUZjUjNCZUhJcy9t/YXhyZXNkZWZhdWx0/LmpwZw) 01:27:43 ![🌐](https://imgs.search.brave.com/Wg4wjE5SHAargkzePU3eSLmWgVz84BEZk1SjSglJK_U/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvOTkyZTZiMWU3/YzU3Nzc5YjExYzUy/N2VhZTIxOWNlYjM5/ZGVjN2MyZDY4Nzdh/ZDYzMTYxNmI5N2Rk/Y2Q3N2FkNy93d3cu/eW91dHViZS5jb20v) YouTube Markdown Parser in C | Live Stream - YouTube 18 August 2025](ref:24:https://www.youtube.com/watch?v=7-FcR3BeHIs)[![](https://imgs.search.brave.com/-ehIYAByMUNMa2lIR05m-SjB-8JystPsCRqb8rxSfWc/rs:fit:200:200:1:0/g:ce/aHR0cHM6Ly9pLnl0/aW1nLmNvbS92aS9i/WTJsX0o0ak9lTS9t/YXhyZXNkZWZhdWx0/LmpwZw) 11:08 ![🌐](https://imgs.search.brave.com/Wg4wjE5SHAargkzePU3eSLmWgVz84BEZk1SjSglJK_U/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvOTkyZTZiMWU3/YzU3Nzc5YjExYzUy/N2VhZTIxOWNlYjM5/ZGVjN2MyZDY4Nzdh/ZDYzMTYxNmI5N2Rk/Y2Q3N2FkNy93d3cu/eW91dHViZS5jb20v) YouTube Build Your Own Markdown Parser From Scratch (Tokenizer) - YouTube 13 July 2025](ref:25:https://www.youtube.com/watch?v=bY2l_J4jOeM)[![](https://imgs.search.brave.com/agbJomdynxYE3MRAn7rgy78Y9vh9x9RdQAOFtQFXsJA/rs:fit:200:200:1:0/g:ce/aHR0cHM6Ly9pLnl0/aW1nLmNvbS92aS9u/cW1HTUwxY01qVS9t/YXhyZXNkZWZhdWx0/LmpwZw) 11:01 ![🌐](https://imgs.search.brave.com/Wg4wjE5SHAargkzePU3eSLmWgVz84BEZk1SjSglJK_U/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvOTkyZTZiMWU3/YzU3Nzc5YjExYzUy/N2VhZTIxOWNlYjM5/ZGVjN2MyZDY4Nzdh/ZDYzMTYxNmI5N2Rk/Y2Q3N2FkNy93d3cu/eW91dHViZS5jb20v) YouTube Build Your Own Markdown Parser From Scratch (Syntatic Analysis) ... 18 July 2025](ref:26:https://www.youtube.com/watch?v=nqmGML1cMjU)[![](https://imgs.search.brave.com/J4B3GhxPOwa1p_d71QfzpOb-hzyKHAmcMLLMTjm-3OY/rs:fit:200:200:1:0/g:ce/aHR0cHM6Ly9pLnl0/aW1nLmNvbS92aS9i/Y0M1a182WWUxby9t/YXhyZXNkZWZhdWx0/LmpwZw) 10:13 ![🌐](https://imgs.search.brave.com/Wg4wjE5SHAargkzePU3eSLmWgVz84BEZk1SjSglJK_U/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvOTkyZTZiMWU3/YzU3Nzc5YjExYzUy/N2VhZTIxOWNlYjM5/ZGVjN2MyZDY4Nzdh/ZDYzMTYxNmI5N2Rk/Y2Q3N2FkNy93d3cu/eW91dHViZS5jb20v) YouTube Build Your Own Markdown Parser From Scratch - YouTube 24 July 2025](ref:27:https://www.youtube.com/watch?v=bcC5k_6Ye1o)[![](https://imgs.search.brave.com/Y27Byu_fCALwm-1zHc_a6qwn0DsmkhVwdFCeT0cUlYY/rs:fit:200:200:1:0/g:ce/aHR0cHM6Ly9pLnl0/aW1nLmNvbS92aS9V/NVUwSDBQdnZ0VS9t/YXhyZXNkZWZhdWx0/LmpwZw) 13:13 ![🌐](https://imgs.search.brave.com/Wg4wjE5SHAargkzePU3eSLmWgVz84BEZk1SjSglJK_U/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvOTkyZTZiMWU3/YzU3Nzc5YjExYzUy/N2VhZTIxOWNlYjM5/ZGVjN2MyZDY4Nzdh/ZDYzMTYxNmI5N2Rk/Y2Q3N2FkNy93d3cu/eW91dHViZS5jb20v) YouTube Parse and Render Markdown with C++ (fast) - YouTube 26 May 2025](ref:28:https://www.youtube.com/watch?v=U5U0H0PvvtU)[View all](ref:29:/videos?q=markdown parser&source=vcluster)[![🌐](https://imgs.search.brave.com/xxsA4YxzaR0cl-DBsH9-lpv2gsif3KMYgM87p26bs_o/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvYWQyNWM1NjA5/ZjZmZjNlYzI2MDNk/N2VkNmJhYjE2MzZl/MDY5ZTMxMDUzZmY1/NmU3NWIzNWVmMjk0/NTBjMjJjZi9naXRo/dWIuY29tLw) GitHub <cite>github.com › markdown-it › markdown-it</cite> GitHub - markdown-it/markdown-it: Markdown parser, done right. 100% CommonMark support, extensions, syntax plugins & high speed · GitHub](ref:30:https://github.com/markdown-it/markdown-it)Markdown parser, done right. 100% CommonMark support, extensions, syntax plugins & high speed - markdown-it/markdown-it **Starred** by 21.6K users**Forked** by 1.8K users**Languages**   JavaScript 97.4% | HTML 1.8% | CSS 0.8%[](ref:31:https://github.com/markdown-it/markdown-it)[![🌐](https://imgs.search.brave.com/dKusAYBYTLeCBl16XSMYRZO-wCc_EyGpoH65Oj11tOU/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvMmMzNjVjYjk4/NmJkODdmNTU4ZDU1/MGUwNjk0MWFmZWU0/NmYzZjVlYmZjZDIy/MWM4MGMwODc4MDhi/MDM5MmZkYy9sZWFy/bi5taWNyb3NvZnQu/Y29tLw) Microsoft Learn <cite>learn.microsoft.com › de-de › dotnet › communitytoolkit › archive › windows › markdownparser</cite> Markdown-Parser - Community Toolkits for .NET | Microsoft Learn](ref:32:https://learn.microsoft.com/de-de/dotnet/communitytoolkit/archive/windows/markdownparser)5 January 2026 - Mit dem Markdown-Parser **können Sie eine Markdown-Zeichenfolge in ein Markdown-Dokument analysieren und dann mit einem Markdown-Renderer rendern**.[](ref:33:https://learn.microsoft.com/de-de/dotnet/communitytoolkit/archive/windows/markdownparser)[![🌐](https://imgs.search.brave.com/129PsExJCizx2_pC-7e4exnT1C0gWE4PUinIWjrXr-4/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvMzg1ZTZiOGFm/M2NhY2JjMmE1NmJl/ZTRlODIwNDVhZWIy/OWRjZDgzYjYyYjcw/NjhmNzQzMWM0NDBk/Y2U3MGIzMi93d3cu/bnBtanMuY29tLw) npm <cite>npmjs.com › package › markdown-parser</cite> markdown-parser - npm](ref:34:https://www.npmjs.com/package/markdown-parser)13 May 2026 - **The main parser class that converts markdown text into a structured block AST (headings, paragraphs, lists, etc.).**
            
            » npm install markdown-parser
            
            <button ref="35">
            </button>
            
            **Published**   May 13, 2026**Version**   0.1.3[](ref:36:https://www.npmjs.com/package/markdown-parser)[![🌐](https://imgs.search.brave.com/qnamV7kCUSH_wAMhKTiy2IIqVM1Oz8dbPrG310AT4RE/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvY2Y5NGQyMmMx/YjQyZDE2OGQ4Yzc1/NjkyMmJiMWYzZDIw/YjRlODY1NDJhYjAw/Mzc5ZDIyMTc5ZDZl/MTc5NWE2Ni9jaHJv/bWV3ZWJzdG9yZS5n/b29nbGUuY29tLw) Chrome Web Store <cite>chromewebstore.google.com › detail › markdown-viewer › ckkdlimhmcjmikdlpkmbgfkaikojcbjk</cite> Markdown Viewer - Chrome Web Store](ref:37:https://chromewebstore.google.com/detail/markdown-viewer/ckkdlimhmcjmikdlpkmbgfkaikojcbjk)✔ Secure by design ✔ Render local and remote file URLs ✔ Granular access to remote origins ✔ Multiple **markdown parser**s ✔ Full control over the compiler options ✔ 30+ Themes ✔ Custom theme support ✔ GitHub Flavored Markdown (GFM) ✔ Auto reload on file change ✔ Syntax highlighted code blocks ✔ Table of Contents (ToC) ✔ MathJax formulas ✔ Mermaid diagrams ✔ Convert emoji shortnames ✔ Remember scroll position ✔ Markdown Content-Type detection ✔ Configurable Markdown file path detection ✔ Settings synchronization ✔ Raw and rendered markdown views ✔ Free and Open Source ✚ Full Documentation ✔ https://github.com/simov/markdown-viewer[](ref:38:https://chromewebstore.google.com/detail/markdown-viewer/ckkdlimhmcjmikdlpkmbgfkaikojcbjk)
            
            <header>
            
            Find elsewhere
            
            </header>
            
            [Google](ref:39:/search?q=markdown%20parser%20!g)[Bing](ref:40:/search?q=markdown%20parser%20!b)[Mojeek](ref:41:/search?q=markdown%20parser%20!mojeek)[![🌐](https://imgs.search.brave.com/xxsA4YxzaR0cl-DBsH9-lpv2gsif3KMYgM87p26bs_o/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvYWQyNWM1NjA5/ZjZmZjNlYzI2MDNk/N2VkNmJhYjE2MzZl/MDY5ZTMxMDUzZmY1/NmU3NWIzNWVmMjk0/NTBjMjJjZi9naXRo/dWIuY29tLw) GitHub <cite>github.com › markedjs › marked</cite> GitHub - markedjs/marked: A markdown parser and compiler. Built for speed. · GitHub](ref:42:https://github.com/markedjs/marked)A **markdown parser** and compiler. Built for speed. Contribute to markedjs/marked development by creating an account on GitHub. **Starred** by 36.9K users**Forked** by 3.5K users**Languages**   JavaScript 37.4% | TypeScript 34.4% | HTML 28.2%[](ref:43:https://github.com/markedjs/marked)[![🌐](https://imgs.search.brave.com/G-u_m0Yl_uydaKTlwTSKP63MY_rte4_Nwi4MFTCaOYQ/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvYWViNjUzNTUz/MGU2NjUzYzBjMTIz/Zjg0YzIzNDk0YzU1/YWQ3OTE0MmM3NjQy/MGQxMmFmODZkMTkx/Mjc1NTQxNy9tYXJr/ZG93bnZpZXdlci5w/YWdlcy5kZXYv) Pages <cite>markdownviewer.pages.dev</cite> Markdown Viewer](ref:44:https://markdownviewer.pages.dev/)**Markdown Viewer** is a powerful GitHub-style Markdown rendering tool with live preview, LaTeX math, Mermaid diagrams, syntax highlighting, dark mode, and export options to PDF, HTML, and MD—all fully client-side and secure.[![🌐](https://imgs.search.brave.com/0yLSJT38DCU3QUyFqQ-47_qvkfjHhmzz-r8y9-tzSlQ/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvMzBiYjVkYzE3/MDhiODU4NTlmZDY1/M2RiOWZjOTRiZTkx/YWI4NWRlYjRkNGZm/MmY0MmI5MmMzNWRi/NTM3ZWQ2Mi9iZXR0/ZXJwcm9ncmFtbWlu/Zy5wdWIv) Better Programming <cite>betterprogramming.pub › create-your-own-markdown-parser-bffb392a06db</cite> Creating Your Own Markdown Parser | by Vidhi Khaitan | Better Programming](ref:45:https://betterprogramming.pub/create-your-own-markdown-parser-bffb392a06db)7 February 2022 - Here is the basic syntax of markdown language. A parser is **a compiler or interpreter component that breaks data into smaller elements for easy translation into another language**.[](ref:46:https://betterprogramming.pub/create-your-own-markdown-parser-bffb392a06db)[![🌐](https://imgs.search.brave.com/xxsA4YxzaR0cl-DBsH9-lpv2gsif3KMYgM87p26bs_o/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvYWQyNWM1NjA5/ZjZmZjNlYzI2MDNk/N2VkNmJhYjE2MzZl/MDY5ZTMxMDUzZmY1/NmU3NWIzNWVmMjk0/NTBjMjJjZi9naXRo/dWIuY29tLw) GitHub <cite>github.com › mity › md4c</cite> GitHub - mity/md4c: C Markdown parser. Fast. SAX-like interface. Compliant to CommonMark specification. · GitHub](ref:47:https://github.com/mity/md4c)16 May 2026 - C Markdown parser. Fast. SAX-like interface. Compliant to CommonMark specification. - mity/md4c **Starred** by 1.4K users**Forked** by 203 users**Languages**   C 91.5% | Python 6.4% | CMake 1.2%[](ref:48:https://github.com/mity/md4c)[![🌐](https://imgs.search.brave.com/d3qHo7-7fIh4HFjZjq1Z-0qVrHgxkLwAImk-ZyQ7v2A/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvNWU5MzZlZGE0/Yzg0ZjFiYzdiMzgw/MTc5NmIyMjQ5ZmQ0/OGMzYmQzODU2ZjI1/M2M1ZDVhYmNiYzll/NzgyYzE2OS9zdGFj/a2VkaXQuaW8v) StackEdit <cite>stackedit.io</cite> StackEdit – In-browser Markdown editor](ref:49:https://stackedit.io/)StackEdit provides very handy formatting buttons and shortcuts, thanks to PageDown, the WYSIWYG-style Markdown editor used by Stack Overflow.[![🌐](https://imgs.search.brave.com/4WRMec_wn8Q9LO6DI43kkBvIL6wD5TYCXztC9C9kEI0/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvNWU3Zjg0ZjA1/YjQ3ZTlkNjQ1ODA1/MjAwODhiNjhjYWU0/OTc4MjM4ZDJlMTBi/ODExYmNiNTkzMjdh/YjM3MGExMS9zdGFj/a292ZXJmbG93LmNv/bS8) Stack Overflow <cite>stackoverflow.com › questions › 605434 › how-would-you-go-about-parsing-markdown</cite> How would you go about parsing Markdown? - Stack Overflow](ref:50:https://stackoverflow.com/questions/605434/how-would-you-go-about-parsing-markdown)
            
            <a href="https://stackoverflow.com/questions/605434/how-would-you-go-about-parsing-markdown" ref="51">
            
            Top answer 1 of 10 73 
            
            The only markdown implementation I know of, that uses an actual parser, is Jon MacFarleane’s peg-markdown. Its parser is based on a Parsing Expression Grammar parser generator called peg.
            
            EDIT: Mauricio Fernandez recently released his Simple Markup Markdown parser, which he wrote as part of his OcsiBlog Weblog Engine. Because the parser is written in OCaml, it is *extremely* simple and short (268 SLOC for the parser, 43 SLOC for the HTML emitter), yet *blazingly* fast (20% faster than discount (written in hand-optimized C) and *sixhundred* times faster than BlueCloth (Ruby)), despite the fact that it isn't even optimized for performance yet. Because it is only intended for internal use by Mauricio himself for his weblog, there are a few deviations from the official Markdown specification, but Mauricio has created a branch which reverts most of those changes.
            
            </a>
            <a href="https://stackoverflow.com/questions/605434/how-would-you-go-about-parsing-markdown" ref="52">
            
            2 of 10 18 
            
            I released a new parser-based Markdown Java implementation last week, called pegdown. pegdown uses a PEG parser to first build an abstract syntax tree, which is subsequently written out to HTML. As such it is quite clean and much easier to read, maintain and extend than a regex based approach. The PEG grammar is based on John MacFarlanes C implementation "peg-markdown".
            
            Maybe something of interest to you...
            
            </a>
            
            [![🌐](https://imgs.search.brave.com/cHR1z3j4S0EzHoVTIbP5tZsbiY9M4eBD76hYfqp1q38/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvMDQzODgyMDJm/ZTI4YmM1MzY0YmFk/NDA2ZDhiZGM0ZDNi/MjIxMWY5MzkzMjRm/NzNlNGQ3MjY3YmNh/MzA5MGIyNy9nZXRr/aXJieS5jb20v) Kirby CMS <cite>getkirby.com › docs › reference › plugins › components › markdown</cite> Markdown parser | Kirby CMS](ref:53:https://getkirby.com/docs/reference/plugins/components/markdown)Kirby::plugin('my/markdown', [ 'components' => [ 'markdown' => function (Kirby $kirby, string $text = null, array $options = [], bool $inline = false) { return YourMarkdownParser::parse($text); } ] ]);[](ref:54:https://getkirby.com/docs/reference/plugins/components/markdown)[![🌐](https://imgs.search.brave.com/129PsExJCizx2_pC-7e4exnT1C0gWE4PUinIWjrXr-4/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvMzg1ZTZiOGFm/M2NhY2JjMmE1NmJl/ZTRlODIwNDVhZWIy/OWRjZDgzYjYyYjcw/NjhmNzQzMWM0NDBk/Y2U3MGIzMi93d3cu/bnBtanMuY29tLw) npm <cite>npmjs.com › package › stream-markdown-parser</cite> stream-markdown-parser - npm](ref:55:https://www.npmjs.com/package/stream-markdown-parser)16 May 2026 - For declared custom tags, content and raw are kept close to the source payload (including quote- or whitespace-sensitive data such as JSON), while children still comes from normal inline Markdown parsing. Heuristic function to detect if content looks like mathematical notation. ... Find the matching closing delimiter in a string, handling nested pairs. ... Reuse parser instances: cache getMarkdown() results per worker/request to avoid re-registering plugins.
            
            » npm install stream-markdown-parser
            
            <button ref="56">
            </button>
            
            **Published**   Jun 05, 2026**Version**   1.0.3[![🌐](https://imgs.search.brave.com/U-eHNCapRHVNWWCVPPMTIvOofZULh0_A_FQKe8xTE4I/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvN2ZiNTU0M2Nj/MTFhZjRiYWViZDlk/MjJiMjBjMzFjMDRk/Y2IzYWI0MGI0MjVk/OGY5NzQzOGQ5NzQ5/NWJhMWI0NC93d3cu/cmVkZGl0LmNvbS8) Reddit <cite>reddit.com › r/typescript › how to parse markdown content (without external packages, from scratch)</cite> r/typescript on Reddit: How to parse Markdown content (without external packages, from scratch)](ref:57:https://www.reddit.com/r/typescript/comments/1l5p78s/how_to_parse_markdown_content_without_external/)8 June 2025 -
            
            Planning to render and beautify some cooking recipes that I've compiled into .md files (as a Vue app). Would like to try to parse and render the MD on my own, as a learning exercise, in the following manner:
            
            1. Prepare regular expressions for each MD syntax element I want to support
            2. Read any .md file, break it into lines, and iteratively test every line on the reg-exs to identify the element
            3. Keep record and organize all (identified) elements into an object, following MD's rules
            4. Render the content as I'm pleased
            
            Basically wonder if line-by-line reg-ex testing is the way to go, isn't it? Thanks in advance for any piece of advice.
            
            ---
            
            UPDATE: Thank you all for saving me time and helping me come to my senses on this daunting task! Will likely adopt a package and yet try to learn as much as possible along the way.
            
            [Top answer 1 of 8 27 markdown is complex enough that you shouldn't expect regular expressions and reading line by line to be enough. A simple linebreak in a .md file doesn't cut a paragraph nor become a new line within a paragraph either. marked.js has an implementation https://github.com/markedjs/marked/tree/master/src](ref:58:https://www.reddit.com/r/typescript/comments/1l5p78s/how_to_parse_markdown_content_without_external/)[2 of 8 4 If you want to learn parsing properly, I'd highly recommend learning recursive descent parsers. Dmitry Soshnikov has a few free videos on his channel that you can check out, but you'd need to get it on udemy for the whole thing (it's extremely worth it, but wait for a sale.) Here's the first one on YouTube . The first 3 are there, so you can get an idea whether it might work for you. I built a parser 4 years ago without knowing any of this stuff and it sucked. It worked, but only barely and was very flimsy and spaghetti-like. The newer ones I've made are much more robust and organised.](ref:59:https://www.reddit.com/r/typescript/comments/1l5p78s/how_to_parse_markdown_content_without_external/)[![🌐](https://imgs.search.brave.com/4GC3-rYHWX63dnj4xVlMYZZiJJAs3qFMdTptbkJgHhQ/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvM2UzMjYzYTQ5/ODA2MTRkNDAyNWZk/OGQxZjU5OTBlY2Q0/MzQ2MzBlOGNjYTQ1/MGVlMGUyNjBlMWQ0/ZDZiYWZiNC9tYXJr/ZWQuanMub3JnLw) Js <cite>marked.js.org</cite> Marked Documentation](ref:60:https://marked.js.org/)a low-level markdown compiler for parsing markdown without caching or blocking for long periods of time.**[![🌐](https://imgs.search.brave.com/moGa_G8aeO7G5-QgrHQ4NOqJx94aOIZuxLEF0F61E8k/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvNjExY2Y4NTll/N2EyYTA5ZGYyYWQ3/N2RkOTg2YWE5ZGU0/OGJkNWEyM2ZiN2Jl/Zjg2NDRmM2IwOTcw/YzI3NTAzMC9wYXJz/ZWRvd24ub3JnLw) Parsedown <cite>parsedown.org</cite> Better Markdown Parser in PHP](ref:61:https://parsedown.org/)Fast and extensible Markdown parser in PHP. It supports GitHub Flavored Markdown and it adheres to CommonMark.[![🌐](https://imgs.search.brave.com/OTm9KfFc4ocdMFlkKw_l5-dKGKJUjmiggr0fNxpN6Us/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvMDQwMzY5ZmNj/OThhY2MyOWNlOGM1/ZGEzZmEwYjYyZjQy/NzU2M2FjOGMwYTRj/OWYxZmIxNTZiYmY5/OWUyMTljYS9tYXJr/ZG93bi1pdC5naXRo/dWIuaW8v) Markdown-it <cite>markdown-it.github.io</cite> markdown-it demo](ref:62:https://markdown-it.github.io/)markdown-it demo · xhtmlOut · breaks · linkify · typographer · highlight · CommonMark strict · clearpermalink · --- __Advertisement :)__ - __[pica](https://nodeca.github.io/pica/demo/)__ - high quality and fast image resize in browser. - __[babelfish](https://github.com/nodeca/babelfish/)__ ...[![🌐](https://imgs.search.brave.com/aLUK7zq9GywdaD4PsA4oks1xU0H2d5M9LbpK1_vFa0c/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvOWI4YzJiZTgx/ZWVhZGM3NjE0ZTM1/MTI4MDIzNWI4ZTk5/ZjUzYTgxZTJmMjY3/ZmQ3OTVhNzkxNzUy/NTVjZDk1Mi9kZXZl/bG9wZXJzLmxsYW1h/aW5kZXguYWkv) Llamaindex <cite>developers.llamaindex.ai › python › framework-api-reference › node_parsers › markdown</cite> Markdown - LlamaIndex](ref:63:https://developers.llamaindex.ai/python/framework-api-reference/node_parsers/markdown/)**Splits a markdown document into Text Nodes and Index Nodes corresponding to embedded objects** (e.g. tables). Source code in llama-index-core/llama_index/core/node_parser/relational/markdown_element.py[![🌐](https://imgs.search.brave.com/V4sitL69KR7AduPXfIVGCXIOgFm8-li-R0ZwScqDXmo/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvNjNjODNlMGQ0/ZGM0ZjY5NmQwNmM5/NDZiMDJlNTljOWRj/YWExZjViNTAxMTYy/OGY3YTA3ODRjNGUy/MjlmM2M5OC9kb2Nz/LnJzLw) Rust <cite>docs.rs › markdown-parser</cite> markdown_parser - Rust](ref:64:https://docs.rs/markdown-parser)use markdown_parser::{ read_file, Error }; fn main() -> Result<(), Error> { let md = read_file("$PATH.md")?; let content = md.content(); println!("{}", content); Ok(()) }
            
            <header>
            
            Related queries
            
            </header>
            
            [**markdown-dokument**](ref:65:/search?q=markdown-dokument&source=relatedQueries)[markdown **beispiel**](ref:66:/search?q=markdown%20beispiel&source=relatedQueries)[markdown parser **online**](ref:67:/search?q=markdown%20parser%20online&source=relatedQueries)[markdown **to****pdf**](ref:68:/search?q=markdown%20to%20pdf&source=relatedQueries)[markdown parser **react**](ref:69:/search?q=markdown%20parser%20react&source=relatedQueries)[markdown parser **java**](ref:70:/search?q=markdown%20parser%20java&source=relatedQueries)[markdown parser **python**](ref:71:/search?q=markdown%20parser%20python&source=relatedQueries)[markdown parser **example**](ref:72:/search?q=markdown%20parser%20example&source=relatedQueries)[Next](ref:73:/search?q=markdown%20parser&offset=1&spellcheck=0)
            
            </main>
            </main>
            <footer>
            <main>
            
            ###### Resources
            
            [Brave Search Premium](ref:74:https://account.brave.com/?intent=checkout&product=search) [Brave Search help](ref:75:/help) [Transparency report](ref:76:https://brave.com/transparency/) [Report a security issue](ref:77:https://hackerone.com/brave) [Status](ref:78:https://status.brave.app/)
            
            ###### Products
            
            [Brave Browser](ref:79:https://brave.com/download/?mtm_source=brave-search&mtm_medium=searchfooter&mtm_campaign=brave-search&mtm_content=evergreen) [Brave Search](ref:80:https://search.brave.com) [Brave Wallet](ref:81:https://brave.com/wallet/?mtm_source=brave-search&mtm_medium=searchfooter&mtm_campaign=brave-search&mtm_content=evergreen) [Brave Talk](ref:82:https://talk.brave.com/?mtm_source=brave-search&mtm_medium=searchfooter&mtm_campaign=brave-search&mtm_content=evergreen) [Brave Firewall + VPN](ref:83:https://brave.com/firewall-vpn/?mtm_source=brave-search&mtm_medium=searchfooter&mtm_campaign=brave-search&mtm_content=evergreen) [Brave Playlist](ref:84:https://brave.com/playlist/?mtm_source=brave-search&mtm_medium=searchfooter&mtm_campaign=brave-search&mtm_content=evergreen) [Brave News](ref:85:https://brave.com/brave-news/?mtm_source=brave-search&mtm_medium=searchfooter&mtm_campaign=brave-search&mtm_content=evergreen) [Brave Rewards](ref:86:https://brave.com/brave-rewards/?mtm_source=brave-search&mtm_medium=searchfooter&mtm_campaign=brave-search&mtm_content=evergreen) [Brave Search API](ref:87:https://brave.com/search/api/?mtm_source=brave-search&mtm_medium=searchfooter&mtm_campaign=brave-search&mtm_content=evergreen) [Brave Ads](ref:88:https://brave.com/brave-ads/?mtm_source=brave-search&mtm_medium=searchfooter&mtm_campaign=brave-search&mtm_content=evergreen)
            
            ###### Policies
            
            [Privacy Policy](ref:89:/help/privacy-policy) [Terms of Use](ref:90:https://brave.com/terms-of-use/)
            
            </main>
            </footer>
            
            Brave Search uses private usage metrics to estimate overall activity and performance. You can turn off this option in [Settings](ref:91:/settings). [Learn more.](ref:92:/help/usage-metrics)
            
            <button aria-label="Close" ref="93">
            </button>
        """.trimIndent()
    }

    @Test
    fun `should round-trip the rendered Markdown with the captured divergences`() = runTest {
        // given — the Markdown the pipeline produces for the Brave SERP dump
        val markdown = dumpFlow(DumpFixtures.serpBrave).transformHtmlToMarkdown().renderMarkdown()

        // when
        val reparsed = flowOf(markdown).parse()

        // then — re-parsing does NOT reproduce the pipeline Markdown verbatim. The
        // residual divergence here is an unsupported inline `<![CDATA[` + icon glyph wedged into a nested
        // image-in-link whose URL re-nests on each pass — a known-hard divergence
        // (inline CDATA and image-in-link are both unsupported). NOTE: this is the
        // *residual* divergence; the unbounded emphasis-delimiter growth that used
        // to dominate this dump is fixed (see EmphasisDelimiterRoundTripTest).
        // Asserting the exact diff pins it down: any new round-trip difference (a
        // regression) changes this message and fails the test.
        try {
            reparsed.renderMarkdown() sameAs markdown
            error("expected the round-trip to diverge")
        } catch (e: AssertionError) {
            e.message sameAs $$"""
                --- expected
                +++ actual
                @@ -11,7 +11,7 @@
                 
                 <header id="site-header">
                 
                -[![Brave logo](https://cdn.search.brave.com/serp/v3/_app/immutable/assets/brave-logo-dark.5D16vJCY.svg)](ref:1:/)
                +[![Brave logo](https://cdn.search.brave.com/serp/v3/*app/immutable/assets/brave-logo-dark.5D16vJCY.svg)*](ref:1:/)
                 
                 <form id="searchform" action="/search" method="GET" target="_self">
                 <input id="searchbox" type="text" name="q" placeholder="Ask anything, find anything…" aria-label="Search" aria-haspopup="false" ref="2">
                @@ -36,15 +36,13 @@
                 - [Videos](ref:12:/videos?q=markdown+parser&source=web)
                 - [Goggles](ref:13:/goggles?q=markdown+parser&source=web)
                 
                -
                 <button type="button" aria-label="Filters" ref="14">
                 </button>
                -
                 </nav>
                 <main id="search-page">
                 <main>
                 
                -[![🌐](https://imgs.search.brave.com/DNNWccTD9Mtp2P0DINzOrm_jD-e9lJ9a0neUmmf_Hls/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvMmJiNGQxYmNh/ZGJmZjUxMzQ3ZWRi/YjRmZDg2NGJjNTRm/ODlmNWQ5Njg3NzI2/MDZiYzkwYWVlNWUz/NjgyYTAzNC9tYXJr/ZG93bmxpdmVwcmV2/aWV3LmNvbS8) Markdown Live Preview <cite>markdownlivepreview.com</cite> Markdown Live Preview](ref:15:https://markdownlivepreview.com/)You may be using [Markdown Live Preview](https://markdownlivepreview.[![🌐](https://imgs.search.brave.com/iGZu5NAmNyRvPlAPhQoHBLF3CDq5A0Ez0PrCoeRotUo/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvMmY2MTM4MTBk/NzVlNTIzMGQyYjMy/Y2M0N2M3NzAzMTgz/NmE0OGFjOTAyZjUx/M2Q2ZWFiZmY2NTRm/ODQ1Mjk2Ni9kaWxs/aW5nZXIuaW8v) Dillinger <cite>dillinger.io</cite> Markdown Editor — Online, Free, with Live Preview | Dillinger](ref:16:https://dillinger.io/)Free online Markdown editor with live preview. Write, format, and export Markdown to HTML or PDF — sync to GitHub, Dropbox & Google Drive. No signup.
                +[![🌐](https://imgs.search.brave.com/DNNWccTD9Mtp2P0DINzOrm_jD-e9lJ9a0neUmmf_Hls/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvMmJiNGQxYmNh/ZGJmZjUxMzQ3ZWRi/YjRmZDg2NGJjNTRm/ODlmNWQ5Njg3NzI2/MDZiYzkwYWVlNWUz/NjgyYTAzNC9tYXJr/ZG93bmxpdmVwcmV2/aWV3LmNvbS8) Markdown Live Preview <cite>markdownlivepreview.com</cite> Markdown Live Preview](ref:15:https://markdownlivepreview.com/)You may be using [Markdown Live Preview](https://markdownlivepreview.[![🌐]([https://imgs.search.brave.com/iGZu5NAmNyRvPlAPhQoHBLF3CDq5A0Ez0PrCoeRotUo/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvMmY2MTM4MTBk/NzVlNTIzMGQyYjMy/Y2M0N2M3NzAzMTgz/NmE0OGFjOTAyZjUx/M2Q2ZWFiZmY2NTRm/ODQ1Mjk2Ni9kaWxs/aW5nZXIuaW8v](https://imgs.search.brave.com/iGZu5NAmNyRvPlAPhQoHBLF3CDq5A0Ez0PrCoeRotUo/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvMmY2MTM4MTBk/NzVlNTIzMGQyYjMy/Y2M0N2M3NzAzMTgz/NmE0OGFjOTAyZjUx/M2Q2ZWFiZmY2NTRm/ODQ1Mjk2Ni9kaWxs/aW5nZXIuaW8v)) Dillinger <cite>dillinger.io</cite> Markdown Editor — Online, Free, with Live Preview | Dillinger](ref:16:https://dillinger.io/)Free online Markdown editor with live preview. Write, format, and export Markdown to HTML or PDF — sync to GitHub, Dropbox & Google Drive. No signup.
                 
                 <header>
                 
                @@ -89,7 +87,7 @@
                 <button ref="35">
                 </button>
                 
                -**Published**   May 13, 2026**Version**   0.1.3[](ref:36:https://www.npmjs.com/package/markdown-parser)[![🌐](https://imgs.search.brave.com/qnamV7kCUSH_wAMhKTiy2IIqVM1Oz8dbPrG310AT4RE/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvY2Y5NGQyMmMx/YjQyZDE2OGQ4Yzc1/NjkyMmJiMWYzZDIw/YjRlODY1NDJhYjAw/Mzc5ZDIyMTc5ZDZl/MTc5NWE2Ni9jaHJv/bWV3ZWJzdG9yZS5n/b29nbGUuY29tLw) Chrome Web Store <cite>chromewebstore.google.com › detail › markdown-viewer › ckkdlimhmcjmikdlpkmbgfkaikojcbjk</cite> Markdown Viewer - Chrome Web Store](ref:37:https://chromewebstore.google.com/detail/markdown-viewer/ckkdlimhmcjmikdlpkmbgfkaikojcbjk)✔ Secure by design ✔ Render local and remote file URLs ✔ Granular access to remote origins ✔ Multiple **markdown parser**s ✔ Full control over the compiler options ✔ 30+ Themes ✔ Custom theme support ✔ GitHub Flavored Markdown (GFM) ✔ Auto reload on file change ✔ Syntax highlighted code blocks ✔ Table of Contents (ToC) ✔ MathJax formulas ✔ Mermaid diagrams ✔ Convert emoji shortnames ✔ Remember scroll position ✔ Markdown Content-Type detection ✔ Configurable Markdown file path detection ✔ Settings synchronization ✔ Raw and rendered markdown views ✔ Free and Open Source ✚ Full Documentation ✔ https://github.com/simov/markdown-viewer[](ref:38:https://chromewebstore.google.com/detail/markdown-viewer/ckkdlimhmcjmikdlpkmbgfkaikojcbjk)
                +**Published**   May 13, 2026**Version**   0.1.3[](ref:36:https://www.npmjs.com/package/markdown-parser)[![🌐](https://imgs.search.brave.com/qnamV7kCUSH_wAMhKTiy2IIqVM1Oz8dbPrG310AT4RE/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvY2Y5NGQyMmMx/YjQyZDE2OGQ4Yzc1/NjkyMmJiMWYzZDIw/YjRlODY1NDJhYjAw/Mzc5ZDIyMTc5ZDZl/MTc5NWE2Ni9jaHJv/bWV3ZWJzdG9yZS5n/b29nbGUuY29tLw) Chrome Web Store <cite>chromewebstore.google.com › detail › markdown-viewer › ckkdlimhmcjmikdlpkmbgfkaikojcbjk</cite> Markdown Viewer - Chrome Web Store](ref:37:https://chromewebstore.google.com/detail/markdown-viewer/ckkdlimhmcjmikdlpkmbgfkaikojcbjk)✔ Secure by design ✔ Render local and remote file URLs ✔ Granular access to remote origins ✔ Multiple **markdown parser**s ✔ Full control over the compiler options ✔ 30+ Themes ✔ Custom theme support ✔ GitHub Flavored Markdown (GFM) ✔ Auto reload on file change ✔ Syntax highlighted code blocks ✔ Table of Contents (ToC) ✔ MathJax formulas ✔ Mermaid diagrams ✔ Convert emoji shortnames ✔ Remember scroll position ✔ Markdown Content-Type detection ✔ Configurable Markdown file path detection ✔ Settings synchronization ✔ Raw and rendered markdown views ✔ Free and Open Source ✚ Full Documentation ✔ [https://github.com/simov/markdown-viewer](https://github.com/simov/markdown-viewer)[](ref:38:https://chromewebstore.google.com/detail/markdown-viewer/ckkdlimhmcjmikdlpkmbgfkaikojcbjk)
                 
                 <header>
                 
                @@ -101,7 +99,7 @@
                 
                 <a href="https://stackoverflow.com/questions/605434/how-would-you-go-about-parsing-markdown" ref="51">
                 
                -Top answer 1 of 10 73 
                +Top answer 1 of 10 73
                 
                 The only markdown implementation I know of, that uses an actual parser, is Jon MacFarleane’s peg-markdown. Its parser is based on a Parsing Expression Grammar parser generator called peg.
                 
                @@ -110,7 +108,7 @@
                 </a>
                 <a href="https://stackoverflow.com/questions/605434/how-would-you-go-about-parsing-markdown" ref="52">
                 
                -2 of 10 18 
                +2 of 10 18
                 
                 I released a new parser-based Markdown Java implementation last week, called pegdown. pegdown uses a PEG parser to first build an abstract syntax tree, which is subsequently written out to HTML. As such it is quite clean and much easier to read, maintain and extend than a regex based approach. The PEG grammar is based on John MacFarlanes C implementation "peg-markdown".
                 
                @@ -118,7 +116,7 @@
                 
                 </a>
                 
                -[![🌐](https://imgs.search.brave.com/cHR1z3j4S0EzHoVTIbP5tZsbiY9M4eBD76hYfqp1q38/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvMDQzODgyMDJm/ZTI4YmM1MzY0YmFk/NDA2ZDhiZGM0ZDNi/MjIxMWY5MzkzMjRm/NzNlNGQ3MjY3YmNh/MzA5MGIyNy9nZXRr/aXJieS5jb20v) Kirby CMS <cite>getkirby.com › docs › reference › plugins › components › markdown</cite> Markdown parser | Kirby CMS](ref:53:https://getkirby.com/docs/reference/plugins/components/markdown)Kirby::plugin('my/markdown', [ 'components' => [ 'markdown' => function (Kirby $kirby, string $text = null, array $options = [], bool $inline = false) { return YourMarkdownParser::parse($text); } ] ]);[](ref:54:https://getkirby.com/docs/reference/plugins/components/markdown)[![🌐](https://imgs.search.brave.com/129PsExJCizx2_pC-7e4exnT1C0gWE4PUinIWjrXr-4/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvMzg1ZTZiOGFm/M2NhY2JjMmE1NmJl/ZTRlODIwNDVhZWIy/OWRjZDgzYjYyYjcw/NjhmNzQzMWM0NDBk/Y2U3MGIzMi93d3cu/bnBtanMuY29tLw) npm <cite>npmjs.com › package › stream-markdown-parser</cite> stream-markdown-parser - npm](ref:55:https://www.npmjs.com/package/stream-markdown-parser)16 May 2026 - For declared custom tags, content and raw are kept close to the source payload (including quote- or whitespace-sensitive data such as JSON), while children still comes from normal inline Markdown parsing. Heuristic function to detect if content looks like mathematical notation. ... Find the matching closing delimiter in a string, handling nested pairs. ... Reuse parser instances: cache getMarkdown() results per worker/request to avoid re-registering plugins.
                +[![🌐](https://imgs.search.brave.com/cHR1z3j4S0EzHoVTIbP5tZsbiY9M4eBD76hYfqp1q38/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvMDQzODgyMDJm/ZTI4YmM1MzY0YmFk/NDA2ZDhiZGM0ZDNi/MjIxMWY5MzkzMjRm/NzNlNGQ3MjY3YmNh/MzA5MGIyNy9nZXRr/aXJieS5jb20v) Kirby CMS <cite>getkirby.com › docs › reference › plugins › components › markdown</cite> Markdown parser | Kirby CMS](ref:53:https://getkirby.com/docs/reference/plugins/components/markdown)Kirby::plugin('my/markdown', [ 'components' => [ 'markdown' => function (Kirby <math>kirby, string </math>text = null, array <math>options = [], bool </math>inline = false) { return YourMarkdownParser::parse(<math>text); } ] ]);[](ref:54:https://getkirby.com/docs/reference/plugins/components/markdown)[![🌐](https://imgs.search.brave.com/129PsExJCizx2_pC-7e4exnT1C0gWE4PUinIWjrXr-4/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvMzg1ZTZiOGFm/M2NhY2JjMmE1NmJl/ZTRlODIwNDVhZWIy/OWRjZDgzYjYyYjcw/NjhmNzQzMWM0NDBk/Y2U3MGIzMi93d3cu/bnBtanMuY29tLw) npm <cite>npmjs.com › package › stream-markdown-parser</cite> stream-markdown-parser - npm](ref:55:https://www.npmjs.com/package/stream-markdown-parser)16 May 2026 - For declared custom tags, content and raw are kept close to the source payload (including quote- or whitespace-sensitive data such as JSON), while children still comes from normal inline Markdown parsing. Heuristic function to detect if content looks like mathematical notation. ... Find the matching closing delimiter in a string, handling nested pairs. ... Reuse parser instances: cache getMarkdown() results per worker/request to avoid re-registering plugins.</math>
                 
                 » npm install stream-markdown-parser
                 
                @@ -140,7 +138,7 @@
                 
                 UPDATE: Thank you all for saving me time and helping me come to my senses on this daunting task! Will likely adopt a package and yet try to learn as much as possible along the way.
                 
                -[Top answer 1 of 8 27 markdown is complex enough that you shouldn't expect regular expressions and reading line by line to be enough. A simple linebreak in a .md file doesn't cut a paragraph nor become a new line within a paragraph either. marked.js has an implementation https://github.com/markedjs/marked/tree/master/src](ref:58:https://www.reddit.com/r/typescript/comments/1l5p78s/how_to_parse_markdown_content_without_external/)[2 of 8 4 If you want to learn parsing properly, I'd highly recommend learning recursive descent parsers. Dmitry Soshnikov has a few free videos on his channel that you can check out, but you'd need to get it on udemy for the whole thing (it's extremely worth it, but wait for a sale.) Here's the first one on YouTube . The first 3 are there, so you can get an idea whether it might work for you. I built a parser 4 years ago without knowing any of this stuff and it sucked. It worked, but only barely and was very flimsy and spaghetti-like. The newer ones I've made are much more robust and organised.](ref:59:https://www.reddit.com/r/typescript/comments/1l5p78s/how_to_parse_markdown_content_without_external/)[![🌐](https://imgs.search.brave.com/4GC3-rYHWX63dnj4xVlMYZZiJJAs3qFMdTptbkJgHhQ/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvM2UzMjYzYTQ5/ODA2MTRkNDAyNWZk/OGQxZjU5OTBlY2Q0/MzQ2MzBlOGNjYTQ1/MGVlMGUyNjBlMWQ0/ZDZiYWZiNC9tYXJr/ZWQuanMub3JnLw) Js <cite>marked.js.org</cite> Marked Documentation](ref:60:https://marked.js.org/)a low-level markdown compiler for parsing markdown without caching or blocking for long periods of time.**[![🌐](https://imgs.search.brave.com/moGa_G8aeO7G5-QgrHQ4NOqJx94aOIZuxLEF0F61E8k/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvNjExY2Y4NTll/N2EyYTA5ZGYyYWQ3/N2RkOTg2YWE5ZGU0/OGJkNWEyM2ZiN2Jl/Zjg2NDRmM2IwOTcw/YzI3NTAzMC9wYXJz/ZWRvd24ub3JnLw) Parsedown <cite>parsedown.org</cite> Better Markdown Parser in PHP](ref:61:https://parsedown.org/)Fast and extensible Markdown parser in PHP. It supports GitHub Flavored Markdown and it adheres to CommonMark.[![🌐](https://imgs.search.brave.com/OTm9KfFc4ocdMFlkKw_l5-dKGKJUjmiggr0fNxpN6Us/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvMDQwMzY5ZmNj/OThhY2MyOWNlOGM1/ZGEzZmEwYjYyZjQy/NzU2M2FjOGMwYTRj/OWYxZmIxNTZiYmY5/OWUyMTljYS9tYXJr/ZG93bi1pdC5naXRo/dWIuaW8v) Markdown-it <cite>markdown-it.github.io</cite> markdown-it demo](ref:62:https://markdown-it.github.io/)markdown-it demo · xhtmlOut · breaks · linkify · typographer · highlight · CommonMark strict · clearpermalink · --- __Advertisement :)__ - __[pica](https://nodeca.github.io/pica/demo/)__ - high quality and fast image resize in browser. - __[babelfish](https://github.com/nodeca/babelfish/)__ ...[![🌐](https://imgs.search.brave.com/aLUK7zq9GywdaD4PsA4oks1xU0H2d5M9LbpK1_vFa0c/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvOWI4YzJiZTgx/ZWVhZGM3NjE0ZTM1/MTI4MDIzNWI4ZTk5/ZjUzYTgxZTJmMjY3/ZmQ3OTVhNzkxNzUy/NTVjZDk1Mi9kZXZl/bG9wZXJzLmxsYW1h/aW5kZXguYWkv) Llamaindex <cite>developers.llamaindex.ai › python › framework-api-reference › node_parsers › markdown</cite> Markdown - LlamaIndex](ref:63:https://developers.llamaindex.ai/python/framework-api-reference/node_parsers/markdown/)**Splits a markdown document into Text Nodes and Index Nodes corresponding to embedded objects** (e.g. tables). Source code in llama-index-core/llama_index/core/node_parser/relational/markdown_element.py[![🌐](https://imgs.search.brave.com/V4sitL69KR7AduPXfIVGCXIOgFm8-li-R0ZwScqDXmo/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvNjNjODNlMGQ0/ZGM0ZjY5NmQwNmM5/NDZiMDJlNTljOWRj/YWExZjViNTAxMTYy/OGY3YTA3ODRjNGUy/MjlmM2M5OC9kb2Nz/LnJzLw) Rust <cite>docs.rs › markdown-parser</cite> markdown_parser - Rust](ref:64:https://docs.rs/markdown-parser)use markdown_parser::{ read_file, Error }; fn main() -> Result<(), Error> { let md = read_file("$PATH.md")?; let content = md.content(); println!("{}", content); Ok(()) }
                +[Top answer 1 of 8 27 markdown is complex enough that you shouldn't expect regular expressions and reading line by line to be enough. A simple linebreak in a .md file doesn't cut a paragraph nor become a new line within a paragraph either. marked.js has an implementation https://github.com/markedjs/marked/tree/master/src](ref:58:https://www.reddit.com/r/typescript/comments/1l5p78s/how_to_parse_markdown_content_without_external/)[2 of 8 4 If you want to learn parsing properly, I'd highly recommend learning recursive descent parsers. Dmitry Soshnikov has a few free videos on his channel that you can check out, but you'd need to get it on udemy for the whole thing (it's extremely worth it, but wait for a sale.) Here's the first one on YouTube . The first 3 are there, so you can get an idea whether it might work for you. I built a parser 4 years ago without knowing any of this stuff and it sucked. It worked, but only barely and was very flimsy and spaghetti-like. The newer ones I've made are much more robust and organised.](ref:59:https://www.reddit.com/r/typescript/comments/1l5p78s/how_to_parse_markdown_content_without_external/)[![🌐](https://imgs.search.brave.com/4GC3-rYHWX63dnj4xVlMYZZiJJAs3qFMdTptbkJgHhQ/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvM2UzMjYzYTQ5/ODA2MTRkNDAyNWZk/OGQxZjU5OTBlY2Q0/MzQ2MzBlOGNjYTQ1/MGVlMGUyNjBlMWQ0/ZDZiYWZiNC9tYXJr/ZWQuanMub3JnLw) Js <cite>marked.js.org</cite> Marked Documentation](ref:60:https://marked.js.org/)a low-level markdown compiler for parsing markdown without caching or blocking for long periods of time.**[![🌐](https://imgs.search.brave.com/moGa_G8aeO7G5-QgrHQ4NOqJx94aOIZuxLEF0F61E8k/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvNjExY2Y4NTll/N2EyYTA5ZGYyYWQ3/N2RkOTg2YWE5ZGU0/OGJkNWEyM2ZiN2Jl/Zjg2NDRmM2IwOTcw/YzI3NTAzMC9wYXJz/ZWRvd24ub3JnLw) Parsedown <cite>parsedown.org</cite> Better Markdown Parser in PHP](ref:61:https://parsedown.org/)Fast and extensible Markdown parser in PHP. It supports GitHub Flavored Markdown and it adheres to CommonMark.[![🌐](https://imgs.search.brave.com/OTm9KfFc4ocdMFlkKw_l5-dKGKJUjmiggr0fNxpN6Us/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvMDQwMzY5ZmNj/OThhY2MyOWNlOGM1/ZGEzZmEwYjYyZjQy/NzU2M2FjOGMwYTRj/OWYxZmIxNTZiYmY5/OWUyMTljYS9tYXJr/ZG93bi1pdC5naXRo/dWIuaW8v) Markdown-it <cite>markdown-it.github.io</cite> markdown-it demo](ref:62:https://markdown-it.github.io/)markdown-it demo · xhtmlOut · breaks · linkify · typographer · highlight · CommonMark strict · clearpermalink · --- __Advertisement :)** - **[pica](https://nodeca.github.io/pica/demo/)** - high quality and fast image resize in browser. - **[babelfish](https://github.com/nodeca/babelfish/)** ...[![🌐](https://imgs.search.brave.com/aLUK7zq9GywdaD4PsA4oks1xU0H2d5M9LbpK1_vFa0c/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvOWI4YzJiZTgx/ZWVhZGM3NjE0ZTM1/MTI4MDIzNWI4ZTk5/ZjUzYTgxZTJmMjY3/ZmQ3OTVhNzkxNzUy/NTVjZDk1Mi9kZXZl/bG9wZXJzLmxsYW1h/aW5kZXguYWkv) Llamaindex <cite>developers.llamaindex.ai › python › framework-api-reference › node_parsers › markdown</cite> Markdown - LlamaIndex](ref:63:https://developers.llamaindex.ai/python/framework-api-reference/node_parsers/markdown/)**Splits a markdown document into Text Nodes and Index Nodes corresponding to embedded objects** (e.g. tables). Source code in llama-index-core/llama_index/core/node_parser/relational/markdown_element.py[![🌐](https://imgs.search.brave.com/V4sitL69KR7AduPXfIVGCXIOgFm8-li-R0ZwScqDXmo/rs:fit:32:32:1:0/g:ce/aHR0cDovL2Zhdmlj/b25zLnNlYXJjaC5i/cmF2ZS5jb20vaWNv/bnMvNjNjODNlMGQ0/ZGM0ZjY5NmQwNmM5/NDZiMDJlNTljOWRj/YWExZjViNTAxMTYy/OGY3YTA3ODRjNGUy/MjlmM2M5OC9kb2Nz/LnJzLw) Rust <cite>docs.rs › markdown-parser</cite> markdown_parser - Rust](ref:64:https://docs.rs/markdown-parser)use markdown_parser::{ read_file, Error }; fn main() -> Result<(), Error> { let md = read_file("<math>PATH.md")?; let content = md.content(); println!("{}", content); Ok(()) }</math>
                 
                 <header>
                 
                @@ -148,7 +146,7 @@
                 
                 </header>
                 
                -[**markdown-dokument**](ref:65:/search?q=markdown-dokument&source=relatedQueries)[markdown **beispiel**](ref:66:/search?q=markdown%20beispiel&source=relatedQueries)[markdown parser **online**](ref:67:/search?q=markdown%20parser%20online&source=relatedQueries)[markdown **to****pdf**](ref:68:/search?q=markdown%20to%20pdf&source=relatedQueries)[markdown parser **react**](ref:69:/search?q=markdown%20parser%20react&source=relatedQueries)[markdown parser **java**](ref:70:/search?q=markdown%20parser%20java&source=relatedQueries)[markdown parser **python**](ref:71:/search?q=markdown%20parser%20python&source=relatedQueries)[markdown parser **example**](ref:72:/search?q=markdown%20parser%20example&source=relatedQueries)[Next](ref:73:/search?q=markdown%20parser&offset=1&spellcheck=0)
                +[**markdown-dokument**](ref:65:/search?q=markdown-dokument&source=relatedQueries)[markdown **beispiel**](ref:66:/search?q=markdown%20beispiel&source=relatedQueries)[markdown parser **online**](ref:67:/search?q=markdown%20parser%20online&source=relatedQueries)[markdown **to\*\*\*\*pdf**](ref:68:/search?q=markdown%20to%20pdf&source=relatedQueries)[markdown parser **react**](ref:69:/search?q=markdown%20parser%20react&source=relatedQueries)[markdown parser **java**](ref:70:/search?q=markdown%20parser%20java&source=relatedQueries)[markdown parser **python**](ref:71:/search?q=markdown%20parser%20python&source=relatedQueries)[markdown parser **example**](ref:72:/search?q=markdown%20parser%20example&source=relatedQueries)[Next](ref:73:/search?q=markdown%20parser&offset=1&spellcheck=0)
                 
                 </main>
                 </main>
                
            """.trimIndent()
        }
    }

}
