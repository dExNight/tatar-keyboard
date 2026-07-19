---
phase: 02-tatarskaya-raskladka
plan: 01
subsystem: ui
tags: [android, ime, keyboard-layout, xml, tatar, cyrillic, aosp-latinime]

# Dependency graph
requires:
  - phase: 01-hello-world
    provides: "собирающийся форк (applicationId org.tatarkeyboard.ime), scripts/check-no-internet.sh, gradle wrapper 9.6.0"
provides:
  - "Татарская раскладка целиком как XML-данные: 7 новых файлов по цепочке east_slavic (keyboard_layout_set_tatar → kbd_tatar → rows_tatar → rowkeys_tatar_extra/1/2/3)"
  - "Пятый ряд ә ө ү җ ң һ (U+04D9/04E9/04AF/0497/04A3/04BB) сверху, 6 × 16.667%p; вся ЙЦУКЕН литеральными кодпоинтами без !text/-рефов"
  - "Высоты решены в данных: default 5 рядов × 20%p, showNumberRow 6 рядов × 16.667%p, обе ветки на config_key_*_5row фракциях"
  - "Реестр SubtypeLocaleUtils: LOCALE_TATAR=\"tt\", LAYOUT_TATAR=\"tatar\", case с break, tt-subtype первым в getDefaultSubtypes — раскладка выбираема и активна по умолчанию на чистой установке"
affects: [phase-03-subtypes, phase-06-ios-skin, zamanalif-latin-later]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Раскладка = данные: rowkeys с литеральными keySpec (без !text/ → без KeyboardTextsTable) — образец для латиницы Zamanälif"
    - "kbd_<name> switch по showNumberRow с rowHeight, подобранным под число рядов (5×20%p / 6×16.667%p)"
    - "Регистрация locale = 4 реестровые записи в SubtypeLocaleUtils.java (единственный допустимый Java-тач для новой раскладки)"

key-files:
  created:
    - app/src/main/res/xml/rowkeys_tatar_extra.xml
    - app/src/main/res/xml/rowkeys_tatar1.xml
    - app/src/main/res/xml/rowkeys_tatar2.xml
    - app/src/main/res/xml/rowkeys_tatar3.xml
    - app/src/main/res/xml/rows_tatar.xml
    - app/src/main/res/xml/kbd_tatar.xml
    - app/src/main/res/xml/keyboard_layout_set_tatar.xml
  modified:
    - app/src/main/java/rkr/simplekeyboard/inputmethod/latin/utils/SubtypeLocaleUtils.java

key-decisions:
  - "Свои rowkeys_tatar1–3 с литеральными кодпоинтами вместо reuse east_slavic (Q1): DEFAULT-ловушка KeyboardTextsTable обойдена конструктивно, KeyboardTextsTable.java не тронут вообще"
  - "Locale-строка \"tt\" для MVP (Q2); tt_RU + три subtype — фаза 3"
  - "MVP-активация через subtypes.add(0, tt) в getDefaultSubtypes (Q3) — до английского fallback; матчинг системных locale не изменён"
  - "case LOCALE_TATAR размещён перед LOCALE_TAMIL_INDIA — вне sakha/serbian fall-through-цепочки; break присутствует (sakha-баг не унаследован)"
  - "Зафиксированное отклонение от «ноль Java»: 4 реестровые записи + 1 вставка в getDefaultSubtypes, всё в одном файле SubtypeLocaleUtils.java; движок не тронут"

patterns-established:
  - "Литеральные rowkeys: новая раскладка не зависит от полокальных Java-таблиц"
  - "5/6-рядные высоты штатными атрибутами rowHeight + 5row-фракциями — без правок KeyboardRow"

requirements-completed: [LAYOUT-01, LAYOUT-04, LAYOUT-05]

