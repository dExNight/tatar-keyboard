#!/usr/bin/env python3
"""tt-lang-priority: измерение конкуренции двух языков в полосе подсказок.

Читает те же два ассета, что лежат в APK, штатным парсером scripts/dictionary_pack.py, и
воспроизводит ПОЛНЫЙ ранжирующий проход TdictPrefixIndex.lookup():

  * точный уровень (collectExact): слова, начинающиеся с префикса, кроме самого префикса;
    порядок — частота по убыванию, затем слово по возрастанию; не более трёх;
  * нечёткий уровень (collectFuzzy), только если точный дал меньше трёх И в префиксе не
    меньше MIN_FUZZY_PREFIX_CODE_POINTS = 3 кодовых точек. Из трёх классов правок в сборку
    входит ровно один — SHIPPED_FUZZY_EDIT_CLASSES = [EDIT_CLASS_LONG_PRESS]: замена одной
    буквы её партнёром по долгому нажатию. Партнёры берутся из res/xml/rowkeys_*.xml той же
    раскладки и симметризуются ровно как KeyNeighborTable.build.

Соответствие с Kotlin проверяется отдельно: --selftest сверяет точный уровень со всеми
строками docs/DICTIONARY-D1A-QUERY-REVIEW.tsv, которые пинует RealDictionaryPrefixIndexTest.
"""
from __future__ import annotations

import json
import re
import sys
import zlib
from bisect import bisect_left
from collections import Counter
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]  # <repo>/docs/lang-priority/<this file>
sys.path.insert(0, str(REPO / "scripts"))

import dictionary_coverage as coverage  # noqa: E402
import dictionary_pack as dp  # noqa: E402

MAX_RESULTS = 3
MIN_FUZZY_PREFIX_CODE_POINTS = 3
MAX_FUZZY_VARIANTS = 64
MAX_FUZZY_VISITED = 8192

ASSETS = {
    "tat": REPO / "app/src/main/assets/dictionaries/tatar_top100k_v1.tdict.zlib",
    "rus": REPO / "app/src/main/assets/dictionaries/russian_top100k_v1.tdict.zlib",
}
ROWKEYS = {
    "tat": ["rowkeys_tatar_extra.xml", "rowkeys_tatar1.xml", "rowkeys_tatar2.xml", "rowkeys_tatar3.xml"],
    "rus": ["rowkeys_russian1.xml", "rowkeys_russian2.xml", "rowkeys_russian3.xml"],
}
XML_DIR = REPO / "app/src/main/res/xml"

_KEY_RE = re.compile(r"<Key\b[^>]*?/?>", re.S)
_ATTR_RE = re.compile(r'latin:(keySpec|moreKeys)="([^"]*)"')


def _unescape(value: str) -> str:
    return re.sub(r"&#x([0-9a-fA-F]+);", lambda m: chr(int(m.group(1), 16)), value)


def long_press_partners(tag: str) -> dict[str, set[str]]:
    """Симметризованная карта партнёров по долгому нажатию — как KeyNeighborTable.build."""
    partners: dict[str, set[str]] = {}
    for name in ROWKEYS[tag]:
        text = (XML_DIR / name).read_text(encoding="utf-8")
        for element in _KEY_RE.findall(text):
            attrs = dict(_ATTR_RE.findall(element))
            spec = _unescape(attrs.get("keySpec", ""))
            if len(spec) != 1 or not spec.isalpha():
                continue
            base = spec.lower()
            for raw in _unescape(attrs.get("moreKeys", "")).split(","):
                more = raw.strip().lower()
                if len(more) != 1 or not more.isalpha():
                    continue
                partners.setdefault(base, set()).add(more)
                partners.setdefault(more, set()).add(base)
    return partners


