---
phase: 07-ios-skin-preview-panel
plan: 01
subsystem: ui
tags: [ime, key-preview, more-keys-panel, haptics, ios-skin, drawable, layer-list]

# Dependency graph
requires:
  - phase: 06-ios-skin-core
    provides: "тема id=7 «Tatar», палитра ios_* light/night, drawable-семейство layer-list 5dp + 1dp-тень"
provides:
  - "iOS-баллон превью: ios_key_preview_background.xml (layer-list roundRect ios_key_normal + 1dp-тень ios_key_shadow, радиус 5dp)"
  - "Фон панели альтернатив: ios_popup_panel_background.xml (радиус 5dp, padding 5dp сохранён)"
  - "Wiring в themes-tatar.xml: keyPreviewBackground + android:background (MoreKeysKeyboardView.Tatar)"
  - "Структурное доказательство UI-02/03/04: in-layer превью/панель (ноль PopupWindow), slide-to-select, отклик на ACTION_DOWN — запиновано fail-capable-грепами"
affects: [08-compat, 10-settings, 11-perf-release, uat]

# Tech tracking
tech-stack:
  added: []
  patterns: ["drawable-семейство фазы 6: layer-list из двух roundRect (тень 1dp offset), радиус 5dp, цвета только @color/ios_* с night-паритетом"]

key-files:
  created:
    - app/src/main/res/drawable/ios_key_preview_background.xml
    - app/src/main/res/drawable/ios_popup_panel_background.xml
  modified:
    - app/src/main/res/values/themes-tatar.xml
    - .planning/REQUIREMENTS.md
    - .planning/STATE.md

key-decisions:
  - "Радиус баллона превью = 5dp (нижняя граница допуска A2 5–8dp): size 45×5dp + padding bottom 60dp старого feedback-drawable зеркалированы, чтобы не сдвигать геометрию превью — при таком силуэте радиус семейства консистентнее крупного"
  - "Тень панели альтернатив НЕ добавлена (A4): панель клампится у краёв экрана, 1dp-offset-слой рисковал бы асимметрией у краёв; минимальный roundRect+padding"
  - "Dimens-подстройка геометрии превью не понадобилась (A3): config_key_preview_{offset,height,width}_lxx оставлены как есть"
  - "config_default_sound_enabled остаётся false — решение пользователя против рекомендации ресерча, запиновано грепом"

patterns-established:
  - "Verification pinning: вердикты ресерча фиксируются fail-capable-грепами в verify задач — регрессия ломает сборку плана"

requirements-completed: [UI-02, UI-03, UI-04]

# Coverage metadata (#1602)
coverage:
  - id: D1
    description: "iOS-баллон превью (drawable + wiring темы Tatar), общие drawable шести старых тем нетронуты"
    requirement: UI-02
    verification:
      - kind: other
        ref: "./gradlew assembleDebug && grep ios_key_preview_background themes-tatar.xml && git diff --name-only fbfd66a..HEAD -- <общие drawable> == пусто"
        status: pass
    human_judgment: false
  - id: D2
    description: "Фон панели альтернатив ios_popup_panel_background (радиус 5dp, padding 5dp) подключён к MoreKeysKeyboardView.Tatar"
    requirement: UI-03
    verification:
      - kind: other
        ref: "./gradlew assembleDebug && grep ios_popup_panel_background themes-tatar.xml"
        status: pass
    human_judgment: false
  - id: D3
    description: "Структурная верификация вердиктов ресерча: in-layer превью/панель (ноль PopupWindow), slide-handoff, down-цепочка отклика, EFFECT_CLICK/KEYBOARD_TAP, prefs wired, sound default false, zero-Java boundary, bookkeeping"
    requirement: UI-04
    verification:
      - kind: other
        ref: "verify-команда Task 2 (полный греп-пакет + boundary fbfd66a..HEAD + bookkeeping-грепы) — exit 0"
        status: pass
    human_judgment: false
  - id: D4
    description: "On-device UAT: мгновенность баллона на down, отсутствие обрезки (пятый ряд/края/MIUI), slide-ощущение, хаптика/подсветка на касании, звук default off, smoke-матрица"
    verification: []
    human_judgment: true
    rationale: "Латентность кадра, пиксельная обрезка, тактильная хаптика — свойства рендер-пайплайна и вибромотора реального устройства; adb devices пуст — отложено в STATE.md Blockers по standing-схеме фаз 1–6"

# Metrics
duration: 25min
completed: 2026-07-18
status: complete
---

# Phase 7 Plan 01: iOS-скин — превью, панель, отклик Summary

**iOS-баллон превью и панель альтернатив чистым XML (2 drawable + 2 item темы, ноль Java); in-layer архитектура и отклик на ACTION_DOWN подтверждены построчно и запинованы fail-capable-грепами — перенос с PopupWindow не понадобился, его в форке нет вообще**

## Performance

- **Duration:** 25 min
- **Started:** 2026-07-18T17:59:53Z
- **Completed:** 2026-07-18T18:25:00Z
- **Tasks:** 2 of 3 (Task 3 on-device UAT deferred)
- **Files modified:** 3 app files + REQUIREMENTS.md, STATE.md, ROADMAP.md

## Accomplishments

