/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2021 wittmane
 * Copyright (C) 2017 Raimondas Rimkus
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

package rkr.simplekeyboard.inputmethod.latin.settings;

import android.content.Context;
import android.content.RestrictionsManager;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.preference.PreferenceScreen;

import java.util.Set;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.compat.PreferenceManagerCompat;
import rkr.simplekeyboard.inputmethod.latin.RichInputMethodManager;

/* package private */ class InputMethodSettingsImpl {
    private Preference mSubtypeEnablerPreference;

    /**
     * Initialize internal states of this object.
     * @param context the context for this application.
     * @param prefScreen a PreferenceScreen of PreferenceActivity or PreferenceFragment.
     * @return true if this application is an IME and has two or more subtypes, false otherwise.
     */
    public boolean init(final Context context, final PreferenceScreen prefScreen) {
        RichInputMethodManager.init(context);

        final SharedPreferences prefs = PreferenceManagerCompat.getDeviceSharedPreferences(context);
        final RestrictionsManager restrictionsMgr = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);
        final Set<String> restrictionKeys = Settings.loadRestrictions(restrictionsMgr, prefs);

        mSubtypeEnablerPreference = new Preference(context);
        mSubtypeEnablerPreference.setTitle(R.string.keyboard_languages);
        mSubtypeEnablerPreference.setFragment(LanguagesSettingsFragment.class.getName());
        mSubtypeEnablerPreference.setEnabled(!restrictionKeys.contains(Settings.PREF_ENABLED_SUBTYPES));
        prefScreen.addPreference(mSubtypeEnablerPreference);
        updateEnabledSubtypeList();
        return true;
    }

    public void updateEnabledSubtypeList() {
        if (mSubtypeEnablerPreference != null) {
            // The enabled languages are listed on the languages screen itself; show a call to
            // action here instead of duplicating them.
            mSubtypeEnablerPreference.setSummary(R.string.keyboard_languages_summary);
        }
    }
}
