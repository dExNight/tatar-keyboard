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

package rkr.simplekeyboard.inputmethod.latin.dictionary.personal

/**
 * The single source of truth for the active-subtype identifier and its per-subtype alphabet.
 *
 * Ownership of the "boolean eligible -> active subtype identifier" seam moved to E4a-1 after phase
 * D2 was cancelled (see PROPOSALS.md, "Сквозное решение: всё новое ключуется активным subtype",
 * amendment of 2026-07-27). Everything the personal dictionary introduces is keyed by a subtype
 * identifier from the very first version — the on-disk path, the file name, the subtypeTag inside
 * the file, the in-memory snapshot and the alphabet filter — and there is no shared "default"
 * store. For a subtype with no declared alphabet the feature is off entirely.
 *
 * The literal `"tt_RU"` used to live twice, in `SuggestionsController.SUBTYPE_ID` and in
 * `LatinIME.isTatarSuggestionsEligible()`, kept in sync by hand. Both now read [TATAR_RU], so the
 * eligibility signal and the storage key can never drift apart. The app is monolingual and stays
 * so, but the format carries a language tag from version 1 anyway, so a later Zamanälif (Latin)
 * subtype never forces a migration of the words the user already saved.
 */
object PersonalSubtypes {
    /** The Tatar Cyrillic subtype identifier ([rkr.simplekeyboard.inputmethod.latin.Subtype.getLocale]). */
    const val TATAR_RU = "tt_RU"

    /**
     * The lowercase Tatar Cyrillic alphabet, identical to the code-point set enforced by
     * `TdictValidator.TATAR_ALPHABET` for the packed dictionary asset. Alphabet checks run on the
     * NORMALIZED (NFC lowercase) form of a word, so only lowercase letters are listed: the raw form
     * "Гүзәл" is accepted because its normalized form "гүзәл" is spelled entirely from this set.
     */
    private val TATAR_RU_ALPHABET: Set<Int> =
        "аәбвгдеёжҗзийклмнңоөпрстуүфхһцчшщъыьэюя".codePoints().toArray().toSet()

    /**
     * The alphabet for [subtypeId], or null when the subtype declares none. A null alphabet means
     * the personal dictionary is off for that subtype: there is no fallback to another language.
     */
    fun alphabetFor(subtypeId: String): Set<Int>? = when (subtypeId) {
        TATAR_RU -> TATAR_RU_ALPHABET
        else -> null
    }

    /** True when [subtypeId] declares an alphabet, i.e. the personal dictionary may run for it. */
    fun isSupported(subtypeId: String): Boolean = alphabetFor(subtypeId) != null
}
