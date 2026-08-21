# HANDOFF — миссия tt-ru-bigrams

**Статус: в работе.** Цель — предсказание следующего слова на русской раскладке
(досье `~/.supermanager/missions/tt-ru-bigrams/dossier.md`). Отчёт пишется в
`docs/RUSSIAN-BIGRAMS.md`; маркер завершения ставится последней строкой и только
по-настоящему.

Ветка `codex/version-1.6.0`, база — `011bc11`. Версия не поднималась, `CHANGELOG.md`
не тронут, наружу ничего не отправлялось, keystore не трогался.

## Сделано

**Коммит `62c76c5` — один резолвер языка (главное архитектурное требование досье).**
Выбор таблицы биграмм больше не отдельное правило: `BigramArtifactSpec` стал полем
`bigrams` у `DictionaryArtifactSpec`, и `forSubtype`/`bigramsForSubtype` отвечают на
вопрос «какой сейчас язык» один раз для обоих артефактов.

* `BigramArtifactSpec` получил `family` + `storageDirectoryName` (как у словаря) и
  разделил **`fileLanguageTag`** («tt», заморожен — под этим именем 1.6.0 распаковала
  файл на устройствах, и `finalFilePattern` принимает только `[a-z]{2,3}`) и
  **`subtypeId`** («tt_RU»), который и участвует в выборе. `DictionaryArtifactSpec.init`
  требует `bigrams.subtypeId == languageTag`.
* `AtomicBigramStore` берёт `temporaryFilePrefix`/`finalFilePattern` из спеки и требует
  одного семейства и одного каталога на стор (иначе два языка делили бы счётчик аренд).
* `AndroidBigramStorageFactory.create(context, executor, artifact)`.
* `scripts/bigram_pack.py` и `scripts/bigram_asset_pack.py` получили `--language`
  (по умолчанию татарский → татарский ассет пересобирается байт в байт).
* Тест `test_no_licensed_sources_are_tracked` расширен с `-words.txt` на `-sentences.txt`.

Проверено: JVM **798 тестов, 0 падений** (столько же, сколько на 1.7.0); все Python-наборы
зелёные.

## Что осталось

1. **Собрать русскую таблицу.** Корпуса скачаны в `~/corpora-leipzig` (вне репозитория):
   `rus_news_2022_1M`, `rus_news_2019_1M`, `rus_wikipedia_2021_1M` — обучение,
   `rus_news_2024_1M` — held-out. Это те же наборы, что у словаря; SHA-256 архивов
   первых трёх сверены с `docs/RUSSIAN-DICTIONARY.md` и совпали.
   `*-sentences.txt` для трёх новостных уже распакованы; для wikipedia — нет.
   Дальше: `python3 scripts/bigram_pack.py matrix --language rus --train … --holdout … --asset
   app/src/main/assets/dictionaries/russian_top100k_v1.tdict.zlib`, выбрать H/K по правилу
   E5a (наибольший безусловный top-3 hit-rate среди проходящих оба потолка), затем
   `bigram_asset_pack.py pack --language rus`.
2. **Дописать `BigramArtifactSpec.RUSSIAN_BIGRAMS_V1`** (family `russian_bigrams`,
   каталог `bigrams-ru`, `fileLanguageTag = "ru"`, `subtypeId = PersonalSubtypes.RUSSIAN`)
   и привязать её к `RUSSIAN_TOP100K_V1.bigrams`.
3. **Вернуть тест** `SuggestionsControllerBigramLanguageSwitchTest.kt` — он написан и лежит
   в скретчпаде сессии
   (`/tmp/claude-1000/-home-tarchok-Projects-tatar-keyboard/b06f93d0-.../scratchpad/`),
   вынут только потому, что ссылается на ещё не существующую `RUSSIAN_BIGRAMS_V1`.
   Восемь тестов: реестр, инвариант «таблица не может принадлежать чужому языку»,
   раздельные семейства/каталоги, переключение раскладки, «тот же subtype, что у словаря»,
   тёплое переключение обратно, язык без таблицы молчит, поздняя публикация.
4. **Обновить `app/src/main/assets/bigrams/NOTICE.txt`** по образцу
   `app/src/main/assets/dictionaries/NOTICE.txt` (два артефакта, оба набора корпусов).
5. **Замеры:** холодный старт (инвариант < 400 мс, на 1.7.0 медиана 299 мс — запас 83 мс,
   нарушение = блокер), PSS с двумя таблицами, размер APK (сейчас 2 386 361 Б из 3 145 728).
6. **Глазами на эмуляторе** AVD `tatar_e5_test`, скриншоты в `docs/` — тёмная и светлая
   темы, оба языка.
7. **Честно замерить новостной перекос**: бытовые фразы («привет, как дела») предскажутся
   хуже новостных («в связи с»). Это результат, а не неудача — записать числами.
8. **Строка настройки** `tatar_suggestions_summary` обещает «suggested and predicted …
   in Tatar and in Russian». Если предсказание для русского выйдет — строка становится
   правдой и менять её не нужно; если нет — правится строка, и это отдельно записывается
   в отчёт.

## Границы (из досье)

Версию не поднимать, `CHANGELOG.md` не трогать, в `dist/` ничего не класть, наружу не
публиковать (ни push, ни tag), keystore не трогать, разговорный корпус не подмешивать,
отсечку словаря не менять, «оба словаря сразу» не делать, закрытые темы не переделывать.
