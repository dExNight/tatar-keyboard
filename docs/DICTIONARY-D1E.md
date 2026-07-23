# D1e — opt-in Tatar suggestions integration

Branch: `codex/d1-sequential` · Base commit: `80f332c` (D1d) · Status: **implemented; per-phase gate GREEN on a machine with the Android SDK.**

The code was written and reviewed in the Cowork cloud sandbox (no Android SDK there); the build / JVM-test / lint / APK gates that `PROPOSALS.md` requires were then run on the developer Mac and passed (see "GATE" below). Device UAT (D1f) remains outstanding.

## Scope delivered

- **Opt-in setting**, default **OFF** (`Settings.PREF_TATAR_SUGGESTIONS` / `SettingsValues.mTatarSuggestionsEnabled`), toggle added to the Preferences screen; strings in en / ru / tt.
- **Eligibility gate** = opt-in ON `&&` current subtype locale `"tt_RU"` `&&` `InputAttributes.mShouldShowSuggestions` `&&` `hasCursorPosition()` `&&` dictionary ready. `mShouldShowSuggestions` already encodes every privacy variation (password / email / URI / filter / `NO_SUGGESTIONS` / autocomplete / non-text classes), reused directly — no re-derivation.
- **`SuggestionsController`** (new) owns storage prep + engine lifecycle: single-thread background executor; `MappedDictionaryEngine` started once, **off the UI thread**, at the first eligible field; destroyed at `onDestroy`; no live hot-swap (keeps its mapping for its lifetime — matches the "next safe lifecycle" rule).
- **Prefix** taken from the `RichInputConnection` cache snapshot (`getCachedTextBeforeCursor`, **no synchronous editor IPC**, not logged); current word = maximal trailing run of letters; normalized `NFC + lowercase` to byte-match the D1a asset (`scripts/dictionary_coverage.py::normalize_word`); UTF-8 encoded. `TdictPrefixIndex` does not re-normalize at lookup, so caller-side normalization is authoritative.
- **Result application** only after `sessionId` match **and** `engine.isCurrent(token)`, marshaled to the UI thread.
- **Invalidation** on genuine external selection change, `onFinishInput`, and subtype change (bumps `sessionId`, hides strip, `engine.finishInput()`). Self-inflicted commits do **not** invalidate.
- **Safe delete+commit tap** (`InputLogic.commitChosenSuggestion`) with a stale-tap guard: re-reads the live cache, requires `!hasSelection()` and the current trailing word to still equal the expected prefix, then batch `deleteTextBeforeCursor(prefix.length)` + `commitText`. A stale/desynced tap is a **no-op** (no text change). No auto-space appended.
- **TalkBack**: the strip announces available suggestions on update (`spoken_suggestions_available`, en/ru/tt).
- **Stable strip height**: the strip is reserved (VISIBLE, fixed 40dp) for the whole *eligible* input session and only swaps word/empty content, so the keyboard never resizes per keystroke or on space. It collapses to `GONE` (0dp) only when the field becomes ineligible (`onFinishInput`, non-Tatar subtype, opt-in OFF, privacy field) — so `GONE` stays fail-closed for every privacy case. This removes the visible keyboard "jump" observed during on-device testing.

## Changeset

New (5): `suggestions/TatarWordUtils.kt`, `suggestions/EngineHandle.kt`, `suggestions/SuggestionsController.kt`, and tests `test/.../suggestions/TatarWordUtilsTest.kt`, `SuggestionsControllerTest.kt`.

Edited (8): `LatinIME.java`, `RichInputConnection.java`, `inputlogic/InputLogic.java`, `settings/Settings.java`, `settings/SettingsValues.java`, `settings/SettingsHostActivity.kt`, `suggestions/SuggestionStripView.kt`, and `res/values{,-ru,-tt}/{strings,strings-a11y}.xml`.

## Independent review + fixes (record)

