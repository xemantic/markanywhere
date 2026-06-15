/*
 * Copyright 2025-2026 Kazimierz Pogoda / Xemantic
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

package com.xemantic.markanywhere.transform

import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.flow.semanticEvents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

@DslMarker
public annotation class SemanticEventMatcherScopeDsl

@SemanticEventMatcherScopeDsl
public interface MatcherScope {

    /**
     * Lets the events nested inside the current mark flow through the
     * matcher pipeline. Optional [mode] is propagated as the child mode
     * visible to [MatcherScope.children]-aware matchers (see the
     * `mode` parameter of [TransformerBuilder.match]).
     *
     * Descent happens **only** via this call: a matcher that does not call
     * [children] (or a mark with no matcher at all) skips its entire input
     * subtree — both nested marks and text. To drop a subtree, simply omit
     * the [children] call; to descend transparently without re-emitting the
     * mark, call [children] alone.
     */
    public fun children(mode: String? = null)

    /**
     * Registers code to run right after the matching unmark has been
     * processed (and after any deferred [children] output has streamed).
     * Useful when an output event depends on information collected from
     * the subtree (e.g. emitting a synthetic `frontmatter` mark once
     * `<head>` closes).
     */
    public fun afterClose(block: suspend MatcherScope.() -> Unit)

    public suspend operator fun String.unaryPlus()

    public suspend operator fun String.invoke(
        vararg attributes: Pair<String, String>,
        block: suspend MatcherScope.() -> Unit
    ) {
        invoke(attributes.toMap(), block)
    }

    public suspend operator fun String.invoke(
        attributes: Map<String, String> = emptyMap(),
        block: suspend MatcherScope.() -> Unit
    )

    /**
     * Emits a mark/unmark pair with `isTagged = true` around [block]'s
     * output, regardless of the surrounding [tagged] / [untagged] default.
     * Mirrors [com.xemantic.markanywhere.flow.SemanticEventScope.tag].
     */
    public suspend fun tag(
        name: String,
        attributes: Map<String, String> = emptyMap(),
        block: suspend MatcherScope.() -> Unit
    )

    public suspend fun tag(
        name: String,
        vararg attributes: Pair<String, String>,
        block: suspend MatcherScope.() -> Unit
    )

    /**
     * Switches the default `isTagged` flag to `true` inside [block] so any
     * `"name" { … }` calls emit HTML-tagged marks. Nestable.
     */
    public suspend fun tagged(block: suspend MatcherScope.() -> Unit)

    /**
     * Switches the default `isTagged` flag to `false` inside [block] so any
     * `"name" { … }` calls emit untagged marks. Nestable.
     */
    public suspend fun untagged(block: suspend MatcherScope.() -> Unit)

}

/**
 * Transforms this event stream according to the rules registered in [block].
 *
 * The [block] is (re)run on **every collection**, so any mutable state it
 * captures — counters, accumulators, builders — is reconstructed fresh per
 * run and never leaks between collections of the returned flow. Package a
 * reusable rule set as an extension on [TransformerBuilder] and invoke it
 * here, e.g. `flow.simplifyHtml()`; such extensions compose
 * (`transform { simplifyHtml(); addSequenceNumbers() }`).
 */
public fun Flow<SemanticEvent>.transform(
    block: TransformerBuilder.() -> Unit
): Flow<SemanticEvent> = flow {
    val transformer = TransformerBuilder().apply(block).build()
    emitAll(transformer.transform(this@transform))
}

internal data class Matcher(
    val mark: String? = null,
    val expression: (SemanticEvent.Mark.() -> Boolean)? = null,
    val mode: String? = null,
    val block: suspend MatcherScope.(event: SemanticEvent.Mark) -> Unit
)

internal data class TextMatcher(
    val mode: String? = null,
    val block: suspend MatcherScope.(text: String) -> Unit
)

