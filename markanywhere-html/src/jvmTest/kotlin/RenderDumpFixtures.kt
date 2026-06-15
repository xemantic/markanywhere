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
import com.xemantic.markanywhere.render.render
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Developer utility (run via the `renderDumpFixtures` Gradle task, not a test)
 * that renders every [SemanticEventDump] JSON under `src/commonTest/dumps` back
 * to pretty-printed HTML under `build/renderedDumps`.
 *
 * The captured events are the source of truth, but a raw event stream is hard to
 * read. This regenerates the human-readable HTML the events represent, so a
 * developer (or agent) can eyeball the input that produced a given Markdown
 * output without keeping the bloated original HTML around.
 */
fun main() {
    val inputDir = File("src/commonTest/dumps")
    val outputDir = File("build/renderedDumps").apply { mkdirs() }
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
        val html = runBlocking {
            dump.events.asFlow().dropHtmlStructuralWhitespace().render()
        }
        File(outputDir, "$base.html").writeText(html)
        println("Rendered ${file.name} (${dump.url}) -> $base.html")
    }
    println("Output written to ${outputDir.absolutePath}")
}