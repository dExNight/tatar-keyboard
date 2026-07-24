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
локальной истории до `a277283`, release hardening 1.2.0 выполняется поверх неё. Ветка
остаётся впереди `origin/codex/d1-sequential`; merge/tag/release ещё не выполнялись.

| Фаза | Статус | Коммит | Примечание |
|---|---|---|---|
| D1a — формат, generator, top-100k asset | ✅ выполнено | `95f98d3` | воспроизводимый asset, строгий validator, held-out gap 0.72 п.п. |
| D1b — атомарное сжатое хранилище | ✅ выполнено | `037f3ea` | lease/lifecycle, fail-closed, device-protected storage |
| D1c — сенсорная Canvas-полоса | ✅ выполнено | `f47000a` | 40dp, insets/touchable-region, a11y virtual nodes |
| D1d — mmap prefix engine | ✅ выполнено | `80f332c` | latest-only/coalescing, immutable prefix, без гонок |
| D1e — интеграция, opt-in, privacy, a11y | ✅ выполнено | `7c7777c`, `a277283` + hardening | runtime interleavings, warm-engine re-lookup и preparing/unavailable state устранены; пять обязательных и пять дополнительных regression tests зелёные; локальный re-audit одобрен, D1f/device-UAT/native proofread/publish открыты |
| D1f — общие gates + device-UAT | ⏳ открыто | — | JVM и `lintVitalRelease` hardening-checks зелёные; финальная clean artifact matrix и device-UAT ещё обязательны |

Текущий release-hardening прогон на Mac, Gradle 9.6: весь JVM-набор — **140 tests,
0 failures/errors** (`SuggestionsControllerTest` — 33), `lintVitalRelease --rerun-tasks` —
**BUILD SUCCESSFUL**. Исторический D1e artifact gate также проходил, но был выполнен до
bump до 1.2.0/vc4 и не заменяет финальную clean build/signing/no-INTERNET/version/size
matrix. Это не device evidence. Подробности — `docs/DICTIONARY-D1E.md`.

Версия 1.2.0/vc4 и changelog подготовлены. Перед фактическим выпуском остаются clean
release-gates, versioned signed artifact, device-UAT, отдельно подтверждённая вычитка
новых татарских строк, push/merge/tag и публикация.

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

- Boundary-анализ выполняется на NFC-копии text snapshot, но отдельно сохраняет точный
  span исходного текста для безопасного удаления, включая канонически разложенный ввод.
- Текущий префикс — максимальная непрерывная последовательность букв татарского
  кириллического алфавита непосредственно перед collapsed cursor.
- Пробел, цифра, пунктуация, дефис, апостроф и любой символ вне алфавита завершают слово.
- При selection, пустом префиксе или татарской букве сразу после cursor результаты
  очищаются: ввод в середине слова и замена выделения не поддерживаются.
- Для lookup префикс приводится к NFC и lowercase. Словарь хранит NFC lowercase.
- Все lowercase-буквы дают lowercase формы. Одна заглавная буква либо первая заглавная
  и остальные lowercase дают Initial Caps. Две и более заглавные без lowercase дают
  ALL CAPS. Любой другой смешанный регистр даёт 0 результатов.
- Отображаемая и вставляемая форма имеют одинаковый регистр.
- Форма, в точности равная уже набранному нормализованному слову, исключается; более
  длинные формы с тем же префиксом остаются кандидатами.
- Кандидаты ранжируются по frequency descending, затем по Unicode code-point lexical
  ascending. Регистр отображения применяется только после ранжирования.
- Перед тапом повторно проверяются editor session, collapsed selection, subtype, generation
  и точный префикс. Удаление выполняется по code points; несовпадение отменяет действие.

Эти правила покрываются тестами для татарских букв, NFC/NFD, начала предложения,
lowercase/Initial Caps/ALL CAPS, exact-word exclusion, frequency/lexical ties, punctuation,
selection, cursor внутри слова и emoji.

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

**Gate:** D1f разблокирована: оба D1e runtime-блокера и warm-engine gap устранены, пять
обязательных и пять дополнительных детерминированных regression tests зелёные. Текущие
JVM и `lintVitalRelease` hardening-checks не означают завершение D1f: всё ещё обязательны
полная clean build/no-INTERNET/signing/version/size matrix и device-UAT; накопленные
отдельные gates их не заменяют.

- Build/lint/no-INTERNET; signed APK, PSS, cold start, compute и end-to-end latency.
- Allocation/frame tests полосы; race/lifecycle/failure/editor regression suite.
- Samsung UAT: 0–3 results, все касания, TalkBack, moreKeys, rotation, subtype, selection,
  быстрый ввод/backspace/cursor, password и first-run/update/corruption/direct boot.

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
