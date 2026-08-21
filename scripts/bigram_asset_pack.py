#!/usr/bin/env python3
"""E5b: pack the shipped Tatar bigram table asset — schema 2, magic ``TATBIGR\\0``.

This is the real, byte-exact artifact generator. It is deliberately a SEPARATE file from
``scripts/bigram_pack.py`` (E5a): that script is the measurement prototype whose gate was
independently reviewed on 2026-08-17, and its own docstring says its job is "measure ... before
any Android code" — turning it into the production packer as well would blur what was reviewed.
This module imports E5a's low-level, already-tested data-layer helpers (``count_pairs``,
``select_heads``, ``read_shipped_vocabulary``, corpus stats) rather than duplicating them, and
adds nothing to E5a's own behaviour.

Format (PROPOSALS.md, "## E5" / "E5b. Секции", byte-for-byte):

* header, 96 bytes, all integers unsigned little-endian:
  magic(8) + 4×u16(schemaId, formatVersion, headerSize, checksumAlgorithm) +
  12×u32(headCount, pairCount, successVocabularyCount, six section offsets, headBlobLength,
  successBlobLength, fileSize) + SHA-256(32) at byte offset 64 — the checksum is computed over
  the full raw file with the digest bytes themselves zeroed, the same trick as
  ``dictionary_pack.py``'s schema 1 (there at offset 40, here at 64 because schema 2 carries
  twice as many u32 fields for its six sections instead of schema 1's three);
* six sections, no padding, no separators: (1) H+1 u32 head-word offsets, (2) UTF-8 head-word
  blob in code-point lexical ascending order (binary search), (3) H+1 u32 success-range
  boundaries, (4) P u32 success ids in packing order (count descending, tie code-point
  ascending) grouped by head, (5) V+1 u32 success-word offsets, (6) UTF-8 deduplicated
  success-word blob in code-point lexical ascending order.

Two independent caps, the same ones E5a measured against, are enforced here too: compressed
<= 250 000 B and raw <= 1 048 576 B. A configuration that violates either stops generation with a
non-zero exit; no partial asset is ever written (the whole raw image is built and validated in
memory before either output file is touched).

**A head selected by frequency that ends up with zero successes in training is dropped from the
file, not stored with an empty range.** The validator below rejects empty ranges as corruption
(PROPOSALS.md, "E5b. Генератор, строгий валидатор" — "пустые диапазоны" is in the rejected list),
so the generator must never produce one; the alternative (storing H exactly as requested) would
make the generator capable of emitting a file its own validator calls corrupt. Dropped heads, if
any, are named in the report — nothing is silently resized.

Multilingual since 2026-08-21 (`docs/RUSSIAN-BIGRAMS.md`): ``--language`` picks the alphabet the
tokenizer and the shipped-vocabulary read apply, and defaults to Tatar, so the shipped Tatar asset
rebuilds byte for byte from the same inputs and the same command line as before.

Usage:

    python3 scripts/bigram_asset_pack.py pack \\
        --train tat_mixed_2015_1M-sentences.txt tat_web_2018_1M-sentences.txt \\
        --asset app/src/main/assets/dictionaries/tatar_top100k_v1.tdict.zlib \\
        --heads 10000 --successes-per-head 6 \\
        --out-raw tatar_bigrams_v1.tatbigr \\
        --out-compressed tatar_bigrams_v1.tatbigr.zlib \\
        --report docs/DICTIONARY-E5B.generated.json
"""

from __future__ import annotations

import argparse
import hashlib
import json
import struct
import sys
import time
import zlib
from dataclasses import dataclass, field
from pathlib import Path
from typing import Sequence, TextIO

sys.path.insert(0, str(Path(__file__).resolve().parent))

import dictionary_coverage as coverage  # noqa: E402
from bigram_pack import (  # noqa: E402
    MAX_COMPRESSED_BYTES,
    MAX_RAW_BYTES,
    count_pairs,
    peak_rss_bytes,
    read_shipped_vocabulary,
    select_heads,
)

MAGIC = b"TATBIGR\0"
SCHEMA_ID = 2
FORMAT_VERSION = 1
HEADER_SIZE = 96
CHECKSUM_OFFSET = 64
CHECKSUM_SIZE = 32
CHECKSUM_ALGORITHM_SHA256 = 1

