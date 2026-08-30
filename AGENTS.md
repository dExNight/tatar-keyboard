# AGENTS.md — руководство для агентных сессий

Проект: офлайн Android-клавиатура с татарской раскладкой (форк Simple Keyboard /
AOSP LatinIME; Java-наследие + новый код на Kotlin). package `org.tatarkeyboard.ime`,
namespace `rkr.simplekeyboard.inputmethod`. Ветка работы — `main`.

## Команды

| Действие | Команда |
|---|---|
| JVM-тесты (976 шт.) | `./gradlew test` (для честного прогона — `--rerun-tasks`) |
| Python-тесты конвейера (181 шт.) | `for f in tests/*/test_*.py; do python3 "$f" \|\| exit 1; done` — pytest НЕ используется, это чистый unittest |
| Линт | `./gradlew lintRelease` (baseline `app/lint-baseline.xml`, `abortOnError=true`) |
| Release APK | `./gradlew assembleRelease` (подпись через `keystore.properties`; без него — unsigned) |
| Гейт «без INTERNET + backup-whitelist» | `bash scripts/check-no-internet.sh [путь-к-apk]` (по умолчанию debug APK; два уровня: манифест + aapt2) |
| Генерация baseline-профиля | `./gradlew :app:generateReleaseBaselineProfile` (нужен запущенный эмулятор) |

Гейты обязательны после любой правки кода/ресурсов: JVM + python + lintRelease +
check-no-internet (исходник и собранный APK) + размер release APK ≤ 3 145 728 Б.

Эмуляторные AVD: `tatar_e5_test`, `tt_prefix3`, `tt_suggest_a14`
(`~/Android/Sdk/emulator/emulator -avd <имя> -no-window &`, adb в
`~/Android/Sdk/platform-tools/`).

## Жёсткие ограничения (нарушать нельзя)

- **Ноль сторонних runtime-зависимостей.** Dev/build-time инструменты (lint,
  detekt, Macrobenchmark) допустимы, в APK попадать не должны.
- **Без `android.permission.INTERNET`** — проверяется гейтом в CI трижды.
- **Без NDK/C++**, без Compose в IME-процессе (Compose допустим только в
  Activity настроек), без сторонних звуков/шрифтов Apple.
- Бюджеты: APK ≤ 3 МБ, холодный старт < 400 мс, ноль аллокаций в цикле отрисовки.
- Локали и раскладки — только tt/ru/en (срезано осознанно в кампании
  реструктуризации; `resConfigs "tt","ru","en"`).

## Ассеты и пины

Словари (`*.tdict.zlib`) и таблицы биграмм (`*.tatbigr.zlib`) собираются
python-конвейером (`scripts/`), а не руками. Их размеры и SHA-256 **запинены**
в `app/src/main/java/.../dictionary/storage/DictionaryStorageContracts.kt` и
`BigramStorageContracts.kt` — любая пересборка ассета требует пересчёта пинов,
иначе красные тесты. Пересборка словаря ДОЛЖНА сопровождаться перепаковкой
обеих таблиц биграмм (исторический источник багов — см. `HANDOFF.md`).

## Дисциплина документов и коммитов

- Историю git и содержимое завершённых отчётов не переписываем; уточнения —
  датированными сносками. Живые документы — наверху `docs/` (индекс:
  `docs/README.md`), архив миссий — `docs/archive/`.
- Актуальное состояние проекта — корневой `HANDOFF.md`; планы работ —
  `docs/RESTRUCTURE-PLAN.md` (выполнен) и `docs/DEV-PLAN.md` (tooling).
- Коммиты по-русски, раздельные по смыслу. Push, теги наружу, публикация —
  только по явной команде оператора.
- `dist/` и `.smgr/` локальны и не коммитятся; `keystore.properties` и
  `tatar-keyboard-release.jks` — секреты, в git им пути нет.

## Структура кода (кратко)

- `latin/LatinIME.java` — InputMethodService, точка входа IME.
- `keyboard/` (Java) — View, PointerTracker, KeyDetector; `latin/suggestions/`,
  `latin/dictionary/**`, `latin/emoji/` (Kotlin) — подсказки, словари, эмодзи.
- `app/src/test` — 976 JVM-тестов (JUnit 4, Robolectric нет — осознанно).
- `scripts/` — python-конвейер ассетов (stdlib only, fail-closed);
  `research/corpus/` — измерительные скрипты и манифесты корпусов (данные OPUS
  не коммитятся — лицензии).
