---
phase: 09-dostupnost
verified: 2026-07-18
status: passed
human_verification_deferred: true
deferred_accepted_by: user standing decision (autonomous run 2026-07-18)
score: 12/12 must-haves verified (structural); 1 human checkpoint accepted-deferred per standing pattern
re_verification: false
human_verification:
  - test: "On-device TalkBack UAT (Task 4): SC1/SC2 explore-by-touch озвучка всех клавиш («татарская э» для ә, «Заглавная татарская э» на shift), SC3 набор и отправка «әни» в Telegram двойным тапом/lift-to-type, динамика shift/раскладки, password-поведение, SC4 не-TalkBack смоук (Telegram + Chrome/WebView)"
    expected: "Каждая клавиша озвучена человеческим описанием, слово набрано и отправлено с TalkBack, обычный ввод без регрессий"
    status: accepted-deferred
    reason: "Устройство недоступно (adb devices пуст) — standing-паттерн фаз 1–8, принят пользователем; self-contained чек-лист в STATE.md Blockers (строка Phase 9, plan 09-01), присоединён к UAT-бандлу фаз 1–9. Также в UAT-скрипт включён L2 из ревью (озвучка регистра Ә vs А)"
gaps: []
---

# Phase 9: Доступность — Verification Report

**Phase Goal:** TalkBack-пользователь может печатать на татарской клавиатуре.
**Verified:** 2026-07-18 (HEAD 82a52fb)
**Status:** PASSED — все структурные must-haves доказаны механически; device-UAT accepted-deferred по standing-паттерну фаз 1–8.

## Observable Truths (ROADMAP SC × plan must_haves)

| # | Truth | Status | Evidence (live) |
|---|-------|--------|-----------------|
| 1 | SC1: каждая клавиша — виртуальный узел, описания человеческие (A11Y-01) | ✓ structural | `KeyboardAccessibilityDelegate.kt`: populate → `KeyDescriptionMapper.getDescription` (:75-76); enumeration по sortedKeys без спейсеров (:52-59); stale-id ветка сохранена (:68-74) |
| 2 | SC2: татарские буквы описаны «татарская э/о/у/ж/н/х» (A11Y-02) | ✓ | Mapper: 6 hex-кодпоинтов U+04D9/04E9/04AF/0497/04A3/04BB (:42-49); values-ru: «татарская э…х» (:24-29); заглавные — isUpperCase→toLowerCase→«Заглавная %s» (:55-60) |
| 3 | SC3: клик = реальный ввод (G1) | ✓ structural, device deferred | ACTION_CLICK → MotionEvent DOWN/UP в видимый центр + padding → `processMotionEvent` (public, MainKeyboardView.java:503) → recycle → TYPE_VIEW_CLICKED → `return true` (:99-112); спейсер/чужой action → false |
| 4 | SC4: обычный ввод не деградировал — by construction | ✓ mechanical | diff 0a280ce..HEAD -- app/ = ровно 4 файла (2 .kt + 2 .xml), ноль .java; diff PointerTracker/KeyboardState/MainKeyboardView/Key пуст |
| 5 | G2: ноль getIconName/KeyboardIconsSet в делегате | ✓ | анти-греп чист; описания только через маппер |
| 6 | G3: isClickable + isTextEntryKey на узле | ✓ | Delegate :85, :91; L1-комментарий с lift-to-type-обоснованием (:86-90) |
| 7 | G4: TYPE_VIEW_CLICKED после успешного клика | ✓ | Delegate :111, только после обоих гардов |
| 8 | Строки данными, en/ru паритет | ✓ | 27/27 имён (26 + spoken_description_tab из M1-фикса), name-diff пуст; единственный плейсхолдер — одиночный `%s` в upper_case (валиден) |
| 9 | Детект строго по коду/elementId/imeAction | ✓ | when по key.code; shift ×4 по mElementId (ELEMENT 4 ветки нет — констант 0-3,5,6 подтверждено в KeyboardId.java:46-51); enter ×7 по imeAction() с приоритетом custom label |
| 10 | Password: ноль собственных announce | ✓ | `grep -r announceForAccessibility app/src/main/java` = 0 |
| 11 | Сборки/приватность/APK-гейт | ✓ | assembleDebug + assembleRelease BUILD SUCCESSFUL; check-no-internet.sh level 1+2 OK (только VIBRATE); release-APK 719 267 ≤ 3 145 728 |
| 12 | Bookkeeping | ✓ | REQUIREMENTS.md: чек-боксы A11Y-01/02 пусты (:54, :57), Traceability ×2 = «Verifying (09-01: structural PASS; on-device UAT deferred)» (:157-158); STATE.md: decision [09-01] (:64), backlog moreKeys-a11y (:79) + values-tt/announce (:80), Blockers self-contained чек-лист фазы 9 (:98) |

## Required Artifacts

| Artifact | Status | Notes |
|----------|--------|-------|
| `accessibility/KeyDescriptionMapper.kt` | ✓ substantive | Kotlin object, чистая функция, when по коду, без reflection/getIdentifier; CODE_TAB ветка (M1) на :75 |
| `accessibility/KeyboardAccessibilityDelegate.kt` | ✓ substantive | Полный ExploreByTouchHelper; KDoc без оговорки «phase 9» |
| `values/strings-a11y.xml` | ✓ | en base, 27 spoken_*-строк |
| `values-ru/strings-a11y.xml` | ✓ | ru-оверлей, 27/27 паритет |

## Key Links

- Клик: ACTION_CLICK → MotionEvent.obtain(t, t, DOWN/UP, center+padding, 0) → `keyboardView.processMotionEvent` → штатный PointerTracker-путь — verified (Delegate :106-110 ↔ MainKeyboardView.java:503).
- Описания: populate → getDescription(context, keyboard, key) → TATAR_LETTERS/when → R.string.spoken_* — verified.
- Динамика: setKeyboard → invalidateRoot (wiring фазы 6, до 0a280ce — вне диффа фазы, подтверждено grep MainKeyboardView.java:184).

## Prohibitions — all held

1. Zero-fork-Java: ноль .java в диффе фазы ✓ (механический diff-чек).
2. Touch-путь/нумерация id/stale-ветка не тронуты ✓.
3. Нет новых зависимостей/INTERNET/announce/Apple-ассетов ✓ (check-no-internet green; манифест и build.gradle вне диффа).
4. MVP-границы: moreKeys-a11y/values-tt/announce shiftmode/password-obscure не делались ✓ (backlog в STATE.md).

## Review Debt — resolved

- 09-REVIEW.md: ✅ Approve (1 medium, 2 low, 4 info, no blockers).
- **M1** (CODE_TAB → «Unknown»): исправлен (ea03764) — ветка в маппере + en «Tab»/ru «Табуляция», паритет 27/27 — verified live.
- **L1** (isTextEntryKey rationale): 5-строчный комментарий в делегате — verified live (:86-90).
- **L2** (заглавные не-татарские без префикса) + **I3** (moreKeys вне a11y-дерева) + **I4** (PointerTracker deltaT fork-bug): корректно в backlog/UAT-скрипте — не блокеры.

## Anti-Pattern Scan

Ноль TODO/FIXME/placeholder/stub во всех 4 файлах фазы.

## Verdict

Цель фазы «TalkBack-пользователь может печатать на татарской клавиатуре» достигнута структурно: полная цепочка (узел → человеческое описание → клик → штатный touch-путь → ввод) существует, запинована fail-capable-грепами и доказана механическим boundary-чеком; финальное end-to-end подтверждение на устройстве честно отложено в UAT-бандл фаз 1–9 по принятому standing-паттерну. **PASSED.**
