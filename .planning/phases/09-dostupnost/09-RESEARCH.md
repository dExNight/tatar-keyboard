# Phase 9: Доступность (TalkBack) — Research

**Researched:** 2026-07-18
**Domain:** Android accessibility — ExploreByTouchHelper, TalkBack, IME
**Confidence:** HIGH (аудит по реальным исходникам форка + AOSP LatinIME source, скачан и прочитан)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Достроить каркас фазы 6 (`KeyboardAccessibilityDelegate.kt`) до полной реализации A11Y-01/A11Y-02 — грей-зон нет.
- Контент-описания букв ә ө ү җ ң һ — локализованные, строковыми ресурсами, не хардкод.
- Действия: клик по виртуальному узлу = нажатие клавиши (onPerformActionForVirtualView достроить).
- Динамика: узлы обновляются при смене раскладки/шифта.
- SC4: hover-path не должен деградировать обычный ввод.

### Claude's Discretion
- Формат описаний татарских букв («татарская э» из требования — образец, не догма; локализация ru + tt при разумных усилиях).
- Полнота: MVP = все клавиши озвучиваемы и нажимаемы через TalkBack; тонкости (announce on shift change и т.п.) — по AOSP-паттерну, без перфекционизма.
- Java/Kotlin правки допустимы; минимальный дифф.

### Deferred Ideas (OUT OF SCOPE)
- SC3 (реальный набор слова с TalkBack) — device-only, отложенный UAT-бандл (принято).
- Локализация описаний на tt/en beyond reasonable — backlog.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| A11Y-01 | TalkBack озвучивает клавиши: ExploreByTouchHelper, каждая клавиша — виртуальный узел | § 1 (аудит каркаса: узлы уже есть, гэпы — описания и ACTION_CLICK), § 4 (маршрут клика), § 5 (динамика) |
| A11Y-02 | Татарские буквы имеют контент-описания (напр. «татарская э» для ә) | § 2 (схема описаний, полная таблица), § 3 (раскладка строковых ресурсов) |
</phase_requirements>

---

## Summary

Каркас фазы 6 — рабочий ExploreByTouchHelper: enumeration, hit-testing и bounds корректны (проверено ревью фазы 6, INFO-9). Для полной A11Y-01/02 не хватает ровно двух вещей: (1) **человеческих описаний** — сейчас TalkBack произносит сырой label или внутреннее имя иконки («shift_key», «space_key»), а (2) **двойной тап по узлу ничего не делает** — `onPerformActionForVirtualView` возвращает `false`, т.е. незрячий пользователь слышит клавиши, но не может печатать (главный гэп для SC3).

Оба гэпа закрываются AOSP-паттерном, скопированным из LatinIME (исходник `KeyCodeDescriptionMapper.java` + `KeyboardAccessibilityDelegate.java` скачан и прочитан в этой сессии): маппер «код клавиши → строковый ресурс spoken_description_*» и клик через **синтез MotionEvent DOWN/UP в центр клавиши** — весь штатный путь PointerTracker (shift-машина, haptics, preview) переиспользуется бесплатно, ноль дублирования логики ввода. Опасение m4 из ревью фазы 6 (смещение hover hit-testing из-за verticalCorrection) снимается: для MainKeyboardView `verticalCorrection = 0.0dp` — коррекция чисто padding-овая и согласована с bounds узлов.

**Primary recommendation:** два новых артефакта (`KeyDescriptionMapper.kt` + `strings-a11y.xml` base-en / values-ru) и ~30 строк диффа в существующем `KeyboardAccessibilityDelegate.kt`. Java-файлы форка не трогаются вообще.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Виртуальные узлы / hover | `KeyboardAccessibilityDelegate.kt` (a11y-слой) | MainKeyboardView (wiring, уже есть) | ExploreByTouchHelper — единственный владелец a11y-дерева |
| Описания клавиш | новый `KeyDescriptionMapper.kt` | строковые ресурсы values/ + values-ru/ | AOSP-паттерн: маппер отделён от делегата,描ания — данными |
| Клик = ввод | делегат → синтез MotionEvent → `MainKeyboardView.processMotionEvent` | PointerTracker/KeyboardState (не трогаем) | Переиспользование штатного touch-пути; ввод не дублируется |
| Обновление при shift/layout | `MainKeyboardView.setKeyboard` → `invalidateRoot()` (уже есть) | — | Каждая смена элемента = новый Keyboard = setKeyboard |