# 8s (magic) + 4 x u16 (meta) + 12 x u32 (counts, six section offsets, two blob lengths, file
# size) + 32s (digest) = 8 + 8 + 48 + 32 = 96, and the digest starts at byte 64 — both numbers
# the contract names explicitly, and both fall out of this format string rather than being typed
# twice.
HEADER_FORMAT = "<8s" + "H" * 4 + "I" * 12 + "32s"
HEADER = struct.Struct(HEADER_FORMAT)
assert HEADER.size == HEADER_SIZE

# zlib settings — identical mode to dictionary_pack.py / bigram_pack.py, so compressed sizes are
# comparable across every asset the project ships.
COMPRESSION_LEVEL = 9
COMPRESSION_WBITS = 15
COMPRESSION_MEM_LEVEL = 9


class BigramFormatError(ValueError):
    """The bytes are not a valid TATBIGR schema-2 file."""


class BigramBudgetError(ValueError):
    """A cap the generator enforces on itself, not only in phase acceptance."""


@dataclass
class ParsedBigramTable:
    """What a validated file actually contains — the round-trip shape used by tests."""

    head_words: list[str]
    success_vocabulary: list[str]
    successes_by_head: dict[str, list[str]]  # packing order preserved, per head


def compress(raw: bytes) -> bytes:
    compressor = zlib.compressobj(
        level=COMPRESSION_LEVEL,
        method=zlib.DEFLATED,
        wbits=COMPRESSION_WBITS,
        memLevel=COMPRESSION_MEM_LEVEL,
        strategy=zlib.Z_DEFAULT_STRATEGY,
    )
    return compressor.compress(raw) + compressor.flush(zlib.Z_FINISH)


def decompress(asset: bytes) -> bytes:
    """Mirrors ``dictionary_pack.decompress_asset`` exactly, with schema 2's own caps."""
    if len(asset) > MAX_COMPRESSED_BYTES:
        raise BigramBudgetError(
            f"compressed asset is {len(asset)} bytes; limit is {MAX_COMPRESSED_BYTES}"
        )
    decompressor = zlib.decompressobj(wbits=COMPRESSION_WBITS)
    try:
        raw = decompressor.decompress(asset, MAX_RAW_BYTES + 1)
        if len(raw) > MAX_RAW_BYTES or decompressor.unconsumed_tail:
            raise BigramBudgetError(f"uncompressed table exceeds {MAX_RAW_BYTES} bytes")
        raw += decompressor.flush(MAX_RAW_BYTES + 1 - len(raw))
    except zlib.error as error:
        raise BigramFormatError(f"invalid zlib stream: {error}") from error
    if len(raw) > MAX_RAW_BYTES:
        raise BigramBudgetError(f"uncompressed table is {len(raw)} bytes; limit is {MAX_RAW_BYTES}")
    if not decompressor.eof:
        raise BigramFormatError("truncated zlib stream")
    if decompressor.unused_data:
        raise BigramFormatError("trailing or concatenated zlib data")
    if decompressor.unconsumed_tail:
        raise BigramFormatError("unconsumed zlib data")
    return raw


def _read_u32_array(raw: bytes, offset: int, count: int) -> tuple[int, ...]:
    return struct.unpack_from(f"<{count}I", raw, offset)


def _blob_words(raw: bytes, blob_offset: int, offsets: Sequence[int]) -> list[str]:
    """Decode a length-N+1 offset array over a UTF-8 blob into N strings, strict UTF-8."""
    words: list[str] = []
    for index in range(len(offsets) - 1):
        start, end = offsets[index], offsets[index + 1]
        if end < start:
            raise BigramFormatError(f"offset array is not non-decreasing at index {index}")
        if end == start:
            raise BigramFormatError(f"empty word at index {index}")
        chunk = raw[blob_offset + start : blob_offset + end]
        try:
            words.append(chunk.decode("utf-8", errors="strict"))
        except UnicodeDecodeError as error:
            raise BigramFormatError(f"invalid UTF-8 in blob at index {index}: {error}") from error
    return words


def _check_strictly_ascending(words: Sequence[str], label: str) -> None:
    for index in range(1, len(words)):
        if not words[index - 1] < words[index]:
            raise BigramFormatError(
                f"{label} is not strictly code-point ascending at index {index}: "
                f"{words[index - 1]!r} >= {words[index]!r}"
            )


