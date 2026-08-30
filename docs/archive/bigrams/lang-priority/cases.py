#!/usr/bin/env python3
"""Набор случаев: что полоса покажет по правилу приоритета на реальных ассетах."""
from __future__ import annotations

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]  # <repo>/docs/lang-priority/<this file>
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(REPO / "scripts"))

import bigram_asset_pack as bap  # noqa: E402
from measure import Dict1, MAX_RESULTS  # noqa: E402

tt, ru = Dict1("tat"), Dict1("rus")
BIG = {}
for tag, name in (("tat", "tatar_bigrams_v1"), ("rus", "russian_bigrams_v1")):
    p = bap.validate_raw(bap.decompress((REPO / f"app/src/main/assets/bigrams/{name}.tatbigr.zlib").read_bytes()))
    BIG[tag] = {h: s[:MAX_RESULTS] for h, s in p.successes_by_head.items()}


def merged(cur: Dict1, other: Dict1, prefix: str) -> dict:
    mine = cur.strip(prefix)
    theirs = other.strip(prefix) if all(c in other.language.alphabet for c in prefix) else []
    cells = list(mine)
    added = []
    if len(cells) < MAX_RESULTS:
        for w in theirs:
            if w in cells:
                continue
            cells.append(w)
            added.append(w)
            if len(cells) >= MAX_RESULTS:
                break
    return {"prefix": prefix, "current": mine, "other": theirs, "band": cells, "added": added}


def merged_next(cur_tag: str, other_tag: str, head: str) -> dict:
    mine = BIG[cur_tag].get(head, [])
    other_alpha = {"tat": tt, "rus": ru}[other_tag].language.alphabet
    theirs = BIG[other_tag].get(head, []) if all(c in other_alpha for c in head) else []
    cells = list(mine)
    added = []
    if len(cells) < MAX_RESULTS:
        for w in theirs:
            if w in cells:
                continue
            cells.append(w)
            added.append(w)
            if len(cells) >= MAX_RESULTS:
                break
    return {"context": head, "current": mine, "other": theirs, "band": cells, "added": added}


CASES_TT = ["бер", "мин", "прив", "спас", "поздрав", "минут", "к", "телеф", "спасиб", "здравств"]
CASES_RU = ["бер", "мин", "кит", "исән", "рәхм", "телеф", "к", "спасиб", "поздрав", "здравств"]
NEXT_TT = ["спасибо", "привет", "мин", "бер", "здравствуйте", "пожалуйста", "как"]
NEXT_RU = ["мин", "бер", "спасибо", "исәнмесез", "рәхмәт", "как", "привет"]

out = {
    "prefix_on_tatar_layout": [merged(tt, ru, p) for p in CASES_TT],
    "prefix_on_russian_layout": [merged(ru, tt, p) for p in CASES_RU],
    "next_word_on_tatar_layout": [merged_next("tat", "rus", h) for h in NEXT_TT],
    "next_word_on_russian_layout": [merged_next("rus", "tat", h) for h in NEXT_RU],
}
print(json.dumps(out, ensure_ascii=False, indent=1))
