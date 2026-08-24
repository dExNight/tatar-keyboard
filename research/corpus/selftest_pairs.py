"""Proof that pairs.count_pairs counts exactly what measure_bigrams.count_pairs counted.

Compares, on the real Tatar corpora and with the real shipped vocabulary: the set of distinct
pairs, every pair's count, the instance total, and the per-head top-K lists that
``top_successors`` produces against a brute-force sort of the reference Counter.
"""
from __future__ import annotations

import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import corpuslib as CL  # noqa: E402
import dictionary_coverage as cov  # noqa: E402
import pairs as P  # noqa: E402
from measure_bigrams import count_pairs as reference_count_pairs  # noqa: E402
from stream import Split, TRAIN  # noqa: E402


def main() -> None:
    tag = sys.argv[1]
    paths = sys.argv[2:]
    alphabet = cov.language_for(tag).alphabet
    shipped, _boundary = CL.load_shipped(tag)
    vocab = set(shipped)

    split = Split(paths)
    lines = [line for line, _ in split.iter_lines(TRAIN)]

    reference = reference_count_pairs(lines, alphabet, vocab)
    streamed = P.count_pairs(lines, alphabet, vocab)

    mine = dict(streamed.as_word_pairs())
    if mine != dict(reference):
        only_reference = set(reference) - set(mine)
        only_mine = set(mine) - set(reference)
        raise AssertionError(
            f"pair tables differ: {len(only_reference)} only in reference, "
            f"{len(only_mine)} only in streamed")
    if streamed.instances != sum(reference.values()):
        raise AssertionError("pair instance totals differ")

    by_head = defaultdict(list)
    for (head, successor), count in reference.items():
        by_head[head].append((successor, count))
    heads = {streamed.ids[head] for head in by_head}
    streamed_top = P.top_successors(streamed, heads, 4)
    for head, row in by_head.items():
        row.sort(key=lambda item: (-item[1], item[0]))
        expected = [successor for successor, _ in row[:4]]
        got = [streamed.words[i] for i in streamed_top.get(streamed.ids[head], [])]
        if expected != got:
            raise AssertionError(f"top-4 differs for {head!r}: {expected} vs {got}")

    print(f"selftest_pairs {tag}: {len(mine)} distinct pairs and "
          f"{streamed.instances} instances identical; top-4 identical for {len(by_head)} heads")


if __name__ == "__main__":
    main()
