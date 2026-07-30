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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.PersonalSubtypes
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.TpersFormat
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.TpersValidator
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.ValidatedPersonalDictionary
import java.nio.charset.StandardCharsets

/**
 * Pure JVM tests (no Android) for the eviction and serialization logic of [PersonalEntries]: the
 * LRU rule keyed by the monotonic file serial, the record cap binding before the byte cap, and a
 * round-trip through `TpersValidator` proving the writer produces bytes the reader accepts.
 */
class PersonalEntriesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val subtype = PersonalSubtypes.TATAR_RU
    private val alphabet = requireNotNull(PersonalSubtypes.alphabetFor(subtype))

    private fun norm(word: String) = PersonalWordFilter.normalize(word)

    private fun PersonalEntries.upsertWord(word: String) = upsert(word, norm(word))

    @Test
    fun evictsTheSmallestSerialEntryWhenOverTheCap() {
        var entries = PersonalEntries.empty(maxEntries = 2)
        entries = entries.upsertWord("абый") // serial 1
        entries = entries.upsertWord("сүзлек") // serial 2
        entries = entries.upsertWord("китап") // serial 3 -> over cap, evict serial 1 ("абый")

        assertEquals(2, entries.size)
        assertFalse("least-recently-used word must be evicted", entries.containsNormalized(norm("абый")))
        assertTrue(entries.containsNormalized(norm("сүзлек")))
        assertTrue(entries.containsNormalized(norm("китап")))
    }

    @Test
    fun touchingAnEntryProtectsItFromEvictionByRaisingItsSerial() {
        var entries = PersonalEntries.empty(maxEntries = 2)
        entries = entries.upsertWord("абый") // serial 1
        entries = entries.upsertWord("сүзлек") // serial 2
        entries = entries.upsertWord("абый") // serial 3: "абый" is now the most recent
        entries = entries.upsertWord("китап") // serial 4 -> over cap, evict smallest serial = "сүзлек"

        assertEquals(2, entries.size)
        assertTrue("touched word must survive", entries.containsNormalized(norm("абый")))
        assertFalse(entries.containsNormalized(norm("сүзлек")))
        assertTrue(entries.containsNormalized(norm("китап")))
    }

    @Test
    fun upsertingAnExistingWordBumpsItsCounterAndTouchesItsSerial() {
        var entries = PersonalEntries.empty(maxEntries = 8)
        entries = entries.upsertWord("китап") // count 1, serial 1
        entries = entries.upsertWord("абый") // count 1, serial 2
        entries = entries.upsertWord("китап") // count 2, serial 3

        val index = (0 until entries.size).first { entries.normalizedFormAt(it) == norm("китап") }
        assertEquals(2, entries.usageCountAt(index))
        assertEquals(3L, entries.lastUseSerialAt(index))
    }

    @Test
    fun noteUseBumpsAnExistingEntryButNeverCreatesAPhantom() {
        var entries = PersonalEntries.empty(maxEntries = 8)
        entries = entries.upsertWord("китап") // count 1, serial 1

        assertTrue(entries.noteUse(norm("сүзлек")) == null) // absent -> no phantom

        val touched = requireNotNull(entries.noteUse(norm("китап")))
        assertEquals(1, touched.size)
        val index = 0
        assertEquals(norm("китап"), touched.normalizedFormAt(index))
        assertEquals(2, touched.usageCountAt(index))
        assertEquals(2L, touched.lastUseSerialAt(index))
    }

    @Test
    fun usageCounterSaturatesAtU16() {
        var entries = PersonalEntries.empty(maxEntries = 4)
        entries = entries.upsertWord("китап") // count 1
        repeat(TpersFormat.MAX_U16.toInt() + 8) { entries = entries.upsertWord("китап") }

        assertEquals(TpersFormat.MAX_U16.toInt(), entries.usageCountAt(0))
    }

    @Test
    fun serializedEntriesRoundTripThroughTheValidatorInNormalizedOrder() {
        var entries = PersonalEntries.empty(maxEntries = 16)
        // Deliberately inserted out of normalized order; serialize must emit them sorted.
        entries = entries.upsertWord("сүзлек")
        entries = entries.upsertWord("абый")
        entries = entries.upsertWord("Гүзәл") // stored raw with a capital, keyed as "гүзәл"

        val bytes = entries.serialize(subtype)
        val file = temporaryFolder.newFile("round-trip.tpers").also { it.writeBytes(bytes) }
        val validated = TpersValidator().validate(file, subtype)

        assertEquals(3, validated.entryCount)
        // The on-disk order is ascending by normalized form.
        val normalized = validated.normalizedForms
        assertEquals(normalized.sortedWith(utf8Order), normalized)
        assertTrue(validated.rawForms.contains("Гүзәл")) // original casing preserved on disk
        assertTrue(validated.normalizedForms.contains("гүзәл"))
    }

    @Test
    fun theRecordCapBindsBeforeTheByteCapAtTwoThousandMaxLengthEntries() {
        val cap = TpersFormat.MAX_PERSONAL_ENTRIES.toInt()
        val words = (0 until cap).map { maxLengthWord(it) }.sortedWith(utf8Order)
        val validated = ValidatedPersonalDictionary(
            rawForms = words,
            normalizedForms = words,
            usageCounts = IntArray(cap) { 1 },
            lastUseSerials = LongArray(cap) { (it + 1).toLong() },
            subtypeTag = subtype,
        )
        val entries = PersonalEntries.fromValidated(validated, cap)

        val bytes = entries.serialize(subtype)
        // 2000 * (1 + 2 + 4 + 48) + 72 header = 110072 bytes, comfortably under the 131072 byte cap.
        assertEquals(cap, entries.size)
        assertTrue("byte cap must not bind before the record cap", bytes.size <= TpersFormat.MAX_FILE_SIZE)
        assertTrue(bytes.size > 100_000)
        // Serialized bytes are accepted by the validator (order, checksum, alphabet, length all hold).
        val file = temporaryFolder.newFile("full.tpers").also { it.writeBytes(bytes) }
        assertEquals(cap, TpersValidator().validate(file, subtype).entryCount)
    }

    @Test
    fun addingBeyondTwoThousandEvictsTheOldestByFileSerial() {
        val cap = TpersFormat.MAX_PERSONAL_ENTRIES.toInt()
        val words = (0 until cap).map { maxLengthWord(it) }.sortedWith(utf8Order)
        val validated = ValidatedPersonalDictionary(
            rawForms = words,
            normalizedForms = words,
            usageCounts = IntArray(cap) { 1 },
            lastUseSerials = LongArray(cap) { (it + 1).toLong() }, // serials 1..2000
            subtypeTag = subtype,
        )
        var entries = PersonalEntries.fromValidated(validated, cap)
        val oldest = words.first { entries.lastUseSerialAt(entries.indexOfNorm(it)) == 1L }

        entries = entries.upsertWord("абыйлар") // a brand-new word takes the next serial (2001)

        assertEquals(cap, entries.size)
        assertFalse("the serial-1 entry must be evicted", entries.containsNormalized(oldest))
        assertTrue(entries.containsNormalized(norm("абыйлар")))
    }

    private fun PersonalEntries.indexOfNorm(normalized: String): Int =
        (0 until size).first { normalizedFormAt(it) == normalized }

    /** A distinct, valid 24-code-point Tatar word for index [i] (all lowercase, in the alphabet). */
    private fun maxLengthWord(i: Int): String {
        val letters = "аәбвгдеёжҗзийклмнңоөпрстуүфхһцчшщъыьэюя"
        val a = letters[i / (letters.length * letters.length) % letters.length]
        val b = letters[i / letters.length % letters.length]
        val c = letters[i % letters.length]
        return "к".repeat(21) + a + b + c // 21 + 3 = 24 code points
    }

    private val utf8Order = Comparator<String> { first, second ->
        val a = first.toByteArray(StandardCharsets.UTF_8)
        val b = second.toByteArray(StandardCharsets.UTF_8)
        val count = minOf(a.size, b.size)
        for (index in 0 until count) {
            val diff = (a[index].toInt() and 0xff) - (b[index].toInt() and 0xff)
            if (diff != 0) return@Comparator diff
        }
        a.size - b.size
    }
}
