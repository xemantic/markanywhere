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

// Most common FontAwesome glyph names → the closest Unicode emoji. Decorative
// variants are NOT listed: `canonicalIconName()` (applied as the fallback lookup
// in `toIconToken`) reduces `square-info` / `info-circle` → `info`,
// `external-link-alt` → `external-link`, etc., so only the canonical base plus
// name synonyms need an entry. Synonyms cover every *official* alias from FA6's
// `metadata/icons.json` (the v4/v5 names FA still documents — `close`,
// `sign-out`, `undo`, `cutlery`, …) whose primary icon is mapped, so legacy
// pages resolve identically to current ones. Extend as new icons are
// encountered; an unmapped recognized glyph falls back to a `:name:` hint.
// Covers the FontAwesome "slab" pack (the ~200 most popular icons, extracted
// from icons/page1.html); slab names with no sensible Unicode counterpart
// (e.g. `gauge`, `grid`, `rectangle-*`) are left to the fallback.
private val iconMap = mapOf(
    "add" to "➕",
    "address-card" to "📇",
    "adjust" to "🌓",
    "alarm-clock" to "⏰",
    "anchor" to "⚓",
    "angle-down" to "🔽",
    "angle-left" to "◀️",
    "angle-right" to "▶️",
    "angle-up" to "🔼",
    "archive" to "🗃️",
    "arrow-circle-down" to "⬇️",
    "arrow-circle-left" to "⬅️",
    "arrow-circle-right" to "➡️",
    "arrow-circle-up" to "⬆️",
    "arrow-down" to "⬇️",
    "arrow-down-to-line" to "⬇️",
    "arrow-down-wide-short" to "⬇️",
    "arrow-left" to "⬅️",
    "arrow-left-rotate" to "↩️",
    "arrow-right" to "➡️",
    "arrow-right-arrow-left" to "🔁",
    "arrow-right-from-bracket" to "🚪",
    "arrow-right-rotate" to "↪️",
    "arrow-right-to-bracket" to "🚪",
    "arrow-rotate-back" to "↩️",
    "arrow-rotate-backward" to "↩️",
    "arrow-rotate-forward" to "↪️",
    "arrow-rotate-left" to "↩️",
    "arrow-rotate-right" to "↪️",
    "arrow-up" to "⬆️",
    "arrow-up-from-bracket" to "⬆️",
    "arrow-up-from-line" to "⬆️",
    "arrow-up-right-from-square" to "↗️",
    "arrow-up-wide-short" to "⬆️",
    "arrows-rotate" to "🔄",
    "at" to "@",
    "automobile" to "🚗",
    "backward" to "⏪",
    "backward-step" to "⏮️",
    "bag-shopping" to "🛍️",
    "ban" to "🚫",
    "bar-chart" to "📊",
    "bars" to "☰",
    "battery" to "🔋",
    "battery-0" to "🪫",
    "battery-5" to "🔋",
    "battery-bolt" to "🔋",
    "battery-empty" to "🪫",
    "battery-full" to "🔋",
    "bed" to "🛏️",
    "bell" to "🔔",
    "bell-slash" to "🔕",
    "biking" to "🚴",
    "birthday-cake" to "🎂",
    "bolt" to "⚡",
    "bomb" to "💣",
    "book" to "📖",
    "book-open" to "📖",
    "bookmark" to "🔖",
    "box" to "📦",
    "box-archive" to "🗃️",
    "briefcase" to "💼",
    "bug" to "🐛",
    "building" to "🏢",
    "bus" to "🚌",
    "cake" to "🎂",
    "cake-candles" to "🎂",
    "calendar" to "📅",
    "calendar-days" to "📅",
    "camera" to "📷",
    "cancel" to "🚫",
    "car" to "🚗",
    "caret-down" to "🔽",
    "caret-right" to "▶️",
    "caret-square-down" to "🔽",
    "caret-square-right" to "▶️",
    "cart-shopping" to "🛒",
    "chain" to "🔗",
    "chart-bar" to "📊",
    "chart-line" to "📈",
    "chart-pie" to "📊",
    "check" to "✅",
    "chevron-circle-down" to "🔽",
    "chevron-circle-left" to "◀️",
    "chevron-circle-right" to "▶️",
    "chevron-circle-up" to "🔼",
    "chevron-down" to "🔽",
    "chevron-left" to "◀️",
    "chevron-right" to "▶️",
    "chevron-up" to "🔼",
    "circle" to "⚪",
    "circle-dot" to "🔘",
    "circle-half-stroke" to "🌓",
    "city" to "🏙️",
    "clipboard" to "📋",
    "clock" to "🕒",
    "clock-four" to "🕒",
    "close" to "❌",
    "cloud" to "☁️",
    "code" to "💻",
    "cog" to "⚙️",
    "cogs" to "⚙️",
    "comment" to "💬",
    "comments" to "💬",
    "compact-disc" to "💿",
    "compass" to "🧭",
    "contact-card" to "📇",
    "copy" to "📋",
    "credit-card" to "💳",
    "crown" to "👑",
    "cut" to "✂️",
    "cutlery" to "🍴",
    "database" to "🗄️",
    "desktop" to "🖥️",
    "dot-circle" to "🔘",
    "download" to "⬇️",
    "droplet" to "💧",
    "earth" to "🌐",
    "edit" to "✏️",
    "ellipsis" to "⋯",
    "ellipsis-h" to "⋯",
    "envelope" to "✉️",
    "equals" to "🟰",
    "exchange" to "🔁",
    "exclamation" to "⚠️",
    "external-link" to "↗️",
    "eye" to "👁️",
    "eye-slash" to "🙈",
    "face-frown" to "☹️",
    "face-grin" to "😀",
    "face-meh" to "😐",
    "face-smile" to "🙂",
    "feed" to "📡",
    "file" to "📄",
    "file-clipboard" to "📋",
    "file-excel" to "📗",
    "file-lines" to "📄",
    "file-pdf" to "📕",
    "file-text" to "📄",
    "file-word" to "📘",
    "files" to "📑",
    "film" to "🎬",
    "filter" to "🔻",
    "fire" to "🔥",
    "fish" to "🐟",
    "flag" to "🚩",
    "floppy-disk" to "💾",
    "flower" to "🌸",
    "folder" to "📁",
    "folder-blank" to "📁",
    "folder-open" to "📂",
    "folders" to "🗂️",
    "font" to "🔤",
    "forward" to "⏩",
    "forward-step" to "⏭️",
    "frown" to "☹️",
    "gamepad" to "🎮",
    "gear" to "⚙️",
    "gears" to "⚙️",
    "gift" to "🎁",
    "glass-martini-alt" to "🍸",
    "globe" to "🌐",
    "graduation-cap" to "🎓",
    "grin" to "😀",
    "hammer" to "🔨",
    "hand" to "✋",
    "hand-paper" to "✋",
    "headphones" to "🎧",
    "heart" to "❤️",
    "home" to "🏠",
    "home-lg-alt" to "🏠",
    "hourglass" to "⏳",
    "hourglass-empty" to "⏳",
    "house" to "🏠",
    "image" to "🖼️",
    "images" to "🖼️",
    "inbox" to "📥",
    "info" to "ℹ️",
    "key" to "🔑",
    "landmark" to "🏛️",
    "language" to "🌐",
    "laptop" to "💻",
    "leaf" to "🍃",
    "life-ring" to "🛟",
    "lightbulb" to "💡",
    "line-chart" to "📈",
    "link" to "🔗",
    "list" to "☰",
    "list-squares" to "☰",
    "location-arrow" to "📍",
    "location-dot" to "📍",
    "lock" to "🔒",
    "lock-open" to "🔓",
    "magic" to "🪄",
    "magic-wand-sparkles" to "🪄",
    "magnifying-glass" to "🔍",
    "mail-forward" to "🔗",
    "map" to "🗺️",
    "map-marker" to "📍",
    "map-pin" to "📍",
    "martini-glass" to "🍸",
    "meh" to "😐",
    "message" to "💬",
    "microphone" to "🎤",
    "microphone-slash" to "🔇",
    "minus" to "➖",
    "mobile" to "📱",
    "mobile-android" to "📱",
    "mobile-phone" to "📱",
    "money-bill" to "💵",
    "money-bill-wave" to "💵",
    "moon" to "🌙",
    "mortar-board" to "🎓",
    "mug-hot" to "☕",
    "multiply" to "❌",
    "music" to "🎵",
    "navicon" to "☰",
    "newspaper" to "📰",
    "palette" to "🎨",
    "paper-plane" to "📤",
    "paperclip" to "📎",
    "paste" to "📋",
    "pause" to "⏸️",
    "paw" to "🐾",
    "pen" to "✏️",
    "pen-to-square" to "✏️",
    "pencil" to "✏️",
    "percent" to "%",
    "percentage" to "%",
    "person-biking" to "🚴",
    "phone" to "📞",
    "phone-slash" to "📵",
    "pie-chart" to "📊",
    "plane" to "✈️",
    "play" to "▶️",
    "play-pause" to "⏯️",
    "plug" to "🔌",
    "plus" to "➕",
    "print" to "🖨️",
    "question" to "❓",
    "redo" to "↪️",
    "redo-alt" to "🔄",
    "refresh" to "🔄",
    "remove" to "❌",
    "robot" to "🤖",
    "rocket" to "🚀",
    "rotate" to "🔄",
    "rotate-forward" to "🔄",
    "rotate-right" to "🔄",
    "rss" to "📡",
    "save" to "💾",
    "scissors" to "✂️",
    "screwdriver-wrench" to "🛠️",
    "search" to "🔍",
    "share" to "🔗",
    "share-alt-square" to "🔗",
    "share-nodes" to "🔗",
    "shield" to "🛡️",
    "shield-blank" to "🛡️",
    "shield-halved" to "🛡️",
    "ship" to "🚢",
    "shirt" to "👕",
    "shop" to "🏪",
    "shopping-bag" to "🛍️",
    "shopping-cart" to "🛒",
    "sign-in" to "🚪",
    "sign-out" to "🚪",
    "signal-bars" to "📶",
    "signal-bars-fair" to "📶",
    "signal-bars-good" to "📶",
    "signal-bars-slash" to "📵",
    "signal-bars-weak" to "📶",
    "skull" to "💀",
    "skull-crossbones" to "☠️",
    "sliders" to "🎛️",
    "sliders-h" to "🎛️",
    "smile" to "🙂",
    "snowflake" to "❄️",
    "sort" to "↕️",
    "sort-amount-asc" to "⬇️",
    "sort-amount-down" to "⬇️",
    "sort-amount-up" to "⬆️",
    "spinner" to "🔄",
    "square" to "⬜",
    "star" to "⭐",
    "step-backward" to "⏮️",
    "step-forward" to "⏭️",
    "stop" to "⏹️",
    "stopwatch" to "⏱️",
    "store-alt" to "🏪",
    "subtract" to "➖",
    "suitcase" to "🧳",
    "sun" to "☀️",
    "sync" to "🔄",
    "t-shirt" to "👕",
    "table" to "📋",
    "tag" to "🏷️",
    "tags" to "🏷️",
    "television" to "📺",
    "terminal" to "💻",
    "thumb-tack" to "📌",
    "thumbs-down" to "👎",
    "thumbs-up" to "👍",
    "thumbtack" to "📌",
    "ticket" to "🎫",
    "times" to "❌",
    "tint" to "💧",
    "tools" to "🛠️",
    "train" to "🚆",
    "trash" to "🗑️",
    "trash-can" to "🗑️",
    "tree" to "🌳",
    "triangle" to "🔺",
    // `triangle-circle-square` is the official alias of `shapes`; without an
    // explicit entry the canonicalizer would mis-reduce it to the unrelated
    // bare `circle`.
    "triangle-circle-square" to ":shapes:",
    "trophy" to "🏆",
    "truck" to "🚚",
    "tshirt" to "👕",
    "tv" to "📺",
    "umbrella" to "☂️",
    "undo" to "↩️",
    "universal-access" to "♿",
    "unlock" to "🔓",
    "unsorted" to "↕️",
    "up-right-from-square" to "↗️",
    "upload" to "⬆️",
    "user" to "👤",
    "user-friends" to "👥",
    "user-group" to "👥",
    "user-plus" to "👤",
    "users" to "👥",
    "utensils" to "🍴",
    "vcard" to "📇",
    "video" to "🎥",
    "video-camera" to "🎥",
    "volume" to "🔊",
    "volume-high" to "🔊",
    "volume-mute" to "🔇",
    "volume-slash" to "🔇",
    "volume-times" to "🔇",
    "volume-up" to "🔊",
    "volume-xmark" to "🔇",
    "wallet" to "👛",
    "wand-magic" to "🪄",
    "wand-magic-sparkles" to "🪄",
    "warning" to "⚠️",
    "wheelchair" to "♿",
    "wifi" to "📶",
    "wifi-3" to "📶",
    "wifi-strong" to "📶",
    "wrench" to "🔧",
    "xmark" to "❌",
    "zap" to "⚡",
)

