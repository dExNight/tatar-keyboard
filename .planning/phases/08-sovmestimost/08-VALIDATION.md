---
phase: 8
slug: sovmestimost
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
status: verified (mechanical) — on-device items deferred
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-18
---

# Phase 8 — Validation Strategy

> Per-phase validation contract. Фаза — data/docs: вся кодовая работа = 1 строка в `values-land/config.xml` (fullscreen-флип); остальное — fail-capable-верификация пяти вердиктов WORKS ресерча (insets-линия upstream, no-composing, password structurally-free, directBoot device-protected) + письменная UAT-матрица как деливерабл SC5. Автоматика = сборка debug+release + грепы + boundary-diff; реальное поведение (extract mode, перекрытие панелями, Direct Boot PIN, MIUI) — только on-device по матрице.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Gradle build (debug + release) + grep/find + git diff — решение фаз 2–7 сохраняется: юнит-харнеса нет; runtime-совместимость доказывается только устройством/эмулятором по 08-UAT-MATRIX.md |
| **Config file** | none — Wave 0 покрыт инфраструктурой фазы 1 (`scripts/check-no-internet.sh`) |
| **Quick run command** | `./gradlew assembleDebug` |
| **Full suite command** | `./gradlew assembleDebug assembleRelease && bash scripts/check-no-internet.sh` |
| **Estimated runtime** | ~90–180 seconds |

---

## Sampling Rate

- **After every task commit:** `./gradlew assembleDebug`
- **After Task 1:** греп-инвариант fullscreen (`true`=0, `false`=5) + boundary-diff (дифф под app/ = ровно values-land/config.xml)
- **After Task 2:** full suite + полный греп-пакет пяти вердиктов + boundary + bookkeeping-грепы
- **After Task 3:** структурные грепы матрицы (окружения/сценарии/легенда присутствуют; ни одного PASS)
- **Before `/gsd-verify-work`:** full suite green + все грепы Task 1–3
- **Max feedback latency:** 180 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 08-01.T1 | 08-01 | 1 | COMPAT-04 (fullscreen-гэп) | T1, T2 | `config_use_fullscreen_mode` = false во всех 5 config-вариантах (`true`-вхождений ноль); соседние ключи values-land (высоты, 5row) нетронуты; дифф под app/ = ровно 1 файл | build + grep + git diff | verify-команда Task 1 (греп-инвариант true=0/false=5 + сохранность 5row + boundary) | ✅ (сборка фазы 1) | ⬜ pending |
| 08-01.T2 | 08-01 | 1 | COMPAT-01..05 (вердикты) + boundary + bookkeeping | T1, T3 | Все 5 вердиктов запинованы: insets-линия (fitsSystemWindows v28-сплит, requestApplyInsets, onComputeInsets, contrast off), directBoot (манифест + device-protected prefs без обходов), no-composing (0 вхождений) + commitText/deleteSurroundingText, password (mIsPasswordField + гейт + ноль словаря), ландшафт (5row-фракции); zero-Java boundary; 5 аннотаций + Traceability ×5 + decision | build + grep/find + git diff | verify-команда Task 2 (полный греп-пакет + boundary от d2ae619 + bookkeeping-грепы) | ✅ | ⬜ pending |
| 08-01.T3 | 08-01 | 1 | SC5 (письменная матрица) | T4 | 08-UAT-MATRIX.md существует: 12 окружений × 8 сценариев, легенда, CLOSED-STRUCTURAL со ссылками, DEFERRED self-contained, ни одного PASS до device-прогона | grep (структура документа) | verify-команда Task 3 (наличие окружений/сценариев/статусов + анти-PASS-греп) | ✅ | ⬜ pending |
| 08-01.T4 | 08-01 | 1 | COMPAT-01..05 + Phase SC1–SC5 (on-device) | T4 | Матрица исполнена построчно: ландшафт без extract mode, API 35+ без перекрытия, Direct Boot PIN, WebView/password-ввод, MIUI/One UI при наличии | manual | — (checkpoint:human-verify, стандартная отложенная схема фаз 1–7) | — | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] Рабочая сборка (`./gradlew assembleDebug` / `assembleRelease`) — с фазы 1
- [x] `scripts/check-no-internet.sh` — с фазы 1
- [x] Insets-инфраструктура (layout-v28-сплит, onComputeInsets, requestApplyInsets), directBoot-манифест, PreferenceManagerCompat, no-composing input-путь, password-детект — объекты верификации, не создания (вердикты ресерча: WORKS ×4)
- [x] Ландшафтные ресурсы (values-land 5row-фракции, высоты, xml-land телефонные раскладки) — объект точечной правки одним bool

