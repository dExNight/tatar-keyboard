# Стек разработки и архитектура IME на Android

Ресерч для проекта татарской клавиатуры (кириллица + ә ө ү җ ң һ), стиль iOS, приоритет — лёгкость и отзывчивость на бюджетных устройствах. Дата: июль 2026.

---

## 1. Почему только нативный стек

Android-клавиатура — это не приложение с Activity, а **системный сервис**: класс, наследующий `android.inputmethodservice.InputMethodService`, объявленный в манифесте с intent-фильтром `android.view.InputMethod` и разрешением `android.permission.BIND_INPUT_METHOD`. Система (InputMethodManagerService) сама биндится к этому сервису, управляет его жизненным циклом, показом/скрытием окна и связью с полем ввода через `InputConnection`.

### Почему Flutter/React Native непригодны

1. **Нет точки входа.** Flutter и RN оборачивают своё дерево UI в Activity (`FlutterActivity` / `ReactActivity`). IME запускается системой как Service без Activity — фреймворки не предоставляют аналог `FlutterInputMethodService`, официальной поддержки IME нет ни у Flutter, ни у RN (по состоянию на 2026).
2. **`onCreateInputView()` обязан вернуть нативный `android.view.View`.** Теоретически можно вручную поднять FlutterEngine внутри сервиса и отдать `FlutterView`, но это недокументированный хак с массой проблем (жизненный цикл engine вне Activity, ввод касаний, IME-окно).
3. **Холодный старт и память.** Dart VM / JS-движок Hermes добавляют 100–200+ МБ RAM и сотни миллисекунд старта. Клавиатура должна появляться мгновенно при каждом фокусе поля, а система агрессивно убивает тяжёлые IME-процессы на устройствах с 2–4 ГБ RAM. Для цели «максимально лёгкая на бюджетниках» это дисквалифицирующий фактор.
4. **Латентность отрисовки.** Каждое нажатие — это touch → view → InputConnection IPC. Лишний слой (platform channels) добавляет задержку на каждом символе.

Допустимый гибрид: companion-приложение (настройки, онбординг) на чём угодно, но сам сервис клавиатуры — только Kotlin/Java. Для маленького проекта проще всё делать нативно.

### Kotlin vs Java в 2026

Однозначно **Kotlin**:
- Официально рекомендуемый язык Android с 2019; вся новая документация и примеры Google — на Kotlin.
- С **AGP 9.0 (январь 2026) поддержка Kotlin встроена прямо в Android Gradle Plugin** — отдельный плагин `org.jetbrains.kotlin.android` больше не обязателен (временный откат: `android.builtInKotlin=false`, в AGP 10 его уберут).
- Null-safety, корутины (фоновая загрузка словарей без ANR), data-классы для раскладок, `when` для обработки клавиш.
- Java нужна только если форкаете старый код: AOSP LatinIME / OpenBoard / HeliBoard — смесь Java+Kotlin+C++; FlorisBoard — чистый Kotlin.

### Инструменты (актуально на июль 2026)

| Инструмент | Версия | Примечание |
|---|---|---|
| Android Studio | **Quail 2 (2026.1.2)** stable | Quail 3/4 в preview |
| Android Gradle Plugin | **9.x** (в доках 9.3.0) | AGP 10 ожидается во 2-й половине 2026 |
| Kotlin | **2.3.21 – 2.4.x** | AGP 9.0.28+ для Kotlin 2.3 |
| Gradle | 8.14+ / 9.x | по compatibility matrix AGP |
| minSdk | рекомендую **24–26** | покрывает бюджетники; HeliBoard держит minSdk 21 |

UI: для максимальной лёгкости — **классические View / кастомный View с Canvas** (так делают AOSP LatinIME и HeliBoard: вся клавиатура — один View, клавиши рисуются на Canvas, а не по View на клавишу). Jetpack Compose в IME возможен (обёртка `AbstractComposeView` + ручное связывание `ViewTreeLifecycleOwner`/`SavedStateRegistryOwner` с сервисом), но тянет рантайм Compose в процесс клавиатуры и увеличивает старт/память — для цели проекта хуже. FlorisBoard использует Compose и заметно тяжелее.

### Когда нужен C++/NDK

Для первой версии — **не нужен**. NDK оправдан только для:
- **Словари и предикшен**: AOSP LatinIME хранит словари в бинарном trie-формате и обходит его нативным кодом (`libjni_latinime`) — миллионы узлов, JNI ради скорости и памяти. HeliBoard унаследовал это ядро.
- **Gesture typing (свайп)**: в AOSP библиотека жестов закрытая; HeliBoard подгружает её как внешний `.so` («swypelibs»).

