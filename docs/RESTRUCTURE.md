# RESTRUCTURE — журнал кампании аудита, реструктуризации, очистки и оптимизации

Кампания по плану `docs/RESTRUCTURE-PLAN.md` (утверждён оператором 2026-08-30).
База: ветка `codex/version-1.6.0`, HEAD `30387472`, версия 1.9.4 (versionCode 20).

## Фаза 0. Подготовка и сеть безопасности

Сделано:

- Страховочная ветка `backup/pre-restructure` → `30387472` (локально).
- `.gitignore`: добавлены `*.aab` и `.pytest_cache/` (ранее покрывался только
  автогенерированным `.gitignore` внутри каталога — хрупко); зафиксировано
  датированное уточнение политики по `docs/*.generated.json`: три JSON в
  `docs/lang-priority/` закоммичены осознанно как пины-свидетельства (паттерн
  верхнего уровня их никогда не покрывал; три верхнеуровневых JSON
  `DICTIONARY-E5A`/`RUSSIAN-BIGRAMS.*` на диске игнорируются корректно).
- План кампании зафиксирован в `docs/RESTRUCTURE-PLAN.md`.

### Замеры «до» (базовая линия)

| Метрика | Значение | Источник |
|---|---|---|
| Release APK (1.9.4, подписан) | 2 538 949 Б (запас до 3 МиБ 19,29 %) | `dist/tatar-keyboard-1.9.4.apk` |
| `classes.dex` (несжатый, внутри APK) | 406 768 Б | `unzip -l` |
| `resources.arsc` | 309 272 Б | `unzip -l` |
| Файлов в APK | 548 | `unzip -l` |
| JVM-тесты (`./gradlew test`, --rerun-tasks) | 974 / 0 failures / 0 errors | `app/build/test-results/testDebugUnitTest/` |
| Python-тесты (7 файлов, `unittest`) | 181 / 0 failures | прогон 2026-08-30 |
| Холодный старт, медиана (эмулятор) | 126,3 мс при инварианте 400 мс | `docs/APK-AUDIT-1.9.4.md` (перемер — в фазе 4; adb-устройств сейчас нет, AVD в наличии: `tatar_e5_test`, `tt_prefix3`, `tt_suggest_a14`) |
| git pack | 29,3 МиБ, 17 502 объекта, garbage 0 | `git count-objects -vH` |

### Примечание по инструментарию

`pytest` в системе не установлен; тесты `tests/` — классы `unittest.TestCase`,
запускаются напрямую: `python3 tests/<каталог>/test_<имя>.py` (все 7 файлов
зелёные). Установка pytest не требуется.

## Фаза 1. Аудит

Дата: 2026-08-30. Код и ресурсы не изменялись — только чтение, сборки, линт.

### Инструментальный прогон

- `./gradlew lintRelease` — **FAILED by design**: 212 errors, 102 warnings
  (отчёт: `app/build/intermediates/lint_intermediate_text_report/release/lintReportRelease/lint-results-release.txt`,
  1252 строки). Lint сейчас красный и ломает сборку — baseline запланирован в фазе 4.
  Ошибки: **MissingTranslation 155**, **StringFormatMatches 39**, **ResourceType 18**.
  Варнинги: **UnusedResources 70**, IconLauncherShape 5, DiscouragedApi 4,
  RedundantLabel 3, InlinedApi 3, Overdraw 2, LabelFor 2, ClickableViewAccessibility 2,
  Autofill 2, по 1: UseRequiresApi, UsableSpace, UnusedAttribute, TextFields,
  StaticFieldLeak, InflateParams, CustomViewStyleable, ApplySharedPref,
  AndroidGradlePluginVersion.
  Примеры: `res/values/strings-a11y.xml:25` (`spoken_letter_04d9` не переведён в 70
  локалей — исчезнет при срезе локалей до tt/ru/en); `settings/SettingsHostActivity.kt:1207`
  (`abbreviation_unit_milliseconds`: `%s` против int — повторяется по всем локалям);
  `latin/emoji/EmojiPanelView.kt:366–370` (ResourceType: `getColor` по числовому
  индексу стиля); `res/drawable/btn_keyboard_key.xml:17` (UnusedResources — LXX-кластер).
- `./gradlew assembleRelease` — **OK**, подписан; APK 2 538 949 Б — **побайтно равен
  базовой линии** фазы 0 (сборка воспроизводима).

### R8-артефакты (app/build/outputs/mapping/release/)

