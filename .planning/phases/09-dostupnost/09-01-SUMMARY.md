---
phase: 09-dostupnost
plan: 01
subsystem: accessibility
tags: [talkback, explorebytouchhelper, accessibility, kotlin, android-ime, a11y]

# Dependency graph
requires:
  - phase: 06-ios-skin
    provides: "Каркас ExploreByTouchHelper (KeyboardAccessibilityDelegate.kt: enumeration/hit-testing/bounds/invalidateRoot) + androidx.customview:1.1.0 (транзитив androidx.core:1.3.0)"
provides:
  - "KeyDescriptionMapper.kt — key→spoken-описание: 6 татарских букв по кодпоинту, шаблон «Заглавная %s», 19 служебных по elementId/imeAction, fallback label"
  - "strings-a11y.xml (en base) + values-ru/strings-a11y.xml — 26 spoken_*-строк AOSP-совместимыми именами"
  - "Полный TalkBack-делегат: описания маппером, isClickable/isTextEntryKey, ACTION_CLICK → синтез MotionEvent DOWN/UP → processMotionEvent + TYPE_VIEW_CLICKED"
affects: [10-onboarding, 11-release, uat-bundle]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "AOSP KeyCodeDescriptionMapper-паттерн: описания данными (строковые ресурсы), прямой when по коду без reflection"
    - "AOSP performClickOn-паттерн: a11y-клик = синтез MotionEvent в штатный touch-путь (zero fork-Java diff)"

key-files:
  created:
    - app/src/main/java/rkr/simplekeyboard/inputmethod/accessibility/KeyDescriptionMapper.kt
    - app/src/main/res/values/strings-a11y.xml
    - app/src/main/res/values-ru/strings-a11y.xml
  modified:
    - app/src/main/java/rkr/simplekeyboard/inputmethod/accessibility/KeyboardAccessibilityDelegate.kt
    - .planning/REQUIREMENTS.md
    - .planning/STATE.md

key-decisions:
  - "Клик через синтез MotionEvent DOWN/UP в видимый центр клавиши → public processMotionEvent — а не через listener (приватен; риск рассинхрона shift-машины); fork-Java-дифф = 0 строк"
  - "Описания строго по key.getCode() (+ mElementId/imeAction) — детект по label/iconName отвергнут (m3-паттерн)"
  - "Password-описания НЕ обскьюрены: ACCESSIBILITY_SPEAK_PASSWORD deprecated с API 26, озвучка паролей — зона TalkBack; собственных announceForAccessibility ноль"
  - "values-tt и announce смены shift-режима — post-MVP backlog (CONTEXT «без перфекционизма»)"

patterns-established:
  - "spoken_*-имена ресурсов AOSP-совместимы — будущий диф с LatinIME маппится один-к-одному"
  - "strings-a11y.xml отдельным файлом (по образцу AOSP strings-talkback-descriptions.xml) — вся a11y-лексика в одном месте"

requirements-completed: [A11Y-01, A11Y-02]

