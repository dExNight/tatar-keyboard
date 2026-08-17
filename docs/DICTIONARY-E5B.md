# E5b — versioned Tatar bigram (next-word prediction) table asset

Generation date: 2026-08-17. This document is the provenance, attribution,
transformation record, format specification, and acceptance record for the single
shipped bigram-table payload:

`app/src/main/assets/bigrams/tatar_bigrams_v1.tatbigr.zlib`

The licensed archives and `*-sentences.txt` inputs are not stored in the repository.
The uncompressed real table is a temporary validation artifact and is not shipped.

This is the second data asset the project ships, and deliberately a **separate file and
a separate schema** from the tt dictionary (`app/src/main/assets/dictionaries/`,
schema 1, `docs/DICTIONARY-D1A.md`): PROPOSALS.md ("E5b. Отдельный файл и отдельная
схема") rejects extending `.tdict` schema 1, because `TdictPrefixIndex.open` and
`TdictValidator` hard-require `schemaId == 1` and exact schema-1 section arithmetic —
mixing bigrams into that format would force a rebuild and a re-pin of the main
dictionary's `expectedRawSha256`/`expectedEntryCount` on every bigram-table change.

## Configuration decision (E5b, not E5a)

`docs/DICTIONARY-E5A.md` measured all 7 matrix configurations and recommended
**H = 10 000, K = 6** as input to this subphase — the highest measured unconditional
top-3 hit-rate (17.01 п.п.) among configurations that safely clear both caps, with
22 269 Б of headroom under the 250 000 Б compressed cap and 404 314 Б under the
1 048 576 Б raw cap. That recommendation is adopted here as the shipped configuration,
decided and packed 2026-08-17 by the agent executing mission `tatar-e5`, using the same
generator that will pack any future language's table.

## License and attribution

The table is derived from downloadable Tatar text corpora in the [Leipzig Corpora
Collection](https://wortschatz.uni-leipzig.de/en/download/tat), the same collection
`docs/DICTIONARY-D1A.md` and `docs/DICTIONARY-E5A.md` use. Leipzig's [Terms of
Usage](https://wortschatz.uni-leipzig.de/en/usage) license the downloadable corpora
under [Creative Commons Attribution 4.0 International
(CC BY 4.0)](https://creativecommons.org/licenses/by/4.0/).

Recommended citation: D. Goldhahn, T. Eckart, U. Quasthoff, "Building Large
Monolingual Dictionaries at the Leipzig Corpora Collection: From 100 to 200
Languages", LREC 2012.

The resulting table data remains a separate CC BY 4.0 data component. It is not
relicensed under the application's Apache-2.0 code license. The shipped attribution is
also present in `app/src/main/assets/bigrams/NOTICE.txt`.

**Only two of the three Leipzig corpora contribute data to the shipped table.**
`tat_mixed_2015_1M` and `tat_web_2018_1M` are the training corpora the pairs are
counted from; `tat_news_2015_1M` is held out completely (`docs/DICTIONARY-E5A.md`) and
contributed no bytes to this asset — it was used only to measure predictive
usefulness before the table was built.

## Sources

| Corpus | Direct archive URL | Corpus date | Role | Archive SHA-256 | Extracted `*-sentences.txt` SHA-256 |
|---|---|---|---|---|---|
| `tat_mixed_2015_1M` | https://downloads.wortschatz-leipzig.de/corpora/tat_mixed_2015_1M.tar.gz | 2015 | training | `c5a27c731116c2540a1053b8b9d6cb3a16134f519f0bf7535bca274173d01fc7` | `838a10f02ddfeddb1fa7bb01a472b47d1cfdfb2e72c0cd41e1c7ed5a4c1b6cf9` |
| `tat_web_2018_1M` | https://downloads.wortschatz-leipzig.de/corpora/tat_web_2018_1M.tar.gz | 2018 | training | `de7816dbd8334ad9cd516be43ddca76e157316db9a53576dc3e813005d7b3f87` | `24318543c3b036b735a2d59c505083e4a3bad57d64384a8c074bbd30f5e7a066` |

Both archive SHA-256 values were verified byte-for-byte against the pins in
`docs/DICTIONARY-D1A.md` on download (2026-08-17); the extracted `*-sentences.txt`
hashes are pinned identically in `docs/DICTIONARY-E5A.md`, which packing reuses
without re-downloading or re-extracting anything. `tat_news_2015_1M`'s archive and
extracted hashes are recorded there too, for the held-out evaluation only.

## Complete transformation record

Generator: `scripts/bigram_asset_pack.py pack`, Python 3 standard library only, same
zlib settings (level 9, wbits 15, memLevel 9, one `Z_FINISH`) as `dictionary_pack.py`
and `bigram_pack.py`. The generator is a **separate file from `scripts/bigram_pack.py`**
(E5a's measurement prototype, independently reviewed 2026-08-17) — it imports E5a's
already-tested data-layer helpers (`count_pairs`, `select_heads`,
`read_shipped_vocabulary`) rather than duplicating or modifying them.

1. Read the shipped `tatar_top100k_v1.tdict.zlib` through
   `dictionary_pack.decompress_asset` + `dictionary_pack.validate_raw` — the single
   proven source for both the word list and the unigram frequencies (no second source
   is created).
2. Select the top 10 000 words by unigram frequency (ties broken code-point
   ascending) — `select_heads`, identical to E5a.
3. Count adjacent in-vocabulary word pairs within sentence boundaries across
   `tat_mixed_2015_1M-sentences.txt` and `tat_web_2018_1M-sentences.txt`
   (`count_pairs`, sharded 8-way over head hashes, identical to E5a): a token rejected
   by `normalize_word` breaks adjacency rather than becoming transparent, both halves
   of every pair must already be in the shipped top-100k, and a pair whose success
   equals its head is dropped.
4. For each of the 10 000 selected heads, truncate its successes to the top 6 by pair
   count (ties broken code-point ascending) — this is K = 6, the successes-per-head
   configuration.
5. **A head with zero successes after truncation is dropped from the file entirely,
   not stored with an empty range.** Four of the 10 000 requested heads hit this case
   on the real corpora — none had ever occurred as the head of an in-vocabulary pair in
   mixed+web at all: `искәрткәнчә`, `билгеләвенчә`, `үтелгәнчә`, `толмацкий`. The
   shipped table therefore has **9 996 heads, not 10 000** — a fact recorded here
   rather than silently rounded away. This is a generator invariant, not incidental:
   the validator (`bigram_asset_pack.validate_raw`, `TatBigrValidator.validateRaw`)
   rejects an empty success range as corruption, so the generator must never be able to
   produce one.
6. Re-sort the kept heads code-point ascending for storage (binary search needs this;
   set membership was already decided by frequency in step 2, only the on-disk ORDER
   changes here — reordering does not touch which successes belong to which head).
7. Deduplicate all kept successes across all heads into one vocabulary, sorted
   code-point ascending.
8. Serialize the six sections below, compute the header, checksum the full raw file
   with the digest bytes zeroed, then zlib-compress. Both caps (250 000 Б compressed,
   1 048 576 Б raw) are checked before either output file is written; a violation
   raises before any byte reaches disk.
9. The generator self-validates its own output (`pack_bigram_table` calls
   `validate_raw` on the raw bytes it just built) before returning — the same
   validator a corrupted file on-device would fail.

## Uncompressed schema 2/version 1

Magic `TATBIGR\0` (8 bytes), `schemaId = 2`, `formatVersion = 1`. All integers are
unsigned little-endian.

**Header, 96 bytes total, SHA-256 digest at byte offset 64:**

| Offset | Size | Field |
|---:|---:|---|
| 0 | 8 | magic (`TATBIGR\0`) |
| 8 | 2 | schemaId (= 2) |
| 10 | 2 | formatVersion (= 1) |
| 12 | 2 | headerSize (= 96) |
| 14 | 2 | checksumAlgorithm (= 1, SHA-256) |
| 16 | 4 | headCount (H) |
| 20 | 4 | pairCount (P) |
| 24 | 4 | successVocabularyCount (V) |
| 28 | 4 | section 1 offset (head-word offsets) |
| 32 | 4 | section 2 offset (head-word blob) |
| 36 | 4 | section 3 offset (success-range boundaries) |
| 40 | 4 | section 4 offset (success ids) |
| 44 | 4 | section 5 offset (success-word offsets) |
| 48 | 4 | section 6 offset (success-word blob) |
| 52 | 4 | head-word blob length, bytes |
| 56 | 4 | success-word blob length, bytes |
| 60 | 4 | file size, bytes |
| 64 | 32 | SHA-256 of the complete raw file with these 32 bytes zeroed |

The checksum trick is the same one `dictionary_pack.py` (schema 1) uses at byte offset
40 — here at offset 64 because schema 2 carries twice as many header fields (twelve
u32 for six sections' worth of offsets/lengths, vs. schema 1's six u32 for three
sections).

**Six sections, no padding, no separators, immediately following the header:**

1. **Head-word offsets** — `H+1` u32 values indexing into section 2; offset[0] = 0,
   offset[H] = head-word blob length.
2. **Head-word blob** — UTF-8 bytes of the `H` head words concatenated, in code-point
   lexical ascending order (binary search at read time).
3. **Success-range boundaries** — `H+1` u32 values indexing into section 4; for head
   `i` (in the SAME order as section 1/2), its successes are section 4 positions
   `[boundary[i], boundary[i+1])`. Every boundary pair must be strictly increasing —
   an equal pair (empty range) is corruption, not a legitimate zero-success head (see
   transformation record, step 5).
4. **Success ids** — `P` u32 values, indices into the success vocabulary (sections
   5/6), grouped by head in section-1/2 order; within one head's range, successes are
   in PACKING order (count descending, tie code-point ascending) — this is the only
   place ranking-by-frequency survives into the file, there is no separate weight
   byte.
5. **Success-word offsets** — `V+1` u32 values indexing into section 6.
6. **Success-word blob** — UTF-8 bytes of the `V` deduplicated success words, in
   code-point lexical ascending order.

Every offset and length in the header is a deterministic function of `(H, P, V)` under
the no-padding rule; the validator recomputes all six section offsets independently
and rejects the file if the stored values disagree ("noncanonical section
arithmetic"), the same defense-in-depth `TdictValidator` applies to schema 1.

## Resulting artifact and budgets

| Property | Value |
|---|---:|
| Head count (H, actual, after dropping 4 zero-success heads) | 9 996 |
| Pair count (P) | 59 790 |
| Success vocabulary (V) | 8 978 |
| Raw size | 644 148 Б |
| Raw SHA-256 | `fb686476f6252f61f9d26632ccbd228f13aa1bffca7fe9bfff5f24baf9e0b05b` |
| Compressed size (zlib level 9) | 226 428 Б |
| Compressed SHA-256 | `89eb4aa82be45a57ea94daa0379ca3d8a07f1c630e5c532960832787b1e1ab8d` |
| Raw cap | 1 048 576 Б — margin 404 428 Б |
| Compressed cap | 250 000 Б — margin 23 572 Б |
| Generator peak RSS | 136 753 152 Б (≈ 130.4 MiB) |
| Packing time | 164.169 s |
| Shards | 8 |

Both figures are marginally smaller than the E5a matrix's H=10 000/K=6 measurement
(644 262 Б raw / 227 731 Б compressed) — expected, and consistent to the byte: E5a's
formula priced the NOMINAL H=10 000, this file has 9 996 real heads. The difference is
`2×4×4 = 32` bytes from the two now-shorter (H+1)-sized offset arrays plus the UTF-8
length of the four dropped words (`искәрткәнчә` 22 Б + `билгеләвенчә` 24 Б +
`үтелгәнчә` 18 Б + `толмацкий` 18 Б = 82 Б), totalling exactly the observed 114-byte
raw delta.

## Fail-closed validation

`scripts/bigram_asset_pack.py` (Python, generation-time) and
`TatBigrValidator.kt` (Kotlin, runtime) implement the SAME format independently and
reject every corruption class PROPOSALS.md names: wrong magic, `schemaId != 2`, wrong
`formatVersion`, wrong `headerSize`, wrong checksum algorithm, checksum mismatch,
noncanonical section arithmetic (each of the six offsets, both blob lengths, file
size), invalid UTF-8, unsorted or duplicate head/success words, a success id `>= V`,
an empty success range, and trailing/truncated bytes. Both validators additionally
reject empty (zero-length) words, defensively, even though the contract's named list
does not call this out separately — a zero-length word cannot occur from honest
generation and both validators treat it the same way they treat the named classes.
26 Python tests (`tests/bigram_asset_pack/`) and 26 Kotlin tests
(`TatBigrValidatorTest`, `AtomicBigramStoreTest`) exercise this, one assertion per
named class plus round-trip and store-lifecycle coverage; see "Fail-closed acceptance"
below for the exact count split.

## Storage

The table is stored on-device the same way the main dictionary is (temp → fsync →
validate → atomicRename → syncDirectory, `AtomicBigramStore.kt`), reusing the generic
seams `DeviceProtectedDirectoryProvider`, `DurableFileOps`, `StorageClock`,
`SpaceProbe` — but in **its own device-protected subdirectory**, never
`filesDir/dictionaries`, with its own final-file naming
(`tatar_bigrams-<language>-v%06d-s%d-f%d-<sha256>.tatbigr`, `AtomicBigramStore.
FINAL_FILE_PATTERN`) and its own process-wide lease registry
(`ProcessBigramStorageOwner`, separate object from `ProcessDictionaryStorageOwner`).
`AtomicBigramStoreTest.aLiveDictionaryLeaseNeverBlocksOrIsBlockedByABigramLease` wires
one of each store to two different temporary directories and proves neither store's
lease/retention logic ever observes the other's shared state.

The new `bigrams/` device-protected subdirectory needs no new backup rule: both
`res/xml/data_extraction_rules.xml` (API 31+) and `res/xml/backup_rules.xml` (API
24–30) exclude the `device_file` domain WHOLE, with no path-specific `<include>`
anywhere in either file (`docs/DICTIONARY-E2.md`, `BackupWhitelistSourceContractTest`).
A whole-domain exclusion covers every file under every device-protected directory —
including one that did not exist when the rule was written — by construction; no
change to either XML file or to the source-contract test was needed or made. Level 2
(the built-APK check, `scripts/check-no-internet.sh`) was re-run after this asset was
added to `app/src/main/assets/` — see "APK gate" below for the run's output.

The table is parameterized by language from the start (`BigramArtifactSpec.
languageTag`, baked into the final file name) even though only the Tatar (`tt`) table
ships in E5b — a second language is a second `BigramArtifactSpec`, not a second class.

Not part of E5b's scope: no `AtomicBigramStore` instance is wired into the running
IME yet (no `BigramStorageController`/background-preparer equivalent to D1d's), and
nothing reads or maps the table for predictions. That wiring — "готовность
вычислителя", `MappedDictionaryEngine.startOwnedLease`'s two-stage publication — is
E5c's contract, not E5b's ("формат, генератор, asset, хранилище").

## APK gate

Measured 2026-08-17, same Gradle/R8 session for both sides of the A/B pair (release,
unsigned, `./gradlew assembleRelease`):

| Artifact | Size, Б | SHA-256 |
|---|---:|---|
| E5a baseline (`git stash` back to the tip of E5a, rebuilt) | 1 505 419 | — (not committed, measurement only) |
| After E5b (this commit) | 1 719 756 | `909b6b3930fa42ae7de6c1d4455082e16d33f36eb1204c7f07e52088f602596d` |
| **Delta** | **+214 337** | — |

The E5a-baseline figure (1 505 419 Б) is measured on THIS machine/toolchain rather than
reused from the D3 gate's recorded 1 509 443 Б — R8/AGP output is not guaranteed
byte-identical across machines or Gradle daemon restarts, and the two numbers differ
by 4 024 Б (0.27%), consistent with ordinary build nondeterminism rather than an actual
content change (`app/` is untouched by every E5a commit, confirmed in
`docs/MILESTONE-v2.md`). The delta above is apples-to-apples: both sides built in the
same session, seconds apart, via `git stash`/`git stash pop` around the E5b changes,
so R8/resource-shrinker nondeterminism cancels out between the two sides rather than
contaminating the comparison.

Breakdown of the +214 337 Б, from `unzip -v`:

| Entry | In-APK compressed size, Б |
|---|---:|
| `assets/bigrams/tatar_bigrams_v1.tatbigr.zlib` (211 079) + `assets/bigrams/NOTICE.txt` (858) | 211 937 |
| `classes.dex` growth (176 430 − 174 319, new storage classes: `BigramStorageContracts`, `TatBigrValidator`, `AtomicBigramStore`) | 2 111 |
| ZIP structural overhead, two new entries | ≈ 289 |

**This is a ONE-language delta, not the two-language worst case the early gate (E5a,
`docs/DICTIONARY-E5A.md`) priced defensively at 2 × 250 000 Б** — only the Tatar table
ships in E5b; a second language's table would add a comparable amount again. Against
the hard limit: `1 719 756 / 3 145 728` = 54.7%, **1 425 972 Б free (45.3%)** — no
margin concern at this size, regardless of how the projected registry chain in
`PROPOSALS.md` ("Бюджет размера APK") plays out once D2 (Russian dictionary, out of
this mission's scope) and E4 ship; today's actual measured baseline is smaller than
that chain's own E4 checkpoint specifically because D2 has not shipped yet, and that is
expected, not a discrepancy to chase down.

`scripts/check-no-internet.sh` re-run against this exact APK, both levels:

```
Level 1 OK: no INTERNET in source manifest
Level 2 OK: no INTERNET in built APK
Backup: raw manifest line -> A: http://schemas.android.com/apk/res/android:allowBackup(0x01010280)=false
Backup: manifest references dataExtractionRules=@0x7f110002 fullBackupContent=@0x7f110001
Level 2 OK: backup closed as a whitelist (allowBackup=false, both editions, no <include>, all domains excluded)
```

## Fail-closed acceptance

- [x] Valid asset in `app/src/main/assets/bigrams/`; SHA-256 pinned in
  `BigramArtifactSpec.TATAR_BIGRAMS_V1` and verified against the committed file
  (`TatBigrValidatorTest.acceptsCommittedTatarBigramAssetWithFrozenProvenance`).
- [x] Validator rejects every corruption class named by PROPOSALS.md, each its own
  test: wrong magic, `schemaId`, `formatVersion`, `headerSize`, checksum algorithm,
  checksum mismatch, seven distinct noncanonical-arithmetic mutations, zero/overflowing
  head count, empty success range, success id ≥ V, invalid UTF-8, unsorted heads,
  duplicate heads, trailing bytes, truncation — 19 tests in `TatBigrValidatorTest`,
  plus the zlib-layer classes (malformed/truncated/trailing/concatenated/preset-dictionary,
  both caps) mirrored from `TdictValidatorTest`. 26 additional Python tests
  (`tests/bigram_asset_pack/`) exercise the same classes generator-side, on an
  independently hand-built fixture (`_manual_raw`), not the generator's own output.
- [x] Both caps enforced by the generator itself before either output file is written
  (`GeneratorGuardrailTest`), and by the runtime validator on inflate
  (`rejectsCompressedAndRawCaps`).
- [x] Own device-protected subdirectory, own final-file regex, own retention limit
  (`AtomicBigramStore`, `FINAL_FILE_PATTERN`, `MAX_FINAL_ARTIFACTS`), own process-wide
  lease registry (`ProcessBigramStorageOwner`) — proven independent of the dictionary
  store's registry by `AtomicBigramStoreTest.
  aLiveDictionaryLeaseNeverBlocksOrIsBlockedByABigramLease`, which runs both stores
  concurrently against separate directories and separate live leases.
- [x] Backup: whole-domain exclusion already covers the new subdirectory, confirmed
  unchanged, re-verified at level 2 on the built APK above.
- [x] `NOTICE.txt` and this document carry source, corpora used and NOT used, license,
  citation, and the complete transformation record; archives and extracted
  `*-sentences.txt` are absent from the repository (`.gitignore`).
- [x] `655` JVM tests total (629 before this phase + 26 new), 0 failures/errors;
  `lintVitalRelease` green; `assembleDebug`/`assembleRelease` both succeed.
- [x] Android code outside `dictionary/storage/` is untouched — this phase adds three
  new files to that one package plus their tests, and touches no existing file.

## Independent review (2026-08-17)

Fresh agent, no context from the work above. **Verdict: PASS with one noted design
asymmetry, not a finding.** Re-ran the real packing on the pinned corpora — every
number (`raw_bytes`, `compressed_bytes`, both SHA-256, `actual_head_count`,
`pair_count`, `success_vocabulary_count`, all four dropped heads) matched byte-for-byte.
Read `validate_raw` (Python) and `validateRaw` (Kotlin) line by line and confirmed both
reject the same corruption classes. Ran both test suites (26 Python + 655 JVM, 0
failures), rebuilt the release APK independently and re-ran `check-no-internet.sh`
(both levels OK, same size). Confirmed `git log` on this branch carries only the two
E5b-scoped commits and that the unrelated pending review-queue files were never pulled
in.

**Noted asymmetry (not a finding):** `bigram_asset_pack.validate_raw` is a pure format
validator and accepts a syntactically well-formed `headCount = 0` file (an empty
table) as structurally valid; `TatBigrValidator.validateRaw` always rejects
`headCount = 0` — not via a dedicated structural check, but because it is spec-bound
and `BigramArtifactSpec.expectedHeadCount` is constructor-required to be `> 0`. This is
not a contract violation (PROPOSALS.md does not name a zero head count among the
corruption classes E5b's validator must reject, and this document only ever claimed
symmetry for empty-*word* rejection, not for empty-*table* rejection) and not a real
gap (the runtime validator is always spec-bound to the one pinned artifact, so a
zero-head file could never reach it as anything but a SHA-256/size mismatch either
way). Recorded here for completeness rather than silently left out of the record.
