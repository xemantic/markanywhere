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

import com.xemantic.kotlin.core.text.unaryPlus

/*
 * Flat YAML / TOML front-matter codec, shared by every operator that produces
 * or consumes the body of a `frontmatter` mark: `simplifyHtml` renders a
 * head-derived metadata map into one, `wrapInHtmlDocument` parses one back
 * into a head, and `ensureFrontmatterTitle` inspects one and prepends a
 * `title` line to it.
 *
 * Only the flat `key: value` (YAML) / `key = value` (TOML) subset is covered
 * — exactly the shape `simplifyHtml` produces. Nested structures, block
 * scalars, comments and malformed lines are skipped on read; nothing here
 * ever throws.
 *
 * The writing and reading halves below are exact inverses (`yamlQuoted` ↔
 * `scanQuotedScalar(yamlEscapes = true)`, `tomlBasicString` ↔
 * `scanQuotedScalar(yamlEscapes = false)`, `renderYamlFrontmatter` ↔
 * `parseFlatYaml`) — they live in one file so a change to either side can be
 * matched in the other on sight.
 */

// --- writing ---------------------------------------------------------------

internal fun renderYamlFrontmatter(
    metadata: Map<String, String>
): String = buildString {
    for ((key, value) in metadata) {
        yamlScalar(key)
        +": "
        yamlScalar(value)
        +'\n'
    }
}

// A single top-level `title` entry, in the given format's syntax. YAML picks
// the narrowest safe scalar form; TOML always uses a basic string (a bare
// TOML value cannot hold arbitrary text).
internal fun titleLine(
    title: String,
    format: String
): String = buildString {
    if (format == "toml") {
        +"title = "
        tomlBasicString(title)
    } else {
        +"title: "
        yamlScalar(title)
    }
    +'\n'
}

// YAML 1.2 reserved boolean / null literals. Must be quoted to keep them as
// strings instead of decoding to `true`/`false`/`null`.
private val YAML_RESERVED_LITERALS = setOf(
    "true", "True", "TRUE", "false", "False", "FALSE",
    "yes", "Yes", "YES", "no", "No", "NO",
    "on", "On", "ON", "off", "Off", "OFF",
    "null", "Null", "NULL", "~"
)

// Plain-scalar indicator characters: starting with any of these forces
// double-quoted output (see YAML 1.2 §6.4 / §6.6).
private const val YAML_INDICATORS = "-?:,[]{}#&*!|>'\"%@`"

private fun Appendable.yamlScalar(s: String) {
    if (needsYamlQuoting(s)) yamlQuoted(s) else +s
}

// Longest YAML_RESERVED_LITERALS entry — lets the set lookup (which hashes
// the whole string on targets without a cached String hash) be skipped for
// anything longer.
private const val YAML_RESERVED_LITERAL_MAX_LENGTH = 5

private fun needsYamlQuoting(s: String): Boolean {
    if (s.isEmpty()) return true
    if (s.first().isWhitespace() || s.last().isWhitespace()) return true
    if (s[0] in YAML_INDICATORS) return true
    for (c in s) when (c) {
        ':', '#', '"', '\\', '\n', '\r', '\t' -> return true
        else -> {}
    }
    return s.length <= YAML_RESERVED_LITERAL_MAX_LENGTH
            && s in YAML_RESERVED_LITERALS
}

private fun Appendable.yamlQuoted(s: String) {
    +'"'
    for (c in s) when (c) {
        '\\' -> +"\\\\"
        '"' -> +"\\\""
        '\n' -> +"\\n"
        '\r' -> +"\\r"
        '\t' -> +"\\t"
        else -> +c
    }
    +'"'
}

// Escapes exactly the set `scanQuotedScalar` decodes — a raw newline would
// otherwise terminate the entry mid-value (and TOML forbids a literal control
// character in a basic string anyway). TOML's own `\b` / `\f` are deliberately
// not emitted: the reader would decode them back to `b` / `f`.
private fun Appendable.tomlBasicString(s: String) {
    +'"'
    for (c in s) when (c) {
        '\\' -> +"\\\\"
        '"' -> +"\\\""
        '\n' -> +"\\n"
        '\r' -> +"\\r"
        '\t' -> +"\\t"
        else -> +c
    }
    +'"'
}

// --- reading ---------------------------------------------------------------

internal fun parseFlatFrontmatter(
    body: String,
    format: String
): Map<String, String> = when (format) {
    "yaml" -> parseFlatYaml(body)
    "toml" -> parseFlatToml(body)
    else -> emptyMap()
}

private val YAML_TITLE = Regex("""^title\s*:""")
private val TOML_TITLE = Regex("""^title\s*=""")

// A `title` key is only recognized at the top level — column 0, so an
// indented (nested) `title:` does not count.
internal fun hasTitle(
    body: CharSequence,
    format: String
): Boolean {
    val regex = if (format == "toml") TOML_TITLE else YAML_TITLE
    return body.lineSequence().any { regex.containsMatchIn(it) }
}

// Flat YAML subset: top-level `key: value` lines, the inverse of
// renderYamlFrontmatter above. Comments, blank lines, indented (nested)
// lines, and keys without an inline scalar value are skipped.
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
// yamlQuoted above, unknown escapes keep the escaped char) and single quotes
// decode the doubled `''`. Without (TOML): double-quoted basic strings use
// the same escapes; single-quoted literal strings are verbatim.
private fun scanQuotedScalar(
    source: String,
    yamlEscapes: Boolean
): Pair<String, Int>? {
    val quote = source[0]
    val content = StringBuilder()
    var i = 1
    while (i < source.length) {
        when (val c = source[i]) {
            quote -> {
                if (quote == '\'' && yamlEscapes
                    && i + 1 < source.length && source[i + 1] == '\''
                ) {
                    content.append('\'')
                    i += 2
                } else {
                    return content.toString() to i + 1
                }
            }
            '\\' if quote == '"' && i + 1 < source.length -> {
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
