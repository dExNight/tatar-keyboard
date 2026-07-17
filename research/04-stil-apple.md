# Визуальный стиль клавиатуры iOS и его воспроизведение на Android

> Ресерч для татарской клавиатуры (IME) на Android. Дата: июль 2026.
> Важно: Apple **не публикует** официальные спецификации системной клавиатуры (размеры клавиш, радиусы, hex-цвета). Все цифры ниже — reverse-engineering сообщества (скриншоты @3x, проект KeyboardKit, zoul/ios-keyboards). Они стабильны с iOS 7 по iOS 17+ с минимальными изменениями, но помечены как «приближение».

---

## 1. Геометрия (iPhone, портрет)

Базовые размеры в **pt** (iOS points). Для Android переводим 1 pt ≈ 1 dp — плотности совпадают по замыслу (@2x/@3x ↔ xhdpi/xxhdpi).

| Параметр | Значение (pt/dp) | Примечание |
|---|---|---|
| Высота клавиатуры (4 ряда, без панели подсказок) | **216** | подтверждено zoul/ios-keyboards и Apple dev forums; на iPhone 6 Plus — 226 |
| Панель подсказок (QuickType) | ~**44–45** | сверху, итого ~260 |
| Высота клавиши | ~**42–43** | |
| Ширина буквенной клавиши | ~**31.5–33** | (375 − 9×6 − 2×3) / 10 при ширине экрана 375 |
| Горизонтальный зазор между клавишами | **6** | |
| Вертикальный зазор | ~**10–12** (шаг ряда ~54) | |
| Боковой отступ от края экрана | **3** | |
| Радиус скругления клавиши | ~**4.6–5** | в CSS-реконструкциях используют 5px; на @3x скриншотах ~4.6 pt |
| Тень клавиши | смещение **0 / +1**, blur 0 | резкая «подложка» снизу, не размытая тень |

Практический вывод для Android: задать высоту клавиатуры не жёстко в dp, а как **долю экрана (~35% высоты в портрете)** с минимумом ~200dp и настройкой пользователем — на бюджетных устройствах с мелкими экранами фиксированные 260dp съедают пол-экрана.

Ряды (латиница QWERTY; для татарской кириллицы будет 11–12 клавиш в ряду — зазоры придётся ужать до 4–5dp или уменьшить боковые отступы):

- Ряд 1: 10 клавиш во всю ширину.
- Ряд 2: 9 клавиш, центрирован (отступ по полклавиши).
- Ряд 3: Shift + 7 букв + Backspace; Shift/Backspace шире (~42–44 pt) и прижаты к краям.
- Ряд 4: `123` (~91 pt) + глобус + пробел (~40% ширины) + Return (~91 pt).

## 2. Цвета

Наиболее надёжный публичный источник — ассеты **KeyboardKit** (open-source Swift-библиотека, цвета сняты с реальной iOS-клавиатуры): `Sources/KeyboardKit/Resources/Colors.xcassets` (тег 6.9.4).

### Светлая тема

