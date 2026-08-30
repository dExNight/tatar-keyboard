# E2a — emoji panel data asset (transformation record)

This document is the provenance and transformation record for the single shipped
emoji-panel payload:

`app/src/main/assets/emoji/emoji_set_v1.txt`

It is produced by `scripts/emoji_pack.py` from the locally downloaded Unicode
`emoji-test.txt`. The Unicode input file is **not** committed to this repository;
only the derived asset, `app/src/main/assets/emoji/NOTICE.txt`, and this record are
shipped. This record covers the asset and its generator only. Runtime code
(`EmojiSet`, `GlyphProbe`, `EmojiTextUtils` and the deletion integration) is out of
scope here except for the two cross-cutting notes explicitly recorded below.

## The four required numbers

| Property | Value |
|---|---|
| Unicode Emoji version | **15.1** (Unicode 15.1; `emoji-test.txt` Date 2023-06-05) |
| Input SHA-256 | `d876ee249aa28eaa76cfa6dfaa702847a8d13b062aa488d465d0395ee8137ed9` |
| Output entries | **1389** |
| Uncompressed asset bytes | **7540** |

Asset SHA-256: `8db92f8869355f79a0a163b3cc6172bc2ff1a8ddf1e0cab7b55aa42977d616c2`

Regenerate and verify byte-for-byte:

```
python3 scripts/emoji_pack.py build \
  --input /path/to/emoji-test.txt \
  --output app/src/main/assets/emoji/emoji_set_v1.txt
```

Identical input (the pinned `emoji-test.txt` plus this generator) produces a
byte-identical asset; determinism is verified by comparing the asset SHA-256 across
two runs.

## Set composition

The generator keeps only records whose status is `fully-qualified`. From those it
removes, **by code point** (never by a literal list), every sequence containing:

- a skin-tone modifier, U+1F3FB..U+1F3FF;
- a zero-width joiner (ZWJ), U+200D;
- a regional indicator, U+1F1E6..U+1F1FF;
- a tag code point, U+E0020..U+E007F.

Keycap sequences and single emoji carrying VS16 (U+FE0F) are kept: they contain none
of the excluded code points. The exclusion arithmetic on the pinned input:

| Quantity | Count |
|---|---:|
| `fully-qualified` records in input | 3773 |
| … containing a skin-tone modifier | 1875 |
| … containing a ZWJ | 1468 |
| … containing a regional indicator | 258 |
| … containing a tag code point | 3 |
| excluded (union of the four classes) | 2384 |
| **kept (shipped entries)** | **1389** |

## Guardrails and the entry-count limit

The generator exits nonzero, and writes no partial asset, when the asset exceeds
**65536 bytes** or **1400 entries**. Current headroom:

| Guardrail | Limit | Actual | Headroom |
|---|---:|---:|---:|
| Uncompressed bytes | 65536 | 7540 | 57996 (asset uses 11.5%) |
| Entries | 1400 | 1389 | **11 (asset uses 99.2%)** |

The guardrail is **not** sized to admit the excluded classes: re-including them takes
the count past 3000 (more than twice the entry limit). Any future widening of the set
requires a written re-derivation of the limits with the arithmetic attached, not a
silent bump.

### Entry-count limit reconsideration (triggered)

The contract requires a written reconsideration in this same change if the measured
base-set count lands within 10% of 1400 (i.e. above 1260). It did: **1389 > 1260**,
leaving only 11 entries of headroom. Per instruction, the limit is **not raised here**:
the measured number is reported and the decision to keep 1400 or to re-derive it is
left to the maintainer/orchestrator. Recorded facts for that decision:

- 1389 is the complete Emoji 15.1 base set under the four exclusions above; it is not
  inflatable by the generator (duplicates and unknown categories fail closed).
- A future Unicode version can only change this number through the version-bump
  procedure below, which re-runs both guardrails; if the next version pushes the count
  past 1400 the generator fails closed by design (that is the guardrail working, not a
  bug) and the limit must be re-derived in writing before proceeding.
- The byte guardrail is not the binding one here; the entry count is.

## Asset format

- UTF-8, LF line endings, one sequence per line.
- Category sections are introduced by a header line of the form `#<slug>`.
- Within a section, entries are in `emoji-test.txt` order for the kept
  fully-qualified records.
- No blank lines, no duplicate sequences, no stray lines. A sequence appearing under
  two groups fails closed as a duplicate.
