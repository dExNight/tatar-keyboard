# PUBLISH-CHECKLIST — публикация v1.2.0

Пошаговый release gate для `versionName 1.2.0` / `versionCode 4`. Галочка означает
только проверенный результат именно кандидата v1.2.0. Исторические результаты v1.1.0
не переносятся на новый APK и явно помечены как справочные.

## Пробел в свидетельствах: аудированного артефакта больше нет (2026-07-26)

**Читать до начала выпуска.** Локальной копии `dist/tatar-keyboard-1.2.0.apk` не
существует: во время работы над фазой E1 она была перезаписана неопубликованной сборкой, а
затем удалена, чтобы неаудированный APK не ушёл в публикацию под именем релиза. Каталог
`dist/` пуст.

Все поля evidence ниже — размер **1 446 111 байт**, SHA-256
`26afd03f200f2939e5ce3b5f102bf4dcd93b5fbb8635161cd393b941cff13bcf`, дата 2026-07-25,
результат `cmp` с build output — относятся к этому артефакту, то есть к файлу, которого
локально больше нет. Проверить их прямо сейчас нельзя, и любая команда из блоков ниже,
читающая `dist/tatar-keyboard-1.2.0.apk`, не найдёт файла.

Пересборка их не восстанавливает. Установлено экспериментально: сборка из того же коммита
`4443a78` даёт ровно тот же размер 1 446 111 байт, но другой SHA-256 —
`7f7f33f90b189c2d99c8dd087668304760e92ca0f221946e663762da98273558`. Подпись APK
побайтово невоспроизводима, поэтому совпадение размера ничего не доказывает, а прежний
SHA-256 новой сборкой не получить в принципе. Сами аудированные байты сохранились только
на тестовом устройстве, где приложение установлено; устройство сейчас не подключено.

**Что из этого следует для выпуска.** Артефакт собирается заново, и artifact audit шагов 2
и 4 выполняется целиком по-новой на свежей сборке: размер, SHA-256, версия, permissions,
схемы подписи, сертификат и `cmp` с build output заполняются заново вместе с датой.
Отметки `[x]`, опирающиеся на утраченные числа, к новому артефакту не переносятся; старые
числа остаются в файле как история и не выдаются за свидетельство о новом APK.

**Что при этом НЕ произошло.** Наружу ничего не ушло: репозиторий ещё не открыт,
`versionCode 4` нигде не выложен, tag и GitHub Release не создавались, в IzzyOnDroid
заявка не подавалась. Отзывать нечего — требуется только повторный аудит перед выпуском.

## Device-UAT снят как критерий релиза (владелец, 2026-08-18)

Раздел 3 «Device-UAT и runtime budgets» ниже больше не блокирует релиз v1.2.0 — решение
владельца. Сам раздел не переписан: ни один пункт не отмечен пройденным, потому что на
реальном устройстве по-прежнему ничего не измерено. Меняется только его вес в этом
чеклисте — из обязательного гейта он становится необязательным списком для будущих
итераций.

## Повторный artifact audit выполнен, подпись — новым ключом (2026-08-18)

Шаги 2 и 4 пройдены заново и целиком на свежей сборке из коммита `9a2a3196`; полный отчёт —
`docs/APK-AUDIT-2026-08-18.md`. Заполнено новыми числами: JVM suite (709 тестов, 0/0/0),
`lintVitalRelease`, сборка debug и подписанного release APK из одного checkout, no-INTERNET
на исходнике и на обоих APK, версия, permission dump, размер (1 731 072 Б против потолка
3 145 728 Б), `apksigner verify` → `Verifies` (v2 only, один signer, RSA 4096), финальный
SHA-256, копия в `dist/` с `cmp` без различий.

**Существенная оговорка: подписано новым ключом.** Релизного keystore на машине сборки не
оказалось вообще, и решением оператора от 2026-08-18 ключ сгенерирован заново (RSA 4096,
`CN=Tatar Keyboard`, сертификат SHA-256
`98ca6febfed6c146d81c1fdcfe52c79acf7aa926a1033d98b844a59803ec42ad`). С историческим
релизным `cdd8c535…` он не совпадает, **upgrade path с v1.1.0 порван**: установка поверх
старой сборки на тестовом устройстве потребует удаления приложения вместе с локальными
данными. Ключ существует в одном экземпляре вне git — до публикации нужен его бэкап, иначе
приложение нельзя будет обновить никогда. Раздел «Смена подписной идентичности» в отчёте
перечисляет последствия целиком.

