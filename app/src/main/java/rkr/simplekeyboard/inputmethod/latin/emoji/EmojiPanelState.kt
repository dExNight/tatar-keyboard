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
 * Pure, Android-free geometry and gesture state for the emoji panel, modelled on
 * [rkr.simplekeyboard.inputmethod.latin.suggestions.SuggestionStripState]. It owns nothing but
 * primitives and the immutable [EmojiSetSnapshot], so every rule in it is exercised on the plain
 * JVM without a device.
 *
 * Layout, top to bottom: a vertically scrollable grid of cells, then a fixed-height bottom bar of
 * `categoryCount + 2` equal slots — slot 0 is the "back to letters" key, slots `1..categoryCount`
 * are the category tabs, and the last slot is delete. There are exactly two functional keys (back
 * and delete); no space, no Enter.
 *
 * Cell width is exactly `panelWidth / columns` (8 columns portrait, 12 landscape). Cell height
 * equals the width, clamped to [MIN_CELL_DP]..[MAX_CELL_DP] once the metrics are supplied. Cell
 * indices are a compact `0 until entryCount` addressing of the active category and never shift when
 * the grid scrolls: scrolling only changes which rows are visible and where they are drawn.
 */
internal class EmojiPanelState {
    private var snapshot: EmojiSetSnapshot = EmojiSetSnapshot.EMPTY
    private var activeCategory = 0

    private var panelWidth = 0
    private var panelHeight = 0
    private var columns = PORTRAIT_COLUMNS
    private var minCellPx = 0
    private var maxCellPx = 0
    private var bottomBarPx = 0

    /**
     * Width of each of the two functional slots ("АБВ" and delete). They are sized to their own
     * label, not to `panelWidth / slotCount`: with ten categories an equal split leaves ~49px per
     * slot on a 591px screen, which is narrower than the word "АБВ" and made the two labels paint
     * over the neighbouring tabs. The tabs share whatever is left.
     */
    private var edgeSlotPx = 0

    private var scrollY = 0

    // Gesture bookkeeping.
    private var downTarget = NO_TARGET
    private var pressedTarget = NO_TARGET
    private var activePointerId = INVALID_POINTER_ID
    private var downInGrid = false
    private var scrolling = false
    private var downY = 0f
    private var lastMoveY = 0f

    // --- Configuration ------------------------------------------------------------------------

    fun setSnapshot(snapshot: EmojiSetSnapshot) {
        this.snapshot = snapshot
        if (activeCategory !in 0 until snapshot.categoryCount) {
            activeCategory = 0
        }
        scrollY = 0
        clampScroll()
    }

    fun setColumns(columns: Int) {
        if (columns > 0 && columns != this.columns) {
            this.columns = columns
            clampScroll()
        }
    }

    fun setCellMetrics(minCellPx: Int, maxCellPx: Int, bottomBarPx: Int, edgeSlotPx: Int = 0) {
        this.minCellPx = minCellPx
        this.maxCellPx = maxCellPx
        this.bottomBarPx = bottomBarPx
        this.edgeSlotPx = edgeSlotPx.coerceAtLeast(0)
        clampScroll()
    }

    fun setViewport(width: Int, height: Int) {
        panelWidth = width.coerceAtLeast(0)
        panelHeight = height.coerceAtLeast(0)
        clampScroll()
    }

    fun setActiveCategory(category: Int): Boolean {
        if (category == activeCategory || category !in 0 until snapshot.categoryCount) return false
        activeCategory = category
        scrollY = 0
        cancelGesture()
        return true
    }

    // --- Grid geometry ------------------------------------------------------------------------

    fun columnCount(): Int = columns

    fun categoryCount(): Int = snapshot.categoryCount

    fun activeCategory(): Int = activeCategory

    /** Cell width in px: exactly `panelWidth / columns`, never clamped. */
    fun cellWidth(): Int = if (columns > 0) panelWidth / columns else 0

