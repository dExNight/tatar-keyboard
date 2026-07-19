---
phase: 06-ios-skin-canvas
plan: 01
subsystem: ui
tags: [android, ime, canvas, theme, layer-list, vectordrawable, explorebytouchhelper, androidx-customview, kotlin]

requires:
  - phase: 05-input-mechanics
    provides: стабильная механика ввода фаз 2–5 (раскладки, слои, shift, жесты), поверх которой рисуется скин
provides:
  - Тема id=7 «Tatar» (дефолтная) — iOS-палитра light/night ресурсами ios_*, стили themes-tatar.xml поверх штатной системы тем форка
  - 3 drawable ios_key_normal/functional/spacebar — selector→layer-list, roundRect 5dp + 1dp-тень + pressed
  - Собственные VectorDrawable-иконки shift / shift_locked / globe (тонировка ?attr/functionalTextColor сохранена)
  - 3 PERF-фикса аллокаций горячего пути onDraw (записаны в код; замер — фаза 11)
  - A11y-каркас KeyboardAccessibilityDelegate.kt (ExploreByTouchHelper) + hover-wiring в MainKeyboardView
affects: [07-ios-skin-feedback, 09-accessibility, 11-performance]

tech-stack:
  added: ["androidx.customview:customview:1.1.0 (первая внешняя зависимость; транзитивы annotation 1.1.0 / core 1.3.0 / collection 1.1.0)", "Kotlin-файл №1 проекта (interop-конвенция)"]
  patterns: ["вид — данными: тема+drawable без правок рендера", "layer-list-тень вместо setShadowLayer", "per-key кэш KeyDrawParams с инвалидацией в setKeyboard"]

key-files:
  created:
    - app/src/main/res/values/themes-tatar.xml
    - app/src/main/res/drawable/ios_key_normal.xml
    - app/src/main/res/drawable/ios_key_functional.xml
    - app/src/main/res/drawable/ios_key_spacebar.xml
    - app/src/main/java/rkr/simplekeyboard/inputmethod/accessibility/KeyboardAccessibilityDelegate.kt
  modified:
    - app/src/main/res/values/colors.xml
    - app/src/main/res/values-night/colors.xml
    - app/src/main/res/values/keyboard-themes.xml
    - app/src/main/res/values/strings.xml
    - app/src/main/res/values/attrs.xml
    - app/src/main/res/drawable/sym_keyboard_shift.xml
    - app/src/main/res/drawable/sym_keyboard_shift_locked.xml
    - app/src/main/res/drawable/sym_keyboard_language_switch.xml
    - app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/KeyboardTheme.java
    - app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/KeyboardView.java
    - app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/MainKeyboardView.java
    - app/build.gradle
    - gradle.properties

key-decisions:
  - "PERF-фикс #2 — вариант «кэш клонов»: HashMap<Key, KeyDrawParams> в KeyboardView, инвалидация clear() в setKeyboard (attrs ключей неизменны после построения Keyboard); scratch-объект отклонён — mAnimAlpha мутируется на params, общий scratch дал бы гонку состояний между ключами"
  - "PERF-фикс #3 кэширует и строку, и textScaleX (layoutLanguageOnSpacebar имел side-effect setTextScaleX); инвалидация: setKeyboard + startDisplayLanguageOnSpacebar"
  - "gradle.properties: android.useAndroidX=false→true — обязательное условие androidx-зависимости (checkDebugAarMetadata падает без него); Jetifier не включался"
  - "Иконки: собственные path'ы, не Material Symbols — формы проще и меньше байт"

patterns-established:
  - "Тема — данными: новая тема = XML-стили + цвета light/night + layer-list drawables; Java-дифф только реестр KeyboardTheme"
  - "Ноль аллокаций в draw-пути: кэш с инвалидацией на setKeyboard-границе"

requirements-completed: [UI-01]

