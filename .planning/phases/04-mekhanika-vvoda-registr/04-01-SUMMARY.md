---
phase: 04-mekhanika-vvoda-registr
plan: 01
subsystem: ime-input-mechanics
tags: [android, ime, shift, capslock, autocaps, backspace, enter, verification, zero-code]

requires:
  - phase: 03-raskladki-pereklyuchenie
    provides: "layout sets tatar/russian (rows_*.xml, kbd_*.xml), реестр subtypes tt_RU/ru/en_US — база, на которой механика форка подхватывается без правок"
provides:
  - "Структурная верификация INPUT-01..04: все доказательства 04-RESEARCH.md запинованы fail-capable-грепами на HEAD (вердикт ALL WORKS подтверждён)"
  - "ZERO-CODE boundary доказан механически: git diff b19ce97..HEAD по '*.java' '*.kt' 'app/' пуст"
  - "Bookkeeping: аннотации трактовок INPUT-01/INPUT-03 в REQUIREMENTS.md, Traceability → Verifying×4, decision [04-01] + backlog прогрессивного разгона в STATE.md"
affects: [phase-05-gestures, phase-08-compat-matrix, verify-work, uat]

tech-stack:
  added: []
  patterns: ["zero-code verification phase: механика доказывается грепами + diff-boundary вместо правок кода"]

key-files:
  created:
    - .planning/phases/04-mekhanika-vvoda-registr/04-01-SUMMARY.md
  modified:
    - .planning/REQUIREMENTS.md
    - .planning/STATE.md
    - .planning/ROADMAP.md

key-decisions:
  - "Трактовка INPUT-03 принята пользователем (2026-07-18): подсчёт chars по кодпоинту + deleteSurroundingText = семантический эквивалент deleteSurroundingTextInCodePoints; «ускоряется» = AOSP-автоповтор 400 мс → 50 мс"
  - "Прогрессивный разгон backspace (удаление словами) — backlog post-MVP, не пробел фазы"
  - "Бонус INPUT-01 зафиксирован аннотацией: caps lock также long-press'ом shift (1200 мс)"
  - "Чек-боксы INPUT-01..04 не проставлены до UAT/verify-work (прецедент фазы 3); Traceability = Verifying"

patterns-established:
  - "ZERO-CODE boundary-check: диф кода фазы обязан быть пуст — механический критерий вместо декларации"

requirements-completed: []  # INPUT-01..04 в статусе Verifying — финальная простановка после device UAT

coverage:
  - id: D1
    description: "INPUT-01 shift/caps-lock: shift-машина (двойной тап через ViewConfiguration.getDoubleTapTimeout, setShiftLocked, stickyOn-визуал alphabetShiftLocked, needsToUpcase для лейблов, long-press 1200 мс) присутствует и подхватывается rows_tatar/rows_russian"
    requirement: INPUT-01
    verification:
      - kind: other
        ref: "Task 1 verify chain: greps KeyboardState/TimerHandler/Key/key_styles_common/config-common + shiftKeyStyle в обоих rows_*"
        status: pass
    human_judgment: false
  - id: D2
    description: "INPUT-02 автокапс: getCurrentAutoCapsState → layoutUsesAutoCaps (tatar/russian идут default-веткой → true) → CapsModeUtils.getCapsMode; PREF_AUTO_CAP default true"
    requirement: INPUT-02
    verification:
      - kind: other
        ref: "Task 1 verify chain: greps InputLogic/RichInputConnection/SettingsValues + sed-проверка тела layoutUsesAutoCaps на отсутствие tatar/russian"
        status: pass
    human_judgment: false
  - id: D3
    description: "INPUT-03 backspace: supplementary ? 2 : 1 → deleteSurroundingText(numChars, 0); автоповтор deleteKeyStyle isRepeatable, 400/50 мс"
    requirement: INPUT-03
    verification:
      - kind: other
        ref: "Task 1 verify chain: greps InputLogic/RichInputConnection/key_styles_common/config-common + deleteKeyStyle в обоих rows_*"
        status: pass
    human_judgment: false
  - id: D4
    description: "INPUT-04 Enter: key_styles_enter switch (≥6 imeAction-кейсов), IME_FLAG_NO_ENTER_ACTION, performEditorAction; row_qwerty4 и key_styles_common включены в наши раскладки"
    requirement: INPUT-04
    verification:
      - kind: other
        ref: "Task 1 verify chain: greps key_styles_enter/InputTypeUtils/InputLogic + row_qwerty4/key_styles_common в rows_*/kbd_*"
        status: pass
    human_judgment: false
  - id: D5
    description: "ZERO-CODE boundary + сборка + приватность: диф кода фазы пуст, assembleDebug зелёный, check-no-internet OK"
    verification:
      - kind: automated_ui
        ref: "./gradlew assembleDebug && bash scripts/check-no-internet.sh && [ -z \"$(git diff --name-only b19ce97..HEAD -- '*.java' '*.kt' 'app/')\" ]"
        status: pass
    human_judgment: false
  - id: D6
    description: "On-device UAT: shift 3 состояния с заглавным пятым рядом Ә Ө Ү Җ Ң Һ, автокапс по типу поля, backspace-серия/кодпоинты, Enter-действия, smoke SC5 (WebView 229 + password)"
    verification: []
    human_judgment: true
    rationale: "Рантайм-поведение на реальном устройстве (A1: runtime vs структура) не проверяемо без adb-устройства; deferred в STATE.md Blockers по standing-паттерну фаз 1–3"

duration: 10min
completed: 2026-07-18
status: complete
---

# Phase 04 Plan 01: Механика ввода — тонкая верификация Summary

