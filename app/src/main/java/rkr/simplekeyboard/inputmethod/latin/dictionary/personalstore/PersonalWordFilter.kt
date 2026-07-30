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

import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.TpersFormat
import rkr.simplekeyboard.inputmethod.latin.suggestions.TatarWordUtils
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale

/**
 * Pure input filter for a word about to enter the personal dictionary. It applies exactly the
 * per-record checks that `TpersValidator` enforces when reading a `.tpers` file, so a word this
 * filter accepts round-trips through the writer and back through the validator without surprise.
 *
 * Filters and dedup run on the NORMALIZED (NFC lowercase) form; the ORIGINAL (as-entered) form is
 * what the store persists. All checks are content checks — no I/O, no logging, no user text in any
 * message (there are none here). Not a `data class`; there is nothing to carry.
 */
internal object PersonalWordFilter {
    /** The NFC lowercase form used for sorting, dedup and search. */
    fun normalize(word: String): String =
        Normalizer.normalize(word, Normalizer.Form.NFC).lowercase(Locale.ROOT)

    /**
     * Returns the normalized form of [rawWord] if it is eligible for the personal dictionary of a
     * subtype whose [alphabet] is given, or null otherwise. Rejects (fail-closed toward NOT
     * storing): MIXED casing of the raw form, a normalized length outside 3..24 code points, a
     * leftover combining mark after NFC, any code point outside the alphabet (which rules out
     * digits, Latin, `@`, dots, dashes and every other symbol), and a raw form that would not fit
     * the on-disk `wordByteLength` u8 field.
     */
    fun acceptedNormalizedForm(rawWord: String, alphabet: Set<Int>): String? {
        if (rawWord.isEmpty()) return null
        // RAW form: casing must not be MIXED (same rule as the validator's raw-form check).
        if (TatarWordUtils.classifyCasing(rawWord) == TatarWordUtils.PrefixCasing.MIXED) return null
        // The raw form is what goes on disk; its UTF-8 length must fit the u8 length field.
        if (rawWord.toByteArray(StandardCharsets.UTF_8).size > MAX_RAW_WORD_BYTES) return null

        val normalized = normalize(rawWord)
        val codePointCount = normalized.codePointCount(0, normalized.length)
        if (codePointCount < TpersFormat.MIN_WORD_CODE_POINTS ||
            codePointCount > TpersFormat.MAX_WORD_CODE_POINTS
        ) {
            return null
        }
        var offset = 0
        while (offset < normalized.length) {
            val codePoint = normalized.codePointAt(offset)
            if (isCombiningMark(codePoint)) return null
            if (codePoint !in alphabet) return null
            offset += Character.charCount(codePoint)
        }
        return normalized
    }

    private fun isCombiningMark(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt()
    }

    /** u8 length field: the raw form cannot exceed 255 UTF-8 bytes on disk. */
    private const val MAX_RAW_WORD_BYTES = 255
}
