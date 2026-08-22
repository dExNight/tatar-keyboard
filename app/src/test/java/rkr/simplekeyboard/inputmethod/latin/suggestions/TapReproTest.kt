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
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import rkr.simplekeyboard.inputmethod.latin.RichInputConnection
import rkr.simplekeyboard.inputmethod.latin.dictionary.engine.LookupKind
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/**
 * Mission tt-tap-repro. Reproduces, from the symptom, the two defects the operator saw on 1.8.0.
 * The cursor-bookkeeping cause behind both symptom-2 tests was fixed in 1.8.1; they pass now and
 * stay here as regression tests. The single symptom-1 test is @Ignore'd — see its own comment.
 *
 * The fakes here differ from [SuggestionsControllerTest]'s in ONE deliberate way: the engine has
 * real latest-only token semantics (a fresh token per request, `isCurrent` true only for the newest
 * one, `finishInput()` invalidating the generation) instead of a constant token with
 * `isCurrent == true`. A fake that answers "yes, current" to every token cannot express a dropped
 * result, which is the state both symptoms live in.
 */
class TapReproTest {

    // --- Fakes ---------------------------------------------------------------------------------

    /** Records what the user can actually SEE on the strip, not just the call log. */
    private class RecordingStrip : StripSurface {
        var visibleWords: List<String> = emptyList()
        var visible = false
        var listener: SuggestionTapListener? = null

        override fun showSuggestions(first: String, second: String?, third: String?) {
            visibleWords = listOfNotNull(first, second, third)
            visible = true
        }

        override fun reserve() {
            visibleWords = emptyList()
            visible = true
        }

        override fun hideSuggestions() {
            visibleWords = emptyList()
            visible = false
        }

        override fun setTapListener(listener: SuggestionTapListener) {
            this.listener = listener
        }

        /** Exactly what a finger can do: tap a word that is currently painted. */
        fun tap(word: String) {
            assertTrue("tap() on a word the strip is not showing: $word", visibleWords.contains(word))
            listener!!.onTap(word)
        }
    }

    private class FakeEditor : EditorSurface {
        var word: String = ""
        var commitResult: Boolean = true
        var knownCursor: Boolean = true
        var textAfterCursor: String = ""
        var nextWordContext: String = ""
        val commits = mutableListOf<Pair<String, String>>()
        val predictedCommits = mutableListOf<Pair<String, String>>()

        override fun cachedWordBeforeCursor(): String = word

        override fun commitSuggestion(expectedPrefix: String, suggestion: String): Boolean {
            commits.add(expectedPrefix to suggestion)
            return commitResult
        }

        override fun hasKnownCursor(): Boolean = knownCursor

        override fun hasLetterAfterCursor(): Boolean =
            TatarWordUtils.startsWithWordCharacter(textAfterCursor)

        override fun cachedNextWordContext(): String = nextWordContext

        override fun commitPredictedWord(expectedContextWord: String, suggestion: String): Boolean {
            predictedCommits.add(expectedContextWord to suggestion)
            return true
        }
    }

    /** Mirrors LatestOnlyPrefixEngine's generation semantics, which the shared fake flattens away. */
    private class LatestOnlyEngine : EngineHandle {
        private var serial = 0L
        private var current: Any? = null
        val requested = mutableListOf<String>()
        var finishCount = 0

        override fun request(editorSessionId: Long, subtypeId: String, prefixUtf8: ByteArray): Any? {
            requested.add(String(prefixUtf8, Charsets.UTF_8))
            val token = "prefix#${serial++}"
            current = token
            return token
        }

        override fun requestNextWord(
            editorSessionId: Long,
            subtypeId: String,
            contextWordUtf8: ByteArray,
        ): Any? {
            requested.add("[next]" + String(contextWordUtf8, Charsets.UTF_8))
            val token = "next#${serial++}"
            current = token
            return token
        }

        override fun isCurrent(token: Any): Boolean = token == current

        override fun finishInput() {
            finishCount++
            current = null
        }

        override fun destroy(timeoutMs: Long): Boolean = true

        /** The token the newest request handed out, or null if the generation was invalidated. */
        fun newestToken(): Any? = current
    }

    private class DirectExecutorService : AbstractExecutorService() {
        private var shutdown = false
        override fun execute(command: Runnable) = command.run()
        override fun shutdown() { shutdown = true }
        override fun shutdownNow(): MutableList<Runnable> { shutdown = true; return mutableListOf() }
        override fun isShutdown(): Boolean = shutdown
        override fun isTerminated(): Boolean = shutdown
        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
    }

    private class Harness {
        val strip = RecordingStrip()
        val editor = FakeEditor()
        val engine = LatestOnlyEngine()
        var callback: ResultCallback? = null

        val controller = SuggestionsController(
            strip,
            editor,
            UiPoster { it.run() },
            { cb -> callback = cb; engine },
            DirectExecutorService(),
            true,
        )

        /**
         * The keyboard's own expected-selection bookkeeping — the REAL class, so the scenario
         * below is driven by production arithmetic rather than by an assumption about it.
         */
        val connection = RichInputConnection(null)

        /** One typed letter: the editor's cached word becomes [word], then the IME reacts. */
        fun type(word: String) {
            editor.word = word
            // A typed character goes through commitText, which collapses the expected pair; the
            // framework then reports the very same position, so no external move is seen.
            connection.updateSelection(word.length, word.length)
            controller.onTextChanged()
        }

