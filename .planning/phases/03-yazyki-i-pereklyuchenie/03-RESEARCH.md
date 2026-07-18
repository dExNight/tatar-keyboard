# Phase 3: Языки и переключение — Research

**Researched:** 2026-07-18
**Domain:** Subtype-слой форка (SubtypeLocaleUtils/RichInputMethodManager/prefs), long-press moreKeys-механика, глобус-клавиша
**Confidence:** HIGH (всё проверено по исходникам форка; построено на ресерче фазы 2 — реестр subtype, формат prefs `locale:layoutSet`, резолвинг layout set не пере-выводились)

## Summary

Фаза 3 почти целиком «включает уже существующие механизмы форка». Глобус-клавиша, цикл subtype, запоминание активной раскладки, пикер по long-press — всё уже реализовано и активируется само, как только enabled-subtypes становится больше одного. Долгопресс-дубли — штатный атрибут `latin:moreKeys`, литеральные кодпоинты работают без KeyboardTextsTable (прецедент: `rowkeys_bengali2.xml`).

**Ключевая находка, меняющая план по русской раскладке:** путь «добавить татарские буквы в `TEXTS_ru` KeyboardTextsTable» НЕ работает полностью. У клавиш **ж** и **х** в `rowkeys_east_slavic*` вообще нет атрибута `moreKeys` (только `keyHintLabel`/`additionalMoreKeys` для символов в number-row-ветке) — значит, записью в таблице текстов popup на них не повесить, а трогать shared-XML `rowkeys_east_slavic*` нельзя: их используют be_BY, kk, ky, ru, uk (`SubtypeLocaleUtils.java:484-490`). Следствие: русской раскладке нужен **свой набор rowkeys** (копия tatar-подхода фазы 2: литеральные кодпоинты + литеральные moreKeys) и свой layout set `russian`, а `case LOCALE_RUSSIAN` в реестре переводится с `LAYOUT_EAST_SLAVIC` на `LAYOUT_RUSSIAN`. East_slavic остаётся нетронутым для остальных четырёх локалей.

Вторая находка: **запоминание активного subtype (SWITCH-01) уже реализовано** — `resetSubtypeCycleOrder()` при закрытии клавиатуры переносит текущий subtype в начало списка и сохраняет prefs; при перезапуске первый элемент списка = активный (`RichInputMethodManager.java:307-316`, `LatinIME.java:354`, `SubtypeList.reload:154-164`). Кода писать не нужно.

Третья: **тап/long-press глобуса уже делают требуемое.** Тап циклит виртуальные subtype (при дефолте `pref_enable_ime_switch=false` — только внутри нашего IME, без ухода в другие клавиатуры). Long-press открывает диалог выбора клавиатуры (собственный AlertDialog форка со всеми subtype нашего IME + другими IME системы — функциональный эквивалент системного пикера). Глобус показывается автоматически при >1 enabled subtype.

**Primary recommendation:** (1) rowkeys_tatar1–3 + новые rowkeys_russian1–3 получают литеральные `latin:moreKeys` (включая ё/ъ из ревью F1); (2) новый layout set `russian` по шаблону east_slavic; (3) `getDefaultSubtypes` переписывается на детерминированную тройку tt_RU→ru→en_US; (4) миграция `tt`→`tt_RU` + кастомное имя «Татарча» через `locale_exception_keys`.

## Q1: Механика moreKeys — трассировка и путь для литералов

### Как работает (полная цепочка)

