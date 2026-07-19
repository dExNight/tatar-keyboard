---
phase: 08-sovmestimost
plan: 01
subsystem: compatibility
tags: [ime, fullscreen-mode, extract-mode, edge-to-edge, insets, direct-boot, webview, password, uat-matrix]

# Dependency graph
requires:
  - phase: 07-zhivost
    provides: in-layer превью/панель (MIUI PopupWindow-класс проблем неприменим), zero-Java стилизация
  - phase: 05-zhesty
    provides: password-гейт double-space (mIsPasswordField, InputLogic.java:355)
  - phase: 04-mekhanika
    provides: backspace по кодпоинтам (deleteSurroundingText), no-composing путь ввода
provides:
  - "COMPAT-04 гэп закрыт данными: values-land config_use_fullscreen_mode true→false — extract mode мёртв во всех 5 config-вариантах"
  - "Все 5 COMPAT-вердиктов запинованы fail-capable-грепами (insets-линия, directBoot, no-composing, password, ландшафт)"
  - "08-UAT-MATRIX.md — письменный деливерабл SC5: 12 окружений × 8 сценариев, CLOSED-STRUCTURAL + DEFERRED, self-contained чеклист"
affects: [phase-9-dostupnost, phase-11-perf-release, uat-bundle]

# Tech tracking
tech-stack:
  added: []
  patterns: ["data-only phase: реализация решения проекта штатным ресурсным механизмом вместо Java-override", "двухуровневая честность UAT-матрицы: CLOSED-STRUCTURAL закрывает механизм, строка окружения — только после device-прогона"]

key-files:
  created:
    - .planning/phases/08-sovmestimost/08-UAT-MATRIX.md
  modified:
    - app/src/main/res/values-land/config.xml
    - .planning/REQUIREMENTS.md
    - .planning/STATE.md
    - .planning/ROADMAP.md

key-decisions:
  - "Fullscreen/extract mode отключён ресурсным флипом (values-land config_use_fullscreen_mode → false), Java-override onEvaluateFullscreenMode отклонён — штатный механизм даёт тот же результат при Java-диффе 0 строк"
  - "SC5 зафиксирован письменно в честном текущем состоянии: структурные механизмы CLOSED-STRUCTURAL со ссылками, все runtime-ячейки DEFERRED — ни одного PASS до device-прогона"

patterns-established:
  - "Греп-инвариант fullscreen: config_use_fullscreen_mode true=0 / false=5 файлов; сломается осознанно при upstream-мёрдже нового qualifier'а (A3)"

requirements-completed: [COMPAT-01, COMPAT-02, COMPAT-03, COMPAT-04, COMPAT-05]
# NB: чек-боксы COMPAT-01..05 в REQUIREMENTS.md НЕ проставлены (Traceability = Verifying) —
# финальная простановка только после on-device UAT (Task 4, deferred). Structural PASS зафиксирован.

coverage:
  - id: D1
    description: "Extract/fullscreen mode мёртв во всех конфигурациях: values-land config_use_fullscreen_mode true→false (COMPAT-04)"
    requirement: COMPAT-04
    verification:
      - kind: other
        ref: "grep: config_use_fullscreen_mode\">true = 0 вхождений в res/; \">false = 5 файлов; assembleDebug зелёный"
        status: pass
    human_judgment: false
  - id: D2
    description: "Все 5 COMPAT-вердиктов запинованы fail-capable-грепами: insets-линия (fitsSystemWindows v28-сплит, requestApplyInsets, onComputeInsets, contrast off), directBoot (манифест + device-protected prefs, ноль обходов), no-composing (0 вхождений) + commitText/deleteSurroundingText, password (mIsPasswordField-гейт + ноль словаря)"
    verification:
      - kind: other
        ref: "Task 2 verify-цепочка (08-01-PLAN.md): assembleDebug + assembleRelease + check-no-internet + 20 структурных грепов + boundary d2ae619..HEAD"
        status: pass
    human_judgment: false
  - id: D3
    description: "08-UAT-MATRIX.md — письменный деливерабл SC5: 12 окружений × 8 сценариев, легенда статусов, CLOSED-STRUCTURAL со ссылками, DEFERRED self-contained чеклист"
    verification:
      - kind: other
        ref: "Task 3 verify: наличие всех окружений/сценариев/статусов, анти-фабрикация — ноль строк 'PASS' в документе"
        status: pass
    human_judgment: false
  - id: D4
    description: "On-device исполнение UAT-матрицы: extract mode не появляется в ландшафте, API 35+ без перекрытия панелями, Direct Boot PIN-ввод, MIUI/One UI"
    requirement: COMPAT-04
    verification: []
    human_judgment: true
    rationale: "Runtime-поведение проверяемо только на устройстве/эмуляторе; adb devices пуст — deferred в UAT-бандл фаз 1–7 (standing-паттерн, принят пользователем)"

# Metrics
duration: 12min
completed: 2026-07-18
status: complete
---

# Phase 8 Plan 01: Совместимость Summary

