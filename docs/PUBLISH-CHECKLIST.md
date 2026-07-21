# PUBLISH-CHECKLIST — публикация v1.1.0

Пошаговая инструкция для ручной публикации. Галочки ниже отражают только подтверждённые
факты; внешние шаги без проверки в GitHub не считаются выполненными.

## Состояние на 2026-07-22

- Release-preparation commit `45f9831` (`chore(release): prepare v1.1.0`) присутствует
  в истории `main`; локальный `main` и `origin/main` синхронизированы
- Версия `1.1.0` (`versionCode 3`), CHANGELOG и release metadata закоммичены и запушены
- Пользователь сообщил 2026-07-22, что переключил репозиторий в Public; независимая
  анонимная проверка GitHub web/API/raw во время release-close всё ещё получила HTTP 404
- Ветки `ci-negative-test` локально и на `origin` указывают на `468ee6a` с намеренно
  добавленным разрешением `INTERNET`; green/red результаты Actions не проверены и не
  утверждаются, пользователь явно снял их как блокер v1.1.0 2026-07-22
- Подписанный `app/build/outputs/apk/release/app-release.apk` собран для `1.1.0`,
  проверен и скопирован в `dist/tatar-keyboard-1.1.0.apk`; старый артефакт `1.0.1`
  сохранён отдельно
- Финальный артефакт в `dist/` зафиксирован после последней чистой сборки; отдельные
  подписанные сборки не считаются побайтово воспроизводимыми

## Шаг 1. ✅ ВЫПОЛНЕНО — Бэкап ключа подписи

Бэкап `release.jks`, `keystore.properties` и паролей сделан минимум в двух местах вне
репозитория. Не публикуй и не коммить эти файлы.

## Шаг 2. Репозиторий и публичные страницы

- [x] Репозиторий создан: https://github.com/dExNight/tatar-keyboard
- [x] URL политики и лицензии в приложении указывают на этот репозиторий
- [ ] Повторить анонимную проверку Public: пользователь сообщил о переключении 2026-07-22,
  но web/API/raw endpoints во время release-close отвечали HTTP 404
- [ ] Без авторизации открыть репозиторий, `PRIVACY.md` и `LICENSE`: на release-close все
  три проверки получили HTTP 404, поэтому доступность для GitHub Release/Izzy не доказана

Расхождение Public не блокирует создание тега по прямому решению пользователя, но должно
быть закрыто до GitHub Release и заявки IzzyOnDroid.

## Шаг 3. GitHub Actions и негативный тест no-INTERNET — waiver для v1.1.0

- [x] Пользователь 2026-07-22 явно разрешил продолжить без проверки GitHub Actions;
  green/red статусы не считаются подтверждёнными, но не блокируют тег `v1.1.0`
- [ ] На вкладке **Actions** убедиться, что последний CI для `main` зелёный — не проверено,
  waived для тега `v1.1.0`
- [ ] Найти прогон ветки `ci-negative-test` для коммита `468ee6a` и убедиться, что он
  красный именно из-за no-INTERNET-гейта — не проверено, waived для тега `v1.1.0`
- [ ] Только после подтверждения удалить тестовую ветку:

```sh
git push origin --delete ci-negative-test
git branch -D ci-negative-test
```

Ветку `ci-negative-test` не сливать: она основана на старом состоянии проекта и намеренно
добавляет `android.permission.INTERNET`.

## Шаг 4. Проверки на устройстве

- [x] Татарский UI и экраны приложения — PASS по подтверждению пользователя 2026-07-22
- [x] TalkBack-UAT A1–A4 — PASS по подтверждению пользователя 2026-07-22
- [x] PERF-02a: PSS показанной клавиатуры не больше 30 720 КБ — PASS по подтверждению
  пользователя 2026-07-22; исходные три числовых значения не записаны
- [x] PERF-02b: холодный старт меньше 400 мс — PASS по подтверждению пользователя
  2026-07-22; исходные пять значений и медиана не записаны
- [ ] PERF-03: аллокации/GC/janky frames — нового результата пользователь не сообщал;
  PASS не утверждается. Требование остаётся открытым и переносится после тега: прямая
  команда пользователя создать `v1.1.0` делает его неблокирующим только для этого тега

PERF-команды и место для результатов находятся в
`.planning/milestones/v1.0-phases/11-proizvoditelnost-i-reliz/11-PERF-CHECKLIST.md`.

## Шаг 5. Финальная локальная сборка

- [x] В `app/build.gradle`: `versionName "1.1.0"`, `versionCode 3`
- [x] В `CHANGELOG.md` подготовлен раздел `[1.1.0]`
- [x] В `metadata/en-US/changelogs/3.txt` подготовлено краткое описание для
  `versionCode 3`
- [x] Выполнены локальные гейты:

```sh
./gradlew assembleDebug --rerun-tasks --console=plain
bash scripts/check-no-internet.sh app/build/outputs/apk/debug/app-debug.apk
./gradlew test --rerun-tasks --console=plain
./gradlew lintVitalRelease --rerun-tasks --console=plain
./gradlew clean lintVitalRelease assembleRelease --rerun-tasks --console=plain
bash scripts/check-no-internet.sh app/build/outputs/apk/release/app-release.apk
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
stat -f%z app/build/outputs/apk/release/app-release.apk
```