---

## 1. Аудит каркаса — полная трасса и гэп-лист

### Что уже работает (verified по коду)

| Аспект | Где | Статус |
|--------|-----|--------|
| Wiring | `MainKeyboardView.java:184-185` (конструктор, после `mKeyDetector`), `:189-191` (`dispatchHoverEvent` → делегат первым, fallback super) | ✅ OK. Helper возвращает `false` при выключенном touch exploration → обычный ввод не затронут (SC4) |
| Enumeration | `KeyboardAccessibilityDelegate.kt:49-56` — id = индекс в `keyboard.sortedKeys`, спейсеры отфильтрованы | ✅ OK. `sortedKeys` включает спейсеры (Key.Spacer попадает в `mSortedKeys`, `KeyboardParams.java:97-106`), поэтому индексы стабильны, а спейсеры пропущены и в populate (строка 64) |
| Hit-testing | `:43-47` — `keyDetector.detectHitKey` | ✅ OK, m4 снят: `KeyDetector` установлен с `(-paddingLeft, -paddingTop + verticalCorrection)` (`MainKeyboardView.java:275-276`), а `verticalCorrection` для MainKeyboardView = `config_keyboard_vertical_correction` = **0.0dp** (`themes-common.xml:43`, `config-common.xml:50`; themes-tatar.xml переопределяет verticalCorrection только для MoreKeysKeyboardView). Т.е. коррекция = чистый перевод view→keyboard координат, симметричный `+padding` в bounds узлов. Нюанс: `detectHitKey` матчит по `mHitbox` (включает половины гэпов, `Key.java:822-824`), bounds узла — по видимым x/y/w/h; hover в гэпе даёт узел, чьи bounds не содержат точку. Так же у AOSP; безвредно |
| Node bounds/populate | `:58-81` — bounds `key.x/y + padding`, non-null contentDescription, stale-id ветка (1×1 bounds) | ✅ Контракт helper'а соблюдён (ревью фазы 6 INFO-9, verified против байткода customview-1.1.0) |
| invalidateRoot | `MainKeyboardView.java:281` в `setKeyboard()` | ✅ Каждая смена shift-состояния/раскладки/языка идёт через `KeyboardSwitcher.setKeyboard` → `keyboardView.setKeyboard(newKeyboard)` (`KeyboardSwitcher.java:140-149`) → invalidateRoot. Полнота подтверждена: shift-состояния — это отдельные ELEMENT_* клавиатуры (`KeyboardId.java:46-54`), не мутация ключей |

### Гэпы (то, что достраивает фаза 9)

| # | Гэп | Где | Что происходит сейчас |
|---|-----|-----|----------------------|
| G1 | **ACTION_CLICK не выполняется** | `KeyboardAccessibilityDelegate.kt:83-90` — `onPerformActionForVirtualView` возвращает `false` | TalkBack фокусирует клавишу и объявляет её, но двойной тап (и lift-to-type) не печатает НИЧЕГО. Это блокер SC3 — классическая «скелет объявляет, но не кликает» |
| G2 | **Сырые описания** (ревью m3) | `:71-72` — `key.label ?: KeyboardIconsSet.getIconName(key.iconId)` | Служебные клавиши озвучиваются внутренними именами: «shift_key», «delete_key», «language_switch_key»; пробел — «space_key»; enter в action-режимах — «search_key»/«send_key» и т.д. (все клавиши action row — иконочные, label=null: `key_styles_common.xml:76,84,109`, `key_styles_actions.xml:26-56`). Буквы озвучиваются самим label — для кириллицы ОК, для ә ө ү җ ң һ произношение TTS не гарантировано |
| G3 | Нет `isClickable`/`isTextEntryKey` на узле | `:58-81` | `addAction(ACTION_CLICK)` есть, но `setClickable(true)` не выставлен; `setTextEntryKey(true)` (есть в androidx.core 1.3.0 — verified javap) не выставлен — а именно по нему TalkBack включает keyboard-режим (lift-to-type, echo) для узла |
| G4 | Нет TYPE_VIEW_CLICKED события после клика | — | После успешного ACTION_CLICK положено `sendEventForVirtualView(id, TYPE_VIEW_CLICKED)` — иначе TalkBack не даёт звуковой обратной связи о срабатывании |

