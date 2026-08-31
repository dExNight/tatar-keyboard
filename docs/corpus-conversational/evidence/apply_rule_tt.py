#!/usr/bin/env python3
"""Применяет расширенное правило extra-heads части B механически. Руками ничего не правится.

Правило (DECISION-RULE-PRECOMMIT-TT.md, записано до прогона):
1. униграммный ранг в поставляемом словаре в [10 000, 40 000);
2. не менее 4 из 6 клеток парадигмы подтверждены тем же словарём
   (клетки — те же, что у IMPERATIVE-HEADS: docs/archive/bigrams/imperative-heads/evidence/candidates.py);
3. слово не является отрицательной формой другого глагола (Y+ма/мә при Y в словаре);
4. в НОВОМ смешанном обучении есть хотя бы одна пара (pairs из collect_pairs_tt.py).

Слова, до которых дотягивается отсечка H = 10 132, в список не попадают (их делает
головами сама отсечка; ShippedExtraHeadListTest следит, чтобы таких строк не было).

Запуск: apply_rule_tt.py PAIRS.json ВЫХОД.json
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT / "docs/archive/bigrams/imperative-heads/evidence"))

import bigram_asset_pack  # noqa: E402
import dictionary_coverage as coverage  # noqa: E402
from candidates import forms  # то же самое определение клеток, что в IMPERATIVE-HEADS

DICTIONARY = ROOT / "app/src/main/assets/dictionaries/tatar_top100k_v1.tdict.zlib"
RANK_LO, RANK_HI, CELLS_MIN, CUTOFF_H = 10_000, 40_000, 4, 10_132


def is_negative_form(word: str, vocabulary: frozenset[str]) -> bool:
    for suffix in ("ма", "мә"):
        if word.endswith(suffix) and word[: -len(suffix)] in vocabulary:
            return True
    return False


def main() -> int:
    pairs_path, out_path = (Path(a) for a in sys.argv[1:3])
    language = coverage.language_for("tat")
    vocabulary, frequencies = bigram_asset_pack.read_shipped_vocabulary(DICTIONARY, language)
    ordered = sorted(frequencies.items(), key=lambda kv: (-kv[1], kv[0]))
    pairs = json.loads(pairs_path.read_text(encoding="utf-8"))["pairs"]

    kept, rejected = [], []
    for index in range(RANK_LO - 1, RANK_HI - 1):
        word, frequency = ordered[index]
        rank = index + 1
        if len(word) < 2:
            continue
        hit = [name for name, variants in forms(word).items() if any(v in vocabulary for v in variants)]
        if len(hit) < CELLS_MIN:
            continue
        if is_negative_form(word, vocabulary):
            rejected.append({"word": word, "rank": rank, "cells": len(hit),
                             "reason": "отрицательная форма другого глагола"})
            continue
        word_pairs = pairs.get(word, [])
        n_pairs = sum(c for _, c in word_pairs)
        if n_pairs == 0:
            rejected.append({"word": word, "rank": rank, "cells": len(hit),
                             "reason": "нет пар в новом смешанном обучении"})
            continue
        kept.append({"word": word, "rank": rank, "frequency": frequency, "cells": len(hit),
                     "pairs": n_pairs, "top_successors": [s for s, _ in word_pairs[:4]],
                     "reachable_by_cutoff": rank <= CUTOFF_H})

    kept.sort(key=lambda r: r["rank"])
    report = {"rule": {"rank_range": [RANK_LO, RANK_HI], "cells_min": CELLS_MIN,
                       "cutoff_h": CUTOFF_H, "pairs_from": str(pairs_path)},
              "kept": kept, "rejected": rejected}
    out_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"# ПРИНЯТО {len(kept)}")
    for row in kept:
        print(f"{row['word']}\t{row['rank']}\t{row['frequency']}\t{row['cells']}/6\t{row['pairs']}\t{','.join(row['top_successors'])}")
    print(f"# ОТКЛОНЕНО {len(rejected)}")
    for row in rejected:
        print(f"#  {row['word']}\t{row['rank']}\t{row['cells']}/6\t{row['reason']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
