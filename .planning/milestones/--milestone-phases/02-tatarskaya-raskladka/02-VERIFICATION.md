---
phase: 02-tatarskaya-raskladka
verified: 2026-07-18T00:00:00Z
status: passed
previous_status: human_needed
human_verification_deferred: true
deferred_accepted_by: user standing decision (autonomous run 2026-07-18, phase 1 «Принять и идти дальше»)
score: 6/7 must-haves verified (mechanical); 1 routed to human verification (on-device UAT, device unavailable)
re_verification: false
requirements_checked: [LAYOUT-01, LAYOUT-04, LAYOUT-05]
human_verification:
  - test: "Чистая установка (adb uninstall org.tatarkeyboard.ime.debug → adb install app-debug.apk) → клавиатура открывается ТАТАРСКОЙ: пятый ряд ә ө ү җ ң һ СВЕРХУ над ЙЦУКЕН"
    expected: "Активный subtype tt:tatar без ручного выбора языка; пятый ряд виден первым"
    why_human: "Рендер и активация по дефолту — только на устройстве; adb devices пуст (deferred по образцу фазы 1, принято пользователем)"
  - test: "Напечатать «әни өй үрдәк җир таң һава» + «щи, ыл, эш, ике» — все 37 букв тапом"
    expected: "Все буквы коммитятся; четыре литеральные клавиши (щ/ы/э/и) не пустые"
    why_human: "Фактический ввод символов — runtime-поведение InputConnection"
  - test: "Shift → пятый ряд показывает и вводит Ә Ө Ү Җ Ң Һ (assumption A3)"
    expected: "toUpperCase(Locale(\"tt\")) даёт заглавные Unicode-пары U+04D8/04E8/04AE/0496/04A2/04BA"
    why_human: "Регистр — runtime toUpperCase, не проверяем статически"
  - test: "?123 → слой цифр/символов; #+= → второй слой; АБВ → возврат к татарским буквам (LAYOUT-04 поведенчески)"
    expected: "Переходы туда-обратно без потери состояния"
    why_human: "KeyboardState-переключение — runtime"
  - test: "5 рядов (default) и 6 рядов (Number row ON) без обрезки нижнего ряда; adb logcat | grep -i \"too tall\" пуст (Pitfall 1/A1)"
    expected: "Обе конфигурации помещаются; clamp «row is too tall» не срабатывает"
    why_human: "Clamp-логика KeyboardRow проявляется только при рендере на реальном экране"
  - test: "SC4 smoke-матрица: «әни өй үрдәк җир таң һава» в (a) Telegram, (b) Chrome — адресная строка + поле формы/WebView (keyCode 229), (c) password-поле"
    expected: "Во всех трёх окружениях без потерянных/задвоенных букв; в password — точки маскировки на каждый тап"
    why_human: "Зоопарк InputConnection воспроизводится только в реальных редакторах"
---

# Phase 2 Verification: Татарская раскладка

**Phase goal:** Пользователь печатает по-татарски: стандартная ЙЦУКЕН + отдельный видимый пятый ряд `ә ө ү җ ң һ`; раскладки — данные, не код.

**Verified:** 2026-07-18 (live re-run: build, aapt2, grep, git diff)

## Observed Truths (mechanical, checked live)

| # | Truth (from 02-01-PLAN must_haves) | Status | Evidence |
|---|-------|--------|----------|
| 1 | 7 новых XML существуют, раскладка целиком данными, ноль `!text/`-рефов | ✓ VERIFIED | Все 7 файлов на диске; `grep -l '!text/' rowkeys_tatar*.xml` пуст; `grep -l moreKeys` пуст; счёт `<Key`: 6/11/11/9 |
| 2 | Пятый ряд ә ө ү җ ң һ алфавитно, сверху, 16.667%p; высоты default 5×20%p / showNumberRow 6×16.667%p на 5row-фракциях | ✓ VERIFIED | Порядок entity в rowkeys_tatar_extra: `&#x04D9; &#x04E9; &#x04AF; &#x0497; &#x04A3; &#x04BB;` ровно в этом порядке; rows_tatar: первый Row 16.667%p + rowkeys_tatar_extra, последний include row_qwerty4; kbd_tatar: default rowHeight 20%p, case showNumberRow rowHeight 16.667%p + row_qwerty0, обе ветки с config_key_vertical_gap_5row + config_key_bonus_height_5row |
| 3 | keyboard_layout_set_tatar: все 6 Element, symbols → общие kbd_symbols* (LAYOUT-04 структурно) | ✓ VERIFIED | alphabet=@xml/kbd_tatar; symbols=@xml/kbd_symbols; symbolsShifted=@xml/kbd_symbols_shift; phone/phoneSymbols/number=общие kbd_* |
| 4 | Реестр SubtypeLocaleUtils: LOCALE_TATAR="tt", sSupportedLocales, LAYOUT_TATAR="tatar", case с break, tt первым в getDefaultSubtypes | ✓ VERIFIED | Строки 116 / 198 / 257 / 556–558 (`case LOCALE_TATAR: addLayout(LAYOUT_TATAR); break;`) / 329 (`subtypes.add(0, getSubtypes(LOCALE_TATAR, resources).get(0))`) |
| 5 | Java-diff фазы = ровно один файл SubtypeLocaleUtils.java; движок/KeyboardTextsTable/method.xml не тронуты | ✓ VERIFIED | `git diff --name-only ed2cf12..HEAD -- '*.java' '*.kt'` → единственная строка SubtypeLocaleUtils.java; SAKHA-строки в diff отсутствуют (баг не тронут и не унаследован); KeyboardTextsTable.java, method.xml, KeyboardBuilder/KeyboardLayoutSet/KeyboardRow/Key.java — вне diff |
| 6 | assembleDebug зелёный; check-no-internet.sh зелёный; ресурсы *_tatar в APK; APK ≤ 3 МБ | ✓ VERIFIED | Перепрогнано live: `./gradlew assembleDebug` exit 0; check-no-internet.sh exit 0 (Level 1 манифест + Level 2 APK); aapt2 dump resources: все 7 xml/*tatar* в APK; APK 1 921 833 байт ≈ 1.83 МБ |
| 7 | On-device: 37 букв тапом, shift-заглавные, ?123/#+=, 5/6 рядов без обрезки, SC4 smoke-матрица | ⚠ HUMAN NEEDED | Deferred — `adb devices` пуст (проверено live). Чек-лист в frontmatter, 02-01-SUMMARY.md § Deferred Verification и STATE.md Blockers. НЕ помечено пройденным |

## Requirements Coverage (cross-reference PLAN ↔ REQUIREMENTS.md)

PLAN 02-01 frontmatter declares `requirements: [LAYOUT-01, LAYOUT-04, LAYOUT-05]` — identical to phase requirement IDs. All three exist in REQUIREMENTS.md, all mapped to Phase 2 in Traceability. No orphan or unmapped IDs.

| Requirement | Text | Mechanical status | Behavioral status |
|---|---|---|---|
| LAYOUT-01 | ЙЦУКЕН + видимый пятый ряд ә ө ү җ ң һ | ✓ Данные существуют, компилируются, в APK; активация tt-first в реестре | ⚠ Печать/рендер — on-device (deferred) |
| LAYOUT-04 | Слои ?123 и #+= с возвратом к буквам | ✓ Структурно: все 6 Element на общие kbd_symbols*/kbd_symbols_shift; переключатель приходит из row_qwerty4 + key_styles_common | ⚠ Переходы — on-device (deferred) |
| LAYOUT-05 | Раскладки данными; новая раскладка без изменения движка | ✓ ПОЛНОСТЬЮ (см. structural assessment ниже) | — (чисто структурное требование) |

