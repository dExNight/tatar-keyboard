# Phase 4: Механика ввода — регистр и служебные клавиши — Research

**Researched:** 2026-07-18 (аудит исходников форка Simple Keyboard, база b40c70d9 + наши фазы 1–3)
**Метод:** чтение кода по каждому требованию INPUT-01..04; вердикты с доказательствами file:line
**Главный вывод: все четыре требования УЖЕ РАБОТАЮТ в базе форка и корректно подхватываются нашими раскладками tatar/russian. Пробелов, требующих Java/XML-правок, не найдено. Фаза = структурная верификация + on-device UAT.**

## Сводная таблица вердиктов

| Req | Вердикт | Кратко |
|-----|---------|--------|
| INPUT-01 shift 3 состояния | **WORKS** | Полная стейт-машина в `KeyboardState.java`; двойной тап → caps lock через системный `ViewConfiguration.getDoubleTapTimeout()`; лейблы (вкл. ә ө ү җ ң һ) апкейсятся по elementId |
| INPUT-02 автокапитализация | **WORKS** | `getCursorCapsMode` → `CapsModeUtils.getCapsMode` (статика, без IPC), маскируется InputType поля; email/URL без CAP-флагов → off; tt_RU не ломает |
| INPUT-03 backspace по кодпоинтам + автоповтор | **WORKS** (с трактовкой) | Кодпоинты: `numChars = supplementary ? 2 : 1` → `deleteSurroundingText` — семантический эквивалент `deleteSurroundingTextInCodePoints`. Автоповтор: 400 мс старт → 50 мс интервал (=«ускоряется при удержании» в стандартной AOSP-трактовке; прогрессивного разгона нет — как и в Gboard/AOSP) |
| INPUT-04 Enter по imeOptions | **WORKS** | XML-switch по `imeAction` (иконки go/search/send/next/done), `performEditorAction` в InputLogic, `IME_FLAG_NO_ENTER_ACTION` → newline, Shift+Enter в multiline |

---

## INPUT-01: Shift — три состояния + визуальный регистр

**Вердикт: WORKS**

### Стейт-машина

`keyboard/internal/KeyboardState.java` — полная машина off/manual-shift/auto-shift/caps-lock:

- Одиночный тап: `onPressShift()` `KeyboardState.java:394-442` — в базовом состоянии `setShifted(MANUAL_SHIFT)` (:432); повторный тап в shifted → `setShifted(UNSHIFT)` в `onReleaseShift` (:477-481).
- Двойной тап → caps lock: `KeyboardState.java:400-414` — первый тап запускает `startDoubleTapShiftKeyTimer()`, второй в пределах таймаута → `setShiftLocked(true)` (:410). Таймаут — системный: `TimerHandler.java:156-159` (`ViewConfiguration.getDoubleTapTimeout()`, ~300 мс; отдельного конфига нет — используется платформенный, это норма).
- Выход из caps lock тапом: `onReleaseShift` `KeyboardState.java:474-476` → `setShiftLocked(false)`.
- Бонус (сверх требования): long-press shift → caps lock через moreKey `!code/key_capslock` (`key_styles_common.xml:46`, `Constants.CODE_CAPSLOCK = -2` `Constants.java:94`, обработка `KeyboardState.java:309-310`); таймаут `config_longpress_shift_lock_timeout = 1200` (`config-common.xml:39`).
- Caps lock визуально отличим: `shiftKeyStyle` switch по `keyboardLayoutSetElement` — `stickyOn` + `!icon/shift_key_shifted` для `alphabetShiftLocked` (`key_styles_common.xml:57-65`), drawable `sym_keyboard_shift_locked` (`KeyboardIconsSet.java:77`).

### Визуальная смена регистра лейблов (критично для пятого ряда)

Механизм: три shifted-элемента клавиатуры — `ELEMENT_ALPHABET_MANUAL_SHIFTED / _AUTOMATIC_SHIFTED / _SHIFT_LOCKED` (`KeyboardId.java:47-49`). `KeyboardSwitcher.java:200-222` переключает элементы по командам стейт-машины.

Апкейс лейблов при построении Key:

- `Key.java:406-416` `needsToUpcase()` — true для всех трёх shifted-элементов (если нет `preserveCase`);
- `Key.java:309-311` — лейбл: `StringUtils.toTitleCaseOfKeyLabel(label, localeForUpcasing)`;
- `Key.java:351-352` — код клавиши апкейсится тем же путём (`toTitleCaseOfKeyCode`) — т.е. в shift-состоянии коммитится «Ә», а не «ә»;
- `Key.java:290` — moreKeys апкейсятся через `MoreKeySpec` (подтверждает вывод фазы 3);
- `StringUtils.java:228-239` `toTitleCaseOfKeyLabel` = `label.toUpperCase(locale)` (спец-кейсы только ß и греческий, `StringUtils.java:186-193`).

