# Artifact audit v1.9.0 / versionCode 16

Миссия `tt-dict-accept`. Первая версия, в которой поставляемые словари собраны не только из
письменных корпусов: 27 134 русские и 226 татарских разговорных форм приняты машинным
правилом и упакованы. Отчёт о самом отборе — `docs/DICT-ACCEPT.md`. Здесь только артефакт:
что в нём, чем он проверен и какими числами.

Каждое число ниже снято командой, запущенной в этой миссии на этом артефакте. Совпадающие с
1.8.4 значения (сертификат, permission dump) сняты заново, а не переписаны из прошлого отчёта.

Наружу не уходило ничего: ни `push`, ни тега, ни релиза, ни merge. Keystore
`tatar-keyboard-release.jks` не трогался и не пересоздавался.

## Главное первой строкой

| | |
|---|---|
| Артефакт | `dist/tatar-keyboard-1.9.0.apk` |
| SHA-256 | `42868ef3215ffb9e98639a968ba550f8b6153fde9ebc8b00a1ac57f678722313` |
| Размер | **2 536 993 Б** (1.8.4 — 2 496 781, **+40 212**) |
| Запас до 3 МиБ | 608 735 Б (19,35 %) |
| Frozen commit | `01f85d2466fee9918ba729bb47f5abdde1334e3a` |
| Сертификат | `98ca6feb…42ad` — **совпал** с требуемым |
| Разрешения | ровно `VIBRATE` |
| JVM-тесты | **957**, 0 падений, 0 ошибок, **0 skip** |
| Python-наборы | **174** в семи файлах, 0 падений, 1 skip |
| `lintVitalRelease` | return-value **0**, отчёта об ошибках не создано |
| no-INTERNET gate | exit 0 на исходнике и обоих APK |
| Холодный старт | медиана **284,9 мс**, худший 306,6 при инварианте 400 (эмулятор) |
| Обновление поверх 1.8.4 | прошло, `firstInstallTime` не изменился (эмулятор) |

**Что в этой версии стоит проверять внимательнее всего — это данные, а не код.** Оба
`.tdict.zlib` пересобраны, их SHA-256 и байты изменились, и вместе с ними пришлось
пересчитать шесть мест, где эти числа продублированы. Все шесть перечислены ниже поимённо.

## Что вошло в 1.9.0 поверх 1.8.4

Артефакт 1.8.4 собран из `2f9e5715`. Между ним и `01f85d24` — девять коммитов двух с
половиной миссий, и в APK попадает не всё: четыре из них трогают только `docs/` и `research/`.

| Хеш | Что | Попадает в APK |
|---|---|---|
| `9529b3ab` | аудит 1.8.4, отчёт финальной доработки | нет |
| `adcc4ef8` | HANDOFF миссии `tt-final` | нет |
| `49dc3a22` | `tt-corpus-os`: экран «Источники данных», оба `NOTICE.txt`, очереди приёмки | **да** |
| `a0ec6a75`, `4ca191a7` | `tt-review-batches`: порции вычитки, отчёт | нет |
| `c1e6516b` | правило отбора, принятое/отклонённое, образцы | нет |
| `bfb78e93` | частоты из всего корпуса, таблица `conv-freq-*.tsv` | нет |
| `dcd14b6f` | чеклист публикации | нет |
| `ab692ad4` | черновик отчёта, проверочный прогон | нет |
| `01f85d24` | **frozen commit**: оба словаря пересобраны, шесть пинов, версия 1.9.0 | **да** |

Версия, `changelogs/16.txt` и раздел `[1.9.0]` подняты в `ab692ad4` и `c1e6516b`
соответственно и вошли в frozen commit. Дерево на момент сборки было чистым; `git diff`
между `01f85d24` и деревом по `app/`, `build.gradle`, `settings.gradle` и
`gradle.properties` — пусто. Этот отчёт и заполненный `docs/DICT-ACCEPT.md` закоммичены
**после** заморозки; ни один из них в APK не попадает, поэтому артефакт остаётся сборкой
ровно из `01f85d24`. Так же поступали миссии 1.8.2 … 1.8.4.

