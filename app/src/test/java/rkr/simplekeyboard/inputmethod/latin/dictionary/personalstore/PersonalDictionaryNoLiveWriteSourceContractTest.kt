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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * E4a-2 introduces the write path but wires NOTHING into the live IME: no toggle, no merge, no
 * learning. Writing is exercised only from tests through the explicit store API, so in the running
 * app not a single word is ever written. This test proves it by source: neither `LatinIME` nor
 * `SuggestionsController` reference the store, and every main reference to the store class lives
 * inside its own package.
 */
class PersonalDictionaryNoLiveWriteSourceContractTest {
    private val latinIme by lazy {
        File(sourceRoot(), "java/rkr/simplekeyboard/inputmethod/latin/LatinIME.java").readText()
    }
    private val suggestionsController by lazy {
        File(sourceRoot(), "java/rkr/simplekeyboard/inputmethod/latin/suggestions/SuggestionsController.kt").readText()
    }

    @Test
    fun neitherTheImeNorTheSuggestionsControllerReferenceThePersonalStore() {
        for (marker in listOf(
            "PersonalDictionaryStore",
            "AndroidPersonalDictionaryStorage",
            "personalstore",
        )) {
            assertFalse("LatinIME references $marker", latinIme.contains(marker))
            assertFalse("SuggestionsController references $marker", suggestionsController.contains(marker))
        }
    }

    @Test
    fun everyMainReferenceToThePersonalStoreLivesInItsOwnPackage() {
        val javaRoot = File(sourceRoot(), "java")
        val offenders = javaRoot.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .filter { it.readText().contains("PersonalDictionaryStore") }
            .map { it.path }
            .toList()

        assertTrue("something references the store, so the class exists", offenders.isNotEmpty())
        val separator = File.separator
        val packagePath = "dictionary${separator}personalstore$separator"
        for (path in offenders) {
            assertTrue(
                "the store is referenced outside its own package: $path",
                path.contains(packagePath),
            )
        }
    }

    private fun sourceRoot(): File =
        listOf(File("src/main"), File("app/src/main")).firstOrNull(File::isDirectory)
            ?: error("cannot locate app/src/main from ${File(".").absolutePath}")
}
