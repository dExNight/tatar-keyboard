#!/usr/bin/env python3
"""E5a: measure the size and the usefulness of a Tatar bigram table before any Android code.

The phase gate is decided by numbers this script produces, so every rule it applies is the rule
written in PROPOSALS.md ("## E5"), not a convenient approximation:

* pairs come from ``*-sentences.txt`` of the three Leipzig archives already pinned in
  docs/DICTIONARY-D1A.md — the ready-made ``*-co_n.txt`` is deliberately NOT used, because its
  weight semantics were never verified offline while these rules can be checked by reading;
* a sentence row is EXACTLY two tab-separated fields, the first a positive decimal id; anything
  else stops the generation with a non-zero exit instead of being skipped in silence;
* tokens are split on runs of whitespace, and a token rejected by ``normalize_word`` BREAKS
  adjacency rather than being transparent — otherwise "х , у" would produce the pair (х, у)
  across punctuation and the table would be trained on events the runtime is forbidden to show;
* both halves of a pair must be in the SHIPPED top-100k, read through
  ``dictionary_pack.decompress_asset`` + ``validate_raw``: one proven source for both the word
  list and the unigram frequencies, so nothing can drift from the shipped artifact in silence;
* training is ``tat_mixed`` + ``tat_web``; ``tat_news`` is held out in full.

Multilingual since 2026-08-21 (`docs/RUSSIAN-BIGRAMS.md`): ``--language`` picks the alphabet the
tokenizer and the shipped-vocabulary read apply, and every entry point defaults to Tatar, so a
caller written before this ran behaves exactly as it did — same tokens, same filtering, same
bytes.

Two independent caps are enforced by the generator itself, not only by the phase acceptance:
compressed <= 250 000 B and raw <= 1 048 576 B per language. The raw cap binds first and is what
limits the matrix.

Usage (the corpora are downloaded by a human — agents have no network):

    python3 scripts/bigram_pack.py matrix \\
        --train tat_mixed_2015_1M-sentences.txt tat_web_2018_1M-sentences.txt \\
        --holdout tat_news_2015_1M-sentences.txt \\
        --asset app/src/main/assets/<shipped .tdict asset> \\
        --report docs/DICTIONARY-E5A.generated.json
"""

from __future__ import annotations

import argparse
import hashlib
import json
import resource
import sys
import time
import zlib
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Iterator, Sequence, TextIO

sys.path.insert(0, str(Path(__file__).resolve().parent))

import dictionary_coverage as coverage  # noqa: E402
from dictionary_coverage import normalize_word  # noqa: E402
from dictionary_pack import decompress_asset, validate_raw  # noqa: E402

# The two caps of the early gate, both binding, both wired into the generator.
MAX_COMPRESSED_BYTES = 250_000
MAX_RAW_BYTES = 1_048_576

# The matrix: H heads by unigram frequency, K successes per head, without the top corner.
HEAD_COUNTS = (8_000, 10_000)
SUCCESSES_PER_HEAD = (4, 6, 8, 10)
EXCLUDED_CORNER = (10_000, 10)

# Successes kept per head while counting. Exact for every K in the matrix; anything beyond the
# largest K can never enter a shipped table, so it is dropped as soon as a shard pass completes.
KEPT_SUCCESSES_PER_HEAD = 32

# Header of the schema-2 file, from the size formula in PROPOSALS.md ("Бюджет размера APK").
HEADER_BYTES = 96

# zlib settings — the same mode as D1a, so compressed sizes are comparable across artifacts.
COMPRESSION_LEVEL = 9
COMPRESSION_WBITS = 15
COMPRESSION_MEM_LEVEL = 9


class BigramInputError(ValueError):
    """A malformed input that must stop the generation rather than be skipped."""


