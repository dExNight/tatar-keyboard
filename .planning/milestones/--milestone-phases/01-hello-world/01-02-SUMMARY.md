---
phase: 01-hello-world
plan: 02
subsystem: infra
tags: [android, gradle, signing, keystore, apksigner, ci, github-actions, perf-04]

# Dependency graph
requires:
  - "01-01: форк собирается, scripts/check-no-internet.sh, applicationId org.tatarkeyboard.ime"
provides:
  - "Условный signingConfig release: подписанный assembleRelease при наличии keystore.properties, unsigned без него (CI без секретов жив) — задел REL-01"
  - "release.jks (RSA 4096, validity 10950, alias tatarkeyboard) + keystore.properties — локально, вне git, оба gitignored"
  - ".github/workflows/ci.yml — сборка debug-APK + двухуровневая PERF-04-проверка на каждый push/PR (заработает после создания GitHub-репо)"
affects: [phase-11-release]

# Tech tracking
tech-stack:
  added:
    - "GitHub Actions workflow (actions/checkout@v4, actions/setup-java@v4 temurin 17, gradle/actions/setup-gradle@v4, actions/upload-artifact@v4)"
  patterns:
    - "Условный signingConfig через rootProject.file('keystore.properties') — сборка не падает без секретов"
    - "CI восстанавливает gradle-wrapper.jar из тега v9.6.0 gradle/gradle с pin по sha256 (jar не в git)"

key-files:
  created:
    - ".github/workflows/ci.yml"
    - "release.jks (локально, ВНЕ git — gitignored *.jks)"
    - "keystore.properties (локально, ВНЕ git — gitignored)"
  modified:
    - "app/build.gradle (signingConfigs.release + условный signingConfig в buildTypes.release)"
    - ".gitignore (+keystore.properties)"

key-decisions:
  - ".gitignore-строка keystore.properties закоммичена ДО генерации секретов (T1); git check-ignore подтверждает оба файла"
  - "Пароль keystore сгенерирован openssl rand -base64 24, один для store и key; живёт только в keystore.properties (chmod 600)"
  - "CI восстанавливает gradle-wrapper.jar шагом workflow (curl из тега v9.6.0 + sha256sum -c) — jar остаётся вне git, supply chain закрыт pin'ом"
  - "GitHub-репозиторий НЕ создан в этой сессии: нет gh CLI, нет origin remote (только upstream), создание публичного репо — outward-facing действие, требующее явного подтверждения пользователя"

requirements-completed: []

coverage:
  - id: T1-signing
    description: "assembleRelease даёт подписанный APK при keystore.properties и unsigned без него; секреты вне git"
    verification:
      - kind: integration
        ref: "apksigner verify --print-certs → Signer #1 certificate DN: CN=Tatar Keyboard (SHA-256 cdd8c535…); rename keystore.properties → BUILD SUCCESSFUL, app-release-unsigned.apk; git check-ignore release.jks keystore.properties exit 0; git log --all --name-only --pretty=format: | grep -iE '(\\.jks|keystore\\.properties)$' пуст"
        status: pass
    human_judgment: false
  - id: T2-ci-workflow
    description: "ci.yml существует: push/pull_request триггеры, permissions contents: read, check-no-internet.sh (fast-fail + по APK), assembleDebug, upload-artifact"
    verification:
      - kind: integration
        ref: "Файл закоммичен (7c63621), YAML валиден; все шаги/actions версии по Pattern 4 + T3 (только официальные actions на мажорных тегах)"
        status: pass
    human_judgment: false
  - id: T2-ci-green-run
    description: "Зелёный прогон CI workflow на main в GitHub Actions"
    verification: []
    human_judgment: true
    rationale: "DEFERRED: GitHub-репозиторий не существует (нет gh CLI, нет origin). Прогон невозможен до создания репо и push. Phase verification MUST treat as human_needed/deferred."
  - id: T3-negative-ci
    description: "Негативный тест: INTERNET в манифесте роняет CI job (доказательство PERF-04 на GitHub)"
    verification:
      - kind: integration
        ref: "Локальный эквивалент: инъекция INTERNET в манифест → scripts/check-no-internet.sh exit 1, откат → exit 0 (продемонстрировано в этой сессии; также в 01-01 D2). GitHub-часть (ветка ci-negative-test, красный job) — DEFERRED"
        status: partial
    human_judgment: true
    rationale: "Механика скрипта доказана локально; свидетельство именно CI-прогона (failure на ветке ci-negative-test) требует существующего репо. НЕ отмечено passed."
  - id: T4-device-release
    description: "Release-APK установлен рядом с debug на устройстве, печатает; бэкап release.jks + пароля вне репозитория подтверждён"
    verification: []
    human_judgment: true
    rationale: "DEFERRED: устройство не подключено (как и 01-01 Task 5). Бэкап ключа обязателен (Pitfall 6, T2) — подтверждение пользователя отсутствует."

