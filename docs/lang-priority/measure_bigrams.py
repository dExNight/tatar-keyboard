#!/usr/bin/env python3
"""NEXT_WORD, знаменатель — «пробел после слова, которое человек действительно написал».

Событие: человек на раскладке L набрал слово W и нажал пробел. Вес события — частота W в
словаре языка L (единственная оценка «как часто это слово пишут», которая у проекта есть).
Считаем по всем 100 000 слов словаря L.
"""
from __future__ import annotations

import json
import sys
import zlib
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]  # <repo>/docs/lang-priority/<this file>
sys.path.insert(0, str(REPO / "scripts"))
import bigram_asset_pack as bap  # noqa: E402
import dictionary_coverage as coverage  # noqa: E402
import dictionary_pack as dp  # noqa: E402

MAX_RESULTS = 3
BIGRAMS = {"tat": REPO / "app/src/main/assets/bigrams/tatar_bigrams_v1.tatbigr.zlib",
           "rus": REPO / "app/src/main/assets/bigrams/russian_bigrams_v1.tatbigr.zlib"}
DICTS = {"tat": REPO / "app/src/main/assets/dictionaries/tatar_top100k_v1.tdict.zlib",
         "rus": REPO / "app/src/main/assets/dictionaries/russian_top100k_v1.tdict.zlib"}
ALPHA = {"tat": coverage.TATAR_ALPHABET, "rus": coverage.RUSSIAN_ALPHABET}


def table(tag): 
    p = bap.validate_raw(bap.decompress(BIGRAMS[tag].read_bytes()))
    return {h: s[:MAX_RESULTS] for h, s in p.successes_by_head.items()}


def words(tag):
    p = dp.validate_raw(zlib.decompress(DICTS[tag].read_bytes()), language=coverage.LANGUAGES[tag])
    return list(zip(p.words, p.frequencies))


def run(cur_tag, other_tag, cur, other, vocab):
    other_alpha = ALPHA[other_tag]
    k = ("events", "current_answers", "current_full3", "current_silent",
         "current_silent_foreign_answers", "underfilled", "foreign_can_fill",
         "both_answer", "same_top1", "disagree_top1")
    t = {f"{x}_types": 0 for x in k} | {f"{x}_weight": 0 for x in k}
    t["foreign_cells_added"] = 0
    silent_examples, disagree_examples = [], []
    for word, freq in vocab:
        mine = cur.get(word, [])
        theirs = other.get(word, []) if all(ch in other_alpha for ch in word) else []

        def bump(name):
            t[f"{name}_types"] += 1
            t[f"{name}_weight"] += freq

        bump("events")
        if mine:
            bump("current_answers")
            if len(mine) == MAX_RESULTS:
                bump("current_full3")
        else:
            bump("current_silent")
            if theirs:
                bump("current_silent_foreign_answers")
                if len(silent_examples) < 20:
                    silent_examples.append({"word": word, "freq": freq, "foreign": theirs})
        if mine and theirs:
            bump("both_answer")
            if mine[0] == theirs[0]:
                bump("same_top1")
            else:
                bump("disagree_top1")
                if len(disagree_examples) < 20:
                    disagree_examples.append({"word": word, "freq": freq,
                                              "current": mine, "foreign": theirs})
        if len(mine) < MAX_RESULTS:
            bump("underfilled")
            fresh = [w for w in theirs if w not in mine]
            added = min(MAX_RESULTS - len(mine), len(fresh))
            if added:
                bump("foreign_can_fill")
                t["foreign_cells_added"] += added
    return {"totals": t, "silent_examples": silent_examples, "disagree_examples": disagree_examples}


def main():
    tt, ru = table("tat"), table("rus")
    vtt, vru = words("tat"), words("rus")
    print(json.dumps({
        "heads": {"tat": len(tt), "rus": len(ru), "shared": len(set(tt) & set(ru))},
        "typing_on_tatar_layout": run("tat", "rus", tt, ru, vtt),
        "typing_on_russian_layout": run("rus", "tat", ru, tt, vru),
    }, ensure_ascii=False, indent=2))
    return 0


raise SystemExit(main())
