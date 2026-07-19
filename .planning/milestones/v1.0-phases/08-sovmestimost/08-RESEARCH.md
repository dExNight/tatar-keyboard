# Phase 8: Совместимость — Research

**Researched:** 2026-07-18
**Method:** аудит исходников форка (grep + чтение LatinIME/InputLogic/RichInputConnection/InputAttributes/ресурсов) + git-археология upstream-коммитов Android 15/16/17 + внешние источники по edge-to-edge IME.

## Сводка вердиктов

| Req | Вердикт | Кодовая работа фазы |
|---|---|---|
| COMPAT-01 password | **WORKS** (структурно бесплатно — словаря нет) | нет — пин грепами |
| COMPAT-02 WebView/229 | **WORKS** (composing-текста в коде ноль) | нет — пин грепами |
| COMPAT-03 edge-to-edge API 35+ | **WORKS** — предполагаемый гэп НЕ существует, upstream закрыл его до нашей базы | нет — пин грепами |
| COMPAT-04 ландшафт | **GAP (маленький, данные не код)**: `values-land` включает fullscreen/extract mode — противоречит решению проекта «onEvaluateFullscreenMode()=false» | 1-строчный флип ресурса |
| COMPAT-05 directBoot | **WORKS** (сервис + все prefs через device-protected storage) | нет — пин грепами |

Главный результат ресерча: **ожидаемая кодовая зона (COMPAT-03 insets) уже полностью решена upstream'ом**; единственный реальный кодовый пробел фазы оказался в COMPAT-04 (landscape fullscreen), и он закрывается флипом одного bool-ресурса.

---

## COMPAT-05: directBootAware — WORKS

**Манифест:** `app/src/main/AndroidManifest.xml:31` — `android:directBootAware="true"` стоит на сервисе `.latin.LatinIME`. Другие компоненты (SettingsActivity, SystemBroadcastReceiver/LOCALE_CHANGED) до разблокировки не нужны и корректно НЕ помечены — система их просто не поднимет в Direct Boot, это правильно.

**Полнота (главный риск — prefs до разблокировки):** форк системно ходит в SharedPreferences ТОЛЬКО через `PreferenceManagerCompat.getDeviceSharedPreferences()`, который построен на `createDeviceProtectedStorageContext()`:

- `compat/PreferenceManagerCompat.java:24-31` — единственная точка получения prefs, всегда device-protected.
- Все call-sites IME-процесса: `Settings.java:95` (Settings.init ← LatinIME.onCreate), `LatinIME.java:257` (DebugFlags), `LatinIME.java:932` (setNavigationBarColor), `RichInputMethodManager.java:150`, `KeyboardTheme.java:110`.
- Грепом подтверждено: прямых `PreferenceManager.getDefaultSharedPreferences(context)` вне PreferenceManagerCompat в IME-пути **ноль**; фрагменты настроек (`SubScreenFragment.java:67` и наследники) тоже идут через getDeviceSharedPreferences, и в любом случае работают только после разблокировки.

Итог: prefs-инициализация в `onCreate` (LatinIME.java:254-273) не может кинуть/зависнуть до first unlock — credential-protected storage не трогается вообще. minSdk 24 ≥ N, `createDeviceProtectedStorageContext` доступен безусловно. **Кода не требуется.** Runtime-подтверждение (ввод PIN на lock screen после ребута) — deferred UAT.

## COMPAT-01: password-поля — WORKS (тривиально)

- Детект: `InputAttributes.java:49-50` — `mIsPasswordField = isPasswordInputType || isVisiblePasswordInputType`; `InputTypeUtils.java:25-36,66-75` покрывает password / web password / number password / visible password (паттерн TextView.isPasswordInputType).
- Подавление подсказок: `InputAttributes.java:85-91` — `shouldSuppressSuggestions` (password + email/URI/filter/noSuggestions/autoComplete) → `mShouldShowSuggestions=false`. В MVP это мёртвый флаг: **движка подсказок/словаря/обучения в форке нет вообще** — греп по `UserHistory|personaliz|DictionaryFacilitator|learn` по всему `app/src/main/java` пуст. «Без подсказок и обучения» выполняется структурно: нечему подсказывать и нечему учиться.
- Password-специфичное поведение, добавленное нами в фазе 5: double-space→period гейтится `!settingsValues.mInputAttributes.mIsPasswordField` (`InputLogic.java:354-355`) — подтверждено, точка в пароле не вставится.
- Ввод в password-поле — тот же `commitText` путь, ничего особого не нужно.

