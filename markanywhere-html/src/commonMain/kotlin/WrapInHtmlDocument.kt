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

import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.flow.semanticEvents
import kotlinx.coroutines.flow.Flow

/**
 * Wraps a semantic event stream (typically parsed Markdown) in an
 * `html`/`head`/`body` document structure.
 *
 * A **leading** `frontmatter` block (the untagged mark the parser emits for
 * YAML `---` / TOML `+++` front matter) feeds the `head` — the inverse of
 * [simplifyHtml]'s head-to-frontmatter extraction, so the two round-trip:
 *
 * - `title` becomes `<title>`
 * - `lang` becomes the `lang` attribute on `<html>`
 * - every other flat key becomes a void `<meta name content>`
 *
 * Only the flat `key: value` (YAML) / `key = "value"` (TOML) subset is
 * interpreted — exactly the shape [simplifyHtml] produces. Nested structures,
 * comments, and malformed lines are silently skipped; an unknown format or
 * a wholly unparseable body yields an empty `head` (never an error). A
 * `frontmatter` mark appearing anywhere past the first event is ordinary
 * content and flows into `body` verbatim.
 *
 * Only the frontmatter body is buffered (bounded); without one the document
 * opening is emitted on the first event and body content streams through
 * untouched. All synthetic marks are untagged, consistent with the parser's
 * `frontmatter` mark and [simplifyHtml] output. An empty input stream still
 * yields the full document skeleton.
 */
public fun Flow<SemanticEvent>.wrapInHtmlDocument(): Flow<SemanticEvent> = semanticEvents {

    var opened = false
    var collectingFrontmatter = false
    // Balances defensively against nested marks inside the frontmatter block
    // (the parser emits only text there, but a hand-built flow might not).
    var frontmatterDepth = 0
    var frontmatterFormat = ""
    val frontmatterBody = StringBuilder()

    // `head` and its subtree are lexically scoped, so the paired `"name" { }`
    // builder fits; `html` and `body` close only at end-of-stream, so their
    // unmark cannot come from a builder block — explicit mark/unmark instead.
    suspend fun openDocument(metadata: Map<String, String>) {
        opened = true
        mark(
            "html",
            attributes = metadata["lang"]
                ?.let { mapOf("lang" to it) }
                ?: emptyMap()
        )
        "head" {
            metadata["title"]?.let { title ->
                "title" {
                    +title
                }
            }
            for ((key, value) in metadata) {
                if (key == "title" || key == "lang") continue
                "meta"("name" to key, "content" to value) {}
            }
        }
        mark("body")
    }

    suspend fun openDocumentFromFrontmatter() {
        collectingFrontmatter = false
        openDocument(
            parseFlatFrontmatter(frontmatterBody.toString(), frontmatterFormat)
        )
    }

    collect { event ->
        when {
            collectingFrontmatter -> when (event) {
                is Text -> frontmatterBody.append(event.text)
                is Mark -> frontmatterDepth++
                is Unmark -> if (frontmatterDepth == 0) {
                    openDocumentFromFrontmatter()
                } else {
                    frontmatterDepth--
                }
            }
            !opened -> if (
                event is Mark && !event.isTagged && event.name == "frontmatter"
            ) {
                collectingFrontmatter = true
                frontmatterFormat = event["format"] ?: ""
            } else {
                openDocument(emptyMap())
                emit(event)
            }
            else -> emit(event)
        }
    }

    // An unclosed frontmatter at end of stream (broken upstream contract) is
    // still used; an empty stream yields the bare skeleton.
    if (collectingFrontmatter) openDocumentFromFrontmatter()
    if (!opened) openDocument(emptyMap())
    unmark("body")
    unmark("html")
}

private fun parseFlatFrontmatter(
    body: String,
    format: String
): Map<String, String> = when (format) {
    "yaml" -> parseFlatYaml(body)
    "toml" -> parseFlatToml(body)
    else -> emptyMap()
}

// Flat YAML subset: top-level `key: value` lines, the inverse of
// renderYamlFrontmatter in SimplifyHtml.kt. Comments, blank lines, indented
// (nested) lines, and keys without an inline scalar value are skipped.
private fun parseFlatYaml(body: String): Map<String, String> {
    val metadata = mutableMapOf<String, String>()
    for (line in body.lineSequence()) {
        if (line.isEmpty()) continue
        val first = line[0]
        if (first == ' ' || first == '\t' || first == '#') continue
        val (key, rest) = parseYamlKey(line) ?: continue
        val value = parseYamlValue(rest) ?: continue
        metadata[key] = value
    }
    return metadata
}

private val YAML_PLAIN_KEY = Regex("""^([A-Za-z_][A-Za-z0-9_-]*)\s*:(.*)$""")

