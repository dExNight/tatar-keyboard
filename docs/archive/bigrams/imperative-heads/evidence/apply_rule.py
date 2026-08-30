"""Применяет правило из DECISION-RULE-PRECOMMIT.md механически. Руками ничего не правится."""
import sys, json
sys.path.insert(0,'scripts')
from pathlib import Path
from bigram_pack import read_shipped_vocabulary
import dictionary_coverage as coverage
sys.path.insert(0, str(Path(sys.argv[0]).parent))
from candidates import forms  # то же самое определение клеток

L = coverage.language_for('tat')
voc, freq = read_shipped_vocabulary(Path('app/src/main/assets/dictionaries/tatar_top100k_v1.tdict.zlib'), L)
ordered = sorted(freq.items(), key=lambda kv:(-kv[1],kv[0]))
pairs = json.loads(Path(sys.argv[1]).read_text())

RANK_LO, RANK_HI, CELLS_MIN = 10_000, 15_000, 4

def is_negative_form(word: str) -> bool:
    for suffix in ("ма", "мә"):
        if word.endswith(suffix) and word[: -len(suffix)] in voc:
            return True
    return False

kept, rejected = [], []
for rank in range(RANK_LO, RANK_HI):
    word, frequency = ordered[rank]
    if len(word) < 2:
        continue
    hit = [n for n, vs in forms(word).items() if any(v in voc for v in vs)]
    if len(hit) < CELLS_MIN:
        continue
    if is_negative_form(word):
        rejected.append((word, rank, frequency, len(hit), "отрицательная форма другого глагола"))
        continue
    n_pairs = sum(c for _, c in pairs.get(word, []))
    if n_pairs == 0:
        rejected.append((word, rank, frequency, len(hit), "нет пар в обучающем корпусе"))
        continue
    kept.append((word, rank, frequency, len(hit), n_pairs))

kept.sort(key=lambda r: r[1])
print("# ПРИНЯТО", len(kept))
for w, r, f, c, p in kept:
    print(f"{w}\t{r}\t{f}\t{c}\t{p}")
print("# ОТКЛОНЕНО", len(rejected))
for row in rejected:
    print("#  " + "\t".join(str(x) for x in row))
