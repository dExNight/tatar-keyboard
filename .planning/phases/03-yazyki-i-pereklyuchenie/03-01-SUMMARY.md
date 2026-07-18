---
phase: 03-yazyki-i-pereklyuchenie
plan: 01
subsystem: ui
tags: [android, ime, subtypes, morekeys, long-press, language-switch, tatar, russian, xml]

# Dependency graph
requires:
  - phase: 02-tatarskaya-raskladka
    provides: "татарская раскладка данными (rowkeys_tatar* литеральными кодпоинтами), паттерн регистрации в SubtypeLocaleUtils, scripts/check-no-internet.sh"
provides:
  - "10 long-press дублей на татарской раскладке литеральными latin:moreKeys (у→ү е→ё н→ң г→һ х→һ / а→ә о→ө ж→җ э→ә / ь→ъ) — LAYOUT-02 + ревью F1, без !text/ и без KeyboardTextsTable"
  - "Русская раскладка как собственный layout set russian (6 новых XML): стандартная ЙЦУКЕН без пятого ряда + те же 10 дублей — LAYOUT-03 данными, shared east_slavic не тронут"
  - "Реестр SubtypeLocaleUtils: LOCALE_TATAR=\"tt_RU\", LAYOUT_RUSSIAN=\"russian\", case LOCALE_RUSSIAN с break вне east_slavic-группы, getDefaultSubtypes = детерминированная тройка tt_RU→ru→en_US (MVP-хак фазы 2 и мёртвый fallback F2 удалены)"
  - "Display name «Татарча» через locale_exception_keys + locale_name_tt_RU (эндоним, фиксирован на любом языке системы)"
  - "SWITCH-01-персистентность и SWITCH-02 (глобус/цикл/пикер) — ноль нового кода, штатные механизмы форка (верификация on-device deferred)"
affects: [phase-04-input-mechanics, phase-06-ios-skin, phase-08-matrix, zamanalif-latin-later]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Long-press дубли = литеральный latin:moreKeys одним кодпоинтом на существующей Key (без !text/, без полокальных Java-таблиц) — прецедент rowkeys_bengali2"
    - "Отвязка локали от shared layout set: собственный набор rowkeys/rows/kbd/keyboard_layout_set + перевод case в реестре на новый LAYOUT_* (shared остаётся чужим локалям)"
    - "Детерминированные дефолтные subtypes вместо матчинга системных локалей (ЦА известна: tt_RU/ru/en_US)"

key-files:
  created:
    - app/src/main/res/xml/rowkeys_russian1.xml
    - app/src/main/res/xml/rowkeys_russian2.xml
    - app/src/main/res/xml/rowkeys_russian3.xml
    - app/src/main/res/xml/rows_russian.xml
    - app/src/main/res/xml/kbd_russian.xml
    - app/src/main/res/xml/keyboard_layout_set_russian.xml
  modified:
    - app/src/main/res/xml/rowkeys_tatar1.xml
    - app/src/main/res/xml/rowkeys_tatar2.xml
    - app/src/main/res/xml/rowkeys_tatar3.xml
    - app/src/main/java/rkr/simplekeyboard/inputmethod/latin/utils/SubtypeLocaleUtils.java
    - app/src/main/res/values/donottranslate.xml
    - app/src/main/res/values/strings.xml