// Returns key + everything after the `:`, or null when the line is not a
// flat key/value entry.
private fun parseYamlKey(line: String): Pair<String, String>? {
    if (line[0] == '"' || line[0] == '\'') {
        val (key, end) = scanQuotedScalar(line, yamlEscapes = true) ?: return null
        var i = end
        while (i < line.length && line[i] == ' ') i++
        if (i >= line.length || line[i] != ':') return null
        return key to line.substring(i + 1)
    }
    val match = YAML_PLAIN_KEY.find(line) ?: return null
    return match.groupValues[1] to match.groupValues[2]
}

private fun parseYamlValue(rest: String): String? {
    val trimmed = rest.trim()
    if (trimmed.isEmpty()) return null           // nested block introducer
    return when (trimmed[0]) {
        '#' -> null                              // comment-only value
        '|', '>' -> null                         // block scalar (multi-line)
        '"', '\'' -> scanQuotedScalar(trimmed, yamlEscapes = true)?.first
            ?: trimmed                           // unterminated: keep as-is
        else -> stripTrailingComment(trimmed, requireSpaceBeforeHash = true)
            .trim()
            .ifEmpty { null }
    }
}

// Flat TOML subset: top-level `key = value` lines up to the first `[section]`
// header (keys below it are section-scoped, not top-level).
private fun parseFlatToml(body: String): Map<String, String> {
    val metadata = mutableMapOf<String, String>()
    for (rawLine in body.lineSequence()) {
        val line = rawLine.trim()
        if (line.isEmpty() || line[0] == '#') continue
        if (line[0] == '[') break
        val (key, rest) = parseTomlKey(line) ?: continue
        val value = parseTomlValue(rest) ?: continue
        metadata[key] = value
    }
    return metadata
}

private val TOML_PLAIN_KEY = Regex("""^([A-Za-z0-9_-]+)\s*=(.*)$""")

private fun parseTomlKey(line: String): Pair<String, String>? {
    if (line[0] == '"' || line[0] == '\'') {
        val (key, end) = scanQuotedScalar(line, yamlEscapes = false) ?: return null
        var i = end
        while (i < line.length && line[i] == ' ') i++
        if (i >= line.length || line[i] != '=') return null
        return key to line.substring(i + 1)
    }
    val match = TOML_PLAIN_KEY.find(line) ?: return null
    return match.groupValues[1] to match.groupValues[2]
}

private fun parseTomlValue(rest: String): String? {
    val trimmed = rest.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("\"\"\"") || trimmed.startsWith("'''")) {
        return null                              // multi-line string, skip
    }
    return when (trimmed[0]) {
        '#' -> null
        '"', '\'' -> scanQuotedScalar(trimmed, yamlEscapes = false)?.first
            ?: trimmed
        // Bare value (number, boolean, date): kept as its source string; in
        // TOML a `#` outside a string always starts a comment.
        else -> stripTrailingComment(trimmed, requireSpaceBeforeHash = false)
            .trim()
            .ifEmpty { null }
    }
}

// Scans a quoted scalar starting at index 0; returns the decoded content and
// the index right after the closing quote, or null when unterminated.
//
// With [yamlEscapes]: double quotes decode `\\ \" \n \r \t` (the inverse of
// yamlQuoted in SimplifyHtml.kt, unknown escapes keep the escaped char) and
// single quotes decode the doubled `''`. Without (TOML): double-quoted basic
// strings use the same escapes; single-quoted literal strings are verbatim.
private fun scanQuotedScalar(
    source: String,
    yamlEscapes: Boolean
): Pair<String, Int>? {
    val quote = source[0]
    val content = StringBuilder()
    var i = 1
    while (i < source.length) {
        val c = source[i]
        when {
            c == quote -> {
                if (quote == '\'' && yamlEscapes
                    && i + 1 < source.length && source[i + 1] == '\''
                ) {
                    content.append('\'')
                    i += 2
                } else {
                    return content.toString() to i + 1
                }
            }
            c == '\\' && quote == '"' && i + 1 < source.length -> {
                when (val escaped = source[i + 1]) {
                    'n' -> content.append('\n')
                    'r' -> content.append('\r')
                    't' -> content.append('\t')
                    else -> content.append(escaped)
                }
                i += 2
            }
            else -> {
                content.append(c)
                i++
            }
        }
    }
    return null
}

private fun stripTrailingComment(
    value: String,
    requireSpaceBeforeHash: Boolean
): String {
    for (i in value.indices) {
        if (value[i] == '#'
            && (!requireSpaceBeforeHash || (i > 0 && value[i - 1] == ' '))
        ) {
            return value.substring(0, i)
        }
    }
    return value
}
