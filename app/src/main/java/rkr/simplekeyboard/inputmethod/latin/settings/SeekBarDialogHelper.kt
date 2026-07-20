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

package rkr.simplekeyboard.inputmethod.latin.settings

import android.app.Activity
import android.app.AlertDialog
import android.widget.SeekBar
import android.widget.TextView
import rkr.simplekeyboard.inputmethod.R

/**
 * Seek-bar value dialog for the View-based settings screens: the dialog
 * half of [SeekBarDialogPreference] extracted into a plain AlertDialog
 * over the same layout/seek_bar_dialog.xml. The preference class itself
 * stays untouched — the legacy language screens still use it until S2.
 *
 * Value semantics (progress↔value mapping, step clipping, the
 * OK/Cancel/Default button contract) are ported 1:1.
 */
object SeekBarDialogHelper {

    /** Mirror of [SeekBarDialogPreference.ValueProxy] for the new screens. */
    interface ValueProxy {
        fun readValue(key: String): Int
        fun readDefaultValue(key: String): Int
        fun writeValue(value: Int, key: String)
        fun writeDefaultValue(key: String)
        fun getValueText(value: Int): String
        fun feedbackValue(value: Int)
    }

    fun show(
        activity: Activity,
        title: CharSequence,
        key: String,
        minValue: Int,
        maxValue: Int,
        stepValue: Int,
        proxy: ValueProxy,
        onValueChanged: () -> Unit
    ): AlertDialog {
        fun clipValue(value: Int): Int {
            val clipped = value.coerceIn(minValue, maxValue)
            return if (stepValue <= 1) clipped else clipped - clipped % stepValue
        }

        val view = activity.layoutInflater.inflate(R.layout.seek_bar_dialog, null)
        val valueView = view.findViewById<TextView>(R.id.seek_bar_dialog_value)
        val seekBar = view.findViewById<SeekBar>(R.id.seek_bar_dialog_bar)
        seekBar.max = maxValue - minValue
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                valueView.text = proxy.getValueText(clipValue(progress + minValue))
            }

            override fun onStartTrackingTouch(bar: SeekBar) {}

            override fun onStopTrackingTouch(bar: SeekBar) {
                proxy.feedbackValue(clipValue(bar.progress + minValue))
            }
        })

        val value = proxy.readValue(key)
        valueView.text = proxy.getValueText(value)
        seekBar.progress = clipValue(value) - minValue

        return AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                proxy.writeValue(clipValue(seekBar.progress + minValue), key)
                onValueChanged()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.button_default) { _, _ ->
                proxy.writeDefaultValue(key)
                onValueChanged()
            }
            .show()
    }
}
