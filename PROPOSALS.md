# Развитие Tatar Keyboard

## Решение

Следующий продуктовый майлстоун — D1: opt-in полоса из трёх татарских подсказок на
основе Leipzig top-100k. D0 показал 97.3318% покрытия принятых токенов и gzip-размер
около 582 КБ. Top-150k допустим только как offline comparator качества, но в D1 не
поставляется.

После завершения D1 порядок фиксирован: D2 — русский словарь, D3 — автозамена с
отменой. Персональный словарь, Zamanälif и пересмотр пятого ряда — позднее по спросу.

Пользовательский результат D1: при разрешённом татарском вводе над клавиатурой видны
до трёх частотных продолжений текущей словоформы. Тап явно заменяет введённый префикс
выбранной формой. Любой отказ словаря отключает только подсказки; обычный ввод всегда
остаётся рабочим.

## Статус выполнения

Обновлено 24.07.2026. Работа ведётся в `codex/d1-sequential`; D1e зафиксирована в
локальной истории до `a277283`, release hardening 1.2.0 и правки по шести находкам
независимого аудита выполняются поверх неё. Ветка остаётся впереди
`origin/codex/d1-sequential`; merge/tag/release ещё не выполнялись.

| Фаза | Статус | Коммит | Примечание |
|---|---|---|---|
| D1a — формат, generator, top-100k asset | ✅ выполнено | `95f98d3` | воспроизводимый asset, строгий validator, held-out gap 0.72 п.п. |
| D1b — атомарное сжатое хранилище | ✅ выполнено | `037f3ea` | lease/lifecycle, fail-closed, device-protected storage |
| D1c — сенсорная Canvas-полоса | ✅ выполнено | `f47000a` | 40dp, insets/touchable-region, a11y virtual nodes |
| D1d — mmap prefix engine | ✅ выполнено | `80f332c` | latest-only/coalescing, immutable prefix, без гонок |
| D1e — интеграция, opt-in, privacy, a11y | ✅ выполнено | `7c7777c`, `a277283` + hardening + audit fixes | runtime interleavings, warm-engine re-lookup и preparing/unavailable state устранены; шесть подтверждённых находок независимого аудита закрыты; независимое ревью правок в трёх линзах (контракт / регресс / крайние случаи) — APPROVED_WITH_NOTES без блокеров |
| D1f — общие gates + device-UAT | 🟡 частично | — | artifact gate пройден; device-UAT есть только на эмуляторе и PSS-бюджет там провален; Samsung/One UI остаётся обязательным |

Текущий прогон на Mac, Gradle 9.6: весь JVM-набор — **177 tests, 0 failures/errors**,
`lintVitalRelease --rerun-tasks` — **BUILD SUCCESSFUL**.

Финальный artifact gate v1.2.0 — **PASS**: `dist/tatar-keyboard-1.2.0.apk`,
**1 446 019 байт**, SHA-256
`4960b85072d4db64669d63e7755e89cefaf295a7a12e6fcb0b889775543d3772`,
versionName `1.2.0` / versionCode `4`, единственное запрошенное permission —
`android.permission.VIBRATE`, подпись APK Signature Scheme **v2 only** (один signer,
RSA 4096, `CN=Tatar Keyboard`), сертификат SHA-256
`cdd8c5350ddc86f13cd89b5bfb55ca33c13efba77beb4d4ccb75d5e6b961b09e` совпадает с
историческим релизным, `apksigner verify` → `Verifies`. Это не device evidence.
Подробности — `docs/DICTIONARY-D1E.md` и `docs/PUBLISH-CHECKLIST.md`.

Device-UAT выполнен **только на эмуляторе** Android API 35 (AVD
`tatar_keyboard_d1f_api35_arm64`, Pixel 3a, google_apis arm64, headless,
software-рендеринг swiftshader). Функционально всё зелёное, включая проверки по свежим
исправлениям (тап после смены subtype, регистр, курсор в середине слова, гашение полосы
жестами); полоса измерена как ровно 40dp (`contentTopInsets` 1294 против 1184 при
density 440); холодный старт 124–147 мс при бюджете 400 мс; крэшей/ANR нет. Но **бюджет
PSS ≤ 30 МБ там провален**: 33,4–33,6 МБ с включённой D1 против 29,2–29,4 МБ с
выключенной, то есть фича стоит ≈ +4,2 МБ PSS. Эмулятор не заменяет Samsung/One UI, а
абсолютные значения памяти и jank на программном рендерере недостоверны — достоверна
только дельта ВКЛ/ВЫКЛ. Требуется перезамер на реальном устройстве, прежде чем считать
это релиз-блокером или артефактом эмулятора.

