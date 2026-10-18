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

package com.xemantic.markanywhere.browse

import com.xemantic.kotlin.test.assert
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.dump.SemanticEventDump
import dev.kdriver.core.browser.Browser
import dev.kdriver.core.tab.Tab
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * How real Chrome composes a secret control's value into an accessible name,
 * and what the capture makes of it when it resolves the name of an element
 * labelled with `aria-labelledby` (see `SnapshotDom.withoutSecrets`). Issues
 * #120, #122 and #123.
 */
class SecretNameTest {

    /**
     * Blink keeps a card number's non-breaking spaces in the name and collapses
     * its tabs as HTML whitespace — the same normalisation the capture applies
     * to the value — so the number is found and cut out, the label kept.
     */
    @Test
    fun `should take a card number with separators out of a resolved name`() = runTest {
        val dump = runInBrowser { browser ->
            // given
            val tab = openPage(browser)

            // when
            PageSession(tab).dump()
        }

        // then
        assert(dump.ariaLabel("b-card-nbsp") == "Card number")
        assert(dump.ariaLabel("b-card-tabs") == "Card number")
    }

    /**
     * Blink names a date, time or number control from the value it *displays*
     * — formatted in the browser's locale (`May 2027`, `31 . 05 . 2027`,
     * `1234,50`) — never from the value string the capture reads, so the
     * secret is not found as written and must not leak through the name.
     */
    @Test
    fun `should not leak a secret value Blink formats for display`() = runTest {
        val dump = runInBrowser { browser ->
            // given
            val tab = openPage(browser)

            // when
            PageSession(tab).dump()
        }

        // then
        listOf("b-exp-month", "b-exp-date", "b-exp-week", "b-exp-local", "b-otp-time").forEach { id ->
            assert(dump.ariaLabel(id) == null)
        }
        // a locale with a decimal point names it as written, one with a decimal comma does not
        val number = dump.ariaLabel("b-csc-number")
        assert(number == null || number == "Expires")
    }

    /**
     * Blink writes a number control's value without digit grouping — `4321`,
     * `7654321`, never `4.321` or `7 654 321` — so a whole number is named as
     * written and cut out of the name, the label kept.
     */
    @Test
    fun `should take a whole number Blink names without digit grouping out of a name`() = runTest {
        val dump = runInBrowser { browser ->
            // given
            val tab = openPage(browser)

            // when
            PageSession(tab).dump()
        }

        // then
        assert(dump.ariaLabel("b-csc-thousand") == "Expires")
        assert(dump.ariaLabel("b-card-million") == "Expires")
    }

    /**
     * Blink names a password field by its mask — a bullet per character — and
     * never by the value, whether it came with the markup or was typed: a
     * password cannot reach a name, so it does not withhold an unrelated name
     * that happens to contain the same text.
     */
    @Test
    fun `should leave a password value to the mask Blink names it by`() = runTest {
        val dump = runInBrowser { browser ->
            // given - a password typed into a field, beside one filled in the markup
            val tab = openPage(browser)
            tab.select("#pw-typed").sendKeys("1234")

            // when
            PageSession(tab).dump()
        }

        // then
        val bullets = "••••"
        assert(dump.ariaLabel("b-pw") == "Password $bullets")
        assert(dump.ariaLabel("b-pw-direct") == "Order 1234 $bullets")
        assert(dump.ariaLabel("b-pw-typed") == "Password $bullets $bullets")
        assert(dump.ariaLabel("b-order") == "Order 1234")
    }

    /**
     * Blink parses `type` untrimmed, so a `type=" password"` is a text field
     * that names its value as written — the capture, redacting it all the
     * same, still has to cut that value out of the name.
     */
    @Test
    fun `should take the value of a text field with a padded password type out of a name`() = runTest {
        val dump = runInBrowser { browser ->
            // given
            val tab = openPage(browser)

            // when
            PageSession(tab).dump()
        }

        // then
        assert(dump.ariaLabel("b-pw-spaced") == "Code")
    }