### Из чего сложились +40 212 Б

Разбор по частям архива, обе версии сравнены поэлементно:

| часть | 1.9.0 | 1.8.4 | разница |
|---|---:|---:|---:|
| `assets/` | 1 562 828 | 1 529 708 | **+33 120** |
| `resources.arsc` | 309 272 | 302 572 | +6 700 |
| `classes*.dex` | 201 242 | 200 855 | +387 |
| `res/` | 391 607 | 391 605 | +2 |
| `META-INF/` | 201 | 199 | +2 |
| прочее | 13 483 | 13 483 | ±0 |

Внутри `assets/` изменились ровно четыре файла:

| файл | 1.8.4 | 1.9.0 | разница |
|---|---:|---:|---:|
| `dictionaries/russian_top100k_v1.tdict.zlib` | 582 311 | 613 489 | **+31 178** |
| `dictionaries/tatar_top100k_v1.tdict.zlib` | 581 236 | 581 782 | **+546** |
| `dictionaries/NOTICE.txt` | 813 | 2 046 | +1 233 |
| `bigrams/NOTICE.txt` | 1 157 | 1 320 | +163 |

(Числа — сжатый размер внутри APK; он отличается от размера файла на диске, потому что
`.zlib` пакуется в архив ещё раз.)

**Обе таблицы предсказания — `bigrams/*.tatbigr.zlib` — не изменились ни на байт.**
Изменился только `NOTICE.txt` рядом с ними, и это правка `tt-corpus-os`, а не этой миссии.
`resources.arsc` и `dex` выросли из-за экрана «Источники данных», тоже от `tt-corpus-os`:
1.8.4 собрана до него. Словари этой миссии стоят **31 724 Б** из 40 212.

## Шесть мест, где числа ассетов продублированы, и все шесть пересчитаны

Пересборка словаря — самая рассыпчатая правка в этом проекте: одно и то же число живёт в
шести файлах, и любое забытое роняет сборку не там, где ошиблись. Все шесть перечислены,
чтобы следующая пересборка не искала их заново.

| Где | Что стоит |
|---|---|
| `app/.../storage/DictionaryStorageContracts.kt` | сжатый и распакованный размер и оба SHA-256 обоих словарей |
| `app/.../storage/TdictValidatorTest.kt` | распакованный размер татарского |
| `scripts/typo_pack.py` | `EXPECTED_ASSET_SHA256`, `EXPECTED_RAW_SHA256` (татарский) |
| `tests/typo_pack/test_typo_pack.py` | те же два плюс размеры и SHA-256 трёх наборов опечаток |
| `app/.../engine/E3aRecoveryCalibrationTest.kt` | размер и SHA-256 набора опечаток класса 1 |
| `app/.../engine/E3bRecoveryCalibrationTest.kt` | размеры и SHA-256 наборов классов 1, 2, 3 |

Плюс два документа происхождения, которые проверяет `tests/dictionary_pack/`:
`docs/DICTIONARY-D1A.md` и `docs/RUSSIAN-DICTIONARY.md` обязаны содержать SHA-256 и байты
**коммитнутого** ассета. История в них не затёрта: числа сборок 2026-07-21 и 2026-08-21
оставлены как были, пересборка дописана отдельным разделом.

**Наборы опечаток изменились, и это не ассеты.** Они строятся генератором из словаря заново
и в APK не попадают. Класс 1: 87 375 → **87 350** строк, класс 2: 99 659 → **99 658**,
класс 3: 99 647 → **99 646**. Восстановление `recovery@3` осталось в контракте: тесты E3a и
E3b зелёные без правки порогов — трогали только пины множеств.

## Что изменилось в данных

