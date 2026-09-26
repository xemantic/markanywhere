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

/**
 * True when [line] is a YAML block mapping key line: an identifier-shaped
 * key (letters, digits, `_`, `-`, `.`, starting with a letter or `_`) or a
 * quoted one, followed by `:` and whitespace or the end of the line.
 *
 * This is the discriminator the Markdown parser's front matter detection
 * applies to the line after an opening `---` — strict on purpose, so a
 * thematic break followed by prose, a heading, a `# comment`, a `- list`
 * or `http://…` never opens a front matter. [YamlWriter] writes every key
 * so that its line passes this check (quoting any key that is not
 * identifier-shaped, even where YAML would not require it), which is what
 * keeps a rendered front matter re-detectable whatever entry comes first.
 *
 * A manual scan rather than a regex: `\p{L}` classes are not portable
 * across the Kotlin/JS and Kotlin/Native regex engines.
 */
public fun isYamlKeyLine(line: String): Boolean {
    if (line.isEmpty()) return false
    val first = line[0]
    var i = if (first == '"' || first == '\'') {
        scanQuoted(line)?.second ?: return false
    } else {
        if (!isIdentifierKeyStart(first)) return false
        var j = 1
        while (j < line.length && isIdentifierKeyChar(line[j])) j++
        j
    }
    while (i < line.length && line[i] == ' ') i++
    return i < line.length && line.isMappingColonAt(i)
}

// A key [YamlWriter] may write plain: identifier-shaped, so it passes
// [isYamlKeyLine] — the two share these character rules.
internal fun isIdentifierKey(key: String): Boolean {
    if (key.isEmpty() || !isIdentifierKeyStart(key[0])) return false
    for (c in key) if (!isIdentifierKeyChar(c)) return false
    return true
}

private fun isIdentifierKeyStart(c: Char): Boolean = c.isLetter() || c == '_'

private fun isIdentifierKeyChar(c: Char): Boolean =
    c.isLetterOrDigit() || c == '_' || c == '-' || c == '.'