Не-гэпы (проверено, чинить не надо): hover не конфликтует с вводом — `dispatchHoverEvent` и `onTouchEvent` (`MainKeyboardView.java:487`) — разные event-стримы, PointerTracker не видит hover; делегат создан после mKeyDetector; спейсеры в populate обрабатываются.

### Известное ограничение (документировать, не чинить)

**MoreKeys-панель (long-press дубли) вне a11y-дерева**: делегат навешен только на MainKeyboardView; `MoreKeysKeyboardView` (`MoreKeysKeyboardView.java:35`) виртуальных узлов не имеет. Для A11Y-01/02 это приемлемо: все 6 татарских букв имеют **собственные клавиши пятого ряда** — TalkBack-пользователю long-press не нужен для татарского ввода. ё/ъ достижимы только long-press'ом (е→ё, ь→ъ) — недоступны с TalkBack в MVP. Зафиксировать как deferred (backlog), в план не включать. `[VERIFIED: grep по исходникам форка]`

---

## 2. Схема контент-описаний (A11Y-02)

### Принцип (AOSP-паттерн)

AOSP `KeyCodeDescriptionMapper.getDescriptionForKey` `[CITED: raw.githubusercontent.com/nxp-imx-android/aosp_platform_packages_inputmethods_LatinIME/master/java/src/com/android/inputmethod/accessibility/KeyCodeDescriptionMapper.java — скачан и прочитан]`:
1. Спец-коды (shift/delete/enter/…) → строковый ресурс `spoken_description_*`, для shift и ?123 — с учётом `keyboard.mId.mElementId`, для enter — с учётом `keyboard.mId.imeAction()`.
2. Акцентированные буквы, которые TTS может не выговорить → явный ресурс (`spoken_accented_letter_%04X`); заглавная = шаблон `spoken_description_upper_case` («Заглавная %s»).
3. Остальные буквы/символы → сама буква (label): TalkBack/TTS русской локали читает кириллицу сам.

Наш маппер — та же схема, но вместо reflection-lookup по имени ресурса (`getIdentifier`) — прямой `when(code)`: букв всего 6×2, reflection не нужен (и минус аллокации/строки).

### Татарские буквы — полная таблица

Требование даёт образец: «татарская э» для ә (фонетическая пара — э, хотя в раскладке long-press парует ә и с а, и с э: `rowkeys_tatar2.xml:33,57`). Для остальных — лингвистическая пара из LAYOUT-02:

| Буква | Codepoint | Описание ru (строчная) | Описание ru (заглавная) | Base en |
|-------|-----------|------------------------|-------------------------|---------|
| ә / Ә | U+04D9 / U+04D8 | «татарская э» | «заглавная татарская э» | "Tatar schwa" |
| ө / Ө | U+04E9 / U+04E8 | «татарская о» | «заглавная татарская о» | "Tatar o" |
| ү / Ү | U+04AF / U+04AE | «татарская у» | «заглавная татарская у» | "Tatar u" |
| җ / Җ | U+0497 / U+0496 | «татарская ж» | «заглавная татарская ж» | "Tatar zhe" |
| ң / Ң | U+04A3 / U+04A2 | «татарская н» | «заглавная татарская н» | "Tatar en" |
| һ / Һ | U+04BB / U+04BA | «татарская х» | «заглавная татарская х» | "Tatar he" |

Заглавные — НЕ 6 отдельных строк, а AOSP-шаблон: `spoken_description_upper_case` = «Заглавная %s» (`values-ru` AOSP, msgid 4904835255229433916 `[CITED: AOSP values-ru/strings-talkback-descriptions.xml — скачан]`) + `Character.isUpperCase(code)` → `toLowerCase` → lookup строчной. На shift-раскладке клавиша несёт код U+04D8 (Ә) — `Key.java:351` upcase'ит код, не только label — поэтому детект по коду работает.

