# Phase 2: Татарская раскладка — Research

**Researched:** 2026-07-18
**Domain:** Раскладочный слой форка Simple Keyboard (AOSP LatinIME-style keyboard XML: kbd_/rows_/rowkeys_/keyboard_layout_set + внутренний реестр subtype в Java)
**Confidence:** HIGH (всё проверено по реальным исходникам форка, не по памяти AOSP)

## Summary

Форк Simple Keyboard (rkkr) строит раскладки классической цепочкой AOSP: `keyboard_layout_set_<name>.xml` (перечень Element: alphabet/symbols/…) → `kbd_<name>.xml` (`<switch>` по `showNumberRow`) → `rows_<name>.xml` (структура рядов и ширины) → `rowkeys_<name>N.xml` (клавиши). Резолвинг имени layout set — по строке в рантайме через `Resources.getIdentifier("keyboard_layout_set_" + layoutSetName, ...)` (`KeyboardLayoutSet.Builder.setSubtype`/`getXmlId`), поэтому НОВЫЙ layout set подхватывается по имени без правки Java. Это подтверждает «раскладка = данные» на уровне layout set.

**НО есть одна развилка, ломающая наивную гипотезу CONTEXT про method.xml.** Форк НЕ использует системные Android-subtype из `method.xml` для выбора раскладки. `method.xml` содержит один generic subtype только для регистрации IME в системе. Реальный выбор раскладки идёт через ВНУТРЕННИЙ реестр «виртуальных subtype» в `SubtypeLocaleUtils.java` (жёстко закодированный `sSupportedLocales[]` + `switch(mLocale)` в `SubtypeBuilder.getSubtypes()`), а активная раскладка хранится в prefs (`pref_enabled_subtypes`, формат `locale:layoutSet;…`) и грузится `SubtypePreferenceUtils.createSubtypesFromPref` → `SubtypeLocaleUtils.getSubtype(locale, layoutSet, res)`. **Раскладка, которой нет в этом реестре, не может быть ни выбрана, ни восстановлена из prefs** (`getSubtype` вернёт null, т.к. `switch` не знает locale). Значит, чтобы татарская раскладка стала выбираемой и активной, требуется минимальная правка Java-реестра (регистрация locale `tt`/`tt_RU` + layout set `tatar`). Это единственный обязательный код-тач фазы; движок отрисовки/парсинга не трогается.

Второй риск — таблица текстов `KeyboardTextsTable`: рефы `!text/keyspec_east_slavic_*` и `!text/morekeys_cyrillic_*` в `rowkeys_east_slavic*` резолвятся ПОЛОКАЛЬНО. Для неизвестного locale (`tt`) отдаётся `TEXTS_DEFAULT`, где `keyspec_east_slavic_row1_9/row2_2/row2_11/row3_5` = `EMPTY` (пустая строка!) → если включить существующие `rowkeys_east_slavic*` под locale `tt`, четыре клавиши (щ/ў, ы, э, и) станут пустыми. Решается тривиально: либо (A) зарегистрировать `"tt", TEXTS_ru` в `LOCALES_AND_TEXTS` и переиспользовать `rowkeys_east_slavic1–3` через include, либо (B) написать свои `rowkeys_tatar1–3` с литеральными кодпоинтами (без `!text/` рефов).

**Primary recommendation:** kbd_tatar/rows_tatar/keyboard_layout_set_tatar по образцу east_slavic (для DEFAULT-случая — с 5-рядной высотной конфигурацией, как в `showNumberRow`-ветке east_slavic); пятый ряд `ә ө ү җ ң һ` сверху, 6 клавиш × 16.667%p (полная ширина). Плюс ~5-строчная регистрация `tt` в `SubtypeLocaleUtils` и `"tt", TEXTS_ru` в `KeyboardTextsTable` — зафиксировать как согласованное отклонение от «ноль Java» (это записи в реестре данных, а не логика движка).

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
| # | Решение | Выбор | Обоснование |
|---|---------|-------|-------------|
| 1 | Порядок пятого ряда | **Алфавитный: `ә ө ү җ ң һ`** | Мнемоника алфавита, легче найти глазами (ресерч 05 §4.5); финальная проверка порядка — юзер-тест после MVP |
| 2 | Позиция пятого ряда | **Сверху, над `й ц у к е н…`** | Классика «extra row», видимость, ЙЦУКЕН-геометрия нетронута |
| 3 | Постоянный цифровой ряд | **Нет — цифры в `?123`** | Компактность на бюджетных экранах; showNumberRow форка остаётся как опция и должен продолжать работать (5-й ряд + цифровой ряд совместимы) |

**Requirements:** LAYOUT-01, LAYOUT-04, LAYOUT-05.

### Claude's Discretion
- Механика подключения пятого ряда (extra Row в rows_tatar.xml vs include-шаблон) — по образцу row_qwerty0 (цифровой ряд).
- Точная высота/ширины пятого ряда — в рамках существующих атрибутов XML (rowHeight, keyWidth, verticalGap), без правок Java-движка. Если совсем без правок кода не выйдет — минимальный Kotlin/Java-туч допустим, но это deviation, зафиксировать.
- Взаимодействие пятого ряда с showNumberRow=true (оба ряда сверху) — рабочее, не ломающееся; полировка не требуется.
- Имена файлов/layout set id.

