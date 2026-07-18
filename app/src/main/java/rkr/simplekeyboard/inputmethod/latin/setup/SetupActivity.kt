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

package rkr.simplekeyboard.inputmethod.latin.setup

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.latin.settings.SettingsActivity

/**
 * Two-step onboarding screen (SETUP-01), following the AOSP LatinIME
 * SetupWizardActivity pattern in a minimal single-Activity form: step 1
 * enables the IME via the system input-method settings screen, step 2
 * selects it as the current keyboard via the system input-method picker.
 *
 * Both step states are read live from the system on every appearance
 * (enabled input-method list and Settings.Secure.DEFAULT_INPUT_METHOD) —
 * no completion flag is stored, the system is the single source of truth.
 * States are re-read in both [onResume] and [onWindowFocusChanged] because
 * the input-method picker is a floating window and returning from it does
 * not reliably trigger onResume.
 *
 * Deliberately NOT directBootAware: first-time setup happens on an
 * unlocked device. Incoming intent extras are ignored entirely — the
 * Activity only reads system state and starts fixed system intents.
 */
class SetupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setup_activity)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val root = findViewById<View>(R.id.setup_root)
            root.setOnApplyWindowInsetsListener { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
                WindowInsets.CONSUMED
            }
        }

        findViewById<Button>(R.id.setup_step1_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        findViewById<Button>(R.id.setup_step2_button).setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showInputMethodPicker()
        }
        findViewById<Button>(R.id.setup_done_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStepStates()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updateStepStates()
    }

    /** Step 1 — is this IME enabled in the system? */
    private fun isImeEnabled(): Boolean {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any { it.packageName == packageName }
    }

    /**
     * Step 2 — is this IME the current one? Prefix comparison by package
     * keeps the check correct on debug builds where applicationId gets a
     * ".debug" suffix while the IME class name stays unchanged.
     */
    private fun isImeCurrent(): Boolean {
        val current = Settings.Secure.getString(
                contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return false
        return current.startsWith("$packageName/")
    }

    /**
     * Idempotent render of the three onboarding states: nothing done,
     * step 1 done (step 2 becomes active), both done (done block shown).
     */
    private fun updateStepStates() {
        val enabled = isImeEnabled()
        val current = enabled && isImeCurrent()

        findViewById<TextView>(R.id.setup_step1_status).text =
                getString(if (enabled) R.string.setup_step_done_mark
                          else R.string.setup_step1_number)
        findViewById<Button>(R.id.setup_step1_button).isEnabled = !enabled

        findViewById<TextView>(R.id.setup_step2_status).text =
                getString(if (current) R.string.setup_step_done_mark
                          else R.string.setup_step2_number)
        findViewById<Button>(R.id.setup_step2_button).isEnabled = enabled && !current
        findViewById<View>(R.id.setup_step2_card).alpha = if (enabled) 1f else 0.4f

        findViewById<View>(R.id.setup_done_block).visibility =
                if (current) View.VISIBLE else View.GONE
    }
}
