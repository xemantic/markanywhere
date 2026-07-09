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

class SerpBingTest {

    @Test
    fun `should convert captured serp-bing DOM dump to Markdown`() = runTest {
        // given
        val events = dumpFlow(DumpFixtures.serpBing)

        // when
        val markdown = events.transformHtmlToMarkdown().renderMarkdown()

        // then
        markdown sameAs /* language=markdown */ """
            ---
            lang: en
            title: markdown parser - Search
            ---
            
            Microsoft and our third-party vendors use cookies and similar technologies to deliver, maintain and improve our services and ads. If you agree, we will use this data for ads personalisation and associated analytics. You can select ‘Accept’ to consent to these uses, ‘Reject’ to decline these uses or click on ‘More options’ to review your options. You can change your selection under ‘Manage Cookie Preferences’ at the bottom of this page.  [Privacy Statement](ref:1:https://go.microsoft.com/fwlink/?LinkId=521839)[Accept](ref:2:javascript: void(0))[Reject](ref:3:javascript: void(0))[More Options](ref:4:javascript: void(0))
            
            <header id="b_header">
            
            [Skip to content](ref:5:#)[Accessibility Feedback](ref:6:#)
            
            <form id="sb_form" action="/search">
            <a href="/?FORM=Z9FD1" ref="7">
            
            #
            
            </a>
            <section>
            
            <div ref="8"></div><input id="sb_form_q" type="search" name="q" value="markdown parser" aria-label="Enter your search here – Search suggestions will show as you type" ref="9">
            <div ref="10"></div><div ref="11"></div>
            
            </section>
            </form>
            <aside id="id_h" aria-label="Account Rewards and Preferences">
            
            [Rewards](ref:12:javascript:void(0))[](ref:13:javascript:void(0))
            
            </aside>
            <nav aria-label="Search Filter">
            
            - [All](ref:14:/?scope=web&FORM=HDRSC1)
            - [Search](ref:15:/copilotsearch?q=markdown+parser&FORM=CSSCOP)
            - [Shopping](ref:16:https://www.bing.com/ck/a?!&&p=17228e241a3bd2b30448c348dd9b2e7c0ba48b207d9d17c85fe93a59d42b3d9fJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3Nob3A_cT1tYXJrZG93bitwYXJzZXImRk9STT1TSE9QVEI&ntb=1)
            - [Images](ref:17:https://www.bing.com/ck/a?!&&p=5f4431ccde5d74035daca1ad028a5d46c0a67a04c4831b66d48f583867002a16JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L2ltYWdlcy9zZWFyY2g_cT1tYXJrZG93bitwYXJzZXImRk9STT1IRFJTQzM&ntb=1)
            - [Videos](ref:18:https://www.bing.com/ck/a?!&&p=0c558586636c86bbff54b118631836b831410ea5cc6fb4c9dfa79dc3aa156c74JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1tYXJrZG93bitwYXJzZXImRk9STT1IRFJTQzQ&ntb=1)
            - [Maps](ref:19:https://www.bing.com/ck/a?!&&p=e0a9124531708e760878e34880adc9372481a1c296f097fbb6db3e735aa28adcJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L21hcHM_cT1tYXJrZG93bitwYXJzZXImRk9STT1IRFJTQzY&ntb=1)
            - [Copilot](ref:20:https://www.bing.com/ck/a?!&&p=1cb17cb936ade6534ee81c477ef6d5dc88e4077c53ae3af8078918791e16e149JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L2NoYXQ_cHJvbXB0PW1hcmtkb3duK3BhcnNlciZzZW5kcXVlcnk9MSZGT1JNPVNDQ09EWA&ntb=1)
            - [More](ref:21:javascript:void(0);)
            
            </nav>
            </header>
            <main aria-label="Search Results">
            
            
            1. <section aria-label="Copilot Search">
            
               - <div ref="22">
                 Here are **three solid, production‑ready markdown parser options**, along with concise guidance to help you choose the right one. If you want runnable example code in a specific language, I can provide it.
            
                 **1) Marked (JavaScript)**
            
                 - Fast, lightweight markdown parser and compiler.
                 - Works in browser, Node.js, or CLI.
                 - Supports CommonMark + GitHub‑flavored Markdown.
            
                 
                 ##### Javascript
                 ```javascript
                 // Example: Using Marked in Node.js import { marked } from "marked"; try { const md = "# Hello **Markdown**!"; const html = marked.parse(md); console.log(html); } catch (err) { console.error("Failed to parse markdown:", err); }
                 ```
                 **2) markdown-it (JavaScript)**
            
                 - Extremely customizable.
                 - Plugin ecosystem (emoji, footnotes, containers).
                 - Very strict CommonMark compliance.
            
                 
                 ##### Javascript
                 ```javascript
                 // Example: Using markdown-it import MarkdownIt from "markdown-it"; try { const md = new MarkdownIt(); const html = md.render("Some *markdown* text."); console.log(html); } catch (err) { console.error("Markdown parsing error:", err); }
                 ```
                 **3) Python-Markdown (Python)**
            
                 - Standard and widely used in the Python ecosystem.
                 - Supports extensions like tables, fenced code blocks, etc.
            
                 
                 ##### Python
                 ```python
                 # Example: Using python-markdown import markdown try: md_text = "# Title\nSome *markdown* content." html = markdown.markdown(md_text, extensions=["extra"]) print(html) except Exception as e: print("Markdown parsing failed:", e)
                 ```
                 If you want, I can provide:
            
                 - A **custom markdown parser from scratch** in any language
                 - A **fast streaming markdown parser**
                 - A **browser‑only** solution
                 - Help integrating a parser with a framework (React, Vue, Node, Python, Go, Rust, etc.)
            
                 </div>
            
                 <textarea id="bCusTweakTextarea" placeholder="What would you like to adjust?" rows="1" ref="30">
                 </textarea>
                 <fieldset>
                 </fieldset>
                 <fieldset>
                 </fieldset>
            
               </section>
            
               - [Markdown formatting examples](ref:36:https://www.bing.com/ck/a?!&&p=3ba8cefd7205b501e745e25f7f049517ada07cf7538aa5fab4299b7671e60d8aJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L2NvcGlsb3RzZWFyY2g_cT1NYXJrZG93bitmb3JtYXR0aW5nK2V4YW1wbGVzJmZvcm09Q1NTQU5T&ntb=1)
               - [Free Markdown Viewer Tools](ref:38:https://www.bing.com/ck/a?!&&p=5d5b2471cfe0a0f74b991e1974ca34b3dd989d30e9dcf060a437c96bbca79d32JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L2NvcGlsb3RzZWFyY2g_cT1GcmVlK01hcmtkb3duK1ZpZXdlcitUb29scyZmb3JtPUNTU0FOUw&ntb=1)
               - [GitHub Flavored Markdown Preview](ref:40:https://www.bing.com/ck/a?!&&p=fdfa4bf1f969b8454f69267dc2f52359c8dbccf52dd5093da93bc264cb02cca9JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L2NvcGlsb3RzZWFyY2g_cT1HaXRIdWIrRmxhdm9yZWQrTWFya2Rvd24rUHJldmlldyZmb3JtPUNTU0FOUw&ntb=1)
            
            1. ## [Writing a Markdown Parser](ref:43:https://www.bing.com/ck/a?!&&p=cec1496be9f283fd141c78e2f6ae7ea4e96b1cf7fb7236f7afaab88f4ac8d71eJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9kZXYudG8va2F3YWxqYWluL2J1aWxkaW5nLW15LW93bi1tYXJrZG93bi1wYXJzZXItYS1kZXZlbG9wZXJzLWpvdXJuZXktM2IyNg&ntb=1)
            
               ^[1](ref:44:https://www.bing.com/ck/a?!&&p=cec1496be9f283fd141c78e2f6ae7ea4e96b1cf7fb7236f7afaab88f4ac8d71eJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9kZXYudG8va2F3YWxqYWluL2J1aWxkaW5nLW15LW93bi1tYXJrZG93bi1wYXJzZXItYS1kZXZlbG9wZXJzLWpvdXJuZXktM2IyNg&ntb=1)^^[2](ref:45:https://www.bing.com/ck/a?!&&p=b3cdc161e5b75f40074777c93795699b6363c66b4b04fd767d7c17eee1c1a808JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9naXRodWIuY29tL21hcmtlZGpzL21hcmtlZA&ntb=1)^^[3](ref:46:https://www.bing.com/ck/a?!&&p=e07186ef73027de600d9478626445ee37e8d6528fb43e08e1e42737e9ac90615JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9zdGFja292ZXJmbG93LmNvbS9xdWVzdGlvbnMvNjA1NDM0L2hvdy13b3VsZC15b3UtZ28tYWJvdXQtcGFyc2luZy1tYXJrZG93bg&ntb=1)^Creating a Markdown parser involves converting Markdown syntax into structured formats like HTML. Below is a step-by-step guide to building your own Markdown parser.
            
               1\. Understand Markdown Syntax
            
               Markdown is a lightweight markup language with simple syntax for formatting text. Key elements include:
               - **Headings**: *# Heading*
               - **Bold/Italic**: *\*\*bold\*\**, *\*italic\**
               - **Lists**: *- Item* or *1. Item*
               - **Links**: *[text](url)*
               - **Code Blocks**: *\`\`\`code\`\`\`*
               Familiarize yourself with the [CommonMark specification](ref:47:https://www.bing.com/ck/a?!&&p=e07186ef73027de600d9478626445ee37e8d6528fb43e08e1e42737e9ac90615JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9zdGFja292ZXJmbG93LmNvbS9xdWVzdGlvbnMvNjA1NDM0L2hvdy13b3VsZC15b3UtZ28tYWJvdXQtcGFyc2luZy1tYXJrZG93bg&ntb=1) for consistent parsing.
            
               2\. Design the Parser
            
               A Markdown parser typically involves:
               1. **Lexing**: Tokenizing the input text into meaningful components (e.g., headings, lists).
               2. **Parsing**: Converting tokens into a structured representation like an Abstract Syntax Tree (AST).
               3. **Rendering**: Transforming the AST into the desired output format (e.g., HTML).
               3\. Implementation Steps
            
               Step 1: Tokenize Input
            
               Use regular expressions to identify Markdown patterns:
            
               import re
            
               def tokenize(markdown):tokens = []lines = markdown.split("\n")for line in lines:if re.match(r"^# ", line):tokens.append(("heading", line[2:]))elif re.match(r"^- ", line):tokens.append(("list_item", line[2:]))elif re.match(r"^\*\*(.+?)\*\*", line):tokens.append(("bold", re.findall(r"\*\*(.+?)\*\*", line)[0]))else:tokens.append(("text", line))return tokens
            
               <button id="devmag_cpy_17_57D5EE" aria-label="Copy" ref="48">
            
               ![Copy](data:image/svg+xml,%EF%BB%BF%3Csvg%20width%3D%2218%22%20height%3D%2219%22%20viewBox%3D%220%200%2018%2019%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0D%0A%20%20%20%20%3Cpath%20d%3D%22M4.11363%203.54174L4.11153%205.16645V13.2054C4.11153%2014.579%205.19925%2015.6926%206.54102%2015.6926L12.982%2015.6929C12.751%2016.3615%2012.1281%2016.8406%2011.3958%2016.8406H6.54102C4.57998%2016.8406%202.99023%2015.2131%202.99023%2013.2054V5.16645C2.99023%204.41591%203.45927%203.77752%204.11363%203.54174ZM13.2688%201.53125C14.1977%201.53125%2014.9508%202.30219%2014.9508%203.25319V13.2022C14.9508%2014.1531%2014.1977%2014.9241%2013.2688%2014.9241H6.54102C5.6121%2014.9241%204.85907%2014.1531%204.85907%2013.2022V3.25319C4.85907%202.30219%205.6121%201.53125%206.54102%201.53125H13.2688ZM13.2688%202.67921H6.54102C6.23138%202.67921%205.98037%202.93619%205.98037%203.25319V13.2022C5.98037%2013.5192%206.23138%2013.7761%206.54102%2013.7761H13.2688C13.5784%2013.7761%2013.8295%2013.5192%2013.8295%2013.2022V3.25319C13.8295%202.93619%2013.5784%202.67921%2013.2688%202.67921Z%22%20fill%3D%22%23767676%22%20%2F%3E%0D%0A%3C%2Fsvg%3E)
            
               </button>
            
               Step 2: Parse Tokens
            
               Convert tokens into an AST:
            
               def parse(tokens):ast = []for token in tokens:if token[0] == "heading":ast.append({"type": "heading", "content": token[1]})elif token[0] == "list_item":ast.append({"type": "list_item", "content": token[1]})elif token[0] == "bold":ast.append({"type": "bold", "content": token[1]})else:ast.append({"type": "text", "content": token[1]})return ast
            
               <button id="devmag_cpy_22_57DA21" aria-label="Copy" ref="49">
            
               ![Copy](data:image/svg+xml,%EF%BB%BF%3Csvg%20width%3D%2218%22%20height%3D%2219%22%20viewBox%3D%220%200%2018%2019%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0D%0A%20%20%20%20%3Cpath%20d%3D%22M4.11363%203.54174L4.11153%205.16645V13.2054C4.11153%2014.579%205.19925%2015.6926%206.54102%2015.6926L12.982%2015.6929C12.751%2016.3615%2012.1281%2016.8406%2011.3958%2016.8406H6.54102C4.57998%2016.8406%202.99023%2015.2131%202.99023%2013.2054V5.16645C2.99023%204.41591%203.45927%203.77752%204.11363%203.54174ZM13.2688%201.53125C14.1977%201.53125%2014.9508%202.30219%2014.9508%203.25319V13.2022C14.9508%2014.1531%2014.1977%2014.9241%2013.2688%2014.9241H6.54102C5.6121%2014.9241%204.85907%2014.1531%204.85907%2013.2022V3.25319C4.85907%202.30219%205.6121%201.53125%206.54102%201.53125H13.2688ZM13.2688%202.67921H6.54102C6.23138%202.67921%205.98037%202.93619%205.98037%203.25319V13.2022C5.98037%2013.5192%206.23138%2013.7761%206.54102%2013.7761H13.2688C13.5784%2013.7761%2013.8295%2013.5192%2013.8295%2013.2022V3.25319C13.8295%202.93619%2013.5784%202.67921%2013.2688%202.67921Z%22%20fill%3D%22%23767676%22%20%2F%3E%0D%0A%3C%2Fsvg%3E)
            
               </button>
            
               Step 3: Render Output
            
               Generate HTML from the AST:
            
               def render(ast):html = ""for node in ast:if node["type"] == "heading":html += f"<h1>{node['content']}</h1>"elif node["type"] == "list_item":html += f"<li>{node['content']}</li>"elif node["type"] == "bold":html += f"<b>{node['content']}</b>"else:html += f"<p>{node['content']}</p>"return html
            
               <button id="devmag_cpy_27_57DCA3" aria-label="Copy" ref="50">
            
               ![Copy](data:image/svg+xml,%EF%BB%BF%3Csvg%20width%3D%2218%22%20height%3D%2219%22%20viewBox%3D%220%200%2018%2019%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0D%0A%20%20%20%20%3Cpath%20d%3D%22M4.11363%203.54174L4.11153%205.16645V13.2054C4.11153%2014.579%205.19925%2015.6926%206.54102%2015.6926L12.982%2015.6929C12.751%2016.3615%2012.1281%2016.8406%2011.3958%2016.8406H6.54102C4.57998%2016.8406%202.99023%2015.2131%202.99023%2013.2054V5.16645C2.99023%204.41591%203.45927%203.77752%204.11363%203.54174ZM13.2688%201.53125C14.1977%201.53125%2014.9508%202.30219%2014.9508%203.25319V13.2022C14.9508%2014.1531%2014.1977%2014.9241%2013.2688%2014.9241H6.54102C5.6121%2014.9241%204.85907%2014.1531%204.85907%2013.2022V3.25319C4.85907%202.30219%205.6121%201.53125%206.54102%201.53125H13.2688ZM13.2688%202.67921H6.54102C6.23138%202.67921%205.98037%202.93619%205.98037%203.25319V13.2022C5.98037%2013.5192%206.23138%2013.7761%206.54102%2013.7761H13.2688C13.5784%2013.7761%2013.8295%2013.5192%2013.8295%2013.2022V3.25319C13.8295%202.93619%2013.5784%202.67921%2013.2688%202.67921Z%22%20fill%3D%22%23767676%22%20%2F%3E%0D%0A%3C%2Fsvg%3E)
            
               </button>
            
               4\. Test Your Parser
            
               Run your parser with sample Markdown input:
            
               markdown_text = "# Heading\n- List item\n**Bold text**"tokens = tokenize(markdown_text)ast = parse(tokens)html_output = render(ast)print(html_output)
            
               <button id="devmag_cpy_32_57DE9A" aria-label="Copy" ref="51">
            
               ![Copy](data:image/svg+xml,%EF%BB%BF%3Csvg%20width%3D%2218%22%20height%3D%2219%22%20viewBox%3D%220%200%2018%2019%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0D%0A%20%20%20%20%3Cpath%20d%3D%22M4.11363%203.54174L4.11153%205.16645V13.2054C4.11153%2014.579%205.19925%2015.6926%206.54102%2015.6926L12.982%2015.6929C12.751%2016.3615%2012.1281%2016.8406%2011.3958%2016.8406H6.54102C4.57998%2016.8406%202.99023%2015.2131%202.99023%2013.2054V5.16645C2.99023%204.41591%203.45927%203.77752%204.11363%203.54174ZM13.2688%201.53125C14.1977%201.53125%2014.9508%202.30219%2014.9508%203.25319V13.2022C14.9508%2014.1531%2014.1977%2014.9241%2013.2688%2014.9241H6.54102C5.6121%2014.9241%204.85907%2014.1531%204.85907%2013.2022V3.25319C4.85907%202.30219%205.6121%201.53125%206.54102%201.53125H13.2688ZM13.2688%202.67921H6.54102C6.23138%202.67921%205.98037%202.93619%205.98037%203.25319V13.2022C5.98037%2013.5192%206.23138%2013.7761%206.54102%2013.7761H13.2688C13.5784%2013.7761%2013.8295%2013.5192%2013.8295%2013.2022V3.25319C13.8295%202.93619%2013.5784%202.67921%2013.2688%202.67921Z%22%20fill%3D%22%23767676%22%20%2F%3E%0D%0A%3C%2Fsvg%3E)
            
               </button>
            
               Expected Output:
            
               <h1>Heading</h1><li>List item</li><b>Bold text</b>
            
               <button id="devmag_cpy_36_57DF72" aria-label="Copy" ref="52">
            
               ![Copy](data:image/svg+xml,%EF%BB%BF%3Csvg%20width%3D%2218%22%20height%3D%2219%22%20viewBox%3D%220%200%2018%2019%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0D%0A%20%20%20%20%3Cpath%20d%3D%22M4.11363%203.54174L4.11153%205.16645V13.2054C4.11153%2014.579%205.19925%2015.6926%206.54102%2015.6926L12.982%2015.6929C12.751%2016.3615%2012.1281%2016.8406%2011.3958%2016.8406H6.54102C4.57998%2016.8406%202.99023%2015.2131%202.99023%2013.2054V5.16645C2.99023%204.41591%203.45927%203.77752%204.11363%203.54174ZM13.2688%201.53125C14.1977%201.53125%2014.9508%202.30219%2014.9508%203.25319V13.2022C14.9508%2014.1531%2014.1977%2014.9241%2013.2688%2014.9241H6.54102C5.6121%2014.9241%204.85907%2014.1531%204.85907%2013.2022V3.25319C4.85907%202.30219%205.6121%201.53125%206.54102%201.53125H13.2688ZM13.2688%202.67921H6.54102C6.23138%202.67921%205.98037%202.93619%205.98037%203.25319V13.2022C5.98037%2013.5192%206.23138%2013.7761%206.54102%2013.7761H13.2688C13.5784%2013.7761%2013.8295%2013.5192%2013.8295%2013.2022V3.25319C13.8295%202.93619%2013.5784%202.67921%2013.2688%202.67921Z%22%20fill%3D%22%23767676%22%20%2F%3E%0D%0A%3C%2Fsvg%3E)
            
               </button>
            
               5\. Best Practices
               - Handle edge cases like nested lists or mixed inline styles.
               - Use libraries like [Marked.js](ref:53:https://www.bing.com/ck/a?!&&p=b3cdc161e5b75f40074777c93795699b6363c66b4b04fd767d7c17eee1c1a808JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9naXRodWIuY29tL21hcmtlZGpzL21hcmtlZA&ntb=1) or [CommonMark](ref:54:https://www.bing.com/ck/a?!&&p=e07186ef73027de600d9478626445ee37e8d6528fb43e08e1e42737e9ac90615JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9zdGFja292ZXJmbG93LmNvbS9xdWVzdGlvbnMvNjA1NDM0L2hvdy13b3VsZC15b3UtZ28tYWJvdXQtcGFyc2luZy1tYXJrZG93bg&ntb=1) for inspiration.
               - Optimize performance for large documents by minimizing regex passes.
               By following these steps, you can build a functional and extensible Markdown parser tailored to your needs!
            
               [See more](ref:55:)feedback[](ref:56:javascript:void(0))[](ref:57:javascript:void(0))
            2. [](ref:58:/images/search?view=detailV2&ccid=04zp9wcQ&id=6A22855BEA6F58327634BE5A47834F166D75B2E4&thid=OIP.04zp9wcQmM7KyyIPLcBC6wHaEK&mediaurl=https://markdownviewer.org/og-image.webp&q=markdown+parser&ck=CF2CBDB76DBCEB0322F9DFA579F49F63&idpp=rc&expw=1200&exph=675&form=rc2idp)[![Global web icon](https://th.bing.com/th/id/ODF.twAY8QtltJN42PpQk3L_AQ?w=32&h=32&qlt=90&pcl=fffffa&o=6&pid=1.2) markdownviewer.org<div ref="60"><cite>https://markdownviewer.org</cite></div>](ref:59:https://www.bing.com/ck/a?!&&p=3859f4031f1e1230773d666ec45504f6465fdaa6aebb31c2777002387b0e0715JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9tYXJrZG93bnZpZXdlci5vcmcv&ntb=1)
               ## [Markdown Viewer — Online Markdown Editor with Live …](ref:61:https://www.bing.com/ck/a?!&&p=3859f4031f1e1230773d666ec45504f6465fdaa6aebb31c2777002387b0e0715JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9tYXJrZG93bnZpZXdlci5vcmcv&ntb=1)
            
               Open the page, start writing, and see your markdown rendered beautifully in real time. Everything you need to write, preview, and share markdown documents — …
            3. [](ref:62:/images/search?view=detailV2&ccid=dv9aBKdQ&id=D1B82E7D84356EFAA0D4FBEFDF1C6272E584D921&thid=OIP.dv9aBKdQ_Q1yIWrCcFIEcgHaDt&mediaurl=https://opengraph.githubassets.com/faa35b9a34e8981cd689bf21ee768a1e89380f4265b97168162156c713170c3a/markedjs/marked&q=markdown+parser&ck=E836A8F29CB73266791BDE159B93DABE&idpp=rc&expw=1200&exph=600&form=rc2idp)[![Global web icon](https://th.bing.com/th/id/ODF.bYAvaN8MCaSZfP0o7q_Z_w?w=32&h=32&qlt=91&pcl=fffffa&o=6&pid=1.2) Github<div ref="64"><cite>https://github.com › markedjs › marked</cite>[](ref:65:#)</div>](ref:63:https://www.bing.com/ck/a?!&&p=b3cdc161e5b75f40074777c93795699b6363c66b4b04fd767d7c17eee1c1a808JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9naXRodWIuY29tL21hcmtlZGpzL21hcmtlZA&ntb=1)
               ## [GitHub - markedjs/marked: A markdown parser and …](ref:66:https://www.bing.com/ck/a?!&&p=b3cdc161e5b75f40074777c93795699b6363c66b4b04fd767d7c17eee1c1a808JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9naXRodWIuY29tL21hcmtlZGpzL21hcmtlZA&ntb=1)
            
               Marked is a low-level compiler for parsing markdown without caching or blocking. It supports all markdown features from the supported flavors and specifications, …
            4. ## [Videos of Markdown Parser](ref:67:https://www.bing.com/ck/a?!&&p=1e80cf930e78cf5d7243abc3754ccafc4534574c3cd34537c9ced8380d6590a8JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1tYXJrZG93bitwYXJzZXImcXB2dD1tYXJrZG93bitwYXJzZXImRk9STT1WRFJF&ntb=1)
            
               <div ref="68"></div>
               - [![](https://th.bing.com/th?q=Jupyter+Notebook&w=30&h=30&c=1&rs=1&qlt=90&o=6&pid=RelatedEntity&mkt=de-DE&cc=DE&setlang=en&adlt=moderate&t=1) Jupyter Notebook](ref:69:https://www.bing.com/ck/a?!&&p=3b91a8bdf8a459a9761dfa46e8d5d0b9453bc08465bbafe202f27898424a044bJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1KdXB5dGVyK05vdGVib29rJiZGT1JNPVZBUlNRUA&ntb=1)
               - [![](https://th.bing.com/th?q=R+Markdown&w=30&h=30&c=1&rs=1&qlt=90&o=6&pid=RelatedEntity&mkt=de-DE&cc=DE&setlang=en&adlt=moderate&t=1) R Markdown](ref:70:https://www.bing.com/ck/a?!&&p=e6884f555ea368e4484f7dd174d79b5642c756531ed45ee7b6a3ff4630fd5f75JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1SK01hcmtkb3duJiZGT1JNPVZBUlNRUA&ntb=1)
               - [![](https://th.bing.com/th?q=Learn+Mark+Down&w=30&h=30&c=1&rs=1&qlt=90&o=6&pid=RelatedEntity&mkt=de-DE&cc=DE&setlang=en&adlt=moderate&t=1) Learn Mark Down](ref:71:https://www.bing.com/ck/a?!&&p=3edafb752d084de38bf771ccefc82cf6cc032ecb1773061dc46374c5f0985aa0JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1MZWFybitNYXJrK0Rvd24mJkZPUk09VkFSU1FQ&ntb=1)
               - [![](https://th.bing.com/th?q=Mark+Down+Online+Converter&w=30&h=30&c=1&rs=1&qlt=90&o=6&pid=RelatedEntity&mkt=de-DE&cc=DE&setlang=en&adlt=moderate&t=1) Online Converter](ref:72:https://www.bing.com/ck/a?!&&p=8f23c404875cef39da58ddd1cde9055e14c6ec8029438532f0822d6f46af411bJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1PbmxpbmUrQ29udmVydGVyJiZGT1JNPVZBUlNRUA&ntb=1)
               - [![](https://th.bing.com/th?q=Markdown+Editor&w=30&h=30&c=1&rs=1&qlt=90&o=6&pid=RelatedEntity&mkt=de-DE&cc=DE&setlang=en&adlt=moderate&t=1) Markdown Editor](ref:73:https://www.bing.com/ck/a?!&&p=5932f7d7e91fd4ba704088d52a7ee12c8a731159cf76f0691efe977995f83c19JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1NYXJrZG93bitFZGl0b3ImJkZPUk09VkFSU1FQ&ntb=1)
               - [![](https://th.bing.com/th?q=HTML&w=30&h=30&c=1&rs=1&qlt=90&o=6&pid=RelatedEntity&mkt=de-DE&cc=DE&setlang=en&adlt=moderate&t=1) HTML](ref:74:https://www.bing.com/ck/a?!&&p=e84763d9e69c4291e8c2c4ded504d2d5d8d49231c2d4216ae0c219c1e6bd6c4bJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1IVE1MJiZGT1JNPVZBUlNRUA&ntb=1)
               - [![](https://th.bing.com/th?q=Markdown+to+HTML&w=30&h=30&c=1&rs=1&qlt=90&o=6&pid=RelatedEntity&mkt=de-DE&cc=DE&setlang=en&adlt=moderate&t=1) Markdown to HTML](ref:75:https://www.bing.com/ck/a?!&&p=8864446fbe7b8198d88cf40cd3fa38d4b25de53a5338375bf365a710f158880aJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1NYXJrZG93bit0bytIVE1MJiZGT1JNPVZBUlNRUA&ntb=1)
               - [![](https://th.bing.com/th?q=GitHub&w=30&h=30&c=1&rs=1&qlt=90&o=6&pid=RelatedEntity&mkt=de-DE&cc=DE&setlang=en&adlt=moderate&t=1) GitHub](ref:76:https://www.bing.com/ck/a?!&&p=615f4d792ee10b10140f19ffe0272464f479e66a826573b90bec1ba921cce11eJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1HaXRIdWImJkZPUk09VkFSU1FQ&ntb=1)
               - [![](https://th.bing.com/th?q=Mark+Down&w=30&h=30&c=1&rs=1&qlt=90&o=6&pid=RelatedEntity&mkt=de-DE&cc=DE&setlang=en&adlt=moderate&t=1) Mark Down](ref:77:https://www.bing.com/ck/a?!&&p=e23b04b4f78016c9db9ac0ea6c35174c43f9c2104a3b12d3a8533cfbe8f4d81bJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1NYXJrK0Rvd24mJkZPUk09VkFSU1FQ&ntb=1)
               - [![](https://th.bing.com/th?q=Mark+Down+vs+HTML&w=30&h=30&c=1&rs=1&qlt=90&o=6&pid=RelatedEntity&mkt=de-DE&cc=DE&setlang=en&adlt=moderate&t=1) Mark Down vs HTML](ref:78:https://www.bing.com/ck/a?!&&p=9dec2459a572eb21f72a28bdc7c70464f6f16517b764d426d655824271c81f5cJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1NYXJrK0Rvd24rdnMrSFRNTCYmRk9STT1WQVJTUVA&ntb=1)
               - [![](https://th.bing.com/th?q=WordPress&w=30&h=30&c=1&rs=1&qlt=90&o=6&pid=RelatedEntity&mkt=de-DE&cc=DE&setlang=en&adlt=moderate&t=1) WordPress](ref:79:https://www.bing.com/ck/a?!&&p=66297ec4fb2dadf93aa15401cbb67a4c08c990399939eb01ea2819c4400da3ffJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1Xb3JkUHJlc3MmJkZPUk09VkFSU1FQ&ntb=1)
               - [![](https://th.bing.com/th?q=JavaScript&w=30&h=30&c=1&rs=1&qlt=90&o=6&pid=RelatedEntity&mkt=de-DE&cc=DE&setlang=en&adlt=moderate&t=1) JavaScript](ref:80:https://www.bing.com/ck/a?!&&p=f05a1572e503478cf83f90ec88945136169c1bb35e189d6b6dc393f9bf225baeJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1KYXZhU2NyaXB0JiZGT1JNPVZBUlNRUA&ntb=1)
               - [![](https://th.bing.com/th?q=R+Studio&w=30&h=30&c=1&rs=1&qlt=90&o=6&pid=RelatedEntity&mkt=de-DE&cc=DE&setlang=en&adlt=moderate&t=1) R Studio](ref:81:https://www.bing.com/ck/a?!&&p=ccbd685c89f00162f180cc6522f5b63e1bf39f75b5736f3252b5207dfd236a35JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1SK1N0dWRpbyYmRk9STT1WQVJTUVA&ntb=1)
               - [![](https://th.bing.com/th?q=CSS&w=30&h=30&c=1&rs=1&qlt=90&o=6&pid=RelatedEntity&mkt=de-DE&cc=DE&setlang=en&adlt=moderate&t=1) CSS](ref:82:https://www.bing.com/ck/a?!&&p=5efb6ecdfdf9881614ac33946d2bbaea26c150b2d7f6e1ac59c3eec56a015f14JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1DU1MmJkZPUk09VkFSU1FQ&ntb=1)
               - [![](https://th.bing.com/th?q=Markdown+Syntax&w=30&h=30&c=1&rs=1&qlt=90&o=6&pid=RelatedEntity&mkt=de-DE&cc=DE&setlang=en&adlt=moderate&t=1) Markdown Syntax](ref:83:https://www.bing.com/ck/a?!&&p=9f1a2f7c30dcbe4d2edb8060c1f8e44cc1937d63d9aa029576345e999dab36cfJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1NYXJrZG93bitTeW50YXgmJkZPUk09VkFSU1FQ&ntb=1)
               - [![](https://th.bing.com/th?q=Mark+Down+Examples&w=30&h=30&c=1&rs=1&qlt=90&o=6&pid=RelatedEntity&mkt=de-DE&cc=DE&setlang=en&adlt=moderate&t=1) Examples](ref:84:https://www.bing.com/ck/a?!&&p=22033c753d20a93f32efb6a04e7fb668c9cd247a26e6f2abe5764165db8e30e3JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1FeGFtcGxlcyYmRk9STT1WQVJTUVA&ntb=1)
               - [![](https://th.bing.com/th?q=How+to+Use+Mark+Down&w=30&h=30&c=1&rs=1&qlt=90&o=6&pid=RelatedEntity&mkt=de-DE&cc=DE&setlang=en&adlt=moderate&t=1) How to Use](ref:85:https://www.bing.com/ck/a?!&&p=a2214cf7834ae9ddd77200d2706066c8f2482b2b9dba23b1073304913c7057c5JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1Ib3crdG8rVXNlJiZGT1JNPVZBUlNRUA&ntb=1)
               - [![](https://th.bing.com/th?q=Mark+Down+Cheat+Sheet&w=30&h=30&c=1&rs=1&qlt=90&o=6&pid=RelatedEntity&mkt=de-DE&cc=DE&setlang=en&adlt=moderate&t=1) Cheat Sheet](ref:86:https://www.bing.com/ck/a?!&&p=2a7dc2222a6ded0a1bf9dc93da33bcdcfb52b70e250f14a756713eb4cbf07579JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1DaGVhdCtTaGVldCYmRk9STT1WQVJTUVA&ntb=1)
               - [![](https://th.bing.com/th?q=Notion&w=30&h=30&c=1&rs=1&qlt=90&o=6&pid=RelatedEntity&mkt=de-DE&cc=DE&setlang=en&adlt=moderate&t=1) Notion](ref:87:https://www.bing.com/ck/a?!&&p=180fadaf565584cd3b25c23f55110adb3a8ff931c085253df7f3deeb1790e77eJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1Ob3Rpb24mJkZPUk09VkFSU1FQ&ntb=1)
               [![Build Your Own Markdown Parser From Scratch (Tokenizer)](https://th.bing.com/th/id/OVP.qK2437vy2iVXmC8FIglc8QEsDh?w=233&h=131&c=8&rs=1&qlt=90&o=6&pid=1.7)11:08 Build Your Own **Markdown** **Parser** From Scratch (Tokenizer) ![Video source site](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABgAAAAYCAYAAADgdz34AAABRklEQVR4Ae2UT0rDQBjFX7QICtaoIOimAReiUNuV696jmx5A8AalZ/AIpbdwIT2B6wqlWYkuWgnBP7jo+D7SkcmiM4kd6KYPfjNDeHlfJt8kwEYOBXqhgENOd+SGnJKQ7JFjsr3k/jmZkg/yQn7IE7lncAwjvEImRHlCskKzQMdjuOZWsrcWNS7hX+dmgQurNQzxD52YBQ6s1maTb3UCtNsooTOzwI7THkXAYAD0+9narVyTR9aGtVoqpyRRqtdzNXls7qCCMqpWgXodqNVsrrkZ/ImiimMe6g4wHLqciQx6BzOXGwn93S7QaBQJF6Uy6B28Wq3y1HKSZC6uL7PAG1wFymskg35FY/jX899Kfkxk6vE/JFlRrhwvXJEH8r5C8Iw8kmudGyzbH037nI6Q9UnWu8i+eDnf38iamC7saVDkJG60Fv0CnB4IzftPhuMAAAAASUVORK5CYII=)YouTubeNetcreed 968 views11 months ago](ref:88:https://www.bing.com/ck/a?!&&p=41385d47e8d057511456d3529940b3c3bcde274168d4cb1b345869c02ddbdc4bJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9yaXZlcnZpZXcvcmVsYXRlZHZpZGVvP3E9bWFya2Rvd24rcGFyc2VyJm1pZD1FRkFFMkVDOEFBOEFENzc0QTQ4N0VGQUUyRUM4QUE4QUQ3NzRBNDg3JmNodXJsPWh0dHBzJTNhJTJmJTJmd3d3LnlvdXR1YmUuY29tJTJmY2hhbm5lbCUyZlVDWkVaRU5MOVV2WVl4Nkx1Ui1yRzlFUSZGT1JNPVZJUkU&ntb=1)[![Build a Markdown Parser in Haskell with Megaparsec | Complete Tutorial](https://th.bing.com/th/id/OVP.Bu77KgbUnJvlj_OynIc8WAHgFo?w=233&h=131&c=8&rs=1&qlt=90&o=6&pid=1.7)1:03:57 Build a **Markdown** **Parser** in Haskell with Megaparsec | Complete Tutorial ![Video source site](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABgAAAAYCAYAAADgdz34AAABRklEQVR4Ae2UT0rDQBjFX7QICtaoIOimAReiUNuV696jmx5A8AalZ/AIpbdwIT2B6wqlWYkuWgnBP7jo+D7SkcmiM4kd6KYPfjNDeHlfJt8kwEYOBXqhgENOd+SGnJKQ7JFjsr3k/jmZkg/yQn7IE7lncAwjvEImRHlCskKzQMdjuOZWsrcWNS7hX+dmgQurNQzxD52YBQ6s1maTb3UCtNsooTOzwI7THkXAYAD0+9narVyTR9aGtVoqpyRRqtdzNXls7qCCMqpWgXodqNVsrrkZ/ImiimMe6g4wHLqciQx6BzOXGwn93S7QaBQJF6Uy6B28Wq3y1HKSZC6uL7PAG1wFymskg35FY/jX899Kfkxk6vE/JFlRrhwvXJEH8r5C8Iw8kmudGyzbH037nI6Q9UnWu8i+eDnf38iamC7saVDkJG60Fv0CnB4IzftPhuMAAAAASUVORK5CYII=)YouTubePurely Haskell 2K views1 Jun 2024](ref:89:https://www.bing.com/ck/a?!&&p=cb89144d235972e06fae016b5d0b2650cff08505f86265908937fa000dc33a1bJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9yaXZlcnZpZXcvcmVsYXRlZHZpZGVvP3E9bWFya2Rvd24rcGFyc2VyJm1pZD03NTg5Mzg0ODM3MzZGQkZBMzcxRjc1ODkzODQ4MzczNkZCRkEzNzFGJmNodXJsPWh0dHBzJTNhJTJmJTJmd3d3LnlvdXR1YmUuY29tJTJmY2hhbm5lbCUyZlVDeVBkYmV5QjVDTk1zVVhuVkRCUHlBQSZGT1JNPVZJUkU&ntb=1)[![dots.mocr: Document Parsing to Markdown and SVG](https://th.bing.com/th/id/OVP.7BmcCuO7EQvK4HkVnmGL9AHgFo?w=233&h=131&c=8&rs=1&qlt=90&o=6&pid=1.7)4:25 dots.mocr: Document Parsing to **Markdown** and SVG ![Video source site](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABgAAAAYCAYAAADgdz34AAABRklEQVR4Ae2UT0rDQBjFX7QICtaoIOimAReiUNuV696jmx5A8AalZ/AIpbdwIT2B6wqlWYkuWgnBP7jo+D7SkcmiM4kd6KYPfjNDeHlfJt8kwEYOBXqhgENOd+SGnJKQ7JFjsr3k/jmZkg/yQn7IE7lncAwjvEImRHlCskKzQMdjuOZWsrcWNS7hX+dmgQurNQzxD52YBQ6s1maTb3UCtNsooTOzwI7THkXAYAD0+9narVyTR9aGtVoqpyRRqtdzNXls7qCCMqpWgXodqNVsrrkZ/ImiimMe6g4wHLqciQx6BzOXGwn93S7QaBQJF6Uy6B28Wq3y1HKSZC6uL7PAG1wFymskg35FY/jX899Kfkxk6vE/JFlRrhwvXJEH8r5C8Iw8kmudGyzbH037nI6Q9UnWu8i+eDnf38iamC7saVDkJG60Fv0CnB4IzftPhuMAAAAASUVORK5CYII=)YouTubeAI Research Roundup 127 views3 months ago](ref:90:https://www.bing.com/ck/a?!&&p=17ce1687e45d09ca9046753b0216ae90ccb643014a0dd766f3939fc8d495de7eJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9yaXZlcnZpZXcvcmVsYXRlZHZpZGVvP3E9bWFya2Rvd24rcGFyc2VyJm1pZD02MUQ2RDM0MDNBM0FGNzY3QkQ1RTYxRDZEMzQwM0EzQUY3NjdCRDVFJmNodXJsPWh0dHBzJTNhJTJmJTJmd3d3LnlvdXR1YmUuY29tJTJmY2hhbm5lbCUyZlVDZ2FuNDBFd3hrd005b3QxcFY4bFRGQSZGT1JNPVZJUkU&ntb=1)
            5. [](ref:91:/images/search?view=detailV2&ccid=HkedMGjU&id=A3FE230F5B6A17750472607F8FB14BC536C684E6&thid=OIP.HkedMGjUUJqPHyEQ8i6s8gAAAA&mediaurl=https://markdownlivepreview.com/image/sample.webp&q=markdown+parser&ck=F771B10C5E97D1CF4C1EABCAC80E4EB2&idpp=rc&expw=200&exph=200&form=rc2idp)[![Global web icon](https://th.bing.com/th/id/ODF.GpAiJJMkVcw4eKdNuwTQMw?w=32&h=32&qlt=92&pcl=fffffa&o=6&pid=1.2) Markdown Live Preview<div ref="93"><cite>https://markdownlivepreview.com</cite></div>](ref:92:https://www.bing.com/ck/a?!&&p=ca74bd240693407427c1f2f075f73fdc2ce434adb48f182840d7a6711bc5797bJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9tYXJrZG93bmxpdmVwcmV2aWV3LmNvbS8&ntb=1)
               ## [Markdown Live Preview](ref:94:https://www.bing.com/ck/a?!&&p=ca74bd240693407427c1f2f075f73fdc2ce434adb48f182840d7a6711bc5797bJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9tYXJrZG93bmxpdmVwcmV2aWV3LmNvbS8&ntb=1)
            
               This is the online markdown editor with live preview.
            6. [![Global web icon](https://th.bing.com/th/id/ODF.yzOL4KSMcVlWmkGBkr9GjQ?w=32&h=32&qlt=93&pcl=fffffa&o=6&pid=1.2) markdownonline.org<div ref="96"><cite>https://markdownonline.org</cite></div>](ref:95:https://www.bing.com/ck/a?!&&p=9945a1d1ef2464c7b3fd2f9c56027439153e204aa139e7f9ff4b6e5a7018ef62JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9tYXJrZG93bm9ubGluZS5vcmcv&ntb=1)
               ## [Markdown Online - Viewer and Converter](ref:97:https://www.bing.com/ck/a?!&&p=9945a1d1ef2464c7b3fd2f9c56027439153e204aa139e7f9ff4b6e5a7018ef62JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9tYXJrZG93bm9ubGluZS5vcmcv&ntb=1)
            
               Free browser-based Markdown viewer and converter. Edit, preview, and convert Markdown to HTML, PDF, tables, and more with local-first privacy.
            7. [](ref:98:/images/search?view=detailV2&ccid=G+zlgf/x&id=04944F123DE7CEC178B13938144FACE9BFCF7AE1&thid=OIP.G-zlgf_xM0lgngLhM4V8TgHaDt&mediaurl=https://opengraph.githubassets.com/39ae70f459af178dc863d41b765afe1182de85d41c92211bf8d564a3eb49f1b0/markdown-it/markdown-it&q=markdown+parser&ck=CC9F2D159E8A426C34766A76D84BC984&idpp=rc&expw=1200&exph=600&form=rc2idp)[![Global web icon](https://th.bing.com/th/id/ODF.bYAvaN8MCaSZfP0o7q_Z_w?w=32&h=32&qlt=94&pcl=fffffa&o=6&pid=1.2) Github<div ref="100"><cite>https://github.com › markdown-it › markdown-it</cite>[](ref:101:#)</div>](ref:99:https://www.bing.com/ck/a?!&&p=867914eb2a962e978ebff851da7d5e104eb5edef55cbde4f06e2600897c10548JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9naXRodWIuY29tL21hcmtkb3duLWl0L21hcmtkb3duLWl0&ntb=1)
               ## [GitHub - markdown-it/markdown-it: Markdown parser, …](ref:102:https://www.bing.com/ck/a?!&&p=867914eb2a962e978ebff851da7d5e104eb5edef55cbde4f06e2600897c10548JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9naXRodWIuY29tL21hcmtkb3duLWl0L21hcmtkb3duLWl0&ntb=1)
            
               markdown-it is a Markdown parser that supports CommonMark spec, syntax extensions, plugins and high speed. It can be used in node.js or browser with …
            8. [](ref:103:/images/search?view=detailV2&ccid=FkHyzjVW&id=A9A6487F570752D4EAB2E23577820A25F5D049BE&thid=OIP.FkHyzjVW6sTm1zO5f6rHxgHaD4&mediaurl=https://dillinger.io/opengraph-image?72fdddf5ac7e0219&q=markdown+parser&ck=3308144C5AFAED9B5E98652364F0228C&idpp=rc&expw=1200&exph=630&form=rc2idp)[![Global web icon](https://th.bing.com/th/id/ODF.KzNcRzKHTcHwAS89WRwvVg?w=32&h=32&qlt=95&pcl=fffffa&o=6&pid=1.2) Dillinger<div ref="105"><cite>https://dillinger.io</cite></div>](ref:104:https://www.bing.com/ck/a?!&&p=c9846684349cf4f558df7ac9ac9a315f7df0c6541cc745c92e923a3e7fe89673JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9kaWxsaW5nZXIuaW8v&ntb=1)
               ## [Markdown Editor — Online, Free, with Live Preview](ref:106:https://www.bing.com/ck/a?!&&p=c9846684349cf4f558df7ac9ac9a315f7df0c6541cc745c92e923a3e7fe89673JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9kaWxsaW5nZXIuaW8v&ntb=1)
            
               Free online Markdown editor with live preview. Write, format, and export Markdown to HTML or PDF — sync to GitHub, Dropbox & Google Drive. No signup.
            9. [![Global web icon](https://th.bing.com/th/id/ODF.43D875aclGlnB4GFYbdonQ?w=32&h=32&qlt=96&pcl=fffffa&o=6&pid=1.2) npm<div ref="108"><cite>https://www.npmjs.com › package › marked</cite></div>](ref:107:https://www.bing.com/ck/a?!&&p=e9ae8f9d288ff8fa9880f2014bf2815b379fcac2e1a710da3415bcb7c8221749JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly93d3cubnBtanMuY29tL3BhY2thZ2UvbWFya2Vk&ntb=1)
               ## [marked - npm](https://www.bing.com/ck/a?!&&p=e9ae8f9d288ff8fa9880f2014bf2815b379fcac2e1a710da3415bcb7c8221749JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly93d3cubnBtanMuY29tL3BhY2thZ2UvbWFya2Vk&ntb=1)
            10. [![Global web icon](https://th.bing.com/th/id/ODF.Xs623j1krK2fpZc5rpp_jQ?w=32&h=32&qlt=97&pcl=fffffa&o=6&pid=1.2) pages.dev<div ref="110"><cite>https://markdownviewer.pages.dev</cite></div>](ref:109:https://www.bing.com/ck/a?!&&p=6bb8656a9853e7d7366ff663d9fe98e986d035ab668e05915d7fbb9e2550fb68JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9tYXJrZG93bnZpZXdlci5wYWdlcy5kZXYv&ntb=1)
                ## [Markdown Viewer](https://www.bing.com/ck/a?!&&p=6bb8656a9853e7d7366ff663d9fe98e986d035ab668e05915d7fbb9e2550fb68JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9tYXJrZG93bnZpZXdlci5wYWdlcy5kZXYv&ntb=1)
            11. [![Global web icon](https://th.bing.com/th/id/ODF.X49lZ0B5hZKCU_k0wLCxNg?w=32&h=32&qlt=98&pcl=fffffa&o=6&pid=1.2) ezparser.com<div ref="112"><cite>https://ezparser.com › markdown-parser</cite></div>](ref:111:https://www.bing.com/ck/a?!&&p=4ebe062ab95e4d58022721b3c872426330a29c4d125e57e23cdd4c518950aa83JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9lenBhcnNlci5jb20vbWFya2Rvd24tcGFyc2Vy&ntb=1)
                ## [Markdown Parser - Free Online Markdown Previewer & HTML …](https://www.bing.com/ck/a?!&&p=4ebe062ab95e4d58022721b3c872426330a29c4d125e57e23cdd4c518950aa83JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9lenBhcnNlci5jb20vbWFya2Rvd24tcGFyc2Vy&ntb=1)
            12. [![Global web icon](https://th.bing.com/th/id/ODF.43D875aclGlnB4GFYbdonQ?w=32&h=32&qlt=99&pcl=fffffa&o=6&pid=1.2) npm<div ref="114"><cite>https://www.npmjs.com › package › markdown-parser</cite></div>](ref:113:https://www.bing.com/ck/a?!&&p=1eeda170c124c44132f1e11ab9e61ff69dbf67c235dc01cdecb3da0734d0ced9JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly93d3cubnBtanMuY29tL3BhY2thZ2UvbWFya2Rvd24tcGFyc2Vy&ntb=1)
                ## [markdown-parser - npm](https://www.bing.com/ck/a?!&&p=1eeda170c124c44132f1e11ab9e61ff69dbf67c235dc01cdecb3da0734d0ced9JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly93d3cubnBtanMuY29tL3BhY2thZ2UvbWFya2Rvd24tcGFyc2Vy&ntb=1)
            13. ## Deep dive into **markdown parser**
                - [markdown parser **python**](https://www.bing.com/ck/a?!&&p=6b6e323323b306f551bc005733409cfe67eebc653bb27cd34ba05a593a2d18f6JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3NlYXJjaD9xPW1hcmtkb3duK3BhcnNlcitweXRob24mRk9STT1RU1JFMQ&ntb=1)
                - [markdown **viewer**](https://www.bing.com/ck/a?!&&p=5c5070f5857b8a654f7d380ecfaf4791e9c12886dadd2e6ee0ae4b27bc060341JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3NlYXJjaD9xPW1hcmtkb3duK3ZpZXdlciZGT1JNPVFTUkUy&ntb=1)
                - [markdown **visualizer online free**](https://www.bing.com/ck/a?!&&p=b66ce2fa8eb1b0f25a7f28ecc37b879e35ac9146aa4bebfe809b6ac94515e6d7JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3NlYXJjaD9xPW1hcmtkb3duK3Zpc3VhbGl6ZXIrb25saW5lK2ZyZWUmRk9STT1RU1JFMw&ntb=1)
                - [**generate** markdown **online**](https://www.bing.com/ck/a?!&&p=e266826dda004688cc001d5a71e2efd444b041f430129dea2026b79f1df991e1JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3NlYXJjaD9xPWdlbmVyYXRlK21hcmtkb3duK29ubGluZSZGT1JNPVFTUkU0&ntb=1)
                - [markdown **generator online**](https://www.bing.com/ck/a?!&&p=5736715c8fdd1a61a280748780d7a5cf163b2dc145e1e2d867032bb3e4bd6cb1JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3NlYXJjaD9xPW1hcmtkb3duK2dlbmVyYXRvcitvbmxpbmUmRk9STT1RU1JFNQ&ntb=1)
                - [markdown **visualizer online**](https://www.bing.com/ck/a?!&&p=e179e0ffb14a93778edb29fce17e64bb2b1106bca527fbe718351cc23691bbeeJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3NlYXJjaD9xPW1hcmtkb3duK3Zpc3VhbGl6ZXIrb25saW5lJkZPUk09UVNSRTY&ntb=1)
                - [markdown **maker online**](https://www.bing.com/ck/a?!&&p=c56e3c1f29eecb3792de8651dac232b19df5c9718f9c45ef6603c4eee17d2b18JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3NlYXJjaD9xPW1hcmtkb3duK21ha2VyK29ubGluZSZGT1JNPVFTUkU3&ntb=1)
                - [**validate** markdown **online**](https://www.bing.com/ck/a?!&&p=e4e4d4e1e7bceb7cd288c8fb7c43a8d95e45f3418995a325c9c5cdf8798639c4JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3NlYXJjaD9xPXZhbGlkYXRlK21hcmtkb3duK29ubGluZSZGT1JNPVFTUkU4&ntb=1)
            
            <aside aria-label="Additional Results">
            
            1. ## Deep dive into **markdown parser**
            
               [markdown parser **python**](ref:115:https://www.bing.com/ck/a?!&&p=a818b2ce2101dd88da34c01b32711dcc986eb3a062e6a7e32668a991da7ddbc7JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3NlYXJjaD9xPW1hcmtkb3duK3BhcnNlcitweXRob24mRk9STT1SNUZE&ntb=1)[markdown **viewer**](ref:116:https://www.bing.com/ck/a?!&&p=9fa664f4fcaa54c271fa4a68b47dde21e6a7d8506a5f61cd9e321ceba0989c94JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3NlYXJjaD9xPW1hcmtkb3duK3ZpZXdlciZGT1JNPVI1RkQx&ntb=1)[markdown **visualizer online free**](ref:117:https://www.bing.com/ck/a?!&&p=eb2a0cfbe76fa7f1aed5c4b11bf9460fa56d897ff811aaaaea66fb90e45c9a91JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3NlYXJjaD9xPW1hcmtkb3duK3Zpc3VhbGl6ZXIrb25saW5lK2ZyZWUmRk9STT1SNUZEMg&ntb=1)[**generate** markdown **online**](ref:118:https://www.bing.com/ck/a?!&&p=a06b4760f42e61e0e8805c5abf55528811855c92c146ec664179016d9493acf9JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3NlYXJjaD9xPWdlbmVyYXRlK21hcmtkb3duK29ubGluZSZGT1JNPVI1RkQz&ntb=1)[markdown **generator online**](ref:119:https://www.bing.com/ck/a?!&&p=f554a38cd851d271b6b0a714414f5db3a3d51c3df453269f063b3e930ab9b773JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3NlYXJjaD9xPW1hcmtkb3duK2dlbmVyYXRvcitvbmxpbmUmRk9STT1SNUZENA&ntb=1)[markdown **visualizer online**](ref:120:https://www.bing.com/ck/a?!&&p=5aa2e7acbfbb0edc8068d24cfa53ed75e3187f3088ab26941b7ebe4a149d649aJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3NlYXJjaD9xPW1hcmtkb3duK3Zpc3VhbGl6ZXIrb25saW5lJkZPUk09UjVGRDU&ntb=1)[markdown **maker online**](ref:121:https://www.bing.com/ck/a?!&&p=430c20d391647f7afcac0cfc0240f25eac8f90178a7c19820e811927abdd5978JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3NlYXJjaD9xPW1hcmtkb3duK21ha2VyK29ubGluZSZGT1JNPVI1RkQ2&ntb=1)[**validate** markdown **online**](ref:122:https://www.bing.com/ck/a?!&&p=519e36c20820fead0add32d6ea5b947e9aedbe91857d54f5c7cb27c69baa055eJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3NlYXJjaD9xPXZhbGlkYXRlK21hcmtkb3duK29ubGluZSZGT1JNPVI1RkQ3&ntb=1)<div ref="123"></div>
            
            </aside>
            </main>
            <footer id="b_footer">
            </footer>
            <section>
            <textarea id="b_copilot_composer_2" placeholder="Ask a follow-up " rows="1" ref="124">
            </textarea>
            </section>
            <button type="submit" aria-label="Send" ref="125">
            </button>
            <nav>
            
            [Markdown formatting examples](ref:126:/copilotsearch?q=Markdown+formatting+examples&form=CSBSUG)[Free Markdown Viewer Tools](ref:127:/copilotsearch?q=Free+Markdown+Viewer+Tools&form=CSBSUG)[GitHub Flavored Markdown Preview](ref:128:/copilotsearch?q=GitHub+Flavored+Markdown+Preview&form=CSBSUG)
            
            </nav>
            
            Here are three solid, production‑ready markdown parser options, along with concise guidance to help you choose the right one. If you want runnable example code in a specific language, I can provide it. 1) Marked (JavaScript) Fast, lightweight markdown parser and compiler. Works in browser, Node.js, or CLI. Supports CommonMark + GitHub‑flavored Markdown. // Example: Using Marked in Node.js import { marked } from "marked"; try { const md = "# Hello **Markdown**!"; const html = marked.parse(md); console.log(html); } catch (err) { console.error("Failed to parse markdown:", err); } 2) markdown-it (JavaScript) Extremely customizable. Plugin ecosystem (emoji, footnotes, containers). Very strict CommonMark compliance. // Example: Using markdown-it import MarkdownIt from "markdown-it"; try { const md = new MarkdownIt(); const html = md.render("Some *markdown* text."); console.log(html); } catch (err) { console.error("Markdown parsing error:", err); } 3) Python-Markdown (Python) Standard and widely used in the Python ecosystem. Supports extensions like tables, fenced code blocks, etc. # Example: Using python-markdown import markdown try: md_text = "# Title\nSome *markdown* content." html = markdown.markdown(md_text, extensions=["extra"]) print(html) except Exception as e: print("Markdown parsing failed:", e) If you want, I can provide: A custom markdown parser from scratch in any language A fast streaming markdown parser A browser‑only solution Help integrating a parser with a framework (React, Vue, Node, Python, Go, Rust, etc.)
        """.trimIndent()
    }

