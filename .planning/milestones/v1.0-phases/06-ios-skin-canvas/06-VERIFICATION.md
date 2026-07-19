---
phase: 06-ios-skin-canvas
verified: 2026-07-18T00:00:00Z
status: passed
previous_status: human_needed
human_verification_deferred: true
deferred_accepted_by: user standing decision (autonomous run 2026-07-18)
score: 9/10 must-haves verified
behavior_unverified: 0
overrides_applied: 0
human_verification:
  - test: "SC1 палитра on-device: светлая #D4D6DD/#FFFFFF/#B3B7C0, радиус ~5dp, резкая 1dp-тень; тёмная #2C2C2C/#6B6B6B/#474747; pressed — обычная темнеет, служебная белеет; caps-lock highlight (M1-фикс, ios_key_checked #A2A6B0 / #9A9A9A) виден в обеих темах"
    expected: "Пиксельное соответствие LOCKED-палитре, тень видна снизу каждой клавиши, caps-lock визуально отличим (особенно light alphabetShiftLocked — рекомендовано ревьюером)"
    why_human: "Пиксельный рендер, HW-acceleration поведение layer-list и видимость highlight — свойства реального устройства; грепом не проверяемо"
  - test: "SC1 смена темы light↔dark при показанной клавиатуре без перезапуска IME (особо Android 12+, риск R3 live-reload)"
    expected: "Палитра меняется без рестарта; если на SDK ≥ S не подхватывается — снять условие < S в KeyboardSwitcher.java:91-93 (1 строка)"
    why_human: "Live-reload конфигурации — runtime-поведение onConfigurationChanged на устройстве"
  - test: "SC4 весь ввод фаз 2–5 поверх нового рендера: пятый ряд ә ө ү җ ң һ видим/нажимаем (гапы не съели ряд, риск R4), глобус tt/ru/en, shift/caps (иконка → залитая/с чертой), long-press дубли, double-space→период, свайп-курсор, двупальцевая печать"
    expected: "Вся механика фаз 2–5 работает идентично, пятый ряд полноценен"
    why_human: "Интерактивное поведение ввода поверх новой темы — только на устройстве"
  - test: "SC3 профайлер: минута непрерывной печати в Android Studio Profiler"
    expected: "Ноль GC-событий, janky-кадры ~0%"
    why_human: "GC/allocation runtime-профилирование — instrumented, не структурный чек (финальный замер — фаза 11)"
  - test: "Smoke-матрица: SC1/SC4 в Telegram, Chrome WebView (keyCode 229), password-поле; MIUI при наличии Xiaomi; TalkBack выключен — только отсутствие touch-регрессий"
    expected: "Без аномалий ввода в проблемных окружениях"
    why_human: "InputConnection-матрица — runtime на реальных приложениях"
---

# Phase 6: iOS-скин — Canvas-отрисовка и темы — Verification Report