1. **XML:** `latin:moreKeys` — обычный атрибут `Key` (`attrs.xml`). В east_slavic значения — рефы `!text/morekeys_cyrillic_*` (`rowkeys_east_slavic1.xml:43,49,55,61,67` — у/к/е/н/г; `rowkeys_east_slavic2.xml:33,42,60,78` — ы/а/о/э; `rowkeys_east_slavic3.xml:27,48,59,70` — я/и/ь/ю).
2. **Парсинг:** `Key.java:253` — `style.getStringArray(keyAttr, R.styleable.Keyboard_Key_moreKeys)` → `KeyStyle.java:41-46 parseStringArray` → `KeyboardTextsSet.resolveTextReference` (`KeyboardTextsSet.java:75`): раскрывает ТОЛЬКО подстроки с префиксом `!text/`; строки без префикса проходят как есть. Затем `MoreKeySpec.splitKeySpecs` режет по запятым.
3. **Резолвинг `!text/`:** `KeyboardTextsSet` → `KeyboardTextsTable.getTextsTable(locale)` (`KeyboardTextsTable.java:54-64`): точный `locale.toString()` → затем `locale.getLanguage()` → иначе `TEXTS_DEFAULT`. Так east_slavic получает ё на е и ъ на ь: `TEXTS_ru` (`KeyboardTextsTable.java:3160-3272`) содержит `morekeys_cyrillic_ie = "ё,е́,ѣ"` (:3182) и `morekeys_cyrillic_soft_sign = "ъ"` (:3195).
4. **Popup:** `Key.java:285-291` — массив оборачивается в `MoreKeySpec[]`, ставится `ACTION_FLAGS_ENABLE_LONG_PRESS`; `PointerTracker.onLongPressed` (`PointerTracker.java:743-783`) → `sDrawingProxy.showMoreKeysKeyboard(key, this)` (:775).
5. **Регистр:** `MoreKeySpec`-конструктор (`MoreKeySpec.java:52-56`) получает `needsToUpcase` + locale (`Key.java:250-251, 290`) — в shifted-состоянии ә→Ә и т.д. поднимаются автоматически, отдельные записи для заглавных НЕ нужны.

### Литеральные moreKeys — подтверждено

`latin:moreKeys="&#x04D9;"` (или несколько через запятую) работает без каких-либо записей в KeyboardTextsTable — `resolveTextReference` не трогает строки без `!text/`. Прямой прецедент в форке: `rowkeys_bengali2.xml:26,31,58` (`latin:moreKeys="&#x09CB;"`, в т.ч. списки через запятую). Это идеально стыкуется с решением фазы 2 (rowkeys_tatar* — литералы без `!text/`).

**Татарская раскладка (LAYOUT-02 + ревью F1)** — добавить в существующие `rowkeys_tatar1–3`:

| Клавиша | Файл | moreKeys |
|---|---|---|
| а | rowkeys_tatar2 | `&#x04D9;` (ә) |
| о | rowkeys_tatar2 | `&#x04E9;` (ө) |
| у | rowkeys_tatar1 | `&#x04AF;` (ү) |
| ж | rowkeys_tatar2 | `&#x0497;` (җ) |
| н | rowkeys_tatar1 | `&#x04A3;` (ң) |
| х | rowkeys_tatar1 | `&#x04BB;` (һ) |
| э | rowkeys_tatar2 | `&#x04D9;` (ә) |
| г | rowkeys_tatar1 | `&#x04BB;` (һ) |
| **е** | rowkeys_tatar1 | `&#x0451;` (ё) — ревью F1 |
| **ь** | rowkeys_tatar3 | `&#x044A;` (ъ) — ревью F1 |

### Русская раскладка: почему НЕ через KeyboardTextsTable

Проверка карты локалей (`KeyboardTextsTable.java:4478-4552`): `TEXTS_ru` привязан ТОЛЬКО к ключу `"ru"` — bg→TEXTS_bg, sah→TEXTS_sah, be_BY/kk/ky/uk имеют собственные таблицы. То есть правка `TEXTS_ru` сама по себе задела бы только русский subtype (это НЕ главный блокер). **Главный блокер:** layout set `east_slavic` разделяют пять локалей (`SubtypeLocaleUtils.java:484-490`: be_BY, kk, ky, ru, uk), а у клавиш **ж** (`rowkeys_east_slavic2.xml` — есть только `keyHintLabel="("` в number-row-ветке) и **х** (`rowkeys_east_slavic1.xml:83-87,124-126`) **нет атрибута `moreKeys` вовсе** — нет имени `!text/`, к которому можно привязать значение. Повесить popup на ж/х можно только правкой shared-XML, что затронуло бы все пять локалей.