def validate_raw(raw: bytes) -> ParsedBigramTable:
    """Strict validator — every corruption class PROPOSALS.md ('E5b') names is rejected here.

    Malformed/truncated/concatenated zlib is caught one layer up, by ``decompress`` — this
    function only ever sees already-inflated bytes, exactly like ``dictionary_pack.validate_raw``.
    """
    if len(raw) < HEADER_SIZE:
        raise BigramFormatError(f"file is {len(raw)} bytes, shorter than the {HEADER_SIZE}-byte header")
    fields = HEADER.unpack_from(raw)
    (
        magic,
        schema_id,
        format_version,
        header_size,
        checksum_algorithm,
        head_count,
        pair_count,
        success_vocabulary_count,
        section1_offset,
        section2_offset,
        section3_offset,
        section4_offset,
        section5_offset,
        section6_offset,
        head_blob_length,
        success_blob_length,
        file_size,
        digest,
    ) = fields

    if magic != MAGIC:
        raise BigramFormatError(f"unrecognized magic: {magic!r}")
    if schema_id != SCHEMA_ID:
        raise BigramFormatError(f"unsupported schema id: {schema_id}")
    if format_version != FORMAT_VERSION:
        raise BigramFormatError(f"unsupported format version: {format_version}")
    if header_size != HEADER_SIZE:
        raise BigramFormatError(f"unsupported header size: {header_size}")
    if checksum_algorithm != CHECKSUM_ALGORITHM_SHA256:
        raise BigramFormatError(f"unsupported checksum algorithm: {checksum_algorithm}")

    zeroed = raw[:CHECKSUM_OFFSET] + bytes(CHECKSUM_SIZE) + raw[CHECKSUM_OFFSET + CHECKSUM_SIZE :]
    expected_digest = hashlib.sha256(zeroed).digest()
    if digest != expected_digest:
        raise BigramFormatError("checksum mismatch")

    # Canonical section arithmetic: every offset and every length is a deterministic function of
    # (head_count, pair_count, success_vocabulary_count) under the "no padding, no separators"
    # rule — a header that disagrees with that arithmetic is corrupt, even if every byte it points
    # at happens to be well-formed.
    expected_section1 = HEADER_SIZE
    expected_section2 = expected_section1 + 4 * (head_count + 1)
    expected_section3 = expected_section2 + head_blob_length
    expected_section4 = expected_section3 + 4 * (head_count + 1)
    expected_section5 = expected_section4 + 4 * pair_count
    expected_section6 = expected_section5 + 4 * (success_vocabulary_count + 1)
    expected_file_size = expected_section6 + success_blob_length
    if (section1_offset, section2_offset, section3_offset, section4_offset, section5_offset, section6_offset) != (
        expected_section1,
        expected_section2,
        expected_section3,
        expected_section4,
        expected_section5,
        expected_section6,
    ):
        raise BigramFormatError("non-canonical section arithmetic")
    if file_size != expected_file_size:
        raise BigramFormatError(
            f"header file_size {file_size} does not match section arithmetic {expected_file_size}"
        )
    if len(raw) != file_size:
        raise BigramFormatError(
            f"actual length {len(raw)} does not match header file_size {file_size} "
            "(truncated or trailing bytes)"
        )

    head_offsets = _read_u32_array(raw, section1_offset, head_count + 1)
    if head_offsets[0] != 0 or head_offsets[-1] != head_blob_length:
        raise BigramFormatError("head offset array does not bound the head blob")
    head_words = _blob_words(raw, section2_offset, head_offsets)
    _check_strictly_ascending(head_words, "head words")

    success_ranges = _read_u32_array(raw, section3_offset, head_count + 1)
    if success_ranges[0] != 0 or success_ranges[-1] != pair_count:
        raise BigramFormatError("success range array does not bound the success id section")
    for index in range(head_count):
        if success_ranges[index] >= success_ranges[index + 1]:
            raise BigramFormatError(f"head {index} ({head_words[index]!r}) has an empty success range")

    success_ids = _read_u32_array(raw, section4_offset, pair_count)
    for position, identifier in enumerate(success_ids):
        if identifier >= success_vocabulary_count:
            raise BigramFormatError(
                f"success id {identifier} at position {position} is >= vocabulary size "
                f"{success_vocabulary_count}"
            )

    success_word_offsets = _read_u32_array(raw, section5_offset, success_vocabulary_count + 1)
    if success_word_offsets[0] != 0 or success_word_offsets[-1] != success_blob_length:
        raise BigramFormatError("success word offset array does not bound the success blob")
    success_vocabulary = _blob_words(raw, section6_offset, success_word_offsets)
    _check_strictly_ascending(success_vocabulary, "success vocabulary")

    successes_by_head: dict[str, list[str]] = {}
    for index, head in enumerate(head_words):
        start, end = success_ranges[index], success_ranges[index + 1]
        successes_by_head[head] = [success_vocabulary[success_ids[position]] for position in range(start, end)]

    return ParsedBigramTable(
        head_words=list(head_words),
        success_vocabulary=list(success_vocabulary),
        successes_by_head=successes_by_head,
    )


