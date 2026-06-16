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

package com.xemantic.markanywhere.html.icons

import com.xemantic.markanywhere.classList
import com.xemantic.markanywhere.transform.MarkSelect

// Most common Glyphicons glyph names → the closest Unicode emoji. Decorative
// variants are NOT listed: `canonicalIconName()` reduces `info-sign` → `info`,
// `ban-circle` → `ban`, `download-alt` → `download`, `heart-empty` → `heart`,
// etc. Extend as new icons are encountered; an unmapped recognized glyph falls
// back to a `:name:` hint.
private val iconMap = mapOf(
    "alert" to "⚠️",
    "apple" to "🍎",
    "arrow-down" to "⬇️",
    "arrow-left" to "⬅️",
    "arrow-right" to "➡️",
    "arrow-up" to "⬆️",
    "asterisk" to "✳️",
    "backward" to "⏪",
    "ban" to "🚫",
    "bed" to "🛏️",
    "bell" to "🔔",
    "bishop" to "♝",
    "bitcoin" to "₿",
    "book" to "📖",
    "bookmark" to "🔖",
    "briefcase" to "💼",
    "btc" to "₿",
    "bullhorn" to "📢",
    "calendar" to "📅",
    "camera" to "📷",
    "cd" to "💿",
    "certificate" to "📜",
    "check" to "✅",
    "chevron-down" to "🔽",
    "chevron-left" to "◀️",
    "chevron-right" to "▶️",
    "chevron-up" to "🔼",
    "circle-arrow-down" to "⬇️",
    "circle-arrow-left" to "⬅️",
    "circle-arrow-right" to "➡️",
    "circle-arrow-up" to "⬆️",
    "cloud" to "☁️",
    "cloud-download" to "⬇️",
    "cloud-upload" to "⬆️",
    "cog" to "⚙️",
    "comment" to "💬",
    "console" to "💻",
    "copy" to "📋",
    "copyright-mark" to "©",
    "credit-card" to "💳",
    "cutlery" to "🍴",
    "download" to "⬇️",
    "duplicate" to "📋",
    "earphone" to "📞",
    "edit" to "✏️",
    "education" to "🎓",
    "eject" to "⏏️",
    "envelope" to "✉️",
    "equalizer" to "🎛️",
    "eur" to "€",
    "euro" to "€",
    "exclamation" to "⚠️",
    "export" to "📤",
    "eye-close" to "🙈",
    "eye-open" to "👁️",
    "fast-backward" to "⏪",
    "fast-forward" to "⏩",
    "file" to "📄",
    "film" to "🎬",
    "filter" to "🔻",
    "fire" to "🔥",
    "flag" to "🚩",
    "flash" to "⚡",
    "floppy-disk" to "💾",
    "folder-close" to "📁",
    "folder-open" to "📂",
    "font" to "🔤",
    "forward" to "⏩",
    "gbp" to "£",
    "gift" to "🎁",
    "glass" to "🍸",
    "globe" to "🌐",
    "hand-down" to "👇",
    "hand-left" to "👈",
    "hand-right" to "👉",
    "hand-up" to "👆",
    "headphones" to "🎧",
    "heart" to "❤️",
    "home" to "🏠",
    "hourglass" to "⏳",
    "import" to "📥",
    "inbox" to "📥",
    "info" to "ℹ️",
    "jpy" to "¥",
    "king" to "♚",
    "knight" to "♞",
    "lamp" to "💡",
    "leaf" to "🍃",
    "link" to "🔗",
    "list" to "☰",
    "lock" to "🔒",
    "log-in" to "🚪",
    "log-out" to "🚪",
    "magnet" to "🧲",
    "map-marker" to "📍",
    "menu-hamburger" to "☰",
    "minus" to "➖",
    "music" to "🎵",
    "new-window" to "↗️",
    "oil" to "🛢️",
    "ok" to "✅",
    "option-horizontal" to "⋯",
    "option-vertical" to "⋮",
    "paperclip" to "📎",
    "paste" to "📋",
    "pause" to "⏸️",
    "pawn" to "♟️",
    "pencil" to "✏️",
    "phone" to "📱",
    // `phone-alt` is a telephone receiver, not a variant of the mobile-phone
    // `phone` glyph the canonicalizer would reduce it to.
    "phone-alt" to "📞",
    "picture" to "🖼️",
    "plane" to "✈️",
    "play" to "▶️",
    "plus" to "➕",
    "print" to "🖨️",
    "pushpin" to "📌",
    "queen" to "♛",
    "question" to "❓",
    "random" to "🔀",
    "record" to "⏺️",
    "refresh" to "🔄",
    "registration-mark" to "®",
    "remove" to "❌",
    "repeat" to "🔁",
    "retweet" to "🔁",
    "road" to "🛣️",
    "rub" to "₽",
    "ruble" to "₽",
    "save" to "💾",
    "scale" to "⚖️",
    "scissors" to "✂️",
    "search" to "🔍",
    "send" to "📤",
    "share" to "🔗",
    "shopping-cart" to "🛒",
    "signal" to "📶",
    "sort" to "↕️",
    "star" to "⭐",
    "stats" to "📊",
    "step-backward" to "⏮️",
    "step-forward" to "⏭️",
    "stop" to "⏹️",
    "sunglasses" to "😎",
    "tag" to "🏷️",
    "tags" to "🏷️",
    "tent" to "⛺",
    "thumbs-down" to "👎",
    "thumbs-up" to "👍",
    "time" to "🕒",
    "tint" to "💧",
    "transfer" to "🔁",
    "trash" to "🗑️",
    "tree-conifer" to "🌲",
    "tree-deciduous" to "🌳",
    "upload" to "⬆️",
    "usd" to "$",
    "user" to "👤",
    "volume-down" to "🔉",
    "volume-off" to "🔇",
    "volume-up" to "🔊",
    "warning" to "⚠️",
    "wrench" to "🔧",
    "xbt" to "₿",
    "yen" to "¥",
    "zoom-in" to "🔍",
    "zoom-out" to "🔍",
)

