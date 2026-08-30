# Artifact audit v1.3.0 — сборка 2026-08-19

Прогон шагов 1 (частично), 2 («Автоматические локальные gates») и 4 («Финальный
distributable») `docs/PUBLISH-CHECKLIST.md` на кандидате **v1.3.0 / versionCode 5**.

Причина отдельного прогона: аудит 2026-08-18 (`docs/APK-AUDIT-2026-08-18.md`) закрыл те же
шаги, но на артефакте, который объявлял `versionCode 4 / 1.2.0`, хотя фактически нёс фазы
E1–E5 и D3 поверх набора D1. Расхождение зафиксировано тем отчётом как открытый вопрос;
решением оператора от 2026-08-18 расширенный набор выходит **отдельной версией 1.3.0/5**, а
не переписанным changelog под 1.2.0/4. Этот отчёт — аудит артефакта уже под новым номером.

**Итог: артефакт собран, подписан и проаудирован полностью, шаги 2 и 4 пройдены на
v1.3.0/versionCode 5.** Все числа получены сегодня на свежей сборке из замороженного
коммита; ни одно не перенесено из прошлого прогона.

**Оговорка, унаследованная целиком и без изменений:** подписано новым релизным ключом от
2026-08-18, а не историческим. Keystore не пересоздавался — тот же файл, тот же сертификат
`98ca6feb…`. Upgrade path с v1.1.0 по-прежнему порван; см. «Подписная идентичность» ниже.

## Среда прогона

| Параметр | Значение |
|---|---|
| Дата | 2026-08-19 |
| Машина | Linux 6.18.41-1-lts, x86-64 |
| JDK | OpenJDK 17.0.20 (build 17.0.20+8) |
| Gradle | 9.6.0 (wrapper) |
| Android Gradle Plugin | 9.2.1 |
| Android SDK | `/home/tarchok/Android/Sdk`, build-tools 37.0.0, platform android-37.0 |
| Ветка | `codex/version-1.3.0` (отведена от `codex/apk-audit-2026-08-18`) |
| Frozen commit SHA | `1dc14afa5576f9781ba56f13df4e94d0c67de61c` |
| Состояние дерева | `git diff --check` чист; из незакоммиченного на момент сборки — только правка оператора в `docs/TATAR-REVIEW-QUEUE.tsv` (документация, на APK не влияет) и служебный `.smgr/` |

Оговорка про frozen commit та же, что и в прошлом отчёте: release commit не создан, поэтому
`1dc14afa` — это HEAD ветки на момент сборки, а не тег релиза. Коммит `1dc14afa` содержит
ровно бамп версии и документацию (`app/build.gradle`, `CHANGELOG.md`,
`metadata/en-US/changelogs/5.txt` и три doc-пометки о сертификате); кода в нём нет.

## Шаг 1 — freeze версии (частично)

Закрыто этим прогоном:

- `app/build.gradle` подтверждает `versionCode 5` и `versionName "1.3.0"` — прочитано в
  дереве `1dc14afa`, строки 15–16.
- `CHANGELOG.md` содержит раздел `[1.3.0] — 2026-08-19`, описывающий фазы E1–E5 и D3
  (панель эмодзи, личный словарь, устойчивость подсказок к опечаткам, предсказание
  следующего слова, автозамена с отменой, приватность, производительность, доступность).
  Историческая запись `[1.2.0]` не переписывалась ни в одном символе — она по-прежнему
  описывает только D1.
- `metadata/en-US/changelogs/5.txt` создан и сверен с `[1.3.0]` построчно: шесть строк,
  каждая соответствует своему разделу записи (эмодзи, личный словарь, опечатки,
  предсказание, автозамена, приватность). Размер **407 байт** при потолке Fastlane 500 —
  для сравнения, `4.txt` весит 494 байта. Ограничение объёма — причина, по которой 5.txt
  агрегирует раздел записи в строку, а не переносит каждый пункт CHANGELOG отдельно.

Не закрыто этим прогоном (за пределами объёма миссии, состояние не изменилось): вычитка
новых татарских строк носителем, отсутствие `pending` в `docs/TATAR-REVIEW-QUEUE.tsv`,
просмотр полного diff перед release commit, актуальность `PRIVACY.md`.

## Команда

Выполнена дословно из раздела 2 чеклиста:

```sh
./gradlew clean test lintVitalRelease assembleDebug assembleRelease \
  --rerun-tasks --console=plain
```

`BUILD SUCCESSFUL in 12s`, `84 actionable tasks: 84 executed`. В логе присутствуют задачи
`:app:validateSigningRelease`, `:app:test`, `:app:lintVitalRelease`, `:app:packageRelease`,
`:app:assembleDebug`, `:app:assembleRelease`. Артефакты датированы 2026-08-19 12:53:44
(debug) и 12:53:52 (release).

## Результаты

### JVM unit suite

**709 тестов, 0 failures / 0 errors / 0 skipped**, 73 suite-файла
(`app/build/test-results/**/*.xml`, агрегировано по атрибутам `tests`/`failures`/`errors`/`skipped`).

То же число, что и 2026-08-18: бамп версии кода не касается, ни один тест не добавлен и не
изменён.

### lintVitalRelease

`BUILD SUCCESSFUL` — задача `:app:lintVitalRelease` отработала без ошибок в общем прогоне.

### no-INTERNET gate

`bash scripts/check-no-internet.sh` — **exit 0 на обоих APK**, оба уровня проверки:

- debug (`app/build/outputs/apk/debug/app-debug.apk`):
  `Level 1 OK: no INTERNET in source manifest`,
  `Level 2 OK: no INTERNET in built APK`,
  `Level 2 OK: backup closed as a whitelist (allowBackup=false, both editions, no <include>, all domains excluded)`.
- release (`app/build/outputs/apk/release/app-release.apk`): те же три строки.

В `app/src/main/AndroidManifest.xml` подстрока `android.permission.INTERNET` встречается
**0 раз**.

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

`output-metadata.json` (release): `"versionCode": 5`, `"versionName": "1.3.0"`,
`"variantName": "release"`, `"applicationId": "org.tatarkeyboard.ime"`,
`"outputFile": "app-release.apk"`.

`output-metadata.json` (debug): `versionCode 5`, `versionName "1.3.0"`,
`variantName debug`, applicationId `org.tatarkeyboard.ime.debug`.

`aapt2 dump badging` (release): `versionCode='5' versionName='1.3.0'`,
`minSdkVersion:'24'`, `targetSdkVersion:'37'`, `compileSdkVersion='37'`.

Цель миссии на этом пункте достигнута: артефакт объявляет ту версию, которую описывает
`CHANGELOG.md`.

### Размер

| Артефакт | Байты | MiB |
|---|---|---|
| release (signed) | **1 731 072** | 1,6509 |
| debug | 3 326 176 | 3,172 |

Бюджет чеклиста для release — 3 145 728 Б: **pass**, запас **1 414 656 Б**. Целевой бюджет
D1 ≤ 1,7 MiB (1 782 579 Б): **pass**.

Размер совпал с артефактом 2026-08-18 до байта, и это ожидаемо: между сборками изменились
только `versionCode` (целое в бинарном манифесте) и `versionName` — обе строки, `1.2.0` и
`1.3.0`, длиной пять символов, поэтому длина ресурсной таблицы не поменялась.

### SHA-256

| Артефакт | SHA-256 |
|---|---|
| release (signed) | `9f366d6cddfdb172fbd254a27afd91f238e51ac94bc34e5531040521cdaa74ea` |
| debug | `45d1707386c0f1ed1fcdee317f5d25771abeb3c3046a0481be14b05cc08b03ca` |

Наблюдение прошлого отчёта — «невоспроизводимость вносится подписью, а не компиляцией» —
подтвердилось повторно и уже прямым A/B. Дерево `1dc14afa` собиралось сегодня дважды: до
коммита (из рабочего дерева с теми же изменениями) и после. Debug APK, подписанный
детерминированным debug-ключом, оба раза дал один и тот же SHA-256
`45d17073…`; release APK, подписанный релизным ключом, дал `61f70214…` и `9f366d6c…` при
одинаковом размере 1 731 072 Б. Аудируемым считается второй, из замороженного коммита, — он
же скопирован в `dist/`.

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

Keystore **не пересоздавался** — это прямое требование миссии и решение оператора от
2026-08-18. Использован тот же `tatar-keyboard-release.jks` (в корне, git-ignored,
`.gitignore:51`) с тем же `keystore.properties` (git-ignored, `.gitignore:52`, права 600).
Доказательство неизменности ключа — совпадение всех трёх отпечатков с прошлым прогоном:

| | Прогон 2026-08-18 | Прогон 2026-08-19 (v1.3.0) | Совпадает |
|---|---|---|---|
| Certificate SHA-256 | `98ca6febfed6c146d81c1fdcfe52c79acf7aa926a1033d98b844a59803ec42ad` | `98ca6febfed6c146d81c1fdcfe52c79acf7aa926a1033d98b844a59803ec42ad` | да |
| Certificate SHA-1 | `dc3e4a4de7b9d6edb74eaef19f14abe37049dd4f` | `dc3e4a4de7b9d6edb74eaef19f14abe37049dd4f` | да |
| Public key SHA-256 | `353e5d51f23bbf7c0d5587ab59320bc61ffa438b8ae92764b91914d3a3dc8466` | `353e5d51f23bbf7c0d5587ab59320bc61ffa438b8ae92764b91914d3a3dc8466` | да |

Сверка с историческим релизным сертификатом:

| | SHA-256 |
|---|---|
| Исторический релизный (v1.1.0 и утраченный v1.2.0) | `cdd8c5350ddc86f13cd89b5bfb55ca33c13efba77beb4d4ccb75d5e6b961b09e` |
| Действующий с 2026-08-18, включая v1.3.0 | `98ca6febfed6c146d81c1fdcfe52c79acf7aa926a1033d98b844a59803ec42ad` |

**Не совпадает.** Пункт чеклиста «signer certificate SHA-256 совпадает с историческим
релизным сертификатом v1.1.0, upgrade path сохранён» на этом артефакте не выполняется и
выполнен быть не может — решение оператора принято осознанно.

Упоминания `cdd8c535…` как действующего релизного сертификата помечены устаревшими в трёх
местах (коммит `1dc14afa`, история не переписывалась — добавлены пометки):

- `PROPOSALS.md` — после абзаца «Финальный artifact gate v1.2.0»;
- `docs/MILESTONE-v2.md` — в блоке D1 artifact gate;
- `docs/DICTIONARY-D1E.md` — после таблицы D1f artifact gate (пометка на английском, по
  языку документа).

В `docs/PUBLISH-CHECKLIST.md` раздел «Историческая справка v1.1.0» получил ту же пометку
отдельным коммитом ретаргета. Формулировки прошлых отчётов не правились: они верны для
своих артефактов и своих дат, добавлено только предупреждение не сверяться со старым
отпечатком при выпуске.

Последствия, перечисленные в `docs/APK-AUDIT-2026-08-18.md` (раздел «Смена подписной
идентичности»), действуют без изменений: обновление поверх установки со старым ключом даст
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`; ключ единственный и невосстановимый, бэкап нужен до
первой публикации; вернуться к историческому ключу можно только пока ничего не опубликовано.

## Шаг 4 «Финальный distributable»

Командный блок раздела 4 выполнен целиком (`shasum -a 256` заменён на `sha256sum` —
Linux-эквивалент, других отличий нет):

```
release_tmp=$(mktemp dist/.tatar-keyboard-1.3.0.apk.tmp.XXXXXX)
cp app/build/outputs/apk/release/app-release.apk "$release_tmp"
cmp app/build/outputs/apk/release/app-release.apk "$release_tmp"      → exit 0, без различий
mv -f "$release_tmp" dist/tatar-keyboard-1.3.0.apk
cmp app/build/outputs/apk/release/app-release.apk dist/tatar-keyboard-1.3.0.apk → exit 0
sha256sum dist/tatar-keyboard-1.3.0.apk
  9f366d6cddfdb172fbd254a27afd91f238e51ac94bc34e5531040521cdaa74ea