    /**
     * Cell height in px: the square cell (width, clamped to the dp range) adjusted so a whole
     * number of rows fills the grid viewport exactly. Leaving it square left the viewport a
     * non-multiple of the row height, which cost a visible half-row; nudging the height by a few
     * px instead keeps every row whole and spends the full panel.
     */
    fun cellHeight(): Int {
        val width = cellWidth()
        if (maxCellPx <= 0) return width
        val preferred = width.coerceIn(minCellPx, maxCellPx)
        val viewport = gridViewportHeight()
        if (viewport <= 0 || preferred <= 0) return preferred
        // Round the row count up, not to nearest: a slightly shorter cell fits one more row and
        // keeps the grid dense, where rounding down stretches four rows over the whole panel.
        val rows = ((viewport + preferred - 1) / preferred).coerceAtLeast(1)
        return (viewport / rows).coerceIn(minCellPx, maxCellPx)
    }

    fun columnLeft(column: Int): Int = if (columns > 0) panelWidth * column / columns else 0

    fun columnRight(column: Int): Int = if (columns > 0) panelWidth * (column + 1) / columns else 0

    fun entryCount(): Int =
        if (activeCategory in 0 until snapshot.categoryCount) snapshot.entryCount(activeCategory) else 0

    fun rowCount(): Int {
        if (columns <= 0) return 0
        val count = entryCount()
        return (count + columns - 1) / columns
    }

    fun contentHeight(): Int = rowCount() * cellHeight()

    /** The space between the top of the panel and the bottom bar, before row alignment. */
    fun gridViewportHeight(): Int = (panelHeight - bottomBarPx).coerceAtLeast(0)

    /**
     * The drawn height of the grid: the largest whole number of rows that fits the viewport. The
     * viewport is almost never an exact multiple of the cell height, and drawing rows straight into
     * it left a permanently half-clipped row above the bottom bar.
     */
    fun gridHeight(): Int {
        val viewport = gridViewportHeight()
        val height = cellHeight()
        if (height <= 0) return viewport
        val rows = viewport / height
        if (rows <= 0) return viewport
        return (rows * height).coerceAtMost(viewport)
    }

    /** The leftover of [gridViewportHeight] over [gridHeight], spent as padding above the grid. */
    fun gridTop(): Int = ((gridViewportHeight() - gridHeight()) / 2).coerceAtLeast(0)

    fun maxScrollY(): Int = (contentHeight() - gridHeight()).coerceAtLeast(0)

    fun scrollY(): Int = scrollY

    fun setScrollY(value: Int): Boolean {
        val clamped = value.coerceIn(0, maxScrollY())
        if (clamped == scrollY) return false
        scrollY = clamped
        return true
    }

    fun scrollBy(deltaY: Int): Boolean = setScrollY(scrollY + deltaY)

    fun firstVisibleRow(): Int {
        val height = cellHeight()
        if (height <= 0 || rowCount() == 0) return 0
        return (scrollY / height).coerceIn(0, rowCount() - 1)
    }

    fun lastVisibleRow(): Int {
        val height = cellHeight()
        val viewport = gridHeight()
        val rows = rowCount()
        if (height <= 0 || viewport <= 0 || rows == 0) return -1
        return ((scrollY + viewport - 1) / height).coerceIn(0, rows - 1)
    }

    /** Number of cells at least partially inside the grid viewport at the current scroll. */
    fun visibleCellCount(): Int {
        val first = firstVisibleRow()
        val last = lastVisibleRow()
        if (last < first) return 0
        val start = first * columns
        val end = ((last + 1) * columns).coerceAtMost(entryCount())
        return (end - start).coerceAtLeast(0)
    }

    /** The sequence at compact cell [index]; scroll never shifts this mapping. */
    fun entryAt(index: Int): String =
        if (index in 0 until entryCount()) snapshot.entryAt(activeCategory, index) else ""

    // --- Bottom bar geometry ------------------------------------------------------------------

    /** Number of category tabs shown; a category with 0 surviving entries is absent by construction. */
    fun tabCount(): Int = snapshot.categoryCount

    /** Slots on the bottom bar: back key + tabs + delete key. */
    fun slotCount(): Int = tabCount() + 2