Практический ориентир для татарской клавиатуры: начать без предикшена или с простым префиксным поиском по отсортированному списку частотных слов на Kotlin (словарь 50–100 тыс. слов в памяти — единицы МБ, бинарный поиск по префиксу — микросекунды). C++ подключать, только если упрётесь в память/скорость. Альтернатива — форкнуть HeliBoard и получить готовое нативное ядро словарей + добавить татарский словарь в формате AOSP (`.dict`, собирается утилитой dicttool из aosp).

---

## 2. Ядро: InputMethodService и его жизненный цикл

Сервис живёт в **собственном процессе приложения клавиатуры** и переживает переключения между полями ввода. Ключевые колбэки (в порядке вызова):

```
onCreate()                 — один раз при создании сервиса (система забиндилась).
                             Инициализация: загрузка настроек, запуск загрузки словаря.
onCreateInputView()        — создать и вернуть View клавиатуры. Вызывается один раз,
                             но пересоздаётся при смене конфигурации (поворот, тема).
onCreateCandidatesView()   — (опц.) полоса подсказок; чаще её делают частью input view.
onStartInput(EditorInfo, restarting)
                           — новое поле получило фокус; UI может быть ещё не показан.
                             Здесь читаем EditorInfo: тип поля, action, флаги.
onStartInputView(EditorInfo, restarting)
                           — клавиатура становится видимой для этого поля.
                             Здесь: выбрать раскладку (цифровая/текстовая), настроить
                             Enter-клавишу, сбросить состояние shift/composing.
onUpdateSelection(...)     — курсор/выделение изменились (в т.ч. пользователем).
onFinishInputView(finishingInput)
                           — клавиатура скрыта.
onFinishInput()            — поле потеряло фокус. Сбросить composing-состояние.
onDestroy()                — система убила сервис (нехватка памяти и т.п.).
```

Важно для бюджетных устройств: `onCreate` + `onCreateInputView` — критический путь холодного старта. Всё тяжёлое (словари) — асинхронно (корутина `Dispatchers.IO`), View — максимально простой.

Минимальный скелет:

```kotlin
class TatarImeService : InputMethodService() {

    override fun onCreateInputView(): View =
        KeyboardView(this).also { it.listener = ::onKey }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val inputClass = info.inputType and InputType.TYPE_MASK_CLASS
        val isPassword = /* см. раздел про пароли */
        // выбрать раскладку: text / number / phone; настроить label Enter
    }

    private fun onKey(char: String) {
        currentInputConnection?.commitText(char, 1)
    }
}
```

## 3. InputConnection — канал к полю ввода

`currentInputConnection` (может быть null!) — интерфейс к редактору, реализуемый приложением-получателем. Каждый вызов — IPC (Binder) в процесс приложения. Основные методы:

- `commitText(text, newCursorPosition)` — вставить финальный текст. `newCursorPosition = 1` — курсор после текста. Основной метод для обычных нажатий.
- `setComposingText(text, 1)` — «черновой» текст с подчёркиванием (для предикшена/автодополнения); заменяет предыдущий composing. `finishComposingText()` — зафиксировать.
- `deleteSurroundingText(beforeLength, afterLength)` — Backspace: `deleteSurroundingText(1, 0)`. Осторожно: длины в Java-char'ах; эмодзи и суррогатные пары — 2 char'а. Есть `deleteSurroundingTextInCodePoints()` (API 24+) — предпочтительнее.
- `getTextBeforeCursor(n, 0)` / `getTextAfterCursor(n, 0)` — контекст вокруг курсора (для автокапитализации, умного backspace, предикшена). Может вернуть null; не запрашивать много и часто — это IPC.
- `sendKeyEvent(KeyEvent)` — сырые key events (нужно для Enter/DEL в некоторых нестандартных полях, игр).
- `performEditorAction(actionId)` — нажать action Enter-клавиши (Search/Send/…).
- `beginBatchEdit()` / `endBatchEdit()` — сгруппировать несколько операций в одно обновление (меньше миганий и IPC-раундтрипов).

Правила надёжности: всегда проверять null; не доверять, что редактор корректно реализует всё (WebView и кастомные редакторы часто глючат с composing-текстом); критичные операции оборачивать в batch edit.

## 4. EditorInfo и imeOptions

`EditorInfo` приходит в `onStartInput(View)` и описывает поле:

- `inputType` — битовая маска: класс (`TYPE_CLASS_TEXT`, `TYPE_CLASS_NUMBER`, `TYPE_CLASS_PHONE`, `TYPE_CLASS_DATETIME`) + вариация (`TYPE_TEXT_VARIATION_EMAIL_ADDRESS`, `..._URI`, `..._PASSWORD` и т.д.) + флаги (`TYPE_TEXT_FLAG_CAP_SENTENCES`, `..._NO_SUGGESTIONS`, `..._AUTO_CORRECT`, `..._MULTI_LINE`). `inputType == 0` — поле не текстовое (не показывать полноценную раскладку либо fallback на sendKeyEvent).
- `imeOptions` — action для Enter: `IME_ACTION_GO / SEARCH / SEND / NEXT / DONE / PREVIOUS`, плюс флаги `IME_FLAG_NO_ENTER_ACTION` (Enter = перевод строки), `IME_FLAG_NO_FULLSCREEN`, `IME_FLAG_NO_EXTRACT_UI`, `IME_FLAG_NO_PERSONALIZED_LEARNING` (инкогнито — не учить словарь!).
- `actionLabel` / `actionId` — кастомная подпись action.
- `initialCapsMode`, а точнее `currentInputConnection.getCursorCapsMode(inputType)` — надо ли включить Shift (начало предложения).
- `hintLocales` (API 24+) — приложение может подсказать язык поля; можно автоматически переключаться на ТАТ/РУС.

Логика Enter-клавиши в стиле iOS: если задан action и нет `IME_FLAG_NO_ENTER_ACTION` — рисуем подпись («Найти», «Готово»...) и по нажатию вызываем `performEditorAction()`; иначе — `commitText("\n")` или `sendKeyEvent(KEYCODE_ENTER)`.

## 5. Регистрация IME: манифест и method.xml

`AndroidManifest.xml`:

```xml
<service
    android:name=".TatarImeService"
    android:label="@string/ime_name"
    android:permission="android.permission.BIND_INPUT_METHOD"
    android:directBootAware="true"
    android:exported="true">
    <intent-filter>
        <action android:name="android.view.InputMethod" />
    </intent-filter>
    <meta-data android:name="android.view.im"
               android:resource="@xml/method" />
</service>
```

`res/xml/method.xml` — метаданные IME и список подтипов:

```xml
<input-method xmlns:android="http://schemas.android.com/apk/res/android"
    android:settingsActivity="com.example.tatar.SettingsActivity"
    android:supportsSwitchingToNextInputMethod="true">
    <subtype
        android:label="@string/subtype_tatar"
        android:imeSubtypeLocale="tt_RU"
        android:languageTag="tt"
        android:imeSubtypeMode="keyboard"
        android:imeSubtypeExtraValue="KeyboardLayoutSet=tatar" />
    <subtype
        android:label="@string/subtype_russian"
        android:imeSubtypeLocale="ru"
        android:languageTag="ru"
        android:imeSubtypeMode="keyboard"
        android:imeSubtypeExtraValue="KeyboardLayoutSet=russian" />
    <subtype
        android:label="@string/subtype_english"
        android:imeSubtypeLocale="en_US"
        android:languageTag="en"
        android:imeSubtypeMode="keyboard"
        android:imeSubtypeExtraValue="KeyboardLayoutSet=qwerty" />
</input-method>
```

`android:isDefault="false"` по умолчанию; `settingsActivity` даёт кнопку-шестерёнку в системном списке клавиатур.

## 6. InputMethodSubtype и переключение языков (глобус)

- Каждый subtype = раскладка/язык. `imeSubtypeExtraValue` — ваша произвольная строка, по ней сервис выбирает, какую раскладку рисовать.
- Текущий subtype: колбэк `onCurrentInputMethodSubtypeChanged(subtype)` в сервисе — здесь перестраиваем раскладку.
- Переключение по глобусу внутри своих subtypes: `switchToNextInputMethod(false)` (метод InputMethodService, API 28+; до этого — через InputMethodManager с IBinder-токеном). Аргумент `onlyCurrentIme=true` — циклить только свои ТАТ→РУС→ENG; `false` — уходить и на другие клавиатуры.
- Показывать ли глобус: `shouldOfferSwitchingToNextInputMethod()` — система сама говорит, есть ли смысл (учитывает `supportsSwitchingToNextInputMethod` в method.xml). Долгое нажатие глобуса — системный пикер: `InputMethodManager.showInputMethodPicker()`.
- Пользователь может отключить часть subtypes в настройках системы (Языки ввода) — уважать выбор: `InputMethodManager.getEnabledInputMethodSubtypeList(null, true)`.
- Про локаль `tt`: татарский — валидный BCP-47 тег (`tt`, `tt-RU`), система его принимает; но системная строка названия локали на старых версиях может не знать «Tatar» — поэтому задавайте явный `android:label` для subtype, не `untranslatable name of locale`.

