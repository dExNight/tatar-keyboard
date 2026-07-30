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

package rkr.simplekeyboard.inputmethod.latin.settings

import android.content.Context
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.PersonalDictionary
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.PersonalSubtypes
import rkr.simplekeyboard.inputmethod.latin.dictionary.personalstore.PersonalDictionaries
import rkr.simplekeyboard.inputmethod.latin.dictionary.personalstore.PersonalWordFilter

/**
 * Everything the "Personal dictionary" screen does to the data, in one place away from the view
 * code: read the snapshots, add a word, remove a word, erase everything.
 *
 * Every mutation goes to the process-wide [PersonalDictionaries] owner, which turns it into an event
 * on the single personal-store worker — the settings screen performs no file I/O itself and holds no
 * second writer. Erasure additionally notifies the IME so a word that is gone stops being tappable
 * in the band that is open right now.
 */
internal class PersonalDictionaryScreenController(private val context: Context) {

    /**
     * The languages shown, in the order given, each with its current snapshot. Only subtypes that
     * can have a personal dictionary at all are listed: the feature is keyed by subtype, and a
     * language the dictionary does not support has nothing to show.
     */
    fun sections(subtypeIds: List<String>): List<Pair<String, PersonalDictionary>> =
        subtypeIds.filter { PersonalSubtypes.alphabetFor(it) != null }
            .map { it to PersonalDictionaries.snapshotFor(context, it) }

    /**
     * Adds one word typed by the user on the screen. Returns false when the word is not eligible —
     * the SAME content filter learning will use, so what the screen accepts and what typing would
     * save can never drift apart.
     */
    fun addWord(subtypeId: String, word: String): Boolean {
        val alphabet = PersonalSubtypes.alphabetFor(subtypeId) ?: return false
        val trimmed = word.trim()
        if (PersonalWordFilter.acceptedNormalizedForm(trimmed, alphabet) == null) return false
        PersonalDictionaries.storeFor(context, subtypeId).addManually(trimmed)
        return true
    }

    /** Removes one word. Erasure semantics: the band unbinds whatever it is showing. */
    fun removeWord(subtypeId: String, word: String) {
        PersonalDictionaries.storeFor(context, subtypeId).forget(word)
        PersonalDictionaries.notifyErased()
    }

    /** Erases the personal dictionaries of ALL languages, not only the one in view. */
    fun eraseAll(subtypeIds: List<String>) {
        for (subtypeId in subtypeIds) {
            if (PersonalSubtypes.alphabetFor(subtypeId) == null) continue
            PersonalDictionaries.storeFor(context, subtypeId).clearAll()
        }
        PersonalDictionaries.notifyErased()
    }
}
