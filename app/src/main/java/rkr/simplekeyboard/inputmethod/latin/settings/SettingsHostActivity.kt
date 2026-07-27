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
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.util.Locale
import java.util.TreeSet
import rkr.simplekeyboard.inputmethod.R
import rkr.simplekeyboard.inputmethod.compat.PreferenceManagerCompat
import rkr.simplekeyboard.inputmethod.keyboard.KeyboardLayoutSet
import rkr.simplekeyboard.inputmethod.latin.AudioAndHapticFeedbackManager
import rkr.simplekeyboard.inputmethod.latin.RichInputMethodManager
import rkr.simplekeyboard.inputmethod.latin.common.LocaleUtils
import rkr.simplekeyboard.inputmethod.latin.emoji.EmojiPanelController
import rkr.simplekeyboard.inputmethod.latin.utils.LocaleResourceUtils
import rkr.simplekeyboard.inputmethod.latin.utils.SubtypeLocaleUtils

/**
 * View-based settings screens (IOS-REDESIGN.md S1 + S2): every screen of
 * the app (root, Preferences, Key press, Appearance, Languages and the
 * per-language layouts screen) rendered as iOS-style grouped cards built
 * from the row_link / row_switch / row_value layouts — no
 * android.preference, no new dependencies.
 *
 * Navigation is a single Activity swapping pages inside one scaffold
 * ([R.layout.settings_screen]) with a manual back stack, so system back
 * and the back chevron pop pages exactly like separate activities would,
 * without six manifest entries. The item composition of every screen is
 * 1:1 with the legacy screen it replaces.
 *
 * The pieces of the legacy harness this class carries over
 * (SubScreenFragment / the settings fragments / InputMethodSettingsImpl /
 * LanguagesSettingsFragment / SingleLanguageSettingsFragment):
 *  - device-protected SharedPreferences via [PreferenceManagerCompat]
 *  - the legacy harness scheduled a backup after every preference change;
 *    that is deliberately NOT carried over. E2b-3 turns backup off
 *    (android:allowBackup="false") and excludes every app data domain from
 *    both backup editions (res/xml/data_extraction_rules.xml for API 31+,
 *    res/xml/backup_rules.xml for 24–30), so a per-change backup request
 *    would have nothing to back up and is gone
 *  - enterprise restrictions ([Settings.ACTIVE_RESTRICTIONS]) disable rows
 *  - dependency chains: sound volume ⇢ sound_on, IME switch ⇢ language key
 *  - [KeyboardLayoutSet.onKeyboardThemeChanged] for the number-row and
 *    special-chars toggles so the open keyboard rebuilds its layout live
 *  - vibrate row hidden without a vibrator; on-screen-keyboard row only
 *    on API 36+ (as in the legacy fragments)
 *  - [RichInputMethodManager.init] before any subtype access, and content
 *    rebuilt on every [onStart] (the legacy fragments' buildContent)
 *
 * [Screen.LANGUAGE_DETAIL] is the one parameterized screen: its locale
 * lives in [detailLocale] and rides along in the saved instance state.
 */
class SettingsHostActivity : Activity() {

    private enum class Screen(val titleRes: Int) {
        ROOT(R.string.english_ime_name),
        PREFERENCES(R.string.settings_screen_preferences),
        KEY_PRESS(R.string.settings_screen_key_press),
        APPEARANCE(R.string.settings_screen_appearance),
        LANGUAGES(R.string.keyboard_languages),
        // Title is the language display name, set dynamically in showScreen.
        LANGUAGE_DETAIL(0)
    }

