# E5c — next-word prediction engine

Generation date: 2026-08-17. This document is the design record and fail-closed
acceptance record for E5c (PROPOSALS.md, "## E5", subsection "E5c" of scope/deliverable
and of Fail-closed acceptance). E5c adds the READ side of biграм prediction to the
already-shipped prefix-suggestion engine: a new query kind, a new zero-allocation
reader for the E5b `TATBIGR` table, and the two-stage readiness that lets the bigram
table become available after the dictionary engine has already published, without ever
delaying that publication.

**E5c does not make any user-visible feature reachable.** No caller anywhere
constructs a `NEXT_WORD` request from real typing yet — that requires the context
extraction and commit path PROPOSALS.md assigns to E5d
(`TatarWordUtils` trailing-word extraction, `InputLogic.commitPredictedWord`, the
"Контракт текста" amendment). E5c's job, and this document's scope, is the mechanism:
correct, tested, and already wired into the real `SuggestionsController` publication
path, ready for E5d to call.

## What was built

- **`LookupKind` (PREFIX / NEXT_WORD)** on both `LookupToken` and `LookupResult`
  (`LatestOnlyPrefixEngine.kt`). `LookupToken.exactPrefix` renamed to
  `normalizedQuery` — the same field carries prefix bytes for PREFIX and normalized
  context-word bytes for NEXT_WORD, and the old name was misleading for the second
  case.
- **`LatestOnlyPrefixEngine.requestNextWord`**, a sibling of the existing `request`.
  Both funnel through one `requestInternal(kind, maxBytes)`; the PREFIX path's
  validation, rejection and generation-invalidation behaviour is byte-for-byte
  unchanged (proved by the full pre-existing suite staying green throughout). `drain()`
  dispatches by `token.kind`: `PrefixComputer.lookup` for PREFIX,
  `(computer as? NextWordComputer)?.predict` for NEXT_WORD. One engine, one token
  type, one executor, one `currentToken`/`pendingRequest` pair — PREFIX and NEXT_WORD
  share the exact same single-flight/coalescing state machine.
- **`NextWordComputer`** — a new, separate interface from `PrefixComputer` (its
  `lookup` signature stays frozen), for the same reason `ClassifiedPrefixComputer` and
  `KeyNeighborSink` are already separate interfaces rather than widening it.
- **`TatBigrPrefixIndex`** (`dictionary/engine/`) — the schema-2 reader. One
  allocation-free binary search for an EXACT head match (no prefix range — there is no
  such thing as a partial next-word context), then up to `min(3, that head's success
  count)` u32 ids decoded into result strings, in the packing order E5b's generator
  already fixed. `open()` re-validates every structural invariant the reads depend on
  (bounds, monotonicity, no empty ranges, ids `< V`) — the same belt-and-suspenders
  posture `TdictPrefixIndex.open` already has, not a rerun of `TatBigrValidator`.
- **`CompositePrefixComputer.attachBigramSource`** — the composite now also implements
  `NextWordComputer`, backed by a `@Volatile` bigram-source reference that starts
  `null` and is set exactly once, later, from a different thread than the one that
  reads it (`predict`) — the same cross-thread handoff shape `lastAutocorrectAdvice`
  already uses, in the opposite direction. `predict` never touches the primary
  dictionary computer or the personal-word source — E5 has no personal bigrams and no
  fuzzy pass for NEXT_WORD.
- **`MappedDictionaryEngine.attachBigramSource(catalog)`** — acquires, maps, opens and
  wires a bigram source into an ALREADY-published engine. `Resources` was extended to
  own the bigram lease/mapping/index alongside the dictionary's, releasing both under
  one lock in `release()`; `attachBigram`/`release` share that lock so a bigram attach
  racing a `destroy()` resolves cleanly — whichever loses closes the lease it just
  acquired instead of leaking it or double-closing.
