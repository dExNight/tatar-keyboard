# Оптимизация татарской клавиатуры под слабые устройства

Дата ресерча: 18.07.2026. Контекст: нативный Android IME (InputMethodService), кириллическая татарская раскладка, стиль iOS, цель — бюджетные устройства (1–4 ГБ RAM, Android Go).

---

## 1. Бюджет памяти IME-процесса

### Реальные цифры (ориентиры, методики замеров у источников разные)

| Клавиатура | RAM (PSS, порядок величины) | Комментарий |
|---|---|---|
| Gboard | ~65–70 МБ | Полная версия, с ML-подсказками, GIF, стикерами |
| Gboard Go | ~40 МБ | Lite-версия для Android Go, вырезаны GIF/стикеры |
| Samsung Keyboard | ~48 МБ | Данные из сравнения 2026 г. |
| OpenBoard / HeliBoard | ~20–40 МБ | AOSP-based, без сети и ML |
| Simple Keyboard | минимальная | APK < 1 МБ, только базовый ввод |

Типичный Java-heap любой клавиатуры — 22–38 МБ; большая разница в PSS набегает за счёт нативных библиотек, graphics-буферов и битмапов тем. Анимированные темы добавляют +14–22 МБ GPU-памяти — ещё один аргумент за статичную отрисовку в стиле iOS.

**Практический бюджет для татарской клавиатуры:** целиться в **PSS ≤ 30 МБ при показанной клавиатуре и ≤ 15–20 МБ в фоне**. Это реально: раскладка без ML-движка — это по сути одна кастомная View + словарь подсказок (если будет).

### Когда LMK убивает клавиатуру и что видит пользователь

- IME с активным вводом привязан (bound) к процессу приложения в фокусе → система держит его на высоком приоритете (уровень visible/perceptible по oom_adj), убивается одним из последних.
- **Скрытая клавиатура** — обычный cached/bound service с низким приоритетом. На устройстве с 1–2 ГБ RAM lmkd убивает её регулярно (запуск камеры, игры, Chrome с несколькими вкладками — достаточно).
- **Что видит пользователь при убийстве:** ничего катастрофического — система автоматически перезапускает IME-процесс при следующем тапе в поле ввода. Но пользователь получает **полный холодный старт**: задержка до появления клавиатуры (см. §2) вместо мгновенного показа. На слабом устройстве это 300–1000+ мс видимой паузы. Если процесс умирает *во время* ввода — клавиатура моргает/исчезает и появляется снова, ввод не теряется (текст живёт в приложении), но UX неприятный.
- **Вывод:** нельзя рассчитывать, что процесс живёт долго. Всё состояние — восстанавливаемое; холодный старт — главная метрика, а не редкий случай.

### Обязательные практики (из официального гайда по IME и опыта Gboard)

1. Реализовать `onTrimMemory(level)` в `InputMethodService`: при `TRIM_MEMORY_UI_HIDDEN` и выше — сбрасывать кэши битмапов, крупные буферы, выгружать словарь подсказок.
2. Официальная рекомендация из доков `InputMethodService`: освобождать крупные аллокации **вскоре после скрытия окна IME**, через отложенное сообщение (Handler.postDelayed на ~5–10 с), чтобы не пересоздавать всё при быстром повторном показе.
3. `ActivityManager.isLowRamDevice()` → true на всех Android Go устройствах (с Android 11 обязательно для ≤2 ГБ RAM). На таких устройствах: отключить тени/blur, уменьшить кэш, не грузить опциональные ресурсы.
4. Не держать статических ссылок на Context/View — классическая утечка, для долгоживущего service-процесса фатальна.

---

## 2. Холодный старт до показа клавиатуры

### Из чего состоит

Тап в поле ввода → `InputMethodManagerService` (system_server, binder) → fork процесса IME (если убит) → `onCreate()` → `onInitializeInterface()` → `onCreateInputView()` → `onStartInput()` → `onStartInputView()` → первый кадр → анимация появления окна IME.

- Холодный старт включает binder-рукопожатие с IMMS и спавн процесса — это НЕ измеряется только колбэками сервиса.
- Ориентиры от Google (для приложений, применимы как планка): холодный старт ≤ 500 мс, тёплый ≤ 200 мс. Для клавиатуры цель жёстче: **тёплый показ (процесс жив) < 100–150 мс, холодный < 400 мс на бюджетном устройстве**.

### Как ускорить

