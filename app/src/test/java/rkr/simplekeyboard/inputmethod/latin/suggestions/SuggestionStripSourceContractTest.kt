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

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionStripSourceContractTest {
    @Test
    fun oneLazyCanvasViewAndBothLayoutsPreserveGoneBaseline() {
        val main = sourceRoot()
        val normalLayout = File(main, "res/layout/input_view.xml").readText()
        val v28Layout = File(main, "res/layout-v28/input_view.xml").readText()
        val stripLayout = File(main, "res/layout/suggestion_strip.xml").readText()

        for (layout in listOf(normalLayout, v28Layout)) {
            assertTrue(layout.contains("<ViewStub"))
            assertTrue(layout.contains("@layout/suggestion_strip"))
            assertTrue(layout.contains("android:layout_height=\"40dp\""))
            assertEquals(1, "<rkr.simplekeyboard.inputmethod.keyboard.MainKeyboardView".toRegex()
                .findAll(layout).count())
        }
        assertEquals(1, "SuggestionStripView".toRegex().findAll(stripLayout).count())
        assertTrue(stripLayout.contains("android:visibility=\"gone\""))
        assertFalse(stripLayout.contains("<Button"))
        assertFalse(stripLayout.contains("<TextView"))
    }

    @Test
    fun hotDrawAndTouchBodiesContainNoKnownAllocationSites() {
        val viewSource = File(
            sourceRoot(),
            "java/rkr/simplekeyboard/inputmethod/latin/suggestions/SuggestionStripView.kt",
        ).readText()
        val drawBody = viewSource.substringAfter("override fun onDraw")
            .substringBefore("@Suppress(\"ClickableViewAccessibility\")")
        val touchBody = viewSource.substringAfter("override fun onTouchEvent")
            .substringBefore("override fun dispatchHoverEvent")

        listOf(drawBody, touchBody).forEach { hotBody ->
            listOf("= Rect(", "= Paint(", "MotionEvent.obtain", "intArrayOf(", "TextUtils.", ".toString()")
                .forEach { forbidden -> assertFalse("hot path contains $forbidden", hotBody.contains(forbidden)) }
        }
        assertTrue(viewSource.contains("VIRTUAL_ID_LEFT = 0"))
        assertTrue(viewSource.contains("VIRTUAL_ID_CENTER = 1"))
        assertTrue(viewSource.contains("VIRTUAL_ID_RIGHT = 2"))
        assertTrue(viewSource.contains("ExploreByTouchHelper"))
    }

    @Test
    fun touchDispatchTracksActivePointerAndHandlesPointerUpExplicitly() {
        val viewSource = File(
            sourceRoot(),
            "java/rkr/simplekeyboard/inputmethod/latin/suggestions/SuggestionStripView.kt",
        ).readText()
        val touchBody = viewSource.substringAfter("override fun onTouchEvent")
            .substringBefore("override fun dispatchHoverEvent")

        assertTrue(touchBody.contains("event.findPointerIndex(state.activePointerId())"))
        assertTrue(touchBody.contains("MotionEvent.ACTION_POINTER_UP"))
        assertTrue(touchBody.contains("state.onPointerUp(event.getPointerId(event.actionIndex))"))
        assertTrue(touchBody.contains("event.getPointerId(pointerIndex)"))
    }

    @Test
    fun detachClearsSuggestionsListenerAndRejectsStaleAccessibilityActions() {
        val main = sourceRoot()
        val viewSource = File(
            main,
            "java/rkr/simplekeyboard/inputmethod/latin/suggestions/SuggestionStripView.kt",
        ).readText()
        val inputViewSource = File(
            main,
            "java/rkr/simplekeyboard/inputmethod/latin/InputView.java",
        ).readText()
        val releaseBody = viewSource.substringAfter("fun release()")
            .substringBefore("fun getSuggestion")
        val detachBody = viewSource.substringAfter("override fun onDetachedFromWindow()")
            .substringBefore("private fun activateCell")
        val actionableBody = viewSource.substringAfter("private fun isVirtualCellActionable")
            .substringBefore("companion object")

        assertTrue(releaseBody.contains("state.clear()"))
        assertTrue(releaseBody.contains("listener = null"))
        assertTrue(releaseBody.contains("visibility = GONE"))
        assertTrue(releaseBody.contains("accessibilityHelper.invalidateRoot()"))
        assertTrue(detachBody.contains("release()"))
        assertTrue(actionableBody.contains("ViewCompat.isAttachedToWindow"))
        assertTrue(actionableBody.contains("isShown"))
        assertTrue(actionableBody.contains("state.isCellPopulated(virtualViewId)"))
        assertTrue(inputViewSource.contains("mSuggestionStripView.release()"))
        assertTrue(inputViewSource.contains("mInsetsChangedListener = null"))
    }

    @Test
    fun insetsUseCombinedBoundsAndKeepMoreKeysAndHardwareGuards() {
        val javaRoot = File(sourceRoot(), "java/rkr/simplekeyboard/inputmethod/latin")
        val inputView = File(javaRoot, "InputView.java").readText()
        val latinIme = File(javaRoot, "LatinIME.java").readText()

        assertTrue(inputView.contains("outBounds.union(mTemporaryBounds)"))
        assertTrue(inputView.contains("strip.isShown()"))
        assertTrue(latinIme.contains("getVisibleInputBounds"))
        assertTrue(latinIme.contains("showingMoreKeys ? 0 : visibleTopY"))
        assertTrue(latinIme.contains("isImeSuppressedByHardwareKeyboard()"))
        assertTrue(latinIme.contains("outInsets.touchableRegion.setEmpty()"))
    }

    @Test
    fun suggestionsAreAnnouncedOnlyOnTheEmptyToPopulatedTransition() {
        val viewSource = File(
            sourceRoot(),
            "java/rkr/simplekeyboard/inputmethod/latin/suggestions/SuggestionStripView.kt",
        ).readText()
        val setSuggestionsBody = viewSource.substringAfter("fun setSuggestions(")
            .substringBefore("fun clearSuggestions()")

        // The triple changes on every keystroke: announcing each one would bury the key echo.
        assertTrue(setSuggestionsBody.contains("val hadSuggestions = state.hasAnySuggestion()"))
        assertTrue(
            setSuggestionsBody.contains(
                "if (hadSuggestions || !accessibilityManager.isTouchExplorationEnabled) return",
            ),
        )
        assertTrue(setSuggestionsBody.contains("announceForAccessibility"))
        assertTrue(setSuggestionsBody.contains("R.string.spoken_suggestions_available"))
    }

    @Test
    fun internalCursorGesturesNotifyTheControllerDirectly() {
        val latinIme = File(
            sourceRoot(),
            "java/rkr/simplekeyboard/inputmethod/latin/LatinIME.java",
        ).readText()
        // onUpdateSelection() cannot cover these: the connection keeps the expected selection in
        // sync (so the move is not "external") and it returns early while the gesture runs.
        val gestureBodies = listOf(
            latinIme.substringAfter("public void onMoveCursorPointer")
                .substringBefore("public void onMoveDeletePointer"),
            latinIme.substringAfter("public void onMoveDeletePointer")
                .substringBefore("public void onUpWithDeletePointerActive"),
            latinIme.substringAfter("public void onUpWithDeletePointerActive")
                .substringBefore("public void onUpWithSpacePointerActive"),
            latinIme.substringAfter("public void onUpWithSpacePointerActive")
                .substringBefore("/**"),
        )
        gestureBodies.forEach {
            assertTrue(it.contains("onSuggestionsAffectingCursorMove()"))
        }
        assertTrue(latinIme.contains("mSuggestionsController.onSelectionChanged()"))
    }

    @Test
    fun commitPathRefusesToReplaceAWordTheCursorSitsInside() {
        val javaRoot = File(sourceRoot(), "java/rkr/simplekeyboard/inputmethod/latin")
        val inputLogic = File(javaRoot, "inputlogic/InputLogic.java").readText()
        val connection = File(javaRoot, "RichInputConnection.java").readText()
        val commitBody = inputLogic.substringAfter("public boolean commitChosenSuggestion")
            .substringBefore("private boolean layoutUsesAutoCaps")

        // Fail-closed second line of defense, before any edit reaches the editor.
        assertTrue(
            commitBody.indexOf("startsWithWordCharacter(mConnection.getCachedTextAfterCursor())")
                in 0 until commitBody.indexOf("deleteTextBeforeCursor"),
        )
        // The right-hand context comes from the local cache, never from IPC, and is never logged.
        assertTrue(connection.contains("public CharSequence getCachedTextAfterCursor()"))
        assertTrue(
            connection.substringAfter("public CharSequence getCachedTextAfterCursor()")
                .substringBefore("}")
                .contains("Do not log the returned value"),
        )
    }

    private fun sourceRoot(): File {
        val candidates = listOf(File("src/main"), File("app/src/main"))
        return candidates.firstOrNull(File::isDirectory)
            ?: error("cannot locate app/src/main from ${File(".").absolutePath}")
    }
}