Версия 1.2.0/vc4 и changelog подготовлены. Перед фактическим выпуском остаются device-UAT
на реальном Samsung (включая перезамер PSS и jank), отдельно подтверждённая вычитка
новых татарских строк носителем языка, push/merge/tag и публикация.

## Устранённые D1e runtime-блокеры

- ✅ **HIGH — readiness/eligibility lifecycle.** Readiness callback сериализован через
  UI owner, запускает engine в уже активной eligible session и запрашивает текущий cached
  prefix; переход ineligible→eligible запускает engine. Поздний callback после destroy
  ничего не запускает и не запрашивает.
- ✅ **HIGH — atomic candidate binding.** Displayed candidates атомарно привязаны к
  exact displayed prefix/session. При переходе text A→text B binding старых A немедленно
  инвалидируется; tap(old A) после B — no-op и не может выполнить
  `commit(B, candidateA)`.
- ✅ **Hardening — warm-engine lifecycle re-entry.** При `tt→ru→tt` и при открытии
  нового eligible field уже опубликованный engine немедленно повторно запрашивает
  текущий известный непустой cached prefix. Cold и in-flight engine не получают
  дублирующий запрос: их единственный lookup выполняется после публикации engine.
- ✅ **Hardening — finished/preparing fail-closed state.** `onFinishInput` закрывает
  eligibility до позднего readiness callback; такой callback не запускает engine. При
  подготовке словаря или неуспешном engine полоса остаётся `GONE`/0dp и резервируется
  только после успешной публикации engine.

Реализованные детерминированные regression tests, закрывающие D1e:

- ✅ eligible start before prepare → readiness callback запускает engine и lookup текущего
  prefix;
- ✅ ineligible start → переход на tt subtype запускает engine и lookup текущего prefix;
- ✅ late readiness callback after destroy → engine/lookup не запускаются;
- ✅ `show(A) → text(B) → tap(old A)` → текст не изменяется;
- ✅ актуальный result B и tap(B) по-прежнему безопасно выполняют commit.
- ✅ `tt→ru→tt` с warm engine → текущий prefix запрашивается повторно без restart или
  cold-engine duplicate.
- ✅ readiness после `onFinishInput` → engine/lookup не запускаются, полоса остаётся
  скрытой.
- ✅ уже поставленный в очередь engine publish после `onFinishInput` → handle немедленно
  получает `finishInput`, lookup/reserve не выполняются.
- ✅ новый eligible field с retained warm engine → текущий cached prefix
  запрашивается ровно один раз без restart factory и без дополнительного keystroke.

Все пять обязательных тестов, `tapWithNothingDisplayedIsNoOp` и
`subtypeChangeToEligibleWithWarmEngineReRequestsCurrentPrefix`,
`eligibleStartWithWarmEngineReRequestsCurrentPrefix`, а также fail-closed тест
readiness после finish проходят; D1e закрыто.
Полная D1f artifact/device matrix остаётся открытой.

## Устранённые находки независимого аудита D1

Аудит подтвердил шесть дефектов; все закрыты и покрыты детерминированными JVM-тестами.

- ✅ **HIGH — tap-listener не переустанавливался при активации полосы через смену
  subtype.** Вью полосы инфлейтится лениво именно из `setTapListener`, а ветка
  ineligible-старта его не ставила, поэтому после переключения глобусом на татарский в
  уже открытом поле тап по кандидату ничего не делал. `onSubtypeChanged(eligible = true)`
  регистрирует listener заново (`subtypeChangeToEligibleRewiresTapListenerSoTapsStillCommit`).