    /**
     * Width actually given to each functional slot: [edgeSlotPx], but never so much that the tabs
     * between them are squeezed out on a narrow screen.
     */
    private fun edgeWidth(): Int {
        if (edgeSlotPx <= 0) {
            val slots = slotCount()
            return if (slots > 0) panelWidth / slots else 0
        }
        return edgeSlotPx.coerceAtMost(panelWidth / 3)
    }

    fun slotLeft(slot: Int): Int {
        val slots = slotCount()
        if (slots <= 0) return 0
        val edge = edgeWidth()
        return when {
            slot <= 0 -> 0
            slot >= slots - 1 -> panelWidth - edge
            else -> {
                val tabs = slots - 2
                edge + (panelWidth - 2 * edge) * (slot - 1) / tabs
            }
        }
    }

    fun slotRight(slot: Int): Int {
        val slots = slotCount()
        if (slots <= 0) return 0
        val edge = edgeWidth()
        return when {
            slot <= 0 -> edge
            slot >= slots - 1 -> panelWidth
            else -> {
                val tabs = slots - 2
                edge + (panelWidth - 2 * edge) * slot / tabs
            }
        }
    }

    fun barTop(): Int = (panelHeight - bottomBarPx).coerceAtLeast(0)

    /** The active tab's slot, or [NO_TARGET] if there are no tabs. */
    fun activeTabSlot(): Int = if (tabCount() > 0) activeCategory + 1 else NO_TARGET

    /** The slot a target paints in, or [NO_TARGET]. Used only for the pressed highlight. */
    fun slotOfTarget(target: Int): Int = when {
        isBack(target) -> 0
        isDelete(target) -> slotCount() - 1
        isTab(target) -> tabIndexOf(target) + 1
        else -> NO_TARGET
    }

    /**
     * Virtual-node count the accessibility delegate (E2c) will expose: every visible cell, every
     * tab, and the two functional keys. Kept here so it is verifiable without a device.
     */
    fun virtualNodeCount(): Int = visibleCellCount() + tabCount() + 2

    // --- Hit testing --------------------------------------------------------------------------

    fun isInGrid(x: Float, y: Float): Boolean {
        val top = gridTop()
        return x >= 0f && x < panelWidth && y >= top && y < top + gridHeight()
    }

    /**
     * The target under ([x], [y]): a compact cell index (`>= 0`), [BACK_TARGET], [DELETE_TARGET],
     * a tab (see [isTab]/[tabIndexOf]), or [NO_TARGET].
     */
    fun targetAt(x: Float, y: Float): Int {
        if (x < 0f || x >= panelWidth || y < 0f || y >= panelHeight) return NO_TARGET
        val barTop = barTop()
        if (bottomBarPx > 0 && y >= barTop) {
            val slots = slotCount()
            var slot = 0
            while (slot < slots - 1 && x >= slotRight(slot)) slot++
            return when {
                slot == 0 -> BACK_TARGET
                slot == slots - 1 -> DELETE_TARGET
                else -> TAB_TARGET_BASE - (slot - 1)
            }
        }
        val height = cellHeight()
        if (height <= 0) return NO_TARGET
        val gridTop = gridTop()
        if (y < gridTop || y >= gridTop + gridHeight()) return NO_TARGET
        val contentY = (y.toInt() - gridTop) + scrollY
        val row = contentY / height
        var column = 0
        while (column < columns - 1 && x >= columnRight(column)) column++
        val index = row * columns + column
        return if (index in 0 until entryCount()) index else NO_TARGET
    }

    // --- Gesture state machine ----------------------------------------------------------------

    fun onDown(pointerId: Int, x: Float, y: Float): Int {
        cancelGesture()
        val target = targetAt(x, y)
        downTarget = target
        pressedTarget = target
        activePointerId = pointerId
        downInGrid = isInGrid(x, y)
        downY = y
        lastMoveY = y
        scrolling = false
        return target
    }

