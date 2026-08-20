# HANDOFF — миссия tt-touch-slop

Обновлено: 2026-08-20. Ветка: `codex/emoji-telegram` (база `96fc862`).
Состояние миссии: **выполнена**.

Предыдущая миссия — `tt-emoji-telegram`, её handoff вытеснен этим файлом;
результаты целиком в `docs/EMOJI-PANEL-TELEGRAM.md` и `docs/EMOJI-SKIN-TONES.md`.

## Что просили

Закрыть два пункта, оставленные миссией `tt-symbol-edge` в разделе 7
`docs/SYMBOL-KEY-EDGE-FIX.md` («найдено попутно, оператору на решение»). Оператор
2026-08-20 решил взять оба в 1.6.0. Досье:
`~/.supermanager/missions/tt-touch-slop/dossier.md`.

## Что сделано

Полный отчёт — **`docs/TOUCH-SLOP-TUNING.md`** (заканчивается строкой `STATUS: done`).

| Коммит | Что |
|---|---|
| `0dc5e9f` | **пункт 2**: `BogusMoveEventDetector` больше не считает движением пальца хвост предыдущего жеста и скачок координат при смене высоты раскладки; тест краснеет до правки (3 из 4) |
| `5bf6b37` | **пункт 1**: `config_key_hysteresis_distance` 5.0dp → 8.0dp (системный touch slop, как в HeliBoard); тест краснеет на 5.0dp (5 из 8) |
| (этот) | отчёт `docs/TOUCH-SLOP-TUNING.md`, HANDOFF |

Оба пункта закрыты, ни один не отложен.

## Числа, которые понадобятся дальше

- `./gradlew test --rerun-tasks` — **780 тестов, 0 failures**.
- `./gradlew lintVitalRelease` — `BUILD SUCCESSFUL`.
- Release APK: 1 801 490 Б (было 1 801 502 Б на `96fc862`), бюджет 3 145 728 Б.
- Версия **не поднималась**: `app/build.gradle` по-прежнему 1.4.0 / versionCode 6.
  Подъём версии, changelog и сборку релиза делает следующая миссия
  `tt-version-1.6.0`.

## Границы, которые соблюдались

Наружу ничего не уходило (ни push, ни tag, ни деплой). Релизная ветка
`codex/version-1.5.0` не тронута. Keystore не тронут. Закрытые решения прошлых
миссий (`config_sliding_modifier_slop` = 10dp,
`keyHysteresisDistanceForSlidingModifier` = 8dp, нижний ряд, панель эмодзи) не
переоткрывались.

## Что осталось оператору

Раздел 6 отчёта — два наблюдения, в код не внесённые:

1. `values-sw600dp/config.xml` держит `config_key_hysteresis_distance` = 35.0dp
   (значение AOSP для планшетов). Планшетного AVD нет, планшеты не целевой класс.
2. `BogusMoveEventDetector` целиком — кандидат на удаление: хак под планшетные
   тачскрины 2013 года, включается только на `sw768dp`.

## Как воспроизвести измерения

AVD `tatar_e5_test` (1080×2280, 440dpi), запуск headless:

```
~/Android/Sdk/emulator/emulator -avd tatar_e5_test -no-window -no-snapshot-save \
    -no-boot-anim -gpu swiftshader_indirect &
```

Установка debug-сборки поверх более свежей требует `adb install -r -d`
(`./gradlew installDebug` падает с `INSTALL_FAILED_VERSION_DOWNGRADE`), а после
переустановки система сбрасывает текущий IME — вернуть его надо `adb shell ime
set org.tatarkeyboard.ime.debug/rkr.simplekeyboard.inputmethod.latin.LatinIME`
**без** `am force-stop` (force-stop снова сбрасывает выбор).

Поле для ввода — «Try it» в `SetupActivity`; оно показывается только когда оба
шага настройки отмечены. Что напечаталось, читается из `uiautomator dump` по
`resource-id` `setup_test_field`. Геометрия клавиш снимается бинарным поиском по
`adb shell input tap`, пороги — бинарным поиском по длине `adb shell input swipe`.
