# DICTIONARY-E4 — личный словарь, привязанный к активному subtype

Фаза E4 из `PROPOSALS.md` («## E4 — личный словарь, привязанный к активному subtype»).
Подфазы: **E4a-1** (формат и чтение) → E4a-2 (запись и устойчивость) → E4b (настройка,
слияние, экран) → E4c (обучение) → E4d (забыть слово). Этот документ ведётся с E4a-1; разделы
последующих подфаз добавляются их коммитами.

Статус: выполнены **E4a-1** и **E4a-2**. E4b/E4c/E4d ещё не начаты.

---

## E4a-1. Формат `.tpers` и путь только для чтения

### Почему отдельный формат `.tpers`, а не `.tdict`

Формат замораживается здесь. `.tdict` под личный словарь непригоден по конструкции:

- `DictionaryArtifactSpec` требует знать `sha256` и `entryCount` **до** сборки файла, имя
  шьётся шаблоном `tatar_top100k-…`, retention 2/1;
- активация через lease (`PublishedDictionaryCatalog.acquireLatestForActivation`) возвращает
  `null`, пока жив чужой reader.

Всё это спроектировано под **неизменяемый asset**. Личный словарь меняется в течение сессии
(E4a-2), поэтому он получает собственный формат `.tpers` со своей схемой.

### Раскладка формата (заморожена)

Заголовок — 72 байта, little-endian; та же checksum-конвенция, что и
`TdictValidator.calculateDigests` (SHA-256 по всему файлу с занулённым полем контрольной суммы):

| смещение | размер | поле |
|---:|---:|---|
| 0  | 8  | magic `TATPERS\0` |
| 8  | 2  | schemaId u16 = 1 |
| 10 | 2  | formatVersion u16 = 1 |
| 12 | 2  | headerSize u16 = 72 |
| 14 | 2  | checksumAlgorithm u16 = 1 (SHA-256) |
| 16 | 4  | entryCount u32 |
| 20 | 4  | payloadSize u32 |
| **24** | **16** | **subtypeTag: ASCII, нулевой паддинг — ТЕГ ЯЗЫКА** |
| 40 | 32 | SHA-256 по всему файлу с занулённым этим полем |

Затем `entryCount` записей в **строго возрастающем порядке НОРМАЛИЗОВАННОЙ (NFC lowercase)
формы слова, без дубликатов по ней же**. Каждая запись:

| размер | поле |
|---:|---|
| 1 | длина слова в байтах u8 |
| 2 | счётчик использования u16 (≥ 1) |
| 4 | серийник последнего использования u32 |
| N | слово UTF-8 **в ИСХОДНОЙ форме** |

**Где тег языка:** поле `subtypeTag` по смещению 24 (16 байт). Формат несёт его с версии 1,
хотя приложение одноязычное: латиница Zamanälif позже не потребует миграции пользовательских
данных. Имя файла тоже несёт subtype и версии схемы/формата:
`personal-<subtypeTag>-s1-f1.tpers` (например `personal-tt_RU-s1-f1.tpers`).

**Исходная vs нормализованная форма.** На диск пишется ИСХОДНАЯ форма записи («Гүзәл»).
Нормализованная NFC lowercase форма («гүзәл») используется для сортировки, дедупликации,
контентных фильтров и поиска. Это реализует правку «Контракта текста» из четырёх пунктов
(поправка 2026-07-27, владелец E4a-1).

### `TpersValidator` — fail-closed, по образцу `TdictValidator`

Любое нарушение → `PersonalDictionaryValidationException` внутри пакета; `PersonalDictionaryReader`
превращает его в **пустой** личный словарь (наружу исключение не выходит). Каждая проверка явно
отнесена к сырой или к нормализованной форме:

- **по файлу целиком:** magic, schemaId, formatVersion, headerSize, checksumAlgorithm,
  совпадение `subtypeTag` с запрошенным subtype, `entryCount ≤ 2000`, `payloadSize` == факту,
  SHA-256, счётчик использования каждой записи ≥ 1;
- **по СЫРОЙ форме записи:** строгий UTF-8 (`CodingErrorAction.REPORT`) и регистр ≠ MIXED;
- **по НОРМАЛИЗОВАННОЙ форме записи:** все кодпоинты входят в алфавит subtype, длина
  3..24 кодпоинта, после NFC не осталось комбинирующих меток, строгая сортировка без
  дубликатов.

Отнесение алфавитной проверки к нормализованной форме — условие работоспособности: алфавит
`tt_RU` содержит только строчные буквы, поэтому проверка сырой формы отвергла бы «Г» в «Гүзәл»
и фича не заработала бы ни на одном имени собственном. Сортировка сравнивает нормализованные
формы (UTF-8, `compareUnsigned` = порядок кодпоинтов), а НЕ сырые байты записи.

### Путь только для чтения

`PersonalDictionaryReader.read(file, subtypeId)` → иммутабельный `PersonalDictionary`
(параллельные массивы сырых форм, нормализованных форм и счётчиков, отсортированные по
нормализованной форме) с префиксным поиском `lookupRawForms(normalizedPrefix)`. Поиск исключает
запись, чья нормализованная форма равна префиксу (правка 3), и оставляет более длинные формы.
**Ни одного байта пользовательских данных эта подфаза на диск не пишет** — атомарная запись и
LRU-вытеснение это E4a-2.

### Рефакторинг subtype — «boolean eligible → идентификатор активного subtype»

Владелец шва — E4a-1 (после отмены D2, поправка 2026-07-27). Сделано:

- Единый источник истины идентификатора активного subtype и его алфавита —
  `PersonalSubtypes` (`const val TATAR_RU = "tt_RU"`, `alphabetFor`, `isSupported`).
- Литерал `"tt_RU"` больше не дублируется: `SuggestionsController.SUBTYPE_ID` и три места в
  `LatinIME` (`isTatarSubtypeActive`, `isTatarSuggestionsEligible`, построение таблицы соседства)
  читают `PersonalSubtypes.TATAR_RU`. Значение неизменно, поведение идентично.
- Всё новое личное хранилище ключуется идентификатором subtype с первой версии: путь/имя файла,
  `subtypeTag` внутри файла, снапшот и алфавитный фильтр. Общего «дефолтного» хранилища нет; для
  subtype без объявленного алфавита фича выключена целиком.
- **Гард по несовпадению subtypeId:** валидатор отвергает файл, чей `subtypeTag` ≠ запрошенному
  subtype; reader читает пустой словарь. Перепроверяется отдельным тестом
  `PersonalSubtypeSeamTest.subtypeIdMismatchGuardRejectsAForeignFile`.

Контроллерный boolean-API жизненного цикла (`onStartInput(Boolean)` и т.д.) намеренно НЕ
менялся: приложение одноязычное, а замороженные тесты его пиннят; теперь этот boolean выводится
из того же единственного источника идентификатора subtype.

### Приватность (E4a-1)

Пакет `latin/dictionary/personal/` не содержит `android.util.Log`, `println(`, `System.out`,
`java.net.`, аналитики. Ни одно сообщение исключения не содержит пользовательского слова или
пути к файлу (сообщения константны). Типы, несущие слово (`PersonalDictionary`,
`ValidatedPersonalDictionary`, исключение), — НЕ `data class` и не переопределяют `toString`.
Пакет не создаёт `createDeviceProtectedStorageContext()`: утверждение проекта «device-protected
контекст создаётся только в `AndroidDictionaryStorageFactory` и `PreferenceManagerCompat`»
остаётся верным (проверяется существующим tree-wide тестом
`EmojiRecentAndFlingSourceContractTest.deviceProtectedStorageContextIsCreatedInExactlyTwoSeamsAndNoneInTheEmojiPackage`,
зелёный). Каталог `personal/` и его credential-protected `PersonalDirectoryProvider` — E4a-2.

### Тесты E4a-1 (все JVM, офлайн)

| Класс | Тестов | Покрывает |
|---|---:|---|
| `TpersValidatorTest` | 21 | golden с заглавной буквой, пустой словарь, и по одному тесту на КАЖДЫЙ вид нарушения (magic, schema, formatVersion, headerSize, checksumAlgorithm, subtypeTag, entryCount>предел, payloadSize, checksum, счётчик=0, невалидный UTF-8, MIXED, вне алфавита, длина <3, длина >24, комбинирующая метка, порядок, дубликат, файл короче заголовка) |
| `PersonalDictionaryReaderTest` | 6 | чтение в снапшот, отсутствие файла, неподдержанный subtype, гард subtypeTag, повреждение → пусто, префиксный поиск |
| `PersonalDictionaryTextContractTest` | 4 | по одному поимённому тесту на каждый из четырёх пунктов «Контракта текста» |
| `PersonalSubtypeSeamTest` | 3 | единый источник истины subtype, всё ключуется subtype, гард по несовпадению |
| `PersonalDictionaryReadPathPrivacyTest` | 3 | нет логов/сети/аналитики, нет write-API (read-only), сообщения исключений без слова и пути |

Набор на входе фазы — 446, 0 падений, 0 skipped. На выходе E4a-1 — **483**, 0 падений, 0
skipped (+37). Существующие тесты не ослаблены и не изменены (`git diff app/src/test/` пуст).

### APK (E4a-1)

- Плечо (release-артефакт после E3, из `PROPOSALS.md`): **1 481 503 Б**.
- Release-артефакт после E4a-1: **1 485 127 Б**, SHA-256
  `6083edaa574f6184e020ddaf268c1c02b9870e57aaf79c350f4928ae5becd050`.
- Дельта: **+3 624 Б** (бюджет фазы E4 ≤ 25 600 Б — выполнено; остаётся 21 976 Б).
- Абсолют: 1 485 127 Б ≤ hard limit 3 145 728 Б; остаток до потолка 1 660 601 Б.
- `lintVitalRelease` BUILD SUCCESSFUL. Новых permission и зависимостей нет; словарный ассет и
  пакеты `latin/emoji` не менялись.

### Отложено (не E4a-1)

Запись, атомарная замена, LRU-вытеснение, каталог `personal/`, `PersonalDirectoryProvider`,
credential-protected размещение в `noBackupFilesDir`, гейт `UserManager.isUserUnlocked()`,
pending-счётчики/соль — **E4a-2**. Тумблер настроек, слияние в `CompositePrefixComputer`, единая
редакция ранжирования на три класса, экран «Личный словарь», правки `PRIVACY.md` — **E4b**.
Обучение и его предикат из пяти сомножителей — **E4c**. Забыть слово долгим нажатием — **E4d**.

### Устройство / замеры

Пункты, требующие реального устройства (открытие экрана при 2 000 записях, PSS-дельта, ANR,
janky-кадры, no-INTERNET на собранном артефакте на устройстве), к E4a-1 не относятся —
read-only путь без UI. Их статус ведётся в подфазах, которые их вводят.

---

## E4a-2. Запись и устойчивость

Подфаза добавляет писателя `.tpers` (формат заморожен E4a-1 и НЕ меняется) и его машинерию
долговечности: атомарную запись только целиком, LRU-вытеснение, credential-protected каталог
`personal/`, гейт разблокировки. **В живом приложении запись НЕ вызывается вовсе** — она
доступна только тестам через явный API `PersonalDictionaryStore`. Тумблер, слияние, экран
управления — E4b; обучение — E4c.

Новый код: пакет `latin/dictionary/personalstore/` (`PersonalDictionaryStore`, `PersonalEntries`,
`PersonalWordFilter`, `PersonalDirectoryProvider`/`PersonalOutputOpener`,
`AndroidPersonalDictionaryStorage`). Расширены (без смены семантики D1b) `DurableFileOps`
методом `atomicReplace` и `AndroidDurableFileOps` (стал `internal`, чтобы логика
fsync/rename/replace не дублировалась).

### Последовательность записи (только целиком, заморожена контрактом)

Мутация выполняется как сериализованное событие на единственном фоновом executor; UI-поток не
делает ни I/O, ни checksum, ни чтения, ни записи файла. Шаги `PersonalDictionaryStore.writeWhole`:

1. каталог создаётся при необходимости, **temp-мусор сметается** (`cleanupTemps`);
2. новый образ сериализуется в память (`PersonalEntries.serialize`);
3. проверка места: `SpaceProbe.usableBytes ≥ размер + 64 КиБ резерва`, иначе изменение
   отбрасывается без создания temp;
4. создаётся **эксклюзивный temp в том же каталоге** (`createNewFile`, уникальное имя
   `.personal-<subtypeTag>.<ts>.<n>.tmp`);
5. запись байтов в temp;
6. `flush`;
7. **fsync файла** (`syncFile` по дескриптору temp);
8. **ПОВТОРНАЯ валидация записанного** — `TpersValidator.validate(temp)` перечитывает temp с
   диска и проверяет magic/схему/checksum/порядок/алфавит; отказ роняет мутацию;
9. **атомарная замена** `atomicReplace(temp, destination)` — POSIX `rename(2)`, заменяет на
   месте (в отличие от `atomicRename`, который намеренно бросает при существующем destination);
10. **fsync каталога** (`syncDirectory`);
11. только теперь инкремент `writeCount` и публикация нового ИММУТАБЕЛЬНОГО снапшота.

Инвариант: `writeWhole` возвращает `true` только когда прошли ВСЕ шаги. Любой пойманный сбой на
шагах 4–9 удаляет temp и возвращает `false`, оставляя прежний файл нетронутым; partial никогда не
становится основным. temp-мусор от аварийного прерывания (Error/смерть процесса) не проглатывается
и удаляется при следующем открытии каталога. Порядок шагов 4–7,9,10 закреплён тестом
`wholeFileWriteFollowsTheContractSequenceAndPreservesEarlierWords`
(события `create → write → flush → file-fsync → replace → dir-fsync`).

### Инъекция сбоя на каждом шаге — «шаг → сбой → исход → тест»

Каждый шаг записи имеет собственный тест: сбой внесён именно там, и проверено, что ПРЕДЫДУЩИЙ
файл остался целым (байт-в-байт) и читаемым, старые записи не потеряны, temp-мусор не остался и
не мешает следующему открытию. Класс `PersonalDictionaryStoreWriteTest` (JVM, офлайн).

| шаг записи | внесённый сбой | наблюдаемый исход | тест |
|---|---|---|---|
| 4. создание temp | `createNewFile` бросает `IOException` | прежний файл байт-в-байт цел, слово не в снапшоте, temp нет | `tempCreationFailureKeepsThePriorFileIntactAndLeavesNoTemp` |
| 5. запись | `write` бросает `IOException` | то же | `writeFailureKeepsThePriorFileIntactAndLeavesNoTemp` |
| 6. flush | `flush` бросает `IOException` | то же | `flushFailureKeepsThePriorFileIntactAndLeavesNoTemp` |
| 7. fsync файла | `syncFile` бросает `IOException` | то же | `fileFsyncFailureKeepsThePriorFileIntactAndLeavesNoTemp` |
| 8. повторная валидация | записанные байты повреждены до fsync → validate отвергает | то же | `revalidationFailureKeepsThePriorFileIntactAndLeavesNoTemp` |
| 9. атомарная замена | `atomicReplace` бросает `IOException` | то же | `atomicReplaceFailureKeepsThePriorFileIntactAndLeavesNoTemp` |
| 10. fsync каталога (после замены) | `syncDirectory` бросает `IOException` уже ПОСЛЕ успешной замены | destination содержит НОВЫЕ валидные данные, читаем; temp нет (поведение как post-rename fsync в D1b) | `directoryFsyncFailureAfterReplaceLeavesTheNewValidFileAndNoTemp` |
| 9. замена (первая запись, файла ещё нет) | `atomicReplace` бросает `IOException` | основного файла нет вовсе, temp нет | `firstWriteFailureLeavesNoFileAndNoTemp` |
| 9. замена (смерть процесса) | `atomicReplace` бросает `Error` (не Exception) | temp остаётся; следующий open его сметает, следующая запись успешна, слово прежнего файла цело | `crashDuringReplaceLeavesATempThatTheNextOpenDiscards` |
| 1. sweep при открытии | заранее подложен `.personal-…​.tmp` | temp сметён при открытии и не мешает следующей записи | `staleTempIsRemovedOnOpenAndNeverBlocksTheNextWrite` |
| 3. проверка места | `SpaceProbe` = 0 (ENOSPC) | изменение отброшено, прежний файл байт-в-байт цел, temp нет | `noFreeSpaceDropsTheChangeAndKeepsThePriorFileWithoutTemp` |

Сопутствующие свойства устойчивости (тот же класс):

| свойство | тест |
|---|---|
| повреждённый файл удаляется при открытии (карантина нет), обычный ввод продолжает работать, следующая запись успешна | `corruptFileIsQuarantinedOnOpenAndInputStillWorks` |
| до разблокировки (`unlockGate == false`) существующий файл байт-в-байт цел, каталог не изменён, снапшот пуст (20 добавлений + accepted + flush) | `lockedDeviceSessionNeverTouchesTheExistingFile` |
| неприемлемый ввод (`guzel@mail.ru`, `t.me/abc`, `код1234`, `iPhone`, `ГҮЗӘЛ2`, MIXED, цифры, слишком короткое) не пишется и файла не создаёт | `ineligibleInputIsNeverStoredNorDoesItCreateAFile` |
| переполнение на диске вытесняет запись с минимальным серийником, файл ≤ 128 КБ | `overflowEvictsTheOldestOnDiskToo` |
| принятая подсказка обновляет счётчик В ПАМЯТИ и НЕ переписывает файл; сброс на границе пишет ровно один раз | `acceptedSuggestionUpdatesCountersInMemoryWithoutRewritingTheFile` |
| `clearAll` опустошает память и удаляет файл, temp нет | `clearAllEmptiesMemoryAndDeletesTheFile` |

### D1b не меняется

`atomicRename` по-прежнему бросает при существующем destination (retention D1b на это опирается),
а `atomicReplace` — отдельный метод, замещающий на месте. Перепроверено
`DurableFileOpsAtomicReplaceContractTest` (`atomicReplaceReplacesAnExistingDestinationInPlace`,
`atomicReplaceAlsoWorksWhenTheDestinationIsAbsent`, `atomicRenameStillThrowsWhenTheDestinationExists`).
16 кейсов `AtomicDictionaryStoreTest` остаются зелёными без правок (`git diff app/src/test/` пуст).

### LRU-вытеснение и пределы

Чистая логика вынесена в иммутабельный `PersonalEntries` (без I/O), покрыта JVM-тестами
`PersonalEntriesTest`. Три параллельных массива (сырые формы, нормализованные формы, счётчики)
плюс параллельный массив серийников, все отсортированы по нормализованной форме (UTF-8,
`compareUnsigned`). Каждая мутация возвращает НОВЫЙ экземпляр; изменения на месте нет.

- `MAX_PERSONAL_ENTRIES = 2 000` записей на subtype (кап записей);
- `MAX_FILE_SIZE = 131 072` Б (128 КиБ, кап байтов);
- **связывает кап записей, а не кап байтов:** при предельных 24 кодпоинтах татарской кириллицы
  запись весит `1 + 2 + 4 + 48 = 55` Б, значит 2 000 записей ≈ 110 072 Б с заголовком — байтовый
  кап не срабатывает раньше (тест `theRecordCapBindsBeforeTheByteCapAtTwoThousandMaxLengthEntries`);
- **LRU по монотонному ФАЙЛОВОМУ серийнику, без системных часов:** при переполнении вытесняется
  запись с минимальным `lastUseSerial`; `upsert`/`noteUse` поднимают серийник, защищая запись от
  вытеснения (`evictsTheSmallestSerialEntryWhenOverTheCap`,
  `touchingAnEntryProtectsItFromEvictionByRaisingItsSerial`,
  `addingBeyondTwoThousandEvictsTheOldestByFileSerial`);
- счётчик использования насыщается на u16 (`usageCounterSaturatesAtU16`); `noteUse` бампит
  существующую запись и НИКОГДА не создаёт фантом (`noteUseBumpsAnExistingEntryButNeverCreatesAPhantom`);
- сериализация даёт байты, которые `TpersValidator` принимает (порядок, checksum, алфавит, длина),
  round-trip проверен (`serializedEntriesRoundTripThroughTheValidatorInNormalizedOrder`).

Пределы pending-счётчиков (`MAX_PENDING = 256`), соль и правило срока жизни — механизм E4c;
E4a-2 их не реализует.

### Путь файла и почему credential-protected

Файл: `personal-<subtypeTag>-s1-f1.tpers` (например `personal-tt_RU-s1-f1.tpers`) в каталоге
`personal/` **внутри `context.getNoBackupFilesDir()` базового (credential-protected) контекста
приложения**. Собственный `PersonalDirectoryProvider`; `DeviceProtectedDirectoryProvider`
дважды НЕ переиспользуется (его имя стало бы ложью), шов «недавних» эмодзи тоже.

- Личный словарь — список того, что человек печатал, поэтому он живёт в хранилище,
  расшифровываемом только после ввода PIN/пароля, а не в device-protected, которое ОС открывает
  при загрузке. Правило для этого класса данных унаследовано от E2b-3 («недавние» эмодзи), E4 —
  второе применение.
- `AndroidPersonalDictionaryStorage` НЕ открывает device-protected контекст: базовый Context уже
  credential-protected. Утверждение проекта «device-protected контекст создаётся ровно в двух
  местах (`AndroidDictionaryStorageFactory`, `PreferenceManagerCompat`)» остаётся верным —
  перепроверено зелёным `EmojiRecentAndFlingSourceContractTest.deviceProtectedStorageContextIsCreatedInExactlyTwoSeamsAndNoneInTheEmojiPackage`.
- Гейт `UserManager.isUserUnlocked()` обязателен: до первой разблокировки путь физически
  недоступен, и фича переживает это штатным «личный словарь выключен» (пустой снапшот), а не
  исключением.
- `noBackupFilesDir` не является подкаталогом `files/` → вне доменов бэкапа по конструкции;
  отдельного правила на путь `personal/` в XML нет и быть не должно (создало бы зелёный
  source-contract тест без защиты). Белый список бэкапа E2b-3 (`allowBackup="false"`, ни одного
  разрешающего элемента) закрывает файл по умолчанию; `personal` — чувствительный маркер в
  `BackupWhitelistSourceContractTest` (зелёный, не тронут).

Source-contract подтверждения: `PersonalStoragePathSourceContractTest` (путь из
`PersonalDirectoryProvider`/`noBackupFilesDir`, не из device-protected; собственный шов),
`PersonalDictionaryNoLiveWriteSourceContractTest` (ни `LatinIME`, ни `SuggestionsController` не
ссылаются на store; все ссылки — внутри пакета).

### Приватность (E4a-2)

Пакет `latin/dictionary/personalstore/`: нет `android.util.Log`, `println(`, `System.out`,
`java.net.`, `android.permission.INTERNET`, аналитики. Ни одно сообщение исключения не содержит
пользовательского слова или пути (сообщения константны). Типы, несущие слово (`PersonalEntries`),
— НЕ `data class`. Имена методов не несут набранный текст. Всё это — зеркало
`DictionaryStoragePrivacyTest` (перевёрнут ассерт про device-protected), проверяется
`PersonalStorePrivacyTest` (5 тестов). Новых permission и зависимостей нет; манифест, словарный
ассет, пакет `latin/emoji/` и формат `.tpers` не менялись.

### Тесты E4a-2 (все JVM, офлайн)

| Класс | Тестов | Покрывает |
|---|---:|---|
| `PersonalDictionaryStoreWriteTest` | 18 | последовательность записи, инъекция сбоя на каждом шаге, no-space, переполнение, гейт, карантин, accepted-без-перезаписи, clearAll |
| `PersonalEntriesTest` | 8 | LRU по серийнику, кап записей раньше капа байтов, насыщение u16, round-trip через валидатор |
| `DurableFileOpsAtomicReplaceContractTest` | 3 | `atomicReplace` замещает; `atomicRename` по-прежнему бросает (D1b) |
| `PersonalStoragePathSourceContractTest` | 3 | путь credential-protected из собственного провайдера, гейт разблокировки |
| `PersonalStorePrivacyTest` | 5 | нет логов/сети/аналитики/device-protected; сообщения без слова/пути; не data class |
| `PersonalDictionaryNoLiveWriteSourceContractTest` | 2 | в живом IME записи нет — ссылки только внутри пакета |

Набор на входе E4a-2 — **483**, 0 падений, 0 skipped. На выходе E4a-2 — **522**, 0 падений,
0 skipped (+39). Существующие тесты не ослаблены и не изменены (`git diff app/src/test/` пуст).

**Аудит унаследованной работы (исправлено этой сессией):** предыдущий агент оставил в KDoc
`AndroidPersonalDictionaryStorage.kt` литерал `createDeviceProtectedStorageContext()` и в
`PersonalStorageSeams.kt` литерал `DeviceProtectedDirectoryProvider`. Эти литералы грепаются
source-contract/privacy-тестами и роняли 4 кейса
(`PersonalStorePrivacyTest`, `PersonalStoragePathSourceContractTest` ×2,
`EmojiRecentAndFlingSourceContractTest` — счёт швов стал 3 вместо 2). Комментарии перефразированы
без смены смысла; тесты (frozen-контракт) не трогались.

### APK (E4a-2)

- Плечо (release-артефакт после E4a-1, из раздела выше): **1 485 127 Б**.
- Release-артефакт после E4a-2: **1 485 119 Б** (`stat -f %z`). APK-уровневый SHA-256 меж сборками
  не воспроизводим (подпись и zip-таймстемпы), поэтому проверяемая метрика здесь — размер, а не
  хеш собранного APK.
- Дельта: **−8 Б** (шум упаковки). Фактически ноль: пакет `personalstore` в живой код не подключён
  (контракт «в приложении записи нет»), поэтому R8 вырезает неиспользуемые классы из release-APK
  целиком. Бюджет фазы E4 ≤ 25 600 Б — выполнено; накопленная дельта E4 (от плеча перед E4a-1
  1 481 503 Б) = **+3 616 Б**, остаётся 21 984 Б.
- Абсолют: 1 485 119 Б ≤ hard limit 3 145 728 Б; остаток до потолка 1 660 609 Б.
- `lintVitalRelease` BUILD SUCCESSFUL.

### Уровень 2 на собранном артефакте (требование контракта E4a-2)

Контракт E4a-2 в пункте о резервном копировании требует не вводить новых правил, а **заново
прогнать уровень 2 на собранном артефакте** и записать его результат сюда. Прогон выполнен на
release-APK этой подфазы (`scripts/check-no-internet.sh app/build/outputs/apk/release/app-release.apk`),
вывод дословно:

```
Level 1 OK: no INTERNET in source manifest
package: org.tatarkeyboard.ime
uses-permission: name='android.permission.VIBRATE'
Level 2 OK: no INTERNET in built APK
Backup: raw manifest line -> A: http://schemas.android.com/apk/res/android:allowBackup(0x01010280)=false
Backup: manifest references dataExtractionRules=@0x7f110002 fullBackupContent=@0x7f110001
Level 2 OK: backup closed as a whitelist (allowBackup=false, both editions, no <include>, all domains excluded) in app/build/outputs/apk/release/app-release.apk
no INTERNET permission
```

То есть на собранном артефакте: единственное запрошенное permission — `VIBRATE`, `INTERNET` нет,
`allowBackup=false`, обе редакции правил объявлены и в обеих нет ни одного разрешающего элемента,
все семь доменов исключены целиком. Отдельного правила на путь `personal/` в XML нет — контракт
его прямо запрещает, а `noBackupFilesDir` не является подкаталогом `files/` и в домены бэкапа не
попадает по конструкции. Этот прогон — не device-пункт: он выполняется на машине сборки, поэтому
в таблице ниже соответствующие строки закрыты, а не отложены.

### Устройство / замеры — статус для E4a-2

E4a-2 добавляет только офлайн-машинерию хранилища без UI и без подключения к живому вводу,
поэтому все ОСТАЛЬНЫЕ device/UAT-пункты приёмки E4 к ней **не относятся** и остаются NOT_COVERED.
Ни один из них не PASSED; цифры PSS не выдумываются.

| device-пункт приёмки | статус | причина |
|---|---|---|
| открытие экрана «Личный словарь» при 2 000 записях ≤ 500 мс, без ANR | NOT_COVERED | экрана ещё нет — E4b |
| дельта PSS от включённого словаря ≤ 0,5 МБ (и освобождение неактивного снапшота) | NOT_COVERED | фича не подключена к живому коду; замер на плечах различающихся тумблером — E4b; цифра не выдумывается |
| latency: prefix lookup p95 ≤ 5 мс, request→publish warm p95 ≤ 16 мс, stale = 0 | NOT_COVERED | слияние в `CompositePrefixComputer` не подключено — E4b |
| ноль аллокаций в hot draw/touch, janky ≤ 1% | NOT_COVERED | таймер долгого нажатия — E4d |
| direct boot на устройстве (до/после разблокировки) | NOT_COVERED | требует подключённой фичи и устройства — E4b+; на JVM гейт `isUserUnlocked` покрыт `lockedDeviceSessionNeverTouchesTheExistingFile` |
| no-INTERNET gate на собранном артефакте (aapt2) | ЗАКРЫТ | прогнан на release-APK этой подфазы, вывод выше: единственное permission — `VIBRATE` |
| правила backup, уровень 2 на собранном APK (aapt2) | ЗАКРЫТ | прогнан на release-APK этой подфазы, вывод выше: `allowBackup=false`, обе редакции без разрешающих элементов |