# Coverage metadata (#1602)
coverage:
  - id: D1
    description: "26 spoken_*-строк (en base + ru) + KeyDescriptionMapper: «татарская э» для ә по кодпоинту, заглавные шаблоном, служебные по elementId/imeAction"
    requirement: A11Y-02
    verification:
      - kind: other
        ref: "Task 1 verify: assembleDebug + fail-capable-грепы (6 hex-кодпоинтов, 26 имён в base И ru, isUpperCase/imeAction/mElementId, ноль getIconName/getIdentifier)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Делегат достроен: описания маппером, isClickable/isTextEntryKey, ACTION_CLICK → синтез MotionEvent DOWN/UP → processMotionEvent + TYPE_VIEW_CLICKED + return true"
    requirement: A11Y-01
    verification:
      - kind: other
        ref: "Task 2 verify: assembleDebug + грепы (processMotionEvent/ACTION_DOWN/ACTION_UP/TYPE_VIEW_CLICKED/return true/isClickable/isTextEntryKey, ноль KeyboardIconsSet, stale-ветка сохранена, ноль .java в диффе)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Структурная верификация фазы: сборки + no-internet + APK-гейт + все fail-capable-линии + zero-fork-Java boundary (0a280ce: ровно 4 файла, ни одного .java) + bookkeeping"
    verification:
      - kind: other
        ref: "Task 3 verify: assembleDebug + assembleRelease + check-no-internet.sh + APK 718695 ≤ 3145728 + полный греп-набор + boundary-счётчик =4 + REQUIREMENTS/STATE-чеки"
        status: pass
    human_judgment: false
  - id: D4
    description: "On-device TalkBack UAT: SC3 (слово «әни» набрано и отправлено с TalkBack) + SC4 (обычный ввод не деградировал) + explore-by-touch озвучка всех клавиш"
    requirement: A11Y-01
    verification: []
    human_judgment: true
    rationale: "TalkBack-поведение (озвучка, double-tap/lift-to-type ввод, earcon) проверяемо только на устройстве; adb devices пуст — deferred в UAT-бандл фаз 1–9 по standing-паттерну (чек-лист self-contained в STATE.md Blockers)"

# Metrics
duration: 13min
completed: 2026-07-18
status: complete
---

# Phase 9 Plan 01: Доступность (TalkBack) Summary

**TalkBack-ввод достроен до полной A11Y-01/02: «татарская э» для ә ресурсами en/ru через KeyDescriptionMapper, клик по виртуальному узлу = синтез MotionEvent в штатный touch-путь (fork-Java-дифф 0 строк), isTextEntryKey/TYPE_VIEW_CLICKED — все 4 гэпа ресерча закрыты**

## Performance

- **Duration:** 13 min
- **Started:** 2026-07-18T20:09:02Z
- **Completed:** 2026-07-18T20:22:30Z
- **Tasks:** 3 of 4 (Task 4 device UAT deferred)
- **Files modified:** 7 (4 app + REQUIREMENTS.md + STATE.md + ROADMAP.md)

## Accomplishments

- **G2 закрыт (m3):** описания клавиш только через новый `KeyDescriptionMapper` — 6 татарских букв «татарская э/о/у/ж/н/х» по кодпоинту (hex-литералы U+04D9/04E9/04AF/0497/04A3/04BB), заглавные шаблоном «Заглавная %s» (isUpperCase→toLowerCase→lookup), shift ×4 состояний по mElementId (ветки ELEMENT 4 нет — в форке отсутствует), enter ×7 по imeAction() с приоритетом custom label; ноль reflection/iconName-детекта.
- **26 spoken_*-строк данными:** `values/strings-a11y.xml` (en base) + `values-ru/strings-a11y.xml` — AOSP-совместимые имена, отдельный файл по образцу strings-talkback-descriptions.xml; values-ru/strings.xml нетронут.
- **G1 закрыт (блокер SC3):** `onPerformActionForVirtualView(ACTION_CLICK)` синтезирует MotionEvent DOWN/UP в видимый центр клавиши → public `MainKeyboardView.processMotionEvent` — весь штатный путь PointerTracker (shift-машина, haptics, preview) как от пальца; спейсер/чужой action → false; `ev.recycle()` после каждого события.
- **G3/G4 закрыты:** `node.isClickable = true` + `node.isTextEntryKey = true` (TalkBack keyboard-режим/lift-to-type; androidx.core транзитив — новых зависимостей нет) + `sendEventForVirtualView(TYPE_VIEW_CLICKED)` + `return true` (earcon, без ретраев).
- **Zero-fork-Java доказан механически:** diff 0a280ce..HEAD по app/ = ровно 4 файла (2 .kt + 2 .xml), ни одного .java; diff по PointerTracker/KeyboardState/MainKeyboardView/Key пуст — SC4 by construction.
- **Гейты:** assembleDebug + assembleRelease зелёные, check-no-internet OK, release-APK 718 695 байт (≤ 3 145 728; +18 016 к фазе 7-8 базе).

