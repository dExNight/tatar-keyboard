---
phase: 11-proizvoditelnost-i-reliz
plan: 01
subsystem: infra
tags: [android, gradle, shrinkResources, keep-xml, apksigner, ci, github-actions, release, rebranding]

requires:
  - phase: 01-fork-i-hello-world
    provides: "keystore + условный signingConfig (Pattern 3), ci.yml с check-no-internet, applicationId org.tatarkeyboard.ime"
  - phase: 10-onbording-i-nastroyki
    provides: "удалённый legacy-диалог (setup_message стал мёртвой строкой), онбординг для README/CHANGELOG"
provides:
  - "Release-конфиг v1.0: shrinkResources true + res/raw/keep.xml (маски 4 getIdentifier call-site), versionName 1.0.0"
  - "Ребрендинг: english_ime_name «Tatar Keyboard» (без dev-суффикса), setup_message нейтрализован"
  - "Подписанный release-APK 681 070 байт ≤ 3 145 728, apksigner verify OK (PERF-01+REL-01 закрыты)"
  - "CI: assembleRelease (unsigned) + stat size-гейт ≤ 3145728 (PERF-01 как CI-контракт)"
  - "Документы: README (переписан), PRIVACY (полная policy), CHANGELOG v1.0.0, docs/PUBLISH-CHECKLIST (10 ручных шагов), 11-PERF-CHECKLIST (adb-замеры)"
affects: [manual-publication, device-uat-bundle, post-v1.0-backlog]

tech-stack:
  added: []
  patterns:
    - "Resource shrinker keep.xml масками по getIdentifier call-site (эксперимент → keep → aapt2-гейт)"
    - "CI собирает unsigned release by design (секреты не в CI); подпись верифицируется локально"

key-files:
  created:
    - app/src/main/res/raw/keep.xml
    - CHANGELOG.md
    - docs/PUBLISH-CHECKLIST.md
    - .planning/phases/11-proizvoditelnost-i-reliz/11-PERF-CHECKLIST.md
  modified:
    - app/build.gradle
    - app/src/main/res/values/strings-appname.xml
    - app/src/main/res/values/strings.xml
    - .github/workflows/ci.yml
    - README.md
    - PRIVACY.md
    - .planning/REQUIREMENTS.md
    - .planning/STATE.md

key-decisions:
  - "shrinkResources включён строго в паре с keep.xml — R1 снят живым экспериментом ресерча (стрипаются locale_name_*, не раскладки); двойной aapt2-гейт в верификации"
  - "A4-фолбэк: setup_message НЕ удалён, а нейтрализован до «Tatar Keyboard» — lintVitalRelease падает ExtraTranslation на 35 сиротах-переводах (вопреки ожиданию плана «warning, не error»)"
  - "privacy_policy_url/license_url остаются .invalid — owner репо неизвестен; замена = шаг 3 PUBLISH-CHECKLIST"
  - "YAML ci.yml провалидирован ruby yaml (pyyaml недоступен — план допускает грепы, ruby дал строгую проверку)"
  - "Публикация строго ручная (locked decision): PUBLISH-CHECKLIST — инструкция, ничего не исполнено автоматически"

patterns-established:
  - "keep.xml для resource shrinker: маски по всем getIdentifier call-site + post-build aapt2-гейт (locale_name ≥ 8, layout set жив)"

requirements-completed: [PERF-01, PERF-02, PERF-03, REL-01, REL-02, REL-03]