coverage:
  - id: D1
    description: "Тема id=7 «Tatar» зарегистрирована, дефолтна; LOCKED-палитра light/night ресурсами ios_*"
    requirement: UI-01
    verification:
      - kind: other
        ref: "grep D4D6DD/FFFFFF/B3B7C0/40000000 values/colors.xml; 2C2C2C/6B6B6B/474747/B3000000 values-night/; THEME_ID_TATAR=7 + DEFAULT_THEME_ID=THEME_ID_TATAR в KeyboardTheme.java; ./gradlew assembleDebug"
        status: pass
    human_judgment: false
  - id: D2
    description: "3 selector→layer-list drawable: roundRect 5dp + 1dp-тень + pressed; подключены к теме; setShadowLayer не добавлен"
    requirement: UI-01
    verification:
      - kind: other
        ref: "grep layer-list/state_pressed/5dp/1dp/ios_key_shadow в 3 drawable; diff-greп: 0 добавленных setShadowLayer"
        status: pass
    human_judgment: false
  - id: D3
    description: "Иконки shift/shift_locked/globe — собственные path'ы, тонировка сохранена, KeyboardIconsSet нетронут"
    requirement: UI-01
    verification:
      - kind: other
        ref: "diff-чек e0d470b..HEAD: 3 sym_* изменены, KeyboardIconsSet.java отсутствует в диффе; grep functionalTextColor в каждом"
        status: pass
    human_judgment: false
  - id: D4
    description: "3 PERF-фикса горячего пути: индексный цикл, кэш KeyDrawParams, кэш строки пробела"
    requirement: UI-01
    verification:
      - kind: other
        ref: "grep: iterator-цикл отсутствует; drawLanguageOnSpacebar без LocaleResourceUtils; 0 добавленных new Paint() в диффе"
        status: pass
    human_judgment: false
  - id: D5
    description: "A11y-каркас ExploreByTouchHelper + APK HARD GATE: release 700 679 байт ≤ 3 145 728"
    requirement: UI-01
    verification:
      - kind: other
        ref: "stat release APK = 700679; releaseRuntimeClasspath = только customview+annotation+core+collection; check-no-internet.sh exit 0"
        status: pass
    human_judgment: false
  - id: D6
    description: "Визуальное соответствие палитры, смена темы без перезапуска, ввод фаз 2–5 поверх нового рендера, ноль GC по профайлеру, smoke-матрица"
    requirement: UI-01
    verification: []
    human_judgment: true
    rationale: "On-device UAT — устройство недоступно (adb devices пуст); отложено в STATE.md Blockers по standing-схеме фаз 1–5"

duration: 15min
completed: 2026-07-18
status: complete
---

# Phase 6 Plan 01: iOS-скин — тема Tatar, layer-list-тень, PERF-фиксы, a11y-каркас Summary

**iOS-скин чистым XML-диффом: тема id=7 «Tatar» (дефолт) с LOCKED-палитрой light/night, layer-list-тень 1dp без setShadowLayer, собственные иконки, 3 хирургических PERF-фикса и ExploreByTouchHelper-каркас на androidx.customview — release-APK 701 КБ при бюджете 3 МБ**

## Performance

- **Duration:** 15 min
- **Started:** 2026-07-18T14:41:59Z
- **Completed:** 2026-07-18T14:57:00Z
- **Tasks:** 6 of 7 (Task 7 on-device UAT deferred)
- **Files modified:** 18

## Accomplishments

