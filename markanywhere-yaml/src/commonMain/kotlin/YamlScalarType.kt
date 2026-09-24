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

// The one copy of the plain-scalar typing rules: [YamlParser] resolves a
// plain scalar's `type` with them, [YamlWriter] quotes a string they would
// type. A plain scalar is typed exactly when a reader front matter is
// written for reads it as something other than a string — the YAML 1.2
// core schema, and the YAML 1.1 shapes of Psych (Jekyll), PyYAML and go-yaml
// v2 (Hugo). The number rules are therefore transcribed per reader, each
// less the shapes that reader then refuses to construct, rather than merged
// into one pattern: every reader has its own separator rules (Psych takes
// `,` only before a digit, PyYAML takes `_` anywhere after the first digit,
// go-yaml v2 deletes every `_` first), and a merged pattern would type
// shapes none of them does (`1,`, `1_,2`). So `type=int` / `type=float` means
// some reader reads a number — reading it yourself needs that reader's
// rules (drop `_` and `,`; base 60, binary and octal prefixes).
// Covered beyond the core schema:
// - booleans and nulls in any letter case (`yEs`, `nULL` — Psych ignores
//   case), and the one-letter booleans `y` / `n` (go-yaml v2);
// - the special floats in any letter case (`.Nan`, `+.InF`);
// - integers with separators, binary, upper-case and signed prefixes
//   (`1_000`, `1,000`, `0b101`, `0X1F`, `+0x1F`, `+_1`);
// - floats with separators (`1_000.5`, `1_e5`), and a leading-zero decimal
//   that is not octal (`08`, go-yaml v2);
// - base-60 numbers (`12:30` an int, `190:20:30.15` a float);
// - timestamps with a one-digit month / day (`2024-5-1`) or an offset
//   without a colon (`2016-01-01 12:00:00 -0500`, Jekyll's documented
//   format) — a date only when it is in the calendar, a date and time only
//   within the ranges Psych's `Time` accepts (it normalises `2023-02-31`).
// Not modelled: a float that overflows (`7e700`), which go-yaml v2 alone
// reads as a string.
// Typing such a value keeps a source document as written: the writer writes
// a typed scalar bare, so only a *string* of one of these shapes is quoted.

private val YAML_NULLS = setOf("null", "~")

private val YAML_BOOLS = setOf("true", "false", "yes", "no", "on", "off", "y", "n")

private val YAML_SPECIAL_FLOATS = setOf(".inf", "+.inf", "-.inf", ".nan")

// the longest word above, so a long string is never lowercased
private val YAML_WORD_MAX_LENGTH =
    (YAML_NULLS + YAML_BOOLS + YAML_SPECIAL_FLOATS).maxOf { it.length }

// Each pattern is explicitly anchored: Kotlin/JS resolves `matches` through
// the leftmost match, so an unanchored alternation lets the first branch win
// on a prefix (`0x1F` matched `[0-9]+` as `0`, a full timestamp matched its
// date-only branch) and the whole-input check then fails — JVM backtracks
// across the branches and never showed it.
private fun anchored(pattern: String) = Regex("^(?:$pattern)$")

// PyYAML's resolver, less a base prefix with no digit (`0x_`), which it
// resolves and then fails to construct
private val PYYAML_INT = anchored(
    "[-+]?0b[01_]*[01][01_]*|[-+]?0[0-7_]+|[-+]?(?:0|[1-9][0-9_]*)" +
        "|[-+]?0x[0-9a-fA-F_]*[0-9a-fA-F][0-9a-fA-F_]*|[-+]?[1-9][0-9_]*(?::[0-5]?[0-9])+"
)

private val PYYAML_FLOAT = anchored(
    """[-+]?[0-9][0-9_]*\.[0-9_]*(?:[eE][-+][0-9]+)?|\.[0-9][0-9_]*(?:[eE][-+][0-9]+)?""" +
        """|[-+]?[0-9][0-9_]*(?::[0-5]?[0-9])+\.[0-9_]*"""
)

