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

package com.xemantic.markanywhere.yaml

import com.xemantic.kotlin.core.text.joinToString
import com.xemantic.markanywhere.SemanticEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Parses a YAML document, arriving in arbitrary chunks, into a stream of
 * semantic events: top-level `entry` marks (attribute `key`) for a root
 * mapping, `item` marks for a root sequence — see [YamlParser] for the
 * vocabulary, the supported subset and the verbatim fallback.
 *
 * Line endings (`\r\n`, `\r`, `\n`) are all accepted. Each line is handed to
 * the parser as soon as its terminator arrives, so events stream while the
 * document is still being received.
 */
public fun Flow<String>.parseYaml(): Flow<SemanticEvent> = flow {
    val parser = YamlParser(this)
    val line = StringBuilder()
    var pendingCr = false
    collect { chunk ->
        for (c in chunk) {
            when (c) {
                '\n' -> if (pendingCr) {
                    pendingCr = false
                } else {
                    parser.line(line.toString())
                    line.clear()
                }
                '\r' -> {
                    parser.line(line.toString())
                    line.clear()
                    pendingCr = true
                }
                else -> {
                    pendingCr = false
                    line.append(c)
                }
            }
        }
    }
    if (line.isNotEmpty()) parser.line(line.toString())
    parser.finish()
}

/**
 * Writes a stream of `entry` / `item` events back as block-style YAML — see
 * [YamlWriter] for the quoting and layout rules. Any other mark is
 * transparent, so the children of a `frontmatter` mark render the same with
 * or without it.
 *
 * Every line is terminated, so a non-empty document ends with `\n` — the
 * trailing newline is significant (a `|+` block scalar keeps it), which is
 * why, unlike the Markdown and HTML renderers, this one never drops it.
 */
public fun Flow<SemanticEvent>.asYaml(): Flow<String> = flow {
    val buffer = StringBuilder()
    val writer = YamlWriter { buffer.append(it) }
    collect { event ->
        writer.collect(event)
        if (buffer.isNotEmpty()) {
            emit(buffer.toString())
            buffer.clear()
        }
    }
}

/** Collects [asYaml] into a single string. */
public suspend fun Flow<SemanticEvent>.renderYaml(): String = asYaml().joinToString()