### Deferred Ideas (OUT OF SCOPE)
- Порядок клавиш пятого ряда — юзер-тест после MVP.
- Long-press дубли (LAYOUT-02), ru/en раскладки (LAYOUT-03), subtypes/глобус (SWITCH-01/02) — фаза 3.
- Уменьшенная высота 5-го ряда как «iOS-полировка» — если потребует правок движка, отложить до фаз 6–7.
- Любые визуальные изменения в стиле iOS — фазы 6–7. Словари/подсказки — позже.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| LAYOUT-01 | Татарская раскладка: стандартная ЙЦУКЕН + отдельный видимый пятый ряд `ә ө ү җ ң һ`, все буквы вводятся тапом | Файлы `kbd_tatar.xml`/`rows_tatar.xml`/`rowkeys_tatar_extra.xml` по образцу east_slavic + пятый ряд как в `showNumberRow`-ветке (row_qwerty0 precedent). Пятый ряд = 6 клавиш литеральными кодпоинтами (§ Code Examples). Ввод тапом обеспечивается штатным `keySpec` без moreKeys. |
| LAYOUT-04 | Слои `?123` и `#+=` открываются и возвращают к буквам | `keyboard_layout_set_tatar.xml` должен объявить Element `symbols`/`symbolsShifted`/`phone`/`phoneSymbols`/`number` = те же `@xml/kbd_symbols` и пр., что и east_slavic. Переключение layout-set-agnostic: клавиши `!text/keylabel_to_symbol\|!code/key_switch_alpha_symbol` (в `key_styles_common`), `keylabel_to_symbol`="?123" и `keylabel_to_alpha`="ABC" есть в `TEXTS_DEFAULT` → работает из коробки. |
| LAYOUT-05 | Раскладка описана XML-данными; правка XML меняет раскладку без правок движка; формат допускает латиницу Zamanälif позже | На уровне layout-set-данных — да (резолвинг по имени в рантайме). Оговорка: РЕГИСТРАЦИЯ нового locale требует одной записи в `SubtypeLocaleUtils` (реестр, не движок). Латиница добавляется тем же паттерном (новый layout set + запись в реестре). Зафиксировано как отклонение. |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Структура раскладки (ряды/клавиши/ширины) | Resource XML (`res/xml/`) | — | Форк парсит kbd/rows/rowkeys декларативно; движок не хардкодит раскладки |
| Резолвинг layout set по имени | Java (`KeyboardLayoutSet.Builder`) | Resource | `getIdentifier("keyboard_layout_set_"+name)` — новое имя работает без правки Java |
| Регистрация выбираемого locale/layout | Java-реестр (`SubtypeLocaleUtils`) | Prefs | Виртуальные subtype хардкодятся в `switch`; без записи раскладка невыбираема |
| Полокальные символы клавиш (`!text/…`) | Java-таблица (`KeyboardTextsTable`) | Resource | `keyspec_east_slavic_*` резолвятся по locale; DEFAULT для них пуст |
| Регистр (shift → заглавные) | Java (`Key.needsToUpcase` + `StringUtils.toUpperCase(locale)`) | — | Кириллические пары U+04D8/04D9 и т.д. поднимаются штатным `toUpperCase`, спец-locale не нужен |
| Переключение `?123`/`#+=`/alpha | Resource (`kbd_symbols*`) + Java (`KeyboardState`) | — | Layout-set-agnostic: общий `kbd_symbols`, коды `key_switch_alpha_symbol` |

## Standard Stack

Не применимо в классическом смысле — фаза не добавляет внешних зависимостей. «Стек» здесь = существующие механизмы форка, которые следует переиспользовать.

### Core (переиспользуемые механизмы форка)
| Механизм | Где | Назначение | Почему это правильный путь |
|----------|-----|------------|----------------------------|
| KeyboardLayoutSet XML | `res/xml/keyboard_layout_set_*.xml` | Объявление Element (alphabet/symbols/…) | Резолвинг по имени в рантайме `[VERIFIED: KeyboardLayoutSet.java:239-275]` |
| kbd_ switch по showNumberRow | `res/xml/kbd_*.xml` | Выбор 4- vs 5-рядной высотной конфигурации | Точный precedent для «доп. верхнего ряда» `[VERIFIED: kbd_east_slavic.xml]` |
| rows_ merge | `res/xml/rows_*.xml` | Ряды + ширины + include rowkeys | Любое число `<Row>` допускается парсером `[VERIFIED: KeyboardBuilder.parseKeyboardContent]` |
| rowkeys_ merge | `res/xml/rowkeys_*.xml` | Клавиши (keySpec литеральный или `!text/`) | sakha/east_slavic — прямые прецеденты для кириллицы `[VERIFIED: rowkeys_sakha1.xml]` |
| 5-row height config | `res/values/config.xml` fractions | verticalGap/bonusHeight/rowHeight для 5 рядов | Готовые `config_key_*_5row` `[VERIFIED: config.xml:66-79]` |

### Alternatives Considered
| Вместо | Можно | Компромисс |
|--------|-------|-----------|
| Регистрация `tt` в `SubtypeLocaleUtils` (правка Java) | Переиспользовать locale `sah`/`ru` с layout `tatar` | Ломает семантику locale (подсказки/upcase), не даёт честный `tt`; отклонено |
| `"tt", TEXTS_ru` в KeyboardTextsTable | Свои `rowkeys_tatar1–3` с литеральными кодпоинтами (без `!text/`) | Оба валидны; литералы независимее и нагляднее, но дублируют раскладку east_slavic. См. Open Questions |
| Пятый ряд как отдельный `<Row>` в rows_tatar | Include-шаблон `row_tatar_extra` (как row_qwerty0) | Include чище если ряд переиспользуется; для одной раскладки — inline `<Row>` проще |