| Элемент | Hex | Источник |
|---|---|---|
| Фон клавиатуры | **#D4D6DD** (KeyboardKit: rgb 0.835/0.839/0.867 ≈ #D5D6DD; в CSS-реконструкциях #D1D5DB) | KeyboardKit / Jon Kantner |
| Обычная клавиша | **#FFFFFF** (на скриншотах чуть тёплый ~#FCFCFE) | KeyboardKit |
| Служебная клавиша (Shift, Backspace, 123, глобус) | **#B3B7C0** (KeyboardKit: 0xB3/0xB7/0xC0; реконструкции: #ACB3BD) | KeyboardKit |
| Тень клавиши | **#000000, alpha 0.30**, offset y=1 | KeyboardKit (`standardButtonShadow`) |
| Текст/иконки | **#000000** | |
| Нажатая служебная клавиша | становится белой (#FFFFFF) — инверсия с обычной | наблюдение |

### Тёмная тема

Настоящая тёмная клавиатура iOS **полупрозрачная** (blur поверх контента). KeyboardKit даёт непрозрачные эквиваленты — для Android это то, что нужно:

| Элемент | Hex | Источник |
|---|---|---|
| Фон клавиатуры | **#2C2C2C** (0.173) | KeyboardKit |
| Обычная клавиша | **#6B6B6B** | KeyboardKit |
| Служебная клавиша | **#474747** | KeyboardKit |
| Тень клавиши | **#000000, alpha 0.70**, offset y=1 | KeyboardKit |
| Текст/иконки | **#FFFFFF** | |
| Нажатая служебная клавиша | светлеет до цвета обычной (#6B6B6B) | наблюдение |

Реализация на Android: клавиша = `GradientDrawable`/shape с `cornerRadius=5dp` + отдельный слой тени. Стандартный `android:elevation` даёт размытую Material-тень — **не то**. Правильно: либо layer-list из двух shape (нижний — чёрный с альфой, смещён на 1dp вниз), либо рисовать на Canvas два `drawRoundRect` (тень 1dp ниже, затем клавиша). Отрисовка одним кастомным View на Canvas (как в AOSP `Keyboard`/LatinIME) — самый быстрый вариант для слабых устройств: одна View на всю клавиатуру вместо ~35 View-кнопок.

## 3. Шрифт

- iOS использует **SF Pro** (San Francisco): буквы на клавишах ~**22–25 pt** Regular (не Bold), подписи служебных клавиш («АБВ», «пробел») ~16 pt, подсказки ~18 pt.
- **SF Pro использовать на Android НЕЛЬЗЯ** — см. раздел 8.
- Легальные замены:
  - **Roboto** (Apache 2.0, системный шрифт Android) — нулевой размер APK, «нативно» для Android, ~78% визуального сходства с SF Pro. **Рекомендация для лёгкого IME: `Typeface.DEFAULT` (Roboto/системный)** — 0 байт в APK, гарантированная поддержка кириллицы включая ә ө ү җ ң һ.
  - **Inter** (SIL OFL 1.1) — самый близкий свободный аналог SF (метрики и формы делались с оглядкой на SF/Helvetica). Полная кириллица, включая расширенную (ә җ ң ө ү һ присутствуют). Цена — ~300–800 КБ в APK (можно субсетировать до ~50 КБ через `pyftsubset`). Подключение: `res/font/inter_regular.ttf` + `ResourcesCompat.getFont()`.
- Проверить обязательно: отображение Ә/ә, Җ/җ, Ң/ң, Һ/һ, Ө/ө, Ү/ү в выбранном шрифте на minSdk-устройстве. В Roboto все эти глифы есть начиная с давних версий Android; в кастомном шрифте — проверять глазами, иначе получите tofu (□).

## 4. Баллон-попап над нажатой клавишей

Геометрия iOS: при нажатии буквенной клавиши **мгновенно** (без анимации появления) над ней вырастает баллон — увеличенная копия клавиши, соединённая с ней плавной «шеей». Символ в баллоне ~в 1.7–2 раза крупнее. Баллон белый (светлая тема) / цвета клавиши (тёмная), с той же тенью. У крайних клавиш баллон асимметричный (не вылезает за экран). Исчезает мгновенно при отпускании. На iPad баллонов нет — только затемнение клавиши.

Android-реализация в IME:

- Классический путь — `PopupWindow` поверх вью клавиатуры (так делал устаревший `android.inputmethodservice.KeyboardView`, deprecated с API 29 — брать как референс кода, не как зависимость).
- **Подводный камень**: окно IME по высоте равно клавиатуре, и попап, выходящий за верхнюю границу, будет обрезан. Решения: (а) `setClippingEnabled(false)` у PopupWindow; (б) как в Gboard/AOSP LatinIME — сделать вью клавиатуры выше на высоту баллона с прозрачной верхней полосой и рисовать баллон прямо на своём Canvas (быстрее и надёжнее, рекомендую для слабых устройств).
- Учесть настройку «показывать попап» (на маленьких экранах его часто отключают).
- Long-press на клавише → баллон расширяется в ряд альтернатив (для татарской: долгое нажатие на а → ә, о → ө, у → ү, ж → җ, н → ң, х → һ — если решите дублировать доступ). Выбор — скольжением пальца, выделенная альтернатива подсвечивается синим (#007AFF — системный tint iOS).

## 5. Анимации нажатия

У iOS их почти нет — в этом и есть «отзывчивость»:

- Буквенная клавиша: **никакого** scale/ripple — просто мгновенный баллон.
- Служебная клавиша: мгновенная смена цвета фона (инверсия, см. §2), без задержки и fade.
- Отпускание: возврат за 1 кадр; допустим fade-out баллона ~50 мс.
- На Android: **отключить ripple** (`android:background` без `?attr/selectableItemBackground`), реакция в `MotionEvent.ACTION_DOWN`, а не `ACTION_UP`/`onClick` — ввод символа и попап на DOWN-фазе (так делают все IME; commit текста можно на UP, но визуал — на DOWN). Никаких `ObjectAnimator` на каждое нажатие — на бюджетных устройствах это джанк.

## 6. Звук и вибрация (Android API)

Звук клика — системные звуки клавиатуры через `AudioManager`, свои mp3 не нужны:

```kotlin
val am = getSystemService(AUDIO_SERVICE) as AudioManager
// вызывать на ACTION_DOWN
when (keyCode) {
    KEYCODE_DELETE -> am.playSoundEffect(AudioManager.FX_KEYPRESS_DELETE, -1f)
    KEYCODE_ENTER  -> am.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN, -1f)
    KEYCODE_SPACE  -> am.playSoundEffect(AudioManager.FX_KEYPRESS_SPACEBAR, -1f)
    else           -> am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, -1f)
}
```

iOS тоже различает звук обычных клавиш, backspace и служебных — маппинг выше это повторяет. Громкость `-1f` = системная. Уважать `Settings.System.SOUND_EFFECTS_ENABLED` (playSoundEffect сам это делает) и дать выключатель в настройках.

Вибрация — по возрастанию качества:

```kotlin
// 1. Базовый и самый дешёвый (рекомендуется по умолчанию):
view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
// API 27+: KEYBOARD_PRESS (=KEYBOARD_TAP) на DOWN и KEYBOARD_RELEASE на UP

// 2. Тонкая настройка (API 26+/29+):
val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)) // API 29
// или слабее: EFFECT_TICK; fallback для старых API — vibrate(VibrationEffect.createOneShot(20, DEFAULT_AMPLITUDE))
```

`performHapticFeedback` автоматически уважает системную настройку хаптики; на дешёвых устройствах без хорошего вибромотора `EFFECT_CLICK` может отрабатывать как грубая вибрация — `KEYBOARD_TAP` безопаснее. iOS-ощущение = очень короткий, тихий тик на каждое нажатие; по умолчанию у iOS хаптика клавиатуры **выключена** (появилась как опция в iOS 16) — включённая по умолчанию слабая вибрация допустима и привычнее на Android.

## 7. Служебные клавиши

### Shift / Caps Lock — три состояния

| Состояние | Иконка | Фон клавиши (светлая тема) | Буквы на клавишах |
|---|---|---|---|
| off | контурная стрелка ⇧ | серый #B3B7C0 | строчные |
| shift (одноразовый) | залитая чёрная стрелка ⇧ | **белый** #FFFFFF | ЗАГЛАВНЫЕ |
| caps lock (двойной тап) | залитая стрелка с чертой под ней (⇪-подобная) | белый | ЗАГЛАВНЫЕ |

Ключевые поведения: двойной тап по Shift = Caps Lock; с iOS 9 **буквы на клавишах меняют регистр** вместе с состоянием (до этого всегда были заглавные); авто-shift в начале предложения; после ввода буквы одноразовый shift сбрасывается. Иконки рисовать самим (VectorDrawable) — **не** использовать SF Symbols (лицензия, см. §8); в Material Symbols есть `shift`/`shift_lock`, либо простой path-треугольник со «хвостом».

### Клавиша-глобус

- iOS: слева внизу; тап — переключение на следующую раскладку, long-press — меню раскладок.
- Android-эквиваленты:
  - Тап: `switchToNextInputMethod(false)` (метод `InputMethodService`, API 28+; до этого через `InputMethodManager.switchToNextInputMethod(token, false)`).
  - Long-press: `InputMethodManager.showInputMethodPicker()` — системное меню выбора IME.
  - `shouldOfferSwitchingToNextInputMethod()` — показывать ли глобус вообще (если пользовательская конфигурация не предполагает переключение, Gboard прячет глобус — стоит повторить).

### Return / Backspace

- Return: подпись текстом («ввод»/«перейти»/«поиск») по `EditorInfo.imeOptions` (`IME_ACTION_SEARCH` → «поиск» и т.д.); в состоянии action-цвета iOS красит её синим #007AFF с белым текстом.
- Backspace: контурный крест-в-пятиугольнике; автоповтор при удержании (начало через ~500 мс, затем ~50 мс/символ, с ускорением до удаления словами — на iOS через ~2 сек начинают удаляться слова целиком).

## 8. Ряд подсказок (QuickType)

- Высота ~44–45 pt, фон = фон клавиатуры (в тёмной — чуть темнее клавиш), **3 ячейки**, разделённые вертикальными hairline-разделителями (1px, ~20% высоты отступ сверху/снизу).
- Центральная ячейка — главный кандидат автокоррекции; если это буквальный ввод пользователя, он показан «в кавычках».
- Тап по ячейке — мгновенная вставка + пробел; фон ячейки при нажатии слегка темнеет (светлая тема) — прямоугольник со скруглением ~4–5 pt.
- Шрифт ~18 pt Regular, главный кандидат может быть чуть жирнее.
- Для MVP татарской клавиатуры ряд можно сделать пустым контейнером фиксированной высоты (стабильная высота клавиатуры) и подключить словарь позже.

## 9. Юридическая часть: где безопасная граница

### Шрифт SF Pro — жёсткий запрет

Лицензия Apple Font (SF Pro): использовать можно **только** для мокапов интерфейсов ПО под iOS/iPadOS/macOS/tvOS/watchOS/visionOS и только зарегистрированным Apple-разработчикам. Прямо запрещено: использование для ПО под не-Apple ОС, **встраивание шрифта в любые программы**, перераспространение. Встраивание SF Pro (или его пиратских клонов «San Francisco Pro» с шрифтовых сайтов) в Android APK — прямое нарушение лицензии с реальным риском (Apple активно защищает шрифт). То же касается **SF Symbols** (иконки): их лицензия запрещает использование вне Apple-платформ — все иконки (shift, backspace, глобус) рисуем сами или берём Material Symbols (Apache 2.0).

### Trade dress: прецедент Apple v. Samsung

- В деле Apple v. Samsung (2011–2018) присяжные изначально дали Apple >$1 млрд, но Федеральный окружной суд в 2015 **отменил** именно trade-dress-часть: UI-элементы признаны **функциональными**, а функциональный дизайн trade-dress-защите не подлежит (тест Disc Golf). Раскладка клавиатуры, форма клавиш, зазоры — это usability-driven элементы, самая слабая позиция для иска по trade dress.
- Реальный риск шёл от **design patents** (там возможно взыскание всей прибыли нарушителя, §289), но патентуются конкретные орнаментальные решения, а базовая сетка «скруглённые прямоугольники с зазорами на сером фоне» используется всеми клавиатурами (Gboard, SwiftKey, Samsung Keyboard выглядят близко к iOS-стилю годами без исков).

### Практическая безопасная граница

Можно (низкий риск):
- Цветовая палитра «серый фон / белые клавиши / серые служебные», радиусы ~5dp, зазоры, резкая 1dp-тень, баллон-попап, три состояния Shift — это функциональные, общеотраслевые паттерны.
- Roboto или Inter вместо SF Pro.
- Собственные векторные иконки «по мотивам» (стрелка shift, глобус, крест backspace — сами по себе давно generic и используются во всех клавиатурах, включая старый AOSP KeyboardView).

Нельзя / не стоит (заметный риск):
- Файлы SF Pro / SF Symbols в APK — нарушение лицензии, не «серая зона».
- Пиксель-в-пиксель копии ассетов Apple (вытащенные из iOS PNG/PDF иконки, звуки клика из iOS) — это уже копирайт на конкретные произведения, он работает независимо от trade dress.
- Маркетинг со словами «iOS keyboard», «iPhone-клавиатура», скриншоты с айфоном, яблоко в иконке — риск по товарным знакам + отклонение в Google Play (impersonation). Формулировка «минималистичный светлый стиль» — ок, «клавиатура как на iPhone» в сторе — не ок.

Итого: «в стиле iOS» = своя реализация с похожей геометрией и палитрой, свой шрифт, свои иконки, свои звуки (или системные Android-звуки), нейтральный маркетинг.

## 10. Чек-лист реализации (сводка решений)

1. Одна кастомная View, отрисовка клавиш на Canvas (`drawRoundRect` ×2: тень + клавиша), без 35 дочерних View и без ripple.
2. Палитра из §2 в двух темах; переключение по `Configuration.UI_MODE_NIGHT_MASK`.
3. `Typeface.DEFAULT` (Roboto), размер буквы ~22sp, служебные подписи ~14–16sp.
4. Попап-баллон на собственном Canvas (верхняя прозрачная зона вью), long-press → альтернативы ә ө ү җ ң һ.
5. Реакция на `ACTION_DOWN`: `playSoundEffect(FX_KEYPRESS_*)` + `performHapticFeedback(KEYBOARD_TAP)`; настройки вкл/выкл звука и вибры.
6. Shift: off/on/caps-lock, двойной тап = caps, регистр букв на клавишах меняется.
7. Глобус: тап → `switchToNextInputMethod`, long-press → `showInputMethodPicker()`; прятать по `shouldOfferSwitchingToNextInputMethod()`.
8. Высота клавиатуры — процент от экрана с пользовательской настройкой.
9. Никаких файлов SF Pro/SF Symbols/ассетов Apple в проекте; в описании в Play — без упоминания Apple/iPhone/iOS.

---

## Источники

- [zoul/ios-keyboards — размеры системной клавиатуры iOS по устройствам](https://github.com/zoul/ios-keyboards) — высота 216 pt (портрет iPhone), 226 pt (6 Plus).
- [Apple Developer Forums — keyboard height on iPhone X](https://developer.apple.com/forums/thread/90061) — базовая высота 216 pt + нижний inset.
- [KeyboardKit (GitHub), Colors.xcassets, тег 6.9.4](https://github.com/KeyboardKit/KeyboardKit/tree/6.9.4/Sources/KeyboardKit/Resources/Colors.xcassets) — hex-значения цветов клавиш/фона/тени для светлой и тёмной темы (сняты с реальной iOS-клавиатуры).
- [KeyboardKit issue #305 — keyboardAppearance vs dark mode](https://github.com/KeyboardKit/KeyboardKit/issues/305) — нюансы тёмной темы клавиатурных расширений iOS.
- [Jon Kantner — iOS Keyboard Recreated in HTML/CSS](https://blog.jonkantner.com/2015/11/ios-keyboard-recreated-html-css-jquery/) — независимое подтверждение палитры (#D1D5DB, #ACB3BD), радиуса 5px и тени 0 1px 0.
- [Apple — Fonts (условия лицензии SF)](https://developer.apple.com/fonts/) и [текст лицензии SF Pro](https://www.scribd.com/document/361217740/SF-Pro-Font-License) — запрет использования вне Apple-платформ и встраивания в ПО.
- [Apple Developer Forums — Can we use SF Pro fonts in apps?](https://developer.apple.com/forums/thread/719561) — «только для мокапов, не встраивать».
- [Wikipedia — San Francisco (typeface)](https://en.wikipedia.org/wiki/San_Francisco_(sans-serif_typeface)) — история и лицензионные ограничения.
- [Inter — SIL OFL, ближайший свободный аналог SF](https://mattwestcott.org/blog/an-ode-to-the-inter-typeface), [сравнение Roboto vs SF Pro](https://fontalternatives.com/compare/roboto-vs-sf-pro-display/).
- [Apple Inc. v. Samsung Electronics — Wikipedia](https://en.wikipedia.org/wiki/Apple_Inc._v._Samsung_Electronics_Co.) и [текст решения CAFC 2015](https://cyber.harvard.edu/people/tfisher/IP/2015_Apple_Abridged.pdf) — отмена trade-dress-вердикта из-за функциональности UI.
- [Katten — Apple-Samsung Trade Dress Case](https://katten.com/Apple-Samsung_Trade_Dress_Case_Demonstrates_Potential_Value_of_Design_Patents), [Revision Legal — Lessons in Trade Dress](https://revisionlegal.com/trademark/trademarks/lessons-trademarking-trade-dress-apple-vs-samsung/) — анализ рисков.
- Android API (developer.android.com): [HapticFeedbackConstants](https://developer.android.com/reference/android/view/HapticFeedbackConstants) (KEYBOARD_TAP/PRESS/RELEASE), [VibrationEffect](https://developer.android.com/reference/android/os/VibrationEffect) (EFFECT_CLICK/TICK, API 29), [AudioManager.playSoundEffect](https://developer.android.com/reference/android/media/AudioManager#playSoundEffect(int)) (FX_KEYPRESS_*), [InputMethodService](https://developer.android.com/reference/android/inputmethodservice/InputMethodService) (switchToNextInputMethod, shouldOfferSwitchingToNextInputMethod), [InputMethodManager.showInputMethodPicker](https://developer.android.com/reference/android/view/inputmethod/InputMethodManager#showInputMethodPicker()).
