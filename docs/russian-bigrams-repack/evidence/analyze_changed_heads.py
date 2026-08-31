#!/usr/bin/env python3
"""Разбор 39 голов с изменившимся хранимым списком и дельты в 1 hit.

Предписанное правилом DECISION-RULE-PRECOMMIT действие при отличиях: стоп и разбор.
Гипотеза механизма: СТАРАЯ таблица паковалась от СТАРОГО словаря (до bfb78e93),
поэтому в её списках есть преемники, которых в сегодняшнем поставляемом словаре нет;
при перепаковке от текущего словаря такие пары не считаются вовсе.

Проверки:
1. Каждый преемник, исчезнувший у сохранившейся головы, отсутствует в ТЕКУЩЕМ
   поставляемом словаре (и, для полноты картины, отмечается, был ли он в старом).
2. Каждый появившийся преемник есть в текущем словаре.
3. Поштучный поиск событий held-out, где старая таблица попала, а новая нет
   (и наоборот), на пересечении голов: для каждого события — голова, цель и
   членство цели в обоих словарях.

Старый словарь — baseline 1.8.4 (`git show 4ca191a7:...`), тот, от которого
паковалась старая таблица (docs/archive/bigrams/BIGRAM-ADJACENCY.md).
"""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts"))

import bigram_asset_pack  # noqa: E402
import dictionary_coverage as coverage  # noqa: E402
import dictionary_pack  # noqa: E402
from bigram_pack import iter_sentences, normalized_tokens  # noqa: E402

HOLDOUT = Path.home() / "corpora-leipzig" / "rus_news_2024_1M-sentences.txt"
CURRENT_DICT = ROOT / "app/src/main/assets/dictionaries/russian_top100k_v1.tdict.zlib"
OLD_DICT_GIT = "4ca191a7:app/src/main/assets/dictionaries/russian_top100k_v1.tdict.zlib"
OLD_TABLE = "build/russian-bigrams-repack/old_russian_bigrams_v1.tatbigr.zlib"
NEW_TABLE = "build/russian-bigrams-repack/russian_bigrams_v1.tatbigr.zlib"
VISIBLE = 3


def load_table(path):
    parsed = bigram_asset_pack.validate_raw(
        bigram_asset_pack.decompress(Path(path).read_bytes())
    )
    return parsed.successes_by_head


def main() -> int:
    language = coverage.language_for("rus")
    current_vocab, _ = bigram_asset_pack.read_shipped_vocabulary(CURRENT_DICT, language)
    old_dict_bytes = subprocess.run(
        ["git", "show", OLD_DICT_GIT], cwd=ROOT, check=True, capture_output=True
    ).stdout
    old_parsed = dictionary_pack.validate_asset(old_dict_bytes, language=language)
    old_vocab = frozenset(old_parsed.words)

    old_table = load_table(ROOT / OLD_TABLE)
    new_table = load_table(ROOT / NEW_TABLE)
    retained = set(old_table) & set(new_table)

    changed = {}
    for head in sorted(retained):
        if old_table[head] == new_table[head]:
            continue
        gone = [w for w in old_table[head] if w not in new_table[head]]
        came = [w for w in new_table[head] if w not in old_table[head]]
        changed[head] = {
            "gone": [
                {"word": w, "in_current_dict": w in current_vocab, "in_old_dict": w in old_vocab}
                for w in gone
            ],
            "came": [
                {"word": w, "in_current_dict": w in current_vocab, "in_old_dict": w in old_vocab}
                for w in came
            ],
        }

    gone_all = [e for c in changed.values() for e in c["gone"]]
    came_all = [e for c in changed.values() for e in c["came"]]
    gone_in_current = [e["word"] for e in gone_all if e["in_current_dict"]]
    came_not_in_current = [e["word"] for e in came_all if not e["in_current_dict"]]

    # Поштучный разбор дельты hit-rate на пересечении голов.
    alphabet = language.alphabet
    lost_hits = []
    gained_hits = []
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
                lost_hits.append((head, target))
            elif new_hit and not old_hit:
                gained_hits.append((head, target))

    report = {
        "changed_heads": len(changed),
        "gone_successors_total": len(gone_all),
        "gone_successors_still_in_current_dictionary": gone_in_current,
        "came_successors_total": len(came_all),
        "came_successors_not_in_current_dictionary": came_not_in_current,
        "changed_detail": changed,
        "lost_hit_events": [
            {"head": h, "target": t, "target_in_current_dict": t in current_vocab,
             "target_in_old_dict": t in old_vocab}
            for h, t in lost_hits
        ],
        "gained_hit_events": [
            {"head": h, "target": t, "target_in_current_dict": t in current_vocab,
             "target_in_old_dict": t in old_vocab}
            for h, t in gained_hits
        ],
    }
    out = ROOT / "docs/russian-bigrams-repack/evidence/changed-heads-analysis.generated.json"
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
                   encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "changed_detail"},
                     ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
