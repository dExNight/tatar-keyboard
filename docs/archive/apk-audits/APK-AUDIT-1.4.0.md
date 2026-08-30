# Artifact audit v1.4.0 — сборка 2026-08-20

Прогон шагов 1 (частично), 2 («Автоматические локальные gates») и 4 («Финальный
distributable») `docs/PUBLISH-CHECKLIST.md` на кандидате **v1.4.0 / versionCode 6**.

Причина отдельного прогона: после аудита v1.3.0 (`docs/APK-AUDIT-1.3.0.md`) в дерево легли
три клавиатурные правки — переделка панели эмодзи (`7a381e3`,
`docs/EMOJI-PANEL-REDESIGN.md`), устойчивость клавиши `?123` при нажатии у края (`a588f2f`,
`docs/SYMBOL-KEY-EDGE-FIX.md`) и эргономика нижнего ряда (`84dba7c`,
`docs/LAYOUT-ERGONOMICS.md`). Проаудированный артефакт v1.3.0 перестал соответствовать
дереву, поэтому решением оператора от 2026-08-20 правки выходят отдельной версией
**1.4.0 / versionCode 6**.

**Итог: артефакт собран, подписан и проаудирован полностью, шаги 2 и 4 пройдены на
v1.4.0/versionCode 6.** Все числа получены сегодня на свежей сборке из замороженного
коммита; ни одно не перенесено из прошлых прогонов — включая те, что совпали
(сертификат, permission dump).

**Оговорка, унаследованная целиком и без изменений:** подписано релизным ключом от
2026-08-18, а не историческим. Keystore не пересоздавался — тот же файл, тот же сертификат
`98ca6feb…`. Upgrade path с v1.1.0 по-прежнему порван; см. «Подписная идентичность» ниже.

## Среда прогона

| Параметр | Значение |
|---|---|
| Дата | 2026-08-20 |
| Машина | Linux 6.18.44-1-lts, x86-64 |
| JDK | OpenJDK 17.0.20.1 (build 17.0.20.1+1) |
| Gradle | 9.6.0 (wrapper) |
| Android Gradle Plugin | 9.2.1 |
| Android SDK | `/home/tarchok/Android/Sdk`, build-tools 37.0.0, platform android-37.0 |
| Ветка | `codex/version-1.4.0` (отведена от `codex/emoji-panel-redesign`) |
| Frozen commit SHA | `660c34f386ed6b00e0327a60434bdd00083d9467` |
| Состояние дерева | `git diff --check` чист; из незакоммиченного на момент сборки — только правка в `docs/TATAR-REVIEW-QUEUE.tsv` (документация, на APK не влияет) и неотслеживаемый служебный `.smgr/` |

Оговорка про frozen commit та же, что и в прошлых отчётах: release commit не создан,
поэтому `660c34f` — это HEAD ветки на момент сборки, а не тег релиза. Коммит `660c34f`
содержит ровно бамп версии и changelog (`app/build.gradle`, `CHANGELOG.md`,
`metadata/en-US/changelogs/6.txt`); кода в нём нет. Код трёх правок — в коммитах `7a381e3`,
`a588f2f` и `84dba7c` под ним.

### Как три правки попали в одну ветку

Панель эмодзи (`7a381e3`) и край `?123` (`a588f2f`) были закоммичены своими миссиями.
Эргономика нижнего ряда оставалась незакоммиченной в рабочем дереве: правка и её отчёт были
готовы (`docs/LAYOUT-ERGONOMICS.md` заканчивается строкой `STATUS: done`), но коммит не
создан. Этот прогон закоммитил её как `84dba7c` **без единого изменения содержимого** — в
коммит вошли ровно те файлы, которые перечисляет раздел 6 её отчёта, плюс сам отчёт и
скриншоты. Ни один файл трёх правок этим прогоном не редактировался.

`docs/TATAR-REVIEW-QUEUE.tsv` в коммит **не втянут** — см. последний раздел.

## Шаг 1 — freeze версии (частично)

Закрыто этим прогоном:

- `app/build.gradle` подтверждает `versionCode 6` и `versionName "1.4.0"` — прочитано в
  дереве `660c34f`, строки 15–16, и подтверждено на собранном артефакте.
