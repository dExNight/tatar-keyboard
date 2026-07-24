# PUBLISH-CHECKLIST — публикация v1.2.0

Пошаговый release gate для `versionName 1.2.0` / `versionCode 4`. Галочка означает
только проверенный результат именно кандидата v1.2.0. Исторические результаты v1.1.0
не переносятся на новый APK и явно помечены как справочные.

## Текущее состояние на 2026-07-24

- Release candidate объявляет плановые `1.2.0` / `versionCode 4`; CHANGELOG и Fastlane
  changelog подготовлены, но должны пройти финальную freeze-проверку после объединения
  всех правок.
- D1a–D1e реализованы. Автоматические JVM-тесты и `lintVitalRelease` проходили на
  текущем hardening-коде, но финальный clean artifact audit D1f ещё не записан.
- Финальный подписанный APK v1.2.0, его размер, версия, permissions, сертификат и
  SHA-256 пока не подтверждены. Поля evidence ниже заполняются только после повторного
  аудита готового артефакта.
- Device-UAT, Android runtime/performance measurements, проверка TalkBack и вычитка
  новых татарских строк носителем языка не выполнены и не заявляются.
- Анонимные GitHub web/API/raw проверки по-прежнему получают HTTP 404. SSH-аутентификация
  позволяет push, но `gh` отсутствует, а доступный API token недействителен. Поэтому
  Public-доступ, GitHub Actions, tag/Release и анонимное скачивание APK не подтверждены.
- Для v1.1.0 был собран подписанный APK с валидной v2-подписью и проверенной цепочкой
  обновления. Это полезный эталон сертификата, но не evidence для APK v1.2.0.

## 1. Freeze исходников и версии

- [ ] Все intended изменения v1.2.0 находятся в одной проверяемой ветке; рабочее дерево
  не содержит случайных файлов, ключей или локальных конфигов.
- [ ] `app/build.gradle` подтверждает `versionName "1.2.0"` и `versionCode 4`.
- [ ] В `CHANGELOG.md` есть финальный раздел `[1.2.0]`, а
  `metadata/en-US/changelogs/4.txt` соответствует ему.
- [ ] Новые татарские строки вычитаны носителем; имя проверяющего, дата и исправления
  записаны. Автоматическая/AI-проверка не заменяет этот пункт.
- [ ] `git diff --check` и просмотр полного diff прошли перед release commit.

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

- [ ] Полный JVM unit suite прошёл без failures/errors; записано число тестов.
- [ ] `lintVitalRelease` завершился `BUILD SUCCESSFUL`.
- [ ] Debug и signed release APK собраны из того же frozen checkout.
- [ ] Source, debug APK и release APK не содержат `android.permission.INTERNET`.
- [ ] Release APK подписан APK Signature Scheme v2; signer certificate совпадает с
  опубликованным v1.1.0, чтобы сохранить upgrade path.
- [ ] `output-metadata.json` и `aapt2 dump badging` подтверждают versionCode 4 и
  versionName 1.2.0.
- [ ] Единственное запрошенное runtime/platform permission — `VIBRATE`; полный permission
  dump просмотрен.
- [ ] APK не превышает 3 145 728 байт; целевой бюджет D1 ≤ 1.7 MB также записан как
  pass/fail, без подмены абсолютного лимита.

### Evidence финального локального артефакта

Заполнять после последней пересборки; `PENDING` не заменять промежуточными числами.

| Проверка | Результат v1.2.0 |
|---|---|
| Frozen commit SHA | `PENDING` |
| JVM tests | `PENDING` |
| `lintVitalRelease` | `PENDING` |
| Manifest version | `PENDING` |
| Permissions / no-INTERNET | `PENDING` |
| APK signing schemes | `PENDING` |
| Signer certificate SHA-256 | `PENDING` |
| APK size, bytes | `PENDING` |
| Final APK SHA-256 | `PENDING` |

## 3. Device-UAT и runtime budgets

Host/JVM tests не являются device evidence. Записать устройство, Android/API, клавиатуру
производителя и сырые измерения там, где требуется метрика.

- [ ] Обычный ввод tt/ru/en не регрессировал; включение/выключение opt-in работает,
  значение по умолчанию OFF.
- [ ] Подсказки работают end-to-end для татарского subtype: 0–3 результата, все ячейки
  и крайние пиксели нажимаются, tap безопасно выполняет exact delete+commit.
- [ ] Полная privacy matrix скрывает полосу и запрещает lookup/commit в password,
  email, URI, filter, autocomplete, `NO_SUGGESTIONS`, non-text и selection/mid-word.
- [ ] TalkBack проверен вручную: обход только заполненных virtual nodes, полные labels,
  click action, обновление/скрытие и отсутствие stale action.
- [ ] Samsung/целевое устройство: rotation/recreation, navigation insets, moreKeys,
  hardware keyboard и `onComputeInsets` не теряют touchable regions.
- [ ] Total PSS показанной клавиатуры ≤ 30 MB; приложены сырые значения.
- [ ] Cold start < 400 ms; приложены отдельные прогоны и медиана.
- [ ] Prefix compute p95 ≤ 5 ms и request→guarded UI publish p95 ≤ 16 ms на целевом
  устройстве; visible stale results = 0.
- [ ] Hot draw/touch allocations = 0, janky frames ≤ 1%, FD/PSS не растут на повторных
  lifecycle cycles; записаны инструменты и результаты.

Полная матрица D1f описана в `PROPOSALS.md` и фазовых документах
`docs/DICTIONARY-D1B.md`–`docs/DICTIONARY-D1E.md`.

## 4. Финальный distributable

Только после шагов 1–3 атомарно опубликовать проверенный APK в `dist/` и проверить, что
копия побайтово совпадает с уже проаудированным build output.

```sh
release_tmp=$(mktemp dist/.tatar-keyboard-1.2.0.apk.tmp.XXXXXX)
cp app/build/outputs/apk/release/app-release.apk "$release_tmp"
cmp app/build/outputs/apk/release/app-release.apk "$release_tmp"
mv -f "$release_tmp" dist/tatar-keyboard-1.2.0.apk
cmp app/build/outputs/apk/release/app-release.apk dist/tatar-keyboard-1.2.0.apk
shasum -a 256 dist/tatar-keyboard-1.2.0.apk
apksigner verify --verbose --print-certs dist/tatar-keyboard-1.2.0.apk
```

- [ ] `dist/tatar-keyboard-1.2.0.apk` совпадает с audited build output.
- [ ] SHA-256 и все поля evidence выше обновлены после копирования.
- [ ] Повторная сборка после этого шага не выполнялась; иначе весь artifact audit и
  публикация в `dist/` повторены.

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
