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
 * state is touched.
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
    private var requestSessionId: Long = -1L
    private var pendingPrefix: String = ""

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
                dictionaryReady = true
            }
        }
    }

    fun onStartInput(eligible: Boolean) {
        sessionId++
        this.eligible = eligible
        if (!eligible) {
            strip.hideSuggestions()
            engine?.finishInput()
            return
        }
        strip.reserve()
        strip.setTapListener(SuggestionTapListener { suggestion -> onTap(suggestion) })
        maybeStartEngine()
    }

    fun onTextChanged() {
        if (!eligible) return
        if (!editor.hasKnownCursor()) {
            strip.reserve()
            return
        }
        val activeEngine = engine ?: return
        val word = editor.cachedWordBeforeCursor()
        if (word.isEmpty()) {
            strip.reserve()
            return
        }
        pendingPrefix = word
        requestSessionId = sessionId
        val prefixBytes = TatarWordUtils.toLookupBytes(TatarWordUtils.normalizeForLookup(word))
        val token = activeEngine.request(sessionId, SUBTYPE_ID, prefixBytes)
        if (token == null) {
            strip.reserve()
        }
    }

    fun onSelectionChanged() {
        sessionId++
        engine?.finishInput()
        // Invalidate any in-flight request, but keep the reserved band while the field stays
        // eligible so an external selection change never collapses the keyboard height. When the
        // field is not eligible the strip is already GONE and must stay that way.
        if (eligible) {
            strip.reserve()
        }
    }

    fun onFinishInput() {
        sessionId++
        strip.hideSuggestions()
        engine?.finishInput()
    }

    fun onSubtypeChanged(eligible: Boolean) {
        sessionId++
        engine?.finishInput()
        this.eligible = eligible
        if (eligible) {
            strip.reserve()
        } else {
            strip.hideSuggestions()
        }
    }

    fun onDestroy() {
        // Set the guard first so an engine start that publishes after this point (posted onto the
        // UI thread from the background executor) is torn down instead of orphaned.
        destroyed = true
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

    private fun maybeStartEngine() {
        if (engine != null || starting || !dictionaryReady) return
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
        if (handle == null) return
        engine = handle
        if (!eligible) {
            handle.finishInput()
            strip.hideSuggestions()
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

    private fun applyResult(token: Any, suggestions: List<String>) {
        if (!eligible) return
        if (sessionId != requestSessionId) return
        val activeEngine = engine ?: return
        if (!activeEngine.isCurrent(token)) return
        if (suggestions.isEmpty()) {
            strip.reserve()
            return
        }
        strip.showSuggestions(
            suggestions[0],
            suggestions.getOrNull(1),
            suggestions.getOrNull(2),
        )
    }

    private fun onTap(suggestion: String) {
        if (editor.commitSuggestion(pendingPrefix, suggestion)) {
            // The field is still eligible after a commit; clear the words but keep the reserved
            // band so accepting a suggestion does not resize the keyboard.
            strip.reserve()
        }
    }

    companion object {
        private const val SUBTYPE_ID = "tt_RU"
        private const val DESTROY_TIMEOUT_MS = 60L
    }
}
