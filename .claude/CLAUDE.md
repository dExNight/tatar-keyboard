<!-- GSD:project-start source:PROJECT.md -->

## Project

**Tatar Keyboard (рабочее название)**

Нативная Android-клавиатура (IME) с татарской кириллической раскладкой — стандартная ЙЦУКЕН плюс отдельный видимый пятый ряд для ә ө ү җ ң һ. Визуально — в стиле системной клавиатуры iOS (в юридически безопасных границах), максимально лёгкая и отзывчивая на бюджетных устройствах (Xiaomi/Redmi, Samsung A-серия). Полностью офлайн, без разрешения INTERNET, open-source (Apache-2.0). Для татароязычных пользователей в Татарстане/России; разработчик — соло, новичок в Android.

**Core Value:** Татарский язык — первым классом: печатать по-татарски так же быстро и приятно, как по-русски в Gboard, — на самом дешёвом телефоне, без интернета.

### Constraints

- **Tech stack**: форк Simple Keyboard; новый код — Kotlin через interop (Java-базу массово не конвертировать); UI — один кастомный View + Canvas; Compose допустим только в Activity настроек — решения окончательные, альтернативы не предлагать
- **Performance** (жёсткие бюджеты): APK ≤ 3 МБ; PSS показанной клавиатуры ≤ 30 МБ; холодный старт до показа < 400 мс (главная метрика); ноль аллокаций в цикле отрисовки; janky-кадры ~0%
- **Privacy**: без разрешения INTERNET вообще + CI-проверка манифеста; никаких аналитик/Firebase — проверяемая гарантия «данные не собираются»
- **SDK**: minSdk 24–26 (уточнить перед релизом), targetSdk/compileSdk 37 (база Simple Keyboard уже на API 37; даунгрейд до 36 отклонён как лишний риск — см. SKELETON.md фазы 01)
- **IME-архитектура**: InputMethodService, directBootAware, onEvaluateFullscreenMode()=false; в MVP без composing-текста — коммит сразу, удаление deleteSurroundingText по кодпоинтам
- **Legal (стиль iOS)**: геометрия/палитра/поведение — можно; шрифт SF Pro, иконки SF Symbols, звуки Apple, слова iPhone/iOS в маркетинге — запрещено (Roboto, свои VectorDrawable)
- **Данные**: раскладки хранить данными (XML), не кодом; формат должен допускать латиницу позже
- **Разработчик**: соло-новичок в Android — фазы маленькие, каждая завершается собирающимся APK

<!-- GSD:project-end -->

<!-- GSD:stack-start source:STACK.md -->

## Technology Stack

Technology stack not yet documented. Will populate after codebase mapping or first phase.
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->

## Conventions

Conventions not yet established. Will populate as patterns emerge during development.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->

## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->

## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->

## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:

- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->

## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