# Metrics
duration: 20min
completed: 2026-07-18
status: complete-local (GitHub + device steps deferred)
---

# Phase 1 Plan 02: Подпись release и CI-гарантия PERF-04 Summary

**assembleRelease даёт реально подписанный APK (apksigner verified, CN=Tatar Keyboard), без keystore.properties собирается unsigned; ci.yml с двухуровневой INTERNET-проверкой закоммичен — но GitHub-репозитория не существует (нет gh/origin), поэтому все GitHub-прогоны и device-чекпойнт отложены, не сфабрикованы.**

## Performance

- **Duration:** ~20 min
- **Started/Completed:** 2026-07-18
- **Tasks:** Task 1 полностью, Task 2 локальная часть (ci.yml); Task 2 GitHub-часть, Task 3 GitHub-часть и Task 4 — DEFERRED
- **Files modified:** 2 изменено, 1 создано (+2 локальных секрета вне git)

## Accomplishments

- **T1 (утечка keystore) закрыт механически:** строка `keystore.properties` добавлена в `.gitignore` и закоммичена ДО генерации секретов; `git check-ignore release.jks keystore.properties` → exit 0; grep путей по всей истории (`git log --all --name-only --pretty=format:`) пуст.
- **Keystore создан по Pattern 3 / T2:** `release.jks` — RSA 4096, validity 10950 дней (30 лет), alias `tatarkeyboard`, пароль `openssl rand -base64 24`; `keystore.properties` (storeFile=../release.jks относительно app/) — оба chmod 600, оба вне git.
- **Условный signingConfig работает в обе стороны (проверено сборками):**
  - с файлом: `app-release.apk`, `apksigner verify --print-certs` → `Signer #1 certificate DN: CN=Tatar Keyboard`, SHA-256 `cdd8c5350ddc86f13cd89b5bfb55ca33c13efba77beb4d4ccb75d5e6b961b09e`;
  - без файла (временный rename): `BUILD SUCCESSFUL`, `app-release-unsigned.apk` — CI без секретов жив;
  - `minifyEnabled true` + proguard-строки release не тронуты.
- **PERF-04 на release-APK:** `bash scripts/check-no-internet.sh app/build/outputs/apk/release/app-release.apk` → оба уровня OK, только VIBRATE.
- **ci.yml авторизован и закоммичен:** push/pull_request, `permissions: contents: read` (T3), fast-fail вызов `check-no-internet.sh` до setup-java, восстановление gradle-wrapper.jar (curl из тега v9.6.0 gradle/gradle + sha256sum -c — jar не в git, тот же артефакт, что локально), `./gradlew assembleDebug`, повторный вызов скрипта по собранному APK (уровень 2), `upload-artifact` `app-debug`. Только официальные actions на мажорных тегах.
- **Негативная механика PERF-04 передоказана локально:** инъекция `<uses-permission android.permission.INTERNET/>` в манифест → скрипт exit 1; откат → exit 0; рабочее дерево чистое.
- **Plan-level suite:** `./gradlew assembleDebug assembleRelease` + `check-no-internet.sh` + `apksigner verify` — всё зелёное.

## Task Commits

1. **Task 1a: .gitignore до секретов (T1)** — `ba4bf38` (chore)
2. **Task 1b: условный signingConfig + подписанный assembleRelease** — `1721fa7` (feat)
3. **Task 2 (локальная часть): ci.yml** — `7c63621` (ci)
4. **Task 2 (GitHub: репо + push + зелёный прогон), Task 3 (GitHub: негативный прогон), Task 4 (device + бэкап)** — **DEFERRED**, не закоммичены как выполненные

## Deviations from Plan

**1. [Blocking → deferred] GitHub-репозиторий не создан, ничего не запушено**
- **Причина:** на машине нет `gh` CLI; git remotes — только `upstream` (rkkr/simple-keyboard, пушить туда нельзя). Создание публичного репозитория — outward-facing действие, требующее явного решения пользователя.
- **Обработка:** ci.yml авторизован и закоммичен локально (готов заработать с первого push); все GitHub-зависимые acceptance-критерии записаны как deferred (см. ниже), НЕ отмечены пройденными.

