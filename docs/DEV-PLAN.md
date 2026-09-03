# DEV-PLAN — план улучшений разработки (DX/tooling, без продуктовых фич)

Дата: 2026-08-31. База: main после кампании реструктуризации (1.9.5, `4e213c84`).
**Статус: все 7 пунктов выполнены 2026-08-31** (гейты после каждого: 976 JVM +
281 python + lintRelease + check-no-internet; воспроизводимость сборки сохранена).
Scope: только процесс разработки, кодогенерация, тесты, CI. Продуктовые фичи,
корпуса, замеры на железе и публикация — вне этого плана.

Каждый пункт — самостоятельная миссия в стиле проекта (отчёт `docs/archive/missions/…`,
гейты: 976 JVM + 281 python + lintRelease с baseline + check-no-internet).

## 1. Оркестратор конвейера ассетов (приоритет 1) — ✅ выполнено 2026-08-31

**Проблема.** Пересборка словаря и перепаковка таблиц биграмм — две независимые
ручные команды. Долг дважды накапливался незаметно: татарские 78 голов (закрыто
в 1.9.4), русские 4 195 голов (открыто до сих пор).

**Решение.** Один вход — `scripts/rebuild_assets.py`: пересборка обоих словарей
(`dict_accept.py pack --write` от baseline 1.8.4) → перепаковка обеих `.tatbigr`
(тат. H=10 132 K=4 + extra-heads, рус. H=10 000 K=4) → автоматический пересчёт
пинов в `DictionaryStorageContracts.kt` / `BigramStorageContracts.kt` → проверка
согласованности. Режим `--check` (без корпусов и пересборки) сверяет ассеты с
пинами и головы таблиц со словарями; известные расхождения принимаются только при
точном совпадении чисел с `scripts/known_asset_drift.json` под
`--allow-known-drift` — CI зелёный сегодня и красный на любом новом расхождении.
Тесты — `tests/rebuild_assets/` (16 шт., включая проверку живого дерева).
Параллельно починены устаревшие пути `docs/` в `dict_accept.py`,
`review_batches.py`, `run_review.sh` после реструктуризации.

## 2. Воспроизводимая сборка (приоритет 1) — ✅ выполнено 2026-08-31

**Проблема.** Две пересборки одного дерева дают разный SHA-256 (зафиксировано
в фазе 5 реструктуризации). F-Droid требует воспроизводимости — это блокер
дистрибуции, а не косметика.