@dataclass
class CorpusStats:
    """What one corpus contributed, including the loss the tokenizer rule causes."""

    path: str
    sha256: str
    sentences: int = 0
    tokens: int = 0
    tokens_rejected: int = 0
    pairs: int = 0

    @property
    def rejected_share(self) -> float:
        return 0.0 if self.tokens == 0 else self.tokens_rejected / self.tokens

    def to_dict(self) -> dict[str, object]:
        return {
            "path": self.path,
            "sha256": self.sha256,
            "sentences": self.sentences,
            "tokens": self.tokens,
            "tokens_rejected": self.tokens_rejected,
            "rejected_share": round(self.rejected_share, 6),
            "pairs": self.pairs,
        }


@dataclass
class Configuration:
    """One cell of the H x K matrix, with everything the report has to carry."""

    heads: int
    successes_per_head: int
    pair_count: int = 0
    success_vocabulary: int = 0
    raw_bytes: int = 0
    compressed_bytes: int = 0
    conditional_hit_rate: float = 0.0
    unconditional_hit_rate: float = 0.0
    events_with_prediction_share: float = 0.0
    events: int = 0
    passes_raw_cap: bool = False
    passes_compressed_cap: bool = False

    @property
    def name(self) -> str:
        return f"H={self.heads} K={self.successes_per_head}"

    def to_dict(self) -> dict[str, object]:
        return {
            "heads": self.heads,
            "successes_per_head": self.successes_per_head,
            "pairs": self.pair_count,
            "success_vocabulary": self.success_vocabulary,
            "raw_bytes": self.raw_bytes,
            "compressed_bytes": self.compressed_bytes,
            "conditional_top3_hit_rate": round(self.conditional_hit_rate, 6),
            "unconditional_top3_hit_rate": round(self.unconditional_hit_rate, 6),
            "events_with_prediction_share": round(self.events_with_prediction_share, 6),
            "events": self.events,
            "passes_raw_cap": self.passes_raw_cap,
            "passes_compressed_cap": self.passes_compressed_cap,
        }


def sha256_of(path: Path) -> str:
    """The pin required by the contract: no ``sentences.txt`` hash is recorded anywhere yet."""
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def parse_sentence_row(line: str, source: str, line_number: int) -> str:
    """Return the sentence text of one row, or raise.

    The rule is written out rather than borrowed: ``dictionary_coverage.parse_row`` understands
    only the id/word/frequency shape of ``words.txt`` and raises on a sentence line, so this
    parser is new — and a new parser is exactly the kind of thing that quietly changes what
    "reproducible from a documented input" means.
    """
    fields = line.rstrip("\n").split("\t")
    if len(fields) != 2:
        raise BigramInputError(
            f"{source}:{line_number}: expected exactly two tab-separated fields, got {len(fields)}"
        )
    identifier, sentence = fields
    if not identifier or any(character < "0" or character > "9" for character in identifier):
        raise BigramInputError(
            f"{source}:{line_number}: sentence id must contain only ASCII decimal digits"
        )
    if int(identifier) <= 0:
        raise BigramInputError(f"{source}:{line_number}: sentence id must be positive")
    return sentence


def iter_sentences(path: Path) -> Iterator[str]:
    with path.open("r", encoding="utf-8", errors="strict") as stream:
        for line_number, line in enumerate(stream, start=1):
            yield parse_sentence_row(line, path.name, line_number)


def normalized_tokens(
    sentence: str, alphabet: frozenset[str] = coverage.TATAR_ALPHABET
) -> list[str | None]:
    """Tokens of one sentence, with ``None`` wherever ``normalize_word`` rejected the token.

    The ``None`` is the whole point: it is what breaks adjacency. Cleaning "сүз," down to "сүз"
    is NOT done — ``normalize_word`` rejects a token whole, which systematically loses the last
    word of every clause, and that bias is recorded in the report instead of being papered over.

    ``alphabet`` is the language's own; it defaults to Tatar so callers older than the second
    language keep their exact behaviour. It matters for more than tidiness: Tatar's alphabet is a
    strict superset of Russian's, so tokenizing Russian text with it would let a stray Tatar
    letter through as a token instead of breaking adjacency there.
    """
    result: list[str | None] = []
    for raw_token in sentence.split():
        normalized, _reason = normalize_word(raw_token, alphabet)
        result.append(normalized)
    return result