key-decisions:
  - "Q1 → литеральные moreKeys без KeyboardTextsTable (консистентно с литеральными keySpec фазы 2); одиночные кодпоинты, заглавные не прописаны (MoreKeySpec auto-upcase по locale)"
  - "Русская раскладка → собственный layout set russian (вариант A): у ж/х в shared rowkeys_east_slavic* нет атрибута moreKeys вовсе, а shared делят be_BY/kk/ky/uk — контаминация исключена конструктивно"
  - "Q4 → LOCALE_TATAR мигрирован на \"tt_RU\"; prefs-миграцию НЕ писали (приложение не публиковалось; невалидная строка tt:tatar самовосстанавливается пустым списком → новая тройка)"
  - "Q7 → getDefaultSubtypes = детерминированная тройка (цикл системных локалей, MVP-вставка add(0,...) и мёртвый английский fallback удалены — ревью F2 закрыто); попутно удалены ставшие неиспользуемыми импорты HashSet/Locale/LocaleUtils"
  - "Q5 → «Татарча» через locale_exception_keys (паттерн Hinglish/Srpski); ru остаётся на ICU-имени, en_US уже в исключениях"
  - "Акцентные moreKeys донора (е́ ѣ ў ґ є і…) НЕ перенесены; keyHintLabel-подсказки НЕ рисуем (Open Q1/Q2 ресерча — минимализм, вернуться в фазах 6–7)"
  - "pref_enable_ime_switch остаётся false — тап-цикл строго внутри IME (tt→ru→en), уход в другие клавиатуры через long-press пикер (Q3)"

patterns-established:
  - "moreKeys литералом: дубль добавляется атрибутом на существующую Key — раскладка остаётся данными"
  - "Новая локаль на базе чужого shared: копировать структуру в свой набор файлов, а не ветвить shared XML"

requirements-completed: []  # LAYOUT-02, LAYOUT-03, SWITCH-01, SWITCH-02 — BUILD-часть закрыта; галочки после device UAT (Task 5 deferred). SWITCH-01 при простановке аннотировать: «система видит три subtype» = виртуальный subtype-реестр форка (принятая трактовка, ресерч Open Q3 / A3 плана)

coverage:
  - id: D1-tatar-morekeys
    description: "10 long-press дублей на татарской раскладке литеральными latin:moreKeys (вкл. е→ё, ь→ъ из ревью F1), без !text/, заглавные не прописаны, rowkeys_tatar_extra не тронут"
    requirement: LAYOUT-02
    verification:
      - kind: integration
        ref: "./gradlew assembleDebug exit 0; grep -c latin:moreKeys → 5/4/1 по rowkeys_tatar1–3; grep '!text/' пуст; счёт <Key не изменился (11/11/9, extra=6); check-no-internet.sh exit 0"
        status: pass
    human_judgment: false
  - id: D2-russian-layout
    description: "Русская раскладка как собственные XML-данные: 6 файлов *_russian, стандартная ЙЦУКЕН без пятого ряда + те же 10 moreKeys; ресурсы в APK"
    requirement: LAYOUT-03
    verification:
      - kind: integration
        ref: "aapt2 dump resources app-debug.apk содержит xml/keyboard_layout_set_russian, kbd_russian, rows_russian, rowkeys_russian1–3; grep -c latin:moreKeys → 5/4/1; rows_russian без rowkeys_tatar_extra, ровно 3 <Row + row_qwerty4; assembleDebug exit 0"
        status: pass
    human_judgment: false
  - id: D3-registry-triple
    description: "Реестр: tt_RU, LAYOUT_RUSSIAN, case LOCALE_RUSSIAN с break вне east_slavic-группы (группа = ровно 4 локали), getDefaultSubtypes = детерминированная тройка tt_RU→ru→en_US без MVP-хака и мёртвого fallback"
    requirement: SWITCH-01
    verification:
      - kind: integration
        ref: "grep LOCALE_TATAR=\"tt_RU\", LAYOUT_RUSSIAN=\"russian\", case LOCALE_RUSSIAN+addLayout+break подтверждены; grep 'subtypes.add(0,' пуст; east_slavic-группа = 4 case; assembleDebug exit 0"
        status: pass
    human_judgment: false
  - id: D4-display-name
    description: "Subtype tt_RU показывается как «Татарча» на любом языке системы (locale_exception_keys + locale_name_tt_RU, translatable=false)"
    requirement: SWITCH-01
    verification:
      - kind: integration
        ref: "grep '<item>tt_RU</item>' donottranslate.xml и 'locale_name_tt_RU' strings.xml подтверждены; assembleDebug exit 0"
        status: pass
    human_judgment: false
  - id: D5-java-boundary
    description: "Java-diff фазы = ровно один файл SubtypeLocaleUtils.java; shared east_slavic и KeyboardTextsTable.java не тронуты ни байтом (T2/T3 закрыты механически)"
    verification:
      - kind: integration
        ref: "git diff --name-only b0b4606..HEAD -- '*.java' '*.kt' = единственная строка SubtypeLocaleUtils.java; diff по '*east_slavic*' и KeyboardTextsTable.java пуст"
        status: pass
    human_judgment: false
  - id: D6-on-device-uat
    description: "On-device: чистая установка открывается татарской; Languages показывает тройку «Татарча»/ru/«English (US)»; глобус-цикл tt→ru→en→tt; long-press пикер; 10 дублей на обеих раскладках; shift+long-press → заглавная; персистентность после force-stop; smoke-матрица SC5 (Telegram/Chrome-WebView 229/password); MIUI при наличии Xiaomi"
    requirement: SWITCH-02
    verification: []
    human_judgment: true
    rationale: "DEFERRED — human verification pending: устройство не подключено (adb devices пуст), по образцу фаз 1–2. Рантайм popup/пикера (A2 плана), цикл, персистентность и SC5-матрица проверяемы только на устройстве. Полные UAT-шаги — § Deferred Verification ниже и STATE.md Blockers."