| | 1.8.4 | 1.9.0 |
|---|---|---|
| `russian_top100k_v1.tdict.zlib` | 606 315 Б, `f4b91cef…c48f` | **639 584 Б**, `91a9f7fe…4780` |
| — распакованный | 2 540 622 Б, `875bc667…86b6` | **2 483 696 Б**, `f3d09f17…a f07` |
| `tatar_top100k_v1.tdict.zlib` | 600 606 Б, `2d98ed35…5cae` | **601 143 Б**, `f44fc5bf…9267` |
| — распакованный | 2 542 036 Б, `798d3257…f558` | **2 541 374 Б**, `1670e8d8…9df3` |
| Записей в каждом | 100 000 | 100 000 |

Записей ровно столько же: разговорные слова не добавляются, а вытесняют самые редкие
письменные. Распакованный размер **упал** у обоих, сжатый вырос у русского — вошедшие слова
короче вытесненных, но разнообразнее и хуже сжимаются. То же направление, что намерила
`docs/CORPUS-OS.md` раздел 8.

## Среда прогона

| | |
|---|---|
| ОС | Linux 6.18.44-1-lts x86-64 |
| JDK | OpenJDK 17.0.20.1 |
| Gradle | 9.6.0 |
| build-tools | 37.0.0 (`aapt2`, `apksigner`) |
| Ветка | `codex/version-1.6.0`, новая не отводилась |
| Frozen commit | `01f85d2466fee9918ba729bb47f5abdde1334e3a` |
| Эмулятор | AVD `tatar_e5_test`, Android 11 / API 30, x86-64, headless, `-gpu swiftshader_indirect`, 1080×2280 |
| Python | 3.14.7 |

Служебный каталог `.smgr/` не отслеживается; ключи и `dist/` git-ignored.

## Команда

```sh
./gradlew --offline clean test lintVitalRelease assembleDebug assembleRelease \
  --rerun-tasks --console=plain
ANDROID_HOME=$HOME/Android/Sdk bash scripts/check-no-internet.sh
ANDROID_HOME=$HOME/Android/Sdk bash scripts/check-no-internet.sh app/build/outputs/apk/debug/app-debug.apk
ANDROID_HOME=$HOME/Android/Sdk bash scripts/check-no-internet.sh app/build/outputs/apk/release/app-release.apk
aapt2 dump permissions app/build/outputs/apk/release/app-release.apk
aapt2 dump badging     app/build/outputs/apk/release/app-release.apk
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
for f in tests/*/test_*.py; do python3 "$f"; done
cp  app/build/outputs/apk/release/app-release.apk dist/tatar-keyboard-1.9.0.apk
cmp dist/tatar-keyboard-1.9.0.apk app/build/outputs/apk/release/app-release.apk
sha256sum dist/tatar-keyboard-1.9.0.apk app/build/outputs/apk/release/app-release.apk
```

---

## Результаты

### JVM unit suite

**957 тестов, 0 падений, 0 ошибок, 0 skipped**, 97 suite-файлов. На 1.8.4 было 952 в 96
файлах. Прибавка ровно одна и вся от `tt-corpus-os`: `DataSourcesScreenSourceContractTest`,
пять тестов. Эта миссия ни одного теста не добавила и ни одного не удалила — она развернула
одно утверждение внутри того же файла (`sources_not_yet_packed_are_not_shown_as_shipped` →
`every_packed_source_is_shown_as_shipped`) и пересчитала пины в четырёх других.

### Python-наборы генераторов

**174 теста, 0 падений, 1 skip** в семи файлах. На 1.8.4 было 157 в шести; седьмой файл —
`tests/review_batches/` (17 тестов) от `tt-review-batches`.

| Файл | Тестов |
|---|---:|
| `tests/bigram_asset_pack/` | 27 |
| `tests/bigram_pack/` | 18 |
| `tests/dictionary_coverage/` | 6 |
| `tests/dictionary_pack/` | 31 |
| `tests/emoji_pack/` | 35 (1 skip) |
| `tests/review_batches/` | 17 |
| `tests/typo_pack/` | 40 |

