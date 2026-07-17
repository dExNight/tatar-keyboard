# Open-source клавиатуры: форкать или писать с нуля

Ресерч по состоянию на **июль 2026**. Цель проекта: нативная Android IME с татарской раскладкой (кириллица + ә ө ү җ ң һ), визуальный стиль системной клавиатуры iOS, максимальная лёгкость и отзывчивость на бюджетных устройствах.

Все цифры (звёзды, релизы, размеры APK) проверены по GitHub и F-Droid в июле 2026.

---

## Сводная таблица

| Проект | Лицензия | Языки кода | Звёзды | Последний релиз | APK (F-Droid, universal) | Предикшен |
|---|---|---|---|---|---|---|
| HeliBoard | GPL-3.0 | Kotlin 37% / Java 32% / C++ 31% | 5.7k | **4.0, 10 июля 2026** | ~21.7 МБ | да (AOSP-словари) |
| FlorisBoard | Apache-2.0 | Kotlin 98% (Compose) | 8.5k | 0.5.2, 28 ноя 2025 (beta) | ~13.8 МБ | **нет** (план на v0.6) |
| Simple Keyboard (rkkr) | Apache-2.0 | Java 100% | 1.5k | 6.4, 23 мая 2026 | **~0.65 МБ** | нет (принципиально) |
| AnySoftKeyboard | Apache-2.0 | Java 74% / C++ 4% | 3.3k | 1.13-r1, 8 фев 2026 | ~24.3 МБ | да |
| Fossify Keyboard | GPL-3.0 | Kotlin 99.8% | 633 | 1.9.1, 2 фев 2026 | ~16.7 МБ | нет |
| AOSP LatinIME | Apache-2.0 | Java + C++ | — (не GitHub) | maintenance mode | — | да (движок-эталон) |

Примечание: APK с F-Droid — universal-сборки со всеми ABI; при сборке под конкретные ABI (splits/App Bundle) размер HeliBoard/AnySoftKeyboard падает примерно вдвое (нативная либа словарей собирается под 4 архитектуры).

---

## 1. HeliBoard (наследник OpenBoard → AOSP LatinIME)

**Репо:** github.com/Helium314/HeliBoard. 5.7k звёзд, ~2470 коммитов, 68 релизов, релиз 4.0 от 10.07.2026 — самый живой из AOSP-наследников. Финансируется через NLnet (NGI Mobifree).

**Лицензия:** GPL-3.0 (как форк OpenBoard); внутри также Apache-2.0 файлы из AOSP. Следствие: ваш форк обязан быть GPL-3.0, исходники — публичными. Для бесплатной открытой татарской клавиатуры это не проблема; закрытая монетизация исключена.

**Архитектура:** классический AOSP-стек — `LatinIME` (InputMethodService), `KeyboardSwitcher`, `MainKeyboardView` (наследник собственного `KeyboardView`, рисование через Canvas, не android.inputmethodservice.KeyboardView). Словарный движок — нативный C++ из AOSP (binary-словари `.dict`), отсюда C++ 31% кодовой базы. Полностью offline, без интернет-пермишена.

**Добавление раскладки — самый низкий порог из всех:**
- Раскладки — текстовые файлы в `app/src/main/assets/layouts/main/` (например `russian.txt`, `russian_extended.txt`). **Simple-формат**: одна строка = одна клавиша (`метка попап1 попап2 ...`), пустая строка = новый ряд. Поддерживается и JSON-формат, совместимый с layout-спекой FlorisBoard (`case_selector`, `multi_text_key` и т.д.).
- Попапы для букв языка — в `assets/locale_key_texts/<locale>.txt` в секции `[popup_keys]` (есть `ru.txt`, `kk.txt`; **татарского `tt.txt` нет** — придётся добавить).
- Регистрация subtype — `res/xml/method.xml` (`android:imeSubtypeLocale="tt"`, `KeyboardLayoutSet` в `imeSubtypeExtraValue`), строка имени в `strings.xml`, для нестандартных локалей — правка `LocaleUtils.localizedDisplayName`.
- Важно: HeliBoard позволяет добавлять **кастомную раскладку + произвольную локаль прямо из настроек приложения, без форка вообще** — можно за вечер прототипировать татарскую раскладку в установленном HeliBoard и проверить её эргономику до написания кода.

