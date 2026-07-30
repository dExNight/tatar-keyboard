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
import rkr.simplekeyboard.inputmethod.latin.dictionary.engine.KeyNeighborTable
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.PersonalSubtypes
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
 * Lazily created dictionary storage: background unpacking/validation plus the catalog the engine is
 * started from.
 *
 * Nothing behind this seam exists until preparation is actually requested, so a user who never
 * turns Tatar suggestions on never pays disk space or background work for them. JVM tests inject a
 * fake, which is what makes "preparation not requested / requested exactly once" observable without
 * Android.
 */
interface DictionaryPreparation {
    /**
     * Requests background preparation of the newest dictionary. There is no de-duplication behind
     * this seam — `DictionaryStorageController.prepare` is a straight delegate and
     * `BackgroundDictionaryPreparer` queues a fresh task per call — so the caller's "preparation
     * requested" flag is the only guard. [onResult] may arrive on any thread.
     */
    fun prepare(onResult: (PreparationResult) -> Unit)

    /** Catalog over the published dictionary, read by the engine factory off the UI thread. */
    fun catalog(): PublishedDictionaryCatalog
}

/**
 * Production [DictionaryPreparation] over the device-protected dictionary store.
 *
 * Built on the first preparation request only: constructing the store resolves the
 * device-protected context and the supported-artifact list, which is exactly the work that must not
 * happen for a user who leaves suggestions off.
 */
private class DeviceProtectedDictionaryPreparation(
    private val storage: DictionaryStorageController,
) : DictionaryPreparation {
    override fun prepare(onResult: (PreparationResult) -> Unit) {
        storage.prepare(onResult)
    }

    override fun catalog(): PublishedDictionaryCatalog = storage

    companion object {
        /** Returns null if the store cannot be built at all, leaving the caller fail-closed. */
        fun create(context: Context, executor: ExecutorService): DictionaryPreparation? = try {
            DeviceProtectedDictionaryPreparation(
                AndroidDictionaryStorageFactory.create(context, executor),
            )
        } catch (_: Throwable) {
            null
        }
    }
}

/**
 * Notified when a dictionary preparation that an *explicit* enable asked for ended
 * [PreparationResult.Unavailable].
 *
 * Only explicit enables are reported. A preparation started by the controller becoming eligible for
 * the first time was never asked for by the user, so failing it silently is the right answer; a
 * preparation started by an observed OFF -> ON transition answers a switch the user just flipped,
 * and leaving that unanswered would look like the setting simply did nothing.
 */
