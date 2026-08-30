"""Shared measurement helpers for the tt-corpus mission.

Nothing here touches app/src/main/assets. Every artefact this module builds is written
under research/corpus/out/ and is a TEST artefact by construction.

Two token rules live here on purpose, because the shipped pipelines use two:

* ``dict_tokens``  mirrors Leipzig ``*-words.txt`` semantics: surrounding punctuation is
  stripped before ``normalize_word``, because Leipzig's own tokenizer already stripped it
  when it produced the word lists the shipped dictionary was built from. Counting raw
  whitespace tokens instead would silently discard ~20 % of every sentence corpus and make
  the conversational frequencies incomparable with the shipped ones.
* ``bigram_tokens`` mirrors the E5a adjacency rule verbatim: split on whitespace, a token
  rejected by ``normalize_word`` BREAKS adjacency rather than being transparent. This is the
  rule tt-bigram-adjacency re-confirmed, so the numbers stay comparable with the shipped
  table.
"""
from __future__ import annotations

import gzip
import re
import sys
import unicodedata
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))

import dictionary_coverage as cov  # noqa: E402
import dictionary_pack as dp  # noqa: E402

OUT = Path(__file__).resolve().parent / "out"
CORPUS_DIR = Path(__file__).resolve().parent

SHIPPED = {
    "tat": ROOT / "app/src/main/assets/dictionaries/tatar_top100k_v1.tdict.zlib",
    "rus": ROOT / "app/src/main/assets/dictionaries/russian_top100k_v1.tdict.zlib",
}

# Characters that may legitimately hug a word inside a subtitle or a Tatoeba sentence.
_EDGE = "\"'«»„“”‘’()[]{}<>.,!?;:…—–-*_/\\|~`^&#№%+=@$"


def load_shipped(tag: str) -> tuple[dict[str, int], int]:
    """Return {word: frequency} of the SHIPPED asset and its boundary frequency."""
    lang = cov.language_for(tag)
    raw = dp.decompress_asset(SHIPPED[tag].read_bytes(), lang)
    parsed = dp.validate_raw(raw, language=lang)
    freqs = dict(zip(parsed.words, parsed.frequencies))
    return freqs, min(parsed.frequencies)


def open_text(path: Path):
    if path.suffix == ".gz":
        return gzip.open(path, "rt", encoding="utf-8", errors="replace", newline="")
    return open(path, "rt", encoding="utf-8", errors="replace", newline="")


def dict_tokens(line: str, alphabet):
    """Yield normalized words, stripping punctuation that hugs a token (Leipzig semantics)."""
    for chunk in line.split():
        word = chunk.strip(_EDGE)
        if not word:
            continue
        norm, _ = cov.normalize_word(word, alphabet)
        if norm is not None:
            yield norm


def bigram_tokens(line: str, alphabet):
    """Yield (word_or_None) per whitespace token; None means adjacency BREAKS here (E5a rule)."""
    for chunk in line.split():
        norm, _ = cov.normalize_word(chunk, alphabet)
        yield norm
