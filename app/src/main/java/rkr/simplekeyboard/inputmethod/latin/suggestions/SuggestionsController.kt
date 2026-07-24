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

import android.content.Context
import android.os.Handler
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.AndroidDictionaryStorageFactory
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.DictionaryStorageController
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.PreparationResult
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.PublishedDictionaryCatalog
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Suggestion-strip UI seam. All methods are called on the UI thread. */
interface StripSurface {
    fun showSuggestions(first: String, second: String?, third: String?)

    /** Make the strip VISIBLE with no words (empty band), keeping its reserved 40dp height. */
    fun reserve()

    fun hideSuggestions()
    fun setTapListener(listener: SuggestionTapListener)
}

/** Fired when the user taps a suggestion in the strip (UI thread). */
fun interface SuggestionTapListener {
    fun onTap(suggestion: String)
}

/** Editor seam backed by RichInputConnection's cache. All methods are called on the UI thread. */
interface EditorSurface {
    fun cachedWordBeforeCursor(): String
    fun commitSuggestion(expectedPrefix: String, suggestion: String): Boolean
    fun hasKnownCursor(): Boolean

    /**
     * True when the cursor sits inside a word, i.e. the text right after it starts with a letter
     * (or with a combining mark, which can only continue one). Typing in the middle of a word is
     * not supported by the frozen contract: the results are cleared instead, because replacing the
     * trailing word would splice the suggestion into the user's text.
     */
    fun hasLetterAfterCursor(): Boolean
}

/**
 * Marshals a [Runnable] onto the UI thread. Production wraps an [android.os.Handler]; JVM tests
 * inject a synchronous poster so no real Handler is needed.
 */
fun interface UiPoster {
    fun post(runnable: Runnable)
}

/**
 * Drives Tatar prefix suggestions from IME lifecycle callbacks.
 *
 * Threading: every public method must be called on the UI thread. The single-thread background
 * executor is used only for the (blocking) engine start and dictionary preparation. Engine results
 * arrive on a worker thread via [ResultCallback] and are re-marshaled onto [uiPoster] before any
 * state is touched. Dictionary-readiness notifications are likewise marshaled onto [uiPoster] so
 * every mutation of controller state happens on the single serialized UI owner.
 *
 * Eligibility (opt-in setting, tt_RU subtype, editor allows suggestions, known cursor) is computed
 * by LatinIME and passed in via [onStartInput]/[onSubtypeChanged]; dictionary readiness is tracked
 * internally.
 */
