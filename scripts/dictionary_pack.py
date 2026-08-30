#!/usr/bin/env python3
"""Build, validate, evaluate, and audit a packed Cyrillic dictionary asset.

The tool uses only the Python standard library. Parsing, normalization, alphabet,
length, frequency ranking, and tie-breaking come from dictionary_coverage.py.

One binary format serves every language: the language decides only which alphabet the
corpus rows are filtered by and which size budget the result must fit. ``--language tat``
is the default and reproduces the D1a Tatar asset byte for byte.
"""

from __future__ import annotations

import argparse
import bisect
import csv
import hashlib
import hmac
import json
import math
import os
import struct
import sys
import tempfile
import zlib
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Sequence, TextIO

import dictionary_coverage as coverage


MAGIC = b"TATDICT\x00"
SCHEMA_ID = 1
FORMAT_VERSION = 1
CHECKSUM_ALGORITHM_SHA256 = 1
HEADER = struct.Struct("<8sHHHHIIIIII32s")
HEADER_SIZE = 72
CHECKSUM_OFFSET = 40
CHECKSUM_SIZE = 32
MAX_U32 = 0xFFFF_FFFF
# ONE budget for the format, the same for every language. The Russian top-100k measured
# 606,315 / 2,540,622 bytes against these caps — within a hundred kilobytes of the Tatar one,
# because Russian words are barely longer (8.9 vs 8.7 code points on average). A per-language
# budget was considered and dropped: a second, laxer number would only ever be an invitation to
# ship a bigger artifact without noticing, and there is nothing to buy with it.
MAX_COMPRESSED_BYTES = 700_000
MAX_UNCOMPRESSED_BYTES = 2_936_012
DEFAULT_COUNT = 100_000
DEFAULT_CUTOFFS = (50_000, 100_000, 150_000)
COMPRESSION_LEVEL = 9
COMPRESSION_WBITS = 15
COMPRESSION_MEM_LEVEL = 9
GENERATOR_VERSION = f"d1a-gen-1+zlib-{zlib.ZLIB_RUNTIME_VERSION}"
AUTOMATED_REVIEWER = "automated (dictionary_pack.py query-audit)"
AUTOMATED_REVIEW_DATE = "2026-07-21"

assert HEADER.size == HEADER_SIZE


class DictionaryPackError(ValueError):
    """Base class for a fail-closed dictionary error."""


class DictionaryInputError(DictionaryPackError):
    """Malformed source data or invalid build request (exit 2)."""


class DictionaryFormatError(DictionaryPackError):
    """Malformed raw dictionary or zlib stream (exit 3)."""


class DictionaryBudgetError(DictionaryPackError):
    """Compressed or uncompressed size budget breach (exit 4)."""


class DictionaryQualityError(DictionaryPackError):
    """Held-out or query-review gate failure (exit 5)."""


class CheckedFrequencyCounter(Counter[str]):
    """Counter that rejects every accepted normalized frequency overflow."""

    def __setitem__(self, key: str, value: int) -> None:
        if value > MAX_U32:
            raise DictionaryInputError(
                f"summed frequency for {key!r} exceeds u32: {value}"
            )
        super().__setitem__(key, value)


@dataclass(frozen=True)
class ParsedDictionary:
    raw: bytes
    words: tuple[str, ...]
    word_bytes: tuple[bytes, ...]
    frequencies: tuple[int, ...]

    @property
    def entry_count(self) -> int:
        return len(self.words)


@dataclass(frozen=True)
class BuiltDictionary:
    raw: bytes
    asset: bytes
    parsed: ParsedDictionary
    boundary_frequency: int


def _read_frequencies(
    paths: Sequence[Path], language: coverage.Language = coverage.DEFAULT_LANGUAGE
) -> CheckedFrequencyCounter:
    if not paths:
        raise DictionaryInputError("at least one Leipzig input is required")
    frequencies = CheckedFrequencyCounter()
    for path in paths:
        with path.open("r", encoding="utf-8-sig", newline="") as stream:
            coverage.read_source(
                stream,
                str(path),
                frequencies,
                skip_malformed=False,
                alphabet=language.alphabet,
            )
    if not frequencies:
        raise DictionaryInputError(
            f"inputs contain no usable {language.display} words"
        )
    return frequencies


