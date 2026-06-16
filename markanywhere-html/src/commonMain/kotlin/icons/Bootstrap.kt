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

// Most common Bootstrap Icons glyph names → the closest Unicode emoji.
// Decorative variants are NOT listed: `canonicalIconName()` reduces
// `info-circle-fill` → `info`, `exclamation-triangle` → `exclamation`, etc.
// Extend as new icons are encountered; an unmapped recognized glyph falls back
// to a `:name:` hint.
private val iconMap = mapOf(
    "alarm" to "⏰",
    "archive" to "🗃️",
    "arrow-clockwise" to "🔄",
    "arrow-counterclockwise" to "↩️",
    "arrow-down" to "⬇️",
    "arrow-down-left" to "↙️",
    "arrow-down-right" to "↘️",
    "arrow-down-up" to "↕️",
    "arrow-left" to "⬅️",
    "arrow-left-right" to "↔️",
    "arrow-repeat" to "🔄",
    "arrow-right" to "➡️",
    "arrow-up" to "⬆️",
    "arrow-up-left" to "↖️",
    "arrow-up-right" to "↗️",
    "award" to "🏅",
    "bag" to "🛍️",
    "ban" to "🚫",
    "bank" to "🏦",
    "bar-chart" to "📊",
    "basket" to "🛒",
    "battery" to "🔋",
    "battery-charging" to "🔋",
    "bell" to "🔔",
    "bell-slash" to "🔕",
    "book" to "📖",
    "bookmark" to "🔖",
    "box" to "📦",
    "box-arrow-in-right" to "🚪",
    "box-arrow-right" to "🚪",
    "box-arrow-up-right" to "↗️",
    "briefcase" to "💼",
    "brightness-high" to "🔆",
    "brush" to "🖌️",
    "bug" to "🐛",
    "building" to "🏢",
    "calculator" to "🧮",
    "calendar" to "📅",
    "calendar-event" to "📅",
    "camera" to "📷",
    "camera-video" to "🎥",
    "caret-down" to "🔽",
    "caret-left" to "◀️",
    "caret-right" to "▶️",
    "caret-up" to "🔼",
    "cart" to "🛒",
    "cash" to "💵",
    "cash-coin" to "💰",
    "cash-stack" to "💵",
    "chat" to "💬",
    "chat-dots" to "💬",
    "check" to "✅",
    "check-all" to "✅",
    "check2" to "✅",
    "check2-all" to "✅",
    "chevron-down" to "🔽",
    "chevron-left" to "◀️",
    "chevron-right" to "▶️",
    "chevron-up" to "🔼",
    "circle" to "⚪",
    "clipboard" to "📋",
    "clock" to "🕒",
    "cloud" to "☁️",
    "cloud-arrow-down" to "⬇️",
    "cloud-arrow-up" to "⬆️",
    "cloud-download" to "⬇️",
    "cloud-upload" to "⬆️",
    "code" to "💻",
    "coin" to "🪙",
    "compass" to "🧭",
    "copy" to "📋",
    "credit-card" to "💳",
    "cup" to "☕",
    "cup-hot" to "☕",
    "currency-bitcoin" to "₿",
    "currency-dollar" to "💲",
    "currency-euro" to "€",
    "currency-pound" to "£",
    "currency-rupee" to "₹",
    "currency-yen" to "¥",
    "dash" to "➖",
    "database" to "🗄️",
    "display" to "🖥️",
    "door-open" to "🚪",
    "dot" to "•",
    "download" to "⬇️",
    "droplet" to "💧",
    "emoji-angry" to "😠",
    "emoji-astonished" to "😲",
    "emoji-dizzy" to "😵",
    "emoji-expressionless" to "😑",
    "emoji-frown" to "☹️",
    "emoji-grimace" to "😬",
    "emoji-grin" to "😀",
    "emoji-heart-eyes" to "😍",
    "emoji-kiss" to "😘",
    "emoji-laughing" to "😆",
    "emoji-neutral" to "😐",
    "emoji-smile" to "🙂",
    "emoji-smile-upside-down" to "🙃",
    "emoji-sunglasses" to "😎",
    "emoji-surprise" to "😮",
    "emoji-tear" to "😢",
    "emoji-wink" to "😉",
    "envelope" to "✉️",
    "envelope-at" to "📧",
    "exclamation" to "⚠️",
    "eye" to "👁️",
    "eye-slash" to "🙈",
    "fast-forward" to "⏩",
    "file" to "📄",
    "file-earmark" to "📄",
    "file-earmark-pdf" to "📕",
    "file-pdf" to "📕",
    "file-text" to "📄",
    "film" to "🎬",
    "filter" to "🔻",
    "fire" to "🔥",
    "flag" to "🚩",
    "folder" to "📁",
    "funnel" to "🔻",
    "gear" to "⚙️",
    "gem" to "💎",
    "geo" to "📍",
    "geo-alt" to "📍",
    "gift" to "🎁",
    "globe" to "🌐",
    "graph-down" to "📉",
    "graph-up" to "📈",
    "hammer" to "🔨",
    "hand-thumbs-down" to "👎",
    "hand-thumbs-up" to "👍",
    "hash" to "#️⃣",
    "headphones" to "🎧",
    "headset" to "🎧",
    "heart" to "❤️",
    "hourglass" to "⏳",
    "house" to "🏠",
    "house-door" to "🏠",
    "image" to "🖼️",
    "images" to "🖼️",
    "inbox" to "📥",
    "infinity" to "♾️",
    "info" to "ℹ️",
    "journal" to "📓",
    "journal-text" to "📓",
    "key" to "🔑",
    "keyboard" to "⌨️",
    "laptop" to "💻",
    "life-preserver" to "🛟",
    "lightbulb" to "💡",
    "lightning" to "⚡",
    "link" to "🔗",
    "link-45deg" to "🔗",
    "list" to "☰",
    "lock" to "🔒",
    "magic" to "🪄",
    "map" to "🗺️",
    "megaphone" to "📢",
    "mic" to "🎤",
    "mic-mute" to "🔇",
    "moon" to "🌙",
    "moon-stars" to "🌙",
    "mortarboard" to "🎓",
    "mouse" to "🖱️",
    "music-note" to "🎵",
    "music-note-beamed" to "🎵",
    "newspaper" to "📰",
    "palette" to "🎨",
    "paperclip" to "📎",
    "patch-check" to "✅",
    "pause" to "⏸️",
    "pen" to "✏️",
    "pencil" to "✏️",
    "people" to "👥",
    "percent" to "%",
    "person" to "👤",
    "person-plus" to "👤",
    "phone" to "📱",
    "pie-chart" to "📊",
    "pin" to "📌",
    "pin-angle" to "📌",
    "play" to "▶️",
    "plug" to "🔌",
    "plus" to "➕",
    "printer" to "🖨️",
    "puzzle" to "🧩",
    "question" to "❓",
    "receipt" to "🧾",
    "record" to "⏺️",
    "recycle" to "♻️",
    "repeat" to "🔁",
    "repeat-1" to "🔂",
    "reply" to "↩️",
    "rewind" to "⏪",
    "robot" to "🤖",
    "rocket" to "🚀",
    "rocket-takeoff" to "🚀",
    "rss" to "📡",
    "save" to "💾",
    "scissors" to "✂️",
    "search" to "🔍",
    "send" to "📤",
    "share" to "🔗",
    "shield" to "🛡️",
    "shield-check" to "🛡️",
    "shield-lock" to "🛡️",
    "shop" to "🏪",
    "shuffle" to "🔀",
    "skip-backward" to "⏮️",
    "skip-end" to "⏭️",
    "skip-forward" to "⏭️",
    "skip-start" to "⏮️",
    // `slash-circle` is Bootstrap's "disabled/ban" icon, but the canonicalizer
    // would mis-reduce it to the unrelated bare `slash` — hence both variants
    // listed explicitly, exception to the no-decorative-variants rule.
    "slash-circle" to "🚫",
    "slash-circle-fill" to "🚫",
    "sliders" to "🎛️",
    "snow" to "❄️",
    "star" to "⭐",
    "stars" to "✨",
    "stop" to "⏹️",
    "stopwatch" to "⏱️",
    "sun" to "☀️",
    "tag" to "🏷️",
    "tags" to "🏷️",
    "telephone" to "📞",
    "terminal" to "💻",
    "thermometer" to "🌡️",
    "three-dots" to "⋯",
    "three-dots-vertical" to "⋮",
    "ticket" to "🎫",
    "tools" to "🛠️",
    "translate" to "🌐",
    "trash" to "🗑️",
    "tree" to "🌳",
    "trophy" to "🏆",
    "truck" to "🚚",
    "tv" to "📺",
    "universal-access" to "♿",
    "unlock" to "🔓",
    "upload" to "⬆️",
    "volume-down" to "🔉",
    "volume-mute" to "🔇",
    "volume-off" to "🔈",
    "volume-up" to "🔊",
    "wallet" to "👛",
    "watch" to "⌚",
    "wifi" to "📶",
    "wrench" to "🔧",
    "x" to "❌",
    "zoom-in" to "🔍",
    "zoom-out" to "🔍",
)