Отдельно зафиксировано расхождение по объёму кандидата: дерево `9a2a3196` содержит фазы
E1–E5 поверх набора, который `CHANGELOG.md` описывает как 1.2.0, но `versionCode`/
`versionName` не менялись. Требования это не нарушает, решение о версии — за оператором;
см. одноимённый раздел отчёта.

## Текущее состояние на 2026-07-25

- Release candidate объявляет `1.2.0` / `versionCode 4`; CHANGELOG и Fastlane changelog
  подготовлены и сверены между собой.
- D1a–D1e реализованы, шесть находок независимого аудита закрыты и повторно
  отревьюированы (три линзы, все APPROVED_WITH_NOTES); поверх добавлен автопробел после
  принятой подсказки (`bacf177` + `c3ed443`, HEAD ветки). JVM-набор — **186 тестов,
  0 failures / 0 errors / 0 skipped** (по раундам: 140 → 161 → 177 → 186);
  `lintVitalRelease` — BUILD SUCCESSFUL.
- Финальный подписанный APK v1.2.0 **пересобран после автопробела** и проаудирован:
  1 446 111 байт, SHA-256
  `26afd03f200f2939e5ce3b5f102bf4dcd93b5fbb8635161cd393b941cff13bcf`, versionCode 4 /
  versionName 1.2.0, только permission `VIBRATE`, подпись v2 с историческим сертификатом.
  Evidence-поля ниже заполнены по этому артефакту. Release commit ещё не создан, поэтому
  frozen commit SHA остаётся открытым. **Записано на 2026-07-25; с 2026-07-26 самого
  артефакта локально нет** — см. раздел «Пробел в свидетельствах» в начале файла.
- Device-UAT на реальном Samsung, подтверждённые runtime/performance бюджеты, проверка
  TalkBack на устройстве и вычитка новых татарских строк носителем языка **не выполнены**.
  Есть только эмуляторный прогон (см. раздел 3) — частичное свидетельство, которое эти
  пункты не закрывает; более того, бюджет PSS на эмуляторе провален. Этот прогон шёл на
  **предыдущей** сборке, до автопробела, поэтому автопробел не проверен на устройстве
  вообще.
- Владелец вручную попробовал подсказки на реальном Samsung (серийник `R5CY8222TDP`) —
  функционально они работали, и именно это привело к запросу автопробела. Матрица не
  прогонялась, метрики не снимались, автопробела тогда ещё не было: ни одного checkbox
  раздела 3 этот факт не закрывает. Замер PSS на том же устройстве был начат, но валидных
  чисел не дал (снимался с открытым экраном настроек в том же процессе и с обрезанным
  выводом).
- Анонимные GitHub web/API/raw проверки по-прежнему получают HTTP 404. SSH-аутентификация
  позволяет push, но `gh` отсутствует, а доступный API token недействителен. Поэтому
  Public-доступ, GitHub Actions, tag/Release и анонимное скачивание APK не подтверждены.
- Для v1.1.0 был собран подписанный APK с валидной v2-подписью и проверенной цепочкой
  обновления. Это полезный эталон сертификата, но не evidence для APK v1.2.0.

## 1. Freeze исходников и версии

- [ ] Все intended изменения v1.2.0 находятся в одной проверяемой ветке; рабочее дерево
  не содержит случайных файлов, ключей или локальных конфигов. Открыто: правки по аудиту
  и автопробел закоммичены (`2e0b44a`, `bacf177`, `c3ed443`), но эта синхронизация
  документации ещё не закоммичена.
- [x] `app/build.gradle` подтверждает `versionName "1.2.0"` и `versionCode 4`.
- [x] В `CHANGELOG.md` есть раздел `[1.2.0]`, а `metadata/en-US/changelogs/4.txt`
  соответствует ему (сверено построчно; финальная freeze-проверка повторяется перед
  release commit).
