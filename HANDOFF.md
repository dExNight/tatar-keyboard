# HANDOFF — миссия tt-version-1.6.0

Обновлено: 2026-08-20. Ветка: `codex/version-1.6.0`.
Состояние миссии: **выполнена**.

Предыдущая миссия — `tt-touch-slop`, её handoff вытеснен этим файлом; результаты целиком в
`docs/TOUCH-SLOP-TUNING.md`.

## Что просили

Собрать **1.6.0 / versionCode 8 поверх 1.5.0**: свайп между разделами эмодзи, кожные тона и
правки порога касания. Наружу ничего не публиковать.

## Что сделано

Полный отчёт — **`docs/APK-AUDIT-1.6.0.md`** (заканчивается строкой `STATUS: done`).

| Коммит | Что |
|---|---|
| `a617f57` | merge: ветка `codex/version-1.6.0` = `codex/emoji-telegram` + `codex/version-1.5.0` |
| `16f477b` | версия 1.6.0 / versionCode 8, `CHANGELOG.md`, `metadata/en-US/changelogs/8.txt` |
| (этот) | `docs/APK-AUDIT-1.6.0.md`, ретаргет `docs/PUBLISH-CHECKLIST.md`, HANDOFF |

### Устройство ветки — первый раз в проекте через merge

Релизные артефакты 1.5.0 жили только на `codex/version-1.5.0`, а код 1.6.0 — только на
`codex/emoji-telegram`. Ветка 1.6.0 отведена от второй и вобрала первую:

```
f30589b ─┬─ codex/version-1.5.0:  c53d450 → 4283944 (1.5.0/7) → 2500fb6 (аудит 1.5.0)
         └─ codex/emoji-telegram: 02d594b → 96fc862 (свайп, тона)
                                → 0dc5e9f → 5bf6b37 → 9244e67 (порог касания)
                                       ↓
                 codex/version-1.6.0 = 9244e67 + merge a617f57 → 16f477b (1.6.0/8)
```

Конфликт был один — `HANDOFF.md`; взята версия с `codex/emoji-telegram`, и этот файл всё
равно переписан заново. Запись `[1.5.0]` в `CHANGELOG.md` и `changelogs/7.txt` сохранены
дословно.

### Evidence прогона 2026-08-20 (frozen commit `16f477b`)

| Проверка | Результат |
|---|---|
| `./gradlew clean test lintVitalRelease assembleDebug assembleRelease --rerun-tasks` | `BUILD SUCCESSFUL`, 84 actionable tasks: 84 executed |
| JVM suite | **780 тестов**, 0 failures / 0 errors / 0 skipped, 78 suite-файлов (было 748 / 75) |
| `lintVitalRelease` | `BUILD SUCCESSFUL` |
| no-INTERNET gate | exit 0 на debug и release, оба уровня + backup-whitelist; в исходном манифесте 0 вхождений |
| Permissions | только `VIBRATE` в обоих APK, дамп просмотрен целиком |
| Версия | `versionCode 8` / `1.6.0` в `output-metadata.json` (release и debug) и в badging артефакта |
| Размер release | **1 801 490 Б** из 3 145 728 (запас 42,7 %); против 1 798 198 Б у v1.5.0 — **+3 292 Б** |
| Подпись | v2 only, 1 signer, RSA 4096, `CN=Tatar Keyboard`, cert SHA-256 `98ca6feb…` — ключ не менялся |
| SHA-256 release | `df8da11e9767732d8186423a28cf0249210735012298136eddfdd502280d180a` |
| Шаг 4 | `dist/tatar-keyboard-1.6.0.apk`, `cmp` без различий, verify и badging копии совпали |

### Два отклонения, оба унаследованы и зафиксированы

1. **Целевой бюджет D1 ≤ 1,7 MiB не выполняется** — 1,7180 MiB, перебор 18 911 Б. Из них
   на 1.6.0 приходится 3 292 Б (таблица тонов — 591 Б сжатыми, остальное код и NOTICE), а
   15 619 Б унаследованы от индекса поиска в 1.5.0. Обязательный потолок 3 МБ выполняется.
2. **Подпись не совпадает с историческим релизным сертификатом v1.1.0** — решение
   оператора от 2026-08-18, upgrade path с v1.1.0 порван. Ключ этой миссией не трогался.

### Главный риск релиза

**TalkBack по-прежнему никем не проверен**, и в 1.6.0 дыра шире, чем в 1.5.0: к переделанной
панели и полосам поиска добавился попап кожных тонов, который забирает себе всё дерево
виртуальных узлов. Его первая версия была вообще не видна скринридеру; исправление
подтверждено юнит-тестами, но живым TalkBack его никто не слышал. На эмуляторе
`tatar_e5_test` TalkBack не установлен, окно IME не отдаётся `uiautomator dump`.

Свайп и порог касания эта миссия пальцами тоже не проверяла: эмулятор в прогоне не
поднимался, измерения по ним сделаны своими миссиями и записаны числами в
`docs/TOUCH-SLOP-TUNING.md`.

## Что осталось оператору

Полный список — в конце `docs/APK-AUDIT-1.6.0.md`, восемь пунктов. Коротко и по порядку:

0. **Бэкап keystore** — пятый прогон подряд не сделан, ключ в одном экземпляре.
1. **Решить судьбу 1.5.0.** Эта миссия не знает, выпускался ли Release v1.5.0 — наружу она
   не ходила. Если нет, `versionCode 7` просто не публикуется, пользователь получает сразу
   1.6.0 (пропуск номера Android допускает). Проверяется `git ls-remote --tags origin`.
2. **Проверить на телефоне**: свайп, кожные тона обоими способами выбора, попадание по
   буквам у краёв и — обязательно — TalkBack на попапе тонов.
3. **Решить про целевой бюджет D1** (принять новую планку в `BRIEF.md` либо заказать сжатие
   индекса поиска отдельной миссией, после релиза).
4. Полный diff и release commit; 5. push/CI/merge; 6. tag `v1.6.0` и GitHub Release;
   7. IzzyOnDroid.

## Границы, которые соблюдены

Наружу ничего не публиковалось: ни push, ни tag, ни Release, ни remote-запросов. Keystore и
`keystore.properties` не трогались. Код правок (свайп, тона, порог касания), ассеты и ветка
`codex/version-1.5.0` вместе с её артефактом `dist/tatar-keyboard-1.5.0.apk` не изменялись.
Тесты не ослаблялись. `docs/DICTIONARY-E3-TYPO-REVIEW.tsv` не трогался — `E3a` остаётся
`pending`, её принимает только оператор лично.

## Как воспроизвести артефакт

```sh
git checkout 16f477b
ANDROID_HOME=$HOME/Android/Sdk ./gradlew clean test lintVitalRelease \
    assembleDebug assembleRelease --rerun-tasks --console=plain
ANDROID_HOME=$HOME/Android/Sdk bash scripts/check-no-internet.sh \
    app/build/outputs/apk/release/app-release.apk
```

Release APK побайтово невоспроизводим из-за подписи — SHA-256 будет другим при том же
содержимом. Аудируемый артефакт — `dist/tatar-keyboard-1.6.0.apk`, `df8da11e…`.

Пересборка ассетов (таблица тонов, индекс поиска) описана в `docs/EMOJI-SKIN-TONES.md` и
`docs/EMOJI-PANEL-TELEGRAM.md`; входные файлы Unicode и CLDR в репозиторий не кладутся и
лежат локально в `~/.local/share/tatar-keyboard-inputs/`.