def select_entries(
    frequencies: Counter[str], count: int
) -> tuple[list[tuple[str, int]], int]:
    if count <= 0:
        raise DictionaryInputError("count must be positive")
    ranked = coverage.sorted_entries(frequencies)
    if len(ranked) < count:
        raise DictionaryInputError(
            f"requested {count} entries but only {len(ranked)} usable words exist"
        )
    selected = ranked[:count]
    boundary_frequency = selected[-1][1]
    for word, frequency in selected:
        if not 0 < frequency <= MAX_U32:
            raise DictionaryInputError(
                f"frequency for {word!r} is not a positive u32: {frequency}"
            )
    selected.sort(key=lambda item: item[0])
    return selected, boundary_frequency


def _checksum_with_zeroed_digest(raw: bytes) -> bytes:
    digest_input = (
        raw[:CHECKSUM_OFFSET]
        + bytes(CHECKSUM_SIZE)
        + raw[CHECKSUM_OFFSET + CHECKSUM_SIZE :]
    )
    return hashlib.sha256(digest_input).digest()


def serialize_entries(
    entries: Sequence[tuple[str, int]],
    language: coverage.Language = coverage.DEFAULT_LANGUAGE,
) -> bytes:
    if not entries:
        raise DictionaryInputError("cannot serialize an empty dictionary")

    encoded_words: list[bytes] = []
    offsets = [0]
    frequencies: list[int] = []
    previous_word: str | None = None
    for word, frequency in entries:
        normalized, reason = coverage.normalize_word(word, language.alphabet)
        if reason is not None or normalized != word:
            raise DictionaryInputError(f"word is not canonical: {word!r}")
        if previous_word is not None:
            if word == previous_word:
                raise DictionaryInputError(f"duplicate word: {word!r}")
            if word < previous_word:
                raise DictionaryInputError("entries are not Unicode-lexically sorted")
        if not 0 < frequency <= MAX_U32:
            raise DictionaryInputError(
                f"frequency for {word!r} is not a positive u32: {frequency}"
            )
        encoded = word.encode("utf-8")
        if offsets[-1] + len(encoded) > MAX_U32:
            raise DictionaryInputError("word blob exceeds u32")
        encoded_words.append(encoded)
        offsets.append(offsets[-1] + len(encoded))
        frequencies.append(frequency)
        previous_word = word

    entry_count = len(entries)
    if entry_count >= MAX_U32:
        raise DictionaryInputError("entry count does not fit schema 1")
    offset_index_offset = HEADER_SIZE
    frequencies_offset = offset_index_offset + 4 * (entry_count + 1)
    word_blob_offset = frequencies_offset + 4 * entry_count
    blob = b"".join(encoded_words)
    file_size = word_blob_offset + len(blob)
    if file_size > MAX_U32:
        raise DictionaryInputError("dictionary file size exceeds u32")

    zero_digest_header = HEADER.pack(
        MAGIC,
        SCHEMA_ID,
        FORMAT_VERSION,
        HEADER_SIZE,
        CHECKSUM_ALGORITHM_SHA256,
        entry_count,
        offset_index_offset,
        frequencies_offset,
        word_blob_offset,
        len(blob),
        file_size,
        bytes(CHECKSUM_SIZE),
    )
    raw = (
        zero_digest_header
        + struct.pack(f"<{entry_count + 1}I", *offsets)
        + struct.pack(f"<{entry_count}I", *frequencies)
        + blob
    )
    digest = hashlib.sha256(raw).digest()
    return raw[:CHECKSUM_OFFSET] + digest + raw[CHECKSUM_OFFSET + CHECKSUM_SIZE :]


def compress_raw(raw: bytes) -> bytes:
    compressor = zlib.compressobj(
        level=COMPRESSION_LEVEL,
        method=zlib.DEFLATED,
        wbits=COMPRESSION_WBITS,
        memLevel=COMPRESSION_MEM_LEVEL,
        strategy=zlib.Z_DEFAULT_STRATEGY,
    )
    return compressor.compress(raw) + compressor.flush(zlib.Z_FINISH)


