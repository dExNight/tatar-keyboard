# Phase 11: Производительность и релиз — Context

**Gathered:** 2026-07-19 (autonomous run; все решения зафиксированы)
**Status:** Ready for planning

<domain_boundary>
Финальная верификация бюджетов производительности + подписанный release-APK + подготовка публикации. НЕ входят: новые фичи, рефакторинг (всё заложено в 1–10).
</domain_boundary>

## Phase Scope

**Requirements:** PERF-01/02/03, REL-01/02/03. Финальный рубеж milestone v1.0.

## Locked Decisions (этот прогон)

| # | Решение |
|---|---------|
| minSdk | **24** (Android 7.0) — без изменений |
| Имя/appId | **Tatar Keyboard** / **org.tatarkeyboard.ime** — финально для v1.0 |
| Публикация | Подготовить всё локально (подписанный APK + privacy policy + README + changelog + инструкция GitHub Release + заявка IzzyOnDroid); публикацию выполнить вручную — GitHub-репо ещё не создано |

## Структура фазы

**Код (авт.):**
- Финальные лайтовые правки для релиза: обновить android:label и strings «english_ime_name» → «Tatar Keyboard», applicationId финально (org.tatarkeyboard.ime без суффиксов в release), shrinkResources=true (I1 из обзора фазы 10), minifyEnabled проверить (уже true?), PERF-03 проверка/оверрайд setup_message → собственный текст (1 строка базы).
- CI: скрипт проверки манифеста на INTERNET-разрешение уже есть (scripts/check-no-internet.sh) — убедиться что он в CI-конфиге.
- Подписание: release keystore инфраструктура (build.gradle signingConfigs → keystore.properties, gitignored — фаза 1 установила, подтвердить); сгенерировать keystore ЕСЛИ НЕТ (ключ 25 лет, RSA-2048/SHA-256); assembleRelease подписанный.

**Верификация бюджетов (device-gated, deferred UAT):**
- PERF-01: release APK ≤ 3 МБ — механически проверяемо (stat на файл).
- PERF-02/03: PSS ≤ 30 МБ, холодный старт < 400 мс, 0 аллокаций/GC/jank — только на устройстве (adb empty → deferred UAT).
- Подготовить: 11-PERF-CHECKLIST.md с конкретными adb-командами для прогона бюджетов.

**Документы:**
- README.md: описание проекта, скриншот-placeholder (будет после device), бейджи (f-droid/IzzyOnDroid — ещё не активны, placeholder), ссылка на privacy policy.
- PRIVACY.md: «данные не собираются», без разрешения INTERNET, Apache-2.0, ссылка в README.
- CHANGELOG.md: v1.0.0 release notes (list всех фаз → features).
- docs/PUBLISH-CHECKLIST.md: пошаговая инструкция создания GitHub-репо, тег v1.0.0, GitHub Release с APK, заявки IzzyOnDroid — всё для ручного выполнения.

## Деferred

- Реальный прогон PERF-02/03 на устройстве — UAT-бандл (принято).
- Публикация (GitHub Release, IzzyOnDroid) — ручное выполнение.
- Полный ребрендинг «Simple Keyboard» в ~30+ локальных строках strings.xml — backlog post-v1.0.
- shrinkResources включить только после проверки что нет ресурсов без Keep-rules, которые нужны рантайм через getIdentifier (раскладки!).

## Критический риск R1: shrinkResources vs getIdentifier

Проект использует `getIdentifier("keyboard_layout_set_"+name)` для загрузки раскладок и `getIdentifier("row_qwerty*")` — если включить shrinkResources без @keep-rules, все XML-раскладки будут стриппнуты. РЕСЕРЧУ обязательно проверить, есть ли keep.xml или нужен, и рекомендовать: включить shrinkResources + написать keep.xml (или оставить false если риск высокий для MVP).

---
*Phase: 11-proizvoditelnost-i-reliz*
*Context gathered: 2026-07-19*
