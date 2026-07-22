#!/usr/bin/env python3

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import io
import json
import re
import struct
import subprocess
import sys
import tempfile
import unittest
import zlib
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FIXTURES = Path(__file__).resolve().parent / "fixtures"
COVERAGE_SCRIPT = ROOT / "scripts" / "dictionary_coverage.py"
PACK_SCRIPT = ROOT / "scripts" / "dictionary_pack.py"
ASSET = (
    ROOT
    / "app"
    / "src"
    / "main"
    / "assets"
    / "dictionaries"
    / "tatar_top100k_v1.tdict.zlib"
)
PROVENANCE = ROOT / "docs" / "DICTIONARY-D1A.md"
REVIEW = ROOT / "docs" / "DICTIONARY-D1A-QUERY-REVIEW.tsv"
NOTICE = ASSET.parent / "NOTICE.txt"


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


coverage = load_module("dictionary_coverage", COVERAGE_SCRIPT)
pack = load_module("dictionary_pack", PACK_SCRIPT)


def unchecked_raw(entries: list[tuple[str | bytes, int]]) -> bytes:
    encoded = [
        word if isinstance(word, bytes) else word.encode("utf-8")
        for word, _ in entries
    ]
    offsets = [0]
    for word in encoded:
        offsets.append(offsets[-1] + len(word))
    count = len(entries)
    offset_index_offset = pack.HEADER_SIZE
    frequencies_offset = offset_index_offset + 4 * (count + 1)
    word_blob_offset = frequencies_offset + 4 * count
    blob = b"".join(encoded)
    file_size = word_blob_offset + len(blob)
    header = pack.HEADER.pack(
        pack.MAGIC,
        pack.SCHEMA_ID,
        pack.FORMAT_VERSION,
        pack.HEADER_SIZE,
        pack.CHECKSUM_ALGORITHM_SHA256,
        count,
        offset_index_offset,
        frequencies_offset,
        word_blob_offset,
        len(blob),
        file_size,
        bytes(pack.CHECKSUM_SIZE),
    )
    raw = (
        header
        + struct.pack(f"<{count + 1}I", *offsets)
        + struct.pack(f"<{count}I", *(frequency for _, frequency in entries))
        + blob
    )
    digest = hashlib.sha256(raw).digest()
    return raw[: pack.CHECKSUM_OFFSET] + digest + raw[pack.HEADER_SIZE :]


def replace_field(raw: bytes, offset: int, fmt: str, value: int) -> bytes:
    changed = bytearray(raw)
    struct.pack_into(fmt, changed, offset, value)
    return bytes(changed)


def rechecksum(raw: bytes) -> bytes:
    changed = bytearray(raw)
    changed[pack.CHECKSUM_OFFSET : pack.HEADER_SIZE] = bytes(pack.CHECKSUM_SIZE)
    digest = hashlib.sha256(changed).digest()
    changed[pack.CHECKSUM_OFFSET : pack.HEADER_SIZE] = digest
    return bytes(changed)


