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

import rkr.simplekeyboard.inputmethod.latin.common.Constants

/**
 * Everything the one-shot offer needs to know about the live IME, behind a seam so the decision
 * logic can be exercised by plain JVM tests (the two dialogs themselves are Android and are covered
 * by a source-contract test instead). Every method is called on the UI thread.
 */
interface OfferEnvironment {
    /**
     * The `pref_tatar_suggestions` value, read fresh rather than taken from `SettingsValues`: the
     * offer exists only for users who have suggestions off, and the message of [E1b-8] is cancelled
     * by the user turning them back off.
     */
    fun isSuggestionsSettingEnabled(): Boolean

    /** The active subtype is the Tatar one. */
    fun isTatarSubtypeActive(): Boolean

    /**
     * The input view is shown AND its window token is non-null. Both halves are one condition
     * because a dialog attached to the IME window cannot exist without a token.
     */
    fun isInputViewShownWithWindowToken(): Boolean

    /**
     * Whether this editor may be offered suggestions at all:
     * `mInputAttributes.mShouldShowSuggestions` (false in password, `NO_SUGGESTIONS`, e-mail and URI
     * fields) AND NOT `mInputAttributes.mNoPersonalizedLearning`
     * (`EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING`, set by incognito browser tabs and by the
     * "incognito keyboard" switch of messengers — those fields carry an ordinary text inputType, so
     * the first half alone lets them through).
     *
     * Checked before any text is read, so such a field is never even looked at and the one-shot flag
     * is never spent on one.
     */
    fun editorAllowsSuggestions(): Boolean

    /** False before the user has unlocked the device (direct boot). */
    fun isUserUnlocked(): Boolean

    /** Another dialog (the subtype picker, or one of these two) is already on screen. */
    fun isAnotherDialogShowing(): Boolean

    /** The IME is suppressed because a hardware keyboard is attached. */
    fun isImeSuppressedByHardwareKeyboard(): Boolean

    /** A finger is dragging on the keyboard: a window must not pop up under a held finger. */
    fun isInDraggingFinger(): Boolean

    /**
     * The editor's cached text before the cursor. Read only after [editorAllowsSuggestions] has
     * returned true, and never logged.
     */
    fun cachedTextBeforeCursor(): CharSequence?
}

/**
 * The one-shot "offer spent" flag in device-protected preferences.
 *
 * One global key rather than one per language: a second language does not earn a second offer.
 */
interface OfferFlagStore {
    fun isOfferSpent(): Boolean

    /** Marks the offer spent durably. Called exactly once, before the dialog is shown. */
    fun spendOffer()
}

/** Shows the two modal dialogs over the IME window. */
interface OfferPresenter {
    /** The offer to turn Tatar suggestions on: two buttons, dismissible. */
    fun showEnableOffer()

    /** The one-shot "could not turn suggestions on" message: one dismiss button. */
    fun showUnavailableMessage()
}

/**
 * Decides whether and when the user is offered Tatar suggestions, and whether the one-shot
 * "could not be turned on" message is shown.
 *
 * Two independent one-shot events live here:
 * - the **offer** (E1b): shown at most once per installation, after the user commits a first word of
 *   at least [MIN_WORD_LETTERS] letters with the Tatar subtype active. The flag that spends it is
 *   durable and is written BEFORE the dialog appears, so a rotation, a crash, a process death or a
 *   reboot can never bring the offer back. The chosen failure direction is explicit: showing it zero
 *   times is acceptable, showing it twice is not;
 * - the **unavailable message** (E1b-8): shown at most once per process, and only for a dictionary
 *   preparation that an explicit enable asked for. It is deferred rather than dropped when the
 *   surroundings are wrong, and the surroundings are re-checked in the very method that shows it.
 *
 * All methods are called on the UI thread.
 */