- ✅ **HIGH — отсутствовала проверка буквы сразу после курсора.** Подсказка в середине
  слова портила текст. Добавлен правый контекст: `EditorSurface.hasLetterAfterCursor()`
  поверх `RichInputConnection.getCachedTextAfterCursor()` и чистого предиката
  `TatarWordUtils.startsWithWordCharacter` (первый кодпоинт: буква или комбинирующая
  метка). Контроллер при `true` не идёт в движок и оставляет пустую полосу,
  `InputLogic.commitChosenSuggestion` дублирует проверку fail-closed перед
  `deleteTextBeforeCursor`.
- ✅ **HIGH — контракт регистра не был реализован.** Добавлены
  `PrefixCasing`/`classifyCasing`/`applyCasing`: MIXED-префикс даёт 0 результатов без
  запроса к движку, регистр применяется к отранжированным кандидатам от `pendingPrefix`
  перед `showSuggestions`, а `expectedPrefix` остаётся сырым — показанная и вставленная
  строка совпадают.
- ✅ **MEDIUM — внутренние жесты курсора и свайп-удаления не гасили полосу.** Они идут
  через `RichInputConnection`, который сам держит expected selection, поэтому
  `onUpdateSelection` не видел внешнего перемещения. `onMoveCursorPointer`,
  `onMoveDeletePointer`, `onUpWithDeletePointerActive` и `onUpWithSpacePointerActive`
  теперь уведомляют контроллер напрямую через приватный
  `onSuggestionsAffectingCursorMove()`.
- ✅ **MEDIUM — TalkBack объявлял подсказки на каждое нажатие.** Объявление тройки
  выполняется только на переходе «пустая полоса → есть подсказки» и только при включённом
  touch exploration; сами слова остаются доступны через virtual nodes в любой момент.
- ✅ **MEDIUM — комбинирующие метки NFD обрывали границу слова.** Ряд слова продолжается
  через метки Mn/Mc/Me, орфанные метки без базовой буквы отбрасываются.

## Инварианты и бюджеты D1

- Функция работает только для татарского subtype, opt-in, default OFF.
- UI — один лениво создаваемый custom suggestion strip `View`: Canvas, 40dp, три равные
  ячейки и три стабильных virtual accessibility node ID; пустые nodes скрыты. Отдельных
  View на ячейку нет.
- В hot `draw`/`touch` пути — ноль аллокаций; janky frames ≤ 1%.
- Тап выполняет только явный `delete + commit`. Composing и автозамены в D1 нет.
- Новый Android-код — Kotlin с минимальным Java interop; новых зависимостей и NDK/JNI нет.
- Распаковка, checksum, mmap и lookup не выполняются на UI/cold-start пути.
- Prefix lookup compute p95 ≤ 5 мс; полный request→publish warm p95, включая очередь и
  UI dispatch, ≤ 16 мс; показанных stale results — 0.
- Сжатый asset ≤ 700 КБ; распакованный словарь ≤ 2.8 MiB.
- Целевой APK после D1 ≤ 1.7 МБ; абсолютный hard limit ≤ 3 MiB.
- Total PSS показанной клавиатуры ≤ 30 МБ; холодный старт < 400 мс.
- INTERNET permission, сетевые операции, аналитика и логирование введённого текста
  отсутствуют.
- Исходные корпусные архивы и `words.txt` не хранятся в проекте.

Превышение hard limit, privacy breach, изменение текста при stale state или поломка
обычного ввода — безусловный no-go.

Фактическое состояние бюджетов: APK 1 446 019 байт — в пределах и hard limit, и целевых
1,7 МБ; холодный старт на эмуляторе 124–147 мс; PSS на эмуляторе выходит за 30 МБ и
требует перезамера на реальном устройстве; latency, allocation и jank на железе не
измерялись. Подробности и оговорки — в разделе D1f.

## Состояния полосы

| Условие | Полоса | Высота | Lookup | Ячейки |
|---|---|---:|---|---|
| Настройка OFF | `GONE` | 0dp | нет | отсутствуют |
| Privacy gate запрещает подсказки | `GONE` | 0dp | нет | отсутствуют |
| Активный subtype не татарский | `GONE` | 0dp | нет | отсутствуют |
| Настройка ON, ввод разрешён, словарь готов, 0 результатов | видима | 40dp | да | все пустые и inert |
| Те же условия, 1–3 результата | видима | 40dp | да | только заполненные clickable/a11y |
| Словарь готовится или недоступен | `GONE` | 0dp | нет | отсутствуют |

