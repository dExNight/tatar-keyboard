---
phase: 11-proizvoditelnost-i-reliz
verified: 2026-07-19T00:18:28Z
status: passed
human_verification_deferred: true
deferred_accepted_by: user standing decision (autonomous run 2026-07-18/19)
score: 8/8 must-haves verified
behavior_unverified: 0
overrides_applied: 0
re_verification: false
deferred:
  - truth: "PSS показанной клавиатуры ≤ 30 МБ и холодный старт < 400 мс зафиксированы (PERF-02)"
    addressed_in: "Task 5 UAT-бандл (standing-паттерн)"
    evidence: "11-PERF-CHECKLIST.md содержит команды dumpsys meminfo/am force-stop с порогами; требует физического устройства (adb)"
  - truth: "0 аллокаций onDraw/onTouchEvent, 0 GC, janky ~0% зафиксированы (PERF-03)"
    addressed_in: "Task 5 UAT-бандл (standing-паттерн)"
    evidence: "11-PERF-CHECKLIST.md содержит команды Profiler + gfxinfo с порогами; требует физического устройства"
  - truth: "Privacy policy «данные не собираются» опубликована (REL-02)"
    addressed_in: "Task 5c — ручная публикация (locked decision)"
    evidence: "PRIVACY.md готова, слинкована из README; «опубликована» = после ручного push репо (PUBLISH-CHECKLIST шаги 2–4)"
  - truth: "Релиз опубликован на GitHub Releases и подана заявка IzzyOnDroid (REL-03)"
    addressed_in: "Task 5c — ручная публикация (locked decision)"
    evidence: "docs/PUBLISH-CHECKLIST.md содержит точные команды/URL; исполнение строго ручное по locked decision плана"
---

# Phase 11: Производительность и релиз — Verification Report

**Phase Goal:** Замеренные бюджеты производительности + подписанный релиз + публикация — граница майлстоуна v1.0.
**Verified:** 2026-07-19T00:18:28Z
**Status:** passed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Release APK ≤ 3 МБ — stat-гейт подтверждён числом (PERF-01 механически) | ✓ VERIFIED | `stat -f%z app-release.apk` = 681 070 bytes ≤ 3 145 728; запас 4.6× |
| 2 | locale_name_tt_RU не стрипнута shrinkResources: keep.xml с @string/locale_name_* предотвращает Resources.NotFoundException | ✓ VERIFIED | `aapt2 dump resources`: locale_name = 8 вхождений ≥ 8; keyboard_layout_set_tatar жив; label_pause/wait = 2 |
| 3 | Имя приложения «Tatar Keyboard» без суффикса разработки во всех 4 label манифеста | ✓ VERIFIED | `strings-appname.xml:22` = `Tatar Keyboard` без `(dev)`; единая точка english_ime_name, манифест не тронут |
| 4 | Подписанный release-APK верифицируется apksigner verify (REL-01) | ✓ VERIFIED | `apksigner verify app-release.apk` exit 0; keystore с фазы 1, gitignored |
| 5 | CI падает при release APK > 3 МБ: stat-гейт в ci.yml (PERF-01 как CI-контракт) | ✓ VERIFIED | `ci.yml:45–46` — `test $(stat -c%s app-release-unsigned.apk) -le 3145728`; YAML валиден (python3 yaml); fail-capable |
| 6 | Privacy policy содержит утверждение об отсутствии сбора данных и ссылку на лицензию; README её линкует (REL-02) | ✓ VERIFIED | `PRIVACY.md` содержит no-INTERNET + «не собирает» + Apache; README линкует PRIVACY.md и PUBLISH-CHECKLIST |
| 7 | PUBLISH-CHECKLIST.md содержит точные команды и URL для GitHub Release + IzzyOnDroid (REL-03 подготовлено) | ✓ VERIFIED | `docs/PUBLISH-CHECKLIST.md`: backup jks, github.com/new, v1.0.0, gitlab.com/IzzyOnDroid, strings-appname — все грепы PASS |
| 8 | PERF-02/03 деферрированы: 11-PERF-CHECKLIST.md содержит конкретные adb-команды с порогами | ✓ VERIFIED | `11-PERF-CHECKLIST.md`: dumpsys meminfo (30 720 KB), am force-stop (400 мс), Profiler, gfxinfo janky ≤ 1% — все 4 замера с командами |