fun interface DictionaryUnavailableListener {
    fun onDictionaryUnavailableAfterExplicitEnable()
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
 * by LatinIME and passed in via [onStartInput]/[onSubtypeChanged]/[onSuggestionsSettingEnabled];
 * dictionary readiness is tracked internally.
 *
 * Nothing is built eagerly: the background executor, the storage controller and the dictionary
 * itself only come into existence once suggestions are actually wanted.
 */
class SuggestionsController internal constructor(
    private val strip: StripSurface,
    private val editor: EditorSurface,
    private val uiPoster: UiPoster,
    private val engineFactory: (ResultCallback) -> EngineHandle?,
    private val executorFactory: () -> ExecutorService?,
    private val preparationFactory: (ExecutorService) -> DictionaryPreparation?,
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
        strip,
        editor,
        UiPoster { runnable -> uiHandler.post(runnable) },
        engineFactory,
        { Executors.newSingleThreadExecutor() },
        { executor -> DeviceProtectedDictionaryPreparation.create(context, executor) },
        false,
    )

    /** Test entry point: injects a synchronous poster + executor and pre-marks the dictionary. */
    internal constructor(
        strip: StripSurface,
        editor: EditorSurface,
        uiPoster: UiPoster,
        engineFactory: (ResultCallback) -> EngineHandle?,
        backgroundExecutor: ExecutorService,
    ) : this(strip, editor, uiPoster, engineFactory, backgroundExecutor, true)

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
    ) : this(
        strip,
        editor,
        uiPoster,
        engineFactory,
        { backgroundExecutor },
        { null },
        dictionaryReady,
    )

    private var executor: ExecutorService? = null

    /**
     * Storage seam, created on the first preparation request. Written on the UI thread and read off
     * it by the production engine factory through [engineCatalog], hence volatile.
     */
    @Volatile
    private var preparation: DictionaryPreparation? = null

    @Volatile
    private var dictionaryReady: Boolean = initialDictionaryReady

    private var engine: EngineHandle? = null
    private var starting: Boolean = false
    private var eligible: Boolean = false
    private var destroyed: Boolean = false

    // The key-neighbor table for the fuzzy pass, built by LatinIME from the live layout. Remembered
    // so an engine started later is handed the current table, and re-pushed on every publish. Null
    // disables the fuzzy pass; the strip and its exact suggestions are unaffected either way.
    private var keyNeighbors: KeyNeighborTable? = null

    // Lifecycle of the "preparation requested" flag, in one place because nothing below it
    // de-duplicates: it is set the moment preparation is requested, it is NEVER cleared after a
    // Published result (readiness survives every later transition of the setting and the engine is
    // restarted from the already published file), and it is cleared ONLY when the last known result
    // was Unavailable and a fresh OFF -> ON transition of the setting has been observed.
    private var preparationRequested: Boolean = false
    private var lastPreparationUnavailable: Boolean = false

    // Provenance of the outstanding request, so an Unavailable result can tell the two callers of
    // requestPreparationIfNeeded apart. Only a request made because the user turned the setting on
    // is reported to [dictionaryUnavailableListener].
    private var preparationRequestedByExplicitEnable: Boolean = false

    /** Set by LatinIME; see [DictionaryUnavailableListener]. */
    var dictionaryUnavailableListener: DictionaryUnavailableListener? = null

    // Set when the setting goes ON -> OFF. The blocking engine teardown is deferred to the next
    // lifecycle boundary instead of running inside the settings handler, where it would hold the UI
    // thread for up to 240 ms on the very keystroke that flipped the setting.
    private var releasePending: Boolean = false

    // True once a deferred release has been attempted at a boundary and refused. The attempt is not
    // free of consequences: the engine has already been told to stop and rejects every later lookup,
    // so it is no longer a correct mapping and the setting coming back on may no longer cancel its
    // release. Without this the cancellation would strand a permanently dead engine — nothing would
    // release it and nothing would replace it — and the user would see an empty band for the rest of
    // the process.
    private var releaseAttemptFailed: Boolean = false

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

    /**
     * Registers the tap listener and deliberately nothing else: the background executor, the
     * storage controller and the dictionary are created on first actual need, so a keyboard start
     * with the setting off does no dictionary work at all.
     */
    fun onCreate() {
        strip.setTapListener(SuggestionTapListener { suggestion -> onTap(suggestion) })
    }

    /**
     * Publishes the key-neighbor table used by the fuzzy suggestion pass. LatinIME rebuilds it from
     * the live layout whenever the keyboard or subtype changes and hands it here; a null table (a
     * non-alphabet layout or an ineligible field) disables the fuzzy pass. Stored so an engine
     * started later still receives it, and forwarded to the running engine at once. UI thread only.
     */
    fun updateKeyNeighbors(table: KeyNeighborTable?) {
        keyNeighbors = table
        engine?.updateKeyNeighbors(table)
    }

    fun onStartInput(eligible: Boolean) {
        // Lifecycle boundary: one of the only two places allowed to run the blocking engine
        // teardown that a disabled setting scheduled.
        runPendingRelease()
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
        val engineWasReady = usableEngine() != null
        if (!engineWasReady) {
            strip.hideSuggestions()
        } else {
            strip.reserve()
        }
        strip.setTapListener(SuggestionTapListener { suggestion -> onTap(suggestion) })
        // Becoming eligible is the second of the two events that may request preparation; the flag
        // keeps every later start from queueing another one.
        requestPreparationIfNeeded()
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
            if (usableEngine() == null) {
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
        // The other lifecycle boundary at which a deferred release may run.
        runPendingRelease()
    }

    fun onSubtypeChanged(eligible: Boolean) {
        sessionId++
        engine?.finishInput()
        displayedPrefix = null
        this.eligible = eligible
        if (eligible) {
            val engineWasReady = usableEngine() != null
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
            requestPreparationIfNeeded()
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

    /**
     * Personal words were erased ("Erase all" or "Forget", E4b) while the keyboard is up.
     *
     * Uses EXACTLY the mechanism of an actual subtype change — bump the session (which invalidates
     * any in-flight generation), idle the engine, unbind the displayed candidates — because a second
     * mechanism for the same job is what drifts apart later. The engine itself is untouched: the
     * personal source it reads has already published an empty snapshot, so the next lookup simply
     * finds nothing personal.
     *
     * Saying only "the NEXT lookup has no personal candidates" would not be enough: the user who
     * just confirmed the dialog would still see the erased word in the band, and a tap would insert
     * it through the single commit path like any other candidate. For a feature whose whole value is
     * "erased means erased", that is a defect in the guarantee itself.
     */
    fun onPersonalDictionaryErased() {
        if (destroyed) return
        sessionId++
        engine?.finishInput()
        displayedPrefix = null
        displayedSessionId = NO_SESSION
        if (eligible) strip.reserve() else strip.hideSuggestions()
    }

    /**
     * The suggestions setting went ON -> OFF while the IME is live.
     *
     * Everything the user can see or reach stops at once, but the engine teardown is only
     * *scheduled*: [destroyHandle] blocks the UI thread for up to 240 ms, which must never land on
     * the keystroke that flipped the setting. Deliberately a separate method rather than a reuse of
     * [onSubtypeChanged]: the two events differ in exactly the part that matters here, whether the
     * engine has to go away at all.
     */
    fun onSuggestionsSettingDisabled() {
        if (destroyed) return
        // Bumping the session invalidates any in-flight lookup, so a result computed for the older
        // generation can no longer repaint the strip.
        sessionId++
        eligible = false
        displayedPrefix = null
        requestSessionId = NO_SESSION
        strip.hideSuggestions()
        engine?.finishInput()
        // Unconditional: a start that is still in flight publishes a live handle even now (see
        // publishEngine's ineligible branch), and that handle must be released at the boundary too.
        releasePending = true
    }

    /**
     * The suggestions setting went OFF -> ON while the IME is live: the mirror image of
     * [onSuggestionsSettingDisabled] and the only thing that sets eligibility on this path. A
     * SharedPreferences change calls neither [onStartInput] nor [onSubtypeChanged], so without this
     * method the strip would stay hidden until the user left the field and came back.
     *
     * @param eligible freshly recomputed by LatinIME for the current field and subtype.
     */
    fun onSuggestionsSettingEnabled(eligible: Boolean) {
        if (destroyed) return
        // The setting came back before the deferred release ran: the live engine is still the right
        // mapping, so the release is cancelled instead of being performed and immediately undone.
        // Only a release that has not been ATTEMPTED yet may be cancelled: a refused attempt has
        // already stopped the engine for good (it rejects every later lookup), so that one stays
        // scheduled and the next boundary retries it, after which a fresh engine is started.
        if (!releaseAttemptFailed) {
            releasePending = false
        }
        if (lastPreparationUnavailable) {
            // The single reset point of the requested flag: the last attempt ended Unavailable and
            // a new OFF -> ON transition has now been observed.
            preparationRequested = false
            lastPreparationUnavailable = false
        }
        this.eligible = eligible
        if (!eligible) {
            // The setting is on, but this field or subtype does not qualify. Nothing may become
            // visible, yet the observed transition still requests preparation so the dictionary is
            // there by the time a Tatar field is opened.
            requestPreparationIfNeeded(explicitEnable = true)
            return
        }
        val engineWasReady = usableEngine() != null
        if (engineWasReady) {
            strip.reserve()
        } else {
            // Cold, preparing and unavailable dictionaries all stay GONE until publish succeeds.
            strip.hideSuggestions()
        }
        // The strip view is inflated lazily from setTapListener(), and while the setting was off it
        // may never have existed at all, so an earlier registration was silently dropped. Re-wire
        // it exactly like onStartInput() does; without this a tap would do nothing for the rest of
        // the editor session (closed HIGH finding of the D1 audit).
        strip.setTapListener(SuggestionTapListener { suggestion -> onTap(suggestion) })
        requestPreparationIfNeeded(explicitEnable = true)
        // Same shape as onStartInput(): capture readiness first so a cold engine that publishes
        // inline requests the current prefix exactly once, from publishEngine().
        maybeStartEngine()
        if (
            engineWasReady &&
            editor.hasKnownCursor() &&
            editor.cachedWordBeforeCursor().isNotEmpty()
        ) {
            requestCurrentPrefix()
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

    /**
     * Catalog for the production engine factory. Null until a preparation request has actually
     * created the storage controller; the factory then produces no engine at all and the strip
     * stays GONE, which is the intended fail-closed behaviour rather than an error.
     */
    fun engineCatalog(): PublishedDictionaryCatalog? = preparation?.catalog()

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

    /**
     * Requests background preparation at most once per enable cycle, creating the background
     * executor and the storage controller on the way if they do not exist yet. Called from every
     * path that can make suggestions wanted; the flag, not the callers, is what keeps the count at
     * one.
     */
    private fun requestPreparationIfNeeded(explicitEnable: Boolean = false) {
        if (destroyed || preparationRequested) return
        // A dictionary that is already published is never prepared again: readiness outlives every
        // later transition of the setting.
        if (dictionaryReady) return
        val active = dictionaryPreparation() ?: return
        preparationRequested = true
        preparationRequestedByExplicitEnable = explicitEnable
        active.prepare { result ->
            // The callback runs on the background executor. Marshal onto the serialized UI owner
            // before touching any controller state.
            uiPoster.post { onPreparationResult(result) }
        }
    }

    /**
     * Handles a preparation result on the UI owner.
     *
     * [PreparationResult.Unavailable] is terminal for the current enable cycle: the strip stays
     * GONE, plain typing is untouched and nothing is logged. Only a fresh OFF -> ON transition of
     * the setting can clear the flag and allow another attempt. The user is shown nothing either,
     * with one exception — a request the user made themselves gets the one-shot message of
     * [DictionaryUnavailableListener].
     */
    private fun onPreparationResult(result: PreparationResult) {
        if (destroyed) return
        when (result) {
            is PreparationResult.Published -> {
                lastPreparationUnavailable = false
                // Start the engine (and look up whatever is already typed) so a field opened before
                // the dictionary finished preparing still gets suggestions this session.
                onDictionaryReady()
            }
            is PreparationResult.Unavailable -> {
                lastPreparationUnavailable = true
                if (preparationRequestedByExplicitEnable) {
                    dictionaryUnavailableListener?.onDictionaryUnavailableAfterExplicitEnable()
                }
            }
        }
    }

    /** Lazily built storage seam; null means fail-closed, with no dictionary and no engine. */
    private fun dictionaryPreparation(): DictionaryPreparation? {
        preparation?.let { return it }
        val backgroundExecutor = backgroundExecutor() ?: return null
        val created = try {
            preparationFactory(backgroundExecutor)
        } catch (_: Throwable) {
            null
        } ?: return null
        preparation = created
        return created
    }

    /**
     * The single background executor, created on the first real need (dictionary preparation or
     * engine start) and never recreated after [onDestroy].
     */
    private fun backgroundExecutor(): ExecutorService? {
        if (destroyed) return null
        executor?.let { return it }
        val created = try {
            executorFactory()
        } catch (_: Throwable) {
            null
        } ?: return null
        executor = created
        return created
    }

    private fun maybeStartEngine() {
        if (engine != null || starting || !dictionaryReady || !eligible) return
        // A lease that has not been released yet still belongs to this controller: never map a
        // second dictionary on top of it. The retry happens at the next lifecycle boundary.
        if (releasePending) return
        val backgroundExecutor = backgroundExecutor() ?: return
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
        // Hand the freshly started engine the current key-neighbor table so its fuzzy pass is armed
        // without waiting for the next layout change. Null is a valid value (fuzzy pass disabled).
        handle.updateKeyNeighbors(keyNeighbors)
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
     * Runs the engine release that a disabled setting scheduled, if it has not been cancelled.
     *
     * Called only from the two lifecycle boundaries ([onStartInput] and [onFinishInput]), never
     * from the settings handler. It does not mark the controller destroyed and does not shut the
     * background executor down: the controller stays fully usable, and re-enabling the setting
     * starts a fresh engine from the already published file. The engine reference is dropped only
     * on a successful release; a lease that refused to close keeps the request pending so the next
     * boundary retries it, and until then no new engine is started on top of it. A refusal is also
     * remembered in [releaseAttemptFailed], which takes the cancellation in
     * [onSuggestionsSettingEnabled] off the table for this release: the handle has already been
     * asked to stop and would be kept alive as a permanently mute engine.
     */
    private fun runPendingRelease() {
        if (!releasePending) return
        val handle = engine
        if (handle == null) {
            // Nothing was ever started, or it is already gone: the request is satisfied.
            releasePending = false
            releaseAttemptFailed = false
            return
        }
        if (destroyHandle(handle)) {
            engine = null
            releasePending = false
            releaseAttemptFailed = false
        } else {
            // Remember the refusal: from here on the setting coming back on no longer cancels this
            // release, because the handle it would keep can no longer serve a single lookup.
            releaseAttemptFailed = true
        }
    }

    /**
     * The engine that may still be used, or null.
     *
     * An engine with a pending release is deliberately invisible to every path that exposes the
     * band or dispatches a lookup. Before the release is attempted this only avoids painting a band
     * that is about to go away; after a refused attempt it is what keeps the strip honest, because
     * the handle rejects every request from then on and a reserved band would stay empty forever.
     * The reference itself is kept so the release can be retried at the next boundary.
     */
    private fun usableEngine(): EngineHandle? = if (releasePending) null else engine

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
        val activeEngine = usableEngine()
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
        val activeEngine = usableEngine() ?: return
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
        // The active-subtype identifier the engine is asked to key its lookup by. Reads the single
        // source of truth (PersonalSubtypes.TATAR_RU) so the request key can never drift from
        // LatinIME.isTatarSuggestionsEligible(), which reads the same constant.
        private const val SUBTYPE_ID = PersonalSubtypes.TATAR_RU
        private const val DESTROY_TIMEOUT_MS = 60L

        // Sentinel for "no request is outstanding". [sessionId] starts at 0 and only ever grows,
        // so this can never be mistaken for a live generation.
        private const val NO_SESSION = -1L
    }
}