Переход между 0 и 1–3 результатами не меняет высоту. Пустая ячейка не получает hit
target/focus и не экспонирует свой virtual node. При уходе в `GONE` результаты и
generation немедленно очищаются.

## Контракт текста

- Boundary-анализ выполняется прямо по сырому тексту snapshot, без промежуточной
  нормализации: ряд слова продолжается через комбинирующие метки (Mn/Mc/Me), поэтому
  канонически разложенный (NFD) ввод не обрывает слово, а результат остаётся точным span
  исходного текста — именно это делает удаление безопасным. Нормализация к NFC
  выполняется отдельно, уже на этапе lookup.
- Текущий префикс — максимальная непрерывная последовательность букв татарского
  кириллического алфавита непосредственно перед collapsed cursor. Классификация
  выполняется по `Character.isLetter`, то есть шире алфавита (латиница и русская
  кириллица тоже считаются буквами); такие префиксы просто не находятся в словаре и дают
  0 результатов.
- Пробел, цифра, пунктуация, дефис, апостроф и любой символ вне алфавита завершают слово.
  Комбинирующая метка без базовой буквы словом не считается: ведущие орфанные метки
  отбрасываются, а ряд без единой буквы даёт пустой префикс.
- При selection, пустом префиксе или букве сразу после cursor результаты очищаются: ввод
  в середине слова и замена выделения не поддерживаются. Реализация намеренно шире
  контракта и fail-closed: границей считается любая буква (в том числе латинская и
  русская) и любая комбинирующая метка сразу после курсора — слишком узкая проверка
  портит текст, слишком широкая лишь не показывает подсказку.
- Для lookup префикс приводится к NFC и lowercase. Словарь хранит NFC lowercase.
- Все lowercase-буквы дают lowercase формы. Одна заглавная буква либо первая заглавная
  и остальные lowercase дают Initial Caps. Две и более заглавные без lowercase дают
  ALL CAPS. Любой другой смешанный регистр даёт 0 результатов.
- Отображаемая и вставляемая форма имеют одинаковый регистр.
- Форма, в точности равная уже набранному нормализованному слову, исключается; более
  длинные формы с тем же префиксом остаются кандидатами.
- Кандидаты ранжируются по frequency descending, затем по Unicode code-point lexical
  ascending. Регистр отображения применяется только после ранжирования.
- Перед тапом повторно проверяются editor session, collapsed selection, subtype, generation,
  отсутствие буквы сразу после курсора и точный префикс. Удаление выполняется по code
  points; несовпадение отменяет действие.

Фактическое покрытие этих правил тестами:

- границы слова — `TatarWordUtilsTest`: татарские буквы, русская кириллица, смешанные
  скрипты, пробел/цифра/пунктуация, NFD (метка внутри слова, слово, оканчивающееся
  меткой, разложенное «ё», орфанная метка) и совпадение возвращённого span с сырым
  текстом;
- регистр — `TatarWordUtilsTest` (`classifyCasing`/`applyCasing`, включая caseless-метки и
  round-trip «классифицировать → применить») плюс сквозные
  `SuggestionsControllerTest.lowerCasePrefixShowsAndCommitsDictionaryFormUnchanged`,
  `initialCapsPrefixShowsAndCommitsInitialCapsForms`,
  `allCapsPrefixShowsAndCommitsAllCapsForms`, `mixedCasePrefixYieldsNoRequestAndAnEmptyBand`;
- курсор внутри слова — `TatarWordUtilsTest.startsWithWordCharacter*` (пусто, пробел,
  запятая, перевод строки, цифра, дефис, emoji, буква, ведущая метка) и
  `SuggestionsControllerTest.letterAfterCursorClearsResultsAndNeverRequests`,
  `tapIsNoOpWhileTheCursorSitsInsideAWord`, `nonLetterAfterCursorKeepsRequestingAsBefore`;
  отказ commit-пути зафиксирован source-contract тестом
  `commitPathRefusesToReplaceAWordTheCursorSitsInside`;
