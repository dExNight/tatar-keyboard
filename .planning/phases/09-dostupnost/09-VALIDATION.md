---
phase: 9
slug: dostupnost
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
status: verified (mechanical) — on-device items deferred
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-18
---

# Phase 9 — Validation Strategy

> Per-phase validation contract. Фаза достраивает a11y-каркас фазы 6 до полной A11Y-01/02: 4 гэпа (G1 ACTION_CLICK→false, G2 сырые описания, G3 нет isClickable/isTextEntryKey, G4 нет TYPE_VIEW_CLICKED). Автоматика = сборка debug+release + APK-гейт + fail-capable-грепы (маппер/клик/узел/ресурсы/password) + zero-fork-Java boundary-diff от 0a280ce; реальное TalkBack-поведение (озвучка, набор слова — SC3; недеградация обычного ввода — SC4) — только on-device.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Gradle build (debug + release) + grep/find + git diff — решение фаз 2–8 сохраняется: юнит-харнеса нет; TalkBack-поведение доказывается только устройством (эмулятор с TalkBack допустим, но lift-to-type/TTS-нюансы — реальное устройство) |
| **Config file** | none — Wave 0 покрыт инфраструктурой фазы 1 (`scripts/check-no-internet.sh`) и фазы 6 (androidx.customview + транзитив androidx.core 1.3.0 с setTextEntryKey — verified javap) |
| **Quick run command** | `./gradlew assembleDebug` |
| **Full suite command** | `./gradlew assembleDebug assembleRelease && bash scripts/check-no-internet.sh` |
| **Estimated runtime** | ~90–180 seconds |

---

## Sampling Rate

- **After every task commit:** `./gradlew assembleDebug`
- **After Task 1:** греп-пакет ресурсов (26 имён в base И ru, «татарская э», «Заглавная %s») + маппер (6 hex-кодпоинтов, isUpperCase, elementId/imeAction, ноль reflection/iconName)
- **After Task 2:** греп-пакет делегата (KeyDescriptionMapper в populate, ноль getIconName/KeyboardIconsSet, processMotionEvent + DOWN/UP + TYPE_VIEW_CLICKED + return true, isClickable/isTextEntryKey, stale-ветка жива) + анти-.java boundary
- **After Task 3:** full suite + APK-гейт ≤ 3 МБ + полный boundary (счётчик =4, пофайловые вхождения, touch-путь diff пуст) + bookkeeping-грепы
- **Before `/gsd-verify-work`:** full suite green + все грепы Task 1–3
- **Max feedback latency:** 180 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 09-01.T1 | 09-01 | 1 | A11Y-02 (строки + маппер) | T3 | 26 spoken_*-имён в values/strings-a11y.xml И values-ru/strings-a11y.xml; маппер: 6 татарских кодпоинтов hex-литералами, заглавные через isUpperCase→toLowerCase→шаблон «Заглавная %s», shift по mElementId (без ELEMENT 4), enter по imeAction() с приоритетом label; ноль reflection (getIdentifier) и ноль iconName-детекта; values-ru/strings.xml нетронут | build + grep | verify-команда Task 1 (циклы по 26 именам ×2 файла + 6 кодпоинтов + анти-грепы) | ✅ (сборка фазы 1) | ⬜ pending |
| 09-01.T2 | 09-01 | 1 | A11Y-01 (G1/G2/G3/G4 в делегате) | T1, T2, T3 | populate — только KeyDescriptionMapper (ноль getIconName/KeyboardIconsSet); node.isClickable + node.isTextEntryKey; ACTION_CLICK → MotionEvent DOWN/UP (видимый центр + padding) → processMotionEvent → TYPE_VIEW_CLICKED → return true; спейсер/чужой action → false; stale-ветка и нумерация id сохранены; ни одного .java в диффе | build + grep + git diff | verify-команда Task 2 (греп-пакет делегата + анти-.java) | ✅ | ⬜ pending |
| 09-01.T3 | 09-01 | 1 | A11Y-01/02 (пиновка) + boundary + bookkeeping | T1–T5 | Обе сборки + no-internet + APK ≤ 3 МБ; все линии запинованы (маппер/клик/узел/ресурсы/ноль announceForAccessibility); дифф 0a280ce..HEAD по app/ = ровно 4 файла (2 .kt + 2 .xml), ноль .java, touch-путь (PointerTracker/KeyboardState/MainKeyboardView/Key) diff пуст; 2 аннотации + Traceability ×2 Verifying + decision [09-01] + 2 backlog-записи | build + grep/find + git diff | verify-команда Task 3 (полный пакет) | ✅ | ⬜ pending |
| 09-01.T4 | 09-01 | 1 | A11Y-01/02 + Phase SC1–SC4 (on-device) | T1, T2 | TalkBack называет каждую клавишу человеческим описанием (вкл. «татарская э» для ә, «Заглавная татарская э» на shift); двойной тап/lift-to-type печатает; слово набрано и отправлено в мессенджере (SC3); без TalkBack обычный ввод не деградировал — Telegram + WebView смоук (SC4) | manual | — (checkpoint:human-verify, стандартная отложенная схема фаз 1–8) | — | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] Рабочая сборка (`./gradlew assembleDebug` / `assembleRelease`) — с фазы 1
- [x] `scripts/check-no-internet.sh` — с фазы 1
- [x] ExploreByTouchHelper-каркас (wiring, enumeration, hit-testing, bounds, invalidateRoot) — фаза 6, объект достройки, не создания
- [x] androidx.customview:1.1.0 + транзитив androidx.core:1.3.0 (`setTextEntryKey` — verified javap) — фаза 6, НОВЫХ зависимостей не требуется
- [x] `MainKeyboardView.processMotionEvent` public (:503) — вызывается делегатом, не модифицируется

