# RESTRUCTURE — журнал кампании аудита, реструктуризации, очистки и оптимизации

Кампания по плану `docs/RESTRUCTURE-PLAN.md` (утверждён оператором 2026-08-30).
База: ветка `version-1.6.0`, HEAD `30387472`, версия 1.9.4 (versionCode 20).

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

- `STATE.md` из устаревшего каталога планирования (удалён 2026-09-03) —
  `milestone: v1.0`, mtime 2026-08-17 (в плане ошибочно
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
устаревший конфиг инструментов (1; удалён 2026-09-03) — механической заменой по явному отображению
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

- `STATE.md` из устаревшего каталога планирования — датированная пометка
  «Устарел 2026-08-30: фазы v1.0 закрыты, дальше проект ведётся миссиями»,
  содержимое не переписано; сам каталог удалён 2026-09-03 как устаревший.
- `metadata/en-US/changelogs/19.txt` (1.9.3) и `20.txt` (1.9.4) — созданы по
  CHANGELOG.md, 335 и 320 символов (< 500). Отклонение от образца: 17.txt/18.txt
  англоязычные, новые написаны по-русски по прямому указанию оператора.
- `docs/README.md` — индекс: живые наверху, архив по категориям, одна строка
  на отчёт (имя — тема — версия).

### Git-операции

- `git checkout main && git merge --no-ff version-1.6.0` → merge-коммит
  `11de3892` «merge: вся работа миссий 1.3.0–1.9.4 + кампания реструктуризации
  в main». main был строго позади на 127 коммитов (0 своих впереди), конфликтов
  не было.
- Локальный аннотированный тег `v1.9.4` на `30387472` («v1.9.4 (versionCode 20)»).
- Удалены локальные ветки, полностью слитые в main (только `-d`):
  `apk-audit-2026-08-18`, `e5-bigram-prediction`,
  `emoji-panel-redesign`, `emoji-telegram`, `version-1.3.0`,
  `version-1.4.0`, `version-1.5.0`, `version-1.6.0`.
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

## Фаза 3а. Очистка мёртвого кода и ресурсов

Дата: 2026-08-30. Ветка `main`, без push. Каждое логическое удаление — отдельным
коммитом; гейты (`./gradlew test`, `./gradlew assembleRelease`,
`scripts/check-no-internet.sh` на свежем release APK) прогонялись перед каждым
коммитом. Срез локалей (tt/ru/en) — отдельная фаза 3б, здесь не трогался.

### Удалено → доказательство → эффект

| Удалено | Доказательство (повторный grep перед удалением) | Эффект |
|---|---|---|
| 6 файлов `values/themes-lxx-*.xml` (30 стилей) | `grep -rn LXX app/src/main` → вне кластера только комментарий в `KeyboardTheme.java:44` и enum-строки в `attrs.xml` | −6 файлов; LXX-стили исчезли из arsc (aapt2: `KeyboardTheme.LXX_*` нет) |
| 40 lxx-цветов: 24 в `values/colors.xml` + 8 в `values-night/colors.xml` + файлы `values-v31/colors.xml` и `values-night-v31/colors.xml` целиком (содержали только lxx) | grep по каждому из 24 имён по всему `app/src` → ссылки только из themes-lxx и друг от друга | −4 файла-носителя, цвета вымыты из arsc |
| 10 drawable: `btn_keyboard_*` (8), `keyboard_key_feedback_background`, `keyboard_popup_panel_background` | grep по каждому имени → вне themes-lxx только совпадения в комментариях ios_*-drawable | −10 файлов |
| `dimen/button_corner_radius_lxx` | grep → ссылки только из удаляемых drawable кластера | — |
| `drawable/ic_add_circle.xml`, `drawable/ic_delete.xml` | grep → 0 ссылок; R8 их уже выкидывал (фаза 1) | гигиена исходников |
| string `english_ime_settings` | grep → только определение в `strings-appname.xml` | — |
| `KotlinInteropCheck.kt` + вызов и импорт в `LatinIME.java` | `log()` целиком за `if (DebugFlags.DEBUG_ENABLED=false)`; класс в `usage.txt` | мёртвый код убран из исходников |
| `DebugFlags.java` + 5 точек использования (`LatinIME` ×3, `PointerTracker` ×2) | константа `DEBUG_ENABLED=false`: ветка `throw NPE` никогда не выполнялась, debug-логирование мёртво; `PointerTracker.DEBUG_MODE = DEBUG_EVENT` — семантика не изменилась | мёртвый код убран |
| `PersonalDictionaryReader.kt` → перенесён из main в `app/src/test` (тот же пакет), снят `@Keep` | grep: из main не используется никем, только 4 тестовых файла; `@Keep` удерживал класс в release APK (seeds.txt) | класс исчез из dex (проверено: 0 вхождений в classes.dex и seeds.txt) |
| `android:fullBackupContent` + `res/xml/backup_rules.xml` | при `allowBackup="false"` Auto Backup не исполняется никогда → legacy-редакция мертва | −1 файл, −1 атрибут манифеста |
| `proguard-rules.pro`: шаблонные комментарии + `-keep class rkr.simplekeyboard.inputmethod.R` | рефлексии на R нет; проверено полной clean release-сборкой + всеми тестами | файл сокращён до осмысленной шапки |

НЕ тронуто (подтверждено живым): `anim/key_preview_dismiss_lxx.xml`,
`dimen config_key_preview_*_lxx`, 6 фракций `config_key_*_ratio_lxx` (используются
темой Tatar — присутствуют в arsc, это ожидаемо), `values/strings-action-keys.xml`,
enum-строки LXX в `attrs.xml` и константы `THEME_ID_*` (находка h: документация
занятого диапазона id).

### Отклонения от задания

1. **`data_extraction_rules.xml` СОХРАНЁН** — находка (i) аудита в этой части
   опровергнута по факту: на API 31+ `allowBackup="false"` закрывает только
   облачный бэкап, но НЕ device-to-device перенос; D2D закрывает именно секция
   `<device-transfer>` (задокументировано в самом файле и в E2b-3,
   `docs/archive/emoji/DICTIONARY-E2.md:235–238`). Удаление silently открыло бы
   перенос пользовательских словарей на новое устройство. Удалена только
   legacy-редакция `fullBackupContent`/`backup_rules.xml`.
2. **Каскад правок**: `scripts/check-no-internet.sh` адаптирован (уровень 2:
   `allowBackup=false` + наличие dataExtractionRules с whitelist-проверкой обеих
   секций + отсутствие fullBackupContent считается ошибкой; заодно починен
   `set -e`/`pipefail`-выход на отсутствующем атрибуте);
   `BackupWhitelistSourceContractTest` — проверки legacy-редакции заменены на
   ассерт её отсутствия (−1 тест: 973); KDoc `SettingsHostActivity` обновлён.
3. **Пункты 4 и 5 задания (KotlinInteropCheck + DebugFlags) — одним коммитом**:
   удаление DebugFlags зависит от удаления KotlinInteropCheck (его единственный
   клиент); правки в `LatinIME.java` пересекаются построчно.
4. **Предсуществующая поломка, найденная гейтом**: 5 JVM-тестов падали ещё до
   правок фазы 3а — фаза 2 перенесла `docs/DICTIONARY-D1A-QUERY-REVIEW.tsv` в
   `docs/archive/dictionary/`, но обновила только python-пины. Починено отдельным
   коммитом `b260da09` (3 файла тестов).

### Замеры

| Метрика | До (базовая линия) | После фазы 3а |
|---|---|---|
| Release APK (подписан) | 2 538 949 Б | **2 526 324 Б** (−12 625 Б, −0,50 %) |
| Файлов в APK | 548 | 537 |
| JVM-тесты | 974 / 0 failures | 973 / 0 failures (−1 вместе с legacy backup-редакцией) |
| `lintRelease` | 212 errors / 102 warnings | 212 errors / **36 warnings** |

Lint: errors без изменений (MissingTranslation 155 — уйдут в фазе 3б;
StringFormatMatches 39, ResourceType 18). Warnings: UnusedResources 70 → **2**;
новые 2 предупреждения — `DataExtractionRules` (шаблонный совет вернуть
fullBackupContent; осознанно не применён — см. отклонение 1) и `GradleDependency`
(customview 1.1.0 → 1.2.0; не связан с правками, решение — фаза 4).

Проверка артефакта: `unzip -l` — 0 совпадений по `lxx|btn_keyboard|backup_rules|
ic_add_circle|ic_delete|PersonalDictionaryReader`; `aapt2 dump resources` —
LXX-стилей и lxx-цветов в arsc нет, живые `*_lxx`-ресурсы темы Tatar на месте;
`PersonalDictionaryReader` отсутствует в classes.dex и seeds.txt.

### ГОТОВО

Все гейты зелёные на каждом шаге, 6 содержательных коммитов + 1 фикс пинов,
`git status` чист. Наружу не ушло ничего.

## Фаза 3б. Срез локалей

Дата: 2026-08-30. Ветка `main`, без push. Решение оператора: локали приложения
и список раскладок срезаются до tt/ru/en. Гейты прогонялись перед каждым
коммитом; эмуляторный прогон — на AVD `tt_suggest_a14` (Android 14).

### Цепочка сабтипов до среза (разведка)

- `SubtypeLocaleUtils.java` — единственный реестр: 82 локальные константы,
  `sSupportedLocales` (73 записи) и `SubtypeBuilder.getSubtypes()` (switch,
  ~40 веток). Потребители: `SettingsHostActivity.buildLanguagesScreen`
  (список «Add language»), `RichInputMethodManager` (дефолт fresh-install
  tt+ru+en), `SubtypePreferenceUtils` (миграция префов, east_slavic→russian).
- `KeyboardTextsTable.java` — сгенерированная таблица moreKeys: 70 массивов
  `TEXTS_<locale>` + регистрация в `LOCALES_AND_TEXTS`; lookup с fallback
  language→DEFAULT. Татарской таблицы нет — tt всегда жил на DEFAULT +
  инлайн-moreKeys из `rows_tatar.xml`.
- Имена локалей: `locale_exception_keys`/`locale_displayed_in_root_locale`
  (donottranslate.xml) → getIdentifier в `LocaleResourceUtils`;
  `locale_name_*` в strings.xml.
- Layout-XML резолвятся по имени (`KeyboardLayoutSet.getXmlId`,
  getIdentifier) → `res/raw/keep.xml` не тронут.

### Что удалено (числа)

| Слой | Было | Стало |
|---|---|---|
| `SubtypeLocaleUtils`: локали в `sSupportedLocales` | 73 | 3 (tt_RU, ru, en_US) |
| LOCALE_*-константы / case-ветки switch | 82 / ~40 | 3 / 3 |
| LAYOUT_*-константы | 44 | 24 (3 активных + `east_slavic` для миграции префов + 20, на которые ещё ссылается switch в `InputLogic` — мёртвые ветки оставлены осознанно) |
| `KeyboardTextsTable.java` | 234 812 Б, 70 массивов | 31 208 Б, 3 массива (DEFAULT, en, ru); файл сгенерированный — **правлен руками**, помечено комментарием |
| Layout-XML (keyboard_layout_set_/kbd_/rows_/rowkeys_/row_/key_/keystyle_) | 368 basename'ов в res/xml{,-land,-sw600dp,-sw600dp-land} | 119; удалено **287 файлов** (1,4 МБ исходников) |
| `res/values-XX/` локали | 84 каталога | 9: values, values-ru, values-tt + land/night/sw600dp{,-land}/sw768dp{,-land}; удалено **75 каталогов** (81 файл: 75 strings.xml + 6 переопределений пунктуации), 668 КБ |
| Строки в base | — | −12: `locale_name_{en_GB,es_US,hi_ZZ,sr_ZZ}`, `subtype_{traditional,compact,bds,q,f,akkhor,ergol,hcesar}`; `subtype_generic_layout` помечен `translatable="false"`; exception-массивы в donottranslate.xml срезаны до en_US+tt_RU; values-ru/tt синхронно (−7 строк каждый) |
| `app/build.gradle` | resConfigs отсутствовал | `resConfigs "tt", "ru", "en"` — режет и переводы androidx.customview |

Достижимость layout-XML проверена построением графа включений `@xml/` из 12
оставшихся наборов (tatar, russian + 9 predefined generic для en_US) плюс все
ссылки из values/, манифеста и `R.xml.*` в коде: `kbd_more_keys_keyboard_template`
(атрибут темы) и общие symbols/phone/number на месте.

### Миграция пользовательских префов

Записи удалённых сабтипов в `pref_enabled_subtypes` отваливаются мягко:
`getSubtype()` возвращает null → запись пропускается, недостающие дефолты
(tt/ru/en) дозаполняются (`SubtypeList.reload`), преф переписывается.
Проверено на эмуляторе: преф `ru:russian;tt_RU:tatar;en_US:qwerty` пережил
срез без изменений.

### Гейты

| Гейт | Результат |
|---|---|
| `./gradlew test` | **973 / 0 failures / 0 errors** (перед каждым коммитом) |
| `./gradlew assembleRelease` | OK, подписан тем же ключом |
| `lintRelease` | 22 errors / 36 warnings; **MissingTranslation 155 → 0** (последняя — `subtype_generic_layout`, закрыта `translatable="false"`); StringFormatMatches 39 → 4; оставшиеся 22 (ResourceType 18 + StringFormatMatches 4) предсуществующие — baseline в фазе 4 |
| `scripts/check-no-internet.sh` | оба режима (debug + release) зелёные |
| python-тесты (7 файлов) | **181 OK** (1 предсуществующий skip в emoji_pack) |

Тесты под новый состав: `ThreeLanguageStringsTest` — из пин-списка
осознанно-непереведённых убраны удалённые ключи, докстринг приведён к трём
локалям; логика проверок не менялась. Других тестов, зависящих от состава
локалей/раскладок, grep не нашёл.

### Эмуляторный прогон (AVD tt_suggest_a14, debug APK)

Google Messages, поле сообщения (как в миссии tt-suggest-dies):

- цикл глобусом ru→tt→en→ru→tt — работает (MRU-ротация: после набора текста
  порядок пересобирается, это штатное поведение форка);
- татарская раскладка: пятый ряд Ә Ө Ү Җ Ң Һ на месте;
- подсказки: tt «Мин» → «Минем · Министры · Министрлыгы», ru «При» →
  «Привет · Придётся · Пришёл»;
- длинный тап а → ә (в поле «Ә», подсказки «Әлеге · Әле · Әмма»);
- эмодзи-панель открывается (длинный тап запятой), recents/поиск/категории;
- экран «Keyboard languages»: ровно Татарча / Russian / English (US),
  диалог «Add language» пуст (добавить нечего);
- `logcat -b crash` пуст, FATAL/AndroidRuntime-крэшей нет.

Свидетельства: `docs/restructure/evidence/3b-01…08` (7 PNG + текстовый дамп
экрана языков — сам экран под FLAG_SECURE, `SettingsHostActivity.kt:166–173`,
скриншот чёрный by design).

### Размеры

| Метрика | Базовая линия 1.9.4 | После 3а | После 3б |
|---|---|---|---|
| Release APK (подписан) | 2 538 949 Б | 2 526 324 Б | **2 130 746 Б** (−395 578 к 3а, −408 203 к базе, −16,1 %) |
| Файлов в APK | 548 | 537 | **250** |
| `resources.arsc` | 309 272 Б | — | **93 024 Б** (конфигурации только tt/ru + системные — `aapt2 dump configurations`) |
| `classes.dex` | 406 768 Б | — | **384 724 Б** (уехала KeyboardTextsTable и ветки switch) |

Запас до инварианта 3 МиБ: 33,6 %.

### Отклонения и пометки

- `KeyboardTextsTable.java` — сгенерированный файл, срезан руками (помечено в
  комментарии у `LOCALES_AND_TEXTS`); `TEXTS_sah` не нёс generated-комментария
  и удалён отдельной правкой.
- 6 локальных переопределений пунктуации (bn, el, fr, fr-CA, hi, hy) удалены
  вместе с каталогами — пунктуация теперь везде из base, что соответствует
  составу tt/ru/en.
- `LAYOUT_*`-константы комплексных письменностей оставлены: switch в
  `InputLogic` на них ссылается; ветки мертвы, удаление — отдельная гигиена.
- Пример в `select_language_description` (managed config) приведён к новому
  составу: `tt_RU:tatar;ru:russian;en_US:qwerty`.

## Фаза 4а. Оптимизация: hot-path, R8-шринк, CI-усиление, дожим размера

Дата: 2026-08-30. Ветка `main`, без push. 4 содержательных коммита.

### Таблица изменений

| Изменение | Файлы | Эффект |
|---|---|---|
| Hot-path: переиспользуемый буфер ширин вместо `new float[len]` на каждый показ превью клавиши | `KeyPreviewView.java` (поле `mTextWidthsBuffer`, package-private хелперы `ensureWidthsCapacity`/`sumWidths`), новый `KeyPreviewViewTextWidthsTest.kt` (+3 JVM-теста) | Ноль аллокаций в steady-state показа превью; гонки нет (вьюшка на UI-потоке). Тест фиксирует контракт: суммируются только свежие `count` записей, стейл-хвост длинного буфера не попадает в результат |
| R8 optimized resource shrinking | `gradle.properties`: удалён `android.r8.optimizedResourceShrinking=false`, включён `android.nonFinalResIds=true` (обязательное требование оптимизированного шринкера) | Единый граф достижимости код+ресурсы; APK −400 Б |
| CI: python-пакеры | `.github/workflows/ci.yml`: шаг `for f in tests/*/test_*.py; do python3 "$f"` | 181 тест в CI (чистый unittest, без pytest) |
| CI: полный lint вместо vital | `app/build.gradle`: `lint { baseline = file("lint-baseline.xml"); abortOnError true }`; `app/lint-baseline.xml` (22 предсуществующие ошибки закоммичены); `app/lint.xml` с пояснением; ci.yml: `lintRelease` вместо `lintVitalRelease` | `lintRelease` зелёный; новая lint-ошибка ломает сборку (проверено зондом ResourceType в EmojiPanelView.kt: lint exit=1, зонд откачен) |
| Дожим PNG | 15 mipmap-иконок пережаты zopfli (re-deflate того же фильтрованного потока, numiterations=50; venv в /tmp, системно ничего не ставилось) | 138 586 → 125 966 Б исходников; пиксели побайтово идентичны (PIL RGBA-сверка каждого файла) |
| Исключение `**/*.kotlin_builtins` | `app/build.gradle` packagingOptions | Метаданные компилятора kotlin-stdlib, в рантайме не нужны (kotlin-reflect в приложении нет): −46,7 КБ распакованных |

### Размеры APK

| Точка | Размер |
|---|---|
| После фазы 3б | 2 130 746 Б |
| После R8-шринка | 2 130 346 Б (−400) |
| После PNG + kotlin_builtins | **2 110 476 Б** (−20 270 к фазе, −428 473 к базе 1.9.4, −16,9 %) |

Запас до инварианта 3 МиБ: 35,2 %.

### Lint-статус

- `lintRelease` с baseline: **зелёный** (22 errors + 36 warnings отфильтрованы baseline, новых нет).
- Вердикт по ResourceType (18 ошибок, EmojiPanelView.kt:366–372, EmojiSearchView.kt:198–203):
  **false positive**. Код читает цвета темы через
  `context.theme.obtainStyledAttributes(intArrayOf(R.attr.*))` — индекс в
  `TypedArray.getColor()` это порядковый номер в ручном массиве атрибутов, а не
  `@StyleableRes`; lint такие массивы не сопоставляет. Рантайм корректен
  (эмодзи-панель рисуется с правильными цветами — смоук 4а). Оставлены в baseline,
  пояснение в `app/lint.xml`. Настоящий фикс (declare-styleable на панель) — отдельная гигиена.
- StringFormatMatches (4, SettingsHostActivity.kt:1207,1229): Int передаётся в `%s` —
  `String.format("%s", int)` отрабатывает корректно; оставлены в baseline.
- Пометка: `lintVitalRelease` внутри `assembleRelease` печатает informational
  «58 entries listed in baseline but not found» — vital-вариант не содержит
  baseline-категорий; на результат не влияет.

### Эмуляторный смоук (AVD tt_suggest_a14, release APK 2 110 476 Б)

Google Messages, поле сообщения:

- татарская раскладка поднимается, пятый ряд Ә Ө Ү Җ Ң Һ на месте
  (getIdentifier-резолвы раскладок целы под оптимизированным шринком);
- «Мин» → подсказки «Министры · Минем · Министрлыгы»;
- эмодзи-панель открывается (длинный тап запятой): категории, поиск, recents;
- `logcat -b crash` пуст после всех прогонов.

Свидетельства: `docs/restructure/evidence/4a-01-tt-layout.png`,
`4a-02-suggestions-min.png`, `4a-03-emoji-panel.png`.

### Гейты

| Гейт | Результат |
|---|---|
| `./gradlew test` | **976 / 0 failures / 0 errors** (973 + 3 новых) |
| `./gradlew assembleRelease` | OK, 2 110 476 Б, подписан тем же ключом |
| `./gradlew lintRelease` | зелёный с baseline |
| `scripts/check-no-internet.sh` | оба режима (debug + release APK) зелёные |
| python-тесты (7 файлов) | **181 OK** (1 предсуществующий skip в emoji_pack) |

### Отклонения и пометки

- R8-флаг **удалён, не возвращён**: единственное препятствие (требование
  nonFinalResIds) снято конфигом, компиляция и смоук чистые.
- `res/raw/keep.xml` не тронут.
- usage.txt/resources.txt после всех правок: нового мусора нет (top usage.txt —
  вычищенные androidx.annotation); `assets/**/NOTICE.txt` (9,7 КБ) оставлены
  осознанно — лицензионные уведомления словарей/эмодзи.
- PNG-дожим касался только res/ (иконки лончера); `icons/`, `metadata/` в APK
  не попадают и не трогались. zopfli/optipng системно отсутствовали —
  использован pip-пакет zopfli в venv `/tmp/pngvenv`, скрипт `/tmp/pngsqueeze.py`
  (в репозиторий не вносился).

## Фаза 4б. Baseline Profile

Дата: 2026-08-30. Ветка `main`, без push. 3 коммита (модуль генерации, профиль,
этот журнал).

### Подход

Стандартная схема AGP 9.x, заработала на AGP 9.2.1 / Gradle 9.6.0 с первой
подобранной комбинацией версий (стабильные, не альфы):

- новый dev-only модуль `:baselineprofile` (плагины `com.android.test` +
  `androidx.baselineprofile` 1.4.1, зависимости `benchmark-macro-junit4:1.4.1`,
  `uiautomator:2.3.0`, `test.ext:junit:1.2.1`) — в APK не попадает ничего,
  единственный продукт модуля — сгенерированный профиль;
- в `:app` применён плагин `androidx.baselineprofile` (classpath
  `androidx.benchmark:benchmark-baseline-profile-gradle-plugin:1.4.1` в корневом
  buildscript) и добавлена зависимость `baselineProfile project(':baselineprofile')`
  — чистая проводка, без артефактов в APK;
- генерация на подключённом AVD (`useConnectedDevices = true`, GMD не нужен):
  `./gradlew :app:generateReleaseBaselineProfile`;
- `androidx.profileinstaller` **не добавляется** — runtime-зависимостей в
  приложении по-прежнему ноль (трейдофф разобран ниже).

### Сценарий CUJ (ImeBaselineProfileGenerator)

CUJ для IME — не launcher-activity, а старт процесса клавиатуры системой по
фокусу в поле. Каждая итерация: холодный kill процесса → `ime enable`/`ime set`
→ старт `SetupActivity` (в ней есть try-it EditText, появляющийся когда наш IME
выбран) → тап по полю → ожидание клавиатуры → набор «сәлам» реальными тапами
по клавишам татарской раскладки → длинный тап запятой → эмодзи-панель → back,
home. Координаты клавиш откалиброваны скриншотом на AVD tt_suggest_a14
(1080×2280) и хранятся в генераторе как доли экрана; верифицировано, что пять
тапов коммитят ровно «сәлам», а длинный тап открывает панель.

Три грабли, найденные по дороге (важны для будущих прогонов):

1. **`org.tatarkeyboard.ime/.latin.LatinIME` для `ime enable/set` не работает.**
   Относительное имя компонента резолвится против *applicationId*, а не
   source-namespace: настоящий id —
   `org.tatarkeyboard.ime/rkr.simplekeyboard.inputmethod.latin.LatinIME`
   (то, что показывает `dumpsys input_method` в `mCurMethodId`). С коротким id
   команда молча отвечает «Unknown input method», а `executeShellCommand` в
   uiautomator ошибку не бросает — поэтому генератор теперь верифицирует выбор
   через `settings get secure default_input_method`.
2. **Kill в Macrobenchmark — это force-stop, а force-stop выбранного IME
   сбрасывает `default_input_method`** (проверено эмпирически: после
   «Force-stopping process» настройка улетает на GBoard). Поэтому `ime set`
   делается строго ПОСЛЕ `killProcess()`, а не до.
3. **`compileSdk` внутри `defaultConfig` несовместим с baselineprofile-плагином**
   (AGP 9.2.1 падает «project ':app' does not specify compileSdk» при его
   применении). `compileSdk 37` вынесен на уровень `android {}` — на выходной
   APK не влияет (проверено: дельта релизного APK равна ровно размеру профиля).

Отдельная находка про замеры: `coldstart.sh` требует `adb root` — `kill -9`
чужого процесса от shell-пользователя на userdebug-образе API 34 отклоняется
(«Operation not permitted»). В прошлых аудитах root на AVD, видимо, уже был
включён; в самом скрипте это не отражено.

### Сгенерированный профиль

- `app/src/main/baseline-prof.txt` — 1552 правила (с шапкой-комментарием:
  генератор, дата, сценарий, команда; `#`-комментарии R8 пережёвывает — сборка
  и маппинг чистые). `app/src/main/startup-prof.txt` — startup-профиль для
  DEX layout (AGP 8.3+, у нас включён).
- Генерация шла против необфусцированного `nonMinifiedRelease`; при сборке
  релиза R8 переписал правила под обфусцированные имена
  (`app/build/intermediates/r8_art_profile/release/minifyReleaseWithR8/baseline-prof.txt`,
  662 правила после свёртки инлайнинга).
- Покрытие hot-path (по текстовому профилю): `LatinIME.onCreate`/`onCreateInputView`,
  парсинг раскладки (`KeyboardLayoutSet$Builder.parse*`, `KeySpecParser`),
  отрисовка (`MainKeyboardView` — 37 правил, `PointerTracker` — 61), подсказки
  (`SuggestionsController`, пакет `dictionary` — 50, включая `BigramStorage`),
  эмодзи-панель (`emoji/EmojiPanelView` — 33).
- В релизном APK: `assets/dexopt/baseline.prof` (883 Б, бинарный, версия 010) и
  `assets/dexopt/baseline.profm` (169 Б). Проверено `unzip -l` + чтением на
  устройстве (`profman --create-profile-from` из переписанного R8 текста даёт
  валидный reference-профиль).

### Когда профиль реально применяется (трейдофф без profileinstaller)

Зафиксировано эмпирически на AVD API 34, и это формулировка для будущих
читателей:

- **`adb install` профиль НЕ применяет.** После установки `ref/primary.prof`
  пуст, dexopt идёт с `--compiler-filter=verify` (artd: «Merge skipped because
  there are no existing profiles»), статус пакета `[status=verify]
  [reason=install]`. Это и есть цена отказа от profileinstaller на
  sideload-канале: локальная установка профиль молча игнорирует.
- Профиль применяется, когда его доставляет в reference-профиль кто-то другой:
  установка из Google Play (install-time dexopt с профилем) либо
  profileinstaller при первом запуске. Проверено руками: профиль, положенный в
  `/data/misc/profiles/ref/org.tatarkeyboard.ime/primary.prof` +
  `cmd package compile -m speed-profile -f` → статус `[status=speed-profile]`,
  появляется `base.art` (69 КБ AOT-кода; при verify его нет). ART читает наш
  профиль корректно — проблема только в доставке при sideload.
- Профиль оставлен в APK осознанно: для Play-канала и будущих механизмов он
  работает, стоит 1 299 Б, вреда на sideload не приносит.

### Замеры до/после (холодный старт до первого кадра клавиатуры)

Метод прежний (`docs/archive/bigrams/imperative-heads/evidence/coldstart.sh`,
поле 22 `/proc/<pid>/stat` → первый FrameCompleted), тот же AVD tt_suggest_a14,
та же сессия, Google Messages compose; 20 попыток, 15 удачных в обоих прогонах.
«До» — текущий релиз без профиля (verify). «После» — релиз с профилем,
скомпилированный speed-profile (состояние, эквивалентное установке из Play).
Сырые числа: `docs/restructure/evidence/coldstart-4b-before.txt` и
`coldstart-4b-after.txt`.

| | До (без профиля) | После (speed-profile) | инвариант |
|---|---:|---:|---:|
| медиана, мс | 125,0 | 127,3 | < 400 |
| среднее | 124,4 | 132,8 | |
| минимум | 104,8 | 100,7 | |
| худший | 138,7 | 165,2 | |

**Эффект на эмуляторе не измерим**: разброс между попытками того же порядка,
что и разница медиан; хвост «после» даже тяжелее. База в 126,3 мс (1.9.4) уже
была далека от инварианта 400 мс, а эмулятор ≠ железо (JIT/AOT-картина на
x86_64-хосте другая). Профиль оставлен: вреда нет, на железе и при
Play-установке он работает как задумано.

### Размер APK

| Точка | Размер |
|---|---|
| После фазы 4а | 2 110 476 Б |
| С baseline-профилем | **2 111 775 Б** (+1 299 Б) |

Дельта = baseline.prof (883) + baseline.profm (169) + zip-выравнивание.
Запас до инварианта 3 МиБ: 35,1 %.

### Гейты

| Гейт | Результат |
|---|---|
| `./gradlew test` | **976 / 0 failures / 0 errors** |
| `./gradlew assembleRelease` | OK, 2 111 775 Б, подписан тем же ключом |
| `./gradlew lintRelease` | зелёный с baseline |
| `scripts/check-no-internet.sh` | оба режима (debug + release APK) зелёные |
| python-тесты (7 файлов) | **181 OK** (1 предсуществующий skip в emoji_pack) |

### Эмуляторный смоук (финальный release APK, tt_suggest_a14)

- клавиатура поднимается в Messages, татарская раскладка с пятым рядом;
- «Мин» → подсказки «Министры · Минем · Министрлыгы» (кадр совпадает с
  4a-02-свидетельством);
- эмодзи-панель открывается (длинный тап запятой): recents, поиск, категории;
- `logcat -b crash` пуст, FATAL/AndroidRuntime нет.

Свидетельства: `docs/restructure/evidence/4b-01-suggestions-min.png`,
`4b-02-emoji-panel.png`.

### Найденное по дороге (не регрессия, зафиксировано)

При смоуке подсказки сначала не показывались: connected-тест генератора
деинсталлирует приложение в конце прогона, и повторный `install -r` оказался
ЧИСТОЙ установкой — данные (распакованные словари, префы) стёрлись. А на
чистой установке подсказки **выключены по умолчанию**
(`PREF_TATAR_SUGGESTIONS`, default `false`, `Settings.java:310`; есть
одноразовый оффер `PREF_TATAR_SUGGESTIONS_OFFER_SPENT`). После включения префа
словари распаковались штатно (имена с пинами SHA-256 совпали с аудитом 1.9.4)
и полоса заработала. Поведение соответствует продуктовому решению (подсказки —
opt-in), регрессии нет; но стоит помнить: смоук подсказок на свежих данных
требует включения настройки.

## Фаза 5. Финализация (релиз-кандидат 1.9.5)

Дата: 2026-08-30. Ветка `main`, без push.

### Сделано

- **Версия**: `app/build.gradle` — versionCode 21, versionName `1.9.5`;
  `CHANGELOG.md` — раздел `[1.9.5]`; `metadata/en-US/changelogs/21.txt`
  (296 символов, лимит 500).
- **Сборка и артефакт**: `./gradlew assembleRelease` → 2 111 775 Б, подписан
  тем же ключом; скопирован в `dist/tatar-keyboard-1.9.5.apk`
  (SHA-256 `cb3fb64f…2306`).
- **Аудит** — `docs/APK-AUDIT-1.9.5.md`: дельта по компонентам против 1.9.4
  (res/ −503 749, arsc −216 908, kotlin_builtins −51 125, dex −21 500,
  assets +1 052 — ровно baseline-профиль), сертификат `98ca6feb…42ad`,
  разрешение одно (VIBRATE), пины всех четырёх ассетов сверены с константами
  `DictionaryStorageContracts.kt`/`BigramStorageContracts.kt` — **совпали
  побайтно, сжатые и распакованные SHA-256**.
- **Обновление поверх 1.9.4** на AVD `tt_suggest_a14`: `firstInstallTime` не
  изменился (20:20:32), распакованные словари/таблицы НЕ переинфлятились
  (те же имена файлов с raw SHA-256, mtime — время первой распаковки), префы
  подсказок и личного словаря сохранились, подсказки tt и ru работают,
  `logcat -b crash` пуст. Свидетельства: `docs/restructure/evidence/5-01…5-04`.
- **Документы**: `PUBLISH-CHECKLIST.md` перецелен на 1.9.5 датированной
  пометкой (история не переписана); `HANDOFF.md` переписан под кампанию;
  `docs/README.md` — добавлен живой аудит 1.9.5; `README.md` проверен
  (устаревших утверждений нет); `BRIEF.md` — датированная пометка об
  актуальных SDK (24/37/37) к строке брифа.

### Гейты (финальные, на HEAD)

| Гейт | Результат |
|---|---|
| `./gradlew test` | **976 / 0 failures / 0 errors** (перепрогнан с `--rerun-tasks`) |
| `./gradlew lintRelease` | зелёный с baseline |
| `./gradlew assembleRelease` | OK, 2 111 775 Б, подписан тем же ключом |
| `scripts/check-no-internet.sh` | оба режима зелёные, включая `dist/tatar-keyboard-1.9.5.apk` |
| python-тесты (7 файлов) | **181 OK** (1 предсуществующий skip в emoji_pack) |

### Зафиксированные факты

- **Сборка не байт-в-байт воспроизводима**: две полные пересборки одного дерева
  (`--rerun-tasks`) дали APK одного размера (2 111 775 Б) с разными SHA-256
  (`cb3fb64f…` и `adb542c6…`). В релиз вкладывать файл из `dist/`.
- **Поправка к журналу фаз 3б–4б**: записанные там значения запаса до 3 МиБ
  (33,6 / 35,2 / 35,1 %) арифметически неверны. По формуле аудитов
  (свободное / 3 145 728) правильные значения: после 3б — 32,3 %,
  после 4а — 32,9 %, после 4б — **32,9 %** (1 033 953 / 3 145 728).
  Исторические записи не переписаны; в CHANGELOG и аудите 1.9.5 — 32,9 %.

## Итог кампании (до → после)

| Метрика | Базовая линия (1.9.4) | Итог (1.9.5) |
|---|---:|---:|
| Release APK (подписан) | 2 538 949 Б | **2 111 775 Б (−427 174, −16,8 %)** |
| `resources.arsc` (несжатый) | 309 272 Б | 92 364 Б |
| `classes.dex` (несжатый) | 406 768 Б | 385 268 Б |
| Файлов в APK | 548 | 244 |
| JVM-тесты | 974 / 0 | 976 / 0 |
| Python-тесты | 181 / 0 | 181 / 0 |
| `lintRelease` | 212 errors / 102 warnings, сборку ломал | зелёный с baseline (22 + 36 зафиксированы, новых нет) |
| Локали / сабтипы | ~75 локалей, 73 сабтипа | tt / ru / en |
| Холодный старт, медиана (эмулятор) | 126,3 мс | 127,3 мс (speed-profile) / 125,0 (verify) — разница не измеряется |
| CI | JVM-тесты, lintVital | + python-тесты, полный lintRelease с baseline |

Кампания закрыта на `main`. Наружу не ушло ничего: push, тег наружу и
публикация — действия оператора (`docs/PUBLISH-CHECKLIST.md`).
