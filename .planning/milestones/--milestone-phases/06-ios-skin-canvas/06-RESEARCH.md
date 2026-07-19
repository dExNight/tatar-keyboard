# Phase 6: iOS-скин — Research (аудит рендера форка)

**Date:** 2026-07-18
**Scope:** UI-01 (SC1–SC5). Аудит существующей отрисовки Simple Keyboard, маршрут внедрения iOS-палитры/геометрии/тени, каркас ExploreByTouchHelper, PERF-03.

---

## TL;DR / Вердикт

**Полная переписка KeyboardView НЕ нужна.** Форк уже рисует всю клавиатуру одним кастомным View на Canvas, ключи — через темизируемые Drawable + текст `canvas.drawText`. iOS-скин достигается почти целиком **XML-диффом**: новая тема (стили + цвета light/night + layer-list drawables с 1dp-тенью) поверх существующего механизма тем. Java-дифф ограничивается: (а) регистрацией темы в `KeyboardTheme.java`, (б) каркасом ExploreByTouchHelper, (в) 2–3 точечными PERF-фиксами в горячем пути. Dark mode «из коробки»: тема `LXX_System` уже переключается через квалификатор `values-night/` + `onConfigurationChanged` — наш скин наследует этот механизм без нового кода.

---

## 1. Архитектура существующей отрисовки

Цепочка (все файлы относительно `app/src/main/java/rkr/simplekeyboard/inputmethod/`):

```
LatinIME.onCreateInputView() → KeyboardSwitcher.onCreateInputView()
  → inflate по ContextThemeWrapper(mLatinIME, mKeyboardTheme.mStyleId)   [KeyboardSwitcher.java:97,101]
MainKeyboardView (final, extends KeyboardView) — touch + preview + spacebar-текст
KeyboardView (extends View) — весь рендер
```

### KeyboardView.onDraw (`keyboard/KeyboardView.java:211-228`)

- **Hardware-accelerated путь** (наш основной, API 24+): `onDrawKeyboard(canvas)` напрямую, offscreen-буфер не используется (:213-216).
- Software-путь: offscreen `Bitmap` + `mOffscreenCanvas` (:218-227) — legacy, остаётся как есть.
- **Важно:** в HW-пути на каждый `invalidate()` перерисовываются **все** ключи (`drawAllKeys || isHardwareAccelerated`, :273) — точечный `invalidateKey(Rect)` (:504-512) сужает только dirty-регион, который GPU клипует; сами вызовы onDrawKey идут по всем ключам. Это upstream-поведение (TODO на :272), для PERF-03 приемлемо — аллокаций нет, а drawRoundRect вне клипа дёшев.

### onDrawKeyboard → onDrawKey (:254-327)

Цикл `for (final Key key : keyboard.getSortedKeys())` (:280) → `onDrawKey`:
1. `canvas.translate(keyDrawX, keyDrawY)` (:311)
2. `key.selectBackgroundDrawable(mKeyBackground, mFunctionalKeyBackground, mSpacebarBackground)` (`keyboard/Key.java:884-898`) — выбор Drawable по `mBackgroundType` (NORMAL/FUNCTIONAL/SPACEBAR) + `setState(pressed)` → **state-list drawable сам меняет вид при нажатии**
3. `onDrawKeyBackground` (protected, :330-346) — `background.setBounds` + `draw(canvas)`
4. `onDrawKeyTopVisuals` (protected, :349-461) — label (`drawText`), hint label, icon

### MainKeyboardView.onDrawKeyTopVisuals (`keyboard/MainKeyboardView.java:523-538`)

Только добавка: alpha для altCode-ключей + `drawLanguageOnSpacebar` (:578-598) когда включено >1 subtype (у нас всегда 3 → рисуется).

### Тексты/шрифт

`paint.setTypeface(key.selectTypeface(params))`, дефолт `KeyDrawParams.mTypeface = Typeface.DEFAULT` (`keyboard/internal/KeyDrawParams.java:25`), тема задаёт `keyTypeface=normal` (`values/themes-common.xml:37`). **Roboto уже используется** — SC-требование по шрифту закрыто нулевым диффом. Кэш метрик — `TypefaceUtils` (`latin/utils/TypefaceUtils.java:34,54` — статические SparseArray-кэши, `static Rect`).

---

## 2. Система тем

### Как применяется тема