- Sections with zero kept entries are not emitted. The `Component` group is present in
  the input but contributes only `component`-status records, so it yields no section.

### Section-naming rule

A slug is derived deterministically from the `emoji-test.txt` group name: lowercase the
(ASCII) group name, replace every maximal run of characters outside `[a-z0-9]` with a
single hyphen `-`, and strip leading and trailing hyphens. The full mapping (input
group → section header) is:

| Group | Header | In shipped asset |
|---|---|---|
| Smileys & Emotion | `#smileys-emotion` | yes |
| People & Body | `#people-body` | yes |
| Component | `#component` | no (no fully-qualified records) |
| Animals & Nature | `#animals-nature` | yes |
| Food & Drink | `#food-drink` | yes |
| Travel & Places | `#travel-places` | yes |
| Activities | `#activities` | yes |
| Objects | `#objects` | yes |
| Symbols | `#symbols` | yes |
| Flags | `#flags` | yes |

The set of accepted groups is a pinned allowlist; an `emoji-test.txt` group outside it
is an unknown category and fails closed (guarding against a future version adding a
group without review).

### Header vs. `#️⃣` disambiguation

Exactly one kept sequence begins with U+0023 `#`: the number-sign keycap
`#️⃣` (U+0023 U+FE0F U+20E3). A section header is defined as a line matching
`^#[a-z][a-z0-9-]*$` — `#` immediately followed by an ASCII lowercase letter. The
keycap's second code unit is U+FE0F, not an ASCII letter, so it never matches the
header pattern and the asset stays unambiguously parseable line by line. The generator
asserts this invariant (fail-closed) for every emitted sequence.

Note for manual counting: `grep -vc '^#'` reports **1388**, not 1389, because it also
skips the `#️⃣` keycap line. The authoritative entry count is `total lines − section
headers = 1398 − 9 = 1389`, which the generator reports directly.

## Accepted product decision: no skin tones, no ZWJ, no flags

The user intentionally does **not** get a skin-tone chooser, composite ZWJ emoji
(families, professions, couples, etc.), or flags. This is an accepted decision, not
unfinished work. Reasons: these classes dominate the size growth (they are 2384 of
3773 fully-qualified records) and carry the main "tofu" (missing-glyph) risk on
Android 7–9, where system emoji-font coverage of newer ZWJ and flag sequences is
weakest. Keeping them would more than double the entry count and push against the APK
contribution budget while worsening the exact defect the panel exists to avoid.

## Cross-cutting notes recorded here by contract

- **Deletion still covers the excluded classes.** Removing skin-tone, ZWJ, regional,
  and tag sequences from the *panel* does not narrow deletion: the `EmojiTextUtils`
  backspace-cluster logic must still handle all of these classes, because such
  sequences arrive from other keyboards and from pasted text regardless of what this
  panel offers.
- **Known debt: `RichInputConnection.getUnicodeSteps`.** Cursor swipe and swipe-delete
  remain code-point-based and are intentionally **not** changed in E2. This leaves two
  divergent notions of a "text step" in the codebase (the emoji-cluster deletion path
  vs. the code-point stepping in `getUnicodeSteps`); the divergence is recorded here as
  known debt to be addressed outside E2.

## Changing the Unicode version

Bumping the Unicode/Emoji version is a separate change. It must: update the pinned
input SHA-256 and the expected version in `scripts/emoji_pack.py`, re-run the generator
(which re-checks both guardrails), and record the new SHA-256, the new entry count, and
the new asset byte size in this document. Silently swapping the input file is
forbidden — the SHA-256 pin exists to make a silent swap fail closed.

## E2b-3 — backup closed as a whitelist (final XML and the decisions behind it)

Backup — not `INTERNET` — is the one channel by which a file the app writes can leave the
device: the system transport carries it, and the app needs no permission for that. E2b-3
closes that channel whole, as a **whitelist** (an empty allow-list plus every data domain
excluded), so a file a future phase adds is closed by default rather than by a rule
someone must remember to write.

### Manifest

`app/src/main/AndroidManifest.xml`, `<application>`:

- `android:allowBackup="false"` (was `true`);
- `android:fullBackupOnly` **removed** — it only qualifies *how* Auto Backup runs, so it
  is meaningless once backup is off;
- `android:dataExtractionRules="@xml/data_extraction_rules"` (API 31+);
- `android:fullBackupContent="@xml/backup_rules"` (API 24–30, same meaning in the legacy
  format).