*Новых Wave 0 зависимостей нет.*

---

## Manual-Only Verifications

On-device UAT (Task 4, отложенная схема при недоступном устройстве — как фазы 1–7). Исполняемый чеклист — **08-UAT-MATRIX.md** (self-contained, все шаги там); краткая карта:

1. **Ландшафт (COMPAT-04):** поворот в Telegram/Chrome → клавиатура сжимается пропорционально, пятый ряд ә ө ү җ ң һ на месте, **extract mode / полноэкранный редактор НЕ появляется** (проверка флипа Task 1 вживую).
2. **Edge-to-edge API 35–36 (COMPAT-03), эмулятор допустим:** gesture-nav и 3-button — нижний ряд не перекрыт, фон под баром = фон клавиатуры, light/dark; исполнимо на эмуляторе раньше остального бандла (A4 плана).
3. **WebView/keyCode 229 (COMPAT-02):** Chrome адресная строка, форма, contenteditable — буквы/backspace-удержание/Enter/long-press/жесты без потерь и дублей.
4. **Password (COMPAT-01):** маска-точки, банковское/PIN-поле — ввод корректен, double-space НЕ ставит точку, никаких подсказок.
5. **Direct Boot (COMPAT-05):** ребут → на экране блокировки PIN вводится нашей клавиатурой ДО первой разблокировки.
6. **MIUI/HyperOS / One UI:** при наличии устройств — «клавиатура не исчезает при наборе», превью не клипаются; иначе честный N/A с оговоркой в матрице.
7. **Регрессии фаз 2–7:** печать, глобус, shift, double-space, свайп-курсор, баллон/панель — без аномалий.

**Почему без автоматики:** extract mode, перекрытие системными панелями, Direct Boot и вендорские killer'ы — свойства реального фреймворка/лончера/вендор-прошивки; instrumented-харнес несоразмерен соло-MVP (решение фаз 2–7 сохраняется). Структурная сторона каждого пункта (ресурсы, манифест, input-путь, insets-цепочка) запинована грепами Task 2, а письменная фиксация SC5 — матрицей Task 3.

---

## Boundary Contract

- База дифа: **d2ae619** (docs-коммит ресерча фазы 8 — последний коммит до кода фазы; последующие docs-коммиты app/ не трогают).
- Разрешённые файлы под `app/`: **только** `res/values-land/config.xml` (1 строка: `config_use_fullscreen_mode` → false).
- Запрещено: любые `.java`/`.kt` (data/docs phase); `layout-v28/input_view.xml`, `layout/input_view.xml`, `LatinIME.java`, `InputLogic.java`, `RichInputConnection.java`, `InputAttributes.java`, манифест (вердикты WORKS — только верификация); sw430/600/768-конфиги и остальные ключи values-land; зависимости; ассеты Apple; PASS в device-ячейках матрицы до UAT.
- Чеки:
  - `[ "$(git diff --name-only d2ae619..HEAD -- app/ | tr -d '[:space:]')" = "app/src/main/res/values-land/config.xml" ]`
  - `[ -z "$(git diff --name-only d2ae619..HEAD -- app/ | grep -E '\.(java|kt)$')" ]`
  - `! grep -rq 'config_use_fullscreen_mode">true' app/src/main/res`
  - `! grep -rq 'setComposingText\|setComposingRegion\|finishComposingText' app/src/main/java`
  - `! grep -qE '\| *PASS *\|' .planning/phases/08-sovmestimost/08-UAT-MATRIX.md` (до device-прогона)
