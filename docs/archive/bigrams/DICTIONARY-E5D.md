# E5d — integration: text contract amendment, commit path, settings

Generation date: 2026-08-17. This document is the design record and fail-closed
acceptance record for E5d (PROPOSALS.md, "## E5", subsection "E5d" of scope/deliverable
and of Fail-closed acceptance). E5d wires the E5c mechanism (already independently
reviewed, `docs/DICTIONARY-E5C.md`) into real user input for the first time in E5:
context extraction from the live editor cache, a third commit path, and the settings
text that has to describe what the toggle now does.

## What was built

- **"Контракт текста" amendment** (`PROPOSALS.md`, five points, one doc-commit, before
  any integration code) — the NEXT_WORD mode of the current-prefix definition, the
  single exception to "empty prefix clears the band", the packing-order/no-re-ranking
  rule for predictions, the three additional pre-tap re-checks, and two new rows in
  "Состояния полосы". Full text and the "what changed / what didn't" breakdown for each
  point live in `PROPOSALS.md` itself, in the amendment blocks dated 2026-08-17.
- **`TatarWordUtils.extractNextWordContext`** — the context-word extraction, sharing
  `extractTrailingWord`'s word-boundary algorithm but with its own separator rule
  (exactly one-or-more U+0020, nothing else) and its own cache-boundary guard (both the
  separator run and the word itself must not touch index 0 of the given text).
- **`SuggestionsController`** — `requestCurrentPrefix()` now falls through to a new
  `requestNextWordContext()` on an empty prefix instead of unconditionally clearing;
  `applyResult` is split into `applyPrefixResult`/`applyNextWordResult` (the latter
  skips casing re-application entirely); `onTap` reads which of
  `displayedPrefix`/`displayedContextWord` is bound (never both) and calls the matching
  editor commit method. Every lifecycle boundary that used to null only
  `displayedPrefix` now nulls `displayedContextWord` alongside it, and the four
  "re-request the cached prefix on a warm engine" gates (`onStartInput`,
  `onSubtypeChanged`, `onSuggestionsSettingEnabled`, `publishEngine`) no longer skip the
  call when the prefix is empty — an empty prefix is exactly when NEXT_WORD needs to
  fire, so skipping it there would silently drop the "no extra keystroke" guarantee for
  predictions that PREFIX already has.
- **`EngineHandle`/`ResultCallback`** — widened to carry `LookupKind` from the engine's
  own `LookupResult.kind` (E5c) up to the controller, which previously discarded it.
- **`InputLogic.commitPredictedWord`** — the third insertion path. Re-checks collapsed
  selection, no letter after the cursor, an EMPTY trailing word (not a matching
  non-empty prefix — NEXT_WORD only ever applies to an empty one), and the live context
  word re-extracted by the same algorithm the request was built with. Deletes zero
  characters; inserts with the same auto-space rule and shift-state refresh the other
  two paths use. Placed deliberately outside the byte range
  `SuggestionStripSourceContractTest` slices as "the shared `replaceTrailingWord` body"
  (after `revertTatarAutocorrection`, not between `replaceTrailingWord` and it) — an
  earlier placement inside that slice was caught by that very test going red before any
  new test was even added, and is the one regression this phase found and fixed rather
  than pre-empted by design.
- **`LatinIME.java`** — wires `EditorSurface.cachedNextWordContext()` and
  `commitPredictedWord()` to `RichInputConnection`'s cache and to
  `InputLogic.commitPredictedWord`, refreshing shift state after a successful commit
  exactly like the other two paths.
- **Settings text** — `tatar_suggestions_summary` and the E1b one-shot dialog's
  `tatar_suggestions_offer_message` revised to mention predictions, not only
  suggestions; new/changed strings recorded in `docs/TATAR-REVIEW-QUEUE.tsv` as
  `pending` in the same commit as the resources (see "Native review" below).

## Fail-closed acceptance

- [x] **Contract amendment introduced and reviewed before integration code, five points
  named, no old formulation left contradicted.** `PROPOSALS.md` amendment blocks dated
  2026-08-17 (in "Контракт текста" for four points, in "Состояния полосы" for the
  fifth); each quotes the exact prior formulation, states its fate (`дополняется`), and
  explains what changed and what didn't. Landed as its own doc-only commit before the
  first code commit of this phase.
- [x] **A non-empty prefix never shows a prediction; an empty prefix never shows a
  prefix suggestion — both directions tested.**
  `aNonEmptyPrefixNeverShowsAPredictionAndAnEmptyPrefixNeverShowsAPrefixSuggestion`
  (`SuggestionsControllerTest`) checks both directions explicitly, on top of
  `nonPrefixModeSuggestionsAreNeverMixedWithPredictionsInOneBand`, which checks the
  live mode-switch itself.
