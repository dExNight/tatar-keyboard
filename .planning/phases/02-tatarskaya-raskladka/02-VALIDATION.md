---
phase: 2
slug: tatarskaya-raskladka
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-18
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Gradle build + aapt2 checks (no unit-test framework this phase — инструментальный харнес для одной раскладки избыточен, см. 02-RESEARCH.md § Wave 0 Gaps) |
| **Config file** | none — Wave 0 покрыт инфраструктурой фазы 1 (`scripts/check-no-internet.sh` существует) |
| **Quick run command** | `./gradlew assembleDebug` |
| **Full suite command** | `./gradlew assembleDebug && bash scripts/check-no-internet.sh` |
| **Estimated runtime** | ~60–120 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew assembleDebug` (компиляция ресурсов aapt2 = базовая валидация XML: merge, атрибуты, ссылки @xml/@fraction)
- **After every plan wave:** Run `./gradlew assembleDebug && bash scripts/check-no-internet.sh`
- **Before `/gsd-verify-work`:** Full suite green + aapt2 dump resources по APK содержит `*_tatar` записи
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 02-01.T1 | 02-01 | 1 | LAYOUT-01 (буквы), LAYOUT-05 (данные) | 02-01/T1 | 37 клавиш литералами, ноль `!text/`-рефов (DEFAULT-ловушка обойдена) | build + grep | `./gradlew assembleDebug && ! grep -l '!text/' app/src/main/res/xml/rowkeys_tatar*.xml` | ✅ (сборка фазы 1) | ⬜ pending |
| 02-01.T2 | 02-01 | 1 | LAYOUT-01 (пятый ряд сверху), LAYOUT-04 (Element-декларации), LAYOUT-05 | 02-01/T2 | Высоты 5/6 рядов решены в XML; ресурсы в APK; ноль Java-правок | build + aapt2 | `./gradlew assembleDebug && aapt2 dump resources app-debug.apk \| grep tatar` | ✅ | ⬜ pending |
| 02-01.T3 | 02-01 | 1 | LAYOUT-05 (оговорка: реестр) | 02-01/T2 | tt/tatar в реестре; `case` с `break` (sakha-баг не унаследован); diff = 1 файл | build + grep | `./gradlew assembleDebug && grep -A2 'case LOCALE_TATAR' SubtypeLocaleUtils.java \| grep 'break;'` | ✅ | ⬜ pending |
| 02-01.T4 | 02-01 | 1 | Phase SC (активация MVP) | — | tt-subtype первым в getDefaultSubtypes; матчинг/fallback не тронуты | build + grep | `./gradlew assembleDebug && grep 'subtypes.add(0' SubtypeLocaleUtils.java` | ✅ | ⬜ pending |
| 02-01.T5 | 02-01 | 1 | LAYOUT-01, LAYOUT-04, Phase SC4 (on-device) | — | Печать 37 букв, shift, ?123/#+=, 5/6 рядов без обрезки, smoke-матрица (Telegram/Chrome-WebView/password) | manual | — (checkpoint:human-verify) | — | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] Рабочая сборка (`./gradlew assembleDebug`) — есть с фазы 1; вся XML-валидация фазы едет на ресурс-компиляции
- [x] `scripts/check-no-internet.sh` — есть с фазы 1 (01-01 Task 2); входит в verify каждой задачи

*Новых Wave 0 зависимостей фаза не создаёт: инструментальный тест-харнес для парсинга раскладок не заводим (несоразмерно задаче «добавить раскладку данными» — 02-RESEARCH.md § Wave 0 Gaps); риск покрывается сборкой + ручным UAT.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Пятый ряд `ә ө ү җ ң һ` виден сверху, все 37 букв вводятся тапом | LAYOUT-01 | Рендер и ввод — только на устройстве/эмуляторе | Чистая установка (uninstall → install, prefs фазы 1 сбрасываются) → клавиатура открывается татарской → напечатать «әни өй үрдәк җир таң һава» + «щи, ыл, эш, ике» (02-01 Task 5, пп. 1–3) |
| 5 рядов (и 6 при showNumberRow) без обрезки нижнего ряда | LAYOUT-01 / Pitfall 1 (A1) | Clamp-логика KeyboardRow проявляется только при рендере | Визуально + `adb logcat \| grep -i "too tall"` пуст; включить Number row → цифры НАД пятым рядом, всё помещается (Task 5, пп. 5, 7) |
| Shift даёт Ә Ө Ү Җ Ң Һ | LAYOUT-01 (A3) | `toUpperCase(Locale)` — runtime-поведение | Тап shift → пятый ряд в верхнем регистре, ввод коммитит заглавные (Task 5, п. 4) |
| ?123 → #+= → возврат к буквам | LAYOUT-04 | Переключение состояний KeyboardState — runtime | Тапы по `?123`, `#+=`, `АБВ` из татарской раскладки (Task 5, п. 6) |
| Smoke-матрица SC4: татарский ввод в Telegram, Chrome/WebView (keyCode 229), password-поле | Phase SC4 (cross-cutting дисциплина STATE.md) | Зоопарк InputConnection проявляется только в реальных редакторах — WebView шлёт keyCode 229, password-поля меняют поведение | Повторить ввод «әни өй үрдәк җир таң һава» (все 6 букв пятого ряда): (a) Telegram — поле сообщения; (b) Chrome — адресная строка + поле формы/WebView; (c) password-поле — точки маскировки на каждый тап. Во всех трёх без потерь/дублей (02-01 Task 5, п. 8) |

*Если устройство недоступно (как в фазе 1) — чекпойнт Task 5 (включая smoke-матрицу SC4) откладывается в STATE.md Blockers с этим чек-листом; BUILD-критерии фазы закрыты автоматикой, но LAYOUT-01/04 и SC4 не считаются полностью верифицированными до прогона.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