Варианты:
- **(A, рекомендую) Свой layout set `russian`:** `keyboard_layout_set_russian.xml` (копия east_slavic, alphabet→`@xml/kbd_russian`), `kbd_russian.xml` (копия `kbd_east_slavic.xml` — 4 ряда в default, 5row-конфиг только в number-row-ветке), `rows_russian.xml`, `rowkeys_russian1–3.xml` — копия rowkeys_tatar1–3 (та же ЙЦУКЕН литералами, уже выверена в фазе 2) + литеральные moreKeys: татарские дубли из таблицы выше ПЛЮС е→ё, ь→ъ. В реестре: `LAYOUT_RUSSIAN = "russian"`, `case LOCALE_RUSSIAN: addLayout(LAYOUT_RUSSIAN); break;` — вынести ru из общей east_slavic-группы (be_BY/kk/ky/uk остаются как были). Ни один shared-файл не трогается; KeyboardTextsTable не трогается; подход побайтно консистентен с фазой 2.
- (B) `<case latin:languageCode="ru">`-ветки внутри shared `rowkeys_east_slavic*` (KeyboardBuilder умеет матчить locale в case: `KeyboardBuilder.java:607-637`, атрибуты `localeCode/languageCode/countryCode` в `attrs.xml:360-362`). Работает, но умножает и без того разросшиеся switch/case (showNumberRow × showExtraChars) и вносит татарскую специфику в upstream-файлы — хуже для diff-минимальности и мержей с upstream. Отклонить.
- (C) TEXTS_ru-записи + точечные правки shared-XML для ж/х — гибрид худшего рода (два механизма + контаминация shared). Отклонить.

Замечание к (A): решить судьбу русских «акцентных» moreKeys донора (е́, ѣ, ы́, ꙑ, у́, ў, ґ, э́, є, и́, і, ї…). Рекомендация: НЕ переносить — оставить только ё/ъ + татарские дубли (татарская буква первой в списке — самый быстрый long-press). Минимализм соответствует iOS-стилю и ЦА (русскоязычные в РФ; белорусско-украинские символы не нужны).

## Q2: Управление enabled subtypes

- **Хранение:** `pref_enabled_subtypes` (`Settings.readPrefSubtypes`), формат `locale:layoutSet;…` — `SubtypePreferenceUtils.java:36-46` (сериализация :74-86, парсинг `createSubtypesFromPref` :48-72; неизвестный subtype → `getSubtype()==null` → строка молча пропускается).
- **Загрузка:** `RichInputMethodManager.SubtypeList.reload` (`RichInputMethodManager.java:154-164`): prefs пусты/битые → `SubtypeLocaleUtils.getDefaultSubtypes()`; `mCurrentSubtypeIndex = 0` — активен первый элемент списка.
- **UI включения:** Settings → Languages (`LanguagesSettingsFragment.java`): меню «+» показывает НЕиспользуемые локали из `SubtypeLocaleUtils.getSupportedLocales()` (:177), чек → `getDefaultSubtype(locale)` → `mRichImm.addSubtype(subtype)` (:247-250) → `SubtypeList.addSubtype` (:250-261) добавляет В КОНЕЦ списка + `saveSubtypeListPref()`. Удаление — `removeSubtype` (:268-299, не даёт удалить последний). Так пользователь сегодня включает ru/en; для tt это уже работает с фазы 2.
- **Дефолт tt+ru+en:** см. Q7.
- **Запоминание активного (SWITCH-01):** уже реализовано. `setCurrentSubtype(index)` при прямом выборе и `resetSubtypeCycleOrder()` (`RichInputMethodManager.java:307-316`) при закрытии клавиатуры (`LatinIME.onFinishInputView:352-356`) переносят текущий subtype в голову списка (`Collections.rotate`) и пишут prefs. После перезапуска `reload` ставит `mCurrentSubtypeIndex=0` → восстанавливается последний активный. **Кода для SWITCH-01-персистентности писать не нужно** — покрывается штатно.

## Q3: Глобус-клавиша

Всё уже есть; включается автоматически при нескольких subtypes.