// Psych's scalar scanner, with its default (legacy) integers that allow `,`
private val PSYCH_INT = anchored(
    "[-+]?0b[01_,]*[01][01_,]*|[-+]?0[0-7_,]+|[-+]?(?:0|[1-9](?:[0-9]|[,_][0-9])*)" +
        "|[-+]?0x[0-9a-fA-F_,]*[0-9a-fA-F][0-9a-fA-F_,]*|[-+]?[0-9][0-9_]*(?::[0-5]?[0-9]){1,2}"
)

private val PSYCH_FLOAT = anchored(
    """[-+]?(?:[0-9][0-9_,]*\.[0-9]*|\.[0-9]+)(?:[eE][-+][0-9]+)?""" +
        """|[-+]?[0-9][0-9_]*(?::[0-5]?[0-9]){1,2}\.[0-9_]*"""
)

// go-yaml v2 deletes every `_` from a text starting with a sign or a digit,
// then tries Go's `ParseInt` with base 0 (which reads a `0x` / `0o` / `0b`
// prefix in either case, and a bare leading `0` as octal — so `08` fails
// here and falls through to the float), a `0b` prefix of its own (which
// lets a sign follow an unsigned prefix: `0b-1`, not `-0b-1`) and a YAML 1.2
// float
private val GO_YAML_INT = anchored(
    "[-+]?(?:0[0-7]*|[1-9][0-9]*|0[xX][0-9a-fA-F]+|0[oO][0-7]+|0[bB][01]+)|0b[-+][01]+"
)

private val GO_YAML_FLOAT = anchored("""[-+]?(?:\.[0-9]+|[0-9]+(?:\.[0-9]*)?)(?:[eE][-+]?[0-9]+)?""")

// … but hands a text starting with `.` to Go's `ParseFloat` as is, which
// takes an `_` only between two digits
private val GO_YAML_DOT_FLOAT = anchored("""\.[0-9](?:_?[0-9])*(?:[eE][-+]?[0-9](?:_?[0-9])*)?""")

// The union of the Psych and PyYAML timestamp shapes (go-yaml v2 decodes a
// timestamp into a string); the groups are the date, the time and the
// offset, so [isTimestampInRange] can check their values.
private val YAML_TIMESTAMP = anchored(
    """(-?[0-9]{4})-([0-9]{1,2})-([0-9]{1,2})""" +
        """(?:(?:[Tt]|[ \t]+)([0-9]{1,2}):([0-9]{2}):([0-9]{2})(?:\.[0-9]*)?""" +
        """(?:[ \t]*(?:Z|[-+]([0-9]{1,2}):?([0-9]{2})?))?)?"""
)

// PyYAML's own timestamp shape: two-digit month and day for a date alone,
// a colon in an offset with minutes. It constructs whatever matches, and
// refuses the document when a value is out of range.
private val PYYAML_TIMESTAMP = anchored(
    """[0-9]{4}-[0-9]{2}-[0-9]{2}""" +
        """|[0-9]{4}-[0-9]{1,2}-[0-9]{1,2}(?:[Tt]|[ \t]+)[0-9]{1,2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]*)?""" +
        """(?:[ \t]*(?:Z|[-+][0-9]{1,2}(?::[0-9]{2})?))?"""
)

// The `type` a plain scalar resolves to, or null for a string.
internal fun yamlScalarType(text: String): String? {
    if (text.isEmpty()) return null
    if (text.length <= YAML_WORD_MAX_LENGTH) {
        val word = text.lowercase()
        if (word in YAML_NULLS) return "null"
        if (word in YAML_BOOLS) return "bool"
        if (word in YAML_SPECIAL_FLOATS) return "float"
    }
    // every numeric shape starts with a sign, a digit or a dot, so the
    // patterns only run on a string that can match them
    val first = text[0]
    if (first != '-' && first != '+' && first != '.' && first !in '0'..'9') return null
    if (PYYAML_INT.matches(text) || PSYCH_INT.matches(text)) return "int"
    if (PYYAML_FLOAT.matches(text) || PSYCH_FLOAT.matches(text)) return "float"
    if (first == '.') {
        if (GO_YAML_DOT_FLOAT.matches(text)) return "float"
    } else {
        val plain = if ('_' in text) text.replace("_", "") else text
        if (GO_YAML_INT.matches(plain)) return "int"
        if (GO_YAML_FLOAT.matches(plain)) return "float"
    }
    val timestamp = YAML_TIMESTAMP.matchEntire(text)
    if (timestamp != null && isTimestampInRange(timestamp.groupValues)) return "timestamp"
    return null
}

