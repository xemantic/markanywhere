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
// A shape go-yaml v2 alone reads as a number is typed only within the
// range it parses (`1e999`, `0X` and 17 hex digits are strings to it).
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
// No pattern repeats a group (`(?:_?[0-9])*`), not even a bounded number of
// times: the JVM engine matches each repetition of a group one stack frame
// deeper, so a long digit run overflows the stack. A digit run is a
// character class instead, and the rule the group expressed is checked by
// hand ([hasSeparatorsBeforeDigits], [hasUnderscoresBetweenDigits],
// [isBase60Tail]).
private fun anchored(pattern: String) = Regex("^(?:$pattern)$")

// PyYAML's resolver, less a base prefix with no digit (`0x_`), which it
// resolves and then fails to construct. The digits after a base prefix are
// written `_*[01][01_]*`, not `[01_]*[01][01_]*`: the same strings, but the
// latter can split a digit run many ways and backtracks quadratically over
// a long near-miss.
private val PYYAML_INT = anchored(
    "[-+]?0b_*[01][01_]*|[-+]?0[0-7_]+|[-+]?(?:0|[1-9][0-9_]*)" +
        "|[-+]?0x_*[0-9a-fA-F][0-9a-fA-F_]*"
)

private val PYYAML_FLOAT = anchored(
    """[-+]?[0-9][0-9_]*\.[0-9_]*(?:[eE][-+][0-9]+)?|\.[0-9][0-9_]*(?:[eE][-+][0-9]+)?"""
)

// The base-60 int and float: PyYAML's `[1-9][0-9_]*(?::[0-5]?[0-9])+` and
// Psych's `[0-9][0-9_]*(?::[0-5]?[0-9]){1,2}` (so a leading `0` only with at
// most two segments), and for the float `[0-9][0-9_]*(?::[0-5]?[0-9])+`
// with a fraction — Psych's float takes the same with at most two segments,
// a subset. The group is written as a run it is the tail of, which
// [isBase60Tail] then checks; the first group is the leading digit.
private val BASE60_INT = anchored("[-+]?([0-9])[0-9_]*(:[0-9:]*)")

private val BASE60_FLOAT = anchored("""[-+]?[0-9][0-9_]*(:[0-9:]*)\.[0-9_]*""")

// Psych's scalar scanner, with its default (legacy) integers that allow `,`
private val PSYCH_INT = anchored(
    "[-+]?0b[_,]*[01][01_,]*|[-+]?0[0-7_,]+|[-+]?0" +
        "|[-+]?0x[_,]*[0-9a-fA-F][0-9a-fA-F_,]*"
)

// … and its decimal, `[1-9](?:[0-9]|[,_][0-9])*`: a separator only before a
// digit, which [hasSeparatorsBeforeDigits] checks
private val PSYCH_DECIMAL_INT = anchored("[-+]?[1-9][0-9,_]*")

private val PSYCH_FLOAT = anchored("""[-+]?(?:[0-9][0-9_,]*\.[0-9]*|\.[0-9]+)(?:[eE][-+][0-9]+)?""")

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
// ([hasUnderscoresBetweenDigits] checks that)
private val GO_YAML_DOT_FLOAT = anchored("""\.[0-9][0-9_]*(?:[eE][-+]?[0-9][0-9_]*)?""")

// The union of the Psych and PyYAML timestamp shapes (go-yaml v2 decodes a
// timestamp into a string); the groups are the date, the time and the
// offset digits, so [isTimestampInRange] can check their values and
// [isPyYamlTimestamp] PyYAML's narrower shape.
private val YAML_TIMESTAMP = anchored(
    """(-?[0-9]{4})-([0-9]{1,2})-([0-9]{1,2})""" +
        """(?:(?:[Tt]|[ \t]+)([0-9]{1,2}):([0-9]{2}):([0-9]{2})(?:\.[0-9]*)?""" +
        """(?:[ \t]*(?:Z|[-+]([0-9]{1,2}(?::?[0-9]{2})?)))?)?"""
)