- **Клавиша:** `languageSwitchKeyStyle` (`key_styles_common.xml:108-109`): `!icon/language_switch_key|!code/key_language_switch` → `Constants.CODE_LANGUAGE_SWITCH = -10` (`Constants.java:102`). Вставляется в action row через `key_space_5kw.xml:47-55` (`<case latin:languageSwitchKeyEnabled="true">`: глобус + space 40%p; default: space 50%p). `row_qwerty4.xml:33` включает `key_space_5kw` → и tatar (rows_tatar включает row_qwerty4), и будущий russian, и qwerty получают глобус автоматически.
- **Показ:** `KeyboardSwitcher.java:121` → `LatinIME.shouldShowLanguageSwitchKey()` (`LatinIME.java:911-924`): pref `pref_show_language_switch_key` (default **true**, `Settings.java:226-227`) И (`hasMultipleEnabledSubtypes()` (`RichInputMethodManager.java:434-436` — `size() > 1`) ИЛИ система предлагает переключение IME). С тремя дефолтными subtypes глобус виден сразу — **никаких правок не нужно**.
- **Тап:** `InputLogic.java:212-214` → `handleLanguageSwitchKey` (:340-342) → `LatinIME.switchToNextSubtype` (:712-715) → `mRichImm.switchToNextInputMethod(token, onlyCurrentIme = !shouldSwitchToOtherInputMethods(token))`. `shouldSwitchToOtherInputMethods` (:901-909) требует pref `pref_enable_ime_switch` (default **false**, `Settings.java:235`) → по умолчанию `onlyCurrentIme=true` → `SubtypeList.switchToNextSubtype(true)` (`RichInputMethodManager.java:388-400`) — чистый цикл tt→ru→en→tt внутри нашего IME. **SWITCH-02-тап выполняется из коробки.** (Порядок цикла = порядок списка; благодаря resetSubtypeCycleOrder последний использованный всегда первый, остальные перебираются по кругу.)
- **Long-press:** `PointerTracker.onLongPressed` (`PointerTracker.java:764-772`): код глобуса или пробела → `onCustomRequest(CUSTOM_CODE_SHOW_INPUT_METHOD_PICKER)` → `LatinIME.showInputMethodPicker` (:643-650) → `RichInputMethodManager.showSubtypePicker` (:540-614). Это НЕ системный `InputMethodManager.showInputMethodPicker()`, а собственный AlertDialog форка: перечисляет виртуальные subtypes нашего IME + системные subtypes всех остальных включённых IME (`getEnabledSubtypeInfoOfAllImes:621`), выбор → `setCurrentSubtype` или переход к чужому IME. Функционально эквивалентен системному пикеру (и лучше: показывает наши tt/ru/en отдельными пунктами, чего системный пикер не смог бы — они не в method.xml). Показывается только при ≥2 пунктах (:548-551). **Рекомендация: принять поведение форка как выполнение SWITCH-02-long-press** (формулировка «системный IME-пикер» в требовании — о возможности уйти в другую клавиатуру, что диалог даёт).

Что остаётся фазе 3 по глобусу: только **проверка на устройстве** (отложенный UAT-чек-лист), кода нет.

## Q4: tt vs tt_RU

- **Механика:** `LocaleUtils.constructLocaleFromString` принимает обе формы; prefs, реестр и `KeyboardLayoutSet` работают со строкой как есть. `findBestLocale` (`LocaleUtils.java:107-135`) матчит каскадно: exact → language+country → language, поэтому `tt_RU` в реестре заматчится и с системным locale `tt`, и с `tt_RU`.
- **KeyboardTextsTable:** rowkeys_tatar* без `!text/` — таблица не участвует, `tt` vs `tt_RU` безразличен.
- **Upcase:** `Locale("tt","RU")` для `toUpperCase` эквивалентен `Locale("tt")` — кириллические пары от региона не зависят.
- **Рекомендация: мигрировать на `tt_RU`** (требование SWITCH-01 говорит tt_RU; регион-квалификация точнее для будущего Zamanälif tt_Latn). Правка: значение константы `LOCALE_TATAR = "tt_RU"` (`SubtypeLocaleUtils.java:116`) — `sSupportedLocales:198` и `case:556-558` используют константу, дополнительных правок нет.
- **Риск миграции prefs:** dev-установки фазы 2 могут держать `tt:tatar` в `pref_enabled_subtypes`. После миграции `getSubtype("tt","tatar")` вернёт null → строка пропускается. Поведение деградации: если валидных строк не останется — список пуст → `reload` подставит новые дефолты (tt_RU+ru+en) — **самовосстановление**; если в prefs был ещё и валидный subtype (например en_US:qwerty, добавленный руками) — останется только он, татарский придётся включить заново через Languages. Приемлемо: приложение не публиковалось, установки только у разработчика. Зафиксировать в плане, миграционный код НЕ писать.

## Q5: Display names

