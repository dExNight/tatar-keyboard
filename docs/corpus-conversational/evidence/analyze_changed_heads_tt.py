#!/usr/bin/env python3
"""Разбор изменившихся видимых троек tt при подмешивании разговорного корпуса (часть B).

Зеркало analyze_changed_heads.py части A: для каждой сохранившейся головы со сменой
видимой тройки — старая/новая тройки и членство преемников в словаре; поштучный разбор
дельты hit-rate на пересечении голов (события held-out, агрегированные по
(голова, цель)); итог потерь/приобретений с членством цели в словаре.

Запуск: analyze_changed_heads_tt.py СТАРАЯ_ТАБЛИЦА НОВАЯ_ТАБЛИЦА ВЫХОД.json
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
from bigram_pack import iter_sentences, normalized_tokens  # noqa: E402

HOLDOUT = Path.home() / "corpora-leipzig" / "tat_news_2015_1M-sentences.txt"
DICTIONARY = ROOT / "app/src/main/assets/dictionaries/tatar_top100k_v1.tdict.zlib"
VISIBLE = 3


def load_table(path):
    parsed = bigram_asset_pack.validate_raw(
        bigram_asset_pack.decompress(Path(path).read_bytes())
    )
    return parsed.successes_by_head


def main() -> int:
    old_path, new_path, out_path = (Path(a) for a in sys.argv[1:4])
    language = coverage.language_for("tat")
    vocabulary, _ = bigram_asset_pack.read_shipped_vocabulary(DICTIONARY, language)

    old_table = load_table(old_path)
    new_table = load_table(new_path)
    retained = set(old_table) & set(new_table)

    changed_visible = {}
    for head in sorted(retained):
        old3, new3 = old_table[head][:VISIBLE], new_table[head][:VISIBLE]
        if old3 != new3:
            changed_visible[head] = {
                "old": old3,
                "new": new3,
                "gone_in_dictionary": [w for w in old3 if w not in new3 and w in vocabulary],
                "came_in_dictionary": [w for w in new3 if w not in old3 and w in vocabulary],
            }

    lost: Counter[tuple[str, str]] = Counter()
    gained: Counter[tuple[str, str]] = Counter()
    alphabet = language.alphabet
    for sentence in iter_sentences(HOLDOUT):
        tokens = normalized_tokens(sentence, alphabet)
        for index in range(len(tokens) - 1):
            head = tokens[index]
            if head is None or head not in retained:
                continue
            target = tokens[index + 1]
            old_hit = target in old_table[head][:VISIBLE]
            new_hit = target in new_table[head][:VISIBLE]
            if old_hit and not new_hit:
                lost[(head, target)] += 1
            elif new_hit and not old_hit:
                gained[(head, target)] += 1

    def detail(counter):
        return [
            {"head": h, "target": t, "events": n, "target_in_dictionary": t in vocabulary}
            for (h, t), n in counter.most_common()
        ]

    report = {
        "retained_heads": len(retained),
        "retained_heads_with_changed_visible_triple": len(changed_visible),
        "changed_visible": changed_visible,
        "lost_hit_events_total": sum(lost.values()),
        "gained_hit_events_total": sum(gained.values()),
        "lost_on_targets_in_dictionary": sum(
            n for (h, t), n in lost.items() if t in vocabulary
        ),
        "gained_on_targets_in_dictionary": sum(
            n for (h, t), n in gained.items() if t in vocabulary
        ),
        "lost_detail": detail(lost),
        "gained_detail": detail(gained),
    }
    out_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    summary = {k: v for k, v in report.items() if k not in ("changed_visible", "lost_detail", "gained_detail")}
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
