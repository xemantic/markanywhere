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

class SerpDuckduckgoTest {

    @Test
    fun `should convert captured serp-duckduckgo DOM dump to Markdown`() = runTest {
        // given
        val events = dumpFlow(DumpFixtures.serpDuckduckgo)

        // when
        val markdown = events.transformHtmlToMarkdown().renderMarkdown()

        // then
        markdown sameAs /* language=markdown */ """
            ---
            lang: en-GB
            description: DuckDuckGo. Privacy, Simplified.
            title: markdown parser at DuckDuckGo
            HandheldFriendly: "true"
            ---
            
            [DuckDuckGo](ref:1:/)
            
            <form id="search_form" action="/" method="GET" name="x">
            <input id="search_form_input" name="q" value="markdown parser" placeholder="Search privately" aria-expanded="false" ref="2">
            <button type="submit" aria-label="search" ref="3">
            </button>
            </form>
            <header>
            
            ![](data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjgiIGhlaWdodD0iMjgiIHZpZXdCb3g9IjAgMCAyOCAyOCIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0xNCAyOEMyMS43MzIgMjggMjggMjEuNzMyIDI4IDE0QzI4IDYuMjY4MDEgMjEuNzMyIDAgMTQgMEM2LjI2ODAxIDAgMCA2LjI2ODAxIDAgMTRDMCAyMS43MzIgNi4yNjgwMSAyOCAxNCAyOFoiIGZpbGw9IiNERTU4MzMiLz4KPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0xNy4xNDEyIDI2LjQ5ODFDMTYuNTU1NiAyNS4zNTk0IDE1Ljk5NTYgMjQuMzExNyAxNS42NDc5IDIzLjYxODdDMTQuNzIzOSAyMS43Njg0IDEzLjc5NTIgMTkuMTU5NyAxNC4yMTc2IDE3LjQ3NzRDMTQuMjk0NiAxNy4xNzE3IDEzLjM0NzIgNi4xNTg0MSAxMi42Nzc2IDUuODAzNzRDMTEuOTMzMiA1LjQwNzA3IDExLjAxNzQgNC43Nzc4MyAxMC4xNzk4IDQuNjM3ODNDOS43NTQ3NSA0LjU2OTgzIDkuMTk3NSA0LjYwMjA0IDguNzYxODcgNC42NjA3MUM4LjY4NDQ0IDQuNjcxMTQgOC42ODEyNCA0LjgxMDMgOC43NTUyNCA0LjgzNTM3QzkuMDQxMjMgNC45MzIyNyA5LjM4ODUxIDUuMTAwNSA5LjU5MzE3IDUuMzU0ODdDOS42MzE5MSA1LjQwMzAxIDkuNTc5OTIgNS40Nzg3NCA5LjUxODE2IDUuNDgxMDJDOS4zMjUyNSA1LjQ4ODE3IDguOTc1MjYgNS41NjkwNSA4LjUxMzUgNS45NjEyNUM4LjQ2MDA5IDYuMDA2NjEgOC41MDQ0NSA2LjA5MDg0IDguNTczMTkgNi4wNzcyNEM5LjU2NTU2IDUuODgwOTEgMTAuNTc5IDUuOTc3NjQgMTEuMTc2MyA2LjUyMDUxQzExLjIxNTEgNi41NTU3NCAxMS4xOTQ4IDYuNjE4OTUgMTEuMTQ0MyA2LjYzMjY4QzUuOTYxMTUgOC4wNDEyMiA2Ljk4NzE5IDEyLjU0OTkgOC4zNjY5OSAxOC4wODI5QzkuNjY3MDcgMjMuMjk2MiAxMC4yODM3IDI1LjM1MjMgMTAuMzU4IDI1LjU5NjRDMTAuMzYzIDI1LjYxMjggMTAuMzcyMiAyNS42MjU0IDEwLjM4NzIgMjUuNjMzN0MxMS40NTU1IDI2LjIxOTkgMTcuMDcyNSAyNi41NzU5IDE2LjgzNDMgMjUuOTA0NkwxNy4xNDEyIDI2LjQ5ODFaIiBmaWxsPSIjREREREREIi8+CjxwYXRoIGZpbGwtcnVsZT0iZXZlbm9kZCIgY2xpcC1ydWxlPSJldmVub2RkIiBkPSJNMjYuODMzMyAxMy45OTk0QzI2LjgzMzMgMjEuMDg3IDIxLjA4NzcgMjYuODMyNyAxNCAyNi44MzI3QzYuOTEyMzYgMjYuODMyNyAxLjE2NjY5IDIxLjA4NyAxLjE2NjY5IDEzLjk5OTRDMS4xNjY2OSA2LjkxMTcgNi45MTIzNiAxLjE2NjAyIDE0IDEuMTY2MDJDMjEuMDg3NyAxLjE2NjAyIDI2LjgzMzMgNi45MTE3IDI2LjgzMzMgMTMuOTk5NFpNMTAuOTk4OSAyNS4zNTE4QzEwLjY3NDMgMjQuMzU1NCA5LjY4NDU4IDIxLjIyMzMgOC42NzI4NyAxNy4wNzYxQzguNjM5OTMgMTYuOTQxMSA4LjYwNzA0IDE2LjgwNjcgOC41NzQzMSAxNi42NzNDNy4zODc3MyAxMS44MjY0IDYuNDE4NTggNy44Njc4IDExLjczMDIgNi42MjQyQzExLjc3ODggNi42MTI4MyAxMS44MDI1IDYuNTU0NzcgMTEuNzcwNCA2LjUxNjYzQzExLjE2MSA1Ljc5MzY0IDEwLjAxOTMgNS41NTY2NyA4LjU3NTY0IDYuMDU0NjlDOC41MTY0NCA2LjA3NTEyIDguNDY1MDMgNi4wMTU0MiA4LjUwMTgxIDUuOTY0NzJDOC43ODQ4OSA1LjU3NDUzIDkuMzM4MDkgNS4yNzQ1MiA5LjYxMTIgNS4xNDNDOS42Njc2NSA1LjExNTgyIDkuNjY0MjIgNS4wMzMxNiA5LjYwNDQyIDUuMDE0NDVDOS40MjU3OCA0Ljk1ODUzIDkuMTIxNjMgNC44NzI5NSA4Ljc3OTQ5IDQuODE3NzNDOC42OTg1NCA0LjgwNDY3IDguNjkxMjQgNC42NjU5NCA4Ljc3MjUgNC42NTUwMUMxMC44MTkxIDQuMzc5NjYgMTIuOTU2MiA0Ljk5NDEgMTQuMDI4NiA2LjM0NTAxQzE0LjAzODcgNi4zNTc3MyAxNC4wNTMzIDYuMzY2NjIgMTQuMDY5MiA2LjM3MDAzQzE3Ljk5NjQgNy4yMTM0MSAxOC4yNzc2IDEzLjQyMTQgMTcuODI1MiAxMy43MDQyQzE3LjczNiAxMy43NTk5IDE3LjQ1MDEgMTMuNzI3OSAxNy4wNzMxIDEzLjY4NThDMTUuNTQ1IDEzLjUxNDggMTIuNTE5MiAxMy4xNzYyIDE1LjAxNjUgMTcuODMwNUMxNS4wNDEyIDE3Ljg3NjQgMTUuMDA4NSAxNy45MzczIDE0Ljk1NyAxNy45NDUzQzEzLjU0MzIgMTguMTY1MSAxNS4zMTcyIDIyLjUzMjggMTYuNjQ1MyAyNS40Mzk3QzIxLjg1NSAyNC4yNCAyNS43Mzk2IDE5LjU3MzMgMjUuNzM5NiAxMy45OTk0QzI1LjczOTYgNy41MTU3NiAyMC40ODM2IDIuMjU5NzcgMTQgMi4yNTk3N0M3LjUxNjQzIDIuMjU5NzcgMi4yNjA0NCA3LjUxNTc2IDIuMjYwNDQgMTMuOTk5NEMyLjI2MDQ0IDE5LjQ0NTYgNS45NjkxNyAyNC4wMjU3IDEwLjk5ODkgMjUuMzUxOFoiIGZpbGw9IndoaXRlIi8+CjxwYXRoIGZpbGwtcnVsZT0iZXZlbm9kZCIgY2xpcC1ydWxlPSJldmVub2RkIiBkPSJNMTguNDM2MyAxOS44NDAyQzE4LjEzNzQgMTkuNzAxNiAxNi45ODc4IDIwLjUyNiAxNi4yMjQ5IDIxLjE1ODhDMTYuMDY1NCAyMC45MzMyIDE1Ljc2NDggMjAuNzY5MiAxNS4wODY2IDIwLjg4NzJDMTQuNDkzIDIwLjk5MDQgMTQuMTY1MyAyMS4xMzM1IDE0LjAxOTEgMjEuMzgwMkMxMy4wODIyIDIxLjAyNSAxMS41MDYxIDIwLjQ3NjkgMTEuMTI1MyAyMS4wMDYzQzEwLjcwOTEgMjEuNTg1IDExLjIyOTMgMjQuMzIyNiAxMS43ODIxIDI0LjY3ODFDMTIuMDcwOCAyNC44NjM3IDEzLjQ1MTMgMjMuOTc2MyAxNC4xNzIzIDIzLjM2NDNDMTQuMjg4NiAyMy41MjgyIDE0LjQ3NTggMjMuNjIyIDE0Ljg2MDggMjMuNjEzMUMxNS40NDMgMjMuNTk5NiAxNi4zODc0IDIzLjQ2NDEgMTYuNTMzOSAyMy4xOTNDMTYuNTQyOCAyMy4xNzY1IDE2LjU1MDUgMjMuMTU3IDE2LjU1NyAyMy4xMzQ4QzE3LjI5OCAyMy40MTE4IDE4LjYwMjIgMjMuNzA0OSAxOC44OTM2IDIzLjY2MTFDMTkuNjUyOCAyMy41NDcxIDE4Ljc4NzggMjAuMDAzIDE4LjQzNjMgMTkuODQwMloiIGZpbGw9IiMzQ0E4MkIiLz4KPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0xNi4yOTQ3IDIxLjIzOTJDMTYuMzI2MiAyMS4yOTUyIDE2LjM1MTUgMjEuMzU0NCAxNi4zNzMgMjEuNDE0NUMxNi40Nzg2IDIxLjcwOTkgMTYuNjUwNyAyMi42NDk4IDE2LjUyMDUgMjIuODgxOUMxNi4zOTA0IDIzLjExNDEgMTUuNTQ1MSAyMy4yMjYyIDE1LjAyMzYgMjMuMjM1MkMxNC41MDIxIDIzLjI0NDIgMTQuMzg0NyAyMy4wNTM1IDE0LjI3OTEgMjIuNzU4QzE0LjE5NDUgMjIuNTIxNyAxNC4xNTI5IDIxLjk2NiAxNC4xNTQgMjEuNjQ3OEMxNC4xMzI2IDIxLjE3NTkgMTQuMzA1IDIxLjAxIDE1LjEwMiAyMC44ODFDMTUuNjkxOCAyMC43ODU2IDE2LjAwMzcgMjAuODk2NiAxNi4xODM3IDIxLjA4NjVDMTcuMDIxIDIwLjQ2MTYgMTguNDE4MSAxOS41Nzk2IDE4LjU1NDQgMTkuNzQwOEMxOS4yMzM4IDIwLjU0NDUgMTkuMzE5NyAyMi40NTc4IDE5LjE3MjYgMjMuMjI3NkMxOS4xMjQ1IDIzLjQ3OTIgMTYuODc0NyAyMi45NzgyIDE2Ljg3NDcgMjIuNzA2OEMxNi44NzQ3IDIxLjU3OTkgMTYuNTgyMiAyMS4yNzA4IDE2LjI5NDcgMjEuMjM5MlpNMTEuMzY2NCAyMC44ODczQzExLjU1MDYgMjAuNTk1NiAxMy4wNDQ5IDIwLjk1ODMgMTMuODY1MiAyMS4zMjMzQzEzLjg2NTIgMjEuMzIzMyAxMy42OTY2IDIyLjA4NyAxMy45NjUgMjIuOTg2N0MxNC4wNDM1IDIzLjI0OTkgMTIuMDc3OSAyNC40MjEyIDExLjgyMTMgMjQuMjE5N0MxMS41MjQ4IDIzLjk4NjggMTAuOTc5IDIxLjUwMDIgMTEuMzY2NCAyMC44ODczWiIgZmlsbD0iIzRDQkEzQyIvPgo8cGF0aCBmaWxsLXJ1bGU9ImV2ZW5vZGQiIGNsaXAtcnVsZT0iZXZlbm9kZCIgZD0iTTEyLjA5MDEgMTQuOTY0QzEyLjIxMSAxNC40MzgzIDEyLjc3NDIgMTMuNDQ3NiAxNC43ODUzIDEzLjQ3MTdDMTUuODAyIDEzLjQ2NzUgMTcuMDY1IDEzLjQ3MTMgMTcuOTAyMyAxMy4zNzYxQzE5LjE0NjkgMTMuMjM0OCAyMC4wNzA2IDEyLjkzMzggMjAuNjg2MiAxMi42OTkxQzIxLjU1NjggMTIuMzY3IDIxLjg2NTcgMTIuNDQxIDIxLjk3NCAxMi42Mzk4QzIyLjA5MzEgMTIuODU4MSAyMS45NTI4IDEzLjIzNTIgMjEuNjQ4NiAxMy41ODIzQzIxLjA2NzYgMTQuMjQ1MyAyMC4wMjMgMTQuNzU5IDE4LjE3ODIgMTQuOTExNUMxNi4zMzMzIDE1LjA2NCAxNS4xMTEyIDE0LjU2OTEgMTQuNTg1IDE1LjM3NDhDMTQuMzU4MSAxNS43MjIzIDE0LjUzMzYgMTYuNTQxMyAxNi4zMTc2IDE2Ljc5OTJDMTguNzI4NSAxNy4xNDcxIDIwLjcwODQgMTYuMzggMjAuOTUzMSAxNi44NDMzQzIxLjE5NzcgMTcuMzA2NiAxOS43ODg1IDE4LjI0OTMgMTcuMzczOCAxOC4yNjkxQzE0Ljk1OSAxOC4yODg4IDEzLjQ1MDggMTcuNDIzNiAxMi45MTU5IDE2Ljk5MzVDMTIuMjM3MyAxNi40NDc5IDExLjkzMzYgMTUuNjUyMiAxMi4wOTAxIDE0Ljk2NFoiIGZpbGw9IiNGRkNDMzMiLz4KPGcgb3BhY2l0eT0iMC44NSI+CjxwYXRoIGQ9Ik0xMC41ODE4IDEyLjg4MTNDMTEuMDcxMyAxMi44ODEzIDExLjQ2NzYgMTIuNDg1OSAxMS40Njc2IDExLjk5ODNDMTEuNDY3NiAxMS41MTA2IDExLjA3MDQgMTEuMTE1MiAxMC41ODE4IDExLjExNTJDMTAuMDkzMiAxMS4xMTUyIDkuNjk1OTcgMTEuNTEwNiA5LjY5NTk3IDExLjk5ODNDOS42OTU5NyAxMi40ODU5IDEwLjA5MzIgMTIuODgxMyAxMC41ODE4IDEyLjg4MTNaTTEwLjk3NzEgMTEuNDc3QzExLjEwMzkgMTEuNDc3IDExLjIwNjUgMTEuNTc5NiAxMS4yMDY1IDExLjcwNTVDMTEuMjA2NSAxMS44MzE0IDExLjEwMzkgMTEuOTMzOSAxMC45NzcxIDExLjkzMzlDMTAuODUwMyAxMS45MzM5IDEwLjc0NzggMTEuODMxNCAxMC43NDc4IDExLjcwNTVDMTAuNzQ4NyAxMS41Nzg3IDEwLjg1MTMgMTEuNDc3IDEwLjk3NzEgMTEuNDc3WiIgZmlsbD0iIzE0MzA3RSIvPgo8cGF0aCBkPSJNMTUuNzQyNiAxMS40NzU5QzE1Ljc0MjYgMTEuODkzNiAxNi4wODMgMTIuMjMzIDE2LjUwMjYgMTIuMjMzQzE2LjkyMjIgMTIuMjMzIDE3LjI2MjUgMTEuODkzNiAxNy4yNjI1IDExLjQ3NTlDMTcuMjYyNSAxMS4wNTgyIDE2LjkyMjIgMTAuNzE4OCAxNi41MDI2IDEwLjcxODhDMTYuMDgzIDEwLjcxODggMTUuNzQyNiAxMS4wNTgyIDE1Ljc0MjYgMTEuNDc1OVpNMTYuODQxMSAxMS4wMjkzQzE2Ljk0OTIgMTEuMDI5MyAxNy4wMzc4IDExLjExNjkgMTcuMDM3OCAxMS4yMjUxQzE3LjAzNzggMTEuMzMzMiAxNi45NTAyIDExLjQyMDkgMTYuODQxMSAxMS40MjA5QzE2LjczMjkgMTEuNDIwOSAxNi42NDQzIDExLjMzMzIgMTYuNjQ0MyAxMS4yMjUxQzE2LjY0NTMgMTEuMTE2OSAxNi43MzI5IDExLjAyOTMgMTYuODQxMSAxMS4wMjkzWiIgZmlsbD0iIzE0MzA3RSIvPgo8cGF0aCBkPSJNOS42NjIzNCA5LjQ5NjQzQzEwLjEzNyA5LjI5ODA5IDEwLjUxIDkuMzIzNzMgMTAuNzczNyA5LjM4NjExQzEwLjgyOTIgOS4zOTkyNCAxMC44Njc4IDkuMzM5NSAxMC44MjM0IDkuMzAzNjhDMTAuNjE4NyA5LjEzODU4IDEwLjE2MDYgOC45MzM2MyA5LjU2MzE3IDkuMTU2NDFDOS4wMzAyMiA5LjM1NTE0IDguNzc5MDEgOS43NjgwNCA4Ljc3NzUzIDEwLjAzOTVDOC43NzcxOCAxMC4xMDM1IDguOTA4OCAxMC4xMDg5IDguOTQyODQgMTAuMDU0OEM5LjAzNDgyIDkuOTA4NDMgOS4xODc3MiA5LjY5NDc4IDkuNjYyMzQgOS40OTY0M1oiIGZpbGw9IiMxNDMwN0UiLz4KPHBhdGggZD0iTTE2LjA4NzQgOC44MjQyMkMxNS41OTg2IDguODI0MjIgMTUuMjk5OSA4Ljk5NDQ1IDE1LjE2NTMgOS4yMTVDMTUuMTMzMyA5LjI2NzI3IDE1LjIxODIgOS4zMjU3MiAxNS4yNzIgOS4yOTY0N0MxNS40MzE3IDkuMjA5NjYgMTUuNjg1MiA5LjEwODk0IDE2LjA4NzQgOS4xMTQ2NkMxNi40ODk2IDkuMTIwMzggMTYuNzEyNyA5LjIyOTY0IDE2Ljg5ODQgOS4zMDk5NEMxNi45NDg3IDkuMzMxNjUgMTYuOTk3NyA5LjI3OTcyIDE2Ljk2NTMgOS4yMzU2MUMxNi44MDYyIDkuMDE4NzEgMTYuNTc2MiA4LjgyNDIyIDE2LjA4NzQgOC44MjQyMloiIGZpbGw9IiMxNDMwN0UiLz4KPC9nPgo8L3N2Zz4K)
            
            Upgrade to our browser.
            
            </header>
            
            ![](/dist/react-assets/5b372fc9558d742823b4.png)![](data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNTgiIGhlaWdodD0iNTkiIHZpZXdCb3g9IjAgMCA1OCA1OSIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHBhdGggaWQ9IlJlY3RhbmdsZSAyNTgyIiBkPSJNMC41MDEzNDMgMTkuOTU3NkMwLjUwMTM0MyAxNi40NzU0IDAuNTAxNzU4IDEzLjg5NDkgMC42NzM5MjMgMTEuODU0N0MwLjg0NTU3MSA5LjgyMDY2IDEuMTg1MjUgOC4zNjkyOCAxLjgzODA0IDcuMTM2MzlDMi45MTY3NCA1LjA5OTA3IDQuNTgyODQgMy40MzI5NyA2LjYyMDE1IDIuMzU0MjdDNy44NTMwNCAxLjcwMTQ5IDkuMzA0NDIgMS4zNjE4MSAxMS4zMzg1IDEuMTkwMTZDMTMuMzc4NyAxLjAxNzk5IDE1Ljk1OTIgMS4wMTc1OCAxOS40NDEzIDEuMDE3NThMMzguNTYxMyAxLjAxNzU4QzQyLjA0MzUgMS4wMTc1OCA0NC42MjQgMS4wMTc5OSA0Ni42NjQyIDEuMTkwMTZDNDguNjk4MyAxLjM2MTgxIDUwLjE0OTYgMS43MDE0OSA1MS4zODI1IDIuMzU0MjdDNTMuNDE5OSAzLjQzMjk3IDU1LjA4NTkgNS4wOTkwNyA1Ni4xNjQ3IDcuMTM2MzlDNTYuODE3NCA4LjM2OTI4IDU3LjE1NzEgOS44MjA2NiA1Ny4zMjg4IDExLjg1NDdDNTcuNTAwOSAxMy44OTQ5IDU3LjUwMTMgMTYuNDc1NCA1Ny41MDEzIDE5Ljk1NzZWMzkuMDc3NkM1Ny41MDEzIDQyLjU1OTcgNTcuNTAwOSA0NS4xNDAyIDU3LjMyODggNDcuMTgwNEM1Ny4xNTcxIDQ5LjIxNDUgNTYuODE3NCA1MC42NjU5IDU2LjE2NDcgNTEuODk4OEM1NS4wODU5IDUzLjkzNjEgNTMuNDE5OSA1NS42MDIyIDUxLjM4MjUgNTYuNjgwOUM1MC4xNDk2IDU3LjMzMzcgNDguNjk4MyA1Ny42NzM0IDQ2LjY2NDIgNTcuODQ1QzQ0LjYyNCA1OC4wMTcyIDQyLjA0MzUgNTguMDE3NiAzOC41NjEzIDU4LjAxNzZIMTkuNDQxM0MxNS45NTkyIDU4LjAxNzYgMTMuMzc4NyA1OC4wMTcyIDExLjMzODUgNTcuODQ1QzkuMzA0NDIgNTcuNjczNCA3Ljg1MzA0IDU3LjMzMzcgNi42MjAxNSA1Ni42ODA5QzQuNTgyODQgNTUuNjAyMiAyLjkxNjc0IDUzLjkzNjEgMS44MzgwNCA1MS44OTg4QzEuMTg1MjUgNTAuNjY1OSAwLjg0NTU3MSA0OS4yMTQ1IDAuNjczOTIzIDQ3LjE4MDRDMC41MDE3NTggNDUuMTQwMiAwLjUwMTM0MyA0Mi41NTk3IDAuNTAxMzQzIDM5LjA3NzZMMC41MDEzNDMgMTkuOTU3NloiIGZpbGw9IiNERTU4MzMiIHN0cm9rZT0idXJsKCNwYWludDBfbGluZWFyXzE0OTJfMjc5OCkiLz4KPGRlZnM+CjxsaW5lYXJHcmFkaWVudCBpZD0icGFpbnQwX2xpbmVhcl8xNDkyXzI3OTgiIHgxPSIxMy4xMDA1IiB5MT0iNTEuNDAxMiIgeDI9IjY3LjAyNjUiIHkyPSIxLjA2NTc3IiBncmFkaWVudFVuaXRzPSJ1c2VyU3BhY2VPblVzZSI+CjxzdG9wIHN0b3AtY29sb3I9IiNDODM5MTEiLz4KPHN0b3Agb2Zmc2V0PSIxIiBzdG9wLWNvbG9yPSIjRkVDNEI0IiBzdG9wLW9wYWNpdHk9IjAuNCIvPgo8L2xpbmVhckdyYWRpZW50Pgo8L2RlZnM+Cjwvc3ZnPgo=)![](data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjgiIGhlaWdodD0iMjgiIHZpZXdCb3g9IjAgMCAyOCAyOCIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0xNCAyOEMyMS43MzIgMjggMjggMjEuNzMyIDI4IDE0QzI4IDYuMjY4MDEgMjEuNzMyIDAgMTQgMEM2LjI2ODAxIDAgMCA2LjI2ODAxIDAgMTRDMCAyMS43MzIgNi4yNjgwMSAyOCAxNCAyOFoiIGZpbGw9IiNERTU4MzMiLz4KPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0xNy4xNDEyIDI2LjQ5ODFDMTYuNTU1NiAyNS4zNTk0IDE1Ljk5NTYgMjQuMzExNyAxNS42NDc5IDIzLjYxODdDMTQuNzIzOSAyMS43Njg0IDEzLjc5NTIgMTkuMTU5NyAxNC4yMTc2IDE3LjQ3NzRDMTQuMjk0NiAxNy4xNzE3IDEzLjM0NzIgNi4xNTg0MSAxMi42Nzc2IDUuODAzNzRDMTEuOTMzMiA1LjQwNzA3IDExLjAxNzQgNC43Nzc4MyAxMC4xNzk4IDQuNjM3ODNDOS43NTQ3NSA0LjU2OTgzIDkuMTk3NSA0LjYwMjA0IDguNzYxODcgNC42NjA3MUM4LjY4NDQ0IDQuNjcxMTQgOC42ODEyNCA0LjgxMDMgOC43NTUyNCA0LjgzNTM3QzkuMDQxMjMgNC45MzIyNyA5LjM4ODUxIDUuMTAwNSA5LjU5MzE3IDUuMzU0ODdDOS42MzE5MSA1LjQwMzAxIDkuNTc5OTIgNS40Nzg3NCA5LjUxODE2IDUuNDgxMDJDOS4zMjUyNSA1LjQ4ODE3IDguOTc1MjYgNS41NjkwNSA4LjUxMzUgNS45NjEyNUM4LjQ2MDA5IDYuMDA2NjEgOC41MDQ0NSA2LjA5MDg0IDguNTczMTkgNi4wNzcyNEM5LjU2NTU2IDUuODgwOTEgMTAuNTc5IDUuOTc3NjQgMTEuMTc2MyA2LjUyMDUxQzExLjIxNTEgNi41NTU3NCAxMS4xOTQ4IDYuNjE4OTUgMTEuMTQ0MyA2LjYzMjY4QzUuOTYxMTUgOC4wNDEyMiA2Ljk4NzE5IDEyLjU0OTkgOC4zNjY5OSAxOC4wODI5QzkuNjY3MDcgMjMuMjk2MiAxMC4yODM3IDI1LjM1MjMgMTAuMzU4IDI1LjU5NjRDMTAuMzYzIDI1LjYxMjggMTAuMzcyMiAyNS42MjU0IDEwLjM4NzIgMjUuNjMzN0MxMS40NTU1IDI2LjIxOTkgMTcuMDcyNSAyNi41NzU5IDE2LjgzNDMgMjUuOTA0NkwxNy4xNDEyIDI2LjQ5ODFaIiBmaWxsPSIjREREREREIi8+CjxwYXRoIGZpbGwtcnVsZT0iZXZlbm9kZCIgY2xpcC1ydWxlPSJldmVub2RkIiBkPSJNMjYuODMzMyAxMy45OTk0QzI2LjgzMzMgMjEuMDg3IDIxLjA4NzcgMjYuODMyNyAxNCAyNi44MzI3QzYuOTEyMzYgMjYuODMyNyAxLjE2NjY5IDIxLjA4NyAxLjE2NjY5IDEzLjk5OTRDMS4xNjY2OSA2LjkxMTcgNi45MTIzNiAxLjE2NjAyIDE0IDEuMTY2MDJDMjEuMDg3NyAxLjE2NjAyIDI2LjgzMzMgNi45MTE3IDI2LjgzMzMgMTMuOTk5NFpNMTAuOTk4OSAyNS4zNTE4QzEwLjY3NDMgMjQuMzU1NCA5LjY4NDU4IDIxLjIyMzMgOC42NzI4NyAxNy4wNzYxQzguNjM5OTMgMTYuOTQxMSA4LjYwNzA0IDE2LjgwNjcgOC41NzQzMSAxNi42NzNDNy4zODc3MyAxMS44MjY0IDYuNDE4NTggNy44Njc4IDExLjczMDIgNi42MjQyQzExLjc3ODggNi42MTI4MyAxMS44MDI1IDYuNTU0NzcgMTEuNzcwNCA2LjUxNjYzQzExLjE2MSA1Ljc5MzY0IDEwLjAxOTMgNS41NTY2NyA4LjU3NTY0IDYuMDU0NjlDOC41MTY0NCA2LjA3NTEyIDguNDY1MDMgNi4wMTU0MiA4LjUwMTgxIDUuOTY0NzJDOC43ODQ4OSA1LjU3NDUzIDkuMzM4MDkgNS4yNzQ1MiA5LjYxMTIgNS4xNDNDOS42Njc2NSA1LjExNTgyIDkuNjY0MjIgNS4wMzMxNiA5LjYwNDQyIDUuMDE0NDVDOS40MjU3OCA0Ljk1ODUzIDkuMTIxNjMgNC44NzI5NSA4Ljc3OTQ5IDQuODE3NzNDOC42OTg1NCA0LjgwNDY3IDguNjkxMjQgNC42NjU5NCA4Ljc3MjUgNC42NTUwMUMxMC44MTkxIDQuMzc5NjYgMTIuOTU2MiA0Ljk5NDEgMTQuMDI4NiA2LjM0NTAxQzE0LjAzODcgNi4zNTc3MyAxNC4wNTMzIDYuMzY2NjIgMTQuMDY5MiA2LjM3MDAzQzE3Ljk5NjQgNy4yMTM0MSAxOC4yNzc2IDEzLjQyMTQgMTcuODI1MiAxMy43MDQyQzE3LjczNiAxMy43NTk5IDE3LjQ1MDEgMTMuNzI3OSAxNy4wNzMxIDEzLjY4NThDMTUuNTQ1IDEzLjUxNDggMTIuNTE5MiAxMy4xNzYyIDE1LjAxNjUgMTcuODMwNUMxNS4wNDEyIDE3Ljg3NjQgMTUuMDA4NSAxNy45MzczIDE0Ljk1NyAxNy45NDUzQzEzLjU0MzIgMTguMTY1MSAxNS4zMTcyIDIyLjUzMjggMTYuNjQ1MyAyNS40Mzk3QzIxLjg1NSAyNC4yNCAyNS43Mzk2IDE5LjU3MzMgMjUuNzM5NiAxMy45OTk0QzI1LjczOTYgNy41MTU3NiAyMC40ODM2IDIuMjU5NzcgMTQgMi4yNTk3N0M3LjUxNjQzIDIuMjU5NzcgMi4yNjA0NCA3LjUxNTc2IDIuMjYwNDQgMTMuOTk5NEMyLjI2MDQ0IDE5LjQ0NTYgNS45NjkxNyAyNC4wMjU3IDEwLjk5ODkgMjUuMzUxOFoiIGZpbGw9IndoaXRlIi8+CjxwYXRoIGZpbGwtcnVsZT0iZXZlbm9kZCIgY2xpcC1ydWxlPSJldmVub2RkIiBkPSJNMTguNDM2MyAxOS44NDAyQzE4LjEzNzQgMTkuNzAxNiAxNi45ODc4IDIwLjUyNiAxNi4yMjQ5IDIxLjE1ODhDMTYuMDY1NCAyMC45MzMyIDE1Ljc2NDggMjAuNzY5MiAxNS4wODY2IDIwLjg4NzJDMTQuNDkzIDIwLjk5MDQgMTQuMTY1MyAyMS4xMzM1IDE0LjAxOTEgMjEuMzgwMkMxMy4wODIyIDIxLjAyNSAxMS41MDYxIDIwLjQ3NjkgMTEuMTI1MyAyMS4wMDYzQzEwLjcwOTEgMjEuNTg1IDExLjIyOTMgMjQuMzIyNiAxMS43ODIxIDI0LjY3ODFDMTIuMDcwOCAyNC44NjM3IDEzLjQ1MTMgMjMuOTc2MyAxNC4xNzIzIDIzLjM2NDNDMTQuMjg4NiAyMy41MjgyIDE0LjQ3NTggMjMuNjIyIDE0Ljg2MDggMjMuNjEzMUMxNS40NDMgMjMuNTk5NiAxNi4zODc0IDIzLjQ2NDEgMTYuNTMzOSAyMy4xOTNDMTYuNTQyOCAyMy4xNzY1IDE2LjU1MDUgMjMuMTU3IDE2LjU1NyAyMy4xMzQ4QzE3LjI5OCAyMy40MTE4IDE4LjYwMjIgMjMuNzA0OSAxOC44OTM2IDIzLjY2MTFDMTkuNjUyOCAyMy41NDcxIDE4Ljc4NzggMjAuMDAzIDE4LjQzNjMgMTkuODQwMloiIGZpbGw9IiMzQ0E4MkIiLz4KPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0xNi4yOTQ3IDIxLjIzOTJDMTYuMzI2MiAyMS4yOTUyIDE2LjM1MTUgMjEuMzU0NCAxNi4zNzMgMjEuNDE0NUMxNi40Nzg2IDIxLjcwOTkgMTYuNjUwNyAyMi42NDk4IDE2LjUyMDUgMjIuODgxOUMxNi4zOTA0IDIzLjExNDEgMTUuNTQ1MSAyMy4yMjYyIDE1LjAyMzYgMjMuMjM1MkMxNC41MDIxIDIzLjI0NDIgMTQuMzg0NyAyMy4wNTM1IDE0LjI3OTEgMjIuNzU4QzE0LjE5NDUgMjIuNTIxNyAxNC4xNTI5IDIxLjk2NiAxNC4xNTQgMjEuNjQ3OEMxNC4xMzI2IDIxLjE3NTkgMTQuMzA1IDIxLjAxIDE1LjEwMiAyMC44ODFDMTUuNjkxOCAyMC43ODU2IDE2LjAwMzcgMjAuODk2NiAxNi4xODM3IDIxLjA4NjVDMTcuMDIxIDIwLjQ2MTYgMTguNDE4MSAxOS41Nzk2IDE4LjU1NDQgMTkuNzQwOEMxOS4yMzM4IDIwLjU0NDUgMTkuMzE5NyAyMi40NTc4IDE5LjE3MjYgMjMuMjI3NkMxOS4xMjQ1IDIzLjQ3OTIgMTYuODc0NyAyMi45NzgyIDE2Ljg3NDcgMjIuNzA2OEMxNi44NzQ3IDIxLjU3OTkgMTYuNTgyMiAyMS4yNzA4IDE2LjI5NDcgMjEuMjM5MlpNMTEuMzY2NCAyMC44ODczQzExLjU1MDYgMjAuNTk1NiAxMy4wNDQ5IDIwLjk1ODMgMTMuODY1MiAyMS4zMjMzQzEzLjg2NTIgMjEuMzIzMyAxMy42OTY2IDIyLjA4NyAxMy45NjUgMjIuOTg2N0MxNC4wNDM1IDIzLjI0OTkgMTIuMDc3OSAyNC40MjEyIDExLjgyMTMgMjQuMjE5N0MxMS41MjQ4IDIzLjk4NjggMTAuOTc5IDIxLjUwMDIgMTEuMzY2NCAyMC44ODczWiIgZmlsbD0iIzRDQkEzQyIvPgo8cGF0aCBmaWxsLXJ1bGU9ImV2ZW5vZGQiIGNsaXAtcnVsZT0iZXZlbm9kZCIgZD0iTTEyLjA5MDEgMTQuOTY0QzEyLjIxMSAxNC40MzgzIDEyLjc3NDIgMTMuNDQ3NiAxNC43ODUzIDEzLjQ3MTdDMTUuODAyIDEzLjQ2NzUgMTcuMDY1IDEzLjQ3MTMgMTcuOTAyMyAxMy4zNzYxQzE5LjE0NjkgMTMuMjM0OCAyMC4wNzA2IDEyLjkzMzggMjAuNjg2MiAxMi42OTkxQzIxLjU1NjggMTIuMzY3IDIxLjg2NTcgMTIuNDQxIDIxLjk3NCAxMi42Mzk4QzIyLjA5MzEgMTIuODU4MSAyMS45NTI4IDEzLjIzNTIgMjEuNjQ4NiAxMy41ODIzQzIxLjA2NzYgMTQuMjQ1MyAyMC4wMjMgMTQuNzU5IDE4LjE3ODIgMTQuOTExNUMxNi4zMzMzIDE1LjA2NCAxNS4xMTEyIDE0LjU2OTEgMTQuNTg1IDE1LjM3NDhDMTQuMzU4MSAxNS43MjIzIDE0LjUzMzYgMTYuNTQxMyAxNi4zMTc2IDE2Ljc5OTJDMTguNzI4NSAxNy4xNDcxIDIwLjcwODQgMTYuMzggMjAuOTUzMSAxNi44NDMzQzIxLjE5NzcgMTcuMzA2NiAxOS43ODg1IDE4LjI0OTMgMTcuMzczOCAxOC4yNjkxQzE0Ljk1OSAxOC4yODg4IDEzLjQ1MDggMTcuNDIzNiAxMi45MTU5IDE2Ljk5MzVDMTIuMjM3MyAxNi40NDc5IDExLjkzMzYgMTUuNjUyMiAxMi4wOTAxIDE0Ljk2NFoiIGZpbGw9IiNGRkNDMzMiLz4KPGcgb3BhY2l0eT0iMC44NSI+CjxwYXRoIGQ9Ik0xMC41ODE4IDEyLjg4MTNDMTEuMDcxMyAxMi44ODEzIDExLjQ2NzYgMTIuNDg1OSAxMS40Njc2IDExLjk5ODNDMTEuNDY3NiAxMS41MTA2IDExLjA3MDQgMTEuMTE1MiAxMC41ODE4IDExLjExNTJDMTAuMDkzMiAxMS4xMTUyIDkuNjk1OTcgMTEuNTEwNiA5LjY5NTk3IDExLjk5ODNDOS42OTU5NyAxMi40ODU5IDEwLjA5MzIgMTIuODgxMyAxMC41ODE4IDEyLjg4MTNaTTEwLjk3NzEgMTEuNDc3QzExLjEwMzkgMTEuNDc3IDExLjIwNjUgMTEuNTc5NiAxMS4yMDY1IDExLjcwNTVDMTEuMjA2NSAxMS44MzE0IDExLjEwMzkgMTEuOTMzOSAxMC45NzcxIDExLjkzMzlDMTAuODUwMyAxMS45MzM5IDEwLjc0NzggMTEuODMxNCAxMC43NDc4IDExLjcwNTVDMTAuNzQ4NyAxMS41Nzg3IDEwLjg1MTMgMTEuNDc3IDEwLjk3NzEgMTEuNDc3WiIgZmlsbD0iIzE0MzA3RSIvPgo8cGF0aCBkPSJNMTUuNzQyNiAxMS40NzU5QzE1Ljc0MjYgMTEuODkzNiAxNi4wODMgMTIuMjMzIDE2LjUwMjYgMTIuMjMzQzE2LjkyMjIgMTIuMjMzIDE3LjI2MjUgMTEuODkzNiAxNy4yNjI1IDExLjQ3NTlDMTcuMjYyNSAxMS4wNTgyIDE2LjkyMjIgMTAuNzE4OCAxNi41MDI2IDEwLjcxODhDMTYuMDgzIDEwLjcxODggMTUuNzQyNiAxMS4wNTgyIDE1Ljc0MjYgMTEuNDc1OVpNMTYuODQxMSAxMS4wMjkzQzE2Ljk0OTIgMTEuMDI5MyAxNy4wMzc4IDExLjExNjkgMTcuMDM3OCAxMS4yMjUxQzE3LjAzNzggMTEuMzMzMiAxNi45NTAyIDExLjQyMDkgMTYuODQxMSAxMS40MjA5QzE2LjczMjkgMTEuNDIwOSAxNi42NDQzIDExLjMzMzIgMTYuNjQ0MyAxMS4yMjUxQzE2LjY0NTMgMTEuMTE2OSAxNi43MzI5IDExLjAyOTMgMTYuODQxMSAxMS4wMjkzWiIgZmlsbD0iIzE0MzA3RSIvPgo8cGF0aCBkPSJNOS42NjIzNCA5LjQ5NjQzQzEwLjEzNyA5LjI5ODA5IDEwLjUxIDkuMzIzNzMgMTAuNzczNyA5LjM4NjExQzEwLjgyOTIgOS4zOTkyNCAxMC44Njc4IDkuMzM5NSAxMC44MjM0IDkuMzAzNjhDMTAuNjE4NyA5LjEzODU4IDEwLjE2MDYgOC45MzM2MyA5LjU2MzE3IDkuMTU2NDFDOS4wMzAyMiA5LjM1NTE0IDguNzc5MDEgOS43NjgwNCA4Ljc3NzUzIDEwLjAzOTVDOC43NzcxOCAxMC4xMDM1IDguOTA4OCAxMC4xMDg5IDguOTQyODQgMTAuMDU0OEM5LjAzNDgyIDkuOTA4NDMgOS4xODc3MiA5LjY5NDc4IDkuNjYyMzQgOS40OTY0M1oiIGZpbGw9IiMxNDMwN0UiLz4KPHBhdGggZD0iTTE2LjA4NzQgOC44MjQyMkMxNS41OTg2IDguODI0MjIgMTUuMjk5OSA4Ljk5NDQ1IDE1LjE2NTMgOS4yMTVDMTUuMTMzMyA5LjI2NzI3IDE1LjIxODIgOS4zMjU3MiAxNS4yNzIgOS4yOTY0N0MxNS40MzE3IDkuMjA5NjYgMTUuNjg1MiA5LjEwODk0IDE2LjA4NzQgOS4xMTQ2NkMxNi40ODk2IDkuMTIwMzggMTYuNzEyNyA5LjIyOTY0IDE2Ljg5ODQgOS4zMDk5NEMxNi45NDg3IDkuMzMxNjUgMTYuOTk3NyA5LjI3OTcyIDE2Ljk2NTMgOS4yMzU2MUMxNi44MDYyIDkuMDE4NzEgMTYuNTc2MiA4LjgyNDIyIDE2LjA4NzQgOC44MjQyMloiIGZpbGw9IiMxNDMwN0UiLz4KPC9nPgo8L3N2Zz4K)
            
            Try the **DuckDuckGo Browser.** Fast. Free. Private.
            
            [Download Browser](ref:4:https://duckduckgo.com/mac?origin=funnel_browser_searchresults__popover)
            
            Free
            
            <button ref="5">
            </button>
            <button id="header-hamburger-menu-btn" ref="6">
            
            Open menu
            
            </button>
            <section>
            <nav>
            
            - [All](ref:7:/?q=markdown+parser&ia=web)
            - [Images](ref:8:/?q=markdown+parser&ia=images&iax=images)
            - [Videos](ref:9:/?q=markdown+parser&ia=videos&iax=videos)
            - [News](ref:10:/?q=markdown+parser&ia=news&iar=news)
            - <div ref="11">More</div>[Maps](ref:12:/?q=markdown+parser&iaxm=maps)[Shopping](ref:13:/?q=markdown+parser&ia=shopping&iax=shopping)
            
            - [Search Assist](ref:14:/?q=markdown+parser&ia=web&assist=false)
            - <span ref="15">[Duck.ai](ref:16:/?q=markdown+parser&ia=chat)</span>
            - <span ref="17">
            
              <button ref="18">
            
              Search Settings[](ref:19:)
            
              </button>
            
              </span>
            
            </nav>
            </section>
            <nav>
            
            <div ref="20">Protected</div><div ref="21"><div ref="22"></div>[Germany]()</div><div ref="23">Safe search: moderate</div><div ref="24">Any time</div>
            
            </nav>
            <section>
            
            
            1. <article id="r1-0">
               <button type="button" aria-expanded="false" aria-haspopup="menu" ref="25">
               </button>
            
               [![](//external-content.duckduckgo.com/ip3/markdownviewer.org.ico)](ref:26:/?q=markdown%20parser+site:markdownviewer.org)
            
               markdownviewer.org
            
               <a href="https://markdownviewer.org/" ref="27">
            
               https://markdownviewer.org
            
               </a>
            
               ## [Markdown Viewer — Online Markdown Editor with Live Preview](ref:28:https://markdownviewer.org/)
            
               A free, privacy-first <b>markdown</b> viewer and editor with live preview, syntax highlighting for 190+ languages, LaTeX math, Mermaid diagrams, and export to HTML.
            
               </article>
            2. <article id="r1-1">
               <button type="button" aria-expanded="false" aria-haspopup="menu" ref="29">
               </button>
            
               [![](//external-content.duckduckgo.com/ip3/markdownlivepreview.com.ico)](ref:30:/?q=markdown%20parser+site:markdownlivepreview.com)
            
               Markdown Live Preview
            
               <a href="https://markdownlivepreview.com/" ref="31">
            
               https://markdownlivepreview.com
            
               </a>
            
               ## [Markdown Live Preview](ref:32:https://markdownlivepreview.com/)
            
               <b>Markdown</b> is a lightweight markup language with plain-text-formatting syntax, created in 2004 by John Gruber with Aaron Swartz. <b>Markdown</b> is often used to format readme files, for writing messages in online discussion forums, and to create rich text using a plain text editor.
            
               </article>
            
            3. ## [Videos for <b>markdown parser</b>](ref:33:/?q=markdown+parser&ia=videos&iax=videos)
            
               1. <a href="https://www.youtube.com/watch?v=bY2l_J4jOeM" ref="34">
                  <article>
            
                  ![](//external-content.duckduckgo.com/iu/?u=https%3A%2F%2Ftse1.mm.bing.net%2Fth%2Fid%2FOVP.qK2437vy2iVXmC8FIglc8QEsDh%3Fpid%3DApi&f=1)
            
                  11:08
            
                  ### Build Your Own Markdown Parser From Scratch (Tokenizer)
            
                  11mo 968 views YouTube
            
                  <button type="button" aria-label="menu" ref="35">
                  </button>
                  </article>
                  </a>
               2. <a href="https://www.youtube.com/watch?v=kxb_j75QSL4" ref="36">
                  <article>
            
                  ![](//external-content.duckduckgo.com/iu/?u=https%3A%2F%2Ftse2.mm.bing.net%2Fth%2Fid%2FOVP.Bu77KgbUnJvlj_OynIc8WAHgFo%3Fpid%3DApi&f=1)
            
                  1:03:57
            
                  ### Build a Markdown Parser in Haskell with Megaparsec | Complete Tutorial
            
                  2yr 2K views YouTube
            
                  <button type="button" aria-label="menu" ref="37">
                  </button>
                  </article>
                  </a>
               3. <a href="https://www.youtube.com/watch?v=fAIuXISBqSE" ref="38">
                  <article>
            
                  ![](//external-content.duckduckgo.com/iu/?u=https%3A%2F%2Ftse2.mm.bing.net%2Fth%2Fid%2FOVP.7BmcCuO7EQvK4HkVnmGL9AHgFo%3Fpid%3DApi&f=1)
            
                  4:25
            
                  ### dots.mocr: Document Parsing to Markdown and SVG
            
                  3mo 127 views YouTube
            
                  <button type="button" aria-label="menu" ref="39">
                  </button>
                  </article>
                  </a>
               4. <a href="https://www.youtube.com/watch?v=d0tK7HP4hpg" ref="40">
                  <article>
            
                  ![](//external-content.duckduckgo.com/iu/?u=https%3A%2F%2Ftse4.mm.bing.net%2Fth%2Fid%2FOVP.ZImxSd92Xqi1Qp8z9Wsj8wEsDh%3Fpid%3DApi&f=1)
            
                  0:19
            
                  ### Use Qwen Code to parse the PDF document into markdown content, and then translate it.
            
                  5mo 1K views YouTube
            
                  <button type="button" aria-label="menu" ref="41">
                  </button>
                  </article>
                  </a>
               5. <a href="https://www.youtube.com/watch?v=GXuJv2Ut0Og" ref="42">
                  <article>
            
                  ![](//external-content.duckduckgo.com/iu/?u=https%3A%2F%2Ftse2.mm.bing.net%2Fth%2Fid%2FOVP.xIXOiwRza8zDbBFh4kZswAEsDh%3Fpid%3DApi&f=1)
            
                  13:46
            
                  ### Markdown Tutorial for Beginners | Markdown Basics Explained | Markdown Crash Course | MD Guide 2026
            
                  4mo 137 views YouTube
            
                  <button type="button" aria-label="menu" ref="43">
                  </button>
                  </article>
                  </a>
               6. <a href="https://www.youtube.com/watch?v=LxeclcePg-c" ref="44">
                  <article>
            
                  ![](//external-content.duckduckgo.com/iu/?u=https%3A%2F%2Ftse4.mm.bing.net%2Fth%2Fid%2FOVP.nh4QRmLw7W8ti01lhSYeAAEsDh%3Fpid%3DApi&f=1)
            
                  5:58
            
                  ### Getting started with Markdown on GitHub
            
                  1mo 18K views YouTube
            
                  <button type="button" aria-label="menu" ref="45">
                  </button>
                  </article>
                  </a>
               7. <a href="https://www.youtube.com/watch?v=NX1Sht1tAA0" ref="46">
                  <article>
            
                  ![](//external-content.duckduckgo.com/iu/?u=https%3A%2F%2Ftse1.mm.bing.net%2Fth%2Fid%2FOVP.FCZCi9NxFmTafRmZQSe78gEsDh%3Fpid%3DApi&f=1)
            
                  23:39
            
                  ### Obsidian Markdown Made Ridiculously Simple
            
                  5mo 13K views YouTube
            
                  <button type="button" aria-label="menu" ref="47">
                  </button>
                  </article>
                  </a>
               8. <a href="https://www.youtube.com/watch?v=tKI9-eFe5YU" ref="48">
                  <article>
            
                  ![](//external-content.duckduckgo.com/iu/?u=https%3A%2F%2Ftse4.mm.bing.net%2Fth%2Fid%2FOVP.NMIyHaJ8cvaeWZhKZNMXRwEkII%3Fpid%3DApi&f=1)
            
                  0:14
            
                  ### Day 23: How To Build a Markdown Previewer Using HTML, CSS & JavaScript (For Beginner)
            
                  2mo 436 views YouTube
            
                  <button type="button" aria-label="menu" ref="49">
                  </button>
                  </article>
                  </a>
               9. <a href="https://www.youtube.com/watch?v=YAgjtZKLVKo" ref="50">
                  <article>
            
                  ![](//external-content.duckduckgo.com/iu/?u=https%3A%2F%2Ftse1.mm.bing.net%2Fth%2Fid%2FOVP.5Dajng69BBl95op6c6HaMAHgFo%3Fpid%3DApi&f=1)
            
                  12:58
            
                  ### Convert Any Document To LLM Knowledge with Docling & Ollama (100% Local) | PDF to Markdown Pipeline
            
                  5mo 12K views YouTube
            
                  <button type="button" aria-label="menu" ref="51">
                  </button>
                  </article>
                  </a>
               10. <a href="https://www.youtube.com/watch?v=rmLi4KdLF0Y" ref="52">
                   <article>
            
                   ![](//external-content.duckduckgo.com/iu/?u=https%3A%2F%2Ftse1.mm.bing.net%2Fth%2Fid%2FOVP.amT2cEUvU2o5hxZl-ED_XAHgFo%3Fpid%3DApi&f=1)
            
                   4:32
            
                   ### Markdown to HTML Made Simple in n8n!
            
                   10mo 12K views YouTube
            
                   <button type="button" aria-label="menu" ref="53">
                   </button>
                   </article>
                   </a>
            
               11. [More Videos](ref:54:)
            
               <button type="button" aria-label="Next page" ref="55">
               </button>
            
               [More Videos](ref:56:/?q=markdown+parser&ia=videos&iax=videos)Was this helpful?
            
               <button type="button" aria-label="Positive feedback" aria-expanded="false" ref="57">
               </button>
               <button type="button" aria-label="Negative feedback" aria-expanded="false" ref="58">
               </button>
            4. <article id="r1-2">
               <button type="button" aria-expanded="false" aria-haspopup="menu" ref="59">
               </button>
            
               [![](//external-content.duckduckgo.com/ip3/dillinger.io.ico)](ref:60:/?q=markdown%20parser+site:dillinger.io)
            
               Dillinger
            
               <a href="https://dillinger.io/" ref="61">
            
               https://dillinger.io
            
               </a>
            
               ## [Markdown Editor — Online, Free, with Live Preview | Dillinger](ref:62:https://dillinger.io/)
            
               Free online <b>Markdown</b> editor with live preview. Write, format, and export <b>Markdown</b> to HTML or PDF — sync to GitHub, Dropbox & Google Drive. No signup.
            
               </article>
            5. <article id="r1-3">
               <button type="button" aria-expanded="false" aria-haspopup="menu" ref="63">
               </button>
            
               [![](//external-content.duckduckgo.com/ip3/github.com.ico)](ref:64:/?q=markdown%20parser+site:github.com)
            
               Github
            
               <a href="https://github.com/markedjs/marked" ref="65">
            
               https://github.com › markedjs › marked
            
               </a>
            
               ## [GitHub - markedjs/marked: A markdown parser and compiler. Built for ...](ref:66:https://github.com/markedjs/marked)
            
               A <b>markdown</b> <b>parser</b> and compiler. Built for speed. Contribute to markedjs/marked development by creating an account on GitHub.
            
               </article>
            6. <article id="r1-4">
               <button type="button" aria-expanded="false" aria-haspopup="menu" ref="67">
               </button>
            
               [![](//external-content.duckduckgo.com/ip3/markdownviewer.pages.dev.ico)](ref:68:/?q=markdown%20parser+site:markdownviewer.pages.dev)
            
               markdownviewer.pages.dev
            
               <a href="https://markdownviewer.pages.dev/" ref="69">
            
               https://markdownviewer.pages.dev
            
               </a>
            
               ## [Markdown Viewer](ref:70:https://markdownviewer.pages.dev/)
            
               <b>Markdown</b> Viewer is a powerful GitHub-style <b>Markdown</b> rendering tool with live preview, LaTeX math, Mermaid diagrams, syntax highlighting, dark mode, and export options to PDF, HTML, and MD—all fully client-side and secure.
            
               </article>
            7. <article id="r1-5">
               <button type="button" aria-expanded="false" aria-haspopup="menu" ref="71">
               </button>
            
               [![](//external-content.duckduckgo.com/ip3/markdownonline.org.ico)](ref:72:/?q=markdown%20parser+site:markdownonline.org)
            
               markdownonline.org
            
               <a href="https://markdownonline.org/" ref="73">
            
               https://markdownonline.org
            
               </a>
            
               ## [Markdown Online - Viewer and Converter](ref:74:https://markdownonline.org/)
            
               Free browser-based <b>Markdown</b> viewer and converter. Edit, preview, and convert <b>Markdown</b> to HTML, PDF, tables, and more with local-first privacy.
            
               </article>
            8. <article id="r1-6">
               <button type="button" aria-expanded="false" aria-haspopup="menu" ref="75">
               </button>
            
               [![](//external-content.duckduckgo.com/ip3/onlinemarkdown.com.ico)](ref:76:/?q=markdown%20parser+site:onlinemarkdown.com)
            
               onlinemarkdown.com
            
               <a href="http://onlinemarkdown.com/" ref="77">
            
               onlinemarkdown.com
            
               </a>
            
               ## [Online Markdown Editor - Simple, Fast & Live Preview](ref:78:http://onlinemarkdown.com/)
            
               Write and preview <b>Markdown</b> instantly with our Online <b>Markdown</b> Editor. Supports GitHub Flavored <b>Markdown</b> (GFM), tables, code blocks, and Mermaid diagrams.
            
               </article>
            9. <article id="r1-7">
               <button type="button" aria-expanded="false" aria-haspopup="menu" ref="79">
               </button>
            
               [![](//external-content.duckduckgo.com/ip3/www.npmjs.com.ico)](ref:80:/?q=markdown%20parser+site:www.npmjs.com)
            
               npm
            
               <a href="https://www.npmjs.com/package/marked" ref="81">
            
               https://www.npmjs.com › package › marked
            
               </a>
            
               ## [Marked - npm](ref:82:https://www.npmjs.com/package/marked)
            
               A <b>markdown</b> <b>parser</b> built for speed. Latest version: 18.0.5, last published: 12 days ago. Start using marked in your project by running `npm i marked`. There are 13228 other projects in the npm registry using marked.
            
               </article>
            10. <article id="r1-8">
                <button type="button" aria-expanded="false" aria-haspopup="menu" ref="83">
                </button>
            
                [![](//external-content.duckduckgo.com/ip3/marked.js.org.ico)](ref:84:/?q=markdown%20parser+site:marked.js.org)
            
                Marked
            
                <a href="https://marked.js.org/" ref="85">
            
                https://marked.js.org
            
                </a>
            
                ## [Marked Documentation](ref:86:https://marked.js.org/)
            
                Marked is a fast and light-weight <b>markdown</b> <b>parser</b> that supports CommonMark and GitHub Flavored <b>Markdown</b>. It can be used as a command line interface, a browser extension, or a Node.js module with various options and extensions.
            
                </article>
            11. <article id="r1-9">
                <button type="button" aria-expanded="false" aria-haspopup="menu" ref="87">
                </button>
            
                [![](//external-content.duckduckgo.com/ip3/mdeditor.net.ico)](ref:88:/?q=markdown%20parser+site:mdeditor.net)
            
                mdeditor.net
            
                <a href="https://mdeditor.net/" ref="89">
            
                https://mdeditor.net
            
                </a>
            
                ## [Online Markdown Editor, Formatter & PDF Export - GitHub Compatible](ref:90:https://mdeditor.net/)
            
                mdeditor.net Online <b>Markdown</b> Editor with live preview, <b>Markdown</b> formatting, and export to PDF. Compatible with GitHub <b>markdown</b>. Easy to use and free!
            
                </article>
            
            <button id="more-results" type="button" ref="91">
            
            More results
            
            </button>
            </section>
            <section>
            
            
            1. <button aria-label="Copy" ref="92">
               </button>
               <button aria-label="Share" ref="93">
               </button>
               <button type="button" aria-label="Learn More About Assist" ref="94">
               </button>
               <button aria-label="Assist Settings" ref="95">
               </button>
            
               A markdown parser is a tool that converts Markdown text into HTML or other formats, allowing for easy formatting of plain text. Popular options include Marked, markdown-it, and Markdig, each offering various features and syntax support.
            
               <span ref="96">[![](//external-content.duckduckgo.com/ip3/chromewebstore.google.com.ico) Google](ref:97:https://chromewebstore.google.com/detail/markdown-viewer/ckkdlimhmcjmikdlpkmbgfkaikojcbjk)</span><span ref="98">[![](//external-content.duckduckgo.com/ip3/markdownlivepreview.com.ico) markdownlivepreview.com](ref:99:https://markdownlivepreview.com/)</span>
            
               <button aria-label="More" ref="100">
            
               More
            
               </button>
               <form action="/?q=markdown+parser&amp;ia=chat" method="GET">
               <button aria-label="Expand Duck.ai chat input" ref="101">
               </button>
               </form>
               <footer>
            
               Auto-generated based on listed sources. May contain inaccuracies.
            
               Was this helpful?
            
               <button type="button" aria-label="Positive feedback" aria-expanded="false" ref="102">
               </button>
               <button type="button" aria-label="Negative feedback" aria-expanded="false" ref="103">
               </button>
               </footer>
            
            2. Searches related to <b>markdown parser</b>
               1. [markdown <b>visualizer</b> <b>online</b> <b>free</b>​](ref:104:?q=markdown%20visualizer%20online%20free)
               2. [<b>generate</b> markdown <b>online</b>​](ref:105:?q=generate%20markdown%20online)
               3. [markdown <b>generator</b> <b>online</b>​](ref:106:?q=markdown%20generator%20online)
               4. [markdown <b>visualizer</b> <b>online</b>​](ref:107:?q=markdown%20visualizer%20online)
               5. [markdown <b>maker</b> <b>online</b>​](ref:108:?q=markdown%20maker%20online)
               1. [<b>validate</b> markdown <b>online</b>​](ref:109:?q=validate%20markdown%20online)
               2. [<b>free</b> <b>online</b> markdown <b>editor</b>​](ref:110:?q=free%20online%20markdown%20editor)
               3. [markdown <b>validator</b> <b>online</b>​](ref:111:?q=markdown%20validator%20online)
               4. [<b>open</b> markdown <b>online</b>​](ref:112:?q=open%20markdown%20online)
               5. [markdown <b>render</b> <b>online</b>​](ref:113:?q=markdown%20render%20online)
            
            </section>
            
            
            <button ref="114">
            
            Close menu
            
            </button>
            
            - Search
            - [Homepage](ref:115:https://start.duckduckgo.com/)
            - [Themes](ref:116:/settings#appearance)
            - [Settings](ref:117:/settings)
            
            - Share Feedback
            
            - <button type="button" aria-label="Positive feedback" aria-expanded="false" ref="118">
              </button>
              <button type="button" aria-label="Negative feedback" aria-expanded="false" ref="119">
              </button>
            
            - Downloads
            - [iOS Browser](ref:120:https://apps.apple.com/app/duckduckgo-private-browser/id663592361?platform=iphone&pt=866401&mt=8&ct=serp-atb-serp)
            - [Android Browser](ref:121:https://play.google.com/store/apps/details?id=com.duckduckgo.mobile.android&referrer=utm_campaign%3Dserp-atb-serp%26origin%3Dfunnel_playstore_searchresults)
            - [Mac Browser](ref:122:/mac?origin=funnel_browser_searchresults)
            - [Windows Browser](ref:123:/windows?origin=funnel_browser_searchresults)
            
            - More From DuckDuckGo
            
            - <button type="button" aria-label="Dismiss promotion" ref="124">
              </button>
            
              Upgrade to our Private Browser
            
              Fast. Secure. Free.
            
              [Install Mac Browser](ref:125:/mac)
            - [DuckDuckGo Subscription](ref:126:/pro?origin=funnel_pro_searchresults)
            - [Duck.ai](ref:127:https://duck.ai)
            - [Email Protection](ref:128:/email)
            - [Newsletter](ref:129:/newsletter)
            - [Blog](ref:130:/blog)
            - [Podcast](ref:131:https://insideduckduckgo.substack.com/?showWelcome=true)
            - [Collaborations](ref:132:/collaborations)
            
            - Learn More
            - [What’s New](ref:133:/updates)
            - [Compare Privacy](ref:134:/compare-privacy)
            - [About Our Browser](ref:135:/app)
            - [About DuckDuckGo](ref:136:/about)
            
            - Other Resources
            - [Help](ref:137:/duckduckgo-help-pages)
            - [Community](ref:138:https://www.reddit.com/r/duckduckgo/)
            - [Careers](ref:139:/careers)
            - [Privacy Policy](ref:140:/privacy)
            - [Terms of Service](ref:141:/terms)
            - [Press Kit](ref:142:/press)
            - [Advertise on Search](ref:143:/duckduckgo-help-pages/company/advertise-on-duckduckgo-search)
            
            <section>
            
            ### Introducing DuckDuckGo Collaborations
            
            Expertly crafted products for people who give a duck about privacy.
            
            [See More](ref:144:https://duckduckgo.com/collaborations)
            
            </section>
            <section>
            
            ### Learn More
            
            - [What's New](ref:145:https://duckduckgo.com/updates)
            - [About Browser](ref:146:https://duckduckgo.com/app)
            - [Compare Privacy](ref:147:https://duckduckgo.com/compare-privacy)
            - [Help Pages](ref:148:https://duckduckgo.com/duckduckgo-help-pages/)
            
            ### Get More
            
            - [Subscription](ref:149:https://duckduckgo.com/pro)
            - [Email Protection](ref:150:https://duckduckgo.com/email)
            - [Podcast](ref:151:https://insideduckduckgo.substack.com/?showWelcome=true) & [Newsletter](ref:152:https://duckduckgo.com/newsletter)
            - [Collaborations](ref:153:https://duckduckgo.com/collaborations)
            
            </section>
            <button type="button" ref="154">
            
            Share Feedback
            
            </button>
        """.trimIndent()
    }

