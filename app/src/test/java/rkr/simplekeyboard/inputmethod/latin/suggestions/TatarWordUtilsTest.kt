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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import rkr.simplekeyboard.inputmethod.latin.suggestions.TatarWordUtils.PrefixCasing

class TatarWordUtilsTest {

    private fun nfd(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)

    @Test
    fun extractTrailingWordReturnsLetterRun() {
        assertEquals("salom", TatarWordUtils.extractTrailingWord("salom"))
    }

    @Test
    fun extractTrailingWordTakesOnlyTheFinalWord() {
        assertEquals("world", TatarWordUtils.extractTrailingWord("hello world"))
    }

    @Test
    fun extractTrailingWordStopsAtPunctuation() {
        assertEquals("def", TatarWordUtils.extractTrailingWord("abc-def"))
    }

    @Test
    fun extractTrailingWordEmptyWhenLastCharIsSpace() {
        assertEquals("", TatarWordUtils.extractTrailingWord("salom "))
    }

    @Test
    fun extractTrailingWordEmptyWhenLastCharIsPunctuation() {
        assertEquals("", TatarWordUtils.extractTrailingWord("done."))
    }

    @Test
    fun extractTrailingWordEmptyWhenLastCharIsDigit() {
        assertEquals("", TatarWordUtils.extractTrailingWord("test123"))
    }

    @Test
    fun extractTrailingWordEmptyForNullAndEmpty() {
        assertEquals("", TatarWordUtils.extractTrailingWord(null))
        assertEquals("", TatarWordUtils.extractTrailingWord(""))
    }

    @Test
    fun extractTrailingWordHandlesRussianCyrillic() {
        assertEquals("привет", TatarWordUtils.extractTrailingWord("скажи привет"))
    }

    @Test
    fun extractTrailingWordHandlesTatarSpecificLetters() {
        // Contains Tatar-specific letters ә, ү, җ, ң, һ, ө.
        assertEquals("сүзләрөҗңһ", TatarWordUtils.extractTrailingWord("яз сүзләрөҗңһ"))
    }

    @Test
    fun extractTrailingWordHandlesMixedScripts() {
        // Latin + Cyrillic are all letters, so the whole trailing run is returned.
        assertEquals("abвг", TatarWordUtils.extractTrailingWord(" abвг"))
    }

    @Test
    fun extractTrailingWordKeepsCombiningMarksInsideDecomposedWord() {
        // In NFD "й" is "и" + U+0306; the mark must not cut the word down to its tail.
        assertEquals(nfd("сәйл"), TatarWordUtils.extractTrailingWord(nfd("яз сәйл")))
    }

    @Test
    fun extractTrailingWordKeepsWordEndingWithCombiningMark() {
        // The user just typed the decomposed "й", so the last character is the mark itself.
        val word = TatarWordUtils.extractTrailingWord(nfd("яз сәй"))
        assertEquals(nfd("сәй"), word)
        assertEquals('\u0306', word[word.length - 1])
    }

    @Test
    fun extractTrailingWordHandlesDecomposedRussianYo() {
        // In NFD "ё" is "е" + U+0308.
        assertEquals(nfd("ёлка"), TatarWordUtils.extractTrailingWord(nfd("бу ёлка")))
    }

    @Test
    fun extractTrailingWordReturnsRawSpanNotNormalizedForm() {
        // The commit path deletes exactly word.length raw characters, so the span must stay
        // decomposed: 4 chars here (с, ә, и, U+0306) against 3 in NFC.
        val word = TatarWordUtils.extractTrailingWord(nfd("яз сәй"))
        assertEquals(4, word.length)
        assertNotEquals("сәй", word)
        assertEquals("сәй", Normalizer.normalize(word, Normalizer.Form.NFC))
    }

    @Test
    fun extractTrailingWordUnchangedForComposedInput() {
        assertEquals("сәйл", TatarWordUtils.extractTrailingWord("яз сәйл"))
        assertEquals("ёлка", TatarWordUtils.extractTrailingWord("бу ёлка"))
    }

    @Test
    fun extractTrailingWordEmptyForLoneCombiningMark() {
        // A mark with no base letter in the run is an orphan, not a word.
        assertEquals("", TatarWordUtils.extractTrailingWord("сүз \u0306"))
        assertEquals("", TatarWordUtils.extractTrailingWord("\u0306"))
    }

    @Test
    fun extractTrailingWordTrimsOrphanMarkAtRunStart() {
        assertEquals("def", TatarWordUtils.extractTrailingWord("abc-\u0306def"))
    }

    @Test
    fun normalizeForLookupLowercasesLatin() {
        assertEquals("hello", TatarWordUtils.normalizeForLookup("HeLLo"))
    }

