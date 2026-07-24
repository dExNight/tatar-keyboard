# D1e — opt-in Tatar suggestions integration

Branch: `codex/d1-sequential` · Base commit: `80f332c` (D1d) · Status:
**implemented; the six confirmed audit findings are fixed and independently re-reviewed;
the D1f artifact gate passed; device UAT on a real Samsung remains open.**

The integration and follow-up runtime fixes are in the local branch through `a277283`, with
the 1.2.0 hardening and the audit fixes on top, and the auto-space follow-up in `bacf177`
(code) / `c3ed443` (docs, branch head). JVM tests, release-vital lint and the
signed-artifact gate pass on the developer Mac (see "Current automated evidence" below).
This does not claim any Samsung/One UI device evidence, any confirmed runtime budget, or a
native-speaker proofread.

## Scope delivered

- **Opt-in setting**, default **OFF** (`Settings.PREF_TATAR_SUGGESTIONS` / `SettingsValues.mTatarSuggestionsEnabled`), toggle added to the Preferences screen; strings in en / ru / tt.
- **Eligibility gate** = opt-in ON `&&` current subtype locale `"tt_RU"` `&&` `InputAttributes.mShouldShowSuggestions` `&&` `hasCursorPosition()` `&&` dictionary ready. `mShouldShowSuggestions` already encodes every privacy variation (password / email / URI / filter / `NO_SUGGESTIONS` / autocomplete / non-text classes), reused directly — no re-derivation.
- **`SuggestionsController`** (new) owns storage prep + engine lifecycle: single-thread background executor; `MappedDictionaryEngine` started once, **off the UI thread**, at the first eligible field; destroyed at `onDestroy`; no live hot-swap (keeps its mapping for its lifetime — matches the "next safe lifecycle" rule).
- **Prefix** taken from the `RichInputConnection` cache snapshot (`getCachedTextBeforeCursor`, **no synchronous editor IPC**, not logged); current word = maximal trailing run of letters; normalized `NFC + lowercase` to byte-match the D1a asset (`scripts/dictionary_coverage.py::normalize_word`); UTF-8 encoded. `TdictPrefixIndex` does not re-normalize at lookup, so caller-side normalization is authoritative.
- **Result application** only after `sessionId` match **and** `engine.isCurrent(token)`, marshaled to the UI thread.
- **Invalidation** on genuine external selection change, `onFinishInput`, and subtype change (bumps `sessionId`, hides strip, `engine.finishInput()`). Self-inflicted commits do **not** invalidate. Returning `tt → non-tt → tt` or opening a later eligible field with an already-published engine immediately re-requests the current known non-empty cached prefix; cold/in-flight publication remains the sole request path for a newly started engine.
- **Safe delete+commit tap** (`InputLogic.commitChosenSuggestion`) with a stale-tap guard: re-reads the live cache, requires `!hasSelection()`, requires that the cursor is not inside a word, and requires the current trailing word to still equal the expected prefix, then batch `deleteTextBeforeCursor(prefix.length)` + `commitText`. A stale/desynced tap is a **no-op** (no text change). The accepted word carries a trailing space in that same `commitText` unless the text after the cursor already separates it (see "Auto-space after an accepted suggestion").
- **TalkBack**: the strip announces the triple (`spoken_suggestions_available`, en/ru/tt) only on the empty-band → populated transition, and only while touch exploration is on.
- **Fail-closed availability + stable height**: eligibility while the dictionary is preparing or its engine is unavailable stays `GONE` (0dp). Only a successfully published engine reserves the fixed 40dp band; from then on it swaps word/empty content without resizing per keystroke or space. It returns to `GONE` for `onFinishInput`, non-Tatar subtype, opt-in OFF, or a privacy field.

## Behavior after the audit fixes

What the feature actually does today. The authoritative wording stays in the frozen
contract in `PROPOSALS.md` ("Состояния полосы", "Контракт текста"); this section describes
the implementation that now satisfies it.

