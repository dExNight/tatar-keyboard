/*
 * Copyright (C) 2014 The Android Open Source Project
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

package rkr.simplekeyboard.inputmethod.latin.utils;

import android.app.Dialog;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.Window;

import rkr.simplekeyboard.inputmethod.R;

public final class DialogUtils {
    private DialogUtils() {
        // This utility class is not publicly instantiable.
    }

    public static Context getPlatformDialogThemeContext(final Context context) {
        // Because {@link AlertDialog.Builder.create()} doesn't honor the specified theme with
        // createThemeContextWrapper=false, the result dialog box has unneeded paddings around it.
        return new ContextThemeWrapper(context, R.style.platformDialogTheme);
    }

    /**
     * Audit 2026-09-02, C5: makes the dialog drop touches delivered while another window obscures
     * it. The IME-attached dialogs float over OTHER apps' screens, where a tapjacking overlay is
     * possible at all, and the settings dialogs keep the gesture consistent with the app's
     * layouts, which already set {@code android:filterTouchesWhenObscured} in XML.
     *
     * <p>The flag lives on views, not windows, so it is set on the decor view — the one ViewGroup
     * every touch into the dialog passes through — and the decor view only exists once the dialog
     * is shown, hence the show listener. Must be installed before {@code show()} and must not be
     * combined with another OnShowListener on the same dialog (none of the app's dialogs has
     * one).</p>
     */
    public static void filterObscuredTouches(final Dialog dialog) {
        dialog.setOnShowListener(d -> {
            final Window window = dialog.getWindow();
            if (window != null) {
                window.getDecorView().setFilterTouchesWhenObscured(true);
            }
        });
    }
}
