---
phase: 06-ios-skin-canvas
review_of: e0d470b..HEAD (5981b6e)
depth: deep
status: approved-with-findings
findings:
  blocker: 0
  major: 1
  minor: 5
  info: 8
build_verified: true   # assembleDebug OK; merged manifest has only VIBRATE (no INTERNET, aapt-verified)
---

# Phase 06 Review — iOS-скин (тема 7, drawables, PERF-фиксы, a11y-каркас)

Reviewed: KeyboardTheme.java, KeyboardView.java, MainKeyboardView.java, KeyDrawParams.java, Key.java, KeyDetector.java, KeyboardIconsSet.java, themes-tatar.xml, colors (day/night), 3 ios_* selectors, 3 icon vector paths, KeyboardAccessibilityDelegate.kt, build.gradle, gradle.properties. Debug build executed and APK inspected.

## MAJOR

### M1. iOS key selectors drop checkable/checked/active states → caps-lock visual state lost; shift-locked icon can be invisible in light theme
`ios_key_normal.xml` / `ios_key_functional.xml` have only two branches: `state_pressed` and default. Upstream `btn_keyboard_key.xml` additionally handles `state_checkable`/`state_checked` (sticky shift) and `state_active` (action/enter), drawing a highlighted background for the checked (caps-locked) state.

Consequences with the new theme:
- Shift with `backgroundType="stickyOn"` (`key_styles_common.xml:63`) resolves through `Key.selectBackgroundDrawable` (Key.java:884) to **keyBackground = ios_key_normal**, and the checked state falls through to the default item → caps-locked shift looks identical to an unlocked key.
- Worse: `sym_keyboard_shift_locked.xml` hardcodes `@android:color/white` fill (pre-existing), and in the light palette `ios_key_normal = #FFFFFF`. Upstream this worked because the checked state swapped in a dark pressed-border background; now it's a **white filled icon on a white key — the caps-lock indicator is effectively invisible in light mode**. (Dark mode is fine: `#6B6B6B` key.)

