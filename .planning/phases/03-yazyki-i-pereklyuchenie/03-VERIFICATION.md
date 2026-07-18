---
phase: 03-yazyki-i-pereklyuchenie
verified: 2026-07-18
status: passed
previous_status: human_needed
human_verification_deferred: true
deferred_accepted_by: user standing decision (autonomous run 2026-07-18)
score: 7/8 must-haves verified (mechanical); 1 deferred to on-device UAT
requirements: [LAYOUT-02, LAYOUT-03, SWITCH-01, SWITCH-02]
diff_base: 4de188c
---

# Phase 3 Verification: Языки и переключение

**Phase goal:** Три языка как system subtypes (tt_RU / ru / en_US), глобус, long-press дубли татарских букв на обеих кириллических раскладках.

**Verdict: human_needed** — все механически проверяемые must-haves подтверждены живыми командами против кодовой базы (не по документам); on-device критерии (must-have #8) отложены по standing-паттерну фаз 1–2 (устройство недоступно; решение пользователя defer-and-accept). До прогона UAT требования LAYOUT-02/03 и SWITCH-01/02 не считаются полностью верифицированными.

## Must-Haves (from 03-01-PLAN.md)

### ✓ 1. rowkeys_tatar1–3: 10 литеральных latin:moreKeys — VERIFIED

Live grep: counts = 5/4/1 (tatar1/2/3), итого 10. Все значения — одиночные XML-entities, привязка к правильным клавишам подтверждена чтением файлов:

| Ряд | Маппинги (keySpec → moreKeys) |
|---|---|
| 1 | у→ү (0443→04AF), е→ё (0435→0451), н→ң (043D→04A3), г→һ (0433→04BB), х→һ (0445→04BB) |
| 2 | а→ә (0430→04D9), о→ө (043E→04E9), ж→җ (0436→0497), э→ә (044D→04D9) |
| 3 | ь→ъ (044C→044A) |

Ни одного `!text/`-рефа, ни одной прописанной заглавной (auto-upcase MoreKeySpec). `rowkeys_tatar_extra.xml`: moreKeys = 0, `<Key>` = 6 (нетронут). `<Key>`-счёт не изменился: 11/11/9.

### ✓ 2. Русская раскладка — 6 новых XML — VERIFIED

Все 6 файлов существуют; `rowkeys_russian1–3` **байт-идентичны** `rowkeys_tatar1–3` (diff пуст) → тот же ЙЦУКЕН и те же 10 дублей автоматически. `rows_russian.xml`: ровно 3 буквенных `<Row>` + include `row_qwerty4`, ноль ссылок на `rowkeys_tatar_extra` (пятого ряда НЕТ — текст LAYOUT-03); структурно идентичен `rows_east_slavic.xml` с точностью до имён rowkeys-include и заголовка лицензии (проверено sed-diff). `kbd_russian.xml`: `<default>` — голый 4-рядный `<Keyboard>`; `<case showNumberRow="true">` — 5row-фракции + `rowHeight="20%p"` + `row_qwerty0` (татарская 5-рядная математика не скопирована). `keyboard_layout_set_russian.xml`: ровно 6 Elements (alphabet→kbd_russian + symbols/symbolsShifted/phone/phoneSymbols/number общие).

### ✓ 3. Реестр SubtypeLocaleUtils — VERIFIED

- `LOCALE_TATAR = "tt_RU"` (:113); `LAYOUT_RUSSIAN = "russian"` (:248) — совпадает с суффиксом `keyboard_layout_set_russian.xml`
- East_slavic-группа = ровно 4 case (be_BY/kk/ky/uk → `LAYOUT_EAST_SLAVIC; break`, :463-468); сразу после — `case LOCALE_RUSSIAN: addLayout(LAYOUT_RUSSIAN); break;` (:469-471) — с break, вне fall-through (следующий case BULGARIAN недостижим по проваливанию)
- `case LOCALE_TATAR: addLayout(LAYOUT_TATAR); break;` (:537-539) на месте
- `getDefaultSubtypes` (:303-314) — детерминированная тройка `getDefaultSubtype(LOCALE_TATAR / LOCALE_RUSSIAN / LOCALE_ENGLISH_UNITED_STATES)`; MVP-вставка `subtypes.add(0,` отсутствует (grep пуст); цикла по системным локалям и английского fallback нет (F2 закрыт)

### ✓ 4. Display name «Татарча» — VERIFIED

`donottranslate.xml:31` — `<item>tt_RU</item>` шестым элементом `locale_exception_keys`; `locale_displayed_in_root_locale` не тронут. `strings.xml:70` — `locale_name_tt_RU` = «Татарча», `translatable="false"`.

### ✓ 5. SWITCH-01/02 без нового кода — цитируемые механизмы существуют — VERIFIED (static)

Заявка «ноль нового кода» подтверждена: цепочки в нетронутом коде форка реально существуют:
- Показ глобуса при >1 subtype: `RichInputMethodManager.hasMultipleEnabledSubtypes()` (:434, использования :493, :505); глобус в `row_qwerty4` → `key_space_5kw` (`languageSwitchKeyEnabled` cases, `languageSwitchKeyStyle`)
- Тап-цикл: `InputLogic` `case Constants.CODE_LANGUAGE_SWITCH → handleLanguageSwitchKey()` (:212-214) → `LatinIME.switchToNextSubtype()` (:712)
- Long-press пикер: `PointerTracker.java:765` (long-press на CODE_LANGUAGE_SWITCH) → `LatinIME.showInputMethodPicker()` (:643) → `RichInputMethodManager.showSubtypePicker` (:540, AlertDialog)
- Персистентность: `resetSubtypeCycleOrder()` (:307-314, rotate + `mCurrentSubtypeIndex=0`) → prefs → reload

Runtime-поведение этих цепочек — on-device (must-have #8).

### ✓ 6. Границы диффа — VERIFIED

`git diff --name-only 4de188c..HEAD -- '*.java' '*.kt'` = ровно одна строка: `SubtypeLocaleUtils.java`. Diff по `app/src/main/res/xml/*east_slavic*` и `KeyboardTextsTable.java` — пуст (be_BY/kk/ky/uk не задеты ни байтом; T2/T3 закрыты). Полный диф фазы (без `.planning/`) = ровно 12 заявленных файлов.

### ✓ 7. Сборка, no-internet, ресурсы в APK — VERIFIED

`./gradlew assembleDebug` exit 0. `scripts/check-no-internet.sh` — Level 1 (манифест) и Level 2 (aapt2 по APK) OK. APK = 1 923 243 байт (1.92 МБ ≤ 3 МБ). `aapt2 dump resources`: все 6 `xml/*russian*` ресурсов присутствуют (keyboard_layout_set_russian, kbd_russian, rows_russian, rowkeys_russian1–3).

### ⏸ 8. On-device UAT (Task 5) — DEFERRED (human verification pending)

Устройство недоступно (adb devices пуст) — по standing-решению пользователя (фазы 1–2): accept-and-continue. Честно зафиксировано: SUMMARY § Deferred Verification + STATE.md Blockers (Phase 3 запись присутствует, проверено). НЕ помечен пройденным. Пункты при появлении устройства (полные шаги — 03-01-SUMMARY.md):

1. Чистая установка (`adb uninstall` обязателен — dev-prefs `tt:tatar` фазы 2 маскируют новую тройку)
2. Открывается татарской; Languages: ровно три — «Татарча», «Русский»/ICU, «English (US)» (SWITCH-01)
3. Тап глобуса циклит tt→ru→en→tt; русская — ЙЦУКЕН без пятого ряда; английская — QWERTY (LAYOUT-03, SWITCH-02)
4. Long-press глобуса → пикер: три subtype + другие IME (SWITCH-02)
5. 10 long-press дублей на ТАТАРСКОЙ, вкл. е→ё, ь→ъ (LAYOUT-02 + F1)
6. Те же 10 дублей на РУССКОЙ (LAYOUT-02)
7. Shift + long-press а → Ә (auto-upcase)
8. Персистентность после force-stop (SWITCH-01)
9. Smoke-матрица SC5: Telegram / Chrome-WebView (keyCode 229) / password
10. MIUI — при наличии Xiaomi (иначе пометить как не покрыто)

Риск A2 плана (невалидный moreKeys-спек падает при билде раскладки в рантайме, не в aapt2) остаётся открытым до п. 5–6.

## Prohibitions (from plan)

| Prohibition | Status |
|---|---|
| MUST NOT править shared east_slavic + KeyboardTextsTable.java | ✓ HELD — diff пуст (механически) |
| MUST NOT расширять Java-диф / трогать механику переключения | ✓ HELD — Java-diff = 1 файл; RichInputMethodManager/LatinIME/InputLogic/PointerTracker нетронуты |
| MUST NOT чужой scope (инверсия пар, акцентные moreKeys, keyHintLabel, method.xml, словари) | ✓ HELD — ровно 10 moreKeys без лишних; акцентных символов (е́ ѣ ў ґ є і) нет; method.xml вне диффа |

## Requirements Cross-Reference

PLAN frontmatter `requirements: [LAYOUT-02, LAYOUT-03, SWITCH-01, SWITCH-02]` — совпадает с ROADMAP Phase 3 и REQUIREMENTS.md Traceability (все четыре → Phase 3, Pending). Соответствие построчно:

| Req | Текст | Механическая часть | On-device часть |
|---|---|---|---|
| LAYOUT-02 | Long-press дубли на обеих раскладках | ✓ 10 moreKeys × 2 раскладки как данные | ⏸ popup/commit/upcase в рантайме (UAT 5–7) |
| LAYOUT-03 | Русская ЙЦУКЕН + английская QWERTY | ✓ layout set russian данными; en_US qwerty штатный | ⏸ резолвинг/рендер (UAT 3) |
| SWITCH-01 | Три subtype, запоминание активного | ✓ реестр: детерминированная тройка; «Татарча» | ⏸ Languages, персистентность (UAT 2, 8) |
| SWITCH-02 | Глобус: тап-цикл + long-press пикер | ✓ штатные код-пути форка существуют (см. #5) | ⏸ цикл/пикер вживую (UAT 3–4) |

**Галочки в REQUIREMENTS.md корректно НЕ проставлены** (все четыре — `[ ]`, Traceability = Pending): требования закрываются только после UAT. Это согласуется с SUMMARY `requirements-completed: []`.

**Bookkeeping A3 (перенесено, не потеряно):** SUMMARY (frontmatter-комментарий + § Deferred Verification) и STATE.md Blockers несут инструкцию: при простановке SWITCH-01 после UAT аннотировать в REQUIREMENTS.md, что «система видит три subtype» выполнено через виртуальный subtype-реестр форка (Languages + пикер глобуса; ОС видит один generic subtype из method.xml) — принятая трактовка (ресерч Open Q3 / A3 плана), не пробел. В REQUIREMENTS.md аннотация сейчас отсутствует — это правильно (проставляется вместе с галочкой).

## Consistency Notes

- REVIEW 03: PASS, 0 blockers / 0 warnings / 5 low-observations (потеря стоковых AOSP-дублей ru — осознанный минимализм; keylabel_to_alpha "ABC" для tt_RU — унаследовано из фазы 2, backlog; sakha-баг без break — pre-existing upstream, вне диффа). Наблюдения не блокируют цель фазы.
- VALIDATION.md остался в статусе draft (`nyquist_compliant: false`, sign-off pending, per-task статусы ⬜) — бухгалтерский разрыв: фактические проверки T1–T4 прогнаны и зелёные (задокументированы в SUMMARY coverage со status: pass и переподтверждены здесь). Не влияет на верификацию кода.
- Diff base: PLAN использует `b0b4606`, REVIEW и эта верификация — `4de188c`; результат идентичен (Java-diff = 1 файл, east_slavic пуст).

## Verdict Rationale

Все 7 механически проверяемых must-haves подтверждены живыми командами (grep/diff/git/gradlew/aapt2) против рабочей копии. Ни один документ не расходится с кодом. Must-have #8 (on-device) отложен честно и по standing-паттерну — статус **human_needed** до прогона 10-пунктного UAT; после подтверждения пользователем (или accepted-deferred решения оркестратора) фаза закрывается, галочки LAYOUT-02/03, SWITCH-01/02 проставляются в REQUIREMENTS.md с аннотацией A3 к SWITCH-01.
