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
