# Phase 09 Review — Доступность (TalkBack delegate + mapper + strings)

**Diff:** `0a280ce..HEAD` | **Depth:** standard | **Date:** 2026-07-18
**Files in scope:** `KeyDescriptionMapper.kt` (new), `KeyboardAccessibilityDelegate.kt` (+27/−9), `values/strings-a11y.xml` (new), `values-ru/strings-a11y.xml` (new)

## Verdict: ✅ Approve — 1 medium, 2 low, 4 informational findings. No blockers.

The MotionEvent synthesis is correct end-to-end (coordinates, timing, recycle), the mapper's
when-tree has no collisions and handles the shift/enter matrices per AOSP convention, en/ru
strings are at 26/26 parity with valid placeholders, and both hard constraints hold (zero
`.java` changes, no new deps/INTERNET).

---

## Findings

### M1 (Medium) — Tab key announced as «Unknown» on tablet PC-QWERTY

`Constants.CODE_TAB` is not handled in the mapper's `when`, and the tab key is icon-only
(`!icon/tab_key|!code/key_tab`, no label), so it falls to the `else` branch → `key.label`
is null → `spoken_description_unknown`.

- Key style: `key_styles_common.xml:113` (`tabKeyStyle`)
- Real usage: `xml-sw600dp/rows_pcqwerty.xml:39` — reachable on tablets with the PC layout subtype
- AOSP has `spoken_description_tab` for exactly this reason.

**Fix (2 lines + 2 strings):** add `Constants.CODE_TAB -> R.string.spoken_description_tab`
and en/ru strings ("Tab" / «Табуляция»). Same applies in principle to `CODE_PASTE`,
`CODE_ACTION_NEXT`, `CODE_ACTION_PREVIOUS` — but I verified those styles
(`key_styles_common.xml:103`, actions) are **not referenced by any row XML**, so they are
dead until a layout uses them; tab is the only live gap. Can ride the phase-1–9 UAT bundle.

### L1 (Low) — `isTextEntryKey = true` on functional keys: defensible, but document the choice

`KeyboardAccessibilityDelegate.kt:86` sets it unconditionally. The framework contract
("a text entry key that is part of a keyboard or keypad") reads as *any* key of an IME —
TalkBack uses this flag to enable lift-to-type across the whole keyboard including
delete/shift, and marking all keys matches Gboard/LatinIME behavior. So current code is
**acceptable as-is**; the alternative reading (letters only) would break lift-to-type on
delete, which is worse. Recommend a one-line comment citing the lift-to-type rationale so
a future reviewer doesn't "fix" it. Verified `setTextEntryKey` exists in the resolved
`androidx.core:1.3.0` (bytecode check) — no dependency bump needed.

### L2 (Low) — Uppercase non-Tatar letters get no «Заглавная» prefix

`KeyDescriptionMapper.kt:55-60`: the `isUpperCase` → template path fires only for the six
Tatar letters; shifted А-Я fall through to the raw label. TalkBack itself detects a
single-uppercase-char label and announces case (pitch change / «заглавная»), so this is
consistent with how bare-label keys behave in other keyboards — but it means Ә gets an
explicit "Capital …" while А relies on TalkBack heuristics; slight inconsistency in
announcement style. Not worth code now; note for the TalkBack UAT script (deferred Task 4):
listen to both Ә and А shifted and confirm both announce case.

---

## Scrutiny areas

### 1. MotionEvent synthesis — ✅ correct

- **obtain() signature** (`Delegate.kt:102`): 6-arg `obtain(downTime, eventTime, action, x, y, metaState)`
  — valid, non-deprecated overload. Both DOWN and UP share `t` as downTime *and* eventTime:
  satisfies the contract (UP.downTime == DOWN.downTime, eventTime ≥ downTime). Zero-duration
  tap is safe: `PointerTracker.onDownEvent` posts long-press/repeat timers via Handler and
  `onUpEvent` cancels them synchronously before any message runs. The fork's touch-noise
  filter (`PointerTracker.java:461-462`, `deltaT = eventTime` — compared against a ~ms
  threshold while holding an absolute uptime) is effectively inert, so the synthetic DOWN
  can't be swallowed by it either. (That filter looks like a pre-existing fork bug vs AOSP's
  `eventTime - mUpTime`; out of scope, worth a backlog note.)
