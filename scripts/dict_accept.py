"""Машинная приёмка слов из очередей `docs/DICTIONARY-*-CONV-REVIEW.tsv`.

Ручная вычитка 39 176 слов не состоится — оператор сказал это прямо. Планку ставит машина,
человек смотрит два образца по сто слов. Правило одно и умещается в одну фразу:

    Слово принимается, если его подтверждает ВТОРОЙ НЕЗАВИСИМЫЙ ИСТОЧНИК
    и регистровая улика не выдаёт в нём имя собственное.

«Второй независимый источник» — ровно одно из трёх, и все три проверяются данными проекта,
без единой внешней зависимости:

  A. `two-corpora`  — слово встречается и в OpenSubtitles, и в Tatoeba. Разные корпуса,
     разные авторы, разные жанры; совпадение двух — это подтверждение, одно упоминание в
     субтитрах — нет. Именно здесь отсеиваются `щрн` (9 263 вхождения), `нб`, `фп`, `бш`.
  B. `shipped-word` — слово уже стоит в поставляемом словаре. По построению очереди
     (`make_review.py`: `all_new = [w for w in kept if w not in shipped]`) такого слова в
     очереди быть не может, поэтому ветка никогда не срабатывает. Она оставлена явной, потому
     что досье называет её планкой, и «условие не сработало ни разу» — это измеренный факт,
     который надо предъявить, а не умолчание.
  C. `shipped-paradigm` — ТОЛЬКО русский: основа слова уже стоит в поставляемом словаре не
     меньше чем в трёх других формах. Поставляемый словарь собран из Leipzig — письменного
     корпуса, никак не связанного ни с субтитрами, ни с Tatoeba, — поэтому три засвидетель-
     ствованные формы той же основы это второй источник в том же смысле, что и A.

Регистровая улика — колонка `cap_ratio` очереди: доля вхождений с заглавной буквы НЕ в начале
строки. У обычного слова она около нуля, у имени собственного и у мусора распознавания —
высокая. Фильтр корпуса уже снял всё, что ≥ 0,80; приёмка режет строже, на 0,50, потому что
ложно принятое слово хуже ложно отклонённого: отклонённое — это подсказка, которой не будет,
принятое — подсказка, которая позорит клавиатуру у живого человека.

Отклонённое НЕ удаляется: оно ложится файлом рядом с принятым, с причиной отказа, и его всегда
можно принять позже одной правкой правила.
"""
from __future__ import annotations

import argparse
import json
import random
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT / "research/corpus"))

QUEUE = {
    "rus": ROOT / "docs/DICTIONARY-RU-CONV-REVIEW.tsv",
    "tat": ROOT / "docs/DICTIONARY-TT-CONV-REVIEW.tsv",
}
OUT_DIR = ROOT / "docs/dict-accept"
SUFFIX = {"rus": "ru", "tat": "tt"}

# Доля заглавных вне начала строки, при которой слово перестаёт считаться обычным.
# Корпусный фильтр (`research/corpus/filters.py`) режет на 0,80 — до очереди; приёмка режет
# на 0,50 — после. Разные пороги намеренно: фильтр решает «это имя собственное», приёмка
# решает «в этом достаточно сомнения, чтобы не показывать человеку».
MAX_CAP_RATIO = 0.50

# Минимальная длина основы для ветки C. Без нижней границы «ме» распалось бы на «м» + «е»,
# а у односимвольной основы формы в словаре найдутся всегда.
MIN_STEM = 4
# Сколько ДРУГИХ форм той же основы должно стоять в поставляемом словаре.
MIN_PARADIGM_SIBLINGS = 3

# Русские словоизменительные окончания. Список плоский и намеренно избыточный: он не должен
# быть морфологически точным, он должен позволить найти основу. Точность даёт не он, а
# требование трёх засвидетельствованных форм: у выдуманной основы их не бывает.
RUSSIAN_ENDINGS = frozenset({
    "",
    # именное и адъективное словоизменение
    "а", "е", "и", "о", "у", "ы", "й", "ь", "я", "ю", "ё", "э",
    "ам", "ами", "ах", "ев", "ей", "ем", "ов", "ом", "ой", "ою", "ую",
    "ая", "ое", "ые", "ый", "ым", "ых", "ыми", "его", "его", "ему", "ого", "ому",
    "ий", "ия", "ии", "ию", "ием", "иях", "иям", "иями", "ими", "их",
    "ок", "ка", "ко", "ки", "ек", "ец", "ца", "цу", "цы", "цев",
    # глагольное словоизменение
    "ть", "ти", "л", "ла", "ло", "ли", "в", "вши",
    "ешь", "ет", "ете", "ут", "ют", "ит", "им", "ите", "ат", "ят", "ишь", "йте",
    "ся", "сь", "ась", "ись", "лся", "лась", "лось", "лись", "ться", "тся",
    "ешься", "ется", "емся", "етесь", "утся", "ются", "ится", "имся", "итесь",
    "атся", "ятся",
    # причастия и краткие формы
    "ущий", "ющий", "ащий", "ящий", "вший", "нный", "тый", "мый",
    "ен", "на", "но", "ны", "ена", "ено", "ены",
})