**Phase Goal:** Новый рендер: Canvas-отрисовка по iOS-геометрии и палитре, светлая/тёмная тема — при работающем поверх всём вводе фаз 2–5.
**Verified:** 2026-07-18
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | iOS-скин маршрутом (b): тема id=7 «Tatar» дефолтная поверх штатной системы тем; рендер не переписан, onDraw не оверрайжен, final не снят | ✓ VERIFIED | `THEME_ID_TATAR = 7` + `DEFAULT_THEME_ID = THEME_ID_TATAR` (KeyboardTheme.java:41-42); Tatar добавлен в KEYBOARD_THEMES, 6 старых тем на местах (:47-52); `public final class MainKeyboardView` сохранён; 0 добавленных `void onDraw(` в диффе keyboard/ |
| 2 | LOCKED-палитра ресурсами ios_* в values/ и values-night/; dark через штатный values-night; ноль цветовых хардкодов в Java | ✓ VERIFIED | light #D4D6DD/#FFFFFF/#B3B7C0/#40000000 + ios_key_checked #A2A6B0 (colors.xml:57-70); night #2C2C2C/#6B6B6B/#474747/#B3000000 + #9A9A9A (values-night:34-47); полная name-parity ios_* между квалификаторами (diff пуст) |
| 3 | Фон клавиши = selector→layer-list: roundRect 5dp + 1dp-смещённая тень; setShadowLayer не используется; pressed state-list'ом | ✓ VERIFIED | 3 drawable: layer-list, radius 5dp, top=1dp тень / bottom=1dp клавиша, state_pressed; normal+functional имеют state_checkable+state_checked (M1-фикс); 0 добавленных setShadowLayer в диффе |
| 4 | Иконки shift/shift_locked/globe — собственные path'ы, тонировка ?attr/functionalTextColor сохранена, KeyboardIconsSet нетронут; ни одного ассета Apple | ✓ VERIFIED | 3 sym_* изменены в диффе; delete.xml нетронут; KeyboardIconsSet 0 изменений; functionalTextColor в shift(1)/globe(3)/shift_locked(3 — M1 fix заменил hardcoded white); 0 SF/Apple строк в res-диффе; ревью подтвердило оригинальную геометрию |
| 5 | 3 PERF-фикса в коде: индексный цикл, устранён per-key клон KeyDrawParams, кэш строки пробела; #4–#6 не тронуты | ✓ VERIFIED | старый iterator-цикл отсутствует, индексный `for (int i...) sortedKeys.get(i)` (KeyboardView:287-288); `HashMap<Key,KeyDrawParams> mKeyDrawParamsCache` (:107) + clear() в setKeyboard (:178); mLanguageOnSpacebarText+TextScaleX кэш (MainKeyboardView:77-78), инвалидация setKeyboard/startDisplayLanguageOnSpacebar; drawLanguageOnSpacebar 0 вызовов LocaleResourceUtils |
| 6 | A11y-каркас: KeyboardAccessibilityDelegate.kt (ExploreByTouchHelper) + hover-wiring; touch-путь не изменён | ✓ VERIFIED | .kt существует, extends ExploreByTouchHelper; MainKeyboardView: field (:112), ctor init после mKeyDetector (:184), dispatchHoverEvent override (:189-191), invalidateRoot в setKeyboard (:281); PointerTracker/onTouchEvent не тронуты |
| 7 | APK HARD GATE: release ≤ 3 МБ; dependencies = ровно androidx.customview:customview | ✓ VERIFIED | release APK 700 963 байт ≤ 3 145 728 (22% бюджета); ровно 1 implementation-строка `androidx.customview:customview:1.1.0`; useAndroidX=true записан как deviation |
| 8 | assembleDebug + assembleRelease зелёные; check-no-internet exit 0; диф ⊆ объявленные файлы | ✓ VERIFIED | обе сборки exit 0; check-no-internet Level 1+2 OK, только VIBRATE; boundary e0d470b..HEAD по app/ ⊆ files_modified (+M1 fix files + gradle.properties deviation + .planning) |
| 9 | REQUIREMENTS.md: аннотация UI-01, чек-бокс не проставлен, Traceability = Verifying; STATE.md: decision [06-01] | ✓ VERIFIED | UI-01 `- [ ]` unchecked + аннотация маршрута (b) (REQUIREMENTS:43-44); Traceability `Verifying (06-01: structural PASS...)` (:138); STATE [06-01] decision + [Phase 6] UAT checklist present |
| 10 | On-device: палитра light/dark, смена темы без перезапуска, ввод фаз 2–5 вкл. пятый ряд, ноль GC, smoke-матрица | ⚠️ human-needed | Устройство недоступно (adb пуст); отложено в STATE.md Blockers [Phase 6] по standing-схеме фаз 1–5 — принято пользователем |

**Score:** 9/10 truths verified (1 deferred to on-device UAT — accepted standing pattern)

### Prohibitions