**Installation:** Нет пакетов. Только новые XML-ресурсы + минимальные записи в двух Java-реестрах.

## Package Legitimacy Audit

Не применимо — фаза не устанавливает внешних пакетов (чистые XML-ресурсы + правки существующих Java-файлов форка). Ограничение CLAUDE.md «без разрешения INTERNET» сохраняется: новых зависимостей нет.

## Architecture Patterns

### System Architecture Diagram

```
Пользователь выбирает язык в настройках ИЛИ первый запуск (getDefaultSubtypes)
        │
        ▼
[Prefs: pref_enabled_subtypes = "tt_RU:tatar;…"]
        │  SubtypePreferenceUtils.createSubtypesFromPref
        ▼
[SubtypeLocaleUtils.getSubtype("tt_RU","tatar")]  ← ТРЕБУЕТ записи в switch(mLocale)
        │  (возвращает Subtype или null если locale неизвестен)
        ▼
[RichInputMethodManager.getCurrentSubtype()]  →  Subtype{locale=tt_RU, layoutSet=tatar}
        │
        ▼  KeyboardSwitcher → KeyboardLayoutSet.Builder.setSubtype(subtype)
[mKeyboardLayoutSetName = "keyboard_layout_set_" + "tatar"]
        │  getIdentifier() → resId
        ▼
[parse keyboard_layout_set_tatar.xml]  →  Element alphabet=@xml/kbd_tatar, symbols=@xml/kbd_symbols, …
        │  getKeyboard(ELEMENT_ALPHABET)
        ▼
[KeyboardBuilder.load(kbd_tatar.xml, id)]
        │  <switch> showNumberRow?
        ├── default(false): 5 letter rows (extra + ЙЦУКЕН×3 + action)
        └── case(true):     number row + 5 letter rows (обе сверху совместимы)
        │  include rows_tatar → include rowkeys_tatar_extra + rowkeys(й ц у…)
        ▼
[KeyboardTextsSet.setLocale(tt)]  →  KeyboardTextsTable.getTextsTable(tt)
        │  tt нет в карте → TEXTS_DEFAULT  ← ЗДЕСЬ keyspec_east_slavic_* = EMPTY (риск!)
        ▼
[Отрисованная клавиатура]   →   тап по клавише → InputConnection.commitText (кодпоинт)
```

### Recommended Project Structure
```
app/src/main/res/xml/
├── keyboard_layout_set_tatar.xml   # НОВЫЙ: копия east_slavic, elementKeyboard alphabet=@xml/kbd_tatar
├── kbd_tatar.xml                   # НОВЫЙ: <switch> showNumberRow; в обеих ветках 5-row height config
├── rows_tatar.xml                  # НОВЫЙ: extra Row + include rowkeys ЙЦУКЕН + row_qwerty4
├── rowkeys_tatar_extra.xml         # НОВЫЙ: пятый ряд ә ө ү җ ң һ (6 клавиш, литеральные кодпоинты)
├── rowkeys_tatar1.xml / 2 / 3      # НОВЫЙ ЛИБО reuse rowkeys_east_slavic1–3 (см. Open Questions)
app/src/main/java/.../latin/utils/
└── SubtypeLocaleUtils.java         # ПРАВКА: +LOCALE_TATAR, +LAYOUT_TATAR, +case в switch, +в sSupportedLocales
app/src/main/java/.../keyboard/internal/
└── KeyboardTextsTable.java         # ПРАВКА (если reuse east_slavic rowkeys): +"tt", TEXTS_ru в LOCALES_AND_TEXTS
```

### Pattern 1: kbd_ switch с 5-рядной высотной конфигурацией
**What:** `kbd_<name>.xml` оборачивает Keyboard в `<switch>` по `showNumberRow`. У east_slavic 5-рядная (bonusHeight/verticalGap_5row/rowHeight=20%p) конфигурация применяется ТОЛЬКО в `case showNumberRow=true`. Для татарской раскладки пятый ряд есть ВСЕГДА, поэтому 5-рядная конфигурация нужна и в `<default>`.
**When to use:** Всегда для этой фазы (базовая структура kbd_tatar).
**Example:**
```xml
<!-- Source: kbd_east_slavic.xml (адаптировано: 5-row config в обеих ветках) -->
<switch xmlns:latin="http://schemas.android.com/apk/res-auto">
    <case latin:showNumberRow="true">
        <Keyboard
            latin:verticalGap="@fraction/config_key_vertical_gap_5row"
            latin:bonusHeight="@fraction/config_key_bonus_height_5row"
            latin:rowHeight="20%p">      <!-- ВНИМАНИЕ: 6 рядов → см. Pitfall 1 -->
            <include latin:keyboardLayout="@xml/key_styles_common" />
            <include latin:keyboardLayout="@xml/row_qwerty0" />
            <include latin:keyboardLayout="@xml/rows_tatar" />  <!-- rows_tatar сам содержит extra row + 3 ряда + action -->
        </Keyboard>
    </case>
    <default>
        <Keyboard
            latin:verticalGap="@fraction/config_key_vertical_gap_5row"
            latin:bonusHeight="@fraction/config_key_bonus_height_5row"
            latin:rowHeight="20%p">
            <include latin:keyboardLayout="@xml/key_styles_common" />
            <include latin:keyboardLayout="@xml/rows_tatar" />
        </Keyboard>
    </default>
</switch>
```

