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

package com.xemantic.markanywhere.browse

import dev.kdriver.core.browser.Browser
import dev.kdriver.core.browser.createBrowser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * Builds a `file://` URL for a test fixture under `src/commonTest/html`.
 *
 * [SystemFileSystem.resolve] absolutizes a relative path against the
 * current working directory (the module's projectDir under Gradle) and
 * throws if it does not exist — so this both builds the `file://` URL and
 * asserts the fixture is present, cross-platform (no `java.io.File`).
 */
fun testPageUrl(
    file: String
): String {
    val htmlPath = SystemFileSystem.resolve(
        Path("src/commonTest/html/$file")
    )
    return "file://$htmlPath"
}

/**
 * Runs [block] with a fresh headless [Browser], ensuring the browser and its
 * coroutine scope are stopped and canceled once [block] completes or fails.
 *
 * `sandbox = false` because CI runs the build as root (inside the reusable
 * workflow container), and Chrome/Chromium refuses to start its sandbox as
 * root ("Possible cause: running as root. Use no_sandbox = true in that
 * case."). Disabling the sandbox is the standard choice for a throwaway
 * headless test browser and works identically locally and in CI.
 */
suspend fun <T> runInBrowser(
    block: suspend (Browser) -> T
): T = withContext(Dispatchers.Default) {
    val browserScope = CoroutineScope(Dispatchers.Default)
    val browser = createBrowser(browserScope, headless = true, sandbox = false)
    try {
        block(browser)
    } finally {
        browser.stop()
        browserScope.cancel()
    }
}