    @Test
    fun `should round-trip the rendered Markdown with the captured divergences`() = runTest {
        // given — the Markdown the pipeline produces for the Bing SERP dump
        val markdown = dumpFlow(DumpFixtures.serpBing).transformHtmlToMarkdown().renderMarkdown()

        // when
        val reparsed = flowOf(markdown).parse()

        // then — re-parsing does NOT reproduce the pipeline Markdown verbatim. The
        // residual divergence here is ATX headings nested inside list items: the forward pipeline emits them
        // inside the item, but on re-parse those indented `#` runs are no longer
        // recognised as block constructs, so the renderer escapes them (`\\##`),
        // plus a couple of blank-line / trailing-space shifts.
        // Asserting the exact diff pins it down: any new round-trip difference (a
        // regression) changes this message and fails the test.
        try {
            reparsed.renderMarkdown() sameAs markdown
            error("expected the round-trip to diverge")
        } catch (e: AssertionError) {
            e.message sameAs """
                --- expected
                +++ actual
                @@ -3,7 +3,7 @@
                 title: markdown parser - Search
                 ---
                 
                -Microsoft and our third-party vendors use cookies and similar technologies to deliver, maintain and improve our services and ads. If you agree, we will use this data for ads personalisation and associated analytics. You can select ‘Accept’ to consent to these uses, ‘Reject’ to decline these uses or click on ‘More options’ to review your options. You can change your selection under ‘Manage Cookie Preferences’ at the bottom of this page.  [Privacy Statement](ref:1:https://go.microsoft.com/fwlink/?LinkId=521839)[Accept](ref:2:javascript: void(0))[Reject](ref:3:javascript: void(0))[More Options](ref:4:javascript: void(0))
                +Microsoft and our third-party vendors use cookies and similar technologies to deliver, maintain and improve our services and ads. If you agree, we will use this data for ads personalisation and associated analytics. You can select ‘Accept’ to consent to these uses, ‘Reject’ to decline these uses or click on ‘More options’ to review your options. You can change your selection under ‘Manage Cookie Preferences’ at the bottom of this page. [Privacy Statement](ref:1:https://go.microsoft.com/fwlink/?LinkId=521839)[Accept](ref:2:javascript: void(0))[Reject](ref:3:javascript: void(0))[More Options](ref:4:javascript: void(0))
                 
                 <header id="b_header">
                 
                @@ -45,75 +45,93 @@
                 
                 1. <section aria-label="Copilot Search">
                 
                -   - <div ref="22">
                +   \- <div ref="22">
                +
                      Here are **three solid, production‑ready markdown parser options**, along with concise guidance to help you choose the right one. If you want runnable example code in a specific language, I can provide it.
                 
                      **1) Marked (JavaScript)**
                 
                      - Fast, lightweight markdown parser and compiler.
                +
                      - Works in browser, Node.js, or CLI.
                +
                      - Supports CommonMark + GitHub‑flavored Markdown.
                 
                -     
                      ##### Javascript
                +
                      ```javascript
                +
                      // Example: Using Marked in Node.js import { marked } from "marked"; try { const md = "# Hello **Markdown**!"; const html = marked.parse(md); console.log(html); } catch (err) { console.error("Failed to parse markdown:", err); }
                +
                      ```
                +
                      **2) markdown-it (JavaScript)**
                 
                      - Extremely customizable.
                +
                      - Plugin ecosystem (emoji, footnotes, containers).
                +
                      - Very strict CommonMark compliance.
                 
                -     
                      ##### Javascript
                +
                      ```javascript
                +
                      // Example: Using markdown-it import MarkdownIt from "markdown-it"; try { const md = new MarkdownIt(); const html = md.render("Some *markdown* text."); console.log(html); } catch (err) { console.error("Markdown parsing error:", err); }
                +
                      ```
                +
                      **3) Python-Markdown (Python)**
                 
                      - Standard and widely used in the Python ecosystem.
                +
                      - Supports extensions like tables, fenced code blocks, etc.
                 
                -     
                      ##### Python
                +
                      ```python
                +
                      # Example: Using python-markdown import markdown try: md_text = "# Title\nSome *markdown* content." html = markdown.markdown(md_text, extensions=["extra"]) print(html) except Exception as e: print("Markdown parsing failed:", e)
                +
                      ```
                +
                      If you want, I can provide:
                 
                      - A **custom markdown parser from scratch** in any language
                +
                      - A **fast streaming markdown parser**
                +
                      - A **browser‑only** solution
                +
                      - Help integrating a parser with a framework (React, Vue, Node, Python, Go, Rust, etc.)
                 
                -     </div>
                +   </div>
                 
                      <textarea id="bCusTweakTextarea" placeholder="What would you like to adjust?" rows="1" ref="30">
                +
                      </textarea>
                -     <fieldset>
                -     </fieldset>
                -     <fieldset>
                -     </fieldset>
                 
                +   <fieldset>
                +   </fieldset>
                +   <fieldset>
                +   </fieldset>
                    </section>
                 
                    - [Markdown formatting examples](ref:36:https://www.bing.com/ck/a?!&&p=3ba8cefd7205b501e745e25f7f049517ada07cf7538aa5fab4299b7671e60d8aJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L2NvcGlsb3RzZWFyY2g_cT1NYXJrZG93bitmb3JtYXR0aW5nK2V4YW1wbGVzJmZvcm09Q1NTQU5T&ntb=1)
                    - [Free Markdown Viewer Tools](ref:38:https://www.bing.com/ck/a?!&&p=5d5b2471cfe0a0f74b991e1974ca34b3dd989d30e9dcf060a437c96bbca79d32JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L2NvcGlsb3RzZWFyY2g_cT1GcmVlK01hcmtkb3duK1ZpZXdlcitUb29scyZmb3JtPUNTU0FOUw&ntb=1)
                    - [GitHub Flavored Markdown Preview](ref:40:https://www.bing.com/ck/a?!&&p=fdfa4bf1f969b8454f69267dc2f52359c8dbccf52dd5093da93bc264cb02cca9JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L2NvcGlsb3RzZWFyY2g_cT1HaXRIdWIrRmxhdm9yZWQrTWFya2Rvd24rUHJldmlldyZmb3JtPUNTU0FOUw&ntb=1)
                +2. ## [Writing a Markdown Parser](ref:43:https://www.bing.com/ck/a?!&&p=cec1496be9f283fd141c78e2f6ae7ea4e96b1cf7fb7236f7afaab88f4ac8d71eJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9kZXYudG8va2F3YWxqYWluL2J1aWxkaW5nLW15LW93bi1tYXJrZG93bi1wYXJzZXItYS1kZXZlbG9wZXJzLWpvdXJuZXktM2IyNg&ntb=1)
                 
                -1. ## [Writing a Markdown Parser](ref:43:https://www.bing.com/ck/a?!&&p=cec1496be9f283fd141c78e2f6ae7ea4e96b1cf7fb7236f7afaab88f4ac8d71eJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9kZXYudG8va2F3YWxqYWluL2J1aWxkaW5nLW15LW93bi1tYXJrZG93bi1wYXJzZXItYS1kZXZlbG9wZXJzLWpvdXJuZXktM2IyNg&ntb=1)
                -
                    ^[1](ref:44:https://www.bing.com/ck/a?!&&p=cec1496be9f283fd141c78e2f6ae7ea4e96b1cf7fb7236f7afaab88f4ac8d71eJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9kZXYudG8va2F3YWxqYWluL2J1aWxkaW5nLW15LW93bi1tYXJrZG93bi1wYXJzZXItYS1kZXZlbG9wZXJzLWpvdXJuZXktM2IyNg&ntb=1)^^[2](ref:45:https://www.bing.com/ck/a?!&&p=b3cdc161e5b75f40074777c93795699b6363c66b4b04fd767d7c17eee1c1a808JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9naXRodWIuY29tL21hcmtlZGpzL21hcmtlZA&ntb=1)^^[3](ref:46:https://www.bing.com/ck/a?!&&p=e07186ef73027de600d9478626445ee37e8d6528fb43e08e1e42737e9ac90615JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9zdGFja292ZXJmbG93LmNvbS9xdWVzdGlvbnMvNjA1NDM0L2hvdy13b3VsZC15b3UtZ28tYWJvdXQtcGFyc2luZy1tYXJrZG93bg&ntb=1)^Creating a Markdown parser involves converting Markdown syntax into structured formats like HTML. Below is a step-by-step guide to building your own Markdown parser.
                 
                    1\. Understand Markdown Syntax
                 
                    Markdown is a lightweight markup language with simple syntax for formatting text. Key elements include:
                    - **Headings**: *# Heading*
                -   - **Bold/Italic**: *\*\*bold\*\**, *\*italic\**
                +   - **Bold/Italic**: ***bold***, \*\*italic\**
                    - **Lists**: *- Item* or *1. Item*
                    - **Links**: *[text](url)*
                -   - **Code Blocks**: *\`\`\`code\`\`\`*
                +   - **Code Blocks**: *```code```*
                    Familiarize yourself with the [CommonMark specification](ref:47:https://www.bing.com/ck/a?!&&p=e07186ef73027de600d9478626445ee37e8d6528fb43e08e1e42737e9ac90615JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9zdGFja292ZXJmbG93LmNvbS9xdWVzdGlvbnMvNjA1NDM0L2hvdy13b3VsZC15b3UtZ28tYWJvdXQtcGFyc2luZy1tYXJrZG93bg&ntb=1) for consistent parsing.
                 
                    2\. Design the Parser
                @@ -130,7 +148,7 @@
                 
                    import re
                 
                -   def tokenize(markdown):tokens = []lines = markdown.split("\n")for line in lines:if re.match(r"^# ", line):tokens.append(("heading", line[2:]))elif re.match(r"^- ", line):tokens.append(("list_item", line[2:]))elif re.match(r"^\*\*(.+?)\*\*", line):tokens.append(("bold", re.findall(r"\*\*(.+?)\*\*", line)[0]))else:tokens.append(("text", line))return tokens
                +   def tokenize(markdown):tokens = []lines = markdown.split("\n")for line in lines:if re.match(r"^# ", line):tokens.append(("heading", line[2:]))elif re.match(r"^- ", line):tokens.append(("list_item", line[2:]))elif re.match(r"^\*\*(.+?)\*\*", line):tokens.append(("bold", re.findall(r"\*\*(.+?)\*\*", line)[0]))else:tokens.append(("text", line))return tokens^
                 
                    <button id="devmag_cpy_17_57D5EE" aria-label="Copy" ref="48">
                 
                @@ -142,7 +160,7 @@
                 
                    Convert tokens into an AST:
                 
                -   def parse(tokens):ast = []for token in tokens:if token[0] == "heading":ast.append({"type": "heading", "content": token[1]})elif token[0] == "list_item":ast.append({"type": "list_item", "content": token[1]})elif token[0] == "bold":ast.append({"type": "bold", "content": token[1]})else:ast.append({"type": "text", "content": token[1]})return ast
                +   def parse(tokens):ast = []for token in tokens:if token[0] == "heading":ast.append({"type": "heading", "content": token[1]})elif token[0] == "list_item":ast.append({"type": "list_item", "content": token[1]})elif token[0] == "bold":ast.append({"type": "bold", "content": token[1]})else:ast.append({"type": "text", "content": token[1]})return ast==
                 
                    <button id="devmag_cpy_22_57DA21" aria-label="Copy" ref="49">
                 
                @@ -154,7 +172,7 @@
                 
                    Generate HTML from the AST:
                 
                -   def render(ast):html = ""for node in ast:if node["type"] == "heading":html += f"<h1>{node['content']}</h1>"elif node["type"] == "list_item":html += f"<li>{node['content']}</li>"elif node["type"] == "bold":html += f"<b>{node['content']}</b>"else:html += f"<p>{node['content']}</p>"return html
                +   def render(ast):html = ""for node in ast:if node["type"] == "heading":html +\= f"<h1>{node['content']}</h1>"elif node["type"] == "list_item":html += f"<li>{node['content']}</li>"elif node["type"] == "bold":html +\= f"<b>{node['content']}</b>"else:html +\= f"<p>{node['content']}</p>"return html==
                 
                    <button id="devmag_cpy_27_57DCA3" aria-label="Copy" ref="50">
                 
                @@ -191,15 +209,15 @@
                    By following these steps, you can build a functional and extensible Markdown parser tailored to your needs!
                 
                    [See more](ref:55:)feedback[](ref:56:javascript:void(0))[](ref:57:javascript:void(0))
                -2. [](ref:58:/images/search?view=detailV2&ccid=04zp9wcQ&id=6A22855BEA6F58327634BE5A47834F166D75B2E4&thid=OIP.04zp9wcQmM7KyyIPLcBC6wHaEK&mediaurl=https://markdownviewer.org/og-image.webp&q=markdown+parser&ck=CF2CBDB76DBCEB0322F9DFA579F49F63&idpp=rc&expw=1200&exph=675&form=rc2idp)[![Global web icon](https://th.bing.com/th/id/ODF.twAY8QtltJN42PpQk3L_AQ?w=32&h=32&qlt=90&pcl=fffffa&o=6&pid=1.2) markdownviewer.org<div ref="60"><cite>https://markdownviewer.org</cite></div>](ref:59:https://www.bing.com/ck/a?!&&p=3859f4031f1e1230773d666ec45504f6465fdaa6aebb31c2777002387b0e0715JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9tYXJrZG93bnZpZXdlci5vcmcv&ntb=1)
                +3. [](ref:58:/images/search?view=detailV2&ccid=04zp9wcQ&id=6A22855BEA6F58327634BE5A47834F166D75B2E4&thid=OIP.04zp9wcQmM7KyyIPLcBC6wHaEK&mediaurl=https://markdownviewer.org/og-image.webp&q=markdown+parser&ck=CF2CBDB76DBCEB0322F9DFA579F49F63&idpp=rc&expw=1200&exph=675&form=rc2idp)[![Global web icon](https://th.bing.com/th/id/ODF.twAY8QtltJN42PpQk3L_AQ?w=32&h=32&qlt=90&pcl=fffffa&o=6&pid=1.2) markdownviewer.org<div ref="60"><cite>https://markdownviewer.org</cite></div>](ref:59:https://www.bing.com/ck/a?!&&p=3859f4031f1e1230773d666ec45504f6465fdaa6aebb31c2777002387b0e0715JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9tYXJrZG93bnZpZXdlci5vcmcv&ntb=1)
                    ## [Markdown Viewer — Online Markdown Editor with Live …](ref:61:https://www.bing.com/ck/a?!&&p=3859f4031f1e1230773d666ec45504f6465fdaa6aebb31c2777002387b0e0715JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9tYXJrZG93bnZpZXdlci5vcmcv&ntb=1)
                 
                    Open the page, start writing, and see your markdown rendered beautifully in real time. Everything you need to write, preview, and share markdown documents — …
                -3. [](ref:62:/images/search?view=detailV2&ccid=dv9aBKdQ&id=D1B82E7D84356EFAA0D4FBEFDF1C6272E584D921&thid=OIP.dv9aBKdQ_Q1yIWrCcFIEcgHaDt&mediaurl=https://opengraph.githubassets.com/faa35b9a34e8981cd689bf21ee768a1e89380f4265b97168162156c713170c3a/markedjs/marked&q=markdown+parser&ck=E836A8F29CB73266791BDE159B93DABE&idpp=rc&expw=1200&exph=600&form=rc2idp)[![Global web icon](https://th.bing.com/th/id/ODF.bYAvaN8MCaSZfP0o7q_Z_w?w=32&h=32&qlt=91&pcl=fffffa&o=6&pid=1.2) Github<div ref="64"><cite>https://github.com › markedjs › marked</cite>[](ref:65:#)</div>](ref:63:https://www.bing.com/ck/a?!&&p=b3cdc161e5b75f40074777c93795699b6363c66b4b04fd767d7c17eee1c1a808JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9naXRodWIuY29tL21hcmtlZGpzL21hcmtlZA&ntb=1)
                +4. [](ref:62:/images/search?view=detailV2&ccid=dv9aBKdQ&id=D1B82E7D84356EFAA0D4FBEFDF1C6272E584D921&thid=OIP.dv9aBKdQ_Q1yIWrCcFIEcgHaDt&mediaurl=https://opengraph.githubassets.com/faa35b9a34e8981cd689bf21ee768a1e89380f4265b97168162156c713170c3a/markedjs/marked&q=markdown+parser&ck=E836A8F29CB73266791BDE159B93DABE&idpp=rc&expw=1200&exph=600&form=rc2idp)[![Global web icon](https://th.bing.com/th/id/ODF.bYAvaN8MCaSZfP0o7q_Z_w?w=32&h=32&qlt=91&pcl=fffffa&o=6&pid=1.2) Github<div ref="64"><cite>https://github.com › markedjs › marked</cite>[](ref:65:#)</div>](ref:63:https://www.bing.com/ck/a?!&&p=b3cdc161e5b75f40074777c93795699b6363c66b4b04fd767d7c17eee1c1a808JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9naXRodWIuY29tL21hcmtlZGpzL21hcmtlZA&ntb=1)
                    ## [GitHub - markedjs/marked: A markdown parser and …](ref:66:https://www.bing.com/ck/a?!&&p=b3cdc161e5b75f40074777c93795699b6363c66b4b04fd767d7c17eee1c1a808JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9naXRodWIuY29tL21hcmtlZGpzL21hcmtlZA&ntb=1)
                 
                    Marked is a low-level compiler for parsing markdown without caching or blocking. It supports all markdown features from the supported flavors and specifications, …
                -4. ## [Videos of Markdown Parser](ref:67:https://www.bing.com/ck/a?!&&p=1e80cf930e78cf5d7243abc3754ccafc4534574c3cd34537c9ced8380d6590a8JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1tYXJrZG93bitwYXJzZXImcXB2dD1tYXJrZG93bitwYXJzZXImRk9STT1WRFJF&ntb=1)
                +5. ## [Videos of Markdown Parser](ref:67:https://www.bing.com/ck/a?!&&p=1e80cf930e78cf5d7243abc3754ccafc4534574c3cd34537c9ced8380d6590a8JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1tYXJrZG93bitwYXJzZXImcXB2dD1tYXJrZG93bitwYXJzZXImRk9STT1WRFJF&ntb=1)
                 
                    <div ref="68"></div>
                    - [![](https://th.bing.com/th?q=Jupyter+Notebook&w=30&h=30&c=1&rs=1&qlt=90&o=6&pid=RelatedEntity&mkt=de-DE&cc=DE&setlang=en&adlt=moderate&t=1) Jupyter Notebook](ref:69:https://www.bing.com/ck/a?!&&p=3b91a8bdf8a459a9761dfa46e8d5d0b9453bc08465bbafe202f27898424a044bJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1KdXB5dGVyK05vdGVib29rJiZGT1JNPVZBUlNRUA&ntb=1)
                @@ -222,31 +240,31 @@
                    - [![](https://th.bing.com/th?q=Mark+Down+Cheat+Sheet&w=30&h=30&c=1&rs=1&qlt=90&o=6&pid=RelatedEntity&mkt=de-DE&cc=DE&setlang=en&adlt=moderate&t=1) Cheat Sheet](ref:86:https://www.bing.com/ck/a?!&&p=2a7dc2222a6ded0a1bf9dc93da33bcdcfb52b70e250f14a756713eb4cbf07579JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1DaGVhdCtTaGVldCYmRk9STT1WQVJTUVA&ntb=1)
                    - [![](https://th.bing.com/th?q=Notion&w=30&h=30&c=1&rs=1&qlt=90&o=6&pid=RelatedEntity&mkt=de-DE&cc=DE&setlang=en&adlt=moderate&t=1) Notion](ref:87:https://www.bing.com/ck/a?!&&p=180fadaf565584cd3b25c23f55110adb3a8ff931c085253df7f3deeb1790e77eJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9zZWFyY2g_cT1Ob3Rpb24mJkZPUk09VkFSU1FQ&ntb=1)
                    [![Build Your Own Markdown Parser From Scratch (Tokenizer)](https://th.bing.com/th/id/OVP.qK2437vy2iVXmC8FIglc8QEsDh?w=233&h=131&c=8&rs=1&qlt=90&o=6&pid=1.7)11:08 Build Your Own **Markdown** **Parser** From Scratch (Tokenizer) ![Video source site](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABgAAAAYCAYAAADgdz34AAABRklEQVR4Ae2UT0rDQBjFX7QICtaoIOimAReiUNuV696jmx5A8AalZ/AIpbdwIT2B6wqlWYkuWgnBP7jo+D7SkcmiM4kd6KYPfjNDeHlfJt8kwEYOBXqhgENOd+SGnJKQ7JFjsr3k/jmZkg/yQn7IE7lncAwjvEImRHlCskKzQMdjuOZWsrcWNS7hX+dmgQurNQzxD52YBQ6s1maTb3UCtNsooTOzwI7THkXAYAD0+9narVyTR9aGtVoqpyRRqtdzNXls7qCCMqpWgXodqNVsrrkZ/ImiimMe6g4wHLqciQx6BzOXGwn93S7QaBQJF6Uy6B28Wq3y1HKSZC6uL7PAG1wFymskg35FY/jX899Kfkxk6vE/JFlRrhwvXJEH8r5C8Iw8kmudGyzbH037nI6Q9UnWu8i+eDnf38iamC7saVDkJG60Fv0CnB4IzftPhuMAAAAASUVORK5CYII=)YouTubeNetcreed 968 views11 months ago](ref:88:https://www.bing.com/ck/a?!&&p=41385d47e8d057511456d3529940b3c3bcde274168d4cb1b345869c02ddbdc4bJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9yaXZlcnZpZXcvcmVsYXRlZHZpZGVvP3E9bWFya2Rvd24rcGFyc2VyJm1pZD1FRkFFMkVDOEFBOEFENzc0QTQ4N0VGQUUyRUM4QUE4QUQ3NzRBNDg3JmNodXJsPWh0dHBzJTNhJTJmJTJmd3d3LnlvdXR1YmUuY29tJTJmY2hhbm5lbCUyZlVDWkVaRU5MOVV2WVl4Nkx1Ui1yRzlFUSZGT1JNPVZJUkU&ntb=1)[![Build a Markdown Parser in Haskell with Megaparsec | Complete Tutorial](https://th.bing.com/th/id/OVP.Bu77KgbUnJvlj_OynIc8WAHgFo?w=233&h=131&c=8&rs=1&qlt=90&o=6&pid=1.7)1:03:57 Build a **Markdown** **Parser** in Haskell with Megaparsec | Complete Tutorial ![Video source site](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABgAAAAYCAYAAADgdz34AAABRklEQVR4Ae2UT0rDQBjFX7QICtaoIOimAReiUNuV696jmx5A8AalZ/AIpbdwIT2B6wqlWYkuWgnBP7jo+D7SkcmiM4kd6KYPfjNDeHlfJt8kwEYOBXqhgENOd+SGnJKQ7JFjsr3k/jmZkg/yQn7IE7lncAwjvEImRHlCskKzQMdjuOZWsrcWNS7hX+dmgQurNQzxD52YBQ6s1maTb3UCtNsooTOzwI7THkXAYAD0+9narVyTR9aGtVoqpyRRqtdzNXls7qCCMqpWgXodqNVsrrkZ/ImiimMe6g4wHLqciQx6BzOXGwn93S7QaBQJF6Uy6B28Wq3y1HKSZC6uL7PAG1wFymskg35FY/jX899Kfkxk6vE/JFlRrhwvXJEH8r5C8Iw8kmudGyzbH037nI6Q9UnWu8i+eDnf38iamC7saVDkJG60Fv0CnB4IzftPhuMAAAAASUVORK5CYII=)YouTubePurely Haskell 2K views1 Jun 2024](ref:89:https://www.bing.com/ck/a?!&&p=cb89144d235972e06fae016b5d0b2650cff08505f86265908937fa000dc33a1bJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9yaXZlcnZpZXcvcmVsYXRlZHZpZGVvP3E9bWFya2Rvd24rcGFyc2VyJm1pZD03NTg5Mzg0ODM3MzZGQkZBMzcxRjc1ODkzODQ4MzczNkZCRkEzNzFGJmNodXJsPWh0dHBzJTNhJTJmJTJmd3d3LnlvdXR1YmUuY29tJTJmY2hhbm5lbCUyZlVDeVBkYmV5QjVDTk1zVVhuVkRCUHlBQSZGT1JNPVZJUkU&ntb=1)[![dots.mocr: Document Parsing to Markdown and SVG](https://th.bing.com/th/id/OVP.7BmcCuO7EQvK4HkVnmGL9AHgFo?w=233&h=131&c=8&rs=1&qlt=90&o=6&pid=1.7)4:25 dots.mocr: Document Parsing to **Markdown** and SVG ![Video source site](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABgAAAAYCAYAAADgdz34AAABRklEQVR4Ae2UT0rDQBjFX7QICtaoIOimAReiUNuV696jmx5A8AalZ/AIpbdwIT2B6wqlWYkuWgnBP7jo+D7SkcmiM4kd6KYPfjNDeHlfJt8kwEYOBXqhgENOd+SGnJKQ7JFjsr3k/jmZkg/yQn7IE7lncAwjvEImRHlCskKzQMdjuOZWsrcWNS7hX+dmgQurNQzxD52YBQ6s1maTb3UCtNsooTOzwI7THkXAYAD0+9narVyTR9aGtVoqpyRRqtdzNXls7qCCMqpWgXodqNVsrrkZ/ImiimMe6g4wHLqciQx6BzOXGwn93S7QaBQJF6Uy6B28Wq3y1HKSZC6uL7PAG1wFymskg35FY/jX899Kfkxk6vE/JFlRrhwvXJEH8r5C8Iw8kmudGyzbH037nI6Q9UnWu8i+eDnf38iamC7saVDkJG60Fv0CnB4IzftPhuMAAAAASUVORK5CYII=)YouTubeAI Research Roundup 127 views3 months ago](ref:90:https://www.bing.com/ck/a?!&&p=17ce1687e45d09ca9046753b0216ae90ccb643014a0dd766f3939fc8d495de7eJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3ZpZGVvcy9yaXZlcnZpZXcvcmVsYXRlZHZpZGVvP3E9bWFya2Rvd24rcGFyc2VyJm1pZD02MUQ2RDM0MDNBM0FGNzY3QkQ1RTYxRDZEMzQwM0EzQUY3NjdCRDVFJmNodXJsPWh0dHBzJTNhJTJmJTJmd3d3LnlvdXR1YmUuY29tJTJmY2hhbm5lbCUyZlVDZ2FuNDBFd3hrd005b3QxcFY4bFRGQSZGT1JNPVZJUkU&ntb=1)
                -5. [](ref:91:/images/search?view=detailV2&ccid=HkedMGjU&id=A3FE230F5B6A17750472607F8FB14BC536C684E6&thid=OIP.HkedMGjUUJqPHyEQ8i6s8gAAAA&mediaurl=https://markdownlivepreview.com/image/sample.webp&q=markdown+parser&ck=F771B10C5E97D1CF4C1EABCAC80E4EB2&idpp=rc&expw=200&exph=200&form=rc2idp)[![Global web icon](https://th.bing.com/th/id/ODF.GpAiJJMkVcw4eKdNuwTQMw?w=32&h=32&qlt=92&pcl=fffffa&o=6&pid=1.2) Markdown Live Preview<div ref="93"><cite>https://markdownlivepreview.com</cite></div>](ref:92:https://www.bing.com/ck/a?!&&p=ca74bd240693407427c1f2f075f73fdc2ce434adb48f182840d7a6711bc5797bJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9tYXJrZG93bmxpdmVwcmV2aWV3LmNvbS8&ntb=1)
                +6. [](ref:91:/images/search?view=detailV2&ccid=HkedMGjU&id=A3FE230F5B6A17750472607F8FB14BC536C684E6&thid=OIP.HkedMGjUUJqPHyEQ8i6s8gAAAA&mediaurl=https://markdownlivepreview.com/image/sample.webp&q=markdown+parser&ck=F771B10C5E97D1CF4C1EABCAC80E4EB2&idpp=rc&expw=200&exph=200&form=rc2idp)[![Global web icon](https://th.bing.com/th/id/ODF.GpAiJJMkVcw4eKdNuwTQMw?w=32&h=32&qlt=92&pcl=fffffa&o=6&pid=1.2) Markdown Live Preview<div ref="93"><cite>https://markdownlivepreview.com</cite></div>](ref:92:https://www.bing.com/ck/a?!&&p=ca74bd240693407427c1f2f075f73fdc2ce434adb48f182840d7a6711bc5797bJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9tYXJrZG93bmxpdmVwcmV2aWV3LmNvbS8&ntb=1)
                    ## [Markdown Live Preview](ref:94:https://www.bing.com/ck/a?!&&p=ca74bd240693407427c1f2f075f73fdc2ce434adb48f182840d7a6711bc5797bJmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9tYXJrZG93bmxpdmVwcmV2aWV3LmNvbS8&ntb=1)
                 
                    This is the online markdown editor with live preview.
                -6. [![Global web icon](https://th.bing.com/th/id/ODF.yzOL4KSMcVlWmkGBkr9GjQ?w=32&h=32&qlt=93&pcl=fffffa&o=6&pid=1.2) markdownonline.org<div ref="96"><cite>https://markdownonline.org</cite></div>](ref:95:https://www.bing.com/ck/a?!&&p=9945a1d1ef2464c7b3fd2f9c56027439153e204aa139e7f9ff4b6e5a7018ef62JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9tYXJrZG93bm9ubGluZS5vcmcv&ntb=1)
                +7. [![Global web icon](https://th.bing.com/th/id/ODF.yzOL4KSMcVlWmkGBkr9GjQ?w=32&h=32&qlt=93&pcl=fffffa&o=6&pid=1.2) markdownonline.org<div ref="96"><cite>https://markdownonline.org</cite></div>](ref:95:https://www.bing.com/ck/a?!&&p=9945a1d1ef2464c7b3fd2f9c56027439153e204aa139e7f9ff4b6e5a7018ef62JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9tYXJrZG93bm9ubGluZS5vcmcv&ntb=1)
                    ## [Markdown Online - Viewer and Converter](ref:97:https://www.bing.com/ck/a?!&&p=9945a1d1ef2464c7b3fd2f9c56027439153e204aa139e7f9ff4b6e5a7018ef62JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9tYXJrZG93bm9ubGluZS5vcmcv&ntb=1)
                 
                    Free browser-based Markdown viewer and converter. Edit, preview, and convert Markdown to HTML, PDF, tables, and more with local-first privacy.
                -7. [](ref:98:/images/search?view=detailV2&ccid=G+zlgf/x&id=04944F123DE7CEC178B13938144FACE9BFCF7AE1&thid=OIP.G-zlgf_xM0lgngLhM4V8TgHaDt&mediaurl=https://opengraph.githubassets.com/39ae70f459af178dc863d41b765afe1182de85d41c92211bf8d564a3eb49f1b0/markdown-it/markdown-it&q=markdown+parser&ck=CC9F2D159E8A426C34766A76D84BC984&idpp=rc&expw=1200&exph=600&form=rc2idp)[![Global web icon](https://th.bing.com/th/id/ODF.bYAvaN8MCaSZfP0o7q_Z_w?w=32&h=32&qlt=94&pcl=fffffa&o=6&pid=1.2) Github<div ref="100"><cite>https://github.com › markdown-it › markdown-it</cite>[](ref:101:#)</div>](ref:99:https://www.bing.com/ck/a?!&&p=867914eb2a962e978ebff851da7d5e104eb5edef55cbde4f06e2600897c10548JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9naXRodWIuY29tL21hcmtkb3duLWl0L21hcmtkb3duLWl0&ntb=1)
                +8. [](ref:98:/images/search?view=detailV2&ccid=G+zlgf/x&id=04944F123DE7CEC178B13938144FACE9BFCF7AE1&thid=OIP.G-zlgf_xM0lgngLhM4V8TgHaDt&mediaurl=https://opengraph.githubassets.com/39ae70f459af178dc863d41b765afe1182de85d41c92211bf8d564a3eb49f1b0/markdown-it/markdown-it&q=markdown+parser&ck=CC9F2D159E8A426C34766A76D84BC984&idpp=rc&expw=1200&exph=600&form=rc2idp)[![Global web icon](https://th.bing.com/th/id/ODF.bYAvaN8MCaSZfP0o7q_Z_w?w=32&h=32&qlt=94&pcl=fffffa&o=6&pid=1.2) Github<div ref="100"><cite>https://github.com › markdown-it › markdown-it</cite>[](ref:101:#)</div>](ref:99:https://www.bing.com/ck/a?!&&p=867914eb2a962e978ebff851da7d5e104eb5edef55cbde4f06e2600897c10548JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9naXRodWIuY29tL21hcmtkb3duLWl0L21hcmtkb3duLWl0&ntb=1)
                    ## [GitHub - markdown-it/markdown-it: Markdown parser, …](ref:102:https://www.bing.com/ck/a?!&&p=867914eb2a962e978ebff851da7d5e104eb5edef55cbde4f06e2600897c10548JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9naXRodWIuY29tL21hcmtkb3duLWl0L21hcmtkb3duLWl0&ntb=1)
                 
                    markdown-it is a Markdown parser that supports CommonMark spec, syntax extensions, plugins and high speed. It can be used in node.js or browser with …
                -8. [](ref:103:/images/search?view=detailV2&ccid=FkHyzjVW&id=A9A6487F570752D4EAB2E23577820A25F5D049BE&thid=OIP.FkHyzjVW6sTm1zO5f6rHxgHaD4&mediaurl=https://dillinger.io/opengraph-image?72fdddf5ac7e0219&q=markdown+parser&ck=3308144C5AFAED9B5E98652364F0228C&idpp=rc&expw=1200&exph=630&form=rc2idp)[![Global web icon](https://th.bing.com/th/id/ODF.KzNcRzKHTcHwAS89WRwvVg?w=32&h=32&qlt=95&pcl=fffffa&o=6&pid=1.2) Dillinger<div ref="105"><cite>https://dillinger.io</cite></div>](ref:104:https://www.bing.com/ck/a?!&&p=c9846684349cf4f558df7ac9ac9a315f7df0c6541cc745c92e923a3e7fe89673JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9kaWxsaW5nZXIuaW8v&ntb=1)
                +9. [](ref:103:/images/search?view=detailV2&ccid=FkHyzjVW&id=A9A6487F570752D4EAB2E23577820A25F5D049BE&thid=OIP.FkHyzjVW6sTm1zO5f6rHxgHaD4&mediaurl=https://dillinger.io/opengraph-image?72fdddf5ac7e0219&q=markdown+parser&ck=3308144C5AFAED9B5E98652364F0228C&idpp=rc&expw=1200&exph=630&form=rc2idp)[![Global web icon](https://th.bing.com/th/id/ODF.KzNcRzKHTcHwAS89WRwvVg?w=32&h=32&qlt=95&pcl=fffffa&o=6&pid=1.2) Dillinger<div ref="105"><cite>https://dillinger.io</cite></div>](ref:104:https://www.bing.com/ck/a?!&&p=c9846684349cf4f558df7ac9ac9a315f7df0c6541cc745c92e923a3e7fe89673JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9kaWxsaW5nZXIuaW8v&ntb=1)
                    ## [Markdown Editor — Online, Free, with Live Preview](ref:106:https://www.bing.com/ck/a?!&&p=c9846684349cf4f558df7ac9ac9a315f7df0c6541cc745c92e923a3e7fe89673JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9kaWxsaW5nZXIuaW8v&ntb=1)
                 
                    Free online Markdown editor with live preview. Write, format, and export Markdown to HTML or PDF — sync to GitHub, Dropbox & Google Drive. No signup.
                -9. [![Global web icon](https://th.bing.com/th/id/ODF.43D875aclGlnB4GFYbdonQ?w=32&h=32&qlt=96&pcl=fffffa&o=6&pid=1.2) npm<div ref="108"><cite>https://www.npmjs.com › package › marked</cite></div>](ref:107:https://www.bing.com/ck/a?!&&p=e9ae8f9d288ff8fa9880f2014bf2815b379fcac2e1a710da3415bcb7c8221749JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly93d3cubnBtanMuY29tL3BhY2thZ2UvbWFya2Vk&ntb=1)
                -   ## [marked - npm](https://www.bing.com/ck/a?!&&p=e9ae8f9d288ff8fa9880f2014bf2815b379fcac2e1a710da3415bcb7c8221749JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly93d3cubnBtanMuY29tL3BhY2thZ2UvbWFya2Vk&ntb=1)
                -10. [![Global web icon](https://th.bing.com/th/id/ODF.Xs623j1krK2fpZc5rpp_jQ?w=32&h=32&qlt=97&pcl=fffffa&o=6&pid=1.2) pages.dev<div ref="110"><cite>https://markdownviewer.pages.dev</cite></div>](ref:109:https://www.bing.com/ck/a?!&&p=6bb8656a9853e7d7366ff663d9fe98e986d035ab668e05915d7fbb9e2550fb68JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9tYXJrZG93bnZpZXdlci5wYWdlcy5kZXYv&ntb=1)
                +10. [![Global web icon](https://th.bing.com/th/id/ODF.43D875aclGlnB4GFYbdonQ?w=32&h=32&qlt=96&pcl=fffffa&o=6&pid=1.2) npm<div ref="108"><cite>https://www.npmjs.com › package › marked</cite></div>](ref:107:https://www.bing.com/ck/a?!&&p=e9ae8f9d288ff8fa9880f2014bf2815b379fcac2e1a710da3415bcb7c8221749JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly93d3cubnBtanMuY29tL3BhY2thZ2UvbWFya2Vk&ntb=1)
                +    ## [marked - npm](https://www.bing.com/ck/a?!&&p=e9ae8f9d288ff8fa9880f2014bf2815b379fcac2e1a710da3415bcb7c8221749JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly93d3cubnBtanMuY29tL3BhY2thZ2UvbWFya2Vk&ntb=1)
                +11. [![Global web icon](https://th.bing.com/th/id/ODF.Xs623j1krK2fpZc5rpp_jQ?w=32&h=32&qlt=97&pcl=fffffa&o=6&pid=1.2) pages.dev<div ref="110"><cite>https://markdownviewer.pages.dev</cite></div>](ref:109:https://www.bing.com/ck/a?!&&p=6bb8656a9853e7d7366ff663d9fe98e986d035ab668e05915d7fbb9e2550fb68JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9tYXJrZG93bnZpZXdlci5wYWdlcy5kZXYv&ntb=1)
                     ## [Markdown Viewer](https://www.bing.com/ck/a?!&&p=6bb8656a9853e7d7366ff663d9fe98e986d035ab668e05915d7fbb9e2550fb68JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9tYXJrZG93bnZpZXdlci5wYWdlcy5kZXYv&ntb=1)
                -11. [![Global web icon](https://th.bing.com/th/id/ODF.X49lZ0B5hZKCU_k0wLCxNg?w=32&h=32&qlt=98&pcl=fffffa&o=6&pid=1.2) ezparser.com<div ref="112"><cite>https://ezparser.com › markdown-parser</cite></div>](ref:111:https://www.bing.com/ck/a?!&&p=4ebe062ab95e4d58022721b3c872426330a29c4d125e57e23cdd4c518950aa83JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9lenBhcnNlci5jb20vbWFya2Rvd24tcGFyc2Vy&ntb=1)
                +12. [![Global web icon](https://th.bing.com/th/id/ODF.X49lZ0B5hZKCU_k0wLCxNg?w=32&h=32&qlt=98&pcl=fffffa&o=6&pid=1.2) ezparser.com<div ref="112"><cite>https://ezparser.com › markdown-parser</cite></div>](ref:111:https://www.bing.com/ck/a?!&&p=4ebe062ab95e4d58022721b3c872426330a29c4d125e57e23cdd4c518950aa83JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9lenBhcnNlci5jb20vbWFya2Rvd24tcGFyc2Vy&ntb=1)
                     ## [Markdown Parser - Free Online Markdown Previewer & HTML …](https://www.bing.com/ck/a?!&&p=4ebe062ab95e4d58022721b3c872426330a29c4d125e57e23cdd4c518950aa83JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly9lenBhcnNlci5jb20vbWFya2Rvd24tcGFyc2Vy&ntb=1)
                -12. [![Global web icon](https://th.bing.com/th/id/ODF.43D875aclGlnB4GFYbdonQ?w=32&h=32&qlt=99&pcl=fffffa&o=6&pid=1.2) npm<div ref="114"><cite>https://www.npmjs.com › package › markdown-parser</cite></div>](ref:113:https://www.bing.com/ck/a?!&&p=1eeda170c124c44132f1e11ab9e61ff69dbf67c235dc01cdecb3da0734d0ced9JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly93d3cubnBtanMuY29tL3BhY2thZ2UvbWFya2Rvd24tcGFyc2Vy&ntb=1)
                +13. [![Global web icon](https://th.bing.com/th/id/ODF.43D875aclGlnB4GFYbdonQ?w=32&h=32&qlt=99&pcl=fffffa&o=6&pid=1.2) npm<div ref="114"><cite>https://www.npmjs.com › package › markdown-parser</cite></div>](ref:113:https://www.bing.com/ck/a?!&&p=1eeda170c124c44132f1e11ab9e61ff69dbf67c235dc01cdecb3da0734d0ced9JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly93d3cubnBtanMuY29tL3BhY2thZ2UvbWFya2Rvd24tcGFyc2Vy&ntb=1)
                     ## [markdown-parser - npm](https://www.bing.com/ck/a?!&&p=1eeda170c124c44132f1e11ab9e61ff69dbf67c235dc01cdecb3da0734d0ced9JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1aHR0cHM6Ly93d3cubnBtanMuY29tL3BhY2thZ2UvbWFya2Rvd24tcGFyc2Vy&ntb=1)
                -13. ## Deep dive into **markdown parser**
                +14. ## Deep dive into **markdown parser**
                     - [markdown parser **python**](https://www.bing.com/ck/a?!&&p=6b6e323323b306f551bc005733409cfe67eebc653bb27cd34ba05a593a2d18f6JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3NlYXJjaD9xPW1hcmtkb3duK3BhcnNlcitweXRob24mRk9STT1RU1JFMQ&ntb=1)
                     - [markdown **viewer**](https://www.bing.com/ck/a?!&&p=5c5070f5857b8a654f7d380ecfaf4791e9c12886dadd2e6ee0ae4b27bc060341JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3NlYXJjaD9xPW1hcmtkb3duK3ZpZXdlciZGT1JNPVFTUkUy&ntb=1)
                     - [markdown **visualizer online free**](https://www.bing.com/ck/a?!&&p=b66ce2fa8eb1b0f25a7f28ecc37b879e35ac9146aa4bebfe809b6ac94515e6d7JmltdHM9MTc4MTgyNzIwMA&ptn=3&ver=2&hsh=4&fclid=38180825-ae08-6c32-1452-1f58afbf6da6&u=a1L3NlYXJjaD9xPW1hcmtkb3duK3Zpc3VhbGl6ZXIrb25saW5lK2ZyZWUmRk9STT1RU1JFMw&ntb=1)
                @@ -267,8 +285,11 @@
                 <footer id="b_footer">
                 </footer>
                 <section>
                +
                 <textarea id="b_copilot_composer_2" placeholder="Ask a follow-up " rows="1" ref="124">
                +
                 </textarea>
                +
                 </section>
                 <button type="submit" aria-label="Send" ref="125">
                 </button>
                
            """.trimIndent()
        }
    }

}
