---
phase: 01-hello-world
verified: 2026-07-18T00:00:00Z
status: passed
previous_status: human_needed
human_verification_deferred: true
deferred_accepted_by: user (autonomous run 2026-07-18 — «Принять и идти дальше»)
score: 3/5 must-haves verified
behavior_unverified: 0
overrides_applied: 0
human_verification:
  - test: "On-device debug smoke (01-01 Task 5): adb install -r app/build/outputs/apk/debug/app-debug.apk; enable «Tatar Keyboard (dev)» in system IME settings; type in a real app; adb logcat -s TatarKeyboard shows «Kotlin interop OK»."
    expected: "Keyboard installs (pm list packages shows org.tatarkeyboard.ime.debug), enables in the system list, commits text in a third-party app, and emits the interop log line at runtime."
    why_human: "Requires a physical device/emulator and system IME settings; no device is connected in this environment (adb devices empty). Runtime interop proof (logcat) and actual typing cannot be observed from disk. Closes SC-1 (on-device typing) and SC-4 runtime proof."
  - test: "Create the GitHub repository, push main, and confirm a green CI run (01-02 D-1): gh repo create tatar-keyboard --public; git remote add origin …; git push -u origin main; gh run list --workflow ci.yml --branch main --limit 1 → completed/success."
    expected: "Latest CI run on main is success; debug-APK uploaded as workflow artifact."
    why_human: "No GitHub repository exists (no gh CLI, only the upstream remote). Creating a public repo is an outward-facing action requiring the user's decision. ci.yml is authored and committed but has never executed on Actions. Partial evidence for SC-3."
  - test: "RED negative CI run (01-02 D-2, the required SC-3 evidence): branch ci-negative-test with android.permission.INTERNET added to the manifest → push → gh run must complete with failure on the INTERNET-check step; then delete the branch and confirm main stays green."
    expected: "gh run list --branch ci-negative-test → completed/failure with INTERNET in the failing step log; branch removed afterward; main run still success."
    why_human: "The script's fail mechanics are proven locally (aapt2 level-2 ran green here; negative injection → exit 1 demonstrated in-session and in 01-01 D2), but the authoritative SC-3 evidence is a RED Actions run, which needs the GitHub repo to exist first. Deferred, not fabricated."
  - test: "Release-APK on device + key backup (01-02 Task 4): adb install -r app/build/outputs/apk/release/app-release.apk alongside debug (org.tatarkeyboard.ime + org.tatarkeyboard.ime.debug); confirm it types; confirm release.jks + password are backed up outside the repository."
    expected: "Both packages coexist and type on device; user confirms an off-repo backup of the signing key and password."
    why_human: "Requires a device (not connected) and a human attestation of the key backup. Key loss = permanent loss of the update channel (Pitfall 6); no automated proxy exists."
prohibition_review: # judgment-tier — non-authoritative LLM-judge verdict, no device/GitHub dependency
  - statement: "MUST NOT нарушать Apache-2.0 гигиену форка (LICENSE, upstream copyright-заголовки, upstream git-история)"
    verdict: satisfied
    evidence: "LICENSE (Apache-2.0, 11358 bytes) present at repo root; b40c70d9 is an ancestor of HEAD (full upstream history preserved); LatinIME.java retains upstream copyright + Apache-2.0 header block."
  - statement: "MUST NOT добавлять Apple-ассеты (SF Pro, SF Symbols, звуки Apple, iPhone/iOS в строках)"
    verdict: satisfied
    evidence: "grep for 'SF Pro'/'SF Symbols' across app/src/main/res and app/src/main/java returned nothing; no Apple asset introduced this phase."
  - statement: "MUST NOT добавлять аналитику, Firebase, телеметрию или сторонние сетевые SDK"
    verdict: satisfied
    evidence: "app/build.gradle dependencies { } block is empty; grep for firebase/analytics/okhttp/retrofit/crashlytics/gms across gradle files returned nothing; manifest declares only VIBRATE."
---