Пример татарской раскладки в simple-формате (расширенный ЙЦУКЕН, вариант «попапы на базовых буквах»):

```
й
ц
у ү
к
е
н ң
г
ш
щ
з
х һ
ъ

ф
ы
в
а ә
п
р
о ө
л
д
ж җ
э

я
ч
с
м
и
т
ь
б
ю
```

**Предикшен/автокоррекция:** есть, на базе AOSP-движка; работает со словарями формата AOSP `main_<lang>.dict` (компилируются `dicttool_aosp` из частотного wordlist). Готового татарского словаря в официальном репо словарей (Codeberg: Helium314/aosp-dictionaries) нет — нужно собрать самим из корпуса (это отдельная тема ресерча). Качество предикшена — «AOSP-уровень», хуже Gboard, но реально работает.

**iOS-стиль:** из коробки — темы, свои цвета, скругления и бордеры клавиш; полноценный iOS-вид (белые клавиши с тенью, шрифт, отступы) потребует правок отрисовки в `KeyboardView`/drawables — умеренный объём работы, рисование Canvas-ное и понятное.

**Память/производительность:** точных публичных замеров нет. Ориентир: View+Canvas UI, нативный словарный движок — исторически один из самых лёгких стеков; OpenBoard/AOSP-наследники нормально живут на бюджетниках. Основной вес RAM — загруженные словари.

## 2. FlorisBoard

**Репо:** github.com/florisboard/florisboard. 8.5k звёзд, Apache-2.0, Kotlin 98%, UI на **Jetpack Compose**. Последний релиз 0.5.2 (28.11.2025) — **всё ещё beta**; v0.7 заявлен как выход в public beta в Google Play.

**Критично:** **предикшена и spell check нет** — заявлены как «major goal for the v0.6 milestone» уже несколько лет. Есть Smartbar, клипборд-менеджер, glide, темы.

**Раскладки:** JSON в `app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/characters/` (~70+ файлов; есть `jcuken_russian.json`, `udmurt_compact.json`; **татарского нет**). Формат мощный (селекторы по shift/типу поля), тот самый, который частично поддержал HeliBoard. Планируется общая библиотека парсинга раскладок k3lp.

**iOS-стиль:** сильная сторона — theming-движок **Snygg** (JSON-стайлшиты: background, border, shadow-elevation, corner radius, переменные `--var`). iOS-тему можно сделать почти без кода. Но: Snygg v1/v2 несовместимы между версиями, тема — не форк-friendly инвестиция.

**Минусы под наши цели:** Compose даёт заметно больший baseline по RAM и холодному старту, чем View/Canvas — на бюджетных устройствах это ощутимо; beta-статус; отсутствие автокоррекции — дыра ровно там, где она нам нужна.

## 3. Simple Keyboard (rkkr)

**Репо:** github.com/rkkr/simple-keyboard. 1.5k звёзд, Apache-2.0, **Java 100%**, релиз 6.4 от 23.05.2026, ~418 коммитов. Это AOSP LatinIME с **вырезанным** словарным движком, эмодзи и прочим: остались клавиатура, темы-цвета, number row, курсор свайпом по пробелу. APK **~0.65 МБ** — на порядок меньше остальных.

**Раскладки:** AOSP-формат — XML в `res/xml/` (`rowkeys_*.xml`, `keyboard_layout_set_*.xml`) + `method.xml`. Формат многословнее HeliBoard-овских txt, но прямолинейный: добавить татарскую раскладку — скопировать XML русской, вставить 6 букв (отдельными клавишами или `moreKeys` для попапов).