- `KeyboardTheme.java:44-51` — реестр из 6 тем (`KEYBOARD_THEMES`): System(5, **дефолт**), SystemBorder(6), Light(3), Dark(4), LightBorder(1), DarkBorder(2). Каждая = `R.style.KeyboardTheme_LXX_*`.
- `KeyboardSwitcher.updateKeyboardThemeAndContextThemeWrapper` (`keyboard/KeyboardSwitcher.java:95-104`) — оборачивает LatinIME в `ContextThemeWrapper(context, keyboardTheme.mStyleId)`; все View инфлейтятся из него.
- `KeyboardView` конструктор (:125-150) — `context.obtainStyledAttributes(attrs, R.styleable.KeyboardView, defStyle=R.attr.keyboardViewStyle, ...)` → фон View (`android:background`), `keyBackground`/`functionalKeyBackground`/`spacebarBackground` (drawable), текстовые цвета через `KeyVisualAttributes`. **Все цвета приходят из стиля, ни одного хардкода в Java.**
- Дополнительно `mCustomColor` (Settings.readKeyboardColor) подменяет фон для тем с `mCustomColorSupport=true` (:262-268) — у System-темы `false` (`KeyboardTheme.java:45`), нас не касается.

### Dark mode сегодня

Тема по умолчанию — **LXX_System**: цвета `*_lxx_system` определены дважды — `values/colors.xml:27-34` (светлые) и `values-night/colors.xml` (тёмные: фон `#ff303030`, ключ `#ff5a595b`). Переключение:
- `LatinIME.onConfigurationChanged` (`latin/LatinIME.java:308-321`) → `mKeyboardSwitcher.onConfigurationChanged()` (`KeyboardSwitcher.java:89-94`): пересоздаёт `ContextThemeWrapper`, вызывает `KeyboardLayoutSet.onKeyboardThemeChanged()` (сброс кэша) и на SDK < S принудительно `setInputView(onCreateInputView())`; на S+ полагается на live-reload ресурсов.

**Вывод:** механизм смены light/dark по системе уже есть и работает через resource-квалификатор `-night`. Наш путь: определить iOS-палитру как цветовые ресурсы в `values/` + `values-night/` и повесить их на новую (или системную) тему — динамика бесплатно.

### Минимальный путь для iOS light/dark

Вариант **«новая тема id=7»** (рекомендую, см. §7): `themes-tatar.xml` со стилями `KeyboardTheme.Tatar` → `KeyboardView.Tatar` (наши background/keyBackground/цвета), регистрация в `KeyboardTheme.KEYBOARD_THEMES` + `DEFAULT_THEME_ID = THEME_ID_TATAR`, элементы в `keyboard-themes.xml` (массивы для диалога настроек). Цвета `ios_*` — в `values/colors.xml` (light) и `values-night/colors.xml` (dark). Существующие 6 тем не трогаем → нулевой риск регрессий и остаётся выбор в настройках.

---

## 3. Отрисовка клавиши: фон, радиус, тень

### Сегодня

`keyBackground` = селектор `drawable/btn_keyboard_key.xml`: обычный ключ **прозрачный** (виден фон клавиатуры), при нажатии — `btn_keyboard_key_pressed_border` = shape roundRect `?attr/keyPressedBackgroundColor`, радиус `@dimen/button_corner_radius_lxx` = 6dp (`values/colors.xml:54`). Т.е. вся «геометрия ключа» уже данными в drawable-слое.

### iOS-ключ БЕЗ Java-кода — layer-list

Требуемый вид (заливка roundRect 5dp + резкая тень 0/+1dp/blur 0) выражается штатным layer-list:

```xml
<layer-list>
    <item android:top="1dp">                <!-- тень: сдвинутый вниз roundRect -->
        <shape android:shape="rectangle">
            <solid android:color="@color/ios_key_shadow"/>   <!-- #40000000 light / #B3000000 dark -->
            <corners android:radius="5dp"/>
        </shape>
    </item>
    <item android:bottom="1dp">             <!-- сама клавиша -->
        <shape android:shape="rectangle">
            <solid android:color="@color/ios_key_normal"/>
            <corners android:radius="5dp"/>
        </shape>
    </item>
</layer-list>
```

Обёрнуто в selector (pressed-состояния: обычная клавиша темнеет/служебная инвертируется в белую — просто другой solid-цвет). Три файла: `ios_key_normal.xml`, `ios_key_functional.xml`, `ios_key_spacebar.xml` — маппятся 1:1 на существующие атрибуты `keyBackground`/`functionalKeyBackground`/`spacebarBackground` — **хук по типу ключа уже существует**, Java не трогаем.

Зазор между клавишами обеспечивают существующие `horizontalGap`/`verticalGap` темы (drawable рисуется в границах ключа минус gap) — при необходимости подстроить фракции в стиле `Keyboard.Tatar`.

### Paint.setShadowLayer — отклонено