**Кода не требуется.** Runtime (точки-маска, банковские поля) — deferred UAT.

## COMPAT-02: WebView / keyCode 229 — WORKS (структурно)

Решение проекта «без composing-текста» реально выполняется кодом:

- Греп `setComposingText|setComposingRegion|finishComposingText` по всему `app/src/main/java` — **ноль вхождений**. Composing-региона у нашего IME не существует в принципе.
- Путь коммита буквы: `InputLogic.sendKeyCodePoint()` → `mConnection.commitText(...)` (`InputLogic.java:598-606`); цифры — `sendDownUpKeyEvent` (унаследованный backward-compat из `InputMethodService.sendKeyChar`, безвреден в WebView — честные key events).
- Backspace: `handleBackspaceEvent` (`InputLogic.java:404-410`) — подсчёт chars по кодпоинту (`supplementary ? 2 : 1`) → `RichInputConnection.deleteTextBeforeCursor` → `deleteSurroundingText` (`RichInputConnection.java:356`). Это ровно рекомендация research/03 §10.1 (Chromium-баги завязаны на sendKeyEvent(KEYCODE_DEL) и composing). Фолбэк `sendDownUpKeyEvent(KEYCODE_DEL)` — только когда перед курсором нет кодпоинта (`InputLogic.java:406-407`), т.е. курсор в начале — no-op-случай.
- keyCode 229 — то, что Chromium шлёт сайтам при composing-вводе; без composing наш ввод для WebView неотличим от «обычного» commitText-ввода. Единственный residual-риск — сайты, слушающие keydown (пропадёт и у Gboard) — не наша зона.
- `replaceText` (`RichInputConnection.java:348`) на API 34+ использует `InputConnection.replaceText`, ниже — delete+commit; composing и здесь не участвует.

**Кода не требуется.** Runtime-прогон (Chrome-адресная строка, форма, contenteditable) — deferred UAT.

## COMPAT-03: edge-to-edge / WindowInsets API 35+ — WORKS (гэп-кандидат закрыт upstream'ом)

Предположение контекста («грep нашёл только LatinIME и SettingsActivity — вероятно, обработки нет») опровергнуто: **вся Android 15/16/17-линия insets уже в нашей базе**. Git-археология (все коммиты — предки HEAD, проверено `merge-base --is-ancestor`):

