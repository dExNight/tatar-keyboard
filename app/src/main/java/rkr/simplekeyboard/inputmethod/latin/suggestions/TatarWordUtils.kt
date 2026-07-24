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
     * Returns the maximal trailing run of word characters in [textBeforeCursor] as an EXACT span of
     * the raw text.
     *
     * A word character is a [Character.isLetter] letter or a combining mark bound to a preceding
     * base character. Marks have to continue the run because the frozen contract accepts
     * canonically decomposed (NFD) input: in NFD "й" is "и" + U+0306 and "ё" is "е" + U+0308, and
     * those marks are not letters, so a letters-only scan would cut the word short — or return ""
     * when the decomposed letter is the last thing typed. All three mark categories continue the
     * run: Mn covers the Cyrillic/Latin canonical decompositions we actually see, Mc appears in the
     * canonical decompositions of other scripts, and Me only ever decorates a preceding base. None
     * of them can stand alone, so none of them is a word boundary.
     *
     * The result stays a verbatim substring of [textBeforeCursor] and is never normalized here: the
     * commit path deletes exactly `prefix.length` raw characters before the cursor, so rewriting
     * the span would delete the wrong amount of text. NFC folding happens later, in
     * [normalizeForLookup].
     *
     * Returns "" when the input is null/empty, when the final character is not a word character, or
     * when the trailing run holds no letter at all (marks without a base letter are orphans, not a
     * word); leading orphan marks are trimmed off the span for the same reason. Only BMP letters
     * occur in Tatar Cyrillic, Latin, and Russian Cyrillic text, so char-based classification is
     * sufficient and matches the frozen contract.
     */
    @JvmStatic
    fun extractTrailingWord(textBeforeCursor: CharSequence?): String {
        if (textBeforeCursor == null) return ""
        val length = textBeforeCursor.length
        if (length == 0) return ""
        var start = length
        while (start > 0 && isWordCharacter(textBeforeCursor[start - 1])) {
            start--
        }
        // The scan stopped on a non-word character (or the start of the text), so marks sitting at
        // the head of the run have no base letter inside the run and are not part of the word.
        while (start < length && !Character.isLetter(textBeforeCursor[start])) {
            start++
        }
        if (start == length) return ""
        return textBeforeCursor.subSequence(start, length).toString()
    }

    /**
     * True when the text right after the cursor continues the word the cursor sits in, i.e. when
     * the cursor is INSIDE a word rather than at its end.
     *
     * The frozen contract clears the results when a Tatar letter follows the cursor, because
     * replacing the trailing word would then splice the suggestion into the middle of the user's
     * text. This test is deliberately wider than "Tatar letter": any [Character.isLetter] letter
     * (Latin and Russian included) and any combining mark counts, because being fail-closed can
     * only cost a suggestion, while being too narrow corrupts text. A leading combining mark means
     * the cursor is inside a canonically decomposed character, which is inside a word too.
     *
     * Reads the FIRST CODE POINT, not the first char, so a supplementary letter is classified
     * correctly instead of being seen as a lone (caseless, non-letter) surrogate.
     */
    @JvmStatic
    fun startsWithWordCharacter(textAfterCursor: CharSequence?): Boolean {
        if (textAfterCursor == null || textAfterCursor.isEmpty()) return false
        return isWordCharacter(Character.codePointAt(textAfterCursor, 0))
    }

    /** True for letters and for the non-standalone combining marks that attach to them. */
    private fun isWordCharacter(ch: Char): Boolean = isWordCharacter(ch.code)

    /** Code-point flavour of [isWordCharacter]; the char flavour delegates here. */
    private fun isWordCharacter(codePoint: Int): Boolean {
        if (Character.isLetter(codePoint)) return true
        val type = Character.getType(codePoint)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt()
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

    /**
     * The casing shape of the prefix the user actually typed, as classified by [classifyCasing].
     *
     * The dictionary stores NFC lowercase words only, so the shape is what re-applies the user's
     * capitalization to a candidate ([applyCasing]) — the displayed and the inserted form always
     * share it.
     */
    enum class PrefixCasing {
        /** No uppercase letter: candidates are shown exactly as the dictionary stores them. */
        LOWER,

        /** A single uppercase letter first (possibly the only letter), the rest lowercase. */
        INITIAL_CAPS,

        /** Two or more uppercase letters and no lowercase letter. */
        ALL_CAPS,

        /** Any other mix of cases; the frozen contract requires 0 results for it. */
        MIXED,
    }

    /**
     * Classifies the casing of the RAW (unnormalized) prefix per the frozen text contract:
     * all-lowercase -> [PrefixCasing.LOWER]; one uppercase letter, or a leading uppercase letter
     * followed only by lowercase ones -> [PrefixCasing.INITIAL_CAPS]; two or more uppercase letters
     * with no lowercase letter -> [PrefixCasing.ALL_CAPS]; anything else -> [PrefixCasing.MIXED],
     * for which the caller must publish 0 results.
     *
     * Caseless characters are ignored rather than treated as lowercase, so a decomposed (NFD)
     * prefix classifies the same as its composed form — the combining marks it carries are neither
     * upper- nor lowercase. An empty prefix is [PrefixCasing.LOWER]: nothing was capitalized, so
     * the safe default is to leave candidates untouched.
     */
    @JvmStatic
    fun classifyCasing(rawPrefix: String): PrefixCasing {
        var upperCount = 0
        var lowerCount = 0
        var firstCasedIsUpper = false
        var sawCased = false
        for (ch in rawPrefix) {
            val isUpper = Character.isUpperCase(ch)
            val isLower = Character.isLowerCase(ch)
            // Caseless characters (combining marks above all) are skipped outright: counting them
            // as lowercase would turn a decomposed "Й" into mixed case.
            if (!isUpper && !isLower) continue
            if (!sawCased) {
                sawCased = true
                firstCasedIsUpper = isUpper
            }
            if (isUpper) upperCount++ else lowerCount++
        }
        if (upperCount == 0) return PrefixCasing.LOWER
        if (upperCount >= 2 && lowerCount == 0) return PrefixCasing.ALL_CAPS
        // Exactly one uppercase letter, and it opens the word: "С" and "Сүз" are both Initial Caps.
        // A lone uppercase later in the word ("сҮз") is mixed case and yields no results.
        if (upperCount == 1 && firstCasedIsUpper) return PrefixCasing.INITIAL_CAPS
        return PrefixCasing.MIXED
    }

    /**
     * Re-applies the user's [casing] to a [candidate] taken from the dictionary (NFC lowercase).
     *
     * Called only AFTER ranking, on the candidates that are actually about to be shown, because
     * ranking runs on the normalized lowercase forms. Casing uses the invariant locale, exactly
     * like [normalizeForLookup], so it stays in step with the Python packing pipeline.
     *
     * [PrefixCasing.MIXED] must never reach this function: the frozen contract requires 0 results
     * for mixed case, so the caller drops the candidates earlier. It is handled as a pass-through
     * rather than as a throw because an exception on the tap path would take the keyboard down.
     */
    @JvmStatic
    fun applyCasing(candidate: String, casing: PrefixCasing): String = when (casing) {
        PrefixCasing.LOWER, PrefixCasing.MIXED -> candidate
        // Uppercase the first character as a string: some casing maps expand to several characters,
        // and going through String.uppercase() keeps that (and the invariant locale) correct.
        PrefixCasing.INITIAL_CAPS ->
            if (candidate.isEmpty()) candidate
            else candidate.substring(0, 1).uppercase() + candidate.substring(1)
        PrefixCasing.ALL_CAPS -> candidate.uppercase()
    }
}