    @Test
    fun normalizeForLookupLowercasesTatarSpecificLetters() {
        assertEquals("әөүҗңһ", TatarWordUtils.normalizeForLookup("ӘӨҮҖҢҺ"))
    }

    @Test
    fun normalizeForLookupLowercasesMixedTatarWord() {
        assertEquals("сүзләр", TatarWordUtils.normalizeForLookup("Сүзләр"))
        assertEquals("сүзләр", TatarWordUtils.normalizeForLookup("СҮЗЛӘР"))
    }

    @Test
    fun normalizeForLookupIsIdempotentOnAlreadyNormalized() {
        val normalized = TatarWordUtils.normalizeForLookup("сүзләр")
        assertEquals(normalized, TatarWordUtils.normalizeForLookup(normalized))
    }

    @Test
    fun toLookupBytesEncodesUtf8() {
        assertArrayEquals("сүз".toByteArray(Charsets.UTF_8), TatarWordUtils.toLookupBytes("сүз"))
    }

    @Test
    fun toLookupBytesRoundTripsThroughNormalization() {
        val bytes = TatarWordUtils.toLookupBytes(TatarWordUtils.normalizeForLookup("СҮЗ"))
        assertArrayEquals("сүз".toByteArray(Charsets.UTF_8), bytes)
    }

    @Test
    fun classifyCasingLowerForAllLowercase() {
        assertEquals(PrefixCasing.LOWER, TatarWordUtils.classifyCasing("сүз"))
        assertEquals(PrefixCasing.LOWER, TatarWordUtils.classifyCasing("әни"))
        assertEquals(PrefixCasing.LOWER, TatarWordUtils.classifyCasing("hello"))
        assertEquals(PrefixCasing.LOWER, TatarWordUtils.classifyCasing("с"))
    }

    @Test
    fun classifyCasingLowerForEmptyPrefix() {
        assertEquals(PrefixCasing.LOWER, TatarWordUtils.classifyCasing(""))
    }

    @Test
    fun classifyCasingInitialCapsForSingleUppercaseLetter() {
        // "One uppercase letter" is Initial Caps, not ALL CAPS: the sentence-start case.
        assertEquals(PrefixCasing.INITIAL_CAPS, TatarWordUtils.classifyCasing("С"))
        assertEquals(PrefixCasing.INITIAL_CAPS, TatarWordUtils.classifyCasing("Ә"))
    }

    @Test
    fun classifyCasingInitialCapsForLeadingUppercase() {
        assertEquals(PrefixCasing.INITIAL_CAPS, TatarWordUtils.classifyCasing("Сүз"))
        assertEquals(PrefixCasing.INITIAL_CAPS, TatarWordUtils.classifyCasing("Әни"))
        assertEquals(PrefixCasing.INITIAL_CAPS, TatarWordUtils.classifyCasing("Җырлар"))
    }

    @Test
    fun classifyCasingAllCapsForTwoOrMoreUppercase() {
        assertEquals(PrefixCasing.ALL_CAPS, TatarWordUtils.classifyCasing("СҮЗ"))
        assertEquals(PrefixCasing.ALL_CAPS, TatarWordUtils.classifyCasing("СҮ"))
        assertEquals(PrefixCasing.ALL_CAPS, TatarWordUtils.classifyCasing("ӘӨҮҖҢҺ"))
    }

    @Test
    fun classifyCasingMixedForEverythingElse() {
        assertEquals(PrefixCasing.MIXED, TatarWordUtils.classifyCasing("сҮз"))
        assertEquals(PrefixCasing.MIXED, TatarWordUtils.classifyCasing("СҮз"))
        assertEquals(PrefixCasing.MIXED, TatarWordUtils.classifyCasing("аБ"))
        assertEquals(PrefixCasing.MIXED, TatarWordUtils.classifyCasing("сүЗЛӘР"))
    }

    @Test
    fun classifyCasingIgnoresCaselessCombiningMarks() {
        // Decomposed input must classify exactly like its composed form: the marks are caseless.
        assertEquals(PrefixCasing.INITIAL_CAPS, TatarWordUtils.classifyCasing(nfd("Й")))
        assertEquals(PrefixCasing.INITIAL_CAPS, TatarWordUtils.classifyCasing(nfd("Сәй")))
        assertEquals(PrefixCasing.ALL_CAPS, TatarWordUtils.classifyCasing(nfd("СӘЙ")))
        assertEquals(PrefixCasing.LOWER, TatarWordUtils.classifyCasing(nfd("сәй")))
        assertEquals(PrefixCasing.LOWER, TatarWordUtils.classifyCasing("\u0306"))
    }