# Metrics
duration: 8min
completed: 2026-07-18
status: complete-local (Task 5 device UAT deferred)
---

# Phase 3 Plan 01: Языки и переключение Summary

**Три языка как subtypes (tt_RU активный, ru со своей ЙЦУКЕН-раскладкой, en_US) + 10 long-press татарских дублей на обеих кириллических раскладках — при Java-diff ровно в один файл-реестр и нетронутых shared east_slavic-файлах; глобус/цикл/пикер/персистентность — ноль нового кода (штатные механизмы форка)**

## Performance

- **Duration:** 8 min
- **Started:** 2026-07-18T09:30:54Z
- **Completed:** 2026-07-18T09:38:13Z
- **Tasks:** 4 of 5 (Task 5 = human-verify checkpoint, deferred — device unavailable)
- **Files modified:** 12 (6 created + 6 modified)

## Accomplishments

- LAYOUT-02 + ревью F1 как данные: 10 литеральных `latin:moreKeys` на rowkeys_tatar1–3 (у→ү, е→ё, н→ң, г→һ, х→һ, а→ә, о→ө, ж→җ, э→ә, ь→ъ) — одиночные кодпоинты, без `!text/`, заглавные не прописаны (MoreKeySpec auto-upcase); ё и ъ теперь достижимы
- LAYOUT-03 как данные: русская раскладка — собственный layout set `russian` (6 новых XML: rowkeys_russian1–3 с теми же 10 дублями, rows_russian БЕЗ пятого ряда, kbd_russian со switch showNumberRow, keyboard_layout_set_russian с общими символьными слоями); shared east_slavic-файлы be_BY/kk/ky/uk не задеты ни байтом
- SWITCH-01 в реестре: `LOCALE_TATAR="tt_RU"`, `LAYOUT_RUSSIAN="russian"`, `case LOCALE_RUSSIAN: addLayout(LAYOUT_RUSSIAN); break;` вынесен из east_slavic-группы (группа = ровно 4 локали); `getDefaultSubtypes` переписан на детерминированную тройку tt_RU→ru→en_US — MVP-хак фазы 2 и мёртвый английский fallback (F2) удалены
- «Татарча» — фиксированный эндоним subtype в Languages и пикере (locale_exception_keys + locale_name_tt_RU, паттерн Hinglish/Srpski)
- SWITCH-01-персистентность и SWITCH-02 (показ глобуса при >1 subtype, тап-цикл внутри IME, long-press пикер) — ноль нового кода: механизмы форка уже реализованы, закрываются device-UAT (deferred)
- Границы держатся механически: Java-diff фазы = ровно SubtypeLocaleUtils.java; diff по `*east_slavic*` и KeyboardTextsTable.java пуст; APK 1.92 МБ (бюджет ≤ 3 МБ); check-no-internet.sh зелёный после каждой задачи