- **`onCreateInputView()` должен быть дешёвым.** Никакого XML-inflate иерархии из десятков `Button`. Правильная архитектура (как в AOSP LatinIME/HeliBoard): **одна кастомная View, рисующая все клавиши в `onDraw()`** через Canvas. Inflate одной View — микросекунды; расчёт геометрии клавиш — простая арифметика.
- Ленивая инициализация всего необязательного (словарь, звуки, вибрация-настройки) — после первого кадра, через `Handler.post`/корутину.
- Не читать SharedPreferences/файлы синхронно в `onCreate` на критическом пути — либо заранее, либо асинхронно с дефолтами.
- Baseline Profile (см. §6) — убирает JIT-интерпретацию на пути старта, на слабых устройствах даёт наибольший выигрыш (типично 25–40% к холодному старту).
- Избегать инициализации тяжёлых библиотек (никакого Firebase/Analytics в IME-процессе вообще).

### Как измерить

```kotlin
// Маркеры в коде сервиса (видны в Perfetto/Systrace):
override fun onCreate() {
    Trace.beginSection("IME.onCreate")
    super.onCreate()
    Trace.endSection()
}
```

- Perfetto (Android 10+): категории atrace `wm view input binder_driver am` + свои `Trace.beginSection`. В трейсе смотреть: binder-транзакции `startInputOrWindowGainedFocus`, спавн процесса, `Choreographer#doFrame` первого кадра IME, анимацию инсетов `WindowInsets.Type.ime()`.
- Грубо: `adb shell am force-stop <pkg>` → тап в поле ввода → замерить по logcat-таймстампам своих логов в `onCreate`/`onWindowShown`.
- Macrobenchmark для IME напрямую не заточен (метрика `StartupTimingMetric` — про Activity), но можно писать кастомный UiAutomator-тест: убить процесс, кликнуть в EditText тестового Activity, ждать появления окна IME, снимать `TraceSectionMetric` по своим маркерам.

---

## 3. Латентность нажатие → символ

### Цепочка

Касание → тач-контроллер (сканирование 60–120 Гц, ~8–16 мс само по себе) → InputDispatcher (system_server) → `onTouchEvent` вашей KeyboardView → определение клавиши → `InputConnection.commitText()` (binder в процесс приложения) → приложение вставляет символ → отрисовка кадра приложения (VSYNC, +16.7 мс при 60 Гц).

Итого физический минимум на 60 Гц экране — порядка 30–50 мс; всё, что добавляет ваша клавиатура сверх ~5–10 мс на обработку, — ваша вина. Порог заметности глазом ~50 мс, поэтому бюджет на код клавиатуры: **< 5 мс на событие касания, ноль пропущенных кадров**.

### Что убивает латентность

- Аллокации в `onTouchEvent`/`onDraw` → GC-паузы (см. §8). Замечание из полевых сравнений: у клавиатур с частым GC (каждые ~90 с против ~4 мин) джанка на ~12% больше — GC-давление важнее абсолютного размера heap.
- Работа на main thread в момент нажатия: синхронный поиск в словаре, логирование в файл, vibrator через тяжёлые пути. Подсказки считать асинхронно, коммит символа — мгновенно.
- Пропуск VSYNC из-за дорогого `onDraw` (перерисовка всей клавиатуры на каждое нажатие вместо `invalidate(rect)` только нажатой клавиши).

### Как измерять

1. **Perfetto**: категория `input` — видны слайсы InputDispatcher/InputReader; свои маркеры `Trace.beginSection("key_commit")` в `onTouchEvent`. End-to-end: от `deliverInputEvent` до кадра приложения в SurfaceFlinger.
2. **`adb shell dumpsys input`** — состояние диспетчера, очереди событий.
3. **Внутренний замер**: `MotionEvent.getEventTime()` (время касания по часам `SystemClock.uptimeMillis`) vs время вызова `commitText` — даёт вашу долю задержки:
   ```kotlin
   val latencyMs = SystemClock.uptimeMillis() - event.eventTime
   ```
4. **Кадры**: `JankStats` (androidx.metrics) или `Window.OnFrameMetricsAvailableListener` — доля janky-кадров при быстрой печати; `adb shell dumpsys gfxinfo <pkg>` — гистограмма времени кадров.
5. Абсолютный end-to-end (палец→пиксель) честно меряется только высокоскоростной камерой (240 fps смартфона достаточно: считать кадры между контактом пальца и появлением символа).

---

## 4. Размер APK

### Ориентиры

- Simple Keyboard: < 1 МБ. HeliBoard 4.0 (июль 2026): ~21 МБ universal-APK на F-Droid (4 ABI + glide-библиотека + словари). Gboard: > 60 МБ.
- **Реалистичная цель для татарской клавиатуры без ML: 1–3 МБ** (split per-ABI, а если без нативного кода — вообще без ABI-splits).

### Практики

1. **R8 fullMode + resource shrinking** (включено по умолчанию в release с AGP 8+):
   ```kotlin
   buildTypes { release {
       isMinifyEnabled = true
       isShrinkResources = true
       proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
   } }
   ```