- exact-word exclusion и frequency/code-point ties — `TdictPrefixIndexTest`;
- selection и внутренние жесты клавиатуры —
  `SuggestionsControllerTest.externalSelectionChangeWhileEligibleReservesAndNeverHides`,
  `staleResultDroppedWhenSelectionChangedBumpsSession`,
  `internalCursorGestureUnbindsDisplayedCandidates`.

Не покрыто JVM-тестами и заявляется только как проверка чтением исходника или на
устройстве: guard `hasSelection()` внутри `InputLogic.commitChosenSuggestion` (Android-класс
без JVM-обвязки) и сценарий автозаглавной в начале предложения — он проверялся лишь в
эмуляторном UAT.

## Gate каждой фазы

Перед коммитом любой D1a–D1e обязаны выполняться четыре условия: приложение собирается;
релевантные быстрые тесты фазы проходят; no-INTERNET check проходит на собранном artifact;
APK size delta записана и укладывается в ожидание фазы. Провал любого условия запрещает
коммит и переход дальше. D1f выполняет полный clean build и всю финальную test/metric/UAT
matrix, а не заменяется накопленными быстрыми проверками.

## D1a — формат, generator и финальный top-100k asset

### Scope и deliverable

- Из закреплённых Leipzig `mixed/news/web` выбрать top-100k по frequency descending,
  затем Unicode code-point lexical ascending, после чего пересортировать весь срез
  только в code-point lexical order для binary prefix search.
- Зафиксировать versioned binary contract: magic/schema, count, offsets, UTF-8 word blob,
  u32 frequencies и checksum.
- Добавить воспроизводимый generator, строгий validator, свободно созданные fixtures и
  реальный сжатый top-100k asset.
- Вместе с asset зафиксировать CC BY 4.0 attribution/NOTICE, source URL, corpus
  version/date и полный transformation record.
- На независимом held-out наборе сравнить 50k/100k/150k; 150k используется только в
  локальной оценке.

### Fail-closed acceptance

- Одинаковые входы и версия generator дают байт-в-байт одинаковый asset.
- Malformed input, duplicate, несортированность, overflow, неверная schema или checksum
  завершают генерацию/валидацию ошибкой.
- Asset и распакованный файл укладываются в 700 КБ / 2.8 MiB.
- Coverage top-100k отстаёт от 150k на held-out не более чем на 1 процентный пункт;
  ручной татарский query-set не выявляет систематический русский/технический мусор.
- No-go: невоспроизводимость, неясное происхождение/условия данных или провал size/quality.

## D1b — compressed storage и безопасная смена версии

> Автоматизируемое ядро и отдельная APK delta зафиксированы в
> `docs/DICTIONARY-D1B.md`. JVM/build/privacy gates зелёные, но физические direct-boot,
> power-loss/no-space и startup проверки остаются в D1f; полная device acceptance здесь не
> заявляется.

### Scope и deliverable

- Background pipeline: asset → temp-файл в device-protected `filesDir` → flush/fsync →
  size/checksum/schema validation → atomic rename.
- Новую versioned file полностью валидировать и атомарно публиковать, но не активировать
  вместо живого mapping. Активация разрешена только в следующем безопасном IME lifecycle,
  когда старый executor остановлен и readers отсутствуют.
- Kotlin storage component и тесты first run, повторного запуска, обновления, corruption,
  оборванной записи, нехватки места и direct boot.

### Fail-closed acceptance

- После прерывания доступен прежний валидный файл либо никакой; partial не становится final.
- Ошибка отключает подсказки и не задерживает показ/обычный ввод.
- Нет I/O, checksum или mmap на UI thread; cold start остаётся < 400 мс.
- На диске сохраняются не более current и одной staged/old version; active/current файл
  не удаляется.
- No-go: недоказанная атомарность/direct-boot безопасность или нарушение size/startup.

## D1c — strip/insets/touch spike на Samsung

### Scope и deliverable

- Один lazy Canvas View реализует state table, три hit region и виртуальные a11y nodes.
- `InputView`, combined visible bounds и `onComputeInsets()` учитывают полосу над
  `MainKeyboardView` без перекрытия клавиш.
