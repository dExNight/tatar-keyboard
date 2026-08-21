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
import rkr.simplekeyboard.inputmethod.latin.dictionary.engine.AutocorrectPolicy
import rkr.simplekeyboard.inputmethod.latin.dictionary.engine.KeyNeighborTable
import rkr.simplekeyboard.inputmethod.latin.dictionary.engine.LookupKind
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.PersonalSubtypes
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.AndroidBigramStorageFactory
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.AndroidDictionaryStorageFactory
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.BigramPreparationResult
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.BigramStorageController
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.DictionaryArtifactSpec
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.DictionaryStorageController
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.WordCompletionSink
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.PreparationResult
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.PublishedBigramTableCatalog
import rkr.simplekeyboard.inputmethod.latin.dictionary.storage.PublishedDictionaryCatalog
import java.util.concurrent.ConcurrentHashMap
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

    /**
     * The SECOND insertion path of the frozen text contract (D3): replaces the trailing word
     * [expectedPrefix] with [replacement] through the very same explicit delete-by-code-points plus
     * `commitText` in ONE batch edit that an accepted suggestion goes through, and with the same
     * re-checks. It differs from [commitSuggestion] in one thing only — no trailing auto-space,
     * because the separator the user just pressed is committed right after it by the ordinary input
     * path.
     *
     * Returns false without editing anything if any check fails. Defaults to false so an editor
     * surface written before D3 keeps compiling and simply never autocorrects.
     */
    fun replaceTypedWord(expectedPrefix: String, replacement: String): Boolean = false

    /**
     * Undoes the last autocorrection: where [insertedForm] + [separator] stands immediately before
     * the cursor, puts [typedForm] + [separator] back, in one batch edit.
     *
     * The suffix match IS the position check the contract asks for, and a stricter one than an
     * offset: an offset can coincide again after unrelated edits, the exact text cannot. Returns
     * false without editing anything when the text before the cursor is no longer what the
     * replacement left there. Defaults to false, like [replaceTypedWord].
     */
    fun revertTypedWord(insertedForm: String, separator: String, typedForm: String): Boolean = false

    /**
     * E5d NEXT_WORD context extraction from the live cache (PROPOSALS.md, "Контракт текста"
     * amendment, 2026-08-17): the word immediately before a trailing run of one-or-more U+0020 right
     * at the cursor, or "" if there is none. Defaults to "" so an editor surface written before E5d
     * keeps compiling and NEXT_WORD simply never fires.
     */
    fun cachedNextWordContext(): String = ""

    /**
     * The THIRD insertion path of the frozen text contract (E5d): commits a predicted next word.
     * Unlike [commitSuggestion] and [replaceTypedWord], this one deletes NOTHING — NEXT_WORD only
     * ever fires on an empty prefix, so there is nothing trailing to remove; it only inserts, with
     * the same auto-space rule an accepted suggestion uses.
     *
     * Re-checked against the live cache: collapsed selection, no letter right after the cursor (the
     * same two checks the other two paths make), an EMPTY trailing word (a non-empty one means the
     * user typed something after the request was built — the tap is stale), and the live context
     * word re-extracted by [cachedNextWordContext]'s own algorithm matching [expectedContextWord]
     * exactly. Defaults to false, like [replaceTypedWord] and [revertTypedWord].
     */
    fun commitPredictedWord(expectedContextWord: String, suggestion: String): Boolean = false
}

/**
 * Reads the live value of `PREF_TATAR_AUTOCORRECT`. A seam, so the controller needs no preferences
 * and JVM tests can flip the setting between two keystrokes exactly as a user can.
 */
fun interface AutocorrectGate {
    fun isOn(): Boolean
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
        /**
         * Storage for the dictionary of [subtypeId], or null when that subtype ships none (every
         * layout but the two that do) or the store cannot be built at all — both leave the caller
         * fail-closed with no engine and a hidden strip.
         */
        fun create(
            context: Context,
            executor: ExecutorService,
            subtypeId: String,
        ): DictionaryPreparation? = try {
            val artifact = DictionaryArtifactSpec.forSubtype(subtypeId)
            if (artifact == null) {
                null
            } else {
                DeviceProtectedDictionaryPreparation(
                    AndroidDictionaryStorageFactory.create(context, executor, artifact),
                )
            }
        } catch (_: Throwable) {
            null
        }
    }
}

/**
 * E5c two-stage readiness: lazily created bigram-table storage, the exact same shape as
 * [DictionaryPreparation] for the exact same reason (a user who never turns suggestions on never
 * pays disk space or background work for the bigram table either) — kept a SEPARATE interface
 * rather than folding into [DictionaryPreparation] because the two artifacts already don't share
 * a spec, validator or store (`docs/DICTIONARY-E5B.md`), and merging their controller seams here
 * would just recreate that coupling one layer up.
 */
interface BigramPreparation {
    fun prepare(onResult: (BigramPreparationResult) -> Unit)
    fun catalog(): PublishedBigramTableCatalog
}

