---
phase: 10-onbording-i-nastroyki
plan: 01
subsystem: ui
tags: [android-ime, onboarding, setup-activity, kotlin, input-method-picker, settings]

requires:
  - phase: 09-a11y
    provides: KeyDescriptionMapper.kt (Kotlin-конвенции нового кода проекта)
  - phase: 07-otklik-i-styling
    provides: живая связка Settings→loadSettings→AudioAndHapticFeedbackManager (основа SETUP-02)
provides:
  - "SetupActivity — 2-шаговый онбординг (включить IME + выбрать текущим), статусы живьём из системы"
  - "MAIN/LAUNCHER на SetupActivity; SettingsActivity без LAUNCHER но exported=true (вход из IME цел)"
  - "SettingsActivity очищен от legacy not-enabled AlertDialog старого бренда"
  - "SETUP-02 верифицирован существующим (zero-code) — тумблеры звук/вибро живо-реактивны"
affects: [phase-11-релиз-ребрендинг, ic_launcher, setup_message-ребрендинг]

tech-stack:
  added: []
  patterns:
    - "Онбординг = classic View/XML Activity (android.app.Activity), ноль новых зависимостей"
    - "Live-детект системного состояния без собственного флага завершения (источник истины — система)"
    - "Рефреш статусов идемпотентно в onWindowFocusChanged(true)+onResume (picker = floating window)"

key-files:
  created:
    - app/src/main/java/rkr/simplekeyboard/inputmethod/latin/setup/SetupActivity.kt
    - app/src/main/res/layout/setup_activity.xml
    - app/src/main/res/values/strings-setup.xml
    - app/src/main/res/values-ru/strings-setup.xml
  modified:
    - app/src/main/AndroidManifest.xml
    - app/src/main/java/rkr/simplekeyboard/inputmethod/latin/settings/SettingsActivity.java

key-decisions:
  - "Онбординг = classic View/XML, Compose отклонён (locked, бюджет APK 3 МБ)"
  - "Детект шага 2 по префиксу packageName (устойчив к debug-суффиксу .debug), не точный equals"
  - "Собственный флаг «онбординг завершён» НЕ хранится — статусы читаются из системы каждый раз"
  - "Legacy not-enabled диалог удалён (проверка зависимостей грепом), setup_message оставлен до фазы 11"

patterns-established:
  - "Two-status live detection: enabledInputMethodList (шаг 1) + Settings.Secure.DEFAULT_INPUT_METHOD (шаг 2)"
  - "Refresh-on-return: updateStepStates() в onWindowFocusChanged+onResume, идемпотентен"

requirements-completed: [SETUP-01, SETUP-02]

coverage:
  - id: D1
    description: "SetupActivity — 2-шаговый онбординг: live-детект статусов, старт системных intents/picker, рефреш по возврату, done→SettingsActivity"
    requirement: SETUP-01
    verification:
      - kind: manual_procedural
        ref: "grep: enabledInputMethodList + DEFAULT_INPUT_METHOD + startsWith + ACTION_INPUT_METHOD_SETTINGS + showInputMethodPicker + onWindowFocusChanged + updateStepStates in SetupActivity.kt; ! getStringExtra/... (no intent-extras); ! SharedPreferences (no own flag)"
        status: pass
      - kind: other
        ref: "./gradlew assembleDebug assembleRelease (both green)"
        status: pass
    human_judgment: true
    rationale: "SC3 (чистая установка → от иконки до «ә» без подсказок, рефреш статусов по возврату из picker) требует device-прогона; структурно PASS, on-device UAT deferred (нет adb-устройства)"
  - id: D2
    description: "MAIN/LAUNCHER переезжает на SetupActivity; SettingsActivity без LAUNCHER но exported=true (вход из IME цел)"
    requirement: SETUP-01
    verification:
      - kind: manual_procedural
        ref: "grep: category.LAUNCHER count==1 и у SetupActivity; SettingsActivity-блок exported=true без LAUNCHER; setClass(LatinIME.this, SettingsActivity.class) в LatinIME.java:881; ! INTERNET"
        status: pass
    human_judgment: true
    rationale: "Неразрыв IME→настройки после снятия LAUNCHER + отсутствие призрака старой иконки (A2) подтверждаются только на устройстве; класс-интент запинован грепом, on-device deferred"
  - id: D3
    description: "Legacy not-enabled AlertDialog старого бренда + private isInputMethodOfThisImeEnabled удалены из SettingsActivity, осиротевшие импорты вычищены"
    requirement: SETUP-01
    verification:
      - kind: manual_procedural
        ref: "grep: ! isInputMethodOfThisImeEnabled (весь java) + ! setup_message (java) + ! AlertDialog + ! onStart в SettingsActivity.java; onCreate/isValidFragment целы; setup_message остаётся в strings.xml"
        status: pass
      - kind: other
        ref: "./gradlew assembleDebug (green after removal)"
        status: pass
    human_judgment: false
  - id: D4
    description: "SETUP-02 (звук/вибро вкл/выкл) — уже реализовано и живо-реактивно, доказано zero-code грепами"
    requirement: SETUP-02
    verification:
      - kind: manual_procedural
        ref: "grep: vibrate_on/sound_on/pref_keypress_sound_volume в prefs_screen_key_press.xml; KeyPressSettingsFragment в prefs.xml; hasVibrator+removePreference; registerOnSharedPreferenceChangeListener + onSettingsChanged (LatinIME + AudioAndHapticFeedbackManager)"
        status: pass
    human_judgment: true
    rationale: "SC2-live (тумблеры реально меняют отклик клавиатуры без перезапуска IME) требует device-прогона; структурная линия PASS, live-переключение deferred"

