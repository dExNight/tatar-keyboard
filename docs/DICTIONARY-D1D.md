# D1d — bounded mmap prefix engine evidence

Automated verification date: 2026-07-23. Candidate base: D1c commit `f47000a`.
This is the isolated engine phase; it does not add D1e settings, editor eligibility, UI
integration, or suggestion application.

## Engine and lifecycle contract

- `TdictPrefixIndex` keeps a read-only view of the already validated schema-1 mapping. Binary
  prefix bounds operate on UTF-8 bytes; ranking is frequency descending then Unicode code-point
  lexical ascending. The exact typed word is excluded and at most three words are decoded.
- Empty, larger-than-128-byte, truncated, overlong, surrogate, and otherwise invalid scalar
  UTF-8 prefixes fail closed. `LatestOnlyPrefixEngine` rejects them before creating a token or
  copying bytes, invalidates running/pending generations, and makes exactly one owned copy for a
  valid request. The internal computer boundary receives only an immutable prefix value: it has
  no mutable byte-array getter, so a computer may safely retain but cannot mutate request bytes.
- The executor has one worker and one replaceable latest-pending slot. Taking that slot and
  transitioning the worker to idle happen in one critical section, so a request cannot be
  stranded in the former pending-to-idle race window. Concurrent burst tests use the real
  single-thread executor and observe one reader, one active computer, and only first/latest
  computations.
- A token contains a process-unique engine instance epoch, request serial, editor session,
  subtype, immutable exact prefix, and the complete dictionary identity (generation, schema,
  format, and raw SHA-256). Every worker handoff checks the complete token. Suppressed stale
  computations are counted; no constant/fabricated stale counter is exposed.
- `ResultHandoff` is explicitly non-applying and runs outside the engine state monitor. It must
  dispatch to the serialized state owner (the UI thread in D1e), which calls `isCurrent(token)`
  again immediately before applying UI state. A deterministic queued-owner regression hands off
  A, starts B, then drains A and proves guarded final applies for stale A remain zero.
- `finishInput` invalidates the generation and clears pending work. `destroy` invalidates, stops
  the executor, waits for readers, clears strong mapping/index references, and then releases the
  catalog lease exactly once. Timeout is retryable, interruption is preserved, and concurrent or
  repeated destroy calls cannot duplicate release. Computer, handoff, executor, mapper, index,
  cleanup, and release `Throwable`s fail closed without stranding lifecycle state.
- `MappedDictionaryEngine.start` acquires and consumes the catalog lease itself; its public API
  does not accept a caller-held lease that could be closed prematurely. Mapping and catalog
  acquisition are explicitly off-UI-thread operations. `FileChannel` closes immediately after a
  `READ_ONLY` mmap. There is no live mapping replacement API; a staged version activates only
  after the old engine is destroyed and its lease is released. Actual unmap remains GC-dependent.

## Automated race, mapping, and ranking evidence

The 38 D1d JVM tests cover:

- deterministic handoff-to-idle boundary requests, queued-owner final-apply revalidation,
  parallel 2,000-request coalescing bursts, direct executors, reentrant handoffs, executor
  rejection, and `Throwable` containment;
- editor, subtype, prefix, dictionary, request-serial, and engine-instance generation guards;
  independent same-generation schema/format/SHA changes, invalid-prefix invalidation, and
  current-token revalidation immediately before guarded apply;
- finish/destroy timeout and retry, interrupt preservation, concurrent release-once, and no
  release while a reader is outstanding;
- 100 repeated production `MappedByteBuffer` lifecycles over read-only temp files, read-only map
  verification, open-FD non-growth threshold of at most +3, and lease release every iteration;
- real `AtomicDictionaryStore` v1 activation, staged v2 activation refusal while v1 is mapped,
  v1 destroy, v2 activation, no hot-swap, and retained finals no greater than two;
- mapper, corrupt index, and executor-factory startup failures closing the consumed lease exactly
  once, including `AssertionError` paths;
- exact-word exclusion, top-three frequency ordering, unsigned u32 frequency, Unicode code-point
  lexical ties across BMP/supplementary characters, malformed layout, invalid UTF-8, empty input,
  and no match;
- all 22 recorded real-dictionary audit prefixes and the committed 100k dictionary.

Automated commands and results:

| Gate | Result |
|---|---|
| Targeted D1d JVM tests | 38 passed, 0 failed/skipped |
| Full JVM suite (`test --rerun-tasks`) | 91 passed, 0 failed/skipped |
| Debug + release assembly (`--rerun-tasks`) | `BUILD SUCCESSFUL`; release lint vital passed |
| no-INTERNET, source + debug APK + release APK | passed; only `android.permission.VIBRATE` |
| Source formatting (`git diff --check`) | passed |

Host timings use `System.nanoTime`, a warmed committed 100k dictionary, and 2,000 compute samples
(1,000 request samples). They are deterministic regression gates for this host, not Android device
or UI-frame measurements.

| Metric | Measured p95 | Gate | Result |
|---|---:|---:|---|
| Mixed 22-prefix compute | 0.009 ms | 5 ms | passed |
| Real maximum one-letter (`к`) fanout compute | 0.049 ms | 5 ms | passed |
| Request to non-applying result handoff | 0.020 ms | 16 ms | passed |
| Guarded final applies for stale A in queued-owner test | 0 | 0 | passed |

## APK delta

Both D1c baseline and D1d candidate use the same project/toolchain configuration. The candidate
was rebuilt with rerun tasks. Release shrinking removes the currently unreferenced isolated D1d
engine until D1e connects it.

| Artifact | D1c baseline | D1d candidate | Delta |
|---|---:|---:|---:|
| Debug APK | 2,895,976 B | 2,918,420 B | +22,444 B |
| Unsigned release APK | 1,430,323 B | 1,430,335 B | +12 B |

The release candidate remains below the D1 target of 1.7 MB and the absolute 3 MiB limit.

## Device evidence still pending

No physical Android device was available for this run. Host request-to-handoff timing does not
claim serialized-owner dispatch or guarded UI apply latency. The following remain in the D1f
device matrix after D1e integration:

- request through main-thread dispatch and guarded UI apply p95, including burst/load conditions;
- Android scheduler behavior and compute p95 on target low-end hardware;
- repeated lifecycle PSS, GC-dependent mapping reclamation, and platform open-FD observation;
- cold-start impact and storage activation I/O timing;
- ordinary typing responsiveness and zero visible stale results in integrated D1e behavior.

Therefore the automatable D1d core gates are green, while physical-device latency/PSS/lifecycle
acceptance is explicitly **pending D1f**. No device result is inferred from host tests.
