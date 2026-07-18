---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
current_phase: 1
current_phase_name: Форк и hello-world
status: executing
stopped_at: Completed 01-02-PLAN.md (GitHub repo/push/CI runs + device checkpoint deferred)
last_updated: "2026-07-18T06:55:00.000Z"
last_activity: 2026-07-18
last_activity_desc: Plan 01-02 executed — keystore, signed release, ci.yml authored; GitHub-side steps deferred
progress:
  total_phases: 1
  completed_phases: 0
  total_plans: 2
  completed_plans: 2
---

# State: Tatar Keyboard

## Current Position

**Milestone:** v1.0 — MVP + релиз (GitHub Releases + IzzyOnDroid)
**Phase:** 1 (Форк и hello-world) — EXECUTING
**Plan:** 01-01 — CLOSED (Task 5 on-device smoke DEFERRED, human_needed). 01-02 — CLOSED locally (Task 1 keystore/signing done: ba4bf38, 1721fa7; Task 2 ci.yml authored: 7c63621). DEFERRED (среда без gh/origin): создание GitHub-репо + push, зелёный прогон CI на main, негативный прогон ветки ci-negative-test (Task 3, GitHub-часть; локальный негативный тест скрипта продемонстрирован), Task 4 device-verify + бэкап ключа (human_needed). Next: phase verification после закрытия deferred-пунктов.
**Status:** Executing Phase 1 (both plans executed; GitHub + device verification pending)
**Last activity:** 2026-07-18 — Plan 01-02 executed; GitHub-side steps deferred

Progress: [████████░░] 80%

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

### Cross-cutting disciplines (каждая фаза)

- Smoke-тест матрицы InputConnection (Telegram, Chrome/WebView keyCode 229, password-поля, MIUI/One UI) — в критериях каждой фазы ввода/UI; полный проход — фаза 8.
- Ноль аллокаций в горячем пути пишется в код при создании (Paint/Rect — поля, точечный invalidate); замеренная верификация PERF-01..03 — фаза 11.
- Каждая фаза завершается собирающимся и устанавливаемым APK — фаз, оставляющих проект несобираемым, не бывает.

### Open questions (не блокеры старта)

- Порядок клавиш пятого ряда (алфавитный vs частотный) — юзер-тест после MVP.
- minSdk 24 vs 26 — решить перед фазой 11.
- Финальное название приложения и applicationId — до публикации.

### Research pointers

- `.planning/research/SUMMARY.md` — конденсат; детали в `research/00`–`08` в корне.
- Фаза 1: фазовый ресерч по реальным исходникам Simple Keyboard (пофайлово не покрыт).
- Фаза 6: ресерч отрисовки KeyboardView форка — что переписывать, что переиспользовать.
- Фаза 8: актуальные known issues MIUI/HyperOS для IME.
- Фазы 4–5, 10: стандартные паттерны (research/01, research/08) — research-phase можно пропустить.

## Session Continuity

**Stopped at:** Completed 01-02-PLAN.md (GitHub repo/push/CI runs deferred — нет gh/origin; device checkpoints 01-01 Task 5 и 01-02 Task 4 deferred — устройство не подключено)
**Resume file:** None

**Next step:** закрыть deferred-пункты (создать GitHub-репо + push → зелёный CI на main → негативный прогон ci-negative-test; on-device проверки debug и release + бэкап ключа), затем phase 1 verification. Точные шаги — 01-02-SUMMARY.md § Deferred.

Last session: 2026-07-18T06:55:00.000Z

---
*Last updated: 2026-07-18 — roadmap created*

## Performance Metrics

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 1 P01 | 35 min | 4 tasks | 7 files |
| Phase 1 P02 | 20 min | 2 of 4 tasks (2 deferred) | 3 files (+2 local secrets) |
