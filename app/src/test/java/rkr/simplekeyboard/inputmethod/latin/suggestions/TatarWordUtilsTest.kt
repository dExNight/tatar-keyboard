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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TatarWordUtilsTest {

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
}