coverage:
  - id: D1-layout-data
    description: "Татарская раскладка целиком описана XML-данными (7 файлов), компилируется и присутствует в APK; ни одного !text/-рефа в rowkeys_tatar*"
    requirement: LAYOUT-05
    verification:
      - kind: integration
        ref: "./gradlew assembleDebug exit 0; aapt2 dump resources app-debug.apk содержит xml/keyboard_layout_set_tatar, xml/kbd_tatar, xml/rows_tatar, xml/rowkeys_tatar_extra/1/2/3; grep -l '!text/' rowkeys_tatar*.xml пуст; счёт <Key = 6/11/11/9; порядок &#x04D9;&#x04E9;&#x04AF;&#x0497;&#x04A3;&#x04BB; подтверждён"
        status: pass
    human_judgment: false
  - id: D2-java-boundary
    description: "Java-diff фазы = ровно один файл SubtypeLocaleUtils.java (реестр); KeyboardTextsTable.java, method.xml, движок (KeyboardBuilder/KeyboardLayoutSet/KeyboardRow/Key) не тронуты"
    requirement: LAYOUT-05
    verification:
      - kind: integration
        ref: "git diff --name-only ed2cf12..HEAD -- '*.java' '*.kt' → единственная строка SubtypeLocaleUtils.java; diff по KeyboardTextsTable.java/method.xml/движку пуст; case LOCALE_TATAR имеет break (grep -A2 подтверждён), case LOCALE_SAKHA не изменён"
        status: pass
    human_judgment: false
  - id: D3-registry-activation
    description: "tt/tatar зарегистрирован (sSupportedLocales + case + LAYOUT_TATAR) и вставлен первым в getDefaultSubtypes — компилируется; поведенческое подтверждение на устройстве"
    requirement: LAYOUT-01
    verification:
      - kind: integration
        ref: "assembleDebug exit 0 после Task 3 и Task 4; grep 'subtypes.add(0' подтверждён; check-no-internet.sh exit 0"
        status: pass
    human_judgment: false
  - id: D4-on-device-uat
    description: "On-device: клавиатура открывается татарской на чистой установке; все 37 букв тапом; shift → Ә Ө Ү Җ Ң Һ; 5/6 рядов без обрезки (logcat без «too tall»); ?123/#+= туда-обратно; showNumberRow совместим; SC4 smoke-матрица (Telegram / Chrome-WebView keyCode 229 / password-поле) без потерь/дублей"
    requirement: LAYOUT-01
    verification: []
    human_judgment: true
    rationale: "DEFERRED — human verification pending: устройство не подключено (adb devices пуст), как в фазе 1. Рендер (Pitfall 1/A1), регистр (A3), LAYOUT-04-переходы и SC4-матрица проверяемы только на устройстве. Полные UAT-шаги — § Deferred Verification ниже и STATE.md Blockers."

# Metrics
duration: 9min
completed: 2026-07-18
status: complete-local (Task 5 device UAT deferred)
---

# Phase 2 Plan 01: Татарская раскладка Summary

**Татарская раскладка как чистые XML-данные: пятый ряд ә ө ү җ ң һ сверху + полная ЙЦУКЕН литеральными кодпоинтами, слои ?123/#+= из коробки, регистрация tt в реестре и tt-дефолт на чистой установке — при Java-diff ровно в один файл-реестр**

## Performance

- **Duration:** 9 min
- **Started:** 2026-07-18T08:18:12Z
- **Completed:** 2026-07-18T08:28:02Z
- **Tasks:** 4 of 5 (Task 5 = human-verify checkpoint, deferred — device unavailable)
- **Files modified:** 8 (7 created + 1 modified)

## Accomplishments

- Все 37 буквенных клавиш татарской раскладки существуют как самодостаточные XML-данные: пятый ряд `ә ө ү җ ң һ` (алфавитный, сверху, 6 × 16.667%p) + ЙЦУКЕН 11/11/9 литеральными кодпоинтами — ни одного `!text/`-рефа, DEFAULT-ловушка KeyboardTextsTable обойдена конструктивно
- Риск высоты (Pitfall 1) закрыт в данных: default-ветка 5 рядов × 20%p, showNumberRow-ветка 6 рядов × 16.667%p, обе на готовых `config_key_*_5row` фракциях
- `keyboard_layout_set_tatar` объявляет все 6 Element; symbols/symbolsShifted → общие `kbd_symbols*` (LAYOUT-04 из коробки)
- Locale `tt` + layout `tatar` зарегистрированы в SubtypeLocaleUtils (4 точечные записи, `case` с `break` — sakha-баг не унаследован); tt-subtype вставлен первым в `getDefaultSubtypes` → на чистой установке клавиатура открывается татарской
- LAYOUT-05 держится механически: `git diff ed2cf12..HEAD` по `*.java`/`*.kt` = ровно `SubtypeLocaleUtils.java`; движок, KeyboardTextsTable.java и method.xml не тронуты; APK 1.92 МБ (бюджет ≤ 3 МБ), check-no-internet.sh зелёный после каждой задачи

## Task Commits

Each task was committed atomically:

1. **Task 1: rowkeys_tatar_extra + rowkeys_tatar1–3 (литеральные кодпоинты)** - `980a9b4` (feat)
2. **Task 2: rows_tatar + kbd_tatar + keyboard_layout_set_tatar (высоты 5/6 рядов)** - `4290094` (feat)
3. **Task 3: регистрация tt/tatar в SubtypeLocaleUtils (отклонение, только реестр)** - `53a7bd1` (feat)
4. **Task 4: MVP-активация — tt первым в getDefaultSubtypes** - `80418e5` (feat)
5. **Task 5: on-device UAT** — NOT PASSED, deferred (см. § Deferred Verification)

**Plan metadata:** `7e9406e` (docs: blockers/state) + SUMMARY commit.

## Files Created/Modified