2. **Отказ от тяжёлых зависимостей.** Клавиатуре не нужны: AppCompat/Material (UI — одна кастомная View; экран настроек можно сделать на обычном `PreferenceActivity`-стиле или маленьком Compose, но Compose добавит ~2+ МБ и медленный старт — для IME-view **не использовать Compose**, только для настроек и то спорно), Glide/Coil, Retrofit/OkHttp (сети нет вообще — это и приватность), kotlinx-serialization (настройки — SharedPreferences).
3. **Ресурсы:** клавиши рисовать кодом (Canvas: roundRect + текст), не PNG. Иконки — VectorDrawable. Одна тема iOS-стиля = набор цветов в коде, а не сотни ассетов. `resConfigs("ru")` — выкинуть чужие локали зависимостей.
4. Словарь подсказок (если будет) — сжатый бинарный формат (trie/FST, как в AOSP LatinIME `.dict`), не JSON/txt.
5. Публикация через AAB — Play сам раздаёт split по ABI/density.
6. Контроль: `apkanalyzer` / Android Studio APK Analyzer после каждого добавления зависимости.

---

## 5. Выбор minSdk в 2026

Данные по источникам расходятся (Google Play телеметрия vs StatCounter vs AppBrain), но картина такая:

- Android 15/16 — лидеры среди новых устройств; Android 16 ~23% (AppBrain, июль 2026).
- Хвост: Android 11 и 13 держат по ~13–14%, Android 10 ~10%, Android 9 ~8%, ниже — единицы процентов. Суммарно Android 8–10 — грубо 15–20% мирового парка, в СНГ/бюджетном сегменте доля старых устройств выше средней.
- Мейнстрим-рекомендация 2026 для обычных приложений — minSdk 31 (теряет ~4–5%).

**Рекомендация для этого проекта: minSdk 24 (Android 7.0) или 26 (Android 8.0).**
Аргументы:
- Целевая аудитория (татароязычные пользователи, бюджетные устройства) — именно тот сегмент, где живут старые Android. Клавиатура — не тот продукт, где можно отсечь 15% аудитории.
- IME API стабилен с древних версий; ничего из нужного (InputMethodService, InputConnection, Canvas) не требует нового API. Ниже 24 опускаться не стоит: растёт стоимость тестирования, а доля Android ≤6 — ~1–2%.
- API 26 удобнее как минимум: с него доступны Fonts in XML/`resources.getFont()`, adaptive icons, и меньше legacy-веток. Если аналитика по региону покажет <1% на API 24–25 — брать 26.
- Новые API использовать через runtime-проверки (`Build.VERSION.SDK_INT`), например `WindowInsetsAnimation` (API 30) для синхронной анимации появления.

### Android Go специфика

- С Android 11 все новые устройства с ≤2 ГБ RAM обязаны быть Go-устройствами и возвращать `ActivityManager.isLowRamDevice() == true`; с Android 13 минимум для Go — 2 ГБ.
- Специальных ограничений на IME в Go нет, но: агрессивный LMK (частые холодные старты — см. §1–2), нет некоторых фич системы (не влияет на клавиатуру).
- Практика: ветка `if (activityManager.isLowRamDevice)` → отключить необязательные эффекты, минимальные кэши.

---

## 6. Baseline Profiles

- Текстовый файл со списком классов/методов горячих путей; при установке из Play ART AOT-компилирует их заранее → нет интерпретации/JIT на первых запусках. Выигрыш холодного старта типично 25–40%, сильнее всего именно на слабых устройствах.
- Пример из доков Google: TTID 324 мс без компиляции → 229 мс с Baseline Profile.
- Подключение: модуль `androidx.baselineprofile` (Gradle-плагин) + генератор на Macrobenchmark. Для IME сценарий генератора нестандартный: тестовое Activity с EditText → тап → показ клавиатуры → печать нескольких символов. Профилируется путь `onCreate → onCreateInputView → onDraw → onTouchEvent → commitText`.
- Библиотека: `androidx.profileinstaller` подтягивается автоматически — профиль работает и при установке APK не из Play (компиляция в фоне/при idle).
- Минимальная альтернатива, если лень настраивать генератор: `ProfileInstaller` + вручную написанный профиль на ключевые классы; но генератор надёжнее.
- Проверка эффекта: Macrobenchmark с `CompilationMode.None()` vs `CompilationMode.Partial()` на физическом устройстве (на эмуляторе результаты невалидны).

---

## 7. Инструменты замера — сводка

