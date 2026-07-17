# GSD (Get Shit Done) — как устроен пайплайн

Изучено 2026-07-18 по официальной документации и сторонним разборам.

## Что это

GSD — система spec-driven разработки и «контекст-инжиниринга» поверх Claude Code (а также OpenCode, Gemini CLI, Codex). Решает проблему **context rot**: качество Claude падает по мере заполнения контекстного окна (0–30% — пик, 50–70% — деградация, 70%+ — плохо). GSD построен вокруг этой кривой: вся тяжёлая работа выносится в свежие субагентские контексты по 200K токенов, а главная сессия остаётся на 30–40%.

Три механизма:
1. **Структурированные файлы** в `.planning/` — состояние проекта живёт на диске, не в контексте.
2. **Мультиагентная оркестрация** — тонкий оркестратор только спавнит агентов и собирает результаты, сам тяжёлую работу не делает.
3. **Атомарный скоупинг** — каждый план рассчитан на выполнение в пределах ~50% контекста (2–3 задачи максимум).

## ⚠️ Критично: статус проекта (2026)

Оригинальный проект (`gsd-build/get-shit-done`, автор TACHES) **скомпрометирован**: в мае 2026 автор провёл rug-pull связанного крипто-токена $GSD, удалил аккаунты и исчез. Оригинальный репозиторий заблокирован, но **npm-пакеты `get-shit-done-cc` остаются под контролем исчезнувшего автора** — устанавливать их нельзя (агенты GSD работают с широкими shell-правами, риск supply chain).

Живое продолжение — community-форк под управлением open-gsd:
- Репозитории: `github.com/open-gsd/get-shit-done-redux`, `open-gsd/gsd-core`, сайт opengsd.net
- Установка: `npx @opengsd/get-shit-done-redux@latest`
- Код MIT, зеркалирован бит-в-бит до rug-pull, прошёл security-аудит, без токен-референсов; существующие `.planning/` совместимы.

## Пайплайн: пять стадий на каждую фазу

```
new-project → [ discuss → plan → execute → verify ] × N фаз → complete-milestone → new-milestone
```

### 0. `/gsd:new-project` — инициализация
Опрашивает до полного понимания идеи → спавнит параллельных ресёрчеров домена → извлекает требования (v1/v2/out-of-scope) → строит roadmap из фаз. Для существующего кода сначала `/gsd:map-codebase` (4 агента: STACK/ARCHITECTURE/CONVENTIONS/CONCERNS).

Создаёт: `PROJECT.md`, `REQUIREMENTS.md` (требования с ID), `ROADMAP.md`, `STATE.md`, `research/`.

### 1. `/gsd:discuss-phase N` — фиксация видения
Анализирует фазу, находит «серые зоны» (layout, взаимодействия, форматы, обработка ошибок) и задаёт вопросы. Итог — `CONTEXT.md` с тремя секциями: **Decisions** (зафиксировано, не обсуждается), **Deferred Ideas** (явно отложено), **Claude's Discretion** (на усмотрение). Его читают ресёрчер и планировщик следующих стадий.

### 2. `/gsd:plan-phase N` — исследование и планирование
1. **Ресёрч**: 4 параллельных агента (stack / features / architecture / pitfalls) → `RESEARCH.md`.
2. **Планирование**: planner в свежем контексте читает PROJECT + REQUIREMENTS + CONTEXT + RESEARCH + 2–4 релевантных прошлых SUMMARY → создаёт 2–3 файла PLAN.md.
3. **Проверка**: plan-checker валидирует по 8 измерениям (покрытие требований, полнота задач, корректность зависимостей/волн, влезание в ~50% контекста, must-haves, key links, уместность TDD, Nyquist-валидация). Не прошло — planner правит, цикл до 3 раз.
4. **Nyquist validation**: до написания кода каждому требованию сопоставляется автоматическая команда проверки (тест); план без verify-команд не одобряется. Итог — `VALIDATION.md`.

### 3. `/gsd:execute-phase N` — волновое исполнение
Планы группируются в **волны** по зависимостям (из YAML frontmatter: `wave`, `depends_on`). Внутри волны — параллельные executor-агенты, каждый со свежим 200K контекстом; волны последовательны. Вертикальные слайсы (фича целиком) параллелятся лучше горизонтальных слоёв.

- **Атомарные коммиты**: каждая задача — отдельный коммит сразу после завершения (`feat(03-01): ...`).
- **Правила отклонений**: баги, недостающая критичная функциональность и блокеры чинятся автоматически (с фиксацией в SUMMARY); архитектурные изменения — STOP и checkpoint с вопросом к пользователю.
- **Checkpoints**: задачи `type="checkpoint:*"` ставят исполнение на паузу (human-verify / decision / human-action).
- После всех волн — **verifier**: сверяет кодовую базу с must-haves из frontmatter → `VERIFICATION.md`.

### 4. `/gsd:verify-work N` — ручная приёмка (UAT)
Извлекает проверяемые деливераблы («что ты теперь можешь сделать») и проводит по ним диалогом. На «не работает» — спавнит debugger-агента, тот находит корневую причину и создаёт fix-план; повторный `/gsd:execute-phase` выполняет только gap-планы. Итог — `UAT.md`.