class Dict1:
    """Один поставляемый словарь и ранжирование поверх него."""

    def __init__(self, tag: str):
        self.tag = tag
        self.language = coverage.LANGUAGES[tag]
        raw = zlib.decompress(ASSETS[tag].read_bytes())
        parsed = dp.validate_raw(raw, language=self.language)
        self.words: tuple[str, ...] = parsed.words
        self.freqs: tuple[int, ...] = parsed.frequencies
        self.partners = long_press_partners(tag)
        self.index = {w: i for i, w in enumerate(self.words)}
        self._cache: dict[str, list[int]] = {}

    def block(self, prefix: str) -> range:
        start = bisect_left(self.words, prefix)
        end = start
        n = len(self.words)
        while end < n and self.words[end].startswith(prefix):
            end += 1
        return range(start, end)

    def exact(self, prefix: str) -> list[int]:
        ranked = [i for i in self.block(prefix) if self.words[i] != prefix]
        ranked.sort(key=lambda i: (-self.freqs[i], self.words[i]))
        return ranked[:MAX_RESULTS]

    def variants(self, prefix: str) -> list[str]:
        out: list[str] = []
        for position, letter in enumerate(prefix):
            for partner in sorted(self.partners.get(letter, ())):
                out.append(prefix[:position] + partner + prefix[position + 1:])
                if len(out) > MAX_FUZZY_VARIANTS:
                    return []  # бюджет превышен — уровень отбрасывается целиком
        return out

    def lookup(self, prefix: str) -> tuple[list[int], int]:
        """(индексы кандидатов в порядке полосы, сколько из них точных)."""
        exact = self.exact(prefix)
        if len(exact) >= MAX_RESULTS or len(prefix) < MIN_FUZZY_PREFIX_CODE_POINTS:
            return exact, len(exact)
        remaining = MAX_RESULTS - len(exact)
        seen = set(exact)
        fuzzy: list[int] = []
        visited = 0
        for variant in self.variants(prefix):
            for i in self.block(variant):
                visited += 1
                if visited > MAX_FUZZY_VISITED:
                    return exact, len(exact)
                if self.words[i] == prefix or i in seen:
                    continue
                seen.add(i)
                fuzzy.append(i)
        # внутри одного класса — частота по убыванию, затем слово по возрастанию
        fuzzy.sort(key=lambda i: (-self.freqs[i], self.words[i]))
        return exact + fuzzy[:remaining], len(exact)

    def cells(self, prefix: str) -> list[int]:
        hit = self._cache.get(prefix)
        if hit is None:
            hit, _ = self.lookup(prefix)
            self._cache[prefix] = hit
        return hit

    def strip(self, prefix: str) -> list[str]:
        return [self.words[i] for i in self.cells(prefix)]

    def top_freq(self, prefix: str) -> int | None:
        cells = self.cells(prefix)
        return self.freqs[cells[0]] if cells else None


def selftest(tt: Dict1) -> None:
    """Точный уровень против того же файла, которым RealDictionaryPrefixIndexTest пинует Kotlin."""
    review = REPO / "docs/DICTIONARY-D1A-QUERY-REVIEW.tsv"
    rows = [line.split("\t") for line in review.read_text(encoding="utf-8").splitlines()[1:] if line.strip()]
    checked = 0
    for fields in rows:
        prefix, expected = fields[0], [w for w in fields[1].split("|") if w]
        got = [tt.words[i] for i in tt.exact(prefix)]
        assert got == expected, f"{prefix}: {got} != {expected}"
        checked += 1
    print(f"selftest: точный уровень совпал на {checked} строках D1A-QUERY-REVIEW", file=sys.stderr)


def typed_prefixes(cur: Dict1, top_words: int, max_len: int) -> Counter:
    """Вес префикса — суммарная частота слов, при наборе которых он проходит под курсором."""
    weight: Counter = Counter()
    order = sorted(range(len(cur.words)), key=lambda i: -cur.freqs[i])[:top_words]
    for i in order:
        word, freq = cur.words[i], cur.freqs[i]
        for k in range(1, min(len(word), max_len) + 1):
            weight[word[:k]] += freq
    return weight