| Инструмент | Что меряет | Команда/API |
|---|---|---|
| `adb shell dumpsys meminfo <pkg>` | PSS/Private Dirty по категориям (Java heap, native, graphics, code) | смотреть TOTAL PSS; сравнивать «клавиатура показана» vs «скрыта 30 с» |
| `adb shell dumpsys meminfo <pkg> -d` | + статистика heap и объектов | |
| Perfetto / System Tracing | таймлайн: binder, кадры, GC, свои Trace-секции | ui.perfetto.dev; Android ≤9 — Systrace |
| Macrobenchmark | холодный старт, TraceSectionMetric, проверка Baseline Profile | `androidx.benchmark:benchmark-macro-junit4`, только физ. устройство |
| `dumpsys gfxinfo <pkg>` | гистограмма времени кадров, janky % | сбрасывать `reset` перед сессией печати |
| JankStats / FrameMetrics | janky-кадры в проде/тестах | `androidx.metrics:metrics-performance` |
| Simpleperf | CPU-флеймграф горячей функции | когда Perfetto показал «где», simpleperf — «почему» |
| Memory Profiler (Studio) | аллокации по колл-стекам | искать аллокации в onDraw/onTouchEvent |
| StrictMode (debug) | disk/network на main thread | `detectAll().penaltyLog()` в debug-сборке |
| LeakCanary (debug) | утечки в service-процессе | |

Тестовые устройства: обязательно иметь один реальный Android Go / 2 ГБ аппарат (или хотя бы `adb shell setprop dalvik.vm.heapgrowthlimit` + эмулятор 2 ГБ как суррогат — но финальные цифры только с железа).

---

## 8. Практики: битмапы, шрифты, аллокации, GC

### Кэширование битмапов

- Основной приём AOSP LatinIME: **клавиатура рисуется один раз в offscreen-битмап**, на кадрах перерисовываются только нажатые клавиши поверх. Либо проще: `invalidate(dirtyRect)` только области нажатой клавиши — на современном HW-рендеринге этого достаточно.
- Key-preview (всплывающая клавиша над пальцем, как в iOS) — заранее подготовленный drawable/битмап, не создавать на каждое нажатие.
- Все битмапы сбрасывать в `onTrimMemory`/при скрытии (отложенно) и пересоздавать лениво.
- Никаких `Bitmap.createBitmap`/`decodeResource` в цикле отрисовки.

### Шрифты

- `Typeface` кэшировать в поле класса один раз (`Typeface.create(...)` / `resources.getFont(R.font...)` на API 26+); создание Typeface на каждый кадр — классическая утечка производительности.
- Для татарских букв ә ө ү җ ң һ достаточно системного Roboto — все есть в кириллическом блоке Unicode, **кастомный шрифт не нужен** (экономия и APK, и памяти). Проверить отрисовку на 2–3 бюджетных устройствах (Samsung/Xiaomi шрифты тоже покрывают эти глифы).
- Замер текста: `Paint.measureText`/`getTextBounds` — результаты для подписей клавиш посчитать один раз при layout, не в `onDraw`.

### Аллокации в цикле отрисовки и GC

- **Правило: ноль аллокаций в `onDraw()` и `onTouchEvent()`.** Все `Paint`, `Rect`, `RectF`, `Path`, массивы — поля класса, создаются в конструкторе/`onSizeChanged`.
  ```kotlin
  // ПЛОХО: аллокация на каждый кадр
  override fun onDraw(c: Canvas) { val p = Paint(); val r = RectF(...) }
  // ХОРОШО:
  private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val tmpRect = RectF()
  ```
- Избегать автобоксинга (`Map<Integer,...>` → `SparseArray`/`IntArray`), лямбд, создающих объекты в горячем пути, конкатенации строк в цикле (переиспользуемый `StringBuilder`).
- Строки-подписи клавиш — предвычисленный массив `String`, не `Char.toString()` на кадр.
- Пулы объектов для событий multi-touch, если понадобится.
- Контроль: Memory Profiler → Record allocations во время печати; в logcat не должно быть сообщений GC во время активного ввода. Также `Debug.startAllocCounting()` в тестах или `dumpsys meminfo -d` до/после сессии.
- Именно GC-паузы, а не абсолютный размер heap, дают ощутимый джанк на слабых устройствах — heap 25 МБ со стабильным нулём аллокаций в горячем пути лучше, чем heap 18 МБ с мусором на каждый кадр.

### Прочее

- Один процесс, никаких отдельных процессов для настроек без необходимости.
- Вибрация: `VibrationEffect.EFFECT_CLICK`/`performHapticFeedback(KEYBOARD_TAP)` — системные, дешёвые.
- Звук нажатия: `AudioManager.playSoundEffect(FX_KEYPRESS_STANDARD)` — не SoundPool со своими файлами.

---

## 9. Итоговые целевые метрики проекта