def iter_pairs(tokens: Sequence[str | None], vocabulary: frozenset[str]) -> Iterator[tuple[str, str]]:
    """Adjacent in-vocabulary pairs of one sentence, self-pairs dropped."""
    for index in range(len(tokens) - 1):
        head = tokens[index]
        success = tokens[index + 1]
        if head is None or success is None:
            continue
        if head not in vocabulary or success not in vocabulary:
            continue
        if head == success:
            continue
        yield head, success


def read_shipped_vocabulary(
    asset_path: Path, language: coverage.Language = coverage.DEFAULT_LANGUAGE
) -> tuple[frozenset[str], dict[str, int]]:
    """The word list AND the unigram frequencies, both from the shipped artifact."""
    parsed = validate_raw(decompress_asset(asset_path.read_bytes()), language=language)
    frequencies = {word: frequency for word, frequency in zip(parsed.words, parsed.frequencies)}
    return frozenset(parsed.words), frequencies


def select_heads(frequencies: dict[str, int], count: int) -> list[str]:
    """The ``count`` most frequent words, ties broken by code point ascending (as everywhere)."""
    ordered = sorted(frequencies.items(), key=lambda item: (-item[1], item[0]))
    return [word for word, _frequency in ordered[:count]]


def count_pairs(
    paths: Sequence[Path],
    heads: frozenset[str],
    vocabulary: frozenset[str],
    shards: int,
    stats: list[CorpusStats],
    alphabet: frozenset[str] = coverage.TATAR_ALPHABET,
) -> dict[str, list[tuple[str, int]]]:
    """Count pairs for every head, one shard of heads per pass over the corpora.

    A dictionary of tuples over millions of pairs does not fit in memory on an ordinary machine,
    which is why this is sharded and why peak RSS is part of the report: without it the prototype
    is not reproducible elsewhere.
    """
    table: dict[str, list[tuple[str, int]]] = {}
    for shard in range(shards):
        counts: dict[str, Counter[str]] = {}
        for path in paths:
            corpus = next((entry for entry in stats if entry.path == str(path)), None)
            first_pass = shard == 0
            for sentence in iter_sentences(path):
                tokens = normalized_tokens(sentence, alphabet)
                if corpus is not None and first_pass:
                    corpus.sentences += 1
                    corpus.tokens += len(tokens)
                    corpus.tokens_rejected += sum(1 for token in tokens if token is None)
                for head, success in iter_pairs(tokens, vocabulary):
                    if head not in heads or hash(head) % shards != shard:
                        continue
                    counts.setdefault(head, Counter())[success] += 1
                    if corpus is not None and first_pass:
                        corpus.pairs += 1
        for head, successes in counts.items():
            table[head] = sorted(
                successes.items(), key=lambda item: (-item[1], item[0])
            )[:KEPT_SUCCESSES_PER_HEAD]
    return table


def blob_bytes(words: Iterable[str]) -> int:
    """Measured UTF-8 size of a word blob — the estimate of 17.42 B/word is replaced by fact."""
    return sum(len(word.encode("utf-8")) for word in words)


def raw_size(heads: Sequence[str], pair_count: int, successes: Sequence[str]) -> int:
    """The documented schema-2 layout: 96 + 8*(H+1) + head blob + 4*P + 4*(V+1) + success blob."""
    return (
        HEADER_BYTES
        + 8 * (len(heads) + 1)
        + blob_bytes(heads)
        + 4 * pair_count
        + 4 * (len(successes) + 1)
        + blob_bytes(successes)
    )


def compress(raw: bytes) -> bytes:
    compressor = zlib.compressobj(
        level=COMPRESSION_LEVEL,
        method=zlib.DEFLATED,
        wbits=COMPRESSION_WBITS,
        memLevel=COMPRESSION_MEM_LEVEL,
        strategy=zlib.Z_DEFAULT_STRATEGY,
    )
    return compressor.compress(raw) + compressor.flush(zlib.Z_FINISH)