**Score:** 8/8 truths verified (0 behavior-unverified)

### Deferred Items

Items not yet met but explicitly addressed by locked decisions and standing UAT-паттерн.

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | PERF-02: PSS ≤ 30 МБ + холодный старт < 400 мс | Task 5a / UAT-бандл | 11-PERF-CHECKLIST.md — команды и пороги готовы; требует adb + бюджетного устройства |
| 2 | PERF-03: 0 аллокаций/GC, janky ~0% | Task 5a / UAT-бандл | 11-PERF-CHECKLIST.md — Profiler + gfxinfo; дисциплина заложена в фазе 6 (3 PERF-фикса горячего пути) |
| 3 | REL-02: privacy policy «опубликована» | Task 5c — ручная публикация | PRIVACY.md готова и слинкована; «опубликована» = после push репо по PUBLISH-CHECKLIST шагам 2–4 |
| 4 | REL-03: GitHub Release + заявка IzzyOnDroid | Task 5c — ручная публикация (locked decision) | docs/PUBLISH-CHECKLIST.md — 10 шагов с точными URL/командами; locked decision «публикация строго ручная» |

---

## Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `app/src/main/res/raw/keep.xml` | Resource shrinker masks: 4 getIdentifier call-site | ✓ VERIFIED | Файл существует; маски keyboard_layout_set_*/kbd_*/rows_*/rowkeys_*/row_*/locale_name_*/label_* |
| `app/build.gradle` | shrinkResources true + versionName 1.0.0 + versionCode 1 + signingConfigs guard | ✓ VERIFIED | shrinkResources:34, versionName:16 "1.0.0", versionCode:15=1, signingConfigs с exists() guard:19+37 |
| `.github/workflows/ci.yml` | assembleRelease (unsigned) + stat size-gate ≤ 3145728 | ✓ VERIFIED | Шаги на строках 42 и 45–46; YAML valid; оба APK проверяются check-no-internet (после M3 fix) |
| `README.md` | Без placeholder-скриншотов, dead-бейджей, iOS-маркетинга; ссылки PRIVACY.md + PUBLISH-CHECKLIST | ✓ VERIFIED | Все грепы PASS; атрибуция Simple Keyboard + AOSP LatinIME |
| `PRIVACY.md` | Данные не собираются, no-INTERNET, Apache-2.0 | ✓ VERIFIED | Контент-грепы PASS |
| `CHANGELOG.md` | v1.0.0, фичи user-facing, пятый ряд буквами | ✓ VERIFIED | v1.0.0, ä ö ü letters, TalkBack |
| `docs/PUBLISH-CHECKLIST.md` | 10 ручных шагов: backup jks, repo, URL-fix, push, CI, tag, Release, IzzyOnDroid | ✓ VERIFIED | Все ключевые грепы PASS |
| `.planning/phases/11-proizvoditelnost-i-reliz/11-PERF-CHECKLIST.md` | 4 adb-замера с порогами | ✓ VERIFIED | dumpsys meminfo/30 720 KB, force-stop/400 мс, Profiler, gfxinfo/janky |

---

## Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `shrinkResources true` → `keep.xml` → `locale_name_tt_RU` | Предотвращение Resources.NotFoundException | `tools:keep="@string/locale_name_*"` | ✓ WIRED | aapt2: 8 locale_name вхождений в release APK; keyboard_layout_set_tatar жив |
| `versionName "1.0.0"` в build.gradle | CHANGELOG v1.0.0 | тег v1.0.0 в PUBLISH-CHECKLIST | ✓ WIRED | Тройное согласование подтверждено грепами |
| `keystore.properties` → `signingConfigs.release` → `assembleRelease` | `apksigner verify OK` | `exists()` guard + signingConfig :37 | ✓ WIRED | apksigner exit 0; keystore.properties gitignored (CI unsigned by design) |

---

## Data-Flow Trace (Level 4)