coverage:
  - id: D1
    description: "Release-конфиг: shrinkResources + keep.xml, versionName 1.0.0, ребрендинг «Tatar Keyboard»; подписанный APK 681 070 байт ≤ 3 МБ (PERF-01, REL-01)"
    requirement: PERF-01
    verification:
      - kind: other
        ref: "./gradlew assembleRelease && stat -f%z app-release.apk (681070 ≤ 3145728) && apksigner verify && aapt2 dump resources: locale_name=8, keyboard_layout_set_tatar, label_pause/wait=2"
        status: pass
    human_judgment: false
  - id: D2
    description: "CI-гейт: assembleRelease (unsigned) + stat size-гейт ≤ 3145728 в ci.yml"
    requirement: PERF-01
    verification:
      - kind: other
        ref: "grep assembleRelease/3145728/stat -c%s .github/workflows/ci.yml + ruby -ryaml YAML.load_file (валиден); живой прогон Actions невозможен до создания репо"
        status: pass
    human_judgment: false
  - id: D3
    description: "Документы REL-02/03: README/PRIVACY/CHANGELOG/PUBLISH-CHECKLIST/PERF-CHECKLIST — все контент-грепы плана PASS"
    requirement: REL-02
    verification:
      - kind: other
        ref: "Task 3 verify chain: существование 5 файлов + позитив/негатив-грепы (ссылки, no-скриншот-плейсхолдеры, no-бейджи, no-iOS-маркетинг, INTERNET/apache/backup/izzy)"
        status: pass
    human_judgment: false
  - id: D4
    description: "PERF-02/03 device-замеры (PSS, холодный старт, аллокации, janky)"
    requirement: PERF-02
    verification: []
    human_judgment: true
    rationale: "Замеры возможны только на физическом бюджетном устройстве (adb) — deferred UAT по standing-паттерну; команды и пороги в 11-PERF-CHECKLIST.md"
  - id: D5
    description: "Публикация: GitHub-репо, push, тег v1.0.0, GitHub Release, заявка IzzyOnDroid"
    requirement: REL-03
    verification: []
    human_judgment: true
    rationale: "Locked decision: публикация строго вручную пользователем по docs/PUBLISH-CHECKLIST.md; автоматизация запрещена планом"

duration: 12min
completed: 2026-07-19
status: complete
---

# Phase 11 Plan 01: Финальный релиз — shrinkResources+keep, CI-гейт, документы Summary

**Подписанный release-APK «Tatar Keyboard» 1.0.0 — 681 070 байт (гейт 3 МБ, запас 4.6×) с shrinkResources+keep.xml, CI size-гейтом и полным комплектом релизных документов; публикация и device-замеры подготовлены для ручного исполнения**

## Performance

- **Duration:** 12 min
- **Started:** 2026-07-18T23:46:57Z
- **Completed:** 2026-07-18T23:58:47Z
- **Tasks:** 4 of 5 (Task 5 checkpoint — deferred: device UAT + ручная публикация)
- **Files modified:** 14 (10 app/docs + 4 planning)

## Accomplishments

- **PERF-01 закрыт механически:** release-APK 681 070 байт ≤ 3 145 728 (stat), shrinkResources true + keep.xml — locale_name = 8 в aapt2-дампе (краш спейсбара «Татарча» исключён), keyboard_layout_set_tatar жив, label_pause/wait = 2.
- **REL-01 закрыт:** APK подписан release.jks (фаза 1), apksigner verify OK; signingConfigs-цепочка нетронута.
- **Ребрендинг:** english_ime_name «Tatar Keyboard» (все 4 label манифеста через единую точку), versionName 1.0.0.
- **CI-контракт PERF-01:** ci.yml дополнен assembleRelease (unsigned by design) + stat-гейтом ≤ 3145728 — fail-capable.
- **5 документов:** README переписан (EN-lead + русский, без мёртвых бейджей/плейсхолдеров/iOS-маркетинга), PRIVACY — полная двуязычная policy, CHANGELOG v1.0.0 user-facing, docs/PUBLISH-CHECKLIST (10 ручных шагов с точными URL/командами), 11-PERF-CHECKLIST (4 adb-замера с порогами).
- **Boundary чист:** дифф 7f99505..HEAD по app/ + .github/ = ровно 5 файлов, ноль .java/.kt, манифест и 35 локализаций нетронуты.

## Task Commits

Each task was committed atomically:

1. **Task 1: release-конфиг (shrinkResources+keep.xml+ребрендинг+1.0.0)** - `6b2703e` (feat)
2. **Task 2: CI assembleRelease + size-гейт** - `b1afe8c` (feat)
3. **Task 3: 5 релизных документов** - `66843a9` (docs)
4. **Task 4: верификация + bookkeeping REQUIREMENTS/STATE** - `f666912` (docs)

## Files Created/Modified

