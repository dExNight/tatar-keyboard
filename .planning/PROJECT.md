# Tatar Keyboard (рабочее название)

## What This Is

Нативная Android-клавиатура (IME) с татарской кириллической раскладкой — стандартная ЙЦУКЕН плюс отдельный видимый пятый ряд для ә ө ү җ ң һ. Визуально — в стиле системной клавиатуры iOS (в юридически безопасных границах), максимально лёгкая и отзывчивая на бюджетных устройствах (Xiaomi/Redmi, Samsung A-серия). Полностью офлайн, без разрешения INTERNET, open-source (Apache-2.0). Для татароязычных пользователей в Татарстане/России; разработчик — соло, новичок в Android.

## Core Value

Татарский язык — первым классом: печатать по-татарски так же быстро и приятно, как по-русски в Gboard, — на самом дешёвом телефоне, без интернета.

## Requirements

### Validated

- ✓ «Живость»: баллон-превью в слое (не PopupWindow), long-press панель со скольжением, хаптика+подсветка+звук на ACTION_DOWN (звук по умолчанию OFF), iOS-стилизация превью/панели — Phase 7 (on-device UAT отложен, принят)

- ✓ iOS-скин: Canvas-рендер по зафиксированной палитре/геометрии, light/dark, тема id=7 по умолчанию, собственные иконки, PERF-фиксы горячего пути, каркас ExploreByTouchHelper (+54 КБ APK) — Phase 6 (on-device UAT отложен, принят)

- ✓ Жесты: двойной пробел → точка (AOSP-паттерн, 9 путей сброса состояния), свайп-курсор включён по умолчанию, multi-touch commit-on-second-touch — Phase 5 (on-device UAT отложен, принят)

- ✓ Механика символьного ввода (shift 3 состояния, автокапитализация, backspace по кодпоинтам с автоповтором, Enter по imeOptions) подтверждена в базе форка структурно, zero-code фаза — Phase 4 (on-device UAT отложен, принят)

- ✓ Три языка (tt_RU «Татарча» / ru / en_US), long-press дубли 10 пар на обеих кириллических раскладках, глобус цикл+пикер, персистентность subtype — Phase 3 (on-device подтверждение отложено, принято)

- ✓ Татарская раскладка как XML-данные: ЙЦУКЕН + пятый ряд `ә ө ү җ ң һ` (алфавитный, сверху), слои ?123/#+= работают, новая раскладка = только XML + 4 строки реестра — Phase 2 (on-device подтверждение отложено, принято)

- ✓ Форк собирается и подписывается под уникальным applicationId (`org.tatarkeyboard.ime` + `.debug`); Kotlin interop работает; манифест без INTERNET с двухуровневой проверкой + CI-workflow — Phase 1 (on-device и GitHub-side доказательства отложены, приняты пользователем)

### Active

- [ ] Раскладки tt (пятый ряд ә ө ү җ ң һ + long-press дубли), ru, en + слои ?123 / #+=
- [ ] Три subtype (tt_RU / ru / en_US), переключение глобусом
- [ ] Полный цикл ввода: shift/caps-lock, автокапитализация, автоповтор backspace, Enter по imeOptions, свайп по пробелу = курсор, multi-touch
- [ ] iOS-скин: Canvas-отрисовка, палитра light/dark, баллон-превью, long-press панель, хаптика/звук на ACTION_DOWN
- [ ] Доступность: ExploreByTouchHelper, контент-описания татарских букв
- [ ] Корректная работа в проблемных окружениях: password-поля, WebView (keyCode 229), edge-to-edge API 35+, ландшафт, MIUI/HyperOS
- [ ] Онбординг (включение/выбор IME) + минимальные настройки
- [ ] Release-ready: подписанный APK ≤ 3 МБ, privacy policy, публикация GitHub Releases + заявка IzzyOnDroid

### Out of Scope

- Автокоррекция/подсказки/словарь — v1 (следующий майлстоун): свой движок на Kotlin, словарь ttwiki+Leipzig
- Эмодзи-панель, история буфера обмена — v1+
- Свайп-ввод — открытой реализации нет, своя = годы работы; исключён из планов
- Голосовой ввод — поддержка tt системным распознаванием не подтверждена; исключён
- Свой C++/NDK — не нужен для MVP и v1; сложность неподъёмна для соло-новичка
- Латиница (Zamanälif) — нишевой сценарий; формат раскладок-как-данных должен позволить добавить позже
- Compose в IME-процессе, Flutter/RN — дисквалифицированы по памяти/старту (research/01, 02)
- Этап 0 «прототип в HeliBoard» — пропущен по решению: выбор форка Simple Keyboard окончателен
- RuStore / Play closed testing / F-Droid — следующий майлстоун (дистрибуция)

## Context