A 3-dimension adversarial fail-closed review (concurrency/lifecycle, privacy/text-safety, integration/compile-consistency) found and all were fixed, with an independent re-verification returning **0 new issues**:

- **BLOCK** — engine start racing `onDestroy` orphaned a started engine → permanent dictionary-lease/mmap leak (and would block a future dictionary version from activating). Fixed with a `destroyed` flag + a `publishEngine` guard that tears down a late-completing handle instead of assigning it.
- **HIGH** — `onUpdateSelection` invalidated on self-inflicted commits, so the strip never persisted during normal typing (feature effectively dead). Fixed by computing `externalMove` from the expected selection before it is overwritten and only invalidating on genuine external cursor moves.
- **MEDIUM** — `onDestroy` ignored `destroy()`'s timeout result. Fixed: bounded retry, engine reference not nulled until teardown succeeds.
- **LOW** — `hasKnownCursor()` was unused. Fixed: `onTextChanged` now fails closed when the cursor is unknown.
- **LOW** — `engineCatalog()` dead code; the second-catalog path was **verified safe** for the single shipped version (process-static lease registry), left as-is.

Two regression tests added (post-destroy start is torn down not activated; unknown-cursor gate).

Review **verified clean**: Kotlin↔Java interop and every cross-file signature; resources present/consistent in all three locales with a single `%s`; off-UI engine start; `applyResult` double-guard; stale-tap safety; normalization byte-match; privacy gates; no editor text logged.

## Automated evidence produced in THIS environment (no Android SDK)

- **no-INTERNET Level 1** (source manifest): PASS — 0 `INTERNET`; declared permissions `BIND_INPUT_METHOD` + `VIBRATE` only.
- Brace / symbol / interop static checks: PASS (agent-verified, cross-checked).
- `main` untouched (tip `2e72f6c`); D1e changes uncommitted on `codex/d1-sequential` (`80f332c`).

## GATE — run on the Mac (Android SDK), Gradle 9.6 (PASS)

| Gate | Command | Result |
|------|---------|--------|
| Compile (debug+release) | `assembleDebug` / `assembleRelease --rerun-tasks` | **BUILD SUCCESSFUL** (Kotlin + Java, both variants; only pre-existing deprecation warnings) |
| Full JVM unit-test suite | `test --rerun-tasks` | **BUILD SUCCESSFUL** — all unit tests pass, incl. new `suggestions/TatarWordUtilsTest` + `SuggestionsControllerTest` |
| Release lint gate | `lintVitalRelease --rerun-tasks` | **PASS** (no fatal issues) |
| no-INTERNET | `scripts/check-no-internet.sh …/release/app-release-unsigned.apk` | **Level 1 + Level 2 OK** — release pkg `org.tatarkeyboard.ime`, only `android.permission.VIBRATE` |
| Release APK size | `ls -l …/release/app-release-unsigned.apk` | **1,440,751 B** — delta **+10,416 B** vs D1d `1,430,335 B`; well under the 1.7 MB budget |
| `main` untouched | — | tip `2e72f6c`, no tracked changes |

Note: the full `lintDebug` report carries a pre-existing baseline of ~125 errors unrelated to D1e; the blocking release gate is `lintVitalRelease`, which passed. The gate above was re-run after the post-integration strip-height refinement (reserve-for-eligible-session); all green.

## Commit (only after gates are green)

`PROPOSALS.md` message: `feat(suggestions): integrate opt-in Tatar suggestions`

## Outstanding

- **D1f device UAT** (blocked — needs a physical Samsung / emulator): on-device suggestion behavior end-to-end, all cell taps + edge pixels, TalkBack, rotation, moreKeys, insets, the full privacy-field matrix, delete+commit, cold start, PSS / FD lifecycle, allocation / jank, end-to-end latency.
- **Tatar strings** (`tatar_suggestions` title/summary + `spoken_suggestions_available`) are machine-assisted; `values-tt` already carries an "awaiting native-speaker proofread" banner — needs a native Tatar review before release.
