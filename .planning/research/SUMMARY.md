# Project Research Summary

**Project:** Tatar Keyboard (рабочее название)
**Domain:** Android IME (input method / custom keyboard)
**Researched:** 2026-07-18 (полный доменный ресерч выполнен до инициализации GSD; этот файл — конденсат `research/00-itog-i-roadmap.md`)
**Confidence:** HIGH

> Детальные секции — в `research/` в корне репозитория (01–08). Этот файл самодостаточен для роадмаппинга; за деталями фазовые агенты идут в соответствующую секцию (см. Sources).

## Executive Summary

Проект — нативная Android-клавиатура (IME) с татарской кириллической раскладкой, в стиле iOS, максимально лёгкая для бюджетных устройств, полностью офлайн. Экспертный способ сборки таких продуктов — форк существующей open-source базы, не написание с нуля: «своё IME в 1–2 тыс. строк» — это уже написанный и оттестированный Simple Keyboard (AOSP LatinIME без словарного движка).

Принятый подход: **форк Simple Keyboard (rkkr)** — Apache-2.0, APK ~0.65 МБ, один модуль, посильный новичку. Новый код на Kotlin через interop, UI — один кастомный View с Canvas (без Compose в IME-процессе). Татарская раскладка = стандартная ЙЦУКЕН + отдельный видимый пятый ряд `ә ө ү җ ң һ` (ә — 5-я по частоте буква, 6.65%) + long-press дубли на родственных русских буквах.

Ключевые риски: (1) зоопарк InputConnection (WebView keyCode 229, кастомные редакторы) — главный источник багов IME; митигация — без composing-текста в MVP, тестовая матрица в каждой фазе; (2) MIUI/HyperOS убивают IME-процесс — холодный старт < 400 мс есть главная метрика; (3) MVP без автокоррекции может «не зайти» — план Б (форк HeliBoard, цена GPL-3.0) держать до больших вложений в скин.

## Key Findings

### Recommended Stack

Консенсус всех секций ресерча, противоречий нет (детали: `research/01`, `research/06`).

**Core technologies:**
- Форк Simple Keyboard (rkkr): база — Apache-2.0, лёгкость, знакомый AOSP-скелет; уникальный applicationId обязателен
- Kotlin (новый код) + Java (база, не конвертировать массово): interop бесплатный
- Кастомный View + Canvas: один View на клавиатуру, клавиши в onDraw — как AOSP LatinIME/HeliBoard/Gboard
- Android Studio Quail, AGP 9.x, Gradle 8.14+/9.x, Kotlin 2.3+; minSdk 24–26, targetSdk/compileSdk 36
- Запрещено: Compose в IME-процессе (+20–40 МБ RAM), Flutter/RN (нет Activity, Dart VM/Hermes +100–200 МБ), NDK/C++, разрешение INTERNET, сторонние зависимости

### Expected Features

Детали: `research/05` (раскладка), `research/07` (scope MVP).