Остальные 31 буква (ЙЦУКЕН + ё/ъ где видимы): **описание = сам label** (одна буква) — русский TTS читает кириллицу штатно, включая заглавные на shift-раскладке (label уже заглавный, `Key.java:309`). `[ASSUMED]` — что ru-TTS корректно читает одиночные кириллические буквы: стандартное поведение, но подтверждается только device-UAT (SC3, deferred).

### Служебные клавиши — полная таблица

Ключевой факт форка: **готовых spoken_description-строк НЕТ** (grep «spoken» по res/ — ноль совпадений, кроме комментария; форк выпилил весь accessibility/ пакет AOSP вместе со strings-talkback-descriptions.xml). Все строки пишем сами. Русские формулировки — по AOSP values-ru (скачаны, см. Sources):

| Клавиша | Детект в маппере | Описание ru | Base en |
|---------|------------------|-------------|---------|
| Shift (alphabet, обычный) | `code == CODE_SHIFT` (-1) && elementId ∈ {ALPHABET, …} | «Shift» → «Клавиша верхнего регистра» | "Shift" |
| Shift (manual/auto shifted) | elementId ∈ {MANUAL_SHIFTED(1), AUTOMATIC_SHIFTED(2)} | «Верхний регистр включён» | "Shift on" |
| Shift (caps lock) | elementId == SHIFT_LOCKED(3) | «Caps Lock включён» | "Caps lock on" |
| #+= (symbols shift) | `code == CODE_SHIFT` && elementId == SYMBOLS(5) | «Дополнительные символы» | "More symbols" |
| 123? назад (symbols shifted) | `code == CODE_SHIFT` && elementId == SYMBOLS_SHIFTED(6) | «Символы» | "Symbols" |
| Delete | `code == CODE_DELETE` (-5) | «Удалить» | "Delete" |
| Пробел | `code == CODE_SPACE` (32) | «Пробел» | "Space" |
| Enter (default/unspecified) | `code == CODE_ENTER` (10), imeAction default | «Ввод» | "Enter" |
| Enter actionSearch | `imeAction() == IME_ACTION_SEARCH` | «Поиск» | "Search" |
| Enter actionGo | IME_ACTION_GO | «Перейти» | "Go" |
| Enter actionSend | IME_ACTION_SEND | «Отправить» | "Send" |
| Enter actionNext | IME_ACTION_NEXT | «Далее» | "Next" |
| Enter actionPrevious | IME_ACTION_PREVIOUS | «Назад» | "Previous" |
| Enter actionDone | IME_ACTION_DONE | «Готово» | "Done" |
| Enter custom label | `key.label != null` | сам label (AOSP: label в приоритете) | — |
| Shift+Enter (multiline shifted) | `code == CODE_SHIFT_ENTER` (-11) | «Ввод» | "Enter" |
| ?123 / АБВ | `code == CODE_SWITCH_ALPHA_SYMBOL` (-3): alphabet-элементы → «Символы», symbols-элементы → «Буквы» | — | "Symbols" / "Letters" |
| Глобус | `code == CODE_LANGUAGE_SWITCH` (-10) | «Сменить язык» | "Switch language" |
| Настройки | `code == CODE_SETTINGS` (-6) — только в moreKeys, на всякий случай | «Настройки» | "Settings" |
| Запятая, точка, символы | label (одиночный символ) | TalkBack читает пунктуацию сам | — |
| CODE_OUTPUT_TEXT | `key.outputText` | сам текст | — |
| Fallback | label ?: «Неизвестный символ» | — | "Unknown" |

Замечания к детекту:
- Все нужные коды публичны: `key.getCode()` (`Key.java:490`), `Constants.CODE_*` (`Constants.java:93-106`). `iconName` для детекта НЕ нужен — код надёжнее (m3-паттерн «raw icon name» уходит полностью).
- elementId/imeAction: `keyboardView.keyboard.mId.mElementId` / `.imeAction()` (`Keyboard.java:51`, `KeyboardId.java:62,159`). У форка нет ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED (id 4 отсутствует в `KeyboardId.java:46-54`) — ветку AOSP для него не копировать.
- Enter в форке — всегда иконка без label (`key_styles_actions.xml:26-56`; строк `label_go_key` и т.п. в форке нет — grep пуст), поэтому AOSP-ветка «label first» сработает только для custom action label (`dummy_label`, `key_styles_actions.xml:56` — там label есть).