### Letter right after the cursor

The strip offers nothing while the cursor sits **inside** a word. `EditorSurface.hasLetterAfterCursor()` reads the local right-hand context (`RichInputConnection.getCachedTextAfterCursor()`, cache only, no IPC, never logged) and feeds it to the pure predicate `TatarWordUtils.startsWithWordCharacter`, which classifies the **first code point** — so a supplementary character is not mistaken for a lone surrogate. Any letter (Tatar, Russian or Latin) and any combining mark counts as "inside a word": the check is deliberately wider than the contract's "Tatar letter", because being too narrow corrupts text while being too wide only costs a suggestion.

The controller applies it in `requestCurrentPrefix()` **before** the engine is asked anything: the band stays visible but empty, and the in-flight generation is invalidated, so a late result cannot repaint words for a position the cursor has left. `InputLogic.commitChosenSuggestion` repeats the same check as a fail-closed second line of defense, before any edit reaches the editor. A space, punctuation, a digit, a newline, an emoji or the end of the text all read as "end of word" and keep the normal request path.

### Auto-space after an accepted suggestion

Tapping a suggestion inserts the word **and a trailing space**, so the next word can be typed without reaching for the space bar — the default behavior of Gboard and of the system keyboards, and not a separate setting. The space is part of the same `commitText` as the word, inside the same batch edit as the prefix deletion: the user never sees the word land without its space, and the tap still costs exactly one editor round trip. `RichInputConnection` appends the whole string to its before-cursor cache and advances the expected cursor by its full length, so the stale-tap guard and `deleteTextBeforeCursor` stay in sync.

Whether the space is needed at all is decided by the pure predicate `TatarWordUtils.needsAutoSpace`, which classifies the **first code point** of the cached right-hand context (`getCachedTextAfterCursor()`, cache only, no IPC, never logged):

| Text right after the cursor | Space |
|---|---|
| nothing (end of the text) | appended |
| whitespace or any space character, the non-breaking space included | not appended — it is already there |
| punctuation that hugs the word: `Pc`/`Pd`/`Ps`/`Pe`/`Pi`/`Pf`/`Po` — `,` `.` `!` `?` `:` `;` `)` `]` `-` `_`, and also `«»`, the em dash, the ellipsis and curly quotes | not appended — "сүз," must not become "сүз ," |
| anything else: a digit, a math symbol (`+`, `=`, written as "2 + 2"), an emoji, a letter | appended |

Classification goes through the Unicode categories rather than a hardcoded character list. `SpacingAndPunctuations` was the alternative: it was rejected because it needs `android.content.res.Resources` (so the predicate could not stay a pure JVM-testable function), its `symbols_word_separators` list is ASCII-only and misses exactly the typography Tatar and Russian text uses (`«»`, `—`, `…`), and it answers a different question — "does this character end a word" — which is true of `(`, `+` and `<` as well.

An inserted space must not arm the double-space-to-period gesture, so the commit resets `mJustDoubleSpaced` and `mLastSpaceDownTime` exactly like a lifecycle boundary does. Without the reset, a user who had pressed space less than `DOUBLE_SPACE_PERIOD_TIMEOUT` (1100 ms) ago and then accepted a suggestion would see the next space press rewrite the auto-space into ". ".

Finally, a tap never builds an `InputTransaction`, so nothing would recompute the shift state the way `updateStateAfterInputTransaction()` does after a typed character. `LatinIME.EditorSurface.commitSuggestion` therefore issues the very same `mKeyboardSwitcher.requestUpdatingShiftState(getCurrentAutoCapsState(), getCurrentRecapitalizeState())` after a successful commit, so auto-caps sees the word (and its trailing space) immediately instead of waiting for the editor's `onUpdateSelection`.

### Casing

The dictionary stores NFC lowercase forms only, so the typed capitalization is re-applied on the way out:

| Typed prefix | Classification | Result |
|---|---|---|
| all lowercase (or no cased letter at all) | `LOWER` | dictionary forms shown unchanged |
| one uppercase letter, and it opens the word | `INITIAL_CAPS` | first character uppercased |
| two or more uppercase letters, no lowercase | `ALL_CAPS` | whole candidate uppercased |
| anything else (`сҮз`, `сҮЗ`) | `MIXED` | **0 results**, no lookup at all |

Classification runs on the **raw** prefix, before NFC/lowercase folding, and skips caseless characters outright, so a decomposed prefix classifies exactly like its composed form. `applyCasing` runs **after** ranking (ranking works on the normalized lowercase forms) and takes its casing from the `pendingPrefix` the result was computed for, never from the live editor state. The `expectedPrefix` bound to the tap stays raw, and the strip hands the very same `String` object back on tap, so the displayed and the inserted form are identical by construction.

### Strip vs. the keyboard's own cursor gestures

Space-slide and delete-swipe move the cursor through `RichInputConnection`, which keeps the expected selection in sync — so `onUpdateSelection` sees no *external* move and would not invalidate anything. `onMoveCursorPointer`, `onMoveDeletePointer`, `onUpWithDeletePointerActive` and `onUpWithSpacePointerActive` therefore notify the controller directly through the private `LatinIME.onSuggestionsAffectingCursorMove()`, which bumps the session and clears the band. It fires only when the gesture actually moved or deleted something (`steps != 0`, or a selection was present), so ordinary typing — including a space tap and backspace auto-repeat — never bumps the session and never drops an in-flight result.

### TalkBack announcements

The triple is announced only on the **empty band → populated** transition, and only while `AccessibilityManager.isTouchExplorationEnabled`. The suggestions change on every keystroke; announcing each update buried the key echo that TalkBack users type by. The words themselves stay reachable at any time through the three virtual cell nodes, which are unchanged.

### Canonically decomposed (NFD) input

Word-boundary analysis runs directly on the raw snapshot; the trailing run continues through combining marks (`Mn`/`Mc`/`Me`), so a decomposed "й" (`и` + U+0306) or "ё" (`е` + U+0308) no longer truncates the word — including when the mark is the last thing typed. The returned prefix is a verbatim substring of the raw text and is never normalized in place, which is what makes `deleteTextBeforeCursor(prefix.length)` delete exactly the span that was matched. A run holding no letter at all yields the empty prefix, and leading orphan marks are trimmed off the span. NFC folding happens only later, in `normalizeForLookup`.

## Changeset

New (5): `suggestions/TatarWordUtils.kt`, `suggestions/EngineHandle.kt`, `suggestions/SuggestionsController.kt`, and tests `test/.../suggestions/TatarWordUtilsTest.kt`, `SuggestionsControllerTest.kt`.

Edited (9): `LatinIME.java`, `RichInputConnection.java`, `inputlogic/InputLogic.java`, `settings/Settings.java`, `settings/SettingsValues.java`, `settings/SettingsHostActivity.kt`, `suggestions/SuggestionStripView.kt`, `suggestions/SuggestionStripState.kt`, and `res/values{,-ru,-tt}/{strings,strings-a11y}.xml`.

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

### Second independent audit (six confirmed findings, all fixed)

A later independent audit confirmed six more defects. All are fixed; the behavior each one
produces is described under "Behavior after the audit fixes" above.

