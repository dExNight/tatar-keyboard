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
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
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

    companion object {
        private val TAG = SetupActivity::class.java.simpleName
    }

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
            // Some stripped OEM/enterprise builds ship without the
            // input-method settings screen — never crash the first tap.
            try {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, R.string.setup_error_no_settings,
                        Toast.LENGTH_LONG).show()
            }
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

    /**
     * Step 1 — is this IME enabled in the system? The IMM read is guarded
     * like upstream's SettingsActivity did it: the binder call has thrown
     * on some OEM builds, and an exception here must not crash the
     * first-run screen — treat it as "not enabled".
     */
    private fun isImeEnabled(): Boolean {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        return try {
            imm.enabledInputMethodList.any { it.packageName == packageName }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in check if input method is enabled", e)
            false
        }
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

        // The visual marks ("1"/"2"/"✓") mean nothing to TalkBack — each
        // status mark carries a spoken done/pending description instead.
        val step1Status = findViewById<TextView>(R.id.setup_step1_status)
        step1Status.text =
                getString(if (enabled) R.string.setup_step_done_mark
                          else R.string.setup_step1_number)
        step1Status.contentDescription =
                getString(if (enabled) R.string.setup_step_status_done
                          else R.string.setup_step_status_pending)
        findViewById<Button>(R.id.setup_step1_button).isEnabled = !enabled

        val step2Status = findViewById<TextView>(R.id.setup_step2_status)
        step2Status.text =
                getString(if (current) R.string.setup_step_done_mark
                          else R.string.setup_step2_number)
        step2Status.contentDescription =
                getString(if (current) R.string.setup_step_status_done
                          else R.string.setup_step_status_pending)
        // The dimming below is decorative; the locked state of step 2 is
        // conveyed non-visually by the button's disabled semantics.
        findViewById<Button>(R.id.setup_step2_button).isEnabled = enabled && !current
        findViewById<View>(R.id.setup_step2_card).alpha = if (enabled) 1f else 0.4f

        findViewById<View>(R.id.setup_done_block).visibility =
                if (current) View.VISIBLE else View.GONE
    }
}