### Password-поля

AOSP obscure-механика (описание→«Маркер списка») была завязана на `Settings.Secure.ACCESSIBILITY_SPEAK_PASSWORD`, **deprecated с API 26**: с O управление озвучкой паролей полностью у TalkBack (headphones-детект и т.д.), и AOSP-ветка `shouldObscure` на современных устройствах фактически всегда «говорить». Решение MVP: **не обскьюрить описания клавиш** — иначе незрячий пользователь не сможет ввести пароль вообще; эхо набранных символов — зона ответственности системы/TalkBack, не IME (мы не вызываем `announceForAccessibility` нигде — утечки со своей стороны нет). `[VERIFIED: android developer docs — ACCESSIBILITY_SPEAK_PASSWORD deprecated in O; grep announceForAccessibility по форку — 0]`

---

## 3. Раскладка строковых ресурсов

Конвенция форка: base = английский (`values/strings.xml`), ~30 локальных оверлеев, включая существующий **values-ru/strings.xml** (русские переводы настроек уже есть). values-tt НЕТ. Следуем конвенции:

```
app/src/main/res/
├── values/strings-a11y.xml        # НОВЫЙ: base en — все spoken_description_* + 6 татарских букв + upper_case-шаблон
└── values-ru/strings.xml          # ДОПОЛНИТЬ теми же именами по-русски (или отдельный values-ru/strings-a11y.xml — чище, тоже валидно)
```

- Отдельный файл `strings-a11y.xml` (а не врезка в strings.xml) — по образцу AOSP `strings-talkback-descriptions.xml`: вся a11y-лексика в одном месте, ревью и tt-локализация позже — точечные.
- Имена ресурсов — AOSP-совместимые (`spoken_description_shift`, `spoken_description_delete`, …, `spoken_letter_04d9`…): при любом будущем сравнении с AOSP маппинг очевиден.
- values-tt: deferred (CONTEXT). Русские описания «татарская э» понятны татароязычным пользователям (все — билингвы, системная локаль почти всегда ru).
- APK-эффект: ~25 строк × 2 локали — сотни байт, бюджет 3 МБ не задет.

---

## 4. Маршрут ACTION_CLICK → реальный ввод (гэп G1, ядро SC3)

### Рекомендация: синтез MotionEvent (AOSP-паттерн, verified по исходнику)

AOSP `KeyboardAccessibilityDelegate.performClickOn(key)` делает ровно это `[CITED: AOSP KeyboardAccessibilityDelegate.java:257-279 — скачан и прочитан]`:

```kotlin
override fun onPerformActionForVirtualView(
    virtualViewId: Int, action: Int, arguments: Bundle?,
): Boolean {
    if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false
    val key = sortedKeys().getOrNull(virtualViewId)?.takeUnless { it.isSpacer } ?: return false
    // Центр видимой области клавиши в координатах view (mHitbox приватен — видимый центр всегда внутри hitbox)
    val x = key.x + key.width / 2 + keyboardView.paddingLeft
    val y = key.y + key.height / 2 + keyboardView.paddingTop
    val t = SystemClock.uptimeMillis()
    for (a in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
        val ev = MotionEvent.obtain(t, t, a, x.toFloat(), y.toFloat(), 0)
        keyboardView.processMotionEvent(ev)   // public, MainKeyboardView.java:503
        ev.recycle()
    }
    sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
    return true
}
```

