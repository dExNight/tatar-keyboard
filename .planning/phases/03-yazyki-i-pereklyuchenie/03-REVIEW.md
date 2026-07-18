---
status: PASS
blockers: 0
warnings: 0
observations: 5
depth: standard
diff_base: 4de188c
date: 2026-07-18
---

# Review 03 — Языки и переключение (plan 03-01)

**Date:** 2026-07-18
**Depth:** standard
**Diff base:** 4de188c
**Files reviewed:** `rowkeys_tatar1/2/3.xml` (moreKeys), 6 новых russian-XML (`rowkeys_russian1/2/3`, `rows_russian`, `kbd_russian`, `keyboard_layout_set_russian`), `SubtypeLocaleUtils.java`, `donottranslate.xml`, `strings.xml`
**Reference material:** `rowkeys_east_slavic1–3.xml`, `rows_east_slavic.xml`, `kbd_east_slavic.xml`, `keyboard_layout_set_east_slavic.xml`, `KeyboardTextsTable.java` (TEXTS_ru, TEXTS_kk), `MoreKeySpec.splitKeySpecs`, `Key.java` (upcase moreKeys), `KeyboardLayoutSet.Builder.getXmlId`, `LocaleResourceUtils.java`, `Subtype.java`, `SubtypePreferenceUtils.createSubtypesFromPref`, `RichInputMethodManager.SubtypeList.reload`, `method.xml`, review 02 (побуквенная сверка ЙЦУКЕН).

## Verdict

**PASS.** Все механические acceptance-критерии подтверждены чтением кода: moreKeys синтаксически корректны для парсера форка, дубли висят на правильных буквах в правильных рядах на обеих раскладках, russian-набор побуквенно совпадает со стандартным ЙЦУКЕН, высотная математика kbd_russian — стандартная 4-рядная (не татарская 5-рядная), реестр диффа корректен, NPE-путей нет. Блокирующих находок нет; 5 наблюдений low-severity (осознанные решения либо унаследованное, зафиксированы для будущих фаз).

---

## 1. moreKeys — синтаксис vs MoreKeySpec parser — PASS

