# Review 02 — Татарская раскладка (plan 02-01)

**Date:** 2026-07-18
**Depth:** standard
**Diff base:** ed2cf12
**Files reviewed:** 7 new XML (`rowkeys_tatar_extra/1/2/3`, `rows_tatar`, `kbd_tatar`, `keyboard_layout_set_tatar`) + `SubtypeLocaleUtils.java`
**Reference material:** east_slavic donor chain (`rowkeys_east_slavic1–3.xml`, `rows_east_slavic.xml`, `kbd_east_slavic.xml`, `keyboard_layout_set_east_slavic.xml`), sakha precedent, `config.xml` 5row-fractions, `RichInputMethodManager.SubtypeList.reload`, `Subtype.java`, plan `02-01-PLAN.md`.

## Verdict

**PASS.** Реализация соответствует плану 02-01 и донорскому паттерну east_slavic. Все механические acceptance-критерии подтверждены чтением кода. Блокирующих находок нет; 3 наблюдения low-severity (все — осознанные решения или наследие MVP-scope, зафиксированы для фазы 3).

---

## 1. Key counts — PASS

| Файл | Ожидание | Факт | Содержимое |
|---|---|---|---|
| `rowkeys_tatar_extra.xml` | 6 | 6 | ә ө ү җ ң һ |
| `rowkeys_tatar1.xml` | 11 | 11 | й ц у к е н г ш щ з х |
| `rowkeys_tatar2.xml` | 11 | 11 | ф ы в а п р о л д ж э |
| `rowkeys_tatar3.xml` | 9 | 9 | я ч с м и т ь б ю |

Порядок ЙЦУКЕН побуквенно сверен с `<default>`-ветками east_slavic1–3 — совпадает полностью, включая позиции четырёх бывших `!text/`-клавиш:

- щ (`&#x0449;`) литералом вместо `!text/keyspec_east_slavic_row1_9` — позиция 9 ряда 1 ✓
- ы (`&#x044B;`) вместо `!text/keyspec_east_slavic_row2_2` — позиция 2 ряда 2 ✓
- э (`&#x044D;`) вместо `!text/keyspec_east_slavic_row2_11` — позиция 11 ряда 2 ✓
- и (`&#x0438;`) вместо `!text/keyspec_east_slavic_row3_5` — позиция 5 ряда 3 ✓

`grep '!text/' rowkeys_tatar*.xml` пуст — зависимость от `KeyboardTextsTable` отсутствует (Pitfall 2 обойдён конструктивно). `moreKeys`/`keyHintLabel` — 0 вхождений (LAYOUT-02 не утёк из фазы 3). Все 7 XML well-formed (xmllint OK).

## 2. Codepoints пятого ряда — PASS

Сверены entity-значения и комментарии в `rowkeys_tatar_extra.xml`:

| Буква | Ожидание | Факт |
|---|---|---|
| ә | U+04D9 | `&#x04D9;` ✓ |
| ө | U+04E9 | `&#x04E9;` ✓ |
| ү | U+04AF | `&#x04AF;` ✓ |
| җ | U+0497 | `&#x0497;` ✓ |
| ң | U+04A3 | `&#x04A3;` ✓ |
| һ | U+04BB | `&#x04BB;` ✓ |

Порядок алфавитный (locked decision №1), все — строчные с определёнными Unicode-парами верхнего регистра (U+04D8/04E8/04AE/0496/04A2/04BA) — штатный shift-upcase применим (runtime-подтверждение — отложенный Task 5).

## 3. Width math (`rows_tatar.xml`) — PASS

- Пятый ряд СВЕРХУ (первый `<Row>`): `keyWidth="16.667%p"` × 6 = 100.002% ≈ 100% ✓ (тот же приём округления, что у донора: 9.091 × 11 = 100.001)
- Ряды 1–2: `9.091%p` × 11 — идентично `rows_east_slavic.xml` ✓
- Ряд 3: default `8.711%p`, shift `10.8%p`, delete `fillRight` — точная копия структуры донора (10.8 + 9×8.711 + fillRight≈10.8 = 100) ✓
- Последний include — `row_qwerty4` (action row) — как у донора ✓