HEADER = ["word", "heldout_hits", "train_freq", "train_freq_clean", "sources",
          "license_status", "cap_ratio", "enters_top100k", "approved", "reviewer",
          "review_date", "note"]


class Row:
    __slots__ = ("word", "heldout", "freq", "freq_clean", "sources", "license",
                 "cap_ratio", "enters_top100k")

    def __init__(self, fields):
        self.word = fields[0]
        self.heldout = int(fields[1])
        self.freq = int(fields[2])
        self.freq_clean = int(fields[3])
        self.sources = fields[4]
        self.license = fields[5]
        self.cap_ratio = float(fields[6])
        self.enters_top100k = fields[7] == "yes"

    def source_set(self):
        return set(self.sources.split("+"))


def read_queue(tag: str) -> list[Row]:
    rows = []
    with QUEUE[tag].open(encoding="utf-8") as handle:
        for line in handle:
            if line.startswith("#"):
                continue
            fields = line.rstrip("\n").split("\t")
            if fields[0] == "word":
                if fields[: len(HEADER)] != HEADER:
                    raise SystemExit(f"{QUEUE[tag]}: неожиданные колонки {fields}")
                continue
            if not fields[0]:
                continue
            rows.append(Row(fields))
    return rows


def paradigm_siblings(word: str, shipped: frozenset[str]) -> tuple[int, str, str]:
    """Сколько ДРУГИХ форм той же основы стоит в поставляемом словаре — по лучшему разбору.

    Возвращает (число, основа, окончание). Перебираются все разборы `word = stem + ending`
    с окончанием из списка и основой не короче MIN_STEM; побеждает тот, у которого форм больше.
    Само `word` в счёт не идёт: оно не в словаре по построению очереди, но проверка явная,
    чтобы правило не зависело от этого построения.
    """
    best, best_stem, best_ending = 0, "", ""
    for ending in RUSSIAN_ENDINGS:
        if ending and not word.endswith(ending):
            continue
        stem = word[: len(word) - len(ending)] if ending else word
        if len(stem) < MIN_STEM:
            continue
        count = 0
        for other in RUSSIAN_ENDINGS:
            if other == ending:
                continue
            form = stem + other
            if form != word and form in shipped:
                count += 1
        if count > best:
            best, best_stem, best_ending = count, stem, ending
    return best, best_stem, best_ending


def decide(rows: list[Row], tag: str, shipped: frozenset[str]):
    """Прогнать правило. Возвращает (accepted, rejected); в каждом — (Row, причина, деталь)."""
    accepted, rejected = [], []
    for row in rows:
        if row.cap_ratio >= MAX_CAP_RATIO:
            rejected.append((row, "proper-noun-evidence",
                             f"cap_ratio={row.cap_ratio:.2f}>={MAX_CAP_RATIO:.2f}"))
            continue
        sources = row.source_set()
        if "OpenSubtitles" in sources and "Tatoeba" in sources:
            accepted.append((row, "two-corpora", row.sources))
            continue
        if row.word in shipped:
            accepted.append((row, "shipped-word", "уже в поставляемом словаре"))
            continue
        if tag == "rus":
            count, stem, ending = paradigm_siblings(row.word, shipped)
            if count >= MIN_PARADIGM_SIBLINGS:
                accepted.append((row, "shipped-paradigm",
                                 f"{stem}|{ending} +{count} форм в словаре"))
                continue
        rejected.append((row, "single-source", row.sources))
    return accepted, rejected


