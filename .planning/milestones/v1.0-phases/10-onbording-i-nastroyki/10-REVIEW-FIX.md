---
phase: 10
fixed_at: 2026-07-19T00:00:00.000Z
review_path: .planning/phases/10-onbording-i-nastroyki/10-REVIEW.md
iteration: 1
findings_in_scope: 4
fixed: 4
skipped: 0
status: all_fixed
---

# Phase 10: Code Review Fix Report

**Fixed at:** 2026-07-19
**Source review:** .planning/phases/10-onbording-i-nastroyki/10-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 4 (M1, L1, L2, L3 — I1 explicitly deferred per instructions)
- Fixed: 4
- Skipped: 0

## Fixed Issues

### M1: startActivity(ACTION_INPUT_METHOD_SETTINGS) unguarded on the first-launch screen

**Files modified:** `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/setup/SetupActivity.kt`, `app/src/main/res/values/strings-setup.xml`, `app/src/main/res/values-ru/strings-setup.xml`
**Commit:** 79ac956
**Applied fix:** Wrapped `startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))` in `try/catch (ActivityNotFoundException)`. On catch, shows a `Toast.LENGTH_LONG` toast with `R.string.setup_error_no_settings` pointing the user to Settings manually. Added `ActivityNotFoundException`, `Toast`, and `Log` imports.

### L1: Step status marks are visual-only for TalkBack

**Files modified:** `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/setup/SetupActivity.kt`, `app/src/main/res/values/strings-setup.xml`, `app/src/main/res/values-ru/strings-setup.xml`
**Commit:** 79ac956
**Applied fix:** In `updateStepStates()`, the two status `TextView` references are now stored in local variables and `.contentDescription` is set to either `setup_step_status_done` or `setup_step_status_pending` after each `.text` assignment. New strings added in both en base and ru overlay: `"Step done"` / `"Step not done yet"` (en), `"Шаг выполнен"` / `"Шаг не выполнен"` (ru). A comment was added explaining that the alpha dimming on step-2 is decorative and the locked state is conveyed non-visually by the button's disabled semantics.

### L2: Defensive try/catch around enabledInputMethodList was dropped

**Files modified:** `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/setup/SetupActivity.kt`
**Commit:** 79ac956
**Applied fix:** `isImeEnabled()` now wraps `imm.enabledInputMethodList.any { … }` in `try { … } catch (e: Exception)`, returning `false` on exception and logging via `Log.e(TAG, …)`. A `companion object { private val TAG = … }` was added to the class. KDoc updated to explain the guard rationale (upstream pattern, OEM binder throws).

### L3: FLAG_ACTIVITY_NEW_TASK on the settings intent is unnecessary and slightly harmful

**Files modified:** `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/setup/SetupActivity.kt`
**Commit:** 79ac956
**Applied fix:** Dropped `.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)` from the `ACTION_INPUT_METHOD_SETTINGS` intent (fixed together with M1 on the same call site). The intent is now a plain `Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)` with no flag overrides.

## Skipped Issues

None.

## Deferred (out of scope)

### I1: setup_message is now dead in base + ~45 locale overlays

**Reason:** Explicitly deferred to phase 11 / rebrand cleanup per fix instructions. The dead string `setup_message` (and the absence of `shrinkResources`) are noted in the commit message as a backlog item. No source change needed here; the resource will be removed together with other upstream dead strings during the rebrand pass.

---

_Fixed: 2026-07-19_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