| # | Prohibition (MUST NOT) | Status | Evidence |
|---|------------------------|--------|----------|
| 1 | Ассеты Apple (SF Symbols path, SF Pro, звуки) | ✓ upheld | 0 SF/Apple строк в res-диффе; Roboto нетронут; code review подтвердил hand-authored геометрию (не трейс SF Symbols). Юридическая оценка — judgment-tier; ревью вынесло «legal posture fine» — окончательный человеческий глазок при UAT рекомендован ревьюером |
| 2 | Переписка рендера / onDraw-оверрайд / снятие final / setShadowLayer / LAYER_TYPE_SOFTWARE / Compose | ✓ upheld | final class сохранён; 0 onDraw-override в диффе; 0 добавленных setShadowLayer; androidx.compose отсутствует в src |
| 3 | Зависимости сверх androidx.customview; INTERNET/аналитика; правки PointerTracker/InputLogic/LatinIME/механики/6 тем/btn_keyboard_key.xml | ✓ upheld | ровно 1 implementation; check-no-internet green; 0 файлов PointerTracker/InputLogic/LatinIME/KeyboardSwitcher тронуто; 6 тем + btn_keyboard_key.xml нетронуты |

### Key Link Verification

| From | To | Via | Status |
|------|----|----|--------|
| KeyboardTheme id=7 | ios_key_* drawables | themes-tatar.xml keyBackground/functionalKeyBackground/spacebarBackground → @drawable/ios_key_normal/functional/spacebar | ✓ WIRED |
| values-night/ios_* | dark render | штатный values-night + onConfigurationChanged (механика форка, не тронута) | ✓ WIRED (structural) |
| Key.selectBackgroundDrawable | layer-list | selector state → layer-list (тень top=1dp + ключ bottom=1dp, corners 5dp) | ✓ WIRED |
| Settings theme dialog | id=7 | keyboard-themes.xml array item `7` + strings «Tatar» + attrs enum Tatar=7 | ✓ WIRED |
| MainKeyboardView.dispatchHoverEvent | KeyboardAccessibilityDelegate | delegate.dispatchHoverEvent(e) \|\| super | ✓ WIRED |

### Requirements Coverage

| Requirement | Source Plan | Status | Evidence |
|-------------|-------------|--------|----------|
| UI-01 | 06-01 | ⚠️ Verifying (structural PASS; UAT deferred) | Тема/палитра/геометрия/иконки/PERF/a11y-каркас структурно верифицированы; SC1 визуал / SC3 GC / SC4 ввод — on-device UAT (Task 7) |

### Anti-Patterns Found

None. No debt markers (TBD/FIXME/XXX) in phase files. gradle.properties useAndroidX flip documented as required deviation. Code review m1–m5 minors explicitly deferred with reasons (pre-existing upstream / phase-9 scope / within budget); M1 major fixed (06-REVIEW-FIX.md, commit b7cd67d).

### Human Verification Required

On-device UAT (Task 7) deferred by standing pattern (phases 1–5): device unavailable (adb empty), accepted by user, recorded in STATE.md Blockers [Phase 6]. Five items — see frontmatter `human_verification`: SC1 palette light/dark + caps-lock highlight, theme switch without restart (R3), full phase 2–5 input over new render (R4), profiler GC-free, InputConnection smoke matrix.

### Gaps Summary

No structural gaps. All 9 mechanically-verifiable truths PASS: theme id=7 registered and default with 6 themes intact, palette exact in both qualifiers with full ios_* parity, 3 layer-list drawables with 5dp/1dp-shadow + M1 state_checked fix, icons redrawn with functionalTextColor tint and no Apple assets, 3 PERF fixes in code, a11y skeleton wired with touch path untouched, release APK 700 963 B ≤ 3 MB with sole androidx.customview dependency, both builds + no-internet green, boundary clean, bookkeeping correct. The single remaining truth (on-device visual/runtime UAT) is deferred and accepted per the established phases 1–5 pattern → status human_needed.

---

_Verified: 2026-07-18_
_Verifier: Claude (gsd-verifier)_