// Psych checks a date against the calendar, but normalises a date and time
// (`2023-02-31T10:00:00` is March 3rd), refusing only the values its `Time`
// cannot take — hour 24 only as `24:00:00`, an offset under a day; a
// date-only shape with a sign is not a timestamp at all.
private fun isTimestampInRange(groups: List<String>): Boolean {
    val year = groups[1].toInt()
    val month = groups[2].toInt()
    val day = groups[3].toInt()
    if (month !in 1..12) return false
    if (groups[4].isNotEmpty()) {
        val hour = groups[4].toInt()
        val minute = groups[5].toInt()
        val second = groups[6].toInt()
        val offsetHours = groups[7]
        val offsetMinutes = groups[8]
        return day in 1..31 && minute <= 59 && second <= 60 &&
            (hour <= 23 || hour == 24 && minute == 0 && second == 0) &&
            (offsetHours.isEmpty() || offsetHours.toInt() <= 23) &&
            (offsetMinutes.isEmpty() || offsetMinutes.toInt() <= 59)
    }
    if (year < 0) return false
    return day in 1..daysInMonth(year, month)
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

// Shapes some reader resolves and then refuses to load, so the whole
// document fails: PyYAML's value / merge tags (`=`, `<<`), which its
// SafeLoader cannot construct; a base prefix with no digit (`0x_`, PyYAML
// and Psych); an exponent with no mantissa digit (`.e+4`, Psych); and, in
// [isUnsafePlainScalar], a [PYYAML_TIMESTAMP] shape out of range, which
// PyYAML refuses (`2024-13-45`, `2024-01-01 24:30:00`) — the parser does
// not type it either, or it would not get there. There is no type to report
// for them, so the parser keeps them strings — DIVERGENCE: the writer still
// quotes them, which rewrites such a plain source value on its first render.
private val YAML_REJECTED = anchored("""=|<<|[-+]?0[bx][_,]+|[-+]?\.[eE][-+][0-9]+""")

// Whether a plain scalar would not read back as this string in every
// reader: the parser types it, or some reader refuses it.
internal fun isUnsafePlainScalar(text: String): Boolean {
    if (yamlScalarType(text) != null) return true
    // every refused shape starts with one of these, so the patterns only
    // run on a string that can match them
    val first = text.firstOrNull() ?: return false
    if (first != '=' && first != '<' && first != '-' && first != '+' && first != '.' && first !in '0'..'9') {
        return false
    }
    return YAML_REJECTED.matches(text) || PYYAML_TIMESTAMP.matches(text)
}

// Whether a plain identifier-shaped mapping key would be read as something
// other than a string: Psych types the boolean and null words in any letter
// case (PyYAML only some cases of them), while go-yaml v2 decodes a front
// matter key into a string, so its one-letter booleans `y` / `n` stay keys.
// The parser never types a key, so this is quoting the writer alone does.
internal fun isTypedPlainKey(key: String): Boolean {
    if (key.length > YAML_WORD_MAX_LENGTH) return false
    val word = key.lowercase()
    return word in YAML_NULLS || word in YAML_BOOLS && word != "y" && word != "n"
}

// The two indicators that can end a plain scalar in block context, shared by
// [YamlParser] (where it splits a key and strips a comment) and [YamlWriter]
// (which quotes a string holding either), so the two cannot drift apart:
// a `:` followed by whitespace or the end is a mapping colon, a `#` after
// whitespace starts a comment. Anywhere else both are plain content.

internal fun String.isMappingColonAt(i: Int): Boolean =
    this[i] == ':' && (i + 1 == length || this[i + 1] == ' ' || this[i + 1] == '\t')

internal fun String.isCommentStartAt(i: Int): Boolean =
    this[i] == '#' && i > 0 && (this[i - 1] == ' ' || this[i - 1] == '\t')