**Почему именно так:**
- Полный штатный путь: `processMotionEvent` → `PointerTracker.processMotionEvent` → `detectAndSendKey` (`PointerTracker.java:880`) → `callListenerOnCodeInput` (`:225-247`) → `LatinIME.onCodeInput` (`LatinIME.java:732`). Shift-машина едет через `onPressKey`/`onReleaseKey` (`KeyboardState.java:259,300`) — тап по shift через TalkBack корректно переключает ELEMENT_*, caps-lock double-tap-логика, haptics (`LatinIME.java:852`), preview — всё бесплатно и в точности как палец.
- Альтернатива «дёрнуть listener напрямую» отвергнута: `mKeyboardActionListener` у MainKeyboardView приватен без геттера, `PointerTracker.sListener` — private static; пришлось бы добавлять геттер и вручную воспроизводить последовательность onPressKey→onCodeInput→onReleaseKey (риск рассинхрона shift-машины). Синтез события — меньший и более надёжный дифф.
- `processMotionEvent` вместо `onTouchEvent`: обходит ветку NonDistinctMultitouchHelper (`MainKeyboardView.java:487-500`) — для одноточечного синтетического события поведение идентично, но путь детерминированнее.
- Аллокация MotionEvent — вне цикла отрисовки (a11y-события, единицы в секунду), PERF-бюджет «ноль аллокаций в draw» не задет; `obtain/recycle` — пул.
- SC4: синтетические события возникают ТОЛЬКО из ACTION_CLICK (TalkBack включён); обычный touch-путь не изменён ни одной строкой.

### Lift-to-type

Современный TalkBack на клавиатурах применяет lift-to-type: палец скользит (hover), отпускание → TalkBack сам шлёт ACTION_CLICK на сфокусированный узел. Т.е. ровно тот же `onPerformActionForVirtualView`-маршрут — отдельного кода не нужно. Условие корректной классификации «это клавиатурная кнопка» — `node.isTextEntryKey = true` (гэп G3; метод есть в AccessibilityNodeInfoCompat androidx.core 1.3.0 — verified javap по jar из gradle-кэша). `[ASSUMED]` — точная эвристика lift-to-type у TalkBack не документирована публично; двойной тап работает в любом случае, lift-to-type подтвердит device-UAT.

---

## 5. Динамика (shift/раскладка/язык)

- **Механизм уже полный**: любое изменение shift-состояния — это `KeyboardSwitcher.setAlphabet*Keyboard()` → новый объект Keyboard (другой ELEMENT_*) → `MainKeyboardView.setKeyboard` → `mAccessibilityDelegate.invalidateRoot()` (`MainKeyboardView.java:281`). Ключи новой клавиатуры уже несут заглавные label/code (`Key.java:250,309,351` — `needsToUpcase`) → описания меняются сами: ә→«татарская э», Ә→«заглавная татарская э», shift-клавиша меняет описание по elementId. Дописывать нечего, кроме самого маппера.
- **Announce «верхний регистр включён» при смене** (AOSP `spoken_description_shiftmode_on` через announceForAccessibility из KeyboardSwitcher): по CONTEXT «без перфекционизма» — **в MVP не делать**. TalkBack перечитает сфокусированный узел после invalidateRoot; описание shift-клавиши уже state-зависимое. Deferred в backlog.
- **Password**: не озвучиваем набранное (нет announceForAccessibility вообще), описания не обскьюрим — § 2.

---

## 6. Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| A11y-дерево/фокус/hover-протокол | свой AccessibilityNodeProvider | ExploreByTouchHelper (уже используется) | Протокол hover→focus→click с TalkBack полон краевых случаев; helper их закрывает |
| Ввод при клике | ручной вызов listener-цепочки | синтез MotionEvent → processMotionEvent | Shift-машина/haptics/preview штатно; AOSP делает так же |
| Заглавные описания | 6 отдельных строк «заглавная …» | шаблон `spoken_description_upper_case` + `%s` | AOSP-паттерн, вдвое меньше строк на локаль |

## 7. Common Pitfalls

