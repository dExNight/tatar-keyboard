---
phase: 5
slug: zhesty-i-multi-touch
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-18
---

# Phase 5 — Validation Strategy

> Per-phase validation contract. Одна кодовая задача (double-space restore) + конфиг-флип + структурная верификация INPUT-07. Автоматика = сборка + fail-capable-грепы + boundary-diff; runtime-поведение (тайминг, revert, свайп, двупальцевая печать) — только on-device.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Gradle build + grep + git diff (решение фаз 2–4 сохраняется: юнит-харнеса нет — несоразмерен; поведение жестов доказывается только на устройстве) |
| **Config file** | none — Wave 0 покрыт инфраструктурой фазы 1 (`scripts/check-no-internet.sh` существует) |
| **Quick run command** | `./gradlew assembleDebug` |
| **Full suite command** | `./gradlew assembleDebug && bash scripts/check-no-internet.sh` |
| **Estimated runtime** | ~60–120 seconds |

---

## Sampling Rate

- **After every task commit:** `./gradlew assembleDebug`
- **After every plan wave:** full suite + boundary-diff (`git diff --name-only 8e4693e..HEAD -- app/` ⊆ 5 объявленных файлов; новых `.kt` нет)
- **Before `/gsd-verify-work`:** full suite green + все грепы Task 1–3
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 05-01.T1 | 05-01 | 1 | INPUT-05 | 05-01/T1 | tryDoubleSpacePeriod (1100 мс, mIsPasswordField-гейт, isLetterOrDigit-чек), revert по backspace, always-on без pref; гигиена состояния: сброс mJustDoubleSpaced/mLastSpaceDownTime в startInput() и на любом событии кроме успешного double-space; сборка зелёная | build + grep | verify-команда Task 1 (грепы по InputLogic вкл. `grep -A3 'public void startInput' … \| grep mJustDoubleSpaced` + отсутствие getDoubleTapTimeout/pref_double_space) | ✅ (сборка фазы 1) | ⬜ pending |
| 05-01.T2 | 05-01 | 1 | INPUT-06 | — | Default true согласован ×3 (Settings.java + prefs XML + app_restrictions); pref_delete_swipe запинован false ×3; механика не тронута; перепутанные title в app_restrictions (pre-existing) не чинятся — backlog | build + grep | verify-команда Task 2 (`PREF_SPACE_SWIPE, true` + два `defaultValue="true"` у space_swipe + `PREF_DELETE_SWIPE, false` + два `defaultValue="false"` у delete_swipe) | ✅ | ⬜ pending |
| 05-01.T3 | 05-01 | 1 | INPUT-07 (structural) + boundary + bookkeeping | 05-01/T2 | Доказательства multi-touch запинованы; диф фазы ⊆ 5 файлов; check-no-internet OK; Traceability = Verifying×3, чек-боксы пустые, decision [05-01] | build + grep + git test | verify-команда Task 3 (грепы PointerTracker/PointerTrackerQueue/MainKeyboardView + boundary-diff от 8e4693e + bookkeeping-грепы) | ✅ | ⬜ pending |
| 05-01.T4 | 05-01 | 1 | INPUT-05..07 + Phase SC1–SC4 (on-device) | 05-01/T1 | Double-space «. »/revert/password-гейт/тайминг; свайп-курсор из коробки поверх ә/җ; двупальцевая печать без потерь; smoke Telegram/WebView 229/password | manual | — (checkpoint:human-verify, стандартная отложенная схема фаз 1–4) | — | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] Рабочая сборка (`./gradlew assembleDebug`) — с фазы 1
- [x] `scripts/check-no-internet.sh` — с фазы 1
- [x] Механика свайпа и multi-touch — уже в базе форка (объект верификации, не создания)

*Новых Wave 0 зависимостей нет.*

---

## Manual-Only Verifications

On-device UAT (Task 4, отложенная схема при недоступном устройстве — как фазы 1–4):

1. **INPUT-05 (SC1):** двойной пробел < 1.1 с после буквы → «әни. » + shift; backspace → откат к двум пробелам; после не-буквы / медленный второй пробел / password-поле → без точки.
2. **INPUT-06 (SC2):** свайп по пробелу двигает курсор сразу после установки (uninstall желателен — старые dev-prefs маскируют новый default); шаги по кодпоинтам поверх ә/җ; выключается prefом.
3. **INPUT-07 (SC3):** быстрая двупальцевая печать «әни өй үрдәк җир» — ни одна буква не потеряна, порядок сохранён.
4. **SC4 smoke-матрица:** пп. 1–3 в Telegram, Chrome WebView/поле формы (keyCode 229), password-поле; MIUI — при наличии Xiaomi (иначе пометить как не покрыто).

**Почему без автоматики:** тайминг двойного тапа, жест свайпа и одновременные касания — MotionEvent-поведение реального тачскрина + IME-взаимодействие с реальными приложениями; юнит-харнес для InputLogic отклонён (решение фаз 2–4, несоразмерен соло-MVP); поведенческая эмуляция MotionEvent требовала бы instrumented-тестов — вне бюджета фазы.

---

## Boundary Contract

- База дифа: **8e4693e** (docs-коммит ресерча фазы 5 — последний коммит до кода фазы).
- Разрешённые файлы под `app/`: `latin/inputlogic/InputLogic.java`, `latin/RichInputConnection.java` (только при добавлении аксессора), `latin/settings/Settings.java`, `res/xml/prefs_screen_preferences.xml`, `res/xml/app_restrictions.xml`.
- Запрещено: PointerTracker, PointerTrackerQueue, NonDistinctMultitouchHelper, LatinIME, KeyboardState, InputAttributes, манифест, зависимости, новые `.kt`, новые prefs.
- Чек: `[ -z "$(git diff --name-only 8e4693e..HEAD -- 'app/' | grep -v -E 'InputLogic\.java|RichInputConnection\.java|settings/Settings\.java|prefs_screen_preferences\.xml|app_restrictions\.xml')" ]`