/**
 * Resolves [Glyphicons](https://getbootstrap.com/docs/3.4/components/#glyphicons)
 * (Bootstrap 3) — `<span class="glyphicon glyphicon-info-sign">`. The glyph
 * name is the `glyphicon-<name>` class token; the bare `glyphicon` family
 * token carries no glyph.
 */
public val GlyphiconResolver: MarkSelect = { mark ->
    // The glyph is the first `glyphicon-<name>` token with a non-empty name —
    // which by itself proves this is a Glyphicons element, so no separate family
    // check is needed. A bare `glyphicon` or stray `glyphicon-` yields no glyph:
    // decline rather than emit an empty icon.
    val glyph = mark.classList
        .firstOrNull { it.length > "glyphicon-".length && it.startsWith("glyphicon-") }
        ?.removePrefix("glyphicon-")
    if (glyph != null) {
        { "icon" { +glyph.toIconToken() } }
    } else null
}

// A recognized glyph resolves to its curated emoji (looked up raw, then after
// canonicalization), otherwise to a `:name:` hint so the label survives downstream.
private fun String.toIconToken(): String =
    iconMap[this] ?: iconMap[canonicalIconName()] ?: ":$this:"

// Strips decorative variant suffixes so `info-sign` / `ok-circle` /
// `download-alt` / `star-empty` all reduce to their base name.
private fun String.canonicalIconName(): String {
    var n = this
    for (suffix in VARIANT_SUFFIXES) {
        if (n.length > suffix.length && n.endsWith(suffix)) {
            n = n.removeSuffix(suffix)
            break
        }
    }
    return n
}

private val VARIANT_SUFFIXES = listOf("-sign", "-circle", "-alt", "-empty")