### lintVitalRelease

Return-value **0**, отчёта об ошибках не создано (`app/build/reports/lint-results*release*`
отсутствует).

### no-INTERNET gate

`scripts/check-no-internet.sh` — exit 0 на исходнике и на обоих APK:

* Level 1 OK: нет INTERNET в исходном манифесте;
* Level 2 OK: нет INTERNET в собранных debug и release;
* backup закрыт как whitelist (`allowBackup=false`, обе редакции, без `<include>`, все
  домены исключены) в обоих APK.

### Permissions

```
package: org.tatarkeyboard.ime
uses-permission: name='android.permission.VIBRATE'
```

Полный дамп артефакта — две строки, больше ничего. Разговорные данные не добавили ни одного
разрешения, и добавить не могли: они лежат файлом в `assets/`.

### Версия

`aapt2 dump badging`: `versionCode='16' versionName='1.9.0'`, `minSdkVersion:'24'`,
`targetSdkVersion:'37'`, `compileSdkVersion='37'`.

`output-metadata.json`: `"versionCode": 16`, `"versionName": "1.9.0"`,
`"variantName": "release"`, applicationId `org.tatarkeyboard.ime`.

Установленный на эмуляторе артефакт: `dumpsys package` → `versionCode=16 minSdk=24
targetSdk=37`, `versionName=1.9.0`.

### Размер

| | Байт |
|---|---:|
| release (signed) | **2 536 993** |
| debug | 4 180 134 |
| 1.8.4 release | 2 496 781 |
| разница | **+40 212** |
| бюджет 3 МиБ | 3 145 728 |
| запас | 608 735 (19,35 %) |

Запас упал с 20,63 % до 19,35 %. Досье велело резать словарь по частоте, если принятое
раздувает артефакт заметно; **резать не понадобилось**, и это сказано прямо, чтобы никто не
резал на всякий случай.

### SHA-256

```
42868ef3215ffb9e98639a968ba550f8b6153fde9ebc8b00a1ac57f678722313  dist/tatar-keyboard-1.9.0.apk
42868ef3215ffb9e98639a968ba550f8b6153fde9ebc8b00a1ac57f678722313  app/build/outputs/apk/release/app-release.apk
```

`cmp` между ними — различий нет. После копирования артефакт не пересобирался.

### Подпись

```
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
Number of signers: 1
V2 Signer: certificate DN: CN=Tatar Keyboard
V2 Signer: certificate SHA-256 digest: 98ca6febfed6c146d81c1fdcfe52c79acf7aa926a1033d98b844a59803ec42ad
V2 Signer: key algorithm: RSA
V2 Signer: key size (bits): 4096
```

Отпечаток сертификата **совпал** с требуемым досье
`98ca6febfed6c146d81c1fdcfe52c79acf7aa926a1033d98b844a59803ec42ad`. Тот же ключ, что у
1.5.0 … 1.8.4, — обновление поверх них поставится.

@@DEVICE@@

---

## Что этот аудит НЕ проверял

1. **Живого телефона нет.** Всё, что снято на устройстве, снято на эмуляторе и помечено
   эмуляторным. Досье требует именно этого.
2. **Качество принятых слов человеком не читалось.** В том и смысл миссии: планку поставила
   машина. Оператору собраны два образца по сто случайных слов на язык —
   `docs/dict-accept/sample-accepted-*.txt` и `sample-rejected-*.txt`.
3. **Риск OpenSubtitles не подтверждён.** Пункт `docs/PUBLISH-CHECKLIST.md` остаётся
   невыполненным: это первый выпуск, который везёт данные без лицензионного гранта в
   ассетах, и подтверждение ставит оператор лично.
4. **Предсказание следующего слова не мерилось**, потому что таблицы биграмм не менялись.