/**
 * Resolves [FontAwesome](https://fontawesome.com) icons — `<i class="fa-solid
 * fa-square-info">`, `<i class="far fa-file-pdf">`, etc. The glyph name is the
 * `fa-<name>` class token that is not a style / sizing / animation modifier.
 */
public val FontAwesomeIconResolver: MarkSelect = { mark ->
    // The glyph is the first `fa-<name>` token whose name is non-empty and not a
    // style / sizing / animation modifier — which by itself proves this is a
    // FontAwesome element, so no separate family check is needed (family tokens
    // like `fa`, `fas`, … are not `fa-`-prefixed, so they can never be mistaken
    // for a glyph). A stray `fa-` or family/modifier classes only yield no
    // glyph: decline rather than emit an empty icon. The empty wrapper is then
    // dropped downstream by dropBlankInlineFormatting, and any text content is
    // preserved.
    val glyph = mark.classList.firstNotNullOfOrNull { token ->
        if (token.length > "fa-".length && token.startsWith("fa-")) {
            token.removePrefix("fa-").takeIf { it.isFontAwesomeGlyph() }
        } else null
    }
    if (glyph != null) {
        { "icon" { +glyph.toIconToken() } }
    } else null
}

// A recognized glyph resolves to its curated emoji (looked up raw, then after
// canonicalization), otherwise to a `:name:` hint so the label survives downstream.
private fun String.toIconToken(): String =
    iconMap[this] ?: iconMap[canonicalIconName()] ?: ":$this:"