**Наши раскладки:** локаль апкейса = локаль subtype (`Key.java:251` → `params.mId.getLocale()` → `KeyboardId.java:163`, `mSubtype.getLocale()` = tt_RU/ru). Для tt/ru Java не применяет никаких спец-правил кейсинга (особые локали в JDK — только tr/az/lt), а все буквы пятого ряда имеют штатные Unicode-заглавные пары: ә U+04D9→Ә U+04D8, ө U+04E9→Ө U+04E8, ү U+04AF→Ү U+04AE, җ U+0497→Җ U+0496, ң U+04A3→Ң U+04A2, һ U+04BB→Һ U+04BA. Кириллица ЙЦУКЕН — тривиально.

**Фолбэк shifted-элементов:** наши `keyboard_layout_set_tatar.xml` / `_russian.xml` объявляют только `alphabet` (без `alphabetManualShifted` и пр.) — точно как эталонный `keyboard_layout_set_qwerty.xml`. Это штатно: `KeyboardLayoutSet.java:150-155` — при отсутствии элемента параметры берутся от `ELEMENT_ALPHABET`, но `KeyboardId` создаётся с фактическим shifted-elementId (:161), поэтому `needsToUpcase` срабатывает и XML тот же, лейблы — заглавные. Ничего добавлять не нужно.

## INPUT-02: Автокапитализация по типу поля

**Вердикт: WORKS**

Цепочка:

1. `LatinIME.getCurrentAutoCapsState()` `LatinIME.java:625-627` → `InputLogic.getCurrentAutoCapsState` `InputLogic.java:388-400`:
   - гейт по префу `mAutoCap` (`SettingsValues.java:74`, `PREF_AUTO_CAP = "auto_cap"`, default **true**, экран настроек `prefs_screen_preferences.xml:20`);
   - гейт по layout set: `layoutUsesAutoCaps` `InputLogic.java:402-428` — false-список только для письменностей без регистра (arabic, hebrew, thai…); `tatar` и `russian` в списке отсутствуют → **true** (default-ветка :425-426);
   - `mConnection.getCursorCapsMode(inputType, mSpacingAndPunctuations)`.
2. `RichInputConnection.getCursorCapsMode` `RichInputConnection.java:282-293` — **не** дёргает `InputConnection#getCapsMode` (без IPC), считает по локальному кэшу текста через `CapsModeUtils.getCapsMode(mTextBeforeCursor, inputType, …)`.
3. `CapsModeUtils.getCapsMode` `CapsModeUtils.java:70+` — копия `TextUtils.getCapsMode`: результат **маскируется** битами `reqModes = inputType`. Поле без `TYPE_TEXT_FLAG_CAP_*` (email, URL, visible password) → возвращается `CAP_MODE_OFF` → авто-shift не включается. Поле с `CAP_SENTENCES` → `CAP_MODE_SENTENCES` после «. » / начала текста.
4. Применение: `KeyboardState.updateAlphabetShiftState` `KeyboardState.java:372-392` — `autoCapsFlags != CAP_MODE_OFF` → `setShifted(AUTOMATIC_SHIFT)` (:385-387); визуально — элемент `alphabetAutomaticShifted` (заглавные лейблы, см. INPUT-01).
5. Триггеры обновления: `onUpdateSelection` `LatinIME.java:501-520` (:517 `requestUpdatingShiftState`), после сепаратора `InputLogic.java:300-304` (`SHIFT_UPDATE_NOW`), после backspace `InputLogic.java:311-322`, при загрузке клавиатуры `LatinIME.java:455`.

**Локале-независимость (tt_RU):** `SpacingAndPunctuations` (`SpacingAndPunctuations.java:36-48`) строится от **ресурсной** локали: `sentence_separator = 46` ('.') из дефолтного `donottranslate-config-spacing-and-punctuations.xml:26`; переопределения существуют только для hy/bn/hi — для ru/tt действует дефолт с точкой, что верно для татарской пунктуации. Спец-ветки `mUsesAmericanTypography`/`mUsesGermanRules` для tt/ru обе false → базовые правила. Локаль subtype tt_RU ни на что здесь не влияет и ничего не ломает.

## INPUT-03: Backspace — кодпоинты + автоповтор

**Вердикт: WORKS (с зафиксированной трактовкой по обоим пунктам)**

### Удаление по кодпоинтам

`InputLogic.handleBackspaceEvent` `InputLogic.java:311-335`:

- при выделении — `deleteSelectedText()` (:324-325);
- обычный случай: `getCodePointBeforeCursor()` (`RichInputConnection.java:295-299`, `Character.codePointBefore` по локальному кэшу) → `numChars = Character.isSupplementaryCodePoint(cp) ? 2 : 1` → `mConnection.deleteTextBeforeCursor(numChars)` (:331-332);
- `RichInputConnection.deleteTextBeforeCursor` `RichInputConnection.java:329-340` → **`mIC.deleteSurroundingText(numChars, 0)`** — не KEYCODE_DEL;
- `sendDownUpKeyEvent(KEYCODE_DEL)` — только фолбэк, когда перед курсором нет текста в кэше (:328-329), что корректно (начало поля/пустой кэш).

**Трактовка для REQUIREMENTS.md:** формулировка требования называет API `deleteSurroundingTextInCodePoints`; форк использует эквивалент — подсчёт chars по кодпоинту + `deleteSurroundingText`. Семантика идентична (суррогатная пара удаляется целиком, «ә» = BMP = 1 char). Эквивалент принимаем как выполнение требования; менять на `deleteSurroundingTextInCodePoints` не нужно (лишний риск: API 24+ ок, но у форка вокруг кэш `mTextBeforeCursor`, синхронизированный с numChars). При простановке INPUT-03 — аннотировать.

### Автоповтор

Механизм целиком есть:

- `deleteKeyStyle` имеет `keyActionFlags="isRepeatable"` `key_styles_common.xml:74-78`; флаг → `Key.isRepeatable()` `Key.java:522-523`; наши ряды используют именно `deleteKeyStyle` (`rows_tatar.xml:51-53`, `rows_russian.xml:45`);
- старт: `PointerTracker.startRepeatKey` `PointerTracker.java:888-895` → `startKeyRepeatTimer(1)`;
- цикл: `TimerHandler` `MSG_REPEAT_KEY` `TimerHandler.java:51-53,67-76` → `PointerTracker.onKeyRepeat` `PointerTracker.java:897-908` — повторный ввод кода + перепланирование;
- тайминги: `PointerTracker.java:910-914` — первый повтор через `keyRepeatStartTimeout`, далее `keyRepeatInterval`; значения `config_key_repeat_start_timeout = 400`, `config_key_repeat_interval = 50` (`config-common.xml:22-23`), прошиты в тему `themes-common.xml:50-51`.

**Трактовка «ускоряется при удержании»:** реализация — задержка 400 мс, затем постоянные 50 мс (20 удалений/сек). Это стандартная схема AOSP LatinIME (у которой требование и списано); «ускорение» = переход от одиночных нажатий к быстрой серии после удержания. Прогрессивного разгона (например, удаление словами после N повторов, как в Gboard) в форке нет — это **не пробел MVP**, а осознанная трактовка; `repeatCount` уже пробрасывается в `onKeyRepeat` (`TimerHandler.java:53`), так что прогрессия достраивается тривиально, если после юзер-теста захочется — кандидат в backlog, не в фазу 4.

## INPUT-04: Enter по imeOptions

**Вердикт: WORKS**

### Вид клавиши (иконка/лейбл)

