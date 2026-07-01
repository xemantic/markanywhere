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

import com.xemantic.markanywhere.dump.SemanticEventDump
import com.xemantic.markanywhere.markanywhereJson
import com.xemantic.markanywhere.render.renderMarkdown
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Developer utility (run via the `renderDumpMarkdown` Gradle task, not a test)
 * that runs every [SemanticEventDump] JSON under `src/commonTest/dumps` through
 * the full HTML-to-Markdown pipeline and writes the result under
 * `build/renderedMarkdown` — in **both** ref modes per dump: `<name>.md`
 * ([RefMode.ENCODE], the default) and `<name>.strip.md` ([RefMode.STRIP]).
 *
 * So it is the easiest way to regenerate a dump test's golden after a pipeline
 * change: use `<name>.md` for an ENCODE-mode golden (e.g. `W3cValidatorRefsTest`,
 * `OpenjurTest`, the SERP dumps) and `<name>.strip.md` for a STRIP-mode golden
 * (e.g. `W3cValidatorNoRefsTest`).
 */
fun main() {
    val inputDir = File("src/commonTest/dumps")
    val outputDir = File("build/renderedMarkdown").apply { mkdirs() }
    val jsonFiles = inputDir.listFiles { f -> f.isFile && f.extension == "json" }
        ?.sortedBy { it.name }
        ?: emptyList()
    if (jsonFiles.isEmpty()) {
        println("No DOM dump *.json files found in ${inputDir.absolutePath}")
        return
    }
    jsonFiles.forEach { file ->
        val dump = markanywhereJson.decodeFromString<SemanticEventDump>(file.readText())
        val base = file.nameWithoutExtension
        runBlocking {
            for ((suffix, refMode) in listOf("" to RefMode.ENCODE, ".strip" to RefMode.STRIP)) {
                val markdown = dump.events.asFlow()
                    .transformHtmlToMarkdown(refMode = refMode)
                    .renderMarkdown()
                File(outputDir, "$base$suffix.md").writeText(markdown)
            }
        }
        println("Rendered ${file.name} (${dump.url}) -> $base.md + $base.strip.md")
    }
    println("Output written to ${outputDir.absolutePath}")
}