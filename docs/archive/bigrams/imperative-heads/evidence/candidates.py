"""Отбор татарских повелительных форм (2 л. ед. ч. = чистая основа) в заданном диапазоне ранга.

Критерий — не догадка, а парадигма: слово считается основой глагола, если в том же
поставляемом словаре 100k лежат его производные словоформы. Требуется не меньше двух
НЕЗАВИСИМЫХ подтверждений из разных клеток парадигмы, чтобы случайное совпадение
(«кар» + «а») не проходило.
"""
import sys
sys.path.insert(0, 'scripts')
from pathlib import Path
from bigram_pack import read_shipped_vocabulary
import dictionary_coverage as coverage

BACK = set("аоуыя")   # задние гласные -> твёрдый вариант аффикса
FRONT = set("әөүеиө")

def is_back(stem: str) -> bool:
    """Сингармонизм по последнему гласному основы; по умолчанию — задний ряд."""
    for ch in reversed(stem):
        if ch in BACK:
            return True
        if ch in FRONT:
            return False
    return True

VOWELS = set("аәеёиоөуүыэюя")

def forms(stem: str) -> dict[str, list[str]]:
    """Клетки парадигмы. Ключ — имя клетки, значение — допустимые варианты."""
    back = is_back(stem)
    vowel_final = stem[-1] in VOWELS
    a, ae = ("а", "ы") if back else ("ә", "е")
    g, k = ("га", "ка") if back else ("гә", "кә")
    cells: dict[str, list[str]] = {}
    # инфинитив: -рга/-ргә после гласной, -арга/-әргә или -ырга/-ергә после согласной
    if vowel_final:
        cells["инфинитив"] = [stem + ("рга" if back else "ргә")]
    else:
        cells["инфинитив"] = [stem + v + ("рга" if back else "ргә") for v in {a, ae}]
    # прошедшее определённое: -ды/-де/-ты/-те
    cells["прош"] = [stem + d + ("ы" if back else "е") for d in ("д", "т")]
    # причастие прошедшего: -ган/-гән/-кан/-кән
    cells["прич"] = [stem + s for s in (g + "н", k + "н")]
    # отрицательное повеление: -ма/-мә
    cells["отриц"] = [stem + ("ма" if back else "мә")]
    # повеление мн. ч.: -ыгыз/-егез/-гыз/-гез
    if vowel_final:
        cells["повел_мн"] = [stem + ("гыз" if back else "гез")]
    else:
        cells["повел_мн"] = [stem + ("ыгыз" if back else "егез")]
    # имя действия: -у/-ү
    cells["имя_действия"] = [stem + ("у" if back else "ү")]
    return cells

def main() -> None:
    L = coverage.language_for("tat")
    voc, freq = read_shipped_vocabulary(
        Path("app/src/main/assets/dictionaries/tatar_top100k_v1.tdict.zlib"), L
    )
    ordered = sorted(freq.items(), key=lambda kv: (-kv[1], kv[0]))
    lo, hi = int(sys.argv[1]), int(sys.argv[2])
    need = int(sys.argv[3]) if len(sys.argv) > 3 else 2
    rows = []
    for rank in range(lo, min(hi, len(ordered))):
        word, frequency = ordered[rank]
        if len(word) < 2:
            continue
        cells = forms(word)
        hit = [name for name, variants in cells.items() if any(v in voc for v in variants)]
        if len(hit) >= need:
            rows.append((rank, word, frequency, hit))
    rows.sort(key=lambda r: -r[2])
    for rank, word, frequency, hit in rows:
        print(f"{word}\t{rank}\t{frequency}\t{len(hit)}\t{','.join(hit)}")
    print(f"# итого {len(rows)} кандидатов в рангах {lo}..{hi} при {need}+ клетках", file=sys.stderr)

if __name__ == '__main__':
    main()