*Новых Wave 0 зависимостей нет.*

---

## Manual-Only Verifications

On-device TalkBack UAT (Task 4, отложенная схема при недоступном устройстве — как фазы 1–8). Полные шаги — Task 4 плана; краткая карта:

1. **Explore-by-touch (SC1/SC2):** палец по всем рядам — каждая клавиша озвучена человеческим описанием; пятый ряд: «татарская э/о/у/ж/н/х»; shift-раскладка: «Заглавная татарская э»; служебные: «Клавиша верхнего регистра»/«Верхний регистр включён»/«Caps Lock включён», «Удалить», «Пробел», «Отправить» (Telegram), «Символы»/«Буквы», «Сменить язык». Ни одного сырого «shift_key».
2. **Набор слова (SC3 — главный):** двойной тап (и lift-to-type — A2) в Telegram: «әни» набрано и отправлено; earcon клика слышен; русская раскладка тоже.
3. **Динамика:** shift/смена раскладки → описания обновляются (invalidateRoot-трасса).
4. **Password:** клавиши озвучены по именам (не обскьюрены — осознанно), IME сам набранное не произносит.
5. **SC4 — без TalkBack:** обычная печать, backspace-удержание, double-space, свайп-курсор, long-press панель, баллон — Telegram + Chrome/WebView, без деградации.

**Почему без автоматики:** TalkBack-поведение (маршрутизация ACTION_CLICK, lift-to-type-эвристика, TTS-произношение татарской кириллицы) — свойства реального сервиса/движка TTS; instrumented-a11y-харнес несоразмерен соло-MVP (решение фаз 2–8 сохраняется). Структурная сторона каждого пункта (описания-маппер, синтез клика, свойства узла, ресурсы обеих локалей) запинована грепами Task 3. Assumptions под device-риском: A1 (ru-TTS кириллица), A2 (lift-to-type) — оба с безопасной деградацией, SC3 не блокируют.

---

## Boundary Contract

- База дифа: **0a280ce** (docs-коммит ресерча фазы 9 — последний коммит до кода фазы; последующие docs-коммиты app/ не трогают — A4 плана).
- Разрешённые файлы под `app/` (ровно 4, из них 3 НОВЫХ):
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/accessibility/KeyDescriptionMapper.kt` (НОВЫЙ)
  - `app/src/main/java/rkr/simplekeyboard/inputmethod/accessibility/KeyboardAccessibilityDelegate.kt` (правка ~30 строк)
  - `app/src/main/res/values/strings-a11y.xml` (НОВЫЙ)
  - `app/src/main/res/values-ru/strings-a11y.xml` (НОВЫЙ)
- Запрещено: любые `.java` (zero-fork-Java — заявление фазы, доказывается механически); `PointerTracker.java`, `KeyboardState.java`, `MainKeyboardView.java`, `Key.java` (touch-путь — SC4 by construction); `values-ru/strings.xml`; геттеры mHitbox/mKeyboardActionListener; MoreKeysKeyboardView-делегат; build.gradle-зависимости; манифест; values-tt; announceForAccessibility; чек-боксы A11Y до UAT.
- Чеки:
  - `[ "$(git diff --name-only 0a280ce..HEAD -- app/ | wc -l | tr -d ' ')" = "4" ]` + пофайловые греп-вхождения всех 4 путей
  - `[ -z "$(git diff --name-only 0a280ce..HEAD -- app/ | grep '\.java$')" ]`
  - `[ -z "$(git diff 0a280ce..HEAD -- '*PointerTracker.java' '*KeyboardState.java' '*MainKeyboardView.java' '*Key.java')" ]`
  - `! grep -rq 'announceForAccessibility' app/src/main/java`
  - `! grep -q 'getIconName\|KeyboardIconsSet' app/src/main/java/rkr/simplekeyboard/inputmethod/accessibility/KeyboardAccessibilityDelegate.kt`