- `CHANGELOG.md` содержит раздел `[1.4.0] — 2026-08-20`, описывающий три правки: нижний ряд
  (пробел 111 → 150dp, эмодзи на долгом нажатии запятой), панель эмодзи (фон, нижняя
  полоса, обрезанный последний ряд, пустая строка подсказок) и край клавиши `?123`.
  Записи `[1.3.0]` и старше не переписывались ни в одном символе.
- `metadata/en-US/changelogs/6.txt` создан и сверен с `[1.4.0]` построчно: шесть строк — по
  строке на смысловой пункт записи (длинный пробел, эмодзи на долгом нажатии запятой,
  переделка панели, обрезанный последний ряд, край `?123`, приватность). Размер **392
  байта** при потолке Fastlane 500 — для сравнения, `5.txt` весит 407 байт.

Порядок пунктов в `[1.4.0]` выбран не по хронологии правок: первым идёт нижний ряд, потому
что это единственная из трёх, которая меняет привычный жест — у пользователя, обновившегося
с 1.3.0, исчезает отдельная клавиша эмодзи рядом с пробелом. Об этом сказано первой строкой
и в русском changelog, и в Fastlane-файле.

Не закрыто этим прогоном (за пределами объёма миссии, состояние не изменилось): вычитка
новых татарских строк носителем — в 1.4.0 изменена строка `show_emoji_key_summary` во всех
трёх локалях, её татарский вариант вычитки не проходил; отсутствие `pending` в
`docs/TATAR-REVIEW-QUEUE.tsv`; просмотр полного diff перед release commit; актуальность
`PRIVACY.md` (новых хранилищ данных три правки не заводят).

## Команда

Выполнена дословно из раздела 2 чеклиста:

```sh
./gradlew clean test lintVitalRelease assembleDebug assembleRelease \
  --rerun-tasks --console=plain
```

`BUILD SUCCESSFUL in 9s`, `84 actionable tasks: 84 executed`. В логе присутствуют задачи
`:app:validateSigningRelease`, `:app:test`, `:app:lintVitalRelease`, `:app:packageRelease`,
`:app:assembleDebug`, `:app:assembleRelease`. Артефакты датированы 2026-08-20 02:49:20.

## Результаты

### JVM unit suite

**723 теста, 0 failures / 0 errors / 0 skipped**, 74 suite-файла
(`app/build/test-results/**/*.xml`, агрегировано по атрибутам `tests`/`failures`/`errors`/`skipped`).

Против 709 на v1.3.0 — **+14 тестов**, и все четырнадцать пришли с тремя правками:
панель эмодзи добавила два в `EmojiPanelStateTest` (709 → 711), край `?123` — восемь в
новом `SlidingModifierSlopTest` на 310 строк (711 → 719), эргономика нижнего ряда — четыре
в переписанном `SpaceKeyLayoutTest` (719 → 723). Промежуточные числа взяты из отчётов тех
миссий; 723 — измерение этого прогона.

### lintVitalRelease

`BUILD SUCCESSFUL` — задача `:app:lintVitalRelease` отработала без ошибок в общем прогоне.

### no-INTERNET gate

`bash scripts/check-no-internet.sh <apk>` — **exit 0 на обоих APK**, оба уровня проверки:

- debug (`app/build/outputs/apk/debug/app-debug.apk`):
  `Level 1 OK: no INTERNET in source manifest`,
  `Level 2 OK: no INTERNET in built APK`,
  `Level 2 OK: backup closed as a whitelist (allowBackup=false, both editions, no <include>, all domains excluded)`.
- release (`app/build/outputs/apk/release/app-release.apk`): те же три строки.

В `app/src/main/AndroidManifest.xml` подстрока `android.permission.INTERNET` встречается
**0 раз**.

Замечание к процедуре: скрипт требует `ANDROID_HOME`/`ANDROID_SDK_ROOT` в окружении —
без переменной он останавливается после уровня 1 с `ERROR: Android SDK build-tools not
found` и exit 1. Это поведение скрипта, а не дефект артефакта; оба прогона выше сделаны с
`ANDROID_HOME=/home/tarchok/Android/Sdk`.

### Permissions (полный дамп артефакта)

`aapt2 dump permissions` — вывод целиком, по две строки на APK:

```
package: org.tatarkeyboard.ime
uses-permission: name='android.permission.VIBRATE'
```

```
package: org.tatarkeyboard.ime.debug
uses-permission: name='android.permission.VIBRATE'
```

Ровно одно permission — `VIBRATE`. Других строк в дампе нет.

### Версия