def decompress_asset(
    asset: bytes, language: coverage.Language = coverage.DEFAULT_LANGUAGE
) -> bytes:
    max_compressed, max_uncompressed = MAX_COMPRESSED_BYTES, MAX_UNCOMPRESSED_BYTES
    if len(asset) > max_compressed:
        raise DictionaryBudgetError(
            f"compressed asset is {len(asset)} bytes; limit is {max_compressed}"
        )
    decompressor = zlib.decompressobj(wbits=COMPRESSION_WBITS)
    try:
        raw = decompressor.decompress(asset, max_uncompressed + 1)
        if len(raw) > max_uncompressed or decompressor.unconsumed_tail:
            raise DictionaryBudgetError(
                f"uncompressed dictionary exceeds {max_uncompressed} bytes"
            )
        remaining = max_uncompressed + 1 - len(raw)
        raw += decompressor.flush(remaining)
    except zlib.error as error:
        raise DictionaryFormatError(f"invalid zlib stream: {error}") from error
    if len(raw) > max_uncompressed:
        raise DictionaryBudgetError(
            f"uncompressed dictionary is {len(raw)} bytes; "
            f"limit is {max_uncompressed}"
        )
    if not decompressor.eof:
        raise DictionaryFormatError("truncated zlib stream")
    if decompressor.unused_data:
        raise DictionaryFormatError("trailing or concatenated zlib data")
    if decompressor.unconsumed_tail:
        raise DictionaryFormatError("unconsumed zlib data")
    return raw