### 5. Завершение
`/gsd:audit-milestone` (проверка требований, поиск заглушек) → `/gsd:plan-milestone-gaps` при пробелах → `/gsd:complete-milestone` (архив в MILESTONES.md, тег релиза) → `/gsd:new-milestone` (новый цикл).

## Формат плана (PLAN.md)

YAML frontmatter + XML-тело. Frontmatter: `phase`, `plan`, `wave`, `depends_on`, `files_modified`, `requirements` (ID), `must_haves` (goal-backward критерии: truths / artifacts / key_links с regex-паттернами). Тело:

```xml
<task type="auto">
  <name>Create login endpoint</name>
  <files>src/app/api/auth/login/route.ts</files>
  <action>Конкретные инструкции, включая чего НЕ делать и почему</action>
  <verify>curl -X POST ... returns 200 + Set-Cookie</verify>
  <done>Критерий готовности</done>
</task>
```

Плюс `<objective>`, `<context>` (@-ссылки на PROJECT/ROADMAP/STATE), `<verification>`, `<success_criteria>`.

## Файловая структура `.planning/`

```
.planning/
  PROJECT.md        # видение, загружается ВСЕГДА и всем (~500 строк лимит)
  REQUIREMENTS.md   # требования v1/v2 с ID и трассировкой (~1000)
  ROADMAP.md        # фазы и статусы (~800)
  STATE.md          # позиция, решения, блокеры — память между сессиями (~300)
  config.json       # профиль моделей, тумблеры workflow
  MILESTONES.md     # архив завершённых майлстоунов
  research/  codebase/  todos/{pending,done}/  debug/
  phases/XX-name/
    CONTEXT.md, RESEARCH.md, VALIDATION.md, VERIFICATION.md, UAT.md
    XX-YY-PLAN.md, XX-YY-SUMMARY.md
```

Лимиты размеров жёсткие (по кривой деградации контекста); при превышении GSD предлагает архивировать. История прошлых фаз загружается селективно: digest-индекс → скоринг релевантности → полные SUMMARY только топ-2–4 фаз.

## Агенты (12 штук)

planner, plan-checker (цикл до 3×), executor, verifier, phase-researcher (×4 параллельно), project-researcher, research-synthesizer, roadmapper, debugger (научный метод, персистентные сессии в debug/), codebase-mapper, integration-checker, nyquist-auditor. Оркестратор — тонкий: load STATE → validate → spawn → collect → update state → route.

## Профили моделей

- `quality`: Opus на планирование и исполнение, Sonnet на проверки.
- `balanced` (дефолт): Opus только на planner, остальное Sonnet.
- `budget`: Sonnet + Haiku на верификацию.
Переключение: `/gsd:set-profile`, `/gsd:settings`.

## Прочие команды

`/gsd:quick` — ад-хок задачи с гарантиями GSD (атомарные коммиты, state), но без ресёрча/checker/verifier, живёт в `.planning/quick/`. `/gsd:progress`, `/gsd:pause-work` / `resume-work` (хэндофф контекста между сессиями), `/gsd:add-phase` / `insert-phase` (десятичные фазы для срочного) / `remove-phase`, `/gsd:debug`, `/gsd:health`, `add-todo` / `check-todos`.

## Ключевые практики

- `/clear` между стадиями — оркестратор всё равно спавнит свежих агентов.
- discuss-phase обязателен для визуальных фич; глубина проработки там = соответствие результата ожиданиям.
- Не пропускать verify-work — он сам диагностирует и создаёт fix-планы.
- Вертикальные слайсы для параллелизма.

## Релевантность нашему проекту

Философия GSD (спеки на диске, свежие контексты, атомарные планы с verify, волны) напрямую применима к разработке клавиатуры: наш ресерч в `research/` — готовый вход для PROJECT.md/REQUIREMENTS.md, а roadmap из `00-itog-i-roadmap.md` ложится на фазы GSD. Если ставить GSD — только форк open-gsd, не оригинальный npm-пакет. Альтернатива — воспроизвести паттерн нативными средствами Claude Code (workflow/субагенты), как уже делали на этапе ресерча.

## Источники

- Документация GSD: https://gsd-build-get-shit-done.mintlify.app/ (introduction, how-it-works, concepts/*, advanced/*, reference/*)
- Разбор устройства: https://www.codecentric.de/en/knowledge-hub/blog/the-anatomy-of-claude-code-workflows-turning-slash-commands-into-an-ai-development-system
- Community-форк: https://github.com/open-gsd/get-shit-done-redux, https://opengsd.net
- О rug-pull: https://aiweekly.co/alerts/get-shit-done-creator-rug-pulls-gsd-token-vanishes, https://vexjoy.com/posts/the-crypto-coin-was-the-tell/, https://github.com/open-gsd/gsd-core/discussions/109
- Обзоры: https://www.augmentcode.com/learn/gsd-58k-stars-claude-code, https://dev.to/alikazmidev/the-complete-beginners-guide-to-gsd-get-shit-done-framework-for-claude-code-24h0
