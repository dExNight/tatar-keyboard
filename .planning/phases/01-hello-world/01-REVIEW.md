---
phase: 01-hello-world
reviewed: 2026-07-18T06:56:50Z
depth: standard
files_reviewed: 7
files_reviewed_list:
  - .github/workflows/ci.yml
  - app/build.gradle
  - app/src/main/java/rkr/simplekeyboard/inputmethod/latin/utils/KotlinInteropCheck.kt
  - scripts/check-no-internet.sh
  - app/src/main/java/rkr/simplekeyboard/inputmethod/latin/LatinIME.java
  - gradle.properties
  - app/src/main/res/values/strings-appname.xml
findings:
  critical: 0
  warning: 4
  info: 5
  total: 9
status: issues_found
---

# Phase 01: Code Review Report

**Reviewed:** 2026-07-18T06:56:50Z
**Depth:** standard
**Files Reviewed:** 7
**Status:** issues_found

## Summary

Phase 1 is infrastructure and rebrand scaffolding on top of the rkkr/simple-keyboard v6.5 merge. The changed surface is small and the security-sensitive parts hold up: the `keystore.properties` pattern keeps secrets out of git (gitignored, verified), no `android.permission.INTERNET` is declared (only `VIBRATE`), the CI token is `contents: read`, and the `gradle-wrapper.jar` sha256 pin (`497c8c2a...a9c7`) verifies exactly against the current official `v9.6.0` download. The `KotlinInteropCheck` interop wiring is correct — package, `@JvmStatic`, and the `LatinIME.onCreate()` call all line up.

No blockers found. The findings are quality and rebrand-completeness issues: SDK levels diverge from the documented project constraint, the rebrand is only partial (settings label and privacy/license URLs still point at upstream), and a proof-of-concept debug log was left running in the keyboard's cold-start path.

Note: `build.gradle` (root, AGP version) and `AndroidManifest.xml` were read for context but are outside the changed-file scope for this phase and are not reviewed as findings here.

## Warnings

### WR-01: compileSdk/targetSdk pinned to 37, contradicts documented constraint of 36

**File:** `app/build.gradle:12,14`
**Issue:** `compileSdk 37` and `targetSdkVersion 37` carry over from the upstream merge, but the project constraint in `.claude/CLAUDE.md` states `targetSdk/compileSdk 36`. This is a silent divergence from a stated, "final" decision. Either the code or the constraint is wrong, and downstream phases will build against assumptions that don't match the recorded contract.
**Fix:** Confirm the intended SDK level. If 36 is correct:
```groovy
compileSdk 36
targetSdkVersion 36
```
Otherwise update the constraint in `CLAUDE.md` so the recorded decision matches reality.

### WR-02: Rebrand incomplete — settings label still reads "Simple Keyboard Settings"

**File:** `app/src/main/res/values/strings-appname.xml:23`
**Issue:** `english_ime_name` was rebranded to "Tatar Keyboard (dev)", but `english_ime_settings` still says "Simple Keyboard Settings". This string is the settings-screen title (referenced via `english_ime_settings` in `res/xml/prefs.xml`), so users see the old upstream name in the settings UI. Inconsistent with the phase's rebrand goal.
**Fix:**
```xml
<string name="english_ime_settings" translatable="false">Tatar Keyboard Settings</string>
```

### WR-03: Privacy and license URLs point to the upstream rkkr repo

**File:** `app/src/main/res/values/strings-appname.xml:24-25`
**Issue:** `privacy_policy_url` and `license_url` still point to `https://github.com/rkkr/simple-keyboard/...`. For a privacy-first fork whose core value is a verifiable "no data collected" guarantee, surfacing the upstream project's privacy policy is misleading — it does not describe this app. The repo already contains its own `PRIVACY.md` and `LICENSE`.
**Fix:** Point both URLs at this project's own repository/policy once the public repo location is known, e.g.:
```xml
<string name="privacy_policy_url" translatable="false">https://github.com/&lt;org&gt;/tatar-keyboard/blob/main/PRIVACY.md</string>
<string name="license_url" translatable="false">https://github.com/&lt;org&gt;/tatar-keyboard/blob/main/LICENSE</string>
```

### WR-04: Proof-of-concept debug log left in the IME cold-start path

**File:** `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/utils/KotlinInteropCheck.kt:7-9` (called from `LatinIME.java:255`)
**Issue:** `KotlinInteropCheck.log()` emits an unconditional `Log.i("TatarKeyboard", "Kotlin interop OK")` on every `LatinIME.onCreate()` — i.e. every keyboard startup. It is a scaffold used only to prove interop works, but it now runs in the exact cold-start path the project budgets to <400ms and treats as zero-allocation. Unlike the rest of the codebase's logging it is not gated behind `DebugFlags`. Leftover debug logging in a hot path should not ship.
**Fix:** Before release, remove the call site in `LatinIME.onCreate()` and delete `KotlinInteropCheck.kt`, or gate it:
```kotlin
if (DebugFlags.DEBUG_ENABLED) Log.i("TatarKeyboard", "Kotlin interop OK")
```
Interop is already exercised by compilation; the runtime log adds no ongoing value.

## Info

### IN-01: Level-1 INTERNET grep is unanchored and uses regex-active dots

**File:** `scripts/check-no-internet.sh:12,32`
**Issue:** `grep -q "android.permission.INTERNET"` treats each `.` as "any char" and is unanchored, so it can false-positive on a comment that merely mentions the permission (e.g. `<!-- deliberately no android.permission.INTERNET -->`). Risk is low because Level 2 (aapt2 on the merged APK) is the authoritative check and the failure direction is safe (over-strict, never under-strict).
**Fix:** Use a fixed-string match to reduce brittleness: `grep -qF "android.permission.INTERNET"`. Optionally scope Level 1 to `uses-permission` lines.

### IN-02: Gradle wrapper distribution download has zero retries

**File:** `gradle/wrapper/gradle-wrapper.properties` (`retries=0`)
**Issue:** With `retries=0` and a 10s timeout, a transient network hiccup fetching the Gradle distribution fails the CI build outright. This is CI-robustness, not correctness.
**Fix:** Consider `retries=2` (or rely on `setup-gradle` caching) to smooth over flaky distribution downloads.

### IN-03: Release build silently produces an unsigned APK when keystore is absent

**File:** `app/build.gradle:32-38`
**Issue:** The `release` build type only attaches `signingConfig` when `keystore.properties` exists. Without it, a release build yields an unsigned artifact with no warning, which is confusing to a solo developer new to Android who may not notice until install fails.
**Fix:** This is the intended conditional-signing pattern, but a `println` in the else branch ("keystore.properties absent — release build will be unsigned") would make the behavior obvious.

### IN-04: No validation of keystore.properties keys before use

**File:** `app/build.gradle:19-26`
**Issue:** If `keystore.properties` exists but is missing a key, `keystoreProps['storeFile']` returns null and `file(null)` throws a configuration-time error that doesn't clearly point at the missing property.
**Fix:** Validate the four expected keys are present after loading and fail with an explicit message naming the missing key.

### IN-05: CI runs the full Android build on every push with no path or branch filter

**File:** `.github/workflows/ci.yml:2-4`
**Issue:** `on: push` / `pull_request` with no filters means doc-only and `.planning/` commits trigger a full `assembleDebug`. Not a defect, but wasteful given the planning-heavy commit cadence in this repo.
**Fix:** Optionally add `paths-ignore: ['.planning/**', '**/*.md']` or branch filters if build minutes matter.

---

_Reviewed: 2026-07-18T06:56:50Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
