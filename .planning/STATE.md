---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
current_phase: 3
current_phase_name: Языки и переключение
status: planning
stopped_at: Completed 02-01-PLAN.md (Task 5 device UAT deferred — устройство не подключено; SUMMARY committed 851697c)
last_updated: "2026-07-18T08:50:49.563Z"
last_activity: 2026-07-18
progress:
  total_phases: 2
  completed_phases: 2
  total_plans: 3
  completed_plans: 3
last_activity_desc: Phase 02 execution started
---

# State: Tatar Keyboard

## Current Position

**Milestone:** v1.0 — MVP + релиз (GitHub Releases + IzzyOnDroid)
**Phase:** 3 — Языки и переключение
**Plan:** Not started
**Status:** Ready to plan
**Last activity:** 2026-07-18

Progress: [██████████] 100%

## Accumulated Context

### Decisions

- База — форк Simple Keyboard (rkkr), Apache-2.0; решение окончательное, «этап 0» (прототип HeliBoard) пропущен.
- Новый код — Kotlin через interop; Java-базу массово не конвертировать. UI — один кастомный View + Canvas; Compose только в Activity настроек.
- Без composing-текста в MVP: коммит сразу, удаление `deleteSurroundingTextInCodePoints`.
- Без разрешения INTERNET — CI-проверка с фазы 1.
- Раскладки — данными (XML), формат должен допускать латиницу Zamanälif позже.
- Рабочее название «Tatar Keyboard»; финальное имя и applicationId — до публикации (фаза 11), applicationId фиксируется в фазе 1.
- План Б (форк HeliBoard, цена GPL-3.0) — решение принимать до больших вложений в iOS-скин (т.е. до фазы 6).
- [01-01] applicationId = `org.tatarkeyboard.ime` (провизорный, нейтральный вариант из ресерча) + `applicationIdSuffix ".debug"` в debug — сосуществование оригинала/debug/release.
- [01-01] compileSdk/targetSdk остаются 37 (как в базе b40c70d9), вопреки «36» в CLAUDE.md — даунгрейд = лишний риск; CLAUDE.md обновить отдельным коммитом вне фазы.
- [01-01] `gradle/wrapper/gradle-wrapper.jar` не трекается (upstream игнорирует `gradle/`); локально скачан официальный jar Gradle v9.6.0 — на новой машине его нужно восстановить (`gradle wrapper` или тот же URL).
- [01-01] Kotlin — через built-in Kotlin AGP 9 (удалена строка `android.builtInKotlin=false`), плагин `org.jetbrains.kotlin.android` не подключён.
- [01-02] Подпись release: условный signingConfig через `keystore.properties` (Pattern 3); `release.jks` (RSA 4096, validity 10950, alias `tatarkeyboard`) и `keystore.properties` — только локально, оба gitignored (проверено `git check-ignore` + grep истории). Без файла assembleRelease даёт unsigned APK — CI живёт без секретов.
- [01-02] CI (`.github/workflows/ci.yml`): официальные actions на мажорных тегах, `permissions: contents: read`; вызывает `scripts/check-no-internet.sh` дважды (fast-fail до сборки + по собранному APK); gradle-wrapper.jar восстанавливается шагом workflow из тега v9.6.0 gradle/gradle с pin по sha256 (jar не в git).
- [01-02] GitHub-репозиторий НЕ создан (на машине нет `gh`, remote только `upstream`) — создание репо/push/прогоны CI отложены; точные шаги в 01-02-SUMMARY.md § Deferred.

- [02-01] Татарская раскладка: rowkeys литеральными кодпоинтами (без !text/ — обход DEFAULT-ловушки KeyboardTextsTable), высоты 5×20%p / 6×16.667%p; Java-диф ограничен реестром SubtypeLocaleUtils (case с break, tt первым в getDefaultSubtypes). Ревью F1: ё и ъ недостижимы до phase-3 long-press — включить в LAYOUT-02.