## Task Commits

Each task was committed atomically:

1. **Task 1: Long-press дубли на татарской раскладке (10 литеральных moreKeys)** - `e321fc6` (feat)
2. **Task 2: Русская раскладка — собственный layout set russian (6 XML)** - `a0c6e05` (feat)
3. **Task 3: Реестр — tt_RU, ru→russian, детерминированная тройка** - `a37a124` (feat)
4. **Task 4: Display name «Татарча»** - `b19ce97` (feat)
5. **Task 5: on-device UAT** — NOT PASSED, deferred (см. § Deferred Verification)

**Plan metadata:** SUMMARY + STATE/ROADMAP commit (этот).

## Files Created/Modified

- `app/src/main/res/xml/rowkeys_tatar1.xml` - +moreKeys у→ү, е→ё, н→ң, г→һ, х→һ
- `app/src/main/res/xml/rowkeys_tatar2.xml` - +moreKeys а→ә, о→ө, ж→җ, э→ә
- `app/src/main/res/xml/rowkeys_tatar3.xml` - +moreKeys ь→ъ
- `app/src/main/res/xml/rowkeys_russian1.xml` - ЙЦУКЕН ряд 1 (11 клавиш) + 5 moreKeys
- `app/src/main/res/xml/rowkeys_russian2.xml` - ЙЦУКЕН ряд 2 (11) + 4 moreKeys
- `app/src/main/res/xml/rowkeys_russian3.xml` - ЙЦУКЕН ряд 3 (9) + moreKeys ь→ъ
- `app/src/main/res/xml/rows_russian.xml` - 3 буквенных ряда + row_qwerty4, без пятого ряда
- `app/src/main/res/xml/kbd_russian.xml` - switch showNumberRow: 4 ряда default / 5 с row_qwerty0
- `app/src/main/res/xml/keyboard_layout_set_russian.xml` - alphabet→kbd_russian, слои общие
- `app/src/main/java/.../latin/utils/SubtypeLocaleUtils.java` - tt_RU, LAYOUT_RUSSIAN, case ru с break, тройка дефолтов
- `app/src/main/res/values/donottranslate.xml` - tt_RU в locale_exception_keys
- `app/src/main/res/values/strings.xml` - locale_name_tt_RU = «Татарча»

## Decisions Made

- Все развилки — по рекомендациям ресерча (приняты планом): литеральные moreKeys (Q1), свой layout set russian (Q1-ru, вариант A), tt_RU без prefs-миграции (Q4), «Татарча» через исключения (Q5), детерминированная тройка (Q7), pref_enable_ime_switch остаётся false (Q3)
- Акцентные moreKeys донора и keyHintLabel-подсказки не переносим (Open Q1/Q2 — минимализм под ЦА)
- При переписывании getDefaultSubtypes удалены ставшие неиспользуемыми импорты (HashSet, Locale, LocaleUtils) — иначе dead code в единственном тронутом Java-файле

## Deviations from Plan

None - plan executed exactly as written. (Удаление трёх неиспользуемых импортов — прямое следствие предписанного планом удаления цикла матчинга; файл тот же, границы Java-дифа не расширены.)

## Deferred Verification

**Task 5 (checkpoint:human-verify) — deferred — human verification pending.** Устройство не подключено (`adb devices` пуст), по стандартному паттерну фаз 1–2 (standing decision пользователя: defer-and-accept). Дублируется в STATE.md Blockers. UAT-шаги при появлении устройства:

1. Чистая установка: `adb uninstall org.tatarkeyboard.ime.debug` (обязательно — dev-prefs с `tt:tatar` фазы 2 иначе не дадут примениться новым дефолтам, Pitfall 4) → `adb install app/build/outputs/apk/debug/app-debug.apk` → включить/выбрать «Tatar Keyboard (dev)»
2. Клавиатура открывается ТАТАРСКОЙ (пятый ряд сверху); настройки приложения → Languages: включены ровно три — «Татарча», «Русский» (или ICU-имя языка системы), «English (US)» (SWITCH-01)
3. Глобус виден слева от пробела; тап → русская раскладка: стандартная ЙЦУКЕН, БЕЗ пятого ряда; тап → английская QWERTY; тап → снова татарская (цикл tt→ru→en→tt, SWITCH-02)
4. Long-press глобуса → диалог выбора клавиатуры: наши три subtype отдельными пунктами + другие IME системы; выбор пункта переключает (SWITCH-02 long-press)
5. LAYOUT-02 на ТАТАРСКОЙ: long-press а/о/у/ж/н/х/э/г → popup с ә/ө/ү/җ/ң/һ/ә/һ, отпускание коммитит букву; long-press е→ё, ь→ъ (ревью F1 — ё и ъ теперь достижимы)
6. LAYOUT-02 на РУССКОЙ: те же 10 long-press дублей работают (а→ә … ь→ъ)
7. Регистр: shift → long-press а → popup показывает/коммитит Ә (авто-upcase MoreKeySpec)
8. Персистентность (SWITCH-01): переключиться на русскую, закрыть клавиатуру, свернуть приложение, `adb shell am force-stop org.tatarkeyboard.ime.debug`, снова открыть поле ввода → клавиатура открывается РУССКОЙ (resetSubtypeCycleOrder → prefs)
9. **Smoke-матрица SC5**: в (a) Telegram, (b) Chrome — адресная строка + поле формы/WebView (keyCode 229), (c) password-поле — переключить язык глобусом и напечатать по фразе на каждой раскладке («әни җир һава» / «привет, ёж, объём» через long-press ё/ъ / «hello»); без потерь/дублей букв
10. MIUI-пункт матрицы — если доступно устройство Xiaomi (иначе явно пометить как не покрыто)

**Bookkeeping (A3 плана):** при простановке галочки SWITCH-01 в REQUIREMENTS.md после UAT добавить аннотацию, что «система видит три subtype» выполнено через виртуальный subtype-реестр форка (Languages + пикер глобуса; ОС видит один generic subtype из method.xml) — принятая трактовка, ресерч Open Q3.

## Issues Encountered

None — сборка зелёная с первого прогона после каждой задачи; hooks не блокировали коммиты.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Subtype-слой готов для фазы 4 (механика shift/backspace/Enter): три раскладки резолвятся, реестр стабилен
- Блокер: on-device UAT фазы 3 (шаги выше) + отложенные проверки фаз 1–2 — прогнать одним заходом при первой возможности (устройство + GitHub)
- Риск A2 остаётся до UAT: невалидный moreKeys-спек падал бы при билде раскладки в рантайме, не в aapt2 — smoke обязателен

## Self-Check: PASSED

- 6 созданных XML *_russian + 6 изменённых файлов существуют на диске
- 4 атомарных коммита задач: e321fc6, a0c6e05, a37a124, b19ce97
- Acceptance-критерии Tasks 1–4 прогнаны: moreKeys-счёт 5/4/1 на обеих тройках rowkeys, <Key-счёт не изменился (11/11/9, extra=6), нет !text/ и акцентных символов, tt_RU/LAYOUT_RUSSIAN/case+break/тройка подтверждены grep'ом, east_slavic-группа = 4 case, MVP-вставка удалена, tt_RU в исключениях + «Татарча»
- Plan-level verification: assembleDebug exit 0; check-no-internet.sh exit 0; aapt2 видит все 6 xml/*russian*; Java-diff b0b4606..HEAD = ровно SubtypeLocaleUtils.java; diff по '*east_slavic*' и KeyboardTextsTable.java пуст
- Task 5 честно deferred (не помечен пройденным)

---
*Phase: 03-yazyki-i-pereklyuchenie*
*Completed: 2026-07-18 (local build scope; device UAT deferred)*