- `usage.txt`: 1620 классов полностью удалено (из них 137 app-классов — в основном
  R$-классы после инлайнинга полей, Kotlin-companion/synthetic-обёртки и интерфейсы-
  пустышки). Топ пакетов: kotlin 111, kotlin.collections 98, androidx.core.app 96,
  androidx.core.view 84, kotlin.jvm.internal 78, kotlin.sequences 66, androidx.annotation 58,
  kotlin.text 51, kotlin.reflect 50, kotlin.time 48. Реально мёртвый прикладной код,
  уже выкидываемый из release: `latin.define.DebugFlags`,
  `latin.utils.KotlinInteropCheck`, `keyboard.internal.DrawingProxy`,
  `latin.common.CoordinateUtils`, `latin.utils.LanguageOnSpacebarUtils`,
  `latin.utils.ViewLayoutUtils` и др. (`usage.txt` размеров не содержит — «крупность»
  оценена по пакетам).
- `resources.txt`: 287 ресурсов помечены прямыми ссылками из dex, 858 — root-reachable,
  308 — не-корневые (живут транзитивно). Ключевая строка:
  `android.content.res.Resources#getIdentifier present: true` → включён консервативный
  режим: **все 24 LXX-стиля сохраняются в APK по совпадению со строковыми константами**
  («used because it matches string pool constant Keyboard/Key/MainKeyboardView»).
  Подтверждено aapt2-дампом APK: `style/KeyboardTheme.LXX_*` и lxx-цвета физически
  присутствуют в arsc, несмотря на ноль ссылок из кода.
- Фактически шринкер сегодня выкидывает из drawable только `ic_add_circle` и
  `ic_delete` (сверено: `aapt2 dump resources` против списка `res/drawable/`).
- APK содержит arsc-конфигурации всех 75 локалей; 1107 resource-entries.

### Карта getIdentifier (блокеры точного шринка и риск при resConfigs)

| Место | Что резолвит по имени |
|---|---|
| `keyboard/KeyboardLayoutSet.java:281` | xml раскладки по имени из сабтипа (`keyboard_layout_set_*`) |
| `keyboard/internal/KeyboardTextsSet.java:134` | строки по имени (`!string/...` в keySpec) |
| `latin/utils/LocaleResourceUtils.java:81` | `subtype_locale_displayed_in_root_locale_*` |
| `latin/utils/LocaleResourceUtils.java:89` | `subtype_locale_displayed_*` |

При срезе локалей (фаза 3) обязателен эмуляторный прогон tt→ru→en: эти четыре точки
могут прятать ссылки, невидимые для grep.

### Таблица находок (подтверждено командой → решение → риск)

