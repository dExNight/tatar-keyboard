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
| (filled by planner) | | | PERF-04 | — | Манифест без INTERNET | build | `bash scripts/check-no-internet.sh` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Форк rkkr/simple-keyboard склонирован и собирается (`./gradlew assembleDebug`) — вся последующая верификация зависит от рабочей сборки
- [ ] `scripts/check-no-internet.sh` — grep манифеста + `aapt2 dump permissions` по APK (PERF-04)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Клавиатура включается и печатает на устройстве | Phase SC-1 | Требует физическое устройство/эмулятор и системные настройки IME | adb install → Настройки → Языки и ввод → включить клавиатуру → печать в реальном приложении |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