apksigner verify --verbose --print-certs dist/tatar-keyboard-1.3.0.apk → Verifies
```

- Копия и build output совпадают побайтово (`cmp` exit 0), оба **1 731 072** байта, у обоих
  SHA-256 `9f366d6c…`.
- `apksigner verify` на копии даёт тот же результат, что и на build output: `Verifies`,
  v2 only, один signer, сертификат `98ca6feb…`.
- Повторной сборки после копирования не было: mtime build output — 2026-08-19 12:53:52,
  mtime копии — 12:54:12, то есть копия позже, и содержимое идентично.
- Каталог `dist/` git-ignored (`.gitignore:57`), APK в репозиторий не попадает.

**Про соседний файл в `dist/`.** Рядом лежит `dist/tatar-keyboard-1.2.0.apk` от 2026-08-18 —
артефакт прошлого аудита, тот же код, но объявляющий `versionCode 4`. Он не удалён (удаление
чужого аудированного артефакта — не решение этой миссии) и **не является кандидатом на
публикацию**: кандидат v1.3.0 — это `dist/tatar-keyboard-1.3.0.apk`. Оба файла имеют один
размер и разные SHA-256; различать их следует по имени и по `aapt2 dump badging`.

Оговорка, которую делает и сам чеклист: шаги 1 и 3 к моменту копирования не закрыты
полностью, поэтому файл в `dist/` — локальный аудируемый артефакт, а не готовый к публикации
релиз.

## Ретаргет `docs/PUBLISH-CHECKLIST.md`

Чеклист перецелен с v1.2.0/4 на **v1.3.0/versionCode 5**: заголовок и вводный абзац, раздел
1 (обе отметки по версии и changelog перезакрыты на новых значениях), раздел 2 (все отметки
и новая таблица evidence по сегодняшнему прогону), раздел 4 (командный блок и три отметки),
имена файлов и тега в разделах 5–7.

Что при этом сохранено намеренно:

- Историческая таблица evidence прогона 2026-08-18 и таблица утраченного артефакта
  2026-07-25 оставлены на месте и явно помечены как история — числа прошлых артефактов не
  переписаны и не выданы за свидетельство о v1.3.0.
- Ни одна отметка разделов 3, 5, 6 и 7 не переведена в `[x]`: в разделах 5–7 изменены только
  версионные литералы (имя тега `v1.3.0`, заголовок Release, имя файла APK), потому что
  оставить там `v1.2.0` значило бы описать выпуск другого артефакта. Существо этих пунктов,
  их статус и весь текст вокруг не тронуты. Раздел 3 не редактировался вовсе, кроме
  версионного литерала в примечании о снятии device-UAT как блокера.
- Пункт про совпадение сертификата с историческим остаётся `[ ]` — так же, как после
  прошлого аудита, чтобы факт разрыва upgrade path не потерялся.

## Что закрыто и что нет

Закрыто на сборке 2026-08-19 из коммита `1dc14afa` (числа выше): версия в
`app/build.gradle`; раздел `[1.3.0]` в `CHANGELOG.md` и его соответствие
`metadata/en-US/changelogs/5.txt`; JVM suite; `lintVitalRelease`; сборка debug и подписанного
release APK из одного checkout; no-INTERNET в исходнике, debug APK и release APK плюс
backup-whitelist; версия по `output-metadata.json` и по манифесту артефакта; permission dump;
размер против бюджета; схема подписи, число signer'ов, алгоритм и размер ключа, DN;
`apksigner verify` → `Verifies`; финальный SHA-256; весь шаг 4 (копия в `dist/`, `cmp`,
SHA-256 копии, verify копии); ретаргет чеклиста на v1.3.0 и пометки об устаревшем
сертификате.

Не закрыто, осознанно и с зафиксированной причиной: совпадение signer certificate с
историческим релизным и сохранение upgrade path — отменено решением оператора о новом ключе
(2026-08-18, ключ не пересоздавался этой миссией).

Не входило в объём миссии и не трогалось: раздел 3 (Device-UAT — снят оператором как блокер
релиза), существо разделов 5–7 (commit/push/CI, tag/Release, IzzyOnDroid), статусы вычитки в
`docs/TATAR-REVIEW-QUEUE.tsv` и `docs/DICTIONARY-E3-TYPO-REVIEW.tsv`, keystore и
`keystore.properties`.

Отдельно к сведению оператора: в рабочем дереве на момент прогона лежала незакоммиченная
правка `docs/TATAR-REVIEW-QUEUE.tsv`, проставляющая `approved` строке E5d
`tatar_suggestions_summary` от имени `dExNight` с датой 2026-08-18. Миссии запрещено
проставлять `approved` в этом файле при любых обстоятельствах, поэтому правка **не
коммитилась и не изменялась** — она осталась в рабочем дереве ровно в том виде, в каком была.
Решение, коммитить её или откатить, за оператором.

STATUS: done