### Cross-cutting disciplines (каждая фаза)

- Smoke-тест матрицы InputConnection (Telegram, Chrome/WebView keyCode 229, password-поля, MIUI/One UI) — в критериях каждой фазы ввода/UI; полный проход — фаза 8.
- Ноль аллокаций в горячем пути пишется в код при создании (Paint/Rect — поля, точечный invalidate); замеренная верификация PERF-01..03 — фаза 11.
- Каждая фаза завершается собирающимся и устанавливаемым APK — фаз, оставляющих проект несобираемым, не бывает.

### Open questions (не блокеры старта)

- Порядок клавиш пятого ряда (алфавитный vs частотный) — юзер-тест после MVP.
- minSdk 24 vs 26 — решить перед фазой 11.
- Финальное название приложения и applicationId — до публикации.

### Blockers/Concerns

- ⚠️ [Phase 1] Отложенная ручная проверка (принята пользователем 2026-07-18): on-device smoke debug/release, создание GitHub-репо + зелёный CI + красный ci-negative-test (доказательство PERF-04 на Actions), бэкап release.jks. Точные шаги — 01-01/01-02-SUMMARY.md § Deferred; прогнать при первой возможности (устройство + GitHub).
- ⚠️ [Phase 2, plan 02-01] Task 5 on-device UAT deferred — устройство не подключено (adb devices пуст), по образцу фазы 1. BUILD-критерии закрыты автоматикой (assembleDebug зелёный, aapt2 видит *_tatar ресурсы, check-no-internet OK, Java-diff = только SubtypeLocaleUtils.java). Чек-лист при появлении устройства: (1) чистая установка adb uninstall org.tatarkeyboard.ime.debug → adb install app-debug.apk → выбрать «Tatar Keyboard (dev)»; (2) клавиатура открывается ТАТАРСКОЙ: пятый ряд ә ө ү җ ң һ СВЕРХУ над ЙЦУКЕН; (3) напечатать «әни өй үрдәк җир таң һава» + «щи, ыл, эш, ике» — все 37 букв тапом, щ/ы/э/и не пустые; (4) shift → Ә Ө Ү Җ Ң Һ; (5) 5 рядов + action row без обрезки, adb logcat | grep -i "too tall" пуст; (6) ?123 → #+= → АБВ туда-обратно; (7) Number row ON: цифры НАД пятым рядом, 6 рядов помещаются, выключить обратно; (8) smoke-матрица SC4: «әни өй үрдәк җир таң һава» в Telegram, Chrome (адресная строка + поле формы/WebView keyCode 229), password-поле — без потерь/дублей. 02-01-SUMMARY.md создаётся после резолва чекпойнта.

### Research pointers

- `.planning/research/SUMMARY.md` — конденсат; детали в `research/00`–`08` в корне.
- Фаза 1: фазовый ресерч по реальным исходникам Simple Keyboard (пофайлово не покрыт).
- Фаза 6: ресерч отрисовки KeyboardView форка — что переписывать, что переиспользовать.
- Фаза 8: актуальные known issues MIUI/HyperOS для IME.
- Фазы 4–5, 10: стандартные паттерны (research/01, research/08) — research-phase можно пропустить.

## Session Continuity

**Stopped at:** Phase 2 complete (verification passed; on-device UAT отложен, принят), ready to plan Phase 3
**Resume file:** None

**Next step:** Phase 3 — Языки и переключение (LAYOUT-02 long-press дубли — вкл. ё/ъ из ревью F1, LAYOUT-03 ru/en, SWITCH-01/02 subtypes+глобус).

Last session: 2026-07-18T08:29:50.584Z

---
*Last updated: 2026-07-18 — Phase 1 complete*

## Performance Metrics

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 1 P01 | 35 min | 4 tasks | 7 files |
| Phase 1 P02 | 20 min | 2 of 4 tasks (2 deferred) | 3 files (+2 local secrets) |
| Phase 02 P01 | 9 min | 4 tasks | 8 files |