/**
 * Resolves [Bootstrap Icons](https://icons.getbootstrap.com) — `<i class="bi
 * bi-info-circle">`. The glyph name is the `bi-<name>` class token; the bare
 * `bi` family token carries no glyph.
 */
public val BootstrapIconResolver: MarkSelect = { mark ->
    // The glyph is the first `bi-<name>` token with a non-empty name — which by
    // itself proves this is a Bootstrap Icons element, so no separate family
    // check is needed. A bare `bi` or stray `bi-` yields no glyph: decline
    // rather than emit an empty icon.
    val glyph = mark.classList
        .firstOrNull { it.length > "bi-".length && it.startsWith("bi-") }
        ?.removePrefix("bi-")
    if (glyph != null) {
        { "icon" { +glyph.toIconToken() } }
    } else null
}

// A recognized glyph resolves to its curated emoji (looked up raw, then after
// canonicalization), otherwise to a `:name:` hint so the label survives downstream.
private fun String.toIconToken(): String =
    iconMap[this] ?: iconMap[canonicalIconName()] ?: ":$this:"

// Strips decorative variant suffixes so `info-circle-fill`, `x-octagon`,
// `check-lg` all reduce to their base name. Bootstrap stacks suffixes
// (`-circle-fill`), hence the loop.
private fun String.canonicalIconName(): String {
    var n = this
    var changed = true
    while (changed) {
        changed = false
        for (suffix in VARIANT_SUFFIXES) {
            if (n.length > suffix.length && n.endsWith(suffix)) {
                n = n.removeSuffix(suffix)
                changed = true
            }
        }
    }
    return n
}

private val VARIANT_SUFFIXES = listOf(
    "-fill", "-circle", "-square", "-triangle", "-octagon",
    "-diamond", "-btn", "-lg", "-short",
)

