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

import java.text.Normalizer

/**
 * Pure text helpers shared by the Tatar suggestion controller and its editor surface.
 *
 * These functions never touch the InputConnection, never log text, and are deterministic so they
 * can be exercised by plain JVM unit tests. The normalization here MUST stay byte-for-byte
 * compatible with how the packed dictionary asset stores its words (see
 * scripts/dictionary_coverage.py::normalize_word and scripts/dictionary_pack.py). The asset words
 * are Unicode NFC then lower-cased; the on-disk index expects the caller to hand it pre-normalized
 * UTF-8 bytes ([TatarWordUtils.toLookupBytes]).
 */
object TatarWordUtils {

    /**
     * Returns the maximal trailing run of [Character.isLetter] characters in [textBeforeCursor].
     *
     * Returns "" when the input is null/empty or when the final character is not a letter. Only
     * BMP letters occur in Tatar Cyrillic, Latin, and Russian Cyrillic text, so char-based
     * classification is sufficient and matches the frozen contract.
     */
    @JvmStatic
    fun extractTrailingWord(textBeforeCursor: CharSequence?): String {
        if (textBeforeCursor == null) return ""
        val length = textBeforeCursor.length
        if (length == 0) return ""
        var start = length
        while (start > 0 && Character.isLetter(textBeforeCursor[start - 1])) {
            start--
        }
        if (start == length) return ""
        return textBeforeCursor.subSequence(start, length).toString()
    }

    /**
     * Normalizes [word] to the exact form used by the dictionary asset: Unicode NFC followed by
     * invariant-locale lower casing. This mirrors the Python pipeline
     * `unicodedata.normalize("NFC", word).lower()`. [String.lowercase] uses the invariant locale,
     * which matches Python's locale-independent `str.lower()` for Cyrillic and Tatar-specific
     * letters.
     */
    @JvmStatic
    fun normalizeForLookup(word: String): String =
        Normalizer.normalize(word, Normalizer.Form.NFC).lowercase()

    /** Encodes an already-normalized lookup key as UTF-8, the byte form the index expects. */
    @JvmStatic
    fun toLookupBytes(normalized: String): ByteArray = normalized.toByteArray(Charsets.UTF_8)
}