| # | Находка | Доказательство | Решение | Риск |
|---|---|---|---|---|
| a | LXX-кластер мёртв, **кроме ресурсов с «lxx» в имени, используемых живой темой** | 6 файлов `values/themes-lxx-*.xml` (30 стилей) — ссылки на них только внутри кластера (`grep -rn LXX app/src/main/java|xml` → лишь комментарий в KeyboardTheme.java:44); 21 lxx-цвет в `values/colors.xml:27–52` + 8 записей в каждом из `values-night/colors.xml`, `values-night-v31/colors.xml`, `values-v31/colors.xml` — ссылок вне кластера нет; 10 drawable `btn_keyboard_*`, `keyboard_key_feedback_background`, `keyboard_popup_panel_background` — ссылки только из themes-lxx и друг от друга (совпадения в `ios_*`-drawable — только в комментариях); `dimen/button_corner_radius_lxx` — только внутри кластера. `KeyboardTheme.java:48–50` — единственная живая тема Tatar | Удалить кластер | Низкий: стили мёртвы, но lint UnusedResources и resources.txt подтверждают |
| a′ | **Опровержение части пред-аудита**: `anim/key_preview_dismiss_lxx`, `dimen config_key_preview_{height,width,offset}_lxx`, 6 фракций `config_key_*_ratio_lxx` — **ЖИВЫЕ**: `anim`+`dimen` ссылаются из `values/themes-tatar.xml:63–66` (MainKeyboardView.Tatar), фракции — из `values/themes-common.xml:60–66` | `grep` по имени → единственные ссылки из живых стилей | **Оставить** (при желании — переименовать позднее, отдельной правкой) | Удаление сломало бы превью клавиш и метрики |
| b | `drawable/ic_add_circle.xml`, `drawable/ic_delete.xml` — ноль ссылок | `grep -rn 'ic_add_circle\|ic_delete' app/src` → 0; оба уже выкинуты шринкером из release APK (aapt2: отсутствуют) | Удалить | Нулевой |
| c | **Опровергнуто**: `values/strings-action-keys.xml` НЕ пуст — 2 строки `label_pause_key`, `label_wait_key`; используются в `xml/rows_phone_symbols.xml:53,72`, `xml-sw600dp/rows_phone.xml:40,69`, `KeyboardTextsTable.java:177–178,342–343` | `grep -rn label_pause_key` | **Оставить** | — |
| d | `english_ime_settings` (`strings-appname.xml:23`) — ноль ссылок | `grep -rn english_ime_settings app/src` → только определение | Удалить | Нулевой |
| e | `KotlinInteropCheck.kt` + вызов `LatinIME.java:363` — мёртвы (`log()` внутри `if (DebugFlags.DEBUG_ENABLED)`, константа `false`) | оба класса в `usage.txt` — R8 уже выкидывает из release | Удалить (гигиена исходников) | Нулевой |
| f | `DebugFlags.java` — пустышка (`DEBUG_ENABLED=false`, пустой `init()`); точки использования: `LatinIME.java:365,1368,1373`, `KotlinInteropCheck.kt:9`, `PointerTracker.java:43` | чтение файла + grep; класс в `usage.txt` | Удалить вместе с точками | Нулевой |
| g | `PersonalDictionaryReader.kt` — из main используется **никем**, только из `app/src/test` (4 тестовых файла) | grep по `app/src`; **нюанс: класс помечен `@Keep`** (`PersonalDictionaryReader.kt:31`), поэтому попадает в release APK (есть в `seeds.txt` и dex) | Перенести в test-sourceset, снять `@Keep` | Низкий (минус мёртвый код из APK) |
| h | `KeyboardTheme.THEME_ID_*` (1–6) — ноль чтений вне `KeyboardTheme.java`; префы читаются только через `KEYBOARD_THEME_KEY` (`getKeyboardTheme`, строки 111–128), неизвестные id → сброс на default | `grep -rn 'THEME_ID_' app/src` вне KeyboardTheme.java → 0; `grep pref_keyboard_theme` → только KeyboardTheme.java | Оставить константы как документацию занятого диапазона id (комментарий уже есть, строки 44–46) — либо удалить, сохранив комментарий; решение фазе 3 | Нулевой в обоих вариантах |
| i | `allowBackup="false"` + `fullBackupContent`/`dataExtractionRules` в `AndroidManifest.xml:22–25`; `xml/backup_rules.xml`, `xml/data_extraction_rules.xml` — whitelist-заглушки «закрыть всё» | чтение файлов; при `allowBackup=false` бэкап отключён целиком, правила не исполняются | Удалить атрибуты и оба файла | Нулевой функционально; единственный эффект — семантика «закрыто по умолчанию» для API 31+ D2D, что эквивалентно allowBackup=false |
| j | 75 локальных `res/values-XX/` (кроме base/ru/tt и 8 квалификаторных: land, night, night-v31, sw600dp, sw600dp-land, sw768dp, sw768dp-land, v31), суммарно 668 КБ исходников против 64 КБ ru+tt; строковый payload локалей ≈ 72,8 % всех строк (50 492 симв. из 69 362) → оценочная верхняя доля в `resources.arsc` (309 272 Б) — до ~200 КБ | `du -sch`, подсчёт символов в `<string>` скриптом; `SubtypeLocaleUtils.java` — 82 уникальных локальных токена (~70 сабтипов), `KeyboardTextsTable.java` — 234 812 Б, 72 массива локалей (DEFAULT + 71) | Срез до tt/ru/en (решение оператора, фаза 3) | Самая рискованная правка — см. карту getIdentifier выше |

### Аудит сборки

- `build.gradle` (root): AGP 9.2.1, репозитории mavenCentral/google. `app/build.gradle`:
  `minifyEnabled true`, `shrinkResources true`,
  `proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'`.
- `gradle.properties`: **`android.enableR8.fullMode=false` отсутствует** (full mode не
  отключён); заданы `android.r8.optimizedResourceShrinking=false` (легаси-шринкер
  ресурсов — кандидат на пересмотр в фазе 4) и `android.r8.strictFullModeForKeepRules=false`.
- **`getDefaultProguardFile("proguard-android.txt")` не используется** — стоит
  `proguard-android-optimize.txt` (совместимо с AGP 9.x).