def measure(cur: Dict1, other: Dict1, weight: Counter) -> dict:
    keys = ("prefixes", "both_answer", "same_top1", "foreign_beats_cell1", "underfilled",
            "foreign_can_fill", "current_empty", "current_empty_foreign_has")
    tot = {f"{k}_types": 0 for k in keys} | {f"{k}_weight": 0 for k in keys}
    tot["foreign_cells_added"] = 0
    by_len: dict[int, dict] = {}
    examples: list[dict] = []

    for prefix, wt in weight.items():
        mine = cur.strip(prefix)
        theirs = other.strip(prefix) if all(ch in other.language.alphabet for ch in prefix) else []
        length = len(prefix)
        bucket = by_len.setdefault(length, {f"{k}_types": 0 for k in keys} | {f"{k}_weight": 0 for k in keys})

        def bump(name: str) -> None:
            tot[f"{name}_types"] += 1
            tot[f"{name}_weight"] += wt
            bucket[f"{name}_types"] += 1
            bucket[f"{name}_weight"] += wt

        bump("prefixes")
        if mine and theirs:
            bump("both_answer")
            if mine[0] == theirs[0]:
                bump("same_top1")
            if (other.top_freq(prefix) or 0) > (cur.top_freq(prefix) or 0):
                bump("foreign_beats_cell1")
                if len(examples) < 40 and length >= 2:
                    examples.append({"prefix": prefix, "current": mine[0], "foreign": theirs[0],
                                     "f_current": cur.top_freq(prefix), "f_foreign": other.top_freq(prefix)})
        if not mine:
            bump("current_empty")
            if theirs:
                bump("current_empty_foreign_has")
        if len(mine) < MAX_RESULTS:
            bump("underfilled")
            fresh = [w for w in theirs if w not in mine]
            added = min(MAX_RESULTS - len(mine), len(fresh))
            if added:
                bump("foreign_can_fill")
                tot["foreign_cells_added"] += added

    return {"totals": tot, "by_prefix_length": dict(sorted(by_len.items())), "cell1_examples": examples}


def scales(tt: Dict1, ru: Dict1) -> dict:
    shared = [w for w in tt.words if w in ru.index]
    ratios = sorted(ru.freqs[ru.index[w]] / tt.freqs[tt.index[w]] for w in shared)
    def q(p: float):
        return round(ratios[int(p * (len(ratios) - 1))], 4) if ratios else None
    return {
        "tt_entries": len(tt.words), "ru_entries": len(ru.words),
        "tt_freq_sum": sum(tt.freqs), "ru_freq_sum": sum(ru.freqs),
        "tt_freq_max": max(tt.freqs), "ru_freq_max": max(ru.freqs),
        "shared_words": len(shared),
        "ratio_ru_over_tt": {"p05": q(.05), "p25": q(.25), "median": q(.50),
                             "p75": q(.75), "p95": q(.95),
                             "min": round(ratios[0], 6), "max": round(ratios[-1], 2)},
    }


def main() -> int:
    top_words = int(sys.argv[1]) if len(sys.argv) > 1 else 5000
    max_len = int(sys.argv[2]) if len(sys.argv) > 2 else 8
    tt, ru = Dict1("tat"), Dict1("rus")
    selftest(tt)
    print(json.dumps({
        "params": {"top_words": top_words, "max_prefix_len": max_len,
                   "partners_tat": {k: sorted(v) for k, v in sorted(tt.partners.items())},
                   "partners_rus": {k: sorted(v) for k, v in sorted(ru.partners.items())}},
        "scales": scales(tt, ru),
        "typing_on_tatar_layout": measure(tt, ru, typed_prefixes(tt, top_words, max_len)),
        "typing_on_russian_layout": measure(ru, tt, typed_prefixes(ru, top_words, max_len)),
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