| Метрика | Цель | Инструмент проверки |
|---|---|---|
| PSS показанной клавиатуры | ≤ 30 МБ | dumpsys meminfo |
| PSS в фоне (30 с после скрытия) | ≤ 15–20 МБ | dumpsys meminfo |
| Холодный старт до показа | < 400 мс (бюджетное устройство) | Perfetto + Trace-маркеры |
| Тёплый показ | < 150 мс | то же |
| Обработка касания (наш код) | < 5 мс | eventTime vs commitText |
| Janky-кадры при печати | ~0% | gfxinfo / JankStats |
| APK (release, per-ABI/без native) | 1–3 МБ | APK Analyzer |
| GC во время печати | 0 событий | logcat / Memory Profiler |
| minSdk | 24–26 | — |

---

## 10. Функциональное тестирование и совместимость (дополнение, 18.07.2026)

Производительность (§1–9) — не главный источник багов IME. Главный — зоопарк редакторов текста и OEM-прошивок: каждое приложение реализует `InputConnection` по-своему (WebView/Chromium, Compose `TextField`, кастомные редакторы Telegram/банков), а вендорские прошивки по-своему управляют жизненным циклом процессов. Этот раздел — план функционального тестирования.

### 10.1 Матрица тестирования: типы полей и проблемные приложения

Для каждой ячейки матрицы прогонять базовый сценарий: показ клавиатуры → ввод татарского текста (обязательно ә ө ү җ ң һ) → backspace посимвольно и удержанием → перемещение курсора в середину слова и правка → Enter/действие → скрытие.

| Тип поля / приложение | Что именно проверять | Известные грабли |
|---|---|---|
| **WebView / Chrome** (форма на сайте, `contenteditable`, поле поиска) | composing-текст, backspace, автоповтор удаления | Chromium не транслирует нажатия в honest key events: при composing шлёт keyCode 229, backspace через `deleteSurroundingText` вообще не порождает keydown — сайты, слушающие keydown, «не видят» ввод. **Вывод для нас: удаление делать через `deleteSurroundingText(1,0)` (как LatinIME с 4.1), а НЕ через `sendKeyEvent(KEYCODE_DEL)` — и проверить оба поведения в WebView.** Если не использовать composing-текст вовсе (нет подсказок в MVP — коммитить сразу `commitText`), большая часть WebView-багов исчезает |
| **Compose `TextField`** (новые приложения, включая Settings на Android 15+) | курсор, выделение, `getSurroundingText`, восстановление состояния | Compose BasicTextField имеет собственную реализацию InputConnection; известны отличия в батчинге (`beginBatchEdit`) и обработке `setComposingRegion` |
| **Классический `EditText`** | эталон — всё должно работать идеально | база для автотестов (§10.4) |
| **Telegram** (обычный чат, поиск, подпись к фото) | кастомный редактор, эмодзи-панель, ответ/редактирование сообщения | популярнейшее приложение целевой аудитории; кастомная обработка Enter (отправка vs перенос строки — зависит от настройки) |
| **WhatsApp** | multi-line поле, автоскролл при росте поля | |
| **Пароли** (`inputType=textPassword`, поля банков) | отключение подсказок/обучения, точки вместо символов, `IME_FLAG_NO_PERSONALIZED_LEARNING` | банковские приложения (Сбер, Т-Банк) любят кастомные поля и флаги; некоторые показывают собственную PIN-клавиатуру — наша не должна мигать при переключении |
| **Инкогнито** (Chrome incognito, приватные вкладки) | флаг `IME_FLAG_NO_PERSONALIZED_LEARNING` уважать (когда появится словарь) | |
| **Поиск с `actionSearch` / `actionGo` / `actionSend` / `actionNext` / `actionDone`** | правильная подпись/иконка Enter, `performEditorAction` | проверить в: Google-поиск, YouTube, Play Market, формы логина (`actionNext` между полями) |
| **Multi-line** (`textMultiLine`: заметки, Gmail) | Enter = перенос строки, не действие | |
| **Числовые/телефонные поля** (`number`, `phone`, `datetime`) | переключение на цифровую раскладку и обратно | если своей числовой раскладки нет в MVP — хотя бы не показывать буквенную без цифр |
| **`textNoSuggestions`, `textVisiblePassword`** | не включать composing/подсказки | |
| **Поле URL** (`textUri`) | раскладка с `/`, `.`, без автокапитализации | |

Дополнительные системные сценарии:

- **Split-screen / freeform**: клавиатура в разделённом экране (особенно нижнее приложение), поворот экрана с открытой клавиатурой, изменение размера окна. С Android 15 приложения с `resizableActivity` в multi-window — обычный случай на планшетах.
- **Физическая/Bluetooth-клавиатура**: при подключённой BT-клавиатуре наша IME не должна ломать ввод; проверить `onEvaluateInputViewShown()` (по умолчанию система скрывает soft-клавиатуру при физической — поведение должно быть предсказуемым, у пользователя должна остаться возможность вызвать экранную).
- **Переключение IME туда-обратно** (Gboard ↔ наша) через переключатель на клавише/системный picker — состояние не должно теряться, окно не должно «залипать».
- **Голосовой ввод / вставка из буфера** — курсор после вставки, ввод после голосового.
- **Смена ориентации и тёмная тема на лету** — пересоздание view без крэша и утечек.

