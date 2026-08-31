#!/usr/bin/env python3
"""Hit-rate на РАЗГОВОРНОМ held-out tt — зеркало письменного замера (часть B).

Разговорный held-out — строки `id % 10 == 1` дедуплицированного tt-входа
(Tatoeba + OpenSubtitles); в обучении (id % 10 != 1) они не участвовали. Мера та же:
событие — позиция, чей предыдущий токен прошёл normalize_word; попадание — цель в
видимой тройке.

Запуск: measure_conv_heldout_tt.py СТАРАЯ_ТАБЛИЦА НОВАЯ_ТАБЛИЦА ВЫХОД.json
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts"))

import bigram_asset_pack  # noqa: E402
import dictionary_coverage as coverage  # noqa: E402
from bigram_pack import iter_sentences, normalized_tokens  # noqa: E402

HELDOUT = ROOT / "build/corpus-conversational/tt_conv-heldout10-sentences.txt"
VISIBLE = 3


def load_table(path):
    parsed = bigram_asset_pack.validate_raw(
        bigram_asset_pack.decompress(Path(path).read_bytes())
    )
    return parsed.successes_by_head


def evaluate(table, alphabet, restrict_to=None):
    events = events_with = hits = 0
    for sentence in iter_sentences(HELDOUT):
        tokens = normalized_tokens(sentence, alphabet)
        for index in range(len(tokens) - 1):
            head = tokens[index]
            if head is None:
                continue
            if restrict_to is not None and head not in restrict_to:
                continue
            events += 1
            successors = table.get(head)
            if not successors:
                continue
            events_with += 1
            if tokens[index + 1] in successors[:VISIBLE]:
                hits += 1
    return {
        "events": events,
        "events_with_prediction": events_with,
        "hits": hits,
        "unconditional_top3": hits / events if events else 0.0,
        "conditional_top3": hits / events_with if events_with else 0.0,
        "coverage": events_with / events if events else 0.0,
    }


def main() -> int:
    old_path, new_path, out_path = (Path(a) for a in sys.argv[1:4])
    alphabet = coverage.language_for("tat").alphabet
    old_table = load_table(old_path)
    new_table = load_table(new_path)
    retained = set(old_table) & set(new_table)
    report = {
        "heldout": str(HELDOUT),
        "note": "строки id % 10 == 1 дедуплицированного разговорного tt-входа; в обучении (id % 10 != 1) не участвовали",
        "old": evaluate(old_table, alphabet),
        "new": evaluate(new_table, alphabet),
        "old_on_intersection_heads": evaluate(old_table, alphabet, restrict_to=retained),
        "new_on_intersection_heads": evaluate(new_table, alphabet, restrict_to=retained),
    }
    out_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