    /** Returns true when the visible state (scroll offset or pressed highlight) changed. */
    fun onMove(pointerId: Int, x: Float, y: Float, touchSlop: Int): Boolean {
        if (pointerId != activePointerId || activePointerId == INVALID_POINTER_ID) return false
        var changed = false
        if (!scrolling && downInGrid && abs(y - downY) > touchSlop) {
            scrolling = true
            if (pressedTarget != NO_TARGET) {
                pressedTarget = NO_TARGET
                changed = true
            }
        }
        if (scrolling) {
            val delta = (lastMoveY - y).toInt()
            lastMoveY = y
            if (scrollBy(delta)) changed = true
            return changed
        }
        lastMoveY = y
        val here = targetAt(x, y)
        val next = if (here == downTarget) downTarget else NO_TARGET
        if (next != pressedTarget) {
            pressedTarget = next
            changed = true
        }
        return changed
    }

    /** Returns the activated target, or [NO_TARGET] when the gesture became a scroll or slid off. */
    fun onUp(pointerId: Int, x: Float, y: Float): Int {
        if (pointerId != activePointerId) {
            cancelGesture()
            return NO_TARGET
        }
        val result = when {
            scrolling -> NO_TARGET
            targetAt(x, y) == downTarget -> downTarget
            else -> NO_TARGET
        }
        cancelGesture()
        return result
    }

    /** Ends the gesture only when Android reports the active pointer went up. */
    fun onPointerUp(pointerId: Int): Boolean =
        pointerId == activePointerId && cancelGesture()

    fun cancelGesture(): Boolean {
        val changed = downTarget != NO_TARGET ||
            pressedTarget != NO_TARGET ||
            activePointerId != INVALID_POINTER_ID ||
            scrolling
        downTarget = NO_TARGET
        pressedTarget = NO_TARGET
        activePointerId = INVALID_POINTER_ID
        downInGrid = false
        scrolling = false
        return changed
    }

    fun pressedTarget(): Int = pressedTarget

    fun downTarget(): Int = downTarget

    fun activePointerId(): Int = activePointerId

    fun isScrolling(): Boolean = scrolling

    private fun clampScroll() {
        scrollY = scrollY.coerceIn(0, maxScrollY())
    }

    companion object {
        const val PORTRAIT_COLUMNS = 8
        const val LANDSCAPE_COLUMNS = 12
        const val MIN_CELL_DP = 36
        const val MAX_CELL_DP = 56

        const val NO_TARGET = -1
        const val BACK_TARGET = -2
        const val DELETE_TARGET = -3

        // Tabs occupy the block at and below this value: tab k is encoded as TAB_TARGET_BASE - k.
        const val TAB_TARGET_BASE = -100

        const val INVALID_POINTER_ID = -1

        fun isCell(target: Int): Boolean = target >= 0
        fun isBack(target: Int): Boolean = target == BACK_TARGET
        fun isDelete(target: Int): Boolean = target == DELETE_TARGET
        fun isTab(target: Int): Boolean = target <= TAB_TARGET_BASE
        fun tabIndexOf(target: Int): Int = TAB_TARGET_BASE - target
    }
}

/**
 * Pure delete auto-repeat state, driven by the panel view. The view fires one delete on
 * [begin], schedules a delayed [tick], and calls [cancel] on every stop condition (ACTION_UP,
 * ACTION_CANCEL, the finger leaving the delete key, the panel being hidden, and input-view
 * recreation). Because it is pure, "one gesture never commits twice" and "every stop condition
 * disarms the repeat" are verifiable on the JVM. The "АБВ" key never touches this class.
 */
internal class DeleteRepeatState {
    private var armed = false

    /** Total number of deletes this instance has fired; used by tests. */
    var fireCount = 0
        private set

    /** Begins a hold: fires once and arms the repeat. A second begin while armed does not fire. */
    fun begin(): Boolean {
        if (armed) return false
        armed = true
        fireCount++
        return true
    }

    /** A scheduled repeat step: fires only while still armed. */
    fun tick(): Boolean {
        if (!armed) return false
        fireCount++
        return true
    }

    /** Stops the repeat; idempotent. Returns true when it was armed. */
    fun cancel(): Boolean {
        val wasArmed = armed
        armed = false
        return wasArmed
    }

    fun isArmed(): Boolean = armed
}