### 10.2 Целевые устройства и OEM-прошивки

Для целевой аудитории (бюджетные Xiaomi/Redmi, Honor/Huawei, Samsung A-серия, realme/Tecno) парк минимум:

| Устройство/прошивка | Зачем | Что проверять специально |
|---|---|---|
| **Xiaomi/Redmi с MIUI 14 / HyperOS** (обязательно, хотя бы одно физическое) | самая частая прошивка у аудитории; агрессивный киллер фона. Многолетний известный баг «клавиатура исчезает во время набора» (в т.ч. Gboard/SwiftKey) на MIUI | 1) в Security app → Autostart/Battery saver: поведение IME с настройками по умолчанию (без «No restrictions»!) — клавиатура обязана переживать киллер, т.к. система перезапускает IME сама, но проверить частоту холодных стартов; 2) набор во время прихода уведомления; 3) после разблокировки экрана; 4) «Очистить всё» в недавних — клавиатура должна вернуться при следующем тапе |
| **Honor/Huawei (EMUI/MagicOS, без GMS на новых)** | вторая по агрессивности прошивка (PowerGenie); часть аудитории | распространение APK без Play (см. 08-distribuciya); поведение после «оптимизации батареи» |
| **Samsung A-серия (One UI)** | массовый бюджетник; Samsung-шрифты и своё поведение инсетов | отрисовка ә ө ү җ ң һ системным шрифтом; navigation bar/жесты и высота клавиатуры |
| **Android Go / чистый Android ≤2 ГБ** (или эмулятор 2 ГБ как суррогат) | база для §1–2 | холодные старты, onTrimMemory |
| **Эмуляторы API 24/26 (minSdk), 30, 35+** | границы версий | инсеты (API 30 `WindowInsets.ime()`), edge-to-edge (API 35 принудительно) |

Справочник по вендорским киллерам — **dontkillmyapp.com**: там задокументировано, какие прошивки убивают фоновые процессы и какие настройки это чинят. Важно: IME перезапускается системой автоматически, поэтому вендорский киллер для клавиатуры проявляется не как «умерла навсегда», а как постоянные холодные старты и «моргания» — то есть меряется метриками §2 именно на MIUI-устройстве, а не на чистом Android.

### 10.3 Как организуют регрессию HeliBoard и FlorisBoard