- Проверяются Samsung, rotation, navigation inset, moreKeys, hardware keyboard и
  повторное создание input view.

### Fail-closed acceptance

- `GONE` даёт 0dp и нулевое layout/touch влияние; видимая полоса стабильно 40dp.
- Все заполненные ячейки полностью touchable; пустые inert; moreKeys и клавиши не теряют
  область касаний.
- TalkBack видит только заполненные ячейки как отдельные кнопки с полным словом.
- Allocation test подтверждает 0 аллокаций в hot draw/touch; frame test — janky ≤ 1%.
- No-go: недоступная ячейка на Samsung, неверные insets, a11y gap, аллокации или jank.

## D1d — mmap prefix engine и bounded threading

### Scope и deliverable

- Binary prefix range по mmap; exact typed word исключается; top-3 ранжируется по
  frequency descending, затем Unicode code-point lexical ascending.
- Один bounded latest-only/coalescing executor: максимум один running и один latest
  pending request; промежуточные запросы заменяются, очередь не растёт.
- Generation включает editor session, subtype, prefix и dictionary version; publish
  разрешён только при полном совпадении.
- `FileChannel` закрывается сразу после read-only `map`. Активный mapping immutable и не
  hot-swap-ится в живом engine session; новая опубликованная версия выбирается только в
  следующем safe lifecycle до запуска readers.
- Finish input очищает pending work и инвалидирует generation. Destroy дополнительно
  останавливает executor; strong references на mapping сбрасываются только после readers.
  Фактический unmap GC-dependent и не считается управляемой операцией public API minSdk 24.
  Старый versioned file удаляется только когда он не active/current.

### Fail-closed acceptance

- Compute p95 ≤ 5 мс, request→publish p95 ≤ 16 мс, stale publish = 0.
- Burst/race tests доказывают bounded queue, coalescing, subtype/editor/version guards и
  отсутствие use-after-switch.
- Repeated lifecycle tests подтверждают: open FD не растут, retained disk ≤ current + одна
  staged/old version, PSS не растёт без границ; hot-swap mapping не происходит.
- Ranking tests подтверждают exact-word exclusion и frequency-desc/code-point-lexical ties.
- Corrupt/no-match/empty input возвращает 0 результатов без влияния на ввод.
- No-go: unbounded backlog, lifecycle leak, use-after-close, stale result или latency miss.

## D1e — интеграция, настройка, privacy и accessibility

### Scope и deliverable

- Настройка default OFF и строки en base/ru/tt.
- Eligibility: татарский subtype, готовый словарь и
  `InputAttributes.mShouldShowSuggestions == true`.
- Сохраняются gates для password, email, URI, filter, `NO_SUGGESTIONS` и autocomplete.
- Prefix берётся из согласованного snapshot `RichInputConnection`, без sync editor IPC.
- Реализуются state table, текстовый контракт, безопасный тап `delete + commit` и
  accessibility announcements.

### Fail-closed acceptance

- При OFF/privacy/non-tt словарь не запрашивается, полоса `GONE`, текст не логируется.
- Смена subtype/editor/selection немедленно очищает UI и инвалидирует запросы.
- Stale tap или рассинхрон кэша не меняет текст.
- Полная editor/privacy matrix, word-boundary/casing и TalkBack tests зелёные.
- Все пять детерминированных regression tests из раздела runtime-блокеров обязательны и
  зелёные; дополнительные no-op, warm-engine subtype-return и late readiness/publish
  after finish тесты также зелёные. Без них D1e не выполнено, а D1f, merge и release
  запрещены.
- No-go: обход gate, default не OFF или изменение текста без повторной валидации.

## D1f — общие gates и device-UAT

**Gate:** D1f разблокирована: оба D1e runtime-блокера, warm-engine gap и шесть находок
независимого аудита устранены, все детерминированные regression tests зелёные (177 JVM).

- Build/lint/no-INTERNET; signed APK, PSS, cold start, compute и end-to-end latency.
- Allocation/frame tests полосы; race/lifecycle/failure/editor regression suite.
- Samsung UAT: 0–3 results, все касания, TalkBack, moreKeys, rotation, subtype, selection,
  быстрый ввод/backspace/cursor, password и first-run/update/corruption/direct boot.