- `proguard-rules.pro`: единственное реальное правило — `-keep class
  rkr.simplekeyboard.inputmethod.R` (строка 19), остальное — шаблонные комментарии
  (включая устаревший путь `/home/rkr/Android/Sdk/...`). Избыточность keep проверять в
  фазе 3 полной пересборкой.
- Подпись: `keystore.properties` на месте, release подписывается;
  `tatar-keyboard-release.jks` лежит в корне репо (не трекнут) — рекомендация оператору
  вынести из каталога проекта (зафиксировано в плане, фаза 2).

### Карта docs/ (66 MD верхнего уровня + 18 каталогов)

Живые (остаются наверху): `PUBLISH-CHECKLIST.md` (устарел — описывает 1.8.4, перецелить
на 1.9.5 в фазе 5), `CLEANUP.md`, `RESTRUCTURE.md`, `RESTRUCTURE-PLAN.md`.

Архив (`docs/archive/`, фаза 2, `git mv`):

- **apk-audits** (17): `APK-AUDIT-1.3.0…1.9.4` (16 шт.) + `APK-AUDIT-2026-08-18.md`.
- **dictionary** (15): `CORPUS.md`, `CORPUS-OS.md`, `CORPUS-TATAR-PERMISSION-DRAFT.md`,
  `DICT-ACCEPT.md`, `DICT-WIDEN.md`, `DICTIONARY-D0.md`, `DICTIONARY-D1A…D1E.md` (5),
  `DICTIONARY-D3.md`, `DICTIONARY-E4.md` (личный словарь), `RUSSIAN-DICTIONARY.md`,
  `REVIEW-BATCHES.md`.
- **bigrams** (8): `DICTIONARY-E5A…E5D.md` (4), `BIGRAM-ADJACENCY.md`,
  `IMPERATIVE-HEADS.md`, `LANG-PRIORITY.md`, `RUSSIAN-BIGRAMS.md`.
- **emoji** (6): `DICTIONARY-E2.md` (emoji data asset), `EMOJI-PANEL-REDESIGN.md`,
  `EMOJI-PANEL-TELEGRAM.md`, `EMOJI-SEARCH-FIXES.md`, `EMOJI-SKIN-TONES.md`,
  `SMALL-FIXES.md`.
- **ux** (7): `IOS-REDESIGN.md`, `LAYOUT-ERGONOMICS.md`, `FINAL-POLISH.md`,
  `UX-POLISH.md`, `FIXES-1.0.1.md`, `SYMBOL-KEY-EDGE-FIX.md`, `TOUCH-SLOP-TUNING.md`.
- **missions** (9): `MILESTONE-v1.1.md`, `MILESTONE-v2.md`, `PERSONAL-DICT-FIX.md`,
  `QUARANTINE.md`, `SILENT-AUDIT.md`, `SUGGEST-DIES.md`, `PREFIX3-BUG.md`,
  `TAP-REPRO.md`, `DICTIONARY-E3.md` (typo set и инструментальный harness).

Сумма: 4 живых + 17+15+8+6+7+9 = 66 (в плане фигурировало 64 — журнал и план кампании
добавлены после его составления). Финальное распределение фиксируется в
`docs/README.md` фазы 2.

### Ссылки и дубли в документации

- Markdown-ссылки `](...)`: всего 4 во всех 75 md (docs + корень), битых 0.
- Код-спаны `docs/...`: 738 проверено, 30 не существуют; из них реально битая ссылка
  одна — **`docs/DICTIONARY-E1.md`** (файл отсутствует; 6 упоминаний в `PROPOSALS.md`,
  по одному в `docs/DICTIONARY-E5C.md` и `docs/MILESTONE-v2.md`). Остальные 23 —
  glob-паттерны (`docs/DICTIONARY-*-REVIEW.tsv`, `docs/*.generated.json`,
  `{ru,tt}`-шаблоны) и ссылки на будущие файлы из самого плана (`docs/archive/`,
  `docs/README.md`, `docs/APK-AUDIT-1.9.5.md`) — не битые по смыслу.
- Дубли по sha256 (452 файла в docs/): 5 пар — `imperative-heads/evidence/coldstart.sh`
  = `suggest-dies/evidence/coldstart.sh`; `bigram-adjacency/tt-k4-…png` =
  `tt-k6-release-…png`; `review-batches/marks-ru.tsv` = `marks-tt.tsv`;
  `symbol-key-edge/02-…png` = `03-…png`; `apk-audit-1.8.1/181-band-back-…png` =
  `181-band-filled.png`. Дедупликация — опционально в фазе 2 (история не переписывается).

