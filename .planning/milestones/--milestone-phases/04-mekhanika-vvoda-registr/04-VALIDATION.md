---
phase: 4
slug: mekhanika-vvoda-registr
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: verified (mechanical) — on-device items deferred
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-18
---

# Phase 4 — Validation Strategy

> Per-phase validation contract. **Тонкая фаза:** zero-code верификация — вердикт ресерча ALL WORKS, gap list пуст; автоматика = структурные грепы + boundary-diff, runtime = только on-device.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Gradle build + grep + git diff (решение фаз 2–3 сохраняется: юнит-харнес для стейт-машины/InputConnection несоразмерен; runtime shift/автокапс/автоповтор/Enter — только on-device) |
| **Config file** | none — Wave 0 покрыт инфраструктурой фазы 1 (`scripts/check-no-internet.sh` существует) |
| **Quick run command** | `./gradlew assembleDebug` |
| **Full suite command** | `./gradlew assembleDebug && bash scripts/check-no-internet.sh` |
| **Estimated runtime** | ~60–120 seconds |

---

## Sampling Rate

- **After every task commit:** `./gradlew assembleDebug` (Task 2 меняет только .planning/ — сборка тривиально зелёная, гоняется как инвариант)
- **After every plan wave:** full suite + ZERO-CODE boundary-diff (`git diff --name-only b19ce97..HEAD -- '*.java' '*.kt' 'app/'` пуст)
- **Before `/gsd-verify-work`:** full suite green + все структурные грепы Task 1 + bookkeeping-грепы Task 2
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 04-01.T1 | 04-01 | 1 | INPUT-01..04 (structural) | 04-01/T1 | Все доказательства ресерча запинованы fail-capable-грепами; сборка + no-internet зелёные; diff кода фазы пуст | build + grep + git test | единая verify-команда Task 1 (см. план) — грепы по KeyboardState/InputLogic/RichInputConnection/key_styles_*/config-common + `[ -z "$(git diff --name-only b19ce97..HEAD -- '*.java' '*.kt' 'app/')" ]` | ✅ (сборка фазы 1) | ⬜ pending |
| 04-01.T2 | 04-01 | 1 | INPUT-01/03 (annotations), bookkeeping | — | Аннотации трактовок в REQUIREMENTS.md (чек-боксы пустые, Traceability=Verifying×4); [04-01]-decision + backlog в STATE.md; app/ нетронут | grep + git test | `grep 'семантика идентична' REQUIREMENTS.md && grep '\[04-01\]' STATE.md && [ "$(grep -c '^- \[ \] \*\*INPUT-0[1-4]\*\*' REQUIREMENTS.md)" = "4" ] && [ -z "$(git diff --name-only -- app/)" ]` | ✅ | ⬜ pending |
| 04-01.T3 | 04-01 | 1 | INPUT-01..04 + Phase SC1–SC5 (on-device) | — | Shift 3 состояния (пятый ряд заглавный), автокапс по полю, backspace кодпоинты+серия, Enter-действия, smoke WebView 229/password | manual | — (checkpoint:human-verify, стандартная отложенная схема) | — | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] Рабочая сборка (`./gradlew assembleDebug`) — с фазы 1
- [x] `scripts/check-no-internet.sh` — с фазы 1
- [x] Раскладки tatar/russian + реестр тройки subtypes (фазы 2–3) — объект верификации; механика INPUT-01..04 уже в базе форка

*Новых Wave 0 зависимостей нет. Фаза не пишет код вообще — вся её «инфраструктура» существует.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Shift: off → тап (одна заглавная, пятый ряд Ә Ө Ү Җ Ң Һ) → двойной тап caps lock (sticky-иконка) → тап-выход; long-press → caps lock (бонус) | INPUT-01 | Апкейс через фолбэк shifted-элементов и sticky-рендер — runtime KeyboardSwitcher/Key; double-tap — системный таймаут | Task 3 п.2: тапы/двойной тап/long-press shift на татарской, выборочно на русской |
| Автокапс: shifted в Telegram и после «. »; НЕ shifted в адресной строке Chrome/email; выключается префом Auto-capitalization | INPUT-02 | CapsModeUtils против реальных InputType-полей + цепочка onUpdateSelection — только в реальных редакторах | Task 3 п.3 |
| Backspace: ә/җ/ң удаляются целиком за одно нажатие; удержание → серия ~20/сек после ~0.4 с | INPUT-03 | deleteSurroundingText против реального InputConnection; тайминги TimerHandler — runtime | Task 3 п.4: «әни өй үрдәк» + удержание |
| Enter: лупа/поиск в Chrome, send в Telegram, перенос в заметках, done-галка в форме | INPUT-04 | Иконка из imeAction и performEditorAction — зависят от EditorInfo реальных приложений | Task 3 п.5 |
| Smoke SC5: backspace и Enter корректны в WebView (keyCode 229) и password-поле | Phase SC5 (cross-cutting STATE.md) | Зоопарк InputConnection — только в реальных окружениях | Task 3 п.6; MIUI — п.7 при наличии Xiaomi |

*Если устройство недоступно (как в фазах 1–3) — чекпойнт Task 3 откладывается в STATE.md Blockers с чек-листом; структурные критерии закрыты автоматикой, но INPUT-01..04 не считаются полностью верифицированными (Traceability остаётся Verifying, чек-боксы пустые) до прогона.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