- **HeliBoard**: основной слой — **JVM-тесты на Robolectric** в `app/src/test/` (не инструментальные — быстрые, гоняются в CI без эмулятора). Ключевой приём — собственный **`ShadowInputMethodService`**: shadow подменяет `InputConnection` и аккумулирует закоммиченный текст в строку, а тест поднимает сервис целиком через `Robolectric.setupService(LatinIME::class.java)`, создаёт реальную KeyboardView и шлёт в неё синтетические `MotionEvent` по координатам клавиш. Так тестируются shift/caps-lock, sliding input, ввод символов (`InputTest.kt`), плюс `InputLogicTest.kt` (логика курсора/удаления/composing), `KeyboardParserTest`/`LayoutTest` (парсинг раскладок), `SuggestTest`. **Этот паттерн стоит скопировать один в один: Robolectric + shadow InputConnection покрывает 80% логики без устройства.**
- **FlorisBoard**: юнит-тесты на Kotest + структурированный **bug report (GitHub issue forms, `bug_report.yml`)** с обязательными полями: версия, источник установки (Play/F-Droid/GitHub), модель устройства, версия Android + прошивка, шаги воспроизведения. Отдельный шаблон `crash_report.yml`.
- **HeliBoard bug report** дополнительно требует указывать **приложение и конкретное текстовое поле**, в котором воспроизводится баг («Settings and the app you're writing in are usually important») — прямое следствие того, что бо́льшая часть багов IME специфична для конкретного редактора. Наш шаблон bug report должен требовать: приложение+поле, устройство+прошивка (MIUI/One UI/…), версия Android, шаги.
- Эталон «как тестирует Google» — **CTS-тесты `android.view.inputmethod.cts`** с фреймворком **MockIme** (`MockImeSession`, `ImeEventStream`): инструментальные тесты, где mock-клавиатура регистрирует все колбэки (`onStartInput`, `showSoftInput`…), а тест ассертит их поток. Для нас это скорее источник ожидаемого поведения системы, чем прямая зависимость.

### 10.4 Скелет автотестов для CI

Двухуровневая схема:

**Уровень 1 (каждый коммит, JVM/Robolectric)** — по образцу HeliBoard: логика раскладки, маппинг тач-координат → клавиша, shift/caps, удаление, ввод всех татарских букв. Без эмулятора, секунды в CI.

**Уровень 2 (инструментальный, эмулятор в CI — nightly/перед релизом)** — тестовое Activity с набором EditText разных `inputType` + UiAutomator:

```kotlin
// androidTest: тестовое Activity объявлено в debug-манифесте,
// содержит EditText'ы: id/plain, id/password, id/multiline, id/search (imeOptions=actionSearch)...
@RunWith(AndroidJUnit4::class)
class ImeInstrumentedTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before fun enableIme() {
        // включить и выбрать нашу IME (требует adb-привилегий инструментации)
        device.executeShellCommand("ime enable com.example.tatar/.TatarIME")
        device.executeShellCommand("ime set com.example.tatar/.TatarIME")
        ActivityScenario.launch(ImeTestActivity::class.java)
    }

    @Test fun typeTatarLetters_plainField() {
        val field = device.findObject(By.res(PKG, "plain"))
        field.click()
        // ждём окно IME (наша KeyboardView имеет contentDescription/resource-id)
        device.wait(Until.hasObject(By.desc("tatar_keyboard")), 3_000)
        tapKey("ә"); tapKey("л"); tapKey("л"); tapKey("ә")   // тап по координатам клавиш
        assertEquals("әллә", field.text)
    }

    @Test fun coldStart_afterProcessKill() {
        device.executeShellCommand("am force-stop com.example.tatar")
        device.findObject(By.res(PKG, "plain")).click()
        assertTrue(device.wait(Until.hasObject(By.desc("tatar_keyboard")), 5_000)) // холодный старт < 5 c — smoke
    }

    private fun tapKey(label: String) { /* координаты из геометрии раскладки; клик device.click(x, y) */ }
}
```

Замечания:

- `ime enable`/`ime set` через `UiDevice.executeShellCommand` — стандартный способ выбрать тестируемую клавиатуру без ручных действий.
- Проверять текст надёжнее через сам EditText тестового Activity (Espresso `onView(withId(...)).check(matches(withText(...)))` в том же процессе), а тап по клавишам — по координатам, вычисленным из той же геометрии, что использует KeyboardView.
- В CI: GitHub Actions + `reactivecircus/android-emulator-runner` (API 26 и API 35 — обе границы). Этот же харнесс переиспользуется генератором Baseline Profile (§6) и Macrobenchmark-замером холодного старта (§2).
- WebView-кейсы автоматизируются тем же способом: в тестовое Activity добавить `WebView` с `contenteditable` и полем `<input>`, ассертить содержимое через `evaluateJavascript`.

**Уровень 3 (ручная регрессия перед релизом)** — чек-лист = матрица §10.1 × устройства §10.2. Минимальный прогон: Telegram, Chrome (обычный + инкогнито), WhatsApp, поле пароля банка, Google-поиск, заметки (multi-line), split-screen, поворот, переключение с Gboard — на Xiaomi (MIUI/HyperOS) и Samsung (One UI) обязательно, остальное по возможности.

---

## Источники

- [Android Developers — Low memory killers](https://developer.android.com/topic/performance/vitals/lmk)
- [Android Developers — Memory allocation among processes](https://developer.android.com/topic/performance/memory-management)
- [Android Developers — InputMethodService (API reference)](https://developer.android.com/reference/android/inputmethodservice/InputMethodService)
- [Android Developers — Create an input method](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method)
- [Android Developers — Overview of measuring app performance](https://developer.android.com/topic/performance/measuring-performance)
- [Android Developers — Benchmark Baseline Profiles with Macrobenchmark](https://developer.android.com/topic/performance/baselineprofiles/measure-baselineprofile)
- [Android Developers — Create Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile)
- [Google Codelab — Improve app performance with Baseline Profiles](https://codelabs.developers.google.com/android-baseline-profiles-improve)
- [Meta Engineering — Accelerating our Android apps with Baseline Profiles (2025)](https://engineering.fb.com/2025/10/01/android/accelerating-our-android-apps-with-baseline-profiles/)
- [Android Developers — Android (Go edition)](https://developer.android.com/guide/topics/androidgo) и [Optimize for Android (Go edition)](https://developer.android.com/guide/topics/androidgo/optimize)
- [Android Developers Blog — Optimize for Android Go: Lessons from Google apps, Part 1](https://android-developers.googleblog.com/2022/09/optimize-for-android-go-lessons-from-google-apps-part-1.html)
- [XDA — Android Go Edition required for new low-RAM devices](https://www.xda-developers.com/android-go-edition-requirement-new-low-ram-devices/)
- [AOSP — IME support (window management)](https://source.android.com/docs/core/display/multi_display/ime-support)
- [AppBrain — Android OS version market share (Jul 2026)](https://www.appbrain.com/stats/top-android-sdk-versions)
- [StatCounter — Android version market share](https://gs.statcounter.com/android-version-market-share)
- [apilevels.com — API levels и кумулятивные доли](https://apilevels.com/)
- [MegaMethod — Recommended minimum SDK version](https://www.megumethod.com/blog/recommended-minimum-sdk-version-for-android-projects)
- [Digital Trends — Gboard Go для low-RAM устройств (~40 МБ vs ~70 МБ)](https://www.digitaltrends.com/phones/google-gboard-go-news/)
- [Phandroid — Gboard Go download](https://phandroid.com/2018/01/22/gboard-go-download/)
- [GeekExtreme — Gboard vs Samsung Keyboard (2026)](https://www.geekextreme.com/gboard-vs-samsung-keyboard/)
- [Android Police — Gboard alternatives (OpenBoard и др.)](https://www.androidpolice.com/gboard-keyboard-alternatives-android/)
- [MakeUseOf — тест open-source альтернатив Gboard (HeliBoard, FlorisBoard)](https://www.makeuseof.com/best-open-source-gboard-alternatives-tested/)
- [F-Droid — HeliBoard (размер APK ~21 МБ, v4.0)](https://f-droid.org/packages/helium314.keyboard/)
- [HeliBoard — GitHub](https://github.com/heliborg/heliboard)
- [How-To Geek — open-source Android keyboards (Simple Keyboard < 1 МБ)](https://www.howtogeek.com/open-source-android-keyboards-that-rival-gboard/)
- [Esper — How to Optimize Android for Low RAM Hardware](https://www.esper.io/blog/how-to-optimize-android-for-low-ram-devi)

Источники к §10 (тестирование и совместимость):

- [HeliBoard — тесты `app/src/test` (Robolectric, ShadowInputMethodService, InputTest/InputLogicTest)](https://github.com/Helium314/HeliBoard/tree/main/app/src/test/java/helium314/keyboard)
- [HeliBoard — шаблон bug report (требует указать приложение и текстовое поле)](https://github.com/Helium314/HeliBoard/blob/main/.github/ISSUE_TEMPLATE/bug_report.md)
- [FlorisBoard — структурированный bug report (issue forms: устройство, Android, источник установки)](https://github.com/florisboard/florisboard/blob/main/.github/ISSUE_TEMPLATE/bug_report.yml)
- [AOSP CTS — тесты InputMethod (`android.view.inputmethod.cts`, фреймворк MockIme)](https://android.googlesource.com/platform/cts/+/master/tests/inputmethod/)
- [AOSP CTS — InputMethodServiceLifecycleTest (hostside)](https://android.googlesource.com/platform/cts/+/master/hostsidetests/inputmethodservice/hostside/src/android/inputmethodservice/cts/hostside/InputMethodServiceLifecycleTest.java)
- [Android Developers — Write automated tests with UI Automator](https://developer.android.com/training/testing/other-components/ui-automator)
- [Chromium issue 118639 — keydown/keyup keyCode = 0/229 при composing-вводе (обсуждение setComposingText)](https://groups.google.com/a/chromium.org/g/chromium-bugs/c/08KdqaHAhsY)
- [Chromium issue 184812 — backspace через deleteSurroundingText не порождает key events](https://groups.google.com/a/chromium.org/g/chromium-bugs/c/hCuNTF76XqQ/m/Qo1oozSi36IJ)
- [Don't kill my app! — справочник агрессивных OEM-киллеров фоновых процессов (Xiaomi, Huawei и др.)](https://dontkillmyapp.com/)
- [xiaomi.eu — «Keyboard stops working» на MIUI (клавиатура исчезает при уведомлении, Gboard/SwiftKey)](https://xiaomi.eu/community/threads/keyboard-stops-working.65635/)
- [xiaomi.eu — «Keyboard and RAM (and multitasking) bug» (давний баг исчезающей клавиатуры на MIUI)](https://xiaomi.eu/community/threads/keyboard-and-ram-and-multitasking-bug.25523/)
- [ProseMirror forum — Samsung Keyboard в Android WebView: спам переносов строк в contenteditable](https://discuss.prosemirror.net/t/samsung-keyboard-within-android-webview-causes-a-spam-of-new-lines/5246)

Пометки о достоверности: цифры RAM клавиатур (§1) собраны из вторичных источников с разными методиками (dumpsys vs обзоры) — использовать как порядок величины, свои цифры снимать dumpsys meminfo на целевых устройствах. Данные о частоте GC Gboard/SwiftKey и «31% меньше RAM» — из непроверяемых сторонних бенчмарков, приведены только как иллюстрация тезиса «GC-давление важнее размера heap». Официальной статистики Google Play по версиям Android публично больше нет (последний публичный срез — декабрь 2025), поэтому доли версий — оценка по AppBrain/StatCounter.
