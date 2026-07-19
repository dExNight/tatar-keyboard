---
phase: 06-ios-skin-canvas
fixed_at: 2026-07-18
review_path: .planning/phases/06-ios-skin-canvas/06-REVIEW.md
iteration: 1
findings_in_scope: 1
fixed: 1
skipped: 5
status: all_fixed
---

# Phase 06: Code Review Fix Report

**Fixed at:** 2026-07-18
**Source review:** .planning/phases/06-ios-skin-canvas/06-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 1 (M1 only; m1–m5 explicitly deferred by request)
- Fixed: 1
- Deferred (out of scope): 5

## Fixed Issues

### M1: iOS key selectors drop checkable/checked/active states → caps-lock visual state lost; shift-locked icon can be invisible in light theme

**Files modified:** `app/src/main/res/drawable/sym_keyboard_shift_locked.xml`, `app/src/main/res/drawable/ios_key_normal.xml`, `app/src/main/res/drawable/ios_key_functional.xml`, `app/src/main/res/values/colors.xml`, `app/src/main/res/values-night/colors.xml`
**Commit:** b7cd67d
**Applied fix:** Addressed both coupled root causes.

1. Icon color (invisible white-on-white): `sym_keyboard_shift_locked.xml` hardcoded `@android:color/white` fills plus a vector-level `android:tint="?attr/functionalTextColor"`. Rewrote it to mirror the working `sym_keyboard_shift.xml` — the theme attribute `?attr/functionalTextColor` is now applied directly to the path `fillColor`/`strokeColor` (both the arrow and the caps bar), and the unreliable vector-level tint was dropped. The locked icon can no longer render same-color as its key in either theme.

2. Missing checked-state background (caps-lock looked unlocked): added a `state_checkable + state_checked` item to both `ios_key_normal.xml` and `ios_key_functional.xml`, ahead of the pressed/default items (mirroring upstream `btn_keyboard_key.xml` ordering). The checked item uses the same 1dp-shadow + roundRect-5dp layer-list as the other states but fills with a new palette color `@color/ios_key_checked` so a caps-locked sticky key is visually distinct.

3. Palette + light/dark parity: added `ios_key_checked` to both `values/colors.xml` (`#A2A6B0`, a highlighted mid-tone against the `#FFFFFF` normal key / `#B3B7C0` functional key) and `values-night/colors.xml` (`#9A9A9A`, lighter than the `#6B6B6B` normal / `#474747` functional dark keys so the locked key reads as "engaged" rather than receding). Both palettes now render the caps-lock state visibly. A dedicated color (rather than reusing `ios_key_functional`) was necessary because in dark mode the functional color is darker than the normal key and would have made the locked key recede.

**Verification:**
- Tier 1: re-read all five edited files; edits present, surrounding markup intact.
- Tier 2: `xmllint --noout` clean on all five files.
- Build: `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- Privacy gate: `scripts/check-no-internet.sh` on the built APK → Level 1 + Level 2 OK, VIBRATE-only permission set (no INTERNET).

Note: this is a visual/state-mapping change verified structurally and by build; the exact on-device appearance of the caps-lock highlight in the light `alphabetShiftLocked` layout still warrants a human glance during UAT, as the reviewer recommended.

## Deferred Issues (out of scope for this fix run)

Per the fix request, only M1 (major) was in scope. The five minors below were explicitly deferred as pre-existing upstream behavior or later-phase scope and were **not** modified.

### m1: Partial-redraw branch still allocates
**File:** `app/src/main/java/.../KeyboardView.java:291`
**Reason:** Deferred — pre-existing upstream behavior; reviewer marked low priority ("fix later with the same pattern if profiling shows it").

### m2: BACKGROUND_TYPE_EMPTY keys would draw a full key background (latent)
**File:** `app/src/main/res/drawable/ios_key_normal.xml` / `ios_key_functional.xml`
**Reason:** Deferred — latent only; no current layout uses `backgroundType="empty"` (reviewer confirmed via grep of res/xml). No behavior change today.

### m3: A11y contentDescription quality (raw icon names / "undefined")
**File:** `.../KeyboardAccessibilityDelegate.kt:87-88`
**Reason:** Deferred — explicitly tracked as phase-9 scope in the class KDoc; no crash risk.

### m4: Hover hit-testing inherits touch correction offsets
**File:** `.../MainKeyboardView.java:272-273`
**Reason:** Deferred — harmless for finger-sized keys; flagged for full TalkBack work in phase 9.

### m5: Kotlin stdlib + androidx transitive deps in APK
**File:** `app/build.gradle`
**Reason:** Deferred — well inside the 3 MB budget (release 701 KB); R8 strips unused stdlib. Dependency already pinned (customview:1.1.0). Monitoring note only.

---

_Fixed: 2026-07-18_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