`SettingsHostActivity` no longer calls `BackupManager(...).dataChanged()` (import and the
KDoc line describing it are gone too): it asked the system to back up data that is no
longer backed up.

### The final XML — both editions, verbatim

`res/xml/data_extraction_rules.xml` (API 31+; comments elided here, present in the file):

```xml
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="file" />
        <exclude domain="database" />
        <exclude domain="sharedpref" />
        <exclude domain="external" />
        <exclude domain="device_file" />
        <exclude domain="device_database" />
        <exclude domain="device_sharedpref" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="file" />
        <exclude domain="database" />
        <exclude domain="sharedpref" />
        <exclude domain="external" />
        <exclude domain="device_file" />
        <exclude domain="device_database" />
        <exclude domain="device_sharedpref" />
    </device-transfer>
</data-extraction-rules>
```

`res/xml/backup_rules.xml` (API 24–30):

```xml
<full-backup-content>
    <exclude domain="file" />
    <exclude domain="database" />
    <exclude domain="sharedpref" />
    <exclude domain="external" />
    <exclude domain="device_file" />
    <exclude domain="device_database" />
    <exclude domain="device_sharedpref" />
</full-backup-content>
```

`dataExtractionRules` carries **both** `<cloud-backup>` and `<device-transfer>` because on
Android 12+ `allowBackup="false"` stops cloud backup but does **not** by itself stop
device-to-device transfer; the two sections therefore exclude the same domains. On API
24–30 there is no D2D edition to distinguish, so the single `<full-backup-content>`
governs Auto Backup.

### The set of domain names, and why the device-protected ones are here

Seven domains per section — the app's data domains, regular and device-protected:

| Domain | Storage it names |
|---|---|
| `file` | internal `getFilesDir()` tree |
| `database` | internal `getDatabasePath()` databases |
| `sharedpref` | internal `SharedPreferences` |
| `external` | app-private external storage |
| `device_file` | device-protected files |
| `device_database` | device-protected databases |
| `device_sharedpref` | device-protected `SharedPreferences` |

The device-protected trio is not optional padding: Auto Backup's default rules **include
device-protected `sharedpref`**, which is exactly where the E1b "offer spent" flag
(`pref_tatar_suggestions_offer_spent`) lives. Leaving those domains out would leave the
one place the whitelist most needs to close. There is no `device_external` and no
`external`-under-device domain; `root`/`device_root` are supersets we do not need once the
four leaf domains are named, and naming the leaves matches the contract's enumeration
("files, SharedPreferences, databases, external storage").

Individual paths (`personal/`, `dictionaries/`, the `recent_emoji_v1` medium) are **not**
listed. A whole-domain exclude with an empty allow-list already covers them; listing paths
would invite the forgotten-rule error this shape exists to make impossible.

### Admissibility of a path-less exclude — verified, not assumed

The contract requires this be checked at execution time rather than invented. Checked
against the platform's own build-time validator — lint's `FullBackupContentDetector`
(bundled `com.android.tools.lint:lint-checks:32.2.1`), the check `lintVitalRelease`
enforces:

- **Only `domain` is required.** The detector reports "Missing domain attribute…" when
  `domain` is absent, and "Unexpected domain `X`…" when it is not one of its
  `VALID_DOMAINS`. There is **no** "missing path" diagnostic at all — a `<exclude>` with a
  `domain` and no `path` is a valid whole-domain exclude. (The only path diagnostics are
  "Paths are not allowed to contain `..`/`//`" and "Subdirectories are not allowed for
  domain `X`"; none applies when no `path` is present.)
- **All seven domain names are in `VALID_DOMAINS`** (`file`, `database`, `sharedpref`,
  `external`, `device_file`, `device_database`, `device_sharedpref`, plus the unused
  `root`/`device_root`).
- **`<cloud-backup>` and `<device-transfer>` are accepted section tags.** The detector
  descends into each child of `<data-extraction-rules>` and validates its `<include>` /
  `<exclude>` children; the section tag names themselves are not constrained to a fixed
  list, so both pass.

`lintVitalRelease` reports **"No issues found."** on these files, and `aapt2` compiles
them into both the debug and the release APK without complaint. So no domain here needed
its concrete paths enumerated instead — the whole-domain form is admissible.

### Proof on the built artifact

`scripts/check-no-internet.sh` gained a level-2 backup check (a new function in the same
script, run whenever it is given an APK — the debug and release calls in CI). It runs
`aapt2 dump xmltree` on the in-APK `AndroidManifest.xml`, confirms the actual
`android:allowBackup` value and both rule references, then resolves each reference through
its resource id to the file inside the APK (release resource-shrinking obfuscates the
paths — `data_extraction_rules` → `res/4j.xml`, `backup_rules` → `res/Qq.xml`) and checks
the extracted rules are the whitelist edition (no `<include>`, both sections present for
the 31+ edition, every domain excluded). The raw line the tool prints, from the built APK,
is:

```
A: http://schemas.android.com/apk/res/android:allowBackup(0x01010280)=false
```

### Accepted consequence, stated plainly

**Keyboard settings are no longer restored on a new device and do not come back from a
cloud backup — the user sets them again.** That is the price of the E1b design decision
that the "offer spent" flag lives in the *shared* settings file, combined with the fact
that backup-rule granularity is the **file**, not the key: a single key cannot be excluded
from a `SharedPreferences` file by any rule. The alternative — move that one boolean into
its own file and keep backing settings up — was rejected in E1b as introducing a second
storage format for a single boolean. The same consequence is stated in `PRIVACY.md`.

## E2c — accessibility, input-view recreation, memory release, and gates

This section records the runtime notes the contract requires E2c to write down, plus the
device-UAT matrix. Nothing here is claimed as PASSED.

### Accessibility (ExploreByTouchHelper)

The panel exposes an `ExploreByTouchHelper` (`EmojiPanelView.EmojiPanelAccessibilityHelper`)
modelled on `SuggestionStripView`'s delegate. Virtual nodes are exactly the visible cells, the
category tabs and the two functional keys — the count `EmojiPanelState.virtualNodeCount()`
reports (`visibleCellCount + tabCount + 2`), enumerated from the same hit-tests and geometry the
touch path uses, with no second geometry of its own. A node click runs the same action as a
finger tap through the same listener path (`onEmojiPanelPick` → `LatinIME.onTextInput`,
`onEmojiPanelDelete` → `LatinIME.onCodeInput`, `onEmojiPanelBackToKeyboard`, tab →
`setActiveCategory`), so there is no second insertion or deletion route. The root node exposes
`ACTION_SCROLL_FORWARD`/`ACTION_SCROLL_BACKWARD`; `invalidateRoot()` runs only on a category
change and once a scroll settles, and only while touch exploration is on (a single gate,
`invalidateAccessibilityRootIfExploring()`).

**Accepted accessibility limitation: emoji cells carry the raw sequence as their
`contentDescription`, and how a screen reader voices it is left to the system.** The phase ships
no emoji-name database on purpose: no Tatar CLDR names exist, and shipping English or Russian
names in a Tatar keyboard would be the worst option (extra bytes, a foreign language in the
readout, and an obligation to maintain the list on every Unicode version bump). A blind user gets
whatever system emoji voicing their device has. Only the tabs and the two functional keys get
localized descriptions (tabs via `spoken_emoji_category_*`; "АБВ" reuses
`spoken_description_to_alpha`, delete reuses `spoken_description_delete`).

### Accepted behaviour: the open panel does not survive an input-view recreation

Rotation, a theme change, a keyboard-height change or a subtype change with a layout reload
recreates the input view. The panel closes and the letter keyboard comes back; the "panel was
open" state is deliberately **not** carried across (carrying it would need a state owner that
outlives recreation and a second snapshot-publish path — cost above benefit). A user who rotates
with the panel open lands on the letters. This is accepted behaviour, recorded here so it does
not resurface as a UAT defect. References are cleared in `KeyboardSwitcher.onCreateInputView()`
(`mEmojiPanelShown = false`) and in `InputView.release()` → `EmojiPanelView.release()`;
`LatinIME.onCreateInputView()` drops any deferred show through `onInputViewRecreated()`.

### Memory release (MSG_DEALLOCATE_MEMORY)

The panel allocates no offscreen `Bitmap` at all. On `LatinIME.deallocateMemory()`
(`MSG_DEALLOCATE_MEMORY`, 10 s) and on `onFinishInputView`,
`KeyboardSwitcher.releaseEmojiPanelCaches()` calls `EmojiPanelView.releaseSnapshotCaches()`, which
drops the bound snapshot (`state.setSnapshot(EmojiSetSnapshot.EMPTY)`) and the tab-label and
tab-name arrays and finishes the scroller; the reusable paints and the single `OverScroller` stay.
It is a no-op while the panel is the shown surface, so it never blanks a live grid. The controller
keeps the single prepared snapshot for the whole process (it is never re-prepared), so the next
show simply re-binds it.