### Pattern 2: rows_ с пятым рядом сверху
**What:** `rows_tatar.xml` (merge) добавляет extra `<Row>` ПЕРЕД тремя рядами ЙЦУКЕН, затем `row_qwerty4` (action row). Каждый `<Row>` задаёт свою `keyWidth`.
**When to use:** Реализация LAYOUT-01 (видимый пятый ряд).
**Example:**
```xml
<!-- Source: rows_east_slavic.xml + rows_sakha.xml (адаптировано) -->
<merge xmlns:latin="http://schemas.android.com/apk/res-auto">
    <Row latin:keyWidth="16.667%p">           <!-- 6 клавиш × 16.667 ≈ 100% -->
        <include latin:keyboardLayout="@xml/rowkeys_tatar_extra" />
    </Row>
    <Row latin:keyWidth="9.091%p">             <!-- 11 клавиш ЙЦУКЕН -->
        <include latin:keyboardLayout="@xml/rowkeys_east_slavic1" />  <!-- reuse ИЛИ rowkeys_tatar1 -->
    </Row>
    <Row latin:keyWidth="9.091%p">
        <include latin:keyboardLayout="@xml/rowkeys_east_slavic2" />
    </Row>
    <Row latin:keyWidth="8.711%p">
        <Key latin:keyStyle="shiftKeyStyle" latin:keyWidth="10.8%p" />
        <include latin:keyboardLayout="@xml/rowkeys_east_slavic3" />
        <Key latin:keyStyle="deleteKeyStyle" latin:keyWidth="fillRight" />
    </Row>
    <include latin:keyboardLayout="@xml/row_qwerty4" />   <!-- ?123, comma, space, period, enter -->
</merge>
```

### Pattern 3: rowkeys литеральными кодпоинтами (пятый ряд)
**What:** Клавиши задаются `keySpec="&#xUUUU;"`. Регистр НЕ пишется в XML — движок сам поднимает через `Key.needsToUpcase` в shifted-состоянии (`StringUtils.toUpperCase(locale)`), кириллические пары U+04D8/04D9 и т.д. обрабатываются штатно.
**When to use:** Пятый ряд (нет `!text/` рефов → не зависит от KeyboardTextsTable → нет DEFAULT-ловушки).
**Example:** см. § Code Examples.

### Anti-Patterns to Avoid
- **Опора на method.xml subtype для выбора раскладки:** в этом форке layout выбирается через `SubtypeLocaleUtils`-реестр + prefs, НЕ через системный subtype. Правка `method.xml` (добавление subtype с `KeyboardLayoutSet=tatar`) НИ НА ЧТО не влияет для выбора раскладки. `[VERIFIED: RichInputMethodManager.SubtypeList.reload + SubtypePreferenceUtils]`
- **Включение `rowkeys_east_slavic*` под locale без записи в KeyboardTextsTable:** `!text/keyspec_east_slavic_row1_9/row2_2/row2_11/row3_5` в `TEXTS_DEFAULT` = EMPTY → 4 клавиши (щ, ы, э, и) окажутся пустыми/без кода. `[VERIFIED: KeyboardTextsTable.java TEXTS_DEFAULT + getTextsTable fallback]`
- **rowHeight=20%p при 6 рядах:** 20%×6=120% высоты → нижние ряды «сжимаются» по clamp-логике `KeyboardRow` (лог «row is too tall»). Нужно `~16.667%p` или полагаться на bonusHeight. См. Pitfall 1.

## Don't Hand-Roll

| Проблема | Не строить | Использовать | Почему |
|----------|-----------|--------------|--------|
| Резолвинг раскладки по имени | Свой маппинг name→resId | `KeyboardLayoutSet.Builder.getXmlId` (`getIdentifier`) | Уже есть, работает по строке `[VERIFIED]` |
| Подъём регистра кириллицы | Пары uppercase в XML/коде | `Key.needsToUpcase` + `StringUtils.toUpperCase(locale)` | Unicode-пары U+04D8/04D9… штатны, спец-locale не нужен `[VERIFIED: StringUtils.java:228-238]` |
| Слои ?123/#+= и возврат | Своя логика переключения | Общие `kbd_symbols`/`kbd_symbols_shift` + `key_switch_alpha_symbol` | Layout-set-agnostic; TEXTS_DEFAULT содержит `?123`/`ABC` `[VERIFIED]` |
| Высота 5-рядной клавиатуры | Свой расчёт высоты | `config_key_vertical_gap_5row` + `config_key_bonus_height_5row` | Готовые fractions под 5 рядов `[VERIFIED: config.xml]` |
| Число рядов | Хардкод «4 ряда» | Парсер принимает любое число `<Row>` | `KeyboardBuilder` итерирует Row без лимита `[VERIFIED]` |

**Key insight:** Почти всё для этой фазы — данные. Единственное, что нельзя сделать данными в этом форке, — сделать новый locale ВЫБИРАЕМЫМ: реестр `SubtypeLocaleUtils` хардкоден. Это 5–7 строк, не логика.

## Runtime State Inventory

Фаза добавляет НОВУЮ раскладку (greenfield для слоя раскладок), не переименовывает существующее. Инвентаризация на предмет побочного runtime-состояния:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | `pref_enabled_subtypes` (DeviceProtectedStorage prefs) хранит выбранные subtype строкой `locale:layoutSet`. Пока `tt:tatar` там нет — раскладка не активна | Чтобы сделать активной по умолчанию для MVP: либо добавить `tt` в `getDefaultSubtypes` fallback, либо задать через первый запуск. Планировщику решить механизм активации (см. Open Questions Q3) |
| Live service config | None — оффлайн IME, внешних сервисов нет (проверено: нет разрешения INTERNET, CLAUDE.md) | — |
| OS-registered state | IME зарегистрирован через `method.xml` (один generic subtype). Новая раскладка НЕ требует нового системного subtype (форк не использует их для выбора) | Ничего; `method.xml` не трогаем для выбора раскладки |
| Secrets/env vars | None — раскладочный слой секретов не касается | — |
| Build artifacts | Новые XML → aapt перегенерит `R.xml.*` и `R.fraction.*` автоматически при сборке. Существующие `keyboard_layout_set_qwerty` и пр. — референс для `getResourcePackageName` (не ломается) | Полная сборка после добавления ресурсов |