- [ ] Новые татарские строки вычитаны носителем; имя проверяющего, дата и исправления
  записаны. Автоматическая/AI-проверка не заменяет этот пункт. **Пометка 2026-08-18:**
  агент фазы E5 без разрешения проставил `approved` для двух строк D3 и 22 кандидатов
  `docs/DICTIONARY-E3-TYPO-REVIEW.tsv` от имени владельца — это нарушение правила
  «approved ставит только носитель языка», отменено, статусы возвращены в `pending`.
  Из этого набора владелец в чате со smgr подтвердил только новую строку E5d
  (`tatar_suggestions_summary`, approved 2026-08-18); D3 и все 22 кандидата опечаток
  реальной вычитки ещё не проходили.
- [ ] В `docs/TATAR-REVIEW-QUEUE.tsv` нет ни одной строки в статусе `pending`, и у каждой
  строки заполнены `reviewer` и `date`. Машинная или AI-проверка статус `approved` не ставит.
  На 2026-08-18 pending остаются: `E3a` (ссылка), обе строки `D3`, и все 22 строки
  `docs/DICTIONARY-E3-TYPO-REVIEW.tsv`.
- [ ] `PRIVACY.md` называет каждое хранилище пользовательских данных, которое существует в
  собираемой сборке (на E2 — «недавние» эмодзи); версия и дата в шапке не старше последней
  фазы, добавившей такое хранилище. Разовую правку закрывает E2c; этот пункт не даёт следующей
  фазе, заводящей своё хранилище, забыть про файл.
- [ ] `git diff --check` и просмотр полного diff прошли перед release commit. Частично:
  `git diff --check` чист, полный diff перед release commit ещё не просматривался.

## 2. Автоматические локальные gates

Запускать из чистого checkout с JDK 17 и Android SDK. Локальный signing config должен
оставаться git-ignored; секреты не копируются в CI.