class SuggestionsController private constructor(
    private val context: Context?,
    private val strip: StripSurface,
    private val editor: EditorSurface,
    private val uiPoster: UiPoster,
    private val engineFactory: (ResultCallback) -> EngineHandle?,
    initialExecutor: ExecutorService?,
    initialDictionaryReady: Boolean,
) {
    /** Production entry point (frozen contract). */
    constructor(
        context: Context,
        strip: StripSurface,
        editor: EditorSurface,
        uiHandler: Handler,
        engineFactory: (ResultCallback) -> EngineHandle?,
    ) : this(
        context,
        strip,
        editor,
        UiPoster { runnable -> uiHandler.post(runnable) },
        engineFactory,
        null,
        false,
    )

    /** Test entry point: injects a synchronous poster + executor and pre-marks the dictionary. */
    internal constructor(
        strip: StripSurface,
        editor: EditorSurface,
        uiPoster: UiPoster,
        engineFactory: (ResultCallback) -> EngineHandle?,
        backgroundExecutor: ExecutorService,
    ) : this(null, strip, editor, uiPoster, engineFactory, backgroundExecutor, true)

    /**
     * Test entry point that lets a test drive the not-ready -> ready path: same as the primary test
     * constructor but with an explicit initial readiness so a test can start ineligible/not-ready
     * and later fire readiness via [signalDictionaryReadyForTest].
     */
    internal constructor(
        strip: StripSurface,
        editor: EditorSurface,
        uiPoster: UiPoster,
        engineFactory: (ResultCallback) -> EngineHandle?,
        backgroundExecutor: ExecutorService,
        dictionaryReady: Boolean,
    ) : this(null, strip, editor, uiPoster, engineFactory, backgroundExecutor, dictionaryReady)

    private var executor: ExecutorService? = initialExecutor
    private var storage: DictionaryStorageController? = null

    @Volatile
    private var dictionaryReady: Boolean = initialDictionaryReady

    private var engine: EngineHandle? = null
    private var starting: Boolean = false
    private var eligible: Boolean = false
    private var destroyed: Boolean = false

    // Monotonic edit-session counter. Bumped on every lifecycle boundary so results computed for an
    // older editor state are dropped even if the engine's own generation check would still pass.
    private var sessionId: Long = 0L
    private var requestSessionId: Long = NO_SESSION

    // The latest prefix a lookup was requested for. Not what is on screen; see [displayedPrefix].
    private var pendingPrefix: String = ""

    // The prefix (and session) the candidates currently shown on the strip were computed for. Set
    // ONLY in [applyResult] when non-empty suggestions are actually displayed; cleared to null the
    // instant those words are cleared or superseded. A tap is bound to THIS value, never to the
    // mutable [pendingPrefix], so a stale candidate can never commit against a newer prefix.
    private var displayedPrefix: String? = null
    private var displayedSessionId: Long = NO_SESSION

    fun onCreate() {
        strip.setTapListener(SuggestionTapListener { suggestion -> onTap(suggestion) })
        if (executor != null) return
        val backgroundExecutor = Executors.newSingleThreadExecutor()
        executor = backgroundExecutor
        val ctx = context ?: return
        val controller = AndroidDictionaryStorageFactory.create(ctx, backgroundExecutor)
        storage = controller
        controller.prepare { result ->
            if (result is PreparationResult.Published) {
                // The prepare callback runs on the background executor. Marshal onto the serialized
                // UI owner before touching any controller state, then start the engine (and look up
                // whatever is already typed) so a field opened before the dictionary finished
                // preparing still gets suggestions this session.
                uiPoster.post { onDictionaryReady() }
            }
        }
    }

    fun onStartInput(eligible: Boolean) {
        sessionId++
        displayedPrefix = null
        this.eligible = eligible
        if (!eligible) {
            strip.hideSuggestions()
            engine?.finishInput()
            return
        }
        // Eligibility alone is not enough to expose the band: while the dictionary is preparing
        // or the engine is unavailable the frozen state table requires GONE/0dp. A successful
        // publishEngine() transitions a cold session to the reserved state.
        val engineWasReady = engine != null
        if (!engineWasReady) {
            strip.hideSuggestions()
        } else {
            strip.reserve()
        }
        strip.setTapListener(SuggestionTapListener { suggestion -> onTap(suggestion) })
        // A warm engine has no publication callback in this new editor session. Re-request the
        // cached prefix now so changing fields never requires an extra keystroke. Capture readiness
        // before maybeStartEngine() so a cold engine that publishes inline still requests exactly
        // once from publishEngine().
        maybeStartEngine()
        if (
            engineWasReady &&
            editor.hasKnownCursor() &&
            editor.cachedWordBeforeCursor().isNotEmpty()
        ) {
            requestCurrentPrefix()
        }
    }

    fun onTextChanged() {
        requestCurrentPrefix()
    }

    fun onSelectionChanged() {
        sessionId++
        engine?.finishInput()
        // Any in-flight request is invalidated and whatever was shown is no longer bound to the
        // live editor state, so drop the displayed binding immediately.
        displayedPrefix = null
        // Keep the reserved band only after an engine has actually published. Eligibility while
        // the dictionary is preparing/unavailable remains fail-closed at GONE/0dp.
        if (eligible) {
            if (engine == null) {
                strip.hideSuggestions()
            } else {
                strip.reserve()
            }
        }
    }

    fun onFinishInput() {
        sessionId++
        displayedPrefix = null
        // Close eligibility before hiding/finishing. A readiness notification queued behind this
        // lifecycle boundary must not start or publish an engine for the finished editor session.
        eligible = false
        strip.hideSuggestions()
        engine?.finishInput()
    }

    fun onSubtypeChanged(eligible: Boolean) {
        sessionId++
        engine?.finishInput()
        displayedPrefix = null
        this.eligible = eligible
        if (eligible) {
            val engineWasReady = engine != null
            if (engineWasReady) {
                strip.reserve()
            } else {
                // Cold, preparing and unavailable engines all stay GONE until publish succeeds.
                strip.hideSuggestions()
            }
            // The strip view is created lazily, so a listener registered while it did not exist
            // yet was silently dropped. Switching INTO the Tatar subtype with the globe key in an
            // already-open field is a routine path for a bilingual user and may be the first
            // moment the strip exists, so (re)wire the tap listener exactly like onStartInput()
            // does; without this a tap would do nothing for the rest of the editor session.
            strip.setTapListener(SuggestionTapListener { suggestion -> onTap(suggestion) })
            // Switching INTO an eligible subtype in an already-open field must start the engine
            // (if not already running); a freshly started engine looks up the current prefix from
            // publishEngine(). An already-published engine has no publish callback to do that work,
            // so re-request the cached prefix immediately after tt -> non-tt -> tt. Capture the
            // state before maybeStartEngine() so even an inline test executor cannot double-request
            // when a cold engine publishes synchronously.
            maybeStartEngine()
            if (
                engineWasReady &&
                editor.hasKnownCursor() &&
                editor.cachedWordBeforeCursor().isNotEmpty()
            ) {
                requestCurrentPrefix()
            }
        } else {
            strip.hideSuggestions()
        }
    }

    fun onDestroy() {
        // Set the guard first so an engine start that publishes after this point (posted onto the
        // UI thread from the background executor) is torn down instead of orphaned.
        destroyed = true
        displayedPrefix = null
        val handle = engine
        if (handle != null && destroyHandle(handle)) {
            // Only drop the reference once the lease is actually released; a still-leaked lease
            // stays recoverable rather than being made permanently unreachable.
            engine = null
        }
        executor?.shutdownNow()
        executor = null
    }

    /** Catalog for the production engine factory; available only after [onCreate]. */
    fun engineCatalog(): PublishedDictionaryCatalog? = storage

    /**
     * Test seam: drives the exact dictionary-ready path the production prepare callback drives
     * (post [onDictionaryReady] onto the injected [uiPoster]). Lets a test start not-ready and fire
     * readiness deterministically without a real storage controller.
     */
    internal fun signalDictionaryReadyForTest() {
        uiPoster.post { onDictionaryReady() }
    }

    /**
     * Handles the dictionary becoming ready. Always runs on the UI owner. A late notification that
     * arrives after [onDestroy] or [onFinishInput] starts nothing and requests nothing.
     */
    private fun onDictionaryReady() {
        if (destroyed) return
        dictionaryReady = true
        maybeStartEngine()
    }

    private fun maybeStartEngine() {
        if (engine != null || starting || !dictionaryReady || !eligible) return
        val backgroundExecutor = executor ?: return
        starting = true
        val callback = ResultCallback { token, suggestions ->
            uiPoster.post { applyResult(token, suggestions) }
        }
        val factory = engineFactory
        try {
            backgroundExecutor.execute {
                val handle = try {
                    factory(callback)
                } catch (_: Throwable) {
                    null
                }
                uiPoster.post { publishEngine(handle) }
            }
        } catch (_: Throwable) {
            starting = false
        }
    }

    private fun publishEngine(handle: EngineHandle?) {
        starting = false
        if (destroyed) {
            // onDestroy already ran; this start is racing a torn-down controller. Release the
            // freshly acquired lease instead of assigning it, and never expose it as the engine.
            if (handle != null) {
                destroyHandle(handle)
            }
            return
        }
        if (handle == null) {
            // Engine creation failed: do not reserve an empty band for an unavailable dictionary.
            displayedPrefix = null
            if (eligible) {
                strip.hideSuggestions()
            }
            return
        }
        engine = handle
        if (!eligible) {
            handle.finishInput()
            strip.hideSuggestions()
            return
        }
        // Successful publication is the transition from preparing/unavailable (GONE) to the stable
        // eligible band. Look up whatever the user has already typed without waiting for another
        // keystroke; an empty/unknown prefix leaves the now-available band reserved with 0 results.
        strip.reserve()
        if (editor.hasKnownCursor() && editor.cachedWordBeforeCursor().isNotEmpty()) {
            requestCurrentPrefix()
        }
    }

    /**
     * Bounded engine teardown: one quick attempt, then a single longer bounded retry so a lease
     * that missed the first deadline still gets a chance to release without risking an ANR.
     * Returns true only if the engine fully released.
     */
    private fun destroyHandle(handle: EngineHandle): Boolean {
        if (handle.destroy(DESTROY_TIMEOUT_MS)) return true
        return handle.destroy(DESTROY_TIMEOUT_MS * 3)
    }

    /**
     * The single request path. Reads the current cached prefix and dispatches a lookup, clearing
     * the displayed binding whenever the words are (or become) empty/unresolvable and whenever the
     * prefix changes so stale candidates cannot be tapped in the window before the fresh result
     * arrives. Reused verbatim by [onTextChanged] and by [publishEngine] right after a successful
     * engine publish.
     *
     * It is also where the frozen text contract's two "0 results" states are enforced, both
     * BEFORE the engine is asked anything: a cursor sitting inside a word, and a prefix in mixed
     * capitalization.
     */
    private fun requestCurrentPrefix() {
        if (!eligible) return
        val activeEngine = engine
        if (activeEngine == null) {
            // Text events can arrive while preparation/start is still in flight. Do not expose the
            // band until publishEngine() establishes that the dictionary is actually available.
            displayedPrefix = null
            strip.hideSuggestions()
            return
        }
        if (!editor.hasKnownCursor()) {
            clearToReservedBand()
            return
        }
        val word = editor.cachedWordBeforeCursor()
        if (word.isEmpty()) {
            clearToReservedBand()
            return
        }
        // Cursor inside a word: the contract clears the results instead of offering a replacement
        // that would be spliced into the middle of the user's text ("ки|тап" + "т" must not become
        // "китапларtап"). Checked before the lookup so the engine is never even asked.
        if (editor.hasLetterAfterCursor()) {
            clearToReservedBand()
            return
        }
        // Mixed capitalization has no defined display form in the frozen contract, which requires
        // 0 results for it. Classified on the RAW prefix, before NFC/lowercase folding.
        val casing = TatarWordUtils.classifyCasing(word)
        if (casing == TatarWordUtils.PrefixCasing.MIXED) {
            clearToReservedBand()
            return
        }
        // Prefix changed relative to what is on screen: invalidate the displayed candidates NOW so
        // a tap arriving before the new result can never commit the old candidate against the new
        // prefix.
        if (word != displayedPrefix) {
            displayedPrefix = null
        }
        pendingPrefix = word
        requestSessionId = sessionId
        val prefixBytes = TatarWordUtils.toLookupBytes(TatarWordUtils.normalizeForLookup(word))
        val token = activeEngine.request(sessionId, SUBTYPE_ID, prefixBytes)
        if (token == null) {
            clearToReservedBand()
        }
    }

    /**
     * Publishes the empty-but-visible band and unbinds everything the strip was showing.
     *
     * The in-flight request generation is invalidated too: these paths deliberately do NOT issue a
     * new lookup, so the engine would still consider an older token current and a late result
     * could repaint words for text the user has already left. Clearing means clearing.
     */
    private fun clearToReservedBand() {
        displayedPrefix = null
        requestSessionId = NO_SESSION
        strip.reserve()
    }

    private fun applyResult(token: Any, suggestions: List<String>) {
        if (!eligible) return
        if (sessionId != requestSessionId) return
        val activeEngine = engine ?: return
        if (!activeEngine.isCurrent(token)) return
        if (suggestions.isEmpty()) {
            displayedPrefix = null
            strip.reserve()
            return
        }
        // The result passed the session and engine currency guards, so pendingPrefix is exactly the
        // prefix these candidates were computed for. Bind the displayed candidates to it atomically.
        displayedPrefix = pendingPrefix
        displayedSessionId = sessionId
        // Ranking runs on the normalized lowercase forms, so the typed capitalization is re-applied
        // here, after ranking and to the candidates that are actually shown. The casing comes from
        // the prefix this result was computed for, never from the live editor state, and the strip
        // hands the very same string back on tap, so the displayed and the inserted form match.
        val casing = TatarWordUtils.classifyCasing(pendingPrefix)
        strip.showSuggestions(
            TatarWordUtils.applyCasing(suggestions[0], casing),
            suggestions.getOrNull(1)?.let { TatarWordUtils.applyCasing(it, casing) },
            suggestions.getOrNull(2)?.let { TatarWordUtils.applyCasing(it, casing) },
        )
    }

    private fun onTap(suggestion: String) {
        val prefix = displayedPrefix
        if (prefix == null || displayedSessionId != sessionId) {
            // Nothing bound to the current session is displayed (e.g. the text changed and the old
            // candidates were invalidated): a tap must be a no-op and must never commit.
            return
        }
        // Commit against the DISPLAYED prefix, not the mutable pendingPrefix. The editor's own
        // stale-tap guard (re-reads live cache, requires collapsed selection and live trailing word
        // == expectedPrefix, deletes by code points) is the second line of defense.
        if (editor.commitSuggestion(prefix, suggestion)) {
            // The field is still eligible after a commit; clear the words but keep the reserved
            // band so accepting a suggestion does not resize the keyboard.
            displayedPrefix = null
            strip.reserve()
        }
    }

    companion object {
        private const val SUBTYPE_ID = "tt_RU"
        private const val DESTROY_TIMEOUT_MS = 60L

        // Sentinel for "no request is outstanding". [sessionId] starts at 0 and only ever grows,
        // so this can never be mistaken for a live generation.
        private const val NO_SESSION = -1L
    }
}