- **Production storage wiring** — `BigramStorageController`/`BackgroundBigramPreparer`
  (mirroring the dictionary pair; not generalized into one implementation, because the
  two artifacts already don't share a spec/validator/store as of E5b) and
  `AndroidBigramStorageFactory` (own device-protected `bigrams/` subdirectory, the same
  production `AndroidDurableFileOps`).
- **`SuggestionsController` integration** — `maybeAttachBigramSource(handle)`, called
  from `publishEngine` immediately after `engine = handle`, strictly before
  `strip.reserve()`/`requestCurrentPrefix()`. The call itself is cheap (it only
  schedules background work); the actual mmap/validate I/O happens inside
  `BigramPreparation.prepare`'s callback, which runs on the background executor and
  never touches UI-visible state — there is nothing for the strip to reflect when
  attach completes, because nothing calls `requestNextWord` yet (E5d).

**Why every engine (re)start fully re-validates the bigram table, not just the
dictionary's.** PROPOSALS.md ("E5c. Готовность вычислителя двухступенчатая") says the
E1d two-layer validation cache "распространяется и на артефакт биграмм" — reads as a
requirement to extend it here. It is not extended, because **E1d does not exist yet**:
`docs/MILESTONE-v2.md` still carries it `[ ]`, conditional on a real-device S2/S3
measurement (`docs/DICTIONARY-E1.md`) that has not been run, and
`AtomicDictionaryStore.ensurePublishedLocked`/`acquireLatestForActivationLocked` call
`TdictValidator.validateRaw` fresh every time today, for the MAIN dictionary too — there
is no cache to extend for either artifact. The contract sentence is a forward
contingency ("if/when E1d exists, it must cover bigrams too"), not a mandate for E5c to
build a cache the dictionary path itself does not have; doing that would leave the two
artifacts inconsistent with each other for no benefit measured yet. `attachBigramSource`
therefore costs the same as `MappedDictionaryEngine.start` does today — full structural
validation each time — which is the CURRENT baseline, not a regression against it. If a
future device measurement makes E1d mandatory, extending it to bigrams becomes a
concrete, scoped follow-up with a real budget to satisfy, not a retrofit onto E5c.
## Fail-closed acceptance

- [x] **A result of one kind is never applied to a request of the other, and the
  owner reads the kind off the result rather than its content.** Proved at the engine
  level: `requestNextWordInvokesNextWordComputerNotPrefixComputerLookup` and
  `burstOfMixedPrefixAndNextWordKeepsSingleFlightAndOnePendingSlot`
  (`LatestOnlyPrefixEngineTest.kt`) show `LookupResult.kind` always matches the
  request that produced it and that `drain()` never calls the wrong computer method.
  **Partial, by design:** the literal "владелец состояния выбирает путь коммита"
  requires TWO commit implementations to choose between, and the second
  (`InputLogic.commitPredictedWord`) is E5d's deliverable, not E5c's — nothing commits
  a NEXT_WORD result yet because nothing requests one from real typing yet. What E5c
  guarantees is that the signal E5d will switch on (`result.kind`) is reliable and
  never crossed.
- [x] **Burst test:** `burstOfMixedPrefixAndNextWordKeepsSingleFlightAndOnePendingSlot`
  — any interleaving of `request`/`requestNextWord` still yields at most one running
  and one pending request; the coalescing invariant does not depend on kind. 0 stale
  results are ever applied (`suppressedStaleResultCount` accounts for every dropped
  one explicitly, matching the existing PREFIX-only burst test's shape).
- [x] **Compute p95 ≤ 5 ms, request→publish warm p95 ≤ 16 ms**
  (`RealBigramPrefixIndexTest.kt`, against the real committed asset): measured
  0.001 ms and 0.008 ms respectively — three orders of magnitude inside budget.
- [x] **Corrupted/missing/inactive bigram file → 0 predictions, no effect on prefix
  suggestions or ordinary input.** Covered at three layers:
  `predictReturnsEmptyBeforeABigramSourceIsAttached` /
  `predictNeverTouchesThePrimaryOrPersonalSources` (composite),
  `attachBigramSourceFailsClosedOnCorruptTableAndLeavesPrefixUnaffected` (engine),
  `unavailableBigramPreparationLeavesDictionarySuggestionsUnaffected` /
  `missingBigramPreparationNeverBlocksOrDelaysPublish` /
  `bigramPreparationFactoryThrowingNeverPropagatesToPublish` (controller).
- [~] **Lifecycle: FD does not grow across repeated start/destroy, ≤ current + one
  staged/old table on disk, mapping never swapped on a live session.**
  `destroyClosesBothDictionaryAndBigramLeasesExactlyOnce` and
  `attachAfterDestroyClosesTheAcquiredBigramLeaseWithoutPublishingIt`
  (`MappedDictionaryEngineTest.kt`) prove exactly-once release and no leak on the
  attach/destroy race, using the same `DictionaryMapper`/lease-close discipline the
  dictionary path's own 100-cycle FD stress test
  (`repeatedReadOnlyMmapLifecycleDoesNotRetainFileDescriptorsOrLeases`) already
  exercises. **NOT_COVERED specifically for bigrams:** a dedicated 100-cycle FD-growth
  stress test repeating bigram attach itself was not written — the release mechanism
  it would exercise is identical to, and already stress-tested by, the dictionary
  path's version, and the on-disk retention bound (≤ 2 finals) was already proved in
  E5b (`AtomicBigramStoreTest.retentionNeverDeletesLeasedFileAndNeverExceedsTwoFinals`).
  If a future phase finds this gap load-bearing, extending the existing dictionary
  loop to also cycle a bigram attach is a small addition, not a redesign.
- [x] **`DictionaryEnginePrivacyTest` covers the new engine code.** The whole-directory
  forbidden-token scan already covers every new/changed file in this phase
  (`TatBigrPrefixIndex.kt`, the edits to `CompositePrefixComputer.kt`,
  `MappedDictionaryEngine.kt`, `LatestOnlyPrefixEngine.kt`) because they all live in
  `dictionary/engine/`, the directory the scan already walks recursively.
  `TatBigrPrefixIndex` was additionally added to the reflection-based
  no-log/no-commit/no-delete method-name check, alongside `TdictPrefixIndex`,
  `LatestOnlyPrefixEngine`, `MappedDictionaryEngine` — the direct schema-2 analogue of
  the schema-1 reader already on that list.

## Regression discipline

Every commit in this subphase was preceded by a full `./gradlew testDebugUnitTest`
run; nothing was committed on a red build. Test count progression from the E5b close:
655 → **662** (`TatBigrPrefixIndex`, +7) → **666** (`LookupKind`/`requestNextWord`, +4)
→ **669** (`CompositePrefixComputer.attachBigramSource`, +7) → **674**
(`MappedDictionaryEngine.attachBigramSource`, +5) → **674**
(`EngineHandle`/`BigramStorageController`/`AndroidBigramStorageFactory` wiring, +0 new
tests, one existing closed-enumeration test updated) → **678**
(`SuggestionsController` two-stage wiring, +4) → **680** (real-asset p95, +2) —
**680 tests total, 0 failures/errors** as of the last commit. One self-caught mistake
along the way is worth recording rather than hiding: an early edit to
`CompositePrefixComputer.kt` accidentally deleted the pre-existing
`clearAutocorrectAdvice()` method; it was caught by re-reading the diff before the
first compile, not by a red build, and restored in the same edit pass.

## APK and privacy gates

Release APK after E5c: **1 725 760 Б** (before: 1 719 756 Б after E5b — delta **+6 004 Б**,
code only, no new assets). `1 725 760 / 3 145 728` = 54.9%, 1 419 968 Б free (45.1%).
`scripts/check-no-internet.sh` re-run on this build: both levels OK. `lintVitalRelease`,
`assembleDebug`, `assembleRelease` all succeed.

`AndroidBigramStorageFactory.kt` — added in E5c, not E5b, as part of wiring
`SuggestionsController` to the real bigram store — introduces a third
`createDeviceProtectedStorageContext()` seam alongside the two the project already had.
`EmojiRecentAndFlingSourceContractTest`'s closed enumeration was updated in the same
commit to name and expect all three.

## Not part of E5c's scope

No `.tt`/`.ru` context is read from real user text, no commit path exists for a
NEXT_WORD result, and no setting or one-shot dialog mentions predictions — all of that
is E5d's contract ("Контракт текста" amendment, `TatarWordUtils` context extraction,
`InputLogic.commitPredictedWord`, texts, device-UAT). E5c's device-UAT is therefore
also not meaningful yet: there is nothing on a real device to observe that a JVM test
does not already cover, because nothing in the shipped app calls `requestNextWord` outside
of tests until E5d exists.
