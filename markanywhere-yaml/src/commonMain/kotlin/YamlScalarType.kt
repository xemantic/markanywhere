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
// type. A plain scalar is typed when any reader front matter is written for
// would not read it as a string — the YAML 1.2 core schema, plus the YAML 1.1
// shapes Psych (Jekyll), PyYAML and go-yaml v2 (Hugo) still type, erring on
// the side of too much:
// - booleans and nulls in any letter case (`yEs`, `nULL` — Psych ignores
//   case), and the one-letter booleans `y` / `n` (the YAML 1.1 spec, go-yaml
//   v2);
// - the special floats in any letter case (`.Nan`, `+.InF`);
// - integers with `_` or `,` separators, binary, signed hex
//   (`1_000`, `1,000`, `0b101`, `+0x1F`);
// - floats with separators or a digit-less mantissa (`1_000.5`, `.e+4`) —
//   but never a shape with no digit at all (`.`, `+.`), which every reader
//   reads as a string;
// - base-60 numbers (`12:30` an int, `190:20:30.15` a float);
// - timestamps with a one-digit month / day (`2024-5-1`) or an offset
//   without a colon (`2016-01-01 12:00:00 -0500`, Jekyll's documented
//   format).
// Typing such a value keeps a source document as written: the writer writes
// a typed scalar bare, so only a *string* of one of these shapes is quoted.

private val YAML_NULLS = setOf("null", "~")

private val YAML_BOOLS = setOf("true", "false", "yes", "no", "on", "off", "y", "n")

private val YAML_SPECIAL_FLOATS = setOf(".inf", "+.inf", "-.inf", ".nan")

// the longest word above, so a long string is never lowercased
private const val YAML_WORD_MAX_LENGTH = 5

// Each pattern is explicitly anchored: Kotlin/JS resolves `matches` through
// the leftmost match, so an unanchored alternation lets the first branch win
// on a prefix (`0x1F` matched `[0-9]+` as `0`, a full timestamp matched its
// date-only branch) and the whole-input check then fails — JVM backtracks
// across the branches and never showed it.
private val YAML_INT = Regex(
    """^[-+]?(?:[0-9][0-9_,]*|0b[01_,]+|0o[0-7]+|0x[0-9a-fA-F_,]+|[0-9][0-9_,]*(?::[0-5]?[0-9])+)$"""
)

// the lookahead requires a digit somewhere, in the mantissa or the exponent
private val YAML_FLOAT = Regex(
    """^(?=[^0-9]*[0-9])[-+]?(?:(?:[0-9][0-9_,]*)?\.[0-9_]*(?:[eE][-+]?[0-9]+)?""" +
        """|[0-9]+[eE][-+]?[0-9]+|[0-9][0-9_,]*(?::[0-5]?[0-9])+\.[0-9_]*)$"""
)

private val YAML_TIMESTAMP = Regex(
    """^-?[0-9]{4}-[0-9]{1,2}-[0-9]{1,2}""" +
        """(?:(?:[Tt]|[ \t]+)[0-9]{1,2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]*)?(?:[ \t]*(?:Z|[-+][0-9]{1,2}:?(?:[0-9]{2})?))?)?$"""
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
    return when {
        YAML_INT.matches(text) -> "int"
        YAML_FLOAT.matches(text) -> "float"
        YAML_TIMESTAMP.matches(text) -> "timestamp"
        else -> null
    }
}

// Whether a plain scalar would not read back as this string: the parser
// types it, or it is one of PyYAML's value / merge tags (`=`, `<<`), which
// its SafeLoader refuses to construct.
internal fun isTypedPlainScalar(text: String): Boolean =
    yamlScalarType(text) != null || text == "=" || text == "<<"
