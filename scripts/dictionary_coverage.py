#!/usr/bin/env python3
"""Merge Leipzig word-frequency files and report dictionary token coverage.

This tool intentionally uses only the Python standard library.  It accepts the
canonical Leipzig format (numeric id, word, frequency) and the reduced format
(word, frequency), both tab-separated.
"""

from __future__ import annotations

import argparse
import gzip
import json
import sys
import unicodedata
from collections import Counter
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Iterable, Sequence, TextIO


DEFAULT_CUTOFFS = (100_000, 150_000, 250_000)
TATAR_ALPHABET = frozenset("аәбвгдеёжҗзийклмнңоөпрстуүфхһцчшщъыьэюя")
TATAR_SPECIFIC = frozenset("әөүҗңһ")
MAX_WORD_LENGTH = 64


class MalformedRowError(ValueError):
    """A structurally invalid Leipzig row."""


class NoUsableWordsError(ValueError):
    """The inputs contained no word that survived validation and filtering."""


@dataclass
class SourceStats:
    path: str
    rows_read: int = 0
    rows_accepted: int = 0
    rows_malformed: int = 0
    rows_filtered: int = 0
    parsed_tokens: int = 0
    accepted_tokens: int = 0
    filtered_reasons: Counter[str] = field(default_factory=Counter)

    def to_dict(self) -> dict[str, object]:
        result = asdict(self)
        result["filtered_reasons"] = dict(sorted(self.filtered_reasons.items()))
        return result


def normalize_word(raw_word: str) -> tuple[str | None, str | None]:
    """Return a normalized Tatar Cyrillic word, or a filtering reason."""
    word = unicodedata.normalize("NFC", raw_word.strip()).lower()
    if not word:
        return None, "empty_word"
    if len(word) > MAX_WORD_LENGTH:
        return None, "too_long"
    if any(character not in TATAR_ALPHABET for character in word):
        return None, "outside_tatar_alphabet"
    return word, None


def parse_row(line: str, source: str, line_number: int) -> tuple[str, int]:
    """Parse either id<TAB>word<TAB>frequency or word<TAB>frequency."""
    fields = line.rstrip("\r\n").split("\t")
    if len(fields) == 3:
        identifier, word, frequency_text = fields
        try:
            if int(identifier) <= 0:
                raise ValueError
        except ValueError as error:
            raise MalformedRowError(
                f"{source}:{line_number}: id must be a positive integer"
            ) from error
    elif len(fields) == 2:
        word, frequency_text = fields
    else:
        raise MalformedRowError(
            f"{source}:{line_number}: expected 2 or 3 tab-separated fields, "
            f"got {len(fields)}"
        )

    try:
        frequency = int(frequency_text)
    except ValueError as error:
        raise MalformedRowError(
            f"{source}:{line_number}: frequency must be an integer"
        ) from error
    if frequency <= 0:
        raise MalformedRowError(
            f"{source}:{line_number}: frequency must be positive"
        )
    return word, frequency


def read_source(
    stream: TextIO,
    source: str,
    frequencies: Counter[str],
    *,
    skip_malformed: bool,
) -> SourceStats:
    stats = SourceStats(path=source)
    for line_number, line in enumerate(stream, start=1):
        if not line.strip():
            continue
        stats.rows_read += 1
        try:
            raw_word, frequency = parse_row(line, source, line_number)
        except MalformedRowError:
            stats.rows_malformed += 1
            if not skip_malformed:
                raise
            continue

        stats.parsed_tokens += frequency
        word, reason = normalize_word(raw_word)
        if reason is not None:
            stats.rows_filtered += 1
            stats.filtered_reasons[reason] += 1
            continue

        assert word is not None
        stats.rows_accepted += 1
        stats.accepted_tokens += frequency
        frequencies[word] += frequency
    return stats


def sorted_entries(frequencies: Counter[str]) -> list[tuple[str, int]]:
    """Sort deterministically: descending frequency, then Unicode word order."""
    return sorted(frequencies.items(), key=lambda item: (-item[1], item[0]))


def serialized_bytes(entries: Iterable[tuple[str, int]]) -> bytes:
    """Serialize the future-friendly word<TAB>frequency<LF> form as UTF-8."""
    return b"".join(
        f"{word}\t{frequency}\n".encode("utf-8") for word, frequency in entries
    )


def packed_nul_u32_size(entries: Iterable[tuple[str, int]]) -> int:
    """Estimate packed UTF-8 word + NUL + uint32 frequency bytes, without an index."""
    return sum(len(word.encode("utf-8")) + 1 + 4 for word, _ in entries)