- [x] `assembleDebug`, `lintVitalRelease` и финальный `assembleRelease` прошли; Gradle
  завершил чистую release-сборку за 12 секунд (`45 actionable tasks`)
- [x] `./gradlew test` прошёл, но тестовых исходников в проекте нет: задачи unit-тестов
  имеют статус `NO-SOURCE`, поэтому это только проверка task graph, а не поведения
- [x] `check-no-internet.sh` прошёл для debug и release APK; в merged manifest release
  присутствует только `android.permission.VIBRATE`, разрешения `INTERNET` нет
- [x] `apksigner verify --verbose` подтвердил одного подписанта и валидную подпись APK
  Signature Scheme v2
- [x] SHA-256 сертификата подписанта совпадает с APK `1.0.1`, поэтому цепочка обновления
  сохранена; подпись копии в `dist/` также проверена
- [x] Размер release APK — **832 442 байта**, что меньше лимита 3 145 728 байт
- [x] `output-metadata.json` и `aapt2 dump badging` подтверждают `versionName 1.1.0` и
  `versionCode 3`
- [x] SHA-256 финального `dist/tatar-keyboard-1.1.0.apk`:
  `071dcbc4ef513f208a4a77b4a600790f90d2f731067c5c4ce11f54d06046ff21`
- [x] Текущий проверенный `app-release.apk` после последней чистой сборки атомарно
  скопирован в `dist/`; сразу после копирования `cmp` подтвердил побайтовое совпадение:

```sh
release_tmp=$(mktemp dist/.tatar-keyboard-1.1.0.apk.tmp.XXXXXX)
cp app/build/outputs/apk/release/app-release.apk "$release_tmp"
cmp app/build/outputs/apk/release/app-release.apk "$release_tmp"
mv -f "$release_tmp" dist/tatar-keyboard-1.1.0.apk
cmp app/build/outputs/apk/release/app-release.apk dist/tatar-keyboard-1.1.0.apk
shasum -a 256 dist/tatar-keyboard-1.1.0.apk
```

Новая `assembleRelease` создаёт нового кандидата: после любой пересборки повтори
проверки подписи, версии, разрешений и размера, заново замени файл в `dist/` и обнови
SHA-256 выше. Совпадение SHA-256 между отдельными подписанными сборками не требуется и
не заявляется.

## Шаг 6. Коммит и push релизной подготовки

- [x] Перед коммитом проверены `git diff` и `git status --short`; `HANDOFF.md`, ключи и
  APK не попали в release-preparation commit
- [x] Проверенные изменения релиза закоммичены; commit `45f9831` присутствует в `main`
- [x] `main` запушен; локальный `main` и `origin/main` синхронизированы
- [x] Ожидание green CI снято для `v1.1.0` явным решением пользователя 2026-07-22;
  фактический результат Actions не проверен и не утверждается

## Шаг 7. Тег v1.1.0

Создавать тег только на запушенном финальном release-status commit после freeze-проверок
версии и APK. Открытые Public, CI и PERF-03 явно записаны выше как неподтверждённые,
waived/deferred и неблокирующие тег по прямому решению пользователя 2026-07-22.

```sh
git tag v1.1.0
git push origin v1.1.0
```

- [ ] Тег `v1.1.0` появился на GitHub и указывает на тот же commit SHA, что и проверенный
  релизный коммит

## Шаг 8. GitHub Release — готовый draft

1. Открыть https://github.com/dExNight/tatar-keyboard/releases/new
2. Выбрать тег **v1.1.0**
3. Точный title: **Tatar Keyboard 1.1.0**
4. Точный body (совпадает с разделом `[1.1.0]` в CHANGELOG):

```markdown
Татарский интерфейс, обновлённые экраны приложения и улучшения доступности.

### Татарский интерфейс

- Добавлен полный татарский перевод настроек, онбординга и строк TalkBack
- Перевод прошёл лингвистическую проверку; финальные формулировки и терминология подтверждены носителем языка

### Доступность

- Альтернативы по долгому нажатию стали доступны в TalkBack: символы можно услышать и выбрать проводкой пальца
- Пробел озвучивает текущий язык клавиатуры
- TalkBack сообщает об изменениях статусов онбординга и ручном переключении Shift/Caps Lock

### Интерфейс приложения

- Онбординг, настройки и управление языками получили единый карточный интерфейс со светлым и тёмным оформлением
- Экраны настроек переведены с устаревшего `android.preference` на лёгкие нативные View без новых зависимостей
```

5. Приложить **`tatar-keyboard-1.1.0.apk`** из шага 5
6. Перед Publish ещё раз проверить title, tag, имя APK и размер
7. Опубликовать Release

- [ ] Release опубликован, APK скачивается без авторизации и его подпись проверяется

## Шаг 9. Заявка IzzyOnDroid

1. Открыть https://gitlab.com/IzzyOnDroid/repo/-/issues/new
2. Выбрать шаблон **App inclusion request**
3. Указать URL `https://github.com/dExNight/tatar-keyboard`
4. Сослаться на Release `v1.1.0`, лицензию Apache-2.0 и `PRIVACY.md`

- [ ] Заявка создана только после появления публичного Release с подписанным APK

## Шаг 10. После включения в IzzyOnDroid

- [ ] Добавить рабочий бейдж IzzyOnDroid в README отдельным коммитом
- [ ] Только после фактической публикации закрыть REL-02/REL-03 в
  `.planning/REQUIREMENTS.md`
