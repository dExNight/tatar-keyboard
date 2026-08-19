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

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiPanelStateTest {

    private val bottomBarPx = 44
    private val minCellPx = 36
    private val maxCellPx = 56

    /** Builds a single-category snapshot with [count] distinct entries via the real parser. */
    private fun snapshotOf(count: Int, categoryName: String = "cat"): EmojiSetSnapshot {
        val text = buildString {
            append('#').append(categoryName).append('\n')
            for (i in 0 until count) append('e').append(i).append('\n')
        }
        return EmojiSet.parse(text)
    }

    private fun multiCategorySnapshot(vararg counts: Int): EmojiSetSnapshot {
        val text = buildString {
            counts.forEachIndexed { category, count ->
                append("#cat").append(category).append('\n')
                for (i in 0 until count) append('c').append(category).append('_').append(i).append('\n')
            }
        }
        return EmojiSet.parse(text)
    }

    // --- Geometry ------------------------------------------------------------------------------

    @Test
    fun portraitCellIsNeverNarrowerThanALetterKeyFrom240To1280Dp() {
        val state = EmojiPanelState()
        state.setColumns(EmojiPanelState.PORTRAIT_COLUMNS)
        for (widthDp in 240..1280) {
            state.setViewport(widthDp, 400)
            val cellWidth = state.cellWidth()
            // A first-row Tatar letter key is 100/11 %p wide (11 keys), i.e. widthDp / 11.
            val letterKeyWidth = widthDp / 11
            assertTrue(
                "cell $cellWidth < letter $letterKeyWidth at $widthDp dp",
                cellWidth >= letterKeyWidth,
            )
            // 100/8 = 12.5%p vs 100/11 = 9.091%p.
            assertTrue(
                "cell fraction ${cellWidth.toDouble() / widthDp} below 9.091%p at $widthDp dp",
                cellWidth.toDouble() / widthDp >= 9.091 / 100.0,
            )
        }
    }

    @Test
    fun cellHeightIsWidthClampedToTheDpRangeThenFittedToWholeRows() {
        val state = EmojiPanelState()
        state.setColumns(8)
        state.setCellMetrics(minCellPx, maxCellPx, bottomBarPx)

        // Narrow: width/8 = 30 < 36 -> clamped up, and the clamp wins over the row fit.
        state.setViewport(8 * 30, 400)
        assertEquals(30, state.cellWidth())
        assertEquals(36, state.cellHeight())

        // In range: the square 45 is nudged down to 44 so that a whole number of rows fills the
        // 356px viewport exactly (8 * 44 = 352, against 7 * 45 = 315 with 41px wasted).
        state.setViewport(8 * 45, 400)
        assertEquals(45, state.cellWidth())
        assertEquals(44, state.cellHeight())

        // Wide: width/8 = 80 > 56 -> the preferred height is clamped down to the 56 maximum, and
        // the row fit then trims it further to 50 (7 * 50 = 350 of the 356px viewport). The clamp
        // bounds the square size the fit starts from; it is not a floor on the result.
        state.setViewport(8 * 80, 400)
        assertEquals(80, state.cellWidth())
        assertEquals(50, state.cellHeight())
        assertTrue("cell above the dp maximum", state.cellHeight() <= maxCellPx)
        assertTrue("cell below the dp minimum", state.cellHeight() >= minCellPx)
    }

    /**
     * The defect this fixes: the grid used to draw straight into `panelHeight - bottomBar`, which
     * is almost never a multiple of the row height, so a half-row was permanently clipped above the
     * bottom bar. The drawn grid must now be a whole number of rows and must never reach the bar.
     */
    @Test
    fun gridHeightIsAWholeNumberOfRowsAndNeverOverlapsTheBottomBar() {
        val state = EmojiPanelState()
        state.setColumns(8)
        state.setCellMetrics(minCellPx, maxCellPx, bottomBarPx)
        for (height in 200..600 step 7) {
            state.setViewport(8 * 45, height)
            val cell = state.cellHeight()
            val grid = state.gridHeight()
            assertEquals("grid $grid not whole rows of $cell at height $height", 0, grid % cell)
            assertTrue(
                "grid bottom ${state.gridTop() + grid} past bar ${state.barTop()} at $height",
                state.gridTop() + grid <= state.barTop(),
            )
        }
    }

    /**
     * The other half of the bottom-bar defect: "АБВ" and the delete key are wider than an equal
     * `panelWidth / slotCount` share once there are many categories, and their labels used to paint
     * over the neighbouring tabs. Both functional slots must keep the width they were given, and
     * the tabs must still divide the rest without overlapping.
     */
    @Test
    fun functionalSlotsKeepTheirWidthAndTabsShareTheRest() {
        val snapshot = multiCategorySnapshot(50, 20, 5, 10, 10, 10, 10, 10) // 8 tabs -> 10 slots
        val state = EmojiPanelState()
        state.setColumns(8)
        val edge = 120
        state.setCellMetrics(minCellPx, maxCellPx, bottomBarPx, edge)
        val width = 591
        state.setViewport(width, 400)
        state.setSnapshot(snapshot)

        val slots = state.slotCount()
        assertEquals(edge, state.slotRight(0) - state.slotLeft(0))
        assertEquals(edge, state.slotRight(slots - 1) - state.slotLeft(slots - 1))
        assertEquals(0, state.slotLeft(0))
        assertEquals(width, state.slotRight(slots - 1))

        // Slots tile the bar left to right with no gap and no overlap.
        for (slot in 1 until slots) {
            assertEquals(
                "slot $slot does not start where slot ${slot - 1} ends",
                state.slotRight(slot - 1),
                state.slotLeft(slot),
            )
            assertTrue("slot $slot is empty", state.slotRight(slot) > state.slotLeft(slot))
        }
    }

    @Test
    fun columnsAreEightPortraitAndTwelveLandscape() {
        val state = EmojiPanelState()
        state.setColumns(EmojiPanelState.PORTRAIT_COLUMNS)
        state.setViewport(360, 400)
        assertEquals(8, state.columnCount())
        assertEquals(45, state.cellWidth())

        state.setColumns(EmojiPanelState.LANDSCAPE_COLUMNS)
        state.setViewport(1200, 400)
        assertEquals(12, state.columnCount())
        assertEquals(100, state.cellWidth())
    }

    @Test
    fun columnBoundariesAreContiguousAndCoverTheFullWidth() {
        val state = EmojiPanelState()
        state.setColumns(8)
        for (width in 240..400) {
            state.setViewport(width, 300)
            assertEquals(0, state.columnLeft(0))
            assertEquals(width, state.columnRight(7))
            for (c in 0 until 7) {
                assertEquals(state.columnRight(c), state.columnLeft(c + 1))
            }
        }
    }

    // --- Virtual node count = visible cells + tabs + 2 functional keys --------------------------

    private fun expectedVisibleCells(state: EmojiPanelState): Int {
        val cellHeight = state.cellHeight()
        val viewport = state.gridViewportHeight()
        val columns = state.columnCount()
        val entryCount = state.entryCount()
        val scrollY = state.scrollY()
        if (cellHeight <= 0 || viewport <= 0 || entryCount == 0) return 0
        val rowCount = (entryCount + columns - 1) / columns
        val firstRow = (scrollY / cellHeight).coerceIn(0, rowCount - 1)
        val lastRow = ((scrollY + viewport - 1) / cellHeight).coerceIn(0, rowCount - 1)
        val start = firstRow * columns
        val end = ((lastRow + 1) * columns).coerceAtMost(entryCount)
        return (end - start).coerceAtLeast(0)
    }

    @Test
    fun virtualNodeCountIsVisibleCellsPlusTabsPlusTwoFunctionalKeys() {
        val snapshot = multiCategorySnapshot(50, 20, 5) // 3 categories -> 3 tabs
        val state = EmojiPanelState()
        state.setColumns(8)
        state.setCellMetrics(minCellPx, maxCellPx, bottomBarPx)
        state.setViewport(8 * 40, 300) // cellHeight 40, viewport 300-44 = 256 -> ~6 full rows + 1
        state.setSnapshot(snapshot)

        assertEquals(3, state.tabCount())
        val visibleAtTop = expectedVisibleCells(state)
        assertEquals(visibleAtTop, state.visibleCellCount())
        assertEquals(visibleAtTop + 3 + 2, state.virtualNodeCount())

        // Scroll to the bottom and re-check: still visible-cells + 3 tabs + 2 functional keys.
        state.setScrollY(state.maxScrollY())
        val visibleAtBottom = expectedVisibleCells(state)
        assertEquals(visibleAtBottom, state.visibleCellCount())
        assertEquals(visibleAtBottom + 3 + 2, state.virtualNodeCount())
    }

    @Test
    fun onlyVisibleRowsAreCounted() {
        val snapshot = snapshotOf(200)
        val state = EmojiPanelState()
        state.setColumns(8)
        state.setCellMetrics(minCellPx, maxCellPx, bottomBarPx)
        state.setViewport(8 * 40, 300)
        state.setSnapshot(snapshot)
        // 200 entries in 25 rows, but only a handful fit the viewport.
        assertEquals(25, state.rowCount())
        assertTrue(state.visibleCellCount() < 200)
        assertTrue(state.visibleCellCount() <= 8 * 8) // bounded by ~8 columns x a few rows
    }

    // --- Glyph filtering: compact, scroll-stable indices ---------------------------------------

    @Test
    fun glyphFilteringYieldsCompactIndicesThatScrollNeverShifts() {
        val text = buildString {
            append("#cat\n")
            for (i in 0 until 40) append('e').append(i).append('\n')
        }
        // A fake probe rejecting half the set: keep only even-numbered entries.
        val probe = GlyphProbe { sequence -> sequence.removePrefix("e").toInt() % 2 == 0 }
        val snapshot = EmojiSet.build(text, probe)

        assertEquals(20, snapshot.totalEntryCount())

        val state = EmojiPanelState()
        state.setColumns(8)
        state.setCellMetrics(minCellPx, maxCellPx, bottomBarPx)
        // 20 kept entries -> 3 rows -> 120px of content; a 120px panel (76px grid) must scroll.
        state.setViewport(8 * 40, 120)
        state.setSnapshot(snapshot)

        assertEquals(20, state.entryCount())
        // Kept entries are packed with no gaps: e0, e2, e4, ...
        assertEquals("e0", state.entryAt(0))
        assertEquals("e2", state.entryAt(1))
        assertEquals("e20", state.entryAt(10))
        assertEquals("e38", state.entryAt(19))

        // Scrolling changes which rows show, never the index -> entry mapping.
        val atIndexTen = state.entryAt(10)
        state.setScrollY(state.maxScrollY())
        assertTrue(state.scrollY() > 0)
        assertEquals(atIndexTen, state.entryAt(10))
    }

    @Test
    fun scrollIsClampedToTheContentHeight() {
        val snapshot = snapshotOf(200)
        val state = EmojiPanelState()
        state.setColumns(8)
        state.setCellMetrics(minCellPx, maxCellPx, bottomBarPx)
        state.setViewport(8 * 40, 300)
        state.setSnapshot(snapshot)

        state.setScrollY(-500)
        assertEquals(0, state.scrollY())
        state.setScrollY(Int.MAX_VALUE / 2)
        assertEquals(state.maxScrollY(), state.scrollY())
        assertTrue(state.maxScrollY() > 0)
    }

    // --- Hit testing ---------------------------------------------------------------------------

    @Test
    fun hitTestingResolvesCellsTabsAndTheTwoFunctionalKeys() {
        val snapshot = multiCategorySnapshot(50, 20, 5) // 3 tabs -> 5 slots
        val state = EmojiPanelState()
        state.setColumns(8)
        state.setCellMetrics(minCellPx, maxCellPx, bottomBarPx)
        val width = 8 * 40
        val height = 300
        state.setViewport(width, height)
        state.setSnapshot(snapshot)

        // Top-left of the grid is cell 0. The grid starts at gridTop, not at the panel's top edge:
        // the leftover of the viewport over a whole number of rows is padding above the grid.
        val gridY = state.gridTop() + 1f
        assertEquals(0, state.targetAt(1f, gridY))
        assertTrue(EmojiPanelState.isCell(state.targetAt(1f, gridY)))

        val barY = (height - 1).toFloat()
        // Leftmost slot -> back key, rightmost -> delete key.
        assertTrue(EmojiPanelState.isBack(state.targetAt(1f, barY)))
        assertTrue(EmojiPanelState.isDelete(state.targetAt((width - 1).toFloat(), barY)))

        // Slot 2 is the second tab (slot 0 is back, slot 1 is tab 0).
        val tabCenter = (state.slotLeft(2) + state.slotRight(2)) / 2f
        val tabTarget = state.targetAt(tabCenter, barY)
        assertTrue(EmojiPanelState.isTab(tabTarget))
        assertEquals(1, EmojiPanelState.tabIndexOf(tabTarget))

        // Out of bounds is no target.
        assertEquals(EmojiPanelState.NO_TARGET, state.targetAt(-1f, 1f))
        assertEquals(EmojiPanelState.NO_TARGET, state.targetAt(1f, (height + 1).toFloat()))
    }

    // --- Gestures ------------------------------------------------------------------------------

    @Test
    fun tappingACellReturnsThatCellOnUp() {
        val state = configuredState(snapshotOf(50))
        assertEquals(0, state.onDown(1, 5f, 5f))
        assertEquals(0, state.pressedTarget())
        assertEquals(0, state.onUp(1, 5f, 5f))
    }

    @Test
    fun verticalDragScrollsTheGridAndSuppressesTheTap() {
        val state = configuredState(snapshotOf(200))
        state.onDown(1, 50f, 200f)
        // Move up past the touch slop.
        assertTrue(state.onMove(1, 50f, 150f, 8))
        assertTrue(state.isScrolling())
        assertTrue(state.scrollY() > 0)
        // A gesture that became a scroll never activates a cell.
        assertEquals(EmojiPanelState.NO_TARGET, state.onUp(1, 50f, 150f))
    }

    @Test
    fun tappingATabSwitchesCategoryAndResetsScroll() {
        val snapshot = multiCategorySnapshot(200, 20, 5)
        val state = configuredState(snapshot)
        state.setScrollY(state.maxScrollY())
        assertTrue(state.scrollY() > 0)

        val barY = 299f
        val tabCenter = (state.slotLeft(2) + state.slotRight(2)) / 2f
        val target = state.onDown(1, tabCenter, barY)
        assertTrue(EmojiPanelState.isTab(target))
        assertEquals(target, state.onUp(1, tabCenter, barY))

        assertTrue(state.setActiveCategory(EmojiPanelState.tabIndexOf(target)))
        assertEquals(1, state.activeCategory())
        assertEquals(0, state.scrollY())
    }

    @Test
    fun aPointerUpForAnotherPointerDoesNotEndTheGesture() {
        val state = configuredState(snapshotOf(50))
        state.onDown(7, 5f, 5f)
        assertFalse(state.onPointerUp(19))
        assertEquals(7, state.activePointerId())
        assertEquals(0, state.onUp(7, 5f, 5f))
    }

    private fun configuredState(snapshot: EmojiSetSnapshot): EmojiPanelState {
        val state = EmojiPanelState()
        state.setColumns(8)
        state.setCellMetrics(minCellPx, maxCellPx, bottomBarPx)
        state.setViewport(8 * 40, 300)
        state.setSnapshot(snapshot)
        return state
    }

    @Test
    fun hotGestureAndHitTestingAllocateZeroBytesAfterWarmup() {
        val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean ?: return
        if (!bean.isThreadAllocatedMemorySupported) return
        bean.isThreadAllocatedMemoryEnabled = true
        val state = configuredState(snapshotOf(200))

        // The continuous ACTION_MOVE scroll path is the one the contract calls allocation-free.
        state.onDown(1, 50f, 250f)
        repeat(200_000) { state.onMove(1, 50f, (60 + (it and 127)).toFloat(), 8) }
        // Hit testing is exercised on the same hot surface.
        repeat(100_000) { state.targetAt(50f, 50f) }

        val threadId = Thread.currentThread().id
        val before = bean.getThreadAllocatedBytes(threadId)
        repeat(200_000) { state.onMove(1, 50f, (60 + (it and 127)).toFloat(), 8) }
        repeat(100_000) { state.targetAt(50f, 50f) }
        val allocated = bean.getThreadAllocatedBytes(threadId) - before

        assertEquals(0L, allocated)
    }

    // --- Delete auto-repeat --------------------------------------------------------------------

    @Test
    fun deleteRepeatFiresOnceOnBeginAndNeverDoublesForOneGesture() {
        val repeat = DeleteRepeatState()
        assertTrue(repeat.begin())
        assertEquals(1, repeat.fireCount)
        assertTrue(repeat.isArmed())
        // A second begin inside the same hold does not fire again.
        assertFalse(repeat.begin())
        assertEquals(1, repeat.fireCount)
    }

    @Test
    fun deleteRepeatTicksWhileArmedAndStopsAfterCancel() {
        val repeat = DeleteRepeatState()
        repeat.begin()
        assertTrue(repeat.tick())
        assertTrue(repeat.tick())
        assertEquals(3, repeat.fireCount)

        assertTrue(repeat.cancel())
        assertFalse(repeat.isArmed())
        // Every stop condition maps to cancel(); after it no tick can fire.
        assertFalse(repeat.tick())
        assertEquals(3, repeat.fireCount)
        // Idempotent.
        assertFalse(repeat.cancel())
    }

    @Test
    fun quickTapDeletesExactlyOnce() {
        val repeat = DeleteRepeatState()
        assertTrue(repeat.begin()) // ACTION_DOWN fires one delete
        repeat.cancel() // ACTION_UP before the repeat timeout
        assertFalse(repeat.tick()) // a stray scheduled tick must not fire
        assertEquals(1, repeat.fireCount)
    }
}