internal class TransformerImpl(
    internal val matchers: List<Matcher>,
    internal val textMatchers: List<TextMatcher>
) {

    private class MatchState(
        var childMode: String? = null,
        // set by children() — the matcher chose to descend into this subtree
        var descended: Boolean = false,
        // this frame bumped skipDepth on entry and must drop it on exit
        var incrementedSkip: Boolean = false,
        val pendingOps: MutableList<suspend () -> Unit> = mutableListOf(),
        val afterCloseOps: MutableList<suspend MatcherScope.() -> Unit> = mutableListOf()
    )

    fun transform(
        flow: Flow<SemanticEvent>
    ): Flow<SemanticEvent> = semanticEvents {

        val matchStack = mutableListOf<MatchState>()
        // > 0 while inside a subtree whose enclosing mark chose not to
        // descend (no children() call) or had no matcher at all — every
        // nested mark and text event is skipped until it closes.
        var skipDepth = 0

        fun currentMode(): String? = matchStack.lastOrNull()?.childMode

        val outputScope = this

        fun matcherScope(state: MatchState): MatcherScope = object : MatcherScope {

            var afterChildren = false
            var defaultTagged = false

            override fun children(mode: String?) {
                afterChildren = true
                state.descended = true
                state.childMode = mode
            }

            override fun afterClose(block: suspend MatcherScope.() -> Unit) {
                state.afterCloseOps += block
            }

            override suspend fun String.unaryPlus() {
                val text = this
                if (afterChildren) {
                    state.pendingOps += {
                        outputScope.text(text)
                    }
                } else {
                    outputScope.text(text)
                }
            }

            private suspend fun emitPair(
                name: String,
                isTagged: Boolean,
                attributes: Map<String, String>,
                block: suspend MatcherScope.() -> Unit
            ) {
                if (afterChildren) {
                    state.pendingOps += {
                        outputScope.mark(name, isTagged = isTagged, attributes = attributes)
                    }
                    block()
                    state.pendingOps += {
                        outputScope.unmark(name, isTagged = isTagged)
                    }
                } else {
                    outputScope.mark(name, isTagged = isTagged, attributes = attributes)
                    block()
                    if (afterChildren) {
                        // children() was called inside the block -
                        // defer this unmark until child events have streamed
                        state.pendingOps += {
                            outputScope.unmark(name, isTagged = isTagged)
                        }
                    } else {
                        outputScope.unmark(name, isTagged = isTagged)
                    }
                }
            }

            override suspend fun String.invoke(
                attributes: Map<String, String>,
                block: suspend MatcherScope.() -> Unit
            ) {
                emitPair(this, defaultTagged, attributes, block)
            }

            override suspend fun tag(
                name: String,
                attributes: Map<String, String>,
                block: suspend MatcherScope.() -> Unit
            ) {
                emitPair(name, isTagged = true, attributes, block)
            }

            override suspend fun tag(
                name: String,
                vararg attributes: Pair<String, String>,
                block: suspend MatcherScope.() -> Unit
            ) {
                emitPair(name, isTagged = true, attributes.toMap(), block)
            }

            override suspend fun tagged(block: suspend MatcherScope.() -> Unit) {
                val previous = defaultTagged
                defaultTagged = true
                try {
                    block()
                } finally {
                    defaultTagged = previous
                }
            }

            override suspend fun untagged(block: suspend MatcherScope.() -> Unit) {
                val previous = defaultTagged
                defaultTagged = false
                try {
                    block()
                } finally {
                    defaultTagged = previous
                }
            }
        }

        // Scope used for text/afterClose contexts where there is no owning
        // match frame whose lifecycle applies. children()/afterClose are
        // no-ops; emits go straight to the output flow.
        val sinkScope: MatcherScope = object : MatcherScope {

            var defaultTagged = false

            override fun children(mode: String?) { /* no children */ }
            override fun afterClose(block: suspend MatcherScope.() -> Unit) { /* no-op */ }

            override suspend fun String.unaryPlus() {
                outputScope.text(this)
            }

            override suspend fun String.invoke(
                attributes: Map<String, String>,
                block: suspend MatcherScope.() -> Unit
            ) {
                outputScope.mark(this, isTagged = defaultTagged, attributes = attributes)
                block()
                outputScope.unmark(this, isTagged = defaultTagged)
            }

            override suspend fun tag(
                name: String,
                attributes: Map<String, String>,
                block: suspend MatcherScope.() -> Unit
            ) {
                outputScope.mark(name, isTagged = true, attributes = attributes)
                block()
                outputScope.unmark(name, isTagged = true)
            }

            override suspend fun tag(
                name: String,
                vararg attributes: Pair<String, String>,
                block: suspend MatcherScope.() -> Unit
            ) {
                outputScope.mark(name, isTagged = true, attributes = attributes.toMap())
                block()
                outputScope.unmark(name, isTagged = true)
            }

            override suspend fun tagged(block: suspend MatcherScope.() -> Unit) {
                val previous = defaultTagged
                defaultTagged = true
                try {
                    block()
                } finally {
                    defaultTagged = previous
                }
            }

            override suspend fun untagged(block: suspend MatcherScope.() -> Unit) {
                val previous = defaultTagged
                defaultTagged = false
                try {
                    block()
                } finally {
                    defaultTagged = previous
                }
            }
        }

        flow.collect { event ->

            when (event) {

                is Mark -> {
                    if (skipDepth > 0) {
                        // Inside a skipped subtree — push a no-op frame so the
                        // matching Unmark keeps the stack balanced, and deepen
                        // the skip so nested marks are skipped too.
                        matchStack += MatchState(incrementedSkip = true)
                        skipDepth++
                        return@collect
                    }
                    val mode = currentMode()
                    val matcher = matchers.firstOrNull { m ->
                        m.mode == mode &&
                            (m.mark?.let { event.name == it } ?: m.expression?.invoke(event) ?: false)
                    } ?: matchers.firstOrNull { m ->
                        m.mode == null &&
                            (m.mark?.let { event.name == it } ?: m.expression?.invoke(event) ?: false)
                    }

                    val state = MatchState()
                    matchStack += state
                    if (matcher != null) {
                        matcher.block(matcherScope(state), event)
                    }
                    if (!state.descended) {
                        // No matcher, or a matcher that did not call children():
                        // skip this mark's entire input subtree (nested marks
                        // and text) until the matching unmark.
                        state.incrementedSkip = true
                        skipDepth++
                    }
                }

                is Text -> {
                    if (skipDepth > 0) return@collect
                    val mode = currentMode()
                    val matcher = textMatchers.firstOrNull { it.mode == mode }
                        ?: textMatchers.firstOrNull { it.mode == null }
                    // Text survives only via an explicit matchText rule; with
                    // no matching rule it is dropped (no implicit passthrough).
                    matcher?.block(sinkScope, event.text)
                }

                is Unmark -> {
                    if (matchStack.isEmpty()) return@collect
                    val state = matchStack.removeLast()
                    if (state.incrementedSkip) skipDepth--
                    for (op in state.pendingOps) op()
                    for (op in state.afterCloseOps) op(sinkScope)
                }

            }

        }

    }

}