// `fa-…` suffixes that are NOT glyph names: style families, sizes, animations,
// rotations, and layout helpers.
private val FA_MODIFIERS = setOf(
    "solid", "regular", "light", "thin", "duotone", "brands", "sharp",
    "fw", "lg", "sm", "xs", "xl", "2xs",
    "border", "inverse", "pull-left", "pull-right",
    "stack", "stack-1x", "stack-2x", "li", "ul",
    "beat", "fade", "beat-fade", "bounce", "shake",
    "spin", "spin-pulse", "spin-reverse", "pulse",
)

private fun String.isFontAwesomeGlyph(): Boolean = when {
    this in FA_MODIFIERS -> false
    endsWith("x") && dropLast(1).toIntOrNull() != null -> false // 1x..10x sizes
    startsWith("rotate-") || startsWith("flip-") -> false
    else -> true
}

// Strips decorative wrappers so `square-info`, `info-circle`, `info-sign` all
// reduce to `info`; `external-link-alt` to `external-link`; etc. The shapes
// match FA's own naming generations: `square-`/`circle-`/`triangle-` prefixes
// are FA6, the same as suffixes are FA5, `-alt` is FA5, `-sign` is FA3, and
// `-o` (outline) is FA4 — no FA generation ever used `-fill`/`-outline(d)`/
// `-thin`/`-solid` in glyph names (styles are family classes), so such
// suffixes are deliberately absent.
private fun String.canonicalIconName(): String {
    var n = this
    for (prefix in VARIANT_PREFIXES) {
        if (n.startsWith(prefix)) { n = n.removePrefix(prefix); break }
    }
    for (suffix in VARIANT_SUFFIXES) {
        if (n.endsWith(suffix)) { n = n.removeSuffix(suffix); break }
    }
    return n
}

private val VARIANT_PREFIXES = listOf("square-", "circle-", "triangle-")

private val VARIANT_SUFFIXES = listOf(
    "-circle", "-square", "-triangle", "-alt", "-sign", "-o",
)
