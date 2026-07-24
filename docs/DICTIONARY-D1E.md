# D1e — opt-in Tatar suggestions integration

Branch: `codex/d1-sequential` · Base commit: `80f332c` (D1d) · Status:
**implemented; current runtime hardening tests pass and awaits re-audit; D1f final artifact
gate and device UAT remain open.**

The integration and follow-up runtime fixes are in the local branch through `a277283`,
with the 1.2.0 hardening changes prepared on top. Current JVM and release-vital-lint
checks pass on the developer Mac (see "Current automated evidence" below). This does not
claim the final clean D1f artifact matrix or any device/native-speaker evidence.

## Scope delivered

- **Opt-in setting**, default **OFF** (`Settings.PREF_TATAR_SUGGESTIONS` / `SettingsValues.mTatarSuggestionsEnabled`), toggle added to the Preferences screen; strings in en / ru / tt.
- **Eligibility gate** = opt-in ON `&&` current subtype locale `"tt_RU"` `&&` `InputAttributes.mShouldShowSuggestions` `&&` `hasCursorPosition()` `&&` dictionary ready. `mShouldShowSuggestions` already encodes every privacy variation (password / email / URI / filter / `NO_SUGGESTIONS` / autocomplete / non-text classes), reused directly — no re-derivation.
- **`SuggestionsController`** (new) owns storage prep + engine lifecycle: single-thread background executor; `MappedDictionaryEngine` started once, **off the UI thread**, at the first eligible field; destroyed at `onDestroy`; no live hot-swap (keeps its mapping for its lifetime — matches the "next safe lifecycle" rule).
- **Prefix** taken from the `RichInputConnection` cache snapshot (`getCachedTextBeforeCursor`, **no synchronous editor IPC**, not logged); current word = maximal trailing run of letters; normalized `NFC + lowercase` to byte-match the D1a asset (`scripts/dictionary_coverage.py::normalize_word`); UTF-8 encoded. `TdictPrefixIndex` does not re-normalize at lookup, so caller-side normalization is authoritative.
- **Result application** only after `sessionId` match **and** `engine.isCurrent(token)`, marshaled to the UI thread.
- **Invalidation** on genuine external selection change, `onFinishInput`, and subtype change (bumps `sessionId`, hides strip, `engine.finishInput()`). Self-inflicted commits do **not** invalidate. Returning `tt → non-tt → tt` or opening a later eligible field with an already-published engine immediately re-requests the current known non-empty cached prefix; cold/in-flight publication remains the sole request path for a newly started engine.
- **Safe delete+commit tap** (`InputLogic.commitChosenSuggestion`) with a stale-tap guard: re-reads the live cache, requires `!hasSelection()` and the current trailing word to still equal the expected prefix, then batch `deleteTextBeforeCursor(prefix.length)` + `commitText`. A stale/desynced tap is a **no-op** (no text change). No auto-space appended.
- **TalkBack**: the strip announces available suggestions on update (`spoken_suggestions_available`, en/ru/tt).
- **Fail-closed availability + stable height**: eligibility while the dictionary is preparing or its engine is unavailable stays `GONE` (0dp). Only a successfully published engine reserves the fixed 40dp band; from then on it swaps word/empty content without resizing per keystroke or space. It returns to `GONE` for `onFinishInput`, non-Tatar subtype, opt-in OFF, or a privacy field.

## Changeset

New (5): `suggestions/TatarWordUtils.kt`, `suggestions/EngineHandle.kt`, `suggestions/SuggestionsController.kt`, and tests `test/.../suggestions/TatarWordUtilsTest.kt`, `SuggestionsControllerTest.kt`.

Edited (8): `LatinIME.java`, `RichInputConnection.java`, `inputlogic/InputLogic.java`, `settings/Settings.java`, `settings/SettingsValues.java`, `settings/SettingsHostActivity.kt`, `suggestions/SuggestionStripView.kt`, and `res/values{,-ru,-tt}/{strings,strings-a11y}.xml`.

## Independent review + fixes (record)

A 3-dimension adversarial fail-closed review (concurrency/lifecycle, privacy/text-safety,
integration/compile-consistency), followed by the D1e runtime audit, found the following
issues; all code-level blockers were fixed and the core fixes were independently
re-verified:

- **BLOCK** — engine start racing `onDestroy` orphaned a started engine → permanent dictionary-lease/mmap leak (and would block a future dictionary version from activating). Fixed with a `destroyed` flag + a `publishEngine` guard that tears down a late-completing handle instead of assigning it.
- **HIGH** — `onUpdateSelection` invalidated on self-inflicted commits, so the strip never persisted during normal typing (feature effectively dead). Fixed by computing `externalMove` from the expected selection before it is overwritten and only invalidating on genuine external cursor moves.
- **MEDIUM** — `onDestroy` ignored `destroy()`'s timeout result. Fixed: bounded retry, engine reference not nulled until teardown succeeds.
- **LOW** — `hasKnownCursor()` was unused. Fixed: `onTextChanged` now fails closed when the cursor is unknown.
- **LOW** — `engineCatalog()` dead code; the second-catalog path was **verified safe** for the single shipped version (process-static lease registry), left as-is.
- **HIGH** — dictionary readiness could arrive after an eligible session had already opened without starting the engine/looking up the current word. Fixed by serializing readiness onto the UI owner, starting only in a live eligible session, and requesting the current prefix after publication; a late callback after destroy is inert.
- **HIGH** — a visible candidate could be committed against a newer pending prefix. Fixed by atomically binding shown candidates to their exact displayed prefix/session and invalidating that binding as soon as text changes.
- **Hardening** — an already-warm engine did not re-request the cached prefix after `tt → non-tt → tt` or when a later eligible editor session opened with pre-existing cached text. Both paths now request immediately without duplicating the cold-engine publication request or restarting the factory.
- **HIGH** — `onFinishInput` left eligibility true, allowing a late dictionary-readiness callback to start an engine for a finished session. Fixed by closing eligibility before hide/finish; the deterministic late-readiness regression keeps engine/lookup inactive and the strip hidden.
- **HIGH** — an eligible start reserved the band while the dictionary was still preparing or its engine failed to publish, contradicting the frozen state table. Fixed by keeping the strip `GONE` until successful engine publication; failed/null publication stays hidden.

The five mandatory runtime regression tests cover readiness/eligibility/destroy and stale/safe
tap interleavings. Five additional guards cover tapping with nothing displayed, warm-engine
subtype/cross-field return, and readiness/publish after finish. Preparing-to-published visibility and
failed-engine hidden state are asserted deterministically. Existing teardown and unknown-cursor
tests remain.

The previous core review verified Kotlin↔Java interop, cross-file signatures, off-UI engine
start, `applyResult` guards, stale-tap safety, normalization and privacy gates. The current
finished/preparing-state hardening is covered by the green tests above and still awaits
independent re-audit.

## Current automated evidence — Mac, Android SDK, Gradle 9.6

| Gate | Command | Result |
|------|---------|--------|
| Full JVM unit-test suite | `./gradlew test --rerun-tasks --console=plain` | **BUILD SUCCESSFUL** — 140 tests, 0 failures/errors; `SuggestionsControllerTest`: 33 |
| Release vital lint | `./gradlew lintVitalRelease --rerun-tasks --console=plain` | **BUILD SUCCESSFUL** (no fatal issues; pre-existing deprecation warnings only) |

An earlier D1e phase artifact passed compile, no-INTERNET and size checks, but it predates
the 1.2.0/vc4 bump and current hardening. It is historical evidence only and must not be
reported as the final release artifact.

## Outstanding

- **Final D1f artifact matrix**: clean release build/tests/lint, signed 1.2.0/vc4 APK,
  no-INTERNET, signing scheme, manifest version, permission and size verification, and a
  versioned distributable with checksum.
- **D1f device UAT**: end-to-end suggestions, all cell taps + edge pixels, TalkBack,
  rotation, moreKeys, insets, the full privacy-field matrix, delete+commit, cold start,
  PSS / FD lifecycle, allocation/jank, and end-to-end latency on the target device or
  emulator. None of those measurements is claimed by this document.
- **Tatar strings**: native-speaker proofread requires separately recorded evidence before
  release; this document does not claim it.
