---
phase: 09
fixed_at: 2026-07-19
review_path: .planning/phases/09-dostupnost/09-REVIEW.md
iteration: 1
findings_in_scope: 2
fixed: 2
skipped: 0
status: all_fixed
---

# Phase 09: Code Review Fix Report

**Fixed at:** 2026-07-19
**Source review:** .planning/phases/09-dostupnost/09-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 2 (M1, L1)
- Fixed: 2
- Skipped: 0

## Fixed Issues

### M1: Tab key announced as «Unknown» on tablet PC-QWERTY

**Files modified:** `app/src/main/java/rkr/simplekeyboard/inputmethod/accessibility/KeyDescriptionMapper.kt`, `app/src/main/res/values/strings-a11y.xml`, `app/src/main/res/values-ru/strings-a11y.xml`
**Commit:** ea03764
**Applied fix:** Added `Constants.CODE_TAB -> R.string.spoken_description_tab` branch to the mapper's `when` (placed after `CODE_DELETE`, before `CODE_SPACE`), plus the backing strings `spoken_description_tab` — en "Tab", ru «Табуляция». The icon-only tab key (`!icon/tab_key|!code/key_tab`, reachable via `xml-sw600dp/rows_pcqwerty.xml`) no longer falls through to the `else` branch → `spoken_description_unknown`. `CODE_TAB = '\t'` (9) is positive and disjoint from the Tatar codepoints (≥ 0x0497) and other functional codes, so ordering and collision safety are preserved. en/ru string parity maintained (27/27).

### L1: `isTextEntryKey = true` rationale undocumented

**Files modified:** `app/src/main/java/rkr/simplekeyboard/inputmethod/accessibility/KeyboardAccessibilityDelegate.kt`
**Commit:** ea03764
**Applied fix:** Added a 5-line comment above the `node.isTextEntryKey = true` assignment explaining the framework contract (the flag means "a text entry key that is part of a keyboard or keypad", i.e. any IME key), why it is set on every key (TalkBack lift-to-type across the whole keyboard including delete/shift), that this matches Gboard/LatinIME, and a "do not fix to letters-only" note so a future reviewer does not narrow it. No behavioral change.

## Verification

- Tier 1: re-read all four modified sections; fixes present, surrounding code intact.
- Tier 2: `./gradlew assembleDebug` — BUILD SUCCESSFUL (the `setBoundsInParent` deprecation warning at `KeyboardAccessibilityDelegate.kt:83` is pre-existing, unrelated to this fix).
- No-internet guard: `scripts/check-no-internet.sh` level 1 (source manifest) and level 2 (built APK, aapt2 dump) both pass — only `android.permission.VIBRATE` present.

## Deferred / not in scope

- **L2** (uppercase non-Tatar letters get no «Заглавная» prefix): reviewer marked "not worth code now"; folded into the deferred TalkBack UAT script (phases 1–9 bundle).
- **I3** (moreKeys panels invisible to TalkBack — caps-lock/diacritics unreachable): backlog for the a11y follow-up phase.
- **I4** (`PointerTracker.java:461` `deltaT = eventTime` noise-filter fork divergence): pre-existing, unrelated to this phase; backlog note.

---

_Fixed: 2026-07-19_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