- **Coordinates** — verified view-local is right: `processMotionEvent` → `tracker.processMotionEvent`
  reads raw `me.getX()/getY()` (`PointerTracker.java:437-438`), then `KeyDetector.getTouchX/Y`
  adds `mCorrectionX/Y` which `MainKeyboardView.setKeyboard` (`MainKeyboardView.java:272-273`)
  sets to `(-paddingLeft, -paddingTop + verticalCorrection)`. So
  `touchX = (key.x + w/2 + padL) − padL = key.x + w/2` — exactly the key center in keyboard
  coords. `verticalCorrection` for MainKeyboardView resolves to
  `config_keyboard_vertical_correction = 0.0dp` (`themes-common.xml:43`; the −26.4dp value is
  only on `MoreKeysKeyboardView.*` styles, incl. `themes-tatar.xml:82`), so Y is exact too.
  The visible center ⊆ `mHitbox` always holds (`Key.java:184` — hitbox is the visible rect
  *expanded* by gap padding), and `getNearestKeys` grid lookup at a key's own center always
  contains that key; spacers never match (`ProximityInfo.java:74` skips them).
- **Recycle timing** — safe. `PointerTracker.processMotionEvent` (lines 415-452) extracts
  primitives only (`getActionMasked/getEventTime/getX/getY/getPointerId`) and never stores
  the event; recycling immediately after return leaks nothing.
- **moreKeys panel open** — if a panel were showing, `processMotionEvent`
  (`MainKeyboardView.java:509-511`) either swallows the event (returns true, no-op) or routes
  it to the panel-owning tracker; no crash, no misfire on the base keyboard. In practice
  unreachable under TalkBack today since the delegate offers no long-press action to open a
  panel. (See I3 below for the follow-up.)
- **Keyboard switched between populate and click** — `sortedKeys()` is re-fetched inside
  `onPerformActionForVirtualView`; a stale/out-of-range id hits `getOrNull → null → return false`
  with **no** TYPE_VIEW_CLICKED — correct. The narrower race (same index, different key after
  a layout swap) taps the *current* key at that index: never unsafe, and
  `setKeyboard → invalidateRoot()` (`MainKeyboardView.java:281`) makes the window tiny.

### 2. Mapper correctness — ✅ correct (minus M1)

- **No code collisions:** functional codes are negative (`CODE_SHIFT=-1 … CODE_UNSPECIFIED=-13`)
  except `CODE_ENTER='\n'` (10) and `CODE_SPACE=' '` (32); Tatar codepoints are ≥ 0x0497 —
  disjoint. Tatar lookup runs before the `when`, so ordering is also safe.
  `Character.isUpperCase(negativeCode)` returns false for invalid codepoints — no trap there.
- **Uppercase detection:** all six pairs verified against Unicode simple case mappings —
  Ә U+04D8→ә U+04D9, Ө U+04E8→04E9, Ү U+04AE→04AF, Җ U+0496→0497, Ң U+04A2→04A3,
  Һ U+04BA→04BB. `Character.toLowerCase` covers all of them; the lookup works. Shifted
  layouts do upcase `mCode` itself (`Key.java:351`, `toTitleCaseOfKeyCode`), confirming the
  comment's premise.
- **Enter/imeAction:** extracted via `keyboard.mId.imeAction()` → `KeyboardId.java:159` →
  `InputTypeUtils.getImeOptionsActionIdFromEditorInfo` (`InputTypeUtils.java:90-98`), which
  handles `IME_FLAG_NO_ENTER_ACTION` → `IME_ACTION_NONE` → mapper's `else` → "Enter". Correct.
  The custom-label-first check mirrors the key construction: `customLabelActionKeyStyle`
  (`key_styles_actions.xml:54-59`) is the only enter variant with a non-null label
  (`Key.java:300-301` pulls `mId.mCustomActionLabel`); all seven action variants are
  icon-only, so the label short-circuit can't shadow an action description. An empty
  `actionLabel` degrades gracefully (`takeIf { isNotEmpty }` → `IME_ACTION_CUSTOM_LABEL`
  = 257 → `else` → "Enter").
- **Label-fallback coverage:** icon-only keys across all shipped layouts are
  shift/delete/space/enter/shift_enter/switch_alpha_symbol/language_switch/settings — all
  have explicit branches. Only `key_tab` leaks to "Unknown" (M1). `key_capslock` exists
  solely as a shift moreKey — moreKeys panels have no a11y delegate at all, so it can't be
  populated (see I3). `CODE_OUTPUT_TEXT → outputText` is right for `.com`-style keys where
  label == outputText.

### 3. Strings — ✅ 26/26 parity, placeholders valid

