# Phase 05 Review — жесты и multi-touch

**Scope:** `git diff 8e4693e..HEAD` — InputLogic.java, RichInputConnection.java, Settings.java, prefs_screen_preferences.xml, app_restrictions.xml.
**Depth:** standard. **Verdict:** solid AOSP-style implementation; two real correctness gaps in the revert state machine (findings 1–2), the rest minor.

---

## Findings

### F1 (major) — `mJustDoubleSpaced` is never reset on cursor movement → revert can mutate unrelated text

`InputLogic.onUpdateSelection()` (InputLogic.java:122) only forwards to `mConnection.updateSelection()`; it does not clear `mJustDoubleSpaced`. The backspace revert guard (InputLogic.java:377-379) checks only the flag plus "text before cursor is `. `" — there is **no time window** on the revert and no positional check.

Failure scenario: user types `text  ` → auto-period fires (`text. `), then taps elsewhere in the document at a position that happens to be preceded by `". "` (end of any earlier sentence), then presses backspace. Instead of deleting one character, the IME deletes `". "` and inserts two spaces — silent corruption of text the user never double-spaced. AOSP avoids this by resetting double-space state in `resetEntireInputState()` from `onUpdateSelection` when the cursor moves unexpectedly.

Fix: clear `mJustDoubleSpaced` (and optionally `mLastSpaceDownTime`) in `onUpdateSelection()` — or at minimum whenever the new selection differs from the expected one. One line, matches the "state must not leak" comment already in `startInput()`.

### F2 (moderate) — text-committing paths that don't clear the flag: `onTextInput`, `handleConsumedEvent`, paste

- `onTextInput()` (InputLogic.java:105) — ".com" key and **clipboard paste** (`RichInputConnection.pasteClipboard()` routes through `LatinIME.onTextInput`) commit text without touching `mJustDoubleSpaced`.
- `handleConsumedEvent()` (InputLogic.java:169) commits combiner text without clearing it.

Scenario: `word  ` → auto-period → paste text ending in `". "` (or press ".com" after a period) → backspace. The revert guard sees flag=true and `". "` before cursor → deletes the pasted `". "` and inserts `"  "` instead of deleting one character. Same corruption class as F1; F1's fix does **not** cover this (no selection change necessarily observed before the backspace lands). Add `mJustDoubleSpaced = false;` in both `onTextInput()` and `handleConsumedEvent()`.

### F3 (minor) — trigger and revert are not wrapped in a batch edit

`tryDoubleSpacePeriod` does `deleteTextBeforeCursor(1)` + `commitText(". ", 1)` as two separate IPC calls (InputLogic.java:344-345); the revert likewise (381-382). AOSP performs the whole `onCodeInput` inside `beginBatchEdit/endBatchEdit`. Without it, the editor observes an intermediate state (word with no trailing space), which can cause visible flicker, an extra `onUpdateSelection` round-trip, and — in aggressive editors (some WebView/compose fields) — a spurious cache reload between the two ops. Wrap both pairs in `mConnection.beginBatchEdit()/endBatchEdit()`.

### F4 (minor, pre-existing exposure) — `deleteTextBeforeCursor` NPEs when disconnected

`RichInputConnection.deleteTextBeforeCursor()` (RichInputConnection.java:355) calls `mIC.deleteSurroundingText` with no `isConnected()` guard, unlike `commitText`. Pre-existing, but this phase adds two new call sites that can run early in a session (`tryDoubleSpacePeriod`, revert). Low probability (space input normally implies a live connection), noting for a future hardening pass — not a blocker.

### F5 (info) — field-type gating is password-only

Only `mIsPasswordField` is gated (InputLogic.java:341). In a URL/email field, `google. com` can be produced from `google␣␣`. This matches upstream-AOSP behavior closely enough (AOSP gates primarily on password/suggestion suppression), and spaces are rare in such fields; acceptable for MVP. If it ever annoys users, gate additionally on `InputTypeUtils` URI/email variations.

---

## Verified correct

- **Revert semantics** (InputLogic.java:377-385): deletes 2 chars (`". "` — both BMP, so char count == codepoint count is safe here), commits `"  "`, cursor ends after the two spaces (`commitText` with `newCursorPosition=1`). Guarded by an explicit text check, so a stale flag alone can't fire it (the guard is what makes F1/F2 "wrong position" bugs rather than unconditional ones). Sits before the codepoint-aware backspace path and returns early — no interaction with the supplementary-pair delete logic.
- **Timing**: both `mLastSpaceDownTime` and `now` come from `SystemClock.uptimeMillis()` (InputLogic.java:339) — one clock, consistent; 1100 ms matches AOSP `config_double_space_period_timeout`. `mLastSpaceDownTime = 0` after a trigger correctly prevents a third space from chaining (`now - 0` exceeds the window except in the first 1.1 s after device boot — negligible). Name nit: it's a commit time, not a down time.
- **Letter/digit gate**: `Character.isLetterOrDigit` is true for Cyrillic-extended ә ө ү җ ң һ (all Lu/Ll) — Tatar text triggers correctly. Non-BMP: the new accessor steps back by 2 chars over supplementary pairs and `codePointBefore` handles the pair, and `isLetterOrDigit(int)` takes the full codepoint — correct.
- **Cache-cold behavior — fails closed**: empty/short `mTextBeforeCursor` → `Constants.NOT_A_CODE` (-1) → `isLetterOrDigit(-1)` is false → no period inserted. Same for the revert guard (no revert on unknown text). Correct direction.
- **Accessor off-by-one** (RichInputConnection.java:307-316): offset 0 ≡ existing `getCodePointBeforeCursor()`; offset 1 skips exactly one codepoint (by its own surrogate width) then reads the previous one. Verified by trace; loop bound-checks `index < 1` both inside and after. Correct. Cache-only, zero IPC — meets the "no blocking in hot path" constraint.
- **State resets — all claimed paths present in the diff**: `startInput()` (:80-81, also reached from `onSubtypeChanged()` → covers mid-window subtype switch), `handleNonSeparatorEvent` (:305), non-space separator branch (:321), backspace with selection (:374) and backspace-without-revert (:386), and both exits of `tryDoubleSpacePeriod`. Enter-as-newline is a word separator (`\n` in `symbols_word_separators`) → reset; Enter-as-editor-action doesn't commit text and the flag's text guard covers it. moreKeys single-codepoint commits route through `onCodeInput` → reset. The uncovered committing paths are exactly F1/F2.
- **Space key is not repeatable** (`spaceKeyStyle` has no `isRepeatable`) — holding space can't machine-gun the timestamp.
- **Pref flips**: exactly 3 (`Settings.readSpaceSwipeEnabled` default, `prefs_screen_preferences.xml`, `app_restrictions.xml`), all `pref_space_swipe`, `pref_delete_swipe` untouched, XML well-formed. Nit (pre-existing, not this diff): the `pref_space_swipe` restriction's title is `@string/pref_enable_ime_switch` — looks like an upstream copy-paste; worth a separate one-liner someday.
- **Hot path allocations**: `tryDoubleSpacePeriod` allocates nothing on the common non-trigger path (primitives + string literals only); trigger path allocates only inside framework `commitText`. Accessor allocates nothing. ✓
- **Constraints**: no new deps, no INTERNET-adjacent code, diff is minimal and matches upstream Java style (final params, javadoc, member prefixes).

## Recommendation

Fix F1 and F2 before calling the phase done (both are one-line flag resets — small, safe, in the spirit of the existing B1 plan revision); F3 is a cheap robustness win to take alongside; F4/F5 can be deferred.
