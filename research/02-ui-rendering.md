# UI и рендеринг клавиатуры (Android IME, июль 2026)

Контекст проекта: татарская кириллическая раскладка (ә ө ү җ ң һ), визуальный стиль iOS-клавиатуры, приоритет — слабые/бюджетные устройства (1–3 ГБ RAM, Android Go, старые SoC). Разработчик — новичок в Android.

---

## 1. Три подхода к отрисовке

### 1.1. `android.inputmethodservice.KeyboardView` — deprecated, не использовать

- `KeyboardView` и `Keyboard` (XML-описание раскладки `<Keyboard><Row><Key/>`) **deprecated с API 29** (Android 10, 2019). Официальная формулировка: «это просто удобный UI-виджет, который разработчики могут переимплементировать поверх публичных API».
- Классы всё ещё присутствуют в SDK и работают, но: нет multi-touch в удобном виде, привязка к внутренним стилям фреймворка, никаких обновлений, ограниченная темизация, проблемы с edge-to-edge на API 35+.
- «Официальный» обходной путь Google — скопировать `KeyboardView.java`/`Keyboard.java` из AOSP к себе в проект (есть готовая обёртка [hijamoya/KeyboardView](https://github.com/hijamoya/KeyboardView), но она без поддержки accessibility и фактически заброшена — последний релиз 0.0.2).
- **Вывод: не подходит даже как временное решение.** Единственная его ценность — читать исходники как справочник по геометрии клавиш и обработке касаний.

### 1.2. Кастомный `View` + Canvas (путь AOSP LatinIME / OpenBoard / HeliBoard / Gboard)

Один класс `KeyboardView : View`, который:

- в `onMeasure()` задаёт высоту клавиатуры (обычно % высоты экрана);
- в `onDraw(canvas)` рисует все клавиши: скруглённый фон клавиши (`canvas.drawRoundRect` или заранее подготовленный `NinePatch`/`Drawable`), текст (`canvas.drawText` с `Paint`, у которого замерены `FontMetrics`);
- в `onTouchEvent(event)` разбирает multi-touch и мапит координаты в клавиши;
- модель раскладки — обычные Kotlin-объекты (`Key(code, label, x, y, w, h, popupChars)`), загружаемые из JSON/кода, а не из deprecated XML.

Так устроены все «серьёзные» клавиатуры старой школы: AOSP LatinIME (`MainKeyboardView`, `Key`, `KeyboardView` в пакете `com.android.inputmethod.keyboard` — [исходники](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/master/java/src/com/android/inputmethod/keyboard/)), OpenBoard и его актуальный форк **HeliBoard** (GPL-3.0), а также Gboard.

Плюсы:
- минимальный memory footprint (один View, один слой), классы View-тулкита уже прогреты Zygote — нулевая «стоимость входа»;
- полный контроль над каждым пикселем и каждой миллисекундой; тап → `invalidate()` → кадр за 1–3 мс на слабом SoC;
- APK не тянет compose-runtime/ui/foundation (это минус ~1.5–2 МБ к APK и заметный минус к RAM).

Минусы:
- всё руками: геометрия, попапы, анимации, темы;
- больше кода, чем в Compose (но для клавиатуры из ~35 клавиш это 1–2 тыс. строк).

### 1.3. Jetpack Compose внутри IME (путь FlorisBoard)

**Технически возможно, но с нюансами.** `ComposeView` требует, чтобы в иерархии были `ViewTreeLifecycleOwner` и `ViewTreeSavedStateRegistryOwner`, иначе падение:
`IllegalStateException: Composed into the View which doesn't propagate ViewTreeLifecycleOwner`.
`InputMethodService` — не Activity, окно IME — внутренний `Dialog` (SoftInputWindow), поэтому владельцев жизненного цикла надо подставить самому:

```kotlin
class TatarImeService : InputMethodService(),
    LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle get() = lifecycleRegistry
    override val savedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateInputView(): View {
        // современный вариант: владельцы вешаются на decorView окна IME
        window!!.window!!.decorView.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        return ComposeView(this).apply { setContent { KeyboardScreen() } }
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}
```

(Также обычно нужен `ViewModelStoreOwner`, если используются ViewModel. Готовый пример — gist [ComposedInputMethodService](https://gist.github.com/LennyLizowzskiy/51c9233d38b21c52b1b7b5a4b8ab57ff), статьи Itsuki и Maksym Koval — см. Источники.)

Накладные расходы Compose, критичные именно для IME на слабых устройствах:

- **Compose — unbundled-библиотека**: её классы НЕ прогреты Zygote (в отличие от View-тулкита) и не входят в system image, поэтому первый показ клавиатуры после холодного старта процесса IME проходит через JIT/интерпретацию. Baseline Profiles снимают часть проблемы (~30% ускорение исполнения по данным Google, до 40% на конкретных экранах Play Store), но не убирают её полностью.
- **Память**: кейс Google Play Store — загрузка Compose-фреймворка в память дорога сама по себе, «two stack problem» особенно бьёт по low-end и Android Go. Для процесса клавиатуры, который система убивает и перезапускает часто, это означает регулярную повторную оплату старта. На практике Compose-IME держит в RSS на десятки МБ больше, чем Canvas-IME (точных публичных бенчмарков именно для IME нет — честная пометка; ориентир: пустой Compose-процесс тяжелее View-процесса примерно на 20–40 МБ).
- **Латентность кадра**: с Compose 1.9+ Google заявляет паритет с Views по jank при скролле, но у клавиатуры критичен другой путь: touch → recomposition → layout → draw. Рекомпозиция при каждом нажатии/подсветке клавиши даёт больше аллокаций и работы, чем точечный `invalidate(rect)` в кастомном View.
- Процесс IME живёт долго и на слабом устройстве первым становится кандидатом на kill при нехватке памяти — чем больше RSS, тем чаще пользователь будет видеть задержку появления клавиатуры.

FlorisBoard (Apache-2.0) — рабочее доказательство, что Compose-IME жизнеспособен (своя тема-движок Snygg, Material You), но его собственные issue-треды регулярно упоминают потребление памяти и стартовую задержку на бюджетных устройствах.

### 1.4. Рекомендация

**Для слабых устройств — кастомный View + Canvas.** Аргументы:

1. Клавиатура — это, по сути, один статичный грид с редкими изменениями (подсветка клавиши, смена раскладки/shift). Идеальный случай для Canvas и худший сценарий для оправдания рекомпозиций.
2. Память и холодный старт процесса IME — главные метрики на low-end; Compose проигрывает по обеим по архитектурным причинам (unbundled, нет Zygote-прогрева).
3. Новичку код Canvas-клавиатуры проще отлаживать, чем связку InputMethodService+Lifecycle+Compose со всеми её нюансами.
4. Референсы для форка/копирования — **HeliBoard** (GPL-3.0, актуально поддерживается, AOSP-родословная) и AOSP LatinIME. Если лицензия GPL неприемлема — LatinIME (Apache-2.0) или писать свой View с нуля, подглядывая в оба.

Compose разумно оставить для **приложения-настройщика** (Activity с настройками, выбором темы, онбордингом включения клавиатуры) — там его стоимость не критична.

---

## 2. Key preview popup (увеличенная клавиша над пальцем)

Два способа:

**A. `PopupWindow`** (так делает старый `KeyboardView` и LatinIME): на каждый активный палец — маленький `TextView`/кастомный View в отдельном окне поверх клавиатуры. В LatinIME этим управляет `KeyPreviewChoreographer` (`keyboard/internal/KeyPreviewChoreographer.java`): пул переиспользуемых preview-вью, атрибуты `keyPreviewLingerTimeout` (задержка исчезновения после отпускания, в мс), аниматоры `keyPreviewShowUpAnimator`/`keyPreviewDismissAnimator`.
Минусы: каждое `PopupWindow.showAtLocation`/обновление — это IPC к WindowManager; на слабых устройствах при быстрой печати заметны задержки и мигание; окно может обрезаться границами окна IME/экрана.

**B. Отрисовка в оверлее внутри собственного окна IME** — предпочтительно. Приёмы:
- Сделать корневой контейнер IME выше самой клавиатуры (прозрачная зона сверху ~1 высоты клавиши) и рисовать превью прямо в `onDraw` поверх клавиш / в дочернем View поверх. Окно IME может быть выше видимой клавиатуры; область «ввода» задаётся через `onComputeInsets()` (`contentTopInsets`/`visibleTopInsets`, `touchableRegion`), чтобы приложение под клавиатурой получало тапы в прозрачной зоне, кроме моментов показа превью.
- Никаких новых окон → нет IPC → превью появляется в том же кадре, что и подсветка клавиши.

Практический план: превью — обычный `drawRoundRect` + `drawText` увеличенным кеглем в слое поверх клавиатуры, показывать по `ACTION_DOWN`, прятать по `ACTION_UP` + linger ~50–70 мс. На iOS-стиль это ложится идеально (у iOS превью — «пузырь», вырастающий из клавиши, в пределах того же слоя).

Замечание: Gboard позволяет отключать превью; на очень слабых устройствах стоит дать такую настройку.

## 3. Long-press попап с альтернативными символами

Критично для татарской раскладки — альтернативы можно продублировать long-press'ом (ә на а, ө на о, ү на у, җ на ж, ң на н, һ на х), даже если основные буквы есть на своих клавишах.

Механика (по образцу LatinIME `MoreKeysKeyboard`/`MoreKeysKeyboardView`/`MoreKeysPanel`):
1. По `ACTION_DOWN` заводится long-press таймер (`Handler.postDelayed`, у LatinIME таймауты через `TimerProxy.startLongPressTimer`; типичное значение 250–400 мс, у AOSP настройка `key_longpress_timeout`, по умолчанию ~300 мс).
2. По срабатыванию — построить мини-клавиатуру из альтернатив над клавишей. LatinIME показывает её через `PopupWindow` (`showMoreKeysPanel(parentView, controller, x, y, window, listener)`), но, как и с превью, проще и быстрее рисовать панель в собственном оверлее.
3. **Палец не отпускается**: дальнейшие `ACTION_MOVE` того же пальца переадресуются панели (slide-to-select). В LatinIME для этого есть `MoreKeysDetector` с допуском `config_more_keys_keyboard_slide_allowance` и флаг `mIsShowingMoreKeysPanel` в `PointerTracker`. Выбор фиксируется на `ACTION_UP`, панель закрывается.
4. Геометрия: панель центрируется над клавишей, прижимается к краям экрана; выбранная по умолчанию альтернатива — под пальцем.

## 4. Multi-touch

Обязателен: быстрая печать — это второй палец, опускающийся до отпускания первого.

- В `onTouchEvent` обрабатывать `ACTION_POINTER_DOWN` / `ACTION_POINTER_UP` и `event.getActionIndex()`; координаты каждого пальца — `event.getX(index)/getY(index)`, идентификация — `event.getPointerId(index)`.
- Паттерн LatinIME: класс **`PointerTracker`** — по одному экземпляру на pointerId (статический пул), каждый хранит своё состояние (текущая клавиша, флаги `mIsRepeatableKey`, `mIsInSlidingKeyInput`, `mKeyAlreadyProcessed`), свои таймеры long-press/repeat и свой key preview.
- Ключевое поведение Gboard/AOSP: когда второй палец делает `ACTION_POINTER_DOWN`, первая клавиша **коммитится немедленно** (не ждём её `ACTION_UP`) — это резко снижает ощущаемую задержку при быстрой печати.
- `ACTION_MOVE` содержит батч точек для всех пальцев — итерировать `for (i in 0 until event.pointerCount)`.
- Попадание в клавишу считать не по прямоугольнику отрисовки, а по расширенным hit-зонам без зазоров (клавиши делят всю площадь ряда; у LatinIME — `KeyDetector` с ближайшей клавишей и вертикальной коррекцией «палец ниже центра видимой цели»).
- Repeat для Backspace: первый повтор ~400 мс, далее каждые ~50 мс (Handler-таймер в трекере).

## 5. Стратегия invalidate/перерисовки без лагов

- **Точечный invalidate**: при нажатии/отпускании перерисовывать только клавишу: `invalidate(key.x, key.y, key.x + key.w, key.y + key.h)` (на современных версиях полный `invalidate()` с аппаратным ускорением тоже дёшев, но точечный экономит на слабых GPU). LatinIME: `DrawingProxy.invalidateKey(key)`.
- **Кэш статичного слоя**: раскладку без подсветки один раз отрисовать в `Bitmap` (или положиться на аппаратный display list — обычно достаточно) и в `onDraw` рисовать bitmap + поверх только нажатые клавиши. Так делал старый `KeyboardView` (`mBuffer` + флаг `mDrawPending`). На API 21+ проще: `setLayerType(LAYER_TYPE_HARDWARE, null)` не нужен, display list кэшируется автоматически, если не менять контент.
- **Никаких аллокаций в `onDraw`**: все `Paint`, `Rect`, `Path`, строки лейблов — поля, созданные заранее. `Paint.getFontMetrics`/`measureText` — один раз при смене размера/темы, результаты кэшировать per-textSize (у LatinIME — `TypefaceUtils`/кэши ширин).
- `onDraw` должен укладываться в бюджет кадра 16.6 мс (60 Гц) с большим запасом; реальная цель на слабом SoC — < 4–5 мс. Проверять через GPU Profiler (Profile HWUI rendering) и `adb shell dumpsys gfxinfo`.
- Не использовать `View.ALPHA`-анимации на всей клавиатуре; анимации подсветки — простое переключение цвета фона клавиши (двух-трёх кадровая интерполяция максимум).
- Текст рисовать `drawText` без `StaticLayout` (лейблы однострочные); иконки (shift, backspace, глобус) — заранее растеризованные `VectorDrawable.toBitmap()` или отрисовка drawable с кэшированными bounds.
- Тактильный отклик — `view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)`, звук — `AudioManager.playSoundEffect(FX_KEYPRESS_STANDARD)`; оба вызывать по `ACTION_DOWN`, не по UP.

## 6. Темизация (светлая/тёмная)

- Источник правды — `Configuration.UI_MODE_NIGHT_MASK` (`resources.configuration.uiMode`). У IME при смене системной темы вызывается `onConfigurationChanged`/пересоздание input view — пересоздавать тему и вызвать `invalidate()`.
- Практичная схема: класс `KeyboardTheme` с полями (фон клавиатуры, фон обычной клавиши, фон служебной клавиши, цвет текста, цвет нажатой клавиши, радиус скругления, тень). Два инстанса — light/dark в стиле iOS:
  - light: фон ~#D1D4D9, клавиши #FFFFFF, служебные #ABB0BA, текст #000000, radius ~5–6dp, лёгкая тень снизу 1dp;
  - dark: фон ~#2B2B2D (iOS использует полупрозрачность/blur — на Android для слабых устройств брать непрозрачные цвета, blur дорог), клавиши #6B6B6D, служебные #464648, текст #FFFFFF.
- Настройка «как в системе / светлая / тёмная» через `SharedPreferences`/DataStore; на Android 12+ опционально Material You (`android.R.color.system_accent1_*` через `getColor`), но для iOS-стиля это не обязательно.
- Все цвета — в объекте темы, `Paint.color` переставляется при отрисовке; никаких resource-lookup в `onDraw`.

## 7. Размеры экранов и ландшафт

- Высота клавиатуры: в портрете обычно 38–42% высоты экрана нельзя (это очень много) — практический ориентир AOSP: суммарная высота ~ `min(38% высоты экрана, ~250dp)`; проще — высота ряда 54–64dp × 4 ряда + ряд подсказок. Дать настройку высоты (множитель 0.85–1.15).
- В ландшафте высоту считать от меньшей стороны: фиксированные dp-ряды по ~48–52dp, иначе клавиатура займёт пол-экрана. Определять через `resources.configuration.orientation`; IME автоматически пересоздаёт input view при повороте (`onCreateInputView`/`setInputView` вызываются заново — держать построение раскладки дешёвым).
- Ширина клавиш — в долях ширины экрана (проценты, как в AOSP `keyWidth="10%p"`), а не в dp: раскладка сама растянется на любые экраны. Татарская раскладка на базе ЙЦУКЕН: стандартные 11 клавиш в ряду (или 12 с ә/ө в основном ряду) — при 11+ клавишах следить, чтобы ширина клавиши на узких экранах (320dp) не падала ниже ~29dp; иначе выносить доп. буквы в long-press.
- Планшеты/раскладные: `smallestScreenWidthDp >= 600` — увеличить боковые отступы или включить float/split (можно отложить на v2).
- Не забыть `resizableActivity`/многооконность: IME получает ширину окна, не экрана — все расчёты вести от ширины собственного View (`onSizeChanged`), не от `DisplayMetrics`.

## 8. Edge-to-edge и WindowInsets (API 35+)

С Android 15 (API 35) edge-to-edge принудителен для приложений с targetSdk 35 — это касается и окна IME:

- Симптом без обработки: нижний ряд клавиатуры перекрыт панелью навигации (3-button) или жестовой полосой; известный кейс — [codeboard issue #137](https://github.com/gazlaws-dev/codeboard/issues/137): окно IME (внутренний `SoftInputWindow`-Dialog) не применяет инсеты автоматически.
- Решения:
  1. Простейшее — `android:fitsSystemWindows="true"` на корневом layout'е input view (рабочий workaround из codeboard);
  2. Правильное — слушатель инсетов на корневом View:
     ```kotlin
     ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
         val nav = insets.getInsets(WindowInsetsCompat.Type.systemBars())
         v.updatePadding(bottom = nav.bottom)
         WindowInsetsCompat.CONSUMED
     }
     ```
     и красить область паддинга цветом фона клавиатуры (чтобы клавиатура «стояла» на прозрачном нав-баре, как Gboard).
  3. Для жестовой навигации дополнительно: `window.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }` по необходимости и учесть `Type.mandatorySystemGestures()` — нижняя жестовая зона конфликтует со свайпами по пробелу; клавиши нижнего ряда не должны требовать точных тапов в самых нижних 8–10dp.
- `onComputeInsets(outInsets)` — отдельная, «обратная» вещь: ею IME сообщает приложению, какая часть окна IME реально занята (важно, если окно IME выше клавиатуры из-за оверлея превью — см. §2B; иначе приложение решит, что клавиатура огромная).
- Тестировать на API 35/36 эмуляторе в трёх режимах: gesture nav, 3-button nav, ландшафт.

## 9. Итоговая архитектура UI-слоя (рекомендация)

```
TatarImeService : InputMethodService
 └─ onCreateInputView() → InputViewContainer (FrameLayout, insets-паддинг)
     ├─ KeyboardView : View        // Canvas: клавиши, подсветка, превью-оверлей
     │   ├─ KeyboardLayout (модель: List<Row<Key>>, доли ширины)
     │   ├─ PointerTracker[MAX_POINTERS]  // multi-touch, таймеры long-press/repeat
     │   ├─ KeyPreviewDrawer      // превью в том же слое
     │   └─ PopupPanelDrawer      // long-press альтернативы, slide-to-select
     └─ KeyboardTheme (light/dark, iOS-стиль)
```

- Раскладки — данные (JSON/Kotlin), не XML `<Keyboard>`.
- Настройщик (Activity) — можно на Compose.
- Референс-код: HeliBoard (GPL-3.0 — только как справочник, если своя лицензия не GPL), AOSP LatinIME (Apache-2.0 — можно заимствовать код с указанием).
- К `KeyboardView` обязательно подключается accessibility-делегат (см. §10) — без него вся клавиатура для TalkBack один «глухой» прямоугольник.

---

## 10. Доступность (TalkBack) кастомной Canvas-клавиатуры

Выбранная архитектура «один View, все клавиши на Canvas» по умолчанию **невидима для accessibility-сервисов**: TalkBack видит один прямоугольник без текста и без дочерних элементов. Незрячий пользователь не сможет ни найти клавишу, ни нажать её. Это ровно та причина, по которой в §1.1 обёртка hijamoya помечена «без поддержки accessibility» — и та же проблема будет у нашего View, если не заложить поддержку на этапе проектирования. Для клавиатуры, адресованной языковому сообществу (среди которого есть незрячие и слабовидящие пользователи), и для качества карточки в Play это обязательная часть v1, а не «когда-нибудь потом»: стоимость почти нулевая, если закладывать сразу, и болезненная, если прикручивать к готовой геометрии.

### 10.1. Виртуальные клавиши: ExploreByTouchHelper или свой node provider

Официальный механизм для «нарисованных» элементов — **виртуальная иерархия accessibility-узлов** (`AccessibilityNodeProvider`): каждый `Key` экспонируется как виртуальный узел со своими границами, текстом и действием `ACTION_CLICK`. Документация Android прямо приводит экранную клавиатуру AOSP как канонический пример такого подхода.

Два пути:

**A. `androidx.customview.widget.ExploreByTouchHelper`** — рекомендуемый для нового кода (это готовый `AccessibilityNodeProviderCompat` + управление accessibility-фокусом). Схема:
- создать хелпер, привязать `ViewCompat.setAccessibilityDelegate(keyboardView, helper)`;
- в `KeyboardView` переопределить `dispatchHoverEvent()` (и `dispatchKeyEvent()`/`onFocusChanged()` для навигации с внешней клавиатуры/D-pad), делегируя хелперу;
- реализовать 4 метода: `getVirtualViewAt(x, y)` → id клавиши по координатам (переиспользовать наш же hit-детектор из §4), `getVisibleVirtualViews()` → список id всех клавиш текущей раскладки (порядок = порядок обхода фокусом), `onPopulateNodeForVirtualView()` → `contentDescription`, `setBoundsInParent(key.hitBox)`, `addAction(ACTION_CLICK)`, `onPerformActionForVirtualView()` → по `ACTION_CLICK` закоммитить клавишу;
- при нажатии посылать `sendEventForVirtualView(id, TYPE_VIEW_CLICKED)`, при смене раскладки/shift — `invalidateRoot()` (узлы и описания меняются: «а» → «А»).

**B. Свой делегат по образцу LatinIME** — пакет `accessibility/` в AOSP LatinIME: `KeyboardAccessibilityDelegate` (базовый), `MainKeyboardAccessibilityDelegate`, `KeyboardAccessibilityNodeProvider`, `KeyCodeDescriptionMapper`, `AccessibilityUtils`. Как это устроено (полезно понимать, даже если берём ExploreByTouchHelper):
- при включённом «исследовании касанием» View получает не touch-, а **hover-события**; делегат мапит `ACTION_HOVER_ENTER/MOVE/EXIT` в клавиши через тот же `KeyDetector`;
- вход пальца на клавишу → node provider шлёт `TYPE_VIEW_HOVER_ENTER` + `ACTION_ACCESSIBILITY_FOCUS` — TalkBack озвучивает клавишу;
- **lift-to-type**: отпускание пальца над клавишей (`ACTION_HOVER_EXIT`) коммитит её — делегат синтезирует пару `ACTION_DOWN`/`ACTION_UP` в центр hit-box и прогоняет через обычный `onTouchEvent` («avoids the complexity of trackers and listeners» — вся логика §4 переиспользуется);
- `sendWindowStateChanged(text)` — озвучивание событий уровня клавиатуры: смена языка/раскладки/режима (`MainKeyboardAccessibilityDelegate` анонсирует смену subtype, режима поля и shift-состояния, намеренно глуша «шумные» переходы вроде авто-shift).

Оценка объёма: весь пакет `accessibility/` LatinIME — ~6 небольших классов; для нашей клавиатуры с ExploreByTouchHelper это порядка 150–250 строк. Закладывать в архитектуру §9 как `KeyboardAccessibilityDelegate`, подключаемый к `KeyboardView`.

### 10.2. Озвучивание татарских букв (ә ө ү җ ң һ)

TalkBack проговаривает `contentDescription` узла через системный TTS. Проблемы:
- **Татарского языка в Google TTS нет** (и на июль 2026 не появился); реальный татарский голос есть только в стороннем **RHVoice** (open-source, есть на F-Droid/Play, татарский в списке поддерживаемых) и в академических моделях TatarTTS/ISSAI (Piper, код языка `tt`). Рассчитывать, что у пользователя стоит RHVoice, нельзя.
- Русский голос Google TTS буквы ә, ө, ү, җ, ң, һ либо молча пропускает, либо читает непредсказуемо — одиночный символ в `contentDescription` («ә») не гарантирует внятного озвучивания.

Fallback-стратегия (дешёвая и рабочая):
1. Для каждой клавиши держать в модели `Key` отдельное поле `a11yLabel` (описание для TalkBack), не выводимое на экран.
2. Для специфических букв — описательные фразы на русском, которые любой русский TTS прочтёт внятно: «татарская э» (ә), «татарская о» (ө), «татарская у» (ү), «татарская жэ» (җ), «н с хвостиком / носовая н» (ң), «һ, мягкая ха» (һ). Точные формулировки согласовать с сообществом незрячих татароязычных пользователей — это дешёвый UX-ресерч с большой отдачей.
3. Проверить фактическое произношение на устройстве: Google TTS (ru), RHVoice (ru и tt). Если установлен движок с татарским — можно передавать саму букву; определяется через `TextToSpeech#isLanguageAvailable(Locale("tt"))`, но для v1 достаточно статических русских описаний.
4. Служебные клавиши — стандартные описания: «удалить», «shift», «ввод», «пробел», «символы», «сменить язык» (у LatinIME это `KeyCodeDescriptionMapper` + строковые ресурсы `spoken_description_*` — готовый список того, что нужно описать).
5. Поля паролей: LatinIME `AccessibilityUtils.shouldObscureInput()` — при вводе пароля без гарнитуры вместо буквы озвучивается заглушка; для v1 можно отложить, но помнить.

### 10.3. Размеры touch-целей: 48dp guideline vs 29dp из §7

Официальная рекомендация Android/Material — touch-цели **минимум 48×48dp** (жёсткий минимум для прохождения проверок доступности — 24×24dp, это же требование WCAG 2.2 AA «Target Size»). Клавиатуры — признанное исключение: даже у Gboard видимая ширина буквенной клавиши на узком экране ~32–36dp. Как честно совместить:
- **Высота ряда** — полностью под нашим контролем: 54–64dp из §7 удовлетворяет 48dp по вертикали; не опускать высоту ряда ниже 48dp даже при пользовательском множителе 0.85 (минимум множителя подобрать так, чтобы ряд оставался ≥48dp).
- **Ширина**: ориентир из §7 «не ниже ~29dp» оставить как абсолютный минимум отрисовки, но правило принятия решения такое: если расчётная ширина клавиши < ~33–34dp (ширина 320dp / 11 клавиш ≈ 29dp — уже за гранью), выносить доп. буквы в long-press, а не добавлять 12-ю колонку. То есть 12-клавишный ряд с ә/ө допустим на экранах ≥ ~390dp, на узких — 10–11 клавиш + long-press.
- **Hit-зоны без зазоров** (§4) — сами по себе a11y-фича: эффективная цель больше видимой клавиши; границы виртуальных узлов задавать по hit-box, а не по видимому прямоугольнику.
- В Play Console работает pre-launch report с проверками доступности (на базе Accessibility Scanner) — предупреждения о маленьких целях и отсутствии описаний попадают в отчёт и косвенно влияют на качество карточки; прогнать Accessibility Scanner перед релизом.

### 10.4. Key preview и long-press при включённом TalkBack

- **Key preview (§2)**: при активном исследовании касанием палец «ездит» по клавиатуре, генерируя hover, а коммит происходит по отпусканию — превью, вырастающее из-под пальца на каждый hover, бесполезно (не видно незрячему) и вредно (слабовидящему мельтешит). LatinIME в accessibility-режиме полагается на речевые анонсы, а не на превью. Правило: если `AccessibilityManager.isTouchExplorationEnabled` — превью не показывать (или показывать только по фактическому коммиту), информация идёт голосом.
- **Long-press попап (§3)**: обычный таймер long-press не сработает — до View доходят hover-события, а не `ACTION_DOWN`. У TalkBack жест «двойной тап с удержанием» (double-tap and hold) транслируется в настоящие touch-события — наш обычный код §3 отработает. LatinIME дополнительно поддерживает программный путь: `MainKeyboardAccessibilityDelegate.performLongClickOn(key)` синтезирует `ACTION_DOWN` + прямой вызов `onLongPressed()`, а после закрытия панели запоминает hit-box клавиши в `mBoundsToIgnoreHoverEvent`, чтобы hover при отрыве пальца не нажал её повторно — этот нюанс (подавление «хвостового» hover-exit после long-press) стоит скопировать.
- Панель альтернатив (§3B, отрисовка в своём оверлее) обязана тоже быть виртуальными узлами: при открытии панели сообщить об этом (`sendWindowStateChanged` / анонс «доступны варианты: ә, а…»), её клавиши добавить в `getVisibleVirtualViews`, при закрытии — `invalidateRoot()`. Иначе long-press-буквы будут недоступны незрячим — а для узких экранов это единственный путь к ә/ө (§10.3).
- Слайдовые жесты (slide-to-select, свайп по пробелу) при TalkBack недоступны по определению — все функции, висящие на жестах, должны иметь дублёр: отдельную клавишу или пункт long-press-панели.

### 10.5. Чеклист v1

1. `ExploreByTouchHelper` на `KeyboardView`: узлы = клавиши, границы = hit-box, `ACTION_CLICK` = commit.
2. `a11yLabel` в модели `Key`; русские описательные фразы для ә ө ү җ ң һ; `spoken_description_*` для служебных клавиш.
3. Анонс смены раскладки/shift/языка через window-state-события; глушить шумные авто-переходы.
4. При `isTouchExplorationEnabled`: отключить key preview, поддержать double-tap-and-hold для long-press-панели, панель альтернатив — тоже виртуальные узлы.
5. Высота ряда ≥48dp всегда; ширина <34dp → буква уходит в long-press.
6. Тест: включить TalkBack, вслепую набрать «әни» и «җиһан»; прогнать Accessibility Scanner.

---

## Источники

- KeyboardView (deprecated, API reference): https://developer.android.com/reference/android/inputmethodservice/KeyboardView
- Диф API 29 (факт депрекации): https://developer.android.com/sdk/api_diff/29/changes/android.inputmethodservice.KeyboardView
- hijamoya/KeyboardView (копия AOSP-виджета): https://github.com/hijamoya/KeyboardView
- AOSP LatinIME, пакет keyboard (PointerTracker, MoreKeysKeyboard, KeyPreviewChoreographer): https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/master/java/src/com/android/inputmethod/keyboard/
- LatinIME PointerTracker.java: https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/master/java/src/com/android/inputmethod/keyboard/PointerTracker.java
- HeliBoard (форк OpenBoard/AOSP, Canvas-рендеринг): https://github.com/Helium314/HeliBoard
- FlorisBoard (Compose-IME, тема-движок Snygg): https://github.com/florisboard/florisboard
- Compose в IME — проблема ViewTreeLifecycleOwner и решение: https://www.androidbugfix.com/2022/08/inputmethodservice-with-jetpack-compose.html
- Itsuki, «Jetpack Compose: Custom System Wide Keyboard»: https://medium.com/@itsuki.enjoy/jetpack-compose-custom-system-wide-keyboard-248da4ff8de4
- Maksym Koval, «Implementation of a custom soft keyboard in Android using Compose»: https://medium.com/@maksymkoval1/implementation-of-a-custom-soft-keyboard-in-android-using-compose-b8522d7ed9cd
- Gist ComposedInputMethodService: https://gist.github.com/LennyLizowzskiy/51c9233d38b21c52b1b7b5a4b8ab57ff
- Google Play + Compose (память на low-end, «two stack problem», baseline profiles): https://android-developers.googleblog.com/2022/03/play-time-with-jetpack-compose.html
- Сравнение метрик Compose vs Views (официально): https://developer.android.com/develop/ui/compose/migrate/compare-metrics
- Baseline Profiles (≈30% к скорости исполнения): https://developer.android.com/topic/performance/baselineprofiles/overview
- Compose performance (паритет по jank с 1.9): https://developer.android.com/develop/ui/compose/performance
- Edge-to-edge enforcement в Android 15 (советы по инсетам): https://medium.com/androiddevelopers/insets-handling-tips-for-android-15s-edge-to-edge-enforcement-872774e8839b
- WindowInsets (официальная документация): https://developer.android.com/develop/ui/compose/system/insets
- Кейс IME, перекрытой нав-баром на API 35 (codeboard #137): https://github.com/gazlaws-dev/codeboard/issues/137
- InputMethodService.onComputeInsets: https://developer.android.com/reference/android/inputmethodservice/InputMethodService#onComputeInsets(android.inputmethodservice.InputMethodService.Insets)
- Make custom views more accessible (виртуальные узлы, пример — экранная клавиатура AOSP): https://developer.android.com/guide/topics/ui/accessibility/custom-views
- ExploreByTouchHelper (androidx.customview, API reference): https://developer.android.com/reference/kotlin/androidx/customview/widget/ExploreByTouchHelper
- AOSP LatinIME, пакет accessibility (KeyboardAccessibilityDelegate, MainKeyboardAccessibilityDelegate, KeyCodeDescriptionMapper): https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/master/java/src/com/android/inputmethod/accessibility/
- LatinIME KeyboardAccessibilityDelegate.java (hover → lift-to-type, синтез touch-событий): https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/01748cde4e692c970617e4478368f83b710a86b6/java/src/com/android/inputmethod/accessibility/KeyboardAccessibilityDelegate.java
- Touch target size (48dp guideline, 24dp минимум): https://support.google.com/accessibility/android/answer/7101858
- Accessibility Scanner / pre-launch report: https://developer.android.com/guide/topics/ui/accessibility/testing
- RHVoice (open-source TTS с татарским языком): https://github.com/RHVoice/RHVoice
- TatarTTS / ISSAI (открытые TTS-модели татарского, Piper): https://github.com/IS2AI/TatarTTS
