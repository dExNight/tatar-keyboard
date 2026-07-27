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

package rkr.simplekeyboard.inputmethod.latin.dictionary.engine

import java.text.Normalizer
import java.util.Arrays

/**
 * Immutable adjacency source ("источник соседства") derived from the live keyboard layout.
 *
 * It is built by a pure function ([build]) from a plain list of [RawKey]s so it carries no Android
 * type and can be exercised by ordinary JVM tests. The keyboard is queried on the Android side
 * (`Keyboard.getSortedKeys()` / `Key.getMoreKeys()`) and adapted into [RawKey]s there
 * ([rkr.simplekeyboard.inputmethod.latin.suggestions.KeyNeighborTableBuilder]); not a single letter
 * or key pair is hard-coded here.
 *
 * E3a fills in the edit class #1 source only — long-press partners taken from the layout's
 * `moreKeys`. The raw geometry (`left/top/right/bottom`) of every letter key is accepted by [build]
 * so the geometric-neighbour relation (edit class #2) can be derived from the very same source in
 * E3b without re-reading the layout; E3a neither computes nor uses it.
 *
 * The table carries the [subtypeId] it was built for so the engine can refuse a fuzzy pass when the
 * table does not match the requesting subtype. It is fully immutable once built.
 *
 * Partners are stored in a sorted [IntArray] searched by primitive binary search: the hot fuzzy
 * path never boxes a code point, so it allocates nothing per position or per variant.
 */
class KeyNeighborTable private constructor(
    val subtypeId: String,
    private val partnerKeys: IntArray,
    private val partnerValues: Array<IntArray>,
    /** Every distinct code point that is a node of the table (keys plus more-key-only letters). */
    val nodes: IntArray,
    /** Geometry-bearing letter keys actually read from the layout (37 on the Tatar layout). */
    val letterKeyCount: Int,
) {

    val isEmpty: Boolean get() = nodes.isEmpty()

    /**
     * Long-press partners of [codePoint] (edit class #1), or null when the code point has none.
     *
     * The returned array is shared and MUST NOT be mutated by the caller: fuzzy variant generation
     * reads it on the hot lookup path and allocates nothing per variant.
     */
    fun longPressPartnersOf(codePoint: Int): IntArray? {
        val index = Arrays.binarySearch(partnerKeys, codePoint)
        return if (index >= 0) partnerValues[index] else null
    }

    /**
     * One alphabet key as read from the live layout, before any normalization.
     *
     * [codePoint] is the key's code; [moreKeyCodePoints] are the `mCode`s of the key's more-keys
     * (the long-press partners). Geometry is the visible key rectangle in pixels, retained for the
     * E3b geometric-neighbour relation.
     */
    class RawKey(
        val codePoint: Int,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val moreKeyCodePoints: IntArray,
    )

    companion object {
        /**
         * Builds the table from the keys of a single keyboard element.
         *
         * The whole table is empty unless [isAlphabetElement] is true: on the shifted element the
         * codes are upper-case and on symbols they are not letters, so only the alphabet element
         * (`KeyboardId.isAlphabetKeyboard()`) is a valid source. This is an engineering precaution
         * rather than a proven fact about `getSortedKeys()`, and it is confirmed by the phase test.
         *
         * Every code (primary and more-key) is folded to its NFC lower-case form; non-code-point
         * and non-letter keys are dropped. Long-press pairs in the layout are one-directional and
         * duplicated ("ә" is declared on both "а" and "э", with no back link from "ә"), so the map
         * is symmetrized and de-duplicated explicitly — without that half the diacritic pairs would
         * silently never fire.
         */
        fun build(
            subtypeId: String,
            isAlphabetElement: Boolean,
            keys: List<RawKey>,
        ): KeyNeighborTable {
            if (!isAlphabetElement) {
                return KeyNeighborTable(subtypeId, IntArray(0), emptyArray(), IntArray(0), 0)
            }
            val pairs = HashMap<Int, MutableSet<Int>>()
            val nodeSet = sortedSetOf<Int>()
            var letterKeys = 0
            for (key in keys) {
                val base = normalizeLetterCodePoint(key.codePoint) ?: continue
                letterKeys++
                nodeSet.add(base)
                for (rawMoreKey in key.moreKeyCodePoints) {
                    val partner = normalizeLetterCodePoint(rawMoreKey) ?: continue
                    if (partner == base) continue
                    nodeSet.add(partner)
                    // Symmetrize: the layout only stores base -> partner, never the reverse.
                    pairs.getOrPut(base) { HashSet() }.add(partner)
                    pairs.getOrPut(partner) { HashSet() }.add(base)
                }
            }
            val partnerKeys = pairs.keys.toIntArray().also { it.sort() }
            val partnerValues = Array(partnerKeys.size) { index ->
                pairs.getValue(partnerKeys[index]).toIntArray().also { it.sort() }
            }
            return KeyNeighborTable(subtypeId, partnerKeys, partnerValues, nodeSet.toIntArray(), letterKeys)
        }

        /**
         * Folds a raw key code to a single NFC lower-case letter code point, or null when it is not
         * a code point, expands to more than one code point once lower-cased, or is not a letter.
         */
        private fun normalizeLetterCodePoint(codePoint: Int): Int? {
            if (!Character.isValidCodePoint(codePoint)) return null
            val folded = Normalizer.normalize(String(Character.toChars(codePoint)), Normalizer.Form.NFC)
                .lowercase()
            if (folded.codePointCount(0, folded.length) != 1) return null
            val normalized = folded.codePointAt(0)
            if (!Character.isLetter(normalized)) return null
            return normalized
        }
    }
}