Механика в стиле iOS: одна клавиша-глобус слева внизу; короткое нажатие — следующий subtype, долгое — пикер. Плюс отдельная клавиша «АӘ»/«?123» для символьной раскладки (это НЕ subtype, а внутреннее состояние view).

## 7. Как пользователь включает клавиатуру

Трёхшаговый процесс (нельзя обойти программно — намеренно):

1. **Включить** в системных настройках: Settings → System → Languages & input → On-screen keyboard → Manage. Открыть этот экран из своего приложения: `startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))`.
2. **Системное предупреждение безопасности**: диалог «Этот метод ввода может собирать весь вводимый текст, включая пароли и номера карт». Это стандартный диалог для ЛЮБОЙ сторонней клавиатуры, его нельзя убрать. В онбординге стоит предупредить пользователя заранее и подчеркнуть, что клавиатура работает офлайн и без разрешения INTERNET (сильный аргумент доверия: у HeliBoard именно так).
3. **Выбрать** клавиатуру текущей: `InputMethodManager.showInputMethodPicker()` из companion-приложения, либо пользователь сам через иконку клавиатуры в navbar.

Типичный онбординг-экран: два шага с кнопками «Включить» (интент на настройки) и «Выбрать» (пикер), с проверкой статуса через `Settings.Secure.getString(cr, Settings.Secure.DEFAULT_INPUT_METHOD)` и `InputMethodManager.getEnabledInputMethodList()`.

## 8. Ограничения IME-процесса

- **Память**: система убивает жирные IME первыми; на 2 ГБ устройствах реальный бюджет — держаться в пределах ~30–50 МБ RSS. Не кэшировать битмапы тем без нужды, словари — memory-mapped или лениво.
- **Окно**: IME рисует только в своём окне (input view + candidates). Поверх приложений рисовать нельзя (кроме собственной панели). Высота — контролируете сами через layout.
- **Фоновая работа**: сервис живёт долго, но это не повод для фоновых задач; сеть в идеале вообще не использовать (и не декларировать INTERNET — маркетинговое и privacy-преимущество, плюс нечему утекать).
- **Стартовые ограничения Activity**: из IME нельзя свободно стартовать Activity на новых версиях Android (background activity launch restrictions) — настройки открывать с флагом NEW_TASK и лучше по явному действию пользователя.
- **Privacy-индикаторы**: начиная с Android 14+ усилен контроль доступа IME к буферу обмена и т.п.; `IME_FLAG_NO_PERSONALIZED_LEARNING` обязателен к соблюдению.

## 9. Fullscreen / extract mode в ландшафте

В ландшафте на маленьких экранах система по умолчанию переводит IME в **fullscreen mode**: клавиатура занимает весь экран, а сверху рисуется «extracted text» — копия редактируемого текста (`ExtractEditText`). Управление:

- `onEvaluateFullscreenMode(): Boolean` — переопределить и вернуть `false`, если хотите никогда не разворачиваться (так делает Gboard на нормальных экранах; для клавиатуры в стиле iOS это обычно правильное решение — iOS не имеет extract mode).
- Приложение может само запретить: `IME_FLAG_NO_FULLSCREEN` / `IME_FLAG_NO_EXTRACT_UI` в imeOptions.
- Если fullscreen оставляете — extract view система делает сама, но проверяйте `isFullscreenMode()` при работе с `getTextBeforeCursor` (в fullscreen InputConnection идёт к extract-полю).

Рекомендация: `override fun onEvaluateFullscreenMode() = false` + компактная раскладка для ландшафта. Меньше кода, ближе к iOS-поведению.

## 10. Поля паролей

Определение по `EditorInfo.inputType`:

```kotlin
val cls = inputType and InputType.TYPE_MASK_CLASS
val variation = inputType and InputType.TYPE_MASK_VARIATION
val isPassword =
    (cls == InputType.TYPE_CLASS_TEXT && (
        variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
        variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
        variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
    (cls == InputType.TYPE_CLASS_NUMBER &&
        variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
```