- Тема id=7 «Tatar» — дефолтная; палитра ТОЛЬКО ресурсами `ios_*` в values/ (#D4D6DD/#FFFFFF/#B3B7C0/#40000000) и values-night/ (#2C2C2C/#6B6B6B/#474747/#B3000000): dark mode бесплатно через штатный values-night + onConfigurationChanged; 6 старых тем нетронуты
- Геометрия iOS-ключа полностью данными: 3 selector→layer-list drawable (roundRect 5dp, тень-слой top=1dp, pressed: обычная темнеет / служебная белеет)
- Иконки shift (контурная стрелка), shift_locked (залитая стрелка + черта), globe (окружность + меридиан + параллели) — собственная простая геометрия; NAME_SHIFT_KEY_SHIFTED уже маппится на залитый вариант
- PERF: горячий путь onDraw без аллокаций по code review — индексный цикл, HashMap-кэш клонов KeyDrawParams, кэш строки+scaleX языка пробела
- A11y: KeyboardAccessibilityDelegate.kt (первый Kotlin-файл проекта) + hover-wiring; touch-путь не изменён; APK-гейт пройден числом

## Task Commits

1. **Task 1: Тема Tatar id=7 + палитра** — `d12784f` (feat)
2. **Task 2: 3 layer-list drawable** — `4f46cc1` (feat)
3. **Task 3: Иконки** — `4852646` (feat)
4. **Task 4: PERF-фиксы** — `7ee5ec9` (perf)
5. **Task 5: a11y-каркас + APK-гейт** — `4798c28` (feat)
6. **Task 6: bookkeeping** — `aa854d0` (docs)

**Plan metadata:** docs(06-01): complete plan (этот коммит)

## Files Created/Modified

См. key-files frontmatter. Java-граница: KeyboardTheme.java (реестр), KeyboardView.java (PERF #1–#2), MainKeyboardView.java (PERF #3 + a11y-wiring), 1 новый .kt. KeyboardSwitcher/PointerTracker/InputLogic/LatinIME/KeyboardIconsSet/btn_keyboard_key.xml — нетронуты.

## APK HARD GATE (Task 5)

- **До:** 645 830 байт (assembleRelease, unsigned-совместимая сборка с локальным keystore)
- **После:** 700 679 байт (**дельта +54 849 ≈ 54 КБ**, меньше ожидаемых 100–250 КБ благодаря R8)
- **Бюджет:** ≤ 3 145 728 байт — **PASS** (22% бюджета)
- **Транзитивное дерево** (releaseRuntimeClasspath): только `androidx.customview:customview:1.1.0` → annotation 1.1.0, core 1.3.0, collection 1.1.0. Сетевых сюрпризов нет — check-no-internet.sh зелёный по собранному APK.

## Источники path'ов иконок (T1-ревью)

Все три path'а написаны вручную простыми геометрическими командами (M/h/v/z, дуги), а не скопированы:

- `sym_keyboard_shift.xml` — stroke-стрелка `M 12,4.5 4.5,12.5 h 4 v 6.5 h 7 v -6.5 h 4 z`: пятиугольная стрелка с прямоугольным хвостом, пропорции отличны и от старого upstream-path (`M 12,3 1,15 …`, шире и острее), и от SF Symbols shift (у SF — скруглённый контур с плавными сопряжениями, наш — полигон с прямыми углами)
- `sym_keyboard_shift_locked.xml` — та же полигональная стрелка залитой + отдельный прямоугольник-черта `M 8.5,18.5 h 7 v 2.5 h -7 z` (у SF Symbols capslock черта интегрирована в единый скруглённый контур)
- `sym_keyboard_language_switch.xml` — глобус из трёх примитивов: окружность (две дуги a9,9), меридиан-эллипс (a4.2,9) и крест вертикаль+2 параллели; у SF Symbols globe меридианы — кривые Безье со сложными сопряжениями, наш — чистые дуги/прямые
- `sym_keyboard_delete.xml`, `sym_keyboard_return.xml` — не тронуты (per research §5)

Греп-чек: строки «SF», «Apple», «sfsymbols» в диффе ресурсов отсутствуют.

## Decisions Made

- **PERF #2 = кэш клонов** (`HashMap<Key, KeyDrawParams>` в KeyboardView, clear() в setKeyboard). Scratch-объект отклонён: `params.mAnimAlpha` мутируется после выбора params (в т.ч. в MainKeyboardView.onDrawKeyTopVisuals для altCode-ключей) — общий scratch перезаписывал бы состояние между ключами в одном кадре. Кэш-в-Key потребовал бы правки Key.java (расширение boundary).
- **PERF #3 кэширует пару строка+textScaleX**: upstream-метод layoutLanguageOnSpacebar имел скрытый side-effect `paint.setTextScaleX` — кэш только строки дал бы неверный масштаб на узких пробелах.
- **gradle.properties `android.useAndroidX=true`** — см. Deviations.
- **KeyboardSwitcher не правился** — риск R3 (live-reload SDK ≥ S) оставлен до UAT, файл вне диффа.
- **attrs.xml тронут** (enum `keyboardTheme` +Tatar=7): Case.keyboardTheme — реальный enum-атрибут форка, выравнивание реестра обязательно (комментарий в KeyboardTheme.java:33-34 это требует).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] gradle.properties: android.useAndroidX false→true**
- **Found during:** Task 5 (androidx.customview dependency)
- **Issue:** `checkDebugAarMetadata` падает: «Configuration contains AndroidX dependencies, but the android.useAndroidX property is not enabled» — база форка явно фиксировала `false`
- **Fix:** флип существующей строки на `true` (Jetifier НЕ включён — legacy support-библиотек в проекте нет)
- **Files modified:** gradle.properties
- **Verification:** assembleDebug + assembleRelease зелёные; APK-гейт и no-internet пройдены
- **Committed in:** 4798c28 (Task 5 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Обязательное условие первой androidx-зависимости; scope не расширен.

## Issues Encountered

- Verify-греп Task 2 `! grep -rq 'setShadowLayer' keyboard/` ложно срабатывал на **pre-existing upstream** `paint.setShadowLayer` для ТЕКСТА (KeyboardView.java:395, легитимен per research §3 — запрет касался фигур). Адаптирован до fail-capable-эквивалента: «0 добавленных setShadowLayer в диффе e0d470b..HEAD» — PASS.

## User Setup Required

None - no external service configuration required.

## Deferred Verification (Task 7 — on-device UAT)

Устройство недоступно (`adb devices` пуст) — по standing-схеме фаз 1–5 чекпойнт отложен в STATE.md Blockers. Чек-лист (полный — в STATE.md ⚠️ [Phase 6]):

1. Установка свежего APK; **uninstall желателен** — dev-prefs могли зафиксировать старую тему (`pref_keyboard_theme_20140509`, риск R6)
2. SC1 палитра light (фон #D4D6DD, белые клавиши, служебные #B3B7C0, 5dp, 1dp-тень) и dark (#2C2C2C/#6B6B6B/#474747); pressed-отклик
3. SC1 смена light↔dark при показанной клавиатуре без перезапуска (Android 12+ — риск R3; фикс = снятие `< S` в KeyboardSwitcher.java:91-93)
4. SC4 весь ввод фаз 2–5 поверх нового рендера (пятый ряд, глобус, shift/caps-иконки, long-press, double-space, свайп, 2 пальца)
5. SC3 профайлер: минута печати — ноль GC, janky ~0
6. Smoke: Telegram / Chrome WebView (229) / password; MIUI при наличии
7. TalkBack не верифицируется (фаза 9) — только отсутствие touch-регрессий

## Next Phase Readiness

- Фаза 7 (баллон-превью, long-press панель, хаптика) перекрашивает уже существующие KeyPreviewChoreographer/DrawingPreviewPlacerView той же темой Tatar — фундамент готов
- Финальная простановка чек-бокса UI-01 — после UAT

## Self-Check: PASSED

- Все key-files.created существуют на диске (`[ -f ]` — PASS ×5)
- `git log --grep="06-01"` — 6 коммитов
- Все `<acceptance_criteria>` Task 1–6 перепрогнаны — PASS (Task 2: setShadowLayer-греп в fail-capable diff-форме, см. Issues)
- Plan-level Verification пп. 1–7 — PASS; п. 8 — отложен честно (Blockers)

---
*Phase: 06-ios-skin-canvas*
*Completed: 2026-07-18 (complete-local; UAT deferred)*