- Домен полностью исследован до инициализации: `research/00-itog-i-roadmap.md` (главный документ) + секции 01–08 (архитектура IME, UI-рендеринг, оптимизация, стиль Apple, раскладка, форк vs с нуля, MVP/предикшен, дистрибуция). Конденсат для агентов — `.planning/research/SUMMARY.md`.
- `BRIEF.md` в корне — бриф с зафиксированными решениями; при противоречии выигрывает BRIEF, затем research/00.
- База — форк Simple Keyboard (rkkr), Apache-2.0, Java, APK ~0.65 МБ, живой проект (релиз 6.4, май 2026). Это «учебник по IME» — вырезанный из AOSP LatinIME скелет без словарного движка.
- План Б (только если MVP без автокоррекции провалит юзабельность): миграция на форк HeliBoard ценой GPL-3.0 — решение принимать до больших вложений в iOS-скин.
- Главный источник багов IME вообще — зоопарк InputConnection (WebView шлёт keyCode 229, кастомные редакторы); тестирование закладывать в каждую фазу, не в конец.
- MIUI/HyperOS агрессивно убивают IME-процесс → холодный старт важнее «средней» производительности.

## Constraints

- **Tech stack**: форк Simple Keyboard; новый код — Kotlin через interop (Java-базу массово не конвертировать); UI — один кастомный View + Canvas; Compose допустим только в Activity настроек — решения окончательные, альтернативы не предлагать
- **Performance** (жёсткие бюджеты): APK ≤ 3 МБ; PSS показанной клавиатуры ≤ 30 МБ; холодный старт до показа < 400 мс (главная метрика); ноль аллокаций в цикле отрисовки; janky-кадры ~0%
- **Privacy**: без разрешения INTERNET вообще + CI-проверка манифеста; никаких аналитик/Firebase — проверяемая гарантия «данные не собираются»
- **SDK**: minSdk 24–26 (уточнить перед релизом), targetSdk/compileSdk 37 (база Simple Keyboard уже на API 37; даунгрейд до 36 отклонён как лишний риск — см. SKELETON.md фазы 01)
- **IME-архитектура**: InputMethodService, directBootAware, onEvaluateFullscreenMode()=false; в MVP без composing-текста — коммит сразу, удаление deleteSurroundingText по кодпоинтам
- **Legal (стиль iOS)**: геометрия/палитра/поведение — можно; шрифт SF Pro, иконки SF Symbols, звуки Apple, слова iPhone/iOS в маркетинге — запрещено (Roboto, свои VectorDrawable)
- **Данные**: раскладки хранить данными (XML), не кодом; формат должен допускать латиницу позже
- **Разработчик**: соло-новичок в Android — фазы маленькие, каждая завершается собирающимся APK

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Форк Simple Keyboard, не с нуля и не HeliBoard | Лёгкость (0.65 МБ), Apache-2.0, посильно новичку; HeliBoard = GPL + 20 МБ (research/06) | ✓ Good — Phase 1: merge b40c70d9, сборка/подпись работают |
| Пятый видимый ряд для ә ө ү җ ң һ, не long-press-only | ә — 5-я по частоте буква (6.65%), 6 букв = 10.6% буквоупотреблений | ✓ Good — Phases 2–5: механика+жесты подтверждены без форсмажоров |
| Kotlin + Canvas-View, без Compose в IME | Compose: +20–40 МБ RAM, медленный холодный старт (research/01, 02) | ✓ Good — Phase 6: Canvas-скин + первый Kotlin-файл (a11y-делегат) |
| Без разрешения INTERNET | Проверяемая ОС-гарантия приватности, Data Safety = «No data collected» | ✓ Good — Phase 1: манифест чист, script+CI написаны |
| Без composing-текста в MVP | Минимизация багов зоопарка InputConnection | ✓ Good — Phases 1–5: deleteSurroundingText, zero composing, работает |
| Этап 0 (прототип HeliBoard) пропущен | Решение о форке окончательное, контрольная точка не нужна (ответ при инициализации) | — Pending |
| Майлстоун v1.0 = release-ready APK + GitHub Releases + IzzyOnDroid | RuStore/Play/F-Droid — отдельный майлстоун (ответ при инициализации) | — Pending |
| Vertical MVP: фазы = сквозные способности, каждая даёт собирающийся APK | Соло-новичок, ранняя рабочая клавиатура для самотестирования | — Pending |
| Рабочее название «Tatar Keyboard», финальное имя и applicationId — позже | Имени в брифе нет; applicationId зафиксировать в фазе форка | ✓ Done — Phase 1: org.tatarkeyboard.ime (провизорный) |

## Open Questions

- Порядок клавиш пятого ряда (алфавитный `ә ө ү җ ң һ` vs частотный) — юзер-тест после MVP
- minSdk 24 vs 26 — по аналитике региона перед релизом
- Финальное название приложения и applicationId — до публикации в сторах
- targetSdk-планка Play после 31.08.2026 (35 или 36) — сверить при сабмите (следующий майлстоун)

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-07-18 after Phase 7*
