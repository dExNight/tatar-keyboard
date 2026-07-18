---
phase: 05-zhesty-i-multi-touch
plan: 01
subsystem: input
tags: [ime, inputlogic, double-space, space-swipe, multi-touch, java]

requires:
  - phase: 04-mekhanika-vvoda
    provides: "верификация shift/автокапс/backspace/enter в базе форка; standing UAT-deferral паттерн"
provides:
  - "Double-space→period восстановлен в InputLogic (AOSP-паттерн, always-on, 1100 мс, password+letter/digit гейты, revert по backspace, гигиена состояния)"
  - "Cache-only аксессор RichInputConnection.getCodePointBeforeCursor(int offsetCodePoints)"
  - "pref_space_swipe default true в 3 согласованных местах (Settings.java, prefs_screen_preferences.xml, app_restrictions.xml)"
  - "INPUT-07 multi-touch commit доказан структурно (грепы), ноль правок"
affects: [phase-06-skin, phase-08-matrix, phase-11-release]

tech-stack:
  added: []
  patterns:
    - "Гигиена стейт-машины InputLogic: сброс жестового состояния в startInput() и на любом событии, кроме успешного жеста"
    - "Cache-only чтения RichInputConnection в горячем пути (ноль IPC/аллокаций)"

key-files:
  created: []
  modified:
    - app/src/main/java/rkr/simplekeyboard/inputmethod/latin/inputlogic/InputLogic.java
    - app/src/main/java/rkr/simplekeyboard/inputmethod/latin/RichInputConnection.java
    - app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/Settings.java
    - app/src/main/res/xml/prefs_screen_preferences.xml
    - app/src/main/res/xml/app_restrictions.xml

key-decisions:
  - "Double-space always-on без pref (конвенция форка — минимум переключателей); отмена только backspace'ом, третьим пробелом — нет (AOSP-паттерн)"
  - "Таймаут — константа 1100 мс (AOSP config_double_space_period_timeout), не системный ~300 мс double-tap"
  - "Letter/digit-гейт через новый cache-only аксессор getCodePointBeforeCursor(offset) — ноль IPC, ноль аллокаций"
  - "Перепутанные android:title в app_restrictions.xml (pre-existing upstream) НЕ исправлены — backlog в STATE.md"

patterns-established:
  - "Жестовое состояние сбрасывается в startInput() — не течёт между полями"

requirements-completed: [INPUT-05, INPUT-06, INPUT-07]

coverage:
  - id: D1
    description: "Double-space→period в InputLogic: tryDoubleSpacePeriod (1100 мс, password-гейт, letter/digit-check), revert по backspace, сбросы состояния"
    requirement: INPUT-05
    verification:
      - kind: other
        ref: "./gradlew assembleDebug + fail-capable-грепы Task 1 (DOUBLE_SPACE_PERIOD_TIMEOUT=1100, tryDoubleSpacePeriod, mJustDoubleSpaced в startInput, mIsPasswordField, isLetterOrDigit, отсутствие pref)"
        status: pass
    human_judgment: true
    rationale: "Поведение (тайминг, revert, password-гейт) проверяемо только on-device — UAT отложен (Task 4, standing-паттерн)"
  - id: D2
    description: "pref_space_swipe default → true согласованно в Settings.java, prefs_screen_preferences.xml, app_restrictions.xml; pref_delete_swipe не тронут"
    requirement: INPUT-06
    verification:
      - kind: other
        ref: "Task 2 verify: грепы PREF_SPACE_SWIPE,true + defaultValue=true ×2 + негативные грепы pref_delete_swipe=false ×3 + assembleDebug"
        status: pass
    human_judgment: true
    rationale: "Свайп из коробки поверх ә/җ проверяем только on-device — UAT отложен"
  - id: D3
    description: "INPUT-07 multi-touch commit работает в базе — доказательства запинованы, ноль правок"
    requirement: INPUT-07
    verification:
      - kind: other
        ref: "Task 3 verify: грепы releaseAllPointersOlderThan/onPhantomUpEvent (PointerTracker, PointerTrackerQueue), NonDistinctMultitouchHelper (MainKeyboardView)"
        status: pass
    human_judgment: true
    rationale: "Двупальцевая печать без потери букв — только on-device"
  - id: D4
    description: "Boundary фазы: диф 8e4693e..HEAD по app/ = ровно 5 объявленных файлов, новых .kt нет; сборка + check-no-internet зелёные"
    verification:
      - kind: other
        ref: "Task 3 verify: git diff --name-only 8e4693e..HEAD фильтр-чек + check-no-internet.sh"
        status: pass
    human_judgment: false

duration: 8min
completed: 2026-07-18
status: complete
---

# Phase 5 Plan 01: Жесты и multi-touch Summary

**Double-space→period восстановлен в InputLogic по AOSP-паттерну (1100 мс, гейты, revert), pref_space_swipe default флипнут на true ×3, INPUT-07 доказан структурно — UAT отложен**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-07-18T13:43:09Z
- **Completed:** 2026-07-18T13:48:44Z
- **Tasks:** 3 of 4 (Task 4 checkpoint deferred)
- **Files modified:** 5 code/res + planning

