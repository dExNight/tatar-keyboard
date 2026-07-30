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

import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.PersonalCandidate
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.PersonalCandidateSource

/**
 * The single ranking of E4b: dictionary candidates and one personal word merged into the three
 * cells of the band, in the order frozen by «Контракт текста»:
 *
 *  1. exact dictionary candidates, in their existing order (frequency desc, then code point asc);
 *  2. at most ONE personal-only word — index 0 when there are no exact dictionary candidates,
 *     index 1 otherwise;
 *  3. fuzzy (E3) candidates, in the order of their own level.
 *
 * Two consequences are stated in the contract and implemented literally here:
 *
 *  - the band is three cells, so a personal word at index 1 does not "push the exact candidates
 *    down" — it pushes the THIRD exact candidate out of the band entirely;
 *  - a word present both in the dictionary and in the personal list occupies exactly ONE cell, and
 *    when the saved casing differs from the normalized form the PERSONAL spelling wins («словарное
 *    гүзәл» + «личное Гүзәл» → one cell «Гүзәл»). Otherwise the dictionary candidate stands as it is.
 *
 * With the personal source empty — the feature off, the device still locked, or simply nothing
 * saved — this returns the primary's list ITSELF, unchanged and not even copied, so the result stays
 * byte-for-byte the E3 result. That is the property the acceptance names, and it is why the empty
 * check comes before the prefix is even decoded.
 *
 * Casing is NOT applied here: display casing is applied after ranking, by the controller, exactly as
 * before. That is what makes the four E4a-1 casing rules hold for personal words for free — the
 * LOWER branch of `applyCasing` returns the candidate unchanged, so a saved «Гүзәл» reaches the cell
 * with its own casing, while INITIAL_CAPS and ALL_CAPS re-case it like any dictionary word.
 */
internal class CompositePrefixComputer(
    private val primary: ClassifiedPrefixComputer,
    private val personal: PersonalCandidateSource,
) : PrefixComputer, KeyNeighborSink {

    /**
     * The D3 verdict as it leaves the engine: the primary's, minus every word the user has saved
     * themselves. Written by the worker at the end of each lookup, read on the UI thread, hence
     * `@Volatile`.
     */
    @Volatile
    var lastAutocorrectAdvice: AutocorrectAdvice? = null
        private set

    /** Drops the current verdict; called when the engine idles or is torn down. */
    fun clearAutocorrectAdvice() {
        lastAutocorrectAdvice = null
    }

    override fun updateKeyNeighbors(table: KeyNeighborTable?) {
        (primary as? KeyNeighborSink)?.updateKeyNeighbors(table)
    }

    override fun lookup(normalizedPrefixUtf8: ImmutableUtf8Prefix): List<String> {
        val dictionary = primary.lookup(normalizedPrefixUtf8)
        lastAutocorrectAdvice = withoutPersonalWords(primary.lastAutocorrectAdvice)
        if (personal.isEmpty()) return dictionary
        val matches = try {
            personal.candidatesFor(normalizedPrefixUtf8.decodeUtf8())
        } catch (_: RuntimeException) {
            // Fail closed toward the frozen behaviour: a broken personal source must never take
            // dictionary suggestions down with it.
            return dictionary
        }
        if (matches.isEmpty()) return dictionary
        val exactCount = primary.lastExactCount.coerceIn(0, dictionary.size)
        return merge(dictionary, exactCount, matches)
    }

    /**
     * A word of the personal dictionary is never autocorrected — whatever the shipped asset thinks
     * of it. The user has already said this is their word, and replacing it would overrule their own
     * decision. Membership is decided on the NORMALIZED form by the same binary search E4d uses
     * (`PersonalDictionary.indexOfNormalized`), never on the displayed spelling.
     *
     * The check follows the same live gate as the band itself: with the personal dictionary switched
     * off the source publishes an empty snapshot, so nothing is looked up and no file is touched.
     * That boundary is deliberate — making autocorrect the one feature that still reads the personal
     * file while the setting is off would break the E4 rule that a disabled personal dictionary costs
     * the lookup path nothing.
     *
     * A source that throws vetoes nothing but also advises nothing: fail-closed towards NOT editing
     * the user's text.
     */
    private fun withoutPersonalWords(advice: AutocorrectAdvice?): AutocorrectAdvice? {
        if (advice == null) return null
        if (personal.isEmpty()) return advice
        return try {
            if (personal.containsNormalized(advice.typedWord)) null else advice
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun merge(
        dictionary: List<String>,
        exactCount: Int,
        matches: List<PersonalCandidate>,
    ): List<String> {
        val personalOnly = firstPersonalOnly(dictionary, matches)
        if (personalOnly == null && !anyDuplicateOverridesCasing(dictionary, matches)) {
            return dictionary
        }

        val cells = ArrayList<String>(CELL_COUNT)
        if (personalOnly != null && exactCount > 0) {
            cells.add(displayFormOf(dictionary[0], matches))
        }
        if (personalOnly != null) {
            cells.add(personalOnly.rawForm)
        }
        val firstRemainingExact = if (personalOnly != null && exactCount > 0) 1 else 0
        for (index in firstRemainingExact until exactCount) {
            if (cells.size >= CELL_COUNT) return cells
            cells.add(displayFormOf(dictionary[index], matches))
        }
        for (index in exactCount until dictionary.size) {
            if (cells.size >= CELL_COUNT) return cells
            cells.add(displayFormOf(dictionary[index], matches))
        }
        return cells
    }

    /**
     * The one personal word that earns a cell of its own: the first match, in personal order, whose
     * normalized form is not already among the dictionary candidates. A duplicate never becomes a
     * personal-only word — it only changes how its single shared cell is spelled.
     */
    private fun firstPersonalOnly(
        dictionary: List<String>,
        matches: List<PersonalCandidate>,
    ): PersonalCandidate? {
        for (match in matches) {
            if (!dictionary.contains(match.normalizedForm)) return match
        }
        return null
    }

    /**
     * The display form of one dictionary candidate: its own text, unless a personal record has the
     * same normalized form AND a different saved spelling, in which case the personal one wins.
     */
    private fun displayFormOf(dictionaryWord: String, matches: List<PersonalCandidate>): String {
        for (match in matches) {
            if (match.normalizedForm == dictionaryWord && match.rawForm != match.normalizedForm) {
                return match.rawForm
            }
        }
        return dictionaryWord
    }

    private fun anyDuplicateOverridesCasing(
        dictionary: List<String>,
        matches: List<PersonalCandidate>,
    ): Boolean {
        for (match in matches) {
            if (match.rawForm != match.normalizedForm && dictionary.contains(match.normalizedForm)) {
                return true
            }
        }
        return false
    }

    companion object {
        /**
         * The band is three cells (`SuggestionStripState.CELL_COUNT`) and the index returns at most
         * three candidates (`TdictPrefixIndex.MAX_RESULTS`), so the merge can only ever hand back
         * three. Pinned against both by `CompositePrefixComputerTest`.
         */
        internal const val CELL_COUNT = 3
    }
}