`setShadowLayer` при hardware acceleration гарантированно работает **только для текста**; для `drawRoundRect` требует `LAYER_TYPE_SOFTWARE` (убивает производительность — дисквалификация для бюджетников). Ручной «двойной roundRect» и так закодирован в layer-list выше; если позже уйдём на прямой Canvas (фаза 7, баллоны), паттерн — два `drawRoundRect` с общим `RectF`-полем. Решение из research/04 §2 подтверждено чтением кода.

---

## 4. ExploreByTouchHelper — каркас

### Факты

- В исходниках нет ни ExploreByTouchHelper, ни AccessibilityNodeProvider (grep пуст — подтверждено).
- **`dependencies {}` в `app/build.gradle` ПУСТ** — androidx отсутствует вообще. `ExploreByTouchHelper` живёт в `androidx.customview:customview` (тянет `androidx.core` + annotation, ~150–250 КБ в release с R8 — вписывается в бюджет 3 МБ, но это первая внешняя зависимость проекта).
- Touch обрабатывает **MainKeyboardView.onTouchEvent** (`MainKeyboardView.java:468-482`) → `PointerTracker.processMotionEvent`. Хелпер использует **hover**-события (`dispatchHoverEvent`), не touch → **не пересекается** с PointerTracker; при включённом TalkBack система сама транслирует explore-жесты в hover.

### Скелет (место: MainKeyboardView — прямая правка, там же где touch и KeyDetector)

```java
// поле
private KeyboardAccessibilityDelegate mA11yDelegate;   // extends ExploreByTouchHelper
// в setKeyboard(): mA11yDelegate.invalidateRoot() — ключи пересоздались
// в конструкторе: ViewCompat.setAccessibilityDelegate(this, mA11yDelegate)
@Override public boolean dispatchHoverEvent(MotionEvent e) {
    return mA11yDelegate.dispatchHoverEvent(e) || super.dispatchHoverEvent(e);
}
```

Провайдер (virtual view id = индекс в `keyboard.getSortedKeys()`):
- `getVirtualViewAt(x, y)` → `mKeyDetector.detectHitKey(...)` (уже есть) → индекс ключа
- `getVisibleVirtualViews(list)` → индексы всех не-spacer ключей
- `onPopulateNodeForVirtualView(id, node)` → `node.setContentDescription(key.getLabel()либо имя иконки)`, `node.setBoundsInParent(key.getHitBox())`, `ACTION_CLICK`
- `onPerformActionForVirtualView` → заглушка (полноценный ввод + A11Y-02-описания «татарская э» — фаза 9)

Риск для существующего поведения: нулевой — без TalkBack hover-события не приходят; touch-путь не изменён.

**Решение к плану:** добавить `androidx.customview:customview` (одна маленькая зависимость, официальный путь) и замерить размер APK; фолбэк, если размер неприемлем — отложить каркас до фазы 9 либо ручной `AccessibilityNodeProvider` (больше кода, ноль зависимостей). Рекомендация — androidx-путь.

---

## 5. Иконки (VectorDrawable)

