"""Проверки принятого словаря на глаз и числом: то, что должно быть видно в отчёте.

Ничего не собирает и ничего не пишет в ассеты — только читает два ассета (до и после) и
печатает JSON. Три вопроса, на каждый из которых отчёт обязан ответить числом:

1. Мусор, который оператор называл лично (`щрн`, `нб`, `фп`, `бш`, `ме`), — в словаре или
   нет, и если да, то приёмка его пустила или он стоял там до неё. Если приёмка пустила хоть
   один — правило неверное, и об этом надо узнать здесь, а не от человека.
1а. Слова, исключённые оператором поимённо (`можна`), — в словаре их быть не должно.
2. Слова, которые проект записал как молчащие (`docs/RUSSIAN-BIGRAMS.md` раздел 7,
   `docs/CORPUS-OS.md` раздел 9), — попали ли они в словарь и на каком месте.
3. Тройка подсказок на обычных префиксах — что она была и что стала. Это единственная
   проверка, которая видит не состав словаря, а то, что человек увидит на полосе.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT / "research/corpus"))

import dictionary_coverage as cov
import dictionary_pack as dp

# Слова, которые оператор показал лично как мусор. Ни одно не должно оказаться в словаре.
# `ме` добавлено в 1.9.1: его называла мусором docs/CORPUS-OS.md, и до 1.9.0 его удерживала
# регистровая улика, которая с расширением перестала решать. Теперь его держит только правило
# формального обрывка (два символа), и проверить это надо здесь, а не поверить на слово.
OPERATOR_GARBAGE = ["щрн", "нб", "фп", "бш", "ме"]
# Слова, исключённые оператором поимённо (`EXCLUDED_WORDS` в scripts/dict_accept.py).
# Условие готовности миссии tt-dict-widen названо прямо: `можна` в ассете быть не должно.
OPERATOR_EXCLUDED = ["можна"]
# Названные проектом молчащими: docs/RUSSIAN-BIGRAMS.md р. 7 и docs/CORPUS-OS.md р. 9.
SILENT_RU = ["привет", "давай", "ладно", "слушай", "извини", "забыл", "ага", "позвони",
             "устал", "здравствуй", "целую", "скучаю", "приходи", "купи", "голоден",
             "напиши", "обнимаю"]
SILENT_TT = ["зинһар", "кил", "кит", "тыңла", "онытма"]
PREFIXES_RU = ["пап", "мам", "дет", "прив", "пожал", "спас", "здрав", "завтр", "можн",
               "позвон", "перест", "послуш"]
PREFIXES_TT = ["исәнм", "рәхм", "зинһ", "хәтерл", "шалтыр", "кайт"]


def load(path: Path, tag: str):
    language = cov.language_for(tag)
    parsed = dp.validate_raw(dp.decompress_asset(path.read_bytes(), language),
                             language=language)
    freqs = dict(zip(parsed.words, parsed.frequencies))
    ranks = {w: i + 1 for i, (w, _) in
             enumerate(sorted(freqs.items(), key=lambda kv: (-kv[1], kv[0])))}
    return parsed, freqs, ranks


def top3(parsed, prefix: str, tag: str):
    language = cov.language_for(tag)
    return [w for w, _ in dp.prefix_candidates(parsed, prefix, 3, language)]


def main() -> None:
    before_dir = Path(sys.argv[1])
    out = {}
    for tag, silent, prefixes in (("rus", SILENT_RU, PREFIXES_RU),
                                  ("tat", SILENT_TT, PREFIXES_TT)):
        name = f"{'russian' if tag == 'rus' else 'tatar'}_top100k_v1.tdict.zlib"
        before, bf, br = load(before_dir / name, tag)
        after, af, ar = load(ROOT / "app/src/main/assets/dictionaries" / name, tag)
        out[tag] = {
            # Различать «приёмка пустила» и «стояло в поставляемом словаре с 1.1.0» надо
            # обязательно: татарское `ме` (частота 78) приехало из Leipzig задолго до всех
            # разговорных корпусов, и объявить это провалом правила приёмки было бы неправдой.
            "operator_garbage_added_by_acceptance": [w for w in OPERATOR_GARBAGE
                                                     if w in af and w not in bf],
            "operator_garbage_already_shipped": [w for w in OPERATOR_GARBAGE
                                                 if w in af and w in bf],
            "operator_excluded_in_dictionary": [w for w in OPERATOR_EXCLUDED if w in af],
            "silent_words": {
                w: {"before_rank": br.get(w), "after_rank": ar.get(w),
                    "before_freq": bf.get(w), "after_freq": af.get(w)}
                for w in silent},
            "prefix_top3": {
                p: {"before": top3(before, p, tag), "after": top3(after, p, tag)}
                for p in prefixes},
            "entries_before": len(bf),
            "entries_after": len(af),
            "words_added": len(set(af) - set(bf)),
            "words_displaced": len(set(bf) - set(af)),
        }
    json.dump(out, sys.stdout, ensure_ascii=False, indent=2)
    print()


if __name__ == "__main__":
    main()