PREAMBLE = """\
# {kind} машинной приёмкой. Миссия tt-dict-accept, отчёт — docs/DICT-ACCEPT.md.
# Ручной вычитки не было и не будет: оператор 2026-08-24 заменил её машинным правилом.
#
# ПРАВИЛО: слово принимается, если его подтверждает второй независимый источник
# и регистровая улика не выдаёт в нём имя собственное (cap_ratio < {cap}).
#   two-corpora      — встречается и в OpenSubtitles, и в Tatoeba
#   shipped-word     — уже стоит в поставляемом словаре (в очереди таких нет по построению)
#   shipped-paradigm — русский: основа стоит в поставляемом словаре ещё в >= {sib} формах
#   single-source    — подтверждения нет: один корпус и ничего больше
#   proper-noun-evidence — >= {cap} вхождений с заглавной буквы не в начале строки
#
# Колонки — те же, что в очереди, плюс rule и rule_detail. Отклонённые НЕ удалены: изменить
# правило и перезапустить `python3 scripts/dict_accept.py select` — одна команда.
"""


def write_rows(path: Path, kind: str, decided):
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write(PREAMBLE.format(kind=kind, cap=f"{MAX_CAP_RATIO:.2f}",
                                     sib=MIN_PARADIGM_SIBLINGS))
        handle.write("\t".join(["word", "heldout_hits", "train_freq", "train_freq_clean",
                                "sources", "license_status", "cap_ratio", "enters_top100k",
                                "rule", "rule_detail"]) + "\n")
        for row, rule, detail in decided:
            handle.write("\t".join([
                row.word, str(row.heldout), str(row.freq), str(row.freq_clean),
                row.sources, row.license, f"{row.cap_ratio:.2f}",
                "yes" if row.enters_top100k else "no", rule, detail]) + "\n")


SAMPLE_HEAD = """\
# {n} случайных {kind} слов ({lang}) — образец на глаз оператору.
# Выборка случайная и воспроизводимая: random.Random({seed}).sample по всему списку,
# порядок оставлен как выпал, а не отсортирован по частоте: сортировка показала бы верхушку,
# а вопрос стоит про всё множество.
# Формат: слово <TAB> вхождений в обучающей части разговорных корпусов <TAB> правило.
# Весь список — docs/dict-accept/{file}
"""

SAMPLE_SEED = 20260824
SAMPLE_SIZE = 100


def write_sample(path: Path, kind: str, lang: str, decided, source_file: str):
    picked = random.Random(SAMPLE_SEED).sample(decided, min(SAMPLE_SIZE, len(decided)))
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write(SAMPLE_HEAD.format(n=len(picked), kind=kind, lang=lang,
                                        seed=SAMPLE_SEED, file=source_file))
        for row, rule, _detail in picked:
            handle.write(f"{row.word}\t{row.freq}\t{rule}\n")


def load_shipped(tag: str):
    import corpuslib as CL
    freqs, boundary = CL.load_shipped(tag)
    return freqs, boundary


def select(args) -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    report = {"rule": {"max_cap_ratio": MAX_CAP_RATIO, "min_stem": MIN_STEM,
                       "min_paradigm_siblings": MIN_PARADIGM_SIBLINGS,
                       "sample_seed": SAMPLE_SEED, "sample_size": SAMPLE_SIZE},
              "languages": {}}
    for tag in ("rus", "tat"):
        shipped_freq, _boundary = load_shipped(tag)
        shipped = frozenset(shipped_freq)
        rows = read_queue(tag)
        accepted, rejected = decide(rows, tag, shipped)
        suffix = SUFFIX[tag]
        write_rows(OUT_DIR / f"accepted-{suffix}.tsv", "ПРИНЯТО", accepted)
        write_rows(OUT_DIR / f"rejected-{suffix}.tsv", "ОТКЛОНЕНО", rejected)
        lang_name = "русский" if tag == "rus" else "татарский"
        write_sample(OUT_DIR / f"sample-accepted-{suffix}.txt", "ПРИНЯТЫХ", lang_name,
                     accepted, f"accepted-{suffix}.tsv")
        write_sample(OUT_DIR / f"sample-rejected-{suffix}.txt", "ОТКЛОНЁННЫХ", lang_name,
                     rejected, f"rejected-{suffix}.tsv")

        def tally(decided):
            out = {}
            for _row, rule, _detail in decided:
                out[rule] = out.get(rule, 0) + 1
            return out

        report["languages"][tag] = {
            "queue_rows": len(rows),
            "accepted": len(accepted),
            "rejected": len(rejected),
            "accepted_by_rule": tally(accepted),
            "rejected_by_rule": tally(rejected),
            "accepted_entering_top100k": sum(1 for r, _, _ in accepted if r.enters_top100k),
            "rejected_entering_top100k": sum(1 for r, _, _ in rejected if r.enters_top100k),
            "accepted_tokens": sum(r.freq for r, _, _ in accepted),
            "rejected_tokens": sum(r.freq for r, _, _ in rejected),
            "accepted_heldout_hits": sum(r.heldout for r, _, _ in accepted),
            "rejected_heldout_hits": sum(r.heldout for r, _, _ in rejected),
        }
    json.dump(report, sys.stdout, ensure_ascii=False, indent=2)
    print()
    if args.json_out:
        Path(args.json_out).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n",
                                       encoding="utf-8")
    return 0