Структура shift/delete-ряда (Key keyStyle до/после include) побайтно повторяет донор — отклонений нет.

## 4. Высоты (`kbd_tatar.xml`) — PASS

Осознанное и корректное отклонение от донора (у east_slavic 4 буквенных ряда, у tatar — 5):

- `<default>`: 5 рядов (extra + 3 ЙЦУКЕН + action), `rowHeight="20%p"` → 5 × 20 = 100%. Донор в default-ветке вообще без 5row-фракций (4 ряда, дефолтная высота); tatar корректно берёт конфигурацию донорской *case*-ветки (5 рядов = 20%p + `config_key_vertical_gap_5row` + `config_key_bonus_height_5row`) — фракции существуют в `config.xml:71,74` ✓
- `<case showNumberRow="true">`: 6 рядов (row_qwerty0 + 5), `rowHeight="16.667%p"` → 6 × 16.667 ≈ 100%. При донорских 20%p сумма была бы 120% → clamp «row is too tall» (Pitfall 1) — закрыто ✓
- Обе ветки включают `key_styles_common`; case дополнительно `row_qwerty0` перед `rows_tatar` — совместимость с опцией числового ряда сохранена (locked decision №3) ✓

Замечание (не дефект): 5row-фракции vertical_gap/bonus_height рассчитаны upstream под 5 рядов; для 6-рядной ветки геометрия чуть плотнее расчётной. Это идентично тому, как ведёт себя донорская case-ветка + любой 5-рядный layout с number row в этом форке; точная посадка — предмет отложенного on-device Task 5 (assumption A1 в плане, уже зафиксировано в STATE.md Blockers).

## 5. Layout set (`keyboard_layout_set_tatar.xml`) — PASS

Диff против `keyboard_layout_set_east_slavic.xml` — ровно одна строка: `alphabet` → `@xml/kbd_tatar`. Все 6 Element на месте: `alphabet`, `symbols`→`kbd_symbols`, `symbolsShifted`→`kbd_symbols_shift`, `phone`, `phoneSymbols`, `number` — общие слои переиспользованы (LAYOUT-04) ✓.