### Реестр устаревших документов

- `.planning/STATE.md` — GSD-state, `milestone: v1.0`, mtime 2026-08-17 (в плане ошибочно
  «19.07.2026»); пометить устаревшим датированной сноской в фазе 2.
- `PUBLISH-CHECKLIST.md` — перецелен на v1.8.4 при версии 1.9.4 (фаза 5).
- `metadata/en-US/changelogs/` — есть `2.txt…18.txt`, **нет `19.txt` и `20.txt`**
  (блокер публикации 1.9.3/1.9.4, фаза 2).
- `docs/DICTIONARY-E1.md` — отсутствует, но упоминается в трёх документах (выше).
- `PROPOSALS.md` (693 КБ, корень) — заморозить в `docs/archive/` (фаза 2).

### ГОТОВО

Все находки подтверждены командами/файлами выше; код и ресурсы не изменялись
(`git status`: только `docs/RESTRUCTURE.md` и неотслеживаемые артефакты `build/`).

## Фаза 2. Реструктуризация

Дата: 2026-08-30. Код приложения (`app/`) не трогался.

### Что куда переехало (все переносы — `git mv`, история сохранена)

Наверху `docs/` остались живые: `PUBLISH-CHECKLIST.md`, `CLEANUP.md`,
`RESTRUCTURE.md`, `RESTRUCTURE-PLAN.md`, новый индекс `README.md` и каталог
свидетельств живого CLEANUP — `cleanup/`. Всё остальное ушло в `docs/archive/`
(62 отчёта + `PROPOSALS.md`, 16 каталогов свидетельств, 6 TSV-очередей,
всего 443 файла на диске, из них 440 трекнутых):

| Категория | Отчётов (md) | Каталоги свидетельств | Прочее |
|---|---|---|---|
| `archive/apk-audits/` | 17 (APK-AUDIT-1.3.0…1.9.4 + 2026-08-18) | 3 (apk-audit-1.7.0/1.8.0/1.8.1) | — |
| `archive/dictionary/` | 15 (D0, D1A–D1E, D3, E4, CORPUS×3, DICT-ACCEPT, DICT-WIDEN, REVIEW-BATCHES, RUSSIAN-DICTIONARY) | 4 (dict-accept, dict-widen, russian-dictionary, review-batches) | 5 TSV (очереди и review-таблицы) |
| `archive/bigrams/` | 8 (E5A–E5D, BIGRAM-ADJACENCY, IMPERATIVE-HEADS, LANG-PRIORITY, RUSSIAN-BIGRAMS) | 4 (bigram-adjacency, imperative-heads, lang-priority, russian-bigrams) | 3 `*.generated.json` (не трекнуты, перенесены `mv`; `.gitignore` дополнен `docs/archive/bigrams/*.generated.json`) |
| `archive/emoji/` | 6 (E2, EMOJI-PANEL-REDESIGN/TELEGRAM, EMOJI-SEARCH-FIXES, EMOJI-SKIN-TONES, SMALL-FIXES) | 1 (emoji-panel) | — |
| `archive/ux/` | 7 (IOS-REDESIGN, LAYOUT-ERGONOMICS, FINAL-POLISH, UX-POLISH, FIXES-1.0.1, SYMBOL-KEY-EDGE-FIX, TOUCH-SLOP-TUNING) | 3 (final-polish, layout-ergonomics, symbol-key-edge) | — |
| `archive/missions/` | 9 (MILESTONE-v1.1/v2, PERSONAL-DICT-FIX, QUARANTINE, SILENT-AUDIT, SUGGEST-DIES, PREFIX3-BUG, TAP-REPRO, DICTIONARY-E3) | 2 (prefix3, suggest-dies) | 1 TSV (E3-TYPO-REVIEW) |
| `archive/PROPOSALS.md` | 1 | — | заморожен пометкой «Заморожен 2026-08-30: архив продуктовых решений миссий D0–E5; живой статус — в HANDOFF.md» — единственная правка содержимого |

### Проверка ссылок