@dataclass
class PackResult:
    raw: bytes
    compressed: bytes
    head_count: int
    pair_count: int
    success_vocabulary_count: int
    dropped_heads: list[str] = field(default_factory=list)


def pack_bigram_table(
    heads_by_frequency: Sequence[str],
    table: dict[str, list[tuple[str, int]]],
    successes_per_head: int,
) -> PackResult:
    """Build the real schema-2 raw+compressed image for one (H, K) configuration.

    ``heads_by_frequency`` is the top-H head list in FREQUENCY order (as ``select_heads``
    returns it) — that is the order that decides SET membership. Storage order is different: the
    contract requires the head blob in code-point lexical ascending order for binary search, so
    the kept heads are re-sorted before serialization. Reordering heads does not touch each
    head's own success list, which keeps its packing order (count descending, tie code-point
    ascending) regardless of where the head ends up in the file.
    """
    kept_successes: dict[str, list[str]] = {}
    dropped_heads: list[str] = []
    for head in heads_by_frequency:
        successes = [word for word, _count in table.get(head, ())[:successes_per_head]]
        if not successes:
            dropped_heads.append(head)
            continue
        kept_successes[head] = successes

    kept_heads = sorted(kept_successes)  # code-point lexical ascending, for binary search

    success_vocabulary: dict[str, int] = {}
    for word in sorted({word for successes in kept_successes.values() for word in successes}):
        success_vocabulary[word] = len(success_vocabulary)

    head_offsets = [0]
    head_blob_parts: list[bytes] = []
    for head in kept_heads:
        encoded = head.encode("utf-8")
        head_blob_parts.append(encoded)
        head_offsets.append(head_offsets[-1] + len(encoded))
    head_blob = b"".join(head_blob_parts)

    success_ranges = [0]
    success_ids: list[int] = []
    for head in kept_heads:
        for word in kept_successes[head]:
            success_ids.append(success_vocabulary[word])
        success_ranges.append(len(success_ids))

    success_word_offsets = [0]
    success_blob_parts: list[bytes] = []
    for word in sorted(success_vocabulary, key=success_vocabulary.get):
        encoded = word.encode("utf-8")
        success_blob_parts.append(encoded)
        success_word_offsets.append(success_word_offsets[-1] + len(encoded))
    success_blob = b"".join(success_blob_parts)

    head_count = len(kept_heads)
    pair_count = len(success_ids)
    success_vocabulary_count = len(success_vocabulary)

    section1_offset = HEADER_SIZE
    section2_offset = section1_offset + 4 * (head_count + 1)
    section3_offset = section2_offset + len(head_blob)
    section4_offset = section3_offset + 4 * (head_count + 1)
    section5_offset = section4_offset + 4 * pair_count
    section6_offset = section5_offset + 4 * (success_vocabulary_count + 1)
    file_size = section6_offset + len(success_blob)

    zero_digest_header = HEADER.pack(
        MAGIC,
        SCHEMA_ID,
        FORMAT_VERSION,
        HEADER_SIZE,
        CHECKSUM_ALGORITHM_SHA256,
        head_count,
        pair_count,
        success_vocabulary_count,
        section1_offset,
        section2_offset,
        section3_offset,
        section4_offset,
        section5_offset,
        section6_offset,
        len(head_blob),
        len(success_blob),
        file_size,
        bytes(CHECKSUM_SIZE),
    )
    raw = (
        zero_digest_header
        + struct.pack(f"<{head_count + 1}I", *head_offsets)
        + head_blob
        + struct.pack(f"<{head_count + 1}I", *success_ranges)
        + struct.pack(f"<{pair_count}I", *success_ids)
        + struct.pack(f"<{success_vocabulary_count + 1}I", *success_word_offsets)
        + success_blob
    )
    digest = hashlib.sha256(raw).digest()
    raw = raw[:CHECKSUM_OFFSET] + digest + raw[CHECKSUM_OFFSET + CHECKSUM_SIZE :]

    if len(raw) > MAX_RAW_BYTES:
        raise BigramBudgetError(f"raw table is {len(raw)} bytes; limit is {MAX_RAW_BYTES}")
    compressed = compress(raw)
    if len(compressed) > MAX_COMPRESSED_BYTES:
        raise BigramBudgetError(f"compressed table is {len(compressed)} bytes; limit is {MAX_COMPRESSED_BYTES}")

    # Self-check: the generator must never emit a file its own validator would reject.
    validate_raw(raw)

    return PackResult(
        raw=raw,
        compressed=compressed,
        head_count=head_count,
        pair_count=pair_count,
        success_vocabulary_count=success_vocabulary_count,
        dropped_heads=dropped_heads,
    )


