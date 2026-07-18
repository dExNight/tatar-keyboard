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

package rkr.simplekeyboard.inputmethod.accessibility

import android.graphics.Rect
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import rkr.simplekeyboard.inputmethod.keyboard.Key
import rkr.simplekeyboard.inputmethod.keyboard.KeyDetector
import rkr.simplekeyboard.inputmethod.keyboard.MainKeyboardView
import rkr.simplekeyboard.inputmethod.keyboard.internal.KeyboardIconsSet

/**
 * ExploreByTouchHelper skeleton: exposes each keyboard key as a virtual accessibility node.
 * Virtual view id = index of the key in keyboard.getSortedKeys(). Hover-only — the touch
 * path (onTouchEvent/PointerTracker) is untouched. Full TalkBack input and Tatar-specific
 * letter descriptions (A11Y-02) are phase 9.
 */
class KeyboardAccessibilityDelegate(
    private val keyboardView: MainKeyboardView,
    private val keyDetector: KeyDetector,
) : ExploreByTouchHelper(keyboardView) {

    private val tempBounds = Rect()

    private fun sortedKeys(): List<Key> =
        keyboardView.keyboard?.sortedKeys ?: emptyList()

    override fun getVirtualViewAt(x: Float, y: Float): Int {
        val key = keyDetector.detectHitKey(x.toInt(), y.toInt()) ?: return INVALID_ID
        val index = sortedKeys().indexOf(key)
        return if (index >= 0) index else INVALID_ID
    }

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        val keys = sortedKeys()
        for (i in keys.indices) {
            if (!keys[i].isSpacer) {
                virtualViewIds.add(i)
            }
        }
    }

    override fun onPopulateNodeForVirtualView(
        virtualViewId: Int,
        node: AccessibilityNodeInfoCompat,
    ) {
        val keys = sortedKeys()
        val key = keys.getOrNull(virtualViewId)
        if (key == null || key.isSpacer) {
            // The helper requires non-empty content and bounds even for stale ids.
            node.contentDescription = ""
            tempBounds.set(0, 0, 1, 1)
            node.setBoundsInParent(tempBounds)
            return
        }
        node.contentDescription =
            key.label ?: KeyboardIconsSet.getIconName(key.iconId)
        tempBounds.set(
            key.x + keyboardView.paddingLeft,
            key.y + keyboardView.paddingTop,
            key.x + keyboardView.paddingLeft + key.width,
            key.y + keyboardView.paddingTop + key.height,
        )
        node.setBoundsInParent(tempBounds)
        node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
    }

    override fun onPerformActionForVirtualView(
        virtualViewId: Int,
        action: Int,
        arguments: android.os.Bundle?,
    ): Boolean {
        // Input via accessibility actions lands in phase 9.
        return false
    }
}