| Upstream-коммит | Что сделал |
|---|---|
| `e165029` / `2382af9` | первые workaround'ы Android 15 edge-to-edge/fullscreen |
| `9026c87`, `e0cd10b`, `2f5a437` «Android 15 insets attempt 4 (#530)» | итерации: OnApplyWindowInsetsListener + ручной applyViewInsets на keyboard_view |
| `827da4f` «fitsSystemWindows now works (#584)» | ручной листенер удалён; `android:fitsSystemWindows="true"` на MainKeyboardView в input_view.xml + `view.requestApplyInsets()` в setInputView |
| `2885ae5` «Android 17 SDK (#629)» | сплит: `layout-v28/input_view.xml` (fitsSystemWindows=true) для API 28+, базовый `layout/input_view.xml` без него для API 24-27 |

Текущее состояние HEAD:

1. **`res/layout-v28/input_view.xml:32`** — `android:fitsSystemWindows="true"` на `MainKeyboardView` → фреймворк сам кладёт systemBars-insets паддингом на вьюху клавиатуры; `KeyboardView.onMeasure` учитывает паддинги в размере (`KeyboardView.java:211-212`), т.е. клавиши поднимаются над gesture-nav-баром, а сам бар закрашивается фоном вьюхи (наша тема: `KeyboardView.Tatar` → `android:background=@color/ios_keyboard_background`, `themes-tatar.xml:43` — фон покрывает и padding-зону). Базовый `layout/input_view.xml` (API 24-27, без edge-to-edge enforcement) — сознательно без флага.
2. **`LatinIME.java:328-334`** — `setInputView` вызывает `view.requestApplyInsets()` (без этого листенер не сработал бы до первого layout-изменения).
3. **`LatinIME.java:534-567`** — полный `onComputeInsets`: contentTopInsets/visibleTopInsets по фактической высоте видимой клавиатуры, `TOUCHABLE_INSETS_REGION` + расширение тач-зоны на 100px вниз (EXTENDED_TOUCHABLE_REGION_HEIGHT) — окно IME растянуто на весь экран (`updateSoftInputWindowLayoutParameters`, LatinIME.java:602-623, MATCH_PARENT + Gravity.BOTTOM), поэтому корректные insets обязательны и они есть.
4. **`LatinIME.java:926-943`** — `setNavigationBarColor()`: цвет навбара = цвет клавиатуры, `setNavigationBarContrastEnforced(false)`, APPEARANCE_LIGHT_NAVIGATION_BARS по яркости. На API 35+ `setNavigationBarColor` deprecated/игнорируется для gesture-nav (бар прозрачный) — но это и не нужно: прозрачный бар показывает фон клавиатуры из п.1. Для 3-button nav (translucent на API 35) отключение contrast enforcement — правильное поведение.
5. **`LatinIME.java:297-305`** — Android 16 (BAKLAVA) ветка `onEvaluateInputViewShown` (`mUseOnScreen`) — upstream уже адаптирован и к API 36; compileSdk/targetSdk 37 = upstream-база, т.е. мы ровно в той конфигурации, которую upstream тестировал.

Это соответствует известному механизму проблемы (окно `SoftInputWindow` не диспатчит insets автоматически; демонстрированный фикс для IME — fitsSystemWindows на корневой вьюхе клавиатуры, см. codeboard issue #137 и гайд Android 15 edge-to-edge). Наша тема «Tatar» (id=7) на механику не влияет — fitsSystemWindows работает на уровне View независимо от темы.

**Вердикт: кода не требуется.** Что остаётся runtime-ом (deferred UAT / эмулятор API 35-36): визуальный чек «нижний ряд не перекрыт» на gesture-nav и 3-button nav, светлая/тёмная тема (фон под баром), ландшафт (боковые insets в gesture-nav). Это верификация, не разработка.

## COMPAT-04: ландшафт — WORKS по раскладкам, GAP по fullscreen-режиму

**Раскладки — WORKS:**

- `kbd_tatar.xml` / `kbd_russian.xml` задают высоты рядов относительными долями (`rowHeight="20%p"` / `16.667%p` с number row) и берут landscape-специфичные фракции из `values-land/config.xml`: `config_key_vertical_gap_5row=3.864%p`, `config_key_bonus_height_5row=10%p` — upstream «For 5-row keyboard» блок существует именно в values-land (строки 57-69), т.е. пятый ряд в ландшафте предусмотрен самим форком. Высота клавиатуры в ландшафте — `config_default_keyboard_height=176dp` / `config_min_keyboard_height=45%p` (values-land/config.xml) — раскладка сожмётся пропорционально, отдельные xml-land-варианты для tatar/russian не нужны (в xml-land лежат только kbd_number/phone/phone_symbols — телефонные раскладки, наши layout set'ы ссылаются на них через общий `@xml/kbd_phone` → квалификатор подхватится автоматически).
- sw600dp/sw768dp варианты конфигов есть (планшеты) — наследуются без нашего участия.

**Fullscreen/extract — GAP:** решение проекта (CLAUDE.md, research/01 §9): `onEvaluateFullscreenMode()=false`, «как iOS, extract mode нет». Реальность форка:

- `LatinIME.java:578-594` — `onEvaluateFullscreenMode()` НЕ возвращает безусловный false: `super.onEvaluateFullscreenMode() && Settings.readUseFullscreenMode(getResources())`.
- `Settings.java:323-325` → `R.bool.config_use_fullscreen_mode`; base `values/config.xml:23` = **false**, но **`values-land/config.xml:23` = `true`** (sw430dp/sw600dp/sw768dp переопределяют в false). То есть **на маленьких телефонах (< sw430dp) в ландшафте включается fullscreen/extract mode** — ExtractEditText над клавиатурой, ровно то, что проект решил не иметь. Замечу: upstream сам боролся с fullscreen-глюками Android 15 (`2382af9` «Workaround for Android 15 fullscreen») — ещё один довод его не включать.

**Минимальный фикс (единственный код фазы, и это данные, не Java):** флип `values-land/config.xml` `config_use_fullscreen_mode` → `false`. Java-дифф = 0 строк, путь `onEvaluateFullscreenMode` при этом всегда даёт false (ветка hardware-keyboard-suppression сохраняется). Альтернатива (override return false в LatinIME) отклонена: ресурсный флип реализует то же самое через штатный механизм и держит Java-базу нетронутой.

## MIUI/HyperOS: код vs UAT

Из research/03 §10.2 + текущего скаутинга ничего нового кодового:

- **Убийство процесса киллером** → проявляется как частые холодные старты; IME перезапускается системой сам. Метрика — фаза 11 (холодный старт < 400 мс), кода в фазе 8 нет.
- **Превью/клиппинг попапов** — снят фазой 7: превью и long-press-панель рисуются in-layer (DrawingPreviewPlacerView в окне IME), PopupWindow в исходниках отсутствует — классический MIUI-класс проблем с окнами поверх неприменим.
- **«Клавиатура исчезает при наборе»** (давний MIUI-баг, бьёт и Gboard) — системный, воспроизводится только на устройстве; чек-пункт UAT-матрицы, не код.

**Вердикт: для фазы 8 по MIUI кодовых действий нет; всё — пункты письменной матрицы.**

## Форма плана

Одна фаза-план (08-01), три задачи:

1. **Код (микро):** флип `values-land/config.xml` `config_use_fullscreen_mode` → `false` (COMPAT-04 / решение «без extract mode»). Boundary: 1 файл, 1 строка.
2. **Структурная верификация (fail-capable грепы, по образцу фаз 4-5):**
   - COMPAT-01: `mIsPasswordField` в InputAttributes + гейт в InputLogic:355; отсутствие словаря (греп UserHistory/Dictionary пуст).
   - COMPAT-02: греп `setComposingText|setComposingRegion` = 0 вхождений; commitText в InputLogic:606; deleteSurroundingText в RichInputConnection:356.
   - COMPAT-03: `fitsSystemWindows` в layout-v28/input_view.xml; `requestApplyInsets` в LatinIME:333; `onComputeInsets` в LatinIME:535; `setNavigationBarContrastEnforced` в LatinIME:935.
   - COMPAT-04: `config_use_fullscreen_mode=false` во ВСЕХ config-вариантах (values, values-land, sw430/600/768).
   - COMPAT-05: `directBootAware="true"` в манифесте; отсутствие `PreferenceManager.getDefaultSharedPreferences` вне PreferenceManagerCompat.
   - Сборка assembleDebug + check-no-internet.
3. **Документ SC5 (письменная матрица):** `08-UAT-MATRIX.md` — полный чек-лист прохода InputConnection-матрицы. Структура: строки = окружения (Telegram; Chrome адресная строка; Chrome форма/WebView-contenteditable [229]; password-поле; поле банка/PIN; multi-line заметки; поля actionSearch/Done/Next; эмулятор API 35-36 gesture-nav + 3-button [edge-to-edge]; ландшафт на телефоне; Direct Boot PIN после ребута; MIUI/HyperOS-устройство; One UI-устройство); колонки = сценарий (татарский текст ә ө ү җ ң һ / backspace+удержание / курсор в середину / Enter-действие / double-space / переключение tt-ru-en) × статус (PASS/FAIL/N-A) × заметки. Часть пунктов помечается «закрыто структурно» со ссылкой на грепы задачи 2; device-пункты — в отложенный UAT-бандл фаз 1-7 (standing-паттерн). Финальная простановка чекбоксов COMPAT-01..05 в REQUIREMENTS.md — после UAT.

## Источники (внешние, по COMPAT-03)

- https://developer.android.com/develop/ui/views/layout/edge-to-edge — enforcement с targetSdk 35: прозрачный gesture-nav, translucent 3-button, обязка insets.
- https://github.com/gazlaws-dev/codeboard/issues/137 — кейс IME, перекрытого системными панелями на API 35; подтверждённый фикс — fitsSystemWindows на корневой вьюхе клавиатуры (SoftInputWindow не диспатчит insets автоматически).
- https://medium.com/androiddevelopers/insets-handling-tips-for-android-15s-edge-to-edge-enforcement-872774e8839b — systemBars-insets паддингом/маргином, contrast enforcement.
- Upstream Simple Keyboard коммиты: 2f5a437 (#530), 827da4f (#584), 2885ae5 (#629) — эволюция insets-обработки, вся в нашей базе.

---
*Phase: 08-sovmestimost*
*Researched: 2026-07-18*
