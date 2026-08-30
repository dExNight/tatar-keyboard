// Copyright (C) 2026 Tatar Keyboard contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//          http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.tatarkeyboard.baselineprofile;

import android.content.Intent;
import android.os.SystemClock;

import androidx.benchmark.macro.MacrobenchmarkScope;
import androidx.benchmark.macro.junit4.BaselineProfileRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import kotlin.Unit;

/**
 * Phase 4b: Baseline Profile generator for the IME (dev-only, never packaged).
 *
 * CUJ (the real user hot path, not a launcher activity): the IME process is started
 * BY THE SYSTEM when an editable field gains focus — so each iteration cold-kills the
 * app process, focuses the try-it field of the app's own SetupActivity, waits for the
 * keyboard, types a Tatar word with real key taps (PointerTracker -> InputLogic ->
 * dictionary suggestion engine -> suggestion strip) and opens the emoji panel
 * (long-press comma). This covers process start, onCreateInputView, layout XML
 * parsing/inflation, first frame render, the background suggestion engine and the
 * emoji panel.
 *
 * Run: ./gradlew :app:generateReleaseBaselineProfile  (connected API 34 emulator).
 */
@RunWith(AndroidJUnit4.class)
public class ImeBaselineProfileGenerator {

    private static final String PACKAGE_NAME = "org.tatarkeyboard.ime";
    // NOTE: the manifest declares the service as ".latin.LatinIME", but relative
    // component names resolve against the *package*, not the source namespace —
    // "org.tatarkeyboard.ime/.latin.LatinIME" is NOT valid for `ime enable/set`.
    // The real id is what `dumpsys input_method` reports as mCurMethodId.
    private static final String IME_ID =
            PACKAGE_NAME + "/rkr.simplekeyboard.inputmethod.latin.LatinIME";
    private static final String SETUP_ACTIVITY =
            "rkr.simplekeyboard.inputmethod.latin.setup.SetupActivity";

    @Rule
    public BaselineProfileRule baselineProfileRule = new BaselineProfileRule();

    @Test
    public void generate() {
        baselineProfileRule.collect(
                PACKAGE_NAME,
                /* maxIterations = */ 15,
                /* stableIterations = */ 3,
                /* outputFilePrefix = */ null,
                /* includeInStartupProfile = */ true,
                scope -> {
                    runCuj(scope);
                    return Unit.INSTANCE;
                });
    }

    private void runCuj(MacrobenchmarkScope scope) {
        UiDevice device = scope.getDevice();
        // Cold start: the IME process must be (re)started by the system below.
        // NOTE: Macrobenchmark's kill flushes ART profiles and FORCE-STOPS the
        // package, and force-stopping the *selected* IME resets
        // Settings.Secure.DEFAULT_INPUT_METHOD — so the selection must be
        // (re)applied AFTER the kill, never before it.
        scope.killProcess();
        enableAndSelectIme(device);

        startSetupActivity(scope, device);

        UiObject2 field = device.wait(
                Until.findObject(By.clazz("android.widget.EditText")), 10_000);
        if (field == null) {
            // One retry: re-assert the selection and relaunch (the done-block
            // EditText only exists while this IME is the current one).
            enableAndSelectIme(device);
            startSetupActivity(scope, device);
            field = device.wait(
                    Until.findObject(By.clazz("android.widget.EditText")), 10_000);
        }
        if (field == null) {
            throw new IllegalStateException("Try-it EditText not found in SetupActivity");
        }
        // Focusing the field binds and cold-starts the IME.
        field.click();
        // Give the keyboard time to appear and render its first frame.
        SystemClock.sleep(2_500);

        // Type the Tatar word "сәлам" with real key taps (coordinates as screen
        // fractions, calibrated on the 1080x2280 API 34 AVD; see KeyGeom).
        tapKeyFraction(device, KeyGeom.KEY_S);
        tapKeyFraction(device, KeyGeom.KEY_AE);
        tapKeyFraction(device, KeyGeom.KEY_L);
        tapKeyFraction(device, KeyGeom.KEY_A);
        tapKeyFraction(device, KeyGeom.KEY_M);
        SystemClock.sleep(1_000);

        // Emoji panel: long-press the comma key, wait, then leave the panel.
        device.swipe(
                KeyGeom.x(device, KeyGeom.KEY_COMMA),
                KeyGeom.y(device, KeyGeom.KEY_COMMA),
                KeyGeom.x(device, KeyGeom.KEY_COMMA),
                KeyGeom.y(device, KeyGeom.KEY_COMMA),
                /* steps = */ 60);
        SystemClock.sleep(1_500);
        device.pressBack();
        SystemClock.sleep(500);

        device.pressHome();
    }

    private void startSetupActivity(MacrobenchmarkScope scope, UiDevice device) {
        Intent intent = new Intent();
        intent.setClassName(PACKAGE_NAME, SETUP_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        scope.startActivityAndWait(intent);
    }

    private void tapKeyFraction(UiDevice device, float[] fraction) {
        device.click(KeyGeom.x(device, fraction), KeyGeom.y(device, fraction));
        SystemClock.sleep(120);
    }

    private void enableAndSelectIme(UiDevice device) {
        try {
            device.executeShellCommand("ime enable " + IME_ID);
            device.executeShellCommand("ime set " + IME_ID);
            // Verify the selection stuck — a stale/unknown id fails silently in
            // executeShellCommand, and the done-block EditText only exists when
            // this IME is the current one.
            for (int attempt = 0; attempt < 10; attempt++) {
                String current = device.executeShellCommand(
                        "settings get secure default_input_method").trim();
                if (current.equals(IME_ID)) {
                    return;
                }
                SystemClock.sleep(300);
                device.executeShellCommand("ime set " + IME_ID);
            }
            throw new IllegalStateException("IME did not stay selected: " + IME_ID);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to enable/select the IME", e);
        }
    }

    /** Screen-fraction key geometry, calibrated on the tt_suggest_a14 AVD (1080x2280,
     *  Tatar layout): verified that the five taps commit exactly "сәлам" into the
     *  try-it field and that long-pressing comma opens the emoji panel. */
    private static final class KeyGeom {
        // {xFraction, yFraction} of key centers on the Tatar layout.
        static final float[] KEY_S = {0.3324f, 0.8474f};
        static final float[] KEY_AE = {0.0833f, 0.6583f};
        static final float[] KEY_L = {0.6815f, 0.7851f};
        static final float[] KEY_A = {0.3176f, 0.7851f};
        static final float[] KEY_M = {0.4231f, 0.8474f};
        static final float[] KEY_COMMA = {0.2009f, 0.9075f};

        static int x(UiDevice device, float[] fraction) {
            return Math.round(device.getDisplayWidth() * fraction[0]);
        }

        static int y(UiDevice device, float[] fraction) {
            return Math.round(device.getDisplayHeight() * fraction[1]);
        }
    }
}
