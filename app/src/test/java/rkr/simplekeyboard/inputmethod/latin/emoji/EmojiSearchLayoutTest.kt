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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The caret must sit at the end of the typed query — the first of the two defects the operator
 * found on a real phone in 1.6.0.
 *
 * The pure half is checked directly; the half that lives inside `onDraw` is checked the way the
 * rest of this package checks drawing code — by grepping the frozen source, in the style of
 * [EmojiPanelSourceContractTest].
 */
class EmojiSearchLayoutTest {

    private fun sourceRoot(): File {
        val candidates = listOf(File("src/main"), File("app/src/main"))
        return candidates.firstOrNull(File::isDirectory)
            ?: error("cannot locate app/src/main from ${File(".").absolutePath}")
    }

    private val view by lazy {
        File(sourceRoot(), "java/rkr/simplekeyboard/inputmethod/latin/emoji/EmojiSearchView.kt")
            .readText()
    }

    private fun bodyOf(start: String, end: String) =
        view.substringAfter(start).substringBefore(end)

    /** Every argument list of [call] in [source], collapsed to one line each; nesting survives. */
    private fun argumentsOf(source: String, call: String): List<String> {
        val calls = ArrayList<String>()
        var from = source.indexOf(call)
        while (from >= 0) {
            var depth = 0
            var index = from + call.length - 1
            while (index < source.length) {
                when (source[index]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) break
                    }
                }
                index++
            }
            calls.add(
                source.substring(from + call.length, index)
                    .replace(Regex("\\s+"), " ")
                    .trim(),
            )
            from = source.indexOf(call, index)
        }
        return calls
    }

    // --- The pure rules -----------------------------------------------------------------------

    /**
     * The caret is exactly at the right edge of the drawn text. 1.6.0 added `closeCrossPx` (5dp)
     * here, so at the default density the caret stood 5px past the text and the operator read the
     * gap as a trailing space.
     */
    @Test
    fun theCaretXIsTheRightEdgeOfTheTextAndNothingElse() {
        assertEquals(65f, EmojiSearchLayout.caretX(40f, 25f), 0f)
        // An empty query: the caret sits where the text would start, against the hint.
        assertEquals(40f, EmojiSearchLayout.caretX(40f, 0f), 0f)
        // Whatever the text width, the caret moves with it one-to-one and gains nothing.
        var width = 0f
        while (width < 400f) {
            assertEquals(40f + width, EmojiSearchLayout.caretX(40f, width), 0f)
            width += 7.5f
        }
    }

    // --- Defect 1: the caret sits at the end of the text ---------------------------------------

    /**
     * 1.6.0 drew the caret at `textLeft + measureText(queryText) + closeCrossPx`, where
     * `closeCrossPx` is the half-size of the "✕" key at the other end of the pill. The operator
     * read the resulting 5dp gap as a trailing space. Nothing but the text width may enter the
     * caret's x.
     */
    @Test
    fun theCaretIsDrawnAtTheEndOfTheQueryAndNothingIsAddedToIt() {
        val queryRow = bodyOf("private fun drawQueryRow", "private fun drawCaret")
        val caretCalls = argumentsOf(queryRow, "drawCaret(")
        assertTrue("expected two drawCaret calls, found ${caretCalls.size}", caretCalls.size == 2)
        for (call in caretCalls) {
            assertFalse(
                "the caret x still carries the close-key constant: $call",
                call.contains("closeCrossPx"),
            )
            assertFalse(
                "the caret x carries a padding constant: $call",
                call.contains("InsetPx") && !call.contains("textLeft"),
            )
        }
        // The typed-text branch positions the caret through the pure helper, so the rule is
        // testable without a device.
        assertTrue(
            "the typed-text caret must go through EmojiSearchLayout.caretX",
            caretCalls.any { it.contains("EmojiSearchLayout.caretX") },
        )
    }

    /** The close-key constant is used only by the close key itself. */
    @Test
    fun theCloseCrossConstantIsUsedOnlyByTheCloseKey() {
        val queryRow = bodyOf("private fun drawQueryRow", "private fun drawCaret")
        val closeSection = queryRow.substringAfter("// The close key sits inside")
        val outsideCloseKey = queryRow.substringBefore("// The close key sits inside")
        assertFalse(
            "closeCrossPx is used before the close key is drawn",
            outsideCloseKey.contains("closeCrossPx"),
        )
        assertTrue(closeSection.contains("closeCrossPx"))
    }
}
