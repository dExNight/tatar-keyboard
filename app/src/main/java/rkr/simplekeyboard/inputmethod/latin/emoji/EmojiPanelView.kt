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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import rkr.simplekeyboard.inputmethod.R

/**
 * E2b-1 emoji panel: a deliberate stub. It is a second full-keyboard-height surface that replaces
 * the [rkr.simplekeyboard.inputmethod.keyboard.MainKeyboardView] while it is shown; the two never
 * draw at the same time. This step carries only the surface, its insets and two functional keys —
 * "АБВ" (back to the letters) and delete. The emoji grid, the snapshot, scrolling and recents are
 * later sub-phases (E2b-2 / E2b-3) and are intentionally absent here.
 *
 * No allocations happen in [onDraw] or [onTouchEvent]; every Paint and Rect is built once. Nothing
 * here reads, logs or transmits the field text: the delete key routes through the same
 * `LatinIME.onCodeInput(CODE_DELETE)` path a normal key press uses.
 */
class EmojiPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs, R.attr.mainKeyboardViewStyle) {

    /** Which of the two functional keys, if any, a touch fell on. */
    interface Listener {
        /** The "АБВ" key: hide the panel and return to the letter keyboard. */
        fun onEmojiPanelBackToKeyboard()

        /** The delete key: one backspace through the ordinary code-input path. */
        fun onEmojiPanelDelete()
    }

    private companion object {
        private const val NO_KEY = -1
        private const val KEY_BACK = 0
        private const val KEY_DELETE = 1
        private const val LABEL_TEXT_SIZE_SP = 18f
        private const val PRESSED_ALPHA = 90
        private const val BACK_LABEL = "АБВ"

        // U+232B ERASE TO THE LEFT: a system glyph, so the stub ships no font of its own.
        private const val DELETE_LABEL = "\u232B"
    }

    private val backgroundPaint = Paint()
    private val separatorPaint = Paint()
    private val pressedPaint = Paint()
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            LABEL_TEXT_SIZE_SP,
            resources.displayMetrics,
        )
    }
    private val fontMetrics = Paint.FontMetrics()
    private val hitBounds = Rect()

    private var panelHeightPx = 0
    private var pressedKey = NO_KEY
    private var labelBaseline = 0f
    private var listener: Listener? = null

    init {
        val themeColors = context.theme.obtainStyledAttributes(
            intArrayOf(
                R.attr.keyNormalBackgroundColor,
                R.attr.functionalTextColor,
                R.attr.keyPressedBackgroundColor,
            ),
        )
        backgroundPaint.color = themeColors.getColor(0, Color.LTGRAY)
        labelPaint.color = themeColors.getColor(1, Color.DKGRAY)
        pressedPaint.color = themeColors.getColor(2, Color.GRAY)
        themeColors.recycle()
        separatorPaint.color = withAlpha(labelPaint.color, PRESSED_ALPHA)
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    /** Matches the panel to the current keyboard height so insets stay identical to the keyboard. */
    fun setPanelHeightPx(heightPx: Int) {
        if (heightPx > 0 && heightPx != panelHeightPx) {
            panelHeightPx = heightPx
            requestLayout()
        }
    }

    /** Drops transient press state and the listener before the view is detached or replaced. */
    fun release() {
        pressedKey = NO_KEY
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
        labelPaint.getFontMetrics(fontMetrics)
        labelBaseline = height / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val mid = w / 2f
        canvas.drawRect(0f, 0f, w, h, backgroundPaint)
        if (pressedKey == KEY_BACK) {
            canvas.drawRect(0f, 0f, mid, h, pressedPaint)
        } else if (pressedKey == KEY_DELETE) {
            canvas.drawRect(mid, 0f, w, h, pressedPaint)
        }
        canvas.drawLine(mid, 0f, mid, h, separatorPaint)
        canvas.drawText(BACK_LABEL, mid / 2f, labelBaseline, labelPaint)
        canvas.drawText(DELETE_LABEL, mid + mid / 2f, labelBaseline, labelPaint)
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedKey = keyAt(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val nowPressed = keyAt(event.x, event.y)
                if (nowPressed != pressedKey) {
                    pressedKey = nowPressed
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val released = keyAt(event.x, event.y)
                pressedKey = NO_KEY
                invalidate()
                if (released == KEY_BACK) {
                    listener?.onEmojiPanelBackToKeyboard()
                } else if (released == KEY_DELETE) {
                    listener?.onEmojiPanelDelete()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedKey = NO_KEY
                invalidate()
                return true
            }
        }
        return false
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    private fun keyAt(x: Float, y: Float): Int {
        hitBounds.set(0, 0, width, height)
        if (!hitBounds.contains(x.toInt(), y.toInt())) {
            return NO_KEY
        }
        return if (x < width / 2f) KEY_BACK else KEY_DELETE
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