**Решение.** Недетерминизм локализован инструментально (python zipfile +
разбор APK Signing Block): все 244 zip-записи (имена, порядок, CRC, размеры,
timestamps, содержимое) и сама v2-подпись были детерминированы; различался
только Dependency Info Block (id `0x504b4453`, 837 из 847 байт) — AGP
подписывает его эфемерным ключом на каждой сборке. Блок несёт метаданные
зависимостей для Play и здесь не нужен — отключён в `app/build.gradle`
(`dependenciesInfo { includeInApk = false; includeInBundle = false }`).
Доказательство: три прогона `clean assembleRelease` (включая один после
полного сноса `build/` и `.gradle/`) дали побайтно одинаковый **подписанный**
APK — SHA-256 `bf6225e5d6f50397250b79d1c2bac9cd6bd3f732982b5a8b9cc1b2aec09eae7b`,
2 111 775 Б (размер не изменился, блок поглощён padding'ом), подпись v2
верифицируется тем же сертификатом. CI-гейт: джоба `reproducible` в
`.github/workflows/ci.yml` — два чистых прогона без build-cache, сравнение
unsigned-APK побайтно (`cmp`).

## 3. Пробелы в покрытии тестами — ✅ выполнено 2026-08-31

- Python-тесты для `emoji_search_pack.py`, `emoji_skin_pack.py`, `dict_accept.py`,
  `dict_accept_check.py` — сейчас без покрытия, хотя `dict_accept` пишет в ассеты.
- Единый `scripts/emulator-smoke.sh`: поднять AVD → установить APK → сценарий
  (клавиатура, подсказки tt/ru, эмодзи, ландшафт) → свидетельства. Сейчас это
  разрозненные ручные band.py/coldstart.sh (устаревший каталог миссий, удалён 2026-09-03).

**Сделано.** Три новых набора на синтетических фикстурах + живом дереве:
`tests/emoji_search_pack/` (32: формат `seq TAB ru-имя TAB keywords`, снятие
U+FE0F при lookup, derived только заполняет пробелы, покрытие-пол 0.99,
guardrails 262 144 Б/1400 строк, fail-closed коды 2/4, детерминизм, живой
ассет против живой панели), `tests/emoji_skin_pack/` (23: формат
`seq TAB prefix TAB suffix`, тон ЗАМЕНЯЕТ U+FE0F, двухтональные записи не
предлагаются, все пять тонов обязаны быть fully-qualified, guardrails
8192 Б/400 строк + sanity-диапазон баз), `tests/dict_accept/` (29: правило
1.9.1 на синтетике — обрывки, `можна`, тат. без порога длины, ветки
two-corpora/shipped-word/shipped-paradigm, select/pack на крошечном baseline
с перепиненным SHA-256, fail-closed на неверном SHA baseline, живой прогон
`dict_accept_check` против ассетов и baseline 1.8.4). Итого python-тестов:
197 → 281.

`scripts/emulator-smoke.sh` (флаги `--avd/--apk/--no-boot/--outdir`): поднимает
AVD (-no-window, свой serial узнаёт как новый в `adb devices`), ставит APK
(пакет из aapt2 — debug несёт суффикс `.debug`), включает IME полным id, пишет
преф подсказок через run-as ДО старта процесса (force-stop → ime set после,
грабля 4б), SetupActivity, тап по try-it полю из свежего дампа, набор
«мин»/«при»/«hi» реальными тапами с проверкой текста поля, сабтипы глобусом
tt→ru→en→tt (через `pref_current_subtype`), подсказки — пиксельной дельтой
полосы (ImageMagick; окно IME uiautomator не видит), эмодзи-панель — коммитом
эмодзи в поле, crash-буфер пуст. Итог `RESULT|…`, FAIL = ненулевой выход.
Прогнан по-настоящему на tt_suggest_a14 (18 PASS, 1 SKIP — у en нет словаря)
и на tt_prefix3 с холодным поднятием AVD.

## 4. Статический анализ — довести до конца — ✅ выполнено 2026-08-31

**Lint baseline: 22 errors + 36 warnings → 0 errors + 36 warnings.** Кодом закрыты
все 22 ошибки: 18× ResourceType — `EmojiPanelView.kt`/`EmojiSearchView.kt` читали
цвета темы через ручной `intArrayOf(R.attr.*)` + `getColor(ordinal)`; заменено на
declare-styleable `EmojiPanelView`/`EmojiSearchView` в `res/values/attrs.xml`
(названы по view — иначе lint CustomViewStyleable), те же 6 атрибутов и дефолты,
поведение не изменилось (эмуляторный смоук: emoji-panel PASS, рендер сверен
скриншотом). 4× StringFormatMatches — `%s`→`%d` в `abbreviation_unit_milliseconds`
(en/ru/tt) и `abbreviation_unit_percent` (для Int вывод идентичен). Baseline
регенерирован из свежего отчёта; каждая из 36 оставшихся warning-записей
классифицирована в `app/lint.xml`: осознанный дизайн (ApplySharedPref — синхронный
commit; InlinedApi ×3; DiscouragedApi ×4 — getIdentifier часть дизайна форка;
StaticFieldLeak — синглтон на время жизни IME; InflateParams; DataExtractionRules
при `allowBackup=false`; UnusedAttribute для API 33+), косметика (RedundantLabel ×3,
Overdraw ×2, accessibility-подсказки легаси-вьюх ×7, UnusedResources ×2,
IconLauncherShape ×5 — дизайн иконки, UsableSpace, UseRequiresApi, TextFields),
пины версий (AndroidGradlePluginVersion, GradleDependency).

**error-prone подключён (build-time, ноль runtime-зависимостей).** Плагин
net.ltgt.errorprone 4.3.0 + `error_prone_core:2.42.0` — последняя версия,
запускающаяся на JDK 17 (с 2.43.0 минимум JDK 21; локально и в CI temurin 17).
Грабля: под AGP плагин инертен by design (его README) — `error_prone_core` добавлен
в `annotationProcessor`/`testAnnotationProcessor`/`androidTestAnnotationProcessor`
(прямая установка `options.annotationProcessorPath` перезаписывается AGP и даёт
`-proc:none`), включение — per-task `options.errorprone` в `app/build.gradle`.
`allErrorsAsWarnings=true`: легаси-наследие не роняет сборку. Находки: 126
предупреждений, 15 типов (63 уникальных с учётом debug+release). Починен один
подтверждённый баг: FormatString в `KeyboardId.toString()` — 15 аргументов на
14 конверсий, имя темы терялось (debug-вывод). Остальное — отчёт с оценкой:
ReferenceEquality ×13 — identity-сравнения Key/Typeface, корректно;
NarrowCalculation ×2 (`MainKeyboardView.java:641,644` — int-деление в float,
потеря ≤ 0.5 px; фикс меняет рендеринг — оставлено сознательно);
NonOverridingEquals/DuplicateBranches/EmptyCatch — осознанные; MutablePublicArray
×2 — публичное API наследия; EffectivelyPrivate ×35, MissingOverride,
MissingSummary, InvalidParam, InlineTrivialConstant, NonApiType ×2, UnusedMethod ×2,
StringSplitter ×2 (разделители `:`/`;` — regex-инертны) — стиль/косметика.
Воспроизводимость не пострадала: два clean assembleRelease побайтно одинаковы,
в APK ноль error-prone артефактов.

**detekt — вердикт: НЕ подключать.** Одноразовый CLI-прогон (1.23.8, дефолтный
конфиг) по Kotlin-слою: 462 взвешенных находки, из них 217 MagicNumber и
88 ReturnCount — шум против идиом проекта (hex-константы отрисовки, guard-clause
стиль). Осмысленные категории — SwallowedException ×19 и TooGenericExceptionCaught
×18 — это задокументированный fail-closed дизайн хранилищ (покрыт тестами).
Сигнал/шум плохой, подключение с baseline дало бы нулевой текущий выигрыш при
постоянном налоге на сборку и конфиг; CLI-прогон можно повторить вручную
(`detekt-cli-*-all.jar` с Maven Central) при желании пересмотреть.

## 5. AGENTS.md — ✅ выполнено 2026-08-31

Написан корневой `AGENTS.md`: команды сборки/тестов/гейтов, жёсткие ограничения
(ноль runtime-зависимостей, no-INTERNET, бюджеты), правила пинов ассетов,
дисциплина документов и коммитов, структура кода, AVD для эмуляторных прогонов.
В устаревший конфиг инструментов добавлена ссылка на него (содержимое не
дублируется; конфиг удалён 2026-09-03).

## 6. Релизный автомат — ✅ выполнено 2026-08-31

`scripts/release_check.sh`: гейты + размер APK против инварианта + сверка пинов
ассетов против констант + разрешения aapt2 + дельта к прошлому dist-APK.
Механическая половина PUBLISH-CHECKLIST перестаёт делаться руками.

Один вход — `bash scripts/release_check.sh [--quick|--full] [путь-к-apk]`
(по умолчанию свежий `app/build/outputs/apk/release/*.apk`; средний режим —
артефакт уже собран, гейты гоняются, сборка не пересобирается): гейты
(`./gradlew test` со счётчиком из XML-отчётов, `lintRelease`, python-наборы
`tests/`, `check-no-internet.sh` на кандидате) → размер против 3 145 728 Б
с запасом в процентах → пины ассетов, извлечённых из APK, против констант
`DictionaryStorageContracts.kt` / `BigramStorageContracts.kt` (16 значений:
сжатый и raw размер + SHA-256 по четырём файлам) → разрешения ровно [VIBRATE]
→ сертификат подписи против зашитого в шапке скрипта SHA-256 релизного ключа →
versionCode/versionName из aapt2 против `app/build.gradle` → наличие
`metadata/en-US/changelogs/<versionCode>.txt` → размерная дельта и сводка
по компонентам (assets/arsc/dex/res) к предыдущему dist-APK. Итог — машинный
блок `RESULT|статус|проверка|деталь`, любой FAIL = ненулевой выход. Проверено
на 1.9.5 (все проверки зелёные), на 1.9.4 (честный FAIL на versionCode 20 ≠ 21)
и на битом APK (FAIL на пинах и подписи).

## 7. Мелочи — ✅ выполнено 2026-08-31

- ~~Включить Gradle configuration cache~~ — включён (`org.gradle.configuration-cache=true`
  в `gradle.properties`); проверено: `test` и `clean assembleRelease` работают, запись
  кэша переиспользуется, воспроизводимость (DEV-2) побайтно сохранена (два чистых
  прогона с кэшем — одинаковый SHA-256).
- ~~`KeyboardTextsTable.java`: генератор форка потерян~~ — в шапке файла добавлено
  предупреждение: генератор утерян, с 2026-08-30 файл правится вручную; описан порядок
  добавления локали (массив TEXTS_* + карта + SubtypeLocaleUtils синхронно).

## Порядок и зависимости

Независимы друг от друга, кроме: п.1 логично делать до следующей словарной
миссии (иначе долг накопится снова); п.2 — до первой попытки F-Droid.
Рекомендуемый порядок по соотношению цена/польза: 5 → 1 → 6 → 2 → 3 → 4 → 7.
