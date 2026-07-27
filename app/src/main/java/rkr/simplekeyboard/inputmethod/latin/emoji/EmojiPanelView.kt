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

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.OverScroller
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import kotlin.math.abs
import rkr.simplekeyboard.inputmethod.R

/**
 * E2b-2 emoji panel: a real, allocation-free Canvas grid that replaces the
 * [rkr.simplekeyboard.inputmethod.keyboard.MainKeyboardView] while shown; the two never draw at
 * once. The keyboard ships no emoji font — every cell is drawn with the system font, and entries
 * the running device cannot render were already dropped by the glyph probe when the snapshot was
 * built, so no "tofu" box ever reaches a cell.
 *
 * All geometry, hit testing and scrolling live in the pure [EmojiPanelState]; the auto-repeat of
 * delete lives in the pure [DeleteRepeatState]. This view owns only the Android surface: paints
 * built once, no allocations in [onDraw] or [onTouchEvent], and only the visible grid rows drawn.
 *
 * There are exactly two functional keys — "АБВ" (back to the letters) and delete — plus the
 * category tabs. No space, no Enter. Insertion goes solely through the listener, which routes to
 * `LatinIME.onTextInput(String)`; delete routes through `LatinIME.onCodeInput(CODE_DELETE)`. This
 * view never commits or deletes text itself and never reads, logs or transmits the field text.
 */
class EmojiPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs, R.attr.mainKeyboardViewStyle) {

    /** Callbacks for the two functional keys and for picking an emoji; all on the UI thread. */
    interface Listener {
        /** The "АБВ" key: hide the panel and return to the letter keyboard. */
        fun onEmojiPanelBackToKeyboard()

        /** The delete key: one backspace through the ordinary code-input path. */
        fun onEmojiPanelDelete()

        /** A grid cell was tapped: insert [sequence] through the ordinary text-input path. */
        fun onEmojiPanelPick(sequence: String)
    }

    private companion object {
        private const val LABEL_TEXT_SIZE_SP = 18f
        private const val EMOJI_TEXT_SCALE = 0.62f
        private const val TAB_TEXT_SCALE = 0.52f
        private const val PRESSED_ALPHA = 90
        private const val SEPARATOR_ALPHA = 0x30
        private const val ACTIVE_TAB_ALPHA = 0x1f
        private const val MIN_CELL_DP = EmojiPanelState.MIN_CELL_DP.toFloat()
        private const val MAX_CELL_DP = EmojiPanelState.MAX_CELL_DP.toFloat()
        private const val BOTTOM_BAR_DP = 44f
        private const val BACK_LABEL = "АБВ"

        // U+232B ERASE TO THE LEFT: a system glyph, so the panel ships no font of its own.
        private const val DELETE_LABEL = "\u232B"

        // VelocityTracker reports velocity in px per this many milliseconds.
        private const val VELOCITY_UNITS = 1000

        // Accessibility virtual-view id space. Cell ids are the compact cell index (0 until
        // entryCount, at most ~1389), so the tab and functional-key ids sit far above any cell id
        // and can never collide with one.
        private const val TAB_ID_BASE = 1_000_000
        private const val BACK_ID = 2_000_000
        private const val DELETE_ID = 2_000_001
    }

    private val state = EmojiPanelState()
    private val deleteRepeat = DeleteRepeatState()

    /** The single, reusable fling scroller for the whole View lifetime. */
    private val scroller = OverScroller(context)

    /** Obtained at most once per gesture on ACTION_DOWN, recycled on ACTION_UP/ACTION_CANCEL. */
    private var velocityTracker: VelocityTracker? = null

