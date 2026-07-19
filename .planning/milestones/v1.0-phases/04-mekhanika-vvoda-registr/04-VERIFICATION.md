---
phase: 04-mekhanika-vvoda-registr
verified: 2026-07-18
status: passed
previous_status: human_needed
human_verification_deferred: true
deferred_accepted_by: user standing decision (autonomous run 2026-07-18)
score: 13/13 mechanical (must-haves); 1 human checkpoint pending (deferred device UAT)
requirements: [INPUT-01, INPUT-02, INPUT-03, INPUT-04]
---

# Phase 4 Verification — Механика ввода: регистр и служебные клавиши

**Phase goal:** Полный цикл ввода символьного уровня: shift/caps-lock, автокапитализация, backspace по кодпоинтам с автоповтором, Enter по imeOptions.

**Phase type:** ZERO-CODE верификация — вердикт 04-RESEARCH.md ALL WORKS в базе форка; работа фазы = структурная верификация + bookkeeping + отложенный device UAT (standing-паттерн фаз 1–3).

## Verdict: **passed (mechanical) — human verification needed (deferred, standing accept pattern)**

Все автоматические проверки перепрогнаны вживую на текущем HEAD (7dcbd6b) и прошли. Zero-code boundary доказан пустым дифом. Runtime-поведение на устройстве остаётся за отложенным UAT (Task 3), запись в STATE.md Blockers присутствует.

## Live re-verification (all re-run 2026-07-18)

### 1. Структурная цепочка Task 1 — PASS

Единая fail-capable команда из 04-01-PLAN.md Task 1 перепрогнана целиком, все ~27 грепов прошли (`STRUCTURAL_ALL_PASS`):

| Req | Проверено | Result |
|-----|-----------|--------|
| INPUT-01 | `shiftKeyStyle` в rows_tatar/rows_russian; `alphabetShiftLocked` → `stickyOn`; `startDoubleTapShiftKeyTimer` + `setShiftLocked(true)` в KeyboardState.java; `getDoubleTapTimeout` в TimerHandler.java; `needsToUpcase` в Key.java; `config_longpress_shift_lock_timeout` = 1200 | ✅ |
| INPUT-02 | `getCurrentAutoCapsState` в InputLogic; тело `layoutUsesAutoCaps` не содержит tatar/russian (default → true); `CapsModeUtils.getCapsMode` в RichInputConnection; `PREF_AUTO_CAP, true` в SettingsValues | ✅ |
| INPUT-03 | `isSupplementaryCodePoint` (numChars = supplementary ? 2 : 1) в InputLogic; `deleteSurroundingText(numChars, 0)` в RichInputConnection; `deleteKeyStyle` с `isRepeatable` + использование в обоих rows_*; тайминги 400/50 в config-common.xml | ✅ |
| INPUT-04 | `key_styles_enter.xml` — ≥6 `latin:imeAction`-кейсов; `IME_FLAG_NO_ENTER_ACTION` в InputTypeUtils; `performEditorAction` в InputLogic; `row_qwerty4` в обоих rows_*; `key_styles_common` в kbd_tatar/kbd_russian | ✅ |

### 2. ZERO-CODE boundary — PASS

`git diff --name-only b19ce97..HEAD -- '*.java' '*.kt' 'app/'` → **пусто**. Все коммиты b19ce97..HEAD (8229df4, 530188e, fa09c36, 7af9b68, 655cc55, 4ab9cc8, 7dcbd6b) — только docs/planning. Фаза не тронула ни одного файла под `app/`.

### 3. Build + privacy — PASS

- `./gradlew assembleDebug` — exit 0 ✅
- `bash scripts/check-no-internet.sh` — exit 0 ✅

### 4. Bookkeeping — PASS

Все грепы Task 2 перепрогнаны (`BOOKKEEPING_PASS`):

