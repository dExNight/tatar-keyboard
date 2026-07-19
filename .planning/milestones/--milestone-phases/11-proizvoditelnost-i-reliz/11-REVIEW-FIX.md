---
phase: 11
fixed_at: 2026-07-19T00:12:35Z
review_path: .planning/phases/11-proizvoditelnost-i-reliz/11-REVIEW.md
iteration: 1
findings_in_scope: 4
fixed: 4
skipped: 0
status: all_fixed
---

# Phase 11: Code Review Fix Report

**Fixed at:** 2026-07-19T00:12:35Z
**Source review:** .planning/phases/11-proizvoditelnost-i-reliz/11-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 4
- Fixed: 4
- Skipped: 0

## Fixed Issues

### W1: metadata/ всё ещё брендирован «Simple Keyboard»

**Files modified:** `metadata/en-US/short_description.txt`, `metadata/en-US/full_description.txt`, `metadata/en-US/title.txt`, `metadata/pt-BR/` (deleted), `metadata/en-US/changelogs/96.txt` (deleted), `metadata/en-US/changelogs/97.txt` (deleted)
**Commit:** fa43379
**Applied fix:**
- `short_description.txt`: rewritten to "Tatar Cyrillic keyboard with a dedicated fifth row for ә ө ү җ ң һ. Offline."
- `full_description.txt`: full rewrite — "Tatar Keyboard" branding, fifth-row feature, three languages (tt/ru/en), long-press duplicates, TalkBack, directBoot, no INTERNET permission, Apache-2.0 fork attribution. All upstream "Simple Keyboard" references removed.
- `title.txt`: created with "Tatar Keyboard"
- `metadata/pt-BR/` directory deleted entirely — stale upstream translation we cannot maintain; IzzyOnDroid falls back to en-US automatically
- Stale upstream changelogs `96.txt` and `97.txt` removed (unrelated upstream content)

### M1: комментарий у setup_message занижает счёт переводов

**Files modified:** `app/src/main/res/values/strings.xml`
**Commit:** fa43379
**Applied fix:** Updated comment count from 35 → 44 (verified with `grep -l "setup_message" app/src/main/res/values-*/strings.xml | wc -l` = 44).

### M2: даты в PRIVACY.md и CHANGELOG.md — 2026-07-19

**Files modified:** none required
**Commit:** n/a
**Applied fix:** Both `PRIVACY.md` and `CHANGELOG.md` already carry `2026-07-19` — the review note was "if publication is not tomorrow the dates will be wrong." Publication is today (2026-07-19), so the dates are correct as-is. No change needed.

### M3: level-2 no-INTERNET проверка в CI — только debug APK

**Files modified:** `.github/workflows/ci.yml`
**Commit:** fa43379
**Applied fix:** Renamed the single "Check no INTERNET permission (built APK)" step to "Check no INTERNET permission (debug APK)" and added a second step "Check no INTERNET permission (release APK)" running `bash scripts/check-no-internet.sh app/build/outputs/apk/release/app-release-unsigned.apk`. Both run after both APKs are built. Comment updated to explain the rationale (buildType-specific merged manifest divergence).

---

## Build verification

- `./gradlew assembleDebug`: BUILD SUCCESSFUL
- `bash scripts/check-no-internet.sh app/build/outputs/apk/debug/app-debug.apk`: Level 1 OK + Level 2 OK

---

_Fixed: 2026-07-19T00:12:35Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
