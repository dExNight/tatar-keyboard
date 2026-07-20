/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2025 Raimondas Rimkus
 * Copyright (C) 2021 wittmane
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

import android.app.ActionBar;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

import rkr.simplekeyboard.inputmethod.latin.utils.FragmentUtils;

public class SettingsActivity extends PreferenceActivity {
    private static final String DEFAULT_FRAGMENT = SettingsFragment.class.getName();

    /**
     * Fragments that must still be served by the legacy PreferenceActivity
     * path (language screens, until S2 migrates them). Any other entry
     * — including a bare launch from LatinIME.launchSettings or the system
     * settings panel — is redirected to SettingsHostActivity.
     */
    private static boolean isLegacyFragment(final String fragmentName) {
        return LanguagesSettingsFragment.class.getName().equals(fragmentName)
                || SingleLanguageSettingsFragment.class.getName().equals(fragmentName);
    }

    @Override
    protected void onCreate(final Bundle savedState) {
        // Redirect to the new View-based host unless this is a legacy
        // fragment request (languages screens, until S2). The decision reads
        // the raw intent — the getIntent() override below injects
        // DEFAULT_FRAGMENT and must not mask a bare launch. Restores from a
        // saved state never redirect: such an instance already survived the
        // check as a legacy screen. The host never starts this activity
        // without a legacy fragment extra, so this cannot loop.
        final String rawFragment = super.getIntent().getStringExtra(EXTRA_SHOW_FRAGMENT);
        final boolean redirect = savedState == null
                && (rawFragment == null || !isLegacyFragment(rawFragment));

        super.onCreate(savedState);

        if (redirect) {
            final Intent host = new Intent(this, SettingsHostActivity.class);
            // Reuse a host instance already in this task (repeated taps on the
            // keyboard's settings key) instead of stacking a second one.
            host.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(host);
            finish();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // com.android.internal.R.id.prefs_container in
            // https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/res/layout/preference_list_content.xml
            // OEM overlays may replace this layout with a different view hierarchy, so skip
            // the edge-to-edge margin fix instead of crashing on an unexpected parent.
            final android.view.ViewParent parent = getListView().getParent();
            final android.view.ViewParent grandparent = parent == null ? null : parent.getParent();
            if (grandparent instanceof View) {
                final View container = (View) grandparent;
                container.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                    android.graphics.Insets insets = windowInsets.getInsets(WindowInsets.Type.systemBars());
                    if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
                        mlp.topMargin = insets.top;
                        mlp.leftMargin = insets.left;
                        mlp.bottomMargin = insets.bottom;
                        mlp.rightMargin = insets.right;
                        view.setLayoutParams(mlp);
                    }
                    return WindowInsets.CONSUMED;
                });
            }
        }

        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            super.onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public Intent getIntent() {
        final Intent intent = super.getIntent();
        final String fragment = intent.getStringExtra(EXTRA_SHOW_FRAGMENT);
        // Some OEM settings apps launch this activity with a foreign fragment name in the
        // extra, which would make PreferenceActivity throw in isValidFragment. Fall back to
        // the default fragment for anything that isn't in our allow-list.
        if (fragment == null || !FragmentUtils.isValidFragment(fragment)) {
            intent.putExtra(EXTRA_SHOW_FRAGMENT, DEFAULT_FRAGMENT);
        }
        intent.putExtra(EXTRA_NO_HEADERS, true);
        return intent;
    }

    @Override
    public boolean isValidFragment(final String fragmentName) {
        return FragmentUtils.isValidFragment(fragmentName);
    }
}
