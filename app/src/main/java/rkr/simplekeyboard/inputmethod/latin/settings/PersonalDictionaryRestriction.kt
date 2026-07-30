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

/**
 * The ONE-DIRECTIONAL enterprise restriction of the personal dictionary (E4b).
 *
 * The generic branch of `Settings.loadRestrictions` writes the policy value in EITHER direction
 * (`prefsEditor.putBoolean(key, appRestrictions.getBoolean(key))`) and `SettingsHostActivity`
 * then greys the row out for every active key. For every other setting that is fine. For this one
 * it would mean a device administrator or work-profile owner could force the keyboard to SAVE the
 * words their user types to disk and lock the user out of turning it off — the exact opposite of a
 * feature whose whole promise is opt-in with default OFF.
 *
 * So the policy is applied only when it RESTRICTS:
 *  - policy `false` → `false` is written into preferences and the settings row is greyed out;
 *  - policy `true`  → nothing is written and the row stays live, leaving the choice where it was:
 *    with the user.
 *
 * The stated goal of the restriction ("a 'do not remember typed words' policy must be
 * expressible") is one-directional by definition, so it is met in full.
 *
 * The logic lives here, apart from `Settings`, precisely so both directions are covered by plain
 * JVM tests: `Settings.loadRestrictions` needs a `RestrictionsManager` and real `SharedPreferences`
 * and cannot run off-device.
 */
object PersonalDictionaryRestriction {

    /**
     * True when a policy carrying [policyValue] may write the preference at all. Only the
     * restrictive direction writes; the permissive one writes nothing.
     */
    @JvmStatic
    fun writesPreference(policyValue: Boolean): Boolean = !policyValue

    /**
     * The value written when [writesPreference] allows it — always `false`. Stated as its own
     * function so no call site can pass the policy value through by accident.
     */
    @JvmStatic
    fun valueToWrite(): Boolean = false

    /**
     * The set stored in `Settings.ACTIVE_RESTRICTIONS`, which is what greys settings rows out.
     *
     * Identical to [policyKeys] except for one case: a PERMISSIVE personal-dictionary policy is
     * dropped, so the row stays enabled. [personalDictionaryPolicy] is null when the policy bundle
     * does not carry the key at all (an absent key must not be read as `false`).
     */
    @JvmStatic
    fun effectiveRestrictionKeys(
        policyKeys: Set<String>,
        personalDictionaryPolicy: Boolean?,
    ): Set<String> {
        if (personalDictionaryPolicy != true) return policyKeys
        if (!policyKeys.contains(Settings.PREF_PERSONAL_DICTIONARY)) return policyKeys
        return policyKeys.filterTo(LinkedHashSet()) { it != Settings.PREF_PERSONAL_DICTIONARY }
    }
}
