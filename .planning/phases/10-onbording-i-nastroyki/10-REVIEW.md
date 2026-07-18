# Phase 10 Review — Онбординг и настройки (SetupActivity + manifest + dialog removal)

**Diff:** `0083715..HEAD` | **Depth:** standard | **Date:** 2026-07-18
**Files in scope:** `SetupActivity.kt` (new, 127 lines), `setup_activity.xml` (new), `values/strings-setup.xml` (new), `values-ru/strings-setup.xml` (new), `AndroidManifest.xml` (+8/−2), `SettingsActivity.java` (−58)

## Verdict: ✅ Approve — 1 medium, 3 low, 4 informational findings. No blockers.

Detection logic is correct on both build variants (the `"$packageName/"` prefix with the
slash rules out cross-variant false positives), the null-default-IME case is handled, the
live-state/no-persistence design with the `onWindowFocusChanged` refresh is the right
pattern for the floating picker, the backgrounded-completion edge case resolves correctly,
manifest has exactly one LAUNCHER, and all hard constraints hold (no new deps, no INTERNET,
no Compose, no directBootAware on activities).

---

## Findings

### M1 (Medium) — `startActivity(ACTION_INPUT_METHOD_SETTINGS)` unguarded on the first-launch screen

`SetupActivity.kt:65` calls `startActivity` with no `ActivityNotFoundException` handling and
no `resolveActivity` check. On mainstream phones (Xiaomi/Samsung — the target devices) this
intent always resolves, but stripped OEM builds, Android TV, and some kiosk/enterprise
profiles ship without the input-method settings screen, and there the very first tap a new
user makes crashes the app. This is the worst possible surface for a crash (first launch →
uninstall + 1-star review). Note the removed dialog code had the same unguarded call, so
this is not a regression — but the old path was behind Settings, the new one is the LAUNCHER
entry point.

**Fix (3 lines):** wrap in `try/catch (ActivityNotFoundException)`; on catch, fall back to
`showInputMethodPicker()`-style guidance or a toast pointing at system settings manually.
Same guard is cheap insurance for the `updateStepStates()` IMM calls — see L2.

### L1 (Low) — Step status marks are visual-only for TalkBack

The project just completed a dedicated accessibility phase (09), so the onboarding screen
should meet the same bar:

- `setup_step1_status` / `setup_step2_status` toggle between `"1"/"2"` and `"✓"` (U+2713).
  A TalkBack user focusing the mark hears "1" or (engine-dependent) "check mark" with no
  context. There is no `contentDescription`/`stateDescription` conveying "step 1 — done".
- `setup_step2_card` dimming via `alpha = 0.4f` (`SetupActivity.kt:122`) is purely visual;
  it also drops text contrast below comfortable levels for low-vision users while step 1 is
  incomplete.

Mitigating: the buttons themselves are properly labeled via `android:text`, and TalkBack
does announce their disabled state, so overall progress *is* inferable — hence Low, not
Medium. **Fix:** set `contentDescription` on the status TextViews inside `updateStepStates()`
(e.g. reuse done/pending strings), and consider `importantForAccessibility="no"` on the
number marks if the description lands on the card instead.

### L2 (Low) — Defensive try/catch around `enabledInputMethodList` was dropped

The removed `SettingsActivity.onStart()` wrapped `isInputMethodOfThisImeEnabled()` in
`try/catch (Exception)` with a log — upstream Simple Keyboard added that guard deliberately
(the pattern usually follows real crash reports; IMM binder calls have historically thrown
on some OEM builds). `SetupActivity.updateStepStates()` calls the same API bare, now on
every `onResume`/focus gain of the launcher activity. Consider restoring the guard (treat
exception as "not enabled") to keep the first-run screen crash-proof on quirky firmware.

### L3 (Low) — `FLAG_ACTIVITY_NEW_TASK` on the settings intent is unnecessary and slightly harmful

`SetupActivity.kt:66`: the flag was carried over from the old dialog code, where the context
requirement justified it. From an Activity context it is not needed, and it forces the
system settings screen into a separate task — the user sees two entries in Recents during
onboarding, and if a stale settings task exists it may resurface at the wrong screen instead
of opening the input-method list. AOSP LatinIME's SetupWizardActivity launches this intent
without the flag. **Fix:** drop `.addFlags(...)`.

---

## Informational

### I1 — `setup_message` is now dead in base + ~45 locale overlays

The dialog was the only consumer. The string remains in `values/strings.xml:126` and ~45
inherited locale files. Release has `minifyEnabled` but **no `shrinkResources`**, so all of
it ships in the APK. Individually tiny, but the 3 MB budget is a stated hard constraint and
this is now the precedent for dead upstream strings. Cleanup candidate for a follow-up
(delete from base; overlays without the base entry just produce lint noise until removed).

### I2 — `findViewById` re-lookups on every refresh

`updateStepStates()` performs six `findViewById` traversals per `onResume`/focus gain.
Harmless here (shallow hierarchy, not a hot path — the zero-allocation budget applies to
the keyboard draw loop, not this screen), but caching the six views as fields in `onCreate`
would match the tightness of the rest of the codebase. Style nit only.

### I3 — Inner card columns use `match_parent` inside a horizontal LinearLayout