public class TransformerBuilder {

    private val matchers = mutableListOf<Matcher>()
    private val textMatchers = mutableListOf<TextMatcher>()

    /**
     * Registers a [block] to run for every mark whose name equals [mark]
     * while inside [mode]. The special name `"*"` is a wildcard that
     * matches any mark — use it to descend transparently through marks
     * that have no dedicated rule (e.g. `match("*") { children() }`).
     */
    public fun match(
        mark: String,
        mode: String? = null,
        block: suspend MatcherScope.(event: SemanticEvent.Mark) -> Unit
    ) {
        matchers += if (mark == "*") {
            Matcher(expression = { true }, mode = mode, block = block)
        } else {
            Matcher(mark = mark, mode = mode, block = block)
        }
    }

    public fun match(
        expression: SemanticEvent.Mark.() -> Boolean,
        mode: String? = null,
        block: suspend MatcherScope.(event: SemanticEvent.Mark) -> Unit
    ) {
        matchers += Matcher(expression = expression, mode = mode, block = block)
    }

    public fun matchText(
        mode: String? = null,
        block: suspend MatcherScope.(text: String) -> Unit
    ) {
        textMatchers += TextMatcher(mode = mode, block = block)
    }

    /**
     * Re-emits every mark (and its text content) untouched while inside
     * [mode]. The mark's [SemanticEvent.Mark.isTagged] flag and attributes
     * are preserved; children are recursed in the same mode so the entire
     * subtree flows through unchanged.
     *
     * Useful when a parent matcher hands off a subtree that should reach
     * the output verbatim — e.g. `match("nav") { tag("nav") { children(mode = "navigation") } }`
     * followed by `passthrough(mode = "navigation")`.
     */
    public fun passthrough(mode: String? = null) {
        matchers += Matcher(
            expression = { true },
            mode = mode,
            block = { event ->
                if (event.isTagged) {
                    tag(event.name, event.attributes) {
                        children(mode = mode)
                    }
                } else {
                   event.name(event.attributes) {
                       children(mode = mode)
                   }
                }
            }
        )
        textMatchers += TextMatcher(mode = mode) { +it }
    }

    internal fun build(): TransformerImpl = TransformerImpl(matchers, textMatchers)

}