Fix options (either is a small diff): add `state_checkable`+`state_checked` items to `ios_key_normal.xml` with a distinct fill (iOS highlights the shift key white/inverted when caps-locked — that's exactly this state), and/or point the locked icon color at `?attr/functionalTextColor` like `sym_keyboard_shift.xml` does. Verify on device in light theme, alphabetShiftLocked layout.

## MINOR

### m1. Partial-redraw branch still allocates — and it's the common typing path
The indexed-loop fix covers only the draw-all branch (KeyboardView.java:284-289). Per-key invalidation (every press/release) goes through the `else` branch: `for (final Key key : mInvalidatedKeys)` (line 291) allocates a HashSet iterator per frame, and `keyboard.hasKey(key)` (Keyboard.java:135) can allocate another iterator on cache miss. Pre-existing upstream behavior, but since the phase's goal was "ноль аллокаций в цикле отрисовки", note that steady-state typing still allocates ~1–2 iterators per invalidation frame. Low priority; fix later with the same pattern if profiling shows it.

### m2. `BACKGROUND_TYPE_EMPTY` keys would now draw a full key background (latent)
Upstream `btn_keyboard_key` draws nothing for `state_empty` unless pressed (no default item for that combination); the ios selectors' unconditional default item matches everything, so an `empty`-type key would render as a normal white key. No layout currently uses `backgroundType="empty"` (grep of res/xml), so this is latent — but it will bite silently if the fifth-row layout ever uses empty spacer-style keys. Consider an explicit `state_empty → @android:color/transparent` item.

### m3. A11y contentDescription quality: raw icon names and "undefined"
`node.contentDescription = key.label ?: KeyboardIconsSet.getIconName(key.iconId)` (KeyboardAccessibilityDelegate.kt:87-88). No crash risk: `getIconName` never returns null (returns `"undefined"` for ICON_UNDEFINED, `"unknown<n>"` for invalid — KeyboardIconsSet.java:122-124), and the helper's non-null validation passes. But TalkBack will announce internal names like "shift_key", "delete_key", and the spacebar (no label, no icon) announces "undefined". Acknowledged as phase-9 scope in the class KDoc — fine as skeleton, just confirming it's tracked.

### m4. Hover hit-testing inherits touch correction offsets
`getVirtualViewAt` uses `keyDetector.detectHitKey` which applies `-paddingLeft / -paddingTop + verticalCorrection` (MainKeyboardView.java:272-273). Node bounds meanwhile are computed with `+paddingLeft/+paddingTop`. The vertical correction (a touch-bias tweak) means hover-explore targets are shifted a few px relative to reported bounds. Harmless in practice for finger-sized keys; worth a note so it isn't a surprise when doing full TalkBack work in phase 9.

### m5. Kotlin stdlib + androidx transitive deps in APK
customview:1.1.0 pulls androidx.core/collection and Kotlin builtins (visible in debug APK). Release commit records 646→701 KB — well inside the 3 MB budget, and R8 strips unused stdlib. Just keep an eye on this when adding further androidx artifacts; pin versions as done here (1.1.0 pinned ✔).

## INFO / verified-correct

1. **KeyDrawParams cache is correct.** Keyed by `Key` (value-equality with precomputed `mHashCode` — Key.java:419/468), one cached clone per distinct key. Invalidation: `mKeyDrawParamsCache.clear()` in `setKeyboard` (KeyboardView.java:178) — every theme switch and layout change goes through `setKeyboard`, so no stale colors after theme change. **No `mAnimAlpha` cross-key leak**: each attr-bearing key has its own cached instance, keys with `attr == null` share `mKeyDrawParams`, and `onDrawKey` resets `mAnimAlpha = ALPHA_OPAQUE` before every key draw (line 322), with MainKeyboardView's altCode override applied after (MainKeyboardView.java:545-548) — same semantics as upstream's fresh clones. **No per-frame allocation**: `HashMap.get` with an object key doesn't box or allocate; `put` (which allocates a Node) happens only on the first draw after a `setKeyboard`. Old-keyboard Keys are released at the next `setKeyboard` clear — no leak.

2. **Spacebar string cache is correct.** `mLanguageOnSpacebarText = null` on `setKeyboard` (covers subtype change, rotation/width change, theme change — a new keyboard is always set) and on `startDisplayLanguageOnSpacebar` (format change). `textScaleX` is cached alongside and re-applied each draw, so the paint state is reproduced without re-measuring. Steady-state `drawLanguageOnSpacebar` no longer calls `LocaleResourceUtils`/`getStringWidth`. (Pre-existing, untouched: `fitsTextIntoWidth` compares the unscaled width against `width` instead of `maxTextWidth` on its first check — an upstream quirk, not introduced here.)

3. **Indexed loop is safe.** `getSortedKeys()` returns an unmodifiable list backed by a copy made at keyboard construction (Keyboard.java:96) — stable during draw; `List.get` on the underlying ArrayList is O(1), no iterator.

4. **Remaining draw-all path allocations: none found.** `TypefaceUtils` char-geometry caches only box on first miss per text size; `getTextBounds` into reused static Rects; no string/Rect/varargs allocation in `onDrawKeyTopVisuals`. Only the partial-redraw branch remains (m1).

5. **Theme id=7 registration is consistent.** `searchKeyboardThemeById` is a linear scan (KeyboardTheme.java:87) — array ordering is presentation-only, no binary-search assumption; Tatar placed first matches the settings arrays where all four (`names`, `ids`, `ids_string`, `colors`) got item 7 prepended in lockstep, so `Settings.java:301-303` index mapping holds. `attrs.xml` keyboardTheme enum extended with `Tatar=7`; no `<case latin:keyboardTheme>` exists in any res/xml, so no layout switches silently change. `DEFAULT_THEME_ID = THEME_ID_TATAR` with `customColorSupport=false` correctly hides the custom-color path (KeyboardView.java:268 guards on `mCustomColorSupport`).

6. **values-night parity: exact.** All 13 `ios_*` color names exist in both `values/colors.xml` and `values-night/colors.xml` — no missing-resource-at-night risk. `keyboard_theme_colors` uses `@color/ios_keyboard_background`, which resolves per configuration.

7. **Layer-list shadow: correct.** Shadow layer inset `top=1dp`, key layer inset `bottom=1dp` → shadow peeks out at the bottom edge (downward, iOS-style). Both layers carry `corners radius=5dp`, so no square shadow corners.

8. **Icons plausibly original.** Shift: a plain outlined up-arrow-on-stem polygon (`M 12,4.5 4.5,12.5 h4 v6.5 h7 v-6.5 h4 z`) — generic keyboard geometry, hand-authored coordinates, not an SF Symbols trace. Shift-locked: same arrow filled plus a separate bar rect — the classic caps indicator. Globe: constructed from primitives (outer circle via two arcs, inner ellipse, meridian + two latitude lines as strokes) replacing the previous filled-path globe — clearly synthesized geometry, not traced artwork. Legal posture looks fine (Roboto untouched, no Apple assets).

9. **ExploreByTouchHelper contract satisfied.** `getVisibleVirtualViews` filters spacers; stale-id branch in `onPopulateNodeForVirtualView` sets non-null (empty) contentDescription and non-empty 1×1 bounds — passes the helper's `text/contentDescription non-null` and `parent bounds set` RuntimeException checks (verified against customview-1.1.0 bytecode). `dispatchHoverEvent` delegates to the helper first; the helper returns false when touch exploration is off, so normal touch (`onTouchEvent`/PointerTracker) is untouched. Delegate constructed after `mKeyDetector` (ctor line 134 vs 184) — no null field capture. `invalidateRoot()` on every `setKeyboard` keeps virtual ids (list indices) coherent. No Kotlin-interop annotations needed (class only consumed via its androidx supertype; Java→Kotlin ctor call is plain). `getVirtualViewAt`'s `indexOf` is O(n) per hover — fine at a11y event rates.

10. **Build/useAndroidX: safe.** No `android.support` references anywhere in the fork (grep clean) → jetifier not needed and correctly absent; AGP 9.2.1 compiles the `.kt` via built-in Kotlin support without extra plugins. `assembleDebug` succeeds; merged manifest permission set = `VIBRATE` only (INTERNET guarantee holds — verified with aapt on the built APK). Only gradle drift is the single pinned dependency + the `useAndroidX` flip, both recorded as the deviation. Debug APK 2.03 MB (unminified); release 701 KB per phase gate — within budget.

## Verdict

Approve with findings. **M1 should be fixed before UAT** (it's a 10-line selector/icon-color change and directly affects the caps-lock UX in the default light theme). m1–m5 can be deferred/tracked; the three PERF fixes and the theme plumbing are correct as implemented.
