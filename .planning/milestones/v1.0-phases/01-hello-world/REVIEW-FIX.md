---
phase: 01-hello-world
fixed_at: 2026-07-18
source_review: 01-REVIEW.md
mode: autonomous
findings_fixed: 5
findings_skipped: 4
---

# Phase 01: Review Fix Report

Fixes applied for `01-REVIEW.md` (0 critical / 4 warning / 5 info). All 4 warnings fixed; 1 info fixed (trivially safe); 4 info skipped per autonomous-mode policy.

## Fixed

### WR-01 — SDK constraint reconciled to 37 (docs, not code)
**Commit:** `3ffaa34` `docs(01): reconcile SDK constraint to 37 matching upstream base (WR-01)`
The build stays on compileSdk/targetSdk 37. Downgrading to 36 was already rejected as needless risk in SKELETON.md (the Simple Keyboard base ships on 37). The divergence was in the docs, not the build: updated the SDK constraint line in `.planning/PROJECT.md` and the generated `.claude/CLAUDE.md` to say 37, with a note referencing the SKELETON.md decision. minSdk 24–26 wording unchanged.

### WR-02 — Settings title rebranded
**Commit:** `ae332d3` `fix(01): rebrand settings title to Tatar Keyboard Settings (WR-02)`
`english_ime_settings` changed from "Simple Keyboard Settings" to "Tatar Keyboard Settings" (product name without the `(dev)` suffix, which belongs only to the launcher label `english_ime_name`).
Note: "Simple Keyboard" also appears in `setup_message` across `values*/strings.xml` (~30 locales) — that is broader upstream-string territory, out of scope for this fix pass and left for the dedicated rebrand/localization work.

### WR-03 — Upstream privacy/license URLs replaced with provisional placeholders
**Commit:** `18172b2` `fix(01): replace upstream privacy/license URLs with provisional placeholders (WR-03)`
`privacy_policy_url` and `license_url` no longer point at rkkr/simple-keyboard. Since the final repo location/owner is not yet fixed, they now use RFC 2606 `.invalid` placeholders (`https://tatarkeyboard.invalid/PRIVACY.md`, `.../LICENSE`) — guaranteed never to resolve, clearly provisional, consistent with the project identity, and no fabricated live GitHub URL. An XML comment marks them as placeholders. **Follow-up:** set the real published URLs at publication (phase 11). The repo's own `PRIVACY.md` and `LICENSE` are the source of truth.

### WR-04 — Interop log gated behind DebugFlags
**Commit:** `624a7e0` `fix(01): gate interop log behind DebugFlags (WR-04)`
`KotlinInteropCheck.log()` now wraps the `Log.i` call in `if (DebugFlags.DEBUG_ENABLED)`, matching the codebase's existing gating pattern (`LatinIME.java:396`, `PointerTracker.java:44`). The SC-4 interop proof is preserved: the Kotlin object still compiles into the app and the Java call site in `LatinIME.onCreate()` still executes — only the release-path log cost is removed. Verified: `compileDebugKotlin` + `compileDebugJavaWithJavac` pass.

### IN-01 — Fixed-string grep in check-no-internet.sh
**Commit:** `e4766e2` `fix(01): use fixed-string grep for INTERNET permission check (IN-01)`
Both greps (level 1 source manifest, level 2 aapt2 output) now use `grep -qF`, so the dots in `android.permission.INTERNET` match literally. Does not weaken the check — the real permission string still matches, failure direction remains over-strict, and the authoritative level-2 aapt2 check is unchanged. Verified: script passes level 1 (`bash -n` + live run).

## Skipped

### IN-02 — Gradle wrapper retries=0
Skipped: judgment call about CI network behavior. The review itself says "consider"; `setup-gradle` caching already mitigates. Revisit if distribution downloads flake in CI.

### IN-03 — Silent unsigned release APK
Skipped: touches release-signing behavior, explicitly excluded from autonomous fixes. The conditional-signing pattern is intentional; a warning println can be added when release signing is actually set up (phase 11).

### IN-04 — No keystore.properties key validation
Skipped: touches release-signing behavior, same as IN-03. Defer to release-prep phase.

### IN-05 — CI runs full build on every push
Skipped: judgment call about CI trigger policy (path filters can silently skip builds that matter, e.g. `.md` changes to lint-checked docs). Not a defect; revisit if build minutes become a concern.

## Verification

- `compileDebugKotlin` + `compileDebugJavaWithJavac`: **pass** (WR-04 change compiles; only pre-existing upstream deprecation notes).
- `scripts/check-no-internet.sh`: **level 1 pass** (level 2 skipped — no APK built; on-device verification already deferred per plan).
- Full `assembleDebug` not run (deferred per fix guidance); CI will build on push.