// Every character a numeric or timestamp shape can hold, so a string with
// any other one is a string without running a pattern: digits, signs,
// separators, base prefixes and hex digits, exponents, and a timestamp's
// `T` / `Z` and spaces.
private fun Char.canBeNumeric(): Boolean =
    this in '0'..'9' || this in "+-._,:" || this in 'a'..'f' || this in 'A'..'F' ||
        this in "xXoO tTZ\t"

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
    if (!text.all { it.canBeNumeric() }) return null
    // a date is the only shape with a `-` after four digits, and a time
    // follows one, so a timestamp never runs the number patterns
    if (text.isTimestampShaped()) {
        return if (timestampVerdict(text) == TYPED) "timestamp" else null
    }
    // … and base 60 is the only number with a `:`
    if (':' in text) {
        val int = BASE60_INT.matchEntire(text)
        if (int != null && isBase60Int(int.groupValues)) return "int"
        val float = BASE60_FLOAT.matchEntire(text)
        return if (float != null && isBase60Tail(float.groupValues[1])) "float" else null
    }
    if (PYYAML_INT.matches(text) || PSYCH_INT.matches(text)) return "int"
    if (PSYCH_DECIMAL_INT.matches(text) && text.hasSeparatorsBeforeDigits()) return "int"
    if (PYYAML_FLOAT.matches(text) || PSYCH_FLOAT.matches(text)) return "float"
    if (first == '.') {
        if (GO_YAML_DOT_FLOAT.matches(text) && text.hasUnderscoresBetweenDigits() &&
            text.replace("_", "").toDouble().isFinite()
        ) {
            return "float"
        }
    } else {
        val plain = if ('_' in text) text.replace("_", "") else text
        if (GO_YAML_INT.matches(plain) && plain.fitsGoYamlInt()) return "int"
        if (GO_YAML_FLOAT.matches(plain) && plain.toDouble().isFinite()) return "float"
    }
    return null
}

// Whether go-yaml v2 parses this integer (`_` already deleted): Go's
// `ParseInt` takes a sign and up to 64 bits signed, `ParseUint` no sign and
// up to 64 bits unsigned; its own `0b` prefix hands the rest after it,
// sign included, to the same two.
private fun String.fitsGoYamlInt(): Boolean {
    val binarySigned = startsWith("0b-") || startsWith("0b+")
    val number = if (binarySigned) substring(2) else this
    val sign = number[0].takeIf { it == '-' || it == '+' }
    val unsigned = if (sign != null) number.substring(1) else number
    val (radix, digits) = when {
        binarySigned -> 2 to unsigned
        unsigned.length > 1 && unsigned[0] == '0' -> when (unsigned[1]) {
            'x', 'X' -> 16 to unsigned.substring(2)
            'o', 'O' -> 8 to unsigned.substring(2)
            'b', 'B' -> 2 to unsigned.substring(2)
            else -> 8 to unsigned.substring(1)
        }
        else -> 10 to unsigned
    }
    val magnitude = digits.toULongOrNull(radix) ?: return false
    return when (sign) {
        '-' -> magnitude <= Long.MIN_VALUE.toULong()
        '+' -> magnitude <= Long.MAX_VALUE.toULong()
        else -> true
    }
}

// Whether the text starts like a [YAML_TIMESTAMP]: four digits, after an
// optional `-`, then a `-`.
private fun String.isTimestampShaped(): Boolean {
    val start = if (startsWith('-')) 1 else 0
    return length > start + 4 && this[start + 4] == '-' &&
        (start until start + 4).all { this[it] in '0'..'9' }
}

// Whether every `,` / `_` is followed by a digit.
private fun String.hasSeparatorsBeforeDigits(): Boolean = indices.all { i ->
    this[i] != ',' && this[i] != '_' || i + 1 < length && this[i + 1] in '0'..'9'
}

// Whether every `_` sits between two digits.
private fun String.hasUnderscoresBetweenDigits(): Boolean = indices.all { i ->
    this[i] != '_' || i > 0 && this[i - 1] in '0'..'9' && i + 1 < length && this[i + 1] in '0'..'9'
}

// Whether [tail] is one or more `:` segments of `[0-5]?[0-9]`.
private fun isBase60Tail(tail: String): Boolean =
    tail.split(':').drop(1).all { segment ->
        segment.length == 1 || segment.length == 2 && segment[0] in '0'..'5'
    }

