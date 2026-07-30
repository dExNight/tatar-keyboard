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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.PersonalCandidate
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.PersonalCandidateSource
import rkr.simplekeyboard.inputmethod.latin.suggestions.SuggestionStripState

/**
 * The single ranking of E4b, one named test per point of the written amendment to «Контракт текста»
 * (2026-07-30, «единая редакция ранжирования на три класса»).
 *
 * The tests drive [CompositePrefixComputer] with a fake primary, so they exercise the merge itself
 * rather than the mmap index: what is under test is the ORDER, not the dictionary.
 */
class CompositePrefixComputerTest {

    private class FakePrimary(
        private val results: List<String>,
        override val lastExactCount: Int,
    ) : ClassifiedPrefixComputer {
        override fun lookup(normalizedPrefixUtf8: ImmutableUtf8Prefix): List<String> = results
    }

    private fun prefix(text: String) = ImmutableUtf8Prefix.copyOf(text.toByteArray(Charsets.UTF_8))

    private fun personalSource(vararg entries: Pair<String, String>): PersonalCandidateSource =
        PersonalCandidateSource { entries.map { PersonalCandidate(it.first, it.second) } }

    private fun merge(
        dictionary: List<String>,
        exactCount: Int,
        personal: PersonalCandidateSource,
    ): List<String> =
        CompositePrefixComputer(FakePrimary(dictionary, exactCount), personal).lookup(prefix("гүз"))

    @Test
    fun personalOnlyWordTakesIndexOneWhenExactCandidatesExist() {
        val result = merge(
            listOf("гүзәллек", "гүзәлләр"), exactCount = 2,
            personalSource("гүзәлия" to "гүзәлия"),
        )
        assertEquals(listOf("гүзәллек", "гүзәлия", "гүзәлләр"), result)
    }

    @Test
    fun personalOnlyWordTakesIndexZeroWhenThereAreNoExactCandidates() {
        val result = merge(
            listOf("гүзәлфия"), exactCount = 0, // the one dictionary hit is fuzzy
            personalSource("гүзәлия" to "гүзәлия"),
        )
        assertEquals(listOf("гүзәлия", "гүзәлфия"), result)
    }

    @Test
    fun atMostOnePersonalOnlyWordEverAppears() {
        val result = merge(
            listOf("гүзәллек"), exactCount = 1,
            personalSource("гүзәлия" to "гүзәлия", "гүзәлбану" to "гүзәлбану"),
        )
        assertEquals("only one personal cell, whatever the personal source offers",
            listOf("гүзәллек", "гүзәлия"), result)
    }

    @Test
    fun theThirdExactCandidateIsPushedOutOfTheBandEntirely() {
        val result = merge(
            listOf("гүзәллек", "гүзәлләр", "гүзәллеге"), exactCount = 3,
            personalSource("гүзәлия" to "гүзәлия"),
        )
        assertEquals("the band is three cells: the third exact candidate is not shown at all",
            listOf("гүзәллек", "гүзәлия", "гүзәлләр"), result)
        assertEquals(SuggestionStripState.CELL_COUNT, result.size)
    }

    @Test
    fun fuzzyCandidatesFollowBothExactAndPersonalOnly() {
        val result = merge(
            listOf("гүзәллек", "гүзалләр"), exactCount = 1, // second entry is a fuzzy candidate
            personalSource("гүзәлия" to "гүзәлия"),
        )
        assertEquals(listOf("гүзәллек", "гүзәлия", "гүзалләр"), result)
    }

    @Test
    fun withThePersonalDictionaryOffTheResultIsByteForByteTheE3Result() {
        val dictionary = listOf("гүзәллек", "гүзәлләр", "гүзәллеге")
        val result = CompositePrefixComputer(
            FakePrimary(dictionary, lastExactCount = 2), PersonalCandidateSource.EMPTY,
        ).lookup(prefix("гүз"))
        assertSame("not merely equal — the very same list the primary returned", dictionary, result)
    }

    @Test
    fun anEmptyPersonalMatchSetChangesNothingEither() {
        val dictionary = listOf("гүзәллек")
        val result = merge(dictionary, exactCount = 1, personalSource())
        assertSame(dictionary, result)
    }

    @Test
    fun duplicateByNormalizedFormOccupiesExactlyOneCell() {
        val result = merge(
            listOf("гүзәл", "гүзәллек"), exactCount = 2,
            personalSource("гүзәл" to "гүзәл"), // saved exactly as the dictionary spells it
        )
        assertEquals("no second cell for a word the dictionary already offers",
            listOf("гүзәл", "гүзәллек"), result)
        assertEquals(1, result.count { it == "гүзәл" })
    }

    @Test
    fun theStoredCasingWinsTheDuplicateWhenItDiffersFromTheNormalizedForm() {
        val result = merge(
            listOf("гүзәл", "гүзәллек"), exactCount = 2,
            personalSource("Гүзәл" to "гүзәл"), // a name the user saved capitalised
        )
        assertEquals("one cell, spelled the way the user saved it",
            listOf("Гүзәл", "гүзәллек"), result)
    }

    @Test
    fun withinThePersonalSourceOrderIsUsageCountThenCodePoint() {
        // The order is produced by PersonalDictionary.lookupCandidates; the merge must take the
        // FIRST personal-only match of that order and no other.
        val result = merge(
            listOf("гүзәллек"), exactCount = 1,
            personalSource("гүзәлбану" to "гүзәлбану", "гүзәлия" to "гүзәлия"),
        )
        assertEquals(listOf("гүзәллек", "гүзәлбану"), result)
    }

    @Test
    fun aDuplicateNeverBecomesThePersonalOnlyWord() {
        // The first personal match duplicates a dictionary candidate: it changes that cell's casing
        // and the SECOND match becomes the personal-only word.
        val result = merge(
            listOf("гүзәл", "гүзәллек"), exactCount = 2,
            personalSource("Гүзәл" to "гүзәл", "гүзәлия" to "гүзәлия"),
        )
        assertEquals(listOf("Гүзәл", "гүзәлия", "гүзәллек"), result)
    }

    @Test
    fun aBrokenPersonalSourceCannotTakeDictionarySuggestionsDown() {
        val dictionary = listOf("гүзәллек", "гүзәлләр")
        val exploding = object : PersonalCandidateSource {
            override fun candidatesFor(normalizedPrefix: String): List<PersonalCandidate> =
                throw IllegalStateException("personal source is broken")

            override fun isEmpty(): Boolean = false
        }
        val result = CompositePrefixComputer(FakePrimary(dictionary, 2), exploding)
            .lookup(prefix("гүз"))
        assertSame(dictionary, result)
    }

    @Test
    fun theBandCapMatchesTheStripAndTheIndex() {
        assertEquals(SuggestionStripState.CELL_COUNT, CompositePrefixComputer.CELL_COUNT)
        // Three exact plus a personal-only word can never produce a fourth cell.
        val result = merge(
            listOf("а1", "а2", "а3"), exactCount = 3, personalSource("личное" to "личное"),
        )
        assertTrue(result.size <= CompositePrefixComputer.CELL_COUNT)
    }
}