`output-metadata.json` (release): `"versionCode": 6`, `"versionName": "1.4.0"`,
`"variantName": "release"`, `"applicationId": "org.tatarkeyboard.ime"`,
`"outputFile": "app-release.apk"`.

`output-metadata.json` (debug): `versionCode 6`, `versionName "1.4.0"`,
`variantName debug`, applicationId `org.tatarkeyboard.ime.debug`.

`aapt2 dump badging` (release): `versionCode='6' versionName='1.4.0'`,
`minSdkVersion:'24'`, `targetSdkVersion:'37'`, `compileSdkVersion='37'`.

### Размер

| Артефакт | Байты | MiB |
|---|---|---|
| release (signed) | **1 733 028** | 1,6528 |
| debug | 3 328 760 | 3,1745 |

Бюджет чеклиста для release — 3 145 728 Б: **pass**, запас **1 412 700 Б**. Целевой бюджет
D1 ≤ 1,7 MiB (1 782 579 Б): **pass**.

Против артефакта v1.3.0 (1 731 072 Б) прирост **+1956 Б** — это и есть цена трёх правок:
новые и переписанные строки настроек в трёх локалях, `<case>` в `key_styles_settings.xml`,
атрибут `config_sliding_modifier_slop` с сопутствующими `attrs.xml`/`themes-common.xml`, а
также код панели эмодзи и `PointerTracker`. Отчёты миссий сообщали 1 732 076 Б (панель
эмодзи), 1 732 552 Б (край `?123`) и 1 733 024 Б (эргономика); последнее число отличается от
сегодняшнего на 4 байта — те сборки шли на `versionCode 5`, и различие такого масштаба
объясняется бинарным манифестом и выравниванием, а не содержимым. Аудируемое число — только
1 733 028 Б, измеренное на этом артефакте.

### SHA-256

| Артефакт | SHA-256 |
|---|---|
| release (signed) | `fea08bdd76e74c091e009ba73f18f6a3eac09a3574c9f256786717c5c218113c` |
| debug | `9e258d0c6998f1cc25a5ebcced8dce29020fc741989dfd102a79fe3581c3df6d` |

Известное свойство, подтверждённое прошлыми прогонами: release APK побайтово
невоспроизводим из-за подписи, поэтому его SHA-256 меняется от сборки к сборке при
неизменном содержимом. Аудируемым считается артефакт из замороженного коммита — он же
скопирован в `dist/`.

### Подпись

```
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

```
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
Verified using v3.1 scheme (APK Signature Scheme v3.1): false
Verified using v3.2 scheme (APK Signature Scheme v3.2): false
Verified using v4 scheme (APK Signature Scheme v4): false
Verified for SourceStamp: false
Number of signers: 1
V2 Signer: certificate DN: CN=Tatar Keyboard
V2 Signer: certificate SHA-256 digest: 98ca6febfed6c146d81c1fdcfe52c79acf7aa926a1033d98b844a59803ec42ad
V2 Signer: certificate SHA-1 digest: dc3e4a4de7b9d6edb74eaef19f14abe37049dd4f
V2 Signer: certificate MD5 digest: 454dfe1b6feaa2e0debd888e4c306a00
V2 Signer: key algorithm: RSA
V2 Signer: key size (bits): 4096
V2 Signer: public key SHA-256 digest: 353e5d51f23bbf7c0d5587ab59320bc61ffa438b8ae92764b91914d3a3dc8466
```

exit 0. Схема — **v2 only**, один signer, RSA 4096, `DN CN=Tatar Keyboard`.

## Подписная идентичность

Keystore **не пересоздавался** — прямое требование миссии и решение оператора от
2026-08-18. Использован тот же `tatar-keyboard-release.jks` (в корне, git-ignored,
`.gitignore:51`) с тем же `keystore.properties` (git-ignored, `.gitignore:52`). Доказательство
неизменности ключа — совпадение всех трёх отпечатков с прошлыми прогонами:

| | Прогон 2026-08-18 | Прогон 2026-08-19 (v1.3.0) | Прогон 2026-08-20 (v1.4.0) | Совпадает |
|---|---|---|---|---|
| Certificate SHA-256 | `98ca6feb…42ad` | `98ca6feb…42ad` | `98ca6febfed6c146d81c1fdcfe52c79acf7aa926a1033d98b844a59803ec42ad` | да |
| Certificate SHA-1 | `dc3e4a4d…dd4f` | `dc3e4a4d…dd4f` | `dc3e4a4de7b9d6edb74eaef19f14abe37049dd4f` | да |
| Public key SHA-256 | `353e5d51…8466` | `353e5d51…8466` | `353e5d51f23bbf7c0d5587ab59320bc61ffa438b8ae92764b91914d3a3dc8466` | да |

Сверка с историческим релизным сертификатом:

| | SHA-256 |
|---|---|
| Исторический релизный (v1.1.0 и утраченный v1.2.0) | `cdd8c5350ddc86f13cd89b5bfb55ca33c13efba77beb4d4ccb75d5e6b961b09e` |
| Действующий с 2026-08-18, включая v1.4.0 | `98ca6febfed6c146d81c1fdcfe52c79acf7aa926a1033d98b844a59803ec42ad` |

**Не совпадает.** Пункт чеклиста «signer certificate SHA-256 совпадает с историческим
релизным сертификатом v1.1.0, upgrade path сохранён» на этом артефакте не выполняется и
выполнен быть не может — решение оператора принято осознанно.

Последствия, перечисленные в `docs/APK-AUDIT-2026-08-18.md` (раздел «Смена подписной
идентичности»), действуют без изменений: обновление поверх установки со старым ключом даст
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`; ключ единственный и невосстановимый, бэкап нужен до
первой публикации; вернуться к историческому ключу можно только пока ничего не опубликовано.