**Нового live/секрет/OS-состояния фаза не создаёт** — verified по отсутствию сетевых компонентов и по тому, что выбор раскладки живёт в локальных prefs + Java-реестре.

## Common Pitfalls

### Pitfall 1: Шесть рядов × 20%p сжимают нижние ряды
**What goes wrong:** East_slavic в `showNumberRow`-ветке ставит `rowHeight="20%p"` для 5 рядов (number + 3 ЙЦУКЕН + action = 5 × 20% = 100%). Татарская раскладка в DEFAULT имеет 5 буквенных рядов + action = 6 рядов. При `rowHeight="20%p"` сумма = 120% → `KeyboardRow` включит clamp-логику («The row is too tall to fit… height reduced») и/или ряды не поместятся ровно.
**Why it happens:** `params.mDefaultRowHeight` умножается на число рядов; `mCurrentY += row.getRowHeight()` копится, последний ряд обрезается до `keyboardBottomEdge`. `[VERIFIED: KeyboardRow.java:136-160, KeyboardBuilder endRow]`
**How to avoid:** Для 6 рядов задать `rowHeight ≈ 16.667%p` (100/6), ЛИБО дать пятому ряду меньшую высоту через отдельный `latin:rowHeight` на этом `<Row>` (напр. 12–15%p — согласуется с «высота 5-го ряда ~0.75–0.8» из ресерча 05) и оставить остальным 5 рядам по ~17%p. bonusHeight (`config_key_bonus_height_5row`=9.194%p) добавляет общую высоту клавиатуры, чтобы клавиши не мельчали. Планировщику подобрать fractions так, чтобы сумма rowHeight = 100% baseHeight.
**Warning signs:** В logcat `KeyboardRow`: «The row is too tall to fit in the keyboard (… px)».

### Pitfall 2: Пустые клавиши при reuse rowkeys_east_slavic под locale tt
**What goes wrong:** Клавиши щ/ў, ы, э, и (используют `!text/keyspec_east_slavic_row1_9`, `_row2_2`, `_row2_11`, `_row3_5`) станут пустыми, потому что `TEXTS_DEFAULT` даёт для них `EMPTY`.
**Why it happens:** `KeyboardTextsTable.getTextsTable(tt)` → нет `tt` и нет `tt_RU` → `TEXTS_DEFAULT`, где эти 4 индекса = `""`. `[VERIFIED: KeyboardTextsTable.java:54-64 + TEXTS_DEFAULT]`
**How to avoid:** Вариант A — добавить `"tt", TEXTS_ru,` в `LOCALES_AND_TEXTS` (Ru-значения: щ/ы/э/и + все `morekeys_cyrillic_*` — идентичны нужным). Вариант B — свои `rowkeys_tatar1–3` с литеральными кодпоинтами вместо `!text/` (тогда таблица не нужна). Планировщику выбрать (см. Open Questions Q1).
**Warning signs:** 4 клавиши без символа в верхних рядах; тап по ним ничего не вводит.

### Pitfall 3: Раскладка не появляется в списке / не выбирается
**What goes wrong:** Добавили все XML, но раскладка нигде не выбирается и не активируется.
**Why it happens:** `SubtypeLocaleUtils.getSubtypes()`/`getSubtype()` — жёсткий `switch(mLocale)`; неизвестный locale → пустой список → `getSubtype` = null → `createSubtypesFromPref` пропускает, `LanguagesSettingsFragment` не показывает. `[VERIFIED: SubtypeLocaleUtils.java:375-568]`
**How to avoid:** Добавить `LOCALE_TATAR="tt"` (или `"tt_RU"`) в `sSupportedLocales[]`, `LAYOUT_TATAR="tatar"`, и `case LOCALE_TATAR: addLayout(LAYOUT_TATAR); break;` в `getSubtypes()`. Убедиться, что locale-строка совпадает с той, что кладётся в prefs.
**Warning signs:** Логи `SubtypePreferenceUtils`: «Unknown subtype specified: tt:tatar».

### Pitfall 4: `case LOCALE_SAKHA` без break (существующий баг-ловушка)
**What goes wrong:** При копипасте по образцу sakha можно унаследовать баг: `case LOCALE_SAKHA: addLayout(LAYOUT_SAKHA);` НЕ имеет `break;` и проваливается в `LOCALE_SERBIAN`. `[VERIFIED: SubtypeLocaleUtils.java:540-544]`
**How to avoid:** У татарского case ОБЯЗАТЕЛЬНО поставить `break;`. Не копировать sakha-фрагмент вслепую.
**Warning signs:** Татарский subtype тянет за собой сербскую раскладку.

## Code Examples