def serialize_table(
    heads: Sequence[str], table: dict[str, list[tuple[str, int]]], successes_per_head: int
) -> tuple[bytes, int, list[str]]:
    """A byte image of the table whose SIZE is what the gate cares about.

    The shape follows the documented layout closely enough for the compressed number to mean
    something: head blob, per-head success id runs, success blob. No explicit weight byte — the
    order of successes is fixed at packing time and the rank is implied by position.
    """
    success_vocabulary: dict[str, int] = {}
    pair_ids: list[int] = []
    offsets: list[int] = [0]
    for head in heads:
        for success, _count in table.get(head, ())[:successes_per_head]:
            pair_ids.append(success_vocabulary.setdefault(success, len(success_vocabulary)))
        offsets.append(len(pair_ids))

    parts = [b"\0" * HEADER_BYTES]
    parts.append(b"".join(offset.to_bytes(8, "little") for offset in offsets))
    parts.append("".join(heads).encode("utf-8"))
    parts.append(b"".join(identifier.to_bytes(4, "little") for identifier in pair_ids))
    ordered_successes = sorted(success_vocabulary, key=success_vocabulary.get)
    parts.append(
        b"".join(
            offset.to_bytes(4, "little")
            for offset in range(len(ordered_successes) + 1)
        )
    )
    parts.append("".join(ordered_successes).encode("utf-8"))
    return b"".join(parts), len(pair_ids), ordered_successes


@dataclass
class HeldOutTally:
    """Counters of one configuration over the held-out corpus."""

    events: int = 0
    events_with_prediction: int = 0
    hits: int = 0


def evaluate(
    holdout_paths: Sequence[Path],
    vocabulary: frozenset[str],
    table: dict[str, list[tuple[str, int]]],
    head_rank: dict[str, int],
    configurations: Sequence[Configuration],
    stats: list[CorpusStats],
    alphabet: frozenset[str] = coverage.TATAR_ALPHABET,
) -> None:
    """One pass over the held-out corpus, all seven configurations tallied at once.

    The denominator is the one frozen in the contract BEFORE this ran, and it is the runtime rule:
    every position inside a held-out sentence whose PREVIOUS token passed ``normalize_word`` and
    stands in the same sentence. Sentence starts and positions right after punctuation are not
    events at all (a rejected token breaks adjacency, so they drop out by construction). An event
    with no prediction available counts as a MISS, which is what makes the number unconditional.
    """
    tallies = [HeldOutTally() for _ in configurations]
    for path in holdout_paths:
        corpus = next((entry for entry in stats if entry.path == str(path)), None)
        for sentence in iter_sentences(path):
            tokens = normalized_tokens(sentence, alphabet)
            if corpus is not None:
                corpus.sentences += 1
                corpus.tokens += len(tokens)
                corpus.tokens_rejected += sum(1 for token in tokens if token is None)
            for index in range(len(tokens) - 1):
                head = tokens[index]
                if head is None:
                    continue
                target = tokens[index + 1]
                successes = table.get(head, ())
                rank = next(
                    (
                        position
                        for position, (word, _count) in enumerate(successes)
                        if word == target
                    ),
                    None,
                )
                head_position = head_rank.get(head)
                for tally, configuration in zip(tallies, configurations):
                    tally.events += 1
                    if head_position is None or head_position >= configuration.heads:
                        continue
                    available = min(configuration.successes_per_head, len(successes))
                    if available == 0:
                        continue
                    tally.events_with_prediction += 1
                    if rank is not None and rank < min(3, available):
                        tally.hits += 1

    for tally, configuration in zip(tallies, configurations):
        configuration.events = tally.events
        configuration.unconditional_hit_rate = (
            0.0 if tally.events == 0 else tally.hits / tally.events
        )
        configuration.conditional_hit_rate = (
            0.0
            if tally.events_with_prediction == 0
            else tally.hits / tally.events_with_prediction
        )
        configuration.events_with_prediction_share = (
            0.0 if tally.events == 0 else tally.events_with_prediction / tally.events
        )


