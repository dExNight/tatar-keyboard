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

package rkr.simplekeyboard.inputmethod.keyboard.internal

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [BogusMoveEventDetector] must measure the distance a *finger* travelled, and nothing else.
 *
 * The detector is AOSP's hack for tablets whose touch screens emit a spurious down-move-up burst:
 * once the path accumulated since the down point passes 0.53 key diagonals and the movement is
 * more horizontal than vertical, `PointerTracker.isMajorEnoughMoveToBeOnNewKey` lets the pointer
 * leave its key even though the key's own hysteresis says it should not.
 *
 * Two things used to be counted into that path without a finger moving at all:
 *
 *  * **The tail of the previous gesture.** Trackers are pooled per pointer id and reused, so at
 *    the moment a fresh press goes down, `PointerTracker.mLastX/mLastY` still hold the point where
 *    the *previous* press ended. `onDownKey` reset the accumulator and only then fed the detector
 *    that distance, so every gesture started with the whole width of the keyboard already banked.
 *  * **A keyboard height change.** `MainKeyboardView` is bottom aligned, so replacing the pressed
 *    keyboard with one of a different height moves the origin of the touch coordinates. The Tatar
 *    alphabet keyboard carries a fifth row for ә ө ү җ ң һ and measures 728px against the symbols
 *    keyboard's 667px on the AVD `tatar_e5_test`, so pressing `?123` shifts every following
 *    coordinate by 61px. `PointerTracker` re-detects the key in the new keyboard's space, and that
 *    re-detection used to bank the 61px as travel and leave the reference point 61px stale.
 *
 * Both are fixed by the same contract, asserted below: recording a down point discards whatever
 * was accumulated before it. See `docs/TOUCH-SLOP-TUNING.md`.
 */
class BogusMoveEventDetectorTest {

    private companion object {
        /**
         * Padded key size of the Tatar alphabet keyboard on a 1080px-wide, 440dpi screen: rows 1
         * and 2 hold 11 keys of `9.091%p` (98px) and the keyboard is 728px over five rows (145px).
         * `PointerTracker.setKeyboardGeometry` feeds exactly this pair to the detector.
         */
        private const val KEY_PADDED_WIDTH = 98
        private const val KEY_PADDED_HEIGHT = 145

        /** `BOGUS_MOVE_ACCUMULATED_DISTANCE_THRESHOLD` (0.53) times the padded key diagonal. */
        private val ACCUMULATED_THRESHOLD =
            (0.53f * Math.hypot(
                KEY_PADDED_WIDTH.toDouble(), KEY_PADDED_HEIGHT.toDouble()).toFloat()).toInt()

        /** Height difference between the alphabet and symbols keyboards, measured on the AVD. */
        private const val KEYBOARD_HEIGHT_SHIFT = 728 - 667

        private val HACK_FLAG =
            BogusMoveEventDetector::class.java
                .getDeclaredField("sNeedsProximateBogusDownMoveUpEventHack")
                .apply { isAccessible = true }
    }

    private lateinit var detector: BogusMoveEventDetector

    /**
     * The hack is enabled only for tablets, by [BogusMoveEventDetector.init], which needs a live
     * `Resources`. Set the flag directly so the branch under test is reachable off-device, and
     * restore it afterwards so the value cannot leak into another test.
     */
    @Before
    fun enableTheTabletHack() {
        HACK_FLAG.setBoolean(null, true)
        detector = BogusMoveEventDetector()
        detector.setKeyboardGeometry(KEY_PADDED_WIDTH, KEY_PADDED_HEIGHT)
    }

    @After
    fun restoreTheTabletHackFlag() {
        HACK_FLAG.setBoolean(null, false)
    }

    @Test
    fun theThresholdIsWhatTheGeometryImplies() {
        // Guards the numbers the rest of the file reasons with: a path shorter than this is not a
        // long journey, a longer one is.
        assertEquals(92, ACCUMULATED_THRESHOLD)
        detector.onActualDownEvent(60, 600)
        detector.onMoveKey(ACCUMULATED_THRESHOLD - 1)
        assertFalse(detector.hasTraveledLongDistance(60 + ACCUMULATED_THRESHOLD - 1, 600))
        detector.onMoveKey(1)
        assertTrue(detector.hasTraveledLongDistance(60 + ACCUMULATED_THRESHOLD, 600))
    }

