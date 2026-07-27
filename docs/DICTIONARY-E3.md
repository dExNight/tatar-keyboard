# E3a — reproducible typo set and recovery@3 calibration

Evaluation date: 2026-07-27. This document is the deliverable record for the open E3a
sub-phase item: a **reproducible edit-class-#1 typo set** and the **recovery@3 calibration**
against the offline reference declared in `PROPOSALS.md`
(«E3a — калибровка порогов», recovery@3 **14,2%** for class #1). The E3a engine code is
already committed (HEAD `e3f9b1e`); nothing in its logic was touched here. This closeout
adds only an offline generator (`scripts/typo_pack.py`), its tests, a JVM calibration
test, and these docs.

The class #1 edit is the contract's «замена буквы на её long-press партнёра в любой
позиции».

---

## 1. Adjacency map (long-press pairs, as built in E3a)

The pairs are read from the keyboard layout resources — the `latin:moreKeys` attributes
of `res/xml/rowkeys_tatar1.xml`, `rowkeys_tatar2.xml`, `rowkeys_tatar3.xml`,
`rowkeys_tatar_extra.xml` — and symmetrized and de-duplicated exactly as
`KeyNeighborTable.build` does on the device. No pair is hard-coded: the offline
`scripts/typo_pack.py` reads the same XML, and the JVM test uses the E3a
`E3aTestFixtures.tatarNeighborTable()` that mirrors it (proven equal by
`KeyNeighborTableTest`). The layout stores each edge one-directionally and duplicated
(«ә» is declared on both «а» and «э»; «һ» on both «г» and «х»), so the reverse edge is
added explicitly and each node's partners are sorted by code point.

Ten undirected pairs (39 Tatar letters, six of them the fifth-row ә ө ү җ ң һ):

| # | pair | note |
|---:|---|---|
| 1 | а ↔ ә | U+0430 ↔ U+04D9 |
| 2 | о ↔ ө | U+043E ↔ U+04E9 |
| 3 | у ↔ ү | U+0443 ↔ U+04AF |
| 4 | ж ↔ җ | U+0436 ↔ U+0497 |
| 5 | н ↔ ң | U+043D ↔ U+04A3 |
| 6 | г ↔ һ | U+0433 ↔ U+04BB |
| 7 | х ↔ һ | U+0445 ↔ U+04BB (һ therefore has two bases: г, х) |
| 8 | е ↔ ё | U+0435 ↔ U+0451 |
| 9 | ь ↔ ъ | U+044C ↔ U+044A |
| 10 | э ↔ ә | U+044D ↔ U+04D9 (ә therefore has two bases: а, э) |

Symmetrized fan-out (partners per node, sorted): а→[ә]; ә→[а, э]; э→[ә]; о→[ө]; ө→[о];
у→[ү]; ү→[у]; ж→[җ]; җ→[ж]; н→[ң]; ң→[н]; г→[һ]; х→[һ]; һ→[г, х]; е→[ё]; ё→[е]; ь→[ъ]; ъ→[ь].

## 2. Thresholds and limits

These are the E3a engine constants (`TdictPrefixIndex`, unchanged here) plus the two
calibration knobs.

| Constant | Value | Where | Kind |
|---|---:|---|---|
| `MIN_FUZZY_PREFIX_CODE_POINTS` | 3 | `TdictPrefixIndex` | engine gate: the fuzzy pass runs only at ≥ 3 code points |
| `MAX_RESULTS` | 3 | `TdictPrefixIndex` | strip cells; fuzzy fills only cells left empty by the exact pass |
| `MAX_FUZZY_VARIANTS` | 24 | `TdictPrefixIndex` | fail-closed budget on variants per lookup |
| `MAX_FUZZY_VISITED` | 8192 | `TdictPrefixIndex` | fail-closed budget on visited dictionary entries |
| geometry overlap threshold | 35% | E3a `KeyNeighborTable` doc | **chosen constant, not a measured value** (recorded per contract; unused by class #1) |
| `PREFIX_CODE_POINTS` (calibration) | 3 | `scripts/typo_pack.py` / calibration test | typo-prefix window; see §4 |
| `TYPO_SEED` (calibration) | 20260727 | `scripts/typo_pack.py` / calibration test | fixed selection seed |

Measured variant counts over the typo set (§4), mirroring
`FuzzyPrefixVariants.generateLongPressVariants` (one variant per partner of every
position): **p50 = 2, p95 = 3, max = 6**. The contract's class #1 offline reference is
«p95 3 варианта, максимум 5»: the p95 matches exactly; the observed maximum is 6 (one
three-code-point prefix carries three multi-partner letters, e.g. an ә/ә/һ shape), one
above the reference — reported, not adjusted.

## 3. The reproducible typo set

Generator: `scripts/typo_pack.py` (Python standard library only, fail-closed, patterned
on `scripts/emoji_pack.py`). It has two committed inputs and writes one output that is
**not** committed:

- **Layout** (`res/xml/rowkeys_tatar*.xml`) → the adjacency map of §1.
- **Dictionary** (`app/src/main/assets/dictionaries/tatar_top100k_v1.tdict.zlib`), pinned
  by SHA-256 on both the compressed asset
  (`2d98ed35…7485cae`) and the inflated raw file (`798d3257…548f558`) and by its
  100 000-entry count — the same triple pin as
  `DictionaryArtifactSpec.TATAR_TOP100K_V1`.
- **Output**: one `original<TAB>typo_prefix` row per eligible word.

**Why nothing is committed.** The licensed source corpus (Leipzig, see
`docs/DICTIONARY-D1A.md`) was never in the repository, and the typo set itself is derived
data: it is fully reproducible from this generator and the two committed inputs, so
committing it would only duplicate the dictionary and risk drift — the same reasoning
that keeps the adjacency out of a separate XML in E3a.

**Selection (deterministic, language-portable).** For every dictionary word of at least
`PREFIX_CODE_POINTS` (= 3) code points whose first-three-code-point window holds at least
one letter with a long-press partner, exactly one `(position, partner)` pair is chosen and
applied inside that window. The choice is a pure function of `(TYPO_SEED, word)`:
`index = SplitMix64(TYPO_SEED xor FNV1a64(word_utf8)) mod eligible_count`, over the
eligible pairs listed in `(position, partner)` order. Both FNV-1a-64 and SplitMix64 are
implemented identically in Python and Kotlin (golden vectors asserted on both sides), so
the offline generator and the JVM test produce **the same** set.

**Why a 3-code-point prefix window.** Three code points is exactly the engine's
`MIN_FUZZY_PREFIX_CODE_POINTS` — the shortest prefix at which the fuzzy pass fires. It is
the conservative worst case (shortest context → largest candidate block → lowest
recovery) and it reproduces the class #1 variant signature (p95 3, §2), which a
full-word model would not.

**Recorded set identity** (from `python3 scripts/typo_pack.py build …`, two runs
byte-identical):

| Property | Value |
|---|---|
| seed | 20260727 |
| prefix window | 3 code points |
| words scanned (≥ 3 code points) | 99 659 |
| eligible words (set size) | **87 375** |
| output bytes | 2 230 638 |
| output SHA-256 | `6a61b48db87ac0bbff78af48ea597b3af19f81dd42ae8deaa2d4c00a6c81dfc3` |

The JVM test independently enumerates the same committed asset, rebuilds the set with the
same rule, and asserts the same size and the same SHA-256 — the cross-implementation
proof that both produce the identical reproducible set.

## 4. Measured recovery@3 (real dictionary)

Test: `app/src/test/java/rkr/simplekeyboard/inputmethod/latin/dictionary/engine/E3aRecoveryCalibrationTest.kt`.
It inflates the committed `tatar_top100k_v1.tdict.zlib` through
`TdictValidator().inflateAsset` (the same harness as `RealDictionaryPrefixIndexTest`),
opens `TdictPrefixIndex`, and for each `(word, typo_prefix)` row looks up the typo prefix
and counts the cases where the correct word is among the top three results. It is **not**
skipped and prints a raw line.

Raw line (from `app/build/test-results/testDebugUnitTest`):

```
E3a recovery@3 class#1 seed=20260727 prefix_cp=3 set=87375 recovered=6364 recovery@3=7.2835% baseline_exact_only=0.0000% fuzzy_fired=49155 recovery@3_when_fuzzy_fired=12.9468% variant_p50=2 variant_p95=3 variant_max=6 contract=14.2% delta=-6.9165pp within_1.0pp=false set_sha256=6a61b48db87ac0bbff78af48ea597b3af19f81dd42ae8deaa2d4c00a6c81dfc3
```

| Metric | Value | Definition |
|---|---:|---|
| recovery@3 (whole set) | **7.2835 %** (6364 / 87375) | «доля случаев» over every row of the set — the contract's literal methodology |
| baseline (exact only) | **0.0000 %** | a typo prefix carries the wrong letter, so the exact pass alone never surfaces the word |
| fuzzy actually fired | 49 155 / 87 375 (56.3 %) | rows where the exact pass returned < 3 continuations, so the cell-fill rule ran the fuzzy level |
| recovery@3 where fuzzy fired | **12.9468 %** (6364 / 49155) | recovery restricted to the fuzzy-eligible subset |

## 5. Сверка with the declared 14,2 %

**Verdict: the whole-set recovery@3 (7.2835 %) diverges from the contract's 14,2 % by
−6.9165 pp** — outside any reasonable tolerance (§6). It was **not** tuned: neither code,
test, generator, nor tolerance was adjusted to move it.

The divergence has a concrete, non-code cause and is not a defect of the E3a
implementation:

- The baseline is 0 % and the fuzzy pass strictly increases recovery, so the mechanism
  works as designed.
- 43.7 % of the typo prefixes (38 220 / 87 375) already have **three or more exact
  continuations of the mistyped prefix**. The contract's own cell-fill rule
  («неточные занимают только ячейки, пустые в D1») then leaves the fuzzy level switched
  off, so those rows can never recover — they drag the whole-set rate down.
- Restricting to the 49 155 prefixes where the fuzzy pass actually runs gives
  **12.9468 %**, only ~1.27 pp below the declared 14,2 %.

The most likely reconciliation is that the offline model that produced 14,2 % measured
recovery over the fuzzy-eligible subset (or used a marginally different word set / gate),
whereas this test reports the literal whole-set «доля случаев». Two defensible readings
therefore exist:

1. whole-set denominator → **7.28 %** (reported here as the primary number), or
2. fuzzy-eligible denominator → **12.95 %** (within ~1.3 pp of 14,2 %).

Per contract this is exactly the case the calibration exists to catch: «иначе корректная
реализация может провалить gate из-за ошибки офлайн-модели, а не кода». The decision on
which denominator is canonical — and hence whether the E3b acceptance threshold is
declared active — is left to the orchestrator. The JVM test consequently prints and
reports the number but does **not** assert equality with 14,2 %; it asserts only
denominator-independent invariants (set size, fraction range, fuzzy > baseline) so the
build stays green while the finding is surfaced.

## 6. Chosen tolerance and its justification

The contract calls the tolerance «согласованный» without giving a number. **Chosen
tolerance: ±1.0 percentage point (absolute)** around the declared 14,2 %, i.e.
recovery@3 ∈ [13.2 %, 15.2 %].

Justification, independent of the outcome:

- The feature's lift over the exact baseline is large (contract 14,2 % vs baseline 1,8 %,
  ~12.4 pp; measured baseline here 0 %). A ±1.0 pp band is ~7 % of the target and ~8 % of
  the lift — tight enough that a genuine reproduction of the offline model would pass and
  a real regression would fail, yet loose enough to absorb second-order noise
  (SplitMix64 selection, frequency ties broken by code point, and the exact-vs-fuzzy slot
  interaction).
- An absolute pp band, not a relative %, is used because recovery@3 is itself a
  percentage and the E3b gate reasons in percentage points.

Applied: the whole-set 7.28 % is **outside** this band; the fuzzy-eligible 12.95 % is also
just outside ±1.0 pp but inside ±1.5 pp. Widening the band to force a pass would be
tuning the tolerance to the result, which the contract forbids, so the band stays at
±1.0 pp and the mismatch is reported.

## 7. Tests on input / output

| | JVM (`:app:testDebugUnitTest`) | Python (`tests/typo_pack`) |
|---|---:|---:|
| before this deliverable | 413 | 0 |
| added here | 4 | 27 |
| after | **417** | **27** |
| failures / errors | 0 | 0 |
| skipped | **0** | **0** |

The four JVM tests (`E3aRecoveryCalibrationTest`): portable-primitive golden vectors;
set byte-identity with the generator; recovery@3; and the fuzzy delta on the 22 everyday
prefixes (§ TSV). None is `@Ignore`d and none is skipped — verified by summing the
`<testsuite>` `tests`/`skipped` attributes across
`app/build/test-results/testDebugUnitTest/*.xml` (417 / 0). The 27 Python tests run under
`python3 -m unittest`; the committed-asset smoke test runs (not skipped) because the asset
is present.

## 8. APK delta

Measured on this worktree with `./gradlew :app:assembleRelease --offline`
(`app/build/outputs/apk/release/app-release.apk`). This deliverable adds no `app/src/main`
source and no asset, so the release APK reflects only the already-committed E3a code
(HEAD `e3f9b1e`); the scripts, tests and docs added here ship nothing.

| Artifact | Bytes |
|---|---:|
| post-E2 baseline (given) | 1 478 015 |
| post-E3a release APK (measured) | 1 480 299 |
| **delta** | **+2 284** |

The +2 284 B is entirely the E3a engine code (`KeyNeighborTable`, `FuzzyPrefixVariants`,
the fuzzy pass in `TdictPrefixIndex`, and the `LatinIME`/`SuggestionsController`/`EngineHandle`
wiring). It is far below the 3 MiB dictionary-feature gate recorded in
`docs/DICTIONARY-D1A.md`.

## 9. Device-UAT matrix

No device is connected, and PSS measurement is deferred by the owner's decision (contract
amendment 2026-07-27). Every row is `NOT_COVERED`; **none is PASSED**, and no PSS number is
invented. Columns follow the cross-cutting "Доступ к реальному устройству" requirement.