- **HIGH** — the tap listener was not re-registered when the strip became active through a subtype change. The strip view is inflated lazily *from* `setTapListener`, and the ineligible-start branch never set it, so switching into Tatar with the globe key in an already-open field left every tap dead. Fixed in `onSubtypeChanged(eligible = true)`.
- **HIGH** — nothing checked for a letter right after the cursor, so a suggestion accepted mid-word spliced itself into the user's text. Fixed with `hasLetterAfterCursor()` in the controller plus a fail-closed repeat in `commitChosenSuggestion`.
- **HIGH** — the frozen casing contract (Initial Caps / ALL CAPS / mixed → 0 results) was not implemented at all. Fixed with `PrefixCasing`/`classifyCasing`/`applyCasing`.
- **MEDIUM** — the keyboard's own cursor and delete-swipe gestures did not clear the strip, leaving tappable candidates bound to a position the cursor had left. Fixed with `onSuggestionsAffectingCursorMove()`.
- **MEDIUM** — TalkBack announced the suggestions on every keystroke. Fixed by announcing only the empty → populated transition, and only under touch exploration.
- **MEDIUM** — NFD combining marks truncated the word boundary. Fixed by continuing the run through `Mn`/`Mc`/`Me` marks and trimming orphan marks.

Thirty-seven new tests came with the fixes: 24 in `TatarWordUtilsTest` (40 in the class now
— boundary analysis, casing classification and application, and the after-cursor
predicate), 9 in `SuggestionsControllerTest` (42), 1 in `SuggestionStripStateTest` (11) and
3 in `SuggestionStripSourceContractTest` (8). At the end of that round the suite stood at
**177** tests (140 → 161 → 177); it has since grown again, see the next paragraph.

The auto-space follow-up (device feedback from a real Samsung: the owner tried the
suggestions by hand, they worked, but an accepted suggestion had to be followed by a
manual space) added 9 more — 6 in `TatarWordUtilsTest` (46) for the `needsAutoSpace`
predicate, 2 in `SuggestionStripSourceContractTest` (10) for the single-`commitText` and
shift-state contracts, and 1 in `SuggestionsControllerTest` (43) for the band after a
commit that ends the word. The full progression is 140 → 161 → 177 → **186**, and 186 is
the current total.

The fixes were re-reviewed independently through three lenses — conformance to the frozen
contract, regression of the previously closed bugs plus lifecycle, and edge cases /
robustness. All three verdicts were **APPROVED_WITH_NOTES**: no blockers, no previously
closed HIGH defect resurrected, remaining notes documentational or minor. That is a code
review, not device evidence.

## Current automated evidence — Mac, Android SDK, Gradle 9.6

Every row describes the current tree — branch head `c3ed443`, auto-space included.

| Gate | Command | Result |
|------|---------|--------|
| Full JVM unit-test suite | `./gradlew test lintVitalRelease --console=plain` | **BUILD SUCCESSFUL** — 186 tests, 0 failures / 0 errors / 0 skipped |
| Release vital lint | same invocation | **BUILD SUCCESSFUL** (no fatal issues; pre-existing deprecation warnings only) |
| D1f artifact gate | `./gradlew assembleRelease` + `aapt2 dump badging/permissions` + `apksigner verify --print-certs` | **PASS** — `dist/tatar-keyboard-1.2.0.apk`, 1 446 111 bytes, SHA-256 `26afd03f200f2939e5ce3b5f102bf4dcd93b5fbb8635161cd393b941cff13bcf`, versionName 1.2.0 / versionCode 4, package `org.tatarkeyboard.ime`, minSdk 24, only `android.permission.VIBRATE`, APK Signature Scheme **v2 only** (v1/v3/v3.1/v4 = false; 1 signer, RSA 4096, `CN=Tatar Keyboard`), certificate SHA-256 `cdd8c535…b09e` matching the historical release certificate |

The artifact was **rebuilt after** the auto-space commit `bacf177`, so the row above and
the 186-test row describe the same tree. `dist/tatar-keyboard-1.2.0.apk` is byte-identical
to `app/build/outputs/apk/release/app-release.apk` (`cmp`, both 1 446 111 bytes). The
earlier APK produced by the audit-fix round — the one the 177-test count belongs to — is
superseded and no longer exists in `dist/`.

## Partial device evidence — emulator only, **not** a Samsung

A UAT pass ran on AVD `tatar_keyboard_d1f_api35_arm64` (Pixel 3a, Android 15 / API 35,
google_apis arm64, headless, `-gpu swiftshader_indirect`).