    // ------------------------------------------------------------ the contract that fixes both

    @Test
    fun aDownPointDiscardsWhateverWasAccumulatedBeforeIt() {
        detector.onMoveKey(900) // the tail of the previous gesture, or a coordinate-space jump
        detector.onActualDownEvent(60, 600)
        assertEquals(0, detector.getAccumulatedDistanceFromDownKey())
    }

    // ------------------------------------------------------------ mine 1: the previous gesture

    /**
     * Tapping `й` and then `ъ` puts the width of the keyboard into the accumulator before the
     * second press has moved a pixel. On a tablet that is enough to clear the threshold, so the
     * very first horizontal tremor takes the pointer off `ъ`.
     */
    @Test
    fun theTailOfThePreviousGestureIsNotTravelOfThisOne() {
        val previousPressEndedAt = 60      // centre of "й"
        val thisPressGoesDownAt = 1020     // centre of "ъ", ten keys away
        val jump = thisPressGoesDownAt - previousPressEndedAt
        assertTrue("the stale distance must be able to clear the threshold on its own",
            jump > ACCUMULATED_THRESHOLD)

        // PointerTracker.onDownKey: the move is fed first, the down point is recorded after it.
        detector.onMoveKey(jump)
        detector.onActualDownEvent(thisPressGoesDownAt, 600)

        assertEquals(0, detector.getAccumulatedDistanceFromDownKey())
        // A 10px tremor is now the 10px it really is, and stays under the threshold.
        detector.onMoveKey(10)
        assertFalse(detector.hasTraveledLongDistance(thisPressGoesDownAt + 10, 600))
    }

    // ------------------------------------------------------------ mine 2: the layout switch

    /**
     * Pressing `?123` at x=60 and then wandering 65px to the right inside the key -- the `?123`
     * hitbox is 162px wide, so the finger never comes within the 8dp hysteresis of its edge --
     * must not count as a long journey: 65px is well under the 92px threshold.
     *
     * Uncompensated it did. The re-detection in the symbols keyboard's space banked the 61px the
     * origin moved, taking the accumulated path to 126px, and left the reference point 61px above
     * the finger, which made a purely horizontal wander look diagonal.
     */
    @Test
    fun aKeyboardHeightChangeIsNotTravelEither() {
        val downX = 60
        val downYInAlphabetSpace = 648
        val downYInSymbolsSpace = downYInAlphabetSpace - KEYBOARD_HEIGHT_SHIFT
        val wander = 65

        // What PointerTracker does now: down, then the re-detection in the new keyboard's space,
        // which feeds the 61px jump and then re-records the down point there.
        detector.onActualDownEvent(downX, downYInAlphabetSpace)
        detector.onMoveKey(KEYBOARD_HEIGHT_SHIFT)
        detector.onActualDownEvent(downX, downYInSymbolsSpace)
        detector.onMoveKey(wander)

        assertEquals(wander, detector.getAccumulatedDistanceFromDownKey())
        assertFalse(
            "65px of wander inside a 162px key is not a long journey",
            detector.hasTraveledLongDistance(downX + wander, downYInSymbolsSpace),
        )

        // The same gesture read through the stale reference point and the banked jump: over the
        // threshold, and horizontal enough to pass the dx >= dy guard.
        val stale = BogusMoveEventDetector()
        stale.setKeyboardGeometry(KEY_PADDED_WIDTH, KEY_PADDED_HEIGHT)
        stale.onActualDownEvent(downX, downYInAlphabetSpace)
        stale.onMoveKey(KEYBOARD_HEIGHT_SHIFT)
        stale.onMoveKey(wander)
        assertEquals(KEYBOARD_HEIGHT_SHIFT + wander, stale.getAccumulatedDistanceFromDownKey())
        assertTrue(
            "characterises the mine: the same 65px used to read as a long journey",
            stale.hasTraveledLongDistance(downX + wander, downYInSymbolsSpace),
        )
    }
}
