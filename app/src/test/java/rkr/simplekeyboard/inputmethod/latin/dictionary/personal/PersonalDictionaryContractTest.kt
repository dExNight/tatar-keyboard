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

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The «Контракт личного словаря» section introduced with E4b, one test per point that is testable
 * today: WHAT is stored, that storage is keyed by subtype, that there are exactly the declared write
 * paths, and that the gates which must exist do exist.
 *
 * The points about the screen live in `PersonalDictionaryScreenSourceContractTest`, the one about
 * the ranking in `CompositePrefixComputerTest`, the settings ones in
 * `PersonalDictionarySettingsTest` and erasure in `PersonalDictionaryErasureTest`.
 */
class PersonalDictionaryContractTest {

    private fun sourceRoot(): File {
        val candidates = listOf(File("src/main"), File("app/src/main"))
        return candidates.firstOrNull(File::isDirectory)
            ?: error("cannot locate app/src/main from ${File(".").absolutePath}")
    }

    private fun mainFiles(): List<File> =
        File(sourceRoot(), "java").walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .toList()

    @Test
    fun aRecordCarriesTheWordAndTwoNumbersAndNothingElse() {
        // 1 byte word length + 2 bytes usage count + 4 bytes last-use serial. No timestamp, no
        // context, no field or application identifier — there is physically nowhere to put one.
        assertEquals(7, TpersFormat.RECORD_HEADER_SIZE)

        // The header is fully accounted for by its declared fields, so it carries nothing else:
        // magic 8 + schema 2 + version 2 + headerSize 2 + checksumAlg 2 + entryCount 4 +
        // payloadSize 4 + subtypeTag 16 + checksum 32 = 72.
        val accounted = TpersFormat.MAGIC_SIZE + 2 + 2 + 2 + 2 + 4 + 4 +
            TpersFormat.SUBTYPE_TAG_SIZE + TpersFormat.CHECKSUM_SIZE
        assertEquals(TpersFormat.HEADER_SIZE, accounted)
    }

    @Test
    fun storageIsKeyedBySubtypeSoLanguagesCannotMix() {
        val tatar = TpersFormat.personalFileName("tt_RU")
        val russian = TpersFormat.personalFileName("ru_RU")
        assertNotEquals("one file per language", tatar, russian)
        assertTrue("the language tag is part of the name", tatar.contains("tt_RU"))
        // The tag also lives INSIDE the file, so a file of another language is rejected rather than
        // read as this one's — pinned by PersonalSubtypeSeamTest/TpersValidatorTest.
        assertTrue(TpersFormat.SUBTYPE_TAG_SIZE > 0)
    }

    @Test
    fun theOnlyWayToCreateAnEntryTodayIsTheExplicitManualAdd() {
        val creators = mainFiles().filter { it.readText().contains("addManually(") }
            .map { it.name }
            .sorted()
        assertEquals(
            "exactly two files: the store that defines it and the screen controller that calls it",
            listOf("PersonalDictionaryScreenController.kt", "PersonalDictionaryStore.kt"),
            creators,
        )

        // Learning is E4c and must not have leaked in early: nothing may create an entry as a
        // CONSEQUENCE of typing yet.
        val store = File(sourceRoot(),
            "java/rkr/simplekeyboard/inputmethod/latin/dictionary/personalstore/PersonalDictionaryStore.kt")
            .readText()
        assertFalse("learning arrives in E4c, not here", store.contains("fun learn("))
        assertFalse("nor the clean-run counter it needs", store.contains("noteCompletion("))
    }

    @Test
    fun theUnlockGateExistsAndFailsClosed() {
        val store = File(sourceRoot(),
            "java/rkr/simplekeyboard/inputmethod/latin/dictionary/personalstore/PersonalDictionaryStore.kt")
            .readText()
        assertTrue("every open honours the gate", store.contains("if (!unlockGate()) return false"))
        val android = File(sourceRoot(),
            "java/rkr/simplekeyboard/inputmethod/latin/dictionary/personalstore/AndroidPersonalDictionaryStorage.kt")
            .readText()
        assertTrue("production reads the real lock state",
            android.contains("userManager?.isUserUnlocked ?: false"))
        assertTrue("and a missing service means locked, not open",
            android.contains("?: false"))
    }

    @Test
    fun readingIsGatedByTheSettingOnEveryLookupRatherThanAtEngineStart() {
        val holder = File(sourceRoot(),
            "java/rkr/simplekeyboard/inputmethod/latin/dictionary/personalstore/PersonalDictionaries.kt")
            .readText()
        assertTrue("the snapshot supplier consults the gate each time it is asked",
            holder.contains("if (gate.isOn()) store.snapshot else PersonalDictionary.EMPTY"))
        val ime = File(sourceRoot(),
            "java/rkr/simplekeyboard/inputmethod/latin/LatinIME.java").readText()
        assertTrue("the gate reads the live preference",
            ime.contains("Settings.readPersonalDictionaryEnabled(mDevicePrefs)"))
    }

    @Test
    fun nothingLeavesTheDeviceAndNoExportPathExists() {
        val personalFiles = mainFiles().filter {
            it.path.contains("dictionary/personal") || it.path.contains("dictionary\\personal")
        }
        assertTrue(personalFiles.isNotEmpty())
        for (file in personalFiles) {
            val text = file.readText()
            for (marker in listOf("java.net.", "HttpURL", "Socket", "export", "upload", "sync(")) {
                assertFalse("${file.name} carries '$marker'", text.contains(marker))
            }
        }
    }
}
