# D1a — versioned Tatar top-100k dictionary asset

Generation and evaluation date: 2026-07-21. This document is the provenance,
attribution, transformation record, and acceptance record for the single shipped
dictionary payload:

`app/src/main/assets/dictionaries/tatar_top100k_v1.tdict.zlib`

The licensed archives and `*-words.txt` inputs are not stored in the repository. The
uncompressed real dictionary is a temporary validation artifact and is not shipped.

## License and attribution

The dictionary is derived from downloadable Tatar text corpora in the
[Leipzig Corpora Collection](https://wortschatz.uni-leipzig.de/en/download/tat).
Leipzig's [Terms of Usage](https://wortschatz.uni-leipzig.de/en/usage) license the
downloadable corpora under [Creative Commons Attribution 4.0 International
(CC BY 4.0)](https://creativecommons.org/licenses/by/4.0/).

Recommended citation: D. Goldhahn, T. Eckart, U. Quasthoff, “Building Large
Monolingual Dictionaries at the Leipzig Corpora Collection: From 100 to 200
Languages”, LREC 2012.

The resulting dictionary data remains a separate CC BY 4.0 data component. It is not
relicensed under the application's Apache-2.0 code license. The shipped attribution
is also present in `app/src/main/assets/dictionaries/NOTICE.txt`.

## Sources

| Corpus | Direct archive URL | Corpus date | Archive Last-Modified | Archive SHA-256 | Extracted `words.txt` SHA-256 |
|---|---|---|---|---|---|
| `tat_mixed_2015_1M` | https://downloads.wortschatz-leipzig.de/corpora/tat_mixed_2015_1M.tar.gz | 2015 | 2020-11-21 | `c5a27c731116c2540a1053b8b9d6cb3a16134f519f0bf7535bca274173d01fc7` | `b4577bcc838eb64114b337a009a45bcf1e0a74e870c97cb6589eeeeecb3554d7` |
| `tat_news_2015_1M` | https://downloads.wortschatz-leipzig.de/corpora/tat_news_2015_1M.tar.gz | 2015 | 2020-11-20 | `e7421c8d036bfaf6ce5dec6b2a121b2c85a55ae1a26004e51b71202f6765b2d7` | `e287a491e63d391a5689afeb44c612b0c2cf7c446bd1e5323229b1ed9a5cf5f6` |
| `tat_web_2018_1M` | https://downloads.wortschatz-leipzig.de/corpora/tat_web_2018_1M.tar.gz | 2018 | 2020-11-20 | `de7816dbd8334ad9cd516be43ddca76e157316db9a53576dc3e813005d7b3f87` | `3caac1a7a81b376cf6b590fb92cccd2264d1fdaae728f433b53a028c1aafee3f` |

The archive hashes were measured during D0 rather than published by the provider.
The corpus selection, archive sizes, source inventory, and access evidence are in
`docs/DICTIONARY-D0.md`.

## Complete transformation record

Generator: `scripts/dictionary_pack.py`, generator identity
`d1a-gen-1+zlib-1.2.12`, Python 3.14.5 standard library only, zlib compile/runtime
version 1.2.12. The generator imports the canonical D0 implementation in
`scripts/dictionary_coverage.py`; it does not maintain a second normalization rule set.

1. Open each of the three inputs as strict `utf-8-sig` with universal newline handling.
2. Ignore blank lines. Parse either `id<TAB>word<TAB>frequency` or
   `word<TAB>frequency`. A three-column ID and every frequency must be positive
   decimal integers. Any malformed row aborts generation.
3. Strip surrounding word whitespace, normalize to Unicode NFC, then apply Unicode
   lowercase.
4. Keep only nonempty forms of at most 64 code points whose every character is in
   `аәбвгдеёжҗзийклмнңоөпрстуүфхһцчшщъыьэюя`.
5. Sum equal normalized forms within and across corpora. Every accepted individual and
   accumulated frequency must fit a positive u32; overflow aborts generation.
6. Rank all unique forms by summed frequency descending, then Unicode code-point
   lexical ascending. Select exactly the first 100,000; the boundary frequency is 10.
7. Re-sort the selected forms only by Unicode code-point lexical ascending for binary
   prefix search. Store each corresponding frequency as a little-endian u32.
8. Serialize schema 1/version 1 described below, calculate its SHA-256 checksum, and
   strictly validate the result.
9. Compress the entire raw file as one RFC 1950 zlib stream containing RFC 1951 DEFLATE:
   level 9, `wbits=15`, `memLevel=9`, `Z_DEFAULT_STRATEGY`, one `Z_FINISH`, and no preset
   dictionary. A zlib stream has no filename or mtime field. No time, path, locale, or
   host metadata enters either output.
10. Strictly and boundedly decompress and validate the generated asset before it is
    published. Identical inputs and the full generator identity must produce identical
    bytes.

## Uncompressed schema 1/version 1

All multibyte integers are unsigned little-endian. The header is exactly 72 bytes:

| Byte offset | Size | Field | Schema 1/version 1 value |
|---:|---:|---|---|
| 0 | 8 | magic | ASCII `TATDICT` followed by `00` |
| 8 | 2 | schema ID | u16 `1` |
| 10 | 2 | format version | u16 `1` |
| 12 | 2 | header size | u16 `72` |
| 14 | 2 | checksum algorithm | u16 `1` = SHA-256 |
| 16 | 4 | entry count `N` | positive u32 |
| 20 | 4 | offset-index offset | u32 `72` |
| 24 | 4 | frequencies offset | u32 `72 + 4 × (N + 1)` |
| 28 | 4 | UTF-8 blob offset | u32 `frequencies offset + 4 × N` |
| 32 | 4 | UTF-8 blob byte size `B` | u32 |
| 36 | 4 | complete file size | u32 `blob offset + B` |
| 40 | 32 | checksum | SHA-256 described below |

The header is followed, without padding, by `N + 1` little-endian u32 offsets relative
to the blob, `N` little-endian u32 frequencies, and the concatenated UTF-8 word blob.
The first offset is zero, offsets are strictly increasing, and the terminal offset is
exactly `B`. There are no delimiters or trailing bytes.

The checksum covers the complete raw file with bytes 40–71 treated as 32 zero bytes.
Thus header metadata and every section are protected. Strictly valid UTF-8 preserves
Unicode code-point lexical order when compared as unsigned bytes; validation of the
alphabet and canonical form establishes that prerequisite for the future mmap reader.

## Resulting artifacts and budgets

| Property | Result | Fail-closed limit |
|---|---:|---:|
| Entries | 100,000 | exactly 100,000 |
| Uncompressed bytes | 2542036 | 2936012 bytes (2.8 MiB) |
| Compressed bytes | 600606 | 700000 bytes |
| Uncompressed SHA-256 | `798d3257700c092cdf17cbe148eb0383b82eb6a2230132af417c6a1b8548f558` | exact provenance match |
| Asset SHA-256 | `2d98ed359aa11261a5042a13c5ca9459c6e365c6ab4bf0563d0e3604a7485cae` | exact provenance match |

The validator checks the compressed limit before bounded decompression and the raw
limit before parsing. It rejects malformed/truncated/concatenated zlib, bad
magic/schema/version/checksum, noncanonical section arithmetic, invalid offsets or
UTF-8, noncanonical words, duplicates, unsorted words, zero frequencies, and trailing
bytes.

## Independent held-out comparison

The complete `tat_news_2015_1M` frequency list is excluded from training. Training uses
only `tat_mixed_2015_1M` and `tat_web_2018_1M`. For each cutoff, coverage is the sum of
accepted held-out token frequencies whose normalized word is in the training top-N,
divided by all 13,741,658 accepted held-out tokens. No 150k artifact is written.

| Training cutoff | Covered held-out tokens | Held-out token coverage |
|---:|---:|---:|
| 50,000 | 13,174,144 | 95.87011989382941% |
| 100,000 | 13,448,999 | 97.87027882661611% |
| 150,000 | 13,547,975 | 98.59054125783075% |

The 150k-minus-100k gap is **0.7202624312146355 percentage points**, below the
1.0 percentage-point fail-closed limit.

## Automated project self-review of query quality

There was no human reviewer available. `docs/DICTIONARY-D1A-QUERY-REVIEW.tsv` is
explicitly an **AUTOMATED PROJECT SELF-REVIEW**, with reviewer
`automated (dictionary_pack.py query-audit)` and date 2026-07-21. The authored query
set covers 22 everyday Tatar prefixes. The tool regenerates top-three candidates from
the committed asset and requires an exact match with every recorded row.

The conservative result is PASS: all recorded candidates are recognizable Tatar forms;
zero candidate was classified as Russian-only, technical, encoding/OCR, or garbage.
Any undecidable candidate would be classified as failure rather than accepted. This is
limited evidence and is not represented as review by a human or native speaker.

## APK gate

A pristine `HEAD` export without the D1a files produced a release APK of 828278 bytes.
The final release APK including the compressed dictionary and shipped NOTICE is
1410455 bytes, a delta of 582177 bytes. This remains below the
absolute 3 MiB/3,145,728-byte gate. No build file or Android source change was made.

---

## Repack for 1.9.0 — conversational words merged in (mission `tt-dict-accept`, 2026-08-24)

Everything above records the D1a build of 2026-07-21 and is left exactly as it was: those
numbers describe the Leipzig-only artifact, and rewriting them would erase the only record of
what this file used to ship. This section records the repack, and the numbers here are the ones
that describe the file committed today.

**What changed.** 226 conversational Tatar forms, accepted by the machine rule of
`docs/DICT-ACCEPT.md`, displaced the 226 least frequent Leipzig forms. The entry count is
unchanged at exactly 100 000, and so is every other property of the format. Every word in the
artifact — the 99 774 that were already here and the 226 that arrived — carries the sum of its
Leipzig frequency and its conversational frequency; `docs/dict-accept/conv-freq-tt.tsv` holds
the second half of that sum and makes the repack reproducible without the corpora.

| Property | Repacked (committed today) | D1a 2026-07-21 | Fail-closed limit |
|---|---:|---:|---:|
| Entries | 100,000 | 100,000 | exactly 100,000 |
| Uncompressed bytes | 2541374 | 2542036 | 2936012 bytes (2.8 MiB) |
| Compressed bytes | 601143 | 600606 | 700000 bytes |
| Uncompressed SHA-256 | `1670e8d8a7b282fb419de506b0aaea5e8846c4c3e5ccf52ac725140fc7aa9df3` | `798d3257…` | exact provenance match |
| Asset SHA-256 | `f44fc5bf1089c24481cfc68589d4d60626ac378dc6f65880b4044fe355a59267` | `2d98ed35…` | exact provenance match |

**Attribution.** The 226 new forms come from Tatoeba (CC BY 2.0 FR) and OpenSubtitles (no
licence grant; used by operator decision of 2026-08-24). `NOTICE.txt` next to the asset names
both, and `docs/PUBLISH-CHECKLIST.md` carries the OpenSubtitles risk to release. The Leipzig
CC BY 4.0 attribution above is unaffected: Leipzig is still the source of 99 774 of the
100 000 entries and of every written frequency in the file.

**The query self-review was re-run, not carried over.** `docs/DICTIONARY-D1A-QUERY-REVIEW.tsv`
now records `review_date` 2026-08-24 on all 22 prefixes. Two of them changed candidates:
`исәнм` swapped the order inside its pair, and `дус` replaced `дуслыгы` with `дуслар`. The
other twenty are byte-identical to the 2026-07-21 rows. The classification is still an
AUTOMATED PROJECT SELF-REVIEW and still not review by a human or a native speaker.

---

## Repack for 1.9.1 — everything but the fragments (mission `tt-dict-widen`, 2026-08-24)

The section above records the file that sat in the tree on the morning of 2026-08-24 and is
left exactly as it was. The numbers here describe the file committed now.

**What changed.** The operator read a hundred random words the 1.9.0 rule had turned away,
judged them ordinary language and lifted the "second independent source" bar. For Tatar the
whole acceptance queue is now in the composition except five vowelless fragments (`хмм`,
`һмм`, `псс`, `рнк`, `тмб`); the length cut that the Russian half uses is deliberately NOT
applied here, because Tatar words are shorter and cutting by length removes living ones — see
`docs/DICT-WIDEN.md`. Accepted went from 1 688 to 3 729, and **303** of them entered the
artifact, displacing the 303 least frequent Leipzig forms. The entry count is unchanged at
exactly 100 000, and so is the frequency-sum rule.

| Property | Repacked 1.9.1 (committed today) | Repack 1.9.0 | D1a 2026-07-21 | Fail-closed limit |
|---|---:|---:|---:|---:|
| Entries | 100,000 | 100,000 | 100,000 | exactly 100,000 |
| Uncompressed bytes | 2541204 | 2541374 | 2542036 | 2936012 bytes (2.8 MiB) |
| Compressed bytes | 601118 | 601143 | 600606 | 700000 bytes |
| Uncompressed SHA-256 | `8f434ec7cfd718df31b4410e55d36cbb914d2497ec265f89db3aaf48ec625f76` | `1670e8d8…` | `798d3257…` | exact provenance match |
| Asset SHA-256 | `76bd5a39bc1091e7e85279e058385321db231084c269fd0a000f7ecb59bce7ac` | `f44fc5bf…` | `2d98ed35…` | exact provenance match |

**Attribution.** The sources are the ones 1.9.0 already carried: Tatoeba (CC BY 2.0 FR) and
OpenSubtitles (no licence grant; used by operator decision of 2026-08-24). Leipzig is still
the source of **99 697** of the 100 000 entries and of every written frequency in the file.
`docs/PUBLISH-CHECKLIST.md` still carries the OpenSubtitles risk to release, and it is still
open.

**The query self-review was re-run.** All 22 Tatar prefixes produce candidates byte-identical
to the 1.9.0 rows: the 77 extra Tatar words that entered are too rare to reach any reviewed
prefix's top three. The classification is still an AUTOMATED PROJECT SELF-REVIEW and still not
review by a human or a native speaker.
