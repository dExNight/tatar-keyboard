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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M4c source-contract, in the style of EmojiPanelSourceContractTest: it greps the frozen source
 * rather than exercising Android, guarding the hide-path reset that keeps the emoji search from
 * resurrecting as a dead band. The defect: the switcher's search/panel flags and the search view
 * survived a plain window hide (home), while the query was already dropped — the band came back
 * visible but swallowed nothing.
 */
class EmojiSearchHideResetSourceContractTest {

    private fun sourceRoot(): File {
        val candidates = listOf(File("src/main"), File("app/src/main"))
        return candidates.firstOrNull(File::isDirectory)
            ?: error("cannot locate app/src/main from ${File(".").absolutePath}")
    }

    private fun java(path: String) = File(sourceRoot(), "java/$path").readText()

    private val latinIme by lazy {
        java("rkr/simplekeyboard/inputmethod/latin/LatinIME.java")
    }
    private val keyboardSwitcher by lazy {
        java("rkr/simplekeyboard/inputmethod/keyboard/KeyboardSwitcher.java")
    }

    private fun onWindowHiddenBody() =
        latinIme.substringAfter("public void onWindowHidden()").substringBefore("void onFinishInputInternal()")

    @Test
    fun hidingTheWindowResetsTheEmojiSearch() {
        val body = onWindowHiddenBody()
        // The query is dropped AND the switcher surfaces/flags reset — one without the other is
        // exactly the dead band of M4c.
        assertTrue("onWindowHidden drops the query", body.contains("abandonEmojiSearch()"))
        assertTrue("onWindowHidden resets the switcher", body.contains("hideEmojiPanel()"))
    }

    @Test
    fun theSwitcherResetCoversBothTheSearchAndThePanel() {
        val body = keyboardSwitcher
            .substringAfter("public void hideEmojiPanel()")
            .substringBefore("public void onEmojiPanelBackToKeyboard()")
        assertTrue(body.contains("mEmojiSearchShown = false"))
        assertTrue(body.contains("hideEmojiSearch()"))
    }

    @Test
    fun anInputViewRecreationStillResetsTheFlags() {
        val body = keyboardSwitcher
            .substringAfter("public View onCreateInputView()")
        assertTrue(body.contains("mEmojiPanelShown = false"))
        assertTrue(body.contains("mEmojiSearchShown = false"))
    }
}