**2. [Auto-fixed] Официальный URL `services.gradle.org/distributions/gradle-9.6.0-wrapper.jar` отдаёт 404**
- CI-шаг восстановления wrapper jar переключён на raw-URL тега `v9.6.0` репозитория gradle/gradle — тот же способ, которым jar восстановлен локально в 01-01; sha256 скачанного файла совпадает с локальным (`497c8c2a…`), pin зашит в workflow.

**3. [Cosmetic] Commit-message Task 1 переформулирован amend'ом** — первоначальное сообщение содержало подстроку `keystore.properties` и давало ложное срабатывание grep-проверки истории на имена секретных файлов; production-проверка ограничена путями файлов (`--pretty=format:`), но сообщение всё равно очищено до `keystore-properties pattern`.

## Deferred Items (все — до создания GitHub-репо; НЕ выполнены, НЕ отмечены passed)

### D-1. Создать репозиторий и запушить (Task 2, GitHub-часть)
1. Установить `gh` (`brew install gh`) и авторизоваться, либо создать репо через веб.
2. Перед push — контроль секретов: `git log --all --name-only --pretty=format: | grep -iE '(\.jks|keystore\.properties)$'` должен быть пуст (сейчас — пуст).
3. `gh repo create tatar-keyboard --public --description "Tatar Cyrillic keyboard for Android, fork of Simple Keyboard, Apache-2.0"`; `git remote add origin …`; `git push -u origin main`.
4. Дождаться зелёного прогона: `gh run watch` / `gh run list --workflow ci.yml --branch main --limit 1` → `completed success`. Если упадёт на лицензиях SDK (Pitfall 7) — добавить шаг `yes | sdkmanager --licenses` до Gradle.

### D-2. Негативный CI-прогон (Task 3, GitHub-часть)
1. `git checkout -b ci-negative-test main`; добавить в `app/src/main/AndroidManifest.xml` строку `<uses-permission android:name="android.permission.INTERNET"/>` рядом с VIBRATE; commit «test(ci): negative check — INTERNET must fail CI [do not merge]»; `git push origin ci-negative-test`.
2. `gh run watch` → job ОБЯЗАН упасть на шаге проверки INTERNET; зафиксировать URL/номер прогона в Summary фазы.
3. `git push origin --delete ci-negative-test`; удалить локальную ветку; убедиться `git show main:app/src/main/AndroidManifest.xml | grep -c INTERNET` → 0 и последний прогон main зелёный.
Локальная механика уже доказана (скрипт exit 1 на инъекции — эта сессия и 01-01 D2); незакрытым остаётся именно свидетельство красного прогона в Actions (обязательное по Flagged assumptions плана).

### D-3. Device-verify + бэкап ключа (Task 4, human_needed)
1. `adb install -r app/build/outputs/apk/release/app-release.apk` — release встаёт РЯДОМ с debug (`org.tatarkeyboard.ime` + `org.tatarkeyboard.ime.debug`); проверить `adb shell pm list packages | grep tatarkeyboard`.
2. Включить release-вариант «Tatar Keyboard (dev)», напечатать в реальном приложении.
3. **Подтвердить бэкап `release.jks` + пароля вне репозитория** (менеджер паролей / внешний диск) — утеря ключа = потеря канала обновлений навсегда (Pitfall 6, T2). Без этого подтверждения T2 остаётся открытым.

## Issues Encountered

- Ложное срабатывание секрет-grep'а на commit message (см. Deviation 3) — проверка уточнена до путей файлов.
- 404 на `services.gradle.org/...-wrapper.jar` (см. Deviation 2) — заменён источник, pin по sha256.

## Next Phase Readiness

- Локально фаза 1 полностью собирается: debug + release (подписанный), PERF-04-проверка зелёная на обоих APK.
- **Блокеры к «фаза verified»:** D-1…D-3 выше + 01-01 Task 5 (on-device debug smoke). Phase verification обязана трактовать их как human_needed/deferred — критерий SC-3 фазы (CI падает при INTERNET) без свидетельства красного прогона Actions закрытым не считается.

## Self-Check: PASSED

- Files on disk: `.github/workflows/ci.yml`, `release.jks`, `keystore.properties`, `app/build/outputs/apk/release/app-release.apk` — FOUND; секреты gitignored (`git check-ignore` exit 0).
- Commits in log: `ba4bf38`, `1721fa7`, `7c63621` — FOUND.
- Deferred items intentionally NOT verified — routed as human_needed/deferred, никаких «passed» без свидетельств.

---
*Phase: 01-hello-world*
*Completed: 2026-07-18 (local scope; GitHub + device steps deferred)*