- Name-set diff of en vs ru: identical, 26 entries each (verified mechanically).
- Placeholders: only `spoken_description_upper_case` has one, `%s` in both files —
  single-substitution `%s` is valid (positional `%1$s` is only lint-required for ≥2 args);
  matches AOSP's own talkback strings.
- No duplicate resource names vs existing `strings.xml` (checked — the fork ships no
  `spoken_*` strings).
- Tatar descriptions read clean: «татарская э/о/у/ж/н/х» per the phase decision (spoken
  approximations for guaranteed RU-TTS coverage), en "Tatar schwa/o/u/zhe/en/he" — no typos.
  «Заглавная татарская э» composes grammatically. Duplicate *values*
  (`to_symbol` = `symbols_shift_shifted` = "Symbols"/«Символы») are intentional and match AOSP.

### 4. Delegate — ✅ contract-clean

- `isTextEntryKey` on all keys: acceptable, see L1.
- `TYPE_VIEW_CLICKED` fires only after both guards pass (action match, live non-spacer key) —
  every failure path returns `false` before `sendEventForVirtualView`. The helper's
  `createEventForChild` re-populates the node and requires non-null text/description — real
  keys always have one, so no `RuntimeException` risk from the event path.
- Stale-id populate branch satisfies all four bytecode-enforced helper invariants
  (non-null contentDescription — `""` passes the `ifnonnull` check; bounds ≠
  INVALID_PARENT_BOUNDS; no ACTION_ACCESSIBILITY_FOCUS/CLEAR added). The added
  `keyboard == null` arm closes the gap the phase-08 version had between `sortedKeys()`
  succeeding and `keyboardView.keyboard` racing to null.

### 5. Kotlin quality — ✅ consistent

- Style matches the phase-08 delegate (same KDoc voice, trailing commas, `tempBounds` reuse).
- Populate path: no new collections; `sortedKeys()` returns the keyboard's existing
  unmodifiable list; `TATAR_LETTERS[code]` autoboxes one `Integer` per populate call (codes
  \> 127 miss the box cache) — negligible, populate is TalkBack-paced, not the draw loop.
  `indexOf` in `getVirtualViewAt` hits `equalsInternal`'s `this == o` fast path
  (`Key.java:443-444`) since keys are identity-stable — O(n) pointer compares, fine.
- `intArrayOf` per click: trivial, cold path.
- No interop annotations needed: the delegate's only Java-facing surface is its constructor
  (`MainKeyboardView.java:184`, wired in phase 08), and the mapper is Kotlin-only.

### Constraints — ✅ verified

- **Zero `.java` changes:** `git diff --name-status` shows only the 2 `.kt` + 2 res files
  (plus `.planning/`). The `MainKeyboardView.java` wiring (lines 184-191, 281) predates
  `0a280ce`.
- **No INTERNET / no new deps:** no manifest or gradle changes in the diff;
  `androidx.customview:1.1.0` and `androidx.core:1.3.0` were already present.

---

## Informational (no action required)

- **I1:** Synthetic events bypass `NonDistinctMultitouchHelper` (delegate calls
  `processMotionEvent` directly, not `onTouchEvent`). Harmless — under TalkBack, real touches
  arrive as hover events, so the helper's `mOldKey` state can't interleave with synthetic taps.
- **I2:** Synthetic events always carry pointer id 0. Same reasoning: touch exploration means
  no concurrent real pointer 0. Would only matter if ACTION_CLICK could arrive mid-real-gesture,
  which TalkBack's input model prevents.
- **I3 (backlog for the a11y follow-up phase):** moreKeys panels are invisible to TalkBack —
  `MoreKeysKeyboardView` has no delegate and long-press isn't exposed as a node action
  (`ACTION_LONG_CLICK`). Today the six Tatar letters are first-class keys so nothing critical
  hides in panels, but caps-lock (shift moreKey) and diacritics are unreachable by TalkBack users.
- **I4 (backlog, pre-existing):** `PointerTracker.java:461` `deltaT = eventTime` — the
  up-to-down noise filter compares absolute uptime against a millisecond threshold and thus
  never fires. Fork divergence from AOSP (`eventTime - mUpTime`); unrelated to this phase.

## Suggested follow-ups

1. **M1:** add `CODE_TAB` branch + `spoken_description_tab` en/ru (fits `/gsd-quick`).
2. **L1:** one-line comment on `isTextEntryKey` citing lift-to-type.
3. Fold L2 + I3 into the deferred TalkBack UAT checklist (phases 1–9 bundle).