Не применимо: фаза zero-code. Все изменения — конфигурация, XML, документы. Нет компонентов, рендерящих динамические данные.

---

## Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Release APK ≤ 3 МБ (PERF-01) | `stat -f%z app-release.apk` | 681 070 bytes | ✓ PASS |
| apksigner verify (REL-01) | `apksigner verify app-release.apk` | exit 0 | ✓ PASS |
| locale_name_tt_RU пережил shrink | `aapt2 dump resources \| grep -c locale_name` | 8 ≥ 8 | ✓ PASS |
| keyboard_layout_set_tatar жив | `aapt2 dump resources \| grep keyboard_layout_set_tatar` | found | ✓ PASS |
| label_pause/wait жив | `aapt2 dump resources \| grep -cE label_pause_key\|label_wait_key` | 2 ≥ 2 | ✓ PASS |
| No INTERNET (source manifest) | `bash scripts/check-no-internet.sh` | Level 1 OK + Level 2 OK | ✓ PASS |
| No INTERNET (release APK) | `bash scripts/check-no-internet.sh app-release.apk` | Level 1 OK + Level 2 OK | ✓ PASS |

---

## Probe Execution

Нет probe-скриптов в `scripts/*/tests/probe-*.sh` для этой фазы. Верификация через fail-capable грепы и механические гейты (Task 4 verify chain).

---

## Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| PERF-01 | 11-01 | Release APK ≤ 3 МБ | ✓ Complete (11-01) | 681 070 bytes; CI stat-gate; aapt2 shrink-check — механически закрыто |
| PERF-02 | 11-01 | PSS ≤ 30 МБ + холодный старт < 400 мс | Verifying (deferred) | 11-PERF-CHECKLIST.md готов; замеры — device UAT |
| PERF-03 | 11-01 | 0 аллокаций/GC, janky ~0% | Verifying (deferred) | 11-PERF-CHECKLIST.md готов; дисциплина заложена фазой 6 |
| REL-01 | 11-01 | Keystore, подписанный release | ✓ Complete (11-01) | apksigner verify OK; signingConfigs-цепочка жива |
| REL-02 | 11-01 | Privacy policy опубликована | Verifying (deferred) | PRIVACY.md готова + README-линк; «опубликована» = после push |
| REL-03 | 11-01 | GitHub Releases + IzzyOnDroid | Verifying (deferred) | PUBLISH-CHECKLIST готов; исполнение строго ручное |

---

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `strings.xml` | 129 | `setup_message` = "Tatar Keyboard" (нейтрализован, не удалён) | ℹ️ Info | Запланированный A4-фолбэк: lintVitalRelease падал ExtraTranslation при удалении; строка мёртвая (0 вхождений в java/kt); чистка 44 локализаций = backlog post-v1.0 |
| `metadata/en-US/full_description.txt` | attr | Содержит слова «Simple Keyboard» | ℹ️ Info | Apache-2.0 attribution — юридически обязательно; W1-предупреждение из REVIEW.md исправлено (fix fa43379): заголовок «Tatar Keyboard», описание переписано; оставшееся упоминание = атрибуция форку |

Нет TBD/FIXME/XXX маркеров в файлах, изменённых фазой (boundary-check подтверждён).

---

## Human Verification Required

Пусто — все механические must-have truths VERIFIED. Деферрированные пункты (device UAT + публикация) задокументированы в deferred-секции выше и в STATE.md Blockers; они не блокируют статус `passed` по standing-паттерну фаз 1–10.

---

## Gaps Summary

Нет блокеров. Все механические must-have PASS. Деферрированные пункты:

- **PERF-02/03** — требуют физического бюджетного устройства (adb); команды и пороги готовы в `11-PERF-CHECKLIST.md`.
- **REL-02/03** — locked decision «публикация строго ручная»; инструкция с точными URL готова в `docs/PUBLISH-CHECKLIST.md`.

Статус `passed` следует standing-паттерну фаз 1–10: механически верифицируемое закрыто числами, неверифицируемое без устройства/репозитория честно деферрировано в STATE.md Blockers.

---

_Verified: 2026-07-19T00:18:28Z_
_Verifier: Claude (gsd-verifier)_
