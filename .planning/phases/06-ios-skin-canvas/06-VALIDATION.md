---
phase: 6
slug: ios-skin-canvas
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-18
---

# Phase 6 — Validation Strategy

> Per-phase validation contract. Фаза почти целиком XML-дифф (тема id=7 + drawables + иконки) + хирургические Java-правки (реестр темы, 3 PERF-фикса, a11y-wiring) + один новый Kotlin-файл под числовым APK-гейтом. Автоматика = сборка debug+release + fail-capable-грепы + числовой замер APK + boundary-diff; визуал/тема-свитч/GC-профайлер — только on-device.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Gradle build (debug + release) + grep + git diff + stat (APK-размер) — решение фаз 2–5 сохраняется: юнит-харнеса нет; визуальный рендер и GC-поведение доказываются только на устройстве |
| **Config file** | none — Wave 0 покрыт инфраструктурой фазы 1 (`scripts/check-no-internet.sh`) |
| **Quick run command** | `./gradlew assembleDebug` |
| **Full suite command** | `./gradlew assembleDebug assembleRelease && bash scripts/check-no-internet.sh` |
| **Estimated runtime** | ~90–180 seconds (release-сборка с R8 дольше debug) |

---

## Sampling Rate

- **After every task commit:** `./gradlew assembleDebug`
- **After Task 5 (a11y + dependency):** full suite + числовой APK-гейт (`stat` release-APK ≤ 3 145 728 байт) + инспекция транзитивного дерева
- **After every plan wave:** full suite + boundary-diff (`git diff --name-only e0d470b..HEAD -- app/` ⊆ объявленные файлы; ровно один новый `.kt` или ноль при фолбэке)
- **Before `/gsd-verify-work`:** full suite green + все грепы Task 1–6
- **Max feedback latency:** 180 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 06-01.T1 | 06-01 | 1 | UI-01 (SC1: тема/палитра) | — | Тема id=7 зарегистрирована и дефолтна; LOCKED-палитра ios_* в values/ + values-night/ (D4D6DD/FFF/B3B7C0 и 2C2C2C/6B6B6B/474747); 6 старых тем нетронуты; ноль цветовых хардкодов в Java | build + grep | verify-команда Task 1 (грепы hex-значений в обоих квалификаторах + THEME_ID_TATAR = 7 + DEFAULT_THEME_ID) | ✅ (сборка фазы 1) | ⬜ pending |
| 06-01.T2 | 06-01 | 1 | UI-01 (SC1: геометрия/тень) | — | 3 drawable selector→layer-list: roundRect 5dp + 1dp-offset тень + pressed; подключены к теме; setShadowLayer отсутствует в keyboard/ | build + grep | verify-команда Task 2 (файлы + layer-list/state_pressed/5dp/1dp-грепы + `! grep setShadowLayer`) | ✅ | ⬜ pending |
| 06-01.T3 | 06-01 | 1 | UI-01 (SC2: иконки) | 06-01/T1 | Path'ы shift/shift_locked/globe перерисованы (diff подтверждает), тонировка ?attr/functionalTextColor сохранена, KeyboardIconsSet нетронут; формы оригинальные | build + grep + git diff | verify-команда Task 3; юридическое ревью форм — Task 6 п.2 (полуручное) | ✅ | ⬜ pending |
| 06-01.T4 | 06-01 | 1 | UI-01 (SC3: PERF-03) | — | 3 аллокации горячего пути устранены: индексный цикл (:280), без per-key клона KeyDrawParams, кэш строки пробела; new Paint/Rect не добавлены | build + grep | verify-команда Task 4 (отсутствие старого iterator-паттерна, drawLanguageOnSpacebar без LocaleResourceUtils, diff-чек на new Paint) | ✅ | ⬜ pending |
| 06-01.T5 | 06-01 | 1 | UI-01 (SC5: a11y-каркас) + APK-бюджет | 06-01/T2, T4 | Каркас ExploreByTouchHelper (Kotlin) + hover-wiring, touch-путь нетронут, dependencies = ровно customview, release-APK ≤ 3 МБ числом — ЛИБО полный откат + деферрал в фазу 9 в STATE.md | build + grep + numeric gate | verify-команда Task 5 (stat APK ≤ 3145728 + двухветочный чек «каркас на месте ∨ фолбэк записан») | ✅ | ⬜ pending |
| 06-01.T6 | 06-01 | 1 | boundary + legal + bookkeeping | 06-01/T1, T3 | Диф фазы ⊆ объявленные файлы; Compose отсутствует; SF-упоминаний в диффе ресурсов нет; Traceability = Verifying, чек-бокс UI-01 пуст, decision [06-01] | build + grep + git test | verify-команда Task 6 (boundary-diff от e0d470b + compose/SF-грепы + bookkeeping-грепы) | ✅ | ⬜ pending |
| 06-01.T7 | 06-01 | 1 | UI-01 + Phase SC1–SC5 (on-device) | 06-01/T3 | Палитра light/dark визуально; смена темы без перезапуска (вкл. Android 12+ live-reload); весь ввод фаз 2–5 вкл. пятый ряд; ноль GC по профайлеру; smoke-матрица | manual | — (checkpoint:human-verify, стандартная отложенная схема фаз 1–5) | — | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] Рабочая сборка (`./gradlew assembleDebug` / `assembleRelease`) — с фазы 1
- [x] `scripts/check-no-internet.sh` — с фазы 1
- [x] Система тем форка (KeyboardTheme + ContextThemeWrapper + values-night) — база маршрута (b), объект расширения, не создания
- [x] Механика ввода фаз 2–5 — объект smoke-верификации поверх нового рендера