class DictionaryPackTest(unittest.TestCase):
    def fixture_paths(self) -> list[Path]:
        return [
            FIXTURES / "synthetic_a-words.txt",
            FIXTURES / "synthetic_b-words.txt",
        ]

    def test_d0_normalization_is_reused(self) -> None:
        frequencies = pack._read_frequencies(self.fixture_paths())
        self.assertEqual(frequencies["әпә"], 13)
        self.assertEqual(frequencies["йорт"], 10)
        self.assertNotIn("йорт", frequencies)
        self.assertNotIn("latin", frequencies)
        self.assertIs(pack.coverage.normalize_word, coverage.normalize_word)

    def test_frequency_selection_and_tie_break(self) -> None:
        selected, boundary = pack.select_entries(
            Counter({"юл": 4, "алма": 4, "абага": 4, "әпә": 10}), 3
        )
        self.assertEqual(boundary, 4)
        self.assertEqual(selected, [("абага", 4), ("алма", 4), ("әпә", 10)])

    def test_selected_slice_is_resorted_lexically(self) -> None:
        selected, _ = pack.select_entries(
            Counter({"юл": 100, "ә": 50, "алма": 1}), 3
        )
        self.assertEqual([word for word, _ in selected], ["алма", "юл", "ә"])

    def test_utf8_byte_order_matches_codepoint_order(self) -> None:
        words = ["а", "ә", "б", "е", "ё", "ж", "җ", "н", "ң", "о", "ө", "у", "ү"]
        self.assertEqual(
            sorted(words), sorted(words, key=lambda word: word.encode("utf-8"))
        )

    def test_schema_v1_exact_layout(self) -> None:
        raw = pack.serialize_entries([("алма", 9), ("юл", 3), ("ә", 1)])
        fields = pack.HEADER.unpack_from(raw)
        self.assertEqual(fields[0], b"TATDICT\0")
        self.assertEqual(fields[1:5], (1, 1, 72, 1))
        count = fields[5]
        self.assertEqual(count, 3)
        self.assertEqual(fields[6], 72)
        self.assertEqual(fields[7], 72 + 4 * (count + 1))
        self.assertEqual(fields[8], fields[7] + 4 * count)
        self.assertEqual(fields[10], len(raw))
        parsed = pack.validate_raw(raw)
        self.assertEqual(parsed.words, ("алма", "юл", "ә"))
        self.assertEqual(parsed.frequencies, (9, 3, 1))

    def test_sha256_covers_header_and_payload(self) -> None:
        valid = pack.serialize_entries([("алма", 2), ("юл", 1)])
        for offset in (0, 8, 16, pack.CHECKSUM_OFFSET, pack.HEADER_SIZE, len(valid) - 1):
            with self.subTest(offset=offset):
                corrupt = bytearray(valid)
                corrupt[offset] ^= 1
                with self.assertRaises(pack.DictionaryFormatError):
                    pack.validate_raw(bytes(corrupt))

    def test_golden_raw_and_zlib_bytes(self) -> None:
        built = pack.build_dictionary(self.fixture_paths(), 10)
        self.assertEqual(built.raw, (FIXTURES / "golden-v1.tdict").read_bytes())
        self.assertEqual(
            built.asset, (FIXTURES / "golden-v1.tdict.zlib").read_bytes()
        )

    def test_repeated_and_permuted_builds_are_identical(self) -> None:
        first = pack.build_dictionary(self.fixture_paths(), 10)
        second = pack.build_dictionary(self.fixture_paths(), 10)
        reversed_inputs = pack.build_dictionary(list(reversed(self.fixture_paths())), 10)
        self.assertEqual(first.raw, second.raw)
        self.assertEqual(first.asset, second.asset)
        self.assertEqual(first.raw, reversed_inputs.raw)
        self.assertEqual(first.asset, reversed_inputs.asset)

    def test_prefix_search_and_ranking(self) -> None:
        parsed = pack.build_dictionary(self.fixture_paths(), 13).parsed
        self.assertEqual(
            pack.prefix_candidates(parsed, "бал", 3),
            [("бала", 10), ("балан", 9), ("балчык", 9)],
        )
        self.assertNotIn(("бал", 5), pack.prefix_candidates(parsed, "бал", 10))
        self.assertIn(("балалар", 4), pack.prefix_candidates(parsed, "бала", 10))

    def test_malformed_input_classes_fail_closed(self) -> None:
        bad_rows = (
            "too\tmany\tfields\there\n",
            "0\tсәлам\t1\n",
            "x\tсәлам\t1\n",
            "+1\tсәлам\t1\n",
            "1_0\tсәлам\t1\n",
            "١\tсәлам\t1\n",
            " 1\tсәлам\t1\n",
            "1 \tсәлам\t1\n",
            "1\tсәлам\tzero\n",
            "1\tсәлам\t0\n",
            "сәлам\t-1\n",
            "сәлам\t+1\n",
            "сәлам\t1_0\n",
            "сәлам\t١\n",
            "сәлам\t 1\n",
            "сәлам\t1 \n",
        )
        for row in bad_rows:
            with self.subTest(row=row), self.assertRaises(coverage.MalformedRowError):
                coverage.read_source(
                    io.StringIO(row), "synthetic", pack.CheckedFrequencyCounter(),
                    skip_malformed=False,
                )

    def test_coverage_gap_threshold_must_be_finite_and_nonnegative(self) -> None:
        self.assertEqual(pack._nonnegative_float("0"), 0.0)
        self.assertEqual(pack._nonnegative_float("1.0"), 1.0)
        for value in ("nan", "NaN", "inf", "+inf", "-inf", "-0.1"):
            with self.subTest(value=value), self.assertRaises(
                argparse.ArgumentTypeError
            ):
                pack._nonnegative_float(value)

    def test_no_usable_words_and_short_cutoff_fail(self) -> None:
        with self.assertRaises(pack.DictionaryInputError):
            pack.build_dictionary([FIXTURES / "filtered-only-words.txt"], 1)
        with self.assertRaises(pack.DictionaryInputError):
            pack.build_dictionary(self.fixture_paths(), 100)

    def test_u32_boundary_and_overflow(self) -> None:
        raw = pack.serialize_entries([("ә", pack.MAX_U32)])
        self.assertEqual(pack.validate_raw(raw).frequencies, (pack.MAX_U32,))
        with self.assertRaises(pack.DictionaryInputError):
            pack.serialize_entries([("ә", pack.MAX_U32 + 1)])
        counter = pack.CheckedFrequencyCounter()
        counter["ә"] = pack.MAX_U32
        with self.assertRaises(pack.DictionaryInputError):
            counter["ә"] += 1
        with self.assertRaises(pack.DictionaryInputError):
            coverage.read_source(
                io.StringIO(f"ә\t{pack.MAX_U32}\nә\t1\n"),
                "overflow",
                pack.CheckedFrequencyCounter(),
                skip_malformed=False,
            )

    def test_serializer_rejects_duplicate_and_unsorted(self) -> None:
        with self.assertRaisesRegex(pack.DictionaryInputError, "duplicate"):
            pack.serialize_entries([("алма", 2), ("алма", 1)])
        with self.assertRaisesRegex(pack.DictionaryInputError, "sorted"):
            pack.serialize_entries([("юл", 1), ("алма", 2)])

    def test_validator_rejects_magic_schema_version_and_header(self) -> None:
        valid = unchecked_raw([("алма", 2), ("юл", 1)])
        cases = {
            "short": valid[:20],
            "magic": b"BADMAGIC" + valid[8:],
            "schema": replace_field(valid, 8, "<H", 2),
            "version": replace_field(valid, 10, "<H", 2),
            "header": replace_field(valid, 12, "<H", 70),
            "algorithm": replace_field(valid, 14, "<H", 2),
            "zero_count": replace_field(valid, 16, "<I", 0),
            "count_arithmetic_overflow": replace_field(
                valid, 16, "<I", pack.MAX_U32
            ),
            "offset_section": replace_field(valid, 20, "<I", 76),
            "frequency_section": replace_field(valid, 24, "<I", 88),
            "blob_section": replace_field(valid, 28, "<I", 100),
            "blob_size": replace_field(valid, 32, "<I", 500),
            "file_size": replace_field(valid, 36, "<I", len(valid) + 1),
            "truncated": valid[:-1],
            "trailing": valid + b"x",
        }
        for name, raw in cases.items():
            with self.subTest(name=name), self.assertRaises(pack.DictionaryFormatError):
                pack.validate_raw(raw)

    def test_validator_rejects_bad_offsets(self) -> None:
        valid = unchecked_raw([("алма", 2), ("юл", 1)])
        blob_size = struct.unpack_from("<I", valid, 32)[0]
        cases = {
            "first": replace_field(valid, 72, "<I", 1),
            "nonincreasing": replace_field(valid, 76, "<I", 0),
            "outside": replace_field(valid, 76, "<I", blob_size + 1),
            "terminal": replace_field(valid, 80, "<I", blob_size - 1),
        }
        for name, raw in cases.items():
            with self.subTest(name=name), self.assertRaises(pack.DictionaryFormatError):
                pack.validate_raw(rechecksum(raw))

    def test_validator_rejects_bad_words(self) -> None:
        cases = {
            "invalid_utf8": [(b"\xff", 1)],
            "nfd": [("йорт", 1)],
            "uppercase": [("Сәлам", 1)],
            "alphabet": [("бер-ике", 1)],
            "too_long": [("а" * 65, 1)],
            "duplicate": [("алма", 2), ("алма", 1)],
            "unsorted": [("юл", 1), ("алма", 2)],
        }
        for name, entries in cases.items():
            with self.subTest(name=name), self.assertRaises(pack.DictionaryFormatError):
                pack.validate_raw(unchecked_raw(entries))

    def test_validator_rejects_zero_frequency_and_checksum(self) -> None:
        with self.assertRaisesRegex(pack.DictionaryFormatError, "positive"):
            pack.validate_raw(unchecked_raw([("алма", 0)]))
        valid = bytearray(unchecked_raw([("алма", 1)]))
        valid[-1] ^= 1
        with self.assertRaisesRegex(pack.DictionaryFormatError, "checksum"):
            pack.validate_raw(bytes(valid))

    def test_validator_rejects_bad_zlib_streams(self) -> None:
        valid = pack.compress_raw(unchecked_raw([("алма", 1)]))
        cases = {
            "invalid": b"not-zlib",
            "truncated": valid[:-1],
            "trailing": valid + b"trailing",
            "concatenated": valid + valid,
        }
        for name, asset in cases.items():
            with self.subTest(name=name), self.assertRaises(pack.DictionaryFormatError):
                pack.validate_asset(asset)

    def test_budget_exact_boundaries_and_decompression_bomb(self) -> None:
        with self.assertRaises(pack.DictionaryFormatError):
            pack.validate_raw(b"x" * pack.MAX_UNCOMPRESSED_BYTES)
        with self.assertRaises(pack.DictionaryBudgetError):
            pack.validate_raw(b"x" * (pack.MAX_UNCOMPRESSED_BYTES + 1))
        with self.assertRaises(pack.DictionaryFormatError):
            pack.validate_asset(b"x" * pack.MAX_COMPRESSED_BYTES)
        with self.assertRaises(pack.DictionaryBudgetError):
            pack.validate_asset(b"x" * (pack.MAX_COMPRESSED_BYTES + 1))
        bomb = zlib.compress(b"x" * (pack.MAX_UNCOMPRESSED_BYTES + 1), 9)
        with self.assertRaises(pack.DictionaryBudgetError):
            pack.validate_asset(bomb)

    def test_generator_does_not_publish_on_source_failure(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            raw_path = Path(directory) / "raw"
            asset_path = Path(directory) / "asset"
            raw_path.write_bytes(b"old raw")
            asset_path.write_bytes(b"old asset")
            result = subprocess.run(
                [
                    sys.executable, str(PACK_SCRIPT), "build", "--count", "1",
                    "--raw-output", str(raw_path), "--asset-output", str(asset_path),
                    str(FIXTURES / "malformed-words.txt"),
                ],
                check=False, capture_output=True, text=True,
            )
            self.assertEqual(result.returncode, 2)
            self.assertEqual(raw_path.read_bytes(), b"old raw")
            self.assertEqual(asset_path.read_bytes(), b"old asset")

    def test_cli_exit_codes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            raw = path / "bad.raw"
            raw.write_bytes(b"bad")
            result = subprocess.run(
                [sys.executable, str(PACK_SCRIPT), "validate", "--raw", str(raw)],
                check=False, capture_output=True, text=True,
            )
            self.assertEqual(result.returncode, 3)
            oversized = path / "oversized.raw"
            oversized.write_bytes(b"x" * (pack.MAX_UNCOMPRESSED_BYTES + 1))
            result = subprocess.run(
                [sys.executable, str(PACK_SCRIPT), "validate", "--raw", str(oversized)],
                check=False, capture_output=True, text=True,
            )
            self.assertEqual(result.returncode, 4)

            invalid_utf8 = path / "invalid-utf8-words.txt"
            invalid_utf8.write_bytes(b"1\t\xff\t1\n")
            result = subprocess.run(
                [
                    sys.executable, str(PACK_SCRIPT), "build", "--count", "1",
                    "--raw-output", str(path / "raw"),
                    "--asset-output", str(path / "asset"), str(invalid_utf8),
                ],
                check=False, capture_output=True, text=True,
            )
            self.assertEqual(result.returncode, 2)

    def test_held_out_uses_disjoint_source_and_threshold(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            train = root / "train.txt"
            held = root / "held.txt"
            train.write_text("алма\t10\nюл\t9\nөй\t8\nәти\t7\n", encoding="utf-8")
            held.write_text("алма\t4\nюл\t3\nөй\t2\nәти\t1\n", encoding="utf-8")
            report = pack.build_coverage_report(
                [train], held, (1, 3, 4), comparison=(3, 4)
            )
        self.assertEqual(report["training_unique_words"], 4)
        self.assertEqual(report["held_out_accepted_tokens"], 10)
        self.assertAlmostEqual(report["coverage_gap_pp"], 10.0)

    def test_query_review_fails_closed_on_rejected_or_changed_candidate(self) -> None:
        parsed = pack.build_dictionary(self.fixture_paths(), 13).parsed
        rows = pack._audit_rows(parsed, ["бал"], 3)
        with tempfile.TemporaryDirectory() as directory:
            review = Path(directory) / "review.tsv"
            review.write_text(
                "prefix\tcandidates\tclassification\treviewer\treview_date\tnote\n"
                f"бал\t{rows[0]['candidates']}\tfail\t{pack.AUTOMATED_REVIEWER}\t"
                f"{pack.AUTOMATED_REVIEW_DATE}\tundecidable\n",
                encoding="utf-8",
            )
            with self.assertRaises(pack.DictionaryQualityError):
                pack._check_review(rows, review)
            review.write_text(
                "prefix\tcandidates\tclassification\treviewer\treview_date\tnote\n"
                f"бал\tchanged\tpass\t{pack.AUTOMATED_REVIEWER}\t"
                f"{pack.AUTOMATED_REVIEW_DATE}\trecognizable Tatar forms\n",
                encoding="utf-8",
            )
            with self.assertRaises(pack.DictionaryQualityError):
                pack._check_review(rows, review)

    def test_committed_asset_provenance_notice_and_review(self) -> None:
        asset = ASSET.read_bytes()
        parsed = pack.validate_asset(asset, expected_count=100_000)
        asset_sha = hashlib.sha256(asset).hexdigest()
        raw_sha = hashlib.sha256(parsed.raw).hexdigest()
        provenance = PROVENANCE.read_text(encoding="utf-8")
        notice = NOTICE.read_text(encoding="utf-8")
        self.assertIn(asset_sha, provenance)
        self.assertIn(raw_sha, provenance)
        self.assertIn(str(len(asset)), provenance)
        self.assertIn(str(len(parsed.raw)), provenance)
        self.assertIn("CC BY 4.0", provenance)
        self.assertIn("CC BY 4.0", notice)
        rows = pack._audit_rows(
            parsed, pack._read_queries(FIXTURES / "manual_tatar_queries.txt"), 3
        )
        pack._check_review(rows, REVIEW)

    def test_no_licensed_sources_are_tracked(self) -> None:
        result = subprocess.run(
            ["git", "ls-files"], cwd=ROOT, check=True, capture_output=True, text=True
        )
        pattern = re.compile(
            r"(^|/)(tat_(mixed|news|web).*-words\.txt|.*\.tar\.gz)$"
        )
        self.assertFalse(any(pattern.search(path) for path in result.stdout.splitlines()))


if __name__ == "__main__":
    unittest.main()