// Whether a [BASE60_INT] match is PyYAML's (no leading `0`, any number of
// segments) or Psych's (at most two).
private fun isBase60Int(groups: List<String>): Boolean {
    val tail = groups[2]
    return isBase60Tail(tail) && (groups[1] != "0" || tail.count { it == ':' } <= 2)
}

private enum class TimestampVerdict { NONE, TYPED, REFUSED }

// For a timestamp-shaped text: [TimestampVerdict.TYPED] when some reader
// types it, [TimestampVerdict.REFUSED] when PyYAML resolves its shape and
// then refuses the value, [TimestampVerdict.NONE] for a plain string.
private fun timestampVerdict(text: String): TimestampVerdict {
    val groups = YAML_TIMESTAMP.matchEntire(text)?.groupValues ?: return NONE
    return when {
        isTimestampInRange(groups) -> TYPED
        isPyYamlTimestamp(groups) -> REFUSED
        else -> NONE
    }
}

// Whether a [YAML_TIMESTAMP] match is also PyYAML's own shape: no sign on the
// year, a two-digit month and day for a date alone, and a colon in an
// offset with minutes.
private fun isPyYamlTimestamp(groups: List<String>): Boolean {
    if (groups[1].startsWith('-')) return false
    if (groups[4].isEmpty()) return groups[2].length == 2 && groups[3].length == 2
    val offset = groups[7]
    return offset.length <= 2 || ':' in offset
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
        val offset = groups[7]
        return day in 1..31 && minute <= 59 && second <= 60 &&
            (hour <= 23 || hour == 24 && minute == 0 && second == 0) &&
            (offset.isEmpty() || offsetMinutes(offset) < 24 * 60)
    }
    // `-0000` is a signed year too, though its value is not negative
    if (groups[1].startsWith('-')) return false
    return day in 1..daysInMonth(year, month)
}

// The offset as Psych's `parse_time` splits it: at the colon when there is
// one, else up to two digits of hours and the rest as minutes (`+530` is
// 53 hours, `+070` is 7). Minutes past 59 carry into the hours (`+05:99`).
private fun offsetMinutes(offset: String): Int {
    val colon = offset.indexOf(':')
    val split = if (colon >= 0) colon else minOf(2, offset.length)
    val hours = offset.substring(0, split).toInt()
    val minutes = offset.substring(if (colon >= 0) split + 1 else split)
    return hours * 60 + (if (minutes.isEmpty()) 0 else minutes.toInt())
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
// [isUnsafePlainScalar], a PyYAML timestamp shape ([isPyYamlTimestamp]) out
// of range, which
// PyYAML refuses (`2024-13-45`, `2024-01-01 24:30:00`) — the parser does
// not type it either, or it would not get there. There is no type to report
// for them, so the parser keeps them strings — DIVERGENCE: the writer still
// quotes them, which rewrites such a plain source value on its first render.
private val YAML_REJECTED = anchored("""=|<<|[-+]?0[bx][_,]+|[-+]?\.[eE][-+][0-9]+""")

// Whether a plain scalar would not read back as this string in every
// reader: the parser types it, or some reader refuses it.
internal fun isUnsafePlainScalar(text: String): Boolean {
    // typed or refused, decided by one match of the timestamp pattern
    if (text.isTimestampShaped()) return timestampVerdict(text) != NONE
    if (yamlScalarType(text) != null) return true
    // every other refused shape starts with one of these, so the pattern
    // only runs on a string that can match it
    val first = text.firstOrNull() ?: return false
    return (first == '=' || first == '<' || first == '0' || first == '+' || first == '-' || first == '.') &&
        YAML_REJECTED.matches(text)
}

// Whether a plain identifier-shaped mapping key would be read as something
// other than a string: Psych types the boolean and null words in any letter
// case (PyYAML only some cases of them), and go-yaml v2 its one-letter
// booleans `y` / `n` too — except in a [topLevel] key, which it decodes into
// a front matter's `map[string]interface{}`, while a nested mapping gets
// `interface{}` keys (`params:\n  y: 1` has the key `true`).
// The parser never types a key, so this is quoting the writer alone does.
internal fun isTypedPlainKey(key: String, topLevel: Boolean): Boolean {
    if (key.length > YAML_WORD_MAX_LENGTH) return false
    val word = key.lowercase()
    return word in YAML_NULLS || word in YAML_BOOLS && (!topLevel || word != "y" && word != "n")
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