/** Production [BigramPreparation] over the device-protected bigram-table store. */
private class DeviceProtectedBigramPreparation(
    private val storage: BigramStorageController,
) : BigramPreparation {
    override fun prepare(onResult: (BigramPreparationResult) -> Unit) = storage.prepare(onResult)

    override fun catalog(): PublishedBigramTableCatalog = storage

    companion object {
        /**
         * Storage for the next-word table of [subtypeId], or null when that subtype ships none —
         * asked of the SAME registry [DeviceProtectedDictionaryPreparation.create] asks for the
         * dictionary, so the two can never end up on different languages. A language present in
         * the registry with no table, and a subtype absent from it entirely, both land here as
         * null and leave NEXT_WORD answering an empty list: the exact fail-closed shape a missing
         * table already had, with no effect on prefix suggestions or ordinary input.
         */
        fun create(
            context: Context,
            executor: ExecutorService,
            subtypeId: String,
        ): BigramPreparation? = try {
            val artifact = DictionaryArtifactSpec.bigramsForSubtype(subtypeId)
            if (artifact == null) {
                null
            } else {
                DeviceProtectedBigramPreparation(
                    AndroidBigramStorageFactory.create(context, executor, artifact),
                )
            }
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
    private val engineFactory: (String, ResultCallback) -> EngineHandle?,
    private val executorFactory: () -> ExecutorService?,
    private val preparationFactory: (ExecutorService, String) -> DictionaryPreparation?,
    initialDictionaryReady: Boolean,
    // E5c: trailing default so every existing internal test constructor below (there is no
    // bigram table in any of their fakes) needs no change at all — only the production
    // constructor passes real wiring.
    private val bigramPreparationFactory: (ExecutorService, String) -> BigramPreparation? =
        { _, _ -> null },
) {
    /** Production entry point (frozen contract). */
    constructor(
        context: Context,
        strip: StripSurface,
        editor: EditorSurface,
        uiHandler: Handler,
        engineFactory: (String, ResultCallback) -> EngineHandle?,
    ) : this(
        strip,
        editor,
        UiPoster { runnable -> uiHandler.post(runnable) },
        engineFactory,
        { Executors.newSingleThreadExecutor() },
        { executor, subtypeId ->
            DeviceProtectedDictionaryPreparation.create(context, executor, subtypeId)
        },
        false,
        { executor, subtypeId ->
            DeviceProtectedBigramPreparation.create(context, executor, subtypeId)
        },
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
        { _, callback -> engineFactory(callback) },
        { backgroundExecutor },
        { _, _ -> null },
        dictionaryReady,
    )

    private var executor: ExecutorService? = null

    /**
     * Everything that belongs to ONE language: its storage seams, its readiness, its engine and
     * the bookkeeping of its preparation and release.
     *
     * Slots are what makes switching layouts free. Both dictionaries are separate artifacts in
     * separate device-protected directories with separate leases, so the engine of a language the
     * user leaves is simply idled ([EngineHandle.finishInput]) and kept warm — it is NOT torn down.
     * A teardown blocks the UI thread for up to 240 ms ([destroyHandle]) and its release is
     * deliberately deferred to a lifecycle boundary, so tearing down on every press of the globe
     * key would either stall the keystroke or leave the user with no suggestions until they left
     * the field. Warm slots cost one idle worker thread and one read-only mapping each; the
     * mapping is file-backed, so its pages are evictable and only the ones actually touched by a
     * lookup are resident.
     */
    private inner class LanguageSlot(val subtypeId: String) {
        /** Storage seam, created on this language's first preparation request. */
        @Volatile
        var preparation: DictionaryPreparation? = null

        /** E5c two-stage readiness: same lazy-seam shape as [preparation], for the bigram table. */
        @Volatile
        var bigramPreparation: BigramPreparation? = null

        @Volatile
        var dictionaryReady: Boolean = false

        var engine: EngineHandle? = null
        var starting: Boolean = false

        // Lifecycle of the "preparation requested" flag, in one place because nothing below it
        // de-duplicates: it is set the moment preparation is requested, it is NEVER cleared after a
        // Published result (readiness survives every later transition of the setting and the engine
        // is restarted from the already published file), and it is cleared ONLY when the last known
        // result was Unavailable and a fresh OFF -> ON transition of the setting has been observed.
        var preparationRequested: Boolean = false
        var lastPreparationUnavailable: Boolean = false

        // Provenance of the outstanding request, so an Unavailable result can tell the two callers
        // of requestPreparationIfNeeded apart. Only a request made because the user turned the
        // setting on is reported to [dictionaryUnavailableListener].
        var preparationRequestedByExplicitEnable: Boolean = false

        // Set when the setting goes ON -> OFF. The blocking engine teardown is deferred to the next
        // lifecycle boundary instead of running inside the settings handler, where it would hold
        // the UI thread for up to 240 ms on the very keystroke that flipped the setting.
        var releasePending: Boolean = false

        // True once a deferred release has been attempted at a boundary and refused. The attempt is
        // not free of consequences: the engine has already been told to stop and rejects every
        // later lookup, so it is no longer a correct mapping and the setting coming back on may no
        // longer cancel its release. Without this the cancellation would strand a permanently dead
        // engine — nothing would release it and nothing would replace it — and the user would see
        // an empty band for the rest of the process.
        var releaseAttemptFailed: Boolean = false
    }

    /**
     * One slot per language, created on first need and kept for the controller's lifetime.
     *
     * Concurrent because the production engine factory reads it from the background executor
     * through [engineCatalog] while the UI thread may be inserting the slot of a language the user
     * has just switched to.
     */
    private val slots = ConcurrentHashMap<String, LanguageSlot>()

    /**
     * The subtype whose dictionary is currently selected, or null when the active subtype ships
     * none. This is the ONE place the choice of dictionary is made; everything downstream —
     * storage directory, engine, personal store, lookup key — follows from it.
     */
    private var activeLanguage: String? = DEFAULT_LANGUAGE

    private var eligible: Boolean = false
    private var destroyed: Boolean = false

    // The key-neighbor table for the fuzzy pass, built by LatinIME from the live layout. Remembered
    // so an engine started later is handed the current table, and re-pushed on every publish. Null
    // disables the fuzzy pass; the strip and its exact suggestions are unaffected either way.
    private var keyNeighbors: KeyNeighborTable? = null

    /** Set by LatinIME; see [DictionaryUnavailableListener]. */
    var dictionaryUnavailableListener: DictionaryUnavailableListener? = null

    init {
        // The frozen test entry points seed readiness without naming a language; they mean the one
        // that was the only one for the app's first five releases.
        if (initialDictionaryReady) slotFor(DEFAULT_LANGUAGE).dictionaryReady = true
    }

    /** The slot of [subtypeId], created on first mention. Creating one costs no I/O. */
    private fun slotFor(subtypeId: String): LanguageSlot =
        slots.getOrPut(subtypeId) { LanguageSlot(subtypeId) }

    /** The slot of the active language, or null when the active subtype ships no dictionary. */
    private fun activeSlot(): LanguageSlot? = activeLanguage?.let(::slotFor)

    /**
     * Points the controller at [subtypeId]'s dictionary, idling whatever engine the language being
     * left still holds.
     *
     * Idling, not destroying: see [LanguageSlot]. The caller has already bumped the session, so
     * nothing in flight for the old language can repaint the strip.
     */
    private fun setActiveLanguage(subtypeId: String?) {
        val resolved = subtypeId?.takeIf { DictionaryArtifactSpec.forSubtype(it) != null }
        if (resolved == activeLanguage) return
        activeSlot()?.engine?.finishInput()
        activeLanguage = resolved
    }

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

    // --- E5d NEXT_WORD state, the exact same shape as pendingPrefix/displayedPrefix above, for the
    // other kind of query. At most one of displayedPrefix/displayedContextWord is ever non-null at a
    // time (PROPOSALS.md, "Контракт текста" amendment, "Сосуществование") — enforced by clearing the
    // other one every time either request path runs, not assumed.
    private var pendingContextWord: String = ""
    private var displayedContextWord: String? = null

    // --- E4c clean-run state. Nothing here is persisted and nothing leaves this object except one
    // completed word handed to [completionSink]; with the default sink that is a no-op.
    /** The trailing word as last seen. Empty between words. */
    private var runWord: String = ""
    /** False as soon as anything but plain growth happens; a dirty run reports nothing. */
    private var runClean: Boolean = true
    /** Length of the longest proper prefix of the current run that came back with NO candidates. */
    private var runEmptyResultPrefixLength: Int = NO_EMPTY_RESULT

    /** Where clean completions go (E4c). Default writes nothing at all. */
    private var completionSink: WordCompletionSink = WordCompletionSink.NONE

    // --- D3 autocorrect state. Nothing here is persisted and nothing leaves this object except the
    // two editor calls that perform the replacement and its single undo.
    /** The autocorrect setting, read live. OFF until LatinIME wires the real one. */
    private var autocorrectGate: AutocorrectGate = AutocorrectGate { false }

    /**
     * One replacement, as far as the undo is concerned. There is no history: at most one of these
     * exists at a time and it is dropped, never stacked.
     */
    private class Replacement(
        val typedForm: String,
        val insertedForm: String,
        val separator: String,
        val sessionId: Long,
    ) {
        /** Deliberately mute: this object carries the user's text. */
        override fun toString(): String = "Replacement"
    }

    /**
     * A replacement that has been made but whose separator has not been committed yet. It survives
     * EXACTLY ONE [onTextChanged] — the one carrying that separator, which is part of the same user
     * action — and becomes [revertable] there. Every later event finds [revertable] and drops it.
     */
    private var armedReplacement: Replacement? = null

    /** The one replacement a backspace may still undo. Null means the window has closed. */
    private var revertable: Replacement? = null

    /** Set once by LatinIME. Kept out of the constructor so the frozen test entry points stay put. */
    fun setCompletionSink(sink: WordCompletionSink) {
        completionSink = sink
    }

    /** Set once by LatinIME, for the same reason as [setCompletionSink]. */
    fun setAutocorrectGate(gate: AutocorrectGate) {
        autocorrectGate = gate
    }

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
        // Only the active language's engine is ever asked anything, and LatinIME rebuilds the table
        // from the live layout on every subtype change, so a warm engine of another language keeps
        // the table of its own layout until it becomes active again and is handed a fresh one.
        activeSlot()?.engine?.updateKeyNeighbors(table)
    }

    @JvmOverloads
    fun onStartInput(eligible: Boolean, subtypeId: String? = DEFAULT_LANGUAGE) {
        markRunDirty()
        // A new field is one of the six events that make an undo impossible.
        clearRevertState()
        // Lifecycle boundary: one of the only two places allowed to run the blocking engine
        // teardown that a disabled setting scheduled.
        runPendingRelease()
        sessionId++
        displayedPrefix = null
        displayedContextWord = null
        setActiveLanguage(subtypeId)
        this.eligible = eligible && activeLanguage != null
        if (!this.eligible) {
            strip.hideSuggestions()
            activeSlot()?.engine?.finishInput()
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
        // E5d: no longer gated on a non-empty prefix — requestCurrentPrefix() falls through to
        // NEXT_WORD on an empty one, and re-requesting on this boundary is what lets switching
        // fields show a prediction without an extra keystroke, the same reason this call exists
        // for PREFIX at all.
        if (engineWasReady && editor.hasKnownCursor()) {
            requestCurrentPrefix()
        }
    }

    fun onTextChanged() {
        advanceRevertWindow()
        trackCleanRun(editor.cachedWordBeforeCursor())
        requestCurrentPrefix()
    }

    fun onSelectionChanged() {
        // A selection change — external, or an internal cursor gesture, which LatinIME routes here —
        // breaks the run: what looks like growth afterwards may be growth of a different word. It is
        // also two of the six events that close the undo window, and for the same reason: the text
        // the replacement described is no longer the text at the cursor.
        markRunDirty()
        clearRevertState()
        sessionId++
        activeSlot()?.engine?.finishInput()
        // Any in-flight request is invalidated and whatever was shown is no longer bound to the
        // live editor state, so drop the displayed binding immediately.
        displayedPrefix = null
        displayedContextWord = null
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
        markRunDirty()
        // The contract names this boundary explicitly: the replacement state is erased on
        // onFinishInput and never outlives the editor session.
        clearRevertState()
        // The one boundary where the personal store writes what it has accumulated: usage counters
        // and pending hashes, once, and only if something changed.
        completionSink.onInputFinished()
        sessionId++
        displayedPrefix = null
        displayedContextWord = null
        // Close eligibility before hiding/finishing. A readiness notification queued behind this
        // lifecycle boundary must not start or publish an engine for the finished editor session.
        eligible = false
        strip.hideSuggestions()
        activeSlot()?.engine?.finishInput()
        // The other lifecycle boundary at which a deferred release may run.
        runPendingRelease()
    }

    /**
     * The active subtype changed. [subtypeId] is the NEW subtype's identifier, and it — not the
     * boolean — is what selects the dictionary: switching between the Tatar and the Russian layout
     * switches which of the two shipped dictionaries answers the next keystroke.
     *
     * The boolean-only overload is the monolingual shorthand every caller written before the second
     * dictionary used: it means "still the Tatar subtype, eligibility recomputed".
     */
    @JvmOverloads
    fun onSubtypeChanged(eligible: Boolean, subtypeId: String? = DEFAULT_LANGUAGE) {
        markRunDirty()
        // A subtype change is one of the six events that make an undo impossible.
        clearRevertState()
        sessionId++
        // Idles the engine of the language being left; setActiveLanguage does the same for a real
        // language change, and doing it here as well keeps a same-language subtype change (a
        // different layout for the same dictionary) behaving exactly as it always did.
        activeSlot()?.engine?.finishInput()
        displayedPrefix = null
        displayedContextWord = null
        setActiveLanguage(subtypeId)
        this.eligible = eligible && activeLanguage != null
        if (this.eligible) {
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
            // E5d: see the comment on the identical gate in onStartInput().
            if (engineWasReady && editor.hasKnownCursor()) {
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
        clearRevertState()
        sessionId++
        activeSlot()?.engine?.finishInput()
        displayedPrefix = null
        displayedContextWord = null
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
        // Autocorrect is subordinate to suggestions, so the undo window closes with them.
        clearRevertState()
        // Bumping the session invalidates any in-flight lookup, so a result computed for the older
        // generation can no longer repaint the strip.
        sessionId++
        eligible = false
        displayedPrefix = null
        displayedContextWord = null
        requestSessionId = NO_SESSION
        strip.hideSuggestions()
        // The setting is global, so EVERY language stops, not just the active one: a warm engine of
        // a language the user is not typing in right now still holds a lease and a mapping, and the
        // user turned the whole feature off.
        for (slot in slots.values) {
            slot.engine?.finishInput()
            // Unconditional: a start that is still in flight publishes a live handle even now (see
            // publishEngine's ineligible branch), and that handle must be released at the boundary
            // too.
            slot.releasePending = true
        }
    }

    /**
     * The suggestions setting went OFF -> ON while the IME is live: the mirror image of
     * [onSuggestionsSettingDisabled] and the only thing that sets eligibility on this path. A
     * SharedPreferences change calls neither [onStartInput] nor [onSubtypeChanged], so without this
     * method the strip would stay hidden until the user left the field and came back.
     *
     * @param eligible freshly recomputed by LatinIME for the current field and subtype.
     */
    @JvmOverloads
    fun onSuggestionsSettingEnabled(eligible: Boolean, subtypeId: String? = DEFAULT_LANGUAGE) {
        if (destroyed) return
        clearRevertState()
        // Mirrors onSuggestionsSettingDisabled: the setting is global, so every language it stopped
        // is un-stopped here.
        for (slot in slots.values) {
            // The setting came back before the deferred release ran: the live engine is still the
            // right mapping, so the release is cancelled instead of being performed and immediately
            // undone. Only a release that has not been ATTEMPTED yet may be cancelled: a refused
            // attempt has already stopped the engine for good (it rejects every later lookup), so
            // that one stays scheduled and the next boundary retries it, after which a fresh engine
            // is started.
            if (!slot.releaseAttemptFailed) {
                slot.releasePending = false
            }
            if (slot.lastPreparationUnavailable) {
                // The single reset point of the requested flag: the last attempt ended Unavailable
                // and a new OFF -> ON transition has now been observed.
                slot.preparationRequested = false
                slot.lastPreparationUnavailable = false
            }
        }
        setActiveLanguage(subtypeId)
        this.eligible = eligible && activeLanguage != null
        if (!this.eligible) {
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
        // E5d: no longer gated on a non-empty prefix — requestCurrentPrefix() falls through to
        // NEXT_WORD on an empty one, and re-requesting on this boundary is what lets switching
        // fields show a prediction without an extra keystroke, the same reason this call exists
        // for PREFIX at all.
        if (engineWasReady && editor.hasKnownCursor()) {
            requestCurrentPrefix()
        }
    }

    fun onDestroy() {
        // Set the guard first so an engine start that publishes after this point (posted onto the
        // UI thread from the background executor) is torn down instead of orphaned.
        destroyed = true
        clearRevertState()
        displayedPrefix = null
        displayedContextWord = null
        for (slot in slots.values) {
            val handle = slot.engine ?: continue
            if (destroyHandle(handle)) {
                // Only drop the reference once the lease is actually released; a still-leaked lease
                // stays recoverable rather than being made permanently unreachable.
                slot.engine = null
            }
        }
        executor?.shutdownNow()
        executor = null
    }

    /**
     * Catalog for the production engine factory. Null until a preparation request has actually
     * created the storage controller; the factory then produces no engine at all and the strip
     * stays GONE, which is the intended fail-closed behaviour rather than an error.
     */
    fun engineCatalog(subtypeId: String): PublishedDictionaryCatalog? =
        slots[subtypeId]?.preparation?.catalog()

    /**
     * Test seam: drives the exact dictionary-ready path the production prepare callback drives
     * (post [onDictionaryReady] onto the injected [uiPoster]). Lets a test start not-ready and fire
     * readiness deterministically without a real storage controller.
     */
    internal fun signalDictionaryReadyForTest() {
        val slot = activeSlot() ?: slotFor(DEFAULT_LANGUAGE)
        uiPoster.post { onDictionaryReady(slot) }
    }

    /**
     * Handles the dictionary becoming ready. Always runs on the UI owner. A late notification that
     * arrives after [onDestroy] or [onFinishInput] starts nothing and requests nothing.
     */
    private fun onDictionaryReady(slot: LanguageSlot) {
        if (destroyed) return
        slot.dictionaryReady = true
        // A dictionary that finished inflating for a language the user has already switched away
        // from is remembered, not acted on: its engine starts the moment that language is active
        // again.
        if (slot === activeSlot()) maybeStartEngine()
    }

    /**
     * Requests background preparation at most once per enable cycle, creating the background
     * executor and the storage controller on the way if they do not exist yet. Called from every
     * path that can make suggestions wanted; the flag, not the callers, is what keeps the count at
     * one.
     */
    private fun requestPreparationIfNeeded(explicitEnable: Boolean = false) {
        if (destroyed) return
        val slot = activeSlot() ?: return
        if (slot.preparationRequested) return
        // A dictionary that is already published is never prepared again: readiness outlives every
        // later transition of the setting.
        if (slot.dictionaryReady) return
        val active = dictionaryPreparation(slot) ?: return
        slot.preparationRequested = true
        slot.preparationRequestedByExplicitEnable = explicitEnable
        active.prepare { result ->
            // The callback runs on the background executor. Marshal onto the serialized UI owner
            // before touching any controller state.
            uiPoster.post { onPreparationResult(slot, result) }
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
    private fun onPreparationResult(slot: LanguageSlot, result: PreparationResult) {
        if (destroyed) return
        when (result) {
            is PreparationResult.Published -> {
                slot.lastPreparationUnavailable = false
                // Start the engine (and look up whatever is already typed) so a field opened before
                // the dictionary finished preparing still gets suggestions this session.
                onDictionaryReady(slot)
            }
            is PreparationResult.Unavailable -> {
                slot.lastPreparationUnavailable = true
                if (slot.preparationRequestedByExplicitEnable) {
                    dictionaryUnavailableListener?.onDictionaryUnavailableAfterExplicitEnable()
                }
            }
        }
    }

    /** Lazily built storage seam; null means fail-closed, with no dictionary and no engine. */
    private fun dictionaryPreparation(slot: LanguageSlot): DictionaryPreparation? {
        slot.preparation?.let { return it }
        val backgroundExecutor = backgroundExecutor() ?: return null
        val created = try {
            preparationFactory(backgroundExecutor, slot.subtypeId)
        } catch (_: Throwable) {
            null
        } ?: return null
        slot.preparation = created
        return created
    }

    /** E5c two-stage readiness: [dictionaryPreparation]'s exact shape, for the bigram table. */
    private fun bigramPreparationSeam(slot: LanguageSlot): BigramPreparation? {
        slot.bigramPreparation?.let { return it }
        val backgroundExecutor = backgroundExecutor() ?: return null
        val created = try {
            bigramPreparationFactory(backgroundExecutor, slot.subtypeId)
        } catch (_: Throwable) {
            null
        } ?: return null
        slot.bigramPreparation = created
        return created
    }

    /**
     * PROPOSALS.md, "E5c. Готовность вычислителя двухступенчатая": called from [publishEngine]
     * strictly AFTER the dictionary engine has already been assigned, reserved and (if a prefix
     * was already typed) looked up — never on the path that gets there. Preparing and attaching
     * the bigram table both happen on the background executor and touch no UI-visible state of
     * their own: unlike [onDictionaryReady], there is nothing here for the strip to reflect —
     * [CompositePrefixComputer.predict] simply starts answering once attached, and a NEXT_WORD
     * lookup is only ever triggered by E5d's own context-driven request, not by this completing.
     * A missing, corrupted, or not-yet-published table leaves [handle] answering NEXT_WORD with
     * an empty list, exactly like before this ran — no failure path reaches the UI thread.
     */
    private fun maybeAttachBigramSource(slot: LanguageSlot, handle: EngineHandle) {
        val preparation = bigramPreparationSeam(slot) ?: return
        try {
            preparation.prepare { result ->
                // Runs on the background executor, exactly like requestPreparationIfNeeded's own
                // callback — attachBigramSource performs the same class of blocking I/O and must
                // stay off the UI thread, so this is NOT re-marshaled through uiPoster.
                if (result is BigramPreparationResult.Published) {
                    handle.attachBigramSource(preparation.catalog())
                }
            }
        } catch (_: Throwable) {
            // Best-effort: NEXT_WORD simply keeps answering empty, exactly like a corrupted or
            // missing table would.
        }
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
        if (!eligible) return
        val slot = activeSlot() ?: return
        if (slot.engine != null || slot.starting || !slot.dictionaryReady) return
        // A lease that has not been released yet still belongs to this controller: never map a
        // second dictionary on top of it. The retry happens at the next lifecycle boundary.
        if (slot.releasePending) return
        val backgroundExecutor = backgroundExecutor() ?: return
        slot.starting = true
        val callback = ResultCallback { token, suggestions, kind ->
            uiPoster.post { applyResult(slot, token, suggestions, kind) }
        }
        val factory = engineFactory
        try {
            backgroundExecutor.execute {
                val handle = try {
                    factory(slot.subtypeId, callback)
                } catch (_: Throwable) {
                    null
                }
                uiPoster.post { publishEngine(slot, handle) }
            }
        } catch (_: Throwable) {
            slot.starting = false
        }
    }

    private fun publishEngine(slot: LanguageSlot, handle: EngineHandle?) {
        slot.starting = false
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
            if (slot !== activeSlot()) return
            displayedPrefix = null
            displayedContextWord = null
            if (eligible) {
                strip.hideSuggestions()
            }
            return
        }
        slot.engine = handle
        // E5c two-stage readiness: started AFTER the engine is already assigned, never before —
        // this is what makes it true that the bigram table cannot delay publication. Kicked off
        // regardless of `eligible` below: the engine stays warm across an ineligible editor, and
        // attaching costs nothing the UI can observe either way.
        maybeAttachBigramSource(slot, handle)
        if (slot !== activeSlot()) {
            // The user switched language while this engine was starting. Keep it — warm and idle —
            // for the moment they switch back, and leave the strip to whatever the language they
            // are actually typing in is doing. Its key-neighbor table is pushed when it becomes
            // active, because the live layout is the other language's right now.
            handle.finishInput()
            return
        }
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
        // E5d: see the comment on the identical gate in onStartInput().
        if (editor.hasKnownCursor()) {
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
        for (slot in slots.values) {
            if (!slot.releasePending) continue
            val handle = slot.engine
            if (handle == null) {
                // Nothing was ever started, or it is already gone: the request is satisfied.
                slot.releasePending = false
                slot.releaseAttemptFailed = false
                continue
            }
            if (destroyHandle(handle)) {
                slot.engine = null
                slot.releasePending = false
                slot.releaseAttemptFailed = false
            } else {
                // Remember the refusal: from here on the setting coming back on no longer cancels
                // this release, because the handle it would keep can no longer serve a lookup.
                slot.releaseAttemptFailed = true
            }
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
    private fun usableEngine(): EngineHandle? {
        val slot = activeSlot() ?: return null
        return if (slot.releasePending) null else slot.engine
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
     * It is also where the frozen text contract's "0 results" states are enforced, all BEFORE the
     * engine is asked anything: a cursor sitting inside a word (shared by both PREFIX and NEXT_WORD),
     * and a prefix in mixed capitalization (PREFIX only — NEXT_WORD does not check the context word's
     * casing at all, PROPOSALS.md, "Контракт текста" amendment, "Регистр предсказаний").
     *
     * E5d: an EMPTY prefix no longer unconditionally clears the band. It falls through to
     * [requestNextWordContext], which is where NEXT_WORD's own "0 results" state (no context word
     * available) is enforced — "Сосуществование" in the same amendment: a non-empty prefix always
     * means PREFIX-only, an empty one means NEXT_WORD-only or nothing, never both in the same band.
     */
    private fun requestCurrentPrefix() {
        if (!eligible) return
        val activeEngine = usableEngine()
        if (activeEngine == null) {
            // Text events can arrive while preparation/start is still in flight. Do not expose the
            // band until publishEngine() establishes that the dictionary is actually available.
            displayedPrefix = null
            displayedContextWord = null
            strip.hideSuggestions()
            return
        }
        if (!editor.hasKnownCursor()) {
            clearToReservedBand()
            return
        }
        // Cursor inside a word: the contract clears the results instead of offering a replacement
        // that would be spliced into the middle of the user's text ("ки|тап" + "т" must not become
        // "китапларtап"). Checked before either path below so the engine is never even asked, and
        // checked ONCE — moved ahead of the prefix/context branch below (it used to run only on the
        // PREFIX path) because "Контракт текста" amendment пункт 2 requires it to gate NEXT_WORD too:
        // "При selection или букве... сразу после курсора правило действует без изменений — NEXT_WORD
        // запрос не строится вообще". Neither check depended on the other's outcome, so this reorders
        // without changing PREFIX behaviour at all.
        if (editor.hasLetterAfterCursor()) {
            clearToReservedBand()
            return
        }
        val word = editor.cachedWordBeforeCursor()
        if (word.isEmpty()) {
            requestNextWordContext(activeEngine)
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
        // A non-empty prefix is unconditionally PREFIX mode: drop whatever NEXT_WORD state might
        // still be bound from a moment ago, so the two kinds never coexist in the band.
        displayedContextWord = null
        pendingPrefix = word
        requestSessionId = sessionId
        val prefixBytes = TatarWordUtils.toLookupBytes(TatarWordUtils.normalizeForLookup(word))
        val token = activeEngine.request(sessionId, activeLanguage ?: return, prefixBytes)
        if (token == null) {
            clearToReservedBand()
        }
    }

    /**
     * E5d NEXT_WORD request path, the sibling [requestCurrentPrefix] falls through to on an empty
     * prefix. Mirrors its PREFIX counterpart's shape exactly (change detection, session stamping,
     * clear-on-null-token) but has no casing gate — "Контракт текста" amendment, "Регистр
     * предсказаний": a mixed-case context word does not suppress a prediction, because nothing about
     * its casing is ever carried into the shown/inserted form.
     */
    private fun requestNextWordContext(activeEngine: EngineHandle) {
        val context = editor.cachedNextWordContext()
        if (context.isEmpty()) {
            clearToReservedBand()
            return
        }
        if (context != displayedContextWord) {
            displayedContextWord = null
        }
        // A NEXT_WORD request is unconditionally not PREFIX mode: drop whatever prefix candidates
        // might still be bound (there should not be any, since this path only runs on an empty
        // prefix, but the invariant is enforced here rather than assumed).
        displayedPrefix = null
        pendingContextWord = context
        requestSessionId = sessionId
        val contextBytes = TatarWordUtils.toLookupBytes(TatarWordUtils.normalizeForLookup(context))
        val token = activeEngine.requestNextWord(sessionId, activeLanguage ?: return, contextBytes)
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
        displayedContextWord = null
        requestSessionId = NO_SESSION
        strip.reserve()
    }

    /**
     * The clean-run machine of E4c, computed from the hooks that already exist — no new IPC, no new
     * editor call and nothing kept about the text beyond the current word.
     *
     * A run is CLEAN while the trailing word grows one piece at a time (`w.startsWith(previous) &&
     * w.length > previous.length`) and it ENDS when the trailing word becomes empty. A shortening
     * (backspace), a replacement, a selection change, a cursor gesture, an accepted suggestion, a
     * field or subtype change all mark it dirty, and a dirty run reports nothing.
     */
    private fun trackCleanRun(word: String) {
        val previous = runWord
        if (word == previous) return
        if (word.isEmpty()) {
            reportCompletionIfClean(previous)
            runWord = ""
            runClean = true
            runEmptyResultPrefixLength = NO_EMPTY_RESULT
            return
        }
        if (previous.isEmpty()) {
            // A fresh word begins; whether it stays clean is decided by what follows.
            runWord = word
            runEmptyResultPrefixLength = NO_EMPTY_RESULT
            return
        }
        if (word.startsWith(previous) && word.length > previous.length) {
            runWord = word
            return
        }
        // Anything else — backspace, a swipe-delete, a replacement — is not growth.
        runWord = word
        runClean = false
        runEmptyResultPrefixLength = NO_EMPTY_RESULT
    }

    /**
     * Reports [word] as cleanly completed, but only when the run also proved the word is NOT in the
     * shipped dictionary: some PROPER prefix of it, actually requested during this same run, came
     * back with an empty result. An empty result for p means no dictionary word other than p itself
     * begins with p, so a longer word starting with p cannot be in the dictionary either.
     *
     * If no such observation was made — coalescing collapsed the requests, the engine was not ready,
     * the band was ineligible — nothing is reported. Fail-closed towards writing LESS.
     */
    private fun reportCompletionIfClean(word: String) {
        if (!runClean || word.isEmpty()) return
        val observed = runEmptyResultPrefixLength
        if (observed !in 1 until word.length) return
        completionSink.onCleanCompletion(word)
    }

    private fun markRunDirty() {
        runWord = ""
        runClean = false
        runEmptyResultPrefixLength = NO_EMPTY_RESULT
    }

    private fun applyResult(
        slot: LanguageSlot,
        token: Any,
        suggestions: List<String>,
        kind: LookupKind,
    ) {
        if (!eligible) return
        // A result computed by the engine of a language the user has left may never repaint the
        // band. The session check below already covers it (every language change bumps the session),
        // and the engine's own token carries the dictionary identity, but the owner of the state
        // says so itself rather than relying on either.
        if (slot !== activeSlot()) return
        if (sessionId != requestSessionId) return
        val activeEngine = usableEngine() ?: return
        if (!activeEngine.isCurrent(token)) return
        when (kind) {
            LookupKind.PREFIX -> applyPrefixResult(suggestions)
            LookupKind.NEXT_WORD -> applyNextWordResult(suggestions)
        }
    }

    private fun applyPrefixResult(suggestions: List<String>) {
        if (suggestions.isEmpty()) {
            // The observation the E4c filter is built on: nothing in the dictionary continues this
            // prefix, so no longer word starting with it can be in the dictionary either.
            //
            // The SHORTEST such prefix is remembered, not the longest. The contract asks for a
            // PROPER prefix of the completed word, and the last empty result of a run is usually the
            // whole word itself — keeping the longest would let that one overwrite the very evidence
            // the rule is about, and nothing would ever be learned.
            if (runClean && pendingPrefix.isNotEmpty()) {
                val length = pendingPrefix.length
                if (runEmptyResultPrefixLength == NO_EMPTY_RESULT ||
                    length < runEmptyResultPrefixLength
                ) {
                    runEmptyResultPrefixLength = length
                }
            }
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

    /**
     * E5d NEXT_WORD counterpart of [applyPrefixResult]. No E4c learning (that filter is about
     * PROPER prefixes of a growing word; NEXT_WORD only ever fires on an empty prefix, so there is no
     * prefix growth to observe) and no casing re-application — "Контракт текста" amendment, "Регистр
     * предсказаний": predictions are shown and inserted exactly as the bigram table stores them.
     */
    private fun applyNextWordResult(suggestions: List<String>) {
        if (suggestions.isEmpty()) {
            displayedContextWord = null
            strip.reserve()
            return
        }
        displayedContextWord = pendingContextWord
        displayedSessionId = sessionId
        strip.showSuggestions(
            suggestions[0],
            suggestions.getOrNull(1),
            suggestions.getOrNull(2),
        )
    }

    /**
     * A word separator has been pressed and is ABOUT to be committed: the last chance to correct the
     * word it finishes (D3). Returns true when the trailing word was actually replaced.
     *
     * Called before the separator reaches the input logic on purpose. At this instant the editor is
     * in exactly the state an accepted suggestion needs — a trailing word, a collapsed cursor right
     * after it — so the replacement is the same single delete + commit, with the same re-checks, and
     * the separator afterwards travels the ordinary path untouched (auto-space, the double-space
     * gesture and the shift update all behave as they always did).
     *
     * Every condition is checked here, and every one of them fails towards NOT editing text:
     *  - the feature is on (and subordinate to suggestions: [eligible] already carries that);
     *  - an engine is usable and the cursor is known;
     *  - the cursor is not inside a word, and the word is not in mixed case (which the frozen
     *    contract gives 0 results for, so it has no defined replacement form either);
     *  - the word is long enough ([AutocorrectPolicy.MIN_WORD_CODE_POINTS], on the normalized form);
     *  - a verdict exists AND was computed for THIS word — a coalesced or never-answered lookup
     *    leaves an older verdict behind, and applying it to a different word is exactly the failure
     *    this comparison exists to prevent;
     *  - the candidate is frequent enough ([AutocorrectPolicy.MIN_CANDIDATE_FREQUENCY]).
     *
     * The last two are re-checked here although the engine already applied them, for the same reason
     * the tap path re-checks the prefix inside the editor: one side of a two-sided decision must not
     * be the only place a rule lives.
     */
    fun maybeAutocorrectBeforeSeparator(separatorCodePoint: Int): Boolean {
        if (destroyed || !eligible) return false
        if (!autocorrectGate.isOn()) return false
        val activeEngine = usableEngine() ?: return false
        if (!editor.hasKnownCursor()) return false
        if (editor.hasLetterAfterCursor()) return false
        val word = editor.cachedWordBeforeCursor()
        if (word.isEmpty()) return false
        val casing = TatarWordUtils.classifyCasing(word)
        if (casing == TatarWordUtils.PrefixCasing.MIXED) return false
        val normalized = TatarWordUtils.normalizeForLookup(word)
        if (normalized.codePointCount(0, normalized.length) <
            AutocorrectPolicy.MIN_WORD_CODE_POINTS
        ) {
            return false
        }
        val advice = activeEngine.autocorrectAdvice() ?: return false
        if (advice.typedWord != normalized) return false
        if (advice.frequency < AutocorrectPolicy.MIN_CANDIDATE_FREQUENCY) return false
        // The user's capitalization is re-applied exactly as it is to a shown candidate, so the
        // replacement is the word they would have got by tapping it.
        val replacement = TatarWordUtils.applyCasing(advice.replacement, casing)
        if (replacement == word) return false
        if (!editor.replaceTypedWord(word, replacement)) return false
        // A correction is not the user spelling the word out: the run stops counting, exactly as it
        // does for an accepted suggestion, so the replaced word reaches neither the pending set nor
        // the personal dictionary.
        markRunDirty()
        // Whatever the band was showing described the word that no longer stands there.
        displayedPrefix = null
        displayedContextWord = null
        armedReplacement = Replacement(
            word, replacement, separatorString(separatorCodePoint), sessionId,
        )
        revertable = null
        return true
    }

    /**
     * A backspace has been pressed. Returns true when it was consumed by undoing the replacement
     * made immediately before it, in which case no character is deleted; false leaves the key to the
     * ordinary backspace path.
     *
     * There is exactly ONE undo. The state is dropped BEFORE the editor is asked to do anything, so
     * a refused undo cannot be retried and the second backspace deletes a character like any other.
     */
    fun maybeRevertAutocorrect(): Boolean {
        val replacement = revertable ?: return false
        revertable = null
        armedReplacement = null
        if (destroyed || !eligible) return false
        if (replacement.sessionId != sessionId) return false
        if (!autocorrectGate.isOn()) return false
        if (!editor.hasKnownCursor()) return false
        markRunDirty()
        return editor.revertTypedWord(
            replacement.insertedForm, replacement.separator, replacement.typedForm,
        )
    }

    /**
     * Moves the undo window forward by one text change.
     *
     * A replacement is armed while its own separator is still on its way to the editor; that one
     * change completes it. Any other text change — a typed character, an accepted suggestion, a
     * deletion — closes the window instead, which is what makes «любое другое событие делает revert
     * невозможным» true for the two of the six events that arrive as text.
     */
    private fun advanceRevertWindow() {
        val armed = armedReplacement
        if (armed != null) {
            armedReplacement = null
            revertable = if (armed.sessionId == sessionId) armed else null
            return
        }
        revertable = null
    }

    /** Drops the undo window outright: a field, subtype, selection or setting boundary. */
    private fun clearRevertState() {
        armedReplacement = null
        revertable = null
    }

    private fun separatorString(codePoint: Int): String =
        if (Character.isValidCodePoint(codePoint)) String(Character.toChars(codePoint)) else ""

    private fun onTap(suggestion: String) {
        // An accepted suggestion is not the user spelling the word out: the run stops counting.
        markRunDirty()
        // A tap is one of the six events that close the undo window.
        clearRevertState()
        if (displayedSessionId != sessionId) {
            // Nothing bound to the current session is displayed (e.g. the text changed and the old
            // candidates were invalidated): a tap must be a no-op and must never commit.
            return
        }
        // Exactly one of these is ever non-null (PROPOSALS.md, "Контракт текста" amendment,
        // "Сосуществование") — the owner of state reads the kind off what is actually bound, not off
        // the tapped string's content, which is the same rule E5c's engine-level guarantee exists
        // for, one layer up.
        val prefix = displayedPrefix
        if (prefix != null) {
            // Commit against the DISPLAYED prefix, not the mutable pendingPrefix. The editor's own
            // stale-tap guard (re-reads live cache, requires collapsed selection and live trailing
            // word == expectedPrefix, deletes by code points) is the second line of defense.
            if (editor.commitSuggestion(prefix, suggestion)) {
                // The field is still eligible after a commit; clear the words but keep the reserved
                // band so accepting a suggestion does not resize the keyboard.
                displayedPrefix = null
                strip.reserve()
            }
            return
        }
        val context = displayedContextWord
        if (context != null) {
            // Same second line of defense as the PREFIX path, through the E5d commit path instead:
            // the editor re-derives the live context word and refuses a stale tap itself.
            if (editor.commitPredictedWord(context, suggestion)) {
                displayedContextWord = null
                strip.reserve()
            }
        }
    }

    companion object {
        /**
         * The language a call that names none means.
         *
         * The app shipped monolingual for five releases and its whole test suite drives the
         * controller through the boolean-only overloads; those mean the language that used to be
         * the only one. Reads the single source of truth (`PersonalSubtypes.TATAR_RU`) so the
         * request key can never drift from `LatinIME.isSuggestionsEligible()`, which reads the same
         * constant.
         */
        internal const val DEFAULT_LANGUAGE = PersonalSubtypes.TATAR_RU
        private const val DESTROY_TIMEOUT_MS = 60L

        // Sentinel for "no request is outstanding". [sessionId] starts at 0 and only ever grows,
        // so this can never be mistaken for a live generation.
        private const val NO_SESSION = -1L
        /** No proper prefix of the current run has come back empty yet. */
        private const val NO_EMPTY_RESULT = -1
    }
}