- `app/src/main/res/xml/rowkeys_tatar_extra.xml` - пятый ряд ә ө ү җ ң һ, 6 клавиш, литеральные keySpec
- `app/src/main/res/xml/rowkeys_tatar1.xml` - й ц у к е н г ш щ з х (11, щ литералом)
- `app/src/main/res/xml/rowkeys_tatar2.xml` - ф ы в а п р о л д ж э (11, ы/э литералами)
- `app/src/main/res/xml/rowkeys_tatar3.xml` - я ч с м и т ь б ю (9, и литералом)
- `app/src/main/res/xml/rows_tatar.xml` - extra row 16.667%p сверху + ЙЦУКЕН-ширины east_slavic + row_qwerty4
- `app/src/main/res/xml/kbd_tatar.xml` - switch showNumberRow: 5 рядов × 20%p / 6 рядов × 16.667%p, 5row-фракции в обеих ветках
- `app/src/main/res/xml/keyboard_layout_set_tatar.xml` - 6 Element, alphabet=kbd_tatar, остальные общие kbd_symbols*
- `app/src/main/java/.../latin/utils/SubtypeLocaleUtils.java` - LOCALE_TATAR/LAYOUT_TATAR/sSupportedLocales/case+break + add(0, tt) в getDefaultSubtypes

## Decisions Made

- Q1 → свои rowkeys с литералами (KeyboardTextsTable не тронут); Q2 → `"tt"` для MVP; Q3 → `subtypes.add(0, ...)` в getDefaultSubtypes
- `case LOCALE_TATAR` размещён перед `LOCALE_TAMIL_INDIA` — вне sakha/serbian fall-through; `break` присутствует
- Комментарий в getDefaultSubtypes фиксирует, что вставка — MVP-механизм фазы 2, снимается в фазе 3 (SWITCH-01/02)

## Deviations from Plan

None - plan executed exactly as written. (Правка SubtypeLocaleUtils.java — не deviation исполнения, а заранее зафиксированное в плане/CONTEXT/RESEARCH отклонение от «ноль Java», ограниченное реестром.)

## Deferred Verification

**Task 5 (checkpoint:human-verify) — deferred — human verification pending.** Устройство не подключено (`adb devices` пуст), по образцу фазы 1. Дублируется в STATE.md Blockers. UAT-шаги при появлении устройства:

1. Чистая установка: `adb uninstall org.tatarkeyboard.ime.debug` (если стоит) → `adb install app/build/outputs/apk/debug/app-debug.apk` → включить/выбрать «Tatar Keyboard (dev)» (чистая установка обязательна: prefs с фазы 1 маскируют дефолт Task 4)
2. Открыть заметки/мессенджер — клавиатура открывается ТАТАРСКОЙ: пятый ряд `ә ө ү җ ң һ` виден СВЕРХУ, под ним полная ЙЦУКЕН
3. Напечатать «әни өй үрдәк җир таң һава» + фразу со щ/ы/э/и («щи, ыл, эш, ике») — все 37 букв коммитятся тапом, четыре литеральные клавиши не пустые
4. Shift → пятый ряд показывает/вводит Ә Ө Ү Җ Ң Һ (A3)
5. Визуально: 5 рядов + action row помещаются, нижний ряд не обрезан; `adb logcat | grep -i "too tall"` пуст (Pitfall 1/A1)
6. Тап `?123` → слой цифр/символов; `#+=` → второй слой; `АБВ` → возврат к татарским буквам (LAYOUT-04)
7. Number row ON → цифровой ряд НАД пятым, всё помещается (6 рядов); выключить обратно
8. **SC4 smoke-матрица**: «әни өй үрдәк җир таң һава» в (a) Telegram (поле сообщения); (b) Chrome — адресная строка И текстовое поле веб-страницы/WebView (keyCode 229); (c) password-поле — точки маскировки на каждый тап, включая ә ө ү җ ң һ. Во всех трёх — без потерянных/задвоенных букв

## Issues Encountered

None — сборка зелёная с первого прогона после каждой задачи; hooks не блокировали коммиты.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Раскладочный слой готов для фазы 3 (LAYOUT-02 long-press дубли, LAYOUT-03 ru/en, SWITCH-01/02 subtypes/глобус): паттерн «rowkeys литералами + запись в реестр» отработан
- Блокер: on-device UAT фазы 2 (этот план, шаги выше) + отложенные проверки фазы 1 — прогнать одним заходом при первой возможности (устройство + GitHub)
- MVP-вставка `subtypes.add(0, tt)` в getDefaultSubtypes подлежит замене в фазе 3 при переходе на tt_RU + три subtype

## Self-Check: PASSED

- 7 созданных XML + SubtypeLocaleUtils.java существуют на диске ([ -f ] подтверждено)
- `git log --oneline --grep="02-01"` ≥ 1 коммит (5 коммитов: 980a9b4, 4290094, 53a7bd1, 80418e5, 7e9406e)
- Все acceptance-критерии Tasks 1–4 прогнаны повторно — PASS (счёт клавиш 6/11/11/9, порядок кодпоинтов, отсутствие !text/ и moreKeys, rowHeight 20/16.667, 5row-фракции × 4, row_qwerty0 в case, break у case LOCALE_TATAR, subtypes.add(0, диапазон Java-diff)
- Plan-level verification: assembleDebug exit 0; check-no-internet.sh exit 0; aapt2 dump resources содержит все 7 xml/*tatar*; Java-diff = ровно SubtypeLocaleUtils.java
- Task 5 честно deferred (не помечен пройденным)

---
*Phase: 02-tatarskaya-raskladka*
*Completed: 2026-07-18 (local build scope; device UAT deferred)*