def validate_raw(
    raw: bytes,
    expected_count: int | None = None,
    language: coverage.Language = coverage.DEFAULT_LANGUAGE,
) -> ParsedDictionary:
    max_uncompressed = MAX_UNCOMPRESSED_BYTES
    if len(raw) > max_uncompressed:
        raise DictionaryBudgetError(
            f"uncompressed dictionary is {len(raw)} bytes; "
            f"limit is {max_uncompressed}"
        )
    if len(raw) < HEADER_SIZE:
        raise DictionaryFormatError("raw dictionary is shorter than its header")
    (
        magic,
        schema_id,
        format_version,
        header_size,
        checksum_algorithm,
        entry_count,
        offset_index_offset,
        frequencies_offset,
        word_blob_offset,
        word_blob_size,
        file_size,
        stored_checksum,
    ) = HEADER.unpack_from(raw)

    if magic != MAGIC:
        raise DictionaryFormatError("wrong dictionary magic")
    if schema_id != SCHEMA_ID:
        raise DictionaryFormatError(f"unsupported schema id: {schema_id}")
    if format_version != FORMAT_VERSION:
        raise DictionaryFormatError(f"unsupported format version: {format_version}")
    if header_size != HEADER_SIZE:
        raise DictionaryFormatError(f"unexpected header size: {header_size}")
    if checksum_algorithm != CHECKSUM_ALGORITHM_SHA256:
        raise DictionaryFormatError(
            f"unsupported checksum algorithm: {checksum_algorithm}"
        )
    if entry_count == 0:
        raise DictionaryFormatError("entry count must be positive")
    if expected_count is not None and entry_count != expected_count:
        raise DictionaryFormatError(
            f"expected {expected_count} entries, found {entry_count}"
        )

    expected_offsets_offset = HEADER_SIZE
    expected_frequencies_offset = expected_offsets_offset + 4 * (entry_count + 1)
    expected_blob_offset = expected_frequencies_offset + 4 * entry_count
    expected_file_size = expected_blob_offset + word_blob_size
    for value, label in (
        (expected_frequencies_offset, "frequencies offset"),
        (expected_blob_offset, "word blob offset"),
        (expected_file_size, "file size"),
    ):
        if value > MAX_U32:
            raise DictionaryFormatError(f"{label} arithmetic exceeds u32")
    if offset_index_offset != expected_offsets_offset:
        raise DictionaryFormatError("offset index is not at the canonical offset")
    if frequencies_offset != expected_frequencies_offset:
        raise DictionaryFormatError("frequency section is not at the canonical offset")
    if word_blob_offset != expected_blob_offset:
        raise DictionaryFormatError("word blob is not at the canonical offset")
    if file_size != expected_file_size or file_size != len(raw):
        raise DictionaryFormatError("declared file size does not match canonical layout")

    calculated_checksum = _checksum_with_zeroed_digest(raw)
    if not hmac.compare_digest(stored_checksum, calculated_checksum):
        raise DictionaryFormatError("SHA-256 checksum mismatch")

    offsets = struct.unpack_from(f"<{entry_count + 1}I", raw, offset_index_offset)
    frequencies = struct.unpack_from(f"<{entry_count}I", raw, frequencies_offset)
    if offsets[0] != 0:
        raise DictionaryFormatError("first word offset must be zero")
    if offsets[-1] != word_blob_size:
        raise DictionaryFormatError("terminal word offset must equal blob size")
    previous_offset = offsets[0]
    for index, offset in enumerate(offsets[1:], start=1):
        if offset > word_blob_size:
            raise DictionaryFormatError(f"word offset {index} is outside the blob")
        if offset <= previous_offset:
            raise DictionaryFormatError("word offsets must be strictly increasing")
        previous_offset = offset

    blob = raw[word_blob_offset:file_size]
    words: list[str] = []
    encoded_words: list[bytes] = []
    previous_word: str | None = None
    for index in range(entry_count):
        encoded = blob[offsets[index] : offsets[index + 1]]
        try:
            word = encoded.decode("utf-8", errors="strict")
        except UnicodeDecodeError as error:
            raise DictionaryFormatError(f"word {index} is not valid UTF-8") from error
        normalized, reason = coverage.normalize_word(word, language.alphabet)
        if reason is not None:
            raise DictionaryFormatError(f"word {index} is invalid: {reason}")
        if normalized != word:
            raise DictionaryFormatError(f"word {index} is not NFC lowercase")
        if previous_word is not None:
            if word == previous_word:
                raise DictionaryFormatError(f"duplicate dictionary word: {word!r}")
            if word < previous_word:
                raise DictionaryFormatError("dictionary words are not lexically sorted")
        words.append(word)
        encoded_words.append(encoded)
        previous_word = word
    for index, frequency in enumerate(frequencies):
        if frequency == 0:
            raise DictionaryFormatError(f"frequency {index} must be positive")

    return ParsedDictionary(
        raw=raw,
        words=tuple(words),
        word_bytes=tuple(encoded_words),
        frequencies=tuple(frequencies),
    )


def validate_asset(
    asset: bytes,
    expected_count: int | None = None,
    language: coverage.Language = coverage.DEFAULT_LANGUAGE,
) -> ParsedDictionary:
    return validate_raw(decompress_asset(asset, language), expected_count, language)


def build_dictionary(
    paths: Sequence[Path],
    count: int,
    language: coverage.Language = coverage.DEFAULT_LANGUAGE,
) -> BuiltDictionary:
    frequencies = _read_frequencies(paths, language)
    entries, boundary_frequency = select_entries(frequencies, count)
    raw = serialize_entries(entries, language)
    parsed = validate_raw(raw, expected_count=count, language=language)
    asset = compress_raw(raw)
    if len(asset) > MAX_COMPRESSED_BYTES:
        raise DictionaryBudgetError(
            f"compressed asset is {len(asset)} bytes; limit is {MAX_COMPRESSED_BYTES}"
        )
    reparsed = validate_asset(asset, expected_count=count, language=language)
    if reparsed.raw != raw:
        raise DictionaryFormatError("zlib round trip changed the raw dictionary")
    return BuiltDictionary(raw, asset, parsed, boundary_frequency)


def _stage_file(path: Path, data: bytes) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        mode="wb", dir=path.parent, prefix=f".{path.name}.", delete=False
    ) as stream:
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())
        return Path(stream.name)


