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

package com.xemantic.markanywhere.transform

import com.xemantic.kotlin.test.assert
import com.xemantic.markanywhere.SemanticEvent
import com.xemantic.markanywhere.flow.semanticEvents
import com.xemantic.markanywhere.test.sameAs
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Tests of [MatcherScope.defer] — lazily evaluated emission spliced into the
 * deferred output stream (issue #72).
 */
class DeferTest {

    @Test
    fun `should splice lazily computed content before the deferred close`() = runTest {
        // given
        val events = semanticEvents {
            "body" {
                "h2" { +"Alpha" }
                "h2" { +"Beta" }
            }
        }

        // when - the toc list is populated while the headings stream; defer
        //   runs at the body unmark, so it sees the complete list, and its
        //   output lands before the deferred </body> - as the last child
        val transformed = events.transform {
            val toc = mutableListOf<String>()
            match("body") {
                "body" {
                    children(mode = "content")
                    defer {
                        "nav" {
                            toc.forEach { title ->
                                "a" { +title }
                            }
                        }
                    }
                }
            }
            match("h2", mode = "content") {
                "h2" { children(mode = "heading") }
            }
            matchText(mode = "heading") {
                toc += it
                +it
            }
        }

        // then
        transformed sameAs semanticEvents {
            "body" {
                "h2" { +"Alpha" }
                "h2" { +"Beta" }
                "nav" {
                    "a" { +"Alpha" }
                    "a" { +"Beta" }
                }
            }
        }
    }

    @Test
    fun `should emit computed content between two deferred closes`() = runTest {
        // given
        val events = semanticEvents {
            "body" {
                "h2" { +"Alpha" }
                "p" { +"text" }
            }
        }

        // when - the content is wrapped in main, and the collected toc must
        //   land outside of it: between </main> and </body>
        val transformed = events.transform {
            val toc = mutableListOf<String>()
            match("body") {
                "body" {
                    "main"("class" to "responsive") {
                        children(mode = "content")
                    }
                    defer {
                        "aside" {
                            toc.forEach { title ->
                                "a" { +title }
                            }
                        }
                    }
                }
            }
            match("h2", mode = "content") {
                "h2" { children(mode = "heading") }
            }
            match("p", mode = "content") {
                "p" { children(mode = "content") }
            }
            matchText(mode = "heading") {
                toc += it
                +it
            }
            matchText(mode = "content") { +it }
        }

        // then
        transformed sameAs semanticEvents {
            "body" {
                "main"("class" to "responsive") {
                    "h2" { +"Alpha" }
                    "p" { +"text" }
                }
                "aside" {
                    "a" { +"Alpha" }
                }
            }
        }
    }

    @Test
    fun `should relocate a matched subtree outside of its synthetic wrapper`() = runTest {
        // given - an aside authored amidst the content
        val events = semanticEvents {
            "body" {
                "p" { +"text" }
                "aside" {
                    "a"("href" to "#alpha") { +"Alpha" }
                }
            }
        }

        // when - the content is wrapped in main, while the aside links are
        //   captured during streaming and replayed outside of the wrapper
        val transformed = events.transform {
            val links = mutableListOf<Pair<String, String>>()
            var currentHref = ""
            match("body") {
                "body" {
                    "main" {
                        children(mode = "content")
                    }
                    defer {
                        "aside" {
                            links.forEach { (href, title) ->
                                "a"("href" to href) { +title }
                            }
                        }
                    }
                }
            }
            // capture only - the aside is not re-emitted in place
            match("aside", mode = "content") {
                children(mode = "aside")
            }
            match("a", mode = "aside") { mark ->
                currentHref = mark["href"] ?: ""
                children(mode = "aside-link")
            }
            matchText(mode = "aside-link") { links += currentHref to it }
            match("p", mode = "content") {
                "p" { children(mode = "content") }
            }
            matchText(mode = "content") { +it }
        }

        // then - the aside ends up as a sibling of main, not inside it
        transformed sameAs semanticEvents {
            "body" {
                "main" {
                    "p" { +"text" }
                }
                "aside" {
                    "a"("href" to "#alpha") { +"Alpha" }
                }
            }
        }
    }

    @Test
    fun `should keep source order between deferred emissions and defer blocks`() = runTest {
        // given
        val events = semanticEvents {
            "section" { +"body" }
        }

        // when - static deferred emissions and a defer block share one queue
        val transformed = events.transform {
            match("section") {
                "section" { children() }
                "static-1" {}
                defer { "lazy" {} }
                "static-2" {}
            }
            matchText { +it }
        }

        // then - </section>, <static-1/>, <lazy/>, <static-2/>
        transformed sameAs semanticEvents {
            "section" { +"body" }
            "static-1" {}
            "lazy" {}
            "static-2" {}
        }
    }

    @Test
    fun `should run several defer blocks in source order`() = runTest {
        // given
        val events = semanticEvents {
            "section" { +"x" }
        }

        // when
        val transformed = events.transform {
            var count = 0
            match("section") {
                "section" {
                    children(mode = "count")
                    defer { "first" { +"$count" } }
                    defer { "second" { +"$count" } }
                }
            }
            matchText(mode = "count") {
                count++
                +it
            }
        }

        // then - both see the complete subtree, and keep their order
        transformed sameAs semanticEvents {
            "section" {
                +"x"
                "first" { +"1" }
                "second" { +"1" }
            }
        }
    }

    @Test
    fun `should splice a defer called before children right after the subtree`() = runTest {
        // given
        val events = semanticEvents {
            "section" { +"body" }
        }

        // when - output before children() is emitted eagerly, so a defer
        //   registered there lands at the earliest deferred position: right
        //   after the subtree, ahead of the deferred output that follows it
        val transformed = events.transform {
            val seen = StringBuilder()
            match("section") {
                "section" {
                    "eager" {}
                    defer { "lazy" { +seen.toString() } }
                    children(mode = "seen")
                    "trailing" {}
                }
            }
            matchText(mode = "seen") {
                seen.append(it)
                +it
            }
        }

        // then - the defer still runs after the subtree has streamed
        transformed sameAs semanticEvents {
            "section" {
                "eager" {}
                +"body"
                "lazy" { +"body" }
                "trailing" {}
            }
        }
    }

    @Test
    fun `should splice a defer into the matched frame not into the eagerly closed mark around it`() = runTest {
        // given
        val events = semanticEvents {
            "body" { +"x" }
        }

        // when - the defer sits inside header, which closes eagerly before
        //   the subtree streams; the slot belongs to the matched body frame
        val transformed = events.transform {
            match("body") {
                "body" {
                    "header" { defer { "lazy" {} } }
                    "main" { children() }
                }
            }
            matchText { +it }
        }

        // then - lazy lands at the earliest deferred position: inside main,
        //   right after the subtree, not inside header
        transformed sameAs semanticEvents {
            "body" {
                "header" {}
                "main" {
                    +"x"
                    "lazy" {}
                }
            }
        }
    }

    @Test
    fun `should run nested defer blocks innermost-first at their own unmark`() = runTest {
        // given
        val events = semanticEvents {
            "outer" {
                "inner" { +"a" }
                "inner" { +"b" }
            }
        }

        // when - each level counts the text streamed so far; the inner defer
        //   fires at each inner unmark, the outer one at the outer unmark
        val transformed = events.transform {
            val streamed = StringBuilder()
            match("outer") {
                "outer" { children() }
                defer { "outer-done" { +streamed.toString() } }
            }
            match("inner") {
                "inner" {
                    children()
                    defer { "inner-done" { +streamed.toString() } }
                }
            }
            matchText {
                streamed.append(it)
                +it
            }
        }

        // then
        transformed sameAs semanticEvents {
            "outer" {
                "inner" {
                    +"a"
                    "inner-done" { +"a" }
                }
                "inner" {
                    +"b"
                    "inner-done" { +"ab" }
                }
            }
            "outer-done" { +"ab" }
        }
    }

    @Test
    fun `should run a defer inside a defer block in place`() = runTest {
        // given
        val events = semanticEvents {
            "section" { +"body" }
        }

        // when - a defer block runs at the unmark on a sink scope, where the
        //   subtree has already streamed: a nested defer has nothing left to
        //   wait for and runs in place, and children() is a no-op
        val transformed = events.transform {
            match("section") {
                "section" {
                    children()
                    defer {
                        "outer" {
                            children() // no-op
                            defer { "inner" {} }
                            "after" {}
                        }
                    }
                }
            }
            matchText { +it }
        }

        // then
        transformed sameAs semanticEvents {
            "section" {
                +"body"
                "outer" {
                    "inner" {}
                    "after" {}
                }
            }
        }
    }

    @Test
    fun `should honour an afterClose registered inside a defer block`() = runTest {
        // given
        val events = semanticEvents {
            "section" { +"body" }
        }

        // when - the defer block runs before the frame's afterClose hooks,
        //   so an afterClose registered there still has a hook to join
        val transformed = events.transform {
            match("section") {
                afterClose { "first" {} }
                "section" {
                    children()
                    defer {
                        afterClose { "second" {} }
                        "lazy" {}
                    }
                }
            }
            matchText { +it }
        }

        // then - it runs after the matched close, after the hooks already
        //   registered
        transformed sameAs semanticEvents {
            "section" {
                +"body"
                "lazy" {}
            }
            "first" {}
            "second" {}
        }
    }

    @Test
    fun `should run a deferred op appended while the queue drains`() = runTest {
        // given
        val events = semanticEvents {
            "x" { +"t" }
        }

        // when - a defer block reaches back to the matcher scope and defers
        //   again while the frame's queue is draining
        val transformed = events.transform {
            match("x") {
                val outer = this
                "x" {
                    children()
                    defer { outer.defer { "late" {} } }
                }
            }
            matchText { +it }
        }

        // then - the late op joins the end of the queue, after </x>
        transformed sameAs semanticEvents {
            "x" { +"t" }
            "late" {}
        }
    }

    @Test
    fun `should run an afterClose hook appended while the hooks drain`() = runTest {
        // given
        val events = semanticEvents {
            "x" { +"t" }
        }

        // when - an afterClose hook registers another one on the matcher scope
        val transformed = events.transform {
            match("x") {
                val outer = this
                "x" { children() }
                afterClose { outer.afterClose { "late" {} } }
            }
            matchText { +it }
        }

        // then
        transformed sameAs semanticEvents {
            "x" { +"t" }
            "late" {}
        }
    }

    @Test
    fun `should run a defer inside a matchText block in place`() = runTest {
        // given
        val events = semanticEvents {
            "p" { +"hello" }
        }

        // when - matchText has no owning frame to defer to
        val transformed = events.transform {
            match("p") { "p" { children() } }
            matchText {
                defer { "b" { +it } }
                +"!"
            }
        }

        // then
        transformed sameAs semanticEvents {
            "p" {
                "b" { +"hello" }
                +"!"
            }
        }
    }

    @Test
    fun `should run a defer of a matcher that skips its subtree at its unmark`() = runTest {
        // given
        val events = semanticEvents {
            "p" { +"before" }
            "aside" { "p" { +"dropped" } }
            "p" { +"after" }
        }

        // when - the aside matcher does not call children(), so its subtree is
        //   skipped; the defer still runs at the aside unmark
        val transformed = events.transform {
            var streamedText = 0
            match("p") { "p" { children() } }
            match("aside") {
                defer { "note" { +"streamed text events: $streamedText" } }
            }
            matchText {
                streamedText++
                +it
            }
        }

        // then - the dropped text never reached matchText (counter still 1)
        transformed sameAs semanticEvents {
            "p" { +"before" }
            "note" { +"streamed text events: 1" }
            "p" { +"after" }
        }
    }

    @Test
    fun `should land defer output before the matched close and afterClose output after it`() = runTest {
        // given
        val events = semanticEvents {
            "section" { +"body" }
        }

        // when - afterClose is registered first, yet still lands after the close
        val transformed = events.transform {
            match("section") {
                afterClose { "after-close" {} }
                "section" {
                    children()
                    defer { "deferred" {} }
                }
            }
            matchText { +it }
        }

        // then
        transformed sameAs semanticEvents {
            "section" {
                +"body"
                "deferred" {}
            }
            "after-close" {}
        }
    }

    @Test
    fun `should run a defer block in the tagged default of its call site`() = runTest {
        // given
        val events = semanticEvents {
            "section" { +"body" }
        }

        // when - like a direct emission after children(), the defer block
        //   keeps the tagged/untagged default in force where it was called
        val transformed = events.transform {
            match("section") {
                "section" {
                    children()
                    tagged {
                        defer { "aside" { "em" {} } }
                    }
                    defer { "footer" {} }
                }
            }
            matchText { +it }
        }

        // then
        transformed sameAs semanticEvents {
            "section" {
                +"body"
                tag("aside") { tag("em") {} }
                "footer" {}
            }
        }
    }

    @Test
    fun `should propagate an exception thrown by a defer block at the unmark`() = runTest {
        // given
        val events = semanticEvents {
            "section" { +"body" }
        }
        val transformed = events.transform {
            match("section") {
                "section" {
                    children()
                    defer { error("defer failed") }
                }
            }
            matchText { +it }
        }
        val collected = mutableListOf<SemanticEvent>()

        // when
        val exception = assertFailsWith<IllegalStateException> {
            transformed.toList(collected)
        }

        // then - the block ran only after the subtree had streamed
        assert(exception.message == "defer failed")
        assert(
            collected == listOf(
                SemanticEvent.Mark("section"),
                SemanticEvent.Text("body")
            )
        )
    }

    @Test
    fun `should not run a defer block when the collector cancels before the unmark`() = runTest {
        // given
        val events = semanticEvents {
            "section" { +"body" }
        }
        var ran = false
        val transformed = events.transform {
            match("section") {
                "section" {
                    children()
                    defer {
                        ran = true
                        "lazy" {}
                    }
                }
            }
            matchText { +it }
        }

        // when - the downstream stops before section closes
        val collected = transformed.take(2).toList()

        // then
        assert(
            collected == listOf(
                SemanticEvent.Mark("section"),
                SemanticEvent.Text("body")
            )
        )
        assert(!ran)
    }

}