### rowkeys_tatar_extra.xml — пятый ряд (алфавитный, литеральные кодпоинты)
```xml
<!-- Source: паттерн keySpec из rowkeys_sakha1.xml (литеральные &#xUUUU;), кодпоинты из research/05 §1 -->
<merge xmlns:latin="http://schemas.android.com/apk/res-auto">
    <!-- U+04D9: "ә" CYRILLIC SMALL LETTER SCHWA -->
    <Key latin:keySpec="&#x04D9;" />
    <!-- U+04E9: "ө" CYRILLIC SMALL LETTER BARRED O -->
    <Key latin:keySpec="&#x04E9;" />
    <!-- U+04AF: "ү" CYRILLIC SMALL LETTER STRAIGHT U -->
    <Key latin:keySpec="&#x04AF;" />
    <!-- U+0497: "җ" CYRILLIC SMALL LETTER ZHE WITH DESCENDER -->
    <Key latin:keySpec="&#x0497;" />
    <!-- U+04A3: "ң" CYRILLIC SMALL LETTER EN WITH DESCENDER -->
    <Key latin:keySpec="&#x04A3;" />
    <!-- U+04BB: "һ" CYRILLIC SMALL LETTER SHHA -->
    <Key latin:keySpec="&#x04BB;" />
</merge>
```
Заглавные (Ә Ө Ү Җ Ң Һ = U+04D8/04E8/04AE/0496/04A2/04BA) появляются автоматически при shift — `Key.needsToUpcase` для `ELEMENT_ALPHABET_*_SHIFTED` вызывает `toUpperCase(locale)`, кириллические пары определены в Unicode. `[VERIFIED: Key.java:406-416, StringUtils.java:228-238]`

### SubtypeLocaleUtils.java — регистрация татарского (правка Java)
```java
// 1) рядом с LOCALE_SAKHA (~строка 110):
private static final String LOCALE_TATAR = "tt";          // или "tt_RU" — согласовать с prefs
// 2) в sSupportedLocales[] (~строка 190) добавить: LOCALE_TATAR,
// 3) рядом с LAYOUT_SAKHA (~строка 249):
public static final String LAYOUT_TATAR = "tatar";
// 4) в getSubtypes() switch — ОТДЕЛЬНЫЙ case с break (не как sakha!):
case LOCALE_TATAR:
    addLayout(LAYOUT_TATAR);
    break;
```

### keyboard_layout_set_tatar.xml — Element-декларация (для LAYOUT-04)
```xml
<!-- Source: keyboard_layout_set_east_slavic.xml (только alphabet отличается) -->
<KeyboardLayoutSet xmlns:latin="http://schemas.android.com/apk/res-auto">
    <Element latin:elementName="alphabet"       latin:elementKeyboard="@xml/kbd_tatar" />
    <Element latin:elementName="symbols"        latin:elementKeyboard="@xml/kbd_symbols" />
    <Element latin:elementName="symbolsShifted" latin:elementKeyboard="@xml/kbd_symbols_shift" />
    <Element latin:elementName="phone"          latin:elementKeyboard="@xml/kbd_phone" />
    <Element latin:elementName="phoneSymbols"   latin:elementKeyboard="@xml/kbd_phone_symbols" />
    <Element latin:elementName="number"         latin:elementKeyboard="@xml/kbd_number" />
</KeyboardLayoutSet>
```

## State of the Art

| Old Approach | Current Approach | When | Impact |
|--------------|------------------|------|--------|
| AOSP: layout выбирается системным InputMethodSubtype из method.xml | rkkr-форк: «виртуальные subtype» в SubtypeLocaleUtils + prefs | форк rkkr | method.xml для выбора раскладки не работает — правим Java-реестр |
| AOSP: раскладки только через subtype в method.xml/additional subtypes | Резолвинг layout set по имени в рантайме (getIdentifier) | — | Новый layout set = данные; регистрация locale = код |

**Deprecated/outdated:**
- Гипотеза из CONTEXT «subtype с KeyboardLayoutSet=tatar в method.xml» — не применима к этому форку (проверено). Заменяется правкой `SubtypeLocaleUtils`.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `rowHeight≈16.667%p` для 6 рядов корректно распределит высоту без обрезки | Pitfall 1 | Средний — нижние ряды сожмутся; лечится подбором fraction, проверяется на устройстве/эмуляторе |
| A2 | Locale-строка `"tt"` (vs `"tt_RU"`) — достаточная; ресурсы локали Android не требуются для рендера раскладки | Q2 / Pitfall 3 | Низкий — `LocaleUtils.constructLocaleFromString` строит Locale из любой строки; отображаемое имя может быть неполным, но раскладка работает. Проверить `LocaleResourceUtils` для display name |
| A3 | Заглавные татарские буквы получаются штатным `toUpperCase(Locale("tt"))` без спец-обработки | Code Examples | Низкий — Unicode-пары определены; research/05 §1 подтверждает «регистр конвертируется штатно». Проверить shift на устройстве |
| A4 | Активация раскладки по умолчанию для MVP решается через prefs/getDefaultSubtypes без нового UI | Q3 / Runtime State | Средний — механизм активации на усмотрение планировщика; может потребовать первого-запуска логики |

## Open Questions

1. **Reuse rowkeys_east_slavic1–3 (+ `"tt",TEXTS_ru`) ИЛИ свои rowkeys_tatar1–3 (литеральные кодпоинты)?**
   - Что знаем: оба работают. Reuse экономит XML, но требует правки KeyboardTextsTable и тянет ru-специфику (ъ/ё в moreKeys). Свои rowkeys независимы, нагляднее для «латиница позже», но дублируют ~33 клавиши.
   - Что неясно: предпочтение по поддерживаемости. Для «латиница Zamanälif позже» (LAYOUT-05) свои rowkeys гибче.
   - Рекомендация: **свои rowkeys_tatar1–3 с литеральными кодпоинтами** — убирает DEFAULT-ловушку, не трогает KeyboardTextsTable, честнее для будущих раскладок. Если планировщик выберет reuse — обязательно добавить `"tt", TEXTS_ru`.