**Extract mode убит однострочным флипом values-land config_use_fullscreen_mode, все 5 COMPAT-вердиктов запинованы fail-capable-грепами, SC5 получил письменный артефакт 08-UAT-MATRIX.md (12×8, CLOSED-STRUCTURAL + DEFERRED) — Java-дифф фазы 0 строк**

## Performance

- **Duration:** 12 min
- **Started:** 2026-07-18T19:02:31Z
- **Completed:** 2026-07-18T19:14:00Z
- **Tasks:** 3 of 4 (Task 4 checkpoint deferred — устройства нет)
- **Files modified:** 5 (1 app + 4 planning)

## Accomplishments

- **COMPAT-04 гэп закрыт данными:** `values-land/config.xml` `config_use_fullscreen_mode` true→false — единственная правка фазы под app/. Инвариант: `">true` = 0 вхождений, `">false` = 5 файлов (values, values-land, sw430/600/768). `onEvaluateFullscreenMode()` теперь всегда даёт false штатным ресурсным путём (hardware-keyboard-ветка сохранена, Java-override не писался).
- **Все 5 вердиктов ресерча запинованы fail-capable-грепами** (Task 2, полная verify-цепочка зелёная): COMPAT-03 insets-линия upstream 827da4f/2885ae5 (fitsSystemWindows v28-сплит + requestApplyInsets:333 + onComputeInsets:535 + contrast off:935); COMPAT-05 directBoot (манифест:31 + createDeviceProtectedStorageContext, ноль обходных getDefaultSharedPreferences); COMPAT-02 no-composing (setComposingText/Region/finish — 0 вхождений) + commitText:606 / deleteSurroundingText:356; COMPAT-01 password (mIsPasswordField + double-space-гейт:355 + ноль Dictionary/UserHistory-файлов и импортов).
- **SC5 письменно зафиксирован:** `08-UAT-MATRIX.md` — 12 окружений × 8 сценариев; 5 структурных механизмов CLOSED-STRUCTURAL со ссылками на грепы/коммиты; все runtime-ячейки DEFERRED единым self-contained чеклистом (подготовка, порядок, спец-блоки E8 эмулятор API 35–36 / E9 ландшафт / E10 Direct Boot / E11 MIUI / E12 One UI); в документе ноль строк «PASS» — анти-фабрикация подтверждена грепом.
- **Zero-Java boundary:** `git diff d2ae619..HEAD -- app/` = ровно `values-land/config.xml`; `.java`/`.kt` в диффе — ноль. assembleDebug + assembleRelease + check-no-internet зелёные после каждой задачи.

## Task Commits

Each task was committed atomically:

1. **Task 1: Флип config_use_fullscreen_mode в values-land → false** - `4088f50` (fix)
2. **Task 2: Структурная верификация COMPAT-01..05 + bookkeeping** - `7d649a2` (docs)
3. **Task 3: 08-UAT-MATRIX.md — письменный деливерабл SC5** - `e92daf6` (docs)
4. **Task 4: On-device UAT** — DEFERRED (checkpoint:human-verify; adb devices пуст; запись в STATE.md § Blockers, этот же коммит plan metadata)

## Files Created/Modified

- `app/src/main/res/values-land/config.xml` — 1 строка: config_use_fullscreen_mode → false (extract mode отключён в последнем живом qualifier'е)
- `.planning/phases/08-sovmestimost/08-UAT-MATRIX.md` — письменный деливерабл SC5 (создан)
- `.planning/REQUIREMENTS.md` — 5 аннотаций COMPAT-01..05 (чек-боксы пусты), Traceability ×5 → Verifying (08-01: structural PASS; on-device UAT deferred)
- `.planning/STATE.md` — decision [08-01], Blockers-запись Phase 8 (UAT-бандл), позиция/метрики
- `.planning/ROADMAP.md` — Progress-строка Phase 8

## Decisions Made

- Ресурсный флип вместо Java-override (отклонён ресерчем): тот же результат штатным механизмом, Java-база нетронута.
- Двухуровневая честность матрицы: CLOSED-STRUCTURAL закрывает механизм, положительный статус строки окружения — только после device-прогона.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 8 complete-local: структурная часть закрыта доказательно, runtime-часть честно отложена (standing defer-and-accept, фазы 1–8 теперь единый UAT-бандл в STATE.md § Blockers; исполняемый чеклист фазы 8 — 08-UAT-MATRIX.md, self-contained).
- Готово к Phase 9 (Доступность): каркас ExploreByTouchHelper заложен в фазе 6, полная реализация + TalkBack-верификация.
- Финальная простановка чек-боксов COMPAT-01..05 — только после реального device-прогона (Task 4 / UAT-бандл).

## Self-Check: PASSED

- 08-UAT-MATRIX.md существует на диске: ✓ (`[ -f ]`)
- `git log --grep="08-01"` ≥ 1 коммит: ✓ (4088f50, 7d649a2, e92daf6)
- Все `<verify>` Task 1–3 повторно зелёные (verbatim): ✓
- Plan-level verification 1–6 закрыты; п.7 (Task 4) — честно отложен в STATE.md § Blockers: ✓

---
*Phase: 08-sovmestimost*
*Completed: 2026-07-18*