- [x] **No separate prediction toggle anywhere.** `PREF_NEXT_WORD_PREDICTION` was never
  introduced; prediction is entirely governed by `PREF_TATAR_SUGGESTIONS`. Confirmed by
  `noSeparateNextWordPredictionToggleExistsAnywhere`
  (`SuggestionStripSourceContractTest`), which greps `Settings.java`, `SettingsValues.
  java`, `SettingsHostActivity.kt`, and both string resource files for the key, its
  snake_case form, and a hypothetical `switchRow` id — none present anywhere.
- [x] **Engine publication does not wait for the bigram table** — an E5c property, not
  re-implemented here, and E5d does not add any code on that path: the new
  `requestNextWordContext()` call in `publishEngine` runs after `maybeAttachBigramSource`
  the same way `requestCurrentPrefix()` already did before E5d, and both remain calls
  that only ever schedule/read state, never block on I/O.
- [x] **Settings texts revised to describe predictions too; new Tatar strings queued as
  `pending`.** `tatar_suggestions_summary` and `tatar_suggestions_offer_message` (EN)
  revised, Tatar drafts added, both rows in `docs/TATAR-REVIEW-QUEUE.tsv` marked
  `pending` in the same commit as the resource changes — one new row
  (`tatar_suggestions_summary`, never queued before) and one existing row reset from
  `approved` to `pending` (its prior approval was for the pre-E5d English wording, which
  no longer matches what ships).
- [x] **Predictions absent (band visible, 0 results) in every named case.** Sentence
  start, after a newline, after any punctuation, after NBSP or tab — all mechanical
  consequences of `extractNextWordContext`'s separator rule (exactly one-or-more
  U+0020), covered by `sentenceStartAndPositionsAfterPunctuationNeverBuildAContext`
  (`TatarWordUtilsTest`). Context word not found in the table — E5c's own fail-closed
  coverage (`predictReturnsEmptyBeforeABigramSourceIsAttached`,
  `attachBigramSourceFailsClosedOnCorruptTableAndLeavesPrefixUnaffected`, `docs/
  DICTIONARY-E5C.md`), unchanged by E5d. Context reaching the cache boundary —
  `extractNextWordContextEmptyWhenTheSeparatorItselfReachesTheCacheBoundary` /
  `...TheWordItselfReachesTheCacheBoundary`. Non-Tatar subtype, privacy gate, disabled
  suggestions — all collapse to the pre-existing `eligible == false` gate at the very
  top of `requestCurrentPrefix()`, which NEXT_WORD falls through from and therefore
  inherits without new code; `emptyPrefixWithSuggestionsOffClearsResultsAndNeverBuildsARequest`
  covers the disabled-suggestions case directly.
- [x] **A predicted tap deletes nothing; a stale tap is a no-op.**
  `commitPredictedWordDeletesExactlyZeroCharacters` (source-contract: no
  `deleteTextBeforeCursor`/`deleteSurroundingText` call anywhere in the method body) and
  `commitPredictedWordRequiresCollapsedSelectionNoLetterAfterCursorEmptyTailAndLiveContextMatch`
  (source-contract: all four re-checks present). The live functional proof that a
  desynchronized context makes the tap a no-op is Android-only (`InputLogic` needs a
  real `RichInputConnection`) — see "Not covered" below.
- [x] **One `commitText` with auto-space, existing suppression rules, double-space
  gesture disarmed, shift state refreshed.**
  `commitPredictedWordInsertsWithAutoSpaceInOneCommitText` (source-contract): exactly
  one `mConnection.commitText(`, `needsAutoSpace` gates the space, `beginBatchEdit`
  precedes it, `mJustDoubleSpaced`/`mLastSpaceDownTime` reset. "The next triple is
  requested without an extra keystroke" is the widened re-request gates above,
  exercised behaviourally by `emptyPrefixWithSuggestionsOnAndAvailableContextBuildsANextWordRequest`.
- [x] **Existing `commitChosenSuggestion` untouched.**
  `commitChosenSuggestionIsByteForByteUnchangedByThisPhase`: its body is a single
  delegating line, asserted verbatim — a full proof for the method itself, since there
  is nothing else in it to diverge.
- [x] **Band height and cell count unaffected by NEXT_WORD, in every transition.**
  `emptyBandStatesShowNextWordRowsWithoutChangingHeightOrCellCount`
  (`SuggestionsControllerTest`) and the "Состояния полосы" amendment itself (two new
  rows, `StripSurface.reserve()`/`showSuggestions()` reused verbatim — no new UI method,
  hence no new code path that could touch height). The real 40dp/`contentTopInsets`
  value is Android-layout-only; see "Not covered" below.