## Шаг 4 «Финальный distributable»

Командный блок раздела 4 выполнен целиком (`shasum -a 256` заменён на `sha256sum` —
Linux-эквивалент, других отличий нет):

```
release_tmp=$(mktemp dist/.tatar-keyboard-1.4.0.apk.tmp.XXXXXX)
cp app/build/outputs/apk/release/app-release.apk "$release_tmp"
cmp app/build/outputs/apk/release/app-release.apk "$release_tmp"      → exit 0, без различий
mv -f "$release_tmp" dist/tatar-keyboard-1.4.0.apk
cmp app/build/outputs/apk/release/app-release.apk dist/tatar-keyboard-1.4.0.apk → exit 0
sha256sum dist/tatar-keyboard-1.4.0.apk
  fea08bdd76e74c091e009ba73f18f6a3eac09a3574c9f256786717c5c218113c
apksigner verify --verbose --print-certs dist/tatar-keyboard-1.4.0.apk → Verifies
```

- Копия и build output совпадают побайтово (`cmp` exit 0), оба **1 733 028** байт, у обоих
  SHA-256 `fea08bdd…`.
- `apksigner verify` на копии даёт тот же результат, что и на build output: `Verifies`,
  v2 only, один signer, сертификат `98ca6feb…`. `aapt2 dump badging` копии —
  `versionCode='6' versionName='1.4.0'`.
- Повторной сборки после копирования не было: mtime build output — 2026-08-20 02:49:20,
  mtime копии — 02:49:53, то есть копия позже, и содержимое идентично.
- Каталог `dist/` git-ignored (`.gitignore:57`), APK в репозиторий не попадает.

**Про соседние файлы в `dist/`.** Рядом лежат `tatar-keyboard-1.2.0.apk` (2026-08-18) и
`tatar-keyboard-1.3.0.apk` (2026-08-19) — артефакты прошлых аудитов. Они не удалены
(удаление чужого аудированного артефакта — не решение этой миссии) и **кандидатами на
публикацию не являются**: кандидат v1.4.0 — это `dist/tatar-keyboard-1.4.0.apk`. Оба старых
файла весят 1 731 072 Б против 1 733 028 Б у кандидата; надёжно различать по имени и по
`aapt2 dump badging`.

Оговорка, которую делает и сам чеклист: шаги 1 и 3 к моменту копирования не закрыты
полностью, поэтому файл в `dist/` — локальный аудируемый артефакт, а не готовый к публикации
релиз.

## Ретаргет `docs/PUBLISH-CHECKLIST.md`

Чеклист перецелен с v1.3.0/5 на **v1.4.0 / versionCode 6**: заголовок и вводный абзац, новый
раздел «Кандидат — v1.4.0 / versionCode 6», раздел 1 (обе отметки по версии и changelog
перезакрыты на новых значениях), раздел 2 (все отметки и новая таблица evidence по
сегодняшнему прогону), раздел 4 (командный блок, пометка и три отметки), имена файлов и тега
в разделах 5–7.

Что при этом сохранено намеренно:

- Разделы и таблицы прогонов 2026-08-19, 2026-08-18 и 2026-07-25 оставлены на месте и явно
  помечены как история — числа прошлых артефактов не переписаны и не выданы за свидетельство
  о v1.4.0.
- Ни одна отметка разделов 3, 5, 6 и 7 не переведена в `[x]`: в разделах 5–7 изменены только
  версионные литералы (имя тега `v1.4.0`, заголовок Release, имя файла APK), потому что
  оставить там `v1.3.0` значило бы описать выпуск другого артефакта. Существо этих пунктов,
  их статус и весь текст вокруг не тронуты. Раздел 3 не редактировался вовсе, кроме
  версионного литерала в примечании о снятии device-UAT как блокера.
- Пункт про совпадение сертификата с историческим остаётся `[ ]` — как и после двух прошлых
  аудитов, чтобы факт разрыва upgrade path не потерялся.

## Что закрыто и что нет

Закрыто на сборке 2026-08-20 из коммита `660c34f` (числа выше): версия в
`app/build.gradle`; раздел `[1.4.0]` в `CHANGELOG.md` и его соответствие
`metadata/en-US/changelogs/6.txt`; JVM suite; `lintVitalRelease`; сборка debug и подписанного
release APK из одного checkout; no-INTERNET в исходнике, debug APK и release APK плюс
backup-whitelist; версия по `output-metadata.json` и по манифесту артефакта; permission dump;
размер против бюджета; схема подписи, число signer'ов, алгоритм и размер ключа, DN;
`apksigner verify` → `Verifies`; финальный SHA-256; весь шаг 4 (копия в `dist/`, `cmp`,
SHA-256 копии, verify копии, badging копии); ретаргет чеклиста на v1.4.0.

Не закрыто, осознанно и с зафиксированной причиной: совпадение signer certificate с
историческим релизным и сохранение upgrade path — отменено решением оператора о новом ключе
(2026-08-18, ключ не пересоздавался и этой миссией).

Не входило в объём миссии и не трогалось: раздел 3 (Device-UAT — снят оператором как блокер
релиза), существо разделов 5–7 (commit/push/CI, tag/Release, IzzyOnDroid), статусы вычитки в
`docs/TATAR-REVIEW-QUEUE.tsv` и `docs/DICTIONARY-E3-TYPO-REVIEW.tsv`, keystore и
`keystore.properties`, код трёх правок.

**Проверка тремя правками только пальцами остаётся за оператором.** Все три меняют то, что
видно и ощущается на экране, а измерения их миссий сделаны на эмуляторе `tatar_e5_test`
через `adb shell input` — идеальным тапом без дрожи. Отдельно непроверяемым остаётся
TalkBack: образа с ним на эмуляторе нет, поэтому доступность переехавшей на долгое нажатие
запятой кнопки эмодзи и переделанной панели скринридером **не проверена никем**. Это
единственная из трёх правок, которая может задеть слепого пользователя: жест, который
раньше был одним касанием, стал долгим нажатием.

## Дефектов в трёх правках не обнаружено

Досье требовало записать в отчёт, если сборка вскроет дефект в правках, а не чинить молча.
Сборка не вскрыла: тесты, lint и оба уровня no-INTERNET зелёные, размер в бюджете. Код правок
этим прогоном не менялся ни в одном файле.

## Отдельно к сведению оператора: `docs/TATAR-REVIEW-QUEUE.tsv`

В рабочем дереве на момент прогона по-прежнему лежит незакоммиченная правка
`docs/TATAR-REVIEW-QUEUE.tsv`, проставляющая `approved` строке E5d
`tatar_suggestions_summary` от имени `dExNight` с датой 2026-08-18 (комментарий в строке
ссылается на подтверждение оператором в чате со smgr). Миссии запрещено проставлять
`approved` в этом файле при любых обстоятельствах, поэтому правка **не коммитилась и не
изменялась** — она осталась в рабочем дереве ровно в том виде, в каком была, и в релизную
ветку не попала. Решение, коммитить её или откатить, за оператором.

Кроме того, версия 1.4.0 изменила строку `show_emoji_key_summary` в `values/`, `values-ru/` и
`values-tt/` — описание переключателя перестало быть неверным после переезда эмодзи на долгое
нажатие. Татарский вариант новой строки в очередь вычитки не внесён и носителем не
проверялся: вносить строки в этот файл миссия тоже не вправе. Это работа оператора вместе с
остальной вычиткой.

STATUS: done
