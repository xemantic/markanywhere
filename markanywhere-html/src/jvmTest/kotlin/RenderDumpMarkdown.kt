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
 * `build/renderedMarkdown`.
 *
 * Mirrors the pipeline exercised by `HtmlToMarkdownTest`
 * ([transformHtmlToMarkdown] with defaults — icon resolution → simplification
 * renaming the dump ref to `ref` → empty-formatting cleanup →
 * structural-whitespace normalization → Markdown), so it is also the easiest
 * way to regenerate that test's golden output after a pipeline change.
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
        val markdown = runBlocking {
            dump.events.asFlow()
                .transformHtmlToMarkdown()
                .renderMarkdown()
        }
        File(outputDir, "$base.md").writeText(markdown)
        println("Rendered ${file.name} (${dump.url}) -> $base.md")
    }
    println("Output written to ${outputDir.absolutePath}")
}