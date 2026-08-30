# D1b — atomic device-protected dictionary storage

Automated verification date: 2026-07-22. This document records the storage contract,
automated evidence, APK delta, and the device evidence that is still outstanding.

## Storage and lifecycle contract

- The Android factory derives the dictionary directory only from
  `createDeviceProtectedStorageContext().filesDir`; the compressed source remains an APK asset.
- Preparation runs through the supplied background executor. It inflates to an exclusive temp
  file, checks bounded compressed and raw sizes and SHA-256 identities, flushes and fsyncs the
  file, validates the complete schema, atomically renames within the directory, then fsyncs the
  directory.
- Stores addressing the same canonical directory share a process-wide owner. Its lock covers
  temp cleanup, validation, publication, rename, retention, and catalog acquisition. Its lease
  counts are also shared across repeated factory/store instances. The single production-path
  state is deliberately retained for the process lifetime so eviction cannot split lock/lease
  identity while a reader is live.
- A lease covers the complete mapping, executor, and reader lifetime. A newer valid file remains
  staged while any older-version lease is live: activation returns no lease until the old
  executor/readers have stopped and all old leases are closed.
- Retention protects every leased file and keeps at most the active/current file plus one staged
  or retired final. Temps are not cleaned while another same-process store is publishing.
- Android `ErrnoException` failures from directory open/fsync/close and rename are translated to
  `IOException`. Public preparation returns `Unavailable`; catalog activation returns no lease;
  best-effort cleanup never throws into normal input.

The shared owner is intentionally process-wide, matching the single-process IME service. This
phase does not claim a cross-process file-lock protocol.

## Automated failure and race evidence

The 38 JVM tests cover:

- first publication, repeat validation without asset rewrite, and version update;
- corrupt asset/final, malformed schema/checksum/UTF-8/order/frequency, bounded inflate, and
  interrupted publication before rename;
- preflight and write-time no-space failures, fsync failure before and after rename, and generic
  checked platform failures at every public boundary;
- device-protected directory selection without credential-protected writes;
- two independent stores serializing an in-flight publication without deleting its temp;
- two independent stores sharing leases, retaining a file leased by the other store, and refusing
  a newer activation until every old reader lease closes;
- multi-store no-space cleanup retaining the active file and final retention of at most two;
- executor rejection, zero caller-thread storage I/O before worker execution, and absence of
  storage logging, network APIs, analytics, or typed-text inputs.

Automated commands and results:

| Gate | Result |
|---|---|
| D0 Python tests | 6 passed |
| D1a Python tests | 26 passed |
| D1b JVM tests | 38 passed, 0 failed/skipped |
| Debug + release assembly, rerun tasks | `BUILD SUCCESSFUL` |
| no-INTERNET, source manifest + debug APK + release APK | passed |
| Compressed dictionary | 600,606 B (limit 700,000 B) |
| Uncompressed dictionary | 2,542,036 B (limit 2,936,012 B) |

## APK delta

Both sides were rebuilt with rerun tasks in isolated worktrees. The baseline is the sequential
D1a commit `95f98d3`; the candidate includes D1b and no D1c/D1d code.

| Artifact | D1a baseline | D1b candidate | Delta |
|---|---:|---:|---:|
| Debug APK | 2,848,472 B | 2,885,783 B | +37,311 B |
| Unsigned release APK | 1,410,451 B | 1,427,147 B | +16,696 B |

The release candidate remains below the D1 target of 1.7 MB and the absolute 3 MiB limit.

## Evidence still required on a device

The manifest declares `LatinIME` direct-boot aware, production code uses the device-protected
context, and host tests prove the selected directory seam. This is not physical pre-unlock
evidence. Power-loss durability, actual direct-boot preparation/activation, storage exhaustion,
cold-start impact, and ordinary-input responsiveness still require the D1f device matrix.

Therefore the automated D1b core is green, but full D1b acceptance remains **BLOCKED on D1f
device instrumentation/UAT**. No physical-device result is inferred from JVM tests.
