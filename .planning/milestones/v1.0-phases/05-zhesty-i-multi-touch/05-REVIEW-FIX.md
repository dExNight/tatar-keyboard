---
phase: 05
fixed_at: 2026-07-18T00:00:00Z
review_path: .planning/phases/05-zhesty-i-multi-touch/05-REVIEW.md
iteration: 1
findings_in_scope: 4
fixed: 4
skipped: 0
status: all_fixed
---

# Phase 05: Code Review Fix Report

**Source review:** `.planning/phases/05-zhesty-i-multi-touch/05-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope: 4 (F1, F2, F3, F4; F5 = info, no action per review)
- Fixed: 4
- Skipped: 0

## Fixed Issues

### F1 (major): `mJustDoubleSpaced` never reset on cursor movement

**Files modified:** `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/inputlogic/InputLogic.java`
**Commit:** 5a0f4b6
**Applied fix:** `InputLogic.onUpdateSelection()` now compares the incoming selection against
`mConnection.getExpectedSelectionStart()/getExpectedSelectionEnd()` **before** forwarding to
`mConnection.updateSelection()`. If the update differs from the expected position (a user tap,
arrow keys, or an app-driven edit — anything the keyboard did not cause), both `mJustDoubleSpaced`
and `mLastSpaceDownTime` are cleared, matching AOSP's `resetEntireInputState()` behavior.
Expected updates — i.e. selection changes produced by our own edits, including the double-space
trigger and the backspace revert, whose `commitText`/`deleteTextBeforeCursor` calls keep the
expected-position bookkeeping in `RichInputConnection` in sync — do NOT clear the state, so the
revert window survives normal typing echo. Gesture cursor moves via `onMoveCursorPointer` route
through `mConnection.setSelection()`, which also updates the expected position; and while a
cursor-move gesture is in progress `LatinIME.onUpdateSelection` returns early — after the gesture,
the final selection report compares against the already-updated expected position, so gesture
moves that landed where expected don't falsely fire either. Note: the state intentionally clears
on genuinely unexpected moves even mid-gesture-window — that is the fix.

### F2 (moderate): text-committing paths that don't clear the flag

**Files modified:** `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/inputlogic/InputLogic.java`
**Commit:** b50e854
**Applied fix:** Added `mJustDoubleSpaced = false;` in `onTextInput()` (covers the ".com" key and
clipboard paste, which routes `RichInputConnection.pasteClipboard()` → `LatinIME.onTextInput`) and
in `handleConsumedEvent()` after the combiner-text commit. Pasted or committed text ending in
`". "` can no longer satisfy a stale revert guard.

### F3 (minor): trigger and revert not wrapped in a batch edit

**Files modified:** `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/inputlogic/InputLogic.java`
**Commit:** 65d9c08
**Applied fix:** Both the trigger (`deleteTextBeforeCursor(1)` + `commitText(". ", 1)` in
`tryDoubleSpacePeriod`) and the revert (`deleteTextBeforeCursor(2)` + `commitText("  ", 1)` in
`handleBackspaceEvent`) are now wrapped in `mConnection.beginBatchEdit()` /
`mConnection.endBatchEdit()`, so the editor never observes the intermediate deleted state.
`beginBatchEdit()` nest level is 1 in both paths (no enclosing batch edit on these code paths;
the only other user is `performRecapitalization`/`deleteSelectedText`, which don't overlap).

### F4 (minor, pre-existing): `deleteTextBeforeCursor` NPE when disconnected

**Files modified:** `app/src/main/java/rkr/simplekeyboard/inputmethod/latin/RichInputConnection.java`
**Commit:** 5263805
**Applied fix:** Wrapped `mIC.deleteSurroundingText(numChars, 0)` in an `if (isConnected())` guard,
matching the existing style of `commitText()` and `sendKeyEvent()` in the same file. Cache
bookkeeping (`mTextBeforeCursor`, `mExpectedSelStart`) still updates unconditionally, consistent
with `commitText()`.

## Not Actioned (per review recommendation)

### F5 (info): field-type gating is password-only

No action — the review documents this as AOSP-consistent behavior, acceptable for MVP. Deferred:
optionally gate on `InputTypeUtils` URI/email variations if user feedback warrants.

## Verification

- `./gradlew assembleDebug` — green (exit 0)
- `scripts/check-no-internet.sh` — green (Level 1: no INTERNET in source manifest; Level 2: no
  INTERNET in built APK; only `android.permission.VIBRATE` present)
- Java boundary respected: only `InputLogic.java` and `RichInputConnection.java` changed.
- Hot path: F1's check is two int comparisons on the existing accessors — zero allocations.

---

_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