# Phase 1: Форк и hello-world — Verification Report

**Phase Goal:** Форк Simple Keyboard собирается под уникальным applicationId и печатает из коробки — фундамент, через который читается «учебник по IME».
**Verified:** 2026-07-18
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

The automated slice of Phase 1 is complete and correct in the codebase: the upstream fork is merged with full history, the build produces a uniquely-packaged debug APK and a signed release APK, the two-level PERF-04 check runs green (both levels, aapt2 available), the Kotlin↔Java interop is wired and gated, and secrets are provably out of git. What remains unverified is exactly the on-device and GitHub-side evidence the launching agent flagged as deliberately deferred — these are human-verification items, not code gaps. No FAILED truths, no missing/stub/unwired artifacts, no blocker anti-patterns.

### Observable Truths (Roadmap Success Criteria = the contract)

| # | Truth (SC) | Status | Evidence |
| --- | --- | --- | --- |
| 1 | Debug + release APK build/install on device; keyboard enables and types in a real app (SC-1) | ⚠️ human_needed | Build + sign VERIFIED on disk (both APKs present; badging confirms packages). Install/enable/type require a device (not connected). Routed to human verification #1. |
| 2 | applicationId unique — installs alongside original Simple Keyboard without conflict (SC-2) | ✓ VERIFIED | debug=`org.tatarkeyboard.ime.debug`, release=`org.tatarkeyboard.ime` (aapt2 badging), both ≠ upstream `rkr.simplekeyboard.inputmethod`. Distinct packages → coexistence guaranteed by construction. |
| 3 | CI on every commit checks manifest and fails when INTERNET appears (SC-3, PERF-04) | ⚠️ human_needed | ci.yml authored + committed; script fail-mechanics proven locally (level-2 aapt2 green here; INTERNET injection → exit 1 in-session). The required RED Actions run + green main run need a GitHub repo that does not exist. Routed to human #2/#3. |
| 4 | Kotlin code participates in build via Java interop (SC-4) | ✓ VERIFIED | `KotlinInteropCheck` (object, `@JvmStatic fun log()`, gated behind `DebugFlags.DEBUG_ENABLED`) imported at LatinIME.java:71 and called at :255 in `onCreate()`; built-in Kotlin enabled (no `android.builtInKotlin=false`); APKs built successfully → Java→Kotlin compiles. Runtime logcat proof folded into human #1 but build-level interop is proven. |
| 5 | Keystore created; assembleRelease produces a signed APK (SC-5) | ✓ VERIFIED | `apksigner verify --print-certs` → `Signer #1 certificate DN: CN=Tatar Keyboard`; conditional signingConfig via `rootProject.file("keystore.properties")`; release.jks + keystore.properties gitignored and absent from history. |

**Score:** 3/5 truths verified (SC-2, SC-4, SC-5); 2 routed to human verification (SC-1 on-device typing, SC-3 GitHub CI evidence). 0 behavior-unverified.

### Required Artifacts

| Artifact | Expected | Status | Details |
| --- | --- | --- | --- |
| `app/` (merged fork) | Full rkkr/simple-keyboard b40c70d9 base | ✓ VERIFIED | b40c70d9 ancestor of HEAD; LICENSE + upstream headers intact; builds. |
| `scripts/check-no-internet.sh` | Two-level PERF-04 check | ✓ VERIFIED | Executable; uses `grep -qF` (IN-01 fix); level 1 + level 2 (aapt2) both ran green against debug APK. |
| `app/.../utils/KotlinInteropCheck.kt` | Interop proof | ✓ VERIFIED | Present, wired into LatinIME.onCreate(), DebugFlags-gated (WR-04 fix). |
| `.github/workflows/ci.yml` | CI PERF-04 gate + build | ✓ VERIFIED (authored) | push/pull_request, `permissions: contents: read`, fast-fail + level-2 check, sha256-pinned wrapper restore, upload-artifact. Never executed (no repo). |
| `app/build.gradle` | applicationId + conditional signingConfig | ✓ VERIFIED | applicationId, `.debug` suffix, versionCode 1 / 0.1.0, signingConfigs.release via keystore.properties, minifyEnabled true. |
| `release.jks` / `keystore.properties` | Signing secrets, off-git | ✓ VERIFIED | Both gitignored (`git check-ignore` exit 0), never in history, signed release APK verified. |

