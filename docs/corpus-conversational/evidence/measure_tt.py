#!/usr/bin/env python3
"""Сверка старой и новой татарских таблиц биграмм + hit-rate на письменном held-out.

Зеркало measure.py части A для tt: разбор обоих артефактов независимым декодером,
выбывшие/новые головы против сегодняшнего отбора (топ-H словаря ∪ адресный список),
поголовная сверка видимых троек, hit-rate на tat_news_2015_1M с замороженным
знаменателем E5a (событие — позиция, чей предыдущий токен прошёл normalize_word;
без предсказания — промах; попадание — цель в трёх первых преемниках,
MAX_RESULTS = 3). Отдельно — та же метрика на пересечении голов.

Запуск: measure_tt.py СТАРАЯ_ТАБЛИЦА НОВАЯ_ТАБЛИЦА ВЫХОД.json
"""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts"))

import bigram_asset_pack  # noqa: E402
import dictionary_coverage as coverage  # noqa: E402
from bigram_pack import iter_sentences, normalized_tokens, select_heads  # noqa: E402

HOLDOUT = Path.home() / "corpora-leipzig" / "tat_news_2015_1M-sentences.txt"
DICTIONARY = ROOT / "app/src/main/assets/dictionaries/tatar_top100k_v1.tdict.zlib"
EXTRA_HEADS = ROOT / "scripts/bigram_extra_heads_tat.txt"
HEADS = 10_132
VISIBLE = 3


def load_table(path: Path):
    asset = path.read_bytes()
    raw = bigram_asset_pack.decompress(asset)
    parsed = bigram_asset_pack.validate_raw(raw)
    meta = {
        "compressed_size": len(asset),
        "compressed_sha256": hashlib.sha256(asset).hexdigest(),
        "raw_size": len(raw),
        "raw_sha256": hashlib.sha256(raw).hexdigest(),
        "heads": len(parsed.head_words),
        "pairs": sum(len(v) for v in parsed.successes_by_head.values()),
        "success_vocabulary": len(parsed.success_vocabulary),
    }
    return parsed.successes_by_head, meta


def evaluate(table, alphabet, restrict_to=None):
    events = events_with = hits = 0
    for sentence in iter_sentences(HOLDOUT):
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

    language = coverage.language_for("tat")
    alphabet = language.alphabet
    vocabulary, frequencies = bigram_asset_pack.read_shipped_vocabulary(DICTIONARY, language)
    todays_selection = set(select_heads(frequencies, HEADS)) | set(
        bigram_asset_pack.read_extra_heads(EXTRA_HEADS, vocabulary)
    )

    old_table, old_meta = load_table(old_path)
    new_table, new_meta = load_table(new_path)

    old_heads = set(old_table)
    new_heads = set(new_table)
    retained = old_heads & new_heads
    dropped = sorted(old_heads - new_heads)
    added = sorted(new_heads - old_heads)

    changed_visible = {
        w: {"old": old_table[w][:VISIBLE], "new": new_table[w][:VISIBLE]}
        for w in sorted(retained)
        if old_table[w][:VISIBLE] != new_table[w][:VISIBLE]
    }
    changed_stored = sum(1 for w in retained if old_table[w] != new_table[w])

    old_eval = evaluate(old_table, alphabet)
    new_eval = evaluate(new_table, alphabet)
    old_eval_inter = evaluate(old_table, alphabet, restrict_to=retained)
    new_eval_inter = evaluate(new_table, alphabet, restrict_to=retained)

    report = {
        "old": old_meta,
        "new": new_meta,
        "heads": {
            "old": len(old_heads),
            "new": len(new_heads),
            "retained": len(retained),
            "dropped": len(dropped),
            "added": len(added),
            "dropped_outside_todays_selection": sorted(w for w in dropped if w not in todays_selection),
            "dropped_inside_todays_selection": sorted(w for w in dropped if w in todays_selection),
            "added_from_todays_selection": sorted(w for w in added if w in todays_selection),
            "added_outside_todays_selection": sorted(w for w in added if w not in todays_selection),
            "heads_outside_dictionary": sorted(w for w in new_heads if w not in vocabulary),
        },
        "continuity": {
            "retained_heads_with_changed_visible_triple": len(changed_visible),
            "retained_heads_with_changed_stored_list": changed_stored,
            "changed_visible_examples": dict(list(changed_visible.items())[:10]),
        },
        "hit_rate": {
            "old": old_eval,
            "new": new_eval,
            "old_on_intersection_heads": old_eval_inter,
            "new_on_intersection_heads": new_eval_inter,
        },
    }
    out_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(report["hit_rate"], ensure_ascii=False, indent=2))
    print(json.dumps(report["continuity"], ensure_ascii=False, indent=2))
    print(json.dumps(report["heads"], ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