duration: 10 min
completed: 2026-07-19
status: complete
---

# Phase 10 Plan 01: Онбординг (SetupActivity) + верификация настроек звука/вибрации Summary

**Kotlin SetupActivity с 2-шаговым онбордингом (включить IME через ACTION_INPUT_METHOD_SETTINGS + выбрать через showInputMethodPicker), живой детект статусов из системы без собственного флага, LAUNCHER-переезд и удаление legacy-диалога; SETUP-02 доказан существующим zero-code.**

## Performance

- **Duration:** 10 min
- **Started:** 2026-07-18T21:46:47Z
- **Completed:** 2026-07-18T21:57:17Z
- **Tasks:** 4 of 5 (Task 5 device-UAT deferred)
- **Files modified:** 6 app-файлов (4 новых + 2 правки) + 2 planning

## Accomplishments
- Новый `SetupActivity.kt` — classic View/XML, ноль новых зависимостей: 2 карточки шагов со статусами, детект живьём (шаг 1 `enabledInputMethodList`, шаг 2 `Settings.Secure.DEFAULT_INPUT_METHOD` по префиксу пакета), кнопки → системные `ACTION_INPUT_METHOD_SETTINGS` / `showInputMethodPicker`, идемпотентный рефреш в `onWindowFocusChanged`+`onResume`, done-блок → `SettingsActivity` с подсказкой «печатайте ә».
- Манифест: MAIN/LAUNCHER переехал на SetupActivity (блок строго перед SettingsActivity); у SettingsActivity LAUNCHER снят, `exported=true` сохранён — IME→настройки цел (класс-интент `launchSettings` LatinIME.java:881).
- Legacy not-enabled `AlertDialog` старого бренда + private `isInputMethodOfThisImeEnabled` удалены из SettingsActivity после механической проверки зависимостей; осиротевшие импорты вычищены; `setup_message` оставлен в ресурсах нетронутым (ребрендинг = фаза 11).
- SETUP-02 доказан существующим (кода 0 строк): тумблеры `vibrate_on`/`sound_on`/громкость + `hasVibrator`→`removePreference` + живая линия `Settings`-listener→`loadSettings`→`AudioAndHapticFeedbackManager.onSettingsChanged` — все грепы PASS.

## Task Commits

Each task was committed atomically:

1. **Task 1: SetupActivity.kt + layout + строки** - `85b19fe` (feat)
2. **Task 2: Манифест — LAUNCHER переезд** - `a310509` (feat)
3. **Task 3: Удаление legacy not-enabled диалога** - `d98bed6` (refactor)
4. **Task 4: SETUP-02 верификация + boundary + bookkeeping** - `d9ee3b8` (docs)

**Plan metadata:** _(docs commit for SUMMARY + STATE + ROADMAP + REQUIREMENTS)_

_Task 5 — checkpoint:human-verify (device UAT) — отложен: adb-устройство не подключено._

## Files Created/Modified
- `app/.../latin/setup/SetupActivity.kt` (new) - Kotlin Activity онбординга: 2 детекта, 3 клик-листенера, updateStepStates, insets под guard API R+
- `app/src/main/res/layout/setup_activity.xml` (new) - ScrollView + 2 карточки шагов (статус/title/instruction/button) + done-блок; platformSettingsTheme, НЕ iOS-скин
- `app/src/main/res/values/strings-setup.xml` (new) - en base онбординг-строки
- `app/src/main/res/values-ru/strings-setup.xml` (new) - ru-оверлей, подсказка «Печатайте ә…»
- `app/src/main/AndroidManifest.xml` (mod) - SetupActivity MAIN/LAUNCHER перед SettingsActivity; SettingsActivity без LAUNCHER, exported сохранён
- `app/.../latin/settings/SettingsActivity.java` (mod) - удалён onStart-диалог + хелпер + 6 осиротевших импортов (−58 строк)

## Decisions Made
- Детект шага 2 по префиксу `"$packageName/"`, не точный equals — устойчиво к debug-суффиксу `.debug` (Pitfall 1).
- Собственного флага завершения нет — источник истины система (anti-pattern ресерча).
- Legacy-диалог удалён после re-грепа: `isInputMethodOfThisImeEnabled` и `setup_message` встречались в java только в SettingsActivity.java → безопасно.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None. Все fail-capable верификации прошли с первого прогона; обе сборки зелёные, release-APK 728 719 байт ≤ 3 145 728.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- SETUP-01/02 структурно PASS; фаза complete-local. Device-UAT (SC2-live/SC3/SC4) отложен в STATE.md Blockers self-contained чек-листом — прогнать вместе с UAT-бандлом фаз 1–9 при появлении устройства.
- Готово к Phase 11 (релиз/ребрендинг): backlog зафиксирован — ic_launcher (adaptive, наследие форка) + ~30 локализованных setup_message.

## Self-Check: PASSED

- Все 4 созданных файла существуют на диске (SetupActivity.kt, setup_activity.xml, strings-setup.xml ×2) + SUMMARY.md.
- Все 4 task-коммита присутствуют в git-истории (85b19fe, a310509, d98bed6, d9ee3b8).

---
*Phase: 10-onbording-i-nastroyki*
*Completed: 2026-07-19*
