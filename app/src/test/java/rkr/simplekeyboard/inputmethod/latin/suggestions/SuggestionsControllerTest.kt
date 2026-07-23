/*
 * Copyright (C) 2026 Tatar Keyboard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rkr.simplekeyboard.inputmethod.latin.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

class SuggestionsControllerTest {

    // --- Fakes ---------------------------------------------------------------------------------

    private class FakeStrip : StripSurface {
        val shown = mutableListOf<Triple<String, String?, String?>>()
        var hideCount = 0
        var reserveCount = 0
        var listener: SuggestionTapListener? = null

        override fun showSuggestions(first: String, second: String?, third: String?) {
            shown.add(Triple(first, second, third))
        }

        override fun reserve() {
            reserveCount++
        }

        override fun hideSuggestions() {
            hideCount++
        }

        override fun setTapListener(listener: SuggestionTapListener) {
            this.listener = listener
        }
    }

    private class FakeEditor : EditorSurface {
        var word: String = ""
        var commitResult: Boolean = true
        var knownCursor: Boolean = true
        val commits = mutableListOf<Pair<String, String>>()

        override fun cachedWordBeforeCursor(): String = word

        override fun commitSuggestion(expectedPrefix: String, suggestion: String): Boolean {
            commits.add(expectedPrefix to suggestion)
            return commitResult
        }

        override fun hasKnownCursor(): Boolean = knownCursor
    }

    private class FakeEngine : EngineHandle {
        val requestedPrefixes = mutableListOf<ByteArray>()
        var nextToken: Any? = TOKEN
        var isCurrentResult: Boolean = true
        var finishCount = 0
        var destroyCount = 0
        var destroyResult: Boolean = true

        override fun request(editorSessionId: Long, subtypeId: String, prefixUtf8: ByteArray): Any? {
            requestedPrefixes.add(prefixUtf8)
            return nextToken
        }

        override fun isCurrent(token: Any): Boolean = isCurrentResult

        override fun finishInput() {
            finishCount++
        }

        override fun destroy(timeoutMs: Long): Boolean {
            destroyCount++
            return destroyResult
        }

        companion object {
            val TOKEN = Any()
        }
    }

    /** Runs submitted work inline so engine start is deterministic in tests. */
    private class DirectExecutorService : AbstractExecutorService() {
        private var shutdown = false

        override fun execute(command: Runnable) = command.run()

        override fun shutdown() {
            shutdown = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            shutdown = true
            return mutableListOf()
        }

        override fun isShutdown(): Boolean = shutdown

        override fun isTerminated(): Boolean = shutdown

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
    }

    /** Captures submitted work so engine start can be completed after onDestroy() deterministically. */
    private class QueuingExecutorService : AbstractExecutorService() {
        private val tasks = ArrayDeque<Runnable>()
        private var shutdown = false

        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        /** Runs the oldest queued task inline; tasks survive shutdownNow so a post-destroy start can complete. */
        fun runNext() = tasks.removeFirst().run()

        override fun shutdown() {
            shutdown = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            shutdown = true
            return mutableListOf()
        }

        override fun isShutdown(): Boolean = shutdown

        override fun isTerminated(): Boolean = shutdown

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
    }

    private class Harness {
        val strip = FakeStrip()
        val editor = FakeEditor()
        val engine = FakeEngine()
        val executor = DirectExecutorService()
        var capturedCallback: ResultCallback? = null
        var factoryCalls = 0
        var factoryResult: EngineHandle? = engine

        val controller = SuggestionsController(
            strip,
            editor,
            UiPoster { it.run() },
            { callback ->
                factoryCalls++
                capturedCallback = callback
                factoryResult
            },
            executor,
        )
    }

    // --- Eligibility gating --------------------------------------------------------------------

    @Test
    fun ineligibleStartHidesAndNeverStartsEngineOrRequests() {
        val h = Harness()

        h.controller.onStartInput(eligible = false)

        assertEquals(1, h.strip.hideCount)
        assertEquals(0, h.factoryCalls)

        // A subsequent text change must not reach the (never-started) engine.
        h.editor.word = "сүз"
        h.controller.onTextChanged()
        assertTrue(h.engine.requestedPrefixes.isEmpty())
    }

    @Test
    fun eligibleStartStartsEngineAndWiresTapListener() {
        val h = Harness()

        h.controller.onStartInput(eligible = true)

        assertEquals(1, h.factoryCalls)
        assertNotNull(h.strip.listener)
        assertNotNull(h.capturedCallback)
    }

    @Test
    fun eligibleStartReservesBandAndNeverHides() {
        val h = Harness()

        h.controller.onStartInput(eligible = true)

        assertEquals(1, h.strip.reserveCount)
        assertEquals(0, h.strip.hideCount)
    }

    @Test
    fun secondStartDoesNotRestartEngine() {
        val h = Harness()

        h.controller.onStartInput(eligible = true)
        h.controller.onStartInput(eligible = true)

        assertEquals(1, h.factoryCalls)
    }

    // --- Request dispatch ----------------------------------------------------------------------

    @Test
    fun emptyWordReservesAndDoesNotRequest() {
        val h = Harness()
        h.controller.onStartInput(eligible = true)
        val reserveBefore = h.strip.reserveCount
        val hideBefore = h.strip.hideCount

        h.editor.word = ""
        h.controller.onTextChanged()

        assertTrue(h.engine.requestedPrefixes.isEmpty())
        assertEquals(reserveBefore + 1, h.strip.reserveCount)
        assertEquals(hideBefore, h.strip.hideCount)
    }

    @Test
    fun nonEmptyWordRequestsNormalizedUtf8() {
        val h = Harness()
        h.controller.onStartInput(eligible = true)

        h.editor.word = "СҮЗ"
        h.controller.onTextChanged()

        assertEquals(1, h.engine.requestedPrefixes.size)
        assertTrue(
            h.engine.requestedPrefixes.single()
                .contentEquals("сүз".toByteArray(Charsets.UTF_8)),
        )
    }

    @Test
    fun nullTokenReservesStrip() {
        val h = Harness()
        h.controller.onStartInput(eligible = true)
        h.engine.nextToken = null
        val reserveBefore = h.strip.reserveCount
        val hideBefore = h.strip.hideCount

        h.editor.word = "сүз"
        h.controller.onTextChanged()

        assertEquals(reserveBefore + 1, h.strip.reserveCount)
        assertEquals(hideBefore, h.strip.hideCount)
    }

    // --- Result application --------------------------------------------------------------------

    @Test
    fun currentResultIsShownTopThree() {
        val h = Harness()
        h.controller.onStartInput(eligible = true)
        h.editor.word = "сүз"
        h.controller.onTextChanged()

        h.engine.isCurrentResult = true
        h.capturedCallback!!.onResult(
            FakeEngine.TOKEN,
            listOf("сүзләр", "сүзлек", "сүзсез", "сүзчән"),
        )

        assertEquals(1, h.strip.shown.size)
        assertEquals(Triple("сүзләр", "сүзлек", "сүзсез"), h.strip.shown.single())
    }

    @Test
    fun emptySuggestionListReserves() {
        val h = Harness()
        h.controller.onStartInput(eligible = true)
        h.editor.word = "сүз"
        h.controller.onTextChanged()
        val reserveBefore = h.strip.reserveCount
        val hideBefore = h.strip.hideCount

        h.capturedCallback!!.onResult(FakeEngine.TOKEN, emptyList())

        assertTrue(h.strip.shown.isEmpty())
        assertEquals(reserveBefore + 1, h.strip.reserveCount)
        assertEquals(hideBefore, h.strip.hideCount)
    }

    @Test
    fun staleResultDroppedWhenSelectionChangedBumpsSession() {
        val h = Harness()
        h.controller.onStartInput(eligible = true)
        h.editor.word = "сүз"
        h.controller.onTextChanged()

        // Session bumps between request and result; engine.isCurrent still true, but the session
        // guard must drop the result anyway.
        h.controller.onSelectionChanged()
        h.engine.isCurrentResult = true
        h.capturedCallback!!.onResult(FakeEngine.TOKEN, listOf("сүзләр"))

        assertTrue(h.strip.shown.isEmpty())
    }

    @Test
    fun staleResultDroppedWhenFinishInputBumpsSession() {
        val h = Harness()
        h.controller.onStartInput(eligible = true)
        h.editor.word = "сүз"
        h.controller.onTextChanged()

        h.controller.onFinishInput()
        h.engine.isCurrentResult = true
        h.capturedCallback!!.onResult(FakeEngine.TOKEN, listOf("сүзләр"))

        assertTrue(h.strip.shown.isEmpty())
    }

    @Test
    fun staleResultDroppedWhenEngineNotCurrent() {
        val h = Harness()
        h.controller.onStartInput(eligible = true)
        h.editor.word = "сүз"
        h.controller.onTextChanged()

        h.engine.isCurrentResult = false
        h.capturedCallback!!.onResult(FakeEngine.TOKEN, listOf("сүзләр"))

        assertTrue(h.strip.shown.isEmpty())
    }

    // --- Tap routing ---------------------------------------------------------------------------

    @Test
    fun tapCommitsPendingPrefixAndReservesOnSuccess() {
        val h = Harness()
        h.controller.onStartInput(eligible = true)
        h.editor.word = "сүз"
        h.controller.onTextChanged()
        val reserveBefore = h.strip.reserveCount
        val hideBefore = h.strip.hideCount

        h.editor.commitResult = true
        h.strip.listener!!.onTap("сүзләр")

        assertEquals(listOf("сүз" to "сүзләр"), h.editor.commits)
        assertEquals(reserveBefore + 1, h.strip.reserveCount)
        assertEquals(hideBefore, h.strip.hideCount)
    }

    @Test
    fun tapDoesNotReserveOrHideWhenCommitFails() {
        val h = Harness()
        h.controller.onStartInput(eligible = true)
        h.editor.word = "сүз"
        h.controller.onTextChanged()
        val reserveBefore = h.strip.reserveCount
        val hideBefore = h.strip.hideCount

        h.editor.commitResult = false
        h.strip.listener!!.onTap("сүзләр")

        assertEquals(listOf("сүз" to "сүзләр"), h.editor.commits)
        assertEquals(reserveBefore, h.strip.reserveCount)
        assertEquals(hideBefore, h.strip.hideCount)
    }

    // --- Teardown ------------------------------------------------------------------------------

    @Test
    fun destroyTearsDownEngineAndExecutor() {
        val h = Harness()
        h.controller.onStartInput(eligible = true)

        h.controller.onDestroy()

        assertEquals(1, h.engine.destroyCount)
        assertTrue(h.executor.isShutdown)
    }

    @Test
    fun failedEngineStartLeavesControllerIdle() {
        val h = Harness()
        h.factoryResult = null

        h.controller.onStartInput(eligible = true)

        // Engine never published; a text change must not crash or request.
        h.editor.word = "сүз"
        h.controller.onTextChanged()
        assertEquals(1, h.factoryCalls)
        assertTrue(h.engine.requestedPrefixes.isEmpty())
        assertFalse(h.executor.isShutdown)
    }

    @Test
    fun engineStartCompletingAfterDestroyIsTornDownNotActivated() {
        val strip = FakeStrip()
        val editor = FakeEditor()
        val engine = FakeEngine()
        val executor = QueuingExecutorService()
        var capturedCallback: ResultCallback? = null
        val controller = SuggestionsController(
            strip,
            editor,
            UiPoster { it.run() },
            { callback ->
                capturedCallback = callback
                engine
            },
            executor,
        )

        // Start the engine but leave the background start task queued (not yet published).
        controller.onStartInput(eligible = true)
        assertEquals(0, engine.destroyCount)

        // Controller is torn down while the start is still in flight.
        controller.onDestroy()

        // Now the background start completes and posts publishEngine onto the UI thread.
        executor.runNext()

        // The in-flight engine must be destroyed, not activated.
        assertEquals(1, engine.destroyCount)

        // It never became the active engine: a late result cannot be applied.
        capturedCallback!!.onResult(FakeEngine.TOKEN, listOf("сүзләр"))
        assertTrue(strip.shown.isEmpty())
    }

    @Test
    fun unknownCursorReservesAndDoesNotRequest() {
        val h = Harness()
        h.controller.onStartInput(eligible = true)
        val reserveBefore = h.strip.reserveCount
        val hideBefore = h.strip.hideCount

        h.editor.knownCursor = false
        h.editor.word = "сүз"
        h.controller.onTextChanged()

        assertTrue(h.engine.requestedPrefixes.isEmpty())
        assertEquals(reserveBefore + 1, h.strip.reserveCount)
        assertEquals(hideBefore, h.strip.hideCount)
    }

    // --- Reserve vs GONE lifecycle -------------------------------------------------------------

    @Test
    fun externalSelectionChangeWhileEligibleReservesAndNeverHides() {
        val h = Harness()
        h.controller.onStartInput(eligible = true)
        val reserveBefore = h.strip.reserveCount
        val hideBefore = h.strip.hideCount

        h.controller.onSelectionChanged()

        assertEquals(reserveBefore + 1, h.strip.reserveCount)
        assertEquals(hideBefore, h.strip.hideCount)
    }

    @Test
    fun selectionChangeWhileIneligibleDoesNotReserve() {
        val h = Harness()
        h.controller.onStartInput(eligible = false)
        val reserveBefore = h.strip.reserveCount

        h.controller.onSelectionChanged()

        assertEquals(reserveBefore, h.strip.reserveCount)
    }

    @Test
    fun finishInputHidesStrip() {
        val h = Harness()
        h.controller.onStartInput(eligible = true)
        val hideBefore = h.strip.hideCount

        h.controller.onFinishInput()

        assertEquals(hideBefore + 1, h.strip.hideCount)
    }

    @Test
    fun subtypeChangedToEligibleReservesBand() {
        val h = Harness()
        h.controller.onStartInput(eligible = true)
        val reserveBefore = h.strip.reserveCount
        val hideBefore = h.strip.hideCount

        h.controller.onSubtypeChanged(eligible = true)

        assertEquals(reserveBefore + 1, h.strip.reserveCount)
        assertEquals(hideBefore, h.strip.hideCount)
    }

    @Test
    fun subtypeChangedToIneligibleHidesBand() {
        val h = Harness()
        h.controller.onStartInput(eligible = true)
        val hideBefore = h.strip.hideCount

        h.controller.onSubtypeChanged(eligible = false)

        assertEquals(hideBefore + 1, h.strip.hideCount)
    }
}
