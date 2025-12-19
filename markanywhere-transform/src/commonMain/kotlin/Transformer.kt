/*
 * Copyright 2025 Kazimierz Pogoda / Xemantic
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

public interface MatcherScope {

    public fun children(mode: String? = null)

    public suspend operator fun String.unaryPlus()

    public suspend operator fun String.invoke(
        attributes: Map<String, String> = emptyMap(),
        end: String? = defaultEnd(mark = this),
        block: MatcherScope.() -> Unit
    )

}

public fun defaultEnd(mark: String): String? = if (mark.startsWith("<")) {
    val afterMarkup = mark.substring(startIndex = 1)
    "</$afterMarkup"
} else {
    null
}

public fun Transformer(
    block: TransformerBuilder.() -> Unit
): Transformer {
    val builder = TransformerBuilder()
    builder.block()
    return builder.build()
}

public interface Transformer {
    public fun transform(
        flow: Flow<SemanticEvent>
    ): Flow<SemanticEvent>
}

internal data class Matcher(
    val mark: String? = null,
    val expression: (SemanticEvent.Mark.() -> Boolean)? = null,
    val mode: String? = null,
    val block: suspend MatcherScope.(event: SemanticEvent.Mark) -> Unit
)

internal data class TextMatcher(
    val block: suspend MatcherScope.(text: String) -> Unit
)

@PublishedApi
internal class TransformerImpl(
    internal val matchers: List<Matcher>,
    internal val textMatcher: TextMatcher?
) : Transformer {

    private class MatchState(
        var childMode: String? = null,
        val pendingText: MutableList<String> = mutableListOf()
    )

    override fun transform(
        flow: Flow<SemanticEvent>
    ): Flow<SemanticEvent> = semanticEvents {

        val matchStack = mutableListOf<MatchState>()

        fun currentMode(): String? = matchStack.lastOrNull()?.childMode

        val outputScope = this

        flow.collect { event ->

            when (event) {

                is SemanticEvent.Mark -> {
                    val mode = currentMode()
                    val matcher = matchers.firstOrNull { m ->
                        (m.mode == null || m.mode == mode) &&
                            (m.mark?.let { event.name == it } ?: m.expression?.invoke(event) ?: false)
                    }

                    if (matcher != null) {
                        val state = MatchState()
                        matchStack.add(state)

                        var afterChildren = false

                        val scope = object : MatcherScope {
                            override fun children(mode: String?) {
                                afterChildren = true
                                matchStack.last().childMode = mode
                            }

                            override suspend fun String.unaryPlus() {
                                if (afterChildren) {
                                    matchStack.last().pendingText.add(this)
                                } else {
                                    outputScope.text(this)
                                }
                            }

                            override suspend fun String.invoke(
                                attributes: Map<String, String>,
                                end: String?,
                                block: MatcherScope.() -> Unit
                            ) {
                                val markName = this
                                if (afterChildren) {
                                    // Buffer the mark operation for after children
                                    matchStack.last().pendingText.add(markName)
                                } else {
                                    outputScope.mark(markName, attributes = attributes)
                                    block()
                                    if (end != null) {
                                        outputScope.unmark(end)
                                    }
                                }
                            }
                        }

                        matcher.block(scope, event)

                    } else {
                        // No matcher - still track for proper nesting
                        matchStack.add(MatchState(childMode = currentMode()))
                    }
                }

                is SemanticEvent.Text -> {
                    if (textMatcher != null) {
                        val scope = object : MatcherScope {
                            override fun children(mode: String?) {
                                // No children for text nodes
                            }

                            override suspend fun String.unaryPlus() {
                                outputScope.text(this)
                            }

                            override suspend fun String.invoke(
                                attributes: Map<String, String>,
                                end: String?,
                                block: MatcherScope.() -> Unit
                            ) {
                                outputScope.mark(this, attributes = attributes)
                                block()
                                if (end != null) {
                                    outputScope.unmark(end)
                                }
                            }
                        }
                        textMatcher.block(scope, event.text)
                    } else {
                        outputScope.text(event.text)
                    }
                }

                is SemanticEvent.Unmark -> {
                    if (matchStack.isNotEmpty()) {
                        val state = matchStack.removeLast()
                        for (text in state.pendingText) {
                            outputScope.text(text)
                        }
                    }
                }

            }

        }

    }

}

public class TransformerBuilder {

    private val matchers = mutableListOf<Matcher>()
    private var textMatcher: TextMatcher? = null

    public fun match(
        mark: String,
        mode: String? = null,
        block: suspend MatcherScope.(event: SemanticEvent.Mark) -> Unit
    ) {
        matchers.add(Matcher(mark = mark, mode = mode, block = block))
    }

    public fun match(
        expression: SemanticEvent.Mark.() -> Boolean,
        mode: String? = null,
        block: suspend MatcherScope.(event: SemanticEvent.Mark) -> Unit
    ) {
        matchers.add(Matcher(expression = expression, mode = mode, block = block))
    }

    public fun matchText(
        block: suspend MatcherScope.(text: String) -> Unit
    ) {
        textMatcher = TextMatcher(block)
    }

    internal fun build(): Transformer = TransformerImpl(matchers, textMatcher)

}

public fun Flow<SemanticEvent>.transform(
    transformer: Transformer
): Flow<SemanticEvent> = transformer.transform(this)
