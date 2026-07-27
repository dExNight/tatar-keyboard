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
  записаны. Автоматическая/AI-проверка не заменяет этот пункт.
- [ ] В `docs/TATAR-REVIEW-QUEUE.tsv` нет ни одной строки в статусе `pending`, и у каждой
  строки заполнены `reviewer` и `date`. Машинная или AI-проверка статус `approved` не ставит.
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

- [x] Полный JVM unit suite прошёл без failures/errors; записано число тестов — **186**
  (0 failures / 0 errors / 0 skipped). Прогон выполнен на дереве с автопробелом.
- [x] `lintVitalRelease` завершился `BUILD SUCCESSFUL`.
- [ ] Debug и signed release APK собраны из того же frozen checkout. Частично: собран и
  проаудирован release APK; debug APK в этом прогоне не собирался
  (`app/build/outputs/apk/debug/` отсутствует).
- [ ] Source, debug APK и release APK не содержат `android.permission.INTERNET`.
  Частично: в `app/src/main/AndroidManifest.xml` INTERNET отсутствует, в release APK
  единственное permission — `VIBRATE`; debug APK не собирался и потому не проверен.
- [x] Release APK подписан APK Signature Scheme v2 (v1=false, v2=true, v3/v3.1/v4=false),
  один signer, RSA 4096, `DN CN=Tatar Keyboard`; signer certificate SHA-256 совпадает с
  историческим релизным сертификатом v1.1.0, upgrade path сохранён; `apksigner verify` →
  `Verifies`.
- [x] `output-metadata.json` (`versionCode 4`, `versionName "1.2.0"`, `variantName
  release`) и manifest-разбор артефакта в отчёте gate подтверждают версию.
- [x] Единственное запрошенное runtime/platform permission — `VIBRATE`; permission dump
  артефакта просмотрен целиком и содержит ровно одну строку.
- [x] APK не превышает 3 145 728 байт: **1 446 111** байт. Целевой бюджет D1 ≤ 1.7 MB —
  **pass** (1,38 MiB).

### Evidence финального локального артефакта

Заполнять после последней пересборки; `PENDING` не заменять промежуточными числами.

**Пометка 2026-07-26: таблица описывает утраченный артефакт.** Файла, к которому относятся
эти числа, локально больше нет, и проверить их сейчас нельзя — см. раздел «Пробел в
свидетельствах» в начале файла. Числа сохранены как история; перед выпуском таблица
заполняется заново по свежей сборке, включая дату и SHA-256, который у новой сборки будет
другим при том же размере.

| Проверка | Результат v1.2.0 |
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
  `app/build/outputs/apk/release/app-release.apk` без различий, оба 1 446 111 байт.
- [x] SHA-256 и все поля evidence выше обновлены после копирования (кроме frozen commit
  SHA — release commit ещё не создан).
- [x] Повторная сборка после этого шага не выполнялась: mtime build output и mtime копии в
  `dist/` совпадают — 2026-07-25 02:02:38, то есть обе позже последнего коммита `c3ed443`
  (02:02:24), содержимое побайтово идентично (`cmp`), SHA-256 обоих файлов —
  `26afd03f200f2939e5ce3b5f102bf4dcd93b5fbb8635161cd393b941cff13bcf`. Любая новая сборка
  обязывает повторить artifact audit и публикацию в `dist/` — как это и было сделано после
  автопробела.

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
