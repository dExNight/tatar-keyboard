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

import kotlin.math.abs

/**
 * Pure, Android-free scroll-bound arithmetic and the fling-or-tap decision.
 *
 * The [EmojiPanelView] owns the single `OverScroller` and the single `VelocityTracker`; everything
 * that decides *whether* a released gesture flings, and everything that clamps a scroll offset into
 * range, lives here so the physics boundary can be exercised on the plain JVM without a device.
 */
internal object EmojiFling {

    /** Clamps [target] into the inclusive scroll range `0..maxScroll`. */
    fun clampScroll(target: Int, maxScroll: Int): Int {
        if (maxScroll <= 0) return 0
        if (target < 0) return 0
        if (target > maxScroll) return maxScroll
        return target
    }

    /**
     * True when a released drag should fling rather than settle in place. A gesture flings only when
     * it was already scrolling, the content can move at all ([maxScroll] > 0), and the release speed
     * clears the platform's minimum fling velocity.
     */
    fun shouldFling(wasScrolling: Boolean, velocityY: Float, minFlingVelocity: Int, maxScroll: Int): Boolean {
        if (!wasScrolling || maxScroll <= 0) return false
        return abs(velocityY) >= minFlingVelocity
    }

    /**
     * True when a release should be treated as a tap on its target: the gesture never crossed the
     * touch slop into a scroll, so there is nothing to fling and the target under the finger acts.
     */
    fun isTap(wasScrolling: Boolean): Boolean = !wasScrolling
}
