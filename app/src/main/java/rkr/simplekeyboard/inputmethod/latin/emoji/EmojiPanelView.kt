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
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
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
    private var listener: Listener? = null

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
        invalidate()
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
        recycleVelocityTracker()
        state.cancelGesture()
        listener = null
        visibility = GONE
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
    }

    override fun computeScroll() {
        super.computeScroll()
        if (!scroller.computeScrollOffset()) {
            return
        }
        // Physics live in EmojiFling: clamp the scroller's position into range, then keep animating
        // until the scroller settles. onDraw still paints only the visible rows.
        state.setScrollY(EmojiFling.clampScroll(scroller.currY, state.maxScrollY()))
        postInvalidateOnAnimation()
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

    private fun dispatchTarget(target: Int) {
        when {
            EmojiPanelState.isCell(target) -> listener?.onEmojiPanelPick(state.entryAt(target))
            EmojiPanelState.isBack(target) -> listener?.onEmojiPanelBackToKeyboard()
            EmojiPanelState.isTab(target) -> if (state.setActiveCategory(EmojiPanelState.tabIndexOf(target))) invalidate()
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
}