## Task Commits

Each task was committed atomically:

1. **Task 1: a11y-строки (en+ru) + KeyDescriptionMapper** - `81a82db` (feat)
2. **Task 2: делегат — маппер, isClickable/isTextEntryKey, ACTION_CLICK → синтез MotionEvent** - `6d8b65e` (feat)
3. **Task 3: структурная верификация + boundary + bookkeeping** - `011f060` (docs)
4. **Task 4: on-device TalkBack UAT** — DEFERRED (устройства нет; чек-лист в STATE.md Blockers)

**Plan metadata:** см. финальный docs-коммит этого SUMMARY

## Files Created/Modified

- `app/src/main/java/rkr/simplekeyboard/inputmethod/accessibility/KeyDescriptionMapper.kt` — NEW: Kotlin object, getDescription(context, keyboard, key) — when по коду, без состояния
- `app/src/main/res/values/strings-a11y.xml` — NEW: en base, 26 spoken_*-строк
- `app/src/main/res/values-ru/strings-a11y.xml` — NEW: ru-оверлей тех же 26 имён
- `app/src/main/java/rkr/simplekeyboard/inputmethod/accessibility/KeyboardAccessibilityDelegate.kt` — populate через маппер (KeyboardIconsSet удалён), свойства узла, perform-action синтезом, KDoc обновлён
- `.planning/REQUIREMENTS.md` — 2 аннотации A11Y, Traceability ×2 → Verifying (чек-боксы НЕ проставлены до UAT)
- `.planning/STATE.md` — decision [09-01], 2 backlog-записи, Blockers-чекпойнт фазы 9
- `.planning/ROADMAP.md` — Progress-строка фазы 9

## Decisions Made

- Синтез MotionEvent вместо прямого вызова listener: `mKeyboardActionListener` приватен, ручная последовательность onPressKey→onCodeInput→onReleaseKey — риск рассинхрона shift-машины; синтез переиспользует весь штатный путь бесплатно (AOSP-паттерн).
- Видимый центр клавиши (x+w/2), не mHitbox.centerX(): mHitbox приватен, геттер в Key.java не добавлен (zero-fork-Java; видимый центр всегда внутри hitbox).
- `processMotionEvent` вместо `onTouchEvent`: обходит NonDistinctMultitouchHelper-ветку — детерминированный путь для одноточечной синтетики.
- Password-описания не обскьюрены (осознанно): ACCESSIBILITY_SPEAK_PASSWORD deprecated с API 26, озвучка — зона TalkBack; собственных announce в IME ноль (запиновано грепом).
- CODE_OUTPUT_TEXT → key.outputText с fallback на unknown (null-safety сверх плана — outputText теоретически nullable).

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## Authentication Gates

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Фаза 9 complete-local: A11Y-01/02 структурно доказаны и запинованы fail-capable-грепами; финальное end-to-end доказательство (SC3: слово с TalkBack; SC4: обычный ввод) — deferred UAT-бандл фаз 1–9 (STATE.md Blockers, self-contained чек-лист).
- Phase 10 (Онбординг и настройки) может стартовать: a11y-слой закончен, зависимостей от device-UAT у фазы 10 нет.
- Backlog зафиксирован: moreKeys-панель вне a11y-дерева (ё/ъ недостижимы с TalkBack — не блокер MVP), values-tt + announce shift-режима — post-MVP.

## Self-Check: PASSED

- Все 4 key-files.created/modified существуют на диске ✓
- `git log --grep="09-01"` — 3 коммита (81a82db, 6d8b65e, 011f060) ✓
- Acceptance criteria Task 1–3 перепрогнаны в составе Task 3 verify — PASS ✓
- Plan-level verification 1–6 — PASS; п. 7 (checkpoint) — честно отложен ✓

---
*Phase: 09-dostupnost*
*Completed: 2026-07-18*