**INPUT-01..04 доказаны структурно без единой правки кода: вердикт ресерча ALL WORKS запинован fail-capable-грепами, zero-code boundary подтверждён пустым дифом b19ce97..HEAD по app/; принятые трактовки зафиксированы в REQUIREMENTS.md; device UAT отложен (Task 3 deferred — human verification pending).**

## Performance

- **Duration:** ~10 min
- **Completed:** 2026-07-18
- **Tasks:** 2 of 3 (Task 3 checkpoint deferred)
- **Files modified:** 3 planning-документа (ноль файлов под app/)

## Accomplishments

- **Task 1 — структурная верификация: ALL PASS.** Единая fail-capable команда (сборка + no-internet + ~27 грепов + boundary-check) прошла целиком:
  - INPUT-01: shiftKeyStyle в rows_tatar/rows_russian; alphabetShiftLocked → stickyOn; startDoubleTapShiftKeyTimer + setShiftLocked(true) в KeyboardState; getDoubleTapTimeout в TimerHandler; needsToUpcase в Key.java; config_longpress_shift_lock_timeout = 1200 — PASS
  - INPUT-02: getCurrentAutoCapsState; тело layoutUsesAutoCaps без tatar/russian (default → true); CapsModeUtils.getCapsMode в RichInputConnection; PREF_AUTO_CAP default true — PASS
  - INPUT-03: isSupplementaryCodePoint (? 2 : 1); deleteSurroundingText(numChars, 0); deleteKeyStyle isRepeatable + использование в обоих rows_*; тайминги 400/50 — PASS
  - INPUT-04: key_styles_enter с ≥6 imeAction-кейсами; IME_FLAG_NO_ENTER_ACTION в InputTypeUtils; performEditorAction в InputLogic; row_qwerty4 + key_styles_common в наших раскладках — PASS
  - Сборка: `./gradlew assembleDebug` BUILD SUCCESSFUL; `check-no-internet.sh` Level 1 + Level 2 OK — PASS
  - **ZERO-CODE boundary:** `git diff --name-only b19ce97..HEAD -- '*.java' '*.kt' 'app/'` пуст — PASS
- **Task 2 — bookkeeping:** аннотации INPUT-03 (эквивалент + AOSP-автоповтор + backlog-ссылка) и INPUT-01 (бонус long-press caps lock) в REQUIREMENTS.md; чек-боксы INPUT-01..04 остались пустыми; Traceability → `Verifying (04-01: structural PASS; on-device UAT deferred)` ×4; decision [04-01] + backlog-пункт прогрессивного разгона в STATE.md.
- **Task 3 — deferred:** устройство не подключено (adb devices пуст) — по standing defer-and-accept решению чекпойнт отложен в STATE.md Blockers; фаза закрыта complete-local.

## Task Commits

1. **Task 1: Структурная верификация** — без коммита (read-only проверки; результаты в этом SUMMARY)
2. **Task 2: Bookkeeping** — `4ab9cc8` (docs)
3. **Task 3: On-device UAT** — deferred — human verification pending (Blockers-запись + этот SUMMARY в закрывающем коммите)

## Decisions Made

- Трактовка INPUT-03 (принята пользователем 2026-07-18): эквивалент deleteSurroundingTextInCodePoints + AOSP-автоповтор 400→50 мс; прогрессивный разгон — backlog post-MVP.
- Финальная простановка галочек INPUT-01..04 — после device UAT/verify-work (прецедент фазы 3).

## Deviations from Plan

None — plan executed exactly as written (Task 3 deferral — предусмотренная планом стандартная схема).

## Deferred Verification (Task 3 — device UAT checklist)

Прогнать при появлении устройства (всё на татарской раскладке; пп. shift/backspace выборочно повторить на русской):

1. **Установка:** текущий `app-debug.apk` (`adb install`; uninstall не обязателен — фаза не меняла дефолты).
2. **INPUT-01 (shift/caps):** тап shift → пятый ряд показывает Ә Ө Ү Җ Ң Һ + ЙЦУКЕН заглавные; ввод одной буквы → возврат в строчные; двойной тап shift → caps lock (иконка sticky/закрашенная), серия заглавных, тап → выход; long-press shift (~1.2 с) → caps lock (бонус).
3. **INPUT-02 (автокапс):** новое сообщение в Telegram → клавиатура открывается shifted; после «. » → снова shifted; адресная строка Chrome и email-поле → НЕ shifted; выключить `Auto-capitalization` в настройках → эффект пропадает; включить обратно.
4. **INPUT-03 (backspace):** набрать «әни өй үрдәк», удержать backspace → после ~0.4 с серия ~20 удалений/сек; «ә/җ/ң» удаляются целиком за одно нажатие (и одиночным тапом тоже).
5. **INPUT-04 (Enter):** Enter в поиске Chrome (иконка-лупа, выполняет поиск), в Telegram (send/перенос по настройке мессенджера), в заметках multiline (перенос строки), в форме с actionDone (галка, закрывает клавиатуру).
6. **Smoke-матрица SC5:** backspace и Enter корректны в WebView/поле формы Chrome (сценарий keyCode 229) и в password-поле (удаление по одному символу, Enter не ломает поле).
7. **MIUI:** при наличии Xiaomi; иначе явно пометить как не покрыто.

После подтверждения пп. 2–6: проставить чек-боксы INPUT-01..04 в REQUIREMENTS.md, перевести Traceability в Complete, снять Blockers-запись фазы 4.

## Next Phase Readiness

- Phase 5 (жесты и multi-touch) может стартовать: механика символьного уровня верифицирована структурно, база кода не тронута.
- Накопленный долг device UAT: фазы 1–4 (единый прогон при появлении устройства — чек-листы в Blockers).