**Not a pass/fail criterion (contract):** returning PSS to its pre-open value after
`MSG_DEALLOCATE_MEMORY` is **not** a pass/fail criterion, because part of the growth lives in the
system font's glyph cache, which the app does not own. What is checked instead is what the app
controls: by `MSG_DEALLOCATE_MEMORY` the panel releases its own snapshot and layout caches and
holds no offscreen `Bitmap` (JVM / source-contract plus a reference check), there is no View leak
after the panel closes, and the observed PSS before and after is recorded as a measurement.

### Device-UAT matrix (real Samsung / One UI)

No device is connected, and the PSS measurement is deferred by the owner's decision (contract
amendment 2026-07-27 at the head of the E2 section of `PROPOSALS.md`). Every row below is
therefore `NOT_COVERED` — none is presented as PASSED. Columns follow the cross-cutting
"Доступ к реальному устройству" requirement: item, status, device (model + serial), build
(versionName/versionCode + APK SHA-256), date, raw numbers or path to raw output.

| Item | Status | Device (model, serial) | Build (vName/vCode, APK SHA-256) | Date | Raw numbers / path |
|---|---|---|---|---|---|
| TalkBack: only visible cells, tabs and the two functional keys are separate buttons; invisible cells expose no node | NOT_COVERED | — (device not connected) | — | — | — |
| TalkBack: activating a cell node inserts the same sequence as a tap; back/delete nodes act like taps | NOT_COVERED | — | — | — | — |
| TalkBack: scroll via ACTION_SCROLL_FORWARD/BACKWARD; with touch exploration off the panel calls neither announceForAccessibility nor invalidateRoot on scroll | NOT_COVERED | — | — | — | — |
| Input-view recreation live: rotation, theme, keyboard-height and subtype-with-reload close the panel, show letters, insets correct, no crash/ANR, no View leak; reopen after rotation works | NOT_COVERED | — | — | — | — |
| MSG_DEALLOCATE_MEMORY / onFinishInputView: observed PSS before and after recorded; no View leak after panel close | NOT_COVERED | — | — | — | — |
| E2 own PSS delta (≤ 3.0 MB; arms "emoji key never pressed" vs "panel opened") | NOT_COVERED — PSS deferred by owner (amendment 2026-07-27) | — | — | — | — |
| Absolute PSS within the recomputed ceiling | NOT_COVERED — ceiling not recomputed; PSS deferred | — | — | — | — |
| touchableRegion covers the whole panel (four corners, every tab, both functional keys); no touch falls through | NOT_COVERED | — | — | — | — |
| contentTopInsets open vs closed = 0 px at 80/100/150% height and with a non-zero bottom offset | NOT_COVERED | — | — | — | — |
| Physical keyboard attached + panel shown: touchableRegion not cleared, panel clickable | NOT_COVERED | — | — | — | — |
| p95 open with snapshot ready ≤ 100 ms; p95 first open in a fresh process ≤ 600 ms | NOT_COVERED | — | — | — | — |
| Cold start < 400 ms, not grown vs the pre-E2 measurement | NOT_COVERED | — | — | — | — |
| 0 allocations in onDraw and ACTION_MOVE over 200 scroll frames; janky frames ≤ 1% on real hardware | NOT_COVERED | — | — | — | — |
| Direct boot: before unlock the panel opens, scrolls and inserts, there is no Recent tab, no crash/ANR; run-as shows no medium before first unlock | NOT_COVERED | — | — | — | — |
| Recents not written in no-suggestion fields and in IME_FLAG_NO_PERSONALIZED_LEARNING fields, in third-party apps | NOT_COVERED | — | — | — | — |
| Landscape at 50% keyboard height: tabs, both functional keys and ≥ 1 full grid row visible | NOT_COVERED | — | — | — | — |
| Clear recent emoji: no WindowLeaked on rotation with the confirm dialog open; Recent tab disappears without input-view recreation | NOT_COVERED | — | — | — | — |

An emulator run, had one been performed, would be a separate row marked `NOT_COVERED` with
"emulator, reference only"; none was performed for E2c.
