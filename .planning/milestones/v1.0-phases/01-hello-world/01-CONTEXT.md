# Phase 1: Форк и hello-world - Context

**Gathered:** 2026-07-18
**Status:** Ready for planning
**Mode:** Infrastructure phase — discuss skipped (no user-facing design decisions)

<domain>
## Phase Boundary

Форк Simple Keyboard (rkkr) собирается под уникальным applicationId и печатает из коробки — фундамент, через который читается «учебник по IME». Входит: форк репозитория rkkr/simple-keyboard, новый applicationId (рабочее имя фиксируется здесь), подключение Kotlin через interop (тестовый файл), создание keystore (задел под REL-01), CI-проверка отсутствия разрешения INTERNET (PERF-04). Не входит: любые изменения раскладок, UI, поведения — сток-функциональность базы остаётся как есть.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
All implementation choices are at Claude's discretion — pure infrastructure phase. Use ROADMAP phase goal, success criteria, and project constraints to guide decisions. Notes for the planner:

- **applicationId** — рабочий, провизорный (финальный — решение до публикации, фаза 11; смена до публикации — одна строка в build.gradle). Требование фазы: уникальность против оригинала `rkr.simplekeyboard.inputmethod`, чтобы ставились рядом. Разумный вариант в духе open-source на GitHub: `io.github.<owner>.tatarkeyboard` или нейтральный `org.tatarkeyboard.ime` — выбрать при планировании и зафиксировать в SUMMARY.
- **CI** — репозиторий git/GitHub → GitHub Actions; job на каждый коммит: сборка + проверка манифеста на отсутствие `android.permission.INTERNET` (fail при появлении).
- **Keystore** — release-keystore создаётся локально, НЕ коммитится (в .gitignore); signing config через локальные свойства. `assembleRelease` даёт подписанный APK.
- **Kotlin interop** — Kotlin-плагин в сборку, один тестовый Kotlin-файл, вызываемый из Java-кода базы (доказательство interop), без массовой конвертации.
- **Toolchain** — ориентир из ресерча: AGP 9.x, Gradle 8.14+/9.x, Kotlin 2.3+; minSdk как в базе (24–26, уточнение перед релизом), targetSdk/compileSdk 36 — если сток-база собирается на более старых версиях, апгрейд тулчейна делать минимально необходимым, не превращать фазу в апгрейд-марафон.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- Кодовой базы в репозитории ещё нет — фаза создаёт её форком rkkr/simple-keyboard (Apache-2.0, APK ~0.65 МБ, один модуль, релиз 6.4 май 2026).
- `research/06-fork-ili-s-nulya.md` — единственный источник правды по базе (вердикт «форк Simple Keyboard»).
- `research/01-stek-i-arhitektura-ime.md` — InputMethodService, манифест, subtypes.
- `.planning/research/SUMMARY.md` — конденсат всего ресерча.

### Established Patterns
- Ещё не установлены — эта фаза их закладывает: Kotlin для нового кода поверх Java-базы, раскладки-как-данные (позже), фазы завершаются собирающимся APK.

### Integration Points
- Research flag из SUMMARY: пофайловое устройство Simple Keyboard нашим ресерчем не покрыто — фазовому ресерчеру читать реальные исходники форка после клонирования.

</code_context>

<specifics>
## Specific Ideas

No specific requirements — infrastructure phase. Жёсткие рамки из PROJECT.md: без разрешения INTERNET (PERF-04 с CI-проверкой с этой фазы), Apache-2.0 сохраняется, никаких сторонних зависимостей/аналитики.

</specifics>

<deferred>
## Deferred Ideas

None — infrastructure phase. Финальное имя приложения и applicationId — решение фазы 11 (до публикации).

</deferred>
