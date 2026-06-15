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

package com.xemantic.markanywhere.html

import com.xemantic.markanywhere.browse.PageSession
import com.xemantic.markanywhere.browse.waitUntilLoaded
import com.xemantic.markanywhere.markanywhereJson
import dev.kdriver.core.browser.createBrowser
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

/**
 * Captures a live page as a [com.xemantic.markanywhere.dump.SemanticEventDump]
 * fixture JSON — the DOM semantic event stream plus the Blink accessibility
 * tree, joined per element — written straight into this module's
 * `src/commonTest/dumps/` so it becomes a baked-in test fixture (see
 * `generateDumpFixtures` / `DumpFixtures`).
 *
 * ```
 * ./gradlew :markanywhere-html:capture --args="<url> [output.json] [--headless] [--wait=<seconds>]"
 * ```
 *
 * - the output path defaults to `src/commonTest/dumps/dump.json`; pass a
 *   second positional argument to name the fixture
 * - `--headless` runs the browser headless; the default is a visible window,
 *   since Blink's accessibility heuristics (e.g. layout-table detection) are
 *   font- and viewport-sensitive and should match what a real user sees
 * - `--wait=<seconds>` hard cap for the post-navigation settle wait
 *   ([waitUntilLoaded]); capture proceeds once the network and DOM go quiet,
 *   or this backstop elapses on a never-quiet page (default 15)
 */
fun main(args: Array<String>) {
    val options = args.filter { it.startsWith("--") }
    val positional = args.filterNot { it.startsWith("--") }
    val url = positional.getOrNull(0) ?: run {
        System.err.println(
            "usage: capture <url> [output.json] [--headless] [--wait=<seconds>]"
        )
        exitProcess(1)
    }
    val output = File(positional.getOrNull(1) ?: "src/commonTest/dumps/dump.json")
    val wait = options
        .firstOrNull { it.startsWith("--wait=") }
        ?.substringAfter('=')?.toIntOrNull()
        ?: 15
    runBlocking {
        val browser = createBrowser(this, headless = "--headless" in options)
        try {
            val tab = browser.get(url)
            tab.waitUntilLoaded(timeout = wait.seconds)
            val dump = PageSession(tab).dump()
            output.writeText(markanywhereJson.encodeToString(dump))
        } finally {
            browser.stop()
        }
    }
}
