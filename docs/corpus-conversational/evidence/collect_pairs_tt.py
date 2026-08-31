#!/usr/bin/env python3
"""Счётчики пар нового смешанного обучения tt для правила extra-heads (часть B).

Считает пары по правилу E5a (iter_pairs: оба конца в поставляемом словаре, самопары
отброшены, отвергнутый токен рвёт соседство) над НОВЫМ смешанным обучением:
tat_mixed_2015_1M + tat_web_2018_1M + разговорный тренировочный поток (id % 10 != 1).

Полная таблица пар не нужна: правило спрашивает только про головы-кандидаты — слова
поставляемого словаря с рангом в [10 000, 40 000). Считаются пары с головой из этого
множества; попутно — общее число пар-вхождений для отчёта.

Запуск: collect_pairs_tt.py ВЫХОД.json
"""

from __future__ import annotations

import json
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts"))

import bigram_asset_pack  # noqa: E402
import dictionary_coverage as coverage  # noqa: E402
from bigram_pack import iter_pairs, iter_sentences, normalized_tokens  # noqa: E402

TRAIN = [
    Path.home() / "corpora-leipzig" / "tat_mixed_2015_1M-sentences.txt",
    Path.home() / "corpora-leipzig" / "tat_web_2018_1M-sentences.txt",
    ROOT / "build/corpus-conversational/tt_conv-train90-sentences.txt",
]
DICTIONARY = ROOT / "app/src/main/assets/dictionaries/tatar_top100k_v1.tdict.zlib"
RANK_LO, RANK_HI = 10_000, 40_000  # 1-based, как в правиле


def main() -> int:
    out_path = Path(sys.argv[1])
    language = coverage.language_for("tat")
    vocabulary, frequencies = bigram_asset_pack.read_shipped_vocabulary(DICTIONARY, language)
    ordered = sorted(frequencies.items(), key=lambda kv: (-kv[1], kv[0]))
    candidates = {w for w, _ in ordered[RANK_LO - 1 : RANK_HI - 1]}

    per_head: dict[str, Counter[str]] = {}
    total_pair_events = 0
    for path in TRAIN:
        for sentence in iter_sentences(path):
            tokens = normalized_tokens(sentence, language.alphabet)
            for head, success in iter_pairs(tokens, vocabulary):
                total_pair_events += 1
                if head in candidates:
                    per_head.setdefault(head, Counter())[success] += 1

    report = {
        "train": [str(p) for p in TRAIN],
        "total_pair_events": total_pair_events,
        "candidate_heads": len(candidates),
        "candidate_heads_with_pairs": len(per_head),
        "pairs": {w: sorted(c.items(), key=lambda kv: (-kv[1], kv[0])) for w, c in per_head.items()},
    }
    out_path.write_text(json.dumps(report, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "pairs"}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
