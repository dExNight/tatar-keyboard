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

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.PersonalDictionaryTestFixtures
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.PersonalDictionaryTestFixtures.Entry
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.PersonalSubtypes
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.TpersFormat

/**
 * Mission `tt-quarantine`, task 1: version 1.8.2 stopped destroying an unreadable personal
 * dictionary and started setting it aside — and then no code existed that could read the copy back.
 * The data was being kept for a recovery path that did not exist.
 *
 * The copy breaks, in practice, by losing its tail: a write cut short leaves a header and whole
 * records at the front and a stump at the end. So the reader here refuses the checksum's verdict on
 * purpose (truncation destroys the checksum first, and refusing on it refuses every copy there is)
 * and leans on the per-record contract instead. Two properties are asserted over and over, because
 * they are the two ways this can go wrong:
 *
 * - what reads, reads — words in front of the damage are not thrown away with it;
 * - what did not read is never passed off as read — [PersonalQuarantineSalvage.readToEnd] is the
 *   only thing allowed to say "nothing is known lost", and it says so only when that is true.
 */
class PersonalQuarantineSalvageTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val subtype = PersonalSubtypes.TATAR_RU

    // ---- the copy reads ------------------------------------------------------------------------

    /**
     * The control, and the reason the honesty flag is worth anything: a copy that is whole reads
     * whole and SAYS it read whole. Without this test, hardwiring `readToEnd = false` would pass
     * every other test in the file.
     */
    @Test
    fun aWholeCopyReadsWholeAndSaysSo() {
        val copy = copyOf(image(Entry("абыйлар"), Entry("бабай"), Entry("гүзәл")))

        val salvage = PersonalQuarantineSalvage.read(copy, subtype)

        assertNotNull(salvage)
        assertEquals(listOf("абыйлар", "бабай", "гүзәл"), salvage!!.rawForms)
        assertEquals(3, salvage.wordCount)
        assertTrue("nothing was lost, so nothing may be reported lost", salvage.readToEnd)
    }

    /** The raw form is what the user typed; the parallel normalized form is what dedup keys on. */
    @Test
    fun theCasingTheUserChoseSurvivesTheSalvage() {
        val copy = copyOf(image(Entry("Гүзәл")))

        val salvage = PersonalQuarantineSalvage.read(copy, subtype)!!

        assertEquals(listOf("Гүзәл"), salvage.rawForms)
        assertEquals(listOf("гүзәл"), salvage.normalizedForms)
    }

    // ---- partially readable: the case this exists for -------------------------------------------

    /**
     * Task 4, "частично читаемая копия". A write cut short in the middle of the last record. The
     * words before the cut are the user's words and they are still there; the stump is not a reason
     * to lose them.
     */
    @Test
    fun aCopyWhoseTailWasCutOffStillYieldsTheWordsInFrontOfTheCut() {
        val whole = image(Entry("абыйлар"), Entry("бабай"), Entry("гүзәл"))
        val copy = copyOf(whole.copyOf(whole.size - 4))

        val salvage = PersonalQuarantineSalvage.read(copy, subtype)!!

        assertEquals(listOf("абыйлар", "бабай"), salvage.rawForms)
        assertEquals(2, salvage.wordCount)
        assertFalse("part of the file is damaged and lost; the user must be told", salvage.readToEnd)
    }

    /** Cut so short that even the record header of the third entry is gone. Same rule. */
    @Test
    fun aCopyCutInsideARecordHeaderKeepsTheCompleteRecordsBeforeIt() {
        val whole = image(Entry("абыйлар"), Entry("бабай"), Entry("гүзәл"))
        val recordSize = TpersFormat.RECORD_HEADER_SIZE + "гүзәл".toByteArray().size
        val copy = copyOf(whole.copyOf(whole.size - recordSize + 2))

        val salvage = PersonalQuarantineSalvage.read(copy, subtype)!!

        assertEquals(listOf("абыйлар", "бабай"), salvage.rawForms)
        assertFalse(salvage.readToEnd)
    }

    /**
     * The header claiming more records than the payload holds is the same loss seen from the other
     * side: the count survived the truncation, the records did not.
     */
    @Test
    fun aHeaderThatPromisesMoreRecordsThanArePresentIsNotBelievedOverTheBytes() {
        val copy = copyOf(image(Entry("абыйлар"), Entry("бабай"), entryCountOverride = 5L))

        val salvage = PersonalQuarantineSalvage.read(copy, subtype)!!

        assertEquals(listOf("абыйлар", "бабай"), salvage.rawForms)
        assertFalse(salvage.readToEnd)
    }

    /** Bytes past the last declared record mean the file is not what its header describes. */
    @Test
    fun bytesLeftOverAfterTheLastRecordCountAsDamage() {
        val whole = image(Entry("абыйлар"))
        val copy = copyOf(whole + byteArrayOf(1, 2, 3))

        val salvage = PersonalQuarantineSalvage.read(copy, subtype)!!

        assertEquals(listOf("абыйлар"), salvage.rawForms)
        assertFalse(salvage.readToEnd)
    }

    /**
     * A copy larger than the writer could ever produce: read up to the format's own file cap and no
     * further. Refusing it outright would throw away readable words; reading it whole would let a
     * corrupt length field ask a budget phone for an array it does not have.
     */
    @Test
    fun aCopyBiggerThanTheFormatAllowsIsReadUpToTheCapAndNoFurther() {
        val whole = image(Entry("абыйлар"), Entry("бабай"))
        val copy = copyOf(whole + ByteArray(TpersFormat.MAX_FILE_SIZE.toInt()))

        val salvage = PersonalQuarantineSalvage.read(copy, subtype)!!

        assertEquals(listOf("абыйлар", "бабай"), salvage.rawForms)
        assertFalse(salvage.readToEnd)
    }

    // ---- the parse stops at the first broken record ---------------------------------------------

    /**
     * The ascending-order rule earns its keep here as a resync detector. A corrupted length byte
     * lands the cursor in the middle of the payload; if the misread bytes happen to decode as a
     * word, the first thing that shows is that it no longer sorts after the previous one. Whatever
     * the reason, the parse stops — it never appends what it did not understand.
     */
    @Test
    fun recordsOutOfOrderStopTheParseAtTheFirstOneThatDoesNotSortAfterItsPredecessor() {
        val copy = copyOf(image(Entry("гүзәл"), Entry("бабай"), Entry("абыйлар"), sort = false))

        val salvage = PersonalQuarantineSalvage.read(copy, subtype)!!

        assertEquals(listOf("гүзәл"), salvage.rawForms)
        assertFalse(salvage.readToEnd)
    }

    /** The same word twice is the ordering rule's other half: strictly ascending, so no duplicates. */
    @Test
    fun aRepeatedWordStopsTheParseInsteadOfEnteringTheListTwice() {
        val copy = copyOf(image(Entry("бабай"), Entry("бабай"), sort = false))

        val salvage = PersonalQuarantineSalvage.read(copy, subtype)!!

        assertEquals(listOf("бабай"), salvage.rawForms)
        assertFalse(salvage.readToEnd)
    }

    /** A corrupted length byte misaligns everything after it, so nothing after it is trusted. */
    @Test
    fun aCorruptedWordLengthByteStopsTheParseInsteadOfInventingWords() {
        val whole = image(Entry("абыйлар"), Entry("бабай"), Entry("гүзәл"))
        val broken = whole.copyOf()
        broken[TpersFormat.HEADER_SIZE] = (broken[TpersFormat.HEADER_SIZE] + 1).toByte()
        val copy = copyOf(broken)

        val salvage = PersonalQuarantineSalvage.read(copy, subtype)!!

        assertTrue(
            "whatever it read, it may not be words the user never saved",
            salvage.rawForms.all { it == "абыйлар" || it == "бабай" || it == "гүзәл" },
        )
        assertTrue("the misaligned tail is dropped", salvage.wordCount < 3)
        assertFalse(salvage.readToEnd)
    }

    /**
     * The content rules are not relaxed for a rescue. A record holding letters outside this
     * language's alphabet is a broken record like any other and stops the parse where it sits.
     */
    @Test
    fun aRecordOutsideTheAlphabetStopsTheParseWhereItSits() {
        val copy = copyOf(image(Entry("абыйлар"), Entry("ііі")))

        val salvage = PersonalQuarantineSalvage.read(copy, subtype)!!

        assertEquals(listOf("абыйлар"), salvage.rawForms)
        assertFalse(salvage.readToEnd)
    }

    /** A usage counter of zero cannot have been written by this app; the record is not trusted. */
    @Test
    fun aRecordWithAnImpossibleUsageCounterStopsTheParse() {
        val copy = copyOf(image(Entry("абыйлар"), Entry("бабай", count = 0), Entry("гүзәл")))

        val salvage = PersonalQuarantineSalvage.read(copy, subtype)!!

        assertEquals(listOf("абыйлар"), salvage.rawForms)
        assertFalse(salvage.readToEnd)
    }

    // ---- completely unreadable, and absent ------------------------------------------------------

    /**
     * Task 4, "полностью нечитаемая". A copy exists but its head is gone, so not one word can be
     * trusted. That is not the same answer as "there is no copy": the file is still there and the
     * screen still has to offer to remove it.
     */
    @Test
    fun aCopyWithNoRecognisableHeaderYieldsNoWordsButStillExists() {
        val copy = copyOf(ByteArray(200) { 0x5a })

        val salvage = PersonalQuarantineSalvage.read(copy, subtype)

        assertNotNull("the copy is present, it is simply unreadable", salvage)
        assertEquals(emptyList<String>(), salvage!!.rawForms)
        assertEquals(0, salvage.wordCount)
        assertFalse(salvage.readToEnd)
    }

    /** Shorter than a header: nothing to read, and nothing invented. */
    @Test
    fun aCopyShorterThanItsOwnHeaderYieldsNoWords() {
        val copy = copyOf(ByteArray(TpersFormat.HEADER_SIZE - 1))

        val salvage = PersonalQuarantineSalvage.read(copy, subtype)!!

        assertEquals(0, salvage.wordCount)
        assertFalse(salvage.readToEnd)
    }

    /** A header from another schema or format version is not this reader's file. */
    @Test
    fun aCopyFromAnotherSchemaOrFormatVersionYieldsNoWords() {
        val schema = copyOf(image(Entry("абыйлар"), schemaId = 2))
        val format = copyOf(image(Entry("абыйлар"), formatVersion = 2))
        val size = copyOf(image(Entry("абыйлар"), headerSize = 64))
        val stamp = copyOf(image(Entry("абыйлар"), magic = "TATDICT "))

        assertEquals(0, PersonalQuarantineSalvage.read(schema, subtype)!!.wordCount)
        assertEquals(0, PersonalQuarantineSalvage.read(format, subtype)!!.wordCount)
        assertEquals(0, PersonalQuarantineSalvage.read(size, subtype)!!.wordCount)
        assertEquals(0, PersonalQuarantineSalvage.read(stamp, subtype)!!.wordCount)
    }

    /**
     * Task 4, "копия отсутствует". Null, and distinguishable from an unreadable copy: with no file
     * there is nothing to restore and nothing to delete, so the screen shows no card at all.
     */
    @Test
    fun anAbsentCopyIsNotTheSameAnswerAsAnUnreadableOne() {
        val folder = temporaryFolder.newFolder()
        val missing = File(folder, "personal-tt_RU-s1-f1.tpers.quarantine")

        assertNull(PersonalQuarantineSalvage.read(missing, subtype))
        assertNull("a directory is not a copy either", PersonalQuarantineSalvage.read(folder, subtype))
    }

    // ---- one language never gets another's words ------------------------------------------------

    /**
     * The language tag is not negotiable. Personal dictionaries are per language on purpose, and a
     * rescue is not an excuse to move a Russian word into the Tatar list, however readable it is.
     */
    @Test
    fun wordsSavedInAnotherLanguageAreNeverRestoredIntoThisOne() {
        val russian = copyOf(image(Entry("бабушка"), subtypeTag = PersonalSubtypes.RUSSIAN))

        assertEquals(0, PersonalQuarantineSalvage.read(russian, subtype)!!.wordCount)
        assertEquals(
            "and the same copy read as its own language does yield the word",
            1, PersonalQuarantineSalvage.read(russian, PersonalSubtypes.RUSSIAN)!!.wordCount,
        )
    }

    /** A subtype with no declared alphabet has the feature off entirely — including the rescue. */
    @Test
    fun aSubtypeWithNoAlphabetSalvagesNothing() {
        val copy = copyOf(image(Entry("абыйлар"), subtypeTag = "de"))

        assertEquals(0, PersonalQuarantineSalvage.read(copy, "de")!!.wordCount)
    }

    // ---- and it never throws --------------------------------------------------------------------

    /**
     * Decision rule 3 of the mission: parsing a broken file has no right to end the process. Every
     * single-byte corruption of a valid copy is fed through the reader; the assertion is simply that
     * it answers. In production this runs on the store's worker thread, whose uncaught handler is
     * the one that kills the IME.
     */
    @Test
    fun noSingleByteCorruptionCanMakeTheReaderThrow() {
        val whole = image(Entry("абыйлар"), Entry("бабай"), Entry("гүзәл"))
        val copy = copyOf(whole)

        for (position in whole.indices) {
            for (mask in intArrayOf(0x01, 0x7f, 0x80, 0xff)) {
                val broken = whole.copyOf()
                broken[position] = (broken[position].toInt() xor mask).toByte()
                copy.writeBytes(broken)
                // Any throw fails the test by escaping; the reader must answer for every input.
                PersonalQuarantineSalvage.read(copy, subtype)
            }
        }
    }

    // ---- helpers --------------------------------------------------------------------------------

    private fun image(
        vararg entries: Entry,
        subtypeTag: String = subtype,
        sort: Boolean = true,
        schemaId: Int = TpersFormat.SCHEMA_ID,
        formatVersion: Int = TpersFormat.FORMAT_VERSION,
        headerSize: Int = TpersFormat.HEADER_SIZE,
        magic: String = TpersFormat.MAGIC,
        entryCountOverride: Long? = null,
    ): ByteArray = PersonalDictionaryTestFixtures.build(
        entries = entries.toList(),
        subtypeTag = subtypeTag,
        sort = sort,
        schemaId = schemaId,
        formatVersion = formatVersion,
        headerSize = headerSize,
        magic = magic,
        entryCountOverride = entryCountOverride,
    )

    /** Writes [bytes] where the store would have set the copy aside, and hands back the file. */
    private fun copyOf(bytes: ByteArray): File =
        File(temporaryFolder.newFolder(), "personal-$subtype-s1-f1.tpers.quarantine")
            .also { it.writeBytes(bytes) }
}