def read_accepted(tag: str) -> dict[str, int]:
    """{слово: train_freq} из docs/dict-accept/accepted-*.tsv."""
    path = OUT_DIR / f"accepted-{SUFFIX[tag]}.tsv"
    out = {}
    with path.open(encoding="utf-8") as handle:
        for line in handle:
            if line.startswith("#"):
                continue
            fields = line.rstrip("\n").split("\t")
            if fields[0] == "word" or not fields[0]:
                continue
            out[fields[0]] = int(fields[2])
    return out


def merged_entries(tag: str):
    """Состав нового ассета: поставляемые частоты плюс принятые разговорные, отсечка 100 000.

    Слово, которого в поставляемом ассете нет, получает РОВНО свою разговорную частоту —
    нижняя граница интервала `docs/CORPUS-OS.md`, самое осторожное из двух допущений.
    Верхняя граница (плюс B) существует только для измерения и в ассет не идёт: она щедра
    к новичкам и вытесняет больше поставляемых слов, чем оправдано.
    """
    import corpuslib as CL
    shipped, _boundary = CL.load_shipped(tag)
    accepted = read_accepted(tag)
    merged = dict(shipped)
    for word, count in accepted.items():
        merged[word] = (merged[word] + count) if word in shipped else count
    top = sorted(merged.items(), key=lambda kv: (-kv[1], kv[0]))[:100_000]
    return shipped, accepted, sorted(top, key=lambda kv: kv[0])


def pack(args) -> int:
    import dictionary_pack as dp
    import corpuslib as CL
    result = {}
    for tag in ("rus", "tat"):
        shipped, accepted, entries = merged_entries(tag)
        raw = dp.serialize_entries(entries)
        asset = dp.compress_raw(raw)
        target = CL.SHIPPED[tag]
        before = target.read_bytes()
        before_raw = dp.decompress_asset(before, __import__("dictionary_coverage").language_for(tag))
        words = {w for w, _ in entries}
        result[tag] = {
            "asset": str(target.relative_to(ROOT)),
            "entries": len(entries),
            "accepted_offered": len(accepted),
            "accepted_that_entered": len(words & set(accepted) - set(shipped)),
            "shipped_words_displaced": len(set(shipped) - words),
            "asset_bytes_before": len(before),
            "asset_bytes_after": len(asset),
            "asset_bytes_delta": len(asset) - len(before),
            "raw_bytes_before": len(before_raw),
            "raw_bytes_after": len(raw),
            "raw_bytes_delta": len(raw) - len(before_raw),
            "fits_compressed": len(asset) <= dp.MAX_COMPRESSED_BYTES,
            "fits_raw": len(raw) <= dp.MAX_UNCOMPRESSED_BYTES,
            "sha256_before": __import__("hashlib").sha256(before).hexdigest(),
            "sha256_after": __import__("hashlib").sha256(asset).hexdigest(),
            "raw_sha256_after": __import__("hashlib").sha256(raw).hexdigest(),
        }
        if args.write:
            dp.validate_asset(asset, language=__import__("dictionary_coverage").language_for(tag))
            target.write_bytes(asset)
            result[tag]["written"] = True
        else:
            result[tag]["written"] = False
    json.dump(result, sys.stdout, ensure_ascii=False, indent=2)
    print()
    if args.json_out:
        Path(args.json_out).write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n",
                                       encoding="utf-8")
    return 0


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--json-out", default=None, help="куда положить те же числа файлом")
    sub = parser.add_subparsers(dest="command", required=True)
    sel = sub.add_parser("select", help="прогнать правило и записать принятое/отклонённое")
    sel.set_defaults(func=select)
    pk = sub.add_parser("pack", help="собрать ассеты из принятого")
    pk.add_argument("--write", action="store_true",
                    help="записать ассеты в app/src/main/assets (без флага только измеряет)")
    pk.set_defaults(func=pack)
    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