1. **Helper бросает RuntimeException на null contentDescription / пустых bounds** — stale-id ветка уже защищает (`:64-70`); при рефакторинге populate не потерять её.
2. **Индексы виртуальных id и спейсеры**: id = индекс в sortedKeys ВКЛЮЧАЯ спейсеры — фильтровать спейсеры можно только в getVisibleVirtualViews/populate/クリック-guard, но не менять нумерацию, иначе invalidateRoot-семантика ломается.
3. **Детект по iconName** — хрупко и англоязычно (текущий m3); детект строго по `key.getCode()` + elementId/imeAction.
4. **CODE_SHIFT на symbols-раскладке** — это #+=, не «регистр»: без elementId-ветки TalkBack скажет «верхний регистр» на клавише доп. символов.
5. **Забыть TYPE_VIEW_CLICKED / return true** — TalkBack сочтёт клик неуспешным, не даст earcon; у части версий — повторные попытки.
6. **Синтез клика по mHitbox.centerX()** (как AOSP) невозможен — `mHitbox` приватен в форке (`Key.java:111`, геттера getHitBox нет); центр видимой области (x+w/2) всегда внутри hitbox — использовать его, НЕ добавлять геттер в Key.java (минимальный Java-дифф = ноль).
7. **ё/ъ через long-press недоступны с TalkBack** — известное ограничение (§ 1), не пытаться чинить в этой фазе (MoreKeysKeyboardView — отдельная view-иерархия, отдельный делегат = не-MVP объём).

## 8. Plan Shape (для планировщика)

Одна волна, один план, минимальный дифф; Java-файлы форка — 0 строк диффа.

1. **Task 1 — строки + маппер**: `res/values/strings-a11y.xml` (en base: ~25 строк), дополнение `values-ru` (те же имена), новый `accessibility/KeyDescriptionMapper.kt` (Kotlin object: `getDescription(key, keyboard, context): String` — when по code → ресурсы; татарские буквы U+04D9/E9/AF/97/A3/BB + upper-case шаблон; фолбэк label; таблицы § 2).
2. **Task 2 — достройка делегата** (`KeyboardAccessibilityDelegate.kt`): populate → `KeyDescriptionMapper` вместо `label ?: iconName`; `node.isClickable = true`, `node.isTextEntryKey = true`; `onPerformActionForVirtualView` → синтез DOWN/UP + TYPE_VIEW_CLICKED (§ 4). KDoc класса обновить (снять «phase 9»-оговорку).
3. **Task 3 — верификация**: assembleDebug + assembleRelease + check-no-internet + APK-гейт ≤ 3 МБ; fail-capable-грепы: (a) в делегате нет `getIconName` (m3 закрыт), (b) `processMotionEvent` вызывается из onPerformActionForVirtualView, (c) все 6 кодпоинтов ә ө ү җ ң һ упомянуты в маппере, (d) `spoken_description_shift|delete|space|language_switch` есть в values/ И values-ru/, (e) touch-путь не тронут (diff по PointerTracker/KeyboardState/KeyboardView = 0). SC3 (реальный набор слова с TalkBack) — deferred UAT-бандл, чек-лист в SUMMARY по standing-паттерну фаз 1–8.

Границы фазы: изменяемые файлы = `KeyboardAccessibilityDelegate.kt`, новый `KeyDescriptionMapper.kt`, `values/strings-a11y.xml`, `values-ru/strings.xml` (или strings-a11y.xml). Всё.

## Package Legitimacy Audit

Новых пакетов фаза не устанавливает. Используется уже присутствующая `androidx.customview:customview:1.1.0` (введена фазой 6, пиннед; транзитив androidx.core:1.3.0 — `setTextEntryKey` в нём есть, verified javap). **Packages removed due to [SLOP]:** none. **Flagged [SUS]:** none.

## Runtime State Inventory

Не rename/refactor-фаза — раздел не применим. Prefs/данные не затрагиваются (описания — ресурсы, состояние — нет).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK/Gradle/AGP | сборка | ✓ (фазы 1–8 собирались) | Gradle 9.6.0 / AGP 9.2.1 | — |
| adb-устройство | SC3 TalkBack UAT | ✗ (standing: adb devices пуст) | — | Deferred UAT-бандл (принято в CONTEXT) |
| TalkBack | SC3 | ✗ (device-only) | — | то же |

**Missing без fallback:** нет (device-верификация штатно deferred).

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | нет (в проекте нет test/androidTest — конвенция всех фаз 1–8) |
| Config file | none |
| Quick run command | `./gradlew assembleDebug` |
| Full suite command | `./gradlew assembleDebug assembleRelease && scripts/check-no-internet.sh` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| A11Y-01 | каждый узел кликабелен, клик → ввод | structural | grep `processMotionEvent` в делегате + build | ✅ (grep) |
| A11Y-01 | узлы объявляются | manual-only (TalkBack) | — deferred UAT SC3 | — |
| A11Y-02 | описания татарских букв | structural | grep 6 кодпоинтов в маппере + строки в values/values-ru | ✅ (grep) |