### LAYOUT-05 structural assessment

Adding the next layout (e.g. латиница Zamanälif) requires:

1. **XML only for the layout itself:** rowkeys_* (literal keySpecs, no KeyboardTextsTable dependency — pattern proven by rowkeys_tatar1–3 replacing 4 `!text/` refs with literals), rows_*, kbd_* (showNumberRow switch with row-count-matched heights), keyboard_layout_set_* (resolved by name at runtime via `getIdentifier` — no code registration of the resource).
2. **Registry entries only in one file:** 2 constants + 1 array element + 1 `case` with `break` in SubtypeLocaleUtils.java. This is the documented, bounded deviation from "zero Java" fixed in 02-CONTEXT/02-RESEARCH/02-01-PLAN — the fork stores its selectable-locale registry in Java; the parsing/rendering engine is untouched (verified: zero diff on KeyboardBuilder/KeyboardLayoutSet/KeyboardRow/Key/KeyboardTextsTable/method.xml).

Verdict: LAYOUT-05 holds. The layout is data; the only Java touch is registry data, not engine logic.

## Prohibitions (from PLAN must_haves)

| Prohibition | Status |
|---|---|
| MUST NOT править движок раскладок и method.xml | ✓ UPHELD — git diff по этим файлам пуст за всю фазу |
| MUST NOT тянуть scope фазы 3 (moreKeys/long-press, ru/en, subtype-механики/глобус) | ✓ UPHELD — moreKeys 0 вхождений во всех rowkeys_tatar*; ru/en раскладок и глобус-правок в diff нет |

## Roadmap Success Criteria (Phase 2)

1. **На устройстве печатается татарский текст (LAYOUT-01)** — данные + активация готовы и в APK; печать = human item ⚠
2. **?123/#+= открываются и возвращают (LAYOUT-04)** — структурно подключено; переходы = human item ⚠
3. **Раскладка XML-данными, правка без изменения движка (LAYOUT-05)** — ✓ verified mechanically
4. **APK собирается; SC4 smoke-матрица (Telegram/Chrome-WebView/password)** — сборка ✓ (1.83 МБ, no-INTERNET); smoke-матрица = human item ⚠

## Score & Verdict

- **Mechanical must-haves:** 6/6 verified live (truths 1–6, both prohibitions, LAYOUT-05 fully).
- **On-device must-have (truth 7):** honestly deferred — device unattached (`adb devices` empty, confirmed during this verification). Enumerated as 6 human_verification items in frontmatter, mirroring 02-01-SUMMARY.md § Deferred Verification and STATE.md Blockers.

**Status: `human_needed`** — everything mechanically verifiable passes; LAYOUT-01/LAYOUT-04 behavioral proof and the SC4 smoke matrix require the on-device UAT checklist when a device is available. Per the user's standing decision (same pattern as phase 1), deferral is accepted and work continues; this report does NOT mark the on-device items as passed.

## Gaps / Notes

- No new gaps found beyond the known deferred UAT. Review 02-REVIEW.md (PASS, 0 blocking) noted F1 (ё/ъ отсутствуют — намеренно, войдут в LAYOUT-02 scope фазы 3), F2 (мёртвый английский fallback — намеренно, снимается в фазе 3), F3 (display name "tt" без региона — принятое MVP-допущение). None affect the phase goal.
- 02-VALIDATION.md frontmatter remains `status: draft / nyquist_compliant: false` and Per-Task Verification Map statuses remain ⬜ pending — bookkeeping lag only; the underlying verify commands were re-run live in this verification and pass. Not a goal blocker.
- Assumption A1 (посадка высот на реальных экранах) — closes with human item 5; fallback уже определён в плане (подбор фракций в XML, без Java).