**Which build it covers.** The installed APK was the **previous** build of
`dist/tatar-keyboard-1.2.0.apk` — the one produced by the audit-fix round, *before* the
auto-space commit `bacf177`. It is **not** the artifact in the gate table above. Auto-space
therefore has **no device evidence at all**: every result below predates it, and none of
them exercises the trailing space, the double-space-gesture reset or the post-commit
shift-state refresh.

`bacf177` edited the tap-commit path itself (`InputLogic.commitChosenSuggestion`,
`LatinIME.EditorSurface.commitSuggestion`) plus the pure `TatarWordUtils` predicate, and
nothing else. So the measurements that do not go through a tap — strip visibility and
height, insets, cold start, PSS, jank, the privacy gating — still describe the current
build. The tap-related functional results do **not**: the code path they exercised has
changed since, and re-running them on the current artifact is part of the open device UAT.

- Functionally green, including all four integration fixes: tap-after-`ru→tt` commits, an
  initial capital yields and inserts capitalized candidates, a cursor inside a word leaves
  the band empty even for a prefix that returns three candidates at the end of a word,
  delete-swipe and space-slide clear the shown suggestions, and a password field keeps the
  strip `GONE`. No crashes, ANRs or tombstones.
- Strip height measured quantitatively: `contentTopInsets` 1294 (suggestions OFF) vs 1184
  (ON) = 110 px = 40 dp at density 440.
- Cold start 124–147 ms against the 400 ms budget.
- **PSS budget failed there**: 33.4–33.6 MB with D1 on vs 29.2–29.4 MB with it off, against
  a 30 MB budget — the feature costs about +4.2 MB PSS. Absolute numbers on a software
  renderer are inflated; the ON/OFF delta is the trustworthy part. Needs a re-measurement on
  real hardware before it is called a release blocker or an emulator artifact.
- Jank 25.93% with Slow UI thread = 0 is a software-GPU profile, so the "janky ~0%" budget
  is neither confirmed nor refuted: NOT_COVERED.
- Never exercised: TalkBack and real virtual-node navigation, rotation/landscape,
  split-screen, hardware keyboard, direct boot, multi-user, dark theme, other densities,
  system locale change.

One UI hosts the IME with its own shell, insets, fonts and layout switcher, so none of the
results above closes the Samsung requirement.

### Manual check on a real Samsung (not a UAT)

The owner installed a build on a real Samsung (serial `R5CY8222TDP`) and tried the
suggestions by hand: they worked functionally, which is what prompted the auto-space
request. That is a single ad-hoc functional check — the D1f matrix was not walked, no
metric was captured, and auto-space itself was not tried, since it did not exist yet. It
closes **no** checkbox; it is recorded here only because it is real evidence that the
feature works on One UI at the "it types" level.

## Outstanding

- **D1f device UAT on a real Samsung**: end-to-end suggestions, all cell taps + edge pixels,
  TalkBack, rotation, moreKeys, insets, the full privacy-field matrix, delete+commit, cold
  start, PSS / FD lifecycle, allocation/jank and end-to-end latency. The emulator pass above
  is partial evidence only, and the manual check above is not a matrix run.
- **Auto-space on a device**: never exercised anywhere. The trailing space, the
  punctuation/whitespace suppression, the double-space-gesture reset and the post-commit
  shift refresh are covered by JVM tests only.
- **PSS budget**: unresolved until re-measured on hardware. A measurement was started on the
  real Samsung but produced no usable numbers (taken with the settings screen open in the
  same process, and with truncated output), so the budget stays **open and potentially
  failing**.
- **Tatar strings**: native-speaker proofread requires separately recorded evidence before
  release; this document does not claim it.
- **Publication**: push/merge/tag, a public GitHub Release and the IzzyOnDroid inclusion
  request are all open. The APK in `dist/` is a local artifact, not a release.