def matrix_configurations() -> list[Configuration]:
    return [
        Configuration(heads=heads, successes_per_head=successes)
        for heads in HEAD_COUNTS
        for successes in SUCCESSES_PER_HEAD
        if (heads, successes) != EXCLUDED_CORNER
    ]


def peak_rss_bytes() -> int:
    """Peak RSS of this process. On Linux ru_maxrss is KiB, on macOS it is bytes."""
    usage = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
    return usage if sys.platform == "darwin" else usage * 1024


def run_matrix(
    train_paths: Sequence[Path],
    holdout_paths: Sequence[Path],
    asset_path: Path,
    shards: int,
    language: coverage.Language = coverage.DEFAULT_LANGUAGE,
) -> dict[str, object]:
    started = time.monotonic()
    vocabulary, frequencies = read_shipped_vocabulary(asset_path, language)
    stats = [
        CorpusStats(path=str(path), sha256=sha256_of(path))
        for path in list(train_paths) + list(holdout_paths)
    ]

    largest_heads = max(HEAD_COUNTS)
    ordered_heads = select_heads(frequencies, largest_heads)
    head_rank = {word: position for position, word in enumerate(ordered_heads)}
    table = count_pairs(
        train_paths, frozenset(ordered_heads), vocabulary, shards, stats, language.alphabet
    )

    configurations = matrix_configurations()
    for configuration in configurations:
        heads = ordered_heads[: configuration.heads]
        image, pair_count, successes = serialize_table(
            heads, table, configuration.successes_per_head
        )
        configuration.pair_count = pair_count
        configuration.success_vocabulary = len(successes)
        configuration.raw_bytes = raw_size(heads, pair_count, successes)
        configuration.compressed_bytes = len(compress(image))
        configuration.passes_raw_cap = configuration.raw_bytes <= MAX_RAW_BYTES
        configuration.passes_compressed_cap = (
            configuration.compressed_bytes <= MAX_COMPRESSED_BYTES
        )

    evaluate(
        holdout_paths,
        vocabulary,
        table,
        head_rank,
        configurations,
        stats,
        language.alphabet,
    )

    return {
        "language": language.tag,
        "corpora": [entry.to_dict() for entry in stats],
        "configurations": [configuration.to_dict() for configuration in configurations],
        "caps": {
            "max_compressed_bytes": MAX_COMPRESSED_BYTES,
            "max_raw_bytes": MAX_RAW_BYTES,
        },
        "peak_rss_bytes": peak_rss_bytes(),
        "elapsed_seconds": round(time.monotonic() - started, 3),
        "shards": shards,
    }


def create_argument_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    matrix = subparsers.add_parser("matrix", help="run the seven H x K configurations")
    matrix.add_argument("--train", nargs="+", required=True, type=Path)
    matrix.add_argument("--holdout", nargs="+", required=True, type=Path)
    matrix.add_argument("--asset", required=True, type=Path)
    matrix.add_argument("--shards", type=int, default=8)
    matrix.add_argument("--report", type=Path)
    matrix.add_argument(
        "--language", default=coverage.DEFAULT_LANGUAGE.tag, choices=sorted(coverage.LANGUAGES)
    )
    return parser


def main(argv: Sequence[str] | None = None, stream: TextIO = sys.stdout) -> int:
    arguments = create_argument_parser().parse_args(argv)
    if arguments.shards < 1:
        raise SystemExit("shards must be positive")
    report = run_matrix(
        arguments.train,
        arguments.holdout,
        arguments.asset,
        arguments.shards,
        coverage.language_for(arguments.language),
    )
    text = json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True)
    if arguments.report is not None:
        arguments.report.write_text(text + "\n", encoding="utf-8")
    print(text, file=stream)
    passing = [
        entry
        for entry in report["configurations"]
        if entry["passes_raw_cap"] and entry["passes_compressed_cap"]
    ]
    if not passing:
        # The caps are enforced here, not only in the phase acceptance: a matrix where every row
        # is over the cap is a failed gate, and it must not look like a successful run.
        print("no configuration fits both caps", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