**Note (info):** ни у донора, ни у sakha, ни у tatar нет элементов `alphabetShiftLocked`/`symbolsShifted`-вариантов сверх этих шести — «shift-варианты Element» в этом форке не декларируются per-layout (shift-состояния алфавита решаются движком, не layout set'ом). Полнота = паритет с донором ✓.

## 6. `SubtypeLocaleUtils.java` — PASS

Диff против ed2cf12 = ровно 4 добавления + 1 вставка, чужие строки не тронуты:

1. `LOCALE_TATAR = "tt"` (строка 116) — в блоке констант ✓
2. `LOCALE_TATAR,` в `sSupportedLocales` (строка 198, после SWAHILI, до TAMIL_INDIA — как соседние) ✓
3. `LAYOUT_TATAR = "tatar"` (строка 257) — совпадает с суффиксом `keyboard_layout_set_tatar.xml`, резолвинг по имени через `getIdentifier` работает ✓
4. `case LOCALE_TATAR: addLayout(LAYOUT_TATAR); break;` (строки 556–558) — **с break**, размещён между `LOCALE_SERBIAN_LATIN` (завершается break) и `LOCALE_TAMIL_INDIA`: в наш case никто не проваливается, и мы не проваливаемся дальше ✓
5. `getDefaultSubtypes`: `subtypes.add(0, getSubtypes(LOCALE_TATAR, resources).get(0));` — после цикла матчинга, до английского fallback ✓

**Sakha fall-through:** существующий upstream-баг (`case LOCALE_SAKHA` без break, строки 547–548) присутствовал в ed2cf12 в том же виде и НЕ затронут диффом — не унаследован и не «починен» попутно (diff-минимальность соблюдена, Pitfall 4 закрыт).

**Цикл матчинга не сломан:** вставка стоит после цикла `for (systemLocale …)`, `addedLocales` не мутируется. Английский fallback (`if subtypes.size() == 0`) недостижим — но это заведомо так по построению: `getSubtypes(LOCALE_TATAR).get(0)` всегда даёт элемент, список никогда не пуст. `.get(0)` безопасен: `case LOCALE_TATAR` в switch гарантирует непустой список для "tt". Fallback-ветка оставлена нетронутой — корректно (diff-минимальность; фаза 3 перестроит метод под SWITCH-01/02).

**Дубликат tt возможен?** Нет на практике: цикл добавляет только locale, заматченные системными; если системный locale — tt, дубль tt:tatar появился бы в списке дважды (индексы 0 и n). `Subtype.equals` по locale+layoutSet сделал бы их равными, но `SubtypeList` дубли переживает, а prefs при первом же изменении нормализуются. Риск принят планом явно (Task 4 action) — не финдинг.

**Единственный вызывающий:** `RichInputMethodManager.SubtypeList.reload` — использует `getDefaultSubtypes` только при пустых/битых prefs, `mCurrentSubtypeIndex = 0` → tt активен на чистой установке. Движок (`KeyboardLayoutSet`, `KeyboardBuilder`, `KeyboardRow`, `KeyboardTextsTable`, `method.xml`) — не тронут ✓.

## 7. Constraints — PASS

- **No INTERNET:** манифест без INTERNET-permission (grep пуст); диff не трогает манифест ✓
- **No new deps:** диff ограничен `res/xml/*` + один Java-файл + `.planning/*` ✓
- **No Apple assets:** только XML-данные и текст, ни шрифтов, ни иконок, ни звуков ✓
- **Diff-minimal vs upstream:** Java-дифф = 10 добавленных строк одного файла-реестра; ни одной изменённой/удалённой upstream-строки; XML — только новые файлы ✓
- **Раскладки = данные:** новая раскладка описана 7 XML; Java — только реестровые записи (задокументированное отклонение из CONTEXT/PLAN) ✓

## Findings

### F1 (low, deferred by design) — ё и ъ недоступны в раскладке
`rowkeys_tatar*` сознательно без `moreKeys`, поэтому русские ё (донор даёт через long-press е: `morekeys_cyrillic_ie`) и ъ (через long-press ь: `morekeys_cyrillic_soft_sign`) недоступны вовсе. Для татарского алфавита обе буквы нужны (заимствования: ёрт-производные написания, мягкий/твёрдый знаки в русских заимствованиях — ъ входит в официальный татарский кириллический алфавит 1939 г.). Это НЕ дефект данной фазы — long-press дубли явно зарезервированы за LAYOUT-02 (фаза 3, анти-паттерн-список плана), но при реализации LAYOUT-02 нужно не забыть ё и ъ, а не только пятый ряд. **Action:** учесть в scope LAYOUT-02.

### F2 (info) — недостижимый английский fallback в `getDefaultSubtypes`
После вставки `subtypes.add(0, …)` условие `subtypes.size() == 0` всегда ложно — мёртвый код до фазы 3. Оставлен сознательно ради diff-минимальности и отката; комментарий в коде это объясняет. Ничего не менять.

### F3 (info) — display name для locale "tt" может быть неполным
`Subtype.getName()` → `LocaleResourceUtils.getLocaleDisplayNameInSystemLocale("tt")` — на части устройств вернёт «татарский»/«Tatar» корректно (ICU знает tt), но без региона. Зафиксировано в плане как принятое допущение MVP («Locale "tt" без _RU»); `tt_RU` — фаза 3. Ничего не менять.

## Deferred verification (не закрываемо кодом)

- On-device UAT (Task 5: рендер высот 5/6 рядов, shift-регистр Ә Ө Ү Җ Ң Һ, smoke-матрица SC4) — отложен в STATE.md Blockers (устройства нет), статус честно отражён в 02-01-SUMMARY.md.
- `./gradlew assembleDebug` / `check-no-internet.sh` / aapt2-проверки прогонялись в verify задач согласно SUMMARY; в рамках ревью не перепрогонялись (standard depth, статическая сверка).