class SuggestionsOfferController(
    private val environment: OfferEnvironment,
    private val flags: OfferFlagStore,
    private val presenter: OfferPresenter,
) {
    /**
     * Mirror of the durable flag, read once at construction. The per-keystroke check then costs one
     * field read instead of a preferences lookup, which is the whole reason the mirror exists.
     */
    private var offerSpent: Boolean = flags.isOfferSpent()

    // Deferred E1b-8 message. Neither field survives the process, and that is exactly the declared
    // lifetime: another preparation is only possible after another observed OFF -> ON transition, so
    // a durable flag would buy nothing.
    private var unavailableMessagePending: Boolean = false
    private var unavailableMessageShown: Boolean = false

    /**
     * True while the offer can still be shown at all. The keystroke path reads this first so that,
     * once the offer is spent, a keypress costs one field read and nothing else — no text read, no
     * preferences read, no separator classification.
     */
    fun isOfferPending(): Boolean = !offerSpent

    /**
     * A key press has just been committed to the editor.
     *
     * The only trigger of the offer, and only for a key press that actually finished a word — see
     * [isWordFinishingKeyPress] for why being a word separator is not sufficient on its own.
     * Showing the keyboard is deliberately NOT a trigger: a user who opens the keyboard and types
     * nothing has shown no intent to type Tatar.
     *
     * Nothing is remembered between fields or sessions: a word left unfinished is simply recounted
     * from the editor cache at the next separator.
     *
     * @param codePoint the committed code point, or `Event.NOT_A_CODE_POINT` for a functional key.
     * @param isWordSeparator the editor's own classification of [codePoint].
     */
    fun onKeyPressCommitted(codePoint: Int, isWordSeparator: Boolean) {
        if (offerSpent) return
        if (!isWordFinishingKeyPress(codePoint, isWordSeparator)) return
        // The offer is for users who do not have the feature; anyone who already turned it on knows.
        if (environment.isSuggestionsSettingEnabled()) return
        if (!isEnvironmentReady()) return
        // Read the text last, so a field that forbids suggestions is never read at all.
        if (
            !TatarWordUtils.endsWithWordOfAtLeast(
                environment.cachedTextBeforeCursor(),
                MIN_WORD_LETTERS,
            )
        ) {
            return
        }
        // Spend the flag on the decision to show, not on the answer: a crash or a rotation between
        // the write and the answer must not resurrect the offer.
        offerSpent = true
        flags.spendOffer()
        presenter.showEnableOffer()
    }

    /**
     * A dictionary preparation that an explicit enable requested ended `Unavailable`.
     *
     * At most one message per preparation attempt and at most one per process. The message is shown
     * right away when the surroundings allow it, and deferred to the next input-view boundary
     * otherwise; it is never turned into a silent failure.
     */
    fun onDictionaryUnavailableAfterExplicitEnable() {
        if (unavailableMessageShown) return
        unavailableMessagePending = true
        showUnavailableMessageIfPossible()
    }

    /** An input view has started: the boundary at which a deferred message gets another chance. */
    fun onInputViewStarted() {
        showUnavailableMessageIfPossible()
    }

    /**
     * An ON -> OFF transition of the setting was observed.
     *
     * This is the one condition that CANCELS a deferred message instead of deferring it further: the
     * user has withdrawn the very attempt the message reports on, so telling them it failed would be
     * answering a question they took back. A later enable starts a fresh attempt with its own
     * message.
     */
    fun onSuggestionsSettingDisabled() {
        unavailableMessagePending = false
    }

    /**
     * Shows the deferred message if it may be shown now.
     *
     * The conditions are evaluated HERE, in the same method that calls [OfferPresenter], and not
     * only where the message was queued: between the preparation result and this boundary the user
     * may have moved into a password field, rotated the screen, opened the subtype picker or turned
     * the setting back off, and a window popping up in the wrong field costs more than a message
     * that never appeared.
     */
    private fun showUnavailableMessageIfPossible() {
        if (!unavailableMessagePending) return
        if (!environment.isSuggestionsSettingEnabled()) {
            // The cancelling condition, mirroring the first condition of the offer.
            unavailableMessagePending = false
            return
        }
        // Any of the seven environment conditions only defers: the message stays pending.
        if (!isEnvironmentReady()) return
        unavailableMessagePending = false
        unavailableMessageShown = true
        presenter.showUnavailableMessage()
    }

    /**
     * The seven conditions that describe the surroundings of a modal dialog over the IME window,
     * shared verbatim by the offer and by the message. The two conditions that belong to the offer
     * alone (the setting being off and the flag being unspent) are checked by their own callers.
     */
    private fun isEnvironmentReady(): Boolean =
        environment.isTatarSubtypeActive() &&
            environment.isInputViewShownWithWindowToken() &&
            environment.editorAllowsSuggestions() &&
            environment.isUserUnlocked() &&
            !environment.isAnotherDialogShowing() &&
            !environment.isImeSuppressedByHardwareKeyboard() &&
            !environment.isInDraggingFinger()

    companion object {
        /**
         * How many letters a finished word must have before the offer is worth making. Matches the
         * minimum prefix length the suggestions themselves need: offering a feature earlier than it
         * would show anything is pointless.
         */
        const val MIN_WORD_LETTERS = 3

        /**
         * True when this key press finished a word, which is the only moment the offer may be made.
         *
         * [isWordSeparator] is the editor's own classification of [codePoint] and is necessary but
         * NOT sufficient. Enter and Tab are ordinary positive code points — [Constants.CODE_ENTER]
         * is `'\n'`, [Constants.CODE_TAB] is `'\t'` — and `symbols_word_separators` lists both, so
         * classification alone would call "send this message" a finished word. That is the worst
         * possible moment for a one-shot offer: the host app answers the editor action by hiding
         * the keyboard, `hideWindow()` dismisses the dialog in the same frame, and the flag, spent
         * before the dialog was shown, never comes back. The genuinely functional keys need no case
         * of their own: delete and the language key carry `Event.NOT_A_CODE_POINT`, which is never
         * a separator.
         */
        @JvmStatic
        fun isWordFinishingKeyPress(codePoint: Int, isWordSeparator: Boolean): Boolean =
            isWordSeparator &&
                codePoint != Constants.CODE_ENTER &&
                codePoint != Constants.CODE_TAB
    }
}