Все 10 значений `latin:moreKeys` — одиночные символы через XML-entity (`&#x04AF;` и т.п.), без запятых и без `\`. `MoreKeySpec.splitKeySpecs` (MoreKeySpec.java:183-190): строка без COMMA/BACKSLASH возвращается как один спек — экранирование не требуется. Апкейс для shift-состояний делается автоматически в `Key` (Key.java:290, `new MoreKeySpec(spec, needsToUpcase, locale)`): ә→Ә, ё→Ё и т.д. — кириллица апкейсится корректно для tt_RU/ru. Все 9 XML прошли `xmllint --noout`.

## 2. Дубли на правильных буквах / правильных рядах — PASS

Ряд 1 (`rowkeys_tatar1` = `rowkeys_russian1`, 11 клавиш й ц у к е н г ш щ з х):
- у→ү (U+0443→U+04AF) ✓, е→ё (U+0435→U+0451) ✓, н→ң (U+043D→U+04A3) ✓, г→һ (U+0433→U+04BB) ✓, х→һ (U+0445→U+04BB) ✓

Ряд 2 (`rowkeys_tatar2` = `rowkeys_russian2`, 11 клавиш ф ы в а п р о л д ж э):
- а→ә (U+0430→U+04D9) ✓, о→ө (U+043E→U+04E9) ✓, ж→җ (U+0436→U+0497) ✓, э→ә (U+044D→U+04D9) ✓

Ряд 3 (`rowkeys_tatar3` = `rowkeys_russian3`, 9 клавиш я ч с м и т ь б ю):
- ь→ъ (U+044C→U+044A) ✓

Итого ровно 10 заявленных дублей, никаких лишних. `diff` подтвердил: russian1/2/3 посимвольно идентичны tatar1/2/3 (keySpec+moreKeys) — обе раскладки несут один и тот же набор дублей, расхождений быть не может.

## 3. Russian rowkeys vs стандартный ЙЦУКЕН — PASS

Russian-файлы идентичны tatar1–3, которые в ревью 02 побуквенно сверены с east_slavic `<default>`-ветками. Четыре бывших `!text/`-плейсхолдера сверены с TEXTS_ru (KeyboardTextsTable.java:3160+):
- `keyspec_east_slavic_row1_9` = U+0449 щ → литерал щ, ряд 1 поз. 9 ✓
- `keyspec_east_slavic_row2_2` = U+044B ы → литерал ы, ряд 2 поз. 2 ✓
- `keyspec_east_slavic_row2_11` = U+044D э → литерал э, ряд 2 поз. 11 ✓
- `keyspec_east_slavic_row3_5` = U+0438 и → литерал и, ряд 3 поз. 5 ✓

Белорусские/украинские варианты (ў U+045E, і U+0456) не просочились. Татарских букв в базовых keySpec нет — только в moreKeys. Основы й ц у к е н г ш з х / ф в а п р о л д ж / я ч с м т ь б ю — на местах.

## 4. kbd_russian — высотная математика — PASS

`kbd_russian.xml` байт-в-байт повторяет структуру `kbd_east_slavic.xml` (отличие — только include `rows_russian` вместо `rows_east_slavic`):
- `<default>`: голый `<Keyboard>` без overrides — стандартная 4-рядная высота (3 буквенных + action), как у east_slavic ✓
- `<case showNumberRow>`: `verticalGap/bonusHeight` = `*_5row`-фракции + `rowHeight="20%p"` → 5 рядов × 20% = 100% ✓

Татарские 5-рядные значения (`rowHeight="16.667%p"` в default-ветке kbd_tatar) **не** скопированы — у russian пятого ряда нет, математика верная. `rows_russian.xml` повторяет `rows_east_slavic.xml` (keyWidth 9.091%p / 9.091%p / 8.711%p + shift 10.8%p + fillRight + row_qwerty4) с точностью до имён include.

## 5. keyboard_layout_set_russian — Elements — PASS

6 элементов: alphabet→kbd_russian, symbols, symbolsShifted, phone, phoneSymbols, number — идентично east_slavic и qwerty. Имя `russian` резолвится через `KeyboardLayoutSet.Builder.getXmlId` (`getIdentifier("keyboard_layout_set_" + "russian", "xml", ...)` — KeyboardLayoutSet.java:239,275) → файл `keyboard_layout_set_russian.xml` существует ✓.

## 6. Реестр SubtypeLocaleUtils — PASS

- `LOCALE_TATAR = "tt_RU"` — присутствует в `sSupportedLocales` (строка 195); case `LOCALE_TATAR → addLayout(LAYOUT_TATAR); break` на месте (537-539).
- Группа east_slavic после выноса ru: ровно 4 локали — be_BY, kk, ky, uk → `LAYOUT_EAST_SLAVIC; break` (463-468) ✓. Общие east_slavic-файлы не тронуты.
- Новый `case LOCALE_RUSSIAN: addLayout(LAYOUT_RUSSIAN); break;` (469-471) — отдельный case, break стоит, fall-through в BULGARIAN исключён ✓.
- `getDefaultSubtypes` — детерминированная тройка `getDefaultSubtype(tt_RU / ru / en_US)`. NPE-безопасность: `getDefaultSubtype` возвращает null только при пустом списке от билдера; все три локали имеют непустые case-ветки (tt_RU→tatar, ru→russian, en_US→QWERTY+generic, где первым добавляется qwerty), т.е. null недостижим. Старый `getSubtypes(...).get(0)`-путь (IndexOutOfBounds-риск) удалён.
- Вызов только из `RichInputMethodManager.SubtypeList.reload` (159) и только когда prefs пусты/невалидны — выбор пользователя не перезатирается ✓.
- Round-trip prefs: `"tt_RU:tatar"`, `"ru:russian"`, `"en_US:qwerty"` → `getSubtype(locale, layout)` находит все три (expectedLayoutSet совпадает с добавляемым layout) ✓.
- Удалённые импорты `HashSet`, `java.util.Locale`, `LocaleUtils`: grep по файлу — других использований нет; `ArrayList`, `Arrays`, `List` по-прежнему используются (Arrays.asList:215, addLayout). Компиляция не пострадает.

## 7. Отображаемое имя «Татарча» — PASS

`<item>tt_RU</item>` в `locale_exception_keys` — формат идентичен существующим (en_US, en_GB, es_US, hi_ZZ, sr_ZZ). Механизм (`LocaleResourceUtils.initLocked`:81-88): `"string/locale_name_" + "tt_RU"` → `locale_name_tt_RU` — имя ресурса совпадает ✓, строка «Татарча» добавлена в strings.xml рядом с `locale_name_sr_ZZ` по образцу. `getLocaleDisplayNameInternal` возвращает строку из exception-map независимо от display-локали → имя фиксировано «Татарча» и в настройках, и на спейсбаре (FORMAT_TYPE_FULL_LOCALE) ✓. В `locale_displayed_in_root_locale` tt_RU корректно НЕ добавлен (этот массив требует пары `locale_name_in_root_locale_*` и здесь не нужен).

## 8. Constraints — PASS

- Дифф от 4de188c: только 12 заявленных файлов + `.planning/*` — минимальный ✓
- `AndroidManifest.xml` не тронут, INTERNET отсутствует ✓
- `build.gradle` не тронуты, зависимостей нет ✓
- Движок (keyboard/internal, KeyboardLayoutSet, парсеры) не тронут ✓

---

## Observations (low, не блокируют)

1. **Russian-раскладка теряет стоковые AOSP-дубли ru**: у east_slavic для ru из текст-таблиц шли у→(у́, ў), е→(ё, е́, ѣ), г→ґ, ы→(ы́, ꙑ). Литеральные файлы оставляют только фиксированные 10 дублей. Осознанный минимализм (акцентированные/исторические буквы для целевой аудитории не нужны), но зафиксировать: это отличие от Gboard/AOSP-поведения.

2. **keylabel_to_alpha для tt_RU = "ABC"**: у tt/tt_RU нет записи в `KeyboardTextsTable` → на клавиатуре символов клавиша «назад к буквам» покажет "ABC", тогда как у ru (TEXTS_ru) — "АБВ". Унаследовано из фазы 2, не регрессия этой фазы; починка потребует правки движковой таблицы — кандидат в backlog.

3. **Upgrade-путь с фазы 2**: установка фазы 2 хранит в prefs `"tt:tatar"`; после переименования локали в tt_RU `createSubtypesFromPref` вернёт пустой список → graceful fallback на новую тройку по умолчанию (без краша), но пользовательский список субтипов сбросится. Для пре-релизного APK приемлемо.

4. **Языковой fallback имени**: узкий спейсбар (`getLanguageDisplayNameInLocale`) и `getLayoutDisplayName` режут tt_RU до языка "tt" → ICU-имя («татар»/"Tatar"), не «Татарча». Проявляется только при нехватке ширины / в списке раскладок; поведение идентично другим не-predefined layout'ам форка (у ru тот же паттерн).

5. **Pre-existing (вне диффа)**: в switch у `case LOCALE_SAKHA` отсутствует `break` — fall-through добавляет сахаларам сербскую раскладку. Присутствует в базе 4de188c (унаследовано от upstream), этой фазой не тронуто. Кандидат на отдельный фикс/upstream-issue.

---

**Findings: 0 blockers / 0 warnings / 5 observations. Recommendation: PASS — фаза готова к коммиту оркестратором; device UAT (переключение глобусом, отображение «Татарча» на спейсбаре) остаётся отложенным, как заявлено в SUMMARY.**