    private val backgroundPaint = Paint()
    private val pressedPaint = Paint()
    private val separatorPaint = Paint()
    private val activeTabPaint = Paint()
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val tabPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            LABEL_TEXT_SIZE_SP,
            resources.displayMetrics,
        )
    }
    private val emojiFontMetrics = Paint.FontMetrics()
    private val tabFontMetrics = Paint.FontMetrics()
    private val labelFontMetrics = Paint.FontMetrics()

    private val minCellPx = dp(MIN_CELL_DP)
    private val maxCellPx = dp(MAX_CELL_DP)
    private val bottomBarPx = dp(BOTTOM_BAR_DP)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity
    private val repeatStartTimeoutMs =
        resources.getInteger(R.integer.config_key_repeat_start_timeout).toLong()
    private val repeatIntervalMs =
        resources.getInteger(R.integer.config_key_repeat_interval).toLong()

    private var panelHeightPx = 0
    private var barLabelBaseline = 0f
    private var barTabBaseline = 0f
    private var tabLabels: Array<String> = emptyArray()

    // The category name of each tab, kept alongside [tabLabels] so the accessibility delegate can
    // read a localized tab description without recomputing any geometry of its own.
    private var tabNames: Array<String> = emptyArray()

    // True while a fling is animating, so a single "scroll finished" accessibility refresh fires on
    // the frame the scroller settles rather than on every frame.
    private var flingActive = false
    private var listener: Listener? = null

    private val accessibilityManager =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    private val accessibilityHelper = EmojiPanelAccessibilityHelper()

    private val deleteRepeatRunnable = object : Runnable {
        override fun run() {
            if (deleteRepeat.tick()) {
                listener?.onEmojiPanelDelete()
                postDelayed(this, repeatIntervalMs)
            }
        }
    }

    init {
        val themeColors = context.theme.obtainStyledAttributes(
            intArrayOf(
                R.attr.keyNormalBackgroundColor,
                R.attr.functionalTextColor,
                R.attr.keyPressedBackgroundColor,
                R.attr.keyTextColor,
            ),
        )
        backgroundPaint.color = themeColors.getColor(0, Color.LTGRAY)
        labelPaint.color = themeColors.getColor(1, Color.DKGRAY)
        tabPaint.color = themeColors.getColor(1, Color.DKGRAY)
        pressedPaint.color = themeColors.getColor(2, Color.GRAY)
        emojiPaint.color = themeColors.getColor(3, Color.BLACK)
        themeColors.recycle()
        separatorPaint.color = withAlpha(labelPaint.color, SEPARATOR_ALPHA)
        activeTabPaint.color = withAlpha(labelPaint.color, ACTIVE_TAB_ALPHA)
        state.setColumns(currentColumns())
        state.setCellMetrics(minCellPx, maxCellPx, bottomBarPx)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper)
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    /** Binds the published snapshot and precomputes the representative-emoji tab labels. */
    fun setSnapshot(snapshot: EmojiSetSnapshot) {
        state.setColumns(currentColumns())
        state.setSnapshot(snapshot)
        val count = snapshot.categoryCount
        tabLabels = Array(count) { snapshot.entryAt(it, 0) }
        tabNames = Array(count) { snapshot.categoryName(it) }
        invalidate()
        // A rebind resets the active category to 0 and rebuilds the whole grid, so the virtual-node
        // tree changed; refresh it, but only while a screen reader is actually exploring.
        invalidateAccessibilityRootIfExploring()
    }

    /** Matches the panel to the current keyboard height so insets stay identical to the keyboard. */
    fun setPanelHeightPx(heightPx: Int) {
        if (heightPx > 0 && heightPx != panelHeightPx) {
            panelHeightPx = heightPx
            requestLayout()
        }
    }

    /** Drops transient state, the delete repeat and the listener before detach or replacement. */
    fun release() {
        cancelDeleteRepeat()
        scroller.forceFinished(true)
        flingActive = false
        recycleVelocityTracker()
        state.cancelGesture()
        listener = null
        visibility = GONE
        invalidateAccessibilityRootIfExploring()
    }

    /**
     * Frees the bound snapshot and per-layout caches under memory pressure
     * ([rkr.simplekeyboard.inputmethod.latin.LatinIME] `MSG_DEALLOCATE_MEMORY`, 10 s) or when input
     * finishes. The panel allocates no offscreen buffer, so there is nothing else to free; the
     * reusable paints and the single [scroller] stay. The controller keeps the one prepared snapshot
     * for the whole process (it is never re-prepared), so the next show simply re-binds it through
     * [setSnapshot]. A no-op while the panel is visible, so it never blanks a shown grid.
     */
    fun releaseSnapshotCaches() {
        if (visibility == VISIBLE) {
            return
        }
        cancelDeleteRepeat()
        scroller.forceFinished(true)
        flingActive = false
        state.cancelGesture()
        state.setSnapshot(EmojiSetSnapshot.EMPTY)
        tabLabels = emptyArray()
        tabNames = emptyArray()
        invalidateAccessibilityRootIfExploring()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        val fallback = MeasureSpec.getSize(heightMeasureSpec)
        val desiredHeight = if (panelHeightPx > 0) panelHeightPx else fallback
        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        state.setColumns(currentColumns())
        state.setCellMetrics(minCellPx, maxCellPx, bottomBarPx)
        state.setViewport(width, height)
        emojiPaint.textSize = state.cellHeight() * EMOJI_TEXT_SCALE
        emojiPaint.getFontMetrics(emojiFontMetrics)
        tabPaint.textSize = bottomBarPx * TAB_TEXT_SCALE
        tabPaint.getFontMetrics(tabFontMetrics)
        labelPaint.getFontMetrics(labelFontMetrics)
        val barCenter = state.barTop() + bottomBarPx / 2f
        barLabelBaseline = barCenter - (labelFontMetrics.ascent + labelFontMetrics.descent) / 2f
        barTabBaseline = barCenter - (tabFontMetrics.ascent + tabFontMetrics.descent) / 2f
        invalidateAccessibilityRootIfExploring()
    }

    override fun computeScroll() {
        super.computeScroll()
        if (scroller.computeScrollOffset()) {
            // Physics live in EmojiFling: clamp the scroller's position into range, then keep
            // animating until the scroller settles. onDraw still paints only the visible rows.
            flingActive = true
            state.setScrollY(EmojiFling.clampScroll(scroller.currY, state.maxScrollY()))
            postInvalidateOnAnimation()
            return
        }
        if (flingActive) {
            // The fling just settled: the visible-cell set is final, so refresh the virtual-node
            // tree exactly once, and only while a screen reader is exploring.
            flingActive = false
            invalidateAccessibilityRootIfExploring()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, backgroundPaint)

        // Grid: only the visible rows are drawn.
        val columns = state.columnCount()
        val cellHeight = state.cellHeight()
        val entryCount = state.entryCount()
        val scrollY = state.scrollY()
        val emojiCenterOffset = -(emojiFontMetrics.ascent + emojiFontMetrics.descent) / 2f
        val pressed = state.pressedTarget()
        val firstRow = state.firstVisibleRow()
        val lastRow = state.lastVisibleRow()
        var row = firstRow
        while (row in firstRow..lastRow) {
            var column = 0
            while (column < columns) {
                val index = row * columns + column
                if (index >= entryCount) break
                val left = state.columnLeft(column).toFloat()
                val right = state.columnRight(column).toFloat()
                val top = (row * cellHeight - scrollY).toFloat()
                val bottom = top + cellHeight
                if (EmojiPanelState.isCell(pressed) && pressed == index) {
                    canvas.drawRect(left, top, right, bottom, pressedPaint)
                }
                val centerX = (left + right) / 2f
                val baseline = top + cellHeight / 2f + emojiCenterOffset
                canvas.drawText(state.entryAt(index), centerX, baseline, emojiPaint)
                column++
            }
            row++
        }

        // Bottom bar: back key, category tabs, delete key.
        val barTop = state.barTop().toFloat()
        canvas.drawLine(0f, barTop, w, barTop, separatorPaint)
        val slots = state.slotCount()
        val activeTabSlot = state.activeTabSlot()
        val pressedSlot = state.slotOfTarget(pressed)
        var slot = 0
        while (slot < slots) {
            val left = state.slotLeft(slot).toFloat()
            val right = state.slotRight(slot).toFloat()
            if (slot == activeTabSlot) {
                canvas.drawRect(left, barTop, right, h, activeTabPaint)
            }
            if (slot == pressedSlot) {
                canvas.drawRect(left, barTop, right, h, pressedPaint)
            }
            val centerX = (left + right) / 2f
            when (slot) {
                0 -> canvas.drawText(BACK_LABEL, centerX, barLabelBaseline, labelPaint)
                slots - 1 -> canvas.drawText(DELETE_LABEL, centerX, barLabelBaseline, labelPaint)
                else -> canvas.drawText(tabLabels[slot - 1], centerX, barTabBaseline, tabPaint)
            }
            slot++
        }
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!scroller.isFinished) {
                    scroller.forceFinished(true)
                }
                obtainVelocityTracker()
                velocityTracker?.addMovement(event)
                val pointerIndex = event.actionIndex
                val target = state.onDown(
                    event.getPointerId(pointerIndex),
                    event.getX(pointerIndex),
                    event.getY(pointerIndex),
                )
                if (EmojiPanelState.isDelete(target) && deleteRepeat.begin()) {
                    listener?.onEmojiPanelDelete()
                    postDelayed(deleteRepeatRunnable, repeatStartTimeoutMs)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val pointerIndex = event.findPointerIndex(state.activePointerId())
                if (pointerIndex < 0) return true
                val changed = state.onMove(
                    event.getPointerId(pointerIndex),
                    event.getX(pointerIndex),
                    event.getY(pointerIndex),
                    touchSlop,
                )
                if (!EmojiPanelState.isDelete(state.pressedTarget())) {
                    cancelDeleteRepeat()
                }
                if (changed) invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> return true
            MotionEvent.ACTION_POINTER_UP -> {
                if (state.onPointerUp(event.getPointerId(event.actionIndex))) {
                    cancelDeleteRepeat()
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                cancelDeleteRepeat()
                velocityTracker?.addMovement(event)
                val pointerIndex = event.actionIndex
                val wasScrolling = state.isScrolling()
                maybeFling(wasScrolling)
                recycleVelocityTracker()
                val target = state.onUp(
                    event.getPointerId(pointerIndex),
                    event.getX(pointerIndex),
                    event.getY(pointerIndex),
                )
                invalidate()
                // A drag-scroll that did not turn into a fling has finished here; refresh the a11y
                // tree once (the fling path refreshes from computeScroll when the scroller settles).
                if (wasScrolling && scroller.isFinished) {
                    invalidateAccessibilityRootIfExploring()
                }
                dispatchTarget(target)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelDeleteRepeat()
                scroller.forceFinished(true)
                recycleVelocityTracker()
                state.cancelGesture()
                invalidate()
                return true
            }
        }
        return false
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE) {
            cancelDeleteRepeat()
            state.cancelGesture()
        }
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    private fun dispatchTarget(target: Int) {
        when {
            EmojiPanelState.isCell(target) -> listener?.onEmojiPanelPick(state.entryAt(target))
            EmojiPanelState.isBack(target) -> listener?.onEmojiPanelBackToKeyboard()
            EmojiPanelState.isTab(target) -> if (state.setActiveCategory(EmojiPanelState.tabIndexOf(target))) {
                invalidate()
                invalidateAccessibilityRootIfExploring()
            }
            // Delete already fired on ACTION_DOWN through the auto-repeat; nothing to do on up.
        }
    }

    private fun cancelDeleteRepeat() {
        if (deleteRepeat.cancel()) {
            removeCallbacks(deleteRepeatRunnable)
        }
    }

    /** Obtains the per-gesture [VelocityTracker] at most once; DOWN calls this, UP/CANCEL recycle. */
    private fun obtainVelocityTracker() {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
    }

    /** Releases the per-gesture [VelocityTracker]; called on ACTION_UP, ACTION_CANCEL and release. */
    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    /**
     * On release of a gesture that was scrolling, starts a fling when the release speed clears the
     * platform minimum. The fling/tap decision and the scroll clamp are the pure [EmojiFling]
     * physics; the single reusable [scroller] carries the motion and [computeScroll] advances it.
     */
    private fun maybeFling(wasScrolling: Boolean) {
        val tracker = velocityTracker ?: return
        if (!wasScrolling) {
            return
        }
        tracker.computeCurrentVelocity(VELOCITY_UNITS, maxFlingVelocity.toFloat())
        val velocityY = tracker.getYVelocity(state.activePointerId())
        if (EmojiFling.shouldFling(true, velocityY, minFlingVelocity, state.maxScrollY())) {
            scroller.forceFinished(true)
            scroller.fling(0, state.scrollY(), 0, -velocityY.toInt(), 0, 0, 0, state.maxScrollY())
            postInvalidateOnAnimation()
        }
    }

    private fun currentColumns(): Int =
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            EmojiPanelState.LANDSCAPE_COLUMNS
        } else {
            EmojiPanelState.PORTRAIT_COLUMNS
        }

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        resources.displayMetrics,
    ).toInt()

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    // --- Accessibility ------------------------------------------------------------------------

    /**
     * Refreshes the ExploreByTouchHelper virtual-node tree, but only while touch exploration is on
     * — the single gate behind every panel invalidateRoot, exactly as the suggestion strip keeps
     * its rare announcements behind the same check. The panel calls this on a category change and
     * once a scroll settles; it never fires during an in-progress scroll or when no screen reader
     * is exploring.
     */
    private fun invalidateAccessibilityRootIfExploring() {
        if (accessibilityManager.isTouchExplorationEnabled) {
            accessibilityHelper.invalidateRoot()
        }
    }

    /**
     * Runs a virtual node's activation through the SAME listener path a finger tap uses, so a click
     * from a screen reader never adds a second insertion or deletion route: a cell goes through
     * [Listener.onEmojiPanelPick] (-> `LatinIME.onTextInput`), delete through
     * [Listener.onEmojiPanelDelete] (-> `LatinIME.onCodeInput`), back through
     * [Listener.onEmojiPanelBackToKeyboard], and a tab switches the active category. Returns true
     * when it did something.
     */
    private fun activateForAccessibility(target: Int): Boolean = when {
        EmojiPanelState.isCell(target) -> {
            listener?.onEmojiPanelPick(state.entryAt(target))
            true
        }
        EmojiPanelState.isBack(target) -> {
            listener?.onEmojiPanelBackToKeyboard()
            true
        }
        EmojiPanelState.isDelete(target) -> {
            listener?.onEmojiPanelDelete()
            true
        }
        EmojiPanelState.isTab(target) -> {
            if (state.setActiveCategory(EmojiPanelState.tabIndexOf(target))) {
                invalidate()
                invalidateAccessibilityRootIfExploring()
            }
            true
        }
        else -> false
    }

    /** Scrolls one grid viewport for a root ACTION_SCROLL_FORWARD/BACKWARD; true when it moved. */
    private fun scrollOneViewport(forward: Boolean): Boolean {
        val viewport = state.gridViewportHeight()
        if (viewport <= 0 || state.maxScrollY() <= 0) {
            return false
        }
        val moved = state.scrollBy(if (forward) viewport else -viewport)
        if (moved) {
            invalidate()
            invalidateAccessibilityRootIfExploring()
        }
        return moved
    }

    /** Localized spoken name of a tab category; the raw slug is the fail-safe fallback. */
    private fun categoryContentDescription(categoryName: String): CharSequence {
        val resId = when (categoryName) {
            EmojiDisplaySnapshots.RECENT_CATEGORY_NAME -> R.string.spoken_emoji_category_recent
            "smileys-emotion" -> R.string.spoken_emoji_category_smileys
            "people-body" -> R.string.spoken_emoji_category_people
            "animals-nature" -> R.string.spoken_emoji_category_animals
            "food-drink" -> R.string.spoken_emoji_category_food
            "travel-places" -> R.string.spoken_emoji_category_travel
            "activities" -> R.string.spoken_emoji_category_activities
            "objects" -> R.string.spoken_emoji_category_objects
            "symbols" -> R.string.spoken_emoji_category_symbols
            "flags" -> R.string.spoken_emoji_category_flags
            else -> return categoryName
        }
        return context.getString(resId)
    }

    /**
     * The panel's [ExploreByTouchHelper], modelled on `SuggestionStripView`'s delegate. Its virtual
     * views are ONLY the visible cells, the category tabs and the two functional keys — the exact
     * set [EmojiPanelState.virtualNodeCount] counts — enumerated from the same hit-tests and
     * geometry the touch path uses, with no second geometry of its own. A cell's contentDescription
     * is the emoji sequence itself: the phase deliberately ships no emoji-name database (no Tatar
     * CLDR names exist and shipping English/Russian names would be the worst option), so how a
     * screen reader voices the sequence is left to the system. Tabs and functional keys get
     * localized descriptions. A node click runs the same action as a finger tap through
     * [activateForAccessibility], and the root node exposes ACTION_SCROLL_FORWARD/BACKWARD.
     */
    private inner class EmojiPanelAccessibilityHelper :
        ExploreByTouchHelper(this@EmojiPanelView) {
        private val tempBounds = Rect()

        override fun getVirtualViewAt(x: Float, y: Float): Int =
            targetToVirtualId(state.targetAt(x, y))

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            // Visible cells: the same first/last visible row range the grid draws, so the node set
            // matches what is on screen and never exposes a scrolled-off cell.
            val columns = state.columnCount()
            if (columns > 0) {
                val firstRow = state.firstVisibleRow()
                val lastRow = state.lastVisibleRow()
                if (lastRow >= firstRow) {
                    var index = firstRow * columns
                    val end = ((lastRow + 1) * columns).coerceAtMost(state.entryCount())
                    while (index < end) {
                        virtualViewIds.add(index)
                        index++
                    }
                }
            }
            // Category tabs, then the two functional keys.
            var tab = 0
            val tabs = state.tabCount()
            while (tab < tabs) {
                virtualViewIds.add(TAB_ID_BASE + tab)
                tab++
            }
            virtualViewIds.add(BACK_ID)
            virtualViewIds.add(DELETE_ID)
        }

        override fun onPopulateNodeForHost(node: AccessibilityNodeInfoCompat) {
            node.className = View::class.java.name
            if (state.maxScrollY() > 0) {
                node.isScrollable = true
                node.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD)
                node.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD)
            }
        }

        override fun onPopulateNodeForVirtualView(
            virtualViewId: Int,
            node: AccessibilityNodeInfoCompat,
        ) {
            node.className = android.widget.Button::class.java.name
            when {
                virtualViewId == BACK_ID -> {
                    node.contentDescription =
                        context.getString(R.string.spoken_description_to_alpha)
                    boundsOfSlot(0, tempBounds)
                }
                virtualViewId == DELETE_ID -> {
                    node.contentDescription = context.getString(R.string.spoken_description_delete)
                    boundsOfSlot(state.slotCount() - 1, tempBounds)
                }
                virtualViewId >= TAB_ID_BASE -> {
                    val tab = virtualViewId - TAB_ID_BASE
                    node.contentDescription =
                        categoryContentDescription(tabNames.getOrElse(tab) { "" })
                    boundsOfSlot(tab + 1, tempBounds)
                }
                virtualViewId in 0 until state.entryCount() -> {
                    // The cell's spoken description is the sequence itself; no name database ships.
                    node.contentDescription = state.entryAt(virtualViewId)
                    boundsOfCell(virtualViewId, tempBounds)
                }
                else -> {
                    node.contentDescription = ""
                    tempBounds.set(0, 0, 1, 1)
                    node.setBoundsInParent(tempBounds)
                    return
                }
            }
            node.setBoundsInParent(tempBounds)
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            node.isClickable = true
            node.isEnabled = true
        }

        override fun onPerformActionForVirtualView(
            virtualViewId: Int,
            action: Int,
            arguments: Bundle?,
        ): Boolean {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) {
                return false
            }
            if (!activateForAccessibility(virtualIdToTarget(virtualViewId))) {
                return false
            }
            sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
            return true
        }

        private fun targetToVirtualId(target: Int): Int = when {
            EmojiPanelState.isCell(target) -> target
            EmojiPanelState.isBack(target) -> BACK_ID
            EmojiPanelState.isDelete(target) -> DELETE_ID
            EmojiPanelState.isTab(target) -> TAB_ID_BASE + EmojiPanelState.tabIndexOf(target)
            else -> INVALID_ID
        }

        private fun virtualIdToTarget(virtualViewId: Int): Int = when {
            virtualViewId == BACK_ID -> EmojiPanelState.BACK_TARGET
            virtualViewId == DELETE_ID -> EmojiPanelState.DELETE_TARGET
            virtualViewId >= TAB_ID_BASE ->
                EmojiPanelState.TAB_TARGET_BASE - (virtualViewId - TAB_ID_BASE)
            virtualViewId in 0 until state.entryCount() -> virtualViewId
            else -> EmojiPanelState.NO_TARGET
        }

        private fun boundsOfCell(index: Int, out: Rect) {
            val columns = state.columnCount().coerceAtLeast(1)
            val column = index % columns
            val row = index / columns
            val cellHeight = state.cellHeight()
            val top = row * cellHeight - state.scrollY()
            out.set(
                state.columnLeft(column),
                top,
                state.columnRight(column),
                top + cellHeight,
            )
        }

        private fun boundsOfSlot(slot: Int, out: Rect) {
            out.set(
                state.slotLeft(slot),
                state.barTop(),
                state.slotRight(slot),
                this@EmojiPanelView.height,
            )
        }
    }

    /**
     * Root scroll actions from a screen reader. ExploreByTouchHelper routes host-node actions back
     * through this view, so handling ACTION_SCROLL_FORWARD/BACKWARD here scrolls the grid by one
     * viewport through the same [EmojiPanelState] the touch path uses.
     */
    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        when (action) {
            AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD ->
                if (scrollOneViewport(forward = true)) return true
            AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD ->
                if (scrollOneViewport(forward = false)) return true
        }
        return super.performAccessibilityAction(action, arguments)
    }
}
