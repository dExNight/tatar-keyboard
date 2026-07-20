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
import android.app.backup.BackupManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceActivity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.compat.PreferenceManagerCompat
import rkr.simplekeyboard.inputmethod.keyboard.KeyboardLayoutSet
import rkr.simplekeyboard.inputmethod.latin.AudioAndHapticFeedbackManager

/**
 * View-based settings screens (IOS-REDESIGN.md S1): the four static
 * screens (root, Preferences, Key press, Appearance) rendered as iOS-style
 * grouped cards built from the row_link / row_switch / row_value layouts —
 * no android.preference, no new dependencies.
 *
 * Navigation is a single Activity swapping pages inside one scaffold
 * ([R.layout.settings_screen]) with a manual back stack, so system back
 * and the back chevron pop pages exactly like separate activities would,
 * without four manifest entries. The item composition of every screen is
 * 1:1 with the legacy prefs*.xml screens it replaces.
 *
 * The pieces of the legacy harness this class carries over
 * (SubScreenFragment / the settings fragments / InputMethodSettingsImpl):
 *  - device-protected SharedPreferences via [PreferenceManagerCompat]
 *  - [BackupManager.dataChanged] after every preference change
 *  - enterprise restrictions ([Settings.ACTIVE_RESTRICTIONS]) disable rows
 *  - dependency chains: sound volume ⇢ sound_on, IME switch ⇢ language key
 *  - [KeyboardLayoutSet.onKeyboardThemeChanged] for the number-row and
 *    special-chars toggles so the open keyboard rebuilds its layout live
 *  - vibrate row hidden without a vibrator; on-screen-keyboard row only
 *    on API 36+ (as in the legacy fragments)
 *
 * The languages screens stay on the legacy PreferenceActivity stack until
 * S2 — the root row bridges to [SettingsActivity] with an explicit
 * fragment extra, which is the one path its router still serves.
 */
class SettingsHostActivity : Activity() {

    private enum class Screen(val titleRes: Int) {
        ROOT(R.string.english_ime_name),
        PREFERENCES(R.string.settings_screen_preferences),
        KEY_PRESS(R.string.settings_screen_key_press),
        APPEARANCE(R.string.settings_screen_appearance)
    }

    companion object {
        private val TAG = SettingsHostActivity::class.java.simpleName
        private const val STATE_SCREEN = "screen"
        private const val STATE_BACK_STACK = "back_stack"
        private const val DISABLED_ALPHA = 0.4f
        private const val PERCENTAGE_FLOAT = 100.0f
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var scrollView: ScrollView
    private lateinit var contentView: LinearLayout
    private lateinit var titleView: TextView

    private val backStack = ArrayDeque<Screen>()
    private var currentDialog: AlertDialog? = null
    private var currentScreen = Screen.ROOT
    private var restrictionKeys: Set<String> = emptySet()

    /**
     * Registered on the device-protected prefs exactly like
     * SubScreenFragment.onCreate: schedule a backup on every change, and
     * clear the keyboard layout cache for the two keys whose legacy
     * fragments did so. Everything else reaches the live keyboard through
     * the Settings singleton's own listener on the same prefs file.
     */
    private val prefChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            BackupManager(this).dataChanged()
            if (Settings.PREF_SHOW_NUMBER_ROW == key
                    || Settings.PREF_SHOW_SPECIAL_CHARS == key) {
                KeyboardLayoutSet.onKeyboardThemeChanged()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_screen)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            findViewById<View>(R.id.settings_root).setOnApplyWindowInsetsListener { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
                WindowInsets.CONSUMED
            }
        }

        prefs = PreferenceManagerCompat.getDeviceSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        // The keyboard may not be running when settings open from the
        // system list; init the singletons used here (see LatinIME#onCreate).
        AudioAndHapticFeedbackManager.init(this)

        scrollView = findViewById(R.id.settings_scroll)
        contentView = findViewById(R.id.settings_content)
        titleView = findViewById(R.id.settings_title)
        findViewById<ImageButton>(R.id.settings_back).setOnClickListener { onBackPressed() }