**Must have (table stakes — MVP):**
- Раскладки tt/ru/en + слои ?123 и #+=; раскладки — данными (XML), не кодом
- Subtypes tt_RU/ru/en_US, глобус (тап = цикл, long-press = системный пикер)
- Shift/caps-lock (двойной тап), автокапитализация, автоповтор backspace (по кодпоинтам!), Enter по imeOptions, двойной пробел = точка, свайп по пробелу = курсор, multi-touch
- iOS-скин: палитра (светлая #D4D6DD/#FFF/#B3B7C0, тёмная #2C2C2C/#6B6B6B/#474747), радиус ~5dp, 1dp-тень, баллон-превью, long-press панель, реакция на ACTION_DOWN, хаптика KEYBOARD_TAP/системный звук
- Доступность: ExploreByTouchHelper, a11y-описания татарских букв; password-поля, edge-to-edge API 35+, WebView, ландшафт
- Онбординг («Включить»/«Выбрать») + минимальные настройки

**Should have (differentiators):**
- Пятый видимый ряд ә ө ү җ ң һ — ни у Gboard/SwiftKey/Яндекса нет (у них татарский «второго сорта» через long-press)
- «Без INTERNET» как проверяемая гарантия приватности
- Лёгкость: APK ≤ 3 МБ против ~20 МБ у конкурентов

**Defer (v2+ / следующий майлстоун):**
- Автокоррекция и подсказки (свой Kotlin-движок: префиксный поиск + Дамерау–Левенштейн ≤2), словарь ttwiki+Leipzig 150–250 тыс. словоформ, билингвальный tt+ru
- Эмодзи-панель, история буфера, компактный режим, Windows-раскладка, латиница
- Исключено навсегда: свайп-ввод, голосовой ввод (tt не подтверждён), свой C++

### Architecture Approach

Детали: `research/01` (жизненный цикл IME), `research/02` (рендеринг). Один сервис, один рисующий View, модель раскладки — данные.

**Major components:**
1. `TatarImeService : InputMethodService` (directBootAware, onEvaluateFullscreenMode()=false) — жизненный цикл, InputConnection, subtypes
2. `InputViewContainer` (FrameLayout) — insets-паддинг для edge-to-edge API 35+
3. `KeyboardView : View` — Canvas-отрисовка: клавиши, подсветка, превью; ноль аллокаций в onDraw
4. `KeyboardLayout` — модель List<Row<Key>> с долями ширины, парсится из данных
5. `PointerTracker[N]` — multi-touch, таймеры long-press/автоповтора
6. `KeyPreviewDrawer` / `PopupPanelDrawer` — баллон и long-press панель в том же слое (не PopupWindow)
7. `KeyboardAccessibilityDelegate` — ExploreByTouchHelper, клавиши = виртуальные узлы
8. `KeyboardTheme` — light/dark, iOS-палитра в коде

### Critical Pitfalls

Детали: `research/03` (§9–10), `research/00` §8.

1. **Зоопарк InputConnection** (WebView keyCode 229, Compose TextField, редакторы банков) — без composing-текста в MVP, коммит сразу, `deleteSurroundingTextInCodePoints`; ручная матрица (Telegram, Chrome/WebView, пароли, MIUI/One UI) в каждой UI-фазе
2. **Холодный старт — норма, не исключение** (LMK/MIUI убивают фоновый IME) — бюджет < 400 мс, всё тяжёлое асинхронно после первого кадра, ленивая инициализация
3. **Аллокации в горячем пути** → GC-паузы при печати — все Paint/Rect как поля, точечный invalidate(rect), 0 GC-событий при печати
4. **Юридика Apple** — SF Pro/SF Symbols/звуки/маркетинг «iOS» запрещены; Roboto + свои VectorDrawable; геометрия и палитра безопасны
5. **Доступность прикручивать потом больно** — ExploreByTouchHelper закладывать при первой версии KeyboardView

## Implications for Roadmap

Черновой roadmap из `research/00` §7 (этап 0 → MVP), адаптированный под решения инициализации: этап 0 пропущен (форк окончателен), майлстоун = MVP + релиз на GitHub/IzzyOnDroid, режим Vertical MVP, фазы мелкие, **каждая фаза завершается собирающимся APK**.

### Phase: Форк и hello-world
**Rationale:** фундамент всего; «учебник по IME» читается через рабочую сборку
**Delivers:** собранный форк Simple Keyboard с новым applicationId, печатает из коробки; keystore; CI-проверка отсутствия INTERNET; Kotlin подключён
**Avoids:** нарушение Apache-2.0 (уникальный applicationId)

### Phase: Татарская раскладка
**Rationale:** ядро ценности (пятый ряд) до косметики; раскладка в базе — правка данных, быстрый выигрыш
**Delivers:** tt-раскладка с пятым рядом + long-press дубли; ru/en; слои ?123/#+=; subtypes и глобус
**Addresses:** главный дифференциатор

### Phase: Механика ввода
**Rationale:** полный цикл ввода нужен до скина — скин рисует состояния (shift, попапы)
**Delivers:** shift/caps-lock/автокапитализация, автоповтор backspace по кодпоинтам, Enter по imeOptions, двойной пробел, свайп-курсор, multi-touch

### Phase: iOS-скин (Canvas)
**Rationale:** самая объёмная переписка (KeyboardView); после стабильной механики
**Delivers:** отрисовка по палитре, light/dark, баллон-превью, long-press панель, хаптика/звук
**Avoids:** аллокации в onDraw, PopupWindow, ассеты Apple

### Phase: Совместимость и доступность
**Rationale:** проблемные окружения и a11y — до полировки
**Delivers:** password-поля, WebView, edge-to-edge, ландшафт, ExploreByTouchHelper, тестовая матрица пройдена

### Phase: Онбординг и настройки
**Delivers:** активити онбординга, минимальные настройки (звук/вибро, высота)

### Phase: Производительность и релиз
**Rationale:** финальная верификация бюджетов + публикация = граница майлстоуна
**Delivers:** замеры (meminfo, старт, gfxinfo), подписанный release-APK ≤ 3 МБ, privacy policy, GitHub Releases, заявка IzzyOnDroid

### Phase Ordering Rationale

- Вертикальные слайсы: после каждой фазы клавиатурой можно пользоваться, эргономика пятого ряда проверяется с первой же фазы раскладки (замена пропущенного этапа 0)
- Механика до скина: скин рисует состояния механики; обратный порядок = двойная работа
- Совместимость отдельной фазой, но smoke-тесты матрицы — в каждой фазе (митигация InputConnection-риска)
- Бюджеты производительности — контроль в каждой фазе (ноль аллокаций закладывается при написании, не после), финальный замер перед релизом

### Research Flags

Phases likely needing deeper research during planning:
- **Форк и hello-world:** структура кодовой базы Simple Keyboard (живой код, не покрыт нашим ресерчем пофайлово) — фазовому ресерчеру читать реальные исходники форка
- **iOS-скин:** точные метрики отрисовки KeyboardView в форке — что переписывать, что переиспользовать
- **Совместимость:** актуальные списки known issues MIUI/HyperOS для IME

Phases with standard patterns (skip research-phase):
- **Онбординг и настройки:** тривиальные Activity, паттерны в `research/08`
- **Механика ввода:** полностью покрыта `research/01` + исходники базы

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Консенсус 8 секций ресерча, официальные доки, живые кодовые базы |
| Features | HIGH | Scope зафиксирован в BRIEF.md, конкуренты разобраны в research/05 |
| Architecture | HIGH | Паттерн AOSP LatinIME/HeliBoard, проверен реверсом исходников |
| Pitfalls | HIGH | research/03 + многолетние issue-трекеры HeliBoard/AOSP |

**Overall confidence:** HIGH

### Gaps to Address

- Пофайловое устройство Simple Keyboard — закрыть фазовым ресерчем первой фазы по реальным исходникам форка
- minSdk 24 vs 26 — решить перед релизной фазой
- Финальное название/applicationId — зафиксировать в фазе форка (рабочее: Tatar Keyboard)
- Порядок клавиш пятого ряда (алфавитный vs частотный) — юзер-тест после MVP, не блокер

## Sources

### Primary (HIGH confidence)
- `research/00-itog-i-roadmap.md` — сводный итог, разрешённые противоречия, roadmap
- `research/01-stek-i-arhitektura-ime.md` — InputMethodService, InputConnection, subtypes, манифест
- `research/02-ui-rendering.md` — Canvas-подход, key preview, multi-touch, темизация, a11y
- `research/03-optimizaciya-slabye-ustroystva.md` — бюджеты, инструменты замера, тестовая матрица
- `research/06-fork-ili-s-nulya.md` — вердикт «форк Simple Keyboard» (единственный источник правды по базе)

### Secondary (MEDIUM confidence)
- `research/04-stil-apple.md` — палитра/геометрия iOS (реверс через KeyboardKit), юридические границы
- `research/05-tatarskaya-raskladka.md` — частотность букв, раскладка, конкуренты
- `research/07-funkcional-mvp-predikciya.md` — scope MVP/v1, словари (для следующего майлстоуна)
- `research/08-distribuciya.md` — GitHub/IzzyOnDroid (релизная фаза), RuStore/Play/F-Droid (следующий майлстоун)

---
*Research completed: 2026-07-18*
*Ready for roadmap: yes*