    @Test
    fun applyCasingLowerReturnsCandidateUnchanged() {
        assertEquals("сүзләр", TatarWordUtils.applyCasing("сүзләр", PrefixCasing.LOWER))
    }

    @Test
    fun applyCasingInitialCapsUppercasesOnlyTheFirstLetter() {
        assertEquals("Сүзләр", TatarWordUtils.applyCasing("сүзләр", PrefixCasing.INITIAL_CAPS))
    }

    @Test
    fun applyCasingInitialCapsHandlesTatarSpecificFirstLetters() {
        assertEquals("Әни", TatarWordUtils.applyCasing("әни", PrefixCasing.INITIAL_CAPS))
        assertEquals("Өйдә", TatarWordUtils.applyCasing("өйдә", PrefixCasing.INITIAL_CAPS))
        assertEquals("Үзем", TatarWordUtils.applyCasing("үзем", PrefixCasing.INITIAL_CAPS))
        assertEquals("Җыр", TatarWordUtils.applyCasing("җыр", PrefixCasing.INITIAL_CAPS))
        assertEquals("Һава", TatarWordUtils.applyCasing("һава", PrefixCasing.INITIAL_CAPS))
        assertEquals("Ңгы", TatarWordUtils.applyCasing("ңгы", PrefixCasing.INITIAL_CAPS))
    }

    @Test
    fun applyCasingAllCapsUppercasesTatarSpecificLetters() {
        assertEquals("СҮЗЛӘР", TatarWordUtils.applyCasing("сүзләр", PrefixCasing.ALL_CAPS))
        assertEquals("ӘӨҮҖҢҺ", TatarWordUtils.applyCasing("әөүҗңһ", PrefixCasing.ALL_CAPS))
    }

    @Test
    fun applyCasingHandlesEmptyCandidate() {
        assertEquals("", TatarWordUtils.applyCasing("", PrefixCasing.INITIAL_CAPS))
        assertEquals("", TatarWordUtils.applyCasing("", PrefixCasing.ALL_CAPS))
    }

    @Test
    fun applyCasingMixedIsAPassThrough() {
        // Mixed case must be filtered out before this point (0 results); never a crash.
        assertEquals("сүзләр", TatarWordUtils.applyCasing("сүзләр", PrefixCasing.MIXED))
    }

    @Test
    fun startsWithWordCharacterIsFalseForEmptyAndWordEndingContext() {
        assertFalse(TatarWordUtils.startsWithWordCharacter(null))
        assertFalse(TatarWordUtils.startsWithWordCharacter(""))
        assertFalse(TatarWordUtils.startsWithWordCharacter(" дигән"))
        assertFalse(TatarWordUtils.startsWithWordCharacter(", дигән"))
        assertFalse(TatarWordUtils.startsWithWordCharacter("\nсүз"))
        assertFalse(TatarWordUtils.startsWithWordCharacter("123"))
        assertFalse(TatarWordUtils.startsWithWordCharacter("-сүз"))
        assertFalse(TatarWordUtils.startsWithWordCharacter("😀"))
    }

    @Test
    fun startsWithWordCharacterIsTrueInsideAWord() {
        // Cursor in the middle of "китап": committing a replacement would corrupt the text.
        assertTrue(TatarWordUtils.startsWithWordCharacter("тап"))
        assertTrue(TatarWordUtils.startsWithWordCharacter("ә"))
        // Deliberately wider than the Tatar alphabet: fail-closed also for Russian and Latin.
        assertTrue(TatarWordUtils.startsWithWordCharacter("ыть"))
        assertTrue(TatarWordUtils.startsWithWordCharacter("word"))
    }

    @Test
    fun startsWithWordCharacterIsTrueForALeadingCombiningMark() {
        // The cursor sits inside a canonically decomposed letter ("й" = "и" + U+0306), which is
        // inside a word by definition.
        assertTrue(TatarWordUtils.startsWithWordCharacter(nfd("й").substring(1)))
    }

    @Test
    fun classifyThenApplyPreservesTypedCapitalization() {
        val candidate = "сүзләр"
        assertEquals(
            "Сүзләр",
            TatarWordUtils.applyCasing(candidate, TatarWordUtils.classifyCasing("Сүз")),
        )
        assertEquals(
            "СҮЗЛӘР",
            TatarWordUtils.applyCasing(candidate, TatarWordUtils.classifyCasing("СҮЗ")),
        )
        assertEquals(
            "сүзләр",
            TatarWordUtils.applyCasing(candidate, TatarWordUtils.classifyCasing("сүз")),
        )
        // A single typed capital at a sentence start keeps the capital the user produced.
        assertEquals(
            "Сүзләр",
            TatarWordUtils.applyCasing(candidate, TatarWordUtils.classifyCasing("С")),
        )
    }
}