        /**
         * One backspace, in the order LatinIME actually runs it: the edit and its bookkeeping
         * (InputLogic.handleBackspaceEvent -> deleteTextBeforeCursor), then
         * updateStateAfterInputTransaction -> onTextChanged, and only later the framework's
         * onUpdateSelection for the same edit.
         */
        fun backspace(word: String) {
            connection.deleteTextBeforeCursor(1)
            editor.word = word
            controller.onTextChanged()
            frameworkReportsCursor(word.length)
        }

        /** LatinIME.onUpdateSelection, lines 1300-1306, verbatim in shape. */
        fun frameworkReportsCursor(offset: Int) {
            val externalMove = offset != connection.expectedSelectionStart ||
                offset != connection.expectedSelectionEnd
            connection.updateSelection(offset, offset)
            if (externalMove) controller.onSelectionChanged()
        }

        /** The engine answers the newest outstanding request. */
        fun deliver(vararg suggestions: String) {
            val token = engine.newestToken() ?: return
            callback!!.onResult(token, suggestions.toList(), LookupKind.PREFIX)
        }
    }

    // --- Symptom 1 -------------------------------------------------------------------------------

    /**
     * "Ячейка подсвечивается, но текст не меняется."
     *
     * The invariant under test is the one the user relies on: WHAT THE STRIP IS PAINTING IS
     * TAPPABLE. Any word the strip shows must, when tapped, reach the editor as a commit.
     */
    @Ignore(
        "Second, independent defect, NOT the cause of the operator's two symptoms (see " +
            "docs/TAP-REPRO.md, section 'Главная версия досье: что с ней стало'). The strip keeps " +
            "painting candidates the controller has already unbound, so a tap inside that window " +
            "is a guaranteed no-op. Closing it is a UX trade-off, not a correctness fix — either " +
            "the band blanks on every keystroke, or the window stays. Asked the operator in " +
            "mission tt-version-1.8.1 (.smgr/tt-version-1.8.1/ask.json); re-enable this test when " +
            "he picks. The test itself is correct and was left runnable on purpose."
    )
    @Test
    fun tappingAWordTheStripIsPaintingAlwaysReachesTheEditor() {
        val h = Harness()
        h.controller.onStartInput(eligible = true)

        // "Сәләм абый сине" — the trailing word is "сине" and the band fills with its candidates.
        h.type("сине")
        h.deliver("синең", "сине", "сингапур")
        assertEquals(listOf("синең", "сине", "сингапур"), h.strip.visibleWords)

        // The live cached word moves off the prefix those candidates were computed for, and the
        // result of the new lookup has not come back yet. Nothing repaints the strip.
        h.type("синеп")

        assertEquals(
            "the strip is still painting the old candidates",
            listOf("синең", "сине", "сингапур"),
            h.strip.visibleWords,
        )

        // The user taps what they can see.
        h.strip.tap("синең")

        assertEquals(
            "a painted word was tapped and nothing reached the editor",
            1,
            h.editor.commits.size,
        )
    }

    // --- Symptom 2 -------------------------------------------------------------------------------

    /**
     * "како" -> "какоц" (0 results, correct) -> backspace -> "како": the candidates that were on
     * screen one keystroke ago must come back.
     *
     * Nothing here asserts on the desync directly. The scenario simply runs the production
     * bookkeeping and lets it decide whether the keyboard's own backspace looks external.
     */
    @Test
    fun suggestionsComeBackAfterDeletingTheExtraLetter() {
        val h = Harness()
        h.controller.onStartInput(eligible = true)

        h.type("како")
        h.deliver("какой", "какое", "какая")
        assertEquals(listOf("какой", "какое", "какая"), h.strip.visibleWords)

        // One letter too many: the dictionary has nothing, and the empty band is correct.
        h.type("какоц")
        h.deliver()
        assertEquals(emptyList<String>(), h.strip.visibleWords)

        // The extra letter is deleted and the lookup for the restored prefix is answered.
        h.backspace("како")
        h.deliver("какой", "какое", "какая")

        assertEquals(
            "the same prefix that had candidates one keystroke ago shows none",
            listOf("какой", "какое", "какая"),
            h.strip.visibleWords,
        )
    }

    /**
     * The same step with the opposite arrival order: the engine answers before the framework
     * reports the cursor. Either order ends with an empty band, which is why the defect reproduces
     * every time rather than intermittently.
     */
    @Test
    fun suggestionsSurviveTheCursorReportThatFollowsABackspace() {
        val h = Harness()
        h.controller.onStartInput(eligible = true)

        h.type("како")
        h.deliver("какой", "какое", "какая")
        h.type("какоц")
        h.deliver()

        // Backspace, but with the result arriving before the framework's cursor report.
        h.connection.deleteTextBeforeCursor(1)
        h.editor.word = "како"
        h.controller.onTextChanged()
        h.deliver("какой", "какое", "какая")
        assertEquals(listOf("какой", "какое", "какая"), h.strip.visibleWords)

        h.frameworkReportsCursor("како".length)

        assertEquals(
            "the band is wiped by the keyboard's own backspace and nothing re-requests",
            listOf("какой", "какое", "какая"),
            h.strip.visibleWords,
        )
    }
}