- **UI-02 (стилизация):** `ios_key_preview_background.xml` — layer-list по конвенции фазы 6: слой-тень `ios_key_shadow` (offset 1dp вниз), слой-баллон `ios_key_normal` (#FFF light / #6B6B6B dark — night бесплатно через values-night), радиус 5dp; size 45×5dp и padding bottom 60dp зеркалят старый `keyboard_key_feedback_background`, чтобы позиционирование текста превью не сдвинулось.
- **UI-03 (косметика):** `ios_popup_panel_background.xml` — roundRect `?attr/popupPanelBackgroundColor` (= `ios_keyboard_background_secondary` в теме Tatar), радиус 5dp вместо 6dp `button_corner_radius_lxx`, padding 5dp сохранён (инсеты клавиш панели).
- **Wiring:** `themes-tatar.xml` — ровно два item: `MainKeyboardView.Tatar.keyPreviewBackground` и `MoreKeysKeyboardView.Tatar.android:background`. `keyPreviewTextColor` уже был iOS (фаза 6), не тронут.
- **UI-02/03/04 (верификация):** все вердикты 07-RESEARCH.md запинованы fail-capable-грепами (11 чеков PASS): ноль PopupWindow в исходниках, placer в `android.R.id.content` окна IME, `panel.showInParent(mDrawingPreviewPlacerView)`, slide-handoff `moreKeysPanel.onDownEvent(translatedX...)`, down-цепочка (press-callback + graphics в `onDownEventInternal`), `onPressKey → hapticAndAudioFeedback`, EFFECT_CLICK + KEYBOARD_TAP, prefs `vibrate_on`/`sound_on` wired, `config_default_sound_enabled=false` не флипнут.
- **Boundary (zero-Java):** дифф `fbfd66a..HEAD` по `app/` = ровно 3 объявленных XML; `.java`/`.kt` в диффе — ноль; общие drawable (`keyboard_key_feedback_background`, `keyboard_popup_panel_background`, `btn_keyboard_key_popup`) и 6 старых тем нетронуты; новых цветов не заводилось (parity-чек тривиально зелёный).
- **Сборки:** assembleDebug + assembleRelease зелёные; `scripts/check-no-internet.sh` exit 0.

## Task Commits

Each task was committed atomically:

1. **Task 1: iOS-баллон превью + косметика панели** - `6edb48e` (feat)
2. **Task 2: Структурная верификация + bookkeeping** - `6b355ce` (docs)
3. **Task 3: On-device UAT** - deferred (см. ниже), запись в STATE.md Blockers — в plan-metadata-коммите

## Files Created/Modified

- `app/src/main/res/drawable/ios_key_preview_background.xml` - iOS-баллон превью (layer-list, тень 1dp, радиус 5dp)
- `app/src/main/res/drawable/ios_popup_panel_background.xml` - фон панели альтернатив (радиус 5dp, padding 5dp)
- `app/src/main/res/values/themes-tatar.xml` - 2 item-замены wiring
- `.planning/REQUIREMENTS.md` - аннотации UI-02/UI-04, Traceability ×3 → Verifying
- `.planning/STATE.md` - decision [07-01], Blocker Phase 7 UAT
- `.planning/ROADMAP.md` - Progress Phase 7 → complete-local

## Decisions Made

- **Радиус баллона 5dp** (допуск A2 разрешал 5–8dp): выбран нижний край — силуэт баллона задаётся зеркалированной геометрией старого drawable (size 45×5dp + padding bottom 60dp), при ней радиус семейства фазы 6 выглядит консистентнее; крупный радиус потребовал бы менять и геометрию (A3), что не понадобилось.
- **Тень панели не добавлена** (A4): панель клампится у краёв экрана; минимальный roundRect надёжнее.
- **Dimens-подстройка не понадобилась** (A3): геометрия превью (`config_key_preview_{offset,height,width}_lxx`) сохранена — silуэт drawable зеркалирует старый, сдвигов нет.
- **Sound default остаётся false** — решение пользователя (как в Gboard), против рекомендации ресерча; запиновано грепом, тумблер UI — фаза 10.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## Authentication Gates

None.

## User Setup Required

None - no external service configuration required.

## Deferred Verification

**Task 3 (checkpoint:human-verify) — on-device UAT deferred:** `adb devices` пуст, устройство недоступно. По standing-схеме фаз 1–6 чекпойнт отложен в STATE.md Blockers (запись ⚠️ [Phase 7, plan 07-01]) с полным чек-листом: (1) установка свежего app-debug.apk; (2) SC1: баллон мгновенно на касании, iOS-вид light/dark, исчезает при отпускании; (3) SC1/SC4: баллон пятого ряда (ә) и крайних колонок (й, ъ/э/һ) не обрезан, MIUI особо; (4) SC2: long-press → панель, slide-select без отрыва, отмена уходом, iOS-палитра; (5) SC3: вибрация+подсветка на касании, звук default off → появляется при включении sound_on, оба pref отключаемы; (6) SC4 smoke: Telegram, Chrome WebView (keyCode 229), password; (7) регрессии фаз 2–6. Финальная простановка чек-боксов UI-02/03/04 в REQUIREMENTS.md — после UAT.

## Self-Check: PASSED

- Оба новых drawable существуют на диске (`[ -f ]` PASS)
- `git log --grep="07-01"` → 2 коммита (6edb48e, 6b355ce)
- Acceptance criteria Task 1: все PASS (verify-команда exit 0)
- Acceptance criteria Task 2: все PASS (verify-команда verbatim exit 0)
- Plan-level verification 1–6: PASS; п.7 — checkpoint честно отложен (STATE.md Blockers)

## Next Phase Readiness

- Phase 7 complete-local: вся кодовая работа iOS-скина превью/панели/отклика сделана и структурно доказана; UAT-хвост фаз 1–7 объединён в STATE.md Blockers — прогнать одним заходом при появлении устройства.
- Ready: Phase 8 — Совместимость (полный проход матрицы InputConnection: COMPAT-01..05).

---
*Phase: 07-ios-skin-preview-panel*
*Completed: 2026-07-18*