### Key Link Verification

| From | To | Via | Status | Details |
| --- | --- | --- | --- | --- |
| LatinIME.onCreate() | KotlinInteropCheck.log() | Java→Kotlin static call | ✓ WIRED | import :71, call :255. |
| app/build.gradle applicationId | aapt2 badging package | build config → artifact | ✓ WIRED | badging confirms org.tatarkeyboard.ime[.debug]. |
| ci.yml | scripts/check-no-internet.sh | run: bash … | ✓ WIRED | invoked twice (fast-fail + built-APK). |
| app/build.gradle signingConfig | release.jks | rootProject.file('keystore.properties') | ✓ WIRED | apksigner CN=Tatar Keyboard. |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| --- | --- | --- | --- |
| PERF-04 check passes both levels | `bash scripts/check-no-internet.sh` | Level 1 OK; Level 2 OK (only VIBRATE); exit 0 | ✓ PASS |
| Release APK signed | `apksigner verify --print-certs …app-release.apk` | Signer #1 DN: CN=Tatar Keyboard | ✓ PASS |
| Unique package | `aapt2 dump badging` debug + release | org.tatarkeyboard.ime.debug / org.tatarkeyboard.ime | ✓ PASS |
| On-device typing + logcat interop | adb install/enable/type | — | ? SKIP (no device → human #1) |
| CI green + RED negative run | gh run list | — | ? SKIP (no repo → human #2/#3) |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| --- | --- | --- | --- | --- |
| PERF-04 | 01-02 | Нет INTERNET в манифесте; CI гарантирует это на каждом коммите | ? NEEDS HUMAN | Manifest clean (only VIBRATE, confirmed at source and in built APK); ci.yml enforcement authored and locally proven, but the on-Actions RED-run evidence (the SC-3 contract) is deferred to human #3. Enforcement present and wired; GitHub proof pending. |

### Anti-Patterns Found

None. No `TBD/FIXME/XXX/HACK/PLACEHOLDER` markers in any phase-modified file. The WR-01..04 and IN-01 review findings were fixed (REVIEW-FIX.md, verified: settings label rebranded, privacy/license URLs are `.invalid` placeholders, interop log DebugFlags-gated, grep uses `-qF`).

### Human Verification Required

Four items, all pre-flagged as deliberately deferred (no Android device connected; no GitHub repo / gh CLI). See frontmatter `human_verification` for exact commands and expected results:

1. **On-device debug smoke** — install, enable IME, type in a real app, confirm `Kotlin interop OK` in logcat (SC-1 typing + SC-4 runtime proof).
2. **GitHub repo + green CI run on main** (SC-3 partial).
3. **RED ci-negative-test run** — the required SC-3 evidence that INTERNET fails CI.
4. **Release install side-by-side + key backup confirmation** (SC-1 release variant + key durability).

### Prohibition Review (judgment-tier, non-authoritative)

All three plan prohibitions resolve satisfied on disk (Apache-2.0 hygiene intact, no Apple assets, no analytics/network SDKs — empty dependencies block, only VIBRATE). These are LLM-judge verdicts recorded for the record; none blocks the phase.

### Gaps Summary

No gaps. Every artifact exists, is substantive, and is wired; every mechanically-checkable truth passed. The phase is not `passed` only because two Success Criteria (SC-1 on-device typing, SC-3 GitHub CI evidence) depend on a physical device and a GitHub repository that are unavailable in this environment — these are honestly classified as human-verification items, not failures. The launching agent confirmed both deferrals are intentional.

---

_Verified: 2026-07-18_
_Verifier: Claude (gsd-verifier)_
