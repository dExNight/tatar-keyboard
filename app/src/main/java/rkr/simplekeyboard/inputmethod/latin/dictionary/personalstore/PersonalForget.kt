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

package rkr.simplekeyboard.inputmethod.latin.dictionary.personalstore

import android.content.Context

/**
 * "Forget this word" (E4d): does the word shown in a band cell belong to the personal dictionary,
 * and if the user confirms, remove it.
 *
 * The lookup is by the NORMALIZED form and against the parallel array of normalized forms in the
 * published snapshot, never against the displayed string. Both halves matter: the snapshot stores
 * raw forms, and the displayed string has already been through `applyCasing` for an INITIAL_CAPS or
 * ALL_CAPS prefix — searching by what is on screen would silently fail to find the saved «Гүзәл»
 * whenever the user typed in capitals, and the long press would do nothing for no visible reason.
 */
object PersonalForget {

    /**
     * The saved spelling of [shownWord] if it is a personal entry, or null if the cell holds an
     * ordinary dictionary word — in which case a long press must do nothing at all.
     */
    @JvmStatic
    fun savedFormOf(context: Context, subtypeId: String, shownWord: String): String? {
        val normalized = PersonalWordFilter.normalize(shownWord)
        val snapshot = PersonalDictionaries.snapshotFor(context, subtypeId)
        val index = snapshot.indexOfNormalized(normalized)
        return if (index >= 0) snapshot.rawFormAt(index) else null
    }

    /** Removes the confirmed word and unbinds whatever the band is showing. */
    @JvmStatic
    fun confirmForget(context: Context, subtypeId: String, shownWord: String) {
        val normalized = PersonalWordFilter.normalize(shownWord)
        PersonalDictionaries.storeFor(context, subtypeId).forget(normalized)
        PersonalDictionaries.notifyErased()
    }
}