*Новых Wave 0 зависимостей нет. androidx.customview — не Wave 0, а гейтованная поставка Task 5.*

---

## Manual-Only Verifications

On-device UAT (Task 7, отложенная схема при недоступном устройстве — как фазы 1–5):

1. **SC1 палитра:** светлая #D4D6DD/#FFF/#B3B7C0, тёмная #2C2C2C/#6B6B6B/#474747, радиус ~5dp, резкая 1dp-тень; pressed: обычная темнеет, служебная белеет. Uninstall желателен — dev-prefs могли зафиксировать старую тему (риск R6).
2. **SC1 смена темы:** системная light↔dark при показанной клавиатуре без перезапуска IME; отдельно проверить на Android 12+ (live-reload, риск R3 — при провале разрешён 1-строчный фикс условия `< S` в KeyboardSwitcher).
3. **SC4 ввод фаз 2–5:** пятый ряд ә ө ү җ ң һ видим и нажимаем (гапы не съели ширину — риск R4); tt/ru/en глобусом; shift/caps (иконки меняются); long-press ё/ъ; double-space; свайп-курсор; двупальцевая печать.
4. **SC3 профайлер:** минута печати в Android Studio Profiler — ноль GC-событий, janky ~0.
5. **Smoke-матрица:** пп. 1–3 в Telegram, Chrome WebView (keyCode 229), password-поле; MIUI — при наличии Xiaomi (иначе пометить как не покрыто).
6. **A11y:** только отсутствие touch-регрессий при выключенном TalkBack; озвучка — фаза 9.

**Почему без автоматики:** пиксельное соответствие палитры, поведение layer-list при HW-рендере, live-reload конфигурации и GC-события — свойства реального рендер-пайплайна устройства; скриншот-тесты/instrumented-профилирование несоразмерны соло-MVP (решение фаз 2–5 сохраняется). Юридическая проверка форм иконок (не-SF) — человеческое визуальное сравнение в Task 6, автоматизируется лишь греп-эвристикой.

---

## Boundary Contract

- База дифа: **e0d470b** (docs-коммит ресерча фазы 6 — последний коммит до кода фазы).
- Разрешённые файлы под `app/`: `res/values/themes-tatar.xml` (новый), `res/values/colors.xml`, `res/values-night/colors.xml`, `res/values/keyboard-themes.xml`, `res/values/attrs.xml` (условно), `res/values/strings.xml`, `res/drawable/ios_key_{normal,functional,spacebar}.xml` (новые), `res/drawable/sym_keyboard_{shift,shift_locked,language_switch,delete}.xml`, `keyboard/KeyboardTheme.java`, `keyboard/KeyboardView.java`, `keyboard/MainKeyboardView.java`, `accessibility/KeyboardAccessibilityDelegate.kt` (новый, условен по APK-гейту), `build.gradle`; условно `keyboard/KeyboardSwitcher.java` (ТОЛЬКО 1-строчный R3-фикс с записью в SUMMARY).
- Запрещено: новый KeyboardView, снятие final, оверрайд onDraw, Compose, btn_keyboard_key.xml, 6 существующих тем, KeyboardIconsSet.java, PointerTracker/PointerTrackerQueue/InputLogic/LatinIME, манифест, зависимости сверх androidx.customview:customview, ассеты Apple.
- Чеки:
  - `[ -z "$(git diff --name-only e0d470b..HEAD -- 'app/' | grep -v -E 'themes-tatar\.xml|colors\.xml|keyboard-themes\.xml|attrs\.xml|strings\.xml|ios_key_(normal|functional|spacebar)\.xml|sym_keyboard_(shift|shift_locked|language_switch|delete)\.xml|KeyboardTheme\.java|keyboard/KeyboardView\.java|MainKeyboardView\.java|KeyboardSwitcher\.java|KeyboardAccessibilityDelegate\.kt|build\.gradle')" ]`
  - APK-гейт: `SIZE=$(stat -f%z app/build/outputs/apk/release/*.apk 2>/dev/null || stat -c%s app/build/outputs/apk/release/*.apk); [ "$SIZE" -le 3145728 ]`
  - `! grep -rq 'androidx.compose' app/src/main/java` · `! grep -rq 'setShadowLayer' app/src/main/java/rkr/simplekeyboard/inputmethod/keyboard/`