- [x] **Subtype change invalidates predictions immediately; no cross-language leakage.**
  Not a new mechanism: `onSubtypeChanged`/`onSelectionChanged` already bump `sessionId`
  and null both `displayedPrefix` and `displayedContextWord` (this phase added the
  second null to every such site, not a new invalidation path) — the same session-guard
  `applyResult` already checks before either `applyPrefixResult` or
  `applyNextWordResult` runs. `tt→ru→tt` specifically for predictions is not
  independently named as a test; it reduces to the same lifecycle-boundary invalidation
  covered generically at every `displayedContextWord = null` site.
- [x] **Any bigram failure — file missing, checksum mismatch, mmap failure, no space —
  yields absent predictions, unaffected prefix suggestions, fully working ordinary
  input.** E5c's own coverage (`docs/DICTIONARY-E5C.md`), unchanged by this phase: E5d
  adds no new failure surface, only a new caller (`requestNextWordContext`) of an
  already fail-closed `EngineHandle.requestNextWord`.
- [x] **`tt_RU` literal absent from new code; language comes from the active
  subtype.** `requestNextWordContext` reuses the same `SUBTYPE_ID` constant
  (`PersonalSubtypes.TATAR_RU`) the PREFIX path already used — no new literal
  introduced.
- [x] **APK ≤ 3 145 728 Б; size, SHA-256, delta recorded; no-INTERNET holds.** See "APK
  and privacy gates" below.
- [~] **Device-UAT on real hardware; PSS delta; cold start.** NOT_COVERED on physical
  hardware — none available to this mission. Live-verified on the Android emulator
  instead: PREFIX and NEXT_WORD both render correctly and NEXT_WORD's predictions
  match the shipped bigram table exactly (see "Emulator coverage" below). What the
  emulator could not settle (tap-to-commit, TalkBack, rotation, password field, PSS,
  cold start) is named explicitly there and in "Not covered" (mission dossier
  `tatar-e5`, "Device-UAT: эмулятор годится... NOT_COVERED поимённо" — the same
  standard D3 and E4 already applied).

## Regression discipline

Every commit was preceded by a full `./gradlew testDebugUnitTest` run. One real
regression was found by the EXISTING suite (not a new test) before any new test was
added: `commitPredictedWord`'s first placement landed inside the exact byte range
`SuggestionStripSourceContractTest.acceptedSuggestionCarriesItsAutoSpaceInsideTheSameCommit`
slices as "the shared `replaceTrailingWord` body", so its own `commitText` call bumped
that test's "exactly one commitText" count to two. Fixed by moving the whole method
after `revertTatarAutocorrection`, outside the slice — a placement choice, not a logic
change. Two more failures were the widened re-request gates' own intended side effect,
not a bug: two characterization tests pinned an exact `strip.reserve()` call count that
a purely-PREFIX-only gate used to produce; both counts were consciously updated (with a
comment explaining why) rather than the gate narrowed back, because narrowing it would
have silently reintroduced the very gap ("field switch needs an extra keystroke for a
prediction") the widening exists to close.

Test count progression from the E5c close: 685 → 709 (24 new tests: the tests named in
the contract amendment itself, plus the ones found while checking this document
against the fail-closed acceptance list line by line, including the missing-toggle
source-contract test and the explicit both-directions "Сосуществование" test).
**709 tests total, 0 failures/errors** — count re-measured via `./gradlew
testDebugUnitTest --rerun-tasks` and the aggregated `test-results` XML, not carried
forward from an earlier estimate (an independent review caught an off-by-one in an
earlier draft of this line).

## APK and privacy gates

Release APK after E5d: **1 726 976 Б** (before: 1 725 760 Б after E5c — delta
**+1 216 Б**, code and string resources only, no new assets). `1 726 976 / 3 145 728` =
54.9%, 1 418 752 Б free (45.1%). `scripts/check-no-internet.sh` re-run on this build:
both levels OK, the only permission is `VIBRATE`. `lintVitalRelease`, `assembleDebug`,
`assembleRelease` all succeed.

## Native review (TATAR-REVIEW-QUEUE.tsv)

Two rows, both `pending`:

| Resource | Status | Note |
|---|---|---|
| `tatar_suggestions_summary` | `pending` (new row) | Never queued before this phase. |
| `tatar_suggestions_offer_message` | `pending` (reset from `approved`) | The 2026-07-31 approval was for the pre-E5d English wording; the shipped string is different text and needs its own review. |

Neither blocks this phase from closing (the queue rule blocks the RELEASE checklist,
not phase development — `docs/PUBLISH-CHECKLIST.md`), but both are open items this
document records rather than silently carries.

## Not covered (device/hardware, named per item)

- Real-device UAT on Samsung/One UI: prediction show/tap, TalkBack across the three
  cells, rotation, moreKeys, a password field, fast typing + backspace, direct boot.
- PSS delta of this phase on real hardware (≤ 1.5 MB budgeted, `PROPOSALS.md`, "Бюджет
  памяти (PSS)").
- Cold start < 400 ms and the E1c S1/S3 thresholds re-measured on the post-E5 artifact.
- The real 40dp / `contentTopInsets` pixel value in a live layout (proven at the
  contract level: no new `StripSurface` method exists for NEXT_WORD to diverge
  through — this is a structural guarantee, not a rendered-pixel measurement).

Emulator coverage, if the session's Android emulator install completes in time, is
recorded in a follow-up section below; if it does not, the items above remain
NOT_COVERED exactly as listed, matching the standard already set by D3 and E4.

## Emulator coverage (android-30 / google_apis / x86_64, headless AVD)

The install completed and the debug build was exercised on-device. Real typing was
done by tapping the IME's own on-screen keys at pixel coordinates calibrated from
screenshots — `uiautomator dump` cannot see this IME's own canvas-drawn keyboard (it
only ever returned an empty tree for `org.tatarkeyboard.*` content despite `dumpsys
input_method` showing the IME genuinely active), so key positions were located by
scanning screenshot pixel rows/columns rather than read from an accessibility tree.
`adb shell input text` cannot type Cyrillic at all on this build (it throws a Java
`NullPointerException` on any non-ASCII string) — every word below was typed as a
sequence of individual taps.

Verified live, in the Messages app's SMS compose box (a plain suggestion-eligible
text field) with `pref_tatar_suggestions=true` and the `tt_RU` subtype active:

- **PREFIX suggestions render and are correct.** Typing `бар` produced the strip
  `барлык / бара / бары`. A bare single-letter prefix (`т`) produced no candidates —
  consistent with the shipped dictionary's own ranking, not investigated further since
  PREFIX behaviour is unchanged by this phase and was already covered by E1–E4.
- **NEXT_WORD predictions render and match the shipped bigram table exactly.**
  Typing a first word, a space, then `бар`, then a space produced the strip
  `да / дип / һәм` — byte-for-byte the top-3 successors this asset's own
  `scripts/bigram_asset_pack.py`-packed data stores for the head `бар` (independently
  confirmed by decoding `tatar_bigrams_v1.tatbigr.zlib` offline with a throwaway
  Python reader before this run, so the on-device result was checked against a known
  answer rather than eyeballed). Reproduced twice, on two separate app sessions.
- **A real, instructive false start.** The first several attempts typed the target
  word as the very first thing in an empty field and consistently saw an empty strip.
  Root-caused with temporary `Log.w` instrumentation (added, used for one rebuild/
  install cycle, then removed before the final clean build below) rather than left as
  an open question: `requestNextWordContext` logged `context=''` every time, meaning
  `TatarWordUtils.extractNextWordContext` itself was returning empty — not an attach,
  wiring, or lookup failure. Its own doc comment explains why: the word-reaches-cache-
  boundary guard (`separatorStart - word.length == 0`) cannot distinguish "the cache
  was truncated" from "this word genuinely starts the field", and fails closed toward
  the former. A word typed as literally the first content in a field always sits at
  that boundary, so NEXT_WORD never fires for it — by design, not by defect. Typing
  any preceding word first (so the context word no longer sits at position 0) made
  predictions appear immediately and reproducibly. This is a genuine, useful device
  finding: it is a live confirmation that the conservative cache-boundary clause in
  "Контракт текста" behaves on a real `InputConnection` exactly as the JVM tests (with
  synthetic `CharSequence`s) already said it would — not a bug this phase introduces.
- **Tap-to-commit was attempted but not conclusively exercised.** Coordinate-based
  automated tapping at the strip's cell locations proved unreliable in this session
  (a stray tap once landed on the host app's own spellcheck popup over the typed word
  instead of the strip, and backspace/tap sequences occasionally did not land as
  intended) — a limitation of blind pixel-coordinate UI automation against a
  canvas-drawn view with no accessibility tree, not an observed product failure.
  Remains NOT_COVERED live; `commitPredictedWordDeletesExactlyZeroCharacters`,
  `commitPredictedWordRequiresCollapsedSelectionNoLetterAfterCursorEmptyTailAndLiveContextMatch`
  and `commitPredictedWordInsertsWithAutoSpaceInOneCommitText` are its JVM/source-
  contract coverage (see "Fail-closed acceptance" above).

Not attempted this session (time-bounded, not blocked): TalkBack, rotation, a
password field, direct boot, PSS/cold-start measurement. These remain NOT_COVERED
exactly as listed above.