**Предикшен:** нет и не будет by design («doesn't have and probably never will: spell checking, swipe typing»).

**iOS-стиль:** темы — только цвета; iOS-вид потребует правок drawables и отрисовки, но кодовая база маленькая (в разы меньше HeliBoard), для новичка это лучший «учебник по IME»: видно весь путь от `InputMethodService` до Canvas без словарного C++.

**Лёгкость:** эталон. Нет нативных либ, нет словарей в памяти. Для слабых устройств — лучший вариант из всех.

## 4. AnySoftKeyboard

**Репо:** github.com/AnySoftKeyboard/AnySoftKeyboard. 3.3k звёзд, Apache-2.0, релиз 1.13-r1 (08.02.2026), 8.8k коммитов — активен, автоматизированный релизный конвейер (main → alpha → beta еженедельно).

**Архитектура:** независимая (не AOSP-форк), проект с 2009 года. Монорепо: `ime/` (ядро), `addons/` (языки/темы как **отдельные APK-пакеты**), двойная сборка Gradle + Bazel, C++ для словарей, даже TypeScript-тулинг. Раскладки — XML в language pack'ах (документировано в `addons/languages/PACKS.md`); есть next-word suggestions, словари паков можно смешивать.

**Под наши цели:** самая тяжёлая для входа кодовая база (легаси 15+ лет, свои абстракции, сложная сборка), APK ~24 МБ, UI — старый View-стек, перерисовка в iOS-стиль возможна, но придётся копать глубоко. Система внешних language pack'ов — оверкилл для одноязычной клавиатуры. Не рекомендую как базу.

## 5. Fossify Keyboard

**Репо:** github.com/FossifyOrg/Keyboard (форк Simple Keyboard от Simple Mobile Tools после продажи SMT компании ZipoApps). 633 звезды, GPL-3.0, Kotlin 99.8%, релиз 1.9.1 (02.02.2026).

Простая клавиатура с клипбордом, **без предикшена/автокоррекции**, собственный самописный UI (не AOSP). Ниша — «клавиатура для экосистемы Fossify». Раскладок мало, формат свой. Минусы: GPL без компенсирующих преимуществ (предикшена нет, как и у Apache-альтернатив), маленькое сообщество, кодовая база не даёт ничего, чего нет у Simple Keyboard/HeliBoard. Не рекомендую.

## 6. AOSP LatinIME (напрямую)

Источник: `android.googlesource.com/platform/packages/inputmethods/LatinIME`. Apache-2.0, Java + C++. Фактически **maintenance mode** — Google развивает закрытый Gboard, LatinIME годами получает только косметические правки. Собирается системой сборки AOSP (Android.bp), не Gradle — превращение в standalone-приложение это ровно та работа, которую уже сделали OpenBoard → HeliBoard и rkkr. Форкать напрямую бессмысленно; ценность — как **Apache-2.0-источник словарного движка** (`native/jni`, dicttool), который можно легально встроить в Apache-проект (например, в форк Simple Keyboard) без GPL-обязательств.

---

## Писать с нуля?

Что придётся сделать самому: `InputMethodService` + `onCreateInputView`, собственная отрисовка клавиш (`android.inputmethodservice.KeyboardView` **deprecated с API 29**, использовать нельзя), обработка touch/multitouch/long-press попапов, `InputConnection` со всеми краевыми случаями (password-поля, `imeOptions`, composing text, курсор, разные `inputType`), subtypes, темы, landscape, split-screen. Это 2–4 месяца работы для новичка только до уровня «стабильно печатает без предикшена» — и вы всё равно будете подглядывать в исходники Simple Keyboard. Предикшен с нуля — ещё дороже.

**Вывод: писать с нуля не нужно.** Всё, что мы хотим, уже есть в форкабельном виде; уникальная работа проекта — раскладка, iOS-скин и (позже) татарский словарь, а не переизобретение IME-обвязки.

---

## Рекомендация

**Базовая стратегия: форкать Simple Keyboard (rkkr) для MVP; словарный движок при необходимости брать из AOSP LatinIME (Apache-2.0).**

Аргументы:

1. **Лёгкость — главный приоритет проекта, и здесь Simple Keyboard вне конкуренции**: APK 0.65 МБ против 14–24 МБ у остальных, ноль нативных либ, Java 100%, минимум зависимостей. На бюджетных устройствах это единственная база, которая гарантированно «летает».
2. **Apache-2.0** — полная свобода лицензирования в будущем (в отличие от GPL-3.0 у HeliBoard/Fossify).
3. **Размер кодовой базы посилен новичку**: ~400 коммитов, один модуль, знакомый AOSP-скелет — реально прочитать и понять целиком. HeliBoard (2.5k коммитов + C++) и тем более AnySoftKeyboard (8.8k коммитов) новичок целиком не охватит.
4. **Татарская раскладка** добавляется правкой XML (`rowkeys_*`, `method.xml`) — день работы. Схема: базовый ЙЦУКЕН + шесть букв ә ө ү җ ң һ отдельными клавишами (доп. ряд/уплотнение) и/или `moreKeys`-попапами на а/о/у/ж/н/х.
5. **iOS-стиль** — в маленькой кодовой базе перерисовать drawables/Canvas проще и предсказуемее, чем перебарывать чужую тему-систему.
6. **Путь апгрейда до предикшена не закрыт**, но не через порт AOSP-движка. ~~Перенести словарный движок из LatinIME в наш форк «несложнее, чем поддерживать HeliBoard-форк»~~ — это утверждение снято по результатам доисследования (см. «Дополнение», пункт а): реальная цена порта сопоставима с пересборкой половины HeliBoard. Апгрейд-путь для форка Simple Keyboard — собственный простой предикшен (префиксный поиск + Дамерау–Левенштейн по частотному словарю, как в 07 §2, путь 2).

**План Б — форк HeliBoard**, если по результатам прототипа решим, что автокоррекция/предикшен нужны с первой версии: там движок уже работает, раскладка добавляется текстовым файлом, проект самый живой (релиз июля 2026, грант NLnet). Цена: GPL-3.0, +20 МБ APK, C++-часть, больший объём кода для iOS-рестайла.

**Практический первый шаг (без единой строки кода):** установить HeliBoard и через Settings → Layouts добавить кастомную татарскую раскладку текстовым файлом — за вечер отладить эргономику раскладки на живой клавиатуре, и только потом переносить финальную схему в свой форк Simple Keyboard.

FlorisBoard не берём как базу (beta, нет предикшена, Compose тяжёл для бюджетников), но **заимствуем**: JSON-спека раскладок и Snygg-подход к темам — хорошие референсы дизайна. AnySoftKeyboard и Fossify — не подходят (сложность/отсутствие преимуществ).

---

## Дополнение: сведение решения по базе проекта (единый вердикт)

Ресерчи 01 (§12), 06 и 07 (§2) давали три расходящиеся рекомендации. Ниже противоречия сняты по результатам доисследования (июль 2026); **этот раздел — единственный источник правды по выбору базы проекта**, 01 и 07 теперь ссылаются сюда.

### (а) Реальная цена переноса AOSP-движка в Simple Keyboard: прав 07, а не 06

Проверено по исходникам и по всем известным попыткам реиспользования движка:

- **Simple Keyboard не содержит словарного пайплайна вообще.** В дереве репозитория rkkr/simple-keyboard (853 файла, проверено через GitHub API, июль 2026) нет ни одного класса `Suggest*`, `*Dictionary*`, `WordComposer` — rkkr вырезал их подчистую ещё в 2018, остался только скелет `ProximityInfo.java`. «Вернуть движок» — это не revert одного коммита: за ~8 лет и 400+ коммитов кодовая база разошлась с LatinIME.
- **Объём того, что пришлось бы перенести** (по дереву HeliBoard, где движок живёт целиком): ~300 файлов C++/заголовков/мейкфайлов в `app/src/main/jni` (сборка через ndkBuild + NDK) **плюс 40+ Java/Kotlin-классов** словарно-подсказочного пайплайна (`BinaryDictionary`, `DictionaryFacilitator*`, `Suggest`, `SuggestedWords`, `WordComposer`, `ExpandableBinaryDictionary`, `SuggestionStripView` и т.д.), которые вплетены в `InputLogic`, настройки и UI. Это не «модуль», а подсистема со связями во все стороны.
- **Прецедентов чистого «выдирания» движка в чужой проект нет.** Все, кто реиспользовал AOSP-движок, делали **полный форк LatinIME**: OpenBoard → HeliBoard, FUTO Keyboard (форк LatinIME командой опытных разработчиков, и то — годы работы), Indic Keyboard, wikimedia/aosp-morelangs-ime (2012, заброшен). FlorisBoard осознанно **отказался** портировать AOSP-движок и много лет пишет свой NLP-стек — предикшена нет до сих пор (issue #325).
- **Сам rkkr** держит позицию «doesn't have and probably never will» (issues #67, #97 закрыты) — upstream-помощи в таком порте не будет.

**Вывод:** утверждение из первоначальной редакции 06 («перенести движок несложнее, чем поддерживать HeliBoard-форк») — неверно. Правильная оценка — из 07 §2: гибрид «своё/лёгкое IME + нативный AOSP-движок» — **самый дорогой путь для новичка, не делаем**. Если AOSP-предикшен действительно нужен — его надо брать **вместе с HeliBoard целиком**, а не выпиливать.

### (б) Kotlin (рекомендация 01) vs Java-база Simple Keyboard

Конфликт разрешается штатной interop-моделью, конвертация не нужна:

- Форкаем Java-базу **как есть** и не трогаем работающий код: массовая конвертация ~сотни файлов в Kotlin не даёт пользователю ничего, а новичку даёт риск регрессий в самом хрупком месте (InputConnection/edge cases).
- **Весь новый код пишем на Kotlin** в том же модуле: с AGP 9.0 (2026) поддержка Kotlin встроена в плагин, Java↔Kotlin interop бесплатный. Новый код проекта — это ровно наши уникальные части: iOS-скин (отрисовка), логика раскладки, позже простой предикшен.
- Точечная конвертация Java-файла (Android Studio, `Convert Java File to Kotlin File`) — только когда файл всё равно существенно переписывается под нашу задачу.
- Прецедент жизнеспособности смешанной базы — сам HeliBoard (Java 32% / Kotlin 37% в одном модуле, годами).

Рекомендация Kotlin из 01 остаётся в силе — она про *новый* код, и Simple Keyboard ей не противоречит.

### (в) Итоговый вердикт и дерево выбора

```
Нужны автокоррекция/предикшен уровня AOSP уже в первой версии,
и GPL-3.0 приемлема?
├─ ДА  → форк HeliBoard (движок, словари, subtypes готовы;
│        цена: GPL, ~20 МБ, большая кодовая база, C++)
└─ НЕТ (MVP без предикшена; приоритеты: лёгкость, Apache-2.0,
   посильная новичку база)
   → форк Simple Keyboard (rkkr)  ← ВЫБОР ПО УМОЛЧАНИЮ
     · новый код — на Kotlin (см. пункт б)
     · предикшен в v1 — свой простой: частотный словарь +
       префиксный поиск + Дамерау–Левенштейн (07 §2, путь 2)
     · порт AOSP-движка — НЕ путь апгрейда (см. пункт а);
       если без AOSP-предикшена продукт не живёт —
       мигрируем на форк HeliBoard ДО больших вложений в iOS-скин

Писать с нуля? — Нет. «Своё IME с нуля» из 01 §12 (1–2 тыс. строк ядра)
по факту и есть Simple Keyboard, уже написанный и оттестированный;
с нуля имеет смысл только как учебное упражнение.
```

Контрольная точка для ветвления: прототип раскладки в установленном HeliBoard (см. «Практический первый шаг» выше) + 1–2 недели личного использования. Если ощущение «без автокоррекции неюзабельно» — ветка HeliBoard; иначе — Simple Keyboard.

Файлы 01 (§12) и 07 (§2) обновлены: их локальные рекомендации заменены ссылкой на этот раздел.

---

## Источники

- HeliBoard: https://github.com/Helium314/HeliBoard (README, релиз 4.0 от 10.07.2026)
- HeliBoard, формат раскладок: https://github.com/Helium314/HeliBoard/blob/main/layouts.md
- HeliBoard, раскладки и локали в исходниках: `app/src/main/assets/layouts/main/`, `app/src/main/assets/locale_key_texts/`, `app/src/main/res/xml/method.xml` (проверено через GitHub API, июль 2026)
- FlorisBoard: https://github.com/florisboard/florisboard (README, релиз 0.5.2)
- FlorisBoard, JSON-раскладки: https://github.com/florisboard/florisboard/tree/main/app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/characters
- FlorisBoard, темы Snygg: https://docs.florisboard.org/themes/ и https://github.com/florisboard/florisboard/discussions/1531
- Simple Keyboard: https://github.com/rkkr/simple-keyboard (README, релиз 6.4)
- AnySoftKeyboard: https://github.com/AnySoftKeyboard/AnySoftKeyboard (README, релиз 1.13-r1)
- Fossify Keyboard: https://github.com/FossifyOrg/Keyboard (README, релиз 1.9.1)
- AOSP LatinIME: https://android.googlesource.com/platform/packages/inputmethods/LatinIME/
- Размеры APK: F-Droid, HEAD-запросы к https://f-droid.org/repo/ (helium314.keyboard_4005, dev.patrickgold.florisboard_117, rkr.simplekeyboard.inputmethod_145, com.menny.android.anysoftkeyboard_8175, org.fossify.keyboard_14), июль 2026
- Обзоры-сравнения: https://www.howtogeek.com/open-source-android-keyboards-that-rival-gboard/ , https://www.makeuseof.com/best-open-source-gboard-alternatives-tested/
- Татарская раскладка (референсы схем размещения ә ө ү җ ң һ): https://speak.tatar/ru/lang/keyboard/ , http://tatsoft.tatar/ru/portfolio-item/tatarskaya-klaviatura-dlya-macos/
- Дополнение (а): дерево HeliBoard `app/src/main/jni` (~300 файлов движка, ndkBuild) и словарные Java/Kotlin-классы — проверено через GitHub API (git/trees, июль 2026); дерево rkkr/simple-keyboard (853 файла, пайплайн отсутствует) — там же
- FUTO Keyboard — полный форк AOSP LatinIME с предикшеном (FUTO Source First License, не OSI): https://github.com/futo-org/android-keyboard , https://gitlab.futo.org/keyboard/latinime
- FlorisBoard: отказ от порта AOSP-движка, свой NLP-стек (многолетний тред): https://github.com/florisboard/florisboard/issues/325
- Simple Keyboard: позиция по предикшену/spell check (закрытые FR): https://github.com/rkkr/simple-keyboard/issues/67 , https://github.com/rkkr/simple-keyboard/issues/97
- wikimedia/aosp-morelangs-ime — ранний (2012) полный форк LatinIME ради движка, заброшен: https://github.com/wikimedia/aosp-morelangs-ime
- AOSP BinaryDictionary JNI (пример связей движка): https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/refs/heads/jb-mr1-dev-plus-aosp/native/jni/com_android_inputmethod_latin_BinaryDictionary.cpp
