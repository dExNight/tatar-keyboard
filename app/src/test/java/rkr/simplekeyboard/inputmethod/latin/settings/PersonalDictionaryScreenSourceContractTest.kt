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

package rkr.simplekeyboard.inputmethod.latin.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.PersonalDictionary
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.ValidatedPersonalDictionary

/**
 * The "Personal dictionary" screen, one test per guarantee the contract names: all languages, a cap
 * of 200 materialized rows, "showing N of M", `FLAG_SECURE` on the whole Activity and the three
 * privacy flags on BOTH text fields.
 *
 * The list logic is exercised for real (it is pure Kotlin); the Activity parts are source-contract,
 * in the established style, because `SettingsHostActivity` cannot run off-device.
 */
class PersonalDictionaryScreenSourceContractTest {

    private fun sourceRoot(): File {
        val candidates = listOf(File("src/main"), File("app/src/main"))
        return candidates.firstOrNull(File::isDirectory)
            ?: error("cannot locate app/src/main from ${File(".").absolutePath}")
    }

    private val host by lazy {
        File(sourceRoot(),
            "java/rkr/simplekeyboard/inputmethod/latin/settings/SettingsHostActivity.kt").readText()
    }

    private fun dictionaryOf(vararg words: String): PersonalDictionary {
        val sorted = words.sorted()
        return PersonalDictionary.of(
            ValidatedPersonalDictionary(
                rawForms = sorted,
                normalizedForms = sorted.map { it.lowercase() },
                usageCounts = IntArray(sorted.size) { 1 },
                lastUseSerials = LongArray(sorted.size) { (it + 1).toLong() },
                subtypeTag = "tt_RU",
            ),
        )
    }

    // --- The list model ---------------------------------------------------------------------

    @Test
    fun wordsOfEveryLanguageAreShownGroupedByLanguage() {
        val content = PersonalDictionaryScreenModel.build(
            listOf("tt_RU" to dictionaryOf("гүзәлия"), "ru_RU" to dictionaryOf("зәйнәп")),
            query = "",
        )
        assertEquals(2, content.sections.size)
        assertEquals("tt_RU", content.sections[0].subtypeId)
        assertEquals("ru_RU", content.sections[1].subtypeId)
        assertEquals(2, content.shownCount)
    }

    @Test
    fun noMoreThanTwoHundredRowsAreMaterializedWhateverTheNumberOfLanguages() {
        val first = dictionaryOf(*Array(150) { "аа%03d".format(it) })
        val second = dictionaryOf(*Array(150) { "бб%03d".format(it) })
        val content = PersonalDictionaryScreenModel.build(
            listOf("tt_RU" to first, "ru_RU" to second), query = "",
        )
        assertEquals(PersonalDictionaryScreenModel.MAX_MATERIALIZED_ROWS, content.shownCount)
        assertEquals(300, content.totalCount)
        assertTrue("the screen must be able to say it is showing a part", content.isTruncated)
        assertEquals("the cap is shared across languages, not per language",
            200, content.sections.sumOf { it.rows.size })
    }

    @Test
    fun theSearchNarrowsTheListBeforeAnyViewExists() {
        val content = PersonalDictionaryScreenModel.build(
            listOf("tt_RU" to dictionaryOf("гүзәлия", "зәйнәп", "гүзәлбану")),
            query = "гүзәл",
        )
        assertEquals(2, content.shownCount)
        assertEquals("the total counts matches, not the whole dictionary", 2, content.totalCount)
        assertFalse(content.isTruncated)
    }

    @Test
    fun theSearchMatchesOnTheNormalizedFormButTheRowKeepsTheSavedSpelling() {
        val content = PersonalDictionaryScreenModel.build(
            listOf("tt_RU" to dictionaryOf("Гүзәлия")), query = "ГҮЗӘЛ",
        )
        assertEquals(1, content.shownCount)
        assertEquals("Гүзәлия", content.sections[0].rows[0].rawForm)
    }

    @Test
    fun anEmptyDictionaryProducesNoSectionsAtAll() {
        val content = PersonalDictionaryScreenModel.build(
            listOf("tt_RU" to PersonalDictionary.EMPTY), query = "",
        )
        assertTrue(content.sections.isEmpty())
        assertEquals(0, content.totalCount)
        assertFalse(content.isTruncated)
    }

    // --- The Activity -----------------------------------------------------------------------

    @Test
    fun flagSecureIsSetOnceForTheWholeActivity() {
        val onCreate = host.substringAfter("override fun onCreate(").substringBefore("override fun onStart(")
        assertTrue("FLAG_SECURE is set in onCreate",
            onCreate.contains("window.setFlags(WindowManager.LayoutParams.FLAG_SECURE"))
        assertFalse("it must never be cleared while navigating between screens",
            host.contains("clearFlags(WindowManager.LayoutParams.FLAG_SECURE"))
        assertEquals("set exactly once, not per screen", 1,
            Regex("setFlags\\(WindowManager\\.LayoutParams\\.FLAG_SECURE").findAll(host).count())
    }

    @Test
    fun bothTextFieldsCarryTheThreePrivacyFlags() {
        val flags = host.substringAfter("private fun applyPrivateInputFlags(")
            .substringBefore("private fun textInputRow(")
        assertTrue(flags.contains("EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING"))
        assertTrue(flags.contains("EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS"))
        assertTrue(flags.contains("View.IMPORTANT_FOR_AUTOFILL_NO"))

        // Every place that inflates the text-input row must run them through that one function.
        val inflations = Regex("R\\.layout\\.row_text_input").findAll(host).count()
        val applications = Regex("applyPrivateInputFlags\\(field\\)").findAll(host).count()
        assertEquals("each of the two fields — search and add-word — applies the flags",
            inflations, applications)
        assertEquals(2, inflations)
    }

    @Test
    fun theShownOfTotalRowExistsAndOnlyAppearsWhenTheListIsTrimmed() {
        assertTrue(host.contains("if (content.isTruncated)"))
        assertTrue(host.contains("R.string.personal_dictionary_shown_of_total"))
        val english = File(sourceRoot(), "res/values/strings.xml").readText()
        assertTrue("the string names both numbers", english
            .substringAfter("<string name=\"personal_dictionary_shown_of_total\">")
            .substringBefore("</string>")
            .let { it.contains("%1\$d") && it.contains("%2\$d") })
    }

    @Test
    fun theSearchTextDoesNotTravelThroughTheSavedInstanceState() {
        val saveState = host.substringAfter("override fun onSaveInstanceState(")
            .substringBefore("override fun onBackPressed(")
        assertFalse("a fragment of a personal word must not go into a Bundle bound for system_server",
            saveState.contains("personalSearchQuery"))
        assertTrue("and the field itself is plain transient state",
            host.contains("private var personalSearchQuery: String = \"\""))
    }

    @Test
    fun theScreenWorksWithTheSettingOffButAddingFollowsTheSetting() {
        assertTrue("the entry row does not depend on the toggle",
            host.contains("addCard(listOf(linkRow(R.string.personal_dictionary_screen)"))
        assertTrue("only the add row follows the setting", host.contains(
            "setRowEnabled(addRow, Settings.readPersonalDictionaryEnabled(prefs)"))
        val screen = host.substringAfter("private fun buildPersonalDictionaryScreen()")
            .substringBefore("private fun personalSubtypeIds()")
        assertTrue("erasing is offered regardless of the toggle",
            screen.contains("R.string.personal_dictionary_erase_all"))
        assertFalse("no early return hides the screen when the setting is off",
            screen.contains("if (!Settings.readPersonalDictionaryEnabled(prefs)) return"))
    }
}