- `Subtype.getName()` (`Subtype.java:106-120`) → `LocaleResourceUtils.getLocaleDisplayNameInSystemLocale(mLocale)` (`LocaleResourceUtils.java:103-107`): сперва смотрит карту исключений `sExceptionalLocaleToNameIdsMap` (строится из `R.array.locale_exception_keys` + строк `locale_name_<locale>`, :81-87), иначе — `Locale.getDisplayName(systemLocale)` через ICU (:155-173).
- Для `tt`/`tt_RU` без исключения: ICU на API 24+ знает `tt` («Tatar»/«татарский»; с регионом — «Tatar (Russia)»). Работает, но выглядит «системно» и зависит от языка системы.
- **Кастомное имя «Татарча» — поддерживается штатно:** добавить `<item>tt_RU</item>` в `locale_exception_keys` (`donottranslate.xml:25-31`; прецеденты en_US/en_GB/es_US/hi_ZZ/sr_ZZ) + `<string name="locale_name_tt_RU">Татарча</string>` в `strings.xml` (рядом с :56-67). Имя-исключение показывается фиксированно на любом языке системы — как «Hinglish»/«Srpski» (эндоним, это осознанный паттерн донора). Рекомендую: «Татарча». Для ru/en_US ничего не делать: `ru` отдаст ICU-имя на языке системы («Русский»/«Russian» — корректно), `en_US` уже в исключениях («English (US)», `strings.xml:59`).
- Замечание: если оставить `tt` без региона, «Татарча» вешается на ключ `tt` тем же механизмом — исключение работает для любой строки. Но выбираем tt_RU (Q4).

## Q6: en_US

Всё уже в реестре: `LOCALE_ENGLISH_UNITED_STATES = "en_US"` (`SubtypeLocaleUtils.java:63`), в `sSupportedLocales:130` первым, `case:398` → `addLayout(LAYOUT_QWERTY)` + `addGenericLayouts()`. `keyboard_layout_set_qwerty.xml` и `kbd_qwerty.xml` существуют; `TEXTS_en` в таблице; display name «English (US)» в исключениях. **Регистрационных правок ноль** — только включить en_US в дефолты (Q7).

## Q7: Реструктуризация getDefaultSubtypes (судьба MVP-хака)

Текущее (`SubtypeLocaleUtils.java:305-335`): цикл матчинга системных локалей → MVP-вставка `subtypes.add(0, getSubtypes(LOCALE_TATAR, resources).get(0))` (:329) → мёртвый английский fallback (:330-333, ревью F2).

**Рекомендация — детерминированная тройка** (убирает хак и мёртвый код):

```java
public static List<Subtype> getDefaultSubtypes(final Resources resources) {
    // Default for a fresh install: Tatar active, Russian and English enabled
    // (SWITCH-01). Deliberately independent of system locales — the app's
    // audience is Tatar speakers whose system language is usually ru or en.
    final ArrayList<Subtype> subtypes = new ArrayList<>(3);
    subtypes.add(getDefaultSubtype(LOCALE_TATAR, resources));    // tt_RU:tatar — активный (index 0)
    subtypes.add(getDefaultSubtype(LOCALE_RUSSIAN, resources));  // ru:russian
    subtypes.add(getDefaultSubtype(LOCALE_ENGLISH_UNITED_STATES, resources)); // en_US:qwerty
    return subtypes;
}
```

Обоснование отказа от цикла системных локалей: ЦА — татароязычные с системой ru/en; матчинг системных локалей дал бы ровно ту же тройку в другом порядке, но с недетерминированными хвостами (пользователь с системным de получил бы qwertz и т.п.). Три фиксированных subtype проще, предсказуемее и точно соответствуют SWITCH-01. Цикл + fallback удаляются (это и есть «оформление» MVP-хака; diff локален в одном методе). `getDefaultSubtype(locale)` (:281-284) возвращает первый layout локали: для ru после Q1-правки это `russian`, для en_US — `qwerty`.

Важно: дефолты применяются только при пустых/битых prefs (`reload:158-160`) — существующий выбор пользователя никогда не перетирается.

## Итоговая карта изменений фазы

