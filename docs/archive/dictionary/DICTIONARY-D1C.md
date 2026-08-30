# D1c suggestion strip and inset evidence

Date: 2026-07-22. Candidate base: D1b commit `037f3ea`.

## Implemented scope

- A single lazily inflated `SuggestionStripView` draws three equal cells on one Canvas. The
  `ViewStub` remains uninflated while suggestions are disabled; the inflated view starts `GONE`.
- The strip is exactly 40dp when visible and contributes zero measured/layout/touch height while
  `GONE`. Both input layouts explicitly stack it above `MainKeyboardView`; the v28 navigation
  handling (`fitsSystemWindows`) remains on `MainKeyboardView`.
- Touch state uses the Android active pointer ID, rejects empty cells and out-of-bounds releases,
  handles `ACTION_POINTER_UP`, and permits a press to recover only after re-entering its original
  cell. Detach clears the suggestions, active gesture, click listener and accessibility nodes.
- Three stable virtual accessibility IDs expose only populated cells as buttons. Accessibility
  clicks require the view to be attached, shown, enabled and still populated.
- `InputView` exposes truthful post-layout combined bounds. `LatinIME.onComputeInsets()` includes
  a visible strip, preserves the exact keyboard-only fallback, keeps the more-keys top at zero,
  extends the bottom touch region, and clears the hardware-keyboard suppressed region.

This is the isolated D1c spike. It does not connect dictionary lookup, settings, subtype/privacy
eligibility or editor actions; those remain D1d/D1e work.

## Automated evidence

Commands use the repository Gradle wrapper with this worktree as the project directory because
this worktree checkout does not contain its own `gradle-wrapper.jar`.

| Gate | Result |
|---|---|
| Targeted suggestion JVM/source-contract tests | passed: 15 tests, 0 failed/skipped |
| Full JVM suite (`test`) | passed: 53 tests, 0 failed/skipped |
| `assembleDebug` + `assembleRelease`, rerun tasks | `BUILD SUCCESSFUL` |
| `lintVitalRelease` as part of release assembly | passed |
| Full `lintDebug` report | blocked by repository baseline: 125 errors, 95 warnings; first error is the pre-existing `StringFormatMatches` conflict at `SettingsHostActivity.kt:737` / `values-af/strings.xml:28` |
| D1c changed-file lint diagnostics | no `ResourceType` error; relevant report rechecked after the styleable fix |
| no-INTERNET, source + debug APK + release APK | passed; only `android.permission.VIBRATE` is declared |
| Source formatting (`git diff --check`) | passed |

The host allocation regression measures zero allocations in the pure fixed-size hit/gesture
state machine after warmup. Source-contract tests also reject known allocation sites in hot
`onDraw`/`onTouchEvent` bodies. These are useful regressions, but they are not a substitute for
Android runtime allocation/frame instrumentation.

## APK delta

The D1b numbers are its recorded rerun-task artifacts. The D1c candidate was rebuilt with the
same project/toolchain configuration.

| Artifact | D1b baseline | D1c candidate | Delta |
|---|---:|---:|---:|
| Debug APK | 2,885,783 B | 2,895,976 B | +10,193 B |
| Unsigned release APK | 1,427,147 B | 1,430,323 B | +3,176 B |

The release candidate is below the D1 target of 1.7 MB and the absolute 3 MiB limit.

## Device acceptance status

Full D1c acceptance is **BLOCKED on physical-device instrumentation/UAT**. No Samsung or other
physical device was available in this host run, so the following are not claimed as passed:

- Samsung touch coverage for all populated cells, edge pixels, multi-touch and vertical
  exit/re-entry;
- TalkBack traversal, labels, click actions, empty-node absence and stale-action rejection;
- rotation/recreation and transitions between `GONE` and visible with 0-3 results;
- gesture interaction with more-keys panels, hardware-keyboard suppression and navigation
  insets on API 28+;
- Android runtime allocation sampling, janky-frame rate, PSS and cold-start impact.

These checks belong to the D1f device matrix. Host tests and a successful APK build do not imply
their outcome.