        val screens = Screen.values()
        savedInstanceState?.getIntArray(STATE_BACK_STACK)?.forEach { ordinal ->
            if (ordinal in screens.indices) backStack.addLast(screens[ordinal])
        }
        val initial = savedInstanceState?.getInt(STATE_SCREEN, 0) ?: 0
        showScreen(screens[initial.coerceIn(screens.indices)])
    }

    override fun onDestroy() {
        // Dismiss any open slider dialog: AlertDialog is not lifecycle-aware,
        // and leaving it attached across a configuration change leaks the
        // window (WindowLeaked). The uncommitted slider value is discarded,
        // matching the legacy DialogPreference behavior closely enough.
        currentDialog?.dismiss()
        currentDialog = null
        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SCREEN, currentScreen.ordinal)
        outState.putIntArray(STATE_BACK_STACK, backStack.map { it.ordinal }.toIntArray())
    }

    override fun onBackPressed() {
        val previous = backStack.removeLastOrNull()
        if (previous != null) {
            showScreen(previous)
        } else {
            super.onBackPressed()
        }
    }

    private fun navigateTo(screen: Screen) {
        backStack.addLast(currentScreen)
        showScreen(screen)
    }

    private fun showScreen(screen: Screen) {
        currentScreen = screen
        restrictionKeys = prefs.getStringSet(Settings.ACTIVE_RESTRICTIONS, null) ?: emptySet()
        titleView.setText(screen.titleRes)
        // The large title is child 0 of the content column and stays.
        contentView.removeViews(1, contentView.childCount - 1)
        when (screen) {
            Screen.ROOT -> buildRootScreen()
            Screen.PREFERENCES -> buildPreferencesScreen()
            Screen.KEY_PRESS -> buildKeyPressScreen()
            Screen.APPEARANCE -> buildAppearanceScreen()
        }
        scrollView.scrollTo(0, 0)
    }

    // ---------------------------------------------------------------------
    // Screens (item composition 1:1 with prefs.xml / prefs_screen_*.xml)
    // ---------------------------------------------------------------------

    private fun buildRootScreen() {
        // The languages entry InputMethodSettingsImpl used to add in code:
        // same title/summary, same PREF_ENABLED_SUBTYPES restriction, and it
        // bridges into the legacy languages screens until they move in S2.
        addCard(listOf(
            linkRow(R.string.keyboard_languages, R.string.keyboard_languages_summary,
                    Settings.PREF_ENABLED_SUBTYPES) { openLegacyLanguagesScreen() }))
        addCard(listOf(
            linkRow(R.string.settings_screen_preferences) { navigateTo(Screen.PREFERENCES) },
            linkRow(R.string.settings_screen_key_press) { navigateTo(Screen.KEY_PRESS) },
            linkRow(R.string.settings_screen_appearance) { navigateTo(Screen.APPEARANCE) }))
        addCard(listOf(
            linkRow(R.string.privacy_policy) { openUrl(getString(R.string.privacy_policy_url)) },
            linkRow(R.string.license) { openUrl(getString(R.string.license_url)) }))
    }

    private fun buildPreferencesScreen() {
        val rows = ArrayList<View>()
        rows.add(switchRow(Settings.PREF_AUTO_CAP, true,
                R.string.auto_cap, R.string.auto_cap_summary))
        rows.add(switchRow(Settings.PREF_SHOW_SPECIAL_CHARS, true,
                R.string.show_special_chars, R.string.show_special_chars_summary))
        var imeSwitchRow: View? = null
        rows.add(switchRow(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, true,
                R.string.show_language_switch_key,
                R.string.show_language_switch_key_summary) { checked ->
            imeSwitchRow?.let {
                setRowEnabled(it, checked && !isRestricted(Settings.PREF_ENABLE_IME_SWITCH))
            }
        })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            rows.add(switchRow(Settings.PREF_USE_ON_SCREEN, false,
                    R.string.pref_use_on_screen, R.string.pref_use_on_screen_summary))
        }
        val imeRow = switchRow(Settings.PREF_ENABLE_IME_SWITCH, false,
                R.string.pref_enable_ime_switch, R.string.pref_enable_ime_switch_summary)
        imeSwitchRow = imeRow
        rows.add(imeRow)
        rows.add(switchRow(Settings.PREF_SPACE_SWIPE, true,
                R.string.space_swipe, R.string.space_swipe_summary))
        rows.add(switchRow(Settings.PREF_DELETE_SWIPE, false,
                R.string.delete_swipe, R.string.delete_swipe_summary))
        addCard(rows)
        // android:dependency="pref_show_language_switch_key" from the legacy screen.
        setRowEnabled(imeRow,
                prefs.getBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, true)
                        && !isRestricted(Settings.PREF_ENABLE_IME_SWITCH))
    }

    private fun buildKeyPressScreen() {
        val rows = ArrayList<View>()
        if (AudioAndHapticFeedbackManager.getInstance().hasVibrator()) {
            rows.add(switchRow(Settings.PREF_VIBRATE_ON,
                    resources.getBoolean(R.bool.config_default_vibration_enabled),
                    R.string.vibrate_on_keypress, R.string.vibrate_on_keypress_summary))
        }
        val soundDefault = resources.getBoolean(R.bool.config_default_sound_enabled)
        var volumeRow: View? = null
        rows.add(switchRow(Settings.PREF_SOUND_ON, soundDefault,
                R.string.sound_on_keypress, R.string.sound_on_keypress_summary) { checked ->
            volumeRow?.let {
                setRowEnabled(it,
                        checked && !isRestricted(Settings.PREF_KEYPRESS_SOUND_VOLUME))
            }
        })
        val volume = valueRow(Settings.PREF_KEYPRESS_SOUND_VOLUME,
                R.string.prefs_keypress_sound_volume_settings,
                0, 100, 0, keypressSoundVolumeProxy())
        volumeRow = volume
        rows.add(volume)
        rows.add(switchRow(Settings.PREF_POPUP_ON,
                resources.getBoolean(R.bool.config_default_key_preview_popup),
                R.string.popup_on_keypress, R.string.popup_on_keypress_summary))
        rows.add(valueRow(Settings.PREF_KEY_LONGPRESS_TIMEOUT,
                R.string.prefs_key_longpress_timeout_settings,
                resources.getInteger(R.integer.config_min_longpress_timeout),
                resources.getInteger(R.integer.config_max_longpress_timeout),
                resources.getInteger(R.integer.config_longpress_timeout_step),
                keyLongpressTimeoutProxy()))
        addCard(rows)
        // android:dependency="sound_on" from the legacy screen. A managed
        // restriction on the volume key must win over the dependency.
        setRowEnabled(volume, prefs.getBoolean(Settings.PREF_SOUND_ON, soundDefault)
                && !isRestricted(Settings.PREF_KEYPRESS_SOUND_VOLUME))
    }

    private fun buildAppearanceScreen() {
        addCard(listOf(
            switchRow(Settings.PREF_SHOW_NUMBER_ROW, false,
                    R.string.show_number_row, R.string.show_number_row_summary),
            valueRow(Settings.PREF_KEYBOARD_HEIGHT,
                    R.string.prefs_keyboard_height_settings,
                    resources.getInteger(R.integer.config_min_keyboar_height),
                    resources.getInteger(R.integer.config_max_keyboar_height),
                    resources.getInteger(R.integer.config_keyboar_height_step),
                    keyboardHeightProxy()),
            valueRow(Settings.PREF_BOTTOM_OFFSET_PORTRAIT,
                    R.string.prefs_bottom_offset_portrait_settings,
                    resources.getInteger(R.integer.config_min_bottom_offset_portrait),
                    resources.getInteger(R.integer.config_max_bottom_offset_portrait),
                    resources.getInteger(R.integer.config_bottom_offset_step),
                    bottomOffsetProxy())))
    }

    // ---------------------------------------------------------------------
    // Row builders
    // ---------------------------------------------------------------------

    private fun inflateRow(layoutRes: Int, titleRes: Int, summaryRes: Int): View {
        val row = layoutInflater.inflate(layoutRes, contentView, false)
        row.findViewById<TextView>(R.id.row_title).setText(titleRes)
        if (summaryRes != 0) {
            row.findViewById<TextView>(R.id.row_summary)?.apply {
                setText(summaryRes)
                visibility = View.VISIBLE
            }
        }
        return row
    }

    private fun linkRow(titleRes: Int, summaryRes: Int = 0, restrictionKey: String? = null,
                        onClick: () -> Unit): View {
        val row = inflateRow(R.layout.row_link, titleRes, summaryRes)
        row.setOnClickListener { onClick() }
        if (isRestricted(restrictionKey)) {
            setRowEnabled(row, false)
        }
        return row
    }

    private fun switchRow(key: String, defaultValue: Boolean, titleRes: Int, summaryRes: Int,
                          onCheckedChanged: ((Boolean) -> Unit)? = null): View {
        val row = inflateRow(R.layout.row_switch, titleRes, summaryRes)
        val switchView = row.findViewById<Switch>(R.id.row_switch)
        switchView.isChecked = prefs.getBoolean(key, defaultValue)
        switchView.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(key, checked).apply()
            onCheckedChanged?.invoke(checked)
        }
        // The whole row is one tap target and one TalkBack node that
        // presents itself as the switch it toggles.
        row.setOnClickListener { switchView.toggle() }
        row.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View,
                                                           info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Switch::class.java.name
                info.isCheckable = true
                info.isChecked = switchView.isChecked
            }
        }
        if (isRestricted(key)) {
            setRowEnabled(row, false)
        }
        return row
    }

    private fun valueRow(key: String, titleRes: Int, minValue: Int, maxValue: Int, stepValue: Int,
                         proxy: SeekBarDialogHelper.ValueProxy): View {
        val row = inflateRow(R.layout.row_value, titleRes, 0)
        val valueView = row.findViewById<TextView>(R.id.row_value)
        valueView.text = proxy.getValueText(proxy.readValue(key))
        row.setOnClickListener {
            currentDialog?.dismiss()
            currentDialog = SeekBarDialogHelper.show(this, getString(titleRes), key,
                    minValue, maxValue, stepValue, proxy) {
                valueView.text = proxy.getValueText(proxy.readValue(key))
            }
        }
        if (isRestricted(key)) {
            setRowEnabled(row, false)
        }
        return row
    }

    /**
     * Appends a card group to the content column: per-position rounded
     * backgrounds (wave D drawables) and a 1px inset hairline between rows,
     * none after the last.
     */
    private fun addCard(rows: List<View>) {
        rows.forEachIndexed { index, row ->
            row.background = getDrawable(when {
                rows.size == 1 -> R.drawable.app_card_bg
                index == 0 -> R.drawable.app_card_top
                index == rows.size - 1 -> R.drawable.app_card_bottom
                else -> R.drawable.app_card_middle
            })
            if (index == 0) {
                (row.layoutParams as LinearLayout.LayoutParams).topMargin = dp(20)
            } else {
                contentView.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    background = getDrawable(R.drawable.app_row_divider)
                })
            }
            contentView.addView(row)
        }
    }

    private fun setRowEnabled(row: View, enabled: Boolean) {
        row.isEnabled = enabled
        // Dim the row contents, not the row itself: the row carries the card
        // background segment, and fading it would punch a hole in the card.
        if (row is ViewGroup) {
            val childAlpha = if (enabled) 1f else DISABLED_ALPHA
            for (i in 0 until row.childCount) {
                row.getChildAt(i).alpha = childAlpha
            }
        } else {
            row.alpha = if (enabled) 1f else DISABLED_ALPHA
        }
        row.findViewById<Switch>(R.id.row_switch)?.isEnabled = enabled
    }

    private fun isRestricted(key: String?): Boolean {
        return key != null && restrictionKeys.contains(key)
    }

    private fun dp(value: Int): Int {
        return Math.round(value * resources.displayMetrics.density)
    }

    // ---------------------------------------------------------------------
    // Row actions
    // ---------------------------------------------------------------------

    /**
     * Bridge to the legacy languages screens (S2 will replace them): an
     * explicit fragment extra is the one request SettingsActivity's router
     * still serves on the PreferenceActivity path instead of bouncing back
     * here — so this cannot loop.
     */
    private fun openLegacyLanguagesScreen() {
        val intent = Intent(this, SettingsActivity::class.java)
        intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,
                LanguagesSettingsFragment::class.java.name)
        startActivity(intent)
    }

    private fun openUrl(uri: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Browser not found")
        }
    }

    // ---------------------------------------------------------------------
    // Seek-bar value proxies, ported 1:1 from KeyPressSettingsFragment and
    // AppearanceSettingsFragment
    // ---------------------------------------------------------------------

    private fun keypressSoundVolumeProxy() = object : SeekBarDialogHelper.ValueProxy {
        override fun readValue(key: String): Int =
                (Settings.readKeypressSoundVolume(prefs) * PERCENTAGE_FLOAT).toInt()

        override fun readDefaultValue(key: String): Int =
                (Settings.readDefaultKeypressSoundVolume() * PERCENTAGE_FLOAT).toInt()

        override fun writeValue(value: Int, key: String) {
            prefs.edit().putFloat(key, value / PERCENTAGE_FLOAT).apply()
        }

        override fun writeDefaultValue(key: String) {
            prefs.edit().remove(key).apply()
        }

        override fun getValueText(value: Int): String =
                if (value < 0) getString(R.string.settings_system_default)
                else value.toString()

        override fun feedbackValue(value: Int) {
            AudioAndHapticFeedbackManager.getInstance().playSoundEffect(
                    AudioManager.FX_KEYPRESS_STANDARD, value / PERCENTAGE_FLOAT)
        }
    }

    private fun keyLongpressTimeoutProxy() = object : SeekBarDialogHelper.ValueProxy {
        override fun readValue(key: String): Int =
                Settings.readKeyLongpressTimeout(prefs, resources)

        override fun readDefaultValue(key: String): Int =
                Settings.readDefaultKeyLongpressTimeout(resources)

        override fun writeValue(value: Int, key: String) {
            prefs.edit().putInt(key, value).apply()
        }

        override fun writeDefaultValue(key: String) {
            prefs.edit().remove(key).apply()
        }

        override fun getValueText(value: Int): String =
                getString(R.string.abbreviation_unit_milliseconds, value)

        override fun feedbackValue(value: Int) {}
    }

    private fun keyboardHeightProxy() = object : SeekBarDialogHelper.ValueProxy {
        override fun readValue(key: String): Int =
                Math.round(Settings.readKeyboardHeight(prefs, 1f) * PERCENTAGE_FLOAT)

        override fun readDefaultValue(key: String): Int =
                Math.round(PERCENTAGE_FLOAT)

        override fun writeValue(value: Int, key: String) {
            prefs.edit().putFloat(key, value / PERCENTAGE_FLOAT).apply()
        }

        override fun writeDefaultValue(key: String) {
            prefs.edit().remove(key).apply()
        }

        override fun getValueText(value: Int): String =
                if (value < 0) getString(R.string.settings_system_default)
                else getString(R.string.abbreviation_unit_percent, value)

        override fun feedbackValue(value: Int) {}
    }

    private fun bottomOffsetProxy() = object : SeekBarDialogHelper.ValueProxy {
        override fun readValue(key: String): Int =
                Settings.readBottomOffsetPortrait(prefs)

        override fun readDefaultValue(key: String): Int = Settings.DEFAULT_BOTTOM_OFFSET

        override fun writeValue(value: Int, key: String) {
            prefs.edit().putInt(key, value).apply()
        }

        override fun writeDefaultValue(key: String) {
            prefs.edit().remove(key).apply()
        }

        override fun getValueText(value: Int): String =
                if (value < 0) getString(R.string.settings_system_default)
                else getString(R.string.abbreviation_unit_dp, value)

        override fun feedbackValue(value: Int) {}
    }
}
