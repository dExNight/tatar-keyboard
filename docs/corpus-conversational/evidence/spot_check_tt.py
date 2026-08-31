#!/usr/bin/env python3
"""Точечная проверка: что показывают конкретные слова до и после (часть B, tt).

Слова: повеления, названные досье миссии (`шалтырат`, `сөйлә`, `утыр`, `җибәр`,
`эшлә`, `укы`), адресный список extra-heads целиком, контроль из прошлых миссий
(`кит` — «предсказывает существительное», `кил`, `бир`, `мин`, `зинһар`) и три
головы known-drift на -гәнчә. Для каждого слова: было ли головой, стало ли, видимые
тройки.

Запуск: spot_check_tt.py СТАРАЯ_ТАБЛИЦА НОВАЯ_ТАБЛИЦА ВЫХОД.json
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts"))

import bigram_asset_pack  # noqa: E402
import dictionary_coverage as coverage  # noqa: E402

EXTRA_HEADS = ROOT / "scripts/bigram_extra_heads_tat.txt"
DICTIONARY = ROOT / "app/src/main/assets/dictionaries/tatar_top100k_v1.tdict.zlib"
NAMED = ["шалтырат", "сөйлә", "утыр", "җибәр", "эшлә", "укы",
         "кит", "кил", "бир", "мин", "зинһар",
         "искәрткәнчә", "билгеләвенчә", "үтелгәнчә"]


def load_table(path):
    parsed = bigram_asset_pack.validate_raw(
        bigram_asset_pack.decompress(Path(path).read_bytes())
    )
    return parsed.successes_by_head


def main() -> int:
    old_path, new_path, out_path = (Path(a) for a in sys.argv[1:4])
    language = coverage.language_for("tat")
    vocabulary, _ = bigram_asset_pack.read_shipped_vocabulary(DICTIONARY, language)
    extra = bigram_asset_pack.read_extra_heads(EXTRA_HEADS, vocabulary)
    old = load_table(old_path)
    new = load_table(new_path)
    words = sorted(set(NAMED) | set(extra))
    report = {}
    for word in words:
        report[word] = {
            "was_head": word in old,
            "is_head": word in new,
            "old_visible": old.get(word, [])[:3],
            "new_visible": new.get(word, [])[:3],
        }
    out_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    awakened = [w for w in words if not report[w]["was_head"] and report[w]["is_head"]]
    still_silent = [w for w in words if not report[w]["is_head"]]
    print(f"заговорили: {len(awakened)}: {' '.join(awakened)}")
    print(f"всё ещё молчат: {len(still_silent)}: {' '.join(still_silent)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