### Sampling Rate
- Per task commit: `./gradlew assembleDebug`
- Phase gate: full build + грепы Task 3
- Manual-only justification: TalkBack-поведение проверяемо только на устройстве — deferred UAT-бандл (locked decision).

### Wave 0 Gaps
None — структурная верификация грепами (проектная конвенция), тестовая инфраструктура сознательно отсутствует.

## Security Domain

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2/V3/V4 | no | оффлайн-IME без auth |
| V5 Input Validation | marginal | virtualViewId → `getOrNull` + spacer-guard (уже в каркасе) |
| V6 Cryptography | no | — |

Единственный security-relevant аспект — **утечка вводимого в password-полях через озвучку**: IME не делает собственных announce (grep = 0), эхо — зона TalkBack/системы; описания клавиш не обскьюрим (обоснование § 2, post-O поведение AOSP). Разрешение INTERNET не появляется (чисто ресурсы + Kotlin, CI-гейт остаётся).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | ru-TTS корректно произносит одиночные кириллические буквы (label-фолбэк) | § 2 | Часть букв объявляется невнятно → добавить явные строки (лёгкий фикс постфактум по UAT) |
| A2 | Lift-to-type TalkBack маршрутизируется в ACTION_CLICK при isTextEntryKey | § 4 | Останется рабочий double-tap-ввод; lift-to-type — деградация UX, не блокер SC3 |
| A3 | TTS-покрытие ә ө ү җ ң һ ненадёжно → явные описания обязательны | § 2 | Если TTS их читает — явные описания всё равно корректны (перестраховка без вреда) |

## Open Questions

1. **Формулировка «татарская э» vs татарские названия букв («әлиф» и т.п.)** — выбрана русская описательная схема из требования (образец A11Y-02); татарские названия — вместе с values-tt в backlog. Recommendation: как в таблице § 2.
2. **Гласность заглавных обычных букв** — label уже заглавный, отдельного «заглавная А» не делаем (как AOSP для не-акцентированных). Если UAT покажет, что TalkBack читает «А» и «а» одинаково без пометки регистра — это system-wide поведение TalkBack, не наша зона.

## Sources

### Primary (HIGH confidence)
- Исходники форка (прочитаны в сессии): `KeyboardAccessibilityDelegate.kt`, `MainKeyboardView.java`, `KeyDetector.java`, `Key.java`, `KeyboardId.java`, `KeyboardState.java`, `PointerTracker.java`, `KeyboardParams.java`, `key_styles_common/enter/actions.xml`, `rowkeys_tatar*.xml`, `themes-tatar.xml`, `config-common.xml`
- AOSP LatinIME (файлы скачаны и прочитаны): `accessibility/KeyCodeDescriptionMapper.java`, `accessibility/KeyboardAccessibilityDelegate.java`, `values-ru/strings-talkback-descriptions.xml` — github.com/nxp-imx-android/aosp_platform_packages_inputmethods_LatinIME (mirror platform/packages/inputmethods/LatinIME)
- javap по androidx.core:1.3.0 / customview:1.1.0 из gradle-кэша: `setTextEntryKey`, `onPerformActionForVirtualView`, `sendEventForVirtualView` — API подтверждён

### Secondary (MEDIUM)
- 06-REVIEW.md (m3/m4/INFO-9) — независимое ревью каркаса

### Tertiary (LOW / [ASSUMED])
- Поведение lift-to-type TalkBack, TTS-покрытие татарской кириллицы — training knowledge, подтверждается device-UAT

## Metadata

**Confidence breakdown:**
- Аудит каркаса: HIGH — построчно по коду
- Маршрут ACTION_CLICK: HIGH — AOSP-исходник прочитан, публичность processMotionEvent проверена
- Схема описаний: HIGH (структура) / MEDIUM (формулировки — discretion)
- Динамика: HIGH — трасса setKeyboard→invalidateRoot проверена

**Research date:** 2026-07-18
**Valid until:** стабильный домен (AOSP a11y-паттерн 2011 года, форк заморожен) — 90+ дней