def _write_outputs(raw_path: Path, raw: bytes, asset_path: Path, asset: bytes) -> None:
    staged: list[tuple[Path, Path]] = []
    try:
        staged.append((_stage_file(raw_path, raw), raw_path))
        staged.append((_stage_file(asset_path, asset), asset_path))
        for temporary, destination in staged:
            os.replace(temporary, destination)
        staged.clear()
    finally:
        for temporary, _ in staged:
            try:
                temporary.unlink()
            except FileNotFoundError:
                pass


def summary(asset: bytes, parsed: ParsedDictionary) -> dict[str, object]:
    return {
        "asset_sha256": hashlib.sha256(asset).hexdigest(),
        "compressed_bytes": len(asset),
        "entry_count": parsed.entry_count,
        "format_version": FORMAT_VERSION,
        "generator_version": GENERATOR_VERSION,
        "raw_sha256": hashlib.sha256(parsed.raw).hexdigest(),
        "schema_id": SCHEMA_ID,
        "uncompressed_bytes": len(parsed.raw),
        "zlib_runtime_version": zlib.ZLIB_RUNTIME_VERSION,
    }


def build_coverage_report(
    training_paths: Sequence[Path],
    held_out_path: Path,
    cutoffs: Sequence[int],
    comparison: tuple[int, int] = (100_000, 150_000),
    language: coverage.Language = coverage.DEFAULT_LANGUAGE,
) -> dict[str, object]:
    training = _read_frequencies(training_paths, language)
    ranked = coverage.sorted_entries(training)
    if max(cutoffs) > len(ranked):
        raise DictionaryInputError(
            f"largest cutoff {max(cutoffs)} exceeds {len(ranked)} training words"
        )
    held_out = _read_frequencies([held_out_path], language)
    held_out_tokens = sum(held_out.values())
    rows: list[dict[str, object]] = []
    by_cutoff: dict[int, float] = {}
    for cutoff in cutoffs:
        vocabulary = {word for word, _ in ranked[:cutoff]}
        covered = sum(
            frequency for word, frequency in held_out.items() if word in vocabulary
        )
        value = covered / held_out_tokens
        by_cutoff[cutoff] = value
        rows.append(
            {
                "cutoff": cutoff,
                "covered_tokens": covered,
                "held_out_tokens": held_out_tokens,
                "token_coverage": value,
                "token_coverage_percent": value * 100,
            }
        )
    lower, upper = comparison
    if lower not in by_cutoff or upper not in by_cutoff:
        raise DictionaryInputError(
            f"cutoffs must include comparison pair {lower},{upper}"
        )
    gap_pp = (by_cutoff[upper] - by_cutoff[lower]) * 100
    return {
        "comparison_lower": lower,
        "comparison_upper": upper,
        "coverage_gap_pp": gap_pp,
        "cutoffs": rows,
        "held_out_accepted_tokens": held_out_tokens,
        "held_out_unique_words": len(held_out),
        "language": language.tag,
        "schema_version": 1,
        "training_unique_words": len(ranked),
    }


def prefix_candidates(
    dictionary: ParsedDictionary,
    prefix: str,
    top: int,
    language: coverage.Language = coverage.DEFAULT_LANGUAGE,
) -> list[tuple[str, int]]:
    normalized, reason = coverage.normalize_word(prefix, language.alphabet)
    if reason is not None or normalized != prefix:
        raise DictionaryInputError(f"query is not canonical: {prefix!r}")
    if top <= 0:
        raise DictionaryInputError("top must be positive")
    start = bisect.bisect_left(dictionary.words, prefix)
    matches: list[tuple[str, int]] = []
    for index in range(start, dictionary.entry_count):
        word = dictionary.words[index]
        if not word.startswith(prefix):
            break
        if word != prefix:
            matches.append((word, dictionary.frequencies[index]))
    matches.sort(key=lambda item: (-item[1], item[0]))
    return matches[:top]


