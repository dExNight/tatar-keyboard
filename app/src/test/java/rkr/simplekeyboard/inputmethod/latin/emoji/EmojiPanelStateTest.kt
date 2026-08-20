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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiPanelStateTest {

    private val minCellPx = 36
    private val maxCellPx = 56
    private val tabBarPx = 44
    private val searchBarPx = 50
    private val headerPx = 30
    private val floatingPx = 44
    private val floatingInsetPx = 8
    private val backWidthPx = 60

    /** The top of the scrolling content under the tab row and the search band. */
    private val gridTopPx = tabBarPx + searchBarPx

    private fun EmojiPanelState.applyMetrics() = setCellMetrics(
        minCellPx,
        maxCellPx,
        tabBarPx,
        searchBarPx,
        headerPx,
        floatingPx,
        floatingInsetPx,
        backWidthPx,
    )

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

    private fun configuredState(
        snapshot: EmojiSetSnapshot,
        width: Int = 8 * 40,
        height: Int = 400,
    ): EmojiPanelState {
        val state = EmojiPanelState()
        state.setColumns(8)
        state.applyMetrics()
        state.setViewport(width, height)
        state.setSnapshot(snapshot)
        return state
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

    /**
     * The cell is square again. The previous panel shrank it so that a whole number of rows filled
     * the viewport exactly; with one continuous scroll through every section there is no row to
     * align to, and that squeeze was what made the glyphs look small against the reference.
     */
    @Test
    fun cellIsSquareAndOnlyClampedToTheDpRange() {
        val state = EmojiPanelState()
        state.setColumns(8)
        state.applyMetrics()

        // Narrow: width/8 = 30 < 36 -> clamped up.
        state.setViewport(8 * 30, 400)
        assertEquals(30, state.cellWidth())
        assertEquals(36, state.cellHeight())

        // In range: square, untouched.
        state.setViewport(8 * 45, 400)
        assertEquals(45, state.cellWidth())
        assertEquals(45, state.cellHeight())

        // Wide: width/8 = 80 > 56 -> clamped down to the maximum, and no further.
        state.setViewport(8 * 80, 400)
        assertEquals(80, state.cellWidth())
        assertEquals(56, state.cellHeight())
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

    /** The content is below both fixed bands, and the bands themselves tile the top of the panel. */
    @Test
    fun theTwoFixedBandsSitAboveTheScrollingContent() {
        val state = configuredState(snapshotOf(50))
        assertEquals(tabBarPx, state.tabBarHeight())
        assertEquals(tabBarPx, state.searchBarTop())
        assertEquals(searchBarPx, state.searchBarHeight())
        assertEquals(gridTopPx, state.gridTop())
        assertEquals(400 - gridTopPx, state.gridHeight())
    }

    // --- Sections ------------------------------------------------------------------------------

    /**
     * The core of the new layout: one continuous content made of section blocks, each a header
     * followed by its whole rows, with the global cell indices packed across section boundaries.
     */
    @Test
    fun sectionsTileTheContentAndIndicesAreGlobalAndCompact() {
        val counts = intArrayOf(50, 20, 5, 9)
        val state = configuredState(multiCategorySnapshot(*counts))
        val cell = state.cellHeight()
        val columns = state.columnCount()

        assertEquals(counts.size, state.sectionCount())
        var expectedTop = 0
        var expectedStart = 0
        for (section in counts.indices) {
            assertEquals("top of section $section", expectedTop, state.sectionTop(section))
            assertEquals("start of section $section", expectedStart, state.sectionStartIndex(section))
            assertEquals(expectedTop + headerPx, state.sectionGridTop(section))
            val rows = (counts[section] + columns - 1) / columns
            assertEquals(rows, state.sectionRowCount(section))
            expectedTop += headerPx + rows * cell
            expectedStart += counts[section]
        }
        assertEquals(counts.sum(), state.entryCount())
        // The content ends one section block past the last one, plus the trailing air that lets the
        // final row be scrolled clear of the floating keys.
        assertEquals(expectedTop + floatingPx + 2 * floatingInsetPx, state.contentHeight())

        // Global index -> entry crosses section boundaries without a gap.
        assertEquals("c0_0", state.entryAt(0))
        assertEquals("c0_49", state.entryAt(49))
        assertEquals("c1_0", state.entryAt(50))
        assertEquals("c2_0", state.entryAt(70))
        assertEquals("c3_8", state.entryAt(83))
        assertEquals(0, state.sectionOfIndex(49))
        assertEquals(1, state.sectionOfIndex(50))
        assertEquals(3, state.sectionOfIndex(83))
    }

    /** The active tab is a consequence of the scroll position, not of a separate mode. */
    @Test
    fun theActiveTabFollowsTheScrollAndATabTapScrollsToItsSection() {
        val state = configuredState(multiCategorySnapshot(50, 20, 40, 40))
        assertEquals(0, state.activeCategory())

        assertTrue(state.setActiveCategory(2))
        assertEquals(state.sectionTop(2), state.scrollY())
        assertEquals(2, state.activeCategory())

        // Scrolling back by hand moves the active tab back with no explicit category change.
        state.setScrollY(state.sectionTop(1))
        assertEquals(1, state.activeCategory())
        state.setScrollY(0)
        assertEquals(0, state.activeCategory())
    }

    @Test
    fun scrollIsClampedToTheContentHeight() {
        val state = configuredState(snapshotOf(200))
        state.setScrollY(-500)
        assertEquals(0, state.scrollY())
        state.setScrollY(Int.MAX_VALUE / 2)
        assertEquals(state.maxScrollY(), state.scrollY())
        assertTrue(state.maxScrollY() > 0)
    }

    // --- Virtual node count = visible cells + tabs + search + 2 functional keys -----------------

    private fun expectedVisibleCells(state: EmojiPanelState): Int {
        val cell = state.cellHeight()
        val columns = state.columnCount()
        if (cell <= 0 || columns <= 0) return 0
        val top = state.scrollY()
        val bottom = top + state.gridHeight()
        var total = 0
        for (section in 0 until state.sectionCount()) {
            val count = state.sectionEntryCount(section)
            val gridTop = state.sectionGridTop(section)
            for (index in 0 until count) {
                val row = index / columns
                val cellTop = gridTop + row * cell
                if (cellTop < bottom && cellTop + cell > top) total++
            }
        }
        return total
    }

    @Test
    fun virtualNodeCountIsVisibleCellsPlusTabsPlusSearchPlusTwoFunctionalKeys() {
        val state = configuredState(multiCategorySnapshot(50, 20, 5), height = 300)
        assertEquals(3, state.tabCount())

        val visibleAtTop = expectedVisibleCells(state)
        assertEquals(visibleAtTop, state.visibleCellCount())
        assertEquals(visibleAtTop + 3 + 3, state.virtualNodeCount())

        state.setScrollY(state.maxScrollY())
        val visibleAtBottom = expectedVisibleCells(state)
        assertEquals(visibleAtBottom, state.visibleCellCount())
        assertEquals(visibleAtBottom + 3 + 3, state.virtualNodeCount())
    }

    @Test
    fun onlyVisibleRowsAreCounted() {
        val state = configuredState(snapshotOf(200), height = 300)
        assertEquals(25, state.sectionRowCount(0))
        assertTrue(state.visibleCellCount() < 200)
        assertTrue(state.visibleCellCount() <= 8 * 8)
    }

    /** The visible-cell walk must agree with the drawn range at every scroll offset. */
    @Test
    fun theVisibleCellWalkMatchesTheDrawnRangeAtEveryScrollOffset() {
        val state = configuredState(multiCategorySnapshot(50, 20, 5, 33), height = 300)
        var scroll = 0
        while (scroll <= state.maxScrollY()) {
            state.setScrollY(scroll)
            assertEquals("at scroll $scroll", expectedVisibleCells(state), state.visibleCellCount())
            scroll += 7
        }
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

        val state = configuredState(snapshot, height = 200)
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

    // --- Hit testing ---------------------------------------------------------------------------

    @Test
    fun hitTestingResolvesCellsTabsSearchAndTheTwoFloatingKeys() {
        val counts = intArrayOf(50, 20, 5)
        val width = 8 * 40
        val height = 400
        val state = configuredState(multiCategorySnapshot(*counts), width, height)

        // The first cell sits under the first section's header, not at the top of the content.
        assertEquals(EmojiPanelState.NO_TARGET, state.targetAt(1f, gridTopPx + 1f))
        assertEquals(0, state.targetAt(1f, gridTopPx + headerPx + 1f))

        // The tab row is the top band; tab 1 is the second category.
        val tabCenter = (state.tabLeft(1) + state.tabRight(1)) / 2f
        val tabTarget = state.targetAt(tabCenter, 1f)
        assertTrue(EmojiPanelState.isTab(tabTarget))
        assertEquals(1, EmojiPanelState.tabIndexOf(tabTarget))

        // The search pill is the band under it.
        assertTrue(EmojiPanelState.isSearch(state.targetAt(width / 2f, tabBarPx + 1f)))

        // The two floating keys win over the content they are drawn on top of.
        val floatingY = (height - floatingInsetPx - 1).toFloat()
        assertTrue(EmojiPanelState.isBack(state.targetAt((floatingInsetPx + 1).toFloat(), floatingY)))
        assertTrue(
            EmojiPanelState.isDelete(state.targetAt((width - floatingInsetPx - 1).toFloat(), floatingY)),
        )

        // Out of bounds is no target.
        assertEquals(EmojiPanelState.NO_TARGET, state.targetAt(-1f, 1f))
        assertEquals(EmojiPanelState.NO_TARGET, state.targetAt(1f, (height + 1).toFloat()))
    }

    /** Tabs tile the row inside the side inset with no gap and no overlap. */
    @Test
    fun tabsTileTheRowWithoutOverlap() {
        val width = 591
        val state = configuredState(multiCategorySnapshot(50, 20, 5, 10, 10, 10, 10, 10), width, 400)
        val tabs = state.tabCount()
        assertEquals(8, tabs)
        assertEquals(floatingInsetPx, state.tabLeft(0))
        assertEquals(width - floatingInsetPx, state.tabRight(tabs - 1))
        for (tab in 1 until tabs) {
            assertEquals(state.tabRight(tab - 1), state.tabLeft(tab))
            assertTrue("tab $tab is empty", state.tabRight(tab) > state.tabLeft(tab))
        }
        // Every x in the tab row still resolves to some tab, the side insets included.
        for (x in 0 until width) {
            assertTrue(EmojiPanelState.isTab(state.targetAt(x.toFloat(), 1f)))
        }
    }

    /** A section header is drawn inside the scroll but is not a target of its own. */
    @Test
    fun sectionHeadersAreNeverATarget() {
        val state = configuredState(multiCategorySnapshot(160, 160, 160))
        for (section in 0 until state.sectionCount()) {
            state.setScrollY(state.sectionTop(section))
            assertEquals("section $section is not reachable", state.sectionTop(section), state.scrollY())
            var y = gridTopPx
            while (y < gridTopPx + headerPx) {
                assertEquals(
                    "header of section $section at y=$y",
                    EmojiPanelState.NO_TARGET,
                    state.targetAt(1f, y.toFloat()),
                )
                y++
            }
        }
    }

    /** Cells beyond a section's last entry are dead space, not the next section's first cell. */
    @Test
    fun theTailOfAPartialRowIsNotATarget() {
        // 9 entries: row 0 is full, row 1 holds exactly one cell and seven empty ones.
        val state = configuredState(multiCategorySnapshot(9, 16))
        val cell = state.cellHeight()
        val y = (gridTopPx + headerPx + cell + 1).toFloat()
        assertEquals(8, state.targetAt(1f, y))
        for (column in 1 until 8) {
            val x = (state.columnLeft(column) + 1).toFloat()
            assertEquals("column $column", EmojiPanelState.NO_TARGET, state.targetAt(x, y))
        }
    }

    // --- Gestures ------------------------------------------------------------------------------

    @Test
    fun tappingACellReturnsThatCellOnUp() {
        val state = configuredState(snapshotOf(50))
        val y = (gridTopPx + headerPx + 5).toFloat()
        assertEquals(0, state.onDown(1, 5f, y))
        assertEquals(0, state.pressedTarget())
        assertEquals(0, state.onUp(1, 5f, y))
    }

    @Test
    fun verticalDragScrollsTheContentAndSuppressesTheTap() {
        val state = configuredState(snapshotOf(200))
        state.onDown(1, 50f, 250f)
        // Move up past the touch slop.
        assertTrue(state.onMove(1, 50f, 200f, 8))
        assertTrue(state.isScrolling())
        assertTrue(state.scrollY() > 0)
        // A gesture that became a scroll never activates a cell.
        assertEquals(EmojiPanelState.NO_TARGET, state.onUp(1, 50f, 200f))
    }

    /** A drag started on the tab row or the search band scrolls nothing; only the content scrolls. */
    @Test
    fun aDragOutsideTheContentNeverScrolls() {
        val state = configuredState(snapshotOf(200))
        state.onDown(1, 50f, 5f)
        // The move may still clear the pressed highlight; what it must never do is scroll.
        state.onMove(1, 50f, 60f, 8)
        assertEquals(0, state.scrollY())
        assertFalse(state.isScrolling())
    }

    @Test
    fun aPointerUpForAnotherPointerDoesNotEndTheGesture() {
        val state = configuredState(snapshotOf(50))
        val y = (gridTopPx + headerPx + 5).toFloat()
        state.onDown(7, 5f, y)
        assertFalse(state.onPointerUp(19))
        assertEquals(7, state.activePointerId())
        assertEquals(0, state.onUp(7, 5f, y))
    }

    @Test
    fun theFloatingKeysAndTheSearchPillHaveDistinctTargets() {
        val targets = intArrayOf(
            EmojiPanelState.NO_TARGET,
            EmojiPanelState.BACK_TARGET,
            EmojiPanelState.DELETE_TARGET,
            EmojiPanelState.SEARCH_TARGET,
        )
        for (i in targets.indices) {
            for (j in i + 1 until targets.size) {
                assertNotEquals(targets[i], targets[j])
            }
            assertFalse(EmojiPanelState.isCell(targets[i]))
            assertFalse(EmojiPanelState.isTab(targets[i]))
        }
        assertTrue(EmojiPanelState.isSearch(EmojiPanelState.SEARCH_TARGET))
        assertFalse(EmojiPanelState.isSearch(EmojiPanelState.BACK_TARGET))
    }

    @Test
    fun hotGestureAndHitTestingAllocateZeroBytesAfterWarmup() {
        val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean ?: return
        if (!bean.isThreadAllocatedMemorySupported) return
        bean.isThreadAllocatedMemoryEnabled = true
        val state = configuredState(multiCategorySnapshot(200, 100, 50))

        // The continuous ACTION_MOVE scroll path is the one the contract calls allocation-free.
        state.onDown(1, 50f, 350f)
        repeat(200_000) { state.onMove(1, 50f, (200 + (it and 127)).toFloat(), 8) }
        // Hit testing and the visible-cell walk are exercised on the same hot surface.
        repeat(100_000) { state.targetAt(50f, 200f) }
        repeat(100_000) { state.visibleCellCount() }

        val threadId = Thread.currentThread().id
        val before = bean.getThreadAllocatedBytes(threadId)
        repeat(200_000) { state.onMove(1, 50f, (200 + (it and 127)).toFloat(), 8) }
        repeat(100_000) { state.targetAt(50f, 200f) }
        repeat(100_000) { state.visibleCellCount() }
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