### Что закрыто

- ✅ **Artifact gate — PASS.** 177 JVM-тестов, `lintVitalRelease` BUILD SUCCESSFUL, clean
  build, no-INTERNET, подпись v2 с историческим сертификатом, versionName/versionCode,
  размер и SHA-256 подтверждены; числа — в разделе «Статус выполнения» и в
  `docs/PUBLISH-CHECKLIST.md`.

### Что открыто

- ⏳ **Device-UAT на реальном Samsung/One UI.** Выполнен только эмуляторный прогон
  (API 35, Pixel 3a, arm64, swiftshader) — это ЧАСТИЧНОЕ свидетельство. Функционально
  всё зелёное (0–3 результата, тап после смены subtype, заглавные, пустая полоса в
  середине слова, гашение полосы жестами, `GONE` в поле пароля, крэшей/ANR нет), полоса
  измерена как ровно 40dp, холодный старт 124–147 мс. У One UI своя оболочка
  IME-хостинга, свои insets и свой переключатель раскладок, поэтому эмулятор эти
  проверки не закрывает.
- ⏳ **Бюджет PSS.** На эмуляторе он **провален**: 33,4–33,6 МБ с включённой D1 против
  29,2–29,4 МБ с выключенной при бюджете 30 МБ. Абсолютные значения на программном
  рендерере завышены (native heap раздут буферами Skia), достоверна дельта ≈ +4,2 МБ.
  Требуется перезамер на реальном железе; до него статус бюджета не определён.
- ⏳ **Jank и allocation-бюджеты.** На swiftshader janky-кадры 25,93% при Slow UI thread
  = 0 — профиль софтверного GPU, а не подтверждённые рывки. Статус NOT_COVERED.
- ⏳ **Не проверено вообще:** TalkBack и реальный обход `ExploreByTouchHelper`, поворот и
  альбомная ориентация, split-screen, физическая клавиатура, direct boot, несколько
  пользователей, тёмная тема, другие плотности экрана, смена системной локали.

Фаза проходит только при соблюдении всех общих бюджетов и phase-specific acceptance.
Любой отказ словаря обязан оставлять полностью рабочий обычный ввод.

## Порядок коммитов

1. D1a — `feat(dictionary): add reproducible top-100k asset format`
2. D1b — `feat(dictionary): add atomic compressed dictionary storage`
3. D1c — `feat(suggestions): add touchable Canvas suggestion strip`
4. D1d — `feat(dictionary): add bounded mmap prefix engine`
5. D1e — `feat(suggestions): integrate opt-in Tatar suggestions`
6. D1f — `test(suggestions): complete D1 gates and device UAT`

Коммиты D1a–D1e создаются только после общего per-phase gate и acceptance своей фазы.
D1f создаётся только после полного clean matrix. Следующая фаза не начинается раньше.

## D1 done

D1 завершён, когда на Samsung пользователь может включить функцию, получить и безопасно
выбрать top-3 в обычном татарском поле; state table, text/casing contract, privacy/a11y,
atomic storage, bounded threading и failure fallback доказаны тестами/UAT; все APK/PSS/
startup/latency/allocation/jank бюджеты соблюдены. Частичный happy path не считается D1.

## После D1

- D2: отдельный русский словарь на той же архитектуре, только при сохранении общего
  APK/PSS/startup запаса и строгом переключении по subtype.
- D3: отдельная opt-in автозамена без composing; немедленный backspace восстанавливает
  исходный ввод, любое другое действие инвалидирует revert.
- Позднее по спросу: персональный словарь, Zamanälif, порядок пятого ряда.

## Non-goals D1

- Поставка 150k/250k без нового held-out evidence; 150k разрешён только как comparator.
- Русский словарь, автозамена, персональный словарь или Zamanälif до D1 done.
- Composing, AOSP `.dict`, NDK/JNI, преждевременные trie/DAWG.
- Новые runtime/build dependencies; sync unpack/checksum/mmap/lookup на cold/UI path.
- Сеть, аналитика, логирование ввода.
- Корпусы без подтверждённых совместимых условий, corpus.tatar без таких условий,
  apertium/GPL и хранение исходных лицензируемых корпусов в проекте.