def build_report(
    frequencies: Counter[str],
    source_stats: Sequence[SourceStats],
    cutoffs: Sequence[int],
) -> tuple[dict[str, object], list[tuple[str, int]]]:
    entries = sorted_entries(frequencies)
    accepted_tokens = sum(frequencies.values())
    parsed_tokens = sum(stats.parsed_tokens for stats in source_stats)
    cumulative_tokens = 0
    coverage_by_rank: dict[int, int] = {}
    cutoff_set = set(cutoffs)
    for rank, (_, frequency) in enumerate(entries, start=1):
        cumulative_tokens += frequency
        if rank in cutoff_set:
            coverage_by_rank[rank] = cumulative_tokens

    cutoff_reports = []
    for requested_rank in cutoffs:
        selected = min(requested_rank, len(entries))
        covered_tokens = (
            coverage_by_rank[requested_rank]
            if requested_rank <= len(entries)
            else accepted_tokens
        )
        selected_entries = entries[:selected]
        tsv_bytes = serialized_bytes(selected_entries)
        cutoff_reports.append(
            {
                "requested_rank": requested_rank,
                "selected_words": selected,
                "covered_tokens": covered_tokens,
                "token_coverage": covered_tokens / accepted_tokens if accepted_tokens else 0.0,
                "serialized_tsv_bytes": len(tsv_bytes),
                "gzip_tsv_bytes": len(gzip.compress(tsv_bytes, compresslevel=9, mtime=0)),
                "packed_nul_u32_bytes": packed_nul_u32_size(selected_entries),
                "packed_nul_u32_plus_offsets_bytes": (
                    packed_nul_u32_size(selected_entries) + 4 * selected
                ),
                "boundary_frequency": selected_entries[-1][1] if selected_entries else None,
                "boundary_word": selected_entries[-1][0] if selected_entries else None,
            }
        )

    tatar_specific_words = sum(
        1 for word, _ in entries if any(letter in word for letter in TATAR_SPECIFIC)
    )
    tatar_specific_tokens = sum(
        frequency
        for word, frequency in entries
        if any(letter in word for letter in TATAR_SPECIFIC)
    )
    total_accepted_rows = sum(stats.rows_accepted for stats in source_stats)
    report: dict[str, object] = {
        "schema_version": 1,
        "normalization": {
            "unicode": "NFC",
            "case": "Unicode lowercase",
            "alphabet": "Tatar Cyrillic letters only",
            "max_word_length": MAX_WORD_LENGTH,
            "tie_break": "descending frequency, then ascending Unicode word order",
        },
        "sources": [stats.to_dict() for stats in source_stats],
        "totals": {
            "source_count": len(source_stats),
            "rows_read": sum(stats.rows_read for stats in source_stats),
            "rows_accepted": total_accepted_rows,
            "rows_malformed": sum(stats.rows_malformed for stats in source_stats),
            "rows_filtered": sum(stats.rows_filtered for stats in source_stats),
            "parsed_tokens": parsed_tokens,
            "accepted_tokens": accepted_tokens,
            "accepted_token_ratio": accepted_tokens / parsed_tokens if parsed_tokens else 0.0,
            "unique_words": len(entries),
            "duplicates_merged": total_accepted_rows - len(entries),
            "hapax_words": sum(1 for _, frequency in entries if frequency == 1),
            "tatar_specific_words": tatar_specific_words,
            "tatar_specific_tokens": tatar_specific_tokens,
            "tatar_specific_word_ratio": tatar_specific_words / len(entries) if entries else 0.0,
            "tatar_specific_token_ratio": tatar_specific_tokens / accepted_tokens if accepted_tokens else 0.0,
            "all_words_serialized_tsv_bytes": len(serialized_bytes(entries)),
            "all_words_packed_nul_u32_bytes": packed_nul_u32_size(entries),
            "all_words_packed_nul_u32_plus_offsets_bytes": (
                packed_nul_u32_size(entries) + 4 * len(entries)
            ),
        },
        "cutoffs": cutoff_reports,
    }
    return report, entries


def parse_cutoffs(raw_cutoffs: str) -> tuple[int, ...]:
    try:
        cutoffs = tuple(sorted({int(value) for value in raw_cutoffs.split(",")}))
    except ValueError as error:
        raise argparse.ArgumentTypeError("cutoffs must be comma-separated integers") from error
    if not cutoffs or any(cutoff <= 0 for cutoff in cutoffs):
        raise argparse.ArgumentTypeError("cutoffs must be positive")
    return cutoffs


def write_entries(path: Path, entries: Iterable[tuple[str, int]]) -> None:
    with path.open("w", encoding="utf-8", newline="\n") as stream:
        for word, frequency in entries:
            stream.write(f"{word}\t{frequency}\n")


def create_argument_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inputs", nargs="+", type=Path, help="Leipzig *-words.txt files")
    parser.add_argument(
        "--cutoffs",
        type=parse_cutoffs,
        default=DEFAULT_CUTOFFS,
        help="comma-separated ranks (default: 100000,150000,250000)",
    )
    parser.add_argument(
        "--skip-malformed",
        action="store_true",
        help="count and skip malformed rows instead of failing",
    )
    parser.add_argument(
        "--output-words",
        type=Path,
        help="optional local merged word-frequency TSV; do not commit licensed data",
    )
    parser.add_argument(
        "--pretty",
        action="store_true",
        help="pretty-print JSON output",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = create_argument_parser().parse_args(argv)
    frequencies: Counter[str] = Counter()
    sources: list[SourceStats] = []
    try:
        for path in args.inputs:
            with path.open("r", encoding="utf-8-sig", newline="") as stream:
                sources.append(
                    read_source(
                        stream,
                        str(path),
                        frequencies,
                        skip_malformed=args.skip_malformed,
                    )
                )
        if not frequencies:
            raise NoUsableWordsError("inputs contain no usable Tatar words")
        report, entries = build_report(frequencies, sources, args.cutoffs)
        if args.output_words:
            write_entries(args.output_words, entries)
    except (MalformedRowError, NoUsableWordsError, OSError, UnicodeError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2

    json.dump(
        report,
        sys.stdout,
        ensure_ascii=False,
        indent=2 if args.pretty else None,
        sort_keys=True,
    )
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