    @Test
    fun `should round-trip the rendered Markdown with the captured divergences`() = runTest {
        // given — the Markdown the pipeline produces for the DuckDuckGo SERP dump
        val markdown = dumpFlow(DumpFixtures.serpDuckduckgo).transformHtmlToMarkdown().renderMarkdown()

        // when
        val reparsed = flowOf(markdown).parse()

        // then — re-parsing does NOT reproduce the pipeline Markdown verbatim. The
        // residual divergence here is ATX headings nested inside list items: on re-parse those indented `#` runs
        // are no longer recognised as block constructs, so the renderer escapes
        // them (`\\##`), plus a blank-line shift between list items.
        // Asserting the exact diff pins it down: any new round-trip difference (a
        // regression) changes this message and fails the test.
        try {
            reparsed.renderMarkdown() sameAs markdown
            error("expected the round-trip to diverge")
        } catch (e: AssertionError) {
            e.message sameAs """
                --- expected
                +++ actual
                @@ -43,7 +43,6 @@
                 - [Videos](ref:9:/?q=markdown+parser&ia=videos&iax=videos)
                 - [News](ref:10:/?q=markdown+parser&ia=news&iar=news)
                 - <div ref="11">More</div>[Maps](ref:12:/?q=markdown+parser&iaxm=maps)[Shopping](ref:13:/?q=markdown+parser&ia=shopping&iax=shopping)
                -
                 - [Search Assist](ref:14:/?q=markdown+parser&ia=web&assist=false)
                 - <span ref="15">[Duck.ai](ref:16:/?q=markdown+parser&ia=chat)</span>
                 - <span ref="17">
                @@ -80,7 +79,7 @@
                 
                    </a>
                 
                -   ## [Markdown Viewer — Online Markdown Editor with Live Preview](ref:28:https://markdownviewer.org/)
                +   \## [Markdown Viewer — Online Markdown Editor with Live Preview](ref:28:https://markdownviewer.org/)
                 
                    A free, privacy-first <b>markdown</b> viewer and editor with live preview, syntax highlighting for 190+ languages, LaTeX math, Mermaid diagrams, and export to HTML.
                 
                @@ -99,7 +98,7 @@
                 
                    </a>
                 
                -   ## [Markdown Live Preview](ref:32:https://markdownlivepreview.com/)
                +   \## [Markdown Live Preview](ref:32:https://markdownlivepreview.com/)
                 
                    <b>Markdown</b> is a lightweight markup language with plain-text-formatting syntax, created in 2004 by John Gruber with Aaron Swartz. <b>Markdown</b> is often used to format readme files, for writing messages in online discussion forums, and to create rich text using a plain text editor.
                 
                @@ -114,7 +113,7 @@
                 
                       11:08
                 
                -      ### Build Your Own Markdown Parser From Scratch (Tokenizer)
                +      \### Build Your Own Markdown Parser From Scratch (Tokenizer)
                 
                       11mo 968 views YouTube
                 
                @@ -129,7 +128,7 @@
                 
                       1:03:57
                 
                -      ### Build a Markdown Parser in Haskell with Megaparsec | Complete Tutorial
                +      \### Build a Markdown Parser in Haskell with Megaparsec | Complete Tutorial
                 
                       2yr 2K views YouTube
                 
                @@ -144,7 +143,7 @@
                 
                       4:25
                 
                -      ### dots.mocr: Document Parsing to Markdown and SVG
                +      \### dots.mocr: Document Parsing to Markdown and SVG
                 
                       3mo 127 views YouTube
                 
                @@ -159,7 +158,7 @@
                 
                       0:19
                 
                -      ### Use Qwen Code to parse the PDF document into markdown content, and then translate it.
                +      \### Use Qwen Code to parse the PDF document into markdown content, and then translate it.
                 
                       5mo 1K views YouTube
                 
                @@ -174,7 +173,7 @@
                 
                       13:46
                 
                -      ### Markdown Tutorial for Beginners | Markdown Basics Explained | Markdown Crash Course | MD Guide 2026
                +      \### Markdown Tutorial for Beginners | Markdown Basics Explained | Markdown Crash Course | MD Guide 2026
                 
                       4mo 137 views YouTube
                 
                @@ -189,7 +188,7 @@
                 
                       5:58
                 
                -      ### Getting started with Markdown on GitHub
                +      \### Getting started with Markdown on GitHub
                 
                       1mo 18K views YouTube
                 
                @@ -204,7 +203,7 @@
                 
                       23:39
                 
                -      ### Obsidian Markdown Made Ridiculously Simple
                +      \### Obsidian Markdown Made Ridiculously Simple
                 
                       5mo 13K views YouTube
                 
                @@ -219,7 +218,7 @@
                 
                       0:14
                 
                -      ### Day 23: How To Build a Markdown Previewer Using HTML, CSS & JavaScript (For Beginner)
                +      \### Day 23: How To Build a Markdown Previewer Using HTML, CSS & JavaScript (For Beginner)
                 
                       2mo 436 views YouTube
                 
                @@ -234,7 +233,7 @@
                 
                       12:58
                 
                -      ### Convert Any Document To LLM Knowledge with Docling & Ollama (100% Local) | PDF to Markdown Pipeline
                +      \### Convert Any Document To LLM Knowledge with Docling & Ollama (100% Local) | PDF to Markdown Pipeline
                 
                       5mo 12K views YouTube
                 
                @@ -249,7 +248,7 @@
                 
                        4:32
                 
                -       ### Markdown to HTML Made Simple in n8n!
                +       \### Markdown to HTML Made Simple in n8n!
                 
                        10mo 12K views YouTube
                 
                @@ -283,7 +282,7 @@
                 
                    </a>
                 
                -   ## [Markdown Editor — Online, Free, with Live Preview | Dillinger](ref:62:https://dillinger.io/)
                +   \## [Markdown Editor — Online, Free, with Live Preview | Dillinger](ref:62:https://dillinger.io/)
                 
                    Free online <b>Markdown</b> editor with live preview. Write, format, and export <b>Markdown</b> to HTML or PDF — sync to GitHub, Dropbox & Google Drive. No signup.
                 
                @@ -302,7 +301,7 @@
                 
                    </a>
                 
                -   ## [GitHub - markedjs/marked: A markdown parser and compiler. Built for ...](ref:66:https://github.com/markedjs/marked)
                +   \## [GitHub - markedjs/marked: A markdown parser and compiler. Built for ...](ref:66:https://github.com/markedjs/marked)
                 
                    A <b>markdown</b> <b>parser</b> and compiler. Built for speed. Contribute to markedjs/marked development by creating an account on GitHub.
                 
                @@ -321,7 +320,7 @@
                 
                    </a>
                 
                -   ## [Markdown Viewer](ref:70:https://markdownviewer.pages.dev/)
                +   \## [Markdown Viewer](ref:70:https://markdownviewer.pages.dev/)
                 
                    <b>Markdown</b> Viewer is a powerful GitHub-style <b>Markdown</b> rendering tool with live preview, LaTeX math, Mermaid diagrams, syntax highlighting, dark mode, and export options to PDF, HTML, and MD—all fully client-side and secure.
                 
                @@ -340,7 +339,7 @@
                 
                    </a>
                 
                -   ## [Markdown Online - Viewer and Converter](ref:74:https://markdownonline.org/)
                +   \## [Markdown Online - Viewer and Converter](ref:74:https://markdownonline.org/)
                 
                    Free browser-based <b>Markdown</b> viewer and converter. Edit, preview, and convert <b>Markdown</b> to HTML, PDF, tables, and more with local-first privacy.
                 
                @@ -359,7 +358,7 @@
                 
                    </a>
                 
                -   ## [Online Markdown Editor - Simple, Fast & Live Preview](ref:78:http://onlinemarkdown.com/)
                +   \## [Online Markdown Editor - Simple, Fast & Live Preview](ref:78:http://onlinemarkdown.com/)
                 
                    Write and preview <b>Markdown</b> instantly with our Online <b>Markdown</b> Editor. Supports GitHub Flavored <b>Markdown</b> (GFM), tables, code blocks, and Mermaid diagrams.
                 
                @@ -378,7 +377,7 @@
                 
                    </a>
                 
                -   ## [Marked - npm](ref:82:https://www.npmjs.com/package/marked)
                +   \## [Marked - npm](ref:82:https://www.npmjs.com/package/marked)
                 
                    A <b>markdown</b> <b>parser</b> built for speed. Latest version: 18.0.5, last published: 12 days ago. Start using marked in your project by running `npm i marked`. There are 13228 other projects in the npm registry using marked.
                 
                @@ -397,7 +396,7 @@
                 
                     </a>
                 
                -    ## [Marked Documentation](ref:86:https://marked.js.org/)
                +    \## [Marked Documentation](ref:86:https://marked.js.org/)
                 
                     Marked is a fast and light-weight <b>markdown</b> <b>parser</b> that supports CommonMark and GitHub Flavored <b>Markdown</b>. It can be used as a command line interface, a browser extension, or a Node.js module with various options and extensions.
                 
                @@ -416,7 +415,7 @@
                 
                     </a>
                 
                -    ## [Online Markdown Editor, Formatter & PDF Export - GitHub Compatible](ref:90:https://mdeditor.net/)
                +    \## [Online Markdown Editor, Formatter & PDF Export - GitHub Compatible](ref:90:https://mdeditor.net/)
                 
                     mdeditor.net Online <b>Markdown</b> Editor with live preview, <b>Markdown</b> formatting, and export to PDF. Compatible with GitHub <b>markdown</b>. Easy to use and free!
                 
                @@ -471,15 +470,13 @@
                    3. [markdown <b>generator</b> <b>online</b>​](ref:106:?q=markdown%20generator%20online)
                    4. [markdown <b>visualizer</b> <b>online</b>​](ref:107:?q=markdown%20visualizer%20online)
                    5. [markdown <b>maker</b> <b>online</b>​](ref:108:?q=markdown%20maker%20online)
                -   1. [<b>validate</b> markdown <b>online</b>​](ref:109:?q=validate%20markdown%20online)
                -   2. [<b>free</b> <b>online</b> markdown <b>editor</b>​](ref:110:?q=free%20online%20markdown%20editor)
                -   3. [markdown <b>validator</b> <b>online</b>​](ref:111:?q=markdown%20validator%20online)
                -   4. [<b>open</b> markdown <b>online</b>​](ref:112:?q=open%20markdown%20online)
                -   5. [markdown <b>render</b> <b>online</b>​](ref:113:?q=markdown%20render%20online)
                +   6. [<b>validate</b> markdown <b>online</b>​](ref:109:?q=validate%20markdown%20online)
                +   7. [<b>free</b> <b>online</b> markdown <b>editor</b>​](ref:110:?q=free%20online%20markdown%20editor)
                +   8. [markdown <b>validator</b> <b>online</b>​](ref:111:?q=markdown%20validator%20online)
                +   9. [<b>open</b> markdown <b>online</b>​](ref:112:?q=open%20markdown%20online)
                +   10. [markdown <b>render</b> <b>online</b>​](ref:113:?q=markdown%20render%20online)
                 
                 </section>
                -
                -
                 <button ref="114">
                 
                 Close menu
                @@ -490,7 +487,6 @@
                 - [Homepage](ref:115:https://start.duckduckgo.com/)
                 - [Themes](ref:116:/settings#appearance)
                 - [Settings](ref:117:/settings)
                -
                 - Share Feedback
                 
                 - <button type="button" aria-label="Positive feedback" aria-expanded="false" ref="118">
                @@ -503,7 +499,6 @@
                 - [Android Browser](ref:121:https://play.google.com/store/apps/details?id=com.duckduckgo.mobile.android&referrer=utm_campaign%3Dserp-atb-serp%26origin%3Dfunnel_playstore_searchresults)
                 - [Mac Browser](ref:122:/mac?origin=funnel_browser_searchresults)
                 - [Windows Browser](ref:123:/windows?origin=funnel_browser_searchresults)
                -
                 - More From DuckDuckGo
                 
                 - <button type="button" aria-label="Dismiss promotion" ref="124">
                @@ -521,13 +516,11 @@
                 - [Blog](ref:130:/blog)
                 - [Podcast](ref:131:https://insideduckduckgo.substack.com/?showWelcome=true)
                 - [Collaborations](ref:132:/collaborations)
                -
                 - Learn More
                 - [What’s New](ref:133:/updates)
                 - [Compare Privacy](ref:134:/compare-privacy)
                 - [About Our Browser](ref:135:/app)
                 - [About DuckDuckGo](ref:136:/about)
                -
                 - Other Resources
                 - [Help](ref:137:/duckduckgo-help-pages)
                 - [Community](ref:138:https://www.reddit.com/r/duckduckgo/)
                
            """.trimIndent()
        }
    }

}