Обязательное поведение в password-полях:
- Отключить подсказки/предикшен/автокоррекцию и **не записывать ничего в пользовательский словарь/историю**.
- Не логировать ввод (вообще никогда, но здесь особенно).
- Обычно отключают и автокапитализацию, и переключение на «умные» фичи; голосовой ввод скрыть.
- То же самое при флаге `TYPE_TEXT_FLAG_NO_SUGGESTIONS` и `IME_FLAG_NO_PERSONALIZED_LEARNING` (incognito в Chrome).

## 11. Direct Boot

После перезагрузки до первого разблокирования устройство в режиме Direct Boot: доступно только **device protected storage**, а credential protected storage (обычные файлы приложения, SharedPreferences) — нет. При этом клавиатура ОБЯЗАНА работать — ей вводят PIN/пароль экрана блокировки.

- Пометить сервис `android:directBootAware="true"` в манифесте (см. пример выше). AOSP LatinIME и Gboard так и сделаны.
- Все данные, нужные до разблокировки (настройки темы/раскладки, раскладки), читать через `context.createDeviceProtectedStorageContext()`; либо при старте проверять `UserManager.isUserUnlocked` и до разблокировки работать с дефолтными настройками, без пользовательского словаря.
- Слушать `Intent.ACTION_USER_UNLOCKED` и после разблокировки перечитать полные настройки/словарь.
- Если сервис НЕ directBootAware, система на экране блокировки откатится на дефолтную клавиатуру — пользователь увидит «чужую» клавиатуру при вводе PIN. Для качественного IME флаг обязателен.

## 12. Практические выводы для проекта

1. **Стек**: Kotlin + классические View (кастомный Canvas-View на всю клавиатуру), AGP 9.x, Android Studio Quail, minSdk 24–26, без Compose, без NDK на старте, без INTERNET-permission.
2. **Форк vs с нуля**: итоговое решение сведено в **06-fork-ili-s-nulya.md, раздел «Дополнение: сведение решения по базе проекта»** — это единственный источник правды. Коротко: по умолчанию форк Simple Keyboard (rkkr, Apache-2.0, Java) с новым кодом на Kotlin; форк HeliBoard — если AOSP-предикшен нужен с первой версии и GPL-3.0 приемлема; писать с нуля не нужно (готовое «ядро в 1–2 тыс. строк» — это и есть Simple Keyboard). Рекомендация Kotlin (см. выше §1) относится к новому коду и с Java-базой Simple Keyboard совместима через interop.
3. Три subtype (tt_RU, ru, en_US) в method.xml, глобус через `switchToNextInputMethod(true)`, `onEvaluateFullscreenMode() = false`, `directBootAware=true`, честная обработка password-полей.
4. Татарские буквы ә ө ү җ ң һ — обычные code points BMP (кириллица, U+04D9, U+04E9, U+04AF, U+0497, U+04A3, U+04BB), никаких проблем с `commitText`; но для backspace по эмодзи использовать `deleteSurroundingTextInCodePoints`.

---

## Источники

- Официальный гайд «Create an input method» — https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method
- InputMethodService (API reference) — https://developer.android.com/reference/android/inputmethodservice/InputMethodService
- InputConnection (API reference) — https://developer.android.com/reference/android/view/inputmethod/InputConnection
- EditorInfo (API reference) — https://developer.android.com/reference/android/view/inputmethod/EditorInfo
- InputMethodSubtype — https://developer.android.com/reference/android/view/inputmethod/InputMethodSubtype
- Direct Boot — https://developer.android.com/privacy-and-security/direct-boot
- Android Studio releases (Quail) — https://developer.android.com/studio/releases и https://androidstudio.googleblog.com/2026/
- AGP releases / built-in Kotlin в AGP 9 — https://developer.android.com/build/releases/about-agp и https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/
- Совместимость Kotlin/AGP/Gradle — https://developer.android.com/build/kotlin-support , https://docs.gradle.org/current/userguide/compatibility.html
- HeliBoard (форк OpenBoard/AOSP, v4.0 июль 2026) — https://github.com/Helium314/HeliBoard , https://f-droid.org/packages/helium314.keyboard/
- FlorisBoard (Kotlin/Compose, beta) — https://github.com/florisboard/florisboard
- AOSP LatinIME (исходники словарного ядра) — https://android.googlesource.com/platform/packages/inputmethods/LatinIME/
- Compose внутри IME (обёртка AbstractComposeView) — https://medium.com/@maksymkoval1/implementation-of-a-custom-soft-keyboard-in-android-using-compose-b8522d7ed9cd