def _read_queries(
    path: Path, language: coverage.Language = coverage.DEFAULT_LANGUAGE
) -> list[str]:
    queries: list[str] = []
    seen: set[str] = set()
    with path.open("r", encoding="utf-8", newline="") as stream:
        for line_number, line in enumerate(stream, start=1):
            query = line.strip()
            if not query or query.startswith("#"):
                continue
            normalized, reason = coverage.normalize_word(query, language.alphabet)
            if reason is not None or normalized != query:
                raise DictionaryInputError(
                    f"{path}:{line_number}: query is not canonical"
                )
            if query in seen:
                raise DictionaryInputError(f"{path}:{line_number}: duplicate query")
            queries.append(query)
            seen.add(query)
    if not queries:
        raise DictionaryInputError("query file contains no queries")
    return queries


def _audit_rows(
    dictionary: ParsedDictionary,
    queries: Iterable[str],
    top: int,
    language: coverage.Language = coverage.DEFAULT_LANGUAGE,
) -> list[dict[str, str]]:
    return [
        {
            "prefix": query,
            "candidates": "|".join(
                word
                for word, _ in prefix_candidates(dictionary, query, top, language)
            ),
        }
        for query in queries
    ]


def _check_review(
    rows: Sequence[dict[str, str]],
    review_path: Path,
    reviewer: str = AUTOMATED_REVIEWER,
    review_date: str = AUTOMATED_REVIEW_DATE,
) -> None:
    with review_path.open("r", encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        expected_fields = [
            "prefix",
            "candidates",
            "classification",
            "reviewer",
            "review_date",
            "note",
        ]
        if reader.fieldnames != expected_fields:
            raise DictionaryQualityError("review TSV has an unexpected header")
        reviewed = list(reader)
    if len(reviewed) != len(rows):
        raise DictionaryQualityError("review TSV query count does not match")
    for actual, recorded in zip(rows, reviewed, strict=True):
        prefix = actual["prefix"]
        if recorded["prefix"] != prefix or recorded["candidates"] != actual["candidates"]:
            raise DictionaryQualityError(f"review candidates changed for {prefix!r}")
        if recorded["classification"] != "pass":
            raise DictionaryQualityError(f"query review failed closed for {prefix!r}")
        if recorded["reviewer"] != reviewer:
            raise DictionaryQualityError(f"unexpected reviewer for {prefix!r}")
        if recorded["review_date"] != review_date:
            raise DictionaryQualityError(f"unexpected review date for {prefix!r}")
        if not recorded["note"].strip():
            raise DictionaryQualityError(f"missing review rationale for {prefix!r}")


def _positive_int(value: str) -> int:
    try:
        parsed = int(value)
    except ValueError as error:
        raise argparse.ArgumentTypeError("must be an integer") from error
    if parsed <= 0:
        raise argparse.ArgumentTypeError("must be positive")
    return parsed


def _nonnegative_float(value: str) -> float:
    try:
        parsed = float(value)
    except ValueError as error:
        raise argparse.ArgumentTypeError("must be a number") from error
    if not math.isfinite(parsed):
        raise argparse.ArgumentTypeError("must be finite")
    if parsed < 0:
        raise argparse.ArgumentTypeError("must not be negative")
    return parsed


def create_argument_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", action="version", version=GENERATOR_VERSION)
    commands = parser.add_subparsers(dest="command", required=True)

    def add_language(command: argparse.ArgumentParser) -> None:
        command.add_argument(
            "--language",
            choices=sorted(coverage.LANGUAGES),
            default=coverage.DEFAULT_LANGUAGE.tag,
            help="alphabet and size budget to use (default: tat)",
        )

    build = commands.add_parser("build", help="build raw and zlib dictionary outputs")
    build.add_argument("inputs", nargs="+", type=Path)
    build.add_argument("--count", type=_positive_int, default=DEFAULT_COUNT)
    build.add_argument("--raw-output", type=Path, required=True)
    build.add_argument("--asset-output", type=Path, required=True)
    add_language(build)

    validate = commands.add_parser("validate", help="strictly validate a dictionary")
    source = validate.add_mutually_exclusive_group(required=True)
    source.add_argument("--raw", type=Path)
    source.add_argument("--asset", type=Path)
    validate.add_argument("--expected-count", type=_positive_int)
    validate.add_argument("--raw-output", type=Path)
    add_language(validate)

    report = commands.add_parser("coverage", help="run held-out cutoff comparison")
    report.add_argument("--train", action="append", required=True, type=Path)
    report.add_argument("--held-out", required=True, type=Path)
    report.add_argument(
        "--cutoffs", type=coverage.parse_cutoffs, default=DEFAULT_CUTOFFS
    )
    report.add_argument("--max-gap-pp", type=_nonnegative_float, required=True)
    add_language(report)

    audit = commands.add_parser("query-audit", help="audit recorded prefix candidates")
    audit.add_argument("--asset", required=True, type=Path)
    audit.add_argument("--queries", required=True, type=Path)
    audit.add_argument("--review", type=Path)
    audit.add_argument("--top", type=_positive_int, default=3)
    audit.add_argument("--reviewer", default=AUTOMATED_REVIEWER)
    audit.add_argument("--review-date", default=AUTOMATED_REVIEW_DATE)
    add_language(audit)
    return parser


def _print_json(value: object, stream: TextIO = sys.stdout) -> None:
    json.dump(value, stream, ensure_ascii=False, indent=2, sort_keys=True)
    stream.write("\n")


def main(argv: Sequence[str] | None = None) -> int:
    args = create_argument_parser().parse_args(argv)
    language = coverage.language_for(args.language)
    try:
        if args.command == "build":
            built = build_dictionary(args.inputs, args.count, language)
            _write_outputs(args.raw_output, built.raw, args.asset_output, built.asset)
            result = summary(built.asset, built.parsed)
            result["boundary_frequency"] = built.boundary_frequency
            result["language"] = language.tag
            _print_json(result)
        elif args.command == "validate":
            if args.raw is not None:
                raw = args.raw.read_bytes()
                parsed = validate_raw(raw, args.expected_count, language)
                result = {
                    "entry_count": parsed.entry_count,
                    "format_version": FORMAT_VERSION,
                    "language": language.tag,
                    "raw_sha256": hashlib.sha256(raw).hexdigest(),
                    "schema_id": SCHEMA_ID,
                    "uncompressed_bytes": len(raw),
                }
            else:
                asset = args.asset.read_bytes()
                parsed = validate_asset(asset, args.expected_count, language)
                raw = parsed.raw
                result = summary(asset, parsed)
                result["language"] = language.tag
            if args.raw_output is not None:
                staged = _stage_file(args.raw_output, raw)
                os.replace(staged, args.raw_output)
            _print_json(result)
        elif args.command == "coverage":
            result = build_coverage_report(
                args.train, args.held_out, args.cutoffs, language=language
            )
            if result["coverage_gap_pp"] > args.max_gap_pp:
                raise DictionaryQualityError(
                    f"100k trails 150k by {result['coverage_gap_pp']:.6f} pp; "
                    f"limit is {args.max_gap_pp:.6f} pp"
                )
            _print_json(result)
        elif args.command == "query-audit":
            dictionary = validate_asset(args.asset.read_bytes(), language=language)
            rows = _audit_rows(
                dictionary,
                _read_queries(args.queries, language),
                args.top,
                language,
            )
            if args.review is not None:
                _check_review(rows, args.review, args.reviewer, args.review_date)
            writer = csv.DictWriter(
                sys.stdout,
                fieldnames=["prefix", "candidates"],
                delimiter="\t",
                lineterminator="\n",
            )
            writer.writeheader()
            writer.writerows(rows)
        else:  # pragma: no cover
            raise AssertionError(args.command)
    except DictionaryQualityError as error:
        print(f"error: {error}", file=sys.stderr)
        return 5
    except DictionaryBudgetError as error:
        print(f"error: {error}", file=sys.stderr)
        return 4
    except DictionaryFormatError as error:
        print(f"error: {error}", file=sys.stderr)
        return 3
    except (
        DictionaryInputError,
        coverage.MalformedRowError,
        coverage.NoUsableWordsError,
        OSError,
        UnicodeError,
    ) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