`setup_activity.xml:64,110`: a `match_parent`-width child following a fixed 32dp sibling in
a horizontal LinearLayout does resolve to "remaining width" in practice, but the canonical
idiom is `layout_width="0dp"` + `layout_weight="1"`. Works as-is on all API levels; change
only if touching the file anyway.

### I4 — `method.xml` still points `settingsActivity` at SettingsActivity — verified correct

The gear icon in system keyboard settings should open real settings, not onboarding, so
leaving `android:settingsActivity` on `SettingsActivity` (`method.xml:24`) is the right
call. Noting it here so a future reviewer doesn't "fix" it to point at SetupActivity.

---

## Scrutiny areas

### 1. SetupActivity detection & lifecycle — ✅ correct

- **`isImeEnabled`** (`SetupActivity.kt:89-92`): exact `packageName` equality against
  `enabledInputMethodList` entries — same predicate as the removed Java code. Correct on
  debug (`org.tatarkeyboard.ime.debug`) because `InputMethodInfo.getPackageName()` reflects
  the actual applicationId.
- **`isImeCurrent`** (`SetupActivity.kt:99-103`): `DEFAULT_INPUT_METHOD` null (no default
  IME set — fresh emulators, post-factory-reset) → `?: return false` ✅. Empty string →
  `startsWith` false ✅. The trailing slash in `"$packageName/"` prevents the release
  package (`org.tatarkeyboard.ime`) matching the debug IME ID
  (`org.tatarkeyboard.ime.debug/…` starts with `org.tatarkeyboard.ime.` — no slash) ✅.
  Class-name abbreviation in the ID is impossible here since namespace
  (`rkr.simplekeyboard.inputmethod`) is not prefixed by applicationId, so
  `flattenToShortString` never shortens it ✅.
- **`showInputMethodPicker` timing**: called from a click listener on a resumed, focused
  Activity — the API 28+ foreground requirement is satisfied ✅.
- **Refresh strategy**: `onResume` + `onWindowFocusChanged(hasFocus=true)` is exactly right
  for the picker (floating window → no onResume on dismissal). `updateStepStates()` is
  idempotent and touches only text/enabled/alpha/visibility — none of which can retrigger a
  focus change, so no refresh loop ✅.
- **Backgrounded completion**: user enables + selects the IME elsewhere, returns → onResume
  re-reads both states from the system → done block shown. No stored flag to go stale ✅.
- **Back navigation**: back from SetupActivity exits to launcher (fine); Done →
  SettingsActivity + `finish()` → back from Settings exits cleanly without returning to a
  completed wizard ✅.
- **Leaks**: no handlers, no registered listeners, no static refs ✅.
- **Insets**: API 30+ listener on `setup_root` returning `WindowInsets.CONSUMED` handles
  the targetSdk 37 enforced edge-to-edge; below R the non-edge-to-edge theme needs nothing ✅.

### 2. Layout & strings — ✅ with L1

- **en/ru parity**: 10/10 translatable strings; ru overlay correctly omits the three
  `translatable="false"` marks ✅.
- **Brand**: title is `@string/english_ime_name` by reference; no brand text in
  strings-setup ✅ (the "(dev)" placeholder in the app name is pre-existing, out of scope).
- **RTL**: `layout_marginStart` throughout, `supportsRtl` already true ✅.
- **Accessibility**: buttons labeled via text ✅; status marks and alpha-dimming — see L1.

### 3. SettingsActivity post-removal — ✅ clean

No references remain to `isInputMethodOfThisImeEnabled`, `TAG`, or the dialog anywhere in
the source tree (verified by grep). Remaining behavior (insets, ActionBar home, fragment
routing via `getIntent()` override + `FragmentUtils.isValidFragment`) untouched ✅. Only
`setup_message` orphaned — see I1.

### 4. Manifest — ✅ correct

- Exactly **one** `LAUNCHER` intent-filter, on SetupActivity ✅.
- SetupActivity `exported="true"` — required for LAUNCHER; extras ignored by design (KDoc
  states it), only fixed system intents launched — no injection surface ✅.
- SettingsActivity keeps `exported="true"` with **no** intent-filter — required: on API 33+
  the system Settings app cannot launch a non-exported `settingsActivity` from `method.xml` ✅.
- No `directBootAware` on either activity (service keeps it) ✅; no taskAffinity/launchMode
  overrides — default single standard task ✅.

### 5. Kotlin style — ✅ consistent

Matches the phase-09 Kotlin conventions: KDoc citing the AOSP pattern being followed,
rationale comments for non-obvious choices (debug-suffix prefix match, deliberate
non-directBootAware), idiomatic `any {}` / elvis / expression bodies, Java interop via `R`
and `SettingsActivity` without converting Java code ✅.

### Constraints check

| Constraint | Status |
|---|---|
| Zero new dependencies | ✅ none added |
| No INTERNET permission | ✅ manifest unchanged (VIBRATE only) |
| No Compose | ✅ classic View/XML |
| APK budget | ✅ +1 small activity/layout/strings; note I1 for dead-string hygiene |

---

## Recommended follow-ups (priority order)

1. **M1 + L2 + L3** — one small hardening pass on `SetupActivity.kt`: try/catch on the
   settings intent, guard on IMM reads, drop `FLAG_ACTIVITY_NEW_TASK` (~10 lines total).
2. **L1** — contentDescription on step status marks; can ride the deferred TalkBack UAT
   bundle from phase 09.
3. **I1** — delete `setup_message` from base strings in a cleanup commit.