| Item | Status | Device (model, serial) | Build (vName/vCode, APK SHA-256) | Date | Raw numbers / path |
|---|---|---|---|---|---|
| recovery@3 on real device typing (≥ 50 hand-typed typos from the reproducible set) | NOT_COVERED — device not connected | — | — | — | — |
| Fuzzy suggestions visibly indistinguishable from exact ones (no colour/icon/suffix, no separate a11y node) | NOT_COVERED | — | — | — | — |
| Fuzzy pass never shifts or replaces an exact candidate, live | NOT_COVERED | — | — | — | — |
| Compute p95 on device on a 100%-fuzzy set ≤ 5 ms (E3b input-gate precondition) | NOT_COVERED — measured only on host JVM; not an Android device | — | — | — | — |
| PSS delta with the fuzzy level on vs off | NOT_COVERED — PSS deferred by owner (amendment 2026-07-27) | — | — | — | — |
| Absolute PSS within the (uncomputed) ceiling | NOT_COVERED — ceiling not recomputed; PSS deferred | — | — | — | — |
| Fuzzy level respects `PREF_TATAR_SUGGESTIONS` and password/no-suggestion fields on device | NOT_COVERED | — | — | — | — |
| No crash/ANR when the layout (and thus the neighbor table) changes live (rotation, theme, subtype) | NOT_COVERED | — | — | — | — |

An emulator run, had one been performed, would be a separate row marked `NOT_COVERED`
with "emulator, reference only"; none was performed for E3a.

## 10. Regeneration and cross-references

```
python3 scripts/typo_pack.py build \
  --dictionary app/src/main/assets/dictionaries/tatar_top100k_v1.tdict.zlib \
  --layout-dir app/src/main/res/xml \
  --output /tmp/e3a_typo_set.txt        # not committed; SHA-256 must equal §3
python3 -m unittest tests.typo_pack.test_typo_pack
./gradlew :app:testDebugUnitTest :app:lintVitalRelease
```

- Fuzzy-candidate review queue for the 22 everyday prefixes:
  `docs/DICTIONARY-E3-TYPO-REVIEW.tsv` (all `pending`; machine classification does not
  replace a human reviewer).
- One summary reference row added to `docs/TATAR-REVIEW-QUEUE.tsv`.