    /**
     * A short secret on the page — a chosen expiry month `1`, a CVC `123`, the
     * numbers of a filled expiry date `2027-01-01` — is no reason to cut or
     * withhold a name Blink spelled from its targets' own text. Issue #120.
     */
    @Test
    fun `should keep a name spelled from its own text whatever short secrets the page holds`() = runTest {
        val dump = runInBrowser { browser ->
            // given
            val tab = openPage(browser)

            // when
            PageSession(tab).dump()
        }

        // then
        assert(dump.ariaLabel("b-step") == "Step 1 of 3")
        assert(dump.ariaLabel("b-copy") == "\u00A9 2027 Acme")
        assert(dump.ariaLabel("b-items") == "10 items")
        // Blink skips the hidden icon text, which the name may leave out
        assert(dump.ariaLabel("b-cart") == "1 item")
        assert(dump.ariaLabel("b-step-cvc") == "Step 1 of 3")
    }

    /**
     * The paths along which Blink brings a secret into a target's text — a
     * field the target owns (`aria-owns`), one in its closed shadow tree, one
     * in a `display:none` target, a secret select inside it — each make the
     * target's text more than its own, so the secret is still taken out.
     * Issues #101 and #120.
     */
    @Test
    fun `should take a secret out of a target Blink filled from beyond its own text`() = runTest {
        val dump = runInBrowser { browser ->
            // given
            val tab = openPage(browser)

            // when
            PageSession(tab).dump()
        }

        // then
        assert(dump.ariaLabel("b-owner") == "Code")
        assert(dump.ariaLabel("b-host") == "Shadow")
        assert(dump.ariaLabel("b-hidden") == "Hidden")
        assert(dump.ariaLabel("b-pick") == "Pick")
    }

    /**
     * Text a target holds but Blink does not lend — hidden, `aria-hidden`,
     * `inert`, `<script>`, `<style>`, `<noscript>`, `<template>`, an unselected
     * option, unslotted light DOM, a duplicate of a hidden copy, the text of an
     * element named from elsewhere (its own `aria-labelledby`) — can equal the secret
     * Blink lent from a field, and must not make the target look as though it
     * spelled the secret from its own text. Issue #120 (code review).
     */
    @Test
    fun `should not mistake a hidden copy of a secret for the target's own text`() = runTest {
        val dump = runInBrowser { browser ->
            // given
            val tab = openPage(browser)

            // when
            PageSession(tab).dump()
        }

        // then
        val leaked = mapOf(
            "b-m-hidden-copy" to "05",
            "b-m-none-copy" to "917",
            "b-m-script" to "638",
            "b-m-option" to "5556",
            "b-m-style" to "551177",
            "b-m-digits" to "8",
            "b-m-unslotted" to "773",
            "b-m-order" to "229",
            "b-m-twice" to "404",
            "b-m-owner" to "612",
            "b-m-none-target" to "09",
            "b-m-noscript" to "371",
            "b-m-masked" to "hunter",
            "b-m-keypad" to "1357",
            "b-m-listbox" to "08",
            "b-m-inert" to "919",
            "b-m-nested" to "920",
            "b-m-nested-two" to "944",
            "b-m-nested-deep" to "926",
            "b-m-nested-select" to "07",
        ).filter { (id, secret) -> dump.ariaLabel(id)?.contains(secret) == true }.keys
        assert(leaked.isEmpty())
    }

    /**
     * Inside an open modal dialog too, an `inert` copy of a secret is text
     * Blink does not lend. Issue #120 (code review).
     */
    @Test
    fun `should not mistake an inert copy of a secret in a modal dialog for the target's own text`() = runTest {
        val dump = runInBrowser { browser ->
            // given
            val tab = browser.get(testPageUrl("secret-names-modal.html")).also { it.waitUntilLoaded() }

            // when
            PageSession(tab).dump()
        }

        // then
        val label = dump.ariaLabel("b-m-modal")
        assert(label == null || "943" !in label)
    }

    private suspend fun openPage(browser: Browser): Tab =
        browser.get(testPageUrl("secret-names.html")).also { it.waitUntilLoaded() }

    private fun SemanticEventDump.ariaLabel(id: String): String? = events
        .filterIsInstance<SemanticEvent.Mark>()
        .single { it["id"] == id }["aria-label"]

}