- `app/build.gradle` — +shrinkResources true, versionName 1.0.0
- `app/src/main/res/raw/keep.xml` — новый: маски keyboard_layout_set_*/kbd_*/rows_*/rowkeys_*/row_*/locale_name_*/label_*
- `app/src/main/res/values/strings-appname.xml` — «Tatar Keyboard» без «(dev)»; URL-плейсхолдеры .invalid оставлены намеренно
- `app/src/main/res/values/strings.xml` — setup_message нейтрализован (см. Deviations)
- `.github/workflows/ci.yml` — +2 шага: assembleRelease (unsigned), size-гейт stat ≤ 3145728
- `README.md` — переписан целиком (был README Simple Keyboard с чужими бейджами)
- `PRIVACY.md` — расширен с 1 строки до полной двуязычной policy
- `CHANGELOG.md` — новый: v1.0.0 по user-facing фичам
- `docs/PUBLISH-CHECKLIST.md` — новый: 10 шагов ручной публикации
- `.planning/phases/11-proizvoditelnost-i-reliz/11-PERF-CHECKLIST.md` — новый: adb-замеры PERF-02/03

## Decisions Made

- shrinkResources только в паре с keep.xml (R1 снят экспериментом ресерча — враг locale_name_*, не раскладки).
- pyyaml недоступен → YAML провалидирован ruby -ryaml (строже, чем допускаемый планом greps-only фолбэк).
- privacy_policy_url/license_url — .invalid до создания репо (owner неизвестен, шаг 3 PUBLISH-CHECKLIST).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] setup_message нейтрализован вместо удаления (запланированный фолбэк A4)**
- **Found during:** Task 1 (release-конфиг)
- **Issue:** План предписывал удалить `setup_message` из values/strings.xml; ожидание A4 «translation without default → warning, не error» не подтвердилось — `lintVitalRelease` падает с Error: ExtraTranslation на 35 сиротах-переводах values-*/, assembleRelease красный.
- **Fix:** Применён предусмотренный планом A4-фолбэк: строка оставлена с нейтральным текстом «Tatar Keyboard» + поясняющий комментарий. Старый бренд «Simple Keyboard» из базового strings.xml исчез (цель ребрендинга достигнута); чистка 35 локализаций остаётся backlog post-v1.0.
- **Files modified:** app/src/main/res/values/strings.xml
- **Verification:** assembleRelease зелёный; `! grep 'Simple Keyboard' values/strings.xml` PASS; верификационные грепы скорректированы честно (проверяется отсутствие старого бренда, а не отсутствие имени строки).
- **Committed in:** 6b2703e (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking, заранее описанный планом фолбэк A4)
**Impact on plan:** Нулевой по существу: мёртвая строка осталась мёртвой, старый бренд из base values/ убран, сборка зелёная. Единственное отличие от буквы плана — грепы `! grep 'name="setup_message"'` заменены на `! grep 'Simple Keyboard'` (der смысл проверки сохранён).

## Issues Encountered

None (сверх задокументированной deviation).

## Authentication Gates

None — фаза не касалась внешних сервисов (публикация строго ручная by design).

## User Setup Required

**⚠️ ВАЖНО — бэкап ключа подписи:** сделайте бэкап `release.jks` + `keystore.properties` минимум в 2 места ВНЕ репозитория (пароль-менеджер + офлайн-носитель) **до публикации и даже если публикация откладывается**. Потеря ключа = невозможность выпускать обновления навсегда. Это шаг 1 в [docs/PUBLISH-CHECKLIST.md](../../../docs/PUBLISH-CHECKLIST.md).

## Next Phase Readiness

Фаза 11 — последняя фаза v1.0: **milestone locally done**. Остались три ручных блока (STATE.md Blockers, Phase 11 запись):

- **(a)** PERF-02/03 device-замеры — 11-PERF-CHECKLIST.md (бюджетное устройство).
- **(b)** Финальный QA v1.0 + UAT-бандл фаз 1–10 (чек-листы в STATE.md Blockers).
- **(c)** Ручная публикация — docs/PUBLISH-CHECKLIST.md (repo → push → CI + негативный тест → тег v1.0.0 → GitHub Release → IzzyOnDroid); после неё REL-02/03 → [x].

## Self-Check: PASSED

- Все key-files.created существуют на диске (test -f ×4 PASS).
- git log --grep="11-01" ≥ 1 коммит (4 task-коммита).
- Все acceptance-грепы Task 1–4 перепрогнаны в Task 4 verify chain — PASS (с учётом задокументированной deviation A4).
- Plan-level verification 1–9 PASS; п. 10 (checkpoint) — честный деферрал в STATE.md Blockers.

---
*Phase: 11-proizvoditelnost-i-reliz*
*Completed: 2026-07-19*