def run_pack(
    train_paths: Sequence[Path],
    asset_path: Path,
    heads: int,
    successes_per_head: int,
    shards: int,
    language: coverage.Language = coverage.DEFAULT_LANGUAGE,
) -> tuple[PackResult, dict[str, object]]:
    started = time.monotonic()
    vocabulary, frequencies = read_shipped_vocabulary(asset_path, language)
    ordered_heads = select_heads(frequencies, heads)
    table = count_pairs(
        train_paths,
        frozenset(ordered_heads),
        vocabulary,
        shards,
        stats=[],
        alphabet=language.alphabet,
    )
    result = pack_bigram_table(ordered_heads, table, successes_per_head)
    report = {
        "language": language.tag,
        "requested_heads": heads,
        "successes_per_head": successes_per_head,
        "actual_head_count": result.head_count,
        "dropped_heads": result.dropped_heads,
        "pair_count": result.pair_count,
        "success_vocabulary_count": result.success_vocabulary_count,
        "raw_bytes": len(result.raw),
        "compressed_bytes": len(result.compressed),
        "raw_sha256": hashlib.sha256(result.raw).hexdigest(),
        "compressed_sha256": hashlib.sha256(result.compressed).hexdigest(),
        "peak_rss_bytes": peak_rss_bytes(),
        "elapsed_seconds": round(time.monotonic() - started, 3),
        "shards": shards,
    }
    return result, report


def _atomic_write(path: Path, data: bytes) -> None:
    temp = path.with_suffix(path.suffix + ".tmp")
    temp.write_bytes(data)
    temp.replace(path)


def create_argument_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    pack = subparsers.add_parser("pack", help="build the real TATBIGR schema-2 asset")
    pack.add_argument("--train", nargs="+", required=True, type=Path)
    pack.add_argument("--asset", required=True, type=Path)
    pack.add_argument("--heads", type=int, required=True)
    pack.add_argument("--successes-per-head", type=int, required=True)
    pack.add_argument("--shards", type=int, default=8)
    pack.add_argument("--out-raw", required=True, type=Path)
    pack.add_argument("--out-compressed", required=True, type=Path)
    pack.add_argument("--report", type=Path)
    pack.add_argument(
        "--language", default=coverage.DEFAULT_LANGUAGE.tag, choices=sorted(coverage.LANGUAGES)
    )
    return parser


def main(argv: Sequence[str] | None = None, stream: TextIO = sys.stdout) -> int:
    arguments = create_argument_parser().parse_args(argv)
    if arguments.shards < 1:
        raise SystemExit("shards must be positive")
    result, report = run_pack(
        arguments.train,
        arguments.asset,
        arguments.heads,
        arguments.successes_per_head,
        arguments.shards,
        coverage.language_for(arguments.language),
    )
    text = json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True)
    if arguments.report is not None:
        arguments.report.write_text(text + "\n", encoding="utf-8")
    print(text, file=stream)
    # Only written after both the raw image and its compression have been validated above —
    # a partially written or over-cap asset never lands on disk.
    _atomic_write(arguments.out_raw, result.raw)
    _atomic_write(arguments.out_compressed, result.compressed)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
