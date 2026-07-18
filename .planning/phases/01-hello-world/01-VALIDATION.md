---
phase: 1
slug: hello-world
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-18
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Gradle build + aapt2 checks (no unit-test framework required this phase) |
| **Config file** | none — Wave 0 installs via fork |
| **Quick run command** | `./gradlew assembleDebug` |
| **Full suite command** | `./gradlew assembleDebug assembleRelease && bash scripts/check-no-internet.sh` |
| **Estimated runtime** | ~60–180 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew assembleDebug`
- **After every plan wave:** Run `./gradlew assembleDebug assembleRelease && bash scripts/check-no-internet.sh`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 180 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 01-01.T1 | 01-01 | 1 | Phase SC-1 (baseline) | 01-01/T1 | Форк собирается без правок | build | `./gradlew assembleDebug` | ❌ W0 | ⬜ pending |
| 01-01.T2 | 01-01 | 1 | PERF-04 (инфраструктура проверки) | 01-01/T2 | Скрипт падает при INTERNET (уровни 1+2) | script | `bash scripts/check-no-internet.sh` | ❌ W0 | ⬜ pending |
| 01-01.T3 | 01-01 | 1 | Phase SC-2 (уникальный applicationId) | — | package `org.tatarkeyboard.ime.debug` | CLI | `aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk \| grep package` | ❌ W0 | ⬜ pending |
| 01-01.T4 | 01-01 | 1 | Phase SC-4 (Kotlin interop) | — | Java-вызов Kotlin-объекта компилируется | build | `./gradlew assembleDebug` (+ logcat в T5) | ❌ W0 | ⬜ pending |
| 01-01.T5 | 01-01 | 1 | Phase SC-1/SC-2/SC-4 | — | Печать на устройстве, side-by-side, logcat | manual | — (checkpoint:human-verify) | — | ⬜ pending |
| 01-02.T1 | 01-02 | 2 | REL-01 (задел), Phase SC-5 | 01-02/T1,T2 | Подписанный release; секреты вне git | CLI | `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk && git check-ignore release.jks keystore.properties` | ❌ W2 | ⬜ pending |
| 01-02.T2 | 01-02 | 2 | PERF-04, Phase SC-3 | 01-02/T3,T4 | CI собирает и проверяет каждый коммит | CI | `gh run list --workflow ci.yml --branch main --limit 1` | ❌ W2 | ⬜ pending |
| 01-02.T3 | 01-02 | 2 | PERF-04 (негативное доказательство) | 01-02/T4 | INTERNET роняет CI-job | CI (negative) | `gh run list --workflow ci.yml --branch ci-negative-test --limit 1` → failure | ❌ W2 | ⬜ pending |
| 01-02.T4 | 01-02 | 2 | Phase SC-1 (release на устройстве) | 01-02/T2 | Release ставится рядом с debug и печатает; бэкап ключа | manual | — (checkpoint:human-verify) | — | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Форк rkkr/simple-keyboard смержен (upstream `b40c70d9`) и собирается (`./gradlew assembleDebug`) — вся последующая верификация зависит от рабочей сборки → **01-01 Task 1**
- [ ] `scripts/check-no-internet.sh` — grep манифеста + `aapt2 dump permissions` по APK (PERF-04) → **01-01 Task 2** (негативная проверка уровня 1 входит в acceptance-критерии задачи)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Клавиатура включается и печатает на устройстве (debug) | Phase SC-1 | Требует физическое устройство/эмулятор и системные настройки IME | adb install debug-APK → Настройки → Языки и ввод → включить «Tatar Keyboard (dev)» → печать в реальном приложении; `adb logcat -s TatarKeyboard` → «Kotlin interop OK» (01-01 Task 5) |
| Release-APK ставится рядом с debug и печатает | Phase SC-1, SC-5 | Установка/ввод на устройстве | adb install release-APK → оба пакета в `pm list packages` → печать (01-02 Task 4) |
| Бэкап release.jks + пароля вне репозитория | REL-01 (задел) | Действие пользователя вне машины сборки | Подтверждение пользователя в чекпойнте 01-02 Task 4 |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