Скрипт (python3, в репозиторий не коммитился): markdown-ссылки `](path)` —
во всех 76 md (docs/** + корень), код-спаны `` `docs/...` `` — только в живых
документах. До правок: 5 markdown-ссылок (0 битых) и 116 битых код-спанов в
живых документах. Починено **112 замен** в `BRIEF.md` (2), `HANDOFF.md` (8),
`CHANGELOG.md` (9), `docs/PUBLISH-CHECKLIST.md` (88), `docs/CLEANUP.md` (4),
`.claude/CLAUDE.md` (1) — механической заменой по явному отображению
старый путь → новый путь; содержание записей не менялось.

После правок битых 0; остаточные 10 срабатываний классифицированы как
не-ссылки: шаблон `docs/<ФАЗА>.md` и литерал `](...)` в тексте, ссылки на
будущие файлы самого плана (`docs/APK-AUDIT-1.9.5.md`), историческая запись
аудита в разделе «Фаза 1» этого файла (в т.ч. несуществующий
`docs/DICTIONARY-E1.md` — предмет находки). Архивные отчёты не редактировались:
их код-спаны `docs/...` — историческая запись.

Каскадная правка за пределами документации: тесты-пины путей —
`tests/dictionary_pack/test_dictionary_pack.py:34–39` и
`tests/emoji_pack/test_emoji_pack.py:21` читают отчёты как provenance-файлы;
константы путей обновлены на новые места (без этого 3 теста падали с
FileNotFoundError). Скрипты завершённых миссий (`scripts/dict_accept.py`,
`scripts/review_batches.py`) содержат старые пути как входные константы —
не тронуты (гейты их не исполняют; повторный запуск миссионных инструментов —
отдельное решение).

### Прочие пункты фазы

- `.planning/STATE.md` — датированная пометка «Устарел 2026-08-30: GSD-фазы
  закрыты на v1.0, дальше проект ведётся миссиями `.smgr/`», содержимое не
  переписано.
- `metadata/en-US/changelogs/19.txt` (1.9.3) и `20.txt` (1.9.4) — созданы по
  CHANGELOG.md, 335 и 320 символов (< 500). Отклонение от образца: 17.txt/18.txt
  англоязычные, новые написаны по-русски по прямому указанию оператора.
- `docs/README.md` — индекс: живые наверху, архив по категориям, одна строка
  на отчёт (имя — тема — версия).

### Git-операции

- `git checkout main && git merge --no-ff codex/version-1.6.0` → merge-коммит
  `11de3892` «merge: вся работа миссий 1.3.0–1.9.4 + кампания реструктуризации
  в main». main был строго позади на 127 коммитов (0 своих впереди), конфликтов
  не было.
- Локальный аннотированный тег `v1.9.4` на `30387472` («v1.9.4 (versionCode 20)»).
- Удалены локальные ветки, полностью слитые в main (только `-d`):
  `codex/apk-audit-2026-08-18`, `codex/e5-bigram-prediction`,
  `codex/emoji-panel-redesign`, `codex/emoji-telegram`, `codex/version-1.3.0`,
  `codex/version-1.4.0`, `codex/version-1.5.0`, `codex/version-1.6.0`.
  `backup/pre-restructure` оставлена. Локальные ветки после чистки:
  `backup/pre-restructure`, `main`.
- `git remote prune origin` — **не выполнен**: сеть до origin недоступна
  (SSH banner timeout до github.com, `fatal: Could not read from remote
  repository`), prune требует соединения. Отложено оператору — команда
  локально-безопасна, выполнить при появлении сети.
- Наружу не ушло ничего: push не делался, remote-ветки не удалялись.

### Гейты (после всех правок, на main)

- `python3 tests/*/test_*.py` (7 файлов): **181 тест, все OK** (1 skip в
  emoji_pack — предсуществующий, ждёт внешний фикстурный файл).
- `./gradlew test`: **BUILD SUCCESSFUL**; последний полный прогон — 974 теста,
  0 failures / 0 errors (код со времени прогона не менялся — правки фазы 2
  касаются только docs/, metadata/, tests/-пинов путей).
- `bash scripts/check-no-internet.sh` — два режима: debug APK (по умолчанию) и
  `dist/tatar-keyboard-1.9.4.apk` — оба зелёные: INTERNET нет ни в исходном
  манифесте, ни в собранном APK; backup закрыт whitelist'ом.

### Рекомендация оператору (действие оператора, не выполнялось)

- Вынести `tatar-keyboard-release.jks` из корня репозитория за пределы каталога
  проекта (файл не трекнут, но лежит рядом с `.gitignore`д-каталогами — риск
  случайной утечки при ручных операциях с архивами/скриншотами).
