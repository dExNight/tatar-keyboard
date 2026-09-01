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

package rkr.simplekeyboard.inputmethod.latin.emoji

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M4b source-contract, in the style of EmojiPanelSourceContractTest: emoji suggestions default ON
 * (subordinate to the master suggestions switch, which stays opt-in), and the reader default and
 * the settings-screen default must never drift apart — a drift is exactly how the feature shipped
 * invisible in 1.9.10.
 */
class EmojiSuggestDefaultSourceContractTest {

    private fun sourceRoot(): File {
        val candidates = listOf(File("src/main"), File("app/src/main"))
        return candidates.firstOrNull(File::isDirectory)
            ?: error("cannot locate app/src/main from ${File(".").absolutePath}")
    }

    private fun java(path: String) = File(sourceRoot(), "java/$path").readText()

    @Test
    fun theReaderDefaultsToOn() {
        val settings = java("rkr/simplekeyboard/inputmethod/latin/settings/Settings.java")
        assertTrue(
            settings.contains(
                "prefs.getBoolean(PREF_EMOJI_SUGGESTIONS, true)"
            )
        )
    }

    @Test
    fun theSettingsScreenShowsTheSameDefault() {
        val host = java("rkr/simplekeyboard/inputmethod/latin/settings/SettingsHostActivity.kt")
        assertTrue(host.contains("switchRow(Settings.PREF_EMOJI_SUGGESTIONS, true"))
        assertFalse(host.contains("switchRow(Settings.PREF_EMOJI_SUGGESTIONS, false"))
    }

    @Test
    fun theMasterSwitchStaysTheGate() {
        val settings = java("rkr/simplekeyboard/inputmethod/latin/settings/Settings.java")
        val body = settings
            .substringAfter("public static boolean readEmojiSuggestionsEnabled")
            .substringBefore("}")
        assertTrue("emoji suggestions stay subordinate", body.contains("readTatarSuggestionsEnabled"))
    }
}