Все 17 иконок форка — уже VectorDrawable в `res/drawable/sym_keyboard_*.xml`, регистр — `KeyboardIconsSet.java:61-81` (имя → R.drawable), тонировка через `?attr/functionalTextColor` внутри самих векторов (пример: `sym_keyboard_shift.xml` — stroke-стрелка path'ом; `sym_keyboard_delete.xml` — fill крест-в-пятиугольнике).

Фаза 6 перерисовывает **path-данные** (файлы те же, регистр не трогаем):
- `sym_keyboard_shift.xml` — контурная стрелка ⇧ (сейчас уже похожа: stroke path `M 12,3 1,15 h 7 v 7 h 8 v -7 h 7 z` — скорректировать пропорции под iOS);
- `sym_keyboard_shift_locked.xml` — залитая стрелка с чертой (⇪-подобная);
- shifted-состояние: `NAME_SHIFT_KEY_SHIFTED` уже маппится на отдельный drawable (`KeyboardIconsSet.java:77`) — залитая стрелка;
- `sym_keyboard_delete.xml` — форма уже «крест в пятиугольнике» (generic, совпадает с iOS-мотивом) — минимальная правка/оставить;
- `sym_keyboard_language_switch.xml` — глобус, перерисовать в тонкоконтурный;
- `sym_keyboard_return.xml` — по вкусу (у iOS текст, у нас допустима иконка/текст action-клавиши — форк умеет текстовые подписи action-клавиш через `strings-action-keys.xml`).

Источник форм: свои path'ы или Material Symbols (Apache-2.0) — юридически чисто (research/04 §9). Никаких SF Symbols.

Тонировка `?attr/functionalTextColor` разрешается из темы → в нашей теме задаём `#000` (light) / `#FFF` (night-оверрайд атрибутного цвета) — иконки перекрашиваются автоматически.

---

## 6. PERF-03: аллокации в горячем пути (полный список)

Аудит `onDraw` → `onDrawKeyboard` → `onDrawKey` → `onDrawKeyTopVisuals` (+ Main-оверрайд):

| # | Место | Аллокация | Тяжесть | Фикс |
|---|-------|-----------|---------|------|
| 1 | `KeyboardView.java:280` | `for (Key : getSortedKeys())` — **iterator** `List<Key>` на каждый onDrawKeyboard | Каждый кадр | Индексный `for (int i...)` — `getSortedKeys()` возвращает `List` (ArrayList → get(i) O(1)) |
| 2 | `KeyDrawParams.mayCloneAndUpdateParams` (`KeyDrawParams.java:126`) | `new KeyDrawParams(this)` для каждого ключа с не-null `KeyVisualAttributes` | Каждый кадр × N ключей с attrs | Кэшировать клон в самом `Key` (поле) или в SparseArray по key; либо переиспользуемый scratch-объект `mScratchDrawParams` (attrs ключей неизменны после построения Keyboard) |
| 3 | `MainKeyboardView.layoutLanguageOnSpacebar` (:558-576) | `LocaleResourceUtils.getLocaleDisplayNameInLocale(...)` — строки на **каждую отрисовку пробела** | Каждый кадр с redraw пробела | Кэшировать строку при `setKeyboard`/`startDisplayLanguageOnSpacebar` (subtype меняется редко) |
| 4 | `TypefaceUtils` (:34,54) | `Float` autoboxing при **первом** обращении на (размер×шрифт) | Одноразово, кэшируется | Не трогать |
| 5 | `KeyboardView.onDraw` software-путь (:241) | `Bitmap.createBitmap` | Только software-рендер, при resize | Не трогать |
| 6 | `newLabelPaint` (:471-483) | `new Paint()` | НЕ горячий путь (только построение moreKeys-панели) | Не трогать |

Поля уже правильные: `mPaint`, `mClipRect`, `mFontMetrics`, `mKeyBackgroundPadding`, `mInvalidatedKeys` — переиспользуемые (:108-116). `Rect`/`Paint` в цикле не создаются. Drawable.draw() (shape/layer-list) не аллоцирует после первого построения.

**Вердикт:** горячий путь почти чистый; **3 хирургических фикса** (#1–#3), никакой переписки. Фиксы #1–#2 — в KeyboardView.java, #3 — в MainKeyboardView.java; каждый ≤ 15 строк.

*Замечание:* `String.format`, `new Rect()`, `new Paint()` в onDraw-цепочке — **не найдены** (подтверждено чтением всей цепочки).

---

## 7. Маршрут реализации (рекомендация)

### Сравнение вариантов

| | (a) Subclass TatarKeyboardView | (b) Новая тема XML + точечные правки | (c) Полный новый View |
|---|---|---|---|
| Java-дифф | Средний; **блокер: `MainKeyboardView` объявлен `final`** (:61) — придётся снимать final + менять layout-ссылки | Минимальный (KeyboardTheme реестр + a11y + 3 PERF-фикса) | Огромный, месяцы, потеря PointerTracker-механики |
| XML-дифф | Тот же, что (b) | стили/цвета/drawables | всё заново |
| Риск регрессий фаз 2–5 | Низкий, но layout-правки | **Минимальный** — механика не тронута | Максимальный |
| Dark mode | вручную | **бесплатно** (values-night) | вручную |

**Выбор: (b).** Ключевой инсайт аудита: iOS-вид (палитра, радиус 5dp, 1dp-тень, разделение normal/functional/spacebar) полностью выражается существующей системой тем + layer-list drawables — «оверрайдить onDraw» не нужно вовсе, потому что весь нужный рендер уже параметризован данными. Ровно в духе конституции проекта («раскладки/вид — данными»). Canvas-требование UI-01 выполняется существующей архитектурой: один View, весь рендер на Canvas (drawable.draw(canvas) + drawText).

### Файловый список (ожидаемый дифф)

**Новые файлы:**
- `res/values/themes-tatar.xml` — стили `KeyboardTheme.Tatar`, `KeyboardView.Tatar`, `MainKeyboardView.Tatar`, `MoreKeysKeyboardView.Tatar` (по образцу themes-lxx-system.xml)
- `res/drawable/ios_key_normal.xml`, `ios_key_functional.xml`, `ios_key_spacebar.xml` — selector→layer-list (тень+ключ+pressed)
- `java/.../accessibility/KeyboardAccessibilityDelegate.kt` (Kotlin! новый код) — ExploreByTouchHelper-скелет

**Правки:**
- `res/values/colors.xml` — блок `ios_*`: `#D4D6DD / #FFFFFF / #B3B7C0 / #40000000` + pressed-цвета
- `res/values-night/colors.xml` — блок `ios_*`: `#2C2C2C / #6B6B6B / #474747 / #B3000000`
- `res/values/keyboard-themes.xml` — тема в массивы выбора
- `res/values/attrs.xml` — enum `themeId` +Tatar (если Case.keyboardTheme используется в layout-XML — проверить при планировании)
- `res/values/strings.xml` (donottranslate) — имя темы
- `res/drawable/sym_keyboard_shift.xml`, `sym_keyboard_shift_locked.xml`, `sym_keyboard_language_switch.xml` (+ по необходимости `sym_keyboard_delete.xml`, `sym_keyboard_return.xml`) — новые path
- `keyboard/KeyboardTheme.java` — `THEME_ID_TATAR = 7`, запись в `KEYBOARD_THEMES`, `DEFAULT_THEME_ID = THEME_ID_TATAR`
- `keyboard/KeyboardView.java` — PERF-фиксы #1, #2
- `keyboard/MainKeyboardView.java` — PERF-фикс #3; подключение a11y-делегата (поле + dispatchHoverEvent + invalidateRoot в setKeyboard)
- `app/build.gradle` — `androidx.customview:customview` (+ проверка размера APK)

### Риски

1. **androidx впервые в проекте** — рост APK; замерить, фолбэк описан в §4.
2. **`values-night` для тем LXX_Light/Dark** — наши `ios_*`-цвета в night перекрасят ТОЛЬКО тему Tatar (остальные ссылаются на свои цвета) — риска нет, но проверить, что `?attr/functionalTextColor` в перерисованных иконках корректен во всех 7 темах (иконки общие!). Path-правки менять форму, не механизм тонировки.
3. **Live-reload на SDK ≥ S** (`KeyboardSwitcher.java:91-93` пересоздаёт View только на < S) — на Android 12+ проверить смену темы без пересоздания; если layer-list-цвета не подхватываются, снять условие `< S` (1 строка, low-risk).
4. **Пятый ряд = 11–12 клавиш в ряду** — gap 6dp может съесть ширину; фракции gap в стиле `Keyboard.Tatar` подстраиваются данными (research/04 §1).
5. **Тень 1dp «съедает» 1dp высоты ключа** (item bottom-inset) — визуально это и есть iOS-паттерн; проверить на клавишах 5-рядной раскладки (высота ряда 16.667%p из фазы 2).
6. Dev-prefs могут хранить старую тему (`pref_keyboard_theme_20140509`) — на dev-устройстве после установки выбрать тему/сбросить prefs; для новых пользователей DEFAULT_THEME_ID достаточно.

### Что НЕ делаем в фазе 6

- Баллон-превью, long-press-панель, хаптика (фаза 7 — но заметить: `KeyPreviewChoreographer`/`DrawingPreviewPlacerView` уже есть у форка, фаза 7 их перекрасит той же темой).
- Полный TalkBack (фаза 9), замеры PERF (фаза 11).

---

## Ответы на research questions (кратко)

1. **Rendering:** один View, HW-путь рисует все ключи каждый invalidate; drawable-фоны + drawText; почти allocation-free, 3 найденных аллокации — §6.
2. **Themes:** style-реестр `KeyboardTheme` + `ContextThemeWrapper`; dark mode уже работает через `values-night` + `onConfigurationChanged`; путь — новая тема id=7 — §2, §7.
3. **Key draw:** layer-list (тень offset 1dp + roundRect 5dp) вместо setShadowLayer (не работает для фигур на HW) — §3; Roboto уже дефолт.
4. **ExploreByTouchHelper:** в MainKeyboardView через androidx.customview, hover-события не конфликтуют с PointerTracker — §4.
5. **Icons:** 17 готовых VectorDrawable, тонируются `?attr/functionalTextColor`; перерисовать path shift/shift_locked/globe (+delete/return опц.) — §5.
6. **PERF-03:** таблица §6; хирургия, не переписка.
7. **Minimal diff:** вариант (b); subclass отпадает (`MainKeyboardView` — `final`), и onDraw-оверрайд не нужен вовсе — §7.

---
*Phase: 06-ios-skin-canvas | Research complete: 2026-07-18*