2. **Locale-строка: `"tt"` или `"tt_RU"`?**
   - Что знаем: prefs-формат `locale:layoutSet`; `LocaleUtils.constructLocaleFromString` принимает оба. research/05 §7 рекомендует `tt_RU`/`tt-RU`.
   - Что неясно: влияние на display name в настройках (LocaleResourceUtils) и на upcase-locale.
   - Рекомендация: `"tt"` для MVP-простоты (одна раскладка), с заделом на `tt_RU` в фазе 3 (три subtype). Согласовать единообразно между `sSupportedLocales`, `case`, и активацией.

3. **Как раскладка становится активной для MVP (фаза 2, до subtypes фазы 3)?**
   - Что знаем: активная раскладка = первый элемент `mSubtypes`, грузится из prefs или `getDefaultSubtypes()` (по системным locale).
   - Что неясно: делаем tt дефолтом принудительно или пользователь выбирает в настройках.
   - Рекомендация: для MVP-проверяемости — добавить tt так, чтобы он выбирался в существующем LanguagesSettingsFragment (регистрация в реестре это уже даёт), + опционально форсировать дефолт. Планировщику определить UAT-критерий «клавиатура открывается с татарской раскладкой».

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Android SDK / aapt (compileSdk 37) | Компиляция XML-ресурсов | ✓ (фаза 1 собрала APK) | targetSdk/compileSdk 37 | — |
| Gradle wrapper (v9.6.0) | Сборка | ✓ (локально; jar восстанавливается) | 9.6.0 | `gradle wrapper` (STATE.md [01-01]) |
| Устройство/эмулятор для on-device проверки | UAT рендера/ввода | ✗ (отложено с фазы 1) | — | Проверка сборки + визуальный ревью XML; on-device в отложенном чек-листе |

**Missing dependencies with no fallback:** нет блокирующих для написания кода/сборки.
**Missing dependencies with fallback:** on-device проверка отложена (см. STATE.md Blockers) — верификация Pitfall 1 (высота рядов) и A3 (регистр) требует запуска; при отсутствии устройства полагаться на сборку + логи эмулятора, если доступен.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Отсутствует — в `app/src` только `main`, нет `test/`/`androidTest/` (проверено) |
| Config file | none — см. Wave 0 |
| Quick run command | `./gradlew assembleDebug` (компиляция ресурсов = базовая валидация XML) |
| Full suite command | `./gradlew assembleDebug lint` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| LAYOUT-01 | Раскладка парсится, 5-й ряд + ЙЦУКЕН, ввод тапом | build + manual | `./gradlew assembleDebug` (парс XML) + on-device ввод | ❌ (сборка есть, ввод — ручной) |
| LAYOUT-01 | 6 рядов помещаются без обрезки | manual/logcat | визуальный + grep logcat «too tall» | ❌ manual |
| LAYOUT-04 | ?123 / #+= открываются и возвращают к буквам | manual | on-device тап по переключателям | ❌ manual |
| LAYOUT-05 | Правка rowkeys меняет раскладку без правок движка | build | `./gradlew assembleDebug` после правки XML | ✅ (сборка) |
| — | Регистрация tt: раскладка выбираема | build + manual | сборка + проверка в настройках | ❌ manual |

### Sampling Rate
- **Per task commit:** `./gradlew assembleDebug` (XML парсится в рамках ресурс-компиляции; ошибки merge/атрибутов выявляются на сборке).
- **Per wave merge:** `./gradlew assembleDebug lint`.
- **Phase gate:** Собирающийся, устанавливаемый APK (дисциплина проекта: каждая фаза = собирающийся APK) + визуальная/on-device проверка рендера, когда устройство доступно.

