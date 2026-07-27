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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.PersonalDictionaryTestFixtures.Entry
import java.io.File

class PersonalDictionaryReaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val reader = PersonalDictionaryReader()

    private val words = listOf(
        Entry("Гүзәл", count = 5, serial = 10),
        Entry("гүзәллек", count = 12, serial = 20),
        Entry("китап", count = 30, serial = 5),
    )

    @Test
    fun readsAValidFileIntoAnImmutableSnapshot() {
        val dictionary = reader.read(write(words), PersonalSubtypes.TATAR_RU)

        assertEquals(3, dictionary.size)
        assertEquals(PersonalSubtypes.TATAR_RU, dictionary.subtypeTag)
        assertEquals("Гүзәл", dictionary.rawFormAt(0))
        assertEquals("гүзәл", dictionary.normalizedFormAt(0))
    }

    @Test
    fun missingFileYieldsTheEmptySingleton() {
        assertSame(PersonalDictionary.EMPTY, reader.read(null, PersonalSubtypes.TATAR_RU))
        assertSame(
            PersonalDictionary.EMPTY,
            reader.read(File(temporaryFolder.root, "absent.tpers"), PersonalSubtypes.TATAR_RU),
        )
    }

    @Test
    fun unsupportedSubtypeIsFailClosed() {
        assertTrue(reader.read(write(words), "en_US").isEmpty)
    }

    @Test
    fun aFileKeyedToAnotherSubtypeIsRejectedAndReadsEmpty() {
        // The guard against a subtypeId mismatch: a file tagged ru_RU is invisible to tt_RU.
        val foreign = PersonalDictionaryTestFixtures.build(words, subtypeTag = "ru_RU")
        assertTrue(reader.read(writeBytes(foreign), PersonalSubtypes.TATAR_RU).isEmpty)
    }

    @Test
    fun aCorruptFileReadsEmptyRatherThanThrowing() {
        val bytes = PersonalDictionaryTestFixtures.build(words)
        bytes[TpersFormat.CHECKSUM_OFFSET] = (bytes[TpersFormat.CHECKSUM_OFFSET].toInt() xor 1).toByte()
        assertTrue(reader.read(writeBytes(bytes), PersonalSubtypes.TATAR_RU).isEmpty)
    }

    @Test
    fun prefixSearchReturnsRawFormsOrderedByUsageThenNormalized() {
        val dictionary = reader.read(write(words), PersonalSubtypes.TATAR_RU)
        // "гүз" matches "гүзәл" (count 5) and "гүзәллек" (count 12): higher usage first.
        assertEquals(listOf("гүзәллек", "Гүзәл"), dictionary.lookupRawForms("гүз"))
    }

    private fun write(entries: List<Entry>): File =
        writeBytes(PersonalDictionaryTestFixtures.build(entries))

    private fun writeBytes(bytes: ByteArray): File =
        temporaryFolder.newFile("personal-${System.nanoTime()}.tpers").also { it.writeBytes(bytes) }
}