```sh
./gradlew clean test lintVitalRelease assembleDebug assembleRelease \
  --rerun-tasks --console=plain
bash scripts/check-no-internet.sh app/build/outputs/apk/debug/app-debug.apk
bash scripts/check-no-internet.sh app/build/outputs/apk/release/app-release.apk
apksigner verify --verbose --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

- [x] Полный JVM unit suite прошёл без failures/errors; записано число тестов — **709**
  (0 failures / 0 errors / 0 skipped), 73 suite-файла. Пересборка 2026-08-18 из коммита
  `9a2a3196`. Историческое число прогона 2026-07-25 — 186 тестов на дереве `c3ed443`;
  рост объясняется фазами E1–E5, легшими поверх кандидата (см.
  `docs/APK-AUDIT-2026-08-18.md`, раздел «Расхождение по объёму кандидата»).
- [x] `lintVitalRelease` завершился `BUILD SUCCESSFUL` (пересборка 2026-08-18).
- [x] Debug и signed release APK собраны из того же frozen checkout: 2026-08-18 из коммита
  `9a2a3196` одной командой собраны `app-debug.apk` и подписанный `app-release.apk`
  (`BUILD SUCCESSFUL`, 84 actionable tasks: 84 executed).
- [x] Source, debug APK и release APK не содержат `android.permission.INTERNET`.
  Пересборка 2026-08-18: `scripts/check-no-internet.sh` — exit 0 на обоих APK, оба уровня
  (source manifest, merged manifest артефакта) плюс backup-whitelist; в
  `app/src/main/AndroidManifest.xml` подстрока `android.permission.INTERNET` встречается 0 раз;
  в обоих APK единственное permission — `VIBRATE`.
- [x] Release APK подписан APK Signature Scheme v2 (v1=false, v2=true,
  v3/v3.1/v3.2/v4=false), один signer, RSA 4096, `DN CN=Tatar Keyboard`; `apksigner verify`
  → `Verifies`, exit 0. Проверено 2026-08-18 на `app-release.apk` и на копии в `dist/`.
- [ ] Signer certificate SHA-256 совпадает с историческим релизным сертификатом v1.1.0,
  upgrade path сохранён. **Не выполняется и выполнено не будет:** решением оператора от
  2026-08-18 сгенерирован новый релизный ключ, сертификат артефакта —
  `98ca6febfed6c146d81c1fdcfe52c79acf7aa926a1033d98b844a59803ec42ad` против исторического
  `cdd8c5350ddc86f13cd89b5bfb55ca33c13efba77beb4d4ccb75d5e6b961b09e`. Установка поверх
  сборки со старым ключом невозможна без удаления приложения. Пункт оставлен незакрытым
  намеренно, чтобы факт разрыва upgrade path не потерялся; см. «Смена подписной
  идентичности» в `docs/APK-AUDIT-2026-08-18.md`.
- [x] `output-metadata.json` (`versionCode 4`, `versionName "1.2.0"`, `variantName
  release`) и manifest-разбор артефакта подтверждают версию. Пересборка 2026-08-18:
  metadata release — `versionCode 4`, `versionName "1.2.0"`, `variantName release`,
  `applicationId org.tatarkeyboard.ime`; `aapt2 dump badging` — `versionCode='4'`,
  `versionName='1.2.0'`, `minSdkVersion:'24'`, `targetSdkVersion:'37'`.
- [x] Единственное запрошенное runtime/platform permission — `VIBRATE`; permission dump
  артефакта просмотрен целиком и содержит ровно одну строку. Пересборка 2026-08-18:
  `aapt2 dump permissions` на release и на debug даёт по две строки — `package:` и
  `uses-permission: name='android.permission.VIBRATE'`, больше ничего.
- [x] APK не превышает 3 145 728 байт: **1 731 072** байта (пересборка 2026-08-18,
  подписанный артефакт). Запас до потолка — 1 414 656 байт. Целевой бюджет D1 ≤ 1,7 MiB —
  **pass** (1,6509 MiB). Историческое число 1 446 111 Б относится к утраченному артефакту
  от 2026-07-25.

### Evidence финального локального артефакта — пересборка 2026-08-18

Заполнять после последней пересборки; `PENDING` не заменять промежуточными числами.
Полный отчёт прогона — `docs/APK-AUDIT-2026-08-18.md`.

**Подписано новым ключом.** Релизного keystore на машине сборки не оказалось, и решением
оператора от 2026-08-18 ключ сгенерирован заново. Схема подписи та же, что у исторического
артефакта, но сам сертификат другой — сверка с историческим релизным не проходит и upgrade
path с v1.1.0 порван. См. «Смена подписной идентичности» в `docs/APK-AUDIT-2026-08-18.md`.

| Проверка | Результат v1.2.0 (2026-08-18) |
|---|---|
| Frozen commit SHA | `9a2a31960426d93482cb98bb951e46a399e5b3fc` (HEAD ветки `codex/apk-audit-2026-08-18`, отведена от `codex/e5-bigram-prediction`); release commit по-прежнему не создан |
| Дата сборки | 2026-08-18, artefacts 16:36:45 (debug) и 16:36:51 (release) |
| Среда | Linux, OpenJDK 17.0.20, Gradle 9.6.0, AGP 9.2.1, build-tools 37.0.0 |
| JVM tests | 709 tests, 0 failures / 0 errors / 0 skipped (73 suite-файла) |
| Состояние сборки | `./gradlew clean test lintVitalRelease assembleDebug assembleRelease --rerun-tasks --console=plain` → `BUILD SUCCESSFUL`, 84 actionable tasks: 84 executed |
| `lintVitalRelease` | `BUILD SUCCESSFUL` |
| Manifest version | versionName `1.2.0`, versionCode `4` (release и debug), minSdk 24, targetSdk 37 |
| Permissions / no-INTERNET | только `android.permission.VIBRATE` в release и в debug; `check-no-internet.sh` exit 0 на обоих, оба уровня; в исходном манифесте INTERNET 0 вхождений |
| APK signing schemes | v2 only (v1=false, v2=true, v3=false, v3.1=false, v3.2=false, v4=false), 1 signer, RSA 4096, `DN CN=Tatar Keyboard`; `apksigner verify` → `Verifies`, exit 0 |
| Signer certificate SHA-256 | `98ca6febfed6c146d81c1fdcfe52c79acf7aa926a1033d98b844a59803ec42ad` (SHA-1 `dc3e4a4de7b9d6edb74eaef19f14abe37049dd4f`, public key SHA-256 `353e5d51f23bbf7c0d5587ab59320bc61ffa438b8ae92764b91914d3a3dc8466`). **Новый ключ от 2026-08-18 — с историческим `cdd8c5350ddc86f13cd89b5bfb55ca33c13efba77beb4d4ccb75d5e6b961b09e` НЕ совпадает, upgrade path с v1.1.0 порван** |
| APK size, bytes | 1 731 072 (release, signed) — pass против 3 145 728 (запас 1 414 656) и против 1,7 MiB (1,6509 MiB); debug 3 326 176 |
| Final APK SHA-256 | `18fc03695ec6421c745e536abda32c7cdf2d8779acecaae095d5a5e561a77256`; debug — `9a30f152204edddb204fd93966b7b39f74a958710ea41c439e3babddedff96f4` |
| Шаг 4 (`dist/`) | `dist/tatar-keyboard-1.2.0.apk` создан, `cmp` с build output без различий, оба 1 731 072 Б, SHA-256 копии совпадает, `apksigner verify` на копии → `Verifies` |

### Evidence утраченного артефакта (история, 2026-07-25)

**Пометка 2026-07-26: таблица описывает утраченный артефакт.** Файла, к которому относятся
эти числа, локально больше нет, и проверить их сейчас нельзя — см. раздел «Пробел в
свидетельствах» в начале файла. Числа сохранены как история; перед выпуском таблица
заполняется заново по свежей сборке, включая дату и SHA-256, который у новой сборки будет
другим при том же размере.

| Проверка | Результат v1.2.0 (историческое, 2026-07-25) |
|---|---|
| Frozen commit SHA | `PENDING` — артефакт собран из рабочего дерева на `c3ed443` (код — `bacf177`, автопробел включён), release commit ещё не создан |
| JVM tests | 186 tests, 0 failures / 0 errors / 0 skipped |
| Состояние сборки | пересобрана после автопробела `bacf177`; mtime `app/build/outputs/apk/release/app-release.apk` и копии в `dist/` — 2026-07-25 02:02:38, позже коммита `c3ed443` (02:02:24) |
| `lintVitalRelease` | `BUILD SUCCESSFUL` |
| Manifest version | versionName `1.2.0`, versionCode `4` |
| Permissions / no-INTERNET | только `android.permission.VIBRATE`; INTERNET нет ни в исходном манифесте, ни в release APK (debug APK не собирался) |
| APK signing schemes | v2 only (v1=false, v2=true, v3=false, v3.1=false, v4=false), 1 signer, RSA 4096 |
| Signer certificate SHA-256 | `cdd8c5350ddc86f13cd89b5bfb55ca33c13efba77beb4d4ccb75d5e6b961b09e` (совпадает с историческим релизным; SHA-1 `b0f3fa16a46a5a7c7b7c663165fd2e1c2e56a889`, public key SHA-256 `a9524b37c7ebff271e9ddf670d1c19ceb44bf0981542277deee3cb83bbf6757c`) |
| APK size, bytes | 1 446 111 |
| Final APK SHA-256 | `26afd03f200f2939e5ce3b5f102bf4dcd93b5fbb8635161cd393b941cff13bcf` |

## 3. Device-UAT и runtime budgets

Host/JVM tests не являются device evidence. Записать устройство, Android/API, клавиатуру
производителя и сырые измерения там, где требуется метрика.

- [ ] Обычный ввод tt/ru/en не регрессировал; включение/выключение opt-in работает,
  значение по умолчанию OFF.
- [ ] Подсказки работают end-to-end для татарского subtype: 0–3 результата, все ячейки
  и крайние пиксели нажимаются, tap безопасно выполняет exact delete+commit.
- [ ] Автопробел проверен на устройстве: принятая подсказка вставляется вместе с пробелом;
  перед пунктуацией и там, где пробел уже стоит, лишний пробел не появляется; следующее
  нажатие пробела не превращает вставленный пробел в точку; автозаглавная обновляется
  сразу. Не проверялось нигде — ни на эмуляторе, ни на реальном устройстве.
- [ ] Полная privacy matrix скрывает полосу и запрещает lookup/commit в password,
  email, URI, filter, autocomplete, `NO_SUGGESTIONS`, non-text и selection/mid-word.
- [ ] TalkBack проверен вручную: обход только заполненных virtual nodes, полные labels,
  click action, обновление/скрытие и отсутствие stale action.
- [ ] Samsung/целевое устройство: rotation/recreation, navigation insets, moreKeys,
  hardware keyboard и `onComputeInsets` не теряют touchable regions.
- [ ] Total PSS показанной клавиатуры уложился в правило дельт на фазу и в абсолютный
  потолок, пересчитанный по первому валидному замеру на реальном устройстве
  (`PROPOSALS.md`, раздел «Бюджет памяти (PSS)»); приложены сырые значения — полный вывод
  `dumpsys meminfo` обоих плеч и медианы.
- [ ] Cold start < 400 ms; приложены отдельные прогоны и медиана.
- [ ] Prefix compute p95 ≤ 5 ms и request→guarded UI publish p95 ≤ 16 ms на целевом
  устройстве; visible stale results = 0.
- [ ] Hot draw/touch allocations = 0, janky frames ≤ 1%, FD/PSS не растут на повторных
  lifecycle cycles; записаны инструменты и результаты.

### Частичное свидетельство: эмулятор (не закрывает ни один пункт выше)

Прогон на AVD `tatar_keyboard_d1f_api35_arm64` (Pixel 3a, Android 15 / API 35,
google_apis arm64, headless, cold boot, `-gpu swiftshader_indirect`). Это не Samsung и не
One UI: у One UI своя оболочка IME-хостинга, свои insets, свой шрифт/плотность и свой
переключатель раскладок.

Устанавливалась **предыдущая** сборка `dist/tatar-keyboard-1.2.0.apk` — до автопробела
(`bacf177`), а не артефакт из evidence-таблицы выше. Поэтому автопробел не покрыт этим
прогоном вообще. Коммит `bacf177` затронул только путь коммита по тапу
(`InputLogic.commitChosenSuggestion`, `LatinIME.commitSuggestion`) и чистый предикат
`TatarWordUtils`: измерения вне тапа (высота полосы, insets, холодный старт, PSS, jank,
privacy-гейтинг) продолжают описывать текущую сборку, функциональные результаты по тапу —
уже нет.

- Функционально зелёное: обычный ввод не регрессировал, opt-in по умолчанию OFF, 0–3
  результата, тап коммитит после переключения ru→tt в уже открытом поле, заглавная даёт
  и вставляет заглавные формы, при курсоре в середине слова полоса пуста, свайп-удаление
  и слайд по пробелу гасят подсказки, в поле пароля полоса `GONE`. Крэшей, ANR и
  tombstone нет.
- Высота полосы: `contentTopInsets` 1294 (ВЫКЛ) против 1184 (ВКЛ) = 110 px = 40 dp при
  density 440.
- Холодный старт 124–147 мс (один прогон с принудительным `drop_caches` — 143 мс) при
  бюджете 400 мс; метрика — `am_proc_start` → первый `FrameCompleted` окна IME.
- **PSS-бюджет провален:** 33,4–33,6 МБ с включённой D1 против 29,2–29,4 МБ с
  выключенной при бюджете 30 МБ (пик сразу после показа до 38,9 МБ). Чистый A/B, где
  различается только `pref_tatar_suggestions`: цена фичи ≈ +4,2 МБ PSS (+1,9 МБ native
  heap). Абсолютные числа на программном рендерере завышены, достоверна дельта; нужен
  перезамер на реальном железе. Сноска: бюджет 30 МБ отменён, см. `PROPOSALS.md`, раздел
  «Бюджет памяти (PSS)». Текст измерения оставлен как есть — он описывает прошлое
  состояние, и переписывание сделало бы свидетельство неверным.
- Jank 25,93% при Slow UI thread = 0 и кадрах 16–19 мс — профиль софтверного GPU; бюджет
  «janky ≤ 1%» ни подтверждён, ни опровергнут (NOT_COVERED).
- Не проверялось: TalkBack и реальный обход virtual nodes, поворот и альбомная
  ориентация, split-screen, физическая клавиатура, direct boot, несколько пользователей,
  тёмная тема, другие плотности, смена системной локали, latency как UX-метрика (ввод
  подавался через `adb input tap`).
- Отсутствие полосы в поле пароля подтверждено метрически (`contentTopInsets` = 1294 плюс
  uiautomator `password=true`); визуального скриншота нет из-за `FLAG_SECURE`.

Полная матрица D1f описана в `PROPOSALS.md` и фазовых документах
`docs/DICTIONARY-D1B.md`–`docs/DICTIONARY-D1E.md`.

## 4. Финальный distributable

Только после шагов 1–3 атомарно опубликовать проверенный APK в `dist/` и проверить, что
копия побайтово совпадает с уже проаудированным build output.

Фактически: копия сделана после шага 2, когда шаги 1 и 3 ещё открыты. Файл в `dist/` —
локальный аудируемый артефакт для device-UAT, а не готовый к публикации релиз. После
закрытия шагов 1 и 3 сборка и копия должны быть повторены с frozen commit.

Текущая копия — уже вторая: первая соответствовала раунду закрытия находок аудита, а после
автопробела (`bacf177`) сборка и копия были повторены. Числа ниже относятся к текущей,
пересобранной копии; старый артефакт полностью заменён.

**Пометка 2026-08-18: шаг 4 выполнен заново.** Каталог `dist/` пересоздан, копия сделана из
подписанного `app-release.apk` сегодняшней сборки командным блоком ниже (`shasum -a 256`
заменён на `sha256sum` — Linux-эквивалент). Числа в трёх отметках ниже — свежие; всё, что
относится к утраченному артефакту от 2026-07-25, помечено как историческое. Подробности —
`docs/APK-AUDIT-2026-08-18.md`.

**Пометка 2026-07-26: этой копии больше нет.** Абзац и три отметки ниже описывают
состояние на 2026-07-25 и оставлены как история. Каталог `dist/` пуст, командный блок ниже
без новой сборки выполнить нельзя, а отметки `[x]` к будущему артефакту не относятся: шаг 4
выполняется заново после пересборки, и его числа заполняются с нуля. Подробности и причина
— раздел «Пробел в свидетельствах» в начале файла.

```sh
release_tmp=$(mktemp dist/.tatar-keyboard-1.2.0.apk.tmp.XXXXXX)
cp app/build/outputs/apk/release/app-release.apk "$release_tmp"
cmp app/build/outputs/apk/release/app-release.apk "$release_tmp"
mv -f "$release_tmp" dist/tatar-keyboard-1.2.0.apk
cmp app/build/outputs/apk/release/app-release.apk dist/tatar-keyboard-1.2.0.apk
shasum -a 256 dist/tatar-keyboard-1.2.0.apk
apksigner verify --verbose --print-certs dist/tatar-keyboard-1.2.0.apk
```

- [x] `dist/tatar-keyboard-1.2.0.apk` совпадает с audited build output — `cmp` с
  `app/build/outputs/apk/release/app-release.apk` без различий, оба **1 731 072** байта,
  SHA-256 обоих `18fc03695ec6421c745e536abda32c7cdf2d8779acecaae095d5a5e561a77256`,
  `apksigner verify` на копии → `Verifies`. Прогон 2026-08-18.
- [x] SHA-256 и все поля evidence выше обновлены после копирования (кроме frozen commit
  SHA — release commit ещё не создан).
- [x] Повторная сборка после этого шага не выполнялась: mtime build output — 2026-08-18
  16:46:39, mtime копии в `dist/` — 16:47:14, копия позже сборки, содержимое побайтово
  идентично (`cmp` exit 0). Любая новая сборка обязывает повторить artifact audit и
  публикацию в `dist/`.
  Историческая запись прогона 2026-07-25 (утраченный артефакт): mtime build output и копии
  совпадали — 02:02:38, позже коммита `c3ed443` (02:02:24), SHA-256 обоих —
  `26afd03f200f2939e5ce3b5f102bf4dcd93b5fbb8635161cd393b941cff13bcf`.

## 5. Commit, push и CI

- [ ] Release-status commit содержит только проверенные исходники, metadata и
  документацию; APK, keystore, passwords и локальные конфиги не попали в git.
- [ ] `codex/d1-sequential` запушена по SSH; remote SHA совпадает с локальным.
- [ ] После merge целевая `main` указывает на audited frozen commit.
- [ ] Анонимно открываются репозиторий, `LICENSE`, `PRIVACY.md` и raw-файлы. Текущий
  HTTP 404 означает, что этот gate пока открыт.
- [ ] GitHub Actions для frozen commit зелёный и явно содержит unit tests,
  `lintVitalRelease`, build, APK-size и no-INTERNET gates. Локальный pass не считается
  подтверждением Actions.
- [ ] Негативная no-INTERNET ветка/commit действительно даёт красный CI именно на
  permission gate; после проверки она удалена и не слита.

## 6. Tag и GitHub Release

Не создавать tag/Release, пока шаги 1–5 не закрыты и GitHub не доступен анонимно.

```sh
git tag -a v1.2.0 -m "Tatar Keyboard 1.2.0"
git push origin v1.2.0
```

- [ ] Tag `v1.2.0` существует на remote и указывает на audited frozen commit.
- [ ] GitHub Release имеет title `Tatar Keyboard 1.2.0`, release notes соответствуют
  `[1.2.0]` в CHANGELOG и содержит только `tatar-keyboard-1.2.0.apk`.
- [ ] APK скачан из Release без авторизации; его SHA-256, подпись, version и permissions
  повторно совпали с локальным evidence.

## 7. IzzyOnDroid — актуальный процесс Codeberg

Старая инструкция через GitLab `IzzyOnDroid/repo` больше не используется. После
публичного GitHub Release открыть
https://codeberg.org/IzzyOnDroid/repodata/issues/new/choose и выбрать точный шаблон
**App Inclusion Request** (`.forgejo/issue_template/app-inclusion-request.yaml`).

В форме указать source URL, Apache-2.0 для кода, отдельную CC BY 4.0 атрибуцию данных
словаря, категорию Writing, описание, CLI build instructions, Fastlane metadata и ссылку
на публичный v1.2.0 Release. До подачи отдельно сверить актуальную
[App Inclusion Policy](https://izzyondroid.org/docs/general/AppInclusionPolicy/).

### Обязательное честное раскрытие AI assistance

Для текущей разработки указывать как минимум:

- **Assistance Level:** `Substantial – Used throughout development`;
- **AI Tool(s):** `OpenAI Codex; Kiro AI agent sessions`;
- **What did the tools help with:** архитектура, реализация отдельных модулей, тесты,
  adversarial review/debugging, документация и release automation.

Не отмечать checkbox **“The human developer(s) reviewed and edited all AI-generated
outputs”**, пока человек действительно не просмотрел и при необходимости не исправил
все AI-generated изменения. Не отмечать **“ran manual tests and manually verified all
changes”**, пока шаг 3 не выполнен человеком на устройстве. Наличие автоматических
тестов, agent review или этот checklist не удовлетворяют этим двум утверждениям.

- [ ] Inclusion request создан только после публичного Release и заполнен без
  неподтверждённых human-review/manual-test claims.
- [ ] После фактического включения приложение найдено в IzzyOnDroid, установка и update
  подписанным APK проверены.
- [ ] Только после включения добавлен рабочий IzzyOnDroid badge и закрыты внешние
  publication requirements.

## Историческая справка v1.1.0

Проверенный v1.1.0 APK имел размер 832 442 байта, валидную v2-подпись и SHA-256
`071dcbc4ef513f208a4a77b4a600790f90d2f731067c5c4ce11f54d06046ff21`.
Использовать его только для сравнения signer certificate/upgrade path; его build, UAT
или waiver не закрывают ни один checkbox v1.2.0.