## Accomplishments

- INPUT-05: восстановлена вырезанная rkkr механика double-space→period — `tryDoubleSpacePeriod` в `handleSeparatorEvent` (константа 1100 мс, гейт `mIsPasswordField`, «перед курсором пробел, перед ним буква/цифра» через новый cache-only аксессор `getCodePointBeforeCursor(offset)`), revert по backspace («. » → два пробела), гигиена состояния: сброс в `startInput()`, в `handleNonSeparatorEvent`, в не-пробельной ветке сепараторов и в backspace без revert. Always-on, без pref.
- INPUT-06: `pref_space_swipe` default false→true в трёх согласованных местах; `pref_delete_swipe` и механика PointerTracker/LatinIME не тронуты.
- INPUT-07: ноль работы — доказательства (releaseAllPointersOlderThan / onPhantomUpEvent / NonDistinctMultitouchHelper) запинованы fail-capable-грепами.
- Boundary доказан механически: `git diff --name-only 8e4693e..HEAD -- app/` = ровно {InputLogic.java, RichInputConnection.java, Settings.java, prefs_screen_preferences.xml, app_restrictions.xml}; новых .kt нет.

## Task Commits

1. **Task 1: Double-space→period в InputLogic** — `27377af` (feat)
2. **Task 2: Флип pref_space_swipe default ×3** — `195e6aa` (feat)
3. **Task 3: Структурная верификация + bookkeeping** — `3eaeeb6` (docs)
4. **Task 4: On-device UAT** — DEFERRED (см. ниже)

## Verification Results (проверка → PASS)

- `./gradlew assembleDebug` → PASS (после каждого таска)
- `scripts/check-no-internet.sh` → PASS (Level 1 манифест + Level 2 APK)
- INPUT-05 грепы (timeout 1100, tryDoubleSpacePeriod, mJustDoubleSpaced в startInput, mIsPasswordField, isLetterOrDigit; негативные: getDoubleTapTimeout, pref_double_space) → PASS
- INPUT-06 грепы (PREF_SPACE_SWIPE,true + defaultValue true ×2; pref_delete_swipe false ×3) → PASS
- INPUT-07 грепы (releaseAllPointersOlderThan, onPhantomUpEvent, NonDistinctMultitouchHelper) → PASS
- Boundary-чек 8e4693e..HEAD (⊆ 5 файлов, нет .kt) → PASS
- Bookkeeping-грепы (Verifying ×3, чек-боксы пустые, decision [05-01], backlog title) → PASS

## Decisions Made

- Always-on double-space без pref; revert только backspace; таймаут-константа 1100 мс — по ресерчу/плану.
- Аксессор `getCodePointBeforeCursor(int offsetCodePoints)` добавлен в RichInputConnection (вариант A2 плана): читает кеш `mTextBeforeCursor` посимвольно (codePointBefore), суррогат-aware, ноль IPC/аллокаций.

## Deviations from Plan

None - plan executed exactly as written. (Единственная микро-правка в ходе Task 1: слово «getDoubleTapTimeout» убрано из javadoc-комментария, т.к. негативный греп verify запрещает его наличие даже в комментарии.)

## Issues Encountered

None.

## Deferred Verification (Task 4 — checkpoint:human-verify)

Устройство не подключено (`adb devices` пуст) — чекпойнт отложен по standing-паттерну фаз 1–4, запись добавлена в STATE.md Blockers. Чек-лист при появлении устройства:

1. Установка свежего app-debug.apk; **uninstall желателен** — dev-prefs могли зафиксировать space_swipe=false до флипа default.
2. **INPUT-05:** в Telegram и заметках «әни» + двойной пробел (< 1.1 с) → «әни. », shift поднят; backspace сразу → откат к двум пробелам; двойной пробел после точки/в начале поля → просто два пробела; медленный второй пробел (> 1.1 с) → без точки.
3. **INPUT-05/password:** в password-поле двойной пробел НЕ даёт точку.
4. **INPUT-06:** свайп по пробелу двигает курсор сразу после установки, без настроек; поверх татарского текста с ә/җ шагает по буквам; выключить pref → жест пропадает, включить обратно.
5. **INPUT-07:** быстрая печать двумя пальцами «әни өй үрдәк җир» — без потери букв и порядка.
6. **Smoke SC4:** пп. 2/4/5 в Chrome WebView/поле формы (keyCode 229); password — без аномалий.
7. MIUI — при наличии Xiaomi (иначе явно пометить как не покрыто).

Финальная простановка чек-боксов INPUT-05..07 в REQUIREMENTS.md — после UAT.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Вся механика ввода (фазы 4–5) закрыта локально; фаза 6 (iOS-скин) может стартовать — скин рисует состояния готовой механики.
- Перед фазой 6: принять решение «План Б» (форк HeliBoard, цена GPL-3.0) — см. STATE.md Decisions.
- Накоплены 5 отложенных device-чек-листов (фазы 1–5) — прогнать пакетно при появлении устройства.

## Self-Check: PASSED

---
*Phase: 05-zhesty-i-multi-touch*
*Completed: 2026-07-18*