- `key_styles_enter.xml:35-101` — `<switch>` по `latin:imeAction`: actionGo/Next/Previous/Done/Send/Search → соответствующий `*ActionKeyStyle`; `actionCustomLabel` → лейбл из `editorInfo.actionLabel` (`customLabelActionKeyStyle`, `key_styles_actions.xml:55-56`; `Key.java:300-301` `LABEL_FLAGS_FROM_CUSTOM_ACTION_LABEL` → `mCustomActionLabel`); default (`actionNone`/`actionUnspecified`) → `defaultEnterKeyStyle` с `!icon/enter_key`.
- `imeAction` для switch-кейсов: `KeyboardId.imeAction()` `KeyboardId.java:160` → `InputTypeUtils.getImeOptionsActionIdFromEditorInfo` `InputTypeUtils.java:90-99`: **`IME_FLAG_NO_ENTER_ACTION` → IME_ACTION_NONE** (:91-92) → default-ветка → обычный Enter. Приоритет `actionLabel` (:93-94) тоже учтён.
- Иконки существуют и замаплены: `KeyboardIconsSet.java:68-74` (`sym_keyboard_return/go/search/send/next/done/previous` — VectorDrawable в `res/drawable*/`).
- В наших раскладках Enter приходит из `row_qwerty4` (`latin:keyStyle="enterKeyStyle"`, `row_qwerty4.xml:37`), включённого в `rows_tatar.xml:55-56` и `rows_russian.xml:49`; `key_styles_common.xml` (который include'ит `key_styles_enter`, :79-80) включён в оба наших kbd-файла (`kbd_tatar.xml:28,39`, `kbd_russian.xml:27,34`).

### Выполнение действия

`InputLogic.handleNonFunctionalEvent` `InputLogic.java:233-263`, кейс `CODE_ENTER` (:236-257):

- `actionLabel` задан → `performEditorAction(editorInfo.actionId)` (:240-243);
- иначе действие из imeOptions ≠ NONE → `performEditorAction(imeOptionsActionId)` (:244-252);
- NONE (в т.ч. после `IME_FLAG_NO_ENTER_ACTION`) → обычный ввод `\n` (:253-256);
- `performEditorAction` = `mConnection.performEditorAction` `InputLogic.java:450-452`.
- Бонус: Shift+Enter в multiline → `!code/key_shift_enter` (`key_styles_enter.xml:36-45`) → `sendDownUpKeyEvent(KEYCODE_ENTER, META_SHIFT_ON)` `InputLogic.java:215-218`.

## Cross-cutting: наши раскладки и настройки

**Раскладки tatar/russian — всё подключено штатно:**

- `rows_tatar.xml`: `shiftKeyStyle` (:46-48), `deleteKeyStyle` (:51-53), `row_qwerty4` (:55-56); `rows_russian.xml` — аналогично (:40,45,49). `key_styles_common` включён в обоих kbd (см. выше) — все стили (shift/delete/enter/space) определены до использования.
- Пятый ряд — простые `keySpec` литеральными кодпоинтами без `preserveCase` (`rowkeys_tatar_extra.xml`) → полный набор клавиш проходит через `needsToUpcase`-путь. Ничего специального для shift не требуется.
- Layout set объявляет только `alphabet` — фолбэк shifted-элементов штатный (см. INPUT-01), идентично upstream qwerty.

**Prefs, гейтящие механику фазы:**

| Pref | Ключ | Default | Влияние |
|------|------|---------|---------|
| Auto-capitalization | `auto_cap` (`Settings.java:50`) | **true** (`SettingsValues.java:74`) | выключает INPUT-02; INPUT-01/03/04 не гейтит |

Автоповтор backspace, двойной тап shift, Enter-действия — префами не гейтятся. Таймауты: repeat 400/50 мс и long-press shift-lock 1200 мс — ресурсные константы (`config-common.xml:22-23,39`); double-tap — системный `ViewConfiguration`.

## Gap list

**Пусто.** Кода писать не нужно. Два пункта — bookkeeping при простановке требований в REQUIREMENTS.md:

1. INPUT-03: аннотация «deleteSurroundingTextInCodePoints реализован эквивалентом (подсчёт chars по кодпоинту + deleteSurroundingText); "ускоряется" = 400 мс старт → серия 50 мс (схема AOSP), прогрессивного разгона нет — кандидат в backlog после юзер-теста».
2. INPUT-01: при желании отметить бонус — caps lock доступен также long-press'ом shift (1200 мс), сверх требования.

## Рекомендуемая форма плана

**Тонкий верификационный план (один план, ~3 задачи), по образцу фазы 3:**

1. **Структурные проверки** (grep/aapt2, автоматизируемо): shift/delete/enter-стили в наших rows_*; `key_styles_common` в kbd_*; `layoutUsesAutoCaps` не содержит tatar/russian; `isRepeatable` у deleteKeyStyle; сборка `assembleDebug` зелёная + `check-no-internet.sh`; Java-diff фазы пуст (фаза не трогает код).
2. **Простановка INPUT-01..04 в REQUIREMENTS.md** с аннотациями трактовок (п.1-2 gap list) — со ссылками на этот ресерч.
3. **On-device UAT** — стандартная отложенная схема (принята пользователем), чек-лист:
   - shift-тап → Ә Ө Ү Җ Ң Һ + ЙЦУКЕН заглавные, ввод одной буквы → возврат в строчные;
   - двойной тап shift → caps lock (иконка sticky), серия заглавных, тап → выход; long-press shift → caps lock (бонус);
   - автокапитализация: новое сообщение в Telegram → клавиатура открывается shifted; после «. » → shifted; в адресной строке Chrome и email-поле → НЕ shifted; выключение `auto_cap` в настройках → эффект пропадает;
   - backspace: удержание на «әни өй үрдәк» — серия удалений ~20/сек после ~0.4 с, «ә/җ/ң» удаляются целиком за одно нажатие (и одиночным тапом тоже);
   - Enter: поиск Chrome (лупа → выполняет поиск), Telegram (send/перенос по настройке мессенджера), заметки multiline (перенос строки), форма с actionDone (галка → закрытие);
   - всё — на татарской раскладке; выборочно повторить shift/backspace на русской.

Красная линия фазы соблюдена: ничего в работающей механике базы не переписываем.

---
*Phase: 04-mekhanika-vvoda-registr*
*Research: 2026-07-18*