### Wave 0 Gaps
- [ ] Автоматических тестов для парсинга раскладок в форке нет и создавать инструментальный тест-харнес для одной раскладки — избыточно для MVP. Основная автоматическая проверка = ресурс-компиляция при сборке.
- [ ] On-device UAT (рендер 6 рядов, регистр, ?123/#+=, ввод кодпоинтов) — ручной; занести в UAT-критерии фазы (совпадает с cross-cutting smoke-дисциплиной STATE.md).

*Инструментальный JUnit/Espresso-харнес не заводим: несоразмерно задаче «добавить раскладку данными»; риск покрывается сборкой + ручным UAT.*

## Security Domain

`security_enforcement: true`, но фаза — оффлайн раскладочный слой без сети, ввода недоверенных данных из сети, auth, крипты или хранения секретов.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | — (IME без аккаунтов) |
| V3 Session Management | no | — |
| V4 Access Control | no | — |
| V5 Input Validation | partial | Клавиатура генерирует ввод, не парсит недоверенный; единственная валидация — корректность keySpec (XML контролируется нами) |
| V6 Cryptography | no | — |

### Known Threat Patterns for {IME/раскладка}
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Утечка вводимого текста по сети | Information Disclosure | Нет разрешения INTERNET (CLAUDE.md + CI-проверка фаза 1); раскладка ничего не отправляет |
| DirectBootAware / состояние в prefs | — | prefs на DeviceProtectedStorage (существующий механизм форка), раскладка секретов не хранит |
| Малварь в новых зависимостях | Tampering | Нет новых зависимостей — только XML + правки существующих Java |

Специфических для этой фазы угроз нет: добавление декларативной раскладки не расширяет attack surface. Приватность («данные не собираются») сохраняется — раскладка не логирует и не передаёт ввод.

## Project Constraints (from CLAUDE.md)

- **Форк Simple Keyboard; новый код — Kotlin через interop, Java-базу массово не конвертировать.** → Правки `SubtypeLocaleUtils.java`/`KeyboardTextsTable.java` — минимальные точечные добавления в существующие Java-файлы (реестр-записи), НЕ конвертация. Новых Kotlin-файлов фаза не требует. Соответствует.
- **UI — один кастомный View + Canvas; Compose только в Activity настроек.** → Фаза не трогает рендер-слой; работает на уровне данных раскладки. Соответствует.
- **Perf: ноль аллокаций в цикле отрисовки; APK ≤ 3 МБ.** → XML-раскладки не влияют на hot path отрисовки; добавляют ~5 малых XML (~единицы КБ). Соответствует.
- **Privacy: без INTERNET, без аналитик; CI-проверка манифеста.** → Фаза сети не касается; method.xml не меняем для выбора раскладки. Соответствует.
- **IME-архитектура: без composing-текста в MVP, коммит сразу, deleteSurroundingTextInCodePoints.** → Ввод татарских букв идёт штатным commit по кодпоинту (пятый ряд — обычные keySpec). Соответствует.
- **Данные: раскладки хранить XML, не кодом; формат должен допускать латиницу позже.** → Основная реализация — XML. Оговорка: РЕГИСТРАЦИЯ locale — единственная неизбежная Java-запись (реестр форка хардкоден). Латиница добавляется тем же паттерном. Отклонение зафиксировано.
- **Разработчик — соло-новичок в Android; фазы маленькие, каждая завершается собирающимся APK.** → Изменения локальны и декларативны; проверка = сборка. Соответствует.
- **SDK targetSdk/compileSdk 37 (даунгрейд отклонён).** → Не затрагивается.

## Sources

### Primary (HIGH confidence) — исходники форка
- `app/src/main/java/.../keyboard/KeyboardLayoutSet.java` — резолвинг layout set по имени (`setSubtype`:239, `getXmlId`:272, `parseKeyboardLayoutSetElement`:319)
- `app/src/main/java/.../latin/utils/SubtypeLocaleUtils.java` — реестр виртуальных subtype (`sSupportedLocales`:128, `getSubtypes` switch:386-568, sakha-баг без break:540)
- `app/src/main/java/.../latin/utils/SubtypePreferenceUtils.java` — формат prefs `locale:layoutSet`, `createSubtypesFromPref`:48
- `app/src/main/java/.../latin/RichInputMethodManager.java` — SubtypeList.reload:154, getCurrentSubtype path
- `app/src/main/java/.../keyboard/internal/KeyboardBuilder.java` — парсер рядов (любое число Row), switch/case, include/merge
- `app/src/main/java/.../keyboard/internal/KeyboardRow.java` — высота/clamp рядов:136-160, ширины/fillRight:281-297
- `app/src/main/java/.../keyboard/internal/KeyboardTextsTable.java` — полокальные тексты, fallback TEXTS_DEFAULT:54-64, keyspec_east_slavic_* = EMPTY в DEFAULT
- `app/src/main/java/.../keyboard/Key.java` — needsToUpcase:406, localeForUpcasing:251
- `app/src/main/java/.../latin/common/StringUtils.java` — toTitleCaseOfKeyLabel/toUpperCase(locale):228-249
- `res/xml/kbd_east_slavic.xml`, `rows_east_slavic.xml`, `rowkeys_east_slavic1–3.xml`, `keyboard_layout_set_east_slavic.xml` — прямой образец
- `res/xml/kbd_sakha.xml`, `rows_sakha.xml`, `rowkeys_sakha1.xml` — кириллица-с-доп-буквами прецедент (13 клавиш/ряд, 7.692%p)
- `res/xml/kbd_armenian_phonetic.xml`, `rows_armenian_phonetic.xml` — прецедент keyLetterSize/keyShiftedLetterHintRatio_5row + include доп. клавиш в ряд
- `res/xml/row_qwerty0.xml`, `rowkeys_qwerty0.xml` — прецедент доп. верхнего ряда (number row)
- `res/values/config.xml`:66-79, `res/values-land/config.xml`:55-70 — 5-row height fractions
- `res/xml/method.xml` — один generic subtype (не используется для выбора раскладки)
- `research/05-tatarskaya-raskladka.md` §1 (кодпоинты), §4.5 (алфавитный порядок, высота 0.75–0.8)

### Secondary (MEDIUM confidence)
- CONTEXT.md / STATE.md — locked decisions, дисциплины проекта

### Tertiary (LOW confidence)
- Нет — все технические выводы проверены по исходникам форка

## Metadata

**Confidence breakdown:**
- Standard stack (механизмы форка): HIGH — прочитаны исходники цепочки резолвинга и парсинга
- Architecture (цепочка выбора + рендера): HIGH — трассировка method.xml→SubtypeLocaleUtils→prefs→KeyboardLayoutSet→KeyboardBuilder по коду
- Критический вывод про method.xml vs виртуальные subtype: HIGH — подтверждён кодом RichInputMethodManager + SubtypePreferenceUtils
- Pitfalls (высота 6 рядов, DEFAULT EMPTY, sakha break-баг): HIGH — из кода KeyboardRow/KeyboardTextsTable/SubtypeLocaleUtils
- Точные fraction для высоты 6 рядов: MEDIUM — требует подбора и on-device проверки (A1)

**Research date:** 2026-07-18
**Valid until:** ~2026-08-18 (форк стабилен; риск устаревания низкий — правки на месте по коду)