    companion object {
        private val TAG = SettingsHostActivity::class.java.simpleName
        private const val STATE_SCREEN = "screen"
        private const val STATE_BACK_STACK = "back_stack"
        private const val STATE_DETAIL_LOCALE = "detail_locale"
        private const val DISABLED_ALPHA = 0.4f
        private const val PERCENTAGE_FLOAT = 100.0f
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var richImm: RichInputMethodManager
    private lateinit var scrollView: ScrollView
    private lateinit var contentView: LinearLayout
    private lateinit var titleView: TextView

    private val backStack = ArrayDeque<Screen>()
    private var currentDialog: AlertDialog? = null
    private var currentScreen = Screen.ROOT
    /** Locale string of the language shown by [Screen.LANGUAGE_DETAIL]. */
    private var detailLocale: String? = null
    private var restrictionKeys: Set<String> = emptySet()

    /**
     * Registered on the device-protected prefs exactly like
     * SubScreenFragment.onCreate, minus its backup request: E2b-3 disables
     * backup entirely, so the only job left here is to clear the keyboard
     * layout cache for the keys whose legacy fragments did so. Everything
     * else reaches the live keyboard through the Settings singleton's own
     * listener on the same prefs file.
     */
    private val prefChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (Settings.PREF_SHOW_NUMBER_ROW == key
                    || Settings.PREF_SHOW_EMOJI_KEY == key
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
        RichInputMethodManager.init(this)
        richImm = RichInputMethodManager.getInstance()

        scrollView = findViewById(R.id.settings_scroll)
        contentView = findViewById(R.id.settings_content)
        titleView = findViewById(R.id.settings_title)
        findViewById<ImageButton>(R.id.settings_back).setOnClickListener { onBackPressed() }

        val screens = Screen.values()
        savedInstanceState?.getIntArray(STATE_BACK_STACK)?.forEach { ordinal ->
            if (ordinal in screens.indices) backStack.addLast(screens[ordinal])
        }
        detailLocale = savedInstanceState?.getString(STATE_DETAIL_LOCALE)
        val initial = savedInstanceState?.getInt(STATE_SCREEN, 0) ?: 0
        currentScreen = screens[initial.coerceIn(screens.indices)]
        // The content itself is built in onStart, which follows right after.
    }

    override fun onStart() {
        super.onStart()
        // Rebuild the visible screen every time the activity comes back to
        // the foreground — the legacy fragments' buildContent-in-onStart.
        // The language list, enabled-layout summaries and restriction state
        // are all re-read, so external changes are picked up.
        showScreen(currentScreen)
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
        outState.putString(STATE_DETAIL_LOCALE, detailLocale)
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
        val detail = if (screen == Screen.LANGUAGE_DETAIL) detailLocale else null
        if (screen == Screen.LANGUAGE_DETAIL && detail == null) {
            // Defensive: a detail screen without its locale (unexpected
            // restore) falls back to the languages list.
            showScreen(Screen.LANGUAGES)
            return
        }
        detailLocale = detail
        currentScreen = screen
        restrictionKeys = prefs.getStringSet(Settings.ACTIVE_RESTRICTIONS, null) ?: emptySet()
        if (detail != null) {
            titleView.text = LocaleResourceUtils.getLocaleDisplayNameInSystemLocale(detail)
        } else {
            titleView.setText(screen.titleRes)
        }
        // The large title is child 0 of the content column and stays.
        contentView.removeViews(1, contentView.childCount - 1)
        when (screen) {
            Screen.ROOT -> buildRootScreen()
            Screen.PREFERENCES -> buildPreferencesScreen()
            Screen.KEY_PRESS -> buildKeyPressScreen()
            Screen.APPEARANCE -> buildAppearanceScreen()
            Screen.LANGUAGES -> buildLanguagesScreen()
            Screen.LANGUAGE_DETAIL -> buildLanguageDetailScreen(detail!!)
        }
        scrollView.scrollTo(0, 0)
    }

    // ---------------------------------------------------------------------
    // Screens (item composition 1:1 with prefs.xml / prefs_screen_*.xml)
    // ---------------------------------------------------------------------

    private fun buildRootScreen() {
        // The languages entry InputMethodSettingsImpl used to add in code:
        // same title/summary, same PREF_ENABLED_SUBTYPES restriction.
        addCard(listOf(
            linkRow(R.string.keyboard_languages, R.string.keyboard_languages_summary,
                    Settings.PREF_ENABLED_SUBTYPES) { navigateTo(Screen.LANGUAGES) }))
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
        rows.add(switchRow(Settings.PREF_TATAR_SUGGESTIONS, false,
                R.string.tatar_suggestions, R.string.tatar_suggestions_summary))
        addCard(rows)
        // android:dependency="pref_show_language_switch_key" from the legacy screen.
        setRowEnabled(imeRow,
                prefs.getBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, true)
                        && !isRestricted(Settings.PREF_ENABLE_IME_SWITCH))
        // A data action, not an appearance toggle: its own card at the end of Preferences, next to
        // the Tatar-suggestions switch. Erasing recent emoji is a confirmed, one-way action; it does
        // not belong on the Appearance screen where the emoji-key toggle lives.
        addCard(listOf(actionRow(R.string.clear_recent_emoji) {
            showClearRecentEmojiDialog()
        }))
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
            switchRow(Settings.PREF_SHOW_EMOJI_KEY, true,
                    R.string.show_emoji_key, R.string.show_emoji_key_summary),
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
    // Languages screens, ported from LanguagesSettingsFragment and
    // SingleLanguageSettingsFragment (S2.1)
    // ---------------------------------------------------------------------

    /**
     * "Keyboard languages": a card with one row per enabled language
     * (summary lists its enabled layouts), then an actions card with
     * "Add language" and — with more than one language — "Remove language",
     * both opening the same multi-choice dialogs the legacy screen used.
     */
    private fun buildLanguagesScreen() {
        val comparator = LocaleUtils.LocaleComparator()
        val usedLocales = TreeSet<Locale>(comparator)
        for (subtype in richImm.getEnabledSubtypes(false)) {
            usedLocales.add(subtype.localeObject)
        }
        val unusedLocales = TreeSet<Locale>(comparator)
        for (localeString in SubtypeLocaleUtils.getSupportedLocales()) {
            val locale = LocaleUtils.constructLocaleFromString(localeString)
            if (!usedLocales.contains(locale)) {
                unusedLocales.add(locale)
            }
        }

        val usedValues = usedLocales.map { LocaleUtils.getLocaleString(it) }
        val unusedValues = unusedLocales.map { LocaleUtils.getLocaleString(it) }

        addSectionHeader(getString(R.string.user_languages))
        addCard(usedValues.map { localeString ->
            val layoutNames = richImm.getEnabledSubtypesForLocale(localeString)
                    .joinToString(", ") { it.layoutDisplayName }
            linkRow(LocaleResourceUtils.getLocaleDisplayNameInSystemLocale(localeString),
                    layoutNames) {
                detailLocale = localeString
                navigateTo(Screen.LANGUAGE_DETAIL)
            }
        }, spacedFromPrevious = false)

        val actions = ArrayList<View>()
        actions.add(actionRow(R.string.add_language) {
            showLocalePickerDialog(unusedValues, R.string.add_language, R.string.add,
                    allowAllChecked = true) { checkedValues ->
                // Enable the default layout for all of the checked languages.
                for (localeString in checkedValues) {
                    richImm.addSubtype(
                            SubtypeLocaleUtils.getDefaultSubtype(localeString, resources))
                }
            }
        })
        if (usedValues.size > 1) {
            actions.add(actionRow(R.string.remove_language) {
                showLocalePickerDialog(usedValues, R.string.remove_language, R.string.remove,
                        allowAllChecked = false) { checkedValues ->
                    // Disable all of the layouts of the checked languages.
                    for (localeString in checkedValues) {
                        for (subtype in richImm.getEnabledSubtypesForLocale(localeString)) {
                            richImm.removeSubtype(subtype)
                        }
                    }
                }
            })
        }
        addCard(actions)
    }

    /**
     * Multi-choice language dialog shared by add/remove, ported from
     * LanguagesSettingsFragment.showMultiChoiceDialog: the positive button
     * is only enabled while at least one item is checked and — unless
     * [allowAllChecked] — at least one is unchecked (removing every
     * language at once must stay impossible). On accept the checked locale
     * strings go to [onAccept] and the screen is rebuilt.
     */
    private fun showLocalePickerDialog(localeValues: List<String>, titleRes: Int,
                                       positiveButtonRes: Int, allowAllChecked: Boolean,
                                       onAccept: (List<String>) -> Unit) {
        val names = localeValues.map {
            LocaleResourceUtils.getLocaleDisplayNameInSystemLocale(it) as CharSequence
        }.toTypedArray()
        val checkedItems = BooleanArray(localeValues.size)
        currentDialog?.dismiss()
        val dialog = AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setMultiChoiceItems(names, checkedItems) { dialogInterface, _, _ ->
                    var hasCheckedItem = false
                    var hasUncheckedItem = false
                    for (itemChecked in checkedItems) {
                        if (itemChecked) hasCheckedItem = true else hasUncheckedItem = true
                    }
                    (dialogInterface as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
                            .isEnabled = hasCheckedItem && (hasUncheckedItem || allowAllChecked)
                }
                .setPositiveButton(positiveButtonRes) { _, _ ->
                    onAccept(localeValues.filterIndexed { index, _ -> checkedItems[index] })
                    // Refresh the list of enabled languages (legacy buildContent).
                    showScreen(Screen.LANGUAGES)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        dialog.show()
        // Disable the positive button since nothing is checked by default.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        currentDialog = dialog
    }

    /**
     * Confirmation dialog for "Clear recent emoji", built like [showLocalePickerDialog]: the
     * previous dialog is dismissed, the reference is kept in [currentDialog] and torn down in
     * [onDestroy] so a rotation with the dialog open never leaks the window (WindowLeaked). The
     * buttons are the platform strings; only the row title and the dialog body are our own.
     *
     * The erase goes to [EmojiPanelController.clearRecents], which routes to the live keyboard when
     * one exists in this process (updating its in-memory list and any open panel) and otherwise
     * replaces the medium directly. It runs off the UI thread inside the controller. This screen
     * never reads the recents content — it only asks for the erase.
     */
    private fun showClearRecentEmojiDialog() {
        currentDialog?.dismiss()
        val dialog = AlertDialog.Builder(this)
                .setTitle(R.string.clear_recent_emoji)
                .setMessage(R.string.clear_recent_emoji_confirm)
                .setPositiveButton(R.string.clear_recent_emoji_action) { _, _ ->
                    EmojiPanelController.clearRecents(this)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        dialog.show()
        currentDialog = dialog
    }

    /**
     * Layouts of one language: a switch row per available layout. The last
     * enabled layout's row is locked so a language can never lose all of
     * its layouts — SingleLanguageSettingsFragment's invariant.
     */
    private fun buildLanguageDetailScreen(locale: String) {
        addSectionHeader(getString(R.string.generic_language_layouts,
                LocaleResourceUtils.getLocaleDisplayNameInSystemLocale(locale)))

        val enabledSubtypes = richImm.getEnabledSubtypes(false)
        val subtypes = SubtypeLocaleUtils.getSubtypes(locale, resources)
        val rows = ArrayList<View>()
        val switches = ArrayList<Switch>()

        fun updateLastLayoutLock() {
            val checkedCount = switches.count { it.isChecked }
            switches.forEachIndexed { index, switchView ->
                setRowEnabled(rows[index], !(checkedCount == 1 && switchView.isChecked))
            }
        }

        for (subtype in subtypes) {
            val row = switchRowRaw(subtype.layoutDisplayName, null,
                    enabledSubtypes.contains(subtype)) { checked ->
                val applied = if (checked) {
                    richImm.addSubtype(subtype)
                } else {
                    richImm.removeSubtype(subtype)
                }
                if (applied) {
                    updateLastLayoutLock()
                }
                applied
            }
            rows.add(row)
            switches.add(row.findViewById(R.id.row_switch))
        }
        addCard(rows, spacedFromPrevious = false)
        updateLastLayoutLock()
    }

    // ---------------------------------------------------------------------
    // Row builders
    // ---------------------------------------------------------------------

    private fun inflateRow(layoutRes: Int, title: CharSequence, summary: CharSequence?): View {
        val row = layoutInflater.inflate(layoutRes, contentView, false)
        row.findViewById<TextView>(R.id.row_title).text = title
        if (!summary.isNullOrEmpty()) {
            row.findViewById<TextView>(R.id.row_summary)?.apply {
                text = summary
                visibility = View.VISIBLE
            }
        }
        return row
    }

    private fun inflateRow(layoutRes: Int, titleRes: Int, summaryRes: Int): View =
            inflateRow(layoutRes, getString(titleRes),
                    if (summaryRes != 0) getString(summaryRes) else null)

    /** Link row with dynamic texts (language rows on the Languages screen). */
    private fun linkRow(title: CharSequence, summary: CharSequence?,
                        onClick: () -> Unit): View {
        val row = inflateRow(R.layout.row_link, title, summary)
        row.setOnClickListener { onClick() }
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

    /**
     * Action row (iOS "button cell"): accent-colored title, no chevron —
     * it opens a dialog on the same screen instead of navigating.
     */
    private fun actionRow(titleRes: Int, onClick: () -> Unit): View {
        val row = inflateRow(R.layout.row_link, titleRes, 0)
        row.findViewById<TextView>(R.id.row_title).setTextColor(getColor(R.color.app_accent))
        row.findViewById<View>(R.id.row_chevron).visibility = View.GONE
        row.setOnClickListener { onClick() }
        return row
    }

    private fun switchRow(key: String, defaultValue: Boolean, titleRes: Int, summaryRes: Int,
                          onCheckedChanged: ((Boolean) -> Unit)? = null): View {
        val row = switchRowRaw(getString(titleRes),
                if (summaryRes != 0) getString(summaryRes) else null,
                prefs.getBoolean(key, defaultValue)) { checked ->
            prefs.edit().putBoolean(key, checked).apply()
            onCheckedChanged?.invoke(checked)
            true
        }
        if (isRestricted(key)) {
            setRowEnabled(row, false)
        }
        return row
    }

    /**
     * Backing-store-agnostic switch row: [onToggle] applies the change and
     * returns whether it took effect — on false the switch is silently
     * reverted (the subtype rows need this when an add/remove fails).
     */
    private fun switchRowRaw(title: CharSequence, summary: CharSequence?,
                             initialChecked: Boolean, onToggle: (Boolean) -> Boolean): View {
        val row = inflateRow(R.layout.row_switch, title, summary)
        val switchView = row.findViewById<Switch>(R.id.row_switch)
        switchView.isChecked = initialChecked
        switchView.setOnCheckedChangeListener(object : CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(button: CompoundButton, checked: Boolean) {
                if (!onToggle(checked)) {
                    button.setOnCheckedChangeListener(null)
                    button.isChecked = !checked
                    button.setOnCheckedChangeListener(this)
                }
            }
        })
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
     * Uppercase 13sp section header above a card (iOS grouped-list header).
     * The card that follows should pass spacedFromPrevious = false to
     * [addCard] — the header carries the vertical spacing itself.
     */
    private fun addSectionHeader(text: CharSequence) {
        // The 4-arg constructor applies AppText.SectionHeader as defStyleRes.
        val header = TextView(this, null, 0, R.style.AppText_SectionHeader)
        header.text = text
        header.setPaddingRelative(dp(16), 0, dp(16), 0)
        val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        params.topMargin = dp(20)
        params.bottomMargin = dp(6)
        header.layoutParams = params
        contentView.addView(header)
    }

    /**
     * Appends a card group to the content column: per-position rounded
     * backgrounds (wave D drawables) and a 1px inset hairline between rows,
     * none after the last. [spacedFromPrevious] is turned off when a
     * section header directly above already provides the gap.
     */
    private fun addCard(rows: List<View>, spacedFromPrevious: Boolean = true) {
        rows.forEachIndexed { index, row ->
            row.background = getDrawable(when {
                rows.size == 1 -> R.drawable.app_card_bg
                index == 0 -> R.drawable.app_card_top
                index == rows.size - 1 -> R.drawable.app_card_bottom
                else -> R.drawable.app_card_middle
            })
            if (index == 0) {
                if (spacedFromPrevious) {
                    (row.layoutParams as LinearLayout.LayoutParams).topMargin = dp(20)
                }
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