| Файл | Действие | Требование |
|---|---|---|
| `res/xml/rowkeys_tatar1–3.xml` | + литеральные `latin:moreKeys` (10 клавиш, табл. Q1) | LAYOUT-02, F1 |
| `res/xml/rowkeys_russian1–3.xml` | НОВЫЕ: копия rowkeys_tatar1–3 + moreKeys (дубли + ё/ъ) | LAYOUT-02, LAYOUT-03 |
| `res/xml/rows_russian.xml` | НОВЫЙ: копия rows_east_slavic с include rowkeys_russian* | LAYOUT-03 |
| `res/xml/kbd_russian.xml` | НОВЫЙ: копия kbd_east_slavic (include rows_russian) | LAYOUT-03 |
| `res/xml/keyboard_layout_set_russian.xml` | НОВЫЙ: копия east_slavic, alphabet→kbd_russian | LAYOUT-03 |
| `SubtypeLocaleUtils.java` | `LOCALE_TATAR`→"tt_RU"; +`LAYOUT_RUSSIAN`; вынести `case LOCALE_RUSSIAN` из east_slavic-группы; переписать `getDefaultSubtypes` | SWITCH-01, LAYOUT-03 |
| `res/values/donottranslate.xml` | +`tt_RU` в `locale_exception_keys` | SWITCH-01 (имя) |
| `res/values/strings.xml` | +`locale_name_tt_RU` = «Татарча» | SWITCH-01 (имя) |
| глобус / цикл / пикер / персистентность | **без правок** — работает штатно | SWITCH-01/02 |

Java-дифф снова ограничен реестром `SubtypeLocaleUtils` — движок, KeyboardTextsTable, method.xml, RichInputMethodManager не трогаются.

## Common Pitfalls

### Pitfall 1: Правка shared east_slavic-файлов
`rowkeys_east_slavic*`/`kbd_east_slavic` используют be_BY, kk, ky, uk (и до нашей правки ru). Любой moreKeys/клавиша там утечёт в четыре чужих локали. Не трогать; русский — только через собственный layout set.

### Pitfall 2: `case LOCALE_RUSSIAN` — не забыть `break` и не разорвать группу
Сейчас ru — в общей группе `case`-провалов (:484-490). Вынося ru, оставить `case LOCALE_BELARUSIAN_BELARUS/KAZAKH/KYRGYZ/UKRAINIAN: addLayout(LAYOUT_EAST_SLAVIC); break;` нетронутой, а новый `case LOCALE_RUSSIAN: addLayout(LAYOUT_RUSSIAN); break;` — с break (память о sakha-баге фазы 2, :547-548 — он всё ещё в коде, не копировать).