- REQUIREMENTS.md: аннотация INPUT-03 присутствует (эквивалент `deleteSurroundingTextInCodePoints`, «семантика идентична», AOSP-автоповтор «400 мс старт → серия 50 мс», прогрессивный разгон → backlog, «принято пользователем», дата 2026-07-18) — строка 29 ✅
- REQUIREMENTS.md: аннотация INPUT-01 (бонус long-press caps lock 1200 мс) — строка 26 ✅
- Чек-боксы INPUT-01..04 остаются пустыми `[ ]` (ровно 4) — прецедент фазы 3 соблюдён ✅
- Traceability: ровно 4 строки `Verifying (04-01: structural PASS; on-device UAT deferred)`, не Complete ✅
- STATE.md: decision `[04-01]` (zero-code, трактовка INPUT-03, бонус INPUT-01, boundary-check) ✅
- STATE.md Open questions: backlog-пункт «Прогрессивный разгон backspace… TimerHandler.java:53» ✅
- STATE.md Blockers: запись `[Phase 4, plan 04-01]` с полным 7-пунктным чек-листом — рядом с деферралами фаз 1–3 ✅

### 5. Requirements cross-ref — PASS

Plan frontmatter `requirements: [INPUT-01, INPUT-02, INPUT-03, INPUT-04]` ↔ REQUIREMENTS.md секция «Механика ввода» INPUT-01..04 ↔ Traceability «Phase 4» ×4 — все четыре учтены, лишних/пропущенных нет. SUMMARY coverage D1–D6 закрывает все четыре требования + boundary (D5) + UAT (D6, human_judgment).

## must_haves scorecard

| # | Truth | Status |
|---|-------|--------|
| 1 | Доказательства ресерча запинованы fail-capable-грепами на HEAD | ✅ re-run PASS |
| 2 | Диф кода фазы пуст (b19ce97..HEAD, app/) | ✅ re-run PASS |
| 3 | Аннотации INPUT-01/03, чек-боксы пустые, Traceability = Verifying | ✅ |
| 4 | STATE.md: decision [04-01] + backlog прогрессивного разгона | ✅ |
| 5 | assembleDebug + check-no-internet зелёные | ✅ re-run PASS |
| 6 | On-device UAT — пройден или честно отложен в Blockers | ✅ отложен по standing-паттерну |

Prohibitions соблюдены: №1 (app/ нетронут) — доказан дифом; №2 (нет прогрессивного разгона) — следует из пустого дифа; №3 (чужой scope) — следует из пустого дифа.

## human_verification

**Status: needed (deferred — standing accept pattern, phases 1–4; пользователь помечает accepted-deferred).**

7-пунктный чек-лист из 04-01-SUMMARY.md § Deferred Verification (зеркалирован в STATE.md Blockers), прогнать при появлении устройства:

1. Установка текущего app-debug.apk (uninstall не обязателен).
2. **INPUT-01:** тап shift → пятый ряд Ә Ө Ү Җ Ң Һ + ЙЦУКЕН заглавные; одна буква → возврат; двойной тап → caps lock (sticky), тап → выход; long-press ~1.2 с → caps lock (бонус).
3. **INPUT-02:** Telegram — открывается shifted, после «. » снова shifted; адресная строка Chrome/email — НЕ shifted; выключение Auto-capitalization убирает эффект.
4. **INPUT-03:** «әни өй үрдәк», удержание backspace → ~0.4 с задержка, серия ~20/сек; ә/җ/ң удаляются целиком одним нажатием.
5. **INPUT-04:** Enter — поиск Chrome (лупа), Telegram (send), заметки multiline (перенос), форма actionDone (галка).
6. **Smoke SC5:** backspace+Enter в WebView/Chrome-форме (keyCode 229) и password-поле.
7. MIUI — при наличии Xiaomi (иначе пометить как не покрыто).

После подтверждения пп. 2–6: проставить чек-боксы INPUT-01..04, Traceability → Complete, снять Blockers-запись фазы 4.

## Gaps

None mechanical. Единственный открытый пункт — runtime device UAT (assumption A1 плана), покрыт human_verification выше.
