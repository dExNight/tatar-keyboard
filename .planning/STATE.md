# State: Tatar Keyboard

## Current Position

**Milestone:** v1.0 — MVP + релиз (GitHub Releases + IzzyOnDroid)
**Phase:** 1 of 11 — Форк и hello-world (not started)
**Status:** Roadmap создан — готов к планированию фазы 1
**Last activity:** 2026-07-18 — создан ROADMAP.md, traceability обновлена (34/34)

Progress: [░░░░░░░░░░░] 0/11 phases

## Accumulated Context

### Decisions

- База — форк Simple Keyboard (rkkr), Apache-2.0; решение окончательное, «этап 0» (прототип HeliBoard) пропущен.
- Новый код — Kotlin через interop; Java-базу массово не конвертировать. UI — один кастомный View + Canvas; Compose только в Activity настроек.
- Без composing-текста в MVP: коммит сразу, удаление `deleteSurroundingTextInCodePoints`.
- Без разрешения INTERNET — CI-проверка с фазы 1.
- Раскладки — данными (XML), формат должен допускать латиницу Zamanälif позже.
- Рабочее название «Tatar Keyboard»; финальное имя и applicationId — до публикации (фаза 11), applicationId фиксируется в фазе 1.
- План Б (форк HeliBoard, цена GPL-3.0) — решение принимать до больших вложений в iOS-скин (т.е. до фазы 6).

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

**Next step:** `/gsd-plan-phase 1` — планирование фазы «Форк и hello-world».

Last session: 2026-07-18 — инициализация проекта, требования (34 v1), роадмап (11 фаз).

---
*Last updated: 2026-07-18 — roadmap created*