### Pitfall 3: Экранирование в moreKeys
Запятая — разделитель спеков, `\` — escape (`MoreKeySpec.splitKeySpecs`). Наши значения — одиночные кириллические кодпоинты, конфликтов нет; но при списке из нескольких (`"&#x0451;,&#x04D9;"`) — просто запятая без пробелов, по образцу `rowkeys_bengali2.xml:58`.

### Pitfall 4: Дефолты не применятся на dev-установках
`getDefaultSubtypes` срабатывает только при пустых/невалидных prefs. Dev-устройство с фазы 2 держит `tt:tatar` → после миграции на tt_RU строка невалидна → если она была единственной, prefs дадут пустой список и новые дефолты подхватятся; но если в prefs есть другие валидные строки — тройка НЕ появится. Для чистого UAT: `adb uninstall` перед установкой (уже в чек-листе фазы 2).

### Pitfall 5: Порядок в moreKeys-popup
Первый элемент списка — дефолтный (выбирается отпусканием без движения). Татарскую букву ставить первой (а→"ә,а́"-стиль), иначе long-press а даст акцентную а́ — регресс UX.

## Open Questions (с рекомендациями)

1. **Русские «акцентные» moreKeys донора (е́, ѣ, ў, ґ, є, і…) в rowkeys_russian?** Рекомендация: не переносить; только татарские дубли + ё/ъ. ЦА — Татарстан/Россия; минимализм = меньше XML и чище popup. Если планировщик решит сохранить паритет с донором — добавить списками через запятую, татарская буква первой (Pitfall 5).
2. **`keyHintLabel` (мини-подсказка дубля на клавише)?** Донор для кириллических moreKeys хинты не рисует (только символы в number-row-ветке). Рекомендация: без хинтов в фазе 3 (паритет с донором, iOS-минимализм); вернуться при iOS-полировке (фазы 6–7).
3. **«Система видит три subtype» (текст SWITCH-01) — буквально системные InputMethodSubtype?** В этом форке subtypes виртуальные (ресерч фазы 2); ОС видит один generic subtype из method.xml. Три subtype видны в Languages-настройках приложения и в пикере глобуса. Рекомендация: трактовать SWITCH-01 на уровне механизма форка (как уже сделано в CONTEXT); вывод трёх реальных системных subtype — архитектурное отклонение без пользы для UX, не делать.

## Validation

- **Per task:** `./gradlew assembleDebug` (XML + Java компиляция; невалидный moreKeys-спек падает на этапе билда раскладки только в рантайме — поэтому важен smoke).
- **Phase gate (on-device, в отложенный чек-лист):** long-press а/о/у/ж/н/х/э/г на tatar и russian → popup с дублем, выбор коммитит букву; long-press е→ё, ь→ъ; shift + long-press а → Ә; глобус виден, тап циклит tt→ru→en→tt, long-press открывает пикер; выбор ru, закрытие клавиатуры, kill процесса → открывается ru (персистентность); Languages показывает «Татарча», «Русский»/display-имя ru, «English (US)»; чистая установка → сразу татарская, в Languages включены три.
- Автотестов не заводим (решение фазы 2 — несоразмерно).

## Sources (исходники форка, HIGH)

- `keyboard/Key.java` :250-291 — парсинг moreKeys, upcase, ENABLE_LONG_PRESS
- `keyboard/internal/MoreKeySpec.java` :52-73 — спек, upcase, splitKeySpecs
- `keyboard/internal/KeyStyle.java` :36-46; `KeyboardTextsSet.java` :75+ — resolveTextReference (только `!text/`)
- `keyboard/internal/KeyboardTextsTable.java` :54-64 (fallback), :3160-3272 (TEXTS_ru), :4478-4552 (карта локалей — TEXTS_ru только у "ru")
- `keyboard/PointerTracker.java` :743-783 — long-press: пикер для глобуса/пробела, showMoreKeysKeyboard
- `keyboard/KeyboardSwitcher.java` :121; `keyboard/internal/KeyboardBuilder.java` :597-637 — languageSwitchKeyEnabled + locale-матчинг case
- `latin/LatinIME.java` :352-356 (reset cycle), :635-650 (пикер), :712-715 (switchToNextSubtype), :901-924 (показ глобуса)
- `latin/inputlogic/InputLogic.java` :212-214, :340-342
- `latin/RichInputMethodManager.java` :154-164 (reload), :250-299 (add/remove), :307-316 (resetSubtypeCycleOrder), :388-400 (switchToNextSubtype), :434-436, :491-513 (switchToNextInputMethod), :540-614 (showSubtypePicker), :621+ (все IME)
- `latin/utils/SubtypeLocaleUtils.java` :63,108,116 (константы), :129-207 (sSupportedLocales), :271-335 (getSubtypes/getDefaultSubtype/getDefaultSubtypes), :393-578 (switch), :484-490 (east_slavic-группа), :556-558 (tatar case)
- `latin/utils/SubtypePreferenceUtils.java` :36-86; `latin/Subtype.java` :106-120; `latin/utils/LocaleResourceUtils.java` :81-87, :103-107, :155-173; `latin/common/LocaleUtils.java` :107-135 (findBestLocale)
- `latin/settings/Settings.java` :54-56, :226-235; `SettingsValues.java` :100-102; `LanguagesSettingsFragment.java` :177, :237-258
- `res/xml/`: rowkeys_east_slavic1–3 (morekeys-рефы; ж/х без moreKeys), rowkeys_bengali2 (литеральные moreKeys — прецедент), key_styles_common :108-109, key_space_5kw :47-55, row_qwerty4 :33, kbd_east_slavic, rows_east_slavic, prefs_screen_preferences
- `res/values/donottranslate.xml` :25-41 (locale_exception_keys), `strings.xml` :56-67 (locale_name_*), `attrs.xml` :342,360-362
- Фаза 2: `02-RESEARCH.md` (реестр, prefs-формат, DEFAULT-ловушка), `02-REVIEW.md` (F1 ё/ъ, F2 мёртвый fallback)

## Metadata

**Confidence:** HIGH по всем семи вопросам — каждая цепочка (moreKeys, цикл, пикер, персистентность, display names) прослежена по коду до конца; единственные MEDIUM-остатки — рантайм-поведение popup/пикера на устройстве (отложенный UAT, устройства нет).
**Research date:** 2026-07-18 · **Valid until:** ~2026-08-18
